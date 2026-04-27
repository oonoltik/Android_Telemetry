import Foundation
import Vision
import AVFoundation
import CoreGraphics
import Combine

enum DriverFatigueState: String {
    case normal
    case warning
    case critical
}

final class DriverMonitoringService: NSObject {

    @Published private(set) var eyeOpenScore: CGFloat = 0
    @Published private(set) var smoothedEyeOpenScore: CGFloat = 0
    @Published private(set) var perclos: Double = 0
    @Published private(set) var driverFatigueState: DriverFatigueState = .normal

    private let faceRequest = VNDetectFaceLandmarksRequest()

    private var smoothedEye: CGFloat = 0
    private var calibratedOpenEye: CGFloat?
    private var calibrationSamples: [CGFloat] = []

    private var eyeClosedWindow: [Bool] = []
    private let windowSize = 150
    
    private var consecutiveClosedFrames = 0
    private let microsleepFrames = 35
    private var microsleepActive = false
    
    private var consecutiveOpenFramesAfterMicrosleep = 0
    private let microsleepRecoveryFrames = 45
    private var recoveringFromMicrosleep = false

    private var candidateState: DriverFatigueState = .normal
    private var candidateFrameCount = 0
    private let stateDebounceFrames = 45

    private var lastPrintedState: DriverFatigueState = .normal

    private let minimumEyeThreshold: CGFloat = 0.14
    private let closedEyeRatio: CGFloat = 0.65
    
    let warningPerclosThreshold: Double = 0.28
    let criticalPerclosThreshold: Double = 0.45
    let normalRecoveryPerclosThreshold: Double = 0.18
    let criticalRecoveryPerclosThreshold: Double = 0.32
    
    private var suppressCriticalFramesAfterRecovery = 0
    private let suppressCriticalFramesLimit = 90
    
    private let speechSynthesizer = AVSpeechSynthesizer()
    
    

    func processFrame(_ pixelBuffer: CVPixelBuffer) {
        let handler = VNImageRequestHandler(
            cvPixelBuffer: pixelBuffer,
            orientation: .leftMirrored
        )

        do {
            try handler.perform([faceRequest])

            guard let results = faceRequest.results as? [VNFaceObservation],
                  let face = results.first else {
                handleNoFace()
                return
            }

            handleFace(face)

        } catch {
            print("[VISION] error:", error)
        }
    }

    private func handleFace(_ face: VNFaceObservation) {
        guard let landmarks = face.landmarks,
              let leftEye = landmarks.leftEye,
              let rightEye = landmarks.rightEye else {
            return
        }

        let left = eyeAspectRatio(points: leftEye.normalizedPoints)
        let right = eyeAspectRatio(points: rightEye.normalizedPoints)
        let rawScore = (left + right) / 2

        if smoothedEye == 0 {
            smoothedEye = rawScore
        } else {
            smoothedEye = 0.85 * smoothedEye + 0.15 * rawScore
        }

        updateCalibrationIfNeeded(smoothedEye)
        updateFatigue(rawScore: rawScore, smoothedScore: smoothedEye)
    }

    private func updateCalibrationIfNeeded(_ score: CGFloat) {
        guard calibratedOpenEye == nil else { return }

        if score > minimumEyeThreshold {
            calibrationSamples.append(score)
        }

        if calibrationSamples.count >= 60 {
            let sorted = calibrationSamples.sorted()
            let start = Int(Double(sorted.count) * 0.6)
            let upperSamples = sorted[start..<sorted.count]
            let average = upperSamples.reduce(CGFloat(0), +) / CGFloat(upperSamples.count)

            calibratedOpenEye = max(average, 0.20)

            print("[FATIGUE][CALIBRATION] openEyeBaseline:", calibratedOpenEye ?? 0)
        }
    }

