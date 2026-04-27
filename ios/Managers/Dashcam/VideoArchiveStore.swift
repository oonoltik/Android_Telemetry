import Foundation
import Combine
import AVFoundation

protocol VideoArchiveStore {
    func createVideoSession(startedAt: Date, linkedTripSessionId: String?) throws -> String
    func finishVideoSession(id: String, endedAt: Date, finalLinkedTripSessionId: String?, stopReason: DashcamStopTrigger) throws
    func createSegment(sessionId: String, fileURL: URL, startedAt: Date) throws -> VideoSegmentRecord
    func finalizeSegment(id: String, endedAt: Date, sizeBytes: Int64) throws
    func protectSegmentsForCrash(from start: Date, to end: Date) throws -> [VideoSegmentRecord]
    func addCompletedSegment(
        sessionId: String,
        fileURL: URL,
        startedAt: Date,
        endedAt: Date,
        sizeBytes: Int64,
        timelineStartSampleT: String?,
        timelineEndSampleT: String?,
        startLat: Double?,
        startLon: Double?,
        endLat: Double?,
        endLon: Double?,
        startSpeedKmh: Double?,
        endSpeedKmh: Double?,
        samplesCount: Int,
        eventsCount: Int,
        eventTypesNearby: [String]
    ) throws -> VideoSegmentRecord
    func createCrashClip(
        from segments: [VideoSegmentRecord],
        crashClipId: String,
        videoSessionId: String,
        crashAt: Date,
        preSeconds: Int,
        postSeconds: Int,
        linkedTripSessionId: String?,
        latitude: Double?,
        longitude: Double?,
        maxG: Double?
    ) throws -> CrashClipRecord
    func listArchiveItems() throws -> [DashcamArchiveItem]
    func deleteArchiveItems(ids: [String]) throws

    func totalUsageBytes() throws -> Int64
    func totalArchiveUsageBytes() throws -> Int64

    func markAsSavedToPhotoLibrary(id: String) throws

    func oldestDeletableNormalSegmentIds(limitBytesToFree: Int64) throws -> [String]
    func urlForSession(sessionId: String) throws -> URL
    func archiveStats() throws -> (normalCount: Int, crashCount: Int, normalSizeBytes: Int64, crashSizeBytes: Int64)
    func segmentsCount(for sessionId: String) throws -> Int

    func updateSegmentTimelineMetadata(
        id: String,
        timelineStartSampleT: String?,
        timelineEndSampleT: String?,
        startLat: Double?,
        startLon: Double?,
        endLat: Double?,
        endLon: Double?,
        startSpeedKmh: Double?,
        endSpeedKmh: Double?,
        samplesCount: Int,
        eventsCount: Int,
        eventTypesNearby: [String]
    ) throws
    
    func currentActiveVideoSessionId() throws -> String?
    func discardSegment(id: String) throws
}

final class JSONVideoArchiveStore: VideoArchiveStore {
    
