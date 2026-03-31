//
//  models.swift
//  TelemetryApp
//

import Foundation

// NOTE: V2 event wire-contract requires JSON key "class". In Swift we map it via CodingKeys.
// MARK: - Telemetry batch (sent to backend)

struct TelemetryBatch: Codable {
    let device_id: String
    let driver_id: String?          // may be nil
    let session_id: String
    let timestamp: String           // batch_created_at (ISO8601)
    
    // === NEW: client/app/device metadata (for clients/client_installs completeness) ===
    let app_version: String?
    let app_build: String?
    let ios_version: String?
    let device_model: String?
    let locale: String?
    let timezone: String?
    
    // === New: trip metadata ===
    let tracking_mode: String?      // "single_trip" | "day_monitoring"
    let transport_mode: String?     // "car" | "bus" | "metro" | "public_transport" | "

    // Idempotency / ordering
    let batch_id: String            // UUID per batch
    let batch_seq: Int              // sequential number within session

    let samples: [TelemetrySample]
    let events: [TelemetryEvent]?
    
    // New (test): resolved trip config (defaults merged with overrides)
    let trip_config: TripConfig?

    // Optional movement hints
    let motion_activity: MotionActivityBatch?
    let pedometer: PedometerBatch?
    
    let altimeter: AltimeterBatch?
    let device_state: DeviceStateBatch?
    let network: NetworkBatch?
    let heading: HeadingBatch?
    let activity_context: ActivityContextBatch?
    let screen_interaction_context: ScreenInteractionContextBatch?

}


// MARK: - Glass game batch (WaterGlassView)

struct GlassGameBatch: Codable {
    let device_id: String
    let driver_id: String?
    let session_id: String

    let game_id: String
    let window_opened_at: String
    let game_started_at: String?
    let game_ended_at: String?
    let window_closed_at: String

    let max_spill_level: Double?

    let total_refilled_01: Double?
    let game_duration_sec: Double?
    let window_duration_sec: Double?

    let background_events: [String: Double]
    let analytics: [String: Double]?
    let aborted: Bool
}


// MARK: - Motion activity aggregation (per batch)


// MARK: - Motion activity aggregation (per batch)

struct MotionActivityBatch: Codable {
    /// Dominant activity for the batch (automotive / cycling / walking / running / stationary / unknown)
    let dominant: String?

    /// Best confidence observed in batch (low / medium / high)
    let confidence: String?

    /// Seconds accumulated per activity type within this batch.
    /// Keys: stationary, walking, running, cycling, automotive, unknown
    let durations_sec: [String: Double]?
}

struct ActivityContextBatch: Codable {
    let dominant: String?
    let best_confidence: String?

    let stationary_share: Double?
    let walking_share: Double?
    let running_share: Double?
    let cycling_share: Double?
    let automotive_share: Double?
    let unknown_share: Double?

    let non_automotive_streak_sec: Double?
    let is_automotive_now: Bool?

    let window_started_at: String?
    let window_ended_at: String?
}

struct ScreenInteractionContextBatch: Codable {
    let count: Int?
    let recent: Bool?
    let active_sec: Double?
    let last_at: String?
    let window_started_at: String?
    let window_ended_at: String?
}

// MARK: - Pedometer aggregation (per batch)

struct PedometerBatch: Codable {
    let steps: Int?
    let distance_m: Double?
    let cadence: Double?            // steps/s (if available)
    let pace: Double?               // s/m (if available)
}

// MARK: - One telemetry sample

struct TelemetrySample: Codable {
    let t: String
    let lat: Double?
    let lon: Double?
    let hAcc: Double?
    let vAcc: Double?

    // V2 canonical speed
    let speed_m_s: Double?

    // Optional accuracies
    let speedAcc: Double?
    let course: Double?
    let courseAcc: Double?

    // Raw IMU
    let accel: Accel?
    let rotation: Rotation?
    let attitude: Attitude?

    // V2 canonical projected accelerations (in g)
    let a_long_g: Double?
    let a_lat_g: Double?
    let a_vert_g: Double?

    enum CodingKeys: String, CodingKey {
        case t, lat, lon, course, accel, rotation, attitude
        case hAcc = "h_acc"
        case vAcc = "v_acc"
        case speed_m_s
        case speedAcc = "speed_acc"
        case courseAcc = "course_acc"
        case a_long_g, a_lat_g, a_vert_g

