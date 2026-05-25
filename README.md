# Android_Telemetry

Android + iOS telemetry system with a current focus on Android → Swift visual/UI parity.

## Overview

The project collects mobile driving telemetry, processes motion/sensor signals, persists batches locally, delivers them to the backend, and sends trip/game/session summaries for analytics.

Current Android direction:

- full Swift UI parity
- reliable telemetry delivery
- contract alignment with Swift/backend DTOs
- Dashcam / Video Mode implementation
- RU/EN localization cleanup

## Pipeline

```text
sensors → batch/outbox → delivery worker → backend → finish/report
```

## Current Android Status

Implemented and stabilized:

- Home screen with real driver telemetry data
- Trips archive rewritten to Swift-style layout
- Trip report screen brought close to Swift parity
- Orientation/state retention fixes via `rememberSaveable`
- Android telemetry pipeline and delivery flow
- Finish flow with pending/retry behavior
- Identity layer
- Day Monitoring
- Partial sensor context batching
- WaterGlass / SaveFish mini-game parity
- `glass_game_ingest` telemetry upload

## WaterGlass / SaveFish Game

Android now mirrors the Swift WaterGlass model:

- accelerometer/gyroscope driven water movement
- spill and refill mechanics
- fish state assets: `fish_low`, `fish_mid`, `fish_high`
- fish direction changes at glass walls
- game-over state
- splash particles inside/outside glass
- Swift-parity JSON payload sent to `/glass_game_ingest`

## Dashcam / Video Mode

Next major feature area:

- CameraX preview
- recording state and timer
- Swift-style video controls
- video archive
- video session metadata
- backend upload contract parity

## Development Notes

Android Studio may show false IDE errors when indexing is broken. Gradle is the source of truth:

```bash
./gradlew assembleDebug
```

API 24 compatibility rule:

- avoid direct `java.time` usage unless desugaring is configured
- use `SimpleDateFormat`, `Date`, and manual formatting where needed

## Project Structure

```text
Android_Telemetry/
├── android/
│   └── app/
├── README.md
├── PROJECT_STATUS.md
├── NEXT_STEPS.md
├── KNOWN_ISSUES.md
└── CHANGELOG.md
```
