"""Fit few, large, rotated boxes to a mesh part (TaCZ guns are box-only).

profile_fit: for flat parts (frame, barrel, grip, hammer...). The side silhouette in (z, y) is split into
thickness bands (x extent); each band is covered greedily by the largest rectangles at the best of a few
angles, so curves become a handful of angled slabs instead of voxel steps. Thinner slabs may run across
thicker cells (they under-fill, never protrude), which keeps rectangles large.

lathe_fit: for round parts about a z-parallel axis (cylinder, ejector rod). Each run of near-constant radius
becomes a regular 2k-gon prism built from k strips rotated about the axis.
Boxes are returned as dicts: centre (x, y, z), size (x, y, z), rotation (rx, ry, rz) about the centre.
"""
import math

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage


def _largest_rect(m):
    """Largest all-True axis-aligned rectangle in 2D bool array m: (area, a0, a1, b0, b1), half-open."""
    na, nb = m.shape
    h = np.zeros(nb, int)
    best = (0, 0, 0, 0, 0)
    for i in range(na):
        h = np.where(m[i], h + 1, 0)
        stack = []
        for j in range(nb + 1):
            cur = h[j] if j < nb else 0
            start = j
            while stack and stack[-1][1] >= cur:
                s, hh = stack.pop()
                area = hh * (j - s)
                if area > best[0]:
                    best = (area, i - hh + 1, i + 1, s, j)
                start = s
            if cur > 0 and (not stack or stack[-1][1] < cur):
                stack.append((start, cur))
    return best


def _grow(allowed, a0, a1, b0, b1):
    na, nb = allowed.shape
    changed = True
    while changed:
        changed = False
        if a0 > 0 and allowed[a0 - 1, b0:b1].all(): a0 -= 1; changed = True
        if a1 < na and allowed[a1, b0:b1].all(): a1 += 1; changed = True
        if b0 > 0 and allowed[a0:a1, b0 - 1].all(): b0 -= 1; changed = True
        if b1 < nb and allowed[a0:a1, b1].all(): b1 += 1; changed = True
    return a0, a1, b0, b1


def _frame(cells, ang):
    """Rotated sampling frame covering `cells` (N,2 grid coords) for angle ang (radians)."""
    u = np.array([math.cos(ang), math.sin(ang)])
    v = np.array([-math.sin(ang), math.cos(ang)])
    pu, pv = cells @ u, cells @ v
    a = np.arange(math.floor(pu.min()) - 1, math.ceil(pu.max()) + 2)
    b = np.arange(math.floor(pv.min()) - 1, math.ceil(pv.max()) + 2)
    A, Bv = np.meshgrid(a, b, indexing='ij')
    pts = A[..., None] * u + Bv[..., None] * v           # grid coords (i, j) of each rotated sample
    return u, v, a, b, pts


def _sample(mask, pts):
    return ndimage.map_coordinates(mask.astype(np.uint8), [pts[..., 0], pts[..., 1]], order=0, cval=0) > 0


def _cover(target, allowed, res, min_side, max_boxes, coverage):
    """Greedy rotated rectangles inside `allowed` covering `target`; yields (u, v, ua, ub, va, vb, cells_inside)."""
    covered = np.zeros_like(target)
    total = target.sum()
    min_cells = (min_side / res) ** 2
    out = []
    for _ in range(max_boxes):
        left = target & ~covered
        if left.sum() <= (1 - coverage) * total or left.sum() < min_cells: break
        cells = np.argwhere(left).astype(float)
        c = cells - cells.mean(0)
        _, vecs = np.linalg.eigh(c.T @ c)
        base = math.atan2(vecs[1, -1], vecs[0, -1])
        angles = {round(base + d, 4) for d in np.radians([0, -6, 6, -15, 15, 30, -30])} | {0.0, math.pi / 2}
        best = None
        for ang in angles:
            u, v, a, b, pts = _frame(np.argwhere(allowed).astype(float), ang)
            L = _sample(left, pts)
            if not L.any(): continue
            area, a0, a1, b0, b1 = _largest_rect(L)
            if area == 0: continue
            a0, a1, b0, b1 = _grow(_sample(allowed, pts), a0, a1, b0, b1)
            score = L[a0:a1, b0:b1].sum()
            if best is None or score > best[0]:
                best = (score, u, v, a[a0] - 0.5, a[a1 - 1] + 0.5, b[b0] - 0.5, b[b1 - 1] + 0.5)
        if best is None or best[0] < min_cells * 0.5: break
        _, u, v, ua, ub, va, vb = best
        ac = np.argwhere(allowed).astype(float)
        pu, pv = ac @ u, ac @ v
        inside = ac[(pu >= ua) & (pu <= ub) & (pv >= va) & (pv <= vb)].astype(int)
        covered[tuple(inside.T)] = True
        covered &= target
        out.append((u, v, ua, ub, va, vb, inside))
    return out


