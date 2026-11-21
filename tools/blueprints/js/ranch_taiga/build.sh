#!/bin/sh
# Builds a biome look of the Ranch (ROADMAP 49.10e, as 49.10a) and saves each level as a candidate
# (build/rebuild/<this folder's name>/). MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
# LEVELS="1 2" picks the levels. SHIP=1 saves them live as looks/<culture>/ranch[_2] (the ship step).
set -e
cd "$(dirname "$0")"
family=$(basename "$(pwd)")
mkdir -p out
where=--candidate; [ -n "$SHIP" ] && where="--look ${family#ranch_}"
for L in ${LEVELS:-1 2}; do
  id=ranch; [ "$L" -gt 1 ] && id=ranch_$L
  f=out/$(echo $id | tr _ -).nbt
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  python3 ../../rebuild.py "$family" "$id" $where --nbt "$f"
done
