#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/com/subgen/nativeapp'
installer=(src/'ModelInstaller.kt').read_text()
engine=(src/'KokoroNative.kt').read_text()
service=(src/'RenderService.kt').read_text()
quality=(src/'AudioQuality.kt').read_text()
assert 'MODEL="kokoro-multi-lang-v1_0"' in installer
assert 'MODEL_VARIANT="FP32"' in installer
assert 'kokoro-int8-multi-lang' not in installer
assert 'model.int8.onnx' not in installer
assert 'model.onnx").length()>280_000_000L' in installer
assert 'lang="en-us"' in engine
assert 'AudioQuality.examine(' in engine
assert 'KOKORO_AUDIO_REJECTED' in engine
assert 'KOKORO_AUDIO_ACCEPTED' in engine
assert 'generateWithConfigAndCallback(' not in engine
assert 'native-kokoro-v1_0-fp32-validated' in service
assert 'val threads=1' in service
assert 'nanCount' in quality and 'infiniteCount' in quality and 'longestRailRun' in quality
assert 'kotlinc "$K/AudioQuality.kt" scripts/test_audio_quality.kt' in (root/'scripts/run-core-tests.sh').read_text()
print('PASS: independent FP32 model, separate cache, no JNI callback, valid audio quality gates')
