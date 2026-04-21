import SwiftUI
import AVKit

struct VideoArchiveView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject var viewModel: VideoArchiveViewModel

    let isInteractionLocked: Bool

    @State private var playbackItem: PlaybackItem?
    
    @State private var showSaveToLibraryConfirm = false
    @State private var showSaveResultAlert = false
    @State private var isSavingToLibrary = false
    @State private var showDeleteNormalConfirm = false
    @State private var showDeleteCrashConfirmStep1 = false
    @State private var showDeleteCrashConfirmStep2 = false
    @State private var showRecordingLockAlert = false
    @State private var showFileMissingAlert = false
    @State private var selectedFilter: ArchiveFilter = .all
    
    

    init(viewModel: VideoArchiveViewModel, isInteractionLocked: Bool = false) {
        _viewModel = StateObject(wrappedValue: viewModel)
        self.isInteractionLocked = isInteractionLocked
    }

    private var normalItems: [DashcamArchiveItem] {
        viewModel.normalItems
    }

    private var crashItems: [DashcamArchiveItem] {
        viewModel.crashItems
    }
    
    private var visibleNormalItems: [DashcamArchiveItem] {
        switch selectedFilter {
        case .all, .normal:
            return normalItems
        case .crash:
            return []
        }
    }

    private var visibleCrashItems: [DashcamArchiveItem] {
        switch selectedFilter {
        case .all, .crash:
            return crashItems
        case .normal:
            return []
        }
    }

    private var selectedNormalIds: [String] {
        normalItems
            .filter { viewModel.selectedIds.contains($0.id) }
            .map(\.id)
    }

    private var selectedCrashIds: [String] {
        Array(
            Set(
                crashItems
                    .filter { viewModel.selectedIds.contains($0.id) }
                    .compactMap { $0.deletionGroupId ?? $0.id }
            )
        )
    }

    private var hasSelection: Bool {
        !viewModel.selectedIds.isEmpty
    }

    private var hasNormalSelection: Bool {
        !selectedNormalIds.isEmpty
    }

    private var hasCrashSelection: Bool {
        !selectedCrashIds.isEmpty
    }

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                headerView
                infoBlock

                if isInteractionLocked {
                    Text("Во время видеозаписи просмотр и удаление записей временно недоступны.")
                        .font(.footnote)
                        .foregroundColor(.orange)
                        .padding(.horizontal, 16)
                        .padding(.top, 8)
                }

                if let errorText = viewModel.errorText, !errorText.isEmpty {
                    Text(errorText)
                        .font(.footnote)
                        .foregroundColor(.red)
                        .padding(.horizontal, 16)
                        .padding(.top, 8)
                }

                if viewModel.items.isEmpty {
                    emptyStateView
                } else {
                    List {
                        summarySection

                        filterBar

                        if !visibleCrashItems.isEmpty {
                            Section("ЗАПИСИ аварий") {
                                ForEach(visibleCrashItems) { item in
                                    archiveRow(item)
                                }
                            }
                        }
                        
                        if !visibleNormalItems.isEmpty {
                            Section("Архив видео") {
                                ForEach(visibleNormalItems) { item in
                                    archiveRow(item)
                                }
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                }

                footerBar
            }
            .navigationTitle("Архив видео")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Закрыть") {
                        dismiss()
                    }
                }

                ToolbarItem(placement: .topBarTrailing) {
                    Button(viewModel.allVisibleSelected ? "Снять всё" : "Выбрать всё") {
                        if isInteractionLocked {
                            showRecordingLockAlert = true
                            return
                        }

                        if viewModel.allVisibleSelected {
                            viewModel.clearSelection()
                        } else {
                            viewModel.selectAll()
                        }
                    }
                    .disabled(viewModel.items.isEmpty)
                }
            }
            .onAppear {
                viewModel.reload()
            }
            .sheet(item: $playbackItem) { item in
                VideoPlayer(player: AVPlayer(url: item.url))
                    .ignoresSafeArea()
            }
            .alert("Удалить выбранные обычные записи?", isPresented: $showDeleteNormalConfirm) {
                Button("Нет", role: .cancel) {}
                Button("Да", role: .destructive) {
                    viewModel.deleteItems(ids: selectedNormalIds)
                }
            } message: {
                Text("Записи будут удалены без возможности восстановления.")
            }
            .alert("Удалить аварийные записи?", isPresented: $showDeleteCrashConfirmStep1) {
                Button("Нет", role: .cancel) {}
                Button("Да", role: .destructive) {
                    showDeleteCrashConfirmStep2 = true
                }
            } message: {
                Text("Аварийные записи хранятся отдельно. Это первое подтверждение удаления.")
            }
            .alert("Подтвердите удаление аварийных записей", isPresented: $showDeleteCrashConfirmStep2) {
                Button("Нет", role: .cancel) {}
                Button("Удалить", role: .destructive) {
                    viewModel.deleteItems(ids: selectedCrashIds)
                }
            } message: {
                Text("Аварийные записи будут удалены без возможности восстановления.")
            }
            .alert("Архив недоступен во время записи", isPresented: $showRecordingLockAlert) {
                Button("OK", role: .cancel) {}
            } message: {
                Text("Остановите видеозапись, чтобы открыть просмотр или удаление записей.")
            }
            .alert("Файл недоступен", isPresented: $showFileMissingAlert) {
                Button("OK", role: .cancel) {}
            } message: {
                Text("Не удалось открыть запись. Возможно, файл сегмента отсутствует.")
            }
            .alert("Сохранить в медиатеку?", isPresented: $showSaveToLibraryConfirm) {
                Button("Нет", role: .cancel) {}

                Button("Да") {
                    Task {
                        isSavingToLibrary = true
                        await viewModel.saveSelectedToPhotoLibrary()
                        isSavingToLibrary = false
                        showSaveResultAlert = true
                    }
                }
            } message: {
                Text("Вы хотите сохранить \(viewModel.selectedCount) записей общим размером \(viewModel.selectedTotalSizeMBText) МБ?")
            }
            .alert("Сохранение завершено", isPresented: $showSaveResultAlert) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(viewModel.saveResultText ?? "Готово.")
            }
        }
    }

    private var headerView: some View {
        VStack(spacing: 10) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Всего записей: \(viewModel.items.count)")
                        .font(.subheadline)
                        .fontWeight(.semibold)

                    Text(String(format: "Использовано: %.2f ГБ (%d%%)", viewModel.totalSizeGB, viewModel.usagePercent))
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }

                Spacer()
            }

            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(Color(.systemGray5))

                    RoundedRectangle(cornerRadius: 6)
                        .fill(viewModel.usagePercent >= 90 ? Color.red : Color.blue)
                        .frame(width: proxy.size.width * CGFloat(min(max(Double(viewModel.usagePercent) / 100.0, 0), 1)))
                }
            }
            .frame(height: 10)
        }
        .padding(16)
        .background(Color(.systemBackground))
    }
    
    private var infoBlock: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label("Записи сохраняются фрагментами по 2 минуты", systemImage: "info.circle")
                .font(.caption)
                .foregroundColor(.secondary)

            Text("Одна запись может состоять из нескольких фрагментов в архиве")
                .font(.caption)
                .foregroundColor(.secondary)

            Text("Это сделано для снижения нагрузки на устройство")
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .padding(.horizontal, 16)
        .padding(.top, 6)
    }

    private var emptyStateView: some View {
        VStack(spacing: 12) {
            Spacer()

            Image(systemName: "video.slash")
                .font(.system(size: 42))
                .foregroundColor(.secondary)

            Text("Архив пуст")
                .font(.headline)

            Text("После записи видео здесь появятся обычные и аварийные ролики.")
                .font(.footnote)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)

            Spacer()
        }
    }

    private var summarySection: some View {
        Section("Сводка") {
            Button {
                selectedFilter = .normal
            } label: {
                HStack {
                    Text("Обычные записи")
                    Spacer()
                    Text("\(normalItems.count)")
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            Button {
                selectedFilter = .crash
            } label: {
                HStack {
                    Text("Аварийные записи")
                    Spacer()
                    Text("\(crashItems.count)")
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            Button {
                selectedFilter = .all
            } label: {
                HStack {
                    Text("Выбрано")
                    Spacer()
                    Text("\(viewModel.selectedIds.count)")
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }
    
    
    private var filterBar: some View {
        Section {
            HStack(spacing: 8) {
                Button {
                    selectedFilter = .all
                } label: {
                    Text("Все")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(selectedFilter == .all ? .blue : .gray.opacity(0.3))

                Button {
                    selectedFilter = .crash
                } label: {
                    Text("Аварийные")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(selectedFilter == .crash ? .red : .gray.opacity(0.3))

                Button {
                    selectedFilter = .normal
                } label: {
                    Text("Обычные")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(selectedFilter == .normal ? .blue : .gray.opacity(0.3))
            }
            .padding(.vertical, 4)
        }
    }
    
    private func segmentLabel(for item: DashcamArchiveItem) -> String? {
        guard item.kind == .normal else { return nil }
        guard let order = item.segmentOrder else { return nil }
        return "Сегмент \(order)"
    }

    private func archiveRow(_ item: DashcamArchiveItem) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Button {
                if isInteractionLocked {
                    showRecordingLockAlert = true
                    return
                }
                viewModel.toggleSelection(item.id)
            } label: {
                Image(systemName: viewModel.isSelected(item.id) ? "checkmark.square.fill" : "square")
                    .font(.title3)
                    .foregroundColor(item.kind == .crash ? .red : .blue)
            }
            .buttonStyle(.plain)

            Button {
                if isInteractionLocked {
                    showRecordingLockAlert = true
                    return
                }
                let url = item.playbackURL

                let exists = FileManager.default.fileExists(atPath: url.path)
                print("[ArchiveDebug] open item id=\(item.id)")
                print("[ArchiveDebug] open item title=\(item.title)")
                print("[ArchiveDebug] open item url=\(url.absoluteString)")
                print("[ArchiveDebug] open item path=\(url.path)")
                print("[ArchiveDebug] open item exists=\(exists)")

                if exists {
                    playbackItem = PlaybackItem(url: url)
                } else {
                    showFileMissingAlert = true
                }
            } label: {
                HStack(alignment: .top, spacing: 12) {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 8) {
                            Text(item.title)
                                .font(.subheadline)
                                .fontWeight(.semibold)
                                .multilineTextAlignment(.leading)

                            if let segmentLabel = item.segmentLabel {
                                    Text(segmentLabel)
                                        .font(.caption2)
                                        .fontWeight(.medium)
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 4)
                                        .background(Color.blue.opacity(0.12))
                                        .foregroundColor(.blue)
                                        .clipShape(Capsule())
                                }

                            if item.kind == .crash {
                                Text("АВАРИЯ")
                                    .font(.caption2)
                                    .fontWeight(.bold)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 3)
                                    .background(Color.red.opacity(0.15))
                                    .foregroundColor(.red)
                                    .clipShape(Capsule())
                            }
                            
                            if item.isSavedToPhotoLibrary {
                                Text("СОХРАНЕНО")
                                    .font(.caption2)
                                    .fontWeight(.bold)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 3)
                                    .background(Color.green.opacity(0.15))
                                    .foregroundColor(.green)
                                    .clipShape(Capsule())
                            }
                        }

                        Text(item.startedAt.formatted(.dateTime.year().month().day().hour().minute().second()))
                            .font(.caption)
                            .foregroundColor(.secondary)
                        
                        if let recordingNumber = item.recordingNumber, let fragmentNumber = item.fragmentNumber {
                            Text("Запись №\(recordingNumber) · Фрагмент \(fragmentNumber)")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }

                        HStack(spacing: 12) {
                            Text("Длительность: \(formatDuration(item.durationSeconds))")
                            Text(String(format: "Размер: %.2f ГБ", Double(item.sizeBytes) / 1_073_741_824.0))
                        }
                        .font(.caption)
                        .foregroundColor(.secondary)
                    }

                    Spacer()

                    Image(systemName: "play.circle.fill")
                        .font(.title2)
                        .foregroundColor(.secondary)
                }
                .contentShape(Rectangle())
                .padding(.vertical, 4)
            }
            .buttonStyle(.plain)
        }
    }

    private var footerBar: some View {
        VStack(spacing: 10) {
            Divider()

            HStack(spacing: 12) {
                Button {
                    if isInteractionLocked {
                        showRecordingLockAlert = true
                        return
                    }
                    showSaveToLibraryConfirm = true
                } label: {
                    if isSavingToLibrary {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                    } else {
                        Text("Сохранить в медиатеку")
                            .frame(maxWidth: .infinity)
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(!hasSelection || isSavingToLibrary)

                Button {
                    if isInteractionLocked {
                        showRecordingLockAlert = true
                        return
                    }
                    showDeleteNormalConfirm = true
                } label: {
                    Text("Удалить обычные")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(!hasNormalSelection || isSavingToLibrary)

                Button {
                    if isInteractionLocked {
                        showRecordingLockAlert = true
                        return
                    }
                    showDeleteCrashConfirmStep1 = true
                } label: {
                    Text("Удалить аварийные")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(!hasCrashSelection || isSavingToLibrary)
            }

            if hasSelection {
                Text("Можно выбрать записи и сохранить их в медиатеку. Для аварийных записей удаление требует двойного подтверждения.")
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(16)
        .background(Color(.systemBackground))
    }

    private func formatDuration(_ seconds: Int) -> String {
        let h = seconds / 3600
        let m = (seconds % 3600) / 60
        let s = seconds % 60
        return String(format: "%02d:%02d:%02d", h, m, s)
    }
}

private enum ArchiveFilter {
    case all
    case normal
    case crash
}
private struct PlaybackItem: Identifiable {
    let id = UUID()
    let url: URL
}
