"""Static checks of the exported assets against the loader rules of the candidate libraries.

Rules were read from the loaders' source (not from documentation):
  GeckoLib 4.8.4, branch 1.20.1 (commit 7d6f32db), Forge module:
    loading/json/raw/Model.java, Cube.java, FormatVersion.java
    loading/json/typeadapter/BakedAnimationsAdapter.java, KeyFramesAdapter.java
    core/animation/Animation.java (LoopType), EasingType.java
  Player Animator 1.0.2-rc1, branch 1.20 (commit a17ecb85), coreLib:
    core/data/gson/GeckoLibSerializer.java, core/data/KeyframeAnimation.java, core/util/Easing.java

This proves the files are shaped the way those loaders read them. It does NOT prove they render
correctly in game; that needs the in-game test in docs/testing.md.
Usage: python3 validate_exports.py <export assets root>   (default ../export/assets/holdfast)
"""
import json, os, sys, glob
from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "export", "assets", "holdfast")
errors, notes = [], []

GECKO_FORMATS = {"1.12.0", "1.14.0", "1.21.0", "1.21.2", "1.21.20"}
GECKO_LOOPS = {True, False, "loop", "true", "play_once", "false", "hold_on_last_frame"}
GECKO_EASINGS = {"linear", "step", "catmullrom", "easeinsine", "easeoutsine", "easeinoutsine", "easeinquad", "easeoutquad",
                 "easeinoutquad", "easeincubic", "easeoutcubic", "easeinoutcubic"}
PA_PARTS = {"head", "body", "torso", "rightArm", "leftArm", "rightLeg", "leftLeg", "rightItem", "leftItem"}


def err(f, m):
    errors.append(f"{os.path.relpath(f, ROOT)}: {m}")


def is_vec3(v):
    return isinstance(v, list) and len(v) == 3 and all(isinstance(x, (int, float)) for x in v)


def load(f):
    try:
        return json.load(open(f))
    except Exception as e:
        err(f, f"invalid JSON: {e}")
        return None


# ------------------------------------------------------------------ geometry
geo_bones, geo_tex = {}, {}
for f in sorted(glob.glob(os.path.join(ROOT, "geo", "*.geo.json"))):
    d = load(f)
    if d is None:
        continue
    if d.get("format_version") not in GECKO_FORMATS:
        err(f, f"format_version {d.get('format_version')} not a GeckoLib FormatVersion")
    geos = d.get("minecraft:geometry")
    if not isinstance(geos, list) or not geos:
        err(f, "missing minecraft:geometry array")
        continue
    g = geos[0]
    desc = g.get("description", {})
    tw, th = desc.get("texture_width"), desc.get("texture_height")
    if not isinstance(tw, int) or not isinstance(th, int):
        err(f, "texture_width/height must be integers")
    names = set()
    for b in g.get("bones", []):
        if "name" not in b or not is_vec3(b.get("pivot", [0, 0, 0])):
            err(f, f"bad bone {b.get('name')}")
        if b.get("parent") and b["parent"] not in names:
            err(f, f"bone {b['name']} parent {b['parent']} not declared before it")
        names.add(b["name"])
        for c in b.get("cubes", []):
            if not (is_vec3(c.get("origin")) and is_vec3(c.get("size"))):
                err(f, f"cube in {b['name']} needs origin and size")
            uv = c.get("uv")
            if isinstance(uv, list):          # box UV: GeckoLib floors the size
                w, h, dd = (int(x) for x in c["size"])
                u, v = uv
                if u + 2 * dd + 2 * w > tw or v + dd + h > th or u < 0 or v < 0:
                    err(f, f"box UV of a cube in {b['name']} runs outside {tw}x{th}")
            elif not isinstance(uv, dict):
                err(f, f"cube in {b['name']} has no uv")
    geo_bones[os.path.basename(f)] = names
    geo_tex[os.path.basename(f)] = (tw, th)

# ------------------------------------------------------------------ textures
for f in sorted(glob.glob(os.path.join(ROOT, "textures", "**", "*.png"), recursive=True)):
    im = Image.open(f)
    if im.mode not in ("RGBA", "RGB", "P", "LA"):
        err(f, f"unexpected PNG mode {im.mode}")
    rel = os.path.relpath(f, ROOT)
    if "/walker/" in rel and im.size != geo_tex.get("walker.geo.json"):
        err(f, f"size {im.size} does not match walker.geo.json {geo_tex.get('walker.geo.json')}")
    if "/player/" in rel and im.size != (64, 64):
        err(f, f"player skin must be the vanilla 64x64 layout, is {im.size}")

