#!/usr/bin/env python3
"""Regression checks for Android Kokoro callback JNI abort (#3492)."""
from pathlib import Path
import sys, re

root=Path(__file__).resolve().parents[1]
source=(root/"app/src/main/java/com/subgen/nativeapp/KokoroNative.kt").read_text()
assert "engine.generateWithConfig(" in source, "Speech generation must use callback-free Kotlin API"
assert "engine.generateWithConfigAndCallback(" not in source, "Unsafe callback API must not return"
assert "engine.generateWithCallback(" not in source
assert re.search(r'config=GenerationConfig\(sid=speaker,speed=speed\.coerceIn\(0\.6f,1\.4f\),silenceScale=0\.0f\)',source), "Keep voice/speed/silence scale unchanged"
assert "CancellationException" in source and source.count("if (onStop())") >= 2, "Check cancellation at block boundaries"
assert "KOKORO_GENERATE_NO_CALLBACK" in source and "KOKORO_GENERATE_COMPLETED" in source, "Keep diagnostic breadcrumbs"
script=(root/"app/src/main/java/com/subgen/nativeapp/RenderService.kt").read_text()
assert "maxSpeechChars=160" in script, "Keep bounded speech blocks"
assert "WavIO.writeMono(clip,samples,sr)" in script, "Keep disk checkpoints"
if "--verify-wrapper" in sys.argv:
    wrapper=root/"app/src/main/java/com/k2fsa/sherpa/onnx/Tts.kt"
    assert wrapper.exists(), "Upstream Kotlin JNI wrapper missing"
    code=wrapper.read_text()
    assert re.search(r'fun generateWithConfig\([\s\S]{0,200}?generateWithConfigImpl\(ptr,\s*text,\s*config,\s*null\)',code), (
        "Upstream generateWithConfig must pass a null native callback")
print("PASS: Kokoro generation bypasses JNI callback, preserves voice settings/checkpoints")
