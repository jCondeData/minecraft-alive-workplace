#!/bin/sh
# Builds both levels of the Berry Nursery (the Rill Garden) and saves each as a candidate (build/rebuild/berry_nursery/).
# LIVE=1 saves the live drawings (structure/berry_nursery*.nbt) instead, once the critic says SHIP.
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
CAND=--candidate; [ -n "$LIVE" ] && CAND=
for L in 1 2; do
  id=berry_nursery; [ "$L" -gt 1 ] && id=berry_nursery_$L
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  python3 ../../rebuild.py berry_nursery "$id" $CAND --nbt "out/$(echo $id | tr _ -).nbt"
done
