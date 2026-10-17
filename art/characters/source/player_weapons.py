"""Melee weapon families and general locomotion (decisions.md "Animation and combat feedback expansion").

Weapon families, each with a hold and two attacks matched to how the weapon is really used:
  knife      one hand, forward grip low guard; quick slash; overhand downward stab
  machete    one hand, blade up by the shoulder; diagonal cut; overhead chop
  hatchet    one hand, carried low; short overhand chop; full overhand chop
  katana     two hands, chudan (hilt at the navel, tip at throat height); diagonal kesa cut; overhead cut
  two-handed (bat, axe, hammer) on the shoulder; horizontal swing; overhead chop (axe geometry)

Layering, so these combine with locomotion, injuries and TaCZ:
  hold_<family>       keys arms, items and head only (no body lean, no legs), loops above locomotion
  swing_<family>[_heavy]  full body for its short duration (it needs the lean), on the action layer
  move_walk/sprint/crouch_walk/jump  locomotion; each has a _gun variant keying legs and body translation only,
                      so TaCZ keeps arms, items and ADS (as the injury walks).
Footstep speeds use the same rigid-leg model as the injury walks (player_more.fit_cycle): walking 4.317 blocks/s,
sprint 1.3x, crouch 0.3x.
"""
import math
import copy
from lib import Anim, S, C, TAU, pos
import player_anims as pa
import player_more as pm

P, U, STAND, Builder = pa.P, pa.U, pa.STAND, pa.Builder
NO_LEGS = ("torso", "body", "rightLeg", "leftLeg")


def hold_only(b):
    """Strip lean, body and legs so a hold layers over locomotion. Holds are authored without lean, so the solved arm
    keys are unchanged by this."""
    for part in NO_LEGS:
        b.a.tracks.pop(part, None)
    return b


def hold(name, weapon, length, keys, two=False):
    """keys: [(t, target, axis, edge)], solved without any body lean."""
    b = Builder(name, length, loop=True, weapon=weapon)
    b.pose(0.0, rightArm=[-25, 10, 10], leftArm=[-20, 0, -6], head=[4, 0, 0], rightItem=[0, 0, 0])
    for t, target, axis, edge in keys:
        b.pose(t, head=[4 + 1.5 * math.sin(TAU * t / length), 0, 0])
        if two:
            b.solve_two(t, target, axis=axis, edge=edge)
        else:
            b.solve_weapon(t, target, axis=axis, edge=edge)
    return hold_only(b)


def breathe(length, target, axis, edge=None, lift=0.6):
    """Two keys per loop: the weapon rises a little with the breath."""
    return [(0.0, target, axis, edge), (length / 2, target + P(0, lift, 0), axis, edge), (length, target, axis, edge)]


def swing(name, weapon, length, poses, two=False, sounds=(), commit=None, strike=None):
    """poses: [(t, body dict, target, axis, edge)]; body dict may include body_pos/torso/head/legs."""
    b = Builder(name, length, weapon=weapon)
    b.pose(0.0, **STAND)
    for t, body, target, axis, edge in poses:
        b.pose(t, **body)
        if two:
            b.solve_two(t, target, axis=axis, edge=edge, upright=200.0 if edge is not None else 0.0)
        else:
            b.solve_weapon(t, target, axis=axis, edge=edge, upright=200.0 if edge is not None else 0.0)
    if commit is not None:
        b.a.event(commit, "commit")
    if strike is not None:
        b.a.event(strike, "strike")
    for t, snd in sounds:
        b.a.sound(t, snd)
    return b


# Strike heights (player model px, three.js: x right, y up, z back): a standing walker's head and chest ~1.5 blocks out.
HEAD, CHEST = 30.0, 21.0


