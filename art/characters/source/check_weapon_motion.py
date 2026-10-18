"""Held-weapon motion check for carries, swings and the charge clips (Andrew 2026-10-17: weapons "spazzing out"
during the animations). Reads build/player_keys.animation.json (authoring keys) and the exported Player Animator
files, and for every clip baked with geodesic held items (extras holdfast_meta, see player_anims.geodesic_item):

  - loop seam: a looping clip's weapon orientation at its end must equal its start (< 1 degree);
  - wander: the weapon path between authored keys, as Player Animator plays the 20 Hz bake (linear per channel),
    must stay within WANDER_LIMIT of the direct rotation, judged between the whole-tick samples inside each interval;
  - speed: reports the peak weapon turn per tick during wind-up, around the strike, and in recovery;
  - torso: points along the weapon must not go deeper into the torso box than the per-channel bake did (0.25 px).

Numbers only; how a swing reads in game still needs a look. Exit status 1 on a failed check.
"""
import json
import math
import os
import sys

import numpy as np

import fk
import player_anims as pa
import pa_item
from lib import Anim, sample, bb_matrix

HERE = os.path.dirname(__file__)
BUILD = os.path.join(HERE, "..", "build")
EXPORT = os.path.join(HERE, "..", "export", "assets", "holdfast")
# The per-channel Catmull bake reached 2.1x on the swings (2026-10-17). Linear per-channel playback within one tick
# still bends the fastest turns (50-110 deg/tick) a little; that remainder is reported, and only a real detour fails.
WANDER_LIMIT = 1.25
MARKER = {"knife": "knifeTip", "machete": "macheteEdge", "hatchet": "hatchetEdge", "katana": "katanaTip",
          "axe": "axeEdge", "bat": "batSweet"}


def ang(R1, R2):
    return math.degrees(math.acos(max(-1.0, min(1.0, (np.trace(R1.T @ R2) - 1) / 2))))


def anim_from_json(j):
    a = Anim(j["animation_length"], j["loop"])
    for bone, chans in j["bones"].items():
        for ch, keys in chans.items():
            for t, v in keys.items():
                v = v["post"] if isinstance(v, dict) else v
                a.key(float(t), bone, **({"rot": v} if ch == "rotation" else {"pos": v}))
    return a


def linear(a, bone, t):
    """Player Animator reads the baked keys as linear (lerp_mode "linear"); sample them the same way."""
    keys = a.tracks.get(bone, {}).get("rotation")
    if not keys:
        return [0, 0, 0]
    ts = sorted(keys)
    t = (t % a.length) if a.loop is True and t > ts[-1] else min(max(t, ts[0]), ts[-1])
    for t1, t2 in zip(ts, ts[1:]):
        if t1 <= t <= t2:
            u = (t - t1) / (t2 - t1)
            return [x + (y - x) * u for x, y in zip(keys[t1], keys[t2])]
    return keys[ts[-1]]


def weapon_world(a, t):
    """Visible weapon orientation (arm x wrist) in Minecraft model space, as Player Animator interpolates the
    exported file: every channel linear between whole-tick keys; the item in its hand frame (pa_item.A)."""
    return pa_item.zyx(linear(a, "rightArm", t)) @ pa_item.A @ pa_item.zyx(linear(a, "rightItem", t)) @ pa_item.A.T


def exported(name):
    j = json.load(open(os.path.join(EXPORT, "player_animation", name + ".json")))["animations"][name]
    a = Anim(j["animation_length"], j["loop"])
    for bone, chans in j["bones"].items():
        for t, v in chans.get("rotation", {}).items():
            a.key(float(t), bone, rot=v["post"])
    return a


def torso_depth(a, t, weapon):
    W = fk.worlds(pa.SR, [a], t)
    inv = np.linalg.inv(W["torso"])
    fist = (W['rightArm'] @ np.array([*pa.SR['bones']['rightItem']['rest'],1.]))[:3]
    tip = fk.point(W, MARKER[weapon])
    worst = -1e9
    for u in np.linspace(0.3, 1.0, 6):      # skip the fist itself: the hand may rest against the body
        q = (inv @ np.array([*(fist + (tip - fist) * u), 1.0]))[:3]
        worst = max(worst, float(np.min(np.minimum(q - pa.TORSO_BOX[0], pa.TORSO_BOX[1] - q))))
    return worst


