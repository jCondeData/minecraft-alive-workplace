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


# --- The Shift Bell (ROADMAP 31.22) -----------------------------------------------------------------------------------
def shift_bell():
    """3 x 6 x 3: the Shift Bell, a bell hung from a beam between two dark oak posts on stone brick feet, under a little
    slab roof; it rings the work day in and out (The Shift Bell, 31.22)."""
    b = Build(3, 6, 3)
    for x in (0, 2):
        b.set(x, 0, 1, "chiseled_stone_bricks")
        for y in (1, 2, 3):
            b.set(x, y, 1, "stripped_dark_oak_log", axis="y")
    stairs(b, 0, 0, 0, STONE_BRICK, "south")
    stairs(b, 2, 0, 0, STONE_BRICK, "south")
    stairs(b, 0, 0, 2, STONE_BRICK, "north")
    stairs(b, 2, 0, 2, STONE_BRICK, "north")
    b.set(1, 0, 1, "stone_brick_slab", type="bottom", waterlogged=False)
    for x in range(3):
        b.set(x, 4, 1, "dark_oak_log", axis="x")
        b.set(x, 5, 1, "dark_oak_slab", type="bottom", waterlogged=False)
    b.set(1, 3, 1, "bell", attachment="ceiling", facing="north", powered=False)
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
    for z in range(7, 14):  # a block in from the door: on a carpet under its lintel a villager hits its head (B173)
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
    for z in range(1, 6):  # (on to the tower's back corner, where the nave's roof takes it up)
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


# --- More decorations (ROADMAP 26.14) ---------------------------------------------------------------------------------
# Small builds that aren't houses, each in the village's family (stone bricks and cobble, spruce and dark oak, lanterns,
# flowers in their own soil) and each its own kind. No villager's job block in any of them (no barrel, composter,
# cauldron, lectern, smoker, loom...), and no bell: a bell is where a village meets.
def stepped_ring(b, x0, z0, x1, z1, y, fam, centre=None):
    """A step of stairs round the rect (x0, z0)-(x1, z1) at y, climbing inwards, slabs at its corners; the inside is
    {@code fam}'s full block (or {@code centre})."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x not in (x0, x1) and z not in (z0, z1):
                b.set(x, y, z, centre or fam.full)
            elif x in (x0, x1) and z in (z0, z1):
                slab(b, x, y, z, fam)
            else:
                side = "north" if z == z0 else "south" if z == z1 else "west" if x == x0 else "east"
                stairs(b, x, y, z, fam, OPP[side])


def market_cross():
    """5 x 8 x 5: a market cross — a stepped plinth of stone bricks (two rings of stairs climbing to a chiseled heart), a
    shaft of stone brick wall to a chiseled head, dark oak arms either side with a lantern hanging from each and one on
    top."""
    b = Build(5, 8, 5)
    for x in range(5):
        for z in range(5):
            b.set(x, 0, z, MOSSY_BRICK_MIX.at(x, 0, z))
    stepped_ring(b, 0, 0, 4, 4, 1, STONE_BRICK)
    stepped_ring(b, 1, 1, 3, 3, 2, STONE_BRICK, centre="chiseled_stone_bricks")
    for y in (3, 4, 5):
        wall_block(b, 2, y, 2, "stone_brick_wall")
    b.set(2, 6, 2, "chiseled_stone_bricks")
    for x in (1, 3):  # the arms, a lantern under each
        fence(b, x, 6, 2, "dark_oak_fence")
        lantern(b, x, 5, 2, hanging=True)
    lantern(b, 2, 7, 2)
    b.set(0, 2, 0, "potted_red_tulip")
    b.set(4, 2, 0, "potted_cornflower")
    b.set(0, 2, 4, "potted_cornflower")
    b.set(4, 2, 4, "potted_red_tulip")
    b.fill_air()
    return b


def stag_statue():
    """7 x 8 x 5: a stone stag on a plinth — legs of andesite wall, a body, neck and head of polished andesite, antlers
    spreading either side, on a stone brick plinth with chiseled corners and a moulding of stairs, on cobbled paving
    with a bush and a fern at its feet. The front (north) shows the stag's flank, its head to the east."""
    b = Build(7, 8, 5)
    pave(b, [(x, z) for x in range(7) for z in range(5)], border=BORDER)
    for x in range(1, 6):
        for z in range(1, 4):
            corner = x in (1, 5) and z in (1, 3)
            b.set(x, 1, z, "chiseled_stone_bricks" if corner else "stone_bricks")
    stepped_ring(b, 1, 1, 5, 3, 2, STONE_BRICK)
    # The stag
    for x in (2, 4):
        wall_block(b, x, 3, 2, "andesite_wall")
    for x in (2, 3, 4):
        b.set(x, 4, 2, "polished_andesite")
    stairs(b, 1, 4, 2, ANDESITE, "east", top=True)  # the rump and tail
    stairs(b, 5, 4, 2, ANDESITE, "west", top=True)  # the chest
    b.set(5, 5, 2, "polished_andesite")  # the head, held high
    stairs(b, 6, 5, 2, ANDESITE, "west")  # the muzzle
    for z in (1, 3):  # the antlers, branching up and back
        wall_block(b, 5, 6, z, "andesite_wall")
        wall_block(b, 5, 7, z, "andesite_wall")
        wall_block(b, 4, 7, z, "andesite_wall")
        wall_block(b, 6, 7, z, "andesite_wall")
    bush(b, 0, 1, 1)
    bush(b, 6, 1, 3)
    b.set(0, 1, 3, "potted_fern")
    b.set(6, 1, 1, "potted_fern")
    b.fill_air()
    return b


