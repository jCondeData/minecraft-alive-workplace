"""More looks for the Pokémon Center (ROADMAP 28.7a): the same two tiers, drawn again in other shapes.

A look of a blueprint lives at ``looks/<look>/<name>`` (``aliveworkplace:looks/lodge/pokemon_center``, its upgrade
``looks/lodge/pokemon_center_2``); the mod counts it as that blueprint everywhere (the steward's rules, the hall's advice,
the Village Map) and a village's steward builds the look its village draws (``blueprint.BlueprintLooks``). Each look
keeps the Center's job blocks where the original has them: the Healing Machine at (6, 1, 7) and the PC at (10, 1, 7);
tier II adds a Shop Counter, four beds and a Pasture Block. Original designs, drawn to STYLE.md.

Mountain Lodge (13 x 12 x 12): the Center as a timber lodge for cold and wooded country. A spruce frame on a cobble
socle with white plaster between, a steep red gable roof along the front, a cross-gabled porch on two posts over the
door that dies into the main slope, flower boxes under shuttered windows and a stone chimney up the west gable.
Lodge II (21 x 14 x 18): a two-storey wing on the east, joined by a short passage (a trade corner with a Shop Counter
below, four beds above) under a higher gable turned across the hall's, and a fenced pen behind with a Pasture Block.

Sunny Plaza (13 x 11 x 12): the Center as a flat-roofed sandstone hall for warm country. Cut sandstone pilasters, arched
glass bays, a red terracotta band under a parapet, a striped red-and-white awning along the front, a raised lantern drum
in the middle of the roof under a low red hipped cap, and a light well over the waiting room.
Plaza II (13 x 12 x 25): a two-storey wing behind with its own taller parapet (a trade corner below, four beds above,
a ladder between) and a walled courtyard garden at the back with a Pasture Block.
"""
from kit import *

LOOK_FOLDER = "looks"

# --- Mountain Lodge ------------------------------------------------------------------------------------
LG_PLASTER = Mix((8, "white_concrete"), (2, "polished_diorite"), seed=281)
LG_SOCLE = Mix((6, "cobblestone"), (3, "stone"), (1, "mossy_cobblestone"), seed=282)
LG_POST = "spruce_log"
LG_BEAM = "stripped_spruce_log"
LG_ROOF = RED_NETHER_BRICK
LG_FLOOR = Mix((7, "spruce_planks"), (3, "stripped_spruce_wood"), seed=283)
CANDLES = ("red_candle", "pink_candle", "light_blue_candle", "lime_candle")


def lg_wall(b, x, y, z):
    b.set(x, y, z, LG_SOCLE.at(x, y, z) if y == 1 else LG_PLASTER.at(x, y, z))


