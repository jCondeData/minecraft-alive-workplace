#!/bin/sh
# Builds a biome look of the Weaver's Cottage (ROADMAP 49.10d, as 49.10a) and saves each level as a candidate
# (build/rebuild/<this folder's name>/). MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
# LEVELS="1 2" picks the levels. SHIP=1 saves them live as looks/<culture>/weavers_cottage[_2] (the ship step).
set -e
cd "$(dirname "$0")"
family=$(basename "$(pwd)")
mkdir -p out
where=--candidate; [ -n "$SHIP" ] && where="--look ${family#weavers_cottage_}"
for L in ${LEVELS:-1 2}; do
  id=weavers_cottage; [ "$L" -gt 1 ] && id=weavers_cottage_$L
  f=out/$(echo $id | tr _ -).nbt
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  python3 ../../rebuild.py "$family" "$id" $where --nbt "$f"
done
