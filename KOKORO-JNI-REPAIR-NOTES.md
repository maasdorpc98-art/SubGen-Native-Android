# Sub Gen Native v3.0.3 — Kokoro JNI callback crash repair (test)

## What the diagnostic report proved
- Android classified the v3.0.2 failure as NATIVE_CRASH, status 6 (SIGABRT).
- Kokoro initialized successfully. The crash occurred at the first speech generation (30 characters, voice sid=3, threads=1).
- System memory was not reported low.
- Previous crash-loop handling worked; there was no automatic render restart.

## Root cause addressed
Kokoro speech generation previously used sherpa-onnx v1.13.8's generateWithConfigAndCallback().
Sherpa issue #3492 documents Android SIGABRT in the JNI callback path; same library source version retains a callback capture that may be invalid on a different thread.
The v3.0.3 repair uses generateWithConfig() without a callback. In the upstream v1.13.8 Kotlin/JNI wrapper this calls native Generate(text, config, nullptr).

## Preserved
- Native Android interface, all 28 slider definitions, all 21 preset value maps, all five Studio sections.
- Voice selection, speed, silence scale, disk speech checkpoints and resumption controls.
- Previous anti-crash-loop protection and diagnostic reports.

## Trade-off
Cancellation can interrupt *between* 160-character speech blocks but not while one native block is running. No audio quality reduction is intended.

## Validation
Host source/parity/audio tests and Android APK compile/signature checks do not execute ONNX inference on the user's device. Treat this as a test candidate, not a confirmed cure until a speech block completes on the phone.
The missing Hypnosis Studio UI features remain intentionally out of scope.

## Device acceptance
1. Install the separate v3.0.3 test app.
2. Use a 1–2 sentence script and the same voice.
3. Check whether at least one speech block generates successfully; then expand to a larger test.
4. Export the local JSON report if it crashes again.
