//
//  WaterGlassFullScreenView.swift
//  TelemetryApp
//
//  Created by Alex on 24.01.26.
//

//
//  WaterGlassFullScreenView.swift
//  TelemetryApp
//

import SwiftUI

struct WaterGlassFullScreenView: View {
    @EnvironmentObject var sensorManager: SensorManager
    @EnvironmentObject var waterGame: WaterGameManager
    @EnvironmentObject var languageManager: LanguageManager
    
    @Environment(\.scenePhase) private var scenePhase
    @State private var isGameOver: Bool = false
    
    @State private var totalSpilledRawPercent: Double = 0
    @State private var refillBonusPercent: Double = 0    
    
    @Environment(\.dismiss) private var dismiss
    
    private func t(_ key: LocalizationKey) -> String {
        languageManager.text(key)
    }
    
    var body: some View {
        GeometryReader { geo in
            ZStack {
                Color.black.ignoresSafeArea()
                
                VStack(spacing: 0) {
                    
                    
                    
                    VStack(spacing: 8) {
                        Text(isGameOver ? t(.glassGameOver) : t(.glassGameRunning))
                            .font(.system(size: 30, weight: .semibold))
                            .foregroundColor(isGameOver ? Color.red.opacity(0.9) : Color.white.opacity(0.90))
                            .multilineTextAlignment(.center)
                            .lineLimit(nil)
                            .fixedSize(horizontal: false, vertical: true)
                        
                        if !isGameOver {
                            Text("\(t(.glassGameSpilled)) \(String(format: "%.1f%%", totalSpilledRawPercent))")
                                .font(.system(size: 16, weight: .semibold, design: .monospaced))
                                .foregroundColor(Color.white.opacity(0.90))
                            
                            Text("\(t(.glassGameBonus)) \(String(format: "%.1f%%", refillBonusPercent))")
                                .font(.system(size: 16, weight: .semibold, design: .monospaced))
                                .foregroundColor(Color.green.opacity(0.90))
                        }
                    }
                    .padding(.horizontal, 18)
                    .padding(.vertical, 10)
                    .background(
                        RoundedRectangle(cornerRadius: 18)
                            .fill(Color.black.opacity(0.45))
                    )
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.horizontal, 16)
                    .padding(.top, -4)
                    
                                    
                    Spacer()
                    
                    WaterGlassView(
                        roll: waterGame.waterTiltRoll,
                        pitch: waterGame.waterTiltPitch,
                        energy: waterGame.waterWaveEnergy,
                        spillSeverity: waterGame.waterSpillSeverity,
                        isGameOver: $isGameOver,
                        
                        deviceId: sensorManager.deviceId,
                        driverId: sensorManager.driverId,
                        sessionId: sensorManager.sessionId
                    )
                    
                    
                    .frame(width: 300, height: 560)
                    .onPreferenceChange(SpillStatsPreferenceKey.self) { stats in
                        totalSpilledRawPercent = stats.totalSpilledRawPercent
                        refillBonusPercent = stats.refillBonusPercent
                    }
                    
                    
                    Spacer()
                    
                    Text(t(.glassGameHint))
                        .font(.footnote)
                        .foregroundColor(.white.opacity(0.75))
                        .multilineTextAlignment(.center)
                        .lineLimit(nil)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 16)
                        .padding(.bottom, 24)
                }
            }
            .overlay(alignment: .topLeading) {
                Button {
                    sensorManager.markScreenInteractionInApp()
                    dismiss()
                } label: {
                    Text(t(.close))
                        .font(.system(size: 16, weight: .semibold))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Color.black.opacity(0.35))
                        .foregroundColor(.white)
                        .cornerRadius(10)
                        .background(Color(red: 0.00, green: 0.45, blue: 0.95).opacity(0.9))
                }
                // выше: уменьшаем добавочный отступ (было +8)
                .padding(.top, geo.safeAreaInsets.top + 2)
                .padding(.leading, 12)
                
                .offset(y: -49)
                .zIndex(1000)
            }

        }
        .onAppear { sensorManager.startWaterVisualization() }
        .onDisappear { sensorManager.stopWaterVisualization() }
        .onChange(of: scenePhase) { ph in
            if ph == .active {
                sensorManager.startWaterVisualization()
            } else {
                sensorManager.stopWaterVisualization()
            }
        }
    }
}
