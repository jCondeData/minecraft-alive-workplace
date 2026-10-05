"""Builds for the Pokémon jobs (ROADMAP 28.13): one build and its upgrade per job, in the Blueprint Table only with
Cobblemon, each with its job block in place (hand the villager standing there the job's item and they take it).

Every build is listed in JOB_BUILDS (blueprint id, draw function); generate.py saves them all, so a new build is a
function here and a line in the list (and its Entry in StarterBlueprints.java, a row in PokemonBuildsCompatTests).

Camp Kitchen (13 x 10 x 11), the Camp Cook's: an open timber shelter (spruce posts and beams on a cobble plinth, a
dark oak gable roof along x) round a Campfire Pot (Cobblemon's campfire with a pot on it: hand the villager by it Hearty
Grains), benches of spruce stairs on three sides, and at the back a grain store: Hearty Grain bales stacked in a log
crib, barrels and the cook's chest. Lanterns hang from the tie beams.

Camp Kitchen II (21 x 10 x 19): a Hearty Grain plot behind (farmland rows either side of a water channel, ripe grains,
a fence and a gate) and a smokehouse on the east (a cobblestone hut with a brick chimney and a smoker by the door).

Berry Nursery (13 x 6 x 11), the Berry Breeder's: a fenced garden of farmland beds laid out in pairs, a water channel
between the two beds of each pair, a path up the middle to the composter (hand the villager there a berry), and at the
back a potting bench under a little lean-to roof: pots, saplings, a barrel and her chest.

Berry Nursery II (13 x 9 x 19): a greenhouse behind (glass between spruce posts under a glass roof on spruce rafters)
with four more beds in two pairs, all within reach of the composter.

Daycare (15 x 11 x 18), the Daycare Keeper's: a barn (spruce planks between dark oak posts on a cobble plinth, a dark
oak gable roof, big doors front and back) whose floor is straw (hay) — the nursery — with a fenced paddock behind round
Cobblemon's Pasture Block (hand the villager by it an egg), a trough and bushes.

Daycare II (19 x 11 x 27): a second paddock behind the first with its own Pasture Block, and a hatchery corner on the
barn's east side: a lean-to with nests of hay, lanterns over them, and a barrel.
"""
from kit import *

STONE_BASE = Mix((6, "cobblestone"), (3, "stone"), (2, "mossy_cobblestone"), seed=51)
KITCHEN_FLOOR = Mix((5, "packed_mud"), (3, "coarse_dirt"), (2, "gravel"), seed=52)
POST = "spruce_log"
BEAM = "stripped_spruce_log"


def pasture(b, x, y, z, facing="north"):
    """Cobblemon's Pasture Block (two blocks tall)."""
    b.set(x, y, z, "cobblemon:pasture", facing=facing, part="bottom", on=False, waterlogged=False)
    b.set(x, y + 1, z, "cobblemon:pasture", facing=facing, part="top", on=False, waterlogged=False)


def campfire_pot(b, x, y, z, facing="north"):
    """Cobblemon's Campfire Pot: its campfire with a pot on it (the builder sets a campfire and puts a red pot on)."""
    b.set(x, y, z, "cobblemon:campfire", facing=facing, lid=False)


def hearty_grains(b, x, y, z):
    """A ripe Hearty Grain plant (two blocks tall) on the farmland at y - 1."""
    b.set(x, y, z, "cobblemon:hearty_grains", age=6, half="lower")
    b.set(x, y + 1, z, "cobblemon:hearty_grains", age=6, half="upper")


