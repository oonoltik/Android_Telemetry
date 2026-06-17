# Android_Telemetry

Android implementation of the DriveTelemetry platform with a primary goal of achieving maximum behavioral and visual parity with the Swift/iOS implementation.

Current project state:

**Android ≈ 95–97% Swift parity**

Swift remains the source of truth.

---

# Project Goals

The Android application should:

* Match Swift UI behavior
* Match Swift telemetry behavior
* Match Swift lifecycle semantics
* Match Swift trip reporting
* Match Swift video/dashcam behavior
* Match Swift Driver Monitoring behavior
* Use the same backend contracts
* Produce equivalent analytics and telemetry output

---

# Architecture Overview

The platform consists of:

## Mobile Client

Android application:

```text
Sensors
↓
Telemetry Processing
↓
Local Persistence
↓
Delivery Pipeline
↓
Backend
↓
Trip Reports
```

Supported subsystems:

* Telemetry
* Driver Scoring
* Trip Reporting
* Day Monitoring
* Dashcam
* Driver Monitoring
* SaveFish
* Localization

---

# Telemetry Pipeline

Current Android telemetry stack:

```text
SensorManager
↓
Frame Generation
↓
Batch Aggregation
↓
Outbox
↓
Delivery Worker
↓
Backend
```

Features:

* replay-safe
* offline-safe
* process-death-safe
* reboot-safe

Implemented:

* telemetry batching
* event detection
* retry delivery
* finish flow
* pending finish persistence

---

# Driver Scoring

Android currently supports:

* backend score retrieval
* recent score changes
* driver status
* driver comparison blocks
* trip score visualization

Home screen uses real backend data.

---

# Day Monitoring

Implemented:

* activity monitoring
* background collection
* monitoring sessions
* aggregation pipeline

Status:

Production ready pending additional long-run tuning.

---

# Dashcam System

Dashcam is one of the most advanced subsystems in the Android implementation.

---

## Recording Pipeline

Implemented:

* CameraX
* PreviewView
* Recorder
* VideoCapture
* ImageAnalysis for Driver Monitoring
* foreground recording service
* background recording

Supports:

* road camera
* driver camera
* camera switching before recording
* recording timer

---

## Foreground Service

Recording continues while:

* app minimized
* screen locked
* user navigates away

Implemented through:

```text
DashcamRecordingService
```

with persistent notification.

---

## Rolling Recording

Android records rolling segments:

Production configuration:

```text
segmentDurationMs = 120_000L
```

Recording structure:

```text
road_xxx_clip_001.mp4
road_xxx_clip_002.mp4
driver_xxx_clip_001.mp4
driver_xxx_clip_002.mp4
...
```

---

## Driver Camera Rotation Stabilization

Driver camera uses a CameraX rebind before starting the next driver segment.

Purpose:

```text
avoid CameraX stream inactive state during driver-camera segment rotation
```

Road camera rotation remains direct and unchanged.

Verified with:

```text
Driver camera
+
Preview
+
VideoCapture
+
ImageAnalysis / DMS
+
120-second segments
```

---

## Video Archive

Implemented:

* Swift-style archive
* real MP4 files
* multi-select
* select all
* deselect all
* delete selected
* export support
* emergency grouping

Archive updates automatically.

No manual refresh required.

---

## Emergency Archive

Implemented:

* emergency clip storage
* crash clip metadata
* crash telemetry
* upload state tracking

States:

```text
LOCAL_ONLY
QUEUED
UPLOADING
UPLOADED
FAILED
```

---

## Crash Detection

Implemented:

```text
CrashDetectionManager
```

Supports:

* g-force detection
* cooldown logic
* duplicate protection

Crash detection is gated:

```text
ON  = telemetry trip active OR video recording active
OFF = telemetry OFF and video OFF
```

---

## Crash Package Assembly

Android creates:

```text
10 seconds before crash
+
10 seconds after crash
```

window.

Crash package contains:

* video
* metadata
* telemetry snapshot
* telemetry timeline

---

## Crash Stop Lifecycle

If the user presses Stop immediately after a crash:

