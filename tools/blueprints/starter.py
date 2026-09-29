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
    for x in range(2, 9):  # a tie beam across the attic, the lantern standing on it
        log(b, x, 8, 5, "stripped_spruce_log", axis="x")
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
    # The awning: a striped canopy sloping up from the front (a wool valance, then carpet on a slab slope)
    for x in range(x0, x1 + 1):
        c = colors[(x - x0) % 2]
        b.set(x, 4, 1, c)
        slab(b, x, 4, 2, SPRUCE, top=True)
        b.set(x, 5, 2, c.replace("_wool", "_carpet"))
        b.set(x, 5, 3, c)
        slab(b, x, 5, 4, SPRUCE, top=True)
        b.set(x, 6, 4, c.replace("_wool", "_carpet"))
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
        b.set(x, 15, z, "chain", axis="y", waterlogged=False)  # from the roof's underside
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
    for z in range(1, 6):  # a tie beam across the guardhouse, the lantern hanging from it
        log(b, 9, 4, z, "stripped_spruce_log", axis="z")
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


CLINIC_FRAME = "stripped_dark_oak_log"
CLINIC_WALL = Mix((8, "white_concrete"), (2, "polished_diorite"), seed=21)
CLINIC_ROOF = RED_NETHER_BRICK
CLINIC_TRIM = DARK_OAK


def clinic_hall(b):
    """The hall (walls x 1-9, z 2-8): white plaster between dark posts on a stone plinth, the long side to the street
    with the door in the middle bay under a gabled porch; inside the counter with the Healing Machine, the Nurse Station
    behind it, benches and plants, tie beams across for the lanterns (and for the builder to reach the ridge from)."""
    skirt(b, 1, 2, 9, 8, STONE_BRICK)
    plinth(b, 1, 2, 9, 8, MOSSY_BRICK_MIX, floor="smooth_quartz")
    walls(b, 1, 2, 9, 8, 1, 3, CLINIC_WALL)
    posts(b, [(x, z) for x in (1, 4, 6, 9) for z in (2, 8)] + [(x, 5) for x in (1, 9)], 1, 3, CLINIC_FRAME)
    beam_ring(b, 1, 2, 9, 8, 4, CLINIC_FRAME)
    # The front: a window in each outer bay, the door in the middle one
    for x0 in (2, 7):
        window(b, x0, 2, 2, "north", width=2, height=1, shutters="dark_oak_trapdoor", sill=STONE_BRICK)
    door(b, 5, 1, 2, "birch_door", "south")
    pane(b, 5, 3, 2)
    # The sides and the back
    for z0 in (3, 6):
        window(b, 1, 2, z0, "west", width=2, height=1, shutters="dark_oak_trapdoor", sill=STONE_BRICK)
        window(b, 9, 2, z0, "east", width=2, height=1, shutters="dark_oak_trapdoor", sill=STONE_BRICK)
    for x0 in (2, 7):
        window(b, x0, 2, 8, "south", width=2, height=1, shutters="dark_oak_trapdoor", sill=STONE_BRICK)
    pane(b, 5, 2, 8)
    # Inside: the counter across the hall with the Healing Machine in the middle, the nurse behind it
    for x in range(2, 9):
        if x != 5:
            b.set(x, 1, 6, "white_concrete")
            slab(b, x, 2, 6, "smooth_quartz_slab")
    b.set(5, 1, 6, "cobblemon:healing_machine", facing="north")
    b.set(3, 1, 7, "aliveworkplace:nurse_station", facing="south")
    b.set(7, 1, 7, "chest", facing="north", type="single", waterlogged=False)
    b.set(8, 1, 7, "potted_azalea_bush")
    for z in (3, 4):
        stairs(b, 2, 1, z, "birch_stairs", "west")
        stairs(b, 8, 1, z, "birch_stairs", "east")
    b.set(2, 1, 5, "potted_azalea_bush")
    b.set(8, 1, 5, "potted_azalea_bush")
    for z in range(3, 6):
        b.set(5, 1, z, "red_carpet")
    for x in (3, 7):
        for z in range(3, 8):
            log(b, x, 4, z, CLINIC_FRAME, axis="z")
        lantern(b, x, 3, 4, hanging=True)


def clinic_roof(b, back=9):
    """The red roof over the hall (ridge along it, gables at the ends with a small window each) and the porch's
    little gable in front, lower, over the door: posts, a beam, lanterns either side."""
    gable_roof(b, 0, 10, 1, back, 5, CLINIC_ROOF, axis="x", gable=CLINIC_WALL, gable_at=(1, 9), eave_trim=CLINIC_TRIM)
    for x in (1, 9):
        log(b, x, 5, 5, CLINIC_FRAME, axis="x")
        pane(b, x, 6, 5)
        pane(b, x, 7, 5)
    # The porch: a cross gable over the door bay, higher than the eaves (its ridge meets the roof's), on two posts
    b.clear(4, 4, 1, 6, 5, 1)  # the eave comes off where the porch runs in
    for x in (4, 6):
        posts(b, [(x, 0)], 1, 4, CLINIC_FRAME)
        log(b, x, 5, 0, CLINIC_FRAME, axis="z")
        log(b, x, 5, 1, CLINIC_FRAME, axis="z")
    log(b, 5, 5, 0, CLINIC_FRAME, axis="x")
    gable_roof(b, 3, 7, 0, 3, 6, CLINIC_ROOF, axis="z", gable=CLINIC_WALL, gable_at=(0,), eave_trim=CLINIC_TRIM)
    pane(b, 5, 6, 0)  # a little window in the porch's gable, a king post over it
    log(b, 5, 7, 0, CLINIC_FRAME)
    box(b, 4, 0, 0, 6, 0, 1, "polished_andesite")
    lantern(b, 5, 4, 0, hanging=True)


def clinic_garden_front(b):
    """Flower beds either side of the porch along the plinth."""
    for x in (1, 2, 8, 9):
        b.set(x, 0, 1, "grass_block", snowy=False)
    for x, plant in ((1, "red_tulip"), (2, "azure_bluet"), (8, "azure_bluet"), (9, "red_tulip")):
        b.set(x, 1, 1, plant)


def healing_center():
    """11 x 10 x 10: a white clinic under a red roof, dark posts, a gabled porch with a red heart over the door (with
    Cobblemon the counter holds a Healing Machine; without, that block loads as air and the builder skips it)."""
    b = Build(11, 10, 10)
    clinic_hall(b)
    clinic_roof(b)
    clinic_garden_front(b)
    b.fill_air()
    return b


