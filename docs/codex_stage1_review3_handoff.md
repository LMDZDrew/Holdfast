# Codex prompt: Andrew's third review (2026-10-17): execution depth, resting axe, blood at the impact point

Continue Holdfast Stage 1 from **`claude/stage1-stances-executions`** at **`1e04b16`** (pushed). Claude worked in the separate worktree `C:\Users\drewl\Holdfast-claude`. Use your own scoped `codex/` branch from that commit; Claude did not edit `codex/stage1-motion-corrections`.

## Read first

`AGENTS.md`, `CLAUDE.md`, `docs/decisions.md` (especially "Review of stances and executions" and "Latest melee presentation direction"), the newest entries at the **top** of `docs/handoff.md` and `docs/testing.md`, then:

- `docs/core_combat.md`: 1a (melee contact), 4a (all-angle executions), 4b (pistol rounds), "Despawning";
- `docs/animation.md`: "Rest and ready stances";
- `docs/presentation_integration.md`: Extra Gore, and the Punchy evaluation (Punchy is not adopted);
- `docs/stage1_bulk_test.md`: the top batch.

## Boundaries (unchanged)

- Stage 1 only; no world generation.
- Keep PR #1 and #2 as unmerged drafts.
- Don't change dependency versions.
- Server authority: contact, hit regions, brain kills, executions, bites, death, despawning and persistence are decided on the server. Animations, particles and sounds are presentation.
- **Gun animations are accepted. Leave every `_gun` and `exec_pistol*` asset byte-identical**, and check that after regenerating.
- Never hand-edit generated JSON, `CombatTimings.java`, `Clips.java` or the catalog. Edit `art/characters/source/` and regenerate.
- Andrew likes the dynamic ready stances. Don't redesign them.

## Andrew's words (exact feedback, this review)

> sword executions do not go far enough, it needs to go deeper. same with knives. axes are still messed up in the resting position. also, blood particles sould come out of the waliker where the weapon/bullet impacts, not how it is now.

He tested build core `CF97AE6F…` / harness `CE0DB3BF…`, installed on 2026-10-17 at 22:35. "Sword" means the katana: it has no other sword class. He gave no view, angle or posture for these points. Check standing and ground, front and directional variants, and both views, before deciding what he saw. Read the newest Prism client log (`%APPDATA%\PrismLauncher\instances\Holdfast Gate0\minecraft\logs\latest.log`) for `[holdfast] pose jump` and `[holdfast] Extra Gore contact` lines from his session. Don't print launcher tokens.

He did not comment on pistol ammunition, despawning, rest arms, the katana's ground cut, all-angle executions, jumping or clicks. Those are **not** passed; they are simply unreported.

---

## 1. Katana and knife executions must go deeper

**Current state, measured by `art/characters/source/measure.py`.** "Depth" is how far the measured weapon point sits inside the 8×8×8 px skull cube, as the distance to its nearest face, so 4 px means the centre:

| Clip | Measured point | Enters (s) | Leaves (s) | Depth (px) |
|---|---|---|---|---|
| `exec_katana` (standing thrust, pairing 36 px) | `katanaTip` | 0.31 | 0.52 | 1.38 |
| `exec_katana_ground` (two-hand overhead cut) | `katanaEdge` | 0.50 | 0.76 | 2.10 |
| `exec_knife` (standing temple hook) | `knifeTip` | 0.27 | 0.52 | 2.23 |
| `exec_knife_ground` | `knifeTip` | 0.46 | 0.82 | 2.35 |
| Directional variants (`check_exec_variants.py`) | same | — | — | knife 1.44–2.00, katana 1.08–1.21 |

All of these are shallow: the blade only nicks the skull. The solver report (`art/characters/build/solver_report.txt`) shows misses of 1–3 px on the impact keys. The standing katana's earlier hold key couldn't reach a deeper target, which is why its impact was aimed shallow.

**What to do:**

1. **Agree a depth target and add it to the checks.** At impact and through the hold, the blade must be visibly *in* the head. A good bar:
   - **Knife:** the blade buried to the guard, about 5–6 px of its ~6 px blade inside the head, tip past the centre.
   - **Katana thrust:** the tip through the skull centre and toward the back of the head, roughly 6–10 px of blade inside.
   - **Ground cut:** the edge 4+ px into the skull.

   `measure.py`'s nearest-face depth can't express "past the centre", so add a second metric: **penetration length** along the blade axis, from where the blade enters the skull cube to the tip, in px. Report it per clip, fail below the agreed minimum, and include the directional variants in `check_exec_variants.py`.
