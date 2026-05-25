# Known Issues

## Android

### Emulator

- No real IMU in emulator.
- WaterGlass and telemetry motion behavior must be checked on physical device.
- CameraX behavior may differ between emulator and Samsung devices.

### Device / OEM

- Samsung battery restrictions can affect background services.
- ActivityRecognition may be unstable depending on permissions/OEM settings.
- Camera permission and lifecycle behavior must be tested on real Samsung device.

### Android Studio

- IDE may show false unresolved references after cache/index corruption.
- Gradle build is the source of truth.
- Recommended recovery:

```text
Sync Project
Invalidate Caches
Restart Android Studio
./gradlew assembleDebug
```

## Shared Android/iOS

### Contract Risks

- DTO mismatch risk remains for non-finalized endpoints.
- Motion divergence risk remains until MotionVectorComputer parity is complete.
- Dashcam backend contract still needs Android implementation.

### UI Parity

- Android must follow Swift UI/source behavior.
- Avoid inventing Android-specific layouts unless Swift does not define behavior.
- Patches should be concrete copy-paste blocks, not “find this function” instructions.

## WaterGlass / SaveFish

Current known watch points:

- verify splash behavior on physical device
- verify fish direction and death state after longer play sessions
- verify `/glass_game_ingest` payload against backend logs
- verify `aborted=false` on full spill/game-over

## Dashcam

Not implemented yet on Android.

Expected risk areas:

- CameraX lifecycle
- recording interruption
- file persistence
- archive metadata
- upload retry
- permission denial states
