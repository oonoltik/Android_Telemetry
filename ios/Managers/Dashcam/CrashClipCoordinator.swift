import Foundation
import Combine

@MainActor
final class CrashClipCoordinator {

    enum CrashJobStage: String, Codable {
        case detected
        case preclipCreated
        case metadataQueued
        case waitingPostWindow
        case finalClipCreated
        case videoUploadQueued
        case completed
        case failed
    }

    struct PendingCrashClip: Codable, Identifiable {
        let id: String
        let crashDate: Date
        let videoSessionId: String
        let linkedTripSessionId: String?
        let latitude: Double?
        let longitude: Double?
        let maxG: Double?

        let requestedPreSeconds: Int
        let requestedPostSeconds: Int

        var isReadyByTimer: Bool
        var isCompleted: Bool
        var stage: CrashJobStage

        var temporaryPreclipId: String?
        var finalClipId: String?
        var stopDateOverride: Date?
        
        var isFinalizing: Bool
        var finalizeAttemptCount: Int
        var nextFinalizeAttemptAt: Date?
        
        var interruptionExtensionSeconds: Int
        var hasUsedInterruptionExtension: Bool
    }

    struct PendingCrashMetadata: Codable {
        let crashId: String
        let videoSessionId: String
        let linkedTripSessionId: String?
        let crashDate: Date
        let preSeconds: Int
        let postSeconds: Int
        let latitude: Double?
        let longitude: Double?
        let maxG: Double?
        let speedKmh: Double?
        let segmentIds: [String]

        var retryCount: Int
        var nextRetryAt: Date
        var isInFlight: Bool
    }

    struct PendingCrashVideoUpload: Codable {
        let crashId: String
        let videoSessionId: String
        let filePath: String

        var retryCount: Int
        var nextRetryAt: Date
        var isInFlight: Bool
    }

    private let sensorManager: SensorManager
    private let archiveStore: VideoArchiveStore
    private let networkManager: NetworkManager
    private let settingsStore: DashcamSettingsStore
    private let currentVideoSessionIdProvider: () -> String?

    private var cancellable: AnyCancellable?

    private var pendingCrashClips: [PendingCrashClip] = []
    private var pendingCrashMetadataQueue: [PendingCrashMetadata] = []
    private var pendingCrashVideoUploadQueue: [PendingCrashVideoUpload] = []

    private let maxMetadataRetryCount = 10
    private let maxCrashVideoRetryCount = 20
    private let cooldownSeconds: TimeInterval = 20

    private var lastAcceptedCrashAt: Date?
    private var isDrainingMetadataQueue = false
    private var isDrainingVideoQueue = false

    private var metadataQueueFileURL: URL {
        let base = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        return base.appendingPathComponent("pending_crash_metadata_queue.json")
    }

    private var videoUploadQueueFileURL: URL {
        let base = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        return base.appendingPathComponent("pending_crash_video_upload_queue.json")
    }

    init(
        sensorManager: SensorManager,
        archiveStore: VideoArchiveStore,
        networkManager: NetworkManager,
        settingsStore: DashcamSettingsStore,
        currentVideoSessionIdProvider: @escaping () -> String?
    ) {
        self.sensorManager = sensorManager
        self.archiveStore = archiveStore
        self.networkManager = networkManager
        self.settingsStore = settingsStore
        self.currentVideoSessionIdProvider = currentVideoSessionIdProvider

        restorePendingCrashMetadataQueue()
        restorePendingCrashVideoUploadQueue()
    }

