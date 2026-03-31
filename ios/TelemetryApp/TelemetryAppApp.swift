//
//  TelemetryAppApp.swift
//  TelemetryApp
//

import SwiftUI

@main
struct TelemetryAppApp: App {

    @StateObject private var sensorManager = SensorManager.shared
    @StateObject private var dayMonitoring = DayMonitoringManager(sensorManager: SensorManager.shared)
    @StateObject private var languageManager = LanguageManager()

    init() {
        // 🔹 Однократная инициализация глобального состояния
        SensorManager.shared.configure()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(sensorManager)
                .environmentObject(dayMonitoring)
                .environmentObject(languageManager)
        }
    }

}
