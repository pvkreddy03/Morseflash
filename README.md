# Morseflash (Flash Morse)
Offline Android Morse transmitter and experimental optical receiver.

## Features
- Text-to-Morse converter and Kotlin timing engine.
- Android rear torch transmission.
- CameraX center-region brightness sampling and pulse-to-text decoder.
- SOS transmission and reference alphabet.
- GitHub Actions debug APK workflow.

## Build
Use GitHub Actions **Android debug APK**, or run `gradle :app:assembleDebug` with JDK 17 and Android SDK 35.

**Important:** This is a first test build. Real phone camera decoding and range are NOT hardware-verified. Start at short distances in low ambient light. No internet required.

## Safety
Do not rely on this app for emergency rescue. Avoid flashing toward people with photosensitivity or drivers.
