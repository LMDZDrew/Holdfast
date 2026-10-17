# Assistant handoff

## Current state — 2026-10-16

- This repository foundation was prepared locally for `LMDZDrew/Holdfast`. Confirm remote publication and commit SHA before relying on the repository as the shared source of truth.
- Design baseline is transcribed in `design.md`. `decisions.md` records superseding instructions including rejection of Lost Cities.
- `architecture.md`, `mods.md`, `roadmap.md`, and `testing.md` separate proposals from verified implementation.
- No Minecraft version, loader build, dependency graph, runtime, or performance result has been validated. No custom mod code exists.

## Next task

Complete roadmap Gate 0. Obtain exact upstream versions, then run a clean client and dedicated-server compatibility test with TaCZ. Record real results before scaffolding custom code.

## Gate 0 animation-library run — 2026-10-16 (Claude, branch `codex/gate0-windows-kit`)

- Script fixed in `bd68bb6` (Java check under Windows PowerShell 5.1, explicit installer folder, TLS 1.2, transcript) and run by Andrew on Windows 11.
- Passed: hash-verified install; dedicated server reached `Done` and stopped cleanly; Prism client loaded both libraries, all Player Animator mixins applied, world played and saved. Details and logs summary in `testing.md`.
- Not covered: Holdfast assets (needs the test mod on `claude/character-animation`), a player joining the dedicated server, and TaCZ, which roadmap Gate 0 step 2 still requires.

## Handoff format for future work

Branch/commit; files changed; behavior observed; exact tests and commands; unresolved issues; next safe task. Separate proposed behavior from verified behavior.