# --- Healing Center, upgraded: a ward behind the hall ---------------------------------------------------
def clinic_ward(b):
    """The ward (walls x 3-7, z 8-14) behind the hall, a door through from behind the counter: four beds (villagers who
    sleep there are in the nurse's reach), its own lower roof running back, a chimney on the east side."""
    skirt(b, 3, 9, 7, 14, STONE_BRICK)
    for z in range(9, 15):
        for x in (3, 7):
            b.set(x, 0, z, MOSSY_BRICK_MIX.at(x, 0, z))
    for x in range(3, 8):
        b.set(x, 0, 14, MOSSY_BRICK_MIX.at(x, 0, 14))
    box(b, 4, 0, 9, 6, 0, 13, "smooth_quartz")
    for z in range(9, 15):
        for x in (3, 7):
            for y in (1, 2, 3):
                b.set(x, y, z, CLINIC_WALL.at(x, y, z))
    for x in range(4, 7):
        for y in (1, 2, 3):
            b.set(x, y, 14, CLINIC_WALL.at(x, y, 14))
    posts(b, [(3, 11), (7, 11), (3, 14), (7, 14)], 1, 3, CLINIC_FRAME)
    for z in range(9, 15):
        log(b, 3, 4, z, CLINIC_FRAME, axis="z")
        log(b, 7, 4, z, CLINIC_FRAME, axis="z")
        log(b, 5, 4, z, CLINIC_FRAME, axis="z")  # a beam down the middle, for the lantern (and the builder)
    for x in range(4, 7):
        log(b, x, 4, 14, CLINIC_FRAME, axis="x")
    b.set(5, 2, 8, "air")
    door(b, 5, 1, 8, "birch_door", "south")
    for z in (9, 12):
        window(b, 3, 2, z + (1 if z == 12 else 0), "west", sill=STONE_BRICK)
        window(b, 7, 2, z + (1 if z == 12 else 0), "east", sill=STONE_BRICK)
    window(b, 5, 2, 14, "south", sill=STONE_BRICK)
    for x in (4, 6):
        b.bed(x, 1, 9, "white", facing="south")
        b.bed(x, 1, 12, "white", facing="south")
    for z in range(9, 14):
        b.set(5, 1, z, "red_carpet")
    lantern(b, 5, 3, 11, hanging=True)
    gable_roof(b, 2, 8, 9, 15, 4, CLINIC_ROOF, axis="z", gable=CLINIC_WALL, gable_at=(14,), eave_trim=CLINIC_TRIM)
    chimney(b, 8, 12, 0, 7, BRICK_WALL_MIX)


def healing_center_2():
    """Upgrade of the Healing Center: a ward with four beds behind the hall under its own lower roof, a chimney beside
    it. 11 x 10 x 16."""
    b = healing_center().grow(11, 10, 16)
    clinic_ward(b)
    b.fill_air()
    return b


# --- Healing Center III: a berry garden behind the ward -------------------------------------------------
def healing_center_3():
    """Upgrade of Healing Center II: a fenced garden behind the ward, sweet berry bushes either side of a path, a Fruit
    Basket and a chest (a villager moves in as the orchard keeper and picks the berries), lamps and benches, and a back
    door out of the ward. 11 x 10 x 22."""
    b = healing_center_2().grow(11, 10, 22)
    walls(b, 0, 16, 10, 21, 0, 0, "stone_bricks")
    box(b, 1, 0, 17, 9, 0, 20, "grass_block", snowy=False)
    for z in range(15, 21):
        b.set(5, 0, z, "dirt_path")
    for x in range(0, 11):
        fence(b, x, 1, 21, "dark_oak_fence")
    for z in range(16, 21):
        fence(b, 0, 1, z, "dark_oak_fence")
        fence(b, 10, 1, z, "dark_oak_fence")
    b.set(10, 1, 18, "dark_oak_fence_gate", facing="east", open=False, in_wall=False, powered=False)
    for x in (1, 2, 3, 7, 8, 9):
        for z in (17, 19):
            if (x + z) % 2 == 0:
                b.set(x, 1, z, "sweet_berry_bush", age=0)
            else:
                b.set(x, 1, z, "rose_bush", half="lower")
                b.set(x, 2, z, "rose_bush", half="upper")
    b.set(4, 1, 20, "aliveworkplace:fruit_basket", facing="north")
    b.set(6, 1, 20, "chest", facing="north", type="single", waterlogged=False)
    for x, z in ((0, 21), (10, 21)):
        lantern(b, x, 2, z)
    stairs(b, 1, 1, 20, "dark_oak_stairs", "south")
    stairs(b, 9, 1, 20, "dark_oak_stairs", "south")
    b.set(5, 2, 14, "air")
    door(b, 5, 1, 14, "birch_door", "north")
    b.fill_air()
    return b


# --- Supply Shop: a timber shop with its gable to the street, display windows under a striped awning ---------
SHOP_FRAME_LOG = "stripped_spruce_log"
SHOP_PLASTER = Mix((8, "white_concrete"), (2, "polished_diorite"), seed=22)
SHOP_ROOF = Family("dark_prismarine", "dark_prismarine_stairs", "dark_prismarine_slab")


def shop_hall(b):
    """The shop (walls x 1-9, z 2-8), its gable to the street: white plaster between spruce posts on a stone plinth, a
    display window either side of the door under a blue-and-white awning; inside shelves of barrels down both sides, the
    Shop Counter at the back (a villager moves in as shopkeeper; the barrels are the stock, the first player to open it
    owns the shop), tie beams across for the lantern (and for the builder)."""
    skirt(b, 1, 2, 9, 8, STONE_BRICK)
    plinth(b, 1, 2, 9, 8, MOSSY_BRICK_MIX, floor="spruce_planks")
    walls(b, 1, 2, 9, 8, 1, 4, SHOP_PLASTER)
    posts(b, [(x, z) for x in (1, 4, 6, 9) for z in (2, 8)] + [(x, 5) for x in (1, 9)], 1, 4, SHOP_FRAME_LOG)
    beam_ring(b, 1, 2, 9, 8, 5, SHOP_FRAME_LOG)
    # The shop front: two display windows (a shelf behind the glass), the door between, the awning over all three
    for x0 in (2, 7):
        for x in (x0, x0 + 1):
            for y in (1, 2):
                pane(b, x, y, 2)
            slab(b, x, 1, 3, "spruce_slab", top=True)
        pane(b, x0, 3, 2, "glass_pane") if False else None
    b.set(2, 2, 3, "potted_cactus")
    b.set(8, 2, 3, "potted_bamboo")
    door(b, 5, 1, 2, "spruce_door", "south")
    pane(b, 5, 3, 2)
    stairs(b, 5, 0, 1, STONE_BRICK, "south")
    for x in range(1, 10):
        b.set(x, 4, 1, "blue_wool" if x % 2 else "white_wool")
    # The sides and the back
    for z0 in (3, 6):
        window(b, 1, 2, z0, "west", width=2, shutters="spruce_trapdoor", sill=SPRUCE)
        window(b, 9, 2, z0, "east", width=2, shutters="spruce_trapdoor", sill=SPRUCE)
    for x0 in (2, 7):
        window(b, x0, 2, 8, "south", width=2, shutters="spruce_trapdoor", sill=SPRUCE)
    # Shelves: barrels two high down both sides; the counter at the back with the Shop Counter in the middle
    for z in (4, 5):
        for y in (1, 2):
            b.set(2, y, z, "barrel", facing="east", open=False)
            b.set(8, y, z, "barrel", facing="west", open=False)
    for x in (3, 4, 6, 7):
        b.set(x, 1, 6, "spruce_planks")
        slab(b, x, 2, 6, "spruce_slab")
    b.set(5, 1, 6, "aliveworkplace:shop_counter", facing="south")
    b.set(3, 2, 6, "lantern", hanging=False, waterlogged=False)
    stairs(b, 5, 1, 7, SPRUCE, "south")  # the shopkeeper's seat
    b.set(5, 1, 3, "light_blue_carpet")
    b.set(5, 1, 4, "light_blue_carpet")
    for z in (3, 7):
        for x in range(2, 9):
            log(b, x, 5, z, SHOP_FRAME_LOG, axis="x")
    lantern(b, 5, 4, 3, hanging=True)
    lantern(b, 5, 4, 7, hanging=True)


