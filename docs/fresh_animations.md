# Fresh Animations client trial — 2026-10-18

Andrew requested the named resource pack for replacement movement and authorized installation. This is a reversible Forge1.20.1 trial, not his visual acceptance or completion of the other dawn-review corrections.

## Components

Official Modrinth metadata and downloaded jars' `META-INF/mods.toml` were checked together. Downloads were checked against published SHA-512. All five mods below are client-only in this setup; do not put them on the dedicated server. The base pack's1.10.4 release is labelled beta by its author.

| Component | Version / official file |
|---|---|
| Entity Model Features | [3.3.12 Forge1.20.1](https://modrinth.com/mod/entity-model-features/version/mQR6L8Qe) |
| Entity Texture Features | [7.2.6 Forge1.20.1](https://modrinth.com/mod/entitytexturefeatures/version/EAHEZRbp) |
| Entity Player Compat | [2.2.1 Forge1.20.1](https://modrinth.com/mod/entity-player-compat/version/3Yn7rcNT) |
| EMF Compat: Core | [2.3.2 Forge1.20.1](https://modrinth.com/mod/emf-compat-core/version/O02HXwIg) |
| EMF Compat: TACZ | [1.0.1 Forge1.20.1](https://modrinth.com/mod/emf-compat-tacz/version/bG7U0hqM) |
| Fresh Animations | [1.10.4](https://modrinth.com/resourcepack/fresh-animations/version/xN57JJts) |
| Fresh Animations: Player Extension | [1.1.0](https://modrinth.com/resourcepack/fa-player-extension/version/Wj7NeGjP) |

EMF requires ETF>=7.2; EPC requires EMF>=3.2 and Player Animator>=1.0.0; Compat Core requires EMF>=3.3.2; TaCZ bridge requires TaCZ>=1.1.4, EMF>=3.2.4 and Compat Core>=1.0.0. Existing PA1.0.2-rc1 and TaCZ1.1.8-hotfix2 meet the declared ranges. Metadata ranges were only the first gate; see actual production-format client evidence in `testing.md`.

The Better Combat compatibility resource pack was researched but not installed: it requires its own helper and Better Combat. Holdfast already owns combat outcomes. EPC bridges PA parts without introducing Better Combat mechanics. The EPC author explicitly warns that Fresh's first-person animations are not fully handled by EPC; real all-weapon clipping/ADS/reload review remains necessary.

## Ownership

`FreshMovement.active()` requires EMF, ETF, EPC, the TaCZ compatibility pair when TaCZ is loaded, and an actively selected `FA+Player` resource supplying the normal and slim player models. With that gate satisfied, `ClientClips` yields ordinary idle/walk/sprint/crouch/jump/fall/land locomotion. Holdfast retains authored injury movement, carries and actions. EPC arbitrates PA's affected model parts; the TaCZ bridge preserves gun parts. Core camera cues have no ordinary Holdfast gait to sample in this mode. Vanilla view bob settings remain unchanged in the play instance.

Disabling the Player Extension or removing required bridge mods returns to the existing Holdfast movement on the next client tick. No exported clip was edited or regenerated for this change. All existing274 core asset entries, including gun/axe clips and audio, match the dawn jar byte-for-byte. Server protocol remains5 and all hit/charge/death authority is unchanged.

Base Fresh affects supported vanilla mobs. Holdfast walkers use a custom GeckoLib renderer and are not automatically replaced by its zombie animations. Player animation presentation is local to clients with this stack installed; it does not force others to install the pack.

## Files and rollback

Prism target: `C:/Users/drewl/AppData/Roaming/PrismLauncher/instances/Holdfast Gate0/minecraft`. Keep existing pack order, append base Fresh then Player Extension (Player Extension above base in the pack selection screen). Existing audio packs remain enabled.

Backups, exact download URLs, all SHA-256/SHA-512 hashes, logs, screenshots and the installed-file manifest are under ignored `core/build/test-evidence/2026-10-18-fresh/`. `before/client` preserves options/config/Holdfast jars; `before/server` preserves Holdfast jars and original log. Third-party jars/resource packs are not committed to the repository.

For movement rollback, disable `FA+Player-v1.1.zip` in Resource Packs. Holdfast immediately resumes its original locomotion after reload. For full presentation-stack rollback, close Minecraft and move only the five new jars listed in the download manifest out of `mods` (move the TaCZ addon and Compat Core together); disable the two new resource packs. Existing PA/TaCZ/FPM/GeckoLib/Extra Gore/Bookling and all audio packs remain. A full options backup exists, but restoring it also restores old keybind/settings values; prefer disabling only the new packs. Core can remain on the new protocol5 build during this presentation rollback.

For code rollback, gracefully stop an empty server, restore `before/server/mods/holdfast_core-0.1.0.jar` and `before/client/mods/holdfast_core-0.1.0.jar` together, then restart. Both are the previous protocol5 dawn build. Harness jar is unchanged.

## Remaining acceptance

Automated launch, ownership assertions and isolated screenshots are distinct from Andrew's review. Dedicated client login, sustained real mouse-driven ADS/reload/shooting with pistol and rifle, complete paired executions/grabs, all injuries, all skin/armor variants and horde performance remain unaccepted. Ordinary walking's previously reported instability must be reviewed with normal play-instance view-bob settings; the isolated probe disables vanilla bob to measure added pitch/roll separately.
