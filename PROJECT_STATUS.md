# Project Status

## Current

### Telemetry Core

✅ Android telemetry pipeline — production-ready  
✅ Delivery pipeline — end-to-end verified  
✅ Outbox → delivery flow verified  
✅ Finish flow — соответствует контракту: pending + retry  
✅ Identity layer — реализован  
✅ Day Monitoring — реализован  
✅ Sensor context batching — частично реализован  
✅ Service layer — стабилизирован  

### UI / Swift Parity

✅ Home screen uses real backend telemetry data  
✅ Trips archive rewritten to Swift-style compact layout  
✅ Trip report screen close to Swift parity  
✅ Driver comparison/details blocks added  
✅ Orientation/state reset bug fixed via `rememberSaveable`  
✅ Buttons, cards, typography, spacing, and rounded corners refined  

### WaterGlass / SaveFish Game

✅ Android WaterGlass game implemented in Compose  
✅ Uses Swift fish assets: `fish_low`, `fish_mid`, `fish_high`  
✅ Fish remains inside water zone  
✅ Fish direction changes near glass walls  
✅ Game-over state implemented  
✅ Splash particles added inside/outside glass  
✅ Accelerometer/gyroscope integration added  
✅ Swift-style payload uploaded to `/glass_game_ingest`  

### Backend Contract Alignment

✅ Glass game JSON now includes Swift-parity fields:

- `device_id`
- `driver_id`
- `session_id`
- `game_id`
- `window_opened_at`
- `game_started_at`
- `game_ended_at`
- `window_closed_at`
- `max_spill_level`
- `total_refilled_01`
- `game_duration_sec`
- `window_duration_sec`
- `background_events`
- `analytics.background_count`
- `analytics.active_play_s`
- `aborted`

## Remaining Work

### High Priority

👉 Dashcam / Video Mode parity with Swift  
👉 CameraX recording pipeline  
👉 Video archive screen  
👉 Video session backend contract  
👉 Full RU/EN localization  

### Medium Priority

👉 MotionVectorComputer parity Android ↔ iOS  
👉 Telemetry DTO / contract alignment  
👉 Observability and delivery diagnostics  
👉 Day Monitoring tuning  

### Low Priority

👉 Cleanup/refactor after parity  
👉 Design system extraction  
👉 Hardcoded string cleanup  
