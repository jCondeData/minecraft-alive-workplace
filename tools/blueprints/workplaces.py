"""
The twelve buildable workplaces the Steward asks for (ROADMAP 27.11), each with its own look (ROADMAP 26.11: the owner
found the Guard House, Kitchen, Clinic, Builder's and Carpenter's Workshops and Ball Workshop all the same house with a
different room inside). Each tells its trade from the street: its own footprint, roof and signature.

    builders_workshop    a lean-to drawing studio under a low slab roof, and a fenced yard with a timber crane
    carpenters_workshop  a long saw shed, half closed workshop, half open trestles, log piles front and back
    kitchen              a plastered cookhouse under a hipped roof, a brick bread oven bulging out of its side, herb beds
    post_office          a narrow two-storey house, its upper floor jettied over the street, a dovecote on the ridge
    guard_house          a squat stone blockhouse with a battlemented flat roof and a timber watch hut on it
    clinic               a white pavilion with a long ward wing across the back, cross-mullioned windows, a ward of beds inside
    ferry_house          a boathouse under a gambrel roof, a landing stage at the front and a light on a mast
    trainers_house       a long low dojo with a deep veranda and a fenced sparring ring before it
    leaders_hall         a stone hall with a columned portico and a pediment, steps the whole width of the front
    ball_workshop        a round apricorn-drying tower under a copper cone, a low workshop wing beside it
    trade_hall           a market hall: an upper storey on a stone arcade, the trading floor open beneath
    school               a U-shaped brick school round a little courtyard with the tutor's post in it

Rules they keep (the tests check most): the front is z = 0 with the door and a step on it; exactly one of the job's
block; no other vanilla job block (a barrel, lectern, composter...) that would take a villager for a vanilla job; no
trapdoor in the front walk row (z = 1, y 0-1: B69); a bed for the worker; every upper floor reached by a ladder (B149);
no container a builder would have to fill. The sizes are in StarterBlueprints.java.
"""
from kit import *


def chair_at(b, x, y, z, fam, toward):
    """A stair as a chair whose sitter faces {@code toward} (the table): its backrest, the stair's facing, is the other
    side (B148)."""
    stairs(b, x, y, z, fam, OPP[toward])


def hang(b, x, y, z, beam, axis="x"):
    """A lantern hanging at (x, y, z) from a log laid at y + 1 (when nothing is there yet)."""
    if b.get(x, y + 1, z) is None:
        log(b, x, y + 1, z, beam, axis=axis)
    lantern(b, x, y, z, hanging=True)


def front_door(b, x, z, name, step, hood=None, lamp=True):
    """The door in the front wall at (x, 1, z), a stair step before it at y 0 and, with {@code hood}, a little roof of
    upside-down stairs and a slab over it with a lantern hanging under the slab."""
    door(b, x, 1, z, name, "south")
    stairs(b, x, 0, z - 1, step, "south")
    if hood:
        stairs(b, x - 1, 3, z - 1, hood, "west", top=True)
        stairs(b, x + 1, 3, z - 1, hood, "east", top=True)
        slab(b, x, 4, z - 1, hood)
        if lamp:
            lantern(b, x, 3, z - 1, hanging=True)


def log_pile(b, x0, x1, z, y, rows, species=("oak_log", "spruce_log", "birch_log")):
    """Logs laid along x from x0 to x1, a stepped pile {@code rows} deep going back from z (y up, one row less each
    layer), the species mixed by position."""
    for layer in range(rows):
        for dz in range(layer, rows):
            for x in range(x0, x1 + 1):
                name = species[(x * 7 + dz * 3 + layer * 5) % len(species)]
                log(b, x, y + layer, z + dz, name, axis="x")


# --- The Builder's Workshop: a lean-to studio and a crane yard ---------------------------------------------------
# Walls x 1-7, z 3-9 under a low-pitched roof of spruce slabs rising to the west (half a block a row), the tall west wall
# with a clerestory window high in the front; spruce posts, oak boards, a stone plinth. East of it a fenced yard of
# gravel and coarse dirt with a timber crane (mast, jib, a chain with a block of stone bricks on it) and stacks of
# building stone and planks: the builder's trade from the street.
BW_FRAME = "spruce_log"
BW_BOARDS = "oak_planks"
BW_ROOF = DARK_OAK
BW_YARD = Mix((5, "coarse_dirt"), (3, "gravel"), (2, "dirt"), seed=301)
# The saltbox roof over x 0-8: a short steep side to the west, the ridge at x 2, a long low slope down to the yard
BW_ROOF_Y = {0: 9, 1: 10, 2: 11, 3: 10, 4: 9, 5: 8, 6: 7, 7: 6, 8: 5}


def builders_workshop_build():
    """13 x 13 x 11: the builder's saltbox studio and crane yard."""
    b = Build(13, 13, 11)
    plinth(b, 1, 3, 7, 9, FOUNDATION_MIX, floor="spruce_planks")
    # Walls up to the roof: each column stops under its stair
    for x in range(1, 8):
        top = BW_ROOF_Y[x] - 1
        for z in range(3, 10):
            if x in (1, 7) or z in (3, 9):
                for yy in range(1, top + 1):
                    b.set(x, yy, z, BW_BOARDS)
    for z in range(2, 11):
        for x in range(0, 9):
            if x == 2:
                slab(b, x, BW_ROOF_Y[x], z, BW_ROOF)
                b.set(x, BW_ROOF_Y[x] - 1, z, "spruce_planks")
            else:
                stairs(b, x, BW_ROOF_Y[x], z, BW_ROOF, "east" if x < 2 else "west")
        stairs(b, 8, 4, z, BW_ROOF, "east", top=True)  # the eave's soffit along the low side
    # Posts to the roof, the top plate at y 4 all round
    for x, z in ((1, 3), (3, 3), (5, 3), (7, 3), (1, 9), (3, 9), (5, 9), (7, 9), (1, 6)):
        for yy in range(1, BW_ROOF_Y[x]):
            log(b, x, yy, z, BW_FRAME)
    beam_ring(b, 1, 3, 7, 9, 4, BW_FRAME)
    # Front: the door in the middle under a hood, shuttered windows either side, the clerestory high on the tall side
    front_door(b, 4, 3, "spruce_door", SPRUCE, hood=SPRUCE)
    for x in (2, 6):
        window(b, x, 2, 3, "north", shutters="spruce_trapdoor", sill=SPRUCE)
    for x in (2, 4):
        window(b, x, 6, 3, "north", height=2)
        window(b, x, 6, 9, "south", height=2)
    window(b, 1, 6, 6, "west", height=2)
    for z in (4, 8):
        window(b, 1, 2, z, "west", shutters="spruce_trapdoor", sill=SPRUCE)
        window(b, 7, 2, z, "east")
    window(b, 4, 2, 9, "south", shutters="spruce_trapdoor")
    # A door out to the yard
    door(b, 7, 1, 6, "spruce_door", "west")
    # Inside: the Blueprint Table and its chest by the west wall, the bed, a lantern under a tie beam
    b.set(2, 1, 7, "aliveworkplace:blueprint_table", facing="east")
    b.set(2, 1, 6, "chest", facing="east", type="single", waterlogged=False)
    b.set(2, 1, 5, "spruce_planks")
    b.set(2, 2, 5, "potted_fern")
    b.bed(5, 1, 8, "orange", facing="west")
    for x in range(2, 7):
        log(b, x, 4, 6, BW_FRAME, axis="x")
    lantern(b, 4, 3, 6, hanging=True)
    b.set(6, 1, 4, "oak_slab", type="bottom", waterlogged=False)
    b.set(6, 1, 5, "oak_slab", type="bottom", waterlogged=False)
    # The yard: x 8-12, z 1-10, fenced, a gate at the front
    box(b, 8, 0, 1, 12, 0, 10, BW_YARD)
    for x in range(8, 13):
        if x != 10:
            fence(b, x, 1, 1, "spruce_fence")
        fence(b, x, 1, 10, "spruce_fence")
    for z in range(2, 10):
        fence(b, 12, 1, z, "spruce_fence")
    b.set(10, 1, 1, "spruce_fence_gate", facing="north", open=False, powered=False, in_wall=False)
    lantern(b, 8, 2, 1)
    lantern(b, 12, 2, 1)
    # The crane: a mast on a stone pad, a jib over the yard, a chain and a load of stone bricks; a counterweight
    for x in (10, 11):
        for z in (6, 7, 8):
            b.set(x, 0, z, "stone_bricks")
    for y in range(1, 12):
        log(b, 11, y, 7, BW_FRAME)
    for x in range(8, 13):
        if x != 11:
            log(b, x, 10, 7, BW_FRAME, axis="x")
    stairs(b, 10, 9, 7, SPRUCE, "west", top=True)
    stairs(b, 11, 1, 6, STONE_BRICK, "south")
    stairs(b, 11, 1, 8, STONE_BRICK, "north")
    stairs(b, 10, 1, 7, STONE_BRICK, "west")
    b.set(12, 9, 7, "cobblestone")
    for y in range(6, 10):
        b.set(9, y, 7, "chain", axis="y", waterlogged=False)
    b.set(9, 5, 7, "stone_bricks")
    b.set(11, 12, 7, "lightning_rod", facing="up", powered=False, waterlogged=False)
    # Stacks of stone and planks by the fence
    for x, z, h, what in ((9, 3, 2, "stone_bricks"), (10, 3, 1, "stone_bricks"), (11, 3, 2, "cobblestone"),
                          (11, 4, 1, "cobblestone")):
        for y in range(1, h + 1):
            b.set(x, y, z, what)
    for z in (9,):
        for x in (8, 9, 10):
            b.set(x, 1, z, "oak_planks")
            b.set(x, 2, z, "oak_slab", type="bottom", waterlogged=False)
    b.fill_air()
    return b


