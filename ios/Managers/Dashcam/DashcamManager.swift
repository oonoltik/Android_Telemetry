import Foundation
import AVFoundation
import UIKit
import Combine
import CoreLocation


@MainActor
final class DashcamManager: NSObject, ObservableObject {
    
    // DEBUG LOG EXPORT
    func debug_shareSessionLog() {
        let url = FileLogger.shared.currentLogURL()
        let vc = UIActivityViewController(activityItems: [url], applicationActivities: nil)

        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let root = scene.windows.first?.rootViewController else { return }

        root.present(vc, animated: true)
    }

    func debug_printSessionLog() {
        let url = FileLogger.shared.currentLogURL()
        print("LOG FILE =", url.path)

        if let text = try? String(contentsOf: url, encoding: .utf8) {
            print("===== SESSION.LOG =====")
            print(text)
            print("===== END LOG =====")
        } else {
            print("failed to read log")
        }
    }
    
    nonisolated private static func __dbg(_ message: String) {
            print(message)
            FileLogger.shared.log(message)
        }
    
    @Published private(set) var state: DashcamRecordingState = .idle {
        didSet {
            sensorManager.suppressAutoFinishWhileDashcamActive =
                (state == .recording || state == .preparing || state == .stopping)
        }
    }

    @Published private(set) var previewState: DashcamPreviewState = .hidden
    @Published private(set) var timerText: String = "00:00:00"
    @Published private(set) var activeVideoSessionId: String?
    @Published private(set) var lastError: DashcamError?

    @Published private(set) var stopProgressText: String?
    @Published private(set) var stopProgressValue: Double = 0

    private let sensorManager: SensorManager
    private let networkManager: NetworkManager
    private let archiveStore: VideoArchiveStore
    private let tripCoordinator: DashcamTripCoordinator
    private var crashCoordinator: CrashClipCoordinator!
    private let quotaManager: StorageQuotaManager
    private let capabilityService: CameraCapabilityService
    private let settingsStore: DashcamSettingsStore

    private let captureSession = AVCaptureSession()
    private let movieOutput = AVCaptureMovieFileOutput()
    private let sessionQueue = DispatchQueue(label: "dashcam.capture.session.queue")

    private var timer: Timer?
    private var segmentCheckTimer: Timer?

    private var currentSegmentId: String?
    private var currentSegmentURL: URL?

    private var currentSegmentStartedAt: Date?
    private var recordingStartedAt: Date?
    private var recordingStartCoordinate: CLLocationCoordinate2D?

    private var pendingStopTrigger: DashcamStopTrigger?
    private var isSegmentFinishing = false

    private var isCrashUploadDrainScheduled = false
    private var pendingRuntimeRecoveryTask: Task<Void, Never>?
    private var lastRuntimeRecoveryAttemptAt: Date?

    // Оставляем только как пассивный snapshot для final camera log / отладки,
    // но без старой crash-оркестрации внутри manager.
    private var crashEventCancellable: AnyCancellable?
    private var lastCrashEvent: CrashEvent?
    private var recentCrashAt: Date?

    private var segmentWatchdogTimer: Timer?
    private var lastSegmentFinishRequestedAt: Date?
    private var captureObserversInstalled = false
    private var sessionInterrupted = false
    private var backgroundTaskId: UIBackgroundTaskIdentifier = .invalid
    private var backgroundStopStartedAt: Date?
    private var isFinalizingStop = false
    private var shouldStopAfterCurrentSegment = false
    private var quotaStopErrorMessage: String?

    private var stopProgressTimer: Timer?
    private var lastStoppedVideoSessionId: String?
    private var lastStoppedVideoSessionEndedAt: Date?
    private let postStopCrashGraceSec: TimeInterval = 15

    private var shouldResumeAfterInterruption = false
    private var pendingInterruptionResumeTask: Task<Void, Never>?
    private var lastSegmentStartAt: Date?

   
    
    init(
        sensorManager: SensorManager,
        networkManager: NetworkManager,
        archiveStore: VideoArchiveStore,
        tripCoordinator: DashcamTripCoordinator,
        quotaManager: StorageQuotaManager,
        capabilityService: CameraCapabilityService,
        settingsStore: DashcamSettingsStore
    ) {
        self.sensorManager = sensorManager
        self.networkManager = networkManager
        self.archiveStore = archiveStore
        self.tripCoordinator = tripCoordinator
        self.quotaManager = quotaManager
        self.capabilityService = capabilityService
        self.settingsStore = settingsStore
        super.init()

        sensorManager.suppressAutoFinishWhileDashcamActive = false
        registerCaptureObservers()
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
        segmentWatchdogTimer?.invalidate()
        pendingRuntimeRecoveryTask?.cancel()
        pendingInterruptionResumeTask?.cancel()
    }

    func crashVideoSessionIdForLateEvents() -> String? {
        if let activeVideoSessionId {
            return activeVideoSessionId
        }

        guard let lastStoppedVideoSessionId,
              let lastStoppedVideoSessionEndedAt else {
            return nil
        }

        if Date().timeIntervalSince(lastStoppedVideoSessionEndedAt) <= postStopCrashGraceSec {
            return lastStoppedVideoSessionId
        }

        return nil
    }

    var isVideoModeActive: Bool {
        state == .recording || state == .preparing || state == .stopping
    }

    var allowsManualTripStartDuringVideo: Bool {
        isVideoModeActive && tripCoordinator.currentTripOwnership() == .videoImplicit
    }

    var shouldBlockTripStopButton: Bool {
        isVideoModeActive && tripCoordinator.currentTripOwnership() == .videoImplicit
    }

    func currentContext() -> DashcamContextSnapshot {
        DashcamContextSnapshot(
            videoSessionId: activeVideoSessionId,
            tripOwnership: tripCoordinator.currentTripOwnership(),
            isRecording: state == .recording,
            previewState: previewState
        )
    }

    func attachCrashCoordinator(_ coordinator: CrashClipCoordinator) {
        self.crashCoordinator = coordinator
        coordinator.attach()
        bindCrashEventsForSnapshotOnly()
    }

    func requestPermissionsIfNeeded() async throws {
        let cameraGranted = await capabilityService.requestCameraAccess()
        guard cameraGranted else { throw DashcamError.cameraPermissionDenied }

        if settingsStore.enableMicrophone {
            let micGranted = await capabilityService.requestMicrophoneAccess()
            guard micGranted else { throw DashcamError.microphonePermissionDenied }
        }
    }

    private func bindCrashEventsForSnapshotOnly() {
        crashEventCancellable = sensorManager.crashEventPublisher
            .sink { [weak self] event in
                guard let self else { return }

                DispatchQueue.main.async {
                    self.lastCrashEvent = event
                    self.recentCrashAt = Date()
                    print("[DASHCAM] crash snapshot received at \(event.at)")
                    Self.__dbg("[DASHCAM] crash snapshot received at \(event.at)")
                }

                self.sessionQueue.async {
                    self.requestSegmentFinishForCrash()
                }
            }
    }

