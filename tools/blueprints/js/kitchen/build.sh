#!/bin/sh
# Builds the Kitchen and saves it as a candidate (build/rebuild/kitchen/), cold larder and all.
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name kitchen --data-version 3955 > /dev/null
python3 ../../rebuild.py kitchen kitchen --candidate --nbt out/kitchen.nbt --basement 4 --cellar "$(cat out/cellar.txt)"
# A workplace's beds stay vanilla (the brief): no bed spot in the candidate's spots, including the ones detect() adds
python3 -c "
import json; p='../../../../build/rebuild/kitchen/kitchen.furniture.json'; d=json.load(open(p))
d['spots']=[s for s in d['spots'] if s['kind']!='bed']; json.dump(d, open(p,'w'), indent=1)"
