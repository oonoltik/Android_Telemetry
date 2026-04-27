import SwiftUI
import AVKit

struct VideoArchiveView: View {
    @Environment(\.dismiss) private var dismiss
    
    @EnvironmentObject var languageManager: LanguageManager

    private func t(_ key: LocalizationKey) -> String {
        languageManager.text(key)
    }
    
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
                    Text(t(.videoArchiveLockedMessage))
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
                            Section(t(.crashRecordsSection)) {
                                ForEach(visibleCrashItems) { item in
                                    archiveRow(item)
                                }
                            }
                        }
                        
                        if !visibleNormalItems.isEmpty {
                            Section(t(.videoArchiveTitle)) {
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
            .navigationTitle(t(.videoArchiveTitle))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(t(.closeButton)) {
                        dismiss()
                    }
                }

                ToolbarItem(placement: .topBarTrailing) {
                    Button(viewModel.allVisibleSelected ? t(.deselectAll) : t(.selectAll)) {
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
            .alert(t(.deleteSelectedNormalTitle), isPresented: $showDeleteNormalConfirm) {
                Button(t(.noButton), role: .cancel) {}
                Button(t(.yesButton), role: .destructive) {
                    viewModel.deleteItems(ids: selectedNormalIds)
                }
            } message: {
                Text(t(.recordsDeleteIrreversible))
            }
            .alert(t(.deleteCrashRecordsTitle), isPresented: $showDeleteCrashConfirmStep1) {
                Button(t(.noButton), role: .cancel) {}
                Button(t(.yesButton), role: .destructive) {
                    showDeleteCrashConfirmStep2 = true
                }
            } message: {
                Text(t(.crashDeleteFirstConfirm))
            }
            .alert(t(.confirmDeleteCrashRecordsTitle), isPresented: $showDeleteCrashConfirmStep2) {
                Button(t(.noButton), role: .cancel) {}
                Button(t(.delete), role: .destructive) {
                    viewModel.deleteItems(ids: selectedCrashIds)
                }
            } message: {
                Text(t(.crashRecordsDeleteIrreversible))
            }
            .alert(t(.archiveUnavailableDuringRecordingTitle), isPresented: $showRecordingLockAlert) {
                Button(t(.ok), role: .cancel) {}
            } message: {
                Text(t(.stopVideoRecordingToOpenArchive))
            }
            .alert(t(.fileUnavailableTitle), isPresented: $showFileMissingAlert) {
                Button(t(.ok), role: .cancel) {}
            } message: {
                Text(t(.unableToOpenRecordingMissingSegment))
            }
            .alert(t(.saveToLibraryTitle), isPresented: $showSaveToLibraryConfirm) {
                Button(t(.noButton), role: .cancel) {}

                Button(t(.yesButton)) {
                    Task {
                        isSavingToLibrary = true
                        await viewModel.saveSelectedToPhotoLibrary()
                        isSavingToLibrary = false
                        showSaveResultAlert = true
                    }
                }
            } message: {
                Text(String(format: t(.saveToLibraryConfirmFormat), viewModel.selectedCount, viewModel.selectedTotalSizeMBText))
            }
            .alert(t(.saveCompletedTitle), isPresented: $showSaveResultAlert) {
                Button(t(.ok), role: .cancel) {}
            } message: {
                Text(localizedSaveResultText)
            }
        }
    }
    
    private func localizedRecordingTitle(for item: DashcamArchiveItem) -> String {
        if let recordingNumber = item.recordingNumber {
            return String(format: t(.recordingTitleFormat), recordingNumber)
        }
        return item.title
    }
    
    private var localizedSaveResultText: String {
        guard let text = viewModel.saveResultText else { return t(.readyShort) }
        let prefix = "Сохранено в медиатеку: "
        if text.hasPrefix(prefix), let count = Int(text.dropFirst(prefix.count)) {
            return String(format: t(.savedToLibraryFormat), count)
        }
        return text
    }

    private var headerView: some View {
        VStack(spacing: 10) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(String(format: t(.totalRecordsFormat), viewModel.items.count))
                        .font(.subheadline)
                        .fontWeight(.semibold)

                    Text(String(format: t(.usedStorageFormat), viewModel.totalSizeGB, viewModel.usagePercent))
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
            Label(t(.recordsSavedInTwoMinuteFragments), systemImage: "info.circle")
                .font(.caption)
                .foregroundColor(.secondary)

            Text(t(.recordingMayContainSeveralFragments))
                .font(.caption)
                .foregroundColor(.secondary)

            Text(t(.reducesDeviceLoad))
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

            Text(t(.archiveEmpty))
                .font(.headline)

            Text(t(.archiveEmptyDescription))
                .font(.footnote)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)

            Spacer()
        }
    }

    private var summarySection: some View {
        Section(t(.summary)) {
            Button {
                selectedFilter = .normal
            } label: {
                HStack {
                    Text(t(.normalRecords))
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
                    Text(t(.crashRecords))
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
                    Text(t(.selected))
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
                    Text(t(.all))
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(selectedFilter == .all ? .blue : .gray.opacity(0.3))

                Button {
                    selectedFilter = .crash
                } label: {
                    Text(t(.crashFilter))
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(selectedFilter == .crash ? .red : .gray.opacity(0.3))

                Button {
                    selectedFilter = .normal
                } label: {
                    Text(t(.normalFilter))
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(selectedFilter == .normal ? .blue : .gray.opacity(0.3))
            }
            .padding(.vertical, 4)
        }
    }
    
    private var displayFormatter: DateFormatter {
        let f = DateFormatter()
        f.locale = languageManager.locale()
        f.timeZone = .current
        f.dateFormat = "d MMM yyyy, HH:mm"
        return f
    }
    
    private func segmentLabel(for item: DashcamArchiveItem) -> String? {
        guard item.kind == .normal else { return nil }
        guard let order = item.segmentOrder else { return nil }
        return String(format: t(.segmentFormat), order)
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
                            let formattedDate = displayFormatter.string(from: item.startedAt)

                            Text(
                                item.kind == .crash
                                ? String(format: t(.crashTitleWithDateFormat), formattedDate)
                                : localizedRecordingTitle(for: item)
                            )
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
                                Text(t(.crashBadge))
                                    .font(.caption2)
                                    .fontWeight(.bold)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 3)
                                    .background(Color.red.opacity(0.15))
                                    .foregroundColor(.red)
                                    .clipShape(Capsule())
                            }
                            
                            if item.isSavedToPhotoLibrary {
                                Text(t(.savedBadge))
                                    .font(.caption2)
                                    .fontWeight(.bold)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 3)
                                    .background(Color.green.opacity(0.15))
                                    .foregroundColor(.green)
                                    .clipShape(Capsule())
                            }
                        }

                        Text(displayFormatter.string(from: item.startedAt))
                            .font(.caption)
                            .foregroundColor(.secondary)
                        
                        if let recordingNumber = item.recordingNumber, let fragmentNumber = item.fragmentNumber {
                            Text(String(format: t(.recordingFragmentFormat), recordingNumber, fragmentNumber))
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }

                        HStack(spacing: 12) {
                            Text(String(format: t(.durationLabelFormat), formatDuration(item.durationSeconds)))
                            Text(String(format: t(.sizeGBFormat), Double(item.sizeBytes) / 1_073_741_824.0))
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
                        Text(t(.saveToMediaLibrary))
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
                    Text(t(.deleteNormal))
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
                    Text(t(.deleteCrash))
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(!hasCrashSelection || isSavingToLibrary)
            }

            if hasSelection {
                Text(t(.archiveFooterHint))
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