    private func scheduleCrashUploadDrain(after delay: TimeInterval = 1.5) {
        
        guard !isCrashUploadDrainScheduled else { return }
        isCrashUploadDrainScheduled = true

        Task { @MainActor in
            let ns = UInt64(max(0, delay) * 1_000_000_000)
            if ns > 0 {
                try? await Task.sleep(nanoseconds: ns)
            }

            defer { self.isCrashUploadDrainScheduled = false }

            guard self.state == .idle || self.state == .stopping || self.state == .recording else { return }

            Self.__dbg("[DASHCAM][DRAIN] scheduled crash upload drain state=\(self.state)")
            await self.crashCoordinator.attemptPendingServerVideoSessionStarts()
            await self.crashCoordinator.attemptPendingCrashMetadataUpload()
            await self.crashCoordinator.attemptPendingCrashVideoUploads()
        }
    }

    private func startStopProgressUI() {
        stopStopProgressUI()

        stopProgressText = "Идет сохранение записи"
        stopProgressValue = 0.05

        stopProgressTimer = Timer.scheduledTimer(withTimeInterval: 0.12, repeats: true) { [weak self] _ in
            guard let self else { return }

            if self.stopProgressValue < 0.9 {
                self.stopProgressValue += 0.04
            } else {
                self.stopProgressValue = 0.9
            }
        }
    }

    private func finishStopProgressUI() {
        stopProgressTimer?.invalidate()
        stopProgressTimer = nil
        stopProgressValue = 1.0
    }

    private func stopStopProgressUI() {
        stopProgressTimer?.invalidate()
        stopProgressTimer = nil
        stopProgressText = nil
        stopProgressValue = 0
    }

    private func finalizeStoppedSessionWithFallback(
        trigger: DashcamStopTrigger,
        fallbackMessage: String? = nil
    ) async {
        await finalizeStoppedSession(trigger: trigger)

        if state != .idle {
            if let fallbackMessage {
                lastError = .unknown(fallbackMessage)
            }
            forceResetAfterInterruptedStop()
        }
    }

