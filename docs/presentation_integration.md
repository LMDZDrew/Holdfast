# Stage 1 blood and walker audio

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
