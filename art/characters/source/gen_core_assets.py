"""Item models, icons, armor textures and lang for the holdfast_core mod (core/src/main/resources/assets/holdfast_core).

Weapons with a preview bone (knife, machete, fire axe, baseball bat) reuse that geometry; hatchet, katana and
sledgehammer get new geometry in the same convention (blade along -Z from the fist, edge -Y). All are placed in
Player Animator's hand frame (pa_item.model_point) with identity third-person transforms, so a held core weapon
sits exactly where the clips were solved for it (testing.md: held-item frame fix). Placeholder art, not final.
"""
import json, os
from PIL import Image
import build_model as bm
import pa_item

HERE = os.path.dirname(__file__)
RES = os.path.join(HERE, "..", "..", "..", "core", "src", "main", "resources", "assets", "holdfast_core")
AUDIO_LANG = os.path.join(HERE, "..", "..", "audio", "subtitles.json")
MODID = "holdfast_core"

SWATCH = {"steel": (0, 0, (196, 200, 210)), "edge": (4, 0, (236, 238, 242)), "wood": (8, 0, (128, 92, 56)),
          "grip": (12, 0, (52, 40, 32)), "bat": (0, 4, (186, 146, 96)), "red": (4, 4, (150, 32, 28)),
          "dark": (8, 4, (60, 62, 68)), "tape": (12, 4, (30, 30, 30))}
FX, FY = -6.0, 12.5      # fist (bedrock), build_model.FIST_R


def cube(x0, y0, z0, sx, sy, sz):
    return dict(origin=[x0, y0, z0], size=[sx, sy, sz])


def preview(bone):
    return [dict(origin=c["origin"], size=c["size"]) for c in next(b for b in bm.SURVIVOR if b["name"] == bone)["cubes"]]


NEW = {
    # one-handed hatchet: short haft, small head hanging below its end
    "hatchet": [cube(FX - 0.6, FY - 0.6, -9.0, 1.2, 1.2, 11.0), cube(FX - 0.5, FY - 4.0, -9.5, 1.0, 4.4, 2.8)],
    # katana: wrapped grip, small guard, long slim blade, edge down
    "katana": [cube(FX - 0.6, FY - 0.6, -2.5, 1.2, 1.2, 7.5), cube(FX - 1.5, FY - 1.5, -3.2, 3.0, 3.0, 0.7),
               cube(FX - 0.3, FY - 1.2, -26.0, 0.6, 1.8, 22.8)],
    # sledgehammer: long haft, heavy block head
    "sledgehammer": [cube(FX - 0.7, FY - 0.7, -17.0, 1.4, 1.4, 20.0), cube(FX - 2.0, FY - 2.5, -20.0, 4.0, 5.0, 3.6)],
}
ITEMS = {
    "knife": (preview("knife"), ["grip", "steel"], "Kitchen Knife"),
    "machete": (preview("machete"), ["grip", "steel"], "Machete"),
    "fire_axe": (preview("axe"), ["red", "steel"], "Fire Axe"),
    "baseball_bat": (preview("bat"), ["tape", "bat"], "Baseball Bat"),
    "hatchet": (NEW["hatchet"], ["wood", "steel"], "Hatchet"),
    "katana": (NEW["katana"], ["tape", "dark", "edge"], "Katana"),
    "sledgehammer": (NEW["sledgehammer"], ["wood", "dark"], "Sledgehammer"),
}
GUARDS = {  # item -> (armor material id, colour, display name, layer)
    "padded_collar": ("padded_leather", (102, 74, 48), "Padded Collar", 1),
    "padded_sleeves": ("padded_leather", (102, 74, 48), "Padded Sleeves", 1),
    "riot_collar": ("riot", (40, 44, 50), "Riot Collar", 1),
    "riot_vest": ("riot", (40, 44, 50), "Riot Vest", 1),
    "riot_shin_guards": ("riot", (40, 44, 50), "Riot Shin Guards", 1),
    "chain_sleeves": ("chain_mesh", (150, 152, 158), "Chain-Mesh Sleeves", 1),
}
IDENTITY = {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}


def fits(cubes, k):
    for c in cubes:
        o, s = c["origin"], c["size"]
        for p in (o, [o[i] + s[i] for i in range(3)]):
            m = (pa_item.model_point("rightItem", p) - 8.0) * k + 8.0
            if not all(-16 <= v <= 32 for v in m):
                return False
    return True


def elements(cubes, mats, k=1.0):
    """k < 1 shrinks about the hand-frame pivot (element 8,8,8); the display scale 1/k restores it exactly, because
    item display transforms scale about the block centre, which is that pivot."""
    out = []
    for c, mat in zip(cubes, mats):
        o, s = c["origin"], c["size"]
        a = (pa_item.model_point("rightItem", o) - 8.0) * k + 8.0
        b = (pa_item.model_point("rightItem", [o[i] + s[i] for i in range(3)]) - 8.0) * k + 8.0
        lo = [round(float(min(x, y)), 4) for x, y in zip(a, b)]
        hi = [round(float(max(x, y)), 4) for x, y in zip(a, b)]
        assert all(-16 <= v <= 32 for v in lo + hi), (lo, hi)
        u, v, _ = SWATCH[mat]
        out.append({"from": lo, "to": hi, "faces": {f: {"uv": [u, v, u + 4, v + 4], "texture": "#t"}
                                                    for f in ("north", "east", "south", "west", "up", "down")}})
    return out


def armor_layer(colour, path, w=64, h=32):
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    r, g, b = colour
    for y in range(h):
        for x in range(w):
            k = ((x * 7 + y * 13) % 5) - 2
            img.putpixel((x, y), (max(0, r + k * 4), max(0, g + k * 4), max(0, b + k * 4), 255))
    img.save(path)


