//
//  ContentView.swift
//  TelemetryApp
//

import SwiftUI
import UIKit

struct ContentView: View {

    @EnvironmentObject var sensorManager: SensorManager
    
    @EnvironmentObject private var dayMonitoring: DayMonitoringManager
    @EnvironmentObject var languageManager: LanguageManager
    @EnvironmentObject var dashcamManager: DashcamManager
    @AppStorage("trackingMode") private var trackingModeRaw: String = TrackingMode.singleTrip.rawValue
    
    @AppStorage("drivingTestMode") private var drivingTestMode: Bool = false
    
    private func t(_ key: LocalizationKey) -> String {
        languageManager.text(key)
    }

    private var trackingMode: TrackingMode {
        TrackingMode(rawValue: trackingModeRaw) ?? .singleTrip
    }

    private func localizedDriverFatigueState(_ state: DriverFatigueState) -> String {
        switch state {
        case .normal:
            return t(.dmsStateNormal)
        case .warning:
            return t(.dmsStateWarning)
        case .critical:
            return t(.dmsStateCritical)
        case .distracted:
            return t(.dmsStateDistracted)
        case .drowsy:
            return t(.dmsStateDrowsy)
        }
    }



    @State private var loginInput: String = ""
    @State private var showingSettings: Bool = false
    @State private var showingDriveTelemetryGuide: Bool = false
    @State private var toolbarRefreshToken: Int = 0
    @State private var showingDriverSetup: Bool = false
    
//    @State private var showingDashcamTeaser: Bool = false
    
    // ===== Water glass visualization =====
        @State private var showWaterGlass: Bool = false

    // === Trip report ===
    @State private var tripReport: TripReport?
    
    @State private var homeMetrics: DriverHomeResponse?
    @State private var homeMetricsError: String? = nil
    @State private var isLoadingHomeMetrics: Bool = false

    // === Completeness snapshot (Stop) ===
    @State private var stopSessionIdForReport: String = ""
    @State private var stopCreatedBatches: Int = 0
    @State private var stopDeliveredBatches: Int = 0

    // === Auto-refresh report while completeness < 100% ===
    @State private var autoRefreshTask: Task<Void, Never>? = nil


    // === Stop / Finish UX ===
    @State private var finishStatusText: String = ""
    @State private var lastFinishError: String? = nil
    
    @State private var lastFinishReason: String = ""
    @State private var lastFinishWasAuto: Bool = false

    // Lock state so repeated Stop doesn't vibrate / doesn't restart flow
    @State private var isStopFlowActive: Bool = false
    @State private var stopRequestId: UUID = UUID()

    // After Stop, keep Stop disabled until next Start
    @State private var stopLockedUntilNextStart: Bool = false
    @State private var lastStopWasAutoFinish: Bool = false
    
    // One-time network recovery on launch
    @State private var didRunStartupRecovery: Bool = false
    
    @State private var stopT0: Date? = nil
    
    @State private var showingTripsArchiveFromDots: Bool = false
    
    @State private var showingDriverSetupAfterForcedStop = false
    
    @State private var stopTripOwnerDriverId: String = ""
    
    @AppStorage("didShowPermissionOnboarding") private var didShowPermissionOnboarding = false
    @State private var showingPermissionOnboarding = false
    
    @State private var recentTrips: [TripSummary] = []
    @State private var isLoadingTrips: Bool = false
    
    
    @State private var isPreviewContainerVisible: Bool = false
    
    
    // ===== Stats table help =====
    private enum StatsColumn: String, Identifiable {
        case event, count, sumG, maxG, countPerKm, gPerKm
        var id: String { rawValue }

        var title: String {
            switch self {
            case .event:      return LocalizationCatalog.text(.statsColumnEvent)
            case .count:      return LocalizationCatalog.text(.statsColumnCount)
            case .sumG:       return LocalizationCatalog.text(.statsColumnSumG)
            case .maxG:       return LocalizationCatalog.text(.statsColumnMaxG)
            case .countPerKm: return LocalizationCatalog.text(.statsColumnCountPerKm)
            case .gPerKm:     return LocalizationCatalog.text(.statsColumnGPerKm)
            }
        }

        var help: String {
            switch self {
            case .event:
                return LocalizationCatalog.text(.statsColumnHelpEvent)
            case .count:
                return LocalizationCatalog.text(.statsColumnHelpCount)
            case .sumG:
                return LocalizationCatalog.text(.statsColumnHelpSumG)
            case .maxG:
                return LocalizationCatalog.text(.statsColumnHelpMaxG)
            case .countPerKm:
                return LocalizationCatalog.text(.statsColumnHelpCountPerKm)
            case .gPerKm:
                return LocalizationCatalog.text(.statsColumnHelpGPerKm)
            }
        }
    }

    @State private var statsHelpColumn: StatsColumn? = nil
    
    // Public Alpha additive fields
    private var isTripActive: Bool {
        sensorManager.isCollectingNow || dayMonitoring.state == .inTrip
    }
    
    private func colorForTripBadge(_ value: String) -> Color {
        switch value.lowercased() {
        case "green":
            return .green
        case "yellow":
            return .yellow
        case "red":
            return .red
        default:
            return .gray.opacity(0.5)
        }
    }
    
    private func fetchRecentTrips() {
        isLoadingTrips = true

        let effectiveDriverId: String
        if sensorManager.isDriverAuthorizedOnThisDevice {
            effectiveDriverId = sensorManager.driverId
        } else {
            effectiveDriverId = ""
        }

        NetworkManager.shared.fetchRecentTrips(
            deviceId: sensorManager.deviceIdForDisplay,
            driverId: effectiveDriverId,
            limit: 5
        ) { result in
            DispatchQueue.main.async {
                self.isLoadingTrips = false

                switch result {
                case .success(let items):
                    self.recentTrips = items
                case .failure:
                    self.recentTrips = []
                }
            }
        }
    }
    
    
    @MainActor
    private func refreshHomeMetrics() async {
        let rawDriverId = sensorManager.driverId.trimmingCharacters(in: .whitespacesAndNewlines)
        let deviceId = sensorManager.deviceIdForDisplay.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !deviceId.isEmpty else {
            homeMetrics = nil
            homeMetricsError = t(.missingDeviceId)
            return
        }

        let effectiveDriverId: String?
        if sensorManager.isDriverAuthorizedOnThisDevice, !rawDriverId.isEmpty {
            effectiveDriverId = rawDriverId
        } else {
            effectiveDriverId = nil
        }

        isLoadingHomeMetrics = true
        defer { isLoadingHomeMetrics = false }

        await withCheckedContinuation { continuation in
            NetworkManager.shared.fetchDriverHome(
                deviceId: deviceId,
                driverId: effectiveDriverId
            ) { result in
                DispatchQueue.main.async {
                    switch result {
                    case .success(let response):
                        self.homeMetrics = response
                        self.homeMetricsError = nil

                    case .failure(let error):
                        self.homeMetricsError = error.localizedDescription
                    }
                    continuation.resume()
                }
            }
        }
    }
    
    @MainActor
    private func finishTripFromUI(reason: String) async {
        // сюда нужно перенести код,
        // который сейчас выполняется при нажатии кнопки Stop
    }
    
    
    private var shouldShowTripSummaryCard: Bool {
        if trackingMode == .singleTrip {
            // Старое поведение “Одна поездка”: карточка появляется, когда есть ненулевое время
            return sensorManager.currentTripElapsedSec > 0
        }

        // Режим “Мониторинг дня”:
        // показываем карточку ТОЛЬКО если реально идёт поездка (авто или ручная),
        // иначе (idle + не collecting) скрываем.
        return sensorManager.isCollectingNow || dayMonitoring.state == .inTrip
    }
    
    @MainActor
    private func resetHomeScreenForDriverChange() {
        homeMetrics = nil
        homeMetricsError = nil
        isLoadingHomeMetrics = false

        tripReport = nil
        finishStatusText = ""
        lastFinishError = nil
        lastFinishReason = ""
        lastFinishWasAuto = false

        stopSessionIdForReport = ""
        stopCreatedBatches = 0
        stopDeliveredBatches = 0

        autoRefreshTask?.cancel()
        autoRefreshTask = nil

        stopLockedUntilNextStart = false
        isStopFlowActive = false
        
        recentTrips = []
        isLoadingTrips = false
    }
    
    private func makeClientTripMetrics(durationSec: Double) -> ClientTripMetrics {
        let rawDistKm = sensorManager.currentTripDistanceKm

        let distKm: Double = {
            guard rawDistKm.isFinite else { return 0.0 }
            return max(0.0, rawDistKm)
        }()

        let distM = NumericSanitizer.metric(distKm * 1000.0)

        func perKm(_ value: Double) -> Double {
            guard distKm > 0.000001 else { return 0.0 }
            return NumericSanitizer.metric(value / distKm)
        }

        func agg(count: Int, sum: Double, maxVal: Double) -> ClientAgg {
            ClientAgg(
                count: count,
                sum_intensity: NumericSanitizer.metric(sum),
                max_intensity: NumericSanitizer.metric(maxVal),
                count_per_km: perKm(Double(count)),
                sum_per_km: perKm(sum)
            )
        }

        return ClientTripMetrics(
            trip_distance_m: distM,
            trip_distance_km_from_gps: NumericSanitizer.metric(distKm),
            brake: agg(
                count: sensorManager.brakeCount,
                sum: sensorManager.brakeSumIntensity,
                maxVal: sensorManager.brakeMaxIntensity
            ),
            accel: agg(
                count: sensorManager.accelCount,
                sum: sensorManager.accelSumIntensity,
                maxVal: sensorManager.accelMaxIntensity
            ),
            road: agg(
                count: sensorManager.roadCount,
                sum: sensorManager.roadSumIntensity,
                maxVal: sensorManager.roadMaxIntensity
            ),
            turn: agg(
                count: sensorManager.turnCount,
                sum: sensorManager.turnSumIntensity,
                maxVal: sensorManager.turnMaxIntensity
            )
        )
    }
    
     
    // Таймаут ожидания отчёта после Stop (по требованиям)
    private let stopFinishUiTimeoutSec: TimeInterval = 5.0

    private var offlineFinishMessage: String { t(.offlineFinishMessage) }
    private var searchingServerMessage: String { t(.searchingServerMessage) }
    private var autoFinishSearchingMessage: String { t(.autoFinishSearchingMessage) }


    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    
                    
