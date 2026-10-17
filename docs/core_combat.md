# Holdfast core: Stage 1 server-owned combat slice

Status: **implemented in source; 20 JUnit tests and 11 GameTests (walkers, and grabs/bites/executions/injury speed with a Forge FakePlayer) pass (`core\gradlew.bat build runGameTestServer`). Core gameplay and the current animation revision still need a real-player review; see `stage1_bulk_test.md`.** Mod id `holdfast_core`, Gradle project `core/` (Forge 1.20.1-47.4.8, same pins as Gate 0). This document is the CLAUDE.md "before implementing" statement for each shared system, and the map of where each rule lives. `animation.md` S1–S14 lists the moments; this file says how the core decides them.

## Principles (from `decisions.md`)

- The server decides every outcome: hit region, brain kill, stagger, grab, escape, bite, protection, infection, execution validity, commit, kill, injury speed. Clients render what the server tells them.
- Animation `timeline` events, sounds, blood, particles and camera motion are presentation. The server never waits for or reads them.
- One timing table. `core/src/main/java/dev/holdfast/core/rules/CombatTimings.java` is generated from the same source (`walker_anims.EXECS`, grab windows) by `art/characters/source/gen_core_tables.py`, so clip lengths and server ticks cannot drift.
- All rules are plain Java in `dev.holdfast.core.rules` with no Minecraft types, so they are covered by fast JUnit tests (`core/src/test`). Forge glue in other packages only translates game state into rule inputs and rule outputs into game actions.

## Systems

### 1. Hit regions and brain kills (S10, S11)

| | |
|---|---|
| Server-owned data | Per hit: the attacked walker's kind (upright, prone, crawler) and pose; the hit point in the walker's local frame; the weapon class; for charged blunt swings, the server-measured charge fraction. |
| Rule | `HitRegions.classify` maps a local hit point to `HEAD`, `NECK`, `TORSO`, `ARM`, `LEG` from the exported geometry (head cube 26–32 px up, etc.; prone and crawler use their lying layouts). `WalkerStrikeRules.resolve` returns `BRAIN_KILL`, `STAGGER` or `GLANCE`. Body hits never kill (no accumulated walker damage). Head: blades, knives, axes, machetes, hatchets, katanas and gunshots kill; blunt kills only at full charge (charge ≥ `FULL_CHARGE`), otherwise stagger. |
| Client display | `HitFeedbackPacket` (region, contact family, position) drives the blood spatter and the flesh/contact sound (`impact_*` families below), separately from the swing or gunfire sound. |
| Saved state | None. A dead walker is a corpse entity; walkers keep no damage. |
| Mod interactions | Vanilla melee: `LivingAttackEvent` on a `WalkerEntity` is cancelled and re-resolved by the core. TaCZ: the core listens to TaCZ's `EntityHurtByGunEvent.Pre` (compile-only dependency on the exact 1.1.8-hotfix2 jar) and uses TaCZ's own headshot flag plus the bullet position; TaCZ damage to a walker is replaced by the core's outcome. Other mods' damage to walkers is ignored unless it carries a hit position (no stray brain kills from fall or fire damage). |
| Test | JUnit: region boundaries for each walker kind; every weapon class × region; partial vs full charge. In game (later): head and body hits per weapon; TaCZ head and body shots. |

### 2. Bite protection and infection (S4, S5)

