//
//  TripReportView.swift
//  TelemetryApp
//
//  Стартовый экран — белая карточка, оранжевый акцент и иконки как на скриншоте
//  + кнопка «Подробно» (sheet) с полным отчётом (все поля).
//

import SwiftUI
import Foundation

struct TripReportRefreshPayload {
    let report: TripReport
    let createdBatches: Int
    let deliveredBatches: Int
}

struct TripReportView: View {
    @State private var currentReport: TripReport
    @State private var currentCreatedBatches: Int
    @State private var currentDeliveredBatches: Int

    let wasAutoFinish: Bool
    let refreshPayload: (() async throws -> TripReportRefreshPayload?)?

    init(
        report: TripReport,
        createdBatches: Int,
        deliveredBatches: Int,
        wasAutoFinish: Bool = false,
        refreshPayload: (() async throws -> TripReportRefreshPayload?)? = nil
    ) {
        _currentReport = State(initialValue: report)
        _currentCreatedBatches = State(initialValue: createdBatches)
        _currentDeliveredBatches = State(initialValue: deliveredBatches)
        self.wasAutoFinish = wasAutoFinish
        self.refreshPayload = refreshPayload
    }

    private var coverage: Double {
        guard currentCreatedBatches > 0 else { return 0 }
        return min(1.0, Double(currentDeliveredBatches) / Double(currentCreatedBatches))
    }

    private var pendingBatches: Int {
        max(0, currentCreatedBatches - currentDeliveredBatches)
    }

    private var serverMissingBatches: Int {
        max(0, currentReport.batches_missing_count ?? 0)
    }

    private var isPartial: Bool {
        pendingBatches > 0 || serverMissingBatches > 0
    }

    private var shouldKeepRefreshing: Bool {
        pendingBatches > 0 || serverMissingBatches > 0
    }

    private var bannerTitle: String {
        isPartial ? t(.preliminaryReport) : t(.batchData)
    }

    private var bannerSubtitle: String {
        var parts: [String] = [
            "\(t(.sent)): \(currentDeliveredBatches)",
            "\(t(.notSent)): \(pendingBatches)",
            "\(t(.total)): \(currentCreatedBatches)",
            "\(Int(coverage * 100))%"
        ]

        if serverMissingBatches > 0 {
            parts.append("\(t(.serverProcessing)): \(serverMissingBatches)")
        }

        return parts.joined(separator: " • ")
    }

    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var languageManager: LanguageManager
    private func t(_ key: LocalizationKey) -> String {
        languageManager.text(key)
    }
    @State private var showDetails = false
    @State private var refreshIteration = 0

    private let accent = Color(red: 0.98, green: 0.55, blue: 0.10) // ~ #FA8C1A

