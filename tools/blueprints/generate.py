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
COMPAT_AREAS = os.path.join(ROOT, "src/compattest/resources/data/aliveworkplace_compat/gametest/structure")
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

    def set_nbt(self, x, y, z, nbt):
        """Block-entity data for the block at (x, y, z) (set the block first)."""
        self.nbt = getattr(self, "nbt", {})
        self.nbt[(x, y, z)] = nbt

    def grow(self, w, h, d):
        """A bigger copy of this build (air left out), for an upgrade drawn on top of its base."""
        b = Build(w, h, d)
        for pos, block in self.blocks.items():
            if block[0] != "minecraft:air":
                b.blocks[pos] = block
        b.nbt = dict(getattr(self, "nbt", {}))
        return b

    def clear(self, x0, y0, z0, x1, y1, z1):
        """Takes blocks out again (they become air when the build is saved)."""
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.blocks.pop((x, y, z), None)

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
            entry = Compound({"pos": List[Int]([Int(x), Int(y), Int(z)]), "state": Int(index[key])})
            extra = getattr(self, "nbt", {}).get((x, y, z))
            if extra is not None:
                entry["nbt"] = extra
            blocks.append(entry)
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


# --- Starter Cottage, upgraded: the same ground floor with a second storey on top -----------------------
def starter_cottage_2():
    """Upgrade of the Starter Cottage (same origin and front): the ground floor is identical, so a builder only
    takes the old roof off and adds the upper floor, a ladder, a new roof and a balcony window."""
    b = Build(9, 14, 9)
    # Ground floor: exactly the Starter Cottage's
    b.fill(0, 0, 0, 8, 0, 8, "cobblestone")
    b.fill(1, 0, 1, 7, 0, 7, "oak_planks")
    for y in range(1, 4):
        b.ring(0, 0, 8, 8, y, "oak_planks")
        for x, z in ((0, 0), (8, 0), (0, 8), (8, 8)):
            b.set(x, y, z, "oak_log", axis="y")
    for x in (2, 6):
        b.set(x, 2, 0, "glass_pane", north=False, south=False, east=True, west=True)
        b.set(x, 2, 8, "glass_pane", north=False, south=False, east=True, west=True)
    for x in (0, 8):
        b.set(x, 2, 4, "glass_pane", north=True, south=True, east=False, west=False)
    b.door(4, 1, 0, "oak_door", facing="south")
    b.bed(7, 1, 6, "red", facing="south")
    b.set(1, 1, 7, "crafting_table")
    b.set(2, 1, 7, "chest", facing="north", type="single")
    b.set(1, 1, 6, "furnace", facing="east", lit=False)
    b.set(1, 1, 1, "barrel", facing="up", open=False)
    b.set(7, 1, 1, "oak_stairs", facing="west", half="bottom", shape="straight")
    b.set(6, 1, 1, "oak_fence", north=False, south=False, east=False, west=False)
    b.set(6, 2, 1, "oak_pressure_plate", powered=False)
    b.set(1, 1, 4, "white_carpet")
    b.set(4, 3, 1, "wall_torch", facing="south")
    b.set(4, 3, 7, "wall_torch", facing="north")
    # The old top plate becomes the upper floor's rim; planks fill it in, with a hole for the ladder
    for x in range(0, 9):
        b.set(x, 4, 0, "oak_log", axis="x")
        b.set(x, 4, 8, "oak_log", axis="x")
    for z in range(1, 8):
        b.set(0, 4, z, "oak_log", axis="z")
        b.set(8, 4, z, "oak_log", axis="z")
    b.fill(1, 4, 1, 7, 4, 7, "spruce_planks")
    for y in range(1, 6):
        b.set(7, y, 3, "ladder", facing="west", waterlogged=False)
    # Upper storey: spruce walls between log corners, windows all round
    for y in range(5, 8):
        b.ring(0, 0, 8, 8, y, "spruce_planks")
        for x, z in ((0, 0), (8, 0), (0, 8), (8, 8)):
            b.set(x, y, z, "oak_log", axis="y")
    for x in (2, 6):
        b.set(x, 6, 0, "glass_pane", north=False, south=False, east=True, west=True)
        b.set(x, 6, 8, "glass_pane", north=False, south=False, east=True, west=True)
    for x in (0, 8):
        b.set(x, 6, 3, "glass_pane", north=True, south=True, east=False, west=False)
        b.set(x, 6, 5, "glass_pane", north=True, south=True, east=False, west=False)
    # A tall front window over the door
    b.set(4, 5, 0, "glass_pane", north=False, south=False, east=True, west=True)
    b.set(4, 6, 0, "glass_pane", north=False, south=False, east=True, west=True)
    for x in range(0, 9):
        b.set(x, 8, 0, "oak_log", axis="x")
        b.set(x, 8, 8, "oak_log", axis="x")
    for z in range(1, 8):
        b.set(0, 8, z, "oak_log", axis="z")
        b.set(8, 8, z, "oak_log", axis="z")
    # Roof: the same gable, one storey higher
    for i, y in enumerate(range(9, 13)):
        for x in range(0, 9):
            b.set(x, y, i, "spruce_stairs", facing="south", half="bottom", shape="straight")
            b.set(x, y, 8 - i, "spruce_stairs", facing="north", half="bottom", shape="straight")
        for z in range(i + 1, 8 - i):
            b.set(0, y, z, "oak_planks")
            b.set(8, y, z, "oak_planks")
    for x in range(0, 9):
        b.set(x, 13, 4, "spruce_slab", type="bottom")
    b.set(0, 10, 4, "glass_pane", north=True, south=True, east=False, west=False)
    b.set(8, 10, 4, "glass_pane", north=True, south=True, east=False, west=False)
    # Upstairs: a second bed, bookshelves, a rug and a lantern
    b.bed(1, 5, 6, "light_blue", facing="south")
    b.set(1, 5, 2, "bookshelf")
    b.set(2, 5, 7, "bookshelf")
    b.set(3, 5, 7, "chest", facing="north", type="single")
    b.fill(3, 5, 3, 5, 5, 5, "light_blue_carpet")
    b.set(1, 6, 2, "lantern", hanging=False, waterlogged=False)  # on the bookshelf
    b.set(4, 7, 1, "wall_torch", facing="south")
    b.fill_air()
    return b


