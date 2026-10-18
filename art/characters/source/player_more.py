"""Player clips added for the Holdfast milestone: ground grab (ankle), bite confirmation, ground
head-stab, free blunt swings (including a missed full charge), and injury-limited movement."""
import math
import numpy as np
from lib import Anim, S, C, TAU, pos
import fk
import player_anims as pa
import walker_anims as wa
import placement

P, U, STAND, Builder, from_hands = pa.P, pa.U, pa.STAND, pa.Builder, pa.from_hands
PAIR = placement.load()


# =========================================================== ground grab (ankle)
def ankle_anims():
    out = {}
    # right ankle held; leaning back, arms out for balance, looking down at it
    HELD = dict(body_pos=[0, -0.4, 1.0], torso=[-6, 0, 0], head=[46, 6, 0], rightLeg=[-14, 0, 0], leftLeg=[16, 0, -4],
                rightArm=[-30, 0, 34], leftArm=[-26, 0, -38], rightItem=[0, 0, 0])
    a = Anim(0.5, False)
    a.pose(0.0, **STAND, rightArm=[-10, 0, 4], leftArm=[-10, 0, -4], rightItem=[0, 0, 0])
    a.pose(0.12, body_pos=[0, -0.6, 0.4], torso=[10, 0, 0], head=[50, 0, 0], rightLeg=[-20, 0, 0],
           rightArm=[-50, 0, 30], leftArm=[-46, 0, -30])
    a.pose(0.5, **HELD)
    a.sound(0.1, "holdfast:player.gasp")
    out["grabbed_ankle_seize"] = (a, dict(impacts=[(0.12, 2.5)]))

    a = Anim(0.8, True)          # yanking the leg against the grip
    for t, k in ((0,0),(.12,.6),(.26,1),(.4,.2),(.54,-1),(.68,-.3),(.8,0)):
        a.pose(t, **dict(HELD, rightLeg=[-14 + 8 * k, 0, 0], torso=[-6 - 2 * abs(k), 0, 0], head=[46 + 3 * k, 6, 0],
                         body_pos=[0,-.4-.12*max(0,k),1+.4*k],rightArm=[-30+3*k,0,34+3*k],leftArm=[-26-2*k,0,-38-5*k]))
    a.sound(0.0, "holdfast:player.strain")
    out["grabbed_ankle_struggle"] = (a, dict(impacts=[], tremor=0.15))

    a = Anim(0.6, False)          # escape: stamp the free foot into its face
    a.pose(0.0, **HELD)
    a.pose(0.08, leftLeg=[-50, 0, 0], body_pos=[0, -0.2, 1.4], torso=[-10, 0, 0], head=[40, 0, 0])
    a.pose(0.14, leftLeg=[-24, 0, 4], body_pos=[0, -1.0, 0.2], torso=[16, 0, 0], head=[52, 0, 0])
    a.pose(0.3, leftLeg=[-10, 0, 0], rightLeg=[10, 0, 0], body_pos=[0, -0.4, 1.4], torso=[4, 0, 0])
    a.pose(0.6, **STAND, rightArm=[-20, 0, 6], leftArm=[-20, 0, -6], rightItem=[0, 0, 0])
    a.sound(0.14, "holdfast:player.exert_heavy")
    a.event(0.14, "kick")
    out["grabbed_ankle_kick"] = (a, dict(impacts=[(0.14, 3.5)]))

    a = Anim(1.0, False)
    a.pose(0.0, **HELD)
    a.pose(0.38, body_pos=[0, -1.4, 0.6], torso=[24, 0, 0], head=[56, -10, 0], rightLeg=[-6, 0, 0],
           rightArm=[-10, 0, 12], leftArm=[-30, 0, -20])
    a.pose(0.6, torso=[14, 0, 0], head=[50, -4, 0])
    a.pose(1.0, **HELD)
    a.sound(0.38, "holdfast:player.scream")
    out["grabbed_ankle_bitten"] = (a, dict(impacts=[(0.38, 4.0)]))

    a = Anim(0.6, False)
    a.pose(0.0, **HELD)
    a.pose(0.36, body_pos=[0, -0.8, 1.2], torso=[-12, 0, 0], head=[38, 10, 0], rightLeg=[-24, 0, 0])
    a.pose(0.6, **HELD)
    a.sound(0.36, "holdfast:player.gasp")
    out["grabbed_ankle_bite_blocked"] = (a, dict(impacts=[(0.36, 2.0)]))

    a = Anim(0.6, False)
    a.pose(0.0, **HELD)
    a.pose(0.25, body_pos=[0, -0.2, 1.6], torso=[-4, 0, 0], head=[30, 0, 0], rightLeg=[6, 0, 0], leftLeg=[4, 0, 0])
    a.pose(0.6, **STAND, rightArm=[-12, 0, 4], leftArm=[-12, 0, -4], rightItem=[0, 0, 0])
    out["grabbed_ankle_released"] = (a, dict(impacts=[]))
    return out


