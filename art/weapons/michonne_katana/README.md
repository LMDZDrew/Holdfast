# Michonne's Katana (`holdfast_core:michonne_katana`)

Michonne's katana is a core item, `MichonneKatanaItem`, a subclass of `MeleeWeaponItem`. It is in the KATANA weapon class and is two-handed, with the same tier, damage and speed as `katana`. Every server rule is therefore the katana's: swing clips and contact ticks, regional hits, executions, swooshes, impact audio and blood. GeckoLib draws it from a box model built here. It has no recipe, and the one-per-world rule is not implemented yet.

## Input (not committed)

The input is `katana_low_poly.glb`: "Katana (low poly)" by Andrey 3D, licensed CC-BY-4.0, from <https://sketchfab.com/3d-models/katana-low-poly-26e4d48383db4ab4893bc6424383cd61>. The required credit ships in `core/src/main/resources/assets/holdfast_core/credits/michonne_katana.txt`.

## Build

These need Python 3 with numpy, scipy and Pillow, plus Blender 5.2 for the icon. Run from the repository root.

```
PYTHONUTF8=1 python art/weapons/michonne_katana/build_michonne_katana.py KATANA_GLB art/weapons/michonne_katana/build core/src/main/resources
blender --background --factory-startup --python art/boxmodel/render_geo_blender.py -- art/weapons/michonne_katana/build/geo/michonne_katana.geo.json art/weapons/michonne_katana/build/michonne_katana.png art/weapons/michonne_katana/build/render_icon.png diagonal 512
PYTHONUTF8=1 python art/weapons/michonne_katana/build_michonne_katana.py icon art/weapons/michonne_katana/build/render_icon.png core/src/main/resources
```

The first command writes three files to `assets/holdfast_core`:

- `geo/item/michonne_katana.geo.json`: 190 boxes.
- `textures/item/michonne_katana.png`: a 2048×2048 baked atlas.
- `animations/item/michonne_katana.animation.json`: empty. GeckoLib's defaulted item model expects the file.

The last command writes the 64×64 inventory icon, `textures/item/michonne_katana_icon.png`. The item model `models/item/michonne_katana.json` is hand-written:

- It uses `forge:separate_transforms`.
- The hand, ground and fixed views use GeckoLib (`builtin/entity`) with the plain katana's display transforms.
- The inventory uses the flat icon.

`build/` is git-ignored. Its `render_*.png` files are shadeless Blender renders of the built box model and texture.

## How it is built

- **Placement.** The model uses the existing katana's frame, from `art/characters/source/gen_core_assets.py`.
  - The authoring rig's hand frame has the blade along −Z from the fist and the edge toward −Y; the guard face is at z −2.5 and the tip at z −26.
  - `pa_item.model_point` converts it to item space, shrunk by 0.5 for display scale 2.0.
  - The baseline is scaled to that length, 0.43 rig px per baseline unit. It lands where the katana clips were solved: item-space y runs from −2.69 to 12.84, against −2.66 to 12.84 for the plain katana.
  - GeckoLib mirrors cube x and puts the item origin at (8, 8.16, 8). The geometry is therefore item space mapped as (8 − X, Y − 8.16, Z − 8).
  - The edge is the baseline blade's zero-thickness side (−y).
- **Geometry.** The baseline's Sketchfab part names do not match their content: `Same` is the wrap and `Tsuba1` the guard.
  - **Blade:** a sweep of segments along its fitted centre line, denser at the kissaki. Each segment has three nested slabs from edge to spine, at the baseline's thickness profile.
  - **Guard:** a regular 16-gon disc built from rotated strips.
  - **Handle and fittings:** straight segments with a rounded-octagon section, sized from the baseline's extents. The braided wrap mesh is replaced by its outer size, and the wrap is painted on.
- **Texture.** `art/boxmodel/bake.py` with painter functions, all drawn to match Andrew's reference renders:
  - polished steel blade with a tempered edge, shinogi line and the crescent knot near the habaki
  - white tsuka-ito with black diamond windows and gold menuki
  - brushed steel guard with a polished rim and triquetra
  - gold habaki and seppa
  - steel fuchi and kashira

  The UV convention matches both TaCZ and GeckoLib, which I checked against GeckoLib 4.8.4's `BakedModelFactory`, `VertexSet`, `GeoQuad` and `FaceUV.Rotation` bytecode. It is documented in `bake.py`.

## Shared tools

`art/boxmodel` holds the tools shared with `art/guns/ricks_python`: the box fitter, the baker, the numpy preview, the glTF reader and the Blender renderer.
