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
    """11 x 5 x 9: a settlers' camp in the wild — a covered wagon on four wheels with the supply chest in it, a
    Blueprint Table beside it with a lamp (the first settler becomes its builder), a campfire with log seats in front
    and two bedrolls. Placed at once by the Settler's Wagon (not built): {@code camp/} blueprints aren't in the
    Blueprint Table."""
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
    # The Blueprint Table beside the wagon (the builder's workstation), a lamp on a post
    b.set(9, 0, 3, "aliveworkplace:blueprint_table", facing="west")
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


# --- Grander homes (ROADMAP 34.15): the Artisan's House and the Burgher's Townhouse ----------------------------------
ARTISAN_FRAME = "spruce_log"
ARTISAN_INFILL = "birch_planks"
ARTISAN_ROOF = DARK_OAK


def artisans_house_body(b):
    """The Artisan's House every tier shares: a narrow house with its gable to the street (walls x 1-7, z 2-10). The
    workroom below in stone (a workbench, an anvil, tool racks, a chest of stock), the upper floor in a spruce frame
    with birch infill jettied a block out over the street on stair brackets, and two beds up there."""
    # The workroom: cobbled plinth, stone walls, stone brick corners
    plinth(b, 1, 2, 7, 10, STONE_MIX, floor="spruce_planks")
    walls(b, 1, 2, 7, 10, 1, 4, STONE_MIX)
    posts(b, [(1, 2), (7, 2), (1, 10), (7, 10)], 1, 4, "stone_bricks")
    door(b, 4, 1, 2, "spruce_door", "south")
    stairs(b, 4, 0, 1, STONE_BRICK, "south")
    b.set(4, 3, 2, "stone_bricks")
    window(b, 2, 2, 2, "north", width=1, height=2, shutters="spruce_trapdoor", sill=STONE_BRICK)
    window(b, 6, 2, 2, "north", width=1, height=2, shutters="spruce_trapdoor", sill=STONE_BRICK)
    for z in (4, 8):
        window(b, 1, 2, z, "west", height=2, sill=STONE_BRICK)
        window(b, 7, 2, z, "east", height=2, sill=STONE_BRICK)
    # The upper floor: a floor beam all round, jettied one out over the street (z 1), on stair brackets
    beam_ring(b, 1, 1, 7, 10, 5, ARTISAN_FRAME)
    box(b, 2, 5, 2, 6, 5, 9, "spruce_planks")
    for x in (1, 4, 7):
        stairs(b, x, 4, 1, SPRUCE, "south", top=True)
    walls(b, 1, 1, 7, 10, 6, 8, ARTISAN_INFILL)
    posts(b, [(1, 1), (4, 1), (7, 1), (1, 10), (4, 10), (7, 10), (1, 4), (7, 4), (1, 7), (7, 7)], 6, 8, ARTISAN_FRAME)
    beam_ring(b, 1, 1, 7, 10, 9, ARTISAN_FRAME)
    for x in (2, 6):
        window(b, x, 6, 1, "north", height=2, shutters="spruce_trapdoor",
               flowers=("spruce_trapdoor", ["potted_red_tulip"] if x == 2 else ["potted_cornflower"]))
    window(b, 4, 7, 10, "south", height=1)
    for x in (3, 5):  # the workroom's back windows (inside the wing from II on)
        glass_column(b, x, 2, 3, 10)
    for z in (5, 9):
        window(b, 1, 6, z, "west", height=2)
        window(b, 7, 6, z, "east", height=2)
    # The roof: dark oak, its gable to the street, birch in the gables with a little window in each
    gable_roof(b, 0, 8, 0, 11, 9, ARTISAN_ROOF, axis="z", gable=ARTISAN_INFILL, gable_at=(1, 10), ridge=ARTISAN_ROOF)
    for z in (1, 10):
        log(b, 4, 10, z, ARTISAN_FRAME)
        pane(b, 4, 11, z)
    chimney(b, 8, 6, 0, 13, STONE_MIX)
    # In the workroom: a workbench by the window, an anvil, a grindstone-free tool wall of item racks (barrels), stock
    b.set(2, 1, 3, "crafting_table")
    b.set(2, 1, 5, "anvil", facing="east")
    b.set(2, 1, 6, "barrel", facing="up", open=False)
    b.set(2, 1, 7, "chest", facing="east", type="single", waterlogged=False)
    b.set(6, 1, 3, "barrel", facing="up", open=False)
    b.set(6, 2, 3, "potted_fern")
    fence(b, 4, 1, 6, "spruce_fence")
    b.set(4, 2, 6, "spruce_pressure_plate", powered=False)
    stairs(b, 4, 1, 7, SPRUCE, "north")
    box(b, 3, 1, 4, 5, 1, 5, "brown_carpet")
    for y in range(1, 6):
        b.set(6, y, 9, "ladder", facing="north", waterlogged=False)
    b.set(6, 5, 9, "air")  # the hatch through the floor
    lantern(b, 4, 4, 4, hanging=True)
    # Upstairs: two beds under the roof, a chest between them, a rug, a lamp
    b.bed(2, 6, 3, "orange", facing="north")
    b.bed(6, 6, 3, "orange", facing="north")
    b.set(4, 6, 2, "chest", facing="south", type="single", waterlogged=False)
    box(b, 3, 6, 5, 5, 6, 7, "orange_carpet")
    b.set(2, 6, 8, "bookshelf")
    lantern(b, 2, 7, 8)
    # Out front: a lamp on the wall post and a barrel by the door
    b.set(5, 1, 1, "barrel", facing="up", open=False)
    lantern(b, 5, 2, 1)