def lg_hall(b):
    skirt(b, 1, 2, 11, 10, COBBLE)
    plinth(b, 1, 2, 11, 10, FOUNDATION_MIX, floor=LG_FLOOR)
    for y in range(1, 5):
        for x in range(1, 12):
            for z in range(2, 11):
                if x in (1, 11) or z in (2, 10):
                    lg_wall(b, x, y, z)
    front = [(x, 2) for x in (1, 5, 7, 11)]
    sides = [(x, 6) for x in (1, 11)]
    back = [(x, 10) for x in (1, 4, 8, 11)]
    posts(b, front + sides + back, 1, 4, LG_POST)
    beam_ring(b, 1, 2, 11, 10, 5, LG_BEAM)
    door(b, 6, 1, 2, "spruce_door", "south")
    pane(b, 6, 3, 2)
    # Shuttered windows with flower boxes in the front bays, plain ones on the sides and the back
    for x in (3, 9):
        window(b, x, 2, 2, "north", height=2, shutters="spruce_trapdoor",
               flowers=("spruce_trapdoor", ("potted_red_tulip",)))
    window(b, 1, 2, 4, "west", height=2, shutters="spruce_trapdoor", sill=SPRUCE)
    for z in (4, 8):
        window(b, 11, 2, z, "east", height=2, shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 6, 2, 10, "south", height=2, sill=SPRUCE)
    # Inside: the counter across the hall (spruce under a slab top), the Healing Machine in the middle, the PC east
    for x in range(3, 10):
        if x != 6:
            b.set(x, 1, 7, "spruce_planks")
            slab(b, x, 2, 7, "spruce_slab")
    b.set(6, 1, 7, "cobblemon:healing_machine", facing="north")
    b.set(10, 1, 7, "cobblemon:pc", facing="north", part="bottom", on=False, waterlogged=False)
    b.set(10, 2, 7, "cobblemon:pc", facing="north", part="top", on=False, waterlogged=False)
    for i, x in enumerate((2, 3, 4, 8, 9, 10)):
        b.set(x, 1, 9, "barrel", facing="north", open=False)
        b.set(x, 2, 9, CANDLES[i % len(CANDLES)], candles=1 + i % 3, lit=False, waterlogged=False)
    b.set(5, 1, 9, "potted_fern")
    b.set(7, 1, 9, "potted_fern")
    for z in (3, 4, 5):
        stairs(b, 2, 1, z, "spruce_stairs", "west")
        stairs(b, 10, 1, z, "spruce_stairs", "east")
    for z in range(3, 7):
        b.set(6, 1, z, "red_carpet")
    for z in (4, 8):  # tie beams for the lanterns, and for the builder to reach the roof from
        for x in range(2, 11):
            log(b, x, 5, z, LG_BEAM, axis="x")
        for x in (4, 8):
            lantern(b, x, 4, z, hanging=True)


def lg_main_roof(b):
    gable_roof(b, 0, 12, 1, 11, 5, LG_ROOF, axis="x", gable=LG_PLASTER, gable_at=(1, 11), eave_trim=SPRUCE)


def lg_porch_roof(b):
    gable_roof(b, 2, 10, 0, 6, 5, LG_ROOF, axis="z", gable=LG_PLASTER, gable_at=(0, 0), eave_trim=SPRUCE)


def lg_roof_details(b):
    for x in (1, 11):  # each gable end: a king post and a little window
        for y in range(6, 10):
            b.set(x, y, 6, LG_POST, axis="y")
        for z in (4, 8):
            pane(b, x, 7, z)
    # the porch: two posts and a beam under its gable, a king post in the gable, a lantern over the step
    posts(b, [(4, 0), (8, 0)], 1, 4, LG_POST)
    for x in range(4, 9):
        log(b, x, 4, 0, LG_BEAM, axis="x")
    for y in (5, 6, 7, 8):
        b.set(6, y, 0, LG_POST, axis="y")
    for x in (4, 8):  # a little window either side of the porch's king post
        pane(b, x, 6, 0)
    lantern(b, 6, 3, 0, hanging=True)
    for x in range(4, 9):
        b.set(x, 0, 0, "stone_bricks")
        b.set(x, 0, 1, "stone_bricks")


def lg_outside(b):
    # the west gable's chimney (replacing the wall there), a lamp at each front corner, hay and a barrel by the porch
    chimney(b, 1, 8, 1, 10, LG_SOCLE)
    for x in (0, 12):
        lamp_post(b, x, 0, 0, post="spruce_fence", height=3)
    b.set(2, 1, 1, "hay_block", axis="y")
    b.set(10, 1, 1, "barrel", facing="up", open=False)


def pokemon_center_lodge():
    """13 x 12 x 12: the Pokémon Center as a timber lodge under a steep red gable, a porch over the door."""
    b = Build(13, 12, 12)
    lg_hall(b)
    roofs(b, lg_main_roof, lg_porch_roof)
    lg_roof_details(b)
    lg_outside(b)
    b.fill_air()
    return b


