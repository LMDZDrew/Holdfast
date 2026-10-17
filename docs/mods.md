# Mod evaluation ledger

No complete mod list is selected. Add an exact version only after checking the upstream release, license, distribution conditions, client/server behavior, and a clean launch. Record conflicts and what the custom core owns.

| Candidate | Intended role | State | Next verification |
| --- | --- | --- | --- |
| TaCZ | Firearm platform | Preferred candidate, not integration-tested | Exact loader/version, server startup, ammo/reload API, head-hit integration, license and packaging |
| Lost Cities | Procedural cities | Rejected by project owner | Do not add |
| GeckoLib 4.8.4 (MIT), branch 1.20.1 | Walker and crawler models and animations | Candidate; character exports statically match its loader source (see `docs/animation.md`) | Exact release jar and hash; clean client and dedicated-server load with our files; license note for distribution |
| Player Animator 1.0.2-rc1 (MIT), branch 1.20 | Player execution, grab, injury and first-person animation; held-item transforms | Candidate; exports statically match its loader and part semantics | Confirm a Forge 1.20.1 release artifact and hash; in-game load; interaction with TaCZ arm poses |
| Other needs/weather/animation/vehicle/performance mods | To be researched | Unselected | Compare capability against design, API extensibility, support, and conflicts |

Source pages and tested hashes should be recorded beside each selected artifact. Matching Minecraft and loader labels alone do not establish full-pack compatibility.
