#!/bin/sh
# Builds both levels of the Royal Hall and saves each as a candidate (build/rebuild/royal_hall/); LIVE=1 saves them
# as the live realm/ structures. MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
for L in 1 2; do
  id=royal_hall; [ "$L" -gt 1 ] && id=royal_hall_$L
  f=out/$(echo $id | tr _ -).nbt
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  if [ -n "$LIVE" ]; then
    python3 ../../rebuild.py royal_hall "realm/$id" --nbt "$f"
  else
    python3 ../../rebuild.py royal_hall "$id" --candidate --nbt "$f"
  fi
done
