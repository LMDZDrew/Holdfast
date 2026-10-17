# Roadmap

## Gate 0 — repeatable development environment

1. Verify intended Minecraft version and loader with upstream documentation.
2. Run a clean client and dedicated server using exact pinned builds and TaCZ alone.
3. Record logs, version list, launch commands, and observed result in `testing.md`; preserve license and distribution evidence in `mods.md`.
4. Create the minimal custom mod and a reproducible build only after that gate succeeds.

## Stage 1 exit gate — combat and presentation before world generation

1. Finish TaCZ arm ownership and Gate 0 step 2 with separately observed pistol and rifle hold, ADS, fire and reload, with and without Holdfast movement clips. Finish standing and suitable ground handgun executions and the pending melee visual re-tests.
2. Implement and test server-owned hit regions and brain kills, bite protection and infection, grabs and execution timing, injury speeds, and persistence. Animation and effects consume the server's results; cosmetic events never decide combat.
3. Add general walk, sprint, crouch and jump animations, then distinct knife, two-handed bat/axe, sword/katana, machete and hatchet animation families. Check transitions with empty hands, injuries, melee weapons and TaCZ firearms.
4. Add original or authorized walker vocals, differentiated weapon and blood/flesh impact sounds, and blood spatter for confirmed melee and gun hits. Verify timing and variation in game.
5. Accept the slice on one local client plus a dedicated server, with automated server tests and scripted reconnect/state checks. If a second local client is practical, use it for synchronization checks. Human multiplayer and 16-player load testing follow when testers are available; they do not block the start of world generation. Record the exact untested multiplayer risk in `testing.md`.

## Milestone 1 — representative rural survival loop

One starter shelter, one connected rural town with purposeful interiors, ordinary scavenging, head-only walker kills and grabs, regional bite protection/infection, needs HUD, a vehicle or defensible supply route, and one working hostile survivor interaction. Tyreese's hammer is the first representative rare reward. Test a solo day and night, dedicated-server restart and persistence; use an automated or second local client for multiplayer checks when practical, then repeat with human testers when available.

## Milestone 2 — base and persistence

Weight and bag access, construction support, route clearing, walls and horde pressure, utilities, food/water sustainability, and unique-item/death persistence. Test hostile contact near a developed compound.

## Milestone 3 — landmark and scale

Prison landmark as a full content test, map discovery and once-per-world registration, then remaining landmarks and icons. Expand multiplayer and optimization tests up to 16 players. Measure FPS and TPS only with documented hardware, settings, and scenarios.

Keep milestone completion tied to observed in-game behavior, not task count or compilation alone.
