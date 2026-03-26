# Changelog

## v1 - Build Fixed
- Исправлены все compile ошибки
- Настроен JAVA_HOME
- Исправлены проблемы с Instant (java.time → kotlinx.datetime)
- Исправлен NumericSanitizer
- Исправлены API level проблемы (Clock)
- Исправлены lint блокирующие ошибки
- Проект успешно собирается

## v2 - Project Stabilized
- Добавлены project context файлы
- Подготовка к запуску и тестированию
## v3 - Telemetry Delivery Pipeline (WIP)

- Реализован TelemetryDeliveryGraph
- Добавлен processor для выполнения delivery цикла
- Добавлен TelemetryDeliveryWorker (CoroutineWorker)
- Добавлен TelemetryDeliveryScheduler
- Подключён запуск scheduler из MainActivity
- Добавлено debug-логирование выполнения pipeline

⚠️ Эмулятор API 36.1 нестабилен (ошибки установки APK)
→ требуется переход на стабильный API (34/35)