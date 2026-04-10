import Foundation
import Combine
import Photos

@MainActor
final class VideoArchiveViewModel: ObservableObject {
    @Published var items: [DashcamArchiveItem] = []
    @Published var selectedIds: Set<String> = []
    @Published var errorText: String?
    @Published var saveResultText: String?

    private let archiveStore: VideoArchiveStore
    private let settingsStore: DashcamSettingsStore
    

    init(archiveStore: VideoArchiveStore, settingsStore: DashcamSettingsStore = UserDefaultsDashcamSettingsStore()) {
        self.archiveStore = archiveStore
        self.settingsStore = settingsStore
    }

    var normalItems: [DashcamArchiveItem] {
        items.filter { $0.kind == .normal }
    }

    var crashItems: [DashcamArchiveItem] {
        items.filter { $0.kind == .crash }
    }

    var allVisibleSelected: Bool {
        !items.isEmpty && items.allSatisfy { selectedIds.contains($0.id) }
    }

    var selectedItems: [DashcamArchiveItem] {
        items.filter { selectedIds.contains($0.id) }
    }

    var selectedCount: Int {
        selectedItems.count
    }

    var selectedTotalSizeBytes: Int64 {
        selectedItems.reduce(0) { $0 + $1.sizeBytes }
    }

    var selectedTotalSizeMBText: String {
        String(format: "%.1f", Double(selectedTotalSizeBytes) / 1_048_576.0)
    }

    func reload() {
        do {
            items = try archiveStore.listArchiveItems()
            selectedIds = selectedIds.intersection(Set(items.map(\.id)))
            errorText = nil
        } catch {
            errorText = error.localizedDescription
        }
    }

    func isSelected(_ id: String) -> Bool {
        selectedIds.contains(id)
    }

    func toggleSelection(_ id: String) {
        if selectedIds.contains(id) {
            selectedIds.remove(id)
        } else {
            selectedIds.insert(id)
        }
    }

    func clearSelection() {
        selectedIds.removeAll()
    }

    func selectAll() {
        selectedIds = Set(items.map(\.id))
    }

    func deleteSelected() {
        deleteItems(ids: Array(selectedIds))
    }

    func deleteItems(ids: [String]) {
        do {
            try archiveStore.deleteArchiveItems(ids: ids)
            for id in ids {
                selectedIds.remove(id)
            }
            reload()
        } catch {
            errorText = error.localizedDescription
        }
    }

    var totalSizeBytes: Int64 {
        items.reduce(0) { $0 + $1.sizeBytes }
    }

    var totalSizeGB: Double {
        Double(totalSizeBytes) / 1_073_741_824.0
    }

    var usagePercent: Int {
        let maxStorageBytes = settingsStore.storageQuotaBytes
        guard maxStorageBytes > 0 else { return 0 }
        return Int((Double(totalSizeBytes) / Double(maxStorageBytes)) * 100)
    }

    private func requestPhotoLibraryAccess() async -> Bool {
        let current = PHPhotoLibrary.authorizationStatus(for: .addOnly)
        switch current {
        case .authorized, .limited:
            return true
        case .notDetermined:
            let status = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
            return status == .authorized || status == .limited
        default:
            return false
        }
    }

    func saveSelectedToPhotoLibrary() async {
        let itemsToSave = selectedItems

        guard !itemsToSave.isEmpty else {
            saveResultText = "Ничего не выбрано для сохранения."
            return
        }

        let granted = await requestPhotoLibraryAccess()
        guard granted else {
            saveResultText = "Доступ к медиатеке не выдан."
            return
        }

        var saved = 0
        var failed = 0

        for item in itemsToSave {
            let url = item.playbackURL

            guard FileManager.default.fileExists(atPath: url.path) else {
                failed += 1
                continue
            }

            do {
                try await saveVideoToPhotoLibrary(url: url)
                try? archiveStore.markAsSavedToPhotoLibrary(id: item.id)
                saved += 1
            } catch {
                failed += 1
            }
        }

        if failed == 0 {
            saveResultText = "Сохранено в медиатеку: \(saved)"
        } else {
            saveResultText = "Сохранено: \(saved), не удалось сохранить: \(failed)"
        }

        reload()
    }

    private func saveVideoToPhotoLibrary(url: URL) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            PHPhotoLibrary.shared().performChanges({
                PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: url)
            }, completionHandler: { success, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if success {
                    continuation.resume(returning: ())
                } else {
                    continuation.resume(
                        throwing: DashcamError.unknown("Не удалось сохранить видео в медиатеку")
                    )
                }
            })
        }
    }
}
