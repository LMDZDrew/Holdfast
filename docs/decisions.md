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

## Injury movement speed (accepted 2026-10-16, Andrew)

- Walking speed scales with injury severity, as a fraction of normal (vanilla) walking: mild leg injury 85%, severe leg injury 55%, injured arm 95%. Leg loss stays as in `design.md`: crawling 20%, improvised crutches 45%, medical crutches 60%, fitted prosthesis 80%.
- The server owns the multiplier (S13 in `animation.md`). Each injury walk cycle is authored so its footsteps match its speed at 1x playback.
- Which wounds count as mild or severe is not decided yet; it belongs to the wound system.

## Firearms and executions (accepted 2026-10-16, Andrew)

- TaCZ is the base mod for guns. Holdfast's gun-related animations (handgun executions, anything shown while a gun is held) must match TaCZ's own hold, aim and reload poses so the two read as one system. Exact TaCZ build still needs its compatibility run (roadmap Gate 0 step 2) before it is pinned.
- Executions: knife, axe, machete and bat as authored, each against upright, prone and crawling walkers where the pose allows. A handgun can execute; a rifle or shotgun cannot. The server's execution check (S7, weapon class) enforces it. "Handgun" means a TaCZ gun whose index `type` is `pistol` (14 in TaCZ 1.1.8's default pack, revolvers included); `rifle`, `shotgun`, `smg`, `sniper`, `mg` and `rpg` cannot execute (Claude's reading of Andrew's rule, 2026-10-16; revisit if SMGs should count).

## Animation and combat feedback expansion (accepted 2026-10-16, Andrew)

- Add sound effects and cosmetic blood spatter for confirmed hits and shots. The server decides hits, hit regions, wounds, and deaths; clients display the corresponding effects. Animation event markers remain presentation cues, never a source of gameplay outcomes.
- Walker vocalizations should closely match the raspy, strained, breathy character of walkers in AMC's *The Walking Dead*. Use original recordings/design or audio whose use is authorized; exact show recordings require rights clearance.
- Blood and flesh impacts need distinct, synchronized audio: blunt beating, blade slashing, and knife stabbing each sound different, with suitable variation to avoid one repeated sample. Gunshot impacts receive their own blood/flesh response. Play these only for the corresponding server-confirmed contact, separate from weapon swing or gunfire sounds.
- Add general player animations for crouching, sprinting, walking, and jumping. They must coexist with injury motion, equipment, and TaCZ's gun poses; firearm locomotion leaves TaCZ's arm, item, and aiming transforms in charge.
- Add distinct melee animation families for knives; two-handed bats and axes; swords and katanas; machetes; and one-handed hatchets. Match holds, attacks, and transitions to each weapon family rather than reusing a single generic melee pose. Exact clip list and visual style remain to be authored and tested.

## Stage 1 validation with one human tester (accepted 2026-10-16, Andrew)

- Finish the combat and presentation slice in `roadmap.md` before world generation. Andrew can perform solo visual checks on a dedicated server, but has no second human tester available now.
- Use automated server tests, scripted reconnect/state checks, and a second local client only if practical to cover multiplayer-sensitive code. Record what each method verifies and what remains untested. Human multiplayer acceptance is deferred until testers are available; it is not a prerequisite for beginning world generation.

## Explicitly deferred

Character skill leveling, friendly/recruitable survivors, dynamic dismemberment, broad stealth disguises beyond Whisperer masks, advanced structural physics, and guaranteed shader FPS. They can be revisited after a representative playable prototype.
