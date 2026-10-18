"""Minimal binary FBX (7.x) reader: world-space triangles and UVs per model.

Supports what the supplied revolver uses: Lcl translation/rotation (XYZ Euler)/scale, model parenting,
ByPolygonVertex UVs. Anything else (pivots, pre-rotation, geometric transforms) is refused, not ignored.
"""
import io
import math
import struct
import zlib

import numpy as np


def _props(f, n):
    out = []
    for _ in range(n):
        t = f.read(1)
        if t == b'Y': out.append(struct.unpack('<h', f.read(2))[0])
        elif t == b'C': out.append(f.read(1) != b'\0')
        elif t == b'I': out.append(struct.unpack('<i', f.read(4))[0])
        elif t == b'F': out.append(struct.unpack('<f', f.read(4))[0])
        elif t == b'D': out.append(struct.unpack('<d', f.read(8))[0])
        elif t == b'L': out.append(struct.unpack('<q', f.read(8))[0])
        elif t in b'fdlib':
            length, enc, clen = struct.unpack('<III', f.read(12))
            data = f.read(clen)
            if enc: data = zlib.decompress(data)
            code = {b'f': 'f', b'd': 'd', b'l': 'q', b'i': 'i', b'b': 'B'}[t]
            out.append(list(struct.unpack('<%d%s' % (length, code), data)))
        elif t in (b'S', b'R'):
            ln = struct.unpack('<I', f.read(4))[0]
            d = f.read(ln)
            out.append(d.decode('utf-8', 'replace') if t == b'S' else d)
        else:
            raise ValueError('unknown FBX property type %r' % t)
    return out


def _node(f, v64):
    end, nprops, _ = struct.unpack('<QQQ' if v64 else '<III', f.read(24 if v64 else 12))
    name = f.read(f.read(1)[0]).decode()
    if end == 0:
        return None
    node = {'name': name, 'props': _props(f, nprops), 'children': []}
    sentinel = 25 if v64 else 13
    while f.tell() < end:
        if end - f.tell() == sentinel:
            f.read(sentinel)
            break
        c = _node(f, v64)
        if c is None: break
        node['children'].append(c)
    return node


def _child(n, name):
    return next((c for c in n['children'] if c['name'] == name), None)


def _p70(n):
    return {c['props'][0]: c['props'][4:] for c in _child(n, 'Properties70')['children']}


def _name(n):
    return n['props'][1].split('\x00')[0]


def _local(m):
    p = _p70(m)
    for k in ('RotationPivot', 'ScalingPivot', 'RotationOffset', 'ScalingOffset', 'PreRotation', 'PostRotation',
              'GeometricTranslation', 'GeometricRotation', 'GeometricScaling'):
        if k in p and any(abs(v - (1 if 'Scaling' in k else 0)) > 1e-4 for v in p[k]):
            raise ValueError('unsupported FBX %s on %s' % (k, _name(m)))
    rx, ry, rz = (math.radians(a) for a in p.get('Lcl Rotation', [0, 0, 0]))
    Rx = np.array([[1, 0, 0], [0, math.cos(rx), -math.sin(rx)], [0, math.sin(rx), math.cos(rx)]])
    Ry = np.array([[math.cos(ry), 0, math.sin(ry)], [0, 1, 0], [-math.sin(ry), 0, math.cos(ry)]])
    Rz = np.array([[math.cos(rz), -math.sin(rz), 0], [math.sin(rz), math.cos(rz), 0], [0, 0, 1]])
    M = np.eye(4)
    M[:3, :3] = Rz @ Ry @ Rx @ np.diag(p.get('Lcl Scaling', [1, 1, 1]))  # FBX default eEulerXYZ
    M[:3, 3] = p.get('Lcl Translation', [0, 0, 0])
    return M


def load_meshes(data):
    """{model name: (triangles float[N,3,3], uvs float[N,3,2])} in the file's world space."""
    f = io.BytesIO(data)
    if f.read(23)[:18] != b'Kaydara FBX Binary':
        raise ValueError('not a binary FBX')
    v64 = struct.unpack('<I', f.read(4))[0] >= 7500
    top = {}
    while True:
        n = _node(f, v64)
        if n is None: break
        top[n['name']] = n
    objs = {o['props'][0]: o for o in top['Objects']['children']}
    children, parent = {}, {}
    for c in top['Connections']['children']:
        if c['props'][0] != 'OO': continue
        src, dst = c['props'][1], c['props'][2]
        children.setdefault(dst, []).append(src)
        if src in objs and dst in objs and objs[src]['name'] == objs[dst]['name'] == 'Model':
            parent[src] = dst

    def world(mid):
        M = _local(objs[mid])
        return world(parent[mid]) @ M if mid in parent else M

    out = {}
    for mid, m in objs.items():
        if m['name'] != 'Model': continue
        geoms = [objs[c] for c in children.get(mid, []) if c in objs and objs[c]['name'] == 'Geometry']
        if len(geoms) != 1: continue
        g, W = geoms[0], world(mid)
        v = np.array(_child(g, 'Vertices')['props'][0], float).reshape(-1, 3)
        v = (W[:3, :3] @ v.T).T + W[:3, 3]
        pvi = _child(g, 'PolygonVertexIndex')['props'][0]
        uvl = _child(g, 'LayerElementUV')
        if _child(uvl, 'MappingInformationType')['props'][0] != 'ByPolygonVertex':
            raise ValueError('unsupported UV mapping on %s' % _name(m))
        uv = np.array(_child(uvl, 'UV')['props'][0], float).reshape(-1, 2)
        direct = _child(uvl, 'ReferenceInformationType')['props'][0] != 'IndexToDirect'
        uvi = None if direct else _child(uvl, 'UVIndex')['props'][0]
        tris, tuv, poly = [], [], []
        for k, idx in enumerate(pvi):
            poly.append((~idx if idx < 0 else idx, k if direct else uvi[k]))
            if idx < 0:
                for t in range(1, len(poly) - 1):
                    tri = (poly[0], poly[t], poly[t + 1])
                    tris.append([v[a] for a, _ in tri])
                    tuv.append([uv[b] for _, b in tri])
                poly = []
        out[_name(m)] = (np.array(tris), np.array(tuv))
    return out
