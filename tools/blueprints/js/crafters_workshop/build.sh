#!/bin/sh
# Builds the Crafter's Workshop and saves it as a candidate (build/rebuild/crafters_workshop/).
# LIVE=1 saves the live drawing (structure/crafters_workshop.nbt) instead, once the critic says SHIP.
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
CAND=--candidate; [ -n "$LIVE" ] && CAND=
node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name crafters_workshop --data-version 3955 > /dev/null
python3 ../../rebuild.py crafters_workshop crafters_workshop $CAND --nbt out/crafters-workshop.nbt