def shop_roof(b):
    """The blue-slate roof, ridge running back from the street; the front gable with a tall window over the awning,
    lamps either side of the door."""
    gable_roof(b, 0, 10, 1, 9, 5, SHOP_ROOF, axis="z", gable=SHOP_PLASTER, gable_at=(2, 8), eave_trim=SPRUCE)
    for z in (2, 8):
        pane(b, 5, 6, z)
        pane(b, 5, 7, z)
        stairs(b, 5, 8, z, SPRUCE, "north" if z == 2 else "south", top=True)
        log(b, 5, 9, z, SHOP_FRAME_LOG)
        for x in (3, 7):  # studs in the gable
            log(b, x, 6, z, SHOP_FRAME_LOG)
            log(b, x, 7, z, SHOP_FRAME_LOG)
    for x in (0, 10):
        lamp_post(b, x, 0, 1, "spruce_fence", height=2)


def supply_shop():
    """11 x 11 x 10: a timber shop with its gable to the street under a blue-slate roof, display windows either side of
    the door under a striped awning."""
    b = Build(11, 11, 10)
    shop_hall(b)
    shop_roof(b)
    b.fill_air()
    return b


# --- Supply Shop, upgraded: the shopkeeper's house behind ---------------------------------------------------
def shop_house(b):
    """The shopkeeper's house (walls x 2-8, z 9-14) behind the shop, its roof across it (a T), through a door behind the
    counter: a bed, a chest, a stove, more stock, a chimney."""
    skirt(b, 2, 9, 8, 14, STONE_BRICK)
    plinth(b, 2, 8, 8, 14, MOSSY_BRICK_MIX, floor="spruce_planks")
    for z in range(9, 15):
        for x in (2, 8):
            for y in (1, 2, 3):
                b.set(x, y, z, SHOP_PLASTER.at(x, y, z))
    for x in range(3, 8):
        for y in (1, 2, 3):
            b.set(x, y, 14, SHOP_PLASTER.at(x, y, 14))
    posts(b, [(2, 11), (8, 11), (2, 14), (8, 14), (5, 14)], 1, 3, SHOP_FRAME_LOG)
    for z in range(9, 15):
        log(b, 2, 4, z, SHOP_FRAME_LOG, axis="z")
        log(b, 8, 4, z, SHOP_FRAME_LOG, axis="z")
    for x in range(3, 8):
        log(b, x, 4, 14, SHOP_FRAME_LOG, axis="x")
        log(b, x, 4, 11, SHOP_FRAME_LOG, axis="x")
    b.set(5, 2, 8, "air")
    door(b, 5, 1, 8, "spruce_door", "south")
    stairs(b, 5, 1, 7, SPRUCE, "west")  # the seat turned, so the door opens
    window(b, 2, 2, 12, "west", width=2, shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 8, 2, 12, "east", width=2, shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 3, 2, 14, "south", sill=SPRUCE)
    window(b, 7, 2, 14, "south", sill=SPRUCE)
    b.bed(3, 1, 12, "blue", facing="south")
    b.set(3, 1, 10, "chest", facing="east", type="single", waterlogged=False)
    b.set(7, 1, 13, "barrel", facing="up", open=False)
    b.set(7, 1, 12, "barrel", facing="up", open=False)
    b.set(7, 2, 13, "barrel", facing="up", open=False)
    b.set(4, 1, 13, "potted_fern")
    box(b, 4, 1, 10, 6, 1, 11, "light_blue_carpet")
    lantern(b, 5, 3, 11, hanging=True)
    gable_roof(b, 1, 9, 9, 15, 4, SHOP_ROOF, axis="x", gable=SHOP_PLASTER, gable_at=(2, 8), eave_trim=SPRUCE)
    chimney(b, 5, 15, 0, 9, BRICK_WALL_MIX)


def supply_shop_2():
    """Upgrade of the Supply Shop: the shopkeeper's house behind, its roof across the shop's (a T), with a bed, a chest,
    more stock and a chimney. 11 x 11 x 16."""
    b = supply_shop().grow(11, 11, 16)
    shop_house(b)
    b.fill_air()
    return b


