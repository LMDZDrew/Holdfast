"""Sample every exported walker/crawler clip for finite FK and loop seams. Requires art/characters/.deps."""
import json
import math
from pathlib import Path
import sys
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'art/characters/source'))
import execution_contacts as ec
import fk

base = ROOT / 'art/characters/export/assets/holdfast'
clips = samples = 0
failures = []
for name, geo in [('walker','walker.geo.json'),('walker_crawler','walker_crawler.geo.json')]:
    rig = fk.rig(geo)
    data = json.loads((base / 'animations' / (name+'.animation.json')).read_text(encoding='utf-8'))['animations']
    for full, raw in data.items():
        a = ec.source_anim(raw)
        a.loop = False  # inspect the authored last key instead of automatically wrapping it to zero
        for t in np.linspace(0,a.length,21):
            for bone, matrix in fk.worlds(rig,[a],float(t)).items():
                if not np.isfinite(matrix).all(): failures.append(f'{full}/{bone}: nonfinite at {t}')
            samples += 1
        if raw['loop'] is True:
            first,last = fk.worlds(rig,[a],0),fk.worlds(rig,[a],a.length)
            for bone in first:
                gap = float(np.linalg.norm(first[bone][:3,3]-last[bone][:3,3]))
                turn = math.degrees(math.acos(float(np.clip((np.trace(first[bone][:3,:3].T @ last[bone][:3,:3])-1)/2,-1,1))))
                if gap>.05 or turn>1: failures.append(f'{full}/{bone}: loop gap {gap:.3f}px, turn {turn:.3f}deg')
        clips += 1
print(f'{clips} walker/crawler exports, {samples} frames; finite transforms and loop position/rotation seams')
if failures: raise SystemExit('\n'.join(failures))
print('PASS (software kinematics only; not an in-game quality judgment)')
