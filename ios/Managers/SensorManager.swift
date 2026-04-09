//
//  SensorManager.swift
//  TelemetryApp
//
//  Full version: orientation-robust + GPS-assisted/IMU-only modes
//  + IMU-only forward-axis calibration (PCA on horizontal accel in reference frame)
//

import Foundation
import CoreLocation
import CoreMotion
import Combine
import simd
import UIKit

import Network




final class SensorManager: NSObject, ObservableObject {
    
    let crashEventSubject = PassthroughSubject<CrashEvent, Never>()

    var crashEventPublisher: AnyPublisher<CrashEvent, Never> {
        crashEventSubject.eraseToAnyPublisher()
    }
    func currentTripSessionId() -> String? {
        let trimmed = currentSessionId.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
    func startImplicitTrip() async throws {
        if !isCollectingNow {
            startCollecting()
        }
    }

    func finishImplicitTrip() async {
        if isCollectingNow {
            stopAll()
        }
    }
    
    

    
    // TEST ONLY: frozen config for current trip
    private var activeTripConfig: TripConfig?
    
    private enum V2Thresholds {
        // turn thresholds (g) — MUST match server
        static var turnSharpLatG: Double = 0.22
        static var turnEmergencyLatG: Double = 0.30

        // accel/brake thresholds (g) — MUST match server
        static var minSpeedForAccelBrakeMS: Double = 3.0
        static var accelBrakeCooldown: TimeInterval = 1.2
        static var accelSharpLongG: Double = 0.18
        static var accelEmergencyLongG: Double = 0.28
        static var brakeSharpLongG: Double = 0.22
        static var brakeEmergencyLongG: Double = 0.32

        // turn
        static var minSpeedForTurnMS: Double = 5.0
        static var turnCooldown: TimeInterval = 0.8
        static var turnYawThreshold: Double = 0.40

        // combined risk (skid)
        static var combinedMinSpeedMS: Double = 5.0
        static var combinedCooldownS: TimeInterval = 0.8
        static var combinedLatMinG: Double = 0.35
        static var accelInTurnSharpG: Double = 0.22
        static var accelInTurnEmergencyG: Double = 0.32
        static var brakeInTurnSharpG: Double = 0.22
        static var brakeInTurnEmergencyG: Double = 0.32

        // road anomaly (vertical window + thresholds)
        // road anomaly (vertical window + thresholds)
        static var roadWindowS: TimeInterval = 0.40
        static var roadCooldownS: TimeInterval = 1.20

        static var roadLowP2PG: Double = 0.70
        static var roadHighP2PG: Double = 1.10
        static var roadLowAbsG: Double = 0.45
        static var roadHighAbsG: Double = 0.75
        
        static var gyroSpikeThreshold: Double = 6.0
        

        // client-only: gate for road anomaly (server doesn’t gate road by speed)
        static var minSpeedForRoadMS: Double = 2.0
        
        
    }
    
    let waterGameManager = WaterGameManager()
    
    // <-- включай для теста дома
    
    // Indoor / home testing mode (tunes thresholds to be more sensitive and disables phone-moved suppression)
    private let indoorTestModeKey = "indoorTestMode_v1"

    @Published var indoorTestMode: Bool = {
        (UserDefaults.standard.object(forKey: "indoorTestMode_v1") as? Bool) ?? true
    }() {
        didSet {
            UserDefaults.standard.set(indoorTestMode, forKey: indoorTestModeKey)
            reapplyDetectorThresholds() // ключевой момент
        }
    }
    
    
    // thresholds (placeholders; tune later)

    
    // MARK: - V2 Road Anomaly config (placeholders; tune later)
    private var roadWindowS: Double = V2Thresholds.roadWindowS

    private var roadCooldownS: Double = V2Thresholds.roadCooldownS

    private var roadLowP2PG: Double = V2Thresholds.roadLowP2PG
    private var roadHighP2PG: Double = V2Thresholds.roadHighP2PG

    private var roadLowAbsG: Double = V2Thresholds.roadLowAbsG
    private var roadHighAbsG: Double = V2Thresholds.roadHighAbsG

    private var lastRoadEventAt: Date? = nil

    private struct VertPoint {
        let t: Date
        let aVertG: Double
    }
    private var vertBuffer: [VertPoint] = []
    
    // MARK: - V2 Combined Risk config (placeholders; tune later)
    private var combinedMinSpeedMS: Double = V2Thresholds.combinedMinSpeedMS
    private var combinedCooldownS: Double = V2Thresholds.combinedCooldownS
    private var combinedLatMinG: Double = V2Thresholds.combinedLatMinG

    private var accelInTurnSharpG: Double = V2Thresholds.accelInTurnSharpG
    private var accelInTurnEmergencyG: Double = V2Thresholds.accelInTurnEmergencyG

    private var brakeInTurnSharpG: Double = V2Thresholds.brakeInTurnSharpG
    private var brakeInTurnEmergencyG: Double = V2Thresholds.brakeInTurnEmergencyG
    
    private var gyroSpikeThreshold: Double = V2Thresholds.gyroSpikeThreshold

    private var lastAccelInTurnAt: Date? = nil
    private var lastBrakeInTurnAt: Date? = nil
    
    private var stopInProgress: Bool = false
    
    // Public Alpha additive fields
    private let crashThresholdG: Double = 1.2




    
    /// Call once at app startup (TelemetryAppApp.swift).
    
    static let shared = SensorManager()

    // MARK: - Published UI state

    @Published var statusText: String = "Idle"
    @Published var appStateText: String = "Приложение в ожидании"
    @Published var lastLocationString: String = "—"
    @Published var lastSpeedString: String = "—"
    @Published var lastAccelString: String = "—"
    @Published var accelMagnitudeString: String = "—"
    @Published var lastNetworkErrors: [String] = []
    
    // DEBUG: driving test
    @Published var lastUserAccelString: String = "—"      // userAcceleration xyz
    @Published var lastRotRateString: String = "—"        // rotationRate xyz (ищем z/yaw)
    @Published var lastProjString: String = "—"           // aLong/aLat/aVert (g)
    @Published var lastFiredEventString: String = "—"     // last detected event

    private var lastDebugEventIndex: Int = 0
    
   


    // Last successful delivery route (EU/RU) for /ingest
    @Published var lastDeliveryRoute: String = UserDefaults.standard.string(forKey: "last_delivery_route_v1") ?? "EU"


    // UI expects this to be @Published
    @Published var driverId: String = UserDefaults.standard.string(forKey: "driverId") ?? ""
    private let autoDriverPasswordKey = "auto_driver_password_v1"
    private let autoDriverPrefix = "user-"
    
    @Published var currentTripElapsedSec: Int = 0
    @Published var currentTripDistanceKm: Double = 0
    
    
   
    // MARK: - DriverId enforcement
    /// DriverId is mandatory. If empty — app must block Start and show onboarding.
    /// Server must never receive anon_* driver ids.
    func resolvedDriverId() -> String {
        return self.driverId
    }
    
    private func defaultAutoDriverId() -> String {
        let compact = deviceId.replacingOccurrences(of: "-", with: "").lowercased()
        return autoDriverPrefix + String(compact.prefix(12))
    }

    private func isAutoDriverId(_ value: String) -> Bool {
        value == defaultAutoDriverId()
    }

    private func loadOrCreateAutoDriverPassword() -> String {
        if let data = KeychainStore.shared.get(autoDriverPasswordKey),
           let stored = String(data: data, encoding: .utf8),
           !stored.isEmpty {
            return stored
        }

        let generated = UUID().uuidString.replacingOccurrences(of: "-", with: "") + UUID().uuidString.prefix(8)
        let password = String(generated)
        try? KeychainStore.shared.set(Data(password.utf8), for: autoDriverPasswordKey)
        return password
    }

    @MainActor
    private func ensureLocalAutoDriverIdIfNeeded() {
        let trimmed = driverId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.isEmpty else { return }
        updateDriverId(defaultAutoDriverId())
    }

    func ensureDriverReadyForAppLaunch() async throws {
        await MainActor.run {
            ensureLocalAutoDriverIdIfNeeded()
            self.driverAuthState = .checking
        }

        let trimmed = await MainActor.run { self.driverId.trimmingCharacters(in: .whitespacesAndNewlines) }
        guard !trimmed.isEmpty else {
            throw NSError(domain: "Driver", code: -10, userInfo: [NSLocalizedDescriptionKey: "driver id missing after bootstrap"])
        }

        do {
            let res = try await prepareDriverId(trimmed)
            switch res.status {
            case .knownDevice:
                await setDriverAuthorized(true)
                return

            case .newDriver:
                if isAutoDriverId(trimmed) {
                    let password = loadOrCreateAutoDriverPassword()
                    try await registerDriverId(trimmed, password: password)
                    await setDriverAuthorized(true)
                    return
                }
                throw NSError(domain: "Driver", code: -11, userInfo: [NSLocalizedDescriptionKey: "interactive setup required"])

            case .needPassword:
                if isAutoDriverId(trimmed) {
                    let password = loadOrCreateAutoDriverPassword()
                    try await loginDriverId(trimmed, password: password)
                    await setDriverAuthorized(true)
                    return
                }
                throw NSError(domain: "Driver", code: -12, userInfo: [NSLocalizedDescriptionKey: "interactive setup required"])
            }
        } catch {
            let ns = error as NSError
            let authNetworkError = ns.domain == "AuthManager" || ns.domain == NSURLErrorDomain
            let backendUnavailable = ns.localizedDescription.lowercased().contains("unreachable") ||
                ns.localizedDescription.lowercased().contains("temporarily unavailable") ||
                ns.localizedDescription.lowercased().contains("timed out")

            if authNetworkError || backendUnavailable {
                await setDriverAuthTemporarilyUnavailable(ns.localizedDescription)
                return
            }
            throw error
        }
    }

    @Published var isDriverAuthorizedOnThisDevice: Bool = false
    @Published var lastDriverAuthError: String? = nil

    enum DriverAuthState: String {
        case unknown
        case checking
        case authorized
        case passwordRequired
        case temporarilyUnavailable
    }

    @Published var driverAuthState: DriverAuthState = .unknown

    enum DriverPrepareStatus: String {
        case knownDevice = "known_device"
        case needPassword = "need_password"
        case newDriver = "new_driver"
    }

    struct DriverPrepareResult {
        let status: DriverPrepareStatus
    }

    @MainActor
    func setDriverAuthorized(_ ok: Bool) {
        self.isDriverAuthorizedOnThisDevice = ok
        self.driverAuthState = ok ? .authorized : .passwordRequired
        if ok {
            self.lastDriverAuthError = nil
        }
    }

    @MainActor
    func setDriverAuthTemporarilyUnavailable(_ message: String?) {
        self.isDriverAuthorizedOnThisDevice = false
        self.driverAuthState = .temporarilyUnavailable
        self.lastDriverAuthError = message
    }

    @MainActor
    func setDriverAuthChecking() {
        self.driverAuthState = .checking
    }

    /// Pre-check driver_id uniqueness + whether password is required for this device.
    func prepareDriverId(_ driverId: String) async throws -> DriverPrepareResult {
        let trimmed = driverId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            throw NSError(domain: "Driver", code: -1, userInfo: [NSLocalizedDescriptionKey: "Driver ID is empty"])
        }

        let status = try await NetworkManager.shared.driverPrepare(deviceId: self.deviceId, driverId: trimmed)
        guard let s = DriverPrepareStatus(rawValue: status) else {
            throw NSError(domain: "Driver", code: -2, userInfo: [NSLocalizedDescriptionKey: "Unknown server status: \(status)"])
        }
        await MainActor.run {
            switch s {
            case .knownDevice:
                self.driverAuthState = .authorized
                self.isDriverAuthorizedOnThisDevice = true
                self.lastDriverAuthError = nil
            case .needPassword, .newDriver:
                self.driverAuthState = .passwordRequired
                self.isDriverAuthorizedOnThisDevice = false
            }
        }
        return DriverPrepareResult(status: s)
    }