# =========================================================== bite confirmation
def bite_confirm():
    """After a confirmed penetrating bite (server decides), the survivor checks the wound. Region-specific."""
    out = {}
    a = Anim(1.6, False)          # neck: hand to the neck, then look at the blood on it
    a.pose(0.0, **STAND, rightArm=[-12, 0, 4], leftArm=[-12, 0, -4], rightItem=[0, 0, 0])
    a.pose(0.3, leftArm=[-150, 10, 30], head=[0, 24, -8], torso=[-4, 6, 0])
    a.pose(0.7, leftArm=[-146, 10, 26], head=[2, 20, -6])
    a.pose(1.0, leftArm=[-86, 26, 8], head=[30, -16, 0], torso=[6, 0, 0])
    a.pose(1.35, leftArm=[-84, 24, 8], head=[34, -18, 0])
    a.pose(1.6, **STAND, rightArm=[-12, 0, 4], leftArm=[-30, 10, -4], rightItem=[0, 0, 0])
    a.sound(0.3, "holdfast:player.breath_ragged")
    out["bite_confirm_neck"] = (a, dict(impacts=[(0.05, 1.5)]))

    a = Anim(1.6, False)          # forearm: turn it over and stare at the teeth marks
    a.pose(0.0, **STAND, rightArm=[-12, 0, 4], leftArm=[-12, 0, -4], rightItem=[0, 0, 0])
    a.pose(0.35, leftArm=[-72, 30, 0], rightArm=[-56, -36, 0], head=[40, -14, 0], torso=[8, 0, 0])
    a.pose(0.9, leftArm=[-78, 26, -10], rightArm=[-50, -30, 0], head=[44, -12, 0])
    a.pose(1.3, leftArm=[-74, 28, 0], rightArm=[-40, -24, 0], head=[38, -10, 0])
    a.pose(1.6, **STAND, rightArm=[-12, 0, 4], leftArm=[-30, 10, -4], rightItem=[0, 0, 0])
    a.sound(0.35, "holdfast:player.breath_ragged")
    out["bite_confirm_forearm"] = (a, dict(impacts=[(0.05, 1.5)]))

    a = Anim(1.6, False)          # leg: bend over the calf
    a.pose(0.0, **STAND, rightArm=[-12, 0, 4], leftArm=[-12, 0, -4], rightItem=[0, 0, 0])
    a.pose(0.4, body_pos=[0, -2.5, 0.8], torso=[38, 0, 0], head=[40, -6, 0], rightLeg=[-20, 0, 0],
           rightArm=[-20, 0, 8], leftArm=[-30, 0, -6])
    a.pose(1.1, body_pos=[0, -2.6, 0.8], torso=[40, 0, 0], head=[44, -8, 0], rightArm=[-16, 0, 6])
    a.pose(1.6, **STAND, rightArm=[-12, 0, 4], leftArm=[-12, 0, -4], rightItem=[0, 0, 0])
    a.sound(0.4, "holdfast:player.breath_ragged")
    out["bite_confirm_leg"] = (a, dict(impacts=[(0.05, 1.5)]))
    return out


