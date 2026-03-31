import SwiftUI

enum ParamKey: String, Identifiable {
    case speed_gate_accel_brake_ms, speed_gate_turn_ms, speed_gate_combined_ms
    case cooldown_accel_brake_s, cooldown_turn_s, cooldown_combined_s, cooldown_road_s
    case accel_sharp_g, accel_emergency_g, brake_sharp_g, brake_emergency_g
    case turn_sharp_lat_g, turn_emergency_lat_g
    case combined_lat_min_g, accel_in_turn_sharp_g, accel_in_turn_emergency_g, brake_in_turn_sharp_g, brake_in_turn_emergency_g
    case road_window_s, road_low_p2p_g, road_high_p2p_g, road_low_abs_g, road_high_abs_g
    case double_count_window_s, speed_breakpoints_ms, speed_factors
    case penalty_accel_sharp, penalty_accel_emergency, penalty_brake_sharp, penalty_brake_emergency
    case penalty_turn_sharp, penalty_turn_emergency
    case penalty_accel_in_turn_sharp, penalty_accel_in_turn_emergency
    case penalty_brake_in_turn_sharp, penalty_brake_in_turn_emergency
    case penalty_road_low, penalty_road_high
    
    var id: String { rawValue }
}

struct ParamHelp {
    let title: String
    let body: String
    let more: String
    let less: String
}

