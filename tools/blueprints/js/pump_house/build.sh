#!/bin/sh
# Builds the Pump House (Create only) and saves it as a candidate (build/rebuild/pump_house/).
# LIVE=1 saves the live drawing instead, once the critic says SHIP. CUT=<y> writes a cutaway .nbt only.
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
if [ -n "$CUT" ]; then
  node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "pump_house_cut$CUT" --data-version 3955 > /dev/null
  exit 0
fi
CAND=--candidate; [ -n "$LIVE" ] && CAND=
node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name pump_house --data-version 3955 > /dev/null
python3 ../../rebuild.py pump_house pump_house $CAND --nbt out/pump-house.nbt
