"""
Decorations: the small builds that make a village a place — a well, lamp posts, benches, a fountain, a gazebo and a
market square. Near a Village Hall each finished one adds to the village's beauty (see hall/Decorations.java).

Pavements are full blocks (a builder can stand on a full block, not on a slab), laid at y 0 like a plinth.
"""
import math

from kit import *

PAVING = Mix((5, "cobblestone"), (3, "stone"), (2, "andesite"), (1, "stone_bricks"), seed=31)
BORDER = "polished_andesite"
DECOR_WOOD = "spruce"


def bush(b, x, y, z, kind="oak_leaves"):
    b.set(x, y, z, kind, distance=1, persistent=True, waterlogged=False)


def disc(cx, cz, r):
    """The cells of a round of radius r (rounded) about (cx, cz)."""
    out = []
    for x in range(cx - int(r), cx + int(r) + 1):
        for z in range(cz - int(r), cz + int(r) + 1):
            if math.hypot(x - cx, z - cz) <= r:
                out.append((x, z))
    return out


def pave(b, cells, y=0, name=PAVING, border=None):
    """Paving on {@code cells}; with {@code border}, the cells at the round's edge in that block."""
    s = set(cells)
    for x, z in cells:
        edge = any((x + dx, z + dz) not in s for dx, dz in DIRS.values())
        b.set(x, y, z, border if (border and edge) else resolve(name, x, y, z))


# --- Well ---------------------------------------------------------------------------------------------------------
def well_kerb(b, cx, cz):
    """A stone kerb round a shaft of water two deep at (cx, cz), a wooden frame over it with a lantern on a chain."""
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 1, cz + 2):
            if (x, z) != (cx, cz):
                for y in (0, 1):
                    corner = x != cx and z != cz
                    b.set(x, y, z, "stone_bricks" if corner else MOSSY_BRICK_MIX.at(x, y, z))
    b.set(cx, 0, cz, "water", level=0)
    b.set(cx, 1, cz, "water", level=0)
    for x in (cx - 1, cx + 1):
        for y in (2, 3):
            fence(b, x, y, cz, "spruce_fence")
    for x in range(cx - 1, cx + 2):
        log(b, x, 4, cz, "spruce_log", axis="x")
    b.set(cx, 3, cz, "chain", axis="y", waterlogged=False)
    lantern(b, cx, 2, cz, hanging=True)


def well():
    """7 x 5 x 7: a village well — a kerb of mossy stone bricks round a shaft of water, a spruce frame over it with a
    lantern hanging on a chain, on a round of cobbled paving."""
    b = Build(7, 5, 7)
    pave(b, disc(3, 3, 3.7), border=BORDER)
    well_kerb(b, 3, 3)
    b.set(1, 1, 5, "potted_cornflower")
    b.set(5, 1, 1, "potted_poppy")
    b.fill_air()
    return b


def well_2():
    """Upgrade of the Well: a little spruce roof over the frame, flowers on the kerb, benches either side and a lamp
    post at each front corner. 7 x 7 x 7."""
    b = well().grow(7, 7, 7)
    gable_roof(b, 1, 5, 1, 5, 3, SPRUCE, axis="x", ridge=SPRUCE)
    for x, z in ((2, 2), (4, 2), (2, 4), (4, 4)):
        b.set(x, 2, z, ["potted_red_tulip", "potted_azure_bluet", "potted_oxeye_daisy", "potted_allium"][(x + z) % 4])
    for z in (2, 3, 4):
        stairs(b, 0, 1, z, SPRUCE, "west")
        stairs(b, 6, 1, z, SPRUCE, "east")
    b.set(1, 1, 5, "air")
    b.set(5, 1, 1, "air")
    for x in (1, 5):
        lamp_post(b, x, 1, 1, "spruce_fence", height=1)
    b.set(1, 1, 5, "barrel", facing="up", open=False)
    b.set(5, 1, 5, "hay_block", axis="y")
    b.fill_air()
    return b


