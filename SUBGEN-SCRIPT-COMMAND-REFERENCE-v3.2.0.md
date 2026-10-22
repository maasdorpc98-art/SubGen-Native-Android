# SubGen Script Command Reference — v3.2.0

**Status:** v3.2.0 preserves the v3.1.1 acoustic contract. No new acoustic commands were introduced by HYPER production analysis. The v3.1.0 APK does **not** support `[profile]` or `[phase]` commands; do not use this new syntax with v3.1.0. The HYPER hypnotist writes the narration and chooses its sound. SubGen reads only the finished script and does not rewrite its story or interpret its goals.

## 1. Script-only communication contract

Three different kinds of text must not be confused:

1. **Narration, actually spoken:** `Relax Deeply`, `Relax your System Deeply`, `State of Mind` and `the Void` are ordinary spoken words. They trigger **no software actions**. Their pronunciation/delivery depends only on the actual words and explicit commands.
2. **Runtime bracket commands:** `[pause 2]`, `[profile MASTER_HYPNOTIST]`, `[phase DEEPENER]`, `[tempo 0.90]` and `[volume -2dB]` create **nonspoken** events or adjust actual supported acoustic parameters.
3. **HYPER writing-plan instructions:** `@PERSONALITY(MASTER_HYPNOTIST)`, `@BESTOW(...)`, `@CEMENT(...)` and other `@...` directives are **NOT valid in the audio editor**. They cause a visible render-validation error rather than being spoken or secretly executed. Keep them in the writing conversation, not the finished narrator script.

The parser accepts commands inline, adjacent to text, and case-insensitively. It never adds a pause simply because a section changes or the author uses a newline. An explicit `[pause N]` inserts sample-counted digital silence; punctuation can add **model-generated** cadence but has no exact numerical pause length.

## 2. Canonical runtime syntax

| Code | Values and meaning | Persistence / reset |
| --- | --- | --- |
| `[profile MASTER_HYPNOTIST]` | Selects a *fixed acoustic* preset. Other supported IDs below. Does **not** select a different neural voice or emotion. Works even if the app's Voice Direction switch is off. | Persists until another `[profile ...]` or `[profile reset]`; new script starts with UI selection. |
| `[profile reset]` | Removes script-selected profile. The UI-selected profile applies if Voice Direction is enabled; otherwise no profile applies. | Does **not** change phase, legacy style, tempo override or volume override. |
| `[phase INDUCTION]` | Selects an **acoustic phase** from nine allowed IDs below. Independent of `[SECTION: ...]`. | Persists through every section name until next `[phase]` or `[phase reset]`. |
| `[phase reset]` | Clears explicit phase to **GLOBAL/general profile defaults**, not a prior chapter's phase. | Remains in the new script's explicit-phase mode; does not reactivate legacy chapter mapping. |
| `[SECTION: CUSTOM NAME]` | Purely informational chapter marker; any nonempty descriptive name, including `STATE OF MIND`, `VOID ENTRY`, `INSTINCTIVE MASTERY` and `FRACTIONATION TEST`. `[stage NAME]` is a legacy alias. | Sets chapter label only for new explicit-profile/phase scripts, never voice delivery. No implicit silence. |
| `[tempo 0.90]` | Sets a **relative multiplier** on the app's global Kokoro speed. Allowed `0.65` through `1.50`, decimal point (`.`). The resulting actual native speed must be `0.60` through `1.40`. It overrides legacy style-driven speed and phase-driven speed. | Persists through new phases, sections and `[NORMAL]` until `[tempo reset]`. |
| `[tempo reset]` | Releases the explicit multiplier; resolve native speed from legacy mode if active, otherwise profile/phase default or 1.00×. | Does not change `[volume]`, `[phase]`, `[profile]` or legacy mode. |
| `[volume -2dB]` | PCM amplitude adjustment relative to the app's global direction gain. Allowed `-18dB` through `+6dB` with an optional `+`, decimal values supported. Total resolved gain must remain between −30 and +6 dB; excessive sample peak/clipping blocks output. | Replaces phase gain while active. Persists through sections, phase/profile changes until `[volume reset]`. |
| `[volume reset]` | Restores current acoustic phase/profile gain plus the app-global direction gain (if enabled). | Does not affect tempo or legacy mode. |
| `[SOFT]` | Legacy Kokoro rate preset 0.94× (approximation, not real emotional softness). | Persists until another legacy style. |
| `[SLOW]` | Legacy Kokoro rate preset 0.82×. | Persists until another legacy style. |
| `[WHISPER]` | Legacy Kokoro rate preset 0.90×. **Not a true whisper.** | Persists until another legacy style. |
| `[NORMAL]` | Removes legacy style speed multiplier; profile/phase rate applies unless an explicit tempo is active. | Does not reset explicit tempo or volume. |
| `[pause 1.5]` | Adds exactly 1.5 seconds of spoken-track silence; range **0–3600** seconds including 0. | One-time event; does not change any style. Legacy aliases `[PAUSE: 2]`, `[PAUSE 2]`, `[pause: 2]` are accepted. |
| `[breathe]` | Inserts the app-configured breathing pause (default 5 seconds). | One-time event. |
| `[paragraph]` | Inserts the app-configured paragraph pause (default 3 seconds). | One-time event. |

