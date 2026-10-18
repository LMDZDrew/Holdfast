"""Build Michonne's Katana (holdfast_core:michonne_katana): GeckoLib item geometry, baked texture and icon inputs.

Input (not committed): katana_low_poly.glb, "Katana (low poly)" by Andrey 3D, CC-BY-4.0
(https://sketchfab.com/3d-models/katana-low-poly-26e4d48383db4ab4893bc6424383cd61).
Usage: python build_michonne_katana.py KATANA_GLB OUT_DIR [CORE_RESOURCES]
       python build_michonne_katana.py icon ICON_RENDER CORE_RESOURCES
With CORE_RESOURCES (core/src/main/resources) the geo, texture and empty animation file are written into the mod.
The inventory icon is a Blender render of the built box model (art/boxmodel/render_geo_blender.py, view
'diagonal'), reduced here to the 64x64 TaCZ-style item/michonne_katana_icon texture.

The baseline is a generic katana; Michonne's fittings come from Andrew's reference renders and are painted here:
white tsuka-ito over black with diamond windows, a round steel tsuba with a raised rim and the triquetra, gold
habaki and seppa, steel fuchi/kashira, and the crescent knot engraved on the blade near the habaki.

Placement follows the existing katana (art/characters/source/gen_core_assets.py): the authoring rig's hand frame
with the blade along -Z from the fist, edge -Y, guard face at z -2.5 and tip at z -26, converted to item space by
pa_item.model_point and shrunk by 0.5 for the display scale 2.0. Held, swung and in executions it therefore sits
exactly where the katana clips were solved. GeckoLib draws cube x mirrored and the item origin at (8, 8.16, 8), so
Bedrock geometry is (8 - X, Y - 8.16, Z - 8) of item space.
"""
import json
import math
import shutil
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'art' / 'boxmodel'))
sys.path.insert(0, str(ROOT / 'art' / 'characters' / 'source'))
import bake  # noqa: E402
import box_fit  # noqa: E402
import gltf_binary  # noqa: E402
import pa_item  # noqa: E402
import preview_geo as P  # noqa: E402

MODID, NAME = 'holdfast_core', 'michonne_katana'
FX, FY = -6.0, 12.5                 # fist (Bedrock rig), as gen_core_assets.FX/FY
RIG_GUARD_Z, RIG_TIP_Z = -2.5, -26.0  # existing katana: grip ends at the guard face z -2.5, tip at z -26
K = 0.5                             # gen_core_assets shrink; the item display scale 2.0 restores it
PPU = 150                           # atlas px per item-space px (the blade is ~0.4 px wide)
ATLAS_W = 2048

# Baseline part -> (fit, options, material). Names are the GLB's own (Sketchfab), which do not match their
# content: Same is the wrapped handle, Tsuba the core under the wrap, Tsuba1 the guard, Hamon the habaki.
# sweep: the curved blade as segments; lathe: guard disc; rounded: straight segments of rounded-octagon section
# sized from the part (the braided wrap mesh itself fits badly, so its outer size carries a painted wrap).
PARTS = {
    'Blade_low': ('sweep', dict(segments=26), 'blade'),
    'Hamon_low': ('rounded', dict(segments=1, cut=0.2), 'gold'),
    'Tsuba1_low': ('lathe', dict(k=8), 'tsuba'),
    'Tsuka1_low': ('rounded', dict(segments=1, cut=0.3), 'gold'),
    'Tsuka2_low': ('rounded', dict(segments=1, cut=0.3), 'gold'),
    'Tsuka3_low': ('rounded', dict(segments=1, cut=0.3), 'steel'),
    'Same2_low': ('rounded', dict(segments=1, cut=0.3), 'steel'),
    'Same_low': ('rounded', dict(segments=8, cut=0.3), 'wrap'),
    'Same1_low': ('rounded', dict(segments=1, cut=0.3), 'steel'),
    'Kasira_low': ('rounded', dict(segments=2, cut=0.35), 'steel'),
}
SKIP = {'Tsuba_low'}                # handle core, fully hidden inside the wrap


