# Character models and animation (walker, crawler, player)

Branch `claude/character-animation`. Source of truth for the character rigs, clips, export formats, and where the server must decide outcomes. Clip-by-clip lengths, events and sounds are generated into [`animation_catalog.md`](animation_catalog.md).

## Status labels (read first)

| What | Where | Status |
|---|---|---|
| Walker and crawler geometry, walker and crawler clips | `art/characters/export/assets/holdfast/geo`, `.../animations` | **Exported asset.** Generated from source, statically validated against the GeckoLib 4.8.4 loader source. **Not tested in game.** |
| Player clips | `.../player_animation/*.json` | **Exported asset.** Statically validated against the Player Animator 1.0.2-rc1 loader source; pose equivalence checked against a model of its part semantics. **Not tested in game.** |
| First-person camera, sounds, events per player clip | `.../player_animation_extras/*.extras.json` | **Exported data for our core mod.** No consumer exists yet. |
| Walker textures (4 outfits), player skin | `.../textures/entity/walker/*.png`, `.../textures/entity/player/survivor_default.png` | **Placeholder.** Procedural pixel art to prove UV layout and readability; replace with authored art. |
| Browser preview, frame sheets | `art/characters/preview/` | **Preview.** Our own renderer of the exported keyframes. Not Minecraft, GeckoLib or Player Animator. |
| Anything tested in game | — | **None.** No Minecraft client or server has loaded these files. |

## Before integration (CLAUDE.md checklist)

- **Server-owned data.** Grab state (attacker, victim, start tick, window length, escape accepted/refused), bite resolution (region, protection piece, blocked/penetrating, piece damage), infection start, execution state (requested, committed, cancelled, kill tick), charge start/release ticks and charge level, hit regions and brain kills, injury state and movement multipliers, walker dormancy/wake. Every gameplay outcome below is decided there.
- **Client display.** Choosing and playing clips from synced state, the escape prompt and countdown, the bite marker, first-person camera motion, sounds and particles timed by animation events. The client may predict presentation but must not decide outcomes.
- **Saved state.** None in the animation layer. Persist only server state (injuries, infection timers, protection condition, walker dormancy). A reloaded walker or player restarts its idle or locomotion clip; an interrupted grab or execution resolves on the server, never by replaying an animation.
- **Interactions with other mods.** GeckoLib renders walkers and crawlers. Player Animator overrides vanilla player part transforms and the held-item transform (`rightItem`/`leftItem`); it will interact with TaCZ first-person and third-person gun poses, so integration must decide which owns the arms while a gun is held. Not yet tested.
- **Testing.** Commands and results are in `docs/testing.md`. The in-game test plan is at the end of this file.

## Candidate dependencies (not selected, not pinned)

Nothing here selects a platform. These are what the files were checked against.

| Library | Version and source checked | License | Used for | Checked how |
|---|---|---|---|---|
| Minecraft Java + Forge | 1.20.1; GeckoLib branch builds against Forge 47.4.8 (range `[47.1,)`), Player Animator against 47.0.1 | — | candidate platform from `decisions.md` | metadata only; no launch |
| GeckoLib | 4.8.4, `bernie-g/geckolib` branch `1.20.1`, commit `7d6f32db` | MIT | walker and crawler models and animations | read `BakedAnimationsAdapter`, `KeyFramesAdapter`, `Cube`, `FormatVersion`, `BakedModelFactory`, `Animation.LoopType`, `EasingType`; encoded in `validate_exports.py` |
| Player Animator (KosmX) | 1.0.2-rc1, `KosmX/minecraftPlayerAnimator` branch `1.20`, commit `a17ecb85` | MIT | player clips, held-item transforms, first-person arms | read `GeckoLibSerializer`, `KeyframeAnimation`, `Easing`, `PlayerAnimationRegistry`, `AnimationApplier`, `PlayerRendererMixin`, `HeldItemMixin`; encoded in `validate_exports.py` and `check_pa_semantics.py` |

bendy-lib is **not** required: the GeckoLib-format reader in Player Animator ignores `bend`, and no clip uses it.

## Model format

- Bedrock geometry, `format_version` `1.12.0`, one geometry per file, box UV, units of 1 px = 1/16 block. Front faces −Z; origin at the feet.
- Loaded by GeckoLib's `BakedModelFactory`. Box-UV sizes are floored by GeckoLib; all walker and crawler cubes are whole numbers.
- The player uses the **vanilla player model**. `art/characters/preview/rig/survivor_preview.geo.json` is a preview stand-in only, with weapon, crutch and contact-marker bones; never ship it.