# ------------------------------------------------------------------ GeckoLib animations
ANIM_GEO = {"walker.animation.json": "walker.geo.json", "walker_crawler.animation.json": "walker_crawler.geo.json"}
gecko_count = 0
for f in sorted(glob.glob(os.path.join(ROOT, "animations", "*.animation.json"))):
    d = load(f)
    if d is None:
        continue
    bones = geo_bones.get(ANIM_GEO.get(os.path.basename(f)), set())
    for name, a in d.get("animations", {}).items():
        gecko_count += 1
        L = a.get("animation_length")
        if not isinstance(L, (int, float)) or L <= 0:
            err(f, f"{name}: animation_length must be positive")
        if a.get("loop", False) not in GECKO_LOOPS:
            err(f, f"{name}: loop {a.get('loop')} is not a GeckoLib LoopType")
        for bn, chans in a.get("bones", {}).items():
            if bn not in bones:
                err(f, f"{name}: bone {bn} not in {ANIM_GEO.get(os.path.basename(f))}")
            for ch, keys in chans.items():
                if ch not in ("rotation", "position", "scale"):
                    err(f, f"{name}.{bn}: unknown channel {ch}")
                if not isinstance(keys, dict):
                    continue
                zeros = 0
                for t, v in keys.items():
                    tt = float(t)
                    zeros += tt == 0
                    if tt > L + 1e-6:
                        err(f, f"{name}.{bn}.{ch}: key {t} after animation_length {L}")
                    vec = v if isinstance(v, list) else (v.get("post") or v.get("pre") if isinstance(v, dict) else None)
                    if not is_vec3(vec):
                        err(f, f"{name}.{bn}.{ch}@{t}: value is not a 3-vector")
                    if isinstance(v, dict) and "lerp_mode" in v and str(v["lerp_mode"]).lower() not in GECKO_EASINGS:
                        err(f, f"{name}.{bn}.{ch}@{t}: lerp_mode {v['lerp_mode']} unknown to GeckoLib EasingType")
                if zeros > 1:
                    err(f, f"{name}.{bn}.{ch}: multiple 0-time keys (GeckoLib rejects)")
        for t, s in a.get("sound_effects", {}).items():
            float(t)
            if not isinstance(s, dict) or "effect" not in s:
                err(f, f"{name}: sound_effects@{t} needs an effect string")
        for t, s in a.get("timeline", {}).items():
            float(t)
            if not isinstance(s, (str, list)):
                err(f, f"{name}: timeline@{t} must be a string")

# ------------------------------------------------------------------ Player Animator
pa_count = 0
for f in sorted(glob.glob(os.path.join(ROOT, "player_animation", "*.json"))):
    d = load(f)
    if d is None:
        continue
    anims = d.get("animations")
    if not isinstance(anims, dict) or len(anims) != 1:
        err(f, "expected exactly one animation per file")
        continue
    for name, a in anims.items():
        pa_count += 1
        if "animation_length" not in a:
            err(f, f"{name}: Player Animator needs animation_length (else it rejects non-looping clips)")
        loop = a.get("loop")
        if not (isinstance(loop, bool) or loop == "hold_on_last_frame"):
            err(f, f"{name}: loop must be true/false/hold_on_last_frame")
        for bn, chans in a.get("bones", {}).items():
            if bn not in PA_PARTS:
                err(f, f"{name}: bone {bn} is not a Player Animator part")
            for ch, keys in chans.items():
                if ch not in ("rotation", "position"):
                    err(f, f"{name}.{bn}: channel {ch} is ignored by Player Animator")
                for t, v in keys.items():
                    if isinstance(v, list) and ch == "rotation":
                        err(f, f"{name}.{bn}@{t}: bare rotation array is read as a stepped key")
                    if isinstance(v, dict) and v.get("lerp_mode") not in ("linear", None):
                        notes.append(f"{name}.{bn}: lerp_mode {v.get('lerp_mode')} (catmullrom becomes INOUTSINE there)")
        if "bones" not in a:
            err(f, f"{name}: no bones")

# ------------------------------------------------------------------ extras (our core's own data)
x_count = 0
for f in sorted(glob.glob(os.path.join(ROOT, "player_animation_extras", "*.extras.json"))):
    d = load(f)
    if d is None:
        continue
    for name, a in d.get("animations", {}).items():
        x_count += 1
        cam = a.get("bones", {}).get("camera", {})
        if set(cam) != {"rotation", "position"}:
            err(f, f"{name}: camera track needs rotation and position")
        if not os.path.exists(os.path.join(ROOT, "player_animation", f"{name}.json")):
            err(f, f"{name}: no matching Player Animator file")

print(f"geometry files: {len(geo_bones)}; GeckoLib animations: {gecko_count}; Player Animator clips: {pa_count}; extras: {x_count}")
for n in sorted(set(notes))[:5]:
    print("note:", n)
if errors:
    print(f"{len(errors)} problem(s):")
    for e in errors[:60]:
        print("  -", e)
    sys.exit(1)
print("all checks passed")
