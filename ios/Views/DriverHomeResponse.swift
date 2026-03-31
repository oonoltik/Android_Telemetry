//
//  DriverHomeResponse.swift
//  TelemetryApp
//
//  Created by Alex on 16.03.26.
//

// DriverHomeResponse.swift

import Foundation

struct DriverHomeResponse: Codable {
    let status: String
    let driver_id: String?
    let rating_status: String
    let trip_count: Int
    let avg_score: Double?
    let score_delta_recent: Double?
    let better_than_drivers_pct: Double?
    let driver_rank: Int?
    let total_drivers: Int
    let driver_level: String?
    let next_level: String?
    let points_to_next_level: Double?
    let recent_trip_scores: [Double]
    let recent_trip_colors: [String]
    let trips_to_unlock_percentile: Int
}
