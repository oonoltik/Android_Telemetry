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
