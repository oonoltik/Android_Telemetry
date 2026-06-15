# Next Steps

Last Updated: 2026-06-10

---

# Project Goal

Target:

```text
Android = Swift
```

Not only feature parity.

Also:

* behavioral parity
* lifecycle parity
* UX parity
* visual parity
* analytics parity

Swift remains the source of truth.

---

# Current Status

Estimated parity:

```text
95–97%
```

Core telemetry is complete.

Dashcam is now stable in the latest focused tests.

Crash export is complete.

Crash upload is complete in normal network conditions.

Driver camera recording with DMS is now stable after CameraX rebind fix.

SaveFish is complete.

Most remaining work is validation, Swift parity audit, localization audit, and release polish.

---


# Priority 0

# DriveTelemetry Guide QA

The new guide screen should be validated before public release because it is a first-contact product explanation surface.

---

## Goal

Ensure the onboarding guide explains the product clearly and renders correctly on real devices in both orientations.

---

## Validation Matrix

### Test 1 — Portrait Guide Flow

Device:

```text
Samsung A55
```

Expected:

* all 7 slides render correctly
* text is readable
* no bottom labels are clipped
* swipe navigation works
* Back / Next / Close work
* slide 7 rating block does not overlap title/subtitle or bottom icons

---

### Test 2 — Landscape Guide Flow

Device:

```text
Samsung A55
```

Expected:

* landscape backgrounds render correctly
* slide 2, 3, 5, and 6 feature blocks fit in one row
* slide 7 rating block is in the upper-right visual quadrant
* "out of 100" label and stars are visible

---

### Test 3 — Orientation Switching

Scenario:

```text
Open guide
↓
Go to slide 7
↓
Rotate device several times
↓
Swipe back to slide 6
↓
Return to slide 7
```

Expected:

* guide does not crash
* current slide state remains consistent
* rating demo resets after leaving slide 7
* layout recalculates correctly

---

### Test 4 — Localization

Check:

```text
Russian
English
```

Expected:

* guide text follows selected app language
* no hardcoded Russian/English strings remain in guide screen
* text fits in both languages

---

## Success Criteria

The guide is release-ready when:

* portrait and landscape are visually acceptable
* all text is localized
* no clipping occurs on target devices
* navigation is intuitive
* final CTA returns the user to the intended flow

---


# Priority 1

# Production Dashcam Validation

This remains the highest priority task.

---

## Goal

Validate dashcam under production-scale use, not only short controlled tests.

Current production segment duration:

```text
segmentDurationMs = 120_000L
```

---

## Validation Matrix

### Test 1 — Road Camera Long Session

Duration:

```text
30+ minutes
```

Expected:

* stable segment rotation
* no archive corruption
* no memory leak
* no stuck UI timer
* no unexpected restart

---

### Test 2 — Driver Camera Long Session

Duration:

```text
30+ minutes
```

Mode:

```text
Driver camera + DMS ON
```

Expected:

* stable DMS values
* voice alerts work in selected language
* segment rotation remains stable
* no `ERROR_NO_VALID_DATA`
* no stuck UI timer

---

### Test 3 — Crash Early In Recording

Crash at:

```text
1–5 seconds
```

Expected:

```text
available pre-crash
+
10 sec post-crash
```

Archive:

* emergency clip appears
* ordinary clip behavior is understandable

---

### Test 4 — Normal Crash

Crash at:

```text
10–60 seconds
```

Expected:

```text
10 sec before
+
10 sec after
```

Verify:

* exact export duration
* archive display
* upload completion
* server database entry

---

### Test 5 — Segment Boundary Crash

Crash near:

```text
119 sec
121 sec
```

Expected:

* exact export uses correct source windows
* no missing post-crash window
* audio preserved
* upload preserved
* archive shows emergency clip

---

### Test 6 — Stop Immediately After Crash

Scenario:

```text
Crash
↓
User presses Stop
↓
Open Archive
↓
Return Home
```

Expected:

* timer disappears immediately after Stop
* timer does not reappear
* crash package still forms
* emergency clip appears

This passed in current short testing and should be repeated in long-session tests.

---

## Success Criteria

