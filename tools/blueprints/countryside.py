"""
The country workplaces of ROADMAP 27.14, each the building the Steward builds for its workers
(steward_rules/workplace_*.json): Farmstead (Farmer), Fisher's Hut (Fisherman), Weaver's Cottage (Shepherd) and
Bandstand (Bard). Like the workshops of 27.13 each keeps only the job blocks of its trades (the Fisher's Hut II's smoker
is the fisherman's own: he smokes his catch in it), so a passing villager isn't handed another job.

Farmstead: I a farmhouse with one bed (cobbled lower course, oak frame, white plaster, a spruce roof with its gable to
the street, a stone chimney) beside a 9 x 5 field of farmland round a water channel, a scarecrow in the middle, and the
farmer's composter with a chest for the harvest on the path between them; II a barn behind the house (spruce boards in
a dark oak frame, a hay loft and a hoist) and a second field behind the first with a second composter.

Fisher's Hut: I a shore hut (oak boards in a spruce frame on a mossy stone footing under a slate roof) with a jetty
5 blocks out into the water on log posts and the fisherman's barrel inside, a net shed in a lean-to on the east; II a
stone smokehouse with a smoker behind, and a boat shed over the water beside the jetty. The jetty's deck is top slabs:
the builder puts no foundation under it (only under the posts, which go down to the bed), so its plot must be on a
shore with water no more than 3 deep under the jetty (city/Plots).

Weaver's Cottage: I a cottage of birch boards in a spruce frame under a dark oak roof, the loom (Shepherd) at the front
window, bales of dyed wool under the eaves, and a fenced sheep pen of grass with a hay rack, a trough and a shelter;
II a dye garden behind: beds of the flowers dyes come from, a path between them and a drying line of coloured wool.

Bandstand: an open eight-sided bandstand on a raised stone base, white birch posts and railings, a dark prismarine roof
rising to a lantern, and a jukebox in the middle (Bard). In the Gardens; it counts 3 for beauty (hall/Decorations).
"""
import zlib

from kit import *

# --- Farmstead (ROADMAP 27.14) ------------------------------------------------------------------------------------
FARM_PLINTH = Mix((5, "cobblestone"), (3, "stone"), (2, "mossy_cobblestone"), seed=81)
FARM_FRAME = "oak_log"
FARM_PLASTER = Mix((7, "white_concrete"), (1, "polished_diorite"), seed=82)
FARM_ROOF = BRICK
FARM_FLOOR = Mix((3, "spruce_planks"), (1, "oak_planks"), seed=83)
BARN_FRAME = "dark_oak_log"
BARN_BOARDS = Mix((5, "spruce_planks"), (1, "stripped_spruce_wood"), seed=84)
BARN_ROOF = DARK_OAK


def crop(x, z):
    """A young crop for (x, z): wheat on the west rows, carrots and potatoes on the east, a few days apart."""
    age = zlib.crc32(f"{x},{z}".encode()) % 4
    if x <= 10:
        return "wheat", age
    return ("carrots" if x == 12 else "potatoes"), age


def farm_field(b, z0, z1, scarecrow=True):
    """A 5 x 9 field (x 9-13, z0-z1) of farmland round a water channel down the middle (x 11), young wheat, carrots and
    potatoes on it, a log curb round its far side and ends (the path on x 8 is its near side), and a scarecrow (a fence
    post, a hay bale with fence arms, a carved pumpkin head) standing on a log across the channel."""
    mid = (z0 + z1) // 2
    for z in range(z0, z1 + 1):
        for x in range(9, 14):
            if x == 11:
                if scarecrow and z == mid:
                    log(b, x, 0, z, "oak_log", axis="x")
                else:
                    b.set(x, 0, z, "water", level=0)
            else:
                b.set(x, 0, z, "farmland", moisture=7)
                name, age = crop(x, z)
                b.set(x, 1, z, name, age=age)
    for x in range(9, 14):
        log(b, x, 0, z0 - 1, "oak_log", axis="x")
        log(b, x, 0, z1 + 1, "oak_log", axis="x")
    for z in range(z0 - 1, z1 + 2):
        log(b, 14, 0, z, "oak_log", axis="z")
    if scarecrow:
        fence(b, 11, 1, mid, "oak_fence")
        b.set(11, 2, mid, "hay_block", axis="y")
        fence(b, 10, 2, mid, "oak_fence")
        fence(b, 12, 2, mid, "oak_fence")
        b.set(11, 3, mid, "carved_pumpkin", facing="north")


