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
    for t, k in ((0, 0), (0.2, 1), (0.4, 0), (0.6, -1), (0.8, 0)):
        a.pose(t, **dict(HELD, rightLeg=[-14 + 8 * k, 0, 0], torso=[-6 - 2 * abs(k), 0, 0], head=[46 + 3 * k, 6, 0],
                         body_pos=[0, -0.4, 1.0 + 0.4 * k], rightArm=[-30, 0, 34 + 4 * k], leftArm=[-26, 0, -38 - 4 * k]))
    a.sound(0.0, "holdfast:player.strain")
    out["grabbed_ankle_struggle"] = (a, dict(impacts=[], tremor=0.4))

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
    CROUCH = dict(body_pos=[0, -3.5, 0.6], torso=[34, 0, 0], head=[36, 0, 0], rightLeg=[-6, 0, 0], leftLeg=[-22, 0, 0])
    b.pose(0.0, **STAND, rightArm=[-20, 10, 10], rightItem=[0, 0, 0], leftArm=[-20, 0, -6])
    b.pose(0.18, body_pos=[0, -2.0, 0.2], torso=[20, 6, 0], head=[30, 0, 0], leftLeg=[-14, 0, 0])
    Hm, _ = b.walker(0.18)
    up0 = -pa.hd(Hm, (0, -1, 0))
    b.solve_weapon(0.18, pa.hp(Hm, (0, 8, 0)) + up0 * 16 + P(3, 0, 6), axis=-up0)       # knife raised clear
    # knife raised, blade pointing down, free hand reaching for its back
    b.pose(e["commit"], **CROUCH)
    Hm, Wm = b.walker(e["commit"])
    down = pa.hd(Hm, (0, -1, 0))
    b.solve_weapon(e["commit"], pa.hp(Hm, (0, 8, 0)) - down * 9, axis=down)
    b.solve_left(e["commit"], fk.point(Wm, "torso", (0, 10, 2.5)))
    b.pose(I - 0.07, body_pos=[0, -3.9, -0.2], torso=[38, -2, 0], head=[40, 0, 0])
    Hm, Wm = b.walker(I - 0.07)
    down = pa.hd(Hm, (0, -1, 0))
    b.solve_weapon(I - 0.07, pa.hp(Hm, (0, 8, 0)) - down * 4, axis=down)                 # tip just above the skull
    b.pose(I, body_pos=[0, -4.2, -0.6], torso=[40, -4, 0], head=[40, 0, 0])
    Hm, Wm = b.walker(I)
    b.solve_weapon(I, pa.hp(Hm, (0, 3.5, 0)), axis=pa.hd(Hm, (0, -1, 0)))
    b.solve_left(I, fk.point(Wm, "torso", (0, 10, 2.5)))
    b.pose(0.66, body_pos=[0, -4.4, -0.8], torso=[42, -6, 0])
    Hm, Wm = b.walker(0.66)
    b.solve_weapon(0.66, pa.hp(Hm, (0, 2.5, 0)), axis=pa.hd(Hm, (0, -1, 0)))
    b.pose(0.8, body_pos=[0, -4.3, -0.7], torso=[41, -5, 0])
    Hm, Wm = b.walker(0.8)
    b.solve_weapon(0.8, pa.hp(Hm, (0, 2.5, 0)), axis=pa.hd(Hm, (0, -1, 0)))
    b.pose(R, body_pos=[0, -3.0, 0], torso=[26, 6, 0], head=[34, 0, 0])
    Hm, Wm = b.walker(R)
    up = -pa.hd(Hm, (0, -1, 0))
    b.solve_weapon(R, pa.hp(Hm, (0, 8, 0)) + up * 9, axis=-up)
    b.pose(e["length"], **STAND, rightArm=[-18, 10, 10], rightItem=[0, 0, 0], leftArm=[-10, 0, -4])
    b.a.event(e["commit"], "commit").event(I, "kill").event(R, "release")
    b.a.sound(0.15, "holdfast:player.exert_light").sound(I, "holdfast:player.exert_heavy").sound(0.8, "holdfast:knife.pull")
    return b, dict(impacts=[(I, 3.0)], weapon="knife")


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
    out["bat_charged_miss"] = (b.a, dict(impacts=[(0.45, 2.0)], weapon="bat"))
    return out


# =========================================================== injury-limited movement
LEG = 12.0