PLANTER_FLOWERS = ["red_tulip", "orange_tulip", "allium", "cornflower", "oxeye_daisy", "azure_bluet", "poppy"]


def flower_planters():
    """7 x 3 x 3: a two-tier brick planter — a low bed at the front and a raised one behind, both full of flowers in their
    own soil — between brick pillars with a lantern on each."""
    b = Build(7, 3, 3)
    for x in range(7):
        b.set(x, 0, 0, "bricks")
    for x in (0, 6):
        for z in range(3):
            b.set(x, 0, z, "bricks")
            b.set(x, 1, z, "bricks")
        lantern(b, x, 2, 1)
    flower_bed(b, 1, 1, 5, 1, 0, PLANTER_FLOWERS)
    for x in range(1, 6):
        b.set(x, 0, 2, "bricks")
    flower_bed(b, 1, 2, 5, 2, 1, list(reversed(PLANTER_FLOWERS)))
    b.fill_air()
    return b


def signpost():
    """3 x 5 x 3: a crossroads signpost — a spruce post on a cobbled foot, fingerboards pointing four ways at two
    heights, a lantern on top."""
    b = Build(3, 5, 3)
    b.set(1, 0, 1, "mossy_cobblestone")
    for side, (dx, dz) in DIRS.items():
        stairs(b, 1 + dx, 0, 1 + dz, COBBLE, OPP[side])
    b.set(0, 0, 0, "potted_dandelion")
    b.set(2, 0, 2, "potted_blue_orchid")
    for y in (1, 2, 3):
        log(b, 1, y, 1, "spruce_log")
    for side, y in (("west", 3), ("east", 3), ("north", 2), ("south", 2)):
        dx, dz = DIRS[side]
        b.set(1 + dx, y, 1 + dz, "spruce_wall_sign", facing=side, waterlogged=False)
    lantern(b, 1, 4, 1)
    b.fill_air()
    return b


