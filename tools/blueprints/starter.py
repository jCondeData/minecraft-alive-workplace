"""The starter blueprints: every build a Blueprint Table offers from the start, and their upgrades."""
from kit import *


# --- Starter Cottage: a timber-framed cottage ------------------------------------------------------------
COTTAGE_FRAME = "spruce_log"
COTTAGE_INFILL = "birch_planks"
COTTAGE_ROOF = SPRUCE


def cottage_ground_floor(b):
    """The cottage's ground floor (walls x 1-9, z 2-8, four high), shared by every tier: stone plinth, spruce frame with
    birch infill, tall windows with shutters and flower boxes, the door with a step, furniture."""
    plinth(b, 1, 2, 9, 8, STONE_MIX, floor="spruce_planks")
    walls(b, 1, 2, 9, 8, 1, 4, COTTAGE_INFILL)
    posts(b, [(1, 2), (9, 2), (1, 8), (9, 8), (4, 2), (6, 2), (5, 8), (1, 5), (9, 5)], 1, 4, COTTAGE_FRAME)
    beam_ring(b, 1, 2, 9, 8, 5, COTTAGE_FRAME)
    door(b, 5, 1, 2, "oak_door", "south")
    stairs(b, 5, 0, 1, COBBLE, "south")
    log(b, 5, 3, 2, COTTAGE_FRAME, axis="x")  # the lintel over the door
    for x in (2, 7):
        window(b, x, 2, 2, "north", width=2, height=2, shutters="spruce_trapdoor",
               flowers=("spruce_trapdoor", ["potted_red_tulip", "potted_oxeye_daisy"] if x == 2 else ["potted_cornflower", "potted_allium"]))
    # Lanterns on the flower boxes nearest the door (not hung from the eaves: an upgrade that replaces the roof
    # would knock them down)
    lantern(b, 3, 2, 1)
    lantern(b, 7, 2, 1)
    for x in (3, 7):
        window(b, x, 2, 8, "south", height=2, shutters="spruce_trapdoor", sill=SPRUCE)
    for z in (4, 6):
        window(b, 9, 2, z, "east", height=2, shutters="spruce_trapdoor", sill=SPRUCE)
    # Inside: a bed, a kitchen corner by the chimney, a table and chair, a rug
    b.bed(7, 1, 6, "red", facing="south")
    b.set(2, 1, 7, "crafting_table")
    b.set(3, 1, 7, "chest", facing="north", type="single", waterlogged=False)
    b.set(2, 1, 5, "furnace", facing="east", lit=False)
    b.set(2, 1, 3, "barrel", facing="up", open=False)
    fence(b, 3, 1, 4, "spruce_fence")
    b.set(3, 2, 4, "spruce_pressure_plate", powered=False)
    stairs(b, 3, 1, 3, SPRUCE, "north")
    box(b, 4, 1, 5, 6, 1, 6, "brown_carpet")


def starter_cottage():
    """11 x 12 x 10: a timber-framed cottage under an overhanging spruce roof, with a chimney up the west gable."""
    b = Build(11, 12, 10)
    cottage_ground_floor(b)
    for x in range(2, 9):  # a tie beam across the middle to hang the lights from
        log(b, x, 5, 5, COTTAGE_FRAME, axis="x")
    lantern(b, 3, 4, 5, hanging=True)
    lantern(b, 7, 4, 5, hanging=True)
    gable_roof(b, 0, 10, 1, 9, 5, COTTAGE_ROOF, axis="x", gable=COTTAGE_INFILL, gable_at=(1, 9))
    for x in (1, 9):
        log(b, x, 6, 5, COTTAGE_FRAME)
        pane(b, x, 7, 5)
    chimney(b, 0, 5, 0, 10, STONE_MIX)
    b.fill_air()
    return b


def cottage_upper_floor(b):
    """Cottage II's upper storey: jettied out a block over the front and back walls on brackets, two high under the roof,
    with a ladder up from the ground floor."""
    # The jetty: floor beams out over the front and back, upside-down stairs under them as brackets
    beam_ring(b, 1, 1, 9, 9, 5, COTTAGE_FRAME)
    box(b, 2, 5, 2, 8, 5, 8, "spruce_planks")
    for x in (1, 3, 7, 9):
        stairs(b, x, 4, 1, SPRUCE, "south", top=True)
        stairs(b, x, 4, 9, SPRUCE, "north", top=True)
    lantern(b, 4, 4, 1, hanging=True)
    lantern(b, 6, 4, 1, hanging=True)
    walls(b, 1, 1, 9, 9, 6, 7, COTTAGE_INFILL)
    posts(b, [(1, 1), (9, 1), (1, 9), (9, 9), (5, 1), (5, 9), (1, 5), (9, 5)], 6, 7, COTTAGE_FRAME)
    beam_ring(b, 1, 1, 9, 9, 8, COTTAGE_FRAME)
    for x in (2, 7):
        window(b, x, 6, 1, "north", width=2, height=2, shutters="spruce_trapdoor")
        window(b, x, 6, 9, "south", width=2, height=2, shutters="spruce_trapdoor")
    for z in (2, 8):
        window(b, 9, 6, z, "east", height=2)
    # The ladder up, against the post between the east windows
    for y in range(1, 7):
        b.set(8, y, 5, "ladder", facing="west", waterlogged=False)
    # Upstairs: a second bed, bookshelves, a chest, a rug and a lantern
    b.bed(2, 6, 7, "light_blue", facing="south")
    b.set(2, 6, 2, "bookshelf")
    b.set(3, 6, 2, "bookshelf")
    b.set(2, 7, 2, "potted_fern")
    b.set(4, 6, 8, "chest", facing="north", type="single", waterlogged=False)
    box(b, 4, 6, 4, 6, 6, 6, "light_blue_carpet")


def starter_cottage_2():
    """Upgrade of the Starter Cottage (same origin and front): the ground floor is identical, so a builder only takes the
    roof off and adds a jettied upper storey, a new roof and a taller chimney. 11 x 16 x 11."""
    b = Build(11, 16, 11)
    cottage_ground_floor(b)
    cottage_upper_floor(b)
    gable_roof(b, 0, 10, 0, 10, 8, COTTAGE_ROOF, axis="x", gable=COTTAGE_INFILL, gable_at=(1, 9))
    for x in (1, 9):
        log(b, x, 9, 5, COTTAGE_FRAME)
        pane(b, x, 10, 5)
    lantern(b, 5, 9, 5, hanging=False)
    b.set(5, 8, 5, "spruce_planks")
    chimney(b, 0, 5, 0, 14, STONE_MIX)
    b.fill_air()
    return b


def starter_cottage_3():
    """Upgrade of Starter Cottage II (same origin and front): a one-storey kitchen wing grows out of the east wall, through
    a doorway where a window was, with its own door, a bench out front and a lamp. 17 x 16 x 11."""
    b = starter_cottage_2().grow(17, 16, 11)
    # The east windows' shutters and sills come off; one window becomes the doorway, the other looks into the kitchen
    b.clear(10, 1, 3, 10, 3, 7)
    for y in (1, 2, 3):
        b.set(9, y, 4, "air")
    b.set(9, 3, 4, COTTAGE_INFILL)
    # Wing walls x 10-15, z 3-7 (the cottage's east wall is its west side), four high like the cottage
    plinth(b, 9, 3, 15, 7, STONE_MIX, floor="spruce_planks")
    b.set(9, 0, 4, "spruce_planks")
    for y in range(1, 5):
        for x in range(10, 16):
            b.set(x, y, 3, COTTAGE_INFILL)
            b.set(x, y, 7, COTTAGE_INFILL)
        for z in range(4, 7):
            b.set(15, y, z, COTTAGE_INFILL)
    posts(b, [(15, 3), (15, 7), (12, 3), (12, 7)], 1, 4, COTTAGE_FRAME)
    for x in range(10, 16):
        log(b, x, 5, 3, COTTAGE_FRAME, axis="x")
        log(b, x, 5, 7, COTTAGE_FRAME, axis="x")
    for z in range(4, 7):
        log(b, 15, 5, z, COTTAGE_FRAME, axis="z")
    door(b, 13, 1, 3, "oak_door", "south")
    stairs(b, 13, 0, 2, COBBLE, "south")
    window(b, 10, 2, 3, "north", width=2, height=2, shutters="spruce_trapdoor", flowers=("spruce_trapdoor", ["potted_poppy", "potted_dandelion"]))
    window(b, 15, 2, 5, "east", height=2, shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 13, 2, 7, "south", width=2, height=2, shutters="spruce_trapdoor", sill=SPRUCE)
    # Its roof, lower than the cottage's, running east from the cottage wall
    gable_roof(b, 10, 16, 2, 8, 5, COTTAGE_ROOF, axis="x", gable=COTTAGE_INFILL, gable_at=(15, 15))
    log(b, 15, 6, 5, COTTAGE_FRAME)
    # The kitchen: a smoker, a barrel and a cauldron, a table with two seats, a light on a beam
    b.set(14, 1, 6, "smoker", facing="west", lit=False)
    b.set(14, 1, 4, "barrel", facing="up", open=False)
    b.set(13, 1, 6, "cauldron")
    fence(b, 11, 1, 5, "spruce_fence")
    b.set(11, 2, 5, "spruce_pressure_plate", powered=False)
    stairs(b, 10, 1, 5, SPRUCE, "west")
    stairs(b, 12, 1, 5, SPRUCE, "east")
    for x in range(10, 15):
        log(b, x, 5, 5, COTTAGE_FRAME, axis="x")
    lantern(b, 12, 4, 5, hanging=True)
    # A bench against the wing's front wall and a lamp by the corner
    stairs(b, 10, 0, 2, SPRUCE, "south")
    stairs(b, 11, 0, 2, SPRUCE, "south")
    lamp_post(b, 16, 0, 2, "spruce_fence", height=2)
    b.fill_air()
    return b


