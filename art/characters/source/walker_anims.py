"""Walker animations.

Layered (three GeckoLib controllers, so every walker gets its own combination):
  body  : stand, 8 gaits, hungry pursuit         -> hips, torso, legs
  arms  : 6 arm sets + reach                     -> arms, forearms
  head  : 6 head sets + snap                     -> head, jaw
Full body (other two controllers stopped while these play):
  postures, lunge, stagger, deaths, grab side, execution reactions
Crawler (legless model): its own file.
"""
import math, os
from lib import Anim, gait, loop_keys, BODY, ARMS, HEAD, S, C, TAU, pos, OUT, write_json, anim_file

ALL = BODY | ARMS | HEAD

NEUTRAL = dict(root=[0, 0, 0], root_pos=[0, 0, 0], hips_pos=[0, 0, 0], hips=[0, 0, 0], torso=[14, 0, 0],
               head=[8, 0, 6], jaw=[6, 0, 0], rightArm=[-30, 0, 6], leftArm=[-30, 0, -6],
               rightForearm=[-18, 0, 0], leftForearm=[-18, 0, 0], rightLeg=[0, 0, 2], leftLeg=[0, 0, -2],
               rightShin=[6, 0, 0], leftShin=[6, 0, 0])
APPROACH = dict(NEUTRAL, torso=[18, 0, 0], head=[-4, 0, 4], jaw=[16, 0, 0], rightArm=[-72, 0, 4],
                leftArm=[-66, 0, -4], rightForearm=[-12, 0, 0], leftForearm=[-12, 0, 0],
                rightLeg=[-14, 0, 2], leftLeg=[10, 0, -2], rightShin=[12, 0, 0], leftShin=[8, 0, 0])


def full(a, t, base, **over):
    """Key every walker bone at t from a base pose, with overrides."""
    d = dict(base)
    d.update(over)
    a.pose(t, **d)


# ===================================================================== body
GAITS = {
    "shamble":    dict(L=2.0, stride=(24, 18), lift=(28, 12), lift_base=(8, 5), lean=14, lean_var=3, roll=5, twist=6,
                       hip_roll=3, sway=0.6, bob=(0.8, 1.6)),
    "shuffle":    dict(L=1.5, stride=(13, 12), lift=(6, 6), lift_base=(10, 10), lean=20, lean_var=1.5, roll=3, twist=3,
                       hip_roll=2, sway=0.3, bob=(0.3, 0.4)),
    "drag_left":  dict(L=2.3, stride=(22, 9), lift=(26, 0), lift_base=(6, 1), lean=13, roll=7, twist=7, sway=0.8,
                       bob=(0.6, 1.3), skew=0.42, splay=(2, -5), circ=(0, 4)),
    "limp_right": dict(L=2.4, stride=(18, 22), lift=(0, 30), lift_base=(3, 6), lean=16, lean_var=4, roll=8, twist=5,
                       hip_roll=4, sway=0.8, bob=(0.4, 0.6), dip_r=1.8, skew=0.6, circ=(9, 0)),
    "stiff":      dict(L=2.2, stride=(16, 16), lift=(4, 4), lift_base=(3, 3), lean=6, lean_var=1, roll=9, twist=2,
                       hip_roll=6, sway=1.6, bob=(0.4, 0.4), splay=(5, -5)),
    "hunched":    dict(L=1.4, stride=(15, 14), lift=(22, 20), lift_base=(12, 12), lean=32, lean_var=3, roll=4, twist=5,
                       hip_roll=2, sway=0.4, bob=(0.6, 0.6)),
    "lurch":      dict(L=2.6, stride=(28, 11), lift=(30, 8), lift_base=(8, 4), lean=12, lean_var=6, roll=6, twist=8,
                       sway=0.9, bob=(1.2, 1.0), skew=0.45, wobble=5, seed=1.3, stumble=(0.62, 14)),
    "heavy":      dict(L=2.7, stride=(19, 19), lift=(14, 14), lift_base=(6, 6), lean=8, lean_var=2, roll=8, twist=9,
                       hip_roll=5, sway=1.4, bob=(1.6, 1.6), splay=(4, -4)),
}
PURSUIT = {"hungry": dict(L=1.2, stride=(30, 28), lift=(38, 36), lift_base=(10, 10), lean=20, lean_var=3, roll=4,
                          twist=8, hip_roll=3, sway=0.5, bob=(1.4, 1.4))}


def body_layer():
    out, speeds = {}, {}
    stand = Anim(3.2, True, BODY)
    for i, t in enumerate([0, 0.8, 1.6, 2.4, 3.2]):
        sw = [0, 1, 0, -1, 0][i]
        stand.pose(t, torso=[12 + abs(sw) * 2, 4 * sw, 3 - 4 * abs(sw) + sw],
                   hips_pos=[0.3 * sw, -0.3 * abs(sw), 0], hips=[0, 0, -1.5 * sw],
                   rightLeg=[-4, 2, 2], rightShin=[6, 0, 0], leftLeg=[2, -4, -3], leftShin=[4, 0, 0])
    out["stand"] = stand
    speeds["stand"] = 0.0
    for name, p in {**GAITS, **PURSUIT}.items():
        p = dict(p)
        L = p.pop("L")
        a, v = gait(L, **p)
        out[name], speeds[name] = a, round(v, 3)
    out["shamble"].sound(0.5, "holdfast:walker.step").sound(1.5, "holdfast:walker.drag")
    out["drag_left"].sound(0.35, "holdfast:walker.drag").sound(1.4, "holdfast:walker.step")
    out["limp_right"].sound(0.0, "holdfast:walker.step").sound(1.44, "holdfast:walker.step_heavy")
    out["heavy"].sound(0.0, "holdfast:walker.step_heavy").sound(1.35, "holdfast:walker.step_heavy")
    out["lurch"].sound(1.6, "holdfast:walker.stumble")
    return out, speeds


