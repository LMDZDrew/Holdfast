# Stage 1 bulk test sheet (for Andrew)

## Current review: scheduled dawn corrections — 2026-10-18

Branch `codex/stage1-dawn-corrections`; installed hashes and deployment evidence are at the top of `testing.md`. Both sides require **core protocol 5**. Start Prism **Holdfast Gate0**, join `localhost`, and run `/hfa stop`. First Person Model is an optional client trial; its F6 toggle is reversible. Report these numbered items together. Automated results below do not constitute your acceptance.

1. **Dead walkers stay quiet.** In survival, let a walker start a long groan, then brain-kill it by melee, execution, or gunshot. The existing voice should stop promptly and the corpse should not start new growls. Nearby living walkers may still vocalize; try one isolated walker first.
2. **One continuous grab.** Let an upright walker approach and grab you. Its first grip should lead directly into the prompt/brace, without retreating and reaching again. Press the displayed F once to escape. Repeat with a crawler and a prone ankle grab. Report any snap, restart, drift, or delayed prompt, and the posture. A blocked bite still opens another server-owned escape window.
3. **Blood amount and location.** Compare real blunt body/head contacts, knife stabs, katana/machete slashes, and pistol/rifle shots. Melee should be substantially bloodier; shots should remain modest. Blood must still begin at the actual impact surface. Air misses must produce neither blood nor impact sounds. These changes increase bounded bursts; persistent pools/dismemberment belong to the later phase.
4. **Katana depth.** Try standing F executions from front/side/rear, plus prone/crawler finishes from the head end. Check the slightly deeper insertion and withdrawal. Optional side inspection: `/hfa pair exec_katana`, `/hfa freeze 0.32`; ground pairs use `exec_katana_ground_prone` or `exec_katana_ground_crawler` and `0.50`. Then `/hfa stop` before normal play.
5. **Punches and swooshes.** Empty-handed, single-click and rapid-click into open air and at a walker in both views: preparation should be readable, impact delayed to the punch, and no sideways torso twitch. For knife/hatchet, katana/machete, and bat/hammer/long axe, listen for their respective pre-contact swoosh. Miss into open air rather than a block (mining is separate). Each accepted swing should sound once; only a confirmed hit gets the additional wet impact. The rejected `axe_hit.mp3` ding remains excluded.
6. **Your body in first person.** Look down with empty hands, knife, axe and katana; walk, crouch, jump, attack, and let a walker grab you. Check torso visibility, camera/head clipping, duplicate arms/items, and animation continuity. Briefly toggle F6 off/on to check fallback. Harness previews deliberately use their own renderer; evaluate normal gameplay after `/hfa stop`.
7. **Preserve accepted behavior.** Your axe poses and pistol ammo behavior were accepted on the preceding build. Check axe carry still looks right. With pistol and rifle separately, check ADS, fire/reload and F routing with the new body renderer. A two-handed Holdfast weapon with an occupied offhand should now refuse an execution. Remaining despawn, reconnect, jumping and Gate 0 rows are still open unless you explicitly observed them.

The following batches are historical; this section is the current consolidated request.

## Earlier review: impact-site blood, execution depth and axe direction (Codex, 2026-10-17)

Branch `codex/stage1-review3-corrections`; deployment status and exact jar hashes are in the newest `testing.md` entry. Both sides need core protocol **4**. These are pending observations, not passes. Join `localhost`, use flat ground/daylight, run `/hfa stop`, and report by number in one reply. Check both views where practical.