    func addCompletedSegment(
        sessionId: String,
        fileURL: URL,
        startedAt: Date,
        endedAt: Date,
        sizeBytes: Int64,
        timelineStartSampleT: String?,
        timelineEndSampleT: String?,
        startLat: Double?,
        startLon: Double?,
        endLat: Double?,
        endLon: Double?,
        startSpeedKmh: Double?,
        endSpeedKmh: Double?,
        samplesCount: Int,
        eventsCount: Int,
        eventTypesNearby: [String]
    ) throws -> VideoSegmentRecord {
        var result: VideoSegmentRecord?
        var capturedError: Error?

        let workItem = DispatchWorkItem {
            do {
                var idx = try self.readIndex()
                let order = idx.segments.filter { $0.sessionId == sessionId }.count + 1

                var item = VideoSegmentRecord(
                    id: "seg_" + UUID().uuidString,
                    sessionId: sessionId,
                    startedAt: startedAt,
                    endedAt: endedAt,
                    sizeBytes: sizeBytes,
                    isProtected: false,
                    fileURL: fileURL,
                    order: order
                )

                item.timeline_start_sample_t = timelineStartSampleT
                item.timeline_end_sample_t = timelineEndSampleT
                item.recording_start_lat = startLat
                item.recording_start_lon = startLon
                item.recording_end_lat = endLat
                item.recording_end_lon = endLon
                item.recording_start_speed_kmh = startSpeedKmh
                item.recording_end_speed_kmh = endSpeedKmh
                item.timeline_samples_count = samplesCount
                item.timeline_events_count = eventsCount
                item.event_types_nearby = eventTypesNearby
                item.isSavedToPhotoLibrary = false

                idx.segments.append(item)

                if let i = idx.sessions.firstIndex(where: { $0.id == sessionId }) {
                    idx.sessions[i].segmentIds.append(item.id)
                }

                try self.writeIndex(idx)
                result = item
            } catch {
                capturedError = error
            }
        }

        queue.sync(execute: workItem)

        if let capturedError {
            throw capturedError
        }

        guard let result else {
            throw DashcamError.unknown("Не удалось добавить завершённый сегмент в архив")
        }

        return result
    }
    
    
    
    private func resolvedURL(for segment: VideoSegmentRecord) -> URL {
        if fileManager.fileExists(atPath: segment.fileURL.path) {
            return segment.fileURL
        }

        let sessionFolder = sessionsURL.appendingPathComponent(segment.sessionId, isDirectory: true)
        return sessionFolder.appendingPathComponent(segment.fileURL.lastPathComponent)
    }

    private func segmentFileExists(_ segment: VideoSegmentRecord) -> Bool {
        let resolved = resolvedURL(for: segment)
        return fileManager.fileExists(atPath: resolved.path)
    }
    
    func discardSegment(id: String) throws {
        try queue.sync {
            var idx = try readIndex()

            guard let segmentIndex = idx.segments.firstIndex(where: { $0.id == id }) else {
                return
            }

            let segment = idx.segments[segmentIndex]

            if segmentFileExists(segment) {
                let url = resolvedURL(for: segment)
                try? fileManager.removeItem(at: url)
            }

            idx.segments.remove(at: segmentIndex)

            if let sessionIndex = idx.sessions.firstIndex(where: { $0.id == segment.sessionId }) {
                idx.sessions[sessionIndex].segmentIds.removeAll { $0 == id }
            }

            try writeIndex(idx)
        }
    }
    
    private struct ArchiveIndex: Codable {
        var sessions: [DashcamVideoSessionRecord] = []
        var segments: [VideoSegmentRecord] = []
        var crashClips: [CrashClipRecord] = []
    }
    
    private func debugLogFileState(prefix: String, url: URL) {
        let exists = fileManager.fileExists(atPath: url.path)

        let sizeBytes: Int64 = {
            guard exists,
                  let attrs = try? fileManager.attributesOfItem(atPath: url.path),
                  let value = attrs[.size] as? NSNumber else {
                return -1
            }
            return value.int64Value
        }()

        print("[ArchiveDebug] \(prefix)")
        print("[ArchiveDebug] url = \(url.absoluteString)")
        print("[ArchiveDebug] path = \(url.path)")
        print("[ArchiveDebug] exists = \(exists)")
        print("[ArchiveDebug] sizeBytes = \(sizeBytes)")
    }
    
    func currentActiveVideoSessionId() throws -> String? {
        try queue.sync {
            let idx = try readIndex()
            return idx.sessions
                .filter { $0.endedAt == nil }
                .sorted { $0.startedAt > $1.startedAt }
                .first?
                .id
        }
    }
    
