# CONTRACT.md

## Purpose

This document defines the cross-platform contract for the Telemetry system.

It is the source of truth for:
- Android implementation
- iOS implementation
- backend-compatible payload shape
- routing and recovery expectations
- parity rules between platforms

This contract exists to prevent Android / iOS drift.

---

## Scope

This contract covers:

1. Session lifecycle
2. Telemetry ingestion model
3. Delivery guarantees
4. Finish / report behavior
5. Routing policy
6. Retry / recovery semantics
7. Platform parity rules

It does **not** define:
- UI behavior
- internal screen/view structure
- platform-specific framework details

---

## System Pipeline

Canonical pipeline:

```text
sensors → frames → batch → outbox → delivery → backend → finish

Platform implementations may differ internally, but externally they must preserve the same lifecycle and backend-visible semantics.

Platform Roles
Android

Android is currently the most validated reference implementation for:

auth flow
ingest flow
finish flow
pending finish recovery
EU-first / RU-fallback routing
priority delivery for sessions with pending finish
iOS

iOS must converge to the same:

backend payload semantics
routing semantics
finish behavior
aggregation outputs
recovery outcomes

If Android and iOS differ, the difference must be explicitly documented and treated as a contract gap.

Core Domain Concepts
Session

A session is the unit that groups telemetry frames, ingest batches, and finish/report lifecycle.

Expected properties:

unique session_id
monotonically ordered telemetry batches within the session
exactly one logical finish intent per session
report/summary bound to the same session identity
Frame

A frame is the smallest logical telemetry sample produced from sensors.

Examples:

GPS
IMU
heading / orientation
network state
device state

Frames are platform-specific internally, but must map to backend-compatible DTOs.

Batch

A batch is an ordered collection of frames prepared for ingestion.

Required guarantees:

belongs to exactly one session
has deterministic order within that session
order must not be broken by retry, fallback, or priority scheduling
Outbox

Outbox is persistent local storage of batches waiting for delivery.

Required properties:

durable across app restarts
retryable
safe for offline recovery
not destructive before confirmed delivery
Pending Finish

Pending finish is a persisted marker that the session has entered finish flow, but backend finish is not yet confirmed.

Required properties:

persisted locally
retried until terminal success or terminal non-retryable failure policy
linked to session identity
recoverable after restart / network restore
Session Lifecycle Contract

Canonical lifecycle:

start → batching → ingest → stop → finish
Required semantics
Start

A session starts when telemetry collection begins for a trip / drive.

Ingest

Telemetry frames are transformed into ordered batches and sent to backend ingest endpoints.

Stop

Stop means client-side trip termination intent is declared.

Stop does not imply backend finish already succeeded.

Finish

Finish is a separate backend-visible action.
It may happen:

immediately after stop
later, after pending recovery
after ingest delivery catches up
Key rule

A session may be locally stopped before finish is backend-confirmed.

This is expected behavior, not an error state.

Delivery Contract
Ingestion Delivery

Delivery layer must:

claim pending batches
send them
mark them delivered on success
retry on retryable failure
preserve per-session order
Ordering

Ordering inside a session is mandatory.

Allowed:

prioritizing one session over another
delaying old backlog

Not allowed:

reordering batch sequence within the same session
delivering later batch before earlier batch of the same session if backend semantics depend on sequence
Priority Delivery

If a session has pending finish, its ingest batches may be prioritized over general backlog.

Required guarantees:

priority must not break in-session ordering
priority exists to accelerate convergence of finish recovery
fairness policy may evolve, but must not violate correctness
Finish Contract
Finish Trigger

Finish is triggered from session stop lifecycle.

Platform-specific trigger source may differ internally, but externally both platforms must produce the same finish semantics.

Finish Preconditions

Finish may depend on backend having enough ingest state for that session.

Therefore:

finish may initially fail
session may enter pending finish state
retry is expected and valid
Finish Success

A finish is considered successful only when backend confirms it.

Client-side stop alone is not finish success.

Pending Finish Recovery

If initial finish attempt fails for retryable/network reasons:

finish intent must be persisted
recovery must resume later
finish retry must happen after network restore and/or after necessary ingest delivery catches up
Immediate Finish Retry

A platform may trigger immediate finish retry after:

first delivered batch for a session with pending finish
network restoration
worker restart / recovery path

This is preferred behavior because it reduces latency to convergence.

Report / Summary Contract

Trip report / summary must belong to the same session and must be backend-compatible.

Covered endpoints currently include:

/trip/finish
/trip/report
/trips/recent
/driver/home

These routes must follow the same routing semantics defined below.

Current state

Aggregation parity is not yet fully complete; this remains one of the explicit cross-platform gaps.

This especially affects:

distance
duration
average speed
driving score
trip summary totals

Until full parity is reached:

backend-visible payload shape must remain stable
platform differences in computed totals must be treated as contract drift and tracked explicitly
Routing Contract
Canonical routing

For ingest and trip API routes:

EU = primary
RU = fallback only
Auth

Auth uses:

EU first
RU fallback on transport/network failure
Ingest

Ingest uses:

EU first
RU fallback on retryable / network failure
Trip API

Trip API routes use:

EU first
RU fallback on retryable / network failure
Not allowed
RU as permanent primary for trip API
platform-specific routing logic that changes backend semantics
inconsistent fallback rules between Android and iOS for the same endpoint class
Retry Contract
Retryable failures

Examples:

timeout
transport/network failure
temporary endpoint instability
retryable HTTP class per platform policy
Non-retryable failures

Must be explicitly classified in code and logged as terminal failures.

Required behavior

On retryable failure:

do not destroy batch or finish intent
persist state
retry later according to platform scheduler / worker policy
Auth Contract

Canonical auth flow:

/auth/challenge
/auth/register
bearer token issuance
token reuse until invalid/expired
invalidate and re-register on 401

Required semantics:

token lifecycle must be consistent
auth must not corrupt delivery ordering
auth refresh must be transparent to ingest / finish layers

Platform internals may differ, but backend-visible auth behavior must remain equivalent.

Persistence Contract

Both platforms must persist enough local state to survive:

app restart
network loss
delayed backend availability

Required persisted concepts:

delivery backlog / outbox
pending finish state
auth state as needed by platform policy
delivery stats / recovery metadata where used

Persistence layer implementation may differ:

Android: Room / stores
iOS: platform-specific persistence

But semantics must match.

Observability Contract

At minimum, both platforms should make it possible to inspect:

session_id
batch sequence
delivery attempt result
route used (EU / RU)
finish attempt result
pending finish state
retry timing / recovery timing

Current observability is still log-heavy rather than metrics-first.
That is acceptable short term, but production target is explicit measurable observability.

Parity Rules
Must be equal across Android and iOS
Session lifecycle semantics
Ingest ordering guarantees
Finish success criteria
Pending finish persistence and retry behavior
Route policy: EU primary, RU fallback
Backend-visible DTO meaning
Terminal vs retryable failure classification
May differ across Android and iOS
Internal class structure
Worker / scheduler implementation
sensor APIs
storage framework
UI/debug tooling
Drift policy

If platform behavior differs in any backend-visible way:

document the difference
classify whether it is temporary or a bug
do not leave it implicit
Contract Gaps Currently Known

The following are known not to be fully closed yet:

Aggregation parity between Android and iOS
Throughput / backlog optimization under heavy queue depth
Formal fairness policy for priority delivery
Full observability / metrics model
Golden tests / replay tests for cross-platform parity
Payload/schema validation hardening

These are not reasons to change the core contract.
They are implementation gaps against the contract.

Change Management

Any change to these areas must update this file:

payload semantics
finish behavior
routing behavior
retry classification
ordering guarantees
report/summary semantics
Recommended commit prefixes
shared(contract): ...
docs(shared): ...
android(...): ...
ios(...): ...
Acceptance Criteria for Cross-Platform Parity

A change is contract-safe when:

Android and iOS produce backend-compatible payloads
both platforms preserve session ordering
both platforms recover pending finish after failure
both platforms use EU primary / RU fallback
recovery scenarios converge to the same backend-visible final state
differences are documented if totals/aggregation still temporarily diverge
Short Version

If one sentence is needed:

Android and iOS may differ internally, but for backend-visible telemetry, delivery, routing, finish, and recovery semantics they must behave as the same system.