1. **Knife and katana depth.** Run `/hfa pair exec_knife`, `/hfa pair exec_katana`, `/hfa pair exec_knife_ground_crawler`, `/hfa pair exec_knife_ground_prone`, `/hfa pair exec_katana_ground_crawler`, `/hfa pair exec_katana_ground_prone`. The knife should bury nearly to the guard; the katana thrust should enter deeply, and its ground cut should bite into the skull. For a side inspection use `/hfa freeze 0.33` (standing knife), `0.32` (standing katana), or `0.50` (ground), then `/hfa stop`. Also try real **F executions** with each weapon against `/holdfast walker upright` from the front and a side/rear; try prone/crawler finishes from the head end. Report shallow contact, early clipping, or a head sliding off an embedded blade.
2. **Resting fire axe.** Hold the fire axe, wait 4 s without attacking, then stand/walk/sprint and inspect first/third person. Its head/blade should point forward/down like the hatchet's resting carry, with the hand **halfway up the handle at rest**. Attack once: it should regrip to the familiar combat hold, then return to the midpoint after relaxing. Report direction, sliding/transition, or clipping problems.
3. **Blood at the walker impact.** Use actual core walkers and real melee/F executions, rather than `/hfa` or `/holdfast presentation` effects. Hit a walker in the torso with the axe (stagger), then in the skull with the knife/katana. Try front and side/rear executions and a prone/crawler finish. Blood must start where the blade contacts the skull/body, including the lying walker's head, never at its hips or a floating point. With a **TaCZ pistol and rifle separately**, fire body and head shots: entry blood should begin on the entry side, with any exit burst on the far surface. Include misses: they must produce no blood. Report any offset with weapon, posture, approach, and view.
4. **Torso on clicks.** After `/hfa stop`, empty hands, knife, axe, then bat: single and rapid clicks into air and at a walker, from rest and ready, in both views. The torso should no longer swivel sideways with each ordinary swing. Forward strike lean remains. Report any remaining twitch and the weapon/view; misses should still show no blood.
5. **Carryover checks still unreported.** In survival, note pistol ammo before/after an F execution: it should spend one round; an empty pistol should refuse. Check resting arms for the other melee weapons. Spawn walkers, leave over 96 blocks away for over 60 s, return; living walkers should despawn. Corpses should disappear after 10 min only while nobody is within 24 blocks. Existing jump, recovery, reconnect and remaining TaCZ Gate 0 checks remain open; previous silence does not pass them.

6. **Grab polish and F controls.** Open Key Binds: **Holdfast -> Escape / Execute / Cancel** and **TaCZ -> Melee** should both show F. Swap offhand moved from F to Y to avoid changing weapons on an execution request. In survival, let an upright walker grab you, then a prone/crawler grab your ankle. Check the brace/slip/recovery motion and pressure prompt; press the displayed key once to escape. The paired shove/kick should finish instead of snapping into a generic release. With a pistol aimed at a nearby walker, F should request execution without also playing TaCZ melee. With a rifle or no execution target, F should retain TaCZ melee. Rebind the Holdfast key temporarily, confirm the prompt follows it and the two actions are independent, then restore your preferred bindings. See `controls.md`; TaCZ's own Reset button still uses its shipped V default.

Earlier batches below are historical. Quick pistol/rifle hold and ADS observation is useful during item 3, but gun animation assets are protected and not redesigned in this pass. Server startup, headless tests and software renders cannot accept these visual outcomes for you.

## Earlier review: your stance and execution notes (2026-10-17, second pass)

Branch `claude/stage1-stances-executions`; hashes of the installed jars are in `testing.md`. Run `/hfa stop` first. Report by number.

1. **Rest arms and reversed axe.** Hold each melee weapon at rest (no fighting for 4 s): the arms should now sit close to the vanilla stance, not flared out. The fire axe at rest is reversed, with the head trailing behind you, low, bit facing back. If "reversed" meant something else (for example, only the blade turned to face the other way), say so. Ready stances are unchanged.
2. **Katana executions.** The katana now has its own two-handed executions instead of the machete chop: standing, a thrust through the face from the guard (it hangs on the blade, then drops when you draw it out); on a prone or crawler walker, a two-handed overhead cut. Try it from the front, a side and behind, standing and on the ground.
3. **Pistol executions cost a round.** Note the ammo count, execute with a TaCZ pistol: one round should be gone. Empty the pistol: the execution should refuse with "Out of ammo".
4. **Despawning.** With `/holdfast walker`, spawn a few, kill one, then go more than 96 blocks away for over a minute and come back: the living ones should be gone. A corpse disappears 10 minutes after death, but only while nobody is within 24 blocks of it. These are provisional defaults in the `walkers` section of the server config (`despawnDistance`, `despawnAfterSeconds`, `corpseDespawnMinutes`, `corpseHiddenDistance`).
5. The earlier items still stand if not yet tried: attacks and fast clicks, jumping, executions from every side, and the quick gun check.