    func attach() {
        log("Attach crash coordinator")

        cancellable = sensorManager.crashEventPublisher
            .sink { [weak self] event in
                print("[CRASH_COORD_RECEIVE] at=\(Date()) eventAt=\(event.at) lag=\(Date().timeIntervalSince(event.at)) threadMain=\(Thread.isMainThread)")

                Task { @MainActor in
                    print("[CRASH_COORD_MAINACTOR_HANDLE] at=\(Date()) eventAt=\(event.at) lag=\(Date().timeIntervalSince(event.at))")
                    await self?.handleCrashDetected(
                        at: event.at,
                        gForce: event.gForce,
                        latitude: event.latitude,
                        longitude: event.longitude
                    )
                }
            }

        Task {
            await attemptPendingCrashMetadataUpload()
            await attemptPendingCrashVideoUploads()
        }
    }
    
    func detach() {
        log("Detach crash coordinator")
        cancellable?.cancel()
        cancellable = nil
    }

    func handleCrashDetected(
        at date: Date,
        gForce: Double,
        latitude: Double?,
        longitude: Double?
    ) async {
        log("Crash detected at \(date.iso8601WithFractionalSeconds), g=\(gForce), lat=\(String(describing: latitude)), lon=\(String(describing: longitude))")

        guard let videoSessionId = currentVideoSessionIdProvider(), !videoSessionId.isEmpty else {
            log("Crash skipped: no active videoSessionId")
            return
        }

        log("Active videoSessionId = \(videoSessionId)")

        if shouldDropCrashByCooldown(date) {
            log("Crash ignored by cooldown")
            return
        }

        lastAcceptedCrashAt = date

        let crashId = "crash_" + UUID().uuidString
        let tripSessionId = sensorManager.currentTripSessionId()

        let finalPreSeconds = max(10, settingsStore.preCrashSeconds)
        let finalPostSeconds = max(10, settingsStore.postCrashSeconds)

        log("Accepted crash", crashId: crashId)
        log("Trip session at crash = \(tripSessionId ?? "nil")", crashId: crashId)
        log("Requested final window pre=\(finalPreSeconds)s post=\(finalPostSeconds)s", crashId: crashId)

        var clip = PendingCrashClip(
            id: crashId,
            crashDate: date,
            videoSessionId: videoSessionId,
            linkedTripSessionId: tripSessionId,
            latitude: latitude,
            longitude: longitude,
            maxG: gForce,
            requestedPreSeconds: finalPreSeconds,
            requestedPostSeconds: finalPostSeconds,
            isReadyByTimer: false,
            isCompleted: false,
            stage: .detected,
            temporaryPreclipId: nil,
            finalClipId: nil,
            stopDateOverride: nil,
            isFinalizing: false,
            finalizeAttemptCount: 0,
            nextFinalizeAttemptAt: nil,
            interruptionExtensionSeconds: 0,
            hasUsedInterruptionExtension: false
        )

//        clip.temporaryPreclipId = createPreclipIfPossible(
//            crashId: crashId,
//            videoSessionId: videoSessionId,
//            linkedTripSessionId: tripSessionId,
//            crashDate: date,
//            latitude: latitude,
//            longitude: longitude,
//            maxG: gForce,
//            preSeconds: 5,
//            postSeconds: 1
//        )
//
//        if let preclipId = clip.temporaryPreclipId {
//            clip.stage = .preclipCreated
//            log("Preclip created: \(preclipId)", crashId: crashId)
//        } else {
//            log("Preclip NOT created — continuing with final clip pipeline", crashId: crashId)
//        }
        
        clip.temporaryPreclipId = nil
        log("Preclip export temporarily disabled; continuing with final clip pipeline", crashId: crashId)

        let speedKmh = latestSpeedKmh()
        log("Crash speed snapshot = \(String(describing: speedKmh)) km/h", crashId: crashId)

        enqueueCrashMetadata(
            crashId: crashId,
            videoSessionId: videoSessionId,
            linkedTripSessionId: tripSessionId,
            crashDate: date,
            preSeconds: finalPreSeconds,
            postSeconds: finalPostSeconds,
            latitude: latitude,
            longitude: longitude,
            maxG: gForce,
            speedKmh: speedKmh,
            segmentIds: []
        )

        clip.stage = .metadataQueued
        pendingCrashClips.append(clip)
        pendingCrashClips.sort { $0.crashDate < $1.crashDate }

        log("Metadata enqueued", crashId: crashId)
        log("Pending crash jobs count = \(pendingCrashClips.count)", crashId: crashId)

        await attemptPendingCrashMetadataUpload()

        Task.detached { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(finalPostSeconds) * 1_000_000_000)
            await self?.completePostWindowIfNeeded(for: crashId)
        }
    }

    func completePostWindowIfNeeded(for crashId: String) async {
        guard let index = pendingCrashClips.firstIndex(where: { $0.id == crashId }) else {
            log("Post-window complete ignored: crash not found", crashId: crashId)
            return
        }

        guard !pendingCrashClips[index].isCompleted else {
            log("Post-window complete ignored: crash already completed", crashId: crashId)
            return
        }

        pendingCrashClips[index].isReadyByTimer = true
        pendingCrashClips[index].stage = .waitingPostWindow
        pendingCrashClips[index].nextFinalizeAttemptAt = Date().addingTimeInterval(2.0)

        log("Post-window completed, crash is ready for finalization after short grace", crashId: crashId)
    }
    
    private func applyInterruptionAwareExtensionIfNeeded(
        crashId: String
    ) -> Bool {
        guard let idx = pendingCrashClips.firstIndex(where: { $0.id == crashId }) else {
            return false
        }

        guard !pendingCrashClips[idx].hasUsedInterruptionExtension else {
            return false
        }

        pendingCrashClips[idx].hasUsedInterruptionExtension = true
        pendingCrashClips[idx].interruptionExtensionSeconds = 20
        pendingCrashClips[idx].nextFinalizeAttemptAt = Date().addingTimeInterval(6.0)

        log(
            "Applied interruption-aware extension: +20s post window, retry in 6s",
            crashId: crashId
        )

        return true
    }

    func processTimerReadyCrashClips() async {
        let now = Date()

        let readyIds = pendingCrashClips
            .filter {
                $0.isReadyByTimer &&
                !$0.isCompleted &&
                !$0.isFinalizing &&
                ($0.nextFinalizeAttemptAt == nil || $0.nextFinalizeAttemptAt! <= now)
            }
            .sorted { $0.crashDate < $1.crashDate }
            .map(\.id)

        guard !readyIds.isEmpty else { return }

        log("Processing timer-ready crash clips count = \(readyIds.count)")

        for crashId in readyIds {
            await finalizeCrashJob(crashId: crashId, stopDate: nil)
        }

        pruneCompletedCrashJobs()
    }

    func processCrashClipsForStop(stopDate: Date) async {
        let ids = pendingCrashClips
            .filter { !$0.isCompleted && !$0.isFinalizing }
            .sorted { $0.crashDate < $1.crashDate }
            .map(\.id)

        guard !ids.isEmpty else { return }

        log("Processing crash clips for stop at \(stopDate.iso8601WithFractionalSeconds), count = \(ids.count)")

        for crashId in ids {
            await finalizeCrashJob(crashId: crashId, stopDate: stopDate)
        }

        pruneCompletedCrashJobs()
    }

    func attemptPendingCrashMetadataUpload() async {
        guard !pendingCrashMetadataQueue.isEmpty else { return }
        guard !isDrainingMetadataQueue else {
            log("Metadata drain skipped: already in progress")
            return
        }

        isDrainingMetadataQueue = true
        defer { isDrainingMetadataQueue = false }

        let now = Date()
        let candidates = pendingCrashMetadataQueue

        log("Attempting crash metadata upload, queue count = \(pendingCrashMetadataQueue.count)")

        for item in candidates {
            guard let idx = pendingCrashMetadataQueue.firstIndex(where: { $0.crashId == item.crashId }) else {
                continue
            }

            if pendingCrashMetadataQueue[idx].isInFlight { continue }
            if pendingCrashMetadataQueue[idx].nextRetryAt > now { continue }

            pendingCrashMetadataQueue[idx].isInFlight = true
            persistPendingCrashMetadataQueue()

            let request = CrashClipEventRequest(
                crash_clip_id: item.crashId,
                video_session_id: item.videoSessionId,
                linked_trip_session_id: item.linkedTripSessionId,
                crash_detected_at: item.crashDate.iso8601WithFractionalSeconds,
                pre_seconds: item.preSeconds,
                post_seconds: item.postSeconds,
                segment_ids: item.segmentIds,
                lat: item.latitude,
                lon: item.longitude,
                max_g: item.maxG,
                speed_kmh: item.speedKmh
            )

            do {
                log("Posting crash metadata", crashId: item.crashId)
                try await networkManager.postCrashClip(request)
                log("Metadata upload SUCCESS", crashId: item.crashId)

                pendingCrashMetadataQueue.removeAll(where: { $0.crashId == item.crashId })
                persistPendingCrashMetadataQueue()
            } catch {
                guard let retryIdx = pendingCrashMetadataQueue.firstIndex(where: { $0.crashId == item.crashId }) else {
                    continue
                }

                pendingCrashMetadataQueue[retryIdx].retryCount += 1
                pendingCrashMetadataQueue[retryIdx].isInFlight = false

                if pendingCrashMetadataQueue[retryIdx].retryCount <= maxMetadataRetryCount {
                    pendingCrashMetadataQueue[retryIdx].nextRetryAt = nextRetryDate(
                        forAttempt: pendingCrashMetadataQueue[retryIdx].retryCount,
                        maxDelay: 300
                    )
                    log(
                        "Metadata retry \(pendingCrashMetadataQueue[retryIdx].retryCount), next at \(pendingCrashMetadataQueue[retryIdx].nextRetryAt.iso8601WithFractionalSeconds), error=\(error)",
                        crashId: item.crashId
                    )
                } else {
                    log("Metadata FAILED permanently, removing from queue, error=\(error)", crashId: item.crashId)
                    pendingCrashMetadataQueue.removeAll(where: { $0.crashId == item.crashId })
                }

                persistPendingCrashMetadataQueue()
            }
        }
    }
    
    func attemptPendingCrashVideoUploads() async {
        guard !pendingCrashVideoUploadQueue.isEmpty else { return }
        guard !isDrainingVideoQueue else {
            log("Video drain skipped: already in progress")
            return
        }

        isDrainingVideoQueue = true
        defer { isDrainingVideoQueue = false }

        let now = Date()
        let candidates = pendingCrashVideoUploadQueue

        log("Attempting crash video uploads, queue count = \(pendingCrashVideoUploadQueue.count)")

        for item in candidates {
            guard let idx = pendingCrashVideoUploadQueue.firstIndex(where: { $0.crashId == item.crashId }) else {
                continue
            }

            if pendingCrashVideoUploadQueue[idx].isInFlight { continue }
            if pendingCrashVideoUploadQueue[idx].nextRetryAt > now { continue }

            pendingCrashVideoUploadQueue[idx].isInFlight = true
            persistPendingCrashVideoUploadQueue()

            let fileURL = URL(fileURLWithPath: item.filePath)

            guard FileManager.default.fileExists(atPath: fileURL.path) else {
                log("Crash clip file missing, dropping upload task: \(fileURL.lastPathComponent)", crashId: item.crashId)
                pendingCrashVideoUploadQueue.removeAll(where: { $0.crashId == item.crashId })
                persistPendingCrashVideoUploadQueue()
                continue
            }

            log("Uploading crash video file \(fileURL.lastPathComponent)", crashId: item.crashId)

            let result = await networkManager.uploadCrashClipResult(
                videoSessionId: item.videoSessionId,
                crashClipId: item.crashId,
                fileURL: fileURL
            )

            switch result {
            case .success:
                log("Video upload SUCCESS", crashId: item.crashId)
                pendingCrashVideoUploadQueue.removeAll(where: { $0.crashId == item.crashId })
                persistPendingCrashVideoUploadQueue()

            case .permanentFailure:
                log("Video upload FAILED permanently", crashId: item.crashId)
                pendingCrashVideoUploadQueue.removeAll(where: { $0.crashId == item.crashId })
                persistPendingCrashVideoUploadQueue()

            case .retryableFailure:
                guard let retryIdx = pendingCrashVideoUploadQueue.firstIndex(where: { $0.crashId == item.crashId }) else {
                    continue
                }

                pendingCrashVideoUploadQueue[retryIdx].retryCount += 1
                pendingCrashVideoUploadQueue[retryIdx].isInFlight = false

                if pendingCrashVideoUploadQueue[retryIdx].retryCount <= maxCrashVideoRetryCount {
                    pendingCrashVideoUploadQueue[retryIdx].nextRetryAt = nextCrashUploadRetryDate(
                        forAttempt: pendingCrashVideoUploadQueue[retryIdx].retryCount
                    )
                    log(
                        "Video retry \(pendingCrashVideoUploadQueue[retryIdx].retryCount), next at \(pendingCrashVideoUploadQueue[retryIdx].nextRetryAt.iso8601WithFractionalSeconds)",
                        crashId: item.crashId
                    )
                } else {
                    log("Video upload retry limit reached, removing from queue", crashId: item.crashId)
                    pendingCrashVideoUploadQueue.removeAll(where: { $0.crashId == item.crashId })
                }

                persistPendingCrashVideoUploadQueue()
            }
        }
    }

    private func shouldDropCrashByCooldown(_ date: Date) -> Bool {
        if let lastAcceptedCrashAt {
            let delta = date.timeIntervalSince(lastAcceptedCrashAt)
            log("Cooldown check: last=\(lastAcceptedCrashAt.iso8601WithFractionalSeconds), now=\(date.iso8601WithFractionalSeconds), delta=\(delta)")
            return delta < cooldownSeconds
        }

        log("Cooldown check: no previous accepted crash")
        return false
    }

    private func finalizeCrashJob(crashId: String, stopDate: Date?) async {
        guard let index = pendingCrashClips.firstIndex(where: { $0.id == crashId }) else {
            log("Finalize skipped: crash not found", crashId: crashId)
            return
        }

        guard !pendingCrashClips[index].isCompleted else {
            log("Finalize skipped: crash already completed", crashId: crashId)
            return
        }

        guard !pendingCrashClips[index].isFinalizing else {
            log("Finalize skipped: crash already finalizing", crashId: crashId)
            return
        }

        pendingCrashClips[index].isFinalizing = true
        defer {
            if let idx = pendingCrashClips.firstIndex(where: { $0.id == crashId }) {
                pendingCrashClips[idx].isFinalizing = false
            }
        }

        let clip = pendingCrashClips[index]

        let finalPreSeconds = max(10, clip.requestedPreSeconds)
        let configuredPost = max(10, clip.requestedPostSeconds)

        let effectiveConfiguredPost = configuredPost + clip.interruptionExtensionSeconds

        let finalPostSeconds: Int
        if let stopDate {
            let available = max(0, Int(stopDate.timeIntervalSince(clip.crashDate)))
            finalPostSeconds = min(effectiveConfiguredPost, available)
        } else {
            finalPostSeconds = effectiveConfiguredPost
        }

        log("Finalizing crash job, stopDate=\(String(describing: stopDate?.iso8601WithFractionalSeconds))", crashId: crashId)
        log("Final window pre=\(finalPreSeconds)s post=\(finalPostSeconds)s", crashId: crashId)

        let start = clip.crashDate.addingTimeInterval(TimeInterval(-finalPreSeconds))
        let end = clip.crashDate.addingTimeInterval(TimeInterval(finalPostSeconds))

        log("Protecting segments from \(start.iso8601WithFractionalSeconds) to \(end.iso8601WithFractionalSeconds)", crashId: crashId)

        do {
            let protectedSegments = try archiveStore.protectSegmentsForCrash(from: start, to: end)
            log("Segments for final clip: \(protectedSegments.count)", crashId: crashId)

            guard !protectedSegments.isEmpty else {
                log("No segments found -> scheduling retry", crashId: crashId)

                if let idx = pendingCrashClips.firstIndex(where: { $0.id == crashId }) {
                    pendingCrashClips[idx].finalizeAttemptCount += 1
                    pendingCrashClips[idx].nextFinalizeAttemptAt = Date().addingTimeInterval(3.0)
                }
                return
            }

            let finalClip = try archiveStore.createCrashClip(
                from: protectedSegments,
                crashClipId: clip.id,
                videoSessionId: clip.videoSessionId,
                crashAt: clip.crashDate,
                preSeconds: finalPreSeconds,
                postSeconds: finalPostSeconds,
                linkedTripSessionId: clip.linkedTripSessionId,
                latitude: clip.latitude,
                longitude: clip.longitude,
                maxG: clip.maxG
            )

            log("Final crash clip created: \(finalClip.id)", crashId: crashId)

            if let tempId = clip.temporaryPreclipId, tempId != finalClip.id {
                try? archiveStore.deleteArchiveItems(ids: [tempId])
                log("Temporary preclip deleted: \(tempId)", crashId: crashId)
            }

            if let fileURL = finalClip.fileURL {
                enqueueCrashVideoUpload(
                    crashId: finalClip.id,
                    videoSessionId: finalClip.videoSessionId ?? clip.videoSessionId,
                    fileURL: fileURL
                )
                log("Enqueued video upload: \(fileURL.lastPathComponent)", crashId: crashId)
            } else {
                log("Final crash clip has no fileURL, upload skipped", crashId: crashId)
            }

            if let idx = pendingCrashClips.firstIndex(where: { $0.id == crashId }) {
                pendingCrashClips[idx].finalClipId = finalClip.id
                pendingCrashClips[idx].isCompleted = true
                pendingCrashClips[idx].stage = .completed
                pendingCrashClips[idx].nextFinalizeAttemptAt = nil
            }

            log("Crash pipeline COMPLETED", crashId: crashId)
        } catch {
            log("Failed to finalize crash clip, error=\(error)", crashId: crashId)

            guard stopDate == nil else {
                let nsError = error as NSError
                let isNoSegmentsError =
                    nsError.domain == "DashcamCrashClip" &&
                    nsError.code == 1001

                if isNoSegmentsError {
                    if applyInterruptionAwareExtensionIfNeeded(crashId: crashId) {
                        log("Finalize failed during stop flow; interruption-aware extension applied", crashId: crashId)
                        return
                    }

                    if let idx = pendingCrashClips.firstIndex(where: { $0.id == crashId }) {
                        pendingCrashClips[idx].finalizeAttemptCount += 1

                        if pendingCrashClips[idx].finalizeAttemptCount >= 2 {
                            pendingCrashClips[idx].isCompleted = true
                            pendingCrashClips[idx].stage = .failed
                            pendingCrashClips[idx].nextFinalizeAttemptAt = nil

                            log(
                                "Crash job marked FAILED during stop flow: no stabilized segments for export",
                                crashId: crashId
                            )
                            return
                        }
                    }
                }

                log("Finalize failed during stop flow; no auto-retry inside stop", crashId: crashId)
                return
            }

            if let idx = pendingCrashClips.firstIndex(where: { $0.id == crashId }) {
                pendingCrashClips[idx].finalizeAttemptCount += 1

                let attempt = pendingCrashClips[idx].finalizeAttemptCount
                let nsError = error as NSError

                let isNoSegmentsError =
                    nsError.domain == "DashcamCrashClip" &&
                    nsError.code == 1001

                if isNoSegmentsError {
                    if applyInterruptionAwareExtensionIfNeeded(crashId: crashId) {
                        return
                    }

                    if attempt >= 3 {
                        pendingCrashClips[idx].isCompleted = true
                        pendingCrashClips[idx].stage = .failed
                        pendingCrashClips[idx].nextFinalizeAttemptAt = nil

                        log(
                            "Crash job marked FAILED after \(attempt) attempts: no stabilized segments for export",
                            crashId: crashId
                        )
                        return
                    }
                }

                let delay = min(Double(2 * attempt), 8.0)
                pendingCrashClips[idx].nextFinalizeAttemptAt = Date().addingTimeInterval(delay)

                log("Finalize retry scheduled in \(delay)s (attempt \(attempt))", crashId: crashId)
            }
        }
    }

