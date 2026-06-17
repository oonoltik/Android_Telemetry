# Changelog

This document tracks major milestones of the Android_Telemetry project.

The primary objective of the project is Android ↔ Swift parity.

Swift remains the source of truth.

---

# v18 (2026-06-15)

## DriveTelemetry Guide / Onboarding Experience

### Added

Implemented an in-app DriveTelemetry guide screen designed as a product-value walkthrough rather than a traditional help page.

Purpose:

```text
explain application value
+
increase first-session engagement
+
show core functionality in a simple game-like format
```

The guide is opened from the Home screen through the DriveTelemetry capabilities entry point.

---

## Guide Screen UX

### Added

Implemented a 7-slide guide flow:

```text
1. Ordinary trip
2. Driving safety
3. Fatigue monitoring
4. Accident protection
5. Built-in dashcam
6. Family protection
7. Personal driver rating
```

Navigation supports:

* swipe left/right
* Back button
* Next button
* Close action
* final call-to-action

---

## Adaptive Layout

### Added

The guide now supports both:

```text
portrait
+
landscape
```

modes.

The implementation uses separate clean background images and renders all user-visible text and UI elements through Compose.

Current approach:

```text
background image without text/icons
+
Compose overlay
+
localized strings.xml
```

This avoids maintaining separate Russian/English image sets.

---

## Localization

### Added

Guide text was moved to Android string resources.

Supported guide localization:

* Russian
* English

Localized elements include:

* navigation buttons
* slide titles
* slide subtitles
* feature labels
* fatigue labels
* crash labels
* dashcam labels
* family labels
* rating labels

---

## Rating Demo Block

### Added

The final guide slide includes a Compose-rendered driver rating block.

Features:

* rating circle
* numeric score
* "out of 100" label
* star row
* final CTA
* adaptive portrait/landscape placement

The rating demo resets when the user leaves the final guide slide.

---

## Technical Direction

The guide screen now follows the preferred scalable architecture:

```text
content = Compose
assets = background only
localization = strings.xml
orientation = adaptive Compose layout
```

This is the correct long-term model for App Store / Google Play localization and future marketing updates.

---


# v17 (2026-06-10)

## Dashcam Recording Lifecycle Stabilization

### Fixed

Stabilized the Android dashcam recording lifecycle after a series of physical-device tests.

Resolved:

* spontaneous recording state after returning from Video Archive
* timer reappearing after user pressed Stop during post-crash capture
* crash detection firing when neither video recording nor telemetry trip was active
* Driver Monitoring continuing after Stop
* Driver camera segment rotation failing after the first segment
* CameraX front/driver camera rotation producing `ERROR_NO_VALID_DATA`
* archive confusion caused by delayed crash package assembly

---

## Driver Camera Segment Rotation

### Fixed

Driver camera now rotates rolling MP4 segments correctly.

Problem:

```text
Driver camera
↓
clip_001 created
↓
clip_002 start attempted
↓
CameraX stream became inactive
↓
ERROR_SOURCE_INACTIVE / ERROR_NO_VALID_DATA
```

Implemented driver-camera-only CameraX rebind before starting the next segment:

```text
Finalize current driver segment
↓
short delay
↓
rebind CameraX use cases
↓
short delay
↓
start next segment
```

Road camera rotation remains unchanged.

### Verified

With Driver camera + DMS + Preview + VideoCapture + ImageAnalysis:

```text
clip_001
clip_002
clip_003
clip_004
```

were created successfully during 10-second segment testing.

With production segment duration:

```text
segmentDurationMs = 120_000L
```

a 5+ minute Driver camera session produced:

```text
clip_001 ≈ 120 sec
clip_002 ≈ 120 sec
clip_003 ≈ remaining duration
```

without fatal recording loss.

---

## Crash Stop UX

### Fixed

When the user presses Stop after a crash, UI now stops immediately even if the recorder internally continues for the remaining post-crash window.

Previous behavior:

```text
Crash
↓
Stop
↓
Open archive
↓
Back to Home
↓
recording timer appeared again
```

New behavior:

