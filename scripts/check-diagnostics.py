#!/usr/bin/env python3
from pathlib import Path
r=Path(__file__).resolve().parents[1]
root=r/'app/src/main/java/com/subgen/nativeapp'
main=(root/'MainActivity.kt').read_text()
diag=(root/'Diagnostics.kt').read_text()
render=(root/'RenderService.kt').read_text()
mix=(root/'NativeSubliminal.kt').read_text()
decoder=(root/'NativeInputDecoder.kt').read_text()
assert '<application android:name=".SubGenApplication"' in (r/'app/src/main/AndroidManifest.xml').read_text()
assert 'com.subgen.nativeapp.original.stability' in (r/'app/build.gradle.kts').read_text()
for item in ['setDefaultUncaughtExceptionHandler','FATAL_UNCAUGHT_EXCEPTION','PREVIOUS_JOB_INTERRUPTED','SUSPECTED_UI_STALL','privateStorageFreeMiB','processPssMiB','thermalStatus','events.previous.jsonl','stream.fd.sync()', 'ACTION_CREATE_DOCUMENT']:
    if item == 'ACTION_CREATE_DOCUMENT': assert item in main,item
    elif item == 'stream.fd.sync()': assert 'it.fd.sync()' in diag,item
    else: assert item in diag,item
assert '923->{' in main and 'SubGen-Diagnostic-Report.json' in main
assert '◉ SUBLIMINAL DIAGNOSTICS' in main
assert 'Diagnostics.failure("JOB_FAILED",t)' in render
assert 'INPUT_DECODE_FAILED' in decoder
assert 'MIXER_CHECKPOINT' in mix and 'MIXER_COMPLETED' in mix
assert not any(s in diag for s in ['HttpURLConnection','https://','Socket(','FirebaseAnalytics'])
assert (r/'app/src/main/assets/original-presets.json').is_file()
print('PASS: diagnostic hooks, privacy, app ID and original controls')
