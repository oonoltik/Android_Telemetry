# Known Issues

## Android

- IMU отсутствует в эмуляторе → нет реальных событий (accel/turn)
- backlog может расти при плохой сети
- finish может висеть в queued до первого delivery (ожидаемое поведение)

---

## iOS

- возможен drift в MotionVectorComputer
- aggregation не полностью синхронизирован

---

## Shared

- DTO пока не полностью унифицированы
- нет contract tests между платформами

👉 главный риск: Android/iOS divergence на уровне метрик