# --- Camp Kitchen ---------------------------------------------------------------------------------------
def kitchen_shelter(b):
    """The open shelter: posts at x 2, 6, 10 and z 2, 8, a beam ring and tie beams at y 4, a dark oak gable roof."""
    plinth(b, 1, 1, 11, 9, STONE_BASE, floor=KITCHEN_FLOOR)
    for x in (2, 6, 10):
        for z in (2, 8):
            for y in range(1, 4):
                log(b, x, y, z, POST)
    for x in range(2, 11):
        log(b, x, 4, 2, BEAM, axis="x")
        log(b, x, 4, 8, BEAM, axis="x")
    for x in (2, 6, 10):
        for z in range(3, 8):
            log(b, x, 4, z, BEAM, axis="z")
    for x in (2, 10):  # knee braces: upside-down stairs under the beam beside each corner post
        stairs(b, x, 3, 3, SPRUCE, "north", top=True)
        stairs(b, x, 3, 7, SPRUCE, "south", top=True)
    gable_roof(b, 1, 11, 1, 9, 5, DARK_OAK, axis="x", gable=SPRUCE.full, gable_at=(2, 10), eave_trim=SPRUCE)
    for x in (2, 10):  # the gable ends: a vent each, under the ridge
        b.set(x, 7, 5, "spruce_fence", north=False, south=False, east=False, west=False, waterlogged=False)
        b.set(x, 6, 5, "spruce_fence", north=False, south=False, east=False, west=False, waterlogged=False)


def kitchen_hearth(b):
    """The Campfire Pot in the middle on a ring of stone, benches on three sides, lanterns from the tie beams."""
    for x in range(5, 8):
        for z in range(4, 7):
            b.set(x, 0, z, "stone_bricks")
    b.set(6, 0, 5, "mud_bricks")
    campfire_pot(b, 6, 1, 5)
    for x in (4, 5, 7, 8):  # the front bench (its back to the street) and the back bench
        stairs(b, x, 1, 3, SPRUCE, "north")
    for z in (4, 5, 6):
        stairs(b, 3, 1, z, SPRUCE, "west")
        stairs(b, 9, 1, z, SPRUCE, "east")
    for x in (2, 10):
        lantern(b, x, 3, 5, hanging=True)
    lantern(b, 6, 3, 3, hanging=True)


def kitchen_grain_store(b):
    """The back bay: Hearty Grain bales in a log crib, barrels, the cook's chest by the pot, sacks of wheat."""
    for x in (3, 4):
        b.set(x, 1, 7, "cobblemon:hearty_grain_bale", axis="y")
    b.set(3, 2, 7, "cobblemon:hearty_grain_bale", axis="x")
    b.set(4, 2, 7, "hay_block", axis="y")
    b.set(6, 1, 7, "chest", facing="north", type="single", waterlogged=False)
    b.set(7, 1, 7, "barrel", facing="up", open=False)
    b.set(8, 1, 7, "barrel", facing="north", open=False)
    b.set(9, 1, 7, "cobblemon:hearty_grain_bale", axis="z")
    b.set(8, 2, 7, "cobblemon:hearty_grain_bale", axis="x")
    for x in (3, 4, 5, 7, 8, 9):  # a rail of fence along the back between the posts
        fence(b, x, 3, 8, "spruce_fence")
    for x in (5, 7):
        fence(b, x, 2, 8, "spruce_fence")


def kitchen_front(b):
    """A step up at the front, flower beds and a lamp post at each front corner."""
    for x in range(5, 8):
        stairs(b, x, 0, 0, "cobblestone_stairs", "north")
    flower_bed(b, 1, 0, 3, 0, 0, ["dandelion", "poppy", None, "fern"])
    flower_bed(b, 9, 0, 11, 0, 0, ["fern", "dandelion", None, "poppy"])
    for x in (0, 12):
        lamp_post(b, x, 0, 0, post="spruce_fence", height=3)


def camp_kitchen():
    """13 x 10 x 11: an open timber shelter round a Campfire Pot, benches round it, a grain store at the back."""
    b = Build(13, 10, 11)
    kitchen_shelter(b)
    kitchen_hearth(b)
    kitchen_grain_store(b)
    kitchen_front(b)
    b.fill_air()
    return b


