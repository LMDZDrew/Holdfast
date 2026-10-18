# Decision record

Status: accepted design unless labeled proposed. This file supersedes older wording in `design.md` and the original Word document.

## Dawn-build follow-up and movement-pack clarification (Andrew, 2026-10-18)

- Andrew reports unstable normal-walking camera motion and no katana execution contact; investigate actual runtime alignment and closer pairing rather than relying on earlier software measurements.
- Swooshes are accepted. Make the sledgehammer convey more weight. Hold left click is the requested universal weapon-charge input; preserve server-measured charge/contact/outcomes and accepted TaCZ gun behavior while resolving input ownership.
- Exaggerate blood on every confirmed impact, including bullets. This supersedes the earlier modest gunshot-blood direction; origins remain impact-site based and effects retain finite capacity/lifetime.
- Evaluate the named **Fresh Animations resource pack**, specifically its official Player Extension, for replacement player locomotion. This is not a request to author new locomotion clips. EMF/ETF and pack versions remain candidates pending dependency checks and actual compatibility testing with Holdfast, Player Animator, First Person Model and TaCZ. Preserve combat/injury/gun animation ownership.

## Third stance/execution review (Andrew, 2026-10-17)

- Blood particles must visibly originate at the weapon/bullet impact site on the walker. Server feedback supplies a surface entry, incoming direction, and a separate gunshot exit where available. Execution contacts follow the authored skull pose; free melee remains a regional ray approximation, not animated mesh collision.
- Knife and katana executions need deeper insertion, standing and on the ground, including side/rear approaches. Measure physical blade insertion as well as nearest-face depth; do not lengthen weapon geometry to manufacture contact.
- Andrew clarified the resting fire axe problem: "the head/blade direction, maybe give the same resting position as the hatchet." Match that carry direction. Preserve the accepted dynamic ready poses and all firearm animation assets.
- These are requirements, not in-game acceptance of the new implementation. The other second-review items remain unreported.
- During this pass Andrew reported that the torso still "shimmers" and clarified **"Twists/twitches on clicks"**. Remove the ordinary attack clips' lateral torso twist as well as vanilla's additional swing. Forward strike lean remains; executions and accepted gun clips are separate actions.
- Andrew also requested the main hand halfway along the longer axe handle and clarified **"Resting carry only"**. Apply that grip to the resting fire axe, then return to the existing combat grip for ready, swings and executions.
- Andrew requested a richer grab QTE animation and **F** as the default execution and TaCZ shove binding, with editable controls in Settings. Holdfast's single context mapping covers escape/kick free, execute and cancel under the Holdfast category; TaCZ's existing editable melee mapping covers its shove. When both share a key, a grab/active execution or pistol execution request takes precedence, while rifles and non-execution contexts retain TaCZ melee. The server still decides the request; presentation complexity does not introduce button mashing or extra outcome authorities.

## Identity and scope

- Faithful to AMC's *The Walking Dead* in atmosphere and recognizable remnants, with original playable survivors and hostile human communities. Spoilers are fine.
- Brutal but learnable survival. Provisions and ordinary ammunition remain reasonably obtainable; travel, hauling, human conflict, and securing a home supply the pressure.
- Southeastern-US-inspired, effectively endless world with widely varied rural distances, furnished towns, roads, and sparse inhabited communities.
- No Nether, End, vanilla structures, fantasy progression, or Lost Cities. TaCZ is a preferred candidate for firearms, not yet validated as an integrated dependency.
- Prioritize quality of mechanics and world design before performance optimization. Eventually test the 120 FPS aspiration on an RTX 5050 laptop with 16 GB system RAM, optional shaders separately, and a 16-player cap; no result is yet measured.

### Next mechanics phase after animation acceptance (Andrew, 2026-10-18)

- Finish animation quality review and obtain Andrew's satisfaction before beginning this phase. Then prioritize precise server-owned melee and bullet hit regions, pose-continuous ragdoll transitions for execution and ordinary deaths, dismemberment, and ground blood pools/spatter; follow with GUI elements and general game mechanics. No world generation is authorized by this sequence.
- Ragdolls should inherit the final animated pose and motion without a stiff fall or visible reset. Cosmetic physics must not decide damage, death, bite reach or authoritative entity placement.
- Suitable accurately placed blade attacks can sever arms, decapitate walkers, or divide the torso. A torso cut should let both pieces fall, then resume the existing crawler behavior after a short recovery. This is an incapacitation/transition when the brain is intact, not a body-damage kill or resurrection after brain destruction. Detached heads with intact brains remain animated; brain-destroyed heads do not. Server-owned limb/head state, crawler transition and persistence must stay consistent across clients and reloads.
- Blood should pool and spatter on ground surfaces at confirmed impact/severance sites, with bounded lifetime and density. Evaluate existing physics, gore and decal mods against Holdfast's custom models and animation stack before selecting dependencies; matching game versions alone do not establish compatibility.
- This explicitly schedules dynamic dismemberment for the next mechanics phase and supersedes its older blanket deferral below. Advanced structural physics remains deferred. All of these features are planned, not implemented or accepted.

### Dawn correction requests (Andrew, 2026-10-18)

