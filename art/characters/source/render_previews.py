"""Labelled frame sheets of the exported clips, drawn by fk.py's software renderer (flat colours, not
the textures). PREVIEW ONLY: this is our own maths, not Minecraft, GeckoLib or Player Animator."""
import os, math
import numpy as np
from PIL import ImageDraw
import fk
from fk import worlds, quads, render, sheet, PALETTE
import player_anims as pa
import walker_anims as wa
import placement

OUT = os.path.join(os.path.dirname(__file__), "..", "preview", "frames")
os.makedirs(OUT, exist_ok=True)
W = pa.W
WR, CR, SR = pa.WR, pa.CR, pa.SR
PAIR = placement.load()
PLAYER, _ = pa.build()
PROPS = {"knife", "bat", "axe", "machete", "crutchR", "crutchL"}
SPAL = {**PALETTE, **fk.SURV, "crutchR": (150, 120, 84), "crutchL": (150, 120, 84)}
BANNER = "PREVIEW (software render of exported keyframes) - not Minecraft - flat colours, not textures"


def save(name, imgs, labels, cols=4):
    s = sheet(imgs, cols, labels)
    from PIL import Image
    out = Image.new("RGB", (s.width, s.height + 18), (40, 40, 36))
    out.paste(s, (0, 18))
    ImageDraw.Draw(out).text((6, 3), BANNER, fill=(236, 232, 220))
    out.save(os.path.join(OUT, name))


def surv(a, t, weapon=None, fp=False, crawl_pose=False):
    Wm = worlds(SR, [a], t)
    if crawl_pose:        # stand-in for the vanilla crawling pose (see docs/animation.md)
        M = fk.local(np.zeros(3), [90, 0, 0], [0, 2, 15])
        Wm = worlds(SR, [a], t, world=M)
    show = {"crutch": {"crutchR", "crutchL"}}.get(weapon, {weapon})
    hide = PROPS - show
    if fp:
        hide |= {"head", "torso", "rightLeg", "leftLeg"}
    return quads(SR, Wm, hide=hide)


def fp_cam(a, info, t):
    cam = pa.camera_track(a, info)
    rot = cam.value("camera", "rotation", t)
    p = cam.value("camera", "position", t)
    pitch, yaw = math.radians(rot[0]), math.radians(rot[1])
    eye = np.array([-p[0], pa.EYE + p[1], p[2]])
    d = np.array([math.sin(yaw) * math.cos(pitch), -math.sin(pitch), -math.cos(yaw) * math.cos(pitch)])
    return dict(cam_pos=eye, cam_target=eye + d * 20, roll_deg=-rot[2], fov=70)


def pair(name, pclip, react, rig, dist, times, fp=True, side=(64, 24), size=(300, 230)):
    a, info = PLAYER[pclip]
    imgs, labels = [], []
    for t in times:
        wq = quads(rig, worlds(rig, [react], t, world=pa.walker_world(dist))) if react else []
        imgs.append(render([(surv(a, t, info.get("weapon")), SPAL), (wq, PALETTE)], size=size,
                           cam_pos=(side[0], side[1], -dist / 2), cam_target=(0, 14, -dist / 2), fov=44))
        labels.append(f"{name} {t:.2f}s third person")
        if fp:
            imgs.append(render([(surv(a, t, info.get("weapon"), fp=True), SPAL), (wq, PALETTE)], size=size, **fp_cam(a, info, t)))
            labels.append(f"{name} {t:.2f}s first person")
    return imgs, labels


def solo(R, layers, times, label, view, size=(240, 200)):
    return ([render([(quads(R, worlds(R, layers, t)), PALETTE)], size=size, **view) for t in times],
            [f"{label} {t:.2f}s" for t in times])


side = dict(cam_pos=(70, 22, 0), cam_target=(0, 16, 0), fov=40)
q34 = dict(cam_pos=(50, 34, -50), cam_target=(0, 10, 0), fov=42)

# 1 walker: idle layers, walk gaits, pursuit
imgs, labels = [], []
for g, ar, hd in [("stand", "dangle", "loll_left"), ("shamble", "forward_low", "chin_down"), ("limp_right", "broken", "sniff"),
                  ("hunched", "clutched", "slack"), ("hungry", "reach", "snap")]:
    i, l = solo(WR, [W["body"][g], W["arms"][ar], W["heads"][hd]], [0.0, W["body"][g].length * 0.25], f"{g}+{ar}+{hd}", side)
    imgs += i; labels += l
