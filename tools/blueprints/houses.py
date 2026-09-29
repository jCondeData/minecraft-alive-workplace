"""
Houses in tiers: homes that grow with the village. Each tier keeps the one before it and adds beds.

Stone House: I a stone cottage with a bed downstairs and one in the attic; II a timber-framed upper storey with two
more beds; III a stone wing at the back with two more.
"""
from kit import *

STONE_HOUSE_WALL = STONE_MIX
STONE_HOUSE_QUOIN = "stone_bricks"
STONE_HOUSE_FRAME = "dark_oak_log"
STONE_HOUSE_ROOF = DEEPSLATE_TILE
STONE_HOUSE_INFILL = Mix((8, "white_concrete"), (2, "polished_diorite"), seed=24)


def slate_roof(b, y, gable, face):
    """The Stone House's roof (x 0-10, z 0-8, its lowest row at {@code y}) with a wall dormer over the door bay: the front
    wall rises through the eave into a little gable of {@code face} with a window in it (x 5, y + 1), under its own
    roof, whose slopes die into the main one (valleys) and whose ridge runs back to it."""
    roofs(b,
          lambda t: gable_roof(t, 0, 10, 0, 8, y, STONE_HOUSE_ROOF, axis="x", gable=gable, gable_at=(1, 9), ridge=STONE_HOUSE_ROOF),
          lambda t: gable_roof(t, 3, 7, 0, 2, y + 1, STONE_HOUSE_ROOF, axis="z", gable=face, gable_at=(1,), ridge=STONE_HOUSE_ROOF))
    pane(b, 5, y + 1, 1)


def stone_house_ground_floor(b):
    """The ground floor every tier shares (walls x 1-9, z 1-7, four high): a cobbled plinth, stone walls with stone brick
    corners, a dark oak top plate, shuttered windows with flower boxes at the front, the door under a little hood, the
    floor above and a ladder up to it; inside a bed, a kitchen corner and a table."""
    plinth(b, 1, 1, 9, 7, STONE_MIX, floor="spruce_planks")
    walls(b, 1, 1, 9, 7, 1, 4, STONE_HOUSE_WALL)
    posts(b, [(1, 1), (9, 1), (1, 7), (9, 7)], 1, 4, STONE_HOUSE_QUOIN)
    beam_ring(b, 1, 1, 9, 7, 5, STONE_HOUSE_FRAME)
    box(b, 2, 5, 2, 8, 5, 6, "spruce_planks")
    # The door: a step, a stone lintel and a slab hood on the wall
    door(b, 5, 1, 1, "dark_oak_door", "south")
    stairs(b, 5, 0, 0, STONE_BRICK, "south")
    b.set(5, 3, 1, "stone_bricks")
    for x in (4, 5, 6):
        slab(b, x, 3, 0, DARK_OAK, top=True)
    lantern(b, 4, 2, 0, hanging=True)
    for x, pots in ((2, ["potted_red_tulip", "potted_oxeye_daisy"]), (7, ["potted_cornflower", "potted_poppy"])):
        window(b, x, 2, 1, "north", width=2, height=2, shutters="dark_oak_trapdoor", flowers=("spruce_trapdoor", pots))
    for z, side in ((4, "west"), (4, "east")):
        window(b, 1 if side == "west" else 9, 2, z, side, height=2, shutters="dark_oak_trapdoor", sill=STONE_BRICK)
    for x in (3, 7):
        window(b, x, 2, 7, "south", height=2, sill=STONE_BRICK)
    # Inside: a bed by the back wall, the kitchen corner, a table with a seat, the ladder up the east wall
    b.bed(7, 1, 5, "red", facing="south")
    b.set(2, 1, 6, "smoker", facing="east", lit=False)
    b.set(2, 1, 5, "crafting_table")
    b.set(3, 1, 6, "chest", facing="north", type="single", waterlogged=False)
    b.set(2, 1, 2, "barrel", facing="up", open=False)
    fence(b, 4, 1, 3, "dark_oak_fence")
    b.set(4, 2, 3, "dark_oak_pressure_plate", powered=False)
    stairs(b, 3, 1, 3, SPRUCE, "west")
    box(b, 4, 1, 5, 5, 1, 6, "brown_carpet")
    for y in range(1, 6):
        b.set(8, y, 3, "ladder", facing="west", waterlogged=False)