# ===================================================================== arms
def arms_layer():
    out = {}

    def sway(L, fr, **tracks):
        a = Anim(L, True, ARMS)
        return loop_keys(a, L, fr, **tracks)

    w = lambda base, amp, n=4, ph=0.0: [base + amp * S(TAU * i / n + ph) for i in range(n)]
    n = 4
    out["dangle"] = sway(2.3, n,
                         rightArm=[[x, 0, 6 + 2 * S(TAU * i / n)] for i, x in enumerate(w(-12, 4))],
                         rightForearm=[[x, 0, 0] for x in w(-10, 4, ph=1.0)],
                         leftArm=[[x, 0, -5 - 2 * S(TAU * i / n + 1)] for i, x in enumerate(w(-9, 4, ph=2.0))],
                         leftForearm=[[x, 0, 0] for x in w(-8, 3, ph=3.0)])
    out["forward_low"] = sway(2.7, n,
                              rightArm=[[x, 0, 6] for x in w(-38, 5)], leftArm=[[x, 0, -6] for x in w(-34, 5, ph=1.6)],
                              rightForearm=[[x, 0, 0] for x in w(-24, 6, ph=0.8)],
                              leftForearm=[[x, 0, 0] for x in w(-22, 6, ph=2.4)])
    out["one_up"] = sway(2.1, n,
                         rightArm=[[x, 6, 4] for x in w(-74, 6)], rightForearm=[[x, 0, 0] for x in w(-14, 10, ph=1.2)],
                         leftArm=[[x, 0, -4] for x in w(-8, 3, ph=2.0)], leftForearm=[[-6, 0, 0]] * n)
    out["broken"] = sway(1.9, n,
                         rightArm=[[x, 0, 6] for x in w(-12, 4)], rightForearm=[[x, 0, 0] for x in w(-10, 4, ph=1.0)],
                         leftArm=[[x, 0, -10 + 3 * S(TAU * i / n)] for i, x in enumerate(w(-6, 6, ph=0.5))],
                         leftForearm=[[x, 8, -18] for x in w(26, 10, ph=1.7)])     # bent the wrong way
    out["clutched"] = sway(3.3, n,
                           rightArm=[[x, 0, -6] for x in w(-30, 3)], leftArm=[[x, 0, 6] for x in w(-30, 3, ph=1.0)],
                           rightForearm=[[x, 0, 0] for x in w(-100, 4, ph=0.4)],
                           leftForearm=[[x, 0, 0] for x in w(-96, 4, ph=2.2)])
    out["wide"] = sway(2.5, n,
                       rightArm=[[-14, 0, z] for z in w(24, 5)], leftArm=[[-14, 0, z] for z in w(-22, 5, ph=1.3)],
                       rightForearm=[[x, 0, 0] for x in w(-10, 5, ph=0.7)],
                       leftForearm=[[x, 0, 0] for x in w(-12, 5, ph=2.0)])
    reach = Anim(1.6, True, ARMS)
    ra = [[-78, 6, 4], [-88, -4, 2], [-72, 8, 6], [-84, -2, 3]]
    la = [[-70, -4, -4], [-80, 4, -2], [-90, -6, -6], [-74, 2, -3]]
    loop_keys(reach, 1.6, 4, rightArm=ra, leftArm=la,
              rightForearm=[[v, 0, 0] for v in (-6, -24, -4, -20)], leftForearm=[[v, 0, 0] for v in (-22, -4, -26, -8)])
    out["reach"] = reach
    return out


# ===================================================================== head
def head_layer():
    out = {}

    def h(L, n, head, jaw):
        a = Anim(L, True, HEAD)
        return loop_keys(a, L, n, head=head, jaw=[[j, 0, 0] for j in jaw])

    q = lambda i, n, ph=0.0: S(TAU * i / n + ph)
    n = 4
    out["loll_left"] = h(3.4, n, [[8 + 3 * q(i, n), -4 + 6 * q(i, n, 1), 22 + 4 * q(i, n, 2)] for i in range(n)],
                         [10 + 6 * q(i, n, 0.5) for i in range(n)])
    out["loll_right"] = h(3.0, n, [[10 + 4 * q(i, n), 6 + 5 * q(i, n, 1), -20 + 4 * q(i, n, 2)] for i in range(n)],
                          [6 + 6 * q(i, n, 1.5) for i in range(n)])
    out["chin_down"] = h(3.6, n, [[30 + 4 * q(i, n), 6 * q(i, n, 1), 4] for i in range(n)],
                         [14 + 6 * q(i, n, 2) for i in range(n)])
    out["sniff"] = h(2.4, n, [[-12 + 4 * q(i, n), 14 * q(i, n, 1), 4 * q(i, n, 2)] for i in range(n)],
                     [4 + 7 * q(i, n, 0.4) for i in range(n)])
    out["slack"] = h(3.8, n, [[20 + 3 * q(i, n), 4 * q(i, n, 1), 6 + 3 * q(i, n, 2)] for i in range(n)],
                     [26 + 4 * q(i, n, 0.8) for i in range(n)])
    tw = Anim(2.0, True, HEAD)               # mostly still, then sharp jerks and teeth chattering
    for t, hd, j in [(0, [10, 0, 8], 6), (0.55, [11, 2, 9], 6), (0.62, [4, 14, -14], 18), (0.74, [10, 2, 8], 4),
                     (0.84, [9, 0, 8], 12), (0.94, [9, 0, 8], 4), (1.04, [9, 0, 8], 12), (1.4, [10, -2, 7], 6),
                     (1.46, [-10, -6, 12], 20), (1.6, [10, 0, 8], 6), (2.0, [10, 0, 8], 6)]:
        tw.pose(t, head=hd, jaw=[j, 0, 0])
    out["twitch"] = tw
    out["snap"] = h(1.0, 6, [[-6 + 4 * q(i, 6), 6 * q(i, 6, 1), 4 * q(i, 6, 2)] for i in range(6)],
                    [2, 24, 4, 26, 2, 20])
    return out


# ================================================================ collapses
def collapse(a, t0, kind="front"):
    """Dead weight from t0: buckle to the knees, then go down face first, on the right side, or on the back."""
    a.pose(t0 + 0.12, hips_pos=[0, -2, 0], rightLeg=[-8, 0, 2], leftLeg=[-6, 0, -2],
           rightShin=[30, 0, 0], leftShin=[26, 0, 0], root=[0, 0, 0])
    if kind == "back":
        a.pose(t0 + 0.3, hips_pos=[0, -4, 0], rightShin=[60, 0, 0], leftShin=[56, 0, 0], torso=[-6, 0, 0],
               rightArm=[-40, 0, 20], leftArm=[-44, 0, -20], root=[-10, 0, 0])
        a.pose(t0 + 0.55, root=[-60, 0, 0], root_pos=[0, 1.5, 2], torso=[-10, 0, 0], rightShin=[34, 0, 0],
               leftShin=[30, 0, 0], rightArm=[-120, 0, 30], leftArm=[-110, 0, -34], head=[-20, 10, 0])
        a.pose(t0 + 0.72, root=[-86, 0, 0], root_pos=[0, 2.5, 3], torso=[0, 0, 0], rightShin=[8, 0, 0],
               leftShin=[14, 0, 0], hips_pos=[0, -4, 0], rightArm=[-150, 0, 34], leftArm=[-140, 0, -40],
               head=[-10, 40, 0], jaw=[20, 0, 0])
        a.pose(t0 + 0.82, root=[-82, 0, 0], root_pos=[0, 3, 3])
        a.pose(t0 + 0.96, root=[-85, 0, 0], root_pos=[0, 2.5, 3])
        return t0 + 0.72
    a.pose(t0 + 0.34, hips_pos=[0, -6, 0], rightLeg=[-10, 0, 3], leftLeg=[-10, 0, -3],
           rightShin=[88, 0, 0], leftShin=[86, 0, 0], torso=[26, 0, 0],
           rightArm=[-20, 0, 6], leftArm=[-18, 0, -6], rightForearm=[-4, 0, 0], leftForearm=[-4, 0, 0], root=[0, 0, 0])
    if kind == "front":
        a.pose(t0 + 0.42, root=[8, 0, 0])
        a.pose(t0 + 0.66, root=[55, 0, 0], rightShin=[30, 0, 0], leftShin=[34, 0, 0], torso=[8, 0, 0],
               rightArm=[-10, 0, 8], leftArm=[-10, 0, -8], root_pos=[0, 1, 0])
        a.pose(t0 + 0.84, root=[86, 0, 0], root_pos=[0, 2.5, 0], rightShin=[6, 0, 0], leftShin=[8, 0, 0],
               torso=[0, 0, 0], head=[-6, 72, 0], rightArm=[8, 0, 14], leftArm=[8, 0, -20], jaw=[18, 0, 0])
        a.pose(t0 + 0.94, root=[82, 0, 0], root_pos=[0, 3, 0])
        a.pose(t0 + 1.08, root=[85, 0, 0], root_pos=[0, 2.5, 0])
        return t0 + 0.84
    m = -1 if kind == "side_left" else 1      # "side": down on its right side; "side_left": the mirror
    a.pose(t0 + 0.42, root=[4, 0, -8 * m])
    a.pose(t0 + 0.66, root=[10, 0, -52 * m], root_pos=[0, 2, 0], torso=[22, 0, 0],
           rightArm=[-26, 0, 4], leftArm=[-34, 0, 4], rightShin=[74, 0, 0], leftShin=[70, 0, 0])
    a.pose(t0 + 0.84, root=[10, 0, -86 * m], root_pos=[0, 4.5, 0], torso=[20, 0, 0], head=[18, 0, -8 * m],
           rightArm=[-34, 0, 10], leftArm=[-44, 0, 14], rightShin=[66, 0, 0], leftShin=[56, 0, 0], jaw=[22, 0, 0])
    a.pose(t0 + 0.94, root=[10, 0, -80 * m], root_pos=[0, 4.2, 0])
    a.pose(t0 + 1.08, root=[10, 0, -84 * m], root_pos=[0, 4.5, 0])
    return t0 + 0.84



