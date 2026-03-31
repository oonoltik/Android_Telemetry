import Foundation

struct OverrideDouble: Codable { var enabled: Bool; var value: Double }
struct OverrideDoubleArray: Codable { var enabled: Bool; var value: [Double] }

struct TripConfigOverrides: Codable {
    var speed_gate_accel_brake_ms: OverrideDouble
    var speed_gate_turn_ms: OverrideDouble
    var speed_gate_combined_ms: OverrideDouble

    var cooldown_accel_brake_s: OverrideDouble
    var cooldown_turn_s: OverrideDouble
    var cooldown_combined_s: OverrideDouble
    var cooldown_road_s: OverrideDouble

    var accel_sharp_g: OverrideDouble
    var accel_emergency_g: OverrideDouble
    var brake_sharp_g: OverrideDouble
    var brake_emergency_g: OverrideDouble

    var turn_sharp_lat_g: OverrideDouble
    var turn_emergency_lat_g: OverrideDouble

    var combined_lat_min_g: OverrideDouble
    var accel_in_turn_sharp_g: OverrideDouble
    var accel_in_turn_emergency_g: OverrideDouble
    var brake_in_turn_sharp_g: OverrideDouble
    var brake_in_turn_emergency_g: OverrideDouble

    var road_window_s: OverrideDouble
    var road_low_p2p_g: OverrideDouble
    var road_high_p2p_g: OverrideDouble
    var road_low_abs_g: OverrideDouble
    var road_high_abs_g: OverrideDouble

    var double_count_window_s: OverrideDouble
    var speed_breakpoints_ms: OverrideDoubleArray
    var speed_factors: OverrideDoubleArray

    var penalty_accel_sharp: OverrideDouble
    var penalty_accel_emergency: OverrideDouble
    var penalty_brake_sharp: OverrideDouble
    var penalty_brake_emergency: OverrideDouble
    var penalty_turn_sharp: OverrideDouble
    var penalty_turn_emergency: OverrideDouble
    var penalty_accel_in_turn_sharp: OverrideDouble
    var penalty_accel_in_turn_emergency: OverrideDouble
    var penalty_brake_in_turn_sharp: OverrideDouble
    var penalty_brake_in_turn_emergency: OverrideDouble
    var penalty_road_low: OverrideDouble
    var penalty_road_high: OverrideDouble

    static func `default`() -> TripConfigOverrides {
        func d(_ v: Double) -> OverrideDouble { .init(enabled: false, value: v) }
        func a(_ v: [Double]) -> OverrideDoubleArray { .init(enabled: false, value: v) }
        return TripConfigOverrides(
            speed_gate_accel_brake_ms: d(3.0),
            speed_gate_turn_ms: d(5.0),
            speed_gate_combined_ms: d(5.0),
            cooldown_accel_brake_s: d(1.2),
            cooldown_turn_s: d(0.8),
            cooldown_combined_s: d(1.2),
            cooldown_road_s: d(1.2),
            accel_sharp_g: d(0.20),
            accel_emergency_g: d(0.30),
            brake_sharp_g: d(0.18),
            brake_emergency_g: d(0.28),
            turn_sharp_lat_g: d(0.25),
            turn_emergency_lat_g: d(0.35),
            combined_lat_min_g: d(0.35),
            accel_in_turn_sharp_g: d(0.22),
            accel_in_turn_emergency_g: d(0.32),
            brake_in_turn_sharp_g: d(0.22),
            brake_in_turn_emergency_g: d(0.32),
            road_window_s: d(0.40),
            road_low_p2p_g: d(0.70),
            road_high_p2p_g: d(1.10),
            road_low_abs_g: d(0.45),
            road_high_abs_g: d(0.75),
            double_count_window_s: d(0.6),
            speed_breakpoints_ms: a([0.0, 5.0, 13.9, 22.2, 30.6]),
            speed_factors: a([0.25, 0.45, 0.75, 1.05, 1.35]),
            penalty_accel_sharp: d(1.0),
            penalty_accel_emergency: d(2.0),
            penalty_brake_sharp: d(1.5),
            penalty_brake_emergency: d(3.0),
            penalty_turn_sharp: d(1.2),
            penalty_turn_emergency: d(2.4),
            penalty_accel_in_turn_sharp: d(2.2),
            penalty_accel_in_turn_emergency: d(4.0),
            penalty_brake_in_turn_sharp: d(2.6),
            penalty_brake_in_turn_emergency: d(4.6),
            penalty_road_low: d(0.3),
            penalty_road_high: d(0.8)
        )
    }
}

struct TripConfig: Codable { let v2: V2Placeholders; let scoring: ScoringConfig }