                    // Public Alpha additive fields
                    _DriverScoreCardView(
                        title: t(.driverScore),
                        scoreText: homeScoreText,
                        primarySubtitle: homePrimarySubtitle,
                        secondarySubtitle: homeSecondarySubtitle,
                        delta: homeMetrics?.score_delta_recent,
                        deltaLabel: {
                            guard recentTrips.count >= 5, let delta = homeMetrics?.score_delta_recent else { return nil }
                            let deltaText = String(format: "%@%.1f", delta >= 0 ? "+" : "", delta)
                            return String(format: t(.scoreDeltaLastTrips), deltaText)
                        }(),
                        ratingFormingText: {
                            guard let metrics = homeMetrics, metrics.rating_status == "forming" else { return nil }
                            return String(format: t(.ratingFormingTripsLeft), metrics.trips_to_unlock_percentile)
                        }(),
                        percentileText: {
                            guard let metrics = homeMetrics,
                                  metrics.rating_status != "forming",
                                  let pct = metrics.better_than_drivers_pct else { return nil }

                            let roundedPct = Int(round(pct))

                            if roundedPct <= 50 {
                                return String(format: t(.betterThanDriversPercent), roundedPct)
                            } else {
                                let topPct = max(1, 100 - roundedPct)
                                return String(format: t(.topDriversPercent), topPct)
                            }
                        }(),
                        nextLevelText: {
                            guard let metrics = homeMetrics,
                                  let nextLevelRaw = metrics.next_level,
                                  let nextLevel = localizedDriverLevel(nextLevelRaw),
                                  let points = metrics.points_to_next_level else { return nil }

                            let pointsText = String(format: "%.1f", points)
                            return String(format: t(.toNextLevelLeft), nextLevel, pointsText)
                        }(),
                        homeMetricsError: homeMetrics == nil ? homeMetricsError : nil,
                        hasRecentTrips: !effectiveRecentTripColors.isEmpty,
                        tripSeriesTitle: tripSeriesTitle,
                        tripSeriesHint: tripSeriesHint,
                        recentTripColors: effectiveRecentTripColors,
                        onTripsTap: {
                            sensorManager.markScreenInteractionInApp()
                            showingTripsArchiveFromDots = true
                        },
                        colorForTripBadge: colorForTripBadge,
                       
                    )
                    
                    // Public Alpha additive fields
                    _TripStateBadgeView(
                        isTripActive: isTripActive,
                        activeText: t(.recording),
                        idleText: t(.ready)
                    )
                    
                    if FeatureFlags.isDeveloperBuild {
                        HStack {
                            Text("\(t(.driverLabel)): \(sensorManager.driverId)")
                                .font(.headline)
                                .foregroundColor(.secondary)

                            Spacer()

                            Button(t(.change)) {
                                sensorManager.markScreenInteractionInApp()
                                showingDriverSetup = true
                            }
                            .buttonStyle(.bordered)
                        }
                        .padding(.horizontal)
                    }

                    if sensorManager.driverAuthState == .passwordRequired {
                        HStack {
                            Image(systemName: "exclamationmark.triangle.fill")
                            Text(t(.passwordConfirmationRequired))
                            Spacer()
                        }
                        .font(.footnote)
                        .foregroundColor(.red)
                        .padding(.horizontal)
                    } else if sensorManager.driverAuthState == .temporarilyUnavailable {
                        HStack {
                            Image(systemName: "wifi.exclamationmark")
                            Text(sensorManager.lastDriverAuthError ?? t(.driverAuthUnavailable))
                            Spacer()
                        }
                        .font(.footnote)
                        .foregroundColor(.orange)
                        .padding(.horizontal)
                    }
                    // ===== App state =====
                    

                    if sensorManager.shouldShowGpsBadgeInMainUI {
                        HStack {
                            Button {
                                showingSettings = true
                            } label: {
                                Label(t(.gps), systemImage: "location.slash.fill")
                                    .font(.subheadline.weight(.semibold))
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(.red)

                            Spacer()
                        }
                        .padding(.horizontal)
                    }


                    if sensorManager.shouldShowAlwaysWarningInMainUI {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(t(.unstableOperationTitle))
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(.red)

                            Text(t(.alwaysPermissionHint))
                                .font(.footnote)
                                .foregroundColor(.red)
                                .fixedSize(horizontal: false, vertical: true)

                            Button(t(.openSettings)) {
                                showingSettings = true
                            }
                            .font(.footnote)
                            .buttonStyle(.bordered)

                        }
                        .padding(.horizontal)
                    }
                    
                    
                    
