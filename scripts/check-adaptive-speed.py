#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/com/subgen/nativeapp'
s=(src/'RenderService.kt').read_text()
t=(src/'InferenceTuner.kt').read_text()
k=(src/'KokoroNative.kt').read_text()
u=(src/'MainActivity.kt').read_text()
assert 'InferenceTuner.autoTuneIfNeeded(this,installer,sid,speed,missing' in s
assert 'KOKORO_ENGINE_LOAD_TIME' in s
assert 'SPEECH_SPEED_CHECKPOINT' in s
assert 'SPEECH_GENERATION_COMPLETED' in s and '"rtf"' in s
assert 'approx ' in s and 'm left' in s
assert 'rememberBenchmark(this,best.first,best.second)' in s
assert 'for(threads in listOf(1,2,4)' in s
assert 'tts.synthesize(phrase,sid,speed){cancelled.get()} // warm-up' in s
assert 'speechBlocks<60' in t
assert 'listOf(2,4).filter' in t
assert 'rememberBenchmark(context:Context' in t
assert 'getString(KEY_MODEL' in t and 'Build.FINGERPRINT' in t
assert 'if(free in 0..449) result=1' in t
assert 'else if(thermal>=PowerManager.THERMAL_STATUS_MODERATE) result=minOf(result,2)' in t
assert 'KokoroNative(installer,worker).use' in t
assert 'val started=SystemClock.elapsedRealtime()' in s
assert 'generateWithConfigAndCallback(' not in k
assert 'AudioQuality.examine' in k
assert 'BENCHMARK · FIND FASTEST CPU SETTING' in u
print('PASS: device calibration + conservative thermals + progress RTF + existing quality checks')