    /// Register a NEW driver_id with password + bind to this device.
    func registerDriverId(_ driverId: String, password: String) async throws {
        let trimmed = driverId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw NSError(domain: "Driver", code: -1, userInfo: [NSLocalizedDescriptionKey: "Driver ID is empty"]) }
        guard !password.isEmpty else { throw NSError(domain: "Driver", code: -1, userInfo: [NSLocalizedDescriptionKey: "Password is empty"]) }
        try await NetworkManager.shared.driverRegister(deviceId: self.deviceId, driverId: trimmed, password: password)
        DispatchQueue.main.async {
            self.updateDriverId(trimmed)
            self.isDriverAuthorizedOnThisDevice = true
            self.driverAuthState = .authorized
            self.lastDriverAuthError = nil
        }
    }

    /// Login existing driver_id with password + bind to this device.
    func loginDriverId(_ driverId: String, password: String) async throws {
        let trimmed = driverId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw NSError(domain: "Driver", code: -1, userInfo: [NSLocalizedDescriptionKey: "Driver ID is empty"]) }
        guard !password.isEmpty else { throw NSError(domain: "Driver", code: -1, userInfo: [NSLocalizedDescriptionKey: "Password is empty"]) }
        try await NetworkManager.shared.driverLogin(deviceId: self.deviceId, driverId: trimmed, password: password)
        DispatchQueue.main.async {
            self.updateDriverId(trimmed)
            self.isDriverAuthorizedOnThisDevice = true
            self.driverAuthState = .authorized
            self.lastDriverAuthError = nil
        }
    }


    // MARK: - App/device metadata (for clients/client_installs completeness)
    private func appVersion() -> String? {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String
    }

    private func appBuild() -> String? {
        Bundle.main.infoDictionary?["CFBundleVersion"] as? String
    }

    private func iosVersion() -> String? {
        UIDevice.current.systemVersion
    }

    /// More precise model (e.g. "iPhone15,2") than UIDevice.current.model
    private func deviceModelIdentifier() -> String? {
        var systemInfo = utsname()
        uname(&systemInfo)
        let mirror = Mirror(reflecting: systemInfo.machine)
        let id = mirror.children.reduce("") { acc, el in
            guard let v = el.value as? Int8, v != 0 else { return acc }
            return acc + String(UnicodeScalar(UInt8(v)))
        }
        return id.isEmpty ? nil : id
    }

    private func localeId() -> String? {
        Locale.current.identifier
    }

    private func timeZoneId() -> String? {
        TimeZone.current.identifier
    }
    
    // acount delition
    @MainActor
    func deleteAccountInApp() async throws {
        let driverId = self.driverId.trimmingCharacters(in: .whitespacesAndNewlines)
        let deviceId = self.deviceId

        guard !driverId.isEmpty else {
            throw NSError(domain: "DeleteAccount", code: -1, userInfo: [
                NSLocalizedDescriptionKey: "Missing driverId"
            ])
        }

        if isCollectingNow {
            stopAll()
        }

        try await NetworkManager.shared.deleteAccount(
            deviceId: deviceId,
            driverId: driverId
        )

        clearLocalAppData()
        AuthManager.shared.clearAllAuthState()

        sessionId = UUID().uuidString
    }

    
    

    // MARK: - Location permission (UI)

    @Published var locationAuthText: String = "—"

    // Small red GPS button on main screen (show whenever Always is NOT granted)
    var shouldShowGpsBadgeInMainUI: Bool {
        !isLocationAlwaysAuthorized
    }

    // Leave the existing red warning logic (p3), but without backgroundGpsEnabled.
    // If user is collecting and not Always -> warn.
    var shouldShowAlwaysWarningInMainUI: Bool {
        isCollectingNow && !isLocationAlwaysAuthorized
    }

    var isLocationAlwaysAuthorized: Bool {
        locationManager.authorizationStatus == .authorizedAlways
    }

    var locationAuthorizationStatus: CLAuthorizationStatus {
        locationManager.authorizationStatus
    }

    func requestWhenInUseAuthorization() {
        locationManager.requestWhenInUseAuthorization()
    }



    
    // MARK: - Completeness (created batches)
    @Published private(set) var createdBatchesCount: Int = 0     // created total in this session
    @Published private(set) var lastCreatedBatchSeq: Int = -1    // last batch_seq created


    
    // MARK: - Telemetry mode / GPS freshness

    @Published var telemetryModeText: String = "IMU only"

    // Отсекаем "старые" GPS-точки (часто прилетают при возврате из фона)
    private var acceptLocationMaxAge: TimeInterval = 5.0

    // Свежесть для статуса "GPS/IMU only"
    private var maxLocationAge: TimeInterval = 15.0

    // Порог точности (ваш вопрос про horizontalAccuracy <= 100 — вот здесь)
    private var maxHorizontalAccuracy: CLLocationAccuracy = 100.0

    private func hasFreshLocation() -> Bool {
        guard let loc = lastKnownLocation else { return false }
        return Date().timeIntervalSince(loc.timestamp) <= maxLocationAge
    }

    private func updateTelemetryModeText() {
        telemetryModeText = hasFreshLocation() ? "GPS" : "IMU only"
    }
    
    // Day monitoring should keep app alive in background (low-power)
    private var dayMonitoringKeepAliveEnabled: Bool = false



    @Published private(set) var isCollectingNow: Bool = false
    
    // Server totals (arrive from server via ingest totals)
    @Published var currentTripSuddenAccelCount: Int = 0
    @Published var currentTripSuddenBrakeCount: Int = 0
    @Published var currentTripSuddenTurnCount: Int = 0
    @Published var currentTripRoadAnomalyCount: Int = 0
    
    // Live true penalty (server-side, per-batch)
    @Published var currentTripLivePenaltyTrue: Double? = nil
    
    @Published var currentTripLiveExposureScoreTrue: Double? = nil
    @Published var currentTripLiveExposurePresetTrue: String? = nil

    // Device totals (instant, on-device detection)
    @Published var currentTripSuddenAccelDeviceCount: Int = 0
    @Published var currentTripSuddenBrakeDeviceCount: Int = 0
    @Published var currentTripSuddenTurnDeviceCount: Int = 0
    @Published var currentTripRoadAnomalyDeviceCount: Int = 0
    
    @Published private(set) var isNetworkSatisfied: Bool = true
    
    // Transparent aggregates (normal + extreme)
    private let aggMinEventG: Double = 0.01
    private let aggMaxReasonableG: Double = 10.0

    @Published var brakeCount: Int = 0
    @Published var brakeSumIntensity: Double = 0
    @Published var brakeExtremeCount: Int = 0
    @Published var brakeExtremeSumIntensity: Double = 0
    @Published var brakeExtremeMaxIntensity: Double? = nil

    @Published var accelCount: Int = 0
    @Published var accelSumIntensity: Double = 0
    @Published var accelExtremeCount: Int = 0
    @Published var accelExtremeSumIntensity: Double = 0
    @Published var accelExtremeMaxIntensity: Double? = nil

    @Published var roadCount: Int = 0
    @Published var roadSumIntensity: Double = 0
    @Published var roadExtremeCount: Int = 0
    @Published var roadExtremeSumIntensity: Double = 0
    @Published var roadExtremeMaxIntensity: Double? = nil

    @Published var turnCount: Int = 0
    @Published var turnSumIntensity: Double = 0
    @Published var turnExtremeCount: Int = 0
    @Published var turnExtremeSumIntensity: Double = 0
    @Published var turnExtremeMaxIntensity: Double? = nil

    @Published var accelInTurnCount: Int = 0
    @Published var accelInTurnSumIntensity: Double = 0
    @Published var accelInTurnExtremeCount: Int = 0
    @Published var accelInTurnExtremeSumIntensity: Double = 0
    @Published var accelInTurnExtremeMaxIntensity: Double? = nil

    @Published var brakeInTurnCount: Int = 0
    @Published var brakeInTurnSumIntensity: Double = 0
    @Published var brakeInTurnExtremeCount: Int = 0
    @Published var brakeInTurnExtremeSumIntensity: Double = 0
    @Published var brakeInTurnExtremeMaxIntensity: Double? = nil
    
    @Published var brakeMaxIntensity: Double = 0
    @Published var accelMaxIntensity: Double = 0
    @Published var roadMaxIntensity: Double = 0
    @Published var turnMaxIntensity: Double = 0
    @Published var accelInTurnMaxIntensity: Double = 0
    @Published var brakeInTurnMaxIntensity: Double = 0
    
    // фиксатор аварий
    @Published var crashDetected: Bool = false
    @Published var crashG: Double = 0
    private var dashcamCrashEventSentForCurrentTrip: Bool = false
    
    // Public Alpha additive fields
    var currentSpeedKmhForAutoFinish: Double {
        let ms = max(0, lastKnownSpeedMS ?? 0)
        let kmh = ms * 3.6
        return kmh < 1.5 ? 0 : kmh
    }
    

    
    private enum AggKind { case accel, brake, road, turn, accelInTurn, brakeInTurn }
    
    private func publishDashcamCrashEventIfNeeded() {
        guard !dashcamCrashEventSentForCurrentTrip else { return }

        dashcamCrashEventSentForCurrentTrip = true

        let event = CrashEvent(
            at: Date(),
            gForce: crashG,
            latitude: lastKnownLocation?.coordinate.latitude,
            longitude: lastKnownLocation?.coordinate.longitude
        )
        crashEventSubject.send(event)
    }

    private func recordAgg(_ kind: AggKind, intensity raw: Double) {
        let x = abs(raw)
        
        // Crash detection
        // Public Alpha additive fields
        // Crash detection with max-G tracking
        let threshold = indoorTestMode ? 1.2 : crashThresholdG
        if x > crashThresholdG {
            DispatchQueue.main.async {
                self.crashDetected = true                                
                self.crashG = Swift.max(self.crashG, x)
                self.publishDashcamCrashEventIfNeeded()
            }
        }
        
        if x < aggMinEventG { return }

        let isExtreme = x > aggMaxReasonableG

        func updNormal(count: inout Int, sum: inout Double) {
            count += 1
            sum += x
        }
        func updExtreme(count: inout Int, sum: inout Double, max: inout Double?) {
            count += 1
            sum += x
            max = Swift.max(max ?? 0, x)
        }

        DispatchQueue.main.async {
            switch kind {
            case .brake:
                if isExtreme { updExtreme(count: &self.brakeExtremeCount, sum: &self.brakeExtremeSumIntensity, max: &self.brakeExtremeMaxIntensity) }
                else { updNormal(count: &self.brakeCount, sum: &self.brakeSumIntensity)
                    self.brakeMaxIntensity = max(self.brakeMaxIntensity, x) }

            case .accel:
                if isExtreme { updExtreme(count: &self.accelExtremeCount, sum: &self.accelExtremeSumIntensity, max: &self.accelExtremeMaxIntensity) }
                else { updNormal(count: &self.accelCount, sum: &self.accelSumIntensity)
                    self.accelMaxIntensity = max(self.accelMaxIntensity, x) }

            case .road:
                if isExtreme { updExtreme(count: &self.roadExtremeCount, sum: &self.roadExtremeSumIntensity, max: &self.roadExtremeMaxIntensity) }
                else { updNormal(count: &self.roadCount, sum: &self.roadSumIntensity)
                    self.roadMaxIntensity = max(self.roadMaxIntensity, x) }

            case .turn:
                if isExtreme { updExtreme(count: &self.turnExtremeCount, sum: &self.turnExtremeSumIntensity, max: &self.turnExtremeMaxIntensity) }
                else { updNormal(count: &self.turnCount, sum: &self.turnSumIntensity)
                    self.turnMaxIntensity = max(self.turnMaxIntensity, x) }

            case .accelInTurn:
                if isExtreme { updExtreme(count: &self.accelInTurnExtremeCount, sum: &self.accelInTurnExtremeSumIntensity, max: &self.accelInTurnExtremeMaxIntensity) }
                else { updNormal(count: &self.accelInTurnCount, sum: &self.accelInTurnSumIntensity)
                    self.accelInTurnMaxIntensity = max(self.accelInTurnMaxIntensity, x) }

            case .brakeInTurn:
                if isExtreme { updExtreme(count: &self.brakeInTurnExtremeCount, sum: &self.brakeInTurnExtremeSumIntensity, max: &self.brakeInTurnExtremeMaxIntensity) }
                else { updNormal(count: &self.brakeInTurnCount, sum: &self.brakeInTurnSumIntensity)
                    self.brakeInTurnMaxIntensity = max(self.brakeInTurnMaxIntensity, x) }
            }
        }
    }
    
    // Public Alpha additive fields
    
    @Published var carplayConnectedForPayload: Bool = false
    @Published var appStateForPayload: String = "foreground"
    @Published var screenInteractionInAppForPayload: Bool = false
    @Published var lastScreenInteractionAt: Date? = nil
    
    private var screenInteractionCountInBatch: Int = 0
    private var screenInteractionActiveSecInBatch: Double = 0
    private var screenInteractionWindowStartedAt: Date? = nil
    
    // Public Alpha additive fields
    func batteryStateStringForPayload() -> String {
        switch UIDevice.current.batteryState {
        case .charging: return "charging"
        case .full: return "full"
        case .unplugged: return "unplugged"
        default: return "unknown"
        }
    }
    // Public Alpha additive fields
    func refreshAppStateForPayload() {
        switch UIApplication.shared.applicationState {
        case .active:
            appStateForPayload = "foreground"
        case .background:
            appStateForPayload = "background"
        case .inactive:
            appStateForPayload = "inactive"
        @unknown default:
            appStateForPayload = "unknown"
        }
    }
    
    // Public Alpha additive fields
    func markScreenInteractionInApp() {
        let now = Date()

        if let last = lastScreenInteractionAt {
            let dt = now.timeIntervalSince(last)
            if dt > 0 && dt <= 5 {
                screenInteractionActiveSecInBatch += dt
            } else {
                screenInteractionActiveSecInBatch += 1.0
            }
        } else {
            screenInteractionActiveSecInBatch += 1.0
        }

        screenInteractionCountInBatch += 1
        lastScreenInteractionAt = now
        screenInteractionInAppForPayload = true
    }

    func refreshScreenInteractionForPayload() {
        guard let last = lastScreenInteractionAt else {
            screenInteractionInAppForPayload = false
            return
        }
        screenInteractionInAppForPayload = Date().timeIntervalSince(last) <= 10
    }

    // MARK: - Trip elapsed timer (UI counter independent from motion)
    private var tripElapsedTimer: DispatchSourceTimer?
    private let tripElapsedQueue = DispatchQueue(label: "SensorManager.tripElapsedTimer")

    private func startTripElapsedTimer() {
        stopTripElapsedTimer()

        let t = DispatchSource.makeTimerSource(queue: tripElapsedQueue)
        t.schedule(deadline: .now(), repeating: 1.0, leeway: .milliseconds(200))
        t.setEventHandler { [weak self] in
            guard let self else { return }
            guard let startedAt = self.tripStartedAt else { return }
            let elapsed = Int(Date().timeIntervalSince(startedAt))
            DispatchQueue.main.async {
                self.currentTripElapsedSec = max(0, elapsed)
                
#if DEBUG
if self.debugPrintsEnabled {
    print("BRAKE:", self.brakeCount,
          self.brakeSumIntensity,
          "EXT:", self.brakeExtremeCount,
          self.brakeExtremeMaxIntensity ?? 0)

    print("ACCEL:", self.accelCount,
          self.accelSumIntensity)

    print("ROAD:", self.roadCount,
          self.roadSumIntensity)

    print("TURN:", self.turnCount,
          self.turnSumIntensity)
}
#endif
            }
        }
        tripElapsedTimer = t
        t.resume()
    }

    private func stopTripElapsedTimer() {
        tripElapsedTimer?.cancel()
        tripElapsedTimer = nil
    }

    // Быстрый “подтяг” счетчиков при возврате в форграунд
    private func refreshTripCountersNow() {
        guard let startedAt = tripStartedAt else { return }
        let elapsed = Int(Date().timeIntervalSince(startedAt))
        DispatchQueue.main.async {
            self.currentTripElapsedSec = max(0, elapsed)
        }
    }

    
    private var tripStartedAt: Date?
    private var lastDistanceLoc: CLLocation?



    // MARK: - Managers

    private let locationManager = CLLocationManager()
    private let motionManager = CMMotionManager()
    
    // Manual trip auto-finish (only when trackingMode != "auto")
    private let manualAutoFinish = ManualTripAutoFinish()
    
    // Очередь для CoreMotion (20 Гц не на main)
    private let motionQueue: OperationQueue = {
        let q = OperationQueue()
        q.name = "SensorManager.motionQueue"
        q.qualityOfService = .userInitiated
        q.maxConcurrentOperationCount = 1
        return q
    }()


    // Activity / Pedometer (optional, if present in your models.swift)
    private let activityManager = CMMotionActivityManager()
    private let pedometer = CMPedometer()
    private let activityQueue = OperationQueue()
    private let movementStateQueue = DispatchQueue(label: "SensorManager.movementStateQueue")
    
    private func ensureTripElapsedTimerRunning() {
        guard isCollectingNow else { return }
        guard tripStartedAt != nil else { return }
        if tripElapsedTimer == nil {
            startTripElapsedTimer()
        }
    }


    // MARK: - Queues

    private let bufferQueue = DispatchQueue(label: "telemetry.buffer.queue")
    private let pendingQueue = DispatchQueue(label: "telemetry.pending.queue")

    // MARK: - Formatters

    private let isoFormatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        f.timeZone = TimeZone(secondsFromGMT: 0)
        return f
    }()

    // MARK: - IDs

    private let deviceIdKeychainKey = "telemetry_device_id_v1"
    private(set) var deviceId: String

    private(set) var sessionId: String = UUID().uuidString
    private(set) var activeTripDriverId: String?
    private(set) var pendingDriverIdAfterStop: String?

    var currentTripOwnerDriverId: String {
        activeTripDriverId ?? driverId
    }

    var deviceIdForDisplay: String { deviceId }
    var currentSessionId: String { sessionId }

    // MARK: - Buffers

    private var sampleBuffer: [TelemetrySample] = []
    private var eventsBuffer: [TelemetryEvent] = []

    // MARK: - Batch timer

    private var batchInterval: TimeInterval = 15.0
    
    // Вилка частот CoreMotion
    private var appMotionInterval: TimeInterval = 0.1    // 10 Гц
    private var waterMotionInterval: TimeInterval = 0.05 // 20 Гц — «Вода в стакане»

    private var wantsWaterRate: Bool = false
    private var currentMotionInterval: TimeInterval = 0.2

    // Телеметрию обрабатываем не чаще 10 Гц
    private var telemetryInterval: TimeInterval = 0.1  // 10 Hz
    private var lastTelemetryTickAt: Date = .distantPast
    
    // MARK: - UI throttle (avoid main thread overload)

    private var lastUIUpdateAt: TimeInterval = 0
    private let uiUpdateInterval: TimeInterval = 0.25   // 4 Hz UI refresh
    private let uiThrottleQueue = DispatchQueue(label: "SensorManager.uiThrottleQueue")

    private func shouldUpdateUI(now: Date) -> Bool {
        let ts = now.timeIntervalSince1970
        return uiThrottleQueue.sync {
            if (ts - lastUIUpdateAt) >= uiUpdateInterval {
                lastUIUpdateAt = ts
                return true
            }
            return false
        }
    }


    private let debugPrintsEnabled = false


    // MARK: - Last known (location & speed)

    private var lastKnownLocation: CLLocation?
    private var lastKnownSpeedMS: CLLocationSpeed?

    // Course caching (for short GPS drops)
    private var lastCourseRad: Double?
    private var lastCourseAt: Date?

    // MARK: - Mode switching (GPS-assisted vs IMU-only)

    private enum TelemetryMode: String {
        case gpsAssisted = "gps_assisted"
        case imuOnly = "imu_only"
    }

    private struct GPSQualitySample {
        let t: Date
        let hAcc: CLLocationAccuracy
        let hasCourse: Bool
        let speed: CLLocationSpeed
    }

    private var telemetryMode: TelemetryMode = .imuOnly
    private var suppressEventsUntil: Date = .distantPast

    private var gpsqWindow: [GPSQualitySample] = []
    private var gpsqWindowSize = 10
    private var gpsqMinSpeedForCourse: Double = 5.0 // 5
    private var gpsqGoodHAcc: Double = 30.0
    private var gpsqOkayHAcc: Double = 100.0

    private var lastModeChangeAt: Date = .distantPast
    private var modeMinHoldSec: TimeInterval = 8.0

    // MARK: - Motion reference frame (important for course projection)

    private var motionReferenceFrame: CMAttitudeReferenceFrame = .xArbitraryZVertical

    // MARK: - Thresholds / cooldowns

    private var lastTurnEventAt: Date?
    private var turnCooldown: TimeInterval = 0.8
    
    private var turnYawThreshold: Double = V2Thresholds.turnYawThreshold
    
    
    private var minSpeedForTurnMS: Double = 5.0 // 5
    
    // MARK: - V2 thresholds (placeholders; tune later)
    private let v2AlgoVersion = "v2"
    private let v2OriginClient = "client"
    
    // LEGACY (unused):
