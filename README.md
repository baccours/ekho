# Ekho

Android app that streams the phone's built-in microphone to your headphones in real time, with a 5-band equalizer. Meant for live monitoring and for hearing your surroundings (for example baby-monitor use with a Bluetooth speaker).

## Features

- **Low-latency pass-through**: `AudioRecord` to `AudioTrack` on a dedicated audio thread, mono, 10 ms chunks, device-native sample rate.
- **5-band equalizer**: peaking filters at 60 Hz, 230 Hz, 910 Hz, 3.6 kHz and 14 kHz, ±15 dB in 1 dB steps, followed by a peak limiter. Implemented in-app so the bands are identical on every device and the low-latency path is kept.
- **Presets**: Flat, Voice Clarity, Boost, plus Custom once you move a slider.
- **Runs in the background**: foreground service of type `microphone`; the notification has a Stop action. The service never restarts on its own.

## Feedback protection

Streaming only runs while a safe output is connected: wired or USB headphones, Bluetooth (including LE and hearing aids). The phone's own speaker and earpiece are not safe.

- Output is pinned to the safe device and stays muted until the actual route is confirmed safe. If the platform reports no route for 1.5 s it unmutes anyway (fail-open); pinning and device callbacks still guard it.
- The stream is cut if it is ever routed elsewhere, then retried with backoff.
- Audio failures (for example microphone busy) are retried every 3 seconds.
- The toggle on the warning card turns feedback protection off, so it streams without a safe output. The setting is remembered and a warning stays visible while it is on.

## Structure

Single module, manual DI (`AppContainer`), no extra frameworks.

```text
app/src/main/java/com/baccours/ekho/
├── audio/        # AudioProcessor (record/track/DSP), BiquadEqualizer, RouteGuard, OutputSafety, AudioDeviceMonitor
├── service/      # AudioService (notification host), StreamingController (when to stream and retry, no Android dependencies), ServiceState
├── data/         # SettingsRepository (Preferences DataStore), EqPreset
└── ui/           # Compose Material 3 screen, MainViewModel, SwipeToggle, theme
```

## Build

- Kotlin 2.4, AGP 9.5, Compose BOM 2026.09, Material 3, DataStore, Coroutines, Timber
- `minSdk` 24, `compileSdk`/`targetSdk` 37; the JDK is provisioned by the Gradle toolchain
- Open in Android Studio and run `:app` on a physical device, or `./gradlew assembleDebug`
- Unit tests: `./gradlew test` (JUnit, `kotlinx-coroutines-test`)

## Permissions

- `RECORD_AUDIO`: required
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MICROPHONE`: background streaming
- `POST_NOTIFICATIONS` (Android 13+): optional; without it the service still runs, but its notification is hidden