                    let safeKm = max(sensorManager.currentTripDistanceKm, 0.001)

                    
                    // ===== Current trip info =====÷÷
                    if shouldShowTripSummaryCard {
                        _TripSummaryCardView(
                            speedLabel: t(.currentSpeed),
                            tripTimeLabel: t(.tripTime),
                            distanceLabel: t(.distance),
                            currentSpeedText: sensorManager.lastSpeedString,
                            tripTimeText: formatElapsed(sensorManager.currentTripElapsedSec),
                            distanceText: String(format: "%.2f %@", sensorManager.currentTripDistanceKm, t(.km))
                        )
                    }
                    // Trip driving statistics (Grid + header with help)
                    if FeatureFlags.isDeveloperBuild {
                        telemetryStatsGrid(safeKm: safeKm)
                            .cardStyle()
                            .overlay(statsHelpOverlay())
                    }
                        
                    
                    if sensorManager.crashDetected {
                        HStack(spacing: 10) {

                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundColor(.white)

                            VStack(alignment: .leading, spacing: 2) {
                                Text("\(t(.crashDetected)) - \(sensorManager.crashCount)")
                                    .onAppear {
                                                print("[CRASH_BANNER_APPEAR] at=\(Date()) count=\(sensorManager.crashCount)")
                                            }
                                
                                    .font(.headline)

                                Text(String(format: t(.crashImpactFormat), sensorManager.crashG))
                                    .font(.subheadline)
                                    .opacity(0.9)
                            }

                            Spacer()
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity)
                        .background(Color.red)
                        .foregroundColor(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }
                    
                    
                    let accelGPerKm = sensorManager.accelSumIntensity / safeKm
                    let brakeGPerKm = sensorManager.brakeSumIntensity / safeKm
                    let turnGPerKm  = sensorManager.turnSumIntensity / safeKm
                    let roadGPerKm  = sensorManager.roadSumIntensity / safeKm
                    
                    if FeatureFlags.isDeveloperBuild {
                        let smooth = smoothnessScore(
                            accelGPerKm: accelGPerKm,
                            brakeGPerKm: brakeGPerKm,
                            turnGPerKm: turnGPerKm,
                            roadGPerKm: roadGPerKm
                        )

                        let accelLC = barLevelAndColor(accelGPerKm)
                        let brakeLC = barLevelAndColor(brakeGPerKm)
                        let turnLC  = barLevelAndColor(turnGPerKm)
                        let roadLC  = barLevelAndColor(roadGPerKm)

                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Text(t(.smoothness))
                                    .font(.headline)
                                Spacer()
                                Text("\(smooth)")
                                    .font(.system(.title3, design: .monospaced).weight(.bold))
                            }

                            MetricBarRow(title: "Acceleration", valuePerKm: accelGPerKm, level: accelLC.level, color: accelLC.color)
                            MetricBarRow(title: "Braking",      valuePerKm: brakeGPerKm, level: brakeLC.level, color: brakeLC.color)
                            MetricBarRow(title: "Turns",        valuePerKm: turnGPerKm,  level: turnLC.level,  color: turnLC.color)
                            MetricBarRow(title: "Road bumps",   valuePerKm: roadGPerKm,  level: roadLC.level,  color: roadLC.color)
                        }
                        .padding(12)
                        .background(Color.gray.opacity(0.10))
                        .cornerRadius(12)
                    }
                    
                    if FeatureFlags.isDeveloperBuild {
                        Picker("", selection: Binding(
                            get: { trackingModeRaw },
                            set: { raw in
                                trackingModeRaw = raw
                                
                                // Важно: при переключении режимов не допускаем конфликтов
                                let newMode = TrackingMode(rawValue: raw) ?? .singleTrip
                                if newMode == .singleTrip {
                                    dayMonitoring.setEnabled(false)
                                } else {
                                    // В day monitoring НЕ стартуем сразу trip-сессию.
                                    // Пользователь отдельно включает мониторинг кнопкой ниже.
                                }
                            }
                        )) {
                            ForEach(TrackingMode.allCases) { mode in
                                Text(mode.title).tag(mode.rawValue)
                            }
                        }
                        .pickerStyle(.segmented)
                        .padding(.horizontal)
                    }
                        
                 
                    // ===== Start / Stop =====
                    
                    if trackingMode == .singleTrip {
                        _StartStopControlsView(
                            startTitle: t(.start),
                            stopTitle: t(.stop),
                            canStart: (!sensorManager.isCollectingNow || dashcamManager.allowsManualTripStartDuringVideo) && !sensorManager.driverId.isEmpty,
                            canStop: sensorManager.isCollectingNow && !dashcamManager.shouldBlockTripStopButton && !isStopFlowActive && !stopLockedUntilNextStart,
                            onStart: {
                                sensorManager.markScreenInteractionInApp()

                                stopLockedUntilNextStart = false
                                finishStatusText = ""
                                lastStopWasAutoFinish = false

                                lastFinishError = nil
                                lastFinishReason = ""
                                lastFinishWasAuto = false
                                tripReport = nil

                                autoRefreshTask?.cancel()
                                autoRefreshTask = nil
                                stopSessionIdForReport = ""
                                stopCreatedBatches = 0
                                stopDeliveredBatches = 0

                                sensorManager.setTripContext(trackingMode: "single_trip", transportMode: "unknown")

                                if dashcamManager.allowsManualTripStartDuringVideo {
                                    Task {
                                        await dashcamManager.prepareManualTripStartDuringVideo()
                                    }
                                } else {
                                    sensorManager.startCollecting()
                                    dashcamManager.manualTripStartedOutsideVideo()
                                }
                            },
                            onStop: {
                                sensorManager.markScreenInteractionInApp()
                                handleStopPressed()
                            }
                        )

                        HStack(spacing: 12) {
                            NavigationLink {
                                TripsArchiveView()
                                    .onAppear {
                                        sensorManager.markScreenInteractionInApp()
                                    }
                            } label: {
                                Label(t(.tripHistory), systemImage: "clock.arrow.circlepath")
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                            }
                            .buttonStyle(.bordered)

                            NavigationLink {
                                VideoArchiveView(
                                    viewModel: VideoArchiveViewModel(
                                        archiveStore: try! JSONVideoArchiveStore(),
                                        settingsStore: UserDefaultsDashcamSettingsStore()
                                    ),
                                    isInteractionLocked: dashcamManager.isVideoModeActive
                                )
                            } label: {
                                Label(t(.videoArchiveTitle), systemImage: "internaldrive")
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                            }
                            .buttonStyle(.bordered)

                        }
                        .padding(.horizontal)


                        if dashcamManager.state == .idle {
                            Picker(t(.camera), selection: Binding(
                                get: { dashcamManager.cameraMode },
                                set: { dashcamManager.setCameraMode($0) }
                            )) {
                                Text(t(.roadCamera)).tag(DashcamCameraMode.rear)
                                Text(t(.driverCamera)).tag(DashcamCameraMode.front)
                            }
                            .pickerStyle(.segmented)
                            .padding(.horizontal)
                        }

                        Button {
                            sensorManager.markScreenInteractionInApp()
                            Task {
                                do {
                                    try await dashcamManager.requestPermissionsIfNeeded()
                                    try await dashcamManager.startVideoMode(trigger: .userButton)
                                    isPreviewContainerVisible = true
                                } catch {
                                    print("[DashcamUI] start error: \(error)")
                                }
                            }
                        } label: {
                            Label(t(.dashcam), systemImage: "video")
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                        }
                        .buttonStyle(.bordered)
                        .padding(.horizontal)
                        .disabled(dashcamManager.state != .idle || sensorManager.driverId.isEmpty)
                        
                        if dashcamManager.state == .recording || dashcamManager.state == .preparing || dashcamManager.state == .stopping {
                            VStack(spacing: 12) {
                                HStack {
                                    Image(systemName: "record.circle.fill")
                                        .foregroundColor(dashcamManager.state == .stopping ? .orange : .red)

                                    Text(dashcamManager.state == .stopping ? t(.videoSavingInProgress) : t(.videoRecordingInProgress))
                                        .font(.headline)

                                    Spacer()

                                    if dashcamManager.state == .stopping {
                                        Text("\(Int(dashcamManager.stopProgressValue * 100))%")
                                            .font(.system(.body, design: .monospaced))
                                    } else {
                                        Text(dashcamManager.timerText)
                                            .font(.system(.body, design: .monospaced))
                                    }
                                }
                                
                                CameraPreviewContainerView(sessionProvider: dashcamManager)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: isPreviewContainerVisible && dashcamManager.state == .recording ? 220 : 0)
                                    .background(Color.black)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12)
                                            .stroke(Color.green, lineWidth: 2)
                                    )
                                    .opacity(isPreviewContainerVisible && dashcamManager.state == .recording ? 1 : 0)
                                    .allowsHitTesting(isPreviewContainerVisible && dashcamManager.state == .recording)
                                    .clipped()
                                
                                driverFatigueAlertView
                                
                                if dashcamManager.cameraMode == .front && dashcamManager.state == .recording {
                                    VStack(alignment: .leading, spacing: 6) {
                                        Text(t(.dmsMonitoringTitle))
                                            .font(.caption)
                                            .foregroundColor(.secondary)

                                        HStack(alignment: .firstTextBaseline, spacing: 14) {
                                            Text(String(format: t(.dmsEyeScoreFormat), dashcamManager.driverEyeOpenScore))
                                                .font(.caption)

                                            Spacer(minLength: 8)

                                            Text(t(.dmsFatigueLabel))
                                                .font(.caption)
                                                .foregroundColor(.secondary)

                                            Text("\(dashcamManager.driverFatigueScore, specifier: "%.0f")")
                                                .font(.system(size: 34, weight: .bold, design: .rounded))

                                            Text(t(.dmsScoreMax))
                                                .font(.caption)
                                                .foregroundColor(.secondary)
                                        }

                                        Text(String(format: t(.dmsPerclosFormat), dashcamManager.driverPerclos))
                                            .font(.caption)

                                        Text(String(format: t(.dmsStateFormat), localizedDriverFatigueState(dashcamManager.driverFatigueState)))
                                            .font(.caption)
                                    }
                                    
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(10)
                                    .background(.thinMaterial)
                                    .cornerRadius(10)
                                }

                                if dashcamManager.state == .stopping {
                                    VStack(spacing: 8) {
                                        ProgressView(value: dashcamManager.stopProgressValue)
                                            .progressViewStyle(.linear)

                                        Text(dashcamManager.stopProgressText ?? t(.videoSavingInProgress))
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                    }
                                } else {
                                    HStack(spacing: 12) {
                                        Button {
                                            if isPreviewContainerVisible {
                                                dashcamManager.hidePreview()
                                                isPreviewContainerVisible = false
                                            } else {
                                                dashcamManager.showPreview()
                                                isPreviewContainerVisible = true
                                            }
                                        } label: {
                                            Label(
                                                isPreviewContainerVisible ? t(.hideCamera) : t(.showCamera),
                                                systemImage: isPreviewContainerVisible ? "eye.slash" : "eye"
                                            )
                                            .frame(maxWidth: .infinity)
                                            .padding(.vertical, 10)
                                        }
                                        .buttonStyle(.bordered)

                                        Button(role: .destructive) {
                                            dashcamManager.hidePreview()
                                            isPreviewContainerVisible = false
                                            
                                            Task {
                                                await dashcamManager.stopVideoMode(trigger: .userButton)
                                            }
                                        } label: {
                                            Label(t(.stopVideo), systemImage: "stop.fill")
                                                .frame(maxWidth: .infinity)
                                                .padding(.vertical, 10)
                                        }
                                        .buttonStyle(.borderedProminent)
                                    }
                                }
                            }
                            .padding(.horizontal)
                        }
                    }
                    
                    // ===== Live visualization: glass of water =====
                
                    
                    Button {
                        sensorManager.markScreenInteractionInApp()

                        showWaterGlass = true
                    } label: {
                        Label(t(.saveFishGame), systemImage: "water.waves")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                    }
                    .buttonStyle(.bordered)
                    .padding(.horizontal)

                    .padding(.top, 12)   // отступ от предыдущего блока}

                    
                    if trackingMode == .dayMonitoring {
                        VStack(spacing: 12) {

                            if FeatureFlags.isDeveloperBuild {
                                HStack {
                                    Text("\(t(.monitoring)):")
                                    Spacer()
                                    Text(dayMonitoring.isEnabled ? "ON" : "OFF")
                                        .foregroundColor(dayMonitoring.isEnabled ? .green : .secondary)
                                }

                                HStack {
                                    Text("\(t(.activity)):")
                                    Spacer()
                                    Text(dayMonitoring.lastActivityText)
                                        .foregroundColor(.secondary)
                                        .lineLimit(1)
                                }

                                HStack {
                                    Text("\(t(.state)):")
                                    Spacer()
                                    Text(dayMonitoring.state.rawValue)
                                        .foregroundColor(.secondary)
                                }

                                HStack {
                                    Text("\(t(.tripsToday)):")
                                    Spacer()
                                    Text("\(dayMonitoring.tripsCompletedToday)")
                                        .foregroundColor(.secondary)
                                }

                                if !dayMonitoring.lastTripFinishStatus.isEmpty {
                                    Text(dayMonitoring.lastTripFinishStatus)
                                        .font(.footnote)
                                        .foregroundColor(.secondary)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                }
                            }

                            Button {
                                dayMonitoring.setEnabled(!dayMonitoring.isEnabled)
                            } label: {
                                Text(dayMonitoring.isEnabled ? t(.disableMonitoring) : t(.enableMonitoring))
                                    .frame(maxWidth: .infinity)
                                    .padding()
                            }
                            .buttonStyle(.borderedProminent)

                            if FeatureFlags.isDeveloperBuild {
                                Text(t(.dayMonitoringNote))
                                    .font(.footnote)
                                    .foregroundColor(.secondary)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                        .padding(.horizontal)
                    }
                    
                    
                    
                    if FeatureFlags.isDeveloperBuild {
                        NavigationLink {
                            TripsArchiveView()
                                .onAppear {
                                    sensorManager.markScreenInteractionInApp()
                                }
                        } label: {
                            Text(t(.viewTripHistory))
                                .frame(maxWidth: .infinity)
                                .padding()
                        }
                        .buttonStyle(.bordered)
                        .padding(.horizontal)
                        .disabled(sensorManager.driverId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                    
                    // ===== Trip finish status =====
                    if !finishStatusText.isEmpty || lastFinishError != nil {
                        
                        VStack(alignment: .leading, spacing: 6) {
                            
                            if !finishStatusText.isEmpty {
                                Text(finishStatusText)
                                    .font(.subheadline)
                                    .fontWeight(.medium)
                                    .foregroundColor(.accentColor)      // цвет кнопки Start
                                    .multilineTextAlignment(.center)    // центрирование текста
                                    .frame(maxWidth: .infinity)         // центр по контейнеру
                                    .padding(.top, 4)
                            }
                            
                            if let lastFinishError {
                                Text(lastFinishError)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .lineLimit(4)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                        .padding(.horizontal)
                        .padding(.top, 6)
                    }
                    
                    if FeatureFlags.isDeveloperBuild {
                        Toggle(t(.indoorTestModeToggle), isOn: $sensorManager.indoorTestMode)
                            .padding(.horizontal)
                        
                        Text(t(.indoorTestModeDescription))
                            .font(.footnote)
                            .foregroundColor(.secondary)
                            .padding(.horizontal)
                    }
                    
                    
                    // ===== Telemetry card =====
                    if FeatureFlags.isDeveloperBuild {
                        Toggle(t(.maneuverTestModeToggle), isOn: $drivingTestMode)
                            .padding(.horizontal)

                        VStack(alignment: .leading, spacing: 8) {
                            if drivingTestMode {

                                Text(t(.maneuverTestTitle))
                                    .font(.headline)

                                Text(t(.maneuverTestDescription))
                                .font(.footnote)
                                .foregroundColor(.secondary)
                                .fixedSize(horizontal: false, vertical: true)

                                Divider()

                                Text(t(.projectedG)).font(.system(.callout))
                                Text(sensorManager.lastProjString)
                                    .font(.system(.callout, design: .monospaced))
                                    .fixedSize(horizontal: false, vertical: true)

                                Text(t(.rotationRate)).font(.system(.callout))
                                Text(sensorManager.lastRotRateString)
                                    .font(.system(.callout, design: .monospaced))

                                Text(t(.userAcceleration)).font(.system(.callout))
                                Text(sensorManager.lastUserAccelString)
                                    .font(.system(.callout, design: .monospaced))

                                Divider()

                                Text(t(.lastEvent)).font(.system(.callout))
                                Text(sensorManager.lastFiredEventString)
                                    .font(.system(.callout, design: .monospaced))
                                    .fixedSize(horizontal: false, vertical: true)

                            } else {
                                
                                
                                infoRow(label: t(.statusLabel), value: sensorManager.statusText)
                                
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(t(.lastFiveErrors))
                                        .font(.system(.callout))
                                        .padding(.top, 4)
                                    
                                    let errText =
                                    sensorManager.lastNetworkErrors.isEmpty
                                    ? "—"
                                    : sensorManager.lastNetworkErrors.joined(separator: "\n")
                                    
                                    ScrollView(.vertical) {
                                        Text(errText)
                                            .font(.system(.callout, design: .monospaced))
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                            .textSelection(.enabled) // удобно копировать
                                    }
                                    .frame(maxWidth: .infinity)
                                    .frame(minHeight: 60, maxHeight: 140) // <-- вот это фиксирует “вменяемый размер”
                                    .padding(8)
                                    .background(Color(.systemBackground).opacity(0.6))
                                    .cornerRadius(8)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 8)
                                            .stroke(Color.secondary.opacity(0.25), lineWidth: 1)
                                    )
                                    
                                    
                                    if !sensorManager.lastNetworkErrors.isEmpty {
                                        Button(t(.clearErrors)) {
                                            sensorManager.clearNetworkErrors()
                                        }
                                        .font(.footnote)
                                        .foregroundColor(.red)
                                    }
                                }
                                
                                infoRow(label: t(.lastGps), value: sensorManager.lastLocationString)
                                infoRow(label: t(.speedKmhLabel), value: sensorManager.lastSpeedString)
                                
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(t(.accelXyz))
                                        .font(.system(.callout))
                                    
                                    Text(sensorManager.lastAccelString)
                                        .font(.system(.callout, design: .monospaced))
                                        .fixedSize(horizontal: false, vertical: true)
                                }
                                
                                infoRow(label: "‖a‖:",
                                        value: sensorManager.accelMagnitudeString)
                                
                                Divider().padding(.vertical, 4)
                                
                            }
                        }
                    
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(.secondarySystemBackground))
                    .cornerRadius(12)
                    .padding(.horizontal)
                    }
                    
                    Spacer()
                    
                }
                .frame(maxWidth: .infinity, alignment: .top)
                .navigationTitle(FeatureFlags.isDeveloperBuild ? "Telemetry" : "")
                
                .toolbar {
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button {
                            sensorManager.markScreenInteractionInApp()
                            showingDriveTelemetryGuide = true
                        } label: {
                            Image(systemName: "questionmark.circle")
                                .font(.title3)
                        }
                        .buttonStyle(.plain)
                        .id(toolbarRefreshToken)
                    }

                    ToolbarItem(placement: .navigationBarTrailing) {
                        HStack(spacing: 8) {

                            if FeatureFlags.isDeveloperBuild {
                                Text(sensorManager.lastDeliveryRoute)
                                    .font(.subheadline.weight(.semibold))
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 6)
                                    .background(Color(.secondarySystemBackground))
                                    .clipShape(Capsule())
                            }

                            Button(t(.settings)) {
                                showingSettings = true
                            }
                        }
                    }
                }
                .onAppear {
                    fetchRecentTrips()
                }
                
                .onAppear {
                    loginInput = sensorManager.driverId
                    
                    

                    // Auto-retry any pending "finishTrip" records saved from previous offline stops.
                    if !didRunStartupRecovery {
                        didRunStartupRecovery = true
                        NetworkManager.shared.retryPendingFinishes { _, _ in
                            // Intentionally silent (UX). Logs, if needed, are handled by NetworkManager.logHandler.
                        }
                    }

                    if !didShowPermissionOnboarding {
                        didShowPermissionOnboarding = true
                        showingPermissionOnboarding = true
                        return
                    }

                    // Mandatory onboarding on first launch (or if driverId was cleared)
                    
                    Task {
                        do {
                            try await sensorManager.ensureDriverReadyForAppLaunch()
                        } catch {
                            let currentDriverId = sensorManager.driverId.trimmingCharacters(in: .whitespacesAndNewlines)

                            if currentDriverId.isEmpty {
                                showingDriverSetup = true
                            } else {
                                print("[DriverSetup] Suppressed automatic username popup because driverId already exists. error=\(error.localizedDescription)")
                            }
                        }

                        await refreshHomeMetrics()
                    }
                }
                .onReceive(NotificationCenter.default.publisher(for: .manualTripAutoFinishDidTrigger)) { note in
                    let reason = (note.userInfo?["reason"] as? String) ?? "auto_finish"
                    handleAutoStopTriggered(reason: reason)
                }
                
                .onReceive(NotificationCenter.default.publisher(for: .driverIdDidChange)) { _ in
                    Task { @MainActor in
                        resetHomeScreenForDriverChange()
                        fetchRecentTrips()
                        await refreshHomeMetrics()
                    }
                }
                
                .onReceive(NotificationCenter.default.publisher(for: .requestDriverChangeFlow)) { _ in
                    if sensorManager.isCollectingNow {
                        showingDriverSetupAfterForcedStop = true
                        handleStopPressed()
                    } else {
                        showingDriverSetup = true
                    }
                }
                
                .sheet(isPresented: $showingSettings) {
                    SettingsView()
                        .environmentObject(sensorManager)
                }

                .fullScreenCover(isPresented: $showingDriverSetup) {
                    DriverSetupView()
                        .environmentObject(sensorManager)
                }
                .fullScreenCover(isPresented: $showingPermissionOnboarding) {
                    PermissionOnboardingView {
                        sensorManager.requestUserPermissionsForTripRecording()
                        didShowPermissionOnboarding = true
                        showingPermissionOnboarding = false
                        showingDriverSetup = false
                    }
                }

                .fullScreenCover(isPresented: $showingDriveTelemetryGuide) {
                    DriveTelemetryGuideView(
                        onBack: {
                            showingDriveTelemetryGuide = false
                            toolbarRefreshToken += 1
                        },
                        onStartFirstTrip: {
                            showingDriveTelemetryGuide = false
                            toolbarRefreshToken += 1
                        }
                    )
                    .environmentObject(languageManager)
                }
                
                .sheet(item: $tripReport, content: tripReportSheet)
                
                .navigationDestination(isPresented: $showingTripsArchiveFromDots) {
                    TripsArchiveView()
                        .environmentObject(sensorManager)
                        .environmentObject(languageManager)
                }
                
                
                // ===== Water glass (full screen) =====
                                .fullScreenCover(isPresented: $showWaterGlass) {
                                    WaterGlassFullScreenView()
                                        .environmentObject(sensorManager)
                                        .environmentObject(sensorManager.waterGameManager)
                                }
                
            }
        }