def artisans_house():
    """9 x 15 x 12: the Artisan's House (grade I) — a narrow timber house, its gable to the street, with a stone
    workroom below and two beds above, the upper floor jettied out on brackets, a stone chimney up the side."""
    b = Build(9, 15, 12)
    artisans_house_body(b)
    b.fill_air()
    return b


def artisans_house_2():
    """Upgrade of the Artisan's House (same origin and front): a stone workshop wing at the back, through a doorway in
    the back wall, with a long bench, a loom, a smoker-free stove of a campfire-less brick hearth and a third bed in a
    corner, under its own lower roof. 9 x 15 x 18."""
    b = artisans_house().grow(9, 15, 18)
    b.clear(0, 0, 11, 8, 14, 11)  # the old back overhang
    b.set(4, 1, 10, "air")
    b.set(4, 2, 10, "air")
    b.blocks.pop((4, 0, 11), None)
    # The wing: walls x 2-6, z 10-16 (the house's back wall is its front), stone with brick quoins
    plinth(b, 2, 10, 6, 16, STONE_MIX, floor="spruce_planks")
    b.set(4, 0, 10, "spruce_planks")
    for y in range(1, 4):
        for z in range(11, 17):
            for x in (2, 6):
                b.set(x, y, z, "stone_bricks" if z == 16 else STONE_MIX.at(x, y, z))
        for x in range(3, 6):
            b.set(x, y, 16, STONE_MIX.at(x, y, 16))
    for z in range(11, 17):
        log(b, 2, 4, z, ARTISAN_FRAME, axis="z")
        log(b, 6, 4, z, ARTISAN_FRAME, axis="z")
    for x in range(3, 6):
        log(b, x, 4, 16, ARTISAN_FRAME, axis="x")
        log(b, x, 4, 13, ARTISAN_FRAME, axis="x")  # a tie beam to hang the lamp from
    keep = {p: v for p, v in b.blocks.items() if p[2] == 10 and p[1] >= 5}
    gable_roof(b, 1, 7, 11, 17, 4, ARTISAN_ROOF, axis="z", gable=ARTISAN_INFILL, gable_at=(16, 16), ridge=ARTISAN_ROOF)
    b.blocks.update(keep)
    window(b, 2, 2, 14, "west", height=1, shutters="spruce_trapdoor", sill=STONE_BRICK)
    window(b, 6, 2, 14, "east", height=1, shutters="spruce_trapdoor", sill=STONE_BRICK)
    window(b, 4, 2, 16, "south", height=1, sill=STONE_BRICK)
    lantern(b, 4, 3, 13, hanging=True)
    # The workshop: a long bench, a loom, a stonecutter-free saw horse, the third bed in the corner
    for z in (11, 12):
        stairs(b, 3, 1, z, SPRUCE, "east", top=True)
    b.set(5, 1, 11, "loom", facing="west")
    b.set(5, 1, 12, "barrel", facing="up", open=False)
    b.bed(3, 1, 15, "brown", facing="north")
    b.set(5, 1, 15, "chest", facing="west", type="single", waterlogged=False)
    b.set(4, 1, 15, "brown_carpet")
    b.fill_air()
    return b