# --- Market Stall: a timber stall under a striped awning ------------------------------------------------
def stall(b, x0, colors, goods):
    """One stall with its posts at x0 and x0 + 6: a plank floor, a counter across the front panelled with trapdoors, a
    sloping striped awning, a back shelf. {@code goods}: what stands on the counter (x0+1 .. x0+5)."""
    x1 = x0 + 6
    box(b, x0, 0, 2, x1, 0, 5, "spruce_planks")
    posts(b, [(x0, 2), (x1, 2)], 1, 4, "spruce_log")
    posts(b, [(x0, 5), (x1, 5)], 1, 5, "spruce_log")
    # The counter: stripped logs on barrels, trapdoor panels in front
    for x in range(x0 + 1, x1):
        if x in (x0 + 1, x1 - 1):
            b.set(x, 1, 2, "barrel", facing="up", open=False)
        else:
            log(b, x, 1, 2, "stripped_spruce_log", axis="x")
        trapdoor(b, x, 1, 1, "spruce_trapdoor", "north", open_=True)
    for i, g in enumerate(goods):
        if g:
            b.set(x0 + 1 + i, 2, 2, g) if isinstance(g, str) else b.set(x0 + 1 + i, 2, 2, g[0], **g[1])
    # The awning: sloping up from the front, striped
    for x in range(x0, x1 + 1):
        c = colors[(x - x0) % 2]
        b.set(x, 4, 1, c)
        b.set(x, 5, 2, c)
        b.set(x, 5, 3, c)
        b.set(x, 6, 4, c)
        b.set(x, 6, 5, c)
    # The back shelf: a beam with stock under it
    for x in range(x0 + 1, x1):
        log(b, x, 4, 5, "spruce_log", axis="x")
        slab(b, x, 2, 5, SPRUCE, top=True)
    lantern(b, x0 + 3, 4, 3, hanging=True)
    b.set(x0 + 3, 5, 3, colors[1])


def market_stall():
    """8 x 7 x 6: a timber stall selling the harvest under a red-and-white awning, crates stacked beside it."""
    b = Build(8, 7, 6)
    stall(b, 1, ("red_wool", "white_wool"), [None, "melon", ("lantern", {"hanging": False, "waterlogged": False}), "pumpkin", None])
    b.set(2, 1, 5, "hay_block", axis="y")
    b.set(3, 1, 5, "composter", level=0)
    b.set(5, 1, 5, "hay_block", axis="x")
    b.set(6, 1, 5, "barrel", facing="up", open=False)
    b.set(3, 3, 5, "potted_red_tulip")
    b.set(5, 3, 5, "potted_dandelion")
    # Crates by the side, and a lamp
    b.set(0, 0, 4, "barrel", facing="up", open=False)
    b.set(0, 0, 5, "barrel", facing="up", open=False)
    b.set(0, 1, 5, "barrel", facing="up", open=False)
    b.set(0, 0, 3, "pumpkin")
    lamp_post(b, 0, 0, 1, "spruce_fence", height=2)
    b.fill_air()
    return b


# --- Lookout Tower: a stone watchtower -------------------------------------------------------------------
def tower_shaft(b):
    """The tower (walls x 1-5, z 1-5): a flared stone base, stone brick walls with a timber band and arrow slits, a
    platform corbelled out on upside-down stairs with a crenellated parapet, a ladder up the back wall."""
    skirt(b, 1, 1, 5, 5, STONE_BRICK)
    plinth(b, 1, 1, 5, 5, FOUNDATION_MIX, floor="cobblestone")
    walls(b, 1, 1, 5, 5, 1, 10, BRICK_WALL_MIX)
    beam_ring(b, 1, 1, 5, 5, 5, "spruce_log")
    for y in (3, 4, 7, 8):  # arrow slits (the ladder side stays solid)
        for x, z in ((3, 1), (1, 3), (5, 3)):
            b.set(x, y, z, "air")
    b.set(3, 3, 1, BRICK_WALL_MIX)
    b.set(3, 4, 1, BRICK_WALL_MIX)
    door(b, 3, 1, 1, "spruce_door", "south")
    stairs(b, 3, 3, 0, STONE_BRICK, "south", top=True)  # a hood over the door
    for y in range(1, 12):
        b.set(3, y, 4, "ladder", facing="north", waterlogged=False)
    # The platform: corbels, a plank floor out to the edge, a parapet of walls with solid corners
    for i in range(7):
        for x, z, f in ((i, 0, "south"), (i, 6, "north"), (0, i, "east"), (6, i, "west")):
            if b.get(x, 10, z) is None:
                stairs(b, x, 10, z, STONE_BRICK, f, top=True)
    walls(b, 0, 0, 6, 6, 11, 11, "stone_bricks")
    box(b, 1, 11, 1, 5, 11, 5, "spruce_planks")
    b.set(3, 11, 4, "ladder", facing="north", waterlogged=False)
    for i in range(7):
        for x, z in ((i, 0), (i, 6), (0, i), (6, i)):
            wall_block(b, x, 12, z, "stone_brick_wall")
    for x, z in ((0, 0), (6, 0), (0, 6), (6, 6), (3, 0), (0, 3), (6, 3), (3, 6)):
        b.set(x, 12, z, "stone_bricks")
    b.set(3, 3, 2, "wall_torch", facing="south")


def lookout_tower():
    """7 x 14 x 7: a stone watchtower with a timber band, arrow slits and a crenellated platform with lanterns."""
    b = Build(7, 14, 7)
    tower_shaft(b)
    for x, z in ((0, 0), (6, 0), (0, 6), (6, 6)):
        lantern(b, x, 13, z)
    b.fill_air()
    return b


# --- Market Stall, upgraded: a second stall alongside with a Shop Counter -----------------------------
def market_stall_2():
    """Upgrade of the Market Stall: a second stall shares its east posts, under a blue awning, with a Shop Counter in
    the middle of its counter (a villager moves in as shopkeeper; the first player to open it owns it). 15 x 7 x 6."""
    b = market_stall().grow(15, 7, 6)
    stall(b, 7, ("blue_wool", "white_wool"), ["potted_cornflower", None, None, None, ("lantern", {"hanging": False, "waterlogged": False})])
    b.set(10, 1, 2, "aliveworkplace:shop_counter", facing="south")
    b.set(10, 1, 1, "air")
    # The shopkeeper's stool and stock on the shelf
    stairs(b, 10, 1, 4, SPRUCE, "south")
    b.set(8, 1, 5, "barrel", facing="up", open=False)
    b.set(12, 1, 5, "barrel", facing="up", open=False)
    b.set(9, 3, 5, "potted_azure_bluet")
    # Where the first stall's crates were, a lamp at the new corner
    lamp_post(b, 14, 0, 1, "spruce_fence", height=2)
    b.fill_air()
    return b


# --- Market Stall III: a storeroom shed behind the two stalls ------------------------------------------
def market_stall_3():
    """Upgrade of Market Stall II: a timber storeroom behind the stalls, through a door behind the second stall, with
    chests and a workbench for the stock (within reach of the Shop Counter, so the shopkeeper sells from it too).
    15 x 9 x 13."""
    b = market_stall_2().grow(15, 9, 13)
    plinth(b, 1, 6, 13, 11, STONE_MIX, floor="spruce_planks")
    walls(b, 1, 6, 13, 11, 1, 3, "birch_planks")
    posts(b, [(1, 6), (13, 6), (1, 11), (13, 11), (7, 11), (4, 11), (10, 11)], 1, 3, "spruce_log")
    beam_ring(b, 1, 6, 13, 11, 4, "spruce_log")
    # It stands right behind the stalls: their back posts are its front corners
    door(b, 10, 1, 6, "spruce_door", "south")
    for x in (2, 5, 8, 11):
        b.set(x, 1, 10, "chest", facing="north", type="single", waterlogged=False)
    b.set(3, 1, 10, "crafting_table")
    b.set(12, 1, 10, "barrel", facing="up", open=False)
    b.set(2, 1, 7, "hay_block", axis="y")
    for z in (8, 9):
        window(b, 1, 2, z, "west", sill=SPRUCE)
        window(b, 13, 2, z, "east", sill=SPRUCE)
    for x in (5, 9):
        window(b, x, 2, 11, "south", width=1, shutters="spruce_trapdoor")
    gable_roof(b, 0, 14, 6, 12, 4, SPRUCE, axis="x", gable="birch_planks", gable_at=(1, 13))
    lantern(b, 7, 3, 8, hanging=True)
    b.set(7, 4, 8, "spruce_planks")
    b.fill_air()
    return b


