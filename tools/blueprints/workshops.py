"""
Workshops for the crafting jobs.

Tinker's Workshop: I a brick workshop with its gable to the street (a hoist over the door), a forge lean-to under a
catslide roof with a big brick chimney, a smithing table inside (the tinkerer's: hand the villager there redstone); II
the hall run back to twice the length, with a storage loft, a cart track in through the back and a lightning rod on the
ridge.

The workplaces of ROADMAP 27.13, each the building the Steward builds for its workers (steward_rules/workplace_*.json):
Smithy (Armorer, Miner; with a smithing table and a grindstone too), Mason's Yard (Mason), Fletcher's Lodge (Fletcher,
Lumberjack) and Map Room (Cartographer). Each keeps only the job blocks of its trades: no barrels, lecterns or other
blocks that would give a passing villager another job.
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


def tinkers_corner(b):
    """The tinkerer's smithing table with its chests, the anvil, and a redstone lamp kept lit on a block of redstone for
    show."""
    b.set(2, 1, 4, "smithing_table")
    b.set(2, 1, 5, "chest", facing="east", type="left", waterlogged=False)
    b.set(2, 1, 6, "chest", facing="east", type="right", waterlogged=False)
    b.set(2, 1, 3, "barrel", facing="east", open=False)
    b.set(2, 2, 3, "lantern", hanging=False, waterlogged=False)
    b.set(3, 1, 2, "crafting_table")
    b.set(6, 1, 2, "redstone_block")
    b.set(6, 2, 2, "redstone_lamp", lit=True)


def tinkers_workshop():
    """12 x 11 x 10: a brick workshop gable-on to the street, a hoist with a barrel on its chain over the door, a forge
    lean-to under a catslide roof with a big smoking brick chimney; inside the tinkerer's smithing table and its chests,
    an anvil and a redstone lamp kept lit for show."""
    b = Build(12, 11, 10)
    tinkers_hall(b, 8)
    tinkers_roof(b, 8)
    tinkers_forge(b)
    tinkers_hoist(b)
    tinkers_corner(b)
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
    tinkers_corner(b)
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


# --- Smithy (ROADMAP 27.13) ------------------------------------------------------------------------------------------
# Smithy: I a stone forge under a dark oak roof, its gable to the street over an open front: the anvil and a quench
# trough inside, the blast furnace (Armorer; Miner with a pickaxe) glowing in a brick chimney breast in the west wall
# with the stack up outside to a smoking campfire, the smithing table (Toolsmith) at the back, and a lean-to on the east
# side over the grindstone (Weaponsmith); II a coal and ore store in a wing behind, with a second blast furnace and its
# own flue.
SMITHY_STONE = Mix((6, "cobblestone"), (3, "stone_bricks"), (2, "andesite"), (1, "cracked_stone_bricks"), seed=41)
SMITHY_FLOOR = Mix((5, "stone_bricks"), (3, "polished_andesite"), (2, "cobblestone"), seed=42)
SMITHY_FRAME = "spruce_log"
SMITHY_BOARDS = "spruce_planks"
SMITHY_ROOF = DARK_OAK


def smithy_hall(b):
    """The smithy (walls x 1-9, z 2-8, four high): stone walls with spruce posts at the corners and halfway along, the
    front open to the street under a log beam on brackets, a cobbled apron and a step in front."""
    plinth(b, 1, 2, 9, 8, FOUNDATION_MIX, floor=SMITHY_FLOOR)
    for y in range(1, 5):
        for x in range(1, 10):
            b.set(x, y, 8, resolve(SMITHY_STONE, x, y, 8))
        for z in range(3, 8):
            b.set(1, y, z, resolve(SMITHY_STONE, 1, y, z))
            b.set(9, y, z, resolve(SMITHY_STONE, 9, y, z))
    posts(b, [(1, 2), (9, 2), (1, 8), (9, 8), (5, 8), (9, 5)], 1, 4, SMITHY_FRAME)
    beam_ring(b, 1, 2, 9, 8, 4, SMITHY_FRAME)
    window(b, 1, 2, 7, "west", height=2, sill=STONE_BRICK)
    window(b, 9, 3, 7, "east")
    for x in (3, 7):
        window(b, x, 2, 8, "south", height=2, shutters="spruce_trapdoor", sill=STONE_BRICK)
    # Brackets under the front beam by the corner posts, a lantern hung in the middle
    stairs(b, 2, 3, 2, SPRUCE, "west", top=True)
    stairs(b, 8, 3, 2, SPRUCE, "east", top=True)
    lantern(b, 5, 3, 2, hanging=True)
    # A cobbled apron in front, a step up in the middle
    for x in range(1, 12):
        b.set(x, 0, 1, resolve(STONE_MIX, x, 0, 1))
    for x in (4, 5, 6):
        stairs(b, x, 0, 0, STONE_BRICK, "south")


def smithy_roof(b):
    """A dark oak roof, its gables front and back (spruce boards, a king post and a little window in the front one), tie
    beams across the hall with a lantern under each."""
    gable_roof(b, 0, 10, 1, 9, 5, SMITHY_ROOF, axis="z", gable=SMITHY_BOARDS, gable_at=(2, 8), eave_trim=SPRUCE)
    smithy_gables(b)


def smithy_gables(b):
    """Both gables framed: a king post up the middle, a two-high window either side of it, studs further out."""
    for z in (2, 8):
        for y in range(5, 10):
            log(b, 5, y, z, SMITHY_FRAME)
        for x in (4, 6):
            pane(b, x, 6, z)
            pane(b, x, 7, z)
        for x in (3, 7):
            b.set(x, 5, z, "stripped_spruce_log", axis="y")
            b.set(x, 6, z, "stripped_spruce_log", axis="y")
    for z in (4, 6):
        for x in range(2, 9):
            log(b, x, 4, z, SMITHY_FRAME, axis="x")
        lantern(b, 5, 3, z, hanging=True)


def smithy_forge(b):
    """The forge in the west wall: a brick chimney breast round the blast furnace (the armorer's; a miner's, picked with a
    pickaxe), a hood of brick stairs over it, and the stack up outside the wall, through the eaves, to a campfire
    smoking on top."""
    for z in (4, 5, 6):
        for y in range(1, 5):
            b.set(1, y, z, "bricks")
    b.set(2, 1, 4, "bricks")
    b.set(2, 1, 6, "bricks")
    b.set(2, 1, 5, "blast_furnace", facing="east", lit=False)
    for z in (4, 6):
        wall_block(b, 2, 2, z, "brick_wall")
    for z in (4, 5, 6):
        stairs(b, 2, 3, z, BRICK, "west", top=True)
        b.set(2, 4, z, "bricks")
    # The stack outside: three wide up the wall, stepping in to one through the eaves
    for z in (4, 5, 6):
        for y in range(0, 6):
            b.set(0, y, z, "bricks")
    stairs(b, 0, 6, 4, BRICK, "south")
    stairs(b, 0, 6, 6, BRICK, "north")
    for y in range(6, 11):
        b.set(0, y, 5, "bricks")
    b.set(0, 11, 5, "campfire", facing="north", lit=True, signal_fire=False, waterlogged=False)


def smithy_lean_to(b):
    """The lean-to on the east wall (x 10-11, z 3-7): a post at the open corner, a stone back wall, the main roof's
    slope carried down over it; under it the weaponsmith's grindstone and a stack of firewood."""
    plinth(b, 10, 3, 11, 7, SMITHY_STONE)
    box(b, 10, 0, 4, 10, 0, 6, SMITHY_FLOOR)
    posts(b, [(11, 3), (11, 7)], 1, 3, SMITHY_FRAME)
    for y in (1, 2, 3):
        b.set(10, y, 7, resolve(SMITHY_STONE, 10, y, 7))
    for z in range(4, 7):
        b.set(11, 1, z, resolve(SMITHY_STONE, 11, 1, z))
        log(b, 11, 2, z, "oak_log", axis="x")
    log(b, 11, 3, 5, SMITHY_FRAME, axis="z")
    log(b, 11, 3, 4, SMITHY_FRAME, axis="z")
    log(b, 11, 3, 6, SMITHY_FRAME, axis="z")
    for z in range(2, 9):
        if 3 <= z <= 7:
            log(b, 10, 4, z, SMITHY_FRAME, axis="z")
        stairs(b, 11, 4, z, SMITHY_ROOF, "west")
        stairs(b, 12, 3, z, SMITHY_ROOF, "west")
    b.set(10, 1, 5, "grindstone", face="floor", facing="north")
    lantern(b, 11, 0, 2)


def smithy_floor(b):
    """The working floor: the anvil, the toolsmith's smithing table, a stone quench trough of water against the east wall,
    a rack of iron bars and a chest of stock."""
    b.set(4, 1, 7, "smithing_table")
    b.set(4, 1, 4, "anvil", facing="north")
    for z in (4, 5):
        b.set(7, 1, z, "stone_bricks")
        b.set(8, 1, z, "water", level=0)
    b.set(8, 1, 3, "stone_bricks")
    b.set(8, 1, 6, "stone_bricks")
    b.set(7, 1, 3, "stone_brick_slab", type="bottom", waterlogged=False)
    b.set(7, 1, 6, "stone_brick_slab", type="bottom", waterlogged=False)
    b.set(8, 1, 7, "chest", facing="west", type="single", waterlogged=False)
    for x in (2, 3):
        b.set(x, 1, 7, "iron_bars", north=False, south=False, east=False, west=False, waterlogged=False)
    b.set(3, 2, 7, "chain", axis="y", waterlogged=False)


