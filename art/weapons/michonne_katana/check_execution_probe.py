"""Measure real GeckoLib blade cube edges against the real posed skull matrices captured by ExecutionItemProbe.

This is renderer evidence, not source-FK substitution or visual acceptance. Coordinates are head-local pixels.
The cube vertices include GeckoLib's baked mirroring and pivot rotations; matrices come from the same frame.
"""
import argparse
import json
import re
from pathlib import Path

import numpy as np

LO, HI = np.array([-4., 2., -4.]), np.array([4., 8., 4.])


def clipped(a, b):
    d = b - a
    near, far = 0., 1.
    for k in range(3):
        if abs(d[k]) < 1e-9:
            if a[k] < LO[k] or a[k] > HI[k]:
                return []
        else:
            u, v = sorted(((LO[k] - a[k]) / d[k], (HI[k] - a[k]) / d[k]))
            near, far = max(near, u), min(far, v)
            if near > far:
                return []
    return [a + near * d, a + far * d]


def measure(mesh, head, item):
    matrix = np.linalg.inv(np.array(head).reshape(4, 4).T) @ np.array(item).reshape(4, 4).T
    direction = matrix[:3, 1] / np.linalg.norm(matrix[:3, 1])
    points, boxes = [], 0
    for vertices in mesh:
        v = np.array(vertices)
        v = (matrix @ np.c_[v, np.ones(len(v))].T).T[:, :3] * 16
        hit = []
        for quad in v.reshape(-1, 4, 3):
            for a, b in zip(quad, np.roll(quad, -1, axis=0)):
                hit.extend(clipped(a, b))
        if hit:
            boxes += 1
            points.extend(hit)
    if not points:
        return dict(boxes=0, span_px=0., depth_px=0.)
    p = np.array(points)
    projection = p @ direction
    depth = np.minimum(p - LO, HI - p).min(axis=1).max()
    return dict(boxes=boxes, span_px=round(float(np.ptp(projection)), 3), depth_px=round(float(depth), 3))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('capture', type=Path)
    args = parser.parse_args()
    root = args.capture
    mesh = json.loads((root / 'blade-cubes.jsonl').read_text().splitlines()[0])
    cases = {}
    for line in (root / 'frames.jsonl').read_text().splitlines():
        frame = json.loads(line)
        tick = int(re.search(r'@(\d+)', frame['state'])[1])
        result = measure(mesh, frame['head'], frame['item'])
        result.update(tick=tick, partial=frame['partial'])
        cases.setdefault(frame['case'], []).append(result)
    report = {}
    for case, frames in cases.items():
        kill = 10 if case in ('crawler', 'prone') else 6
        contact = [f for f in frames if kill <= f['tick'] <= kill + 1]
        hold = [f for f in frames if kill <= f['tick'] <= kill + 2]
        report[case] = dict(frames=len(frames), contact_frames=len(contact),
                            contact_max_span_px=max((f['span_px'] for f in contact), default=0),
                            contact_min_span_px=min((f['span_px'] for f in contact), default=0),
                            hold_max_span_px=max((f['span_px'] for f in hold), default=0),
                            contact_boxes=max((f['boxes'] for f in contact), default=0))
    (root / 'contact-report.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))
    expected = {'standing-front', 'standing-left', 'standing-right', 'standing-rear', 'crawler', 'prone'}
    assert cases.keys() == expected, f'Missing capture cases: {expected - cases.keys()}'
    assert all(r['contact_max_span_px'] > 0 and r['contact_frames'] > 0 for r in report.values()), 'Rendered blade missed skull'


if __name__ == '__main__':
    main()