# --- Supply Shop III: a post office annex --------------------------------------------------------------
def supply_shop_3():
    """Upgrade of Supply Shop II: a one-storey post office on the east side, set back a little, through a doorway from the
    shop, with a Postal Desk and a chest (a villager moves in as the postman: the shop's goods can go out by mail), its
    own blue awning and a bench out front. 17 x 11 x 16."""
    b = supply_shop_2().grow(17, 11, 16)
    skirt(b, 10, 3, 15, 8, STONE_BRICK)
    plinth(b, 9, 3, 15, 8, MOSSY_BRICK_MIX, floor="spruce_planks")
    for y in range(1, 4):
        for x in range(10, 16):
            b.set(x, y, 3, SHOP_PLASTER.at(x, y, 3))
            b.set(x, y, 8, SHOP_PLASTER.at(x, y, 8))
        for z in range(4, 8):
            b.set(15, y, z, SHOP_PLASTER.at(15, y, z))
    posts(b, [(12, 3), (15, 3), (15, 8), (12, 8)], 1, 3, SHOP_FRAME_LOG)
    for x in range(10, 16):
        log(b, x, 4, 3, SHOP_FRAME_LOG, axis="x")
        log(b, x, 4, 8, SHOP_FRAME_LOG, axis="x")
    for z in range(4, 8):
        log(b, 15, 4, z, SHOP_FRAME_LOG, axis="z")
    for y in (1, 2):
        b.set(9, y, 6, "air")  # through from the shop, between the east windows
    b.set(9, 2, 6, "air")
    door(b, 13, 1, 3, "spruce_door", "south")
    stairs(b, 13, 0, 2, STONE_BRICK, "south")
    window(b, 10, 2, 3, "north", sill=SPRUCE)
    window(b, 15, 2, 5, "east", width=2, shutters="spruce_trapdoor", sill=SPRUCE)
    for x in range(10, 17):
        b.set(x, 4, 2, "blue_wool" if x % 2 else "white_wool")
    gable_roof(b, 9, 16, 2, 9, 5, SHOP_ROOF, axis="x", gable=SHOP_PLASTER, gable_at=(15,), eave_trim=SPRUCE)
    b.set(14, 1, 6, "aliveworkplace:postal_desk", facing="west")
    b.set(14, 1, 4, "chest", facing="west", type="single", waterlogged=False)
    b.set(10, 1, 4, "chiseled_bookshelf", facing="east")
    b.set(12, 1, 5, "light_blue_carpet")
    lantern(b, 12, 3, 5, hanging=True)
    for z in range(4, 8):
        log(b, 12, 4, z, SHOP_FRAME_LOG, axis="z")
    stairs(b, 15, 0, 1, SPRUCE, "south")
    stairs(b, 16, 0, 1, SPRUCE, "south")
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
        for z in range(8, 15):  # tie beams across the back range, a lantern hanging from each
            log(b, x, 5, z, "stripped_spruce_log", axis="z")
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
    """11 x 9 x 11: a stone lab under a copper hip roof (it greens with the years) with a glass skylight, a columned porch, a Fossil Lab at the
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
        slab(b, x, 4, 0, CUT_COPPER, top=True)
    # The roof: deepslate tiles, a glass skylight on top
    hip_roof(b, 0, 10, 0, 10, 5, CUT_COPPER, rings=3)
    walls(b, 3, 3, 7, 7, 8, 8, "cut_copper")
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
    hip_roof(b, 0, 10, 0, 10, 5, CUT_COPPER, rings=3)
    walls(b, 3, 3, 7, 7, 8, 8, "cut_copper")
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
    gable_roof(b, 10, 17, 1, 9, 5, CUT_COPPER, axis="x", gable=BRICK_WALL_MIX, gable_at=(16, 16))
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
    b.blocks[(14, 1, 1)] = b.blocks[(13, 1, 1)]  # the flower box runs on under the lantern
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


# --- Town Hall (research): a stone hall under a hipped roof with a bell tower over the door ------------------------
HALL_FRAME = "dark_oak_log"
HALL_ROOF = DEEPSLATE_TILE


def town_hall():
    """13 x 16 x 13, drawn up by a village's scholars (Architecture research): a tall stone hall — the Village Hall block
    at the head of a council table, benches, bookshelves, lamps on the beams — under a hipped slate roof, entered through
    a bell tower that stands out in front with a pointed cap."""
    b = Build(13, 16, 13)
    # The hall: walls x 1-11, z 3-11, five high
    plinth(b, 1, 3, 11, 11, FOUNDATION_MIX, floor="polished_andesite")
    walls(b, 1, 3, 11, 11, 1, 5, BRICK_WALL_MIX)
    for x, z in ((1, 3), (11, 3), (1, 11), (11, 11), (1, 7), (11, 7)):
        box(b, x, 1, z, x, 5, z, "polished_andesite")
    for z in (5, 9):
        window(b, 1, 2, z, "west", height=3, sill=STONE_BRICK, lintel=STONE_BRICK)
        window(b, 11, 2, z, "east", height=3, sill=STONE_BRICK, lintel=STONE_BRICK)
    for x in (3, 9):
        window(b, x, 2, 3, "north", height=3, sill=STONE_BRICK, lintel=STONE_BRICK)
    window(b, 4, 3, 11, "south", width=5, height=2, sill=STONE_BRICK)
    beam_ring(b, 1, 3, 11, 11, 6, HALL_FRAME)
    for z in (6, 9):  # tie beams for the lamps
        for x in range(2, 11):
            log(b, x, 5, z, HALL_FRAME, axis="x")
        lantern(b, 4, 4, z, hanging=True)
        lantern(b, 8, 4, z, hanging=True)
    # The council: the Village Hall at the head of a long table, benches along the walls, bookshelves at the back
    b.set(6, 1, 10, "aliveworkplace:village_hall", facing="north")
    for z in range(5, 9):
        slab(b, 6, 1, z, DARK_OAK, top=True)
        stairs(b, 5, 1, z, DARK_OAK, "west")
        stairs(b, 7, 1, z, DARK_OAK, "east")
    for x in (2, 10):
        for z in (5, 6, 8, 9):
            stairs(b, x, 1, z, DARK_OAK, "east" if x == 2 else "west")
    for x in (2, 3, 9, 10):
        for y in (1, 2, 3):
            b.set(x, y, 10, "bookshelf")
    box(b, 4, 1, 4, 8, 1, 4, "red_carpet")
    # The hipped slate roof
    hip_roof(b, 0, 12, 2, 12, 6, HALL_ROOF)
    # The tower in front: x 5-7, z 1-3, through the hall's front wall; the door at its foot, the belfry at the top
    for y in range(1, 12):
        for x in range(5, 8):
            for z in range(1, 4):
                if x in (5, 7) or z in (1, 3):
                    corner = x in (5, 7) and z in (1, 3)
                    b.set(x, y, z, "polished_andesite" if corner else BRICK_WALL_MIX)
                else:
                    b.set(x, y, z, "air")
    box(b, 5, 0, 1, 7, 0, 3, FOUNDATION_MIX)  # the tower's footing
    b.set(6, 0, 2, "polished_andesite")
    door(b, 6, 1, 1, "dark_oak_door", "south")
    stairs(b, 6, 0, 0, STONE_BRICK, "south")
    b.set(6, 3, 1, "chiseled_stone_bricks")
    for y in (1, 2):
        b.set(6, y, 3, "air")  # through into the hall
    pane(b, 6, 5, 1)
    pane(b, 6, 6, 1)
    for (x, z) in ((6, 1), (5, 2), (7, 2)):  # the belfry's openings
        for y in (9, 10):
            b.set(x, y, z, "air")
    b.set(6, 11, 2, "polished_andesite")
    b.set(6, 10, 2, "bell", attachment="ceiling", facing="north", powered=False)
    for (x, z) in ((5, 1), (7, 1), (5, 3), (7, 3)):
        b.set(x, 11, z, "chiseled_stone_bricks")
    hip_roof(b, 4, 8, 0, 4, 12, HALL_ROOF)
    # Out front: lamp posts either side of the tower
    lamp_post(b, 3, 0, 1, "dark_oak_fence", height=2)
    lamp_post(b, 9, 0, 1, "dark_oak_fence", height=2)
    b.fill_air()
    return b

# --- Schoolhouse: a one-room school with a bell on the ridge ------------------------------------------------------
SCHOOL_FRAME = "spruce_log"
SCHOOL_INFILL = "white_terracotta"
SCHOOL_ROOF = SPRUCE


def schoolhouse_room(b):
    """The schoolroom (walls x 1-9, z 2-10): stone plinth, spruce frame with white walls, tall windows down both sides,
    the door under a porch at the front; inside a Teacher's Desk before a blackboard, three rows of benches, a bookshelf."""
    plinth(b, 1, 2, 9, 10, STONE_MIX, floor="spruce_planks")
    walls(b, 1, 2, 9, 10, 1, 4, SCHOOL_INFILL)
    posts(b, [(1, 2), (9, 2), (1, 10), (9, 10), (1, 6), (9, 6), (3, 2), (7, 2)], 1, 4, SCHOOL_FRAME)
    beam_ring(b, 1, 2, 9, 10, 5, SCHOOL_FRAME)
    door(b, 5, 1, 2, "spruce_door", "south")
    stairs(b, 5, 0, 1, COBBLE, "south")
    log(b, 5, 3, 2, SCHOOL_FRAME, axis="x")
    for x in (4, 5, 6):  # the porch roof over the door
        stairs(b, x, 4, 1, SCHOOL_ROOF, "south")
    fence(b, 4, 1, 1, "spruce_fence")
    fence(b, 6, 1, 1, "spruce_fence")
    b.set(4, 2, 1, "lantern", hanging=False, waterlogged=False)
    for z in (4, 8):
        window(b, 1, 2, z, "west", height=2, shutters="spruce_trapdoor", sill=SPRUCE)
        window(b, 9, 2, z, "east", height=2, shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 2, 2, 2, "north", height=2, shutters="spruce_trapdoor",
           flowers=("spruce_trapdoor", ["potted_dandelion"]))
    window(b, 8, 2, 2, "north", height=2, shutters="spruce_trapdoor",
           flowers=("spruce_trapdoor", ["potted_blue_orchid"]))
    # The blackboard on the back wall, the teacher's desk before it, the benches facing it
    box(b, 3, 2, 10, 7, 3, 10, "black_concrete")
    b.set(5, 1, 9, "aliveworkplace:teachers_desk", facing="north")
    for z in (4, 5, 6):
        for x in (3, 4, 6, 7):
            stairs(b, x, 1, z, SPRUCE, "north")
    b.set(2, 1, 9, "bookshelf")
    b.set(2, 2, 9, "bookshelf")
    b.set(8, 1, 9, "potted_fern")
    box(b, 2, 5, 3, 8, 5, 9, "spruce_planks")  # the loft floor (the lights hang from it)
    lantern(b, 3, 4, 6, hanging=True)
    lantern(b, 7, 4, 6, hanging=True)
    b.set(5, 3, 1, "bell", attachment="ceiling", facing="north", powered=False)  # the school bell, under the porch


