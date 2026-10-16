# Master design transcription

> Source: October 16, 2026, version 1.1 Word document. This is a transcription, not implemented functionality. **Read `decisions.md` first:** later instructions reject Lost Cities, which remains mentioned as a candidate in this original document.

Version 1.1 • October 16 2026 • Project owner Andrew

Build a faithful AMC-inspired survival experience in a block-based world. Survival is brutal but learnable: provisions are obtainable, threats obey understandable rules, and a secure, sustainable home is the ultimate achievement.

This document establishes the design baseline for selecting existing mods, identifying custom development, and producing a representative playable prototype. Confirmed scope describes intended behavior, not completed or verified implementation.


### Design priorities

- Human opponents are the greatest strategic danger; walkers and hordes remain a constant physical threat.
- Exploration rewards preparation. Ordinary supplies are reasonably available, but retrieving and transporting them can be dangerous.
- World generation is a central feature: believable towns, furnished buildings, connected roads, rural distances, and recognizable landmarks.
- Permanent death gives decisions weight. Clear feedback and consistent rules prevent arbitrary losses.
- Use high-quality existing mods wherever they fit. Integrate custom systems when necessary, preserving a cohesive identity.

### How to read this document

Confirmed means accepted during planning. Proposed means an implementation hypothesis or tuning candidate. Sections 10 to 14 resolve earlier open design choices under delegated design authority; their prototype defaults govern where wording differs. Deferred means outside the initial release. Open means a design or technical question that must be resolved before implementation can be promised.


### Document map

2 World and exploration • 3 Combat and walkers • 4 Health and survival • 5 Equipment and progression • 6 Homes and utilities • 7 Humans and multiplayer • 8 Iconic equipment • 9 Landmarks and discovery • 10 Technical direction and validation • 11 Survival and combat defaults • 12 Persistence and multiplayer • 13 Systems and ability tuning • 14 World architecture and content


## 2 World and exploration


### Confirmed world direction

An effectively endless world evokes the southeastern United States well into the collapse. Fictional American small towns, scattered properties, forests, rural roads, and rare dense urban centers dominate. Functioning civilization and substantial occupied compounds are exceptionally scarce.

Town spacing varies naturally. Some journeys are short; others require overnight supplies or a vehicle. Roadside buildings, wrecks, camps, and other discoveries make the countryside meaningful without guaranteeing a nearby safe destination.

Roads connect destinations through highways, smaller roads, streets, tracks, and driveways. Wrecks and damaged routes can force detours. Clearing a route provides a lasting improvement for later supply runs.


### Buildings and environmental storytelling

Accessible buildings have furnished, purposeful interiors. Some entrances or sections are blocked or barricaded. Breaking into a building must not reveal an unfinished empty shell; inaccessible sections can instead contain designed rubble or collapsed rooms.

Mostly low or moderate overgrowth, occasional heavy reclamation, and some largely intact towns create variation. Visual storytelling communicates failed shelters, evacuation, and abandoned lives. Written clues are deliberate, particularly around iconic discoveries.

Ordinary public utilities have failed. Very rare buildings retain working power or water, with an identifiable local system. Most vanilla elements and all vanilla structures are to be removed or replaced; the Nether and End progression is removed entirely. Retained vanilla systems are defined in section 14.


### Navigation and starting conditions

Regional maps, signs, a compass, and a personal exploration map support navigation. A working GPS shows position and saved destinations, not enemies or hidden loot. No live enemy minimap is planned.

Start as an existing survivor with ordinary clothing, a modest bag, a basic melee weapon, limited provisions, and a plausible temporary shelter. Its long-term defensibility is not guaranteed. Ground sticks and stones provide an emergency tool-making fallback.


### Scavenging and transportation

Provisions are distributed unevenly: accessible stores can have useful leftovers, while sealed warehouses reward substantial effort. Ordinary weapons are somewhat harder to find than provisions; iconic equipment is far rarer. Looted containers and drained fuel sources do not refill.

Furniture, doors, fencing, appliances, and materials can be dismantled with suitable tools, time, and carrying space. Vehicles have fuel, battery, tires, and engine condition; keys or manual-taught hotwiring permit access. Repairs, towing, or returning with parts can recover breakdowns.

Cars, trucks, and motorcycles have different storage and handling roles. Cargo respects weight and capacity. Collisions can injure occupants and damage vehicles; minor bumps remain forgiving. Cabins protect occupants until openings are compromised. Hordes can slow and damage vehicles. Bikes and handcarts deteriorate but remain repairable and useful after acquiring a car.


## 3 Combat and walkers


### Confirmed walker rules

Ordinary walkers move slowly, with variation in pace and aggression. Include crawlers missing their lower halves, prone walkers, and initially motionless walkers. No special variants initially. Predefined crawlers do not require dynamic dismemberment.

