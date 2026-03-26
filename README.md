# Android Telemetry Project

## Overview
Android приложение для сбора телеметрии с устройства:
- GPS (location)
- IMU (accelerometer, gyroscope)
- Heading / orientation
- Network state
- Device state

Данные агрегируются в батчи и подготавливаются для отправки.

---

## Current Status
✅ Project builds successfully  
✅ Gradle configured  
✅ Lint blocking issues fixed  
⚠️ Runtime behavior not fully verified
⚠️ Telemetry delivery pipeline реализован, но не протестирован на устройстве
⚠️ Возможны проблемы с эмулятором API 36.1 (preview)

Подробнее смотри: `PROJECT_STATUS.md`

---

## Quick Start

### 1. Проверить Java
```powershell
java -version
2. Собрать debug APK
.\gradlew clean assembleDebug
3. Полная проверка
.\gradlew clean build
4. Запустить приложение
Через Android Studio → Run
Или на эмуляторе / устройстве
Project Structure
app/
  ├── sensors/           # источники данных (GPS, IMU и т.д.)
  ├── telemetry/         # доменные модели и pipeline
  │    ├── domain/
  │    ├── ingest/
  │    └── mapper/
  ├── platform/          # Android-specific реализации
  └── ui/ (если есть)

gradle/                  # Gradle wrapper
Key Technical Notes
Time Handling

Используется:

kotlinx.datetime.Instant

❌ НЕ используется java.time (из-за minSdk 24)

Numeric Processing

Используется:

NumericSanitizer

Для:

фильтрации NaN / Infinity
округления значений
API Compatibility
minSdk: 24
избегаются API 26+ без необходимости
заменён java.time.Clock → kotlinx.datetime.Clock
Known Limitations
Telemetry pipeline не проверен полностью на устройстве
Есть не критичные warnings
Нет полной runtime-валидации данных

Подробнее: KNOWN_ISSUES.md

Next Steps
- использовать стабильный эмулятор (API 34/35)
- проверить выполнение TelemetryDeliveryWorker

Смотри: NEXT_STEPS.md

Коротко:

запустить приложение
проверить сбор данных
добавить логирование
протестировать edge cases
Troubleshooting
Gradle не запускается

Проверь:

java -version

Если ошибка:

JAVA_HOME is not set

→ настроить:

$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
Build падает на lint

Проверь:

permissions в AndroidManifest.xml
API level (не использовать java.time)
Useful Commands
# сборка debug
.\gradlew assembleDebug

# полная сборка
.\gradlew build

# очистка
.\gradlew clean
Context Files

Этот проект содержит дополнительные файлы для быстрого входа в контекст:

PROJECT_STATUS.md — текущее состояние
NEXT_STEPS.md — что делать дальше
KNOWN_ISSUES.md — известные проблемы
CHANGELOG.md — история изменений
Goal

Цель проекта:

стабильный сбор телеметрии
корректная обработка данных
подготовка к отправке на backend