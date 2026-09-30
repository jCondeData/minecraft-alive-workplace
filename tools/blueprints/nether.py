"""
The Nether Gate: where a village's netherworkers set out from.

Nether Gate I: an obsidian portal frame set in a polished blackstone arch with a gilded keystone and soul lanterns on
the pillars, on a paved blackstone court with soul-fire braziers at the front; a cartography table (the netherworker's:
hand the villager there netherrack) and its chests at the side. The builder lights the frame when it's done (with a
flint and steel or a fire charge from the chests).
II: a vaulted gatehouse roof over the portal, a blackstone storehouse on the east side and a nether wart garden behind.
"""
from kit import *

BLACKSTONE_BRICK = Family("polished_blackstone_bricks", "polished_blackstone_brick_stairs", "polished_blackstone_brick_slab",
                          wall="polished_blackstone_brick_wall")
BLACKSTONE = Family("polished_blackstone", "polished_blackstone_stairs", "polished_blackstone_slab", wall="polished_blackstone_wall")
NETHER_BRICK = Family("nether_bricks", "nether_brick_stairs", "nether_brick_slab", wall="nether_brick_wall", fence="nether_brick_fence")
COURT = Mix((6, "polished_blackstone_bricks"), (2, "polished_blackstone"), (1, "cracked_polished_blackstone_bricks"), seed=21)
GATE_WALL = Mix((7, "polished_blackstone_bricks"), (1, "cracked_polished_blackstone_bricks"), seed=22)


def gate_court(b, x1, z1):
    """The paved court (x 0-{x1}, z 1-{z1}) with a step along the front."""
    box(b, 0, 0, 1, x1, 0, z1, COURT)
    for x in range(0, x1 + 1):
        stairs(b, x, 0, 0, BLACKSTONE_BRICK, "south")


def gate_arch(b):
    """The portal frame (inside x 4-5, y 2-4, in the plane z = 3) in its arch: pillars, a pointed pediment with a gilded
    keystone, soul lanterns on the pillars, stair buttresses at their feet."""
    for x in (4, 5):
        b.set(x, 1, 3, "obsidian")
        b.set(x, 5, 3, "obsidian")
    for y in (2, 3, 4):
        b.set(3, y, 3, "obsidian")
        b.set(6, y, 3, "obsidian")
    for x in (3, 6):
        b.set(x, 1, 3, "chiseled_polished_blackstone")
        b.set(x, 5, 3, "chiseled_polished_blackstone")
    for x in (2, 7):
        for y in range(1, 7):
            b.set(x, y, 3, resolve(GATE_WALL, x, y, 3))
        b.set(x, 1, 3, "chiseled_polished_blackstone")
        for z, face in ((2, "south"), (4, "north")):
            stairs(b, x, 1, z, BLACKSTONE_BRICK, face)
        lantern(b, x, 7, 3, soul=True)
    for x in range(3, 7):
        b.set(x, 6, 3, resolve(GATE_WALL, x, 6, 3))
    stairs(b, 3, 7, 3, BLACKSTONE_BRICK, "east")
    stairs(b, 6, 7, 3, BLACKSTONE_BRICK, "west")
    b.set(4, 7, 3, "gilded_blackstone")
    b.set(5, 7, 3, "gilded_blackstone")
    slab(b, 4, 8, 3, BLACKSTONE_BRICK)
    slab(b, 5, 8, 3, BLACKSTONE_BRICK)


def gate_braziers(b):
    """Soul-fire braziers on wall posts either side of the way in."""
    for x in (1, 8):
        wall_block(b, x, 1, 1, "polished_blackstone_wall")
        b.set(x, 2, 1, "soul_campfire", facing="south", lit=True, signal_fire=False, waterlogged=False)


def gate_brazier_corner(b):
    """The netherworker's cartography table with a chest and a barrel beside it, on the east side of the court."""
    b.set(9, 1, 4, "cartography_table")
    b.set(9, 1, 5, "chest", facing="west", type="single", waterlogged=False)
    b.set(9, 1, 6, "barrel", facing="up", open=False)


def nether_gate():
    """11 x 9 x 7: an obsidian portal frame in a polished blackstone arch (a gilded keystone, soul lanterns on the
    pillars) on a paved court with soul-fire braziers at the front, and the netherworker's cartography table with its
    chest to one side."""
    b = Build(11, 9, 7)
    gate_court(b, 10, 6)
    gate_arch(b)
    gate_braziers(b)
    gate_brazier_corner(b)
    for z in range(2, 7):
        wall_block(b, 0, 1, z, "polished_blackstone_brick_wall")
        wall_block(b, 10, 1, z, "polished_blackstone_brick_wall")
    lantern(b, 0, 2, 6, soul=True)
    lantern(b, 10, 2, 6, soul=True)
    b.fill_air()
    return b


