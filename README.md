# Telemetry (Android + iOS Monorepo)

## Overview

Этот репозиторий теперь содержит **оба клиента**:
- Android (Kotlin)
- iOS (Swift / Xcode)

---

## Structure

```
Android_Telemetry/
  android/   # Android project (Android Studio)
  ios/       # iOS project (Xcode)
  docs/
```

---

## How to run

### Android
Открыть:
```
android/
```
в Android Studio

---

### iOS
Открыть:
```
ios/TelemetryApp.xcodeproj
```
в Xcode

---

## Important

📌 Android — основной reference pipeline  
📌 iOS — должен сходиться по контракту и поведению  

---

## Pipeline

```
sensors → frames → batch → outbox → delivery → backend → finish
```

(одинаковый концепт для Android и iOS)

---

## Current focus

- aggregation parity (Android ↔ iOS)
- shared contracts
- throughput / backlog
- observability
