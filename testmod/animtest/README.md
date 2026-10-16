# Holdfast animation test harness (`holdfast_animtest`)

**Test-only.** Loads the exported walker, crawler and player clips from `art/characters/export` into Minecraft so they can be checked by eye and by log. It has no gameplay: no hits, bites, escapes, kills, damage or AI. Do not ship it.

| Item | Value |
|---|---|
| Platform | Minecraft 1.20.1, Forge 47.4.8 (Gate 0 build), Java 17 |
| Build | Forge 1.20.1 MDK layout: ForgeGradle `[6.0,6.2)`, Gradle 8.8 wrapper (copied from MinecraftForge branch `1.20.1`), official mappings |
| Libraries | GeckoLib 4.8.4 (Modrinth version `aC5KMoNg`) and Player Animator 1.0.2-rc1+1.20 (`xe2EVE6q`), the exact jars Gate 0 hash-verified |
| Assets | `art/characters/export` is added as a resource folder, so the jar always carries the committed exports unchanged |
| Status | **Written without a compile** (this workspace cannot reach Forge or Mojang). First build happens on Andrew's PC |

## Build

```
cd testmod/animtest
gradlew build          (Windows: .\gradlew.bat build)
```

Output: `build/libs/holdfast_animtest-0.1.0.jar`. Put it in the client instance's `mods` folder and the dedicated server's `mods` folder, next to the two library jars.

## What loads, and what it checks

- On world join the client runs a **load check**: GeckoLib loaded both geo files and both animation files, every exported clip name exists, every bone an animation keys exists in its model (GeckoLib skips missing bones silently), and the loaded lengths match the exports; Player Animator registered all 30 `holdfast:` player clips. One `PASS`/`FAIL` line per clip in the log, summary in chat.
- Every start, timeline event, sound cue and finish is logged with ticks. All lines start with `[holdfast-test]`.

## Commands (operator, `/hfa ...`)

| Command | Does |
|---|---|
| `/hfa spawn` / `/hfa spawn crawler` | Test walker (or lower-half crawler) 3 blocks ahead, facing you. No AI, invulnerable; `/kill @e[type=holdfast_animtest:test_walker]` removes it |
| `/hfa walker <clip>` | Nearest test walker plays a clip. `body.*`, `arms.*`, `head.*` replace one layer; anything else is full-body. Crawler clips switch the walker to the crawler rig |
| `/hfa mix <body.x> <arms.x> <head.x>` | Set all three layers at once (the "no two walkers alike" combinations) |
| `/hfa skin <0-3>` | farmer, tshirt, scrubs, office placeholder textures |
| `/hfa player <clip>` | You play a Player Animator clip; others tracking you see it too |
| `/hfa pair <preset>` | Places the walker at the exported pairing distance in front of you, levels your view, and starts both clips on the same client frame (grabs, ankle grabs, executions) |
| `/hfa fp <mode>` | First-person mode for later clips: `THIRD_PERSON_MODEL` (default), `VANILLA`, `NONE`, `DISABLED` |
| `/hfa camera <flags>` | First-person camera rotation track: 1 on (default), 0 off, +2 flip yaw, +4 flip roll, +8 flip pitch (for diagnosing sign errors) |
| `/hfa stop` | Stop your clip and reset the walker to idle |

## Known gaps (by design for now)

- The camera **position** track in the extras files is not applied (Forge 1.20.1 has no camera-position event; needs a hook later). Rotation is applied.
- Sounds are logged, not played (no sounds registered yet).
- Held items are whatever you hold; the preview weapon shapes are not real item models.
- The legless crawl clip expects the vanilla swimming/crawling pose: get into it by crawling under a trapdoor or a 1-block gap.
- Pair timing is aligned on the client; server-side pairing, tolerances and validation (S1–S14 in `docs/animation.md`) are not implemented here.
