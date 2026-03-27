# Known Issues

## Current

### 1. EU Endpoint Timeout

* `https://api.drivetelemetry.com` не отвечает
* ошибка:

    * `SocketTimeoutException`
* система использует fallback (RU)

👉 Не блокирует delivery

---

### 2. Высокий backlog

* ~2000+ записей в очереди
* обрабатывается батчами по 20

👉 Нужна оптимизация throughput

---

## Minor

* Есть lint warnings (не критично)
* Нет метрик (success rate / latency)
* Нет runtime validation payload

---

## Resolved

* ❌ WorkManager infinite loop
* ❌ auth 403 (App Attest bypass)
* ❌ token handling
* ❌ delivery не работал

---

## Notes

📌 Все критические проблемы решены
📌 Остались только оптимизации и инфраструктура
