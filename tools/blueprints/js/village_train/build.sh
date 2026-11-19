#!/bin/sh
# Builds the Village Train (Create only) and saves it as a candidate (build/rebuild/village_train/train/).
# LIVE=1 saves the live drawing (structure/train/village_train.nbt) instead, once the critic says SHIP (and
# generate.py's create_builds.village_train() call is retired in the same commit). CUT=<y> writes a cutaway .nbt only.
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
if [ -n "$CUT" ]; then
  node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "village_train_cut$CUT" --data-version 3955 > /dev/null
  exit 0
fi
CAND=--candidate; [ -n "$LIVE" ] && CAND=
[ -z "$LIVE" ] && mkdir -p ../../../../build/rebuild/village_train/train
node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name village_train --data-version 3955 > /dev/null
python3 ../../rebuild.py village_train train/village_train $CAND --nbt out/village-train.nbt
