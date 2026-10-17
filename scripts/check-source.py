#!/usr/bin/env python3
from pathlib import Path
import re
p=Path(__file__).resolve().parents[1]
a=(p/'app/src/main/java/com/subgen/nativeapp')
files=list(a.glob('*.kt'))
assert len(files)>=7, len(files)
text='\n'.join(x.read_text() for x in files)
for forbidden in ['import android.webkit.', 'new WebView(', 'SpeechSynthesisUtterance(', 'window.AudioContext', '@JavascriptInterface']:
    assert forbidden not in text, f'Forbidden legacy engine: {forbidden}'
for required in ['OfflineTts(', 'startForeground(', 'ModelInstaller(', 'WavIO.assemble(', 'StateStore.hash(', 'MediaStore.Downloads', 'NativeSubliminal.mix(', 'ACTION_INSTALL']:
    assert required in text,required
m=(p/'app/src/main/AndroidManifest.xml').read_text()
assert 'FOREGROUND_SERVICE_DATA_SYNC' in m
assert 'android:exported="false"' in m
assert 'android:stopWithTask="false"' in m
assert 'WebView' not in m
assert (p/'.github/workflows/build-android.yml').is_file()
assert not list(p.rglob('*.html'))
print('PASS: pure native UI paths, foreground service, checkpoints, Android download, workflow, no HTML assets')
