import SwiftUI
import UIKit
import AVFoundation

final class CameraPreviewHostView: UIView {

    override class var layerClass: AnyClass {
        AVCaptureVideoPreviewLayer.self
    }

    private var previewLayer: AVCaptureVideoPreviewLayer {
        layer as! AVCaptureVideoPreviewLayer
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .black
        layer.cornerRadius = 12
        layer.masksToBounds = true
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        backgroundColor = .black
        layer.cornerRadius = 12
        layer.masksToBounds = true
    }

    func bind(session: AVCaptureSession?) {
        previewLayer.videoGravity = .resizeAspectFill

        if previewLayer.session !== session {
            previewLayer.session = session
        }

        previewLayer.connection?.isEnabled = true
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        previewLayer.frame = bounds
    }
}

struct CameraPreviewContainerView: UIViewRepresentable {

    let sessionProvider: DashcamPreviewSessionProvider

    func makeUIView(context: Context) -> CameraPreviewHostView {
        let view = CameraPreviewHostView()
        view.backgroundColor = .black
        view.bind(session: sessionProvider.previewSession)
        return view
    }

    func updateUIView(_ uiView: CameraPreviewHostView, context: Context) {
        uiView.bind(session: sessionProvider.previewSession)
        uiView.setNeedsLayout()
    }
}
