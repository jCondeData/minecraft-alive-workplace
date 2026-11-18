#!/bin/sh
# Builds the three levels of Inn A and saves each as a candidate (build/rebuild/inn_a/).
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
for L in 1 2 3; do
  id=inn; [ "$L" -gt 1 ] && id=inn_$L
  f=out/$(echo $id | tr _ -).nbt
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  if [ "$L" = 3 ]; then
    python3 ../../rebuild.py inn_a "$id" --candidate --nbt "$f" --basement 4 --cellar "$(cat out/cellar.txt)"
  else
    python3 ../../rebuild.py inn_a "$id" --candidate --nbt "$f"
  fi
done