    func markAsSavedToPhotoLibrary(id: String) throws {
        try queue.sync {
            var idx = try readIndex()

            if let i = idx.segments.firstIndex(where: { $0.id == id }) {
                idx.segments[i].isSavedToPhotoLibrary = true
            }

            if let i = idx.crashClips.firstIndex(where: { $0.id == id }) {
                idx.crashClips[i].isSavedToPhotoLibrary = true
            }

            try writeIndex(idx)
        }
    }
    private func actualDurationSeconds(for url: URL) -> Int {
        let asset = AVURLAsset(url: url)
        let seconds = CMTimeGetSeconds(asset.duration)

        guard seconds.isFinite, seconds > 0 else {
            return 1
        }

        return max(1, Int(seconds.rounded()))
    }
    private let fileManager = FileManager.default
    private let queue = DispatchQueue(label: "dashcam.archive.store")
    private let baseURL: URL
    private let indexURL: URL

    private lazy var sessionsURL = baseURL.appendingPathComponent("sessions", isDirectory: true)
    private lazy var crashURL = baseURL.appendingPathComponent("crash_archive", isDirectory: true)

    init(baseURL: URL? = nil) throws {
        let root = try baseURL ?? Self.makeBaseURL()
        self.baseURL = root
        self.indexURL = root.appendingPathComponent("archive_index.json")

        try fileManager.createDirectory(at: root, withIntermediateDirectories: true)
        try fileManager.createDirectory(at: sessionsURL, withIntermediateDirectories: true)
        try fileManager.createDirectory(at: crashURL, withIntermediateDirectories: true)

        if !fileManager.fileExists(atPath: indexURL.path) {
            let data = try JSONEncoder.make().encode(ArchiveIndex())
            try data.write(to: indexURL, options: .atomic)
        }
    }
    
    func updateSegmentTimelineMetadata(
        id: String,
        timelineStartSampleT: String?,
        timelineEndSampleT: String?,
        startLat: Double?,
        startLon: Double?,
        endLat: Double?,
        endLon: Double?,
        startSpeedKmh: Double?,
        endSpeedKmh: Double?,
        samplesCount: Int,
        eventsCount: Int,
        eventTypesNearby: [String]
    ) throws {
        try queue.sync {
            var idx = try readIndex()

            guard let i = idx.segments.firstIndex(where: { $0.id == id }) else {
                return
            }

            idx.segments[i].timeline_start_sample_t = timelineStartSampleT
            idx.segments[i].timeline_end_sample_t = timelineEndSampleT

            idx.segments[i].recording_start_lat = startLat
            idx.segments[i].recording_start_lon = startLon
            idx.segments[i].recording_end_lat = endLat
            idx.segments[i].recording_end_lon = endLon

            idx.segments[i].recording_start_speed_kmh = startSpeedKmh
            idx.segments[i].recording_end_speed_kmh = endSpeedKmh

            idx.segments[i].timeline_samples_count = samplesCount
            idx.segments[i].timeline_events_count = eventsCount

            idx.segments[i].event_types_nearby = eventTypesNearby

            try writeIndex(idx)
        }
    }
    
    func createVideoSession(startedAt: Date, linkedTripSessionId: String?) throws -> String {
        try queue.sync {
            var idx = try readIndex()
            let id = "vid_" + UUID().uuidString

            idx.sessions.append(
                DashcamVideoSessionRecord(
                    id: id,
                    startedAt: startedAt,
                    endedAt: nil,
                    linkedTripSessionIdInitial: linkedTripSessionId,
                    finalLinkedTripSessionId: nil,
                    stopReason: nil,
                    audioEnabled: true,
                    cameraMode: "rear",
                    segmentIds: []
                )
            )

            try writeIndex(idx)

            let dir = sessionsURL.appendingPathComponent(id, isDirectory: true)
            try fileManager.createDirectory(at: dir, withIntermediateDirectories: true)
            return id
        }
    }

    func finishVideoSession(id: String, endedAt: Date, finalLinkedTripSessionId: String?, stopReason: DashcamStopTrigger) throws {
        try queue.sync {
            var idx = try readIndex()
            if let i = idx.sessions.firstIndex(where: { $0.id == id }) {
                idx.sessions[i].endedAt = endedAt
                idx.sessions[i].finalLinkedTripSessionId = finalLinkedTripSessionId
                idx.sessions[i].stopReason = stopReason
            }
            try writeIndex(idx)
        }
    }