def farmhouse(b):
    """The farmhouse (walls x 1-7, z 2-8): a cobbled lower course, white plaster in an oak frame (posts at 1, 3, 5, 7 on
    the front, the door in the middle), shuttered windows with flower boxes at the front, a spruce roof with its gable to
    the street, a stone chimney up the west side."""
    plinth(b, 1, 2, 7, 8, FARM_PLINTH, floor=FARM_FLOOR)
    for y in range(1, 4):
        for x in range(1, 8):
            for z in (2, 8):
                b.set(x, y, z, resolve(FARM_PLINTH if y == 1 else FARM_PLASTER, x, y, z))
        for z in range(3, 8):
            for x in (1, 7):
                b.set(x, y, z, resolve(FARM_PLINTH if y == 1 else FARM_PLASTER, x, y, z))
    posts(b, [(1, 2), (3, 2), (5, 2), (7, 2), (1, 8), (4, 8), (7, 8), (1, 5), (7, 5)], 1, 3, FARM_FRAME)
    beam_ring(b, 1, 2, 7, 8, 4, FARM_FRAME)
    door(b, 4, 1, 2, "spruce_door", "north")
    stairs(b, 4, 0, 1, COBBLE, "south")
    for x in (3, 4, 5):
        stairs(b, x, 3, 1, SPRUCE, "south", top=True)
    for x in (2, 6):
        window(b, x, 2, 2, "north", shutters="spruce_trapdoor", flowers=("spruce_trapdoor", ["potted_red_tulip" if x == 2 else "potted_oxeye_daisy"]))
    window(b, 1, 2, 3, "west", shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 7, 2, 3, "east", shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 7, 2, 7, "east", shutters="spruce_trapdoor", sill=SPRUCE)
    for x in (2, 6):
        window(b, x, 2, 8, "south", shutters="spruce_trapdoor", sill=SPRUCE)
    # Stones under the flower boxes (nobody walks in under one and sticks), lanterns on stones either side of the step
    for x in (2, 3, 5, 6):
        b.set(x, 0, 1, resolve(FARM_PLINTH, x, 0, 1))
    for x in (3, 5):
        lantern(b, x, 1, 1)


def farmhouse_roof(b):
    """A spruce roof with the gables front and back: plaster round a king post and a window either side of it."""
    gable_roof(b, 0, 8, 1, 9, 5, FARM_ROOF, axis="z", gable=FARM_PLASTER, gable_at=(2, 8), eave_trim=SPRUCE)
    farmhouse_gables(b)


def farmhouse_gables(b):
    for z in (2, 8):
        for y in range(5, 9):
            log(b, 4, y, z, FARM_FRAME)
        for x in (3, 5):
            pane(b, x, 6, z)
        for x in (2, 6):
            log(b, x, 5, z, FARM_FRAME)


def farmhouse_chimney(b):
    """A stone chimney up the west wall, outside, with a campfire smoking on top."""
    for z in (5, 6, 7):
        for y in range(0, 3):
            b.set(0, y, z, resolve(FARM_PLINTH, 0, y, z))
    stairs(b, 0, 3, 5, COBBLE, "south")
    stairs(b, 0, 3, 7, COBBLE, "north")
    for y in range(3, 10):
        b.set(0, y, 6, resolve(FARM_PLINTH, 0, y, 6))
    b.set(0, 10, 6, "campfire", facing="north", lit=True, signal_fire=False, waterlogged=False)


def farmhouse_inside(b):
    """One bed, a chest at its foot, a table and stools by the hearth, a lantern from the tie beam."""
    b.bed(6, 1, 6, "red", "south")
    b.set(6, 1, 3, "chest", facing="west", type="single", waterlogged=False)
    fence(b, 3, 1, 4, "spruce_fence")
    b.set(3, 2, 4, "spruce_pressure_plate", powered=False)
    stairs(b, 3, 1, 5, SPRUCE, "north")
    stairs(b, 2, 1, 4, SPRUCE, "east")
    b.set(2, 1, 6, "brown_carpet")
    b.set(2, 1, 7, "brown_carpet")
    for z in range(3, 8):
        log(b, 4, 4, z, FARM_FRAME, axis="z")
    lantern(b, 4, 3, 5, hanging=True)


def farm_yard(b, z0, z1, composter_z, chest_z):
    """The path between the house and the field (x 8, dirt path), with the farmer's composter and a chest for the
    harvest beside it, and hay by the house wall."""
    for z in range(z0, z1 + 1):
        b.set(8, 0, z, "dirt_path")
    b.set(8, 1, composter_z, "composter", level=0)
    b.set(8, 1, chest_z, "chest", facing="east", type="single", waterlogged=False)


def farmstead():
    """15 x 12 x 13: a farmhouse with one bed (white plaster in an oak frame on a cobbled course, a spruce roof with its
    gable to the street, a stone chimney) beside a 9 x 5 field of farmland round a water channel with a scarecrow, and on
    the path between them the farmer's composter with a chest for the harvest."""
    b = Build(15, 12, 13)
    farmhouse(b)
    farmhouse_roof(b)
    farmhouse_chimney(b)
    farmhouse_inside(b)
    farm_field(b, 2, 10)
    farm_yard(b, 0, 12, 4, 6)
    b.set(8, 1, 8, "hay_block", axis="y")
    b.set(8, 2, 8, "hay_block", axis="x")
    b.set(8, 1, 9, "hay_block", axis="z")
    lamp_post(b, 14, 1, 0, height=2)
    b.set(14, 0, 0, "oak_log", axis="y")
    b.fill_air()
    return b