//    // speed gate
//    private var minSpeedForManoeuvreMS: Double = 3.0   // ~18 km/h - было  5.0
//
//    // accel/brake thresholds in g
//    private var accelSharpG: Double = 0.18
//    private var accelEmergencyG: Double = 0.28
//
//    private var brakeSharpG: Double = 0.22
//    private var brakeEmergencyG: Double = 0.32

    // shared cooldown for accel/brake/combined
//    private var manoeuvreCooldown: TimeInterval = 0.8

//    private var lastCombinedEventAt: Date?
    
    private var lastAccelEventAt: Date?
    private var lastBrakeEventAt: Date?
    
    private var lastGyroSpikeAt: Date?

    private var accelBrakeCooldown: TimeInterval = 1.2

    // thresholds in g (CMDeviceMotion.userAcceleration units are in g)
    // MARK: - V2 Maneuver thresholds (placeholders; tune later)
    private var turnSharpLatG: Double = 0.22
    private var turnEmergencyLatG: Double = 0.30

    private var accelSharpLongG: Double = 0.20
    private var accelEmergencyLongG: Double = 0.30

    private var brakeSharpLongG: Double = 0.18
    private var brakeEmergencyLongG: Double = 0.28


    private var minSpeedForAccelBrakeMS: Double = 3.0 // 3
    private var minSpeedForRoadMS: Double = V2Thresholds.minSpeedForRoadMS


    // MARK: - Phone moved detection (reposition)

    private var lastGravityDevice: SIMD3<Double>?
    private var phoneMoveAngleThresholdRad: Double = 0.40 // ~23 degrees
    private var phoneMoveSuppressSec: TimeInterval = 2.5
    
    
    // MARK: - IMU-only forward axis calibration (2D in reference frame)

    private enum IMUCalibState: String {
        case none
        case calibrating
        case ready
    }

    private var imuCalibState: IMUCalibState = .none
    
    private var imuCalibStartedAt: Date? = nil
    private var imuCalibTargetSeconds: Double = 3.0   // 3–5 сек

    // running covariance accumulator for aH = (x,y) in reference frame
    private var covXX: Double = 0
    private var covXY: Double = 0
    private var covYY: Double = 0
    private var covN: Int = 0

    // estimated forward axis (unit vector) in reference horizontal plane
    private var forwardAxisRef2D: SIMD2<Double>? = nil

    // sign disambiguation helper
    private var lastSpeedForSign: Double? = nil
    private var signScore: Double = 0   // if >0 => keep axis as is, if <0 => flip

    // tuning
    private var imuCalibMinSamples: Int = 100        // ~16s at ~5 Hz effective
    private var imuCalibMinHorizG: Double = 0.03    // ignore tiny noise
    private var imuCalibMaxYawRate: Double = 1.20   // rad/s “no turn” gate
    private var imuCalibUpdateEvery: Int = 20       // recompute eigenvector every N samples
    
    private func currentTelemetryModeString() -> String {
        hasFreshLocation() ? "GPS" : "IMU only"
    }
    
    /// Stable device identifier (must not change between launches).
    /// - Uses Keychain persistence to survive app restarts.
    /// - On first creation, clears existing Bearer token to avoid
    ///   403 device_id mismatch with previously issued tokens.
    private static func loadOrCreateStableDeviceId(deviceIdKeychainKey: String) -> (id: String, createdNew: Bool) {
        if let data = KeychainStore.shared.get(deviceIdKeychainKey),
           let s = String(data: data, encoding: .utf8),
           !s.isEmpty {
            #if DEBUG
            print("[DEVICEID] loaded from keychain")
            #endif
            return (s, false)
        }

        let newId = UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString
        
        #if DEBUG
        print("[DEVICEID] creating NEW id; trying to store in keychain")
        #endif
        
        do {
            try KeychainStore.shared.set(Data(newId.utf8), for: deviceIdKeychainKey)
            #if DEBUG
            print("[DEVICEID] stored OK")
            #endif
            
        } catch {
            
            #if DEBUG
            print("[DEVICEID] store FAILED: \(error)")
            #endif
            
            // If Keychain write fails, we still use the ID for this run.
            // It may be re-created on next launch.
        }
        return (newId, true)
    }



    // MARK: - Init

    private override init() {
        let (stableId, createdNew) = Self.loadOrCreateStableDeviceId(deviceIdKeychainKey: deviceIdKeychainKey)
        self.deviceId = stableId

        super.init()
        
        DispatchQueue.main.async {
            self.ensureLocalAutoDriverIdIfNeeded()
        }
        
        UIDevice.current.isBatteryMonitoringEnabled = true


        // If device_id was newly created but a token exists (old builds),
        // clear token to avoid 403 device_id mismatch
        
        if createdNew, AuthManager.shared.currentToken() != nil {
            
            #if DEBUG
            print("[AUTH] clearing token because deviceId was newly created")
            #endif
            
            AuthManager.shared.clearToken()
        }

        // Public Alpha additive fields
     
        
        locationManager.allowsBackgroundLocationUpdates = false
        locationManager.showsBackgroundLocationIndicator = false

        locationManager.delegate = self
        
        refreshLocationAuthText()
        applyBackgroundGpsPolicy()
        
        locationManager.activityType = .automotiveNavigation
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.distanceFilter = kCLDistanceFilterNone

        // Optional: if you pipe NetworkManager logs into UI
        NetworkManager.shared.logHandler = { [weak self] msg in
            // Не засоряем "Last 5 errors" служебными retry-сообщениями
            if msg.hasPrefix("[Network] retry") { return }
            self?.pushNetworkError(msg)
        }

        // Observe route changes from NetworkManager (EU/RU indicator)
        NotificationCenter.default.addObserver(
            forName: .networkManagerIngestTotalsDidUpdate,
            object: nil,
            queue: .main
        ) { [weak self] note in
            guard let self else { return }
            guard
                let sid = note.userInfo?["session_id"] as? String,
                sid == self.sessionId
            else { return }

            DispatchQueue.main.async {

                let accelSharp = (note.userInfo?["accel_sharp_total"] as? Int) ?? 0
                let accelEmerg = (note.userInfo?["accel_emergency_total"] as? Int) ?? 0
                self.currentTripSuddenAccelCount = accelSharp + accelEmerg

                let brakeSharp = (note.userInfo?["brake_sharp_total"] as? Int) ?? 0
                let brakeEmerg = (note.userInfo?["brake_emergency_total"] as? Int) ?? 0
                self.currentTripSuddenBrakeCount = brakeSharp + brakeEmerg

                let turnSharp = (note.userInfo?["turn_sharp_total"] as? Int) ?? 0
                let turnEmerg = (note.userInfo?["turn_emergency_total"] as? Int) ?? 0
                self.currentTripSuddenTurnCount = turnSharp + turnEmerg

                let roadLow = (note.userInfo?["road_anomaly_low_total"] as? Int) ?? 0
                let roadHigh = (note.userInfo?["road_anomaly_high_total"] as? Int) ?? 0
                self.currentTripRoadAnomalyCount = roadLow + roadHigh

                // Live true penalty
                if let p = note.userInfo?["live_penalty_true"] as? Double {
                    self.currentTripLivePenaltyTrue = p
                } else if let pInt = note.userInfo?["live_penalty_true"] as? Int {
                    self.currentTripLivePenaltyTrue = Double(pInt)
                } else if note.userInfo?["live_penalty_true"] != nil {
                    // если пришло, но не распарсилось — не трогаем старое значение
                } else {
                    // если вообще не приходит (например RU offline-accept), тоже не сбрасываем
                }
                
                if let s = note.userInfo?["live_exposure_score_true"] as? Double {
                    self.currentTripLiveExposureScoreTrue = s
                } else if let n = note.userInfo?["live_exposure_score_true"] as? NSNumber {
                    self.currentTripLiveExposureScoreTrue = n.doubleValue
                } else if let i = note.userInfo?["live_exposure_score_true"] as? Int {
                    self.currentTripLiveExposureScoreTrue = Double(i)
                }

                if let p = note.userInfo?["live_exposure_preset_true"] as? String {
                    self.currentTripLiveExposurePresetTrue = p
                }
            }
        }


    }
    
    func resetLiveServerMetrics() {
        currentTripLivePenaltyTrue = nil
        currentTripLiveExposureScoreTrue = nil
        currentTripLiveExposurePresetTrue = nil
    }
    
    
    // Monotonic batch sequence (per session)
    private var batchSeq: UInt64 = 0


    // MARK: - Public controls
    
    func setDayMonitoringKeepAliveEnabled(_ enabled: Bool) {
        dayMonitoringKeepAliveEnabled = enabled

        if enabled {
            requestAlwaysAuthorization()
        }

        applyBackgroundGpsPolicy()

        if enabled {
            startIdleBackgroundLocationIfNeeded()
        } else {
            stopIdleBackgroundLocation()
        }
    }


    func updateDriverId(_ id: String) {
        let trimmed = id.trimmingCharacters(in: .whitespacesAndNewlines)
        DispatchQueue.main.async {
            if self.driverId != trimmed {
                // Any change invalidates previous authorization until server confirms.
                self.isDriverAuthorizedOnThisDevice = false
            }
            self.driverId = trimmed
        }
        DispatchQueue.main.async {
            NotificationCenter.default.post(
                name: .driverIdDidChange,
                object: nil,
                userInfo: ["driver_id": trimmed]
            )
        }


        if trimmed.isEmpty {
            UserDefaults.standard.removeObject(forKey: "driverId")
            DispatchQueue.main.async {
                self.isDriverAuthorizedOnThisDevice = false
            }
        } else {
            UserDefaults.standard.set(trimmed, forKey: "driverId")
        }
    }

    func clearNetworkErrors() {
        DispatchQueue.main.async {
            self.lastNetworkErrors.removeAll()
        }
    }

    /// Lightweight warm-up: permissions only. Actual collection starts in startCollecting().
    func configure() {
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        locationManager.activityType = .automotiveNavigation
        locationManager.pausesLocationUpdatesAutomatically = false
//        locationManager.allowsBackgroundLocationUpdates = true
        
        refreshLocationAuthText()
        applyBackgroundGpsPolicy()
        
        NotificationCenter.default.addObserver(
            forName: UIApplication.didEnterBackgroundNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            guard let self else { return }
            self.applyBackgroundGpsPolicy()
            self.startIdleBackgroundLocationIfNeeded()
            self.refreshTripCountersNow()          // фиксируем elapsed “на момент ухода”
            self.ensureTripElapsedTimerRunning()   // если таймер по какой-то причине nil
        }

        NotificationCenter.default.addObserver(
            forName: UIApplication.willEnterForegroundNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            guard let self else { return }
            self.applyBackgroundGpsPolicy()
            self.startIdleBackgroundLocationIfNeeded()
            self.ensureTripElapsedTimerRunning()
            self.refreshTripCountersNow()
        }

    }

    
    // MARK: - Water glass live visualization
    /// Запуск CoreMotion исключительно для визуализации (без GPS/батчей/сети).
    /// Безопасно вызывать даже если сбор телеметрии уже идёт.
    func startWaterVisualization() {
        wantsWaterRate = true
#if DEBUG

        print("[WATER] START")

#endif
        ensureMotionRunning()
    }


    /// Остановка CoreMotion, если он был запущен только ради визуализации.
    /// Если идёт сбор телеметрии — motion не останавливаем.
    func stopWaterVisualization() {
        wantsWaterRate = false
#if DEBUG

        print("[WATER] STOP")

#endif

        if isCollectingNow {
            ensureMotionRunning()
            return
        }

        if motionManager.isDeviceMotionActive {
            stopMotion()
        }
    }




    func startCollecting() {
        // 0) Защита от повторного Start: НЕ пересоздаём sessionId и НЕ сбрасываем буферы
        if isCollectingNow {
            #if DEBUG
            print("[Session] Start ignored: already collecting. sessionId=\(sessionId)")
            #endif
            setStatusOnMain("Уже запущено")
            return
        }

        // 1) Валидация
        guard !driverId.isEmpty else {
            setStatusOnMain("Введите driverId")
            return
        }

        // 2) UI/State: фиксируем старт сессии ОДИН раз
        setStatusOnMain("Starting…")
        setAppStateOnMain("Collecting")


        sessionId = UUID().uuidString
        activeTripDriverId = resolvedDriverId()
        
#if DEBUG

        print("[SESSION] start session_id=\(sessionId) driver=\(activeTripDriverId ?? driverId) device=\(deviceIdForDisplay)")

#endif

        if FeatureFlags.manualTuning {
            activeTripConfig = TripConfigResolver.resolveForNextTrip()
        } else {
            activeTripConfig = nil
        }
        applyV2Placeholders(activeTripConfig?.v2)
        
#if DEBUG

        print("manualTuning =", FeatureFlags.manualTuning)
        print("activeTripConfig is nil =", activeTripConfig == nil)
        
        print("[INDOOR] enabled=\(indoorTestMode)")
        print("[THR] minTurn=\(minSpeedForTurnMS) minRoad=\(minSpeedForRoadMS) minAccelBrake=\(minSpeedForAccelBrakeMS)")
        print("[THR] roadLowP2P=\(roadLowP2PG) roadHighP2P=\(roadHighP2PG) roadCooldown=\(roadCooldownS)")

#endif
        
        sessionStartedAt = Date()
        sessionEndedAt = nil
        
        DispatchQueue.main.async {
            self.isCollectingNow = true
        }

        tripStartedAt = Date()
                
        startTripElapsedTimer()
        refreshTripCountersNow()
        
        resetLiveServerMetrics()
        // 🔹 Автофиниш для manual режимов
        manualAutoFinish.stop()
        if self.trackingMode != "auto" {
            manualAutoFinish.start(
                currentSpeedKmh: { [weak self] in
                    self?.currentSpeedKmhForAutoFinish ?? 0
                },
                onAutoStop: { [weak self] in
                    guard let self else { return }
                    Task { @MainActor in
                        self.stopAll()
                    }
                }
            )
        }
              

        lastDistanceLoc = nil
        currentTripElapsedSec = 0
        currentTripDistanceKm = 0
        
        currentTripSuddenAccelCount = 0
        currentTripSuddenBrakeCount = 0
        currentTripSuddenTurnCount = 0
        currentTripRoadAnomalyCount = 0

        currentTripSuddenAccelDeviceCount = 0
        currentTripSuddenBrakeDeviceCount = 0
        currentTripSuddenTurnDeviceCount = 0
        currentTripRoadAnomalyDeviceCount = 0
        
        crashDetected = false
        crashG = 0
        dashcamCrashEventSentForCurrentTrip = false
        
        brakeCount = 0
        brakeSumIntensity = 0
        brakeExtremeCount = 0
        brakeExtremeSumIntensity = 0
        brakeExtremeMaxIntensity = nil

        accelCount = 0
        accelSumIntensity = 0
        accelExtremeCount = 0
        accelExtremeSumIntensity = 0
        accelExtremeMaxIntensity = nil

        roadCount = 0
        roadSumIntensity = 0
        roadExtremeCount = 0
        roadExtremeSumIntensity = 0
        roadExtremeMaxIntensity = nil

        turnCount = 0
        turnSumIntensity = 0
        turnExtremeCount = 0
        turnExtremeSumIntensity = 0
        turnExtremeMaxIntensity = nil

        accelInTurnCount = 0
        accelInTurnSumIntensity = 0
        accelInTurnExtremeCount = 0
        accelInTurnExtremeSumIntensity = 0
        accelInTurnExtremeMaxIntensity = nil

        brakeInTurnCount = 0
        brakeInTurnSumIntensity = 0
        brakeInTurnExtremeCount = 0
        brakeInTurnExtremeSumIntensity = 0
        brakeInTurnExtremeMaxIntensity = nil
        
        brakeMaxIntensity = 0
        accelMaxIntensity = 0
        roadMaxIntensity = 0
        turnMaxIntensity = 0
        accelInTurnMaxIntensity = 0
        brakeInTurnMaxIntensity = 0
        
        screenInteractionCountInBatch = 0
        screenInteractionActiveSecInBatch = 0
        screenInteractionWindowStartedAt = Date()
        lastScreenInteractionAt = nil
        screenInteractionInAppForPayload = false


        // 3) Location manager tuning (как у вас)
        locationManager.delegate = self
        locationManager.activityType = .automotiveNavigation
        locationManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        locationManager.distanceFilter = 10
        locationManager.pausesLocationUpdatesAutomatically = false
        // locationManager.allowsBackgroundLocationUpdates = true  // не напрямую, используем policy
        
        

        // 4) Применяем policy фонового GPS и запускаем GPS/Heading
        applyBackgroundGpsPolicy()

        locationManager.stopMonitoringSignificantLocationChanges()
        locationManager.startUpdatingLocation()


        if CLLocationManager.headingAvailable() {
            locationManager.headingFilter = 1.0
            locationManager.startUpdatingHeading()
        }

        // 5) Сброс внутренних буферов/режимов — ТОЛЬКО при реальном старте новой сессии
        bufferQueue.sync {
            sampleBuffer.removeAll()
            eventsBuffer.removeAll()
            lastKnownLocation = nil
            lastKnownSpeedMS = nil
            lastCourseRad = nil
            lastCourseAt = nil

            batchSeq = 0   // reset batch sequence
            
        // Completeness counters reset on new session start
            DispatchQueue.main.async {
                self.createdBatchesCount = 0
                self.lastCreatedBatchSeq = -1
            }

            gpsqWindow.removeAll()
            telemetryMode = .imuOnly
            lastModeChangeAt = .distantPast
            suppressEventsUntil = .distantPast

            lastGravityDevice = nil
            lastAccelEventAt = nil
            lastBrakeEventAt = nil
            lastTurnEventAt = nil

            resetIMUCalibration(reason: "startCollecting")
            
            
        }

        // 6) Старт сенсоров/таймеров (как у вас)
        wantsWaterRate = false
        ensureMotionRunning()
        startBatchTimer()


        // 7) Optional блоки (как у вас)
        startActivityUpdates()
        startPedometerUpdates()
        startAltimeterUpdates()
        startNetworkMonitor()

        // 8) Финальный статус
        setStatusOnMain("Running (\(telemetryMode.rawValue))")
        
        // Public Alpha additive fields
        UIDevice.current.isBatteryMonitoringEnabled = true
        
        refreshAppStateForPayload()
        refreshScreenInteractionForPayload()
        carplayConnectedForPayload = UIScreen.screens.count > 1
    }
    
    // Apply resolved V2 placeholders to detector thresholds
    private func applyV2Placeholders(_ v2: V2Placeholders?) {
        guard let v2 else {
            resetV2ToDefaults()
            return
        }

        minSpeedForAccelBrakeMS = v2.speed_gate_accel_brake_ms
        minSpeedForTurnMS = v2.speed_gate_turn_ms
        combinedMinSpeedMS = v2.speed_gate_combined_ms

        accelBrakeCooldown = v2.cooldown_accel_brake_s
        turnCooldown = v2.cooldown_turn_s
        combinedCooldownS = v2.cooldown_combined_s
        roadCooldownS = v2.cooldown_road_s

        accelSharpLongG = v2.accel_sharp_g
        accelEmergencyLongG = v2.accel_emergency_g
        brakeSharpLongG = v2.brake_sharp_g
        brakeEmergencyLongG = v2.brake_emergency_g

        turnSharpLatG = v2.turn_sharp_lat_g
        turnEmergencyLatG = v2.turn_emergency_lat_g

        combinedLatMinG = v2.combined_lat_min_g
        accelInTurnSharpG = v2.accel_in_turn_sharp_g
        accelInTurnEmergencyG = v2.accel_in_turn_emergency_g
        brakeInTurnSharpG = v2.brake_in_turn_sharp_g
        brakeInTurnEmergencyG = v2.brake_in_turn_emergency_g

        roadWindowS = v2.road_window_s
        roadLowP2PG = v2.road_low_p2p_g
        roadHighP2PG = v2.road_high_p2p_g
        roadLowAbsG = v2.road_low_abs_g
        roadHighAbsG = v2.road_high_abs_g
        
        gyroSpikeThreshold = V2Thresholds.gyroSpikeThreshold
        
        applyIndoorOverridesIfNeeded()
        
    }
    
    
    // Reset detector thresholds to built-in defaults
    private func resetV2ToDefaults() {
        // IMPORTANT: keep in sync with V2Thresholds
        turnYawThreshold = V2Thresholds.turnYawThreshold
        
        minSpeedForRoadMS = V2Thresholds.minSpeedForRoadMS

        minSpeedForAccelBrakeMS = V2Thresholds.minSpeedForAccelBrakeMS
        minSpeedForTurnMS = V2Thresholds.minSpeedForTurnMS
        combinedMinSpeedMS = V2Thresholds.combinedMinSpeedMS

        accelBrakeCooldown = V2Thresholds.accelBrakeCooldown
        turnCooldown = V2Thresholds.turnCooldown
        combinedCooldownS = V2Thresholds.combinedCooldownS
        roadCooldownS = V2Thresholds.roadCooldownS

        accelSharpLongG = V2Thresholds.accelSharpLongG
        accelEmergencyLongG = V2Thresholds.accelEmergencyLongG
        brakeSharpLongG = V2Thresholds.brakeSharpLongG
        brakeEmergencyLongG = V2Thresholds.brakeEmergencyLongG

        turnSharpLatG = V2Thresholds.turnSharpLatG
        turnEmergencyLatG = V2Thresholds.turnEmergencyLatG

        combinedLatMinG = V2Thresholds.combinedLatMinG
        accelInTurnSharpG = V2Thresholds.accelInTurnSharpG
        accelInTurnEmergencyG = V2Thresholds.accelInTurnEmergencyG
        brakeInTurnSharpG = V2Thresholds.brakeInTurnSharpG
        brakeInTurnEmergencyG = V2Thresholds.brakeInTurnEmergencyG

        roadWindowS = V2Thresholds.roadWindowS
        roadLowP2PG = V2Thresholds.roadLowP2PG
        roadHighP2PG = V2Thresholds.roadHighP2PG
        roadLowAbsG = V2Thresholds.roadLowAbsG
        roadHighAbsG = V2Thresholds.roadHighAbsG
        gyroSpikeThreshold = V2Thresholds.gyroSpikeThreshold
        
        applyIndoorOverridesIfNeeded()
        
    }

    // Apply "indoor test mode" overrides on top of whatever thresholds are currently active
    private func applyIndoorOverridesIfNeeded() {
        guard indoorTestMode else { return }

        // turns
        turnYawThreshold = 0.75
        turnSharpLatG = 0.12
        turnEmergencyLatG = 0.18
        turnCooldown = 0.35

        // road
        roadWindowS   = 0.60
        roadCooldownS = 0.25
        roadLowP2PG   = 0.11
        roadHighP2PG  = 0.20
        roadLowAbsG   = 0.05
        roadHighAbsG  = 0.09

        // speed gates
        minSpeedForTurnMS = 0.0
        minSpeedForRoadMS = 0.0
        minSpeedForAccelBrakeMS = 0.0

        // gyro spike threshold
        gyroSpikeThreshold = 20.0
    }

    // Re-apply thresholds from TripConfig / defaults, then apply indoor overrides if enabled.
    private func reapplyDetectorThresholds() {
        bufferQueue.async { [weak self] in
            guard let self else { return }

            if let v2 = self.activeTripConfig?.v2 {
                self.applyV2Placeholders(v2)
            } else {
                self.resetV2ToDefaults()
            }

            // Чтобы при резком переключении не казалось, что всё “замерло”
            // (старые cooldown/window состояния могут блокировать события)
            self.lastTurnEventAt = nil
            self.lastAccelEventAt = nil
            self.lastBrakeEventAt = nil
            self.lastRoadEventAt = nil
            self.vertBuffer.removeAll()
#if DEBUG

            print("[INDOOR] \(self.indoorTestMode) yawThr=\(self.turnYawThreshold) gyroSpike=\(self.gyroSpikeThreshold) minTurnSpeed=\(self.minSpeedForTurnMS)")

#endif
            
        }
        
    }
    
    private func applyStoppedStateImmediately() {
        if Thread.isMainThread {
            self.isCollectingNow = false
            self.applyBackgroundGpsPolicy()
            self.startIdleBackgroundLocationIfNeeded()
            self.statusText = "Stopped"
            self.appStateText = "Приложение в ожидании"
        } else {
            DispatchQueue.main.sync {
                self.isCollectingNow = false
                self.applyBackgroundGpsPolicy()
                self.startIdleBackgroundLocationIfNeeded()
                self.statusText = "Stopped"
                self.appStateText = "Приложение в ожидании"
            }
        }
    }


    /// UI currently calls stopAll() before NetworkManager.finishTrip(...)
    func stopAll() {
        if !isCollectingNow {
            #if DEBUG
            print("[Session] Stop ignored: not collecting.")
            #endif
            setStatusOnMain("Уже остановлено")
            return
        }

        stopLocation()
        stopMotion()
        stopBatchTimer()

        stopActivityUpdates()
        stopPedometerUpdates()
        stopAltimeterUpdates()
        stopNetworkMonitor()
        stopTripElapsedTimer()
        manualAutoFinish.stop()
        resetLiveServerMetrics()

        let elapsedSnapshot = Double(currentTripElapsedSec)

        if let s = sessionStartedAt {
            sessionEndedAt = s.addingTimeInterval(elapsedSnapshot)
        } else {
            sessionEndedAt = Date()
        }

        flushBuffersNow(enqueueAndWait: true, waitTimeoutSec: 0.8)

        applyStoppedStateImmediately()

        tripStartedAt = nil
        lastDistanceLoc = nil
    }

    func stopAndDrainUploads(completion: @escaping (Bool) -> Void) {
        let t0 = Date()

        if stopInProgress {
            #if DEBUG
            print("[STOP] ignored: stop already in progress")
            #endif
            return
        }
        stopInProgress = true
        defer { stopInProgress = false }


        // 1) Останавливаем сенсоры + ставим последний batch в очередь
        stopAll()

        let t0a = Date()
        #if DEBUG
        print("[STOP] after stopAll dt=\(t0a.timeIntervalSince(t0))s")
        #endif

        // 2) Если сеть оффлайн/маршрута нет — НЕ пытаемся drain/finish.
        // Пусть всё уйдет через pending механизмы при восстановлении сети.
        let offlineNow = !self.isNetworkSatisfied


        if offlineNow {
            #if DEBUG
            print("[STOP] offline -> skip drain/finish; keep pending. dt=\(Date().timeIntervalSince(t0))s")
            #endif
            DispatchQueue.main.async {
                completion(false)
            }
            return
        }

        NetworkManager.shared.drainIngestQueue(timeout: 3.0, pollInterval: 0.25) { drained in
            let t1 = Date()
            #if DEBUG
            print("[STOP] t1 Drain finished drained=\(drained) dt=\(t1.timeIntervalSince(t0))s")
            #endif

            #if DEBUG
            print("[STOP] retryPendingFinishes started (background) dt=\(Date().timeIntervalSince(t0))s")
            #endif

            NetworkManager.shared.retryPendingFinishes { remaining, lastErr in
                #if DEBUG
                print("[STOP] retryPendingFinishes finished (background) remaining=\(remaining) lastErr=\(String(describing: lastErr))")
                #endif
            }

            DispatchQueue.main.async {
                completion(drained)
            }
        }
    }




    // MARK: - Location   

    private func stopLocation() {
        locationManager.stopUpdatingLocation()
        if CLLocationManager.headingAvailable() {
            locationManager.stopUpdatingHeading()
        }
    }