def item_affine():
    """Rig (Bedrock px) -> item-space px, as gen_core_assets.elements with k = 0.5."""
    base = np.array([FX, FY, 0.0])
    pts = [base, base + [1, 0, 0], base + [0, 1, 0], base + [0, 0, 1]]
    m = [(pa_item.model_point('rightItem', p) - 8.0) * K + 8.0 for p in pts]
    A = np.column_stack([m[1] - m[0], m[2] - m[0], m[3] - m[0]])
    return A, m[0] - A @ base


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def shade_metal(n, base, hi, lo):
    """Polished metal: bright facing up, darker facing down, a horizon on the sides."""
    ny = n[:, 1:2]
    side = base + (hi - base) * 0.35
    return np.where(ny > 0, hi * ny + side * (1 - ny), lo * -ny + side * (1 + ny))


STEEL = (np.array([196, 199, 205.0]), np.array([240, 242, 246.0]), np.array([120, 123, 130.0]))
GUARD = (np.array([150, 153, 160.0]), np.array([196, 199, 206.0]), np.array([92, 95, 102.0]))
BRIGHT = (np.array([214, 217, 222.0]), np.array([248, 249, 251.0]), np.array([140, 143, 150.0]))
GOLD = (np.array([205, 168, 78.0]), np.array([250, 222, 138.0]), np.array([128, 96, 36.0]))


def blade_frame(geo_tris):
    """Edge and spine z per y along the curved blade, and the half-thickness by width fraction (0 edge, 1 spine)."""
    bt = geo_tris['Blade_low']
    blade = bake._samples(bt, np.zeros(bt.shape[:2] + (2,)), 0.01)[0]
    y0, y1 = blade[:, 1].min(), blade[:, 1].max()               # blade runs along +y from the habaki
    ys = np.linspace(y0, y1, 64)
    edge_z, spine_z, votes = [], [], 0
    slices = []
    for a, b in zip(ys[:-1], ys[1:]):
        s = blade[(blade[:, 1] >= a) & (blade[:, 1] <= b + 1e-6)]
        if len(s) < 4: s = blade[np.argsort(np.abs(blade[:, 1] - (a + b) / 2))[:40]]
        lo, hi = s[:, 2].min(), s[:, 2].max()
        thick_lo = np.ptp(s[s[:, 2] < lo + 0.02][:, 0]); thick_hi = np.ptp(s[s[:, 2] > hi - 0.02][:, 0])
        votes += 1 if thick_lo < thick_hi else -1
        slices.append((lo, hi))
    for lo, hi in slices:
        edge, spine = (lo, hi) if votes > 0 else (hi, lo)
        edge_z.append(edge); spine_z.append(spine)
    yc = (ys[:-1] + ys[1:]) / 2
    edge_z, spine_z = np.array(edge_z), np.array(spine_z)
    # Smooth curves (the low-poly blade's slices are noisy near the point): cubic fits over the body of the
    # blade, then the kissaki tapers the edge straight up to the spine at the tip.
    t = (yc - y0) / (y1 - y0)
    body = t < 0.9
    spine_z = np.polyval(np.polyfit(yc[body], spine_z[body], 3), yc)
    edge_fit = np.polyval(np.polyfit(yc[body], edge_z[body], 3), yc)
    k0 = np.searchsorted(t, 0.9)
    edge_z = np.where(body, edge_fit, edge_fit[k0 - 1] + (spine_z - edge_fit[k0 - 1]) * (t - t[k0 - 1]) / (1 - t[k0 - 1]))
    yc = np.concatenate([[y0], yc, [y1]])
    spine_z = np.concatenate([[spine_z[0]], spine_z, [spine_z[-1]]])
    edge_z = np.concatenate([[edge_z[0]], edge_z, [spine_z[-1]]])
    e = np.interp(blade[:, 1], yc, edge_z); sp = np.interp(blade[:, 1], yc, spine_z)
    w = np.clip((blade[:, 2] - e) / np.where(np.abs(sp - e) < 1e-6, 1e-6, sp - e), 0, 1)
    xc = (blade[:, 0].min() + blade[:, 0].max()) / 2
    bins = np.linspace(0, 1, 11)
    half = np.array([np.abs(blade[(w >= a) & (w <= b), 0] - xc).max() if ((w >= a) & (w <= b)).any() else 0.0
                     for a, b in zip(bins[:-1], bins[1:])])
    return dict(y0=y0, y1=y1, yc=yc, edge_z=edge_z, spine_z=spine_z, xc=xc,
                half=lambda f: float(np.interp(f, (bins[:-1] + bins[1:]) / 2, np.maximum.accumulate(half))))