def barn(b):
    """The barn behind the house (walls x 1-7, z 12-20, five high): spruce boards in a dark oak frame on a stone footing,
    a wide doorway onto the path, a hay loft over the back half with a loft door and a hoist beam in the back gable."""
    plinth(b, 1, 12, 7, 20, FARM_PLINTH, floor="coarse_dirt")
    for y in range(1, 6):
        for x in range(1, 8):
            for z in (12, 20):
                b.set(x, y, z, resolve(BARN_BOARDS, x, y, z))
        for z in range(13, 20):
            for x in (1, 7):
                b.set(x, y, z, resolve(BARN_BOARDS, x, y, z))
    posts(b, [(1, 12), (4, 12), (7, 12), (1, 20), (4, 20), (7, 20), (1, 16), (7, 14), (7, 18)], 1, 5, BARN_FRAME)
    beam_ring(b, 1, 12, 7, 20, 6, BARN_FRAME)
    # The wide doorway onto the path (x 7, z 15-17) under a dark oak lintel, with open board doors folded back
    for z in (15, 16, 17):
        for y in (1, 2, 3):
            b.set(7, y, z, "air")
        log(b, 7, 4, z, BARN_FRAME, axis="z")
    trapdoor(b, 8, 1, 14, "spruce_trapdoor", "east", open_=True)
    trapdoor(b, 8, 2, 14, "spruce_trapdoor", "east", open_=True)
    trapdoor(b, 8, 1, 18, "spruce_trapdoor", "east", open_=True)
    trapdoor(b, 8, 2, 18, "spruce_trapdoor", "east", open_=True)
    lantern(b, 6, 3, 16, hanging=True)
    # Little windows in the side walls
    window(b, 1, 2, 14, "west", shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 1, 2, 18, "west", shutters="spruce_trapdoor", sill=SPRUCE)
    # The loft over the back half, its edge on a beam, a ladder up
    for x in range(2, 7):
        for z in range(17, 20):
            b.set(x, 4, z, "spruce_planks")
    for x in range(2, 7):
        log(b, x, 4, 16, BARN_FRAME, axis="x")
    for y in range(1, 5):
        b.set(6, y, 13, "ladder", facing="south", waterlogged=False)
    for x, z in ((3, 18), (4, 18), (5, 19), (3, 19), (2, 19)):
        b.set(x, 5, z, "hay_block", axis="x" if z == 18 else "z")
    # Stalls below: fences, hay and a cart of feed
    for x in (2, 3, 5, 6):
        fence(b, x, 1, 17, "spruce_fence")
    for x in (2, 6):
        b.set(x, 1, 19, "hay_block", axis="y")
    b.set(3, 1, 19, "hay_block", axis="x")
    lantern(b, 4, 3, 14, hanging=True)
    for x in range(2, 7):
        log(b, x, 4, 14, BARN_FRAME, axis="x")


def barn_roof(b):
    """A dark oak roof over the barn, its gables front and back: spruce boards, a loft door and a hoist beam out of the
    back one."""
    gable_roof(b, 0, 8, 11, 21, 6, BARN_ROOF, axis="z", gable=BARN_BOARDS, gable_at=(12, 20), eave_trim=SPRUCE)
    for z in (12, 20):
        for y in range(6, 10):
            log(b, 4, y, z, BARN_FRAME)
    # the loft door in the back gable (two open trapdoors), the hoist beam over it with a chain and a bale
    for y in (7, 8):
        b.set(3, y, 20, "air")
        b.set(5, y, 20, "air")
    pane(b, 3, 7, 12)
    pane(b, 5, 7, 12)
    b.set(3, 7, 20, "spruce_trapdoor", facing="south", half="bottom", open=True, powered=False, waterlogged=False)
    b.set(5, 7, 20, "spruce_trapdoor", facing="south", half="bottom", open=True, powered=False, waterlogged=False)
    b.set(3, 8, 20, "spruce_trapdoor", facing="south", half="bottom", open=True, powered=False, waterlogged=False)
    b.set(5, 8, 20, "spruce_trapdoor", facing="south", half="bottom", open=True, powered=False, waterlogged=False)
    log(b, 4, 10, 21, BARN_FRAME, axis="z")
    b.set(4, 9, 21, "chain", axis="y", waterlogged=False)
    b.set(4, 8, 21, "hay_block", axis="y")


def farmstead_2():
    """15 x 12 x 22: the farmstead with a barn behind the house (spruce boards in a dark oak frame, a hay loft and a
    hoist in the back gable) and a second field behind the first, its own composter and chest by the barn door for a
    second farmer."""
    b = farmstead().grow(15, 12, 22)
    farm_field(b, 12, 20, scarecrow=False)
    farm_yard(b, 13, 21, 13, 19)
    barn(b)
    barn_roof(b)
    b.fill_air()
    return b


# --- Fisher's Hut (ROADMAP 27.14) ---------------------------------------------------------------------------------
# The jetty runs out from the front (z 0-4) to the hut's door; the hut stands on the shore behind it (z 5-11). The deck
# is top slabs (no foundation under them, BuildPlan) on spruce log posts the builder takes down to the bed.
HUT_PLINTH = Mix((5, "cobblestone"), (3, "mossy_cobblestone"), (2, "stone"), seed=91)
HUT_FRAME = "spruce_log"
HUT_BOARDS = Mix((6, "oak_planks"), (1, "stripped_oak_wood"), seed=92)
HUT_ROOF = DEEPSLATE_TILE
HUT_FLOOR = Mix((3, "spruce_planks"), (1, "oak_planks"), seed=93)
JETTY = 5