def families():
    out, rep = {}, []

    def add(b, info):
        out[b.name] = (b.a, info)
        rep.extend(b.report)

    # ---------------------------------------------------------------- knife
    add(hold("hold_knife", "knife", 3.0, breathe(3.0, P(4, 15, -7), U(0.2, 0.3, -0.93), U(0, -1, 0))), dict(weapon="knife"))
    add(swing("swing_knife", "knife", 0.45, [
        (0.08, dict(torso=[2, 16, 0], head=[4, -6, 0], body_pos=[0, -0.2, 0.4]), P(8, 20, -6), U(0.6, 0.2, -0.78), U(0, -1, 0)),
        (0.18, dict(torso=[6, -14, 0], head=[6, 4, 0], body_pos=[0, -0.5, -1.4], leftLeg=[-14, 0, 0], rightLeg=[8, 0, 0]),
         P(-2, CHEST + 3, -20), U(-0.7, 0.05, -0.7), U(0, -0.3, 0.95)),
        (0.3, dict(torso=[4, -24, 0], head=[4, 6, 0], body_pos=[0, -0.4, -1.0]), P(-10, CHEST, -12), U(-0.9, 0, -0.3), None),
        (0.45, dict(**STAND), P(4, 15, -7), U(0.2, 0.3, -0.93), U(0, -1, 0)),
    ], sounds=((0.1, "holdfast_core:swing.light"),), commit=0.08, strike=0.18), dict(weapon="knife", impacts=[]))
    add(swing("swing_knife_heavy", "knife", 0.7, [
        (0.16, dict(torso=[-6, 10, 0], head=[-4, 0, 0], body_pos=[0, 0.3, 0.6]), P(6, 38, 2), U(0.1, -0.4, -0.9), U(0, 0, 1)),
        (0.3, dict(torso=[14, -6, 0], head=[10, 0, 0], body_pos=[0, -1.0, -1.8], leftLeg=[-18, 0, 0], rightLeg=[10, 0, 0]),
         P(1, HEAD - 2, -20), U(0, -0.9, -0.4), U(0, 0.4, -0.9)),
        (0.45, dict(torso=[16, -6, 0], body_pos=[0, -1.1, -1.8]), P(1, HEAD - 6, -19), U(0, -0.95, -0.3), None),
        (0.7, dict(**STAND), P(4, 15, -7), U(0.2, 0.3, -0.93), U(0, -1, 0)),
    ], sounds=((0.2, "holdfast_core:swing.light"),), commit=0.16, strike=0.3), dict(weapon="knife", impacts=[]))

    # ---------------------------------------------------------------- machete
    add(hold("hold_machete", "machete", 3.0, breathe(3.0, P(7, 24, -3), U(0.15, 0.95, -0.25), U(0, 0.2, -0.98))), dict(weapon="machete"))
    add(swing("swing_machete", "machete", 0.6, [
        (0.12, dict(torso=[-2, 20, 0], head=[2, -8, 0], body_pos=[0, 0, 0.6]), P(10, 34, 2), U(0.3, 0.8, 0.5), U(-0.3, 0.5, -0.8)),
        (0.25, dict(torso=[10, -16, 0], head=[8, 2, 0], body_pos=[0, -0.8, -1.6], leftLeg=[-16, 0, 0], rightLeg=[10, 0, 0]),
         P(0, HEAD - 2, -22), U(-0.5, -0.3, -0.8), U(-0.6, -0.7, 0.2)),
        (0.4, dict(torso=[14, -30, 0], head=[8, 6, 0], body_pos=[0, -0.9, -1.4]), P(-12, 14, -12), U(-0.8, -0.5, -0.3), None),
        (0.6, dict(**STAND), P(7, 24, -3), U(0.15, 0.95, -0.25), U(0, 0.2, -0.98)),
    ], sounds=((0.16, "holdfast_core:swing.light"),), commit=0.12, strike=0.25), dict(weapon="machete", impacts=[]))
    add(swing("swing_machete_heavy", "machete", 0.8, [
        (0.22, dict(torso=[-10, 6, 0], head=[-4, 0, 0], body_pos=[0, 0.4, 0.8]), P(5, 44, 8), U(0.05, 0.6, 0.8), U(0, 0.8, -0.6)),
        (0.38, dict(torso=[18, 0, 0], head=[12, 0, 0], body_pos=[0, -1.4, -2.2], leftLeg=[-20, 0, 0], rightLeg=[12, 0, 0]),
         P(1, HEAD, -22), U(0, -0.5, -0.86), U(0, -0.86, 0.5)),
        (0.55, dict(torso=[22, 0, 0], body_pos=[0, -1.6, -2.0]), P(1, 12, -16), U(0, -0.9, -0.4), U(0, -0.4, 0.9)),
        (0.8, dict(**STAND), P(7, 24, -3), U(0.15, 0.95, -0.25), U(0, 0.2, -0.98)),
    ], sounds=((0.3, "holdfast_core:swing.heavy"),), commit=0.22, strike=0.38), dict(weapon="machete", impacts=[]))

    # ---------------------------------------------------------------- hatchet
    add(hold("hold_hatchet", "hatchet", 3.0, breathe(3.0, P(6, 10, -5), U(0.15, -0.15, -0.97), U(0, -1, 0))), dict(weapon="hatchet"))
    add(swing("swing_hatchet", "hatchet", 0.5, [
        (0.1, dict(torso=[-4, 12, 0], head=[0, -4, 0], body_pos=[0, 0.1, 0.5]), P(8, 36, 0), U(0.2, 0.7, 0.68), U(0, 0.7, -0.7)),
        (0.22, dict(torso=[12, -6, 0], head=[8, 0, 0], body_pos=[0, -0.9, -1.6], leftLeg=[-14, 0, 0], rightLeg=[8, 0, 0]),
         P(2, HEAD - 1, -20), U(0, -0.4, -0.92), U(0, -0.92, 0.4)),
        (0.34, dict(torso=[14, -8, 0], body_pos=[0, -1.0, -1.4]), P(3, 16, -14), U(0.1, -0.9, -0.4), U(0, -0.4, 0.9)),
        (0.5, dict(**STAND), P(6, 10, -5), U(0.15, -0.15, -0.97), U(0, -1, 0)),
    ], sounds=((0.14, "holdfast_core:swing.light"),), commit=0.1, strike=0.22), dict(weapon="hatchet", impacts=[]))
    add(swing("swing_hatchet_heavy", "hatchet", 0.75, [
        (0.2, dict(torso=[-12, 14, 0], head=[-4, -4, 0], body_pos=[0, 0.4, 0.8]), P(8, 44, 8), U(0.1, 0.5, 0.86), U(0, 0.86, -0.5)),
        (0.35, dict(torso=[20, -4, 0], head=[12, 0, 0], body_pos=[0, -1.5, -2.2], leftLeg=[-20, 0, 0], rightLeg=[12, 0, 0]),
         P(2, HEAD, -21), U(0, -0.5, -0.86), U(0, -0.86, 0.5)),
        (0.5, dict(torso=[22, -4, 0], body_pos=[0, -1.6, -2.0]), P(2, 12, -14), U(0, -0.95, -0.3), U(0, -0.3, 0.95)),
        (0.75, dict(**STAND), P(6, 10, -5), U(0.15, -0.15, -0.97), U(0, -1, 0)),
    ], sounds=((0.26, "holdfast_core:swing.heavy"),), commit=0.2, strike=0.35), dict(weapon="hatchet", impacts=[]))

    # ---------------------------------------------------------------- katana (two hands)
    # mid-blade marker 18 px along a blade rising 27 deg from hands at the navel (y 15, z -6)
    kamae = (P(1, 23, -22), U(0, 0.45, -0.89), U(0, -0.89, -0.45))
    add(hold("hold_katana", "katana", 3.2, breathe(3.2, *kamae), two=True), dict(weapon="katana"))
    add(swing("swing_katana", "katana", 0.65, [
        (0.14, dict(torso=[-4, 18, 0], head=[2, -8, 0], body_pos=[0, 0.1, 0.6]), P(8, 40, 4), U(0.35, 0.75, 0.55), U(-0.3, 0.6, -0.75)),
        (0.28, dict(torso=[12, -14, 0], head=[8, 2, 0], body_pos=[0, -1.0, -2.0], leftLeg=[-18, 0, 0], rightLeg=[10, 0, 0]),
         P(-2, HEAD - 3, -26), U(-0.45, -0.25, -0.86), U(-0.55, -0.8, 0.2)),
        (0.44, dict(torso=[16, -28, 0], head=[8, 6, 0], body_pos=[0, -1.1, -1.6]), P(-14, 10, -14), U(-0.75, -0.55, -0.35), None),
        (0.65, dict(**STAND), *kamae),
    ], two=True, sounds=((0.18, "holdfast_core:swing.heavy"),), commit=0.14, strike=0.28), dict(weapon="katana", impacts=[]))
    add(swing("swing_katana_heavy", "katana", 0.85, [
        (0.24, dict(torso=[-10, 0, 0], head=[-6, 0, 0], body_pos=[0, 0.4, 0.8]), P(0, 46, 6), U(0, 0.5, 0.86), U(0, 0.86, -0.5)),
        (0.4, dict(torso=[18, 0, 0], head=[12, 0, 0], body_pos=[0, -1.4, -2.4], leftLeg=[-20, 0, 0], rightLeg=[12, 0, 0]),
         P(0, HEAD + 1, -26), U(0, -0.4, -0.92), U(0, -0.92, 0.4)),
        (0.56, dict(torso=[22, 0, 0], body_pos=[0, -1.6, -2.2]), P(0, 12, -22), U(0, -0.8, -0.6), U(0, -0.6, 0.8)),
        (0.85, dict(**STAND), *kamae),
    ], two=True, sounds=((0.32, "holdfast_core:swing.heavy"),), commit=0.24, strike=0.4), dict(weapon="katana", impacts=[]))

    # ---------------------------------------------------------------- two-handed: axe and hammer (bat clips exist)
    shoulder = (pa.from_hands((-1, 16, -4), (0.2, 0.85, 0.48)), U(0.2, 0.85, 0.48), U(0, -0.5, 0.86))
    add(hold("hold_axe", "axe", 3.2, breathe(3.2, *shoulder), two=True), dict(weapon="axe"))
    add(swing("swing_axe", "axe", 0.75, [
        (0.12, dict(torso=[0, 28, 0], head=[4, -12, 0], body_pos=[0, -0.4, 0.5]), pa.from_hands((2, 17, -4), (0.5, 0.55, 0.66)),
         U(0.5, 0.55, 0.66), U(0, -0.8, 0.6)),
        (0.3, dict(torso=[6, -18, 0], head=[6, -4, 0], body_pos=[0, -0.9, -2.0], leftLeg=[-20, 0, 0], rightLeg=[10, 0, 0]),
         P(-2, HEAD - 2, -27), U(-0.3, 0, -0.95), U(-0.95, 0, 0.3)),
        (0.48, dict(torso=[4, -48, 0], body_pos=[0, -0.7, -1.4], head=[4, -14, 0]), P(-16, 22, -2), U(-1, 0, 0.2), None),
        (0.75, dict(**STAND), *shoulder),
    ], two=True, sounds=((0.2, "holdfast_core:swing.heavy"),), commit=0.12, strike=0.3), dict(weapon="axe", impacts=[]))
    add(swing("swing_axe_overhead", "axe", 1.0, [
        (0.3, dict(body_pos=[0, 0.4, 0.8], torso=[-12, 0, 0], head=[-10, 0, 0], leftLeg=[-10, 0, 0], rightLeg=[6, 0, 0]),
         P(0, 46, 10), U(0, 0.6, 0.8), U(0, 0.8, -0.6)),
        (0.46, dict(body_pos=[0, -1.6, -2.5], torso=[20, 0, 0], head=[12, 0, 0], leftLeg=[-18, 0, 0], rightLeg=[14, 0, 0]),
         P(0, HEAD - 1, -26), U(0, -0.3, -0.95), U(0, -0.95, 0.3)),
        (0.62, dict(body_pos=[0, -1.8, -2.4], torso=[24, 0, 0]), P(0, 10, -18), U(0, -0.85, -0.5), U(0, -0.5, 0.85)),
        (1.0, dict(**STAND), *shoulder),
    ], two=True, sounds=((0.36, "holdfast_core:swing.heavy"),), commit=0.3, strike=0.46), dict(weapon="axe", impacts=[]))
    return out, rep


