"""Sample authored carries at 20 Hz: every READY carry (hold_<weapon>) and REST carry (rest_<weapon>).

  - no arm inside the chest; two-hand support hand on the handle within its slide range (<= 1.5 px);
  - the weapon's own geometry stays out of the head, torso, legs and the free arm (sampled on its cubes); the
    carrying arm is exempt within 5 px of the fist, where the hand closes on the grip;
  - a carry with an authored axis (player_weapons.STANCES) points within 3 degrees of it, and a one-hand carry
    without one hangs blade down (machete); solved carries are judged by their geometry only;
  - every weapon point stays at least 0.5 px above the ground.

Uses the regenerated authoring keys, not a new solver run. This checks geometry; Andrew still judges appearance.
"""
import json
import os
import numpy as np
from lib import Anim, OUT
import fk
import player_anims as pa
from player_weapons import STANCES, REST_FAMILIES

BODY = ("head", "torso", "rightLeg", "leftLeg", "leftArm", "rightArm")


def weapon_points(W, weapon):
    pts = []
    for lo, hi in pa.SR["bones"][weapon]["cubes"]:
        lo, hi = np.array(lo, float), np.array(hi, float)
        n = np.maximum(2, np.ceil(np.abs(hi - lo)).astype(int) + 1)
        for x in np.linspace(lo[0], hi[0], n[0]):
            for y in np.linspace(lo[1], hi[1], n[1]):
                for z in np.linspace(lo[2], hi[2], n[2]):
                    pts.append((W[weapon] @ np.array([x, y, z, 1.0]))[:3])
    return np.array(pts)


def depth_in(W, bone, p):
    q = (np.linalg.inv(W[bone]) @ np.array([*p, 1.0]))[:3]
    worst = -1e9
    for lo, hi in pa.SR["bones"][bone]["cubes"]:
        lo, hi = np.minimum(lo, hi), np.maximum(lo, hi)
        worst = max(worst, float(np.min(np.minimum(q - lo, hi - q))))
    return worst


def load(name):
    data = keys["animation.holdfast.player." + name]
    a = Anim(data["animation_length"], data["loop"])
    for bone, channels in data["bones"].items():
        for channel, frames in channels.items():
            for time, value in frames.items():
                a.key(float(time), bone, **({"rot": value["post"]} if channel == "rotation" else {"pos": value["post"]}))
    return a


keys = json.load(open(os.path.join(OUT, "player_keys.animation.json")))["animations"]
clips = [("hold_" + w, w, "ready") for w in ("knife", "machete", "hatchet", "katana", "axe", "bat")]
clips += [("rest_" + w, w, "rest") for w in REST_FAMILIES]
failures = []
for name, weapon, stance in clips:
    a = load(name)
    spec = STANCES.get((weapon, stance))
    two = spec.get("two", False) if spec else weapon in ("katana", "axe", "bat")
    penetration = grip = clash = 0.0
    clash_where = ""
    for t in np.arange(0, a.length, .05):
        W = fk.worlds(pa.SR, [a], t)
        penetration = max(penetration, pa.body_penetration(W))
        fist = fk.point(W, "rightItem")
        if two:
            # the support hand may sit anywhere on the handle within the solver's slide range (item z -4..+4)
            hand = fk.point(W, "leftHand")
            grip = max(grip, min(np.linalg.norm(hand - (W["rightItem"] @ np.array([0, 0, z, 1]))[:3])
                                 for z in np.linspace(-4, 4, 33)))
        for p in weapon_points(W, weapon):
            for bone in BODY:
                if bone == "rightArm" and np.linalg.norm(p - fist) < 5.0:
                    continue
                if bone == "leftArm" and two and np.linalg.norm(p - fk.point(W, "leftHand")) < 5.0:
                    continue
                d = depth_in(W, bone, p)
                if d > clash:
                    clash, clash_where = d, f"{bone} t={t:.2f}"
        handle = -W["rightItem"][:3, 2]
        floor = min(p[1] for p in weapon_points(W, weapon))
        if floor < 0.5:
            failures.append(f"{name} t={t:.2f}: weapon {0.5 - floor:.2f} px too close to the ground")
        if spec and spec.get("solve"):
            pass
        elif spec:
            want = np.array(spec["axis"], float) / np.linalg.norm(spec["axis"])
            off = np.degrees(np.arccos(np.clip(handle @ want, -1, 1)))
            if off > 3.0:
                failures.append(f"{name} t={t:.2f}: handle {off:.1f} deg from its authored axis")
        elif not two and -W["rightItem"][1, 2] >= -.8:
            failures.append(f"{name} t={t:.2f}: blade not pointing down")
    note = ""
    print(f"{name}: chest depth {penetration:.4f} px; support-grip miss {grip:.3f} px; "
          f"weapon inside body {max(0.0, clash):.2f} px{' (' + clash_where + ')' if clash > 0 else ''}{note}")
    if penetration > 1e-6:
        failures.append(f"{name}: arm {penetration:.3f} px inside the chest")
    if grip > 1.5:
        failures.append(f"{name}: support hand {grip:.2f} px off the handle")
    if clash > 0.3:
        failures.append(f"{name}: weapon {clash:.2f} px inside the {clash_where}")
if failures:
    print("FAIL\n  " + "\n  ".join(failures))
    raise SystemExit(1)
print("All carry geometry checks passed (visual quality still needs the game).")