# --- The Carpenter's Workshop: a long saw shed ---------------------------------------------------------------------
# One long roof (ridge along x) over posts x 1-11, z 3-8: the west third a closed workshop of oak boards in a dark oak
# frame (the carpenter's crafting table, his bed), the rest an open shed with logs on trestles and a plank bench, its
# east gable an open truss. Logs piled before the shed and along the back wall under the eaves.
CW_FRAME = "dark_oak_log"
CW_BOARDS = "oak_planks"
CW_ROOF = DEEPSLATE_TILE
CW_SHED_FLOOR = Mix((4, "coarse_dirt"), (3, "spruce_planks"), (1, "dirt"), seed=311)


def carpenters_workshop_build():
    """13 x 11 x 11: the carpenter's saw shed."""
    b = Build(13, 11, 11)
    plinth(b, 1, 3, 11, 8, STONE_MIX)
    box(b, 2, 0, 4, 4, 0, 7, "spruce_planks")
    box(b, 5, 0, 4, 10, 0, 7, CW_SHED_FLOOR)
    # The workshop room: walls x 1-5, a partition with a door into the shed
    walls(b, 1, 3, 5, 8, 1, 4, CW_BOARDS)
    posts(b, [(1, 3), (5, 3), (1, 8), (5, 8), (8, 3), (11, 3), (8, 8), (11, 8), (3, 8), (1, 6)], 1, 4, CW_FRAME)
    beam_ring(b, 1, 3, 11, 8, 5, CW_FRAME)
    for z in range(4, 8):
        log(b, 5, 5, z, CW_FRAME, axis="z")
        log(b, 8, 5, z, CW_FRAME, axis="z")
    # Knee braces where the open shed's posts meet the beams
    for z in (3, 8):
        stairs(b, 7, 4, z, DARK_OAK, "east", top=True)
        stairs(b, 9, 4, z, DARK_OAK, "west", top=True)
        stairs(b, 10, 4, z, DARK_OAK, "east", top=True)
        stairs(b, 6, 4, z, DARK_OAK, "west", top=True)
    for z in (4, 7):
        stairs(b, 11, 4, z, DARK_OAK, "north" if z == 4 else "south", top=True)
    front_door(b, 3, 3, "dark_oak_door", DARK_OAK, hood=DARK_OAK)
    for z in (4, 7):
        window(b, 1, 2, z, "west", shutters="dark_oak_trapdoor", sill=DARK_OAK)
    window(b, 2, 2, 8, "south", shutters="dark_oak_trapdoor", sill=DARK_OAK)
    window(b, 4, 2, 8, "south", shutters="dark_oak_trapdoor", sill=DARK_OAK)
    door(b, 5, 1, 6, "dark_oak_door", "east")
    # The roof: stone-grey tiles, the west gable boarded with a king post and two little windows, the east an open truss
    gable_roof(b, 0, 12, 2, 9, 6, CW_ROOF, axis="x", gable=CW_BOARDS, gable_at=(1,), ridge=CW_ROOF, eave_trim=DARK_OAK)
    for y in range(6, 9):
        log(b, 1, y, 5, CW_FRAME)
        log(b, 1, y, 6, CW_FRAME)
    pane(b, 1, 7, 4)
    pane(b, 1, 7, 7)
    for y in range(6, 9):
        log(b, 11, y, 5, CW_FRAME)
        log(b, 11, y, 6, CW_FRAME)
    stairs(b, 11, 6, 4, DARK_OAK, "north", top=True)
    stairs(b, 11, 6, 7, DARK_OAK, "south", top=True)
    # Inside the workshop: the crafting table (the carpenter's), a chest of wood, the bed, a lantern
    b.set(2, 1, 7, "crafting_table")
    b.set(2, 1, 6, "chest", facing="east", type="single", waterlogged=False)
    b.bed(4, 1, 5, "brown", facing="north")  # its head to the wall: the cell inside the door stays clear (B178)
    b.set(2, 1, 4, "oak_slab", type="bottom", waterlogged=False)
    b.set(2, 2, 4, "potted_azure_bluet")
    for x in range(2, 5):
        log(b, x, 5, 5, CW_FRAME, axis="x")
    lantern(b, 3, 4, 5, hanging=True)
    # The shed: a log on trestles, a plank bench, an off-cut pile, lanterns under the tie beams
    for x in (6, 9):
        fence(b, x, 1, 5, "oak_fence")
        fence(b, x, 1, 7, "oak_fence")
    for x in range(6, 10):
        log(b, x, 2, 5, "stripped_oak_log", axis="x")
        slab(b, x, 2, 7, OAK)
    b.set(10, 1, 4, "oak_slab", type="double", waterlogged=False)
    b.set(10, 2, 4, "oak_slab", type="bottom", waterlogged=False)
    b.set(10, 1, 7, "oak_planks")
    lantern(b, 8, 4, 6, hanging=True)
    # Boards stood on end against the open gable, under the eaves
    for z in range(4, 8):
        for y in range(1, 4 if z % 2 else 3):
            log(b, 12, y, z, ("stripped_oak_log", "stripped_spruce_log", "stripped_birch_log", "stripped_oak_log")[z - 4])
    # Log piles: one before the shed, its ends to the street, a long one under the back eaves
    for x in range(7, 11):
        for z in (0, 1):
            log(b, x, 0, z, ("oak_log", "spruce_log", "birch_log")[(x + z) % 3], axis="z")
    for x in (8, 9):
        for z in (0, 1):
            log(b, x, 1, z, ("spruce_log", "oak_log")[(x + z) % 2], axis="z")
    for x in range(2, 11):
        log(b, x, 0, 9, ("oak_log", "spruce_log", "birch_log")[x % 3], axis="x")
        if x % 4 != 1:
            log(b, x, 1, 9, ("spruce_log", "birch_log", "oak_log")[x % 3], axis="x")
    lamp_post(b, 12, 0, 2, "dark_oak_fence", height=2)
    b.fill_air()
    return b


# --- The Kitchen: a plastered cookhouse with a bread oven -------------------------------------------------------
# Walls x 1-7, z 3-9 of white plaster in a spruce frame on a stone plinth under a dark oak hipped roof. On the east wall a
# domed brick bread oven bulges out, its mouth (the smoker, the chef's) opening into the kitchen and its flue up past the
# eaves to a smoking campfire. Herb beds either side of the path to the door; firewood stacked by the oven.
KI_FRAME = "spruce_log"
KI_PLASTER = Mix((7, "white_concrete"), (1, "polished_diorite"), seed=321)
KI_ROOF = DARK_OAK
KI_HERBS = ("fern", "allium", "azure_bluet", "lily_of_the_valley", "fern", "oxeye_daisy")