def blade_sweep(fr, segments=28, layers=((0.0, 0.15), (0.34, 0.5), (0.67, 0.85))):
    """The curved blade as short segments following its centre line; per segment nested slabs from the edge
    (thin) to the spine (thick), so the cross-section is a stepped wedge."""
    y0, y1 = fr['y0'], fr['y1']
    kissaki = y0 + 0.88 * (y1 - y0)                               # denser segments where the point tapers
    ys = np.concatenate([np.linspace(y0, kissaki, segments + 1)[:-1], np.linspace(kissaki, y1, 25)])
    e = np.interp(ys, fr['yc'], fr['edge_z']); sp = np.interp(ys, fr['yc'], fr['spine_z'])
    boxes = []
    for i in range(len(ys) - 1):
        a, b = i, i + 1
        dz = (e[b] + sp[b]) / 2 - (e[a] + sp[a]) / 2
        dy = ys[b] - ys[a]
        L = math.hypot(dz, dy)
        uz, uy = dz / L, dy / L
        width = abs(((sp[a] - e[a]) + (sp[b] - e[b])) / 2) * abs(uy)      # perpendicular to the segment
        if width < 1e-4: continue
        em, sm = (e[a] + e[b]) / 2, (sp[a] + sp[b]) / 2
        vz, vy = -uy, uz                                                  # across the segment, in (z, y)
        sgn = 1.0 if (sm - em) * vz >= 0 else -1.0
        for w0, wt in layers:
            off = ((w0 + 1) / 2 - 0.5) * width * sgn                      # layer centre from the segment centre
            zc = (em + sm) / 2 + off * vz
            yc_ = (ys[a] + ys[b]) / 2 + off * vy
            th = 2 * fr['half'](wt)
            if th < 2e-3: th = 2e-3
            boxes.append({'centre': (fr['xc'], yc_, zc), 'size': (th, width * (1 - w0), L + 0.004),
                          'rotation': (math.degrees(math.atan2(uy, uz)), 0.0, 0.0)})
    return boxes


def rounded_sweep(tris, segments, cut):
    """A straight part along y as segments of rounded-octagon section: per segment two crossed boxes sized from
    the part's extent in that slice (98th percentiles about its centre line)."""
    pts = bake._samples(tris, np.zeros(tris.shape[:2] + (2,)), 0.01)[0]
    ys = np.linspace(pts[:, 1].min(), pts[:, 1].max(), segments + 1)
    boxes = []
    for a, b in zip(ys[:-1], ys[1:]):
        sl = pts[(pts[:, 1] >= a - 1e-6) & (pts[:, 1] <= b + 1e-6)]
        if len(sl) < 8: continue
        cx = (np.percentile(sl[:, 0], 1) + np.percentile(sl[:, 0], 99)) / 2
        cz = (np.percentile(sl[:, 2], 1) + np.percentile(sl[:, 2], 99)) / 2
        hx = np.percentile(np.abs(sl[:, 0] - cx), 98); hz = np.percentile(np.abs(sl[:, 2] - cz), 98)
        c = cut * min(hx, hz)
        for sx, sz in ((hx - c, hz), (hx, hz - c)):
            boxes.append({'centre': (cx, (a + b) / 2, cz), 'size': (2 * sx, b - a, 2 * sz), 'rotation': (0.0, 0.0, 0.0)})
    return boxes