TOWNHOUSE_WALL = Mix((9, "bricks"), (1, "stone_bricks"), seed=31)
TOWNHOUSE_FRAME = "dark_oak_log"
TOWNHOUSE_INFILL = Mix((8, "white_concrete"), (2, "polished_diorite"), seed=32)
TOWNHOUSE_ROOF = DEEPSLATE_TILE


def townhouse_gables(b):
    """The studs in the Townhouse's big gables (no flat panel of plaster): two posts and a king post with a window."""
    for z in (1, 10):
        for x in (3, 7):
            for y in (14, 15):
                log(b, x, y, z, TOWNHOUSE_FRAME)
        for x in range(4, 7):
            log(b, x, 16, z, TOWNHOUSE_FRAME, axis="x")
        log(b, 5, 14, z, TOWNHOUSE_FRAME)
        pane(b, 5, 15, z)
        log(b, 5, 17, z, TOWNHOUSE_FRAME)


def townhouse_body(b):
    """The Burgher's Townhouse every tier shares: three storeys, its gable to the street (walls x 1-9, z 2-10). A brick
    shop below with a bay of shop windows either side of the door; two timber storeys above in dark oak and white
    plaster, the first jettied over the street; bedrooms on both; a slate roof with a loft under it."""
    # The shop: a stone plinth, brick walls with stone brick quoins, shop windows
    plinth(b, 1, 2, 9, 10, FOUNDATION_MIX, floor="spruce_planks")
    walls(b, 1, 2, 9, 10, 1, 4, TOWNHOUSE_WALL)
    posts(b, [(1, 2), (9, 2), (1, 10), (9, 10)], 1, 4, "stone_bricks")
    door(b, 5, 1, 2, "dark_oak_door", "south")
    stairs(b, 5, 0, 1, STONE_BRICK, "south")
    b.set(5, 3, 2, "stone_bricks")
    for x in (2, 7):  # the shop windows: two wide, two tall, a stall board of slabs before each
        window(b, x, 2, 2, "north", width=2, height=2, lintel=STONE_BRICK)
        for dx in (0, 1):
            slab(b, x + dx, 1, 1, SPRUCE, top=True)
    for z in (4, 8):
        window(b, 1, 2, z, "west", height=2, sill=STONE_BRICK)
        window(b, 9, 2, z, "east", height=2, sill=STONE_BRICK)
    window(b, 3, 2, 10, "south", height=2, sill=STONE_BRICK)
    # The first floor: jettied a block over the street on brackets, dark oak frame, white plaster
    beam_ring(b, 1, 1, 9, 10, 5, TOWNHOUSE_FRAME)
    box(b, 2, 5, 2, 8, 5, 9, "spruce_planks")
    for x in (1, 4, 6, 9):
        stairs(b, x, 4, 1, DARK_OAK, "south", top=True)
    walls(b, 1, 1, 9, 10, 6, 8, TOWNHOUSE_INFILL)
    frame = [(1, 1), (4, 1), (6, 1), (9, 1), (1, 10), (4, 10), (6, 10), (9, 10), (1, 4), (9, 4), (1, 7), (9, 7)]
    posts(b, frame, 6, 8, TOWNHOUSE_FRAME)
    beam_ring(b, 1, 1, 9, 10, 9, TOWNHOUSE_FRAME)
    box(b, 2, 9, 2, 8, 9, 9, "spruce_planks")
    # The second floor: the same frame
    walls(b, 1, 1, 9, 10, 10, 12, TOWNHOUSE_INFILL)
    posts(b, frame, 10, 12, TOWNHOUSE_FRAME)
    beam_ring(b, 1, 1, 9, 10, 13, TOWNHOUSE_FRAME)
    for y in (6, 10):
        for x, pot in ((2, "potted_red_tulip"), (5, "potted_allium"), (8, "potted_red_tulip")):
            if y == 6:
                window(b, x, y, 1, "north", height=2, shutters=None, flowers=("dark_oak_trapdoor", [pot]))
            else:
                window(b, x, y, 1, "north", height=2, sill=DARK_OAK)
        for x in (2, 8):
            window(b, x, y, 10, "south", height=2)
        for z in (2, 5, 8):
            window(b, 1, y, z, "west", height=2)
            window(b, 9, y, z, "east", height=2)
    # The loft floor and the slate roof, its gable to the street with a window in each end
    box(b, 2, 13, 2, 8, 13, 9, "spruce_planks")
    gable_roof(b, 0, 10, 0, 11, 13, TOWNHOUSE_ROOF, axis="z", gable=TOWNHOUSE_INFILL, gable_at=(1, 10), ridge=TOWNHOUSE_ROOF)
    townhouse_gables(b)
    chimney(b, 0, 7, 0, 18, "bricks")
    # The shop: a counter of barrels and a slab top, shelves of goods behind, the strongbox
    for x in (3, 4):
        b.set(x, 1, 6, "barrel", facing="up", open=False)
    b.set(6, 1, 6, "barrel", facing="up", open=False)
    for x in (3, 4, 6):
        slab(b, x, 2, 6, DARK_OAK)
    for x in (2, 3, 4):
        b.set(x, 1, 9, "chest", facing="north", type="single", waterlogged=False) if x == 2 else b.set(x, 1, 9, "bookshelf")
    b.set(7, 1, 9, "smoker", facing="west", lit=False)  # the back room's stove
    b.set(8, 1, 9, "barrel", facing="up", open=False)
    b.set(8, 1, 8, "crafting_table")
    lantern(b, 5, 4, 4, hanging=True)
    for y in range(1, 14):  # the stair-ladder up all three floors by the west wall
        b.set(2, y, 7, "ladder", facing="east", waterlogged=False)
    for y in (5, 9, 13):
        b.set(2, y, 7, "ladder", facing="east", waterlogged=False)
    # First floor: the main bedroom (two beds), a wardrobe chest, a rug
    b.bed(6, 6, 8, "blue", facing="south")
    b.bed(8, 6, 8, "blue", facing="south")
    b.set(7, 6, 9, "chest", facing="north", type="single", waterlogged=False)
    box(b, 5, 6, 4, 7, 6, 6, "blue_carpet")
    b.set(4, 6, 2, "bookshelf")
    b.set(3, 6, 2, "bookshelf")
    lantern(b, 3, 7, 2)
    # Second floor: the second bedroom and a sitting corner
    b.bed(7, 10, 8, "light_gray", facing="south")
    b.set(8, 10, 2, "barrel", facing="up", open=False)
    stairs(b, 4, 10, 3, SPRUCE, "south")
    fence(b, 4, 10, 4, "dark_oak_fence")
    b.set(4, 11, 4, "dark_oak_pressure_plate", powered=False)
    box(b, 5, 10, 6, 7, 10, 7, "light_gray_carpet")
    lantern(b, 8, 11, 2)
    # Out front: a lamp at each end of the shop front
    lantern(b, 2, 2, 1)  # on the stall boards, lighting the door
    lantern(b, 8, 2, 1)


