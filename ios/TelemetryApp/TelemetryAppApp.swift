//
//  TelemetryAppApp.swift
//  TelemetryApp
//

import SwiftUI
import UIKit

final class AppDelegate: NSObject, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        
#if DEBUG
   if UserDefaults.standard.bool(forKey: "reset_logs_two") == false {
       FileLogger.shared.reset()
       UserDefaults.standard.set(true, forKey: "reset_logs_two")
   }
#endif
        
        logEvent("=== APP LAUNCH ===")
        logEvent("LOG FILE = \(FileLogger.shared.currentLogURL().path)")
        return true
    }

    func application(
        _ application: UIApplication,
        handleEventsForBackgroundURLSession identifier: String,
        completionHandler: @escaping () -> Void
    ) {
        if identifier == "com.telemetryapp.dashcam.crashupload" {
            DashcamBackgroundSessionBridge.shared.completionHandler = completionHandler
        } else {
            completionHandler()
        }
    }
}

@main
struct TelemetryAppApp: App {
    
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @Environment(\.scenePhase) private var scenePhase

    @StateObject private var sensorManager = SensorManager.shared
    @StateObject private var dayMonitoring = DayMonitoringManager(sensorManager: SensorManager.shared)
    @StateObject private var languageManager = LanguageManager()
    @StateObject private var dashcamManager: DashcamManager
    private let archiveStore: VideoArchiveStore

    init() {
        // 🔹 Однократная инициализация глобального состояния
        SensorManager.shared.configure()
        let stack = Self.makeDashcamStack(sensorManager: SensorManager.shared)
            _dashcamManager = StateObject(wrappedValue: stack.0)
            self.archiveStore = stack.1
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(sensorManager)
                .environmentObject(dayMonitoring)
                .environmentObject(languageManager)
                .environmentObject(dashcamManager)
                .onChange(of: scenePhase) { phase in
                    switch phase {
                    case .background:
                        dashcamManager.applicationWillResignActive()

                    case .active:
                        dashcamManager.applicationDidBecomeActive()

                    case .inactive:
                        break

                    @unknown default:
                        break
                    }
                }
        }
    }
}
