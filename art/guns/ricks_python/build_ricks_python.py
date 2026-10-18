"""Build the Holdfast TaCZ gun pack for Rick's Colt Python.

No third-party model, texture or TaCZ asset is committed or downloaded. Supply both inputs explicitly:
  MODEL_ZIP  the user-supplied "gameready-colt-python-revolver.zip" (FBX + PBR textures)
  TACZ_JAR   the installed TaCZ jar (read-only: the Rhino .357 rig, stats and display settings)
Usage: python build_ricks_python.py MODEL_ZIP TACZ_JAR OUT_DIR [ICON_DIR]
  ICON_DIR  optional output of render_icons.py (Blender renders of the original mesh) for the hotbar and HUD
            images; without it they are rendered from the box model.

TaCZ renders guns as Bedrock box geometry only, so each named source part is rebuilt from a few large, rotated
boxes (box_fit.py): flat parts from their side outline, round parts as polygon prisms. Bones, names and pivots
follow TaCZ's rhino357 rig so the gun plays TaCZ's own rhino357 animations, state machine and sounds by
reference, unchanged. Reload pivots (crane, cylinder, ejector, chambered rounds, speed loader) keep the Rhino's
relative layout, shifted together by one offset, so the loader still meets the swung-out cylinder; hammer,
trigger and latch pins come from the Python mesh. Cartridges and the speed loader are original boxes fitted to
the spaces the Rhino's use.
"""
import hashlib
import io
import json
import math
import shutil
import sys
import zipfile
import zlib
from pathlib import Path

import numpy as np
from PIL import Image

import bake
import box_fit
import fbx_binary
import preview_geo as P

NS, GUN = 'holdfast', 'ricks_python'
DISPLAY_NAME = "Rick's Colt Python"
RHINO = 'assets/tacz/custom/tacz_default_gun/'

# Source FBX part -> rhino357 bone.
PART_BONE = {
    'wheel_low.001': 'bone5',              # cylinder
    'wheel_rot_low.001': 'crane',          # yoke
    'handle_low.001': 'ejector',           # ejector rod
    'wheel_pan_low.001': 'bone11',         # extractor star (child of ejector)
    'Hammer_low.001': 'hammer',
    'trigger_low.001': 'trigger',
    'slot_low.001': 'cylinder_release',    # latch thumbpiece
    'slot_ball_low.001': 'cylinder_release',
    'body_low.001': 'gun_body',
    'wheel_stop_low.001': 'gun_body',
    'sight_fixed_low.001': 'gun_body',
    'airtight_low.001': 'gun_body',
    'barrel_low.001': 'barrel',
    'sight_low.001': 'rear_sight',
    'grip_low.001': 'grip',
}
# How each part becomes boxes. profile: side-outline rectangles (res = raster cell, band = thickness step,
# bevel = chamfered crossed slabs for rounded parts). lathe: 2k-gon prism about the cylinder or bore axis.
SMALL = dict(res=0.02, band=0.05, min_side=0.05)
FIT = {
    'body_low.001': ('profile', dict(res=0.04, band=0.08, exclude='grip_low.001')),
    'barrel_low.001': ('profile', dict(res=0.04, band=0.08)),
    'grip_low.001': ('profile', dict(res=0.04, bevel=0.22)),
    'wheel_rot_low.001': ('profile', dict(res=0.025, band=0.06)),
    'Hammer_low.001': ('profile', SMALL), 'trigger_low.001': ('profile', SMALL),
    'slot_low.001': ('profile', SMALL), 'slot_ball_low.001': ('profile', SMALL),
    'sight_fixed_low.001': ('profile', SMALL),
    'wheel_stop_low.001': ('profile', SMALL),
    'wheel_low.001': ('lathe', dict(axis='cylinder', k=6)),
    'handle_low.001': ('lathe', dict(axis='cylinder', k=4)),
    'airtight_low.001': ('lathe', dict(axis='bore', k=6)),
}
FLUTED = 'wheel_low.001'
# Built explicitly instead (see main): the rear sight's notch and the thin front blade are lost by outline
# fitting, and the extractor star must stay inside the chamber ring so empty chambers show.
BUILT = {'sight_low.001', 'wheel_pan_low.001'}
FRONT_BLADE_HALF = 0.11       # cm; the real blade is 0.07 cm each side, widened slightly to read at ADS distance   # alternate 12-gon faces sit between chambers: darker, like the Python's flutes