# --- Lamp post ----------------------------------------------------------------------------------------------------
def street_lamp():
    """3 x 6 x 3: a tall street lamp — a stone foot, a dark oak post and two arms with a lantern hanging from each."""
    b = Build(3, 6, 3)
    b.set(1, 0, 1, "chiseled_stone_bricks")
    stairs(b, 1, 0, 0, STONE_BRICK, "south")
    stairs(b, 1, 0, 2, STONE_BRICK, "north")
    stairs(b, 0, 0, 1, STONE_BRICK, "east")
    stairs(b, 2, 0, 1, STONE_BRICK, "west")
    wall_block(b, 1, 1, 1, "stone_brick_wall")
    for y in (2, 3, 4):
        fence(b, 1, y, 1, "dark_oak_fence")
    fence(b, 0, 4, 1, "dark_oak_fence")
    fence(b, 2, 4, 1, "dark_oak_fence")
    lantern(b, 0, 3, 1, hanging=True)
    lantern(b, 2, 3, 1, hanging=True)
    b.set(1, 5, 1, "dark_oak_trapdoor", facing="north", half="bottom", open=False, powered=False, waterlogged=False)
    b.fill_air()
    return b


# --- Bench --------------------------------------------------------------------------------------------------------
def bench(b, x0, y, z, facing, wood=SPRUCE, lamp=True, bush_kind="oak_leaves"):
    """A three-seat bench of stairs with its back to {@code OPP[facing]}... drawn along x at row z: a bush at each end,
    a hedge behind and (lamp) a lamp post in the middle of the hedge. {@code facing}: the way the sitters look
    ("north": the hedge is at z + 1)."""
    back = OPP[facing]
    _, dz = DIRS[back]
    for x in range(x0 + 1, x0 + 4):
        stairs(b, x, y, z, wood, back)
    bush(b, x0, y, z, bush_kind)
    bush(b, x0 + 4, y, z, bush_kind)
    for x in range(x0, x0 + 5):
        if lamp and x == x0 + 2:
            lamp_post(b, x, y, z + dz, wood.fence, height=2)
        else:
            bush(b, x, y, z + dz, bush_kind)


def park_bench():
    """5 x 3 x 2: a spruce bench between two bushes, a clipped hedge behind it with a lamp post in the middle."""
    b = Build(5, 3, 2)
    bench(b, 0, 0, 0, "north")
    b.fill_air()
    return b


# --- Fountain -----------------------------------------------------------------------------------------------------
def fountain_basin(b, cx, cz, y=0):
    """A 5 x 5 stone basin of water about (cx, cz) (floor at y, water and rim at y + 1), a column in the middle with a
    little bowl of water on top."""
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 3):
            b.set(x, y, z, "stone_bricks")
            rim = abs(x - cx) == 2 or abs(z - cz) == 2
            corner = abs(x - cx) == 2 and abs(z - cz) == 2
            if corner:
                b.set(x, y + 1, z, "chiseled_stone_bricks")
            elif rim:
                b.set(x, y + 1, z, "polished_andesite")
            elif (x, z) != (cx, cz):
                b.set(x, y + 1, z, "water", level=0)
    b.set(cx, y + 1, cz, "chiseled_stone_bricks")
    wall_block(b, cx, y + 2, cz, "stone_brick_wall")
    b.set(cx, y + 3, cz, "chiseled_stone_bricks")
    # The bowl: stairs on the four sides (their feet towards the column), slabs in the corners, water in the middle
    for side, (dx, dz) in DIRS.items():
        stairs(b, cx + dx, y + 4, cz + dz, STONE_BRICK, OPP[side], top=True)
    for dx in (-1, 1):
        for dz in (-1, 1):
            slab(b, cx + dx, y + 4, cz + dz, STONE_BRICK, top=True)
    b.set(cx, y + 4, cz, "water", level=0)


def fountain():
    """7 x 5 x 7: a stone fountain — a basin of water with a chiseled column and a bowl on top — on a round of paving."""
    b = Build(7, 5, 7)
    pave(b, disc(3, 3, 3.7), border=BORDER)
    fountain_basin(b, 3, 3)
    b.fill_air()
    return b


