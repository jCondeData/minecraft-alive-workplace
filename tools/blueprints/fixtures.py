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


# --- The import corpus (ROADMAP 23.7) ------------------------------------------------------------------------------
# Self-made sample files in every format, size and age the importer must handle, plus files it must refuse with a clear
# reason. All are drawn here (CC0, part of this repository). ImportCorpusGameTests reads each and checks the outcome.
CORPUS = os.path.join(OUT, "corpus")


def big_hall():
    """A 48x8x48 hall: stone-brick floor, plank walls with log corners and windows, a door, pillars, a slab roof,
    lanterns, stairs and a chest with items. {(x, y, z): (name, props)}; air is left out."""
    w, h, l = 48, 8, 48
    b = {}
    for x in range(w):
        for z in range(l):
            b[(x, 0, z)] = ("minecraft:stone_bricks", ())
            b[(x, 6, z)] = ("minecraft:oak_slab", (("type", "bottom"), ("waterlogged", "false")))
    for y in range(1, 6):
        for x in range(w):
            for z in (0, l - 1):
                b[(x, y, z)] = ("minecraft:oak_planks", ())
        for z in range(l):
            for x in (0, w - 1):
                b[(x, y, z)] = ("minecraft:oak_planks", ())
        for x, z in ((0, 0), (w - 1, 0), (0, l - 1), (w - 1, l - 1)):
            b[(x, y, z)] = ("minecraft:oak_log", (("axis", "y"),))
        for px in range(8, w - 1, 8):
            for pz in range(8, l - 1, 8):
                b[(px, y, pz)] = ("minecraft:spruce_log", (("axis", "y"),))
    for i in range(4, w - 4, 4):
        for y in (2, 3):
            pane = ("minecraft:glass_pane", (("east", "true"), ("north", "false"), ("south", "false"), ("waterlogged", "false"), ("west", "true")))
            b[(i, y, 0)] = pane
            b[(i, y, l - 1)] = pane
    b[(23, 1, 0)] = ("minecraft:oak_door", (("facing", "north"), ("half", "lower"), ("hinge", "left"), ("open", "false"), ("powered", "false")))
    b[(23, 2, 0)] = ("minecraft:oak_door", (("facing", "north"), ("half", "upper"), ("hinge", "left"), ("open", "false"), ("powered", "false")))
    for x in range(10, 38):
        b[(x, 1, 40)] = ("minecraft:oak_stairs", (("facing", "north"), ("half", "bottom"), ("shape", "straight"), ("waterlogged", "false")))
    for x in range(4, w - 4, 6):
        b[(x, 1, 4)] = ("minecraft:lantern", (("hanging", "false"), ("waterlogged", "false")))
    b[(2, 1, 2)] = ("minecraft:chest", (("facing", "south"), ("type", "single"), ("waterlogged", "false")))
    return (w, h, l), b


def chest_items():
    return List[Compound]([Compound({"Slot": nbtlib.Byte(0), "id": String("minecraft:bread"), "count": Int(12)})])


def sponge_v3(path, size, blocks, data_version=3955, block_entities=()):
    w, h, l = size
    palette, data = {"minecraft:air": 0}, []
    for y in range(h):
        for z in range(l):
            for x in range(w):
                key = state_string(*blocks[(x, y, z)]) if (x, y, z) in blocks else "minecraft:air"
                idx = palette.setdefault(key, len(palette))
                while True:
                    byte = idx & 0x7F
                    idx >>= 7
                    data.append(byte | (0x80 if idx else 0))
                    if not idx:
                        break
    root = Compound({"Schematic": Compound({
        "Version": Int(3), "DataVersion": Int(data_version),
        "Width": Short(w), "Height": Short(h), "Length": Short(l), "Offset": IntArray([0, 0, 0]),
        "Blocks": Compound({
            "Palette": Compound({k: Int(v) for k, v in palette.items()}),
            "Data": ByteArray([v - 256 if v > 127 else v for v in data]),
            "BlockEntities": List[Compound](list(block_entities)),
        }),
    })})
    nbtlib.File(root).save(path, gzipped=True)
    return path


def sponge_v1(path, size, blocks):
    """Sponge v1 (WorldEdit 7.0 for 1.13): everything at the root, no DataVersion."""
    w, h, l = size
    palette, data = {"minecraft:air": 0}, []
    for y in range(h):
        for z in range(l):
            for x in range(w):
                key = state_string(*blocks[(x, y, z)]) if (x, y, z) in blocks else "minecraft:air"
                data.append(palette.setdefault(key, len(palette)))
    root = Compound({
        "Version": Int(1), "Width": Short(w), "Height": Short(h), "Length": Short(l),
        "Palette": Compound({k: Int(v) for k, v in palette.items()}), "PaletteMax": Int(len(palette)),
        "BlockData": ByteArray(data),
    })
    nbtlib.File(root, root_name="Schematic").save(path, gzipped=True)
    return path


def structure_nbt(path, size, blocks, data_version=3955, chest_at=None):
    """Vanilla structure-block NBT. Palette entries are {Name, Properties}."""
    palette, index, entries = [], {}, []
    for pos, (name, props) in sorted(blocks.items()):
        key = (name, props)
        if key not in index:
            index[key] = len(palette)
            entry = {"Name": String(name)}
            if props:
                entry["Properties"] = Compound({k: String(v) for k, v in props})
            palette.append(Compound(entry))
        block = {"pos": List[Int]([Int(pos[0]), Int(pos[1]), Int(pos[2])]), "state": Int(index[key])}
        if pos == chest_at:
            block["nbt"] = Compound({"id": String("minecraft:chest"), "Items": chest_items()})
        entries.append(Compound(block))
    root = Compound({
        "DataVersion": Int(data_version),
        "size": List[Int]([Int(s) for s in size]),
        "palette": List[Compound](palette),
        "blocks": List[Compound](entries),
        "entities": List[Compound]([]),
    })
    nbtlib.File(root).save(path, gzipped=True)
    return path