2. **Source:**
   - **Knife:** `player_anims.knife()` targets `temple (-4, 4.5, -0.5)` → `deep (-0.5, 4.5, -0.5)` → drive `(0.8, 4.5, -0.5)`. Move the deep and drive targets further across the head, for example x +2…+3. Strengthen the lunge (`body_pos` and torso at I and 0.45) so the rigid arm can reach; misses mean the arm can't get there. The walker's `react_knife` pre-impact pose moves its head, so check `head_frame` at the impact key, not at rest.
   - **Katana thrust:** `player_anims.katana()` aims the tip at `hp(Hm, (0, 5, 1.5))`. Move it toward the back of the skull, for example local z +3…+4. Shorten the pairing distance (`walker_anims.EXECS["katana"]["dist"]`, now 36) only if the guard pose stays clear of the head at t = 0; at 30 px the guard tip was inside the skull. The walker's thrust reaction now keeps its head almost still, because a bigger head whip pulled the skull off the blade. Keep that property, or move the blade with the head.
   - **Ground cut:** `player_more.OVERHEAD["katana"]` uses `hit=(0, 7, 0)`, `follow=(0, 6, 0)`. Lower the hit point into the skull and add follow-through depth.
   - Rerun `make_all.sh` and **`measure.py`**. The kill ticks in `CombatTimings` come from `build/contact.json` (`first_in`), so if contact timing changes, regenerate the table and rerun the GameTests.
3. **Don't fake depth.** Don't lengthen the weapon models, and don't move the walker into the player. The weapon geometry must keep matching `gen_core_assets.py` and the in-game item models.
4. **Verify both views.** The first-person camera can make a deep stab read as shallow or the reverse. `/hfa pair exec_knife`, `/hfa pair exec_katana` and `/hfa freeze <seconds>` (harness) let Andrew inspect the impact frames from the side.

## 2. Fire axe resting carry is still wrong

**Current state:** `player_weapons.STANCES[("axe", "rest")]` uses the `_rest` arm (about −10° forward, 3° out) with axis `(0.15, −0.55, 0.82)` and edge `(0, −0.3, 1.0)`: the axe is held at the end of its handle, which points back and down, with the head trailing behind and low and the bit facing back. Claude made it that way after Andrew asked to "reverse the axe so that the head is facing the opposite direction". The version before that pointed the handle forward and down, head low in front, bit forward. Andrew now says it is "still messed up" without saying how. Renders of the current pose are in `core/build/test-evidence/2026-10-17-stances/review2.png` (ignored folder, worktree).

**What to do:**

1. **Find out what is wrong before re-authoring.** Ask Andrew for one specific: a screenshot, or which part looks wrong (head direction, height, blade facing, arm, clipping, or how it moves while walking). Andrew asked not to be pestered with repeated tests, so make this one short question. If that isn't possible, inspect it yourself in a client with F5 and record what you see.
2. **Likely root cause:** the grip, not the angle. Holdfast items are gripped at the end of the handle (item frame origin = fist, handle along local −z; the fire axe handle runs from z +3 to z −14.5, head at about −13 to −15). A heavy axe looks natural in one hand only when held **just below the head**, hanging along the leg with the head up by the hand, or carried over the shoulder. Holding a 17.5 px handle at its very end, angled down, forces the head to point at the ground or trail behind. Two viable designs:
   - **(a) Choke-up rest:** in the `rest` stance only, translate the item along its handle so the fist sits just below the head (Player Animator `rightItem` position channel, converted by `pa_item`; the constant position key there is the grip pivot). Add a `grip_offset` field to `STANCES`. The handle then hangs down along the outside of the right leg, head up at the hand, bit facing forward or outward. This is how fire axes are usually carried.
   - **(b) Rest on the shoulder:** this needs the handle to lie on the shoulder. Claude's rigid-arm attempt ran the handle through the carrying arm (`check_carries` measured 1.5 px inside it), because the fist and that shoulder are the two ends of the same arm. It only works combined with (a) and the arm hanging.
3. **Check that the rest → ready → swing transition stays smooth.** The ready axe is `hold_axe`, a two-hand "port arms" carry that Andrew likes. A grip offset in rest must fade back to the ready grip without the item sliding visibly through the hand, so check the fade frames.
4. **Checks:** extend `check_carries.py` for the grip offset; weapon cubes must stay out of the legs over walk and sprint, which is already checked. Add the axe to the next review batch with a precise question.

## 3. Blood at the actual impact point

**Current state.**

The **client** draws the burst at the packet position (`HitEffects.play` and, with Extra Gore loaded, `ExtraGoreEffects.play`, both at `hit.x/y/z` along `hit.dx/dy/dz`). The positions the **server** sends:

