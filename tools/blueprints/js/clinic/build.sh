#!/bin/sh
# Builds the Clinic and saves it as a candidate (build/rebuild/clinic/).
# LIVE=1 saves the live drawing (structure/clinic.nbt) instead, once the critic says SHIP (50.8: it did).
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
CAND=--candidate; [ -n "$LIVE" ] && CAND=
node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name clinic --data-version 3955 > /dev/null
python3 ../../rebuild.py clinic clinic $CAND --nbt out/clinic.nbt
