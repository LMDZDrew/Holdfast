"""Forward kinematics with the same Bedrock -> three.js conversion the preview uses, plus a tiny
software rasteriser for checking poses from any camera (including first person)."""
import json, math, os
import numpy as np
from PIL import Image, ImageDraw
from lib import OUT


def load(name):
    with open(os.path.join(OUT, name)) as f:
        return json.load(f)


def rig(geo_name):
    g = load(geo_name)["minecraft:geometry"][0]
    bones, piv, order = {}, {}, []
    for b in g["bones"]:
        p = np.array([-b["pivot"][0], b["pivot"][1], b["pivot"][2]], float)
        piv[b["name"]] = p
        pp = piv[b["parent"]] if "parent" in b else np.zeros(3)
        cubes = []
        for c in b.get("cubes", []):
            fr = np.array([-(c["origin"][0] + c["size"][0]), c["origin"][1], c["origin"][2]], float)
            to = fr + np.array(c["size"], float)
            cubes.append((fr - p, to - p))
        bones[b["name"]] = dict(parent=b.get("parent"), rest=p - pp, cubes=cubes)
        order.append(b["name"])
    return dict(bones=bones, order=order)


def rotm(x, y, z):
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx                      # three.js Euler order "ZYX"


def local(rest, rot_deg, pos_px):
    M = np.eye(4)
    if rot_deg is not None:
        M[:3, :3] = rotm(-math.radians(rot_deg[0]), -math.radians(rot_deg[1]), math.radians(rot_deg[2]))
    off = np.zeros(3) if pos_px is None else np.array([-pos_px[0], pos_px[1], pos_px[2]])
    M[:3, 3] = rest + off
    return M


def entity(x=0.0, z=0.0, yaw_deg=0.0, y=0.0):
    M = np.eye(4)
    M[:3, :3] = rotm(0, math.radians(yaw_deg), 0)
    M[:3, 3] = [x, y, z]
    return M


def worlds(R, layers, t, world=None, override=None):
    """layers: list of lib.Anim (later layers win per bone/channel). override: {bone: rot_deg}."""
    world = np.eye(4) if world is None else world
    W = {}
    for name in R["order"]:
        b = R["bones"][name]
        rot = posv = None
        for a in layers:
            v = a.value(name, "rotation", t)
            if v is not None:
                rot = v
            v = a.value(name, "position", t)
            if v is not None:
                posv = v
        if override and name in override:
            rot = override[name]
        M = local(b["rest"], rot, posv)
        W[name] = (W[b["parent"]] if b["parent"] else world) @ M
    return W


def point(W, bone, local_pt=(0, 0, 0)):
    return (W[bone] @ np.array([*local_pt, 1.0]))[:3]


def quads(R, W, hide=()):
    out = []
    for name in R["order"]:
        if name in hide:
            continue
        for fr, to in R["bones"][name]["cubes"]:
            xs, ys, zs = (fr[0], to[0]), (fr[1], to[1]), (fr[2], to[2])
            c = np.array([[x, y, z, 1] for x in xs for y in ys for z in zs]).T
            c = (W[name] @ c)[:3].T
            for f in ([0, 1, 3, 2], [4, 6, 7, 5], [0, 4, 5, 1], [2, 3, 7, 6], [0, 2, 6, 4], [1, 5, 7, 3]):
                out.append((c[f], name))
    return out


PALETTE = {"head": (150, 164, 136), "jaw": (122, 48, 48), "torso": (138, 74, 62), "hips": (90, 90, 90),
           "rightArm": (160, 90, 72), "leftArm": (160, 90, 72), "rightForearm": (150, 164, 136),
           "leftForearm": (150, 164, 136), "rightLeg": (74, 86, 104), "leftLeg": (74, 86, 104),
           "rightShin": (60, 48, 40), "leftShin": (60, 48, 40), "stub": (110, 30, 30), "gut": (140, 80, 84),
           "knife": (200, 200, 210), "bat": (176, 140, 96), "axe": (150, 120, 90), "machete": (200, 200, 210)}
SURV = {"torso": (88, 110, 76), "head": (216, 176, 140), "rightArm": (88, 110, 76), "leftArm": (88, 110, 76),
        "rightLeg": (96, 92, 80), "leftLeg": (96, 92, 80)}


def render(groups, cam_pos, cam_target, fov=70, size=(420, 320), up=(0, 1, 0), roll_deg=0.0, bg=(205, 210, 198), ground=True):
    """groups: list of (quads, palette). Simple painter's algorithm with lambert shading."""
    W_, H_ = size
    cam_pos, cam_target = np.array(cam_pos, float), np.array(cam_target, float)
    f = cam_target - cam_pos
    f /= np.linalg.norm(f)
    r = np.cross(f, np.array(up, float))
    r /= np.linalg.norm(r)
    u = np.cross(r, f)
    if roll_deg:
        a = math.radians(roll_deg)
        r, u = r * math.cos(a) + u * math.sin(a), -r * math.sin(a) + u * math.cos(a)
    fl = (H_ / 2) / math.tan(math.radians(fov) / 2)
    light = np.array([0.4, 0.8, 0.3])
    light /= np.linalg.norm(light)
    img = Image.new("RGB", size, bg)
    d = ImageDraw.Draw(img)
    items = []
    if ground:
        g = 64
        for gx in range(-96, 96, g):
            for gz in range(-128, 96, g):
                q = np.array([[gx, 0, gz], [gx + g, 0, gz], [gx + g, 0, gz + g], [gx, 0, gz + g]], float)
                items.append((q, (150, 156, 132) if ((gx + gz) // g) % 2 else (140, 146, 124), True))
    for qs, pal in groups:
        for q, name in qs:
            items.append((q, pal.get(name, (128, 128, 128)), False))
    drawn = []
    for q, col, flat in items:
        rel = q - cam_pos
        z = rel @ f
        if np.any(z < 0.5):
            continue
        xs = (rel @ r) / z * fl + W_ / 2
        ys = -(rel @ u) / z * fl + H_ / 2
        n = np.cross(q[1] - q[0], q[2] - q[0])
        nn = np.linalg.norm(n)
        if nn < 1e-9:
            continue
        n /= nn
        if not flat and n @ (q.mean(0) - cam_pos) > 0:
            n = -n
        shade = 0.55 + 0.45 * abs(n @ light) if not flat else 1.0
        c = tuple(int(v * shade) for v in col)
        drawn.append((z.mean() if not flat else 1e9 - 1, list(zip(xs, ys)), c, flat))
    drawn.sort(key=lambda it: -it[0])
    for _, pts, c, flat in drawn:
        d.polygon(pts, fill=c, outline=None if flat else (30, 30, 30))
    return img


def sheet(images, cols, labels=None, pad=4):
    w, h = images[0].size
    rows = (len(images) + cols - 1) // cols
    out = Image.new("RGB", (cols * (w + pad), rows * (h + pad + 14)), (255, 255, 255))
    d = ImageDraw.Draw(out)
    for i, im in enumerate(images):
        x, y = (i % cols) * (w + pad), (i // cols) * (h + pad + 14)
        out.paste(im, (x, y + 14))
        if labels:
            d.text((x + 2, y), labels[i], fill=(0, 0, 0))
    return out
