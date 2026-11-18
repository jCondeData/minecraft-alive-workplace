#!/bin/sh
# Builds both levels of the Schoolhouse and saves each as a candidate (build/rebuild/schoolhouse/).
set -e
cd "$(dirname "$0")"
mkdir -p out
for L in 1 2; do
  id=schoolhouse; [ "$L" -gt 1 ] && id=schoolhouse_$L
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  python3 ../../rebuild.py schoolhouse "$id" --candidate --nbt "out/$(echo $id | tr _ -).nbt"
done