def kitchen_plot(b):
    """A fenced Hearty Grain plot behind the shelter (x 1-11, z 11-18): farmland rows either side of a water channel."""
    for x in range(1, 12):
        for z in (11, 18):
            b.set(x, 0, z, "stone_bricks")
    for z in range(12, 18):
        b.set(1, 0, z, "stone_bricks")
        b.set(11, 0, z, "stone_bricks")
        for x in range(2, 11):
            if x == 6:
                b.set(x, 0, z, "water", level=0)
            else:
                b.set(x, 0, z, "farmland", moisture=7)
                hearty_grains(b, x, 1, z)
    for x in range(1, 12):
        fence(b, x, 1, 18, "spruce_fence")
    for z in range(11, 18):
        fence(b, 1, 1, z, "spruce_fence")
        fence(b, 11, 1, z, "spruce_fence")
    b.set(6, 0, 10, "coarse_dirt")  # a way out of the back of the shelter to the plot's gate
    b.set(6, 0, 11, "stone_bricks")
    b.set(6, 1, 11, "spruce_fence_gate", facing="north", open=False, in_wall=False, powered=False)
    for x in range(2, 11):
        if x != 6:
            fence(b, x, 1, 11, "spruce_fence")
    for x in (1, 11):
        lantern(b, x, 2, 18)


SMOKE_WALL = Mix((7, "cobblestone"), (2, "stone"), (1, "mossy_cobblestone"), seed=53)


def kitchen_smokehouse(b):
    """A small cobblestone smokehouse on the east (walls x 14-18, z 2-8), its gable to the street under a spruce roof
    lower than the shelter's: a door on the west by the shelter, a smoker, a barrel and a chest inside, a lantern from
    the tie beam, a brick chimney up the back with a smoking campfire."""
    plinth(b, 14, 2, 18, 8, STONE_BASE, floor="stone_bricks")
    walls(b, 14, 2, 18, 8, 1, 3, SMOKE_WALL)
    posts(b, [(14, 2), (18, 2), (14, 8), (18, 8), (14, 5), (18, 5)], 1, 3, POST)
    beam_ring(b, 14, 2, 18, 8, 4, BEAM)
    b.set(14, 4, 5, BEAM, axis="z")
    b.set(14, 1, 5, "air")
    b.set(14, 2, 5, "air")
    door(b, 14, 1, 6, "spruce_door", "west")
    b.set(14, 1, 5, SMOKE_WALL.at(14, 1, 5))
    b.set(14, 2, 5, SMOKE_WALL.at(14, 2, 5))
    window(b, 16, 2, 2, "north", shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 18, 2, 4, "east", shutters="spruce_trapdoor", sill=SPRUCE)
    b.set(16, 1, 7, "smoker", facing="north", lit=False)
    b.set(15, 1, 7, "barrel", facing="up", open=False)
    b.set(17, 1, 7, "chest", facing="north", type="single", waterlogged=False)
    for x in range(15, 18):
        log(b, x, 4, 5, BEAM, axis="x")
    lantern(b, 16, 3, 5, hanging=True)
    gable_roof(b, 13, 19, 1, 9, 5, SPRUCE, axis="z", gable=SMOKE_WALL, gable_at=(2, 8), eave_trim=SPRUCE)
    pane(b, 16, 6, 2)
    chimney(b, 16, 9, 0, 8, BRICK_WALL_MIX)


def camp_kitchen_2():
    """Upgrade of the Camp Kitchen: a Hearty Grain plot behind and a smokehouse with a smoker on the east. 21 x 10 x 19."""
    b = camp_kitchen().grow(21, 10, 19)
    b.clear(12, 0, 0, 12, 3, 0)  # the east lamp post moves to the smokehouse's corner
    kitchen_plot(b)
    kitchen_smokehouse(b)
    lamp_post(b, 20, 0, 0, post="spruce_fence", height=3)
    b.fill_air()
    return b


