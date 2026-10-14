# ClearQR for Android

A native Kotlin QR scanner. CameraX streams frames to ML Kit for QR-code detection, and ZXing recreates the decoded text as a clean QR code on screen. The app does not take photos, write scan history, or upload scans. The decoded value and generated bitmap stay in memory for the current screen only.

## Requirements

- Android Studio
- JDK 17
- Android SDK Platform 35 and Platform-Tools
- An Android device with a camera, or a camera-enabled emulator

The project uses Android Gradle Plugin 8.7.3, Gradle 8.9, and Kotlin 2.0.21. No Expo, Node.js, or development server is used. The first Gradle sync downloads Gradle and Android dependencies.

## Open and run

1. Open this project folder in Android Studio (the folder containing `settings.gradle.kts`).
2. Allow Gradle to sync. If prompted, install Android SDK Platform 35.
3. Connect an Android device with USB debugging enabled, or start an emulator.
4. Select the `app` run configuration and press **Run**.

Alternatively, with a connected device and `adb` available:

```sh
./gradlew installDebug
```

## Build an installable APK

In Android Studio, select **Build → Build Bundle(s) / APK(s) → Build APK(s)**. The debug APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

You can also copy the APK to an Android device and open it. Android may prompt you to allow installs from that file manager. Debug APKs are for testing; they are not the recommended way to distribute a public release.

## Create a signed APK

For a standalone APK signed with your own key, choose **Build → Generate Signed Bundle / APK → APK** in Android Studio. Create a signing key and keep it secure. Android Studio shows the APK output location when it finishes.
