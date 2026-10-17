"""Handgun executions (TaCZ pistol class only; decisions.md). Authored directly in Player Animator part space
("pa_native" clips) so they start from and return to TaCZ 1.1.8's own third-person pistol hold, read from the
installed default gun pack (assets/tacz/player_animator/pistol_default.player_animation.json, hold_upper):

  right_arm rot (-90.5, 0, 2.5)  pos (2.275, -2, 1)
  left_arm  rot (-97.94487, 47.30096, -9.05507)  pos (-2.45, -1.75, -2.225)
  torso     rot (8.44236, 27.24513, 1.38714)     pos (0, -0.35, -1)     (Player Animator torso = chest box only)
  head      rot (0, 0, 0)                        pos (0, -0.125, -1)

Values are written exactly as TaCZ writes them (same GeckoLib-format reader), so the first and last frames match
its hold and the hand-off to TaCZ's layers has no pop. In between, both arms keep TaCZ's two-handed grip and are
turned together so the barrel (along the right arm) points at the target's forehead, solved from the real
shoulder positions including body lean, step-in and the 15/16 player render scale.

The gun is TaCZ's own item and model; these clips never key rightItem/leftItem. The muzzle flash, shot sound and
kill are the server's (S7-S9): the "fire" timeline event is a presentation cue only.
"""
import math
import numpy as np
from scipy.optimize import minimize
from lib import Anim, bb_matrix
import pa_item
import player_anims as pa
import walker_anims as wa
import placement

PS = pa.PLAYER_SCALE
PAIR = placement.load()

HOLD = {
    "rightArm": dict(rot=[-90.5, 0.0, 2.5], pos=[2.275, -2.0, 1.0]),
    "leftArm": dict(rot=[-97.94487, 47.30096, -9.05507], pos=[-2.45, -1.75, -2.225]),
    "torso": dict(rot=[8.44236, 27.24513, 1.38714], pos=[0.0, -0.35, -1.0]),
    "head": dict(rot=[0.0, 0.0, 0.0], pos=[0.0, -0.125, -1.0]),
}
LEGS0 = dict(rightLeg=[0, 0, 0], leftLeg=[0, 0, 0])

# Three.js authoring space (x right, y up, z back, model px from the feet). Shoulder pivots of the vanilla model,
# moved by TaCZ's arm position keys (Player Animator negates y for part positions; x is the model's left).
SHOULDER = {"rightArm": np.array([5.0, 22.0, 0.0]), "leftArm": np.array([-5.0, 22.0, 0.0])}
BODY_PIVOT = np.array([0.0, 11.2, 0.0])
S = np.diag([-1.0, -1.0, 1.0])          # three.js <-> Minecraft model space (pa_item docstring)


def shoulder_world(arm, body_pos, lean):
    off = np.array(HOLD[arm]["pos"]) * [-1, -1, 1]                 # JSON part offset -> three.js px
    rest = SHOULDER[arm] + off
    L = bb_matrix(lean)
    bp = np.array([-body_pos[0], body_pos[1], body_pos[2]])          # bedrock position convention -> three.js
    return BODY_PIVOT + L @ (rest - BODY_PIVOT) + bp, L


def arm_dir(rot):
    """Unit direction shoulder -> hand, Minecraft model space, for a part rotation in degrees."""
    return pa_item.zyx(rot) @ np.array([0.0, 1.0, 0.0])


def aim(target, body_pos, lean):
    """Rotation deltas (dx, dy) for both arms so the right arm's barrel line passes through `target` (three.js)."""
    sh, L = shoulder_world("rightArm", body_pos, lean)
    want = target - sh
    want = S @ (L.T @ (want / np.linalg.norm(want)))                 # into the body frame, Minecraft axes
    base = np.array(HOLD["rightArm"]["rot"])

    def f(d):
        r = base + [d[0], d[1], 0.0]
        return np.sum((arm_dir(r) - want) ** 2) + 1e-6 * np.sum(d ** 2)

    best = min((minimize(f, g, method="Nelder-Mead", options={"xatol": 1e-4, "fatol": 1e-10})
                for g in ([0, 0], [20, 0], [-20, 0], [0, 20], [0, -20])), key=lambda r: r.fun)
    miss = math.degrees(math.acos(np.clip(arm_dir(base + [*best.x, 0]) @ want, -1, 1)))
    return best.x, miss


def arms_aimed(d, extra=(0.0, 0.0)):
    out = {}
    for arm in ("rightArm", "leftArm"):
        r = list(HOLD[arm]["rot"])
        r[0] += d[0] + extra[0]
        r[1] += d[1] + extra[1]
        out[arm] = r
    return out


def key_pose(a, t, arms, body_pos, lean, head, legs=None, torso=None):
    for arm, r in arms.items():
        a.key(t, arm, rot=r, pos=HOLD[arm]["pos"])
    a.key(t, "torso", rot=torso or HOLD["torso"]["rot"], pos=HOLD["torso"]["pos"])
    a.key(t, "head", rot=head, pos=HOLD["head"]["pos"])
    a.key(t, "body", rot=lean, pos=body_pos)
    for leg, v in (legs or LEGS0).items():
        a.key(t, leg, rot=v)


