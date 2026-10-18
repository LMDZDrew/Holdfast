"""Player animations (Player Animator) for grabs and head executions, plus the extra tracks our
core mod applies: a first-person camera track and the held-item wrist track (rightItem).

Weapon contact is solved, not eyeballed: at each key that names a target, the right arm and
wrist are solved so the weapon's striking point lands on the walker's head (skull top, side or
face) with the blade or edge facing into it. Two-handed weapons then solve the left arm onto
the handle grip."""
import math, os, json
import numpy as np
from scipy.optimize import minimize
from lib import Anim, OUT, write_json, sample, fmt_t
import fk
import walker_anims as wa

SR = fk.rig("survivor_preview.geo.json")
WR = fk.rig("walker.geo.json")
W = wa.build()
EYE = 25.92                      # 1.62 blocks
PLAYER_BONES = ["body", "torso", "head", "rightArm", "leftArm", "rightLeg", "leftLeg"]

WEAPON = {  # strike marker, grip marker for the left hand (None = one-handed)
    "knife": ("knifeTip", None), "bat": ("batSweet", "batGrip"),
    "axe": ("axeEdge", "axeGrip"), "machete": ("macheteEdge", None),
    "hatchet": ("hatchetEdge", None), "katana": ("katanaEdge", "katanaGrip"),
}


# Vanilla PlayerRenderer.scale draws the player model at 15/16; walkers render at 1. The solver works in player
# model pixels, so a walker (and its distance) is 16/15 larger there. Before this, knife strikes landed ~1 px
# short and ~1.7 px low in game, below the skull (Andrew, 2026-10-16).
PLAYER_SCALE = 0.9375
# The installed PlayerRendererMixin applies its 0.7-block pivot BEFORE PlayerRenderer's 15/16 model scale.
# Thus the runtime pivot is 11.9467 model pixels, not the authoring rig's 11.2. Correct the exported body
# translation rather than changing a solved weapon pose to hide this frame mismatch.
PA_BODY_PIVOT = 0.7 * 16 / PLAYER_SCALE


def walker_world(dist):
    M = fk.entity(0, -dist / PLAYER_SCALE, 180)
    M[:3, :3] /= PLAYER_SCALE
    return M


CR = fk.rig("walker_crawler.geo.json")


def _arm_line(arm):
    fr, to = SR["bones"][arm]["cubes"][0]
    c = (fr + to) / 2
    return [np.array([c[0], y, c[2]]) for y in np.linspace(to[1] - 1, fr[1], 7)]


ARM_LINE = {arm: _arm_line(arm) for arm in ("rightArm", "leftArm")}
TORSO_BOX = SR["bones"]["torso"]["cubes"][0]
ARM_HALF = 1.6                   # arm half-thickness less a little slack, px
W_BODY = 6.0                     # solver weight on arm-through-torso depth (px^2); Andrew saw it in game 2026-10-16


def body_penetration(Wm, arms=("rightArm", "leftArm")):
    """Deepest point (px) at which an arm's centre line, thickened by ARM_HALF, is inside the torso box.
    Vanilla arms are rigid, so a hand reaching across the body can drive the arm through the chest."""
    inv = np.linalg.inv(Wm["torso"])
    lo, hi = TORSO_BOX[0] - ARM_HALF, TORSO_BOX[1] + ARM_HALF
    worst = 0.0
    for arm in arms:
        for p in ARM_LINE[arm]:
            q = (inv @ Wm[arm] @ np.array([*p, 1.0]))[:3]
            worst = max(worst, float(np.min(np.minimum(q - lo, hi - q))))
    return worst


def head_frame(react, t, dist, R=None):
    Wm = fk.worlds(R or WR, [react], t, world=walker_world(dist))
    return Wm["head"], Wm


def hp(Hm, local):
    """A point in the walker head's local three.js space (x=-left... i.e. -x is the walker's left)."""
    return (Hm @ np.array([*local, 1.0]))[:3]


def hd(Hm, local_dir):
    v = Hm[:3, :3] @ np.array(local_dir, float)
    return v / np.linalg.norm(v)