def stone_archway():
    """7 x 7 x 3: a stone archway over a path — mossy stone brick piers on stair feet, an arch of upside-down stairs to a
    chiseled keystone, a coping of slabs, a lantern hanging in the opening, ivy up the piers and lilies at their feet."""
    b = Build(7, 7, 3)
    for z in range(3):
        for x in range(2, 5):
            b.set(x, 0, z, "polished_andesite" if x == 3 else "cobblestone")
        for x in (1, 5):
            for y in range(0, 5):
                b.set(x, y, z, MOSSY_BRICK_MIX.at(x, y, z))
        for x in (2, 3, 4):
            b.set(x, 4, z, BRICK_WALL_MIX.at(x, 4, z))
        stairs(b, 2, 3, z, STONE_BRICK, "west", top=True)
        stairs(b, 4, 3, z, STONE_BRICK, "east", top=True)
        for x in range(1, 6):
            slab(b, x, 5, z, STONE_BRICK)
    for x in (1, 5):  # the piers carried up through the coping, capped
        b.set(x, 5, 1, "stone_bricks")
        slab(b, x, 6, 1, STONE_BRICK)
    b.set(3, 4, 0, "chiseled_stone_bricks")
    b.set(3, 4, 2, "chiseled_stone_bricks")
    for x, face in ((0, "east"), (6, "west")):  # the piers' feet, and a bed of lilies between them
        stairs(b, x, 0, 0, STONE_BRICK, face)
        stairs(b, x, 0, 2, STONE_BRICK, face)
    flower_bed(b, 0, 1, 0, 1, 0, ["lily_of_the_valley"])
    flower_bed(b, 6, 1, 6, 1, 0, ["lily_of_the_valley"])
    lantern(b, 3, 3, 1, hanging=True)
    for x, side in ((0, "east"), (6, "west")):  # ivy on the piers' outer faces
        for y, z in ((2, 0), (3, 0), (4, 0), (2, 2), (3, 2), (2, 1), (3, 1)):
            if (x == 0) == (z == 2):
                continue
            b.set(x, y, z, "vine", up=False, north=False, south=False, east=side == "east", west=side == "west")
    b.fill_air()
    return b


def lily_pond():
    """9 x 3 x 7: a lily pond — an oval of water on a clay bed in a low kerb of mossy cobbles and stone, lily pads on it,
    a grassy bank round it with ferns and flowers in its own soil, and a stone seat on the south side."""
    b = Build(9, 3, 7)
    outer = [(x, z) for x in range(9) for z in range(7) if ((x - 4) / 4.6) ** 2 + ((z - 3) / 3.6) ** 2 <= 1.0]
    inner = {(x, z) for x, z in outer if ((x - 4) / 3.6) ** 2 + ((z - 3) / 2.6) ** 2 <= 1.0}
    kerb = Mix((4, "mossy_cobblestone"), (3, "cobblestone"), (2, "stone"), seed=41)
    plants = ["fern", "short_grass", None, "azure_bluet", "fern", None, "blue_orchid", "short_grass"]
    for x, z in outer:
        if (x, z) not in inner:  # the bank
            b.set(x, 0, z, "grass_block", snowy=False)
            p = plants[(x * 3 + z * 5) % len(plants)]
            if p and z != 6:
                b.set(x, 1, z, p)
            continue
        edge = any((x + dx, z + dz) not in inner for dx, dz in DIRS.values())
        b.set(x, 0, z, kerb.at(x, 0, z) if edge else "clay")
        if edge:
            b.set(x, 1, z, kerb.at(x, 1, z))
        else:
            b.set(x, 1, z, "water", level=0)
    for x, z in ((2, 3), (4, 2), (5, 4), (6, 3), (3, 4)):
        if b.get(x, 1, z)[0] == "minecraft:water":
            b.set(x, 2, z, "lily_pad")
    for x in (3, 4, 5):  # a seat on the south bank
        b.set(x, 0, 6, "cobblestone")
        slab(b, x, 1, 6, STONE_BRICK)
    b.fill_air()
    return b