def school_building():
    """11 x 11 x 12: a one-room village school — white walls in a spruce frame, a porch over the door with the school
    bell under it, the Teacher's Desk before a blackboard and benches for the children, a loft under the roof."""
    b = Build(11, 11, 12)
    schoolhouse_room(b)
    gable_roof(b, 0, 10, 1, 11, 5, SCHOOL_ROOF, axis="z", gable=SCHOOL_INFILL, gable_at=(2, 10), ridge=SPRUCE)
    for z in (2, 10):
        log(b, 5, 6, z, SCHOOL_FRAME)
        pane(b, 5, 7, z)
    b.fill_air()
    return b


def school_building_2():
    """Upgrade of the Schoolhouse (same origin and front): a fenced schoolyard on the east side with a sandpit, a bench
    and a young tree, reached by a door where a window was. 16 x 11 x 12."""
    b = school_building().grow(16, 11, 12)
    b.clear(10, 1, 7, 10, 3, 9)  # the east window's shutters and sill
    for y in (2, 3):
        b.set(9, y, 8, "air")
    door(b, 9, 1, 8, "spruce_door", "west", hinge="right")
    # The yard: x 10-15, z 3-11, a fence round it with a gate at the front
    for x in range(10, 16):
        for z in range(3, 12):
            b.set(x, 0, z, "grass_block", snowy=False)
    for x in range(10, 16):
        fence(b, x, 1, 3, "spruce_fence")
        fence(b, x, 1, 11, "spruce_fence")
    for z in range(4, 11):
        fence(b, 15, 1, z, "spruce_fence")
    b.set(12, 1, 3, "spruce_fence_gate", facing="south", open=False, powered=False, in_wall=False)
    # A sandpit edged with trapdoors, a bench, a sapling, a lamp at the corner
    for x in range(11, 14):
        for z in range(8, 11):
            b.set(x, 0, z, "sand")
    for x in range(11, 14):
        trapdoor(b, x, 1, 7, "spruce_trapdoor", "south", half="bottom")
    stairs(b, 14, 1, 5, SPRUCE, "west")
    stairs(b, 14, 1, 6, SPRUCE, "west")
    b.set(11, 1, 5, "oak_sapling", stage=0)
    b.set(11, 0, 5, "grass_block", snowy=False)
    lamp_post(b, 15, 1, 3, "spruce_fence", height=2)
    b.fill_air()
    return b


# --- Library: a stone reading hall for the scholars ---------------------------------------------------------------
LIBRARY_ROOF = DEEPSLATE_TILE


def library_hall(b):
    """The reading hall (walls x 1-11, z 2-10): stone brick walls with buttresses and tall arched windows, a Scholar's
    Desk under the back window, bookshelves along both sides, two reading tables with lamps."""
    plinth(b, 1, 2, 11, 10, FOUNDATION_MIX, floor="spruce_planks")
    walls(b, 1, 2, 11, 10, 1, 5, BRICK_WALL_MIX)
    for x, z in ((1, 2), (11, 2), (1, 10), (11, 10)):
        box(b, x, 1, z, x, 5, z, "polished_andesite")
    for z in (4, 8):  # buttresses between the windows
        for x, face in ((0, "east"), (12, "west")):
            b.set(x, 0, z, "stone_bricks")
            b.set(x, 1, z, "stone_bricks")
            b.set(x, 2, z, "stone_bricks")
            stairs(b, x, 3, z, STONE_BRICK, face)
    for z in (6,):
        window(b, 1, 2, z, "west", height=3, sill=STONE_BRICK, lintel=STONE_BRICK)
        window(b, 11, 2, z, "east", height=3, sill=STONE_BRICK, lintel=STONE_BRICK)
    # The front: the door under a stone arch, a wide step, windows either side
    door(b, 6, 1, 2, "dark_oak_door", "south")
    stairs(b, 5, 3, 1, STONE_BRICK, "east", top=True)
    stairs(b, 7, 3, 1, STONE_BRICK, "west", top=True)
    b.set(6, 3, 1, "stone_brick_slab", type="top", waterlogged=False)
    b.set(6, 3, 2, "chiseled_stone_bricks")
    for x in (5, 7):
        b.set(x, 1, 1, "stone_brick_wall", north="none", south="none", east="none", west="none", up=True, waterlogged=False)
        b.set(x, 2, 1, "stone_brick_wall", north="none", south="none", east="none", west="none", up=True, waterlogged=False)
    for x in (5, 6, 7):
        stairs(b, x, 0, 1, STONE_BRICK, "south") if x == 6 else b.set(x, 0, 1, "stone_bricks")
    for x in (3, 9):
        window(b, x, 2, 2, "north", height=3, sill=STONE_BRICK, lintel=STONE_BRICK)
    # Inside: shelves down the sides, the desk under the back window, tables with lamps
    for z in (3, 4, 5, 7, 8, 9):
        for y in (1, 2, 3):
            b.set(2, y, z, "bookshelf")
            b.set(10, y, z, "bookshelf")
    window(b, 6, 2, 10, "south", height=3, sill=STONE_BRICK)
    b.set(6, 1, 9, "aliveworkplace:scholars_desk", facing="north")
    for tx in (4, 8):
        for tz in (5, 6):
            slab(b, tx, 1, tz, DARK_OAK, top=True)
        b.set(tx, 2, 5, "lantern", hanging=False, waterlogged=False)
        stairs(b, tx - 1 if tx == 4 else tx + 1, 1, 6, DARK_OAK, "east" if tx == 4 else "west")
    beam_ring(b, 1, 2, 11, 10, 6, "dark_oak_log")
    for x in range(2, 11):
        log(b, x, 6, 6, "dark_oak_log", axis="x")
    lantern(b, 6, 5, 6, hanging=True)


def library():
    """13 x 15 x 12: a stone library — buttressed walls with tall windows, double doors under an arch, shelves of books
    down both sides and a Scholar's Desk under the back window — under a steep slate roof."""
    b = Build(13, 15, 12)
    library_hall(b)
    gable_roof(b, 0, 12, 1, 11, 6, LIBRARY_ROOF, axis="z", gable="stone_bricks", gable_at=(2, 10), ridge=LIBRARY_ROOF)
    for z in (2, 10):
        pane(b, 6, 8, z)
        pane(b, 6, 9, z)
        b.set(6, 10, z, "chiseled_stone_bricks")
    b.fill_air()
    return b


