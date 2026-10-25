"""ROADMAP 37.17: the storage builds, each a Storehouse (a porter moves in, the village counts it as one) and each drawn
twice from one function: the vanilla build (`structure/<name>.nbt`: chests, barrels, no terminal) and its Tom's Simple
Storage copy (`structure/modded/<name>.nbt`, the modded library of 37.12: built in its place when every block of it is
a real block on the server, so only with Tom's installed). The copy keeps the vanilla build's shape: only the cells
below change, one for one.

| Tom's block (2.3.0, ids from its jar) | In the vanilla build |
|---|---|
| `inventory_connector` | a chest (one more store) |
| `storage_terminal` | a stripped spruce counter block |
| `crafting_terminal` | a spruce counter stair (never the crafting table: B388) |
| `inventory_cable_framed`, `inventory_cable_connector_framed` (floor runs) | spruce planks (the floor) |
| `trim` (floor inlays and a deck post) | stripped spruce logs |
| `filing_cabinet` | a chest |

What a Tom's network joins (StorageNetworks.members, 37.10): the inventories touching the connector or each other
(through trims), within 16 blocks, and the one inventory each cable connector faces. Every store is within the
Storehouse's reach (8 across, 4 up or down from the Storehouse block), so the vanilla build's chests are all its own.

Job blocks stay (B388): the crafting table is the Carpenter's job site and Tom's crafting terminal is none, so the
copies keep the vanilla build's crafting table and set the crafting terminal into the counter beside it instead.
"""
from kit import *

TOMS = "toms_storage:"
FRAME = "spruce_log"
INFILL = "birch_planks"
ROOF = DARK_OAK


class Tom:
    """Draws a cell as Tom's block in the copy, its vanilla stand-in in the vanilla build."""

    def __init__(self, toms):
        self.toms = toms

    def set(self, b, x, y, z, toms_block, vanilla, toms_props=None, vanilla_props=None):
        if self.toms:
            b.set(x, y, z, TOMS + toms_block, **(toms_props or {}))
        else:
            b.set(x, y, z, vanilla, **(vanilla_props or {}))

    def connector(self, b, x, y, z):
        # A chest, not a barrel: a barrel is a Fisherman's job block, and the copy never changes a job block
        self.set(b, x, y, z, "inventory_connector", "chest", vanilla_props={"facing": "north", "type": "single", "waterlogged": False})

    def cable(self, b, x, y, z, floor="spruce_planks"):
        """A framed cable laid in the floor (a full block, flush with the boards)."""
        self.set(b, x, y, z, "inventory_cable_framed", floor, toms_props=_closed())

    def cable_connector(self, b, x, y, z, facing, floor="spruce_planks"):
        self.set(b, x, y, z, "inventory_cable_connector_framed", floor, toms_props={"facing": facing, **_closed()})

    def trim(self, b, x, y, z, axis="x"):
        self.set(b, x, y, z, "trim", "stripped_spruce_log", vanilla_props={"axis": axis})

    def terminal(self, b, x, y, z, facing, pos="center", crafting=False, counter="south"):
        """A terminal reading the block on its {@code facing} side (pos center) or under it (pos down: a terminal set
        into a counter, its screen up). Vanilla: a counter stair (rising to {@code counter}) for the crafting terminal,
        a counter block for the other. Never a crafting table: that's the Carpenter's job site, which the copy keeps (B388)."""
        props = {"facing": facing, "pos": pos, "waterlogged": False}
        if crafting:
            self.set(b, x, y, z, "crafting_terminal", SPRUCE.stairs, toms_props=props,
                     vanilla_props={"facing": counter, "half": "top", "shape": "straight", "waterlogged": False})
        else:
            self.set(b, x, y, z, "storage_terminal", "stripped_spruce_wood", toms_props=props, vanilla_props={"axis": "y"})

    def cabinet(self, b, x, y, z, facing):
        self.set(b, x, y, z, "filing_cabinet", "chest", toms_props={"facing": facing},
                 vanilla_props={"facing": facing, "type": "single", "waterlogged": False})


def _closed():
    return {d: False for d in ("north", "south", "east", "west", "up", "down")}


def store(b, x, y, z, facing, chest):
    if chest:
        b.set(x, y, z, "chest", facing=facing, type="single", waterlogged=False)
    else:
        b.set(x, y, z, "barrel", facing=facing, open=False)