def smithy():
    """13 x 12 x 10: a stone forge under a dark oak roof, its gable to the street over an open front: the anvil and a
    quench trough inside, a blast furnace in a brick chimney breast (Armorer, or Miner with a pickaxe), its stack up the
    west wall with a campfire smoking on top, a smithing table (Toolsmith) at the back, and a lean-to on the east side
    over a grindstone (Weaponsmith)."""
    b = Build(13, 12, 10)
    smithy_hall(b)
    smithy_roof(b)
    smithy_forge(b)
    smithy_lean_to(b)
    smithy_floor(b)
    b.fill_air()
    return b


def smithy_store(b):
    """The coal and ore store behind (walls x 3-7, z 8-12, four high, through a door in the back wall): bins of coal,
    raw iron and copper behind boards, a chest, and the second blast furnace with a brick flue up the back gable."""
    plinth(b, 3, 8, 7, 12, FOUNDATION_MIX, floor=SMITHY_FLOOR)
    for y in range(1, 5):
        for z in range(9, 13):
            for x in (3, 7):
                b.set(x, y, z, resolve(SMITHY_STONE, x, y, z))
        for x in range(4, 7):
            b.set(x, y, 12, resolve(SMITHY_STONE, x, y, 12))
    posts(b, [(3, 12), (7, 12)], 1, 4, SMITHY_FRAME)
    for z in range(9, 13):
        log(b, 3, 4, z, SMITHY_FRAME, axis="z")
        log(b, 7, 4, z, SMITHY_FRAME, axis="z")
    # Through the back wall: the window there becomes a door
    b.set(5, 0, 8, "stone_bricks")
    for y in (1, 2):
        b.set(5, y, 8, "air")
    b.set(5, 3, 8, SMITHY_FRAME, axis="x")
    b.set(5, 4, 8, SMITHY_FRAME, axis="x")
    # The bins: coal, raw iron, raw copper, behind spruce boards
    for x, ore in ((4, "coal_block"), (6, "raw_iron_block")):
        b.set(x, 1, 11, ore)
        b.set(x, 1, 10, ore if x == 4 else "raw_copper_block")
        trapdoor(b, x, 2, 11, "spruce_trapdoor", "south", open_=True)
    b.set(4, 1, 9, "chest", facing="east", type="single", waterlogged=False)
    window(b, 3, 2, 10, "west", height=2, shutters="spruce_trapdoor", sill=STONE_BRICK)
    window(b, 7, 2, 10, "east", height=2, shutters="spruce_trapdoor", sill=STONE_BRICK)
    # The second blast furnace on the back wall, its flue up the gable
    b.set(5, 1, 11, "blast_furnace", facing="north", lit=False)
    for y in range(1, 9):
        b.set(5, y, 12, "bricks")
    b.set(5, 2, 11, "brick_wall", north="none", south="none", east="none", west="none", up=True, waterlogged=False)
    stairs(b, 5, 3, 11, BRICK, "south", top=True)
    slab(b, 5, 9, 12, BRICK)
    lantern(b, 4, 2, 9)


def smithy_2():
    """13 x 12 x 13: the smithy with a coal and ore store in a stone wing behind, under a lower roof of its own: bins of
    coal and raw ore and a second blast furnace (a second armorer, or a miner) with its own brick flue."""
    b = smithy().grow(13, 12, 13)
    smithy_store(b)
    roofs(b,
          lambda t: gable_roof(t, 0, 10, 1, 9, 5, SMITHY_ROOF, axis="z", gable=SMITHY_BOARDS, gable_at=(2, 8), eave_trim=SPRUCE),
          lambda t: gable_roof(t, 2, 8, 9, 12, 5, SMITHY_ROOF, axis="z", gable=SMITHY_BOARDS, gable_at=(12, 12), eave_trim=SPRUCE))
    smithy_gables(b)
    for y in range(1, 10):
        b.set(5, y, 12, "bricks")
    slab(b, 5, 10, 12, BRICK)
    smithy_forge(b)
    b.fill_air()
    return b


# --- Mason's Yard (ROADMAP 27.13) -----------------------------------------------------------------------------------
# Mason's Yard: I a yard paved with cut stone behind a fence on stone pillars, a way in between lit piers, stacks
# of dressed stone and a mason's bench in the yard, a lean-to along the back wall over the stonecutter (Mason);
# II a second stonecutter under the lean-to, and a timber hoist over the stacks with a block of stone on its chain.
YARD_PAVING = Mix((5, "stone_bricks"), (3, "smooth_stone"), (2, "polished_andesite"), (1, "cracked_stone_bricks"), seed=51)
YARD_WALL = Mix((5, "cobblestone"), (3, "stone"), (2, "andesite"), seed=52)
YARD_FRAME = "dark_oak_log"
YARD_ROOF = SPRUCE


def masons_yard_ground(b):
    """The yard (x 0-10, z 1-9) paved with cut stone, a step up to the gate."""
    for x in range(0, 11):
        for z in range(1, 10):
            b.set(x, 0, z, YARD_PAVING.at(x, 0, z))
    stairs(b, 5, 0, 0, STONE_BRICK, "south")


def masons_yard_fence(b):
    """A spruce fence round the front of the yard between stone brick piers; the way in, in the middle, between two piers
    with a lantern on each (no gate: villagers can't open one, and the mason must reach his stonecutter)."""
    for x in range(0, 11):
        if x != 5:
            fence(b, x, 1, 1, "spruce_fence")
    for z in range(2, 6):
        fence(b, 0, 1, z, "spruce_fence")
        fence(b, 10, 1, z, "spruce_fence")
    for x, z in ((0, 1), (10, 1), (4, 1), (6, 1), (0, 4), (10, 4)):
        b.set(x, 1, z, "stone_bricks")
        wall_block(b, x, 2, z, "stone_brick_wall")
    for x in (4, 6):
        lantern(b, x, 3, 1)


def masons_yard_lean_to(b):
    """The lean-to along the back (x 0-10, z 6-9): a stone back wall split by a dark oak beam halfway up, stone brick
    piers buttressed outside it, dark oak posts in front under a beam, a spruce roof rising to the back wall; the
    mason's stonecutter and a chest under it."""
    for x in range(0, 11):
        for y in range(1, 8):
            b.set(x, y, 9, resolve(YARD_WALL, x, y, 9))
        b.set(x, 1, 9, "stone_bricks")
        log(b, x, 4, 9, YARD_FRAME, axis="x")
    for z, top in ((7, 5), (8, 6)):
        for x in (0, 10):
            for y in range(1, top + 1):
                b.set(x, y, z, resolve(YARD_WALL, x, y, z))
            b.set(x, 1, z, "stone_bricks")
            log(b, x, 4, z, YARD_FRAME, axis="z")
    # Piers: stone brick columns through the back wall, buttressed outside with a stair cap
    for x in (0, 5, 10):
        for y in range(1, 8):
            b.set(x, y, 9, "stone_bricks")
        for y in (0, 1, 2):
            b.set(x, y, 10, "stone_bricks")
        stairs(b, x, 3, 10, STONE_BRICK, "north")
    posts(b, [(0, 6), (5, 6), (10, 6)], 1, 3, YARD_FRAME)
    for x in range(0, 11):
        log(b, x, 4, 6, YARD_FRAME, axis="x")
        for z, y in ((5, 4), (6, 5), (7, 6), (8, 7), (9, 8)):
            stairs(b, x, y, z, YARD_ROOF, "south")
        slab(b, x, 8, 10, YARD_ROOF)
        if x not in (0, 5, 10):
            stairs(b, x, 7, 10, SPRUCE, "north", top=True)
    for x in (1, 4, 6, 9):
        stairs(b, x, 3, 6, SPRUCE, "east" if x in (1, 6) else "west", top=True)
    # Little windows high in the back wall either side of the middle pier
    for x in (2, 8):
        window(b, x, 5, 9, "south", height=2)
    b.set(3, 1, 8, "stonecutter", facing="north")
    b.set(1, 1, 8, "chest", facing="east", type="single", waterlogged=False)
    lantern(b, 2, 3, 6, hanging=True)
    lantern(b, 8, 3, 6, hanging=True)


def masons_yard_stock(b):
    """What lies in the yard: stacks of dressed stone on pallets, a mason's bench (a smooth stone slab on stone brick
    legs) with a block on it being shaped, a heap of rubble."""
    for x, z in ((1, 2), (2, 2), (1, 3)):
        slab(b, x, 1, z, SPRUCE)
    b.set(1, 2, 2, "stone_bricks")
    b.set(2, 2, 2, "polished_andesite")
    b.set(1, 2, 3, "stone_bricks")
    b.set(1, 3, 2, "polished_andesite")
    for x, z, block in ((9, 2, "smooth_stone"), (9, 3, "smooth_stone"), (8, 2, "stone_bricks")):
        slab(b, x, 1, z, SPRUCE)
    b.set(9, 2, 2, "smooth_stone")
    b.set(9, 2, 3, "chiseled_stone_bricks")
    b.set(8, 2, 2, "polished_andesite")
    wall_block(b, 7, 1, 5, "stone_brick_wall")
    wall_block(b, 9, 1, 5, "stone_brick_wall")
    slab(b, 7, 2, 5, "smooth_stone_slab")
    slab(b, 8, 2, 5, "smooth_stone_slab")
    slab(b, 9, 2, 5, "smooth_stone_slab")
    slab(b, 8, 1, 5, STONE_BRICK, top=True)
    b.set(8, 3, 5, "andesite_wall", north="none", south="none", east="none", west="none", up=True, waterlogged=False)
    b.set(2, 1, 6, "cobblestone")
    slab(b, 1, 1, 6, COBBLE)
    slab(b, 2, 1, 5, COBBLE)
    b.set(9, 1, 6, "stone_brick_slab", type="bottom", waterlogged=False)