struct V2Placeholders: Codable {
    let speed_gate_accel_brake_ms: Double
    let speed_gate_turn_ms: Double
    let speed_gate_combined_ms: Double
    let cooldown_accel_brake_s: Double
    let cooldown_turn_s: Double
    let cooldown_combined_s: Double
    let cooldown_road_s: Double
    let accel_sharp_g: Double
    let accel_emergency_g: Double
    let brake_sharp_g: Double
    let brake_emergency_g: Double
    let turn_sharp_lat_g: Double
    let turn_emergency_lat_g: Double
    let combined_lat_min_g: Double
    let accel_in_turn_sharp_g: Double
    let accel_in_turn_emergency_g: Double
    let brake_in_turn_sharp_g: Double
    let brake_in_turn_emergency_g: Double
    let road_window_s: Double
    let road_low_p2p_g: Double
    let road_high_p2p_g: Double
    let road_low_abs_g: Double
    let road_high_abs_g: Double
}

struct ScoringConfig: Codable {
    let double_count_window_s: Double
    let speed_factor: SpeedFactorConfig
    let penalty: PenaltyConfig
}

struct SpeedFactorConfig: Codable { let breakpoints_ms: [Double]; let factors: [Double] }

struct PenaltyConfig: Codable {
    let accel: ClassPenalty
    let brake: ClassPenalty
    let turn: ClassPenalty
    let accel_in_turn: ClassPenalty
    let brake_in_turn: ClassPenalty
    let road_anomaly: SeverityPenalty
}
struct ClassPenalty: Codable { let sharp: Double; let emergency: Double }
struct SeverityPenalty: Codable { let low: Double; let high: Double }

enum TripConfigOverridesStorage {
    private static let key = "trip_config_overrides_v1"
    private static let savedMetaKey = "trip_config_overrides_saved_meta_v1"

    static func load() -> TripConfigOverrides {
        guard
            let data = UserDefaults.standard.data(forKey: key),
            let obj = try? JSONDecoder().decode(TripConfigOverrides.self, from: data)
        else {
            return TripConfigOverrides.default()
        }
        return obj
    }

    static func save(_ obj: TripConfigOverrides) {
        if let data = try? JSONEncoder().encode(obj) {
            UserDefaults.standard.set(data, forKey: key)
        }
    }

    static func markSavedNow(_ obj: TripConfigOverrides) {
        let enabledCount = countEnabledOverrides(obj)
        let meta: [String: Any] = [
            "ts": Date().timeIntervalSince1970,
            "enabledCount": enabledCount
        ]
        UserDefaults.standard.set(meta, forKey: savedMetaKey)
    }

    static func lastSavedLabel() -> String? {
        guard
            let meta = UserDefaults.standard.dictionary(forKey: savedMetaKey),
            let ts = meta["ts"] as? TimeInterval
        else { return nil }

        let enabledCount = meta["enabledCount"] as? Int ?? 0
        let date = Date(timeIntervalSince1970: ts)

        let df = DateFormatter()
        df.locale = Locale(identifier: "ru_RU")
        df.dateStyle = .medium
        df.timeStyle = .short

        return "Сохранено: \(df.string(from: date)) · Override включено: \(enabledCount)"
    }

    private static func countEnabledOverrides(_ obj: TripConfigOverrides) -> Int {
        var n = 0
        func add(_ x: OverrideDouble) { if x.enabled { n += 1 } }
        func addA(_ x: OverrideDoubleArray) { if x.enabled { n += 1 } }

        // gates / cooldowns
        add(obj.speed_gate_accel_brake_ms)
        add(obj.speed_gate_turn_ms)
        add(obj.speed_gate_combined_ms)
        add(obj.cooldown_accel_brake_s)
        add(obj.cooldown_turn_s)
        add(obj.cooldown_combined_s)
        add(obj.cooldown_road_s)

        // thresholds
        add(obj.accel_sharp_g)
        add(obj.accel_emergency_g)
        add(obj.brake_sharp_g)
        add(obj.brake_emergency_g)
        add(obj.turn_sharp_lat_g)
        add(obj.turn_emergency_lat_g)
        add(obj.combined_lat_min_g)
        add(obj.accel_in_turn_sharp_g)
        add(obj.accel_in_turn_emergency_g)
        add(obj.brake_in_turn_sharp_g)
        add(obj.brake_in_turn_emergency_g)

        // road
        add(obj.road_window_s)
        add(obj.road_low_p2p_g)
        add(obj.road_high_p2p_g)
        add(obj.road_low_abs_g)
        add(obj.road_high_abs_g)

        // scoring
        add(obj.double_count_window_s)
        addA(obj.speed_breakpoints_ms)
        addA(obj.speed_factors)

        // penalties
        add(obj.penalty_accel_sharp)
        add(obj.penalty_accel_emergency)
        add(obj.penalty_brake_sharp)
        add(obj.penalty_brake_emergency)
        add(obj.penalty_turn_sharp)
        add(obj.penalty_turn_emergency)
        add(obj.penalty_accel_in_turn_sharp)
        add(obj.penalty_accel_in_turn_emergency)
        add(obj.penalty_brake_in_turn_sharp)
        add(obj.penalty_brake_in_turn_emergency)
        add(obj.penalty_road_low)
        add(obj.penalty_road_high)

        return n
    }
}



