# Claude Code prompt: Stage 1 runtime corrections (2026-10-17)

You are continuing Holdfast Stage 1 in `C:\Users\drewl\Holdfast`. Finish the following runtime corrections before asking Andrew for another test batch. Work accurately: distinguish user observations, measured evidence, hypotheses, and untested implementation. Blood/particle quality and walker audio are explicitly deferred by Andrew.

## Start and boundaries

1. Read `AGENTS.md`, `CLAUDE.md`, `docs/decisions.md`, relevant `docs/design.md` sections, and the latest entries in `docs/handoff.md` and `docs/testing.md`.
2. Read this file, `docs/codex_stage1_animation_feedback.md`, `docs/core_combat.md`, `docs/stage1_bulk_test.md`, `docs/animation.md`, and `testmod/animtest/README.md`. Older feedback is historical; the latest observations below override any claim that these runtime paths are unobserved.
3. Inspect Git status and history. Start your own scoped `claude/` implementation branch from `codex/stage1-animation-quality` at `42a963d` (implementation `89755ac`, followed by handoff/whitespace commit). That branch is pushed and clean at this handoff. Do not edit another assistant's branch or overwrite local changes.
4. Stay in Stage 1. Do not begin world generation. Keep PR #1 and PR #2 drafts and unmerged. Do not change dependency versions without official metadata and a compatibility run.
5. Client animations/events/effects remain cosmetic. The server owns contact validation, damage, brain destruction, bites, execution results, death and persistence. Do not reintroduce competing vanilla or TaCZ state owners.

## Latest actual feedback from Andrew

Andrew is testing the installed revision and reports:

- Ordinary walker deaths fall over but **float above the ground**, unlike walker executions invoked through `/hfa` commands, which lay on the ground correctly. The exact ordinary kill method/posture and clearance were not specified. Do not invent them or claim every death path was tested.
- Blood effects/particles and walker noises are unacceptable. **Leave their quality work for later.** This is now negative runtime feedback, not an untested or silent feature. It does not request deleting/disabling them.
- The **hatchet carry holds the weapon incorrectly**. **The hatchet swing is fine.** Preserve the accepted swing while correcting the carry and its transitions.
- Walker hits register **as soon as the player clicks**. Andrew wants the hit to register **when the weapon makes contact**.
- First-person animations look better than third-person animations, but **weapons “start spazzing out during the animations.”** Exact weapons, movements and perspective of each instance were not specified. Do not assume this is confined to one view or TaCZ.

This is partial feedback, not a pass for the remaining checklist, knife contact, TaCZ Gate 0, reconnect/persistence, or all core combat.

## What Codex changed before this review

- Added a continuous controller driven by actual movement, posture, injury, held weapon family and item use; smoothed gait speed/phase, hold/state fades, responsive head look, carry arm follow, and unarmed/bat animation selection. Core layers yield to harness inspection. Injury state rehydrates after reconnect.
- Source-generated relaxed carries, revised gaits/swings and idle/fall/landing clips: 76 player clips, 53 walker and 15 crawler animations, 31 pair presets.
- Corrected Player Animator's actual body-pivot conversion: its 0.7-block pivot is applied before Minecraft's 15/16 player scale, so the source compensation uses 11.9467 model pixels. Preserved authored leg translations. Do not revert this to hide a new symptom.
- Added paired packet start and `/hfa freeze <seconds>` inspection for player + walker, including seek after a clip ends.
- Pistol executions snapshot the actual TaCZ pistol, invoke its native sound at the server kill tick, and call the local muzzle-flash hook. These are cosmetic, not projectile simulation.
- Preserved gun movement variants with only legs and body translation, first-person mode NONE, and TaCZ ownership of firearm arms/items/head/ADS. Crutch/crawl limitations remain.

## Required work, in order

### 1. Ground ordinary corpses correctly

Compare `core/.../entity/WalkerEntity.java` and `client/WalkerModel.java` against the harness entity/controller/renderer and the source death/reaction clips. Trace the complete transform chain: entity origin, posture, model/root translation, pivots, vanilla death rotation, GeckoLib controller state, terminal pose and persistent corpse rendering. The harness comparison is a diagnostic lead, not proof of the cause.

Correct ordinary corpse placement at its source of error. Avoid an arbitrary global renderer offset that breaks standing walkers, crawlers or existing execution pairs. Verify normal brain-kill deaths and core execution deaths across supported upright/prone/crawler states, including persistent corpse save/load. Preserve terrain collision and server-owned death. If animation keys need changing, edit `art/characters/source/` and regenerate.

### 2. Resolve ordinary melee at authoritative contact time

Inspect the attack hooks, `combat/Weapons.java`, `item/MeleeWeaponItem.java`, `rules/CombatTimings.java`, and `art/characters/source/gen_core_tables.py`. Current ordinary melee resolves at the attack event; the delayed execution kill tables are not a substitute for free-swing contact timing.

Introduce an authoritative pending swing with source-derived contact timing for the selected family/attack. A click begins the accepted attack and matching visual; it must not immediately apply walker damage, stagger, knockback, blood, impact audio or durability consequences belonging to contact. At the server contact tick, validate the current target geometry, reach, obstruction, player/weapon state and relevant cooldown/charge rules. A target moving out of reach must be able to cause a miss; do not blindly damage the entity selected at click time. Prevent duplicate vanilla/Forge/TaCZ damage, client-forged hits, repeated hits from one swing and stale queued attacks after interruption, weapon switching, death or disconnect. Keep charged blunt and existing execution semantics correct. Define latency and cancellation behavior in `docs/core_combat.md` before implementing it.

