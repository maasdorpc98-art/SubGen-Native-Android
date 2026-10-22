# SubGen Native v3.2.0 — HYPER production integration

Development baseline: v3.1.3, source contents matching commit `2d7c0a66e528475496aecb846d463dc454b1a1d1`. Acoustic contract: v3.1.1. Android versionCode 14.

## Product behavior

Paste → Create → Export is preserved. The Hypnosis output panel has one optional button for HYPER production tools. No audit, classification, checklist, cue registration, loop listening or release approval is required to create or export an ordinary WAV.

Studio, Hypnosis and NeuroModulation A/B/A+B remain native. Fourteen protected engine/control/asset files are checked byte for byte against v3.1.3, including Kokoro, both modulators, PCM processing, voice resolution and Studio presets.

## Added

- Immutable source mapping emitted by the real parser: original UTF-16 offsets, line/column, exact narration and command spans. Default parsing returns unchanged valid synthesis nodes and cache inputs.
- Case-insensitive canonical universal cue audit; lexical boundaries; matching across supported inline controls, never across programmed pauses; duplicate and partial-match protection; source-linked contexts.
- Persistent, explicitly authorized session cue registry with session scope, function, associated experience, optional usage/phase expectations, configuration snapshots and audit history.
- Optional independent architecture/eye/ending/focus/familiarity classification. Conservative inference leaves uncertainty visible.
- Versioned local JSON rule packs, exact cue-frequency tier boundaries, sleep override, distribution/cluster/gap warnings and measured-duration reassessment.
- Planned first-response, deepener, renewed-depth, fractionation and ending analysis. Detected labels remain separate from human editorial acceptance.
- Native planned-structure graphic, source navigation, profile/phase/tempo/gain provenance and reset inspection, pause summaries and estimated timing.
- Read-only streaming WAV QA: PCM8/16/24/32 and IEEE float32/64; frame-derived duration, peak, rail samples, RMS, silence and boundary measurements. RMS is not LUFS.
- Original-tail/original-head junction auditions, exact three-cycle playback/export with RIFF size and free-space checks. No source modification, crossfade, gain correction or silence removal.
- Locally stored JSON and Markdown reports, immutable audit snapshots, review notes, rule exceptions and separate technical/text/audio/listener/release states.
- Approval binding to script/rules/mode/cue/acoustic identities, app version, render configuration, WAV hash and unique render ID. New same-byte renders still require new audio review. Loop audio approval explicitly requires recorded junction listening.
- Lightweight evidence capture after a successful existing render, without introducing an audit gate or moving synthesis work into the UI.

## Validation fixes

Malformed/unclosed production-style commands and unsupported `emotion`, `pitch`, `voice` and `repeat` directives are rejected clearly. These remain unsupported capabilities; no new acoustic syntax was added. Literal narration escaped with `\[` remains supported.

The live timeline now reports invalid effective acoustic settings instead of throwing while updating the editor. Valid commands retain their established precedence and persistence.

## Limitations and deferred work

- Existing grammar has no inline comment command. Do not paste HTML/code comments as narration. The audit warns about comment-like text because the legacy engine would speak it; nonspoken production notes remain separate metadata. Section labels and sidecar metadata are excluded correctly.
- Per-voice calibrated duration prediction and optional named production convenience presets are deferred. The existing 145-WPM prediction and uncertainty range remain explicit.
- No neural emotion, true whisper, pitch acting, automatic rewriting or voice-backend replacement.
- Zero-crossing/RMS boundary figures are coarse indicators, not perceptual or semantic seam approval.
- Actual Sessions 001/004/006 and Samsung listening/device acceptance need their real assets/device. Synthetic fixtures do not substitute for those tests.
- The original stable private signer is unavailable in this workspace. The installable test build uses a separate `.hypertest` application ID. The stable-ID release candidate remains unsigned until the established key is provided; no replacement stable key was created.