//    старый вариант -он рабочий
//    private func startIdleBackgroundLocationIfNeeded() {
//        guard dayMonitoringKeepAliveEnabled else { return }
//        guard !isCollectingNow else { return }
//        guard locationManager.authorizationStatus == .authorizedAlways else { return }
//
//        // Low-power keep-alive: wakes app on movement
//        locationManager.activityType = .automotiveNavigation
//        locationManager.desiredAccuracy = kCLLocationAccuracyThreeKilometers
//        locationManager.distanceFilter = 200
//        locationManager.pausesLocationUpdatesAutomatically = false
//
//        // Important: significant-change is what keeps background wake-ups alive
//        locationManager.startMonitoringSignificantLocationChanges()
//    }
    
    private func startIdleBackgroundLocationIfNeeded() {
        guard dayMonitoringKeepAliveEnabled else { return }
        guard !isCollectingNow else { return }
        guard locationManager.authorizationStatus == .authorizedAlways else { return }

        locationManager.activityType = .automotiveNavigation
        locationManager.desiredAccuracy = kCLLocationAccuracyThreeKilometers
        locationManager.distanceFilter = 200
        locationManager.pausesLocationUpdatesAutomatically = false

        #if DEBUG
        print("[GPS] startMonitoringSignificantLocationChanges")
        #endif

        locationManager.startMonitoringSignificantLocationChanges()
    }

    private func stopIdleBackgroundLocation() {
        locationManager.stopMonitoringSignificantLocationChanges()
    }



    // MARK: - Motion
    
    private func desiredMotionInterval() -> TimeInterval {
        wantsWaterRate ? waterMotionInterval : appMotionInterval
    }

    private func ensureMotionRunning() {
        let desired = desiredMotionInterval()

        if motionManager.isDeviceMotionActive {
            if abs(currentMotionInterval - desired) < 0.0001 {
                return
            }
            stopMotion()
        }

        startMotion(interval: desired)
    }


    private func startMotion(interval: TimeInterval) {
        guard motionManager.isDeviceMotionAvailable else {
            DispatchQueue.main.async {
                self.statusText = "DeviceMotion not available"
            }
            return
        }

        let available = CMMotionManager.availableAttitudeReferenceFrames()
        if available.contains(.xTrueNorthZVertical) {
            motionReferenceFrame = .xTrueNorthZVertical
        } else if available.contains(.xArbitraryCorrectedZVertical) {
            motionReferenceFrame = .xArbitraryCorrectedZVertical
        } else {
            motionReferenceFrame = .xArbitraryZVertical
        }


        currentMotionInterval = interval
        motionManager.deviceMotionUpdateInterval = interval

        motionManager.startDeviceMotionUpdates(
            using: motionReferenceFrame,
            to: motionQueue
        ) { [weak self] motion, error in
            guard let self else { return }
            if let error = error {
                DispatchQueue.main.async {
                    self.statusText = "Motion error: \(error.localizedDescription)"
                }
                return
            }
            guard let motion else { return }
            self.handleDeviceMotion(motion)
        }
    }


    private func stopMotion() {
        motionManager.stopDeviceMotionUpdates()
    }

    // MARK: - Activity / Pedometer (optional)

    private var activityCurrentType: String = "unknown"
    private var activityLastUpdateAt: Date?
    private var activityDurationsSec: [String: Double] = [:]
    private var activityBestConfidence: String = "low"
    private var nonAutomotiveSince: Date?
    private var activityWindowStartedAt: Date?

    private var pedometerTotalSteps: Int?
    private var pedometerTotalDistanceM: Double?
    private var pedometerCadence: Double?
    private var pedometerPace: Double?
    private var pedometerBaselineSteps: Int?
    private var pedometerBaselineDistanceM: Double?
    
    // Altimeter (per-batch)
    private let altimeter = CMAltimeter()
    private let altimeterQueue = OperationQueue()

    private var altRelAltMin: Double?
    private var altRelAltMax: Double?
    private var altPressureMin: Double?
    private var altPressureMax: Double?

    // Network (per-batch)
    private var pathMonitor: NWPathMonitor? = nil
    private let pathMonitorQueue = DispatchQueue(label: "SensorManager.pathMonitorQueue")

    private var lastNetworkStatus: String?
    private var lastNetworkInterface: String?
    private var lastNetworkExpensive: Bool?
    private var lastNetworkConstrained: Bool?

    // Heading (per-batch)
    private var lastHeadingMagDeg: Double?
    private var lastHeadingTrueDeg: Double?
    private var lastHeadingAccuracyDeg: Double?
    private var lastHeadingAt: Date?
    
    // MARK: - Trip context (per session)
    private var sessionStartedAt: Date?
    private var sessionEndedAt: Date?

    private var trackingMode: String = "single_trip"      // default
    private var transportMode: String = "unknown"         // default

    func setTripContext(trackingMode: String, transportMode: String? = nil) {
        self.trackingMode = trackingMode
        if let tm = transportMode {
            self.transportMode = normalizeTransportMode(tm)
        }
    }

    
    private func normalizeTransportMode(_ s: String) -> String {
        let v = s.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()

        // V2 canonical set (можно расширять)
        switch v {
        case "car", "auto", "automotive":
            return "car"
        case "bus":
            return "bus"
        case "metro", "subway":
            return "metro"
        case "public_transport", "publictransport", "pt":
            return "public_transport"
        case "walk", "walking", "on_foot":
            return "walk"
        case "bike", "bicycle", "cycling":
            return "bike"
        default:
            return "unknown"
        }
    }


    // for NetworkManager.finishTrip
    func getTripDurationSec() -> Double? {
        guard let s = sessionStartedAt else { return nil }
        let e = sessionEndedAt ?? Date()
        return max(0, e.timeIntervalSince(s))
    }

    func getClientEndedAtISO() -> String {
        isoFormatter.string(from: sessionEndedAt ?? Date())
    }



    private func startActivityUpdates() {
        guard CMMotionActivityManager.isActivityAvailable() else { return }

        activityCurrentType = "unknown"
        activityLastUpdateAt = nil
        activityDurationsSec = [:]
        activityBestConfidence = "low"
        nonAutomotiveSince = nil
        activityWindowStartedAt = Date()

        activityQueue.name = "SensorManager.activityQueue"
        activityQueue.qualityOfService = .utility

        activityManager.startActivityUpdates(to: activityQueue) { [weak self] activity in
            guard let self, let activity else { return }
            self.handleActivityUpdate(activity)
        }
    }

    private func stopActivityUpdates() {
        guard CMMotionActivityManager.isActivityAvailable() else { return }
        activityManager.stopActivityUpdates()
    }

    private func handleActivityUpdate(_ activity: CMMotionActivity) {
        let now = Date()
        let newType = mapActivityType(activity)
        let newConf = mapConfidence(activity.confidence)

        movementStateQueue.async {
            if let lastAt = self.activityLastUpdateAt {
                let dt = max(0, now.timeIntervalSince(lastAt))
                self.activityDurationsSec[self.activityCurrentType, default: 0] += dt
            }

            self.activityLastUpdateAt = now
            self.activityCurrentType = newType
            self.activityBestConfidence = self.maxConfidence(self.activityBestConfidence, newConf)
        }
        
        if activityCurrentType == "automotive" {
            nonAutomotiveSince = nil
        } else {
            if nonAutomotiveSince == nil {
                nonAutomotiveSince = now
            }
        }
    }
    
    private func normalizedActivityShares() -> [String: Double] {
        let total = activityDurationsSec.values.reduce(0, +)
        guard total > 0 else {
            return [
                "stationary": 0,
                "walking": 0,
                "running": 0,
                "cycling": 0,
                "automotive": 0,
                "unknown": 1
            ]
        }

        func share(_ key: String) -> Double {
            (activityDurationsSec[key] ?? 0) / total
        }

        return [
            "stationary": share("stationary"),
            "walking": share("walking"),
            "running": share("running"),
            "cycling": share("cycling"),
            "automotive": share("automotive"),
            "unknown": share("unknown")
        ]
    }

    private func dominantActivity(from shares: [String: Double]) -> String {
        shares.max(by: { $0.value < $1.value })?.key ?? "unknown"
    }

    private func currentNonAutomotiveStreakSec(now: Date = Date()) -> Double {
        guard let since = nonAutomotiveSince else { return 0 }
        return max(0, now.timeIntervalSince(since))
    }

    func makeBatchActivityContext() -> ActivityContextBatch {
        let now = Date()
        let shares = normalizedActivityShares()
        let dominant = dominantActivity(from: shares)

        return ActivityContextBatch(
            dominant: dominant,
            best_confidence: activityBestConfidence,
            stationary_share: shares["stationary"] ?? 0,
            walking_share: shares["walking"] ?? 0,
            running_share: shares["running"] ?? 0,
            cycling_share: shares["cycling"] ?? 0,
            automotive_share: shares["automotive"] ?? 0,
            unknown_share: shares["unknown"] ?? 0,
            non_automotive_streak_sec: currentNonAutomotiveStreakSec(now: now),
            is_automotive_now: activityCurrentType == "automotive",
            window_started_at: isoFormatter.string(from: activityWindowStartedAt ?? now),
            window_ended_at: isoFormatter.string(from: now)
        )
    }
    
    func makeBatchScreenInteractionContext() -> ScreenInteractionContextBatch {
        let now = Date()
        let windowStart = screenInteractionWindowStartedAt ?? now
        let recent: Bool

        if let last = lastScreenInteractionAt {
            recent = now.timeIntervalSince(last) <= 10
        } else {
            recent = false
        }

        return ScreenInteractionContextBatch(
            count: screenInteractionCountInBatch,
            recent: recent,
            active_sec: screenInteractionActiveSecInBatch,
            last_at: lastScreenInteractionAt.map { isoFormatter.string(from: $0) },
            window_started_at: isoFormatter.string(from: windowStart),
            window_ended_at: isoFormatter.string(from: now)
        )
    }

    func makeTailActivityContext(windowSec: Double = 120.0, speedGuardResetCount: Int = 0) -> [String: Any] {
        let now = Date()
        let shares = normalizedActivityShares()
        let dominant = dominantActivity(from: shares)

        return [
            "window_sec": windowSec,
            "dominant_last_120s": dominant,
            "best_confidence_last_120s": activityBestConfidence,
            "stationary_share_last_120s": shares["stationary"] ?? 0,
            "walking_share_last_120s": shares["walking"] ?? 0,
            "running_share_last_120s": shares["running"] ?? 0,
            "cycling_share_last_120s": shares["cycling"] ?? 0,
            "automotive_share_last_120s": shares["automotive"] ?? 0,
            "unknown_share_last_120s": shares["unknown"] ?? 0,
            "non_automotive_streak_at_finish_sec": currentNonAutomotiveStreakSec(now: now),
            "is_automotive_at_finish": activityCurrentType == "automotive",
            "speed_guard_reset_count_last_120s": speedGuardResetCount
        ]
    }

    func resetBatchActivityWindow() {
        activityDurationsSec = [:]
        activityWindowStartedAt = Date()
    }
    
    func resetBatchScreenInteractionWindow() {
        screenInteractionCountInBatch = 0
        screenInteractionActiveSecInBatch = 0
        screenInteractionWindowStartedAt = Date()
    }

    private func mapActivityType(_ a: CMMotionActivity) -> String {
        if a.automotive { return "automotive" }
        if a.cycling { return "cycling" }
        if a.running { return "running" }
        if a.walking { return "walking" }
        if a.stationary { return "stationary" }
        return "unknown"
    }

    private func mapConfidence(_ c: CMMotionActivityConfidence) -> String {
        switch c {
        case .low: return "low"
        case .medium: return "medium"
        case .high: return "high"
        @unknown default: return "low"
        }
    }

    private func maxConfidence(_ a: String, _ b: String) -> String {
        let rank: [String: Int] = ["low": 0, "medium": 1, "high": 2]
        return (rank[b] ?? 0) > (rank[a] ?? 0) ? b : a
    }

    private func startPedometerUpdates() {
        guard CMPedometer.isStepCountingAvailable() || CMPedometer.isDistanceAvailable() else { return }

        pedometerTotalSteps = nil
        pedometerTotalDistanceM = nil
        pedometerCadence = nil
        pedometerPace = nil
        pedometerBaselineSteps = nil
        pedometerBaselineDistanceM = nil

        pedometer.startUpdates(from: Date()) { [weak self] data, error in
            guard let self else { return }
            if let error = error {
                self.pushNetworkError("Pedometer error: \(error.localizedDescription)")
                return
            }
            guard let d = data else { return }

            let steps = d.numberOfSteps.intValue
            let dist = d.distance?.doubleValue
            let cadence = d.currentCadence?.doubleValue
            let pace = d.currentPace?.doubleValue

            self.movementStateQueue.async {
                self.pedometerTotalSteps = steps
                self.pedometerTotalDistanceM = dist
                self.pedometerCadence = cadence
                self.pedometerPace = pace
            }
        }
    }

    private func stopPedometerUpdates() {
        pedometer.stopUpdates()
    }

    // Snapshot + reset per-batch (only if your TelemetryBatch supports these fields)
    private func snapshotMovementHintsForBatch() -> (MotionActivityBatch?, PedometerBatch?) {
        let now = Date()

        var motion: MotionActivityBatch? = nil
        var pedo: PedometerBatch? = nil

        movementStateQueue.sync {
            if let lastAt = self.activityLastUpdateAt {
                let dt = max(0, now.timeIntervalSince(lastAt))
                self.activityDurationsSec[self.activityCurrentType, default: 0] += dt
            }
            self.activityLastUpdateAt = now

            if !self.activityDurationsSec.isEmpty {
                let dominant = self.activityDurationsSec.max(by: { $0.value < $1.value })?.key
                motion = MotionActivityBatch(
                    dominant: dominant,
                    confidence: self.activityBestConfidence,
                    durations_sec: self.activityDurationsSec
                )
            }

            self.activityDurationsSec = [:]
            self.activityBestConfidence = "low"

            let curSteps = self.pedometerTotalSteps
            let curDist = self.pedometerTotalDistanceM
            let curCadence = self.pedometerCadence
            let curPace = self.pedometerPace

            if self.pedometerBaselineSteps == nil && self.pedometerBaselineDistanceM == nil {
                if curSteps != nil || curDist != nil {
                    self.pedometerBaselineSteps = curSteps
                    self.pedometerBaselineDistanceM = curDist
                }
                return
            }

            var deltaSteps: Int? = nil
            var deltaDist: Double? = nil

            if let curSteps, let base = self.pedometerBaselineSteps {
                deltaSteps = max(0, curSteps - base)
            }
            if let curDist, let baseD = self.pedometerBaselineDistanceM {
                deltaDist = max(0, curDist - baseD)
            }

            if deltaSteps != nil || deltaDist != nil {
                pedo = PedometerBatch(
                    steps: deltaSteps,
                    distance_m: deltaDist,
                    cadence: curCadence,
                    pace: curPace
                )
            }

            if curSteps != nil { self.pedometerBaselineSteps = curSteps }
            if curDist != nil { self.pedometerBaselineDistanceM = curDist }
        }

        return (motion, pedo)
    }

    // MARK: - UI network error helper

    private func pushNetworkError(_ msg: String) {
        DispatchQueue.main.async {
            self.lastNetworkErrors.insert(msg, at: 0)
            if self.lastNetworkErrors.count > 5 {
                self.lastNetworkErrors = Array(self.lastNetworkErrors.prefix(5))
            }
        }
    }
    
    private func setStatusOnMain(_ s: String) {
        DispatchQueue.main.async {
            self.statusText = s
        }
    }

    private func setAppStateOnMain(_ s: String) {
        DispatchQueue.main.async {
            self.appStateText = s
        }
    }


    // MARK: - GPS quality & mode switching

    private func gpsQualityScore(hAcc: Double, hasCourse: Bool, speed: Double) -> Int {
        let h = max(0.0, hAcc)

        let accScore: Int
        if h <= gpsqGoodHAcc {
            accScore = 100
        } else if h <= gpsqOkayHAcc {
            let k = (h - gpsqGoodHAcc) / (gpsqOkayHAcc - gpsqGoodHAcc)  // 0..1
            accScore = Int(round(100.0 - k * 70.0))                    // 100..30
        } else {
            let k = min(1.0, (h - gpsqOkayHAcc) / 200.0)               // cap at 300m
            accScore = Int(round(30.0 - k * 30.0))                     // 30..0
        }

        let courseBonus = (hasCourse && speed >= gpsqMinSpeedForCourse) ? 10 : 0
        return max(0, min(100, accScore + courseBonus))
    }

    private func updateTelemetryMode(with loc: CLLocation) {
                       
        let now = Date()
        let hAcc = max(0.0, loc.horizontalAccuracy)
        let speed = loc.speed >= 0 ? loc.speed : 0.0
        let hasCourse = (speed >= gpsqMinSpeedForCourse) && (loc.course >= 0)

        gpsqWindow.append(GPSQualitySample(t: now, hAcc: hAcc, hasCourse: hasCourse, speed: speed))
        if gpsqWindow.count > gpsqWindowSize {
            gpsqWindow.removeFirst(gpsqWindow.count - gpsqWindowSize)
        }

        let scores = gpsqWindow.map { s in
            gpsQualityScore(hAcc: s.hAcc, hasCourse: s.hasCourse, speed: s.speed)
        }
        let avg = scores.isEmpty ? 0 : Int(round(Double(scores.reduce(0, +)) / Double(scores.count)))

        let wantGPSAssisted = (avg >= 55)
        let wantIMUOnly = (avg <= 40)

        let canSwitch = now.timeIntervalSince(lastModeChangeAt) >= modeMinHoldSec
        guard canSwitch else { return }

        let prev = telemetryMode

        switch telemetryMode {
        case .imuOnly:
            if wantGPSAssisted {
                telemetryMode = .gpsAssisted
                lastModeChangeAt = now
                suppressEventsUntil = now.addingTimeInterval(1.5)
            }
        case .gpsAssisted:
            if wantIMUOnly {
                telemetryMode = .imuOnly
                lastModeChangeAt = now
                suppressEventsUntil = now.addingTimeInterval(1.5)
            }
        }

        if telemetryMode != prev {
            DispatchQueue.main.async {
                self.statusText = "Running (\(self.telemetryMode.rawValue)) [\(self.currentTelemetryModeString())]"
            }

        }
    }

    // MARK: - Orientation / vector math helpers

    /// Convert vector from device frame to reference frame.
    /// CMRotationMatrix describes rotation from reference -> device, so inverse is transpose.
    private func deviceToReference(_ vDev: SIMD3<Double>, rotationMatrix m: CMRotationMatrix) -> SIMD3<Double> {
        // vRef = M^T * vDev
        let x = m.m11 * vDev.x + m.m21 * vDev.y + m.m31 * vDev.z
        let y = m.m12 * vDev.x + m.m22 * vDev.y + m.m32 * vDev.z
        let z = m.m13 * vDev.x + m.m23 * vDev.y + m.m33 * vDev.z
        return SIMD3<Double>(x, y, z)
    }

    /// Compute (a_long, a_lat, a_vert) in g-units.
    /// Returns also horizontal accel in reference frame (aRefH) for IMU-only calibration.
    private func computeProjectedAccelerations(
        motion: CMDeviceMotion,
        courseRad: Double?
    ) -> (aLong: Double?, aLat: Double?, aVert: Double, aHorizMag: Double, aRefH: SIMD2<Double>) {

        let aDev = SIMD3<Double>(motion.userAcceleration.x, motion.userAcceleration.y, motion.userAcceleration.z)
        let aRef = deviceToReference(aDev, rotationMatrix: motion.attitude.rotationMatrix)

        // vertical (Up) in reference frame
        let aVert = aRef.z

        // horizontal vector (x North, y East when xTrueNorthZVertical)
        let aH = SIMD2<Double>(aRef.x, aRef.y)
        let aHorizMag = sqrt(aH.x * aH.x + aH.y * aH.y)

        let rf = motionReferenceFrame
        let okFrame = (rf == .xTrueNorthZVertical) || (rf == .xArbitraryCorrectedZVertical)

        guard okFrame, let cr = courseRad else {
            return (nil, nil, aVert, aHorizMag, aH)
        }

        // course: 0 = North, 90 = East
        let vHat = SIMD2<Double>(cos(cr), sin(cr))
        let vPerp = SIMD2<Double>(-sin(cr), cos(cr))

        let aLong = Double(aH.x * vHat.x + aH.y * vHat.y)
        let aLat  = Double(aH.x * vPerp.x + aH.y * vPerp.y)

        return (aLong, aLat, aVert, aHorizMag, aH)
    }

    private func detectPhoneMovedSignificantly(gravityDev: SIMD3<Double>) -> Bool {
        guard let prev = lastGravityDevice else {
            lastGravityDevice = gravityDev
            return false
        }

        let n1 = simd_length(prev)
        let n2 = simd_length(gravityDev)
        if n1 <= 1e-9 || n2 <= 1e-9 {
            lastGravityDevice = gravityDev
            return false
        }

        let c = max(-1.0, min(1.0, simd_dot(prev, gravityDev) / (n1 * n2)))
        let angle = acos(c)

        lastGravityDevice = gravityDev
        return angle >= phoneMoveAngleThresholdRad
    }

    // MARK: - IMU-only calibration helpers

    private func resetIMUCalibration(reason: String) {
        covXX = 0; covXY = 0; covYY = 0
        covN = 0
        forwardAxisRef2D = nil
        imuCalibState = .none
        lastSpeedForSign = nil
        signScore = 0
        // (optional) log `reason`
    }
    
    private func normalize2(_ v: SIMD2<Double>) -> SIMD2<Double> {
        let n = sqrt(v.x*v.x + v.y*v.y)
        if n < 1e-9 { return SIMD2<Double>(0, 1) }
        return v / n
    }

    private func principalEigenvector2x2(xx: Double, xy: Double, yy: Double) -> SIMD2<Double>? {
        // symmetric [[xx,xy],[xy,yy]]
        let trace = xx + yy
        let det = xx * yy - xy * xy
        let disc = max(0.0, trace * trace - 4.0 * det)
        let lambda1 = 0.5 * (trace + sqrt(disc)) // largest eigenvalue

        let a = xx - lambda1
        let b = xy

        var v: SIMD2<Double>
        if abs(b) > 1e-9 {
            v = SIMD2<Double>(1.0, -a / b)
        } else {
            v = (xx >= yy) ? SIMD2<Double>(1.0, 0.0) : SIMD2<Double>(0.0, 1.0)
        }

        let n = simd_length(v)
        if n <= 1e-9 { return nil }
        return v / n
    }

    private func updateIMUCalibration(aH: SIMD2<Double>, yawRateZ: Double, speedMS: Double?) {
        let mag = sqrt(aH.x * aH.x + aH.y * aH.y)
        if mag < imuCalibMinHorizG { return }
        if abs(yawRateZ) > imuCalibMaxYawRate { return }
               
        if imuCalibState == .none {
            imuCalibState = .calibrating
            imuCalibStartedAt = Date()
        }

        covXX += aH.x * aH.x
        covXY += aH.x * aH.y
        covYY += aH.y * aH.y
        covN += 1

        if covN % imuCalibUpdateEvery == 0 {
            if let v = principalEigenvector2x2(xx: covXX, xy: covXY, yy: covYY) {
                forwardAxisRef2D = v

                // sign disambiguation using speed delta if available
                if let sp = speedMS {
                    if let last = lastSpeedForSign {
                        let dV = sp - last
                        let aLongCand = Double(aH.x * v.x + aH.y * v.y)
                        signScore += dV * aLongCand
                    }
                    lastSpeedForSign = sp
                }

                let wasReady = (imuCalibState == .ready)

                if let t0 = imuCalibStartedAt {
                    let elapsed = Date().timeIntervalSince(t0)
                    if elapsed >= imuCalibTargetSeconds, covN >= 40 {
                        imuCalibState = .ready
                    }
                } else if covN >= imuCalibMinSamples {
                    imuCalibState = .ready
                }

                // Логируем только на переходе в ready (один раз)
                if !wasReady, imuCalibState == .ready {
#if DEBUG

                    print("[AXIS] READY covN=\(covN) axis=\(String(describing: forwardAxisRef2D)) sign=\(signScore)")

#endif
                }
            }
        }
    }

    private func calibratedAxisWithSign() -> SIMD2<Double>? {
        guard let v = forwardAxisRef2D else { return nil }
        return (signScore < 0) ? -v : v
    }
    
    

    // MARK: - Main motion handler

    private func handleDeviceMotion(_ motion: CMDeviceMotion) {
        let accel = motion.userAcceleration
        let rot = motion.rotationRate
        let att = motion.attitude
        let grav = motion.gravity

        let now = Date()
        let tISO = isoFormatter.string(from: now)
        
        // === Live «water glass» params (updated regardless of telemetry suppression) ===
        if wantsWaterRate {
            waterGameManager.process(
                attitude: att,
                accel: accel,
                rotationRate: rot
            )
        }
        
        
        // --- Heavy work throttle (samples/events/UI debug) ---
        let dueForTelemetry: Bool = {
            let dt = now.timeIntervalSince(lastTelemetryTickAt)
            if dt >= telemetryInterval {
                lastTelemetryTickAt = now
                return true
            }
            return false
        }()

        // Если не пришло время — НЕ делаем тяжелый bufferQueue.async / эвенты / строки
        // Но “воду” ты уже обновил выше, так что UX живой.
        if !dueForTelemetry {
            return
        }


        

        // Snapshot shared state
        let snapshot: (loc: CLLocation?, speedMS: Double?, mode: TelemetryMode, suppressUntil: Date, courseRad: Double?) = bufferQueue.sync {
            let l = lastKnownLocation
            let s: Double? = (lastKnownSpeedMS != nil && lastKnownSpeedMS! >= 0) ? Double(lastKnownSpeedMS!) : nil
            let mode = telemetryMode
            let sup = suppressEventsUntil

            var courseRad: Double? = nil
            if let cr = lastCourseRad, let at = lastCourseAt, now.timeIntervalSince(at) <= 10.0 {
                courseRad = cr
            }

            return (l, s, mode, sup, courseRad)
        }

        let loc = snapshot.loc
        let speedMS = snapshot.speedMS
        let mode = snapshot.mode
        let suppressNow = (now < snapshot.suppressUntil)
        
        // NOTE: suppressNow applies only to telemetry EVENTS, not samples.
        let suppressEventsOnly = suppressNow


        
        let gpsOK: Bool = {
            guard let l = loc else { return false }
            if now.timeIntervalSince(l.timestamp) > self.maxLocationAge { return false }
            if l.horizontalAccuracy < 0 || l.horizontalAccuracy > self.maxHorizontalAccuracy { return false }
            return true
        }()
        
        
        bufferQueue.async { [weak self] in
            guard let self else { return }

            // Detect phone moved -> suppress events + reset IMU calibration
            let gDev = SIMD3<Double>(grav.x, grav.y, grav.z)
//            if self.detectPhoneMovedSignificantly(gravityDev: gDev) {
//                print("[PHONE_MOVED] now=\(tISO) suppressUntil=\(self.suppressEventsUntil)")
//                self.suppressEventsUntil = max(self.suppressEventsUntil, now.addingTimeInterval(self.phoneMoveSuppressSec))
//                self.resetIMUCalibration(reason: "phoneMoved")
//            }
            if !self.indoorTestMode {
                if self.detectPhoneMovedSignificantly(gravityDev: gDev) {
                    self.suppressEventsUntil = max(
                        self.suppressEventsUntil,
                        now.addingTimeInterval(self.phoneMoveSuppressSec)
                    )
                    self.resetIMUCalibration(reason: "phoneMoved")
                }
            }

//            // iOS 15+: speed/course accuracy
//            var speedAcc: Double? = nil
//            var courseAcc: Double? = nil
//            if let loc = loc {
//                if #available(iOS 15.0, *) {
//                    if loc.speedAccuracy >= 0 { speedAcc = loc.speedAccuracy }
//                    if loc.courseAccuracy >= 0 { courseAcc = loc.courseAccuracy }
//                }
//            }
            
            // iOS can return negative accuracies to indicate "invalid"
            let speedAccToSend: Double? = {
                guard let l = loc else { return nil }
                if #available(iOS 10.0, *) {
                    let v = l.speedAccuracy
                    return v >= 0 ? v : nil
                }
                return nil
            }()

            let courseAccToSend: Double? = {
                guard let l = loc else { return nil }
                if #available(iOS 13.4, *) {
                    let v = l.courseAccuracy
                    return v >= 0 ? v : nil
                }
                return nil
            }()
            

            // --- Speed selection logic ---
            let gpsSpeed: Double? = {
                guard gpsOK, let s = loc?.speed, s >= 0 else { return nil }
                return s
            }()

 
            
            // If GPS speed is bad or missing — fallback to IMU speed
                   
            let speedToSend: Double? = {
                // Prefer GPS when available, otherwise fallback to IMU-estimated speed
                if let gps = gpsSpeed { return gps }
                if let s = speedMS, s > 0 { return s }
                return nil
            }()
            
            // V2: speed_m_s is MUST for every event -> never send nil
            // Samples: keep "unknown" as -1.0 (server should treat as unknown)
            let speedForSample: Double = speedToSend ?? -1.0
            
            // Events: must be present; if unknown keep 0.0 (or switch to -1.0 if server supports it)
            let speedForEvent: Double = speedToSend ?? 0.0
            
            // Unified speed gate for all detectors (use -1 when unknown)
            let speedGateMS: Double = speedToSend ?? -1.0
            
            

            

            // Orientation-robust accelerations (compute ONCE)
            let proj = self.computeProjectedAccelerations(motion: motion, courseRad: snapshot.courseRad)
            
            // Seed IMU forward axis from GPS course once (so IMU projection works even if ref-frame later becomes non-north)
            do {
                let rf = motionReferenceFrame
                let okFrame = (rf == .xTrueNorthZVertical) || (rf == .xArbitraryCorrectedZVertical)

                if okFrame,
                   let cr = snapshot.courseRad,
                   let sp = speedMS,
                   sp >= self.minSpeedForAccelBrakeMS {

                    let vHat = SIMD2<Double>(cos(cr), sin(cr))  // GPS forward in reference plane

                    if let v = self.forwardAxisRef2D {
                        // мягкая коррекция оси GPS'ом
                        self.forwardAxisRef2D = normalize2(v * 0.9 + vHat * 0.1)
                    } else {
                        // initial seed
                        self.forwardAxisRef2D = vHat
                        self.signScore = 1.0
                        self.imuCalibState = .ready
                    }
                }
            }

            //калибруемся всегда, независимо от режима, просто не во время suppress. Это обычно и есть “IMU-first”
            if !(now < self.suppressEventsUntil) {
                self.updateIMUCalibration(aH: proj.aRefH, yawRateZ: rot.z, speedMS: speedMS)
            }

            // Compute aLong/aLat for IMU-only using calibrated axis (compute ONCE)
            var aLongIMU: Double? = nil
            var aLatIMU: Double? = nil
            // IMU projection is valuable as a fallback even in gpsAssisted mode
            if let axis = self.calibratedAxisWithSign() {
                 let vPerp = SIMD2<Double>(-axis.y, axis.x)
                 aLongIMU = Double(proj.aRefH.x * axis.x + proj.aRefH.y * axis.y)
                 aLatIMU  = Double(proj.aRefH.x * vPerp.x + proj.aRefH.y * vPerp.y)
            }
            else {
                // fallback until axis calibration happens
                aLongIMU = Double(proj.aRefH.y)
                aLatIMU  = Double(proj.aRefH.x)
            }
             

            // Trust GPS-assisted projection ONLY when course is trustworthy
            let gpsCourseTrusted: Bool = {
                guard mode == .gpsAssisted else { return false }
                guard snapshot.courseRad != nil else { return false }
                // Require non-trivial speed (course at low speed is noisy)
                if speedGateMS >= 0 && speedGateMS < self.gpsqMinSpeedForCourse { return false }
                // If courseAccuracy is available, require it to be reasonable
                if let ca = courseAccToSend, ca > 15 { return false } // degrees
                return true
            }()
            
            // Canonical values to send (V2): prefer GPS projection if trusted, else IMU projection
            let aLongToSend: Double? = ((gpsCourseTrusted) ? proj.aLong : nil) ?? aLongIMU