# =========================================================== ground head-stab
def knife_ground():
    e = wa.EXECS["knife_ground"]
    W = pa.W
    b = Builder("exec_knife_ground", e["length"], weapon="knife", react=W["crawler"]["react_knife_ground"],
                dist=PAIR["knife_ground_crawler"]["dist"], rig=pa.CR)
    I, R = e["impact"], e["release"]
    # Shifted 4 px to the left (bedrock +x) so the right hand sits over the head; centred, the rigid right arm
    # cut through the chest to reach it (Andrew, in game 2026-10-16).
    CROUCH = dict(body_pos=[4, -3.5, 0.6], torso=[34, 0, 0], head=[36, 0, 0], rightLeg=[-6, 0, 0], leftLeg=[-22, 0, 0])
    b.pose(0.0, **STAND, rightArm=[-20, 10, 10], rightItem=[0, 0, 0], leftArm=[-20, 0, -6])
    b.pose(0.18, body_pos=[4, -2.0, 0.2], torso=[20, 6, 0], head=[30, 0, 0], leftLeg=[-14, 0, 0])
    Hm, _ = b.walker(0.18)
    up0 = -pa.hd(Hm, (0, -1, 0))
    b.solve_weapon(0.18, pa.hp(Hm, (0, 8, 0)) + up0 * 16 + P(3, 0, 6), axis=-up0)       # knife raised clear
    # knife raised, blade pointing down, free hand reaching for its back
    b.pose(e["commit"], **CROUCH)
    Hm, Wm = b.walker(e["commit"])
    down = pa.hd(Hm, (0, -1, 0))
    b.solve_weapon(e["commit"], pa.hp(Hm, (0, 8, 0)) - down * 9, axis=down)
    # The free arm cannot reach a ground brace from this rigid-arm crouch (9-12 px short). Counterbalance at
    # the player's side instead of leaving a hand suspended in front of an unreachable support surface.
    b.pose(e['commit'],leftArm=[-28,0,-8])
    b.pose(I - 0.07, body_pos=[4, -3.9, -0.2], torso=[38, -2, 0], head=[40, 0, 0])
    Hm, Wm = b.walker(I - 0.07)
    down = pa.hd(Hm, (0, -1, 0))
    b.solve_weapon(I - 0.07, pa.hp(Hm, (0, 8, 0)) - down * 4, axis=down)                 # tip just above the skull
    # With rigid arms a deep crouch puts the desired fist only 4 px from a 9.55 px arm's shoulder. A shallower
    # forward lean lets the arm extend naturally and the blade follow the skull axis instead of folding the wrist.
    b.pose(I, body_pos=[4, -2.3, -0.6], torso=[20, -4, 0], head=[40, 0, 0])
    Hm, Wm = b.walker(I)
    b.solve_weapon(I, pa.hp(Hm, (0, 2.5, 0)), axis=pa.hd(Hm, (0, -1, 0)), reg=0.00002, w_axis=200)
    b.pose(I,leftArm=[-18,0,-6])
    b.pose(0.66, body_pos=[4, -2.3, -0.6], torso=[20, -4, 0])
    Hm, Wm = b.walker(0.66)
    b.solve_weapon(0.66, pa.hp(Hm, (0, 2.5, 0)), axis=pa.hd(Hm, (0, -1, 0)), reg=0.00002, w_axis=200)
    b.pose(0.8, body_pos=[4, -2.3, -0.6], torso=[20, -4, 0])
    Hm, Wm = b.walker(0.8)
    b.solve_weapon(0.8, pa.hp(Hm, (0, 2.5, 0)), axis=pa.hd(Hm, (0, -1, 0)), reg=0.00002, w_axis=200)
    b.pose(R, body_pos=[4, -3.0, 0], torso=[26, 6, 0], head=[34, 0, 0])
    Hm, Wm = b.walker(R)
    up = -pa.hd(Hm, (0, -1, 0))
    b.solve_weapon(R, pa.hp(Hm, (0, 8, 0)) + up * 9, axis=-up)
    b.pose(e["length"], **STAND, rightArm=[-18, 10, 10], rightItem=[0, 0, 0], leftArm=[-10, 0, -4])
    b.a.event(e["commit"], "commit").event(I, "kill").event(R, "release")
    b.a.sound(0.15, "holdfast:player.exert_light").sound(I, "holdfast:player.exert_heavy").sound(0.8, "holdfast:knife.pull")
    return b, dict(impacts=[(I, 3.0)], weapon="knife")


