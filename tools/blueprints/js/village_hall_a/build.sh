#!/bin/sh
# Builds the three levels of Village Hall A and saves each as a candidate (build/rebuild/village_hall_a/).
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
for L in 1 2 3; do
  id=village_hall; [ "$L" -gt 1 ] && id=village_hall_$L
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  python3 ../../rebuild.py village_hall_a "$id" --candidate --nbt "out/$(echo $id | tr _ -).nbt"
done
