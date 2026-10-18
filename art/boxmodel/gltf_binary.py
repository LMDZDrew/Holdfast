"""Minimal binary glTF 2.0 (.glb) reader: world-space triangles and UVs per mesh node, plus embedded images.

Supports node translation/rotation/scale/matrix hierarchies, indexed or non-indexed TRIANGLES primitives,
float POSITION and TEXCOORD_0. Anything else is refused rather than guessed.
"""
import io
import json
import struct

import numpy as np

COMPONENT = {5120: 'b', 5121: 'B', 5122: 'h', 5123: 'H', 5125: 'I', 5126: 'f'}
COUNT = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}


def _chunks(data):
    magic, version, _ = struct.unpack_from('<4sII', data, 0)
    if magic != b'glTF' or version != 2:
        raise ValueError('not a glTF 2.0 binary')
    off, js, bin_ = 12, None, None
    while off < len(data):
        length, kind = struct.unpack_from('<I4s', data, off)
        body = data[off + 8:off + 8 + length]
        if kind == b'JSON': js = json.loads(body)
        elif kind == b'BIN\x00': bin_ = body
        off += 8 + length
    return js, bin_


def _accessor(js, bin_, i):
    a = js['accessors'][i]
    if a.get('sparse'): raise ValueError('sparse accessors unsupported')
    bv = js['bufferViews'][a['bufferView']]
    comp, n = COMPONENT[a['componentType']], COUNT[a['type']]
    size = struct.calcsize('<' + comp)
    stride = bv.get('byteStride') or size * n
    start = bv.get('byteOffset', 0) + a.get('byteOffset', 0)
    raw = np.frombuffer(bin_, dtype=np.dtype('<' + comp), count=(a['count'] - 1) * stride // size + n, offset=start)
    out = np.lib.stride_tricks.as_strided(raw, (a['count'], n), (stride, size)).astype(float)
    if a.get('normalized') and comp != 'f':
        out /= float(np.iinfo(np.dtype(comp)).max)
    return out


def _local(node):
    if 'matrix' in node:
        return np.array(node['matrix'], float).reshape(4, 4).T
    t = np.array(node.get('translation', [0, 0, 0]), float)
    x, y, z, w = node.get('rotation', [0, 0, 0, 1])
    R = np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                  [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                  [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])
    M = np.eye(4)
    M[:3, :3] = R @ np.diag(node.get('scale', [1, 1, 1]))
    M[:3, 3] = t
    return M


def load(data):
    """Returns ({part name: (triangles[N,3,3], uvs[N,3,2])}, [image bytes], json).

    A part is named after the nearest named ancestor of its mesh node (Sketchfab exports put the real part name
    on the parent of a 'defaultMaterial' node).
    """
    js, bin_ = _chunks(data)
    nodes = js['nodes']
    parent = {c: i for i, n in enumerate(nodes) for c in n.get('children', [])}

    def world(i):
        M = _local(nodes[i])
        return world(parent[i]) @ M if i in parent else M

    def label(i):
        j = i
        while j is not None:
            name = nodes[j].get('name', '')
            if name and name != 'defaultMaterial': return name
            j = parent.get(j)
        return 'node%d' % i

    parts = {}
    for i, n in enumerate(nodes):
        if 'mesh' not in n: continue
        W = world(i)
        tris, uvs = [], []
        for prim in js['meshes'][n['mesh']]['primitives']:
            if prim.get('mode', 4) != 4: raise ValueError('only TRIANGLES primitives are supported')
            pos = _accessor(js, bin_, prim['attributes']['POSITION'])
            uv = _accessor(js, bin_, prim['attributes']['TEXCOORD_0']) if 'TEXCOORD_0' in prim['attributes'] \
                else np.zeros((len(pos), 2))
            idx = _accessor(js, bin_, prim['indices'])[:, 0].astype(int) if 'indices' in prim else np.arange(len(pos))
            pos = pos @ W[:3, :3].T + W[:3, 3]
            tris.append(pos[idx].reshape(-1, 3, 3))
            uvs.append(uv[idx].reshape(-1, 3, 2))
        name = label(i)
        if name in parts:
            t0, u0 = parts[name]
            parts[name] = (np.vstack([t0] + tris), np.vstack([u0] + uvs))
        else:
            parts[name] = (np.vstack(tris), np.vstack(uvs))
    images = []
    for im in js.get('images', []):
        bv = js['bufferViews'][im['bufferView']]
        images.append(bin_[bv.get('byteOffset', 0):bv.get('byteOffset', 0) + bv['byteLength']])
    return parts, images, js