def masons_yard():
    """11 x 9 x 11: a yard paved with cut stone behind a fence on stone brick piers, a lantern on each pier of the way in,
    stacks of dressed stone and a mason's bench, and a spruce lean-to along the stone back wall over the stonecutter
    (Mason)."""
    b = Build(11, 9, 11)
    masons_yard_ground(b)
    masons_yard_lean_to(b)
    masons_yard_fence(b)
    masons_yard_stock(b)
    b.fill_air()
    return b


def masons_yard_2():
    """11 x 9 x 11: a second stonecutter under the lean-to (a second mason), and a timber hoist over the stacks, a block
    of stone on its chain and a windlass of logs at its foot."""
    b = masons_yard().grow(11, 9, 11)
    b.set(7, 1, 8, "stonecutter", facing="north")
    b.set(9, 1, 8, "chest", facing="west", type="single", waterlogged=False)
    # The hoist: two posts, a beam across them and an arm out over the stacks
    for z in (2, 5):
        for y in range(1, 6):
            log(b, 3, y, z, YARD_FRAME)
    for z in range(2, 6):
        log(b, 3, 6, z, YARD_FRAME, axis="z")
    for z in (3, 4):
        stairs(b, 3, 5, z, SPRUCE, "north" if z == 3 else "south", top=True)
    log(b, 2, 6, 3, YARD_FRAME, axis="x")
    log(b, 1, 6, 3, YARD_FRAME, axis="x")
    b.set(1, 5, 3, "chain", axis="y", waterlogged=False)
    b.set(1, 4, 3, "chain", axis="y", waterlogged=False)
    b.clear(1, 3, 2, 1, 3, 2)
    b.set(1, 3, 3, "polished_andesite")
    b.set(1, 2, 3, "air")
    b.set(1, 1, 3, "spruce_slab", type="bottom", waterlogged=False)
    # The windlass at the hoist's foot: a log drum between two posts
    fence(b, 4, 1, 3, "spruce_fence")
    fence(b, 4, 1, 4, "spruce_fence")
    log(b, 4, 2, 3, "stripped_spruce_log", axis="z")
    log(b, 4, 2, 4, "stripped_spruce_log", axis="z")
    lantern(b, 3, 7, 2)
    b.fill_air()
    return b


# --- Fletcher's Lodge (ROADMAP 27.13) -------------------------------------------------------------------------------
# Fletcher's Lodge: I a log cabin of spruce logs on a mossy stone footing, the logs crossing at the corners, a dark oak roof
# with its gable to the street, a stone chimney up the west side, a log pile against the east wall and a straw target
# (a target on hay bales) in front; inside the fletching table (Fletcher; Lumberjack with an axe). II a drying-rack wing
# on the east under a lower roof of its own, open on its sides, with a second fletching table; the log pile moves to the
# back of the cabin.
LODGE_LOG = "spruce_log"
LODGE_PLINTH = Mix((5, "cobblestone"), (3, "mossy_cobblestone"), (2, "stone"), seed=61)
LODGE_ROOF = DARK_OAK
LODGE_GABLE = "spruce_planks"


def lodge_cabin(b):
    """The cabin (walls x 1-7, z 2-8, four logs high): logs laid along each wall, crossing past the corners on alternate
    courses as a log cabin's do; a door in the middle of the front under a hood, shuttered windows."""
    plinth(b, 1, 2, 7, 8, LODGE_PLINTH, floor="spruce_planks")
    for y in range(1, 5):
        for x in range(1, 8):
            log(b, x, y, 2, LODGE_LOG, axis="x")
            log(b, x, y, 8, LODGE_LOG, axis="x")
        for z in range(3, 8):
            log(b, 1, y, z, LODGE_LOG, axis="z")
            log(b, 7, y, z, LODGE_LOG, axis="z")
        # the corners: one course runs on past the corner along x, the next along z
        for x, z in ((1, 2), (7, 2), (1, 8), (7, 8)):
            if y % 2:
                log(b, x, y, z, LODGE_LOG, axis="x")
                log(b, x + (1 if x == 7 else -1), y, z, LODGE_LOG, axis="x")
            else:
                log(b, x, y, z, LODGE_LOG, axis="z")
                log(b, x, y, z + (1 if z == 8 else -1), LODGE_LOG, axis="z")
    for x, z in ((0, 2), (8, 2), (0, 8), (8, 8), (1, 1), (7, 1), (1, 9), (7, 9)):
        b.set(x, 0, z, resolve(LODGE_PLINTH, x, 0, z))
    door(b, 4, 1, 2, "spruce_door", "north")
    stairs(b, 4, 0, 1, COBBLE, "south")
    for x in (3, 4, 5):
        stairs(b, x, 3, 1, SPRUCE, "south", top=True)
    for x in (2, 6):
        window(b, x, 2, 2, "north", shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 7, 2, 5, "east", height=2, shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 4, 2, 8, "south", height=2, shutters="spruce_trapdoor", sill=SPRUCE)
    # Lanterns on stones either side of the step
    for x in (2, 6):
        b.set(x, 0, 1, resolve(LODGE_PLINTH, x, 0, 1))
    lantern(b, 2, 1, 1)
    lantern(b, 6, 1, 1)


def lodge_roof(b, back=9):
    """A dark oak roof with its gables front and back (spruce boards round a little window), the ridge front to back."""
    gable_roof(b, 0, 8, 1, back, 5, LODGE_ROOF, axis="z", gable=LODGE_GABLE, gable_at=(2, 8), eave_trim=SPRUCE)
    lodge_gables(b)


def lodge_gables(b):
    for z in (2, 8):
        log(b, 4, 5, z, "stripped_oak_log")
        log(b, 4, 7, z, "stripped_oak_log")
        pane(b, 4, 6, z)
        for x in (2, 6):
            log(b, x, 5, z, "stripped_oak_log")


def lodge_chimney(b):
    """A stone chimney up the west wall, outside, smoking."""
    for z in (4, 5, 6):
        for y in range(0, 3):
            b.set(0, y, z, resolve(LODGE_PLINTH, 0, y, z))
    stairs(b, 0, 3, 4, COBBLE, "south")
    stairs(b, 0, 3, 6, COBBLE, "north")
    for y in range(3, 10):
        b.set(0, y, 5, resolve(LODGE_PLINTH, 0, y, 5))
    b.set(0, 10, 5, "campfire", facing="north", lit=True, signal_fire=False, waterlogged=False)


def lodge_inside(b):
    """The fletcher's fletching table at the back, a chest of feathers and flint, a hearth of stone by the chimney, a
    lantern on the beam."""
    b.set(6, 1, 7, "fletching_table")
    b.set(2, 1, 7, "chest", facing="east", type="single", waterlogged=False)
    for z in (4, 6):
        b.set(2, 1, z, "stone_bricks")
    b.set(2, 1, 5, "campfire", facing="east", lit=False, signal_fire=False, waterlogged=False)
    for z in range(3, 8):
        log(b, 4, 4, z, "stripped_oak_log", axis="z")
    lantern(b, 4, 3, 5, hanging=True)


def lodge_target(b, x):
    """A straw target: hay bales with a target on top, a step of cobble to stand on in front."""
    b.set(x, 0, 0, "hay_block", axis="y")
    b.set(x, 1, 0, "hay_block", axis="x")
    b.set(x, 2, 0, "target", power=0)


def fletchers_lodge():
    """11 x 11 x 10: a log cabin of spruce logs crossing at the corners, a dark oak roof with its gable to the street, a stone
    chimney up the west side, a log pile against the east wall and a straw target in front; inside the fletching table
    (Fletcher; Lumberjack with an axe)."""
    b = Build(11, 11, 10)
    lodge_cabin(b)
    lodge_roof(b)
    lodge_chimney(b)
    lodge_inside(b)
    # The log pile against the east wall, under the eaves
    for z in range(4, 8):
        for y in range(0, 3):
            log(b, 8, y, z, "oak_log", axis="z")
        for y in range(0, 2):
            log(b, 9, y, z, "spruce_log", axis="z")
    b.set(10, 0, 5, "oak_log", axis="y")
    b.set(10, 1, 5, "oak_log", axis="y")
    lodge_target(b, 10)
    b.fill_air()
    return b


def lodge_wing(b):
    """The drying-rack wing on the east (x 8-11, z 3-7): spruce posts on a stone footing, open sides, racks of fence
    rails with drying bundles hung on chains, a second fletching table under one of them."""
    plinth(b, 8, 3, 11, 7, LODGE_PLINTH, floor="spruce_planks")
    posts(b, [(11, 3), (11, 7), (8, 3), (8, 7), (11, 5)], 1, 3, "spruce_log")
    for x in range(8, 12):
        log(b, x, 4, 3, "spruce_log", axis="x")
        log(b, x, 4, 7, "spruce_log", axis="x")
    for z in range(4, 7):
        log(b, 11, 4, z, "spruce_log", axis="z")
    # Low fences round the open sides; the way in at the front
    for z in (4, 6):
        fence(b, 11, 1, z, "spruce_fence")
    for x in (9, 10):
        fence(b, x, 1, 7, "spruce_fence")
    # The racks: two rails across the wing. The front one carries bundles laid over it to dry, clear underneath for the
    # way in; from the back one sheaves hang on chains over hay and kelp, and over the fletching table
    for x in (9, 10):
        for z in (4, 6):
            fence(b, x, 3, z, "spruce_fence")
    b.set(9, 4, 4, "hay_block", axis="x")
    b.set(10, 4, 4, "dried_kelp_block")
    for x in (9, 10):
        b.set(x, 2, 6, "chain", axis="y", waterlogged=False)
    b.set(9, 1, 6, "hay_block", axis="x")
    b.set(10, 1, 6, "fletching_table")
    lantern(b, 11, 0, 2)