def rack(b, x0, x1, z, ys, facing, flip=0):
    """A rack of stores along x at z, columns of chests and barrels by turns (a chest is never under a barrel, which
    would keep its lid shut)."""
    for x in range(x0, x1 + 1):
        for y in ys:
            store(b, x, y, z, facing, (x + flip) % 2 == 0)


# --- the Terminal Nook --------------------------------------------------------------------------------------------

def terminal_nook(toms=False):
    """9 x 10 x 9: the Terminal Nook, a small store room that fits beside a house. A stone-footed, birch-and-spruce room
    under a dark oak gable; inside along the back wall, six chests round an inventory connector (a chest without
    Tom's) with a potted fern on it (a lantern can't stand on a chest); a storage terminal in front of the connector,
    its screen to the door (without Tom's a counter block there); the Storehouse by the side wall, a barrel and hay by the door."""
    t = Tom(toms)
    b = Build(9, 10, 9)
    plinth(b, 1, 2, 7, 7, FOUNDATION_MIX, floor="spruce_planks")
    walls(b, 1, 2, 7, 7, 1, 1, STONE_MIX)
    walls(b, 1, 2, 7, 7, 2, 3, INFILL)
    posts(b, [(1, 2), (7, 2), (1, 7), (7, 7), (3, 2), (5, 2), (4, 7)], 1, 3, FRAME)
    beam_ring(b, 1, 2, 7, 7, 4, FRAME)
    gable_roof(b, 0, 8, 1, 8, 5, ROOF, axis="x", gable=INFILL, gable_at=(1, 7), ridge=ROOF, eave_trim=SPRUCE)
    b.door(4, 1, 2, "spruce_door", "south")
    stairs(b, 4, 0, 1, STONE_BRICK, "south")
    for x in (3, 5):
        lantern(b, x, 1, 1)
        b.set(x, 0, 1, "stone_bricks")
    window(b, 1, 2, 5, "west", width=2, shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 7, 2, 4, "east", width=2, shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 2, 2, 7, "south")
    window(b, 6, 2, 7, "south")
    # The store: six chests along the back wall round the connector, the terminal reading it
    t.connector(b, 4, 1, 6)
    for x in (2, 3, 5, 6):
        store(b, x, 1, 6, "north", True)
    for x in (3, 5):
        store(b, x, 2, 6, "north", True)
    b.set(4, 2, 6, "potted_fern")  # not a lantern: it falls off the chest that stands for the connector without Tom's
    t.terminal(b, 4, 1, 5, "south")  # without Tom's a counter block, so the copy only swaps it (no block in an empty cell)
    b.set(2, 1, 4, "aliveworkplace:storehouse", facing="east")
    b.set(6, 1, 3, "barrel", facing="up", open=False)
    b.set(6, 2, 3, "hay_block", axis="y")
    lantern(b, 4, 3, 4, hanging=True)
    for z in range(3, 7):
        b.set(4, 4, z, FRAME, axis="z")  # a tie beam the lantern hangs from
    b.fill_air()
    return b


# --- the Storage Hall ---------------------------------------------------------------------------------------------