def profile_fit(tris, samples, res, band=0.05, min_side=0.12, max_boxes=60, coverage=0.985, bevel=None, exclude=None):
    """tris (N,3,3) and samples (M,3) in model units. Returns list of boxes.

    bevel=None: thickness bands, one slab per rectangle. bevel=f: one region; each rectangle takes the median
    thickness of its cells and becomes two crossed slabs (full width/inset thickness, full thickness/inset
    width) so a rounded part reads as chamfered rather than terraced.
    exclude: triangles of a part that hides this one (eroded silhouette is removed), e.g. walnut over the frame.
    """
    zy = samples[:, [2, 1]]
    tzy = tris[:, :, [2, 1]]
    o = tzy.reshape(-1, 2).min(0) - 3 * res
    shape = tuple((np.ceil((tzy.reshape(-1, 2).max(0) - o) / res) + 4).astype(int))
    img = Image.new('L', (shape[0], shape[1]), 0)                   # PIL (x=i=z, y=j=y)
    dr = ImageDraw.Draw(img)
    for t in (tzy - o) / res:
        dr.polygon([tuple(p) for p in t], fill=1, outline=1)
    mask = np.asarray(img).T > 0                                     # [i(z), j(y)]
    if exclude is not None:
        ex = Image.new('L', (shape[0], shape[1]), 0)
        dx = ImageDraw.Draw(ex)
        for t in (exclude[:, :, [2, 1]] - o) / res:
            dx.polygon([tuple(p) for p in t], fill=1, outline=1)
        mask &= ~ndimage.binary_erosion(np.asarray(ex).T > 0, iterations=2)
    ij = np.clip(((zy - o) / res).astype(int), 0, np.array(shape) - 1)
    xmin = np.full(shape, np.inf); xmax = np.full(shape, -np.inf)
    np.minimum.at(xmin, (ij[:, 0], ij[:, 1]), samples[:, 0])
    np.maximum.at(xmax, (ij[:, 0], ij[:, 1]), samples[:, 0])
    known = np.isfinite(xmin)
    _, near = ndimage.distance_transform_edt(~known, return_indices=True)
    xmin = xmin[near[0], near[1]]; xmax = xmax[near[0], near[1]]
    xmin = ndimage.median_filter(xmin, 5); xmax = ndimage.median_filter(xmax, 5)

    def box(u, v, ua, ub, va, vb, x0, x1, dv=0.0, dx=0.0):
        ctr = ((ua + ub) / 2) * u + ((va + vb) / 2) * v              # grid coords
        zc, yc = o + (ctr + 0.5) * res
        return {'centre': ((x0 + x1) / 2, yc, zc), 'size': (x1 - x0 - 2 * dx, (vb - va) * res - 2 * dv, (ub - ua) * res),
                'rotation': (math.degrees(math.atan2(u[1], u[0])), 0.0, 0.0), 'long': 'z'}

    boxes = []
    if bevel:
        for u, v, ua, ub, va, vb, cells in _cover(mask, mask, res, min_side, max_boxes, coverage):
            x0 = float(np.median(xmin[tuple(cells.T)])); x1 = float(np.median(xmax[tuple(cells.T)]))
            h = (vb - va) * res
            cut = bevel * min(x1 - x0, h)
            boxes.append(box(u, v, ua, ub, va, vb, x0, x1, dx=cut))
            boxes.append(box(u, v, ua, ub, va, vb, x0, x1, dv=cut))
        return boxes
    qmin = np.floor(xmin / band) * band; qmax = np.ceil(xmax / band) * band
    keys = sorted({(a, b) for a, b in zip(qmin[mask].round(4), qmax[mask].round(4))}, key=lambda k: k[1] - k[0])
    for k0, k1 in keys:
        target = mask & np.isclose(qmin, k0) & np.isclose(qmax, k1)
        if target.sum() < (min_side / res) ** 2 * 0.5: continue
        allowed = mask & (qmin <= k0 + 1e-6) & (qmax >= k1 - 1e-6)
        for u, v, ua, ub, va, vb, _ in _cover(target, allowed, res, min_side, max_boxes, coverage):
            boxes.append(box(u, v, ua, ub, va, vb, k0, k1))
    return boxes


def lathe_fit(samples, axis_xy, res=0.03, k=6, tol=0.05, min_len=0.05, pct=97):
    """Round part about the z-parallel axis through axis_xy: 2k-gon prisms per constant-radius run."""
    d = np.hypot(samples[:, 0] - axis_xy[0], samples[:, 1] - axis_xy[1])
    z = samples[:, 2]
    zs = np.floor(z / res).astype(int)
    slices = []
    for s in range(zs.min(), zs.max() + 1):
        sel = zs == s
        if sel.any(): slices.append((s, np.percentile(d[sel], pct)))
    runs, cur = [], None
    for s, r in slices:
        if cur and abs(r - cur[2]) <= tol and s == cur[1] + 1:
            cur = (cur[0], s, max(cur[2], r) if abs(r - cur[2]) < tol / 2 else (cur[2] + r) / 2)
        else:
            if cur: runs.append(cur)
            cur = (s, s, r)
    runs.append(cur)
    boxes = []
    for s0, s1, r in runs:
        z0, z1 = s0 * res, (s1 + 1) * res
        if z1 - z0 < min_len: continue
        apo = r * math.cos(math.pi / (2 * k))                         # polygon between inscribed and circumscribed
        half_side = apo * math.tan(math.pi / (2 * k))
        for i in range(k):
            boxes.append({'centre': (axis_xy[0], axis_xy[1], (z0 + z1) / 2),
                          'size': (2 * half_side, 2 * apo, z1 - z0),
                          'rotation': (0.0, 0.0, 180.0 * i / k), 'long': 'z', 'strip': i})
    return boxes