//    private func createPreclipIfPossible(
//        crashId: String,
//        videoSessionId: String,
//        linkedTripSessionId: String?,
//        crashDate: Date,
//        latitude: Double?,
//        longitude: Double?,
//        maxG: Double?,
//        preSeconds: Int,
//        postSeconds: Int
//    ) -> String? {
//        let start = crashDate.addingTimeInterval(TimeInterval(-preSeconds))
//        let end = crashDate.addingTimeInterval(TimeInterval(postSeconds))
//
//        log("Creating preclip window pre=\(preSeconds)s post=\(postSeconds)s", crashId: crashId)
//        log("Preclip time range \(start.iso8601WithFractionalSeconds) -> \(end.iso8601WithFractionalSeconds)", crashId: crashId)
//
//        do {
//            let segments = try archiveStore.protectSegmentsForCrash(from: start, to: end)
//            log("Segments for preclip: \(segments.count)", crashId: crashId)
//
//            guard !segments.isEmpty else {
//                return nil
//            }
//
//            let preclipId = "preclip_" + crashId
//
//            let created = try archiveStore.createCrashClip(
//                from: segments,
//                crashClipId: preclipId,
//                videoSessionId: videoSessionId,
//                crashAt: crashDate,
//                preSeconds: preSeconds,
//                postSeconds: postSeconds,
//                linkedTripSessionId: linkedTripSessionId,
//                latitude: latitude,
//                longitude: longitude,
//                maxG: maxG
//            )
//
//            return created.id
//        } catch {
//            log("Failed to create preclip (non-blocking), error=\(error)", crashId: crashId)
//            return nil
//        }
//    }

    private func latestSpeedKmh() -> Double? {
        guard let sample = sensorManager.latestSample(),
              let speed = sample.speed_m_s else {
            return nil
        }
        return speed * 3.6
    }

    private func enqueueCrashMetadata(
        crashId: String,
        videoSessionId: String,
        linkedTripSessionId: String?,
        crashDate: Date,
        preSeconds: Int,
        postSeconds: Int,
        latitude: Double?,
        longitude: Double?,
        maxG: Double?,
        speedKmh: Double?,
        segmentIds: [String]
    ) {
        guard !pendingCrashMetadataQueue.contains(where: { $0.crashId == crashId }) else {
            log("Metadata already queued", crashId: crashId)
            return
        }

        let item = PendingCrashMetadata(
            crashId: crashId,
            videoSessionId: videoSessionId,
            linkedTripSessionId: linkedTripSessionId,
            crashDate: crashDate,
            preSeconds: preSeconds,
            postSeconds: postSeconds,
            latitude: latitude,
            longitude: longitude,
            maxG: maxG,
            speedKmh: speedKmh,
            segmentIds: segmentIds,
            retryCount: 0,
            nextRetryAt: Date(),
            isInFlight: false
        )

        pendingCrashMetadataQueue.append(item)
        persistPendingCrashMetadataQueue()
    }

    private func enqueueCrashVideoUpload(
        crashId: String,
        videoSessionId: String,
        fileURL: URL
    ) {
        guard !pendingCrashVideoUploadQueue.contains(where: { $0.crashId == crashId }) else {
            log("Video upload already queued", crashId: crashId)
            return
        }

        let item = PendingCrashVideoUpload(
            crashId: crashId,
            videoSessionId: videoSessionId,
            filePath: fileURL.path,
            retryCount: 0,
            nextRetryAt: Date(),
            isInFlight: false
        )

        pendingCrashVideoUploadQueue.append(item)
        persistPendingCrashVideoUploadQueue()
    }

    private func pruneCompletedCrashJobs() {
        let before = pendingCrashClips.count

        let completedCount = pendingCrashClips.filter { $0.isCompleted && $0.stage == .completed }.count
        let failedCount = pendingCrashClips.filter { $0.isCompleted && $0.stage == .failed }.count

        pendingCrashClips.removeAll(where: { $0.isCompleted })

        let after = pendingCrashClips.count

        if before != after {
            log("Pruned crash jobs: completed=\(completedCount), failed=\(failedCount), before=\(before), after=\(after)")
        }
    }

    private func nextRetryDate(forAttempt attempt: Int, maxDelay: TimeInterval) -> Date {
        let delay = min(pow(2.0, Double(attempt)), maxDelay)
        return Date().addingTimeInterval(delay)
    }

    private func nextCrashUploadRetryDate(forAttempt attempt: Int) -> Date {
        let delay = min(pow(2.0, Double(attempt)), 600.0)
        return Date().addingTimeInterval(delay)
    }

    private func persistPendingCrashMetadataQueue() {
        do {
            let data = try JSONEncoder().encode(pendingCrashMetadataQueue)
            try data.write(to: metadataQueueFileURL, options: .atomic)
            log("Persisted metadata queue, count = \(pendingCrashMetadataQueue.count)")
        } catch {
            log("Failed to persist metadata queue: \(error)")
        }
    }

    private func restorePendingCrashMetadataQueue() {
        do {
            let data = try Data(contentsOf: metadataQueueFileURL)
            pendingCrashMetadataQueue = try JSONDecoder().decode([PendingCrashMetadata].self, from: data)
            log("Restored metadata queue, count = \(pendingCrashMetadataQueue.count)")
        } catch {
            pendingCrashMetadataQueue = []
            log("Metadata queue not restored, starting empty")
        }
    }

    private func persistPendingCrashVideoUploadQueue() {
        do {
            let data = try JSONEncoder().encode(pendingCrashVideoUploadQueue)
            try data.write(to: videoUploadQueueFileURL, options: .atomic)
            log("Persisted video upload queue, count = \(pendingCrashVideoUploadQueue.count)")
        } catch {
            log("Failed to persist video upload queue: \(error)")
        }
    }

    private func restorePendingCrashVideoUploadQueue() {
        do {
            let data = try Data(contentsOf: videoUploadQueueFileURL)
            pendingCrashVideoUploadQueue = try JSONDecoder().decode([PendingCrashVideoUpload].self, from: data)
            log("Restored video upload queue, count = \(pendingCrashVideoUploadQueue.count)")
        } catch {
            pendingCrashVideoUploadQueue = []
            log("Video upload queue not restored, starting empty")
        }
    }

    private func log(_ message: String, crashId: String? = nil) {
        let prefix = crashId.map { "[CRASH \($0)]" } ?? "[CRASH]"
        print("\(prefix) \(message)")
    }
}

private extension Date {
    var iso8601WithFractionalSeconds: String {
        ISO8601DateFormatter.withFractionalSeconds.string(from: self)
    }
}

private extension ISO8601DateFormatter {
    static let withFractionalSeconds: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()
}
