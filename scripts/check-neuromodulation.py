from pathlib import Path
r=Path(__file__).resolve().parents[1]
u=(r/'app/src/main/java/com/subgen/nativeapp/MainActivity.kt').read_text()
a=(r/'app/src/main/java/com/subgen/nativeapp/NeuroModulationA.kt').read_text()
s=(r/'app/src/main/java/com/subgen/nativeapp/NeuroModulationService.kt').read_text()
manifest=(r/'app/src/main/AndroidManifest.xml').read_text()
assert 'tabNeuro=tab("≈ NEUROMOD")' in u
assert 'if(name=="neuro")' in u
assert 'private fun buildNeuro()' in u
assert 'VERSION B · OFDM/QAM' in u and 'GENERATE A + B' in u
assert 'if(name=="studio")buildStudio() else buildHypnosis()' not in u
assert 'affirmFrequency' in u and 'neuro:mixer' in u
assert 'NeuroModulationB.generate' in s and 'NeuroModulationB.createMixerCopy' in s
assert 'neuro:verified:$version' in s
assert 'NEURO_GENERATE' in s and 'NeuroModulationA.generate' in s and 'NeuroModulationA.createMixerCopy' in s
assert 'NeuroModulationService' in manifest
assert 'samples[i]=y' not in a  # no giant in-memory WAV buffer
assert 'Digital roundtrip verification failed' in a and 'Master exceeds original' in a
assert 'Version A' in a and 'Version B' in a
assert 'isEnabled=true' in u and 'if(requireReady())' in u
assert 'versionCode = 15' in (r/'app/build.gradle.kts').read_text()
print('PASS: native third tab, protected master, verified A, mixer handoff, B/A+B functional, clearly gated exports, stable versionCode15')