    var body: some View {
        ZStack {
            Color(white: 0.95).ignoresSafeArea()

            ScrollView {
                VStack(spacing: 12) {
                    if wasAutoFinish {
                        Text(t(.autoFinishLabel))
                            .font(.footnote.weight(.semibold))
                            .foregroundColor(.secondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.bottom, 4)
                    }

                    if FeatureFlags.isDeveloperBuild {
                        if currentCreatedBatches > 0 {
                            VStack(alignment: .leading, spacing: 8) {
                                Text(bannerTitle)
                                    .font(.headline)
                                    .foregroundColor(accent)

                                Text(bannerSubtitle)
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)

                                ProgressView(value: coverage)
                                    .tint(accent)
                            }
                            .padding()
                            .background(Color(.secondarySystemBackground))
                            .cornerRadius(12)
                        }
                    }

                    cardContent
                }
                .padding(.horizontal, 14)
                .padding(.top, 14)
                .padding(.bottom, 20)
                .foregroundColor(.black)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button(t(.close)) { dismiss() }
            }
        }
        .sheet(isPresented: $showDetails) {
            TripReportDetailsView(report: currentReport, accent: accent)
        }
        .task(id: shouldKeepRefreshing) {
            await autoRefreshLoopIfNeeded()
        }
    }
    
    private var riskyManeuversCount: Int {
        currentReport.accel_sharp_total + currentReport.accel_emergency_total
      + currentReport.brake_sharp_total + currentReport.brake_emergency_total
      + currentReport.turn_sharp_total + currentReport.turn_emergency_total
    }

    private var skidRiskCount: Int {
        currentReport.accel_in_turn_total + currentReport.brake_in_turn_total
    }

    private var roadAnomalyCount: Int {
        currentReport.road_anomaly_low_total + currentReport.road_anomaly_high_total
    }



    // MARK: - Card

    private var cardContent: some View {
        VStack(spacing: 18) {

            // SCORE
            // Public Alpha additive fields
            VStack(spacing: 10) {
                Text(t(.driverScoreOneTrip))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if isPartial {
                    HStack(spacing: 6) {
                        ProgressView()
                            .controlSize(.small)
                        Text("Ждём данные с сервера")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.secondary)
                    }
                }
                

                Text("\(Int(round(primaryScore))) / 100")
                    .font(.system(size: 54, weight: .bold, design: .rounded))
                    .foregroundStyle(accent)
                    .monospacedDigit()
                    .padding(.top, 8)
                    .frame(maxWidth: .infinity, alignment: .center)   
                    .multilineTextAlignment(.center)
                

                // Public Alpha additive fields
                if let mode = currentReport.driving_mode, !mode.isEmpty {
                    Text(drivingModeText)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .padding(.top, -4)
                }

                ProgressView(value: max(0, min(1, primaryScore / 100.0)))
                    .tint(accent)
                    .progressViewStyle(.linear)
                    .scaleEffect(x: 1, y: 2.2, anchor: .center)
                    .padding(.horizontal, 10)

                HStack {
                    HStack(spacing: 10) {
                        Image(systemName: "car.fill")
                            .foregroundStyle(accent)
                        Text(verdictText(for: primaryScore))
                            .font(.system(size: 26, weight: .bold))
                            .foregroundStyle(accent)
                    }

                    Spacer()

                    Text(verdictRightHint(for: primaryScore))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .padding(.horizontal, 6)
                .padding(.bottom, 4)
            }
            
            Divider()

            // SUMMARY (3 rows)
            VStack(spacing: 14) {
                IconValueRow(
                    icon: "stopwatch.fill",
                    iconColor: .secondary,
                    title: t(.tripDuration),
                    value: durationText,
                    valueEmphasis: true
                    
                )

                IconValueRow(
                    icon: "road.lanes",
                    iconColor: .secondary,
                    title: t(.distance),
                    value: distanceText,
                    valueEmphasis: true
                )

                IconValueRow(
                    icon: "speedometer",
                    iconColor: .secondary,
                    title: t(.averageSpeed),
                    value: avgSpeedText,
                    valueEmphasis: true
                )
                
                // Public Alpha additive fields
                IconValueRow(
                    icon: "car.rear.and.tire.marks",
                    iconColor: .secondary,
                    title: t(.drivingMode),
                    value: drivingModeText,
                    valueEmphasis: true
                )
            }

            Divider()

            // Public Alpha additive fields
            VStack(spacing: 14) {
                IconValueRow(
                    icon: "gauge.medium",
                    iconColor: .secondary,
                    title: t(.drivingLoad),
                    value: drivingLoadText,
                    valueEmphasis: true
                )
            }

            Divider()

            // RANKING (center header + 2 rows)
            VStack(spacing: 14) {
                if let s = rankLineText {
                    Text(s)
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(.primary)
                }

                VStack(spacing: 12) {
                    IconValueRow(
                        icon: "car.fill",
                        iconColor: accent,
                        title: t(.yourAverageRating),
                        value: avgScoreText,
                        valueEmphasis: true
                    )

                    IconValueRow(
                        icon: "mappin.circle.fill",
                        iconColor: accent,
                        title: t(.countedTripsYouHave),
                        value: driverTripsText,
                        valueEmphasis: true
                    )
                }
            }

            Divider()

            // COMPARISON
            VStack(alignment: .leading, spacing: 14) {
                if let s = betterThanPrevLine {
                    ComparisonLine(icon: "flag.checkered", iconColor: .primary, text: s, bold: false)
                }
                if let s = betterThanAllLine {
                    ComparisonLine(icon: "globe.europe.africa", iconColor: .primary, text: s, bold: true)
                }
                if let s = allTripsLine {
                    ComparisonLine(icon: "chart.bar.xaxis", iconColor: .secondary, text: s, bold: false, secondary: true)
                }

                if betterThanPrevLine == nil && betterThanAllLine == nil && allTripsLine == nil {
                    Text(t(.comparisonUnavailable))
                        .foregroundStyle(.secondary)
                }
            }

            Divider()
            
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text(t(.eventSummary))
                        .font(.headline)
                    Spacer()
                    V2HelpButton()
                }

                LazyVGrid(
                    columns: [
                        GridItem(.flexible(), spacing: 12),
                        GridItem(.flexible(), spacing: 12),
                        GridItem(.flexible(), spacing: 12)
                    ],
                    alignment: .leading,
                    spacing: 12
                ) {
                    SummaryPill(
                        title: t(.dangerousManeuvers),
                        value: riskyManeuversCount,
                        systemIcon: "exclamationmark.triangle.fill"
                    )

                    SummaryPill(
                        title: t(.skidRisk),
                        value: skidRiskCount,
                        systemIcon: "snowflake"
                    )

                    SummaryPill(
                        title: t(.roadAnomalies),
                        value: roadAnomalyCount,
                        systemIcon: "exclamationmark.circle.fill"
                    )
                }
            }


            // EVENTS (like screenshot: warning / stop / uturn)
            // EVENTS (V2)
            DisclosureGroup(t(.detailsV2)) {
                VStack(spacing: 10) {
                    EventLine(
                        icon: "exclamationmark.triangle.fill",
                        iconColor: .yellow,
                        text: "\(t(.accelerationSharp)): \(currentReport.accel_sharp_total)"
                    )
                    EventLine(
                        icon: "exclamationmark.triangle.fill",
                        iconColor: .orange,
                        text: "\(t(.accelerationEmergency)): \(currentReport.accel_emergency_total)"
                    )

                    EventLine(
                        icon: "octagon.fill",
                        iconColor: .red,
                        text: "\(t(.brakingSharp)): \(currentReport.brake_sharp_total)"
                    )
                    EventLine(
                        icon: "octagon.fill",
                        iconColor: .purple,
                        text: "\(t(.brakingEmergency)): \(currentReport.brake_emergency_total)"
                    )

                    EventLine(
                        icon: "arrow.uturn.right.circle.fill",
                        iconColor: .blue,
                        text: "\(t(.turnsSharp)): \(currentReport.turn_sharp_total)"
                    )
                    EventLine(
                        icon: "arrow.uturn.right.circle.fill",
                        iconColor: .teal,
                        text: "\(t(.turnsEmergency)): \(currentReport.turn_emergency_total)"
                    )

                    EventLine(
                        icon: "snowflake",
                        iconColor: .cyan,
                        text: "\(t(.accelerationInTurn)): \(currentReport.accel_in_turn_total)"
                    )
                    EventLine(
                        icon: "snowflake",
                        iconColor: .mint,
                        text: "\(t(.brakingInTurn)): \(currentReport.brake_in_turn_total)"
                    )

                    EventLine(
                        icon: "exclamationmark.circle.fill",
                        iconColor: .brown,
                        text: "\(t(.roadAnomaliesLow)): \(currentReport.road_anomaly_low_total)"
                    )
                    EventLine(
                        icon: "exclamationmark.circle.fill",
                        iconColor: .black,
                        text: "\(t(.roadAnomaliesHigh)): \(currentReport.road_anomaly_high_total)"
                    )
                }
                .padding(.top, 8)
            }
    
            // DETAILS BUTTON (снизу карточки)
            Button {
                
                showDetails = true
            } label: {
                Text(t(.details))
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .foregroundStyle(.white)
                    .background(accent)
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .padding(.top, 6)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 16)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        .shadow(color: Color.black.opacity(0.08), radius: 16, x: 0, y: 10)
    }
}

