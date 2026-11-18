#!/usr/bin/env python3
"""Saves the Bandit Camp's recipe output as the live camp/bandit_camp (or, with --candidate, beside the critic's) the
way rebuild.py does, with the two things rebuild.py can't carry (brief: tools/blueprints/briefs/bandit_camp.md, "Ship
step"):

- the loot chest's block entity: `LootTable "aliveworkplace:chests/bandit_camp"` on the chest at (8, 1, 11) (Lairs.found
  gives the culture's loot only to a container that already has one; the King's camps and the bounty hideout get only
  this). The stash chest (13, 1, 10) gets none.
- the ground layer's undrawn cells left out of the file, never saved as air: air at y = 0 would dig a hole in the
  world's ground where the camp is set down (legend_sites._keep_world_ground does the same).

Usage: ship.py <recipe .nbt> [--candidate]
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", ".."))

from nbtlib.tag import Compound, String  # noqa: E402

import furniture  # noqa: E402
from kit import MAIN_STRUCTURES  # noqa: E402
from rebuild import CANDIDATES, load_recipe  # noqa: E402

LOOT_CHEST = (8, 1, 11)
LOOT_TABLE = "aliveworkplace:chests/bandit_camp"


def main(argv):
    nbt = argv[0]
    candidate = "--candidate" in argv[1:]
    b = load_recipe(nbt, os.path.join(HERE, "furniture.json"))
    assert b.blocks.get(LOOT_CHEST, ("",))[0] == "minecraft:chest", f"no chest at {LOOT_CHEST}"
    b.set_nbt(*LOOT_CHEST, Compound({"id": String("minecraft:chest"), "LootTable": String(LOOT_TABLE)}))
    for x in range(b.w):
        for z in range(b.d):
            if b.blocks.get((x, 0, z), ("",))[0] == "minecraft:air":
                del b.blocks[(x, 0, z)]
    folder = os.path.join(CANDIDATES, "bandit_camp") if candidate else MAIN_STRUCTURES
    b.save(folder, "camp/bandit_camp")
    if candidate:  # outside structure/, so save() wrote no spots file: kept beside the candidate (as rebuild.py does)
        with open(os.path.join(folder, "camp", "bandit_camp.furniture.json"), "w") as f:
            json.dump({"spots": getattr(b, "spots", []) + furniture.detect(b)}, f, indent=1, default=list)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
