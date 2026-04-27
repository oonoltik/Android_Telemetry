//
//  DriverMonitoringFrameDelegate.swift
//  TelemetryApp
//
//  Created by Alex on 27.04.26.
//

import Foundation
import AVFoundation

final class DriverMonitoringFrameDelegate: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {

    private let driverMonitoring: DriverMonitoringService

    init(driverMonitoring: DriverMonitoringService) {
        self.driverMonitoring = driverMonitoring
        super.init()
    }

    func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
            return
        }

        driverMonitoring.processFrame(pixelBuffer)
    }
}
