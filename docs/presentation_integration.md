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

## Confirmed-contact blood

Only `HitFeedbackPacket` triggers the Holdfast blood adapter. Extra Gore's automatic click/health-change handler is unregistered after client login; its particle renderer remains registered. This is intentional across this test instance, including ordinary non-walker core-confirmed hits. Misses and cancelled attacks do not produce a packet. The operator-only `/holdfast presentation blunt|slash|stab|gunshot` is a cosmetic diagnostic exception: no victim, hit, damage, durability or saved state.

Profiles vary counts and direction: compact blunt bursts, wider slashes, narrow stabs, faster gunshot sprays; brain kills increase count. Particles use dark red world-lit heavy fluid, 20–35 second lifetime, a 48-block emission cutoff and at most 1,800 particles under Extra Gore's capacity manager. Surface collision/drips are the dependency's renderer. This does not add wounds, hit regions, damage, infection or permanent blood persistence. Previous core dust/block blood remains the fallback when Extra Gore is absent; it is not emitted alongside the adapter.

## Deployment and rollback

Use the recorded hashes. Close the Prism Minecraft client before replacing jars or options. Back up `options.txt`, prior Holdfast jars and logs. Copy both newly built Holdfast jars to both server/client mods folders, with Extra Gore and Bookling Gear in the client folder only. Copy the generated audio ZIP into client `resourcepacks` and enable it. Preserve other selected resource packs when repeating this on an existing customized profile.

Rollback: close the client, move the two added client jars out of `mods`, disable the audio pack, and restore the backed-up matching Holdfast jars on both sides after a graceful server stop. Do not mix core protocols 2 and 3. Runtime logs and in-game acceptance belong in `docs/testing.md`; see the top of `docs/stage1_bulk_test.md` for one review batch.