All tests pass with:

```text
exact crash export
audio
archive
upload
server
UI state
```

working correctly.

---

# Priority 2

# Upload Pipeline Hardening

---

## Verify

Crash uploads under:

* airplane mode
* weak network
* Wi-Fi to mobile transition
* process restart
* app restart
* device reboot

---

## Verify

Upload stages:

* `/video/session/start`
* `/video/crash-log`
* `/video/crash-clip`
* `/crash-clips/upload/init`
* `/crash-clips/upload/chunk`
* `/crash-clips/upload/complete`

---

## Success Criteria

No upload loss.

Expected behavior:

```text
failed or interrupted upload
↓
persistent queue
↓
retry
↓
uploaded=true
```

---

# Priority 3

# Full Swift Parity Audit

After Dashcam validation.

---

## Home Screen

Compare Android vs Swift:

* layout
* spacing
* typography
* button sizes
* warning banners
* score block
* trip controls
* dashcam controls

---

## Trips Archive

Verify:

* card structure
* spacing
* typography
* navigation

---

## Trip Report

Verify:

* field ordering
* labels
* calculations
* visual presentation

---

## Video Archive

Verify:

* grouping
* archive ordering
* emergency cards
* selection mode
* delete workflow

---

## Video Player

Verify:

* controls
* metadata
* emergency presentation

---

## Settings

Verify:

* structure
* localization
* navigation

---

## Driver Account

Verify:

* editing
* display
* persistence
* validation

---

## SaveFish

Verify:

* layout
* animation
* behavior
* analytics

---

## Deliverable

Produce:

```text
Android vs Swift Difference Report
```

with every remaining difference documented.

---

# Priority 4

# Localization Audit

---

## Goal

Remove remaining hardcoded user-visible strings.

---

## Audit Areas

### Home

Check:

* warnings
* dialogs
* toasts
* crash alerts

---

### Dashcam

Check:

* errors
* notifications
* archive actions
* camera state messages

---

### Driver Monitoring

Check:

* TTS messages
* warning texts
* critical alerts
* recovery messages

Expected:

All DMS messages must follow selected language.

---

### Video Archive

Check:

* selection mode
* delete actions
* upload states
* emergency labels

---

### SaveFish

Check:

* game messages
* results
* warnings

---

### Driver Account

Check:

* labels
* actions
* validation

---

### Notifications

Check:

* foreground service
* upload notifications
* warning notifications

---

## Success Criteria

All user-visible strings come from:

```text
strings.xml
```

for:

* RU
* EN

---

# Priority 5

# Cleanup

After parity work is finished.

---

## Remove Or Reduce

Temporary diagnostics where no longer required:

* CrashClipSaved
* CrashClipExactExport
* VideoArchiveDelete
* DashcamRotation
* DriverMonitoring verbose calibration logs

Keep only logs useful for production diagnostics.

---

## Refactor

Potential targets:

* DashcamRecordingController
* DashcamCrashCoordinator
* CrashClipRepository
* Crash upload scheduling

---

## Review

* naming
* architecture
* dead code
* duplicate lifecycle guards

---

# Priority 6

# Documentation

Update after every major milestone:

* README.md
* PROJECT_STATUS.md
* NEXT_STEPS.md
* KNOWN_ISSUES.md
* CHANGELOG.md

---

# Roadmap to 100% Swift Parity

## Phase A

Production Dashcam Validation

Status:

```text
In Progress
```

---

## Phase B

Upload Hardening

Status:

```text
Pending
```

---

## Phase C

Full Swift Audit

Status:

```text
Pending
```

---

## Phase D

Localization Audit

Status:

```text
Pending
```

---

## Phase E

Final Polish

Status:

```text
Pending
```

---

## Phase F

Release Candidate

Status:

```text
Pending
```

Requirements:

* all parity gaps documented
* all critical bugs resolved
* localization complete
* crash export validated
* upload pipeline validated
* long recording stable

---

# Final Target

```text
Android = Swift
```

with equivalent:

* UI
* behavior
* lifecycle
* telemetry
* crash handling
* video handling
* driver monitoring
* analytics
* backend contracts
