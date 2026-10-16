"""Third- and first-person frames of the executions and the paired grab."""
import sys, os, math
import numpy as np
import fk
from fk import worlds, quads, render, sheet, PALETTE, SURV
import player_anims as pa
import walker_anims as wa

OUTDIR = sys.argv[1]
out, _ = pa.build()
WEAPONS = {"knife", "bat", "axe", "machete"}
EYE = pa.EYE


def surv_quads(a, t, weapon=None, fp=False):
    Wm = worlds(pa.SR, [a], t)
    hide = set(WEAPONS - {weapon}) if weapon else set(WEAPONS)
    if fp:
        hide |= {"head", "torso", "rightLeg", "leftLeg"}
    return quads(pa.SR, Wm, hide=hide)


def fp_camera(cam_anim, t):
    rot = cam_anim.value("camera", "rotation", t) or [0, 0, 0]
    pos = cam_anim.value("camera", "position", t) or [0, 0, 0]
    pitch, yaw, roll = (math.radians(v) for v in rot)
    eye = np.array([-pos[0], EYE + pos[1], pos[2]])
    d = np.array([math.sin(yaw) * math.cos(pitch), -math.sin(pitch), -math.cos(yaw) * math.cos(pitch)])
    return dict(cam_pos=eye, cam_target=eye + d * 20, roll_deg=-rot[2], fov=70)


def scene(name, a, info, react, dist, times, weapon):
    cam = pa.camera_track(a, info)
    imgs, labels = [], []
    for t in times:
        wq = quads(pa.WR, worlds(pa.WR, [react], t, world=pa.walker_world(dist))) if react else []
        tp = render([(surv_quads(a, t, weapon), {**PALETTE, **fk.SURV}), (wq, PALETTE)], size=(300, 240),
                    cam_pos=(62, 24, -dist / 2), cam_target=(0, 20, -dist / 2), fov=42)
        fpi = render([(surv_quads(a, t, weapon, fp=True), {**PALETTE, **fk.SURV}), (wq, PALETTE)], size=(300, 240),
                     **fp_camera(cam, t))
        imgs += [tp, fpi]
        labels += [f"{name} {t:.2f} 3rd", f"{name} {t:.2f} 1st"]
    return imgs, labels


imgs, labels = [], []
for w in ["knife", "bat_quick", "bat_charged", "axe", "machete"]:
    e = wa.EXECS[w]
    a, info = out[f"exec_{w}"]
    ts = [0.0, e["commit"], e["impact"], e.get("release", e["impact"] + 0.15), e["length"] * 0.8]
    i2, l2 = scene(w, a, info, pa.W["react"][f"react_{w}"], e["dist"], ts, info["weapon"])
    imgs += i2; labels += l2
    sheet(i2, 4, l2).save(os.path.join(OUTDIR, f"v2_exec_{w}.png"))

# grab: pair player and walker clips
pairs = [("grabbed_seize", "grab_seize", [0.0, 0.4]), ("grabbed_struggle", "grab_hold", [0.2]),
         ("grabbed_bitten", "grab_bite", [0.38]), ("grabbed_bite_blocked", "grab_bite_blocked", [0.36]),
         ("grabbed_shove", "grab_shoved", [0.16, 0.4])]
gi, gl = [], []
for p, wname, ts in pairs:
    a, info = out[p]
    i2, l2 = scene(p, a, info, pa.W["grab"][wname], wa.GRAB_DIST, ts, None)
    gi += i2; gl += l2
sheet(gi, 4, gl).save(os.path.join(OUTDIR, "v2_grab.png"))
print("ok")