def fletchers_lodge_2():
    """13 x 11 x 10: the cabin with a drying-rack wing on the east under a lower roof of its own, open on its sides, its
    racks hung with bundles, and a second fletching table (a second fletcher, or a lumberjack); the log pile moved to the
    back of the cabin, the target to the wing's corner."""
    b = fletchers_lodge().grow(13, 11, 10)
    b.clear(8, 0, 4, 10, 2, 7)
    b.clear(10, 0, 0, 10, 2, 0)
    lodge_wing(b)
    roofs(b,
          lambda t: gable_roof(t, 0, 8, 1, 9, 5, LODGE_ROOF, axis="z", gable=LODGE_GABLE, gable_at=(2, 8), eave_trim=SPRUCE),
          lambda t: gable_roof(t, 8, 12, 2, 8, 4, LODGE_ROOF, axis="x", gable=LODGE_GABLE, gable_at=(11, 11), eave_trim=SPRUCE))
    for z in range(4, 7):
        log(b, 11, 4, z, "spruce_log", axis="z")
    # The log pile now along the back, under the eaves
    for x in range(2, 7):
        log(b, x, 0, 9, "oak_log", axis="x")
        if x in (3, 4, 5):
            log(b, x, 1, 9, "oak_log", axis="x")
    lodge_target(b, 12)
    b.fill_air()
    return b


# --- Map Room (ROADMAP 27.13) ---------------------------------------------------------------------------------------
# Map Room: a narrow tower house: a stone ground floor (flared at the foot) with the cartography table (Cartographer),
# a timber-framed plaster storey above with shelves of charts, and at the top a lookout: a balcony on stair brackets all
# round, a railing, and a slate cap on four posts. A ladder runs up inside from the ground to the lookout.
MAP_STONE = Mix((5, "stone_bricks"), (3, "cobblestone"), (2, "andesite"), (1, "mossy_stone_bricks"), seed=71)
MAP_FRAME = "spruce_log"
MAP_PLASTER = Mix((7, "white_concrete"), (1, "polished_diorite"), seed=72)
MAP_ROOF = DEEPSLATE_TILE


def map_room():
    """9 x 16 x 9: a narrow tower house: a stone ground floor flared at the foot, with the cartography table
    (Cartographer); a timber-framed plaster storey of shelves and charts; a lookout at the top on stair brackets with a
    railing all round under a slate cap on four posts; a ladder up inside."""
    b = Build(9, 16, 9)
    # Ground floor: stone walls x 1-7, z 1-7, four high, on a flared foot
    plinth(b, 1, 1, 7, 7, FOUNDATION_MIX, floor=Mix((3, "spruce_planks"), (1, "stripped_spruce_wood"), seed=73))
    walls(b, 1, 1, 7, 7, 1, 4, MAP_STONE)
    skirt(b, 1, 1, 7, 7, STONE_BRICK)
    for x, z in ((1, 1), (7, 1), (1, 7), (7, 7)):
        for y in range(1, 5):
            b.set(x, y, z, "stone_bricks" if y % 2 else "polished_andesite")
    door(b, 4, 1, 1, "spruce_door", "north")
    stairs(b, 4, 0, 0, STONE_BRICK, "south")
    stairs(b, 4, 3, 0, SPRUCE, "south", top=True)
    for x in (3, 5):
        fence(b, x, 3, 0, "spruce_fence")
        lantern(b, x, 2, 0, hanging=True)
    for out, (x, z) in (("west", (1, 4)), ("east", (7, 4)), ("south", (4, 7))):
        window(b, x, 2, z, out, height=2, sill=STONE_BRICK)
    # The upper storey: a spruce beam round the floor, posts at the corners, white plaster, a shuttered window a side
    beam_ring(b, 1, 1, 7, 7, 5, MAP_FRAME)
    box(b, 2, 5, 2, 6, 5, 6, "spruce_planks")
    walls(b, 1, 1, 7, 7, 6, 8, MAP_PLASTER)
    posts(b, [(1, 1), (7, 1), (1, 7), (7, 7)], 6, 8, MAP_FRAME)
    for out, (x, z) in (("north", (4, 1)), ("west", (1, 4)), ("east", (7, 4)), ("south", (4, 7))):
        b.set(x, 6, z, MAP_FRAME, axis="y")
        b.set(x, 8, z, MAP_FRAME, axis="y")
        window(b, x, 7, z, out, shutters="spruce_trapdoor")
        for s in (-1, 1):
            sx, sz = (x + s, z) if out in ("north", "south") else (x, z + s)
            pane(b, sx, 7, sz)
    # The lookout: its floor a block out all round on stair brackets, a railing, posts and a slate cap
    for x in range(0, 9):
        for z in range(0, 9):
            b.set(x, 9, z, "spruce_planks")
    for i in range(1, 8):
        stairs(b, i, 8, 0, SPRUCE, "south", top=True)
        stairs(b, i, 8, 8, SPRUCE, "north", top=True)
        stairs(b, 0, 8, i, SPRUCE, "east", top=True)
        stairs(b, 8, 8, i, SPRUCE, "west", top=True)
    for i in range(0, 9):
        for x, z in ((i, 0), (i, 8), (0, i), (8, i)):
            fence(b, x, 10, z, "spruce_fence")
    posts(b, [(1, 1), (7, 1), (1, 7), (7, 7)], 10, 12, MAP_FRAME)
    beam_ring(b, 1, 1, 7, 7, 12, MAP_FRAME)
    hip_roof(b, 0, 8, 0, 8, 12, MAP_ROOF, rings=4)
    b.set(4, 15, 4, "deepslate_tiles")
    lantern(b, 4, 10, 4)
    # The ladder up the east wall, through both floors
    for y in range(1, 10):
        b.set(6, y, 6, "ladder", facing="west", waterlogged=False)
    # The ground floor: the cartography table, a chest of maps, a lantern on the beam
    b.set(2, 1, 6, "cartography_table")
    b.set(2, 1, 5, "chest", facing="east", type="single", waterlogged=False)
    b.set(6, 1, 2, "chest", facing="west", type="single", waterlogged=False)
    lantern(b, 6, 2, 2)
    for z in range(2, 7):
        log(b, 4, 4, z, MAP_FRAME, axis="z")
    lantern(b, 4, 3, 4, hanging=True)
    # The chart room: shelves along the walls, a table with a chart on it
    for x in (2, 3):
        b.set(x, 6, 6, "bookshelf")
    b.set(2, 6, 5, "bookshelf")
    fence(b, 4, 6, 4, "spruce_fence")
    b.set(4, 7, 4, "spruce_pressure_plate", powered=False)
    b.set(5, 6, 4, "spruce_stairs", facing="west", half="bottom", shape="straight", waterlogged=False)
    b.set(2, 6, 2, "chest", facing="east", type="single", waterlogged=False)
    lantern(b, 2, 7, 2)
    b.fill_air()
    return b


# --- Winery (ROADMAP 34.13) -----------------------------------------------------------------------------------------
# Winery: I a stone press house over a half-sunk cellar. The cellar is the ground storey, of rough mossy stone, with
# earth banked up its sides and back so only a course of it and its barred slits show; its door is at the foot of the
# front, in a passage under the landing of the outside stair. The stair climbs along the front wall to the press room
# above (cut stone, a spruce frame, a clay-tile roof with its gable to the street, a gabled porch over the landing):
# the cauldron vat (Vintner) by the door, a press on a chain, a hatch and ladder down. In the cellar racks of casks
# (spruce logs laid so their ends show) and chests. No barrels anywhere: a barrel is the fisherman's job block.
# II the knoll behind the house: a tasting porch off a back door under a lean-to roof, then two terraces of sweet
# berry rows stepping down to a pergola hung with glow berries.
WINERY_CELLAR = Mix((5, "cobblestone"), (3, "stone"), (2, "mossy_cobblestone"), (1, "andesite"), seed=81)
WINERY_STONE = Mix((7, "stone_bricks"), (2, "andesite"), (1, "cracked_stone_bricks"), seed=82)
WINERY_PAVING = Mix((4, "stone_bricks"), (2, "cobblestone"), (1, "mossy_stone_bricks"), seed=83)
WINERY_FRAME = "spruce_log"
WINERY_ROOF = BRICK
WINERY_CASK = "spruce_log"


def grass(b, x, y, z):
    b.set(x, y, z, "grass_block", snowy=False)


