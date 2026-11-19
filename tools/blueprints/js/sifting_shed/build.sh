#!/bin/sh
# Builds both levels of the Sifting Shed (the Tipple) and saves each as a candidate (build/rebuild/sifting_shed/).
# LIVE=1 saves the live drawings (structure/sifting_shed*.nbt) instead, once the critic says SHIP.
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
CAND=--candidate; [ -n "$LIVE" ] && CAND=
for L in 1 2; do
  id=sifting_shed; [ "$L" -gt 1 ] && id=sifting_shed_$L
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  python3 ../../rebuild.py sifting_shed "$id" $CAND --nbt "out/$(echo $id | tr _ -).nbt"
done
