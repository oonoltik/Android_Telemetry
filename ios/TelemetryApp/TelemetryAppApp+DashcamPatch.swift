import SwiftUI
import Foundation

final class DashcamBackgroundSessionBridge {
    static let shared = DashcamBackgroundSessionBridge()
    var completionHandler: (() -> Void)?
}

extension TelemetryAppApp {
    @MainActor
    static func makeDashcamStack(sensorManager: SensorManager) -> (DashcamManager, VideoArchiveStore) {
        let settings = UserDefaultsDashcamSettingsStore()
        let archiveStore = try! JSONVideoArchiveStore()
        let tripCoordinator = DashcamTripCoordinator(sensorManager: sensorManager)
        let quotaManager = StorageQuotaManager(archiveStore: archiveStore, settingsStore: settings)

        let dashcamManager = DashcamManager(
            sensorManager: sensorManager,
            networkManager: .shared,
            archiveStore: archiveStore,
            tripCoordinator: tripCoordinator,
            quotaManager: quotaManager,
            capabilityService: CameraCapabilityService(),
            settingsStore: settings
        )

        let crashCoordinator = CrashClipCoordinator(
            sensorManager: sensorManager,
            archiveStore: archiveStore,
            networkManager: .shared,
            settingsStore: settings,
            currentVideoSessionIdProvider: { [weak dashcamManager] in
                dashcamManager?.crashVideoSessionIdForLateEvents()
            }
        )

        dashcamManager.attachCrashCoordinator(crashCoordinator)

        return (dashcamManager, archiveStore)
    }
}