let PARAM_HELP: [ParamKey: ParamHelp] = [
    // MARK: Speed gates
    .speed_gate_accel_brake_ms: .init(
        title: "Гейт скорости для accel/brake",
        body: "Минимальная скорость (м/с), ниже которой ускорения и торможения не фиксируются.",
        more: "Больше → меньше событий на малых скоростях (пробка/парковка), но можно пропустить резкий манёвр на малой скорости.",
        less: "Меньше → больше событий на малых скоростях, выше шум/ложные срабатывания."
    ),
    .speed_gate_turn_ms: .init(
        title: "Гейт скорости для поворотов",
        body: "Минимальная скорость (м/с), ниже которой события поворота (turn) не фиксируются.",
        more: "Больше → меньше поворотов на малой скорости (двор/парковка), ниже шум, но можно пропустить манёвр на 10–15 км/ч.",
        less: "Меньше → больше поворотов на малой скорости, выше вероятность ложных срабатываний."
    ),
    .speed_gate_combined_ms: .init(
        title: "Гейт скорости для combined (в повороте)",
        body: "Минимальная скорость (м/с), ниже которой комбинированные события accel_in_turn / brake_in_turn не фиксируются.",
        more: "Больше → меньше combined на малых скоростях, ниже шум.",
        less: "Меньше → больше combined на малых скоростях, выше риск шума."
    ),

    // MARK: Cooldowns
    .cooldown_accel_brake_s: .init(
        title: "Cooldown accel/brake",
        body: "Минимальный интервал (сек) между двумя событиями accel или brake.",
        more: "Больше → меньше повторов событий (сильнее анти-спам), но можно недосчитать серию манёвров.",
        less: "Меньше → больше событий при длительном ускорении/торможении, но выше риск дублей."
    ),
    .cooldown_turn_s: .init(
        title: "Cooldown turn",
        body: "Минимальный интервал (сек) между событиями turn.",
        more: "Больше → меньше повторов поворотов на длинных дугах, ниже шум.",
        less: "Меньше → больше событий на одном манёвре (длинный поворот может дать несколько turn)."
    ),
    .cooldown_combined_s: .init(
        title: "Cooldown combined",
        body: "Минимальный интервал (сек) между событиями accel_in_turn / brake_in_turn.",
        more: "Больше → меньше combined событий, сильнее анти-спам.",
        less: "Меньше → чаще будут фиксироваться repeated combined в одном длительном манёвре."
    ),
    .cooldown_road_s: .init(
        title: "Cooldown road_anomaly",
        body: "Минимальный интервал (сек) между событиями road_anomaly.",
        more: "Больше → меньше повторов на серии неровностей (один “кластер” → одно событие).",
        less: "Меньше → больше событий на гребёнке/брусчатке."
    ),

    // MARK: Accel/Brake thresholds
    .accel_sharp_g: .init(
        title: "Порог accel (sharp)",
        body: "Продольное ускорение (g), начиная с которого фиксируется accel класса sharp.",
        more: "Больше → реже accel_sharp, только более агрессивные ускорения.",
        less: "Меньше → чаще accel_sharp, выше чувствительность."
    ),
    .accel_emergency_g: .init(
        title: "Порог accel (emergency)",
        body: "Продольное ускорение (g), начиная с которого фиксируется accel класса emergency.",
        more: "Больше → emergency почти не будет, только экстремальные ускорения.",
        less: "Меньше → emergency будет чаще, жёстче штрафы."
    ),
    .brake_sharp_g: .init(
        title: "Порог brake (sharp)",
        body: "Продольное замедление (g) по модулю, начиная с которого фиксируется brake класса sharp.",
        more: "Больше → реже brake_sharp, меньше событий.",
        less: "Меньше → чаще brake_sharp, выше чувствительность."
    ),
    .brake_emergency_g: .init(
        title: "Порог brake (emergency)",
        body: "Продольное замедление (g) по модулю, начиная с которого фиксируется brake класса emergency.",
        more: "Больше → emergency почти не будет.",
        less: "Меньше → emergency будет чаще, итоговый score падает сильнее."
    ),

    // MARK: Turn thresholds
    .turn_sharp_lat_g: .init(
        title: "Порог turn (sharp)",
        body: "Боковое ускорение |a_lat| (g), начиная с которого фиксируется поворот sharp.",
        more: "Больше → реже turn_sharp.",
        less: "Меньше → чаще turn_sharp."
    ),
    .turn_emergency_lat_g: .init(
        title: "Порог turn (emergency)",
        body: "Боковое ускорение |a_lat| (g), начиная с которого фиксируется поворот emergency.",
        more: "Больше → emergency почти не будет.",
        less: "Меньше → emergency будет чаще, штрафы выше."
    ),

    // MARK: Combined thresholds
    .combined_lat_min_g: .init(
        title: "Минимальный |a_lat| для combined",
        body: "Комбинированные события фиксируются только если |a_lat| ≥ этого порога.",
        more: "Больше → fewer combined (строже), меньше подавления plain событий рядом.",
        less: "Меньше → more combined, чаще будет suppression plain событий."
    ),
    .accel_in_turn_sharp_g: .init(
        title: "Порог accel_in_turn (sharp)",
        body: "Продольное ускорение (g) для accel_in_turn sharp при достаточном боковом ускорении.",
        more: "Больше → реже accel_in_turn_sharp.",
        less: "Меньше → чаще accel_in_turn_sharp."
    ),
    .accel_in_turn_emergency_g: .init(
        title: "Порог accel_in_turn (emergency)",
        body: "Продольное ускорение (g) для accel_in_turn emergency.",
        more: "Больше → emergency почти не будет.",
        less: "Меньше → emergency будет чаще."
    ),
    .brake_in_turn_sharp_g: .init(
        title: "Порог brake_in_turn (sharp)",
        body: "Продольное замедление (g) по модулю для brake_in_turn sharp.",
        more: "Больше → реже brake_in_turn_sharp.",
        less: "Меньше → чаще brake_in_turn_sharp."
    ),
    .brake_in_turn_emergency_g: .init(
        title: "Порог brake_in_turn (emergency)",
        body: "Продольное замедление (g) по модулю для brake_in_turn emergency.",
        more: "Больше → emergency почти не будет.",
        less: "Меньше → emergency будет чаще."
    ),

    // MARK: Road anomaly thresholds
    .road_window_s: .init(
        title: "Окно анализа road_anomaly",
        body: "Длительность окна (сек), по которому считаются peak-to-peak и max_abs по вертикали.",
        more: "Больше → сглаживание, можно склеить несколько ударов в один, ниже чувствительность к коротким ударам.",
        less: "Меньше → выше чувствительность к коротким ударам, но выше шум."
    ),
    .road_low_p2p_g: .init(
        title: "road_anomaly low: порог peak-to-peak",
        body: "Порог p2p по вертикали (g) для road_anomaly severity=low.",
        more: "Больше → реже low события.",
        less: "Меньше → чаще low события."
    ),
    .road_high_p2p_g: .init(
        title: "road_anomaly high: порог peak-to-peak",
        body: "Порог p2p по вертикали (g) для road_anomaly severity=high.",
        more: "Больше → реже high события.",
        less: "Меньше → чаще high события."
    ),
    .road_low_abs_g: .init(
        title: "road_anomaly low: порог max_abs",
        body: "Порог max(|a_vert|) (g) для road_anomaly low.",
        more: "Больше → реже low события.",
        less: "Меньше → чаще low события."
    ),
    .road_high_abs_g: .init(
        title: "road_anomaly high: порог max_abs",
        body: "Порог max(|a_vert|) (g) для road_anomaly high.",
        more: "Больше → реже high события.",
        less: "Меньше → чаще high события."
    ),

    // MARK: Scoring core
    .double_count_window_s: .init(
        title: "Окно подавления double-counting",
        body: "Если рядом есть combined (accel_in_turn/brake_in_turn), то plain accel/brake/turn в окне ±Δt подавляются, чтобы не штрафовать несколько раз.",
        more: "Больше → сильнее подавление plain событий рядом с combined → итоговый штраф меньше.",
        less: "Меньше → чаще будут считаться и combined, и plain вместе → итоговый штраф больше."
    ),
    .speed_breakpoints_ms: .init(
        title: "speed_factor: breakpoints_ms",
        body: "Скоростные точки (м/с), по которым интерполируется коэффициент speedFactor.",
        more: "Больше значения/разнос → можно сделать рост штрафа более резким на высоких скоростях.",
        less: "Меньше значения/сжатие → рост штрафа с скоростью будет мягче."
    ),
    .speed_factors: .init(
        title: "speed_factor: factors",
        body: "Коэффициенты speedFactor для соответствующих breakpoints_ms. Должны быть той же длины.",
        more: "Больше → события на этих скоростях штрафуются сильнее.",
        less: "Меньше → события на этих скоростях штрафуются слабее."
    ),

    // MARK: Penalties
    .penalty_accel_sharp: .init(
        title: "Penalty: accel sharp",
        body: "Базовый штраф за accel класса sharp (до умножения на speedFactor).",
        more: "Больше → accel_sharp сильнее снижает score.",
        less: "Меньше → accel_sharp слабее влияет."
    ),
    .penalty_accel_emergency: .init(
        title: "Penalty: accel emergency",
        body: "Базовый штраф за accel emergency.",
        more: "Больше → emergency ускорения сильнее наказываются.",
        less: "Меньше → emergency ускорения слабее наказываются."
    ),
    .penalty_brake_sharp: .init(
        title: "Penalty: brake sharp",
        body: "Базовый штраф за brake sharp.",
        more: "Больше → резкие торможения сильнее снижают score.",
        less: "Меньше → слабее снижают score."
    ),
    .penalty_brake_emergency: .init(
        title: "Penalty: brake emergency",
        body: "Базовый штраф за brake emergency.",
        more: "Больше → аварийные торможения сильнее наказываются.",
        less: "Меньше → аварийные торможения слабее наказываются."
    ),
    .penalty_turn_sharp: .init(
        title: "Penalty: turn sharp",
        body: "Базовый штраф за turn sharp.",
        more: "Больше → резкие повороты сильнее снижают score.",
        less: "Меньше → слабее снижают score."
    ),
    .penalty_turn_emergency: .init(
        title: "Penalty: turn emergency",
        body: "Базовый штраф за turn emergency.",
        more: "Больше → аварийные повороты сильнее наказываются.",
        less: "Меньше → аварийные повороты слабее наказываются."
    ),
    .penalty_accel_in_turn_sharp: .init(
        title: "Penalty: accel_in_turn sharp",
        body: "Базовый штраф за accel_in_turn sharp.",
        more: "Больше → combined accel в повороте сильнее снижает score.",
        less: "Меньше → слабее влияет."
    ),
    .penalty_accel_in_turn_emergency: .init(
        title: "Penalty: accel_in_turn emergency",
        body: "Базовый штраф за accel_in_turn emergency.",
        more: "Больше → аварийные combined accel сильнее наказываются.",
        less: "Меньше → слабее наказываются."
    ),
    .penalty_brake_in_turn_sharp: .init(
        title: "Penalty: brake_in_turn sharp",
        body: "Базовый штраф за brake_in_turn sharp.",
        more: "Больше → combined brake в повороте сильнее снижает score.",
        less: "Меньше → слабее влияет."
    ),
    .penalty_brake_in_turn_emergency: .init(
        title: "Penalty: brake_in_turn emergency",
        body: "Базовый штраф за brake_in_turn emergency.",
        more: "Больше → аварийные combined brake сильнее наказываются.",
        less: "Меньше → слабее наказываются."
    ),
    .penalty_road_low: .init(
        title: "Penalty: road_anomaly low",
        body: "Базовый штраф за road_anomaly severity=low.",
        more: "Больше → неровности low сильнее снижают score.",
        less: "Меньше → low неровности почти не влияют."
    ),
    .penalty_road_high: .init(
        title: "Penalty: road_anomaly high",
        body: "Базовый штраф за road_anomaly severity=high.",
        more: "Больше → сильные неровности сильнее снижают score.",
        less: "Меньше → high неровности слабее влияют."
    ),
]


