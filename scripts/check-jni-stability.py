#!/usr/bin/env python3
from pathlib import Path
base=Path(__file__).resolve().parents[1]
p=base/'app/src/main/java/com/subgen/nativeapp/KokoroNative.kt'
s=p.read_text()
assert 'generateWithConfigAndCallback(' not in s, "Unsafe callback JNI path must not return"
assert 'generateWithCallback(' not in s
assert 'engine.generateWithConfig(' in s
assert 'require(!onStop())' in s
assert 'engine.numSpeakers()' in s
assert 'require(speaker in 0 until engine.numSpeakers())' in s
assert 'KOKORO_GENERATE_COMPLETED' in s
assert 'KOKORO_ENGINE_READY' in s
print('PASS: non-callback JNI path, cancellation boundaries and speaker validation')