def stone_house():
    """11 x 11 x 9: a stone cottage — stone walls with stone brick corners on a cobbled plinth, shuttered windows with
    flower boxes, a steep-looking slate roof with stone gables and a chimney — a bed downstairs and one in the attic."""
    b = Build(11, 11, 9)
    stone_house_ground_floor(b)
    # The attic: a bed under the ridge, a chest and a lantern
    b.bed(3, 6, 4, "light_blue", facing="east")
    b.set(6, 6, 4, "chest", facing="west", type="single", waterlogged=False)
    lantern(b, 7, 6, 4)  # standing: the ridge it could hang from comes off for the upper storey
    slate_roof(b, 5, STONE_HOUSE_WALL, STONE_HOUSE_WALL)  # the dormer lights the attic bedroom
    for x in (1, 9):
        pane(b, x, 7, 4)
    chimney(b, 0, 4, 0, 9, STONE_MIX)
    b.fill_air()
    return b


def stone_house_upper_floor(b):
    """Stone House II's upper storey: dark oak posts and top plate with white infill, windows front and back, two beds."""
    walls(b, 1, 1, 9, 7, 6, 8, STONE_HOUSE_INFILL)
    posts(b, [(1, 1), (9, 1), (1, 7), (9, 7), (3, 1), (7, 1), (3, 7), (7, 7), (1, 4), (9, 4)], 6, 8, STONE_HOUSE_FRAME)
    beam_ring(b, 1, 1, 9, 7, 9, STONE_HOUSE_FRAME)
    for x in (2, 5, 8):
        window(b, x, 6, 1, "north", height=2, lintel=DARK_OAK if x == 5 else None)
    for x in (2, 5, 8):
        window(b, x, 6, 7, "south", height=2)
    for x in (1, 9):
        window(b, x, 6, 2, "west" if x == 1 else "east", height=2)
    # Upstairs: the attic bed stays, a second one across the room, a bookshelf and a rug
    b.bed(3, 6, 4, "light_blue", facing="east")
    b.bed(6, 6, 6, "white", facing="west")
    b.set(6, 6, 4, "chest", facing="west", type="single", waterlogged=False)
    lantern(b, 7, 6, 4)
    b.set(2, 6, 6, "bookshelf")
    b.set(2, 7, 6, "potted_fern")
    box(b, 4, 6, 2, 6, 6, 3, "light_gray_carpet")
    for x in range(2, 9):  # a tie beam under the ridge to hang the lamp from (and to stand on)
        log(b, x, 9, 4, STONE_HOUSE_FRAME, axis="x")
    lantern(b, 5, 8, 4, hanging=True)


def stone_house_2():
    """Upgrade of the Stone House (same origin and front): the ground floor stays, the roof comes off, and a
    timber-framed upper storey with white plaster goes on with two bedrooms under a new slate roof. 11 x 16 x 9."""
    b = Build(11, 16, 9)
    stone_house_ground_floor(b)
    stone_house_upper_floor(b)
    slate_roof(b, 9, STONE_HOUSE_INFILL, STONE_HOUSE_INFILL)  # the dormer over the middle bay, above the upstairs window
    for x in (1, 9):
        log(b, x, 10, 4, STONE_HOUSE_FRAME)
        pane(b, x, 11, 4)
    chimney(b, 0, 4, 0, 14, STONE_MIX)
    b.fill_air()
    return b