# --- Starter Cottage III: Cottage II with a kitchen wing to the east and a roof terrace on it -----------------------
def starter_cottage_3():
    """Upgrade of Starter Cottage II (same origin and front): a one-storey wing grows out of the east wall, with a
    doorway through from the ground floor and a door from the upper floor onto the wing's roof terrace. The wing
    stands on ground the cottage never used, so a builder clears whatever is there first."""
    b = starter_cottage_2().grow(15, 14, 9)
    # Wing floor (x 9-14, z 1-7): cobblestone rim, planks inside
    b.fill(9, 0, 1, 14, 0, 7, "cobblestone")
    b.fill(9, 0, 2, 13, 0, 6, "oak_planks")
    # Walls y1-3, log corners; the cottage's east wall is the wing's fourth side
    for y in range(1, 4):
        for x in range(9, 15):
            b.set(x, y, 1, "oak_planks")
            b.set(x, y, 7, "oak_planks")
        for z in range(1, 8):
            b.set(14, y, z, "oak_planks")
        b.set(14, y, 1, "oak_log", axis="y")
        b.set(14, y, 7, "oak_log", axis="y")
    for x in (10, 12):
        b.set(x, 2, 1, "glass_pane", north=False, south=False, east=True, west=True)
        b.set(x, 2, 7, "glass_pane", north=False, south=False, east=True, west=True)
    for z in (2, 6):
        b.set(14, 2, z, "glass_pane", north=True, south=True, east=False, west=False)
    # A door out of the wing, and a doorway through the cottage's east wall where its window was
    b.door(14, 1, 4, "oak_door", facing="west")
    b.clear(8, 1, 4, 8, 2, 4)
    # Kitchen: a smoker and a barrel, a table with two seats, torches
    b.set(13, 1, 2, "smoker", facing="west", lit=False)
    b.set(12, 1, 2, "barrel", facing="up", open=False)
    b.set(11, 1, 6, "oak_fence", north=False, south=False, east=False, west=False)
    b.set(11, 2, 6, "oak_pressure_plate", powered=False)
    b.set(10, 1, 6, "oak_stairs", facing="west", half="bottom", shape="straight")
    b.set(12, 1, 6, "oak_stairs", facing="east", half="bottom", shape="straight")
    b.set(11, 3, 2, "wall_torch", facing="south")
    b.set(11, 3, 6, "wall_torch", facing="north")
    # Roof terrace: a log rim round spruce planks, a railing, lanterns on the far corners
    for x in range(9, 15):
        b.set(x, 4, 1, "oak_log", axis="x")
        b.set(x, 4, 7, "oak_log", axis="x")
    for z in range(2, 7):
        b.set(14, 4, z, "oak_log", axis="z")
    b.fill(9, 4, 2, 13, 4, 6, "spruce_planks")
    for x in range(9, 14):
        b.set(x, 5, 1, "oak_fence", north=False, south=False, east=True, west=True)
        b.set(x, 5, 7, "oak_fence", north=False, south=False, east=True, west=True)
    for z in range(2, 7):
        b.set(14, 5, z, "oak_fence", north=True, south=True, east=False, west=False)
    b.set(14, 5, 1, "oak_fence", north=False, south=True, east=False, west=True)
    b.set(14, 5, 7, "oak_fence", north=True, south=False, east=False, west=True)
    b.set(14, 6, 1, "lantern", hanging=False, waterlogged=False)
    b.set(14, 6, 7, "lantern", hanging=False, waterlogged=False)
    # The door out onto it from the upper floor
    b.door(8, 5, 4, "oak_door", facing="west")
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


# --- Market Stall, upgraded: a second stall alongside with a Shop Counter -----------------------------
def market_stall_2():
    """Upgrade of the Market Stall: a second stall shares its right-hand posts, under a blue canopy, with a Shop
    Counter between two barrels of stock (a villager moves in as shopkeeper; the first player to open it owns it)."""
    b = market_stall().grow(13, 5, 5)
    b.fill(7, 0, 0, 12, 0, 4, "spruce_planks")
    for z in (0, 4):
        for y in range(1, 4):
            b.set(12, y, z, "spruce_fence")
    b.set(7, 1, 1, "barrel", facing="up", open=False)
    b.set(8, 1, 1, "spruce_planks")
    b.set(9, 1, 1, "aliveworkplace:shop_counter", facing="south")
    b.set(10, 1, 1, "spruce_planks")
    b.set(11, 1, 1, "barrel", facing="up", open=False)
    b.set(8, 2, 1, "potted_cornflower")
    b.set(10, 2, 1, "lantern", hanging=False, waterlogged=False)
    # Back shelf: more stock either side of the shopkeeper's stool
    b.set(8, 1, 3, "barrel", facing="up", open=False)
    b.set(9, 1, 3, "spruce_stairs", facing="south", half="bottom", shape="straight", waterlogged=False)
    b.set(10, 1, 3, "barrel", facing="up", open=False)
    for x in range(7, 13):
        for z in range(0, 5):
            b.set(x, 4, z, "blue_wool" if x % 2 == 0 else "white_wool")
    b.fill_air()
    return b

# --- Market Stall III: a storeroom shed behind the two stalls ------------------------------------------
def market_stall_3():
    """Upgrade of Market Stall II: a shed behind the stalls, reached through a door behind the second stall, with chests
    and a workbench for the stock (within reach of the Shop Counter, so the shopkeeper sells from it too)."""
    b = market_stall_2().grow(13, 5, 10)
    b.fill(1, 0, 5, 11, 0, 9, "spruce_planks")
    for y in range(1, 4):
        b.ring(1, 5, 11, 9, y, "spruce_planks")
        for x, z in ((1, 5), (11, 5), (1, 9), (11, 9)):
            b.set(x, y, z, "spruce_log", axis="y")
    b.fill(1, 4, 5, 11, 4, 9, "spruce_slab", type="bottom", waterlogged=False)
    b.door(9, 1, 5, "spruce_door", facing="south")  # behind the second stall (a post stands behind the first's middle)
    for x in (3, 9):
        b.set(x, 2, 9, "glass_pane", north=False, south=False, east=True, west=True, waterlogged=False)
    for x in (1, 11):
        b.set(x, 2, 7, "glass_pane", north=True, south=True, east=False, west=False, waterlogged=False)
    for x in (2, 4, 8, 10):
        b.set(x, 1, 8, "chest", facing="north", type="single", waterlogged=False)
    b.set(6, 1, 8, "crafting_table")
    b.set(2, 1, 6, "hay_block", axis="y")
    b.set(10, 1, 6, "hay_block", axis="y")
    b.set(6, 3, 7, "lantern", hanging=True, waterlogged=False)
    b.fill_air()
    return b


# --- Lookout Tower, upgraded: a guard post with a bell under a pointed roof --------------------------------
def lookout_tower_2():
    """Upgrade of the Lookout Tower: a Guard Post and a chest for gear at the foot of the ladder (a villager moves
    in as a guard), and a pointed roof over the platform with a bell to call the guards and a lightning rod."""
    b = lookout_tower().grow(7, 18, 7)
    b.set(2, 1, 3, "aliveworkplace:guard_post", facing="east")
    b.set(4, 1, 3, "chest", facing="west", type="single", waterlogged=False)
    b.clear(0, 14, 0, 6, 14, 6)  # the flat slab roof
    b.clear(3, 13, 3, 3, 13, 3)  # and the lantern under it
    for y, lo, hi in ((14, 0, 6), (15, 1, 5), (16, 2, 4)):
        for x in range(lo, hi + 1):
            b.set(x, y, lo, "spruce_stairs", facing="south", half="bottom", shape="straight", waterlogged=False)
            b.set(x, y, hi, "spruce_stairs", facing="north", half="bottom", shape="straight", waterlogged=False)
        for z in range(lo + 1, hi):
            b.set(lo, y, z, "spruce_stairs", facing="east", half="bottom", shape="straight", waterlogged=False)
            b.set(hi, y, z, "spruce_stairs", facing="west", half="bottom", shape="straight", waterlogged=False)
    b.set(3, 16, 3, "spruce_planks")
    b.set(3, 17, 3, "lightning_rod", facing="up", powered=False, waterlogged=False)
    b.set(3, 15, 3, "bell", attachment="ceiling", facing="north", powered=False)
    for x in (1, 5):
        b.set(x, 14, 3, "lantern", hanging=True, waterlogged=False)
    b.fill_air()
    return b

