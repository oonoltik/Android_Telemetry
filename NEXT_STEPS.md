# Next Steps

## 🔴 High Priority

### 1. Aggregation parity (Android ↔ iOS)
- MotionVectorComputer 1:1 с iOS
- одинаковые события (accel/brake/turn)
- golden tests

---

### 2. Shared contracts
- TelemetryBatch DTO синхронизация
- TripFinishRequest полный контракт
- единый naming (speed_m_s, a_long_g и т.д.)

---

### 3. Finish payload completion
- trip_core
- trip_summary
- trip_metrics_raw
- device_meta
- client_metrics

---

### 4. Sensor completeness
- activity recognition
- pedometer
- altimeter
- screen interaction

---

## 🟡 Medium

### Observability
- queue depth
- delivery latency
- finish retry metrics

### Day monitoring
- auto start/stop trips
- activity-based gating

---

## 🟢 Low

- debug UI → production UI (Compose navigation)