def kitchen_build():
    """11 x 12 x 11: the cookhouse with its bread oven and herb beds; under it the cold larder (ROADMAP 26.15), down a
    ladder inside the front door, 4 below."""
    from basements import cold_larder, BASEMENT
    b = Build(11, 12, 11, basement=BASEMENT)
    plinth(b, 1, 3, 7, 9, FOUNDATION_MIX, floor=Mix((3, "spruce_planks"), (1, "oak_planks"), seed=322))
    walls(b, 1, 3, 7, 9, 1, 4, KI_PLASTER)
    posts(b, [(1, 3), (7, 3), (1, 9), (4, 9), (7, 9), (1, 6), (7, 6)], 1, 4, KI_FRAME)
    beam_ring(b, 1, 3, 7, 9, 5, KI_FRAME)
    # Herb beds before the house, either side of the path
    flower_bed(b, 1, 1, 3, 2, 0, KI_HERBS)
    flower_bed(b, 5, 1, 7, 2, 0, KI_HERBS[::-1])
    b.set(4, 0, 0, "dirt_path")
    b.set(4, 0, 1, "dirt_path")
    front_door(b, 4, 3, "spruce_door", DARK_OAK, hood=KI_ROOF)
    for x in (2, 6):
        window(b, x, 2, 3, "north", height=2, sill=SPRUCE, lintel=SPRUCE)
    for z in (4, 8):
        window(b, 1, 2, z, "west", height=2, shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 7, 2, 8, "east", height=2, shutters="spruce_trapdoor", sill=SPRUCE)
    for x in (2, 6):
        window(b, x, 2, 9, "south", height=2, shutters="spruce_trapdoor", sill=SPRUCE)
    hip_roof(b, 0, 8, 2, 10, 6, KI_ROOF)
    # The bread oven on the east wall: a brick dome on a stone base, the smoker its mouth inside, the flue
    box(b, 8, 0, 4, 10, 0, 8, STONE_BRICK.full)
    box(b, 8, 1, 5, 10, 2, 7, "bricks")
    for z in (5, 6, 7):
        stairs(b, 10, 3, z, BRICK, "west")
    stairs(b, 8, 3, 5, BRICK, "south")
    stairs(b, 9, 3, 5, BRICK, "south")
    stairs(b, 8, 3, 7, BRICK, "north")
    stairs(b, 9, 3, 7, BRICK, "north")
    b.set(8, 3, 6, "bricks")
    b.set(9, 3, 6, "bricks")
    for y in range(4, 11):
        b.set(9, y, 6, "bricks")
    b.set(9, 11, 6, "campfire", facing="north", lit=True, signal_fire=False, waterlogged=False)
    b.set(7, 1, 6, "smoker", facing="west", lit=False)
    b.set(7, 2, 6, "bricks")
    b.set(7, 3, 6, "bricks")
    stairs(b, 6, 3, 6, BRICK, "west", top=True)  # the hood over the oven's mouth
    # Firewood by the oven
    for x in (8, 9, 10):
        log(b, x, 1, 4, "oak_log", axis="x")
        log(b, x, 1, 8, "birch_log", axis="x")
    log(b, 9, 2, 8, "oak_log", axis="x")
    # Inside: the table with two chairs, the larder chest, the bed, lanterns under a tie beam
    fence(b, 3, 1, 6, "spruce_fence")
    b.set(3, 2, 6, "spruce_pressure_plate", powered=False)
    chair_at(b, 2, 1, 6, SPRUCE, "east")
    chair_at(b, 4, 1, 6, SPRUCE, "west")
    b.set(6, 1, 4, "chest", facing="west", type="single", waterlogged=False)
    b.set(6, 1, 8, "smooth_stone_slab", type="double", waterlogged=False)
    b.set(5, 1, 8, "smooth_stone_slab", type="double", waterlogged=False)
    b.set(5, 2, 8, "flower_pot")  # crockery on the worktop (37.5; it was a potted fern)
    furnish(b, "crockery", 5, 2, 8, "north", wood="spruce", colour="white")
    b.bed(3, 1, 8, "yellow", facing="west")
    for x in range(2, 7):
        log(b, x, 5, 6, KI_FRAME, axis="x")
    lantern(b, 3, 4, 6, hanging=True)
    lantern(b, 6, 4, 6, hanging=True)
    cold_larder(b, 1, 3, 7, 9, ladder=(2, 4, "east"))
    b.fill_air()
    return b


# --- The Post Office: a narrow jettied house with a dovecote ---------------------------------------------------
# A stone ground floor (walls x 1-7, z 2-7) with a wide shop window either side of the door; the upper floor of birch
# boards in a spruce frame jettied a block out over the street on stair brackets, flower boxes under its windows; a
# red tiled roof with its gable to the street, and on the ridge a little dovecote for the post pigeons.
PO_STONE = Mix((7, "stone_bricks"), (2, "cracked_stone_bricks"), (1, "mossy_stone_bricks"), seed=331)
PO_FRAME = "spruce_log"
PO_BOARDS = "birch_planks"
PO_ROOF = BRICK


def post_office_build():
    """9 x 17 x 10: the post office with its dovecote."""
    b = Build(9, 17, 10)
    plinth(b, 1, 2, 7, 7, FOUNDATION_MIX, floor="spruce_planks")
    walls(b, 1, 2, 7, 7, 1, 3, PO_STONE)
    for x, z in ((1, 2), (7, 2), (1, 7), (7, 7)):
        for y in range(1, 4):
            b.set(x, y, z, "polished_andesite")
    # The floor between: planks, its beams showing, carried a block out at the front on brackets
    box(b, 1, 4, 1, 7, 4, 7, "spruce_planks")
    beam_ring(b, 1, 1, 7, 7, 4, PO_FRAME)
    for x in (1, 4, 7):
        stairs(b, x, 3, 1, SPRUCE, "south", top=True)
    # The upper floor (walls x 1-7, z 1-7)
    walls(b, 1, 1, 7, 7, 5, 7, PO_BOARDS)
    posts(b, [(1, 1), (4, 1), (7, 1), (1, 7), (4, 7), (7, 7), (1, 4), (7, 4)], 5, 7, PO_FRAME)
    beam_ring(b, 1, 1, 7, 7, 8, PO_FRAME)
    gable_roof(b, 0, 8, 0, 8, 9, PO_ROOF, axis="z", gable=PO_BOARDS, gable_at=(1, 7), eave_trim=SPRUCE)
    for z in (1, 7):
        for y in range(9, 12):
            log(b, 4, y, z, PO_FRAME)
        pane(b, 3, 10, z)
        pane(b, 5, 10, z)
    # The front: the door with a lantern over it, the shop windows, flower boxes on the jetty
    front_door(b, 4, 2, "spruce_door", SPRUCE)
    lantern(b, 4, 3, 1, hanging=True)
    for x in (2, 6):
        window(b, x, 1, 2, "north", height=2)
    window(b, 2, 6, 1, "north", width=2, flowers=("spruce_trapdoor", ("potted_red_tulip", "potted_poppy")))
    window(b, 5, 6, 1, "north", width=2, flowers=("spruce_trapdoor", ("potted_poppy", "potted_red_tulip")))
    for z in (3, 6):
        window(b, 1, 2, z, "west", sill=STONE_BRICK)
        window(b, 7, 2, z, "east", sill=STONE_BRICK)
    for z in (2, 6):
        window(b, 1, 6, z, "west", shutters="spruce_trapdoor")
        window(b, 7, 6, z, "east", shutters="spruce_trapdoor")
    window(b, 2, 6, 7, "south", shutters="spruce_trapdoor")
    window(b, 6, 6, 7, "south", shutters="spruce_trapdoor")
    # The dovecote on the ridge: a planked box with a pigeon hole front and back, a little hipped cap
    box(b, 3, 13, 4, 5, 13, 6, "spruce_planks")
    for x, z in ((3, 4), (5, 4), (3, 6), (5, 6)):
        log(b, x, 14, z, PO_FRAME)
    for x, z in ((3, 5), (5, 5)):
        b.set(x, 14, z, PO_BOARDS)
    slab(b, 4, 14, 4, SPRUCE)
    slab(b, 4, 14, 6, SPRUCE)
    hip_roof(b, 3, 5, 4, 6, 15, PO_ROOF)
    # Inside: the counter, the Mailbox (the postman's) behind it, pigeonholes, a chest of parcels, a ladder up
    b.set(6, 1, 5, "aliveworkplace:mailbox", facing="west", has_mail=False)
    b.set(2, 1, 6, "chiseled_bookshelf", facing="south")
    b.set(3, 1, 6, "chiseled_bookshelf", facing="south")
    b.set(6, 1, 6, "chest", facing="west", type="single", waterlogged=False)
    for x in (2, 3):
        b.set(x, 1, 4, "spruce_planks")
        slab(b, x, 2, 4, SPRUCE)
    b.set(4, 4, 6, "air")
    for y in range(1, 5):
        b.set(4, y, 6, "ladder", facing="north", waterlogged=False)
    lantern(b, 5, 3, 4, hanging=True)
    # Upstairs: the postman's bed, a desk with a lantern, a chest of letters
    b.bed(2, 5, 3, "blue", facing="south")
    b.set(6, 5, 2, "spruce_planks")
    lantern(b, 6, 6, 2)
    b.set(6, 5, 3, "chest", facing="west", type="single", waterlogged=False)
    lamp_post(b, 8, 0, 1, "spruce_fence", height=2)
    b.fill_air()
    return b