def storage_hall(toms=False):
    """13 x 14 x 14: the Storage Hall. A long stone-and-birch hall under a dark oak gable, its gable end to the street
    with a porch over the door. Inside, 27 chests and barrels stand in an island round an inventory connector; a framed
    cable in the floor runs from the connector to a counter by the door (a storage terminal and a crafting terminal set
    into it either side of the Storehouse, the crafting table beside it) and back to two filing cabinets for the paper and maps against the back
    wall, each read through a cable connector under it. Without Tom's: chests for the connector and the cabinets, a
    counter block and a counter stair for the terminals, plain boards for the cable. The crafting table (the Carpenter's
    job site) is in both (B388)."""
    t = Tom(toms)
    b = Build(13, 14, 14)
    x0, z0, x1, z1 = 1, 3, 11, 12
    plinth(b, x0, z0, x1, z1, FOUNDATION_MIX, floor="spruce_planks")
    walls(b, x0, z0, x1, z1, 1, 2, STONE_MIX)
    walls(b, x0, z0, x1, z1, 3, 4, INFILL)
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        box(b, x, 1, z, x, 2, z, "stone_bricks")  # quoins
    posts(b, [(4, z0), (8, z0), (4, z1), (8, z1)] + [(x, z) for x in (x0, x1) for z in (6, 9)], 1, 4, FRAME)
    posts(b, [(x0, z0), (x1, z0), (x0, z1), (x1, z1)], 3, 4, FRAME)
    beam_ring(b, x0, z0, x1, z1, 5, FRAME)
    for z in (4, 7, 10):
        window(b, x0, 3, z + 1, "west", width=2, shutters="spruce_trapdoor", sill=STONE_BRICK)
        window(b, x1, 3, z, "east", width=2, shutters="spruce_trapdoor", sill=STONE_BRICK)
    for x in (2, 10):
        window(b, x, 3, z0, "north", sill=STONE_BRICK)
    window(b, 6, 3, z1, "south", width=1, sill=STONE_BRICK)
    for x in (2, 10):
        window(b, x, 3, z1, "south", sill=STONE_BRICK)

    def main_roof(r):
        gable_roof(r, 0, 12, 2, 13, 6, ROOF, axis="z", gable=INFILL, gable_at=(z0, z1), ridge=ROOF, eave_trim=SPRUCE)

    def porch_roof(r):
        gable_roof(r, 3, 9, 0, 3, 4, ROOF, axis="z", gable=INFILL, gable_at=(1,), ridge=ROOF, eave_trim=SPRUCE)

    roofs(b, main_roof, porch_roof)
    # The gable ends: a king post and a little window high up
    for z in (z0, z1):
        for y in range(6, 10):
            if b.get(6, y, z) and b.get(6, y, z)[0] == "minecraft:" + INFILL:
                log(b, 6, y, z, FRAME)
        pane(b, 5, 7, z)
        pane(b, 7, 7, z)
    # The porch: posts, a beam, lanterns hanging either side of the door, a step
    for x in (4, 8):
        posts(b, [(x, 1)], 1, 3, FRAME)
        b.set(x, 0, 1, "stone_bricks")
    for x in range(4, 9):
        b.set(x, 4, 1, FRAME, axis="x")
    for z in (2,):
        for x in (4, 8):
            b.set(x, 4, z, FRAME, axis="z")
    for x in range(5, 8):
        b.set(x, 0, 1, "spruce_planks")
        b.set(x, 0, 2, "spruce_planks")
    stairs(b, 6, 0, 0, STONE_BRICK, "south")
    lantern(b, 5, 3, 1, hanging=True)
    lantern(b, 7, 3, 1, hanging=True)
    b.door(6, 1, z0, "spruce_door", "south")
    b.set(4, 1, 2, "barrel", facing="up", open=False)
    b.set(8, 1, 2, "hay_block", axis="y")
    # The counter by the door: the two terminals set into it either side of the Storehouse, on the floor cable
    stairs(b, 4, 1, 5, SPRUCE, "south", top=True)
    t.terminal(b, 8, 1, 5, "south", pos="down", crafting=True)  # the crafting terminal at the counter's end
    t.terminal(b, 5, 1, 5, "south", pos="down")
    b.set(6, 1, 5, "aliveworkplace:storehouse", facing="north")
    b.set(7, 1, 5, "crafting_table")  # in both builds: the Carpenter's job site (B388)
    for x in (5, 6, 7, 8):
        t.cable(b, x, 0, 5)
    for z in range(6, 12):
        t.cable(b, 6, 0, z)
    # The island round the connector: rows of chests and barrels either side, the ends and a top row between
    t.connector(b, 6, 1, 8)
    rack(b, 4, 8, 7, (1, 2), "north")
    rack(b, 4, 8, 9, (1, 2), "south", flip=1)
    for x in (4, 8):
        for y in (1, 2):
            store(b, x, y, 8, "west" if x == 4 else "east", False)
    for x in (5, 7):
        t.trim(b, x, 1, 8)
        store(b, x, 2, 8, "north", True)
    store(b, 6, 2, 8, "north", True)
    # The filing cabinets on the back wall, each read through a cable connector under it; a bookshelf between
    for x in (5, 7):
        t.cabinet(b, x, 1, 11, "north")
        t.cable_connector(b, x, 0, 11, "up")
    b.set(6, 1, 11, "bookshelf")
    b.set(6, 2, 11, "potted_fern")
    for z in (5, 9):
        lantern(b, 3, 4, z, hanging=True)
        lantern(b, 9, 4, z, hanging=True)
    for x in range(2, 11):  # tie beams the lanterns hang from
        b.set(x, 5, 5, FRAME, axis="x")
        b.set(x, 5, 9, FRAME, axis="x")
    b.fill_air()
    return b


