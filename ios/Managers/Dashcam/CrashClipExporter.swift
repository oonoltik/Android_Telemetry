import Foundation
import AVFoundation

enum CrashClipExporterError: Error {
    case noSegments
    case cannotCreateComposition
    case exportSessionUnavailable
    case exportTimedOut
    case exportFailed
}

final class CrashClipExporter {
    static func exportCrashClip(
        from segments: [VideoSegmentRecord],
        crashAt: Date,
        preSeconds: Int,
        postSeconds: Int,
        outputURL: URL
    ) throws {
        guard !segments.isEmpty else {
            throw CrashClipExporterError.noSegments
        }

        let sorted = segments.sorted { $0.startedAt < $1.startedAt }

        let availableStart = sorted.first?.startedAt ?? crashAt
        let availableEnd = sorted.compactMap(\.endedAt).max() ?? crashAt

        let clipStartDate = max(availableStart, crashAt.addingTimeInterval(TimeInterval(-preSeconds)))
        let clipEndDate = min(availableEnd, crashAt.addingTimeInterval(TimeInterval(postSeconds)))

        guard clipEndDate > clipStartDate else {
            throw CrashClipExporterError.noSegments
        }

        let composition = AVMutableComposition()

        guard let videoTrack = composition.addMutableTrack(
            withMediaType: .video,
            preferredTrackID: kCMPersistentTrackID_Invalid
        ) else {
            throw CrashClipExporterError.cannotCreateComposition
        }

        let audioTrack = composition.addMutableTrack(
            withMediaType: .audio,
            preferredTrackID: kCMPersistentTrackID_Invalid
        )

        var insertAt = CMTime.zero

        for segment in sorted {
            let asset = AVURLAsset(url: segment.fileURL)
            let assetDuration = asset.duration

            let segmentStart = segment.startedAt
            let segmentDurationSec = max(
                0,
                segment.endedAt?.timeIntervalSince(segment.startedAt) ?? CMTimeGetSeconds(assetDuration)
            )
            let segmentEnd = segmentStart.addingTimeInterval(segmentDurationSec)

            let effectiveStart = max(segmentStart, clipStartDate)
            let effectiveEnd = min(segmentEnd, clipEndDate)

            guard effectiveEnd > effectiveStart else { continue }

            let trimStartSec = max(0, effectiveStart.timeIntervalSince(segmentStart))
            let trimDurationSec = effectiveEnd.timeIntervalSince(effectiveStart)

            let timeRange = CMTimeRange(
                start: CMTime(seconds: trimStartSec, preferredTimescale: 600),
                duration: CMTime(seconds: trimDurationSec, preferredTimescale: 600)
            )

            if let assetVideoTrack = asset.tracks(withMediaType: .video).first{
                try videoTrack.insertTimeRange(timeRange, of: assetVideoTrack, at: insertAt)
                videoTrack.preferredTransform = assetVideoTrack.preferredTransform
            }

            if let assetAudioTrack = asset.tracks(withMediaType: .audio).first {
                try audioTrack?.insertTimeRange(timeRange, of: assetAudioTrack, at: insertAt)
            }

            insertAt = insertAt + timeRange.duration
        }

        if FileManager.default.fileExists(atPath: outputURL.path) {
            try? FileManager.default.removeItem(at: outputURL)
        }

        guard let exportSession = AVAssetExportSession(
            asset: composition,
            presetName: AVAssetExportPresetHighestQuality
        ) else {
            throw CrashClipExporterError.exportSessionUnavailable
        }

        exportSession.outputURL = outputURL
        exportSession.outputFileType = .mov
        exportSession.shouldOptimizeForNetworkUse = true

        let semaphore = DispatchSemaphore(value: 0)
        var exportError: Error?

        exportSession.exportAsynchronously {
            switch exportSession.status {
            case .completed:
                break
            case .failed, .cancelled:
                exportError = exportSession.error ?? CrashClipExporterError.exportFailed
            default:
                exportError = CrashClipExporterError.exportFailed
            }
            semaphore.signal()
        }

        let waitResult = semaphore.wait(timeout: .now() + 60)
        if waitResult == .timedOut {
            throw CrashClipExporterError.exportTimedOut
        }

        if let exportError {
            throw exportError
        }
    }
}