# Overhead strike on a head on the ground, one template for every weapon with an overhead swing (Andrew,
# 2026-10-16). A weapon is a grip (one or two hands), whether it has an edge (held vertical, sinks in, is worked
# free) or is blunt (rebounds), and its timing in walker_anims.EXECS["<weapon>_ground"]. Solved against the
# crawler; the prone walker is placed to match (placement.py). New overhead weapons only need an OVERHEAD entry.
CHOP_AXIS, CHOP_EDGE = U(0, -0.6, -0.8), U(0, -0.8, 0.6)
OVERHEAD = {
    # two_handed, edged, overhead hand target, impact point in the skull, follow-through point, sounds
    "axe": dict(two=True, edge=True, up=P(0, 46, 10), hit=(0, 7, 0), follow=(0, 6, 0), shift=0,
                sounds=((0.15, "holdfast:player.exert_heavy"), (0.8, "holdfast:player.exert_strain"), (1.15, "holdfast:player.breath_heavy"))),
    "bat": dict(two=True, edge=False, up=P(0, 44, 8), hit=(0, 7.5, 0), follow=None, shift=0,
                sounds=((0.06, "holdfast:player.exert_heavy"), (0.42, "holdfast:player.exert_heavy"), (0.9, "holdfast:player.breath_heavy"))),
    "katana": dict(two=True, edge=True, up=P(0, 46, 10), hit=(0, 3.2, 0), follow=(0, 2.2, 0), shift=0, sink=.8,
                   sounds=((0.12, "holdfast:player.exert_light"), (0.5, "holdfast:player.exert_heavy"), (0.86, "holdfast:player.exert_strain"))),
    "machete": dict(two=False, edge=True, up=P(8, 38, 4), hit=(0, 7, 0), follow=(0, 5.8, 0), shift=2,
                    sounds=((0.08, "holdfast:player.exert_light"), (0.38, "holdfast:player.exert_heavy"), (0.64, "holdfast:player.exert_strain"))),
}


