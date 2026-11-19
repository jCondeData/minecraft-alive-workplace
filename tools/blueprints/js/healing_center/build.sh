#!/bin/sh
# Builds every level of the Healing Center (the Spring Infirmary) and saves each as a candidate (build/rebuild/healing_center/).
# LIVE=1 saves the live drawings (structure/healing_center*.nbt) instead, once the critic says SHIP (50.8: it did).
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
CAND=--candidate; [ -n "$LIVE" ] && CAND=
for L in 1 2 3 4; do
  id=healing_center; [ "$L" -gt 1 ] && id=healing_center_$L
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  if [ "$L" = 4 ]; then
    python3 ../../rebuild.py healing_center "$id" $CAND --nbt "out/$(echo $id | tr _ -).nbt" --basement 4 --cellar "$(cat out/cellar.txt)"
  else
    python3 ../../rebuild.py healing_center "$id" $CAND --nbt "out/$(echo $id | tr _ -).nbt"
  fi
done
