# Local source verification — 16 October 2026

| Item | Result |
|---|---|
| Native source inventory, no HTML / WebView engine | PASS |
| Kotlin parser (sections, 5s pause, breathe, paragraph, delivery commands) | PASS |
| Kotlin parser long speech segmentation | PASS |
| WAV 16-bit header and 24kHz output frame count | PASS (JVM test with Android API stubs) |
| Exact 250ms digital silence in rendered WAV | PASS (JVM test) |
| Two-input native Subliminal mixer, 60-second WAV | PASS (JVM test with Android API stubs) |
| Source-only Kotlin ModelInstaller and sherpa bridge type syntax | PASS against *simplified stubs*, not upstream JNI library |
| Native Android SDK build | **NOT RUN** — SDK / Gradle / prebuilt JNI absent from this execution environment |
| Foreground service behavior on Galaxy S21 | **NOT TESTED** |
| Native Kokoro latency on Galaxy S21 | **NOT MEASURED** |
| Kokoro model download and unpack on Android | **NOT TESTED** |
| APK installation | **NO APK CREATED** |

Strict limitation: JVM tests with local stubs verify part of the portable Kotlin logic, not Android package buildability or native third-party compatibility. The prebuild workflow must complete and the app must be tested on Android before calling it a release.
