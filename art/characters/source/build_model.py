"""Walker (four outfits), legless crawler, and a survivor stand-in with execution weapons."""
import os, random
from PIL import Image
from lib import OUT, write_json


def cube(origin, size, uv):
    return {"origin": origin, "size": size, "uv": uv}


def bone(name, parent, pivot, cubes=None):
    b = {"name": name, "pivot": pivot}
    if parent:
        b["parent"] = parent
    if cubes:
        b["cubes"] = cubes
    return b


def geo(identifier, bones, tw=64, th=64):
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": identifier, "texture_width": tw, "texture_height": th,
                        "visible_bounds_width": 4, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": bones}]}


# Front faces -Z. Limbs are jointed at elbows and knees; the jaw is hinged.
WALKER = [
    bone("root", None, [0, 0, 0]),
    bone("hips", "root", [0, 12, 0]),
    bone("torso", "hips", [0, 12, 0], [cube([-4, 12, -2], [8, 12, 4], [0, 14])]),
    bone("head", "torso", [0, 24, 0], [cube([-4, 26, -4], [8, 6, 8], [0, 0])]),
    bone("jaw", "head", [0, 26, 3], [cube([-4, 24, -4], [8, 2, 8], [32, 0])]),
    bone("rightArm", "torso", [-5, 22, 0], [cube([-8, 18, -2], [4, 6, 4], [32, 10])]),
    bone("rightForearm", "rightArm", [-6, 18, 0], [cube([-8, 12, -2], [4, 6, 4], [24, 20])]),
    bone("leftArm", "torso", [5, 22, 0], [cube([4, 18, -2], [4, 6, 4], [48, 10])]),
    bone("leftForearm", "leftArm", [6, 18, 0], [cube([4, 12, -2], [4, 6, 4], [40, 20])]),
    bone("rightLeg", "hips", [-2, 12, 0], [cube([-4, 6, -2], [4, 6, 4], [0, 30])]),
    bone("rightShin", "rightLeg", [-2, 6, 0], [cube([-4, 0, -2], [4, 6, 4], [32, 30])]),
    bone("leftLeg", "hips", [2, 12, 0], [cube([0, 6, -2], [4, 6, 4], [16, 30])]),
    bone("leftShin", "leftLeg", [2, 6, 0], [cube([0, 0, -2], [4, 6, 4], [48, 30])]),
]

# Missing everything below the ribs (§12: crawlers are predefined, no dynamic dismemberment).
CRAWLER = [
    bone("root", None, [0, 0, 0]),
    bone("torso", "root", [0, 12, 0], [cube([-4, 12, -2], [8, 12, 4], [0, 14])]),
    bone("stub", "torso", [0, 12, 0], [cube([-2.5, 9, -1.5], [5, 3, 3], [0, 40])]),
    bone("gut", "torso", [0, 10, -0.5], [cube([-1, 3, -1.5], [2, 7, 2], [16, 40])]),
    bone("head", "torso", [0, 24, 0], [cube([-4, 26, -4], [8, 6, 8], [0, 0])]),
    bone("jaw", "head", [0, 26, 3], [cube([-4, 24, -4], [8, 2, 8], [32, 0])]),
    bone("rightArm", "torso", [-5, 22, 0], [cube([-8, 18, -2], [4, 6, 4], [32, 10])]),
    bone("rightForearm", "rightArm", [-6, 18, 0], [cube([-8, 12, -2], [4, 6, 4], [24, 20])]),
    bone("leftArm", "torso", [5, 22, 0], [cube([4, 18, -2], [4, 6, 4], [48, 10])]),
    bone("leftForearm", "leftArm", [6, 18, 0], [cube([4, 12, -2], [4, 6, 4], [40, 20])]),
]