#if DEBUG
if debugPrintsEnabled && self.tripStartedAt != nil {
    print("[DBG] gpsTrusted=\(gpsCourseTrusted) axis=\(self.calibratedAxisWithSign() != nil) aLongIMU=\(aLongIMU.map{String(format:"%.3f",$0)} ?? "nil") proj.aLong=\(String(describing: proj.aLong)) aLongToSend=\(aLongToSend.map{String(format:"%.3f",$0)} ?? "nil") speed=\(String(format:"%.2f", speedGateMS))")
}
#endif
            let aLatToSend: Double?  = ((gpsCourseTrusted) ? proj.aLat  : nil) ?? aLatIMU
            
            let aVertToSend: Double? = proj.aVert
            
            let src = gpsCourseTrusted ? "GPS_proj" : (self.calibratedAxisWithSign() != nil ? "IMU_proj" : "no_axis")

            if self.shouldUpdateUI(now: now) {
                DispatchQueue.main.async {
                    self.lastProjString = String(
                        format: "%@ | aLong: %.2f  aLat: %.2f  aVert: %.2f",
                        src,
                        aLongToSend ?? 0.0,
                        aLatToSend ?? 0.0,
                        aVertToSend ?? 0.0
                    )
                }
            }
            
            let safeSpeedForSample = NumericSanitizer.raw(speedForSample)
            let safeSpeedForEvent = NumericSanitizer.metric(speedForEvent)
            

            let safeSpeedAccToSend  = NumericSanitizer.metricOptional(speedAccToSend)
            let safeCourseAccToSend = NumericSanitizer.metricOptional(courseAccToSend)

            let safeALongToSend = NumericSanitizer.rawOptional(aLongToSend)
            let safeALatToSend  = NumericSanitizer.rawOptional(aLatToSend)
            let safeAVertToSend = NumericSanitizer.rawOptional(aVertToSend)
            
            let safeHAcc = NumericSanitizer.metricOptional(loc?.horizontalAccuracy)
            let safeVAcc = NumericSanitizer.metricOptional(loc?.verticalAccuracy)



            // V2: speed_m_s MUST be present in every sample.
            // If unknown, send sentinel -1.0 (server should treat as "unknown").
            

           
            
            let sample = TelemetrySample(
                t: tISO,
                lat: loc?.coordinate.latitude,
                lon: loc?.coordinate.longitude,
                hAcc: safeHAcc,
                vAcc: safeVAcc,

                speed_m_s: safeSpeedForSample,
                speedAcc: safeSpeedAccToSend,

                course: (loc?.course ?? -1) >= 0 ? loc?.course : nil,
                courseAcc: safeCourseAccToSend,

                accel: Accel(x: accel.x, y: accel.y, z: accel.z),
                rotation: Rotation(x: rot.x, y: rot.y, z: rot.z),
                attitude: Attitude(yaw: att.yaw, pitch: att.pitch, roll: att.roll),

                a_long_g: safeALongToSend,
                a_lat_g: safeALatToSend,
                a_vert_g: safeAVertToSend
            )


            self.sampleBuffer.append(sample)

            // suppression for EVENTS only
            let suppress2 = suppressEventsOnly || (now < self.suppressEventsUntil)

            // --- V2 per-tick gating flags (prevents combined-risk from double-classifying) ---
            var firedV2AccelThisTick = false
            var firedV2BrakeThisTick = false
            var firedV2TurnThisTick  = false   // если хочешь, отмечай в turn-блоке

            
            // ===== V2 road anomaly (vertical) =====
            if !suppress2,
               // road anomalies считаем даже на малой скорости; но если speed неизвестна (-1) — разрешаем
                  (speedGateMS < 0 || speedGateMS >= self.minSpeedForRoadMS),
            let aVert = aVertToSend {

                // 1) maintain rolling buffer
                vertBuffer.append(VertPoint(t: now, aVertG: aVert))

                let cutoff = now.addingTimeInterval(-roadWindowS)
                while let first = vertBuffer.first, first.t < cutoff {
                    vertBuffer.removeFirst()
                }

                // 2) compute metrics within window
                if vertBuffer.count >= 3 {
                    var maxAbs: Double = 0
                    var maxVal: Double = -Double.greatestFiniteMagnitude
                    var minVal: Double =  Double.greatestFiniteMagnitude

                    for p in vertBuffer {
                        let v = p.aVertG
                        maxAbs = max(maxAbs, abs(v))
                        maxVal = max(maxVal, v)
                        minVal = min(minVal, v)
                    }

                    let p2p = maxVal - minVal

                    // 3) severity thresholds
                    let severity: String?
                    if p2p >= roadHighP2PG || maxAbs >= roadHighAbsG { severity = "high" }
                    else if p2p >= roadLowP2PG || maxAbs >= roadLowAbsG { severity = "low" }
                    else { severity = nil }

                    if let severity {

                        // 4) cooldown
                        let canFire = (lastRoadEventAt == nil) || (now.timeIntervalSince(lastRoadEventAt!) >= roadCooldownS)
                        if canFire {
                            lastRoadEventAt = now

                            // 5) subtype heuristic (placeholder)
                            let subtype: String
                            let hasDip = (minVal <= -0.35)
                            let hasBump = (maxVal >= 0.35)
                            if hasDip && hasBump { subtype = "pothole" }
                            else {
                                let windowIsFull = (vertBuffer.first != nil) && (now.timeIntervalSince(vertBuffer.first!.t) >= (roadWindowS * 0.85))
                                subtype = windowIsFull ? "speed_bump" : "bump"
                            }

                            // 6) meta_json
                            let safeMaxAbs = NumericSanitizer.metric(maxAbs, digits: 3)
                            let safeP2P    = NumericSanitizer.metric(p2p, digits: 3)
                            let safeWindow = NumericSanitizer.metric(roadWindowS, digits: 2)
                            let safeMaxVal = NumericSanitizer.metric(maxVal, digits: 3)
                            let safeMinVal = NumericSanitizer.metric(minVal, digits: 3)

                            let meta = """
                            {"peak_abs_vert_g":\(String(format: "%.3f", safeMaxAbs)),
                             "peak_p2p_vert_g":\(String(format: "%.3f", safeP2P)),
                             "window_s":\(String(format: "%.2f", safeWindow)),
                             "max_vert_g":\(String(format: "%.3f", safeMaxVal)),
                             "min_vert_g":\(String(format: "%.3f", safeMinVal))}
                            """
                            .replacingOccurrences(of: "\n", with: "")
                            .replacingOccurrences(of: " ", with: "")
                            
                            DispatchQueue.main.async {
                                self.currentTripRoadAnomalyDeviceCount += 1
                            }

                            self.eventsBuffer.append(
                                TelemetryEvent(
                                    type: .roadAnomaly,
                                    t: tISO,
                                    intensity: NumericSanitizer.metric(p2p),
                                    details: "v2 road_anomaly subtype=\(subtype) severity=\(severity)",
                                    origin: "client",
                                    algo_version: "v2",
                                    speed_m_s: safeSpeedForEvent,
                                    eventClass: nil,
                                    subtype: subtype,
                                    severity: severity,
                                    meta_json: meta
                                )
                            )
                            self.recordAgg(.road, intensity: p2p)
                            
                            #if DEBUG
                            print("[V2][ROAD] t=\(tISO) aVert=\(String(format: "%.3f", aVert)) speed=\(String(format: "%.1f", speedForEvent))")
                            #endif
                            
                        }
                    }
                }
            }



            // IMPORTANT: do not recompute aLongIMU/aLatIMU here.
            // Reuse values computed above (before building the sample).
            
            // ===== V2 turn (gyro-first; lateral used for severity when available) =====
            if !suppress2,
               (speedGateMS < 0 || speedGateMS >= self.minSpeedForTurnMS) {

                // Optional suppression: don't classify a turn immediately after a strong brake/accel
                let recentlyBraked = (self.lastBrakeEventAt != nil) && (now.timeIntervalSince(self.lastBrakeEventAt!) < 0.4)
                let recentlyAccel  = (self.lastAccelEventAt != nil) && (now.timeIntervalSince(self.lastAccelEventAt!) < 0.4)

                if !(recentlyBraked || recentlyAccel) {

                    let yawAbsRaw = abs(rot.z)
                    let yawAbs = min(yawAbsRaw, 3.0)
                   
                    if yawAbsRaw > gyroSpikeThreshold {
                        if let last = lastGyroSpikeAt, now.timeIntervalSince(last) < 2.0 {
                            // уже недавно сбрасывали — не делаем reset снова
                        } else {
                            lastGyroSpikeAt = now
                            self.suppressEventsUntil = now.addingTimeInterval(2.0)
                            self.resetIMUCalibration(reason: "gyro spike (phone moved)")
#if DEBUG

                            print("[GYRO_SPIKE] raw=\(yawAbsRaw) thr=\(gyroSpikeThreshold) -> suppress 2s + resetIMUCalibration")

#endif
                        }
                    }
                    

                    // Primary detection: yaw rate
                    if yawAbs >= self.turnYawThreshold {

                        // Cooldown gate
                        let canFire = (self.lastTurnEventAt == nil) || (now.timeIntervalSince(self.lastTurnEventAt!) >= self.turnCooldown)
                        if canFire {
                            self.lastTurnEventAt = now
                            firedV2TurnThisTick = true

                            // classify by aLat if present; otherwise classify by yaw rate
                            let aLatAbs: Double? = aLatToSend.map { abs($0) }

                            let turnCls: String = {
                                if let x = aLatAbs {
                                    if x >= self.turnEmergencyLatG { return "emergency" }
                                    if x >= self.turnSharpLatG { return "sharp" }
                                    return "sharp"
                                } else {
                                    if yawAbs >= (self.turnYawThreshold * 1.4) { return "emergency" }
                                    return "sharp"
                                }
                            }()

                            let intensity = NumericSanitizer.metric(aLatAbs ?? yawAbs)

                            self.eventsBuffer.append(
                                TelemetryEvent(
                                    type: .turn,
                                    t: tISO,
                                    intensity: NumericSanitizer.metric(intensity),
                                    details: "v2 turn gyro-first cls=\(turnCls) yawAbs=\(String(format: "%.3f", yawAbs)) a_lat_g=\(aLatAbs != nil ? String(format: "%.3f", aLatAbs!) : "—")",
                                    origin: "client",
                                    algo_version: "v2",
                                    speed_m_s: safeSpeedForEvent,
                                    eventClass: turnCls,
                                    subtype: nil,
                                    severity: nil,
                                    meta_json: nil
                                )
                            )
                            
                            self.recordAgg(.turn, intensity: intensity)

                            DispatchQueue.main.async {
                                self.currentTripSuddenTurnDeviceCount += 1
                            }
                            
                            #if DEBUG
                            print("[V2][TURN] t=\(tISO) yaw=\(String(format: "%.3f", abs(rot.z))) aLat=\(aLatToSend != nil ? String(format: "%.3f", abs(aLatToSend!)) : "—") speed=\(String(format: "%.1f", speedForEvent))")
                            #endif
                        }
                    }
                }
            }
            


            // ===== V2 brake (a_long_g negative; guarded vs turn + per-tick gating) =====
            
            if !suppress2,
               (speedGateMS < 0 || speedGateMS >= self.minSpeedForAccelBrakeMS)