//        .simultaneousGesture(
//            TapGesture().onEnded {
//                sensorManager.markScreenInteractionInApp()
//            }
//        )
//        .simultaneousGesture(
//            DragGesture(minimumDistance: 0)
//                .onChanged { _ in
//                    sensorManager.markScreenInteractionInApp()
//                }
//        )
    }
    
    @ViewBuilder
    private var driverFatigueAlertView: some View {
        if dashcamManager.cameraMode == .front &&
            dashcamManager.state == .recording {

            switch dashcamManager.driverFatigueState {

            case .critical:
                VStack(spacing: 6) {
                    Text(t(.dmsAlertCriticalTitle))
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(.white)

                    Text(t(.dmsAlertCriticalSubtitle))
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundColor(.white)
                }
                .frame(maxWidth: .infinity)
                .padding(14)
                .background(Color.red.opacity(0.92))
                .cornerRadius(12)

            case .warning:
                Text(t(.dmsAlertWarning))
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(.black)
                    .frame(maxWidth: .infinity)
                    .padding(10)
                    .background(Color.yellow.opacity(0.92))
                    .cornerRadius(10)

            case .drowsy:
                Text(t(.dmsAlertDrowsy))
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(10)
                    .background(Color.orange.opacity(0.92))
                    .cornerRadius(10)

            case .distracted:
                Text(t(.dmsAlertDistracted))
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(10)
                    .background(Color.blue.opacity(0.92))
                    .cornerRadius(10)

            case .normal:
                EmptyView()
            }
        }
    }
    
    @ViewBuilder
    private func tripReportSheet(report: TripReport) -> some View {
        NavigationStack {
            TripReportView(
                report: report,
                createdBatches: stopCreatedBatches,
                deliveredBatches: stopDeliveredBatches,
                wasAutoFinish: lastStopWasAutoFinish,
                refreshPayload: {
                    let latestReport: TripReport = try await withCheckedThrowingContinuation { continuation in
                        NetworkManager.shared.fetchTripReport(
                            deviceId: report.device_id,
                            sessionId: report.session_id,
                            driverId: report.driver_id
                        ) { result in
                            switch result {
                            case .success(let fetchedReport):
                                continuation.resume(returning: fetchedReport)
                            case .failure(let error):
                                continuation.resume(throwing: error)
                            }
                        }
                    }

                    let stats = NetworkManager.shared.getDeliveryStats(sessionId: report.session_id)
                    let delivered = stats.euBatches + stats.ruBatches
                    let created = max(stopCreatedBatches, delivered)

                    return TripReportRefreshPayload(
                        report: latestReport,
                        createdBatches: created,
                        deliveredBatches: delivered
                    )
                }
            )
        }
    }
    
    @ViewBuilder
    private func telemetryStatsGrid(safeKm: Double) -> some View {

        let skidCount = sensorManager.accelInTurnCount + sensorManager.brakeInTurnCount
        let skidSum   = sensorManager.accelInTurnSumIntensity + sensorManager.brakeInTurnSumIntensity
        let skidMax   = max(sensorManager.accelInTurnMaxIntensity, sensorManager.brakeInTurnMaxIntensity)

        GeometryReader { geo in
            let totalW = geo.size.width

            // spacing между колонками в Grid
            let spacing: CGFloat = 12

            // Ширина каждой numeric-колонки (5 штук).
            // Подбираем диапазон: чтобы влезали 3-значные с запятой и заголовки.
            let numericW = min(78, max(58, (totalW - 160) / 5))

            // Первая колонка (Событие) — всё, что осталось
            let eventW = max(110, totalW - (numericW * 5) - (spacing * 5))

            Grid(alignment: .leading, horizontalSpacing: spacing, verticalSpacing: 10) {

                // Header
                GridRow {
                    statsHeaderCell(.event, width: eventW, align: .leading)

                    statsHeaderCell(.count,      width: numericW, align: .trailing)
                    statsHeaderCell(.sumG,       width: numericW, align: .trailing)
                    statsHeaderCell(.maxG,       width: numericW, align: .trailing)
                    statsHeaderCell(.countPerKm, width: numericW, align: .trailing)
                    statsHeaderCell(.gPerKm,     width: numericW, align: .trailing)
                }
                .font(.caption)
                .foregroundColor(.secondary)

                GridRow { Divider().gridCellColumns(6) }
                    .padding(.vertical, 2)

                // Rows
                statsGridRow(t(.statsBraking),
                             count: sensorManager.brakeCount,
                             sumG: sensorManager.brakeSumIntensity,
                             maxG: sensorManager.brakeMaxIntensity,
                             safeKm: safeKm,
                             eventW: eventW, numericW: numericW)

                statsGridRow(t(.statsAcceleration),
                             count: sensorManager.accelCount,
                             sumG: sensorManager.accelSumIntensity,
                             maxG: sensorManager.accelMaxIntensity,
                             safeKm: safeKm,
                             eventW: eventW, numericW: numericW)

                statsGridRow(t(.statsRoadAnomalies),
                             count: sensorManager.roadCount,
                             sumG: sensorManager.roadSumIntensity,
                             maxG: sensorManager.roadMaxIntensity,
                             safeKm: safeKm,
                             eventW: eventW, numericW: numericW)

                statsGridRow(t(.statsTurns),
                             count: sensorManager.turnCount,
                             sumG: sensorManager.turnSumIntensity,
                             maxG: sensorManager.turnMaxIntensity,
                             safeKm: safeKm,
                             eventW: eventW, numericW: numericW)

                statsGridRow(t(.statsSkid),
                             count: skidCount,
                             sumG: skidSum,
                             maxG: skidMax,
                             safeKm: safeKm,
                             eventW: eventW, numericW: numericW)
            }
        }
        .frame(height: 260) // можно убрать и дать авто, но GeometryReader требует высоту
    }
    
    
    private static let statsNumberFormatter: NumberFormatter = {
        let nf = NumberFormatter()
        nf.locale = .current
        nf.numberStyle = .decimal
        nf.minimumFractionDigits = 2
        nf.maximumFractionDigits = 2
        return nf
    }()

    private func formatted(_ v: Double) -> String {
        ContentView.statsNumberFormatter.string(from: NSNumber(value: v))
        ?? String(format: "%.2f", v)
    }

    @ViewBuilder
    private func statsHeaderCell(_ col: StatsColumn, width: CGFloat, align: Alignment) -> some View {
        HStack(spacing: 4) {
            Text(col.title)
                .lineLimit(1)
                .minimumScaleFactor(0.85)

            Button { statsHelpColumn = col } label: {
                Image(systemName: "questionmark.circle")
                    .font(.caption)
            }
            .buttonStyle(.plain)
            .foregroundColor(.secondary)
        }
        .frame(width: width, alignment: align)
    }

    @ViewBuilder
    private func statsGridRow(_ title: String,
                              count: Int,
                              sumG: Double,
                              maxG: Double,
                              safeKm: Double,
                              eventW: CGFloat,
                              numericW: CGFloat) -> some View {

        let countPerKm = Double(count) / safeKm
        let sumPerKm   = sumG / safeKm

        GridRow {
            Text(title)
                .foregroundColor(.secondary)
                .frame(width: eventW, alignment: .leading)
                .lineLimit(2)

            numCell("\(count)", width: numericW, strong: true)

            numCell(formatted(sumG), width: numericW, strong: true)
            numCell(formatted(maxG), width: numericW, strong: true, isMax: true)

            numCell(formatted(countPerKm), width: numericW, strong: true)
            numCell(formatted(sumPerKm),   width: numericW, strong: true)
        }
    }

    @ViewBuilder
    private func numCell(_ text: String, width: CGFloat, strong: Bool, isMax: Bool = false) -> some View {
        Text(text)
            .fontWeight(strong ? .semibold : .regular)
            .monospacedDigit()
            .lineLimit(1)
            .minimumScaleFactor(0.75)
            .allowsTightening(true)
            .foregroundColor(isMax && text != "0" && text != "0,00" ? .red : .primary)
            .frame(width: width, alignment: .trailing)
    }
    private func handleAutoStopTriggered(reason: String) {
        // то же, что Stop, но без haptic и без повторной блокировки, если уже стопимся
        guard !isStopFlowActive && !stopLockedUntilNextStart else { return }
        if dashcamManager.shouldBlockTripStopButton {
            return
        }
        lastStopWasAutoFinish = true
        lastFinishReason = reason
        lastFinishWasAuto = true

        // Не вибрируем на авто
        stopLockedUntilNextStart = true
        isStopFlowActive = true

        let reqId = UUID()
        stopRequestId = reqId

        tripReport = nil
        lastFinishError = nil
        finishStatusText = lastStopWasAutoFinish ? autoFinishSearchingMessage : searchingServerMessage

        stopSessionIdForReport = sensorManager.currentSessionId
        stopCreatedBatches = sensorManager.createdBatchesCount
        let stats = NetworkManager.shared.getDeliveryStats(sessionId: sensorManager.currentSessionId)
        stopDeliveredBatches = stats.euBatches + stats.ruBatches

        autoRefreshTask?.cancel()
        autoRefreshTask = nil

        let t0 = Date()
        self.stopT0 = t0
        
#if DEBUG

        print("[AUTO-STOP] t0 Auto stop at \(t0) session=\(sensorManager.currentSessionId) reason=\(reason)")

#endif
        let deviceContextSnapshot = sensorManager.makeDeviceContextPayload()
        let tailActivityContext = sensorManager.makeTailActivityContext(windowSec: 120.0)
        
        let tripOwnerDriverId = sensorManager.currentTripOwnerDriverId
        self.stopTripOwnerDriverId = tripOwnerDriverId

        DispatchQueue.main.asyncAfter(deadline: .now() + stopFinishUiTimeoutSec) {
            guard self.stopRequestId == reqId else { return }
            guard self.tripReport == nil else { return }
            guard self.isStopFlowActive else { return }
            self.finishStatusText = self.offlineFinishMessage
            self.lastFinishError = nil
            self.isStopFlowActive = false
        }

        sensorManager.stopAndDrainUploads { _ in
            DispatchQueue.main.async {
                self.stopSessionIdForReport = sensorManager.currentSessionId
                self.stopCreatedBatches = sensorManager.createdBatchesCount

                let stats = NetworkManager.shared.getDeliveryStats(sessionId: sensorManager.currentSessionId)
                self.stopDeliveredBatches = stats.euBatches + stats.ruBatches
            }

            let t2 = Date()
            let dt2 = t2.timeIntervalSince(self.stopT0 ?? t2)
#if DEBUG

            print("[AUTO-STOP] t2 FinishTrip request started dt=\(dt2)s session=\(sensorManager.currentSessionId)")

#endif

            let stopDurationSecSnapshot = Double(sensorManager.currentTripElapsedSec)
            let stopEndedAtISO = ISO8601DateFormatter().string(from: Date())
            let clientMetrics = makeClientTripMetrics(durationSec: stopDurationSecSnapshot)

            NetworkManager.shared.finishTrip(
                sessionId: sensorManager.currentSessionId,
                driverId: tripOwnerDriverId,
                deviceId: sensorManager.deviceIdForDisplay,
                trackingMode: "single_trip",
                transportMode: nil,
                clientEndedAt: stopEndedAtISO,
                tripDurationSec: stopDurationSecSnapshot,
                finishReason: self.lastFinishReason,
                clientMetrics: clientMetrics,
                deviceContext: deviceContextSnapshot,
                tailActivityContext: tailActivityContext
            ) { result in

                let t3 = Date()
                let dt3 = t3.timeIntervalSince(self.stopT0 ?? t3)

#if DEBUG
switch result {
case .success:
    print("[AUTO-STOP] t3 FinishTrip SUCCESS dt=\(dt3)s")

case .failure(let error):
    print("[AUTO-STOP] t3 FinishTrip ERROR dt=\(dt3)s err=\(error.localizedDescription)")
}
#endif

                DispatchQueue.main.async {
                    guard self.stopRequestId == reqId else { return }

                    switch result {
                    case .success(let report):
                        // На авто лучше без дополнительной вибрации (можно оставить success если хочешь)
                        saveTripReportToDisk(report)
                        self.tripReport = report
                        Task { @MainActor in
                            await dashcamManager.restoreImplicitTripAfterManualTripStopIfNeeded()
                        }
                        
                        Task { @MainActor in
                            sensorManager.finalizeTripOwnerAfterFinish()
                            sensorManager.applyPendingDriverIdIfNeeded()
                            if showingDriverSetupAfterForcedStop {
                                showingDriverSetupAfterForcedStop = false
                                showingDriverSetup = true
                            }
                        }
                        Task { @MainActor in
                            fetchRecentTrips()
                            await refreshHomeMetrics()
                        }
                        self.lastFinishError = nil
                        self.finishStatusText = self.lastStopWasAutoFinish ? t(.autoFinished) : ""
                        self.isStopFlowActive = false

                    case .failure(let error):
                        if self.finishStatusText.isEmpty || self.finishStatusText == t(.tripFinishingGettingReport) {
                            self.finishStatusText = self.offlineFinishMessage
                        }
                        self.lastFinishError = "FinishTrip error: \(error.localizedDescription)"
                        self.isStopFlowActive = false
                    }
                }
            }
        }
    }

    // MARK: - Stop flow (Stop → drain → finishTrip) with 5s UX timeout
    private func handleStopPressed(skipHaptics: Bool = false) {
        // Hard guard: if user somehow can tap again, ignore without vibration
        guard !isStopFlowActive && !stopLockedUntilNextStart else { return }
        if !skipHaptics {
            // ручной стоп
            lastStopWasAutoFinish = false
        }
        
        lastFinishReason = "manual_stop"
        lastFinishWasAuto = false
        

        if !skipHaptics {
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        }

        // Lock Stop until next Start (prevents repeated vibration in offline case)
        stopLockedUntilNextStart = true
        isStopFlowActive = true

        // Новый stop request (защита от гонок)
        let reqId = UUID()
        stopRequestId = reqId

        // Сбрасываем показ отчёта/ошибок от предыдущей поездки
        tripReport = nil
        lastFinishError = nil
        finishStatusText = t(.tripFinishingGettingReport)
        
        // Completeness snapshot at Stop
        stopSessionIdForReport = sensorManager.currentSessionId
        stopCreatedBatches = sensorManager.createdBatchesCount
        let stats = NetworkManager.shared.getDeliveryStats(sessionId: sensorManager.currentSessionId)
        stopDeliveredBatches = stats.euBatches + stats.ruBatches

        // cancel previous auto-refresh (if any)
        autoRefreshTask?.cancel()
        autoRefreshTask = nil

        
        let t0 = Date()
        self.stopT0 = t0
        
#if DEBUG

        print("[STOP] t0 Stop pressed at \(t0) session=\(sensorManager.currentSessionId)")

#endif
        let deviceContextSnapshot = sensorManager.makeDeviceContextPayload()
        let tailActivityContext = sensorManager.makeTailActivityContext(windowSec: 120.0)

        

        // 1) UX-таймаут 5 секунд: если отчёт не пришёл — показываем “офлайн-успех”
        DispatchQueue.main.asyncAfter(deadline: .now() + stopFinishUiTimeoutSec) {
            guard self.stopRequestId == reqId else { return }          // уже новая операция
            guard self.tripReport == nil else { return }               // отчёт уже пришёл
            guard self.isStopFlowActive else { return }                // уже завершили flow
            self.finishStatusText = self.offlineFinishMessage
            self.lastFinishError = nil
            // Разблокируем flow (но Stop всё равно останется заблокирован до следующего Start)
            self.isStopFlowActive = false
        }

        // 2) Останавливаем сенсоры + пытаемся дожать ingest очередь (best-effort)
        sensorManager.stopAndDrainUploads { _ in
            // IMPORTANT: re-snapshot AFTER stopAll()->flushBuffersNow() had a chance to create the final batch
            DispatchQueue.main.async {
                self.stopSessionIdForReport = sensorManager.currentSessionId
                self.stopCreatedBatches = sensorManager.createdBatchesCount

                let stats = NetworkManager.shared.getDeliveryStats(sessionId: sensorManager.currentSessionId)
                self.stopDeliveredBatches = stats.euBatches + stats.ruBatches
            }

            // t2: finishTrip request started
            let t2 = Date()
            let dt2 = t2.timeIntervalSince(self.stopT0 ?? t2)
#if DEBUG

            print("[STOP] t2 FinishTrip request started dt=\(dt2)s session=\(sensorManager.currentSessionId)")

#endif

            let stopDurationSecSnapshot = Double(sensorManager.currentTripElapsedSec)
            let stopEndedAtISO = ISO8601DateFormatter().string(from: Date())
            let clientMetrics = makeClientTripMetrics(durationSec: stopDurationSecSnapshot)
            let tripOwnerDriverId = sensorManager.currentTripOwnerDriverId
            self.stopTripOwnerDriverId = tripOwnerDriverId

            NetworkManager.shared.finishTrip(
                sessionId: sensorManager.currentSessionId,
                driverId: tripOwnerDriverId,
                deviceId: sensorManager.deviceIdForDisplay,
                trackingMode: "single_trip",
                transportMode: nil,
                clientEndedAt: stopEndedAtISO,
                tripDurationSec: stopDurationSecSnapshot,
                finishReason: self.lastFinishReason,
                clientMetrics: clientMetrics,
                deviceContext: deviceContextSnapshot,
                tailActivityContext: tailActivityContext
            ) { result in
                
                // t3: finishTrip completed (success/error)
                let t3 = Date()
                let dt3 = t3.timeIntervalSince(self.stopT0 ?? t3)
#if DEBUG

                switch result {
                case .success:
                    print("[STOP] t3 FinishTrip SUCCESS dt=\(dt3)s")
                case .failure(let error):
                    print("[STOP] t3 FinishTrip ERROR dt=\(dt3)s err=\(error.localizedDescription)")
                }

#endif
                DispatchQueue.main.async {
                    guard self.stopRequestId == reqId else { return } // устаревший ответ

                    switch result {
                    case .success(let report):
                        UINotificationFeedbackGenerator().notificationOccurred(.success)
                        saveTripReportToDisk(report)
                        self.tripReport = report
                        Task { @MainActor in
                            await dashcamManager.restoreImplicitTripAfterManualTripStopIfNeeded()
                        }
                        Task { @MainActor in
                            sensorManager.finalizeTripOwnerAfterFinish()
                            sensorManager.applyPendingDriverIdIfNeeded()
                            if showingDriverSetupAfterForcedStop {
                                showingDriverSetupAfterForcedStop = false
                                showingDriverSetup = true
                            }
                        }
                        Task { @MainActor in
                            fetchRecentTrips()
                            await refreshHomeMetrics()
                        }
                        self.lastFinishError = nil
                        if self.lastFinishWasAuto {
                            self.finishStatusText = t(.autoFinished)
                        } else {
                            self.finishStatusText = ""
                        }
                        self.isStopFlowActive = false

                        // Auto-refresh report while completeness < 100%
                        let localReqId = reqId
                        autoRefreshTask?.cancel()
                        autoRefreshTask = Task {
                            while !Task.isCancelled {
                                // stop if a new Stop operation started
                                if self.stopRequestId != localReqId { return }

                                let created = self.stopCreatedBatches

                                let stats = NetworkManager.shared.getDeliveryStats(
                                    sessionId: self.stopSessionIdForReport
                                )
                                let delivered = stats.euBatches + stats.ruBatches
                                
                                // If complete, stop polling
                                if created > 0, delivered >= created {
                                    return
                                }

                                // Poll interval
                                try? await Task.sleep(nanoseconds: 5_000_000_000)

                                NetworkManager.shared.fetchTripReport(
                                    deviceId: sensorManager.deviceIdForDisplay,
                                    sessionId: self.stopSessionIdForReport,
                                    driverId: self.stopTripOwnerDriverId
                                ) { result in
                                    guard self.stopRequestId == localReqId else { return }
                                    if case .success(let updated) = result {
                                        DispatchQueue.main.async {
                                            self.tripReport = updated
                                        }
                                    }
                                }
                            }
                        }

                    case .failure(let error):
                        UINotificationFeedbackGenerator().notificationOccurred(.warning)

                        if self.finishStatusText.isEmpty || self.finishStatusText == t(.tripFinishingGettingReport) {
                            self.finishStatusText = self.offlineFinishMessage
                        }

                        self.lastFinishError = "FinishTrip error: \(error.localizedDescription)"
                        self.isStopFlowActive = false
                    }
                }
            }
        }

        
    }

    // MARK: - Helper row
    private func infoRow(label: String, value: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text(label)
                .frame(width: 110, alignment: .leading)

            Text(value)
                .lineLimit(nil)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func formatElapsed(_ sec: Int) -> String {
        let h = sec / 3600
        let m = (sec % 3600) / 60
        let s = sec % 60

        if h > 0 {
            return String(format: "%d:%02d:%02d", h, m, s)
        } else {
            return String(format: "%02d:%02d", m, s)
        }
    }
    
    private func localizedDriverLevel(_ raw: String?) -> String? {
        guard let raw else { return nil }

        switch raw {
        case "Risky Driver":
            return t(.driverLevelRisky)
        case "Average Driver":
            return t(.driverLevelAverage)
        case "Safe Driver":
            return t(.driverLevelSafe)
        case "Pro Driver":
            return t(.driverLevelPro)
        default:
            return raw
        }
    }


    // MARK: - Persist TripReport for Archive
    private func saveTripReportToDisk(_ report: TripReport) {
        do {
            let enc = JSONEncoder()
            enc.outputFormatting = [.prettyPrinted, .sortedKeys]
            let data = try enc.encode(report)

            let fm = FileManager.default
            let docs = try fm.url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: true)

            // 1) Folder-based (на будущее)
            let dir = docs.appendingPathComponent("TripReports", isDirectory: true)
            if !fm.fileExists(atPath: dir.path) {
                try fm.createDirectory(at: dir, withIntermediateDirectories: true)
            }
            let file1 = dir.appendingPathComponent("\(report.session_id).json")

            // 2) Flat (совместимость — если архив ищет в корне)
            let file2 = docs.appendingPathComponent("\(report.session_id).json")
            let file3 = docs.appendingPathComponent("trip_report_\(report.session_id).json")

            try data.write(to: file1, options: [.atomic])
            try data.write(to: file2, options: [.atomic])
            try data.write(to: file3, options: [.atomic])

#if DEBUG
            print("TripReport saved:", report.session_id)
#endif
        } catch {
#if DEBUG
            print("TripReport save error:", error)
#endif
        }



    }
    @ViewBuilder
    private func statsHelpOverlay() -> some View {
        if let col = statsHelpColumn {
            ZStack {
                // Тап по любому месту вне окна закрывает подсказку
                Color.black.opacity(0.001)
                    .ignoresSafeArea()
                    .onTapGesture { statsHelpColumn = nil }

                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text(col.title)
                            .font(.headline)
                        Spacer()
                        Button {
                            statsHelpColumn = nil
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .font(.title3)
                                .foregroundColor(.secondary)
                        }
                        .buttonStyle(.plain)
                    }

                    Text(col.help)
                        .font(.body)
                        .foregroundColor(.secondary)

                    // (Опционально) можно вообще без кнопки OK, раз есть тап вне окна + крестик
                    // Button("OK") { statsHelpColumn = nil }
                }
                .padding(14)
                .background(.ultraThinMaterial)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(Color(.separator).opacity(0.25), lineWidth: 1)
                )
                .padding(.horizontal, 20)
                // Важно: чтобы тап по самой карточке НЕ закрывал (иначе будет раздражать)
                .onTapGesture { }
            }
            .transition(.opacity)
            .animation(.easeOut(duration: 0.12), value: statsHelpColumn)
        }
    }


}

