import Foundation
import AVFoundation
import UIKit
import Combine

@MainActor
final class DashcamManager: NSObject, ObservableObject {
    @Published private(set) var state: DashcamRecordingState = .idle
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
    private let crashCoordinator: CrashClipCoordinator
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
    
    private var pendingStopTrigger: DashcamStopTrigger?
    private var isSegmentFinishing = false
    private var crashHoldUntil: Date?
    private var lastCrashEvent: CrashEvent?
    private var crashEventCancellable: AnyCancellable?
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
   
    
    init(
        sensorManager: SensorManager,
        networkManager: NetworkManager,
        archiveStore: VideoArchiveStore,
        tripCoordinator: DashcamTripCoordinator,
        crashCoordinator: CrashClipCoordinator,
        quotaManager: StorageQuotaManager,
        capabilityService: CameraCapabilityService,
        settingsStore: DashcamSettingsStore
    ) {
        self.sensorManager = sensorManager
        self.networkManager = networkManager
        self.archiveStore = archiveStore
        self.tripCoordinator = tripCoordinator
        self.crashCoordinator = crashCoordinator
        self.quotaManager = quotaManager
        self.capabilityService = capabilityService
        self.settingsStore = settingsStore
        super.init()
        
        bindCrashEvents()
        registerCaptureObservers()
    }
    
