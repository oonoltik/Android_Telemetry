//
//  WaterGlassView.swift
//  TelemetryApp
//
//  Spill-enabled implementation (no TimelineView, no Canvas).
//  - Wave surface reacts to motion (roll + energy)
//  - If surface reaches spill line -> water decreases
//  - Slow refill toward target level
//

import SwiftUI
import Combine

struct SpillStats: Equatable {
    var totalSpilledRawPercent: Double
    var refillBonusPercent: Double
}

struct SpillStatsPreferenceKey: PreferenceKey {
    static var defaultValue: SpillStats = SpillStats(totalSpilledRawPercent: 0, refillBonusPercent: 0)
    static func reduce(value: inout SpillStats, nextValue: () -> SpillStats) {
        value = nextValue()
    }
}


struct WaterGlassView: View {
    
    @Environment(\.scenePhase) private var scenePhase
    @EnvironmentObject var languageManager: LanguageManager
    
    private func t(_ key: LocalizationKey) -> String {
        languageManager.text(key)
    }
    @State private var isSceneActive: Bool = true
    @State private var fishDirection: CGFloat = 1
    // 1 — плывёт вправо, -1 — влево
    
    // MARK: - Glass game analytics (session)
    @State private var glassGameId: String = UUID().uuidString
    @State private var windowOpenedAt: Date? = nil
    @State private var windowClosedAt: Date? = nil
    @State private var gameStartedAt: Date? = nil
    @State private var gameEndedAt: Date? = nil

    @State private var maxSpillLevel01: Double = 0
    @State private var lastPoints: Double = 0

    // { "ISO8601 time": pointsAtThatMoment }
    @State private var backgroundEvents: [String: Double] = [:]

    // защита от двойной отправки
    @State private var didEnqueueGlassGameBatch: Bool = false
    
    @State private var lastDraftSaveAt: Date = .distantPast



    let roll: Double
    let pitch: Double
    let energy: Double
    let spillSeverity: Double
    @Binding var isGameOver: Bool
    
    // ids already used by main telemetry batching
    // ids already used by main telemetry batching
    let deviceId: String
    let driverId: String?     // user id
    let sessionId: String


    init(
        roll: Double,
        pitch: Double,
        energy: Double,
        spillSeverity: Double,
        isGameOver: Binding<Bool> = .constant(false),

        deviceId: String,
        driverId: String?,
        sessionId: String
    ) {
        self.roll = roll
        self.pitch = pitch
        self.energy = energy
        self.spillSeverity = spillSeverity
        self._isGameOver = isGameOver

        self.deviceId = deviceId
        self.driverId = driverId
        self.sessionId = sessionId
    }



    // MARK: - Water state
    private let initialFillLevel01: Double = 0.90


    @State private var waterLevel01: CGFloat = 0.9

    
    @State private var gameStartTime: Date = Date()
    
    @State private var headroomPx: CGFloat = 22
    @State private var headroomCalibrated: Bool = false




    @State private var lastSpillTime: Date = .distantPast
    @State private var lastRefillTime: Date = .distantPast


    @State private var phase: Double = 0
    @State private var lastTick: Date = Date()

    // Baseline (slow) + high-pass (dynamic) attitude
    @State private var rollLP: Double = 0
    @State private var pitchLP: Double = 0
    @State private var rollDyn: Double = 0
    @State private var pitchDyn: Double = 0


    // Geometry cache for spill math
    @State private var cachedGlassRect: CGRect = .zero

    // Debug
    @State private var debugOverflowPx: CGFloat = 0
    @State private var debugMinSurfaceY: CGFloat = 0
    @State private var debugSpillLineY: CGFloat = 0
    @State private var debugDidSpill: Bool = false
    @State private var debugSpillHoldUntil: Date = .distantPast
    
    @State private var debugSpillCount: Int = 0
    @State private var debugSpilledTotal01: CGFloat = 0
    @State private var debugLastSpillAmount01: CGFloat = 0
    
    @State private var debugRefilledTotal01: CGFloat = 0
    @State private var debugLastRefillAmount01: CGFloat = 0
    
    

   

    
    //модель капель и стейты
    private struct Droplet: Identifiable {
        let id = UUID()
        var p: CGPoint
        var v: CGVector
        var life: CGFloat
        var radius: CGFloat
    }

    @State private var droplets: [Droplet] = []
    @State private var viewSize: CGSize = .zero
    
    // MARK: - Fish overlay (inside water)

    private func fishAssetName(for progress01: CGFloat) -> String {
        // progress01 = 0..1 (0%..100% “пролито” по начальной воде)
        if progress01 < 0.33 { return "fish_low" }
        if progress01 < 0.66 { return "fish_mid" }
        return "fish_high"
    }

