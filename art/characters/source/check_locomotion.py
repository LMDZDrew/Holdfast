"""Check stance-foot grounding and firearm part ownership on the actual generated movement keys."""
import json, os, glob
import numpy as np
from lib import Anim, OUT
import fk
import player_anims as pa

data = json.load(open(os.path.join(OUT, "player_keys.animation.json")))["animations"]
for name in ("move_walk", "move_sprint", "move_crouch_walk"):
    raw = data["animation.holdfast.player." + name]
    a = Anim(raw["animation_length"], True)
    for part, channels in raw["bones"].items():
        for channel, keys in channels.items():
            for time, value in keys.items():
                a.key(float(time), part, **({"rot": value["post"]} if channel == "rotation" else {"pos": value["post"]}))
    worst = 0
    for u in np.linspace(0, 1, 81, endpoint=False):
        W = fk.worlds(pa.SR, [a], float(u) * a.length)
        stance = "leftLeg" if u < .5 else "rightLeg"
        worst = max(worst, abs(fk.point(W, stance, (0, -12, 0))[1]))
    print(f"{name}: stance-foot height error <= {worst:.3f} px")
    assert worst <= .5, (name, worst)

for path in glob.glob(os.path.join(OUT, "player_animation", "*_gun.json")):
    clip = next(iter(json.load(open(path))["animations"].values()))
    for part, channels in clip["bones"].items():
        assert part in ("body", "leftLeg", "rightLeg"), (path, part)
        if part == "body":
            assert set(channels) <= {"position"}, (path, channels)
print("Firearm movement clips never key upper-body, item or aim transforms.")

# Take-off is selected once already airborne, not in anticipation of a jump button press.
jump = json.load(open(os.path.join(OUT, "player_animation", "move_jump.json")))["animations"]["move_jump"]
assert jump["loop"] == "hold_on_last_frame", "take-off must not finish/land during a long ascent"
assert all(v["post"][1] >= 0 for v in jump["bones"]["body"]["position"].values()), "no airborne loading squat"
print("Non-firearm take-off holds its last frame without a ground-loading squat or timed landing.")