# --- The Guard House: a stone blockhouse with a watch hut on its roof ---------------------------------------------
# Thick walls of rubble stone (x 1-9, z 2-9) on a flared base, arrow slits barred with iron, a door under a stone hood;
# a corbelled parapet with merlons round a flat timber roof, and on it a timber watch hut under a dark pyramid roof,
# reached by a ladder inside.
GH_STONE = Mix((5, "cobblestone"), (3, "stone_bricks"), (2, "andesite"), (1, "mossy_cobblestone"), seed=341)
GH_TRIM = STONE_BRICK


def guard_house_build():
    """11 x 13 x 11: the blockhouse and its watch hut."""
    b = Build(11, 13, 11)
    box(b, 2, 0, 3, 8, 0, 8, Mix((4, "stone_bricks"), (2, "cobblestone"), (1, "andesite"), seed=342))
    walls(b, 1, 2, 9, 9, 0, 6, GH_STONE)
    for x, z in ((1, 2), (9, 2), (1, 9), (9, 9)):
        for y in range(0, 7):
            b.set(x, y, z, "stone_bricks" if y % 2 == 0 else "polished_andesite")
    skirt(b, 1, 2, 9, 9, COBBLE)
    # The roof: a deck of planks inside the wall top, a cornice of upside-down stairs, the parapet with merlons
    box(b, 2, 6, 3, 8, 6, 8, "spruce_planks")
    for x in range(0, 11):
        stairs(b, x, 6, 1, GH_TRIM, "south", top=True)
        stairs(b, x, 6, 10, GH_TRIM, "north", top=True)
    for z in range(2, 10):
        stairs(b, 0, 6, z, GH_TRIM, "east", top=True)
        stairs(b, 10, 6, z, GH_TRIM, "west", top=True)
    b.ring(0, 1, 10, 10, 7, "stone_bricks")
    for x in range(0, 11, 2):
        b.set(x, 8, 1, "stone_bricks")
        b.set(x, 8, 10, "stone_bricks")
    for z in range(3, 10, 2):
        b.set(0, 8, z, "stone_bricks")
        b.set(10, 8, z, "stone_bricks")
    # Arrow slits and the door
    for x in (3, 7):
        for y in (2, 3):
            fence(b, x, y, 2, "iron_bars")
    for z in (4, 7):
        for y in (2, 3):
            fence(b, 1, y, z, "iron_bars")
            fence(b, 9, y, z, "iron_bars")
    for y in (2, 3):
        fence(b, 5, y, 9, "iron_bars")
    door(b, 5, 1, 2, "spruce_door", "south")
    stairs(b, 4, 3, 1, GH_TRIM, "west", top=True)
    stairs(b, 6, 3, 1, GH_TRIM, "east", top=True)
    slab(b, 5, 4, 1, GH_TRIM)
    lantern(b, 5, 3, 1, hanging=True)
    stairs(b, 5, 0, 1, COBBLE, "south")
    # The watch hut on the back of the roof: posts, a rail, a pyramid roof
    for x, z in ((5, 5), (7, 5), (5, 7), (7, 7)):
        for y in (7, 8, 9):
            log(b, x, y, z, "spruce_log")
    fence(b, 6, 7, 7, "spruce_fence")
    fence(b, 5, 7, 6, "spruce_fence")
    fence(b, 7, 7, 6, "spruce_fence")
    for x in (5, 6, 7):
        log(b, x, 9, 4 + 1, "spruce_log", axis="x")
        log(b, x, 9, 7, "spruce_log", axis="x")
    hip_roof(b, 4, 8, 4, 8, 10, DARK_OAK)
    lantern(b, 6, 8, 5, hanging=True)
    # Inside: the grindstone (the guard's), the chest of gear, an anvil, the bed, a ladder to the roof
    b.set(8, 1, 7, "grindstone", face="floor", facing="west")
    b.set(8, 1, 8, "chest", facing="west", type="single", waterlogged=False)
    b.set(8, 1, 3, "anvil", facing="north")
    b.bed(4, 1, 8, "red", facing="west")
    for y in range(1, 7):
        b.set(2, y, 6, "ladder", facing="east", waterlogged=False)
    lantern(b, 5, 5, 5, hanging=True)
    b.fill_air()
    return b


# --- The Clinic: a white pavilion and its ward ---------------------------------------------------------------------
# A plastered pavilion (walls x 4-8, z 2-6) with its gable to the street, a big cross-mullioned window over the door;
# behind it, across the whole width, the ward (walls x 1-11, z 6-10) under a lower roof, with cross windows in its
# gable ends and back and beds along the back wall. Grey stone roofs, andesite quoins and mullions, white walls.
CL_PLASTER = Mix((8, "white_concrete"), (1, "polished_diorite"), seed=351)
CL_ROOF = STONE_BRICK
CL_MULLION = "polished_andesite"
CL_PLINTH = Mix((5, "stone_bricks"), (2, "polished_andesite"), seed=352)


def cross_window(b, x, y, z, axis, mullion=CL_MULLION):
    """A 3 x 3 window centred on (x, y, z) in a wall running along {@code axis}: a stone cross (mullion and transom)
    through its middle and glass in the four corners."""
    for i in (-1, 0, 1):
        for j in (-1, 0, 1):
            px, pz = (x + i, z) if axis == "x" else (x, z + i)
            if i == 0 or j == 0:
                b.set(px, y + j, pz, mullion)
            else:
                pane(b, px, y + j, pz)


def clinic_build():
    """13 x 11 x 12: the clinic's pavilion and ward."""
    b = Build(13, 11, 12)
    plinth(b, 1, 6, 11, 10, CL_PLINTH, floor="birch_planks")
    walls(b, 1, 6, 11, 10, 1, 5, CL_PLASTER)
    plinth(b, 4, 2, 8, 6, CL_PLINTH, floor="birch_planks")
    walls(b, 4, 2, 8, 6, 1, 6, CL_PLASTER)
    for x, z, top in ((1, 6, 5), (11, 6, 5), (1, 10, 5), (11, 10, 5), (4, 2, 6), (8, 2, 6)):
        for y in range(1, top + 1):
            b.set(x, y, z, "polished_andesite" if y % 2 else "stone_bricks")
    for x in range(5, 8):  # the pavilion opens into the ward
        b.set(x, 1, 6, "air")
        b.set(x, 2, 6, "air")
        b.set(x, 0, 6, "birch_planks")
    roofs(b,
          lambda t: gable_roof(t, 0, 12, 5, 11, 6, CL_ROOF, axis="x", gable=CL_PLASTER, gable_at=(1, 11), eave_trim=CL_ROOF,
                               closed=True),
          lambda t: gable_roof(t, 3, 9, 1, 7, 7, CL_ROOF, axis="z", gable=CL_PLASTER, gable_at=(2,), eave_trim=CL_ROOF,
                               closed=True))
    # The front: the door, the cross window over it, a window in the gable; the ward's ends either side
    front_door(b, 6, 2, "birch_door", STONE_BRICK)
    cross_window(b, 6, 4, 2, "x")
    log(b, 6, 8, 2, "stripped_spruce_log")
    for x in (5, 7):
        b.set(x, 1, 1, "polished_andesite")
        lantern(b, x, 2, 1)
    for x in (2, 10):
        window(b, x, 2, 6, "north", height=2, sill=CL_ROOF, lintel=CL_ROOF)
    # The ward's cross windows: one in each gable end, two at the back
    cross_window(b, 1, 3, 8, "z")
    cross_window(b, 11, 3, 8, "z")
    cross_window(b, 3, 3, 10, "x")
    cross_window(b, 9, 3, 10, "x")
    window(b, 6, 2, 10, "south", height=2, sill=CL_ROOF)
    # Inside: the brewing stand (the nurse's) by the door, a chest of remedies, four white beds along the back wall
    # (heads to it; B639: they were cots of slab and carpet, which read as broken beds), lanterns
    b.set(5, 1, 3, "polished_andesite")
    b.set(5, 2, 3, "potted_poppy")
    b.set(5, 1, 4, "brewing_stand", has_bottle_0=False, has_bottle_1=False, has_bottle_2=False)
    b.set(7, 1, 4, "chest", facing="west", type="single", waterlogged=False)
    b.set(7, 1, 3, "potted_azure_bluet")
    for x in (2, 4, 8, 10):
        b.bed(x, 1, 8, "white", facing="south")
    for x in range(2, 11):
        log(b, x, 5, 8, "stripped_spruce_log", axis="x")
    for x in (3, 6, 9):
        lantern(b, x, 4, 8, hanging=True)
    b.fill_air()
    return b