def townhouse():
    """11 x 20 x 12: the Burgher's Townhouse (grade II) — three storeys of brick and timber, its slate gable to the
    street: a shop with two bay windows below, the main bedroom over it jettied out on brackets, a second bedroom above,
    a brick chimney up the west side."""
    b = Build(11, 20, 12)
    townhouse_body(b)
    b.fill_air()
    return b


COURTYARD_WALL = Mix((9, "bricks"), (1, "stone_bricks"), seed=33)


def townhouse_courtyard(b):
    """Townhouse II's walled garden behind the house (z 11-17): brick walls with stone caps, a gate, flower beds in their
    own soil round a path, a bench, a lamp, a little tree in a bed; and the loft fitted out as a third bedroom."""
    b.clear(0, 0, 11, 10, 19, 11)  # the old back overhang comes off; the roof runs on over a back porch instead
    gable_roof(b, 0, 10, 10, 12, 13, TOWNHOUSE_ROOF, axis="z")  # (only its eaves row shows, over the back door)
    b.clear(1, 14, 10, 9, 19, 12)
    gable_roof(b, 0, 10, 0, 12, 13, TOWNHOUSE_ROOF, axis="z", gable=TOWNHOUSE_INFILL, gable_at=(1, 10), ridge=TOWNHOUSE_ROOF)
    townhouse_gables(b)
    # The back door into the garden
    door(b, 5, 1, 10, "dark_oak_door", "south")
    # The walls: x 0-10, z 11-17
    for y in (0, 1, 2):
        for z in range(11, 18):
            for x in (0, 10):
                b.set(x, y, z, COURTYARD_WALL)
        for x in range(1, 10):
            b.set(x, y, 17, COURTYARD_WALL)
    for z in range(11, 18):
        for x in (0, 10):
            slab(b, x, 3, z, STONE_BRICK)
    for x in range(1, 10):
        slab(b, x, 3, 17, STONE_BRICK)
    for y in (1, 2):
        b.set(5, y, 17, "air")
    b.set(5, 1, 17, "dark_oak_fence_gate", facing="south", in_wall=True, open=False, powered=False)
    for x in (0, 10):  # pillars with lanterns
        b.set(x, 3, 17, "stone_bricks")
        lantern(b, x, 4, 17)
    # The garden: a gravel path to the gate, flower beds either side, a tree in the middle of one, a bench
    for z in range(11, 17):
        b.set(5, 0, z, "gravel")
    flower_bed(b, 1, 11, 3, 16, 0, ["poppy", "oxeye_daisy", "cornflower", None, "allium"])
    flower_bed(b, 7, 11, 9, 13, 0, ["poppy", "azure_bluet", None, "cornflower"])
    b.set(8, 1, 12, "azalea")
    stairs(b, 7, 1, 15, SPRUCE, "east")
    stairs(b, 8, 1, 15, SPRUCE, "east")
    for x in (7, 8):
        b.set(x, 0, 15, "grass_block", snowy=False)
        b.set(x, 0, 14, "gravel")
        b.set(x, 0, 16, "gravel")
    b.set(9, 0, 14, "gravel")
    b.set(9, 0, 15, "gravel")
    b.set(9, 0, 16, "gravel")
    b.set(9, 1, 16, "potted_oxeye_daisy")
    b.set(6, 0, 14, "gravel")
    b.set(6, 0, 15, "gravel")
    b.set(6, 0, 16, "gravel")
    b.set(4, 0, 14, "gravel")
    b.set(4, 0, 15, "gravel")
    b.set(4, 0, 16, "gravel")
    lamp_post(b, 6, 1, 11, "dark_oak_fence", height=2)
    # The loft: a third bedroom under the slates, lit by the gable windows
    b.bed(5, 14, 4, "green", facing="south")
    b.set(4, 14, 8, "chest", facing="north", type="single", waterlogged=False)
    box(b, 4, 14, 6, 6, 14, 7, "green_carpet")
    lantern(b, 6, 14, 8)


