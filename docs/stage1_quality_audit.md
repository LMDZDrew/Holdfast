# Stage 1 quality audit — 2026-10-18

Code: `a24755b` + follow-up **`a2c0968`**, branch `codex/stage1-review3-corrections`. Exact deployed jars, logs and commands are in the newest `testing.md` entry. No new mod was needed for the concrete faults found; dependencies/audio packs were preserved. Andrew authorized additional mods if useful, but none is selected by that authorization alone.

## Animation coverage

| Coverage | Evidence | Limit |
|---|---|---|
| Every **82 player exports** | Actual installed Player Animator serializer/playback, quarter-tick samples of body/head/limb/item position and rotation, finite-value assertions | Does not judge rendered appearance or TaCZ's separate gun renderer |
| Every **89 walker/crawler exports** | `core/tools/audit-character-exports.py`: 1,869 FK samples, finite transforms, loop position/rotation seams | Software maths, not GeckoLib in-game rendering |
| Every export | Format/duration/bone/event validation, semantics check, source regeneration | Cosmetic events remain non-authoritative |
| Holds, rests, swings, charge | Carry collision/floor/moving-leg checks; 26 weapon-motion checks | Vanilla rigid arms; first-person framing still needs review |
| Execution approaches | 28 pair checks, 36 generated surface entries, first-entry/initial-hold depth gates | Native pistol rows check skull alignment, not gun mesh collision |
| Protected firearms | 28 assets byte-identical to previous accepted gun clips, verified in both jars | Does not complete remaining TaCZ Gate 0 gameplay checks |

Inspected software sheets cover rest/ready/swing axe, deeper standing/ground executions, upright grabs/escape in both view approximations, and ground knife counterbalance. The latter replaced an unreachable free-hand brace; only `leftArm` source keys changed, with all blade/body tracks asserted identical. Not every animation has been manually seen in Minecraft; there is no blanket visual acceptance.

## Mechanics reviewed and exercised

Existing rule/unit and dedicated GameTests cover brain-only kills/body stagger; swing contact timing, interruption, obstruction and durability; grab windows, latency allowance, escape, blocked/breaking guards, infection; execution eligibility, alignment/ownership and timing; injury speed/state; condition NBT round trips; despawn timers; corpse grounding; packet round trips; and context-key routing. Final counts are recorded in `testing.md`.

Concrete faults fixed in this pass:

- Grab escape's generic stagger/release callbacks replaced the paired shove/kick. Ownership is removed before stagger; the selected paired animation is applied afterward. Upright/prone/crawler regressions added.
- Ordinary swings authored a lateral torso swivel after vanilla's extra swing had already been suppressed. Removed source yaw/roll and re-solved the arms; preserved forward weight shift.
- Execution kill validation checked the target but not the player/weapon. It now keeps the exact starting stack and slot, player object/dimension and aligned position; an interruption clears the paired clips without a kill/wear. Four GameTests cover weapon swap, identical-item replacement, player departure and target departure.
- Pistol execution ignored `consumeRound` failure. A round must now be spent successfully before the server kill/cosmetic shot; otherwise the execution aborts. Real TaCZ ammunition still needs the client run.
- A blunt charge could replace an active paired clip; releasing the charge used to begin an execution could send a stop over that execution. Start is refused during grabs/executions/swings, and release clears charge without overwriting an active pair.
- Transient grab prompt/result state now clears on client logout, preventing a stale HUD from taking ownership of a later shared-key input. Real reconnect remains unobserved.

## Remaining before claiming Stage 1 acceptance

- **Next code-audit priorities (not runtime-confirmed):** `MeleeSwings.tick` still sends a generic player clip stop when a pending swing is interrupted by a newly started grab/execution. The newer paired clip may therefore be cleared after taking ownership; reproduce/capture packet order and make the stop ownership-aware. Also inspect execution entry's offhand handling: free swings enforce `MeleeWeaponItem.twoHanded`, but the execution request currently does not mirror that check. Do not label this broader mechanic audit as complete acceptance.

- Run the single current batch in `stage1_bulk_test.md`. It includes blood origins, deeper blades, rest-only midpoint grip, torso, grabs/F routing, ammunition and despawn. Record Andrew's actual words against the installed hashes.
- TaCZ and Extra Gore are absent from headless tests: bullet/event/key arbitration, first-person framing, blood appearance and pistol ammo need the real client. A cancelled TaCZ client melee event may still touch TaCZ's internal lock preparation; inspect actual repeated-use behavior.
- Free melee remains static regional ray geometry. Arrows/non-TaCZ projectiles still use the older projectile position path; the impact correction here targets free melee, paired executions and TaCZ shots. No animated mesh-collision claim.
- Rest-to-swing arm rise, paired-entry snap, jumping/click feel, live reconnect and remaining Gate0 rows are not accepted. No second-human multiplayer/load proof.
- This remains the Stage 1 combat prototype. Full permadeath/no-replacement enforcement, ended-world archival, unique-equipment persistence and the larger survival systems are not implemented by this audit; roadmap milestones still govern them. Condition cloning currently starts fresh after death and must not be presented as final permadeath enforcement.
- No worldgen work. PR #1/#2 remain drafts and unmerged. Future fixes must preserve server authority and editable-source regeneration.