### Upright walker — `geometry.holdfast.walker`, texture 64×64

| Bone | Parent | Pivot (px) | Cube |
|---|---|---|---|
| `root` | — | 0, 0, 0 | — |
| `hips` | root | 0, 12, 0 | — |
| `torso` | hips | 0, 12, 0 | 8×12×4 at uv 0,14 |
| `head` | torso | 0, 24, 0 | skull 8×6×8 at uv 0,0 |
| `jaw` | head | 0, 26, 3 | 8×2×8 at uv 32,0 |
| `rightArm` / `leftArm` | torso | ∓5, 22, 0 | 4×6×4 at uv 32,10 / 48,10 |
| `rightForearm` / `leftForearm` | arm | ∓6, 18, 0 | 4×6×4 at uv 24,20 / 40,20 |
| `rightLeg` / `leftLeg` | hips | ∓2, 12, 0 | 4×6×4 at uv 0,30 / 16,30 |
| `rightShin` / `leftShin` | leg | ∓2, 6, 0 | 4×6×4 at uv 32,30 / 48,30 |

### Lower-half crawler — `geometry.holdfast.walker_crawler`, texture 64×64 (same atlas as the walker)

`root` → `torso` (0,12,0) → `stub` (spine and torn hem, 5×3×3 at uv 0,40), `gut` (trailing, 2×7×2 at uv 16,40), `head`, `jaw`, arms and forearms as the walker. No hips or legs. This is a predefined model, not dismemberment (`decisions.md`).

### Player — Player Animator parts

Clips key these parts only: `body`, `head`, `rightArm`, `leftArm`, `rightLeg`, `leftLeg`, `rightItem`, `leftItem`. `torso` is never keyed; see the next paragraph.

**Part semantics (from Player Animator source).** `body` translates and rotates the whole player about a point 0.7 blocks (11.2 px) above the feet (`PlayerRendererMixin.applyBodyTransforms`). `torso` turns only the vanilla chest box about the neck and carries nothing else (`PlayerModelMixin.updatePart("torso", body)`). Our authoring rig has an upper-body lean bone (`torso`, pivot 11.2 px) carrying head and arms. On export the lean becomes `body` rotation, and the legs get counter-rotation plus a small position offset so the hips and feet stay where they were authored. `check_pa_semantics.py` replays every exported clip with Player Animator's part rules: hands, held-item tips, head and feet match the authored poses within 0.58 px.

## Textures

| File | Size | Layout | Status |
|---|---|---|---|
| `textures/entity/walker/{farmer,tshirt,scrubs,office}.png` | 64×64 | walker and crawler box UV (table above) | placeholder |
| `textures/entity/player/survivor_default.png` | 64×64 | vanilla player skin, base layer only, overlay empty | placeholder |
| `preview/rig/survivor_preview.png` | 64×128 | skin on top, preview props below | preview only |

Outfits are appearance variation of ordinary walkers, not variants.

## Animation format, naming and frame rate

