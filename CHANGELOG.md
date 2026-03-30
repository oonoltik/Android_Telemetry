# Changelog

## v5 - Full Android ↔ iOS Parity Achieved 🎯

### 🚀 Major

* Достигнут полный end-to-end flow:

  sensors → frames → batch → outbox → delivery → backend → finish

* Реализован корректный lifecycle finish:

  * `onStop()` → `stopTrip()` → `finish`

* Подтверждён успешный `/trip/finish`:

  * ingest → session → finish → `200 OK`

---

### 🔐 Auth

* Полный Android auth flow:

  * `/auth/challenge`
  * `/auth/register`
  * bearer token lifecycle
* Token caching + expiry handling
* 401 → invalidate → re-register
* Mutex (single-flight)

---

### 📦 Delivery

* WorkManager delivery pipeline:

  * batching → Room → worker → backend
* Retry + backoff
* EU → RU fallback
* Logging (request/response/errors)

---

### 🧠 Fixes

* ❌ `Map<String, Any>` → заменено на DTO (`TripSummaryPayloadDto`)
* ❌ Serialization crash (`Any`) → полностью устранён
* ❌ Finish не отправлялся → исправлено (payload всегда формируется)
* ❌ Lifecycle mismatch → приведён к iOS (onStop trigger)

---

### ✅ Verified

* ingest: `200 OK`
* finish: `200 OK`
* fallback работает
* retry работает
* полный цикл подтверждён логами

---

### 📌 Status

✅ Android pipeline полностью совместим с iOS backend flow  
🚀 Production-ready baseline

---

## v4 - Android Telemetry Pipeline Complete ✅
(оставь как есть ниже)