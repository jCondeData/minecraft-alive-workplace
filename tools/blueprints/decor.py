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
    """A 3 x 3 market kiosk with its counter facing north: posts at the corners, a striped wool canopy, goods on the
    counter (a list of three, west to east)."""
    for x in (x0, x0 + 2):
        for z in (z0, z0 + 2):
            posts(b, [(x, z)], 1, 2, "spruce_log")
    for x in range(x0, x0 + 3):
        for z in range(z0, z0 + 3):
            b.set(x, 3, z, colors[(x - x0) % 2])
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
    b.fill_air()
    return b
