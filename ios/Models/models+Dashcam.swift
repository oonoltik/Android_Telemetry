import Foundation
import Combine

struct VideoSessionStartRequest: Codable {
    let video_session_id: String
    let device_id: String
    let driver_id: String
    let started_at: String
    let linked_trip_session_id: String?
    let trip_source: TripSource
    let camera_mode: String
    let audio_enabled: Bool
    let app_version: String?
    let ios_version: String?
    let device_model: String?
}

struct VideoSessionStopRequest: Codable {
    let video_session_id: String
    let ended_at: String
    let stop_reason: String
    let final_linked_trip_session_id: String?
    let segments_count: Int?
    let total_size_bytes: Int64?
}

struct CrashClipEventRequest: Codable {
    let crash_clip_id: String
    let video_session_id: String?
    let linked_trip_session_id: String?
    let crash_detected_at: String
    let pre_seconds: Int
    let post_seconds: Int
    let segment_ids: [String]
    let lat: Double?
    let lon: Double?
    let max_g: Double?
    let speed_kmh: Double?
}

struct DashcamCameraLogRequest: Codable {
    let video_session_id: String
    let linked_trip_session_id: String?
    let driver_id: String
    let device_id: String
    let started_at: String
    let ended_at: String?
    let stop_reason: String?
    let camera_mode: String
    let audio_enabled: Bool
    let is_crash_log: Bool
    let crash_detected_at: String?
    let crash_lat: Double?
    let crash_lon: Double?
    let crash_max_g: Double?
    let total_size_bytes: Int64?
    let total_segments_count: Int?
    let archive_normal_count: Int
    let archive_crash_count: Int
    let archive_normal_size_bytes: Int64
    let archive_crash_size_bytes: Int64
}

struct CrashEvent: Equatable {
    let at: Date
    let gForce: Double
    let latitude: Double?
    let longitude: Double?
}
