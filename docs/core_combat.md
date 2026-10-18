# Holdfast core: Stage 1 server-owned combat slice

## Dawn follow-up (2026-10-18)

- **Protocol 5:** entity-owned vocal packets and ownership-qualified clip stops require matching core jars. Corpse state stops already-playing vocals on the client; server corpse guards refuse new ones, including after reload. Brain kills no longer start the external pack's long death-vocal pool. Confirmed impact audio remains.
- Free swings schedule one cosmetic server sound three ticks before contact (at least one tick after start), including misses. Executions use the same weapon-family sound before their kill tick; firearms retain TaCZ audio. No local predicted audio or extras-event emitter duplicates it. On higher RTTs, received sound can lag the locally predicted animation; localhost smoke is not a latency acceptance test.
- Pending swings cancel without stopping a newly owned grab/execution. Other interruption stops carry the expected old clip, so a delayed packet cannot clear a differently named newer paired action. Execution entry mirrors the Holdfast two-handed item's occupied-offhand refusal and refuses busy/spectator players.
- Upright AI starts `grab_seize` once and validates grip at its source-generated tick 8 (0.4 s). Success continues that walker clip, starts the prompt/player catch, then joins hold after four ticks. Crawler lunge also continues instead of replaying reach; prone no longer starts its clip twice. Alignment faces the victim toward the real attacker and never moves a close walker backward to the pairing distance. Grab hold clips loop. Server bite/escape outcomes and configured window lengths remain unchanged; the contact delay changed from 11 to 8 ticks for upright grabs.
- Unarmed light/heavy preparation now contacts at generated ticks 6/8, with lengths 12/16. Client displays the same regenerated clips. Blood profiles are cosmetic: blunt 64/110, slash 72/120, stab 48/85, gunshot 10/18 particles (ordinary/brain kill), retaining the optional renderer's 1,800-particle cap and entry/exit surfaces.

Newest `testing.md` distinguishes automated checks and actual client smoke from Andrew's pending acceptance. Older counts/status descriptions below are historical.

## Review 3 impact placement contract (2026-10-17)

The server supplies each confirmed impact's surface point and incoming direction. Free melee uses current posture-local regional boxes, with a closest-surface fallback for an accepted arc-margin ray. Region and point come from the same volume. This remains a static regional ray approximation, not animated mesh or weapon-mesh collision. Executions use source-generated blade/skull entry points/directions for the actual reaction/approach at the server kill frame. Bullets use TaCZ's collision segment against those volumes rather than an unprojected bullet tick position. Clients render outward entry blood there; a separately server-supplied traversed exit can render an exit burst. Feedback is transient, with no new saved state. Tests cover segment/surface math, live server feedback and generated execution transforms. Cosmetic packets never decide damage, regions or kills.

Status: **implemented in source; 26 JUnit tests and 22 GameTests (2026-10-17 runtime corrections; earlier: 20 and 11) (walkers, and grabs/bites/executions/injury speed with a Forge FakePlayer) pass (`core\gradlew.bat build runGameTestServer`). Core gameplay and the current animation revision still need a real-player review; see `stage1_bulk_test.md`.** Mod id `holdfast_core`, Gradle project `core/` (Forge 1.20.1-47.4.8, same pins as Gate 0). This document is the CLAUDE.md "before implementing" statement for each shared system, and the map of where each rule lives. `animation.md` S1–S14 lists the moments; this file says how the core decides them.

## Principles (from `decisions.md`)

- The server decides every outcome: hit region, brain kill, stagger, grab, escape, bite, protection, infection, execution validity, commit, kill, injury speed. Clients render what the server tells them.
- Animation `timeline` events, sounds, blood, particles and camera motion are presentation. The server never waits for or reads them.
- One timing table. `core/src/main/java/dev/holdfast/core/rules/CombatTimings.java` is generated from the same source (`walker_anims.EXECS`, grab windows) by `art/characters/source/gen_core_tables.py`, so clip lengths and server ticks cannot drift.
- All rules are plain Java in `dev.holdfast.core.rules` with no Minecraft types, so they are covered by fast JUnit tests (`core/src/test`). Forge glue in other packages only translates game state into rule inputs and rule outputs into game actions.