# --- The Ferry House: a boathouse with a landing stage ----------------------------------------------------------
# The same 9 x 10 plot as before (the plot finder's shore rule measures from its front): a landing stage of planks at
# the front (z 0-2) with mooring posts and a light on a mast, then the boathouse (walls x 1-7, z 3-8): a stone footing,
# spruce boards between log posts, under a dark oak gambrel roof (steep low, gentle high) with its gable to the water.
FE_BOARDS = "spruce_planks"
FE_FRAME = "stripped_spruce_log"
FE_ROOF = DARK_OAK
# The gambrel over x 0-8: (stairs y, a full block under it?) per column; x 4 is the ridge slab
FE_PROFILE = {0: (4, False), 1: (6, True), 2: (7, False), 3: (8, False), 4: (9, None), 5: (8, False), 6: (7, False),
              7: (6, True), 8: (4, False)}


def ferry_house_build():
    """9 x 10 x 10: the ferry house, its landing stage to the front."""
    b = Build(9, 10, 10)
    # The landing stage, rails either side, mooring posts, the light on its mast
    box(b, 0, 0, 0, 8, 0, 2, "spruce_planks")
    for z in (0, 1, 2):
        b.set(0, 0, z, "spruce_log", axis="z")
        b.set(8, 0, z, "spruce_log", axis="z")
    for z in (1, 2):
        fence(b, 0, 1, z, "spruce_fence")
    log(b, 0, 1, 0, "spruce_log")
    for y in range(1, 7):
        fence(b, 8, y, 0, "spruce_fence")
    fence(b, 7, 6, 0, "spruce_fence")
    lantern(b, 7, 5, 0, hanging=True)
    for z in (1, 2):
        fence(b, 8, 1, z, "spruce_fence")
    # The boathouse
    plinth(b, 1, 3, 7, 8, STONE_MIX, floor="spruce_planks")
    walls(b, 1, 3, 7, 8, 1, 1, Mix((5, "cobblestone"), (2, "mossy_cobblestone"), (2, "stone"), seed=361))
    walls(b, 1, 3, 7, 8, 2, 3, FE_BOARDS)
    posts(b, [(1, 3), (7, 3), (1, 8), (7, 8), (3, 3), (5, 3), (1, 6), (7, 6)], 1, 3, FE_FRAME)
    beam_ring(b, 1, 3, 7, 8, 4, "spruce_log")
    for z in range(2, 10):
        for x, (y, full) in FE_PROFILE.items():
            if full is None:
                slab(b, x, y, z, FE_ROOF)
                b.set(x, y - 1, z, FE_ROOF.full)
                continue
            stairs(b, x, y, z, FE_ROOF, "east" if x < 4 else "west")
            if full:
                b.set(x, y - 1, z, FE_ROOF.full)
    for z in range(2, 10):
        stairs(b, 0, 3, z, FE_ROOF, "east", top=True)
        stairs(b, 8, 3, z, FE_ROOF, "west", top=True)
    for z in (3, 8):  # the gables boarded up under the roof, a window and a loading door in the front one
        for x in range(2, 7):
            for y in range(5, FE_PROFILE[x][0]):
                b.set(x, y, z, FE_BOARDS)
        for y in range(5, 8):
            log(b, 4, y, z, FE_FRAME)
    pane(b, 3, 6, 3)
    pane(b, 5, 6, 3)
    front_door(b, 4, 3, "spruce_door", SPRUCE, hood=FE_ROOF)
    for x in (2, 6):
        window(b, x, 2, 3, "north", shutters="spruce_trapdoor", sill=SPRUCE)
    for z in (5, 7):
        window(b, 1, 2, z, "west", shutters="spruce_trapdoor")
        window(b, 7, 2, z, "east", shutters="spruce_trapdoor")
    # Inside: the Travel Post (the ferryman's), a bench for passengers, the bed, oars on the wall, a lantern
    b.set(2, 1, 7, "aliveworkplace:travel_post", facing="east")
    for z in (4, 5):
        stairs(b, 2, 1, z, SPRUCE, "west")
    b.bed(6, 1, 7, "cyan", facing="north")
    for x in range(2, 7):
        log(b, x, 4, 5, "spruce_log", axis="x")
    lantern(b, 4, 3, 5, hanging=True)
    for y in (2, 3):
        fence(b, 6, y, 4, "spruce_fence")
    b.fill_air()
    return b


# --- The Trainer's House: a dojo and its sparring ring ------------------------------------------------------------
# A long low hall at the back (walls x 2-10, z 8-11) of birch boards in a dark oak frame under a wide acacia roof that
# reaches forward over a deep veranda on posts (z 5-7), the Training Post on it; before it a fenced sparring ring of
# sand with straw targets at its corners and lanterns on its posts.
TR_FRAME = "dark_oak_log"
TR_BOARDS = "birch_planks"
TR_ROOF = ACACIA
TR_RING = Mix((5, "sand"), (2, "smooth_sandstone"), (1, "sandstone"), seed=371)


def trainers_house_build():
    """13 x 10 x 13: the trainer's dojo and sparring ring."""
    b = Build(13, 10, 13)
    plinth(b, 2, 5, 10, 11, STONE_MIX, floor="spruce_planks")
    walls(b, 2, 8, 10, 11, 1, 4, TR_BOARDS)
    posts(b, [(2, 8), (10, 8), (2, 11), (10, 11), (4, 8), (8, 8), (6, 11), (4, 11), (8, 11)], 1, 4, TR_FRAME)
    beam_ring(b, 2, 8, 10, 11, 5, TR_FRAME)
    # The veranda: posts along z 5, a beam on them, the deck of the floor
    for x in (2, 4, 6, 8, 10):
        for y in range(1, 6):
            log(b, x, y, 5, TR_FRAME)
    for x in range(2, 11):
        if b.get(x, 5, 5) is None:
            log(b, x, 5, 5, TR_FRAME, axis="x")
    for z in (6, 7):
        log(b, 2, 5, z, TR_FRAME, axis="z")
        log(b, 10, 5, z, TR_FRAME, axis="z")
    for x in (3, 5, 7, 9):
        stairs(b, x, 4, 5, DARK_OAK, "west" if x in (3, 7) else "east", top=True)
    gable_roof(b, 1, 11, 4, 12, 5, TR_ROOF, axis="x", gable=TR_BOARDS, gable_at=(2, 10), eave_trim=DARK_OAK)
    for x in (2, 10):
        for y in range(6, 9):
            if b.get(x, y, 8) is not None and b.get(x, y, 8)[0].endswith("birch_planks"):
                log(b, x, y, 8, TR_FRAME)
    # The front of the hall: sliding screens of glass between the posts, the door in the middle
    door(b, 6, 1, 8, "dark_oak_door", "south")
    for x in (3, 5, 7, 9):
        for y in (2, 3):
            pane(b, x, y, 8)
    window(b, 2, 2, 9, "west", height=2)
    window(b, 10, 2, 10, "east", height=2)
    for x in (3, 5, 7, 9):
        window(b, x, 2, 11, "south", height=2)
    # The Training Post (the trainer's) on the veranda, a bench, lanterns from the veranda beam
    b.set(9, 1, 7, "aliveworkplace:training_post", facing="south")
    for x in (3, 4):
        stairs(b, x, 1, 7, SPRUCE, "north")
    log(b, 6, 5, 6, TR_FRAME, axis="z")
    log(b, 6, 5, 7, TR_FRAME, axis="z")
    lantern(b, 6, 4, 6, hanging=True)
    # Inside: the trainer's bed, a trophy shelf, a lantern
    b.bed(9, 1, 10, "orange", facing="west")
    b.set(3, 1, 10, "bookshelf")
    b.set(3, 2, 10, "potted_cactus")
    b.set(4, 1, 10, "chest", facing="north", type="single", waterlogged=False)
    for x in range(3, 10):
        if b.get(x, 5, 9) is None:
            log(b, x, 5, 9, TR_FRAME, axis="x")
    lantern(b, 6, 4, 9, hanging=True)
    # The sparring ring: sand inside a fence, gaps front and back, targets on straw at the corners, lanterns
    box(b, 1, 0, 0, 11, 0, 4, TR_RING)
    for x in range(1, 12):
        if x not in (5, 6, 7):
            fence(b, x, 1, 0, "dark_oak_fence")
            fence(b, x, 1, 4, "dark_oak_fence")
    for z in range(1, 4):
        fence(b, 1, 1, z, "dark_oak_fence")
        fence(b, 11, 1, z, "dark_oak_fence")
    for x, z in ((1, 0), (11, 0), (1, 4), (11, 4)):
        b.set(x, 1, z, "dark_oak_log", axis="y")
        lantern(b, x, 2, z)
    for x in (2, 10):
        b.set(x, 1, 1, "hay_block", axis="y")
        b.set(x, 2, 1, "target")
    b.fill_air()
    return b


