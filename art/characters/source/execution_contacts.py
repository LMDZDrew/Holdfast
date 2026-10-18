"""Source-derived skull entry/exit points and incoming directions for every server execution reaction.

Coordinates are walker-local model px. Sampling uses the actual server kill tick and the exact player/walker
pair transforms, including directional and prone/crawler placement. This changes no weapon geometry.
"""
import json
import math
import os
import numpy as np
import fk
import placement
import player_anims as pa
import walker_anims as wa
import contact_geometry as cg
from lib import Anim, OUT


def source_anim(data):
    a = Anim(data['animation_length'], data['loop'])
    for part, channels in data['bones'].items():
        for channel, keys in channels.items():
            for t, value in keys.items():
                a.key(float(t), part, **({'rot': value['post']} if channel == 'rotation' else {'pos': value['post']}))
    return a


def pair_world(name, target, posture, pair):
    if posture != 'UPRIGHT':
        dist = pair[f'{name}_{"prone" if posture == "PRONE" else "crawler"}']['dist']
        return pa.walker_world(dist)
    front, c = pair['exec_skull'][f'react_{name}'], pair['exec_skull'][target]
    suffix = target.removeprefix(f'react_{name}').lstrip('_')
    theta = wa.DIRECTIONS[suffix] if suffix else 180.
    sx, sz = -front[0], -wa.EXECS[name]['dist'] - front[1]
    ct, st = math.cos(math.radians(theta)), math.sin(math.radians(theta))
    ox, oz = sx - (ct*c[0]+st*c[1]), sz - (-st*c[0]+ct*c[1])
    world = fk.entity(ox/pa.PLAYER_SCALE, oz/pa.PLAYER_SCALE, theta)
    world[:3, :3] /= pa.PLAYER_SCALE
    return world


def generate():
    raw = json.load(open(os.path.join(OUT, 'player_keys.animation.json')))['animations']
    measured = json.load(open(os.path.join(OUT, 'contact.json')))
    pair, rows = placement.load(), {}
    specs = []
    for name in wa.UPRIGHT_EXECS:
        specs += [(name, f'react_{name}{side}', 'UPRIGHT') for side in ('', '_left', '_right', '_rear')]
    for weapon in wa.GROUND_EXECS:
        name = f'{weapon}_ground'
        specs += [(name, f'react_{name}', 'CRAWLER'), (name, f'react_{name}_prone', 'PRONE')]
    for name, target, posture in specs:
        weapon = 'bat' if name == 'bat_charged' else name.removesuffix('_ground')
        pc = f'exec_{name}'
        e = wa.EXECS[name]
        t = cg.kill_tick((measured.get(pc) or {}).get('first_in'), e['impact']) / 20
        world = pair_world(name, target, posture, pair)
        rig = pa.CR if posture == 'CRAWLER' else pa.WR
        react = pa.W['crawler'][target] if posture == 'CRAWLER' else pa.W['react'][target]
        head = fk.worlds(rig, [react], t, world=world)['head']
        inverse = np.linalg.inv(head)
        if weapon == 'pistol':
            p = (head @ np.array([0., 5., 0., 1.]))[:3]
            start = np.array([0., pa.EYE / pa.PLAYER_SCALE, 0.])
            direction = p - start
            direction /= np.linalg.norm(direction)
            end = p + direction * 32
        else:
            a = source_anim(raw[f'animation.holdfast.player.{pc}'])
            transforms = fk.worlds(pa.SR, [a], t)
            marker = e.get('marker', pa.WEAPON[weapon][0])
            p = fk.point(transforms, marker)
            # Stabs enter along the blade; cuts/blunt blows enter along the striking face.
            cut = name.endswith('_ground') and weapon != 'knife' or weapon not in ('knife', 'katana')
            direction = -transforms['rightItem'][:3, 1 if cut else 2]
            direction /= np.linalg.norm(direction)
            start, end = p - direction*64, p
        local_start = (inverse @ np.array([*start, 1.]))[:3]
        local_end = (inverse @ np.array([*end, 1.]))[:3]
        hit = cg.segment_box(local_start, local_end)
        if hit is None:
            raise ValueError(f'{pc}/{target}: striking ray misses skull at kill tick {t*20:g}')
        local_entry = local_start + (local_end-local_start)*hit[0]
        local_exit = local_start + (local_end-local_start)*hit[1]
        into_walker = np.linalg.inv(world)
        entry = (into_walker @ head @ np.array([*local_entry, 1.]))[:3]
        exit = (into_walker @ head @ np.array([*local_exit, 1.]))[:3]
        incoming = into_walker[:3,:3] @ direction
        incoming /= np.linalg.norm(incoming)
        rows[target] = dict(point=[round(float(v),6) for v in entry], direction=[round(float(v),6) for v in incoming],
                            exit=[round(float(v),6) for v in exit] if weapon=='pistol' else None,
                            tick=round(t*20), head_local=[round(float(v),6) for v in local_entry], posture=posture)
    json.dump(rows, open(os.path.join(OUT,'execution_contacts.json'),'w'), indent=1)
    print(f'Generated {len(rows)} execution surface contacts at their authoritative kill ticks.')
    return rows


if __name__ == '__main__':
    generate()
