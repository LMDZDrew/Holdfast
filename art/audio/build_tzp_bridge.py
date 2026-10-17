"""Build an audio-only Holdfast resource pack from an installed Tissou's Zombie Pack 2.7 ZIP.

No third-party audio is committed or downloaded by this script. Supply the original pack explicitly.
The output includes the original referenced zombie Oggs, vanilla events, core aliases and attribution.
Usage: python build_tzp_bridge.py original.zip output.zip
"""
import hashlib
import json
from pathlib import Path
import sys
import zipfile

SOURCE_URL = "https://www.curseforge.com/minecraft/texture-packs/tissous-zombie-pack-optifine-1-7x-1-20"
ALIASES = {
    "walker.groan": "entity.zombie.ambient",
    "walker.breath": "entity.zombie.ambient",
    "walker.snarl": "entity.zombie.hurt",
    "walker.lunge": "entity.zombie.hurt",
    "walker.bite": "entity.zombie.hurt",
    "walker.death": "entity.zombie.death",
}


def build(source, output):
    source, output = Path(source), Path(output)
    if source.resolve() == output.resolve():
        raise ValueError("Output must not overwrite the original pack")
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    with zipfile.ZipFile(source) as original:
        definitions = json.loads(original.read("assets/minecraft/sounds.json"))
        events = {name: definitions[name] for name in sorted(set(ALIASES.values()))}
        files = {}
        missing = []
        for event in events.values():
            event["replace"] = True
            valid = []
            for entry in event["sounds"]:
                name = entry if isinstance(entry, str) else entry["name"]
                namespace, path = name.split(":", 1) if ":" in name else ("minecraft", name)
                filename = f"assets/{namespace}/sounds/{path}.ogg"
                if filename not in original.namelist():
                    missing.append(filename)
                    continue
                data = original.read(filename)
                if not data.startswith(b"OggS"):
                    raise ValueError(f"Not Ogg: {filename}")
                # The original pack uses moreZsounds, which strict 1.20.1 rejects. Normalize both references and
                # ZIP member names together, retaining the original sample bytes.
                files[filename.lower()] = filename
                if isinstance(entry, str):
                    valid.append(entry.lower())
                else:
                    valid.append(dict(entry, name=entry["name"].lower()))
            if not valid:
                raise ValueError("Source event has no playable sounds")
            event["sounds"] = valid
        aliases = {name: {"replace": True, "subtitle": f"subtitles.holdfast_core.{name}",
                          "sounds": [{"name": f"minecraft:{event}", "type": "event"}]}
                   for name, event in ALIASES.items()}
        attribution = {"source": SOURCE_URL, "author": "Tissou", "source_file": source.name,
                       "source_sha256": digest, "purpose": "Audio-only local Holdfast integration; no show-audio provenance claimed",
                       "events": {name: len(event["sounds"]) for name, event in events.items()},
                       "aliases": ALIASES, "ogg_count": len(files), "normalized_resource_paths": True,
                       "excluded_missing_source_files": missing}
        output.parent.mkdir(parents=True, exist_ok=True)
        # Stable timestamps allow a repeated build to have the same SHA-256.
        with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as target:
            def write(name, data):
                info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                target.writestr(info, data)
            write("pack.mcmeta", json.dumps({"pack": {"pack_format": 15,
                  "description": "Holdfast walker audio — Tissou's Zombie Pack 2.7 (credit: Tissou)"}}))
            write("assets/minecraft/sounds.json", json.dumps(events, indent=2))
            write("assets/holdfast_core/sounds.json", json.dumps(aliases, indent=2))
            write("holdfast-audio-provenance.json", json.dumps(attribution, indent=2))
            write("CREDITS.txt", f"Walker audio: Tissou's Zombie Pack 2.7, by Tissou.\n{SOURCE_URL}\n"
                  "Audio-only adaptation for local Holdfast use. Original assets remain credited to their authors.\n"
                  "Do not treat this adaptation as rights clearance for unrelated source recordings.\n")
            for filename in sorted(files):
                write(filename, original.read(files[filename]))
    print(json.dumps(attribution, indent=2))
    print("output_sha256", hashlib.sha256(output.read_bytes()).hexdigest())


if __name__ == "__main__":
    build(*sys.argv[1:])