## Earlier review: stances, all-angle executions, plus Codex's motion fixes (2026-10-17)

Branch `claude/stage1-stances-executions`, code `1da61ff`, on top of Codex's `c327bef` (its motion corrections are included and also still await your look). Installed on both sides: core `CA805B1D…`, harness `4CC35E0F…` (full hashes in `testing.md`). The server restarted at 20:02 and is ready; the client is closed. The no-ding impact pack and Tissou pack are unchanged. Run `/hfa stop` first. Report by number; "fine" is a valid answer.

1. **Rest and ready.** Hold the knife, hatchet, katana and fire axe one after another, standing, walking, sprinting and crouching, in both views. Each should rest naturally:
   - knife and hatchet: blade or head forward and down at your side;
   - katana: lowered, blade forward and down;
   - axe: one hand, head low in front.

   Attack once (into the air counts): each should come up to ready at once and the attack should not wait for it:
   - knife: forward guard;
   - hatchet: head up;
   - katana: two-hand guard;
   - axe: both hands, head up by your left shoulder.

   Stop fighting: about 4 s later each should relax back to rest. Holding, walking or mining should never bring up ready. Switching weapons should start at rest. The machete now also points forward and down instead of hanging straight down.
2. **Attacks and clicks.** Knife light and heavy, axe light and overhead, from rest and from ready, standing and moving, both views. Then click as fast as you can during a swing. Look for the weapon spinning or jumping at the start or end of the hit, and for any torso shimmy on clicks.
3. **Jumping.** Empty hands, then knife and axe: standing jumps, running jumps, repeated jumps, walking off a ledge, under a low ceiling. Both views.
4. **Executions from every side.** `/holdfast walker upright`, then V with the knife from the walker's front, back, left, right and a diagonal. Repeat a few with the axe, machete, charged bat and a TaCZ pistol. The walker should stay where it stands (turning at most about 45°). You step in to it; from behind it pitches forward and falls face first, from a side it is knocked away and falls on the far side. Then `/holdfast walker prone`: execute from in front of and beside its head; from over its body it should refuse ("No room to do it here"). Put a wall or fence between you and a walker: it should refuse ("Something's in the way").
5. **Guns.** Quick pistol and rifle hold and ADS check only; the firearm animation files are byte-identical.

Still open and not covered here: standing knife execution contact (`/hfa freeze`), pistol execution ammunition, real-player reconnect, and the remaining TaCZ Gate 0 rows.

## Previous review: motion corrections and no-ding audio (2026-10-17)

Branch `codex/stage1-motion-corrections`, code `e3f2181`. Installed core `644ED4B7…`, harness `B57180A7…`; five-cut impact pack `5378A624…`. Server restarted and ready, client left closed. Automated checks passed; **these visual outcomes are not yet accepted**. Use `/hfa stop` so core layers own playback. One batch when ready:

1. **Clicks:** with empty hands, knife and axe, attack the air/a walker, then click rapidly during recovery. Check both views for the unwanted torso shimmy or repeated animation restarts. Intentional body lean inside the attack remains; say if that is the movement you dislike. Block mining remains vanilla input.
2. **Jump:** empty-handed, then knife/axe: standing/running jumps, repeated jumps, off a ledge and under a low ceiling. Check rising, apex, fall and landing in both views. Creative flight and ladders should not force a nongun falling/landing pose.
3. **Weapon recovery:** knife light/heavy and axe light/overhead, idle and moving, both views. Check whether the weapon still spins/jumps as the hit finishes and returns to carry. Include a quick slot switch. Server contact should still coincide with the strike.
4. **Audio:** actual blunt/slash/stab contacts. The metallic ding from `axe_hit.mp3` should be absent; the remaining approved wet/blade effects should play. No new blood or walker voice change.
5. **Gun preservation:** quick pistol/rifle hold and ADS check only to catch regressions; all 28 protected firearm assets match the previous jar byte-for-byte. This does not complete the broader fire/reload/ammo Gate 0 evidence.

