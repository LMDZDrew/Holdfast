"""Extract isolated user-supplied impacts into a local Minecraft 1.20.1 resource pack.

Usage: python art/audio/build_impact_pack.py SOURCE_DIRECTORY OUTPUT.zip [--preview-dir DIRECTORY]
Requires numpy and soundfile with MP3 decoding and Vorbis encoding. Sources remain untouched.
The checked-in cut list is editable source; no recordings are committed or downloaded.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import zipfile

import numpy as np
import soundfile as sf


def sha(data):
    return hashlib.sha256(data).hexdigest().upper()


def stable_ogg(data, serial):
    """Canonicalize libsndfile's random stream serial and repair each Ogg page CRC."""
    result = bytearray(data)
    offset = 0
    while offset < len(result):
        if result[offset:offset + 4] != b"OggS":
            raise ValueError("Invalid Ogg page")
        count = result[offset + 26]
        end = offset + 27 + count + sum(result[offset + 27:offset + 27 + count])
        result[offset + 14:offset + 18] = serial.to_bytes(4, "little")
        result[offset + 22:offset + 26] = b"\0" * 4
        crc = 0
        for value in result[offset:end]:
            crc ^= value << 24
            for _ in range(8):
                crc = ((crc << 1) ^ (0x04C11DB7 if crc & 0x80000000 else 0)) & 0xFFFFFFFF
        result[offset + 22:offset + 26] = crc.to_bytes(4, "little")
        offset = end
    return bytes(result)


def build(source_dir, output, preview_dir=None):
    source_dir, output = Path(source_dir).resolve(), Path(output).resolve()
    recipe_path = Path(__file__).with_name("impact_cuts.json")
    recipe = json.loads(recipe_path.read_text(encoding="utf-8"))
    sources = {}
    for name in sorted({cut["source"] for cut in recipe["cuts"]}):
        path = source_dir / name
        if output == path.resolve():
            raise ValueError("Output must not overwrite a source recording")
        data, rate = sf.read(path, dtype="float64", always_2d=True)
        if not len(data) or not np.isfinite(data).all():
            raise ValueError(f"Empty or non-finite source: {name}")
        sources[name] = (data, rate, sha(path.read_bytes()))

    files, measurements = {}, []
    if preview_dir:
        preview_dir = Path(preview_dir)
        preview_dir.mkdir(parents=True, exist_ok=True)
    for cut in recipe["cuts"]:
        data, rate, digest = sources[cut["source"]]
        start, end = round(cut["start"] * rate), round(cut["end"] * rate)
        if not 0 <= start < end < len(data):
            raise ValueError(f"Cut must be a proper subrange: {cut['id']}")
        # Mono for positional Minecraft audio. Gain reduction happens in float before encoding;
        # decoded MP3 overshoots therefore are not clipped during the conversion.
        clip = data[start:end].mean(axis=1).copy()
        clip -= clip.mean()
        peak = float(np.abs(clip).max())
        if peak < 1e-6:
            raise ValueError(f"Silent cut: {cut['id']}")
        gain = min(1.0, 10 ** (-6 / 20) / peak)
        clip *= gain
        # Tiny onset ramp preserves the transient; longer tail ramp avoids abrupt cut clicks.
        attack, release = min(round(rate * .001), len(clip)), min(round(rate * .025), len(clip))
        clip[:attack] *= np.linspace(0, 1, attack)
        clip[-release:] *= np.linspace(1, 0, release)
        encoded = io.BytesIO()
        sf.write(encoded, clip, rate, format="OGG", subtype="VORBIS")
        ogg = stable_ogg(encoded.getvalue(), int(sha(cut["id"].encode())[:8], 16))
        decoded, decoded_rate = sf.read(io.BytesIO(ogg))
        if (decoded_rate != rate or len(decoded) != len(clip) or not np.isfinite(decoded).all()
                or np.max(np.abs(decoded)) >= .95):
            raise ValueError(f"Unsafe encoded output: {cut['id']}")
        files[f"assets/holdfast_core/sounds/user_impacts/{cut['id']}.ogg"] = ogg
        measurements.append({**cut, "source_sha256": digest, "source_duration": len(data) / rate,
                             "sample_rate": rate, "channels": 1, "duration": len(clip) / rate,
                             "gain_db": float(20 * np.log10(gain)), "decoded_peak": float(np.abs(decoded).max()),
                             "rms_dbfs": float(20 * np.log10(np.sqrt(np.mean(decoded ** 2)))),
                             "ogg_sha256": sha(ogg)})
        if preview_dir:
            sf.write(preview_dir / f"{cut['id']}.wav", clip, rate, subtype="PCM_16")

    events = {}
    ids = {cut["id"] for cut in recipe["cuts"]}
    for event, variants in recipe["events"].items():
        if not variants or any(v not in ids for v in variants):
            raise ValueError(f"Invalid event pool: {event}")
        events[event] = {"replace": True, "subtitle": f"subtitles.holdfast_core.{event}",
                         "sounds": [{"name": f"holdfast_core:user_impacts/{variant}"} for variant in variants]}
    report = {"recipe_sha256": sha(recipe_path.read_bytes()), "cuts": measurements,
              "events": recipe["events"], "processing": "mono downmix, DC removal, reduce-only -6 dBFS peak ceiling, 1ms/25ms fades, Vorbis",
              "review": "Waveform selection and decode validation only; listening and in-game acceptance pending.",
              "routing": "Core sends contact families only: axes/hatchets/swords share slash; blade skull kills share one pool.",
              "provenance": "Supplied by Andrew for local use; original authors and redistribution rights not established."}
    files["pack.mcmeta"] = json.dumps({"pack": {"pack_format": 15,
        "description": "Holdfast isolated impact cuts - Andrew's reference recordings"}}).encode()
    files["assets/holdfast_core/sounds.json"] = json.dumps(events, indent=2).encode()
    files["holdfast-impact-provenance.json"] = json.dumps(report, indent=2).encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w") as pack:
        for name, content in sorted(files.items()):
            entry = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            pack.writestr(entry, content)
    print(json.dumps(report, indent=2))
    print("output_sha256", sha(output.read_bytes()))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_dir")
    parser.add_argument("output")
    parser.add_argument("--preview-dir")
    args = parser.parse_args()
    build(args.source_dir, args.output, args.preview_dir)