# --- Berry Nursery --------------------------------------------------------------------------------------
NURSERY_EDGE = Mix((6, "mud_bricks"), (3, "packed_mud"), seed=54)


def bed_pair(b, x, z0, z1):
    """Two farmland beds (x and x + 2) either side of a water channel (x + 1), from z0 to z1."""
    for z in range(z0, z1 + 1):
        b.set(x, 0, z, "farmland", moisture=7)
        b.set(x + 1, 0, z, "water", level=0)
        b.set(x + 2, 0, z, "farmland", moisture=7)


def nursery_garden(b):
    """The fenced garden (fence round x 0-12, z 0-10): grass, two pairs of beds edged in mud brick, a path to the back."""
    box(b, 1, 0, 1, 11, 0, 9, "grass_block", snowy=False)
    walls(b, 0, 0, 12, 10, 0, 0, NURSERY_EDGE)  # the kerb the fence stands on
    for x0 in (1, 7):  # the mud brick kerb round each pair
        for x in range(x0, x0 + 5):
            b.set(x, 0, 1, NURSERY_EDGE.at(x, 0, 1))
            b.set(x, 0, 7, NURSERY_EDGE.at(x, 0, 7))
        for z in range(2, 7):
            b.set(x0, 0, z, NURSERY_EDGE.at(x0, 0, z))
            b.set(x0 + 4, 0, z, NURSERY_EDGE.at(x0 + 4, 0, z))
        bed_pair(b, x0 + 1, 2, 6)
    for z in range(0, 10):  # (not under the back fence: a path under a fence turns to dirt)
        b.set(6, 0, z, "dirt_path")
    for x in range(0, 13):
        if x != 6:
            fence(b, x, 1, 0, "spruce_fence")
        fence(b, x, 1, 10, "spruce_fence")
    for z in range(1, 10):
        fence(b, 0, 1, z, "spruce_fence")
        fence(b, 12, 1, z, "spruce_fence")
    b.set(6, 1, 0, "spruce_fence_gate", facing="south", open=False, in_wall=False, powered=False)
    for x in (0, 12):
        lantern(b, x, 2, 0)


def nursery_bench(b):
    """The back: the composter by the path, the breeder's chest, and a potting bench under a lean-to roof."""
    b.set(5, 1, 8, "composter", level=0)
    b.set(4, 1, 9, "chest", facing="south", type="single", waterlogged=False)
    b.set(3, 1, 9, "barrel", facing="up", open=False)
    b.set(2, 1, 9, "potted_spruce_sapling")
    b.set(1, 1, 9, "flower_pot")
    # The potting bench: a worktop of slabs on a barrel and trapdoors, pots on top
    for x in (8, 9, 10):
        slab(b, x, 1, 9, SPRUCE, top=True)
    b.set(11, 1, 9, "barrel", facing="north", open=False)
    b.set(8, 2, 9, "potted_oak_sapling")
    b.set(9, 2, 9, "flower_pot")
    b.set(10, 2, 9, "potted_fern")
    b.set(11, 2, 9, "potted_azalea_bush")
    for x in (8, 9, 10):
        trapdoor(b, x, 1, 8, "spruce_trapdoor", "north", half="top")
    # the lean-to: two posts in front of the bench, a slab roof back to the fence
    for x in (7, 12):
        for y in (1, 2, 3):
            log(b, x, y, 8 if x == 7 else 8, POST)
    for x in range(7, 13):
        for z in (8, 9, 10):
            slab(b, x, 4, z, SPRUCE)
    for x in (7, 12):
        for y in (1, 2, 3):
            fence(b, x, y, 10, "spruce_fence")
    lantern(b, 9, 3, 9, hanging=True)
    b.set(9, 4, 9, "spruce_planks")


def berry_nursery():
    """13 x 6 x 11: fenced farmland beds in pairs, a composter by the path and a potting bench under a lean-to."""
    b = Build(13, 6, 11)
    nursery_garden(b)
    nursery_bench(b)
    b.fill_air()
    return b