private extension ContentView {
    
    var effectiveRecentTripColors: [String] {
        let colors = homeMetrics?.recent_trip_colors ?? []

        guard !colors.isEmpty else { return [] }
        guard recentTrips.count > 0 else { return [] }

        return Array(colors.prefix(recentTrips.count))
    }
    
    var tripSeriesTitle: String? {
        let colors = effectiveRecentTripColors
        guard !colors.isEmpty else { return nil }

        let normalized = colors.map { $0.lowercased() }
        let greenCount = normalized.filter { $0 == "green" }.count
        let hasRed = normalized.contains("red")

        if normalized.count >= 5 && greenCount == 5 {
            return t(.excellentSeriesFiveOfFive)
        }

        if hasRed {
            return t(.tripCanBeImproved)
        }

        if greenCount == 4 {
            return t(.goodSeriesFourOfFive)
        }

        if greenCount == 3 {
            return t(.decentSeriesThreeOfFive)
        }

        if greenCount >= 4 {
            return t(.goodSeriesFourOfFive)
        }

        if greenCount >= 3 {
            return t(.decentSeriesThreeOfFive)
        }

        return t(.tripCanBeImproved)
    }

    var tripSeriesHint: String? {
        let colors = effectiveRecentTripColors
        guard !colors.isEmpty else { return nil }

        let lastColor = colors.first?.lowercased() ?? ""
        if lastColor == "green" {
            return t(.keepGreenSeriesNextTrip)
        } else {
            return t(.restoreGreenSeriesNextTrip)
        }
    }