enum TripConfigResolver {
    static func resolveForNextTrip() -> TripConfig {
        let ov = TripConfigOverridesStorage.load()
        func pick(_ x: OverrideDouble, _ def: Double) -> Double { x.enabled ? x.value : def }
        func pickA(_ x: OverrideDoubleArray, _ def: [Double]) -> [Double] { x.enabled ? x.value : def }

        let v2 = V2Placeholders(
            speed_gate_accel_brake_ms: pick(ov.speed_gate_accel_brake_ms, 3.0),
            speed_gate_turn_ms: pick(ov.speed_gate_turn_ms, 5.0),
            speed_gate_combined_ms: pick(ov.speed_gate_combined_ms, 5.0),
            cooldown_accel_brake_s: pick(ov.cooldown_accel_brake_s, 1.2),
            cooldown_turn_s: pick(ov.cooldown_turn_s, 0.8),
            cooldown_combined_s: pick(ov.cooldown_combined_s, 1.2),
            cooldown_road_s: pick(ov.cooldown_road_s, 1.2),
            accel_sharp_g: pick(ov.accel_sharp_g, 0.20),
            accel_emergency_g: pick(ov.accel_emergency_g, 0.30),
            brake_sharp_g: pick(ov.brake_sharp_g, 0.18),
            brake_emergency_g: pick(ov.brake_emergency_g, 0.28),
            turn_sharp_lat_g: pick(ov.turn_sharp_lat_g, 0.25),
            turn_emergency_lat_g: pick(ov.turn_emergency_lat_g, 0.35),
            combined_lat_min_g: pick(ov.combined_lat_min_g, 0.35),
            accel_in_turn_sharp_g: pick(ov.accel_in_turn_sharp_g, 0.22),
            accel_in_turn_emergency_g: pick(ov.accel_in_turn_emergency_g, 0.32),
            brake_in_turn_sharp_g: pick(ov.brake_in_turn_sharp_g, 0.22),
            brake_in_turn_emergency_g: pick(ov.brake_in_turn_emergency_g, 0.32),
            road_window_s: pick(ov.road_window_s, 0.40),
            road_low_p2p_g: pick(ov.road_low_p2p_g, 0.70),
            road_high_p2p_g: pick(ov.road_high_p2p_g, 1.10),
            road_low_abs_g: pick(ov.road_low_abs_g, 0.45),
            road_high_abs_g: pick(ov.road_high_abs_g, 0.75)
        )

        let scoring = ScoringConfig(
            double_count_window_s: pick(ov.double_count_window_s, 0.6),
            speed_factor: SpeedFactorConfig(
                breakpoints_ms: pickA(ov.speed_breakpoints_ms, [0.0, 5.0, 13.9, 22.2, 30.6]),
                factors: pickA(ov.speed_factors, [0.25, 0.45, 0.75, 1.05, 1.35])
            ),
            penalty: PenaltyConfig(
                accel: ClassPenalty(sharp: pick(ov.penalty_accel_sharp, 1.0), emergency: pick(ov.penalty_accel_emergency, 2.0)),
                brake: ClassPenalty(sharp: pick(ov.penalty_brake_sharp, 1.5), emergency: pick(ov.penalty_brake_emergency, 3.0)),
                turn: ClassPenalty(sharp: pick(ov.penalty_turn_sharp, 1.2), emergency: pick(ov.penalty_turn_emergency, 2.4)),
                accel_in_turn: ClassPenalty(sharp: pick(ov.penalty_accel_in_turn_sharp, 2.2), emergency: pick(ov.penalty_accel_in_turn_emergency, 4.0)),
                brake_in_turn: ClassPenalty(sharp: pick(ov.penalty_brake_in_turn_sharp, 2.6), emergency: pick(ov.penalty_brake_in_turn_emergency, 4.6)),
                road_anomaly: SeverityPenalty(low: pick(ov.penalty_road_low, 0.3), high: pick(ov.penalty_road_high, 0.8))
            )
        )

        return TripConfig(v2: v2, scoring: scoring)
    }
}