// MARK: - Start screen computed values

private extension TripReportView {
    func autoRefreshLoopIfNeeded() async {
        guard shouldKeepRefreshing else { return }
        guard let refreshPayload else { return }

        while true {
            let shouldContinue = await MainActor.run { shouldKeepRefreshing }
            guard shouldContinue else { break }

            do {
                if let payload = try await refreshPayload() {
                    await MainActor.run {
                        currentReport = payload.report
                        currentCreatedBatches = max(currentCreatedBatches, payload.createdBatches)
                        currentDeliveredBatches = payload.deliveredBatches
                        refreshIteration += 1
                    }
                }
            } catch {
                // Тихо продолжаем: это фоновое автообновление экрана, не ломаем UI
            }

            let shouldContinueAfterUpdate = await MainActor.run { shouldKeepRefreshing }
            guard shouldContinueAfterUpdate else { break }

            let delayNs: UInt64 = await MainActor.run {
                refreshIteration < 10 ? 2_000_000_000 : 5_000_000_000
            }

            try? await Task.sleep(nanoseconds: delayNs)
        }
    }
}
private extension TripReportView {
    
    // Public Alpha additive fields
    var primaryScore: Double {
        currentReport.score_v2 ?? currentReport.trip_score
    }

    // Public Alpha additive fields
    var drivingModeText: String {
        guard let rawMode = currentReport.driving_mode?.trimmingCharacters(in: .whitespacesAndNewlines),
              !rawMode.isEmpty else { return "—" }

        switch rawMode.lowercased() {
        case "mixed":
            return t(.drivingModeMixed)
        case "city":
            return t(.drivingModeCity)
        case "highway":
            return t(.drivingModeHighway)
        default:
            return t(.drivingModeUnknown)
        }
    }