GREEN_FRAME = "stripped_spruce_log"


def nursery_greenhouse(b):
    """A greenhouse behind (walls x 1-11, z 11-18): glass between spruce posts on a mud brick plinth, a glass roof on
    spruce rafters, a door in front at the end of the path, four beds in two pairs inside (z 13-16)."""
    plinth(b, 1, 11, 11, 18, NURSERY_EDGE, floor="coarse_dirt")
    for x0 in (2, 7):
        bed_pair(b, x0 + (0 if x0 == 2 else 1), 13, 16)
    for z in range(12, 18):
        b.set(6, 0, z, "dirt_path")
    for y in range(1, 4):
        for x in range(1, 12):
            for z in (11, 18):
                b.set(x, y, z, "glass")
        for z in range(12, 18):
            b.set(1, y, z, "glass")
            b.set(11, y, z, "glass")
    posts(b, [(x, z) for x in (1, 4, 8, 11) for z in (11, 18)] + [(x, z) for x in (1, 11) for z in (14, 15)], 1, 3, POST)
    beam_ring(b, 1, 11, 11, 18, 4, GREEN_FRAME)
    b.set(6, 1, 11, "air")
    b.set(6, 2, 11, "air")
    door(b, 6, 1, 11, "spruce_door", "north")
    b.set(5, 1, 11, "spruce_log", axis="y")
    b.set(7, 1, 11, "spruce_log", axis="y")
    b.set(5, 2, 11, "spruce_log", axis="y")
    b.set(7, 2, 11, "spruce_log", axis="y")
    b.set(6, 3, 11, "spruce_planks")
    # The roof: glass slopes between spruce verges (the overhanging ends) over a spruce eave row, a spruce ridge
    for i in range(0, 7):
        y, lo, hi = 5 + i, i, 12 - i
        for z in range(10, 20):
            if lo == hi:
                slab(b, lo, y, z, SPRUCE)
                continue
            for x, face in ((lo, "east"), (hi, "west")):
                if i == 0 or z in (10, 19):
                    stairs(b, x, y, z, SPRUCE, face)
                else:
                    b.set(x, y, z, "glass")
        for z in (11, 18):  # the glass gables
            for x in range(lo + 1, hi):
                b.set(x, y, z, "glass")
        if lo == hi:
            break
    for z in range(12, 18):
        log(b, 6, 9, z, GREEN_FRAME, axis="z")  # a beam along the ridge for the lanterns, and for the builder
    lantern(b, 6, 8, 13, hanging=True)
    lantern(b, 6, 8, 16, hanging=True)
    b.set(2, 1, 17, "potted_red_tulip")
    b.set(10, 1, 17, "barrel", facing="up", open=False)
    b.set(9, 1, 17, "potted_fern")


def berry_nursery_2():
    """Upgrade of the Berry Nursery: a greenhouse behind with four more beds. 13 x 12 x 20."""
    b = berry_nursery().grow(13, 12, 20)
    b.set(6, 1, 10, "spruce_fence_gate", facing="south", open=False, in_wall=False, powered=False)
    b.set(6, 0, 10, "dirt_path")
    nursery_greenhouse(b)
    b.fill_air()
    return b


# --- Daycare --------------------------------------------------------------------------------------------
BARN_WALL = "spruce_planks"
BARN_FRAME = "dark_oak_log"