def lg_wing(b):
    """A two-storey wing on the east (walls x 13-19, z 3-9) joined to the hall by a short passage: a trade corner with
    a Shop Counter below, four beds above, a ladder between, under a higher gable turned across the hall's."""
    skirt(b, 13, 3, 19, 9, COBBLE)
    for z in range(2, 11):  # between the hall and the wing: a strip of foundation, the hall's skirt there replaced
        b.set(12, 0, z, FOUNDATION_MIX.at(12, 0, z))
    plinth(b, 13, 3, 19, 9, FOUNDATION_MIX, floor=LG_FLOOR)
    for y in range(1, 9):
        for x in range(13, 20):
            for z in range(3, 10):
                if x in (13, 19) or z in (3, 9):
                    lg_wall(b, x, y, z)
    posts(b, [(13, 3), (16, 3), (19, 3), (13, 9), (16, 9), (19, 9), (19, 6), (13, 7)], 1, 8, LG_POST)
    beam_ring(b, 13, 3, 19, 9, 5, LG_BEAM)
    beam_ring(b, 13, 3, 19, 9, 9, LG_BEAM)
    box(b, 14, 5, 4, 18, 5, 8, "spruce_planks")
    # the passage from the hall: through its east wall (the bench there makes way, the window beside it is walled up)
    b.clear(10, 1, 5, 10, 1, 5)
    for y in (2, 3):
        lg_wall(b, 11, y, 4)
    b.clear(12, 1, 3, 12, 3, 5)
    for y in range(1, 4):
        lg_wall(b, 12, y, 4)
        lg_wall(b, 12, y, 6)
    lg_wall(b, 12, 3, 5)
    for z in (4, 5, 6):
        slab(b, 12, 4, z, "spruce_slab")
    b.set(12, 0, 5, "spruce_planks")
    b.clear(11, 1, 5, 11, 2, 5)
    b.clear(13, 1, 5, 13, 2, 5)
    door(b, 13, 1, 5, "spruce_door", "east")
    # downstairs: the Shop Counter facing the door, the counter either side, barrels, a lantern under the floor
    b.set(16, 1, 6, "aliveworkplace:shop_counter", facing="west")
    for z in (5, 7):
        b.set(16, 1, z, "spruce_planks")
        slab(b, 16, 2, z, "spruce_slab")
    b.set(18, 1, 8, "barrel", facing="up", open=False)
    b.set(17, 1, 8, "barrel", facing="up", open=False)
    lantern(b, 15, 4, 7, hanging=True)
    # the ladder up in the north-east corner
    b.clear(18, 5, 4, 18, 5, 4)
    for y in range(1, 6):
        b.set(18, y, 4, "ladder", facing="west", waterlogged=False)
    # upstairs: four beds, a chest, a lantern from the ridge beam
    for x, color in ((14, "red"), (15, "white"), (17, "red"), (18, "white")):
        b.bed(x, 6, 7, color, facing="south")
    b.set(16, 6, 8, "chest", facing="north", type="single", waterlogged=False)
    for z in range(4, 9):
        log(b, 16, 9, z, LG_BEAM, axis="z")
    lantern(b, 16, 8, 6, hanging=True)
    # windows in the bays: the east wall and the front on both storeys, the back upstairs
    for z in (5, 7):  # (19, 6) is a post: a window either side of it
        window(b, 19, 2, z, "east", height=2, shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 19, 7, 5, "east", height=1, sill=SPRUCE)
    window(b, 19, 7, 7, "east", height=1, sill=SPRUCE)
    for x in (14, 17):
        window(b, x, 2, 3, "north", width=2, height=2, shutters="spruce_trapdoor",
               flowers=("spruce_trapdoor", ("potted_blue_orchid", "potted_red_tulip")))
        window(b, x, 7, 3, "north", width=2, height=1, shutters="spruce_trapdoor", sill=SPRUCE)
        window(b, x, 7, 9, "south", width=2, height=1, shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 17, 2, 9, "south", width=2, height=2, sill=SPRUCE)


def lg_wing_roof(b):
    gable_roof(b, 12, 20, 2, 10, 9, LG_ROOF, axis="z", gable=LG_PLASTER, gable_at=(3, 9), eave_trim=SPRUCE)


