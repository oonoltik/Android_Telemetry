# Android Telemetry Project

## Overview

Android приложение для сбора и отправки телеметрии:

* GPS
* IMU
* orientation
* network state
* device state

Pipeline:

`sensors → frames → batch → outbox → delivery → backend → finish`

---

## Current Status

✅ Pipeline полностью работает  
✅ Auth интегрирован  
✅ Ingest (`200 OK`)  
✅ Finish (`200 OK`)  
✅ Pending finish recovery подтверждён  
✅ Trip API переведён на `EU first / RU fallback`  
⚠️ EU endpoint периодически нестабилен, поэтому RU fallback активно используется

---

## Architecture

### Ingestion

* sensors → frames → batch

### Storage

* Room (outbox)
* pending finish store
* delivery stats store

### Delivery

* WorkManager
* retry + backoff
* fallback (`EU → RU`)
* priority delivery для session с pending finish

### Auth

* `/auth/challenge`
* `/auth/register`
* bearer token
* Android bypass

### Finish

* lifecycle trigger (`onStop`)
* payload DTO (без `Any`)
* pending finish + retry worker
* immediate finish retry после delivered batch / network restore
* backend-compatible

### Trip API Routing

* `/trip/finish`
* `/trip/report`
* `/trips/recent`
* `/driver/home`

Работают по схеме:

```text
EU = primary
RU = fallback only
```

---

## What is already verified

* ingest success через EU/RU routing
* finish success
* early stop recovery
* network-loss recovery
* WorkManager delivery + finish retry convergence

---

## Remaining Gaps

* aggregation parity с iOS
* throughput / backlog optimization
* observability / metrics

---

## Quick Start

```bash
./gradlew clean assembleDebug
```

---

## Status Summary

Проект уже находится в состоянии:
* correctness confirmed
* recovery confirmed
* next phase = latency / throughput / aggregation parity
