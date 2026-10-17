"""Pairing distances for ground attacks and ground executions, derived from the rigs.

All distances are in px (1/16 block) along the survivor's facing, walker turned to face them.
- prone/crawler ankle grab: the attacker's hands land just in front of the survivor's shins.
- <weapon>_ground: the crawler's skull centre sits GROUND_TARGET_Z[weapon] in front of the survivor's feet;
  the prone walker is placed so its skull is at the same spot."""
import json, os
import numpy as np
import fk
import walker_anims as wa
from lib import OUT

SHIN_FRONT_Z = -2.5          # survivor shins span z -2..2; grab just in front
KNIFE_GROUND_TARGET_Z = -12.0
# skull centre in front of the survivor's feet for each ground execution: longer weapons stand further back
GROUND_TARGET_Z = {"knife": KNIFE_GROUND_TARGET_Z, "machete": -17.0, "bat": -22.0, "axe": -24.0, "pistol": -20.0}


def local_point(R, anim, t, bone, pt):
    W = fk.worlds(R, [anim], t)
    return fk.point(W, bone, pt)


def compute():
    W = wa.build()
    WR, CR = fk.rig("walker.geo.json"), fk.rig("walker_crawler.geo.json")
    out = {}

    def hands(R, anim):
        # fists: bottom of the forearms
        Wm = fk.worlds(R, [anim], 0.0)
        r = fk.point(Wm, "rightForearm", (-0.0, -5.5, 0))
        l = fk.point(Wm, "leftForearm", (0.0, -5.5, 0))
        return (r + l) / 2

    # in walker-local three.js space the walker faces -z; turned 180 deg at distance d, a local point p
    # lands at world z = -d - p_z  (x flips sign too).
    for key, R, anim in (("ankle_prone", WR, W["full"]["prone_ankle_hold"]), ("ankle_crawler", CR, W["crawler"]["crawler_ankle_hold"])):
        h = hands(R, anim)
        out[key] = dict(dist=round(float(-SHIN_FRONT_Z - h[2]), 2), hands_height=round(float(h[1]), 2))
    for w in wa.GROUND_EXECS:
        I = wa.EXECS[f"{w}_ground"]["impact"]
        for key, R, anim in ((f"{w}_ground_crawler", CR, W["crawler"][f"react_{w}_ground"]),
                             (f"{w}_ground_prone", WR, W["react"][f"react_{w}_ground_prone"])):
            Wm = fk.worlds(R, [anim], I - 0.02)
            c = fk.point(Wm, "head", (0, 5, 0))
            out[key] = dict(dist=round(float(-GROUND_TARGET_Z[w] - c[2]), 2), skull_height=round(float(c[1]), 2),
                            skull_x=round(float(-c[0]), 2))
    return out


def load():
    p = os.path.join(OUT, "pairing.json")
    if not os.path.exists(p):
        json.dump(compute(), open(p, "w"), indent=1)
    return json.load(open(p))


if __name__ == "__main__":
    res = compute()
    json.dump(res, open(os.path.join(OUT, "pairing.json"), "w"), indent=1)
    print(json.dumps(res, indent=1))