def lg_wing_gables(b):
    for z in (3, 9):
        for y in range(10, 13):
            b.set(16, y, z, LG_POST, axis="y")
        for x in (15, 17):
            pane(b, x, 10, z)


def lg_pen(b):
    """A fenced pen behind the hall (z 12-17): grass, a path from the back door to the Pasture Block, a gate on the
    east, azaleas in the corners, flowers, lamps on the back corners."""
    b.set(6, 1, 10, "air")
    b.set(6, 2, 10, "air")
    b.set(6, 3, 10, LG_PLASTER.at(6, 3, 10))
    door(b, 6, 1, 10, "spruce_door", "north")
    stairs(b, 6, 0, 11, COBBLE, "north")
    box(b, 1, 0, 12, 11, 0, 17, "grass_block", snowy=False)
    for z in range(12, 15):
        b.set(6, 0, z, "dirt_path")
    for x in range(1, 12):
        fence(b, x, 1, 17, "spruce_fence")
    for z in range(12, 18):
        fence(b, 1, 1, z, "spruce_fence")
        fence(b, 11, 1, z, "spruce_fence")
    for x in (2, 3, 4, 8, 9, 10):
        fence(b, x, 1, 12, "spruce_fence")
    b.set(11, 1, 14, "spruce_fence_gate", facing="east", open=False, in_wall=False, powered=False)
    b.set(6, 1, 15, "cobblemon:pasture", facing="north", part="bottom", on=False, waterlogged=False)
    b.set(6, 2, 15, "cobblemon:pasture", facing="north", part="top", on=False, waterlogged=False)
    for x, z in ((2, 16), (10, 16)):
        b.set(x, 1, z, "azalea")
    for x, z, plant in ((3, 16, "poppy"), (4, 16, "cornflower"), (8, 16, "cornflower"), (9, 16, "poppy"),
                        (2, 13, "oxeye_daisy"), (10, 13, "oxeye_daisy")):
        b.set(x, 1, z, plant)
    for x in (1, 11):
        lantern(b, x, 2, 17)


def pokemon_center_lodge_2():
    """Upgrade of the Mountain Lodge: a two-storey wing on the east (a Shop Counter, four beds) and a pen with a
    Pasture Block behind. 21 x 14 x 18."""
    b = pokemon_center_lodge().grow(21, 14, 18)
    lg_wing(b)
    roofs(b, lg_main_roof, lg_porch_roof, lg_wing_roof)
    lg_wing_gables(b)
    lg_pen(b)
    b.fill_air()
    return b


# --- Sunny Plaza ----------------------------------------------------------------------------------------
PZ_WALL = Mix((8, "smooth_sandstone"), (2, "sandstone"), seed=284)
PZ_PILASTER = "cut_sandstone"
PZ_BAND = "red_terracotta"
PZ_ROOF = RED_NETHER_BRICK
PZ_FLOOR = Mix((6, "white_terracotta"), (4, "smooth_sandstone"), seed=285)
PZ_BASE = Mix((7, "sandstone"), (3, "cut_sandstone"), seed=286)


def pz_walls(b, x0, z0, x1, z1, y0, y1, pilasters):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    b.set(x, y, z, PZ_WALL.at(x, y, z))
    posts(b, pilasters, y0, y1, PZ_PILASTER)


