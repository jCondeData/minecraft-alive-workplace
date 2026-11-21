#!/bin/sh
# Builds the four levels of the Starter Cottage (Version B, the owner's pick, ROADMAP 49.3) and saves each: as a
# candidate (build/rebuild/starter_cottage_b/) by default, or with LIVE=1 as the live drawing (structure/<id>.nbt and
# blueprint_furniture/<id>.json). MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
where=--candidate; [ "${LIVE:-0}" = 1 ] && where=
for L in 1 2 3 4; do
  id=starter_cottage; [ "$L" -gt 1 ] && id=starter_cottage_$L
  f=out/$(echo $id | tr _ -).nbt
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955 > /dev/null
  if [ "$L" = 4 ]; then
    python3 ../../rebuild.py starter_cottage_b "$id" $where --nbt "$f" --basement 4 --cellar 2,3,6,7
  else
    python3 ../../rebuild.py starter_cottage_b "$id" $where --nbt "$f"
  fi
done