Brain destruction is required for a kill. Body hits do not accumulate lethal damage; impacts can create stagger or space. Groans and other cues help reveal threats, but silence never guarantees safety.

Sight and sound drive detection. A walker fixates while it can detect a target, follows the last known position after losing sight, searches nearby, and eventually disengages without new evidence. Nearby walkers respond to disturbances rather than inheriting magical knowledge.

Distractions work best on wandering walkers or after breaking sight. A bottle breaking nearby does not automatically distract a walker actively watching its target. Sound is attenuated by distance and obstructions. Crouching reduces noise but does not hide someone in plain sight.


### Melee and grabs

Precise, correctly spaced head attacks trigger short first-person executions. Ordinary kills do not require repeated quick-time sequences. Weapon reach and handling matter: knives require proximity, axes commit to slower swings, and blunt weapons can need several hits.

Blunt swings can be charged up to a clear maximum. Full charge can deliver a one-hit brain kill with a suitable weapon and accurate placement. Charging slows movement; cancellation is possible before swinging. Misses expose the player through recovery time. Charged body hits still cannot kill walkers.

Execution animations do not grant invulnerability. Players can cancel before the committed finishing motion. Grabs provide a brief, clearly displayed timed escape prompt rather than button mashing. A successful escape shoves the walker away; a counterattack requires a separate action. Teammates can intervene before a bite.


### Protection and gunplay

A confirmed penetrating bite causes infection, not instant death. Bite-rated protection reliably protects its covered region while intact. The bite that breaks protection is blocked; the next bite can penetrate. Blocked bites do not automatically release a grab. Deterioration and failure have clear warnings.

Bite and ballistic protection are separate properties. Bullet protection depends on region and a small number of readable tiers; the guaranteed final blocked bite rule does not automatically apply to gunfire.

Guns use physical magazines, ammunition types, reloading, and recoil without excessive maintenance chores. Ammunition accompanies ordinary weapons, but sustained firefights consume significant resources. Exposed headshots are intended to be lethal in ordinary circumstances; serious torso wounds cause bleeding and impairment. Section 11 defines initial damage rules; implementation still requires testing.


### Hordes and pressure

Noise-driven gatherings coexist with occasional migrating hordes. Groups can converge and cross quiet areas without deliberately targeting a player. Stronger materials resist pressure better; weak barriers can fail under crowds. Threats give observable approach and damage cues. No scheduled blood moons, unavoidable raids, or spawning inside secured bases. A horde can pass when nothing holds its attention.


## 4 Health and survival


### Needs and exertion

One hunger percentage; meal quality affects satisfaction and recovery without vitamin or body-weight tracking. Thirst responds to heat and exertion. Water has weight. Simple boiling or suitable filtration treats appropriate sources; chemically contaminated water is not made safe by boiling alone under the game rules.

Short-term stamina recovers relatively quickly; fatigue reflects the need for sleep. Healthy players can walk and kill several isolated walkers without implausible exhaustion. Sprinting, heavy repeated swings, struggles, excessive loads, and neglected needs compound exertion.

Fresh food spoils with generous expedition-friendly windows. Abandoned loot reflects the late-collapse setting and is mainly preserved food, not fresh meat that has inexplicably survived for years. Farming, hunting, fishing, drying, smoking, canning, and refrigeration support long-term survival.


### Injuries and infection

The survivor interface maps bites, cuts, and other injuries to body regions, alongside illness and needs. Arm injuries impair handling; leg injuries impair movement; serious torso injuries reduce exertion. Treatment controls bleeding, cleans and dresses wounds, and allows recovery. Bandages do not instantly restore health.

Common wounds recover relatively quickly with good care; fractures persist longer. Splints and necessary self-treatment support solo survival. Pain relief can reduce penalties without curing the injury. Food, hydration, and rest support recovery. Diagnoses are explicit, including ordinary wound infection and a small set of distinct illnesses.

Ordinary wound infection is clearly distinguished from walker-bite progression. Generic scratches do not randomly produce the bite outcome. Bite progression is irreversible without eligible timely limb amputation; difficulty controls its duration. No miracle cure.

Self-amputation is possible through the survivor system. Losing a hand or forearm limits ordinary two-handed equipment. Losing a leg initially permits extremely slow movement or crawling, followed by improvised crutches, better crutches, and a prosthetic. Emergency crafting must remain usable with the player’s remaining capabilities. Permanent limitations remain; initial timings and restrictions are defined in section 11.


### Incapacitation and death

Some survivable injuries permit a brief incapacitated state. Accessible supplies and injury severity determine whether self-stabilization is possible. Teammates can stabilize and move an injured player. Bleeding and exposure remain dangerous; fatal injuries do not gain a guaranteed rescue window. Stabilization does not cure a bite. Confirmed death is permanent.


### Weather and sleep