Rest/ready stance separation and execution approaches from all sides are **not implemented by this revision**; Claude's next prompt is `claude_stage1_motion_followup.md`, including a proposed Punchy first-person compatibility trial. The older sheets below retain outstanding combat/persistence checks and historical instructions.

## Next review: runtime corrections + blood/audio (2026-10-17, branch `codex/stage1-blood-audio`)

Andrew's last review reported floating corpses, click-time hits, a wrong hatchet carry, unstable weapons, and unacceptable blood/audio. He subsequently reopened blood/audio and authorized animation quality work while away. This revision includes Claude's runtime fixes plus the Extra Gore confirmed-contact adapter, Tissou audio-only pack, and slower light knife/machete wind-ups with regenerated server contact times. Use `/hfa stop` first so the automatic core layers are in control. Report by number; "fine" is a valid answer. All visual/audio acceptance remains pending Andrew's review.

Current installed jars: core `FF4870A8…`, harness `A682441F…` on both sides (protocol 3); client also has Extra Gore 5.3, Bookling Gear 3.15 and the enabled `Holdfast-TZP-audio-2.7.zip`. Full hashes and evidence: `testing.md` and `presentation_integration.md`.

1. **Corpses on the ground.** `/holdfast walker upright`, `/holdfast walker prone` and `/holdfast walker crawler` (a prone walker wakes when you come close). Kill each with an ordinary head hit (knife or machete), plus one V-key execution. Each corpse should lie on the ground, not float. Leave the area, come back, and relog once: the corpses should still be lying and should not replay their fall. Any corpse that floated before this build should drop to the ground when its chunk loads.
2. **Hit at contact.** With the knife, machete, hatchet and fire axe, hit a standing walker's head and body. The flesh sound, blood and stagger or kill should come when the blade visibly lands, not on the click. Then click while the walker is just out of reach, or step back as you click: that should be a miss. Hitting through a wall or fence should do nothing.
3. **Charged bat.** Hold right-click with the bat for a full second, then release it at a walker's head: the heavy swing should kill when it lands. Release with nothing in front of you: the overswing. Release early: a light swing and a stagger.
4. **Hatchet carry.** Hold the hatchet standing, walking, sprinting and crouching, in both views. The new carry is low at your side, head forward and a little up, bit down. Then swing it a few times (the swing itself is unchanged).
5. **Weapon steadiness.** For each melee weapon, in both views: idle, walk, sprint, crouch-walk, jump; one swing; several swings clicked as fast as you can; switch hotbar slots mid-swing; hold right-click with the bat. Say which weapon, action and view still looks unsteady. Then send `logs/latest.log` from the Prism instance: every `[holdfast] pose jump` line names the clip and layer that moved.
6. **TaCZ regression.** Hold a pistol, then a rifle: third-person hold, first-person ADS, fire and reload. Nothing in these fixes touches TaCZ.
7. **Blood and audio.** Around real `/holdfast walker` entities, compare blunt, slash, stab and TaCZ gunshot contacts. Blood should be dark red droplets/surface spatter rather than red dust/block chunks, with one burst at contact and no blood for a miss. Put a wall behind the target to inspect surface marks; wait for marks to fade. Walkers should now use the Tissou sounds from the Modrinth pack you liked. Idle samples are spaced out, pursuing walkers use the hurt/snarl pool, and deaths use the death pool. Report voice character/volume, impact sound differences, wall/floor blood and any FPS drop. Optional cosmetic-only previews: `/holdfast presentation blunt`, `/holdfast presentation slash`, `/holdfast presentation stab`, `/holdfast presentation gunshot`; `/playsound holdfast_core:walker.groan hostile @s`. These previews do not exercise real damage or contact timing.