def jetty(b, x0=4, x1=6):
    """The jetty (x 4-6, z 0-4): a deck of top slabs on pairs of log posts (z 1 and 3, within a builder's reach from the
    shore), the outer posts standing up as bollards (one with a lantern), the end open to fish from, a rail of fences
    along both sides back to the hut, lanterns either side of its door."""
    for x in range(x0, x1 + 1):
        for z in range(0, JETTY):
            slab(b, x, 0, z, SPRUCE, top=True)
    for z in (1, 3):
        for x in (x0, x1):
            log(b, x, 0, z, "spruce_log")
    for x in (x0, x1):
        log(b, x, 1, 1, "spruce_log")
        for z in range(2, JETTY):
            fence(b, x, 1, z, "spruce_fence")
    lantern(b, x1, 2, 1)
    lantern(b, x0, 2, 4)
    lantern(b, x1, 2, 4)


def fishers_hut_walls(b):
    """The hut (walls x 1-9, z 5-11): a mossy stone course, oak boards above in a spruce frame (posts at 1, 4, 6, 9 on
    the front and back, the door in the middle onto the jetty), shuttered windows two wide."""
    plinth(b, 1, 5, 9, 11, HUT_PLINTH, floor=HUT_FLOOR)
    for y in range(1, 5):
        for x in range(1, 10):
            for z in (5, 11):
                b.set(x, y, z, resolve(HUT_PLINTH if y == 1 else HUT_BOARDS, x, y, z))
        for z in range(6, 11):
            for x in (1, 9):
                b.set(x, y, z, resolve(HUT_PLINTH if y == 1 else HUT_BOARDS, x, y, z))
    posts(b, [(1, 5), (4, 5), (6, 5), (9, 5), (1, 11), (4, 11), (6, 11), (9, 11), (9, 8)], 1, 4, HUT_FRAME)
    beam_ring(b, 1, 5, 9, 11, 5, HUT_FRAME)
    door(b, 5, 1, 5, "spruce_door", "north")
    for x in (2, 7):
        window(b, x, 2, 5, "north", width=2, height=2, shutters="spruce_trapdoor", sill=SPRUCE)
        # (a window looking south runs west from its x: the east cell of each bay, so no shutter swings out by the door; a lintel
        # over it, no sill: the way round the back to the door, under the eaves, stays clear at head height)
        window(b, x + 1, 2, 11, "south", width=2, height=2, shutters="spruce_trapdoor", lintel=SPRUCE)
    # The back door, to the land (the front one is onto the jetty)
    door(b, 5, 1, 11, "spruce_door", "south")
    stairs(b, 5, 0, 12, MOSSY_COBBLE, "north")
    b.set(6, 0, 12, resolve(HUT_PLINTH, 6, 0, 12))
    lantern(b, 6, 1, 12)
    window(b, 1, 2, 6, "west", height=2, shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 1, 2, 10, "west", height=2, shutters="spruce_trapdoor", sill=SPRUCE)
    # A hood over the door on brackets
    for x in (4, 5, 6):
        stairs(b, x, 4, 4, SPRUCE, "south", top=True)


def fishers_hut_roof(b):
    """A slate roof, the ridge along the hut, oak gables east and west round a king post with a window either side."""
    gable_roof(b, 0, 10, 4, 12, 6, HUT_ROOF, axis="x", gable=HUT_BOARDS, gable_at=(1, 9), eave_trim=SPRUCE)
    fishers_hut_gables(b)


def fishers_hut_gables(b):
    for x in (1, 9):
        for y in range(6, 10):
            log(b, x, y, 8, HUT_FRAME)
        if x == 9:
            pane(b, x, 7, 7)
            pane(b, x, 7, 9)
    for x in range(2, 9):
        log(b, x, 5, 8, HUT_FRAME, axis="x")


def fishers_hut_chimney(b):
    """A stone chimney up the west gable, smoking."""
    for z in (7, 8, 9):
        for y in range(0, 3):
            b.set(0, y, z, resolve(HUT_PLINTH, 0, y, z))
    stairs(b, 0, 3, 7, MOSSY_COBBLE, "south")
    stairs(b, 0, 3, 9, MOSSY_COBBLE, "north")
    for y in range(3, 12):
        b.set(0, y, 8, resolve(HUT_PLINTH, 0, y, 8))
    b.set(0, 12, 8, "campfire", facing="north", lit=True, signal_fire=False, waterlogged=False)


def fishers_hut_inside(b):
    """The fisherman's barrel by the back window, a chest beside it, a bench and a table, a rug, a lantern from the tie
    beam."""
    b.set(2, 1, 10, "barrel", facing="up", open=False)
    b.set(3, 1, 10, "chest", facing="north", type="single", waterlogged=False)
    for z in (7, 8):
        stairs(b, 2, 1, z, SPRUCE, "west")
    fence(b, 3, 1, 8, "spruce_fence")
    b.set(3, 2, 8, "spruce_pressure_plate", powered=False)
    for x in (5, 6, 7):
        b.set(x, 1, 8, "blue_carpet" if x == 6 else "white_carpet")
    b.set(8, 1, 10, "dried_kelp_block")
    b.set(8, 2, 10, "potted_fern")
    lantern(b, 5, 4, 8, hanging=True)