    var homeScoreText: String {
        if let score = homeMetrics?.avg_score {
            return "\(Int(round(score))) / 100"
        }

        if let report = tripReport {
            let score = report.score_v2 ?? report.trip_score
            return "\(Int(round(score))) / 100"
        }

        return "—"
    }

    var homePrimarySubtitle: String {
        if isTripActive {
            return t(.tripRecordingInProgress)
        }

        if let rawLevel = homeMetrics?.driver_level,
           let level = localizedDriverLevel(rawLevel),
           !level.isEmpty {
            if homeMetrics?.rating_status == "forming" {
                return "\(t(.currentDrivingLevel)) \(level)"
            }
            return level
        }

        return t(.readyToStartTrip)
    }

    var homeSecondarySubtitle: String? {
        return nil
    }
}

#Preview {
    ContentView()
        .environmentObject(SensorManager.shared)
        .environmentObject(DayMonitoringManager(sensorManager: SensorManager.shared))
        .environmentObject(LanguageManager())
}

func infoRow(_ title: String, _ value: String) -> some View {
    HStack {
        Text(title)
        Spacer()
        Text(value)
    }
}
private struct MetricBarRow: View {
    let title: String
    let valuePerKm: Double   // g/km
    let level: Int           // 0...5
    let color: Color