class Builder:
    """Collects authored keys, then solves arm/wrist/left-arm keys in time order."""

    def __init__(self, name, length, loop=False, weapon=None, react=None, dist=0, rig=None):
        self.a = Anim(length, loop)
        self.name, self.weapon, self.react, self.dist, self.rig = name, weapon, react, dist, rig
        self.solves = []          # (t, spec)
        self.prev = {"rightArm": np.array([-20, 10, 10.0]), "rightItem": np.zeros(3), "leftArm": np.array([-20, 0, -6.0])}
        self.report = []

    def pose(self, t, **k):
        self.a.pose(t, **k)
        for b in ("rightArm", "rightItem", "leftArm"):
            if b in k:
                self.prev[b] = np.array(k[b], float)

    # -- solving ---------------------------------------------------------------------------
    def _W(self, t, over):
        return fk.worlds(SR, [self.a], t, override=over)

    def solve_weapon(self, t, target, axis=None, edge=None, w_axis=8.0, w_edge=6.0, reg=0.002, upright=0.0):
        strike = WEAPON[self.weapon][0]
        g0 = np.concatenate([self.prev["rightArm"], self.prev["rightItem"]])

        def f(x):
            Wm = self._W(t, {"rightArm": x[:3], "rightItem": x[3:]})
            p = fk.point(Wm, strike)
            c = np.sum((p - target) ** 2)
            R = Wm["rightItem"][:3, :3]
            if axis is not None:
                c += w_axis * np.sum((-R[:, 2] - axis) ** 2)
            if edge is not None:
                c += w_edge * np.sum((-R[:, 1] - edge) ** 2)
            if upright:
                n = np.cross(R[:, 2], R[:, 1])
                c += upright * (n[1] / np.linalg.norm(n)) ** 2
            c += W_BODY * body_penetration(Wm, ("rightArm",)) ** 2
            c += reg * np.sum((x[:3] - g0[:3]) ** 2) + 3 * reg * np.sum(x[3:] ** 2)
            return c

        best = None
        for g in (g0, g0 + [0, 30, 0, 0, 0, 0], g0 + [0, -30, 0, 0, 0, 0], g0 + [-40, 0, 0, 0, 0, 0]):
            r = minimize(f, g, method="Nelder-Mead", options={"maxiter": 6000, "xatol": 1e-3, "fatol": 1e-7})
            if best is None or r.fun < best.fun:
                best = r
        x = best.x
        Wm = self._W(t, {"rightArm": x[:3], "rightItem": x[3:]})
        err = np.linalg.norm(fk.point(Wm, strike) - target)
        self.a.key(t, "rightArm", rot=list(x[:3]))
        self.a.key(t, "rightItem", rot=list(x[3:]))
        self.prev["rightArm"], self.prev["rightItem"] = x[:3], x[3:]
        self.report.append(f"{self.name} t={t:.2f} weapon miss {err:.2f}px")
        return Wm

    def solve_two(self, t, target, axis=None, edge=None, w_axis=8.0, w_edge=6.0, w_grip=1.5, reg=0.002, upright=0.0, marker=None):
        """Two-handed: solve right arm, wrist, left arm and where the left hand sits on the handle together.
        Vanilla player arms are rigid (no elbow), so the grip may slide along the handle (local z +4..-4).
        upright > 0 keeps the blade plane (handle and edge) vertical, so a chop doesn't read as tilted."""
        strike = marker or WEAPON[self.weapon][0]
        g0 = np.concatenate([self.prev["rightArm"], self.prev["rightItem"], self.prev["leftArm"], [2.0]])

        def parts(x):
            Wm = self._W(t, {"rightArm": x[:3], "rightItem": x[3:6], "leftArm": x[6:9]})
            s = float(np.clip(x[9], -4, 4))
            grip = (Wm["rightItem"] @ np.array([0, 0, s, 1.0]))[:3]
            return Wm, grip

        def f(x):
            Wm, grip = parts(x)
            c = np.sum((fk.point(Wm, strike) - target) ** 2)
            R = Wm["rightItem"][:3, :3]
            if axis is not None:
                c += w_axis * np.sum((-R[:, 2] - axis) ** 2)
            if edge is not None:
                c += w_edge * np.sum((-R[:, 1] - edge) ** 2)
            if upright:
                n = np.cross(R[:, 2], R[:, 1])
                c += upright * (n[1] / np.linalg.norm(n)) ** 2
            c += w_grip * np.sum((fk.point(Wm, "leftHand") - grip) ** 2)
            c += W_BODY * body_penetration(Wm) ** 2
            c += reg * (np.sum((x[:3] - g0[:3]) ** 2) + np.sum((x[6:9] - g0[6:9]) ** 2)) + 3 * reg * np.sum(x[3:6] ** 2)
            c += 0.5 * max(0, abs(x[9]) - 4) ** 2
            return c

        best = None
        for d in ([0] * 10, [0, 30, 0, 0, 0, 0, 0, -30, 0, 0], [0, -20, 0, 0, 0, 0, 0, 20, 0, 0], [-30, 0, 0, 0, 0, 0, -30, 0, 0, 0]):
            r = minimize(f, g0 + np.array(d, float), method="Nelder-Mead",
                         options={"maxiter": 20000, "xatol": 1e-3, "fatol": 1e-7, "adaptive": True})
            if best is None or r.fun < best.fun:
                best = r
        x = best.x
        Wm, grip = parts(x)
        self.a.key(t, "rightArm", rot=list(x[:3]))
        self.a.key(t, "rightItem", rot=list(x[3:6]))
        self.a.key(t, "leftArm", rot=list(x[6:9]))
        self.prev["rightArm"], self.prev["rightItem"], self.prev["leftArm"] = x[:3], x[3:6], x[6:9]
        self.report.append(f"{self.name} t={t:.2f} weapon miss {np.linalg.norm(fk.point(Wm, strike) - target):.2f}px, "
                           f"left hand off handle {np.linalg.norm(fk.point(Wm, 'leftHand') - grip):.2f}px")

    def solve_left(self, t, target, reg=0.002):
        g0 = self.prev["leftArm"]

        def f(x):
            Wm = self._W(t, {"leftArm": x})
            return (np.sum((fk.point(Wm, "leftHand") - target) ** 2) + reg * np.sum((x - g0) ** 2)
                    + W_BODY * body_penetration(Wm, ("leftArm",)) ** 2)

        best = None
        for g in (g0, g0 + [0, 30, 0], g0 + [0, -30, 0], np.array([-80, 0, 0.0])):
            r = minimize(f, g, method="Nelder-Mead", options={"maxiter": 4000, "xatol": 1e-3, "fatol": 1e-7})
            if best is None or r.fun < best.fun:
                best = r
        self.a.key(t, "leftArm", rot=list(best.x))
        self.prev["leftArm"] = best.x
        err = np.linalg.norm(fk.point(self._W(t, {"leftArm": best.x}), "leftHand") - target)
        self.report.append(f"{self.name} t={t:.2f} left hand miss {err:.2f}px")

    def grip(self, t):
        g = WEAPON[self.weapon][1]
        if g:
            Wm = self._W(t, None)
            self.solve_left(t, fk.point(Wm, g))

    def walker(self, t):
        return head_frame(self.react, t, self.dist, self.rig)


def P(x, y, z):
    """Player-local point: x is the survivor's right, y up, z backward."""
    return np.array([x, y, z], float)


def U(*v):
    v = np.array(v, float)
    return v / np.linalg.norm(v)


def from_hands(hands, axis, reach=15.0):
    """Place a long weapon by where the hands are: the striking point sits `reach` px up the handle."""
    return np.array(hands, float) + U(*axis) * reach


STAND = dict(body_pos=[0, 0, 0], torso=[0, 0, 0], head=[4, 0, 0], rightLeg=[0, 0, 0], leftLeg=[0, 0, 0])