def make_painters(geo_tris, fr):
    """Material painters in Bedrock geo space, built from the transformed baseline parts."""
    y0, y1, yc, edge_z, spine_z = fr['y0'], fr['y1'], fr['yc'], fr['edge_z'], fr['spine_z']

    def blade_paint(p, n):
        e = np.interp(p[:, 1], yc, edge_z); sp = np.interp(p[:, 1], yc, spine_z)
        w = np.clip((p[:, 2] - e) / np.where(np.abs(sp - e) < 1e-6, 1e-6, sp - e), 0, 1)   # 0 edge .. 1 spine
        col = shade_metal(n, *STEEL)
        hamon = 1 - smoothstep(0.18, 0.32, w + 0.03 * np.sin(p[:, 1] * 9))                  # bright tempered edge
        col = col + (np.array([250, 251, 253.0]) - col) * (hamon[:, None] * 0.55)
        col = col * (1 - 0.18 * np.exp(-((w - 0.72) / 0.03) ** 2))[:, None]                 # shinogi line
        col = col * np.where(w > 0.72, 0.94, 1.0)[:, None]
        flat = np.abs(n[:, 0]) > 0.5
        width = np.maximum(np.abs(sp - e), 1e-6)
        mark = crescent_knot((p[:, 1] - (y0 + 0.07 * (y1 - y0))) / (0.5 * width), (w - 0.5) * 2.0)
        col = np.where((flat & mark)[:, None], col * 0.45, col)
        return col

    handle = geo_tris['Same_low'].reshape(-1, 3)
    hy0, hy1 = handle[:, 1].min(), handle[:, 1].max()
    hc = handle.mean(0)
    hx, hz = np.ptp(handle[:, 0]) / 2, np.ptp(handle[:, 2]) / 2
    diamonds = 9

    def wrap_paint(p, n):
        along = (p[:, 1] - hy0) / (hy1 - hy0) * diamonds
        u = (along % 1.0) - 0.5                                    # -0.5..0.5 within one wrap crossing
        across = np.where(np.abs(n[:, 0]) > 0.5, (p[:, 2] - hc[2]) / hz, (p[:, 0] - hc[0]) / hx)
        window = np.abs(u) / 0.5 + np.abs(across) / 0.62 < 1.0     # black diamond where the ito parts
        twist = 0.88 + 0.12 * np.abs(np.sin((along + across * 0.5) * math.pi))
        white = np.array([236, 236, 232.0]) * twist[:, None]
        shade = 0.82 + 0.18 * (n[:, 1:2] + 1) / 2
        col = np.where(window[:, None], np.array([22, 22, 24.0]), white * shade)
        menuki = window & (np.abs(u) < 0.12) & (np.abs(across) < 0.1) & (np.abs(along - diamonds / 2) < 1.2)
        return np.where(menuki[:, None], GOLD[0], col)

    guard = geo_tris['Tsuba1_low'].reshape(-1, 3)
    gc = (guard.min(0) + guard.max(0)) / 2
    gr = max(np.ptp(guard[:, 0]), np.ptp(guard[:, 2])) / 2

    def tsuba_paint(p, n):
        col = shade_metal(n, *GUARD)                                   # brushed face, darker than the fittings
        if not len(p): return col
        r = np.hypot(p[:, 0] - gc[0], p[:, 2] - gc[2]) / gr
        face = np.abs(n[:, 1]) > 0.5
        rim = shade_metal(n, *STEEL)
        col = np.where((face & (r > 0.84))[:, None] | (~face)[:, None], rim, col)              # raised polished rim
        col = np.where((face & (np.abs(r - 0.84) < 0.025))[:, None], col * 0.55, col)
        u, v = (p[:, 0] - gc[0]) / gr, (p[:, 2] - gc[2]) / gr
        outline = triquetra(u, v, line=0.06)
        knot = triquetra(u, v, line=0.032)
        col = np.where((face & outline)[:, None], col * 0.45, col)                                # recess around the knot
        col = np.where((face & knot)[:, None], BRIGHT[1], col)                                    # polished knot
        hole = face & (np.abs((p[:, 0] - gc[0]) / gr) < 0.12) & (np.abs((p[:, 2] - gc[2]) / gr) < 0.3)
        return np.where(hole[:, None], GOLD[0] * 0.9, col)

    return {'blade': blade_paint, 'wrap': wrap_paint, 'tsuba': tsuba_paint,
            'gold': lambda p, n: shade_metal(n, *GOLD), 'steel': lambda p, n: shade_metal(n, *STEEL)}


def triquetra(x, y, line=0.035):
    """Triquetra outline (three interlaced lobes) in the unit disc: arcs of three circles inside one another."""
    hit = np.zeros(len(x), bool)
    R, d = 0.5, 0.29
    cs = [(d * math.cos(a), d * math.sin(a)) for a in (math.pi / 2, math.pi / 2 + 2 * math.pi / 3, math.pi / 2 + 4 * math.pi / 3)]
    for i, (cx, cy) in enumerate(cs):
        on = np.abs(np.hypot(x - cx, y - cy) - R) < line
        inside_other = np.zeros(len(x), bool)
        for j, (ox, oy) in enumerate(cs):
            if j != i: inside_other |= np.hypot(x - ox, y - oy) < R
        hit |= on & inside_other
    hit |= np.abs(np.hypot(x, y) - 0.36) < line * 0.8                                         # interlaced ring
    return hit


