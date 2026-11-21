#!/bin/sh
# Builds both levels of the Smithy and saves each as a candidate (build/rebuild/smithy/), cellar and all.
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
for L in 1 2; do
  id=smithy; [ "$L" -gt 1 ] && id=smithy_$L
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null

  python3 ../../rebuild.py smithy "$id" --candidate --nbt "out/$(echo $id | tr _ -).nbt" --basement 4 --cellar "$(cat out/cellar.txt)"
done
