#!/usr/bin/env python3
"""
Generates the structure files that ship with Alive Workplace, plus the gametest fixtures.

    pip install nbtlib
    python3 tools/blueprints/generate.py

Conventions for every blueprint (the mod relies on these):
  * x = width, y = height, z = depth; the FRONT of the build is the z = 0 side (north).
  * y = 0 is the floor layer; it sits on top of the block the player clicks.
  * Every position inside the bounds is listed (air included) so builders clear the site.

If you change a build's size, update StarterBlueprints.java (a gametest checks they match).
"""
import os
import nbtlib
from nbtlib import Compound, List, Int, String

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
MAIN_STRUCTURES = os.path.join(ROOT, "src/main/resources/data/aliveworkplace/structure")
TEST_STRUCTURES = os.path.join(ROOT, "src/gametest/resources/data/aliveworkplace_test/structure")
TEST_AREAS = os.path.join(ROOT, "src/gametest/resources/data/aliveworkplace_test/gametest/structure")
DATA_VERSION = 3955  # Minecraft 1.21.1


class Build:
    def __init__(self, w, h, d):
        self.w, self.h, self.d = w, h, d
        self.blocks = {}

    def set(self, x, y, z, name, **props):
        assert 0 <= x < self.w and 0 <= y < self.h and 0 <= z < self.d, (x, y, z, name)
        if ":" not in name:
            name = "minecraft:" + name
        self.blocks[(x, y, z)] = (name, tuple(sorted((k, str(v).lower()) for k, v in props.items())))

    def fill(self, x0, y0, z0, x1, y1, z1, name, **props):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, name, **props)

    def ring(self, x0, z0, x1, z1, y, name, **props):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    self.set(x, y, z, name, **props)

    def fill_air(self):
        for x in range(self.w):
            for y in range(self.h):
                for z in range(self.d):
                    self.blocks.setdefault((x, y, z), ("minecraft:air", ()))

    def door(self, x, y, z, name, facing, hinge="left"):
        self.set(x, y, z, name, facing=facing, half="lower", hinge=hinge, open=False, powered=False)
        self.set(x, y + 1, z, name, facing=facing, half="upper", hinge=hinge, open=False, powered=False)

    def bed(self, x, y, z, color, facing):
        """(x, y, z) is the foot; the head is one block towards `facing`."""
        dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[facing]
        self.set(x, y, z, f"{color}_bed", facing=facing, part="foot", occupied=False)
        self.set(x + dx, y, z + dz, f"{color}_bed", facing=facing, part="head", occupied=False)

    def to_nbt(self):
        palette, index, blocks = [], {}, []
        for (x, y, z), key in sorted(self.blocks.items()):
            if key not in index:
                index[key] = len(palette)
                name, props = key
                entry = Compound({"Name": String(name)})
                if props:
                    entry["Properties"] = Compound({k: String(v) for k, v in props})
                palette.append(entry)
            blocks.append(Compound({"pos": List[Int]([Int(x), Int(y), Int(z)]), "state": Int(index[key])}))
        return Compound({
            "DataVersion": Int(DATA_VERSION),
            "size": List[Int]([Int(self.w), Int(self.h), Int(self.d)]),
            "palette": List[Compound](palette),
            "blocks": List[Compound](blocks),
            "entities": List[Compound]([]),
        })

    def save(self, folder, name):
        os.makedirs(folder, exist_ok=True)
        path = os.path.join(folder, name + ".nbt")
        nbtlib.File(self.to_nbt()).save(path, gzipped=True)
        solid = sum(1 for n, _ in self.blocks.values() if n != "minecraft:air")
        print(f"{name}: {self.w}x{self.h}x{self.d}, {solid} blocks -> {os.path.relpath(path, ROOT)}")


