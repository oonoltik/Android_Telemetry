import Foundation
import Combine

protocol VideoArchiveStore {
    func createVideoSession(startedAt: Date, linkedTripSessionId: String?) throws -> String
    func finishVideoSession(id: String, endedAt: Date, finalLinkedTripSessionId: String?, stopReason: DashcamStopTrigger) throws
    func createSegment(sessionId: String, fileURL: URL, startedAt: Date) throws -> VideoSegmentRecord
    func finalizeSegment(id: String, endedAt: Date, sizeBytes: Int64) throws
    func protectSegmentsForCrash(from start: Date, to end: Date) throws -> [VideoSegmentRecord]
    func createCrashClip(from segments: [VideoSegmentRecord], crashAt: Date, preSeconds: Int, postSeconds: Int, linkedTripSessionId: String?, latitude: Double?, longitude: Double?, maxG: Double?) throws -> CrashClipRecord
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
}

final class JSONVideoArchiveStore: VideoArchiveStore {
    private struct ArchiveIndex: Codable {
        var sessions: [DashcamVideoSessionRecord] = []
        var segments: [VideoSegmentRecord] = []
        var crashClips: [CrashClipRecord] = []
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
                    audioEnabled: false,
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

            return result.sorted { $0.startedAt < $1.startedAt }
        }
    }

    func createCrashClip(from segments: [VideoSegmentRecord], crashAt: Date, preSeconds: Int, postSeconds: Int, linkedTripSessionId: String?, latitude: Double?, longitude: Double?, maxG: Double?) throws -> CrashClipRecord {
        try queue.sync {
            var idx = try readIndex()

            let clip = CrashClipRecord(
                id: "crash_" + UUID().uuidString,
                crashAt: crashAt,
                preSeconds: preSeconds,
                postSeconds: postSeconds,
                segmentIds: segments.map(\.id),
                linkedTripSessionId: linkedTripSessionId,
                latitude: latitude,
                longitude: longitude,
                maxG: maxG,
                isSavedToPhotoLibrary: false
            )

            let crashFolder = crashURL.appendingPathComponent(clip.id, isDirectory: true)
            try? fileManager.removeItem(at: crashFolder)
            try fileManager.createDirectory(at: crashFolder, withIntermediateDirectories: true)

            let outputURL = crashFolder.appendingPathComponent("crash.mov")

            try CrashClipExporter.exportCrashClip(
                from: segments,
                crashAt: crashAt,
                preSeconds: preSeconds,
                postSeconds: postSeconds,
                outputURL: outputURL
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
                        title: "Запись №\(recordingNumber ?? 0)",
                        segmentLabel: "Фрагмент \(segment.order)",
                        playbackURL: segment.fileURL,
                        sessionId: segment.sessionId,
                        segmentOrder: segment.order,
                        recordingNumber: recordingNumber,
                        fragmentNumber: segment.order,
                        deletionGroupId: nil,
                        isSavedToPhotoLibrary: segment.isSavedToPhotoLibrary
                    )
                )
            }
            
            let crashClipsSorted = idx.crashClips.sorted { $0.crashAt < $1.crashAt }
            let crashNumberMap = Dictionary(
                uniqueKeysWithValues: crashClipsSorted.enumerated().map { index, clip in
                    (clip.id, index + 1)
                }
            )

            for clip in idx.crashClips {
                let crashFolder = crashURL.appendingPathComponent(clip.id, isDirectory: true)
                let playbackURL = crashFolder.appendingPathComponent("crash.mov")

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

                let durationSeconds = max(1, clip.preSeconds + clip.postSeconds)

                items.append(
                    DashcamArchiveItem(
                        id: clip.id,
                        startedAt: clip.crashAt,
                        durationSeconds: durationSeconds,
                        sizeBytes: fileSizeBytes,
                        kind: .crash,
                        isProtected: true,
                        title: "АВАРИЯ \(clip.crashAt.formatted(.dateTime.year().month().day().hour().minute().second()))",
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
                .sorted {
                    if $0.recordingNumber == $1.recordingNumber {
                        return ($0.fragmentNumber ?? 0) < ($1.fragmentNumber ?? 0)
                    }
                    return ($0.recordingNumber ?? 0) > ($1.recordingNumber ?? 0)
                }

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
                try? fileManager.removeItem(at: item.fileURL)
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
