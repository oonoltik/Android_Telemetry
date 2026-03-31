import Foundation

struct TripReport: Codable, Identifiable {
    
    // === Local-only state (not part of server JSON) ===
    enum LocalStatus: String {
        case ready
        case queued
    }
    
    static func queued(sessionId: String, driverId: String, deviceId: String) -> TripReport {
        TripReport(
            local_status: .queued,
            session_id: sessionId,
            driver_id: driverId,
            device_id: deviceId,
            trip_score: 0.0,
            worst_batch_score: 0.0
        )
    }


    var local_status: LocalStatus = .ready


    // === Identifiable ===
    var id: String { session_id }

    // === IDs ===
    let session_id: String
    let driver_id: String
    let device_id: String

    // === Time ===
    let client_started_at: String?
    let client_ended_at: String?
    let received_started_at: String?
    let received_ended_at: String?

    // === Delivery counters ===
    let batches_count: Int
    let samples_count: Int
    let events_count: Int

    // === Trip aggregates ===
    let distance_km: Double?
    let stops_count: Int?
    let stops_total_sec: Double?
    let stops_p95_sec: Double?
    let stops_per_km: Double?

    // === V2 totals ===
    let accel_sharp_total: Int
    let accel_emergency_total: Int

    let brake_sharp_total: Int
    let brake_emergency_total: Int

    let turn_sharp_total: Int
    let turn_emergency_total: Int

    // combined risk (kept split; UI can sum)
    let accel_in_turn_sharp_total: Int
    let accel_in_turn_emergency_total: Int
    let brake_in_turn_sharp_total: Int
    let brake_in_turn_emergency_total: Int

    // road anomalies
    let road_anomaly_low_total: Int
    let road_anomaly_high_total: Int

    // === Scores ===
    let trip_score: Double
    let trip_score_exposure: Double?
    let trip_preset: String?
    let trip_penalty_total: Double?
    let worst_batch_score: Double

    // === Speed ===
    let speed_max_kmh: Double?
    let speed_avg_kmh: Double?
    let speed_p95_kmh: Double?

    // === IMU diagnostics ===
    let accel_x_min: Double?
    let accel_x_max: Double?
    let accel_y_abs_max: Double?
    let accel_z_abs_max: Double?
    let gyro_z_abs_max: Double?

    // === Data completeness ===
    let batch_seq_max: Int?
    let batches_missing_count: Int?
    let batches_missing_seqs: String?

    // === GPS quality ===
    let gps_points: Int?
    let gps_1hz_points: Int?
    let gps_hacc_p95_m: Double?
    let gps_good_100_share: Double?
    let gps_unique_coords_5dp: Int?
    let gps_span_m: Double?
    let gps_is_stuck: Int?
    let gps_quality_score: Int?

    // === Comparisons / ranking ===
    let better_than_prev_pct: Double?
    let better_than_all_pct: Double?
    let prev_trips_count: Int?
    let all_trips_count: Int?
    let driver_rank: Int?
    let total_drivers: Int?
    let driver_avg_score: Double?
    let driver_trips_total: Int?
    
    // Public Alpha additive fields
    let score_v2: Double?
    let driving_load: Double?
    let avg_speed_kmh: Double?
    let driving_mode: String?
    let trip_duration_sec: Double?

    // Convenience (UI already uses these)
    var accel_in_turn_total: Int { accel_in_turn_sharp_total + accel_in_turn_emergency_total }
    var brake_in_turn_total: Int { brake_in_turn_sharp_total + brake_in_turn_emergency_total }

    enum CodingKeys: String, CodingKey {
        case session_id, driver_id, device_id
        case client_started_at, client_ended_at, received_started_at, received_ended_at

        case batches_count, samples_count, events_count
        case distance_km
        case stops_count, stops_total_sec, stops_p95_sec, stops_per_km

        case accel_sharp_total, accel_emergency_total
        case brake_sharp_total, brake_emergency_total
        case turn_sharp_total, turn_emergency_total

        case accel_in_turn_sharp_total, accel_in_turn_emergency_total
        case brake_in_turn_sharp_total, brake_in_turn_emergency_total

        case road_anomaly_low_total, road_anomaly_high_total

        case trip_score, worst_batch_score
        case trip_score_exposure, trip_preset, trip_penalty_total
        case speed_max_kmh, speed_avg_kmh, speed_p95_kmh

        case accel_x_min, accel_x_max, accel_y_abs_max, accel_z_abs_max, gyro_z_abs_max

        case batch_seq_max, batches_missing_count, batches_missing_seqs