def crescent_knot(x, y, line=0.09):
    """Three interlocking crescents (the blade engraving) in a unit box."""
    hit = np.zeros(len(x), bool)
    for k in range(3):
        a = math.pi / 2 + k * 2 * math.pi / 3
        cx, cy = 0.28 * math.cos(a), 0.28 * math.sin(a)
        outer = np.abs(np.hypot(x - cx, y - cy) - 0.42) < line
        inner = np.hypot(x - cx - 0.18 * math.cos(a), y - cy - 0.18 * math.sin(a)) < 0.38
        hit |= outer & ~inner
    return hit & (np.abs(x) < 1) & (np.abs(y) < 1)


def main(glb, out_dir, core_res=None):
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    parts, _, _ = gltf_binary.load(Path(glb).read_bytes())
    if set(parts) != set(PARTS) | SKIP:
        raise SystemExit('unexpected baseline parts: %s' % sorted(set(parts) ^ (set(PARTS) | SKIP)))
    allp = np.vstack([t.reshape(-1, 3) for t, _ in parts.values()])
    guard = parts['Tsuba1_low'][0].reshape(-1, 3)
    z_guard, z_tip, z_pommel = guard[:, 2].min(), allp[:, 2].max(), allp[:, 2].min()
    s = (RIG_GUARD_Z - RIG_TIP_Z) / (z_tip - z_guard)                # rig px per baseline unit (blade fit)
    A, b = item_affine()

    def to_geo(p):
        rig = np.stack([FX - p[..., 0] * s, FY + p[..., 1] * s, RIG_GUARD_Z - (p[..., 2] - z_guard) * s], -1)
        item = rig @ A.T + b
        return np.stack([8.0 - item[..., 0], item[..., 1] - 8.16, item[..., 2] - 8.0], -1)

    geo_tris = {n: to_geo(t) for n, (t, _) in parts.items()}
    fr = blade_frame(geo_tris)
    painters = make_painters(geo_tris, fr)
    cubes, baked, stats = [], [], {}
    for name, (mode, opt, mat) in PARTS.items():
        tris = geo_tris[name]
        res = opt.get('res', 0.004)
        samples = bake._samples(tris, np.zeros(tris.shape[:2] + (2,)), res * 0.35)[0]
        if mode == 'sweep':
            boxes = blade_sweep(fr, opt['segments'])
        elif mode == 'rounded':
            boxes = rounded_sweep(tris, opt['segments'], opt['cut'])
        elif mode == 'lathe':
            # lathe_fit works about a z-parallel axis; the guard's axis is item y, so swap y and z around it
            sw = samples[:, [0, 2, 1]]
            centre = (sw[:, 0].min() + sw[:, 0].max()) / 2, (sw[:, 1].min() + sw[:, 1].max()) / 2
            boxes = []
            for bx in box_fit.lathe_fit(sw, centre, res=0.004, k=opt['k'], tol=1.0, min_len=0.004, pct=99.5):
                cx, cy, cz = bx['centre']; sx, sy, sz = bx['size']
                boxes.append({'centre': (cx, cz, cy), 'size': (sx, sz, sy), 'rotation': (0.0, bx['rotation'][2], 0.0)})
        else:
            boxes = box_fit.profile_fit(tris, samples, **opt)
        part_cubes = [bake_cube(bx) for bx in boxes if min(bx['size']) > 1e-4]
        cubes += part_cubes
        baked += [(c, name, painters[mat]) for c in part_cubes]
        stats[name] = len(part_cubes)
    atlas, used = bake.bake([(cubes, c, part, m) for c, part, m in baked], None, PPU, ATLAS_W, swatch_rows=0,
                            edge=1.0)
    tex_h = 1 << int(math.ceil(math.log2(max(used, 64))))
    full = np.zeros((tex_h, ATLAS_W, 4), np.uint8)
    full[:used] = atlas
    tex = Image.fromarray(full, 'RGBA')
    allg = np.vstack([t.reshape(-1, 3) for t in geo_tris.values()])
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.%s' % NAME, 'texture_width': ATLAS_W, 'texture_height': tex_h,
                        'visible_bounds_width': 4, 'visible_bounds_height': 3,
                        'visible_bounds_offset': [0, 1, 0]},
        'bones': [{'name': 'katana', 'pivot': [0, 0, 0], 'cubes': cubes}]}]}
    (out_dir / 'geo').mkdir(exist_ok=True)
    (out_dir / 'geo' / ('%s.geo.json' % NAME)).write_text(json.dumps(geo, separators=(',', ':')) + '\n')
    tex.save(out_dir / ('%s.png' % NAME))
    anim = {'format_version': '1.8.0', 'animations': {}}
    report = {'baseline_units_to_rig_px': s, 'boxes': len(cubes), 'per_part': stats, 'texture': [ATLAS_W, tex_h],
              'geo_bounds': [allg.min(0).round(3).tolist(), allg.max(0).round(3).tolist()],
              'pommel_rig_z': float(RIG_GUARD_Z - (z_pommel - z_guard) * s)}
    (out_dir / 'build_report.json').write_text(json.dumps(report, indent=1) + '\n')
    if core_res:
        a = Path(core_res) / 'assets' / MODID
        for sub, src, dst in (('geo/item', out_dir / 'geo' / ('%s.geo.json' % NAME), '%s.geo.json' % NAME),
                              ('textures/item', out_dir / ('%s.png' % NAME), '%s.png' % NAME)):
            (a / sub).mkdir(parents=True, exist_ok=True)
            shutil.copyfile(src, a / sub / dst)
        (a / 'animations' / 'item').mkdir(parents=True, exist_ok=True)
        (a / 'animations' / 'item' / ('%s.animation.json' % NAME)).write_text(json.dumps(anim, indent=1) + '\n')
    preview(geo, tex, out_dir, geo_tris)
    print(json.dumps(report, indent=1))