# --- Lookout Tower, upgraded: a guard post with a bell under a pointed roof --------------------------------
def lookout_tower_2():
    """Upgrade of the Lookout Tower: a Guard Post and a chest for gear at the foot of the ladder (a villager moves in as a
    guard), and a steep pointed roof on corner posts over the platform, with a bell to call the guards and a lightning
    rod on the spire. 7 x 23 x 7."""
    b = lookout_tower().grow(7, 23, 7)
    b.set(2, 1, 3, "aliveworkplace:guard_post", facing="east")
    b.set(4, 1, 3, "chest", facing="west", type="single", waterlogged=False)
    for x, z in ((0, 0), (6, 0), (0, 6), (6, 6)):
        for y in (13, 14):
            log(b, x, y, z, "spruce_log")
    top = hip_roof(b, 0, 6, 0, 6, 15, DARK_OAK, steep=True)
    b.set(3, top + 1, 3, "lightning_rod", facing="up", powered=False, waterlogged=False)
    b.set(3, 12, 3, "bell", attachment="floor", facing="north", powered=False)
    for x, z in ((1, 1), (5, 1), (1, 5), (5, 5)):
        lantern(b, x, 14, z, hanging=True)
    b.fill_air()
    return b


# --- Lookout Tower III: a guardhouse beside the tower --------------------------------------------------
def lookout_tower_3():
    """Upgrade of Lookout Tower II: a stone-and-timber guardhouse against the tower's east side, through a doorway from the
    tower, with a second Guard Post, a chest for gear and two bunks (a second villager moves in as a guard). 14 x 23 x 7."""
    b = lookout_tower_2().grow(14, 23, 7)
    b.clear(6, 0, 0, 6, 0, 6)  # the base's flare on that side
    plinth(b, 6, 1, 12, 5, FOUNDATION_MIX, floor="spruce_planks")
    for y in range(1, 3):
        for x in range(7, 13):
            b.set(x, y, 1, BRICK_WALL_MIX)
            b.set(x, y, 5, BRICK_WALL_MIX)
        for z in range(2, 5):
            b.set(12, y, z, BRICK_WALL_MIX)
    for x in range(7, 13):
        b.set(x, 3, 1, "birch_planks")
        b.set(x, 3, 5, "birch_planks")
    for z in range(2, 5):
        b.set(12, 3, z, "birch_planks")
    posts(b, [(12, 1), (12, 5), (9, 1), (9, 5)], 1, 3, "spruce_log")
    for x in range(6, 13):
        log(b, x, 4, 1, "spruce_log", axis="x")
        log(b, x, 4, 5, "spruce_log", axis="x")
    for z in range(2, 5):
        log(b, 12, 4, z, "spruce_log", axis="z")
    for z in range(1, 6):
        b.set(6, 1, z, FOUNDATION_MIX)
        b.set(6, 2, z, FOUNDATION_MIX)
        b.set(6, 3, z, FOUNDATION_MIX)
    # Through from the tower, and a door of its own
    for x in (5, 6):
        b.set(x, 1, 2, "air")
        b.set(x, 2, 2, "air")
    door(b, 10, 1, 1, "spruce_door", "south")
    stairs(b, 10, 0, 0, STONE_BRICK, "south")
    window(b, 7, 2, 1, "north", width=2, shutters="spruce_trapdoor", sill=STONE_BRICK)
    window(b, 12, 2, 3, "east", shutters="spruce_trapdoor", sill=STONE_BRICK)
    window(b, 10, 2, 5, "south", width=2, sill=STONE_BRICK)
    gable_roof(b, 7, 13, 0, 6, 4, DARK_OAK, axis="x", gable="birch_planks", gable_at=(12, 12))
    # The second guard's post, gear and two bunks
    b.set(7, 1, 3, "aliveworkplace:guard_post", facing="east")
    b.set(7, 1, 4, "chest", facing="east", type="single", waterlogged=False)
    b.bed(10, 1, 3, "white", facing="south")
    b.bed(11, 1, 3, "white", facing="south")
    b.set(11, 1, 2, "anvil", facing="north")
    lantern(b, 9, 3, 3, hanging=True)
    b.set(9, 4, 3, "spruce_planks")
    b.fill_air()
    return b


# --- Healing Center: white walls under a stepped red roof, a heart sign over the door --------------------------
def step_roof(b, x0, x1, z0, z1, y, color="red_concrete"):
    """The concrete roof of the Healing Center and the Supply Shop over (x0, z0)-(x1, z1) at y: an overhanging slab and
    three steps up (only their rims where the next step covers them), the top one edged in white."""
    box(b, x0, y, z0, x1, y, z1, color)
    walls(b, x0 + 1, z0 + 1, x1 - 1, z1 - 1, y + 1, y + 1, color)
    walls(b, x0 + 2, z0 + 2, x1 - 2, z1 - 2, y + 2, y + 2, color)
    box(b, x0 + 3, y + 3, z0 + 3, x1 - 3, y + 3, z1 - 3, color)
    walls(b, x0 + 3, z0 + 3, x1 - 3, z1 - 3, y + 3, y + 3, "white_concrete")


def red_roof(b, x0, x1, z0, z1, y):
    step_roof(b, x0, x1, z0, z1, y, "red_concrete")


HEART_SIGN = (".WWW.", "WRWRW", "WRRRW", "WWRWW", ".WWW.")
BAG_SIGN = (".WWW.", "WBWBW", "WBBBW", "WBBBW", ".WWW.")


def concrete_sign(b, x0, y_top, z, pattern=HEART_SIGN, colors=None):
    """A 5 x 5 sign in concrete (W white, R red, B blue), its top row at y_top."""
    colors = colors or {"R": "red_concrete", "W": "white_concrete", "B": "blue_concrete"}
    for row, line in enumerate(pattern):
        for i, c in enumerate(line):
            if c != ".":
                b.set(x0 + i, y_top - row, z, colors[c])


def heart_sign(b, x0, y_top, z):
    """A white sign with a red heart on it."""
    concrete_sign(b, x0, y_top, z, HEART_SIGN)


def healing_center_hall(b):
    """The hall (walls x 1-9, z 1-7): a glass front round the door under a white canopy, the counter with the Healing
    Machine, the Nurse Station behind it, benches, plants."""
    plinth(b, 1, 1, 9, 7, "smooth_stone", floor="white_concrete")
    walls(b, 1, 1, 9, 7, 1, 4, "white_concrete")
    for x in (2, 4, 6, 8):
        glass_column(b, x, 1, 3, 1)
    door(b, 5, 1, 1, "birch_door", "south")
    pane(b, 5, 3, 1)
    stairs(b, 5, 0, 0, "smooth_quartz_stairs", "south")
    for x in range(3, 8):  # a canopy over the door
        slab(b, x, 4, 0, "smooth_quartz_slab", top=True)
    for z in (3, 5):
        window(b, 1, 2, z, "west", height=2)
        window(b, 9, 2, z, "east", height=2)
    # The counter across the hall with the Healing Machine in the middle, the nurse behind it
    for x in range(2, 9):
        if x != 5:
            b.set(x, 1, 5, "white_concrete")
            slab(b, x, 2, 5, "smooth_quartz_slab")
    b.set(5, 1, 5, "cobblemon:healing_machine", facing="north")
    b.set(3, 1, 6, "aliveworkplace:nurse_station", facing="south")
    b.set(7, 1, 6, "barrel", facing="up", open=False)
    b.set(8, 1, 6, "barrel", facing="up", open=False)
    for z in (2, 3):
        stairs(b, 2, 1, z, "birch_stairs", "west")
        stairs(b, 8, 1, z, "birch_stairs", "east")
    b.set(2, 1, 4, "potted_azalea_bush")
    b.set(8, 1, 4, "potted_azalea_bush")
    for z in range(2, 5):
        b.set(5, 1, z, "red_carpet")
    for x in (3, 7):
        lantern(b, x, 4, 3, hanging=True)


def healing_center():
    """11 x 10 x 9: the hall under a red roof, a heart sign over the door (with Cobblemon the counter holds a Healing
    Machine; without, that block loads as air and the builder skips it)."""
    b = Build(11, 10, 9)
    healing_center_hall(b)
    red_roof(b, 0, 10, 0, 8, 5)
    heart_sign(b, 3, 9, 0)
    b.fill_air()
    return b