def main():
    keys = json.load(open(os.path.join(BUILD, "player_keys.animation.json")))["animations"]
    failures, lines = [], []
    for full, j in sorted(keys.items()):
        name = full.split(".")[-1]
        xpath = os.path.join(EXPORT, "player_animation_extras", name + ".extras.json")
        if not os.path.exists(xpath):
            continue
        ex = json.load(open(xpath))
        weapon = (ex.get("holdfast_meta") or {}).get("weapon")
        if not (name.startswith(("swing_", "hold_", "rest_", "bat_charge"))):
            continue
        if "rightItem" not in j["bones"]:
            continue
        a = anim_from_json(j)
        baked = a.baked()                                   # authoring frame, for the torso check
        if (ex.get("holdfast_meta") or {}).get("geodesic_items"):
            pa.geodesic_item(a, baked, "rightItem", "rightArm")
        played = exported(name)                             # what Player Animator interpolates
        if name.startswith('swing_'):
            turns = played.tracks.get('body',{}).get('rotation',{}).values()
            if any(abs(v[1])>.01 or abs(v[2])>.01 for v in turns):
                failures.append(f'{name}: ordinary clicks must not swivel torso yaw/roll')
        L = a.length
        if a.loop:
            seam = ang(weapon_world(played, 0.0), weapon_world(played, L - 1e-6))
            if seam > 1.0:
                failures.append(f"{name}: loop seam {seam:.1f} deg")
        kts = sorted(float(t) for t in j["bones"]["rightItem"]["rotation"])
        wander = 1.0
        for k1, k2 in zip(kts, kts[1:]):
            # Player Animator keys whole ticks, so an authored key between samples is a corner of the 20 Hz bake;
            # judge each interval between the samples that lie inside it.
            t1, t2 = math.ceil(k1 * 20 - 1e-6) / 20, math.floor(k2 * 20 + 1e-6) / 20
            if t2 - t1 < 0.05 - 1e-9:
                continue
            ts = np.linspace(t1, t2, 21)
            direct = ang(weapon_world(played, t1), weapon_world(played, t2))
            if direct < 5:
                continue
            path = sum(ang(weapon_world(played, x), weapon_world(played, y)) for x, y in zip(ts, ts[1:]))
            wander = max(wander, path / direct)
        if wander > WANDER_LIMIT:
            failures.append(f"{name}: weapon path {wander:.2f}x the direct rotation between keys")
        timeline = (ex["animations"][name].get("timeline") or {})
        strike = next((float(t) for t, e in timeline.items() if e == "strike"), None)
        speeds = [(t, ang(weapon_world(played, t), weapon_world(played, min(L, t + 0.05)))) for t in np.arange(0, L - 1e-6, 0.05)]
        if strike is not None:
            wind = max([s for t, s in speeds if t < strike - 0.1] or [0])
            hit = max([s for t, s in speeds if abs(t - strike) <= 0.1] or [0])
            rec = max([s for t, s in speeds if t > strike + 0.1] or [0])
            speed = f"wind-up {wind:5.1f}, strike {hit:5.1f}, recovery {rec:5.1f} deg/tick"
        else:
            speed = f"peak {max(s for _, s in speeds):5.1f} deg/tick"
        depth = ""
        if weapon in MARKER:
            deep_geo = max(torso_depth(baked, t, weapon) for t in np.arange(0, L + 1e-6, 0.05))
            deep_cat = max(torso_depth(a, t, weapon) for t in np.arange(0, L + 1e-6, 0.05))
            depth = f"; torso depth {max(0.0, deep_geo):.2f} px (per-channel bake {max(0.0, deep_cat):.2f})"
            if deep_geo > max(0.0, deep_cat) + 0.25:
                failures.append(f"{name}: weapon {deep_geo:.2f} px inside the torso (per-channel bake {deep_cat:.2f})")
        lines.append(f"{name:22s} wander {wander:.2f}x; {speed}{depth}")
    print("\n".join(lines))
    if failures:
        print("FAIL\n  " + "\n  ".join(failures))
        return 1
    print(f"weapon motion: {len(lines)} clips checked, all passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