    func createSegment(sessionId: String, fileURL: URL, startedAt: Date) throws -> VideoSegmentRecord {
        try queue.sync {
            var idx = try readIndex()
            let order = idx.segments.filter { $0.sessionId == sessionId }.count + 1

            let item = VideoSegmentRecord(
                id: "seg_" + UUID().uuidString,
                sessionId: sessionId,
                startedAt: startedAt,
                endedAt: nil,
                sizeBytes: 0,
                isProtected: false,
                fileURL: fileURL,
                order: order
            )

            idx.segments.append(item)

            if let i = idx.sessions.firstIndex(where: { $0.id == sessionId }) {
                idx.sessions[i].segmentIds.append(item.id)
            }

            print("[ArchiveDebug] createSegment id=\(item.id) sessionId=\(item.sessionId)")
            debugLogFileState(prefix: "createSegment before writeIndex", url: item.fileURL)

            try writeIndex(idx)
            return item
        }
    }

    func finalizeSegment(id: String, endedAt: Date, sizeBytes: Int64) throws {
        try queue.sync {
            var idx = try readIndex()

            if let i = idx.segments.firstIndex(where: { $0.id == id }) {
                idx.segments[i].endedAt = endedAt
                idx.segments[i].sizeBytes = sizeBytes

                print("[ArchiveDebug] finalizeSegment id=\(idx.segments[i].id)")
                debugLogFileState(prefix: "finalizeSegment before writeIndex", url: idx.segments[i].fileURL)
            } else {
                print("[ArchiveDebug] finalizeSegment missing id=\(id)")
            }

            try writeIndex(idx)
        }
    }
    func protectSegmentsForCrash(from start: Date, to end: Date) throws -> [VideoSegmentRecord] {
        try queue.sync {
            let idx = try readIndex()

            let result = idx.segments.filter { segment in
                let s = segment.startedAt
                let e = segment.endedAt ?? Date()
                return s <= end && e >= start
            }
            
            print("[CRASH_SEGMENTS] protectSegmentsForCrash at=\(Date()) windowStart=\(start.formatted(.iso8601)) windowEnd=\(end.formatted(.iso8601)) found=\(result.count)")
            for segment in result.sorted(by: { $0.startedAt < $1.startedAt }) {
                print("[CRASH_SEGMENTS] candidate id=\(segment.id) sessionId=\(segment.sessionId) startedAt=\(segment.startedAt.formatted(.iso8601)) endedAt=\(segment.endedAt?.formatted(.iso8601) ?? "nil") exists=\(segmentFileExists(segment))")
            }

            return result.sorted { $0.startedAt < $1.startedAt }
        }
    }
    
    private func stabilizedSegmentsForCrashExport(
        _ segments: [VideoSegmentRecord]
    ) -> [VideoSegmentRecord] {
        
        print("[CRASH_STABILIZE] enter at=\(Date()) inputCount=\(segments.count)")
        for segment in segments.sorted(by: { $0.startedAt < $1.startedAt }) {
            print("[CRASH_STABILIZE] input id=\(segment.id) startedAt=\(segment.startedAt.formatted(.iso8601)) endedAt=\(segment.endedAt?.formatted(.iso8601) ?? "nil") exists=\(segmentFileExists(segment))")
        }
        let existing = segments
            .filter { segment in
                segmentFileExists(segment)
            }
            .sorted { $0.startedAt < $1.startedAt }

        guard !existing.isEmpty else {
            return []
        }

        let finished = existing.filter { $0.endedAt != nil }
        print("[CRASH_STABILIZE] existingCount=\(existing.count) finishedCount=\(finished.count)")

        if !finished.isEmpty {
            return finished
        }

        if existing.count >= 2 {
            print("[CRASH_STABILIZE] using dropLast fallback because no finished segments")
            return Array(existing.dropLast())
        }

        return existing
    }

