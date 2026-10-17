"""Sample authored carries at 20 Hz: no arm inside chest, support hand on handle, one-hand blades down.

Uses the regenerated authoring keys, not a new solver run. This checks geometry; Andrew still judges appearance.
"""
import json
import os
import numpy as np
from lib import Anim, OUT
import fk
import player_anims as pa

keys = json.load(open(os.path.join(OUT, "player_keys.animation.json")))["animations"]
for weapon in ("knife", "machete", "hatchet", "katana", "axe", "bat"):
    data = keys["animation.holdfast.player.hold_" + weapon]
    a = Anim(data["animation_length"], data["loop"])
    for bone, channels in data["bones"].items():
        for channel, frames in channels.items():
            for time, value in frames.items():
                a.key(float(time), bone, **({"rot": value["post"]} if channel == "rotation" else {"pos": value["post"]}))
    penetration = grip = 0.0
    for t in np.arange(0, a.length, .05):
        W = fk.worlds(pa.SR, [a], t)
        penetration = max(penetration, pa.body_penetration(W))
        if weapon in ("katana", "axe", "bat"):
            target = (W["rightItem"] @ np.array([0, 0, 3.5, 1]))[:3]
            grip = max(grip, np.linalg.norm(fk.point(W, "leftHand") - target))
        else:
            assert -W["rightItem"][1, 2] < -.8, (weapon, t, "blade not pointing down")
    print(f"hold_{weapon}: chest depth {penetration:.4f} px; support-grip miss {grip:.3f} px")
    assert penetration < 1e-6, (weapon, penetration)
    assert grip <= 1.5, (weapon, grip)
print("All carry geometry checks passed (visual quality still needs the game).")
