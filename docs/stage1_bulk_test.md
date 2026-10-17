# Stage 1 bulk test sheet (for Andrew)

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
