"""Render the hotbar (slot) and HUD source images of Rick's Colt Python from the original mesh in Blender.

Run headless:  blender --background --factory-startup --python render_icons.py -- MODEL_ZIP OUT_DIR
Writes OUT_DIR/slot_render.png (3/4 view, transparent) and OUT_DIR/hud_render.png (left side, transparent).
build_ricks_python.py --icons OUT_DIR downsizes them to TaCZ's 64x64 slot icon and 1299x433 HUD image.
Finish matches the pack: polished nickel metal; the grip keeps the source's walnut albedo and normal map.
"""
import math
import os
import sys
import tempfile
import zipfile

import bpy
from mathutils import Vector


def args():
    a = sys.argv[sys.argv.index('--') + 1:]
    if len(a) != 2:
        raise SystemExit(__doc__)
    return a


def nickel():
    m = bpy.data.materials.new('nickel')
    m.use_nodes = True
    p = m.node_tree.nodes['Principled BSDF']
    p.inputs['Base Color'].default_value = (0.80, 0.80, 0.82, 1)
    p.inputs['Metallic'].default_value = 1.0
    p.inputs['Roughness'].default_value = 0.16
    return m


def walnut(tex_dir):
    m = bpy.data.materials.new('walnut')
    m.use_nodes = True
    nt = m.node_tree
    p = nt.nodes['Principled BSDF']
    img = nt.nodes.new('ShaderNodeTexImage')
    img.image = bpy.data.images.load(os.path.join(tex_dir, 'M_WP_Revolver_albedo.jpg'))
    nt.links.new(img.outputs['Color'], p.inputs['Base Color'])
    nrm = nt.nodes.new('ShaderNodeTexImage')
    nrm.image = bpy.data.images.load(os.path.join(tex_dir, 'M_WP_Revolver_normal.png'))
    nrm.image.colorspace_settings.name = 'Non-Color'
    nm = nt.nodes.new('ShaderNodeNormalMap')
    nt.links.new(nrm.outputs['Color'], nm.inputs['Color'])
    nt.links.new(nm.outputs['Normal'], p.inputs['Normal'])
    p.inputs['Roughness'].default_value = 0.42
    return m


def studio_world():
    """Bright-top, dark-bottom gradient so polished metal shows clean reflections."""
    w = bpy.data.worlds.new('studio')
    bpy.context.scene.world = w
    w.use_nodes = True
    nt = w.node_tree
    bg = nt.nodes['Background']
    coord = nt.nodes.new('ShaderNodeTexCoord')
    sep = nt.nodes.new('ShaderNodeSeparateXYZ')
    ramp = nt.nodes.new('ShaderNodeValToRGB')
    ramp.color_ramp.elements[0].position = 0.35
    ramp.color_ramp.elements[0].color = (0.05, 0.05, 0.06, 1)
    ramp.color_ramp.elements[1].position = 0.62
    ramp.color_ramp.elements[1].color = (1.0, 1.0, 1.0, 1)
    mapr = nt.nodes.new('ShaderNodeMapRange')
    nt.links.new(coord.outputs['Generated'], sep.inputs['Vector'])
    nt.links.new(sep.outputs['Z'], mapr.inputs['Value'])
    nt.links.new(mapr.outputs['Result'], ramp.inputs['Fac'])
    nt.links.new(ramp.outputs['Color'], bg.inputs['Color'])
    bg.inputs['Strength'].default_value = 1.2


def bounds(objs):
    pts = [o.matrix_world @ Vector(c) for o in objs for c in o.bound_box]
    lo = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
    hi = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
    return lo, hi


def centre(objs):
    lo, hi = bounds(objs)
    return (lo + hi) / 2


