"""Melee weapon families and general locomotion (decisions.md "Animation and combat feedback expansion").

Weapon families, each with a hold and two attacks matched to how the weapon is really used:
  knife      one hand, forward grip low guard; quick slash; overhand downward stab
  machete    one hand, relaxed blade-down carry; diagonal cut; overhead chop
  hatchet    one hand, carried low; short overhand chop; full overhand chop
  katana     two hands, chudan (hilt at the navel, tip at throat height); diagonal kesa cut; overhead cut
  two-handed (bat, axe, hammer) low diagonal carry; horizontal swing; overhead chop

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
from functools import cache
import numpy as np
import fk
from lib import Anim, S, C, TAU, pos, bb_euler
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
    # Andrew still sees a torso twitch on clicks after vanilla's extra swing was suppressed. These clips also
    # authored 30-78 degree side-to-side torso turns. Keep the forward weight shift, but solve the hands against
    # a stable torso yaw/roll so removing that turn cannot drag the weapon off its intended strike path.
    poses = [(t, dict(body, torso=[body['torso'][0],0,0]) if 'torso' in body else dict(body), target, axis, edge)
             for t,body,target,axis,edge in poses]
    b = Builder(name, length, weapon=weapon)
    b.pose(0.0, **STAND)
    carry, _ = relaxed_hold(weapon)
    for part, channels in carry.tracks.items():
        for channel in channels:
            b.a.key(0, part, **({"rot": carry.value(part, channel, 0)} if channel == "rotation" else {"pos": carry.value(part, channel, 0)}))
    for part in b.prev:
        value = carry.value(part, "rotation", 0)
        if value is not None:
            b.prev[part] = np.array(value, float)
    # The hips start loading before the hands lift. This keeps the attack from looking like an arm-only swipe.
    first_t, first_pose, *_ = poses[0]
    b.pose(round(first_t * 0.5, 3), **{k: [v * 0.65 for v in values] for k, values in first_pose.items()})
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
    # Set the body before contact and let the arm follow; preserve the authored contact time and weapon target.
    if strike is not None:
        body = next(body for t, body, *_ in poses if abs(t - strike) < 0.001)
        b.pose(round(strike - 0.045, 3), **body)
    for part, channels in carry.tracks.items():
        for channel in channels:
            value = carry.value(part, channel, 0)
            b.a.key(length, part, **({"rot": value} if channel == "rotation" else {"pos": value}))
    for channels in b.a.tracks.values():
        keys = channels.get("rotation", {})
        times = sorted(keys)
        if times:
            values = np.rad2deg(np.unwrap(np.deg2rad([keys[t] for t in times]), axis=0))
            for t, value in zip(times, values):
                keys[t] = [round(float(v), 2) for v in value]
    return b


# Carries come in two stances (Andrew 2026-10-17: swords look permanently combat-ready, the axe T-posed, knife and
# hatchet holds unnatural). REST is the relaxed out-of-combat carry; READY is what swings start from and return to,
# shown during combat and for a few seconds after it (client timer, docs/animation.md "Rest and ready stances").
# Each spec: arm(breath, shift) -> right arm rotation; the weapon's grip-to-head axis and edge direction (rig space:
# x right, y up, z back); or solve=True with a head target for a two-hand carry solved like a swing key.
def _hang(b, s):
    return [6 + 1.6 * b, 2 + s, 13 + 0.7 * b]


def _rest(b, s):
    """Rest arm, close to vanilla's item-holding arm (Andrew 2026-10-17: the rest arms flared out too much)."""
    return [-10 + 1.0 * b, 1 + 0.5 * s, 3 + 0.4 * b]