```text
Crash
↓
Stop
↓
UI timer disappears immediately
↓
recorder finishes post-crash capture internally
↓
crash package is created
↓
exact export runs
↓
archive shows emergency clip
```

### Verified

After Stop:

* timer disappears immediately
* timer does not reappear when returning from archive
* emergency clip appears in archive
* exact crash export completes successfully

---

## Crash Detection Gating

### Fixed

Crash detection is now active only when:

```text
telemetry trip is active
OR
video recording is active
```

Crash detection is no longer active when:

```text
telemetry OFF
video OFF
```

### Verified

Leaving the app open without recording or telemetry:

* does not start recording
* does not trigger crash alert
* does not run crash telemetry

---

## Driver Monitoring / DMS

### Fixed

DMS lifecycle was stabilized.

Validated behavior:

```text
Road + preview      → DMS OFF
Road + recording    → DMS OFF
Driver + preview    → DMS OFF
Driver + recording  → DMS ON
After Stop          → DMS OFF
```

Voice localization was also corrected so English settings use English TTS strings instead of Russian or mixed-language phonetics.

---

## Emergency Clip Flow

### Verified

Crash pipeline now works end-to-end:

```text
CrashTelemetry queued
↓
CrashClipPackage created
↓
CrashClipExactExport EXPORTING
↓
CrashClipExactExport completed
↓
CrashClipUpload queued
↓
upload completed
```

The previously planned text:

```text
"Аварийная запись сохраняется..."
```

was removed from UI because the exact export is now fast enough and the text created poor UX.

---

## Archive Validation

### Verified

Regular segment archive works correctly.

With:

```text
segmentDurationMs = 10_000L
```

a 40-second Road camera test created and displayed:

```text
clip_001
clip_002
clip_003
clip_004
```

With:

```text
segmentDurationMs = 120_000L
```

a 40-second recording correctly creates one ordinary archive entry.

---

# v16 (2026-06-01)

## Exact Crash Export

### Added

Implemented exact crash clip generation using:

```text
Media3 Transformer
```

New components:

* CrashClipExactExporter
* CrashClipExactExportWorker
* CrashClipExactExportScheduler

---

### Workflow

Implemented:

```text
Crash detected
↓
Crash package assembly
↓
Exact export scheduling
↓
Worker execution
↓
Exact clip generation
↓
Archive refresh
↓
Upload pipeline
↓
Server
```

---

### Exact Export Features

Supports:

* early crashes
* normal crashes
* segment-boundary crashes

Examples:

```text
Crash at 5 sec

Result:

5 sec before
+
10 sec after
```

```text
Crash at 25 sec

Result:

10 sec before
+
10 sec after
```

---

### Fallback Logic

Implemented fallback behavior:

```text
If exact export fails
↓
Fallback crash package remains available
```

This prevents crash clip loss.

---

### Fixed

* early crash window truncation
* archive refresh issues
* exact export persistence
* upload integration
* worker scheduling issues

---

## Crash Upload Pipeline

### Added

* persistent upload queue
* crash upload retries
* restart-safe uploads
* background uploads

### Verified

Crash uploads survive:

* process death
* application restart
* temporary network loss

---

## Video Archive

### Added

* select all
* deselect all
* batch delete
* automatic archive refresh

### Fixed

* regular clips becoming emergency clips
* archive refresh race conditions
* stale archive entries

---

# v15 and Earlier

Earlier milestones include:

* emergency archive
* crash telemetry cards
* upload states
* rolling segment recording
* real MP4 archive
* Media3 playback
* CameraX preview and recording
* background foreground-service recording
* backend driver scoring
* trip reports
* SaveFish Swift parity
* telemetry batching
* delivery outbox
* sensor collection
* initial Compose UI

---

# Current Project Assessment

Current parity estimate:

```text
95–97%
```

Most technically challenging milestones completed:

```text
Crash Detection
+
Crash Package Assembly
+
Exact Crash Export
+
Crash Upload Pipeline
+
Driver Camera Rotation Stabilization
+
Crash Stop Lifecycle Fix
```

Remaining work:

* long-session recording validation
* network-loss upload validation
* final Swift parity audit
* localization hardcoded-string audit
* final polish

Target:

```text
100% Android ↔ Swift parity
```
