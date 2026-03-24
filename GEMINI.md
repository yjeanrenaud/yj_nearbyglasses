# Nearby Glasses (yj_nearbyglasses)

Protecting privacy by alerting users to smart glasses nearby using Bluetooth Low Energy (BLE) manufacturer IDs.

## Project Overview

*Nearby Glasses* is an Android application designed to detect the presence of smart glasses (such as Meta Ray-Bans and Snap Spectacles) in the vicinity. It functions by scanning for specific Bluetooth SIG assigned company identifiers in the advertising frames of BLE devices.

### Core Technologies
- **Language:** Kotlin
- **Build System:** Gradle (Wrapper 8.13)
- **Minimum SDK:** 26 (Android 8.0)
- **Target/Compile SDK:** 35 (Android 15)
- **Architecture:** 
    - **`BluetoothScanService`**: A foreground service that manages background scanning and notification lifecycle.
    - **`BluetoothScanner`**: The core engine for BLE scanning, filtering by RSSI and Manufacturer Specific Data.
    - **`DetectionEvent`**: Data model and heuristic logic for identifying smart glasses.
    - **`MainActivity`**: Main UI providing status updates, logs, and "Canary Mode" visualization.
    - **`NotificationHelper`**: Manages Android notification channels and alert delivery.
    - **`PreferencesManager`**: Handles persistent user settings.

## Building and Running

### Prerequisites
- **JDK 17** is required for the build.
- **Android SDK Platform 35** must be installed.

### Key Commands
- **Build Debug APK:** `./gradlew assembleDebug`
- **Build Release APK:** `./gradlew assembleRelease` (requires signing configuration)
- **Run Tests:** `./gradlew test`
- **Run Lint:** `./gradlew lint`
- **Clean Project:** `./gradlew clean`

## Development Conventions

### BLE Detection Logic
The app uses a heuristic approach based on Company IDs in the `Manufacturer Specific Data` of BLE advertising frames.
- **Meta Platforms (formerly Facebook):** `0x01AB`, `0x058E`
- **Luxottica Group (Meta Ray-Ban manufacturer):** `0x0D53`
- **Snapchat, Inc.:** `0x03C2`

Devices are filtered based on an RSSI (signal strength) threshold (default `-75 dBm`) to ensure alerts are only triggered for devices that are "nearby" (roughly 3-15 meters depending on environment).

### Notification & Canary Mode
- **Standard Mode:** Shows high-priority system notifications when a device is detected.
- **Canary Mode:** A subtle UI-based alert where a canary icon changes state (hides) when a device is detected, avoiding disruptive system notifications.

### Permissions
The app requires the following permissions (requested at runtime):
- `BLUETOOTH_SCAN` & `BLUETOOTH_CONNECT` (Android 12+)
- `ACCESS_FINE_LOCATION` (Required for BLE scanning on older Android versions)
- `POST_NOTIFICATIONS` (Android 13+)

## Project Structure
- `app/src/main/java/.../`: Kotlin source code.
- `app/src/main/res/`: Android resources (layouts, drawables, localized strings).
- `app/src/main/res/xml/preferences.xml`: Definition of the settings screen.
- `fastlane/`: Metadata and configuration for Play Store deployment.
