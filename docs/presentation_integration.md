# Stage 1 blood and walker audio

## Exaggerated blood follow-up — 2026-10-18

Supersedes dawn burst values below: ordinary/brain kill blunt260/380, slash300/420, stab220/340, gunshot240/360. Extra Gore droplets double in scale to0.09–0.20, spray more strongly/widely, live80–160ticks and retain ground merging. Holdfast tracks at most1,200 own particles (or the lower configured capacity), retiring older droplets before a fresh impact; global maximum remains capped at1,800. Login/logout/world-change cleanup prevents retaining old worlds. Glancing confirmed melee contacts now emit blood while retaining their existing quiet impact sound; misses still have no feedback emitter. Entry/exit origins, damage authority, accepted audio and animation assets are unchanged. Counts/factory admission checked in an isolated renderer probe; Andrew's appearance/performance review is pending. No persistent pools/dismemberment added. See latest `testing.md` for current jar hash and exact limits.

## Latest client movement integration — 2026-10-18

Fresh Animations plus its Player Extension and EMF/ETF/EPC/TaCZ bridges are now installed as a reversible client trial. Core yields ordinary locomotion while preserving injury/action/carry ownership; all existing blood/audio/gun assets remain unchanged. See `fresh_animations.md` and newest `testing.md` for versions, actual production-format client evidence, limitations and rollback. Andrew's request for exaggerated blood including bullets remains pending; the dawn counts below are still the implemented values.

## Dawn revision — 2026-10-18 (supersedes older counts/death routing below)

**Walker voices:** `WalkerVocalPacket` identifies the source entity. `WalkerVoices` permits one tickable, following voice per living walker, stops it on corpse/dead/silent/unloaded state, and cleans finished instances. Brain kills no longer start a death growl. This changes playback ownership, not the accepted recordings or external pack. A client smoke confirmed an active voice before death and stopped playback afterward; Andrew's listening review remains pending. Protocol **5** requires matching core jars.

**Blood:** both renderers share `BloodProfile`: blunt 64/110, slash 72/120, stab 48/85, gunshot 10/18 (ordinary/brain kill). Entry and optional gun exit placement remain server-supplied; non-gun brain kills keep the fallback's six extra gore fragments. Extra Gore's 48-block cutoff, finite lifetime and 1,800-particle capacity remain. This is increased burst volume, not a new persistent-pool system or measured dense-horde performance pass.

**Swing audio:** editable `art/audio/swoosh_cuts.json`; build with `python art/audio/build_impact_pack.py C:/Users/drewl/Downloads OUTPUT.zip --recipe art/audio/swoosh_cuts.json --preview-dir PREVIEWS`. Three proper subranges, mono 48 kHz Vorbis, DC removal, reduce-only -6 dBFS peak ceiling, 1/25 ms edge fades. Knife 0.12–0.60 s, sword 0.08–0.48 s, blunt 0.01–0.245 s; original recordings are unchanged and not committed. Events `swing.knife` (knife/hatchet), `swing.sword` (katana/sword/machete), `swing.blunt` (bat/hammer/staff/long axe). Empty hands use quiet existing `swing.light`. `gen_sounds.py --refresh-swing-aliases` supplies procedural fallbacks without re-encoding existing samples; the local resource pack replaces only these three new events. Existing impact pack, rejected-ding exclusion and Tissou pack stay intact. One server cue before contact; misses have no confirmed-impact cue. An actual isolated missed knife swing produced one swing event at client tick 102, zero impact events. High-latency timing and listening approval are pending.