    deinit {
        NotificationCenter.default.removeObserver(self)
        segmentWatchdogTimer?.invalidate()
       
       
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
    
    func requestPermissionsIfNeeded() async throws {
        let cameraGranted = await capabilityService.requestCameraAccess()
        guard cameraGranted else { throw DashcamError.cameraPermissionDenied }
        
        if settingsStore.enableMicrophone {
            let micGranted = await capabilityService.requestMicrophoneAccess()
            guard micGranted else { throw DashcamError.microphonePermissionDenied }
        }
    }
    
    private func startStopProgressUI() {
        stopStopProgressUI()

        stopProgressText = "Идет сохранение на устройство"
        stopProgressValue = 0.05

        stopProgressTimer = Timer.scheduledTimer(withTimeInterval: 0.12, repeats: true) { [weak self] _ in
            guard let self else { return }

            // Плавно растем, но не доходим до 100%, пока реально не завершили запись
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

        // Если по какой-то причине после finalize состояние не сбросилось,
        // значит cleanup не дошел до конца — уходим в аварийный reset.
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
        crashHoldUntil = nil
        lastCrashEvent = nil
        backgroundStopStartedAt = nil
        state = .idle
        
        shouldStopAfterCurrentSegment = false
        quotaStopErrorMessage = nil

        endBackgroundTaskIfNeeded()
    }
    
    @objc private func handleCaptureSessionInterrupted(_ notification: Notification) {
        sessionInterrupted = true
        print("[DashcamCapture] session interrupted")

        if state == .recording {
            lastError = .unknown("Камера была прервана системой")
        }
    }

    @objc private func handleCaptureSessionInterruptionEnded(_ notification: Notification) {
        sessionInterrupted = false
        print("[DashcamCapture] session interruption ended")

        // После системного interruption (например, телефонного звонка)
        // не пытаемся автоматически поднимать запись.
        // Реальное завершение/финализация будет обработано в didFinishRecordingTo / runtimeError.
    }

    @objc private func handleCaptureSessionRuntimeError(_ notification: Notification) {
        let nsError = notification.userInfo?[AVCaptureSessionErrorKey] as? NSError
        let text = nsError?.localizedDescription ?? "Неизвестная ошибка камеры"

        print("[DashcamCapture] runtime error: \(text)")

        // Если мы уже останавливаемся или уже idle — не запускаем повторный stop-цикл.
        guard state == .recording || state == .preparing else { return }

        lastError = .unknown("Ошибка камеры: \(text)")

        Task { @MainActor in
            let trigger = self.pendingStopTrigger ?? .fatalError
            await self.finalizeStoppedSession(trigger: trigger)
        }
    }
    
    func startVideoMode(trigger: DashcamStartTrigger) async throws {
        guard state == .idle else { return }
        
        state = .preparing
        lastError = nil
        pendingStopTrigger = nil
        crashHoldUntil = nil
        lastCrashEvent = nil
        
        try await quotaManager.ensureCanStartRecording()
        
        try capabilityService.validateRearCameraAvailable()
        try configureAudioSessionIfNeeded()
        
        let ownership = try await tripCoordinator.ensureTripForVideoStart()
        try configureSessionIfNeeded()
        
        let linkedTrip = tripCoordinator.currentTripSessionId()
        let sessionId = try archiveStore.createVideoSession(
            startedAt: Date(),
            linkedTripSessionId: linkedTrip
        )
        
        activeVideoSessionId = sessionId
        recordingStartedAt = Date()
        
        UIApplication.shared.isIdleTimerDisabled = true
        
        try await withCheckedThrowingContinuation { continuation in
            sessionQueue.async {
                self.captureSession.startRunning()
                continuation.resume()
            }
        }
        
        try startNextSegment()
        
        state = .recording
        startTimers()
        
        Task {
            try? await networkManager.startVideoSession(
                VideoSessionStartRequest(
                    video_session_id: sessionId,
                    device_id: sensorManager.deviceIdForDisplay,
                    driver_id: sensorManager.driverId,
                    started_at: ISO8601DateFormatter().string(from: Date()),
                    linked_trip_session_id: linkedTrip,
                    trip_source: ownership == .videoImplicit ? .videoImplicit : .manual,
                    camera_mode: "rear",
                    audio_enabled: settingsStore.enableMicrophone,
                    app_version: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String,
                    ios_version: UIDevice.current.systemVersion,
                    device_model: UIDevice.current.model
                )
            )
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

        state = .stopping
        pendingStopTrigger = trigger
        startStopProgressUI()

        timer?.invalidate()
        segmentCheckTimer?.invalidate()
        timer = nil
        segmentCheckTimer = nil

        // Если запись реально ещё идёт — просим завершить сегмент.
        if movieOutput.isRecording {
            requestSegmentFinish()
            startSegmentWatchdog()
            return
        }

        // Если после interruption / phone call output уже не пишет,
        // не ждём бесконечно, а сразу штатно финализируем.
        await finalizeStoppedSession(trigger: trigger)
    }
    
    func prepareManualTripStartDuringVideo() async {
        let ownership = tripCoordinator.currentTripOwnership()

        if ownership == .videoImplicit {
            await tripCoordinator.beginManualTripDuringVideo()
        } else {
            tripCoordinator.handleManualTripStartedOutsideVideo()
        }

        sensorManager.startCollecting()
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

        beginBackgroundTaskIfNeeded()
        backgroundStopStartedAt = Date()
    }
    
    func applicationDidBecomeActive() {
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
    
   
    
    private func bindCrashEvents() {
        crashEventCancellable = sensorManager.crashEventPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] event in
                Task { @MainActor in
                    await self?.handleCrashEvent(event)
                }
            }
    }
    
    private func handleCrashEvent(_ event: CrashEvent) async {
        guard state == .recording else { return }
        
        lastCrashEvent = event
        
        let holdUntil = event.at.addingTimeInterval(60)
        if let existing = crashHoldUntil {
            crashHoldUntil = max(existing, holdUntil)
        } else {
            crashHoldUntil = holdUntil
        }
        
        await postInterimCrashLog(event)
    }
    
    private func configureAudioSessionIfNeeded() throws {
        guard settingsStore.enableMicrophone else { return }
        
        let audioSession = AVAudioSession.sharedInstance()
        try audioSession.setCategory(.playAndRecord, mode: .videoRecording, options: [.defaultToSpeaker])
        try audioSession.setActive(true)
    }
    
    private func configureSessionIfNeeded() throws {
        guard !captureSession.outputs.contains(movieOutput) else { return }
        
        captureSession.beginConfiguration()
        captureSession.sessionPreset = capabilityService.recommendedPreset()
        
        guard let camera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) else {
            captureSession.commitConfiguration()
            throw DashcamError.cameraUnavailable
        }
        
        let videoInput = try AVCaptureDeviceInput(device: camera)
        if captureSession.canAddInput(videoInput) {
            captureSession.addInput(videoInput)
        }
        
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
        guard let activeVideoSessionId else { throw DashcamError.cannotStartRecording }

        if !captureSession.isRunning {
            let semaphore = DispatchSemaphore(value: 0)
            sessionQueue.async {
                self.captureSession.startRunning()
                semaphore.signal()
            }
            _ = semaphore.wait(timeout: .now() + 3)
        }

        guard captureSession.isRunning else {
            throw DashcamError.cannotStartRecording
        }

        let dir = try archiveStore.urlForSession(sessionId: activeVideoSessionId)
        let url = dir.appendingPathComponent("segment_\(UUID().uuidString).mov")

        let segmentRecord = try archiveStore.createSegment(
            sessionId: activeVideoSessionId,
            fileURL: url,
            startedAt: Date()
        )

        currentSegmentId = segmentRecord.id
        currentSegmentURL = url
        currentSegmentStartedAt = Date()
        isSegmentFinishing = false
        lastSegmentFinishRequestedAt = nil

        movieOutput.startRecording(to: url, recordingDelegate: self)
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
            let hardLimit = TimeInterval(max(30, settingsStore.maxSegmentDurationSeconds) + 20)
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

        
        let now = Date()
        
        if let holdUntil = crashHoldUntil, now < holdUntil {
            return
        }
        
        let elapsed = now.timeIntervalSince(segmentStarted)
        if elapsed >= TimeInterval(max(30, settingsStore.maxSegmentDurationSeconds)) {
            requestSegmentFinish()
        }
    }
    
    private func requestSegmentFinish() {
        guard movieOutput.isRecording else { return }
        guard !isSegmentFinishing else { return }

        isSegmentFinishing = true
        lastSegmentFinishRequestedAt = Date()
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
    
    private func createCrashClipIfNeeded(stopDate: Date) async {
        guard let crash = lastCrashEvent else { return }
        guard let videoStartedAt = recordingStartedAt else { return }

        let clipStart = max(videoStartedAt, crash.at.addingTimeInterval(-60))
        let clipEnd = min(stopDate, crash.at.addingTimeInterval(60))

        guard clipEnd > clipStart else { return }

        do {
            let protectedSegments = try archiveStore.protectSegmentsForCrash(from: clipStart, to: clipEnd)
            guard !protectedSegments.isEmpty else { return }

            let preSeconds = max(0, Int(crash.at.timeIntervalSince(clipStart)))
            let postSeconds = max(0, Int(clipEnd.timeIntervalSince(crash.at)))

            let clip = try archiveStore.createCrashClip(
                from: protectedSegments,
                crashAt: crash.at,
                preSeconds: preSeconds,
                postSeconds: postSeconds,
                linkedTripSessionId: tripCoordinator.currentTripSessionId(),
                latitude: crash.latitude,
                longitude: crash.longitude,
                maxG: crash.gForce
            )

            try? await networkManager.postCrashClip(
                CrashClipEventRequest(
                    crash_clip_id: clip.id,
                    video_session_id: activeVideoSessionId,
                    linked_trip_session_id: clip.linkedTripSessionId,
                    crash_detected_at: ISO8601DateFormatter().string(from: crash.at),
                    pre_seconds: preSeconds,
                    post_seconds: postSeconds,
                    segment_ids: clip.segmentIds,
                    lat: crash.latitude,
                    lon: crash.longitude,
                    max_g: crash.gForce,
                    speed_kmh: nil
                )
            )
        } catch {
            print("[DashcamCrashExport] \(error)")
        }
    }
    
    private func finalizeStoppedSession(trigger: DashcamStopTrigger) async {
        guard !isFinalizingStop else { return }
        isFinalizingStop = true
        defer { isFinalizingStop = false }

        let stopDate = Date()

        finishStopProgressUI()
        UIApplication.shared.isIdleTimerDisabled = false
        stopSegmentWatchdog()

        if captureSession.isRunning {
            sessionQueue.async {
                self.captureSession.stopRunning()
            }
        }

        await createCrashClipIfNeeded(stopDate: stopDate)

        if let sessionId = activeVideoSessionId {
            let segmentsCount = try? archiveStore.segmentsCount(for: sessionId)
            let totalSize = try? archiveStore.totalUsageBytes()

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

            try? archiveStore.finishVideoSession(
                id: sessionId,
                endedAt: stopDate,
                finalLinkedTripSessionId: tripCoordinator.currentTripSessionId(),
                stopReason: trigger
            )

            await postFinalCameraLog(trigger: trigger)
        }

        await tripCoordinator.handleVideoStop()

        activeVideoSessionId = nil
        currentSegmentId = nil
        currentSegmentURL = nil
        currentSegmentStartedAt = nil
        recordingStartedAt = nil
        timerText = "00:00:00"
        pendingStopTrigger = nil
        isSegmentFinishing = false
        crashHoldUntil = nil
        lastCrashEvent = nil
        backgroundStopStartedAt = nil
        sessionInterrupted = false
        shouldStopAfterCurrentSegment = false
        quotaStopErrorMessage = nil
        state = .idle

        endBackgroundTaskIfNeeded()
    }
    
    private func postInterimCrashLog(_ crash: CrashEvent) async {
        guard let sessionId = activeVideoSessionId, let started = recordingStartedAt else { return }
        
        let stats = (try? archiveStore.archiveStats()) ?? (0, 0, 0, 0)
        let totalSize = try? archiveStore.totalUsageBytes()
        
        let payload = DashcamCameraLogRequest(
            video_session_id: sessionId,
            linked_trip_session_id: tripCoordinator.currentTripSessionId(),
            driver_id: sensorManager.driverId,
            device_id: sensorManager.deviceIdForDisplay,
            started_at: ISO8601DateFormatter().string(from: started),
            ended_at: nil,
            stop_reason: nil,
            camera_mode: "rear",
            audio_enabled: settingsStore.enableMicrophone,
            is_crash_log: true,
            crash_detected_at: ISO8601DateFormatter().string(from: crash.at),
            crash_lat: crash.latitude,
            crash_lon: crash.longitude,
            crash_max_g: crash.gForce,
            total_size_bytes: totalSize,
            total_segments_count: nil,
            archive_normal_count: stats.0,
            archive_crash_count: stats.1,
            archive_normal_size_bytes: stats.2,
            archive_crash_size_bytes: stats.3
        )
        
        try? await networkManager.postDashcamCameraLog(payload)
    }
    
    private func postFinalCameraLog(trigger: DashcamStopTrigger) async {
        guard let sessionId = activeVideoSessionId, let started = recordingStartedAt else { return }

        let stats = (try? archiveStore.archiveStats()) ?? (0, 0, 0, 0)
        let totalSize = try? archiveStore.totalUsageBytes()
        let segmentsCount = try? archiveStore.segmentsCount(for: sessionId)

        let payload = DashcamCameraLogRequest(
            video_session_id: sessionId,
            linked_trip_session_id: tripCoordinator.currentTripSessionId(),
            driver_id: sensorManager.driverId,
            device_id: sensorManager.deviceIdForDisplay,
            started_at: ISO8601DateFormatter().string(from: started),
            ended_at: ISO8601DateFormatter().string(from: Date()),
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

        try? await networkManager.postDashcamCameraLog(payload)
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
            if let segmentId = currentSegmentId {
                let size = fileSize(outputFileURL)

                try? archiveStore.finalizeSegment(
                    id: segmentId,
                    endedAt: Date(),
                    sizeBytes: size
                )

                await quotaManager.notifySegmentCommitted(sizeBytes: size)
            }

            currentSegmentId = nil
            currentSegmentURL = nil
            currentSegmentStartedAt = nil
            isSegmentFinishing = false
            lastSegmentFinishRequestedAt = nil
            stopSegmentWatchdog()

            if let error {
                print("[Dashcam] didFinishRecordingTo error = \(error.localizedDescription)")

                // Если запись завершилась с ошибкой, не пытаемся стартовать новый сегмент.
                // Особенно важно после phone call / system interruption.
                lastError = .unknown("Запись была прервана системой: \(error.localizedDescription)")

                let trigger = pendingStopTrigger ?? .fatalError
                await finalizeStoppedSession(trigger: trigger)
                return
            }

            if state == .stopping {
                let trigger = pendingStopTrigger ?? .userButton
                await finalizeStoppedSession(trigger: trigger)
                return
            }

            if state == .recording && UIApplication.shared.applicationState != .active {
                let trigger = pendingStopTrigger ?? .appBackground
                await finalizeStoppedSession(trigger: trigger)
                return
            }

            if state == .recording && UIApplication.shared.applicationState == .active {
                do {
                    try startNextSegment()
                } catch {
                    lastError = .unknown("Не удалось запустить следующий сегмент: \(error.localizedDescription)")
                    await finalizeStoppedSession(trigger: .fatalError)
                }
                return
            }
        }
    }
}
