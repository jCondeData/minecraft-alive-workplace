#!/bin/sh
# Builds the Terminal Nook, both drawings from one plan: the vanilla build and (TOMS=1) its Tom's Simple Storage copy,
# saved as candidates (build/rebuild/terminal_nook/ and .../modded/). LIVE=1 saves the live drawings instead
# (structure/terminal_nook.nbt and structure/modded/terminal_nook.nbt), once the critic says SHIP.
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out ../../../../build/rebuild/terminal_nook/modded
CAND=--candidate; [ -n "$LIVE" ] && CAND=
node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955 > /dev/null
python3 ../../rebuild.py terminal_nook terminal_nook $CAND --nbt out/terminal-nook.nbt
TOMS=1 node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955 > /dev/null
python3 ../../rebuild.py terminal_nook modded/terminal_nook $CAND --nbt out/terminal-nook-toms.nbt