**First Person Model candidate:** [official 2.4.8 Forge 1.20.1 metadata/file](https://modrinth.com/mod/first-person-model/version/iwodXe68), [official source](https://github.com/tr7zw/FirstPersonModel). Client only, reversible trial, MIT repository; no extra required mod in that version's metadata. API metadata and binary SHA-512 verified. Latest 2.7.3 was examined but not installed: it requires Not Enough Animations and its published PA offset handler is a no-op; 2.4.8 retains the implemented handler against the installed PA API. This is an evidence-based trial choice, not an assertion that old versions are generally preferable.

Holdfast's `BodyAwarePlayer` yields the PA first-person render mode only while FPM is actually enabled/eligible; PA still applies all clip poses. This avoids PA hiding the torso. Config: `dynamicMode:false`, `vanillaHandsMode:"OFF"`, `autoVanillaHands` includes `tacz:modern_kinetic_gun`, preserving native TaCZ hands when looking down. F6 toggling restores the original PA fallback; harness inspection temporarily opts out of FPM. No Punchy or Not Enough Animations installed.

An isolated integrated-world run used Forge 47.4.8, GeckoLib 4.8.4, PA 1.0.2-rc1, TaCZ 1.1.8-hotfix2, Extra Gore 5.3, Bookling 3.15, FPM 2.4.8, and all three audio packs. Screenshots inspected: torso visible looking down empty-handed/with knife/with a TaCZ pistol; ordinary swing, grab brace, katana third person, FPM disabled/restored. This does not accept animation fluidity, all weapons, ADS/reload, dedicated login, or performance. An earlier isolated userdev dedicated login timed out; the successful run was local/integrated. Production Gate 0 client login and Andrew's seven-item batch remain required. Probe sources are excluded from release jars.

**Rollback:** close Minecraft; remove only `firstperson-forge-2.4.8-mc1.20.1.jar` from the Prism mods folder to restore PA presentation; disable/remove `Holdfast-Swooshes-1.zip` to restore procedural swoosh fallbacks. Prior options and optional prior FPM config are in `core/build/test-evidence/2026-10-18-dawn/before/client`. For complete code rollback, gracefully stop the server and restore BOTH old protocol-4 Holdfast jars to server/client from `before`, then restart. Never mix protocol 4/5 core jars. FPM is never installed on the dedicated server.

Andrew reopened this work on 2026-10-17. These additions come from Noxus's [video list](https://www.youtube.com/watch?v=3EgxgXso0PM); they add presentation, not a new combat or zombie framework.

## Components under review

| Component | Official metadata | Placement / purpose |
|---|---|---|
| Extra Gore 5.3, Forge 1.20.1 beta | [file 8608443](https://www.curseforge.com/minecraft/mc-mods/extra-gore/files/8608443) | Prism client only. Public particle factory used by `ExtraGoreEffects`; not bundled in core. |
| Bookling Gear 3.15, Forge 1.20.1 | [file 8600738](https://www.curseforge.com/minecraft/mc-mods/bookling-gear/files/8600738) | Prism client only; Extra Gore's required library. |
| Tissou's Zombie Pack 2.7, 1.20.1 | [project](https://www.curseforge.com/minecraft/texture-packs/tissous-zombie-pack-optifine-1-7x-1-20) | Local source ZIP from Andrew's Modrinth instance; generated audio-only pack enabled in Prism. No textures, models or entity-animation replacement. |

The Extra Gore 5.3 file changelog, unlike the older project summary, explicitly describes it as client-side and requires Bookling Gear. It also warns of unfinished features and potential performance drops. Treat this as a candidate being tested, not a general performance recommendation. Core optionally accepts exactly its inspected 5.3 API on the client. Neither new jar is required on the dedicated server.

SHA-256:

- Extra Gore: `09AA915EE925EE1C61010F5050415F5C1049306D816FD4563AF9263610A0CD18`
- Bookling Gear: `5D3C24C199E2EE7F086103F49403B918DB9488B7690FC53B50BB4C2B9B6E299A`
- Original TZP ZIP: `AED1AF48318A160F049FE8AC52D8445848DE89EEBC530C45DD5C0C5DC7AF95BB`
- Generated audio-only ZIP: `30EAFE260E8BBF82E1311D43B455E24341B0D6F05197FAB46C5D07B5473F2789`

## Reproduce the local audio pack

```
python art/audio/build_tzp_bridge.py ORIGINAL_TZP_1.20.1_2.7.zip OUTPUT.zip
```

The generated ZIP includes 195 ambient, 98 hurt and 10 death Oggs, six core event aliases and Tissou attribution/provenance. Six nonexistent ambient references in the original are excluded and listed in its provenance JSON. Original uppercase `moreZsounds` references and ZIP paths are normalized together to lowercase because strict Minecraft 1.20.1 rejected them in the first client load. It contains original sample bytes, not new recordings or remastered audio. Some decoded original peaks exceed full scale; listening/volume acceptance remains pending. The project page is not proof of rights to any underlying television recordings. Do not redistribute extracted audio as Holdfast-owned material; review distribution terms/provenance before public packaging. No third-party samples are committed to this repository.

Core groan/breath use the ambient event, snarl/lunge/bite use hurt, death uses death. This reuses the pack's pools; it does not claim dedicated context recordings. Original core flesh/skull impact sounds remain separate from vocals and TaCZ gunfire. Disabling the audio pack restores the core's procedural sounds. Ordinary walker vocal intervals are now bounded: idle 9–18 seconds, pursuit 5–9 seconds, randomized first call. This prevents a single walker repeatedly stacking long ambient samples; it is not a global horde voice budget.

## Editable context/shove controls (2026-10-17)

Holdfast's **Escape / Execute / Cancel** mapping is in the **Holdfast** keybind category and defaults to **F**. All current Holdfast-specific key actions use that context mapping. TaCZ's existing **Melee** mapping stays in its own category and is separately editable; the Gate 0 preset sets it to **F** too. TaCZ's shipped reset default remains V; Holdfast does not patch the third-party jar or silently overwrite later user remaps. `core/tools/set-gate0-controls.ps1 -OptionsPath <Prism minecraft/options.txt>` applies the pack preset with a timestamped backup. It moves vanilla's conflicting F offhand swap to Y only if that action still uses F.

When the mappings share a physical key, the optional TaCZ client adapter cancels its `GunMeleeEvent` before its melee packet/sound/animation if the input belongs to a Holdfast grab, active execution, or a pistol execution request. Other contexts, including rifle shove, remain TaCZ's. Different remapped keys remain independent. The server also cancels native TaCZ melee while grabbed/executing, preventing an overlapping combat state. This is request arbitration; the server still validates all execution/escape outcomes. Rebinding and actual TaCZ event ordering need the client review; headless tests only cover the routing policy and server grab behavior.

## Confirmed-contact blood (placement details)

Review 3 placement: free-melee feedback clips the current server eye/view ray against posture-local regional boxes, with nearest-surface projection for the existing arc margin. The selected region and surface come from the same volume. This is a static approximation, not weapon-mesh collision. Executions use generated, reaction-specific skull entry points at the authoritative kill tick, including left/right/rear, prone and crawler poses; the old fixed entity offsets are removed.

TaCZ 1.1.8-hotfix2's inspected `onBulletTick` performs its collision sweep from `bullet.position()` to `position + getDeltaMovement()` before moving and posting hurt events. Its hurt event exposes no hit location. The adapter clips that forward segment against regional boxes (skull only for a TaCZ headshot); other living entities use their bounds. A missing/zero-velocity bullet uses an attacker-to-target ray projected to the surface. Fast projectiles therefore no longer send their tick-start position as the impact. This path still needs an in-game TaCZ check.

Both particle renderers start the entry burst at the supplied surface, flowing opposite the incoming strike. Gunshots with a confirmed exit send the final 30% of particles from the separate exit, continuing along the shot. Counts and budgets are unchanged. `HitFeedbackPacket` now carries optional exit coordinates; **core protocol 4 requires matching new core jars on server and client**. Sounds and walker audio assets are unchanged in this pass. `/holdfast presentation` remains an arbitrary-position cosmetic diagnostic and cannot verify walker contact placement.

Only `HitFeedbackPacket` triggers the Holdfast blood adapter. Extra Gore's automatic click/health-change handler is unregistered after client login; its particle renderer remains registered. This is intentional across this test instance, including ordinary non-walker core-confirmed hits. Misses and cancelled attacks do not produce a packet. The operator-only `/holdfast presentation blunt|slash|stab|gunshot` is a cosmetic diagnostic exception: no victim, hit, damage, durability or saved state.

Profiles vary counts and direction: compact blunt bursts, wider slashes, narrow stabs, faster gunshot sprays; brain kills increase count. Particles use dark red world-lit heavy fluid, 20–35 second lifetime, a 48-block emission cutoff and at most 1,800 particles under Extra Gore's capacity manager. Surface collision/drips are the dependency's renderer. This does not add wounds, hit regions, damage, infection or permanent blood persistence. Previous core dust/block blood remains the fallback when Extra Gore is absent; it is not emitted alongside the adapter.

## Deployment and rollback

Use the recorded hashes. Close the Prism Minecraft client before replacing jars or options. Back up `options.txt`, prior Holdfast jars and logs. Copy both newly built Holdfast jars to both server/client mods folders, with Extra Gore and Bookling Gear in the client folder only. Copy the generated audio ZIP into client `resourcepacks` and enable it. Preserve other selected resource packs when repeating this on an existing customized profile.

Rollback: close the client, move the two added client jars out of `mods`, disable the audio pack, and restore the backed-up matching Holdfast jars on both sides after a graceful server stop. Do not mix core protocols 2 and 3. Runtime logs and in-game acceptance belong in `docs/testing.md`; see the top of `docs/stage1_bulk_test.md` for one review batch.

## Punchy first-person evaluation (2026-10-17, Claude) — not adopted

Andrew proposed tailoring first-person animation to Punchy. Candidate: Punchy **2.8f** (Forge 1.20.1), Modrinth version `vbMU3NlS`, file `punchy-2.8f-forge-1.20.1.jar`, 1,937,605 bytes, SHA-1 `d8f4bbf93c6166b811fe923dc3b82c6825056b25` (matches the API). It is client-only, all rights reserved (`ARR`), and depends only on Forge 47+ and Minecraft 1.20.1. I downloaded it from the official CDN into a scratch folder and inspected its manifest, mixin lists and class signatures with `javap`. It was **not installed** in any mods folder and not run.

**What it can do:**
- First-person arm and item clips, set per item through tuning/resource-pack entries (`attack_1`…`attack_6`, `critical_hit`, `charge`/`release`/`abort`, start/loop/end).
- An item blacklist, with built-in TaCZ awareness (`TaczBlacklistCompat` lists every TaCZ gun id).

**Why it doesn't fit Holdfast now:**
- **No hook for Holdfast to trigger it.** There is no API package, no network handling, and only client debug commands. Attacks start from its own mixins on `LivingEntity.swing` and `MultiPlayerGameModeAttack`, which play its combo slots. Holdfast can't make it play the clip the server picked (light/heavy, charged, overswing) at the server's contact tick, so its visual contact would drift from the outcome.
- **It needs the vanilla swing that the shimmy fix removed.** Codex's motion fix suppresses the vanilla hand swing for Holdfast attacks. Punchy would then never animate them; re-enabling that swing brings the click shimmy back.
- **It can't step aside for executions and grabs.** Its exclusion is a static per-item blacklist. Holdfast's first-person execution and grab clips would render alongside Punchy's arms for any non-blacklisted item, for example being grabbed while holding food.
- **It overlaps Player Animator.** Its `LivingEntityRendererMixin.applyThirdPersonAnimation` also animates third person.

**Decision:** keep Holdfast's first-person path through Player Animator for melee, executions and grabs, and TaCZ for guns. A later trial could still use Punchy for non-combat items only: blacklist all Holdfast weapons and TaCZ guns. That trial would first have to show how being grabbed while holding a non-blacklisted item renders, and that Player Animator's third person is unaffected. Its license allows modpack listing, not redistributing the jar.
