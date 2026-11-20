# Gaze Link

[简体中文](README.md) | **English**

Gaze Link is an Android application for gaze-assisted communication, designed to offer an affordable way for people with ALS or physical disabilities to communicate in daily life. Users can select screen options by looking toward them through the front camera or by touching the screen. The app provides common phrases, Chinese Pinyin and letter-by-letter input, text editing, and speech through the system text-to-speech engine. Caregivers can assist with calibration, adjust controls, and review usage quality.

Gaze control supports communication and is not intended for medical diagnosis. Camera placement, lighting around the eyes, glasses, posture, and device positioning can affect recognition. Important messages and automatically generated suggestions should be confirmed by the user or caregiver.

## Current version

- App version: 2.10 (`versionCode` 12)
- Android: 8.0 (API 26) or later; `targetSdk` 34
- APK architectures: `arm64-v8a`, `armeabi-v7a`, and `x86_64`
- The app is designed primarily for landscape use. Gaze control requires an accessible front camera and camera permission. Touch controls remain available when gaze control is unavailable.

This repository provides source code and documentation without a prebuilt APK. Follow the instructions below to build an installation package locally.

## Features

- **Gaze selection:** Use the front camera to recognize gaze direction and select interface items by dwelling. Supports calibration for both eyes, the left eye, or the right eye.
- **Touch controls:** Continue using communication features when the camera is unavailable or permission has not been granted.
- **Common phrases:** Phrases organized by everyday communication scenarios, with local editing and reordering.
- **Text input:** Compose Chinese sentences from Pinyin initials, select words from a local Pinyin dictionary, use common words and phrases, or enter text letter by letter. The draft remains editable.
- **Speech:** Uses Android's system text-to-speech (TTS) engine with adjustable speech speed. Chinese speech requires an installed and enabled Chinese TTS engine or voice pack.
- **Optional expression suggestions:** When explicitly requested by the user, a model service configured at build time can generate candidates. Users confirm suggestions before adopting them.
- **Caregiver tools:** Adjust dwell confirmation time, practice optional eye gestures, configure mouth gesture confirmation, run an independent gaze direction test, and export a diagnostic report for the current session.

For detailed instructions, see the [User Guide (Chinese)](docs/Gaze_Link使用说明书.md). For compatibility and testing scope, see the [2.10 Compatibility and Acceptance Record (Chinese)](docs/Gaze-Link-2.10多机型适配与验收.md).

## Build

### Requirements

- Android Studio or a compatible Gradle command-line environment
- JDK 17
- Android SDK Platform 34, Android Build Tools, and NDK/CMake for the project's native components

### Configure and build the APK

In Windows PowerShell:

```powershell
Copy-Item gradle.properties.example gradle.properties
# To enable online expression suggestions, configure the model service
# in your local gradle.properties file.
# Do not commit gradle.properties, API keys, or signing files to Git.
.\gradlew.bat :app:assembleDebug
```

The APK is generated in `app/build/outputs/apk/debug/`. You can also open the repository root in Android Studio and run `:app:assembleDebug` from the Gradle panel.

Offline phrases, text input, and system speech do not depend on a model service. Online expression suggestions require a network connection and service configuration. Local input and speech remain available if the service is unavailable.

> **Credential security:** Client-side keys can be extracted from an Android APK. Although the repository ignores local `gradle.properties`, keys added to the build configuration are still embedded in the APK. Do not include personal or production keys in publicly distributed builds. For production distribution, route model requests through a controlled backend. Revoke and rotate any key that has been included in a shared APK.

## Project structure

```text
app/                         Android application, UI, gaze and communication features
opencv/                      OpenCV Android module and native components
gradle/                      Gradle Wrapper configuration
gradle.properties.example    Local build configuration example without API keys
docs/                        User guide and 2.10 compatibility and acceptance record
```

## Validation scope

The 2.10 acceptance record covers unit tests, Lint, and compatibility checks on an Android 15/API 35 emulator. It does not cover acceptance testing on physical devices from different manufacturers. Camera behavior, gaze performance, Chinese TTS, and sustained operation still need to be tested on the target device. Gaze recognition quality data describes a single session and does not represent a clinical assessment or medical accuracy.

## Privacy

Gaze recognition runs on the device using the camera. Optional online expression suggestions send the current text and any optional conversation context to the configured model service only when the user explicitly requests them. Diagnostic reports exported by the user or caregiver may include device and session information. Review the contents and intended recipient before sharing.

## Acknowledgments

This project builds on and extends the original [Jonassunhk/Gaze-Link](https://github.com/Jonassunhk/Gaze-Link) project. We pay tribute to the original author, [Jonassunhk](https://github.com/Jonassunhk), and thank them for their work on gaze-assisted communication and for sharing it as open source, providing an important foundation for this project.