# --- Lookout Tower III: a guardhouse beside the tower --------------------------------------------------
def lookout_tower_3():
    """Upgrade of Lookout Tower II: a stone guardhouse against the tower's east side with a second Guard Post, a chest
    for gear and two bunks (a second villager moves in as a guard)."""
    b = lookout_tower_2().grow(13, 18, 7)
    b.fill(7, 0, 1, 12, 0, 5, "stone_bricks")
    for y in range(1, 4):
        b.ring(7, 1, 12, 5, y, "stone_bricks")
    b.ring(7, 1, 12, 5, 4, "spruce_planks")
    b.fill(7, 5, 1, 12, 5, 5, "spruce_slab", type="bottom", waterlogged=False)
    b.door(9, 1, 1, "spruce_door", facing="south")
    b.set(11, 2, 1, "glass_pane", north=False, south=False, east=True, west=True, waterlogged=False)
    b.set(12, 2, 3, "glass_pane", north=True, south=True, east=False, west=False, waterlogged=False)
    b.set(8, 1, 3, "aliveworkplace:guard_post", facing="east")
    b.set(8, 1, 4, "chest", facing="east", type="single", waterlogged=False)
    b.bed(10, 1, 3, "white", facing="south")
    b.bed(11, 1, 3, "white", facing="south")
    b.set(10, 3, 2, "wall_torch", facing="south")
    b.fill_air()
    return b


# --- Healing Center: 11 x 8 x 9 ---------------------------------------------------------------
def healing_center():
    """White walls and a red roof. With Cobblemon installed the counter holds a Healing Machine
    (without Cobblemon that block loads as air and the builder skips it)."""
    b = Build(11, 8, 9)
    b.fill(0, 0, 0, 10, 0, 8, "smooth_stone")
    b.fill(1, 0, 1, 9, 0, 7, "white_concrete")
    for y in range(1, 4):
        b.ring(0, 0, 10, 8, y, "white_concrete")
    b.ring(0, 0, 10, 8, 4, "red_concrete")
    # Big front windows either side of the door, windows down each side
    for x in (1, 2, 3, 7, 8, 9):
        for y in (2, 3):
            b.set(x, y, 0, "glass_pane", north=False, south=False, east=True, west=True)
    for z in (2, 3, 5, 6):
        for x in (0, 10):
            b.set(x, 2, z, "glass_pane", north=True, south=True, east=False, west=False)
    b.door(5, 1, 0, "birch_door", facing="south")
    # Roof: a red slab over everything, a second red tier, a white emblem on top
    b.fill(0, 5, 0, 10, 5, 8, "red_concrete")
    b.fill(2, 6, 2, 8, 6, 6, "red_concrete")
    b.fill(4, 7, 3, 6, 7, 5, "white_concrete")
    b.set(5, 7, 4, "red_concrete")
    # Counter across the room with the Healing Machine in the middle
    for x in range(2, 9):
        if x != 5:
            b.set(x, 1, 5, "white_concrete")
    b.set(5, 1, 5, "cobblemon:healing_machine", facing="north")
    b.set(3, 2, 5, "red_carpet")
    b.set(7, 2, 5, "red_carpet")
    # Staff side: the Nurse Station (a villager moves in as the nurse) and storage
    b.set(1, 1, 7, "barrel", facing="up", open=False)
    b.set(9, 1, 7, "barrel", facing="up", open=False)
    b.set(2, 1, 7, "aliveworkplace:nurse_station", facing="south")
    # Waiting benches along the side walls, a red carpet to the counter
    for z in (2, 3):
        b.set(1, 1, z, "birch_stairs", facing="west", half="bottom", shape="straight")
        b.set(9, 1, z, "birch_stairs", facing="east", half="bottom", shape="straight")
    for z in range(1, 5):
        b.set(5, 1, z, "red_carpet")
    # Light
    for x in (3, 7):
        b.set(x, 4, 2, "lantern", hanging=True)
        b.set(x, 4, 6, "lantern", hanging=True)
    b.fill_air()
    return b


# --- Healing Center, upgraded: a ward with four beds behind the counter --------------------------------
def healing_center_2():
    """Upgrade of the Healing Center: a door in the back wall opens into a ward with four beds (villagers who sleep
    there are in the nurse's reach) under the same red roof."""
    b = healing_center().grow(11, 8, 13)
    b.fill(0, 0, 9, 10, 0, 12, "smooth_stone")
    b.fill(1, 0, 9, 9, 0, 11, "white_concrete")
    for y in range(1, 4):
        b.ring(0, 8, 10, 12, y, "white_concrete")
    b.ring(0, 8, 10, 12, 4, "red_concrete")
    b.fill(0, 5, 9, 10, 5, 12, "red_concrete")
    b.door(5, 1, 8, "birch_door", facing="south")
    for x in (0, 10):
        b.set(x, 2, 10, "glass_pane", north=True, south=True, east=False, west=False)
    for x in (2, 3, 7, 8):
        b.set(x, 2, 12, "glass_pane", north=False, south=False, east=True, west=True)
    for x in (1, 3, 7, 9):
        b.bed(x, 1, 10, "white", facing="south")
    b.set(5, 1, 11, "potted_poppy")
    for x in (3, 7):
        b.set(x, 4, 10, "lantern", hanging=True, waterlogged=False)
    b.fill_air()
    return b

# --- Healing Center III: a berry garden behind the ward -------------------------------------------------
def healing_center_3():
    """Upgrade of Healing Center II: a fenced garden behind the ward with sweet berry bushes, a Fruit Basket and a
    chest (a villager moves in as the orchard keeper and picks the berries)."""
    b = healing_center_2().grow(11, 8, 18)
    b.fill(0, 0, 13, 10, 0, 17, "grass_block", snowy=False)
    for x in range(0, 11):
        b.set(x, 1, 13, "birch_fence", north=False, south=False, east=x < 10, west=x > 0, waterlogged=False)
        b.set(x, 1, 17, "birch_fence", north=False, south=False, east=x < 10, west=x > 0, waterlogged=False)
    for z in range(14, 17):
        b.set(0, 1, z, "birch_fence", north=True, south=True, east=False, west=False, waterlogged=False)
        if z != 15:
            b.set(10, 1, z, "birch_fence", north=True, south=True, east=False, west=False, waterlogged=False)
    b.set(10, 1, 15, "birch_fence_gate", facing="east", open=False, in_wall=False, powered=False)
    for x in (3, 5, 7, 9):
        b.set(x, 1, 15, "sweet_berry_bush", age=0)
    for z in (14, 16):
        for x in range(3, 10):
            b.set(x, 0, z, "coarse_dirt")
    b.set(1, 1, 14, "aliveworkplace:fruit_basket", facing="east")
    b.set(1, 1, 16, "chest", facing="east", type="single", waterlogged=False)
    for x in (0, 10):
        b.set(x, 2, 17, "lantern", hanging=False, waterlogged=False)
    b.fill_air()
    return b