Use generated contact metadata shared by server timing and client playback; animation events cannot decide outcomes. Explain whether contact uses a validated ray, swept weapon volume or another server geometry approximation. Do not claim exact mesh collision if the implementation only samples an approximation. Add meaningful server tests for no early damage, one contact resolution, misses/obstructions and cancellation, plus supported charged attacks. Preserve body hits never killing and brain-only walker kills.

### 3. Fix weapon instability and hatchet carry

Inspect `core/.../client/ClientClips.java`, Player Animator layer priorities/modifiers, weapon transitions and `art/characters/source/player_weapons.py`, `player_anims.py`, `pa_item.py`. Check overlapping action/hold/locomotion transforms, repeated animation restarts, fade composition, angle wrap/interpolation, hand/item coordinate conversion and first-person versus third-person handling. Treat these as hypotheses until demonstrated. Prefer narrow logging or numerical tracing to guessing new poses.

Fix the instability in the transform/state owner responsible. Correct the hatchet grip/orientation in the carry source, retaining the accepted swing contact and motion. Verify idle/carry → swing → recovery → carry, movement/crouch transitions, rapid repeated input, item switches and both views. Preserve two-hand grip contact and TaCZ pistol/rifle hold, ADS, fire and reload ownership. Numerical carry audits passed before this report; they are insufficient evidence of correct runtime grip or stable interpolation.

### 4. Finish checks and present one review batch

Run source regeneration/export/geometry checks for changed assets and both Gradle builds. Run meaningful core tests for changed combat/death behavior. Inspect the resulting jars, logs and actual installed mod versions. Do not request user testing after every edit. Finish all feasible implementation and checks first; then provide one short consolidated in-game checklist for the remaining visual observations. Andrew has no second human tester; use automated dedicated-server checks and optional local clients where practical.

Do not replace jars while the Prism client is running or restart an active server without accounting for Andrew's current session. Preserve logs and jars, stop the dedicated server gracefully when required, verify saves, deploy matching jars, and record SHA-256 hashes. Ask only for indispensable session coordination, not repetitive approval or premature visual tests.

## Existing environment and evidence

- Windows 11, Minecraft 1.20.1, Forge 47.4.8, GeckoLib 4.8.4, Player Animator 1.0.2-rc1+1.20, TaCZ 1.1.8-hotfix2.
- Java: `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`; Gradle 8.8.
- Python 3.14.7: `C:\Users\drewl\AppData\Roaming\uv\python\cpython-3.14.7-windows-x86_64-none\python.exe`; dependencies in `art/characters/.deps`, use `PYTHONPATH` and `PYTHONUTF8=1`. Read the recorded Git Bash shim in the prior evidence rather than assuming `python3` or a venv works.
- Core command, from `core`: `.\gradlew.bat build runGameTestServer --no-daemon`; harness, from `testmod/animtest`: `.\gradlew.bat build --no-daemon`, with JAVA_HOME set to Java 17. Previous result: 20 JUnit and 11 GameTests pass, both builds successful. Headless GameTests do not load TaCZ or prove rendered behavior.
- Source pipeline: `art/characters/source/make_all.sh`; source refresh option `player_weapons.py --refresh-movement-carries` is appropriate only when its scope covers the changes. Run the relevant validators and generators; never hand-edit generated animation JSON or timing tables.
- Installed core SHA-256: `A718C40AFA44599CBF9844169D1F520B3312E7FB972F63C052A5929A8336CA57`; harness: `63E968EBF46AD6FDDB3F5B1F1019E31A621B96870BE8C5D52AF50FC9E088B2D1`.
- Both installed in `C:\HoldfastGate0\holdfast-gate0\server\mods` and `C:\Users\drewl\AppData\Roaming\PrismLauncher\instances\Holdfast Gate0\minecraft\mods`. User now reports testing; reconfirm live files/processes before deployment.
- Server last verified running as PID 3800 at 15:26 on 2026-10-17; this is historical, not a current-process guarantee. Server logs: `C:\HoldfastGate0\holdfast-gate0\server\logs\latest.log`, `codex-quality-final-stdout.log`, `codex-quality-final-stderr.log`. Client log is in the Prism instance's `minecraft/logs` directory.
- Build logs: `core/build-quality-final.log`, `testmod/animtest/build-quality-final.log`; preserved evidence in ignored `core/build/test-evidence/2026-10-17-animation-quality/`. Source logs in `art/characters/build/`.

## Deferred and still open

Do not work on blood/particles or walker audio quality this session. Retain the future direction of original/authorized raspy walker vocals and distinct blunt/slash/stab/gunshot flesh and skull sounds; no unlicensed show recordings. Standing knife runtime contact, native pistol sound/flash observations, real-player reconnect and remaining Gate 0 checks are not silently passed. Existing core pistol executions also lack explicit ammo validation/consumption; flag this follow-up without confusing cosmetic shot playback with full firearm integration.

Finish with a clean, reviewable branch and commit/push the work. Record exact environment, commands, results, hashes, logs, Andrew's actual observations and remaining risks in `docs/testing.md`. Update `docs/core_combat.md` for timing/state changes, the consolidated test sheet for the next batch, and `docs/handoff.md` with branch/commits, implemented versus verified behavior and next safe task. Do not claim any of the reported visual defects fixed solely because a build or static check passes.