## Systems

### Presentation bridge under review (2026-10-17)

Server-owned data: unchanged confirmed contact family, point, direction, region and outcome in `HitFeedbackPacket`; melee contact timing remains server owned. Client display: optional Extra Gore 5.3 particles replace the previous dust/block burst, with directional blunt/slash/stab/gunshot profiles, world lighting, finite 20–35 second lifetime and the renderer's capacity limited to 1,800. Its automatic health/click emitter is unregistered on login to prevent duplication and early effects; non-walker core-confirmed contacts use the same bridge. No additional saved gameplay state, entities, damage or protocol fields. Missing Extra Gore retains the old core fallback.

Audio: an external audio-only resource pack generated by `art/audio/build_tzp_bridge.py` aliases the six core walker events to Tissou's ambient/hurt/death pools. Core contact sounds remain separate from gunfire and vocals. The script validates each Ogg reference, reports excluded missing source files and embeds provenance/credit; no third-party Ogg is committed. The pack changes presentation only and can be disabled to restore core audio.

Verification: both builds, unchanged server-rule/GameTests, source asset/reference and animation checks, then an actual modded client load. Confirmed-hit particle playback, surfaces, audio character and animation appearance require the consolidated in-game review. No performance or visual pass is inferred from compilation.

### 1. Hit regions and brain kills (S10, S11)

| | |
|---|---|
| Server-owned data | Per hit: the attacked walker's kind (upright, prone, crawler) and pose; the hit point in the walker's local frame; the weapon class; for charged blunt swings, the server-measured charge fraction. |
| Rule | `HitRegions.classify` maps a local hit point to `HEAD`, `NECK`, `TORSO`, `ARM`, `LEG` from the exported geometry (head cube 26–32 px up, etc.; prone and crawler use their lying layouts). `WalkerStrikeRules.resolve` returns `BRAIN_KILL`, `STAGGER` or `GLANCE`. Body hits never kill (no accumulated walker damage). Head: blades, knives, axes, machetes, hatchets, katanas and gunshots kill; blunt kills only at full charge (charge ≥ `FULL_CHARGE`), otherwise stagger. |
| Client display | `HitFeedbackPacket` (region, contact family, position) drives the blood spatter and the flesh/contact sound (`impact_*` families below), separately from the swing or gunfire sound. |
| Saved state | None. A dead walker is a corpse entity; walkers keep no damage. |
| Mod interactions | Vanilla melee: the attack on a `WalkerEntity` is cancelled and resolved by the core at the swing's contact tick (section 1a). TaCZ: the core listens to TaCZ's `EntityHurtByGunEvent.Pre` (compile-only dependency on the exact 1.1.8-hotfix2 jar) and uses TaCZ's own headshot flag plus the bullet position; TaCZ damage to a walker is replaced by the core's outcome. Other mods' damage to walkers is ignored unless it carries a hit position (no stray brain kills from fall or fire damage). |
| Test | JUnit: region boundaries for each walker kind; every weapon class × region; partial vs full charge. In game (later): head and body hits per weapon; TaCZ head and body shots. |

### 1a. Melee contact timing (2026-10-17)

Andrew observed walker hits registering as soon as he clicked. A click now only starts a swing; the hit is decided at the generated strike tick. Codex's follow-up slows light knife/machete wind-ups: light knife contact is now tick 5, heavy knife remains tick 6; light machete is tick 7. These timings need the visual review and remain a view-ray approximation of contact.