def pz_parapet(b, x0, z0, x1, z1, y, piers):
    """The red band at y, a slab parapet on it with cut sandstone piers, a flat roof inside."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                b.set(x, y, z, PZ_BAND)
                slab(b, x, y + 1, z, "smooth_sandstone_slab")
            else:
                b.set(x, y, z, "smooth_sandstone")
    for x, z in piers:
        b.set(x, y + 1, z, PZ_PILASTER)


def pz_arch(b, x0, z, y0=1):
    """A glass bay three wide and three high with an arched head (upside-down stairs in its top corners)."""
    for x in range(x0, x0 + 3):
        for y in range(y0, y0 + 3):
            pane(b, x, y, z)
    stairs(b, x0, y0 + 2, z, SMOOTH_SANDSTONE, "west", top=True)
    stairs(b, x0 + 2, y0 + 2, z, SMOOTH_SANDSTONE, "east", top=True)
    b.set(x0 + 1, y0 + 3, z, "chiseled_sandstone")


def pz_hall(b):
    skirt(b, 1, 2, 11, 10, SANDSTONE)
    plinth(b, 1, 2, 11, 10, PZ_BASE, floor=PZ_FLOOR)
    front = [(x, 2) for x in (1, 5, 7, 11)]
    sides = [(x, 6) for x in (1, 11)]
    back = [(x, 10) for x in (1, 4, 8, 11)]
    pz_walls(b, 1, 2, 11, 10, 1, 4, front + sides + back)
    pz_parapet(b, 1, 2, 11, 10, 5, front + sides + back)
    for x0 in (2, 8):
        pz_arch(b, x0, 2)
    door(b, 6, 1, 2, "birch_door", "south")
    pane(b, 6, 3, 2)
    # a pediment over the door: the parapet raised, a chiseled block in the middle
    for x in (5, 7):
        b.set(x, 6, 2, PZ_PILASTER)
    b.set(6, 6, 2, PZ_PILASTER)
    b.set(6, 7, 2, "chiseled_sandstone")
    slab(b, 5, 7, 2, "smooth_sandstone_slab")
    slab(b, 7, 7, 2, "smooth_sandstone_slab")
    # side and back windows, narrow and tall, in their bays
    for z in (4, 8):
        window(b, 1, 2, z, "west", height=2, sill=SMOOTH_SANDSTONE, lintel=SMOOTH_SANDSTONE)
        window(b, 11, 2, z, "east", height=2, sill=SMOOTH_SANDSTONE, lintel=SMOOTH_SANDSTONE)
    for x in (2, 6, 10):
        window(b, x, 2, 10, "south", height=2, sill=SMOOTH_SANDSTONE, lintel=SMOOTH_SANDSTONE)
    # inside: a terracotta counter under a sandstone top, the Healing Machine in the middle, the PC at the east end
    for x in range(3, 10):
        if x != 6:
            b.set(x, 1, 7, "white_terracotta")
            slab(b, x, 2, 7, "cut_sandstone_slab")
    b.set(6, 1, 7, "cobblemon:healing_machine", facing="north")
    b.set(10, 1, 7, "cobblemon:pc", facing="north", part="bottom", on=False, waterlogged=False)
    b.set(10, 2, 7, "cobblemon:pc", facing="north", part="top", on=False, waterlogged=False)
    for i, x in enumerate((2, 3, 4, 8, 9, 10)):
        b.set(x, 1, 9, "barrel", facing="north", open=False)
        b.set(x, 2, 9, CANDLES[(i + 1) % len(CANDLES)], candles=2 + i % 2, lit=False, waterlogged=False)
    b.set(5, 1, 9, "potted_cactus")
    b.set(7, 1, 9, "potted_cactus")
    for z in (3, 4, 5):
        stairs(b, 2, 1, z, "birch_stairs", "west")
        stairs(b, 10, 1, z, "birch_stairs", "east")
    for z in range(3, 7):
        b.set(6, 1, z, "red_carpet")
    for x, z in ((3, 4), (9, 4), (3, 8), (9, 8)):
        lantern(b, x, 4, z, hanging=True)


def pz_drum(b):
    """A lantern drum in the middle of the roof over a light well into the waiting room, under a low red hipped cap."""
    b.clear(5, 5, 4, 7, 5, 6)
    for x in range(4, 9):
        for z in range(3, 8):
            if x in (4, 8) or z in (3, 7):
                for y in (6, 7):
                    if x in (4, 8) and z in (3, 7):
                        b.set(x, y, z, PZ_PILASTER)
                    else:
                        pane(b, x, y, z)
    hip_roof(b, 3, 9, 2, 8, 8, PZ_ROOF, rings=2)
    box(b, 5, 9, 4, 7, 9, 6, "smooth_quartz")
    slab(b, 6, 10, 5, "smooth_quartz_slab")
    lantern(b, 6, 8, 5, hanging=True)


def pz_front(b):
    """A striped awning along the front over the bays, steps to the door, cacti in pots, a lamp at each corner."""
    for x in range(1, 12):
        stairs(b, x, 4, 1, PZ_ROOF if x % 2 else SMOOTH_QUARTZ, "north")
    for x in range(4, 9):
        b.set(x, 0, 0, "smooth_sandstone")
        b.set(x, 0, 1, "smooth_sandstone")
    for x in (4, 8):
        lantern(b, x, 3, 1, hanging=True)
    for x in (1, 3, 9, 11):
        b.set(x, 0, 0, "sandstone")
        b.set(x, 1, 0, "potted_cactus" if x in (1, 11) else "potted_dead_bush")
    for x in (0, 12):
        lamp_post(b, x, 0, 0, post="birch_fence", height=3)


def pokemon_center_plaza():
    """13 x 11 x 12: the Pokémon Center as a flat-roofed sandstone hall, a striped awning and a red-capped drum."""
    b = Build(13, 11, 12)
    pz_hall(b)
    pz_drum(b)
    pz_front(b)
    b.fill_air()
    return b


def pz_wing(b):
    """Two storeys behind the hall (walls x 2-10, z 11-17): the Shop Counter's trade corner below, four beds above."""
    skirt(b, 2, 11, 10, 17, SANDSTONE)
    for x in range(1, 12):
        b.set(x, 0, 10, PZ_BASE.at(x, 0, 10))
    for z in range(11, 18):
        for x in (2, 10):
            b.set(x, 0, z, PZ_BASE.at(x, 0, z))
    for x in range(2, 11):
        b.set(x, 0, 17, PZ_BASE.at(x, 0, 17))
    box(b, 3, 0, 11, 9, 0, 16, PZ_FLOOR)
    pilasters = [(2, 11), (10, 11), (2, 14), (10, 14), (2, 17), (6, 17), (10, 17)]
    pz_walls(b, 2, 11, 10, 17, 1, 9, pilasters)
    b.clear(3, 1, 11, 9, 4, 11)  # downstairs the hall's back wall is the wing's north wall
    box(b, 3, 0, 11, 9, 0, 11, PZ_FLOOR)
    # the floor between the storeys, a band outside at its line
    for z in range(11, 18):
        for x in (2, 10):
            b.set(x, 5, z, PZ_BAND)
    for x in range(2, 11):
        b.set(x, 5, 17, PZ_BAND)
    box(b, 3, 5, 11, 9, 5, 16, "birch_planks")
    pz_parapet(b, 2, 11, 10, 17, 10, pilasters)
    # the hall's back windows would look into the wing's walls: walled up; the middle one becomes a door
    for x in (2, 10):
        for y in (2, 3):
            b.set(x, y, 10, PZ_WALL.at(x, y, 10))
    b.set(6, 3, 10, PZ_WALL.at(6, 3, 10))
    b.clear(6, 1, 10, 6, 2, 10)
    door(b, 6, 1, 10, "birch_door", "south")
    # downstairs: the Shop Counter facing the door, barrels, a bench, the ladder up
    b.set(6, 1, 14, "aliveworkplace:shop_counter", facing="north")
    for x in (5, 7):
        b.set(x, 1, 14, "white_terracotta")
        slab(b, x, 2, 14, "cut_sandstone_slab")
    b.set(9, 1, 16, "barrel", facing="up", open=False)
    b.set(8, 1, 16, "barrel", facing="up", open=False)
    stairs(b, 9, 1, 12, "birch_stairs", "east")
    stairs(b, 9, 1, 13, "birch_stairs", "east")
    lantern(b, 6, 4, 12, hanging=True)
    b.clear(3, 5, 16, 3, 5, 16)
    for y in range(1, 6):
        b.set(3, y, 16, "ladder", facing="east", waterlogged=False)
    # upstairs: four beds, a chest, lanterns from the roof
    for x in (4, 8):
        b.bed(x, 6, 12, "red", facing="south")
    b.bed(5, 6, 12, "white", facing="south")
    b.bed(7, 6, 12, "red", facing="south")
    b.set(6, 6, 16, "chest", facing="north", type="single", waterlogged=False)
    for x in (4, 8):
        lantern(b, x, 9, 15, hanging=True)
    # windows: arched bays on the sides upstairs and down, two on the back
    for z in (12, 15):  # not z 16: the ladder hangs on the west wall there
        for y in (2, 7):
            window(b, 2, y, z, "west", height=2, sill=SMOOTH_SANDSTONE, lintel=SMOOTH_SANDSTONE)
            window(b, 10, y, z, "east", height=2, sill=SMOOTH_SANDSTONE, lintel=SMOOTH_SANDSTONE)
    for x in (4, 8):
        for y in (2, 7):
            window(b, x, y, 17, "south", height=2, sill=SMOOTH_SANDSTONE, lintel=SMOOTH_SANDSTONE)


