//
//  TrackingMode.swift
//  TelemetryApp
//
//  Created by Alex on 22.01.26.
//

import Foundation

enum TrackingMode: String, CaseIterable, Identifiable {
    case singleTrip = "single_trip"
    case dayMonitoring = "day_monitoring"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .singleTrip: return "Одна поездка"
        case .dayMonitoring: return "Мониторинг дня"
        }
    }
}