,
               !firedV2BrakeThisTick,
               !firedV2AccelThisTick,
               let aLong = aLongToSend {

                // brake is negative longitudinal acceleration
                if aLong <= -brakeSharpLongG {

                    // don't classify brake right after a turn (reduces mislabels)
                    let recentlyTurned = (self.lastTurnEventAt != nil) && (now.timeIntervalSince(self.lastTurnEventAt!) < 0.35)
                    if !recentlyTurned {

                        // keep your existing cooldown
                        let canFire = (self.lastBrakeEventAt == nil) || (now.timeIntervalSince(self.lastBrakeEventAt!) >= self.accelBrakeCooldown)
                        if canFire {
                            self.lastBrakeEventAt = now
                            firedV2BrakeThisTick = true
                           
                            let mag = abs(aLong)

                            let brakeCls: String
                            if mag >= brakeEmergencyLongG { brakeCls = "emergency" }
                            else { brakeCls = "sharp" }

                            self.eventsBuffer.append(
                                TelemetryEvent(
                                    type: .brake,
                                    t: tISO,
                                    intensity: NumericSanitizer.metric(mag),
                                    details: "v2 brake cls=\(brakeCls) a_long_g=\(String(format: "%.3f", aLong))",
                                    origin: "client",
                                    algo_version: "v2",
                                    speed_m_s: safeSpeedForEvent,
                                    eventClass: brakeCls,
                                    subtype: nil,
                                    severity: nil,
                                    meta_json: nil
                                )
                            
                            )
                            
                            self.recordAgg(.brake, intensity: mag)

                            DispatchQueue.main.async {
                                self.currentTripSuddenBrakeDeviceCount += 1
                            }
                            #if DEBUG
                            print("[V2][BRAKE] t=\(tISO) aLong=\(String(format: "%.3f", aLong)) speed=\(String(format: "%.1f", speedForEvent)) firedTurn=\(firedV2TurnThisTick)")
                            #endif
                        }
                    }
                }
            }
            

            // ===== V2 accel (a_long_g positive; mutual exclusion + cooldown) =====
            if !suppress2,
               (speedGateMS < 0 || speedGateMS >= self.minSpeedForAccelBrakeMS),
               !firedV2AccelThisTick,                       // don't fire twice in one tick
               !firedV2BrakeThisTick,                       // mutual exclusion within tick
               let aLong = aLongToSend {

                // accel is positive longitudinal acceleration
                if aLong >= self.accelSharpLongG {

                    // Optional suppression window: don't classify accel right after a turn
                    let recentlyTurned = (self.lastTurnEventAt != nil) && (now.timeIntervalSince(self.lastTurnEventAt!) < 0.35)
                    if !recentlyTurned {

                        let canFire = (self.lastAccelEventAt == nil) || (now.timeIntervalSince(self.lastAccelEventAt!) >= self.accelBrakeCooldown)
                        if canFire {
                            
                            self.lastAccelEventAt = now
                            firedV2AccelThisTick = true

                            let mag = NumericSanitizer.metric(aLong)

                            let cls: String = {
                                if mag >= self.accelEmergencyLongG { return "emergency" }
                                return "sharp"
                            }()

                            self.eventsBuffer.append(
                                TelemetryEvent(
                                    type: .accel,
                                    t: tISO,
                                    intensity: mag,
                                    details: "v2 accel cls=\(cls) a_long_g=\(String(format: "%.3f", aLong))",
                                    origin: "client",
                                    algo_version: "v2",
                                    speed_m_s: safeSpeedForEvent,
                                    eventClass: cls,
                                    subtype: nil,
                                    severity: nil,
                                    meta_json: nil
                                )
                            )
                            
                            self.recordAgg(.accel, intensity: mag)

                            DispatchQueue.main.async {
                                self.currentTripSuddenAccelDeviceCount += 1
                            }
                            
                            #if DEBUG
                            print("[V2][ACCEL] t=\(tISO) aLong=\(String(format: "%.3f", aLong)) speed=\(String(format: "%.1f", speedForEvent)) firedTurn=\(firedV2TurnThisTick)")
                            #endif
                        }
                    }
                }
            }
            



            
            // ===== V2 combined risk (skid risk) =====
            let primaryFired = firedV2BrakeThisTick || firedV2AccelThisTick || firedV2TurnThisTick

            if !suppress2,
               (speedGateMS < 0 || speedGateMS >= combinedMinSpeedMS),
               !primaryFired {

                if let aLong = aLongToSend, let aLat = aLatToSend {

                    let aLatAbs = abs(aLat)

                    if aLatAbs >= combinedLatMinG {

                        // brake_in_turn
                        let brakeCls: String?
                        if aLong <= -brakeInTurnEmergencyG { brakeCls = "emergency" }
                        else if aLong <= -brakeInTurnSharpG { brakeCls = "sharp" }
                        else { brakeCls = nil }

                        if let brakeCls {
                            let canFireBrake = (lastBrakeInTurnAt == nil) || (now.timeIntervalSince(lastBrakeInTurnAt!) >= combinedCooldownS)
                            if canFireBrake {
                                lastBrakeInTurnAt = now
                                
                                let safeALong = NumericSanitizer.metric(aLong, digits: 3)
                                let safeALat  = NumericSanitizer.metric(aLat, digits: 3)
                                let safeLatMin = NumericSanitizer.metric(combinedLatMinG, digits: 3)
                                
                                let meta = """
                                {"a_long_g":\(String(format: "%.3f", safeALong)),
                                 "a_lat_g":\(String(format: "%.3f", safeALat)),
                                 "lat_min_g":\(String(format: "%.3f", safeLatMin))}
                                """
                                .replacingOccurrences(of: "\n", with: "")
                                .replacingOccurrences(of: " ", with: "")
                                
                                self.eventsBuffer.append(
                                    TelemetryEvent(
                                        type: .brakeInTurn,
                                        t: tISO,
                                        intensity: NumericSanitizer.metric(abs(aLong)),
                                        details: "v2 brake_in_turn cls=\(brakeCls)",
                                        origin: "client",
                                        algo_version: "v2",
                                        speed_m_s: safeSpeedForEvent,
                                        eventClass: brakeCls,
                                        subtype: nil,
                                        severity: nil,
                                        meta_json: meta
                                    )
                                )
                                
                                self.recordAgg(.brakeInTurn, intensity: abs(aLong))
                                
                                #if DEBUG
                                print("[V2][COMBINED] t=\(tISO) aLong=\(String(format: "%.3f", aLong)) aLat=\(String(format: "%.3f", aLat)) speed=\(String(format: "%.1f", speedForEvent)) primaryFired=\(firedV2BrakeThisTick || firedV2AccelThisTick || firedV2TurnThisTick)")
                                #endif
                            }
                        }

                        // accel_in_turn
                        let accelCls: String?
                        if aLong >= accelInTurnEmergencyG { accelCls = "emergency" }
                        else if aLong >= accelInTurnSharpG { accelCls = "sharp" }
                        else { accelCls = nil }

                        if let accelCls {
                            let canFireAccel = (lastAccelInTurnAt == nil) || (now.timeIntervalSince(lastAccelInTurnAt!) >= combinedCooldownS)
                            if canFireAccel {
                                lastAccelInTurnAt = now

                                let meta = """
                                {"a_long_g":\(String(format: "%.3f", aLong)),
                                 "a_lat_g":\(String(format: "%.3f", aLat)),
                                 "lat_min_g":\(String(format: "%.3f", combinedLatMinG))}
                                """.replacingOccurrences(of: "\n", with: "").replacingOccurrences(of: " ", with: "")

                                self.eventsBuffer.append(
                                    TelemetryEvent(
                                        type: .accelInTurn,
                                        t: tISO,
                                        intensity: NumericSanitizer.metric(abs(aLong)),
                                        details: "v2 accel_in_turn cls=\(accelCls)",
                                        origin: "client",
                                        algo_version: "v2",
                                        speed_m_s: safeSpeedForEvent,
                                        eventClass: accelCls,
                                        subtype: nil,
                                        severity: nil,
                                        meta_json: meta
                                    )
                                )
                                
                                self.recordAgg(.accelInTurn, intensity: abs(aLong))
                            }
                        }
                    }

                }
            }


            // safety cap for RAM
            if self.sampleBuffer.count > 5000 {
                self.sampleBuffer.removeFirst(1000)
            }
            
            // DEBUG: last fired event
            if self.eventsBuffer.count > self.lastDebugEventIndex {
                self.lastDebugEventIndex = self.eventsBuffer.count
                if let e = self.eventsBuffer.last {
                    let cls = e.eventClass ?? "—"
                    let sub = e.subtype ?? "—"
                    let sev = e.severity ?? "—"

                    if self.shouldUpdateUI(now: now) {
                        DispatchQueue.main.async {
                            self.lastFiredEventString =
                                "\(e.type.rawValue) | cls=\(cls) sub=\(sub) sev=\(sev) int=\(String(format: "%.3f", e.intensity))"
                        }
                    }

                }
            }


            // Status hint
            if self.shouldUpdateUI(now: now) {
                DispatchQueue.main.async {
                    let gpsOrImu = self.currentTelemetryModeString()
                    if suppress2 {
                        self.statusText = "Running (\(mode.rawValue)) [\(gpsOrImu)] — stabilizing…"
                    } else {
                        self.statusText = "Running (\(mode.rawValue)) [\(gpsOrImu)] calib=\(self.imuCalibState.rawValue)"
                    }
                    self.telemetryModeText = gpsOrImu
                }
            }

        }

        // UI numbers
        let mag = sqrt(accel.x * accel.x + accel.y * accel.y + accel.z * accel.z)
        if self.shouldUpdateUI(now: now) {
            DispatchQueue.main.async {
                self.lastUserAccelString = String(
                    format: "ua  x: %.2f  y: %.2f  z: %.2f",
                    accel.x, accel.y, accel.z
                )

                self.lastRotRateString = String(
                    format: "rr  x: %.2f  y: %.2f  z: %.2f",
                    rot.x, rot.y, rot.z
                )

                self.lastAccelString = String(
                    format: "x: %.2f y: %.2f z: %.2f",
                    accel.x, accel.y, accel.z
                )

                self.accelMagnitudeString = String(format: "‖a‖ = %.2f", mag)

                if let s = speedMS {
                    self.lastSpeedString = String(format: "%.1f km/h", s * 3.6)
                } else {
                    self.lastSpeedString = "—"
                }
            }
        }

    }

    // MARK: - Batching

    private var batchGCDTimer: DispatchSourceTimer?

    private func startBatchTimer() {
        let t = DispatchSource.makeTimerSource(queue: bufferQueue)
        t.schedule(deadline: .now() + batchInterval, repeating: batchInterval, leeway: .milliseconds(200))
        t.setEventHandler { [weak self] in
            self?.flushBuffersNow()
        }
        batchGCDTimer?.cancel()
        batchGCDTimer = t
        t.resume()
    }

    private func stopBatchTimer() {
        batchGCDTimer?.cancel()
        batchGCDTimer = nil
    }
    
    private func startAltimeterUpdates() {
        guard CMAltimeter.isRelativeAltitudeAvailable() else { return }

        altRelAltMin = nil
        altRelAltMax = nil
        altPressureMin = nil
        altPressureMax = nil

        altimeter.startRelativeAltitudeUpdates(to: altimeterQueue) { [weak self] data, _ in
            guard let self, let d = data else { return }

            let relAlt = d.relativeAltitude.doubleValue
            let pressure = d.pressure.doubleValue

            self.movementStateQueue.async {
                self.altRelAltMin = self.altRelAltMin.map { min($0, relAlt) } ?? relAlt
                self.altRelAltMax = self.altRelAltMax.map { max($0, relAlt) } ?? relAlt
                self.altPressureMin = self.altPressureMin.map { min($0, pressure) } ?? pressure
                self.altPressureMax = self.altPressureMax.map { max($0, pressure) } ?? pressure
            }
        }
    }

    private func stopAltimeterUpdates() {
        guard CMAltimeter.isRelativeAltitudeAvailable() else { return }
        altimeter.stopRelativeAltitudeUpdates()
    }

    private func startNetworkMonitor() {
            let m = NWPathMonitor()
            self.pathMonitor = m
            m.pathUpdateHandler = { [weak self] path in
                guard let self else { return }

            let status: String
            switch path.status {
            case .satisfied: status = "satisfied"
            case .unsatisfied: status = "unsatisfied"
            case .requiresConnection: status = "requires_connection"
            @unknown default: status = "unknown"
            }

            let iface: String
            if path.usesInterfaceType(.wifi) { iface = "wifi" }
            else if path.usesInterfaceType(.cellular) { iface = "cellular" }
            else if path.usesInterfaceType(.wiredEthernet) { iface = "wired" }
            else { iface = "other" }

            self.movementStateQueue.async {
                self.lastNetworkStatus = status
                self.lastNetworkInterface = iface
                self.lastNetworkExpensive = path.isExpensive
                self.lastNetworkConstrained = path.isConstrained
            }
                
            DispatchQueue.main.async {
                self.isNetworkSatisfied = (status == "satisfied")
            }

        }
        m.start(queue: pathMonitorQueue)
    }

    private func stopNetworkMonitor() {
        pathMonitor?.cancel()
            pathMonitor = nil        
    }
    
    private func snapshotBatchContext() -> (AltimeterBatch?, DeviceStateBatch?, NetworkBatch?, HeadingBatch?) {

        var alt: AltimeterBatch?
        var net: NetworkBatch?
        var head: HeadingBatch?

        movementStateQueue.sync {
            if altRelAltMin != nil || altRelAltMax != nil {
                alt = AltimeterBatch(
                    rel_alt_m_min: altRelAltMin,
                    rel_alt_m_max: altRelAltMax,
                    pressure_kpa_min: altPressureMin,
                    pressure_kpa_max: altPressureMax
                )
            }

            altRelAltMin = nil
            altRelAltMax = nil
            altPressureMin = nil
            altPressureMax = nil

            net = NetworkBatch(
                status: lastNetworkStatus,
                interface: lastNetworkInterface,
                expensive: lastNetworkExpensive,
                constrained: lastNetworkConstrained
            )

            if let at = lastHeadingAt, Date().timeIntervalSince(at) <= 15 {
                head = HeadingBatch(
                    magnetic_deg: lastHeadingMagDeg,
                    true_deg: lastHeadingTrueDeg,
                    accuracy_deg: lastHeadingAccuracyDeg
                )
            }
        }

        let level = UIDevice.current.batteryLevel
        let dev = DeviceStateBatch(
            battery_level: level >= 0 ? Double(level) : nil,
            battery_state: mapBatteryState(UIDevice.current.batteryState),
            low_power_mode: ProcessInfo.processInfo.isLowPowerModeEnabled
            
        )

        return (alt, dev, net, head)
    }

    private func mapBatteryState(_ st: UIDevice.BatteryState) -> String {
        switch st {
        case .unknown: return "unknown"
        case .unplugged: return "unplugged"
        case .charging: return "charging"
        case .full: return "full"
        @unknown default: return "unknown"
        }
    }




    private func flushBuffersNow(enqueueAndWait: Bool = false, waitTimeoutSec: TimeInterval = 1.0) {
        let group = DispatchGroup()
        group.enter()

        bufferQueue.async { [weak self] in
            defer { group.leave() }
            guard let self else { return }
            guard !self.sampleBuffer.isEmpty else { return }

            let samples = self.sampleBuffer
            let events = self.eventsBuffer

            self.sampleBuffer.removeAll()
            self.eventsBuffer.removeAll()

            let (motionActivity, pedo) = self.snapshotMovementHintsForBatch()
            let (alt, dev, net, head) = self.snapshotBatchContext()

            let seq = Int(self.batchSeq)
            let activityContext = self.makeBatchActivityContext()
            let screenInteractionContext = self.makeBatchScreenInteractionContext()
            
            let batch = TelemetryBatch(
                device_id: self.deviceId,
                driver_id: self.resolvedDriverId(),
                session_id: self.sessionId,
                timestamp: self.isoFormatter.string(from: Date()),

                app_version: self.appVersion(),
                app_build: self.appBuild(),
                ios_version: self.iosVersion(),
                device_model: self.deviceModelIdentifier(),
                locale: self.localeId(),
                timezone: self.timeZoneId(),

                tracking_mode: self.trackingMode,
                transport_mode: self.transportMode,
                batch_id: UUID().uuidString,
                batch_seq: seq,
                samples: samples,
                events: events.isEmpty ? nil : events,
//                trip_config: FeatureFlags.manualTuning ? self.activeTripConfig : nil,
                trip_config: self.activeTripConfig,
                motion_activity: motionActivity,
                

                pedometer: pedo,
                altimeter: alt,
                device_state: dev,
                network: net,
                heading: head,
                activity_context: activityContext,
                
                screen_interaction_context: screenInteractionContext,
            )

            self.batchSeq &+= 1

            DispatchQueue.main.async {
                self.lastCreatedBatchSeq = seq
                self.createdBatchesCount = seq + 1
            }

            NetworkManager.shared.upload(batch: batch) { [weak self] result in
                guard let self else { return }
                if case .failure(let err) = result {
                    self.pushNetworkError("Upload error: \(err.localizedDescription)")
                } else {
                    self.resetBatchActivityWindow()
                    self.resetBatchScreenInteractionWindow()
                }
            }
        }

        if enqueueAndWait {
            _ = group.wait(timeout: .now() + waitTimeoutSec)
        }
    }


    func requestAlwaysAuthorization() {
        let st = locationManager.authorizationStatus
        switch st {
        case .authorizedAlways:
            refreshLocationAuthText()
            applyBackgroundGpsPolicy()

        case .authorizedWhenInUse:
            locationManager.requestAlwaysAuthorization()

        case .notDetermined:
            // Не триггерим системный popup отсюда.
            // First prompt должен идти только через onboarding:
            // requestUserPermissionsForTripRecording()
            break

        case .denied, .restricted:
            openSystemSettings()

        @unknown default:
            openSystemSettings()
        }
    }

    func openSystemSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        DispatchQueue.main.async {
            UIApplication.shared.open(url, options: [:], completionHandler: nil)
        }
    }


    private func refreshLocationAuthText() {
        let st = locationManager.authorizationStatus
        let text: String
        switch st {
        case .notDetermined:        text = "Не запрошено"
        case .restricted:          text = "Restricted"
        case .denied:              text = "Denied"
        case .authorizedWhenInUse: text = "When In Use"
        case .authorizedAlways:    text = "Always"
        @unknown default:          text = "Unknown"
        }
        DispatchQueue.main.async { self.locationAuthText = text }
    }

        
    private func applyBackgroundGpsPolicy() {
        let st = locationManager.authorizationStatus
        let wantsBackground = (isCollectingNow || dayMonitoringKeepAliveEnabled) && (st == .authorizedAlways)

        #if DEBUG
        print("[GPS] applyBackgroundGpsPolicy wantsBackground=\(wantsBackground) auth=\(st.rawValue)")
        #endif

        locationManager.allowsBackgroundLocationUpdates = wantsBackground
        locationManager.pausesLocationUpdatesAutomatically = false
    }
    
    // Public Alpha additive fields
    func makeDeviceContextPayload() -> [String: Any] {
        refreshAppStateForPayload()
        refreshScreenInteractionForPayload()

        let chargerConnected =
            UIDevice.current.batteryState == .charging ||
            UIDevice.current.batteryState == .full

        return [
            "battery_level": UIDevice.current.batteryLevel >= 0
                ? NSNumber(value: UIDevice.current.batteryLevel)
                : NSNull(),
            "battery_state": batteryStateStringForPayload(),
            "low_power_mode": ProcessInfo.processInfo.isLowPowerModeEnabled,
            
            "carplay_connected": carplayConnectedForPayload,
            "app_state": appStateForPayload,
            "screen_interaction_in_app": screenInteractionInAppForPayload,
            "charger_connected": chargerConnected
        ]
    }
}
    
