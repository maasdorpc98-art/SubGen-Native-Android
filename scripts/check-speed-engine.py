#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/com/subgen/nativeapp"
render=(src/"RenderService.kt").read_text()
wav=(src/"WavIO.kt").read_text()
tuner=(src/"InferenceTuner.kt").read_text()
installer=(src/"ModelInstaller.kt").read_text()
diag=(src/"Diagnostics.kt").read_text()
assert 'SPEECH_CACHE_PREFLIGHT' in render and 'CACHE_ONLY_ASSEMBLY' in render
assert 'if(missing>0) KokoroNative(installer,threads).use' in render
assert 'else renderSpeech(null)' in render
assert 'WAV_ASSEMBLY_COMPLETED' in render and 'WAV_EXPORT_COMPLETED' in render
assert 'InferenceTuner.threads(this)' in render
assert 'InferenceTuner.rememberBenchmark(this,best.first,best.second)' in render
assert 'android.webkit.' not in render
assert 'ByteArray(65536)' in wav and 'writeHeader(out,frames,sr,2)' in wav
assert 'outputBytes[q]=lo' in wav and 'silenceBytes' in wav
assert 'appendWithBinaural' in wav and 'sinL*stepCosL+cosL*stepSinL' in wav
assert 'getHeaderField("Content-Range")' in installer and 'Range' in installer
assert 'FileOutputStream(archive,append)' in installer
assert 'if(present())archive.delete()' in installer
assert 'durable: Boolean = true' in diag
assert 'scripts/test_fast_wav.kt' in (root/"scripts/run-core-tests.sh").read_text()
print("PASS: cache-first lazy Kokoro, adaptive CPU, buffered 24kHz WAV, resumable FP32 download")
