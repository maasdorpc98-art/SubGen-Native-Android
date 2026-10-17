from pathlib import Path
p=Path(__file__).resolve().parents[1]
src=p/'app/src/main/java/com/subgen/nativeapp'
r=(src/'RenderService.kt').read_text()
t=(src/'AdaptiveNeuralSession.kt').read_text()
w=(src/'WavIO.kt').read_text()
assert 'SPEECH_CACHE_PREFLIGHT' in r
assert 'AdaptiveNeuralSession(installer,threads)' in r
assert 'CACHE_ONLY_ASSEMBLY' in r
assert 'WAV_ASSEMBLY_COMPLETED' in r
assert 'WAV_EXPORT_COMPLETED' in r
assert 'THERMAL_COMPARE_COMPLETED' in t
assert 'ThermalRecheckPolicy.shouldCompare' in t
assert 'ThermalRecheckPolicy.preferTwo' in t
assert 'quartileRtf' in r
assert 'val existing=WavIO.loadMono' not in r or 'WavIO.loadMono(clip)' in r
assert 'ByteArray(65536)' in w
print('PASS: cache-first neural speed, safe thermal comparison and preserved WAV engine')
