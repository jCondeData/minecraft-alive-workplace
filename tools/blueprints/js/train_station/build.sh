#!/bin/sh
# Builds the Train Station (Create only) and saves it as a candidate (build/rebuild/train_station/).
# LIVE=1 saves the live drawing (structure/train_station.nbt) instead, once the critic says SHIP.
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
CAND=--candidate; [ -n "$LIVE" ] && CAND=
node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name train_station --data-version 3955 > /dev/null
python3 ../../rebuild.py train_station train_station $CAND --nbt out/train-station.nbt