# Preview stand-in for the player (the real game uses the vanilla player model and skin).
# Body parts use the vanilla 64x64 skin layout in the top half of a 64x128 preview atlas; the
# weapons and crutches below are preview props only (in game they are item models).
# Bone names match Player Animator parts. Marker bones (no cubes) are contact points.
FIST_R, FIST_L = [-6, 12.5, 0], [6, 12.5, 0]
SURVIVOR = [
    bone("body", None, [0, 0, 0]),
    # "torso" here is the upper-body lean. It pivots where Player Animator's "body" part pivots
    # (0.7 blocks = 11.2 px above the feet) and is exported as that part; see player_anims.export().
    bone("torso", "body", [0, 11.2, 0], [cube([-4, 12, -2], [8, 12, 4], [16, 16])]),
    bone("head", "torso", [0, 24, 0], [cube([-4, 24, -4], [8, 8, 8], [0, 0])]),
    bone("rightArm", "torso", [-5, 22, 0], [cube([-8, 12, -2], [4, 12, 4], [40, 16])]),
    bone("rightItem", "rightArm", FIST_R),
    bone("knife", "rightItem", FIST_R, [cube([-6.5, 12, -2.5], [1, 1, 3], [40, 100]),
                                       cube([-6.5, 11.75, -8.5], [1, 1.5, 6], [40, 106])]),
    bone("knifeTip", "knife", [-6, 12.5, -8.3]),
    bone("bat", "rightItem", FIST_R, [cube([-6.6, 11.9, -5], [1.2, 1.2, 9], [30, 64]),
                                     cube([-7.1, 11.4, -17], [2.2, 2.2, 12], [0, 64])]),
    bone("batSweet", "bat", [-6, 12.5, -13.5]),
    bone("batGrip", "bat", [-6, 12.5, 2.6]),
    bone("axe", "rightItem", FIST_R, [cube([-6.6, 11.9, -14.5], [1.2, 1.2, 17.5], [0, 80]),
                                     cube([-6.5, 7.5, -14.8], [1, 5, 3.6], [40, 80])]),
    bone("axeEdge", "axe", [-6, 7.7, -13]),
    bone("axeGrip", "axe", [-6, 12.5, 2.0]),
    bone("machete", "rightItem", FIST_R, [cube([-6.5, 11.9, -3], [1, 1.2, 3.5], [52, 64]),
                                         cube([-6.4, 10.6, -14], [0.8, 2.4, 11], [0, 100])]),
    bone("macheteEdge", "machete", [-6, 10.8, -10]),
    # one-handed hatchet and two-handed katana (weapon families, decisions.md 2026-10-16); same convention:
    # along -Z from the fist, edge -Y. Geometry matches gen_core_assets.NEW.
    bone("hatchet", "rightItem", FIST_R, [cube([-6.6, 11.9, -9.0], [1.2, 1.2, 11.0], [0, 80]),
                                         cube([-6.5, 8.5, -9.5], [1.0, 4.4, 2.8], [40, 80])]),
    bone("hatchetEdge", "hatchet", [-6, 8.7, -8.1]),
    bone("katana", "rightItem", FIST_R, [cube([-6.6, 11.9, -2.5], [1.2, 1.2, 7.5], [30, 64]),
                                        cube([-7.5, 11.0, -3.2], [3.0, 3.0, 0.7], [30, 64]),
                                        cube([-6.3, 11.3, -26.0], [0.6, 1.8, 22.8], [0, 100])]),
    bone("katanaEdge", "katana", [-6, 11.4, -18.0]),
    bone("katanaTip", "katana", [-6, 12.2, -25.8]),
    bone("katanaGrip", "katana", [-6, 12.5, 3.6]),
    bone("crutchR", "rightItem", FIST_R, [cube([-6.5, 0, 1], [1, 21, 1], [24, 100]),
                                         cube([-7.5, 20.5, 0.5], [3, 1, 1], [30, 100])]),
    bone("leftArm", "torso", [5, 22, 0], [cube([4, 12, -2], [4, 12, 4], [32, 48])]),
    bone("leftHand", "leftArm", FIST_L),
    bone("leftItem", "leftArm", FIST_L),
    bone("crutchL", "leftItem", FIST_L, [cube([5.5, 0, 1], [1, 21, 1], [24, 100]),
                                        cube([4.5, 20.5, 0.5], [3, 1, 1], [30, 100])]),
    bone("rightLeg", "body", [-1.9, 12, 0], [cube([-3.9, 0, -2], [4, 12, 4], [0, 16])]),
    bone("leftLeg", "body", [1.9, 12, 0], [cube([-0.1, 0, -2], [4, 12, 4], [16, 48])]),
]


# ------------------------------------------------------------------ painting
def faces(u, v, w, h, d):
    w, h, d = int(w), int(h), int(d)            # GeckoLib floors box-UV sizes
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "east": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "west": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


