"""All-angle executions (core ExecutionRules.align): play each upright player execution clip against its left, right
and rear walker reactions, placed exactly as the server places them (walker origin so its skull at impact meets the
front pair's strike point), and measure like measure.py: when the striking point enters the skull, how deep, and
whether any part of the weapon is inside the skull before the impact. The server kill tick comes from the front
pair's measurement; a variant whose entry differs by more than 0.05 s from the front fails.

Pistol: no blade; reports only that the skull centre meets the front strike point. Visual quality still needs the game.
"""
import json
import math
import os
import sys

import numpy as np

import fk
import measure as m
import placement
import player_anims as pa
import walker_anims as wa
import contact_geometry as geometry
import execution_contacts

PAIR = placement.load()
SKULL = PAIR["exec_skull"]


def walker_world_at(name, side):
    """The pair-frame walker transform the server uses for reaction react_<name><side> (see ExecutionRules)."""
    e = wa.EXECS[name]
    front, c = SKULL[f"react_{name}"], SKULL[f"react_{name}{side}"]
    theta = 180.0 if not side else wa.DIRECTIONS[side[1:]]
    sx, sz = -front[0], -e["dist"] - front[1]
    ct, st = math.cos(math.radians(theta)), math.sin(math.radians(theta))
    ox, oz = sx - (ct * c[0] + st * c[1]), sz - (-st * c[0] + ct * c[1])
    M = fk.entity(ox / pa.PLAYER_SCALE, oz / pa.PLAYER_SCALE, theta)
    M[:3, :3] /= pa.PLAYER_SCALE
    return M


def run():
    failures, lines, pairs = [], [], 0
    for name in wa.UPRIGHT_EXECS:
        clip = "exec_" + name
        if clip not in m.out:
            if name == 'pistol':
                # Native TaCZ poses are not stored in the authoring-key file. Validate skull alignment without
                # rebuilding or altering those accepted player clips.
                t=wa.EXECS[name]['impact']-.02
                front=fk.worlds(pa.WR,[pa.W['react']['react_pistol']],t,world=walker_world_at(name,''))['head']
                for side in ('','_left','_right','_rear'):
                    head=fk.worlds(pa.WR,[pa.W['react']['react_pistol'+side]],t,world=walker_world_at(name,side))['head']
                    gap=float(np.linalg.norm((head @ np.array([0,5,0,1.]))[:3]-(front @ np.array([0,5,0,1.]))[:3]))
                    lines.append(f'{clip}{side}: native-pose skull alignment {gap:.3f}px')
                    pairs+=1
                    if gap>1: failures.append(f'{clip}{side}: skull alignment exceeds 1px')
            continue
        a, info = m.out[clip]
        weapon = info["weapon"]
        I = wa.EXECS[name]["impact"]
        entries = {}
        for side in ("",) + tuple("_" + d for d in wa.DIRECTIONS):
            pairs += 1
            react = pa.W["react"][f"react_{name}{side}"]
            world = walker_world_at(name, side)
            if weapon not in pa.WEAPON:
                Hm = fk.worlds(pa.WR, [react], I - 0.02, world=world)["head"]
                Hf = fk.worlds(pa.WR, [pa.W["react"][f"react_{name}"]], I - 0.02, world=walker_world_at(name, ""))["head"]
                gap = float(np.linalg.norm((Hm @ np.array([0, 5, 0, 1.0]))[:3] - (Hf @ np.array([0, 5, 0, 1.0]))[:3]))
                lines.append(f"{clip}{side or ' (front)':10s} skull centre {gap:.2f} px from the front pair's at impact")
                if gap > 1.0:
                    failures.append(f"{clip}{side}: skull {gap:.2f} px off the strike point")
                continue
            strike = wa.EXECS[name].get("marker", pa.WEAPON[weapon][0])
            first_in, max_d, early = None, 0.0, []
            inserted = []
            for i in range(int(a.length / 0.01) + 1):
                t = round(i * 0.01, 3)
                Wm = fk.worlds(pa.SR, [a], t)
                Hinv = np.linalg.inv(fk.worlds(pa.WR, [react], t, world=world)["head"])
                d = m.depth_inside(Hinv, fk.point(Wm, strike))
                if I <= t <= I + .10:
                    inserted.append(geometry.penetration(Hinv,Wm,weapon,strike))
                R = Wm["rightItem"]
                z0, z1, ys = m.EXTENT[weapon]
                anyin = max(m.depth_inside(Hinv, (R @ np.array([0, y, z, 1.0]))[:3]) for z in np.linspace(z0, z1, 21) for y in ys)
                if d > 0.3:
                    first_in = first_in if first_in is not None else t
                    max_d = max(max_d, d)
                if anyin > 0.3 and t < I - 0.06:
                    early.append(t)
            entries[side] = first_in
            lines.append(f"{clip}{side or ' (front)':10s} enters {first_in}  depth {max_d:.2f}px  early clipping "
                         f"{early[:3] if early else 'none'}")
            if first_in is None:
                failures.append(f"{clip}{side}: the weapon never reaches the skull")
            if early:
                failures.append(f"{clip}{side}: weapon inside the skull at {early[0]} s, before the impact")
            threshold, metric = geometry.minimum(name)
            if threshold:
                peak = max(v[metric] for v in inserted)
                lines.append(f"  {clip}{side}: {metric} impact {inserted[0][metric]:.2f}, hold minimum {min(v[metric] for v in inserted):.2f}, peak {peak:.2f} px")
                if peak < threshold or min(v[metric] for v in inserted) < threshold-.25:
                    failures.append(f'{clip}{side}: blade insertion below {threshold}px target')
        front = entries.get("")
        for side, t in entries.items():
            if side and front is not None and t is not None and abs(t - front) > 0.05:
                failures.append(f"{clip}{side}: enters at {t} s, front pair at {front} s (server kill tick from front)")
    for name in ('knife_ground','katana_ground'):
        a,info=m.out['exec_'+name]
        weapon=info['weapon'];I=wa.EXECS[name]['impact']
        for posture in ('CRAWLER','PRONE'):
            pairs += 1
            target='react_'+name+('_prone' if posture=='PRONE' else '')
            rig=pa.WR if posture=='PRONE' else pa.CR
            react=pa.W['react'][target] if posture=='PRONE' else pa.W['crawler'][target]
            world=execution_contacts.pair_world(name,target,posture,PAIR)
            rows=[]
            for t in np.arange(I,I+.1001,.01):
                Wm=fk.worlds(pa.SR,[a],float(t))
                inverse=np.linalg.inv(fk.worlds(rig,[react],float(t),world=world)['head'])
                rows.append(geometry.penetration(inverse,Wm,weapon,pa.WEAPON[weapon][0],cut=weapon=='katana'))
            threshold,metric=geometry.minimum(name)
            peak=max(v[metric] for v in rows)
            lines.append(f'exec_{name}/{posture}: {metric} impact {rows[0][metric]:.2f}, hold minimum {min(v[metric] for v in rows):.2f}, peak {peak:.2f}px')
            if peak<threshold or min(v[metric] for v in rows)<threshold-.25:
                failures.append(f'exec_{name}/{posture}: insertion below {threshold}px target')
    print("\n".join(lines))
    if failures:
        print("FAIL\n  " + "\n  ".join(failures))
        return 1
    print(f"execution approach variants: {pairs} pairs checked, all passed (native pistols: skull alignment only)")
    return 0


if __name__ == "__main__":
    sys.exit(run())
