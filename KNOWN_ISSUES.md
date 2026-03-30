# Known Issues

## Current

### 1. EU Endpoint Timeout

* `https://api.drivetelemetry.com` не отвечает
* SocketTimeoutException
* используется RU fallback

👉 Не блокирует ingest или finish

---

### 2. Высокий backlog

* ~2000+ записей
* batch size = 20

👉 требует оптимизации

---

## Functional Gaps

### 3. Нет persistent finish recovery

* если приложение убито:
  * pending finish может потеряться

👉 требуется iOS-like recovery

---

### 4. Aggregation упрощён (fallback summary)

* при отсутствии metrics используется stub summary

👉 требуется полноценная агрегация как в iOS

---

## Minor

* нет метрик (success rate / latency)
* нет payload validation

---

## Resolved

* ❌ auth 403 (App Attest)
* ❌ token lifecycle
* ❌ ingest не работал
* ❌ finish падал (serialization Any)
* ❌ lifecycle не вызывал finish

---

## Notes

📌 Все критические блокеры устранены  
📌 Остались только улучшения reliability и качества данных