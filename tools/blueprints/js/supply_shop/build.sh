#!/bin/sh
# Builds the four levels of the Supply Shop and saves each as a candidate (build/rebuild/supply_shop/).
# LIVE=1 saves the live drawings (structure/supply_shop*.nbt) instead, once the critic says SHIP.
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
CAND=--candidate; [ -n "$LIVE" ] && CAND=
for L in 1 2 3 4; do
  id=supply_shop; [ "$L" -gt 1 ] && id=supply_shop_$L
  LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "$id" --data-version 3955 > /dev/null
  if [ "$L" -eq 4 ]; then
    python3 ../../rebuild.py supply_shop "$id" $CAND --nbt "out/$(echo $id | tr _ -).nbt" --basement 4 --cellar "$(cat out/cellar.txt)"
  else
    python3 ../../rebuild.py supply_shop "$id" $CAND --nbt "out/$(echo $id | tr _ -).nbt"
  fi
done
# The shop has no bed spot (brief; furniture.py drops a workplace's beds when it ships): keep the candidates' spot
# files the same as what ships.
[ -n "$LIVE" ] || python3 - <<'PY'
import glob, json
for f in glob.glob("../../../../build/rebuild/supply_shop/supply_shop*.furniture.json"):
    d = json.load(open(f))
    d["spots"] = [s for s in d["spots"] if s["kind"] != "bed"]
    json.dump(d, open(f, "w"), indent=1)
PY