# --- Gazebo -------------------------------------------------------------------------------------------------------
def gazebo():
    """7 x 8 x 7: an open timber gazebo on a stone plinth — four dark oak posts, railings with a way in at the front,
    benches round the back, a hipped roof with a lantern hanging from the tie beam."""
    b = Build(7, 8, 7)
    plinth(b, 1, 1, 5, 5, STONE_MIX, floor="spruce_planks")
    stairs(b, 3, 0, 0, STONE_BRICK, "south")
    for x, z in ((1, 1), (5, 1), (1, 5), (5, 5)):
        posts(b, [(x, z)], 1, 3, "dark_oak_log")
    for x in range(2, 5):
        if x != 3:
            fence(b, x, 1, 1, "dark_oak_fence")
    for z in range(2, 5):
        stairs(b, 1, 1, z, SPRUCE, "west")
        stairs(b, 5, 1, z, SPRUCE, "east")
    for x in range(2, 5):
        stairs(b, x, 1, 5, SPRUCE, "south")
    beam_ring(b, 1, 1, 5, 5, 4, "dark_oak_log")
    for x in range(2, 5):
        log(b, x, 4, 3, "dark_oak_log", axis="x")
    lantern(b, 3, 3, 3, hanging=True)
    for x, z in ((1, 1), (5, 1)):
        b.set(x, 0, z - 1, "potted_azure_bluet" if x == 1 else "potted_red_tulip")
    hip_roof(b, 0, 6, 0, 6, 4, DARK_OAK)
    b.fill_air()
    return b


# --- Market square ------------------------------------------------------------------------------------------------
def kiosk(b, x0, z0, colors, goods):
    """A 3 x 3 market kiosk with its counter facing north: posts at the corners, a peaked striped wool canopy (a ridge
    down the middle, carpet slopes either side), goods on the counter (a list of three, west to east)."""
    for x in (x0, x0 + 2):
        for z in (z0, z0 + 2):
            posts(b, [(x, z)], 1, 2, "spruce_log")
    for x in range(x0, x0 + 3):
        c = colors[(x - x0) % 2]
        for z in range(z0, z0 + 3):
            b.set(x, 3, z, c)
        b.set(x, 4, z0 + 1, c)
        for z in (z0, z0 + 2):
            b.set(x, 4, z, c.replace("_wool", "_carpet"))
    b.set(x0 + 1, 1, z0, "spruce_trapdoor", facing="north", half="top", open=False, powered=False, waterlogged=False)
    for i, g in enumerate(goods):
        if g:
            x = x0 + i
            y = 2 if i == 1 else 1
            z = z0 if i == 1 else z0 + 1
            b.set(x, y, z, g) if isinstance(g, str) else b.set(x, y, z, g[0], **g[1])


def market_square():
    """13 x 5 x 13: a paved square — a fountain in the middle, lamp posts at the corners, benches between bushes on the
    east and west sides, two striped kiosks at the back and the village bell on a pedestal between them, flower beds at
    the front corners. Villagers meet at the bell."""
    b = Build(13, 5, 13)
    cells = [(x, z) for x in range(13) for z in range(13)]
    pave(b, cells)
    for i in range(13):
        for x, z in ((i, 0), (i, 12), (0, i), (12, i)):
            b.set(x, 0, z, BORDER)
    # A ring of stone bricks round the fountain, and the ways in
    for x, z in disc(6, 6, 3.6):
        if math.hypot(x - 6, z - 6) > 2.9:
            b.set(x, 0, z, "stone_bricks")
    for z in range(0, 3):
        b.set(6, 0, z, "polished_andesite")
    fountain_basin(b, 6, 6)
    # Lamp posts at the corners
    for x, z in ((1, 1), (11, 1), (1, 11), (11, 11)):
        lamp_post(b, x, 1, z, "dark_oak_fence", height=2)
    # Benches facing the fountain
    for z in range(5, 8):
        stairs(b, 1, 1, z, SPRUCE, "west")
        stairs(b, 11, 1, z, SPRUCE, "east")
    for x in (1, 11):
        bush(b, x, 1, 4)
        bush(b, x, 1, 8)
    # Flower beds in the front corners
    flower_bed(b, 2, 1, 4, 2, 0, ["poppy", "dandelion", "cornflower", "oxeye_daisy", "azure_bluet"], edge=None)
    flower_bed(b, 8, 1, 10, 2, 0, ["red_tulip", "allium", "cornflower", "lily_of_the_valley", "orange_tulip"], edge=None)
    # Kiosks at the back and the bell between them
    kiosk(b, 2, 9, ("red_wool", "white_wool"), ["melon", ("lantern", {"hanging": False, "waterlogged": False}), "pumpkin"])
    kiosk(b, 8, 9, ("blue_wool", "white_wool"), [("hay_block", {"axis": "y"}), ("lantern", {"hanging": False, "waterlogged": False}), "potted_cornflower"])
    b.set(6, 1, 11, "chiseled_stone_bricks")
    b.set(6, 2, 11, "bell", attachment="floor", facing="north", powered=False)
    # Stock beside the kiosks
    b.set(1, 1, 10, "barrel", facing="up", open=False)
    b.set(1, 1, 9, "hay_block", axis="y")
    b.set(11, 1, 10, "barrel", facing="up", open=False)
    b.set(11, 2, 10, "potted_red_tulip")
    b.fill_air()
    return b


