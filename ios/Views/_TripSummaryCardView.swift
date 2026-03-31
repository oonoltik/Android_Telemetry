//
//  _TripSummaryCardView.swift
//  TelemetryApp
//
//  Created by Alex on 17.03.26.
//

import SwiftUI

struct _TripSummaryCardView: View {
    let speedLabel: String
    let tripTimeLabel: String
    let distanceLabel: String

    let currentSpeedText: String
    let tripTimeText: String
    let distanceText: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Label(speedLabel, systemImage: "speedometer")
                    .foregroundColor(.secondary)
                Spacer()
                Text(currentSpeedText)
                    .fontWeight(.semibold)
            }

            HStack {
                Label(tripTimeLabel, systemImage: "timer")
                    .foregroundColor(.secondary)
                Spacer()
                Text(tripTimeText)
                    .fontWeight(.semibold)
            }

            HStack {
                Label(distanceLabel, systemImage: "ruler")
                    .foregroundColor(.secondary)
                Spacer()
                Text(distanceText)
                    .fontWeight(.semibold)
            }
        }
        .padding()
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
        .padding(.horizontal)
    }
}
