import Foundation
import Combine

@MainActor
final class VideoArchiveViewModel: ObservableObject {
    @Published var items: [DashcamArchiveItem] = []
    @Published var selectedIds: Set<String> = []
    @Published var errorText: String?

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
}