# ================================================================== executions
def knife():
    e = wa.EXECS["knife"]
    b = Builder("exec_knife", e["length"], weapon="knife", react=W["react"]["react_knife"], dist=e["dist"])
    I, R = e["impact"], e["release"]
    b.pose(0.0, **STAND, rightArm=[-20, 10, 10], rightItem=[0, 0, 0], leftArm=[-20, 0, -6])
    # load: weight back, shoulders coil, knife hand drops low and back, free hand goes for its chest
    b.pose(0.12, body_pos=[0, 0, 1], torso=[-4, 14, 0], head=[2, -6, 0], rightArm=[10, 30, 30], rightItem=[-30, 0, 0])
    Hm, Wm = b.walker(0.12)
    b.solve_left(0.12, fk.point(Wm, "torso", (0, 9, -2.5)))
    # Into the temple on the walker's left (the player's right), blade travelling right to left across the head.
    # Andrew, in game 2026-10-16: the frontal eye-socket stab read as passing through the face; the rigid
    # vanilla arm could only bring the blade ~50 deg off a head-on line anyway, and a hook suits it.
    temple, deep = (-4, 4.5, -0.5), (2.0, 4.5, -0.5)
    # commit: step in hard, blade comes round beside the head
    b.pose(0.22, body_pos=[0, -0.8, -2.5], torso=[8, 6, 0], head=[6, -2, 0], leftLeg=[-22, 0, 0], rightLeg=[14, 0, 0])
    Hm, Wm = b.walker(0.22)
    b.solve_left(0.22, fk.point(Wm, "torso", (0, 9, -2.5)))
    side = hd(Hm, (-1, 0, 0))                    # out of the walker's left temple
    b.solve_weapon(0.22, hp(Hm, temple) + side * 6, axis=-side)
    b.pose(I - .06, body_pos=[0, 0, -9.7], torso=[14, -10, 0], head=[6, -2, 0], rightArm_pos=[0, 1, -2])
    Hm, _ = b.walker(I - .06)
    b.solve_weapon(I - .06, hp(Hm, temple) + hd(Hm, (-1, 0, 0))*2, reg=.00002)
    # impact: into the temple, whole body behind it
    b.pose(I, body_pos=[0, 0, -9.7], torso=[14, -10, 0], head=[8, -4, 0], rightArm_pos=[0, 1, -2])
    Hm, Wm = b.walker(I)
    b.solve_weapon(I, hp(Hm, deep), axis=hd(Hm, (1, 0, 0)), w_axis=100, reg=0.00002)
    b.solve_left(I, fk.point(Wm, "torso", (0, 9, -2.5)))
    # drive it home
    b.pose(0.45, body_pos=[0, 0, -10.0], torso=[17, -12, 0], head=[10, -4, 0], rightArm_pos=[0, 1, -2])
    Hm, Wm = b.walker(0.45)
    b.solve_weapon(0.45, hp(Hm, (2.8, 4.5, -0.5)), axis=hd(Hm, (1, 0, 0)), w_axis=100, reg=0.00002)
    # wrench it out with a twist, back the way it came
    b.pose(0.6, body_pos=[0, -0.8, -2], torso=[6, 8, 0], head=[4, 4, 0], rightArm_pos=[0, 0, 0])
    Hm, Wm = b.walker(0.6)
    b.solve_weapon(0.6, hp(Hm, temple) + hd(Hm, (-1, 0, 0)) * 6 + P(1, -2, 2), axis=U(-0.8, 0.3, -0.5))
    # shove the body off the free hand
    b.pose(R, body_pos=[0, -0.5, -1.5], torso=[4, -4, 0], leftArm=[-98, 0, 10], rightArm=[-30, 20, 20], rightItem=[-10, 0, 0])
    b.pose(1.0, **STAND, rightArm=[-18, 10, 10], rightItem=[0, 0, 0], leftArm=[-10, 0, -4])
    b.a.event(e["commit"], "commit").event(I, "kill").event(R, "release")
    b.a.sound(0.1, "holdfast:player.exert_light").sound(I, "holdfast:player.exert_heavy").sound(0.58, "holdfast:knife.pull")
    return b, dict(impacts=[(I, 2.5)], weapon="knife")


def katana_guard(b, t):
    # the ready chudan (player_weapons hold_katana): hilt at the navel, tip at throat height
    b.solve_two(t, P(1, 23, -22), axis=U(0, 0.45, -0.89), edge=U(0, -0.89, -0.45))


def katana():
    """Two-handed thrust through the face (tsuki) from the guard: draw the hands back to the right hip with the blade
    level, drive it in with the whole body, hold, draw it out and return to guard."""
    e = wa.EXECS["katana"]
    b = Builder("exec_katana", e["length"], weapon="katana", react=W["react"]["react_katana"], dist=e["dist"])
    I, R = e["impact"], e["release"]
    tip = 7.8                                   # katanaTip lies 7.8 px past the solved katanaEdge marker
    b.pose(0.0, **STAND, rightArm=[-62, -45, 14], rightItem=[0, 0, 0], leftArm=[-60, 20, -6])
    katana_guard(b, 0.0)
    # chamber: hands back by the right hip, blade level and aimed, weight on the back foot
    b.pose(e["commit"], torso=[-2, 18, 0], body_pos=[0, -0.3, 1.0], head=[2, -10, 0], leftLeg=[-6, 0, 0], rightLeg=[8, 0, 0])
    b.solve_two(e["commit"], P(5, 23, -12), axis=U(-0.08, 0.06, -1), edge=U(0, -1, 0))
    b.pose(I - .06, torso=[4, 6, 0], body_pos=[0, -.5, -3], head=[5, -4, 0])
    Hm, _ = b.walker(I - .06)
    b.solve_two(I - .06, hp(Hm, (0, 5, -5.5)), axis=U(0, 0, -1), edge=U(0, -1, 0), marker="katanaTip", reg=.00002)
    # thrust: lunge, the tip through the face to the middle of the skull
    b.pose(I, torso=[10, -4, 0], body_pos=[0, -1.0, -6.0], head=[8, -2, 0], leftLeg=[-22, 0, 0], rightLeg=[12, 0, 0])
    Hm, _ = b.walker(I)
    deep = hp(Hm, (0, 5, 3.8))
    axis = U(*(deep - P(0, 20, -6)))
    b.solve_two(I, deep, axis=axis, edge=U(0, -1, 0), marker="katanaTip", w_axis=30, reg=0.00002)
    # hold it in: same reachable lunge, the tip kept in the skull while the walker hangs on it
    b.pose(0.48, torso=[10, -4, 0], body_pos=[0, -1.0, -6.0])
    Hm, _ = b.walker(0.48)
    b.solve_two(0.48, hp(Hm, (0, 5, 3.8)), axis=axis, edge=U(0, -1, 0), marker="katanaTip", w_axis=30, reg=0.00002)
    # draw it out along the same line
    b.pose(R, torso=[2, 6, 0], body_pos=[0, -0.5, -1.0], head=[4, 2, 0], leftLeg=[-8, 0, 0], rightLeg=[6, 0, 0])
    Hm, _ = b.walker(R)
    b.solve_two(R, hp(Hm, (0, 5, -8)) - axis * (tip + 4), axis=axis, edge=U(0, -1, 0))
    b.pose(e["length"], **STAND)
    katana_guard(b, e["length"])
    b.a.event(e["commit"], "commit").event(I, "kill").event(R, "release")
    b.a.sound(0.1, "holdfast:player.exert_light").sound(I, "holdfast:player.exert_heavy").sound(R - 0.06, "holdfast:knife.pull")
    return b, dict(impacts=[(I, 3.0)], weapon="katana")


