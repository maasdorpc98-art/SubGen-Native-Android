#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
python3 scripts/check-source.py
K="app/src/main/java/com/subgen/nativeapp"
kotlinc "$K/ScriptParser.kt" scripts/test_parser.kt -include-runtime -d /tmp/subgen-native-parser-test.jar
java -jar /tmp/subgen-native-parser-test.jar
kotlinc "$K/AudioQuality.kt" scripts/test_audio_quality.kt -include-runtime -d /tmp/subgen-audio-quality-test.jar
java -jar /tmp/subgen-audio-quality-test.jar
kotlinc "$K/ScriptParser.kt" "$K/WavIO.kt" "$K/NativeSubliminal.kt" scripts/test_audio.kt tests/stubs/*.kt -include-runtime -d /tmp/subgen-native-audio-test.jar
java -jar /tmp/subgen-native-audio-test.jar
kotlinc "$K/ScriptParser.kt" "$K/WavIO.kt" scripts/test_fast_wav.kt tests/stubs/*.kt -include-runtime -d /tmp/subgen-native-fast-pcm-test.jar
java -jar /tmp/subgen-native-fast-pcm-test.jar
kotlinc "$K/ScriptParser.kt" "$K/ScriptTimeline.kt" "$K/VoiceDirection.kt" "$K/VoiceSpeed.kt" "$K/WavIO.kt" "$K/OutputFormat.kt" scripts/test_hypnosis_output.kt tests/stubs/*.kt -include-runtime -d /tmp/subgen-hyp-output-test.jar
java -jar /tmp/subgen-hyp-output-test.jar
kotlinc "$K/WorkerPolicy.kt" scripts/test_worker_policy.kt -include-runtime -d /tmp/subgen-worker-policy-test.jar
java -jar /tmp/subgen-worker-policy-test.jar
kotlinc "$K/VoiceSpeed.kt" scripts/test_voice_speed.kt -include-runtime -d /tmp/subgen-voice-speed-test.jar
java -jar /tmp/subgen-voice-speed-test.jar

kotlinc "$K/ScriptParser.kt" "$K/VoiceDirection.kt" "$K/ScriptTimeline.kt" "$K/VoiceSpeed.kt" scripts/test_hyper_voice.kt -include-runtime -d /tmp/subgen-hyper-voice-tests.jar
java -jar /tmp/subgen-hyper-voice-tests.jar

kotlinc "$K/ScriptParser.kt" "$K/WavIO.kt" scripts/test_voice_pcm.kt tests/stubs/*.kt -include-runtime -d /tmp/subgen-hyper-voice-pcm.jar
java -jar /tmp/subgen-hyper-voice-pcm.jar

kotlinc "$K/ScriptParser.kt" "$K/VoiceDirection.kt" "$K/ScriptTimeline.kt" "$K/VoiceSpeed.kt" scripts/test_script_acoustic_contract.kt -include-runtime -d /tmp/subgen-script-acoustic-contract.jar
java -jar /tmp/subgen-script-acoustic-contract.jar
# Native NeuroModulation A exact PCM recovery, normalizer, mixer-copy and limits.
kotlinc "$K/NeuroModulationA.kt" scripts/test_neuro_a.kt -include-runtime -d /tmp/subgen-neuro-a-test.jar
java -jar /tmp/subgen-neuro-a-test.jar