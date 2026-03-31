//
//  ManualTripAutoFinish.swift
//  TelemetryApp
//
//  Created by Alex on 24.02.26.
//

import Foundation
import CoreMotion


extension Notification.Name {
    static let manualTripAutoFinishDidTrigger = Notification.Name("manualTripAutoFinishDidTrigger")
}

@MainActor
final class ManualTripAutoFinish {
    private let motion = CMMotionActivityManager()
    private let queue = OperationQueue()
       
    private var nonAutomotiveSince: Date? = nil
    private var isRunning = false
    private var isAutoStoppingNow = false

    // Подберите: 60–120 сек. Я бы оставил 80 как в DayMonitoringManager.
    var stopConfirmSec: TimeInterval = 120
    
    

    func start(
        currentSpeedKmh: @escaping () -> Double,
        onAutoStop: @escaping () -> Void
    ) {
        guard !isRunning else { return }
        isRunning = true
        nonAutomotiveSince = nil

        queue.name = "ManualTripAutoFinish.ActivityQueue"

        guard CMMotionActivityManager.isActivityAvailable() else { return }

        motion.startActivityUpdates(to: queue) { [weak self] activity in
            guard let self, let a = activity else { return }

            let isAutomotive = a.automotive
            let now = Date()

            Task { @MainActor in
                let currentSpeed = currentSpeedKmh()

                // Если телефон/машина снова движется заметно — сбрасываем кандидат на автофиниш.
                if currentSpeed > 7 {
                    self.nonAutomotiveSince = nil
                    return
                }

                if isAutomotive {
                    self.nonAutomotiveSince = nil
                    return
                }

                // Любое НЕ automotive трактуем как кандидат на автофиниш:
                // walking/running/stationary/unknown/cycling — все ок.
                if self.nonAutomotiveSince == nil {
                    self.nonAutomotiveSince = now
                }

                if let t0 = self.nonAutomotiveSince {

                    let elapsed = now.timeIntervalSince(t0)

                    if elapsed >= self.stopConfirmSec && currentSpeed < 5 {
                        if self.isAutoStoppingNow { return }

                        self.isAutoStoppingNow = true
                        self.nonAutomotiveSince = nil
#if DEBUG

                        print("[AUTO_FINISH] nonAutomotive \(Int(elapsed))s, speed \(String(format: "%.1f", currentSpeed)) km/h -> finish")

#endif
                        DispatchQueue.main.async {
                            onAutoStop()

                            NotificationCenter.default.post(
                                name: .manualTripAutoFinishDidTrigger,
                                object: nil,
                                userInfo: [
                                    "reason": "auto_finish"
                                ]
                            )
                        }
                    }
                }
            }
        }
    }

    func stop() {
        guard isRunning else { return }
        isRunning = false
        isAutoStoppingNow = false
        nonAutomotiveSince = nil
        motion.stopActivityUpdates()
    }
}
