"""Shared animation tooling: key-pose authoring, Catmull-Rom sampling, export, and a parametric gait generator.

Conventions (Bedrock / Blockbench / GeckoLib values, degrees and pixels):
  limb rotation x  : negative swings the limb forward, positive backward
  shin x           : positive bends the knee (foot goes back)
  forearm x        : negative bends the elbow (hand comes forward)
  torso x          : positive leans forward
  head x           : positive looks down, negative looks up
  jaw x            : positive opens the mouth
  rightArm z       : positive lifts it out to the side (leftArm uses negative)
  torso / head z   : positive tilts toward the model's left
  y                : positive turns toward the model's right
  position         : y is up, negative z is forward, positive x is the model's left
"""
import json, math, os

TAU = math.tau
S, C = math.sin, math.cos
ROOT = os.path.join(os.path.dirname(__file__), "..")
OUT = os.path.join(ROOT, "build")          # staging; make_all.sh copies finished files to export/
os.makedirs(OUT, exist_ok=True)


def pos(v):
    return max(0.0, v)


def r2(v):
    return round(v + 0.0, 2)


def fmt_t(t):
    s = f"{t:.4f}".rstrip("0")
    return s + "0" if s.endswith(".") else s


def sample(keys, ts, t):
    """Blockbench-style Catmull-Rom between neighbouring keyframes, clamped at the ends."""
    if t <= ts[0]:
        return keys[ts[0]]
    if t >= ts[-1]:
        return keys[ts[-1]]
    lo, hi = 0, len(ts) - 1
    while hi - lo > 1:
        m = (lo + hi) // 2
        if ts[m] <= t:
            lo = m
        else:
            hi = m
    i = lo
    t1, t2 = ts[i], ts[i + 1]
    p1, p2 = keys[t1], keys[t2]
    p0 = keys[ts[i - 1]] if i > 0 else p1
    p3 = keys[ts[i + 2]] if i + 2 < len(ts) else p2
    u = (t - t1) / (t2 - t1)
    return [0.5 * ((2 * b) + (-a + c) * u + (2 * a - 5 * b + 4 * c - d) * u * u
                   + (-a + 3 * b - 3 * c + d) * u ** 3) for a, b, c, d in zip(p0, p1, p2, p3)]


class Anim:
    def __init__(self, length, loop, bones_allowed=None):
        self.length = length
        self.loop = loop
        self.tracks = {}          # bone -> channel -> {time: [x,y,z]}
        self.sounds = {}
        self.timeline = {}
        self.allowed = bones_allowed

    def key(self, t, bone, rot=None, pos=None):
        if self.allowed is not None and bone not in self.allowed:
            raise ValueError(f"bone {bone} not allowed in this animation layer")
        t = round(t, 4)
        for ch, v in (("rotation", rot), ("position", pos)):
            if v is not None:
                self.tracks.setdefault(bone, {}).setdefault(ch, {})[t] = [r2(x) for x in v]
        return self

    def pose(self, t, **bones):
        """pose(t, head=[x,y,z], hips_pos=[x,y,z], ...). A *_pos name keys position."""
        for name, v in bones.items():
            if name.endswith("_pos"):
                self.key(t, name[:-4], pos=v)
            else:
                self.key(t, name, rot=v)
        return self

    def last_key(self):
        return max((t for ch in self.tracks.values() for keys in ch.values() for t in keys), default=0.0)

    def fit_length(self):
        """Make animation_length cover every key (a key past the end is never reached)."""
        self.length = round(max(self.length, self.last_key()), 4)
        return self

    def sound(self, t, effect):
        self.sounds[round(t, 4)] = effect
        return self

    def event(self, t, name):
        self.timeline[round(t, 4)] = name
        return self

    def value(self, bone, ch, t):
        keys = self.tracks.get(bone, {}).get(ch)
        if not keys:
            return None
        ts = sorted(keys)
        if self.loop is True:
            t = t % self.length
        else:
            t = min(max(t, 0.0), self.length)
        return sample(keys, ts, t)

    def to_json(self, smooth=True):
        """smooth=True: Catmull-Rom key poses (GeckoLib). smooth="linear": explicit linear keys
        (Player Animator reads bare rotation arrays as stepped, so baked files must say linear)."""
        bones = {}
        for b, chans in self.tracks.items():
            bones[b] = {}
            for ch, keys in chans.items():
                out = {}
                for t in sorted(keys):
                    v = keys[t]
                    if smooth is True:
                        out[fmt_t(t)] = {"post": v, "lerp_mode": "catmullrom"}
                    elif smooth == "linear":
                        out[fmt_t(t)] = {"post": v, "lerp_mode": "linear"}
                    else:
                        out[fmt_t(t)] = v
                bones[b][ch] = out
        a = {"loop": self.loop, "animation_length": self.length, "bones": bones}
        if self.sounds:
            a["sound_effects"] = {fmt_t(t): {"effect": e} for t, e in sorted(self.sounds.items())}
        if self.timeline:
            a["timeline"] = {fmt_t(t): e for t, e in sorted(self.timeline.items())}
        return a

    def baked(self, step=0.05):
        """Sample the curves into dense linear keys (for loaders without catmullrom)."""
        b2 = Anim(self.length, self.loop)
        b2.sounds, b2.timeline = dict(self.sounds), dict(self.timeline)
        n = int(round(self.length / step))
        for b, chans in self.tracks.items():
            for ch, keys in chans.items():
                ts = sorted(keys)
                for i in range(n + 1):
                    t = round(min(i * step, self.length), 4)
                    b2.tracks.setdefault(b, {}).setdefault(ch, {})[t] = [r2(x) for x in sample(keys, ts, t)]
        return b2