    // Public Alpha additive fields
    var drivingLoadText: String {
        guard let v = currentReport.driving_load else { return "—" }
        return String(format: "%.2f", v)
    }

    var durationText: String {
        // Public Alpha additive fields
        if let d = currentReport.trip_duration_sec, d > 0 {
            return formatDuration(d)
        }

        guard let d = durationSeconds else { return "—" }
        return formatDuration(d)
    }

    var distanceText: String {
        guard let km = currentReport.distance_km, km > 0 else { return "—" }
        if km < 1 {
            return "\(Int(round(km * 1000.0))) \(t(.meters))"
        }
        return String(format: "%.2f %@", km, t(.km))
    }

    var avgSpeedText: String {
        if let v = currentReport.avg_speed_kmh, v > 0 {
            return String(format: "%.1f %@", v, t(.kmh))
        }

        if let v = currentReport.speed_avg_kmh, v > 0 {
            return String(format: "%.1f %@", v, t(.kmh))
        }

        if let km = currentReport.distance_km, km > 0, let s = durationSeconds, s > 0 {
            let h = Double(s) / 3600.0
            let v = km / h
            if v.isFinite, v > 0 { return String(format: "%.1f %@", v, t(.kmh)) }
        }
        return "—"
    }

    var avgScoreText: String {
        if let v = currentReport.driver_avg_score {
            return String(format: "%.1f / 100", v)
        }
        return "—"
    }

    var driverTripsText: String {
        if let n = currentReport.driver_trips_total {
            return "\(n)"
        }
        return "—"
    }



    var rankLineText: String? {
        guard
            let r = currentReport.driver_rank,
            let total = currentReport.total_drivers,
            total > 0
        else { return nil }

        return "\(r) \(t(.outOf)) \(total) \(t(.driversGenitive))"
    }



    var betterThanPrevLine: String? {
        guard let p = currentReport.better_than_prev_pct else { return nil }
        return "\(t(.betterThanPrevTripsPrefix)) \(Int(round(p)))% \(t(.betterThanPrevTripsSuffix))"
    }

    var betterThanAllLine: String? {
        guard let p = currentReport.better_than_all_pct else { return nil }
        return "\(t(.betterThanAllTripsPrefix)) \(Int(round(p)))% \(t(.betterThanAllTripsSuffix))"
    }


    var allTripsLine: String? {
        guard let n = currentReport.all_trips_count, n > 0 else { return nil }
        return "\(t(.totalTripsCounted)): \(n)"
    }