- **Walker and crawler (GeckoLib).** Bedrock animation JSON (`format_version` 1.8.0). Key poses in seconds with `"lerp_mode": "catmullrom"`. GeckoLib converts to ticks (×20) and evaluates every render frame. GeckoLib's Catmull-Rom wraps the spline in ease-in-out, so in-game curves may differ slightly from the preview (unverified). Names: `animation.holdfast.walker.<clip>` and `animation.holdfast.crawler.<clip>`.
- **Player (Player Animator).** One clip per file in `assets/holdfast/player_animation/`, registered as `holdfast:<clip>`. Baked to **20 keys per second** (one per game tick) with explicit `"lerp_mode": "linear"`; Player Animator interpolates per frame. Bare rotation arrays would be read as **stepped** keys, so they are forbidden (the validator fails on them).
- **Extras (our core).** `player_animation_extras/<clip>.extras.json`, same Bedrock shape, 20 Hz. Contains:
  - a `camera` track: rotation is pitch (+ down), yaw (+ right) and roll (+ right) in degrees; position is x (+ player's left), y and z (+ back) in px from the eye;
  - `sound_effects` and `timeline` events;
  - `holdfast_meta`: the held prop, and `vanilla_pose: "swimming"` for the legless crawl, which must play inside the vanilla crawling pose.

### Walker controllers

Three GeckoLib controllers per upright walker, chosen per walker from a seed such as the entity UUID:

- body: `body.stand`, eight gaits, or `body.hungry` in pursuit;
- arms: six sets, or `arms.reach` near a target;
- head: six sets, or `head.snap`.

Full-body clips (postures, attacks, grabs, deaths, reactions) need the arms and head controllers stopped. Gait speeds are in the catalog; scale playback by actual speed so feet don't slide. Gaits change appearance only. Speed, senses and damage stay those of an ordinary walker; there are no special variants.

## Coverage

| Requested | Walker | Crawler | Player |
|---|---|---|---|
| Idle | `body.stand` + arm/head sets, `still`, `sit_slumped`, `prone_still` | `crawler_still` | (vanilla) |
| Walking | 8 gaits | `crawl` | (vanilla) |
| Pursuit | `body.hungry` + `arms.reach` + `head.snap` | `crawl_drag` | — |
| Attacks | `lunge` | `crawler_lunge` | — |
| Grabs | `grab_seize`, `grab_hold` | `crawler_lunge` → `crawler_ankle_hold` | `grabbed_seize`, `grabbed_struggle`, `grabbed_ankle_seize`, `grabbed_ankle_struggle` |
| Bite and confirmation | `grab_bite`, `grab_bite_blocked`, `prone_ankle_bite(_blocked)` | `crawler_ankle_bite(_blocked)` | `grabbed_bitten`, `grabbed_bite_blocked`, `grabbed_ankle_bitten(_blocked)`, `bite_confirm_{neck,forearm,leg}` |
| Timed escape | `grab_shoved`, `prone_ankle_kicked` | `crawler_ankle_kicked` | `grabbed_shove`, `grabbed_ankle_kick`, teammate: `grabbed_released`, `grabbed_ankle_released` |
| Prone to active | `prone_rise`, `prone_ankle_grab`, `rise_from_sit` | `crawler_still` → `crawl`/`crawler_lunge` | — |
| Head-kill executions | `react_knife`, `react_axe`, `react_machete`, `react_bat_charged`, `react_knife_ground_prone` | `react_knife_ground` | `exec_knife`, `exec_axe`, `exec_machete`, `exec_bat_charged`, `exec_knife_ground` |
| Charged blunt swings | `react_bat_quick` (stagger, not a kill) | — | `bat_charge_hold`, `exec_bat_quick`, `exec_bat_charged`, `bat_swing_quick`, `bat_charged_miss` |
| Injury-limited movement | — | — | `move_limp`, `move_arm_injured`, `move_crawl_legless` (20%), `move_crutch_improvised` (45%), `move_crutch_medical` (60%), `move_prosthesis` (80%) |
| Death | `death_headshot` | `crawler_death` | — |

## Where the server must decide — animations never do

Every `timeline` event in the clips is a **presentation cue** for sounds, particles, camera shake and knockback visuals. The server never reads animation events and never waits for a client to report one. It decides from its own state, clock and hit tests, then tells clients which clip to play.

| # | Moment | Server validates | Animation's role | Cue in clip |
|---|---|---|---|---|
| S1 | **Walker attack lands** | Its own attack timer (0.55 s after `lunge` starts; crawler 0.5 s), reach (proposed ≤ 1.2 blocks), line of sight, the victim not already grabbed | Plays `lunge`/`crawler_lunge`; shows nothing about success | `attack_hit` 0.55 / `grab_locked` 0.5 |
| S2 | **Grab starts** | Grab success from S1; snaps the attacker to the pairing distance (18 px upright, 19.58 px prone, 15.58 px crawler); records the window start tick | Plays the seize pair only after the server's grab packet | `grab_locked` 0.40 / 0.55 / 0.50 |
| S3 | **Escape** | Window start tick and length (1.6 / 1.2 / 0.9 s, plus the accessibility option). Accepts one escape input if its arrival falls inside the window plus a latency allowance (proposed: half RTT, capped at 150 ms). Applies ~0.75 blocks of knockback itself. Charges stamina (shove 5%). | Prompt and countdown are display only. Plays shove or kick after acceptance | `shove`/`shoved` 0.14, `kick`/`kicked` 0.14 |
| S4 | **Bite** | At window expiry without an accepted escape: chooses the region (upright: neck by default; ground grabs: leg), checks that region's bite-rated piece (intact blocks, including the breaking bite), damages it, or starts infection. A blocked bite keeps the grab active and starts a new window. | Plays the blocked or penetrating clip the server chose | `bite_blocked` 0.36, `bite` 0.38 |
| S5 | **Bite confirmation** | Only after S4 decides penetrating: infection timer, eligibility for amputation (head, neck and torso never) | `bite_confirm_<region>` after release, plus the HUD marker | — |
| S6 | **Teammate intervention** | The teammate's own hit or shove, validated like any other attack. A brain kill or accepted shove ends the grab on all clients. | `grabbed_released` / `grabbed_ankle_released` on the victim; the attacker's death or shoved clip | — |
| S7 | **Execution request** | Target type, distance within pairing tolerance (proposed ±4 px), facing (proposed ±25°), head exposed, weapon class, stamina, target not already executed or grabbing someone else. Then snaps both. | Plays the pair from the server's start packet | — |
| S8 | **Execution cancel or commit** | Cancel accepted if it arrives before the server's commit tick for that clip. After commit, the swing proceeds; nearby walkers still act (no invulnerability). | Plays cancel-out (blend to idle) or continues | `commit` (see catalog) |
| S9 | **Brain kill (execution)** | Applies the kill at its own tick for that clip: knife 0.28 s, bat charged 0.19 s, axe 0.45 s, machete 0.30 s, knife ground 0.47 s after start (measured blade entry; see testing). Aborts with no kill if the target moved or died first. | Reaction clip shows the fall | `kill` (cosmetic) |
| S10 | **Charged swing (not paired)** | Charge start and release ticks, charge level capped at the maximum. At strike time (0.20–0.22 s after release) runs its own head-region hit test. Full charge, suitable weapon and head → brain kill; head below full → stagger or threshold; body → stagger only; nothing hit → recovery lock until 1.5 s. | Plays charge hold, release, `react_bat_quick` (stagger) or `bat_charged_miss` | `strike`, `recovered` |
| S11 | **Ordinary head and body hits** | Hit regions and brain destruction from server hitboxes. Body hits never kill walkers. | `stagger`, `death_headshot` | `stagger`, `corpse` |
| S12 | **Dormant / prone to active** | AI wake decision from sight and sound | `prone_rise`, `rise_from_sit`, `prone_ankle_grab` | `grab_locked` |
| S13 | **Injury movement** | Injury and limb state, movement multiplier (20 / 45 / 60 / 80%), sprint lock with a prosthesis | Client picks the loop from synced state and scales playback; the animation never changes speed | — |
| S14 | **Death and corpse** | Death, reanimation, corpse persistence per `design.md` §12 | Fall clips | `corpse` |

Clip lengths are presentation lengths. The server's own timings are the ones listed above; keep them in one table in the core mod and test that the clips match them.

## Not verified (needs the game)

1. That GeckoLib 4.8.4 and Player Animator 1.0.2-rc1 load these files without warnings in a real Forge 1.20.1 client, and that a dedicated server starts with them installed.
2. Visual match of GeckoLib's Catmull-Rom easing to the preview.
3. That Player Animator's `rightItem`/`leftItem` rotation, applied in `HeldItemMixin`, orients our weapon item models as the preview assumes (blade out of the fist, at a right angle to the arm). Item model display transforms will need tuning.
4. First-person view with Player Animator's `FirstPersonConfiguration`, plus our camera track applied by the core.
5. The legless crawl playing correctly inside the vanilla crawling pose, and how to hide an amputated limb (Player Animator's GeckoLib reader has no scale channel).
6. Interaction with TaCZ arm poses while a gun is held.
7. Two-handed grips: vanilla arms have no elbows, so grips sit low and centred. Left-hand bracing reaches on the knife, machete and ground stab fall 4–8 px short of the walker.
8. Hitboxes for prone walkers, crawlers and corpses.

## In-game test plan (to run after the platform gate)

Load in a clean Forge 1.20.1 client and a dedicated server with GeckoLib and Player Animator only. Then check:

- every clip plays with no log errors;
- walker controllers layer correctly;
- feet don't slide at gait speeds;
- the grab window is accepted and refused correctly at 0, 100 and 200 ms simulated latency;
- the blocked bite on the breaking piece and the next penetrating bite;
- an execution cancelled before and after commit;
- a charged miss leaves the player exposed;
- first-person views.

Record results in `docs/testing.md`.

## Regenerate

```
cd art/characters/source
./make_all.sh                 # models, clips, pairing, export, validation, preview page
python3 check_pa_semantics.py # exported player clips vs authored poses under Player Animator rules
python3 measure.py            # weapon-in-skull timing for every execution
node test_preview.js          # preview page logic, headless
python3 render_previews.py    # frame sheets
python3 gen_catalog.py        # docs/animation_catalog.md
```

Requires Python 3.10+ with numpy, scipy and Pillow, and Node 18+ for the page test.