    @ViewBuilder
    private func fishLayer(in rect: CGRect, progress01: CGFloat, phase: CGFloat) -> some View {
        let name = fishAssetName(for: progress01)

//        // Размер рыбки относительно стакана
//        let size = min(rect.width, rect.height) * 0.40
//
//        // Позиция + лёгкое “плавание”
//        let x = rect.minX + rect.width * 0.62 + cos(phase * 0.8) * rect.width * 0.03
//        let y = rect.minY + rect.height * 0.55 + sin(phase * 0.9) * rect.height * 0.02
//        
        // Размер рыбки
//        let size = min(rect.width, rect.height) * 0.40 // так рыбка будет уменьшаться с поднятием воды
        let size = rect.width * 0.40 // так рыбка иднакового размера все время


        // Отступ от края, чтобы рыбка не выходила за стакан
        let margin = size * 0.65

        // Длина хода от края до края
        let travel = max(0, rect.width - margin * 2)

        // Нормализованное движение 0...1
        let t = (cos(phase * 0.8) + 1) * 0.5

        // Позиция X — от левого края до правого
        let x = rect.minX + margin + travel * t

        // Лёгкое вертикальное плавание
        let y = rect.minY
            + rect.height * 0.55
            + sin(phase * 0.9) * rect.height * 0.02

        // Направление для зеркального отражения
        let dir: CGFloat = sin(phase * 0.8) < 0 ? -1 : 1


        Image(name)
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .scaleEffect(x: dir, y: 1)   // зеркалим при обратном ходе
            // зеркалим рыбку по горизонтали при движении влево
            .transaction { t in
                t.animation = nil
            }
            .position(x: x, y: y)
            .opacity(0.9)
            .allowsHitTesting(false)
    }
    
    private func deadFishLayer(in rect: CGRect, progress01: CGFloat) -> some View {
        let name = fishAssetName(for: progress01)

        // Фиксированный размер от ширины стакана (не зависит от уровня воды)
        let size = rect.width * 0.40

        // Лежит на дне стакана, по центру
        let x = rect.midX
        let y = rect.maxY - size * 0.35

        // Если ассет “по умолчанию” смотрит не туда — поменяй на 1
        let baseFlip: CGFloat = 1

        return Image(name)
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .scaleEffect(x: baseFlip, y: 1)   // ориентация по горизонтали (под ассет)
            .rotationEffect(.degrees(180))    // вверх пузом
            .position(x: x, y: y)
            .opacity(0.9)
            .allowsHitTesting(false)
    }



    

    // MARK: - Tuning
    private let minFillLevel: CGFloat = 0.05

    // Долив: очень медленно, но стремимся к "почти полному стакану"
    private let refillPerSecond: CGFloat = 0.00002
    private let targetFillLevel: CGFloat = 1.02
    private let maxFillLevel: CGFloat = 1.06

    // Линия перелива: внутренняя кромка сверху (px от верхней границы glassRect)
    private let spillLipPx: CGFloat = 14

    // Волны/наклон (влияют на вероятность перелива)
    private let tiltScale: CGFloat = 80
    private let Amax: CGFloat = 24
    private let ampScale: CGFloat = 55

    // Spill dynamics
    private let spillCooldown: TimeInterval = 0.10
    private let spillThresholdPx: CGFloat = 0.3          // перелив считаем, когда превысили кромку на 0.5px
    private let maxSpillPerEvent: CGFloat = 0.030        // максимум потери уровня за один "сплеск"
    private let spillGain: CGFloat = 4.4                // множитель силы spill относительно overflow/height
    // Sensor-based spill tuning
    private let severityThreshold: Double = 0.05     // lower -> spills easier on bumps
    private let severityGain01PerSec: Double = 0.1 // higher -> more volume per second above threshold


    private let timer = Timer.publish(every: 1.0 / 60.0, on: .main, in: .common).autoconnect()
    
    // MARK: - Glass proportions
    private let glassWidthScale: CGFloat = 1.20   // +20% ширины

    
    // MARK: - Glass shape (tapered cup)
    private func glassOuterPath(in r: CGRect) -> Path {
        // Expand width by scale, keep center


        // Настройки “силуэта”
        let topInset: CGFloat = 10            // насколько “сужаем” верх внутрь (меньше = шире верх)
        let bottomInset: CGFloat = 34         // насколько “сужаем” низ внутрь (больше = уже низ)
        let corner: CGFloat = 26              // скругление углов
        let rimDrop: CGFloat = 10             // высота кромки (ободка)

        let topLeft = CGPoint(x: r.minX + topInset, y: r.minY + rimDrop)
        let topRight = CGPoint(x: r.maxX - topInset, y: r.minY + rimDrop)
        let bottomRight = CGPoint(x: r.maxX - bottomInset, y: r.maxY)
        let bottomLeft = CGPoint(x: r.minX + bottomInset, y: r.maxY)

        var p = Path()

        // Начинаем слева сверху (под ободком)
        p.move(to: CGPoint(x: topLeft.x + corner, y: topLeft.y))

        // Верхняя линия до правого верха
        p.addLine(to: CGPoint(x: topRight.x - corner, y: topRight.y))
        p.addQuadCurve(
            to: CGPoint(x: topRight.x, y: topRight.y + corner),
            control: CGPoint(x: topRight.x, y: topRight.y)
        )

        // Правая стенка к низу
        p.addLine(to: CGPoint(x: bottomRight.x, y: bottomRight.y - corner))
        p.addQuadCurve(
            to: CGPoint(x: bottomRight.x - corner, y: bottomRight.y),
            control: CGPoint(x: bottomRight.x, y: bottomRight.y)
        )

        // Низ
        p.addLine(to: CGPoint(x: bottomLeft.x + corner, y: bottomLeft.y))
        p.addQuadCurve(
            to: CGPoint(x: bottomLeft.x, y: bottomLeft.y - corner),
            control: CGPoint(x: bottomLeft.x, y: bottomLeft.y)
        )

        // Левая стенка вверх
        p.addLine(to: CGPoint(x: topLeft.x, y: topLeft.y + corner))
        p.addQuadCurve(
            to: CGPoint(x: topLeft.x + corner, y: topLeft.y),
            control: CGPoint(x: topLeft.x, y: topLeft.y)
        )

        p.closeSubpath()
        return p
    }
    
