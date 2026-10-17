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

**TaCZ (Gate 0 step 2)**: TaCZ 1.1.8-hotfix2 added to the same modpack (Andrew: no separate TaCZ-only server). Server and client load clean. **Failures, both with `move_limp` looping (Andrew confirmed):** holding a pistol or rifle "scrunches the shoulders" in third person (arms drawn in across the chest, shoulders hunched, gun low; first person normal), and you cannot aim down sights. Cause: our clip keys `rightArm`/`leftArm` (and `rightItem`) every tick, overriding or stacking with TaCZ's arm pose. TaCZ with no clip running is untested. Decisions recorded: TaCZ is the gun base and gun animations must match it; only TaCZ `pistol` guns can execute.

**Not done:** handgun execution clips (wait for the arm-ownership fix); melee re-test of the jar above; walker `react_knife` may need a sideways snap for the temple stab; ground-knife bracing hand still 7–9 px short (rigid arms); Player Animator body pivot off by ≤ 0.25 px.

## Next task

Retest the new first-person limp cue and ADS, then author handgun executions against TaCZ's pistol hold, re-test the pending melee clips, and complete TaCZ Gate 0 step 2. World generation stays on hold until stage one is complete.

## Codex arm-ownership handoff — 2026-10-16

**Branch/commits:** `codex/tacz-arm-ownership`, `055ac3f` and `b572f6b`, based on `codex/tacz-arm-handoff`. Draft PR #1 and PR #2 remain drafts and unmerged.

**Implemented:** The test harness plays source-generated firearm injury variants for TaCZ's `tacz:modern_kinetic_gun`. They key body translation and legs only, leaving arms, items, head and ADS to TaCZ. The harness layer is priority 95 (TaCZ installed jar uses 93–96). Firearm variants report Player Animator first-person mode `NONE`, which restored TaCZ first-person ADS. Added a source-generated asymmetric first-person injury camera cue, scaled by client movement; it is cosmetic and cannot decide injury or speed. Crutch and legless crawl clips have no gun variant. No core server gameplay implementation was added.

**Evidence:** `docs/testing.md` has the exact Windows 11/Java 17/Python 3.14/Forge four-mod environment, commands, logs, and jar hashes. Source exporter, static validator and PA semantic checks passed; Gradle builds passed. Andrew observed normal third-person gun pose and ADS on jar `686207C5…`, and working first-person ADS on client jar `EC9F9184…` while the server kept `686207C5…`. Client log confirmed `move_limp_gun` and first-person mode `NONE`. The current camera-cue client jar is `B555A79DAC5DB46DEDA09923744650A97EB8758AB995CC9CFF50A8C922FB05FA`; it is installed in Prism, built and statically checked, but **not yet observed in game**. The dedicated server process 33808 remains running from the earlier jar and accepted a localhost TCP connection at 22:51. A redundant launch failed on the world lock and exited; it overwrote the server's `latest.log` with that failed startup, so read the client log for the upcoming test.

**Open:** Andrew reported no noticeable first-person limp cue, even with empty hands, before the camera-cue change. Retest the new jar while walking and read its `first-person camera clip` log lines; verify ADS and third-person pose again. Pistol and rifle shot/reload observations with and without clips remain separate Gate 0 step 2 work. Handgun executions and the pending ground knife, machete, ground axe/machete/bat and `react_knife` retests are next. Do not start world generation or claim the camera cue passed before an in-game observation.

## Codex overnight stop — 2026-10-16

**Branch/commit:** `codex/stage1-handgun-executions`, after `c4159f8` (this stop records the later retest). No handgun source changes were made on this branch. Draft PR #1 and PR #2 remain drafts and unmerged.

**Observed:** With client jar SHA-256 `B555A79DAC5DB46DEDA09923744650A97EB8758AB995CC9CFF50A8C922FB05FA`, Andrew said the selected `move_limp_gun` version “works.” Prism `latest.log` at 22:55:41 passed the 51/14/38 clip load check, selected `move_limp_gun` at 22:56:27 with first-person mode `NONE`, and logged applied first-person camera angles during empty-hand and gun-held walking. Exact log values and environment are in `docs/testing.md`. This is a general user success report; individual pistol/rifle firing and reload, and the perceived camera strength, were not separately reported.

**Next:** Continue from the exact installed TaCZ 1.1.8-hotfix2 pistol `hold_upper`/`aim_upper` poses inspected in the default gun pack. Author standing and appropriate ground handgun pairs in editable source, then regenerate exports and build the harness. The future core server S7 gate must accept only TaCZ index `type: pistol`; the harness must not claim server gameplay authority. Then re-test ground knife, standing machete, ground axe/machete/bat, and temple `react_knife`; finish Gate 0 step 2 with separate pistol/rifle shot, reload and ADS observations with and without Holdfast clips. Stay in Stage 1; do not begin world generation.

**Server status:** At Andrew's request, the Gate 0 dedicated server was stopped cleanly at 22:59:10 on 2026-10-16. Its log confirms player/world saves and all chunks saved; process 33808 exited. Restart it for the next in-game test.

**Added for the next Stage 1 work session (Andrew, 2026-10-16):** After the in-flight handgun execution, melee retest, and TaCZ Gate 0 checks, start sound and cosmetic blood spatter feedback for confirmed hits and shots. Then author general crouch, sprint, walk, and jump animations, followed by distinct melee sets for knives, two-handed bats and axes, swords/katanas, machetes, and one-handed hatchets. Check every movement set with empty hands, melee items, and TaCZ guns so the new layers do not revive the arm/ADS conflict. Update editable animation source, regenerate exports, build, and record actual in-game results. The new scope is recorded in `docs/decisions.md`; it remains Stage 1 and does not authorize world generation. The server remains stopped until the next test.