# --- Healing Center, upgraded: a ward with four beds behind the counter --------------------------------
def healing_center_2():
    """Upgrade of the Healing Center: a door in the back wall opens into a ward with four beds (villagers who sleep there
    are in the nurse's reach) under the same red roof, now over both. 11 x 10 x 13."""
    b = Build(11, 10, 13)
    healing_center_hall(b)
    plinth(b, 1, 7, 9, 11, "smooth_stone", floor="white_concrete")
    walls(b, 1, 7, 9, 11, 1, 4, "white_concrete")
    door(b, 5, 1, 7, "birch_door", "south")
    for z in (9,):
        window(b, 1, 2, z, "west", height=2)
        window(b, 9, 2, z, "east", height=2)
    for x in (3, 7):
        window(b, x, 2, 11, "south", height=2)
    for x in (2, 4, 6, 8):
        b.bed(x, 1, 9, "white", facing="south")
    b.set(3, 1, 10, "potted_poppy")
    b.set(7, 1, 10, "potted_poppy")
    for x in (3, 7):
        lantern(b, x, 4, 9, hanging=True)
    red_roof(b, 0, 10, 0, 12, 5)
    heart_sign(b, 3, 9, 0)
    b.fill_air()
    return b


# --- Healing Center III: a berry garden behind the ward -------------------------------------------------
def healing_center_3():
    """Upgrade of Healing Center II: a raised garden behind the ward, edged in stone and fenced, with sweet berry bushes
    either side of a path, a Fruit Basket and a chest (a villager moves in as the orchard keeper and picks the berries),
    lamps and a bench. 11 x 10 x 19."""
    b = healing_center_2().grow(11, 10, 19)
    walls(b, 0, 13, 10, 18, 0, 0, "stone_bricks")
    box(b, 1, 0, 14, 9, 0, 17, "grass_block", snowy=False)
    for z in range(13, 18):
        b.set(5, 0, z, "dirt_path")
    door(b, 5, 1, 11, "birch_door", "north") if False else None
    for x in range(0, 11):
        fence(b, x, 1, 18, "birch_fence")
    for z in range(13, 18):
        fence(b, 0, 1, z, "birch_fence")
        fence(b, 10, 1, z, "birch_fence")
    b.set(10, 1, 15, "birch_fence_gate", facing="east", open=False, in_wall=False, powered=False)
    for x in (1, 2, 3, 7, 8, 9):
        for z in (14, 16):
            b.set(x, 1, z, "sweet_berry_bush", age=0) if (x + z) % 2 == 0 else b.set(x, 1, z, "rose_bush", half="lower")
    for x in (1, 2, 3, 7, 8, 9):
        for z in (14, 16):
            if b.get(x, 1, z)[0] == "minecraft:rose_bush":
                b.set(x, 2, z, "rose_bush", half="upper")
    b.set(4, 1, 17, "aliveworkplace:fruit_basket", facing="north")
    b.set(6, 1, 17, "chest", facing="north", type="single", waterlogged=False)
    for x, z in ((0, 18), (10, 18)):
        lantern(b, x, 2, z)
    stairs(b, 1, 1, 17, "birch_stairs", "south")
    stairs(b, 9, 1, 17, "birch_stairs", "south")
    # A back door out of the ward into the garden
    door(b, 5, 1, 11, "birch_door", "north")
    b.fill_air()
    return b


# --- Supply Shop: the Healing Center's blue neighbour: display windows under an awning ------------------------
def shop_floor(b):
    """The shop (walls x 1-9, z 1-7): display windows either side of the door under a striped awning, shelves of barrels
    down both sides, the Shop Counter at the back (a villager moves in as shopkeeper; the barrels are the stock, the first
    player to open it owns the shop)."""
    plinth(b, 1, 1, 9, 7, "smooth_stone", floor="birch_planks")
    walls(b, 1, 1, 9, 7, 1, 4, "white_concrete")
    for x0 in (2, 6):
        window(b, x0, 2, 1, "north", width=3, height=2)
        for x in range(x0, x0 + 3):
            slab(b, x, 1, 2, "birch_slab", top=True)  # the display shelf behind the glass
            trapdoor(b, x, 1, 0, "birch_trapdoor", "north", open_=True)
    b.set(3, 2, 2, "potted_cactus")
    b.set(7, 2, 2, "potted_bamboo")
    door(b, 5, 1, 1, "birch_door", "south")
    pane(b, 5, 3, 1)
    stairs(b, 5, 0, 0, "smooth_quartz_stairs", "south")
    for x in range(1, 10):  # the awning over the front
        b.set(x, 4, 0, "blue_wool" if x % 2 else "white_wool")
    for z in (3, 5):
        window(b, 1, 2, z, "west", height=2)
    window(b, 9, 2, 3, "east", height=2)
    # Shelves: barrels two high down both sides; the counter at the back with the Shop Counter in the middle
    for z in (2, 3, 4):
        for y in (1, 2):
            b.set(2, y, z, "barrel", facing="east", open=False)
            b.set(8, y, z, "barrel", facing="west", open=False)
    for x in (3, 4, 6, 7):
        b.set(x, 1, 5, "blue_concrete")
        slab(b, x, 2, 5, "smooth_quartz_slab")
    b.set(5, 1, 5, "aliveworkplace:shop_counter", facing="south")
    b.set(3, 2, 5, "lantern", hanging=False, waterlogged=False)
    b.set(7, 2, 5, "barrel", facing="up", open=False)
    stairs(b, 5, 1, 6, "birch_stairs", "south")  # the shopkeeper's seat
    b.set(5, 1, 2, "light_blue_carpet")
    b.set(5, 1, 3, "light_blue_carpet")
    lantern(b, 5, 4, 3, hanging=True)


def supply_shop():
    """11 x 10 x 9: white walls under a stepped blue roof, a shopping-bag sign over the door."""
    b = Build(11, 10, 9)
    shop_floor(b)
    step_roof(b, 0, 10, 0, 8, 5, "blue_concrete")
    concrete_sign(b, 3, 9, 0, BAG_SIGN)
    b.fill_air()
    return b


# --- Supply Shop, upgraded: the shopkeeper's rooms upstairs -------------------------------------------------
def supply_shop_2():
    """Upgrade of the Supply Shop: the roof goes up a storey; the old roof's edge stays as a blue band round the new
    upper floor, with a bed, a chest and more stock up a ladder behind the shelves (still in reach of the Shop Counter).
    11 x 14 x 9."""
    b = Build(11, 14, 9)
    shop_floor(b)
    box(b, 0, 5, 0, 10, 5, 8, "blue_concrete")
    box(b, 2, 5, 2, 8, 5, 6, "birch_planks")
    walls(b, 1, 1, 9, 7, 6, 8, "white_concrete")
    for x0 in (2, 6):
        window(b, x0, 7, 1, "north", width=3)
    for z in (3, 5):
        window(b, 1, 7, z, "west")
        window(b, 9, 7, z, "east")
    for y in range(1, 7):
        b.set(8, y, 6, "ladder", facing="west", waterlogged=False)
    b.bed(2, 6, 5, "blue", facing="south")
    b.set(3, 6, 6, "chest", facing="north", type="single", waterlogged=False)
    for z in (2, 3):
        b.set(8, 6, z, "barrel", facing="west", open=False)
    box(b, 4, 6, 3, 6, 6, 4, "light_blue_carpet")
    b.set(2, 6, 2, "potted_fern")
    lantern(b, 5, 8, 4, hanging=True)
    step_roof(b, 0, 10, 0, 8, 9, "blue_concrete")
    concrete_sign(b, 3, 13, 0, BAG_SIGN)
    b.fill_air()
    return b


# --- Supply Shop III: a post office annex --------------------------------------------------------------
def supply_shop_3():
    """Upgrade of Supply Shop II: a one-storey post office on the east side, set back a little, through a doorway from the
    shop, with a Postal Desk and a chest (a villager moves in as the postman: the shop's goods can go out by mail) and a
    bench out front. 17 x 14 x 9."""
    b = supply_shop_2().grow(17, 14, 9)
    plinth(b, 9, 2, 15, 7, "smooth_stone", floor="birch_planks")
    for y in range(1, 5):
        for x in range(10, 16):
            b.set(x, y, 2, "white_concrete")
            b.set(x, y, 7, "white_concrete")
        for z in range(3, 7):
            b.set(15, y, z, "white_concrete")
    for y in (1, 2):
        b.set(9, y, 6, "air")  # through from the shop
    door(b, 12, 1, 2, "birch_door", "south")
    stairs(b, 12, 0, 1, "smooth_quartz_stairs", "south")
    window(b, 10, 2, 2, "north", height=2)
    window(b, 14, 2, 2, "north", height=2)
    window(b, 15, 2, 4, "east", width=2, height=2)
    for x in range(10, 16):
        b.set(x, 4, 1, "blue_wool" if x % 2 else "white_wool")
    box(b, 10, 5, 1, 16, 5, 8, "blue_concrete")
    walls(b, 10, 2, 15, 7, 6, 6, "blue_concrete")
    box(b, 11, 7, 3, 14, 7, 6, "white_concrete")
    b.set(14, 1, 6, "aliveworkplace:postal_desk", facing="west")
    b.set(14, 1, 3, "chest", facing="west", type="single", waterlogged=False)
    b.set(10, 1, 3, "chiseled_bookshelf", facing="east")
    stairs(b, 11, 1, 6, "birch_stairs", "south")
    b.set(12, 1, 4, "light_blue_carpet")
    lantern(b, 12, 4, 4, hanging=True)
    stairs(b, 14, 0, 1, "birch_stairs", "south")
    stairs(b, 15, 0, 1, "birch_stairs", "south")
    b.fill_air()
    return b