def bat_ready(b, t, extra=None):
    # bat up on the right shoulder, both hands on it
    b.solve_two(t, from_hands((0, 15, -5), (0.25, 0.85, 0.45)), axis=U(0.25, 0.85, 0.45))


def bat_quick():
    e = wa.EXECS["bat_quick"]
    b = Builder("exec_bat_quick", e["length"], weapon="bat", react=W["react"]["react_bat_quick"], dist=e["dist"])
    I = e["impact"]
    b.pose(0.0, **STAND, rightArm=[-60, 30, 40], rightItem=[-40, 0, 0], leftArm=[-60, -20, 10])
    bat_ready(b, 0.0)
    b.pose(0.08, torso=[0, 22, 0], body_pos=[0, -0.4, 0.5], head=[4, -10, 0])
    b.solve_two(0.08, from_hands((1.5, 16.5, -5), (0.5, 0.55, 0.65)), axis=U(0.5, 0.55, 0.65))
    b.pose(I, torso=[6, -18, 0], body_pos=[0, -0.8, -2], head=[6, -4, 0], leftLeg=[-20, 0, 0], rightLeg=[10, 0, 0])
    Hm, _ = b.walker(I)
    b.solve_two(I, hp(Hm, (-3, 5, 0)), axis=U(-0.2, 0, -1))
    b.pose(0.36, torso=[4, -45, 0], body_pos=[0, -0.6, -1.5], head=[4, -14, 0])
    b.solve_two(0.36, P(-14, 24, -4), axis=U(-1, 0, 0.2))
    b.pose(e["length"], **STAND)
    bat_ready(b, e["length"])
    b.a.event(e["commit"], "commit").event(I, "stagger")
    b.a.sound(0.06, "holdfast:player.exert_light").sound(I, "holdfast:player.exert_heavy")
    return b, dict(impacts=[(I, 2.5)], weapon="bat")


def bat_charge():
    b = Builder("bat_charge_hold", 1.0, loop=True, weapon="bat")
    for t, d in ((0.0, 0.0), (0.5, 0.4), (1.0, 0.0)):
        b.pose(t, body_pos=[0, -0.6 - d, 0.6], torso=[-2, 35 + 2 * d, 0], head=[2, -14, 0], rightLeg=[10, 0, 0], leftLeg=[-8, 0, 0])
        b.solve_two(t, from_hands((1, 16 + d, -5.5), (0.45, 0.6, 0.65)), axis=U(0.45, 0.6, 0.65))
    # The loop end must be the loop start: the warm-started solver lands on another arm/wrist solution for the
    # same target at t=1.0, which snapped the bat ~35 degrees once a second while charging (2026-10-17 check).
    for channels in b.a.tracks.values():
        for k in channels.values():
            if 0.0 in k:
                k[b.a.length] = list(k[0.0])
    return b, dict(impacts=[], weapon="bat", geodesic_items=True)


def bat_charged():
    e = wa.EXECS["bat_charged"]
    b = Builder("exec_bat_charged", e["length"], weapon="bat", react=W["react"]["react_bat_charged"], dist=e["dist"])
    I = e["impact"]
    b.pose(0.0, body_pos=[0, -0.6, 0.6], torso=[-2, 35, 0], head=[2, -14, 0], rightLeg=[10, 0, 0], leftLeg=[-8, 0, 0],
           rightArm=[-120, 40, 60], rightItem=[-20, 0, 0], leftArm=[-110, -20, 10])
    b.solve_two(0.0, from_hands((1, 16, -5.5), (0.45, 0.6, 0.65)), axis=U(0.45, 0.6, 0.65))
    b.pose(0.12, body_pos=[0, -1.0, -2], torso=[0, 20, 0], head=[4, -6, 0], leftLeg=[-26, 0, 0], rightLeg=[14, 0, 0])
    b.solve_two(0.12, from_hands((1.5, 16, -6), (0.6, 0.3, -0.75)), axis=U(0.6, 0.3, -0.75))
    b.pose(I, body_pos=[0, -1.4, -3], torso=[8, -24, 0], head=[8, -6, 0])
    Hm, _ = b.walker(I)
    b.solve_two(I, hp(Hm, (-2.5, 5, 0)), axis=U(-0.25, 0, -1))
    b.pose(0.42, body_pos=[0, -1.2, -2.6], torso=[6, -60, 0], head=[6, -20, 0], rightLeg=[20, 0, 0])
    b.solve_two(0.42, P(-16, 22, 2), axis=U(-1, -0.1, 0.4))
    # spent: bent over, bat tip down, breathing
    b.pose(0.7, body_pos=[0, -1.6, -1.5], torso=[16, -30, 0], head=[16, -10, 0])
    b.solve_two(0.7, P(-6, 6, -12), axis=U(-0.2, -0.6, -0.8))
    b.pose(0.88, body_pos=[0, -1.2, -1], torso=[12, -24, 0], head=[12, -8, 0])
    b.pose(e["length"], **STAND)
    bat_ready(b, e["length"])
    b.a.event(e["commit"], "commit").event(I, "kill")
    b.a.sound(0.02, "holdfast:player.exert_heavy").sound(0.7, "holdfast:player.breath_heavy")
    return b, dict(impacts=[(I, 5.0)], weapon="bat")


