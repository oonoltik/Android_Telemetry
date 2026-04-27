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
    @State private var page = 0

    private func t(_ key: LocalizationKey) -> String {
        languageManager.text(key)
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                TabView(selection: $page) {
                    onboardingPage(
                        icon: "car.fill",
                        title: t(.onboardingTitle),
                        items: [
                            (t(.onboardingAutoDetect), "car.fill"),
                            (t(.onboardingSummary), "chart.line.uptrend.xyaxis"),
                            (t(.onboardingEvents), "figure.walk.motion"),
                            (t(.onboardingBackground), "location.fill")
                        ],
                        footer: t(.onboardingPermissionText)
                    )
                    .tag(0)

                    onboardingPage(
                        icon: "video.fill",
                        title: t(.onboardingDashcamTitle),
                        items: [
                            (t(.onboardingDashcamVideoRecording), "video.fill"),
                            (t(.onboardingDashcamForegroundOnly), "iphone"),
                            (t(.onboardingDashcamNoHiddenBackground), "pause.circle")
                        ],
                        footer: t(.onboardingDashcamFooter)
                    )
                    .tag(1)

                    onboardingPage(
                        icon: "exclamationmark.triangle.fill",
                        title: t(.onboardingCrashTitle),
                        items: [
                            (t(.onboardingCrashProtectedClip), "lock.fill"),
                            (t(.onboardingCrashBeforeAfter), "clock.arrow.circlepath"),
                            (t(.onboardingCrashNotAutoDeleted), "archivebox.fill")
                        ],
                        footer: t(.onboardingCrashFooter)
                    )
                    .tag(2)

                    onboardingPage(
                        icon: "hand.raised.fill",
                        title: t(.onboardingPermissionsTitle),
                        items: [
                            (t(.onboardingPermissionCamera), "camera.fill"),
                            (t(.onboardingPermissionMicrophone), "mic.fill"),
                            (t(.onboardingPermissionPhotoLibrary), "photo.fill")
                        ],
                        footer: t(.onboardingPermissionsFooter)
                    )
                    .tag(3)
                }
                .tabViewStyle(.page(indexDisplayMode: .always))

                VStack(alignment: .leading, spacing: 8) {
                    Link(t(.privacyPolicy), destination: URL(string: "https://drivetelemetry.com/privacy/")!)
                    Link(t(.termsOfUse), destination: URL(string: "https://drivetelemetry.com/terms/")!)
                }
                .font(.footnote)

                Button(page == 3 ? t(.continueButton) : t(.nextButton)) {
                    if page < 3 {
                        withAnimation {
                            page += 1
                        }
                    } else {
                        onContinue()
                    }
                }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
            }
            .padding()
        }
    }

    @ViewBuilder
    private func onboardingPage(
        icon: String,
        title: String,
        items: [(String, String)],
        footer: String
    ) -> some View {
        VStack(alignment: .leading, spacing: 20) {
            Spacer()

            Image(systemName: icon)
                .font(.system(size: 48))
                .foregroundColor(.blue)

            Text(title)
                .font(.largeTitle.bold())

            VStack(alignment: .leading, spacing: 14) {
                ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                    Label(item.0, systemImage: item.1)
                }
            }
            .font(.body)

            Text(footer)
                .font(.footnote)
                .foregroundColor(.secondary)

            Spacer()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
