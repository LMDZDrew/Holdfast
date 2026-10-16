"""Render contact sheets of the walker work for visual checks."""
import sys, os
import fk
from fk import rig, worlds, quads, render, sheet, entity, PALETTE
import walker_anims as wa

OUTDIR = sys.argv[1]
W = wa.build()
WR = rig("walker.geo.json")
CR = rig("walker_crawler.geo.json")
side = dict(cam_pos=(70, 22, 0), cam_target=(0, 16, 0), fov=40)
front = dict(cam_pos=(0, 24, -70), cam_target=(0, 16, 0), fov=40)
three4 = dict(cam_pos=(50, 34, -50), cam_target=(0, 14, 0), fov=40)


def frames(R, layers, times, view, size=(260, 220), world=None):
    return [render([(quads(R, worlds(R, layers, t, world=world)), PALETTE)], size=size, **view) for t in times]


def save(name, imgs, cols, labels):
    sheet(imgs, cols, labels).save(os.path.join(OUTDIR, name))


# 1) eight gaits mid-stride from the side, each with a different arm and head set
combos = list(zip(wa.GAITS, ["dangle", "forward_low", "one_up", "broken", "clutched", "wide", "dangle", "forward_low"],
                  ["loll_left", "loll_right", "chin_down", "sniff", "twitch", "slack", "chin_down", "sniff"]))
imgs, labels = [], []
for g, ar, hd in combos:
    layers = [W["body"][g], W["arms"][ar], W["heads"][hd]]
    L = W["body"][g].length
    for t in (0.0, L * 0.25):
        imgs += frames(WR, layers, [t], side)
        labels.append(f"{g}/{ar}/{hd} t={t:.2f}")
save("v2_gaits.png", imgs, 4, labels)

# 2) postures
jobs = [("still", [0]), ("sit_slumped", [0]), ("rise_from_sit", [0.4, 1.0, 1.6, 2.4]), ("prone_still", [0]),
        ("prone_rise", [0.4, 0.9, 1.5, 2.0]), ("prone_ankle_grab", [0.5, 1.2])]
imgs, labels = [], []
for n, ts in jobs:
    for t in ts:
        imgs += frames(WR, [W["full"][n]], [t], three4)
        labels.append(f"{n} {t}")
save("v2_postures.png", imgs, 4, labels)

# 3) crawler
imgs, labels = [], []
for n, ts in [("crawl", [0, 0.6, 1.2]), ("crawl_drag", [0, 0.8]), ("crawler_still", [0]), ("crawler_reach", [0.4]),
              ("crawler_death", [1.0])]:
    for t in ts:
        imgs += frames(CR, [W["crawler"][n]], [t], three4)
        labels.append(f"{n} {t}")
save("v2_crawler.png", imgs, 3, labels)

# 4) grab side and reactions (walker only)
imgs, labels = [], []
for n, ts in [("grab_seize", [0.4]), ("grab_hold", [0.2]), ("grab_bite", [0.38]), ("grab_bite_blocked", [0.42]),
              ("grab_shoved", [0.2, 0.38])]:
    for t in ts:
        imgs += frames(WR, [W["grab"][n]], [t], side)
        labels.append(f"{n} {t}")
for n in wa.EXECS:
    a = W["react"][f"react_{n}"]
    for t in (wa.EXECS[n]["impact"] + 0.05, a.length):
        imgs += frames(WR, [a], [t], three4)
        labels.append(f"react_{n} {t:.2f}")
save("v2_grab_react.png", imgs, 4, labels)
print("ok")
