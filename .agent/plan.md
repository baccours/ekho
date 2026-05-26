# Project Plan

Ekho: A low-latency audio pass-through app for Android.
Features:
- Real-time microphone to output streaming.
- Low-latency using AudioRecord and AudioTrack.
- Foreground Service for background operation.
- Equalizer with presets (Voice Clarity, Low Latency Boost, Flat) and per-band sliders.
- DataStore for persisting settings.
- Safety: Prevents feedback by blocking built-in speakers. Auto-pause/resume on routing changes (headphones/Bluetooth).
- UI: Jetpack Compose, Material 3, vibrancy, energetic color scheme.
- Permissions: RECORD_AUDIO, POST_NOTIFICATIONS.
- Notification with a Stop action.
- Target: API 24+.

## Project Brief

# Project Brief: Ekho

## Features
1.  **Low-Latency Audio Pass-Through**: Real-time microphone-to-output streaming utilizing `AudioRecord` and `AudioTrack` to ensure minimal delay.
2.  **Foreground Service & Persistent Controls**: Continuous background operation with a dedicated notification featuring "Stop" actions for seamless user control.
3.  **Dynamic Equalizer**: Multi-band frequency control with energetic UI sliders and optimized presets like "Voice Clarity" and "Low Latency Boost."
4.  **Intelligent Feedback Prevention**: Automated safety logic that pauses audio when headphones are disconnected and blocks routing to built-in speakers to prevent acoustic feedback.
5.  **Persistent Settings**: Integration of Jetpack DataStore to reliably save user equalizer configurations and app preferences across sessions.

## High-Level Technical Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3) with an energetic, vibrant color scheme.
- **Concurrency**: Kotlin Coroutines and Flow for high-performance, asynchronous audio data processing.
- **Data Persistence**: Jetpack DataStore (Preferences).
- **Core APIs**: Android Audio Framework (`AudioRecord`, `AudioTrack`, `DynamicsProcessing`).
- **Code Generation**: KSP (Kotlin Symbol Processing).

## Implementation Steps
**Total Duration:** 27m 33s

### Task_1_Audio_Engine: Implement the core audio engine: Handle RECORD_AUDIO and POST_NOTIFICATIONS permissions. Create a Foreground Service with a persistent notification (including a Stop action). Implement low-latency audio pass-through using AudioRecord and AudioTrack. Include safety logic to detect headphones and prevent feedback by blocking built-in speakers.
- **Status:** COMPLETED
- **Updates:** Implemented core audio engine using AudioRecord and AudioTrack. Added Foreground Service with persistent notification and Stop action. Integrated safety logic to prevent feedback by blocking built-in speakers and detecting headphones. Handled permissions and updated Manifest. Generated adaptive app icon.
- **Acceptance Criteria:**
  - Permissions are requested and handled.
  - AudioRecord and AudioTrack stream mic to output with low latency.
  - Foreground Service runs in the background with a notification.
  - Audio pauses or routes safely when headphones are disconnected.
  - App builds successfully.
- **Duration:** 13m 26s

### Task_2_EQ_and_DataStore: Implement audio processing and persistence: Integrate an Equalizer (e.g., DynamicsProcessing) into the audio stream. Set up Jetpack DataStore to persist equalizer bands and presets (Voice Clarity, Low Latency Boost, Flat).
- **Status:** COMPLETED
- **Updates:** Integrated android.media.audiofx.Equalizer into AudioService. Implemented SettingsRepository using Jetpack DataStore to persist presets and band levels. Defined "Voice Clarity", "Low Latency Boost", and "Flat" presets. AudioService now reactively applies settings from DataStore to the live audio stream.
- **Acceptance Criteria:**
  - Equalizer affects the audio stream.
  - DataStore successfully saves and loads settings.
  - Presets are implemented and functional.
  - App builds successfully.
- **Duration:** 1m 42s

### Task_3_UI_Implementation: Develop the UI using Jetpack Compose and Material 3: Create a vibrant, energetic color scheme. Implement the main screen with start/stop controls, real-time EQ sliders, and preset selection. Ensure full edge-to-edge display and integration with the audio service.
- **Status:** COMPLETED
- **Updates:** Developed the UI using Jetpack Compose and Material 3 with a vibrant, energetic color scheme. Implemented the main screen with start/stop controls, real-time EQ sliders, and preset selection. Integrated the UI with the audio service and DataStore. Ensured full edge-to-edge display. The UI is responsive and provides clear feedback on the app's status (transmitting, paused, etc.).
- **Acceptance Criteria:**
  - UI is built with Material 3 and vibrant colors.
  - Sliders and controls correctly update the audio engine and DataStore.
  - Edge-to-edge display is functional.
  - App builds successfully.
- **Duration:** 6m 15s

### Task_4_AppIcon_and_Verification: Finalize assets and verify the application: Create an adaptive app icon matching the app's theme. Perform a 'Run and Verify' pass to ensure stability, requirement alignment, and lack of crashes.
- **Status:** COMPLETED
- **Updates:** Verified the application's stability and core features. Adaptive app icon is implemented and matches the theme. Permissions for microphone and notifications are handled correctly. Start/Stop toggle, Equalizer controls, and presets are fully functional. Safety logic (headphone detection) is robust and prevents feedback. UI is vibrant, Material 3, and edge-to-edge. Application is stable with no crashes. Build passes.
- **Acceptance Criteria:**
  - Adaptive app icon is implemented.
  - Application is stable (no crashes).
  - All core features (pass-through, EQ, safety) work as intended.
  - All existing tests pass.
  - Build passes.
- **Duration:** 6m 10s

