#!/bin/sh
# Builds the three levels of the Arena (ROADMAP 49 rebuild) and saves each as a candidate (build/rebuild/arena/), or
# with LIVE=1 as the live drawing. MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
where=--candidate; [ "${LIVE:-0}" = 1 ] && where=
for L in 1 2 3; do
  id=arena; [ "$L" -gt 1 ] && id=arena_$L
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  python3 ../../rebuild.py arena "$id" $where --nbt "out/$(echo $id | tr _ -).nbt"
done