    var body: some View {
        HStack(spacing: 10) {
            Text(title)
                .frame(width: 110, alignment: .leading)

            HStack(spacing: 3) {
                ForEach(0..<5, id: \.self) { i in
                    RoundedRectangle(cornerRadius: 2)
                        .fill(i < level ? color : Color.gray.opacity(0.25))
                        .frame(width: 16, height: 10)
                }
            }

            Spacer()

            Text("\(valuePerKm, specifier: "%.2f") g/km")
                .foregroundColor(.secondary)
                .font(.system(.caption, design: .monospaced))
        }
        .font(.system(.body, design: .monospaced))
    }
}

private func barLevelAndColor(_ gPerKm: Double) -> (level: Int, color: Color) {
    // Шкала можно подкрутить позже. Сейчас сделана “инженерно-понятная”.
    // 0..0.10 — отлично, 0.10..0.20 — норм, 0.20..0.30 — заметно, 0.30..0.40 — агрессивно, >0.40 — очень агрессивно.
    switch gPerKm {
    case ..<20.00: return (1, .green)
    case ..<50.00: return (2, .green)
    case ..<80.00: return (3, .yellow)
    case ..<120.00: return (4, .orange)
    default:      return (5, .red)
    }
}

private func smoothnessScore(accelGPerKm: Double, brakeGPerKm: Double, turnGPerKm: Double, roadGPerKm: Double) -> Int {
    // Чем меньше g/km — тем лучше. Сводим в 0..100.
    // Весами можно управлять. Сейчас: тормоз/разгон/поворот важнее дороги.
    let weighted = 0.30 * brakeGPerKm + 0.25 * accelGPerKm + 0.25 * turnGPerKm + 0.20 * roadGPerKm

    // Простая, но устойчивая шкала: 0.00 -> ~100, 0.50 -> ~0.
    let raw = 100.0 - (weighted / 0.50) * 100.0
    return Int(max(0, min(100, raw)).rounded())
}

// MARK: - DriveTelemetry Guide

private struct DriveTelemetryGuideSlide {
    let landscapeImage: String
    let portraitImage: String
    let titleKey: LocalizationKey
    let subtitleKey: LocalizationKey
}

private let driveTelemetryGuideSlides: [DriveTelemetryGuideSlide] = [
    .init(landscapeImage: "guide_bg_1", portraitImage: "guide_bg_1_portrait", titleKey: .guide1Title, subtitleKey: .guide1Subtitle),
    .init(landscapeImage: "guide_bg_2", portraitImage: "guide_bg_2_portrait", titleKey: .guide2Title, subtitleKey: .guide2Subtitle),
    .init(landscapeImage: "guide_bg_3", portraitImage: "guide_bg_3_portrait", titleKey: .guide3Title, subtitleKey: .guide3Subtitle),
    .init(landscapeImage: "guide_bg_4", portraitImage: "guide_bg_4_portrait", titleKey: .guide4Title, subtitleKey: .guide4Subtitle),
    .init(landscapeImage: "guide_bg_5", portraitImage: "guide_bg_5_portrait", titleKey: .guide5Title, subtitleKey: .guide5Subtitle),
    .init(landscapeImage: "guide_bg_6", portraitImage: "guide_bg_6_portrait", titleKey: .guide6Title, subtitleKey: .guide6Subtitle),
    .init(landscapeImage: "guide_bg_7", portraitImage: "guide_bg_7_portrait", titleKey: .guide7Title, subtitleKey: .guide7Subtitle)
]

private func guideColor(_ red: Double, _ green: Double, _ blue: Double, _ opacity: Double = 1.0) -> Color {
    Color(red: red / 255.0, green: green / 255.0, blue: blue / 255.0).opacity(opacity)
}

struct DriveTelemetryGuideView: View {
    @EnvironmentObject var languageManager: LanguageManager

    let onBack: () -> Void
    let onStartFirstTrip: () -> Void

    @State private var page = 0
    @State private var demoRating = 90
    @State private var lastIsPortrait: Bool?

    private func t(_ key: LocalizationKey) -> String {
        languageManager.text(key)
    }

    private func setPage(_ newPage: Int) {
        let clamped = max(0, min(driveTelemetryGuideSlides.count - 1, newPage))
        guard clamped != page else { return }

        withAnimation(.easeInOut(duration: 0.22)) {
            page = clamped
        }
    }

    private func handleSwipe(_ translationWidth: CGFloat) {
        if translationWidth < -45 {
            setPage(page + 1)
        } else if translationWidth > 45 {
            setPage(page - 1)
        }
    }

    var body: some View {
        GeometryReader { geometry in
            let isPortrait = geometry.size.height >= geometry.size.width
            let isLastPage = page == driveTelemetryGuideSlides.count - 1
            let slide = driveTelemetryGuideSlides[page]

            ZStack {
                DriveTelemetryGuidePageView(
                    page: page,
                    imageName: isPortrait ? slide.portraitImage : slide.landscapeImage,
                    title: t(slide.titleKey),
                    subtitle: t(slide.subtitleKey),
                    isPortrait: isPortrait,
                    demoRating: demoRating
                )
                .environmentObject(languageManager)
                .id("\(page)-\(isPortrait ? "portrait" : "landscape")")
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 28, coordinateSpace: .local)
                        .onEnded { value in
                            handleSwipe(value.translation.width)
                        }
                )
                .ignoresSafeArea()

                VStack {
                    HStack {
                        Button {
                            onBack()
                        } label: {
                            ZStack(alignment: .topLeading) {
                                Text(t(.guideClose))
                                    .font(.system(size: 16, weight: .semibold))
                                    .foregroundColor(.white.opacity(0.92))
                            }
                            .frame(width: 112, height: 44, alignment: .topLeading)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .zIndex(20)

                        Spacer()

                        Text("\(page + 1)/\(driveTelemetryGuideSlides.count)")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(.white.opacity(0.92))
                    }
                    .padding(.horizontal, 14)
                    .padding(.top, 0)
                    .offset(y: isPortrait ? -24 : 8)

                    Spacer()

                    VStack(spacing: 12) {
                        DriveTelemetryGuideDots(currentPage: page, pageCount: driveTelemetryGuideSlides.count)

                        HStack(spacing: 12) {
                            if page > 0 {
                                Button {
                                    setPage(page - 1)
                                } label: {
                                    Text(t(.guideBack))
                                        .font(.system(size: 16, weight: .semibold))
                                        .foregroundColor(.white)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 12)
                                }
                                .background(Color.white.opacity(0.16))
                                .clipShape(RoundedRectangle(cornerRadius: 28))
                            }

                            Button {
                                if isLastPage {
                                    onStartFirstTrip()
                                } else {
                                    setPage(page + 1)
                                }
                            } label: {
                                Text(isLastPage ? t(.guideGetRating) : t(.guideNext))
                                    .font(.system(size: 16, weight: .semibold))
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                            }
                            .background(guideColor(10, 132, 255))
                            .clipShape(RoundedRectangle(cornerRadius: 28))
                        }
                    }
                    .padding(.horizontal, 18)
                    .padding(.bottom, 18)
                }
                .offset(y: isPortrait ? 10 : 0)
            }
            .background(Color.black)
            .onAppear {
                lastIsPortrait = isPortrait
            }
            .onChange(of: page) { newPage in
                if newPage != driveTelemetryGuideSlides.count - 1 {
                    demoRating = 90
                    lastIsPortrait = isPortrait
                }
            }
            .onChange(of: isPortrait) { newValue in
                if page == driveTelemetryGuideSlides.count - 1,
                   let previous = lastIsPortrait,
                   previous != newValue {
                    demoRating = min(demoRating + 1, 99)
                }
                lastIsPortrait = newValue
            }
        }
    }
}


