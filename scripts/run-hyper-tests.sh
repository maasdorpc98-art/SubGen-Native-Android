#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p test-results
python3 scripts/check-hyper-compatibility.py | tee test-results/COMPATIBILITY.txt
JSON_JAR="${SUBGEN_JSON_JAR:-/tmp/subgen-test-json-20240303.jar}"
if [[ ! -s "$JSON_JAR" ]]; then
  curl -fsSL --retry 3 https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar -o "$JSON_JAR"
fi
K=app/src/main/java/com/subgen/nativeapp
kotlinc "$K/ScriptParser.kt" "$K/ScriptSourceMap.kt" "$K/ScriptTimeline.kt" "$K/VoiceDirection.kt" "$K/VoiceSpeed.kt" \
  "$K/HyperAnalysis.kt" "$K/HyperAudioQa.kt" "$K/HyperGovernance.kt" "$K/HyperJson.kt" \
  tests/baseline/*.kt scripts/test_hyper_production.kt -cp "$JSON_JAR" -include-runtime -d /tmp/subgen-hyper-production-tests.jar
java -Xmx512m -cp "/tmp/subgen-hyper-production-tests.jar:$JSON_JAR" Test_hyper_productionKt | tee test-results/HYPER-TEST-LOG.txt

XZ_JAR="${SUBGEN_XZ_JAR:-/tmp/subgen-xz-1.10.jar}"
if [[ ! -s "$XZ_JAR" ]]; then
  curl -fsSL --retry 3 https://repo.maven.apache.org/maven2/org/tukaani/xz/1.10/xz-1.10.jar -o "$XZ_JAR"
fi
kotlinc "$K/NeuroModulationA.kt" "$K/NeuroModulationB.kt" scripts/test_neuro_b.kt -cp "$XZ_JAR" -include-runtime -d /tmp/subgen-real-xz-neuro-b.jar
java -cp "/tmp/subgen-real-xz-neuro-b.jar:$XZ_JAR" Test_neuro_bKt | tee test-results/NEURO-B-REAL-XZ.txt
