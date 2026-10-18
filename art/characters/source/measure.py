"""Frame-by-frame contact check: how deep the weapon's striking point is inside the walker's skull,
and whether the weapon passes through the head before the authored impact time."""
import json, os
import numpy as np
import fk
import player_anims as pa
import walker_anims as wa

import placement
from lib import Anim, OUT
# Measure the exact generated keys that are about to ship, rather than running the nonlinear solvers again.
raw = json.load(open(os.path.join(OUT, "player_keys.animation.json")))["animations"]
out = {}
for full_name, data in raw.items():
    name = full_name.removeprefix("animation.holdfast.player.")
    if not name.startswith("exec_"):
        continue
    a = Anim(data["animation_length"], data["loop"])
    for bone, channels in data["bones"].items():
        for channel, keys in channels.items():
            for time, value in keys.items():
                a.key(float(time), bone, **({"rot": value["post"]} if channel == "rotation" else {"pos": value["post"]}))
    meta = json.load(open(os.path.join(OUT, "player_animation_extras", name + ".extras.json")))["holdfast_meta"]
    out[name] = (a, meta)
PAIR = placement.load()
SKULL = (np.array([-4, 2, -4]), np.array([4, 8, 4]))   # head-bone local (skull cube); jaw below


def depth_inside(Hinv, p):
    l = (Hinv @ np.array([*p, 1.0]))[:3]
    lo, hi = SKULL
    if np.all(l >= lo) and np.all(l <= hi):
        return float(min(np.min(l - lo), np.min(hi - l)))
    return 0.0


# weapon extent along the item's local -z from the fist, and sample heights (local y)
EXTENT = {"knife": (0.5, -8.5, (0,)), "bat": (4, -17, (0,)), "axe": (3, -14.5, (0, -4.6)), "machete": (0.5, -14, (0, -1.6)),
          "hatchet": (2, -9.5, (0, -3.6)), "katana": (5, -26, (0, -1.0))}
rows = {}
for name, (a, info) in out.items():
    if not name.startswith("exec_"):
        continue
    w = name[5:]
    e = wa.EXECS[w]
    weapon = info["weapon"]
    if weapon not in pa.WEAPON:      # pistol: no blade in the skull; the server's hitscan decides the shot
        continue
    strike = e.get("marker", pa.WEAPON[weapon][0])
    if w.endswith("_ground"):
        react, RIG, dist = pa.W["crawler"][f"react_{w}"], pa.CR, PAIR[f"{w}_crawler"]["dist"]
    else:
        react, RIG, dist = pa.W["react"][f"react_{w}"], pa.WR, e["dist"]
    first_in, last_in, max_d, early = None, None, 0.0, []
    for i in range(int(a.length / 0.01) + 1):
        t = round(i * 0.01, 3)
        Wm = fk.worlds(pa.SR, [a], t)
        Hm = fk.worlds(RIG, [react], t, world=pa.walker_world(dist))["head"]
        Hinv = np.linalg.inv(Hm)
        d = depth_inside(Hinv, fk.point(Wm, strike))
        # any part of the weapon inside the skull?
        R = Wm["rightItem"]
        z0, z1, ys = EXTENT[weapon]
        anyin = max(depth_inside(Hinv, (R @ np.array([0, y, z, 1.0]))[:3]) for z in np.linspace(z0, z1, 21) for y in ys)
        if d > 0.3:
            first_in = first_in if first_in is not None else t
            last_in = t
            max_d = max(max_d, d)
        if anyin > 0.3 and t < e["impact"] - 0.06:
            early.append(t)
    rows[name] = dict(impact=e["impact"], first_in=first_in, last_in=last_in, max_depth=round(max_d, 2),
                      clips_before_impact=early[:3])
    print(f"{name:18s} impact {e['impact']:.2f}  enters {first_in}  leaves {last_in}  depth {max_d:.2f}px  "
          f"early clipping {early[:3] if early else 'none'}")
import os
json.dump(rows, open(os.path.join(os.path.dirname(__file__), "..", "build", "contact.json"), "w"), indent=1)
