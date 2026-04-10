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

    let recording_start_lat: Double?
    let recording_start_lon: Double?
    let recording_end_lat: Double?
    let recording_end_lon: Double?

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
    
    let session_start_sample_t: String?
    let session_end_sample_t: String?
    let total_samples: Int?
    let total_events: Int?
    
    let session_start_speed_kmh: Double?
    let session_end_speed_kmh: Double?
    let session_event_types: [String]?

    init(
        video_session_id: String,
        linked_trip_session_id: String?,
        driver_id: String,
        device_id: String,
        started_at: String,
        ended_at: String?,
        recording_start_lat: Double?,
        recording_start_lon: Double?,
        recording_end_lat: Double?,
        recording_end_lon: Double?,
        session_start_sample_t: String?,
        session_end_sample_t: String?,
        total_samples: Int?,
        total_events: Int?,
        session_start_speed_kmh: Double?,
        session_end_speed_kmh: Double?,
        session_event_types: [String]?,
        stop_reason: String?,
        camera_mode: String,
        audio_enabled: Bool,
        is_crash_log: Bool,
        crash_detected_at: String?,
        crash_lat: Double?,
        crash_lon: Double?,
        crash_max_g: Double?,
        total_size_bytes: Int64?,
        total_segments_count: Int?,
        archive_normal_count: Int,
        archive_crash_count: Int,
        archive_normal_size_bytes: Int64,
        archive_crash_size_bytes: Int64
    
    ) {
        self.video_session_id = video_session_id
        self.linked_trip_session_id = linked_trip_session_id
        self.driver_id = driver_id
        self.device_id = device_id
        self.started_at = started_at
        self.ended_at = ended_at
        self.recording_start_lat = recording_start_lat
        self.recording_start_lon = recording_start_lon
        self.recording_end_lat = recording_end_lat
        self.recording_end_lon = recording_end_lon
        self.stop_reason = stop_reason
        self.camera_mode = camera_mode
        self.audio_enabled = audio_enabled
        self.is_crash_log = is_crash_log
        self.crash_detected_at = crash_detected_at
        self.crash_lat = crash_lat
        self.crash_lon = crash_lon
        self.crash_max_g = crash_max_g
        self.total_size_bytes = total_size_bytes
        self.total_segments_count = total_segments_count
        self.archive_normal_count = archive_normal_count
        self.archive_crash_count = archive_crash_count
        self.archive_normal_size_bytes = archive_normal_size_bytes
        self.archive_crash_size_bytes = archive_crash_size_bytes
        self.session_start_sample_t = session_start_sample_t
        self.session_end_sample_t = session_end_sample_t
        self.total_samples = total_samples
        self.total_events = total_events
        self.session_start_speed_kmh = session_start_speed_kmh
        self.session_end_speed_kmh = session_end_speed_kmh
        self.session_event_types = session_event_types
    }
}

struct CrashEvent: Equatable {
    let at: Date
    let gForce: Double
    let latitude: Double?
    let longitude: Double?
}

struct CrashLogRequest: Codable {
    let crash_id: String

    let video_session_id: String?
    let trip_session_id: String?

    let crash_detected_at: String

    let latitude: Double?
    let longitude: Double?

    let max_g: Double?

    let active_segment_id: String?

    let pre_window_sec: Int
    let post_window_sec: Int

    // Optional snapshot
    let nearest_sample_timestamp: String?
    let nearest_speed_kmh: Double?
    let nearest_heading: Double?
    let event_types_nearby: [String]?

}