def townhouse_2():
    """Upgrade of the Townhouse (same origin and front): a walled courtyard garden behind, through a back door, and the
    loft under the slates fitted out as a third bedroom. 11 x 20 x 18."""
    b = townhouse().grow(11, 20, 18)
    townhouse_courtyard(b)
    b.fill_air()
    return b


TOWER_WALL = Mix((7, "stone_bricks"), (2, "cracked_stone_bricks"), (1, "mossy_stone_bricks"), seed=34)


def townhouse_3():
    """Upgrade of Townhouse II (same origin and front): a stone corner tower on the east, a block proud of the street
    front, four storeys up to a study under a slate cap — books, a desk, a map on the wall, windows every way —
    reached from the first floor and up ladders. 15 x 26 x 18."""
    b = townhouse_2().grow(15, 26, 18)
    # Clear where the tower stands (x 9-13, z 1-5): the roof's east eave and the jetty corner
    b.clear(10, 0, 0, 10, 19, 6)
    # The tower: walls x 9-13, z 1-5 (its west wall is the house's east wall at z 2-5)
    plinth(b, 9, 1, 13, 5, FOUNDATION_MIX, floor="spruce_planks")
    for y in range(1, 21):
        for x in range(9, 14):
            for z in range(1, 6):
                if x in (9, 13) or z in (1, 5):
                    if x == 9 and 2 <= z <= 5 and y <= 12:
                        continue  # the house wall
                    corner = (x, z) in ((9, 1), (13, 1), (9, 5), (13, 5))
                    b.set(x, y, z, "stone_bricks" if corner else TOWER_WALL)
    for y in (5, 9, 13, 17):  # floors with a ladder hole in the north-east corner
        box(b, 10, y, 2, 12, y, 4, "spruce_planks")
        b.set(12, y, 4, "air")
    for y in range(1, 21):
        b.set(12, y, 4, "ladder", facing="north", waterlogged=False)
    b.set(12, 21, 4, "air")
    # A doorway from the first-floor bedroom into the tower
    for y in (6, 7):
        b.set(9, y, 3, "air")
    # Windows: arrow slits below, wider ones up top for the study
    for y in (2, 6, 10):
        window(b, 11, y, 1, "north", height=2, sill=STONE_BRICK)
        window(b, 13, y, 3, "east", height=2, sill=STONE_BRICK)
    for (x, z, out) in ((11, 1, "north"), (13, 3, "east"), (11, 5, "south")):
        window(b, x, 18, z, out, height=2, shutters="dark_oak_trapdoor", sill=STONE_BRICK)
    window(b, 9, 18, 3, "west", height=2, sill=STONE_BRICK)
    # A cornice and the steep slate cap
    for x in range(8, 15):
        for z in (0, 6):
            stairs(b, x, 20, z, STONE_BRICK, "south" if z == 0 else "north", top=True)
    for z in range(1, 6):
        stairs(b, 8, 20, z, STONE_BRICK, "east", top=True)
        stairs(b, 14, 20, z, STONE_BRICK, "west", top=True)
    box(b, 9, 21, 1, 13, 21, 5, "stone_bricks")
    box(b, 10, 21, 2, 12, 21, 4, "spruce_planks")
    hip_roof(b, 8, 14, 0, 6, 22, TOWNHOUSE_ROOF)
    # The study (y 18-20): a desk, books, a lectern-free reading chair, a map, a lamp
    for z in (2, 3):
        b.set(10, 18, z, "bookshelf")
        b.set(10, 19, z, "bookshelf")
    stairs(b, 11, 18, 4, DARK_OAK, "east", top=True)
    b.set(11, 19, 4, "potted_fern")
    stairs(b, 12, 18, 2, SPRUCE, "west")
    b.set(10, 18, 4, "chest", facing="east", type="single", waterlogged=False)
    lantern(b, 11, 20, 3, hanging=True)
    # The house's east eave meets the tower: close the roof over the cleared column with stairs against the tower
    for z in list(range(6, 12)) + [0]:
        if b.get(10, 13, z) is None:
            stairs(b, 10, 13, z, TOWNHOUSE_ROOF, "west")
    b.fill_air()
    return b
