import Foundation
import Vision
import AVFoundation
import CoreGraphics
import Combine

enum DriverFatigueState: String {
    case normal
    case warning
    case critical
    case distracted
    case drowsy
}

private enum DriverAlertReason {
    case eyesClosed
    case headDown
    case distractedLeft
    case distractedRight
    case yawning
    case faceLost
    case emergencyStop
}
private enum HeadTurnDirection {
    case left
    case right
    case none
}


final class DriverMonitoringService: NSObject, ObservableObject, AVSpeechSynthesizerDelegate {

    @Published private(set) var eyeOpenScore: CGFloat = 0
    @Published private(set) var smoothedEyeOpenScore: CGFloat = 0
    @Published private(set) var perclos: Double = 0
    @Published private(set) var driverFatigueState: DriverFatigueState = .normal
    
    @Published private(set) var mouthOpenScore: CGFloat = 0
    @Published private(set) var isYawning: Bool = false
    @Published private(set) var fatigueScore: Double = 0

    private let faceRequest = VNDetectFaceLandmarksRequest()

    private var smoothedEye: CGFloat = 0
    private var calibratedOpenEye: CGFloat?
    private var calibrationSamples: [CGFloat] = []

    private var eyeClosedWindow: [Bool] = []
    private let windowSize = 150
    
    private var smoothedMouthOpenScore: CGFloat = 0
    private var yawnFrames = 0
    private let yawnFramesThreshold = 45
    private let yawnMouthOpenThreshold: CGFloat = 0.40
    private var yawnAlertActive = false
    
    private var consecutiveClosedFrames = 0
    private let microsleepFrames = 35
    private var microsleepActive = false
    
    private var eyesClosedStartTime: Date?
    private var emergencyStopTriggered = false
    private let emergencyEyesClosedDuration: TimeInterval = 4.0
    
    
    private var consecutiveOpenFramesAfterMicrosleep = 0
    private let microsleepRecoveryFrames = 45
    private var recoveringFromMicrosleep = false
    
    private var faceMissingFrames = 0
    private let distractedFramesThreshold = 30
    
    private let faceMissingFramesThreshold = 30
    private var faceLostAlertActive = false

    private var distractedFrames = 0
    private var drowsyFrames = 0

    private let headPoseSmoothing: CGFloat = 0.85

    private var smoothedYaw: CGFloat = 0
    private var smoothedPitch: CGFloat = 0
    private var smoothedRoll: CGFloat = 0

    private var headPoseBaselineYaw: CGFloat?
    private var headPoseBaselinePitch: CGFloat?
    private var headPoseCalibrationYawSamples: [CGFloat] = []
    private var headPoseCalibrationPitchSamples: [CGFloat] = []

    private let pitchDownThreshold: CGFloat = -0.055
   
    private let yawRightDistractedThreshold: CGFloat = 0.020
    private let yawLeftDistractedThreshold: CGFloat = 0.03
    
    private var rawFatigueScore: Double = 0

    private let fatigueScoreRiseStep: Double = 0.30
    private let fatigueScoreFallStep: Double = 0.008

    private var lastFatigueScoreUpdateAt: Date?
    
    private var fatigueScoreHoldUntil: Date?
    private let fatigueScoreHoldDuration: TimeInterval = 60 * 60
    private let fatigueScoreHoldThreshold: Double = 60
    

    private let drowsyFramesThreshold = 8

    private var candidateState: DriverFatigueState = .normal
    private var candidateFrameCount = 0
    private let stateDebounceFrames = 45

    private var lastPrintedState: DriverFatigueState = .normal

    private let minimumEyeThreshold: CGFloat = 0.10
    private let calibrationMinimumEyeSample: CGFloat = 0.08
    private let minimumClosedEyeThreshold: CGFloat = 0.09
    private let closedEyeRatio: CGFloat = 0.55
    private let strongClosedEyeThreshold: CGFloat = 0.075
    