def ankle_set(prefix, base, bones):
    """Ground grab on the player's ankle: hold loop, bite, blocked bite, kicked free. Prone frame:
    head x more negative looks further ahead/up; arms sweep on z."""
    out = {}
    put = lambda a, t, **o: a.pose(t, **{k: v for k, v in dict(base, **o).items() if k.replace("_pos", "") in bones})
    a = Anim(0.8, True)
    for t, hx, jw, fa, rz in [(0, -70, 14, -34, 0), (0.2, -60, 30, -40, 0.6), (0.3, -62, 2, -40, 0.6),
                              (0.5, -74, 26, -32, -0.4), (0.6, -72, 4, -34, -0.4), (0.8, -70, 14, -34, 0)]:
        put(a, t, head=[hx, 0, 0], jaw=[jw, 0, 0], rightForearm=[fa, 0, 0], leftForearm=[fa, 0, 0],
            root_pos=[0, base["root_pos"][1], base["root_pos"][2] + rz])
    a.sound(0.2, "holdfast:walker.snap").sound(0.5, "holdfast:walker.snap")
    out[f"{prefix}_ankle_hold"] = a

    def bite(blocked):
        a = Anim(0.6 if blocked else 0.8, False)
        put(a, 0)
        a.pose(0.15, head=[-86, 0, 0], jaw=[34, 0, 0])
        a.pose(0.3, head=[-38, 0, 0], jaw=[30, 0, 0], root_pos=[0, base["root_pos"][1], base["root_pos"][2] - 1.5])
        if blocked:
            a.pose(0.36, jaw=[2, 0, 0])
            a.pose(0.42, head=[-84, 6, 0], jaw=[22, 0, 0], root_pos=list(base["root_pos"]))
            put(a, 0.6)
            a.sound(0.36, "holdfast:protect.bite_blocked").event(0.36, "bite_blocked")
        else:
            a.pose(0.38, jaw=[0, 0, 0])
            a.pose(0.48, head=[-34, 10, 0])
            a.pose(0.58, head=[-36, -10, 0])
            put(a, 0.8)
            a.sound(0.38, "holdfast:walker.bite_flesh").event(0.38, "bite")
        return a

    out[f"{prefix}_ankle_bite"] = bite(False)
    out[f"{prefix}_ankle_bite_blocked"] = bite(True)

    a = Anim(0.9, False)          # boot to the face: head snaps back, hands let go
    put(a, 0)
    a.pose(0.12, head=[-70, 0, 0])
    a.pose(0.2, head=[-118, 14, 10], jaw=[30, 0, 0], rightArm=[16, 0, 110], leftArm=[16, 0, -110],
           rightForearm=[-4, 0, 0], leftForearm=[-4, 0, 0], root_pos=[0, base["root_pos"][1], base["root_pos"][2] + 3])
    a.pose(0.5, head=[-30, 20, 0], jaw=[16, 0, 0])
    put(a, 0.9, head=[-46, 6, 0], rightArm=[2, 0, 140], leftArm=[2, 0, -140], rightForearm=[-10, 0, 0],
        leftForearm=[-10, 0, 0], root_pos=[0, base["root_pos"][1], base["root_pos"][2] + 3])
    a.sound(0.14, "holdfast:hit.kick").event(0.14, "kicked")   # code separates them here
    out[f"{prefix}_ankle_kicked"] = a
    return out