# Finish (Andrew, 2026-10-18): smooth, shiny nickel with walnut grips. Every visible box face gets its own patch
# of a baked atlas (bake.py): walnut from the source's own grip texture, nickel as baked polished reflections with
# the source's occlusion and markings. Cartridges, loader and bores use flat swatches in the atlas's first row.
# Model units per real centimetre. TaCZ's default pistols measure 0.48-0.62 (Glock 17 0.59, M1911 0.52,
# Desert Eagle 0.49, Rhino 0.51, Taurus 500 0.50); 0.51 keeps the Python's real 6" proportions at TaCZ scale.
SCALE = 0.51
PPU = 32                                   # atlas pixels per model unit (TaCZ's own guns use about 4)
ATLAS_W = 1024
FIXED_COLORS = {'brass': (176, 141, 72), 'brass_dark': (128, 98, 46), 'copper': (168, 102, 66),
                'loader': (34, 34, 36), 'loader_knob': (78, 78, 82), 'bore': (22, 22, 24)}

# Pins measured on the source mesh (cm, source axes): see README.
HAMMER_PIN = (0.0, -0.55, 2.45)
TRIGGER_PIN = (0.0, -2.75, 0.75)
LATCH_PIN = (-0.94, -0.77, 0.75)
# Rhino reload bones whose relative layout is preserved and shifted onto the Python cylinder.
SHIFTED = {'cylinder_crane', 'crane', 'cy_base', 'bone5', 'ejector', 'bone11', 'round_group2',
           'loader_and_round', 'speed_loader', 'round_group'} | {'round%d' % i for i in range(1, 13)} | \
          {'bone2', 'bone4', 'bone6', 'bone7', 'bone8', 'bone9'}
KEEP_CUBES = {'lefthand_pos', 'righthand_pos'}   # arm placement markers TaCZ replaces with the player's arms
CYL_ROUNDS = {'round7': 'bone4', 'round8': 'bone2', 'round9': 'bone6', 'round10': 'bone7', 'round11': 'bone8',
              'round12': 'bone9'}
LOADER_ROUNDS = ['round%d' % i for i in range(1, 7)]
# Real .357 Magnum / speed-loader dimensions (cm).
CASE_D, RIM_D, RIM_T, CASE_L, BULLET_D, OAL = 0.965, 1.11, 0.15, 3.30, 0.91, 4.04

FACES = tuple(bake.NORMAL)


def jsonc(text):
    """JSON with // comments (TaCZ pack files)."""
    out, i, n, s = [], 0, len(text), False
    while i < n:
        c = text[i]
        if s:
            out.append(c)
            if c == '\\': out.append(text[i + 1]); i += 1
            elif c == '"': s = False
        elif c == '"': s = True; out.append(c)
        elif text.startswith('//', i):
            while i < n and text[i] != '\n': i += 1
            continue
        else: out.append(c)
        i += 1
    return json.loads(''.join(out))


def envelope(bone):
    pts = [q for q, _ in P.quads({'bones': [dict(bone, parent=None)]}, {bone['name']: np.eye(4)})]
    a = np.vstack(pts)
    return a.min(0), a.max(0)


def surface_samples(tris, h):
    """Points on every triangle spaced <= 0.35 h."""
    pts = []
    edges = np.linalg.norm(tris - np.roll(tris, 1, axis=1), axis=2).max(1)
    steps = np.maximum(1, np.ceil(edges / (0.35 * h))).astype(int)
    for n in np.unique(steps):
        i, j = np.meshgrid(np.arange(n + 1), np.arange(n + 1), indexing='ij')
        keep = i + j <= n
        b = np.stack([i[keep], j[keep]], 1) / n
        w = np.column_stack([1 - b.sum(1), b])
        pts.append(np.einsum('kc,tcd->tkd', w, tris[steps == n]).reshape(-1, 3))
    return np.vstack(pts)


# ---------------------------------------------------------------- texture and faces