def net_shed(b):
    """A lean-to on the east gable (x 10-12, z 6-10): posts and a beam, a slate roof sloping down from the gable, nets
    (chains) hung over drying kelp, and a crate of the catch."""
    for x in (10, 11):
        for z in range(6, 11):
            b.set(x, 0, z, resolve(HUT_PLINTH, x, 0, z) if x == 11 and z in (6, 10) else "spruce_planks")
    posts(b, [(11, 6), (11, 10)], 1, 4, HUT_FRAME)
    for z in range(6, 11):
        log(b, 11, 5, z, HUT_FRAME, axis="z")
    for z in range(5, 12):
        stairs(b, 10, 7, z, HUT_ROOF, "west")
        stairs(b, 11, 6, z, HUT_ROOF, "west")
        stairs(b, 12, 5, z, HUT_ROOF, "west")
    for z in (7, 9):
        b.set(11, 4, z, "chain", axis="y", waterlogged=False)
        b.set(11, 3, z, "chain", axis="y", waterlogged=False)
        b.set(11, 2, z, "chain", axis="y", waterlogged=False)
    b.set(11, 1, 7, "dried_kelp_block")
    b.set(11, 1, 9, "hay_block", axis="z")
    b.set(10, 1, 10, "dried_kelp_block")
    b.set(10, 2, 10, "dried_kelp_block")
    b.set(10, 1, 6, "dried_kelp_block")


def fishers_hut():
    """13 x 13 x 13: a shore hut (oak boards in a spruce frame on a mossy stone course under a slate roof, a stone
    chimney up the west gable) with a jetty 5 blocks out in front on log posts, the fisherman's barrel inside, and a net
    shed in a lean-to on the east gable. Its plot is on a shore: water within 4 blocks of its front and no more than 3
    deep under the jetty."""
    b = Build(13, 13, 13)
    jetty(b)
    fishers_hut_walls(b)
    fishers_hut_roof(b)
    fishers_hut_chimney(b)
    fishers_hut_inside(b)
    net_shed(b)
    b.fill_air()
    return b


SMOKEHOUSE_STONE = Mix((5, "stone_bricks"), (3, "cobblestone"), (2, "mossy_stone_bricks"), seed=94)


def smokehouse(b):
    """The smokehouse behind the hut (walls x 3-7, z 13-17, four high), through a door in the hut's back wall and across
    a step under the eaves: stone walls, the smoker (the fisherman smokes his catch in it) in a brick breast on the back
    wall with the flue up through the roof, racks of drying kelp on chains, a chest of salt."""
    plinth(b, 3, 13, 7, 17, SMOKEHOUSE_STONE, floor="stone_bricks")
    walls(b, 3, 13, 7, 17, 1, 4, SMOKEHOUSE_STONE)
    posts(b, [(3, 13), (7, 13), (3, 17), (7, 17)], 1, 4, HUT_FRAME)
    beam_ring(b, 3, 13, 7, 17, 5, HUT_FRAME)
    # Out of the hut's back door, a step under the eaves, the smokehouse door
    b.set(5, 0, 12, resolve(HUT_PLINTH, 5, 0, 12))
    door(b, 5, 1, 13, "spruce_door", "north")
    for z in (15,):
        window(b, 3, 2, z, "west", shutters="spruce_trapdoor", sill=STONE_BRICK)
        window(b, 7, 2, z, "east", shutters="spruce_trapdoor", sill=STONE_BRICK)
    # The smoker in a brick breast, the flue up the back wall to a campfire
    for y in range(1, 11):
        b.set(5, y, 17, "bricks")
    b.set(4, 1, 16, "bricks")
    b.set(6, 1, 16, "bricks")
    b.set(5, 1, 16, "smoker", facing="north", lit=False)
    wall_block(b, 5, 2, 16, "brick_wall")
    stairs(b, 5, 3, 16, BRICK, "south", top=True)
    b.set(5, 11, 17, "campfire", facing="north", lit=True, signal_fire=False, waterlogged=False)
    # Drying racks: kelp on chains from a beam, a chest of salt
    for x in range(4, 7):
        log(b, x, 4, 14, HUT_FRAME, axis="x")
    for x in (4, 6):
        b.set(x, 3, 14, "chain", axis="y", waterlogged=False)
        b.set(x, 2, 14, "dried_kelp_block")
    b.set(4, 1, 14, "chest", facing="east", type="single", waterlogged=False)


def boat_shed(b):
    """The boat shed over the water east of the jetty (x 8-12, z 0-3): log posts down to the bed, walkways of top slabs
    along both sides of an open slip, a slate roof with its open gable to the water, a lantern from the ridge, and a way
    across from the jetty."""
    for z in range(0, 5):
        for x in (8, 12):
            slab(b, x, 0, z, SPRUCE, top=True)
    for x, z in ((8, 0), (12, 0), (8, 3), (12, 3)):
        posts(b, [(x, z)], 0, 3, HUT_FRAME)
    beam_ring(b, 8, 0, 12, 3, 4, HUT_FRAME)
    gable_roof(b, 7, 13, 0, 4, 4, HUT_ROOF, axis="z", gable="spruce_planks", gable_at=(0, 3), eave_trim=SPRUCE)
    for z in (0, 3):
        b.set(10, 5, z, "air")
    for z in range(0, 4):
        log(b, 10, 6, z, HUT_FRAME, axis="z")
    lantern(b, 10, 5, 1, hanging=True)
    # Kelp bales on the walkway
    b.set(12, 1, 2, "dried_kelp_block")
    # The way across from the jetty: through its rail at z 3 onto a slab and the shed's walkway
    b.set(6, 1, 3, "air")
    slab(b, 7, 0, 3, SPRUCE, top=True)