def overhead_ground(w):
    o, e = OVERHEAD[w], wa.EXECS[f"{w}_ground"]
    b = Builder(f"exec_{w}_ground", e["length"], weapon=w, react=pa.W["crawler"][f"react_{w}_ground"],
                dist=PAIR[f"{w}_ground_crawler"]["dist"], rig=pa.CR)
    I, R, L = e["impact"], e["release"], e["length"]
    up = 300.0 if o["edge"] else 0.0
    sx = o["shift"]                       # one-handed: body shifted left so the rigid arm stays clear of the chest

    def strike(t, target, axis, edge=None):
        edge = edge if o["edge"] else None
        if o["two"]:
            b.solve_two(t, target, axis=axis, edge=edge, upright=up, reg=0.00002 if w == "katana" else 0.002)
        else:
            b.solve_weapon(t, target, axis=axis, edge=edge, upright=up)

    def ready(t):
        if w == "bat":
            pa.bat_ready(b, t)
        elif o["two"]:
            b.solve_two(t, P(-3, 18, -12), axis=U(-0.3, 0.5, -0.8), edge=U(0, -0.6, -0.8))

    # ready stance
    if o["two"]:
        b.pose(0.0, **STAND, rightArm=[-30, 10, 10], rightItem=[0, 0, 0], leftArm=[-40, -20, 0])
    else:
        b.pose(0.0, **STAND, rightArm=[-20, 10, 10], rightItem=[0, 0, 0], leftArm=[-20, 0, -6])
    ready(0.0)
    # raise it overhead, eyes on the head at its feet
    b.pose(e["commit"], body_pos=[sx, 0.4 - 1.8 * (not o["two"]), 0.8], torso=[-10 if o["two"] else 12, 0 if o["two"] else 12, 0],
           head=[24, 0, 0], leftLeg=[-12, 0, 0], rightLeg=[8, 0, 0])
    strike(e["commit"], o["up"], U(0, 0.6, 0.8) if o["two"] else U(0.2, 0.7, 0.7), U(0, 0.8, -0.6))
    # coming down
    b.pose(I - 0.07, body_pos=[sx, -1.0 - 1.4 * (not o["two"]), -0.6], torso=[12 if o["two"] else 24, 0, 0], head=[30, 0, 0])
    Hm, _ = b.walker(I - 0.07)
    strike(I - 0.07, pa.hp(Hm, (0, 8, 0)) + P(0, 9, 5), U(0, 0.1, -1), U(0, -1, 0.1))
    # impact, whole body into it
    b.pose(I, body_pos=[sx, -2.6-o.get('sink',0), -1.6], torso=[30 if o["two"] else 34, 0 if o["two"] else -6, 0], head=[32, 0, 0],
           leftLeg=[-22, 0, 0], rightLeg=[14 if o["two"] else -6, 0, 0], **({} if o["two"] else {"leftArm": [-40, 0, -14]}))
    Hm, _ = b.walker(I)
    strike(I, pa.hp(Hm, o["hit"]), CHOP_AXIS, CHOP_EDGE)
    t1 = round(I + 0.12, 2)
    b.pose(t1, body_pos=[sx, (-2.7 if o["edge"] else -2.0)-o.get('sink',0), -1.6 if o["edge"] else -1.0], torso=[31 if o["edge"] else 24, 0, 0])
    Hm, _ = b.walker(t1)
    if o["edge"]:                         # sink in, then work it free
        strike(t1, pa.hp(Hm, o["follow"]), CHOP_AXIS, CHOP_EDGE)
        t2 = round((t1 + R) / 2, 2)
        b.pose(t2, body_pos=[sx, -2.0, -0.8], torso=[20, 6, 0], head=[26, 4, 0])
        Hm, _ = b.walker(t2)
        strike(t2, pa.hp(Hm, (0, 6.5, 0)), U(0, -0.4, -0.9), U(0, -0.9, 0.4))
    else:                                 # blunt: rebound off the skull
        strike(t1, pa.hp(Hm, (0, 8, 0)) + P(0, 3, 2), U(0, -0.4, -0.9))
    b.pose(R, body_pos=[sx / 2, -1.0, -0.2], torso=[10, 0, 0], head=[16, 0, 0])
    Hm, _ = b.walker(R)
    strike(R, pa.hp(Hm, (0, 8, 0)) + P(0, 8, 4), U(0, 0.2, -1), U(0, -1, 0.2))
    if o["two"]:
        b.pose(L, **STAND)
    else:
        b.pose(L, **STAND, rightArm=[-20, 10, 10], rightItem=[0, 0, 0], leftArm=[-15, 0, -4])
    ready(L)
    b.a.event(e["commit"], "commit").event(I, "kill").event(R, "release")
    for t, snd in o["sounds"]:
        b.a.sound(t, snd)
    impacts = [(I, 6.0 if w == "axe" else 5.0 if w == "bat" else 3.5)] + ([(t2, 2.0)] if o["edge"] else [])
    return b, dict(impacts=impacts, weapon=w)