    var durationSeconds: TimeInterval? {
        // Приоритет: client_* (как для пользователя)
        if let s = currentReport.client_started_at,
           let e = currentReport.client_ended_at,
           let sd = parseISO(s),
           let ed = parseISO(e) {
            let delta = ed.timeIntervalSince(sd)
            if delta.isFinite, delta > 0 { return delta }
        }
        // Фолбэк: received_*
        if let s = currentReport.received_started_at,
           let e = currentReport.received_ended_at,
           let sd = parseISO(s),
           let ed = parseISO(e) {
            let delta = ed.timeIntervalSince(sd)
            if delta.isFinite, delta > 0 { return delta }
        }
        return nil
    }

    func verdictText(for score: Double) -> String {
        switch score {
        case ..<60: return t(.poor)
        case 60..<80: return t(.normal)
        case 80..<90: return t(.good)
        default: return t(.excellent)
        }
    }

    func verdictRightHint(for score: Double) -> String {
        switch score {
        case ..<60: return t(.needsMoreCare)
        case 60..<80: return t(.someSharpMoments)
        case 80..<90: return t(.almostPerfect)
        default: return t(.verySmooth)
        }
    }
}

// MARK: - Detailed sheet (все поля)

private struct TripReportDetailsView: View {
    let report: TripReport
    let accent: Color

    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var languageManager: LanguageManager

    private func t(_ key: LocalizationKey) -> String {
        languageManager.text(key)
    }
    
    private var appLocale: Locale {
        languageManager.locale()
    }
    
    private var deliveryStats: NetworkManager.DeliveryStats {
        NetworkManager.shared.getDeliveryStats(sessionId: report.session_id)
    }
    // Public Alpha additive fields
    private var primaryScore: String {
        let value = report.score_v2 ?? report.trip_score
        return String(format: "%.1f", value)
    }

    // Public Alpha additive fields
    private var drivingLoadText: String {
        guard let v = report.driving_load else { return "—" }
        return String(format: "%.2f", v)
    }

    // Public Alpha additive fields
    var drivingModeText: String {
        guard let rawMode = report.driving_mode?.trimmingCharacters(in: .whitespacesAndNewlines),
              !rawMode.isEmpty else { return "—" }

        switch rawMode.lowercased() {
        case "mixed":
            return t(.drivingModeMixed)
        case "city":
            return t(.drivingModeCity)
        case "highway":
            return t(.drivingModeHighway)
        default:
            return t(.drivingModeUnknown)
        }
    }