# --- Starter Cottage: 9 x 10 x 9 --------------------------------------------------------------
def starter_cottage():
    b = Build(9, 10, 9)
    # Floor: cobblestone rim, oak plank interior
    b.fill(0, 0, 0, 8, 0, 8, "cobblestone")
    b.fill(1, 0, 1, 7, 0, 7, "oak_planks")
    # Walls y1-3: log corners, plank walls
    for y in range(1, 4):
        b.ring(0, 0, 8, 8, y, "oak_planks")
        for x, z in ((0, 0), (8, 0), (0, 8), (8, 8)):
            b.set(x, y, z, "oak_log", axis="y")
    # Top plate at y4: logs running along each wall
    for x in range(0, 9):
        b.set(x, 4, 0, "oak_log", axis="x")
        b.set(x, 4, 8, "oak_log", axis="x")
    for z in range(1, 8):
        b.set(0, 4, z, "oak_log", axis="z")
        b.set(8, 4, z, "oak_log", axis="z")
    # Windows
    for x in (2, 6):
        b.set(x, 2, 0, "glass_pane", north=False, south=False, east=True, west=True)
        b.set(x, 2, 8, "glass_pane", north=False, south=False, east=True, west=True)
    for x in (0, 8):
        b.set(x, 2, 4, "glass_pane", north=True, south=True, east=False, west=False)
    # Front door (front = z0), opens into the house
    b.door(4, 1, 0, "oak_door", facing="south")
    # Gable roof: spruce stairs rising from front and back to a slab ridge
    for i, y in enumerate(range(5, 9)):
        for x in range(0, 9):
            b.set(x, y, i, "spruce_stairs", facing="south", half="bottom", shape="straight")
            b.set(x, y, 8 - i, "spruce_stairs", facing="north", half="bottom", shape="straight")
        for z in range(i + 1, 8 - i):
            b.set(0, y, z, "oak_planks")
            b.set(8, y, z, "oak_planks")
    for x in range(0, 9):
        b.set(x, 9, 4, "spruce_slab", type="bottom")
    # Little gable windows
    b.set(0, 6, 4, "glass_pane", north=True, south=True, east=False, west=False)
    b.set(8, 6, 4, "glass_pane", north=True, south=True, east=False, west=False)
    # Furniture
    b.bed(7, 1, 6, "red", facing="south")
    b.set(1, 1, 7, "crafting_table")
    b.set(2, 1, 7, "chest", facing="north", type="single")
    b.set(1, 1, 6, "furnace", facing="east", lit=False)
    b.set(1, 1, 1, "barrel", facing="up", open=False)
    b.set(7, 1, 1, "oak_stairs", facing="west", half="bottom", shape="straight")  # a seat
    b.set(6, 1, 1, "oak_fence", north=False, south=False, east=False, west=False)
    b.set(6, 2, 1, "oak_pressure_plate", powered=False)                            # a little table
    b.set(1, 1, 4, "white_carpet")
    # Light
    b.set(4, 3, 1, "wall_torch", facing="south")
    b.set(4, 3, 7, "wall_torch", facing="north")
    b.fill_air()
    return b


# --- Market Stall: 7 x 5 x 5 ------------------------------------------------------------------
def market_stall():
    b = Build(7, 5, 5)
    b.fill(0, 0, 0, 6, 0, 4, "spruce_planks")
    for x, z in ((0, 0), (6, 0), (0, 4), (6, 4)):
        for y in range(1, 4):
            b.set(x, y, z, "spruce_fence")
    # Counter along the front
    for x in range(1, 6):
        b.set(x, 1, 1, "barrel" if x in (1, 5) else "spruce_planks", **({"facing": "up", "open": False} if x in (1, 5) else {}))
    b.set(2, 2, 1, "melon")
    b.set(4, 2, 1, "pumpkin")
    b.set(3, 2, 1, "lantern", hanging=False)
    # Back shelf
    b.set(2, 1, 3, "composter", level=0)
    b.set(3, 1, 3, "hay_block", axis="x")
    b.set(4, 1, 3, "hay_block", axis="x")
    # Striped canopy
    for x in range(0, 7):
        for z in range(0, 5):
            b.set(x, 4, z, "red_wool" if x % 2 == 0 else "white_wool")
    b.fill_air()
    return b


