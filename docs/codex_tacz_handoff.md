# Local Codex handoff: TaCZ arm ownership (2026-10-16)

Base: `claude/character-animation` at `5a43eb4e470e9c60acc44e6b49a066283e42ae40` (draft PR #1). This handoff lives on scoped branch `codex/tacz-arm-handoff`. Gate 0 kit remains draft PR #2. Do not merge either PR.

## Read first

`AGENTS.md`, `CLAUDE.md`, `docs/decisions.md`, newest "Stage 1 in-game run" entry in `docs/handoff.md`, `docs/animation.md`, `testmod/animtest/README.md`, and the 2026-10-16 rows near the bottom of `docs/testing.md`. The decision record supersedes stale status text in earlier docs.

## New evidence from Andrew

Andrew reports: "with no clips enabled, tacz works perfectly." This is the no-Holdfast-clip baseline that was missing from the previous handoff. The report has no recorded commands, timestamps, client/server logs, jar hash, or separate pistol/rifle result yet. Treat it as an in-game observation from Andrew, not as a documented Gate 0 step 2 pass. Obtain the separate ADS/shoot/reload observations and logs when practical.

With `/hfa player move_limp` looping, both pistol and rifle previously scrunched shoulders/arms in third person and ADS failed; first person looked normal. Andrew's video: `C:\Users\drewl\Videos\2026-10-16 21-52-13.mp4` (local to his PC; not in repo). The test jar was SHA-256 `1169668BA8B341660041A3B9476D2B8F8F1844BE50C13A6D2F1C74AA1793A51C`. Server and client loaded the four-mod setup, but gun coexistence failed.

## Source inspection by Codex (hypothesis, not tested fix)

- `testmod/animtest/src/main/java/dev/holdfast/animtest/client/PlayerClips.java` registers `holdfast_animtest:test_layer` with priority **1000** and plays the full exported `KeyframeAnimationPlayer` there.
- `move_limp.json` keys `body`, `head`, both legs, both arms and both item parts. It can therefore compete for the same upper-body parts as TaCZ while looping.
- TaCZ's 1.20.1 source `PlayerAnimatorCompat` registers separate lower, loop upper, once upper, and rotation layers. `AnimationDataRegisterFactory` assigns priorities **93, 94, 95, 96** respectively. `AnimationManager` chooses upper-body hold, walk, aim, fire and reload animations according to gun state. Sources: https://github.com/MCModderAnchor/TACZ/blob/1.20.1/src/main/java/com/tacz/guns/compat/playeranimator/PlayerAnimatorCompat.java and https://github.com/MCModderAnchor/TACZ/blob/1.20.1/src/main/java/com/tacz/guns/compat/playeranimator/animation/AnimationDataRegisterFactory.java and https://github.com/MCModderAnchor/TACZ/blob/1.20.1/src/main/java/com/tacz/guns/compat/playeranimator/animation/AnimationManager.java
- These are source observations from the public 1.20.1 branch; verify against the actual 1.1.8-hotfix2 jar/API before claiming exact runtime behavior. Higher layer plus overlapping keys is a strong explanation, not yet a measured cause.

## Immediate work, in order

1. Reproduce/document the no-clip baseline and failure separately for a TaCZ pistol and rifle: third-person hold, ADS, shot, reload, and first-person behavior. Record client/server environment, commands, actual log lines, observed pose, test jar hash in `docs/testing.md`. Andrew can perform the visible checks.
2. On a new implementation branch from `claude/character-animation` (or advance this scoped branch), inspect the exact TaCZ jar and Player Animator part enablement/priority semantics. Define arm ownership for firearm-held locomotion/injury clips: TaCZ should own arms/item parts and ADS; Holdfast retains appropriate injury lower/body motion. Ensure no Holdfast layer masks TaCZ's gun rotations. Export variants from `art/characters/source` if needed; never hand-edit generated JSON. Test both held pistol and rifle. Do not assume simply deleting arm keys preserves TaCZ's animation; verify layer behavior.
3. Then author standing and appropriate ground handgun executions against TaCZ's actual pistol hold, with paired walker/crawler reactions and `/hfa pair` presets. S7 server weapon class must accept TaCZ index type `pistol` only. Harness is presentation/test only; server outcomes still belong in the future core mod.
4. Re-test the deployed but unseen melee changes: ground knife, standing machete, ground axe/machete/bat, and frontal-versus-temple `react_knife`. Fix editable source and re-export.
5. Re-run TaCZ Gate 0 step 2: pistol and rifle shot, reload, ADS, with and without Holdfast clips. Only call it passed from actual in-game evidence.

The harness source is `testmod/animtest`; build on Andrew's PC with `.\gradlew.bat build`. Put the resulting jar in both `C:\HoldfastGate0\holdfast-gate0\server\mods` and the Prism instance "Holdfast Gate0" mods folder, restart server via `.\run.bat nogui` and restart Prism client. AoTMonarch is op. Andrew can run `/hfa stop`, `/hfa player move_limp`, `/hfa pair ...`. Read the harness README for full commands.

Pipeline: `art/characters/source/make_all.sh` with numpy, scipy, Pillow and `PYTHONUTF8=1` on Windows. Run `validate_exports.py`, `check_pa_semantics.py`, `measure.py` and `pa_item.py` as appropriate. Revert PNG files that only re-encode identical pixels.

Keep both PRs draft. The agreed order is animations and gameplay mechanics, then world generation, then loot pools/remaining elements. Do not claim in-game success without a run. Update `docs/testing.md` and `docs/handoff.md` when stopping.
