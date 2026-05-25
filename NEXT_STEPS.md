# Next Steps

## Immediate Focus: Dashcam / Video Mode

The next project phase is Android Dashcam parity with the Swift implementation.

### 1. Locate Swift Source of Truth

Review Swift files related to:

- Dashcam screen
- camera preview
- recording state
- video archive
- video metadata upload
- permissions flow

Android implementation should follow Swift behavior and UI, not invent a new flow.

### 2. Android Video Mode Screen

Implement a Compose screen with:

- live camera preview
- start/stop recording control
- recording timer
- recording status indicator
- Swift-style large pill controls
- compact typography
- black/video-first layout
- safe permission fallback state

Recommended Android stack:

- CameraX
- `PreviewView`
- `LifecycleCameraController` or `ProcessCameraProvider`
- `Recorder`
- `VideoCapture`

### 3. Recording Pipeline

Implement:

- recording start
- recording stop
- file output
- duration tracking
- error handling
- lifecycle cleanup
- orientation handling

### 4. Video Archive

Implement Swift-style archive:

- list of recorded videos
- compact cards
- date/time
- duration
- local file status
- upload status
- preview/play action

### 5. Backend Contract

Define/align payload with Swift:

- `device_id`
- `driver_id`
- `session_id`
- `video_id`
- `started_at`
- `ended_at`
- `duration_sec`
- `file_size_bytes`
- `local_uri`
- upload state
- metadata/analytics

### 6. Permissions

Handle:

- camera permission
- microphone permission if audio is required
- storage/media compatibility
- background/lifecycle edge cases

### 7. Localization

Move hardcoded strings to resources:

- RU
- EN

Priority strings:

- Home buttons
- SaveFish game
- Dashcam
- Video archive
- Trip report

## After Dashcam

- Full DTO parity audit
- Motion aggregation parity Android ↔ iOS
- Cleanup/refactor
- Contract tests
- UI snapshot/comparison checks