    let warningPerclosThreshold: Double = 0.28
    let criticalPerclosThreshold: Double = 0.45
    let normalRecoveryPerclosThreshold: Double = 0.18
    let criticalRecoveryPerclosThreshold: Double = 0.32
    
    private var suppressCriticalFramesAfterRecovery = 0
    private let suppressCriticalFramesLimit = 90
    
    private let speechSynthesizer = AVSpeechSynthesizer()
    
    private var lastVoiceAlertAt: Date?
    private let voiceAlertCooldown: TimeInterval = 10
    private var lastVoiceAlertReason: DriverAlertReason?
    
    private var lastSpokenHeadTurnDirection: HeadTurnDirection = .none
    
    private var speechQueue: [String] = []
    private var isSpeakingNow = false
    private var lastSpokenReason: String?
    
    
    
    
    override init() {
        super.init()
        speechSynthesizer.delegate = self
    }
    
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
    
    private func processSpeechQueue() {
        guard !isSpeakingNow, !speechQueue.isEmpty else { return }

        isSpeakingNow = true
        let next = speechQueue.removeFirst()

        let utterance = AVSpeechUtterance(string: next)
        utterance.voice = AVSpeechSynthesisVoice(language: "ru-RU")

        speechSynthesizer.speak(utterance)
    }
    
   
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer,
                           didFinish utterance: AVSpeechUtterance) {
        isSpeakingNow = false
        processSpeechQueue()
    }
    


    private func speakAlert(text: String) {
        guard text != lastSpokenReason else { return }
        lastSpokenReason = text

        speechQueue.append(text)
        processSpeechQueue()
    }
    
