#!/usr/bin/env bash
# Checks that the external JNI runtime matches the Android app, before building.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JNI="$ROOT/app/src/main/jniLibs/arm64-v8a"
API="$ROOT/app/src/main/java/com/k2fsa/sherpa/onnx/Tts.kt"
for n in libsherpa-onnx-jni.so libonnxruntime.so; do
  test -s "$JNI/$n" || { echo "ERROR: missing $JNI/$n" >&2; exit 1; }
  file -b "$JNI/$n" | grep -Eiq 'aarch64|ARM64' || { echo "ERROR: $n is not ARM64" >&2; exit 1; }
done
for sym in 'data class GenerationConfig' 'class OfflineTts(' 'generateWithConfigAndCallback' 'sid: Int'; do
  grep -Fq "$sym" "$API" || { echo "ERROR: sherpa-onnx Kotlin API missing $sym" >&2; exit 1; }
done
find "$JNI" -type f -name '*.so' -exec file {} \;
echo 'PASS: expected native Android arm64 libraries and Kotlin voice API present'