STANCES = {
    # knife: relaxed, wrist neutral, blade forward and down along the thigh; ready: forward low guard
    ("knife", "rest"): dict(arm=_rest, axis=(0.15, -0.6, -0.79), edge=(0.0, -1.0, 0.0)),
    # machete (one carry for both stances): the old blade-down hang put its tip ~1 px into the ground
    ("machete", "ready"): dict(arm=_hang, axis=(0.15, -0.6, -0.79), edge=(0.0, -1.0, 0.0)),
    ("knife", "ready"): dict(arm=lambda b, s: [-26 + 1.2 * b, 6 + 0.6 * s, 10 + 0.5 * b], axis=(0.2, 0.3, -0.93), edge=(0.0, -1.0, 0.0)),
    # hatchet: head forward and down at the side; ready: head forward and a little up, bit down (the rest pose its
    # accepted swing was authored around)
    ("hatchet", "rest"): dict(arm=_rest, axis=(0.12, -0.45, -0.88), edge=(0.0, -1.0, 0.0)),
    ("hatchet", "ready"): dict(arm=_hang, axis=(0.12, 0.3, -0.95), edge=(0.0, -1.0, 0.0)),
    # katana rest: lowered one-handed, blade ~20 degrees below level so its 26 px stay off the ground, edge down
    ("katana", "rest"): dict(arm=_rest, axis=(0.12, -0.34, -0.93), edge=(0.0, -1.0, 0.0)),
    # Andrew clarified review 3: match the hatchet's resting head/blade direction. Keep the accepted ready unchanged.
    ("axe", "rest"): dict(arm=_rest, axis=(0.12, -0.45, -0.88), edge=(0.0, -1.0, 0.0), grip_offset=5.75),
    # fire axe ready ("port arms"): both hands, handle across the body, head up by the left shoulder; solved with
    # Builder.solve_two so the left hand finds the handle (replaces the low arms-forward carry read as a T-pose).
    # The katana ready stays the two-hand chudan below.
    ("axe", "ready"): dict(solve=True, target=(-6.0, 26.0, -11.5), axis=(-0.45, 0.75, -0.48), edge=(0.0, 0.0, -1.0), two=True),
}
REST_FAMILIES = ("knife", "hatchet", "katana", "axe")


def _frame(axis, edge):
    axis = U(*axis) if not isinstance(axis, np.ndarray) else axis / np.linalg.norm(axis)
    edge = np.array(edge, float)
    edge = edge - (edge @ axis) * axis
    edge /= np.linalg.norm(edge)
    z, y = -axis, -edge
    return np.column_stack((np.cross(y, z), y, z))


def relaxed_hold(weapon):
    """The READY carry: what swings start from and return to (kept under its old name for callers)."""
    return carry(weapon, "ready")