// MARK: - CLLocationManagerDelegate

extension SensorManager: CLLocationManagerDelegate {
    
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        refreshLocationAuthText()
        applyBackgroundGpsPolicy()
    }

    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let loc = locations.last else { return }

        // 1) отсекаем кэш/старьё (критично для фона/возврата из фона)
        let age = Date().timeIntervalSince(loc.timestamp)
        if age > acceptLocationMaxAge { return }

        // 2) отсекаем плохую точность (ваша настройка <= 100)
        if loc.horizontalAccuracy < 0 || loc.horizontalAccuracy > maxHorizontalAccuracy { return }

        bufferQueue.async {
            self.lastKnownLocation = loc
            
            if let prev = self.lastDistanceLoc {
                let d = loc.distance(from: prev) // meters
                if d > 0 {
                    DispatchQueue.main.async {
                        self.currentTripDistanceKm += d / 1000.0
                    }
                }
            }
            self.lastDistanceLoc = loc


            if loc.speed >= 0 {
                self.lastKnownSpeedMS = loc.speed
            }

            // course не зависит от speed — он может быть валиден отдельно
            if loc.course >= 0 {
                self.lastCourseRad = Double(loc.course) * Double.pi / 180.0
                self.lastCourseAt = Date()
            }

            self.updateTelemetryMode(with: loc)

            // обновляем режим после принятия валидной точки
            if self.shouldUpdateUI(now: Date()) {
                DispatchQueue.main.async {
                    self.updateTelemetryModeText()
                }
            }

        }

        if self.shouldUpdateUI(now: Date()) {
            DispatchQueue.main.async {
                self.lastLocationString = String(format: "%.5f, %.5f",
                                                 loc.coordinate.latitude,
                                                 loc.coordinate.longitude)

                if loc.speed >= 0 {
                    self.lastSpeedString = String(format: "%.1f km/h", loc.speed * 3.6)
                } else {
                    self.lastSpeedString = "—"
                }
            }
        }


    }
    
    

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        let ns = error as NSError
        // kCLErrorLocationUnknown = 0 — временно нет фикса, НЕ фатально
        if ns.domain == kCLErrorDomain && ns.code == CLError.locationUnknown.rawValue {
            return
        }

        let msg = "Location error: \(error.localizedDescription)"
        pushNetworkError(msg)
        DispatchQueue.main.async { self.statusText = msg }
    }
    
    func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        movementStateQueue.async {
            self.lastHeadingMagDeg = newHeading.magneticHeading >= 0 ? newHeading.magneticHeading : nil
            self.lastHeadingTrueDeg = newHeading.trueHeading >= 0 ? newHeading.trueHeading : nil
            self.lastHeadingAccuracyDeg = newHeading.headingAccuracy >= 0 ? newHeading.headingAccuracy : nil
            self.lastHeadingAt = Date()
        }
    }
}