# --- Lookout Tower: 7 x 15 x 7 ----------------------------------------------------------------
def lookout_tower():
    b = Build(7, 15, 7)
    b.fill(1, 0, 1, 5, 0, 5, "stone_bricks")
    for y in range(1, 12):
        b.ring(1, 1, 5, 5, y, "stone_bricks")
    # Arrow slits
    for y in (5, 8):
        b.set(3, y, 1, "air")
        b.set(2, y, 5, "air")  # off-centre: the ladder runs up the middle of the back wall
        b.set(1, y, 3, "air")
        b.set(5, y, 3, "air")
    b.door(3, 1, 1, "spruce_door", facing="south")
    # Ladder up the back wall
    for y in range(1, 12):
        b.set(3, y, 4, "ladder", facing="north")
    # Platform
    for x in range(0, 7):
        for z in range(0, 7):
            if not (1 <= x <= 5 and 1 <= z <= 5 and (x in (1, 5) or z in (1, 5))):
                b.set(x, 11, z, "spruce_planks")
    b.set(3, 11, 4, "ladder", facing="north")
    for x in range(0, 7):
        for z in range(0, 7):
            if x in (0, 6) or z in (0, 6):
                b.set(x, 12, z, "spruce_fence")
    for x, z in ((0, 0), (6, 0), (0, 6), (6, 6)):
        b.set(x, 13, z, "spruce_fence")
    b.fill(0, 14, 0, 6, 14, 6, "spruce_slab", type="bottom")
    b.set(3, 13, 3, "lantern", hanging=True)
    b.set(3, 3, 2, "wall_torch", facing="south")
    b.fill_air()
    return b


# --- Gametest fixtures ------------------------------------------------------------------------
def test_hut():
    """5x4x5 hut: floor, walls with a door and a torch, flat roof. Needs 25 cobblestone,
    55 oak planks, 1 oak door, 1 torch."""
    b = Build(5, 4, 5)
    b.fill(0, 0, 0, 4, 0, 4, "cobblestone")
    for y in (1, 2):
        b.ring(0, 0, 4, 4, y, "oak_planks")
    b.door(2, 1, 0, "oak_door", facing="south")
    b.set(2, 2, 3, "wall_torch", facing="north")
    b.fill(0, 3, 0, 4, 3, 4, "oak_planks")
    b.fill_air()
    return b


def test_area(name, w, h, d):
    """Flat smooth-stone floor at y=0, empty above. Saved as SNBT in the *packed* layout the gametest
    loader expects (palette of state strings, `data` entries with a `state` string)."""
    os.makedirs(TEST_AREAS, exist_ok=True)
    data = [Compound({"pos": List[Int]([Int(x), Int(0), Int(z)]), "state": String("minecraft:smooth_stone")})
            for x in range(w) for z in range(d)]
    tag = Compound({
        "DataVersion": Int(DATA_VERSION),
        "size": List[Int]([Int(w), Int(h), Int(d)]),
        "data": List[Compound](data),
        "entities": List[Compound]([]),
        "palette": List[String]([String("minecraft:smooth_stone")]),
    })
    path = os.path.join(TEST_AREAS, name + ".snbt")
    with open(path, "w") as f:
        f.write(nbtlib.serialize_tag(tag))
    print(f"{name}: test area {w}x{h}x{d} -> {os.path.relpath(path, ROOT)}")


if __name__ == "__main__":
    starter_cottage().save(MAIN_STRUCTURES, "starter_cottage")
    market_stall().save(MAIN_STRUCTURES, "market_stall")
    lookout_tower().save(MAIN_STRUCTURES, "lookout_tower")
    test_hut().save(TEST_STRUCTURES, "test_hut")
    test_area("build_area", 17, 8, 17)
    test_area("big_area", 22, 18, 22)
