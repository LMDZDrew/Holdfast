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
    failures, lines = [], []
    for name in wa.UPRIGHT_EXECS:
        clip = "exec_" + name
        if clip not in m.out:
            continue
        a, info = m.out[clip]
        weapon = info["weapon"]
        I = wa.EXECS[name]["impact"]
        entries = {}
        for side in ("",) + tuple("_" + d for d in wa.DIRECTIONS):
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
            for i in range(int(a.length / 0.01) + 1):
                t = round(i * 0.01, 3)
                Wm = fk.worlds(pa.SR, [a], t)
                Hinv = np.linalg.inv(fk.worlds(pa.WR, [react], t, world=world)["head"])
                d = m.depth_inside(Hinv, fk.point(Wm, strike))
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
        front = entries.get("")
        for side, t in entries.items():
            if side and front is not None and t is not None and abs(t - front) > 0.05:
                failures.append(f"{clip}{side}: enters at {t} s, front pair at {front} s (server kill tick from front)")
    print("\n".join(lines))
    if failures:
        print("FAIL\n  " + "\n  ".join(failures))
        return 1
    print(f"execution approach variants: {len(lines)} pairs checked, all passed")
    return 0


if __name__ == "__main__":
    sys.exit(run())
