#!/usr/bin/env python3
"""Adds the plan's charter frames (frames_<id>.json, written by recipe.mjs) to a saved Guildhall blueprint as empty
item_frame entities: rebuild.py carries blocks only. Usage: frames.py <id> <nbt>."""
import json, os, sys
import nbtlib
from nbtlib.tag import Byte, Compound, Double, Float, Int, List, String

FACING = {"north": 2, "south": 3, "west": 4, "east": 5}
STEP = {"north": (0, 1), "south": (0, -1), "west": (1, 0), "east": (-1, 0)}  # toward the wall it hangs on


def main(ident, path):
    plan = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), f"frames_{ident}.json")))
    f = nbtlib.load(path)
    root = f if "entities" in f or "blocks" in f else f[""]
    ents = [e for e in root.get("entities", []) if e["nbt"]["id"] != "minecraft:item_frame"]
    for fr in plan["frames"]:
        x, y, z = fr["at"]
        y += plan["basement"]
        dx, dz = STEP[fr["facing"]]
        ents.append(Compound({
            "pos": List[Double]([Double(x + 0.5 + dx * 0.46875), Double(y + 0.5), Double(z + 0.5 + dz * 0.46875)]),
            "blockPos": List[Int]([Int(x), Int(y), Int(z)]),
            "nbt": Compound({"id": String("minecraft:item_frame"), "TileX": Int(x), "TileY": Int(y), "TileZ": Int(z),
                             "Facing": Byte(FACING[fr["facing"]]), "ItemRotation": Byte(0), "ItemDropChance": Float(1.0)}),
        }))
    root["entities"] = List[Compound](ents)
    f.save()
    print(f"{path}: {len(plan['frames'])} frame(s)")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
