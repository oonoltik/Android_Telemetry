import Foundation

final class StorageQuotaManager {
    private let archiveStore: VideoArchiveStore
//    private let settingsStore: DashcamSettingsStore

    // Консервативный резерв под один следующий сегмент.
    // При необходимости потом можно вынести в настройки.
//    private let reservedBytesForNextSegment: Int64 = 300 * 1024 * 1024
    private let maxArchiveBytes: Int64 = 10 * 1024 * 1024 * 1024

    init(archiveStore: VideoArchiveStore, settingsStore: DashcamSettingsStore) {
        self.archiveStore = archiveStore
    }

//    func maximumQuotaBytes() -> Int64 {
//        settingsStore.storageQuotaBytes
//    }
    
    private func cleanupArchiveIfNeeded() throws {
        let totalBytes = try archiveStore.totalArchiveUsageBytes()

        guard totalBytes > maxArchiveBytes else {
            return
        }

        let bytesToFree = totalBytes - maxArchiveBytes
        let idsToDelete = try archiveStore.oldestDeletableNormalSegmentIds(limitBytesToFree: bytesToFree)

        guard !idsToDelete.isEmpty else {
            return
        }

        try archiveStore.deleteArchiveItems(ids: idsToDelete)
    }

    /// Можно ли вообще стартовать новую запись:
    /// в архиве должен быть запас под хотя бы один новый сегмент.
    func ensureCanStartRecording() throws -> Bool {
        try cleanupArchiveIfNeeded()

        let totalBytes = try archiveStore.totalArchiveUsageBytes()
        return totalBytes < maxArchiveBytes
    }

    /// Вызывается после фиксации сегмента.
    /// Возвращает true, если можно стартовать следующий сегмент.
    func canContinueRecordingAfterSegmentCommit() throws -> Bool {
        try cleanupArchiveIfNeeded()

        let totalBytes = try archiveStore.totalArchiveUsageBytes()
        return totalBytes < maxArchiveBytes
    }

    /// Поддержка старого вызова
    func notifySegmentCommitted(sizeBytes: Int64) async {
        _ = sizeBytes
        try? cleanupArchiveIfNeeded()
    }

//    private func cleanupIfNeeded(requiredHeadroomBytes: Int64) throws {
//        let quota = maximumQuotaBytes()
//        let total = try archiveStore.totalArchiveUsageBytes()
//
//        let allowedUsage = quota - requiredHeadroomBytes
//        guard total > allowedUsage else { return }
//
//        let bytesToFree = total - allowedUsage
//        let ids = try archiveStore.oldestDeletableNormalSegmentIds(limitBytesToFree: bytesToFree)
//
//        guard !ids.isEmpty else {
//            throw DashcamError.archiveBlockedByCrashRecords
//        }
//
//        try archiveStore.deleteArchiveItems(ids: ids)
//
//        let totalAfterCleanup = try archiveStore.totalArchiveUsageBytes()
//        guard totalAfterCleanup <= allowedUsage else {
//            throw DashcamError.archiveBlockedByCrashRecords
//        }
//    }
}