    var body: some View {
        NavigationStack {
            List {
                Section(t(.yourTrip)) {
                    // Public Alpha additive fields
                    KVRow(t(.score), value: primaryScore)
                    KVRow(t(.drivingLoad), value: drivingLoadText)
                    KVRow(t(.drivingMode), value: drivingModeText)

                    if FeatureFlags.isDeveloperBuild {
                        if let s2 = report.trip_score_exposure {
                            KVRow("Оценка (exposure)", value: String(format: "%.1f", s2))
                            if let p = report.trip_preset {
                                KVRow("Preset", value: p)
                            }
                        }
                        KVRow("Худший batch score", value: fmt1(report.worst_batch_score))
                    }
                }

                Section(t(.totals)) {
                    KVRow(t(.distance), value: distanceText)
                    KVRow(t(.tripDuration), value: durationText)
                    KVRow(t(.averageSpeed), value: avgSpeedText)
                    KVRow(t(.drivingMode), value: drivingModeText)
                    KVRow(t(.startLabel), value: fmtDate(report.client_started_at, locale: appLocale))
                    KVRow(t(.finishLabel), value: fmtDate(report.client_ended_at ?? report.received_ended_at, locale: appLocale))
                }

                if FeatureFlags.isDeveloperBuild {
                    Section("Скорость") {
                        KVRow("Максимальная", value: fmt1(report.speed_max_kmh, suffix: " км/ч"))
                        KVRow("Средняя", value: fmt1(report.speed_avg_kmh, suffix: " км/ч"))
                        KVRow("P95", value: fmt1(report.speed_p95_kmh, suffix: " км/ч"))
                    }
                }

                Section(t(.eventSummary)) {
                    // Public Alpha additive fields
                    KVRow(t(.dangerousManeuvers), value: "\(report.accel_sharp_total + report.accel_emergency_total + report.brake_sharp_total + report.brake_emergency_total + report.turn_sharp_total + report.turn_emergency_total)")
                    KVRow(t(.skidRisk), value: "\(report.accel_in_turn_total + report.brake_in_turn_total)")
                    KVRow(t(.roadAnomalies),value: "\(report.road_anomaly_low_total + report.road_anomaly_high_total)")

                    if FeatureFlags.isDeveloperBuild {
                        KVRow("Ускорения (резкие)", value: "\(report.accel_sharp_total)")
                        KVRow("Ускорения (экстренные)", value: "\(report.accel_emergency_total)")

                        KVRow("Торможения (резкие)", value: "\(report.brake_sharp_total)")
                        KVRow("Торможения (экстренные)", value: "\(report.brake_emergency_total)")

                        KVRow("Повороты (резкие)", value: "\(report.turn_sharp_total)")
                        KVRow("Повороты (экстренные)", value: "\(report.turn_emergency_total)")

                        KVRow("Ускорение в повороте", value: "\(report.accel_in_turn_total)")
                        KVRow("Торможение в повороте", value: "\(report.brake_in_turn_total)")

                        KVRow("Неровности (низкие)", value: "\(report.road_anomaly_low_total)")
                        KVRow("Неровности (высокие)", value: "\(report.road_anomaly_high_total)")

                        KVRow("Events (в пакетах)", value: "\(report.events_count)")
                    }
                }


                if FeatureFlags.isDeveloperBuild {
                    Section("Датчики (экстремумы)") {
                        KVRow("Accel X min", value: fmt3(report.accel_x_min))
                        KVRow("Accel X max", value: fmt3(report.accel_x_max))
                        KVRow("Accel Y |max|", value: fmt3(report.accel_y_abs_max))
                        KVRow("Accel Z |max|", value: fmt3(report.accel_z_abs_max))
                        KVRow("Gyro Z |max|", value: fmt3(report.gyro_z_abs_max))
                    }
                }

                if FeatureFlags.isDeveloperBuild {
                    Section("Передача данных") {
                        KVRow("Batches", value: "\(report.batches_count)")
                        KVRow("Samples", value: "\(report.samples_count)")

                        KVRow("Макс. batch_seq", value: intOrDash(report.batch_seq_max))
                        KVRow("Пропущено batch_seq", value: intOrDash(report.batches_missing_count))
                        KVRow("Отправлено batch в EU", value: "\(deliveryStats.euBatches)")
                        KVRow("Отправлено batch через RU", value: "\(deliveryStats.ruBatches)")
                        KVRow("Отчёт получен через", value: deliveryStats.reportVia?.rawValue ?? "—")
                    }
                }
                
                if FeatureFlags.isDeveloperBuild {
                    Section("Качество GPS") {
                        KVRow("GPS points", value: intOrDash(report.gps_points))
                        KVRow("GPS 1Hz points", value: intOrDash(report.gps_1hz_points))

                        KVRow(
                            "Точность GPS (P95)",
                            value: {
                                guard let v = report.gps_hacc_p95_m else { return "—" }
                                return String(format: "%.0f м", v)
                            }()
                        )

                        KVRow(
                            "Хороших точек (≤100 м)",
                            value: {
                                guard let v = report.gps_good_100_share else { return "—" }
                                return String(format: "%.0f%%", v * 100.0)
                            }()
                        )

                        KVRow(
                            "GPS залип",
                            value: {
                                guard let v = report.gps_is_stuck else { return "—" }
                                return (v == 1) ? "Да" : "Нет"
                            }()
                        )

                        KVRow(
                            "GPS качество",
                            value: {
                                guard let v = report.gps_quality_score else { return "—" }
                                return "\(v) / 100"
                            }()
                        )

                        KVRow("Уникальные координаты", value: intOrDash(report.gps_unique_coords_5dp))

                        KVRow(
                            "Разброс координат",
                            value: {
                                guard let v = report.gps_span_m else { return "—" }
                                return String(format: "%.1f м", v)
                            }()
                        )
                    }
                }


                Section(t(.stops)) {
                    KVRow(t(.stopsCount), value: intOrDash(report.stops_count))
                    KVRow(t(.stopsTotal), value: fmt1(report.stops_total_sec, suffix: " \(t(.seconds))"))
                    KVRow(t(.stopsP95), value: fmt1(report.stops_p95_sec, suffix: " \(t(.seconds))"))
                    KVRow(t(.stopsPerKm), value: fmt3(report.stops_per_km))
                }
                
                Section(t(.comparison)) {
                    KVRow(t(.vsPreviousTrip), value: pctOrDash(report.better_than_prev_pct))
                    KVRow(t(.vsAllTrips), value: pctOrDash(report.better_than_all_pct))
                    KVRow(t(.previousTripsDriver), value: intOrDash(report.prev_trips_count))
                    KVRow(t(.allTripsInDatabase), value: intOrDash(report.all_trips_count))
                    KVRow(t(.driverRank), value: intOrDash(report.driver_rank))
                    KVRow(t(.totalDrivers), value: intOrDash(report.total_drivers))
                    KVRow(t(.driverAverageScore), value: fmt1(report.driver_avg_score))
                    KVRow(t(.driverTripsCount), value: intOrDash(report.driver_trips_total))
                }
                if FeatureFlags.isDeveloperBuild {
                    Section("Идентификаторы") {
                        KVRow("session_id", value: report.session_id)
                        KVRow("driver_id", value: report.driver_id)
                        KVRow("device_id", value: report.device_id)
                        
                    }
                }
                
                if FeatureFlags.isDeveloperBuild {
                    Section("Тайминги (сервер)") {
                        KVRow("received_started_at", value: fmtDate(report.received_started_at, locale: appLocale))
                        KVRow("received_ended_at", value: fmtDate(report.received_ended_at, locale: appLocale))
                    }
                }
            }
            .navigationTitle(t(.tripReportTitle))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(t(.close)) { dismiss() }
                }
            }
            .tint(accent)
        }
    }

    private var durationSeconds: TimeInterval? {
        if let s = report.client_started_at,
           let e = report.client_ended_at,
           let sd = parseISO(s),
           let ed = parseISO(e) {
            let d = ed.timeIntervalSince(sd)
            if d.isFinite, d > 0 { return d }
        }
        if let s = report.received_started_at,
           let e = report.received_ended_at,
           let sd = parseISO(s),
           let ed = parseISO(e) {
            let d = ed.timeIntervalSince(sd)
            if d.isFinite, d > 0 { return d }
        }
        return nil
    }

    private var durationText: String {
        guard let d = durationSeconds else { return "—" }
        return formatDuration(d)
    }

    private var distanceText: String {
        guard let km = report.distance_km, km > 0 else { return "—" }
        if km < 1 { return "\(Int(round(km * 1000.0))) \(t(.meters))" }
        return String(format: "%.3f %@", km, t(.km))
    }

    private var avgSpeedText: String {
        if let v = report.speed_avg_kmh, v > 0 { return String(format: "%.1f %@", v, t(.kmh)) }
        if let km = report.distance_km, km > 0, let s = durationSeconds, s > 0 {
            let h = Double(s) / 3600.0
            let v = km / h
            if v.isFinite, v > 0 { return String(format: "%.1f %@", v, t(.kmh)) }
        }
        return "—"
    }
}