def ground_speed(L, a_r, a_l):
    """Blocks per second at 1x for a rigid-leg cycle with thigh amplitudes a_r, a_l (degrees)."""
    return 2 * LEG * (math.sin(math.radians(a_r)) + math.sin(math.radians(a_l))) / L / 16.0


def cycle(name, L, n, fn, info):
    a = Anim(L, True)
    for i in range(n + 1):
        u = i / n
        a.pose(L * u, **fn(u))
    return a, info


def movement():
    out, speeds = {}, {}

    # leg injury (right): short, stiff stance on the bad leg, lurch over the good one
    def limp(u):
        p = math.pi * u / 0.6 if u < 0.6 else math.pi + math.pi * (u - 0.6) / 0.4
        s, c = S(p), C(p)
        return dict(body_pos=[0.5 * s, -0.4 * pos(-s) * 2.2, 0], torso=[6 + 3 * pos(-s), 3 * s, 7 * pos(s) - 5 * pos(-s)],
                    head=[6 + 3 * pos(-s), 2 * s, -3 * s], rightLeg=[14 * c, 0, 2 + 5 * pos(s)], leftLeg=[-22 * c, 0, -1],
                    rightArm=[-8 - 10 * s, 0, 8 + 6 * pos(-s)], leftArm=[-6 + 12 * s, 0, -6 - 8 * pos(s)], rightItem=[0, 0, 0])
    out["move_limp"] = cycle("move_limp", 1.3, 12, limp, dict(impacts=[]))
    speeds["move_limp"] = ground_speed(1.3, 14, 22)

    # arm injury (left): arm cradled across the belly, short guarded steps
    def arm(u):
        p = TAU * u
        s, c = S(p), C(p)
        return dict(body_pos=[0, -0.5 * s * s, 0], torso=[8, -4 * c, 2 * s], head=[8, 2 * c, 0],
                    rightLeg=[-24 * s, 0, 0], leftLeg=[24 * s, 0, 0], rightArm=[16 * s - 6, 0, 6],
                    leftArm=[-50 + 2 * s, 34, 6], rightItem=[0, 0, 0])
    out["move_arm_injured"] = cycle("move_arm_injured", 1.1, 12, arm, dict(impacts=[]))
    speeds["move_arm_injured"] = ground_speed(1.1, 24, 24)

    # fitted prosthesis (right leg): nearly normal, stiff knee swings out a little, slight hitch (80%)
    def prosth(u):
        p = TAU * u
        s, c = S(p), C(p)
        return dict(body_pos=[0.3 * s, -0.6 * s * s - 0.4 * pos(-s), 0], torso=[3, -5 * c, 3 * s],
                    head=[4, 2 * c, -1 * s], rightLeg=[-22 * s, 0, 2 + 6 * pos(s)], leftLeg=[26 * s, 0, -1],
                    rightArm=[18 * s, 0, 4], leftArm=[-18 * s, 0, -4], rightItem=[0, 0, 0])
    out["move_prosthesis"] = cycle("move_prosthesis", 1.0, 12, prosth, dict(impacts=[]))
    speeds["move_prosthesis"] = ground_speed(1.0, 22, 26)

    # crutches, right leg not bearing weight: plant both crutches ahead, swing through on the left leg
    def crutch(effort):
        def f(u):
            p = TAU * u
            s, c = S(p), C(p)
            plant = 0.5 - 0.5 * c            # 0 at start (crutches back) -> 1 mid-cycle (crutches ahead)
            return dict(body_pos=[0, -1.2 * effort * S(math.pi * u) ** 2, 0.6 * c],
                        torso=[10 + 8 * effort * plant, 0, 0], head=[10 + 4 * effort * plant, 0, 0],
                        rightArm=[-6 - 26 * plant, 0, 10], leftArm=[-6 - 26 * plant, 0, -10],
                        rightLeg=[32, 0, 2], leftLeg=[-22 * s, 0, -1],
                        rightItem=[0, 0, 0], leftItem=[0, 0, 0])
        return f
    out["move_crutch_improvised"] = cycle("move_crutch_improvised", 1.5, 12, crutch(1.4), dict(impacts=[], weapon="crutch"))
    out["move_crutch_medical"] = cycle("move_crutch_medical", 1.15, 12, crutch(0.8), dict(impacts=[], weapon="crutch"))
    speeds["move_crutch_improvised"] = ground_speed(1.5, 0, 22)
    speeds["move_crutch_medical"] = ground_speed(1.15, 0, 22)

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
    out.update(bat_free())
    mv, speeds = movement()
    out.update(mv)
    return out, b.report, speeds