| | |
|---|---|
| Server-owned data | `MeleeSwings`: per player, one `SwingRules.Session` with the clip, the start tick, the contact tick and the state (pending / resolved / cancelled). It also holds the weapon class, the vanilla attack strength spent at the click, the blunt charge, and the selected slot and item at the click. |
| Timing source | `CombatTimings.SWINGS` is generated by `gen_core_tables.py` from each exported clip's timeline `commit`/`strike` events: the same `player_animation_extras` files the clients play. The contact tick is the `strike` time × 20, rounded. The clip comes from `SwingRules.clipFor(weapon, heavy)`, which client and server share. Attack strength ≥ 0.95 gives the heavy clip; knife light contact is at tick 5 and heavy at tick 6. A charged blunt release uses `SwingRules.chargedClip`. |
| Start | Clicking a walker: `AttackEntityEvent` is cancelled, so vanilla damage, knockback and sweep never run, and `MeleeSwings.click` reads and spends attack strength. Clicking into the air: the client sends `SwingPacket`, which names no target and no outcome, and the server runs the same `click`. Releasing a charged blunt weapon: `MeleeSwings.release` uses the server-measured charge. The swing is refused if the player is dead, a spectator, grabbed or executing; if the held item is a firearm (TaCZ owns those); if a two-handed weapon has something in the off hand; or while another swing is pending or its clip is still playing. Refused clicks are not queued. |
| Contact | At the contact tick the server takes the player's **current** eye position, view ray and entity reach (3 blocks in survival). It enters each live walker's box, grown by `ARC_MARGIN` 0.2 blocks to stand in for the blade's arc, and picks the nearest one entered. A block hit before that point (`ClipContext.COLLIDER`) makes the result `OBSTRUCTED`. The hit point and region come from `Strikes.meleeHitPoint`, and `WalkerStrikeRules` decides the outcome (body hits never kill; only brain hits do). Stagger, brain kill, knockback, the `HitFeedbackPacket` (blood and impact sound) and one point of durability happen only then. This is a validated ray, **not** a swept weapon volume and not mesh collision. A walker that walks into the ray before contact can be hit; one that has left reach or ducked behind a wall is a `MISS`. |
| Latency | The swinging client starts its clip on the click, about half a round trip before the server sees it. The server therefore subtracts `GrabRules.latencyAllowanceTicks(rtt)` (half the RTT, capped at 3 ticks or 150 ms) from the contact delay, never going below 1 tick. Server contact lines up with the visual contact for round trips up to 300 ms; the blood and sound feedback arrive half a round trip later. The same allowance ends the server's recovery early, so a click as the client's clip finishes is accepted. |
| Cancellation | A pending swing is cancelled, with a clip stop sent to all viewers, if the player switches slot or item, is grabbed, starts an execution, starts using an item (the charged swing excepted), dies, respawns, logs out or the server stops. A cancelled swing never resolves and frees the player to swing again at once. A resolved swing resolves exactly once. |
| Client display | The swinging client plays the clip from its own click (`InputEvent.InteractionKeyMappingTriggered`, read before vanilla resets attack strength; clicks on blocks are mining and play nothing). Other players get the server's `PlayClipPacket`. The charged release clip comes from the server, because it depends on what was in reach at release. Animation events never decide anything. |
| Known gaps | Attacks on non-walkers (animals, players) keep vanilla's click-time damage; only the clip is shown. A client's light/heavy guess can differ from the server's when attack strength is right at 0.95, which shifts its visual contact by up to 2 ticks. A full charge that probed a target at release but finds none at contact plays the heavy swing, not the overswing. |
| Test | JUnit `SwingRulesTest` (6, including the latency-shortened recovery): every melee clip has generated timing with contact inside the clip; the heavy and charged choices; the latency cap; one resolution; cancel before contact. GameTests `MeleeGameTests` (7, Forge FakePlayer): a knife kills at contact and not on the click or the tick before, a second click mid-swing is refused, durability is spent once at contact; a target that leaves reach is a miss; a block in between is obstructed; a weapon switch cancels; a body hit staggers once without killing; a full charged bat kills at the heavy contact, not at release; a partial charge staggers; an empty full charge picks the overswing. A mutation run that resolved on the click failed 6 of these. A real-client check of the timing still needs Andrew's review. |

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