# --- Supply Shop: 9 x 7 x 8 -------------------------------------------------------------------
def supply_shop():
    """White walls and a blue roof; shelves of barrels and a Shop Counter."""
    b = Build(9, 7, 8)
    b.fill(0, 0, 0, 8, 0, 7, "smooth_stone")
    b.fill(1, 0, 1, 7, 0, 6, "birch_planks")
    for y in range(1, 4):
        b.ring(0, 0, 8, 7, y, "white_concrete")
    b.ring(0, 0, 8, 7, 4, "blue_concrete")
    for x in (1, 2, 6, 7):
        b.set(x, 2, 0, "glass_pane", north=False, south=False, east=True, west=True)
        b.set(x, 3, 0, "glass_pane", north=False, south=False, east=True, west=True)
    b.door(4, 1, 0, "birch_door", facing="south")
    b.fill(0, 5, 0, 8, 5, 7, "blue_concrete")
    b.fill(2, 6, 2, 6, 6, 5, "light_blue_concrete")
    # Shelves: barrels two high along both side walls
    for z in (2, 3, 4, 5):
        for y in (1, 2):
            b.set(1, y, z, "barrel", facing="east", open=False)
            b.set(7, y, z, "barrel", facing="west", open=False)
    # Counter at the back with a register (lantern) and a till (barrel); the middle of it is the Shop
    # Counter (a villager moves in as shopkeeper, the barrels are the stock; first to open it owns the shop)
    for x in (3, 5):
        b.set(x, 1, 5, "blue_concrete")
    b.set(4, 1, 5, "aliveworkplace:shop_counter", facing="south")
    b.set(3, 2, 5, "lantern", hanging=False)
    b.set(5, 2, 5, "barrel", facing="up", open=False)
    b.set(4, 1, 6, "birch_stairs", facing="south", half="bottom", shape="straight")  # shopkeeper's seat
    # Welcome mat and light
    b.set(4, 1, 1, "light_blue_carpet")
    b.set(4, 1, 2, "light_blue_carpet")
    b.set(4, 4, 3, "lantern", hanging=True)
    b.fill_air()
    return b


# --- Supply Shop, upgraded: a storeroom and the shopkeeper's bedroom upstairs -----------------------------
def supply_shop_2():
    """Upgrade of the Supply Shop: a second storey up a ladder behind the shelves, with a bed, a chest and more
    barrels of stock (still close enough to the Shop Counter to sell from)."""
    b = supply_shop().grow(9, 12, 8)
    b.clear(2, 6, 2, 6, 6, 5)  # the old light blue roof tier
    b.fill(1, 5, 1, 7, 5, 6, "birch_planks")
    for y in range(1, 6):
        b.set(7, y, 6, "ladder", facing="west", waterlogged=False)
    for y in range(6, 9):
        b.ring(0, 0, 8, 7, y, "white_concrete")
    b.ring(0, 0, 8, 7, 9, "blue_concrete")
    b.fill(0, 10, 0, 8, 10, 7, "blue_concrete")
    b.fill(2, 11, 2, 6, 11, 5, "light_blue_concrete")
    for x in (1, 2, 6, 7):
        b.set(x, 7, 0, "glass_pane", north=False, south=False, east=True, west=True)
    for x in (0, 8):
        for z in (3, 4):
            b.set(x, 7, z, "glass_pane", north=True, south=True, east=False, west=False)
    b.bed(1, 6, 5, "blue", facing="south")
    b.set(3, 6, 6, "chest", facing="north", type="single", waterlogged=False)
    for z in (1, 2, 3):
        b.set(7, 6, z, "barrel", facing="west", open=False)
    b.fill(3, 6, 2, 5, 6, 4, "light_blue_carpet")
    b.set(4, 9, 3, "lantern", hanging=True, waterlogged=False)
    b.fill_air()
    return b

# --- Supply Shop III: a post office annex --------------------------------------------------------------
def supply_shop_3():
    """Upgrade of Supply Shop II: a one-storey annex on the east side, through a doorway from the shop, with a Postal
    Desk and a chest (a villager moves in as the postman: the shop's goods can go out by mail)."""
    b = supply_shop_2().grow(15, 12, 8)
    b.fill(9, 0, 0, 14, 0, 6, "smooth_stone")
    b.fill(9, 0, 1, 13, 0, 5, "birch_planks")
    for y in range(1, 4):
        for x in range(9, 15):
            b.set(x, y, 0, "white_concrete")
            b.set(x, y, 6, "white_concrete")
        for z in range(1, 6):
            b.set(14, y, z, "white_concrete")
    for x in range(9, 15):
        b.set(x, 4, 0, "blue_concrete")
        b.set(x, 4, 6, "blue_concrete")
    for z in range(1, 6):
        b.set(14, 4, z, "blue_concrete")
    b.fill(9, 5, 0, 14, 5, 6, "blue_concrete")
    b.clear(8, 1, 1, 8, 2, 1)  # a doorway through the shop's east wall
    b.door(11, 1, 0, "birch_door", facing="south")
    for x in (9, 13):
        b.set(x, 2, 0, "glass_pane", north=False, south=False, east=True, west=True, waterlogged=False)
    for z in (2, 4):
        b.set(14, 2, z, "glass_pane", north=True, south=True, east=False, west=False, waterlogged=False)
    b.set(13, 1, 4, "aliveworkplace:postal_desk", facing="west")
    b.set(13, 1, 2, "chest", facing="west", type="single", waterlogged=False)
    b.set(10, 1, 5, "birch_stairs", facing="south", half="bottom", shape="straight", waterlogged=False)
    b.set(11, 1, 3, "light_blue_carpet")
    b.set(11, 4, 3, "lantern", hanging=True, waterlogged=False)
    b.fill_air()
    return b


# --- Storehouse: the Porter's workstation and the village's store ------------------------------------
def storehouse_chests(b, xs, z, facing, ys=(1, 2)):
    for x in xs:
        for y in ys:
            b.set(x, y, z, "chest", facing=facing, type="single", waterlogged=False)


def storehouse():
    """7 x 6 x 6: an open-fronted timber shed with a Storehouse and eight chests along the back wall (a villager
    moves in as the porter and fills them with what the village's workers make)."""
    b = Build(7, 6, 6)
    b.fill(0, 0, 0, 6, 0, 5, "cobblestone")
    b.fill(1, 0, 1, 5, 0, 4, "spruce_planks")
    for x, z in ((0, 0), (6, 0), (0, 5), (6, 5)):
        for y in range(1, 4):
            b.set(x, y, z, "oak_log", axis="y")
    for y in range(1, 4):
        for z in range(1, 5):
            b.set(0, y, z, "spruce_planks")
            b.set(6, y, z, "spruce_planks")
        for x in range(1, 6):
            b.set(x, y, 5, "spruce_planks")
    for x in (0, 6):
        b.set(x, 2, 2, "glass_pane", north=True, south=True, east=False, west=False, waterlogged=False)
    # A lean-to roof, low at the open front
    for x in range(7):
        b.set(x, 4, 0, "spruce_stairs", facing="south", half="bottom", shape="straight", waterlogged=False)
        for z in range(1, 6):
            b.set(x, 4, z, "spruce_planks")
        b.set(x, 5, 1, "spruce_stairs", facing="south", half="bottom", shape="straight", waterlogged=False)
        for z in range(2, 6):
            b.set(x, 5, z, "spruce_slab", type="bottom", waterlogged=False)
    # The Storehouse between two stacks of chests, a lantern on top
    b.set(3, 1, 4, "aliveworkplace:storehouse", facing="north")
    b.set(3, 2, 4, "lantern", hanging=False, waterlogged=False)
    storehouse_chests(b, (1, 2, 4, 5), 4, "north")
    b.set(1, 1, 1, "hay_block", axis="y")
    b.set(5, 1, 1, "hay_block", axis="y")
    b.set(3, 3, 1, "lantern", hanging=True, waterlogged=False)
    b.fill_air()
    return b


