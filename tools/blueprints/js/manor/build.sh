#!/bin/sh
# Builds the four levels of the Noble's Manor and saves each as a candidate (build/rebuild/manor/).
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
for L in 1 2 3 4; do
  id=manor; [ "$L" -gt 1 ] && id=manor_$L
  f=out/$(echo $id | tr _ -).nbt
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  if [ "$L" = 4 ]; then
    python3 ../../rebuild.py manor "$id" --candidate --nbt "$f" --basement 4 --cellar "$(cat out/cellar.txt)"
  else
    python3 ../../rebuild.py manor "$id" --candidate --nbt "$f"
  fi
done
