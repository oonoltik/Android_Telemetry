# Android Telemetry Project

## Overview

Android приложение для сбора и отправки телеметрии:

* GPS
* IMU (accelerometer, gyroscope)
* orientation
* network state
* device state

Данные проходят pipeline:

```
sensors → frames → batch → outbox → delivery → backend
```

---

## Current Status

✅ Pipeline полностью работает
✅ Auth интегрирован
✅ Delivery подтверждён (`delivered id=...`)
⚠️ EU endpoint не отвечает (используется fallback)

Подробнее: `PROJECT_STATUS.md`

---

## Architecture

### Ingestion

* sensors → frames → batch

### Storage

* Room (outbox)

### Delivery

* WorkManager
* retry + backoff
* fallback (EU → RU)

### Auth

* `/auth/challenge`
* `/auth/register`
* bearer token
* Android stub bypass

---

## Quick Start

```bash
./gradlew clean assembleDebug
```

Запуск:

* через Android Studio
* или на устройстве

---

## Logs

Для диагностики:

```bash
adb logcat -s TelemetryDelivery
```

---

## Known Behavior

* EU endpoint может таймаутить
* RU fallback стабильно работает

---

## Development Notes

* используется `kotlinx.datetime`
* minSdk = 24
* OkHttp для networking
* WorkManager для delivery

---

## Context Files

* PROJECT_STATUS.md
* NEXT_STEPS.md
* KNOWN_ISSUES.md
* CHANGELOG.md

---

## Goal

📌 Надёжный telemetry pipeline
📌 Гарантированная доставка данных
📌 Готовность к production