# ============================================================ full body
def full_body():
    out = {}

    # --- motionless walker (§12 initially motionless): barely drifts until it notices something
    a = Anim(6.0, True)
    for i, t in enumerate([0, 1.5, 3.0, 4.5, 6.0]):
        k = [0, 1, 0, -1, 0][i]
        full(a, t, NEUTRAL, torso=[10 + abs(k), k, 2], head=[26 + k, -8, 10 + k], jaw=[6 + 2 * abs(k), 0, 0],
             rightArm=[-6, 0, 4], leftArm=[-4, 0, -4], rightForearm=[-4, 0, 0], leftForearm=[-4, 0, 0])
    out["still"] = a

    # --- slumped sitting (against a wall or a car)
    SIT = dict(NEUTRAL, hips_pos=[0, -10, 0], torso=[-14, 0, 0], head=[40, -10, 14], jaw=[10, 0, 0],
               rightLeg=[-100, 6, 8], leftLeg=[-84, -4, -10], rightShin=[56, 0, 0], leftShin=[30, 0, 0],
               rightArm=[-10, 0, 18], rightForearm=[-10, 0, 0], leftArm=[-24, 0, -8], leftForearm=[-34, 0, 0])
    a = Anim(5.0, True)
    full(a, 0, SIT)
    full(a, 2.5, SIT, head=[42, -8, 16], jaw=[12, 0, 0])
    full(a, 5.0, SIT)
    out["sit_slumped"] = a
    a = Anim(2.4, False)
    full(a, 0, SIT)
    a.pose(0.4, head=[-8, 0, 4], jaw=[24, 0, 0], torso=[2, 0, 0], rightArm=[8, 0, 30], leftArm=[6, 0, -30])
    a.pose(1.0, hips_pos=[0, -6, 0], rightLeg=[-10, 0, 3], leftLeg=[-10, 0, -3], rightShin=[88, 0, 0],
           leftShin=[86, 0, 0], torso=[32, 0, 0], head=[-14, 0, 0], rightArm=[-40, 0, 8], leftArm=[-44, 0, -8],
           rightForearm=[-10, 0, 0], leftForearm=[-10, 0, 0])
    a.pose(1.6, hips_pos=[0, -4, 0], rightLeg=[-50, 0, 2], leftLeg=[-30, 0, -2], rightShin=[82, 0, 0],
           leftShin=[70, 0, 0], torso=[40, 0, 0], head=[-20, 0, 0], jaw=[18, 0, 0])
    full(a, 2.4, APPROACH)
    a.sound(0.4, "holdfast:walker.notice")
    out["rise_from_sit"] = a

    # --- prone (face down, looks dead; §14 motionless bodies are not harmless)
    PRONE = dict(NEUTRAL, root=[90, 0, 0], root_pos=[0, 2, 16], torso=[0, 0, 0], head=[-10, 70, 0], jaw=[10, 0, 0],
                 rightArm=[0, 0, 20], rightForearm=[0, 0, 0], leftArm=[0, 0, -150], leftForearm=[-20, 0, 0],
                 rightLeg=[0, 0, 4], leftLeg=[0, 0, -4], rightShin=[6, 0, 0], leftShin=[8, 0, 0])
    a = Anim(5.0, True)
    full(a, 0, PRONE)
    full(a, 2.5, PRONE, jaw=[12, 0, 0])
    full(a, 5.0, PRONE)
    out["prone_still"] = a
    a = Anim(2.6, False)
    full(a, 0, PRONE)
    # hands planted under the shoulders, elbows up (in the prone frame, +x lifts the arm off the ground)
    a.pose(0.4, head=[-60, 0, 0], jaw=[24, 0, 0], rightArm=[40, 0, 30], leftArm=[40, 0, -30],
           rightForearm=[-95, 0, 0], leftForearm=[-95, 0, 0])
    a.pose(0.9, root=[70, 0, 0], root_pos=[0, 2, 12], torso=[10, 0, 0], rightArm=[-60, 0, 14], leftArm=[-60, 0, -14],
           rightLeg=[-40, 0, 3], leftLeg=[-40, 0, -3], rightShin=[90, 0, 0], leftShin=[90, 0, 0], head=[-50, 0, 0])
    a.pose(1.5, root=[20, 0, 0], root_pos=[0, 0, 4], hips_pos=[0, -6, 0], rightLeg=[-10, 0, 3], leftLeg=[-10, 0, -3],
           rightShin=[88, 0, 0], leftShin=[86, 0, 0], torso=[40, 0, 0], rightArm=[-50, 0, 8], leftArm=[-50, 0, -8],
           head=[-20, 0, 0])
    a.pose(2.0, root=[0, 0, 0], root_pos=[0, 0, 0], hips_pos=[0, -4, 0], rightLeg=[-50, 0, 2], leftLeg=[-30, 0, -2],
           rightShin=[82, 0, 0], leftShin=[70, 0, 0], torso=[40, 0, 0], head=[-20, 0, 0])
    full(a, 2.6, APPROACH)
    a.sound(0.3, "holdfast:walker.notice")
    out["prone_rise"] = a
    a = Anim(1.2, "hold_on_last_frame")
    full(a, 0, PRONE)
    a.pose(0.25, head=[-70, 0, 0], jaw=[30, 0, 0], leftArm=[0, 0, -150], rightArm=[0, 0, 60])
    a.pose(0.5, rightArm=[10, 0, 165], leftArm=[10, 0, -165], rightForearm=[-6, 0, 0], leftForearm=[-6, 0, 0],
           root_pos=[0, 2, 13], head=[-74, 0, 0], jaw=[34, 0, 0])
    a.pose(0.8, rightArm=[4, 0, 150], leftArm=[4, 0, -150], rightForearm=[-34, 0, 0], leftForearm=[-34, 0, 0],
           jaw=[4, 0, 0])
    a.pose(1.2, jaw=[26, 0, 0], head=[-68, 6, 0])
    a.sound(0.2, "holdfast:walker.lunge")
    a.event(0.55, "grab_locked")
    out["prone_ankle_grab"] = a

    # held onto the ankle (loops while the escape prompt is up), bite, blocked bite, kicked free
    ANK = dict(PRONE, root_pos=[0, 2, 13], head=[-70, 0, 0], jaw=[14, 0, 0], rightArm=[4, 0, 150], leftArm=[4, 0, -150],
               rightForearm=[-34, 0, 0], leftForearm=[-34, 0, 0])
    out.update(ankle_set("prone", ANK, ALL))

    # --- lunge, stagger, headshot death
    a = Anim(1.0, False)
    full(a, 0.0, NEUTRAL)
    a.pose(0.25, root_pos=[0, 0, 1], torso=[4, 0, 0], head=[-8, 0, 2], jaw=[20, 0, 0], rightArm=[-60, 0, 14],
           leftArm=[-60, 0, -14], rightForearm=[-40, 0, 0], leftForearm=[-40, 0, 0], rightLeg=[6, 0, 2],
           leftLeg=[-4, 0, -2], rightShin=[16, 0, 0], leftShin=[18, 0, 0], hips_pos=[0, -1.2, 0])
    a.pose(0.45, root_pos=[0, 0, -10], torso=[34, 0, 0], head=[-22, 0, 0], jaw=[32, 0, 0], rightArm=[-96, 0, 4],
           leftArm=[-92, 0, -4], rightForearm=[-4, 0, 0], leftForearm=[-4, 0, 0], rightLeg=[-38, 0, 2],
           leftLeg=[22, 0, -2], rightShin=[20, 0, 0], leftShin=[14, 0, 0], hips_pos=[0, -1.6, 0])
    a.pose(0.6, root_pos=[0, 0, -10], torso=[38, 0, 0], head=[18, 0, 0], jaw=[0, 0, 0], rightArm=[-80, -10, 0],
           leftArm=[-78, 10, 0], rightForearm=[-44, 0, 0], leftForearm=[-44, 0, 0], rightLeg=[-34, 0, 2],
           leftLeg=[20, 0, -2], rightShin=[24, 0, 0], leftShin=[16, 0, 0], hips_pos=[0, -2, 0])
    full(a, 1.0, NEUTRAL)
    a.sound(0.25, "holdfast:walker.lunge").sound(0.6, "holdfast:walker.bite")
    a.event(0.55, "attack_hit")
    out["lunge"] = a

    a = Anim(0.8, False)               # root offsets are small; the knockback itself is code-driven
    full(a, 0.0, NEUTRAL)
    a.pose(0.1, root_pos=[0, 0, 2], torso=[-10, -6, 6], head=[22, 0, 10], jaw=[24, 0, 0], hips_pos=[0, -0.5, 0],
           rightArm=[-55, 0, 24], leftArm=[-48, 0, -26], rightForearm=[-6, 0, 0], leftForearm=[-8, 0, 0],
           rightLeg=[-10, 0, 2], leftLeg=[6, 0, -2], rightShin=[8, 0, 0], leftShin=[10, 0, 0])
    a.pose(0.3, root_pos=[0, 0, 3], torso=[-4, 4, -4], head=[-14, 6, 0], jaw=[14, 0, 0], hips_pos=[0, -1.2, 0],
           rightArm=[-20, 0, 18], leftArm=[-24, 0, -20], rightForearm=[-24, 0, 0], leftForearm=[-20, 0, 0],
           rightLeg=[-8, 0, 2], leftLeg=[22, 0, -2], rightShin=[10, 0, 0], leftShin=[24, 0, 0])
    a.pose(0.55, root_pos=[0, 0, 1.5], torso=[18, 0, 2], head=[12, -4, 8], jaw=[10, 0, 0], hips_pos=[0, -0.6, 0],
           rightArm=[-36, 0, 8], leftArm=[-30, 0, -8], rightLeg=[-4, 0, 2], leftLeg=[8, 0, -2], leftShin=[12, 0, 0])
    full(a, 0.8, NEUTRAL)
    a.sound(0.0, "holdfast:walker.hurt")
    out["stagger"] = a

    a = Anim(1.6, "hold_on_last_frame")
    full(a, 0.0, NEUTRAL)
    a.pose(0.06, head=[-28, 4, 12], jaw=[24, 0, 0], torso=[6, 0, 0], rightArm=[-36, 0, 16], leftArm=[-34, 0, -16],
           rightForearm=[-6, 0, 0], leftForearm=[-6, 0, 0])
    a.pose(0.2, head=[16, 0, 8])
    a.pose(0.4, head=[38, 0, 6])
    a.pose(0.62, head=[30, 30, 0])
    hit = collapse(a, 0.06, "front")
    full(a, 1.6, NEUTRAL, root=[85, 0, 0], root_pos=[0, 2.5, 0], hips_pos=[0, -6, 0], head=[-6, 72, 0], torso=[0, 0, 0],
         rightLeg=[-10, 0, 3], leftLeg=[-10, 0, -3], rightShin=[6, 0, 0], leftShin=[8, 0, 0],
         rightArm=[8, 0, 14], leftArm=[8, 0, -20], rightForearm=[-4, 0, 0], leftForearm=[-4, 0, 0], jaw=[18, 0, 0])
    a.sound(0.0, "holdfast:walker.headshot").sound(0.4, "holdfast:body.knees").sound(hit, "holdfast:body.fall")
    a.event(1.6, "corpse")
    out["death_headshot"] = a
    return out


