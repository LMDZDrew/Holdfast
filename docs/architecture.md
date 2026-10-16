# Architecture proposal (unverified)

## Authority boundaries

| Domain | Authoritative state | Client responsibility |
| --- | --- | --- |
| Needs, wounds, infection, limbs | Server character record and timestamped transitions | HUD, body GUI, prompts, predicted presentation |
| Walker perception and damage | Server AI, hit regions, last-known target, sound events | Audio/visual animation and interpolation |
| Grab/execution | Server start, time window, outcome, hit validation | Input capture, visible countdown, animation |
| Unique iconic items and landmarks | Persistent world registry with immutable IDs | Maps, clue display, item visuals |
| Death and reanimation | Server identity and world records | Spectator view and death presentation |
| Loot/vehicle/base state | Persistent world/chunk records | Inventory, interaction, damage feedback |

Use one canonical owner of each mechanic. Inventory, firearm, animation, health, and weather mods should be mapped to adapters only after evaluating their capabilities and licensing. Avoid maintaining two hunger meters, two conflicting firearm inventories, or client-only uniqueness.

## Save and disconnect invariants

- A unique item can have only one registry state and one physical instance across loaded chunks and restart.
- A player identity has exactly one living/dead state. Reconnect cannot create another character or duplicate a bag.
- A logged-out combat character, if implemented, persists for its defined interval; an empty server pauses the simulation. Crash recovery must load the last consistent save without treating backups as in-game resurrection.
- Discoverable landmark coordinates are reserved before a map points at them. Generated structures must not overwrite player construction.

## Platform gate

Java 1.20.1 Forge is the **current candidate**, not a lock. Verify an actual clean client and dedicated-server launch with chosen exact builds, TaCZ and each integration dependency. Record sources and licenses in `mods.md`. The custom core should only start after this gate; Gradle files are intentionally absent until a platform is selected.
