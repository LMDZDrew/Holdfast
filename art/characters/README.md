# Characters: walker, lower-half crawler, player

Specification: [`docs/animation.md`](../../docs/animation.md). Clip list: [`docs/animation_catalog.md`](../../docs/animation_catalog.md).

| Folder | Contents | Status |
|---|---|---|
| `source/` | Python generators (editable source of every pose), validators, preview builder, headless page test | source |
| `export/assets/holdfast/` | Geometry, GeckoLib clips, Player Animator clips, extras, textures, laid out as mod resources | exported, statically validated, **not tested in game** |
| `preview/holdfast_character_preview.html` | Browser preview of the exported clips (needs internet for three.js from cdnjs/jsdelivr) | preview |
| `preview/frames/*.png` | Frame sheets from our software renderer | preview |
| `preview/rig/` | Preview-only player stand-in, authored player key poses, pairing and speed tables | preview / source data |

Textures are procedural **placeholders**. `build/` is a gitignored staging folder.

Regenerate with `source/make_all.sh`.