//    private func speakAlert(reason: DriverAlertReason) {
//        let text: String
//
//        switch reason {
//        case .eyesClosed:
//            text = "Глаза закрыты!"
//        case .headDown:
//            text = "Голова опущена!"
//        case .distractedLeft:
//            text = "Голова смотрит влево! Смотрите на дорогу! "
//        case .distractedRight:
//            text = "Голова смотрит вправо! Смотрите на дорогу!"
//        }
//        if speechSynthesizer.isSpeaking {
//            speechSynthesizer.stopSpeaking(at: .immediate)
//        }
//
//        let utterance = AVSpeechUtterance(string: text)
//        utterance.voice = AVSpeechSynthesisVoice(language: "ru-RU")
//        utterance.rate = 0.48
//
//        speechSynthesizer.speak(utterance)
//    }

    private func handleFace(_ face: VNFaceObservation) {
        faceMissingFrames = 0
        faceLostAlertActive = false

        guard let landmarks = face.landmarks,
              let leftEye = landmarks.leftEye,
              let rightEye = landmarks.rightEye else {
            return
        }

        updateHeadPose(landmarks: landmarks)

        let left = eyeAspectRatio(points: leftEye.normalizedPoints)
        let right = eyeAspectRatio(points: rightEye.normalizedPoints)
        let rawScore = (left + right) / 2
        
        let rawMouthOpenScore: CGFloat

        if let outerLips = landmarks.outerLips {
            rawMouthOpenScore = mouthOpenRatio(points: outerLips.normalizedPoints)
        } else {
            rawMouthOpenScore = 0
        }

        if smoothedMouthOpenScore == 0 {
            smoothedMouthOpenScore = rawMouthOpenScore
        } else {
            smoothedMouthOpenScore = 0.85 * smoothedMouthOpenScore + 0.15 * rawMouthOpenScore
        }

        if smoothedEye == 0 {
            smoothedEye = rawScore
        } else {
            smoothedEye = 0.85 * smoothedEye + 0.15 * rawScore
        }

        updateCalibrationIfNeeded(smoothedEye)
        updateFatigue(
            rawScore: rawScore,
            smoothedScore: smoothedEye,
            mouthOpenScore: smoothedMouthOpenScore
        )
    }
    
    private func updateHeadPose(landmarks: VNFaceLandmarks2D) {
        guard let leftEye = landmarks.leftEye,
              let rightEye = landmarks.rightEye,
              let nose = landmarks.nose else {
            return
        }

        let leftEyeCenter = averagePoint(leftEye.normalizedPoints)
        let rightEyeCenter = averagePoint(rightEye.normalizedPoints)
        let noseCenter = averagePoint(nose.normalizedPoints)

        let eyeCenter = CGPoint(
            x: (leftEyeCenter.x + rightEyeCenter.x) / 2,
            y: (leftEyeCenter.y + rightEyeCenter.y) / 2
        )

        let rawYaw = noseCenter.x - eyeCenter.x
        let rawPitch = noseCenter.y - eyeCenter.y

        let dy = rightEyeCenter.y - leftEyeCenter.y
        let dx = rightEyeCenter.x - leftEyeCenter.x
        let rawRoll = atan2(dy, dx)

        if smoothedYaw == 0 && smoothedPitch == 0 && smoothedRoll == 0 {
            smoothedYaw = rawYaw
            smoothedPitch = rawPitch
            smoothedRoll = rawRoll
        } else {
            smoothedYaw = headPoseSmoothing * smoothedYaw + (1 - headPoseSmoothing) * rawYaw
            smoothedPitch = headPoseSmoothing * smoothedPitch + (1 - headPoseSmoothing) * rawPitch
            smoothedRoll = headPoseSmoothing * smoothedRoll + (1 - headPoseSmoothing) * rawRoll
        }

        updateHeadPoseCalibrationIfNeeded(yaw: smoothedYaw, pitch: smoothedPitch)
    }

    private func updateHeadPoseCalibrationIfNeeded(yaw: CGFloat, pitch: CGFloat) {
        guard headPoseBaselineYaw == nil || headPoseBaselinePitch == nil else {
            return
        }

        headPoseCalibrationYawSamples.append(yaw)
        headPoseCalibrationPitchSamples.append(pitch)

        if headPoseCalibrationYawSamples.count >= 60 &&
            headPoseCalibrationPitchSamples.count >= 60 {

            headPoseBaselineYaw = average(headPoseCalibrationYawSamples)
            headPoseBaselinePitch = average(headPoseCalibrationPitchSamples)

            print(
                "[HEAD_POSE][CALIBRATION]",
                "yaw:", headPoseBaselineYaw ?? 0,
                "pitch:", headPoseBaselinePitch ?? 0
            )
        }
    }

    private func updateHeadPoseStates() -> (isDistracted: Bool, isDrowsy: Bool, lookingLeft: Bool, lookingRight: Bool, direction: HeadTurnDirection) {
        guard let baselineYaw = headPoseBaselineYaw,
              let baselinePitch = headPoseBaselinePitch else {
            return (false, false, false, false, .none)
        }

        let yawDelta = smoothedYaw - baselineYaw
        
        let yawDeadZone: CGFloat = 0.004

        let yawFiltered: CGFloat
        if abs(yawDelta) < yawDeadZone {
            yawFiltered = 0
        } else {
            yawFiltered = yawDelta
        }
        
        let pitchDelta = smoothedPitch - baselinePitch
        let rollAbs = abs(smoothedRoll)

        let lookingRight = yawFiltered >= yawRightDistractedThreshold
        let lookingLeft = yawFiltered <= -yawLeftDistractedThreshold

        let lookingAway = lookingRight || lookingLeft
        
        let headDown = pitchDelta <= pitchDownThreshold

        if lookingAway {
            distractedFrames += 1
        } else {
            distractedFrames = 0
        }

        if headDown {
            drowsyFrames += 1
        } else {
            drowsyFrames = 0
        }

        let isDistracted = distractedFrames >= distractedFramesThreshold
        let isDrowsy = drowsyFrames >= drowsyFramesThreshold
        
        let direction: HeadTurnDirection

        if lookingLeft {
            direction = .left
        } else if lookingRight {
            direction = .right
        } else {
            direction = .none
        }

        print(
            "[HEAD_POSE]",
            "yaw:", smoothedYaw,
            "pitch:", smoothedPitch,
            "roll:", smoothedRoll,
            "yawDelta:", yawDelta,
            "pitchDelta:", pitchDelta,
            "lookingLeft:", lookingLeft,
            "lookingRight:", lookingRight,
            "direction:", direction,
            "distractedFrames:", distractedFrames,
            "drowsyFrames:", drowsyFrames,
            "state:", driverFatigueState.rawValue
        )

        return (isDistracted, isDrowsy, lookingLeft, lookingRight, direction)
    }

    private func updateCalibrationIfNeeded(_ score: CGFloat) {
        guard calibratedOpenEye == nil else { return }

        if score > calibrationMinimumEyeSample {
            calibrationSamples.append(score)
        }

        if calibrationSamples.count >= 60 {
            let sorted = calibrationSamples.sorted()
            let start = Int(Double(sorted.count) * 0.6)
            let upperSamples = sorted[start..<sorted.count]
            let average = upperSamples.reduce(CGFloat(0), +) / CGFloat(upperSamples.count)

            calibratedOpenEye = max(average, 0.12)

            print("[FATIGUE][CALIBRATION] openEyeBaseline:", calibratedOpenEye ?? 0)
        }
    }
    
    private func speakEmergencyStop() {
        for i in 0..<3 {
            DispatchQueue.main.asyncAfter(deadline: .now() + Double(i) * 2.0) {
                self.triggerVoiceAlertIfNeeded(reason: .emergencyStop)
            }
        }
    }

    private func updateFatigue(
        rawScore: CGFloat,
        smoothedScore: CGFloat,
        mouthOpenScore: CGFloat
    ) {
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

        let dynamicClosedThreshold = max(minimumClosedEyeThreshold, openEye * closedEyeRatio)

        let stronglyClosed = rawScore < strongClosedEyeThreshold
        let normallyClosed = rawScore < dynamicClosedThreshold && smoothedScore < dynamicClosedThreshold

        let isClosed = stronglyClosed || normallyClosed
        
        let yawningNow = mouthOpenScore >= yawnMouthOpenThreshold

        if yawningNow {
            yawnFrames += 1
        } else {
            yawnFrames = 0
            yawnAlertActive = false
        }

        let yawningDetected = yawnFrames >= yawnFramesThreshold
        let effectiveEyesClosed = isClosed && !yawningDetected

        if effectiveEyesClosed {
            consecutiveClosedFrames += 1
            consecutiveOpenFramesAfterMicrosleep = 0
            
            if eyesClosedStartTime == nil {
                eyesClosedStartTime = Date()
            }

            if let start = eyesClosedStartTime,
               Date().timeIntervalSince(start) >= emergencyEyesClosedDuration,
               !emergencyStopTriggered {

                emergencyStopTriggered = true

                print("[EMERGENCY] Eyes closed > 4s")

                speakEmergencyStop()
            }

            if driverFatigueState == .critical {
                recoveringFromMicrosleep = true
            }
        } else {
            consecutiveClosedFrames = 0
            microsleepActive = false
            
            eyesClosedStartTime = nil
            emergencyStopTriggered = false

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

        eyeClosedWindow.append(effectiveEyesClosed)
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
        
        let headPoseState = updateHeadPoseStates()

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

        } else if headPoseState.isDrowsy {
            stableState = .drowsy

        } else if driverFatigueState == .drowsy && !headPoseState.isDrowsy {
            stableState = .normal

        } else if headPoseState.isDistracted {
            stableState = .distracted

        } else {
            stableState = updateStateMachine(effectiveMeasuredState)
        }
        
        DispatchQueue.main.async {
            let previousState = self.driverFatigueState

            self.eyeOpenScore = rawScore
            self.smoothedEyeOpenScore = smoothedScore
            self.perclos = ratio
            
            self.mouthOpenScore = mouthOpenScore
            self.isYawning = yawningDetected
            self.fatigueScore = self.calculateFatigueScore(
                perclos: ratio,
                microsleepActive: self.microsleepActive,
                isDrowsy: headPoseState.isDrowsy,
                isDistracted: headPoseState.isDistracted,
                isYawning: yawningDetected
            )
            
            self.driverFatigueState = stableState
            
            if stableState == .distracted {
                if headPoseState.direction != .none &&
                   headPoseState.direction != self.lastSpokenHeadTurnDirection {

                    self.lastSpokenHeadTurnDirection = headPoseState.direction

                    switch headPoseState.direction {
                    case .left:
                        self.triggerVoiceAlertIfNeeded(reason: .distractedLeft)
                    case .right:
                        self.triggerVoiceAlertIfNeeded(reason: .distractedRight)
                    case .none:
                        break
                    }
                }
            } else {
                self.lastSpokenHeadTurnDirection = .none
            }
            
            if yawningDetected && !self.yawnAlertActive && stableState != .warning {
                self.yawnAlertActive = true
                self.triggerVoiceAlertIfNeeded(reason: .yawning)
            }

            self.speakForStateTransition(
                from: previousState,
                to: stableState,
                headPoseState: headPoseState
            )
        }
        
//        speakCriticalFatigueWarningIfNeeded(
//            previousState: previousState,
//            newState: stableState
//        )

        if stableState != lastPrintedState {
            print("[FATIGUE][STATE] \(lastPrintedState.rawValue) -> \(stableState.rawValue)")
            lastPrintedState = stableState
        }

        print(
            "[FATIGUE]",
            "eye:", rawScore,
            "smooth:", smoothedScore,
            "threshold:", dynamicClosedThreshold,
            "effectiveClosed:", effectiveEyesClosed,
            "yawning:", yawningDetected,
            "perclos:", ratio,
            "state:", stableState.rawValue,
            "microsleep:", microsleepActive,
            "closedFrames:", consecutiveClosedFrames,
            "openRecoveryFrames:", consecutiveOpenFramesAfterMicrosleep,
            "recovering:", recoveringFromMicrosleep
        )
    }
    