Long day/night cycles and slowly changing seasons begin in spring or summer. Variable weather is enabled immediately. Temperature, wet clothing, shelter, and exertion affect comfort and survival. Nights require lighting but remain survivable; walkers do not gain arbitrary nighttime powers. Fog and rain affect perception consistently.

Sleeping bags provide basic recovery and permit participation in skipping the night. Proper beds in sheltered homes improve recovery. All living connected players must sleep to advance night; rest can still help while others remain awake. Sleep does not replace emergency treatment or reverse bites and amputations.


## 5 Equipment and progression


### Inventory and handling

Carry limits use weight plus storage capacity. Bags add space without making items weightless. Visible bags have access animations; opening a backpack prevents attacking, but access can be cancelled quickly. Limited configurable quick-access slots hold equipped weapons, lights, and selected supplies.

Accessible bandages can be used faster than packed supplies. Excess load gradually slows movement and increases exertion, with a clear maximum and no surprise immobilization from a single pickup. A quick bag-drop action permits emergency escape and later retrieval.


### Maintenance

All equipment remains repairable at zero condition. Ordinary wear does not delete it. Suitable materials permit limited field repairs; tools and workbenches permit full restoration. Protective pieces primarily deteriorate through attacks, not merely being worn.

Iconic weapons and clothing cost more to repair through suitable materials, tools, and time. Their required materials remain obtainable. Full bonuses remain effective until breaking; zero condition disables use and bonuses until repaired. Iconic items do not despawn, but fire can permanently destroy them. Section 12 defines ordinary-item cleanup and persistent containers.


### Crafting and knowledge

Essential recipes are known immediately, including improvised tools, simple bandages, splints, and emergency crutches. Manuals or books teach advanced electrical work, fuel production, specialized repairs, and hotwiring. Knowledge does not replace tools, materials, or facilities.

No initial character skill leveling or repetitive crafting grind. Progress comes from player experience, instructions, equipment, and infrastructure. Found equipment and salvage often provide practical alternatives to manufacturing from raw materials.


### Survivor interface

A mostly monochrome interface establishes the pack’s identity. Compact health, stamina, hunger, and thirst indicators coexist with an always-visible temperature gauge and a minimal personal noise indicator. Detailed percentages and localized conditions appear inside the survivor screen; at-a-glance warnings use plain language.

The proposed temperature gauge communicates the survivor’s thermal condition, with ambient temperature and contributing factors in the detailed screen. The HUD implementation defaults are defined in section 12. Icons, text, and sound communicate critical conditions without relying on color alone.

Inventory, treatment, maps, repair screens, and combat prompts should share one visual system. Off-white and charcoal with restrained warning colors are the proposed palette. Section 12 establishes initial layout and accessibility decisions.


### Fairness and controls

Solo pause menus freeze the world; inventory and treatment do not. Multiplayer menus remain live. Closing menus immediately restores control. Combat prompts are readable and rebindable, using timed responses rather than rapid button mashing. Bites, armor failure, bleeding, and dangerous temperature receive visual and audio warnings.


## 6 Homes and utilities


### Construction and security

Tools are required; punching trees hurts. Scavenging, hauling, and construction labor make a permanent compound an achievement. Proposed support rules use foundations, walls, posts, beams, and limited spans. A grounded freestanding wall counts as supported.

Unsupported sections visibly crack and gradually break like ordinary Minecraft blocks. Poor support reduces resistance to attacks. Structural collapse does not directly injure players. Falls directly caused by support-system collapse cause no damage; ordinary falls remain dangerous.

Security comes from walls, surveillance, concealment, clear sightlines, gates, and maintenance. A better defended home offers genuine comfort. There is no automatic invulnerable claim zone. Locks can be breached with suitable tools and time; curtains and boards conceal interiors at a visibility cost.

Cameras require power and display real views, not enemies through walls. Tripwire alarms warn but can attract walkers. Initial defensive traps are small in scope, visible, avoidable, and manually disarmable. Proposed examples are noise tripwires, barbed-wire obstacles, and stake barriers. Body impalement alone does not kill walkers. Traps can hurt their owner and teammates.


### Sustainable supplies

Seasonal crops use manageable watering and multi-day growth. Hunting and fishing supplement scavenging; gunfire while hunting carries normal consequences. Preserving food supports low-tech homes. A modest garden supports fewer people than a large group, which needs additional space and work.

Rain collection provides renewable water requiring treatment. Wells need meaningful construction or restoration. Refrigerators, lights, pumps, and chargers make utilities valuable without being mandatory for basic sustainability.


### Power and fuel

Generators require fuel and occasional repairs, and produce detectable noise. Solar requires panels, batteries, and connections but offers quiet operation. Wiring is understandable rather than an electrical-engineering simulation. Portable batteries support lights and GPS; rechargeable batteries benefit from home power.

Fuel remains obtainable through new exploration. Previously drained vehicles, stations, and stores stay empty. Supply runs should return meaningfully more fuel than they consume. A demanding late-game production option supplies an alternative compatible with selected engines; section 13 defines the initial fuel system.


