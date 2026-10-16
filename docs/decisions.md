# Decision record

Status: accepted design unless labeled proposed. This file supersedes older wording in `design.md` and the original Word document.

## Identity and scope

- Faithful to AMC's *The Walking Dead* in atmosphere and recognizable remnants, with original playable survivors and hostile human communities. Spoilers are fine.
- Brutal but learnable survival. Provisions and ordinary ammunition remain reasonably obtainable; travel, hauling, human conflict, and securing a home supply the pressure.
- Southeastern-US-inspired, effectively endless world with widely varied rural distances, furnished towns, roads, and sparse inhabited communities.
- No Nether, End, vanilla structures, fantasy progression, or Lost Cities. TaCZ is a preferred candidate for firearms, not yet validated as an integrated dependency.
- Prioritize quality of mechanics and world design before performance optimization. Eventually test the 120 FPS aspiration on an RTX 5050 laptop with 16 GB system RAM, optional shaders separately, and a 16-player cap; no result is yet measured.

## Survival and combat

- Walkers die by brain destruction. Body impacts can stagger but cannot accumulate lethal walker damage. No special walker variants; prone, dormant, and lower-half crawlers are allowed.
- Charged blunt swings can deliver one accurate brain-killing hit. Short precise kill animations and readable grab-escape prompts; no routine button-mash QTE or animation invulnerability.
- One penetrating bite causes infection, not immediate death. Intact bite-rated pieces protect covered regions reliably, including the bite that breaks them. Ordinary jackets have no magical bite defense.
- Regional wound GUI, explicit illnesses, hunger/thirst/temperature HUD, self-amputation and permanent limb limitations, crutches/prosthetic progression. Infection duration varies by difficulty. Solo treatment must be possible.
- Healthy survivors can dispatch several walkers without implausible exhaustion. Weather and slow seasons start enabled in spring or summer. Night is survivable with lighting; sleep skip requires all connected living players.
- Weight plus volume capacity, visible bags, tools and scavenged materials for building, repairable equipment that remains at zero condition rather than despawning.

## World, people, and persistence

- Roads can be cleared; GPS shows position and saved waypoints, not hidden threats or loot. Structures can be barricaded but their interiors must not expose unfinished empty shells.
- Hostile human NPCs can warn, demand only carried items, retreat, or surrender. Friendly survivor recruitment is deferred. Human threats are strategic; hordes and walkers matter independently.
- Solo and up to 16 players, cooperative or rival. Character death is final with no replacement identity; remaining players continue. Last survivor death ends the run. Save the ended world as an archive, without a survival-resume route.
- No unavoidable timed raids. Security, materials, surveillance, and sightlines must reward real effort. Structural failure cracks and breaks blocks, with no direct collapse damage.

## Rare AMC remnants

- One unique instance per world for each iconic weapon and jacket, repairable at higher cost, never ordinary container loot. Unique world landmarks appear once, with maps as discovery aids, not quest gates.
- Clothing icons are Rick's season 5 brown jacket, Negan's jacket, and Daryl's winged vest, each comparably rare. No Michonne outfit or collectible outfit-completion system.
- Weapons include Rick's Python, red-handled machete and hatchet; Michonne's katana; Lucille; Daryl's crossbow; Carol's knife; Morgan's staff; Sasha's suppressed rifle; Tyreese's hammer; and Merle's bayonet attachment usable only after compatible amputation.
- The more accessible icons emphasize passive handling. Rarer examples receive triggered, powerful but physically plausible abilities. Whisperer masks are rare but repeatable, with slow-walk horde concealment and readable failure conditions.
- Rick's Python and jacket are separate discoveries in a longer, dangerous investigation. Other rare pieces need not each receive a full quest. Recognizable show-linked locations matter.

## Explicitly deferred

Character skill leveling, friendly/recruitable survivors, dynamic dismemberment, broad stealth disguises beyond Whisperer masks, advanced structural physics, and guaranteed shader FPS. They can be revisited after a representative playable prototype.
