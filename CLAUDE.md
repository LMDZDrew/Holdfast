# Project instructions for Claude

Read `docs/decisions.md`, relevant sections of `docs/design.md`, and `docs/handoff.md` at the start of project work. Later accepted decisions override stale wording in the transcribed master document. Follow `AGENTS.md` for the shared engineering rules.

Before implementing a system, state its server-owned data, client display, saved state, interactions with existing mods, and how it will be tested. Do not silently choose a Minecraft version, Forge build, animation library, or mod version. The current platform and TaCZ are candidates pending a compatibility run. Lost Cities is excluded.

Work on an independent branch and leave a reviewable change. Record tested commands and actual outcomes in `docs/testing.md`, then write a concise `docs/handoff.md` entry for Codex and Andrew. A passing compile is not evidence that in-game animation, dedicated-server startup, or multiplayer persistence works.