// MARK: - UI primitives

private struct IconValueRow: View {
    let icon: String
    let iconColor: Color
    let title: String
    let value: String
    var valueEmphasis: Bool = false

    var body: some View {
        HStack(alignment: .center, spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(iconColor)
                .frame(width: 28, alignment: .center)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .foregroundStyle(.secondary)
                    .font(.body)
                Text(value)
                    .font(valueEmphasis ? .title3.weight(.semibold) : .body)
                    .foregroundStyle(.primary)
                    .monospacedDigit()
            }

            Spacer()
        }
    }
}

private struct ComparisonLine: View {
    let icon: String
    let iconColor: Color
    let text: String
    var bold: Bool
    var secondary: Bool = false

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(secondary ? .secondary : iconColor)
                .frame(width: 26, alignment: .top)

            Text(text)
                .font(bold ? .headline : .body)
                .foregroundStyle(secondary ? .secondary : .primary)
                .lineLimit(3)
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 0)
        }
    }
}

private struct EventLine: View {
    let icon: String
    let iconColor: Color
    let text: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(iconColor)
                .frame(width: 28)

            Text(text)
                .font(.headline)
                .foregroundStyle(.primary)

            Spacer()
        }
    }
}

private struct KVRow: View {
    let key: String
    let value: String
    init(_ key: String, value: String) { self.key = key; self.value = value }