def swatch(col):
    """Flat colour cell `col` of the atlas's first row (8 px cells, sampled well inside)."""
    return {'uv': [col * 8 + 2, 2], 'uv_size': [4, 4]}


def bedrock(box, uv):
    c, s = np.array(box['centre'], float), np.array(box['size'], float)
    cube = {'origin': [round(float(x), 4) for x in c - s / 2], 'size': [round(float(x), 4) for x in s], 'uv': uv}
    if any(abs(r) > 1e-6 for r in box['rotation']):
        cube['pivot'] = [round(float(x), 4) for x in c]
        cube['rotation'] = [round(float(r), 3) for r in box['rotation']]
    return cube


def prism(cx, cy, z0, z1, d, uv=None, k=4):
    """Round prism of diameter d along z from k strips (a regular 2k-gon); uv None leaves faces for baking."""
    apo = d / 2
    side = 2 * apo * math.tan(math.pi / (2 * k))
    return [bedrock({'centre': (cx, cy, (z0 + z1) / 2), 'size': (side, 2 * apo, z1 - z0),
                     'rotation': (0.0, 0.0, 180.0 * i / k)}, {} if uv is None else uv) for i in range(k)]


def cartridge(cx, cy, z_rear, k, pal):
    """Original .357 cartridge boxes in units k per cm, rear rim at z_rear (model -z is forward)."""
    zr = z_rear - RIM_T * k
    zc = z_rear - CASE_L * k
    zt = z_rear - OAL * k
    case = prism(cx, cy, zr, z_rear, RIM_D * k, pal['brass_dark']) + prism(cx, cy, zc, zr, CASE_D * k, pal['brass'])
    mid = zc - (zc - zt) * 0.45
    bullet = prism(cx, cy, mid, zc, BULLET_D * k, pal['copper']) + \
        prism(cx, cy, zt, mid, BULLET_D * 0.62 * k, pal['copper'])
    return case, bullet


# ---------------------------------------------------------------- build