        case gps_points, gps_1hz_points, gps_hacc_p95_m, gps_good_100_share
        case gps_unique_coords_5dp, gps_span_m, gps_is_stuck, gps_quality_score

        case better_than_prev_pct, better_than_all_pct
        case prev_trips_count, all_trips_count
        case driver_rank, total_drivers, driver_avg_score, driver_trips_total
        
        // Public Alpha additive fields
        case score_v2
        case driving_load
        case avg_speed_kmh
        case driving_mode
        case trip_duration_sec
    }
    init(
        local_status: LocalStatus = .ready,
        session_id: String,
        driver_id: String,
        device_id: String,

        client_started_at: String? = nil,
        client_ended_at: String? = nil,
        received_started_at: String? = nil,
        received_ended_at: String? = nil,

        batches_count: Int = 0,
        samples_count: Int = 0,
        events_count: Int = 0,

        distance_km: Double? = 0.0,
        stops_count: Int? = 0,
        stops_total_sec: Double? = 0.0,
        stops_p95_sec: Double? = 0.0,
        stops_per_km: Double? = nil,

        accel_sharp_total: Int = 0,
        accel_emergency_total: Int = 0,
        brake_sharp_total: Int = 0,
        brake_emergency_total: Int = 0,
        turn_sharp_total: Int = 0,
        turn_emergency_total: Int = 0,
        accel_in_turn_sharp_total: Int = 0,
        accel_in_turn_emergency_total: Int = 0,
        brake_in_turn_sharp_total: Int = 0,
        brake_in_turn_emergency_total: Int = 0,
        road_anomaly_low_total: Int = 0,
        road_anomaly_high_total: Int = 0,

        trip_score: Double = 0.0,
        trip_score_exposure: Double? = nil,
        trip_preset: String? = nil,
        trip_penalty_total: Double? = nil,
        worst_batch_score: Double = 0.0,

        speed_max_kmh: Double? = nil,
        speed_avg_kmh: Double? = nil,
        speed_p95_kmh: Double? = nil,

        accel_x_min: Double? = nil,
        accel_x_max: Double? = nil,
        accel_y_abs_max: Double? = nil,
        accel_z_abs_max: Double? = nil,
        gyro_z_abs_max: Double? = nil,

        batch_seq_max: Int? = nil,
        batches_missing_count: Int? = nil,
        batches_missing_seqs: String? = nil,

        gps_points: Int? = nil,
        gps_1hz_points: Int? = nil,
        gps_hacc_p95_m: Double? = nil,
        gps_good_100_share: Double? = nil,
        gps_unique_coords_5dp: Int? = nil,
        gps_span_m: Double? = nil,
        gps_is_stuck: Int? = nil,
        gps_quality_score: Int? = nil,

        better_than_prev_pct: Double? = nil,
        better_than_all_pct: Double? = nil,
        prev_trips_count: Int? = nil,
        all_trips_count: Int? = nil,
        driver_rank: Int? = nil,
        total_drivers: Int? = nil,
        driver_avg_score: Double? = nil,
        driver_trips_total: Int? = nil,
        
        // Public Alpha additive fields
        score_v2: Double? = nil,
        driving_load: Double? = nil,
        avg_speed_kmh: Double? = nil,
        driving_mode: String? = nil,
        trip_duration_sec: Double? = nil
    ) {
        self.local_status = local_status

        self.session_id = session_id
        self.driver_id = driver_id
        self.device_id = device_id

        self.client_started_at = client_started_at
        self.client_ended_at = client_ended_at
        self.received_started_at = received_started_at
        self.received_ended_at = received_ended_at

        self.batches_count = batches_count
        self.samples_count = samples_count
        self.events_count = events_count

        self.distance_km = distance_km
        self.stops_count = stops_count
        self.stops_total_sec = stops_total_sec
        self.stops_p95_sec = stops_p95_sec
        self.stops_per_km = stops_per_km

        self.accel_sharp_total = accel_sharp_total
        self.accel_emergency_total = accel_emergency_total
        self.brake_sharp_total = brake_sharp_total
        self.brake_emergency_total = brake_emergency_total
        self.turn_sharp_total = turn_sharp_total
        self.turn_emergency_total = turn_emergency_total
        self.accel_in_turn_sharp_total = accel_in_turn_sharp_total
        self.accel_in_turn_emergency_total = accel_in_turn_emergency_total
        self.brake_in_turn_sharp_total = brake_in_turn_sharp_total
        self.brake_in_turn_emergency_total = brake_in_turn_emergency_total
        self.road_anomaly_low_total = road_anomaly_low_total
        self.road_anomaly_high_total = road_anomaly_high_total

        self.trip_score = trip_score
        self.trip_score_exposure = trip_score_exposure
        self.trip_preset = trip_preset
        self.trip_penalty_total = trip_penalty_total
        self.worst_batch_score = worst_batch_score

        self.speed_max_kmh = speed_max_kmh
        self.speed_avg_kmh = speed_avg_kmh
        self.speed_p95_kmh = speed_p95_kmh

        self.accel_x_min = accel_x_min
        self.accel_x_max = accel_x_max
        self.accel_y_abs_max = accel_y_abs_max
        self.accel_z_abs_max = accel_z_abs_max
        self.gyro_z_abs_max = gyro_z_abs_max

        self.batch_seq_max = batch_seq_max
        self.batches_missing_count = batches_missing_count
        self.batches_missing_seqs = batches_missing_seqs

        self.gps_points = gps_points
        self.gps_1hz_points = gps_1hz_points
        self.gps_hacc_p95_m = gps_hacc_p95_m
        self.gps_good_100_share = gps_good_100_share
        self.gps_unique_coords_5dp = gps_unique_coords_5dp
        self.gps_span_m = gps_span_m
        self.gps_is_stuck = gps_is_stuck
        self.gps_quality_score = gps_quality_score

        self.better_than_prev_pct = better_than_prev_pct
        self.better_than_all_pct = better_than_all_pct
        self.prev_trips_count = prev_trips_count
        self.all_trips_count = all_trips_count
        self.driver_rank = driver_rank
        self.total_drivers = total_drivers
        self.driver_avg_score = driver_avg_score
        self.driver_trips_total = driver_trips_total
        
        // Public Alpha additive fields
        self.score_v2 = score_v2
        self.driving_load = driving_load
        self.avg_speed_kmh = avg_speed_kmh
        self.driving_mode = driving_mode
        self.trip_duration_sec = trip_duration_sec
    }


