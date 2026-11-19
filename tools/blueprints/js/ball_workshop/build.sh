#!/bin/sh
# Builds the Ball Workshop (one level) and saves it as a candidate (build/rebuild/ball_workshop/).
# LIVE=1 saves the live drawing (structure/ball_workshop.nbt) instead, once the critic says SHIP.
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
CAND=--candidate; [ -n "$LIVE" ] && CAND=
node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name ball_workshop --data-version 3955 > /dev/null
python3 ../../rebuild.py ball_workshop ball_workshop $CAND --nbt out/ball-workshop.nbt