def axe():
    e = wa.EXECS["axe"]
    b = Builder("exec_axe", e["length"], weapon="axe", react=W["react"]["react_axe"], dist=e["dist"])
    I, R = e["impact"], e["release"]
    b.pose(0.0, **STAND, rightArm=[-30, 10, 10], rightItem=[20, 0, 0], leftArm=[-40, -20, 0])
    b.solve_two(0.0, P(-3, 18, -12), axis=U(-0.3, 0.5, -0.8), edge=U(0, -0.6, -0.8))
    # heave it overhead, rising onto the toes, leaning back
    b.pose(e["commit"], body_pos=[0, 0.4, 0.8], torso=[-12, 0, 0], head=[-10, 0, 0], leftLeg=[-10, 0, 0], rightLeg=[6, 0, 0])
    # The chop keys hold the blade plane vertical (Andrew, in game 2026-10-16: tilted read wrong).
    b.solve_two(e["commit"], P(0, 46, 10), axis=U(0, 0.6, 0.8), edge=U(0, 0.8, -0.6), upright=300.0)
    # drop everything into the chop
    b.pose(I, body_pos=[0, -1.6, -2.5], torso=[20, 0, 0], head=[12, 0, 0], leftLeg=[-18, 0, 0], rightLeg=[14, 0, 0])
    Hm, _ = b.walker(I)
    b.solve_two(I, hp(Hm, (0, 6.5, 0)), axis=U(0, -0.3, -1), edge=hd(Hm, (0, -1, 0)), upright=300.0)
    b.pose(0.62, body_pos=[0, -1.6, -2.4], torso=[19, 0, 0])
    Hm, _ = b.walker(0.62)
    b.solve_two(0.62, hp(Hm, (0, 6.5, 0)), axis=U(0, -0.3, -1), edge=hd(Hm, (0, -1, 0)), upright=300.0)
    # wrench: lean back and lever it out
    b.pose(0.76, body_pos=[0, -1.2, -1.2], torso=[8, 8, 0], head=[6, 6, 0])
    Hm, _ = b.walker(0.76)
    b.solve_two(0.76, hp(Hm, (0, 6.5, 0)), axis=U(0, 0.2, -1), edge=hd(Hm, (0, -1, 0)), upright=300.0)
    b.pose(R, body_pos=[0, -0.4, 0], torso=[0, -6, 0], head=[2, 0, 0])
    b.solve_two(R, P(0, 36, -12), axis=U(0, 0.8, -0.6), edge=U(0, 0.3, -1))
    # spent
    b.pose(1.05, body_pos=[0, -1.2, 0], torso=[14, 0, 0], head=[14, 0, 0])
    b.solve_two(1.05, P(-4, 6, -12), axis=U(-0.2, -0.5, -0.8), edge=U(0, -0.8, 0.5))
    b.pose(e["length"], **STAND)
    b.solve_two(e["length"], P(-3, 18, -12), axis=U(-0.3, 0.5, -0.8), edge=U(0, -0.6, -0.8))
    b.a.event(e["commit"], "commit").event(I, "kill").event(R, "release")
    b.a.sound(0.15, "holdfast:player.exert_heavy").sound(0.72, "holdfast:player.exert_strain").sound(1.0, "holdfast:player.breath_heavy")
    return b, dict(impacts=[(I, 6.0), (0.76, 2.0)], weapon="axe")


def machete():
    e = wa.EXECS["machete"]
    b = Builder("exec_machete", e["length"], weapon="machete", react=W["react"]["react_machete"], dist=e["dist"])
    I, R = e["impact"], e["release"]
    b.pose(0.0, **STAND, rightArm=[-20, 10, 10], rightItem=[0, 0, 0], leftArm=[-20, 0, -6])
    b.pose(e["commit"], torso=[-4, 18, 0], body_pos=[0, -0.2, 0.5], head=[0, -6, 0])
    b.solve_weapon(e["commit"], P(12, 40, 4), axis=U(0.3, 0.6, 0.7), edge=U(-0.5, 0.2, -0.8))
    Hm, Wm = b.walker(e["commit"])
    b.solve_left(e["commit"], fk.point(Wm, "torso", (-3, 11, -2.5)))
    b.pose(I, torso=[16, -14, 0], body_pos=[0, -1.1, -2.5], head=[8, -4, 0], leftLeg=[-20, 0, 0], rightLeg=[10, 0, 0])
    Hm, Wm = b.walker(I)
    # Into the side of the skull, not its top corner: the old target (-3, 7, -1) glanced 1.6 px deep
    # (Andrew, in game 2026-10-16: "a bit more accurate").
    b.solve_weapon(I, hp(Hm, (-2, 5.5, -1)), axis=U(-0.4, -0.2, -1), edge=hd(Hm, (0.7, -0.7, 0)))
    b.solve_left(I, fk.point(Wm, "torso", (-3, 11, -2.5)))
    b.pose(0.45, torso=[18, -10, 0], body_pos=[0, -1.2, -2.6])
    Hm, Wm = b.walker(0.45)
    b.solve_weapon(0.45, hp(Hm, (-1, 4.8, -1)), axis=U(-0.4, -0.2, -1), edge=hd(Hm, (0.7, -0.7, 0)))
    b.solve_left(0.45, fk.point(Wm, "torso", (-3, 11, -2.5)))
    b.pose(R, torso=[6, 10, 0], body_pos=[0, -0.6, -1], head=[4, 4, 0], leftArm=[-60, 0, -4])
    b.solve_weapon(R, P(8, 30, -10), axis=U(0.2, 0.8, -0.5), edge=U(-0.3, 0.4, -0.8))
    b.pose(e["length"], **STAND, rightArm=[-20, 10, 10], rightItem=[0, 0, 0], leftArm=[-15, 0, -4])
    b.a.event(e["commit"], "commit").event(I, "kill").event(R, "release")
    b.a.sound(0.08, "holdfast:player.exert_light").sound(I, "holdfast:player.exert_heavy").sound(0.56, "holdfast:player.exert_strain")
    return b, dict(impacts=[(I, 3.5), (R - 0.06, 1.5)], weapon="machete")


