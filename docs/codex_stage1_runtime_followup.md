# Codex prompt: after Claude's Stage 1 runtime corrections (2026-10-17)

Continue Holdfast Stage 1 in `C:\Users\drewl\Holdfast` from **`claude/stage1-runtime-corrections`** (pushed). Read `AGENTS.md`, `docs/decisions.md`, `docs/claude_stage1_runtime_handoff.md` (the brief this branch answered), `docs/core_combat.md` §1a, the 2026-10-17 "Claude runtime corrections" rows in `docs/testing.md`, and the top section of `docs/stage1_bulk_test.md`. Work on your own `codex/` branch from this head. Keep PR #1 and #2 as unmerged drafts. Do not start world generation. Blood, particles and walker audio stay deferred.

## What changed (implemented, with automated evidence only; nothing seen in game yet)

1. **Floating corpses (`5295d0b`).** The cause is confirmed in bytecode and reproduced by GameTests on the unfixed code, where upright corpses sat 0.725 blocks up. On death the box grows from 0.6 to 1.6 wide. Vanilla `refreshDimensions` lifts a growing mob to its old mid-height, and `setNoAi(true)` disabled `travel()`, so there was no gravity. `WalkerEntity` changes:
   - It keeps the feet in place when the lying box is free.
   - Corpses use `isImmobile()` instead of NoAI.
   - Old saved corpses have NoAI cleared, so they settle.
   - A client that first sees an already-dead walker holds the death clip's last frame instead of replaying the fall.

   The GameTests now place actors on the arena floor at structure y 2. Earlier tests had them embedded in the stone.
2. **Melee at contact (`717ee8b`, `2b5e008`).** These no longer apply a hit immediately; each only starts a server `SwingRules.Session` (`MeleeSwings`):
   - a click on a walker (the vanilla attack is cancelled);
   - a click into the air (`SwingPacket`, which carries no target or outcome);
   - a charged bat release.

   The contact tick is the clip's `strike` timeline event. `gen_core_tables.py` generates it into `CombatTimings.SWINGS` from the same extras files clients play. At contact the server:
   - casts the current view ray at entity reach, with a 0.2-block arc margin on walker boxes (a ray approximation, not swept or mesh collision);
   - refuses hits through blocks;
   - applies one outcome, and only then spends durability and sends feedback.

   A pending swing is cancelled by:
   - a slot or item change;
   - a grab or an execution;
   - starting to use an item;
   - death or respawn;
   - logout.

   Clicks during a swing are refused, not queued. The latency allowance follows the grab rule (half the round trip, at most 3 ticks) for both the contact tick and the end of recovery. Clients share `SwingRules.clipFor`. The local clip starts from `InputEvent.InteractionKeyMappingTriggered`, before vanilla spends the attack strength; other players get the server's `PlayClipPacket`. The `CoreNet` protocol is now version 3.
3. **Weapon instability and hatchet carry (`d76a7fe`).** The cause was measured, not guessed. Per-channel Catmull baking of arm and wrist made the visible weapon wander (up to 1.84× the direct rotation) and roll over in the hand, fastest during wind-ups and recoveries. `bat_charge_hold` also had a 35° loop seam. Fixes:
   - `player_anims.geodesic_item` re-bakes held items (carries, swings, bat charge and miss) along the shortest weapon rotation between authored keys, which are unchanged.
   - The hatchet carry is a low ready carry: head forward and up, bit down (`player_weapons.CARRY_ORIENT`). The swing keys are untouched.
   - The charge-hold seam is closed.
   - Switching from one weapon to another cuts the hold instead of fading it.

   The new `check_weapon_motion.py` passes all 22 clips; the old export failed 9. `PoseTrace` (a client-side diagnostic) logs `[holdfast] pose jump` lines naming the active clips whenever an arm or held item jumps more than 45° in one frame at over 150°/tick.

Evidence: 26 JUnit tests and 22 GameTests pass. A mutation that resolves hits on the click fails 6 of the melee tests. The worst Player Animator semantics difference is 0.577 px. Both builds pass. The jars are built but **not deployed**: core `46AC40EB…`, harness `73E66410…` (full hashes in `testing.md`).

## Your next steps, in order

1. **Deploy for review.**
   - Check that no Prism client is running and no player is online.
   - Stop the server gracefully. RCON is off in `server.properties`. The tested method is `AttachConsole(pid)` plus `GenerateConsoleCtrlEvent(CTRL_C_EVENT, 0)` from a separate process. Confirm "All dimensions are saved" in the log.
   - Copy both new jars into the server mods folder and the Prism "Holdfast Gate0" mods folder. The protocol bump means both sides must match. Verify the SHA-256 hashes.
   - Start the server with `.\run.bat nogui` and record the deployment in `testing.md`.
2. **Andrew's review.** He uses the top section of `docs/stage1_bulk_test.md`: corpses, contact timing, charged bat, hatchet carry, weapon steadiness with the `pose jump` log lines, and a TaCZ regression check. Collect his actual words, and don't mark anything passed without them.
3. **If weapons still look unsteady,** read the `pose jump` lines in the Prism `latest.log` first. They name the clip, layer, tick and view. The known numeric hotspots are the light knife wind-up (108°/tick) and the machete wind-up (71°/tick), because their blade-down carries are far from their wind-up poses. If Andrew flags them, re-author those carries or wind-ups in `player_weapons.py` as was done for the hatchet, then rerun `make_all.sh` and `check_weapon_motion.py`. Don't hand-edit the exports.
4. **Known gaps; keep these stated as open:**
   - Non-walker targets (animals, players) still take vanilla click-time damage.
   - When attack strength is near 0.95, the client's light/heavy guess can differ from the server's, which shifts the visual contact by up to 2 ticks.
   - A full charge that saw a target at release but finds none at contact plays the heavy swing, not the overswing.
   - The latency allowance is unit-tested only, because fake players have zero latency.
   - The corpse box is a square approximation.
   - Pistol executions still don't validate or consume ammo.
   - Still open: standing knife execution contact, a real-player reconnect test, and the separate TaCZ pistol and rifle Gate 0 rows.

Record exact commands, results, hashes and Andrew's observations in `docs/testing.md`, and add a concise `docs/handoff.md` entry.