To speak a literal bracket command instead of executing it, escape its opening bracket, e.g. `\[pause 2]`. The backslash itself is removed, leaving the literal phrase for synthesis. Ordinary brackets such as `[the door opens]` remain narration; malformed command-like brackets cause a render-validation error.

## 3. Profile IDs (exact code names)

`GENTLE_GUIDE`, `CONFIDENT_AUTHORITY`, `MASTER_HYPNOTIST`, `COMMANDING_DRAMATIC`, `INTELLIGENT_INSTRUCTOR`.

These names describe **writing personality and documented fixed speed/gain presets** only. They do not cause Kokoro to produce distinct voice characters, real emotional intention, genuine whisper, or inferred emphasis. The app's separately selected Kokoro voice is unchanged by `[profile ...]`.

Acoustic preset defaults by profile/phase (relative tempo ×, relative dB):

| Profile | Arrival / Induction | Deepener / Sleep | Wake | Other phases / general |
| --- | --- | --- | --- | --- |
| Gentle Guide | 0.94×, −2 dB | 0.88×, −3 dB | 1.00×, −1 dB | 0.96×, −1 dB |
| Confident Authority | 1.00×, 0 dB | 0.91×, −1 dB | 1.02×, 0 dB | 1.00×, 0 dB |
| Master Hypnotist | 0.96×, −1 dB | 0.88×, −2 dB | 1.04×, 0 dB | 1.00×, 0 dB |
| Commanding / Dramatic | 1.00×, 0 dB | 0.89×, −1 dB | 1.02×, +1 dB | 1.00×, 0 dB |
| Intelligent Instructor | 1.00×, 0 dB | 0.97×, −1 dB | 1.04×, 0 dB | 1.00×, 0 dB |

The profiles intentionally share the same neural voice and cannot by themselves ensure a different perceived character. A profile selected **in the script** uses its built-in table rather than the custom section-rule overrides saved for another profile in the UI. If the app's overall direction gain control is enabled, it still adds to script settings.

## 4. Acoustic phase IDs (exact)

`ARRIVAL`, `INDUCTION`, `DEEPENER`, `FRACTIONATION`, `SUGGESTIONS`, `REINFORCEMENT`, `INTEGRATION`, `WAKE`, `SLEEP`.

Examples: `[phase DEEPENER]`, `[phase SUGGESTIONS]`, `[phase WAKE]`. An unknown phase such as `[phase VOID ENTRY]` is **invalid**: use `[SECTION: VOID ENTRY][phase DEEPENER]` instead. This keeps new custom section names arbitrary while phase behavior is deterministic.

**Legacy compatibility:** A script using **neither `[profile]` nor `[phase]`** keeps v3.1.0's earlier automatic mapping of familiar section names to acoustic phases *when the UI profile is enabled*. Custom sections then retain v3.1.0's old remembered/fallback phase behavior. As soon as the script encounters its first **valid** `[profile]` or `[phase]`, it switches to explicit-phase behavior from that point forward. `[profile reset]` and `[phase reset]` **do not return to legacy auto-mapping**. For completely deterministic HYPER scripts, place `[profile ...][phase ...]` at the start.

## 5. Definitive precedence and calculation

For each successive spoken segment, SubGen resolves the following, **separately**:

1. **Neural voice ID:** chosen in the app. `[profile]` does not change it.
2. **Profile:** explicit script-selected profile, if active; otherwise UI profile if UI Voice Direction is enabled; otherwise no profile.
3. **Acoustic phase:** latest explicit `[phase ...]`, if any; otherwise general/profile baseline in explicit-phase mode. For legacy scripts only, old known-section mapping is retained.
4. **Native speech speed:** global slider value × (**explicit `[tempo]`**, if set; else legacy `SLOW`/`SOFT`/`WHISPER` rate if active; else acoustic-phase profile multiplier if a profile is active; else 1.00). An explicit tempo does **not** multiply again by `[SLOW]` or section speed. The native speed limit is checked; the app does not secretly accelerate to hit a target duration.
5. **Post-synthesis phrase gain:** (app-global direction dB if enabled, else 0) **plus** (explicit `[volume]` dB if set, else acoustic phase/profile gain when active, else 0). An inline volume replaces *phase gain*, rather than adding to it. Gain has short in-frame ramps and cannot introduce extra playback time.
6. **Coded silence:** `[pause N]`, `[breathe]`, `[paragraph]` add their exact configured frames. Profile/phase/section events add **zero** frames.
7. **Final export processing:** existing output gain, peak ceiling, channel layout, binaural and sample-rate settings remain separate user-configured production controls.

