"""Render a Bedrock box model (TaCZ / GeckoLib .geo.json) with its texture in Blender, using the per-face UV
convention documented in bake.py. Used for TaCZ-style inventory icons and textured previews of baked models.

blender --background --factory-startup --python render_geo_blender.py -- GEO_JSON TEXTURE OUT_PNG VIEW SIZE
VIEW: 'diagonal' (sword icon: hilt bottom-left, tip top-right; the model's long axis is Bedrock +y),
      'side' (long axis left to right), 'guard' (looking down the long axis at the guard), 'hilt' (3/4 of the hilt).
"""
import json
import math
import sys

import bpy
from mathutils import Matrix, Vector


def rot(r):
    """Bedrock rotation [rx, ry, rz] in Bedrock space (Blockbench): Rz(-rz) @ Ry(ry) @ Rx(-rx)."""
    rx, ry, rz = (math.radians(a) for a in r)
    return Matrix.Rotation(-rz, 3, 'Z') @ Matrix.Rotation(ry, 3, 'Y') @ Matrix.Rotation(-rx, 3, 'X')


def face_corners(o, s, face):
    """Corners for (s,t) = (0,0), (1,0), (1,1), (0,1): u along s, v (downward in the texture) along t."""
    x0, y0, z0 = o; x1, y1, z1 = o[0] + s[0], o[1] + s[1], o[2] + s[2]
    f = {
        'west': lambda a, b: (x1, y1 - b * s[1], z0 + a * s[2]),
        'east': lambda a, b: (x0, y1 - b * s[1], z1 - a * s[2]),
        'north': lambda a, b: (x0 + a * s[0], y1 - b * s[1], z0),
        'south': lambda a, b: (x1 - a * s[0], y1 - b * s[1], z1),
        'up': lambda a, b: (x0 + a * s[0], y1, z1 - b * s[2]),
        'down': lambda a, b: (x0 + a * s[0], y0, z0 + b * s[2]),
    }[face]
    return [Vector(f(a, b)) for a, b in ((0, 0), (1, 0), (1, 1), (0, 1))]


NORMAL = {'west': (1, 0, 0), 'east': (-1, 0, 0), 'north': (0, 0, -1), 'south': (0, 0, 1), 'up': (0, 1, 0),
          'down': (0, -1, 0)}
TO_BLENDER = Matrix(((1, 0, 0), (0, 0, -1), (0, 1, 0)))     # Bedrock y-up -> Blender z-up (a rotation)


def build(geo, tex_path):
    desc = geo['minecraft:geometry'][0]['description']
    W, H = desc['texture_width'], desc['texture_height']
    verts, faces, uvs = [], [], []
    for bone in geo['minecraft:geometry'][0]['bones']:
        for c in bone.get('cubes', []):
            o, s = c['origin'], c['size']
            R = rot(c['rotation']) if 'rotation' in c else Matrix.Identity(3)
            pv = Vector(c.get('pivot', (0, 0, 0)))
            for face, uv in c['uv'].items():
                corners = [TO_BLENDER @ (pv + R @ (p - pv)) for p in face_corners(o, s, face)]
                n = TO_BLENDER @ (R @ Vector(NORMAL[face]))
                (u0, v0), (du, dv) = uv['uv'], uv['uv_size']
                st = [(0, 0), (1, 0), (1, 1), (0, 1)]
                if (corners[1] - corners[0]).cross(corners[3] - corners[0]).dot(n) < 0:
                    corners, st = corners[::-1], st[::-1]
                base = len(verts)
                verts += corners
                faces.append(tuple(range(base, base + 4)))
                uvs.append([((u0 + a * du) / W, 1 - (v0 + b * dv) / H) for a, b in st])
    mesh = bpy.data.meshes.new('geo')
    mesh.from_pydata([tuple(v) for v in verts], [], faces)
    layer = mesh.uv_layers.new()
    for poly, uv in zip(mesh.polygons, uvs):
        for li, (u, v) in zip(poly.loop_indices, uv):
            layer.data[li].uv = (u, v)
    obj = bpy.data.objects.new('geo', mesh)
    bpy.context.scene.collection.objects.link(obj)
    mat = bpy.data.materials.new('tex')
    mat.use_nodes = True
    nt = mat.node_tree
    img = nt.nodes.new('ShaderNodeTexImage')
    img.image = bpy.data.images.load(tex_path)
    img.interpolation = 'Closest'
    # Shadeless: the baked texture already carries the shading, as Minecraft draws it (Cycles would also let
    # overlapping coplanar faces of rotated boxes shadow each other, which the game does not).
    out = nt.nodes['Material Output']
    nt.nodes.remove(nt.nodes['Principled BSDF'])
    em = nt.nodes.new('ShaderNodeEmission')
    nt.links.new(img.outputs['Color'], em.inputs['Color'])
    nt.links.new(em.outputs['Emission'], out.inputs['Surface'])
    mesh.materials.append(mat)
    return obj, verts


