# Project Status

## Current

✅ Android telemetry pipeline — production-ready  
✅ Delivery pipeline — end-to-end verified  
✅ Finish flow — соответствует контракту (pending + retry)  
✅ Identity layer — реализован (prepare/register/login/delete)  
✅ Monorepo (Android + iOS) — завершён  

---

## State

### Telemetry
- ingest → batching → outbox → delivery → backend ✔
- batch_seq — строго монотонный ✔
- no duplicates ✔

### Finish lifecycle
- finish НЕ отправляется до первого delivered batch ✔
- pending finish storage ✔
- retry после первого delivery ✔
- UI states:
  - queued
  - in progress
  - finished ✔

### Identity
- driver_id lifecycle ✔
- account binding ✔
- delete flow ✔

---

## Known Stability

- recovery после restart ✔
- delivery retry ✔
- finish retry ✔

---

## Next

👉 aggregation parity (Android ↔ iOS)  
👉 shared DTO contracts  
👉 throughput / backlog optimization