    private func crashExportCandidates(
        from segments: [VideoSegmentRecord],
        crashAt: Date,
        preSeconds: Int,
        postSeconds: Int
    ) -> [VideoSegmentRecord] {
        let windowStart = crashAt.addingTimeInterval(TimeInterval(-preSeconds))
        let windowEnd = crashAt.addingTimeInterval(TimeInterval(postSeconds))

        let overlapped = segments
            .filter { segment in
                let start = segment.startedAt
                let end = segment.endedAt ?? Date()
                return start <= windowEnd && end >= windowStart
            }
            .sorted { $0.startedAt < $1.startedAt }

        let stabilized = stabilizedSegmentsForCrashExport(overlapped)

        print("[CrashExport] total=\(segments.count) overlapped=\(overlapped.count) stabilized=\(stabilized.count)")
        
        for segment in stabilized {
            let endedText = segment.endedAt?.formatted(.iso8601) ?? "nil"
            let exists = segmentFileExists(segment)
            let url = resolvedURL(for: segment)
            print("[CrashExport] segment id=\(segment.id) startedAt=\(segment.startedAt.formatted(.iso8601)) endedAt=\(endedText) exists=\(exists) url=\(url.path) size=\(segment.sizeBytes)")
        }

        if !stabilized.isEmpty {
            return stabilized
        }

        let existingOverlapped = overlapped.filter { segment in
            segmentFileExists(segment)
        }

        print("[CrashExport] fallback existingOverlapped=\(existingOverlapped.count)")

        if existingOverlapped.count >= 2 {
            let fallback = Array(existingOverlapped.dropLast())
            print("[CrashExport] fallback dropLast count=\(fallback.count)")
            return fallback
        }

        return existingOverlapped
    }
    func createCrashClip(
        from segments: [VideoSegmentRecord],
        crashClipId: String,
        videoSessionId: String,
        crashAt: Date,
        preSeconds: Int,
        postSeconds: Int,
        linkedTripSessionId: String?,
        latitude: Double?,
        longitude: Double?,
        maxG: Double?
    ) throws -> CrashClipRecord {
        try queue.sync {
            var idx = try readIndex()
            let clipId = crashClipId

            let crashFolder = crashURL.appendingPathComponent(clipId, isDirectory: true)
            try? fileManager.removeItem(at: crashFolder)
            try fileManager.createDirectory(at: crashFolder, withIntermediateDirectories: true)

            let outputURL = crashFolder.appendingPathComponent("crash.mov")

            let exportSegments = crashExportCandidates(
                from: segments,
                crashAt: crashAt,
                preSeconds: preSeconds,
                postSeconds: postSeconds
            )

            guard !exportSegments.isEmpty else {
                throw NSError(
                    domain: "DashcamCrashClip",
                    code: 1001,
                    userInfo: [NSLocalizedDescriptionKey: "No stabilized segments for crash export"]
                )
            }

            try CrashClipExporter.exportCrashClip(
                from: exportSegments,
                crashAt: crashAt,
                preSeconds: preSeconds,
                postSeconds: postSeconds,
                outputURL: outputURL
            )

            idx.crashClips.removeAll { $0.id == clipId }

            let clip = CrashClipRecord(
                id: clipId,
                crashAt: crashAt,
                preSeconds: preSeconds,
                postSeconds: postSeconds,
                segmentIds: exportSegments.map(\.id),
                linkedTripSessionId: linkedTripSessionId,
                latitude: latitude,
                longitude: longitude,
                maxG: maxG,
                videoSessionId: videoSessionId,
                fileURL: outputURL,
                isSavedToPhotoLibrary: false
            )

            idx.crashClips.append(clip)
            try writeIndex(idx)
            return clip
        }
    }
            