@cache
def carry(weapon, stance="ready"):
    """Construct a carry from the shoulder and fist, then orient the weapon around that fixed grip.

    A low blade does not need an IK target that drags the elbow through the chest. Two-hand carries solve only
    the support hand to a point on the actual handle. Shoulder protraction gives rigid vanilla arms clearance.
    """
    a = Anim(3.2, False)  # Do not wrap t=length to the unfinished first key while constructing the loop.
    spec = STANCES.get((weapon, stance))
    two = spec.get("two", False) if spec else weapon in ("axe", "bat", "katana")
    for i in range(9):
        t = i * 0.4
        phase = TAU * t / a.length
        breath, shift = S(phase), S(phase * 2)
        right = [-62 + 1.2 * breath, -31 + 0.8 * shift, 14 + 0.6 * breath] if two else [6 + 1.6 * breath, 2 + shift, 13 + 0.7 * breath]
        if weapon == "katana":
            right = [-68 + 1.2 * breath, -45 + .8 * shift, 14 + .6 * breath]
        if spec and "arm" in spec:
            right = spec["arm"](breath, shift)
        rest = stance == "rest"                      # vanilla-like arms: no shoulder shift, the free arm hangs
        a.pose(t, rightArm=right, leftArm=[-2 + 1.1 * breath, 0, -3 - 0.4 * breath] if rest else [-5 + 1.1 * breath, 0, -13 - 0.6 * breath],
               rightArm_pos=[0, 0.08 * breath, 0] if rest else [-1, 0.08 * breath, -2 if two else -0.2],
               leftArm_pos=[0, -0.08 * breath, 0] if rest else [1, -0.08 * breath, -2 if two else -0.2],
               head=[1.2 * breath, 1.1 * shift, 0.5 * breath], rightItem=[0, 0, 0])
        W = fk.worlds(pa.SR, [a], t)
        if spec and spec.get("solve"):
            continue                                   # solved below with both hands
        elif spec:
            frame = _frame(spec["axis"], spec["edge"])
        else:
            axis = U(0.15, -0.97, -0.18) if not two else U(0.0, 0.42, -0.91)
            if weapon in ("axe", "bat"):
                axis = U(0.32, -0.65, -0.69)  # low diagonal carry, head clear of the legs
            z = -axis
            hint = U(0, 0, 1) if not two else U(0, 1, 0)
            x = np.cross(hint, z); x /= np.linalg.norm(x)
            frame = np.column_stack((x, np.cross(z, x), z))
        wrist = bb_euler(W["rightItem"][:3, :3].T @ frame)
        a.key(t, "rightItem", rot=wrist)
        if spec and spec.get('grip_offset'):
            # The fire axe handle runs from local z +3 to -14.5: its midpoint is -5.75. Move the item along
            # its own handle so that point sits at the unchanged fist. Rest only; ready/swings keep their grip.
            offset=W['rightArm'][:3,:3].T @ frame @ np.array([0.,0.,spec['grip_offset']])
            a.key(t,'rightItem',pos=[-offset[0],offset[1],offset[2]])
    # Same grip on every key; support hand can slide only within the physical handle.
    report = []
    if spec and spec.get("solve"):
        b = Builder(("hold_" if stance == "ready" else "rest_") + weapon, a.length, loop=True, weapon=weapon)
        b.a = a
        b.prev["rightArm"], b.prev["leftArm"] = np.array([-40.0, 20, 0]), np.array([-60.0, -20, 0])
        for i in range(9):
            t = i * .4
            lift = S(TAU * t / a.length) * 0.4
            b.solve_two(t, np.array(spec["target"], float) + [0, lift, 0], axis=U(*spec["axis"]), edge=U(*spec["edge"]))
        report = b.report
    elif two:
        b = Builder(("hold_" if stance == "ready" else "rest_") + weapon, a.length, loop=True, weapon=weapon)
        b.a = a
        for i in range(9):
            t = i * .4
            W = fk.worlds(pa.SR, [a], t)
            grip = (W["rightItem"] @ np.array([0, 0, 3.5, 1]))[:3]
            b.solve_left(t, grip, reg=0.00001)
        report = b.report
        # Leave a little numerical clearance at the support shoulder (rigid vanilla arm, no elbow).
        for values in a.tracks["leftArm"]["position"].values():
            values[0] += .08
            values[2] -= .04
    for channels in a.tracks.values():
        for keys in channels.values():
            keys[a.length] = list(keys[0.0])
    a.loop = True
    return a, report


# Strike heights (player model px, three.js: x right, y up, z back): a standing walker's head and chest ~1.5 blocks out.
HEAD, CHEST = 30.0, 21.0