# ================================================================== locomotion
for name, frac in (("move_walk", 1.0), ("move_sprint", 1.3), ("move_crouch_walk", 0.3)):
    pm.MOVE_SPEED.setdefault(name, frac)


def locomotion():
    out = {}

    def gait(name, a_leg, arm, lean, bob, length_hint):
        L, a_r, a_l = pm.fit_cycle(name, a_leg, a_leg)

        def f(u):
            p = TAU * u
            s, c = S(p), C(p)
            return dict(body_pos=[0, -bob * abs(c), 0], torso=[lean, -3 * s, 0], head=[2 - lean * 0.5, 2 * s, 0],
                        rightLeg=[-a_r * s, 0, 0], leftLeg=[a_l * s, 0, 0],
                        rightArm=[arm * s, 0, 4], leftArm=[-arm * s, 0, -4], rightItem=[0, 0, 0])
        a, info = pm.cycle(name, L, 12, f, dict(impacts=[]))
        out[name] = (a, info)

    gait("move_walk", 32, 24, 2, 0.5, 0.75)
    gait("move_sprint", 48, 46, 9, 0.9, 0.55)
    gait("move_crouch_walk", 22, 10, 0, 0.2, 1.0)

    # jump: load, push off, tuck, land, recover (played once on take-off; vanilla still owns the physics)
    a = Anim(0.8, False)
    a.pose(0.0, **STAND, rightArm=[-10, 0, 6], leftArm=[-10, 0, -6], rightItem=[0, 0, 0])
    a.pose(0.08, body_pos=[0, -1.4, 0.4], torso=[10, 0, 0], head=[-4, 0, 0], rightLeg=[-14, 0, 0], leftLeg=[-14, 0, 0],
           rightArm=[30, 0, 10], leftArm=[30, 0, -10])
    a.pose(0.2, body_pos=[0, 0.4, 0], torso=[-4, 0, 0], head=[-6, 0, 0], rightLeg=[8, 0, 0], leftLeg=[4, 0, 0],
           rightArm=[-70, 0, 14], leftArm=[-70, 0, -14])
    a.pose(0.4, body_pos=[0, 0.6, 0], torso=[6, 0, 0], head=[2, 0, 0], rightLeg=[-26, 0, 0], leftLeg=[-12, 0, 0],
           rightArm=[-40, 0, 20], leftArm=[-40, 0, -20])
    a.pose(0.6, body_pos=[0, -1.0, -0.2], torso=[12, 0, 0], head=[6, 0, 0], rightLeg=[-10, 0, 0], leftLeg=[-10, 0, 0],
           rightArm=[-20, 0, 14], leftArm=[-20, 0, -14])
    a.pose(0.8, **STAND, rightArm=[-6, 0, 4], leftArm=[-6, 0, -4], rightItem=[0, 0, 0])
    out["move_jump"] = (a, dict(impacts=[(0.6, 1.2)]))

    # TaCZ-safe variants: legs and body translation only (no lean, head, arms or items), like the injury _gun walks.
    for name in ("move_walk", "move_sprint", "move_crouch_walk", "move_jump"):
        g, info = out[name]
        gg = copy.deepcopy(g)
        for part in ("torso", "head", "rightArm", "leftArm", "rightItem", "leftItem"):
            gg.tracks.pop(part, None)
        out[name + "_gun"] = (gg, dict(info, firearm_variant=True))
    speeds = {n: round(pm.ground_speed(out[n][0].length, *pm.fit_cycle(n, a_leg, a_leg)[1:]), 3)
              for n, a_leg in (("move_walk", 32), ("move_sprint", 48), ("move_crouch_walk", 22))}
    return out, speeds


def build():
    out, rep = families()
    loco, speeds = locomotion()
    out.update(loco)
    return out, rep, speeds


if __name__ == "__main__":
    o, r, s = build()
    print(len(o), "clips;", s)
    print("\n".join(r))