    var body: some View {
        HStack {
            Text(key)
            Spacer()
            Text(value)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.trailing)
        }
    }
}

// MARK: - Formatting helpers

private func parseISO(_ s: String?) -> Date? {
    guard var s = s?.trimmingCharacters(in: .whitespacesAndNewlines), !s.isEmpty else { return nil }
    if s.hasSuffix("Z") { s = String(s.dropLast()) + "+00:00" }

    // Cache DateFormatter + formats (DateFormatter is expensive to create)
    struct Cache {
        static let df: DateFormatter = {
            let df = DateFormatter()
            df.locale = Locale(identifier: "en_US_POSIX")
            return df
        }()
        static let formats: [String] = [
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss"
        ]
    }

    for f in Cache.formats {
        Cache.df.dateFormat = f
        if let d = Cache.df.date(from: s) { return d }
    }
    return nil
}


private func fmtDate(_ s: String?, locale: Locale) -> String {
    guard let d = parseISO(s) else { return s ?? "—" }
    let df = DateFormatter()
    df.locale = locale
    df.dateFormat = "dd.MM.yyyy HH:mm:ss"
    return df.string(from: d)
}

private func formatDuration(_ seconds: TimeInterval) -> String {
    let total = max(0, Int(seconds.rounded()))
    let h = total / 3600
    let m = (total % 3600) / 60
    let s = total % 60
    if h > 0 { return String(format: "%d:%02d:%02d", h, m, s) }
    return String(format: "%d:%02d", m, s)
}

private func fmt1(_ v: Double?, suffix: String = "") -> String {
    guard let v else { return "—" }
    return String(format: "%.1f%@", v, suffix)
}

private func fmt3(_ v: Double?) -> String {
    guard let v else { return "—" }
    return String(format: "%.3f", v)
}

private func intOrDash(_ v: Int?) -> String {
    guard let v else { return "—" }
    return "\(v)"
}

private func fmtInt(_ v: Int?, suffix: String = "") -> String {
    guard let v else { return "—" }
    return "\(v)\(suffix)"
}

private func fmt0(_ v: Double?, suffix: String = "") -> String {
    guard let v else { return "—" }
    return String(format: "%.0f%@", v, suffix)
}


private func pctOrDash(_ v: Double?) -> String {
    guard let v else { return "—" }
    let pct = (v <= 1.0) ? (v * 100.0) : v
    return String(format: "%.1f%%", pct)
}

private struct V2HelpButton: View {
    @EnvironmentObject var languageManager: LanguageManager
    @State private var show = false

    private func t(_ key: LocalizationKey) -> String {
        languageManager.text(key)
    }

    var body: some View {
        Button {
            show = true
        } label: {
            Image(systemName: "questionmark.circle")
        }
        .buttonStyle(.plain)
        .popover(isPresented: $show) {
            VStack(alignment: .leading, spacing: 12) {
                Text(t(.eventClassesV2))
                    .font(.headline)

                Text(t(.eventClassSharpHelp))
                Text(t(.eventClassEmergencyHelp))
                Text(t(.eventClassAccelBrakeTurnHelp))
                Text(t(.eventClassAccelBrakeInTurnHelp))
                Text(t(.eventClassRoadAnomalyHelp))

                Divider()

                Text(t(.eventClassesImportantNote))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .padding(20)
            .frame(width: 340)
            .background(Color(.systemGray))
            .shadow(radius: 10)
            .clipShape(RoundedRectangle(cornerRadius: 20))
            .presentationBackground(.clear)
            .presentationCompactAdaptation(.popover)
        }
    }
}

private struct SummaryPill: View {
    let title: String
    let value: Int
    let systemIcon: String

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Image(systemName: systemIcon)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.primary)

            Text(title)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .lineLimit(3)
                .minimumScaleFactor(0.85)
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 0)

            Text("\(value)")
                .font(.system(size: 24, weight: .semibold))
                .foregroundStyle(.primary)
                .monospacedDigit()
        }
        .padding(12)
        .frame(maxWidth: .infinity, minHeight: 132, alignment: .topLeading)
        .background(.thinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}
