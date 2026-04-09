import SwiftUI

extension TelemetryAppApp {
    @MainActor
    static func makeDashcamStack(sensorManager: SensorManager) -> (DashcamManager, VideoArchiveStore) {
        let settings = UserDefaultsDashcamSettingsStore()
        let archiveStore = try! JSONVideoArchiveStore()
        let tripCoordinator = DashcamTripCoordinator(sensorManager: sensorManager)
        let quotaManager = StorageQuotaManager(archiveStore: archiveStore, settingsStore: settings)
        let crashCoordinator = CrashClipCoordinator(sensorManager: sensorManager, archiveStore: archiveStore, networkManager: .shared, settingsStore: settings)
        let dashcamManager = DashcamManager(sensorManager: sensorManager, networkManager: .shared, archiveStore: archiveStore, tripCoordinator: tripCoordinator, crashCoordinator: crashCoordinator, quotaManager: quotaManager, capabilityService: CameraCapabilityService(), settingsStore: settings)
        return (dashcamManager, archiveStore)
    }
}