def barn(b):
    """The barn (walls x 2-12, z 1-7): spruce planks between dark oak posts on a cobble plinth, big doorways front and
    back, a straw floor (hay), a dark oak gable roof along x with a hay loft door in each gable."""
    plinth(b, 2, 1, 12, 7, STONE_BASE, floor="hay_block")
    walls(b, 2, 1, 12, 7, 1, 4, BARN_WALL)
    posts(b, [(x, z) for x in (2, 5, 9, 12) for z in (1, 7)] + [(2, 4), (12, 4)], 1, 4, BARN_FRAME)
    beam_ring(b, 2, 1, 12, 7, 4, BARN_FRAME)
    for z in (1, 7):  # the big doorways, three wide, two high, under a beam
        for x in (6, 7, 8):
            for y in (1, 2):
                b.set(x, y, z, "air")
        for x in (6, 7, 8):
            b.set(x, 0, z, "stone_bricks")
            log(b, x, 3, z, BEAM, axis="x")
    for x in (3, 10):  # a window in each side bay of the front, and of the back
        window(b, x, 2, 1, "north", width=2, shutters="spruce_trapdoor", sill=SPRUCE)
        window(b, x, 2, 7, "south", width=2, sill=SPRUCE)
    window(b, 2, 2, 3, "west", shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 12, 2, 5, "east", shutters="spruce_trapdoor", sill=SPRUCE)
    for x in range(3, 12):
        log(b, x, 4, 4, BEAM, axis="x")  # a tie beam across for the lanterns and the builder
    gable_roof(b, 1, 13, 0, 8, 5, DARK_OAK, axis="x", gable=BARN_WALL, gable_at=(2, 12), eave_trim=SPRUCE)
    for x in (2, 12):  # a loft door in each gable
        trapdoor(b, x, 6, 4, "spruce_trapdoor", "west" if x == 2 else "east", open_=False)
    # The nursery: hay to lie in, a trough of water, a chest and barrels, lanterns
    b.set(3, 1, 6, "hay_block", axis="y")
    b.set(4, 1, 6, "hay_block", axis="x")
    b.set(3, 2, 6, "hay_block", axis="z")
    b.set(11, 1, 6, "chest", facing="west", type="single", waterlogged=False)
    b.set(11, 1, 2, "barrel", facing="up", open=False)
    b.set(3, 1, 2, "barrel", facing="up", open=False)
    for x in (4, 10):
        lantern(b, x, 3, 4, hanging=True)
    for x in (3, 4, 5):
        b.set(x, 1, 3, "yellow_carpet")
    for x in (9, 10):
        b.set(x, 1, 3, "yellow_carpet")
    b.set(10, 1, 5, "yellow_carpet")


def paddock(b, z0, z1, gate_z):
    """A fenced paddock (fence round x 1-13, z0-z1) on grass round a Pasture Block, a trough and two bushes."""
    box(b, 2, 0, z0 + 1, 12, 0, z1 - 1, "grass_block", snowy=False)
    for x in range(1, 14):
        b.set(x, 0, z0, "coarse_dirt") if b.get(x, 0, z0) is None else None
        b.set(x, 0, z1, "coarse_dirt")
        fence(b, x, 1, z1, "dark_oak_fence")
        if b.get(x, 1, z0) is None:
            fence(b, x, 1, z0, "dark_oak_fence")
    for z in range(z0, z1 + 1):
        b.set(1, 0, z, "coarse_dirt")
        b.set(13, 0, z, "coarse_dirt")
        fence(b, 1, 1, z, "dark_oak_fence")
        fence(b, 13, 1, z, "dark_oak_fence")
    cz = (z0 + z1) // 2
    b.set(7, 0, cz, "stone_bricks")
    pasture(b, 7, 1, cz)
    for x in (10, 11):  # a trough
        b.set(x, 0, z1 - 1, "water", level=0)
    b.set(9, 0, z1 - 1, "stone_bricks")
    b.set(12, 0, z1 - 1, "stone_bricks")
    for x, z in ((3, z0 + 1), (12, z0 + 1), (2, z1 - 1)):
        b.set(x, 1, z, "azalea")
    for x, z, plant in ((4, z1 - 1, "dandelion"), (3, z1 - 1, "poppy"), (11, z0 + 2, "oxeye_daisy")):
        b.set(x, 1, z, plant)
    for x in (1, 13):
        lantern(b, x, 2, z1)