def pz_courtyard(b):
    """A walled courtyard behind the wing (z 18-24): sandstone walls with piers, grass, a path from the back door to the
    Pasture Block, flower beds, a gate on the west, lanterns on the back piers."""
    for x in range(1, 12):
        for z in range(18, 25):
            if x in (1, 11) or z == 24:
                b.set(x, 0, z, PZ_BASE.at(x, 0, z))
                wall_block(b, x, 1, z, "sandstone_wall")
    box(b, 2, 0, 18, 10, 0, 23, "grass_block", snowy=False)
    for z in range(18, 21):
        b.set(6, 0, z, "smooth_sandstone")
    b.set(1, 1, 21, "birch_fence_gate", facing="west", open=False, in_wall=True, powered=False)
    b.set(6, 1, 21, "cobblemon:pasture", facing="north", part="bottom", on=False, waterlogged=False)
    b.set(6, 2, 21, "cobblemon:pasture", facing="north", part="top", on=False, waterlogged=False)
    for x, z in ((2, 23), (10, 23)):
        b.set(x, 1, z, "flowering_azalea")
    for x, z, plant in ((3, 23, "orange_tulip"), (4, 23, "allium"), (8, 23, "allium"), (9, 23, "orange_tulip"),
                        (2, 18, "dandelion"), (10, 18, "dandelion")):
        b.set(x, 1, z, plant)
    for x in (1, 11):
        b.set(x, 1, 24, PZ_PILASTER)
        lantern(b, x, 2, 24)
    b.set(6, 2, 17, "air")
    b.set(6, 1, 17, "air")
    door(b, 6, 1, 17, "birch_door", "north")
    stairs(b, 6, 0, 18, SMOOTH_SANDSTONE, "north")
    for x in range(5, 8):  # a striped awning over the back door, a lantern under it
        stairs(b, x, 4, 18, PZ_ROOF if x % 2 else SMOOTH_QUARTZ, "south")
    lantern(b, 6, 3, 18, hanging=True)


def pokemon_center_plaza_2():
    """Upgrade of the Sunny Plaza: a two-storey wing behind (a Shop Counter, four beds upstairs) and a walled courtyard
    with a Pasture Block. 13 x 12 x 25."""
    b = pokemon_center_plaza().grow(13, 12, 25)
    pz_wing(b)
    pz_courtyard(b)
    b.fill_air()
    return b


# The looks, as (look, tier I, tier II): saved to looks/<look>/pokemon_center(_2).
POKEMON_CENTER_LOOKS = (("lodge", pokemon_center_lodge, pokemon_center_lodge_2),
                        ("plaza", pokemon_center_plaza, pokemon_center_plaza_2))
