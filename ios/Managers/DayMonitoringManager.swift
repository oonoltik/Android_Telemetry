//
//  DayMonitoringManager.swift
//  TelemetryApp
//
//  Created by Alex on 22.01.26.
//

import Foundation
import CoreMotion
import SwiftUI
import Combine

@MainActor
final class DayMonitoringManager: ObservableObject {

    enum State: String {
        case disabled
        case idle
        case inTrip
    }
    // MARK: - Auto trip ownership

    private var autoTripSessionId: String? = nil


    // MARK: - Published (UI)
    @Published private(set) var isEnabled: Bool = false
    @Published private(set) var state: State = .disabled
    @Published private(set) var lastActivityText: String = "—"
    @Published private(set) var tripsCompletedToday: Int = 0
    @Published private(set) var lastTripFinishStatus: String = ""

    // MARK: - Dependencies
    private let sensorManager: SensorManager
    private let motionActivity = CMMotionActivityManager()
    private let activityQueue = OperationQueue()

    // MARK: - Hysteresis / thresholds
    /// Сколько времени "automotive" должен держаться, чтобы стартовать поездку
    private let startConfirmSec: TimeInterval = 10
    /// Сколько времени "не automotive" должен держаться, чтобы завершить поездку
    private let stopConfirmSec: TimeInterval = 80

    private var automotiveSince: Date?
    private var nonAutomotiveSince: Date?

    init(sensorManager: SensorManager) {
        self.sensorManager = sensorManager
        activityQueue.name = "DayMonitoring.ActivityQueue"
    }

    // MARK: - Public API

    func setEnabled(_ enabled: Bool) {
        if enabled {
            enable()
        } else {
            disable()
        }
        autoTripSessionId = nil
        if enabled {
            state = .idle
        }


    }

    func enable() {
        guard !isEnabled else { return }
        isEnabled = true
        state = .idle
        lastTripFinishStatus = ""

        startMotionActivity()
        sensorManager.setDayMonitoringKeepAliveEnabled(true)

    }

    func disable() {
        guard isEnabled else { return }

        // 1) Если day monitoring владеет активной поездкой — завершаем её как при Stop
        if autoTripSessionId != nil && sensorManager.isCollectingNow {
            stopTripAuto()
        }
        
        sensorManager.setDayMonitoringKeepAliveEnabled(false)


        // 2) Останавливаем мониторинг активности
        stopMotionActivity()

        // 3) Сбрасываем состояние monitoring
        isEnabled = false
        state = .disabled
        lastActivityText = "—"
        automotiveSince = nil
        nonAutomotiveSince = nil
    }


    // MARK: - Motion Activity

    private func startMotionActivity() {
        guard CMMotionActivityManager.isActivityAvailable() else {
            lastActivityText = "Activity недоступен"
            return
        }

        motionActivity.startActivityUpdates(to: activityQueue) { [weak self] activity in
            guard let self, let activity else { return }
            Task { @MainActor in
                self.handle(activity: activity)
            }
        }
    }

    private func stopMotionActivity() {
        motionActivity.stopActivityUpdates()
    }

    private func handle(activity: CMMotionActivity) {
        let now = Date()

        // Текст для UI
        lastActivityText = formatActivity(activity)

        let isAutomotive = activity.automotive && activity.confidence != .low

        if isAutomotive {
            nonAutomotiveSince = nil
            if automotiveSince == nil { automotiveSince = now }

            // Start trip if stable automotive
            if state == .idle,
               now.timeIntervalSince(automotiveSince ?? now) >= startConfirmSec {
                startTripAuto()
            }

        } else {
            automotiveSince = nil
            if nonAutomotiveSince == nil { nonAutomotiveSince = now }

            // Stop trip if stable non-automotive
            if state == .inTrip,
               now.timeIntervalSince(nonAutomotiveSince ?? now) >= stopConfirmSec {
                stopTripAuto()
            }
        }
    }

    private func startTripAuto() {
        // Если уже идёт ручная поездка — day monitoring только наблюдает
        if sensorManager.isCollectingNow {
            lastActivityText = "Manual trip active"
            return
        }

        // Стартуем авто-поездку
        // Стартуем авто-поездку (V2 contract)
        sensorManager.setTripContext(trackingMode: "day_monitoring", transportMode: "car")
        sensorManager.startCollecting()




        autoTripSessionId = sensorManager.currentSessionId
        state = .inTrip
        lastActivityText = "Auto trip started"
    }

    private func stopTripAuto() {
        // Если day monitoring не владел поездкой — не вмешиваемся
        guard let ownedSessionId = autoTripSessionId else {
            state = .idle
            return
        }

        // Если текущая сессия не та, что стартовал day monitoring — выходим
        guard sensorManager.currentSessionId == ownedSessionId else {
            autoTripSessionId = nil
            state = .idle
            return
        }

        sensorManager.stopAndDrainUploads { [weak self] _ in
            guard let self else { return }

            // 1) Снимок длительности — ровно то, что показывалось в UI
            let durationSnapshot = Double(self.sensorManager.currentTripElapsedSec)

            // 2) Момент автозавершения
            let endedAtISO = ISO8601DateFormatter().string(from: Date())

            NetworkManager.shared.finishTrip(
                sessionId: ownedSessionId,
                driverId: self.sensorManager.driverId,
                deviceId: self.sensorManager.deviceIdForDisplay,
                trackingMode: "day_monitoring",
                transportMode: "car",                

                clientEndedAt: endedAtISO,
                tripDurationSec: durationSnapshot,
                finishReason: "day_monitoring_auto_finish"
            ) { _ in
                // результат можно игнорировать
            }
        }




        autoTripSessionId = nil
        state = .idle
        lastActivityText = "Auto trip finished"
    }

    

    private func formatActivity(_ a: CMMotionActivity) -> String {
        var tags: [String] = []
        if a.stationary { tags.append("stationary") }
        if a.walking { tags.append("walking") }
        if a.running { tags.append("running") }
        if a.cycling { tags.append("cycling") }
        if a.automotive { tags.append("automotive") }
        if a.unknown || tags.isEmpty { tags.append("unknown") }

        let conf: String = {
            switch a.confidence {
            case .low: return "low"
            case .medium: return "med"
            case .high: return "high"
            @unknown default: return "?"
            }
        }()

        return "\(tags.joined(separator: ",")) [\(conf)]"
    }
}
