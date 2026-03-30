# Changelog

## v6 - Priority Delivery + EU-first Trip Routing 🚀

### 🚀 Major

* Реализован `priority delivery` для session с pending finish:
  * batch'и session с незавершённым `finish` отправляются раньше общего backlog
  * порядок внутри session сохраняется (`batch_seq` не нарушается)

* Добавлен `immediate finish retry`:
  * после первого успешно доставленного batch для session с pending finish
  * после восстановления сети / запуска worker

* Trip API переведён на корректную схему маршрутизации:
  * **EU = primary**
  * **RU = fallback only**

---

### 🌐 Routing

* Auth:
  * EU first
  * RU fallback при transport/network failure

* Ingest:
  * EU first
  * RU fallback при retryable/network failure

* Trip API (`/trip/finish`, `/trip/report`, `/trips/recent`, `/driver/home`):
  * EU first
  * RU fallback при retryable/network failure

---

### ✅ Verified

* подтверждён сценарий:
  * early stop → pending finish → ingest delivered → finish retry → `200 OK`
* подтверждён сценарий:
  * network loss → finish fail → pending → network restore → finish retry → `200 OK`
* `FinishRetryWorker` и delivery worker корректно сходятся к конечному состоянию
* `FallbackTripApi` собран и подключён

---

### 🧠 Fixes

* ❌ `RU` как primary для trip API → исправлено на `EU first, RU fallback`
* ❌ медленное схождение finish при backlog → ускорено через priority delivery
* ❌ finish retry зависел только от периодического worker → добавлен immediate retry trigger

---

### 📌 Status

✅ Correctness подтверждён  
✅ Recovery подтверждён  
✅ EU-first / RU-fallback routing выровнен для ingest и trip API  
🚀 Система перешла от "просто работает" к "быстро сходится"

---

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
