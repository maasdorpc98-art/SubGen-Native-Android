# Sub Gen Native 3.1.0 — HYPER Voice Foundations

## Built in this update
- Continuous-text inline lexer for `[pause N]`, historical `[PAUSE: N]`, `[SOFT]`, `[SLOW]`, `[WHISPER]`, `[NORMAL]`, `[breathe]`, `[paragraph]`, and nonspoken `[SECTION: NAME]` / `[stage NAME]` at arbitrary offsets.
- Strict invalid-command render gate, literal `\[` escape, authoring `@...` warnings, decimal pause range 0–3600 seconds. Unknown ordinary narrative brackets stay spoken.
- Optional Voice Direction UI with five named **writing personality labels** and bounded, actual acoustic approximations for section tempo/gain. Existing presets and Kokoro engine unchanged.
- Native post-TTS per-phrase PCM volume (+3/-6 dB tested), in-frame short gain ramps, and clipping rejection. No audio-length modification from amplitude changes.
- Authoritative inline `[tempo 0.90]`, `[tempo reset]`, `[volume -2dB]` and `[volume reset]` syntax. No fake `[delivery AUTHORITATIVE]` support.
- Shared parser and speech resolution for editor estimates, previews and final render; preview phrase with neutral/directed A/B.
- Speech cache respects effective synthesized tempo, while final per-phrase gain does not unnecessarily invalidate synthesized PCM checkpoints.
- Estimated speech/silence with a heuristic uncertainty range, plus last actual export frame duration when script matches; preserve global voice speed 1.00x.
- V3.1.0 stable package `com.subgen.nativeapp.stable`, Android versionCode 10, requires the **same offline signing certificate** as v3.0.8/3.0.9.

## Compatibility and limitations
- All 21 original Studio presets, native production controls and saved files remain in place; profile processing is opt-in, previously stored profiles are not overwritten.
- Legacy whisper/soft are **Kokoro speech speed variations**, **not** genuine whisper synthesis, emotion conditioning, spectral warmth or dynamic pitch control.
- HYPER `@...` instructions belong in a separate writing plan. If pasted into narration, render is blocked with an actionable notice rather than silently executing/voicing them.
- Profiles only implement fixed section rate/gain; listener-perceived personality and prosody remain dependent on the text and Kokoro voice.
- Post-synthesis phrase volume changes have a fixed 8ms transition and cannot exceed PCM peak safely; clipping attempts fail instead of silently distorting.
- Pre-render time estimates are **not exact** neural waveform predictions; exact durations are measured from output frame counts, with the last matching script displayed.
- Voice profile library management, complete HYPER drafting UI, planner import/export, optional advanced stems, authoring journal, calibrated speech WPM, per-sentence waveform audition and genuine emotion/whisper are deferred (require further staged work and potentially a different voice backend).
- There is no device hardware benchmark showing faster Kokoro inference or guaranteed natural prosody. Listen to directed/neutral previews and verify on Samsung SM-G991B.

## Rollback
- v3.0.9 source branch and release are preserved. APK downgrade to a lower Android versionCode is normally blocked. Before switching backward, export scripts/settings; avoid clearing app data. Future updates retain the stable signing identity and private model storage.