# --- The Leader's Hall: a stone hall with a columned portico ----------------------------------------------------
# A stately hall of stone bricks (walls x 2-10, z 4-12) with tall windows in andesite surrounds; before it a portico of
# four andesite columns with chiseled capitals carrying a stone pediment (the front gable of a deepslate roof that runs
# the whole length); steps the full width of the front; inside a red carpet up to the Training Post between banners.
LH_STONE = Mix((8, "stone_bricks"), (1, "cracked_stone_bricks"), seed=381)
LH_ROOF = DEEPSLATE_BRICK


def leaders_hall_build():
    """13 x 14 x 14: the leader's columned hall."""
    b = Build(13, 14, 14)
    # The podium: stone brick floor over the portico and the hall, steps the full width
    box(b, 1, 0, 1, 11, 0, 13, "stone_bricks")
    for x in range(1, 12):
        stairs(b, x, 0, 0, STONE_BRICK, "south")
    walls(b, 2, 4, 10, 12, 1, 7, LH_STONE)
    box(b, 3, 0, 5, 9, 0, 11, "polished_andesite")
    for x, z in ((2, 4), (10, 4), (2, 12), (10, 12), (2, 8), (10, 8)):
        for y in range(1, 8):
            b.set(x, y, z, "polished_andesite")
    # The portico: four columns, an architrave over them and the ceiling, the pediment above
    for x in (3, 5, 7, 9):
        b.set(x, 1, 2, "chiseled_stone_bricks")
        for y in range(2, 6):
            b.set(x, y, 2, "polished_andesite")
        b.set(x, 6, 2, "chiseled_stone_bricks")
    box(b, 2, 7, 1, 10, 7, 3, "stone_bricks")
    for x in range(1, 12):
        stairs(b, x, 6, 1, STONE_BRICK, "south", top=True)
    for z in (2, 3):
        stairs(b, 1, 6, z, STONE_BRICK, "east", top=True)
        stairs(b, 11, 6, z, STONE_BRICK, "west", top=True)
    gable_roof(b, 1, 11, 1, 13, 8, LH_ROOF, axis="z", gable=LH_STONE, gable_at=(1, 12), eave_trim=STONE_BRICK)
    for z in (1, 12):
        b.set(6, 10, z, "chiseled_stone_bricks")
    # The front: a double door under a stone lintel, tall windows either side; tall windows down both sides
    b.door(5, 1, 4, "dark_oak_door", "south", hinge="left")
    b.door(7, 1, 4, "dark_oak_door", "south", hinge="right")
    b.set(6, 1, 4, "air")
    b.set(6, 2, 4, "air")
    for x in (5, 6, 7):
        b.set(x, 3, 4, "chiseled_stone_bricks" if x == 6 else "polished_andesite")
    for x in (3, 9):
        window(b, x, 2, 4, "north", height=3, sill=STONE_BRICK)
    for z in (6, 10):
        window(b, 2, 2, z, "west", height=4, sill=STONE_BRICK, lintel=STONE_BRICK)
        window(b, 10, 2, z, "east", height=4, sill=STONE_BRICK, lintel=STONE_BRICK)
    window(b, 6, 4, 12, "south", height=3)
    for x in (4, 8):
        lantern(b, x, 6, 2, hanging=True)
    # Inside: a red carpet from the door to the Training Post (the leader's) on a dais, banners, the leader's bed. It
    # starts a block in from the door (B170): the doorway is two blocks high, and a villager (1.95 tall) stepping up onto
    # a carpet while its head is still under the lintel hits it, so the leader stood in the doorway for good.
    for z in range(6, 11):
        b.set(6, 1, z, "red_carpet")
    for x in (5, 6, 7):
        slab(b, x, 1, 11, STONE_BRICK)
    b.set(6, 1, 11, "aliveworkplace:training_post", facing="north")
    for x in (4, 8):
        b.set(x, 1, 11, "polished_andesite")
        lantern(b, x, 2, 11)
    for x in (4, 8):
        b.set(x, 4, 11, "blue_wall_banner", facing="north")
    b.bed(3, 1, 6, "blue", facing="south")
    b.set(9, 1, 5, "chest", facing="west", type="single", waterlogged=False)
    for z in (6, 10):
        for x in range(3, 10):
            log(b, x, 7, z, "dark_oak_log", axis="x")
        lantern(b, 6, 6, z, hanging=True)
    b.fill_air()
    return b


# --- The Ball Workshop: a round drying tower and a workshop wing ---------------------------------------------------
# A round tower (7 across, centred on x 9, z 5): a rubble base, stone bricks above, and at the top an open drying loft behind
# slatted fences where apricorns (coloured carpets) are laid out to dry, under a copper cone that greens with the years;
# a ladder inside up to the loft. Against it on the west the low workshop (walls x 1-6, z 3-8): oak boards in a spruce
# frame under a spruce roof that runs into the tower, the smithing table (the ball smith's) inside.
BA_TOWER = (9, 5)
BA_ROOF = CUT_COPPER
BA_LOWER = Mix((5, "cobblestone"), (3, "stone"), (2, "andesite"), seed=391)
BA_UPPER = Mix((7, "stone_bricks"), (2, "polished_andesite"), (1, "cracked_stone_bricks"), seed=392)
APRICORNS = ("red_carpet", "yellow_carpet", "blue_carpet", "lime_carpet", "pink_carpet", "black_carpet", "white_carpet")


def round_ring(cx, cz):
    """The wall cells of a round tower 7 across centred on (cx, cz), and the 21 cells inside."""
    ring = [(cx + dx, cz + dz) for dx in range(-3, 4) for dz in range(-3, 4)
            if (abs(dx) == 3 and abs(dz) <= 1) or (abs(dz) == 3 and abs(dx) <= 1) or (abs(dx) == 2 and abs(dz) == 2)]
    inside = [(cx + dx, cz + dz) for dx in range(-2, 3) for dz in range(-2, 3) if abs(dx) + abs(dz) < 4]
    return ring, inside


def ball_workshop_build():
    """14 x 17 x 11: the apricorn-drying tower and the ball smith's workshop."""
    b = Build(14, 17, 11)
    cx, cz = BA_TOWER
    ring, inside = round_ring(cx, cz)
    # The workshop wing (walls x 1-5), its roof running into the tower
    plinth(b, 1, 3, 5, 8, STONE_MIX, floor="spruce_planks")
    walls(b, 1, 3, 5, 8, 1, 3, "oak_planks")
    posts(b, [(1, 3), (5, 3), (1, 8), (5, 8), (1, 6), (3, 8)], 1, 3, "spruce_log")
    beam_ring(b, 1, 3, 5, 8, 4, "spruce_log")
    gable_roof(b, 0, 6, 2, 9, 5, SPRUCE, axis="x", gable="oak_planks", gable_at=(1,), ridge=SPRUCE, eave_trim=SPRUCE)
    log(b, 1, 6, 5, "spruce_log")
    log(b, 1, 6, 6, "spruce_log")
    # The tower
    for x, z in ring:
        b.set(x, 0, z, resolve(STONE_MIX, x, 0, z))
        for y in range(1, 9):
            b.set(x, y, z, resolve(BA_LOWER if y <= 3 else BA_UPPER, x, y, z))
    for x, z in inside:
        b.set(x, 0, z, "spruce_planks")
        b.set(x, 8, z, "spruce_planks")
    for x, z in ring:  # the drying loft: stone piers on the diagonals, slatted fences between
        for y in (9, 10):
            if abs(x - cx) == 2:
                b.set(x, y, z, resolve(BA_UPPER, x, y, z))
            else:
                fence(b, x, y, z, "spruce_fence")
    for i, (x, z) in enumerate(inside):
        if (x, z) != (cx + 2, cz):
            b.set(x, 9, z, APRICORNS[i % len(APRICORNS)])
    b.set(cx + 2, 8, cz, "air")
    for y in range(1, 9):
        b.set(cx + 2, y, cz, "ladder", facing="west", waterlogged=False)
    hip_roof(b, cx - 4, cx + 4, cz - 4, cz + 4, 11, BA_ROOF)
    b.set(cx, 16, cz, "lightning_rod", facing="up", powered=False, waterlogged=False)
    # Narrow windows up the tower, a doorway through from the workshop
    for y in (5, 6):
        pane(b, cx, y, cz - 3)
        pane(b, cx, y, cz + 3)
    for y in (1, 2):
        b.set(cx - 3, y, cz, "air")
        b.set(5, y, cz, "air")
    # The wing's front: the door under a hood, a window either side; windows at the side and back
    front_door(b, 3, 3, "spruce_door", SPRUCE, hood=SPRUCE)
    for x in (2, 4):
        window(b, x, 2, 3, "north")
    window(b, 1, 2, 4, "west", shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 1, 2, 7, "west", shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 2, 2, 8, "south", shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 4, 2, 8, "south", shutters="spruce_trapdoor", sill=SPRUCE)
    # Inside: the smithing table (the ball smith's), a chest of copper and dye, an anvil, the bed, a lantern
    b.set(2, 1, 7, "smithing_table")
    b.set(2, 1, 6, "chest", facing="east", type="single", waterlogged=False)
    b.set(2, 1, 4, "anvil", facing="north")
    b.bed(4, 1, 7, "red", facing="west")
    for x in range(2, 5):
        log(b, x, 4, 5, "spruce_log", axis="x")
    lantern(b, 3, 3, 5, hanging=True)
    lantern(b, cx, 3, cz, hanging=True)
    for y in range(4, 8):
        b.set(cx, y, cz, "chain", axis="y", waterlogged=False)
    # Drying trays on trestles before the tower, apricorns laid out on them
    for x in (8, 10):
        fence(b, x, 1, 1, "spruce_fence")
    for x, c in ((8, "red_carpet"), (9, "yellow_carpet"), (10, "blue_carpet")):
        slab(b, x, 2, 1, SPRUCE)
        b.set(x, 3, 1, c)
    b.fill_air()
    return b


