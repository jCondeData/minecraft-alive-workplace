#!/bin/sh
# Builds the Physician's House and saves it as a candidate (build/rebuild/physicians_house/).
# LIVE=1 saves the live drawing (structure/physicians_house.nbt) instead, once the critic says SHIP (50.8: it did).
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
CAND=--candidate; [ -n "$LIVE" ] && CAND=
node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name physicians-house --data-version 3955 > /dev/null
python3 ../../rebuild.py physicians_house physicians_house $CAND --nbt out/physicians-house.nbt
