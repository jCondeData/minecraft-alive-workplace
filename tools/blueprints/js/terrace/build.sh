#!/bin/sh
# Builds both levels of the Terrace (the Crow-Step Row) and saves each as a candidate (build/rebuild/terrace/).
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
for L in 1 2; do
  id=terrace; [ "$L" -gt 1 ] && id=terrace_$L
  f=out/$(echo $id | tr _ -).nbt
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  python3 ../../rebuild.py terrace "$id" --candidate --nbt "$f"
done
