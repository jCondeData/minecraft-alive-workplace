#!/bin/sh
# Builds a biome look of the Stone House (ROADMAP 49.10b) and saves each level as a candidate
# (build/rebuild/<this folder's name>/). MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
# LEVELS="1 2 3 4" picks the levels. SHIP=1 saves them live as looks/<culture>/stone_house[_n] (49.10a's way).
set -e
cd "$(dirname "$0")"
family=$(basename "$(pwd)")
mkdir -p out
where=--candidate; [ -n "$SHIP" ] && where="--look ${family#stone_house_}"
for L in ${LEVELS:-1 2 3 4}; do
  id=stone_house; [ "$L" -gt 1 ] && id=stone_house_$L
  f=out/$(echo $id | tr _ -).nbt
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  if [ "$L" = 4 ]; then
    python3 ../../rebuild.py "$family" "$id" $where --nbt "$f" --basement 4 --cellar "$(cat out/cellar.txt)"
  else
    python3 ../../rebuild.py "$family" "$id" $where --nbt "$f"
  fi
done
