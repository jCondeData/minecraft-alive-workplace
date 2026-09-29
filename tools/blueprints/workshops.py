"""
Workshops for the crafting jobs.

Tinker's Workshop: I a brick workshop with its gable to the street (a hoist over the door), a forge lean-to under a
catslide roof with a big brick chimney, the Tinker's Bench inside; II the hall run back to twice the length, with a
storage loft, a cart track in through the back and a lightning rod on the ridge.
"""
from kit import *

TINKER_BRICK = Mix((8, "bricks"), (1, "stone_bricks"), seed=11)
TINKER_FLOOR = Mix((5, "polished_andesite"), (2, "stone_bricks"), (1, "andesite"), seed=12)
TINKER_ROOF = DEEPSLATE_TILE
TINKER_GABLE = TINKER_BRICK
TINKER_FRAME = "spruce_log"


def tinkers_hall(b, depth):
    """The hall both tiers share: walls x 1-7, z 1-{depth}, four high; a stone brick course along the foot, brick above,
    spruce log corners. The front (z = 1) has the door under a hood with two wide windows either side."""
    z1 = depth
    plinth(b, 1, 1, 7, z1, FOUNDATION_MIX, floor=TINKER_FLOOR)
    walls(b, 1, 1, 7, z1, 1, 1, "stone_bricks")
    walls(b, 1, 1, 7, z1, 2, 4, TINKER_BRICK)
    posts(b, [(1, 1), (7, 1), (1, z1), (7, z1)], 1, 4, "spruce_log")
    # The front: a door on a step under a slab hood, two big windows, a hoist over it all
    door(b, 4, 1, 1, "spruce_door", "south")
    stairs(b, 4, 0, 0, STONE_BRICK, "south")
    for x in (3, 4, 5):
        slab(b, x, 3, 0, SPRUCE, top=True)
    for x in (2, 6):
        window(b, x, 2, 1, "north", height=2, sill=STONE_BRICK)
    # West side: shuttered windows
    for z in range(3, z1 - 1, 3):
        window(b, 1, 2, z, "west", height=2, shutters="spruce_trapdoor", sill=STONE_BRICK)


def tinkers_roof(b, depth):
    """The main roof (ridge front to back, gables to the street and the back) and its catslide over the forge."""
    ridge = gable_roof(b, 0, 8, 0, depth + 1, 5, TINKER_ROOF, axis="z", gable=TINKER_GABLE, gable_at=(1, depth), eave_trim=SPRUCE)
    beam_ring(b, 1, 1, 7, depth, 5, TINKER_FRAME)
    for z in (1, depth):
        # the gables: a peaked window and a log frame up the middle
        for x in (3, 4, 5):
            pane(b, x, 6, z)
        pane(b, 4, 7, z)
    # Open trusses across the hall: a tie beam, a king post up to the ridge, a lantern under each
    for z in range(3, min(depth, 8), 3):
        for x in range(2, 7):
            log(b, x, 5, z, TINKER_FRAME, axis="x")
        for y in (6, 7, 8):
            log(b, 4, y, z, TINKER_FRAME)
        lantern(b, 4, 4, z, hanging=True)
    return ridge


def tinkers_forge(b):
    """The forge lean-to on the east wall (x 8-10, z 3-6): an arch through from the hall, two furnaces set in the outer
    wall with the chimney stack behind them, the main roof's slope carried down over it."""
    plinth(b, 8, 3, 10, 6, FOUNDATION_MIX)
    box(b, 8, 0, 4, 9, 0, 5, "bricks")
    for z in (3, 6):
        for y in range(1, 5):
            b.set(8, y, z, resolve(TINKER_BRICK, 8, y, z))
        for y in range(1, 4):
            b.set(9, y, z, resolve(TINKER_BRICK, 9, y, z))
        for y in range(1, 3):
            b.set(10, y, z, resolve(TINKER_BRICK, 10, y, z))
    for z in (4, 5):
        b.set(10, 1, z, "furnace", facing="west", lit=False)
        b.set(10, 2, z, "bricks")
        b.set(7, 1, z, "air")
        b.set(7, 2, z, "air")
    b.set(7, 3, 4, "brick_stairs", facing="south", half="top", shape="straight", waterlogged=False)
    b.set(7, 3, 5, "brick_stairs", facing="north", half="top", shape="straight", waterlogged=False)
    # The catslide: the main roof's east slope runs on down over the forge
    for x, y in ((9, 4), (10, 3), (11, 2)):
        for z in range(2, 8):
            stairs(b, x, y, z, TINKER_ROOF, "west")
    # The stack: two bricks wide outside the forge wall, stepping in to one near the top, smoking
    for z, top in ((4, 9), (5, 8)):
        for y in range(0, top + 1):
            b.set(11, y, z, "bricks")
    b.set(11, 10, 4, "campfire", facing="north", lit=True, signal_fire=False, waterlogged=False)


def tinkers_hoist(b):
    """A log beam out of the front gable's peak with a chain and a barrel on it, as if goods were going up to the loft."""
    log(b, 4, 8, 0, TINKER_FRAME, axis="z")
    log(b, 4, 8, 1, TINKER_FRAME, axis="z")
    b.set(4, 7, 0, "chain", axis="y", waterlogged=False)
    b.set(4, 6, 0, "chain", axis="y", waterlogged=False)
    b.set(4, 5, 0, "barrel", facing="up", open=False)