def storehouse_2():
    """Upgrade of the Storehouse: a second bay to the east through a doorway, eight more chests (a gap in the middle
    of them for the door the third tier puts there)."""
    b = storehouse().grow(13, 6, 6)
    b.fill(7, 0, 0, 12, 0, 5, "cobblestone")
    b.fill(7, 0, 1, 11, 0, 4, "spruce_planks")
    for z in (0, 5):
        for y in range(1, 4):
            b.set(12, y, z, "oak_log", axis="y")
    for y in range(1, 4):
        for z in range(1, 5):
            b.set(12, y, z, "spruce_planks")
        for x in range(7, 12):
            b.set(x, y, 5, "spruce_planks")
    b.set(12, 2, 2, "glass_pane", north=True, south=True, east=False, west=False, waterlogged=False)
    b.clear(6, 1, 2, 6, 2, 3)  # the doorway between the bays
    for x in range(7, 13):
        b.set(x, 4, 0, "spruce_stairs", facing="south", half="bottom", shape="straight", waterlogged=False)
        for z in range(1, 6):
            b.set(x, 4, z, "spruce_planks")
        b.set(x, 5, 1, "spruce_stairs", facing="south", half="bottom", shape="straight", waterlogged=False)
        for z in range(2, 6):
            b.set(x, 5, z, "spruce_slab", type="bottom", waterlogged=False)
    storehouse_chests(b, (7, 8, 10, 11), 4, "north")
    b.set(11, 1, 1, "hay_block", axis="y")
    b.set(9, 3, 1, "lantern", hanging=True, waterlogged=False)
    b.fill_air()
    return b


def storehouse_3():
    """Upgrade of Storehouse II: a stone warehouse behind both bays, through a door in the east bay's back wall, with
    sixteen more chests between spruce posts."""
    b = storehouse_2().grow(13, 7, 12)
    b.fill(0, 0, 6, 12, 0, 11, "stone_bricks")
    b.fill(1, 0, 6, 11, 0, 10, "spruce_planks")
    for y in range(1, 5):
        for z in range(6, 12):
            b.set(0, y, z, "stone_bricks")
            b.set(12, y, z, "stone_bricks")
        for x in range(1, 12):
            b.set(x, y, 11, "stone_bricks")
    for z in (8, 9):
        b.set(0, 2, z, "glass_pane", north=True, south=True, east=False, west=False, waterlogged=False)
        b.set(12, 2, z, "glass_pane", north=True, south=True, east=False, west=False, waterlogged=False)
    b.fill(0, 5, 6, 12, 5, 11, "spruce_planks")
    b.fill(1, 6, 7, 11, 6, 10, "spruce_slab", type="bottom", waterlogged=False)
    b.clear(9, 1, 5, 9, 2, 5)
    b.door(9, 1, 5, "spruce_door", facing="south")
    storehouse_chests(b, (1, 2, 4, 5, 7, 8, 10, 11), 10, "north")
    for x in (3, 6, 9):
        for y in (1, 2):
            b.set(x, y, 10, "stripped_spruce_log", axis="y")
    for x in (3, 9):
        b.set(x, 4, 8, "lantern", hanging=True, waterlogged=False)
    b.fill_air()
    return b


# --- Berry Farm: an orchard keeper's garden --------------------------------------------------------------
def berry_farm():
    """11 x 4 x 9: a fenced garden of sweet berry bushes either side of a path, with a Fruit Basket and a chest by the
    back fence (a villager moves in as the orchard keeper and picks the berries)."""
    b = Build(11, 4, 9)
    b.fill(0, 0, 0, 10, 0, 8, "grass_block", snowy=False)
    for z in range(0, 8):
        b.set(5, 0, z, "dirt_path")
    for x in range(0, 11):
        for z in (0, 8):
            if (x, z) != (5, 0):
                b.set(x, 1, z, "spruce_fence", north=False, south=False, east=x < 10, west=x > 0, waterlogged=False)
    for z in range(1, 8):
        for x in (0, 10):
            b.set(x, 1, z, "spruce_fence", north=True, south=True, east=False, west=False, waterlogged=False)
    b.set(5, 1, 0, "spruce_fence_gate", facing="south", open=False, in_wall=False, powered=False)
    for x in (1, 3, 7, 9):
        for z in (2, 4, 6):
            b.set(x, 1, z, "sweet_berry_bush", age=0)
    b.set(4, 1, 7, "aliveworkplace:fruit_basket", facing="north")
    b.set(6, 1, 7, "chest", facing="north", type="single", waterlogged=False)
    for x, z in ((0, 0), (10, 0), (0, 8), (10, 8)):
        b.set(x, 2, z, "lantern", hanging=False, waterlogged=False)
    b.fill_air()
    return b


def berry_farm_2():
    """Upgrade of the Berry Farm: through a gate in the back fence, a pergola with glow berries hanging from its roof and
    more bushes underneath."""
    b = berry_farm().grow(11, 5, 15)
    b.fill(0, 0, 9, 10, 0, 14, "grass_block", snowy=False)
    b.set(5, 0, 8, "dirt_path")
    b.set(5, 1, 8, "spruce_fence_gate", facing="south", open=False, in_wall=False, powered=False)
    for z in range(9, 14):
        for x in (0, 10):
            b.set(x, 1, z, "spruce_fence", north=True, south=True, east=False, west=False, waterlogged=False)
    for x in range(0, 11):
        b.set(x, 1, 14, "spruce_fence", north=False, south=False, east=x < 10, west=x > 0, waterlogged=False)
    for x, z in ((2, 10), (8, 10), (2, 13), (8, 13)):
        for y in range(1, 4):
            b.set(x, y, z, "spruce_log", axis="y")
    b.fill(2, 4, 10, 8, 4, 13, "spruce_planks")
    for x in (3, 5, 7):
        for z in (11, 12):
            b.set(x, 3, z, "cave_vines", age=0, berries=False)
    for x in (4, 6):
        for z in (11, 12):
            b.set(x, 1, z, "sweet_berry_bush", age=0)
    for z in range(9, 14):
        b.set(5, 0, z, "dirt_path")
    b.set(5, 3, 10, "lantern", hanging=True, waterlogged=False)
    b.fill_air()
    return b


