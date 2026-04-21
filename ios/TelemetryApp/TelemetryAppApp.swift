//
//  TelemetryAppApp.swift
//  TelemetryApp
//

import SwiftUI
import UIKit

final class AppDelegate: NSObject, UIApplicationDelegate {
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
            
        }
    }

}
