//
//  _StartStopControlsView.swift
//  TelemetryApp
//
//  Created by Alex on 17.03.26.
//

import SwiftUI

struct _StartStopControlsView: View {
    let startTitle: String
    let stopTitle: String
    let canStart: Bool
    let canStop: Bool
    let onStart: () -> Void
    let onStop: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onStart) {
                Text(startTitle)
                    .frame(maxWidth: .infinity)
                    .padding()
            }
            .buttonStyle(.borderedProminent)
            .disabled(!canStart)

            Button(action: onStop) {
                Text(stopTitle)
                    .frame(maxWidth: .infinity)
                    .padding()
            }
            .buttonStyle(.bordered)
            .disabled(!canStop)
        }
    }
}