struct TripConfigView: View {
    @State private var overrides: TripConfigOverrides = TripConfigOverridesStorage.load()
    
    @State private var helpKey: ParamKey?

    @State private var showSavedAlert = false
    @State private var lastSavedText: String? = TripConfigOverridesStorage.lastSavedLabel()


    var body: some View {
        Form {
            Section("V2: гейты/кулдауны") {
                ParamRowDouble("speed_gate_accel_brake_ms", key: .speed_gate_accel_brake_ms, ov: $overrides.speed_gate_accel_brake_ms, helpKey: $helpKey)
                ParamRowDouble("speed_gate_turn_ms", key: .speed_gate_turn_ms, ov: $overrides.speed_gate_turn_ms, helpKey: $helpKey)
                ParamRowDouble("speed_gate_combined_ms", key: .speed_gate_combined_ms, ov: $overrides.speed_gate_combined_ms, helpKey: $helpKey)

                ParamRowDouble("cooldown_accel_brake_s", key: .cooldown_accel_brake_s, ov: $overrides.cooldown_accel_brake_s, helpKey: $helpKey)
                ParamRowDouble("cooldown_turn_s", key: .cooldown_turn_s, ov: $overrides.cooldown_turn_s, helpKey: $helpKey)
                ParamRowDouble("cooldown_combined_s", key: .cooldown_combined_s, ov: $overrides.cooldown_combined_s, helpKey: $helpKey)
                ParamRowDouble("cooldown_road_s", key: .cooldown_road_s, ov: $overrides.cooldown_road_s, helpKey: $helpKey)
            }

            Section("V2: пороги") {
                ParamRowDouble("accel_sharp_g", key: .accel_sharp_g, ov: $overrides.accel_sharp_g, helpKey: $helpKey)
                ParamRowDouble("accel_emergency_g", key: .accel_emergency_g, ov: $overrides.accel_emergency_g, helpKey: $helpKey)
                ParamRowDouble("brake_sharp_g", key: .brake_sharp_g, ov: $overrides.brake_sharp_g, helpKey: $helpKey)
                ParamRowDouble("brake_emergency_g", key: .brake_emergency_g, ov: $overrides.brake_emergency_g, helpKey: $helpKey)

                ParamRowDouble("turn_sharp_lat_g", key: .turn_sharp_lat_g, ov: $overrides.turn_sharp_lat_g, helpKey: $helpKey)
                ParamRowDouble("turn_emergency_lat_g", key: .turn_emergency_lat_g, ov: $overrides.turn_emergency_lat_g, helpKey: $helpKey)
            }

            Section("Scoring") {
                ParamRowDouble("double_count_window_s", key: .double_count_window_s, ov: $overrides.double_count_window_s, helpKey: $helpKey)
                ParamRowArray("speed_factor.breakpoints_ms", key: .speed_breakpoints_ms, ov: $overrides.speed_breakpoints_ms, helpKey: $helpKey)
                ParamRowArray("speed_factor.factors", key: .speed_factors, ov: $overrides.speed_factors, helpKey: $helpKey)
            }

            Section("Scoring: penalty") {
                ParamRowDouble("penalty.accel.sharp", key: .penalty_accel_sharp, ov: $overrides.penalty_accel_sharp, helpKey: $helpKey)
                ParamRowDouble("penalty.accel.emergency", key: .penalty_accel_emergency, ov: $overrides.penalty_accel_emergency, helpKey: $helpKey)
                ParamRowDouble("penalty.brake.sharp", key: .penalty_brake_sharp, ov: $overrides.penalty_brake_sharp, helpKey: $helpKey)
                ParamRowDouble("penalty.brake.emergency", key: .penalty_brake_emergency, ov: $overrides.penalty_brake_emergency, helpKey: $helpKey)
                ParamRowDouble("penalty.turn.sharp", key: .penalty_turn_sharp, ov: $overrides.penalty_turn_sharp, helpKey: $helpKey)
                ParamRowDouble("penalty.turn.emergency", key: .penalty_turn_emergency, ov: $overrides.penalty_turn_emergency, helpKey: $helpKey)
                ParamRowDouble("penalty.accel_in_turn.sharp", key: .penalty_accel_in_turn_sharp, ov: $overrides.penalty_accel_in_turn_sharp, helpKey: $helpKey)
                ParamRowDouble("penalty.accel_in_turn.emergency", key: .penalty_accel_in_turn_emergency, ov: $overrides.penalty_accel_in_turn_emergency, helpKey: $helpKey)
                ParamRowDouble("penalty.brake_in_turn.sharp", key: .penalty_brake_in_turn_sharp, ov: $overrides.penalty_brake_in_turn_sharp, helpKey: $helpKey)
                ParamRowDouble("penalty.brake_in_turn.emergency", key: .penalty_brake_in_turn_emergency, ov: $overrides.penalty_brake_in_turn_emergency, helpKey: $helpKey)
                ParamRowDouble("penalty.road.low", key: .penalty_road_low, ov: $overrides.penalty_road_low, helpKey: $helpKey)
                ParamRowDouble("penalty.road.high", key: .penalty_road_high, ov: $overrides.penalty_road_high, helpKey: $helpKey)
            }

            Section {
                Button("Сбросить (override = Нет)") {
                    overrides = TripConfigOverrides.default()
                    TripConfigOverridesStorage.save(overrides)
                }
                Button("Сохранить (для следующей поездки)") {
                    TripConfigOverridesStorage.save(overrides)
                    TripConfigOverridesStorage.markSavedNow(overrides)
                    lastSavedText = TripConfigOverridesStorage.lastSavedLabel()
                    showSavedAlert = true
                }

            }
            Section("Статус") {
                if let lastSavedText {
                    Text(lastSavedText).foregroundColor(.secondary)
                } else {
                    Text("Ещё не сохранялось").foregroundColor(.secondary)
                }
            }

        }
        .navigationTitle("Пороги/скоринг")
        .sheet(item: $helpKey) { k in
            HelpSheet(key: k)
        }
        .alert("Сохранено", isPresented: $showSavedAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(lastSavedText ?? "Настройки сохранены для следующей поездки.")
        }
        

    }
}

