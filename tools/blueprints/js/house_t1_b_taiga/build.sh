#!/bin/sh
# Builds a biome look of the Daub House (ROADMAP 49.10d, as 49.10a) and saves it as a candidate
# (build/rebuild/<this folder's name>/house_t1_b.nbt). MC_BUILDER = the minecraft-builder skill's folder (with
# node_modules). The Daub House has one level. SHIP=1 saves it live as looks/<culture>/house_t1_b (the ship step).
set -e
cd "$(dirname "$0")"
family=$(basename "$(pwd)")
mkdir -p out
where=--candidate; [ -n "$SHIP" ] && where="--look ${family#house_t1_b_}"
id=house_t1_b
node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
python3 ../../rebuild.py "$family" "$id" $where --nbt out/house-t1-b.nbt
