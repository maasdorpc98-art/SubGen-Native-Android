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
