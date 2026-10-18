"""Software preview of a TaCZ/Bedrock geometry posed by a Bedrock animation at time t (z-buffered, flat colour per face).

Blockbench convention in Bedrock space: rotation [rx, ry, rz] is Rz(-rz) @ Ry(ry) @ Rx(-rx); animation positions
add (x, y, z). Checked against TaCZ's rhino357 reload: the speed loader meets the swung-out cylinder.
Preview only; it does not reproduce TaCZ lighting, catmull-rom easing or first-person transforms.
"""
import json, math
import numpy as np
from PIL import Image, ImageDraw


def rot(r):
    rx, ry, rz = [math.radians(a) for a in r]
    rx, ry, rz = -rx, ry, -rz
    Rx = np.array([[1, 0, 0], [0, math.cos(rx), -math.sin(rx)], [0, math.sin(rx), math.cos(rx)]])
    Ry = np.array([[math.cos(ry), 0, math.sin(ry)], [0, 1, 0], [-math.sin(ry), 0, math.cos(ry)]])
    Rz = np.array([[math.cos(rz), -math.sin(rz), 0], [math.sin(rz), math.cos(rz), 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def affine(R=None, t=None):
    M = np.eye(4)
    if R is not None: M[:3, :3] = R
    if t is not None: M[:3, 3] = t
    return M


def sample(chan, t):
    """Value of a Bedrock channel at time t (linear between keys)."""
    if not isinstance(chan, dict):
        return chan if isinstance(chan, list) else [chan] * 3
    keys = sorted((float(k), v) for k, v in chan.items())
    def pre(v): return (v.get('pre', v.get('post')) if isinstance(v, dict) else v)
    def post(v): return (v.get('post', v.get('pre')) if isinstance(v, dict) else v)
    def vec(x):
        if isinstance(x, dict): x = x.get('vector', x)
        return x if isinstance(x, list) else [x] * 3
    if t <= keys[0][0]: return vec(pre(keys[0][1]))
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t0 <= t < t1:
            a, b = np.array(vec(post(v0)), float), np.array(vec(pre(v1)), float)
            return list(a + (b - a) * (t - t0) / (t1 - t0))
    return vec(post(keys[-1][1]))


def pose(geo, anim=None, t=0.0, sx=1.0):
    bones = {b['name']: b for b in geo['bones']}
    chans = (anim or {}).get('bones', {})
    cache = {}
    def world(name):
        if name in cache: return cache[name]
        b = bones[name]
        p = np.array(b.get('pivot', [0, 0, 0]), float)
        r = np.array(b.get('rotation', [0, 0, 0]), float)
        off = np.zeros(3); s = np.ones(3)
        ch = chans.get(name, {})
        if 'rotation' in ch: r = r + np.array(sample(ch['rotation'], t), float)
        if 'position' in ch:
            v = sample(ch['position'], t); off = np.array([sx * v[0], v[1], v[2]], float)
        if 'scale' in ch: s = np.array(sample(ch['scale'], t), float)
        M = affine(t=p + off) @ affine(R=rot(r)) @ affine(R=np.diag(s)) @ affine(t=-p)
        if b.get('parent'): M = world(b['parent']) @ M
        cache[name] = M
        return M
    return {n: world(n) for n in bones}


FACES = {  # corner indices of a box (bit0=x, bit1=y, bit2=z) per face, outward winding irrelevant
    'north': [0, 1, 3, 2], 'south': [4, 5, 7, 6], 'west': [0, 2, 6, 4], 'east': [1, 3, 7, 5],
    'down': [0, 1, 5, 4], 'up': [2, 3, 7, 6]}


def quads(geo, mats, hide=()):
    out = []
    for b in geo['bones']:
        if b['name'] in hide or b['name'].endswith('_pos'): continue
        M = mats[b['name']]
        for c in b.get('cubes', []):
            inf = c.get('inflate', 0)
            o = np.array(c['origin'], float) - inf
            sz = np.array(c['size'], float) + 2 * inf
            if (sz <= 0).any(): continue
            corners = np.array([[o[0] + sz[0] * (i & 1), o[1] + sz[1] * (i >> 1 & 1), o[2] + sz[2] * (i >> 2 & 1), 1] for i in range(8)])
            C = affine()
            if 'rotation' in c:
                cp = np.array(c.get('pivot', [0, 0, 0]), float)
                C = affine(t=cp) @ affine(R=rot(c['rotation'])) @ affine(t=-cp)
            w = (M @ C @ corners.T).T[:, :3]
            uv = c.get('uv', {})
            for f, idx in FACES.items():
                if isinstance(uv, dict) and f not in uv: continue
                out.append((w[idx], (uv.get(f) if isinstance(uv, dict) else None)))
    return out


def face_color(tex, face):
    if tex is None or face is None: return np.array([150, 150, 150, 255])
    (u, v), (du, dv) = face['uv'], face['uv_size']
    W, H = tex.size
    x0, x1 = sorted([u, u + du]); y0, y1 = sorted([v, v + dv])
    x0 = int(math.floor(x0 * W / TEXW)); x1 = max(x0 + 1, int(math.ceil(x1 * W / TEXW)))
    y0 = int(math.floor(y0 * H / TEXH)); y1 = max(y0 + 1, int(math.ceil(y1 * H / TEXH)))
    a = np.asarray(tex.crop((x0, y0, x1, y1)).convert('RGBA'), float).reshape(-1, 4)
    vis = a[a[:, 3] > 0]
    return vis.mean(0) if len(vis) else np.array([0, 0, 0, 0])


TEXW = TEXH = 128
VIEWS = {  # (right vector, up vector, toward-viewer vector) in Bedrock space
    'right': (np.array([0, 0, -1.0]), np.array([0, 1.0, 0]), np.array([1.0, 0, 0])),
    'left': (np.array([0, 0, 1.0]), np.array([0, 1.0, 0]), np.array([-1.0, 0, 0])),
    'rear': (np.array([1.0, 0, 0]), np.array([0, 1.0, 0]), np.array([0, 0, 1.0])),
    'top': (np.array([1.0, 0, 0]), np.array([0, 0, -1.0]), np.array([0, 1.0, 0])),
}


def view34(yaw, pitch):
    y, p = math.radians(yaw), math.radians(pitch)
    fwd = np.array([math.sin(y) * math.cos(p), math.sin(p), math.cos(y) * math.cos(p)])
    right = np.cross([0, 1.0, 0], fwd); right /= np.linalg.norm(right)
    up = np.cross(fwd, right)
    return right, up, fwd


def render(geo, tex, mats, view, center, ppu, size=(700, 420), hide=(), bg=(245, 245, 245)):
    """Orthographic z-buffered render; flat colour per face with simple directional shading."""
    global TEXW, TEXH
    TEXW, TEXH = geo['description']['texture_width'], geo['description']['texture_height']
    right, up, fwd = VIEWS[view] if isinstance(view, str) else view
    W, H = size
    zbuf = np.full((H, W), -np.inf)
    rgb = np.zeros((H, W, 3), np.uint8)
    light = np.array([0.4, 0.8, 0.45]); light /= np.linalg.norm(light)
    cache = {}
    for pts, face in quads(geo, mats, hide):
        n = np.cross(pts[1] - pts[0], pts[3] - pts[0])
        ln = np.linalg.norm(n)
        if ln < 1e-12: continue
        key = None if face is None else (tuple(face['uv']), tuple(face['uv_size']))
        if key not in cache: cache[key] = face_color(tex, face)
        col = cache[key]
        if col[3] < 10: continue
        c = np.clip(col[:3] * (0.55 + 0.45 * abs(n @ light) / ln), 0, 255).astype(np.uint8)
        q = pts - center
        sx = W / 2 + q @ right * ppu
        sy = H / 2 - q @ up * ppu
        dz = q @ fwd
        for tri in ((0, 1, 2), (0, 2, 3)):
            x, y, d = sx[list(tri)], sy[list(tri)], dz[list(tri)]
            x0, x1 = max(int(np.floor(x.min())), 0), min(int(np.ceil(x.max())), W - 1)
            y0, y1 = max(int(np.floor(y.min())), 0), min(int(np.ceil(y.max())), H - 1)
            if x0 > x1 or y0 > y1: continue
            den = (y[1] - y[2]) * (x[0] - x[2]) + (x[2] - x[1]) * (y[0] - y[2])
            if abs(den) < 1e-9: continue
            px, py = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            a = ((y[1] - y[2]) * (px - x[2]) + (x[2] - x[1]) * (py - y[2])) / den
            b = ((y[2] - y[0]) * (px - x[2]) + (x[0] - x[2]) * (py - y[2])) / den
            g = 1 - a - b
            inside = (a >= -1e-6) & (b >= -1e-6) & (g >= -1e-6)
            if not inside.any(): continue
            depth = a * d[0] + b * d[1] + g * d[2]
            zb = zbuf[y0:y1 + 1, x0:x1 + 1]
            win = inside & (depth > zb)
            zb[win] = depth[win]
            rgb[y0:y1 + 1, x0:x1 + 1][win] = c
    hit = np.isfinite(zbuf)
    if bg is None:
        out = np.zeros((H, W, 4), np.uint8); out[..., :3] = rgb; out[..., 3] = hit * 255
        return Image.fromarray(out, 'RGBA')
    out = np.where(hit[..., None], rgb, np.array(bg, np.uint8))
    return Image.fromarray(out.astype(np.uint8), 'RGB')


def load_geo(path):
    return json.load(open(path, encoding='utf-8'))['minecraft:geometry'][0]