# --- Storehouse: the Porter's workstation and the village's store ------------------------------------
def storehouse_chests(b, xs, z, facing, ys=(1, 2)):
    for x in xs:
        for y in ys:
            b.set(x, y, z, "chest", facing=facing, type="single", waterlogged=False)


def storehouse_bay(b, x0, doorway_west=False):
    """One open-fronted bay of the storehouse (walls x0..x0+6, z 2-7): a stone plinth, spruce posts and birch walls, open
    to the street under a beam, a window each side, chests along the back."""
    x1 = x0 + 6
    plinth(b, x0, 2, x1, 7, STONE_MIX, floor="spruce_planks")
    for y in range(1, 4):
        for x in range(x0, x1 + 1):
            b.set(x, y, 7, "birch_planks")
        for z in range(3, 7):
            b.set(x0, y, z, "birch_planks")
            b.set(x1, y, z, "birch_planks")
    posts(b, [(x0, 2), (x1, 2), (x0, 7), (x1, 7), (x0 + 3, 7)], 1, 3, "spruce_log")
    beam_ring(b, x0, 2, x1, 7, 4, "spruce_log")
    lantern(b, x0 + 2, 3, 2, hanging=True)
    lantern(b, x0 + 4, 3, 2, hanging=True)
    for x in range(x0 + 1, x1):
        b.set(x, 0, 2, "stripped_spruce_log", axis="x")  # a threshold across the opening


def storehouse():
    """9 x 9 x 9: an open-fronted timber storehouse with a Storehouse and eight chests along the back wall (a villager
    moves in as the porter and fills them with what the village's workers make), hay and crates by the door."""
    b = Build(9, 9, 9)
    storehouse_bay(b, 1)
    window(b, 1, 2, 4, "west", width=2, shutters="spruce_trapdoor")
    window(b, 7, 2, 4, "east", width=2, shutters="spruce_trapdoor")
    b.set(4, 1, 6, "aliveworkplace:storehouse", facing="north")
    b.set(4, 2, 6, "lantern", hanging=False, waterlogged=False)
    storehouse_chests(b, (2, 3, 5, 6), 6, "north")
    b.set(2, 1, 3, "hay_block", axis="y")
    b.set(6, 1, 3, "hay_block", axis="x")
    b.set(6, 2, 3, "hay_block", axis="z")
    gable_roof(b, 0, 8, 1, 8, 4, SPRUCE, axis="x", gable="birch_planks", gable_at=(1, 7), ridge=SPRUCE)
    # Crates outside, and a sack on the ground
    b.set(0, 0, 2, "barrel", facing="up", open=False)
    b.set(0, 1, 2, "barrel", facing="up", open=False)
    b.set(0, 0, 1, "barrel", facing="up", open=False)
    b.set(1, 0, 1, "hay_block", axis="y")
    b.fill_air()
    return b


def storehouse_2():
    """Upgrade of the Storehouse: a second bay to the east under the same roof, through a doorway, eight more chests (a
    gap in the middle of them for the door the third tier puts there). 16 x 9 x 9."""
    b = storehouse().grow(16, 9, 9)
    b.clear(8, 0, 0, 8, 8, 8)
    storehouse_bay(b, 8)
    posts(b, [(7, 2), (7, 7)], 1, 3, "spruce_log")
    for z in (3, 4, 5, 6):
        for y in (1, 2, 3):
            b.set(8, y, z, "birch_planks")
    b.clear(7, 1, 4, 8, 2, 5)  # the doorway between the bays
    for x in (7, 8):
        for z in (4, 5):
            for y in (1, 2):
                b.set(x, y, z, "air")
        b.set(x, 3, 4, "birch_planks")
        b.set(x, 3, 5, "birch_planks")
    window(b, 14, 2, 4, "east", width=2, shutters="spruce_trapdoor")
    storehouse_chests(b, (9, 10, 12, 13), 6, "north")
    b.set(13, 1, 3, "hay_block", axis="y")
    gable_roof(b, 0, 15, 1, 8, 4, SPRUCE, axis="x", gable="birch_planks", gable_at=(1, 14), ridge=SPRUCE)
    b.set(15, 0, 2, "barrel", facing="up", open=False)
    b.set(15, 0, 1, "barrel", facing="up", open=False)
    b.fill_air()
    return b


def storehouse_3():
    """Upgrade of Storehouse II: a stone warehouse behind both bays, through a door in the east bay's back wall, with
    sixteen more chests between spruce posts, lit from above. 16 x 10 x 16."""
    b = storehouse_2().grow(16, 10, 16)
    plinth(b, 1, 8, 14, 14, FOUNDATION_MIX, floor="spruce_planks")
    walls(b, 1, 8, 14, 14, 1, 3, BRICK_WALL_MIX)
    walls(b, 1, 8, 14, 14, 4, 4, "birch_planks")
    posts(b, [(1, 14), (14, 14), (1, 8), (14, 8), (5, 14), (10, 14)], 1, 4, "spruce_log")
    beam_ring(b, 1, 8, 14, 14, 5, "spruce_log")
    for z in (10, 12):
        window(b, 1, 3, z, "west", sill=STONE_BRICK)
        window(b, 14, 3, z, "east", sill=STONE_BRICK)
    # The door through from the east bay (where its middle chests leave a gap), the chests between posts
    for y in (1, 2):
        b.set(11, y, 7, "air")
        b.set(11, y, 8, "air")
    door(b, 11, 1, 8, "spruce_door", "south")
    storehouse_chests(b, (2, 3, 4, 6, 7, 8, 9), 13, "north")
    storehouse_chests(b, (12, 13), 13, "north")
    storehouse_chests(b, (2, 3, 4, 6, 7), 9, "south")
    for x in (5, 10):
        for y in (1, 2, 3):
            log(b, x, y, 13, "stripped_spruce_log")
    gable_roof(b, 0, 15, 7, 15, 5, DARK_OAK, axis="x", gable="birch_planks", gable_at=(1, 14))
    for x in (4, 11):
        lantern(b, x, 4, 11, hanging=True)
        b.set(x, 5, 11, "spruce_planks")
    b.fill_air()
    return b


# --- Berry Farm: an orchard keeper's garden --------------------------------------------------------------
def berry_farm():
    """11 x 5 x 9: raised beds of sweet berry bushes edged in stone either side of a gravel path, a fence all round with a
    gate, and a little lean-to with a Fruit Basket and a chest by the back fence (a villager moves in as the orchard
    keeper and picks the berries)."""
    b = Build(11, 5, 9)
    walls(b, 0, 0, 10, 8, 0, 0, STONE_MIX)
    box(b, 1, 0, 1, 9, 0, 7, "grass_block", snowy=False)
    for z in range(0, 8):
        b.set(5, 0, z, "gravel")
    for x0 in (1, 7):  # the raised beds, edged with trapdoors
        for x in range(x0, x0 + 3):
            for z in (2, 3, 4):
                b.set(x, 0, z, "farmland", moisture=7) if False else None
                b.set(x, 1, z, "sweet_berry_bush", age=0)
    for x in range(0, 11):
        if x != 5:
            fence(b, x, 1, 0, "spruce_fence")
        fence(b, x, 1, 8, "spruce_fence")
    for z in range(1, 8):
        fence(b, 0, 1, z, "spruce_fence")
        fence(b, 10, 1, z, "spruce_fence")
    b.set(5, 1, 0, "spruce_fence_gate", facing="south", open=False, in_wall=False, powered=False)
    # The lean-to at the back: two posts, a slab roof, the basket and chest under it, flowers either side
    for x in (3, 7):
        fence(b, x, 1, 6, "spruce_fence")
        fence(b, x, 2, 6, "spruce_fence")
    for x in range(2, 9):
        slab(b, x, 3, 6, SPRUCE)
        slab(b, x, 3, 7, SPRUCE)
    for x in range(3, 8):
        b.set(x, 2, 7, "spruce_planks") if False else None
    b.set(4, 1, 7, "aliveworkplace:fruit_basket", facing="north")
    b.set(6, 1, 7, "chest", facing="north", type="single", waterlogged=False)
    b.set(5, 0, 7, "gravel")
    for x in (1, 2, 8, 9):
        b.set(x, 1, 6, "rose_bush", half="lower")
        b.set(x, 2, 6, "rose_bush", half="upper")
    lantern(b, 5, 2, 6, hanging=True)
    for x, z in ((0, 0), (10, 0), (0, 8), (10, 8)):
        lantern(b, x, 2, z)
    b.fill_air()
    return b