def stone_house_3():
    """Upgrade of Stone House II (same origin and front): a stone wing at the back, through a doorway in the back wall,
    with two more beds under its own lower roof. 11 x 16 x 14."""
    b = stone_house_2().grow(11, 16, 14)
    # The back windows' sills come off (they're inside now) and a doorway opens between them
    for x in (3, 7):
        b.set(x, 1, 8, "air")
    for y in (1, 2):
        b.set(5, y, 7, "air")
    # The wing: walls x 2-8, z 7-12 (the house's back wall is its front)
    plinth(b, 2, 7, 8, 12, STONE_MIX, floor="spruce_planks")
    b.set(5, 0, 7, "spruce_planks")
    for y in range(1, 5):
        for z in range(8, 13):
            b.set(2, y, z, STONE_HOUSE_QUOIN if z == 12 else STONE_HOUSE_WALL.at(2, y, z))
            b.set(8, y, z, STONE_HOUSE_QUOIN if z == 12 else STONE_HOUSE_WALL.at(8, y, z))
        for x in range(3, 8):
            b.set(x, y, 12, STONE_HOUSE_WALL.at(x, y, 12))
    for z in range(8, 13):
        log(b, 2, 5, z, STONE_HOUSE_FRAME, axis="z")
        log(b, 8, 5, z, STONE_HOUSE_FRAME, axis="z")
    for x in range(3, 8):
        log(b, x, 5, 12, STONE_HOUSE_FRAME, axis="x")
    for x in range(3, 8):  # the tie beam to hang the lamp from
        log(b, x, 5, 10, STONE_HOUSE_FRAME, axis="x")
    lantern(b, 5, 4, 10, hanging=True)
    window(b, 2, 2, 10, "west", height=2, shutters="dark_oak_trapdoor", sill=STONE_BRICK)
    window(b, 8, 2, 10, "east", height=2, shutters="dark_oak_trapdoor", sill=STONE_BRICK)
    window(b, 5, 2, 12, "south", height=2, sill=STONE_BRICK)
    # Its roof, lower than the house's, the ridge running back from the house wall; the house's eave stays over its top
    keep = {p: v for p, v in b.blocks.items() if p[2] == 8 and p[1] >= 9}
    gable_roof(b, 1, 9, 8, 13, 5, STONE_HOUSE_ROOF, axis="z", gable=STONE_HOUSE_WALL, gable_at=(12, 12), ridge=STONE_HOUSE_ROOF)
    b.blocks.update(keep)
    # Inside: two beds, a chest between them, a rug
    b.bed(3, 1, 9, "green", facing="south")
    b.bed(7, 1, 9, "lime", facing="south")
    b.set(5, 1, 11, "chest", facing="north", type="single", waterlogged=False)
    box(b, 4, 1, 9, 6, 1, 10, "green_carpet")
    b.fill_air()
    return b


# --- The settlers' camp: what a Settler's Wagon sets up ------------------------------------------------------------
def settlers_camp():
    """11 x 5 x 9: a settlers' camp in the wild — a covered wagon on four wheels with the supply chest in it, a Builder's
    Bench beside it with a lamp, a campfire with log seats in front and two bedrolls. Placed at once by the Settler's
    Wagon (not built): {@code camp/} blueprints aren't in the Blueprint Table."""
    b = Build(11, 5, 9)
    # The wagon: x 2-8, z 3-5, its bed a block up on two axles and four wheels
    for x in range(2, 9):
        for z in (3, 4, 5):
            b.set(x, 1, z, "spruce_planks")
    for x in (3, 7):
        for z in (3, 4, 5):
            fence(b, x, 0, z, "dark_oak_fence")
        trapdoor(b, x, 0, 2, "dark_oak_trapdoor", "north", open_=True)
        trapdoor(b, x, 0, 6, "dark_oak_trapdoor", "south", open_=True)
    # Its sides and the canvas
    for x in range(2, 9):
        for z, face in ((3, "south"), (5, "north")):
            if x in (2, 8):
                fence(b, x, 2, z, "spruce_fence")
    for x in range(3, 8):
        b.set(x, 2, 3, "white_wool")
        b.set(x, 2, 5, "white_wool")
        b.set(x, 3, 4, "white_wool")
        stairs(b, x, 3, 3, "minecraft:smooth_quartz_stairs", "south")  # the canvas rounds over the top (placed at once, never built)
        stairs(b, x, 3, 5, "minecraft:smooth_quartz_stairs", "north")
    # The tongue out of the front
    fence(b, 1, 1, 4, "dark_oak_fence")
    fence(b, 0, 1, 4, "dark_oak_fence")
    # The load: the supply chest (a loot table fills it), a hay bale, a pumpkin
    b.set_nbt(5, 2, 4, Compound({"LootTable": String("aliveworkplace:chests/settlers_wagon"), "id": String("minecraft:chest")}))
    b.set(5, 2, 4, "chest", facing="west", type="single", waterlogged=False)
    b.set(4, 2, 4, "hay_block", axis="x")
    b.set(6, 2, 4, "pumpkin")
    # The Builder's Bench beside the wagon, a lamp on a post
    b.set(9, 0, 3, "aliveworkplace:builders_bench", facing="west")
    lamp_post(b, 10, 0, 2, "spruce_fence", height=2)
    # The campfire in front, with logs to sit on
    b.set(5, 0, 0, "campfire", facing="north", lit=True, signal_fire=False, waterlogged=False)
    log(b, 3, 0, 0, "stripped_spruce_log", axis="z")
    log(b, 7, 0, 0, "stripped_spruce_log", axis="z")
    # Two bedrolls behind the wagon
    b.bed(2, 0, 8, "brown", facing="west")
    b.bed(8, 0, 8, "brown", facing="east")
    b.set(5, 0, 8, "potted_fern")
    b.fill_air()
    return b
