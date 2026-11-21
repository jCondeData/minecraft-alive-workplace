#!/bin/sh
# Builds the Butcher's Shop and saves it as a candidate (build/rebuild/butchers_shop/), freezer and all.
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name butchers_shop --data-version 3955 > /dev/null
python3 ../../rebuild.py butchers_shop butchers_shop --candidate --nbt out/butchers-shop.nbt --basement 4 --cellar "$(cat out/cellar.txt)"
