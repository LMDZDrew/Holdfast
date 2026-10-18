"""Summarize actual item rotation relative to the paired head; camera motion cancels."""
import json
import re
import sys
from pathlib import Path
import numpy as np

root = Path(sys.argv[1])
groups = {}
for row in map(json.loads, (root / "frames.jsonl").read_text().splitlines()):
    tick = float(re.search(r"action \S+@(\d+)", row["state"])[1]) + row["partial"]
    head = np.array(row["head"]).reshape(4, 4, order="F")
    item = np.array(row["item"]).reshape(4, 4, order="F")
    u, _, v = np.linalg.svd((np.linalg.inv(head) @ item)[:3, :3])
    groups.setdefault(row["case"], []).append((tick, u @ v))
report = {}
for case, rows in groups.items():
    samples = []
    for (t0, a), (t1, b) in zip(rows, rows[1:]):
        if t1 <= t0 or t1 - t0 > 2:
            continue
        degrees = float(np.rad2deg(np.arccos(np.clip((np.trace(a.T @ b)-1)/2, -1, 1))))
        samples.append((t1, degrees, degrees/(t1-t0)))
    report[case] = {}
    end = max(t for t, _ in rows)
    for name, lo, hi in (("strike", 5, 15), ("recovery", 23, 30), ("final_return", end-5, end)):
        window = [(d, rate) for t, d, rate in samples if lo <= t <= hi]
        report[case][name] = dict(arc_degrees=round(sum(d for d, _ in window), 2),
                                 max_degrees_per_tick=round(max((r for _, r in window), default=0), 2))
(root / "rotation-report.json").write_text(json.dumps(report, indent=2))
print(json.dumps(report, indent=2))
