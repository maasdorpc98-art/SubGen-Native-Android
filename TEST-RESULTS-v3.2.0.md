# SubGen v3.2.0 verification record

## Scope

Baseline v3.1.3 source matches the contents of the supplied source archive and GitHub commit `2d7c0a66e528475496aecb846d463dc454b1a1d1`. Source package archive metadata had an earlier commit comment; file contents were compared directly rather than relying on that comment.

## Completed automated checks

- Existing `scripts/run-core-tests.sh`: passed locally. This covers the original parser, audio quality, PCM assembly, output formats, worker policy, voice speed, acoustic contract, NeuroModulation A and B recovery paths.
- `scripts/run-hyper-tests.sh`: 23 HYPER production test groups, protected-engine checks, actual XZ Version B recovery and storage integration checks. See exact logs in the evidence package and final CI record.
- Baseline differential fixtures compare parser nodes, segmentation, resolved speed/gain and speech-cache inputs for fixed scripts plus 100 seeded mixed-control scripts.
- Fourteen protected engine/control/asset files remain byte-identical to v3.1.3. Studio retains 28 slider mappings and 21 factory presets.
- Source map tests cover UTF-16 positions, CRLF, duplicate phrases, punctuation, case, inline controls, explicit pauses and section-label exclusion.
- Rule tests cover exact tier boundaries, natural-sleep overrides, conservative inference, session cue authorization and history.
- Approval tests cover source/rule/mode/cue/acoustic/app-version changes, output changes and same-byte new renders. Parser errors cannot be approved away. Loop audio review requires recorded junction listening.
- WAV tests cover PCM8/16/24/32, float32/64, mono/stereo, silence, clicks, rail samples, malformed/truncated/nonfinite inputs and a sparse 1.6 GB size-limit fixture.
- Loop tests compare every concatenated PCM byte to the unchanged source and verify exact 3× frame count. Both junctions repeat the same original boundary.

The legacy B unit suite uses a test compression stub. The additional real-XZ test explicitly compiles and runs with the production `org.tukaani:xz:1.10` library; it verifies saved-master recovery. Python-reference byte-for-byte equivalence remains outside the established baseline evidence and is not newly claimed.

## Real neural evidence

An optional desktop harness uses the original `kokoro-multi-lang-v1_0` FP32 model, sherpa-onnx 1.13.8 CPU backend and the same voice IDs: ORION 16, ROWAN 14, LUNA 2, SERENA 3. It exercises three speed values per voice, a directed/neutral script and a synthetic long session. Neural speech checkpoints are assembled through the original Kotlin `WavIO` and `OutputFormat` code.

The real desktop inference and original Kotlin assembly checks passed. The long synthetic recording is **22 min 54 s**, not synthesis processing time. All four supported voices had monotonically shorter output at native speeds 0.88×, 1.00× and 1.04×. 25 unique neural calls produced the corpus; repeated long-script blocks reused their exact generated checkpoints.

| Neural fixture | Frames | Measured playback | Peak dBFS | Rail samples |
|---|---:|---:|---:|---:|
| directed | 370,800 | 15.450 s | -9.21 | 0 |
| neutral | 364,800 | 15.200 s | -7.76 | 0 |
| long-session | 32,976,000 | 1374.000 s | -8.73 | 0 |

Five storage integration groups also passed, including on-disk report/review round trips, correct WAV association, new-render invalidation, rule history and authorization revocation.

Backend diagnostic: the unchanged baseline passes `silence_scale=0.0`. sherpa-onnx 1.13.8 logs that zero is outside its [0.01, 10] range and skips silence scaling. This release preserves that baseline behavior and does not claim model-generated internal silence was removed.

The exact final frame counts, durations, peak/RMS/rail figures, programmed silence, model hash and inference results are recorded in the neural evidence JSON files. Those results must be read as desktop backend/PCM evidence, not Android JNI, Samsung listening acceptance or calibrated duration prediction.

The evidence also includes explicitly produced directed/neutral comparison copies at matched overall RMS −25 dBFS. These are separate listening aids; the originals are unchanged. Overall RMS matching is not guaranteed perceptual loudness matching.

The short directed fixture measured 15.45 s against an 11.37 s heuristic estimate, outside that fixture's stated heuristic upper range. The long fixture measured 1374.0 s against approximately 1300.76 s predicted (about 5.6% longer). These results demonstrate why per-voice calibration remains deferred and why measured WAV frames are authoritative.

## Android build record

The first build caught a syntax error in the new cue-editor UI. It was corrected before packaging. The subsequent build compiled the native debug APK and passed signature/arm64-runtime verification. Final release-candidate build status and exact artifact identities are recorded in the final build manifest accompanying delivery.

The original stable signing backup was subsequently recovered and its certificate matched against the signed v3.1.3 APK. The final release is signed offline with that same key. APK signature, package and version checks are recorded in the final identity manifest. Actual Samsung installation is not claimed.

## Not performed

- Physical Samsung installation, UI interaction, playback, long-run thermal/background behavior, export and upgrade tests.
- Listening approval for pronunciation, naturalness, prosody, command-boundary seams or loop narrative continuity.
- Real Sessions 001 Foundation/Sleep, 004 loop and 006 A/B acceptance; the supplied materials contain the specification, not those actual source scripts/audio.
- Genuine expressive neural acting or a backend replacement.

Synthetic fixture results and successful CI compilation do not certify these remaining criteria.