# --- Chapel: where the village's couples marry --------------------------------------------------------------------
CHAPEL_WALL = BRICK_WALL_MIX
CHAPEL_ROOF = DEEPSLATE_TILE


def chapel():
    """11 x 16 x 16: a stone chapel — a bell tower at the front with an open belfry under a slate spire, a nave behind
    it under a steep slate roof, buttresses between tall windows, pews, candles and flowers inside. Near a Village Hall
    it's where couples marry (its bell is the village's), and it makes the village prettier.
    Tower walls x 3-7, z 1-5; nave walls x 2-8, z 5-14."""
    b = Build(11, 16, 16)
    # The nave
    skirt(b, 2, 5, 8, 14, STONE_BRICK)
    plinth(b, 2, 5, 8, 14, MOSSY_BRICK_MIX, floor="polished_andesite")
    walls(b, 2, 5, 8, 14, 1, 5, CHAPEL_WALL)
    for z in (8, 11):  # buttresses between the windows
        for x, face in ((1, "east"), (9, "west")):
            b.set(x, 1, z, "stone_bricks")
            b.set(x, 2, z, "stone_bricks")
            stairs(b, x, 3, z, STONE_BRICK, face)
    for z0 in (6, 9, 12):  # tall windows, a slab arch over each
        for x, out in ((2, "west"), (8, "east")):
            window(b, x, 2, z0, out, width=2, height=2, sill=STONE_BRICK)
    for x in (4, 6):  # a pair of windows in the back wall, a round one over them
        window(b, x, 2, 14, "south", height=2, sill=STONE_BRICK)
    beam_ring(b, 2, 5, 8, 14, 5, "stripped_dark_oak_log")
    for x in (2, 8):
        b.set(x, 5, 5, "stone_bricks")  # (the beam's ends stay behind the tower)
    gable_roof(b, 1, 9, 6, 15, 6, CHAPEL_ROOF, axis="z", gable=CHAPEL_WALL, gable_at=(14,), steep=True, eave_trim=STONE_BRICK)
    pane(b, 5, 8, 14)
    pane(b, 5, 9, 14)
    # Inside: pews either side of an aisle, the altar at the back with candles and flowers
    for z in (7, 9, 11):
        for x in (3, 4, 6, 7):
            stairs(b, x, 1, z, DARK_OAK, "south")
    for z in range(6, 14):
        b.set(5, 1, z, "red_carpet")
    for x in range(3, 8):
        b.set(x, 1, 13, "polished_andesite" if x != 5 else "chiseled_stone_bricks")
    b.set(4, 2, 13, "candle", candles=3, lit=True, waterlogged=False)
    b.set(6, 2, 13, "candle", candles=3, lit=True, waterlogged=False)
    b.set(5, 2, 13, "potted_lily_of_the_valley")
    b.set(3, 2, 13, "potted_white_tulip")
    b.set(7, 2, 13, "potted_white_tulip")
    for z in range(6, 14):
        log(b, 5, 5, z, "stripped_dark_oak_log", axis="z")  # a beam down the nave (for the lights, and the builder)
    for z in (7, 11):
        lantern(b, 5, 4, z, hanging=True)
    # The tower: stone to the belfry, a floor under it and one under the spire, a ladder up the inside
    skirt(b, 3, 1, 7, 4, STONE_BRICK)
    plinth(b, 3, 1, 7, 5, MOSSY_BRICK_MIX, floor="polished_andesite")
    walls(b, 3, 1, 7, 5, 1, 8, CHAPEL_WALL)
    for x, z in ((3, 1), (7, 1), (3, 5), (7, 5)):
        for y in range(1, 12):
            b.set(x, y, z, "stone_bricks")
    door(b, 5, 1, 1, "dark_oak_door", "south")
    stairs(b, 5, 0, 0, STONE_BRICK, "south")
    stairs(b, 5, 3, 0, STONE_BRICK, "south", top=True)  # a hood over the door
    b.set(5, 1, 5, "air")
    b.set(5, 2, 5, "air")  # through into the nave
    for y in (5, 6):
        pane(b, 5, y, 1)  # a tall window over the door
    box(b, 4, 8, 2, 6, 8, 4, "spruce_planks")  # the belfry floor
    for y in range(1, 9):
        b.set(6, y, 4, "ladder", facing="north", waterlogged=False)
    b.set(6, 8, 4, "air")
    # The belfry: open arches on every side, the bell hanging from the spire's floor
    for y in (9, 10):
        for x in (4, 5, 6):
            b.set(x, y, 1, "air")
            b.set(x, y, 5, "air")
        for z in (2, 3, 4):
            b.set(3, y, z, "air")
            b.set(7, y, z, "air")
    for x in (4, 5, 6):
        b.set(x, 11, 1, CHAPEL_WALL.at(x, 11, 1))
        b.set(x, 11, 5, CHAPEL_WALL.at(x, 11, 5))
        stairs(b, x, 10, 1, STONE_BRICK, "south", top=True) if x != 5 else None
        stairs(b, x, 10, 5, STONE_BRICK, "north", top=True) if x != 5 else None
    for z in (2, 3, 4):
        b.set(3, 11, z, CHAPEL_WALL.at(3, 11, z))
        b.set(7, 11, z, CHAPEL_WALL.at(7, 11, z))
        stairs(b, 3, 10, z, STONE_BRICK, "west", top=True) if z != 3 else None
        stairs(b, 7, 10, z, STONE_BRICK, "east", top=True) if z != 3 else None
    box(b, 4, 11, 2, 6, 11, 4, "spruce_planks")  # the floor under the spire
    b.set(5, 10, 3, "bell", attachment="ceiling", facing="north", powered=False)
    lantern(b, 4, 9, 2)
    # A string course round the tower at the belfry floor; the spire: slate, steep, straight up from the walls
    for x in range(3, 8):
        stairs(b, x, 8, 0, STONE_BRICK, "south", top=True)
    for z in range(1, 5):
        stairs(b, 2, 8, z, STONE_BRICK, "west", top=True)
        stairs(b, 8, 8, z, STONE_BRICK, "east", top=True)
    hip_roof(b, 3, 7, 1, 5, 12, CHAPEL_ROOF, steep=True, rings=2)
    b.set(5, 14, 3, "deepslate_tiles")  # the spire's tip
    slab(b, 5, 15, 3, CHAPEL_ROOF)
    # Outside: a path to the door, lamps, flowers by the tower
    for z in (0,):
        for x in (4, 6):
            b.set(x, 0, z, "polished_andesite")
    for x in (1, 9):
        lamp_post(b, x, 0, 1, "dark_oak_fence", height=2)
    flower_bed(b, 1, 3, 2, 4, 0, ["poppy", "azure_bluet", "white_tulip", None])
    flower_bed(b, 8, 3, 9, 4, 0, ["poppy", "azure_bluet", "white_tulip", None])
    b.fill_air()
    return b
