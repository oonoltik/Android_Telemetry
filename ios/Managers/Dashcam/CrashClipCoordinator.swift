import Foundation
import Combine

final class CrashClipCoordinator {
    private let sensorManager: SensorManager
    private let archiveStore: VideoArchiveStore
    private let networkManager: NetworkManager
    private let settingsStore: DashcamSettingsStore
    private var cancellable: AnyCancellable?

    init(sensorManager: SensorManager, archiveStore: VideoArchiveStore, networkManager: NetworkManager, settingsStore: DashcamSettingsStore) {
        self.sensorManager = sensorManager
        self.archiveStore = archiveStore
        self.networkManager = networkManager
        self.settingsStore = settingsStore
    }

    func attach() {
        cancellable = sensorManager.crashEventPublisher
            .receive(on: DispatchQueue.global(qos: .utility))
            .sink { [weak self] event in
                Task {
                    await self?.handleCrashDetected(at: event.at, gForce: event.gForce, latitude: event.latitude, longitude: event.longitude)
                }
            }
    }

    func detach() {
        cancellable?.cancel()
        cancellable = nil
    }

    func handleCrashDetected(at date: Date, gForce: Double, latitude: Double?, longitude: Double?) async {
        do {
            let start = date.addingTimeInterval(TimeInterval(-settingsStore.maxSegmentDurationSeconds))
            let end = date.addingTimeInterval(TimeInterval(settingsStore.maxSegmentDurationSeconds))

            let segments = try archiveStore.protectSegmentsForCrash(from: start, to: end)
            guard !segments.isEmpty else { return }

            let clip = try archiveStore.createCrashClip(
                from: segments,
                crashAt: date,
                preSeconds: settingsStore.maxSegmentDurationSeconds,
                postSeconds: settingsStore.maxSegmentDurationSeconds,
                linkedTripSessionId: sensorManager.currentTripSessionId(),
                latitude: latitude,
                longitude: longitude,
                maxG: gForce
            )

            try await networkManager.postCrashClip(
                CrashClipEventRequest(
                    crash_clip_id: clip.id,
                    video_session_id: nil,
                    linked_trip_session_id: clip.linkedTripSessionId,
                    crash_detected_at: ISO8601DateFormatter().string(from: date),
                    pre_seconds: settingsStore.maxSegmentDurationSeconds,
                    post_seconds: settingsStore.maxSegmentDurationSeconds,
                    segment_ids: clip.segmentIds,
                    lat: latitude,
                    lon: longitude,
                    max_g: gForce,
                    speed_kmh: nil
                )
            )
        } catch {
            print("[DashcamCrash] \(error)")
        }
    }
}
