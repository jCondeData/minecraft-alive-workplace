#!/bin/sh
# Builds a biome look of the Starter Cottage (ROADMAP 49.10a) and saves each level as a candidate
# (build/rebuild/<this folder's name>/). Looks are not shipped from here yet: the Java side of 49.10a decides where
# looks/<culture>/starter_cottage[_n] are saved. MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
# LEVELS="1 2 3 4" picks the levels.
set -e
cd "$(dirname "$0")"
family=$(basename "$(pwd)")
mkdir -p out
for L in ${LEVELS:-1 2 3 4}; do
  id=starter_cottage; [ "$L" -gt 1 ] && id=starter_cottage_$L
  f=out/$(echo $id | tr _ -).nbt
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955 > /dev/null
  if [ "$L" = 4 ]; then
    python3 ../../rebuild.py "$family" "$id" --candidate --nbt "$f" --basement 4 --cellar 2,3,6,7
  else
    python3 ../../rebuild.py "$family" "$id" --candidate --nbt "$f"
  fi
done
