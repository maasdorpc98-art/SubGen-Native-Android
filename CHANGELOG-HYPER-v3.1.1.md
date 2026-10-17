# SubGen v3.1.1 — Script-only Acoustic Contract

## Added
- `[profile MASTER_HYPNOTIST]`, `[profile GENTLE_GUIDE]`, `[profile CONFIDENT_AUTHORITY]`, `[profile COMMANDING_DRAMATIC]`, `[profile INTELLIGENT_INSTRUCTOR]`, and `[profile reset]`. Script-selected acoustic profile overrides the UI profile while active, even if the Voice Direction UI switch is off.
- Independent `[phase ARRIVAL|INDUCTION|DEEPENER|FRACTIONATION|SUGGESTIONS|REINFORCEMENT|INTEGRATION|WAKE|SLEEP]` and `[phase reset]` acoustic categories, without changing the script's descriptive `[SECTION: ...]` label.
- Deterministic per-segment inspector rows showing resolved effective speed/gain and active acoustic phase/profile; profile/phase change events displayed on script timeline.
- New automated profile/phase parser tests, reset/precedence tests, custom section stability, invalid/unsupported command gates, cue non-trigger tests, duration invariants and legacy mapping checks.
- Published versioned `SUBGEN-SCRIPT-COMMAND-REFERENCE-v3.1.1.md` for the HYPER hypnotist.

## Corrected
- Speech-cache key now uses resolved native speed whenever a script explicitly selects a profile. Preserves legacy cached-audio identity for scripts without direction features.
- Neutral audition ignores script-selected profile, phase-driven speed, legacy speed modes, inline tempo and gain, retaining the same voice, words and pauses.
- Fix descriptive custom SECTION label tracking while preserving v3.1.0 legacy acoustic mapping for old scripts.
- Stable Android versionCode increment 10 → 11, same application ID and original private signing certificate.

## Intentionally unchanged
- Kokoro FP32 native voice backend, `1.00×` global default, original 21 Studio presets, original visual layout, WAV and binaural/8D paths, existing legacy commands and saved data. No voice emotion controls are claimed.
- Existing old-script section mappings, unless new explicit-profile/phase syntax is used. This is necessary to avoid audible regressions for legacy projects.

## Deferred (not silently omitted)
- Listening-quality signoff on Samsung (preview, long 20–30m render, multi-phase seams, code non-speech, pauses, peak/loudness). Existing CPU inference speed is not improved here.
- Per-voice/speed/punctuation speech calibration, advanced GUI source span inspector, versioned HYPER authoring library, selective re-render, emotional/neural prosody backend study (only on explicit approval).