def library_2():
    """Upgrade of the Library (same origin and front): a study tower on the east side, through a door where the east
    window was, with a second Scholar's Desk (two scholars research twice as fast) and a lookout under its cap.
    18 x 15 x 12."""
    b = library().grow(18, 15, 12)
    b.clear(12, 0, 3, 12, 6, 9)  # the east buttresses and the window's sill come off
    for y in (2, 3, 4):
        b.set(11, y, 6, "air")
    door(b, 11, 1, 6, "dark_oak_door", "east")
    b.set(11, 3, 6, BRICK_WALL_MIX.at(11, 3, 6))
    b.set(11, 4, 6, BRICK_WALL_MIX.at(11, 4, 6))
    # The tower: walls x 12-16, z 4-8, up to y 10
    plinth(b, 12, 4, 16, 8, FOUNDATION_MIX, floor="spruce_planks")
    b.set(11, 0, 6, "spruce_planks")
    for y in range(1, 11):
        for x in range(12, 17):
            for z in range(4, 9):
                if x in (12, 16) or z in (4, 8):
                    corner = x in (12, 16) and z in (4, 8)
                    b.set(x, y, z, "polished_andesite" if corner else BRICK_WALL_MIX.at(x, y, z))
    for y in (1, 2):
        b.set(12, y, 6, "air")
    for y in (5, 8):  # floors, with a ladder up the back corner
        box(b, 13, y, 5, 15, y, 7, "spruce_planks")
    for y in range(1, 9):
        b.set(15, y, 7, "ladder", facing="west", waterlogged=False)
    # Windows on each floor, the lookout's arches at the top
    for y in (2, 6):
        window(b, 16, y, 6, "east", height=2 if y == 2 else 1, sill=STONE_BRICK)
        window(b, 14, y, 4, "north", height=2 if y == 2 else 1, sill=STONE_BRICK)
    for (x, z) in ((14, 4), (16, 6), (14, 8)):
        b.set(x, 9, z, "air")
    b.set(14, 9, 6, "lantern", hanging=False, waterlogged=False)
    hip_roof(b, 11, 17, 3, 9, 11, LIBRARY_ROOF)
    # The second desk, shelves round it
    b.set(13, 1, 7, "aliveworkplace:scholars_desk", facing="west")
    b.set(15, 1, 5, "bookshelf")
    b.set(13, 1, 5, "bookshelf")
    b.set(13, 2, 5, "bookshelf")
    lantern(b, 14, 4, 6, hanging=True)
    b.fill_air()
    return b


# --- Ranch: a stable with a fenced paddock ---------------------------------------------------------------------
RANCH_FRAME = "dark_oak_log"
RANCH_ROOF = SPRUCE


def ranch_stable(b, x0, x1, trough=True):
    """A stable across the back (walls x0-x1, z 8-11): a cobble footing, a dark oak frame with spruce boards, its front
    open onto the paddock between the posts, a hay loft under the roof beams."""
    plinth(b, x0, 8, x1, 11, STONE_MIX, floor="coarse_dirt")
    for y in (1, 2, 3):
        for x in range(x0, x1 + 1):
            b.set(x, y, 11, "spruce_planks")
        for z in range(9, 11):
            b.set(x0, y, z, "spruce_planks")
            b.set(x1, y, z, "spruce_planks")
    posts(b, [(x, z) for x in range(x0, x1 + 1, 3) for z in (8, 11)] + [(x1, 8), (x1, 11)], 1, 3, RANCH_FRAME)
    beam_ring(b, x0, 8, x1, 11, 4, RANCH_FRAME)
    for x in range(x0 + 1, x1):  # the loft: beams across, hay on them
        log(b, x, 4, 9, RANCH_FRAME, axis="x")
        log(b, x, 4, 10, RANCH_FRAME, axis="x")
    for x in (x0 + 1, x0 + 2, x1 - 2, x1 - 1):
        b.set(x, 5, 10, "hay_block", axis="x")
    b.set(x1 - 1, 5, 9, "hay_block", axis="z")
    for x in range(x0 + 1, x1, 4):
        lantern(b, x + 1, 3, 9, hanging=True)
    if trough:
        b.set(x0 + 2, 1, 10, "aliveworkplace:feed_trough", facing="north")
        b.set(x0 + 1, 1, 10, "chest", facing="east", type="single", waterlogged=False)
    b.set(x1 - 1, 1, 10, "hay_block", axis="y")
    b.set(x1 - 1, 2, 10, "hay_block", axis="x")
    b.set(x1 - 2, 1, 10, "water_cauldron", level=3)


def paddock(b, x0, x1, z0, z1, gate_x):
    """A fence round the paddock (x0-x1, z0 to the stable at z1), a gate at the front with lamps on its posts."""
    for x in range(x0, x1 + 1):
        fence(b, x, 0, z0, "spruce_fence")
    for z in range(z0, z1 + 1):
        fence(b, x0, 0, z, "spruce_fence")
        fence(b, x1, 0, z, "spruce_fence")
    b.set(gate_x, 0, z0, "spruce_fence_gate", facing="south", open=False, powered=False, in_wall=False)
    for x in (gate_x - 1, gate_x + 1):
        fence(b, x, 1, z0, "spruce_fence")
        lantern(b, x, 2, z0)


def ranch():
    """13 x 10 x 13: a ranch — a timber stable across the back with a Feed Trough, a water trough, hay down below and up
    in the loft, and a fenced paddock in front with a gate between two lamps."""
    b = Build(13, 10, 13)
    ranch_stable(b, 1, 11)
    paddock(b, 0, 12, 0, 8, 6)
    gable_roof(b, 0, 12, 7, 12, 4, RANCH_ROOF, axis="x", gable="spruce_planks", gable_at=(1, 11), ridge=SPRUCE)
    # In the paddock: a hay bale to eat from, a tie post
    b.set(3, 0, 3, "hay_block", axis="y")
    fence(b, 9, 0, 3, "spruce_fence")
    fence(b, 9, 1, 3, "spruce_fence")
    b.fill_air()
    return b


def ranch_2():
    """Upgrade of the Ranch (same origin and front): the paddock doubled to the east with a second stable (more stalls,
    more hay) and a saddle rack. 20 x 10 x 13."""
    b = ranch().grow(20, 10, 13)
    b.clear(12, 0, 1, 12, 9, 7)  # the old east fence
    b.clear(12, 4, 7, 12, 9, 12)  # the old roof's east overhang
    ranch_stable(b, 12, 18, trough=False)
    for y in (1, 2, 3):
        for z in range(9, 11):
            b.set(11, y, z, "air")  # one long stable now
            b.set(12, y, z, "air")
    paddock(b, 0, 19, 0, 8, 6)
    for x in (13, 14, 15):
        b.set(x, 0, 0, "spruce_fence", north=False, south=False, east=False, west=False, waterlogged=False)
    b.set(15, 0, 0, "spruce_fence_gate", facing="south", open=False, powered=False, in_wall=False)
    gable_roof(b, 0, 19, 7, 12, 4, RANCH_ROOF, axis="x", gable="spruce_planks", gable_at=(1, 18), ridge=SPRUCE)
    # A saddle rack: armor stand? no — trapdoors on the wall with a barrel of tack
    for x in (14, 15, 16):
        trapdoor(b, x, 2, 10, "spruce_trapdoor", "north", half="top")
    b.set(17, 1, 10, "hay_block", axis="y")
    b.set(16, 5, 10, "hay_block", axis="x")
    b.fill_air()
    return b


# --- Apiary Garden: beehives on posts in a meadow, a honey shed ---------------------------------------------------
FLOWERS = ["poppy", "dandelion", "cornflower", "oxeye_daisy", "allium", "azure_bluet", "red_tulip", "orange_tulip", None]


