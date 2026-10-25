# Sub Gen Native v3.1.2 — Third NeuroModulation section (initial integration)

## Implemented, native Android

- Third top-level **≈ NEUROMOD** tab, native Views; Hypnosis and Studio remain in place, with the same original slider/preset set.
- Import affirmation recording from local Android Documents; compatible compressed formats are decoded by Android MediaCodec; normalized 24 kHz mono PCM16 WAV is made independently of the original file.
- **Original Version A direct integer amplitude modulation**: 96 kHz signed PCM32 master, 40 Hz, 90% reference depth, 60 seconds default, adjustable 0.5–100 Hz, 0–98% depth, 1–180s output, and source-capacity guard without silent trimming.
- Independent **complete digital recovery** of the normalized PCM16 source from the written signed PCM32 master, verified sample by sample. The original source file is never overwritten.
- Distinct 96 kHz PCM16 listening copy and separate, peak-target -18 dBFS PCM16 mixer edition; listening / mixer files are **not digitally reversible**.
- Explicit export of protected PCM32 master, mixer-ready PCM16, normalized source, and JSON verification report via Android Storage Access Framework.
- Native foreground audio encoding service with safe cancellation and bounded memory-mapped WAV reads, progress/status in the dedicated tab.
- Explicit one-tap handoff of the mixer-ready edition to **Studio Primary** or **Studio Secondary** affirmation slots. The protected PCM32 master is never sent to the Studio mixer.
- Keep package ID `com.subgen.nativeapp.stable` and original stable signing identity; versionCode 12.

## Not yet implemented

- **Original Version B** predictor + LZMA residual + stereo OFDM/22-bit QAM path. The supplied Python original has NOT been replaced with a different algorithm. Native Android B and simultaneous A+B controls are visibly unavailable until a separately verified bit-exact digital recovery implementation exists.
- Full A/B fixture comparison against an archived Experiment 001 source/master, and Samsung user acceptance tests.
- Native NeuroModulation input source duration currently limited to 12 minutes; WAV input up to 96kHz PCM16, and Android MediaCodec supported formats.

## Verification

`bash scripts/run-core-tests.sh` runs Version A 24k mono, 44.1k stereo, 48k mono, exact encoded-master recovery, protected master file length, separate mixer edition length and invalid/too-short output checks. Android CI additionally compiles and signs a debug APK, and builds unsigned stable release for offline signing.

## Safety / interpretation

40 Hz here is waveform amplitude-modulation rate. Exact digital recovery holds for the unmodified PCM32 file, not for listening editions, submixed output, headphones, physical acoustics or the listener. No subliminal learning or automatic speech comprehension is demonstrated.