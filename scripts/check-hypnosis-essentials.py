#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/com/subgen/nativeapp"
ui=(src/"MainActivity.kt").read_text()
render=(src/"RenderService.kt").read_text()
fmt=(src/"OutputFormat.kt").read_text()
timeline=(src/"ScriptTimeline.kt").read_text()
for label in ["03 — SCRIPT CHECK + TIMELINE","04 — NEURAL VOICE PREVIEW","05 — OUTPUT / RENDER",
              "FINAL OUTPUT VOLUME","PEAK CEILING","ESTIMATED WAV SIZE","OUTPUT CHANNELS","OUTPUT SAMPLE RATE"]:
    assert label in ui,label
assert 'OutputFormat.convert(mixingInput,finalPath' in render
assert 'if(backgroundLayers.isEmpty())path' in render
assert 'WavIO.saveToDownloads(this,finalPath,name)' in render
assert 'p.getInt("hyp:channels",2)' in ui and 'prefs.getInt("hyp:channels",2)' in render
assert 'ScriptTimeline.analyze(field.text.toString()' in ui
assert 'ScriptParser.parse(script,breathe,paragraph,maxSpeechChars=160)' in timeline
assert 'OutputFormat.rates' in ui and 'OutputFormat.rates' in render
assert 'arrayOf("16 kHz","22.05 kHz","24 kHz · Neural native","32 kHz","44.1 kHz","48 kHz")' in ui
assert 'peak' in fmt.lower() and 'lowPassKernel' in fmt and 'lerpCubic' in fmt
assert 'if(rate==24000 && channels==2 && abs(gain-1.0)<1e-10)' in fmt
assert 'scripts/test_hypnosis_output.kt' in (root/"scripts/run-core-tests.sh").read_text()
assert 'android.webkit' not in ui
print("PASS: essentials UI fully backed by actual native PCM export and script parser")