def winery_house(b):
    """The house both tiers share (walls x 2-10, z 3-9): the cellar storey of rough stone (y 0-2), a spruce beam round
    the press room's floor (y 3), the press room of cut stone, spruce posts at its corners and halfway along its sides (y 4-7). Doors one over the other in
    the middle of the front: the cellar's at the ground, the press room's at the head of the stair."""
    plinth(b, 2, 3, 10, 9, WINERY_CELLAR, floor=WINERY_PAVING)
    walls(b, 2, 3, 10, 9, 1, 2, WINERY_CELLAR)
    beam_ring(b, 2, 3, 10, 9, 3, WINERY_FRAME)
    box(b, 3, 3, 4, 9, 3, 8, "spruce_planks")
    walls(b, 2, 3, 10, 9, 4, 7, WINERY_STONE)
    posts(b, [(2, 3), (10, 3), (2, 6), (10, 6), (2, 9), (10, 9)], 4, 7, WINERY_FRAME)
    door(b, 6, 1, 3, "spruce_door", "south")
    door(b, 6, 4, 3, "spruce_door", "south")
    # Front and back: a tall window either side of the middle, under the gable's studs, on a stone sill (none on the
    # front's west one: the stair passes under it, and a sill there would be in a climber's face)
    for z, out in ((3, "north"), (9, "south")):
        for x in (4, 8):
            window(b, x, 5, z, out, height=2, sill=None if (x, z) == (4, 3) else STONE_BRICK)
    # The sides: a wide shuttered window in each bay of the press room, barred slits to the cellar just above the bank
    for z in (5, 8):
        window(b, 2, 5, z, "west", width=2, height=2, shutters="spruce_trapdoor", sill=STONE_BRICK)
    for z in (4, 7):
        window(b, 10, 5, z, "east", width=2, height=2, shutters="spruce_trapdoor", sill=STONE_BRICK)
    for x in (2, 10):
        for z in (4, 5):
            b.set(x, 2, z, "iron_bars", north=False, south=False, east=False, west=False, waterlogged=False)


def winery_roof(b):
    """A clay-tile roof, its gables to the street and the back (stone, a king post, a little window), a tie beam across
    the press room with a lantern on it."""
    gable_roof(b, 1, 11, 2, 10, 8, WINERY_ROOF, axis="z", gable=WINERY_STONE, gable_at=(3, 9), eave_trim=SPRUCE)
    beam_ring(b, 2, 3, 10, 9, 8, WINERY_FRAME)
    for z in (3, 9):
        for y in (9, 10, 12):
            log(b, 6, y, z, WINERY_FRAME)
        pane(b, 6, 11, z)
        for x in (4, 8):
            for y in (9, 10):
                log(b, x, y, z, WINERY_FRAME)
    for x in range(3, 10):
        log(b, x, 8, 6, WINERY_FRAME, axis="x")
    lantern(b, 6, 7, 6, hanging=True)


def winery_perron(b):
    """The outside stair: four steps up along the front wall from the west to a landing before the press room's door,
    on two piers with the passage to the cellar door between them; a railing, and a gabled porch roof on posts with a
    lantern under its beam. East of it a rack of casks under the eaves."""
    for i, x in enumerate((1, 2, 3, 4)):
        for y in range(0, i):
            b.set(x, y, 2, resolve(WINERY_CELLAR, x, y, 2))
        stairs(b, x, i, 2, STONE_BRICK, "east")
    for z in (1, 2):
        for x in (5, 7):
            for y in range(0, 3):
                b.set(x, y, z, resolve(WINERY_CELLAR, x, y, z))
        b.set(6, 0, z, resolve(WINERY_PAVING, 6, 0, z))
        for x in (5, 6, 7):
            b.set(x, 3, z, "stone_bricks")
    for x in (5, 7):
        for y in (4, 5, 6):
            fence(b, x, y, 1, "spruce_fence")
    fence(b, 6, 4, 1, "spruce_fence")
    fence(b, 7, 4, 2, "spruce_fence")
    gable_roof(b, 4, 8, 0, 2, 7, WINERY_ROOF, axis="z")
    for x in (5, 6, 7):
        log(b, x, 7, 1, WINERY_FRAME, axis="x")
    lantern(b, 6, 6, 1, hanging=True)
    # Casks: log ends to the street, three and two, a lantern on the low end
    for x, y in ((8, 0), (9, 0), (10, 0), (8, 1), (9, 1)):
        log(b, x, y, 2, WINERY_CASK, axis="z")
    lantern(b, 10, 1, 2)


def winery_bank(b, back=11):
    """Earth banked against the cellar: two high against the walls, one high outside that, stone where it ends at the
    front. {@code back}: the last row the side banks reach (tier II runs them on beside its porch)."""
    for z in range(3, back + 1):
        for inner, outer in ((1, 0), (11, 12)):
            if z == 3:
                for y in (0, 1):
                    b.set(inner, y, z, resolve(WINERY_CELLAR, inner, y, z))
                b.set(outer, 0, z, resolve(WINERY_CELLAR, outer, 0, z))
            else:
                b.set(inner, 0, z, "dirt")
                grass(b, inner, 1, z)
                grass(b, outer, 0, z)


def winery_back_bank(b):
    """Tier I: the bank round the back of the cellar."""
    for x in range(1, 12):
        b.set(x, 0, 10, "dirt")
        grass(b, x, 1, 10)
    for x in range(0, 13):
        grass(b, x, 0, 11)
    for x, plant in ((3, "poppy"), (9, "dandelion")):
        b.set(x, 2, 10, plant)


def winery_inside(b):
    """The press room: the cauldron vat by the door, the press (a tub under a chain from the tie beam), a table, casks,
    the hatch. The cellar: casks two high along the west and back walls, two big chests along the east, the ladder."""
    b.set(7, 4, 4, "cauldron")
    log(b, 8, 4, 6, "stripped_spruce_log")
    for y in (5, 6, 7):
        b.set(8, y, 6, "chain", axis="y", waterlogged=False)
    fence(b, 3, 4, 5, "spruce_fence")
    b.set(3, 5, 5, "spruce_pressure_plate", powered=False)
    stairs(b, 3, 4, 4, SPRUCE, "north")
    log(b, 3, 4, 8, WINERY_CASK, axis="x")
    log(b, 3, 5, 8, WINERY_CASK, axis="x")
    log(b, 4, 4, 8, WINERY_CASK, axis="z")
    lantern(b, 4, 5, 8)
    trapdoor(b, 9, 3, 8, "spruce_trapdoor", "west", half="top")
    for y in (1, 2):
        b.set(9, y, 8, "ladder", facing="west", waterlogged=False)
        for z in (6, 7, 8):
            log(b, 3, y, z, WINERY_CASK, axis="x")
        for x in (4, 5, 6, 7):
            log(b, x, y, 8, WINERY_CASK, axis="z")
    for z, kind in ((4, "right"), (5, "left"), (6, "right"), (7, "left")):
        b.set(9, 1, z, "chest", facing="west", type=kind, waterlogged=False)
    lantern(b, 8, 1, 8)


def winery():
    """13 x 14 x 12: a stone press house over a half-sunk cellar: earth banked up the cellar's sides and back, its door
    under the landing of the outside stair, the press room above under a clay-tile roof with a gabled porch; the
    cauldron vat (Vintner) by the door, a press, racks of casks (spruce log ends) and a cellar of chests."""
    b = Build(13, 14, 12)
    winery_house(b)
    winery_roof(b)
    winery_perron(b)
    winery_bank(b)
    winery_back_bank(b)
    winery_inside(b)
    window(b, 6, 5, 9, "south", height=2, shutters="spruce_trapdoor", sill=STONE_BRICK)
    b.fill_air()
    return b


WINERY_ROWS = (2, 4, 8, 10)