def families():
    out, rep = {}, []

    def add(b, info):
        out[b.name] = (b.a, dict(info, geodesic_items=True))
        rep.extend(b.report)

    # ---------------------------------------------------------------- knife
    # Give the blade-down carry time to load: the former 0.08 s wind-up turned 108 degrees in one tick.
    add(swing("swing_knife", "knife", 0.55, [
        (0.14, dict(torso=[2, 16, 0], head=[4, -6, 0], body_pos=[0, -0.2, 0.4]), P(8, 20, -6), U(0.6, 0.2, -0.78), U(0, -1, 0)),
        (0.24, dict(torso=[6, -14, 0], head=[6, 4, 0], body_pos=[0, -0.5, -1.4], leftLeg=[-14, 0, 0], rightLeg=[8, 0, 0]),
         P(-2, CHEST + 3, -20), U(-0.7, 0.05, -0.7), U(0, -0.3, 0.95)),
        (0.36, dict(torso=[4, -24, 0], head=[4, 6, 0], body_pos=[0, -0.4, -1.0]), P(-10, CHEST, -12), U(-0.9, 0, -0.3), None),
        (0.55, dict(**STAND), P(4, 15, -7), U(0.2, 0.3, -0.93), U(0, -1, 0)),
    ], sounds=((0.16, "holdfast_core:swing.light"),), commit=0.14, strike=0.24), dict(weapon="knife", impacts=[]))
    add(swing("swing_knife_heavy", "knife", 0.7, [
        (0.16, dict(torso=[-6, 10, 0], head=[-4, 0, 0], body_pos=[0, 0.3, 0.6]), P(6, 38, 2), U(0.1, -0.4, -0.9), U(0, 0, 1)),
        (0.3, dict(torso=[14, -6, 0], head=[10, 0, 0], body_pos=[0, -1.0, -1.8], leftLeg=[-18, 0, 0], rightLeg=[10, 0, 0]),
         P(1, HEAD - 2, -20), U(0, -0.9, -0.4), U(0, 0.4, -0.9)),
        (0.45, dict(torso=[16, -6, 0], body_pos=[0, -1.1, -1.8]), P(1, HEAD - 6, -19), U(0, -0.95, -0.3), None),
        (0.7, dict(**STAND), P(4, 15, -7), U(0.2, 0.3, -0.93), U(0, -1, 0)),
    ], sounds=((0.2, "holdfast_core:swing.light"),), commit=0.16, strike=0.3), dict(weapon="knife", impacts=[]))

    # ---------------------------------------------------------------- machete
    add(swing("swing_machete", "machete", 0.75, [
        (0.20, dict(torso=[-2, 20, 0], head=[2, -8, 0], body_pos=[0, 0, 0.6]), P(10, 34, 2), U(0.3, 0.8, 0.5), U(-0.3, 0.5, -0.8)),
        (0.33, dict(torso=[10, -16, 0], head=[8, 2, 0], body_pos=[0, -0.8, -1.6], leftLeg=[-16, 0, 0], rightLeg=[10, 0, 0]),
         P(0, HEAD - 2, -22), U(-0.5, -0.3, -0.8), U(-0.6, -0.7, 0.2)),
        (0.50, dict(torso=[14, -30, 0], head=[8, 6, 0], body_pos=[0, -0.9, -1.4]), P(-12, 14, -12), U(-0.8, -0.5, -0.3), None),
        (0.75, dict(**STAND), P(7, 24, -3), U(0.15, 0.95, -0.25), U(0, 0.2, -0.98)),
    ], sounds=((0.24, "holdfast_core:swing.light"),), commit=0.20, strike=0.33), dict(weapon="machete", impacts=[]))
    add(swing("swing_machete_heavy", "machete", 0.8, [
        (0.22, dict(torso=[-10, 6, 0], head=[-4, 0, 0], body_pos=[0, 0.4, 0.8]), P(5, 44, 8), U(0.05, 0.6, 0.8), U(0, 0.8, -0.6)),
        (0.38, dict(torso=[18, 0, 0], head=[12, 0, 0], body_pos=[0, -1.4, -2.2], leftLeg=[-20, 0, 0], rightLeg=[12, 0, 0]),
         P(1, HEAD, -22), U(0, -0.5, -0.86), U(0, -0.86, 0.5)),
        (0.55, dict(torso=[22, 0, 0], body_pos=[0, -1.6, -2.0]), P(1, 12, -16), U(0, -0.9, -0.4), U(0, -0.4, 0.9)),
        (0.8, dict(**STAND), P(7, 24, -3), U(0.15, 0.95, -0.25), U(0, 0.2, -0.98)),
    ], sounds=((0.3, "holdfast_core:swing.heavy"),), commit=0.22, strike=0.38), dict(weapon="machete", impacts=[]))

    # ---------------------------------------------------------------- hatchet
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
    for heavy, length, wind, contact in ((False, .65, .12, .25), (True, .9, .22, .38)):
        add(swing("swing_bat" + ("_heavy" if heavy else ""), "bat", length, [
            (wind, dict(torso=[-3, 30, -4], body_pos=[0, -.3, .8], head=[2, -12, 0]),
             pa.from_hands((2, 18, -4), (.5, .55, .66)), U(.5, .55, .66), None),
            (contact, dict(torso=[10, -18, 4], body_pos=[0, -.9, -1.8], head=[-2, 7, -2], leftLeg=[-18, 0, 0], rightLeg=[10, 0, 0]),
             P(-2, 27, -25), U(-.35, 0, -.94), None),
            (contact + .14, dict(torso=[14, -48, 6], body_pos=[0, -1, -1.4], head=[-4, 15, -3]),
             P(-16, 21, -5), U(-.97, -.1, .2), None),
            (length, dict(**STAND), P(5, 9, -17), U(.32, -.65, -.69), None),
        ], two=True, sounds=((contact - .08, "holdfast_core:swing.heavy" if heavy else "holdfast_core:swing.light"),),
            commit=wind, strike=contact), dict(weapon="bat", impacts=[]))
    for weapon in ("knife", "machete", "hatchet", "katana", "axe", "bat"):
        carry, report = relaxed_hold(weapon)
        out["hold_" + weapon] = (carry, dict(weapon=weapon, impacts=[], geodesic_items=True))
        rep.extend(report)
    for weapon in REST_FAMILIES:
        rest, report = globals()["carry"](weapon, "rest")
        out["rest_" + weapon] = (rest, dict(weapon=weapon, impacts=[], geodesic_items=True))
        rep.extend(report)
    out.update(unarmed())
    return out, rep