# =========================================================== free blunt swings
def bat_free():
    out = {}
    b = Builder("bat_swing_quick", 0.65, weapon="bat")
    b.pose(0.0, **STAND)
    b.solve_two(0.0, from_hands((0, 15, -5), (0.25, 0.85, 0.45)), axis=U(0.25, 0.85, 0.45))
    b.pose(0.08, torso=[0, 22, 0], body_pos=[0, -0.4, 0.5], head=[4, -10, 0])
    b.solve_two(0.08, from_hands((1.5, 16.5, -5), (0.5, 0.55, 0.65)), axis=U(0.5, 0.55, 0.65))
    b.pose(0.22, torso=[6, -18, 0], body_pos=[0, -0.8, -2], head=[6, -4, 0], leftLeg=[-20, 0, 0], rightLeg=[10, 0, 0])
    b.solve_two(0.22, P(-2, 26, -24), axis=U(-0.2, 0, -1))
    b.pose(0.38, torso=[4, -50, 0], body_pos=[0, -0.6, -1.5], head=[4, -16, 0])
    b.solve_two(0.38, P(-16, 22, -2), axis=U(-1, 0, 0.3))
    b.pose(0.65, **STAND)
    b.solve_two(0.65, from_hands((0, 15, -5), (0.25, 0.85, 0.45)), axis=U(0.25, 0.85, 0.45))
    b.a.event(0.12, "commit").event(0.22, "strike")
    b.a.sound(0.06, "holdfast:player.exert_light").sound(0.2, "holdfast:swing.whoosh")
    out["bat_swing_quick"] = (b.a, dict(impacts=[], weapon="bat"))
    _ = b.report

    # full charge released into empty air: overswing, stumble, long exposed recovery (§3 misses expose the player)
    b = Builder("bat_charged_miss", 1.5, weapon="bat")
    b.pose(0.0, body_pos=[0, -0.6, 0.6], torso=[-2, 35, 0], head=[2, -14, 0], rightLeg=[10, 0, 0], leftLeg=[-8, 0, 0])
    b.solve_two(0.0, from_hands((1, 16, -5.5), (0.45, 0.6, 0.65)), axis=U(0.45, 0.6, 0.65))
    b.pose(0.12, body_pos=[0, -1.0, -2], torso=[0, 20, 0], head=[4, -6, 0], leftLeg=[-26, 0, 0], rightLeg=[14, 0, 0])
    b.solve_two(0.12, from_hands((1.5, 16, -6), (0.6, 0.3, -0.75)), axis=U(0.6, 0.3, -0.75))
    b.pose(0.22, body_pos=[0, -1.4, -3], torso=[10, -30, 0], head=[8, -8, 0])
    b.solve_two(0.22, P(-4, 25, -24), axis=U(-0.3, 0, -1))
    b.pose(0.45, body_pos=[0, -1.6, -3.6], torso=[14, -80, 0], head=[10, -30, 0], rightLeg=[24, 0, 0], leftLeg=[-30, 0, 4])
    b.solve_two(0.45, P(-12, 14, 10), axis=U(-0.6, -0.3, 0.75))
    b.pose(0.75, body_pos=[0, -2.0, -2.8], torso=[24, -60, 6], head=[18, -24, 0], leftLeg=[-20, 0, 8])     # stumble
    b.solve_two(0.75, P(-8, 4, -6), axis=U(-0.4, -0.7, -0.6))
    b.pose(1.05, body_pos=[0, -1.6, -1.6], torso=[18, -30, 0], head=[16, -12, 0])
    b.solve_two(1.05, P(-4, 3, -12), axis=U(-0.2, -0.7, -0.7))
    b.pose(1.5, **STAND)
    b.solve_two(1.5, from_hands((0, 15, -5), (0.25, 0.85, 0.45)), axis=U(0.25, 0.85, 0.45))
    b.a.event(0.06, "commit").event(0.22, "strike").event(1.5, "recovered")
    b.a.sound(0.02, "holdfast:player.exert_heavy").sound(0.2, "holdfast:swing.whoosh_heavy").sound(0.75, "holdfast:player.stumble")
    out["bat_charged_miss"] = (b.a, dict(impacts=[(0.45, 2.0)], weapon="bat", geodesic_items=True))
    return out


# =========================================================== injury-limited movement
LEG = 12.0


def ground_speed(L, a_r, a_l):
    """Blocks per second at 1x for a rigid-leg cycle with thigh amplitudes a_r, a_l (degrees)."""
    return 2 * LEG * (math.sin(math.radians(a_r)) + math.sin(math.radians(a_l))) / L / 16.0