def winery_terrace(b, x0, x1, z0, z1, top):
    """A garden terrace: earth held by a dry-stone edge on its sides and its low end, grass on top at {@code top}, a
    paved path down the middle, rows of sweet berry bushes running down the slope."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z == z1
            for y in range(0, top):
                b.set(x, y, z, resolve(WINERY_CELLAR, x, y, z) if edge else "dirt")
            if x == 6:
                b.set(x, top, z, resolve(WINERY_PAVING, x, top, z))
            elif edge:
                b.set(x, top, z, resolve(WINERY_CELLAR, x, top, z))
            else:
                grass(b, x, top, z)
                if x in WINERY_ROWS and z < z1:
                    b.set(x, top + 1, z, "sweet_berry_bush", age=0)


def winery_2():
    """13 x 14 x 20: the knoll behind the press house: a back door onto a tasting porch (a table, stools, a cask, a
    railing) under a lean-to of the same tiles, steps down to two terraces of sweet berry rows held by dry-stone edges,
    and over the lower one a pergola hung with glow berries; a lamp at each foot of the garden."""
    b = winery().grow(13, 14, 20)
    b.clear(0, 0, 10, 12, 2, 11)
    b.clear(5, 4, 10, 7, 6, 10)
    winery_bank(b, back=12)
    # The back door where the window was
    b.set(6, 6, 9, resolve(WINERY_STONE, 6, 6, 9))
    door(b, 6, 4, 9, "spruce_door", "north")
    # The porch: a platform as high as the press room's floor, beams round its edge, boards inside
    for x in range(2, 11):
        for z in (10, 11, 12):
            edge = x in (2, 10) or z == 12
            for y in range(0, 3):
                b.set(x, y, z, resolve(WINERY_CELLAR, x, y, z) if edge else "dirt")
            if z == 12:
                log(b, x, 3, z, WINERY_FRAME, axis="x")
            elif edge:
                log(b, x, 3, z, WINERY_FRAME, axis="z")
            else:
                b.set(x, 3, z, "spruce_planks")
    posts(b, [(2, 12), (5, 12), (7, 12), (10, 12)], 4, 5, WINERY_FRAME)
    for x in (3, 4, 8, 9):
        fence(b, x, 4, 12, "spruce_fence")
    for x in (2, 10):
        for z in (10, 11):
            fence(b, x, 4, z, "spruce_fence")
    for x in range(1, 12):
        if 2 <= x <= 10:
            stairs(b, x, 8, 10, WINERY_ROOF, "north")
        stairs(b, x, 7, 11, WINERY_ROOF, "north")
        stairs(b, x, 6, 12, WINERY_ROOF, "north")
    # Tasting: a table with a stool either side, a cask with a lantern on it and a stool by it
    fence(b, 4, 4, 11, "spruce_fence")
    b.set(4, 5, 11, "spruce_pressure_plate", powered=False)
    stairs(b, 3, 4, 11, SPRUCE, "west")
    stairs(b, 5, 4, 11, SPRUCE, "east")
    log(b, 8, 4, 11, WINERY_CASK)
    lantern(b, 8, 5, 11)
    stairs(b, 9, 4, 11, SPRUCE, "east")
    # The garden: two terraces stepping down, a stair at the head of each
    winery_terrace(b, 1, 11, 13, 15, 2)
    winery_terrace(b, 0, 12, 16, 18, 1)
    stairs(b, 6, 3, 13, STONE_BRICK, "north")
    stairs(b, 6, 2, 16, STONE_BRICK, "north")
    stairs(b, 6, 1, 19, STONE_BRICK, "north")
    # The pergola over the lower terrace, glow berries hanging from its rafters
    for x, z in ((5, 16), (7, 16), (5, 18), (7, 18)):
        for y in (2, 3, 4):
            fence(b, x, y, z, "spruce_fence")
    for x in range(4, 9):
        log(b, x, 5, 16, WINERY_FRAME, axis="x")
        log(b, x, 5, 18, WINERY_FRAME, axis="x")
    for x in (5, 6, 7):
        log(b, x, 5, 17, WINERY_FRAME, axis="z")
    for x, z in ((4, 16), (8, 16), (4, 18), (8, 18), (5, 17), (7, 17), (6, 16)):
        b.set(x, 4, z, "cave_vines", age=0, berries=True)
    lamp_post(b, 0, 2, 18)
    lamp_post(b, 12, 2, 18)
    b.fill_air()
    return b


# --- Tailor's Shop (ROADMAP 34.13) ----------------------------------------------------------------------------------
# Tailor's Shop: I a timber shop, dark oak frame and white plaster under a spruce roof with its gable to the street: the
# door under a hood between two banners of the tailor's cloth, a bay window beside it with dress forms and a bolt on
# show, a lower wing behind for the fitting room. Inside the loom (Tailor), a counter, bolts of coloured wool along the
# walls. II a cutting-room storey above (a cutting table, bolts of cloth on racks) and a drying loft in the roof, its
# front open behind a rail with dyed wool hung from the rafters and a bale on the hoist; a ladder up through both.
TAILOR_PLINTH = Mix((6, "stone_bricks"), (2, "cobblestone"), (1, "cracked_stone_bricks"), seed=91)
TAILOR_PLASTER = Mix((7, "white_concrete"), (1, "polished_diorite"), seed=92)
TAILOR_FRAME = "dark_oak_log"
TAILOR_ROOF = SPRUCE
TAILOR_BOLTS = ("red_wool", "blue_wool", "yellow_wool", "lime_wool", "magenta_wool", "cyan_wool", "orange_wool", "white_wool")


def dress_form(b, x, y, z, cloth):
    """A dress form: a post with a length of cloth on it."""
    fence(b, x, y, z, "dark_oak_fence")
    b.set(x, y + 1, z, cloth)


def tailors_wing(b):
    """The fitting room: a lower wing behind the shop (walls x 2-8, z 7-10, three high) under its own roof, which dies
    into the shop's back wall: a door from the shop, a carpet, a dress form, a stool, a shuttered window at the back."""
    plinth(b, 2, 7, 8, 10, TAILOR_PLINTH, floor="spruce_planks")
    walls(b, 2, 7, 8, 10, 1, 3, TAILOR_PLASTER)
    posts(b, [(2, 10), (8, 10)], 1, 3, TAILOR_FRAME)
    gable_roof(b, 1, 9, 8, 11, 4, TAILOR_ROOF, axis="z", gable=TAILOR_PLASTER, gable_at=(10, 10), eave_trim=SPRUCE)
    for z in (8, 9):
        log(b, 2, 4, z, TAILOR_FRAME, axis="z")
        log(b, 8, 4, z, TAILOR_FRAME, axis="z")
    for x in range(2, 9):
        log(b, x, 4, 10, TAILOR_FRAME, axis="x")
    for y in (5, 6):
        log(b, 5, y, 10, TAILOR_FRAME)
    window(b, 5, 2, 10, "south", shutters="spruce_trapdoor", sill=SPRUCE)
    for x in range(3, 8):
        log(b, x, 4, 9, TAILOR_FRAME, axis="x")
    lantern(b, 5, 3, 9, hanging=True)
    for x in (4, 5, 6):
        for z in (8, 9):
            b.set(x, 1, z, "red_carpet")
    dress_form(b, 7, 1, 9, "purple_wool")
    stairs(b, 3, 1, 9, DARK_OAK, "west")


def tailors_ground(b):
    """The shop's ground floor (walls x 1-9, z 1-7, four high): the door in the west bay of the front, the bay window in
    the east one; the loom, a counter, bolts of coloured wool."""
    plinth(b, 1, 1, 9, 7, TAILOR_PLINTH, floor="spruce_planks")
    walls(b, 1, 1, 9, 7, 1, 4, TAILOR_PLASTER)
    posts(b, [(1, 1), (5, 1), (9, 1), (1, 4), (9, 4), (1, 7), (9, 7)], 1, 4, TAILOR_FRAME)
    # The door: a step, a hood with a lantern under it, a banner of the tailor's cloth either side
    door(b, 3, 1, 1, "dark_oak_door", "south")
    stairs(b, 3, 0, 0, STONE_BRICK, "south")
    slab(b, 3, 4, 0, TAILOR_ROOF)
    lantern(b, 3, 3, 0, hanging=True)
    b.set(2, 3, 0, "red_wall_banner", facing="north")
    b.set(4, 3, 0, "blue_wall_banner", facing="north")
    # The bay window: a block out from the wall on its own footing, three panes wide between posts, a pent roof
    for x in range(5, 10):
        b.set(x, 0, 0, resolve(TAILOR_PLINTH, x, 0, 0))
        stairs(b, x, 4, 0, TAILOR_ROOF, "south")
    posts(b, [(5, 0), (9, 0)], 1, 3, TAILOR_FRAME)
    b.clear(6, 1, 1, 8, 3, 1)
    for x in (6, 7, 8):
        b.set(x, 1, 0, "dark_oak_planks")
        pane(b, x, 2, 0)
        pane(b, x, 3, 0)
        b.set(x, 0, 1, "spruce_planks")
        log(b, x, 4, 1, TAILOR_FRAME, axis="x")
    dress_form(b, 6, 1, 1, "red_wool")
    dress_form(b, 8, 1, 1, "blue_wool")
    b.set(7, 1, 1, "yellow_wool")
    # The sides: one wide window each, a flower box on the west, shutters on both
    window(b, 1, 2, 3, "west", width=2, height=2, shutters="spruce_trapdoor", flowers=("spruce_trapdoor", ["potted_red_tulip", "potted_blue_orchid"]))
    window(b, 9, 2, 2, "east", width=2, height=2, shutters="spruce_trapdoor", sill=SPRUCE)
    # The way through to the fitting room
    b.set(5, 0, 7, "spruce_planks")
    door(b, 5, 1, 7, "dark_oak_door", "north")
    # Inside: the loom and its chest, a counter, bolts of wool stacked along the back wall
    b.set(2, 1, 5, "loom", facing="east")
    b.set(2, 1, 6, "chest", facing="east", type="single", waterlogged=False)
    for x in (6, 7, 8):
        slab(b, x, 1, 4, DARK_OAK, top=True)
    for i, (x, y) in enumerate(((8, 1), (8, 2), (7, 1), (7, 2), (6, 1), (3, 1), (3, 2))):
        b.set(x, y, 6, TAILOR_BOLTS[i])
    beam_ring(b, 1, 1, 9, 7, 5, TAILOR_FRAME)
    for x in range(2, 9):
        log(b, x, 5, 4, TAILOR_FRAME, axis="x")
    lantern(b, 5, 4, 4, hanging=True)


def tailors_gable(b, z, y, window_at=None):
    """A gable's timbers at the wall {@code z}, its lowest plaster row at {@code y}: a king post, a stud either side,
    a little window in the post if asked."""
    for dy in range(0, 4):
        log(b, 5, y + dy, z, TAILOR_FRAME)
    for x in (3, 7):
        log(b, x, y, z, TAILOR_FRAME)
    if window_at is not None:
        pane(b, 5, window_at, z)


def tailors_shop():
    """11 x 11 x 12: a timber shop, dark oak and white plaster under a spruce roof: the door between two banners, a bay
    window with dress forms on show, a lower wing behind for the fitting room; inside the loom (Tailor), a counter
    and bolts of coloured wool."""
    b = Build(11, 11, 12)
    tailors_wing(b)
    tailors_ground(b)
    gable_roof(b, 0, 10, 0, 7, 5, TAILOR_ROOF, axis="z", gable=TAILOR_PLASTER, gable_at=(1, 7), eave_trim=SPRUCE)
    beam_ring(b, 1, 1, 9, 7, 5, TAILOR_FRAME)
    tailors_gable(b, 1, 6, window_at=7)
    tailors_gable(b, 7, 6)
    b.fill_air()
    return b


