//
//  DriverSetupView.swift
//  TelemetryApp
//

import SwiftUI

struct DriverSetupView: View {

    @EnvironmentObject var sensorManager: SensorManager
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var languageManager: LanguageManager

    private func t(_ key: LocalizationKey) -> String {
        languageManager.text(key)
    }
    private func localizedAuthError(_ error: Error) -> String {
        let nsError = error as NSError
        let raw = nsError.localizedDescription.lowercased()

        if raw.contains("driver_id not found") || raw.contains("not found") {
            return t(.driverIdNotFound)
        }

        if raw.contains("invalid password") || raw.contains("wrong password") {
            return t(.invalidPassword)
        }

        if raw.contains("device confirmation failed") {
            return t(.deviceConfirmationFailed)
        }

        if nsError.domain == "AuthManager", nsError.code == -1001 || nsError.code == -1002 {
            #if DEBUG
            return t(.deviceConfirmationFailed)
            #else
            return t(.loginFailedTryAgain)
            #endif
        }
        
        if raw.contains("temporarily unavailable") || raw.contains("unreachable") || raw.contains("timed out") {
            return t(.driverAuthUnavailable)
        }

        if raw.contains("device is not authorized for this driver_id") {
            return t(.deviceNotAuthorizedForDriver)
        }

        return t(.loginFailedTryAgain)
    }

    enum Stage {
        case enterId
        case needPassword(isNew: Bool)
        case working
    }

    @State private var stage: Stage = .enterId
    @State private var driverId: String = ""
    @State private var password: String = ""
    @State private var errorText: String? = nil

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                VStack(spacing: 6) {
                    Text(t(.enterDriverId))
                        .font(.title2).fontWeight(.semibold)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    Text(t(.driverIdSetupDescription))
                        .font(.footnote)
                        .foregroundColor(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.top, 12)

                VStack(alignment: .leading, spacing: 8) {
                    TextField(t(.driverId), text: $driverId)
                        .textFieldStyle(.roundedBorder)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled(true)

                    if case .needPassword = stage {
                        SecureField(t(.password), text: $password)
                            .textFieldStyle(.roundedBorder)
                    }
                }

                if let errorText {
                    Text(errorText)
                        .font(.footnote)
                        .foregroundColor(.red)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                Spacer()

                buttons
            }
            .padding(.horizontal)
            .navigationTitle(t(.driver))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                // When onboarding is mandatory (no driverId) we do not show Cancel.
                ToolbarItem(placement: .topBarLeading) {
                    Button(t(.close)) { dismiss() }
                }
            }
            .onAppear {
                driverId = sensorManager.driverId
            }
        }
    }

    @ViewBuilder
    private var buttons: some View {
        switch stage {
        case .enterId:
            Button {
                sensorManager.markScreenInteractionInApp()
                Task { await onContinue() }
            } label: {
                Text(t(.continueButton))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
            .buttonStyle(.borderedProminent)
            .disabled(driverId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

        case .needPassword(let isNew):
            VStack(spacing: 10) {
                Text(isNew ? t(.newDriverPasswordPrompt) : t(.existingDriverPasswordPrompt))
                    .font(.footnote)
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)

                Button {
                    Task { await onSubmitPassword(isNew: isNew) }
                } label: {
                    Text(isNew ? t(.create) : t(.signIn))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.borderedProminent)
                .disabled(driverId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || password.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

                Button(t(.back)) {
                    errorText = nil
                    password = ""
                    stage = .enterId
                }
                .buttonStyle(.bordered)
            }

        case .working:
            ProgressView(t(.checking))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
        }
    }

    private func onContinue() async {
        errorText = nil
        stage = .working
        do {
            let trimmedId = driverId.trimmingCharacters(in: .whitespacesAndNewlines)
            let res = try await sensorManager.prepareDriverId(trimmedId)
            switch res.status {
            case .knownDevice:
                if sensorManager.isCollectingNow {
                    await MainActor.run {
                        sensorManager.queueDriverIdChangeAfterStop(trimmedId)
                    }
                } else {
                    await MainActor.run {
                        sensorManager.updateDriverId(trimmedId)
                    }
                }
                
                dismiss()
            case .needPassword:
                stage = .needPassword(isNew: false)
            case .newDriver:
                stage = .needPassword(isNew: true)
            }
        } catch {
            stage = .enterId
            errorText = localizedAuthError(error)
        }
    }

    private func onSubmitPassword(isNew: Bool) async {
        errorText = nil
        stage = .working

        do {
            let trimmedId = driverId.trimmingCharacters(in: .whitespacesAndNewlines)
            let trimmedPw = password.trimmingCharacters(in: .whitespacesAndNewlines)

            if isNew {
                try await sensorManager.registerDriverId(trimmedId, password: trimmedPw)
            } else {
                try await sensorManager.loginDriverId(trimmedId, password: trimmedPw)
            }

            // 🔎 ВАЖНО: проверяем, что сервер теперь считает устройство known_device
            let res = try await sensorManager.prepareDriverId(trimmedId)

            switch res.status {
            case .knownDevice:
                if sensorManager.isCollectingNow {
                    await MainActor.run {
                        sensorManager.queueDriverIdChangeAfterStop(trimmedId)
                    }
                } else {
                    await MainActor.run {
                        sensorManager.updateDriverId(trimmedId)
                    }
                }
                await sensorManager.setDriverAuthorized(true)
                dismiss()

            case .needPassword:
                stage = .needPassword(isNew: false)
                errorText = t(.serverStillRequiresPassword)
                return

            case .newDriver:
                stage = .needPassword(isNew: true)
                errorText = t(.driverIdNotCreatedOnServer)
                return
            }

        } catch {
            stage = .enterId
            errorText = localizedAuthError(error)
        }
    }

}