def unarmed():
    """Empty-hand attacks replace vanilla's swing; source-scoped regeneration is supported."""
    out = {}
    for heavy, length, contact in ((False, .6, .28), (True, .8, .38)):
        a = Anim(length, False)
        a.pose(0, **STAND, rightArm=[-12, 0, 12], leftArm=[-12, 0, -12], rightItem=[0, 0, 0])
        # Chamber the fist beside the ribs, opposite hand guarding, then drive forward.
        # Forward weight shift only: no click-driven torso yaw/roll.
        a.pose(contact * .60, torso=[-4, 0, 0], body_pos=[0, -.3, .8],
               rightArm=[-32, -24, 22], leftArm=[-68, 12, -12])
        a.pose(contact, torso=[10, 0, 0], body_pos=[0, -.6, -1], rightArm=[-92, -6, 8], leftArm=[-30, 0, -14])
        a.pose(contact + .08, torso=[12, 0, 0], rightArm=[-96, -8, 8])
        a.pose(length, **STAND, rightArm=[-12, 0, 12], leftArm=[-12, 0, -12])
        a.event(contact, "strike").sound(contact - .05, "holdfast_core:swing.light")
        out["swing_unarmed" + ("_heavy" if heavy else "")] = (a, dict(impacts=[], geodesic_items=True))
    return out


# ================================================================== locomotion
for name, frac in (("move_walk", 1.0), ("move_sprint", 1.3), ("move_crouch_walk", 0.3)):
    pm.MOVE_SPEED.setdefault(name, frac)


