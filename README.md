# Ekho - Professional Audio Monitoring & EQ

**Ekho** is a high-performance, low-latency audio monitoring application designed for real-time microphone pass-through and equalization. Built with a focus on precision and persistence, it allows users to monitor their environment or audio input with professional-grade signal processing.

---

## 🚀 Key Features

- **Real-Time Pass-Through**: Instantaneous microphone-to-output streaming with optimized latency for live monitoring.
- **5-Band Parametric Equalizer**: Precise control over frequency bands with custom +/- 15dB adjustments.
- **Intelligent Audio Routing**:
  - Automatic headphone detection and safety interlocks to prevent feedback loops.
  - Optional "Allow Speaker" mode for specialized monitoring scenarios.
- **Persistent Foreground Service**: Leverages Android's `FOREGROUND_SERVICE_MICROPHONE` type to ensure audio processing remains uninterrupted when the app is backgrounded or the screen is locked.
- **Material 3 Interface**: Modern, expressive design with high-contrast UI elements and responsive controls.
- **Audio Presets**: Quick-access settings for common scenarios:
  - **Flat**: Neutral frequency response.
  - **Voice**: Enhanced clarity for vocal monitoring.
  - **Boost**: Amplified output for low-signal environments.

---

## 🛠 Tech Stack

- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose (Material 3)
- **Architecture**: MVVM (Model-View-ViewModel) with a Repository pattern for state management.
- **State Management**: Kotlin Flow (StateFlow) and Coroutines.
- **Background Execution**: Android Foreground Service with Android 14+ compatibility (Microphone type).
- **Audio Logic**: Low-latency `AudioRecord` and `AudioTrack` processing with `Equalizer` effect integration.
- **Data Persistence**: Jetpack DataStore for saving presets and band levels.

---

## 🏗 Architecture & Standards

Ekho is built following modern Android Development standards:
- **Clean Architecture**: Clear separation between UI components, Audio processing logic, and Data repositories.
- **Single Source of Truth**: `SettingsRepository` manages the global state of equalizer bands and app preferences, synchronized across the Service and UI.
- **Material 3 Expressive**: Adherence to the latest design guidelines, featuring dynamic layout transitions and accessible component styling.
- **Safety First Logic**: Built-in protections against feedback loops by requiring headphone connection by default.

---

## ⚙️ Setup Instructions

### Prerequisites
- Android Studio Ladybug (or newer)
- Android SDK 35
- Kotlin 2.x

### Build Steps
1. Clone the repository.
2. Open the project in Android Studio.
3. Sync Project with Gradle Files.
4. Run the `:app` module on a device (Physical device recommended for audio latency testing).

### Permissions
The app requires the following permissions for full functionality:
- `RECORD_AUDIO`: Required for microphone pass-through.
- `FOREGROUND_SERVICE_MICROPHONE`: Required for background audio processing (Android 14+).
- `POST_NOTIFICATIONS`: For persistent service controls in the notification shade.

---

## 🎨 Design Aesthetic
Ekho utilizes a **modern Material 3 palette** with high-contrast surfaces to ensure readability in various lighting conditions. The UI features custom vertical slider components for the equalizer, providing a tactile and intuitive mixing experience.