# ================================================================== grab
# Paired with the player: the walker is snapped GRAB_DIST px in front of them, facing them.
GRAB_DIST = 18
HOLD = dict(APPROACH, root_pos=[0, 0, -2], torso=[24, 0, 0], head=[-10, 8, 0], jaw=[18, 0, 0],
            rightArm=[-80, 10, 0], leftArm=[-80, -10, 0], rightForearm=[-40, 0, 0], leftForearm=[-40, 0, 0],
            rightLeg=[-6, 0, 2], leftLeg=[14, 0, -2], rightShin=[10, 0, 0], leftShin=[12, 0, 0])


def grab_side():
    out = {}
    a = Anim(0.5, False)
    full(a, 0, APPROACH)
    a.pose(0.25, root_pos=[0, 0, -1.5], rightArm=[-88, 0, 4], leftArm=[-86, 0, -4], rightForearm=[-4, 0, 0],
           leftForearm=[-4, 0, 0], jaw=[30, 0, 0], torso=[22, 0, 0])
    full(a, 0.4, HOLD)
    full(a, 0.5, HOLD, jaw=[10, 0, 0])
    a.sound(0.1, "holdfast:walker.lunge").sound(0.4, "holdfast:walker.grab")
    a.event(0.4, "grab_locked")
    out["grab_seize"] = a

    a = Anim(0.8, True)                  # loops while the escape prompt is up
    for t, hd, jw, tz, fa in [(0, [-10, 8, 0], 10, 24, -40), (0.2, [-4, 14, 6], 30, 26, -46), (0.3, [-6, 12, 4], 2, 26, -44),
                              (0.5, [-14, 4, -4], 26, 23, -40), (0.6, [-12, 6, -2], 4, 24, -42), (0.8, [-10, 8, 0], 10, 24, -40)]:
        full(a, t, HOLD, head=hd, jaw=[jw, 0, 0], torso=[tz, 0, 0], rightForearm=[fa, 0, 0], leftForearm=[fa + 2, 0, 0])
    a.sound(0.2, "holdfast:walker.snap").sound(0.5, "holdfast:walker.snap")
    out["grab_hold"] = a

    def bite(blocked):
        a = Anim(0.8 if not blocked else 0.6, False)
        full(a, 0, HOLD)
        a.pose(0.15, head=[-18, 6, 0], jaw=[34, 0, 0], torso=[22, 0, 0])
        a.pose(0.3, head=[24, 22, 10], torso=[30, 0, 0], root_pos=[0, 0, -3], jaw=[30, 0, 0])
        if blocked:
            a.pose(0.36, jaw=[2, 0, 0])
            a.pose(0.42, head=[-20, 4, -6], torso=[22, 0, 0], root_pos=[0, 0, -2], jaw=[22, 0, 0])
            full(a, 0.6, HOLD)
            a.sound(0.36, "holdfast:protect.bite_blocked")
            a.event(0.36, "bite_blocked")
            return a
        a.pose(0.38, jaw=[0, 0, 0])
        a.pose(0.48, head=[26, 14, 12], jaw=[2, 0, 0])
        a.pose(0.58, head=[24, 26, 8], jaw=[0, 0, 0])
        full(a, 0.8, HOLD, head=[-8, 10, 2], jaw=[14, 0, 0])
        a.sound(0.38, "holdfast:walker.bite_flesh")
        a.event(0.38, "bite")
        return a

    out["grab_bite"] = bite(False)
    out["grab_bite_blocked"] = bite(True)

    a = Anim(0.9, False)
    full(a, 0, HOLD)
    a.pose(0.1, torso=[28, 0, 0], root_pos=[0, 0, -1.5])            # compressed as the player loads up
    a.pose(0.2, rightArm=[-112, 0, 30], leftArm=[-108, 0, -32], rightForearm=[-6, 0, 0], leftForearm=[-6, 0, 0],
           torso=[-8, 0, 0], head=[-24, 0, 6], jaw=[30, 0, 0], root_pos=[0, 0, 2])
    a.pose(0.38, head=[20, -6, 10], rightLeg=[-4, 0, 2], leftLeg=[26, 0, -2], leftShin=[20, 0, 0], hips_pos=[0, -1, 0],
           torso=[-2, 0, 4], rightArm=[-50, 0, 20], leftArm=[-44, 0, -24])
    a.pose(0.62, torso=[20, 0, 0], head=[2, 0, 4], hips_pos=[0, -0.4, 0], root_pos=[0, 0, 1], leftLeg=[10, 0, -2])
    full(a, 0.9, APPROACH)
    a.sound(0.14, "holdfast:walker.shoved")
    a.event(0.14, "shoved")          # code applies ~0.75 blocks of knockback here
    out["grab_shoved"] = a
    return out


