#!/bin/sh
# Builds the Ferry House and saves it as a candidate (build/rebuild/ferry_house/).
# LIVE=1 saves the live drawing (structure/ferry_house.nbt) instead, once the critic says SHIP.
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
CAND=--candidate; [ -n "$LIVE" ] && CAND=
node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name ferry_house --data-version 3955 > /dev/null
python3 ../../rebuild.py ferry_house ferry_house $CAND --nbt out/ferry-house.nbt