Not covered by these fixes, and still open from earlier sheets: standing knife execution contact (`/hfa freeze`), section 4's grabs, bites, executions and persistence, the separate TaCZ pistol/rifle Gate 0 rows, and real-player reconnect.

The original sections 1-3 were reviewed on 2026-10-17; exact feedback is in `codex_stage1_animation_feedback.md`. Section 4 has not yet been run. The current Codex revision is on `codex/stage1-animation-quality`; its build and installed jar hashes are recorded in `testing.md`.

## Consolidated review for the Codex revision

Run this only after the current jars have been built, checked and installed. `/hfa` clips temporarily take precedence over the automatic core animation layers; `/hfa stop` returns to ordinary play.

1. Run `/hfa stop`. With empty hands, then each **Holdfast** melee item (including the bat), stand, walk, sprint, crouch, crouch-walk, jump and land, and left-click into the air. Repeat a few moves with a vanilla sword and axe. Look for relaxed blade-down one-hand carries, clear two-hand grips, moving shoulders, smoother transitions, distinct swings, and whether first-person views remain usable. Continuous movement uses the real core controller; no `/hfa player move_*` command is needed.
2. Run `/hfa pair exec_knife`. Check contact. If it still lands short, run `/hfa freeze 0.27`, then `0.30` and `0.33` to seek both clips to those exact times. The pose stays frozen so it can be viewed from the side and above. Report where the tip sits relative to the temple; the client log also records the skin model (classic/slim). `/hfa stop` clears inspection. The knife pose itself has not been moved to hide the mismatch; the exporter now compensates the library's actual body pivot.
3. Run the three pistol presets in section 1. Each now has a cosmetic shot using the held pistol's own TaCZ sound, separately from core skull contact audio. A currently held TaCZ pistol is preserved, so repeat with a second pistol if available; otherwise the harness equips a Glock 17. The native local muzzle-flash hook is invoked; its visibility still needs review. Harness shots are cosmetic and do not fire a projectile or consume ammo.
4. Run **section 4 below** for the first time. The walker vocals and flesh/skull impact audio are original procedural sounds from `art/audio/gen_sounds.py`; they play around `/holdfast walker` entities and server-resolved contacts. The harness test walkers and most harness sound markers still only log cues. Report sound character, volume and timing, blood, actual combat, grabs/bites, V-key executions and condition persistence by the section's item numbers.
5. Finish the TaCZ check with **pistol and rifle reported separately**. For each, check third-person hold, first-person ADS, fire and reload after `/hfa stop`, then while `/holdfast injury LIMP` is active and you walk. Finish with `/holdfast injury NORMAL`. All mods stay in the same pack. No second human tester is required.

These are pending observations, not recorded passes. In particular, the exact cause of the standing knife's reported miss, automatic animation quality, client shot playback, remote rendering and core section 4 remain open until tested.

## 0. Install (done 2026-10-17: core `A718C40A…`, harness `63E968EB…` in both mods folders; redo after any rebuild)

- Build both mods: `testmod\animtest\gradlew.bat build` and `core\gradlew.bat build` (Java 17).
- Copy **both** jars — `testmod/animtest/build/libs/holdfast_animtest-0.1.0.jar` and `core/build/libs/holdfast_core-0.1.0.jar` — into `C:\HoldfastGate0\holdfast-gate0\server\mods` and the Prism "Holdfast Gate0" mods folder, next to GeckoLib, Player Animator and TaCZ.
- Start the server (`.\run.bat nogui`), launch Prism, join `localhost`, stand on flat ground in daylight.

## 1. Handgun executions (harness)

```
/hfa pair exec_pistol
/hfa pair exec_pistol_ground_crawler
/hfa pair exec_pistol_ground_prone
```
A TaCZ Glock 17 is put in your hand. Look for: the clip **starts and ends in TaCZ's normal pistol hold** (no snap), the barrel points at the forehead / back of the skull at the shot, recoil flip, the walker's head whips back (standing) or bounces (ground). Then do each once in first person.