def main():
    geo_path, tex_path, out, view, size = sys.argv[sys.argv.index('--') + 1:]
    bpy.ops.wm.read_factory_settings(use_empty=True)
    geo = json.load(open(geo_path))
    obj, verts = build(geo, tex_path)
    lo = Vector((min(v.x for v in verts), min(v.y for v in verts), min(v.z for v in verts)))
    hi = Vector((max(v.x for v in verts), max(v.y for v in verts), max(v.z for v in verts)))
    c = (lo + hi) / 2
    length = (hi - lo).length
    scene = bpy.context.scene
    scene.render.engine = 'CYCLES'
    scene.cycles.samples = 64
    scene.cycles.device = 'CPU'
    scene.render.film_transparent = True
    scene.view_settings.view_transform = 'Standard'
    world = bpy.data.worlds.new('w'); scene.world = world
    world.use_nodes = True
    world.node_tree.nodes['Background'].inputs['Strength'].default_value = 1.6
    world.node_tree.nodes['Background'].inputs['Color'].default_value = (0.8, 0.8, 0.82, 1)
    sun = bpy.data.objects.new('sun', bpy.data.lights.new('sun', 'SUN'))
    sun.data.energy = 2.5
    sun.rotation_euler = (math.radians(40), math.radians(20), math.radians(30))
    scene.collection.objects.link(sun)
    cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam'))
    scene.collection.objects.link(cam)
    scene.camera = cam
    cam.data.type = 'ORTHO'
    axis = Vector((0, 0, 1))                    # Bedrock +y (the long axis) in Blender
    if view == 'diagonal':
        d = Vector((1, -0.35, 0.15)).normalized()
        cam.location = c + d * length * 2
        q = (-d).to_track_quat('-Z', 'Y')
        cam.rotation_euler = q.to_euler()
        up_screen = q @ Vector((0, 1, 0)); right = q @ Vector((1, 0, 0))
        ang = math.atan2(axis.dot(up_screen), axis.dot(right)) - math.radians(45)
        cam.rotation_euler.rotate_axis('Z', ang)
        cam.data.ortho_scale = length * 0.8
        res = (int(size), int(size))
    elif view == 'side':
        cam.location = c + Vector((1, 0, 0)) * length * 2
        cam.rotation_euler = Vector((-1, 0, 0)).to_track_quat('-Z', 'Y').to_euler()
        cam.rotation_euler.rotate_axis('Z', math.radians(90))
        cam.data.ortho_scale = length * 1.04
        res = (int(size), int(int(size) * 0.25))
    else:
        target = c + axis * (lo.z - c.z + (0.2 if view == 'guard' else 0.12) * length)
        d = Vector((0.15, -0.2, 1)).normalized() if view == 'guard' else Vector((1, -0.6, 0.2)).normalized()
        cam.location = target + d * length
        cam.rotation_euler = (-d).to_track_quat('-Z', 'Y').to_euler()
        cam.data.ortho_scale = length * (0.16 if view == 'guard' else 0.4)
        c = target
        res = (int(size), int(int(size) * 0.7))
    scene.render.resolution_x, scene.render.resolution_y = res
    scene.render.filepath = out
    bpy.ops.render.render(write_still=True)


main()