Each new control persists until its own reset/change. `[NORMAL]` resets **only** the legacy style. `[tempo reset]` resets **only** explicit tempo. `[volume reset]` resets **only** explicit volume. `[phase reset]` resets **only** explicit acoustic phase. `[profile reset]` resets **only** script-selected profile. Section labels and authored trigger phrases never reset them.

## 6. Complete HYPER example — script-only, no briefing required

```text
[profile MASTER_HYPNOTIST][phase INDUCTION][SECTION: ARRIVAL]
Make yourself comfortable... and allow your breathing to settle naturally.
[pause 2]
[SECTION: STATE OF MIND]
Relax Deeply.[pause 2] And notice the comfort spreading.
[SECTION: VOID ENTRY][phase DEEPENER]
[tempo 0.85][volume -2dB]Relax your System Deeply.[pause 4]
[tempo reset][volume reset]You can imagine the Void as a place for reflection and understanding.
[SECTION: ACTIVATION][phase SUGGESTIONS][NORMAL]
Your attention becomes clearer as you practice.
[SECTION: INTEGRATION][phase INTEGRATION]
Notice what you have learned.[pause 3]
[SECTION: WAKE][phase WAKE]
One... two... and now you become comfortably alert and fully aware of the room.
[phase reset][profile reset]
```

No `@...` planner text appears in the finished narration. Neither trigger phrase nor narrative destination is parsed as a runtime opcode.

## 7. Validation, prediction and preview

- A strict render gate blocks malformed production codes, unknown profile or phase IDs, invalid pause/tempo/volume parameters and pasted HYPER `@...` planning directives. Never expect unrecognized commands to be read aloud safely.
- **Pre-render duration:** an estimate using each resolved effective speech speed and the 145-WPM heuristic (with a stated uncertainty range) plus exact configured digital pauses; this is **not a guarantee** of Kokoro prosody or timing.
- **After rendering:** authoritative speech/export durations derive from actual decoded PCM/WAV sample frames. Render processing time is not playback duration. Gain adjustments do not change estimated playback time.
- **Inspector:** the existing full-script timeline lists each speech block with its effective speed, gain, acoustic phase and any script-selected profile, plus profile/phase event rows and precise pauses. In this release it is a text timeline, not yet cursor-linked highlighting.
- **Preview:** directed preview follows the same parsing and acoustic resolution. Neutral A/B removes direction, legacy mode, inline speed and gain for comparison while keeping spoken words and pauses; it uses the same Kokoro voice and global speed.
- **Actual listening validation remains required.** No emulator/CI test proves genuine expressive acting, perfect joining at every code boundary, or final naturalness of 20–30-minute sessions. Verify on Samsung before declaring the narration fully accepted.

## 8. Unsupported commands and future work

`[delivery AUTHORITATIVE]`, `[emotion ...]`, `[pitch ...]`, `[gain ...]`, `[voice NAME]`, `[speed ...]`, `[repeat ...]`, and any unlisted production directive are **not approved runtime commands**. The parser rejects recognized unsupported production-style spellings; do not put them in finished scripts. A new TTS backend, if ever desired for true expressive prosody, requires a separate technical evaluation and explicit approval.

Remaining development work: real Kokoro device listening and long-session measurements; voice/speed/punctuation-calibrated runtime estimates; deeper source-range UI inspector, saved versioned HYPER plans, library and script workflows, selective rerender, richer audio audition, and optional separate audio stems. **No backend replacement is included.**

## v3.2 production-analysis notes

The optional HYPER panel observes the same parser/resolver. Recording mode, session cue registration, rule packs, editor reviews and reports are separate metadata, never spoken commands. Section labels and nonspoken acoustic controls are excluded from cue matching. A cue can span permitted inline controls but never a programmed pause, including `[pause 0]`.

There is no established inline comment command. HTML/code comments pasted into narration can be spoken; production notes belong outside the finished script. The audit flags this ambiguity rather than silently stripping narration.

Unclosed/oversized malformed production-style directives and unsupported emotion/pitch/voice/repeat directives are rejected clearly. Invalid effective combined speed is displayed as a validation error rather than crashing the live timeline. This does not change the meaning or persistence of valid acoustic commands.

Pre-render times remain heuristic. The actual WAV frame count is authoritative. The supplied real neural short fixtures demonstrate that the estimate range is not a guarantee.
