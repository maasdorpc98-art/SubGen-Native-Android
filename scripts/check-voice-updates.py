#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
base=root/'app/src/main/java/com/subgen/nativeapp'
ui=(base/'MainActivity.kt').read_text()
service=(base/'RenderService.kt').read_text()
timeline=(base/'ScriptTimeline.kt').read_text()
speed=(base/'VoiceSpeed.kt').read_text()
gradle=(root/'app/build.gradle.kts').read_text()
assert 'VOICE SPEED · 1.00×' in ui
assert 'VoiceSpeed.fromPosition(n)' in ui
assert 'VoiceSpeed.label(selected)' in ui
assert 'const val DEFAULT=1.00f' in speed and 'VoiceSpeed.DEFAULT' in ui
assert '↺ RESET VOICE SPEED · 1.00×' in ui
assert 'getFloat("speed",VoiceSpeed.DEFAULT)' in service
assert 'speed:Float=VoiceSpeed.DEFAULT' in timeline
assert 'getFloat("speed",.84f)' not in ui+service
assert 'com.subgen.nativeapp.stable' in gradle
assert 'versionCode = 8' in gradle
assert 'scripts/test_voice_speed.kt' in (root/'scripts/run-core-tests.sh').read_text()
print('PASS: 1.0x default across renderer, live slider label, stable package/version identity')