## 2. Melee fixes (harness)

```
/hfa pair exec_knife
/hfa pair exec_machete
/hfa pair exec_knife_ground_crawler
/hfa pair exec_knife_ground_prone
/hfa pair exec_axe_ground_crawler
/hfa pair exec_machete_ground_prone
/hfa pair exec_bat_ground_crawler
```
Look for: knife into the walker's left temple and its **head knocked sideways** (new reaction); machete into the side of the skull; ground knife with **no arm passing through your chest**; axe/machete/bat ground strikes landing on the head.

## 3. Weapon families and locomotion (harness)

```
/hfa weapons
/hfa player hold_knife      (then hold_machete, hold_hatchet, hold_katana, hold_axe, hold_bat)
/hfa player swing_knife     (then swing_knife_heavy, swing_machete, swing_machete_heavy, swing_hatchet,
                             swing_hatchet_heavy, swing_katana, swing_katana_heavy, swing_axe, swing_axe_overhead)
/hfa player move_walk       (walk forward; then move_sprint while sprinting, move_crouch_walk while crouching)
/hfa player move_jump
/hfa stop
```
Look for: relaxed knife/machete/hatchet carries with the blades down, katana two-handed in front, axe and bat low across the front with the weapon clear of the body, and distinct swings. For the walk clips: do the feet slide, and does it look better or worse than vanilla's walk? Repeat one walk clip **holding a TaCZ gun**: the gun pose and ADS must stay TaCZ's.

## 4. Core combat (holdfast_core, the real systems)

```
/holdfast walker              spawns an upright walker 4 blocks ahead
/holdfast walker crawler
/holdfast walker prone        a dormant prone walker (wakes when you come close)
```
Give yourself weapons from the **Holdfast** creative tab (kitchen knife, machete, hatchet, fire axe, baseball bat, katana, sledgehammer, and the bite guards).

| # | Do | Expect |
|---|---|---|
| 4.1 | Hit a walker's body several times with any melee weapon | it staggers and is knocked back, **never dies** |
| 4.2 | Hit its head with a knife / machete / axe at full swing (wait for the attack bar) | dies at once, blood spray and a skull sound; the body stays as a corpse |
| 4.3 | Baseball bat to the head with a plain left-click | stagger only. Then aim at the head, hold right-click ~1 s and release (the release is the swing) | dies. A full charge released at nothing overswings and stumbles |
| 4.4 | Shoot a walker in the body, then the head, with a TaCZ pistol and a rifle | body: stagger only; head: dies. Separate flesh vs skull sounds from the gunfire |
| 4.5 | Let a walker grab you (stand still in front of it) | a prompt bar with the key (default **V**); press V in time → you shove it off. Let the bar run out → bite: "Bitten (neck)" in the top-left with a decline bar and, for an arm or leg, an amputation timer |
| 4.6 | Wear a **Riot Collar** (head slot) and let it grab you without escaping | the first bites are blocked ("didn't get through"), the grab keeps going; once the collar is used up the next bite gets through |
| 4.7 | Prone walker: walk up to it | it wakes and rises, or grabs your ankle (kick free with V) |
| 4.8 | Look at a walker within ~1.5 blocks holding a knife and press **V** | execution plays and kills it. Press V again during the first ~0.2 s → cancelled. With a rifle or shotgun → "Can't finish it with this"; with a pistol → it works |
| 4.9 | `/holdfast injury LIMP` then walk; `LIMP_SEVERE`; `CRUTCH_MEDICAL`; `NORMAL` | speed changes (85 / 55 / 60 / 100%) and the matching walk plays; with a gun held, TaCZ keeps the arms |
| 4.10 | After 4.5, log out and back in; restart the server | still bitten, same timer; injury speed kept |
| 4.11 | Listen near walkers | raspy groans, breathing, snarls when chasing (original synthetic audio, not show recordings) — say if they sound right |

Report per number: OK, or what was wrong. Logs: server `logs/latest.log`, Prism `logs/latest.log`.
