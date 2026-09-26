#!/usr/bin/env python3
"""
Writes blueprint-import test fixtures using independent third-party writers, so the mod's readers
are checked against other people's implementations of the formats:

  * Litematica .litematic  -> litemapy   (pip install litemapy)
  * Sponge .schem v2       -> mcschematic (pip install mcschematic)
  * Sponge .schem v3       -> written here by hand from the spec (nbtlib)

    python3 tools/blueprints/fixtures.py

Every fixture contains the same 5x4x5 test hut as src/gametest/.../structure/test_hut.nbt.
"""
import os
import sys

import litemapy
import mcschematic
import nbtlib
from nbtlib import ByteArray, Compound, Int, IntArray, List, Short, String

sys.path.insert(0, os.path.dirname(__file__))
from generate import ROOT, test_hut  # noqa: E402

OUT = os.path.join(ROOT, "src/gametest/resources/fixtures")


def state_string(name, props):
    return name + ("[" + ",".join(f"{k}={v}" for k, v in props) + "]" if props else "")


def hut_blocks():
    return test_hut().blocks  # {(x, y, z): (name, ((prop, value), ...))}


def litematic():
    """Two regions: walls (positive size) and the roof (negative size, stored mirrored)."""
    blocks = hut_blocks()
    walls = litemapy.Region(0, 0, 0, 5, 3, 5)
    for x in walls.range_x():
        for y in walls.range_y():
            for z in walls.range_z():
                name, props = blocks[(x, y, z)]
                walls[x, y, z] = litemapy.BlockState(name, **dict(props))
    roof = litemapy.Region(4, 3, 4, -5, 1, -5)
    for lx in roof.range_x():
        for lz in roof.range_z():
            name, props = blocks[(4 + lx, 3, 4 + lz)]
            roof[lx, 0, lz] = litemapy.BlockState(name, **dict(props))
    schem = litemapy.Schematic(name="Test Hut", author="fixtures", description="", regions={"walls": walls, "roof": roof})
    path = os.path.join(OUT, "hut.litematic")
    schem.save(path)
    return path


def schem_v2():
    blocks = hut_blocks()
    s = mcschematic.MCSchematic()
    for (x, y, z), (name, props) in blocks.items():
        if name != "minecraft:air":
            s.setBlock((x, y, z), state_string(name, props))
    s.save(OUT, "hut_v2", mcschematic.Version.JE_1_21_1)
    return os.path.join(OUT, "hut_v2.schem")


def schem_v3():
    """Sponge v3 layout: root { Schematic: { Version: 3, ..., Blocks: { Palette, Data, BlockEntities } } }.
    Also puts a chest with items in a corner to check block-entity data survives the import."""
    blocks = dict(hut_blocks())
    blocks[(1, 1, 1)] = ("minecraft:chest", (("facing", "north"), ("type", "single"), ("waterlogged", "false")))
    w, h, l = 5, 4, 5
    palette, data = {}, []
    for y in range(h):
        for z in range(l):
            for x in range(w):
                key = state_string(*blocks[(x, y, z)])
                idx = palette.setdefault(key, len(palette))
                while True:  # varint
                    b = idx & 0x7F
                    idx >>= 7
                    data.append(b | (0x80 if idx else 0))
                    if not idx:
                        break
    chest = Compound({
        "Pos": IntArray([1, 1, 1]),
        "Id": String("minecraft:chest"),
        "Data": Compound({"Items": List[Compound]([Compound({"Slot": nbtlib.Byte(0), "id": String("minecraft:diamond"), "count": Int(5)})])}),
    })
    root = Compound({"Schematic": Compound({
        "Version": Int(3),
        "DataVersion": Int(3955),
        "Width": Short(w), "Height": Short(h), "Length": Short(l),
        "Offset": IntArray([0, 0, 0]),
        "Blocks": Compound({
            "Palette": Compound({k: Int(v) for k, v in palette.items()}),
            "Data": ByteArray([b - 256 if b > 127 else b for b in data]),
            "BlockEntities": List[Compound]([chest]),
        }),
    })})
    path = os.path.join(OUT, "hut_v3.schem")
    nbtlib.File(root).save(path, gzipped=True)
    return path


def schem_old_version():
    """Written for 1.20.2, before 'grass' was renamed 'short_grass': the importer must upgrade it."""
    s = mcschematic.MCSchematic()
    s.setBlock((0, 0, 0), "minecraft:grass_block")
    s.setBlock((0, 1, 0), "minecraft:grass")
    s.setBlock((1, 0, 0), "minecraft:stone")
    s.save(OUT, "old_1_20_2", mcschematic.Version.JE_1_20_2)
    return os.path.join(OUT, "old_1_20_2.schem")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for f in (litematic(), schem_v2(), schem_v3(), schem_old_version()):
        print("wrote", os.path.relpath(f, ROOT), os.path.getsize(f), "bytes")