    func listArchiveItems() throws -> [DashcamArchiveItem] {
        try queue.sync {
            let idx = try readIndex()

            var items: [DashcamArchiveItem] = []

            let sessionsSorted = idx.sessions.sorted { $0.startedAt < $1.startedAt }
            let sessionNumberMap = Dictionary(
                uniqueKeysWithValues: sessionsSorted.enumerated().map { index, session in
                    (session.id, index + 1)
                }
            )

            let normalSegments = idx.segments
                .sorted {
                    if $0.sessionId == $1.sessionId {
                        return $0.order < $1.order
                    }
                    return $0.startedAt > $1.startedAt
                }

            for segment in normalSegments {
                
                guard segmentFileExists(segment) else {
                    continue
                }

                let playbackURL = resolvedURL(for: segment)
                
                print("[ArchiveDebug] listArchiveItems normal id=\(segment.id)")
                debugLogFileState(prefix: "listArchiveItems normal", url: segment.fileURL)

                
                let ended = segment.endedAt ?? segment.startedAt
                let recordingNumber = sessionNumberMap[segment.sessionId]

                items.append(
                    DashcamArchiveItem(
                        id: segment.id,
                        startedAt: segment.startedAt,
                        durationSeconds: max(0, Int(ended.timeIntervalSince(segment.startedAt))),
                        sizeBytes: segment.sizeBytes,
                        kind: .normal,
                        isProtected: segment.isProtected,
                        title: "",
                        segmentLabel: nil,
                        playbackURL: playbackURL,
                        sessionId: segment.sessionId,
                        segmentOrder: segment.order,
                        recordingNumber: recordingNumber,
                        fragmentNumber: segment.order,
                        deletionGroupId: nil,
                        isSavedToPhotoLibrary: segment.isSavedToPhotoLibrary
                    )
                )
            }
            
        
            for clip in idx.crashClips {
                let crashFolder = crashURL.appendingPathComponent(clip.id, isDirectory: true)
                let playbackURL = crashFolder.appendingPathComponent("crash.mov")
                
                print("[ArchiveDebug] listArchiveItems crash id=\(clip.id)")
                debugLogFileState(prefix: "listArchiveItems crash", url: playbackURL)

                guard fileManager.fileExists(atPath: playbackURL.path) else { continue }

                let fileSizeBytes: Int64 = {
                    guard
                        let attrs = try? fileManager.attributesOfItem(atPath: playbackURL.path),
                        let n = attrs[.size] as? NSNumber
                    else {
                        return 0
                    }
                    return n.int64Value
                }()

                let durationSeconds = actualDurationSeconds(for: playbackURL)

                items.append(
                    DashcamArchiveItem(
                        id: clip.id,
                        startedAt: clip.crashAt,
                        durationSeconds: durationSeconds,
                        sizeBytes: fileSizeBytes,
                        kind: .crash,
                        isProtected: true,
                        title: "",
                        
                        segmentLabel: nil,
                        playbackURL: playbackURL,
                        recordingNumber: nil,
                        fragmentNumber: nil,
                        deletionGroupId: clip.id,
                        isSavedToPhotoLibrary: clip.isSavedToPhotoLibrary
                    )
                )
            
            }

            let crashItems = items
                .filter { $0.kind == .crash }
                .sorted { $0.startedAt > $1.startedAt }
            
            let normalItemsOnly = items
                .filter { $0.kind == .normal }
                .sorted {
                    if $0.recordingNumber == $1.recordingNumber {
                        return ($0.fragmentNumber ?? 0) < ($1.fragmentNumber ?? 0)
                    }
                    return ($0.recordingNumber ?? 0) > ($1.recordingNumber ?? 0)
                }

            return crashItems + normalItemsOnly
        }
    }