def tailors_shop_2():
    """11 x 15 x 12: a cutting-room storey above the shop (a cutting table with cloth laid out, bolts of cloth on racks
    along the walls) and a drying loft in the roof: its front open behind a rail, dyed wool hung from the rafters, a
    bale on the hoist over the street; a ladder up through both floors."""
    b = Build(11, 15, 12)
    tailors_wing(b)
    tailors_ground(b)
    # The cutting room: its floor on the shop's beams, plaster walls in the same frame, windows over the ones below
    box(b, 2, 5, 2, 8, 5, 6, "spruce_planks")
    for x in range(2, 9):
        log(b, x, 5, 4, TAILOR_FRAME, axis="x")
    walls(b, 1, 1, 9, 7, 6, 8, TAILOR_PLASTER)
    posts(b, [(1, 1), (5, 1), (9, 1), (1, 4), (9, 4), (1, 7), (5, 7), (9, 7)], 6, 8, TAILOR_FRAME)
    window(b, 3, 7, 1, "north", shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 6, 7, 1, "north", width=3, shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 1, 7, 3, "west", width=2, shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 9, 7, 2, "east", width=2, shutters="spruce_trapdoor", sill=SPRUCE)
    for x in (4, 5, 6):
        b.set(x, 6, 4, "dark_oak_planks")
    b.set(4, 7, 4, "light_blue_carpet")
    b.set(6, 7, 4, "yellow_carpet")
    for i, (x, z) in enumerate(((2, 5), (2, 6), (3, 6), (4, 6), (6, 6), (7, 6), (8, 6))):
        fence(b, x, 6, z, "dark_oak_fence")
        b.set(x, 7, z, TAILOR_BOLTS[(i + 3) % len(TAILOR_BOLTS)])
    lantern(b, 5, 8, 3, hanging=True)
    # The loft: its floor on the top beams, the roof over it, the front gable open behind a rail
    gable_roof(b, 0, 10, 0, 7, 9, TAILOR_ROOF, axis="z", gable=TAILOR_PLASTER, gable_at=(1, 7), eave_trim=SPRUCE)
    beam_ring(b, 1, 1, 9, 7, 9, TAILOR_FRAME)
    box(b, 2, 9, 2, 8, 9, 6, "spruce_planks")
    tailors_gable(b, 7, 10, window_at=11)
    b.clear(4, 10, 1, 6, 11, 1)
    for x in (3, 7):
        for y in (10, 11):
            log(b, x, y, 1, TAILOR_FRAME)
    for x in (4, 5, 6):
        fence(b, x, 10, 1, "dark_oak_fence")
        log(b, x, 12, 1, TAILOR_FRAME, axis="x")
    log(b, 5, 13, 1, TAILOR_FRAME)
    # Dyed wool hung from the rafters to dry, and a bale going up on the hoist
    for i, z in enumerate((2, 4, 6)):
        for x in (4, 5, 6):
            log(b, x, 12, z, TAILOR_FRAME, axis="x")
        b.set(4, 11, z, TAILOR_BOLTS[(2 * i) % len(TAILOR_BOLTS)])
        b.set(6, 11, z, TAILOR_BOLTS[(2 * i + 5) % len(TAILOR_BOLTS)])
    lantern(b, 5, 10, 5)
    log(b, 5, 13, 0, TAILOR_FRAME, axis="z")
    for y in (11, 12):
        b.set(5, y, 0, "chain", axis="y", waterlogged=False)
    b.set(5, 10, 0, "magenta_wool")
    # The ladder up the east wall, through both floors
    for y in range(1, 10):
        b.set(8, y, 5, "ladder", facing="west", waterlogged=False)
    b.fill_air()
    return b


# --- Print Shop (ROADMAP 34.14) -------------------------------------------------------------------------------------
# Print Shop: I a brick shop in a spruce frame with its plastered gable to the street, under a dark oak roof: the door
# under a hood in the middle bay, a wide shuttered window either side. Inside the cartography table is the press
# (Printer), standing free under a skylight in the west slope of the roof; a counter for buyers by the door, and shelves
# of paper (white sheets) and ink (black pots) behind it. No lectern anywhere: a lectern would make a librarian.
# II a lower wing behind: the bindery (a bench of sheets, a chest of paper) and a reading room lined with bookshelves
# three high, a chair on a carpet under a lantern.
PRINT_PLINTH = Mix((6, "stone_bricks"), (2, "cobblestone"), (1, "cracked_stone_bricks"), seed=101)
PRINT_BRICK = Mix((9, "bricks"), (1, "granite"), seed=102)
PRINT_PLASTER = Mix((7, "white_concrete"), (1, "polished_diorite"), seed=103)
PRINT_FRAME = "spruce_log"
PRINT_ROOF = DARK_OAK


def shelf(b, x, y, z, what):
    """A shelf (the top half of a board) with paper (a white sheet) or ink (black pots) on it."""
    slab(b, x, y, z, SPRUCE, top=True)
    if what == "paper":
        b.set(x, y + 1, z, "white_carpet")
    else:
        b.set(x, y + 1, z, "black_candle", candles=3, lit=False, waterlogged=False)


def print_shop_ground(b):
    """The shop (walls x 1-9, z 1-7, four high): brick in a spruce frame, the door in the middle bay of the front."""
    plinth(b, 1, 1, 9, 7, PRINT_PLINTH, floor="spruce_planks")
    walls(b, 1, 1, 9, 7, 1, 4, PRINT_BRICK)
    posts(b, [(1, 1), (4, 1), (6, 1), (9, 1), (1, 4), (9, 4), (1, 7), (4, 7), (6, 7), (9, 7)], 1, 4, PRINT_FRAME)
    door(b, 5, 1, 1, "spruce_door", "south")
    stairs(b, 5, 0, 0, STONE_BRICK, "south")
    slab(b, 5, 4, 0, PRINT_ROOF)
    lantern(b, 5, 3, 0, hanging=True)
    window(b, 2, 2, 1, "north", width=2, height=2, shutters="spruce_trapdoor", flowers=("spruce_trapdoor", ["potted_cornflower", "potted_dandelion"]))
    window(b, 7, 2, 1, "north", width=2, height=2, shutters="spruce_trapdoor", sill=STONE_BRICK)
    window(b, 1, 2, 3, "west", width=2, height=2, shutters="spruce_trapdoor", sill=STONE_BRICK)
    window(b, 1, 2, 6, "west", width=2, height=2, shutters="spruce_trapdoor", sill=STONE_BRICK)
    window(b, 9, 2, 2, "east", width=2, height=2, shutters="spruce_trapdoor", sill=STONE_BRICK)
    beam_ring(b, 1, 1, 9, 7, 5, PRINT_FRAME)
    for x in range(2, 9):
        log(b, x, 5, 3, PRINT_FRAME, axis="x")
    lantern(b, 6, 4, 3, hanging=True)
    # The press, free in the west of the room under the skylight, its chest of paper by the wall
    b.set(3, 1, 5, "cartography_table")
    b.set(2, 1, 6, "chest", facing="east", type="single", waterlogged=False)
    # The counter for buyers, east of the door, and the shelves of paper and ink behind it
    for x in (6, 7, 8):
        slab(b, x, 1, 3, DARK_OAK, top=True)
    for i, (x, z) in enumerate(((8, 5), (8, 6), (7, 6), (6, 6))):
        shelf(b, x, 1, z, "paper" if i % 2 == 0 else "ink")
        shelf(b, x, 3, z, "ink" if i % 2 == 0 else "paper")


def print_shop_roof(b):
    """The dark oak roof, its gable to the street (plaster, a king post, studs, a little window), and the skylight over
    the press: four panes of glass let into the west slope."""
    gable_roof(b, 0, 10, 0, 8, 5, PRINT_ROOF, axis="z", gable=PRINT_PLASTER, gable_at=(1, 7), eave_trim=SPRUCE)
    beam_ring(b, 1, 1, 9, 7, 5, PRINT_FRAME)
    for z, window_at in ((1, 7), (7, None)):
        for dy in range(0, 4):
            log(b, 5, 6 + dy, z, PRINT_FRAME)
        for x in (3, 7):
            log(b, x, 6, z, PRINT_FRAME)
        if window_at is not None:
            pane(b, 5, window_at, z)
    for x in (2, 3):
        for z in (4, 5):
            b.set(x, 5 + x, z, "glass")


def print_shop():
    """11 x 11 x 9: a brick shop in a spruce frame, its plastered gable to the street under a dark oak roof: the
    cartography table as the press (Printer) under a skylight, a counter for buyers, paper and ink on shelves."""
    b = Build(11, 11, 9)
    print_shop_ground(b)
    print_shop_roof(b)
    window(b, 5, 2, 7, "south", height=2, shutters="spruce_trapdoor", sill=STONE_BRICK)
    b.fill_air()
    return b