def main():
    mdir, tdir, adir, ldir = (os.path.join(RES, d) for d in ("models/item", "textures/item", "textures/models/armor", "lang"))
    for d in (mdir, tdir, adir, ldir):
        os.makedirs(d, exist_ok=True)
    atlas = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for u, v, col in SWATCH.values():
        for y in range(v, v + 4):
            for x in range(u, u + 4):
                atlas.putpixel((x, y), col + (255,))
    atlas.save(os.path.join(tdir, "weapons_atlas.png"))
    lang = {"itemGroup.holdfast_core": "Holdfast", "entity.holdfast_core.walker": "Walker",
            "item.holdfast_core.walker_spawn_egg": "Walker Spawn Egg",
            "entity.holdfast_core.crawler": "Crawler",
            "item.holdfast_core.crawler_spawn_egg": "Crawler Spawn Egg",
            "key.categories.holdfast_core": "Holdfast", "key.holdfast_core.action": "Escape / Execute / Cancel",
            "hud.holdfast_core.grip": "BREAK THE GRIP", "hud.holdfast_core.escape_once": "Press once to escape",
            "hud.holdfast_core.shove": "%s  SHOVE IT OFF", "hud.holdfast_core.kick_free": "%s  KICK FREE",
            "hud.holdfast_core.grab.escaped": "You broke free", "hud.holdfast_core.grab.released": "It let go",
            "hud.holdfast_core.grab.bitten_neck": "Bitten: neck", "hud.holdfast_core.grab.bitten_arm": "Bitten: forearm",
            "hud.holdfast_core.grab.bitten_leg": "Bitten: leg",
            "hud.holdfast_core.bitten": "Bitten (%s)", "hud.holdfast_core.amputation_window": "Amputation possible: %s:%s",
            "message.holdfast_core.escape_late": "Too late",
            "message.holdfast_core.bite_blocked": "The bite didn't get through",
            "message.holdfast_core.bite_blocked_broke": "The bite didn't get through, but the guard is ruined",
            "tooltip.holdfast_core.bite_guard": "Stops %s more bites (%s)",
            "tooltip.holdfast_core.bite_guard_broken": "Torn through: no bite protection (%2$s)",
            "death.attack.holdfast_core.walker_infection": "%1$s succumbed to a walker bite",
            "death.attack.holdfast_core.walker_infection.player": "%1$s succumbed to a walker bite"}
    for r in ("weapon_cannot_execute", "no_clip_for_posture", "too_far", "not_facing", "head_covered", "target_busy",
              "already_executed", "blunt_not_charged", "too_close", "obstructed", "no_room", "no_ammo", "off_hand_occupied"):
        lang[f"message.holdfast_core.execute_refused.{r}"] = {
            "weapon_cannot_execute": "Can't finish it with this", "no_clip_for_posture": "No way to finish it like this",
            "too_far": "Too far away", "not_facing": "Face it first", "head_covered": "Its head is covered",
            "target_busy": "It's on someone", "already_executed": "Already being finished",
            "blunt_not_charged": "Wind up first (hold use)", "too_close": "Too close; step back",
            "obstructed": "Something's in the way", "no_room": "No room to do it here", "no_ammo": "Out of ammo",
            "off_hand_occupied": "This weapon needs both hands free"}[r]
    for item, (cubes, mats, name) in ITEMS.items():
        k = 1.0 if fits(cubes, 1.0) else 0.5
        held = {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1 / k] * 3}
        model = {"textures": {"t": f"{MODID}:item/weapons_atlas", "particle": f"{MODID}:item/weapons_atlas"},
                 "elements": elements(cubes, mats, k),
                 "display": {"thirdperson_righthand": held, "thirdperson_lefthand": held,
                             "firstperson_righthand": held, "firstperson_lefthand": held,
                             "gui": {"rotation": [30, 45, 0], "translation": [0, 0, 0], "scale": [0.42, 0.42, 0.42]},
                             "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
                             "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]}}}
        json.dump(model, open(os.path.join(mdir, f"{item}.json"), "w"), indent=1)
        lang[f"item.{MODID}.{item}"] = name
    done = set()
    for item, (mat, colour, name, _) in GUARDS.items():
        icon_img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for y in range(3, 13):
            for x in range(3, 13):
                if (x + y) % 7 != 0:
                    icon_img.putpixel((x, y), colour + (255,))
        icon_img.save(os.path.join(tdir, f"{item}.png"))
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"{MODID}:item/{item}"}},
                  open(os.path.join(mdir, f"{item}.json"), "w"), indent=1)
        lang[f"item.{MODID}.{item}"] = name
        if mat not in done:
            armor_layer(colour, os.path.join(adir, f"{mat}_layer_1.png"))
            armor_layer(colour, os.path.join(adir, f"{mat}_layer_2.png"))
            done.add(mat)
    for egg in ("walker_spawn_egg", "crawler_spawn_egg"):
        json.dump({"parent": "minecraft:item/template_spawn_egg"}, open(os.path.join(mdir, egg + ".json"), "w"))
    if os.path.exists(AUDIO_LANG):
        lang.update(json.load(open(AUDIO_LANG)))
    # Iconic weapons with their own GeckoLib models (art/weapons); their item models are not generated here.
    lang[f"item.{MODID}.michonne_katana"] = "Michonne's Katana"
    json.dump(lang, open(os.path.join(ldir, "en_us.json"), "w"), indent=1)
    print(f"wrote {len(ITEMS)} weapon models, {len(GUARDS)} guards, {len(lang)} lang keys")


if __name__ == "__main__":
    main()
