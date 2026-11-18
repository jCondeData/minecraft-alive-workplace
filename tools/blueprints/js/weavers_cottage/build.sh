#!/bin/sh
# Builds both levels of the Weaver's Cottage and saves each as a candidate (build/rebuild/weavers_cottage/).
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules). CUT=<y> builds cutaways only (out/, not saved).
set -e
cd "$(dirname "$0")"
mkdir -p out
for L in 1 2; do
  id=weavers_cottage; [ "$L" -gt 1 ] && id=weavers_cottage_$L
  f=out/$(echo $id | tr _ -).nbt
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  python3 ../../rebuild.py weavers_cottage "$id" --candidate --nbt "$f"
done