def hay_cart():
    """6 x 4 x 5: a hay cart left by the road — a spruce bed on two dark oak wheels, its shafts down on the ground in
    front, a tailboard at the back, loaded with bales."""
    b = Build(6, 4, 5)
    for x in range(0, 4):
        for z in range(1, 4):
            slab(b, x, 0, z, SPRUCE, top=True)
    trapdoor(b, 2, 0, 0, "dark_oak_trapdoor", "north", open_=True)  # the wheels
    trapdoor(b, 2, 0, 4, "dark_oak_trapdoor", "south", open_=True)
    for z in (1, 3):  # the shafts
        fence(b, 4, 0, z, "spruce_fence")
        fence(b, 5, 0, z, "spruce_fence")
    for z in (1, 2, 3):
        fence(b, 0, 1, z, "spruce_fence")  # the tailboard
    for x, z in ((1, 1), (2, 1), (3, 1), (1, 2), (2, 2), (3, 2), (2, 3), (3, 3)):
        b.set(x, 1, z, "hay_block", axis="x")
    for x, z in ((2, 2), (3, 2), (2, 1)):
        b.set(x, 2, z, "hay_block", axis="z")
    lamp_post(b, 4, 1, 3, "spruce_fence", height=1)  # a lantern on the front corner, for the road at night
    b.fill_air()
    return b


def notice_board():
    """5 x 5 x 3: a village notice board — a spruce board between dark oak posts under a little gabled roof, notices
    pinned to it (a white banner and birch trapdoors), a lantern hanging behind and flowers at its feet."""
    b = Build(5, 5, 3)
    for x in (0, 4):
        for y in (0, 1, 2):
            log(b, x, y, 1, "dark_oak_log")
    for x in (1, 2, 3):
        b.set(x, 0, 1, "cobblestone")
        for y in (1, 2):
            b.set(x, y, 1, "spruce_planks")
    for x in range(5):
        log(b, x, 3, 1, "stripped_dark_oak_log", axis="x")
        stairs(b, x, 3, 0, DARK_OAK, "south")
        stairs(b, x, 3, 2, DARK_OAK, "north")
        slab(b, x, 4, 1, DARK_OAK)
    trapdoor(b, 1, 2, 0, "birch_trapdoor", "north", open_=True)
    trapdoor(b, 3, 1, 0, "birch_trapdoor", "north", open_=True)
    trapdoor(b, 3, 2, 0, "birch_trapdoor", "north", open_=True)
    b.set(2, 2, 0, "white_wall_banner", facing="north")
    trapdoor(b, 2, 2, 2, "birch_trapdoor", "south", open_=True)
    lantern(b, 0, 2, 0, hanging=True)
    lantern(b, 4, 2, 0, hanging=True)
    b.set(1, 0, 0, "potted_allium")
    b.set(3, 0, 0, "potted_oxeye_daisy")
    b.fill_air()
    return b


def lantern_pergola():
    """5 x 6 x 7: a pergola walk — spruce posts on a paved floor, beams along the sides, slats across with azalea grown
    over them, lanterns hanging on chains over the path and benches either side."""
    b = Build(5, 6, 7)
    for x in range(5):
        for z in range(7):
            b.set(x, 0, z, "polished_andesite" if x == 2 else PAVING.at(x, 0, z))
    for x in (0, 4):
        for z in (0, 3, 6):
            posts(b, [(x, z)], 1, 3, "spruce_log")
        for z in range(7):
            log(b, x, 4, z, "stripped_spruce_log", axis="z")
    for z in (0, 2, 4, 6):
        for x in range(5):
            slab(b, x, 5, z, SPRUCE)
    for x, z, leaf in ((1, 1, "flowering_azalea_leaves"), (3, 1, "azalea_leaves"), (0, 3, "azalea_leaves"),
                       (1, 3, "azalea_leaves"), (4, 5, "flowering_azalea_leaves"), (3, 5, "azalea_leaves"),
                       (0, 1, "azalea_leaves"), (2, 3, "flowering_azalea_leaves")):
        bush(b, x, 5, z, leaf)
    for z in (2, 4):
        b.set(2, 4, z, "chain", axis="y", waterlogged=False)
        lantern(b, 2, 3, z, hanging=True)
        stairs(b, 0, 1, z, SPRUCE, "west")
        stairs(b, 4, 1, z, SPRUCE, "east")
    b.set(0, 1, 1, "potted_azalea_bush")
    b.set(4, 1, 5, "potted_azalea_bush")
    b.set(0, 1, 5, "potted_flowering_azalea_bush")
    b.set(4, 1, 1, "potted_flowering_azalea_bush")
    b.fill_air()
    return b


