# Next Steps

## 🔴 High Priority

### 1. Throughput optimization after correctness

* уменьшить backlog
* оптимизировать claim/send loop
* при необходимости повысить batch size
* добавить fairness policy для priority delivery

👉 цель: быстрее схождение после stop/offline

---

### 2. Aggregation Parity (iOS)

* довести расчёты:
  * distance
  * duration
  * avg speed
  * driving score
  * trip summary
* убрать fallback/simplified summary

👉 это главный remaining parity gap

---

### 3. Metrics / Observability

* finish recovery latency
* success rate by route (EU / RU)
* retry rate
* queue depth
* per-session delivery timing

👉 нужна измеримая production-картина

---

## 🟡 Medium Priority

### 4. Fairness for priority delivery

* не допускать starvation старого backlog
* варианты:
  * N priority batch → 1 normal batch
  * weighted scheduling
  * per-session quota

---

### 5. Golden tests / replay tests

* одинаковый synthetic trip
* сравнение:
  * iOS
  * Android
  * backend totals

👉 нужен контроль drift'а после следующих изменений

---

### 6. Payload validation

* schema checks
* size limits
* defensive validation перед отправкой

---

## 🟢 Low Priority

### 7. Parallel delivery tuning

* аккуратно увеличить throughput без нарушения порядка внутри session

---

### 8. Diagnostics UX

* показать в debug UI:
  * pending finish count
  * outbox depth
  * last delivery route
  * finish retry state

---

## Goal

📌 Быстрое и предсказуемое схождение после stop/offline  
📌 Полный iOS parity по aggregation  
📌 Production-grade observability