        // legacy alias (decode-only safety): allow "speed" from old builds
        case speed
    }

    init(
        t: String,
        lat: Double?,
        lon: Double?,
        hAcc: Double?,
        vAcc: Double?,
        speed_m_s: Double?,
        speedAcc: Double?,
        course: Double?,
        courseAcc: Double?,
        accel: Accel?,
        rotation: Rotation?,
        attitude: Attitude?,
        a_long_g: Double?,
        a_lat_g: Double?,
        a_vert_g: Double?
    ) {
        self.t = t
        self.lat = lat
        self.lon = lon
        self.hAcc = hAcc
        self.vAcc = vAcc
        self.speed_m_s = speed_m_s
        self.speedAcc = speedAcc
        self.course = course
        self.courseAcc = courseAcc
        self.accel = accel
        self.rotation = rotation
        self.attitude = attitude
        self.a_long_g = a_long_g
        self.a_lat_g = a_lat_g
        self.a_vert_g = a_vert_g
    }

    // Decode legacy "speed" into speed_m_s if present
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)

        t = try c.decode(String.self, forKey: .t)
        lat = try c.decodeIfPresent(Double.self, forKey: .lat)
        lon = try c.decodeIfPresent(Double.self, forKey: .lon)
        hAcc = try c.decodeIfPresent(Double.self, forKey: .hAcc)
        vAcc = try c.decodeIfPresent(Double.self, forKey: .vAcc)

        let v2 = try c.decodeIfPresent(Double.self, forKey: .speed_m_s)
        let v1 = try c.decodeIfPresent(Double.self, forKey: .speed)
        speed_m_s = v2 ?? v1

        speedAcc = try c.decodeIfPresent(Double.self, forKey: .speedAcc)
        course = try c.decodeIfPresent(Double.self, forKey: .course)
        courseAcc = try c.decodeIfPresent(Double.self, forKey: .courseAcc)

        accel = try c.decodeIfPresent(Accel.self, forKey: .accel)
        rotation = try c.decodeIfPresent(Rotation.self, forKey: .rotation)
        attitude = try c.decodeIfPresent(Attitude.self, forKey: .attitude)

        a_long_g = try c.decodeIfPresent(Double.self, forKey: .a_long_g)
        a_lat_g  = try c.decodeIfPresent(Double.self, forKey: .a_lat_g)
        a_vert_g = try c.decodeIfPresent(Double.self, forKey: .a_vert_g)
    }
    
    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)

        try c.encode(t, forKey: .t)
        try c.encodeIfPresent(lat, forKey: .lat)
        try c.encodeIfPresent(lon, forKey: .lon)
        try c.encodeIfPresent(hAcc, forKey: .hAcc)
        try c.encodeIfPresent(vAcc, forKey: .vAcc)

        // V2 canonical speed (encode only V2 key)
        try c.encodeIfPresent(speed_m_s, forKey: .speed_m_s)

        try c.encodeIfPresent(speedAcc, forKey: .speedAcc)
        try c.encodeIfPresent(course, forKey: .course)
        try c.encodeIfPresent(courseAcc, forKey: .courseAcc)

        try c.encodeIfPresent(accel, forKey: .accel)
        try c.encodeIfPresent(rotation, forKey: .rotation)
        try c.encodeIfPresent(attitude, forKey: .attitude)

        try c.encodeIfPresent(a_long_g, forKey: .a_long_g)
        try c.encodeIfPresent(a_lat_g, forKey: .a_lat_g)
        try c.encodeIfPresent(a_vert_g, forKey: .a_vert_g)
    }

}



// MARK: - IMU structures

struct Accel: Codable {
    let x: Double
    let y: Double
    let z: Double
}

struct Rotation: Codable {
    let x: Double
    let y: Double
    let z: Double
}

struct Attitude: Codable {
    let yaw: Double
    let pitch: Double
    let roll: Double
}

// MARK: - Telemetry event types (client ↔ server contract)
enum TelemetryEventType: String, Codable {
    case accel = "accel"
    case brake = "brake"
    case turn  = "turn"

    case accelInTurn = "accel_in_turn"
    case brakeInTurn = "brake_in_turn"

    case roadAnomaly = "road_anomaly"