WALK = 4.317                    # vanilla walking speed, blocks/s
# Movement speed as a fraction of normal walking (decisions.md; leg loss from design.md). The server owns these
# (S13); the clips are authored so their footsteps match them at 1x playback.
MOVE_SPEED = {"move_limp": 0.85, "move_limp_severe": 0.55, "move_arm_injured": 0.95, "move_prosthesis": 0.80,
              "move_crutch_medical": 0.60, "move_crutch_improvised": 0.45, "move_crawl_legless": 0.20}


def fit_cycle(name, a_r, a_l):
    """(length, a_r, a_l) for a rigid-leg cycle that covers ground at its target speed. The length is a whole
    number of ticks (Player Animator loops on ticks), and the thigh amplitudes are scaled to make up the rest."""
    v = MOVE_SPEED[name] * WALK
    L = max(0.05, round(ground_speed(1.0, a_r, a_l) / v * 20) / 20)
    lo, hi = 0.5, 1.5
    for _ in range(60):
        k = (lo + hi) / 2
        lo, hi = (k, hi) if ground_speed(L, k * a_r, k * a_l) < v else (lo, k)
    k = (lo + hi) / 2
    return L, round(k * a_r, 2), round(k * a_l, 2)


def cycle(name, L, n, fn, info):
    a = Anim(L, True)
    for i in range(n + 1):
        u = i / n
        a.pose(L * u, **fn(u))
    return a, info


