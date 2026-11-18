#!/bin/sh
# Builds both levels of the Fletcher's Lodge and saves each as a candidate (build/rebuild/fletchers_lodge/).
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
for L in 1 2; do
  id=fletchers_lodge; [ "$L" -gt 1 ] && id=fletchers_lodge_$L
  COUNT=1 LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  python3 ../../rebuild.py fletchers_lodge "$id" --candidate --nbt "out/$(echo $id | tr _ -).nbt"
done