- Actual preceding-build observations: dead walkers still vocalize; katana looks much better but needs a little more insertion; axes look great; pistol ammunition is good; QTE presentation looks much better but the initial walker grab retreats/restarts before the real hold/prompt; melee blood is insufficient. Preserve accepted axe/gun poses and ammo behavior.
- Dead walkers must stop an existing voice and stay silent. Increase blood substantially for blunt, stab and slash contacts, with modest gunshot blood and impact-site placement preserved.
- Unify the initial walker reach/grip and QTE transition. The server validates first contact and starts the escape window then; no new button-mash or client-owned outcomes.
- Make punches more believable with readable preparation and corresponding server contact timing, without reintroducing lateral torso twitch. Add one pre-contact weapon swoosh even on misses, but impact audio and blood only on confirmed contact.
- Use isolated excerpts from Andrew's named sources: `knife swoosh.mp3` for knives/hatchets; `sword swoosh.mp3` for swords/katanas/machetes; `blunt force woosh.mp3` for blunt weapons. Long axe uses the heavier blunt swoosh. Do not use entire recordings or the rejected `axe_hit.mp3` ding.
- Evaluate First Person Model for visible body presentation, preserving Player Animator's Holdfast clips and TaCZ's arms/items/ADS ownership. It remains a reversible client candidate until the relevant runtime and user checks are recorded. Punchy remains unadopted.

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

## Holdfast animations replace vanilla's (accepted 2026-10-17, Andrew)

- Player animations play from real input and state, not test commands: left-click attacks play the held weapon family's swing; walking, sprinting, crouching, jumping and idle play Holdfast locomotion; a held melee weapon plays its hold. They replace vanilla's limb animation, follow actual movement speed, blend between states, and are visible to every player.
- TaCZ keeps ownership of arms, items and ADS while a gun is held (legs-only locomotion variants); injury walks replace normal locomotion when injured. The `/hfa` harness remains a test tool.

## Stage 1 validation with one human tester (accepted 2026-10-16, Andrew)

- Finish the combat and presentation slice in `roadmap.md` before world generation. Andrew can perform solo visual checks on a dedicated server, but has no second human tester available now.
- Use automated server tests, scripted reconnect/state checks, and a second local client only if practical to cover multiplayer-sensitive code. Record what each method verifies and what remains untested. Human multiplayer acceptance is deferred until testers are available; it is not a prerequisite for beginning world generation.

## Stage 1 presentation integration (Andrew, 2026-10-17)

- Andrew reopened blood/particles and walker audio after the runtime-correction handoff. Integrate the YouTube-list candidates where they fit; the earlier deferral no longer applies to this work.
- Use the locally installed Tissou's Zombie Pack 2.7 as the walker-audio reference/integration, preserving attribution and keeping third-party assets separate from core source. This does not establish rights to any underlying show recordings.
- Evaluate Extra Gore 5.3 and its Bookling Gear dependency as optional client presentation. Holdfast's confirmed-hit packet remains the only emitter in this integration; no predicted click, health heuristic, particle or sound decides an outcome. Version selection remains a candidate until a client compatibility run and user review.
- Andrew authorized animation quality checks and source corrections while away. Automated checks do not constitute his in-game acceptance.

## Latest melee presentation direction (Andrew, 2026-10-17)

- Sword/katana, axe, knife and hatchet need natural resting carries outside combat and distinct ready stances during combat and a few seconds afterward, then return to rest. The exact timeout is not prescribed; document any tuning default.
- Gun animations are accepted by Andrew and should be left alone for now. This does not waive separate server firearm checks.
- Remove the unwanted torso shimmy Andrew reports on every click. Executions must support front, rear, side and diagonal approaches while retaining server-owned eligibility, reach, collision/obstruction checks, weapon gates and outcomes; this is not authorization for unlimited reach or through-wall alignment.
- Claude owns resting/ready poses, remaining axe/knife hit instability and the newly reported buggy jumping. Codex owns impact-sound extraction from Andrew's four supplied MP3s. Extract individual effects, never play entire source recordings as one hit. Keep sounds cosmetic and preserve server-confirmed contact timing.
- Subsequent direction: Codex should address the click shimmy, jumping and axe/knife instability first, then leave Claude an updated handoff. Andrew likes the extracted effects but rejects the ding in **`axe_hit.mp3`**; exclude that source from playback. Stop implementation and write the handoff if the active usage window reaches 5% remaining.
- **Proposed for evaluation, not selected:** Andrew wants to consider tailoring first-person presentation to Punchy. Evaluate a reversible Forge 1.20.1 client trial and ownership/synchronization with Holdfast and TaCZ before replacing existing first-person animation. Keep server combat outcomes authoritative and preserve accepted gun animations.

## Review of stances and executions (Andrew, 2026-10-17)

- He likes the dynamic ready stances. Rest carries should keep the arms close to the vanilla stance (no flaring), and the resting axe is reversed (head trailing).
- Pistol executions must spend a round. An empty gun can't execute.
- The katana needs its own execution; it borrowed the one-handed machete chop and looked odd.
- **Walkers despawn to limit lag.** This supersedes design §12's "corpses persist as the ended state" for Stage 1. Living walkers despawn after a while with no player in range; corpses despawn some minutes after death, never in sight of a player. Exact times are configurable; defaults are provisional (`core_combat.md`, "Despawning").

## Explicitly deferred

Character skill leveling, friendly/recruitable survivors, broad stealth disguises beyond Whisperer masks, advanced structural physics, and guaranteed shader FPS. They can be revisited after a representative playable prototype. Dynamic dismemberment is now scheduled for the mechanics phase after animation acceptance, as specified above.