#### 4b. Pistol executions spend a round (2026-10-17)

Andrew noticed pistol executions cost no bullet. A pistol execution is refused with `NO_AMMO` ("Out of ammo") unless the held TaCZ gun has a round: its magazine count plus a chambered round (`TaczGuns.rounds`). At the server kill tick, `TaczGuns.consumeRound` spends one round from the gun in hand, the way a shot would: the chambered round, re-chambered from the magazine if any are left, otherwise one from the magazine. This uses TaCZ 1.1.8's `IGun` methods (`getCurrentAmmoCount`, `reduceCurrentAmmoCount`, `hasBulletInBarrel`, `setBulletInBarrel`). Untested: headless GameTests don't load TaCZ. Whether TaCZ itself skips consumption in creative mode was not checked, so Holdfast always spends the round.

#### 4a. Executions from any approach (2026-10-17, Claude)

Andrew wants executions from the front, rear, either side and diagonals, within legitimate range and never through obstacles. Eligibility was never front-only: the player had to face the walker within 25° from any side. What made it front-only was the alignment. The server always spun the walker to face the player and slid it into the slot in front of them, and every authored pair assumed a frontal blow.

| | |
|---|---|
| Server-owned data | Unchanged session state, plus the chosen reaction clip (`Active.targetClip`). |
| Rule | `ExecutionRules.validate` checks weapon, posture clip, ownership, busy target, facing (25°) and blunt charge as before; distance moved to the alignment step. `ExecutionRules.align` then pairs without moving the walker. **Upright walkers:** the side the player stands on (front within ±45° of the walker's facing, rear beyond ±135°, otherwise left or right) picks the reaction `react_<w>`, `react_<w>_left`, `react_<w>_right` or `react_<w>_rear`. The walker turns in place to whichever authored approach needs the smallest turn (about 50° at most). **Lying walkers (prone, crawler):** no turn; the player is aligned on the skull, and an approach more than 120° from the head's facing (from over the body) is refused (`NO_ROOM`). **Both:** the player steps along their own approach line to the slot that puts the walker's skull at impact (generated `CombatTimings.EXEC_SKULL`, from `placement.py`) exactly where the unchanged player clip strikes. A step longer than 24 px is `TOO_FAR`; a step back of more than 8 px is `TOO_CLOSE`. |
| World checks | `Executions.placeable`: the player's box must fit at the slot without overlapping an upright walker (`NO_ROOM`); no block on the step at feet, waist or eye height, and none between the slot's eyes and the skull (`OBSTRUCTED`). The walker's box doesn't change (it only turns). Pistol-only firearms, exclusive target ownership, commit/cancel, the in-place check at the kill tick and the single server brain kill are unchanged. |
| Client display | Unchanged packets. The player is moved and turned, and the walker turns; both clips start together. Directional reactions keep the front pair's impact, kill and release times, so the server kill tick is unchanged. Hit from behind: the head is driven forward and the walker falls face first. From a side: the head is knocked away from the player and it falls on the far side. |
| Test | JUnit `ExecutionAlignmentTest`: every clip × 24 approach angles puts the skull exactly on the strike point (1e-6 blocks); upright turns are at most 55° (worst measured 51°: sectors of 90° plus the slot bearing; the server picks the approach needing the smallest turn); lying walkers don't turn; side naming; step limits; over-body refusal; skull data for every variant. GameTests `ExecutionGameTests`: front, rear, left, right and two diagonal approaches kill once, never move the walker and leave a corpse that can't be executed again; a wall between gives a refusal without moving anyone; a blocked slot is refused; a prone walker is refused from over its body, accepted from beside its head, and neither moves nor turns; empty hands and a second executor are refused. `check_exec_variants.py` measures blade entry and early clipping for every variant against the front pair. Natural-looking alignment still needs Andrew's look. |

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

## Despawning (2026-10-17)

Andrew asked for walker despawning to limit lag; the decision is recorded in `decisions.md`. Walkers stay persistent to vanilla (no despawn on chunk unload). Holdfast removes loaded ones by `DespawnRules`, checked once a second per walker on the server, using real non-spectator players:

| Rule | Default (config `walkers`) |
|---|---|
| A living walker with no player within `despawnDistance` for `despawnAfterSeconds` | 96 blocks, 60 s |
| A corpse that long out of range | same |
| A corpse `corpseDespawnMinutes` after death, if no player is within `corpseHiddenDistance` | 10 min, 24 blocks |
| Never: a walker that is grabbing someone | — |

Time out of range and corpse age are saved in the walker's NBT (`HfFarTicks`, `HfCorpseAge`) and keep counting after a reload. Setting a time to 0 disables that removal. Tests: JUnit `DespawnRulesTest` (3); GameTests remove a reloaded corpse past its time and a walker past its out-of-range time, and keep a fresh corpse and a fresh walker. The defaults are provisional, and performance has not been measured.

## Walker entity (minimal, Stage 1)

`WalkerEntity` (GeckoLib, exported `walker.geo.json` / `walker_crawler.geo.json`): ordinary walker speed and senses, no variants. Kinds: upright, prone (dormant until it sees or hears), crawler. Melee approach, lunge at 1.2 blocks, grab attempt resolved by the server (S1). Takes no accumulated damage; dies only by brain kill. Three animation controllers as in `animation.md`, gait and arm/head sets from the UUID seed. Corpses lie as entities until the despawn rules above remove them.

## Audio and blood (presentation)

Sound events under `holdfast:` with several variants each (`sounds.json`): `walker.groan`, `walker.breath`, `walker.snarl`, `walker.lunge`, `walker.bite`; contact families `impact.blunt`, `impact.slash`, `impact.stab`, `impact.gunshot` (flesh) and `impact.*_skull` for brain kills. They are original procedural recordings generated by `art/audio/gen_sounds.py` (no show audio). Blood spatter is a client particle burst sized by contact family. Both are triggered only by `HitFeedbackPacket`, which the server sends after it has decided a contact.

## Continuous player animation and execution-shot presentation (2026-10-17)

- Server-owned data remains the combat state above. Paired clip packets now include the target clip and restart nonce, so clients start both participants together and later entity-data sync does not restart the target. Paired target clips use zero transition ticks; locomotion still blends.
- Client display: idle/crouch, walk/sprint/crouch-walk, jump/fall/landing and injury selection from actual player movement, for local and tracked players. Cycle speed follows measured displacement with smoothing; state transitions use Player Animator fades. Weapon mapping includes vanilla swords/axes and empty-hand attacks. Carries layer over movement with modest gait follow-through and preserve head look input. The harness temporarily takes precedence for clip inspection. TaCZ and item-use poses keep their upper body and first-person view.
- Saved state: none added. Rejoining reconstructs presentation from current entity and injury state. Animation layers and camera caches are client-only and clear on disconnect.
- At a successful pistol execution's server kill tick, a cosmetic packet carries a snapshot of that execution's gun. The client uses TaCZ 1.1.8-hotfix2's native `getGunDisplay`/`playShootSound` and local muzzle-flash hook. No projectile or client hit is generated; skull contact feedback stays separate. The harness schedules its cosmetic shot from the same authored timing table and snapshots the held pistol.
- Verification: source carry clearance/support-grip audit, export and PA-transform checks, unchanged execution contact measurements, packet round trip, core JUnit/GameTests, both builds, then one consolidated visual review. Visual quality, remote rendering and muzzle-flash visibility require actual client observations. Ordinary melee now resolves at the clip's generated contact tick (section 1a); paired execution timing uses the generated server table.

## Not in Stage 1 core yet

Wound GUI, illness, needs HUD, amputation interaction, stamina model (a flat cost is used), human NPCs, hordes, world generation.