    // optional legacy (decode-only safety)
    case suddenAccel = "sudden_accel"
    case suddenBrake = "sudden_brake"
    case suddenTurn  = "sudden_turn"
}



// MARK: - Telemetry event (maneuver)

struct TelemetryEvent: Codable {
    let type: TelemetryEventType
    let t: String

    // magnitude (g for accel/brake/turn, p2p g for road anomalies)
    let intensity: Double

    // human/debug string
    let details: String?

    // V2 provenance
    let origin: String?        // "client" | "server"
    let algo_version: String?  // "v2"

    // V2 speed snapshot
    let speed_m_s: Double

    // V2 maneuver class
    let eventClass: String?   // "sharp" | "emergency"  (wire key: "class")

    // V2 road anomaly subtype / severity
    let subtype: String?       // "pothole" | "bump" | "speed_bump"
    let severity: String?      // "low" | "high"

    // V2 extra structured info
    let meta_json: String?

    enum CodingKeys: String, CodingKey {
        case type, t, intensity, details
        case origin
        case algo_version
        case speed_m_s
        case eventClass = "class"
        case subtype
        case severity
        case meta_json
    }
}



// MARK: - Altimeter aggregation (per batch)

struct AltimeterBatch: Codable {
    let rel_alt_m_min: Double?
    let rel_alt_m_max: Double?
    let pressure_kpa_min: Double?
    let pressure_kpa_max: Double?
}

// MARK: - Device state (per batch)

struct DeviceStateBatch: Codable {
    let battery_level: Double?
    let battery_state: String?
    let low_power_mode: Bool?
   
}

// MARK: - Network summary (per batch)

struct NetworkBatch: Codable {
    let status: String?
    let interface: String?
    let expensive: Bool?
    let constrained: Bool?
}

// MARK: - Heading summary (per batch)

struct HeadingBatch: Codable {
    let magnetic_deg: Double?
    let true_deg: Double?
    let accuracy_deg: Double?
}

// MARK: - Client trip metrics (sent in /trip/finish)

struct ClientAgg: Codable, Equatable {
    let count: Int
    let sum_intensity: Double
    let max_intensity: Double
    let count_per_km: Double
    let sum_per_km: Double
}

struct ClientTripMetrics: Codable, Equatable {
    let trip_distance_m: Double
    let trip_distance_km_from_gps: Double

    let brake: ClientAgg
    let accel: ClientAgg
    let road: ClientAgg
    let turn: ClientAgg
}


// MARK: - Pending trip finish (local retry)

struct PendingTripFinish: Codable, Identifiable, Equatable {
    var id: String { session_id }

    let session_id: String
    let driver_id: String
    let device_id: String
    let client_ended_at: String // ISO8601
    let created_at: String      // ISO8601
    let tracking_mode: String?
    let transport_mode: String?
    let trip_duration_sec: Double?
    let finish_reason: String?
    let client_metrics: ClientTripMetrics?
    
    // NEW: immutable snapshot of device_context captured at stop time
    let device_context_json: String?
    
    let tail_activity_context_json: String?
    
    // === NEW: client/app/device metadata ===
    let app_version: String?
    let app_build: String?
    let ios_version: String?
    let device_model: String?
    let locale: String?
    let timezone: String?
}

struct ClientAggMetric: Codable, Equatable {
    var count: Int
    var sumIntensity: Double
    var maxIntensity: Double?

    init(count: Int = 0, sumIntensity: Double = 0.0, maxIntensity: Double? = nil) {
        self.count = count
        self.sumIntensity = sumIntensity
        self.maxIntensity = maxIntensity
    }
}

struct ClientTripAggTotalsV1: Codable, Equatable {
    var accel: ClientAggMetric
    var brake: ClientAggMetric
    var turn: ClientAggMetric
    var road: ClientAggMetric
    var accel_in_turn: ClientAggMetric
    var brake_in_turn: ClientAggMetric
}

struct ClientTripAggThresholdsV1: Codable, Equatable {
    var min_event_g: Double
    var max_reasonable_g: Double
}

struct ClientTripAggV1: Codable, Equatable {
    var normal: ClientTripAggTotalsV1
    var extreme: ClientTripAggTotalsV1
    var distance_km: Double?
    var duration_sec: Double?
    var thresholds: ClientTripAggThresholdsV1
}