    private func glassWaterClipPath(in r: CGRect) -> Path {
        let wall: CGFloat = 2
        let inner = r.insetBy(dx: wall, dy: wall)
        return glassOuterPath(in: inner)
    }


    private func glassInnerPath(in r: CGRect) -> Path {
        // Внутренняя полость (чуть уже внешней — имитация толщины стекла)
        let wall: CGFloat = 6
        let inner = r.insetBy(dx: wall, dy: wall)
        return glassOuterPath(in: inner)
    }
    
    // функция блика воды
    private func waterGlintLayer(in rect: CGRect, phase: CGFloat) -> some View {
        let t = (sin(phase * 0.60) + 1) * 0.5
        let w = rect.width * 0.28
        let h = max(40, rect.height * 1.35)
        let x = rect.minX + rect.width * t
        let y = rect.minY + rect.height * 0.35

        return Rectangle()
            .fill(
                LinearGradient(
                    gradient: Gradient(stops: [
                        .init(color: Color.white.opacity(0.00), location: 0.00),
                        .init(color: Color.white.opacity(0.22), location: 0.45),
                        .init(color: Color.white.opacity(0.00), location: 1.00)
                    ]),
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
            .frame(width: w, height: h)
            .rotationEffect(.degrees(-18))
            .position(x: x, y: y)
            .blur(radius: 0.6)
            .opacity(0.75)
            .blendMode(.screen)
            .allowsHitTesting(false)
    }


    private func glassRimPath(in r: CGRect) -> Path {


        // Ободок: тонкая “капля” сверху
        let topInset: CGFloat = 10
        let rimHeight: CGFloat = 10
        let rimRadius: CGFloat = 18

        let rimRect = CGRect(
            x: r.minX + topInset,
            y: r.minY + 2,
            width: r.width - 2 * topInset,
            height: rimHeight * 2
        )


        return Path(roundedRect: rimRect, cornerRadius: rimRadius)
    }


    var body: some View {
        GeometryReader { geo in
            let size = geo.size
            
            let glassInset: CGFloat = 18
            let glassRect = CGRect(origin: .zero, size: size).insetBy(dx: glassInset, dy: glassInset)
            
            let extraWidth = glassRect.width * (glassWidthScale - 1.0)
            let expandedGlassRect = glassRect.insetBy(dx: -extraWidth / 2.0, dy: 0)

            // Vector clip (reliable): keeps water strictly INSIDE the glass silhouette.
            let waterClipPath = glassWaterClipPath(in: expandedGlassRect)

            
            // Loss meter: spilled percent from bottom
            // Game capacity grows with refill: total spillable = initial + refilled
            let startFill01: CGFloat = max(0.05, min(0.99, CGFloat(initialFillLevel01)))

            // net spilled = spilled - refilled (refill давит поплавок вниз)
            let netSpilled01 = max(0.0, debugSpilledTotal01 - debugRefilledTotal01)

            // progress is always 0..100% of INITIAL WATER amount
            let progress01 = min(1.0, max(0.0, netSpilled01 / max(0.0001, startFill01)))
            let spilledPercent = progress01 * 100.0
            
            // --- Aggregates for UI ---
            let totalSpilledRawPercent = (debugSpilledTotal01 / startFill01) * 100.0
            let refillBonusPercent = (debugRefilledTotal01 / startFill01) * 100.0
            
            


            // Poplavok moves up exactly by net spilled volume (in full-cup units)
            let initialWaterHeightPx = expandedGlassRect.height * startFill01

            let markerY = expandedGlassRect.maxY
                - netSpilled01 / startFill01 * initialWaterHeightPx




            
            let effectiveWaterRect = CGRect(
                x: expandedGlassRect.minX,
                y: expandedGlassRect.minY,
                width: expandedGlassRect.width,
                height: max(0, markerY - expandedGlassRect.minY)
            )



            let gameOverNow = (progress01 >= 0.9995)



            
            ZStack {
                // Water (clipped inside glass path)
                let waterShape = waterPath(
                    in: effectiveWaterRect,
                    roll: rollDyn,
                    pitch: pitchDyn,
                    energy: energy,
                    phase: phase,
                    waterLevel01: waterLevel01
                )


                
                ZStack {
                    // Water gradient fill (top lighter, bottom deeper)
                    waterShape
                        .fill(
                            LinearGradient(
                                gradient: Gradient(stops: [
                                    .init(color: Color(red: 0.25, green: 0.70, blue: 1.00).opacity(0.85), location: 0.00),
                                    .init(color: Color(red: 0.00, green: 0.35, blue: 0.95).opacity(0.82), location: 0.55),
                                    .init(color: Color(red: 0.00, green: 0.20, blue: 0.75).opacity(0.85), location: 1.00)
                                ]),
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )

                    // Animated specular highlight (glint)
                    waterGlintLayer(in: effectiveWaterRect, phase: phase)
                        .mask(waterShape)

                    // Fish layer inside water
                    fishLayer(in: effectiveWaterRect, progress01: progress01, phase: phase)
                        .mask(waterShape)
                }
                .clipShape(waterClipPath)
                .drawingGroup(opaque: false, colorMode: .linear)



                // progress01 у тебя уже есть выше (0..1)
                if progress01 < 0.999 {
                    fishLayer(in: effectiveWaterRect, progress01: progress01, phase: phase)
                        .mask(waterShape)          // рыбка видна только в воде
                        // и строго внутри стакана
                }



                
                // === "Float / platform" — spilled progress from bottom (dark green) ===
                // Вся область НИЖЕ markerY становится тёмно-зелёной, а синяя вода остаётся сверху.
                Rectangle()
                    .fill(Color(red: 0.02, green: 0.25, blue: 0.10).opacity(0.85))
                    .frame(width: expandedGlassRect.width, height: max(0, expandedGlassRect.maxY - markerY))
                    .position(x: expandedGlassRect.midX, y: (markerY + expandedGlassRect.maxY) * 0.5)
                    .clipShape(waterClipPath)



                // === Float / platform edge (thick brown line) ===
                Path { p in
                    let w: CGFloat = 5
                    let x0 = expandedGlassRect.minX
                    let x1 = expandedGlassRect.maxX


                    p.move(to: CGPoint(x: x0, y: markerY))
                    p.addLine(to: CGPoint(x: x1, y: markerY))
                }
                .stroke(
                    Color(red: 0.45, green: 0.28, blue: 0.12).opacity(0.95),
                    style: StrokeStyle(
                        lineWidth: 10,
                        lineCap: .butt,
                        lineJoin: .miter
                    )
                )
                
                .clipShape(waterClipPath)
                
                // 100% spilled: рыбка лежит на дне в зелёной зоне (видима поверх зелёного)
                if progress01 >= 0.999 {
                    deadFishLayer(in: expandedGlassRect, progress01: progress01)
                        .clipShape(waterClipPath) // строго внутри стакана
                }


                // small label near the line (optional)
                Text("\(t(.glassGameSpilledShort)) \(String(format: "%.1f%%", Double(spilledPercent)))")
                    .font(.system(size: 16, weight: .semibold, design: .monospaced))
                    .foregroundColor(.white.opacity(0.85))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.black.opacity(0.35))
                    .cornerRadius(8)
                    .position(
                        x: expandedGlassRect.midX,
                        y: max(expandedGlassRect.minY + 18, min(expandedGlassRect.maxY - 18, markerY - 14))
                    )
                    .zIndex(100)

                    

                


                // Highlight
                //Path(roundedRect: glassRect.insetBy(dx: 10, dy: 24), cornerRadius: 20)
                  //  .stroke(Color.white.opacity(0.10), lineWidth: 6)
                    //.clipShape(outerGlassPath)

                

                // ===== Debug overlay =====
//                VStack(alignment: .leading, spacing: 6) {
//                    Text("WaterGlassView.swift — LIVE")
//
//                    Text(String(format: "Water: %.3f", Double(waterLevel01)))
//                    Text(String(format: "OverflowPx: %.2f", Double(debugOverflowPx)))
//                    Text(String(format: "minY: %.1f", Double(debugMinSurfaceY)))
//                    Text(String(format: "spillY: %.1f", Double(debugSpillLineY)))
//                    Text("Spill: \(debugDidSpill ? "YES" : "NO")")
//                    Text("Spills: \(debugSpillCount)")
//                    Text(String(format: "SpilledTot: %.3f (%.1f%%)", Double(debugSpilledTotal01), Double(debugSpilledTotal01 * 100.0)))
//                    Text(String(format: "RefilledTot: %.3f", Double(debugRefilledTotal01)))
//                    Text(String(format: "NetSpilled: %.3f", Double(max(0.0, debugSpilledTotal01 - debugRefilledTotal01))))
//                    Text(String(format: "LastRefill: %.4f", Double(debugLastRefillAmount01)))
//
//                    Text(String(format: "LastSpill: %.4f", Double(debugLastSpillAmount01)))
//
//                }
//                .font(.system(size: 13, weight: .semibold, design: .monospaced))
//                .foregroundColor(.white.opacity(0.85))
//                .padding(10)
//                .background(Color.black.opacity(0.35))
//                .cornerRadius(10)
//                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
//                .padding(.top, 10)
//                .padding(.leading, 10)
//                .zIndex(10)
                
                // ===== Glass outline (from old file style) =====

                // Outer contour
                glassOuterPath(in: expandedGlassRect)
                    .stroke(Color.white.opacity(0.75), lineWidth: 3)
                    .allowsHitTesting(false)
                    .zIndex(50)

                // Inner contour (glass thickness)
                glassInnerPath(in: expandedGlassRect)
                    .stroke(Color.white.opacity(0.35), lineWidth: 2)
                    .allowsHitTesting(false)
                    .zIndex(51)

                // Soft inner highlight
                glassInnerPath(in: expandedGlassRect)
                    .stroke(Color.white.opacity(0.18), lineWidth: 8)
                    .blur(radius: 6)
                    .offset(x: 10, y: -8)
                    .mask(glassInnerPath(in: expandedGlassRect))
                    .allowsHitTesting(false)
                    .zIndex(49)


                
                ForEach(droplets) { d in
                    Circle()
                        .fill(Color.white.opacity(0.45))
                        .frame(width: d.radius * 2, height: d.radius * 2)
                        .position(d.p)
                        .opacity(Double(max(0.0, min(1.0, d.life / 0.6))))
                        .blur(radius: 0.2)
                        .allowsHitTesting(false)
                }
                .zIndex(80)

                
//                photoView


            }
            .preference(
                            key: SpillStatsPreferenceKey.self,
                            value: SpillStats(
                                totalSpilledRawPercent: Double(totalSpilledRawPercent),
                                refillBonusPercent: Double(refillBonusPercent)
                            )
                        )
                        .onChange(of: gameOverNow) { newValue in
                            isGameOver = newValue
                            
                            if newValue == true, gameEndedAt == nil {
                                    gameEndedAt = Date()
                                    windowClosedAt = windowClosedAt ?? Date()
                                    saveGlassGameDraft()
                                    enqueueGlassGameBatchIfNeeded(aborted: false)
                                }
                        }

            .onAppear {
                // 1) recover stale draft from previous run (crash/kill/background without onDisappear)
                    recoverAndEnqueueStaleGlassGameDraftIfNeeded()

                    // 2) start new session
                
                   glassGameId = UUID().uuidString
                   windowOpenedAt = Date()
                   windowClosedAt = nil
                   gameStartedAt = nil
                   gameEndedAt = nil
                   maxSpillLevel01 = 0
                   lastPoints = 0
                   backgroundEvents = [:]
                   didEnqueueGlassGameBatch = false

                   saveGlassGameDraft()
                
                gameStartTime = Date()
                viewSize = size

                // Compute final glass rect from size (single source of truth)
                let base = CGRect(origin: .zero, size: size).insetBy(dx: glassInset, dy: glassInset)
                let extraWidth = base.width * (glassWidthScale - 1.0)
                let rect = base.insetBy(dx: -extraWidth / 2.0, dy: 0)
                cachedGlassRect = rect

                // Reset simulation baselines on screen appear
                let start01: CGFloat = max(0.05, min(0.99, CGFloat(initialFillLevel01)))
                waterLevel01 = start01
                debugSpilledTotal01 = 0
                debugRefilledTotal01 = 0
                debugSpillCount = 0
                debugDidSpill = false
                isGameOver = false
                lastSpillTime = .distantPast
                lastRefillTime = .distantPast

                // Baseline init (so the surface starts level for the current resting pose)
                rollLP = roll
                pitchLP = pitch
                rollDyn = 0
                pitchDyn = 0


                // Calibrate fixed headroom once, based on FINAL cachedGlassRect
                if !headroomCalibrated {
                    let spillLineY = rect.minY + spillLipPx
                    let baseLevel0 = rect.minY + rect.height * (1.0 - start01)

                    headroomPx = baseLevel0 - spillLineY
                    headroomCalibrated = true
                }
            }

            .onChange(of: size) { newSize in
                viewSize = newSize

                let base = CGRect(origin: .zero, size: newSize).insetBy(dx: glassInset, dy: glassInset)
                let extraWidth = base.width * (glassWidthScale - 1.0)
                cachedGlassRect = base.insetBy(dx: -extraWidth / 2.0, dy: 0)
            }


            .onChange(of: scenePhase) { ph in
                let active = (ph == .active)
                isSceneActive = active

                // фиксируем сворачивание без завершения
                if ph == .inactive || ph == .background {
                    backgroundEvents[isoUTC(Date())] = lastPoints
                    saveGlassGameDraft()
                }

                // Reset tick baseline to avoid huge dt jump on resume
                if active {
                    lastTick = Date()
                }
            }

        
        .onDisappear {
            windowClosedAt = Date()
            saveGlassGameDraft()

            // если окно закрыли без явного завершения — aborted
            enqueueGlassGameBatchIfNeeded(aborted: gameEndedAt == nil)
        }


        .onReceive(timer) { now in
            
            // Hard pause the game loop while app is not active (prevents hangs after background/foreground)
            if !isSceneActive {
                lastTick = now
                return
            }
            
            let timeSinceStart = now.timeIntervalSince(gameStartTime)
            let gracePeriod: TimeInterval = 1.5   // секунды

            // В первые секунды spill запрещён полностью
            let spillAllowed = timeSinceStart > gracePeriod
            
            let energyThreshold: Double = 0.005
            let isMovingEnough = energy > energyThreshold



            
            if isGameOver {
                return
            }

            let dt = min(1.0/20.0, max(0.0, now.timeIntervalSince(lastTick)))
            lastTick = now
            phase += dt * 2.6  // higher = fish swims faster

            fishDirection = cos(phase) >= 0 ? 1 : -1
            // направление рыбки: автоматически меняется в точках разворота

            


            
            // --- Soft pitch integration without "instant tilt" at rest ---
            // Update baseline only when device is mostly still, so resting pose becomes "level".
            if !isMovingEnough {
                let beta = 0.995
                rollLP = beta * rollLP + (1 - beta) * roll
                pitchLP = beta * pitchLP + (1 - beta) * pitch
            }

            // High-pass (dynamic) components used by water + spill math
            rollDyn = roll - rollLP
            pitchDyn = pitch - pitchLP

            
            stepDroplets(dt: CGFloat(dt))

            
            // === Effective bottom follows the float (brown line) ===
            // === Core progress math (your spec) ===
            let startFill01: CGFloat = max(0.05, min(0.99, CGFloat(initialFillLevel01)))

            // refill presses the float down
            let netSpilled01 = max(0.0, debugSpilledTotal01 - debugRefilledTotal01)
            let progress01 = min(1.0, max(0.0, netSpilled01 / max(0.0001, startFill01)))
            
            // points snapshot for analytics (same as UI metric)
            lastPoints = Double((debugRefilledTotal01 / startFill01) * 100.0)

            
            // --- analytics: max spill ---
            let p = Double(progress01)
            if p > maxSpillLevel01 { maxSpillLevel01 = p }

            // --- analytics: start time (после небольшой задержки от открытия окна) ---
            if gameStartedAt == nil {
                let grace: TimeInterval = 1.5
                if now.timeIntervalSince(gameStartTime) > grace {
                    gameStartedAt = Date()
                }
            }

            saveGlassGameDraft()

            // stop refill when only 1% remains (i.e., spilled >= 99% of initial water)
            let allowRefill = progress01 < 0.99

            // endgame mode: allow spill even with low energy near the end
            let endgameMode = progress01 >= 0.97

            let initialWaterHeightPx = cachedGlassRect.height * startFill01

            let effectiveBottomY = cachedGlassRect.maxY
                - (netSpilled01 / startFill01) * initialWaterHeightPx

            let effectiveRect = CGRect(
                x: cachedGlassRect.minX,
                y: cachedGlassRect.minY,
                width: cachedGlassRect.width,
                height: max(0, effectiveBottomY - cachedGlassRect.minY)
            )

            // === Slow refill (toward target) ===
            // Считаем фактический долив, чтобы “давить вниз” на поплавок.
            debugLastRefillAmount01 = 0

            // refill rate: 0.0002% per second of INITIAL WATER amount
            // 0.0002% = 0.000002 in fraction units
            
            let refillRate01PerSec: CGFloat = startFill01 * 0.0002

            if allowRefill {
                let wantAdd = refillRate01PerSec * CGFloat(dt)

                // cannot "refill more than spilled": cap so netSpilled never goes below 0
                let netSpilledForRefill01 = max(0.0, debugSpilledTotal01 - debugRefilledTotal01)
                let add = max(0.0, min(wantAdd, netSpilledForRefill01))

                if add > 0 {
                    debugRefilledTotal01 += add
                    debugLastRefillAmount01 = add
                    lastRefillTime = now
                }
                
            
            }



            if waterLevel01 > maxFillLevel { waterLevel01 = maxFillLevel }
            if waterLevel01 < minFillLevel { waterLevel01 = minFillLevel }

            // === Spill ===
            
            // Важно: считаем перелив относительно "эффективного" стакана,
            // у которого дно поднято до уровня поплавка (marker).
            // === Spill ===
            // Важно: считаем перелив относительно "эффективного" стакана.
            // Если эффективная высота стала слишком маленькой, это конец игры (иначе вы "зависаете" на ~98% из-за guard).
            if effectiveRect.width <= 10 || effectiveRect.height <= 10 {
                isGameOver = true
                return
            }


            // computeOverflow is disabled (sensor-based spill); keep debug values stable
            debugOverflowPx = 0
            debugMinSurfaceY = 0
            debugSpillLineY = cachedGlassRect.minY + spillLipPx



            let sev = spillSeverity
            let sevExcess = max(0.0, sev - severityThreshold)

            if spillAllowed,
               sevExcess > 0,
               now.timeIntervalSince(lastSpillTime) > spillCooldown {

                // Continuous-style spill per event window (dt-scaled), capped per event
                let wantSpill01 = sevExcess * severityGain01PerSec * dt
                let spillAmount01 = min(Double(maxSpillPerEvent), wantSpill01)

                if spillAmount01 > 0 {
                    debugDidSpill = true
                    debugSpillCount += 1
                    lastSpillTime = now

                    debugSpilledTotal01 += CGFloat(spillAmount01)

                    // Clamp: cannot exceed "initial water + refill" in net terms
                    let maxNetSpill01 = startFill01
                    if debugSpilledTotal01 - debugRefilledTotal01 > maxNetSpill01 {
                        debugSpilledTotal01 = maxNetSpill01 + debugRefilledTotal01
                    }

                    // Splash amount scaled by severity
                    let splashPowerPx: CGFloat = min(30, CGFloat(sevExcess) * 14)
                    spawnSplash(glassRect: cachedGlassRect,
                                spillLineY: cachedGlassRect.minY + spillLipPx,
                                overflowPx: splashPowerPx)
                }
            }
            let now = Date()
            if now.timeIntervalSince(lastDraftSaveAt) >= 1.0 {
                lastDraftSaveAt = now
                saveGlassGameDraft()
            }


            

        }
    }
}
    private func waterPath(
        in glassRect: CGRect,
        roll: Double,
        pitch: Double,
        energy: Double,
        phase: Double,
        waterLevel01: CGFloat
    ) -> Path {

        // Fixed headroom strategy:
        // mean water level stays at constant distance from the spill line (as at game start)
        let spillLineY = glassRect.minY + spillLipPx
        var baseLevel: CGFloat = spillLineY + headroomPx

        // Do not let the mean level go below the effective bottom
        baseLevel = min(baseLevel, glassRect.maxY - 8)


        // roll = left/right tilt
        let rollMax = 0.9
        let pitchMax = 0.9

        let rollClamped = max(-rollMax, min(rollMax, roll))
        let pitchClamped = max(-pitchMax, min(pitchMax, pitch))

        // Soft pitch (dynamic already high-passed): keep it weaker than roll
        let pitchTiltScale: CGFloat = tiltScale * 0.95

        let tilt: CGFloat = 0 // water surface stays level; waves only


        // waves amplitude from energy
        let A = min(Amax, CGFloat(max(0, energy)) * ampScale)
        let A2 = A * 0.25

        let freq1: CGFloat = 1.6
        let freq2: CGFloat = 3.2

        let samples = 64
        var p = Path()

        for i in 0...samples {
            let x01 = CGFloat(i) / CGFloat(samples)
            let x = glassRect.minX + x01 * glassRect.width

            let tiltY = tilt * (x01 - 0.5)

            let w1 = A * sin(2 * .pi * (freq1 * x01 + CGFloat(phase)))
            let w2 = A2 * sin(2 * .pi * (freq2 * x01 + CGFloat(phase) * 1.35))

            let y = baseLevel + tiltY + w1 + w2

            if i == 0 {
                p.move(to: CGPoint(x: x, y: y))
            } else {
                p.addLine(to: CGPoint(x: x, y: y))
            }
        }

        p.addLine(to: CGPoint(x: glassRect.maxX, y: glassRect.maxY))
        p.addLine(to: CGPoint(x: glassRect.minX, y: glassRect.maxY))
        p.closeSubpath()

        return p
    }

    private func computeOverflow(
        in glassRect: CGRect,
        roll: Double,
        pitch: Double,
        energy: Double,
        phase: Double,
        waterLevel01: CGFloat
    ) -> (overflow: CGFloat, minSurfaceY: CGFloat, spillLineY: CGFloat) {

        let spillLineY = glassRect.minY + spillLipPx
        var baseLevel: CGFloat = spillLineY + headroomPx

        // Do not let the mean level go below the effective bottom
        baseLevel = min(baseLevel, glassRect.maxY - 8)


        let rollMax = 0.8
        let pitchMax = 0.8

        let rollClamped = max(-rollMax, min(rollMax, roll))
        let pitchClamped = max(-pitchMax, min(pitchMax, pitch))

        let pitchTiltScale: CGFloat = tiltScale * 0.35

        let tilt: CGFloat =
            (-CGFloat(rollClamped) * tiltScale) +
            (-CGFloat(pitchClamped) * pitchTiltScale)


        let A = min(Amax, CGFloat(max(0, energy)) * ampScale)
        let A2 = A * 0.25

        let freq1: CGFloat = 1.6
        let freq2: CGFloat = 3.2

        let samples = 64
        var minSurfaceY: CGFloat = .greatestFiniteMagnitude

        for i in 0...samples {
            let x01 = CGFloat(i) / CGFloat(samples)
            let tiltY = tilt * (x01 - 0.5)

            let w1 = A * sin(2 * .pi * (freq1 * x01 + CGFloat(phase)))
            let w2 = A2 * sin(2 * .pi * (freq2 * x01 + CGFloat(phase) * 1.35))

            let y = baseLevel + tiltY + w1 + w2
            minSurfaceY = min(minSurfaceY, y)
        }

        // Spill line is inside-top lip
        let overflow = spillLineY - minSurfaceY
        return (max(0, overflow), minSurfaceY, spillLineY)

    }
    private func stepDroplets(dt: CGFloat) {
        guard dt > 0 else { return }
        let g: CGFloat = 1800

        var next: [Droplet] = []
        next.reserveCapacity(droplets.count)

        for var d in droplets {
            d.life -= dt
            if d.life <= 0 { continue }

            d.v.dy += g * dt
            d.p.x += d.v.dx * dt
            d.p.y += d.v.dy * dt

            if viewSize != .zero {
                if d.p.y > viewSize.height + 40 { continue }
                if d.p.x < -40 || d.p.x > viewSize.width + 40 { continue }
            }


            next.append(d)
        }

        droplets = next
    }

    private func spawnSplash(glassRect: CGRect, spillLineY: CGFloat, overflowPx: CGFloat) {
            // more droplets per spill; keep it bounded
            let n = max(4, min(20, Int(overflowPx * 0.6)))
            if n <= 0 { return }


        for _ in 0..<n {
            let x = CGFloat.random(in: (glassRect.minX + 16)...(glassRect.maxX - 16)) // количество капель: зависит от перелива, но ограничено минимумом и максимумом
            let y = spillLineY - CGFloat.random(in: 2...10)

            let vx = CGFloat.random(in: -320...320) // горизонтальная скорость капли (разлёт влево/вправо)
            let vy = -CGFloat.random(in: 320...650) // вертикальная скорость вверх (минус — система координат iOS)


            let life = CGFloat.random(in: 0.30...0.60) // время жизни капли в секундах
            let r = CGFloat.random(in: 1.5...3.3) // радиус капли (размер)


            droplets.append(
                Droplet(
                    p: CGPoint(x: x, y: y),
                    v: CGVector(dx: vx, dy: vy),
                    life: life,
                    radius: r
                )
            )
        }

        let limit = 200
        if droplets.count > limit {
            droplets = Array(droplets.suffix(limit))
        }

    }
        
        
    
    
    
    


    // MARK: - Glass game draft + enqueue

    private func isoUTC(_ d: Date) -> String {
        let f = ISO8601DateFormatter()
        f.timeZone = TimeZone(secondsFromGMT: 0)
        return f.string(from: d)
    }

    private var glassGameDraftURL: URL {
        let fm = FileManager.default
        let base = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let dir = base.appendingPathComponent("glass-game-drafts-v1", isDirectory: true)
        if !fm.fileExists(atPath: dir.path) {
            try? fm.createDirectory(at: dir, withIntermediateDirectories: true)
        }
        return dir.appendingPathComponent("draft.json")
    }

    private struct GlassGameDraft: Codable {
        let glassGameId: String
        let windowOpenedAt: Date?
        let windowClosedAt: Date?
        let gameStartedAt: Date?
        let gameEndedAt: Date?

        let maxSpillLevel01: Double

        // НОВОЕ: сколько всего долито (0...1, cumulative)
        let totalRefilled01: Double?


        let backgroundEvents: [String: Double]
    }


    private func saveGlassGameDraft() {
        let draft = GlassGameDraft(
            glassGameId: glassGameId,
            windowOpenedAt: windowOpenedAt,
            windowClosedAt: windowClosedAt,
            gameStartedAt: gameStartedAt,
            gameEndedAt: gameEndedAt,
            maxSpillLevel01: maxSpillLevel01,
            
            totalRefilled01: Double(debugRefilledTotal01),
            backgroundEvents: backgroundEvents
        )
        if let data = try? JSONEncoder().encode(draft) {
            try? data.write(to: glassGameDraftURL, options: .atomic)
        }
    }

    private func enqueueGlassGameBatchIfNeeded(aborted: Bool) {
        guard didEnqueueGlassGameBatch == false else { return }
        guard let opened = windowOpenedAt else { return }

        let closed = windowClosedAt ?? Date()
        
        let gameDurationSec: Double? =
            (gameStartedAt != nil && gameEndedAt != nil)
            ? gameEndedAt!.timeIntervalSince(gameStartedAt!)
            : nil
        
        let windowDurationSec: Double? =
            (windowOpenedAt != nil && windowClosedAt != nil)
            ? windowClosedAt!.timeIntervalSince(windowOpenedAt!)
            : nil


        let batch = GlassGameBatch(
            device_id: deviceId,
            driver_id: driverId,
            session_id: sessionId,

            game_id: glassGameId,
            window_opened_at: isoUTC(opened),
            game_started_at: gameStartedAt.map(isoUTC),
            game_ended_at: gameEndedAt.map(isoUTC),
            window_closed_at: isoUTC(closed),

            max_spill_level: maxSpillLevel01,
            total_refilled_01: Double(debugRefilledTotal01),
            game_duration_sec: gameDurationSec,
            window_duration_sec: windowDurationSec,
            background_events: backgroundEvents,

            analytics: [
                "background_count": Double(backgroundEvents.count),
                "active_play_s": max(0, (gameEndedAt ?? Date()).timeIntervalSince(gameStartedAt ?? opened))
            ],
            aborted: aborted
        )

        NetworkManager.shared.uploadGlassGame(batch: batch, sessionId: sessionId, completion: nil)

        didEnqueueGlassGameBatch = true
        try? FileManager.default.removeItem(at: glassGameDraftURL)
    }
    
    private func recoverAndEnqueueStaleGlassGameDraftIfNeeded() {
        // If draft exists, it means previous session didn't reach onDisappear / gameOver.
        let url = glassGameDraftURL
        guard FileManager.default.fileExists(atPath: url.path) else { return }

        guard let data = try? Data(contentsOf: url),
              let draft = try? JSONDecoder().decode(GlassGameDraft.self, from: data)
        else {
            // corrupted draft -> delete to avoid infinite loop
            try? FileManager.default.removeItem(at: url)
            return
        }

        // If we have no open time, it isn't a valid session draft
        guard let opened = draft.windowOpenedAt else {
            try? FileManager.default.removeItem(at: url)
            return
        }

        // If the game was already ended AND should have been enqueued, we still treat it as aborted
        // because we don't know whether enqueue happened.
        let closed = draft.windowClosedAt ?? Date()

        let gameDurationSec: Double? =
            (draft.gameStartedAt != nil && draft.gameEndedAt != nil)
            ? draft.gameEndedAt!.timeIntervalSince(draft.gameStartedAt!)
            : nil

        let windowDurationSec: Double? =
            closed.timeIntervalSince(opened)

        let batch = GlassGameBatch(
            device_id: deviceId,
            driver_id: driverId,
            session_id: sessionId,

            game_id: draft.glassGameId,
            window_opened_at: isoUTC(opened),
            game_started_at: draft.gameStartedAt.map(isoUTC),
            game_ended_at: draft.gameEndedAt.map(isoUTC),
            window_closed_at: isoUTC(closed),

            max_spill_level: draft.maxSpillLevel01,

            // НОВОЕ: для recovery мы точно знаем только длительности по таймстемпам.
            // "всего долито" в draft раньше не сохраняли — поэтому nil.
            total_refilled_01: draft.totalRefilled01,
            game_duration_sec: gameDurationSec,
            window_duration_sec: windowDurationSec,


            background_events: draft.backgroundEvents,

            analytics: [
                "background_count": Double(draft.backgroundEvents.count),
                "recovered_draft": 1.0
            ],
            aborted: true
        )


        // Enqueue using the same persistent queue as main telemetry
        NetworkManager.shared.uploadGlassGame(batch: batch, sessionId: sessionId, completion: nil)

        // Delete draft only after enqueue (enqueue persists to disk)
        try? FileManager.default.removeItem(at: url)
    }

}