### Fire and threats

Fire spreads gradually through flammable materials with visible warnings and practical extinguishing options. Correctly used campfires and stoves are manageable. Fire can destroy stored supplies and iconic equipment. Burning walkers remain dangerous until brain destruction. No spontaneous unexplained base fires.

Human scouts can discover a base through observation, leave, and potentially return with others. Routes, doors, and weaknesses matter. The intended system offers clues and counterplay rather than a raid timer with perfect knowledge. Security reduces risk but does not eliminate it.


## 7 Humans and multiplayer


### Human encounters

NPC survivors are hostile in the initial release, but not every encounter begins with shooting. Territorial groups can warn, robbers can demand goods, scavengers can compete, and ambushers can attack from prepared positions. Retreat and compliance are legitimate options.

Robbery demands are limited to supplies the player actually carries. The interaction shows the demand and allows time to choose. Complying ordinarily permits departure; random betrayal during the interaction is excluded initially. Eligibility checks must not give NPCs unrestricted tactical knowledge of hidden inventory.

Outmatched or wounded NPCs can retreat or surrender. Surrender is clearly signaled; players can disarm them and let them flee. No recruitment or prisoner management initially. No random attack during the surrender interaction.

Enemies carry recoverable weapons, armor, and remaining ammunition matching their actual equipment. They reload and have finite ammunition; low supplies can change behavior. Damage affects the equipment actually struck. Accuracy is imperfect, positioning matters, and enemies do not absorb arbitrary magazines of damage.

Walkers target NPCs too. NPC gunfire attracts threats, and NPCs can shift attention from the player to immediate walker danger. Human deaths without brain destruction can reanimate. Players may lure walkers toward hostile positions without controlling the aftermath.


### Multiplayer rules

Hard cap of 16 players. Cooperation and rivalry are both allowed, including shared compounds or separate bases. Friendly fire is enabled. Keys and permissions can be shared without mandatory formal teams.

Death permanently removes that player’s character from the world. No respawn or replacement character; spectating is allowed. Remaining players can continue and recover equipment. The playable run ends when no survivors remain. Solo death ends that world’s playable run. Ended worlds are archived for viewing; their survival run cannot resume. Files are not automatically deleted.

Dead players without brain destruction can reanimate with equipment. Remaining players may need to confront the reanimated survivor to retrieve belongings. Section 12 defines account, spectator, and disconnect rules.

Logout uses a short countdown. Combat disconnects briefly leave the character exposed to prevent instant escape; timings and crash handling require testing. Unattended bases remain vulnerable while others are online. The world pauses when the server is empty, requiring timers and simulations to respect the same rule.


### Difficulty and goals

Tentative plan: named presets plus individual settings, with the intended experience as default. Permanent death, head destruction, dependable protection, and no loot refill remain core rules. Harder settings shorten bite progression and may increase threats, tighten readable escape windows, and increase neglected-needs penalties. Do not inflate enemy health or make ordinary provisions universally scarce.

Survival is open-ended. An optional journal recognizes milestones such as securing water, restoring power, preserving food, clearing routes, and fortifying a compound. There is no mandatory victory ending.


## 8 Iconic equipment


### Confirmed roster and rarity

Every named iconic weapon and jacket is unique per world. More accessible icons rely on exceptional passive handling; the rarest add a powerful but physically plausible activated ability. One selected active ability at a time applies across weapons and clothing. Section 13 defines initial effects and cooldowns.

Merle’s attachment is usable only after a compatible hand or forearm amputation. It provides a weapon rather than restoring ordinary hand function. A weaker craftable weapon prosthesis is included so recovery does not depend on the unique item.


### Signature clothing replaces full sets

Only Rick’s brown season 5 jacket, Negan’s jacket, and Daryl’s winged vest are collectible clothing icons. All are equally rare with comparable acquisition effort. There are no separately collected iconic pants, boots, or accessories and no outfit-completion requirement. Michonne’s outfit was removed; her katana remains.

All three ability identities are selected for the prototype; numerical balance remains subject to testing. Lucille’s proposed activation accelerates reaching full charge, distinguishing it from the jacket’s follow-through emphasis. No ability bypasses brain destruction or exposed-region bite vulnerability. Ordinary compatible clothing and protective pads can be combined with the signature piece.


### Whisperer masks

Masks are rare but repeatable, allowing several players to obtain them. They enable horde concealment and connect to forest camps. Proposed behavior requires slow movement; sprinting, attacking, or firing breaks concealment. Equipping a mask does not instantly erase active pursuit. A clear status indicator explains concealment. Masks do not inherently grant bite protection. These disguise rules are selected for the prototype and require implementation testing.


## 9 Landmarks and discovery


### Confirmed landmark roster