def daycare():
    """15 x 11 x 18: a barn with a straw-floored nursery and a fenced paddock behind round a Pasture Block."""
    b = Build(15, 11, 18)
    barn(b)
    paddock(b, 8, 16, 7)
    for x in (6, 7, 8):
        b.set(x, 0, 8, "coarse_dirt")
        b.clear(x, 1, 8, x, 1, 8)
    b.set(6, 1, 8, "dark_oak_fence_gate", facing="north", open=False, in_wall=False, powered=False)
    b.set(7, 1, 8, "dark_oak_fence_gate", facing="north", open=False, in_wall=False, powered=False)
    b.set(8, 1, 8, "dark_oak_fence_gate", facing="north", open=False, in_wall=False, powered=False)
    for x in (6, 7, 8):
        stairs(b, x, 0, 0, "cobblestone_stairs", "north")
    for x in (0, 14):
        lamp_post(b, x, 0, 0, post="dark_oak_fence", height=3)
    flower_bed(b, 3, 0, 4, 0, 0, ["poppy", "dandelion"])
    flower_bed(b, 10, 0, 11, 0, 0, ["dandelion", "poppy"])
    b.fill_air()
    return b


def hatchery(b):
    """A lean-to against the barn's east wall (x 13-17, z 2-6): open to the street between its posts, a door through
    from the barn, three nests of hay along the back wall (candles: eggs keeping warm), lanterns from a beam over them,
    a dark oak slab roof."""
    plinth(b, 13, 2, 17, 6, STONE_BASE, floor="hay_block")
    for x in range(13, 18):
        for y in (1, 2, 3):
            b.set(x, y, 6, BARN_WALL)
    for z in range(2, 7):
        for y in (1, 2, 3):
            b.set(17, y, z, BARN_WALL)
    for x in (13, 14, 15, 16):
        b.set(x, 3, 2, BARN_WALL)
    posts(b, [(17, 2), (17, 6), (13, 2)], 1, 3, BARN_FRAME)
    window(b, 17, 2, 4, "east", shutters="spruce_trapdoor", sill=SPRUCE)
    for x in range(13, 19):
        for z in range(1, 8):
            slab(b, x, 4, z, DARK_OAK)
    for x in range(13, 17):
        log(b, x, 3, 4, BEAM, axis="x")
    for x, candles in ((14, 3), (15, 2), (16, 1)):
        b.set(x, 1, 5, "hay_block", axis="y")
        b.set(x, 2, 5, "white_candle", candles=candles, lit=False, waterlogged=False)
    for x in (14, 16):
        lantern(b, x, 2, 4, hanging=True)
    b.set(16, 1, 3, "barrel", facing="up", open=False)
    b.set(12, 1, 3, "air")
    b.set(12, 2, 3, "air")
    door(b, 12, 1, 3, "spruce_door", "east")
    lamp_post(b, 18, 0, 1, post="dark_oak_fence", height=3)


def daycare_2():
    """Upgrade of the Daycare: a second paddock behind with its own Pasture Block and a hatchery on the barn's east side.
    19 x 11 x 27."""
    b = daycare().grow(19, 11, 27)
    b.clear(14, 1, 0, 14, 3, 0)  # the east lamp post moves to the hatchery's corner
    paddock(b, 16, 25, 16)
    for x in (6, 7, 8):
        b.set(x, 1, 16, "dark_oak_fence_gate", facing="north", open=False, in_wall=False, powered=False)
    hatchery(b)
    b.fill_air()
    return b


# Every Pokémon-job build: (blueprint id, draw). generate.py saves them; StarterBlueprints.java lists them.
JOB_BUILDS = [
    ("camp_kitchen", camp_kitchen),
    ("camp_kitchen_2", camp_kitchen_2),
    ("berry_nursery", berry_nursery),
    ("berry_nursery_2", berry_nursery_2),
    ("daycare", daycare),
    ("daycare_2", daycare_2),
]