struct ParamRowDouble: View {
    let title: String
    let key: ParamKey
    @Binding var ov: OverrideDouble
    @Binding var helpKey: ParamKey?

    init(_ title: String,
         key: ParamKey,
         ov: Binding<OverrideDouble>,
         helpKey: Binding<ParamKey?>) {
        self.title = title
        self.key = key
        self._ov = ov
        self._helpKey = helpKey
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(title).font(.subheadline)
                Spacer()
                Button {
                    helpKey = key
                } label: {
                    Image(systemName: "info.circle")
                        .frame(width: 28, height: 28)
                }
                .buttonStyle(.borderless)
                .contentShape(Rectangle())

                Toggle("Вручную", isOn: $ov.enabled).labelsHidden()
            }
            if ov.enabled {
                TextField("Value", value: $ov.value, format: .number)
                    .keyboardType(.decimalPad)
            } else {
                Text("По умолчанию").foregroundColor(.secondary).font(.footnote)
            }
        }
        .padding(.vertical, 4)
    }
}


struct ParamRowArray: View {
    let title: String
    let key: ParamKey
    @Binding var ov: OverrideDoubleArray
    @Binding var helpKey: ParamKey?

    @State private var csv: String

    init(_ title: String, key: ParamKey, ov: Binding<OverrideDoubleArray>, helpKey: Binding<ParamKey?>) {
        self.title = title
        self.key = key
        self._ov = ov
        self._helpKey = helpKey
        self._csv = State(initialValue: ov.wrappedValue.value.map { String($0) }.joined(separator: ", "))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(title).font(.subheadline)
                Spacer()
                Button {
                    helpKey = key
                } label: {
                    Image(systemName: "info.circle")
                        .frame(width: 28, height: 28)
                }
                .buttonStyle(.borderless)
                .contentShape(Rectangle())

                Toggle("Вручную", isOn: $ov.enabled).labelsHidden()
            }
            if ov.enabled {
                TextField("CSV: 0.0, 5.0, 13.9", text: $csv)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
                    .onChange(of: csv) { nv in
                        let parts = nv.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }
                        let nums = parts.compactMap { Double($0) }
                        if !nums.isEmpty { ov.value = nums }
                    }
            } else {
                Text("По умолчанию").foregroundColor(.secondary).font(.footnote)
            }
        }
        .padding(.vertical, 4)
    }
}

struct HelpSheet: View {
    let key: ParamKey?

    var body: some View {
        NavigationView {
            VStack(alignment: .leading, spacing: 12) {
                if let key, let h = PARAM_HELP[key] {
                    Text(h.title).font(.headline)
                    Text(h.body)
                    Divider()
                    Text("Если больше:").font(.subheadline).bold()
                    Text(h.more)
                    Divider()
                    Text("Если меньше:").font(.subheadline).bold()
                    Text(h.less)
                } else {
                    Text("Описание для этого параметра пока не добавлено.")
                }
                Spacer()
            }
            .padding()
            .navigationTitle("Коммент")
        }
    }
}
