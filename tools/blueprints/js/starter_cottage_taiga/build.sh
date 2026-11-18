#!/bin/sh
# Builds the drawn levels of the Starter Cottage's taiga look (ROADMAP 49.10a) and saves each as a candidate
# (build/rebuild/starter_cottage_taiga/<id>; in game: looks/taiga/<id>). MC_BUILDER = the minecraft-builder skill's
# folder (with node_modules). LEVELS defaults to the drawn ones (1 2).
set -e
cd "$(dirname "$0")"
mkdir -p out
for L in ${LEVELS:-1 2 3 4}; do
  id=starter_cottage; [ "$L" -gt 1 ] && id=starter_cottage_$L
  f=out/$(echo $id | tr _ -).nbt
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955 > /dev/null
  if [ "$L" = 4 ]; then
    python3 ../../rebuild.py "$(basename "$PWD")" "$id" --candidate --nbt "$f" --basement 4 --cellar 2,3,6,7
  else
    python3 ../../rebuild.py "$(basename "$PWD")" "$id" --candidate --nbt "$f"
  fi
done
