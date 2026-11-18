#!/bin/sh
# Builds the three levels of the Jeweler's Workshop and saves each as a candidate (build/rebuild/jewellers_workshop/);
# III with its Deep Vault. LIVE=1 saves the live drawings (structure/jewellers_workshop*.nbt) instead, once the critic
# says SHIP. MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
CAND=--candidate; [ -n "$LIVE" ] && CAND=
for L in 1 2 3; do
  id=jewellers_workshop; [ "$L" -gt 1 ] && id=jewellers_workshop_$L
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  if [ "$L" -eq 3 ]; then
    python3 ../../rebuild.py jewellers_workshop "$id" $CAND --nbt "out/$(echo $id | tr _ -).nbt" --basement 4 --cellar "$(cat out/cellar.txt)"
  else
    python3 ../../rebuild.py jewellers_workshop "$id" $CAND --nbt "out/$(echo $id | tr _ -).nbt"
  fi
done
