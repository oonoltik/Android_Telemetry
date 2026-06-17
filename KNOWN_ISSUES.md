# Known Issues

Last Updated: 2026-06-10

---

# Overview

This document contains active issues, validation targets, and known watch points.

Resolved issues should be moved to CHANGELOG.md.

Items listed here are not necessarily bugs.

Many entries are validation tasks required before declaring full Android ↔ Swift parity.

---

# Current Project Status

Current parity estimate:

```text
95–97%
```

Major subsystems implemented and recently validated:

* Telemetry
* Driver Scoring
* Trip Reporting
* Day Monitoring
* Dashcam
* Crash Detection
* Exact Crash Export
* Crash Upload
* Driver Monitoring
* SaveFish
* Localization

---

# Recently Resolved P1 Items

The following issues are considered resolved after June 10, 2026 testing:

## Driver Camera Segment Rotation

Resolved.

Driver camera now rotates segments with DMS enabled.

Verified with:

```text
Preview + VideoCapture + ImageAnalysis
```

and:

```text
segmentDurationMs = 10_000L
segmentDurationMs = 120_000L
```

---

## Crash Stop UI State

Resolved.

After Stop during post-crash capture:

* timer disappears immediately
* timer does not reappear when returning from archive
* emergency clip still exports correctly

---

## Crash Detection Outside Active Session

Resolved.

Crash detection no longer runs when both telemetry and video recording are inactive.

---

## DMS Lifecycle After Stop

Resolved.

DMS now stops after video Stop and does not keep speaking or freezing stale values after Stop.

---

## Spontaneous Recording Start

Resolved in current testing.

Leaving the app open without pressing Start:

* does not start video
* does not start timer
* does not trigger crash detection

Continue watching during longer QA sessions.

---


# Active UX Watch Points

## DriveTelemetry Guide Screen

Status:

Implemented.

Requires final physical-device validation.

Validate:

* portrait layout on Samsung A55
* landscape layout on Samsung A55
* portrait layout on smaller devices such as Samsung A03
* landscape layout on smaller devices
* swipe navigation
* Close action
* Back / Next buttons
* final CTA behavior
* language switch RU ↔ EN
* text clipping in both orientations
* bottom navigation overlap
* status bar overlap
* rating circle placement on slide 7
* star row placement on slide 7
* feature-card wrapping on slides 2, 3, 5, and 6

Expected:

* no text is clipped
* no important UI overlaps system bars
* landscape mode keeps feature cards in a single row where required
* portrait mode keeps feature cards readable
* final rating block remains visually centered in its intended area
* all guide strings follow selected language

---


# Active High Priority Watch Points

## Long Recording Sessions

Status:

Requires extended validation.

Validate:

```text
30+ minute recording
1+ hour recording
```

Expected:

* stable memory usage
* stable segment rotation
* stable archive behavior
* no UI state desynchronization
* no segment loss

---

## 120-Second Segment Production Validation

Status:

Mostly validated on short production-like tests.

Current production target:

```text
segmentDurationMs = 120_000L
```

Still validate:

* 30+ minute Road camera recording
* 30+ minute Driver camera recording
* crash near 119–121 second segment boundary
* crash during Driver camera recording with DMS active
* crash immediately after segment rotation

Expected:

* exact export remains correct
* audio preserved
* upload preserved
* archive shows correct emergency clip

---

## CameraX Finalize Warnings

Known watch point.

During final Stop after crash, CameraX may still emit:

```text
ERROR_SOURCE_INACTIVE
```

Current assessment:

Not fatal if:

* MP4 file exists
* file size is non-zero
* crash package is created
* exact export completes
* archive shows emergency clip

Escalate only if it causes:

* zero-byte files
* missing crash clip
* stuck timer
* repeated auto-start
* upload failure

---

# Crash Upload Pipeline

Status:

Implemented and working in normal network conditions.

---

## Validation Required

Network loss during:

* exact export
* upload/init
* chunk upload
* upload/complete
* archive refresh

Expected:

* retry works
* upload resumes
* crash clip not lost

---

## Reboot Scenarios

Validate:

* device reboot during upload
* application restart during upload
* process death during exact export
* process death after crash package but before upload

Expected:

* crash package survives
* exact clip survives or regenerates
* upload eventually completes

---

# Video Archive

Status:

Implemented and working.

---

## Validation Required

Verify under larger archive sizes:

* archive ordering
* emergency grouping
* selection mode
* delete selected
* select all / deselect all
* player open/close
* performance with many items

---

## Large Archive Test

Validate:

```text
100+ videos
```

Expected:

* acceptable performance
* stable scrolling
* stable filtering
* no stale deleted entries

---

# Localization

Status:

Mostly complete.

Voice localization for DMS was corrected during June 10 testing.

---

## Remaining Audit

Check:

* warning dialogs
* edge-case toasts
* upload failures
* archive failures
* runtime exceptions
* foreground-service notifications
* upload notifications
* crash notifications

for remaining hardcoded strings.

---

# Swift Parity Audit

Status:

Not completed.

---

## Screens To Audit

### Home Screen

Verify:

* spacing
* typography
* paddings
* warning banners
* score block
* dashcam block

### Trip Report

Verify:

* field ordering
* score presentation
* spacing
* localization

### Video Archive

Verify:

* card presentation
* emergency presentation
* selection mode
* delete workflow

### Video Player

Verify:

* controls
* metadata
* emergency information

### Settings

Verify:

* navigation
* layout
* localization

### Driver Account

Verify:

* workflow
* persistence
* validation

### SaveFish

Verify:

* animations
* layout
* analytics

against latest Swift implementation.

---

# Android Platform Risks

## Samsung Battery Policies

Samsung may:

* suspend background work
* delay uploads
* delay workers

Must be tested on physical devices.

---

## Android OEM Variations

Potential differences:

* camera lifecycle
* permissions
* notifications
* foreground-service policy

Testing should include:

* Samsung A55
* Samsung A03 if still supported
* Pixel
* Android emulator

where possible.

---

# Technical Debt

Current debt level:

Low to moderate.

---

## Candidates For Future Cleanup

Only after parity stabilization:

* DashcamRecordingController
* DashcamCrashCoordinator
* CrashClipRepository
* Upload scheduling diagnostics
* temporary debug logs

---

# Not Considered Issues

The following are intentionally implemented behaviors:

## Camera Switch Restriction

Switching camera while recording:

```text
Not Supported
```

Current behavior is intentional.

---

## Exact Export Fallback

If exact export fails:

```text
Fallback crash package remains available
```

This is intentional.

---

## Crash Upload Retry

Delayed upload after network recovery:

```text
Expected behavior
```

Not a bug.

---

# Exit Criteria

KNOWN_ISSUES.md should approach empty status when:

* long-session dashcam validation passes
* 120-second segment boundary tests pass
* upload pipeline hardening passes
* Swift parity audit completes
* localization audit completes

At that point Android may be considered feature-complete relative to Swift.
