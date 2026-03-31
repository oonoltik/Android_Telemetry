//
//  TripSummary.swift
//  TelemetryApp
//
//  Created by Alex on 20.01.26.
//

import Foundation

struct TripSummary: Identifiable, Decodable {
    var id: String { session_id }

    let session_id: String
    let driver_id: String?

    let client_started_at: String?
    let client_ended_at: String?
    let received_started_at: String?
    let received_ended_at: String?

    let distance_km: Double?
    let trip_score: Double?
    let trip_score_exposure: Double?
    let trip_preset: String?
    
    // Public Alpha additive fields
    let score_v2: Double?
    let driving_load: Double?
    let avg_speed_kmh: Double?
    let driving_mode: String?
    let trip_duration_sec: Double?
}

struct RecentTripsResponse: Decodable {
    let status: String
    let trips: [TripSummary]
}