# ============================================================ executions
# Shared with player_anims.py. Times in seconds; dist in px between survivor and walker.
EXECS = {
    "knife":       dict(length=1.0, dist=18, commit=0.22, impact=0.33, release=0.70, kind="stab", fall="back"),
    "bat_quick":   dict(length=0.65, dist=28, commit=0.12, impact=0.22, kind="blunt_stagger"),
    "bat_charged": dict(length=1.1, dist=28, commit=0.06, impact=0.2, kind="blunt_kill", fall="side"),
    "axe":         dict(length=1.3, dist=26, commit=0.30, impact=0.46, release=0.86, kind="chop_top", fall="side"),
    "machete":     dict(length=0.95, dist=22, commit=0.16, impact=0.30, release=0.62, kind="chop_side", fall="side"),
    # downward stab into a target on the ground; the player anim is solved against the crawler,
    # the prone walker is placed so its head sits in the same spot (dist values set by placement())
    "knife_ground": dict(length=1.2, dist=0, dist_prone=0, commit=0.32, impact=0.5, release=0.86, kind="stab_ground"),
    # same scheme for the other melee weapons (Andrew, 2026-10-16); release = blade out / bat lifted
    "axe_ground":     dict(length=1.4, commit=0.34, impact=0.56, release=0.96, kind="chop_ground"),
    "machete_ground": dict(length=1.1, commit=0.2, impact=0.38, release=0.72, kind="chop_ground"),
    "bat_ground":     dict(length=1.2, commit=0.2, impact=0.42, release=0.74, kind="blunt_ground"),
    # TaCZ pistol class only (decisions.md); player clips in player_guns.py start from TaCZ's own hold
    "pistol":         dict(length=1.1, dist=24, commit=0.18, impact=0.36, kind="shot", fall="back"),
    "pistol_ground":  dict(length=1.2, commit=0.22, impact=0.46, release=0.56, kind="shot_ground"),
}
GROUND_EXECS = ("knife", "axe", "machete", "bat", "pistol")


def reactions():
    out = {}
    for name, e in EXECS.items():
        I, kind = e["impact"], e["kind"]
        if kind.endswith("_ground"):          # ground executions have their own reactions
            continue
        R = e.get("release", I)
        if kind == "blunt_stagger":
            a = Anim(1.0, False)
            full(a, 0, APPROACH)
            full(a, I - 0.02, APPROACH)
            a.pose(I + 0.05, head=[-6, -30, -32], jaw=[30, 0, 0], torso=[10, -20, -10], root_pos=[-1, 0, 0])
            a.pose(I + 0.25, head=[16, -18, -20], torso=[6, -12, -14], root_pos=[-3, 0, 1.5], rightLeg=[-6, 0, 12],
                   leftLeg=[14, 0, -2], hips_pos=[0, -1, 0], rightArm=[-40, 0, 26], leftArm=[-30, 0, -14])
            a.pose(I + 0.5, head=[4, -6, -6], torso=[16, -4, -4], root_pos=[-2, 0, 0.5], hips_pos=[0, -0.4, 0])
            full(a, 1.0, APPROACH)
            a.sound(I, "holdfast:hit.blunt_skull")
            a.event(I, "stagger")
            out[f"react_{name}"] = a
            continue
        length = round(R + 1.0, 2)
        a = Anim(length, "hold_on_last_frame")
        full(a, 0, APPROACH)
        if kind == "stab":
            # The knife hooks into the LEFT temple travelling across the head (player_anims.knife), so the
            # head is knocked over toward the walker's right and turned away, not snapped back as for a
            # frontal eye stab (Andrew, in game 2026-10-16). z < 0 tilts toward its right; y < 0 turns right.
            a.pose(0.12, torso=[14, 0, 0], root_pos=[0, 0, 0.5])            # palm on its chest
            a.pose(I, head=[-4, -16, -26], jaw=[30, 0, 0], rightForearm=[-2, 0, 0], leftForearm=[-2, 0, 0])
            a.pose(I + 0.08, head=[-2, -18, -30], torso=[12, -4, -6], rightArm=[-50, 0, 10], leftArm=[-46, 0, -10])
            a.pose(R - 0.12, head=[4, -14, -24], torso=[16, -4, -6], hips_pos=[0, -1, 0], rightArm=[-20, 0, 6],
                   leftArm=[-16, 0, -6], rightShin=[18, 0, 0], leftShin=[16, 0, 0])
            a.pose(R - 0.06, head=[0, -6, -10])                              # blade wrenched out, head swings back
            a.pose(R + 0.04, root_pos=[0, 0, 3], torso=[-6, 0, 0], head=[-14, 10, 8])  # shoved off
            hit = collapse(a, R, "back")
        elif kind == "shot":
            # close-range shot to the forehead: head whips back, knees go, falls straight back
            full(a, I - 0.02, APPROACH)
            a.pose(I + 0.04, head=[-46, 4, 6], jaw=[34, 0, 0], torso=[-10, 0, 0], root_pos=[0, 0, 0.8],
                   rightArm=[-30, 0, 20], leftArm=[-26, 0, -18], rightForearm=[-4, 0, 0], leftForearm=[-4, 0, 0])
            a.pose(I + 0.14, head=[-30, 2, 4], torso=[-14, 0, 0], hips_pos=[0, -1.5, 0], rightShin=[20, 0, 0],
                   leftShin=[18, 0, 0])
            hit = collapse(a, I + 0.1, "back")
        elif kind == "blunt_kill":
            full(a, I - 0.02, APPROACH)
            a.pose(I + 0.06, head=[-10, -40, -55], jaw=[34, 0, 0], torso=[8, -26, -16], root_pos=[-2, 0, 0],
                   rightArm=[-40, 0, 34], leftArm=[-20, 0, -30], rightForearm=[-4, 0, 0], leftForearm=[-4, 0, 0])
            a.pose(I + 0.3, root=[0, -50, 0], head=[20, -30, -40])
            hit = collapse(a, I + 0.14, "side")
        elif kind == "chop_top":
            a.pose(I, head=[28, 0, 4], jaw=[30, 0, 0], hips_pos=[0, -2, 0], rightShin=[22, 0, 0], leftShin=[20, 0, 0],
                   rightArm=[-24, 0, 8], leftArm=[-20, 0, -8], rightForearm=[-2, 0, 0], leftForearm=[-2, 0, 0])
            a.pose(R - 0.22, hips_pos=[0, -3, 0], torso=[20, 0, 0], head=[30, 0, 4], rightShin=[34, 0, 0], leftShin=[30, 0, 0])
            a.pose(R - 0.14, head=[16, 0, 2])
            a.pose(R - 0.08, head=[28, 0, 4])
            a.pose(R, head=[8, 0, 0])
            hit = collapse(a, R, "side")
        else:   # chop_side (machete): blade bites the left temple from above
            a.pose(I, head=[18, -10, -24], jaw=[28, 0, 0], torso=[20, -6, -6], rightForearm=[-2, 0, 0], leftForearm=[-2, 0, 0])
            a.pose(I + 0.1, rightArm=[-30, 0, 10], leftArm=[-26, 0, -10], hips_pos=[0, -0.3, 0], rightShin=[10, 0, 0],
                   leftShin=[9, 0, 0])      # hangs on the blade until it is wrenched free
            a.pose(R - 0.06, head=[22, -12, -28])
            a.pose(R, head=[6, -4, -14])
            hit = collapse(a, R, "side")
        a.sound(I, {"stab": "holdfast:hit.knife_skull", "blunt_kill": "holdfast:hit.blunt_skull_heavy",
                    "chop_top": "holdfast:hit.axe_skull", "chop_side": "holdfast:hit.blade_skull",
                    "shot": "holdfast:hit.gunshot_skull"}[kind])
        a.sound(hit, "holdfast:body.fall")
        a.event(I, "kill")
        if "release" in e:
            a.event(R, "release")
        out[f"react_{name}"] = a.fit_length()
    return out