def hold_pose(a, t):
    key_pose(a, t, {k: HOLD[k]["rot"] for k in ("rightArm", "leftArm")}, [0, 0, 0], [0, 0, 0], HOLD["head"]["rot"])


def forehead(dist_real, height_real, depth=0.0):
    """Target in the player's three.js model space for a skull point `height_real` up, `dist_real` ahead (real px)."""
    return np.array([0.0, height_real / PS, -(dist_real + depth) / PS])


def standing():
    e = wa.EXECS["pistol"]
    I, L = e["impact"], e["length"]
    a = Anim(L, False)
    rep = []
    hold_pose(a, 0.0)
    target = forehead(e["dist"], 29.5)
    # raise the muzzle to the forehead while stepping in, head following the sights
    for t, body_pos, lean, head in ((e["commit"], [0, -0.2, -1.2], [4, 0, 0], [-6, 0, 0]),
                                    (I - 0.06, [0, -0.3, -1.6], [5, 0, 0], [-8, 0, 0]),
                                    (I, [0, -0.3, -1.6], [5, 0, 0], [-8, 0, 0])):
        d, miss = aim(target, body_pos, lean)
        key_pose(a, t, arms_aimed(d), body_pos, lean, head)
        rep.append(f"exec_pistol t={t:.2f} barrel off forehead {miss:.2f} deg")
    # recoil: muzzle flips up, shoulders take it, a blink of a flinch
    d, _ = aim(target, [0, -0.2, -1.2], [2, 0, 0])
    key_pose(a, I + 0.05, arms_aimed(d, (-14, 2)), [0, -0.2, -1.2], [2, 0, 0], [-12, 2, 2])
    key_pose(a, I + 0.2, arms_aimed(d, (-4, 0)), [0, -0.1, -0.8], [3, 0, 0], [-4, 0, 0])
    # watch it drop, then come back to TaCZ's hold
    d, _ = aim(forehead(e["dist"] + 6, 10), [0, 0, -0.4], [6, 0, 0])
    key_pose(a, 0.75, arms_aimed(d), [0, 0, -0.4], [6, 0, 0], [14, 0, 0])
    hold_pose(a, L)
    a.event(e["commit"], "commit").event(I, "fire").event(I, "kill")
    a.sound(0.1, "holdfast:player.exert_light").sound(I + 0.4, "holdfast:player.breath_heavy")
    return a, dict(impacts=[(I, 4.0)], weapon="pistol", pa_native=True), rep


def ground():
    """Over a prone walker or crawler, the pistol pointed down into the back of its skull."""
    e = wa.EXECS["pistol_ground"]
    I, L = e["impact"], e["length"]
    a = Anim(L, False)
    rep = []
    hold_pose(a, 0.0)
    rx = pa.W["crawler"]["react_pistol_ground"]
    dist = PAIR["pistol_ground_crawler"]["dist"]
    crouch_legs = dict(rightLeg=[-8, 0, 2], leftLeg=[-20, 0, -2])
    for t, body_pos, lean, head, legs in ((e["commit"], [0, -1.6, -0.6], [16, 0, 0], [24, 0, 0], crouch_legs),
                                          (I - 0.06, [0, -2.2, -1.0], [20, 0, 0], [30, 0, 0], crouch_legs),
                                          (I, [0, -2.2, -1.0], [20, 0, 0], [30, 0, 0], crouch_legs)):
        Hm, _ = pa.head_frame(rx, t, dist, pa.CR)
        target = pa.hp(Hm, (0, 6.5, 0))                                # back of the skull, solver space
        d, miss = aim(target, body_pos, lean)
        key_pose(a, t, arms_aimed(d), body_pos, lean, head, legs)
        rep.append(f"exec_pistol_ground t={t:.2f} barrel off skull {miss:.2f} deg")
    key_pose(a, I + 0.05, arms_aimed(d, (-12, 1)), [0, -2.0, -0.8], [16, 0, 0], [24, 2, 2], crouch_legs)
    key_pose(a, I + 0.3, arms_aimed(d, (-6, 0)), [0, -1.6, -0.6], [14, 0, 0], [26, 0, 0], crouch_legs)
    key_pose(a, 0.9, arms_aimed(d, (-30, 0)), [0, -0.6, -0.2], [6, 0, 0], [12, 0, 0])
    hold_pose(a, L)
    a.event(e["commit"], "commit").event(I, "fire").event(I, "kill")
    a.sound(0.12, "holdfast:player.exert_light")
    return a, dict(impacts=[(I, 3.0)], weapon="pistol", pa_native=True), rep


def build():
    out, rep = {}, []
    for name, fn in (("exec_pistol", standing), ("exec_pistol_ground", ground)):
        a, info, r = fn()
        out[name] = (a, info)
        rep += r
    return out, rep


if __name__ == "__main__":
    _, rep = build()
    print("\n".join(rep))