def hive_on_post(b, x, z, facing="south", smoke=False):
    """A beehive on a fence post (a campfire at its foot when {@code smoke}: the smoke keeps the bees calm)."""
    if smoke:
        b.set(x, 0, z, "campfire", facing="north", lit=True, signal_fire=False, waterlogged=False)
    else:
        fence(b, x, 0, z, "oak_fence")
    fence(b, x, 1, z, "oak_fence")
    b.set(x, 2, z, "beehive", facing=facing, honey_level=0)


def honey_shed(b, x0, z0):
    """An open-fronted shed (x0 to x0+4, z0 to z0+3) with the Apiary, a chest, shelves of bottles and a spruce gable roof."""
    plinth(b, x0, z0, x0 + 4, z0 + 3, STONE_MIX, floor="oak_planks")
    for y in (1, 2, 3):
        for x in range(x0, x0 + 5):
            b.set(x, y, z0 + 3, "oak_planks")
        for z in range(z0 + 1, z0 + 3):
            b.set(x0, y, z, "oak_planks")
            b.set(x0 + 4, y, z, "oak_planks")
    posts(b, [(x0, z0), (x0 + 4, z0), (x0, z0 + 3), (x0 + 4, z0 + 3)], 1, 3, "stripped_oak_log")
    beam_ring(b, x0, z0, x0 + 4, z0 + 3, 4, "stripped_oak_log")
    gable_roof(b, x0 - 1, x0 + 5, z0 - 1, z0 + 4, 4, SPRUCE, axis="x", gable="oak_planks", gable_at=(x0, x0 + 4), ridge=SPRUCE,
               eave_trim=OAK)
    for x in range(x0 + 1, x0 + 4):  # a tie beam for the lantern (and for the builder to reach the ridge)
        log(b, x, 4, z0 + 1, "stripped_oak_log", axis="x")
    b.set(x0 + 2, 1, z0 + 2, "aliveworkplace:apiary", facing="north")
    b.set(x0 + 1, 1, z0 + 2, "chest", facing="north", type="single", waterlogged=False)
    for x in (x0 + 1, x0 + 3):
        trapdoor(b, x, 2, z0 + 2, "oak_trapdoor", "north", half="top")
    b.set(x0 + 3, 1, z0 + 2, "honey_block")
    lantern(b, x0 + 2, 3, z0 + 1, hanging=True)


def apiary_garden():
    """11 x 6 x 11: a beekeeper's garden — a meadow of flowers inside a low fence, four beehives on posts (one over a
    smouldering campfire), and a honey shed at the back with the Apiary, a chest and the honey."""
    b = Build(11, 8, 11)
    flower_bed(b, 1, 1, 9, 4, 0, FLOWERS)
    for x in range(0, 11):
        fence(b, x, 0, 0, "oak_fence")
    for z in range(0, 7):
        fence(b, 0, 0, z, "oak_fence")
        fence(b, 10, 0, z, "oak_fence")
    b.set(5, 0, 0, "oak_fence_gate", facing="south", open=False, powered=False, in_wall=False)
    b.set(5, 0, 1, "dirt_path")
    b.set(5, 0, 2, "dirt_path")
    b.set(5, 1, 1, "air")
    b.set(5, 1, 2, "air")
    for (x, z, smoke) in ((2, 2, True), (8, 2, False), (2, 4, False), (8, 4, False)):
        b.set(x, 1, z, "air")
        hive_on_post(b, x, z, smoke=smoke)
    honey_shed(b, 3, 6)
    for z in range(7, 11):
        fence(b, 0, 0, z, "oak_fence")
        fence(b, 10, 0, z, "oak_fence")
    for x in range(0, 11):
        fence(b, x, 0, 10, "oak_fence")
    b.fill_air()
    return b


def apiary_garden_2():
    """Upgrade of the Apiary Garden (same origin and front): the meadow runs on to the east with four more hives and a
    bench among the flowers. 17 x 6 x 11."""
    b = apiary_garden().grow(17, 8, 11)
    for z in range(0, 11):
        b.clear(10, 0, z, 10, 0, z)
    flower_bed(b, 10, 1, 15, 9, 0, FLOWERS)
    for x in range(10, 17):
        fence(b, x, 0, 0, "oak_fence")
        fence(b, x, 0, 10, "oak_fence")
    for z in range(0, 11):
        fence(b, 16, 0, z, "oak_fence")
    for (x, z, smoke) in ((11, 3, False), (14, 3, True), (11, 7, False), (14, 7, False)):
        b.set(x, 1, z, "air")
        hive_on_post(b, x, z, smoke=smoke)
    b.set(12, 0, 5, "oak_planks")
    stairs(b, 12, 1, 5, OAK, "west")
    stairs(b, 13, 1, 5, OAK, "west")
    b.set(13, 0, 5, "oak_planks")
    lamp_post(b, 16, 1, 0, "oak_fence", height=2)
    b.fill_air()
    return b


# --- Flower Shop: a little shop front with an awning and window boxes ---------------------------------------------
SHOP_FRAME = "stripped_birch_log"
SHOP_WALL = Mix((7, "bricks"), (2, "granite"), (1, "polished_granite"), seed=21)


def flower_shop_front(b):
    """The shop (walls x 1-7, z 2-7): brick walls on a stone plinth, a wide display window either side of the door under
    a striped awning, window boxes full of flowers; inside the Flower Stand, a chest, pots along a shelf."""
    plinth(b, 1, 2, 7, 7, STONE_MIX, floor="birch_planks")
    walls(b, 1, 2, 7, 7, 1, 4, SHOP_WALL)
    posts(b, [(1, 2), (7, 2), (1, 7), (7, 7)], 1, 4, SHOP_FRAME)
    beam_ring(b, 1, 2, 7, 7, 5, SHOP_FRAME)
    door(b, 4, 1, 2, "birch_door", "south")
    stairs(b, 4, 0, 1, STONE_BRICK, "south")
    for x in (2, 6):
        for y in (1, 2):
            pane(b, x, y, 2)
    for x in (3, 5):
        pane(b, x, 2, 2)
        b.set(x, 0, 1, "mossy_stone_bricks")  # a little pedestal each side of the step
        b.set(x, 1, 1, "potted_red_tulip" if x == 3 else "potted_allium")
    # The awning: red and white wool stripes, a slab lip
    for x in range(1, 8):
        b.set(x, 4, 1, "red_wool" if x % 2 else "white_wool")
    # Window boxes on the sides
    for z in (4,):
        window(b, 1, 2, z, "west", width=2, height=1, shutters="birch_trapdoor", flowers=("birch_trapdoor", ["potted_poppy", "potted_dandelion"]))
        window(b, 7, 2, z, "east", width=2, height=1, shutters="birch_trapdoor", flowers=("birch_trapdoor", ["potted_cornflower", "potted_lily_of_the_valley"]))
    # Inside: the stand and a chest by the back wall, a shelf of pots, a hanging light
    b.set(4, 1, 6, "aliveworkplace:flower_stand", facing="north")
    b.set(3, 1, 6, "chest", facing="north", type="single", waterlogged=False)
    for x in (2, 5, 6):
        trapdoor(b, x, 2, 6, "birch_trapdoor", "north", half="top")
        b.set(x, 3, 6, "potted_oxeye_daisy" if x != 5 else "potted_fern")
    lantern(b, 4, 4, 4, hanging=True)
    for x in range(2, 7):
        log(b, x, 5, 4, SHOP_FRAME, axis="x")