# Execution approach directions other than the authored front pair (Andrew 2026-10-17: executions from the rear,
# either side and diagonals). The player's clip is unchanged and still lands on the skull, which is a centred
# cube; the walker keeps its own facing (turned at most 45 deg on the server) and plays one of these instead of
# spinning to face the player. Name: the side of the WALKER the player is on. Timing (impact, kill, release) is
# the front pair's, so the server's kill tick is unchanged.
DIRECTIONS = {"left": 90.0, "right": 270.0, "rear": 0.0}     # pair yaw theta (front pair: 180)
UPRIGHT_EXECS = ("knife", "axe", "machete", "bat_charged", "pistol")


def directional_reactions():
    out = {}
    for name in UPRIGHT_EXECS:
        e = EXECS[name]
        I, kind = e["impact"], e["kind"]
        R = e.get("release", I)
        pinned = kind in ("stab", "chop_side", "chop_top")          # hangs on the blade until it is wrenched out
        for side in DIRECTIONS:
            a = Anim(round(R + 1.0, 2), "hold_on_last_frame")
            full(a, 0, APPROACH)
            full(a, I - 0.02, APPROACH)                               # no lean toward a player it never faced
            if side == "rear":
                # struck from behind: head driven forward and down, folds over the blow, goes down face first
                blow = dict(head=[34, 0, 0], jaw=[30, 0, 0], torso=[26, 0, 0], root_pos=[0, 0, -0.6],
                            rightArm=[-40, 0, 10], leftArm=[-36, 0, -10], rightForearm=[-4, 0, 0], leftForearm=[-4, 0, 0])
                fall = "front"
            else:
                # struck from its left (player on that side): head knocked toward its right, and the mirror
                m = 1 if side == "left" else -1
                blow = dict(head=[-4, -12 * m, -32 * m], jaw=[30, 0, 0], torso=[10, -6 * m, -12 * m],
                            root_pos=[0.8 * m, 0, 0], rightArm=[-44, 0, 10 + 8 * m], leftArm=[-40, 0, -10 + 8 * m],
                            rightForearm=[-4, 0, 0], leftForearm=[-4, 0, 0])
                fall = "side" if side == "left" else "side_left"
            a.pose(I + 0.05, **blow)
            if pinned:
                a.pose(R - 0.06, **dict(blow, hips_pos=[0, -1, 0], rightShin=[16, 0, 0], leftShin=[14, 0, 0]))
                hit = collapse(a, R, fall)
            else:
                hit = collapse(a, I + 0.12, fall)
            a.sound(I, {"stab": "holdfast:hit.knife_skull", "blunt_kill": "holdfast:hit.blunt_skull_heavy",
                        "chop_top": "holdfast:hit.axe_skull", "chop_side": "holdfast:hit.blade_skull",
                        "shot": "holdfast:hit.gunshot_skull"}[kind])
            a.sound(hit, "holdfast:body.fall")
            a.event(I, "kill")
            if "release" in e:
                a.event(R, "release")
            out[f"react_{name}_{side}"] = a.fit_length()
    return out


def ground_reactions():
    """Prone walker and crawler hit in the skull from above: pinned by a knife, axe or machete (released when the
    blade comes out) or crushed by a bat. Names: react_<weapon>_ground_prone (walker) and
    react_<weapon>_ground_crawler (registered on the crawler as react_<weapon>_ground)."""
    out = {}
    for w in GROUND_EXECS:
        out.update(_ground_reaction(w))
    return out


def _ground_reaction(w):
    e = EXECS[f"{w}_ground"]
    I, R = e["impact"], e["release"]
    blunt = e["kind"].startswith(("blunt", "shot"))          # no blade to pull out: the head bounces
    hit = {"knife": "holdfast:hit.knife_skull", "axe": "holdfast:hit.axe_skull", "machete": "holdfast:hit.machete_skull",
           "bat": "holdfast:hit.bat_skull", "pistol": "holdfast:hit.gunshot_skull"}[w]
    out = {}
    # prone walker: lifts its head at the player, gets pinned
    PR = dict(NEUTRAL, root=[90, 0, 0], root_pos=[0, 2, 16], torso=[0, 0, 0], head=[-50, 0, 0], jaw=[20, 0, 0],
              rightArm=[6, 0, 150], rightForearm=[-20, 0, 0], leftArm=[6, 0, -150], leftForearm=[-20, 0, 0],
              rightLeg=[0, 0, 4], leftLeg=[0, 0, -4], rightShin=[6, 0, 0], leftShin=[8, 0, 0])
    for prefix, base, bones in ((f"react_{w}_ground_prone", PR, ALL), (f"react_{w}_ground_crawler", dict(CBASE, head=[-50, 0, 0], jaw=[20, 0, 0], rightArm=[6, 0, 150], leftArm=[6, 0, -150], rightForearm=[-20, 0, 0], leftForearm=[-20, 0, 0]), CRAWL_BONES)):
        a = Anim(round(R + 0.9, 2), "hold_on_last_frame")
        a.pose(0, **{k: v for k, v in base.items() if k.replace("_pos", "") in bones})
        a.pose(0.2, jaw=[30, 0, 0], head=[-54, 0, 0])
        a.pose(I - 0.02, head=[-52, 0, 0])
        a.pose(I + 0.04, head=[-14, 0, 6], jaw=[34, 0, 0], rightForearm=[-2, 0, 0], leftForearm=[-2, 0, 0],
               rightArm=[14, 0, 166], leftArm=[14, 0, -166])                                  # slammed down, hands splay
        a.pose(I + 0.2, head=[-10, 0, 4], rightArm=[0, 0, 140], leftArm=[0, 0, -140])
        if blunt:                                                                               # crushed: no pin, it bounces
            a.pose(I + 0.1, head=[-4, 0, 8], jaw=[38, 0, 0])
            a.pose(R, head=[-2, 14, 4], jaw=[28, 0, 0])
        else:
            a.pose(R - 0.08, head=[-16, 0, 4])
            a.pose(R + 0.06, head=[-6, 20, 0], jaw=[22, 0, 0])                                 # blade out, head rolls
        a.pose(R + 0.9, head=[-4, 30, 0], jaw=[20, 0, 0], rightArm=[0, 0, 120], leftArm=[0, 0, -120],
               rightForearm=[0, 0, 0], leftForearm=[0, 0, 0])
        a.sound(I, hit).event(I, "kill").event(R, "release").event(R + 0.9, "corpse")
        out[prefix] = a
    return out

# ================================================================ crawler
CRAWL_BONES = {"root", "torso", "stub", "gut", "head", "jaw", "rightArm", "leftArm", "rightForearm", "leftForearm"}
CBASE = dict(root=[90, 0, 0], root_pos=[0, 2, 20], torso=[0, 0, 0], stub=[0, 0, 0], gut=[10, 0, 0],
             head=[-60, 0, 0], jaw=[10, 0, 0], rightArm=[0, 0, 150], leftArm=[0, 0, -150],
             rightForearm=[-10, 0, 0], leftForearm=[-10, 0, 0])


