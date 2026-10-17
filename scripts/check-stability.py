#!/usr/bin/env python3
"""Crash-loop and bounded-JNI regression guard for v3.0.2."""
from pathlib import Path
base=Path(__file__).resolve().parents[1]
src=base/'app/src/main/java/com/subgen/nativeapp'
service=(src/'RenderService.kt').read_text()
activity=(src/'MainActivity.kt').read_text()
diagnostics=(src/'Diagnostics.kt').read_text()
kokoro=(src/'KokoroNative.kt').read_text()
assert 'return START_STICKY' not in service
assert 'return START_NOT_STICKY' in service
assert 'request == null' in service and 'IGNORED_SERVICE_RESTART' in service
assert 'ACTION_RENDER->runJob()' in service
assert 'startForegroundService(Intent(this,RenderService::class.java))' not in activity
assert 'startAction(RenderService.ACTION_RENDER)' in activity
assert 'maxSpeechChars=160' in service
assert 'val threads=1' in service
assert 'KOKORO_BLOCK_START' in service and 'KOKORO_BLOCK_SUCCESS' in service
assert 'KOKORO_ENGINE_CREATE' in kokoro and 'KOKORO_ENGINE_READY' in kokoro
assert 'KOKORO_GENERATE' in kokoro
assert 'require(text.length in 1..180)' in kokoro
assert 'getHistoricalProcessExitReasons' in diagnostics
assert 'beforeNative(' in diagnostics and 'persist(entry, true)' in diagnostics
assert 'StateStore.active(app, false)' in diagnostics
print('PASS: no crash restart-loop, safe speech blocks, durable native breadcrumbs, exit reason')
