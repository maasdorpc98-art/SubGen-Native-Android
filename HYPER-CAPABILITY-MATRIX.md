# Audio support — feature versus reality

| Requirement | Support | Implementation / limitation |
| --- | --- | --- |
| HYPER `@...` plans | Writing only | Flag in narration; no TTS side effect or accidental speech; separate plan editor remains future |
| Gentle Guide / Authority / Master / Dramatic / Instructor | Informational writing profile | Each optional profile also selects *documented* acoustic tempo/gain defaults; not genuine emotional acting |
| Kokoro voice selection | Genuine | Existing four offline native Kokoro voices unchanged |
| Global 1.00× speech speed | Genuine | Existing Kokoro native `GenerationConfig.speed`; preserve controls and saved settings |
| `[tempo 0.90]` / reset | Genuine rate parameter | Per-segment Kokoro speed, no independent DSP time stretching or guaranteed constant pitch |
| `[volume -2dB]` / reset | Genuine PCM gain | Per-segment signed 16-bit gain, tested amplitude, short ramp, clip avoidance |
| Section rate/gain defaults | Supported approximation | Opt-in presets / manually set overrides, do not add spoken text or pause time |
| Legacy `[SOFT]` / `[SLOW]` | Approximation | Existing 0.94×/0.82× native-rate modes; exclusive of explicit tempo |
| Legacy `[WHISPER]` | **Approximation** | Existing 0.90× rate only. Not neural whisper or breathy voice |
| True whisper / authoritative emotion / dynamic pitch / emotional timbre | **Unsupported** | Requires validated alternate Kokoro controls or different neural voice backend; no backend changed |
| `[pause 0.1]`, breathe/paragraph | Genuine digital silence | 24 kHz frame-aligned, no auto hidden pause at section boundaries |
| Pre-render speech duration | Approximate | Per resolved speech segment at 145-WPM baseline with uncertainty, not yet empirically calibrated |
| Last completed output duration | Genuine | Derived from signed WAV frame count / sample rate; displayed for same script |
| Original mixer/8D/binaural/21 presets | Retained | Not redesigned, all production paths preserve their original implementation |
| Source script preservation | Retained | No rewriter, phrase trigger activation or forced script sequence |

No user script text is uploaded to a server by this update.