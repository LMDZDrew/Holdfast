"""Bake a per-face texture atlas for TaCZ box geometry from the source mesh.

UV convention read from TaCZ 1.1.8 (BedrockModel.convertOrigin, BedrockCubePerFace, BedrockPolygon,
FaceUVsItem.getFace): faces are named as in the Bedrock JSON, but TaCZ looks up JSON "west" for the +x side and
"down" for the bottom. On every face u runs from uv to uv+uv_size across the face and v runs downward:
  west (+x)  u: z0 -> z1   v: top -> bottom      east (-x)  u: z1 -> z0   v: top -> bottom
  north (-z) u: x0 -> x1   v: top -> bottom      south (+z) u: x1 -> x0   v: top -> bottom
  up (+y)    u: x0 -> x1   v: z1 -> z0           down (-y)  u: x0 -> x1   v: z0 -> z1
"""
import math

import numpy as np
from scipy.spatial import cKDTree

import preview_geo as P

NORMAL = {'west': (1, 0, 0), 'east': (-1, 0, 0), 'north': (0, 0, -1), 'south': (0, 0, 1),
          'up': (0, 1, 0), 'down': (0, -1, 0)}
SPAN = {'west': (2, 1), 'east': (2, 1), 'north': (0, 1), 'south': (0, 1), 'up': (0, 2), 'down': (0, 2)}


def face_local(cube, face, s, t):
    """Points (box-local, before rotation) at face coordinates s (along u) and t (along v) in [0,1]."""
    o = np.array(cube['origin'], float); z = np.array(cube['size'], float)
    x0, y0, z0 = o; x1, y1, z1 = o + z
    s, t = np.asarray(s, float), np.asarray(t, float)
    one = np.ones_like(s * t)
    if face == 'west':  p = (x1 * one, y1 - t * z[1], z0 + s * z[2])
    elif face == 'east': p = (x0 * one, y1 - t * z[1], z1 - s * z[2])
    elif face == 'north': p = (x0 + s * z[0], y1 - t * z[1], z0 * one)
    elif face == 'south': p = (x1 - s * z[0], y1 - t * z[1], z1 * one)
    elif face == 'up':   p = (x0 + s * z[0], y1 * one, z1 - t * z[2])
    else:                p = (x0 + s * z[0], y0 * one, z0 + t * z[2])
    return np.stack(p, -1)


def to_world(cube, pts):
    if 'rotation' not in cube: return pts
    c = np.array(cube['pivot'], float)
    return (pts - c) @ P.rot(cube['rotation']).T + c


def world_normal(cube, face):
    n = np.array(NORMAL[face], float)
    return P.rot(cube['rotation']) @ n if 'rotation' in cube else n


def inside_any(pts, cubes):
    """Bool per point: inside at least one of the (rotated) cubes."""
    hit = np.zeros(len(pts), bool)
    for c in cubes:
        q = pts
        if 'rotation' in c:
            pc = np.array(c['pivot'], float)
            q = (pts - pc) @ P.rot(c['rotation']) + pc         # inverse rotation (orthonormal)
        o = np.array(c['origin'], float); z = np.array(c['size'], float)
        hit |= ((q >= o - 1e-4) & (q <= o + z + 1e-4)).all(1)
    return hit


def hidden(cube, face, others):
    """Face fully covered by other cubes of the same bone (tested just outside the face)."""
    s, t = np.meshgrid([0.08, 0.5, 0.92], [0.08, 0.5, 0.92])
    pts = to_world(cube, face_local(cube, face, s.ravel(), t.ravel())) + world_normal(cube, face) * 0.006
    return inside_any(pts, others).all()


def pack(sizes, width, y0):
    """Shelf packing of (w, h) rects (px) into a strip `width` wide starting at row y0. Returns positions, height."""
    order = sorted(range(len(sizes)), key=lambda i: -sizes[i][1])
    pos = [None] * len(sizes)
    x = y = 0; shelf = 0; y = y0
    for i in order:
        w, h = sizes[i]
        if x + w > width:
            x = 0; y += shelf; shelf = 0
        pos[i] = (x, y)
        x += w; shelf = max(shelf, h)
    return pos, y + shelf


class Source:
    """Nearest-surface lookup of the source mesh's albedo and AO per part (model coordinates)."""

    def __init__(self, parts_model, albedo, ao, spacing):
        self.trees = {}
        self.albedo, self.ao = albedo, ao
        for name, (tris, uvs) in parts_model.items():
            pts, tuv = _samples(tris, uvs, spacing)
            self.trees[name] = (cKDTree(pts), tuv)

    def lookup(self, part, pts):
        tree, tuv = self.trees[part]
        _, i = tree.query(pts)
        uv = tuv[i]
        H, W = self.albedo.shape[:2]
        x = np.clip((uv[:, 0] % 1.0) * W, 0, W - 1).astype(int)
        y = np.clip((1 - uv[:, 1] % 1.0) * H, 0, H - 1).astype(int)
        return self.albedo[y, x].astype(float), self.ao[y, x].astype(float) / 255.0


