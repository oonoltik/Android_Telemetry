//
//  TripsArchiveView.swift
//  TelemetryApp
//
//  Created by Alex on 20.01.26.
//

import SwiftUI

struct TripsArchiveView: View {
    
    @EnvironmentObject var sensorManager: SensorManager
    @EnvironmentObject var languageManager: LanguageManager
    
    private func t(_ key: LocalizationKey) -> String {
        languageManager.text(key)
    }
    
    private func localizedArchiveError(_ error: Error) -> String {
        let raw = (error as NSError).localizedDescription.lowercased()
        
        if raw.contains("device is not authorized for this driver_id") {
            return t(.deviceNotAuthorizedForDriver)
        }
        
        if raw.contains("not found") {
            return t(.archiveNotFound)
        }
        
        return t(.archiveLoadFailedGeneric)
    }
    
    @State private var trips: [TripSummary] = []
    @State private var isLoading: Bool = false
    @State private var errorText: String? = nil
    
    private let isoParser: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [
            .withInternetDateTime,
            .withFractionalSeconds
        ]
        return f
    }()
    
    private var displayFormatter: DateFormatter {
        let f = DateFormatter()
        f.locale = languageManager.locale()
        f.timeZone = .current
        f.dateFormat = "d MMM yyyy, HH:mm"
        return f
    }
    
    private func formatDate(_ iso: String) -> String {
        if let date = isoParser.date(from: iso) {
            return displayFormatter.string(from: date)
        }
        return iso   // fallback, если вдруг формат не распарсился
    }
    
    private func localizedDrivingMode(_ rawMode: String) -> String {
        switch rawMode.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case "mixed":
            return t(.drivingModeMixed)
        case "city":
            return t(.drivingModeCity)
        case "highway":
            return t(.drivingModeHighway)
        default:
            return t(.drivingModeUnknown)
        }
    }
    
    private func tripBadgeColor(score: Double?) -> Color {
        guard let score else { return .gray.opacity(0.5) }
        if score >= 80 {
            return .green
        }
        if score >= 60 {
            return .yellow
        }
        return .red
    }
    
    
    var body: some View {
        VStack {
            if isLoading {
                ProgressView(t(.loading))
                    .padding()
            }
            
            if let errorText {
                VStack(spacing: 12) {
                    Text(errorText)
                        .font(.footnote)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                    
                    Button {
                        load()
                    } label: {
                        Text(t(.retry))
                            .font(.subheadline.weight(.semibold))
                            .padding(.horizontal, 20)
                            .padding(.vertical, 10)
                            .background(Color.blue.opacity(0.15))
                            .cornerRadius(10)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.bottom, 8)
            }
            
            List(trips) { trip in
                NavigationLink {
                    TripReportLoaderView(
                        sessionId: trip.session_id,
                        tripDriverId: trip.driver_id
                    )
                    .onAppear {
                        sensorManager.markScreenInteractionInApp()
                    }
                } label: {
                    HStack(alignment: .top, spacing: 10) {
                        let scoreValue = trip.score_v2 ?? trip.trip_score
                        
                        Circle()
                            .fill(tripBadgeColor(score: scoreValue))
                            .frame(width: 12, height: 12)
                            .padding(.top, 4)
                        
                        VStack(alignment: .leading, spacing: 4) {
                            if FeatureFlags.isDeveloperBuild {
                                Text("Поездка: \(trip.session_id)")
                                    .font(.footnote)
                                    .lineLimit(1)
                            }
                            
                            if let ended = trip.received_ended_at ?? trip.client_ended_at {
                                Text("\(t(.tripDate)): \(formatDate(ended))")
                                    .font(.caption2)
                                    .foregroundColor(.secondary)
                            }
                            
                            HStack(spacing: 12) {
                                if let score = scoreValue {
                                    Text(String(format: "\(t(.score)): %.2f", score))
                                        .foregroundColor(.secondary)
                                }
                                
                                if let mode = trip.driving_mode {
                                    Text(localizedDrivingMode(mode))
                                        .foregroundColor(.secondary)
                                }
                                
                                if let km = trip.distance_km {
                                    Text(String(format: "%.2f %@", km, t(.km)))
                                        .foregroundColor(.secondary)
                                }
                                
                                if let speed = trip.avg_speed_kmh {
                                    Text(String(format: "%.1f %@", speed, t(.kmh)))
                                        .foregroundColor(.secondary)
                                }
                            }
                            .font(.caption)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .navigationTitle(t(.tripArchiveTitle))
        .onAppear {
            load()
        }
    }
    
    
    
    
    private func load() {
        isLoading = true
        errorText = nil

        let effectiveDriverId: String
        if sensorManager.isDriverAuthorizedOnThisDevice {
            effectiveDriverId = sensorManager.driverId
        } else {
            effectiveDriverId = ""
        }

        NetworkManager.shared.fetchRecentTrips(
            deviceId: sensorManager.deviceIdForDisplay,
            driverId: effectiveDriverId,
            limit: 30
        ) { result in
            DispatchQueue.main.async {
                self.isLoading = false
                switch result {
                case .success(let items):
                    self.trips = items
                    self.errorText = items.isEmpty ? t(.noTripsInArchive) : nil
                case .failure(let err):
                    self.trips = []
                    self.errorText = localizedArchiveError(err)
                }
            }
        }
    }
    
    private struct TripReportLoaderView: View {

        @EnvironmentObject var sensorManager: SensorManager
        @EnvironmentObject var languageManager: LanguageManager

        private func t(_ key: LocalizationKey) -> String {
            languageManager.text(key)
        }

        private func localizedArchiveError(_ error: Error) -> String {
            let raw = (error as NSError).localizedDescription.lowercased()

            if raw.contains("device is not authorized for this driver_id") {
                return t(.deviceNotAuthorizedForDriver)
            }

            if raw.contains("not found") {
                return t(.archiveNotFound)
            }

            return t(.archiveLoadFailedGeneric)
        }

        let sessionId: String
        let tripDriverId: String?
        
        @State private var report: TripReport? = nil
        @State private var errorText: String? = nil
        @State private var isLoading: Bool = false
        
        var body: some View {
            Group {
                if isLoading {
                    ProgressView(t(.reportLoading))
                        .padding()
                } else if let report {
                    TripReportView(
                        report: report,
                        createdBatches: report.batches_count,
                        deliveredBatches: report.batches_count
                        
                    )
                    
                } else if let errorText {
                    Text(errorText)
                        .font(.footnote)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding()
                } else {
                    Color.clear.onAppear { load() }
                }
            }
            .navigationTitle(t(.report))
        }
        
        private func load() {
            isLoading = true
            errorText = nil
            report = nil

            let effectiveDriverId = (tripDriverId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)

            NetworkManager.shared.fetchTripReport(
                deviceId: sensorManager.deviceIdForDisplay,
                sessionId: sessionId,
                driverId: effectiveDriverId
            ) { result in
                DispatchQueue.main.async {
                    self.isLoading = false
                    switch result {
                    case .success(let r):
                        self.report = r
                    case .failure(let err):
                        self.errorText = localizedArchiveError(err)
                    }
                }
            }
        }
    }
}
    
