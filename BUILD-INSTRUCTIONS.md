# Android APK build instructions (native Sub Gen)

**No APK has been generated yet.** A successful build requires an Android SDK, Gradle, and the matching sherpa-onnx v1.13.8 Android JNI runtime; these binaries are not contained in this archive. The v2.1 project has been locally tested only where the available Kotlin CLI supports testing.

## GitHub Actions (recommended)

1. On GitHub, create an empty repository under your account (for example `SubGen-Native-Android`). Do not commit any password or signing key.
2. Extract this ZIP. Upload the **contents** of `SubGen-Native-v2.1-build-ready/` to the **root** of the repository. `.github/workflows/build-android.yml` must be at the repository root, not inside a nested folder.
3. Open the repository's **Actions** tab, select **Native Android APK**, and click **Run workflow** (or push to `main` to trigger it automatically).
4. Wait for the run to finish. Only if it turns **green**, download the artifact **SubGen-Native-Android-debug-APK**. It contains `app-debug.apk`.
5. Install on an ARM64 Galaxy S21. First install of Kokoro model requires internet and free space; thereafter voices/model are loaded locally.

This project is a native feature-development build; its Subliminal mixer currently handles 4 imported PCM16 WAV sources but does **not** recreate all advanced v8.0.32 DSP and presets. Do not replace or uninstall your older working APK.

### Verify the APK

The workflow checks: pure Kotlin parser/audio tests, downloaded Android native libraries and their machine architecture, Kotlin JNI API methods, successful Gradle compilation, Android signature, and embedded `arm64-v8a` JNI lib. A green result verifies the package **build**, but not actual voice-generation speed or background operation on the phone.

### Local Android Studio

Open this project folder in Android Studio; allow Android SDK 35 and Gradle dependencies to install. Run `bash scripts/prepare-native-runtime.sh` on a machine with internet. Run `bash scripts/check-runtime.sh`, then `gradle :app:assembleDebug`. APK: `app/build/outputs/apk/debug/app-debug.apk`.

### Updating a test build

Debug APKs built on separate machines/runners may use different keys. To update without uninstalling, build with the same permanent private signing key. Production signing is NOT configured in this development candidate.
