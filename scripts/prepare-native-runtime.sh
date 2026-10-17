#!/usr/bin/env bash
# Downloads matching Sherpa Android native binaries + Kotlin JNI bridge. No API credentials required.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="v1.13.8"
BASE="https://github.com/k2-fsa/sherpa-onnx/releases/download/${VERSION}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$ROOT/app/src/main/jniLibs/arm64-v8a" "$ROOT/app/src/main/java/com/k2fsa/sherpa/onnx"
echo "Downloading sherpa-onnx ${VERSION} native Android runtime…"
curl -fL --retry 3 --connect-timeout 15 "${BASE}/sherpa-onnx-${VERSION}-android.tar.bz2" -o "$TMP/android.tar.bz2"
mkdir "$TMP/unpack"
tar -xjf "$TMP/android.tar.bz2" -C "$TMP/unpack"
count=0
while IFS= read -r -d '' lib; do
  # Prevent accidentally packaging the wrong architecture from a multi-ABI release archive.
  if file -b "$lib" | grep -Eq 'ARM aarch64|ARM64|AArch64'; then
    cp -L "$lib" "$ROOT/app/src/main/jniLibs/arm64-v8a/$(basename "$lib")"
    printf 'Copied %s\n' "$(basename "$lib")"
    count=$((count+1))
  fi
done < <(find "$TMP/unpack" \( -type f -o -type l \) -name '*.so' -print0)
for lib in libsherpa-onnx-jni.so libonnxruntime.so; do
  test -s "$ROOT/app/src/main/jniLibs/arm64-v8a/$lib" || { echo "ERROR: missing $lib in Android release" >&2; exit 1; }
done
curl -fL --retry 3 --connect-timeout 15 \
  "https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/${VERSION}/sherpa-onnx/kotlin-api/Tts.kt" \
  -o "$ROOT/app/src/main/java/com/k2fsa/sherpa/onnx/Tts.kt"
grep -q 'class OfflineTts(' "$ROOT/app/src/main/java/com/k2fsa/sherpa/onnx/Tts.kt" || exit 1
printf 'Native libraries: %d\n' "$count"
sha256sum "$ROOT/app/src/main/jniLibs/arm64-v8a/"*.so
