# Next Steps

## 🔴 High Priority (Critical)

### 1. iOS-like Finish Recovery

* сохранять pending finish
* retry после рестарта
* WorkManager для finish delivery

👉 критично для production

---

### 2. Aggregation Parity (iOS)

* считать:

    * distance
    * duration
    * avg speed
    * driving score

* убрать fallback summary

---

## 🟡 Medium Priority

### 3. Throughput optimization

* увеличить batch size
* уменьшить backlog
* оптимизировать worker

---

### 4. Metrics

* success rate
* retry rate
* latency

---

## 🟢 Low Priority

### 5. Parallel delivery

* несколько batch одновременно

---

### 6. Payload validation

* schema
* size limits

---

## Optional

* временно отключить EU endpoint

---

## Goal

📌 Надёжный delivery (без потерь)  
📌 Полный parity с iOS  
📌 Production readiness