def nether_gate_2():
    """15 x 11 x 11: a vaulted gatehouse roof over the portal on four pillars, a blackstone storehouse on the east side
    (chests, barrels, soul lanterns) and a nether wart garden on soul sand behind the gate."""
    b = Build(15, 11, 11)
    gate_court(b, 14, 10)
    gate_arch(b)
    gate_braziers(b)
    gate_brazier_corner(b)
    for z in range(2, 7):
        wall_block(b, 0, 1, z, "polished_blackstone_brick_wall")
    lantern(b, 0, 2, 6, soul=True)
    # The gatehouse: four pillars round the arch and a stepped roof over them
    for x in (1, 8):
        for z in (1, 5):
            for y in range(1, 7):
                b.set(x, y, z, resolve(GATE_WALL, x, y, z))
            b.set(x, 1, z, "chiseled_polished_blackstone")
    # the braziers move out in front of the pillars
    for x in (0, 9):
        wall_block(b, x, 1, 1, "polished_blackstone_wall")
        b.set(x, 2, 1, "soul_campfire", facing="south", lit=True, signal_fire=False, waterlogged=False)
    for x in range(1, 9):
        for z in range(1, 6):
            if z != 3 or x in (1, 8):
                slab(b, x, 7, z, BLACKSTONE_BRICK, top=True)
    for x in range(0, 10):
        stairs(b, x, 7, 0, BLACKSTONE_BRICK, "south")
        stairs(b, x, 7, 6, BLACKSTONE_BRICK, "north")
    for z in range(1, 6):
        stairs(b, 0, 7, z, BLACKSTONE_BRICK, "east")
        stairs(b, 9, 7, z, BLACKSTONE_BRICK, "west")
    for x in range(1, 9):
        for z in (1, 5):
            stairs(b, x, 8, z, BLACKSTONE_BRICK, "south" if z == 1 else "north")
    for x in range(1, 9):
        for z in (2, 3, 4):
            if z != 3 or x not in (4, 5):
                slab(b, x, 8, z, BLACKSTONE_BRICK)
    for x in (4, 5):
        b.set(x, 8, 3, "polished_blackstone_bricks")  # (a slab in Nether Gate I: the spire stands on it here)
    # the spire over the keystone: a gilded block, a chain and a soul lantern hanging in the gate
    b.set(4, 9, 3, "gilded_blackstone")
    b.set(5, 9, 3, "gilded_blackstone")
    wall_block(b, 4, 10, 3, "polished_blackstone_brick_wall")
    wall_block(b, 5, 10, 3, "polished_blackstone_brick_wall")
    for x in (2, 7):
        b.set(x, 6, 2, "chain", axis="y", waterlogged=False)
        lantern(b, x, 5, 2, hanging=True, soul=True)
    # The storehouse on the east side (walls x 11-14, z 1-6)
    walls(b, 11, 1, 14, 6, 1, 4, GATE_WALL)
    posts(b, [(11, 1), (14, 1), (11, 6), (14, 6)], 1, 4, "polished_basalt")
    door(b, 12, 1, 1, "crimson_door", "south")
    window(b, 14, 2, 3, "east", height=2, glass="iron_bars")
    window(b, 14, 2, 5, "east", height=2, glass="iron_bars")
    for x in range(10, 16):
        if x <= 14:
            for z in range(0, 8):
                slab(b, x, 5, z, BLACKSTONE_BRICK, top=False)
    for z in range(2, 6):
        b.set(13, 1, z, "chest" if z in (2, 3) else "barrel", **({"facing": "west", "type": "right" if z == 2 else "left", "waterlogged": False}
                                                                if z in (2, 3) else {"facing": "up", "open": False}))
    b.set(12, 1, 5, "barrel", facing="up", open=False)
    lantern(b, 12, 4, 4, hanging=True, soul=True)
    # The nether wart garden behind the gate (z 8-9), edged with nether brick fence
    for x in range(1, 9):
        for z in (8, 9):
            b.set(x, 0, z, "soul_sand")
            b.set(x, 1, z, "nether_wart", age=3)
    for x in range(0, 10):
        fence(b, x, 1, 10, "nether_brick_fence")
    for z in (7, 8, 9):
        fence(b, 0, 1, z, "nether_brick_fence")
        fence(b, 9, 1, z, "nether_brick_fence")
    for x in (0, 9):
        lantern(b, x, 2, 10, soul=True)
    b.fill_air()
    return b
