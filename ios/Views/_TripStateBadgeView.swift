//
//  _TripStateBadgeView.swift
//  TelemetryApp
//
//  Created by Alex on 17.03.26.
//

import SwiftUI

struct _TripStateBadgeView: View {
    let isTripActive: Bool
    let activeText: String
    let idleText: String

    var body: some View {
        HStack(spacing: 12) {
            Circle()
                .fill(isTripActive ? Color.red : Color.green)
                .frame(width: 12, height: 12)

            Text(isTripActive ? activeText : idleText)
                .font(.headline)

            Spacer()
        }
        .padding(.horizontal, 4)
        .cardStyle()
    }
}