# --- The Trade Hall: a market hall on a stone arcade ------------------------------------------------------------
# An open trading floor of stone arches (columns x 1, 5, 9 by z 2, 5, 8) with the Shop Counter (the trader's) and goods
# on show under it; above, the hall's upper storey of brick nogging in a dark oak frame, under a spruce roof with a
# little open cupola and a vane on its ridge. A ladder up a column to the storeroom and the trader's bed.
TH_COLUMN = Mix((6, "stone_bricks"), (2, "polished_andesite"), (1, "cracked_stone_bricks"), seed=401)
TH_FRAME = "dark_oak_log"
TH_NOGGING = Mix((9, "bricks"), (1, "granite"), seed=402)
TH_ROOF = SPRUCE


def trade_hall_build():
    """11 x 17 x 11: the market hall on its arcade."""
    b = Build(11, 17, 11)
    box(b, 1, 0, 2, 9, 0, 8, Mix((5, "stone_bricks"), (3, "polished_andesite"), (2, "cobblestone"), seed=403))
    for x in range(0, 11):
        b.set(x, 0, 1, resolve(STONE_MIX, x, 0, 1))
    columns = [(x, z) for x in (1, 5, 9) for z in (2, 5, 8)]
    for x, z in columns:
        if (x, z) == (5, 5):
            continue
        for y in range(1, 4):
            b.set(x, y, z, resolve(TH_COLUMN, x, y, z))
    # The arches: upside-down stairs springing from the columns, the floor of the hall over them
    for z in (2, 8):
        for x0 in (1, 5):
            stairs(b, x0 + 1, 3, z, STONE_BRICK, "west", top=True)
            stairs(b, x0 + 3, 3, z, STONE_BRICK, "east", top=True)
    for x in (1, 9):
        for z0 in (2, 5):
            stairs(b, x, 3, z0 + 1, STONE_BRICK, "north", top=True)
            stairs(b, x, 3, z0 + 2, STONE_BRICK, "south", top=True)
    box(b, 1, 4, 2, 9, 4, 8, "spruce_planks")
    beam_ring(b, 1, 2, 9, 8, 4, TH_FRAME)
    for x in (5,):
        for z in range(3, 8):
            log(b, x, 4, z, TH_FRAME, axis="z")
    # The upper storey, jettied a little: brick between dark oak posts
    walls(b, 1, 2, 9, 8, 5, 7, TH_NOGGING)
    posts(b, [(x, z) for x in (1, 3, 5, 7, 9) for z in (2, 8)] + [(1, 5), (9, 5)], 5, 7, TH_FRAME)
    beam_ring(b, 1, 2, 9, 8, 8, TH_FRAME)
    for x in (2, 4, 6, 8):
        window(b, x, 6, 2, "north", sill=DARK_OAK)
        window(b, x, 6, 8, "south", sill=DARK_OAK)
    for z in (3, 7):
        window(b, 1, 6, z, "west")
        window(b, 9, 6, z, "east")
    gable_roof(b, 0, 10, 1, 9, 9, TH_ROOF, axis="x", gable=TH_NOGGING, gable_at=(1, 9), eave_trim=DARK_OAK)
    for x in (1, 9):
        for y in range(9, 12):
            log(b, x, y, 5, TH_FRAME)
    # The cupola on the ridge: four posts, a lantern, a little hipped cap and a vane
    box(b, 4, 13, 4, 6, 13, 6, "spruce_planks")
    for x, z in ((4, 4), (6, 4), (4, 6), (6, 6)):
        fence(b, x, 14, z, "dark_oak_fence")
    hip_roof(b, 4, 6, 4, 6, 15, TH_ROOF)
    b.set(5, 16, 5, "lightning_rod", facing="up", powered=False, waterlogged=False)
    # The trading floor: the Shop Counter (the trader's), stalls of goods, lanterns under the floor beams
    b.set(5, 1, 4, "aliveworkplace:shop_counter", facing="north")
    for x, z, what in ((2, 3, "melon"), (2, 4, "pumpkin"), (8, 3, "hay_block"), (8, 4, "white_wool"), (8, 6, "red_wool"),
                       (2, 6, "oak_planks")):
        b.set(x, 1, z, what, **({"axis": "y"} if what == "hay_block" else {}))
    b.set(2, 2, 6, "potted_red_tulip")
    for x, z in ((3, 3), (7, 3), (3, 7), (7, 7)):
        lantern(b, x, 3, z, hanging=True)
    # A ladder up the back middle column to the storeroom: the trader's bed, a chest
    b.set(5, 4, 7, "air")
    for y in range(1, 5):
        b.set(5, y, 7, "ladder", facing="north", waterlogged=False)
    b.bed(2, 5, 7, "green", facing="north")
    b.set(8, 5, 7, "chest", facing="west", type="single", waterlogged=False)
    b.set(8, 5, 3, "chest", facing="west", type="single", waterlogged=False)
    for x in range(2, 9):
        log(b, x, 8, 5, TH_FRAME, axis="x")
    lantern(b, 5, 7, 5, hanging=True)
    b.fill_air()
    return b


# --- The School: a U of brick round a courtyard ------------------------------------------------------------------
# A brick range across the back (walls x 1-15, z 7-11, taller) and two lower wings coming forward to the street
# (x 1-5 and x 11-15, z 2-7), their gables to the front, round a paved courtyard behind a low wall and a gateway, with
# the tutor's Training Post in it. Stone quoins and plinth, a grey tiled roof, shelves of books in the wings.
SC_BRICK = Mix((9, "bricks"), (1, "granite"), seed=411)
SC_ROOF = DEEPSLATE_TILE
SC_STONE = Mix((6, "stone_bricks"), (2, "polished_andesite"), seed=412)


