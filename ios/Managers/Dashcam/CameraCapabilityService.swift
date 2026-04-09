import Foundation
import AVFoundation
import Combine

final class CameraCapabilityService {
    func validateRearCameraAvailable() throws {
        guard AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) != nil else {
            throw DashcamError.cameraUnavailable
        }
    }

    func validateMicrophoneAvailable() throws {
        guard AVCaptureDevice.default(for: .audio) != nil else {
            throw DashcamError.microphoneUnavailable
        }
    }

    func isMultiCamSupported() -> Bool {
        if #available(iOS 13.0, *) {
            return AVCaptureMultiCamSession.isMultiCamSupported
        }
        return false
    }

    func recommendedPreset() -> AVCaptureSession.Preset { .hd1280x720 }
    func requestCameraAccess() async -> Bool { await AVCaptureDevice.requestAccess(for: .video) }
    func requestMicrophoneAccess() async -> Bool { await AVCaptureDevice.requestAccess(for: .audio) }
}
