#!/bin/sh
# Builds both levels' candidates (and CUT=<y> cutaways with ./build.sh cut <y>). MC_BUILDER = the minecraft-builder skill's folder.
set -e
cd "$(dirname "$0")"
for L in 1 2; do
  id=nether_gate; [ $L = 2 ] && id=nether_gate_2
  rm -rf out; LEVEL=$L node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955 >/dev/null
  python3 ../../rebuild.py nether_gate $id --candidate
done