```text
UI stops immediately
↓
recorder internally finishes the post-crash window
↓
crash package is created
↓
exact export runs
↓
archive shows emergency clip
```

This prevents the timer from reappearing when returning from Video Archive.

---

## Exact Crash Export

Implemented:

```text
CrashClipExactExporter
```

Based on:

```text
Media3 Transformer
```

Purpose:

Generate a precise crash clip window independent of rolling segment size.

Example:

```text
Crash at 5 sec

Result:

available pre-crash
+
10 sec after
```

Normal crash:

```text
10 sec before
+
10 sec after
```

---

## Exact Export Components

Implemented:

```text
CrashClipExactExporter
CrashClipExactExportWorker
CrashClipExactExportScheduler
```

Workflow:

```text
Crash detected
↓
Fallback crash package
↓
Exact export worker
↓
Exact clip generated
↓
Archive updated
↓
Upload pipeline
↓
Server
```

Fallback remains available if exact export fails.

---

# Driver Monitoring

Implemented for Driver camera recording.

Behavior:

```text
Road + preview      → OFF
Road + recording    → OFF
Driver + preview    → OFF
Driver + recording  → ON
After Stop          → OFF
```

Signals include:

* eye openness
* PERCLOS
* microsleep
* head pose
* fatigue state
* voice warnings

Voice messages follow selected language:

* Russian
* English

---

# Upload Pipeline

Implemented:

* upload queue
* retry support
* background upload
* crash clip upload
* chunk upload

Crash uploads survive:

* app restarts
* process death
* temporary network loss

Additional validation is still required for reboot and forced network interruption scenarios.

---

# Server Integration

Integrated endpoints:

* telemetry ingest
* trip finish
* crash uploads
* video uploads
* SaveFish uploads

Android and Swift share the same backend infrastructure.

---

# SaveFish Game

Android SaveFish is close to Swift parity.

Implemented:

* fish assets
* water simulation
* spill mechanics
* refill mechanics
* game over state
* analytics upload
* telemetry upload

Assets:

```text
fish_low
fish_mid
fish_high
```

Analytics parity achieved.

---

# Localization

Implemented:

## Russian

Production ready.

## English

Production ready.

Localized areas:

* Home
* Trips
* Trip Reports
* Video Archive
* Video Player
* SaveFish
* Notifications
* Runtime Warnings
* Driver Monitoring voice messages

Remaining audit still required for edge cases and rare error states.

---

# Current Stability Summary

Recently verified:

* regular Road camera archive segmentation
* Driver camera segment rotation
* Driver camera + DMS recording
* 120-second segment duration
* crash detection gating
* crash stop UX
* exact crash export
* emergency archive display
* no spontaneous recording start in idle app

Remaining validation:

* long dashcam sessions
* network-loss upload retry
* reboot/process-death upload recovery
* final Swift visual parity audit
* final localization audit

---


# DriveTelemetry Guide

The Android app includes an in-app DriveTelemetry guide screen.

Purpose:

```text
product explanation
+
first-session engagement
+
feature discovery
```

The guide is implemented as a Compose-based adaptive onboarding experience.

---

## Guide Flow

Slides:

```text
1. Ordinary trip
2. Driving safety
3. Fatigue monitoring
4. Accident protection
5. Built-in dashcam
6. Family protection
7. Personal driver rating
```

---

## Implementation Model

The guide uses:

```text
clean background assets
+
Compose text and UI overlays
+
strings.xml localization
+
orientation-aware layout
```

This allows the same visual system to support:

* Russian
* English
* portrait mode
* landscape mode
* future copy changes without regenerating images

---

## Assets

Background files are expected under:

```text
app/src/main/res/drawable-nodpi/
```

Example naming:

```text
guide_bg_1.webp
guide_bg_1_portrait.webp
...
guide_bg_7.webp
guide_bg_7_portrait.webp
```

---

## Localization

Guide text must remain in:

```text
res/values/strings.xml
res/values-en/strings.xml
```

Do not embed user-visible text inside guide images.

---


# Documentation

Project documentation:

* README.md
* PROJECT_STATUS.md
* NEXT_STEPS.md
* KNOWN_ISSUES.md
* CHANGELOG.md