**Audio direction added by Andrew:** Give walkers the raspy, breathy vocal character heard in the show. Pair blood spatter with distinct contact sounds for beating, slashing, stabbing, and gunshot flesh impacts; separate impact audio from the swing or shot itself. Source original or authorized audio that achieves this character. Trigger blood and impact sound from server-confirmed contact, with cosmetic timing on the client. Exact show recordings require rights clearance. See `docs/decisions.md`.

**Accepted Stage 1 exit plan:** See `docs/roadmap.md` for the ordered pre-worldgen gate: (1) finish TaCZ/handgun/melee evidence, (2) build server-authoritative combat and persistence, (3) add general locomotion and melee-family clips, (4) add walker, weapon, blood and flesh sound plus cosmetic spatter, (5) pass solo dedicated-server, automated state/reconnect, and practical second-local-client checks. Andrew has no second human tester available; do not request a friend-based two-player test or hold world generation for one. Mark human multiplayer synchronization and 16-player load as explicit later risks until testers exist. Keep PR #1 and PR #2 as drafts and do not merge them.

## Claude Stage 1 build-out (no in-game testing) — 2026-10-17

**Branch** `claude/stage1-combat-and-animation` from `0a3e7d1` (`codex/stage1-handgun-executions`). Draft PR #1 and PR #2 untouched and unmerged. Andrew asked for changes without testing; he will test in bulk with **`docs/stage1_bulk_test.md`** (exact commands, expected results, numbered for reporting).

**Built (static checks and unit tests only; see the 2026-10-17 rows in `testing.md`):**
- Handgun executions `exec_pistol`, `exec_pistol_ground` (prone and crawler) in `player_guns.py`, authored in Player Animator part space from TaCZ 1.1.8's `pistol_default` `hold_upper` (match at both ends) with the barrel solved onto the skull; shot reactions; 3 pair presets; the harness equips a real TaCZ `glock_17`.
- `react_knife` now takes the temple stab sideways. Hatchet and katana in the solver rig.
- Weapon families (`player_weapons.py`): holds (arms/items/head only) and light/heavy swings for knife, machete, hatchet, katana, axe/hammer; locomotion `move_walk/sprint/crouch_walk/jump` with legs-only `_gun` variants.
- New mod `holdfast_core` (`core/`, design per system in `docs/core_combat.md`): plain-Java rules (19 JUnit tests), generated `CombatTimings` from the clips (`gen_core_tables.py`), walker entity (GeckoLib, no damage pool, upright/prone/crawler, lunge → server grab), melee/projectile/TaCZ hit resolution (TaCZ bridge compile-only against the real jar), grabs with escape window and latency allowance, bites through bite-rated guards, infection with amputation window, executions with commit/cancel/kill ticks and the pistol-only rule, injury speed, saved condition, one context key (V), HUD, client clip selection (locomotion 95 / weapon hold 97 / action 1100), blood and contact sounds, `/holdfast` debug commands, 7 melee items and 6 bite guards.
- Original procedural audio (`art/audio/gen_sounds.py`): walker groan/breath/snarl/lunge/bite/death and blunt/slash/stab/gunshot flesh and skull contacts, 72 variants.

**Automated server evidence:** 11 GameTests pass on a headless Forge server (`core\gradlew.bat runGameTestServer`): walker damage rules, brain kills, charged blunt, vanilla damage ignored, crawler skull layout, walker save/load, and with a Forge FakePlayer: grab escape, blocked/breaking/penetrating bites with infection, knife execution kill tick, empty-hand refusal, injury speed. The core mod loads and registers there.

**Not done / risks:** no client or player run yet; grabs, bites, executions, HUD, clips, sounds and blood are compiled only. Blunt charge: hold use, the release is the swing. First-person views of swings untested. No scripted reconnect check with a real player yet. The walker voice character is unjudged. Prone/crawler hit layout approximate. Two-handed weapons need an empty off hand. Injury walks are now broadcast to other players (`MovementPacket`); flesh/blood feedback also plays for TaCZ and melee hits on non-walkers.

**Next safe task:** install both jars and run `docs/stage1_bulk_test.md` with Andrew; fix what fails in source; then a scripted real-player reconnect check, and TaCZ Gate 0 step 2's separate pistol/rifle observations.

## Andrew's first in-game review of the build-out — 2026-10-17 (Claude)

Branch `claude/stage1-combat-and-animation`. Andrew ran bulk test sections 1-3; results, his exact words, causes, and an ordered task list for Codex are in **`docs/codex_stage1_animation_feedback.md`**. In short: pistol executions pass (need TaCZ's per-gun shot sound); axe/bat/machete executions pass; standing knife lands short again; every hold is stiff and unnatural (axe clips through the body); swings and locomotion work but are stiff and minimal; `_gun` locomotion passes. No new sounds heard because the new audio lives in `holdfast_core`, whose section 4 wasn't run. Nothing was fixed after the review, at Andrew's request. Andrew also decided that Holdfast animations replace vanilla's (left click plays real swings; walk/sprint/crouch/jump play Holdfast locomotion; see `decisions.md` and the feedback handoff).

## Handoff format for future work

Branch/commit; files changed; behavior observed; exact tests and commands; unresolved issues; next safe task. Separate proposed behavior from verified behavior.
