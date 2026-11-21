#!/bin/sh
# Builds the two levels of the Pokémon Center's Sunny Plaza look (ROADMAP 49) and saves each as a candidate
# (build/rebuild/pokemon_center_plaza/). SHIP=1 saves them live as looks/plaza/pokemon_center(_2), once the critic
# says SHIP. MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
where=--candidate; [ -n "$SHIP" ] && where="--look plaza"
for L in 1 2; do
  id=pokemon_center; [ "$L" -gt 1 ] && id=pokemon_center_$L
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  python3 ../../rebuild.py pokemon_center_plaza "$id" $where --nbt "out/$(echo $id | tr _ -).nbt"
done