    private func registerCaptureObservers() {
        guard !captureObserversInstalled else { return }
        captureObserversInstalled = true

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleCaptureSessionInterrupted(_:)),
            name: .AVCaptureSessionWasInterrupted,
            object: captureSession
        )

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleCaptureSessionInterruptionEnded(_:)),
            name: .AVCaptureSessionInterruptionEnded,
            object: captureSession
        )

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleCaptureSessionRuntimeError(_:)),
            name: .AVCaptureSessionRuntimeError,
            object: captureSession
        )
    }

    private func beginBackgroundTaskIfNeeded() {
        guard backgroundTaskId == .invalid else { return }

        backgroundTaskId = UIApplication.shared.beginBackgroundTask(withName: "DashcamStop") { [weak self] in
            guard let self else { return }

            Task { @MainActor in
                if self.state == .stopping || self.state == .recording || self.state == .preparing {
                    await self.finalizeStoppedSessionWithFallback(
                        trigger: self.pendingStopTrigger ?? .appBackground,
                        fallbackMessage: "Не удалось корректно завершить видеозапись в фоне"
                    )
                } else {
                    self.forceResetAfterInterruptedStop()
                }
            }
        }
    }

    private func endBackgroundTaskIfNeeded() {
        guard backgroundTaskId != .invalid else { return }
        UIApplication.shared.endBackgroundTask(backgroundTaskId)
        backgroundTaskId = .invalid
    }

    private func forceResetAfterInterruptedStop() {
        stopStopProgressUI()
        timer?.invalidate()
        segmentCheckTimer?.invalidate()
        timer = nil
        segmentCheckTimer = nil
        stopSegmentWatchdog()

        pendingRuntimeRecoveryTask?.cancel()
        pendingRuntimeRecoveryTask = nil
        lastRuntimeRecoveryAttemptAt = nil
        isCrashUploadDrainScheduled = false

        UIApplication.shared.isIdleTimerDisabled = false

        if captureSession.isRunning {
            sessionQueue.async {
                self.captureSession.stopRunning()
            }
        }

        activeVideoSessionId = nil
        currentSegmentId = nil
        currentSegmentURL = nil
        currentSegmentStartedAt = nil
        recordingStartedAt = nil
        timerText = "00:00:00"
        pendingStopTrigger = nil
        isSegmentFinishing = false
        lastCrashEvent = nil
        recentCrashAt = nil
        backgroundStopStartedAt = nil
        state = .idle

        shouldStopAfterCurrentSegment = false
        quotaStopErrorMessage = nil

        Self.__dbg("[DASHCAM][STOP] forceResetAfterInterruptedStop complete lastStoppedVideoSessionId=\(lastStoppedVideoSessionId ?? "nil")")
    }
    
    private func attemptResumeAfterInterruption(force: Bool = false) async {
        let appState = UIApplication.shared.applicationState
        guard appState != .background else {
            if force {
                print("[DashcamCapture] forced resume deferred: app in background")
                Self.__dbg("[DashcamCapture] forced resume deferred: app in background")
            }
            return
        }

        guard state == .recording || state == .preparing || state == .stopping else {
            print("[DashcamCapture] resume skipped: invalid state \(state)")
            return
        }

        guard activeVideoSessionId != nil else {
            print("[DashcamCapture] resume skipped: no activeVideoSessionId")
            return
        }

        if isSegmentFinishing && !force {
            print("[DashcamCapture] resume skipped: segment still finishing")
            return
        }

        if movieOutput.isRecording {
            print("[DashcamCapture] resume skipped: movieOutput already recording")
            state = .recording
            return
        }

        do {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                sessionQueue.async {
                    if !self.captureSession.isRunning {
                        self.captureSession.startRunning()
                    }
                    continuation.resume()
                }
            }

            currentSegmentId = nil
            currentSegmentURL = nil
            currentSegmentStartedAt = nil
            isSegmentFinishing = false
            lastSegmentFinishRequestedAt = nil

            try startNextSegment()
            state = .recording

            print("[DashcamCapture] recording resumed after interruption")
            Self.__dbg("[DashcamCapture] recording resumed after interruption")
        } catch {
            print("[DashcamCapture] forced resume failed: \(error)")
            Self.__dbg("[DashcamCapture] forced resume failed: \(error)")
            lastError = .unknown("Не удалось возобновить запись после interruption: \(error.localizedDescription)")
            await finalizeStoppedSession(trigger: .fatalError)
        }
    }

    @objc private func handleCaptureSessionInterrupted(_ notification: Notification) {
        sessionInterrupted = true

        let reasonValue = notification.userInfo?[AVCaptureSessionInterruptionReasonKey] as? NSNumber
        let reason = reasonValue.flatMap { AVCaptureSession.InterruptionReason(rawValue: $0.intValue) }

        print("[DashcamCapture] interrupted reason=\(String(describing: reason))")
        Self.__dbg("[DashcamCapture] interrupted reason=\(String(describing: reason))")

        shouldResumeAfterInterruption = (state == .recording || state == .preparing)

        pendingInterruptionResumeTask?.cancel()
        pendingInterruptionResumeTask = Task { [weak self] in
            guard let self else { return }

            try? await Task.sleep(nanoseconds: 4_000_000_000)

            guard !Task.isCancelled else { return }
            guard self.shouldResumeAfterInterruption else { return }
            guard self.state == .recording || self.state == .preparing else { return }
            guard !self.movieOutput.isRecording else { return }

            let appState = UIApplication.shared.applicationState
            guard appState != .background else {
                print("[DashcamCapture] interruption watchdog skipped: app in background")
                return
            }

            print("[DashcamCapture] interruption watchdog fired -> forcing resume, appState=\(appState.rawValue)")
            Self.__dbg("[DashcamCapture] interruption watchdog fired -> forcing resume, appState=\(appState.rawValue)")
            await self.attemptResumeAfterInterruption(force: true)
        }
    }
    
    @objc private func handleCaptureSessionInterruptionEnded(_ notification: Notification) {
        print("[DashcamCapture] interruption ended")
        Self.__dbg("[DashcamCapture] interruption ended")
        sessionInterrupted = false

        guard shouldResumeAfterInterruption else { return }
        shouldResumeAfterInterruption = false

        pendingInterruptionResumeTask?.cancel()
        pendingInterruptionResumeTask = nil

        guard state == .recording || state == .preparing || state == .stopping else { return }

        Task { @MainActor in
            await attemptResumeAfterInterruption()
        }
    }

    @objc private func handleCaptureSessionRuntimeError(_ notification: Notification) {
        let nsError = notification.userInfo?[AVCaptureSessionErrorKey] as? NSError
        let text = nsError?.localizedDescription ?? "Неизвестная ошибка камеры"

        print("[DashcamCapture] runtime error: \(text)")
        Self.__dbg("[DashcamCapture] runtime error: \(text)")

        guard state == .recording || state == .preparing else { return }

        if sessionInterrupted, let nsError, nsError.domain == AVFoundationErrorDomain {
            print("[DashcamCapture] runtime error during interruption ignored: \(nsError.code)")
            return
        }

        let now = Date()
        if let lastAttempt = lastRuntimeRecoveryAttemptAt,
           now.timeIntervalSince(lastAttempt) < 5 {
            print("[DashcamCapture] runtime error repeated too soon, stopping session")
            lastError = .unknown("Ошибка камеры: \(text)")

            Task { @MainActor in
                let trigger = self.pendingStopTrigger ?? .fatalError
                await self.finalizeStoppedSession(trigger: trigger)
            }
            return
        }

        lastRuntimeRecoveryAttemptAt = now
        pendingRuntimeRecoveryTask?.cancel()

        pendingRuntimeRecoveryTask = Task { @MainActor in
            guard self.state == .recording || self.state == .preparing else { return }

            print("[DashcamCapture] attempting soft recovery")
            Self.__dbg("[DashcamCapture] attempting soft recovery")

            self.stopSegmentWatchdog()
            self.isSegmentFinishing = false
            self.lastSegmentFinishRequestedAt = nil
            self.currentSegmentURL = nil
            self.currentSegmentStartedAt = nil
            self.currentSegmentId = nil

            do {
                try await withCheckedThrowingContinuation { continuation in
                    self.sessionQueue.async {
                        if self.captureSession.isRunning {
                            self.captureSession.stopRunning()
                        }

                        self.captureSession.startRunning()
                        continuation.resume(returning: ())
                    }
                }

                guard self.state == .recording || self.state == .preparing else { return }
                guard UIApplication.shared.applicationState == .active else {
                    print("[DashcamCapture] app not active, skipping immediate segment restart after recovery")
                    return
                }

                try self.startNextSegment()
                self.state = .recording
                print("[DashcamCapture] soft recovery succeeded")
                Self.__dbg("[DashcamCapture] soft recovery succeeded")
            } catch {
                self.lastError = .unknown("Ошибка камеры: \(text). Восстановление не удалось: \(error.localizedDescription)")
                let trigger = self.pendingStopTrigger ?? .fatalError
                await self.finalizeStoppedSession(trigger: trigger)
            }
        }
    }

    func startVideoMode(trigger: DashcamStartTrigger) async throws {
        guard state == .idle else { return }

        print("[DASHCAM][START] startVideoMode begin trigger=\(trigger.rawValue)")
        Self.__dbg("[DASHCAM][START] startVideoMode begin trigger=\(trigger.rawValue)")
        state = .preparing
        lastStoppedVideoSessionId = nil
        lastStoppedVideoSessionEndedAt = nil
        sensorManager.suppressAutoFinishWhileDashcamActive = true
        lastError = nil
        pendingStopTrigger = nil
        lastCrashEvent = nil
        recentCrashAt = nil

        do {
            print("[DASHCAM][START] ensure quota")
            Self.__dbg("[DASHCAM][START] ensure quota")
            try await quotaManager.ensureCanStartRecording()

            print("[DASHCAM][START] validate rear camera")
            Self.__dbg("[DASHCAM][START] validate rear camera")
            try capabilityService.validateRearCameraAvailable()

            print("[DASHCAM][START] ensure trip for video start")
            Self.__dbg("[DASHCAM][START] ensure trip for video start")
            let ownership = try await tripCoordinator.ensureTripForVideoStart()

            print("[DASHCAM][START] configure capture session")
            Self.__dbg("[DASHCAM][START] configure capture session")
            try configureSessionIfNeeded()

            let linkedTrip = tripCoordinator.currentTripSessionId()
            print("[DASHCAM][START] create archive video session linkedTrip=\(linkedTrip ?? "nil")")
            Self.__dbg("[DASHCAM][START] create archive video session linkedTrip=\(linkedTrip ?? "nil")")
            let sessionId = try archiveStore.createVideoSession(
                startedAt: Date(),
                linkedTripSessionId: linkedTrip
            )

            activeVideoSessionId = sessionId
            recordingStartedAt = Date()
            recordingStartCoordinate = sensorManager.latestKnownLocationCoordinate()

            UIApplication.shared.isIdleTimerDisabled = true

            print("[DASHCAM][START] start captureSession running")
            Self.__dbg("[DASHCAM][START] start captureSession running")
            try await startCaptureSessionWithTimeout()

            print("[DASHCAM][START] configure audio session after capture start")
            Self.__dbg("[DASHCAM][START] configure audio session after capture start")
            try configureAudioSessionIfNeeded()

            print("[DASHCAM][START] start first segment")
            Self.__dbg("[DASHCAM][START] start first segment")
            try startNextSegment()

            state = .recording
            startTimers()
            print("[DASHCAM][START] recording started sessionId=\(sessionId)")
            Self.__dbg("[DASHCAM][START] recording started sessionId=\(sessionId)")

            Task {
                print("[DASHCAM][START] post /video/session/start")
                Self.__dbg("[DASHCAM][START] post /video/session/start")

                crashCoordinator.markServerVideoSessionPending(
                    localVideoSessionId: sessionId,
                    tripSessionId: linkedTrip,
                    startedAt: Date(),
                    deviceId: sensorManager.deviceIdForDisplay,
                    driverId: sensorManager.driverId,
                    tripSourceRaw: (ownership == .videoImplicit ? "video_implicit" : "manual"),
                    cameraMode: "rear",
                    audioEnabled: settingsStore.enableMicrophone,
                    appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String,
                    iosVersion: UIDevice.current.systemVersion,
                    deviceModel: UIDevice.current.model
                )

                do {
                    try await networkManager.startVideoSession(
                        VideoSessionStartRequest(
                                        video_session_id: sessionId,
                                        device_id: sensorManager.deviceIdForDisplay,
                                        driver_id: sensorManager.driverId,
                                        started_at: ISO8601DateFormatter().string(from: Date()),
                                        linked_trip_session_id: linkedTrip,
                                        trip_source: (ownership == .videoImplicit ? .videoImplicit : .manual),
                                        camera_mode: "rear",
                                        audio_enabled: settingsStore.enableMicrophone,
                                        app_version: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String,
                                        ios_version: UIDevice.current.systemVersion,
                                        device_model: UIDevice.current.model
                                    )
                                )
                    crashCoordinator.markServerVideoSessionConfirmed(localVideoSessionId: sessionId)
                    Self.__dbg("[DASHCAM][START] /video/session/start SUCCESS sessionId=\(sessionId)")
                } catch {
                    print("[DASHCAM][START] /video/session/start deferred, error=\(error)")
                    Self.__dbg("[DASHCAM][START] /video/session/start deferred, error=\(error)")
                    Task {
                        await crashCoordinator.attemptPendingServerVideoSessionStarts()
                    }
                }
                        }
        } catch {
            print("[DASHCAM][START] FAILED error=\(error)")
            Self.__dbg("[DASHCAM][START] FAILED error=\(error)")
            lastError = (error as? DashcamError) ?? .unknown("Не удалось начать видеозапись: \(error.localizedDescription)")
            sensorManager.suppressAutoFinishWhileDashcamActive = false
            UIApplication.shared.isIdleTimerDisabled = false
            state = .idle
            throw error
        }
    }
    
    private func startCaptureSessionWithTimeout() async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let lock = NSLock()
            var resumed = false

            func finish(_ result: Result<Void, Error>) {
                lock.lock()
                defer { lock.unlock() }
                guard !resumed else { return }
                resumed = true
                continuation.resume(with: result)
            }

            let timeoutWorkItem = DispatchWorkItem {
                finish(.failure(DashcamError.unknown("captureSession.startRunning timeout")))
            }

            DispatchQueue.global().asyncAfter(deadline: .now() + 5, execute: timeoutWorkItem)

            sessionQueue.async {
                print("[DASHCAM][START] captureSession.startRunning() begin")
                Self.__dbg("[DASHCAM][START] captureSession.startRunning() begin")

                if self.captureSession.isRunning {
                    print("[DASHCAM][START] captureSession already running")
                    Self.__dbg("[DASHCAM][START] captureSession already running")
                    timeoutWorkItem.cancel()
                    finish(.success(()))
                    return
                }

                self.captureSession.startRunning()

                print("[DASHCAM][START] captureSession.startRunning() end isRunning=\(self.captureSession.isRunning)")
                Self.__dbg("[DASHCAM][START] captureSession.startRunning() end isRunning=\(self.captureSession.isRunning)")

                timeoutWorkItem.cancel()

                if self.captureSession.isRunning {
                    finish(.success(()))
                } else {
                    finish(.failure(DashcamError.unknown("captureSession did not start running")))
                }
            }
        }

        guard captureSession.isRunning else {
            throw DashcamError.unknown("captureSession did not start running")
        }
    }
    
    private func evaluateQuotaForNextSegment() async {
        guard state == .recording else { return }
        guard !shouldStopAfterCurrentSegment else { return }

        do {
            let canContinue = try await quotaManager.canContinueRecordingAfterSegmentCommit()
            if !canContinue {
                shouldStopAfterCurrentSegment = true
                quotaStopErrorMessage = DashcamError.insufficientSpaceForNextSegment.errorDescription
            }
        } catch let error as DashcamError {
            shouldStopAfterCurrentSegment = true
            quotaStopErrorMessage = error.errorDescription
        } catch {
            shouldStopAfterCurrentSegment = true
            quotaStopErrorMessage = DashcamError.insufficientSpaceForNextSegment.errorDescription
        }
    }

    func stopVideoMode(trigger: DashcamStopTrigger) async {
        guard state == .recording || state == .preparing else { return }

        Self.__dbg("[DASHCAM][STOP] stopVideoMode begin trigger=\(trigger.rawValue) state=\(state)")
        state = .stopping
        pendingStopTrigger = trigger
        startStopProgressUI()

        timer?.invalidate()
        segmentCheckTimer?.invalidate()
        timer = nil
        segmentCheckTimer = nil

        if movieOutput.isRecording {
            requestSegmentFinish()
            startSegmentWatchdog()
            return
        }

        await finalizeStoppedSession(trigger: trigger)
    }

    func prepareManualTripStartDuringVideo() async {
        let ownership = tripCoordinator.currentTripOwnership()

        if ownership == .videoImplicit {
            await tripCoordinator.beginManualTripDuringVideo()
            return
        }

        tripCoordinator.handleManualTripStartedOutsideVideo()
    }

    func restoreImplicitTripAfterManualTripStopIfNeeded() async {
        await tripCoordinator.restoreImplicitTripAfterManualTripStopIfNeeded(videoStillActive: isVideoModeActive)
    }

    func manualTripStartedOutsideVideo() {
        tripCoordinator.handleManualTripStartedOutsideVideo()
    }

    func applicationWillResignActive() {
        guard state == .recording || state == .preparing else { return }

        Self.__dbg("[DASHCAM][APP] applicationWillResignActive state=\(state)")
        beginBackgroundTaskIfNeeded()
        backgroundStopStartedAt = Date()
    }

    func applicationDidBecomeActive() {
        Self.__dbg("[DASHCAM][APP] applicationDidBecomeActive state=\(state) movieOutput.isRecording=\(movieOutput.isRecording)")
        backgroundStopStartedAt = nil

        if state == .stopping {
            return
        }

        if state == .recording && !movieOutput.isRecording {
            lastError = .unknown("Видеозапись была завершена при уходе приложения в фон")
            forceResetAfterInterruptedStop()
            return
        }

        if state == .preparing && !movieOutput.isRecording {
            lastError = .unknown("Не удалось продолжить видеозапись после возврата в приложение")
            forceResetAfterInterruptedStop()
            return
        }

        if state == .recording && movieOutput.isRecording {
            sessionInterrupted = false
        }
    }
    
    private func configureAudioSessionIfNeeded() throws {
        guard settingsStore.enableMicrophone else {
            print("[DASHCAM][AUDIO] microphone disabled -> skip audio session config")
            Self.__dbg("[DASHCAM][AUDIO] microphone disabled -> skip audio session config")
            return
        }

        print("[DASHCAM][AUDIO] configure begin")
        Self.__dbg("[DASHCAM][AUDIO] configure begin")
        let audioSession = AVAudioSession.sharedInstance()
        try audioSession.setCategory(
            .playAndRecord,
            mode: .videoRecording,
            options: [
                .mixWithOthers,
                .defaultToSpeaker,
                .allowBluetoothA2DP
            ]
        )
        print("[DASHCAM][AUDIO] configure success")
        Self.__dbg("[DASHCAM][AUDIO] configure success")
    }


    private func configureSessionIfNeeded() throws {
        captureSession.beginConfiguration()

        for input in captureSession.inputs {
            captureSession.removeInput(input)
        }

        for output in captureSession.outputs {
            captureSession.removeOutput(output)
        }

        captureSession.sessionPreset = capabilityService.recommendedPreset()

        guard let camera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) else {
            captureSession.commitConfiguration()
            throw DashcamError.cameraUnavailable
        }

        let videoInput = try AVCaptureDeviceInput(device: camera)
        guard captureSession.canAddInput(videoInput) else {
            captureSession.commitConfiguration()
            throw DashcamError.cannotCreateOutput
        }
        captureSession.addInput(videoInput)

        if settingsStore.enableMicrophone, let mic = AVCaptureDevice.default(for: .audio) {
            let audioInput = try AVCaptureDeviceInput(device: mic)
            if captureSession.canAddInput(audioInput) {
                captureSession.addInput(audioInput)
            }
        }

        guard captureSession.canAddOutput(movieOutput) else {
            captureSession.commitConfiguration()
            throw DashcamError.cannotCreateOutput
        }

        captureSession.addOutput(movieOutput)
        captureSession.commitConfiguration()
    }

        

    private func startNextSegment() throws {
        if movieOutput.isRecording {
            print("[DASHCAM][SEGMENT] skip startNextSegment: already recording")
            Self.__dbg("[DASHCAM][SEGMENT] skip startNextSegment: already recording")
            return
        }

        if isSegmentFinishing {
            print("[DASHCAM][SEGMENT] skip startNextSegment: segment finishing in progress")
            Self.__dbg("[DASHCAM][SEGMENT] skip startNextSegment: segment finishing in progress")
            return
        }

        guard let activeVideoSessionId else { throw DashcamError.cannotStartRecording }

        print("[DASHCAM][SEGMENT] startNextSegment begin sessionId=\(activeVideoSessionId)")
        Self.__dbg("[DASHCAM][SEGMENT] startNextSegment begin sessionId=\(activeVideoSessionId)")

        if !captureSession.isRunning {
            print("[DASHCAM][SEGMENT] captureSession not running -> startRunning again")
            Self.__dbg("[DASHCAM][SEGMENT] captureSession not running -> startRunning again")
            let semaphore = DispatchSemaphore(value: 0)
            sessionQueue.async {
                self.captureSession.startRunning()
                semaphore.signal()
            }
            _ = semaphore.wait(timeout: .now() + 3)
        }

        guard captureSession.isRunning else {
            print("[DASHCAM][SEGMENT] captureSession is still not running")
            Self.__dbg("[DASHCAM][SEGMENT] captureSession is still not running")
            throw DashcamError.cannotStartRecording
        }

        let now = Date()
        if let last = lastSegmentStartAt, now.timeIntervalSince(last) < 1.0 {
            print("[DASHCAM][SEGMENT] skip startNextSegment: too soon after previous start")
            Self.__dbg("[DASHCAM][SEGMENT] skip startNextSegment: too soon after previous start")
            return
        }
        lastSegmentStartAt = now

        let dir = try archiveStore.urlForSession(sessionId: activeVideoSessionId)
        let url = dir.appendingPathComponent("segment_\(UUID().uuidString).mov")

        if FileManager.default.fileExists(atPath: url.path) {
            try? FileManager.default.removeItem(at: url)
        }

        print("[DASHCAM][SEGMENT] outputURL=\(url.path)")
        Self.__dbg("[DASHCAM][SEGMENT] outputURL=\(url.path)")

        currentSegmentId = UUID().uuidString
        currentSegmentURL = url
        currentSegmentStartedAt = now
        isSegmentFinishing = false
        lastSegmentFinishRequestedAt = nil

        print("[SEG_ROTATE] startNextSegment begin at=\(Date()) state=\(state) currentSegmentId=\(currentSegmentId ?? "nil") currentVideoSessionId=\(activeVideoSessionId ?? "nil")")
        Self.__dbg("[SEG_ROTATE] startNextSegment begin at=\(Date()) state=\(state) currentSegmentId=\(currentSegmentId ?? "nil") currentVideoSessionId=\(activeVideoSessionId ?? "nil")")
        print("[SEG_ROTATE] startNextSegment outputURL=\(url.path)")
        Self.__dbg("[SEG_ROTATE] startNextSegment outputURL=\(url.path)")
        print("[SEG_ROTATE] segment assigned at=\(Date()) currentSegmentURL=\(currentSegmentURL?.path ?? "nil") currentSegmentStartedAt=\(currentSegmentStartedAt?.formatted(.iso8601) ?? "nil")")
        Self.__dbg("[SEG_ROTATE] segment assigned at=\(Date()) currentSegmentURL=\(currentSegmentURL?.path ?? "nil") currentSegmentStartedAt=\(currentSegmentStartedAt?.formatted(.iso8601) ?? "nil")")

        movieOutput.startRecording(to: url, recordingDelegate: self)
        print("[DASHCAM][SEGMENT] movieOutput.startRecording CALLED url=\(url.path)")
        Self.__dbg("[DASHCAM][SEGMENT] movieOutput.startRecording CALLED url=\(url.path)")

        startSegmentWatchdog()
    }
    
    private func startSegmentWatchdog() {
        stopSegmentWatchdog()

        segmentWatchdogTimer = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { [weak self] _ in
            guard let self else { return }
            self.checkSegmentWatchdog()
        }
    }

    private func stopSegmentWatchdog() {
        segmentWatchdogTimer?.invalidate()
        segmentWatchdogTimer = nil
    }

    private func checkSegmentWatchdog() {
        guard state == .recording || state == .stopping else { return }

        let now = Date()

        if let startedAt = currentSegmentStartedAt, !isSegmentFinishing {
            let hardLimit = TimeInterval(max(20, min(45, settingsStore.maxSegmentDurationSeconds + 10)))
            if now.timeIntervalSince(startedAt) > hardLimit {
                print("[DashcamWatchdog] segment exceeded hard limit, forcing finish")
                requestSegmentFinish()
                return
            }
        }

        if isSegmentFinishing, let finishRequestedAt = lastSegmentFinishRequestedAt {
            if now.timeIntervalSince(finishRequestedAt) > 10 {
                print("[DashcamWatchdog] segment finish stalled, stopping video with fatal error")
                lastError = .unknown("Сегмент не завершился вовремя")
                Task { @MainActor in
                    await stopVideoMode(trigger: .fatalError)
                }
            }
        }
    }

    private func startTimers() {
        stopTimers()

        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            guard let self else { return }
            guard let started = self.recordingStartedAt else { return }

            let total = max(0, Int(Date().timeIntervalSince(started)))
            self.timerText = String(format: "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
        }

        segmentCheckTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            guard let self else { return }

            self.checkSegmentRotationIfNeeded()

            Task { @MainActor in
                Self.__dbg("[DASHCAM][TIMER] periodic drain tick")
                await self.crashCoordinator.processTimerReadyCrashClips()
                await self.crashCoordinator.attemptPendingServerVideoSessionStarts()
                await self.crashCoordinator.attemptPendingCrashMetadataUpload()
                await self.crashCoordinator.attemptPendingCrashVideoUploads()
            }
        }
    }

    private func stopTimers() {
        timer?.invalidate()
        segmentCheckTimer?.invalidate()
        timer = nil
        segmentCheckTimer = nil
        stopSegmentWatchdog()
    }

    private func checkSegmentRotationIfNeeded() {
        guard state == .recording else { return }
        guard movieOutput.isRecording else { return }
        guard !isSegmentFinishing else { return }
        guard let segmentStarted = currentSegmentStartedAt else { return }

        Task { @MainActor in
            await self.evaluateQuotaForNextSegment()
        }

        let maxDuration = max(10, min(120, settingsStore.maxSegmentDurationSeconds))
        let elapsed = Date().timeIntervalSince(segmentStarted)
        if elapsed >= TimeInterval(maxDuration) {
            print("[SEG_ROTATE] regular rotation after \(elapsed)s maxDuration=\(maxDuration)")
            Self.__dbg("[SEG_ROTATE] regular rotation after \(elapsed)s maxDuration=\(maxDuration)")
            requestSegmentFinish()
        }
    }

    private func requestSegmentFinish() {
        guard movieOutput.isRecording else {
            print("[SEG_ROTATE] requestSegmentFinish skipped: movieOutput.isRecording == false")
            Self.__dbg("[SEG_ROTATE] requestSegmentFinish skipped: movieOutput.isRecording == false")
            return
        }
        guard !isSegmentFinishing else {
            print("[SEG_ROTATE] requestSegmentFinish skipped: already finishing")
            Self.__dbg("[SEG_ROTATE] requestSegmentFinish skipped: already finishing")
            return
        }

        isSegmentFinishing = true
        lastSegmentFinishRequestedAt = Date()
        print("[SEG_ROTATE] movieOutput.stopRecording CALLED at=\(Date()) currentSegmentURL=\(currentSegmentURL?.path ?? "nil")")
        Self.__dbg("[SEG_ROTATE] movieOutput.stopRecording CALLED at=\(Date()) currentSegmentURL=\(currentSegmentURL?.path ?? "nil")")
        movieOutput.stopRecording()
    }
    
    func requestSegmentFinishForCrash() {
        if state == .idle {
            print("[SEG_ROTATE] requestSegmentFinishForCrash skipped: state=idle")
            Self.__dbg("[SEG_ROTATE] requestSegmentFinishForCrash skipped: state=idle")
            return
        }

        if isSegmentFinishing {
            print("[SEG_ROTATE] requestSegmentFinishForCrash: already finishing, keep waiting for didFinishRecording")
            Self.__dbg("[SEG_ROTATE] requestSegmentFinishForCrash: already finishing, keep waiting for didFinishRecording")
            return
        }

        guard movieOutput.isRecording else {
            print("[SEG_ROTATE] requestSegmentFinishForCrash skipped: movieOutput.isRecording == false")
            Self.__dbg("[SEG_ROTATE] requestSegmentFinishForCrash skipped: movieOutput.isRecording == false")
            return
        }

        isSegmentFinishing = true
        lastSegmentFinishRequestedAt = Date()
        print("[SEG_ROTATE] requestSegmentFinishForCrash stopRecording at=\(Date()) currentSegmentURL=\(currentSegmentURL?.path ?? "nil")")
        Self.__dbg("[SEG_ROTATE] requestSegmentFinishForCrash stopRecording at=\(Date()) currentSegmentURL=\(currentSegmentURL?.path ?? "nil")")
        movieOutput.stopRecording()
    }

    private func fileSize(_ url: URL?) -> Int64 {
        guard
            let url,
            let attrs = try? FileManager.default.attributesOfItem(atPath: url.path),
            let n = attrs[.size] as? NSNumber
        else {
            return 0
        }

        return n.int64Value
    }

    private func finalizeStoppedSession(trigger: DashcamStopTrigger) async {
        guard !isFinalizingStop else { return }
        isFinalizingStop = true
        defer { isFinalizingStop = false }

        let stopDate = Date()
        Self.__dbg("[DASHCAM][STOP] finalizeStoppedSession begin trigger=\(trigger.rawValue) activeVideoSessionId=\(activeVideoSessionId ?? "nil")")

        finishStopProgressUI()
        UIApplication.shared.isIdleTimerDisabled = false
        stopSegmentWatchdog()

        if captureSession.isRunning {
            sessionQueue.async {
                self.captureSession.stopRunning()
            }
        }

        await crashCoordinator.processCrashClipsForStop(stopDate: stopDate)
        scheduleCrashUploadDrain(after: 0.5)

        if let sessionId = activeVideoSessionId {
            let segmentsCount = try? archiveStore.segmentsCount(for: sessionId)
            let totalSize = try? archiveStore.totalUsageBytes()

            
            if crashCoordinator.isServerVideoSessionConfirmed(sessionId) {
                Self.__dbg("[DASHCAM][STOP] Posting server stop + camera-log sessionId=\(sessionId)")
                try? await networkManager.stopVideoSession(
                    VideoSessionStopRequest(
                        video_session_id: sessionId,
                        ended_at: ISO8601DateFormatter().string(from: stopDate),
                        stop_reason: trigger.rawValue,
                        final_linked_trip_session_id: tripCoordinator.currentTripSessionId(),
                        segments_count: segmentsCount,
                        total_size_bytes: totalSize
                    )
                )

                await postFinalCameraLog(trigger: trigger)
            } else {
                print("[DASHCAM] Skipping server stop/camera-log until video session is confirmed")
                Self.__dbg("[DASHCAM][STOP] Skipping server stop/camera-log until video session is confirmed sessionId=\(sessionId)")
            }

            try? archiveStore.finishVideoSession(
                id: sessionId,
                endedAt: stopDate,
                finalLinkedTripSessionId: tripCoordinator.currentTripSessionId(),
                stopReason: trigger
            )
        }

        await tripCoordinator.handleVideoStop(reason: trigger.tripFinishReason.rawValue)

        if let sessionId = activeVideoSessionId {
            lastStoppedVideoSessionId = sessionId
            lastStoppedVideoSessionEndedAt = stopDate
        }

        activeVideoSessionId = nil
        currentSegmentId = nil
        currentSegmentURL = nil
        currentSegmentStartedAt = nil
        recordingStartedAt = nil
        recordingStartCoordinate = nil
        timerText = "00:00:00"
        pendingStopTrigger = nil
        isSegmentFinishing = false
        lastCrashEvent = nil
        recentCrashAt = nil
        backgroundStopStartedAt = nil
        sessionInterrupted = false
        shouldStopAfterCurrentSegment = false
        quotaStopErrorMessage = nil
        pendingRuntimeRecoveryTask?.cancel()
        pendingRuntimeRecoveryTask = nil
        lastRuntimeRecoveryAttemptAt = nil
        isCrashUploadDrainScheduled = false
        sensorManager.suppressAutoFinishWhileDashcamActive = false
        state = .idle

        Self.__dbg("[DASHCAM][STOP] finalizeStoppedSession complete trigger=\(trigger.rawValue) lastStoppedVideoSessionId=\(lastStoppedVideoSessionId ?? "nil")")
        endBackgroundTaskIfNeeded()
    }

    private func postFinalCameraLog(trigger: DashcamStopTrigger) async {
        guard let sessionId = activeVideoSessionId, let started = recordingStartedAt else { return }

        let stats = (try? archiveStore.archiveStats()) ?? (0, 0, 0, 0)
        let totalSize = try? archiveStore.totalUsageBytes()
        let segmentsCount = try? archiveStore.segmentsCount(for: sessionId)
        let endCoordinate = sensorManager.latestKnownLocationCoordinate()

        let endedAt = Date()
        let sessionRange = started...endedAt

        let sessionSamples = sensorManager.samples(in: sessionRange)
        let sessionEvents = sensorManager.events(in: sessionRange)

        let sessionStartSample = sensorManager.nearestSample(to: started)
        let sessionEndSample = sensorManager.nearestSample(to: endedAt)
        let sessionStartSpeedKmh = sessionStartSample?.speed_m_s.flatMap { speed in
            speed >= 0 ? speed * 3.6 : nil
        }

        let sessionEndSpeedKmh = sessionEndSample?.speed_m_s.flatMap { speed in
            speed >= 0 ? speed * 3.6 : nil
        }

        let sessionEventTypes = Array(Set(sessionEvents.map { $0.type.rawValue })).sorted()

        let payload = DashcamCameraLogRequest(
            video_session_id: sessionId,
            linked_trip_session_id: tripCoordinator.currentTripSessionId(),
            driver_id: sensorManager.driverId,
            device_id: sensorManager.deviceIdForDisplay,
            started_at: ISO8601DateFormatter().string(from: started),
            ended_at: ISO8601DateFormatter().string(from: endedAt),
            recording_start_lat: recordingStartCoordinate?.latitude,
            recording_start_lon: recordingStartCoordinate?.longitude,
            recording_end_lat: endCoordinate?.latitude,
            recording_end_lon: endCoordinate?.longitude,
            session_start_sample_t: sessionStartSample?.t,
            session_end_sample_t: sessionEndSample?.t,
            total_samples: sessionSamples.count,
            total_events: sessionEvents.count,
            session_start_speed_kmh: sessionStartSpeedKmh,
            session_end_speed_kmh: sessionEndSpeedKmh,
            session_event_types: sessionEventTypes,
            stop_reason: trigger.rawValue,
            camera_mode: "rear",
            audio_enabled: settingsStore.enableMicrophone,
            is_crash_log: false,
            crash_detected_at: lastCrashEvent.map { ISO8601DateFormatter().string(from: $0.at) },
            crash_lat: lastCrashEvent?.latitude,
            crash_lon: lastCrashEvent?.longitude,
            crash_max_g: lastCrashEvent?.gForce,
            total_size_bytes: totalSize,
            total_segments_count: segmentsCount,
            archive_normal_count: stats.0,
            archive_crash_count: stats.1,
            archive_normal_size_bytes: stats.2,
            archive_crash_size_bytes: stats.3
        )

        Self.__dbg("[DASHCAM][STOP] postFinalCameraLog sessionId=\(sessionId) trigger=\(trigger.rawValue)")
        try? await networkManager.postDashcamCameraLog(payload)
    }
}