extension SensorManager {
    @MainActor
    func clearLocalAppData() {
        if isCollectingNow {
            stopAll()
        }

        // Full auth reset: bearer + App Attest key id
        AuthManager.shared.clearAllAuthState()
        

        // Persisted ids / flags
        KeychainStore.shared.delete(autoDriverPasswordKey)
        UserDefaults.standard.removeObject(forKey: "didShowPermissionOnboarding")
        UserDefaults.standard.removeObject(forKey: "saved_driver_id")
        UserDefaults.standard.removeObject(forKey: "saved_vehicle_id")
        updateDriverId(defaultAutoDriverId())

        // Driver auth UI state
        isDriverAuthorizedOnThisDevice = false
        driverAuthState = .unknown
        lastDriverAuthError = nil

        // Session / trip state
        isCollectingNow = false
        statusText = "Stopped"
        appStateText = "Приложение в ожидании"

        tripStartedAt = nil
        sessionStartedAt = nil
        sessionEndedAt = nil
        lastDistanceLoc = nil
        currentTripElapsedSec = 0
        currentTripDistanceKm = 0

        // Clear transient UI/network state
        lastNetworkErrors.removeAll()
        createdBatchesCount = 0
        lastCreatedBatchSeq = -1

        // Reset live server metrics
        resetLiveServerMetrics()

        // Reset current per-trip counters shown in UI
        currentTripSuddenAccelCount = 0
        currentTripSuddenBrakeCount = 0
        currentTripSuddenTurnCount = 0
        currentTripRoadAnomalyCount = 0

        currentTripSuddenAccelDeviceCount = 0
        currentTripSuddenBrakeDeviceCount = 0
        currentTripSuddenTurnDeviceCount = 0
        currentTripRoadAnomalyDeviceCount = 0

        crashDetected = false
        crashG = 0
    }

    
    func requestUserPermissionsForTripRecording() {
        switch locationManager.authorizationStatus {
        case .notDetermined:
            locationManager.requestWhenInUseAuthorization()

        case .authorizedWhenInUse:
            locationManager.requestAlwaysAuthorization()
            

        default:
            break
        }

        if CMMotionActivityManager.authorizationStatus() == .notDetermined {
            ensureMotionRunning()
            stopMotion()
        }

        refreshLocationAuthText()
        applyBackgroundGpsPolicy()
    }
    @MainActor
    func finalizeTripOwnerAfterFinish() {
        activeTripDriverId = nil
    }

    @MainActor
    func applyPendingDriverIdIfNeeded() {
        guard let pending = pendingDriverIdAfterStop?.trimmingCharacters(in: .whitespacesAndNewlines),
              !pending.isEmpty else {
            pendingDriverIdAfterStop = nil
            return
        }

        updateDriverId(pending)
        pendingDriverIdAfterStop = nil
    }

    @MainActor
    func queueDriverIdChangeAfterStop(_ newDriverId: String) {
        pendingDriverIdAfterStop = newDriverId.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
extension Notification.Name {
    static let driverIdDidChange = Notification.Name("driverIdDidChange")
}

extension Notification.Name {
    static let requestDriverChangeFlow = Notification.Name("requestDriverChangeFlow")
}
