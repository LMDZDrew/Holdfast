"""Check that the exported Player Animator clips reproduce the authored (solved) poses when played
with Player Animator's part semantics, as read from its 1.0.2-rc1 source:
  - "body" rotates and offsets the whole player about a point 11.2 px (0.7 blocks) above the feet;
  - "torso" turns only the chest box about the neck; head, arms and legs are not its children.
Compares world positions of hands, held-item tip markers, head and feet over every clip.
This is a model of Player Animator, not Player Animator itself; the in-game test still decides."""
import copy, glob, json, os, sys
import numpy as np
import fk
from lib import Anim, OUT
import player_anims as pa
import pa_item


def pa_rig():
    R = copy.deepcopy(fk.rig("survivor_preview.geo.json"))
    geo = fk.load("survivor_preview.geo.json")["minecraft:geometry"][0]
    piv = {b["name"]: np.array([-b["pivot"][0], b["pivot"][1], b["pivot"][2]], float) for b in geo["bones"]}
    piv["body"] = np.array([0, 11.2, 0])
    piv["torso"] = np.array([0, 24, 0])
    parent = {b["name"]: b.get("parent") for b in geo["bones"]}
    for part in ("head", "rightArm", "leftArm", "rightLeg", "leftLeg", "torso"):
        parent[part] = "body"
    order, seen = [], set()

    def visit(n):
        if n in seen:
            return
        if parent.get(n):
            visit(parent[n])
        seen.add(n)
        order.append(n)
    for n in parent:
        visit(n)
    bones = {}
    for n in order:
        p = parent[n]
        rest = piv[n] - (piv[p] if p else np.zeros(3))
        cubes = []
        for fr, to in R["bones"][n]["cubes"]:
            old = np.array([-g["pivot"][0], g["pivot"][1], g["pivot"][2]] if (g := next(b for b in geo["bones"] if b["name"] == n)) else 0)
            cubes.append((fr + old - piv[n], to + old - piv[n]))
        bones[n] = dict(parent=p, rest=rest, cubes=cubes)
    return dict(bones=bones, order=order)


def load_pa(path):
    d = json.load(open(path))
    name, a = next(iter(d["animations"].items()))
    an = Anim(a["animation_length"], a["loop"])
    for bone, chans in a["bones"].items():
        for ch, keys in chans.items():
            near = None
            for t, v in keys.items():
                vec = v["post"] if isinstance(v, dict) else v
                if bone in ("rightItem", "leftItem"):
                    # Held items are exported in Player Animator's hand frame (pa_item.py, whose self test
                    # covers that chain); map them back to the fist pivot this rig turns them about.
                    if ch == "position":
                        continue
                    near = pa_item.from_pa_rotation(vec, near)
                    vec = [float(x) for x in near]
                if bone == "body" and ch == "position":
                    vec = [x / pa.PLAYER_SCALE for x in vec]   # exported in real px (before the model scale)
                an.key(float(t), bone, **({"rot": vec} if ch == "rotation" else {"pos": vec}))
    return name, an


MARKERS = [("rightItem", (0, 0, 0)), ("leftHand", (0, 0, 0)), ("head", (0, 4, 0)), ("rightLeg", (0, -12, 0)),
           ("leftLeg", (0, -12, 0)), ("knifeTip", (0, 0, 0)), ("batSweet", (0, 0, 0)), ("axeEdge", (0, 0, 0))]

if __name__ == "__main__":
    out, _ = pa.build() if "--rebuild" in sys.argv else (None, None)
    keys = json.load(open(os.path.join(OUT, "player_keys.animation.json")))["animations"]
    SR, PR = pa.SR, pa_rig()
    worst = 0.0
    for f in sorted(glob.glob(os.path.join(OUT, "player_animation", "*.json"))):
        name, exported = load_pa(f)
        raw = keys[f"animation.holdfast.player.{name}"]
        authored = Anim(raw["animation_length"], raw["loop"])
        for bone, chans in raw["bones"].items():
            for ch, kk in chans.items():
                for t, v in kk.items():
                    authored.key(float(t), bone, **({"rot": v["post"]} if ch == "rotation" else {"pos": v["post"]}))
        err = 0.0
        for i in range(int(exported.length / 0.05) + 1):
            t = round(i * 0.05, 4)
            A_ = fk.worlds(SR, [authored], t)
            B_ = fk.worlds(PR, [exported], t)
            for bone, p in MARKERS:
                err = max(err, float(np.linalg.norm(fk.point(A_, bone, p) - fk.point(B_, bone, p))))
        worst = max(worst, err)
        print(f"{name:28s} max marker difference {err:.3f} px")
    print(f"worst {worst:.3f} px")
    sys.exit(0 if worst < 1.0 else 1)
