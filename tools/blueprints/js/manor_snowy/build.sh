#!/bin/sh
# Builds a biome look of the Noble's Manor (ROADMAP 49.10f) and saves each level as a candidate
# (build/rebuild/<this folder's name>/). MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
# LEVELS="1 2 3 4" picks the levels. SHIP=1 saves them live as looks/<culture>/manor[_n] (49.10a's way).
set -e
cd "$(dirname "$0")"
family=$(basename "$(pwd)")
mkdir -p out
where=--candidate; [ -n "$SHIP" ] && where="--look ${family#manor_}"
for L in ${LEVELS:-1 2 3 4}; do
  id=manor; [ "$L" -gt 1 ] && id=manor_$L
  f=out/$(echo $id | tr _ -).nbt
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  if [ "$L" = 4 ]; then
    python3 ../../rebuild.py "$family" "$id" $where --nbt "$f" --basement 4 --cellar "$(cat out/cellar.txt)"
  else
    python3 ../../rebuild.py "$family" "$id" $where --nbt "$f"
  fi
done
