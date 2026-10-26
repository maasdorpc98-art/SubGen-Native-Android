from pathlib import Path
root=Path(__file__).resolve().parents[1]
base=root/'app/src/main/java/com/subgen/nativeapp'
parser=(base/'ScriptParser.kt').read_text()
voice=(base/'VoiceDirection.kt').read_text()
service=(base/'RenderService.kt').read_text()
ui=(base/'MainActivity.kt').read_text()
assert 'PROFILE_CODES' in parser and 'ACOUSTIC_PHASES' in parser
assert 'ScriptNode.ProfileChange' in parser and 'ScriptNode.PhaseChange' in parser
assert 'explicitPhaseMode' in voice and 'node.acousticPhase' in voice
assert 'n.scriptProfile!=null' in service
assert 'scriptProfile=null,acousticPhase=null,explicitPhaseMode=true' in service
assert '[profile MASTER_HYPNOTIST]' in ui and '[phase DEEPENER]' in ui
assert 'versionCode = 21' in (root/'app/build.gradle.kts').read_text()
print('PASS: script-selected native acoustic control, cache isolation and neutral audition wiring')
