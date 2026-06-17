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
    let titleKey: LocalizationKey
    let bodyKey: LocalizationKey
    let moreKey: LocalizationKey
    let lessKey: LocalizationKey

    var title: String { LocalizationCatalog.text(titleKey) }
    var body: String { LocalizationCatalog.text(bodyKey) }
    var more: String { LocalizationCatalog.text(moreKey) }
    var less: String { LocalizationCatalog.text(lessKey) }
}

let PARAM_HELP: [ParamKey: ParamHelp] = [
    .speed_gate_accel_brake_ms: .init(
        titleKey: .tripConfigHelpSpeedGateAccelBrakeMsTitle,
        bodyKey: .tripConfigHelpSpeedGateAccelBrakeMsBody,
        moreKey: .tripConfigHelpSpeedGateAccelBrakeMsMore,
        lessKey: .tripConfigHelpSpeedGateAccelBrakeMsLess
    ),
    .speed_gate_turn_ms: .init(
        titleKey: .tripConfigHelpSpeedGateTurnMsTitle,
        bodyKey: .tripConfigHelpSpeedGateTurnMsBody,
        moreKey: .tripConfigHelpSpeedGateTurnMsMore,
        lessKey: .tripConfigHelpSpeedGateTurnMsLess
    ),
    .speed_gate_combined_ms: .init(
        titleKey: .tripConfigHelpSpeedGateCombinedMsTitle,
        bodyKey: .tripConfigHelpSpeedGateCombinedMsBody,
        moreKey: .tripConfigHelpSpeedGateCombinedMsMore,
        lessKey: .tripConfigHelpSpeedGateCombinedMsLess
    ),
    .cooldown_accel_brake_s: .init(
        titleKey: .tripConfigHelpCooldownAccelBrakeSTitle,
        bodyKey: .tripConfigHelpCooldownAccelBrakeSBody,
        moreKey: .tripConfigHelpCooldownAccelBrakeSMore,
        lessKey: .tripConfigHelpCooldownAccelBrakeSLess
    ),
    .cooldown_turn_s: .init(
        titleKey: .tripConfigHelpCooldownTurnSTitle,
        bodyKey: .tripConfigHelpCooldownTurnSBody,
        moreKey: .tripConfigHelpCooldownTurnSMore,
        lessKey: .tripConfigHelpCooldownTurnSLess
    ),
    .cooldown_combined_s: .init(
        titleKey: .tripConfigHelpCooldownCombinedSTitle,
        bodyKey: .tripConfigHelpCooldownCombinedSBody,
        moreKey: .tripConfigHelpCooldownCombinedSMore,
        lessKey: .tripConfigHelpCooldownCombinedSLess
    ),
    .cooldown_road_s: .init(
        titleKey: .tripConfigHelpCooldownRoadSTitle,
        bodyKey: .tripConfigHelpCooldownRoadSBody,
        moreKey: .tripConfigHelpCooldownRoadSMore,
        lessKey: .tripConfigHelpCooldownRoadSLess
    ),
    .accel_sharp_g: .init(
        titleKey: .tripConfigHelpAccelSharpGTitle,
        bodyKey: .tripConfigHelpAccelSharpGBody,
        moreKey: .tripConfigHelpAccelSharpGMore,
        lessKey: .tripConfigHelpAccelSharpGLess
    ),
    .accel_emergency_g: .init(
        titleKey: .tripConfigHelpAccelEmergencyGTitle,
        bodyKey: .tripConfigHelpAccelEmergencyGBody,
        moreKey: .tripConfigHelpAccelEmergencyGMore,
        lessKey: .tripConfigHelpAccelEmergencyGLess
    ),
    .brake_sharp_g: .init(
        titleKey: .tripConfigHelpBrakeSharpGTitle,
        bodyKey: .tripConfigHelpBrakeSharpGBody,
        moreKey: .tripConfigHelpBrakeSharpGMore,
        lessKey: .tripConfigHelpBrakeSharpGLess
    ),
    .brake_emergency_g: .init(
        titleKey: .tripConfigHelpBrakeEmergencyGTitle,
        bodyKey: .tripConfigHelpBrakeEmergencyGBody,
        moreKey: .tripConfigHelpBrakeEmergencyGMore,
        lessKey: .tripConfigHelpBrakeEmergencyGLess
    ),
    .turn_sharp_lat_g: .init(
        titleKey: .tripConfigHelpTurnSharpLatGTitle,
        bodyKey: .tripConfigHelpTurnSharpLatGBody,
        moreKey: .tripConfigHelpTurnSharpLatGMore,
        lessKey: .tripConfigHelpTurnSharpLatGLess
    ),
    .turn_emergency_lat_g: .init(
        titleKey: .tripConfigHelpTurnEmergencyLatGTitle,
        bodyKey: .tripConfigHelpTurnEmergencyLatGBody,
        moreKey: .tripConfigHelpTurnEmergencyLatGMore,
        lessKey: .tripConfigHelpTurnEmergencyLatGLess
    ),
    .combined_lat_min_g: .init(
        titleKey: .tripConfigHelpCombinedLatMinGTitle,
        bodyKey: .tripConfigHelpCombinedLatMinGBody,
        moreKey: .tripConfigHelpCombinedLatMinGMore,
        lessKey: .tripConfigHelpCombinedLatMinGLess
    ),
    .accel_in_turn_sharp_g: .init(
        titleKey: .tripConfigHelpAccelInTurnSharpGTitle,
        bodyKey: .tripConfigHelpAccelInTurnSharpGBody,
        moreKey: .tripConfigHelpAccelInTurnSharpGMore,
        lessKey: .tripConfigHelpAccelInTurnSharpGLess
    ),
    .accel_in_turn_emergency_g: .init(
        titleKey: .tripConfigHelpAccelInTurnEmergencyGTitle,
        bodyKey: .tripConfigHelpAccelInTurnEmergencyGBody,
        moreKey: .tripConfigHelpAccelInTurnEmergencyGMore,
        lessKey: .tripConfigHelpAccelInTurnEmergencyGLess
    ),
    .brake_in_turn_sharp_g: .init(
        titleKey: .tripConfigHelpBrakeInTurnSharpGTitle,
        bodyKey: .tripConfigHelpBrakeInTurnSharpGBody,
        moreKey: .tripConfigHelpBrakeInTurnSharpGMore,
        lessKey: .tripConfigHelpBrakeInTurnSharpGLess
    ),
    .brake_in_turn_emergency_g: .init(
        titleKey: .tripConfigHelpBrakeInTurnEmergencyGTitle,
        bodyKey: .tripConfigHelpBrakeInTurnEmergencyGBody,
        moreKey: .tripConfigHelpBrakeInTurnEmergencyGMore,
        lessKey: .tripConfigHelpBrakeInTurnEmergencyGLess
    ),
    .road_window_s: .init(
        titleKey: .tripConfigHelpRoadWindowSTitle,
        bodyKey: .tripConfigHelpRoadWindowSBody,
        moreKey: .tripConfigHelpRoadWindowSMore,
        lessKey: .tripConfigHelpRoadWindowSLess
    ),
    .road_low_p2p_g: .init(
        titleKey: .tripConfigHelpRoadLowP2pGTitle,
        bodyKey: .tripConfigHelpRoadLowP2pGBody,
        moreKey: .tripConfigHelpRoadLowP2pGMore,
        lessKey: .tripConfigHelpRoadLowP2pGLess
    ),
    .road_high_p2p_g: .init(
        titleKey: .tripConfigHelpRoadHighP2pGTitle,
        bodyKey: .tripConfigHelpRoadHighP2pGBody,
        moreKey: .tripConfigHelpRoadHighP2pGMore,
        lessKey: .tripConfigHelpRoadHighP2pGLess
    ),
    .road_low_abs_g: .init(
        titleKey: .tripConfigHelpRoadLowAbsGTitle,
        bodyKey: .tripConfigHelpRoadLowAbsGBody,
        moreKey: .tripConfigHelpRoadLowAbsGMore,
        lessKey: .tripConfigHelpRoadLowAbsGLess
    ),
    .road_high_abs_g: .init(
        titleKey: .tripConfigHelpRoadHighAbsGTitle,
        bodyKey: .tripConfigHelpRoadHighAbsGBody,
        moreKey: .tripConfigHelpRoadHighAbsGMore,
        lessKey: .tripConfigHelpRoadHighAbsGLess
    ),
    .double_count_window_s: .init(
        titleKey: .tripConfigHelpDoubleCountWindowSTitle,
        bodyKey: .tripConfigHelpDoubleCountWindowSBody,
        moreKey: .tripConfigHelpDoubleCountWindowSMore,
        lessKey: .tripConfigHelpDoubleCountWindowSLess
    ),
    .speed_breakpoints_ms: .init(
        titleKey: .tripConfigHelpSpeedBreakpointsMsTitle,
        bodyKey: .tripConfigHelpSpeedBreakpointsMsBody,
        moreKey: .tripConfigHelpSpeedBreakpointsMsMore,
        lessKey: .tripConfigHelpSpeedBreakpointsMsLess
    ),
    .speed_factors: .init(
        titleKey: .tripConfigHelpSpeedFactorsTitle,
        bodyKey: .tripConfigHelpSpeedFactorsBody,
        moreKey: .tripConfigHelpSpeedFactorsMore,
        lessKey: .tripConfigHelpSpeedFactorsLess
    ),
    .penalty_accel_sharp: .init(
        titleKey: .tripConfigHelpPenaltyAccelSharpTitle,
        bodyKey: .tripConfigHelpPenaltyAccelSharpBody,
        moreKey: .tripConfigHelpPenaltyAccelSharpMore,
        lessKey: .tripConfigHelpPenaltyAccelSharpLess
    ),
    .penalty_accel_emergency: .init(
        titleKey: .tripConfigHelpPenaltyAccelEmergencyTitle,
        bodyKey: .tripConfigHelpPenaltyAccelEmergencyBody,
        moreKey: .tripConfigHelpPenaltyAccelEmergencyMore,
        lessKey: .tripConfigHelpPenaltyAccelEmergencyLess
    ),
    .penalty_brake_sharp: .init(
        titleKey: .tripConfigHelpPenaltyBrakeSharpTitle,
        bodyKey: .tripConfigHelpPenaltyBrakeSharpBody,
        moreKey: .tripConfigHelpPenaltyBrakeSharpMore,
        lessKey: .tripConfigHelpPenaltyBrakeSharpLess
    ),
    .penalty_brake_emergency: .init(
        titleKey: .tripConfigHelpPenaltyBrakeEmergencyTitle,
        bodyKey: .tripConfigHelpPenaltyBrakeEmergencyBody,
        moreKey: .tripConfigHelpPenaltyBrakeEmergencyMore,
        lessKey: .tripConfigHelpPenaltyBrakeEmergencyLess
    ),
    .penalty_turn_sharp: .init(
        titleKey: .tripConfigHelpPenaltyTurnSharpTitle,
        bodyKey: .tripConfigHelpPenaltyTurnSharpBody,
        moreKey: .tripConfigHelpPenaltyTurnSharpMore,
        lessKey: .tripConfigHelpPenaltyTurnSharpLess
    ),
    .penalty_turn_emergency: .init(
        titleKey: .tripConfigHelpPenaltyTurnEmergencyTitle,
        bodyKey: .tripConfigHelpPenaltyTurnEmergencyBody,
        moreKey: .tripConfigHelpPenaltyTurnEmergencyMore,
        lessKey: .tripConfigHelpPenaltyTurnEmergencyLess
    ),
    .penalty_accel_in_turn_sharp: .init(
        titleKey: .tripConfigHelpPenaltyAccelInTurnSharpTitle,
        bodyKey: .tripConfigHelpPenaltyAccelInTurnSharpBody,
        moreKey: .tripConfigHelpPenaltyAccelInTurnSharpMore,
        lessKey: .tripConfigHelpPenaltyAccelInTurnSharpLess
    ),
    .penalty_accel_in_turn_emergency: .init(
        titleKey: .tripConfigHelpPenaltyAccelInTurnEmergencyTitle,
        bodyKey: .tripConfigHelpPenaltyAccelInTurnEmergencyBody,
        moreKey: .tripConfigHelpPenaltyAccelInTurnEmergencyMore,
        lessKey: .tripConfigHelpPenaltyAccelInTurnEmergencyLess
    ),
    .penalty_brake_in_turn_sharp: .init(
        titleKey: .tripConfigHelpPenaltyBrakeInTurnSharpTitle,
        bodyKey: .tripConfigHelpPenaltyBrakeInTurnSharpBody,
        moreKey: .tripConfigHelpPenaltyBrakeInTurnSharpMore,
        lessKey: .tripConfigHelpPenaltyBrakeInTurnSharpLess
    ),
    .penalty_brake_in_turn_emergency: .init(
        titleKey: .tripConfigHelpPenaltyBrakeInTurnEmergencyTitle,
        bodyKey: .tripConfigHelpPenaltyBrakeInTurnEmergencyBody,
        moreKey: .tripConfigHelpPenaltyBrakeInTurnEmergencyMore,
        lessKey: .tripConfigHelpPenaltyBrakeInTurnEmergencyLess
    ),
    .penalty_road_low: .init(
        titleKey: .tripConfigHelpPenaltyRoadLowTitle,
        bodyKey: .tripConfigHelpPenaltyRoadLowBody,
        moreKey: .tripConfigHelpPenaltyRoadLowMore,
        lessKey: .tripConfigHelpPenaltyRoadLowLess
    ),
    .penalty_road_high: .init(
        titleKey: .tripConfigHelpPenaltyRoadHighTitle,
        bodyKey: .tripConfigHelpPenaltyRoadHighBody,
        moreKey: .tripConfigHelpPenaltyRoadHighMore,
        lessKey: .tripConfigHelpPenaltyRoadHighLess
    ),
]