def _samples(tris, uvs, h):
    pts, tuv = [], []
    edges = np.linalg.norm(tris - np.roll(tris, 1, axis=1), axis=2).max(1)
    steps = np.maximum(1, np.ceil(edges / h)).astype(int)
    for n in np.unique(steps):
        sel = steps == n
        i, j = np.meshgrid(np.arange(n + 1), np.arange(n + 1), indexing='ij')
        keep = i + j <= n
        b = np.stack([i[keep], j[keep]], 1) / n
        w = np.column_stack([1 - b.sum(1), b])
        pts.append(np.einsum('kc,tcd->tkd', w, tris[sel]).reshape(-1, 3))
        tuv.append(np.einsum('kc,tcd->tkd', w, uvs[sel]).reshape(-1, 2))
    return np.vstack(pts), np.vstack(tuv)


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


NICKEL_HI = np.array([246, 247, 250.0])
NICKEL_MID = np.array([206, 209, 215.0])
NICKEL_LO = np.array([128, 131, 138.0])


def shade_nickel(n, height, ao, detail, flute):
    """Polished nickel: studio reflection by surface direction plus a horizon line on the sides.

    n: (N,3) world normals; height: 0 at the part's top .. 1 at its bottom; ao 0..1; detail: source albedo
    luminance relative to the part (engraving and edges), flute: darker recessed faces.
    """
    ny = n[:, 1]
    side = 1 - np.abs(ny)
    horizon = smoothstep(0.38, 0.5, height)                          # sky above, ground below on the sides
    sides = NICKEL_HI * (1 - horizon[:, None]) * 0.97 + NICKEL_LO * horizon[:, None] * 1.05
    sides = sides + (NICKEL_MID - sides) * smoothstep(0.6, 1.0, height)[:, None] * 0.6
    top = NICKEL_HI
    bottom = NICKEL_LO * 0.85
    col = np.where(ny[:, None] > 0, top * ny[:, None] + sides * side[:, None],
                   bottom * -ny[:, None] + sides * side[:, None])
    col = col * (0.62 + 0.38 * ao[:, None]) + detail[:, None] * 0.45
    if flute: col = col * 0.78
    return np.clip(col, 0, 255)


def shade_walnut(albedo, ao):
    return np.clip(albedo * (0.75 + 0.25 * ao[:, None]) * 1.08, 0, 255)


def bake(entries, source, ppu, atlas_w, swatch_rows=32, edge=0.93):
    """entries: list of (bone_cubes_of_same_bone, cube, part, material). Fills cube['uv']; returns (image, height).

    material: 'nickel', 'flute' or 'walnut'. Hidden faces get a 1 px patch of the face's mean colour.
    """
    faces, sizes = [], []
    part_box = {}
    for others, cube, part, mat in entries:
        for f in NORMAL:
            if hidden(cube, f, [c for c in others if c is not cube]):
                w = h = 2
            else:
                a, b = SPAN[f]
                w = max(2, int(math.ceil(cube['size'][a] * ppu)))
                h = max(2, int(math.ceil(cube['size'][b] * ppu)))
            faces.append((cube, part, mat, f)); sizes.append((w + 2, h + 2))   # 1 px gutter each side
    pos, height = pack(sizes, atlas_w, swatch_rows)
    img = np.zeros((height, atlas_w, 4), np.uint8)
    # part vertical extents (rest pose) for the horizon line
    for others, cube, part, mat in entries:
        corners = to_world(cube, np.array([[cube['origin'][0] + cube['size'][0] * (i & 1),
                                            cube['origin'][1] + cube['size'][1] * (i >> 1 & 1),
                                            cube['origin'][2] + cube['size'][2] * (i >> 2 & 1)] for i in range(8)]))
        lo, hi = part_box.get(part, (np.inf, -np.inf))
        part_box[part] = (min(lo, corners[:, 1].min()), max(hi, corners[:, 1].max()))
    lum_ref = {}
    for (cube, part, mat, f), (x, y), (w, h) in zip(faces, pos, sizes):
        w -= 2; h -= 2
        s, t = np.meshgrid((np.arange(w) + 0.5) / w, (np.arange(h) + 0.5) / h)
        pts = to_world(cube, face_local(cube, f, s.ravel(), t.ravel()))
        n = np.repeat(world_normal(cube, f)[None], len(pts), 0)
        alb, ao = source.lookup(part, pts)
        if mat == 'walnut':
            col = shade_walnut(alb, ao)
        else:
            lum = alb @ [0.3, 0.59, 0.11]
            if part not in lum_ref:
                lum_ref[part] = float(np.median(source.lookup(part, source.trees[part][0].data[::7])[0] @ [0.3, 0.59, 0.11]))
            lo, hi = part_box[part]
            height_ = (hi - pts[:, 1]) / max(hi - lo, 1e-6)
            col = shade_nickel(n, height_, ao, np.clip(lum - lum_ref[part], -60, 25), mat == 'flute')
        col = col.reshape(h, w, 3)
        if w > 3 and h > 3:
            col[0, :] *= edge; col[-1, :] *= edge; col[:, 0] *= edge; col[:, -1] *= edge
        block = np.clip(col, 0, 255).astype(np.uint8)
        block = np.pad(block, ((1, 1), (1, 1), (0, 0)), mode='edge')     # gutter repeats the border
        img[y:y + h + 2, x:x + w + 2, :3] = block
        img[y:y + h + 2, x:x + w + 2, 3] = 255
        cube['uv'][f] = {'uv': [x + 1, y + 1], 'uv_size': [w, h]}
    return img, height
