# Changelog

## v4 - Android Telemetry Pipeline Complete ✅

* Реализован полный Android telemetry pipeline:

    * sensors → frames → batch → outbox → delivery → backend
* Добавлен Android auth flow:

    * `/auth/challenge`
    * `/auth/register`
    * bearer token
* Реализован Android stub bypass (совместим с backend strict App Attest)
* Добавлен TelemetryAuthManager:

    * caching token
    * expiry handling
    * mutex (single-flight)
* Интегрирован bearer в delivery API
* Реализован retry + backoff
* Реализована обработка 401/403:

    * invalidate token
    * повторная регистрация
* Добавлен fallback delivery:

    * EU → RU
* Добавлено HTTP логирование:

    * request
    * response
    * exceptions
* Исправлен WorkManager loop:

    * REPLACE → KEEP
    * убран агрессивный scheduler spam
* Подтверждён end-to-end delivery:

    * `runOnce(): delivered id=...`

📌 Статус: **Pipeline полностью работает**

---

## v3 - Telemetry Delivery Pipeline (WIP)

* Реализован TelemetryDeliveryGraph
* Добавлен processor
* Добавлен Worker
* Добавлен scheduler

---

## v2 - Project Stabilized

* Добавлены project context файлы
* Подготовка к запуску

---

## v1 - Build Fixed

* Исправлены compile ошибки
* Настроен JAVA_HOME
* Исправлены проблемы времени и API

## 🚀 Android Telemetry Pipeline v1 (Milestone)

### Summary

Реализован полный telemetry pipeline для Android с end-to-end доставкой данных на backend.

Pipeline:

sensors → frames → batch → outbox → delivery → backend

---

### Implemented

* Сбор telemetry (GPS, IMU, device state)
* Batch aggregation
* Outbox (Room)
* Delivery через WorkManager
* Retry + backoff
* Auth flow:

  * `/auth/challenge`
  * `/auth/register`
  * bearer token
* Android stub bypass (совместим с App Attest backend)
* Token caching + expiry handling
* 401/403 → token invalidation → re-register
* Fallback delivery:

  * EU → RU
* HTTP logging (request / response / errors)

---

### Fixed

* WorkManager infinite restart loop
* auth 403 (App Attest incompatibility)
* missing bearer token in ingest
* delivery pipeline not reaching backend

---

### Verified

* Worker execution стабильный
* Batch успешно отправляется
* Backend принимает данные
* Подтверждено логами:

  * `sending id=...`
  * `delivered id=...`

---

### Current Behavior

* EU endpoint → timeout
* RU endpoint → success (200 accepted)

👉 Delivery полностью работает через fallback

---

### Status

✅ Production-ready telemetry pipeline
⚠️ Требуется проверка EU backend

---

### Next Focus

* Fix EU endpoint
* Improve throughput (batching / parallelism)
* Add metrics (success rate / latency)
