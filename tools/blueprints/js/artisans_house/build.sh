#!/bin/sh
# Builds the three levels of the Artisan's House (ROADMAP 49.8) and saves each: as a candidate
# (build/rebuild/artisans_house/) by default, or with LIVE=1 as the live drawing (structure/<id>.nbt and
# blueprint_furniture/<id>.json). MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
where=--candidate; [ "${LIVE:-0}" = 1 ] && where=
for L in 1 2 3; do
  id=artisans_house; [ "$L" -gt 1 ] && id=artisans_house_$L
  f=out/$(echo $id | tr _ -).nbt
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  if [ "$L" = 3 ]; then
    python3 ../../rebuild.py artisans_house "$id" $where --nbt "$f" --basement 4 --cellar "$(cat out/cellar.txt)"
  else
    python3 ../../rebuild.py artisans_house "$id" $where --nbt "$f"
  fi
done