def write_json(path, data, indent=1):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=indent)


def anim_file(prefix, anims):
    return {"format_version": "1.8.0",
            "animations": {f"{prefix}.{k}": v.to_json() for k, v in anims.items()}}


# ------------------------------------------------------------------ gaits
BODY = {"root", "hips", "torso", "rightLeg", "leftLeg", "rightShin", "leftShin"}
ARMS = {"rightArm", "leftArm", "rightForearm", "leftForearm"}
HEAD = {"head", "jaw"}
LEG_LEN = 12.0


def gait(L, stride=(24, 18), lift=(28, 12), lift_base=(8, 5), lean=14, lean_var=3, roll=5, twist=6,
         hip_roll=3, sway=0.6, bob=(0.8, 1.6), dip_r=0.0, skew=0.5, splay=(2, -2), circ=(0, 0),
         wobble=0.0, seed=0.0, stumble=None, n=16):
    """A walk cycle on the body layer only (hips, torso, legs).

    stride   : (right, left) thigh swing amplitude in degrees
    lift     : (right, left) extra knee bend during the swing; lift_base is the bend at stance
    skew     : share of the cycle the right leg spends swinging (0.5 = even rhythm)
    circ     : (right, left) outward hip swing for a stiff leg
    dip_r    : extra hip drop when standing on the right leg (a bad leg)
    wobble   : irregular torso sway; stumble = (cycle position, size) of a forward stumble
    Returns (Anim, ground speed in blocks per second at 1x).
    """
    a = Anim(L, True, BODY)
    sr, sl = stride
    lr, ll = lift
    br, bl = lift_base
    for i in range(n + 1):
        u = i / n
        p = math.pi * u / skew if u < skew else math.pi + math.pi * (u - skew) / (1 - skew)
        t = L * u
        s, c = S(p), C(p)
        tor_x = lean + lean_var * s * s + wobble * 0.5 * S(2 * p + seed * 2)
        tor_z = roll * s + wobble * S(3 * p + seed)
        hip_y = -(c * c) * (bob[0] if c > 0 else bob[1]) - dip_r * pos(-s)
        if stumble:
            u0, amt = stumble
            d = min(abs(u - u0), 1 - abs(u - u0))
            k = math.exp(-(d / 0.07) ** 2)
            tor_x += amt * k
            hip_y -= amt * 0.08 * k
        a.pose(t,
               rightLeg=[sr * c, 0, splay[0] + circ[0] * pos(s)],
               leftLeg=[-sl * c, 0, splay[1] - circ[1] * pos(-s)],
               rightShin=[br + lr * pos(s) ** 1.3, 0, 0],
               leftShin=[bl + ll * pos(-s) ** 1.3, 0, 0],
               hips_pos=[sway * s, hip_y, 0],
               hips=[0, 0, hip_roll * s],
               torso=[tor_x, -twist * c, tor_z])
    # each stance sweeps the hip forward by 2 * leg * sin(amplitude) of the stance leg
    travel = 2 * LEG_LEN * (math.sin(math.radians(sr)) + math.sin(math.radians(sl)))
    speed_bps = travel / L / 16.0
    return a, speed_bps


def loop_keys(a, L, frames, **tracks):
    """Key a loop from parallel lists: tracks = {bone: [[x,y,z], ...]}, frames evenly spaced over L."""
    n = frames
    for i in range(n + 1):
        t = L * i / n
        for bone, vals in tracks.items():
            v = vals[i % n]
            if bone.endswith("_pos"):
                a.key(t, bone[:-4], pos=v)
            else:
                a.key(t, bone, rot=v)
    return a


# ------------------------------------------------------------- rotation helpers
def bb_matrix(e):
    """3x3 matrix for a Blockbench/Bedrock Euler (degrees), same convention as fk.local."""
    import numpy as np
    x, y, z = -math.radians(e[0]), -math.radians(e[1]), math.radians(e[2])
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def bb_euler(M):
    """Inverse of bb_matrix: Blockbench Euler (degrees) for a rotation matrix (ZYX order)."""
    b = math.asin(max(-1.0, min(1.0, -M[2, 0])))
    a = math.atan2(M[2, 1], M[2, 2])
    c = math.atan2(M[1, 0], M[0, 0])
    return [-math.degrees(a), -math.degrees(b), math.degrees(c)]