- **Free melee** (`combat/MeleeSwings.resolve` → `Strikes.apply`): the point comes from `Strikes.meleeHitPoint`, where the player's view ray meets the walker's **axis-aligned bounding box** (0.6 × 1.95, inflated 0.05), or the nearest box point. It is not the weapon's position at contact and not the model surface. A side swing that meets the head from the left can show blood on the box face toward the camera; for lying walkers the box is 1.6 × 0.5 and square.
- **Executions** (`combat/Executions.tick`, KILL): a **fixed point**, `target.position() + (0, 1.8 or 0.3, 0)`, with the direction from the player's eye. That is about the top-centre of an upright walker regardless of where the blade is. For prone and crawler walkers it is the hips, while the skull is 9–13 px ahead (`Executions.skullPoint` and `CombatTimings.EXEC_SKULL` already know where the skull is).
- **TaCZ shots on walkers** (`compat/tacz/TaczBridge.onGunHurt`, `EntityHurtByGunEvent.Pre`): `event.getBullet().position()`, the bullet entity's position when the event fires. For fast bullets this can be its position at the start of the tick, short of the walker, so the blood appears in front of the target. Shots on other entities (`onGunHurtOther`) behave the same.
- **Grabs and shoves** send no positioned blood, so they're out of scope.

**What to do (the server decides the point; clients only draw):**

1. **Executions.** Generate the weapon contact point per execution reaction and approach: walker-local px at the measured `first_in` time of the striking marker, from `measure.py` or `check_exec_variants.py`; for the pistol, use the skull point on the entry side. Emit it from `gen_core_tables.py` next to `EXEC_SKULL` (for example an `EXEC_CONTACT` map). In `Executions.tick` at KILL, transform it with the walker's yaw (`ExecutionRules.toWorld`, already there) and send that position. The direction is the blade or shot direction at contact, not eye → head. Optional: a second, smaller burst at `release` (blade pulled out) at the same point.
2. **Free melee.** Resolve the hit point against the **walker's head or body volume** rather than its AABB:
   - **Upright head:** the head cube, about 8 px, centred at walker-local y 29, z 0. For lying postures use the `HitRegions` layout.
   - **Point:** where the server ray enters that region's box. With the arc margin, clamp to the nearest point on the region's box surface.
   - **Region:** keep the classification from the same point, so blood and region agree.

   This is still a ray approximation (`core_combat.md` §1a); say so in the docs. Don't claim mesh collision.
3. **TaCZ shots.** Use the bullet segment from its previous position to its current position (or position plus `getDeltaMovement()`), clipped against the walker's box, or better the region box from step 2, for the entry point. Fall back to the current position. Use `getDeltaMovement()` as the spray direction (already done for non-walkers). Check whether TaCZ 1.1.8's `EntityKineticBullet` exposes the hit location or `xo/yo/zo` (read with `javap` on the jar in the Gradle cache, `modrinth/SzzJttH8/AzCBJlex`).
4. **Client.** No gameplay change; keep the Extra Gore path and the fallback. The burst should start **on the surface** and spray along the impact direction: outward from the entry for stabs, slashes and shots, plus an exit spray for gunshots.
5. **Tests.** GameTests: free-melee head and body hits whose feedback point lies on or inside the matching region box (expose the last feedback position for tests, as `MeleeSwings.lastResult` does). Execution KILL points within 1 px of the generated contact point for front and directional variants. A pure JUnit test for the bullet-segment intersection. TaCZ can't run headless, so its in-game check goes into the review batch.

## 4. Finish

1. Regenerate (`make_all.sh`, then `measure.py`, `gen_core_tables.py`, `gen_core_assets.py`, `gen_catalog.py`, `gen_testmod_clips.py`, `check_exec_variants.py`).
2. Check `check_carries`, `check_weapon_motion` and PA semantics (worst was 0.577 px).
3. Confirm the firearm assets are unchanged; restore regenerated PNGs whose pixels didn't change.
4. Build `core` with `.\gradlew.bat build runGameTestServer --no-daemon` (last result: 46 JUnit, 34 GameTests) and build the harness.
5. **Deploy:**
   - check that no client is running and nobody is online;
   - stop server PID 1520 (a snapshot; check it) with the `AttachConsole` + `CTRL_C` helper;
   - confirm "All dimensions are saved";
   - install both jars on both sides and hash them;
   - restart and confirm `Done`.
6. Write the `testing.md` and `handoff.md` entries at the top of each file, and add one short review batch at the top of `stage1_bulk_test.md`:
   - katana and knife depth (standing and ground, two directions);
   - the axe rest, with the specific question;
   - blood placement for melee head and body hits, executions, and pistol and rifle shots;
   - carried-over unreported items: pistol rounds, despawning, rest arms.

Still open, not part of this task: the arm rise when attacking from rest (`ClientClips` 2-tick melee fade; Andrew's 21:30 log showed 92–98°), the paired execution entry snap, standing knife execution contact (`/hfa freeze`), real-player reconnect, and the remaining TaCZ Gate 0 rows.
