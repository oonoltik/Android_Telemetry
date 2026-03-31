//
//  _DriverScoreCardView.swift
//  TelemetryApp
//
//  Created by Alex on 17.03.26.
//

import SwiftUI

struct _DriverScoreCardView: View {
    let title: String
    let scoreText: String
    let primarySubtitle: String
    let secondarySubtitle: String?
    let delta: Double?
    let deltaLabel: String?
    let ratingFormingText: String?
    let percentileText: String?
    let nextLevelText: String?
    let homeMetricsError: String?
    let hasRecentTrips: Bool
    let tripSeriesTitle: String?
    let tripSeriesHint: String?
    let recentTripColors: [String]
    let onTripsTap: () -> Void
    let colorForTripBadge: (String) -> Color

    var body: some View {
        VStack(spacing: 6) {
            Text(title)
                .font(.title2)
                .fontWeight(.bold)

            Text(scoreText)
                .font(.system(size: 42, weight: .bold, design: .rounded))
                .foregroundColor(.accentColor)
                .monospacedDigit()

            Text(primarySubtitle)
                .font(.footnote)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

            if let secondarySubtitle {
                Text(secondarySubtitle)
                    .font(.footnote)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
            }

            if let delta, let deltaLabel {
                HStack(spacing: 6) {
                    Image(systemName: delta >= 0 ? "arrow.up.right" : "arrow.down.right")
                    Text(deltaLabel)
                }
                .font(.subheadline.weight(.semibold))
                .foregroundColor(delta >= 0 ? .green : .orange)
                .padding(.top, 2)

                Divider()
                    .padding(.vertical, 2)
            }

            if let ratingFormingText {
                Text(ratingFormingText)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
            } else if let percentileText {
                Text(percentileText)
                    .font(.subheadline.weight(.semibold))
                    .multilineTextAlignment(.center)
            }

            if let nextLevelText {
                Text(nextLevelText)
                    .font(.footnote)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
            }

            if hasRecentTrips {
                Divider()
                    .padding(.vertical, 2)

                Button(action: onTripsTap) {
                    VStack(spacing: 4) {
                        HStack(spacing: 10) {
                            ForEach(Array(recentTripColors.enumerated()), id: \.offset) { index, color in
                                Circle()
                                    .fill(colorForTripBadge(color))
                                    .frame(width: index == 0 ? 18 : 14, height: index == 0 ? 18 : 14)
                            }
                        }

                        if let tripSeriesTitle {
                            Text(tripSeriesTitle)
                                .font(.subheadline.weight(.semibold))
                                .multilineTextAlignment(.center)
                        }

                        if let tripSeriesHint {
                            Text(tripSeriesHint)
                                .font(.footnote)
                                .foregroundColor(.secondary)
                                .multilineTextAlignment(.center)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .padding(.top, 2)
            }

            if let homeMetricsError, !homeMetricsError.isEmpty {
                Text(homeMetricsError)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top)
        .cardStyle()
    }
}
