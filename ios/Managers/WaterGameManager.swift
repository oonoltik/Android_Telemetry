//
//  WaterGameManager.swift
//  TelemetryApp
//
//  Created by Alex on 17.03.26.
//

import Foundation
import CoreMotion
import Combine

final class WaterGameManager: ObservableObject {

    // MARK: - Public (UI)
    @Published var waterTiltRoll: Double = 0
    @Published var waterTiltPitch: Double = 0
    @Published var waterWaveEnergy: Double = 0
    @Published var waterSpillSeverity: Double = 0

    // MARK: - Internal state
    private var waterRollLP: Double = 0
    private var waterPitchLP: Double = 0
    private var waterEnergyLP: Double = 0
    private var waterSeverityLP: Double = 0

    private var lastAccMag: Double = 0
    private var lastWaterUpdateTS: TimeInterval = 0

    // MARK: - Processing
    func process(attitude: CMAttitude,
                 accel: CMAcceleration,
                 rotationRate: CMRotationRate) {

        let roll = attitude.roll
        let pitch = attitude.pitch

        let alpha = 0.12
        waterRollLP = waterRollLP * (1 - alpha) + roll * alpha
        waterPitchLP = waterPitchLP * (1 - alpha) + pitch * alpha

        let ax = accel.x, ay = accel.y, az = accel.z
        let accMag = sqrt(ax*ax + ay*ay + az*az)

        let rx = rotationRate.x, ry = rotationRate.y, rz = rotationRate.z
        let rotMag = sqrt(rx*rx + ry*ry + rz*rz)

        let nowTS = Date().timeIntervalSince1970
        let dt = (lastWaterUpdateTS > 0) ? max(0.001, nowTS - lastWaterUpdateTS) : 0.02
        lastWaterUpdateTS = nowTS

        let jerk = abs(accMag - lastAccMag) / dt
        lastAccMag = accMag

        let severityRaw =
            (accMag * 1.10) +
            (jerk * 0.08) +
            (rotMag * 0.15)

        waterSeverityLP = 0.85 * waterSeverityLP + 0.15 * severityRaw

        let input = 0.9 * accMag + 0.15 * rotMag
        let decay = 0.92
        waterEnergyLP = min(2.0, waterEnergyLP * decay + input * 0.35)

        DispatchQueue.main.async {
            self.waterTiltRoll = self.waterRollLP
            self.waterTiltPitch = self.waterPitchLP
            self.waterWaveEnergy = self.waterEnergyLP
            self.waterSpillSeverity = self.waterSeverityLP
        }
    }
}