class Painter:
    def __init__(self, w=64, h=64, seed=7):
        self.img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.px = self.img.load()
        self.W, self.H = w, h
        self.rnd = random.Random(seed)

    def put(self, x, y, c):
        if 0 <= x < self.W and 0 <= y < self.H:
            self.px[x, y] = tuple(max(0, min(255, int(v))) for v in c[:3]) + (255,)

    def box(self, u, v, w, h, d, fn):
        for name, (x0, y0, ww, hh) in faces(u, v, w, h, d).items():
            for y in range(hh):
                for x in range(ww):
                    self.put(x0 + x, y0 + y, fn(name, x, y, ww, hh))

    def j(self, c, amt=10):
        k = self.rnd.randint(-amt, amt)
        return (c[0] + k + self.rnd.randint(-3, 3), c[1] + k + self.rnd.randint(-3, 3), c[2] + k + self.rnd.randint(-3, 3))


def mix(a, b, t):
    return tuple(a[i] * (1 - t) + b[i] * t for i in range(3))


BLOOD, BLOOD_DRY, MOUTH, TEETH = (92, 22, 20), (64, 26, 22), (58, 16, 18), (196, 182, 132)
EYE, SOCKET, MUD, BOOT = (214, 206, 160), (34, 30, 30), (74, 62, 46), (48, 36, 28)

OUTFITS = {
    # name: skin, hair (None = bald), top pattern, top colours, bottom colour, shoes
    "farmer": dict(skin=(118, 128, 104), hair=(46, 38, 30), top="plaid", ta=(104, 52, 44), tb=(58, 40, 38),
                   bottom=(62, 72, 86), shoe=BOOT),
    "tshirt": dict(skin=(128, 126, 106), hair=(150, 132, 96), top="plain", ta=(178, 172, 156), tb=(150, 144, 128),
                   bottom=(54, 62, 80), shoe=(70, 70, 70)),
    "scrubs": dict(skin=(112, 122, 108), hair=None, top="plain", ta=(70, 112, 106), tb=(58, 96, 92),
                   bottom=(64, 104, 98), shoe=(160, 160, 156)),
    "office": dict(skin=(124, 120, 100), hair=(70, 66, 62), top="shirt", ta=(140, 160, 178), tb=(120, 140, 160),
                   bottom=(56, 56, 60), shoe=(30, 26, 24)),
}