save("01_walker_idle_walk_pursuit.png", imgs, labels, 4)

# 2 walker: attack, prone-to-active, death
imgs, labels = [], []
for n, ts in [("lunge", [0.25, 0.45, 0.6]), ("prone_still", [0]), ("prone_rise", [0.4, 1.5, 2.6]), ("prone_ankle_grab", [0.5]),
              ("rise_from_sit", [1.0]), ("death_headshot", [0.4, 1.6])]:
    i, l = solo(WR, [W["full"][n]], ts, n, q34)
    imgs += i; labels += l
save("02_walker_attack_prone_death.png", imgs, labels, 4)

# 3 crawler
imgs, labels = [], []
for n, ts in [("crawler_still", [0]), ("crawl", [0, 1.2]), ("crawl_drag", [0.8]), ("crawler_lunge", [0.2, 0.45, 0.9]), ("crawler_death", [1.0])]:
    i, l = solo(CR, [W["crawler"][n]], ts, n, q34)
    imgs += i; labels += l
save("03_crawler.png", imgs, labels, 4)

# 4 grabs and escapes (upright, prone, crawler)
imgs, labels = [], []
for pclip, wclip, ts in [("grabbed_seize", "grab_seize", [0.4]), ("grabbed_struggle", "grab_hold", [0.2]),
                         ("grabbed_bitten", "grab_bite", [0.38]), ("grabbed_shove", "grab_shoved", [0.2])]:
    i, l = pair(pclip, pclip, W["grab"][wclip], WR, wa.GRAB_DIST, ts)
    imgs += i; labels += l
save("04_grab_upright.png", imgs, labels, 4)
imgs, labels = [], []
for rig, key, pre, d in [(WR, "prone", W["full"], PAIR["ankle_prone"]["dist"]), (CR, "crawler", W["crawler"], PAIR["ankle_crawler"]["dist"])]:
    for pclip, wclip, ts in [("grabbed_ankle_struggle", f"{key}_ankle_hold", [0.2]), ("grabbed_ankle_bitten", f"{key}_ankle_bite", [0.38]),
                             ("grabbed_ankle_kick", f"{key}_ankle_kicked", [0.14])]:
        i, l = pair(f"{key}:{pclip}", pclip, pre[wclip], rig, d, ts, side=(56, 14))
        imgs += i; labels += l
save("05_grab_ankle_prone_and_crawler.png", imgs, labels, 4)

# 6 executions (third and first person at commit, impact, release)
for w in ["knife", "bat_charged", "axe", "machete", "knife_ground"]:
    e = wa.EXECS[w]
    if w == "knife_ground":
        react, rig, d = W["crawler"]["react_knife_ground"], CR, PAIR["knife_ground_crawler"]["dist"]
    else:
        react, rig, d = W["react"][f"react_{w}"], WR, e["dist"]
    ts = [e["commit"], e["impact"], e.get("release", e["impact"] + 0.2)]
    i, l = pair(f"exec_{w}", f"exec_{w}", react, rig, d, ts)
    save(f"06_exec_{w}.png", i, l, 4)

# 7 bite confirmation, blunt swings, injury movement (player alone)
imgs, labels = [], []
for clip, ts in [("bite_confirm_neck", [1.0]), ("bite_confirm_forearm", [0.9]), ("bite_confirm_leg", [1.1]),
                 ("bat_charge_hold", [0.0]), ("bat_charged_miss", [0.45, 0.75])]:
    i, l = pair(clip, clip, None, None, 0, ts, fp=False, side=(60, 22))
    imgs += i; labels += l
save("07_bite_confirm_and_swings.png", imgs, labels, 4)
imgs, labels = [], []
for clip in ["move_limp", "move_arm_injured", "move_prosthesis", "move_crutch_improvised", "move_crutch_medical", "move_crawl_legless"]:
    a, info = PLAYER[clip]
    for t in (0.0, a.length * 0.5):
        q = surv(a, t, info.get("weapon"), crawl_pose=info.get("vanilla_pose") == "swimming")
        imgs.append(render([(q, SPAL)], size=(240, 200), cam_pos=(58, 22, 6), cam_target=(0, 12, 0), fov=44))
        labels.append(f"{clip} {t:.2f}s")
save("08_injury_movement.png", imgs, labels, 4)
print("sheets in", os.path.relpath(OUT))