def berry_farm_2():
    """Upgrade of the Berry Farm: through a gate in the back fence, a pergola on log posts with glow berries hanging from
    its beams, more bushes underneath and a bench. 11 x 6 x 15."""
    b = berry_farm().grow(11, 6, 15)
    walls(b, 0, 8, 10, 14, 0, 0, STONE_MIX)
    box(b, 1, 0, 9, 9, 0, 13, "grass_block", snowy=False)
    for z in range(8, 14):
        b.set(5, 0, z, "gravel")
    b.set(5, 1, 8, "spruce_fence_gate", facing="south", open=False, in_wall=False, powered=False)
    for z in range(9, 14):
        fence(b, 0, 1, z, "spruce_fence")
        fence(b, 10, 1, z, "spruce_fence")
    for x in range(0, 11):
        fence(b, x, 1, 14, "spruce_fence")
    for x, z in ((2, 9), (8, 9), (2, 13), (8, 13)):
        for y in range(1, 4):
            log(b, x, y, z, "spruce_log")
    for x in range(1, 10):
        log(b, x, 4, 9, "spruce_log", axis="x")
        log(b, x, 4, 13, "spruce_log", axis="x")
    for z in range(10, 13):
        for x in (2, 4, 6, 8):
            log(b, x, 4, z, "spruce_log", axis="z")
    for x in (4, 6):
        for z in (10, 12):
            b.set(x, 3, z, "cave_vines", age=0, berries=True)
    for x in (3, 7):
        for z in (10, 12):
            b.set(x, 1, z, "sweet_berry_bush", age=0)
    stairs(b, 4, 1, 13, SPRUCE, "south")
    stairs(b, 6, 1, 13, SPRUCE, "south")
    lantern(b, 5, 3, 11, hanging=True)
    b.set(5, 4, 11, "spruce_planks")
    b.fill_air()
    return b


# --- Research Lab: the fossil scientist's -------------------------------------------------------------
def research_lab():
    """11 x 9 x 11: a stone lab under a deepslate hip roof with a glass skylight, a columned porch, a Fossil Lab at the
    back (a villager moves in as the fossil scientist; with Cobblemon they revive fossils), bookshelves, a glass case and
    a bone skeleton on show."""
    b = Build(11, 9, 11)
    plinth(b, 1, 1, 9, 9, FOUNDATION_MIX, floor="polished_andesite")
    walls(b, 1, 1, 9, 9, 1, 4, BRICK_WALL_MIX)
    posts(b, [(1, 1), (9, 1), (1, 9), (9, 9), (5, 9), (1, 5), (9, 5)], 1, 4, "polished_andesite") if False else None
    for x, z in ((1, 1), (9, 1), (1, 9), (9, 9), (1, 5), (9, 5), (5, 9)):
        for y in range(1, 5):
            b.set(x, y, z, "polished_andesite")
    walls(b, 1, 1, 9, 9, 4, 4, "polished_andesite")
    for x in (2, 7):
        window(b, x, 2, 1, "north", width=2, sill=STONE_BRICK)
    for z in (2, 7):
        window(b, 1, 2, z, "west", width=2, height=1, sill=STONE_BRICK)
        window(b, 9, 2, z, "east", width=2, height=1, sill=STONE_BRICK)
    door(b, 5, 1, 1, "dark_oak_door", "south")
    stairs(b, 5, 0, 0, STONE_BRICK, "south")
    # The porch: two columns and a slab roof
    for x in (4, 6):
        for y in (1, 2, 3):
            wall_block(b, x, y, 0, "stone_brick_wall")
        wall_block(b, x, 0, 0, "stone_brick_wall")
    for x in range(3, 8):
        slab(b, x, 4, 0, DEEPSLATE_TILE, top=True)
    # The roof: deepslate tiles, a glass skylight on top
    hip_roof(b, 0, 10, 0, 10, 5, DEEPSLATE_TILE, rings=3)
    walls(b, 3, 3, 7, 7, 8, 8, "polished_deepslate")
    box(b, 4, 8, 4, 6, 8, 6, "glass")
    # Inside: the Fossil Lab between bookshelves, a skeleton and a case on show
    b.set(5, 1, 8, "aliveworkplace:fossil_lab", facing="north")
    for x in (2, 3, 7, 8):
        for y in (1, 2):
            b.set(x, y, 8, "bookshelf")
    stairs(b, 4, 1, 7, "dark_oak_stairs", "south")
    b.set(2, 1, 4, "polished_andesite")
    b.set(2, 2, 4, "bone_block", axis="y")
    b.set(2, 3, 4, "skeleton_skull", rotation=4)
    b.set(3, 1, 4, "bone_block", axis="x")
    b.set(8, 1, 4, "polished_andesite")
    b.set(8, 2, 4, "glass")
    b.set(8, 1, 6, "polished_andesite")
    b.set(8, 2, 6, "potted_fern")
    box(b, 4, 1, 4, 6, 1, 6, "brown_carpet")
    for x in (3, 7):
        lantern(b, x, 4, 5, hanging=True) if False else None
    lantern(b, 5, 7, 5, hanging=True)
    b.set(5, 8, 5, "glass")
    b.fill_air()
    return b


def research_lab_2():
    """Upgrade of the Research Lab: a museum hall to the east through a doorway, under a glass roof on stone ribs, with
    a big skeleton of bone blocks and cases either side. 18 x 10 x 11."""
    b = research_lab().grow(18, 10, 11)
    b.clear(10, 0, 0, 10, 9, 10)
    hip_roof(b, 0, 10, 0, 10, 5, DEEPSLATE_TILE, rings=3)
    walls(b, 3, 3, 7, 7, 8, 8, "polished_deepslate")
    box(b, 4, 8, 4, 6, 8, 6, "glass")
    b.set(5, 8, 5, "glass")
    plinth(b, 9, 2, 16, 8, FOUNDATION_MIX, floor="polished_andesite")
    for y in range(1, 5):
        for x in range(10, 17):
            b.set(x, y, 2, BRICK_WALL_MIX)
            b.set(x, y, 8, BRICK_WALL_MIX)
        for z in range(3, 8):
            b.set(16, y, z, BRICK_WALL_MIX)
    for x, z in ((16, 2), (16, 8), (13, 2), (13, 8)):
        for y in range(1, 5):
            b.set(x, y, z, "polished_andesite")
    for x in range(10, 17):
        b.set(x, 4, 2, "polished_andesite")
        b.set(x, 4, 8, "polished_andesite")
    for z in range(3, 8):
        b.set(16, 4, z, "polished_andesite")
    for y in (1, 2):
        b.set(9, y, 5, "air")  # the doorway from the lab (where its east pillar was)
    b.set(9, 3, 5, "polished_andesite")
    for x in (11, 14):
        window(b, x, 2, 2, "north", width=2, sill=STONE_BRICK)
    window(b, 16, 2, 4, "east", width=3, sill=STONE_BRICK)
    # The roof: deepslate tiles like the lab's, with a glass ridge to light the skeleton
    gable_roof(b, 10, 17, 1, 9, 5, DEEPSLATE_TILE, axis="x", gable=BRICK_WALL_MIX, gable_at=(16, 16))
    for x in range(10, 18):
        for z, y in ((4, 8), (6, 8), (5, 9)):
            b.set(x, y, z, "glass")
    for z, y in ((4, 8), (6, 8), (5, 9)):
        b.set(16, y, z, "polished_andesite") if False else None
    # The skeleton: legs, a spine, ribs, a neck and a skull
    for x in (11, 14):
        for z in (4, 6):
            b.set(x, 1, z, "bone_block", axis="y")
    for x in range(11, 15):
        b.set(x, 2, 5, "bone_block", axis="x")
    for x in (12, 13):
        b.set(x, 2, 4, "bone_block", axis="z")
        b.set(x, 2, 6, "bone_block", axis="z")
    b.set(15, 2, 5, "bone_block", axis="y")
    b.set(15, 3, 5, "bone_block", axis="x")
    b.set(15, 4, 5, "skeleton_skull", rotation=12) if False else None
    b.set(15, 3, 5, "skeleton_skull", rotation=12)
    b.set(10, 2, 5, "bone_block", axis="x")
    for z in (3, 7):
        b.set(15, 1, z, "polished_andesite")
        b.set(15, 2, z, "glass")
        b.set(11, 1, z, "polished_andesite")
        b.set(11, 2, z, "potted_fern")
    b.fill_air()
    return b


# --- Terrace: row houses, two homes of two storeys (the upgrade adds a third) -------------------------------------
TERRACE_FRAME = "spruce_log"
TERRACE_INFILL = "birch_planks"
TERRACE_ROOF = DARK_OAK


