#!/usr/bin/env python3
"""Explicit evidence-only level-matched copies; never changes source or app QA audio."""
from pathlib import Path
import json,wave
import numpy as np
root=Path('test-results/neural');items=[]
for kind in ['directed','neutral']:
    source=root/f'{kind}-final.wav'
    with wave.open(str(source),'rb') as w:
        assert w.getsampwidth()==2
        channels=w.getnchannels();rate=w.getframerate();samples=np.frombuffer(w.readframes(w.getnframes()),'<i2').astype(np.float64)/32768
    rms=float(np.sqrt(np.mean(samples*samples)));peak=float(np.max(np.abs(samples)))
    items.append((kind,samples,channels,rate,rms,peak))
target_db=min([-25.0]+[20*np.log10(rms)-3-20*np.log10(peak) for _,_,_,_,rms,peak in items])
report={'targetOverallRmsDbfs':target_db,'notLufs':True,'originalsUnmodified':True,'manualListeningAccepted':False,'files':[]}
for kind,samples,ch,rate,rms,peak in items:
    gain=10**(target_db/20)/rms;pcm=np.clip(np.floor(samples*gain*32768+.5),-32768,32767).astype('<i2')
    dest=root/f'ab-{kind}-rms-matched.wav'
    with wave.open(str(dest),'wb') as w:w.setnchannels(ch);w.setsampwidth(2);w.setframerate(rate);w.writeframes(pcm.tobytes())
    report['files'].append({'name':dest.name,'appliedGainDb':float(20*np.log10(gain)),'peakDbfs':float(20*np.log10(peak*gain))})
(root/'ab-level-matching.json').write_text(json.dumps(report,indent=2)+'\n')
print('PASS: separate directed/neutral evidence copies matched to overall RMS',target_db,'dBFS; originals unchanged')