def litematic_file(path, size, blocks, data_version=None, chest_at=None):
    w, h, l = size
    region = litemapy.Region(0, 0, 0, w, h, l)
    for (x, y, z), (name, props) in blocks.items():
        region[x, y, z] = litemapy.BlockState(name, **dict(props))
    schem = litemapy.Schematic(name=os.path.basename(path), author="fixtures", description="", regions={"main": region})
    schem.save(path)
    if data_version is not None or chest_at is not None:
        f = nbtlib.load(path)
        if data_version is not None:
            f["MinecraftDataVersion"] = Int(data_version)
        if chest_at is not None:
            x, y, z = chest_at
            f["Regions"]["main"]["TileEntities"] = List[Compound]([Compound({
                "x": Int(x), "y": Int(y), "z": Int(z), "id": String("minecraft:chest"), "Items": chest_items()})])
        f.save(path, gzipped=True)
    return path


def corpus():
    os.makedirs(CORPUS, exist_ok=True)
    out = []
    size, hall = big_hall()
    chest = (2, 1, 2)
    out.append(litematic_file(os.path.join(CORPUS, "big_hall.litematic"), size, hall, chest_at=chest))
    out.append(sponge_v3(os.path.join(CORPUS, "big_hall.schem"), size, hall, block_entities=[Compound({
        "Pos": IntArray(list(chest)), "Id": String("minecraft:chest"), "Data": Compound({"Items": chest_items()})})]))
    out.append(structure_nbt(os.path.join(CORPUS, "big_hall.nbt"), size, hall, chest_at=chest))

    # A build using blocks from mods the server doesn't have: imports, and says which.
    mixed = {}
    for x in range(5):
        for z in range(5):
            mixed[(x, 0, z)] = ("minecraft:cobblestone", ())
    for x in range(5):
        mixed[(x, 1, 2)] = ("create:shaft", (("axis", "x"),))
    mixed[(2, 2, 2)] = ("create:andesite_casing", ())
    mixed[(0, 1, 0)] = ("supplementaries:sconce", ())
    out.append(sponge_v3(os.path.join(CORPUS, "modded_mix.schem"), (5, 3, 5), mixed))
    # Nothing but modded blocks: nothing would be left, so it is refused, naming them.
    only = {(x, 0, z): ("create:andesite_casing", ()) for x in range(3) for z in range(3)}
    only[(1, 1, 1)] = ("mekanism:steel_casing", ())
    out.append(litematic_file(os.path.join(CORPUS, "all_modded.litematic"), (3, 2, 3), only))

    # Old formats and versions, upgraded on import.
    old_nbt = {
        (0, 0, 0): ("minecraft:planks", (("variant", "spruce"),)),
        (1, 0, 0): ("minecraft:wool", (("color", "red"),)),
        (2, 0, 0): ("minecraft:stone", (("variant", "granite"),)),
    }
    out.append(structure_nbt(os.path.join(CORPUS, "old_1_12.nbt"), (3, 1, 1), old_nbt, data_version=1343))
    old_lite = {(0, 0, 0): ("minecraft:grass_path", ()), (1, 0, 0): ("minecraft:grass_block", (("snowy", "false"),)),
                (1, 1, 0): ("minecraft:grass", ())}
    out.append(litematic_file(os.path.join(CORPUS, "old_1_16.litematic"), (2, 2, 1), old_lite, data_version=2586))
    v1 = {(0, 0, 0): ("minecraft:oak_planks", ()), (1, 0, 0): ("minecraft:stone_bricks", ()),
          (0, 1, 0): ("minecraft:torch", ())}
    out.append(sponge_v1(os.path.join(CORPUS, "sponge_v1.schem"), (2, 2, 1), v1))

    # Files that must be refused with a clear reason.
    mcedit = Compound({"Width": Short(2), "Height": Short(1), "Length": Short(1), "Materials": String("Alpha"),
                       "Blocks": ByteArray([1, 4]), "Data": ByteArray([0, 0])})
    path = os.path.join(CORPUS, "old_mcedit.schematic")
    nbtlib.File(mcedit, root_name="Schematic").save(path, gzipped=True)
    out.append(path)
    with open(os.path.join(CORPUS, "big_hall.litematic"), "rb") as f:
        whole = f.read()
    path = os.path.join(CORPUS, "cut_short.litematic")
    with open(path, "wb") as f:
        f.write(whole[: len(whole) // 2])
    out.append(path)
    f = nbtlib.load(os.path.join(CORPUS, "big_hall.litematic"))
    states = f["Regions"]["main"]["BlockStates"]
    f["Regions"]["main"]["BlockStates"] = nbtlib.LongArray(list(states)[: len(states) // 3])
    path = os.path.join(CORPUS, "damaged_region.litematic")
    f.save(path, gzipped=True)
    out.append(path)
    path = os.path.join(CORPUS, "a_level_dat.nbt")
    nbtlib.File(Compound({"Data": Compound({"LevelName": String("Not a build"), "DataVersion": Int(3955)})})).save(path, gzipped=True)
    out.append(path)
    return out


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for f in (litematic(), schem_v2(), schem_v3(), schem_old_version()):
        print("wrote", os.path.relpath(f, ROOT), os.path.getsize(f), "bytes")
    for f in corpus():
        print("wrote", os.path.relpath(f, ROOT), os.path.getsize(f), "bytes")
