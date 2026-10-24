#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p test-results
K=app/src/main/java/com/subgen/nativeapp
XZ_JAR="${SUBGEN_XZ_JAR:-/tmp/subgen-xz-1.10.jar}"
if [[ ! -s "$XZ_JAR" ]]; then
  curl -fsSL --retry 3 https://repo.maven.apache.org/maven2/org/tukaani/xz/1.10/xz-1.10.jar -o "$XZ_JAR"
fi
kotlinc "$K/HighQualityPcm.kt" "$K/HighQualityNarration.kt" "$K/HighQualityMixer.kt" "$K/PreferenceSnapshot.kt" \
  "$K/ScriptParser.kt" "$K/NativeSubliminal.kt" "$K/WavIO.kt" "$K/NeuroModulationA.kt" "$K/NeuroModulationB.kt" \
  tests/stubs/*.kt tests/hq-stubs/*.kt scripts/test_high_quality.kt -cp "$XZ_JAR" -include-runtime -d /tmp/subgen-high-quality-tests.jar
java -Xmx256m -cp "/tmp/subgen-high-quality-tests.jar:$XZ_JAR" Test_high_qualityKt | tee test-results/HIGH-QUALITY-TESTS.txt