    private func updateFatigue(rawScore: CGFloat, smoothedScore: CGFloat) {
        guard let openEye = calibratedOpenEye else {
            DispatchQueue.main.async {
                self.eyeOpenScore = rawScore
                self.smoothedEyeOpenScore = smoothedScore
                self.perclos = 0
                self.driverFatigueState = .normal
            }

            print("[FATIGUE][CALIBRATING] eye:", rawScore, "smooth:", smoothedScore)
            return
        }

        let dynamicClosedThreshold = max(minimumEyeThreshold, openEye * closedEyeRatio)
        
        let isClosed = smoothedScore < dynamicClosedThreshold

        if isClosed {
            consecutiveClosedFrames += 1
            consecutiveOpenFramesAfterMicrosleep = 0

            if driverFatigueState == .critical {
                recoveringFromMicrosleep = true
            }
        } else {
            consecutiveClosedFrames = 0
            microsleepActive = false

            if driverFatigueState == .critical || recoveringFromMicrosleep {
                consecutiveOpenFramesAfterMicrosleep += 1
            } else {
                consecutiveOpenFramesAfterMicrosleep = 0
            }
        }

        if consecutiveClosedFrames >= microsleepFrames {
            microsleepActive = true
            recoveringFromMicrosleep = true
            consecutiveOpenFramesAfterMicrosleep = 0
            suppressCriticalFramesAfterRecovery = 0
        }

        eyeClosedWindow.append(isClosed)
        if eyeClosedWindow.count > windowSize {
            eyeClosedWindow.removeFirst()
        }

        let closedCount = eyeClosedWindow.filter { $0 }.count
        let ratio = Double(closedCount) / Double(max(eyeClosedWindow.count, 1))

        let measuredState: DriverFatigueState

        if microsleepActive {
            measuredState = .critical
        } else {
            measuredState = measuredFatigueState(perclos: ratio)
        }

        if suppressCriticalFramesAfterRecovery > 0 && !microsleepActive {
            suppressCriticalFramesAfterRecovery -= 1
        }

        let effectiveMeasuredState: DriverFatigueState

        if suppressCriticalFramesAfterRecovery > 0 &&
            measuredState == .critical &&
            !microsleepActive {
            effectiveMeasuredState = .warning
        } else {
            effectiveMeasuredState = measuredState
        }

        let stableState: DriverFatigueState

        if microsleepActive {
            stableState = .critical
        } else if recoveringFromMicrosleep {
            if consecutiveOpenFramesAfterMicrosleep >= microsleepRecoveryFrames {
                stableState = .warning
                recoveringFromMicrosleep = false
                consecutiveOpenFramesAfterMicrosleep = 0
                candidateState = .warning
                candidateFrameCount = 0
                suppressCriticalFramesAfterRecovery = suppressCriticalFramesLimit
            } else {
                stableState = .critical
            }
        } else {
            stableState = updateStateMachine(effectiveMeasuredState)
        }

        let previousState = driverFatigueState

        DispatchQueue.main.async {
            self.eyeOpenScore = rawScore
            self.smoothedEyeOpenScore = smoothedScore
            self.perclos = ratio
            self.driverFatigueState = stableState
        }

        speakCriticalFatigueWarningIfNeeded(
            previousState: previousState,
            newState: stableState
        )

        if stableState != lastPrintedState {
            print("[FATIGUE][STATE] \(lastPrintedState.rawValue) -> \(stableState.rawValue)")
            lastPrintedState = stableState
        }

        print(
            "[FATIGUE]",
            "eye:", rawScore,
            "smooth:", smoothedScore,
            "threshold:", dynamicClosedThreshold,
            "perclos:", ratio,
            "state:", stableState.rawValue,
            "microsleep:", microsleepActive,
            "closedFrames:", consecutiveClosedFrames,
            "openRecoveryFrames:", consecutiveOpenFramesAfterMicrosleep,
            "recovering:", recoveringFromMicrosleep
        )
    }
    
    private func speakCriticalFatigueWarningIfNeeded(previousState: DriverFatigueState, newState: DriverFatigueState) {
        guard previousState != .critical && newState == .critical else { return }

        DispatchQueue.main.async {
            guard !self.speechSynthesizer.isSpeaking else { return }

            let utterance = AVSpeechUtterance(string: "Водитель засыпает. Останови-тесь")
            utterance.rate = 0.5
            utterance.volume = 1.0
            utterance.voice = AVSpeechSynthesisVoice(language: "ru-RU")

            self.speechSynthesizer.speak(utterance)
        }
    }

    private func measuredFatigueState(perclos: Double) -> DriverFatigueState {
        switch driverFatigueState {
        case .normal:
            if perclos >= criticalPerclosThreshold { return .critical }
            if perclos >= warningPerclosThreshold { return .warning }
            return .normal

        case .warning:
            if perclos >= criticalPerclosThreshold { return .critical }
            if perclos <= normalRecoveryPerclosThreshold { return .normal }
            return .warning

        case .critical:
            if perclos <= criticalRecoveryPerclosThreshold { return .warning }
            return .critical
        }
    }

    private func updateStateMachine(_ measuredState: DriverFatigueState) -> DriverFatigueState {
        if measuredState == driverFatigueState {
            candidateState = measuredState
            candidateFrameCount = 0
            return driverFatigueState
        }

        if measuredState == candidateState {
            candidateFrameCount += 1
        } else {
            candidateState = measuredState
            candidateFrameCount = 1
        }

        if candidateFrameCount >= stateDebounceFrames {
            candidateFrameCount = 0
            return candidateState
        }

        return driverFatigueState
    }

    private func handleNoFace() {
        eyeClosedWindow.append(true)

        if eyeClosedWindow.count > windowSize {
            eyeClosedWindow.removeFirst()
        }

        let closedCount = eyeClosedWindow.filter { $0 }.count
        let ratio = Double(closedCount) / Double(max(eyeClosedWindow.count, 1))
        let measuredState = measuredFatigueState(perclos: ratio)
        let stableState = updateStateMachine(measuredState)

        DispatchQueue.main.async {
            self.perclos = ratio
            self.driverFatigueState = stableState
        }

        print("[FATIGUE][NO_FACE] perclos:", ratio, "state:", stableState.rawValue)
    }

    private func eyeAspectRatio(points: [CGPoint]) -> CGFloat {
        guard points.count >= 6 else { return 0 }

        let v1 = distance(points[1], points[5])
        let v2 = distance(points[2], points[4])
        let h = distance(points[0], points[3])

        guard h > 0 else { return 0 }

        return (v1 + v2) / (2 * h)
    }

    private func distance(_ p1: CGPoint, _ p2: CGPoint) -> CGFloat {
        hypot(p1.x - p2.x, p1.y - p2.y)
    }
}