| | |
|---|---|
| Server-owned data | The bitten region (upright grab: neck; ankle grabs: leg; a forearm block if the player is guarding); every worn piece's bite rating and remaining condition; difficulty; infection start tick and duration. |
| Rule | `BiteRules.resolve(region, pieces, difficulty)`: an intact bite-rated piece covering the region blocks the bite, **including the bite that breaks it** (condition drops by one bite's wear; at zero it stays worn but no longer protects). Otherwise the bite penetrates: one penetrating bite infects. Ordinary clothing has rating 0. `InfectionRules` (design.md §11): bite decline lasts 60 real minutes on the default preset, 120 on lower-pressure and 30 on severe; eligible amputation must begin within 5 / 10 / 3 real minutes; head, neck and torso bites are never amputation-eligible. Times run on server ticks while the player is online. |
| Client display | `ConditionSyncPacket` to the owner: infected flag, progress, wounds; the HUD shows a bite marker and the region. Bite confirmation clip `bite_confirm_<region>`. |
| Saved state | `PlayerCondition` (Forge capability, player NBT): infection start game time and duration, wounds by region, limb state, injury severity. Survives relog and server restart; cloned only for dimension change (death is final, so not copied on respawn). Item condition is the item's own damage value. |
| Mod interactions | Armor pieces are Holdfast items (`BiteGuardItem`) with a `bite_rating`; vanilla armor counts as rating 0 unless tagged `holdfast:bite_rated`. |
| Test | JUnit: blocked, breaking-but-blocked, penetrating, uncovered region, zero-condition piece, difficulty durations. Persistence: NBT round trip test. |

### 3. Grabs, escape and bites (S1–S4, S6)

| | |
|---|---|
| Server-owned data | `GrabSession` per victim: attacker, kind (upright / prone ankle / crawler ankle), start tick, window length by difficulty preset (default 1.2 s, lower-pressure 1.6, severe 0.9; design.md §11), the accessibility extension, accepted or refused escape, bite count. |
| Rule | `GrabRules` state machine: `SEIZE` → `WINDOW` → (escape input arrives with server tick ≤ window end + latency allowance → `ESCAPED`, knockback 0.75 blocks, stamina) or (window expires → `BITE`, resolved by `BiteRules`; blocked bite → a new `WINDOW`; penetrating → `RELEASED_BITTEN`). Latency allowance = half the player's measured RTT, capped at 150 ms (3 ticks). A teammate's validated brain kill or shove → `RELEASED`. One active grab per victim and per attacker. |
| Client display | `PlayClipPacket` starts the victim's and attacker's clip pairs at the server's tick; the prompt and countdown are display only (`GrabPromptPacket` carries the window end tick). The client sends `EscapeInputPacket` once. |
| Saved state | None. A grab in progress at logout/shutdown resolves immediately as an escape (no unavoidable bite on reconnect); logged in `testing.md` as a design choice to confirm. |
| Test | JUnit: on-time escape, late escape, latency allowance at 0/100/200 ms RTT, blocked-then-penetrating bite, teammate release, double-grab refusal. |

### 4. Executions (S7–S9)

| | |
|---|---|
| Server-owned data | `ExecutionSession`: executor, target, clip, request tick, commit tick, kill tick, cancelled flag. |
| Rule | `ExecutionRules.validate`: target is a walker that is not already executed and not grabbing someone else; distance within the clip's pairing distance ± 4 px; facing within ± 25°; head exposed; weapon class allowed (knife, axe, machete, bat (charged), hatchet, katana, or a TaCZ gun whose index type is `pistol` — rifles, shotguns, SMGs, snipers, MGs and launchers are refused); target posture matches the clip (standing / prone / crawler). Commit and kill ticks come from `CombatTimings` (S9 table). Cancel accepted only before commit. At the kill tick the target must still be in place, else no kill. No invulnerability for the executor. |
| Client display | `PlayClipPacket` for both participants; the harness clips `exec_*` and `react_*`. |
| Saved state | None (an execution never spans a save; on shutdown it is dropped without a kill). |
| Mod interactions | TaCZ gun type is read from the held gun's `GunId` through TaCZ's index (`TimelessAPI.getCommonGunIndex(...).getType()`), compile-only. |
| Test | JUnit: each refusal reason; pistol accepted, rifle/shotgun/smg refused; cancel before and after commit; target moved before kill. |

### 5. Injury speed (S13)

| | |
|---|---|
| Server-owned data | Injury severity and limb state in `PlayerCondition`. |
| Rule | `InjuryRules.speedMultiplier`: mild leg 0.85, severe leg 0.55, injured arm 0.95, legless crawl 0.20 (applied on top of vanilla's 0.3 crawl), improvised crutches 0.45, medical crutches 0.60, prosthesis 0.80 (no sprint). Worst applicable value wins. |
| Client display | The client picks the matching locomotion clip (`move_*`, `_gun` variant while a TaCZ gun is held) from the synced condition. |
| Saved state | In `PlayerCondition`. The attribute modifier is transient and re-applied on login/respawn from saved state. |
| Test | JUnit table test; NBT round trip; in game later: walk at each severity. |

### 6. Persistence

`PlayerCondition` serialises to player NBT through a Forge capability. Walker dormancy (`dormant`, `prone`, kind) is saved in the walker's entity NBT. Grabs and executions are transient and resolved at shutdown as above. Automated check: `ConditionNbtTest` round trip; scripted reconnect check is a GameTest to run later on the dedicated server.

## Walker entity (minimal, Stage 1)

`WalkerEntity` (GeckoLib, exported `walker.geo.json` / `walker_crawler.geo.json`): ordinary walker speed and senses, no variants. Kinds: upright, prone (dormant until it sees or hears), crawler. Melee approach, lunge at 1.2 blocks, grab attempt resolved by the server (S1). Takes no accumulated damage; dies only by brain kill. Three animation controllers as in `animation.md`, gait and arm/head sets from the UUID seed. Corpses persist as the ended state (design §12) — Stage 1 keeps the dead walker as a lying corpse entity that despawns only by explicit cleanup (not yet decided).

## Audio and blood (presentation)

Sound events under `holdfast:` with several variants each (`sounds.json`): `walker.groan`, `walker.breath`, `walker.snarl`, `walker.lunge`, `walker.bite`; contact families `impact.blunt`, `impact.slash`, `impact.stab`, `impact.gunshot` (flesh) and `impact.*_skull` for brain kills. They are original procedural recordings generated by `art/audio/gen_sounds.py` (no show audio). Blood spatter is a client particle burst sized by contact family. Both are triggered only by `HitFeedbackPacket`, which the server sends after it has decided a contact.

## Continuous player animation and execution-shot presentation (2026-10-17)

- Server-owned data remains the combat state above. Paired clip packets now include the target clip and restart nonce, so clients start both participants together and later entity-data sync does not restart the target. Paired target clips use zero transition ticks; locomotion still blends.
- Client display: idle/crouch, walk/sprint/crouch-walk, jump/fall/landing and injury selection from actual player movement, for local and tracked players. Cycle speed follows measured displacement with smoothing; state transitions use Player Animator fades. Weapon mapping includes vanilla swords/axes and empty-hand attacks. Carries layer over movement with modest gait follow-through and preserve head look input. The harness temporarily takes precedence for clip inspection. TaCZ and item-use poses keep their upper body and first-person view.
- Saved state: none added. Rejoining reconstructs presentation from current entity and injury state. Animation layers and camera caches are client-only and clear on disconnect.
- At a successful pistol execution's server kill tick, a cosmetic packet carries a snapshot of that execution's gun. The client uses TaCZ 1.1.8-hotfix2's native `getGunDisplay`/`playShootSound` and local muzzle-flash hook. No projectile or client hit is generated; skull contact feedback stays separate. The harness schedules its cosmetic shot from the same authored timing table and snapshots the held pistol.
- Verification: source carry clearance/support-grip audit, export and PA-transform checks, unchanged execution contact measurements, packet round trip, core JUnit/GameTests, both builds, then one consolidated visual review. Visual quality, remote rendering and muzzle-flash visibility require actual client observations. Ordinary melee damage currently resolves at the server's attack event, so agreement between free-swing anticipation and contact feedback remains a visual/test concern; paired execution timing uses the generated server table.

## Not in Stage 1 core yet

Wound GUI, illness, needs HUD, amputation interaction, stamina model (a flat cost is used), human NPCs, hordes, world generation.