# ======================================================================= grab
def grab_anims():
    out = {}
    STRUG = dict(body_pos=[0, -0.6, 1.2], torso=[-8, 0, 0], head=[-6, 18, 0], rightLeg=[18, 0, 0], leftLeg=[-10, 0, 0],
                 rightArm=[-84, -10, 0], leftArm=[-80, 12, 0], rightItem=[0, 0, 0])
    a = Anim(0.2, False)
    # Server starts this at the walker's first grip, not at another anticipatory reach.
    a.pose(0.0, **STAND, rightArm=[-60, -14, 8], leftArm=[-42, 14, -10], rightItem=[0, 0, 0])
    a.pose(0.06, body_pos=[0, -.2, .6], torso=[-6, 0, 0], head=[-8, 12, -2],
           rightArm=[-76,-12,4], leftArm=[-64,14,-6])
    a.pose(0.2, **STRUG)
    a.sound(0.02, "holdfast:player.gasp")
    out["grabbed_seize"] = (a, dict(impacts=[(0.06, 2.0)]))

    a = Anim(0.8, True) # brace, one grip slips under pressure, regain the frame; not two arms pumping together
    for t,k,slip in ((0,0,0),(.12,.65,0),(.26,1,1),(.40,.25,.3),(.54,-.75,0),(.68,-.35,-.35),(.8,0,0)):
        a.pose(t,**dict(STRUG,rightArm=[-84+5*k+3*slip,-10-4*slip,2*slip],
                        leftArm=[-80-3*k,12+5*slip,-2*slip],head=[-6+2*k,18+4*k,1.5*slip],
                        torso=[-8-1.3*max(0,k),0,1.2*slip],body_pos=[0,-.6-.15*max(0,k),1.2+.35*k],
                        rightLeg=[18-3*k,0,0],leftLeg=[-10+2*k,0,0]))
    a.sound(0.0, "holdfast:player.strain")
    out["grabbed_struggle"] = (a, dict(impacts=[], tremor=0.18))

    a = Anim(0.6, False)
    a.pose(0.0, **STRUG)
    a.pose(0.08, torso=[6, 0, 0], body_pos=[0, -0.9, 0.6], rightArm=[-70, -10, 0], leftArm=[-68, 12, 0])
    a.pose(.12,torso=[11,0,0],body_pos=[0,-.85,-.8],rightArm=[-86,-4,3],leftArm=[-82,5,-3],head=[0,5,0])
    a.pose(0.16, rightArm=[-95, 0, 4], leftArm=[-95, 0, -4], torso=[14, 0, 0], body_pos=[0, -0.6, -2.5],
           leftLeg=[-24, 0, 0], rightLeg=[16, 0, 0], head=[2, 0, 0])
    a.pose(0.35, rightArm=[-60, 0, 6], leftArm=[-58, 0, -6], torso=[8, 0, 0])
    a.pose(.46,torso=[3,0,0],body_pos=[0,-.2,-.4],rightArm=[-32,0,8],leftArm=[-28,0,-8],head=[4,0,0])
    a.pose(0.6, **STAND, rightArm=[-20, 0, 6], leftArm=[-20, 0, -6], rightItem=[0, 0, 0])
    a.sound(0.14, "holdfast:player.exert_heavy")
    a.event(0.14, "shove")
    out["grabbed_shove"] = (a, dict(impacts=[(0.16, 3.0)]))

    a = Anim(1.0, False)
    a.pose(0.0, **STRUG)
    a.pose(0.38, head=[-10, 30, -14], torso=[-14, 10, -6], leftArm=[-140, -10, 20], rightArm=[-70, -10, 0])
    a.pose(0.6, torso=[-6, 6, -4], head=[-4, 26, -10])
    a.pose(1.0, **STRUG)
    a.sound(0.38, "holdfast:player.scream")
    out["grabbed_bitten"] = (a, dict(impacts=[(0.38, 5.0)]))

    a = Anim(0.6, False)
    a.pose(0.0, **STRUG)
    a.pose(0.36, head=[-8, 28, -6], torso=[-12, 6, 0])
    a.pose(0.6, **STRUG)
    a.sound(0.36, "holdfast:player.gasp")
    out["grabbed_bite_blocked"] = (a, dict(impacts=[(0.36, 3.0)]))

    a = Anim(0.6, False)               # a teammate kills it mid-grab
    a.pose(0.0, **STRUG)
    a.pose(0.2, body_pos=[0, -0.3, 2], torso=[-6, 0, 0], rightArm=[-40, 0, 8], leftArm=[-40, 0, -8], head=[10, 0, 0])
    a.pose(0.6, **STAND, rightArm=[-12, 0, 4], leftArm=[-12, 0, -4], rightItem=[0, 0, 0])
    out["grabbed_released"] = (a, dict(impacts=[]))
    return out


# ================================================================ camera track
def camera_track(a, info, step=0.05):
    """First-person camera derived from the animated head, plus decaying shake at each impact.
    rotation [pitch (+ down), yaw (+ right), roll (+ right)] degrees; position [x (+ left), y, z (+ back)] px."""
    cam = Anim(a.length, a.loop)
    n = int(round(a.length / step))
    rest = fk.point(fk.worlds(SR, [Anim(1, False)], 0), "torso", (0, 12, 0))
    for i in range(n + 1):
        t = round(min(i * step, a.length), 4)
        Wm = fk.worlds(SR, [a], t)
        Hm = Wm["head"]
        dpos = fk.point(Wm, "torso", (0, 12, 0)) - rest
        f = -Hm[:3, 2]                       # head forward
        pitch = math.degrees(math.asin(np.clip(-f[1], -1, 1)))
        yaw = math.degrees(math.atan2(f[0], -f[2]))
        up = Hm[:3, 1]
        roll = math.degrees(math.atan2(-up[0], up[1]))
        pr = [0.55 * pitch, 0.55 * yaw, 0.5 * roll]
        for t0, amp in info.get("impacts", []):
            if t >= t0:
                d = t - t0
                k = amp * math.exp(-d / 0.09)
                pr[0] += k * math.cos(d * 55)
                pr[2] += 0.7 * k * math.sin(d * 47)
        if info.get("tremor"):
            pr[0] += info["tremor"] * math.sin(t * 70)
            pr[2] += info["tremor"] * math.sin(t * 53 + 1)
        if info.get("injury_camera"):
            # A readable first-person uneven step. This is presentation only; the harness
            # scales the track by actual movement so standing still does not bob the view.
            phase = 2 * math.pi * t / a.length
            severity = info["injury_camera"]
            pr[0] += severity * 1.4 * max(0.0, -math.cos(phase))
            pr[2] += severity * 2.4 * math.sin(phase)
        cam.key(t, "camera", rot=pr, pos=[-0.9 * dpos[0], 0.9 * dpos[1], 0.9 * dpos[2]])
    return cam