def main(model_zip, tacz_jar, out_dir, icon_dir=None):
    out_dir = Path(out_dir)
    with zipfile.ZipFile(model_zip) as z:
        parts = fbx_binary.load_meshes(z.read('source/revolver_game.fbx'))
        albedo = np.asarray(Image.open(io.BytesIO(z.read('textures/M_WP_Revolver_albedo.jpg'))).convert('RGB'))
        ao = np.asarray(Image.open(io.BytesIO(z.read('textures/M_WP_Revolver_AO.jpg'))).convert('L'))
    if set(parts) != set(PART_BONE):
        raise SystemExit('unexpected source parts: %s' % sorted(set(parts) ^ set(PART_BONE)))
    with zipfile.ZipFile(tacz_jar) as j:
        rhino_geo_file = json.loads(j.read(RHINO + 'assets/tacz/geo_models/gun/rhino357_geo.json'))
        rhino_display = jsonc(j.read(RHINO + 'assets/tacz/display/guns/rhino357_display.json').decode('utf-8'))
        rhino_data = jsonc(j.read(RHINO + 'data/tacz/data/guns/rhino357_data.json').decode('utf-8'))
        rhino_index = jsonc(j.read(RHINO + 'data/tacz/index/guns/rhino357.json').decode('utf-8'))
        rhino_allow = jsonc(j.read(RHINO + 'data/tacz/tacz_tags/attachments/allow_attachments/rhino357.json')
                            .decode('utf-8'))
        rhino_anim = json.loads(j.read(RHINO + 'assets/tacz/animations/rhino357.animation.json'))
    rgeo = rhino_geo_file['minecraft:geometry'][0]
    rb = {b['name']: b for b in rgeo['bones']}

    # --- placement: scale by cylinder width, align trigger, shift reload rig onto the Python cylinder
    def bbox(name):
        t = parts[name][0].reshape(-1, 3)
        return t.min(0), t.max(0)
    wlo, whi = bbox('wheel_low.001')
    tlo, thi = bbox('trigger_low.001')
    r5lo, r5hi = envelope(rb['bone5'])
    rtlo, rthi = envelope(rb['trigger'])
    k = SCALE                                                       # model units per cm
    oy = (rtlo[1] + rthi[1]) / 2 - k * (tlo[1] + thi[1]) / 2
    oz = (rtlo[2] + rthi[2]) / 2 - k * (tlo[2] + thi[2]) / 2

    def M(p):
        p = np.asarray(p, float)
        return np.stack([-k * p[..., 0], k * p[..., 1] + oy, k * p[..., 2] + oz], -1)   # TaCZ: gun's left is +x
    axis_cm = np.array([0.0, (wlo[1] + whi[1]) / 2, whi[2]])
    rhino_axis = np.array([0.0, rb['cy_base']['pivot'][1], r5hi[2]])
    delta = M(axis_cm) - rhino_axis
    delta[0] = 0.0
    axis = rhino_axis + delta
    bore = M(np.array([0.0, *(np.array(bbox('airtight_low.001')).mean(0)[1:])]))
    r_ch = bore[1] - axis[1]

    pal = {name: {f: swatch(i) for f in FACES} for i, name in enumerate(FIXED_COLORS)}
    baked = []                                                       # (bone, cube, source part, material)

    # --- bones: rhino names/hierarchy/pivots, Python geometry
    bones = []
    for b in rgeo['bones']:
        nb = {k2: v for k2, v in b.items() if k2 != 'cubes'}
        if b['name'] in KEEP_CUBES: nb['cubes'] = b['cubes']
        if b['name'] in SHIFTED: nb['pivot'] = [round(float(a + d), 5) for a, d in zip(b['pivot'], delta)]
        bones.append(nb)
    B = {b['name']: b for b in bones}
    for name, pin in (('hammer', HAMMER_PIN), ('trigger', TRIGGER_PIN), ('cylinder_release', LATCH_PIN)):
        B[name]['pivot'] = [round(float(x), 5) for x in M(pin)]

    # --- geometry: few rotated boxes per part
    stats = {}
    for name, (mode, opt) in FIT.items():
        if name in BUILT: continue
        tris = M(parts[name][0])
        opt = dict(opt)
        samples = surface_samples(tris, min(0.03, opt.get('res', 0.03)))
        if mode == 'lathe':
            centre = (axis if opt['axis'] == 'cylinder' else bore)[:2]
            boxes = box_fit.lathe_fit(samples, centre, k=opt['k'], min_len=opt.get('min_len', 0.05))
        else:
            if 'exclude' in opt: opt['exclude'] = M(parts[opt['exclude']][0])
            boxes = box_fit.profile_fit(tris, samples, **opt)
        material = 'walnut' if name == 'grip_low.001' else 'nickel'
        cubes = []
        for bx in boxes:
            if min(bx['size']) < 1e-3: continue
            m = 'flute' if name == FLUTED and bx.get('strip', 0) % 2 == 1 else material
            cubes.append(bedrock(bx, {}))
            baked.append((PART_BONE[name], cubes[-1], name, m))
        B[PART_BONE[name]].setdefault('cubes', []).extend(cubes)
        stats[name] = len(cubes)

    # --- chambers: dark mouths on the cylinder front and dark empty chambers on its rear face (hidden by the
    # cartridge rims until the rounds are ejected); the extractor star stays inside the chamber ring
    cz = [(c['origin'][2], c['origin'][2] + c['size'][2]) for c in B['bone5']['cubes']]
    front, rear = min(a for a, _ in cz), max(b for _, b in cz)       # the prism's actual end faces
    chamber_d = CASE_D * k * 1.02
    assert rear + 0.01 < axis[2] + RIM_T * k, 'empty-chamber discs must stay behind the cartridge rims'
    for rnd in CYL_ROUNDS:
        lo, hi = envelope(rb[rnd])
        ang = math.atan2((lo[1] + hi[1]) / 2 - rhino_axis[1], (lo[0] + hi[0]) / 2 - rhino_axis[0])
        cx, cy = axis[0] + r_ch * math.cos(ang), axis[1] + r_ch * math.sin(ang)
        B['bone5']['cubes'] += prism(cx, cy, front - 0.008, front + 0.02, chamber_d, pal['bore'])
        B['bone5']['cubes'] += prism(cx, cy, rear - 0.02, rear + 0.008, chamber_d, pal['bore'])
    star = M(parts['wheel_pan_low.001'][0]).reshape(-1, 3)
    star_d = 2 * (r_ch - chamber_d / 2 - 0.02)
    B['bone11']['cubes'] = prism(axis[0], axis[1], star[:, 2].min(), star[:, 2].max() + 0.01, star_d, k=6)
    baked += [('bone11', c, 'wheel_pan_low.001', 'nickel') for c in B['bone11']['cubes']]
    stats['wheel_pan_low.001'] = len(B['bone11']['cubes'])

    # --- iron sights as on the real gun (Andrew's reference photo): nickel rear sight with its real notch,
    # nickel ramp front sight fitted from the source's side profile at its real width
    rs_cm = parts['sight_low.001'][0].reshape(-1, 3)
    rlo, rhi = rs_cm.min(0), rs_cm.max(0)
    mid = rs_cm[np.abs(rs_cm[:, 1] - (rlo[1] + rhi[1]) / 2 - 0.15) < 0.06]
    notch = np.abs(mid[:, 0])[np.abs(mid[:, 0]) > 0.05].min()          # half-width of the notch
    notch_floor = rs_cm[np.abs(rs_cm[:, 0]) < notch * 0.8][:, 1].max()

    def cm_box(lo, hi):
        a, b = M(np.array(lo, float)), M(np.array(hi, float))
        return bedrock({'centre': tuple((a + b) / 2), 'size': tuple(np.abs(b - a)), 'rotation': (0, 0, 0)}, {})
    B['rear_sight']['cubes'] = [
        cm_box((rlo[0], rlo[1], rlo[2]), (rhi[0], notch_floor, rhi[2])),
        cm_box((rlo[0], notch_floor, rlo[2]), (-notch, rhi[1], rhi[2])),
        cm_box((notch, notch_floor, rlo[2]), (rhi[0], rhi[1], rhi[2]))]
    baked += [('rear_sight', c, 'sight_low.001', 'nickel') for c in B['rear_sight']['cubes']]
    stats['sight_low.001'] = 3
    b_tris = parts['barrel_low.001'][0]
    b_cm = b_tris.reshape(-1, 3)
    zmin = b_cm[:, 2].min()
    rib_top = b_cm[(b_cm[:, 2] > zmin + 3) & (b_cm[:, 2] < zmin + 8)][:, 1].max()
    sel = ((np.abs(b_tris[:, :, 0]) < 0.12) & (b_tris[:, :, 1] > rib_top - 0.05) & (b_tris[:, :, 2] < zmin + 2.5)).all(1)
    ramp_tris = M(b_tris[sel])
    ramp = box_fit.profile_fit(ramp_tris, surface_samples(ramp_tris, 0.006), res=0.006, band=0.012,
                               min_side=0.012, coverage=0.99)
    blade = b_cm[(b_cm[:, 2] < zmin + 2.5) & (np.abs(b_cm[:, 0]) < 0.12) & (b_cm[:, 1] > rib_top - 0.05)]
    ramp_cubes = [bedrock(bx, {}) for bx in ramp if min(bx['size']) > 1e-3]
    B['barrel']['cubes'] += ramp_cubes
    baked += [('barrel', c, 'barrel_low.001', 'nickel') for c in ramp_cubes]
    stats['front_ramp'] = len(ramp_cubes)

    bar = M(parts['barrel_low.001'][0]).reshape(-1, 3)
    muzzle = bar[:, 2].min()
    B['barrel']['cubes'] += prism(0.0, bore[1], muzzle - 0.012, muzzle + 0.03, BULLET_D * k, pal['bore'])

    # --- chambered rounds: Rhino chamber angles, Python chamber radius, rims at the cylinder face
    rim_z = axis[2] + RIM_T * k
    for rnd, bul in CYL_ROUNDS.items():
        lo, hi = envelope(rb[rnd])
        ang = math.atan2((lo[1] + hi[1]) / 2 - rhino_axis[1], (lo[0] + hi[0]) / 2 - rhino_axis[0])
        cx, cy = axis[0] + r_ch * math.cos(ang), axis[1] + r_ch * math.sin(ang)
        case, bullet = cartridge(cx, cy, rim_z, k, pal)
        B[rnd]['cubes'] = case
        B[rnd]['pivot'] = [round(cx, 5), round(cy, 5), B[rnd]['pivot'][2]]
        B[bul]['cubes'] = bullet
        B[bul]['pivot'] = [round(cx, 5), round(cy, 5), round(rim_z - (CASE_L + OAL) / 2 * k, 5)]
    # --- speed loader at the Rhino's 1/10 scale (the animation scales loader_and_round x10)
    ring = []
    for rnd in LOADER_ROUNDS:
        lo, hi = envelope(rb[rnd])
        ring.append(((lo + hi) / 2, hi[2]))
    ctr = np.mean([c for c, _ in ring], 0)
    tiny = k / 10
    for rnd, (c, rear) in zip(LOADER_ROUNDS, ring):
        ang = math.atan2(c[1] - ctr[1], c[0] - ctr[0])
        cx = ctr[0] + delta[0] + r_ch / 10 * math.cos(ang)
        cy = ctr[1] + delta[1] + r_ch / 10 * math.sin(ang)
        case, bullet = cartridge(cx, cy, rear + delta[2], tiny, pal)
        B[rnd]['cubes'] = case + bullet
    lx, ly = ctr[0] + delta[0], ctr[1] + delta[1]
    z0 = max(r[1] for r in ring) + delta[2]                       # loader body sits behind the rims
    B['speed_loader']['cubes'] = (
        prism(lx, ly, z0, z0 + 0.5 * tiny, 2 * r_ch / 10 + RIM_D * tiny, pal['loader'], k=6) +
        prism(lx, ly, z0 + 0.5 * tiny, z0 + 1.3 * tiny, 1.2 * tiny, pal['loader_knob'], k=6))

    # --- functional points from the Python geometry
    bmin, bmax = bar.min(0), bar.max(0)
    front_sight = bar[bar[:, 2] < bmin[2] + 0.8]
    sight_y = float(M(np.array([0.0, blade[:, 1].max(), 0.0]))[1])         # front blade top
    B['muzzle_flash']['pivot'] = [0.0, round(float(bore[1]), 5), round(float(bmin[2]), 5)]
    B['laser_pos']['pivot'] = [0.0, round(float(front_sight[:, 1].min()), 5), round(float(bmin[2] + 1.25), 5)]
    B['bone']['pivot'] = [0.0, round(float(sight_y), 5), round(float(bmin[2] + 0.4), 5)]
    B['sight_illuminated']['pivot'] = list(B['bone']['pivot'])
    B['scope_pos']['pivot'] = [0.0, round(float(sight_y), 5), round(float((bmin[2] + bmax[2]) / 2), 5)]
    rs = M(parts['sight_low.001'][0].reshape(-1, 3))
    B['rear_sight']['pivot'] = [0.0, round(float(rs[:, 1].max()), 5), round(float(rs[:, 2].mean()), 5)]
    B['rear_sight_illuminated']['pivot'] = list(B['rear_sight']['pivot'])
    B['iron_view']['pivot'] = [0.0, round(sight_y - 0.01, 5), B['iron_view']['pivot'][2]]   # eye on the blade top

    # --- bake the atlas: walnut from the source texture, nickel as baked polished reflections
    source = bake.Source({n: (M(t), uv) for n, (t, uv) in parts.items()}, albedo, ao, spacing=0.02)
    atlas, used = bake.bake([(B[bone]['cubes'], c, part, mat) for bone, c, part, mat in baked], source, PPU, ATLAS_W)
    tex_h = 1 << int(math.ceil(math.log2(max(used, 64))))
    full = np.zeros((tex_h, ATLAS_W, 4), np.uint8)
    full[:used] = atlas
    for i, c in enumerate(FIXED_COLORS.values()):
        full[0:8, i * 8:(i + 1) * 8] = tuple(c) + (255,)
    tex = Image.fromarray(full, 'RGBA')

    geo = {'format_version': rhino_geo_file['format_version'], 'minecraft:geometry': [{
        'description': dict(rgeo['description'], identifier='geometry.%s' % GUN, texture_width=ATLAS_W,
                            texture_height=tex_h),
        'bones': bones}]}

    # --- write pack
    root = out_dir / ('%s_%s' % (NS, GUN))
    if root.exists(): shutil.rmtree(root)
    A, D = root / 'assets' / NS, root / 'data' / NS

    def put(path, obj, compact=False):
        path.parent.mkdir(parents=True, exist_ok=True)
        text = json.dumps(obj, separators=(',', ':')) if compact else json.dumps(obj, indent=1, ensure_ascii=False)
        path.write_text(text + '\n', encoding='utf-8')
    put(root / 'gunpack.meta.json', {'namespace': NS})
    put(A / 'gunpack_info.json', {
        'version': '0.2.0', 'name': 'pack.%s.%s.name' % (NS, GUN), 'desc': 'pack.%s.%s.desc' % (NS, GUN),
        'license': 'Private Holdfast test pack; source model licence unverified',
        'authors': ['Holdfast'], 'date': '2026-10-18'})
    put(A / 'geo_models' / 'gun' / ('%s_geo.json' % GUN), geo, compact=True)
    (A / 'textures' / 'gun' / 'uv').mkdir(parents=True, exist_ok=True)
    tex.save(A / 'textures' / 'gun' / 'uv' / ('%s.png' % GUN))
    display = dict(rhino_display)
    display.pop('lod', None)
    display.update(model='%s:gun/%s_geo' % (NS, GUN), texture='%s:gun/uv/%s' % (NS, GUN),
                   hud='%s:gun/hud/%s' % (NS, GUN), slot='%s:gun/slot/%s' % (NS, GUN))
    for key in ('animation', 'state_machine', 'player_animator_3rd', 'use_default_animation', 'sounds'):
        assert display[key] == rhino_display[key]
    put(A / 'display' / 'guns' / ('%s_display.json' % GUN), display)
    put(D / 'data' / 'guns' / ('%s_data.json' % GUN), rhino_data)
    put(D / 'index' / 'guns' / ('%s.json' % GUN), dict(rhino_index, name='%s.gun.%s.name' % (NS, GUN),
        display='%s:%s_display' % (NS, GUN), data='%s:%s_data' % (NS, GUN), tooltip='%s.gun.%s.desc' % (NS, GUN)))
    put(D / 'tacz_tags' / 'attachments' / 'allow_attachments' / ('%s.json' % GUN), rhino_allow)
    put(A / 'lang' / 'en_us.json', {
        'pack.%s.%s.name' % (NS, GUN): 'Holdfast: %s' % DISPLAY_NAME,
        'pack.%s.%s.desc' % (NS, GUN): 'Rick Grimes\'s .357 revolver for Holdfast.',
        '%s.gun.%s.name' % (NS, GUN): DISPLAY_NAME,
        '%s.gun.%s.desc' % (NS, GUN): 'Six rounds of .357 Magnum. Make them count.'})

    # --- icons: Blender renders of the original mesh when given, else the posed box model
    gg = geo['minecraft:geometry'][0]
    if icon_dir:
        hud_src = Image.open(Path(icon_dir) / 'hud_render.png').convert('RGBA')
        slot_src = Image.open(Path(icon_dir) / 'slot_render.png').convert('RGBA')
    else:
        mats = P.pose(gg)
        hide = ('lefthand', 'righthand', 'loader_and_round', 'round_group2')
        center = np.array([0, 8.4, 1.6])
        hud_src = P.render(gg, tex, mats, 'right', center, 92, (1299, 433), hide, bg=None).transpose(Image.FLIP_LEFT_RIGHT)
        slot_src = P.render(gg, tex, mats, 'right', center, 40, (640, 640), hide, bg=None)
    (A / 'textures' / 'gun' / 'hud').mkdir(parents=True, exist_ok=True)
    hud_icon(hud_src).save(A / 'textures' / 'gun' / 'hud' / ('%s.png' % GUN))
    (A / 'textures' / 'gun' / 'slot').mkdir(parents=True, exist_ok=True)
    slot_icon(slot_src).save(A / 'textures' / 'gun' / 'slot' / ('%s.png' % GUN))

    zpath = out_dir / ('%s_%s.zip' % (NS, GUN))
    with zipfile.ZipFile(zpath, 'w', zipfile.ZIP_DEFLATED) as zf:
        for f in sorted(root.rglob('*')):
            if f.is_file(): zf.write(f, f.relative_to(root).as_posix())
    report = {'scale_units_per_cm': k, 'texture': [ATLAS_W, tex_h], 'atlas_rows_used': used, 'reload_rig_shift': delta.tolist(), 'chamber_radius': r_ch,
              'cubes_per_part': stats, 'total_cubes': sum(len(b.get('cubes', [])) for b in bones),
              'pack_zip': str(zpath), 'pack_zip_sha256': hashlib.sha256(zpath.read_bytes()).hexdigest(),
              'tacz_jar_sha256': hashlib.sha256(Path(tacz_jar).read_bytes()).hexdigest(),
              'model_zip_sha256': hashlib.sha256(Path(model_zip).read_bytes()).hexdigest()}
    put(out_dir / 'build_report.json', report)
    preview(geo, tex, rhino_geo_file, rhino_anim, tacz_jar, out_dir)
    print(json.dumps(report, indent=1))