def movement():
    out, speeds = {}, {}

    # Each cycle's length comes from its target speed (MOVE_SPEED) and thigh amplitudes, so feet plant at the
    # speed the server will move the player. Andrew, 2026-10-16: slow slightly or a lot by severity.

    # leg injury (right): short, stiff stance on the bad leg, lurch over the good one. Two severities.
    def limp(a_bad, a_good, lurch):
        def f(u):
            p = math.pi * u / 0.6 if u < 0.6 else math.pi + math.pi * (u - 0.6) / 0.4
            s, c = S(p), C(p)
            return dict(body_pos=[0.5 * lurch * s, -0.4 * lurch * pos(-s) * 2.2, 0],
                        torso=[6 + 3 * lurch * pos(-s), 3 * s, lurch * (7 * pos(s) - 5 * pos(-s))],
                        head=[6 + 3 * lurch * pos(-s), 2 * s, -3 * lurch * s], rightLeg=[a_bad * c, 0, 2 + 5 * pos(s)],
                        leftLeg=[-a_good * c, 0, -1], rightArm=[-8 - 10 * s, 0, 8 + 6 * lurch * pos(-s)],
                        leftArm=[-6 + 12 * s, 0, -6 - 8 * lurch * pos(s)], rightItem=[0, 0, 0])
        return f
    for name, a_bad, a_good, lurch in (("move_limp", 34, 45, 0.7), ("move_limp_severe", 16, 40, 1.4)):
        L, a_bad, a_good = fit_cycle(name, a_bad, a_good)
        out[name] = cycle(name, L, 12, limp(a_bad, a_good, lurch), dict(impacts=[]))
        speeds[name] = ground_speed(L, a_bad, a_good)

    # arm injury (left): arm cradled across the belly, guarded but full-length steps
    def arm(a):
        def f(u):
            p = TAU * u
            s, c = S(p), C(p)
            return dict(body_pos=[0, -0.5 * s * s, 0], torso=[8, -4 * c, 2 * s], head=[8, 2 * c, 0],
                        rightLeg=[-a * s, 0, 0], leftLeg=[a * s, 0, 0], rightArm=[24 * s - 6, 0, 6],
                        leftArm=[-50 + 2 * s, 34, 6], rightItem=[0, 0, 0])
        return f
    L, a, _ = fit_cycle("move_arm_injured", 45, 45)
    out["move_arm_injured"] = cycle("move_arm_injured", L, 12, arm(a), dict(impacts=[]))
    speeds["move_arm_injured"] = ground_speed(L, a, a)

    # fitted prosthesis (right leg): nearly normal, stiff knee swings out a little, slight hitch
    def prosth(a_r, a_l):
        def f(u):
            p = TAU * u
            s, c = S(p), C(p)
            return dict(body_pos=[0.3 * s, -0.6 * s * s - 0.4 * pos(-s), 0], torso=[3, -5 * c, 3 * s],
                        head=[4, 2 * c, -1 * s], rightLeg=[-a_r * s, 0, 2 + 6 * pos(s)], leftLeg=[a_l * s, 0, -1],
                        rightArm=[24 * s, 0, 4], leftArm=[-24 * s, 0, -4], rightItem=[0, 0, 0])
        return f
    L, a_r, a_l = fit_cycle("move_prosthesis", 40, 45)
    out["move_prosthesis"] = cycle("move_prosthesis", L, 12, prosth(a_r, a_l), dict(impacts=[]))
    speeds["move_prosthesis"] = ground_speed(L, a_r, a_l)

    # crutches, right leg not bearing weight: plant both crutches ahead, swing through on the left leg
    def crutch(effort, a_l):
        def f(u):
            p = TAU * u
            s, c = S(p), C(p)
            plant = 0.5 - 0.5 * c            # 0 at start (crutches back) -> 1 mid-cycle (crutches ahead)
            return dict(body_pos=[0, -1.2 * effort * S(math.pi * u) ** 2, 0.6 * c],
                        torso=[10 + 8 * effort * plant, 0, 0], head=[10 + 4 * effort * plant, 0, 0],
                        rightArm=[-6 - 34 * plant, 0, 10], leftArm=[-6 - 34 * plant, 0, -10],
                        rightLeg=[32, 0, 2], leftLeg=[-a_l * s, 0, -1],
                        rightItem=[0, 0, 0], leftItem=[0, 0, 0])
        return f
    for name, effort in (("move_crutch_improvised", 1.4), ("move_crutch_medical", 0.8)):
        L, _, a_l = fit_cycle(name, 0, 55)
        out[name] = cycle(name, L, 12, crutch(effort, a_l), dict(impacts=[], weapon="crutch"))
        speeds[name] = ground_speed(L, 0, a_l)

    # leg loss before any crutch: dragging along the ground. Played inside the vanilla crawling pose
    # (Pose.SWIMMING on land), so it keys arms and legs only and never the body bone.
    def crawl(u):
        def stroke(uu):
            uu %= 1.0
            if uu < 0.5:
                k = uu / 0.5
                return 170 - 100 * (0.5 - 0.5 * C(math.pi * k)), 0.0
            k = (uu - 0.5) / 0.5
            return 70 + 100 * (0.5 - 0.5 * C(math.pi * k)), 16 * S(math.pi * k)
        rz, rl = stroke(u)
        lz, ll = stroke(u + 0.5)
        return dict(rightArm=[rl, 0, rz], leftArm=[ll, 0, -lz], rightLeg=[24, 0, 6], leftLeg=[4 * S(TAU * u), 0, -4],
                    head=[-40 + 3 * S(2 * TAU * u), 4 * S(TAU * u), 0], rightItem=[0, 0, 0])
    out["move_crawl_legless"] = cycle("move_crawl_legless", 2.2, 12, crawl, dict(impacts=[], vanilla_pose="swimming"))
    speeds["move_crawl_legless"] = round(0.2 * 4.317, 3)         # set by the server (20% of walk); the arms do not drive it
    return out, {k: round(v, 3) for k, v in speeds.items()}


def build():
    out = {}
    out.update(ankle_anims())
    out.update(bite_confirm())
    b, info = knife_ground()
    out["exec_knife_ground"] = (b.a, info)
    reports = list(b.report)
    for w in OVERHEAD:
        g, ginfo = overhead_ground(w)
        out[g.name] = (g.a, ginfo)
        reports += g.report
    out.update(bat_free())
    mv, speeds = movement()
    out.update(mv)
    return out, reports, speeds