    init(from decoder: Decoder) throws {
        self.local_status = .ready

        let c = try decoder.container(keyedBy: CodingKeys.self)

        self.session_id = try c.decode(String.self, forKey: .session_id)
        self.driver_id = (try c.decodeIfPresent(String.self, forKey: .driver_id)) ?? ""
        self.device_id = (try c.decodeIfPresent(String.self, forKey: .device_id)) ?? ""


        self.client_started_at   = try c.decodeIfPresent(String.self, forKey: .client_started_at)
        self.client_ended_at     = try c.decodeIfPresent(String.self, forKey: .client_ended_at)
        self.received_started_at = try c.decodeIfPresent(String.self, forKey: .received_started_at)
        self.received_ended_at   = try c.decodeIfPresent(String.self, forKey: .received_ended_at)

        self.batches_count = (try c.decodeIfPresent(Int.self, forKey: .batches_count)) ?? 0
        self.samples_count = (try c.decodeIfPresent(Int.self, forKey: .samples_count)) ?? 0
        self.events_count  = (try c.decodeIfPresent(Int.self, forKey: .events_count)) ?? 0

        self.distance_km     = try c.decodeIfPresent(Double.self, forKey: .distance_km)
        self.stops_count     = try c.decodeIfPresent(Int.self, forKey: .stops_count)
        self.stops_total_sec = try c.decodeIfPresent(Double.self, forKey: .stops_total_sec)
        self.stops_p95_sec   = try c.decodeIfPresent(Double.self, forKey: .stops_p95_sec)
        self.stops_per_km    = try c.decodeIfPresent(Double.self, forKey: .stops_per_km)

        self.accel_sharp_total     = (try c.decodeIfPresent(Int.self, forKey: .accel_sharp_total)) ?? 0
        self.accel_emergency_total = (try c.decodeIfPresent(Int.self, forKey: .accel_emergency_total)) ?? 0

        self.brake_sharp_total     = (try c.decodeIfPresent(Int.self, forKey: .brake_sharp_total)) ?? 0
        self.brake_emergency_total = (try c.decodeIfPresent(Int.self, forKey: .brake_emergency_total)) ?? 0

        self.turn_sharp_total     = (try c.decodeIfPresent(Int.self, forKey: .turn_sharp_total)) ?? 0
        self.turn_emergency_total = (try c.decodeIfPresent(Int.self, forKey: .turn_emergency_total)) ?? 0

        self.accel_in_turn_sharp_total     = (try c.decodeIfPresent(Int.self, forKey: .accel_in_turn_sharp_total)) ?? 0
        self.accel_in_turn_emergency_total = (try c.decodeIfPresent(Int.self, forKey: .accel_in_turn_emergency_total)) ?? 0
        self.brake_in_turn_sharp_total     = (try c.decodeIfPresent(Int.self, forKey: .brake_in_turn_sharp_total)) ?? 0
        self.brake_in_turn_emergency_total = (try c.decodeIfPresent(Int.self, forKey: .brake_in_turn_emergency_total)) ?? 0

        self.road_anomaly_low_total  = (try c.decodeIfPresent(Int.self, forKey: .road_anomaly_low_total)) ?? 0
        self.road_anomaly_high_total = (try c.decodeIfPresent(Int.self, forKey: .road_anomaly_high_total)) ?? 0

        self.trip_score        = (try c.decodeIfPresent(Double.self, forKey: .trip_score)) ?? 0
        self.trip_score_exposure = try c.decodeIfPresent(Double.self, forKey: .trip_score_exposure)
        self.trip_preset = try c.decodeIfPresent(String.self, forKey: .trip_preset)
        self.trip_penalty_total = try c.decodeIfPresent(Double.self, forKey: .trip_penalty_total)
        self.worst_batch_score = (try c.decodeIfPresent(Double.self, forKey: .worst_batch_score)) ?? 0

        self.speed_max_kmh = try c.decodeIfPresent(Double.self, forKey: .speed_max_kmh)
        self.speed_avg_kmh = try c.decodeIfPresent(Double.self, forKey: .speed_avg_kmh)
        self.speed_p95_kmh = try c.decodeIfPresent(Double.self, forKey: .speed_p95_kmh)

        self.accel_x_min = try c.decodeIfPresent(Double.self, forKey: .accel_x_min)
        self.accel_x_max = try c.decodeIfPresent(Double.self, forKey: .accel_x_max)
        self.accel_y_abs_max = try c.decodeIfPresent(Double.self, forKey: .accel_y_abs_max)
        self.accel_z_abs_max = try c.decodeIfPresent(Double.self, forKey: .accel_z_abs_max)
        self.gyro_z_abs_max  = try c.decodeIfPresent(Double.self, forKey: .gyro_z_abs_max)

        self.batch_seq_max = try c.decodeIfPresent(Int.self, forKey: .batch_seq_max)
        self.batches_missing_count = try c.decodeIfPresent(Int.self, forKey: .batches_missing_count)
        self.batches_missing_seqs = try c.decodeIfPresent(String.self, forKey: .batches_missing_seqs)

        self.gps_points = try c.decodeIfPresent(Int.self, forKey: .gps_points)
        self.gps_1hz_points = try c.decodeIfPresent(Int.self, forKey: .gps_1hz_points)
        self.gps_hacc_p95_m = try c.decodeIfPresent(Double.self, forKey: .gps_hacc_p95_m)
        self.gps_good_100_share = try c.decodeIfPresent(Double.self, forKey: .gps_good_100_share)
        self.gps_unique_coords_5dp = try c.decodeIfPresent(Int.self, forKey: .gps_unique_coords_5dp)
        self.gps_span_m = try c.decodeIfPresent(Double.self, forKey: .gps_span_m)
        self.gps_is_stuck = try c.decodeIfPresent(Int.self, forKey: .gps_is_stuck)
        self.gps_quality_score = try c.decodeIfPresent(Int.self, forKey: .gps_quality_score)

        self.better_than_prev_pct = try c.decodeIfPresent(Double.self, forKey: .better_than_prev_pct)
        self.better_than_all_pct  = try c.decodeIfPresent(Double.self, forKey: .better_than_all_pct)
        self.prev_trips_count = try c.decodeIfPresent(Int.self, forKey: .prev_trips_count)
        self.all_trips_count  = try c.decodeIfPresent(Int.self, forKey: .all_trips_count)

        self.driver_rank      = try c.decodeIfPresent(Int.self, forKey: .driver_rank)
        self.total_drivers    = try c.decodeIfPresent(Int.self, forKey: .total_drivers)
        self.driver_avg_score = try c.decodeIfPresent(Double.self, forKey: .driver_avg_score)
        self.driver_trips_total = try c.decodeIfPresent(Int.self, forKey: .driver_trips_total)
        
        // Public Alpha additive fields
        self.score_v2 = try c.decodeIfPresent(Double.self, forKey: .score_v2)
        self.driving_load = try c.decodeIfPresent(Double.self, forKey: .driving_load)
        self.avg_speed_kmh = try c.decodeIfPresent(Double.self, forKey: .avg_speed_kmh)
        self.driving_mode = try c.decodeIfPresent(String.self, forKey: .driving_mode)
        self.trip_duration_sec = try c.decodeIfPresent(Double.self, forKey: .trip_duration_sec)
    }
    
}