def locomotion():
    out = {}

    def gait(name, a_leg, arm, lean, bob, length_hint):
        L, a_r, a_l = pm.fit_cycle(name, a_leg, a_leg)

        # Contact, down, passing, up; then the opposite leg. The stance foot stays grounded while the swing
        # foot clears it. Rigid Minecraft legs cannot bend at the knee, so clearance comes from hip/leg offsets.
        a = Anim(L, False)
        leg = (-1, -.62, 0, .67, 1, .62, 0, -.67, -1)
        for i, v in enumerate(leg):
            u, t = i / 8, L * i / 8
            p = TAU * u
            sway = S(p)
            right_lift = max(0, S(p)) * (1.0 if name != "move_sprint" else 1.9)
            left_lift = max(0, -S(p)) * (1.0 if name != "move_sprint" else 1.9)
            a.pose(t, body_pos=[.3 * sway, 0, .12 * S(p * 2)],
                   torso=[lean + .8 * S(p * 2), -3.5 * sway, 1.2 * sway],
                   head=[-lean * .55 - .5 * S(p * 2), 2.8 * sway, -.8 * sway],
                   rightLeg=[a_r * v, 1.5 * sway, .8 * sway], leftLeg=[-a_l * v, 1.5 * sway, .8 * sway],
                   rightLeg_pos=[0, right_lift, 0], leftLeg_pos=[0, left_lift, 0],
                   rightArm=[-arm * C(p - .18), 2.5 * sway, 10 + 1.5 * S(p - .3)],
                   leftArm=[arm * C(p - .18), 2.5 * sway, -10 + 1.5 * S(p - .3)], rightItem=[0, 0, 0])
            stance = "leftLeg" if u < .5 else "rightLeg"
            W = fk.worlds(pa.SR, [a], t)
            floor = fk.point(W, stance, (0, -12, 0))[1]
            a.pose(t, body_pos=[.3 * sway, -floor, .12 * S(p * 2)])
        a.loop = True
        info = dict(impacts=[])
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
    a = Anim(1, True)
    for t in (0, .5, 1):
        k = S(TAU * t)
        a.pose(t, body_pos=[0, 0, 0], torso=[6, 0, .5 * k], head=[-4, 0, -.4 * k],
               rightLeg=[-12 + 2 * k, 0, 0], leftLeg=[8 - 2 * k, 0, 0],
               rightArm=[-22 + k, 0, 18], leftArm=[-22 - k, 0, -18], rightItem=[0, 0, 0])
    out["move_fall"] = (a, dict(impacts=[]))
    a = Anim(.3, False)
    for t, k in ((0, .3), (.08, 1), (.18, .35), (.3, 0)):
        a.pose(t, body_pos=[0, -1.2 * k, .2 * k], torso=[10 * k, 0, 0], head=[-5 * k, 0, 0],
               rightLeg=[-8 * k, 0, 0], leftLeg=[-8 * k, 0, 0],
               rightArm=[-14 * k, 0, 10 + 5 * k], leftArm=[-14 * k, 0, -10 - 5 * k], rightItem=[0, 0, 0])
    out["move_land"] = (a, dict(impacts=[]))

    for name, crouch in (("move_idle", False), ("move_crouch", True)):
        a = Anim(3.2, True)
        for i in range(9):
            t, p = i * .4, TAU * i / 8
            a.pose(t, body_pos=[.12 * S(p), -.1 * (1 - C(p)), 0],
                   torso=[(4 if crouch else 0) + .5 * S(p), .6 * S(p), .35 * S(p)],
                   head=[-.6 * S(p), .8 * S(p * 2), -.3 * S(p)],
                   rightLeg=[-3 if crouch else 0, 0, 0], leftLeg=[-3 if crouch else 0, 0, 0],
                   rightArm=[-5 + S(p), 0, 11 + .5 * S(p)], leftArm=[-5 - S(p), 0, -11 - .5 * S(p)],
                   rightItem=[0, 0, 0])
        out[name] = (a, dict(impacts=[]))

    # TaCZ-safe variants: legs and body translation only (no lean, head, arms or items), like the injury _gun walks.
    for name in ("move_walk", "move_sprint", "move_crouch_walk", "move_jump", "move_idle", "move_crouch", "move_fall", "move_land"):
        g, info = out[name]
        gg = copy.deepcopy(g)
        for part in ("torso", "head", "rightArm", "leftArm", "rightItem", "leftItem"):
            gg.tracks.pop(part, None)
        out[name + "_gun"] = (gg, dict(info, firearm_variant=True))
    # Non-firearm take-off is already airborne when selected. Hold the tuck until the controller sees descent;
    # do not play a ground-loading squat or a timed landing while still rising. Preserve accepted _gun assets above.
    rising = Anim(.3, "hold_on_last_frame")
    rising.pose(0, **dict(STAND, rightLeg=[6, 0, 0], leftLeg=[2, 0, 0]),
                rightArm=[-18, 0, 10], leftArm=[-18, 0, -10], rightItem=[0, 0, 0])
    rising.pose(.15, body_pos=[0, .15, 0], torso=[3, 0, 0], head=[-3, 0, 0],
                rightLeg=[-18, 0, 0], leftLeg=[-8, 0, 0],
                rightArm=[-32, 0, 14], leftArm=[-32, 0, -14])
    rising.pose(.3, body_pos=[0, .2, 0], torso=[4, 0, 0], head=[-2, 0, 0],
                rightLeg=[-22, 0, 0], leftLeg=[-10, 0, 0],
                rightArm=[-30, 0, 15], leftArm=[-30, 0, -15])
    out["move_jump"] = (rising, dict(impacts=[]))
    speeds = {n: round(pm.ground_speed(out[n][0].length, *pm.fit_cycle(n, a_leg, a_leg)[1:]), 3)
              for n, a_leg in (("move_walk", 32), ("move_sprint", 48), ("move_crouch_walk", 22))}
    return out, speeds