private extension DashcamStopTrigger {
    var tripFinishReason: TripFinishReason {
        switch self {
        case .userButton:
            return .userStop
        case .fatalError:
            return .fatalError
        case .appLifecycle:
            return .lifecycleStop
        case .diskLimit:
            return .diskLimit
        case .captureInterrupted:
            return .captureInterrupted
        case .appBackground:
            return .backgroundStop
        }
    }
}

extension DashcamManager: AVCaptureFileOutputRecordingDelegate {
    nonisolated func fileOutput(
        _ output: AVCaptureFileOutput,
        didFinishRecordingTo outputFileURL: URL,
        from connections: [AVCaptureConnection],
        error: Error?
    ) {
        Task { @MainActor in
            let expectedSegmentURL = currentSegmentURL
            let segmentStartedAt = currentSegmentStartedAt ?? Date()
            let segmentEndedAt = Date()
            print("[SEG_ROTATE] didFinishRecording ENTER at=\(Date()) outputFileURL=\(outputFileURL.path) expectedSegmentURL=\(expectedSegmentURL?.path ?? "nil") segmentStartedAt=\(segmentStartedAt.formatted(.iso8601)) segmentEndedAt=\(segmentEndedAt.formatted(.iso8601)) currentSegmentId=\(currentSegmentId ?? "nil") state=\(state)")
            Self.__dbg("[SEG_ROTATE] didFinishRecording ENTER at=\(Date()) outputFileURL=\(outputFileURL.path) expectedSegmentURL=\(expectedSegmentURL?.path ?? "nil") segmentStartedAt=\(segmentStartedAt.formatted(.iso8601)) segmentEndedAt=\(segmentEndedAt.formatted(.iso8601)) currentSegmentId=\(currentSegmentId ?? "nil") state=\(state)")

            currentSegmentURL = nil
            currentSegmentStartedAt = nil
            isSegmentFinishing = false
            lastSegmentFinishRequestedAt = nil
            
            stopSegmentWatchdog()

            if let error {
                print("[Dashcam] didFinishRecordingTo error = \(error.localizedDescription)")
                Self.__dbg("[Dashcam] didFinishRecordingTo error = \(error.localizedDescription)")
                lastSegmentStartAt = nil

                if FileManager.default.fileExists(atPath: outputFileURL.path) {
                    try? FileManager.default.removeItem(at: outputFileURL)
                } else if let expectedSegmentURL,
                          FileManager.default.fileExists(atPath: expectedSegmentURL.path) {
                    try? FileManager.default.removeItem(at: expectedSegmentURL)
                }

                lastError = .unknown("Запись была прервана системой: \(error.localizedDescription)")

                if shouldResumeAfterInterruption || sessionInterrupted {
                    print("[Dashcam] segment interrupted, waiting for automatic resume after interruption end")
                    Self.__dbg("[Dashcam] segment interrupted, waiting for automatic resume after interruption end")

                    currentSegmentId = nil
                    currentSegmentURL = nil
                    currentSegmentStartedAt = nil
                    isSegmentFinishing = false
                    lastSegmentFinishRequestedAt = nil

                    pendingInterruptionResumeTask?.cancel()
                    pendingInterruptionResumeTask = Task { [weak self] in
                        guard let self else { return }

                        try? await Task.sleep(nanoseconds: 5_000_000_000)

                        guard !Task.isCancelled else { return }
                        guard self.state == .recording || self.state == .preparing else { return }
                        guard !self.movieOutput.isRecording else { return }

                        let appState = UIApplication.shared.applicationState
                        guard appState != .background else {
                            print("[Dashcam] didFinish watchdog skipped: app in background, waiting for interruption-ended resume")
                            return
                        }

                        print("[Dashcam] didFinish watchdog fired -> forcing resume after interrupted segment, appState=\(appState.rawValue)")
                        Self.__dbg("[Dashcam] didFinish watchdog fired -> forcing resume after interrupted segment, appState=\(appState.rawValue)")
                        await self.attemptResumeAfterInterruption(force: true)
                    }

                    return
                }

                let trigger = pendingStopTrigger ?? .fatalError
                await finalizeStoppedSession(trigger: trigger)
                return
            }

            let sessionIdForSegment: String
            if let activeVideoSessionId {
                sessionIdForSegment = activeVideoSessionId
            } else if let fallback = lastStoppedVideoSessionId {
                sessionIdForSegment = fallback
                print("[SEG_ROTATE] using lastStoppedVideoSessionId fallback = \(fallback)")
                Self.__dbg("[SEG_ROTATE] using lastStoppedVideoSessionId fallback = \(fallback)")
            } else {
                print("[SEG_ROTATE] no session id for completed segment, file will not be indexed")
                Self.__dbg("[SEG_ROTATE] no session id for completed segment, file will not be indexed")
                return
            }

            let segmentURL = outputFileURL

            guard FileManager.default.fileExists(atPath: segmentURL.path) else {
                print("[Dashcam] recorded file missing after successful finish")
                Self.__dbg("[Dashcam] recorded file missing after successful finish")
                return
            }

            let size = fileSize(segmentURL)

            let range = segmentStartedAt...segmentEndedAt
            let samplesInRange = sensorManager.samples(in: range)
            let eventsInRange = sensorManager.events(in: range)

            let startSample = sensorManager.nearestSample(to: segmentStartedAt)
            let endSample = sensorManager.nearestSample(to: segmentEndedAt)

            let startSpeedKmh = startSample?.speed_m_s.flatMap { speed in
                speed >= 0 ? speed * 3.6 : nil
            }

            let endSpeedKmh = endSample?.speed_m_s.flatMap { speed in
                speed >= 0 ? speed * 3.6 : nil
            }

            let eventTypesNearby = Array(Set(eventsInRange.map { $0.type.rawValue })).sorted()

            do {
                print("[SEG_ROTATE] addCompletedSegment CALL at=\(Date()) sessionId=\(sessionIdForSegment) fileURL=\(segmentURL.path) startedAt=\(segmentStartedAt.formatted(.iso8601)) endedAt=\(segmentEndedAt.formatted(.iso8601)) size=\(size)")
                Self.__dbg("[SEG_ROTATE] addCompletedSegment CALL at=\(Date()) sessionId=\(sessionIdForSegment) fileURL=\(segmentURL.path) startedAt=\(segmentStartedAt.formatted(.iso8601)) endedAt=\(segmentEndedAt.formatted(.iso8601)) size=\(size)")
                try archiveStore.addCompletedSegment(
                    sessionId: sessionIdForSegment,
                    fileURL: segmentURL,
                    startedAt: segmentStartedAt,
                    endedAt: segmentEndedAt,
                    sizeBytes: size,
                    timelineStartSampleT: startSample?.t,
                    timelineEndSampleT: endSample?.t,
                    startLat: startSample?.lat,
                    startLon: startSample?.lon,
                    endLat: endSample?.lat,
                    endLon: endSample?.lon,
                    startSpeedKmh: startSpeedKmh,
                    endSpeedKmh: endSpeedKmh,
                    samplesCount: samplesInRange.count,
                    eventsCount: eventsInRange.count,
                    eventTypesNearby: eventTypesNearby
                )
                
                print("[SEG_ROTATE] addCompletedSegment DONE at=\(Date()) sessionId=\(sessionIdForSegment) fileURL=\(segmentURL.path)")
                Self.__dbg("[SEG_ROTATE] addCompletedSegment DONE at=\(Date()) sessionId=\(sessionIdForSegment) fileURL=\(segmentURL.path)")
                
            } catch {
                print("❌ archiveStore.addCompletedSegment failed: \(error)")
                Self.__dbg("❌ archiveStore.addCompletedSegment failed: \(error)")
                lastError = .unknown("Не удалось сохранить сегмент видео: \(error.localizedDescription)")
                let trigger = pendingStopTrigger ?? .fatalError
                await finalizeStoppedSession(trigger: trigger)
                return
            }

            await quotaManager.notifySegmentCommitted(sizeBytes: size)
            await crashCoordinator.processTimerReadyCrashClips()

            if state == .stopping {
                scheduleCrashUploadDrain(after: 0.5)
                let trigger = pendingStopTrigger ?? .userButton
                await finalizeStoppedSession(trigger: trigger)
                return
            }

            if state == .recording && UIApplication.shared.applicationState != .active {
                if shouldResumeAfterInterruption || sessionInterrupted {
                    print("[Dashcam] app inactive during interruption, waiting for resume")
                    return
                }
            }

            scheduleCrashUploadDrain(after: 2.0)

            do {
                try startNextSegment()
            } catch {
                lastError = .unknown("Не удалось начать следующий сегмент: \(error.localizedDescription)")
                let trigger = pendingStopTrigger ?? .fatalError
                await finalizeStoppedSession(trigger: trigger)
            }
        }
    }
}
