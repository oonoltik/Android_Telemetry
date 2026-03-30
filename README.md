# Android Telemetry Project

## Overview

Android приложение для сбора и отправки телеметрии:

* GPS
* IMU
* orientation
* network state
* device state

Pipeline:
sensors → frames → batch → outbox → delivery → backend → finish

---

## Current Status

✅ Pipeline полностью работает  
✅ Auth интегрирован  
✅ Ingest (200 OK)  
✅ Finish (200 OK)  
⚠️ EU endpoint не отвечает (fallback на RU)

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
* Android bypass

### Finish

* lifecycle trigger (`onStop`)
* payload DTO (без Any)
* backend-compatible

---

## Quick Start

```bash
./gradlew clean assembleDebug