def flower_shop_building():
    """9 x 11 x 9: a little flower shop — brick walls, display windows full of pots under a striped awning, window boxes,
    the Flower Stand inside, under a dark oak roof with a flower box in the gable."""
    b = Build(9, 11, 9)
    flower_shop_front(b)
    gable_roof(b, 0, 8, 1, 8, 5, DARK_OAK, axis="z", gable="birch_planks", gable_at=(2, 7), ridge=DARK_OAK, eave_trim=BIRCH)
    window(b, 4, 6, 2, "north", height=1, shutters="birch_trapdoor", flowers=("birch_trapdoor", ["potted_pink_tulip"]))
    b.fill_air()
    return b


def flower_shop_building_2():
    """Upgrade of the Flower Shop (same origin and front): a glass greenhouse on the east side, through a door where the
    window box was, with beds of flowers under the glass. 15 x 11 x 9."""
    b = flower_shop_building().grow(15, 11, 9)
    b.clear(8, 0, 3, 8, 3, 6)  # the east window box and shutters
    for x in (7,):
        for z in (4, 5):
            b.set(x, 2, z, SHOP_WALL.at(x, 2, z))
    door(b, 7, 1, 5, "birch_door", "east")
    # The greenhouse: x 8-13, z 3-7, a stone kerb, glass walls and roof on birch posts
    plinth(b, 8, 3, 13, 7, STONE_MIX, floor=None)
    for x in range(9, 13):
        for z in range(4, 7):
            b.set(x, 0, z, "grass_block", snowy=False)
    b.set(8, 0, 5, "birch_planks")
    for y in (1, 2, 3):
        for x in range(8, 14):
            for z in range(3, 8):
                if x in (8, 13) or z in (3, 7):
                    b.set(x, y, z, "glass")
    posts(b, [(8, 3), (13, 3), (8, 7), (13, 7), (13, 5)], 1, 3, SHOP_FRAME)
    for y in (1, 2):
        b.set(8, y, 5, "air")
    for x in range(8, 14):
        for z in range(3, 8):
            b.set(x, 4, z, "glass")
    for x in range(8, 14):
        log(b, x, 4, 5, SHOP_FRAME, axis="x")
    for x in range(9, 13):
        for z in (4, 6):
            b.set(x, 1, z, FLOWERS[(x + z) % (len(FLOWERS) - 1)])
    lantern(b, 11, 3, 5, hanging=True)
    b.fill_air()
    return b


# --- Graveyard: a stone mortuary in a walled churchyard -------------------------------------------------------------
def headstone(b, x, z, kind=0):
    """A small headstone on the ground (a stone wall block) with a potted flower before it (a pot stands on any ground)."""
    b.set(x, 0, z, ["stone_brick_wall", "mossy_stone_brick_wall", "cobblestone_wall"][kind % 3],
          north="none", south="none", east="none", west="none", up=True, waterlogged=False)
    b.set(x, 0, z - 1, ["potted_poppy", "potted_oxeye_daisy", "potted_lily_of_the_valley", "potted_azure_bluet"][kind % 4])


def graveyard():
    """13 x 11 x 13: a quiet churchyard — a low wall of mossy cobblestone with a gate between two soul lanterns, rows of
    old headstones with flowers, a little yew — and a small stone mortuary at the back with the Undertaker's Table
    under a steep slate roof."""
    b = Build(13, 11, 13)
    for x in range(0, 13):
        wall_block(b, x, 0, 0, "mossy_cobblestone_wall" if x % 3 == 0 else "cobblestone_wall")
        wall_block(b, x, 0, 12, "mossy_cobblestone_wall" if x % 3 == 0 else "cobblestone_wall")
    for z in range(1, 12):
        wall_block(b, 0, 0, z, "mossy_cobblestone_wall" if z % 3 == 0 else "cobblestone_wall")
        wall_block(b, 12, 0, z, "mossy_cobblestone_wall" if z % 3 == 0 else "cobblestone_wall")
    for x in (5, 6, 7):
        b.set(x, 0, 0, "dark_oak_fence_gate", facing="south", open=False, powered=False, in_wall=True)
    for x in (4, 8):
        wall_block(b, x, 1, 0, "cobblestone_wall")
        lantern(b, x, 2, 0, soul=True)
    # Headstones in two rows either side of the way in
    k = 0
    for z in (3, 5):
        for x in (2, 3, 9, 10):
            headstone(b, x, z, k)
            k += 1
    # A little yew in the corner: a spruce trunk under a crown of (lasting) leaves
    for y in (0, 1, 2):
        log(b, 10, y, 8, "spruce_log")
    for x in range(9, 12):
        for z in range(7, 10):
            for y in (2, 3):
                if (x, z) != (10, 8) or y == 3:
                    b.set(x, y, z, "spruce_leaves", distance=1, persistent=True, waterlogged=False)
    b.set(10, 4, 8, "spruce_leaves", distance=1, persistent=True, waterlogged=False)
    # The mortuary: walls x 3-9, z 7-11
    plinth(b, 3, 7, 9, 11, STONE_MIX, floor="polished_andesite")
    walls(b, 3, 7, 9, 11, 1, 3, MOSSY_BRICK_MIX)
    for x, z in ((3, 7), (9, 7), (3, 11), (9, 11)):
        box(b, x, 1, z, x, 3, z, "polished_andesite")
    door(b, 6, 1, 7, "dark_oak_door", "south")
    stairs(b, 6, 0, 6, STONE_BRICK, "south")
    b.set(6, 3, 7, "chiseled_stone_bricks")
    for x in (4, 8):
        window(b, x, 2, 7, "north", height=1, glass="iron_bars", sill=STONE_BRICK)
    window(b, 3, 2, 9, "west", height=1, glass="iron_bars")
    window(b, 9, 2, 9, "east", height=1, glass="iron_bars")
    b.set(6, 1, 10, "aliveworkplace:undertakers_table", facing="north")
    b.set(5, 1, 10, "chest", facing="north", type="single", waterlogged=False)
    b.set(7, 1, 10, "candle", candles=4, lit=False, waterlogged=False)
    b.set(4, 1, 10, "potted_lily_of_the_valley")
    for x in range(4, 9):  # a tie beam for the lamp
        log(b, x, 4, 9, "dark_oak_log", axis="x")
    lantern(b, 6, 3, 9, soul=True, hanging=True)
    gable_roof(b, 2, 10, 6, 12, 4, DEEPSLATE_TILE, axis="x", gable=MOSSY_BRICK_MIX, gable_at=(3, 9), steep=True, ridge=DEEPSLATE_TILE)
    b.fill_air()
    return b


def graveyard_2():
    """Upgrade of the Graveyard (same origin and front): a lych-gate over the way in — dark oak posts, a beam and a slab
    canopy with a lantern under it — and soul lanterns on posts among the graves. 13 x 11 x 13."""
    b = graveyard().grow(13, 11, 13)
    for x in (4, 8):
        for y in (0, 1, 2, 3):
            b.set(x, y, 0, "dark_oak_log", axis="y")
    for x in range(5, 8):
        log(b, x, 3, 0, "dark_oak_log", axis="x")
    for x in range(3, 10):
        for z in (0, 1):
            slab(b, x, 4, z, DARK_OAK)
    lantern(b, 6, 2, 0, soul=True, hanging=True)
    for z in (2, 4):
        lamp_post(b, 5, 0, z, "dark_oak_fence", height=1)
        lamp_post(b, 7, 0, z, "dark_oak_fence", height=1)
    b.fill_air()
    return b
