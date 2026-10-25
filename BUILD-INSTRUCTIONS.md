# Build SubGen Native v3.2.4

The source includes the existing three native engines and optional High Quality
outputs. See `CHANGELOG-v3.2.2.md` for quality behavior and format limits, and
`CHANGELOG-v3.2.3.md` for Samsung My Files audio selection.
See `CHANGELOG-v3.2.4.md` for explicit ON/OFF control labels.

## Build environment

- Java 17
- Android SDK platform 35 and build-tools 35.0.0
- Gradle 8.10.2
- Kotlin 2.0.21 (selected by the project)
- ARM64 sherpa-onnx v1.13.8 runtime (downloaded by the preparation script)

## Verify and compile

Run from the project root with `ANDROID_HOME` set to your SDK location:

```bash
python3 scripts/check-source.py
python3 scripts/check-studio-parity.py
python3 scripts/check-hyper-compatibility.py
bash scripts/run-core-tests.sh
bash scripts/run-hyper-tests.sh
bash scripts/run-hq-tests.sh
bash scripts/prepare-native-runtime.sh
bash scripts/check-runtime.sh
gradle --no-daemon :app:assembleDebug :app:assembleRelease
```

The GitHub Actions workflow runs the complete source/regression checks, builds
both variants, checks the ARM64 runtime and uploads an offline signing kit.
`run-hq-tests.sh` includes precision, aliasing, headroom, pause, A/B recovery,
cache, cancellation, snapshot and 20-minute synthetic export tests.

## Outputs and signing

- Debug: `app/build/outputs/apk/debug/app-debug.apk`, a separate `.hypertest` app.
- Stable candidate: `app/build/outputs/apk/release/app-release-unsigned.apk`.
- Stable identity: `com.subgen.nativeapp.stable`, version code 18.

The release must be signed offline with the existing private update key for
installation over an earlier stable version. The key and its password are not
included in this source or sent to CI. Follow `SIGNING-AND-UPDATES.md` and compare
certificate digests before distributing an update.

The neural model is installed by the app's existing model installer; it is not
embedded in this source archive. No new voice engine or model is required.

A successful build validates code and packaging. Samsung installation, playback,
listening quality and creation times require real-device acceptance.
