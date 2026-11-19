#!/bin/sh
# Builds the two levels of the Pokémon Center ("The Red Dome", ROADMAP 49) and saves each as a candidate
# (build/rebuild/pokemon_center/). LIVE=1 saves the live drawings (structure/pokemon_center(_2).nbt) instead, once the
# critic says SHIP. MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
CAND=--candidate; [ -n "$LIVE" ] && CAND=
for L in 1 2; do
  id=pokemon_center; [ "$L" -gt 1 ] && id=pokemon_center_$L
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  python3 ../../rebuild.py pokemon_center "$id" $CAND --nbt "out/$(echo $id | tr _ -).nbt"
done