def terrace_home(b, x0, door_x, window_x, beds, kitchen_west=True, flowers=("potted_red_tulip", "potted_azure_bluet")):
    """One home of the terrace between party walls at x0 and x0 + 5 (inside x0+1 .. x0+4): a stone ground floor with the
    door, a window with a flower box and a little kitchen; a ladder up to the timber bedroom under the roof (two beds)."""
    door(b, door_x, 1, 2, "spruce_door", "south", hinge="left" if kitchen_west else "right")
    stairs(b, door_x, 0, 1, COBBLE, "south")
    log(b, door_x, 3, 2, TERRACE_FRAME, axis="x")
    window(b, window_x, 2, 2, "north", width=2, height=1, shutters="spruce_trapdoor", flowers=("spruce_trapdoor", list(flowers)))
    # Downstairs: stove, worktop and barrel along the back, a table by the window, a ladder up in the corner
    inner = [x0 + 1, x0 + 2, x0 + 3, x0 + 4]
    ladder_x = inner[-1] if kitchen_west else inner[0]
    back = [x for x in inner if x != ladder_x]
    b.set(back[0], 1, 7, "furnace", facing="north", lit=False)
    b.set(back[1], 1, 7, "crafting_table")
    b.set(back[2], 1, 7, "barrel", facing="up", open=False)
    for y in range(1, 6):
        b.set(ladder_x, y, 7, "ladder", facing="north", waterlogged=False)
    table_x = window_x if window_x in inner else inner[1]
    fence(b, table_x, 1, 4, "spruce_fence")
    b.set(table_x, 2, 4, "spruce_pressure_plate", powered=False)
    other = window_x + 1 if window_x + 1 in inner and window_x + 1 != table_x else window_x - 1
    stairs(b, other, 1, 4, SPRUCE, "east" if other < table_x else "west")
    box(b, inner[0], 1, 5, inner[-1], 1, 6, "brown_carpet")
    # Upstairs: the floor (with the ladder's hole), two beds against the back wall, a chest and a light
    box(b, inner[0], 5, 2, inner[-1], 5, 7, "spruce_planks")
    b.set(ladder_x, 5, 7, "ladder", facing="north", waterlogged=False)
    bed_xs = [x for x in inner if x != ladder_x][:2] if kitchen_west else [x for x in inner if x != ladder_x][-2:]
    for x, colour in zip(bed_xs, beds):
        b.bed(x, 6, 3, colour, facing="south")
    chest_x = [x for x in inner if x not in bed_xs and x != ladder_x][0]
    b.set(chest_x, 6, 2, "chest", facing="south", type="single", waterlogged=False)
    b.set(chest_x, 6, 7, "white_carpet")
    for x in bed_xs:
        b.set(x, 6, 5, "red_carpet" if kitchen_west else "blue_carpet")
    for x in inner:  # a tie beam across the bedroom for its light
        log(b, x, 9, 5, TERRACE_FRAME, axis="x")
    lantern(b, inner[1] if kitchen_west else inner[2], 8, 5, hanging=True)
    # The bedroom's windows: a pair in the jettied front, one at the back
    window(b, inner[1], 7, 1, "north", width=2, height=1, shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, inner[1] if kitchen_west else inner[2], 7, 8, "south", height=1, sill=SPRUCE)


def terrace_block(b, x1, x2, party):
    """The terrace's shell from wall x1 to wall x2 with party walls at {@code party}: a stone ground storey, an upper
    storey jettied a block over the street on stair brackets, a spruce frame with birch infill."""
    plinth(b, x1, 2, x2, 8, STONE_MIX, floor="spruce_planks")
    walls(b, x1, 2, x2, 8, 1, 4, BRICK_WALL_MIX)
    for x in party:
        box(b, x, 1, 3, x, 4, 7, BRICK_WALL_MIX)
        box(b, x, 0, 3, x, 0, 7, STONE_MIX)
    # The jetty: brackets under the front, a floor beam round the top of the stone storey
    for x in range(x1, x2 + 1):
        stairs(b, x, 4, 1, TERRACE_ROOF, "south", top=True)
    beam_ring(b, x1, 1, x2, 8, 5, TERRACE_FRAME)
    for x in party:
        for z in range(2, 8):
            log(b, x, 5, z, TERRACE_FRAME, axis="z")
    walls(b, x1, 1, x2, 8, 6, 8, TERRACE_INFILL)
    for x in party:
        box(b, x, 6, 2, x, 8, 7, TERRACE_INFILL)
    posts(b, [(x, z) for x in [x1, x2] + list(party) for z in (1, 8)], 6, 8, TERRACE_FRAME)
    beam_ring(b, x1, 1, x2, 8, 9, TERRACE_FRAME)


def terrace_gable_end(b, x, chimney_x, window=True):
    """A gable end at wall x: a post up the middle, a small window, and the home's chimney outside it."""
    log(b, x, 10, 4, TERRACE_FRAME)
    log(b, x, 10, 5, TERRACE_FRAME)
    if window:
        pane(b, x, 11, 4)
        pane(b, x, 11, 5)
    for z in (4, 5):  # side windows upstairs
        pane(b, x, 7, z)
    if chimney_x is not None:
        chimney(b, chimney_x, 6, 0, 13, STONE_MIX)


def party_stack(b, x):
    """A chimney stack up through the ridge over a party wall (the homes either side share it): it marks the homes apart."""
    for z in (4, 5):
        for y in range(9, 15):
            b.set(x, y, z, BRICK_WALL_MIX)
    b.set(x, 15, 4, "campfire", facing="north", lit=True, signal_fire=False, waterlogged=False)
    b.set(x, 15, 5, "stone_brick_slab", type="bottom", waterlogged=False)


def terrace_colours(b, x0, x1, infill):
    """Each home of a terrace is its own colour upstairs: the timber infill from x0 to x1 in {@code infill}."""
    for (x, y, z), block in list(b.blocks.items()):
        if x0 <= x <= x1 and y >= 6 and block[0] == "minecraft:" + TERRACE_INFILL:
            b.set(x, y, z, infill)


def terrace():
    """13 x 16 x 10: two narrow homes of two storeys under one dark oak roof — stone below, the timber bedrooms jettied
    over the street, a chimney up each gable. Four beds: building one grows the village."""
    b = Build(13, 16, 10)
    terrace_block(b, 1, 11, [6])
    terrace_home(b, 1, 2, 4, ["red", "red"], kitchen_west=True)
    terrace_home(b, 6, 10, 7, ["blue", "blue"], kitchen_west=False, flowers=("potted_cornflower", "potted_oxeye_daisy"))
    gable_roof(b, 0, 12, 0, 9, 9, TERRACE_ROOF, axis="x", gable=TERRACE_INFILL, gable_at=(1, 11), ridge=DARK_OAK)
    terrace_gable_end(b, 1, 0)
    terrace_gable_end(b, 11, None)
    party_stack(b, 6)
    terrace_colours(b, 7, 11, "oak_planks")
    for x in (3, 9):  # windows at the back of the kitchens
        window(b, x, 2, 8, "south", height=1, sill=SPRUCE)
    # Small life: a lantern on each flower box by the doors, a barrel and a bench out front
    lantern(b, 5, 2, 1)
    lantern(b, 7, 2, 1)
    b.set(1, 1, 1, "barrel", facing="up", open=False)
    stairs(b, 11, 1, 1, SPRUCE, "south")
    b.fill_air()
    return b


def terrace_2():
    """Upgrade of the Terrace (same origin and front): a third home on the east end, the east gable becoming a party wall
    with its own chimney stack; the roof runs on over it. Six beds. 18 x 16 x 10."""
    b = terrace().grow(18, 16, 10)
    b.clear(12, 0, 0, 12, 15, 9)  # the old east overhang
    terrace_block(b, 11, 16, [])
    for z in (4, 5):
        b.set(11, 11, z, TERRACE_INFILL)  # the old gable window, now in the attic's party wall
    terrace_home(b, 11, 15, 12, ["yellow", "yellow"], kitchen_west=True, flowers=("potted_allium", "potted_poppy"))
    gable_roof(b, 0, 17, 0, 9, 9, TERRACE_ROOF, axis="x", gable=TERRACE_INFILL, gable_at=(1, 16), ridge=DARK_OAK)
    terrace_gable_end(b, 16, 17)
    party_stack(b, 6)
    party_stack(b, 11)
    terrace_colours(b, 7, 10, "oak_planks")
    for x in (3, 9, 13):
        window(b, x, 2, 8, "south", height=1, sill=SPRUCE)
    lantern(b, 14, 2, 1)
    lamp_post(b, 17, 0, 1, "spruce_fence", height=2)
    b.fill_air()
    return b


# --- Inn: a tavern below, guest rooms above -----------------------------------------------------------------------
INN_FRAME = "dark_oak_log"
INN_INFILL = "birch_planks"
INN_ROOF = SPRUCE