def main():
    model_zip, out_dir = args()
    os.makedirs(out_dir, exist_ok=True)
    tmp = tempfile.mkdtemp(prefix='python_icons_')
    with zipfile.ZipFile(model_zip) as z:
        z.extractall(tmp)
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.fbx(filepath=os.path.join(tmp, 'source', 'revolver_game.fbx'))
    meshes = [o for o in bpy.context.scene.objects if o.type == 'MESH']
    nk, wd = nickel(), walnut(os.path.join(tmp, 'textures'))
    for o in meshes:
        o.data.materials.clear()
        o.data.materials.append(wd if o.name.lower().startswith('grip') else nk)
        for poly in o.data.polygons:
            poly.use_smooth = True
    by = {o.name.split('.')[0].lower(): o for o in meshes}

    # Gun axes from its parts (the FBX is axis-aligned): forward = the barrel's long axis towards the muzzle,
    # up = towards the sights, right = away from the latch.
    c = centre(meshes)
    blo, bhi = bounds([by['barrel_low']])
    ext = bhi - blo
    i = max(range(3), key=lambda a: ext[a])
    fwd = Vector([1.0 if a == i else 0.0 for a in range(3)])
    if fwd.dot(centre([by['barrel_low']]) - c) < 0: fwd = -fwd
    sight = centre([by['sight_low']]) - centre([by['grip_low']])
    j = max((a for a in range(3) if a != i), key=lambda a: abs(sight[a]))
    up = Vector([math.copysign(1.0, sight[a]) if a == j else 0.0 for a in range(3)])
    right = fwd.cross(up) if fwd.cross(up).dot(centre([by['slot_low']]) - c) < 0 else -fwd.cross(up)
    lo, hi = bounds(meshes)
    size = (hi - lo).length
    length, height = abs((hi - lo).dot(fwd)), abs((hi - lo).dot(up))

    studio_world()
    scene = bpy.context.scene
    scene.render.engine = 'CYCLES'
    scene.cycles.samples = 96
    scene.cycles.device = 'CPU'
    scene.render.film_transparent = True
    scene.view_settings.view_transform = 'Standard'
    for name, loc, energy in (('key', c + up * size + right * size * 0.6 - fwd * size * 0.2, 1500),
                              ('fill', c - up * size * 0.3 + right * size + fwd * size * 0.5, 400),
                              ('rim', c + up * size * 0.5 - right * size - fwd * size * 0.4, 700)):
        light = bpy.data.lights.new(name, 'AREA')
        light.energy = energy * (size / 30) ** 2
        light.size = size * 0.6
        ob = bpy.data.objects.new(name, light)
        scene.collection.objects.link(ob)
        ob.location = loc
        ob.rotation_euler = (c - loc).to_track_quat('-Z', 'Y').to_euler()

    cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam'))
    scene.collection.objects.link(cam)
    scene.camera = cam
    cam.data.type = 'ORTHO'

    def shoot(direction, roll_up, res, path, ortho):
        d = direction.normalized()
        cam.location = c + d * size * 2
        cam.rotation_euler = (-d).to_track_quat('-Z', 'Y').to_euler()
        # roll so the gun's up points up on screen
        cam.rotation_euler = (c - cam.location).to_track_quat('-Z', 'Y').to_euler()
        look = (c - cam.location).normalized()
        q = look.to_track_quat('-Z', 'Y')
        cam_up = q @ Vector((0, 1, 0))
        target_up = (roll_up - look * roll_up.dot(look)).normalized()
        ang = math.atan2(look.dot(cam_up.cross(target_up)), cam_up.dot(target_up))
        cam.rotation_euler = q.to_euler()
        cam.rotation_euler.rotate_axis('Z', -ang)
        cam.data.ortho_scale = ortho
        scene.render.resolution_x, scene.render.resolution_y = res
        scene.render.filepath = path
        bpy.ops.render.render(write_still=True)

    # Slot: TaCZ-style 3/4 view of the right side, muzzle to the right and tipped down (like TaCZ's own icons).
    tilt = math.radians(32)
    up_tilted = up * math.cos(tilt) - fwd * math.sin(tilt)
    shoot(right + up * 0.45 + fwd * 0.5, up_tilted, (512, 512), os.path.join(out_dir, 'slot_render.png'), length * 1.05)
    # HUD: plain left side so the muzzle points left, as TaCZ's HUD images do; framed by height (3:1 image).
    shoot(-right, up, (1299, 433), os.path.join(out_dir, 'hud_render.png'), max(length, height * 3) * 1.04)


main()