# --- the Warehouse ------------------------------------------------------------------------------------------------

def warehouse(toms=False):
    """19 x 16 x 16: the Warehouse. Two storeys under a long dark oak roof, stone below and birch above, the loading
    doors in the middle of the long front under a lean-to, a handcart in the loading bay. Inside, four racks of chests
    and barrels on the ground floor and two along the back of the loft, all one store: one inventory connector at the
    packing counter by the doors, Inventory Trim set in the floor under the racks and up a post to the loft's racks; a
    crafting terminal on its other side under the Storehouse, the crafting table (the Carpenter's job site, in both
    builds: B388) beside it. Without Tom's: a chest, a counter stair, stripped spruce inlays and post."""
    t = Tom(toms)
    b = Build(19, 16, 16)
    x0, z0, x1, z1 = 1, 4, 17, 14
    plinth(b, x0, z0, x1, z1, FOUNDATION_MIX, floor="spruce_planks")
    walls(b, x0, z0, x1, z1, 1, 3, STONE_MIX)
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        box(b, x, 1, z, x, 3, z, "stone_bricks")
    beam_ring(b, x0, z0, x1, z1, 4, FRAME)
    box(b, x0 + 1, 4, z0 + 1, x1 - 1, 4, z1 - 1, "spruce_planks")  # the loft floor
    walls(b, x0, z0, x1, z1, 5, 7, INFILL)
    bays = (1, 4, 7, 11, 14, 17)
    posts(b, [(x, z) for x in bays for z in (z0, z1)] + [(x, z) for x in (x0, x1) for z in (7, 11)], 5, 7, FRAME)
    beam_ring(b, x0, z0, x1, z1, 8, FRAME)
    for xa in (2, 5, 12, 15):
        window(b, xa, 2, z0, "north", width=2, sill=STONE_BRICK)
        window(b, xa, 6, z0, "north", width=2, shutters="spruce_trapdoor")
        window(b, xa, 6, z1, "south", width=2, shutters="spruce_trapdoor")
    for za in (8, 9):
        pane(b, x0, 6, za)
        pane(b, x1, 6, za)
    window(b, x0, 2, 9, "west", sill=STONE_BRICK)
    window(b, x1, 2, 9, "east", sill=STONE_BRICK)
    # The loading doors: an opening three wide, open trapdoor doors folded back either side, a loft door over them
    b.clear(8, 1, z0, 10, 3, z0)
    for x in range(8, 11):
        b.set(x, 0, z0, "stripped_spruce_log", axis="x")
    for x, f in ((7, "north"), (11, "north")):
        for y in (1, 2, 3):
            trapdoor(b, x, y, z0 - 1, "spruce_trapdoor", f, open_=True)

    def main_roof(r):
        gable_roof(r, 0, 18, 3, 15, 9, ROOF, axis="x", gable=INFILL, gable_at=(x0, x1), ridge=ROOF, eave_trim=SPRUCE)

    def hoist_gable(r):
        gable_roof(r, 6, 12, 2, 7, 9, ROOF, axis="z", gable=INFILL, gable_at=(z0,), ridge=ROOF, eave_trim=SPRUCE)

    roofs(b, main_roof, hoist_gable)
    log(b, 9, 9, z0, FRAME)  # the hoist's gable: a king post, a little window either side, a beam with a chain
    pane(b, 8, 9, z0)
    pane(b, 10, 9, z0)
    for x in (x0, x1):
        for y in range(9, 14):
            if b.get(x, y, 9) and b.get(x, y, 9)[0] == "minecraft:" + INFILL:
                log(b, x, y, 9, FRAME)
        pane(b, x, 10, 8)
        pane(b, x, 10, 10)
    # The lean-to over the loading bay, on two posts, a lantern under it
    for x in (6, 12):
        posts(b, [(x, 1)], 1, 2, FRAME)
        b.set(x, 0, 1, "stone_bricks")
    for x in range(6, 13):
        b.set(x, 3, 1, FRAME, axis="x")
        stairs(b, x, 3, 0, ROOF, "south")
        stairs(b, x, 4, 1, ROOF, "south")
        stairs(b, x, 5, 2, ROOF, "south")
        b.set(x, 5, 3, ROOF.slab, type="bottom", waterlogged=False)
    for x in (6, 12):
        for z in (2, 3):
            b.set(x, 3, z, FRAME, axis="z")
    for x in range(7, 12):
        for z in range(1, 4):
            b.set(x, 0, z, "coarse_dirt" if (x + z) % 3 == 0 else "gravel")
    lantern(b, 7, 2, 1, hanging=True)
    lantern(b, 11, 2, 1, hanging=True)
    # The handcart: a bed of slabs on four wheels, sacks of hay and a barrel on it, its shaft to the street
    for x in (8, 9, 10):
        b.set(x, 1, 2, "spruce_slab", type="top", waterlogged=False)
    for x in (8, 10):
        trapdoor(b, x, 1, 1, "dark_oak_trapdoor", "north", open_=True)
        trapdoor(b, x, 1, 3, "dark_oak_trapdoor", "south", open_=True)
    b.set(8, 2, 2, "hay_block", axis="x")
    b.set(9, 2, 2, "hay_block", axis="y")
    b.set(10, 2, 2, "pumpkin")
    # The packing counter inside the doors: the connector, the crafting table on one side, the crafting terminal
    # reading the connector on the other with the Storehouse on it
    t.connector(b, 9, 1, 6)
    b.set(9, 2, 6, "potted_azalea_bush")  # not a lantern: it falls off the vanilla build's chest
    b.set(10, 1, 6, "crafting_table")  # in both builds: the Carpenter's job site (B388)
    t.terminal(b, 8, 1, 6, "east", crafting=True, counter="north")
    b.set(8, 2, 6, "aliveworkplace:storehouse", facing="north")  # on the counter, in reach of both floors' racks
    # Inventory Trim in the floor: down from the connector, along under the racks, and up a post to the loft
    t.trim(b, 9, 0, 6, axis="z")
    for z in range(7, 12):
        t.trim(b, 9, 0, z, axis="z")
    for z in (8, 11):
        for x in range(3, 16):
            if x != 9:
                t.trim(b, x, 0, z, axis="x")
    for y in (1, 2, 3):
        t.trim(b, 9, y, 11, axis="y")
    t.trim(b, 9, 4, 11, axis="y")
    for x in range(3, 16):
        if x != 9:
            t.trim(b, x, 4, 13, axis="x")
    t.trim(b, 9, 4, 12, axis="z")
    t.trim(b, 9, 4, 13, axis="z")
    # The racks: four on the ground floor (aisles between, the post in the middle one), two along the loft's back
    for xa, xb in ((3, 7), (11, 15)):
        rack(b, xa, xb, 8, (1, 2), "north")
        rack(b, xa, xb, 11, (1, 2), "south", flip=1)
        rack(b, xa, xb, 13, (5, 6), "north")
    # The ladder up to the loft on the east wall, through a hatch
    for y in range(1, 5):
        b.set(16, y, 6, "ladder", facing="west", waterlogged=False)
    for x, z in ((5, 6), (13, 6), (5, 10), (13, 10)):
        lantern(b, x, 3, z, hanging=True)
    for x, z in ((5, 9), (13, 9)):
        lantern(b, x, 7, z, hanging=True)
    for x in range(2, 17):  # tie beams in the loft the lanterns hang from
        b.set(x, 8, 9, FRAME, axis="x")
    b.set(3, 5, 6, "hay_block", axis="y")
    b.set(4, 5, 6, "hay_block", axis="x")
    b.fill_air()
    return b


BUILDS = (("storage_hall", storage_hall), ("terminal_nook", terminal_nook), ("warehouse", warehouse))


def save_all(main, modded):
    """Each build into `main` and its Tom's copy into `modded` (the modded library's folder)."""
    for name, draw in BUILDS:
        draw().save(main, name)
        draw(toms=True).save(modded, name, checked=False)
