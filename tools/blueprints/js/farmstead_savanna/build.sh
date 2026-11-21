#!/bin/sh
# Builds a biome look of the Farmstead (ROADMAP 49.10e, as 49.10a) and saves each level as a candidate
# (build/rebuild/<this folder's name>/). MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
# LEVELS="1 2 3" picks the levels. SHIP=1 saves them live as looks/<culture>/farmstead[_n] (the ship step).
# CUT=<y> with the runner alone (npm run cut) makes cutaways in out/, not saved.
set -e
cd "$(dirname "$0")"
family=$(basename "$(pwd)")
mkdir -p out
where=--candidate; [ -n "$SHIP" ] && where="--look ${family#farmstead_}"
for L in ${LEVELS:-1 2 3}; do
  id=farmstead; [ "$L" -gt 1 ] && id=farmstead_$L
  f=out/$(echo $id | tr _ -).nbt
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  if [ "$L" = 3 ]; then
    python3 ../../rebuild.py "$family" "$id" $where --nbt "$f" --basement 4 --cellar "$(cat out/cellar.txt)"
  else
    python3 ../../rebuild.py "$family" "$id" $where --nbt "$f"
  fi
done
