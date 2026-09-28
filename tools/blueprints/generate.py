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


VILLAGE_HOUSES = {"trainers_house": trainers_house, "guard_house": guard_house, "clinic": clinic, "post_office": post_office,
                  "leaders_hall": leaders_hall, "school": school, "trade_hall": trade_hall, "orchard_house": orchard_house,
                  "ball_workshop": ball_workshop}


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
    market_stall().save(MAIN_STRUCTURES, "market_stall")
    lookout_tower().save(MAIN_STRUCTURES, "lookout_tower")
    healing_center().save(MAIN_STRUCTURES, "healing_center")
    supply_shop().save(MAIN_STRUCTURES, "supply_shop")
    for style in VILLAGE_STYLES:
        builders_workshop(style).save(VILLAGE_STRUCTURES, f"{style}_builders_workshop")
        for name, fit_out in VILLAGE_HOUSES.items():
            staffed_house(style, fit_out).save(VILLAGE_STRUCTURES, f"{style}_{name}")
    test_hut().save(TEST_STRUCTURES, "test_hut")
    test_area("build_area", 17, 8, 17)
    test_area("big_area", 22, 18, 22)
    test_area("build_area", 17, 8, 17, COMPAT_AREAS)