def school_build():
    """17 x 11 x 13: the U-shaped school and its courtyard."""
    b = Build(17, 11, 13)
    # The back range and the wings
    plinth(b, 1, 7, 15, 11, SC_STONE, floor="oak_planks")
    walls(b, 1, 7, 15, 11, 1, 5, SC_BRICK)
    for x0, x1 in ((1, 5), (11, 15)):
        plinth(b, x0, 2, x1, 7, SC_STONE, floor="oak_planks")
        walls(b, x0, 2, x1, 7, 1, 4, SC_BRICK)
        for x in range(x0 + 1, x1):  # the wing opens into the range
            for y in (1, 2, 3):
                b.set(x, y, 7, "air")
            b.set(x, 0, 7, "oak_planks")
    for x, z, top in ((1, 2, 4), (5, 2, 4), (11, 2, 4), (15, 2, 4), (1, 11, 5), (15, 11, 5), (1, 7, 5), (15, 7, 5),
                      (5, 7, 5), (11, 7, 5)):
        for y in range(1, top + 1):
            b.set(x, y, z, "stone_bricks" if y % 2 else "polished_andesite")
    roofs(b,
          lambda t: gable_roof(t, 0, 16, 6, 12, 6, SC_ROOF, axis="x", gable=SC_BRICK, gable_at=(1, 15), eave_trim=STONE_BRICK),
          lambda t: gable_roof(t, 0, 6, 1, 8, 5, SC_ROOF, axis="z", gable=SC_BRICK, gable_at=(2,), eave_trim=STONE_BRICK),
          lambda t: gable_roof(t, 10, 16, 1, 8, 5, SC_ROOF, axis="z", gable=SC_BRICK, gable_at=(2,), eave_trim=STONE_BRICK))
    # The wings' fronts: a tall window and a little one in each gable; windows down their sides and along the back
    for x in (3, 13):
        window(b, x, 2, 2, "north", height=2, sill=STONE_BRICK, lintel=STONE_BRICK)
        pane(b, x, 6, 2)
    for z in (4, 6):
        window(b, 1, 2, z, "west", height=2, sill=STONE_BRICK)
        window(b, 15, 2, z, "east", height=2, sill=STONE_BRICK)
        window(b, 5, 2, z, "east", height=2)
        window(b, 11, 2, z, "west", height=2)
    for x in (3, 6, 10, 13):
        window(b, x, 2, 11, "south", height=2, sill=STONE_BRICK)
    for x in (6, 10):
        window(b, x, 3, 7, "north", height=2)
    # The courtyard: paving, a low wall with a gateway to the street, the Training Post (the tutor's), potted shrubs
    box(b, 6, 0, 1, 10, 0, 6, Mix((4, "stone_bricks"), (2, "polished_andesite"), (1, "mossy_stone_bricks"), seed=413))
    for x in (6, 7, 9, 10):
        wall_block(b, x, 1, 1, "stone_brick_wall")
    for x in (7, 9):
        lantern(b, x, 2, 1)
    stairs(b, 8, 0, 0, STONE_BRICK, "south")
    b.set(8, 1, 4, "aliveworkplace:training_post", facing="north")
    for x in (6, 10):
        b.set(x, 1, 6, "potted_azalea_bush")
    # The way in from the courtyard, a hood over it
    door(b, 8, 1, 7, "oak_door", "south")
    stairs(b, 7, 3, 6, STONE_BRICK, "west", top=True)
    stairs(b, 9, 3, 6, STONE_BRICK, "east", top=True)
    slab(b, 8, 4, 6, STONE_BRICK)
    lantern(b, 8, 3, 6, hanging=True)
    # Inside: shelves of books in the wings, a desk and chair in each, the tutor's bed in the range, lanterns
    for shelf_x, desk_x in ((2, 3), (14, 13)):
        for z in (3, 4, 5, 6):
            b.set(shelf_x, 1, z, "bookshelf")
        fence(b, desk_x, 1, 4, "oak_fence")
        b.set(desk_x, 2, 4, "oak_pressure_plate", powered=False)
        chair_at(b, desk_x, 1, 5, OAK, "north")
        for z in range(3, 7):
            log(b, desk_x, 4, z, "spruce_log", axis="z")
        lantern(b, desk_x, 3, 6, hanging=True)
    b.bed(14, 1, 10, "light_blue", facing="west")
    b.set(2, 1, 10, "chest", facing="east", type="single", waterlogged=False)
    b.set(2, 1, 9, "bookshelf")
    for x in range(2, 15):
        log(b, x, 5, 9, "spruce_log", axis="x")
    for x in (4, 12):
        lantern(b, x, 4, 9, hanging=True)
    b.fill_air()
    return b


# --- The Crafter's Workshop (ROADMAP 37.14): a timber workroom with an open store lean-to ------------------------------
# Main mass x 1-9, z 3-8: birch boards in a spruce frame (posts at 1, 4, 6, 9 so the door sits in the middle bay) on a
# stone plinth, under a dark oak gable roof (ridge along x) with a boarded west gable and a king post. Against the east
# wall a lower open lean-to on two posts, under a slab roof: the store a Crafter works from, a stepped pile of logs and
# stacks of planks and slabs on shelves. Inside the Crafter's Workbench faces the door, with a chest either side of it
# (the Crafter's own: what they make for nobody in particular goes there), the bed and a lantern on the tie beam. No
# crafting table or stonecutter (those would make a Carpenter or a Mason).
CR_FRAME = "spruce_log"
CR_BOARDS = "birch_planks"
CR_ROOF = DARK_OAK


def crafters_workshop():
    """13 x 11 x 11: the Crafter's workroom and its store."""
    b = Build(13, 11, 11)
    plinth(b, 1, 3, 9, 8, STONE_MIX)
    box(b, 2, 0, 4, 8, 0, 7, "spruce_planks")
    walls(b, 1, 3, 9, 8, 1, 4, CR_BOARDS)
    posts(b, [(1, 3), (4, 3), (6, 3), (9, 3), (1, 8), (4, 8), (6, 8), (9, 8), (1, 5), (1, 6), (9, 5), (9, 6)], 1, 4, CR_FRAME)
    beam_ring(b, 1, 3, 9, 8, 5, CR_FRAME)
    for z in range(4, 8):
        log(b, 5, 5, z, CR_FRAME, axis="z")  # the tie beam the lantern hangs from
    front_door(b, 5, 3, "spruce_door", SPRUCE, hood=SPRUCE)
    for x in (2, 7):
        window(b, x, 2, 3, "north", width=2, shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 2, 2, 8, "south", width=2, shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 7, 2, 8, "south", width=2, shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 1, 2, 4, "west", shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 1, 2, 7, "west", shutters="spruce_trapdoor", sill=SPRUCE)
    door(b, 9, 1, 6, "spruce_door", "east")  # out to the store
    gable_roof(b, 0, 10, 2, 9, 6, CR_ROOF, axis="x", gable=CR_BOARDS, gable_at=(1, 9), ridge=CR_ROOF, eave_trim=SPRUCE)
    for y in range(6, 9):
        log(b, 1, y, 5, CR_FRAME)
        log(b, 1, y, 6, CR_FRAME)
        log(b, 9, y, 5, CR_FRAME)
        log(b, 9, y, 6, CR_FRAME)
    pane(b, 1, 7, 4)
    pane(b, 1, 7, 7)
    # Inside: the workbench facing the door, a chest either side, the bed, a lantern on the tie beam
    b.set(5, 1, 7, "aliveworkplace:crafters_workbench", facing="north")
    b.set(4, 1, 7, "chest", facing="north", type="single", waterlogged=False)
    b.set(6, 1, 7, "chest", facing="north", type="single", waterlogged=False)
    b.bed(8, 1, 5, "cyan", facing="north")
    b.set(2, 1, 7, "birch_slab", type="bottom", waterlogged=False)
    b.set(2, 2, 7, "potted_oxeye_daisy")
    b.set(2, 1, 4, "oak_slab", type="double", waterlogged=False)
    b.set(2, 2, 4, "oak_slab", type="bottom", waterlogged=False)
    lantern(b, 5, 4, 5, hanging=True)
    # The store: a lean-to on posts against the east wall, a slab roof, logs piled and boards stacked on shelves
    for z in (3, 8):
        for y in (1, 2, 3):
            log(b, 12, y, z, CR_FRAME)
    plinth(b, 10, 3, 12, 8, STONE_MIX)
    for x in range(10, 13):
        for z in range(3, 9):
            slab(b, x, 4, z, SPRUCE)
    for z in range(4, 8):
        slab(b, 10, 0, z, SPRUCE, double=True)
    log_pile(b, 11, 12, 5, 1, 2)
    for z in (4, 7):
        slab(b, 11, 1, z, OAK, double=True)
        slab(b, 11, 2, z, OAK)
    lantern(b, 11, 3, 6, hanging=True)
    # Outside: a lantern on a post at the corner, a path of coarse dirt to the step
    lamp_post(b, 0, 0, 1, "spruce_fence", height=2)
    for z in (0, 1):
        b.set(5, 0, z, "coarse_dirt" if z else "dirt_path")
    b.fill_air()
    return b


# Each workplace's drawing, by its blueprint id (the ids never change: saves name them).
WORKPLACE_BUILDS = {
    "builders_workshop": builders_workshop_build, "carpenters_workshop": carpenters_workshop_build,
    "kitchen": kitchen_build, "post_office": post_office_build, "guard_house": guard_house_build,
    "clinic": clinic_build, "ferry_house": ferry_house_build, "trainers_house": trainers_house_build,
    "leaders_hall": leaders_hall_build, "ball_workshop": ball_workshop_build, "trade_hall": trade_hall_build,
    "school": school_build,
}
