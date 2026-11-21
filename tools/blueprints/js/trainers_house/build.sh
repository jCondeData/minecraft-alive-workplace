#!/bin/sh
# Builds the Trainer's House (one level) and saves it as a candidate (build/rebuild/trainers_house/).
# LIVE=1 saves the live drawing (structure/trainers_house.nbt) instead, once the critic says SHIP (50.12: it did).
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
CAND=--candidate; [ -n "$LIVE" ] && CAND=
node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name trainers_house --data-version 3955 > /dev/null
python3 ../../rebuild.py trainers_house trainers_house $CAND --nbt out/trainers-house.nbt
