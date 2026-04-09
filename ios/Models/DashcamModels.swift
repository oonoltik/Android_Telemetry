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
    case archiveBlockedByCrashRecords
    case insufficientSpaceForNextSegment
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
        case .archiveBlockedByCrashRecords:
            return "Архив заполнен аварийными записями. Удалите часть аварийных записей вручную или сохраните их в медиатеку."
        case .insufficientSpaceForNextSegment:
            return "Недостаточно места для следующего сегмента. Текущая запись завершена."
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
    var isSavedToPhotoLibrary: Bool

    enum CodingKeys: String, CodingKey {
        case id
        case sessionId
        case startedAt
        case endedAt
        case sizeBytes
        case isProtected
        case fileURL
        case order
        case isSavedToPhotoLibrary
    }

    init(
        id: String,
        sessionId: String,
        startedAt: Date,
        endedAt: Date?,
        sizeBytes: Int64,
        isProtected: Bool,
        fileURL: URL,
        order: Int,
        isSavedToPhotoLibrary: Bool = false
    ) {
        self.id = id
        self.sessionId = sessionId
        self.startedAt = startedAt
        self.endedAt = endedAt
        self.sizeBytes = sizeBytes
        self.isProtected = isProtected
        self.fileURL = fileURL
        self.order = order
        self.isSavedToPhotoLibrary = isSavedToPhotoLibrary
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)

        id = try container.decode(String.self, forKey: .id)
        sessionId = try container.decode(String.self, forKey: .sessionId)
        startedAt = try container.decode(Date.self, forKey: .startedAt)
        endedAt = try container.decodeIfPresent(Date.self, forKey: .endedAt)
        sizeBytes = try container.decode(Int64.self, forKey: .sizeBytes)
        isProtected = try container.decode(Bool.self, forKey: .isProtected)
        fileURL = try container.decode(URL.self, forKey: .fileURL)
        order = try container.decode(Int.self, forKey: .order)

        // backward compatibility for old archive_index.json
        isSavedToPhotoLibrary = try container.decodeIfPresent(Bool.self, forKey: .isSavedToPhotoLibrary) ?? false
    }
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
    var isSavedToPhotoLibrary: Bool

    enum CodingKeys: String, CodingKey {
        case id
        case crashAt
        case preSeconds
        case postSeconds
        case segmentIds
        case linkedTripSessionId
        case latitude
        case longitude
        case maxG
        case isSavedToPhotoLibrary
    }

    init(
        id: String,
        crashAt: Date,
        preSeconds: Int,
        postSeconds: Int,
        segmentIds: [String],
        linkedTripSessionId: String?,
        latitude: Double?,
        longitude: Double?,
        maxG: Double?,
        isSavedToPhotoLibrary: Bool = false
    ) {
        self.id = id
        self.crashAt = crashAt
        self.preSeconds = preSeconds
        self.postSeconds = postSeconds
        self.segmentIds = segmentIds
        self.linkedTripSessionId = linkedTripSessionId
        self.latitude = latitude
        self.longitude = longitude
        self.maxG = maxG
        self.isSavedToPhotoLibrary = isSavedToPhotoLibrary
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        crashAt = try c.decode(Date.self, forKey: .crashAt)
        preSeconds = try c.decode(Int.self, forKey: .preSeconds)
        postSeconds = try c.decode(Int.self, forKey: .postSeconds)
        segmentIds = try c.decode([String].self, forKey: .segmentIds)
        linkedTripSessionId = try c.decodeIfPresent(String.self, forKey: .linkedTripSessionId)
        latitude = try c.decodeIfPresent(Double.self, forKey: .latitude)
        longitude = try c.decodeIfPresent(Double.self, forKey: .longitude)
        maxG = try c.decodeIfPresent(Double.self, forKey: .maxG)
        isSavedToPhotoLibrary = try c.decodeIfPresent(Bool.self, forKey: .isSavedToPhotoLibrary) ?? false
    }
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
    let isSavedToPhotoLibrary: Bool

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
        deletionGroupId: String? = nil,
        isSavedToPhotoLibrary: Bool = false
        
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
        self.isSavedToPhotoLibrary = isSavedToPhotoLibrary
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
