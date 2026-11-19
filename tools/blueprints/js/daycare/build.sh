#!/bin/sh
# Builds both levels of the Daycare (the Clutch) and saves each as a candidate (build/rebuild/daycare/).
# LIVE=1 saves the live drawings (structure/daycare*.nbt) instead, once the critic says SHIP.
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
CAND=--candidate; [ -n "$LIVE" ] && CAND=
for L in 1 2; do
  id=daycare; [ "$L" -gt 1 ] && id=daycare_$L
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  python3 ../../rebuild.py daycare "$id" $CAND --nbt "out/$(echo $id | tr _ -).nbt"
done
