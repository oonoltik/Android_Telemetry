import Foundation
import Combine

@MainActor
final class DashcamTripCoordinator: ObservableObject {
    private let sensorManager: SensorManager

    @Published private(set) var tripOwnership: DashcamTripOwnership = .none

    init(sensorManager: SensorManager) {
        self.sensorManager = sensorManager
        if sensorManager.isCollectingNow {
            tripOwnership = .manual
        }
    }

    func ensureTripForVideoStart() async throws -> DashcamTripOwnership {
        if sensorManager.isCollectingNow {
            tripOwnership = .manual
            return .manual
        }

        try await sensorManager.startImplicitTrip()
        tripOwnership = .videoImplicit
        return .videoImplicit
    }

    func handleVideoStop(reason: String = "video_stop") async {
        switch tripOwnership {
        case .videoImplicit:
            await sensorManager.finishImplicitTrip(reason: reason)
            tripOwnership = .none

        case .manual:
            tripOwnership = sensorManager.isCollectingNow ? .manual : .none

        case .none:
            break
        }
    }

    func beginManualTripDuringVideo() async {
        if tripOwnership == .videoImplicit {
            await sensorManager.finishImplicitTrip(reason: TripFinishReason.manualTakeover.rawValue)
        }

        if !sensorManager.isCollectingNow {
            sensorManager.startCollecting()
        }

        tripOwnership = .manual
    }

    func handleManualTripStartedOutsideVideo() {
        tripOwnership = .manual
    }

    func restoreImplicitTripAfterManualTripStopIfNeeded(videoStillActive: Bool) async {
        guard videoStillActive else {
            tripOwnership = sensorManager.isCollectingNow ? .manual : .none
            return
        }

        guard !sensorManager.isCollectingNow else {
            tripOwnership = .manual
            return
        }

        do {
            try await sensorManager.startImplicitTrip()
            tripOwnership = .videoImplicit
        } catch {
            tripOwnership = .none
        }
    }

    func syncOwnershipAfterExternalStateChange(videoStillActive: Bool) async {
        if sensorManager.isCollectingNow {
            if tripOwnership == .none {
                tripOwnership = .manual
            }
            return
        }

        if videoStillActive && tripOwnership != .manual {
            do {
                try await sensorManager.startImplicitTrip()
                tripOwnership = .videoImplicit
            } catch {
                tripOwnership = .none
            }
        } else {
            tripOwnership = .none
        }
    }

    func currentTripOwnership() -> DashcamTripOwnership {
        tripOwnership
    }

    func currentTripSessionId() -> String? {
        sensorManager.currentTripSessionId()
    }
}