def fishers_hut_2():
    """14 x 13 x 19: the hut with a stone smokehouse behind (a smoker in a brick breast for the fisherman's catch, its flue
    smoking over the roof, kelp drying on racks) and a boat shed over the water beside the jetty (an open slip between
    walkways under a slate roof)."""
    b = fishers_hut().grow(14, 13, 19)
    smokehouse(b)
    boat_shed(b)
    roofs(b,
          lambda t: gable_roof(t, 0, 10, 4, 12, 6, HUT_ROOF, axis="x", gable=HUT_BOARDS, gable_at=(1, 9), eave_trim=SPRUCE),
          lambda t: gable_roof(t, 2, 8, 12, 18, 5, HUT_ROOF, axis="x", gable=SMOKEHOUSE_STONE, gable_at=(3, 7), eave_trim=SPRUCE))
    fishers_hut_gables(b)
    net_shed(b)
    for y in range(1, 11):
        b.set(5, y, 17, "bricks")
    b.set(5, 11, 17, "campfire", facing="north", lit=True, signal_fire=False, waterlogged=False)
    b.fill_air()
    return b


# --- Weaver's Cottage (ROADMAP 27.14) -----------------------------------------------------------------------------
WEAVER_PLINTH = Mix((5, "stone_bricks"), (3, "cobblestone"), (2, "andesite"), seed=101)
WEAVER_FRAME = "spruce_log"
WEAVER_BOARDS = Mix((7, "birch_planks"), (1, "stripped_birch_wood"), seed=102)
WEAVER_ROOF = DARK_OAK
WOOL = ("white_wool", "light_blue_wool", "yellow_wool", "red_wool", "lime_wool", "purple_wool")


def weavers_cottage_walls(b):
    """The cottage (walls x 1-7, z 2-8): a stone course, birch boards in a spruce frame (posts at 1, 3, 5, 7 on the front,
    the door in the middle), shuttered windows with flower boxes at the front."""
    plinth(b, 1, 2, 7, 8, WEAVER_PLINTH, floor=Mix((3, "spruce_planks"), (1, "oak_planks"), seed=103))
    for y in range(1, 5):
        for x in range(1, 8):
            for z in (2, 8):
                b.set(x, y, z, resolve(WEAVER_PLINTH if y == 1 else WEAVER_BOARDS, x, y, z))
        for z in range(3, 8):
            for x in (1, 7):
                b.set(x, y, z, resolve(WEAVER_PLINTH if y == 1 else WEAVER_BOARDS, x, y, z))
    posts(b, [(1, 2), (3, 2), (5, 2), (7, 2), (1, 8), (4, 8), (7, 8), (1, 5), (7, 5)], 1, 4, WEAVER_FRAME)
    beam_ring(b, 1, 2, 7, 8, 5, WEAVER_FRAME)
    door(b, 4, 1, 2, "spruce_door", "north")
    for x in (2, 6):
        window(b, x, 2, 2, "north", height=2, shutters="spruce_trapdoor",
               flowers=("spruce_trapdoor", ["potted_cornflower" if x == 2 else "potted_dandelion"]))
        b.set(x, 0, 1, resolve(WEAVER_PLINTH, x, 0, 1))  # under the flower box: nobody walks in under it and sticks
    window(b, 1, 2, 4, "west", height=2, shutters="spruce_trapdoor", sill=SPRUCE)
    for z in (4, 6):
        window(b, 7, 2, z, "east", height=2, shutters="spruce_trapdoor", sill=SPRUCE)
    for x in (2, 6):
        window(b, x, 2, 8, "south", height=2, shutters="spruce_trapdoor", sill=SPRUCE)


def weavers_porch(b):
    """A pent hood over the door on two fence posts, a lantern under it, a step."""
    for x in (3, 5):
        fence(b, x, 1, 0, "spruce_fence")
        fence(b, x, 2, 0, "spruce_fence")
    for x in (3, 4, 5):
        stairs(b, x, 3, 0, WEAVER_ROOF, "south")
        stairs(b, x, 4, 1, WEAVER_ROOF, "south")
    stairs(b, 4, 0, 0, STONE_BRICK, "south")
    b.set(4, 0, 1, resolve(WEAVER_PLINTH, 4, 0, 1))
    lantern(b, 4, 3, 1, hanging=True)


def weavers_roofs(b):
    """The main roof: dark oak, its ridge along the front, eaves to the street, birch gables round a king post."""
    gable_roof(b, 0, 8, 1, 9, 6, WEAVER_ROOF, axis="x", gable=WEAVER_BOARDS, gable_at=(1, 7), eave_trim=SPRUCE)
    # A dormer on the front slope over the door: log cheeks, a two-high window, its own little roof running back
    for y in (6, 7):
        log(b, 3, y, 2, WEAVER_FRAME)
        log(b, 5, y, 2, WEAVER_FRAME)
        pane(b, 4, y, 2)
    for z in (1, 2, 3):
        stairs(b, 3, 8, z, WEAVER_ROOF, "east")
        stairs(b, 5, 8, z, WEAVER_ROOF, "west")
    b.set(4, 8, 2, "birch_planks")
    for z in (1, 2, 3, 4):
        slab(b, 4, 9, z, WEAVER_ROOF)
    for x in (1, 7):
        for y in range(6, 9):
            log(b, x, y, 5, WEAVER_FRAME)
        pane(b, x, 7, 4)
        pane(b, x, 7, 6)


