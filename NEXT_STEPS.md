# Next Steps

## Immediate (High Priority)

### 1. Fix EU endpoint

* проверить backend
* проверить latency
* проверить routing / firewall

---

### 2. Ускорить delivery

* увеличить batch size
* добавить несколько runOnce() за worker
* уменьшить backlog

---

## Short Term

### 3. Добавить метрики

* success rate
* retry rate
* latency

---

### 4. Улучшить retry

* adaptive backoff
* smarter retry policy

---

## Mid Term

### 5. Параллельная отправка

* несколько batch одновременно
* ограничение concurrency

---

### 6. Payload validation

* schema check
* size limits

---

## Long Term

### 7. Align с iOS pipeline

* унификация логики
* shared контракт

---

## Optional

* отключить EU временно
* использовать только RU endpoint

---

## Goal

📌 Production-ready telemetry ingestion
📌 Высокая надёжность доставки
📌 Минимальная latency
