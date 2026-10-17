"""Held-item frame: how Minecraft 1.20.1 and Player Animator 1.0.2-rc1 place an item in the hand, and the
conversion from our authoring rig's wrist bone (rightItem/leftItem, a child of the arm about the fist) to the
values Player Animator needs.

Read from the game and library, not assumed:
  - ItemInHandLayer.renderArmWithItem (1.20.1, javap of the Forge 47.4.8 mapped jar): arm.translateAndRotate
    (pivot, then Quaternionf.rotationZYX(z, y, x)), then mulPose(XP -90deg), mulPose(YP 180deg),
    translate(+-1/16, 2/16, -10/16) (+ right hand, - left hand), then ItemInHandRenderer.renderItem.
  - Player Animator HeldItemMixin.changeItemLocation (branch 1.20, a17ecb85), injected just before renderItem:
    translate(pos / 16), then mulPose(ZP z), mulPose(YP y), mulPose(XP x), radians from the JSON degrees.
  - GeckoLibSerializer.readCollection: item rotation values are used as written; item position y is negated.
  - ItemRenderer: the item's display transform, then translate(-0.5, -0.5, -0.5), then the model elements (px).

All maths here is in Minecraft model space: pixels, y down, the model faces -z, the right arm sits at -x.
A Bedrock/Blockbench point (bx, by, bz) is (bx, 24 - by, bz) there, and our Euler convention (lib.bb_matrix in
three.js space) is Rz(z) Ry(y) Rx(x) there with the same numbers.

Our authoring rig turns the weapon about the fist in the arm frame: p = f + R (w - f).
Player Animator turns it about the vanilla grip point in the hand frame: p = h + A (pos + R_pa m).
With R_pa = A^T R A, pos = A^T (f - h) and item-model points m = A^T (w - f) the two are identical, which
self_test() checks. The vanilla slim (Alex) arm shifts the hand 0.5 px sideways; not modelled.
"""
import math
import numpy as np


def rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def zyx(e_deg):
    x, y, z = (math.radians(v) for v in e_deg)
    return rz(z) @ ry(y) @ rx(x)


def zyx_euler(M, near=None):
    """Degrees (x, y, z) with M = Rz Ry Rx, unwrapped towards `near` so keyed tracks stay continuous."""
    y = math.asin(max(-1.0, min(1.0, -M[2, 0])))
    x = math.atan2(M[2, 1], M[2, 2])
    z = math.atan2(M[1, 0], M[0, 0])
    sols = [np.degrees([x, y, z])]
    sols.append(np.degrees([x + math.pi, math.pi - y, z + math.pi]))       # the other ZYX solution
    best = None
    for s in sols:
        if near is not None:
            s = np.array([v + 360.0 * round((n - v) / 360.0) for v, n in zip(s, near)])
        if best is None or (near is not None and np.sum((s - near) ** 2) < np.sum((best - near) ** 2)):
            best = s
    return best


A = rx(math.radians(-90)) @ ry(math.radians(180))           # hand frame relative to the arm part
ARM_PIVOT = {"rightItem": np.array([-5.0, 2.0, 0.0]), "leftItem": np.array([5.0, 2.0, 0.0])}
FIST_BB = {"rightItem": (-6.0, 12.5, 0.0), "leftItem": (6.0, 12.5, 0.0)}     # build_model.FIST_R / FIST_L
HAND_SIDE = {"rightItem": 1.0, "leftItem": -1.0}


def bb_to_mc(p):
    return np.array([p[0], 24.0 - p[1], p[2]], float)


def fist(part):
    """Our wrist pivot in the arm frame (relative to the arm pivot)."""
    return bb_to_mc(FIST_BB[part]) - ARM_PIVOT[part]


def grip(part):
    """Vanilla grip point (where Player Animator rotates the item) in the arm frame."""
    return A @ np.array([HAND_SIDE[part], 2.0, -10.0])


def pa_position(part):
    """Constant Player Animator position key (JSON values) that moves its pivot onto our fist."""
    p = A.T @ (fist(part) - grip(part))
    return [round(float(p[0]), 4), round(float(-p[1]), 4), round(float(p[2]), 4)]


def to_pa_rotation(e_deg, near=None):
    return zyx_euler(A.T @ zyx(e_deg) @ A, near)


def from_pa_rotation(e_deg, near=None):
    return zyx_euler(A @ zyx(e_deg) @ A.T, near)


def model_point(part, w_bb):
    """Item-model element coordinate (px, 0..16 is the unit block) for a point of our weapon geometry."""
    return A.T @ (bb_to_mc(w_bb) - ARM_PIVOT[part] - fist(part)) + 8.0


def self_test(n=200, seed=3):
    rng = np.random.default_rng(seed)
    # our Euler in three.js space is the same numbers in Minecraft model space
    import lib
    S = np.diag([-1.0, -1.0, 1.0])
    worst = 0.0
    for _ in range(n):
        e = rng.uniform(-180, 180, 3)
        worst = max(worst, float(np.abs(S @ lib.bb_matrix(e) @ S - zyx(e)).max()))
        for part in ("rightItem", "leftItem"):
            w = rng.uniform(-10, 10, 3) + np.array(FIST_BB[part])
            ours = fist(part) + zyx(e) @ (bb_to_mc(w) - ARM_PIVOT[part] - fist(part))
            pos = np.array(pa_position(part)) * [1, -1, 1]
            m = model_point(part, w) - 8.0
            pa = grip(part) + A @ (pos + zyx(to_pa_rotation(e)) @ m)
            worst = max(worst, float(np.abs(ours - pa).max()))
            worst = max(worst, float(np.abs(zyx(from_pa_rotation(to_pa_rotation(e))) - zyx(e)).max()))
    return worst


if __name__ == "__main__":
    print("A =", np.round(A).astype(int).tolist())
    for part in ("rightItem", "leftItem"):
        print(part, "fist", fist(part), "grip", np.round(grip(part), 4), "position key", pa_position(part))
    w = self_test()
    print(f"self test worst difference {w:.2e}")
    raise SystemExit(0 if w < 1e-6 else 1)
