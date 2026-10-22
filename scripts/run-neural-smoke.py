#!/usr/bin/env python3
"""Optional desktop neural evidence; does not claim Android JNI or listening acceptance.
Requires sherpa-onnx==1.13.8, numpy and the original FP32 model, never committed.
Run the Kotlin test_neural_evidence plan stage first, then this script, then assemble.
"""
import argparse,hashlib,json,time,wave
from pathlib import Path
import numpy as np
import sherpa_onnx
p=argparse.ArgumentParser();p.add_argument('--model-dir',type=Path,required=True);p.add_argument('--results',type=Path,default=Path('test-results/neural'));args=p.parse_args()
dir=args.model_dir;out=args.results;out.mkdir(parents=True,exist_ok=True)
assert sherpa_onnx.__version__=='1.13.8'
k=sherpa_onnx.OfflineTtsKokoroModelConfig(model=str(dir/'model.onnx'),voices=str(dir/'voices.bin'),tokens=str(dir/'tokens.txt'),
    data_dir=str(dir/'espeak-ng-data'),lexicon=str(dir/'lexicon-us-en.txt'),lang='en-us')
config=sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(kokoro=k,num_threads=2,provider='cpu',debug=False),max_num_sentences=1)
start=time.perf_counter();engine=sherpa_onnx.OfflineTts(config);print('FP32 model loaded',time.perf_counter()-start,flush=True)
cache={};rows=[]
def speech(text,sid,speed,name):
    key=(text,sid,speed)
    if key not in cache:
        c=sherpa_onnx.GenerationConfig();c.sid=sid;c.speed=speed;c.silence_scale=0.0
        at=time.perf_counter();a=engine.generate(text,config=c);samples=np.asarray(a.samples,dtype=np.float32)
        assert a.sample_rate==24000 and np.isfinite(samples).all() and len(samples)>0
        peak=float(np.abs(samples).max());rms=float(np.sqrt(np.mean(samples*samples)));assert peak<1 and rms>1e-4
        cache[key]=(samples,a.sample_rate)
        rows.append({'text':text,'speakerId':sid,'nativeSpeed':speed,'frames':len(samples),'seconds':len(samples)/a.sample_rate,
                     'synthesisSeconds':time.perf_counter()-at,'peak':peak,'rms':rms})
        print('Generated',sid,speed,len(samples),text[:60],flush=True)
    samples,rate=cache[key]
    pcm=np.clip(np.floor(samples*32768+0.5),-32768,32767).astype('<i2')
    with wave.open(str(out/name),'wb') as w:w.setnchannels(1);w.setsampwidth(2);w.setframerate(rate);w.writeframes(pcm.tobytes())
# All four IDs match KokoroNative.voices. Same spoken sentence at three synthesis speeds.
for voice,sid in [('orion',16),('rowan',14),('luna',2),('serena',3)]:
    for speed in [.88,1.0,1.04]:speech('Relax Deeply. Relax Your System Deeply. Notice a comfortable breath.',sid,speed,f'{voice}-speed-{speed:.2f}.wav')
plan=json.loads((out/'neural-plan.json').read_text())
for script in plan['plans']:
    for item in script['nodes']:
        if item['kind']=='speech':speech(item['text'],3,item['speed'],f"serena-{script['name']}-{item['index']}.wav")
for sid in [16,14,2,3]:
    samples=[r for r in rows if r['speakerId']==sid and r['text'].startswith('Relax Deeply.')]
    durations={r['nativeSpeed']:r['seconds'] for r in samples};assert durations[.88]>durations[1.0]>durations[1.04]
report={'backendVersion':sherpa_onnx.__version__,'model':'kokoro-multi-lang-v1_0 FP32','modelBytes':(dir/'model.onnx').stat().st_size,
 'modelSha256':hashlib.file_digest((dir/'model.onnx').open('rb'),'sha256').hexdigest(),'uniqueNeuralCalls':len(cache),
 'sameRenderReuse':True,'measurements':rows,'allSamplesFinite':True,'voiceSpeedMonotonic':True,
 'manualListeningAccepted':False,'SamsungTested':False,'limitations':'Desktop CPU backend checks and original Kotlin PCM assembly; not Android JNI/device acceptance or voice calibration.'}
(out/'neural-inference-results.json').write_text(json.dumps(report,indent=2)+'\n')
print('PASS: original FP32 neural inference, four voices, monotonic rate changes, no NaN/Inf/clipping.',flush=True)
