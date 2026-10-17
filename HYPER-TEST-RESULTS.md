# HYPER Voice v3.1.0 verification

## Locally executed Kotlin JVM tests
- `test_parser.kt`: existing compatibility fixtures — PASS.
- `test_hyper_voice.kt`: embedded and adjacent codes, aliases, decimal pauses, resets, escaped literal brackets, malformed/HYPER plan detection, cue phrases, legacy and explicit speed, section/default gain, volume-invariant duration — PASS.
- `test_audio.kt`: checkpoint WAV and mixed PCM output — PASS.
- `test_voice_pcm.kt`: two exact digital pauses, fixed output frame duration, 24 kHz stereo export, measured attenuation −6dB, measured amplification +3dB with no clipping — PASS.

## Needed before declaring device-level voice/prosody accepted
- GitHub Actions full Android build and signature verification; verify APK installed over stable v3.0.9 without losing user work.
- On Samsung SM-G991B audition each voice profile and A/B phrase, inspect real Kokoro pronunciation, punctuation, seam quality, dynamic volume, cache, 20-minute long render and saved-file duration.
- Genuinely expressive hypnotist acting, pitch-preserving stretch, true whisper and corrected neural voice prosody are **not verified or supported** by current backend.