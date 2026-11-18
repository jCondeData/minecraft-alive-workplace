#!/usr/bin/env python3
"""Adds the Traveler's Camp's map frame (frames.json, written by recipe.mjs) to a saved blueprint: rebuild.py carries
blocks only. The frame lies on top of the camp's barrel (Facing 1, up) holding an empty map, its TileX/TileY/TileZ its
own cell (TileY 2), and is the template's only entity (brief: tools/blueprints/briefs/traveller_camp.md).
Usage: frames.py <nbt>."""
import json
import os
import sys

import nbtlib
from nbtlib.tag import Byte, Compound, Double, Float, Int, List, String

FACING = {"down": 0, "up": 1, "north": 2, "south": 3, "west": 4, "east": 5}


def main(path):
    plan = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "frames.json")))
    f = nbtlib.load(path)
    root = f if "entities" in f or "blocks" in f else f[""]
    ents = []  # the frame is the only entity: anything else is dropped
    for fr in plan["frames"]:
        x, y, z = fr["at"]
        assert fr["facing"] == "up" and y == 2, fr
        ents.append(Compound({
            "pos": List[Double]([Double(x + 0.5), Double(y + 0.03125), Double(z + 0.5)]),
            "blockPos": List[Int]([Int(x), Int(y), Int(z)]),
            "nbt": Compound({
                "id": String("minecraft:item_frame"), "TileX": Int(x), "TileY": Int(y), "TileZ": Int(z),
                "Facing": Byte(FACING[fr["facing"]]), "ItemRotation": Byte(0), "ItemDropChance": Float(1.0),
                "Item": Compound({"id": String(fr["item"]), "count": Int(1)}),
            }),
        }))
    root["entities"] = List[Compound](ents)
    f.save()
    print(f"{path}: {len(ents)} frame(s)")


if __name__ == "__main__":
    main(sys.argv[1])
