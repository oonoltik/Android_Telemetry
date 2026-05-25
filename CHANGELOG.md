# Changelog

## v10

### Added

- Android WaterGlass / SaveFish game Swift parity
- Real fish image assets support:
  - `fish_low`
  - `fish_mid`
  - `fish_high`
- Fish direction changes at glass walls
- Game-over fish state using rotated `fish_high`
- Water spill/refill mechanics
- Accelerometer and gyroscope integration for game physics
- Splash particles inside and outside glass
- `/glass_game_ingest` Android upload path
- Swift-parity glass game telemetry JSON
- `GlassGameBatchDto`
- `GlassGameApi`

### Changed

- WaterGlass UI proportions refined for Swift-style layout
- Header typography reduced to fit taller glass
- Glass width/height adjusted for more Swift-like vertical shape
- Lower instruction text refined:
  - “Осторожно передвигайте стакан — вода реагирует на движение и разливается! Рыбка может погибнуть.”
- Android glass telemetry timestamps aligned with Swift `Z` format without milliseconds
- Android `analytics` aligned to Swift fields:
  - `background_count`
  - `active_play_s`

### Fixed

- Game JSON now includes:
  - `game_started_at`
  - `game_ended_at`
  - `game_duration_sec`
  - `window_duration_sec`
- `aborted=false` on completed spill/game-over
- `max_spill_level` clamped to 100%
- Lower instruction text visibility on screen
- Fish position constrained to water zone
- Splash clipping so particles can render outside the glass
- API 24-safe timestamp formatting

## v9

### Added

- Day Monitoring
- Sensor context batching

### Fixed

- Service lifecycle issues

### Notes

- System close to iOS parity