def fire_pit():
    """7 x 3 x 7: a fire pit — a campfire in a ring of cobble slabs on a round of gravel and coarse dirt, log seats on
    three sides, a log pile and a lamp post at the back corners."""
    b = Build(7, 3, 7)
    ground = Mix((3, "coarse_dirt"), (2, "gravel"), (2, "cobblestone"), seed=43)
    for x, z in disc(3, 3, 3.5):
        b.set(x, 0, z, "cobblestone" if math.hypot(x - 3, z - 3) < 1.5 else ground.at(x, 0, z))
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if (dx, dz) != (0, 0):
                slab(b, 3 + dx, 1, 3 + dz, COBBLE)
    b.set(3, 1, 3, "campfire", facing="north", lit=True, signal_fire=False, waterlogged=False)
    for z in (2, 3, 4):
        log(b, 0, 1, z, "stripped_spruce_log", axis="z")
        log(b, 6, 1, z, "stripped_spruce_log", axis="z")
    for x in (2, 3, 4):
        log(b, x, 1, 6, "stripped_spruce_log", axis="x")
    for x, z in ((0, 6), (1, 6), (0, 5)):  # the log pile
        b.set(x, 0, z, "coarse_dirt")
        log(b, x, 1, z, "spruce_log", axis="x")
    log(b, 0, 2, 6, "spruce_log", axis="x")
    b.set(6, 0, 6, "coarse_dirt")
    lamp_post(b, 6, 1, 6, "spruce_fence", height=1)
    b.fill_air()
    return b


def sundial():
    """7 x 3 x 7: a sundial plaza — a round of smooth stone with hour stones of chiseled stone bricks in a polished
    andesite border, a dial of slabs on a stone foot in the middle and its gnomon, lamp posts at the back corners."""
    b = Build(7, 3, 7)
    cells = disc(3, 3, 3.5)
    pave(b, cells, name="smooth_stone", border=BORDER)
    for x, z in ((3, 0), (3, 6), (0, 3), (6, 3), (1, 1), (5, 1), (1, 5), (5, 5)):
        b.set(x, 0, z, "chiseled_stone_bricks")
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            slab(b, 3 + dx, 1, 3 + dz, "smooth_stone_slab", top=(dx, dz) == (0, 0))
    b.set(3, 1, 3, "stone_bricks")
    stairs(b, 3, 2, 3, STONE_BRICK, "south")  # the gnomon, its edge towards the north
    for x in (0, 6):
        lamp_post(b, x, 0, 6, "dark_oak_fence", height=2)
    b.fill_air()
    return b


def topiary_garden():
    """9 x 5 x 9: a topiary garden in a clipped hedge — gravel walks crossing at an urn of azalea, four beds of lawn
    with a shape in each (a cone and a tiered cone of spruce, a ball and a cube of oak on short trunks)."""
    b = Build(9, 5, 9)
    for x in range(9):
        for z in range(9):
            walk = x == 4 or z == 4
            b.set(x, 0, z, "gravel" if walk else "grass_block", **({} if walk else {"snowy": False}))
            if (x in (0, 8) or z in (0, 8)) and not (walk and (x, z) != (4, 8)):
                bush(b, x, 1, z)
                if x in (0, 8) and z in (0, 8):
                    bush(b, x, 2, z)
    bush(b, 4, 1, 8)
    b.set(4, 0, 4, "stone_bricks")
    b.set(4, 1, 4, "chiseled_stone_bricks")
    b.set(4, 2, 4, "potted_flowering_azalea_bush")
    # NW: a cone of spruce; NE: a ball of oak on a trunk; SW: a cube of oak on a trunk; SE: a tiered spruce cone
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            bush(b, 2 + dx, 1, 2 + dz, "spruce_leaves")
            if dx == 0 or dz == 0:
                bush(b, 2 + dx, 2, 2 + dz, "spruce_leaves")
            bush(b, 2 + dx, 2, 6 + dz, "oak_leaves")
            bush(b, 2 + dx, 3, 6 + dz, "oak_leaves")
    bush(b, 2, 3, 2, "spruce_leaves")
    log(b, 2, 1, 6, "oak_log")
    log(b, 6, 1, 2, "oak_log")
    log(b, 6, 2, 2, "oak_log")
    for dx, dz in DIRS.values():
        bush(b, 6 + dx, 3, 2 + dz, "oak_leaves")
    bush(b, 6, 3, 2, "oak_leaves")
    bush(b, 6, 4, 2, "oak_leaves")
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if dx == 0 or dz == 0:
                bush(b, 6 + dx, 1, 6 + dz, "spruce_leaves")
    bush(b, 6, 2, 6, "spruce_leaves")
    bush(b, 6, 3, 6, "spruce_leaves")
    bush(b, 6, 4, 6, "spruce_leaves")
    for x in (3, 5):  # stone gateposts with lanterns either side of the way in
        b.set(x, 0, 0, "stone_bricks")
        wall_block(b, x, 1, 0, "stone_brick_wall")
        lantern(b, x, 2, 0)
    b.fill_air()
    return b