All eight are intended to appear once per world, substantially separated, and closely reflect their appearance in the show. The working baseline is their most recognizable intact state, combining moments rather than enforcing one exact timeline. Section 14 selects landmark eras; exact visual measurements require reference research.

Original hostile survivors and walkers can occupy landmarks without replacing their recognizable architecture. Players can clear, repair, and live in any landmark. Secured areas do not refill with artificial spawns; new threats must physically enter. Solo survivors can secure one portion before expanding.


### Unique rewards and maps

Landmarks and rewards already exist conceptually regardless of whether the player obtains a map. Maps reveal a clear destination, not an enormous vague search radius. Multiple map copies can point to the same unique landmark. Direct discovery remains possible; no mandatory quest gate prevents retrieval.

Do not organize all unique content into an easy ring around spawn. Variable, potentially demanding journeys should reward preparation and vehicles. Section 14 specifies a placement architecture that must be proven in a prototype.

Rick’s Python and jacket each require a longer investigation followed by dangerous retrieval, with separate stories. The other jackets are equally rare and comparably difficult to obtain. Clues should create meaningful progress instead of repeated low-chance drawer searches.

Other icons can be placed in difficult environments with brief contextual clues, without individual multi-stage stories. Connections to the characters and show remain recognizable. Original connective details must not be presented as verified canon. Section 14 specifies initial item placements as original game stories with recognizable connections.


### First playable milestone

Build one polished rural area with a small town, connecting roads, a temporary shelter, a meaningful scavenging destination, and a representative acquisition encounter. Rick’s jacket and Python are not introductory rewards. Tyreese’s hammer is the first prototype reward, placed in a dangerous roadside survivor outpost.

The milestone should demonstrate furnished interiors, combat and grabs, protective coverage, survival needs, hauling, and defensible construction. It establishes the quality standard before expanding the landmark catalog and procedural world.


## 10 Technical direction and validation

Design ownership is delegated to the project assistant. The following choices are adopted prototype defaults, not measured outcomes. Andrew’s explicit requirements retain priority. Values can be tuned after playtesting without asking for approval for every routine adjustment.


### Selected prototype platform

Use Minecraft Java 1.20.1 with Forge as the provisional prototype platform. Evaluate TaCZ as the first firearm candidate and The Lost Cities as a configurable urban-generation candidate. Their project pages list 1.20.1 Forge support. This supports evaluating the platform, not claiming the full pack is compatible. No dependency binaries or exact versions are pinned yet.

Lost Cities is a candidate for configured urban regions, not a promise that its default generation supplies our rural roads, furnished small towns, or unique landmarks. Use custom assets and a world coordinator where necessary. No military-vehicle content pack is automatically selected merely because it has vehicles.

One custom core should own survival state, regional damage, grabs, unique items, knowledge, and world events. Existing mods can provide assets and supporting systems through adapters. Avoid two independently authoritative health or combat systems. Keep combat, loot, deaths, and placement server-authoritative.


### Performance baseline and gates

Benchmark at 1920 by 1080, shaders off, 12-chunk render distance and 8-chunk simulation distance, with the full intended gameplay rules. Target at least 120 FPS average and a provisional 80 FPS 1 percent low. A shader profile is optional and separately measured; no shader FPS guarantee is made. Begin with an 8 GB client allocation on the 16 GB laptop and measure memory pressure rather than assuming this is optimal.

Test dedicated-server scenarios separately from client rendering, including 16 players in one town and 16 players exploring separate regions. Target 20 ticks per second and record tick duration against the 50 ms budget. Server hardware selection follows measurements; do not promise 16-player hosting on the laptop.

Required prototype gates: correct head and armor hits; grabs that preserve agency; save and reload without duplicated unique items; connected roads and furnished interiors; stable landmark maps; finite-ammunition NPC behavior; empty-server pausing; and acceptable frame times under representative hordes. A failed gate triggers implementation work or a platform review, not silent removal of core mechanics.


### Deferred scope and evidence

Friendly survivors, character skill leveling, livestock, dynamic dismemberment, and general camouflage beyond masks remain deferred. Keyboard and mouse are the initial supported control scheme; controller support is a later integration target. Detailed mod selection, licenses, asset permissions, and compatibility require further research before distribution.

Project sources checked October 16 2026:

TaCZ project and supported platform: https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero

Lost Cities customization and version documentation: https://www.curseforge.com/minecraft/mc-mods/the-lost-cities

Lost Cities 1.20.1 files: https://www.curseforge.com/minecraft/mc-mods/the-lost-cities/files/all?page=1&version=1.20.1


## 11 Survival and combat defaults

All figures in this section are selected game-balance values, not medical claims or real-world survival estimates. Game hours mean simulated world time; real seconds and minutes mean unpaused simulation time. Bite timers use real elapsed simulation time and cannot be bypassed by sleeping.


### Time and needs