def inn():
    """13 x 16 x 13: a two-storey inn with its gable to the street — a stone tavern downstairs (a bar of barrels with an
    Inn Counter at its end: an innkeeper moves in, a hearth, tables and benches), three guest rooms with six beds under
    the steep spruce roof, a lamp over the door."""
    b = Build(13, 16, 13)
    # Stone storey: walls x 1-11, z 2-11
    plinth(b, 1, 2, 11, 11, FOUNDATION_MIX, floor="spruce_planks")
    walls(b, 1, 2, 11, 11, 1, 4, STONE_MIX)
    for x, z in ((1, 2), (11, 2), (1, 11), (11, 11)):
        box(b, x, 1, z, x, 4, z, "stone_bricks")
    # The door in the middle under a little porch roof, windows either side
    door(b, 6, 1, 2, "spruce_door", "south")
    stairs(b, 6, 0, 1, STONE_BRICK, "south")
    stairs(b, 5, 0, 1, STONE_BRICK, "south")
    stairs(b, 7, 0, 1, STONE_BRICK, "south")
    log(b, 6, 3, 2, INN_FRAME, axis="x")
    for x in (5, 6, 7):
        stairs(b, x, 4, 1, INN_ROOF, "south")
    fence(b, 4, 1, 1, "spruce_fence")
    fence(b, 4, 2, 1, "spruce_fence")
    fence(b, 8, 1, 1, "spruce_fence")
    fence(b, 8, 2, 1, "spruce_fence")
    b.set(4, 3, 1, "spruce_planks")
    b.set(8, 3, 1, "spruce_planks")
    lantern(b, 6, 3, 1, hanging=True)
    for x in (2, 9):
        window(b, x, 2, 2, "north", width=2, height=2, shutters="spruce_trapdoor", sill=SPRUCE)
    for z in (5, 8):
        window(b, 1, 2, z, "west", height=2, shutters="spruce_trapdoor", sill=SPRUCE)
        window(b, 11, 2, z, "east", height=2, shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 4, 2, 11, "south", width=2, height=1, sill=SPRUCE)
    # The tavern: a bar along the back, a hearth on the east wall, two tables with benches
    for x in range(2, 8):
        slab(b, x, 1, 9, SPRUCE, double=True) if x == 2 else stairs(b, x, 1, 9, SPRUCE, "north", top=True)
    b.set(8, 1, 9, "aliveworkplace:inn_counter", facing="north")  # the innkeeper's end of the bar
    for x in (2, 4, 6):
        b.set(x, 1, 10, "barrel", facing="north", open=False)
    b.set(3, 1, 10, "barrel", facing="up", open=False)
    b.set(5, 2, 10, "barrel", facing="north", open=False)
    b.set(7, 1, 10, "brewing_stand", has_bottle_0=False, has_bottle_1=False, has_bottle_2=False)
    for z in (5, 6, 7):
        b.set(10, 1, z, "stone_bricks")
    b.set(10, 1, 6, "campfire", facing="west", lit=True, signal_fire=False, waterlogged=False)
    for z in (5, 7):
        b.set(10, 2, z, "stone_bricks")
    b.set(10, 3, 5, "stone_brick_slab", type="bottom", waterlogged=False)
    b.set(10, 3, 7, "stone_brick_slab", type="bottom", waterlogged=False)
    for tx, tz in ((3, 5), (7, 5)):
        fence(b, tx, 1, tz, "spruce_fence")
        b.set(tx, 2, tz, "spruce_pressure_plate", powered=False)
        stairs(b, tx - 1, 1, tz, SPRUCE, "east")
        stairs(b, tx + 1, 1, tz, SPRUCE, "west")
    box(b, 2, 1, 7, 8, 1, 7, "red_carpet")
    for y in range(1, 6):
        b.set(10, y, 10, "ladder", facing="west", waterlogged=False)
    # Timber storey: floor beam, frame and infill, y 6-8
    beam_ring(b, 1, 2, 11, 11, 5, INN_FRAME)
    box(b, 2, 5, 3, 10, 5, 10, "spruce_planks")
    b.set(10, 5, 10, "ladder", facing="west", waterlogged=False)
    for x in range(2, 11):
        log(b, x, 5, 6, INN_FRAME, axis="x")  # a beam across the tavern ceiling to hang lights from
    lantern(b, 4, 4, 6, hanging=True)
    lantern(b, 8, 4, 6, hanging=True)
    walls(b, 1, 2, 11, 11, 6, 8, INN_INFILL)
    posts(b, [(1, 2), (11, 2), (1, 11), (11, 11), (4, 2), (8, 2), (1, 6), (11, 6), (4, 11), (8, 11), (1, 9), (11, 9)], 6, 8, INN_FRAME)
    beam_ring(b, 1, 2, 11, 11, 9, INN_FRAME)
    # Three guest rooms along the front (partitions at x 4 and 8), two beds each; a landing at the back
    for px in (4, 8):
        box(b, px, 6, 3, px, 8, 6, "spruce_planks")
    b.bed(2, 6, 3, "red", facing="south")
    b.bed(3, 6, 3, "red", facing="south")
    b.bed(5, 6, 3, "green", facing="south")
    b.bed(7, 6, 3, "green", facing="south")
    b.set(6, 6, 3, "chest", facing="south", type="single", waterlogged=False)
    b.bed(9, 6, 3, "blue", facing="south")
    b.bed(10, 6, 3, "blue", facing="south")
    for x in (3, 6, 9):
        b.set(x, 6, 6, "air")  # the rooms open onto the landing
    b.set(4, 6, 7, "cauldron")
    b.set(2, 6, 10, "barrel", facing="up", open=False)
    for x in range(2, 11):  # a tie beam over the landing for its light
        log(b, x, 9, 8, INN_FRAME, axis="x")
    lantern(b, 6, 8, 8, hanging=True)
    # Upstairs windows: one to each room at the front, the landing's at the sides and back
    for x in (2, 6, 9):
        window(b, x, 7, 2, "north", width=2 if x != 6 else 1, height=1, shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 1, 7, 8, "west", height=1, sill=SPRUCE)
    window(b, 11, 7, 8, "east", height=1, sill=SPRUCE)
    window(b, 6, 7, 11, "south", height=1, sill=SPRUCE)
    # The roof: gable to the street, overhanging all round
    gable_roof(b, 0, 12, 1, 12, 9, INN_ROOF, axis="z", gable=INN_INFILL, gable_at=(2, 11), ridge=SPRUCE)
    for z in (2, 11):  # the gables framed like the walls: posts, a collar beam, a window
        for y in (10, 11, 13):
            log(b, 6, y, z, INN_FRAME)
        for x in (3, 9):
            log(b, x, 10, z, INN_FRAME)
            log(b, x, 11, z, INN_FRAME)
        for x in range(4, 9):
            log(b, x, 12, z, INN_FRAME, axis="x")
        pane(b, 5, 11, z)
        pane(b, 7, 11, z)
    b.set(6, 14, 1, "spruce_planks")  # the ridge's end, for the lamp to hang from
    lantern(b, 6, 13, 1, hanging=True)
    chimney(b, 12, 6, 0, 13, STONE_MIX)
    # Small life: barrels and a hay bale by the door, a lamp post at the corner
    b.set(2, 1, 1, "barrel", facing="up", open=False)
    b.set(10, 1, 1, "hay_block", axis="y")
    lamp_post(b, 0, 0, 1, "spruce_fence", height=2)
    b.fill_air()
    return b


def inn_2():
    """Upgrade of the Inn (same origin and front): a stable on the east side for travellers' horses, its roof running back
    past the inn's chimney — a Feed Trough, hay and water inside, so a rancher moves in. 19 x 16 x 13."""
    b = inn().grow(19, 16, 13)
    plinth(b, 13, 3, 17, 10, STONE_MIX, floor="coarse_dirt")
    for y in range(1, 4):
        for x in range(13, 18):
            if x not in (14, 15, 16) or y == 3:
                b.set(x, y, 3, "spruce_planks")
            b.set(x, y, 10, "spruce_planks")
        for z in range(4, 10):
            b.set(13, y, z, "spruce_planks")
            b.set(17, y, z, "spruce_planks")
    posts(b, [(13, 3), (17, 3), (13, 10), (17, 10), (17, 6), (13, 6)], 1, 3, INN_FRAME)
    beam_ring(b, 13, 3, 17, 10, 4, INN_FRAME)
    # The way in: a gate between fences under the beam
    fence(b, 14, 1, 3, "spruce_fence")
    fence(b, 16, 1, 3, "spruce_fence")
    b.set(15, 1, 3, "spruce_fence_gate", facing="south", open=False, powered=False, in_wall=False)
    for z in (5, 8):  # little windows high in the side wall
        trapdoor(b, 17, 3, z, "spruce_trapdoor", "east", open_=True)
        b.set(17, 3, z, "air")
    # Inside: the Feed Trough with a chest beside it, hay, water
    b.set(14, 1, 9, "aliveworkplace:feed_trough", facing="south")
    b.set(15, 1, 9, "chest", facing="north", type="single", waterlogged=False)
    b.set(16, 1, 9, "hay_block", axis="y")
    b.set(16, 2, 9, "hay_block", axis="x")
    b.set(16, 1, 8, "water_cauldron", level=3)
    lantern(b, 15, 3, 6, hanging=True)
    for x in range(14, 17):
        log(b, x, 4, 6, INN_FRAME, axis="x")
    gable_roof(b, 12, 18, 2, 11, 5, INN_ROOF, axis="z", gable="spruce_planks", gable_at=(3, 10), ridge=SPRUCE)
    chimney(b, 12, 6, 0, 13, STONE_MIX)  # the inn's chimney goes up through the stable roof
    # A hitching post by the gate
    fence(b, 18, 0, 1, "spruce_fence")
    fence(b, 18, 1, 1, "spruce_fence")
    b.fill_air()
    return b
