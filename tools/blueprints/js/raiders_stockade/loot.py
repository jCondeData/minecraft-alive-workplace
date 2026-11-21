#!/usr/bin/env python3
"""Writes the loot chest's LootTable into a saved Raiders' Stockade .nbt (rebuild.py carries no block-entity data):
the plain chest at CaravanTrail.LOOT_CHEST (7, 1, 14) gets aliveworkplace:chests/bandit_camp. Fails if that cell
holds no chest or if any other plain chest is in the build (the story takes the nearest one).

    python3 loot.py <path/to/raiders_stockade.nbt>
"""
import sys
import nbtlib
from nbtlib.tag import Compound, String

CHEST = (7, 1, 14)
TABLE = "aliveworkplace:chests/bandit_camp"


def main(path):
    f = nbtlib.load(path)
    palette = f["palette"]
    chests = [b for b in f["blocks"] if str(palette[int(b["state"])]["Name"]) == "minecraft:chest"]
    at = [b for b in chests if tuple(int(v) for v in b["pos"]) == CHEST]
    if len(chests) != 1 or len(at) != 1:
        sys.exit(f"expected exactly one plain chest, at {CHEST}; found {[tuple(int(v) for v in b['pos']) for b in chests]}")
    at[0]["nbt"] = Compound({"LootTable": String(TABLE), "id": String("minecraft:chest")})
    f.save()
    print(f"loot table {TABLE} written to the chest at {CHEST}")


if __name__ == "__main__":
    main(sys.argv[1])
