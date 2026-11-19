#!/bin/sh
# Builds the three levels of the Market Stall and saves each as a candidate (build/rebuild/market_stall/).
# LIVE=1 saves the live drawings (structure/market_stall*.nbt) instead, once the critic says SHIP.
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
CAND=--candidate; [ -n "$LIVE" ] && CAND=
for L in 1 2 3; do
  id=market_stall; [ "$L" -gt 1 ] && id=market_stall_$L
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  python3 ../../rebuild.py market_stall "$id" $CAND --nbt "out/$(echo $id | tr _ -).nbt"
done
