#!/bin/sh
# Builds both levels of the Weaver's Cottage and saves each: as a candidate (build/rebuild/weavers_cottage/) by default,
# or with LIVE=1 as the live drawing (structure/<id>.nbt and blueprint_furniture/<id>.json; ROADMAP 49.8).
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules). CUT=<y> builds cutaways only (out/, not saved).
set -e
cd "$(dirname "$0")"
mkdir -p out
where=--candidate; [ "${LIVE:-0}" = 1 ] && where=
for L in 1 2; do
  id=weavers_cottage; [ "$L" -gt 1 ] && id=weavers_cottage_$L
  f=out/$(echo $id | tr _ -).nbt
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  python3 ../../rebuild.py weavers_cottage "$id" $where --nbt "$f"
done