struct TripConfigView: View {
    @EnvironmentObject private var languageManager: LanguageManager

    private func t(_ key: LocalizationKey) -> String {
        languageManager.text(key)
    }

    @State private var overrides: TripConfigOverrides = TripConfigOverridesStorage.load()
    
    @State private var helpKey: ParamKey?

    @State private var showSavedAlert = false
    @State private var lastSavedText: String? = TripConfigOverridesStorage.lastSavedLabel()


    var body: some View {
        Form {
            Section(t(.tripConfigSectionGatesCooldowns)) {
                ParamRowDouble("speed_gate_accel_brake_ms", key: .speed_gate_accel_brake_ms, ov: $overrides.speed_gate_accel_brake_ms, helpKey: $helpKey)
                ParamRowDouble("speed_gate_turn_ms", key: .speed_gate_turn_ms, ov: $overrides.speed_gate_turn_ms, helpKey: $helpKey)
                ParamRowDouble("speed_gate_combined_ms", key: .speed_gate_combined_ms, ov: $overrides.speed_gate_combined_ms, helpKey: $helpKey)

                ParamRowDouble("cooldown_accel_brake_s", key: .cooldown_accel_brake_s, ov: $overrides.cooldown_accel_brake_s, helpKey: $helpKey)
                ParamRowDouble("cooldown_turn_s", key: .cooldown_turn_s, ov: $overrides.cooldown_turn_s, helpKey: $helpKey)
                ParamRowDouble("cooldown_combined_s", key: .cooldown_combined_s, ov: $overrides.cooldown_combined_s, helpKey: $helpKey)
                ParamRowDouble("cooldown_road_s", key: .cooldown_road_s, ov: $overrides.cooldown_road_s, helpKey: $helpKey)
            }

            Section(t(.tripConfigSectionThresholds)) {
                ParamRowDouble("accel_sharp_g", key: .accel_sharp_g, ov: $overrides.accel_sharp_g, helpKey: $helpKey)
                ParamRowDouble("accel_emergency_g", key: .accel_emergency_g, ov: $overrides.accel_emergency_g, helpKey: $helpKey)
                ParamRowDouble("brake_sharp_g", key: .brake_sharp_g, ov: $overrides.brake_sharp_g, helpKey: $helpKey)
                ParamRowDouble("brake_emergency_g", key: .brake_emergency_g, ov: $overrides.brake_emergency_g, helpKey: $helpKey)

                ParamRowDouble("turn_sharp_lat_g", key: .turn_sharp_lat_g, ov: $overrides.turn_sharp_lat_g, helpKey: $helpKey)
                ParamRowDouble("turn_emergency_lat_g", key: .turn_emergency_lat_g, ov: $overrides.turn_emergency_lat_g, helpKey: $helpKey)
            }

            Section(t(.tripConfigSectionScoring)) {
                ParamRowDouble("double_count_window_s", key: .double_count_window_s, ov: $overrides.double_count_window_s, helpKey: $helpKey)
                ParamRowArray("speed_factor.breakpoints_ms", key: .speed_breakpoints_ms, ov: $overrides.speed_breakpoints_ms, helpKey: $helpKey)
                ParamRowArray("speed_factor.factors", key: .speed_factors, ov: $overrides.speed_factors, helpKey: $helpKey)
            }

            Section(t(.tripConfigSectionScoringPenalty)) {
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
                Button(t(.tripConfigResetOverrides)) {
                    overrides = TripConfigOverrides.default()
                    TripConfigOverridesStorage.save(overrides)
                }
                Button(t(.tripConfigSaveForNextTrip)) {
                    TripConfigOverridesStorage.save(overrides)
                    TripConfigOverridesStorage.markSavedNow(overrides)
                    lastSavedText = TripConfigOverridesStorage.lastSavedLabel()
                    showSavedAlert = true
                }

            }
            Section(t(.tripConfigStatusSection)) {
                if let lastSavedText {
                    Text(lastSavedText).foregroundColor(.secondary)
                } else {
                    Text(t(.tripConfigNeverSaved)).foregroundColor(.secondary)
                }
            }

        }
        .navigationTitle(t(.tripConfigNavigationTitle))
        .sheet(item: $helpKey) { k in
            HelpSheet(key: k)
        }
        .alert(t(.tripConfigSavedAlertTitle), isPresented: $showSavedAlert) {
            Button(t(.ok), role: .cancel) {}
        } message: {
            Text(lastSavedText ?? t(.tripConfigSavedForNextTrip))
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

                Toggle(LocalizationCatalog.text(.tripConfigManualOverride), isOn: $ov.enabled).labelsHidden()
            }
            if ov.enabled {
                TextField(LocalizationCatalog.text(.tripConfigValuePlaceholder), value: $ov.value, format: .number)
                    .keyboardType(.decimalPad)
            } else {
                Text(LocalizationCatalog.text(.tripConfigDefaultValue)).foregroundColor(.secondary).font(.footnote)
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

                Toggle(LocalizationCatalog.text(.tripConfigManualOverride), isOn: $ov.enabled).labelsHidden()
            }
            if ov.enabled {
                TextField(LocalizationCatalog.text(.tripConfigCsvPlaceholder), text: $csv)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
                    .onChange(of: csv) { nv in
                        let parts = nv.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }
                        let nums = parts.compactMap { Double($0) }
                        if !nums.isEmpty { ov.value = nums }
                    }
            } else {
                Text(LocalizationCatalog.text(.tripConfigDefaultValue)).foregroundColor(.secondary).font(.footnote)
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
                    Text(LocalizationCatalog.text(.tripConfigIfHigher)).font(.subheadline).bold()
                    Text(h.more)
                    Divider()
                    Text(LocalizationCatalog.text(.tripConfigIfLower)).font(.subheadline).bold()
                    Text(h.less)
                } else {
                    Text(LocalizationCatalog.text(.tripConfigMissingHelp))
                }
                Spacer()
            }
            .padding()
            .navigationTitle(LocalizationCatalog.text(.tripConfigHelpTitle))
        }
    }
}
