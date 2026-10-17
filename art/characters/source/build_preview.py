"""Inline every exported file into the preview page."""
import base64, json, os, glob
from lib import OUT
import walker_anims as wa
from player_anims import PLAYER_SCALE

HERE = os.path.dirname(__file__)


def j(name):
    with open(os.path.join(OUT, name)) as f:
        return json.load(f)


def img(name):
    with open(os.path.join(OUT, name), "rb") as f:
        return "data:image/png;base64," + base64.b64encode(f.read()).decode()


cams, meta = {}, {}
for p in glob.glob(os.path.join(OUT, "player_animation_extras", "*.extras.json")):
    d = json.load(open(p))
    for k, v in d["animations"].items():
        cams[k] = {**v, "bones": {"camera": v["bones"]["camera"]}}
        meta[k] = d.get("holdfast_meta", {})

data = {
    "walkerGeo": j("walker.geo.json"), "crawlerGeo": j("walker_crawler.geo.json"), "survGeo": j("survivor_preview.geo.json"),
    "walkerAnims": j("walker.animation.json")["animations"], "crawlerAnims": j("walker_crawler.animation.json")["animations"],
    "playerAnims": j("player_keys.animation.json")["animations"], "cameras": cams,
    "speeds": j("gait_speeds.json"), "execs": wa.EXECS, "grabDist": wa.GRAB_DIST, "eye": 25.92,
    "pairing": j("pairing.json"), "meta": meta, "moveSpeeds": j("player_move_speeds.json"),
    "playerScale": PLAYER_SCALE,
    "textures": {n: img(f"walker_{n}.png") for n in ["farmer", "tshirt", "scrubs", "office"]},
    "survTex": img("survivor_preview.png"),
}
blob = json.dumps(data, separators=(",", ":")).replace("</", "<\\/")
with open(os.path.join(HERE, "preview_template.html")) as f:
    html = f.read().replace("__DATA__", blob)
PREVIEW = os.path.join(HERE, "..", "preview")
os.makedirs(PREVIEW, exist_ok=True)
with open(os.path.join(PREVIEW, "holdfast_character_preview.html"), "w") as f:
    f.write(html)
print(f"{len(html) / 1024:.0f} KB")
