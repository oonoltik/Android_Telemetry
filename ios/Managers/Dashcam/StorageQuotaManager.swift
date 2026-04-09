import Foundation

final class StorageQuotaManager {
    private let archiveStore: VideoArchiveStore
    private let settingsStore: DashcamSettingsStore

    // Консервативный резерв под один следующий сегмент.
    // При необходимости потом можно вынести в настройки.
    private let reservedBytesForNextSegment: Int64 = 300 * 1024 * 1024

    init(archiveStore: VideoArchiveStore, settingsStore: DashcamSettingsStore) {
        self.archiveStore = archiveStore
        self.settingsStore = settingsStore
    }

    func maximumQuotaBytes() -> Int64 {
        settingsStore.storageQuotaBytes
    }

    /// Можно ли вообще стартовать новую запись:
    /// в архиве должен быть запас под хотя бы один новый сегмент.
    func ensureCanStartRecording() async throws {
        try cleanupIfNeeded(requiredHeadroomBytes: reservedBytesForNextSegment)

        let total = try archiveStore.totalArchiveUsageBytes()
        let quota = maximumQuotaBytes()

        guard total + reservedBytesForNextSegment <= quota else {
            throw DashcamError.archiveBlockedByCrashRecords
        }
    }

    /// Вызывается после фиксации сегмента.
    /// Возвращает true, если можно стартовать следующий сегмент.
    func canContinueRecordingAfterSegmentCommit() async throws -> Bool {
        try cleanupIfNeeded(requiredHeadroomBytes: reservedBytesForNextSegment)

        let total = try archiveStore.totalArchiveUsageBytes()
        let quota = maximumQuotaBytes()

        return total + reservedBytesForNextSegment <= quota
    }

    /// Поддержка старого вызова
    func notifySegmentCommitted(sizeBytes: Int64) async {
        _ = sizeBytes
        try? cleanupIfNeeded(requiredHeadroomBytes: 0)
    }

    private func cleanupIfNeeded(requiredHeadroomBytes: Int64) throws {
        let quota = maximumQuotaBytes()
        let total = try archiveStore.totalArchiveUsageBytes()

        let allowedUsage = quota - requiredHeadroomBytes
        guard total > allowedUsage else { return }

        let bytesToFree = total - allowedUsage
        let ids = try archiveStore.oldestDeletableNormalSegmentIds(limitBytesToFree: bytesToFree)

        guard !ids.isEmpty else {
            throw DashcamError.archiveBlockedByCrashRecords
        }

        try archiveStore.deleteArchiveItems(ids: ids)

        let totalAfterCleanup = try archiveStore.totalArchiveUsageBytes()
        guard totalAfterCleanup <= allowedUsage else {
            throw DashcamError.archiveBlockedByCrashRecords
        }
    }
}
