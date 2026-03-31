//
//  SettingsView.swift
//  TelemetryApp
//

import SwiftUI
import CoreLocation

struct SettingsView: View {

    @EnvironmentObject var sensorManager: SensorManager
    @EnvironmentObject var languageManager: LanguageManager
    @Environment(\.dismiss) private var dismiss

    @State private var driverDraft: String = ""
    @State private var showingDriverSetup: Bool = false
    
    @State private var showingDeleteAccountAlert = false
    @State private var deleteAccountError: String?
    
    
    // Public Alpha additive fields
    @State private var showingDeleteDataAlert: Bool = false
    
    @State private var showingChangeDriverDuringTripAlert = false
    
    private func t(_ key: LocalizationKey) -> String {
        languageManager.text(key)
    }
    
    var body: some View {
        NavigationStack {
            Form {
                Section(t(.language)) {
                    Picker(t(.language), selection: Binding(
                        get: { languageManager.currentLanguage },
                        set: { languageManager.setLanguage($0) }
                    )) {
                        ForEach(AppLanguageRegistry.enabledInUI) { language in
                            Text(language.displayName).tag(language)
                        }
                    }
                }

                if FeatureFlags.isDeveloperBuild {
                    Section("Идентификаторы") {
                        HStack {
                            Text("Driver")
                            Spacer()
                            Text(sensorManager.driverId.isEmpty ? "—" : sensorManager.driverId)
                                .foregroundColor(.secondary)
                        }
                        HStack {
                            Text("Device")
                            Spacer()
                            Text(sensorManager.deviceIdForDisplay)
                                .foregroundColor(.secondary)
                                .lineLimit(1)
                                .truncationMode(.middle)
                        }
                        HStack {
                            Text("Session")
                            Spacer()
                            Text(sensorManager.currentSessionId)
                                .foregroundColor(.secondary)
                                .lineLimit(1)
                                .truncationMode(.middle)
                        }
                    }
                }

                Section(t(.driverIdSection)) {
                    HStack {
                        Text(t(.current))
                        Spacer()
                        Text(sensorManager.driverId.isEmpty ? "—" : sensorManager.driverId)
                            .foregroundColor(.secondary)
                    }

                    Button(t(.changeDriverId)) {
                        sensorManager.markScreenInteractionInApp()

                        if sensorManager.isCollectingNow {
                            showingChangeDriverDuringTripAlert = true
                        } else {
                            showingDriverSetup = true
                        }
                    }
                    .buttonStyle(.borderedProminent)
                }
                
                // Public Alpha additive fields
                
                Section(t(.privacy)) {
                    Text(t(.privacyDescription))
                        .font(.footnote)
                        .foregroundColor(.secondary)

                    Link(t(.privacyPolicy), destination: URL(string: "https://drivetelemetry.com/privacy/")!)
                    Link(t(.termsOfUse), destination: URL(string: "https://drivetelemetry.com/terms/")!)
                    
                    Button(t(.deleteLocalData), role: .destructive) {
                        showingDeleteDataAlert = true
                    }
                }
                Section(t(.account)) {
                    Button(t(.deleteAccount), role: .destructive)  {
                        showingDeleteAccountAlert = true
                    }
                }
                

                Section(
                    header: Text(t(.backgroundLocation)),
                    footer: Text(t(.backgroundLocationFooter))
                        .font(.footnote)
                ) {
                    let st = sensorManager.locationAuthorizationStatus

                    if st == .denied || st == .restricted {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(t(.gpsDeniedTitle))
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(.red)

                            Text(t(.gpsDeniedMessage))
                                .font(.footnote)
                                .foregroundColor(.red)
                                .fixedSize(horizontal: false, vertical: true)

                            Button(t(.openIOSSettings)) {
                                sensorManager.openSystemSettings()
                            }
                            .buttonStyle(.borderedProminent)
                        }
                        .padding(.vertical, 6)
                    }

                    if st == .authorizedWhenInUse {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(t(.alwaysRequiredTitle))
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(.red)

                            Text(t(.alwaysRequiredMessage))
                                .font(.footnote)
                                .foregroundColor(.red)
                                .fixedSize(horizontal: false, vertical: true)

                            Button(t(.requestAlways)) {
                                sensorManager.openSystemSettings()
                            }
                            .buttonStyle(.borderedProminent)
                        }
                        .padding(.vertical, 6)
                    }

                    if st == .notDetermined {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(t(.locationPermissionRequiredTitle))
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(.red)

                            Text(t(.locationPermissionRequiredMessage))
                                .font(.footnote)
                                .foregroundColor(.red)
                                .fixedSize(horizontal: false, vertical: true)

                            Button(t(.allowLocation)) {
                                sensorManager.requestWhenInUseAuthorization()
                            }
                            .buttonStyle(.borderedProminent)
                        }
                        .padding(.vertical, 6)
                    }
                   
                }
                
                if FeatureFlags.isDeveloperBuild {                    
                    Section("Ошибки") {
                        Button("Clear errors") {
                            sensorManager.clearNetworkErrors()
                        }
                        .foregroundColor(.red)
                        .disabled(sensorManager.lastNetworkErrors.isEmpty)
                        
                        if FeatureFlags.manualTuning {
                            NavigationLink("Настройка порога датчиков") {
                                TripConfigView()
                            }
                        }
                    }
                }

            }
            .navigationTitle(t(.settings))
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(t(.done)) { dismiss() }
                }
            }
            .onAppear {
                driverDraft = sensorManager.driverId
            }
            .alert("Delete account?", isPresented: $showingDeleteAccountAlert) {
                Button("Delete", role: .destructive) {
                    Task {
                        do {
                            try await sensorManager.deleteAccountInApp()
                            dismiss()
                        } catch {
                            deleteAccountError = error.localizedDescription
                        }
                    }
                }
                Button(t(.cancel), role: .cancel) {}
            } message: {
                Text(t(.deleteAccountMessage))
            }
            .alert(t(.unableToDeleteAccount), isPresented: Binding(
                get: { deleteAccountError != nil },
                set: { if !$0 { deleteAccountError = nil } }
            )) {
                Button(t(.ok), role: .cancel) {}
            } message: {
                Text(deleteAccountError?.replacingOccurrences(of: #"^\{"detail":"Not Found"\}$"#, with: t(.accountNotFound), options: .regularExpression) ?? t(.errorGeneric))
            }
            
            .alert(t(.changeDriverDuringTripTitle), isPresented: $showingChangeDriverDuringTripAlert) {
                Button(t(.continueButton), role: .destructive) {
                    NotificationCenter.default.post(name: .requestDriverChangeFlow, object: nil)
                    dismiss()
                }
                Button(t(.cancel), role: .cancel) {}
            } message: {
                Text(t(.changeDriverDuringTripMessage))
            }
            
            // Public Alpha additive fields
            .alert(t(.deleteLocalDataTitle), isPresented: $showingDeleteDataAlert) {
                Button(t(.delete), role: .destructive) {
                    sensorManager.clearLocalAppData()
                    dismiss()
                }
                Button(t(.cancel), role: .cancel) { }
            } message: {
                Text(t(.deleteLocalDataMessage))
            }
            
            .fullScreenCover(isPresented: $showingDriverSetup) {
                DriverSetupView()
                    .environmentObject(sensorManager)
                    .environmentObject(languageManager)
                    
            }
        }
    }
}
