# Dispenser Trigger Android · AGENT Guide

## What this app is
- Kotlin Android app that sends trigger commands over USB serial to a dispenser.
- Single `app` module, ViewBinding UI (no Compose), Activity launch mode `singleTop` to keep one instance while handling USB attach intents.
- Uses `usb-serial-for-android` (mik3y) with helper classes `SerialPortManager` and `UsbPermissionHelper`.

## Stack and build expectations
- Android Gradle Plugin 8.2.0, Kotlin 1.9.20, Gradle 8.5 (wrapper configured).
- JDK 17 required. If `org.gradle.java.home` in `gradle.properties` is invalid locally, point it to an installed JDK 17 or remove the override.
- `compileSdk`/`targetSdk` 34, `minSdk` 26. Android SDK path is read from `local.properties` (`sdk.dir=/Users/ganggyunggyu/Library/Android/sdk`).
- Build via `./gradlew assembleDebug` (use wrapper; regenerate scripts if missing) and run on a device with USB host support; emulator cannot exercise USB serial.

## Code style and architecture
- Kotlin only; keep ViewBinding for UI wiring. Avoid adding non-essential comments—keep the existing concise explanations.
- Respect existing config objects in `Constants.kt` (`SerialConfig`, `TriggerCommand`, `UsbConfig`, `LogConfig`, `UiConfig`); do not hardcode protocol values elsewhere.
- UI updates must stay on the main thread (`runOnUiThread`/`Handler`). USB listeners may fire off the UI thread.
- Keep logging through `LogConfig.TAG` and flow logs to both Logcat and the on-screen log; trim logs via `LogConfig.MAX_LOG_LINES`.
- USB permission flow: register/unregister receivers correctly (`UsbPermissionHelper.unregister(this)` in lifecycle). When changing permission handling, maintain per-device callbacks and `ACTION_USB_PERMISSION` consistency.
- If adding strings or colors, place them in `res/values` resources; keep layout definitions in XML alongside ViewBinding.

## Testing and verification
- Minimum check: `./gradlew assembleDebug` once the wrapper script and JDK are available.
- Functional validation needs a physical device with a USB-Serial adapter; emulator testing is insufficient.
- Watch for regression in auto-connect logic: verify attach/detach handling and that `SerialPortManager.isConnected()` stays in sync with UI state.

## Pitfalls to watch
- Missing Gradle wrapper scripts block CLI builds; restore them if absent.
- Invalid `org.gradle.java.home` or missing JDK 17 stops Gradle before configuration.
- USB permission broadcasts require `setPackage` on the intent (already set); keep it when modifying.