//    private func speakCriticalFatigueWarningIfNeeded(previousState: DriverFatigueState, newState: DriverFatigueState) {
//        guard previousState != .critical && newState == .critical else { return }
//
//        DispatchQueue.main.async {
//            guard !self.speechSynthesizer.isSpeaking else { return }
//
//            let utterance = AVSpeechUtterance(string: "Водитель засыпает. Останови-тесь")
//            utterance.rate = 0.5
//            utterance.volume = 1.0
//            utterance.voice = AVSpeechSynthesisVoice(language: "ru-RU")
//
//            self.speechSynthesizer.speak(utterance)
//        }
//    }
    private func calculateFatigueScore(
        perclos: Double,
        microsleepActive: Bool,
        isDrowsy: Bool,
        isDistracted: Bool,
        isYawning: Bool
    ) -> Double {
        let now = Date()

        var targetScore = 0.0

        let perclosScore = min(perclos / criticalPerclosThreshold, 1.0) * 45.0
        targetScore += perclosScore

        if microsleepActive {
            targetScore = max(targetScore, 95.0)
        }

        if isDrowsy {
            targetScore = max(targetScore, 75.0)
        }

        if isYawning {
            targetScore = max(targetScore, 45.0)
        }

        if isDistracted {
            targetScore = max(targetScore, 30.0)
        }

        targetScore = min(targetScore, 100.0)

        if targetScore > rawFatigueScore {
            rawFatigueScore = min(rawFatigueScore + fatigueScoreRiseStep, targetScore)
        } else {
            let isHoldActive = fatigueScoreHoldUntil.map { now < $0 } ?? false

            if !isHoldActive {
                rawFatigueScore = max(rawFatigueScore - fatigueScoreFallStep, targetScore)
            }
        }

        if rawFatigueScore >= fatigueScoreHoldThreshold {
            fatigueScoreHoldUntil = now.addingTimeInterval(fatigueScoreHoldDuration)
        }

        return min(max(rawFatigueScore, 0), 100)
    }
    
    private func measuredFatigueState(perclos: Double) -> DriverFatigueState {
        switch driverFatigueState {

        case .normal, .distracted, .drowsy:
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
    
    private func speakForStateTransition(
        from previousState: DriverFatigueState,
        to newState: DriverFatigueState,
        headPoseState: (
            isDistracted: Bool,
            isDrowsy: Bool,
            lookingLeft: Bool,
            lookingRight: Bool,
            direction: HeadTurnDirection
        )
    ){
        guard previousState != newState else {
            return
        }

        switch newState {
        case .critical:
            triggerVoiceAlertIfNeeded(reason: .eyesClosed)

        case .drowsy:
            triggerVoiceAlertIfNeeded(reason: .headDown)

        case .distracted:
            break

        case .warning, .normal:
            break
        }
    }
    
    private func speakEmergencyAlert(text: String) {
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: "ru-RU")
        utterance.volume = 1.0
        utterance.rate = 0.55
        utterance.pitchMultiplier = 1.15

        speechSynthesizer.speak(utterance)
    }
    
    private func triggerVoiceAlertIfNeeded(reason: DriverAlertReason) {
        let now = Date()
        
        if reason == .emergencyStop {
            speechSynthesizer.stopSpeaking(at: .immediate)
            lastVoiceAlertAt = Date()
            lastVoiceAlertReason = reason
            speakEmergencyAlert(text: "Срочно останови-тесь!")
            return
        }

        if let lastTime = lastVoiceAlertAt,
           let lastReason = lastVoiceAlertReason {

            let isSameReason = lastReason == reason
            let cooldown: TimeInterval = {
                switch reason {
                case .yawning:
                    return 30
                case .emergencyStop:
                    return 0
                default:
                    return voiceAlertCooldown
                }
            }()
            let withinCooldown = now.timeIntervalSince(lastTime) < cooldown

            if isSameReason && withinCooldown {
                return
            }
        }

        lastVoiceAlertAt = now
        lastVoiceAlertReason = reason

        let text: String

        switch reason {
        case .eyesClosed:
            text = "Глаза закрыты!"

        case .headDown:
            text = "Голова опущена!"

        case .distractedLeft:
            text = "Голова смотрит влево! Смотрите на дорогу!"

        case .distractedRight:
            text = "Голова смотрит вправо! Смотрите на дорогу!"
            
        case .yawning:
            text = "Водитель зевает!"

        case .faceLost:
            text = "Лицо не видно! Смотрите на дорогу!"
            
        case .emergencyStop:
            text = "Срочно остановитесь!"
        
        }

        DispatchQueue.main.async {
            self.speakAlert(text: text)
        }
    }

    private func handleNoFace() {
        faceMissingFrames += 1
        distractedFrames += 1

        consecutiveClosedFrames = 0
        consecutiveOpenFramesAfterMicrosleep = 0
        microsleepActive = false
        recoveringFromMicrosleep = false

        eyeClosedWindow.append(false)
        if eyeClosedWindow.count > windowSize {
            eyeClosedWindow.removeFirst()
        }

        let ratio = eyeClosedWindow.isEmpty
            ? 0
            : Double(eyeClosedWindow.filter { $0 }.count) / Double(eyeClosedWindow.count)

        let noFaceDistracted = faceMissingFrames >= faceMissingFramesThreshold

        let stableState: DriverFatigueState

        if noFaceDistracted {
            stableState = .distracted
        } else {
            stableState = driverFatigueState
        }

        DispatchQueue.main.async {
            let previousState = self.driverFatigueState

            self.perclos = ratio
            self.driverFatigueState = stableState
            self.fatigueScore = self.calculateFatigueScore(
                perclos: ratio,
                microsleepActive: false,
                isDrowsy: false,
                isDistracted: stableState == .distracted,
                isYawning: self.isYawning
            )

            if noFaceDistracted && !self.faceLostAlertActive {
                self.faceLostAlertActive = true
                self.triggerVoiceAlertIfNeeded(reason: .faceLost)
            }

            if previousState != stableState {
                print("[FATIGUE][STATE]", previousState.rawValue, "->", stableState.rawValue)
            }
        }

        if stableState != lastPrintedState {
            print("[FATIGUE][STATE] \(lastPrintedState.rawValue) -> \(stableState.rawValue)")
            lastPrintedState = stableState
        }

        print(
            "[FATIGUE][NO_FACE]",
            "faceMissingFrames:", faceMissingFrames,
            "perclos:", ratio,
            "state:", stableState.rawValue
        )
    }
    
    private func mouthOpenRatio(points: [CGPoint]) -> CGFloat {
        guard points.count >= 4 else {
            return 0
        }

        let minX = points.map { $0.x }.min() ?? 0
        let maxX = points.map { $0.x }.max() ?? 0
        let minY = points.map { $0.y }.min() ?? 0
        let maxY = points.map { $0.y }.max() ?? 0

        let width = maxX - minX
        let height = maxY - minY

        guard width > 0 else {
            return 0
        }

        return height / width
    }

    private func eyeAspectRatio(points: [CGPoint]) -> CGFloat {
        guard points.count >= 6 else { return 0 }

        let v1 = distance(points[1], points[5])
        let v2 = distance(points[2], points[4])
        let h = distance(points[0], points[3])

        guard h > 0 else { return 0 }

        return (v1 + v2) / (2 * h)
    }
    
    private func averagePoint(_ points: [CGPoint]) -> CGPoint {
        guard !points.isEmpty else {
            return .zero
        }

        let sum = points.reduce(CGPoint.zero) { partial, point in
            CGPoint(
                x: partial.x + point.x,
                y: partial.y + point.y
            )
        }

        return CGPoint(
            x: sum.x / CGFloat(points.count),
            y: sum.y / CGFloat(points.count)
        )
    }

    private func average(_ values: [CGFloat]) -> CGFloat {
        guard !values.isEmpty else {
            return 0
        }

        return values.reduce(0, +) / CGFloat(values.count)
    }

    private func distance(_ p1: CGPoint, _ p2: CGPoint) -> CGFloat {
        hypot(p1.x - p2.x, p1.y - p2.y)
    }
    
    func reset() {
        print("[DMS] reset")

        smoothedEye = 0
        calibratedOpenEye = nil
        calibrationSamples.removeAll()

        eyeClosedWindow.removeAll()
        
        smoothedMouthOpenScore = 0
        yawnFrames = 0
        yawnAlertActive = false
        mouthOpenScore = 0
        isYawning = false
        fatigueScore = 0
        
        rawFatigueScore = 0
        fatigueScoreHoldUntil = nil
        lastFatigueScoreUpdateAt = nil
        
        consecutiveClosedFrames = 0
        microsleepActive = false

        consecutiveOpenFramesAfterMicrosleep = 0
        recoveringFromMicrosleep = false

        candidateState = .normal
        candidateFrameCount = 0

        suppressCriticalFramesAfterRecovery = 0

        driverFatigueState = .normal
       

        // head pose
        faceMissingFrames = 0
        distractedFrames = 0
        drowsyFrames = 0

        smoothedYaw = 0
        smoothedPitch = 0
        smoothedRoll = 0

        headPoseBaselineYaw = nil
        headPoseBaselinePitch = nil
        headPoseCalibrationYawSamples.removeAll()
        headPoseCalibrationPitchSamples.removeAll()

        // voice
        lastVoiceAlertAt = nil
        lastVoiceAlertReason = nil
        
        lastSpokenHeadTurnDirection = .none
        
        speechQueue.removeAll()
        isSpeakingNow = false
        lastSpokenReason = nil
        speechSynthesizer.stopSpeaking(at: .immediate)
        
        rawFatigueScore = 0
        
        faceMissingFrames = 0
        faceLostAlertActive = false
    }
}