def crawler():
    out = {}

    def stroke(uu):
        uu %= 1.0
        if uu < 0.5:
            k = uu / 0.5
            return 165 - 110 * (0.5 - 0.5 * C(math.pi * k)), 0.0, -26 * S(math.pi * k)
        k = (uu - 0.5) / 0.5
        return 55 + 110 * (0.5 - 0.5 * C(math.pi * k)), 18 * S(math.pi * k), 0.0

    L, n = 2.4, 12
    a = Anim(L, True)
    for i in range(n + 1):
        u = i / n
        rz, rl, rc = stroke(u)
        lz, ll, lc = stroke(u + 0.5)
        pull = S(TAU * u)
        a.pose(L * u, root=[90, 0, 0], root_pos=[0, 2, 20 - 1.0 * abs(pull)], torso=[0, 6 * pull, 6 * pull],
               stub=[0, 0, -4 * pull], gut=[12 + 4 * S(TAU * u + 1), 10 * pull, 0],
               head=[-66 + 4 * S(2 * TAU * u), 8 * pull, 0], jaw=[8 + 14 * pos(S(TAU * u + 1.0)), 0, 0],
               rightArm=[rl, 0, rz], rightForearm=[rc, 0, 0], leftArm=[ll, 0, -lz], leftForearm=[lc, 0, 0])
    a.sound(0.4, "holdfast:walker.crawl_drag").sound(1.6, "holdfast:walker.crawl_drag")
    out["crawl"] = a

    L, n = 1.6, 8                         # both arms at once: lunge-and-drag, faster and uglier
    a = Anim(L, True)
    for i in range(n + 1):
        u = i / n
        k = 0.5 - 0.5 * C(TAU * u)       # 0 reach -> 1 pulled -> 0
        a.pose(L * u, root=[90 - 6 * S(TAU * u), 0, 0], root_pos=[0, 2, 20 - 2.2 * k], torso=[4 * S(TAU * u), 0, 0],
               stub=[0, 0, 0], gut=[14 - 6 * k, 0, 0], head=[-70 + 14 * k, 0, 0], jaw=[24 - 20 * k, 0, 0],
               rightArm=[6 * (1 - k), 0, 168 - 100 * k], leftArm=[6 * (1 - k), 0, -(168 - 100 * k)],
               rightForearm=[-30 * k, 0, 0], leftForearm=[-30 * k, 0, 0])
    a.sound(0.8, "holdfast:walker.crawl_drag")
    out["crawl_drag"] = a

    a = Anim(4.0, True)
    for i, t in enumerate([0, 2.0, 4.0]):
        k = [0, 1, 0][i]
        a.pose(t, **dict(CBASE, head=[-34 - 6 * k, 6 * k, 4 * k], jaw=[8 + 10 * k, 0, 0], rightArm=[0, 0, 140 + 6 * k],
                         leftArm=[0, 0, -130 - 4 * k]))
    out["crawler_still"] = a

    a = Anim(1.6, True)                   # props itself up on one arm and claws upward
    for i, t in enumerate([0, 0.4, 0.8, 1.2, 1.6]):
        k = [0, 1, 0.4, 1, 0][i]
        a.pose(t, **dict(CBASE, root=[72 - 4 * k, 0, 6], root_pos=[0, 3, 20], torso=[-6, 0, 0],
                         leftArm=[-80, 0, -14], leftForearm=[0, 0, 0],
                         rightArm=[-40 - 30 * k, 0, 150], rightForearm=[-30 + 20 * k, 0, 0],
                         head=[-50 - 6 * k, 0, 0], jaw=[6 + 24 * k, 0, 0]))
    out["crawler_reach"] = a

    a = Anim(1.0, "hold_on_last_frame")
    a.pose(0, **dict(CBASE, head=[-60, 0, 0], jaw=[20, 0, 0]))
    a.pose(0.08, head=[-74, 10, 8], jaw=[30, 0, 0])
    a.pose(0.4, head=[-10, 60, 0], jaw=[16, 0, 0], rightArm=[0, 0, 120], leftArm=[0, 0, -120],
           rightForearm=[0, 0, 0], leftForearm=[0, 0, 0])
    a.pose(0.5, head=[-6, 66, 0])
    a.pose(1.0, head=[-8, 64, 0])
    a.sound(0.0, "holdfast:walker.headshot")
    a.event(1.0, "corpse")
    out["crawler_death"] = a

    a = Anim(0.9, "hold_on_last_frame")    # attack: surges forward and grabs at the ankles
    a.pose(0, **dict(CBASE, head=[-60, 0, 0], jaw=[14, 0, 0]))
    a.pose(0.2, rightArm=[14, 0, 120], leftArm=[14, 0, -120], head=[-76, 0, 0], jaw=[30, 0, 0], root_pos=[0, 2, 20.5])
    a.pose(0.45, rightArm=[8, 0, 170], leftArm=[8, 0, -170], rightForearm=[-4, 0, 0], leftForearm=[-4, 0, 0],
           root_pos=[0, 2, 17], head=[-74, 0, 0], jaw=[34, 0, 0])
    a.pose(0.7, rightArm=[4, 0, 150], leftArm=[4, 0, -150], rightForearm=[-34, 0, 0], leftForearm=[-34, 0, 0],
           root_pos=[0, 2, 17], jaw=[6, 0, 0])
    a.pose(0.9, head=[-70, 0, 0], jaw=[14, 0, 0])
    a.sound(0.2, "holdfast:walker.lunge")
    a.event(0.5, "grab_locked")
    out["crawler_lunge"] = a
    CANK = dict(CBASE, root_pos=[0, 2, 17], head=[-70, 0, 0], jaw=[14, 0, 0], rightArm=[4, 0, 150], leftArm=[4, 0, -150],
                rightForearm=[-34, 0, 0], leftForearm=[-34, 0, 0])
    out.update(ankle_set("crawler", CANK, CRAWL_BONES))
    return out


def build():
    body, speeds = body_layer()
    arms, heads = arms_layer(), head_layer()
    fullb = full_body()
    grabs = grab_side()
    reacts = reactions()
    reacts.update(directional_reactions())
    crawl = crawler()
    ground = ground_reactions()
    for w in GROUND_EXECS:
        reacts[f"react_{w}_ground_prone"] = ground[f"react_{w}_ground_prone"]
        crawl[f"react_{w}_ground"] = ground[f"react_{w}_ground_crawler"]
    walker_all = {**{f"body.{k}": v for k, v in body.items()}, **{f"arms.{k}": v for k, v in arms.items()},
                  **{f"head.{k}": v for k, v in heads.items()}, **fullb, **grabs, **reacts}
    for group in (fullb, grabs, reacts, crawl):
        for a in group.values():
            if a.loop is not True:
                a.fit_length()
    return dict(body=body, speeds=speeds, arms=arms, heads=heads, full=fullb, grab=grabs, react=reacts,
                crawler=crawl, walker_all=walker_all)


if __name__ == "__main__":
    W = build()
    write_json(os.path.join(OUT, "walker.animation.json"), anim_file("animation.holdfast.walker", W["walker_all"]))
    write_json(os.path.join(OUT, "walker_crawler.animation.json"), anim_file("animation.holdfast.crawler", W["crawler"]))
    write_json(os.path.join(OUT, "gait_speeds.json"), W["speeds"], indent=2)
    for k, v in W["speeds"].items():
        print(f"{k:12s} {v:.2f} blocks/s")
    print(len(W["walker_all"]), "walker animations,", len(W["crawler"]), "crawler animations")