def build():
    out, rep = families()
    loco, speeds = locomotion()
    out.update(loco)
    return out, rep, speeds


if __name__ == "__main__":
    import sys, os, shutil, json
    if any(flag in sys.argv for flag in ('--refresh-movement-carries','--refresh-families','--refresh-rest-axe')):
        # Fast source regeneration while iterating on carries/gaits; executions and native gun poses stay intact.
        # The normal make_all.sh still rebuilds everything from scratch.
        from lib import OUT, write_json
        if '--refresh-rest-axe' in sys.argv:
            a,report=carry('axe','rest')
            out={'rest_axe':(a,dict(weapon='axe',impacts=[],geodesic_items=True))}
        elif "--refresh-families" in sys.argv:
            out, report = families()
            print('\n'.join(report))
        else:
            out, speeds = locomotion()
            for weapon in ("knife", "machete", "hatchet", "katana", "axe", "bat"):
                a, _ = relaxed_hold(weapon)
                out["hold_" + weapon] = (a, dict(weapon=weapon, impacts=[], geodesic_items=True))
            for weapon in REST_FAMILIES:
                a, _ = carry(weapon, "rest")
                out["rest_" + weapon] = (a, dict(weapon=weapon, impacts=[], geodesic_items=True))
        pa.export(out, merge_keys=True)
        root = os.path.join(os.path.dirname(__file__), "..")
        target = os.path.join(root, "export", "assets", "holdfast")
        for name in out:
            for folder, suffix in (("player_animation", ".json"), ("player_animation_extras", ".extras.json")):
                shutil.copy2(os.path.join(OUT, folder, name + suffix), os.path.join(target, folder, name + suffix))
        shutil.copy2(os.path.join(OUT, "player_keys.animation.json"), os.path.join(root, "preview", "rig", "player_keys.animation.json"))
        print(f"Refreshed {len(out)} clips from editable source.")
        sys.exit(0)
    o, r, s = build()
    print(len(o), "clips;", s)
    print("\n".join(r))