def hud_icon(src):
    """TaCZ HUD style: light grey monochrome, muzzle left, filling the 1299x433 height, right-aligned."""
    a = np.asarray(src).astype(float)
    lum = a[..., :3] @ [0.3, 0.59, 0.11]
    a[..., :3] = (150 + lum / 255 * 75)[..., None]
    img = Image.fromarray(a.astype(np.uint8), 'RGBA')
    img = img.crop(img.getbbox())
    s = min(433 / img.height, 1299 / img.width)
    img = img.resize((round(img.width * s), round(img.height * s)), Image.LANCZOS)
    out = Image.new('RGBA', (1299, 433), (0, 0, 0, 0))
    out.alpha_composite(img, (1299 - img.width, 433 - img.height))
    return out


def slot_icon(src):
    """TaCZ slot style: the shaded gun filling a 64x64 icon with a one-pixel margin."""
    img = src.crop(src.getbbox())
    s = 62 / max(img.width, img.height)
    img = img.resize((max(1, round(img.width * s)), max(1, round(img.height * s))), Image.LANCZOS)
    out = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    out.alpha_composite(img, ((64 - img.width) // 2, (64 - img.height) // 2))
    return out


def preview(geo, tex, rhino_geo_file, anim, tacz_jar, out_dir):
    """Side-by-side Rhino/Python frames of the shared animations, gun-relative (whole-gun motion removed)."""
    with zipfile.ZipFile(tacz_jar) as j:
        rtex = Image.open(io.BytesIO(j.read(RHINO + 'assets/tacz/textures/gun/uv/rhino357.png'))).convert('RGBA')
    A = anim['animations']
    frames = [('rest', None, 0), ('cocked', 'charge_in', 0.15), ('fired', 'shoot', 0.07),
              ('latch+swing', 'reload_empty', 0.7), ('eject', 'reload_empty', 1.05), ('loader', 'reload_empty', 1.9),
              ('insert', 'reload_empty', 2.15), ('closing', 'reload_empty', 3.25)]
    hide = ('lefthand', 'righthand')
    rows = []
    for label, an, t in frames:
        row = Image.new('RGB', (1500, 300), 'white')
        for col, (g, tx) in enumerate(((rhino_geo_file['minecraft:geometry'][0], rtex), (geo['minecraft:geometry'][0], tex))):
            m = P.pose(g, A.get(an) if an else None, t)
            inv = np.linalg.inv(m['righthand_and_gun'])
            m = {k2: inv @ v for k2, v in m.items()}
            row.paste(P.render(g, tx, m, 'right', np.array([0, 8.6, 1.2]), 34, (480, 300), hide), (col * 750, 0))
            row.paste(P.render(g, tx, m, 'rear', np.array([0.6, 8.6, 0]), 34, (270, 300), hide), (col * 750 + 480, 0))
        rows.append(row)
    sheet = Image.new('RGB', (1500, 300 * len(rows)), 'white')
    for i, r in enumerate(rows):
        sheet.paste(r, (0, 300 * i))
    sheet.save(out_dir / 'preview_rhino_vs_python.png')


if __name__ == '__main__':
    if len(sys.argv) not in (4, 5):
        raise SystemExit(__doc__)
    main(*sys.argv[1:])
