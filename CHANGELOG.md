# Changelog

## v8 - Delivery + Identity + Finish parity

### Added
- driver API:
  - prepare
  - register
  - login
  - delete
- AccountDeleteManager
- finish retry pipeline
- pending finish storage
- debug UI (identity + finish states)

---

### Changed
- finish flow переработан:
  - stop → drain → pending → retry
- delivery pipeline стабилизирован
- batch_seq централизован

---

### Fixed
- duplicate batch_seq
- race condition при flush
- finish до первого delivery

---

### Notes
- Android теперь соответствует core telemetry contract
- готово к iOS parity этапу