def market_corner():
    """5 x 5 x 5: a market corner — a striped awning sloping down to the front on dark oak posts (a lantern hanging from
    its front beam) over an L of goods stacked against the back and the east side: crates of planks with trapdoor lids,
    sacks of wool, hay, pumpkins and melons."""
    b = Build(5, 5, 5)
    for x in range(5):
        for z in range(5):
            b.set(x, 0, z, PAVING.at(x, 0, z))
    for x in (0, 4):
        for y in (1, 2):
            fence(b, x, y, 0, "dark_oak_fence")
        for y in (1, 2, 3):
            fence(b, x, y, 4, "dark_oak_fence")
    for x in range(5):  # the awning: a beam at the front, the cloth rising to the back
        log(b, x, 3, 0, "stripped_dark_oak_log", axis="x")
        stripe = "green_wool" if x % 2 == 0 else "white_wool"
        b.set(x, 3, 1, stripe)
        b.set(x, 4, 2, stripe)
        b.set(x, 4, 3, stripe)
        b.set(x, 4, 4, stripe)
    lantern(b, 2, 2, 0, hanging=True)
    # Goods along the back (z 3-4) and the east side (x 3)
    b.set(1, 1, 4, "spruce_planks")  # crates: two stacked, one alone, trapdoor lids
    b.set(1, 2, 4, "spruce_planks")
    trapdoor(b, 1, 3, 4, "spruce_trapdoor", "north")
    b.set(2, 1, 4, "spruce_planks")
    trapdoor(b, 2, 2, 4, "spruce_trapdoor", "north")
    b.set(3, 1, 4, "hay_block", axis="y")
    b.set(3, 2, 4, "hay_block", axis="x")
    b.set(3, 1, 3, "white_wool")
    b.set(3, 1, 2, "brown_wool")
    b.set(3, 2, 3, "pumpkin")
    b.set(2, 1, 3, "melon")
    b.set(1, 1, 3, "potted_red_tulip")
    b.fill_air()
    return b


def footbridge():
    """7 x 4 x 7: a footbridge over a channel — a stone-kerbed stretch of water with lily pads, a spruce bridge across on
    stair steps with fence rails, a lantern on a post at each end."""
    b = Build(7, 4, 7)
    for x in range(7):
        for z in range(7):
            if z in (0, 6):
                b.set(x, 0, z, PAVING.at(x, 0, z))
            elif z in (1, 5) or x in (0, 6):
                b.set(x, 0, z, MOSSY_BRICK_MIX.at(x, 0, z))
            else:
                b.set(x, 0, z, "water", level=0)
    for z in range(1, 6):  # the deck between two beams, rails on the beams
        b.set(3, 1, z, "spruce_planks")
        for x in (2, 4):
            log(b, x, 1, z, "stripped_spruce_log", axis="z")
            fence(b, x, 2, z, "spruce_fence")
    stairs(b, 3, 0, 0, SPRUCE, "south")
    stairs(b, 3, 0, 6, SPRUCE, "north")
    for x in (2, 4):
        for z in (0, 6):
            b.set(x, 0, z, "spruce_planks")
    for x, z in ((2, 1), (4, 5)):
        lantern(b, x, 3, z)
    for x, z in ((1, 2), (5, 4), (0, 0)):
        if b.get(x, 0, z)[0] == "minecraft:water":
            b.set(x, 1, z, "lily_pad")
    b.fill_air()
    return b


