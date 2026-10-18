"""Execution blade/skull measurements in walker pixels; shared by validation and server-contact generation."""
import numpy as np
import fk
import math

SKULL = (np.array([-4., 2., -4.]), np.array([4., 8., 4.]))


def kill_tick(first_in, authored):
    return math.ceil(first_in * 20 - 1e-9) if first_in is not None else round(authored * 20)


def segment_box(start, end, bounds=SKULL):
    lo, hi = bounds
    delta = end - start
    enter, leave = 0., 1.
    for k in range(3):
        if abs(delta[k]) < 1e-9:
            if start[k] < lo[k] or start[k] > hi[k]:
                return None
        else:
            a, b = sorted(((lo[k] - start[k]) / delta[k], (hi[k] - start[k]) / delta[k]))
            enter, leave = max(enter, a), min(leave, b)
            if enter > leave:
                return None
    return enter, leave


def penetration(Hinv, Wm, weapon, marker, cut=False):
    point = (Hinv @ np.array([*fk.point(Wm, marker), 1.]))[:3]
    axis = Hinv[:3, :3] @ (-Wm['rightItem'][:3, 1 if cut else 2])
    axis /= np.linalg.norm(axis)
    start = point - axis * 64
    hit = segment_box(start, point)
    inserted = 64 * (1 - hit[0]) if hit else 0.
    inside = 0.
    if weapon in ('knife', 'katana') and not cut:
        base_z = -2.5 if weapon == 'knife' else -3.2
        base = (Hinv @ Wm['rightItem'] @ np.array([0., 0., base_z, 1.]))[:3]
        blade_hit = segment_box(base, point)
        if blade_hit:
            inside = float(np.linalg.norm(point - base) * (blade_hit[1] - blade_hit[0]))
    past_centre = float((point - np.array([0., 5., 0.])) @ axis)
    return dict(penetration=round(float(inserted), 3), blade_inside=round(inside, 3),
                past_centre=round(past_centre, 3), local_point=[round(float(x), 3) for x in point])


def minimum(name):
    # Physical knife blade is 6 authoring px * 15/16 player scale: 5 px inside is nearly guard-deep.
    return (5.0, 'blade_inside') if name in ('knife', 'knife_ground') else ((6.0, 'penetration') if name == 'katana'
            else ((4.0, 'penetration') if name == 'katana_ground' else (0., 'penetration')))
