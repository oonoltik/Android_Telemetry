import Foundation

final class StorageQuotaManager {
    private let archiveStore: VideoArchiveStore
    private let settingsStore: DashcamSettingsStore

    init(archiveStore: VideoArchiveStore, settingsStore: DashcamSettingsStore) {
        self.archiveStore = archiveStore
        self.settingsStore = settingsStore
    }

    func enforceQuotaIfNeeded() async throws {
        let usage = try archiveStore.totalUsageBytes()
        let quota = maximumQuotaBytes()
        guard usage > quota else { return }
        let ids = try archiveStore.oldestDeletableNormalSegmentIds(limitBytesToFree: usage - quota)
        if ids.isEmpty { throw DashcamError.quotaExceeded }
        try archiveStore.deleteArchiveItems(ids: ids)
    }

    func notifySegmentCommitted(sizeBytes: Int64) async {
        _ = sizeBytes
        try? await enforceQuotaIfNeeded()
    }

    func maximumQuotaBytes() -> Int64 { settingsStore.storageQuotaBytes }
}
