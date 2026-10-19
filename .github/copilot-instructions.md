# ClearQR repository instructions

## Build and validation

- Use JDK 17, Android SDK Platform 35, and the checked-in Gradle wrapper (Gradle 8.9).
- Build the debug APK with `./gradlew assembleDebug`; it is written to `app/build/outputs/apk/debug/app-debug.apk`.
- Install on a connected device or camera-enabled emulator with `./gradlew installDebug`.
- Run unit tests with `./gradlew testDebugUnitTest`; run the focused extractor tests with `./gradlew testDebugUnitTest --tests 'com.clearqr.NearbyNumberExtractorTest'`. There is no configured lint task.

## Architecture

- This is a single Android application module (`:app`) with one activity, `com.clearqr.MainActivity`; the UI is built programmatically rather than from XML layouts or Compose.
- `MainActivity` renders one of three complete screens by replacing the content view: the permission prompt, camera scanner, or recreated-code result. Shared screen-building helpers at the bottom of the class define the visual language.
- The scanner pipeline is CameraX `Preview` plus `ImageAnalysis`, bound to the activity lifecycle. Frames are analyzed on a dedicated single-thread executor and passed to ML Kit configured for all barcode formats; for QR codes, ML Kit Latin text recognition analyzes that same frame.
- Numeric text elements are shown only when their bounding boxes are outside and near the barcode's bounding box; `NearbyNumberExtractor` contains the geometry and numeric-pattern logic. On the first decoded nonempty value, an `AtomicBoolean` gates subsequent results, the camera is unbound, and ML Kit's barcode-format integer is converted to ZXing's `BarcodeFormat`. ZXing's `MultiFormatWriter` then creates an in-memory bitmap for the result screen.
- The app neither persists camera images nor decoded values. Preserve this in-memory-only behavior and the camera permission/error/retry flows when changing scan or result handling.

## Codebase conventions

- Keep scanner work lifecycle-safe: every `ImageProxy` received by the analyzer must be closed, and camera bindings must be released when displaying a result and in `onDestroy`.
- Close the ML Kit text recognizer in `onDestroy`; when modifying frame processing, retain the single-frame-in-flight gate so OCR does not build up a queue of camera frames.
- Update both `Int.toZxingFormat()` and `Int.displayName()` together when supporting an additional ML Kit format. Update the README's supported-format list at the same time.
- Barcode dimensions and margins are deliberately format-sensitive in `createBarcodeBitmap`: linear formats use a wide, short image and larger margin; QR/Data Matrix/Aztec are square; PDF417 has its own height.
- Use `dp()` for runtime view dimensions, and build visual elements through the existing helpers (`createRoot`, `addHeader`, `addFooter`, `actionButton`, `label`, `roundedBackground`) so all screens retain the same styling.
- `AndroidManifest.xml` owns the camera permission, optional camera feature declaration, portrait orientation, and launcher activity. Keep the activity entry and runtime permission flow aligned.
