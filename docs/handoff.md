# Assistant handoff

## Current state — 2026-10-16

- This repository foundation was prepared locally for `LMDZDrew/Holdfast`. Confirm remote publication and commit SHA before relying on the repository as the shared source of truth.
- Design baseline is transcribed in `design.md`. `decisions.md` records superseding instructions including rejection of Lost Cities.
- `architecture.md`, `mods.md`, `roadmap.md`, and `testing.md` separate proposals from verified implementation.
- No Minecraft version, loader build, dependency graph, runtime, or performance result has been validated. No custom mod code exists.

## Character animation milestone — 2026-10-16 (Claude)

**Branch:** `claude/character-animation`; milestone commit `d00417a`. Draft PR against `main`; do not merge until it has been tested in game.

**Created**
- Upright walker and lower-half crawler models (Bedrock geometry for GeckoLib), 4 placeholder walker outfits, placeholder 64×64 vanilla-layout player skin.
- 48 walker clips and 11 crawler clips (GeckoLib JSON):
  - layered idle, 8 gaits and pursuit;
  - lunge attack;
  - grab seize, hold, bite, blocked bite and shoved;
  - prone, sitting and motionless starts with rises and an ankle grab, plus ankle hold, bite, blocked bite and kicked;
  - crawler lunge and ankle set;
  - headshot death;
  - reactions for knife, bat quick and charged, axe, machete and ground knife.
- 30 player clips (Player Animator JSON) with per-clip extras (first-person camera, sounds, events): grabs and ankle grabs, escapes, bites, bite confirmation by region, executions (knife, axe, machete, bat quick and charged, ground knife), charge hold, free and missed charged swings, and injury movement (limp, injured arm, legless crawl, improvised and medical crutches, prosthesis).
- Specs: `docs/animation.md` (formats, rigs, bone names, texture sizes, frame rates, dependencies, server validation table S1–S14) and the generated `docs/animation_catalog.md`.
- Previews: `art/characters/preview/` (HTML page with a playable grab prompt, frame sheets). Labelled PREVIEW.

**Tests run** (details in `testing.md`): `make_all.sh` and `validate_exports.py` (pass), `check_pa_semantics.py` (worst 0.577 px), `measure.py` (blade entry per execution, no early clipping), `node test_preview.js` (22/22), frame sheets inspected. **No Minecraft client or server was run.** Nothing is tested in game.

**Unresolved**
- In-game load under GeckoLib 4.8.4 and Player Animator 1.0.2-rc1 on Forge 1.20.1; platform gate 0 still open.
- Held-item orientation through Player Animator's `rightItem`, and item display transforms.
- First-person configuration and camera application.
- Legless crawl inside the vanilla crawling pose; hiding amputated limbs.
- Rigid arms limit two-handed grips and the left-hand bracing reach.
- GeckoLib's Catmull-Rom easing versus the preview.
- TaCZ arm-pose conflicts.
- Hitboxes for prone walkers, crawlers and corpses.
- Textures are placeholders.

**For Codex to integrate (after gate 0)**
1. Load `art/characters/export/assets/holdfast/**` as mod resources; register the walker and crawler GeckoLib entities with three controllers (body, arms, head) and pick per-walker sets from a UUID seed.
2. Implement the server tables in `docs/animation.md` "Where the server must decide" (S1–S14). Grab window, bite resolution, execution commit and kill ticks, and charged-swing hit tests are server state; animation `timeline` events are presentation only.
3. Play Player Animator clips `holdfast:<clip>` from server-synced state. Apply `*.extras.json` camera tracks in first person.
4. Run the in-game test plan at the end of `docs/animation.md` and record results in `testing.md`.

## Animation test harness — 2026-10-16 (Claude)

- Gate 0 library loading passed on Andrew's PC (rows on PR #2). TaCZ, part of roadmap Gate 0 step 2, is still untested.
- Added `testmod/animtest/`: test-only Forge 1.20.1 mod (MDK layout, ForgeGradle 6, Gradle 8.8 wrapper from MinecraftForge `1.20.1`) that takes `art/characters/export` as its resources, runs a load check on world join, and plays walker, crawler, player and paired clips by `/hfa` command, logging timing. `art/characters/source/gen_testmod_clips.py` generates its clip table; `make_all.sh` runs it.
- **Not compiled yet** (no Forge or Mojang access here; Java parse check only). Next: Andrew builds it, loads it with the Gate 0 setup, and we record per-clip results.

## Stage 1 in-game run — 2026-10-16 (Claude, with Andrew on his PC)

**Branch** `claude/character-animation` (draft PR #1; do not merge). Every run, command, hash and observation is in `testing.md` (rows dated 2026-10-16 from "first compile" on).

**Verified in game** (dedicated server + Prism client, Forge 47.4.8, GeckoLib 4.8.4, Player Animator 1.0.2-rc1+1.20):
- Harness builds (one `build.gradle` repo fix) and loads; load check PASS for every clip. All walker, player and pair clips played via `/hfa sweep`; Andrew passed the poses. First-person camera fine; legless crawl in the swimming pose fine.
- Held items: the exporter now converts `rightItem`/`leftItem` into Player Animator's hand frame (`pa_item.py`, read from `ItemInHandLayer` bytecode and `HeldItemMixin`). With the harness's test weapons (`gen_test_items.py`), the machete, charged bat and crutches passed; the axe passed after a vertical-blade term.
- Standing knife re-posed as a temple stab; player drawn at 15/16 (`PLAYER_SCALE`) now accounted for in all paired clips. No complaint after the fix (not confirmed in words).
- Injury speeds decided by Andrew (`decisions.md`): limp 85%, severe limp 55% (new clip), arm 95%, plus leg-loss speeds. Cycles re-authored to match; the limps passed in game with the harness's test-only speed modifier.

**Built but not yet seen in game** (harness jar `1169668B…`, deployed): arm-through-torso solver check; ground-knife fix (arm crossed the chest); machete strike into the side of the skull; `exec_axe_ground`, `exec_machete_ground`, `exec_bat_ground` from one overhead template (`player_more.OVERHEAD`) with matching walker/crawler reactions and 6 new pair presets.

**TaCZ (Gate 0 step 2)**: TaCZ 1.1.8-hotfix2 added to the same modpack (Andrew: no separate TaCZ-only server). Server and client load clean. **Failures:** holding a pistol or rifle "scrunches the shoulders"; with a Player Animator clip running (`move_limp`) you cannot aim down sights. Decisions recorded: TaCZ is the gun base and gun animations must match it; only TaCZ `pistol` guns can execute.

**Not done:** handgun execution clips (wait for the arm-ownership fix); melee re-test of the jar above; walker `react_knife` may need a sideways snap for the temple stab; ground-knife bracing hand still 7–9 px short (rigid arms); Player Animator body pivot off by ≤ 0.25 px.

## Next task

Resolve TaCZ vs Player Animator arm ownership (shoulder scrunch while holding guns; ADS blocked during injury clips), then author handgun executions against TaCZ's pistol hold, then re-test the melee clips. World generation stays on hold until stage one is complete.

## Handoff format for future work

Branch/commit; files changed; behavior observed; exact tests and commands; unresolved issues; next safe task. Separate proposed behavior from verified behavior.