def bake_cube(bx):
    c, s = np.array(bx['centre'], float), np.array(bx['size'], float)
    cube = {'origin': [round(float(v), 5) for v in c - s / 2], 'size': [round(float(v), 5) for v in s], 'uv': {}}
    if any(abs(r) > 1e-6 for r in bx['rotation']):
        cube['pivot'] = [round(float(v), 5) for v in c]
        cube['rotation'] = [round(float(r), 3) for r in bx['rotation']]
    return cube


def preview(geo, tex, out_dir, geo_tris):
    """Flat-colour preview (face-average colours); the textured check is render_geo_blender.py."""
    g = geo['minecraft:geometry'][0]
    m = P.pose(g)
    allg = np.vstack([t.reshape(-1, 3) for t in geo_tris.values()])
    ctr = (allg.min(0) + allg.max(0)) / 2
    gc = geo_tris['Tsuba1_low'].reshape(-1, 3).mean(0)
    hc = geo_tris['Same_low'].reshape(-1, 3).mean(0)
    side = P.render(g, tex, m, (np.array([0, 1.0, 0]), np.array([0, 0, 1.0]), np.array([1.0, 0, 0])), ctr, 85, (1400, 360))
    guard = P.render(g, tex, m, P.view34(15, -60), gc, 260, (700, 500))
    hilt = P.render(g, tex, m, P.view34(70, 15), hc, 160, (700, 500))
    out = Image.new('RGB', (1400, 860), 'white')
    out.paste(side, (0, 0)); out.paste(guard, (0, 360)); out.paste(hilt, (700, 360))
    out.save(out_dir / 'preview.png')


def install_icon(render, core_res):
    src = Image.open(render).convert('RGBA')
    img = src.crop(src.getbbox())
    k = 62 / max(img.size)
    img = img.resize((max(1, round(img.width * k)), max(1, round(img.height * k))), Image.LANCZOS)
    icon = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    icon.alpha_composite(img, ((64 - img.width) // 2, (64 - img.height) // 2))
    out = Path(core_res) / 'assets' / MODID / 'textures' / 'item' / ('%s_icon.png' % NAME)
    out.parent.mkdir(parents=True, exist_ok=True)
    icon.save(out)
    print('icon', out)


if __name__ == '__main__':
    if len(sys.argv) == 4 and sys.argv[1] == 'icon':
        install_icon(sys.argv[2], sys.argv[3])
    elif len(sys.argv) in (3, 4):
        main(*sys.argv[1:])
    else:
        raise SystemExit(__doc__)
