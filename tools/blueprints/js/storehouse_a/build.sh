#!/bin/sh
# Builds the three levels of the Storehouse (Version A, the Hoist House) and saves each as a candidate
# (build/rebuild/storehouse_a/). MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
for L in 1 2 3; do
  id=storehouse; [ "$L" -gt 1 ] && id=storehouse_$L
  f=out/$(echo $id | tr _ -).nbt
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  python3 ../../rebuild.py storehouse_a "$id" --candidate --nbt "$f" --basement 4 --cellar "$(cat out/cellar.txt)"
done