private struct DriveTelemetryGuidePageView: View {
    @EnvironmentObject var languageManager: LanguageManager

    let page: Int
    let imageName: String
    let title: String
    let subtitle: String
    let isPortrait: Bool
    let demoRating: Int

    private func t(_ key: LocalizationKey) -> String {
        languageManager.text(key)
    }

    var body: some View {
        ZStack {
            Image(imageName)
                .resizable()
                .scaledToFit()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.black)
                .ignoresSafeArea()

            Color.black.opacity(isPortrait ? 0.36 : 0.28)
                .ignoresSafeArea()

            VStack(alignment: .leading, spacing: 0) {
                Text("\(page + 1)")
                    .font(.system(size: 26, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(guideColor(10, 132, 255))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .offset(y: 5)

                Spacer().frame(height: 16)

                Text(title)
                    .font(.system(size: isPortrait ? 34 : 30, weight: .bold))
                    .lineSpacing(isPortrait ? 2 : 1)
                    .foregroundColor(.white)
                    .fixedSize(horizontal: false, vertical: true)

                Spacer().frame(height: 12)

                Text(subtitle)
                    .font(.system(size: isPortrait ? 19 : 18, weight: .regular))
                    .lineSpacing(isPortrait ? 5 : 3)
                    .foregroundColor(.white.opacity(0.92))
                    .fixedSize(horizontal: false, vertical: true)

                Spacer()

                if page == 6 {
                    HStack(spacing: 0) {
                        DriveTelemetryGuideIconLabel(icon: "🛡", label: t(.guideRatingSafety), isPortrait: isPortrait)
                        DriveTelemetryGuideIconLabel(icon: "👁", label: t(.guideRatingAttention), isPortrait: isPortrait)
                        DriveTelemetryGuideIconLabel(icon: "🛞", label: t(.guideRatingSmoothness), isPortrait: isPortrait)
                        DriveTelemetryGuideIconLabel(icon: "🏁", label: t(.guideRatingSpeed), isPortrait: isPortrait)
                    }
                    .frame(maxWidth: .infinity)
                } else {
                    bottomContent
                }
            }
            .padding(.leading, isPortrait ? 28 : 84)
            .padding(.trailing, isPortrait ? 28 : 84)
            .padding(.top, isPortrait ? 60 : 10)
            .padding(.bottom, isPortrait ? 190 : 125)

            if page == 6 {
                VStack {
                    HStack {
                        if isPortrait {
                            Spacer()
                            DriveTelemetryGuideRatingCircle(rating: demoRating, isPortrait: isPortrait)
                            Spacer()
                        } else {
                            Spacer()
                            DriveTelemetryGuideRatingCircle(rating: demoRating, isPortrait: isPortrait)
                                .padding(.trailing, 96)
                        }
                    }
                    Spacer()
                }
                .padding(.top, isPortrait ? 320 : 38)
            }
        }
    }

    @ViewBuilder
    private var bottomContent: some View {
        switch page {
        case 1:
            responsiveCards([
                ("⚠️", t(.guideSafetyBraking), guideColor(10, 132, 255)),
                ("🚀", t(.guideSafetyAcceleration), guideColor(10, 132, 255)),
                ("↪️", t(.guideSafetyTurns), guideColor(10, 132, 255)),
                ("🏁", t(.guideSafetySpeeding), guideColor(10, 132, 255))
            ])
        case 2:
            responsiveCards([
                ("🙂", t(.guideFatigueNormal), guideColor(85, 242, 122)),
                ("😟", t(.guideFatigueWarning), guideColor(255, 194, 71)),
                ("😴", t(.guideFatigueMicrosleep), guideColor(255, 69, 58)),
                ("🚨", t(.guideFatigueAlert), guideColor(255, 69, 58))
            ])
        case 3:
            HStack(spacing: 0) {
                DriveTelemetryGuideIconLabel(icon: "🎥", label: t(.guideCrashBefore), isPortrait: isPortrait)
                DriveTelemetryGuideIconLabel(icon: "🛡", label: t(.guideCrashProtected), isPortrait: isPortrait)
                DriveTelemetryGuideIconLabel(icon: "🎥", label: t(.guideCrashAfter), isPortrait: isPortrait)
            }
            .frame(maxWidth: .infinity)
        case 4:
            responsiveCards([
                ("📷", t(.guideDashcamRoad), guideColor(10, 132, 255)),
                ("👤", t(.guideDashcamDriver), guideColor(10, 132, 255)),
                ("📁", t(.guideDashcamArchive), guideColor(10, 132, 255))
            ])
        case 5:
            responsiveCards([
                ("🔔", t(.guideFamilyNotifications), guideColor(10, 132, 255)),
                ("📍", t(.guideFamilyRoute), guideColor(10, 132, 255)),
                ("🛡", t(.guideFamilySafety), guideColor(10, 132, 255))
            ])
        default:
            HStack(spacing: 0) {
                DriveTelemetryGuideIconLabel(icon: "📹", label: t(.guideIconVideo), isPortrait: isPortrait)
                DriveTelemetryGuideIconLabel(icon: "🚗", label: t(.guideIconDriving), isPortrait: isPortrait)
                DriveTelemetryGuideIconLabel(icon: "🛡", label: t(.guideIconSafety), isPortrait: isPortrait)
                DriveTelemetryGuideIconLabel(icon: "📊", label: t(.guideIconStats), isPortrait: isPortrait)
            }
            .frame(maxWidth: .infinity)
        }
    }

    @ViewBuilder
    private func responsiveCards(_ items: [(String, String, Color)]) -> some View {
        if isPortrait {
            VStack(spacing: 10) {
                ForEach(items.indices, id: \.self) { index in
                    DriveTelemetryGuideFeatureCard(
                        icon: items[index].0,
                        text: items[index].1,
                        accentColor: items[index].2,
                        isPortrait: isPortrait
                    )
                }
            }
        } else {
            HStack(spacing: 10) {
                ForEach(items.indices, id: \.self) { index in
                    DriveTelemetryGuideCompactFeatureCard(
                        icon: items[index].0,
                        text: items[index].1,
                        accentColor: items[index].2
                    )
                }
            }
        }
    }
}

private struct DriveTelemetryGuideRatingCircle: View {
    @EnvironmentObject var languageManager: LanguageManager

    let rating: Int
    let isPortrait: Bool

    private func t(_ key: LocalizationKey) -> String {
        languageManager.text(key)
    }

    var body: some View {
        let size: CGFloat = isPortrait ? 170 : 160

        ZStack {
            Circle()
                .trim(from: 0, to: 0.75)
                .stroke(Color.white.opacity(0.18), style: StrokeStyle(lineWidth: 18, lineCap: .round))
                .rotationEffect(.degrees(135))

            Circle()
                .trim(from: 0, to: 0.75)
                .stroke(guideColor(85, 242, 122), style: StrokeStyle(lineWidth: 18, lineCap: .round))
                .rotationEffect(.degrees(135))

            VStack(spacing: 2) {
                Text("\(rating)")
                    .font(.system(size: size < 180 ? 46 : 58, weight: .bold))
                    .foregroundColor(guideColor(85, 242, 122))

                Text(t(.guideRatingOutOf))
                    .font(.system(size: size < 180 ? 16 : 19, weight: .semibold))
                    .foregroundColor(.white)

                Text("★★★★★")
                    .font(.system(size: size < 180 ? 20 : 24, weight: .bold))
                    .foregroundColor(guideColor(255, 194, 71))
            }
        }
        .frame(width: size, height: size)
    }
}

private struct DriveTelemetryGuideCompactFeatureCard: View {
    let icon: String
    let text: String
    let accentColor: Color

    var body: some View {
        HStack(spacing: 8) {
            Text(icon)
                .font(.system(size: 20))
                .foregroundColor(accentColor)

            Text(text)
                .font(.system(size: 13, weight: .semibold))
                .lineLimit(2)
                .lineSpacing(1)
                .foregroundColor(.white)

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity)
        .background(Color.black.opacity(0.48))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

private struct DriveTelemetryGuideFeatureCard: View {
    let icon: String
    let text: String
    let accentColor: Color
    let isPortrait: Bool

    var body: some View {
        HStack(spacing: 12) {
            Text(icon)
                .font(.system(size: isPortrait ? 26 : 22))
                .foregroundColor(accentColor)

            Text(text)
                .font(.system(size: isPortrait ? 18 : 16, weight: .semibold))
                .foregroundColor(.white)
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, isPortrait ? 14 : 10)
        .frame(maxWidth: .infinity)
        .background(Color.black.opacity(0.48))
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }
}

private struct DriveTelemetryGuideIconLabel: View {
    let icon: String
    let label: String
    let isPortrait: Bool

    var body: some View {
        VStack(spacing: 6) {
            Text(icon)
                .font(.system(size: isPortrait ? 28 : 24))
                .padding(12)
                .background(Color.black.opacity(0.45))
                .clipShape(Circle())

            Text(label)
                .font(.system(size: isPortrait ? 12 : 11, weight: .semibold))
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .minimumScaleFactor(0.72)
                .allowsTightening(true)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity)
        }
        .frame(maxWidth: .infinity)
    }
}

private struct DriveTelemetryGuideDots: View {
    let currentPage: Int
    let pageCount: Int

    var body: some View {
        HStack(spacing: 7) {
            ForEach(0..<pageCount, id: \.self) { index in
                Capsule()
                    .fill(index == currentPage ? guideColor(10, 132, 255) : Color.white.opacity(0.35))
                    .frame(width: index == currentPage ? 20 : 10, height: 10)
            }
        }
    }
}
