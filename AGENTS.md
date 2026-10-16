# Project instructions for Codex

Read `docs/decisions.md` before changing gameplay or choosing dependencies. Consult relevant parts of `docs/design.md`; the decision record wins over stale source-document wording. Read `docs/handoff.md` before resuming work.

Make implementation claims only after building and testing. Record the exact environment, command, result, and remaining risk in `docs/testing.md`. Pin versions only after checking official metadata and an actual compatibility run. Never imply that a candidate is selected solely because it has a matching Minecraft version.

Maintain server authority for health, bites, hit regions, unique equipment, death, and persistence. Client code renders and predicts feedback without authoritatively deciding outcomes. Keep overlapping mod mechanics from maintaining conflicting states.

Current immutable design choices include brain destruction for walker kills, reliable intact bite-rated regional protection, one penetrating bite causing infection, permadeath with no replacement character, original hostile survivors, available ordinary provisions, and no Nether, End, vanilla structures, or Lost Cities. See the full decision record for nuances.

Work in scoped branches. Do not edit another assistant's branch without coordinating. Review player-visible behavior, multiplayer persistence, and the design record when changing shared systems. Update `docs/handoff.md` with commit/branch, implemented behavior, tests, and open problems.
