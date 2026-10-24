# SubGen Native v3.2.2 — High Quality outputs

Adds an optional **HIGH QUALITY · 24-BIT WAV** switch in Studio, Hypnosis and NeuroMod.
Existing Standard rendering remains available. No neural voice/model replacement,
cloud processing or new audio effects are introduced.

## Hypnosis

- High Quality keeps Kokoro's original float samples in separate atomic checkpoints.
- Gain changes and assembly use float PCM; export performs one final PCM24 conversion.
- Existing script words, speed/profile/phase resolution, pauses and binaural settings are retained.
- Native 24 kHz avoids an unnecessary conversion. Other existing output rates remain selectable.
- Gain/headroom protection uses one uniform reduction, rather than clipping individual peaks.
- First HQ use needs its own voice cache because original PCM16 checkpoints cannot recover lost precision.
- Subsequent renders reuse matching float voice blocks. Output level, channels or sample rate changes do not invalidate them.
- Voice previews also use PCM24 when HQ is enabled.

## Studio

- Reads PCM16, PCM24, PCM32 integer and float32 WAV without early reduction to PCM16.
- Compressed sources use the existing device decoder; decoder output may still be PCM16.
- Prepares each source once for its rate/speed using a fixed polyphase sinc filter and caches the result.
- Mixing uses float precision and one final PCM24 conversion at the selected 32/44.1/48 kHz rate.
- Peak protection applies uniform gain to preserve track balance; actual reduction is recorded.
- Avoids repeated conversion on every loop and precomputes oscillator/gain values.
- Fixes quick-duration buttons writing the wrong preference key.

## NeuroModulation

- Original A and B protected encoders, PCM32 masters and independent source-recovery verification are unchanged.
- HQ source preparation accepts higher-precision WAVs and converts once to the required 24 kHz mono PCM16 payload.
- Generates independent 96 kHz mono PCM24 listening/mixer copies directly from the protected masters.
- These copies are not digitally recoverable. They are not advertised as a higher-resolution source payload.
- Reuses normalized inputs and verified masters when source/configuration match; master checksum must match the previous verification record.
- A new or damaged master is encoded and independently verified again.
- Sending a PCM24 copy to Studio selects Studio HQ to preserve its precision.

## Reliability and cost

- Rendering snapshots settings so editing the UI cannot change an ongoing job.
- Stops overlapping Studio/Hypnosis and NeuroMod jobs through the app's normal controls.
- Fresh app processes clear stale busy flags after process death; completed caches/outputs remain available.
- Atomic writes retain prior completed outputs on failure/cancellation; bounded buffers avoid holding full sessions in RAM.
- PCM24 WAVs use 50% more sample-data storage than PCM16 at the same rate/channel count. Float intermediates and prepared-source caches also require free space; preflight guards fail clearly when storage is insufficient.
- No target-phone speed guarantee: extra conversion cost and cached speedups need Samsung measurement.
- Increasing bit depth or sample rate cannot recreate detail absent from the source. Audible differences depend on source material and processing.

## Build identity

- Package: `com.subgen.nativeapp.stable`
- Version code: 16
- Version name: `3.2.2-high-quality`
- Build/test/signing status is recorded in `RELEASE-VERIFICATION-v3.2.2.json` when available.