def weavers_cottage_inside(b):
    """The loom at the front window (the shepherd's), bales of wool along the wall, a chest of dyes, a bench, a lantern."""
    b.set(2, 1, 3, "loom", facing="east")
    b.set(6, 1, 3, "chest", facing="west", type="single", waterlogged=False)
    for i, z in enumerate((5, 6, 7)):
        b.set(2, 1, z, WOOL[i])
        if z != 6:
            b.set(2, 2, z, WOOL[i + 3])
    stairs(b, 6, 1, 6, SPRUCE, "east")
    stairs(b, 6, 1, 7, SPRUCE, "east")
    b.set(4, 1, 5, "light_blue_carpet")
    b.set(4, 1, 6, "white_carpet")
    for z in range(3, 8):
        log(b, 4, 5, z, WEAVER_FRAME, axis="z")
    lantern(b, 4, 4, 6, hanging=True)


def sheep_pen(b):
    """The sheep pen east of the cottage (x 9-14, z 2-8): grass inside an oak fence with a gate to the street, a trough, a
    hay rack, and a lean-to shelter in the back corner; lanterns on the gate posts."""
    for x in range(9, 15):
        for z in range(2, 9):
            b.set(x, 0, z, "grass_block", snowy=False)
    for x in range(9, 15):
        for z in range(2, 9):
            if x in (9, 14) or z in (2, 8):
                fence(b, x, 1, z, "oak_fence")
    b.set(11, 1, 2, "oak_fence_gate", facing="north", open=False, powered=False, in_wall=False)
    for x in (10, 12):
        log(b, x, 1, 2, "oak_log")
        lantern(b, x, 2, 2)
    # the trough: water in a stone curb
    for x in (10, 11):
        b.set(x, 0, 7, "water", level=0)
    # a hay rack against the cottage side
    b.set(10, 1, 4, "hay_block", axis="y")
    b.set(10, 1, 5, "hay_block", axis="z")
    # the shelter in the back corner: a lean-to of dark oak on posts against the back fence
    for x in (12, 14):
        posts(b, [(x, 8)], 1, 3, "oak_log")
        fence(b, x, 1, 7, "oak_fence")
        fence(b, x, 2, 7, "oak_fence")
    for x in range(11, 15):
        stairs(b, x, 3, 7, DARK_OAK, "south")
        stairs(b, x, 4, 8, DARK_OAK, "south")
    b.set(13, 1, 8, "hay_block", axis="x")


def weavers_cottage():
    """15 x 11 x 11: a cottage of birch boards in a spruce frame under a dark oak roof, a porch over the door, the loom
    (Shepherd) at the front window and bales of dyed wool inside and out, and a fenced sheep pen of grass with a gate, a
    trough, a hay rack and a shelter."""
    b = Build(15, 11, 11)
    weavers_cottage_walls(b)
    weavers_porch(b)
    weavers_roofs(b)
    weavers_cottage_inside(b)
    sheep_pen(b)
    # Wool bales under the west eaves
    for i, (z, y) in enumerate(((3, 1), (4, 1), (3, 2))):
        b.set(0, y, z, WOOL[i + 1])
    b.fill_air()
    return b


def dye_garden(b):
    """The dye garden behind the cottage (z 10-14): three beds of the flowers dyes come from (red, yellow and blue; purple,
    white and orange), coarse paths between them, and a drying frame at the back with dyed cloth (banners) hung on it."""
    for x in range(0, 15):
        b.set(x, 0, 10, "coarse_dirt")
        b.set(x, 0, 14, "coarse_dirt")
    for x in (4, 9):
        for z in range(11, 14):
            b.set(x, 0, z, "coarse_dirt")
    beds = ((1, 3, ("poppy", "red_tulip", "dandelion")), (5, 8, ("cornflower", "blue_orchid", "allium")),
            (10, 13, ("lily_of_the_valley", "oxeye_daisy", "orange_tulip")))
    for x0, x1, plants in beds:
        for x in range(x0, x1 + 1):
            for z in range(11, 14):
                b.set(x, 0, z, "grass_block", snowy=False)
                b.set(x, 1, z, plants[(x - x0 + z) % len(plants)])
    # The drying frame: two posts, a beam, cloth hung from it
    for x in (2, 12):
        posts(b, [(x, 14)], 1, 3, "spruce_log")
    for x in range(3, 12):
        log(b, x, 3, 14, "spruce_log", axis="x")
    for x, colour in ((4, "red"), (5, "yellow"), (7, "blue"), (8, "purple"), (10, "orange")):
        b.set(x, 2, 15, f"{colour}_wall_banner", facing="south")
    lantern(b, 2, 4, 14)
    lantern(b, 12, 4, 14)


def weavers_cottage_2():
    """15 x 11 x 16: the cottage with a dye garden behind: beds of poppies, dandelions, cornflowers, orchids, alliums,
    lilies and tulips between coarse paths, and a drying frame of dyed cloth along the back."""
    b = weavers_cottage().grow(15, 11, 16)
    dye_garden(b)
    b.fill_air()
    return b


