# Local impact recordings — 2026-10-17

Andrew supplied four MP3 references and requested individual extracted effects rather than full-recording playback. Codex owns this audio work; Claude's separate assignment is `claude_stage1_stances_handoff.md`. Original MP3s remain unchanged in `C:\Users\drewl\Downloads`. Neither sources nor derived recordings are committed. Their original authors and public redistribution rights are unspecified.

**Current revision:** Andrew likes the effects but identified **`axe_hit.mp3`** as the excessive ding. Recipe version 2 excludes that entire source and both axe cuts from every event. Five wet/blade cuts remain. Latest motion/animation continuation: `claude_stage1_motion_followup.md`.

## Editable extraction

`art/audio/impact_cuts.json` is the editable cut list and provisional event routing. `art/audio/build_impact_pack.py` decodes in floating point, downmixes to mono for spatial audio, removes DC, reduces gain to a -6 dBFS pre-encode peak ceiling (never boosts), applies 1 ms onset/25 ms tail fades, and writes Vorbis. It checks decoded output, canonicalizes Ogg stream serials/CRCs and uses stable ZIP timestamps for repeatability under the same encoder versions. No synthetic variations or pitch-shifted copies were added.

| Cut | Original file | Source range, seconds | Duration |
| --- | --- | --- | --- |
| wet_1 | walker_hit_squelch.mp3 | 0.110–0.850 | 0.740 s |
| wet_2 | walker_hit_squelch_2.mp3 | 0.060–0.960 | 0.900 s |
| blade_1 | sword_stabbing_slashing.mp3 | 0.055–1.020 | 0.965 s |
| blade_2 | sword_stabbing_slashing.mp3 | 1.350–1.965 | 0.615 s |
| blade_3 | sword_stabbing_slashing.mp3 | 1.980–2.940 | 0.960 s |

Cuts were selected from waveform transients and quiet gaps. **Andrew likes the effects overall and rejects the axe-source ding.** No new listening result for the no-ding revision has been reported. The blade 2 stab assignment and blade 1/3 slash assignments remain provisional descriptions of the recorded action.

## Build and local installation

PowerShell, repository root:

```powershell
$env:PYTHONPATH = 'C:\Users\drewl\Holdfast\core\build\audio-check-deps'
& 'C:\Users\drewl\AppData\Roaming\uv\python\cpython-3.14.7-windows-x86_64-none\python.exe' art/audio/build_impact_pack.py C:/Users/drewl/Downloads core/build/impact-audio/Holdfast-Impact-Cuts-1.zip --preview-dir core/build/impact-audio/previews
```

The ignored Python dependency directory currently contains numpy 2.5.4, soundfile 0.14.0 and libsndfile 1.2.2. `--preview-dir` produces separate PCM16 WAV previews for each cut. The pack contains its complete source hashes, source ranges, gain reductions, decoded measurements and Ogg hashes in `holdfast-impact-provenance.json`.

Current no-ding pack SHA-256: **`5378A62459DA38FBABB47A4E048046A03808BC90131CEB340FD273BBA855CB97`**. Copied to Prism `Holdfast Gate0/minecraft/resourcepacks/Holdfast-Impact-Cuts-1.zip` after confirming the client was closed. Current options already enabled this filename above the Tissou pack; that selection was retained. The previous seven-cut ZIP is backed up under `core/build/test-evidence/2026-10-17-motion-corrections/impacts-before.zip`. No server restart is needed for this resource change. Disable this pack to restore original core impacts.

Five events use `replace: true`, preventing procedural samples from remaining mixed into the same event: `impact.blunt` (wet cuts), `impact.slash` (blade 1/3), `impact.stab` (blade 2 and wet cuts), `impact.skull_blunt` (wet cuts), and `impact.skull_blade` (blade cuts). Existing subtitles and positional sound playback remain. Gunshot sounds, swings, walker vocalizations and blood are unchanged. Emission still comes from the current server-confirmed contact flow; the cosmetic operator preview remains the existing diagnostic exception.

**Routing limitation:** current `HitFeedbackPacket` contains only BLUNT/SLASH/STAB/GUNSHOT, not the specific weapon. Axe, hatchet, machete and katana therefore share the slash pool; stabbing and slashing brain kills share `skull_blade`. This pack does not claim dedicated per-weapon routing or different wet/skull recordings where the supplied files do not establish one. If dedicated axe-versus-sword routing is needed, Codex must extend the presentation metadata coherently on both sides and test it; do not infer the weapon from a nearby player's current held item.

## Evidence and remaining review

Five Oggs decode fully as finite mono audio, 0.615–0.965 s, decoded peaks 0.495–0.551; all event pools resolve to included files, no MP3s included and no axe source/cuts/references remain. The original seven-cut pack was checked for repeatability; canonical encoding is unchanged. Logs/report and source waveform image: `core/build/impact-audio/`; current report `core/build/test-evidence/2026-10-17-motion-corrections/audio-no-ding.log`. These are file/build checks only. Detailed category/volume/contact acceptance remains open beyond Andrew's general positive report. Existing core random pitch 0.88–1.12 still applies at runtime; assess before changing it.
