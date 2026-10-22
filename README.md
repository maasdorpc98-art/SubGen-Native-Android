# v3.2.0 HYPER production integration

Native optional production tools now sit within Hypnosis. Normal use remains **Paste → Create → Export**. Kokoro FP32, Studio and NeuroModulation A/B/A+B are retained.

See `CHANGELOG-HYPER-v3.2.0.md`, `FEATURE-ACCEPTANCE-v3.2.0.md`, `TEST-RESULTS-v3.2.0.md` and `INSTALL-AND-ROLLBACK-v3.2.0.md` for implementation, evidence and limits. The acoustic syntax remains the documented v3.1.1 contract; the new tools do not add hypnotic commands or rewrite narration.

The installable test build uses a separate `.hypertest` ID. A stable-ID release candidate requires the original private stable signer, which is not present here. Samsung UI/listening acceptance and real-session verification remain outstanding.

---

> **Build candidate, not a compiled or proven APK.** See [BUILD-INSTRUCTIONS.md](BUILD-INSTRUCTIONS.md).

# Sub Gen Native v2.1 — Android native rebuild (SOURCE PREVIEW)

**Status: code drafted, parser unit-tested, but no APK compiled and no real Android test performed.** This is a new native Kotlin/Android app, not an upgraded HTML WebView. It installs alongside the old Subliminal / Hypnosis beta under the new package `com.subgen.nativeapp`.

## Features implemented in source

- Native Android Views (no HTML, JavaScript or WebView).
- Hypnosis script editor with auto-save; sections, exact pauses, breathe, paragraph, pacing controls.
- Four Kokoro voice IDs: ORION=16 (am_michael), ROWAN=14 (am_fenrir), LUNA=2 (af_bella), SERENA=3 (af_heart).
- sherpa-onnx Android JNI native inference, reusable loaded model, configurable CPU threads 1–4, and int8 Kokoro model.
- One-time on-device download of model + voice table from upstream release, stored under app-private files (not re-downloaded each launch). Requires approximately 500MB available for download and extraction. It contains all speakers, not one neural engine for each speaker.
- Built-in native preview and 2-vs-4-thread benchmark that stores the fastest measured thread setting.
- Foreground render service and wake lock, with ongoing notification while working. Home button should not stop the job; Android can still stop it under battery/process restrictions, and force-stop always stops it.
- Every speech block saved atomically as a unique WAV checkpoint. Render again after interruption to reuse saved blocks. Script and settings saved via SharedPreferences.
- Constant-memory disk-based final WAV assembly, exact 24kHz pauses, optional low-level binaural tone, output to public Downloads/SubGen via MediaStore.
- Separate native Subliminal mixer: 2 carriers and 2 affirmation tracks, weighted real gain controls, 24kHz stereo PCM16 WAV export. **Present limitation: only PCM16 WAV imports; advanced DSP/presets from v8.0.32 have not been ported.**

## Speed optimization rationale

The v0.9 diagnostics showed a 3.05 second preview taking 18.36 seconds (RTF 6.02) on WebAssembly and 0 blocks saved. This native rebuild uses C++/JNI ONNX inference without JavaScript/WebView and a compact int8 Kokoro model; the speed benefit MUST be established with a real device benchmark. A smaller package or CPU thread increase is not a guarantee of a faster voice model. Final processing uses a streaming file pipeline rather than building an 80MB+ in-memory blob.

## Building an APK

1. Use an Android Studio / Linux environment with internet, Android SDK 35, Java 17 and Gradle 8.10.2.
2. Run `bash scripts/prepare-native-runtime.sh` to fetch the **matching sherpa-onnx v1.13.8** Android arm64 JNI libraries and Kotlin binding. These downloaded binaries are not bundled in this source ZIP. Verify upstream licenses and versions before distributing.
3. Run `gradle :app:assembleDebug` or use `.github/workflows/build-android.yml` after adding this directory to a GitHub repository and manually triggering the workflow. An APK will be produced under `app/build/outputs/apk/debug/` only **after the build succeeds**.
4. Install the newly built APK on the Galaxy S21 (arm64). It uses its own app identifier; do not uninstall your working v0.4 beta.
5. Press **Install / Check Offline Voices** once while online, then use **Create Hypnosis WAV**. To test speed, compare 2 and 4 CPU threads with the SAME sample phrase, report actual RTF and output quality.

Note: The Github Actions debug signing key is ephemeral across clean runners. For reliable in-place updates, configure a permanent, private Android signing key on your build system. Never commit private keystores or passwords to a repository.

## Requirements and limitations

- Native TTS runtime: [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (Apache-2.0; see dependencies/third-party licenses).
- Model: `kokoro-int8-multi-lang-v1_0` from [upstream sherpa-onnx release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models), with 53 speaker IDs and the exact four voices selected above.
- First-time voice installation needs internet. On-device generation is offline after install. The model pack is an upstream HTTPS download: **the archive currently has no pinned checksum**; adding cryptographic hash verification before production release is recommended.
- When Android force-stops the process, the foreground service cannot continue. A battery-optimized device may also still stop background processing. App process restart / manually pressing Create can resume already saved blocks.
- Export supported: WAV 24kHz 16-bit stereo. Hypnosis ambiance/ducking/rhythmic tones and full old Subliminal v8.0.32 DSP have not been migrated. This is a native **development baseline**, not a fully equivalent production replacement.
- Native GPU/NPU acceleration is NOT implemented. sherpa-onnx's documented Android CPU inference is the baseline. Do not assume WebGPU availability implies acceleration for JNI.

## Tests and what is unverified

- Pure Kotlin script parser tests pass (`scripts/test_parser.kt`).
- No Android SDK, Gradle, JNI artifacts, model weights or emulator are present in the current build environment. Therefore the app cannot be compiled or benchmarked here. Source remains subject to compile / device integration testing.

## Existing apps preserved

Your last confirmed-working Subliminal v0.4 and current v1.1 HTML-based beta are separate packages. The source here does not modify or sign either of them. No automatic migration from the HTML WebView's IndexedDB storage is currently available; copy your script into the new editor as needed.