One day lasts 90 real minutes: start with 60 minutes of daylight and 30 of night, varying by season while preserving the total. Each season lasts 24 in-game days, producing a 96-day year. Default start is early spring; summer is the alternate start option. Regional weather favors warmth, with occasional cold snaps and rare snow.

At rest, hunger falls by 3 percentage points per game hour and thirst by 4.5. Exertion raises those rates up to 50 percent; heat adds up to 50 percent to thirst. Warnings begin at 30 percent, significant penalties at 15 percent, and damage only after sustained depletion. Simple meals restore about 30 hunger points and snacks 10. These are recipe-balance targets, not nutritional quantities.

A healthy player can sprint for roughly 45 real seconds before emptying stamina; a light melee strike costs 3 percent, a heavy strike 8 percent, and a shove 5 percent. Recovery begins after 2 seconds without demanding exertion and restores 10 percent per second. Injuries and deprivation lower recovery. Fatigue warnings begin after 16 game hours awake; there is no forced blackout while fighting.

Eight game hours in a bed restore fatigue fully; a bag provides 80 percent of that benefit. A safe night skip advances crops, fuel, spoilage, and healing consistently. It is refused while anyone has an active bite countdown, uncontrolled bleeding, incapacitation, or immediate danger. Sleep cannot make fatal conditions disappear during a time jump.


### Injuries and bites

Default bite decline lasts 60 real minutes; lower-pressure mode uses 120 and severe mode 30. Eligible amputation must begin within 5, 10, or 3 real minutes respectively, with the same short procedure duration across presets. Head, neck, and torso bites cannot be amputated. The UI explicitly marks eligibility and remaining time.

Amputation is a game interaction requiring an appropriate cutting tool and accessible bleeding-control supplies. It leaves a severe wound requiring stabilization. Leg loss permits crawling at 20 percent normal walking speed, improvised crutches at 45 percent, medical crutches at 60 percent, and a fitted prosthesis at 80 percent. No ordinary sprinting with a leg prosthesis initially. One-handed use, crafting, and container access must remain possible.

Well-treated minor cuts heal within one game day, moderate wounds in two to three, and fractures in five to seven. Untreated bleeding prevents ordinary recovery. First release illnesses are foodborne illness, flu-like illness, and ordinary wound infection, each explicitly diagnosed. Illness treatments remain simplified game items rather than real medication dosing.


### Combat response

Default grab response is 1.2 seconds, lower-pressure 1.6, severe 0.9. Use a consistent context key and clear indicator, not randomized keys. An exposed brain hit from a suitable projectile is fatal; covered hits consult ballistic class. A blunt head hit uses impact thresholds. Torso damage can incapacitate or kill through trauma and bleeding, without arbitrary bullet-sponge health.

A nonfatal incapacitation grants up to 60 real seconds to stabilize, shortened by additional injury. No repeated down-state reset restores health. Generic scratches never roll for walker-bite progression. Normal fall damage remains, except the first landing directly caused by support-system collapse, which is exempt to preserve the no-collapse-injury requirement.


## 12 Persistence and multiplayer decisions


### Unique items and storage

Unique means one per world, shared across all players. No personal copies, respawn replacement, or multiplayer duplication. Fire destruction is permanent. Repair does not recreate a burned item. Unique records track unclaimed, claimed, dropped, stored, and destroyed states; ownership changes and inventory transfers are server-authoritative.

Maps remain physical records and do not magically update when someone takes a reward. A locally discovered search-complete marker can be added to the journal. Finding an old map may lead to an already-looted location; environmental evidence should make that outcome understandable.

Dropped bags, death containers, and unique items never expire through time. Ordinary loose items expire after 30 minutes of loaded simulation time. Nonessential small debris expires after 5 minutes. Container contents persist. Empty corpses can be cleaned up after three game days; loot-bearing bodies become persistent searchable remains rather than deleting possessions. Fire can still destroy exposed containers and their contents.


### Accounts and disconnections

The first server cohort begins the run; genuinely new players may join while at least one survivor lives, up to 16 concurrent players. A dead player identity can never start a replacement character in that world. New players receive ordinary starting equipment in a safe peripheral spawn, with no teleport to teammates or duplicated unique loot. Deliberate alternate-account evasion is an administrative issue; do not claim perfect detection.

Spectators cannot interact, scout unrestricted terrain, or keep the simulation running. Restrict them to following a consenting living player. No free-flight enemy reconnaissance. When no living characters or pending disconnect bodies remain, pause the world even if spectators are connected.

Safe logout requires 10 seconds without a new attack or damage; a combat disconnect leaves the existing body present for up to 30 seconds. It can be injured or killed. Reconnection resumes that same body and state. A disconnected player is not cloned. Client crashes follow the same rule because intent cannot be reliably inferred.

When the final exposed disconnect body is resolved, the empty-server pause freezes needs, enemies, fire, fuel, crops, and deadlines. A full server crash resumes from the last consistent save without charging offline time. Keep rolling recovery backups, but no player-facing undo-death action. Technical recovery is for verified faults, not ordinary losses.