# --- Research Lab: the fossil scientist's -------------------------------------------------------------
def research_lab():
    """9 x 6 x 9: a stone lab with a Fossil Lab at the back (a villager moves in as the fossil scientist; with Cobblemon
    they revive fossils), bookshelves, a glass case and a bone-block skeleton on show."""
    b = Build(9, 6, 9)
    b.fill(0, 0, 0, 8, 0, 8, "polished_andesite")
    for y in range(1, 4):
        b.ring(0, 0, 8, 8, y, "stone_bricks")
    for z in (2, 3, 5, 6):
        for x in (0, 8):
            b.set(x, 2, z, "glass_pane", north=True, south=True, east=False, west=False, waterlogged=False)
    for x in (2, 6):
        b.set(x, 2, 0, "glass_pane", north=False, south=False, east=True, west=True, waterlogged=False)
    b.door(4, 1, 0, "spruce_door", facing="south")
    b.fill(0, 4, 0, 8, 4, 8, "polished_andesite")
    b.fill(1, 5, 1, 7, 5, 7, "stone_brick_slab", type="bottom", waterlogged=False)
    b.set(4, 1, 7, "aliveworkplace:fossil_lab", facing="north")
    for x in (1, 7):
        for y in (1, 2):
            b.set(x, y, 7, "bookshelf")
    b.set(3, 1, 6, "spruce_stairs", facing="south", half="bottom", shape="straight", waterlogged=False)
    # A fossil on show: a spine and ribs of bone blocks
    b.set(1, 1, 3, "bone_block", axis="y")
    b.set(1, 2, 3, "bone_block", axis="y")
    b.set(2, 2, 3, "bone_block", axis="x")
    b.set(1, 1, 4, "polished_andesite")
    # A glass case
    b.set(7, 1, 3, "polished_andesite")
    b.set(7, 2, 3, "glass")
    b.set(4, 3, 4, "lantern", hanging=True, waterlogged=False)
    b.fill_air()
    return b


def research_lab_2():
    """Upgrade of the Research Lab: a museum hall to the east through a doorway, with a big skeleton of bone blocks under
    a glass roof and more cases."""
    b = research_lab().grow(15, 6, 9)
    b.fill(9, 0, 0, 14, 0, 8, "polished_andesite")
    for y in range(1, 4):
        for x in range(9, 15):
            b.set(x, y, 0, "stone_bricks")
            b.set(x, y, 8, "stone_bricks")
        for z in range(1, 8):
            b.set(14, y, z, "stone_bricks")
    for x in range(9, 15):
        b.set(x, 4, 0, "polished_andesite")
        b.set(x, 4, 8, "polished_andesite")
    for z in range(1, 8):
        b.set(14, 4, z, "polished_andesite")
    b.fill(9, 4, 1, 13, 4, 7, "glass")
    b.clear(8, 1, 4, 8, 2, 4)  # the doorway from the lab
    for x in (10, 12):
        b.set(x, 2, 0, "glass_pane", north=False, south=False, east=True, west=True, waterlogged=False)
    # The skeleton: legs, a spine, a neck and a skull
    for x in (10, 12):
        for z in (3, 5):
            b.set(x, 1, z, "bone_block", axis="y")
    for x in range(10, 13):
        b.set(x, 2, 4, "bone_block", axis="x")
    b.set(10, 2, 3, "bone_block", axis="z")
    b.set(10, 2, 5, "bone_block", axis="z")
    b.set(12, 2, 3, "bone_block", axis="z")
    b.set(12, 2, 5, "bone_block", axis="z")
    b.set(13, 2, 4, "bone_block", axis="y")  # the neck, and the skull on top (the doorway side stays clear)
    b.set(13, 3, 4, "skeleton_skull", rotation=12)
    for z in (1, 7):
        b.set(13, 1, z, "polished_andesite")
        b.set(13, 2, z, "glass")
    b.fill_air()
    return b


# --- Village builder's workshops: one per village type, added to the vanilla house pools ------
VILLAGE_STYLES = {
    #          floor              walls            corners                 roof stairs         roof slab            door           bed
    "plains":  ("cobblestone",      "oak_planks",     "oak_log",              "oak_stairs",       "oak_slab",          "oak_door",    "red_bed"),
    "desert":  ("smooth_sandstone", "cut_sandstone",  "chiseled_sandstone",   "sandstone_stairs", "sandstone_slab",    "jungle_door", "yellow_bed"),
    "savanna": ("acacia_planks",    "acacia_planks",  "acacia_log",           "acacia_stairs",    "acacia_slab",       "acacia_door", "orange_bed"),
    "snowy":   ("spruce_planks",    "white_terracotta", "stripped_spruce_log", "spruce_stairs",   "spruce_slab",       "spruce_door", "light_blue_bed"),
    "taiga":   ("cobblestone",      "spruce_planks",  "spruce_log",           "spruce_stairs",    "spruce_slab",       "spruce_door", "brown_bed"),
}
VILLAGE_STRUCTURES = os.path.join(MAIN_STRUCTURES, "village")


