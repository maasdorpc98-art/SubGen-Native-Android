#!/usr/bin/env python3
"""Verify version-locked original Studio control names/ranges/presets are retained.
UI pixel-parity still requires comparison on actual Android device.
"""
from pathlib import Path
import json,re
root=Path(__file__).resolve().parents[1]
asset=root/'app/src/main/assets/original-presets.json'
source=root/'app/src/main/java/com/subgen/nativeapp/OriginalStudioControlSpec.kt'
ui=root/'app/src/main/java/com/subgen/nativeapp/MainActivity.kt'
items=re.findall(r'OriginalSliderSpec\("([^"]+)",\s*"([^"]+)",\s*([-\d.]+),\s*([-\d.]+),\s*([-\d.]+),\s*([-\d.]+)',source.read_text())
assert len(items)==28, f'Lost original sliders: {len(items)}'
assert len({row[0] for row in items})==28
assert set(row[0] for row in items)==set(r['id'] for r in json.loads((root/'design-lock/design-manifest.json').read_text())['slides'])
expected={r['id']:(float(r['min']),float(r['max']),float(r['step']),float(r['default'])) for r in json.loads((root/'design-lock/design-manifest.json').read_text())['slides']}
for id,label,mn,mx,step,default in items:
    assert (float(mn),float(mx),float(step),float(default))==expected[id],f'Drift: {id}'
presets=json.loads(asset.read_text())
assert len(presets)==21
assert len(set(x['id'] for x in presets))==21
assert all(x['title'] and x['description'] and len(x['values'])>=20 for x in presets)
text=ui.read_text()
assert 'android.webkit' not in text and 'WebView(' not in text
for required in ['01 — Upload Tracks','02 — Presets','03 / MIXING DESK','04 — Shared Affirmation Layer','05 — Render Installation','SAVE WORK · OFF','GENERATE PURE MASTER','PREVIEW 30 SEC']:
    assert required in text,required
for slot in ('carrierStandard','affirmNormal','affirmFrequency'):
    assert slot in text,slot
print('PASS: original 28 slider ranges/defaults, 21 preset maps and 5 Studio sections preserved')