def build():
    import player_more
    import copy
    execs = dict(exec_knife=knife(), exec_bat_quick=bat_quick(), bat_charge_hold=bat_charge(),
                 exec_bat_charged=bat_charged(), exec_axe=axe(), exec_machete=machete(), exec_katana=katana())
    out, reports = {}, []
    for k, (b, info) in execs.items():
        out[k] = (b.a, info)
        reports += b.report
    out.update(grab_anims())
    more, rep2, speeds = player_more.build()
    for name, severity in (("move_limp", 1.0), ("move_limp_severe", 1.6),
                           ("move_arm_injured", 0.35), ("move_prosthesis", 0.7)):
        more[name][1]["injury_camera"] = severity
    out.update(more)
    # TaCZ owns the complete gun hold/aim chain. These firearm locomotion variants retain
    # the injury gait and body translation, but never key the torso lean, head, arms or items.
    # Removing the authored torso lean also prevents its exported whole-body rotation from
    # carrying TaCZ's arms away from the gun. Crutches and legless crawl are excluded: they
    # require their arms to move and cannot be combined with a normal firearm hold.
    for name in ("move_limp", "move_limp_severe", "move_arm_injured", "move_prosthesis"):
        gait, info = more[name]
        gun_gait = copy.deepcopy(gait)
        for part in ("torso", "head", "rightArm", "leftArm", "rightItem", "leftItem"):
            gun_gait.tracks.pop(part, None)
        out[name + "_gun"] = (gun_gait, dict(info, firearm_variant=True))
    reports += rep2
    import player_guns
    guns, rep3 = player_guns.build()
    out.update(guns)
    reports += rep3
    import player_weapons
    fams, rep4, loco_speeds = player_weapons.build()
    out.update(fams)
    reports += rep4
    speeds.update(loco_speeds)
    write_json(os.path.join(OUT, "player_move_speeds.json"), speeds, indent=2)
    return out, reports


PA_PARTS = set(PLAYER_BONES) | {"rightItem", "leftItem"}


def native_player_animator(a, step=0.05):
    """Clips authored directly in Player Animator part space (player_guns.py): parts and values are written as
    authored, baked at 20 Hz. Only body position is scaled into real px (applied before the 15/16 model scale)."""
    b = a.baked(step)
    out = Anim(a.length, a.loop)
    for bone, chans in b.tracks.items():
        for ch, keys in chans.items():
            for t, v in keys.items():
                if bone == "body" and ch == "position":
                    v = [round(x * PLAYER_SCALE, 3) for x in v]
                out.key(t, bone, **({"rot": v} if ch == "rotation" else {"pos": v}))
    return out


def native_camera(a, info, step=0.05):
    """First-person camera for native clips from their own head and body keys (the authoring rig would read the
    chest-only torso part as an upper-body lean), plus the usual impact shake."""
    cam = Anim(a.length, a.loop)
    for i in range(int(round(a.length / step)) + 1):
        t = round(min(i * step, a.length), 4)
        h = a.value("head", "rotation", t) or [0, 0, 0]
        bd = a.value("body", "rotation", t) or [0, 0, 0]
        bp = a.value("body", "position", t) or [0, 0, 0]
        pr = [0.55 * (h[0] + bd[0]), 0.55 * (h[1] + bd[1]), 0.5 * (h[2] + bd[2])]
        for t0, amp in info.get("impacts", []):
            if t >= t0:
                d = t - t0
                k = amp * math.exp(-d / 0.09)
                pr[0] += k * math.cos(d * 55)
                pr[2] += 0.7 * k * math.sin(d * 47)
        cam.key(t, "camera", rot=pr, pos=[0.9 * bp[0], 0.9 * bp[1], 0.9 * bp[2]])
    return cam


def geodesic_item(a, out, part, arm):
    """Re-bake a held item so the WEAPON (arm x wrist) turns along the shortest rotation between authored keys,
    at a steady rate per key interval, instead of the composition of two per-channel Catmull-Rom curves.

    Measured on the 2026-10-17 swings (scratch analysis, docs/testing.md): the per-channel bake made the visible
    weapon travel up to 2.1x the direct rotation between keys and roll over about its handle during wind-ups and
    recoveries, faster there than at the strike. Authored keys are unchanged; only the in-between wrist values are."""
    import numpy as np
    from scipy.spatial.transform import Rotation, Slerp
    from lib import bb_matrix, bb_euler
    keys = a.tracks.get(part, {}).get("rotation", {})
    rot = out.tracks.get(part, {}).get("rotation", {})
    kts = sorted(keys)
    if len(kts) < 2 or not rot:
        return

    def arm_m(t):
        return bb_matrix(a.value(arm, "rotation", t) or [0, 0, 0])

    import pa_item
    S = np.diag([-1.0, -1.0, 1.0])            # our Euler numbers are Rz Ry Rx in Minecraft model space (pa_item)
    world = Rotation.from_matrix(np.array([arm_m(t) @ bb_matrix(keys[t]) for t in kts]))
    slerp = Slerp(kts, world)
    near = None
    for t in sorted(rot):
        w = slerp([min(max(t, kts[0]), kts[-1])]).as_matrix()[0]
        near = pa_item.zyx_euler(S @ (arm_m(t).T @ w) @ S, near)      # continuous: no +-180 wrap between samples
        rot[t] = [round(float(v), 2) for v in near]