def builders_workshop(style):
    """7 x 8 x 8. The street connects at the front (z = 0); inside: a Builder's Bench, a chest of
    building supplies, a bed and a villager spawn, so a builder moves in on its own."""
    floor, walls, corners, stairs, slab, door, bed = VILLAGE_STYLES[style]
    b = Build(7, 8, 8)
    # Front row stays open ground: keep the terrain there (structure void), air above.
    for x in range(7):
        b.set(x, 0, 0, "structure_void")
    # Street connection, in front of the door, one block above the floor like vanilla houses.
    b.set(3, 1, 0, "jigsaw", orientation="north_up")
    b.set_nbt(3, 1, 0, Compound({
        "name": String("minecraft:building_entrance"), "target": String("minecraft:building_entrance"),
        "pool": String("minecraft:empty"), "final_state": String("minecraft:structure_void"),
        "joint": String("aligned"), "id": String("minecraft:jigsaw"),
        "selection_priority": Int(0), "placement_priority": Int(0)}))
    b.fill(0, 0, 1, 6, 0, 7, floor)
    for y in range(1, 4):
        b.ring(0, 1, 6, 7, y, walls)
        for x, z in ((0, 1), (6, 1), (0, 7), (6, 7)):
            b.set(x, y, z, corners, **({"axis": "y"} if "log" in corners else {}))
    # Door and windows
    b.door(3, 1, 1, door, facing="south")
    for x in (1, 5):
        b.set(x, 2, 1, "glass_pane", north=False, south=False, east=True, west=True)
    for z in (3, 5):
        b.set(0, 2, z, "glass_pane", north=True, south=True, east=False, west=False)
        b.set(6, 2, z, "glass_pane", north=True, south=True, east=False, west=False)
    # Gable roof running front to back
    for i, y in enumerate(range(4, 7)):
        for z in range(1, 8):
            b.set(i, y, z, stairs, facing="east", half="bottom", shape="straight")
            b.set(6 - i, y, z, stairs, facing="west", half="bottom", shape="straight")
    for z in range(1, 8):
        b.set(3, 7, z, slab, type="bottom")
    # Gable ends
    for y, (x0, x1) in ((4, (1, 5)), (5, (2, 4)), (6, (3, 3))):
        for x in range(x0, x1 + 1):
            b.set(x, y, 1, walls)
            b.set(x, y, 7, walls)
    # Workshop: bench, supplies, a place to sleep
    b.set(1, 1, 6, "aliveworkplace:builders_bench", facing="east")
    b.set(1, 1, 5, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_builders_workshop"), "id": String("minecraft:chest")}))
    # (no barrel: it is a fisherman's job block and would steal the villager)
    b.set(1, 1, 4, corners, **({"axis": "y"} if "log" in corners else {}))
    b.set(1, 1, 2, "crafting_table")
    b.bed(5, 1, 5, bed.replace("_bed", ""), facing="south")
    b.set(5, 1, 2, "scaffolding", bottom=False, distance=0, waterlogged=False)
    b.set(3, 3, 6, "wall_torch", facing="north")
    b.set(3, 3, 2, "wall_torch", facing="south")
    # Where the village's villager for this house appears (it takes the bench as its job)
    b.set(3, 0, 4, "jigsaw", orientation="up_north")
    b.set_nbt(3, 0, 4, Compound({
        "name": String("minecraft:bottom"), "target": String("minecraft:bottom"),
        "pool": String(f"aliveworkplace:village/{style}/workers"), "final_state": String("minecraft:" + floor),
        "joint": String("rollable"), "id": String("minecraft:jigsaw"),
        "selection_priority": Int(0), "placement_priority": Int(0)}))
    b.fill_air()
    return b


# --- Other village houses: the same small house, fitted out for another job ---------------------------
def staffed_house(style, fit_out):
    """7 x 8 x 8 like the builder's workshop (street at z = 0, a bed, a villager spawn), with the inside
    fitted out by {@code fit_out(b, style)} for another job."""
    floor, walls, corners, stairs, slab, door, bed = VILLAGE_STYLES[style]
    b = Build(7, 8, 8)
    for x in range(7):
        b.set(x, 0, 0, "structure_void")
    b.set(3, 1, 0, "jigsaw", orientation="north_up")
    b.set_nbt(3, 1, 0, Compound({
        "name": String("minecraft:building_entrance"), "target": String("minecraft:building_entrance"),
        "pool": String("minecraft:empty"), "final_state": String("minecraft:structure_void"),
        "joint": String("aligned"), "id": String("minecraft:jigsaw"),
        "selection_priority": Int(0), "placement_priority": Int(0)}))
    b.fill(0, 0, 1, 6, 0, 7, floor)
    for y in range(1, 4):
        b.ring(0, 1, 6, 7, y, walls)
        for x, z in ((0, 1), (6, 1), (0, 7), (6, 7)):
            b.set(x, y, z, corners, **({"axis": "y"} if "log" in corners else {}))
    b.door(3, 1, 1, door, facing="south")
    for x in (1, 5):
        b.set(x, 2, 1, "glass_pane", north=False, south=False, east=True, west=True)
    for z in (3, 5):
        b.set(0, 2, z, "glass_pane", north=True, south=True, east=False, west=False)
        b.set(6, 2, z, "glass_pane", north=True, south=True, east=False, west=False)
    for i, y in enumerate(range(4, 7)):
        for z in range(1, 8):
            b.set(i, y, z, stairs, facing="east", half="bottom", shape="straight")
            b.set(6 - i, y, z, stairs, facing="west", half="bottom", shape="straight")
    for z in range(1, 8):
        b.set(3, 7, z, slab, type="bottom")
    for y, (x0, x1) in ((4, (1, 5)), (5, (2, 4)), (6, (3, 3))):
        for x in range(x0, x1 + 1):
            b.set(x, y, 1, walls)
            b.set(x, y, 7, walls)
    fit_out(b, style)
    b.bed(5, 1, 5, bed.replace("_bed", ""), facing="south")
    b.set(3, 3, 6, "wall_torch", facing="north")
    b.set(3, 3, 2, "wall_torch", facing="south")
    b.set(3, 0, 4, "jigsaw", orientation="up_north")
    b.set_nbt(3, 0, 4, Compound({
        "name": String("minecraft:bottom"), "target": String("minecraft:bottom"),
        "pool": String(f"aliveworkplace:village/{style}/workers"), "final_state": String("minecraft:" + floor),
        "joint": String("rollable"), "id": String("minecraft:jigsaw"),
        "selection_priority": Int(0), "placement_priority": Int(0)}))
    b.fill_air()
    return b


def trainers_house(b, style):
    """A Training Post, a sparring target and a trophy shelf."""
    b.set(1, 1, 6, "aliveworkplace:training_post", facing="east")
    b.set(1, 1, 3, "target")
    b.set(1, 2, 3, "target")
    b.set(1, 1, 5, "bookshelf")
    b.set(1, 2, 5, "flower_pot")


def guard_house(b, style):
    """A Guard Post and a chest of starting gear."""
    b.set(1, 1, 6, "aliveworkplace:guard_post", facing="east")
    b.set(1, 1, 5, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_guard_house"), "id": String("minecraft:chest")}))
    b.set(1, 1, 3, "anvil", facing="north")


# No vanilla job blocks in these houses (a cauldron, lectern, barrel...): the villager who moves in could
# take that job instead of ours.
def clinic(b, style):
    """A Nurse Station, a cot and flowers."""
    b.set(1, 1, 6, "aliveworkplace:nurse_station", facing="east")
    b.set(1, 1, 3, "white_carpet")
    b.set(1, 1, 4, "potted_poppy")


def post_office(b, style):
    """A Postal Desk with a sorting shelf."""
    b.set(1, 1, 6, "aliveworkplace:postal_desk", facing="east")
    b.set(1, 1, 5, "chiseled_bookshelf", facing="east")
    b.set(1, 1, 3, "bookshelf")


def leaders_hall(b, style):
    """The Trainer Leader's Podium between two polished andesite pillars, and a target to spar with."""
    b.set(1, 1, 6, "aliveworkplace:leaders_podium", facing="east")
    b.set(1, 1, 5, "polished_andesite")
    b.set(1, 2, 5, "polished_andesite")
    b.set(1, 1, 3, "target")
    b.set(1, 2, 3, "lantern", hanging=False, waterlogged=False)


def school(b, style):
    """A Tutor's Desk and bookshelves."""
    b.set(1, 1, 6, "aliveworkplace:tutors_desk", facing="east")
    b.set(1, 1, 5, "bookshelf")
    b.set(1, 2, 5, "bookshelf")
    b.set(1, 1, 3, "bookshelf")
    b.set(1, 2, 3, "flower_pot")


def trade_hall(b, style):
    """A Trade Board and somewhere to sit down and haggle."""
    b.set(1, 1, 6, "aliveworkplace:trade_board", facing="east")
    b.set(1, 1, 4, "oak_stairs", facing="east", half="bottom", shape="straight", waterlogged=False)
    b.set(1, 1, 3, "potted_fern")


def orchard_house(b, style):
    """A Fruit Basket, a chest for the harvest and an indoor bed of sweet berry bushes to pick."""
    b.set(1, 1, 6, "aliveworkplace:fruit_basket", facing="east")
    b.set(1, 1, 5, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_orchard"), "id": String("minecraft:chest")}))
    for x in (2, 3):
        b.set(x, 0, 6, "grass_block", snowy=False)
        b.set(x, 1, 6, "sweet_berry_bush", age=3)
    b.set(1, 1, 3, "potted_azalea_bush")


def ball_workshop(b, style):
    """A Ball Workbench, a chest of copper and dye, and an anvil."""
    b.set(1, 1, 6, "aliveworkplace:ball_workbench", facing="east")
    b.set(1, 1, 5, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_ball_workshop"), "id": String("minecraft:chest")}))
    b.set(1, 1, 3, "anvil", facing="north")


def ferry_house(b, style):
    """A Travel Post (it joins the travel network under a village name) and a bench for passengers."""
    b.set(1, 1, 6, "aliveworkplace:travel_post", facing="east")
    b.set(1, 1, 4, "spruce_stairs", facing="east", half="bottom", shape="straight", waterlogged=False)
    b.set(1, 1, 3, "spruce_stairs", facing="east", half="bottom", shape="straight", waterlogged=False)
    b.set(1, 2, 5, "lantern", hanging=False, waterlogged=False)
    b.set(1, 1, 5, "spruce_planks")


def storehouse_room(b, style):
    """A Storehouse and four empty chests: the village's store (the porter who moves in fills them)."""
    b.set(1, 1, 6, "aliveworkplace:storehouse", facing="east")
    for z in (4, 5):
        for y in (1, 2):
            b.set(1, y, z, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_storehouse"), "id": String("minecraft:chest")}))
    b.set(1, 1, 3, "hay_block", axis="y")


def carpenters_workshop(b, style):
    """A Carpenter's Bench, a chest of wood and a sawhorse: the carpenter makes what the village's builders are waiting for."""
    b.set(1, 1, 6, "aliveworkplace:carpenters_bench", facing="east")
    b.set(1, 1, 5, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_carpenters_workshop"), "id": String("minecraft:chest")}))
    b.set(1, 1, 3, "oak_log", axis="z")
    b.set(1, 1, 2, "oak_fence", north=False, south=False, east=False, west=False, waterlogged=False)


def kitchen(b, style):
    """A Kitchen Stove, a chest of the village's produce and a little table: the chef cooks for the village."""
    b.set(1, 1, 6, "aliveworkplace:kitchen_stove", facing="east")
    b.set(1, 1, 5, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_kitchen"), "id": String("minecraft:chest")}))
    b.set(1, 1, 3, "oak_fence", north=False, south=False, east=False, west=False, waterlogged=False)
    b.set(1, 2, 3, "oak_pressure_plate", powered=False)
    b.set(2, 1, 3, "oak_stairs", facing="west", half="bottom", shape="straight", waterlogged=False)


def fossil_lab(b, style):
    """A Fossil Lab, a shelf of books and a glass case: the fossil scientist revives fossils here (with Cobblemon)."""
    b.set(1, 1, 6, "aliveworkplace:fossil_lab", facing="east")
    b.set(1, 1, 5, "bookshelf")
    b.set(1, 1, 3, "glass")
    b.set(1, 2, 3, "bone_block", axis="y")


VILLAGE_HOUSES = {"trainers_house": trainers_house, "guard_house": guard_house, "clinic": clinic, "post_office": post_office,
                  "leaders_hall": leaders_hall, "school": school, "trade_hall": trade_hall, "orchard_house": orchard_house,
                  "ball_workshop": ball_workshop, "ferry_house": ferry_house, "storehouse": storehouse_room,
                  "carpenters_workshop": carpenters_workshop, "kitchen": kitchen,
                  "fossil_lab": fossil_lab}


# --- Gametest fixtures ------------------------------------------------------------------
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


def test_hut_2():
    """The test hut's upgrade: a lantern on the roof (a builder sells it once they've built the hut)."""
    b = test_hut().grow(5, 5, 5)
    b.set(2, 4, 2, "lantern", hanging=False, waterlogged=False)
    b.fill_air()
    return b


def test_area(name, w, h, d, folder=TEST_AREAS):
    """Flat smooth-stone floor at y=0, empty above. Saved as SNBT in the *packed* layout the gametest
    loader expects (palette of state strings, `data` entries with a `state` string)."""
    os.makedirs(folder, exist_ok=True)
    data = [Compound({"pos": List[Int]([Int(x), Int(0), Int(z)]), "state": String("minecraft:smooth_stone")})
            for x in range(w) for z in range(d)]
    tag = Compound({
        "DataVersion": Int(DATA_VERSION),
        "size": List[Int]([Int(w), Int(h), Int(d)]),
        "data": List[Compound](data),
        "entities": List[Compound]([]),
        "palette": List[String]([String("minecraft:smooth_stone")]),
    })
    path = os.path.join(folder, name + ".snbt")
    with open(path, "w") as f:
        f.write(nbtlib.serialize_tag(tag))
    print(f"{name}: test area {w}x{h}x{d} -> {os.path.relpath(path, ROOT)}")


if __name__ == "__main__":
    starter_cottage().save(MAIN_STRUCTURES, "starter_cottage")
    starter_cottage_2().save(MAIN_STRUCTURES, "starter_cottage_2")
    starter_cottage_3().save(MAIN_STRUCTURES, "starter_cottage_3")
    market_stall().save(MAIN_STRUCTURES, "market_stall")
    market_stall_2().save(MAIN_STRUCTURES, "market_stall_2")
    market_stall_3().save(MAIN_STRUCTURES, "market_stall_3")
    lookout_tower().save(MAIN_STRUCTURES, "lookout_tower")
    lookout_tower_2().save(MAIN_STRUCTURES, "lookout_tower_2")
    lookout_tower_3().save(MAIN_STRUCTURES, "lookout_tower_3")
    healing_center().save(MAIN_STRUCTURES, "healing_center")
    healing_center_2().save(MAIN_STRUCTURES, "healing_center_2")
    healing_center_3().save(MAIN_STRUCTURES, "healing_center_3")
    supply_shop().save(MAIN_STRUCTURES, "supply_shop")
    supply_shop_2().save(MAIN_STRUCTURES, "supply_shop_2")
    supply_shop_3().save(MAIN_STRUCTURES, "supply_shop_3")
    storehouse().save(MAIN_STRUCTURES, "storehouse")
    storehouse_2().save(MAIN_STRUCTURES, "storehouse_2")
    storehouse_3().save(MAIN_STRUCTURES, "storehouse_3")
    berry_farm().save(MAIN_STRUCTURES, "berry_farm")
    berry_farm_2().save(MAIN_STRUCTURES, "berry_farm_2")
    research_lab().save(MAIN_STRUCTURES, "research_lab")
    research_lab_2().save(MAIN_STRUCTURES, "research_lab_2")
    for style in VILLAGE_STYLES:
        builders_workshop(style).save(VILLAGE_STRUCTURES, f"{style}_builders_workshop")
        for name, fit_out in VILLAGE_HOUSES.items():
            staffed_house(style, fit_out).save(VILLAGE_STRUCTURES, f"{style}_{name}")
    test_hut().save(TEST_STRUCTURES, "test_hut")
    test_hut_2().save(TEST_STRUCTURES, "test_hut_2")
    test_area("build_area", 17, 8, 17)
    test_area("big_area", 22, 18, 22)
    test_area("build_area", 17, 8, 17, COMPAT_AREAS)
