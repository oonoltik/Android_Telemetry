import Foundation

enum DashcamRecordingState: Equatable {
    case idle
    case preparing
    case recording
    case stopping
    case failed(DashcamError)
}

enum DashcamPreviewState: Equatable {
    case hidden
    case visible
}

enum DashcamTripOwnership: String, Codable {
    case none
    case manual
    case videoImplicit
}

enum DashcamStartTrigger: String, Codable {
    case userButton
    case resumeAfterInterruption
}

enum DashcamStopTrigger: String, Codable {
    case userButton
    case fatalError
    case appLifecycle
    case diskLimit
    case captureInterrupted
    case appBackground = "app_background"
}

enum ArchiveKind: String, Codable {
    case normal
    case crash
}

enum DashcamError: Error, Equatable, LocalizedError {
    case cameraUnavailable
    case microphoneUnavailable
    case cameraPermissionDenied
    case microphonePermissionDenied
    case photoLibraryDenied
    case cannotCreateSession
    case cannotCreateOutput
    case cannotStartRecording
    case quotaExceeded
    case storageFailure(String)
    case interrupted
    case unknown(String)

    var errorDescription: String? {
        switch self {
        case .cameraUnavailable:
            return "Задняя камера недоступна."
        case .microphoneUnavailable:
            return "Микрофон недоступен."
        case .cameraPermissionDenied:
            return "Доступ к камере не выдан."
        case .microphonePermissionDenied:
            return "Доступ к микрофону не выдан."
        case .photoLibraryDenied:
            return "Доступ к медиатеке не выдан."
        case .cannotCreateSession:
            return "Не удалось создать capture session."
        case .cannotCreateOutput:
            return "Не удалось создать video output."
        case .cannotStartRecording:
            return "Не удалось начать запись видео."
        case .quotaExceeded:
            return "Превышен лимит локального архива."
        case .storageFailure(let message):
            return message
        case .interrupted:
            return "Запись была прервана системой."
        case .unknown(let message):
            return message
        }
    }
}

struct DashcamContextSnapshot {
    let videoSessionId: String?
    let tripOwnership: DashcamTripOwnership
    let isRecording: Bool
    let previewState: DashcamPreviewState
}

struct VideoSegmentRecord: Codable, Identifiable {
    let id: String
    let sessionId: String
    let startedAt: Date
    var endedAt: Date?
    var sizeBytes: Int64
    var isProtected: Bool
    let fileURL: URL
    let order: Int
}

struct CrashClipRecord: Codable, Identifiable {
    let id: String
    let crashAt: Date
    let preSeconds: Int
    let postSeconds: Int
    let segmentIds: [String]
    let linkedTripSessionId: String?
    let latitude: Double?
    let longitude: Double?
    let maxG: Double?
}


enum DashcamArchiveItemKind: String, Codable {
    case normal
    case crash
}

struct DashcamArchiveItem: Identifiable, Codable {
    let id: String
    let startedAt: Date
    let durationSeconds: Int
    let sizeBytes: Int64
    let kind: DashcamArchiveItemKind
    let isProtected: Bool
    let title: String
    let segmentLabel: String?
    let playbackURL: URL

    let sessionId: String?
    let segmentOrder: Int?

    let recordingNumber: Int?
    let fragmentNumber: Int?
    let deletionGroupId: String?

    init(
        id: String,
        startedAt: Date,
        durationSeconds: Int,
        sizeBytes: Int64,
        kind: DashcamArchiveItemKind,
        isProtected: Bool,
        title: String,
        segmentLabel: String? = nil,
        playbackURL: URL,
        sessionId: String? = nil,
        segmentOrder: Int? = nil,
        recordingNumber: Int? = nil,
        fragmentNumber: Int? = nil,
        deletionGroupId: String? = nil
        
    ) {
        self.id = id
        self.startedAt = startedAt
        self.durationSeconds = durationSeconds
        self.sizeBytes = sizeBytes
        self.kind = kind
        self.isProtected = isProtected
        self.title = title
        self.segmentLabel = segmentLabel
        self.playbackURL = playbackURL
        self.sessionId = sessionId
        self.segmentOrder = segmentOrder
        self.recordingNumber = recordingNumber
        self.fragmentNumber = fragmentNumber
        self.deletionGroupId = deletionGroupId
    }
}

struct DashcamVideoSessionRecord: Codable, Identifiable {
    let id: String
    let startedAt: Date
    var endedAt: Date?
    var linkedTripSessionIdInitial: String?
    var finalLinkedTripSessionId: String?
    var stopReason: DashcamStopTrigger?
    var audioEnabled: Bool
    var cameraMode: String
    var segmentIds: [String]
}

enum TripSource: String, Codable {
    case manual
    case videoImplicit = "video_implicit"
}
