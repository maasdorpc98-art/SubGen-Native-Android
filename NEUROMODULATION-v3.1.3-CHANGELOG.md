# Sub Gen Native v3.1.3 — Full A / B / A+B NeuroModulation (test candidate)

## Full, actual functionality
- Three interactive native modes: A direct signed 96kHz mono PCM32; B genuine reference-derived three-tone predictor + XZ/LZMA correction + stereo 2048 OFDM / 22-bit QAM with 40Hz-positive AM, signed 96kHz stereo PCM32; and sequential A+B.
- Independent recovery from **saved PCM32 WAV** against the exact 24kHz mono normalized source, checked with PCM hash and source sample verification before output is marked ready.
- Export protected masters for A and B, JSON manifests including B recovery `decodeScale`, normalized source and separate listening/mixer copies.
- Every UI control remains clickable: setting sliders accept touch without pre-enabling a custom checkbox; moving a slider selects custom mode. Reset/reference, mode selection, generate, cancel, refresh, preview, export and Studio transfers provide action/validation rather than inert controls.
- Mixer copies remain separately generated, clearly non-reversible, and can be assigned to Studio primary or secondary affirmations. Export of large files runs off the UI thread to avoid UI freezing.
- The output section refreshes automatically after successful verification. Prior original Studio slider/preset maps and native Kokoro/Voice Direction paths remain untouched.
- Version code 13; stable release stays `com.subgen.nativeapp.stable`. Debug variant has suffix `.abtest` so an installable CI debug test APK cannot overwrite the signed stable build.

## Limitations and acceptance
- The B waveform follows the reference codec structure and independently decodes the normalized source; byte-for-byte parity with the original Python experiment **is not established**. The Python original uses NumPy/FFT/resampling settings that may differ in rounding and frame padding.
- Long renders on Samsung SM-G991B, B gain seam behavior, listening quality, duration prediction, cancellation under thermal stress and Studio mixing after an A+B render **need actual-device tests**. Version B is computationally heavy.
- 40 Hz AM does not establish brain entrainment, hypnosis, subliminal language perception or durable learning.
- The separately signed private stable update key is NOT stored in the GitHub repository. CI produces a test/debug APK and an unsigned release; stable in-place update requires a trusted release-signing step with the previous private certificate.