### HUD and access

Use compact lower-left health and needs, temperature beside needs, a faint noise meter near the reticle, and the quick bar centered below. Fatigue and injuries appear contextually. The survivor screen provides percentages, regional wounds, coverage, condition, and treatment actions. Color is supplementary to text and shapes.

Provide rebindable controls, adjustable HUD scale, subtitle cues, hold-or-toggle settings, reduced camera motion, and a longer response-window accessibility option. Custom settings are recorded on the world, not presented as cheating. Critical cues must be tested at 1080p. Backpack opening takes 0.7 seconds; cancellation restores control immediately. Use six configurable quick slots initially.


## 13 Systems and ability tuning


### Armor and repairs

Use three fictional game ballistic classes: none, handgun-rated, and rifle-rated. Display explicit cartridge compatibility; do not imply a real certification. Shot location and protection condition determine penetration consistently. Only bite protection receives the guaranteed final blocked attack rule. Passive clothing benefits never create ballistic resistance.

Field repairs restore equipment up to 60 percent; workbench repairs can reach 100 percent. A common metal weapon repair uses scrap and a tool kit; an iconic equivalent costs three times the materials and twice the action time. Clothing uses fabric or leather with sewing supplies; firearms require mechanical parts. Ingredient quantities scale with condition lost. Every ingredient has repeatable exploration or crafting sources.


### Power and fuel

Use gasoline for ordinary vehicles and selected generators; late-game biodiesel powers only explicitly labeled compatible diesel vehicles and generators. Implement a simplified processing station using farmed oil crops, water, reusable processing equipment, and power. Do not provide real chemical manufacturing procedures. Output is a game balance recipe.

Prototype recipe: ten oil-crop units plus one water container and twenty real minutes of powered processing produce one fuel unit. A generator uses one unit per twenty real minutes at rated load; idling uses less. Standard vehicle travel uses approximately one unit per 3000 blocks on roads. Exact engine classes receive their own consumption multipliers.

Fresh food lasts three game days at ambient temperature, twelve refrigerated; smoked or dried food lasts twenty-four, canned food ninety-six. Sealed scavenged shelf-stable items begin in usable condition with long remaining life. Power loss resumes ambient spoilage without instantly spoiling everything.


### Noise and threats

Initial unobstructed detection radii: walking 4 blocks, sprinting 12, broken glass 24, generator 48, suppressed shot 64, and unsuppressed shot 160. These are game tuning values. Solid barriers reduce sound energy; suppressors never make guns silent. Walkers pursue the last sighting and search for 90 seconds without fresh evidence before wandering.

Hordes and patrols use regional records beyond loaded areas. They materialize only at unobserved valid approaches, never inside secured spaces or in the player’s view. Scouts must observe an occupied site, escape, and reach their group before enabling a return. A quiet base is not guaranteed a raid. Patrol budgets and migrations are spatial events, not player-centric timers.


### Iconic active abilities

Use a shared 180-second cooldown across all equipped iconic activations. Changing the selected ability takes 5 seconds out of combat and never resets cooldown. Activation requires the corresponding item equipped; removing or breaking it ends the effect. No kill healing, time slowing, invulnerability, or automatic targeting.

Rick’s jacket: Brutality lasts 12 seconds, reducing committed melee recovery by 25 percent and improving shove force by 30 percent. Negan’s jacket: Heavy Hitter lasts 12 seconds, reducing heavy-swing recovery by 30 percent and improving impact by 25 percent. Daryl’s vest: Hunter’s Focus lasts 15 seconds, reducing aim sway by 40 percent and handling time by 20 percent.

Python: 10 seconds of 40 percent lower aim sway and 20 percent shorter follow-up delay. Machete: 12 seconds of 30 percent faster recovery after a successful head kill. Katana: 12 seconds of 35 percent lower melee stamina cost and 20 percent faster recovery. Lucille: 12 seconds of 40 percent shorter charge time. Crossbow: 15 seconds of 30 percent faster reload and 30 percent lower sway. Passive-focused icons gain roughly 10 to 15 percent handling advantages; all values are initial tuning, not promised balance.

Whisperer concealment requires the mask, walking or slower, and 3 seconds outside confirmed pursuit to establish. Sprinting, attacking, or shooting breaks it immediately; no automatic grab escape. Masks lose condition slowly while used in concealment, and a broken mask provides none. Rain alone does not abruptly cancel disguise in this first implementation.


## 14 World architecture and content decisions


### Procedural geography

Use deterministic region planning: plan terrain suitability and connected roads before buildings. Select handcrafted furnished templates for rural properties and towns. Town centers initially vary around 1200 to 4500 road blocks apart, with occasional longer gaps; roadside discoveries occur irregularly rather than at guaranteed intervals. The distribution is a tuning target, not a performance shortcut.

