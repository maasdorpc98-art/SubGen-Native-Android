from pathlib import Path
import hashlib,json
root=Path(__file__).resolve().parents[1]
expected=json.loads((root/'tests/baseline/protected-files-sha256.json').read_text())
for name,digest in expected.items():
    assert hashlib.sha256((root/name).read_bytes()).hexdigest()==digest, f'Protected baseline changed: {name}'
ui=(root/'app/src/main/java/com/subgen/nativeapp/MainActivity.kt').read_text()
assert 'HYPER AUDIT & PRODUCTION TOOLS · OPTIONAL' in ui
assert 'startAction(RenderService.ACTION_RENDER)' in ui
service=(root/'app/src/main/java/com/subgen/nativeapp/RenderService.kt').read_text()
assert 'HyperAnalysis.analyze' not in service and 'technicalErrors' not in service
assert 'StateStore.hash("native-kokoro-v1_0-fp32-validated|' in service
assert 'applicationId = "com.subgen.nativeapp.stable"' in (root/'app/build.gradle.kts').read_text()
print(f'PASS: {len(expected)} protected engine/control/assets byte-identical to v3.1.3; Create has no HYPER approval gate')