def tinkers_bench_corner(b):
    """The Tinker's Bench with its chests, the anvil, and a redstone lamp kept lit on a block of redstone for show."""
    b.set(2, 1, 4, "aliveworkplace:tinkers_bench", facing="east")
    b.set(2, 1, 5, "chest", facing="east", type="left", waterlogged=False)
    b.set(2, 1, 6, "chest", facing="east", type="right", waterlogged=False)
    b.set(2, 1, 3, "barrel", facing="east", open=False)
    b.set(2, 2, 3, "lantern", hanging=False, waterlogged=False)
    b.set(3, 1, 2, "crafting_table")
    b.set(6, 1, 2, "redstone_block")
    b.set(6, 2, 2, "redstone_lamp", lit=True)


def tinkers_workshop():
    """12 x 11 x 10: a brick workshop gable-on to the street, a hoist with a barrel on its chain over the door, a forge
    lean-to under a catslide roof with a big smoking brick chimney; inside the Tinker's Bench and its chests, an anvil and
    a redstone lamp kept lit for show."""
    b = Build(12, 11, 10)
    tinkers_hall(b, 8)
    tinkers_roof(b, 8)
    tinkers_forge(b)
    tinkers_hoist(b)
    tinkers_bench_corner(b)
    b.set(4, 1, 7, "anvil", facing="east")
    b.set(5, 1, 7, "barrel", facing="up", open=False)
    b.set(6, 1, 7, "barrel", facing="up", open=False)
    b.set(6, 2, 7, "barrel", facing="up", open=False)
    window(b, 4, 2, 8, "south", height=2, sill=STONE_BRICK)
    window(b, 7, 2, 2, "east", height=2, sill=STONE_BRICK)
    window(b, 7, 2, 7, "east", height=2, sill=STONE_BRICK)
    # Crates by the door
    b.set(0, 0, 3, "barrel", facing="up", open=False)
    b.set(0, 0, 4, "barrel", facing="north", open=False)
    b.set(0, 1, 3, "barrel", facing="up", open=False)
    b.fill_air()
    return b


def tinkers_workshop_2():
    """12 x 11 x 14: the hall run back to twice the length — a storage loft over the back half (ladder up the east wall),
    a cart track in through an arch at the back, more windows, and a lightning rod on the ridge."""
    b = Build(12, 11, 14)
    depth = 12
    tinkers_hall(b, depth)
    # Posts halfway along the long walls, and the loft floor on a beam
    posts(b, [(1, 7), (7, 7)], 1, 4, "spruce_log")
    tinkers_roof(b, depth)
    tinkers_forge(b)
    tinkers_hoist(b)
    tinkers_bench_corner(b)
    b.set(4, 1, 6, "anvil", facing="east")
    for x in range(2, 7):
        log(b, x, 4, 8, TINKER_FRAME, axis="x")
        for z in range(9, depth):
            b.set(x, 5, z, "spruce_planks")
        fence(b, x, 6, 8, "spruce_fence")
    b.set(6, 5, 11, "air")
    for y in range(1, 6):
        b.set(6, y, 11, "ladder", facing="west", waterlogged=False)
    # The loft: barrels and chests of stock
    b.set(2, 6, 11, "chest", facing="south", type="single", waterlogged=False)
    for x in (3, 4):
        b.set(x, 6, 11, "barrel", facing="north", open=False)
    b.set(2, 6, 9, "barrel", facing="up", open=False)
    lantern(b, 5, 6, 11)
    # Under the loft: the cart track in from the back arch to a stop, racks either side
    for z in range(9, depth + 1):
        b.set(4, 1, z, "rail", shape="north_south", waterlogged=False)
    b.set(4, 1, 8, "powered_rail", shape="north_south", powered=False, waterlogged=False)
    b.set(4, 1, depth, "rail", shape="north_south", waterlogged=False)
    b.set(4, 2, depth, "air")
    b.set(4, 3, depth, "brick_stairs", facing="north", half="top", shape="straight", waterlogged=False)
    b.set(4, 0, depth + 1, "stone_bricks")
    for z in (9, 10):
        b.set(2, 1, z, "barrel", facing="east", open=False)
    b.set(6, 1, 9, "barrel", facing="west", open=False)
    b.set(2, 2, 9, "barrel", facing="up", open=False)
    b.set(2, 1, 11, "iron_bars", north=False, south=False, east=False, west=False, waterlogged=False)
    window(b, 2, 2, depth, "south", height=2, sill=STONE_BRICK)
    window(b, 6, 2, depth, "south", height=2, sill=STONE_BRICK)
    for z in (2, 8):
        window(b, 7, 2, z, "east", height=2, sill=STONE_BRICK)
    b.set(5, 1, 7, "barrel", facing="up", open=False)
    # The lightning rod at the front of the ridge, standing on the gable's peak
    b.set(4, 9, 1, "lightning_rod", facing="up", powered=False, waterlogged=False)
    # Crates by the door
    b.set(0, 0, 3, "barrel", facing="up", open=False)
    b.set(0, 0, 4, "barrel", facing="north", open=False)
    b.set(0, 1, 3, "barrel", facing="up", open=False)
    b.fill_air()
    return b