# --- Bandstand (ROADMAP 27.14) ------------------------------------------------------------------------------------
BAND_BASE = Mix((6, "stone_bricks"), (2, "polished_andesite"), (1, "cracked_stone_bricks"), seed=111)
BAND_POST = "stripped_birch_log"
BAND_ROOF = Family("dark_prismarine", "dark_prismarine_stairs", "dark_prismarine_slab")
BAND_C = 5  # the centre


def octagon(r):
    """The cells (dx, dz) of an eight-sided shape of 'radius' r round the centre: a square with its corners cut at 45°."""
    if r <= 0:
        return {(0, 0)}
    return {(dx, dz) for dx in range(-r, r + 1) for dz in range(-r, r + 1) if abs(dx) + abs(dz) <= r + r // 2}


def octagon_edge(r):
    """The outer ring of {@link octagon}: its cells with a neighbour (sides or corners) outside it."""
    cells = octagon(r)
    return {(dx, dz) for dx, dz in cells
            if any((dx + i, dz + k) not in cells for i in (-1, 0, 1) for k in (-1, 0, 1))}


def inward(cells, dx, dz):
    """Which way a stair at (dx, dz) on the rim of {@code cells} rises: away from the side that is outside (on a corner of
    the cut, towards the centre along the longer axis; the finish works out the corner shapes)."""
    out = [d for d, (i, k) in (("west", (-1, 0)), ("east", (1, 0)), ("north", (0, -1)), ("south", (0, 1)))
           if (dx + i, dz + k) not in cells]
    if len(out) == 1:
        return OPP[out[0]]
    if abs(dz) > abs(dx) or abs(dz) == abs(dx) and dz != 0:
        return "south" if dz < 0 else "north"
    return "east" if dx < 0 else "west"


def octagon_roof(b, c, y, r_out, r_top, fam):
    """An eight-sided roof over the centre (c, c): a ring of stairs per layer from the octagon of r_out at y up to r_top,
    each ring the octagon of its layer less the one above (with the notches at its cut corners filled, so no gap shows
    between stairs that meet only at an edge), and the octagon of r_top - 1 capped with slabs."""
    for k, r in enumerate(range(r_out, r_top - 1, -1)):
        cells = octagon(r)
        ring = cells - octagon(r - 1)
        if k > 0:
            # the notches of the cut corners, where two stairs of the ring meet only at an edge, filled from the ring below
            notches = {(dx, dz) for dx, dz in octagon(r + 1) - cells
                       if sum((dx + i, dz + j) in ring for i, j in ((1, 0), (-1, 0), (0, 1), (0, -1))) >= 2}
            cells = cells | notches
            ring = ring | notches
        for dx, dz in ring:
            stairs(b, c + dx, y + k, c + dz, fam, inward(cells, dx, dz))
    for dx, dz in octagon(r_top - 1):
        slab(b, c + dx, y + r_out - r_top, c + dz, fam)


def bandstand():
    """11 x 10 x 11: an open eight-sided bandstand on a raised stone base with a flared foot and steps at the front, lit
    pedestals either side; eight white birch posts with birch railings (open at the front), a dark prismarine roof in
    rings up to a cap, a lantern hanging in the middle over the jukebox (the bard's) and two note blocks for the band."""
    b = Build(11, 10, 11)
    c = BAND_C
    # The base: a flared foot of stairs, two courses of stone, a plank floor
    for dx, dz in octagon(5) - octagon(4):
        stairs(b, c + dx, 0, c + dz, STONE_BRICK, inward(octagon(5), dx, dz))
    for dx, dz in octagon(4):
        b.set(c + dx, 0, c + dz, resolve(BAND_BASE, c + dx, 0, c + dz))
    edge = octagon_edge(4)
    for dx, dz in octagon(4):
        if (dx, dz) in edge:
            b.set(c + dx, 1, c + dz, resolve(BAND_BASE, c + dx, 1, c + dz))
        else:
            b.set(c + dx, 1, c + dz, "spruce_planks" if (dx + dz) % 2 else "birch_planks")
    # Steps up at the front, a lit pedestal either side
    for x in (4, 5, 6):
        stairs(b, x, 0, 0, STONE_BRICK, "south")
        stairs(b, x, 1, 1, STONE_BRICK, "south")
    for x in (3, 7):
        b.set(x, 0, 0, "stone_bricks")
        lantern(b, x, 1, 0)
    # Eight posts at the corners, railings between them but for the way in at the front
    corners = {(4, 2), (4, -2), (-4, 2), (-4, -2), (2, 4), (-2, 4), (2, -4), (-2, -4)}
    for dx, dz in edge:
        if (dx, dz) in corners:
            posts(b, [(c + dx, c + dz)], 2, 4, BAND_POST)
        elif not (dz == -4 and abs(dx) < 2):
            fence(b, c + dx, 2, c + dz, "birch_fence")
    # The ring beam over the posts, the roof in rings up to a cap
    for dx, dz in edge:
        b.set(c + dx, 5, c + dz, "birch_planks")
    octagon_roof(b, c, 5, 5, 2, BAND_ROOF)
    lantern(b, c, 7, c, hanging=True)
    # The band: the jukebox in the middle, note blocks either side
    b.set(c, 2, c, "jukebox", has_record=False)
    for x in (c - 2, c + 2):
        b.set(x, 2, c + 1, "note_block", instrument="basedrum" if x < c else "harp", note=0, powered=False)
    b.fill_air()
    return b