def print_shop_2():
    """11 x 11 x 16: a lower wing behind the shop: the bindery (a bench of sheets, a chest of paper) and a reading
    room lined with bookshelves three high, a chair on a carpet under a lantern, a shuttered window at the back."""
    b = print_shop().grow(11, 11, 16)
    b.clear(4, 1, 8, 6, 4, 8)
    # The wing: walls x 2-8, z 8-14, three high, brick in the same frame, its roof dying into the shop's back wall
    for z in range(8, 15):
        for x in range(2, 9):
            b.set(x, 0, z, resolve(PRINT_PLINTH, x, 0, z) if x in (2, 8) or z == 14 else "spruce_planks")
            if x in (2, 8) or z == 14:
                for y in (1, 2, 3):
                    b.set(x, y, z, resolve(PRINT_BRICK, x, y, z))
    posts(b, [(2, 11), (8, 11), (2, 14), (8, 14)], 1, 3, PRINT_FRAME)
    gable_roof(b, 1, 9, 8, 15, 4, PRINT_ROOF, axis="z", gable=PRINT_PLASTER, gable_at=(14, 14), eave_trim=SPRUCE)
    for z in range(8, 14):
        log(b, 2, 4, z, PRINT_FRAME, axis="z")
        log(b, 8, 4, z, PRINT_FRAME, axis="z")
    for x in range(2, 9):
        log(b, x, 4, 14, PRINT_FRAME, axis="x")
    for y in (5, 6):
        log(b, 5, y, 14, PRINT_FRAME)
    window(b, 5, 2, 14, "south", shutters="spruce_trapdoor", sill=SPRUCE)
    # The way through, where the shop's back window was
    b.set(5, 3, 7, resolve(PRINT_BRICK, 5, 3, 7))
    door(b, 5, 1, 7, "spruce_door", "north")
    # The bindery, by the door: a bench with sheets laid out, a chest of paper
    slab(b, 3, 1, 8, DARK_OAK, top=True)
    slab(b, 3, 1, 9, DARK_OAK, top=True)
    b.set(3, 2, 9, "white_carpet")
    b.set(7, 1, 8, "chest", facing="west", type="single", waterlogged=False)
    b.set(7, 1, 9, "bookshelf")
    b.set(7, 2, 9, "bookshelf")
    # The reading room: bookshelves three high along both walls and the back, a chair on a carpet under a lantern
    for z in (10, 11, 12, 13):
        for y in (1, 2, 3):
            b.set(3, y, z, "bookshelf")
            b.set(7, y, z, "bookshelf")
    for x in (4, 6):
        b.set(x, 1, 13, "bookshelf")
        b.set(x, 3, 13, "bookshelf")
        b.set(x, 2, 13, "bookshelf")
    for x in range(3, 8):
        log(b, x, 4, 11, PRINT_FRAME, axis="x")
    lantern(b, 5, 3, 11, hanging=True)
    for z in (10, 11):
        b.set(5, 1, z, "red_carpet")
    stairs(b, 5, 1, 12, SPRUCE, "south")
    b.fill_air()
    return b


# --- Jeweller's Workshop (ROADMAP 34.14) ----------------------------------------------------------------------------
# Jeweller's Workshop: I a small shop of cut stone on a dark footing under a slate roof, its gable to the street: an
# iron door in the middle of the front (a stone button by it, outside and in), a barred look to its windows (iron
# shutters). Inside the stonecutter (Jeweller) at a bench by the west window under a lantern, a counter, and by the
# front window an amethyst cluster in a glass case. A villager can't open an iron door, so the jeweller's own way in is
# a plank door in the east wall. II a strong room behind, lower, with no window: through a second iron door, a vault of
# ten chests behind iron bars and a third iron door.
JEWEL_FOOT = Mix((5, "cobbled_deepslate"), (3, "polished_deepslate"), (1, "deepslate_bricks"), seed=111)
JEWEL_STONE = Mix((7, "stone_bricks"), (2, "polished_andesite"), (1, "cracked_stone_bricks"), seed=112)
JEWEL_ROOF = DEEPSLATE_TILE


def button(b, x, y, z, facing):
    b.set(x, y, z, "stone_button", face="wall", facing=facing, powered=False)


def iron_door(b, x, y, z, facing):
    door(b, x, y, z, "iron_door", facing)


def bars(b, x, y, z):
    b.set(x, y, z, "iron_bars", north=False, south=False, east=False, west=False, waterlogged=False)


def jewellers_shop(b):
    """The shop (walls x 1-7, z 1-7, four high): cut stone with quoins at the corners, the iron door in the front."""
    plinth(b, 1, 1, 7, 7, JEWEL_FOOT, floor=Mix((3, "polished_andesite"), (1, "smooth_stone"), seed=113))
    walls(b, 1, 1, 7, 7, 1, 4, JEWEL_STONE)
    for x, z in ((1, 1), (7, 1), (1, 7), (7, 7)):
        for y in range(1, 5):
            b.set(x, y, z, "chiseled_stone_bricks" if y % 2 else "polished_andesite")
    iron_door(b, 4, 1, 1, "south")
    stairs(b, 4, 0, 0, STONE_BRICK, "south")
    button(b, 3, 2, 0, "north")
    button(b, 3, 2, 2, "south")
    slab(b, 4, 4, 0, STONE_BRICK)
    lantern(b, 4, 3, 0, hanging=True)
    for x in (2, 6):
        window(b, x, 2, 1, "north", height=2, shutters="iron_trapdoor", sill=STONE_BRICK)
    window(b, 1, 2, 4, "west", height=2, shutters="iron_trapdoor", sill=STONE_BRICK)
    window(b, 7, 2, 3, "east", height=2, shutters="iron_trapdoor", sill=STONE_BRICK)
    # The jeweller's own door, in the east wall towards the back, a step before it
    door(b, 7, 1, 5, "spruce_door", "west")
    stairs(b, 8, 0, 5, STONE_BRICK, "west")
    # The bench by the west window: the stonecutter between two boards, a lantern over it on the tie beam
    beam_ring(b, 1, 1, 7, 7, 5, "stripped_spruce_log")
    for x in range(2, 7):
        log(b, x, 5, 4, "stripped_spruce_log", axis="x")
    b.set(2, 1, 4, "stonecutter", facing="east")
    slab(b, 2, 1, 3, STONE_BRICK, top=True)
    slab(b, 2, 1, 5, STONE_BRICK, top=True)
    lantern(b, 2, 4, 4, hanging=True)
    # The counter, and the amethyst cluster in its glass case by the front window
    for x in (4, 5):
        slab(b, x, 1, 4, "polished_andesite_slab", top=True)
    b.set(6, 1, 2, "polished_andesite")
    b.set(6, 2, 2, "amethyst_cluster", facing="up", waterlogged=False)
    pane(b, 5, 2, 2)
    pane(b, 6, 2, 3)
    slab(b, 6, 3, 2, "smooth_stone_slab")
    b.set(2, 1, 6, "chest", facing="east", type="single", waterlogged=False)
    b.set(3, 1, 6, "purple_carpet")
    b.set(4, 1, 6, "purple_carpet")


def jewellers_roof(b):
    gable_roof(b, 0, 8, 0, 8, 5, JEWEL_ROOF, axis="z", gable=JEWEL_STONE, gable_at=(1, 7), eave_trim=STONE_BRICK)
    beam_ring(b, 1, 1, 7, 7, 5, "stripped_spruce_log")
    for z in (1, 7):
        b.set(4, 6, z, "chiseled_stone_bricks")
        pane(b, 4, 7, z)


def jewellers_workshop():
    """9 x 10 x 9: a small shop of cut stone under a slate roof: an iron door, iron shutters, the stonecutter
    (Jeweller) at a bench under a lantern, a counter, an amethyst cluster in a glass case; a plank door in the east
    wall for the jeweller."""
    b = Build(9, 10, 9)
    jewellers_shop(b)
    jewellers_roof(b)
    window(b, 4, 2, 7, "south", height=2, shutters="iron_trapdoor", sill=STONE_BRICK)
    b.fill_air()
    return b


def jewellers_workshop_2():
    """9 x 10 x 14: a strong room behind the shop, lower and windowless: through an iron door, a vault of ten chests
    behind iron bars with an iron door of its own, a lantern on the wall."""
    b = jewellers_workshop().grow(9, 10, 14)
    b.clear(3, 1, 8, 5, 4, 8)
    for z in range(8, 13):
        for x in range(1, 8):
            edge = x in (1, 7) or z == 12
            b.set(x, 0, z, resolve(JEWEL_FOOT, x, 0, z) if edge else "polished_andesite")
            if edge:
                for y in (1, 2, 3):
                    b.set(x, y, z, resolve(JEWEL_STONE, x, y, z))
    for x in (1, 7):
        for y in (1, 2, 3):
            b.set(x, y, 12, "chiseled_stone_bricks" if y % 2 else "polished_andesite")
            b.set(x, y, 10, "polished_andesite")
    gable_roof(b, 0, 8, 8, 13, 4, JEWEL_ROOF, axis="z", gable=JEWEL_STONE, gable_at=(12, 12), eave_trim=STONE_BRICK)
    for z in range(8, 12):
        log(b, 1, 4, z, "stripped_spruce_log", axis="z")
        log(b, 7, 4, z, "stripped_spruce_log", axis="z")
    for x in range(1, 8):
        log(b, x, 4, 12, "stripped_spruce_log", axis="x")
    # The way in, where the shop's back window was: an iron door, a button either side of the wall
    b.set(4, 3, 7, resolve(JEWEL_STONE, 4, 3, 7))
    iron_door(b, 4, 1, 7, "north")
    button(b, 3, 2, 6, "north")
    button(b, 3, 2, 8, "south")
    # The vault: bars across the room with an iron door in them, ten chests behind
    for x in range(2, 7):
        if x == 4:
            iron_door(b, x, 1, 10, "north")
            bars(b, x, 3, 10)
        else:
            for y in (1, 2, 3):
                bars(b, x, y, 10)
        for y in (1, 2):
            b.set(x, y, 11, "chest", facing="north", type="single", waterlogged=False)
    button(b, 2, 2, 9, "east")
    lantern(b, 6, 1, 9)
    b.fill_air()
    return b
