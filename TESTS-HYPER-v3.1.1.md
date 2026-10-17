# v3.1.1 — Test scope and acceptance results

## Source baseline
SubGen 3.1.0 native Android source, stable package `com.subgen.nativeapp.stable`; no WebView. New changes isolate acoustic command parsing, delivery resolution, script inspector/preview and cache key in a feature branch; no merge into main.

## Automated tests
- Standalone Kotlin parser, native audio-quality, WAV format, frame-accurate pauses, binaural/stereo verification.
- Full HYPER v3.1.0 regression suite: existing phrase overrides, soft/slow/whisper legacy rates, strict malformed command rendering block.
- v3.1.1 script acoustic contract fixture: all five profile IDs, nine phase IDs, unknown tag rejection, independent custom section labels, continuous-text trigger cues, profile/phase/tempo/volume reset semantics, UI fallback and previous known-section compatibility, measured resolve speed and gain, gain-only duration invariance.
- Static source, original Studio parity, FP32 JNI workflow, stable update identity checks.
- CI release/debug Android build and signature verification; offline same-certificate signing of release.

## Not completed in CI
- Genuine Kokoro model preview and A/B listening on Samsung SM-G991B, complete 20–30-minute narration with the user's intended natural delivery, hearing checks at phrase boundary seams, longer-session memory/thermal stability, individual profile subjective comparison.
- Learned per-voice/punctuation duration calibration. Predictions remain heuristics; actual media frames authoritative.

## No backend change
Kokoro FP32 unchanged. Profiles use native speed and post-synthesis PCM gain only; they are not true emotion or whisper presets.