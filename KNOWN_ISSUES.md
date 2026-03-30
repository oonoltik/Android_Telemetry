# Known Issues

## Current

### 1. EU Endpoint instability / timeout

* `https://api.drivetelemetry.com` периодически отвечает timeout / transport error
* клиент корректно уходит в RU fallback
* критичный flow не блокируется, но latency recovery ухудшается

👉 correctness не ломает, но влияет на скорость схождения

---

### 2. Высокий backlog outbox

* в очереди может накапливаться большой хвост batch'ей
* даже с priority delivery backlog всё ещё влияет на общее время дренажа
* batch size = 20

👉 требует дальнейшей throughput-оптимизации

---

### 3. Aggregation всё ещё упрощён

* используется fallback / simplified summary
* нет полной parity с iOS aggregation pipeline

👉 нужен отдельный этап выравнивания расчётов

---

## Functional Gaps

### 4. Нет полной metrics/observability модели

* нет стабильных метрик:
  * success rate
  * retry rate
  * finish recovery latency
  * per-route delivery stats dashboard

👉 для production нужна измеримость, а не только логи

---

### 5. Нет явной starvation policy для priority delivery

* session с pending finish теперь имеет приоритет
* но отдельная политика fairness для старого backlog ещё не формализована

👉 желательно добавить guard против starvation

---

## Minor

* нет payload/schema validation
* нет golden tests на Android ↔ iOS aggregation parity
* `client_ended_at` / report serialization стоит дополнительно перепроверить на backend round-trip

---

## Resolved

* ✅ auth 403 / App Attest mismatch
* ✅ token lifecycle
* ✅ ingest pipeline
* ✅ finish serialization crash (`Any`)
* ✅ lifecycle-triggered finish
* ✅ persistent pending finish recovery
* ✅ immediate finish retry
* ✅ EU-first / RU-fallback для trip API

---

## Notes

📌 Все критические блокеры correctness устранены  
📌 Остались reliability / latency / data quality improvements