def to_player_animator(a, step=0.05, firearm_variant=False, geodesic_items=False):
    """Map our authoring rig onto Player Animator 1.0.2-rc1 parts, baked at 20 keys per second.

    In Player Animator the "torso" part only turns the chest box about the neck and does not carry
    the head or arms, while "body" turns the whole player about a point 0.7 blocks (11.2 px) up
    (PlayerRendererMixin.applyBodyTransforms, before the 15/16 model scale). Our "torso" bone leans about
    11.2 authoring pixels; a body-translation correction preserves that pose at the actual scaled pivot.
    It is exported as "body" rotation, and the legs (which in game would now turn
    with the body) are counter-rotated so they keep their authored world orientation."""
    from lib import bb_matrix, bb_euler
    b = a.baked(step)
    out = Anim(a.length, a.loop)
    lean = b.tracks.get("torso", {}).get("rotation", {})
    for bone, chans in b.tracks.items():
        if bone not in PA_PARTS or bone == "torso":
            continue
        for ch, keys in chans.items():
            if bone == "body" and ch == "rotation":
                continue                       # body rotation is reserved for the lean
            for t, v in keys.items():
                if bone == "body" and ch == "position":
                    # applied in setupRotations, before the 15/16 model scale: model px -> real px
                    v = [round(x * PLAYER_SCALE, 3) for x in v]
                if bone in ("rightLeg", "leftLeg") and ch == "rotation" and lean:
                    L = bb_matrix(lean.get(t, [0, 0, 0]))
                    v = [round(x, 2) for x in bb_euler(L.T @ bb_matrix(v))]
                out.key(t, bone, **({"rot": v} if ch == "rotation" else {"pos": v}))
    import numpy as np
    hips = {"rightLeg": np.array([1.9, 12.0, 0.0]), "leftLeg": np.array([-1.9, 12.0, 0.0])}   # three.js space
    pivot = np.array([0.0, 11.2, 0.0])
    for t, v in lean.items():
        out.key(t, "body", rot=v)
        L = bb_matrix(v)
        delta = pivot - np.array([0.0, PA_BODY_PIVOT, 0.0])
        correction = delta - L @ delta
        authored = np.array(b.value("body", "position", t) or [0, 0, 0], float)
        out.key(t, "body", pos=(authored + np.array([-correction[0], correction[1], correction[2]])) * PLAYER_SCALE)
        for leg, hip in hips.items():
            if leg not in b.tracks or "rotation" not in b.tracks[leg]:
                out.key(t, leg, rot=[round(x, 2) for x in bb_euler(L.T)])
            # keep the hip joint where it was authored: offset expressed in the leaned body frame
            d = L.T @ (hip - (pivot + L @ (hip - pivot)))
            authored_leg = b.value(leg, "position", t)
            if authored_leg is not None:
                d += L.T @ np.array([-authored_leg[0], authored_leg[1], authored_leg[2]])
            if np.abs(d).max() > 0.005 or authored_leg is not None:
                out.key(t, leg, pos=[round(-d[0], 3), round(d[1], 3), round(d[2], 3)])
    if geodesic_items and not firearm_variant:
        for part, arm in (("rightItem", "rightArm"), ("leftItem", "leftArm")):
            geodesic_item(a, out, part, arm)
    # Held items: Player Animator turns them in the vanilla hand frame about the grip point, our rig turns
    # them in the arm frame about the fist (pa_item.py). Convert every key, and pin the pivot with a
    # constant position key so the same item model works whether or not a clip keys the wrist.
    import pa_item
    for part in (() if firearm_variant else ("rightItem", "leftItem")):
        rot = out.tracks.get(part, {}).get("rotation", {})
        near = None
        for t in sorted(rot):
            near = pa_item.to_pa_rotation(rot[t], near)
            rot[t] = [round(float(v), 2) for v in near]
        authored_positions=b.tracks.get(part,{}).get('position')
        out.tracks.setdefault(part, {})["position"] = ({t:pa_item.pa_position(part,v) for t,v in authored_positions.items()}
                                                     if authored_positions else {0.0:pa_item.pa_position(part)})
    return out


def export(out, merge_keys=False):
    """Player Animator files (body parts + held-item parts, explicit linear keys at 20/s) and an
    extras file per clip for our core (first-person camera, sounds, events, metadata)."""
    pdir = os.path.join(OUT, "player_animation")
    xdir = os.path.join(OUT, "player_animation_extras")
    key_path = os.path.join(OUT, "player_keys.animation.json")
    keys = json.load(open(key_path))["animations"] if merge_keys and os.path.exists(key_path) else {}
    for k, (a, info) in out.items():
        native = info.get("pa_native", False)
        pa_clip = native_player_animator(a) if native else to_player_animator(a, firearm_variant=info.get("firearm_variant", False),
                                                                                geodesic_items=info.get("geodesic_items", False))
        write_json(os.path.join(pdir, f"{k}.json"),
                   {"format_version": "1.8.0", "animations": {k: pa_clip.to_json(smooth="linear")}})
        ex = native_camera(a, info) if native else camera_track(a, info)
        ex.sounds, ex.timeline = dict(a.sounds), dict(a.timeline)
        meta = {"weapon": info.get("weapon"), "vanilla_pose": info.get("vanilla_pose"), "pa_native": native,
                "geodesic_items": bool(info.get("geodesic_items", False))}
        write_json(os.path.join(xdir, f"{k}.extras.json"),
                   {"format_version": "1.8.0", "holdfast_meta": meta, "animations": {k: ex.to_json(smooth="linear")}})
        if not native:      # player_keys feeds the authoring-rig checks and preview, which native clips don't fit
            keys[f"animation.holdfast.player.{k}"] = a.to_json()
    write_json(os.path.join(OUT, "player_keys.animation.json"), {"format_version": "1.8.0", "animations": keys})


if __name__ == "__main__":
    out, reports = build()
    export(out)
    for r in reports:
        print(r)
    print(len(out), "player animations")