# The decorations of 26.14, by blueprint name, in the order the Table and the gallery show them.
MORE_DECORATIONS = (("market_cross", market_cross), ("stag_statue", stag_statue), ("flower_planters", flower_planters),
                    ("signpost", signpost), ("stone_archway", stone_archway), ("lily_pond", lily_pond),
                    ("hay_cart", hay_cart), ("notice_board", notice_board), ("lantern_pergola", lantern_pergola),
                    ("fire_pit", fire_pit), ("sundial", sundial), ("topiary_garden", topiary_garden),
                    ("market_corner", market_corner), ("footbridge", footbridge))


# ROADMAP 38.7: the Mega Shrine, with Mega Showdown only (StarterBlueprints.MEGA_ONLY; listed when its blocks exist).
MEGA = "mega_showdown:"


def mega_shrine():
    """7 x 6 x 7: the Mega Shrine — Mega Showdown's pedestal on a two-step dais of Mega meteorite bricks: a base course
    of meteorite bricks with chiseled corners, edged with deepslate brick stairs (the meteorite has no stairs of its
    own), a polished meteorite top with a chiseled meteorite plinth under the pedestal; four corner posts of deepslate
    brick wall crowned with chiseled meteorite and an amethyst cluster, soul lanterns hung between, and steps up the
    front (north)."""
    b = Build(7, 6, 7)
    pave(b, [(x, z) for x in range(7) for z in range(7)], name="polished_deepslate", border="deepslate_tiles")
    for x in range(1, 6):  # the first step: meteorite bricks, chiseled at its corners
        for z in range(1, 6):
            corner = x in (1, 5) and z in (1, 5)
            b.set(x, 1, z, MEGA + ("chiseled_mega_meteorid_brick" if corner else "mega_meteorid_brick"))
    for x in range(2, 5):  # the top: polished meteorite round the plinth
        for z in range(2, 5):
            b.set(x, 2, z, MEGA + "polished_mega_meteorid_block")
    b.set(3, 2, 3, MEGA + "chiseled_mega_meteorid_block")
    b.set(3, 3, 3, MEGA + "pedestal", facing="north")
    for x in (2, 3, 4):  # the steps up the front
        stairs(b, x, 1, 0, DEEPSLATE_BRICK, "south")
    stairs(b, 3, 2, 1, DEEPSLATE_BRICK, "south")
    for x, z in ((2, 1), (4, 1)):
        slab(b, x, 2, z, DEEPSLATE_BRICK)
    for z in range(1, 6):  # the sides' moulding
        stairs(b, 0, 1, z, DEEPSLATE_BRICK, "east")
        stairs(b, 6, 1, z, DEEPSLATE_BRICK, "west")
    for x in range(1, 6):
        stairs(b, x, 1, 6, DEEPSLATE_BRICK, "north")
    for x, z in ((1, 1), (5, 1), (1, 5), (5, 5)):  # the corner posts
        wall_block(b, x, 2, z, "deepslate_brick_wall")
        wall_block(b, x, 3, z, "deepslate_brick_wall")
        b.set(x, 4, z, MEGA + "chiseled_mega_meteorid_brick")
        b.set(x, 5, z, "amethyst_cluster", facing="up")
    for x, z in ((1, 3), (5, 3), (3, 5)):  # soul lanterns on the sides and back of the top step
        lantern(b, x, 2, z, soul=True)
    b.set(0, 1, 0, "potted_allium")
    b.set(6, 1, 0, "potted_allium")
    b.fill_air()
    return b
