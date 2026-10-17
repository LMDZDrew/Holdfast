# Stage 1 bulk test sheet (for Andrew)

Everything below was built and statically checked on 2026-10-17 but **not run in game**. Work through it in one sitting and note anything that looks wrong by section number. Branch `claude/stage1-combat-and-animation`.

## 0. Install (Claude or Codex does this before the session)

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
/hfa player hold_knife      (then hold_machete, hold_hatchet, hold_katana, hold_axe)
/hfa player swing_knife     (then swing_knife_heavy, swing_machete, swing_machete_heavy, swing_hatchet,
                             swing_hatchet_heavy, swing_katana, swing_katana_heavy, swing_axe, swing_axe_overhead)
/hfa player move_walk       (walk forward; then move_sprint while sprinting, move_crouch_walk while crouching)
/hfa player move_jump
/hfa stop
```
Look for: each weapon held the way it's used (knife low guard, machete up by the shoulder, hatchet low, katana two-handed in front, axe on the shoulder), blades and edges pointing the right way, swings that look like that weapon. For the walk clips: do the feet slide, and does it look better or worse than vanilla's walk? Repeat one walk clip **holding a TaCZ gun**: the gun pose and ADS must stay TaCZ's.

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