    func deleteArchiveItems(ids: [String]) throws {
        try queue.sync {
            var idx = try readIndex()
            let idSet = Set(ids)

            let crashClipIds = Set(idx.crashClips.filter { idSet.contains($0.id) }.map(\.id))

            let normalSegmentsToDelete = idx.segments.filter {
                idSet.contains($0.id)
            }

            for item in normalSegmentsToDelete {
                if segmentFileExists(item) {
                    let url = resolvedURL(for: item)
                    try? fileManager.removeItem(at: url)
                }
            }

            for clipId in crashClipIds {
                let crashFolder = crashURL.appendingPathComponent(clipId, isDirectory: true)
                try? fileManager.removeItem(at: crashFolder)
            }

            idx.segments.removeAll {
                idSet.contains($0.id)
            }

            idx.crashClips.removeAll {
                idSet.contains($0.id)
            }

            for sessionIndex in idx.sessions.indices {
                let validIds = Set(idx.segments.filter { $0.sessionId == idx.sessions[sessionIndex].id }.map(\.id))
                idx.sessions[sessionIndex].segmentIds = idx.sessions[sessionIndex].segmentIds.filter { validIds.contains($0) }
            }

            try writeIndex(idx)
        }
    }

    func totalUsageBytes() throws -> Int64 {
        try queue.sync {
            try readIndex().segments.reduce(Int64(0)) { $0 + $1.sizeBytes }
        }
    }
    
    
    

    func totalArchiveUsageBytes() throws -> Int64 {
        try queue.sync {
            let idx = try readIndex()

            let segmentsBytes = idx.segments.reduce(Int64(0)) { $0 + $1.sizeBytes }

            let crashClipBytes = idx.crashClips.reduce(Int64(0)) { partial, clip in
                let crashFileURL = crashURL
                    .appendingPathComponent(clip.id, isDirectory: true)
                    .appendingPathComponent("crash.mov")

                let attrs = try? fileManager.attributesOfItem(atPath: crashFileURL.path)
                let size = (attrs?[.size] as? NSNumber)?.int64Value ?? 0
                return partial + size
            }

            return segmentsBytes + crashClipBytes
        }
    }
    func oldestDeletableNormalSegmentIds(limitBytesToFree: Int64) throws -> [String] {
        try queue.sync {
            let candidates = try readIndex()
                .segments
                .filter { !$0.isProtected && $0.endedAt != nil }
                .sorted { $0.startedAt < $1.startedAt }

            var ids: [String] = []
            var freed: Int64 = 0

            for item in candidates {
                ids.append(item.id)
                freed += item.sizeBytes
                if freed >= limitBytesToFree {
                    break
                }
            }

            return ids
        }
    }

    func urlForSession(sessionId: String) throws -> URL {
        let dir = sessionsURL.appendingPathComponent(sessionId, isDirectory: true)
        try fileManager.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    func archiveStats() throws -> (normalCount: Int, crashCount: Int, normalSizeBytes: Int64, crashSizeBytes: Int64) {
        try queue.sync {
            let idx = try readIndex()
            let crashSegmentIds = Set(idx.crashClips.flatMap(\.segmentIds))

            let normalSegments = idx.segments.filter { !crashSegmentIds.contains($0.id) }
            let crashSegments = idx.segments.filter { crashSegmentIds.contains($0.id) }

            return (
                normalSegments.count,
                idx.crashClips.count,
                normalSegments.reduce(0) { $0 + $1.sizeBytes },
                crashSegments.reduce(0) { $0 + $1.sizeBytes }
            )
        }
    }

    func segmentsCount(for sessionId: String) throws -> Int {
        try queue.sync {
            try readIndex().segments.filter { $0.sessionId == sessionId }.count
        }
    }

    private func readIndex() throws -> ArchiveIndex {
        try JSONDecoder.make().decode(ArchiveIndex.self, from: Data(contentsOf: indexURL))
    }

    private func writeIndex(_ idx: ArchiveIndex) throws {
        try JSONEncoder.make().encode(idx).write(to: indexURL, options: .atomic)
    }

    private static func makeBaseURL() throws -> URL {
        let appSupport = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
        let root = appSupport.appendingPathComponent("Dashcam", isDirectory: true)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        return root
    }
    
    
}

private extension JSONEncoder {
    static func make() -> JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return encoder
    }
}

private extension JSONDecoder {
    static func make() -> JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }
}
