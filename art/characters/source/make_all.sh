#!/usr/bin/env bash
# Regenerate every character asset, check it, and copy finished files into export/ and preview/.
# Requires Python 3.10+ with numpy, scipy and Pillow (matplotlib not needed).
set -euo pipefail
cd "$(dirname "$0")"
ART=..
python3 build_model.py
python3 walker_anims.py
python3 placement.py > /dev/null
python3 player_anims.py | tee "$ART/build/solver_report.txt" | tail -1

A="$ART/export/assets/holdfast"
rm -rf "$ART/export"
mkdir -p "$A/geo" "$A/animations" "$A/textures/entity/walker" "$A/textures/entity/player" \
         "$A/player_animation" "$A/player_animation_extras"
cp "$ART/build/walker.geo.json" "$ART/build/walker_crawler.geo.json" "$A/geo/"
cp "$ART/build/walker.animation.json" "$ART/build/walker_crawler.animation.json" "$A/animations/"
for o in farmer tshirt scrubs office; do cp "$ART/build/walker_$o.png" "$A/textures/entity/walker/$o.png"; done
cp "$ART/build/survivor_default.png" "$A/textures/entity/player/survivor_default.png"
cp "$ART/build"/player_animation/*.json "$A/player_animation/"
cp "$ART/build"/player_animation_extras/*.json "$A/player_animation_extras/"

mkdir -p "$ART/preview/rig"
cp "$ART/build/survivor_preview.geo.json" "$ART/build/survivor_preview.png" "$ART/build/player_keys.animation.json" \
   "$ART/build/pairing.json" "$ART/build/gait_speeds.json" "$ART/build/player_move_speeds.json" "$ART/preview/rig/"
python3 validate_exports.py "$A"
python3 check_carries.py
python3 check_locomotion.py
python3 pa_item.py | tail -1
python3 gen_testmod_clips.py
python3 gen_test_items.py
python3 build_preview.py