Assign unique landmarks before maps are issued through a world registry. Evaluate terrain candidates in a broad 64000 by 64000 block world-coordinate region, with at least 5000 blocks between major landmarks. Coordinates are not a ring around player spawn. The wilderness and ordinary settlements continue beyond that region. Choose the starting shelter separately, at least 3000 blocks from major iconic destinations.

This bounded placement domain guarantees a finite search target while avoiding endless random searching; it does not impose a playable world border. Terrain sampling, coast placement, roads, and actual distances must pass seed tests. Reserve sites and entrances before generation; never overwrite player constructions. Failed candidate validation moves the reservation before any corresponding map is distributed.


### Landmark eras and loot assignments

Select the prison before its major destruction, Hershel’s farm before evacuation, Woodbury in its established fortified state, Terminus before its destruction, Alexandria before major devastation, Hilltop in its intact early form, the Sanctuary in its established industrial form, and Oceanside as an intact concealed settlement. These are art-reference selections, not a shared canonical date. Verify layouts and episode references during asset production.

Initial reward placements are original game aftermath stories, not claims that these objects were left there in the show: Python in an Atlanta sheriff evacuation evidence cache; Rick’s jacket in a Terminus railway escape cache; Negan’s jacket and Lucille in separately secured Sanctuary areas; Daryl’s vest and crossbow in separate prison expedition caches; Michonne’s katana in a concealed woodland shelter tied to prison routes.

Place Rick’s machete at an abandoned church connected through railway clues; his hatchet in an Alexandria salvage cache; Carol’s knife at a concealed roadside cottage; Morgan’s staff at an isolated woodland cabin; Sasha’s rifle at a fortified lookout overlooking industrial approaches; Merle’s attachment in a Woodbury-linked medical salvage cache; and Tyreese’s hammer in the roadside outpost used for the first playable milestone. Specific scenes and props will be referenced before final art is approved.

Python and jacket each use three deliberate leads ending in an exact map and difficult retrieval. Negan’s jacket and Daryl’s vest receive equivalent total investigation and encounter effort. Other rewards vary between a dangerous direct discovery and one or two leads. Named weapons do not appear in generic loot tables. All maps to a given landmark reuse its registry identity.


### Retained systems and construction

Retain block building, terrain, ordinary animals, crops, water, weather, useful basic materials, and an adapted crafting foundation. Remove fantasy hostile mobs, villagers and trading, magical loot, enchantments, brewing magic, experience leveling, portals, vanilla structures, and dimension travel. Disable villager and iron-farm progression. Replace exposed redstone technology with the designed electrical interface; internal implementation may still reuse compatible mechanics.

Prototype supports allow four-block timber spans and eight-block reinforced spans. Unsupported sections begin visible cracking after 30 real seconds and fail after a further 90 unless repaired. Grounded walls are stable at ordinary height; taller walls require posts or bracing. Fire and crowd pressure are separate damage sources. Material resistance is tuned by encounter tests, not by giving walkers arbitrary concrete-cutting abilities.


### Delivery order

Build the technical core and a rural test area first, then Tyreese’s hammer encounter, followed by the prison as the first full iconic landmark. Validate solo play, then two-player cooperation and PvP, then the 16-player cap. Expand other landmarks after the quality standard is proven. Finish with integration and performance tuning, retaining the accepted core gameplay scope.

Design decisions are now specified. What remains uncertain is engineering feasibility, dependency compatibility, asset accuracy, and measured balance and performance. Those are validation tasks to execute, not unanswered preferences to send back to Andrew.


## Original document table

| Passive focused weapons | Weapons with active abilities |
| --- | --- |
| Rick’s hatchet | Rick’s Python |
| Carol’s knife | Rick’s red-handled machete |
| Morgan’s staff | Michonne’s katana |
| Sasha’s suppressed rifle | Lucille |
| Merle’s bayonet arm | Daryl’s crossbow |
| Tyreese’s hammer |  |

## Original document table

| Piece | Passive direction | Active direction |
| --- | --- | --- |
| Rick’s jacket | Melee strength and grab escape | Brutality: faster committed attacks, stronger shoves, reduced stagger |
| Negan’s jacket | Blunt charge speed and stagger | Heavy Hitter: shorter heavy-swing recovery and increased impact |
| Daryl’s vest | Quieter movement and steadier aim | Hunter’s Focus: reduced sway, faster handling and crouched movement |

## Original document table

| Landmark | Design direction |
| --- | --- |
| The prison | Recognizable layout and interior spaces |
| Hershel’s farm | Recognizable property and buildings |
| Woodbury | Recognizable town compound |
| Terminus | Recognizable complex and approaches |
| Alexandria | Recognizable streets, homes, and perimeter |
| Hilltop | Recognizable compound and central buildings |
| The Sanctuary | Recognizable industrial compound |
| Oceanside | Recognizable coastal settlement |
