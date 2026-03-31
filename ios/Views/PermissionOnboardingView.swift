//
//  PermissionOnboardingView.swift
//  TelemetryApp
//
//  Created by Alex on 18.03.26.
//

import SwiftUI



struct PermissionOnboardingView: View {
    let onContinue: () -> Void
    
    @EnvironmentObject var languageManager: LanguageManager

    private func t(_ key: LocalizationKey) -> String {
        languageManager.text(key)
    }
    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 20) {
                Spacer()

                Text(t(.onboardingTitle))
                    .font(.largeTitle.bold())

                VStack(alignment: .leading, spacing: 14) {
                    Label(t(.onboardingAutoDetect), systemImage: "car.fill")
                    Label(t(.onboardingSummary), systemImage: "chart.line.uptrend.xyaxis")
                    Label(t(.onboardingEvents), systemImage: "figure.walk.motion")
                    Label(t(.onboardingBackground), systemImage: "location.fill")
                }
                .font(.body)

                Text(t(.onboardingPermissionText))
                    .font(.footnote)
                    .foregroundColor(.secondary)

                VStack(alignment: .leading, spacing: 8) {
                    Link(t(.privacyPolicy), destination: URL(string: "https://drivetelemetry.com/privacy/")!)
                    Link(t(.termsOfUse), destination: URL(string: "https://drivetelemetry.com/terms/")!)
                }
                .font(.footnote)

                Spacer()

                Button(t(.continueButton))  {
                    onContinue()
                }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
            }
            .padding()
        }
    }
}
