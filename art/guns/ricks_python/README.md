# Rick's Colt Python (TaCZ gun pack)

A Holdfast TaCZ gun pack, `holdfast:ricks_python`, displayed as **Rick's Colt Python**. Ammunition, damage, fire rate, recoil, reload timing, attachments, sounds, state machine and animations are TaCZ's `tacz:rhino357`: the stats are copied from that gun's data file, and the animations and sounds are referenced, not copied. The index `type` is `pistol`, so Holdfast's execution rules treat it as a handgun. The pack has no crafting recipe. One-per-world uniqueness is future work.

## Inputs (not committed)

- `gameready-colt-python-revolver.zip` supplied by Andrew. The ZIP has no licence file, and the model's origin and licence are unverified. Do not distribute the built pack until that is settled.
- The installed TaCZ 1.1.8-hotfix2 jar, Modrinth version `AzCBJlex`, SHA-256 `2cbb89f4…7f10c2`. It is read for the Rhino rig, stats and display settings only.

## Build

Requires Python 3 with numpy, scipy and Pillow (the same as the character pipeline).

```
blender --background --factory-startup --python art/guns/ricks_python/render_icons.py -- MODEL_ZIP art/guns/ricks_python/build/icons
PYTHONUTF8=1 python art/guns/ricks_python/build_ricks_python.py MODEL_ZIP TACZ_JAR art/guns/ricks_python/build art/guns/ricks_python/build/icons
```

The first command is optional. Blender 5.2.2 renders the original mesh, in polished nickel with the source walnut albedo and normal map, as TaCZ-style hotbar and HUD images. Without it, the build renders both images from the box model.

Output goes to the git-ignored `build/`:

- `holdfast_ricks_python.zip`: the pack. Install it in the `tacz/` folder of both the server and the client.
- `holdfast_ricks_python/`: the same pack, unzipped.
- `build_report.json`: hashes, scale, rig offset and box counts.
- `preview_rhino_vs_python.png`: Rhino and Python posed side by side at the same `rhino357` keyframes. The whole-gun motion is removed; side and rear views.

## How it is built

- **Geometry** (`box_fit.py`). TaCZ loads Bedrock box geometry only. Each named FBX part is rebuilt from a few large, rotated boxes, which keeps the surfaces smooth, like TaCZ's own guns. The result is about 550 boxes; the Rhino has about 480.
  - Flat parts are fitted from their side outline. The outline is split by thickness, and each band is covered greedily by the largest rectangles at a few angles, rotated about x.
  - The walnut grip uses bevelled pairs of crossed slabs, so its rounded sides read as chamfered rather than terraced. The steel grip frame is cut out under the walnut so the two cannot z-fight.
  - The cylinder, ejector rod, extractor star and forcing cone are regular polygon prisms built from strips rotated about their axis. The cylinder is a 12-gon whose alternate faces are darker flutes.
  - The chamber mouths and the muzzle bore are dark discs.
  - The cylinder's rear face also has six dark empty chambers. The cartridge rims cover them until the rounds are ejected, and the extractor star is cut down to stay inside the chamber ring.
  - Iron sights match Andrew's reference photo. The rear sight is nickel and built explicitly with the source's notch, which outline fitting would fill. The ramp front sight is fitted from the source's side profile at its real width. The ADS eye point (`iron_view`) is on the blade top.
- **Finish** (Andrew, 2026-10-18): smooth, shiny nickel with walnut grips. `bake.py` gives every visible box face its own patch of a 1024×1024 atlas at 32 px per model unit.
  - Walnut texels sample the source albedo at the nearest point of the grip mesh: real grain, checkering borders and medallion.
  - Nickel texels are baked polished shading: a horizon reflection on the sides, bright tops, dark undersides, the source's occlusion and albedo detail, and a dark one-pixel edge. The cylinder's flute faces are darker.
  - TaCZ's per-face UV orientation, read from its bytecode, is documented at the top of `bake.py`.
  - Cartridges, the speed loader and the bores use flat swatches in the atlas's first row.
- **Placement.** The gun is scaled at 0.51 model units per real centimetre, TaCZ's own pistol scale: its default pistols measure 0.48–0.62 and the Rhino 0.51. The first pass matched the Rhino's cylinder (0.418) and looked undersized. It is placed so its trigger sits where the Rhino's trigger sits, which keeps TaCZ's hand poses on the grip. It is mirrored in x, because the gun's left side is +x in TaCZ space.
- **Rig.** It uses the Rhino's bone names, hierarchy and pivots. The reload bones keep the Rhino's relative layout and are all shifted together by one offset onto the Python's cylinder axis and rear face. These are the crane, cylinder, ejector, the six chambered rounds and their bullets, and the speed loader at the Rhino's 1/10 scale. The swung-out cylinder, ejected cases and speed loader therefore follow the Rhino's paths together. The hammer, trigger and latch pivot on pins measured on the Python mesh, given in source centimetres in the script. The rear sight, front sight, muzzle, laser and scope points, and the ADS (`iron_view`) height, come from the Python geometry.
- **Icons.** The slot icon is 64×64: a shaded 3/4 view, muzzle to the right and tipped down, like TaCZ's own icons. The HUD image is a light-grey side view with the muzzle left, filling the height of the 1299×433 image.
- **Rounds and loader.** The cartridges and speed loader are original boxes sized from real .357 Magnum dimensions and fitted to the spaces the Rhino's rounds occupy.

## Verification

- `preview_geo.py` is a z-buffered preview. It was checked against the Rhino reload: the speed loader meets the swung-out cylinder.
- `core/tools/gunprobe` is an opt-in isolated dev-client probe, run with `runClient -PgunPackProbe=<evidence dir>`. It screenshots both guns in TaCZ's own renderer: rest, reload, ADS, fire, third person and inspect. See `docs/testing.md`.
- Neither replaces Andrew's in-game review.