def walker_texture(name, o, seed):
    p = Painter(seed=seed)
    SKIN, HAIR = o["skin"], o["hair"]
    SKIN_DARK, BRUISE = mix(SKIN, (40, 44, 36), 0.35), mix(SKIN, (96, 76, 100), 0.6)

    def skin(x=0, y=0):
        c = p.j(SKIN, 12)
        r = p.rnd.random()
        if r < 0.12:
            c = mix(c, BRUISE, 0.6)
        elif r < 0.18:
            c = mix(c, SKIN_DARK, 0.8)
        return c

    def hair_or_skin():
        if HAIR is None or p.rnd.random() < 0.18:
            return skin()
        return p.j(HAIR, 8)

    def top(x, y, face=""):
        if o["top"] == "plaid":
            base = o["ta"] if (x // 2 + y // 2) % 2 == 0 else o["tb"]
            if x % 4 == 1 or y % 4 == 3:
                base = mix(base, (30, 26, 26), 0.35)
            c = p.j(base, 8)
        elif o["top"] == "shirt":
            c = p.j(o["ta"] if y % 3 else o["tb"], 6)
        else:
            c = p.j(o["ta"] if (x + y) % 5 else o["tb"], 6)
        if p.rnd.random() < 0.12:
            c = mix(c, MUD, 0.5)
        return c

    def bottom(x, y):
        c = p.j(o["bottom"] if (x + y) % 3 else mix(o["bottom"], (0, 0, 0), 0.2), 7)
        if p.rnd.random() < 0.15:
            c = mix(c, MUD, 0.5)
        return c

    def skull(face, x, y, w, h):
        if face == "top":
            return hair_or_skin()
        if face == "bottom":
            return p.j(MOUTH, 6)
        if face == "front":
            if y == 0:
                return hair_or_skin()
            if y == 1:
                return mix(skin(), SKIN_DARK, 0.5)
            if y == 2:
                return p.j(SOCKET, 4) if x in (1, 2, 5, 6) else skin()
            if y == 3:
                if x in (2, 5):
                    return p.j(EYE, 6)
                return p.j(SOCKET, 4) if x in (1, 6) else skin()
            if y == 4:
                if x in (3, 4):
                    return p.j(SKIN_DARK, 4)
                return p.j(BLOOD_DRY, 6) if x == 6 else skin()
            return (p.j(TEETH, 10) if x not in (2, 5) else p.j(MOUTH, 4)) if 1 <= x <= 6 else skin()
        return hair_or_skin() if y <= 3 else skin()

    p.box(0, 0, 8, 6, 8, skull)

    def jaw(face, x, y, w, h):
        if face == "top":
            return (p.j(TEETH, 10) if x != 4 else p.j(MOUTH, 4)) if y == 0 and 1 <= x <= 6 else p.j((96, 34, 36), 8)
        if face == "front":
            if y == 0:
                return p.j(TEETH, 10) if 1 <= x <= 6 and x != 3 else p.j(BLOOD, 8)
            return p.j(BLOOD, 10) if x in (2, 3, 6) else skin()
        return mix(skin(), BLOOD_DRY, 0.4) if face == "bottom" else skin()

    p.box(32, 0, 8, 2, 8, jaw)

    def torso(face, x, y, w, h):
        c = top(x, y, face)
        if face in ("front", "back") and y <= 1:
            c = mix(c, BLOOD, 0.7)
        if face == "front":
            if o["top"] == "shirt" and 3 <= x <= 4 and 1 <= y <= 8:
                return p.j((40, 40, 52), 5)                      # tie
            if 3 <= x <= 4 and y <= 2 and o["top"] != "shirt":
                return skin()
            if x >= 5 and 6 <= y <= 9:
                return skin() if p.rnd.random() > 0.3 else p.j(BLOOD, 8)
            if x <= 2 and 2 <= y <= 5:
                c = mix(c, BLOOD_DRY, 0.6)
        if face in ("top", "bottom"):
            c = mix(c, SKIN_DARK, 0.3)
        return c

    p.box(0, 14, 8, 12, 4, torso)

    short_sleeves = name in ("tshirt", "scrubs")

    def upper(bitten):
        def f(face, x, y, w, h):
            if bitten and face in ("front", "west") and 2 <= y <= 4 and x <= 2:
                return p.j(BLOOD, 10) if p.rnd.random() > 0.3 else skin()
            if short_sleeves and y >= 3 and face not in ("top",):
                return skin()
            return top(x, y)
        return f

    p.box(32, 10, 4, 6, 4, upper(False))
    p.box(48, 10, 4, 6, 4, upper(True))

    def forearm(face, x, y, w, h):
        if face == "top":
            return top(x, y) if not short_sleeves else skin()
        if face == "bottom":
            return mix(skin(), SKIN_DARK, 0.5)
        if y == 0 and not short_sleeves and name != "office":
            return top(x, y)
        if name == "office" and y <= 2:
            return top(x, y)
        c = skin()
        if y >= 4:
            c = mix(c, SKIN_DARK, 0.35)
            if y == 5 and face == "front" and x % 2 == 0:
                c = mix(c, BLOOD_DRY, 0.7)
        return c

    p.box(24, 20, 4, 6, 4, forearm)
    p.box(40, 20, 4, 6, 4, forearm)

    def thigh(face, x, y, w, h):
        c = bottom(x, y)
        if face == "front" and y >= 4 and p.rnd.random() < 0.3:
            c = mix(c, BLOOD_DRY, 0.6)
        return c

    def shin(torn):
        def f(face, x, y, w, h):
            if face == "bottom":
                return p.j(o["shoe"], 4)
            if y >= 4:
                return p.j(o["shoe"], 6)
            if torn and face == "front" and y <= 1:
                return skin()
            return bottom(x, y)
        return f

    p.box(0, 30, 4, 6, 4, thigh)
    p.box(16, 30, 4, 6, 4, thigh)
    p.box(32, 30, 4, 6, 4, shin(False))
    p.box(48, 30, 4, 6, 4, shin(True))

    # crawler stub (torn shirt hem, exposed spine) and trailing gut
    def stub(face, x, y, w, h):
        if face == "bottom":
            return (222, 214, 196) if 2 <= x <= 2 and 1 <= y <= 1 else p.j((110, 30, 30), 14)
        return top(x, y) if y == 0 else p.j((96, 26, 26), 12)

    p.box(0, 40, 5, 3, 3, stub)
    p.box(16, 40, 2, 7, 2, lambda f, x, y, w, h: p.j((126, 70, 72) if (x + y) % 2 else (96, 36, 40), 10))
    return p.img


def survivor_skin():
    """Placeholder default survivor skin, vanilla 64x64 layout (base layer only; overlay left empty)."""
    p = Painter(64, 64, seed=3)
    S, JACKET, SHIRT, HAIR, JEANS = (176, 136, 112), (66, 62, 48), (120, 116, 104), (52, 40, 30), (70, 74, 82)

    def head(face, x, y, w, h):
        if face == "top":
            return p.j(HAIR, 6)
        if face == "bottom":
            return p.j(S, 6)
        if face == "front":
            if y <= 1:
                return p.j(HAIR, 6)
            if y == 3 and x in (1, 2, 5, 6):
                return (240, 240, 236) if x in (1, 6) else (60, 70, 80)
            if y >= 5:
                return p.j((78, 62, 48), 8) if y >= 6 or x in (0, 7) else p.j(S, 6)
            return p.j(S, 6)
        return p.j(HAIR, 6) if y <= 3 else p.j(S, 6)

    p.box(0, 0, 8, 8, 8, head)
    p.box(16, 16, 8, 12, 4, lambda f, x, y, w, h: p.j(SHIRT, 6) if f == "front" and 3 <= x <= 4 else p.j(JACKET, 7))
    arm = lambda f, x, y, w, h: p.j(S, 6) if y >= 10 or f == "bottom" else p.j(JACKET, 7)
    p.box(40, 16, 4, 12, 4, arm)
    p.box(32, 48, 4, 12, 4, arm)
    leg = lambda f, x, y, w, h: p.j((40, 32, 26), 5) if y >= 10 or f == "bottom" else p.j(JEANS, 6)
    p.box(0, 16, 4, 12, 4, leg)
    p.box(16, 48, 4, 12, 4, leg)
    return p.img


def preview_atlas(skin):
    """64x128: the skin on top, preview-only props below."""
    p = Painter(64, 128, seed=5)
    p.img.paste(skin, (0, 0))
    p.px = p.img.load()
    steel = lambda *a: p.j((176, 180, 184), 8)
    wood = lambda *a: p.j((140, 104, 66), 8)
    grip = lambda *a: p.j((30, 28, 26), 4)
    p.box(40, 100, 1, 1, 3, grip); p.box(40, 106, 1, 1, 6, steel)
    p.box(30, 64, 1, 1, 9, grip); p.box(0, 64, 2, 2, 12, lambda *a: p.j((150, 118, 80), 8))
    p.box(0, 80, 1, 1, 17, wood); p.box(40, 80, 1, 5, 3, lambda *a: p.j((120, 124, 128), 8))
    p.box(52, 64, 1, 1, 3, grip); p.box(0, 100, 0, 2, 11, steel)
    p.box(24, 100, 1, 21, 1, wood); p.box(30, 100, 3, 1, 1, lambda *a: p.j((70, 60, 50), 6))
    return p.img


if __name__ == "__main__":
    write_json(os.path.join(OUT, "walker.geo.json"), geo("geometry.holdfast.walker", WALKER), indent=2)
    write_json(os.path.join(OUT, "walker_crawler.geo.json"), geo("geometry.holdfast.walker_crawler", CRAWLER), indent=2)
    write_json(os.path.join(OUT, "survivor_preview.geo.json"), geo("geometry.holdfast.survivor_preview", SURVIVOR, 64, 128), indent=2)
    for i, (name, o) in enumerate(OUTFITS.items()):
        walker_texture(name, o, 11 + i * 17).save(os.path.join(OUT, f"walker_{name}.png"))
    skin = survivor_skin()
    skin.save(os.path.join(OUT, "survivor_default.png"))
    preview_atlas(skin).save(os.path.join(OUT, "survivor_preview.png"))
    print("models and textures written")
