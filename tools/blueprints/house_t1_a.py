"""
House T1, Version A (ROADMAP 36.23): "the Hearthstead", a Level I home for a village's first family.

Story: the first family that settles a new village builds small but proud. They raise one tall, steep-roofed hall
with its gable to the street (the hero mass: the hearth on its back wall, the children's beds up in the roof), then a
low kitchen wing off its east side, so the two make an L round a little woodyard by the door. A dark oak frame on a
stone curb, lime-white plaster panels between, a clay-tile roof. Original: not drawn from the Starter Cottage (which
is a side-gabled box with a porch gable and a chimney up its end) or any other blueprint.

Footprint 13 x 16 x 13 (W x H x D), front z = 0 (north), y = 0 on the clicked block:
  * Hall (the main mass): walls x 1-7, z 2-10, four high, a steep roof (ridge along z, at y 13) whose front gable
    faces the street; the frame posts stand a block proud of the walls on the stone curb, under the roof's overhang,
    tied by beams at the eaves' height.
  * Kitchen wing (side mass, about half the hall): walls x 7-11, z 4-10, a low roof (ridge along x, at y 9) whose
    ridge runs into the hall's slope; its own gable to the east.
  * Chimney: a stone breast on the hall's back gable, its stack (two deep) through the overhang, above the ridge.
  * Dormer: a little gabled dormer on the hall's long west slope, lighting the stair and the loft.
  * Woodyard: the corner between the hall and the wing (x 8-12, z 0-2): a woodpile, a lantern and bushes.

Named parts (for review and the owner's notes):
  Hearth room   the hall's ground floor: hearth on the back wall with two fireside chairs and a rug, a dining table for
                four by the east wall, the stair up the west wall with a chest under it, a clock.
  Kitchen       the wing: counters with a stone top along the east wall under the window, a furnace, shelves of cups,
                a pantry chest, herbs hanging; a doorway from the hearth room.
  Sleeping loft the hall's roof space (floor y 5): two beds under the street gable window with a nightstand between,
                a lamp, a rug, a bench by the stair head, a boarded ceiling on collar
                beams with a lantern hanging from it.
  Front gable   the street face: the door in the middle with a stone step and a lantern on the tie beam, a flower box
                window each side, the loft window on the tie beam, a king post and collar beam up the gable.
  Dormer        the west slope's window: framed cheeks, its own little gable roof one block out over the eave.
  Roof          the hall's steep clay-tile roof and the wing's lower one, 1-block overhang with dark oak eave trim.
  Chimney       the stone stack on the back gable with a smoking campfire on top.
  Woodyard      logs stacked in front of the wing with a lantern on them, bushes at the corners.

Walking line: the door (4, z 2) straight down the middle of the hearth room to the hearth (z 9); off it, east into
the kitchen through (7, z 8), and west up the stair (x 2, z 4 -> 8) to the loft, whose head is at the back (z 9).

No job block (the house is a home); containers: three chests (one per room, never in a row). Built in passes: block-out, structure (curb, walls,
frame, roof), depth (proud posts, tie beams, sills, lintels, flower boxes, the dormer), texture (stone mix, mossy corners),
detail (gables, door, chimney), interior (each room from its furniture), ground (woodyard), light.
"""
from kit import *
from decor import bush

FRAME = "dark_oak_log"


class _Plaster(Mix):
    """Lime plaster: white concrete, with a course of polished diorite along the foot of the walls where rain splashes
    it (weathering kept low and together, not scattered over the panels)."""

    def at(self, x, y, z):
        return "polished_diorite" if y == 1 else "white_concrete"


PLASTER = _Plaster((1, "white_concrete"))
ROOF = BRICK
TRIM = DARK_OAK
CURB = Mix((6, "cobblestone"), (3, "stone"), (2, "andesite"), seed=42)
MOSSY = Mix((3, "mossy_cobblestone"), (2, "cobblestone"), seed=43)
WOOD = "spruce"  # the furniture and floors
SHUTTER = "spruce_trapdoor"  # the flower boxes, a shade lighter than the frame

HALL = (1, 2, 7, 10)  # x0, z0, x1, z1 of the hall's walls
WING = (7, 4, 11, 10)  # the kitchen wing's walls


def _in(rect, x, z):
    x0, z0, x1, z1 = rect
    return x0 <= x <= x1 and z0 <= z <= z1


def curb(b):
    """The stone curb round both masses (a block out from the walls: the proud posts stand on it), mossy at the
    corners, and the stone floor ring under the walls."""
    for x in range(b.w):
        for z in range(b.d):
            inside = _in(HALL, x, z) or _in(WING, x, z)
            near = any(_in(HALL, x + dx, z + dz) or _in(WING, x + dx, z + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1))
            if not inside and near:
                corner = (x, z) in ((0, 1), (0, 11), (12, 11), (12, 3), (8, 1))
                b.set(x, 0, z, MOSSY if corner or (x, z) in ((0, 10), (1, 11), (11, 11), (12, 10)) else CURB)
    plinth(b, *HALL[:2], *HALL[2:], CURB, floor=f"{WOOD}_planks")
    plinth(b, *WING[:2], *WING[2:], CURB, floor=f"{WOOD}_planks")


def hall_walls(b):
    x0, z0, x1, z1 = HALL
    walls(b, x0, z0, x1, z1, 1, 4, PLASTER)
    walls(b, *WING[:2], WING[2], WING[3], 1, 4, PLASTER)
    # the frame in the wall line: the corners, the wing's corners, the hearth's posts
    posts(b, [(1, 2), (7, 2), (1, 10), (7, 10), (7, 4), (11, 4), (11, 10), (3, 10), (5, 10)],
          1, 4, FRAME)


def beams(b):
    """The top plates round both masses (drawn after the roofs, whose gable triangles start at this height)."""
    beam_ring(b, *HALL, 5, FRAME)
    beam_ring(b, *WING, 5, FRAME)


PROUD = [(1, 1), (7, 1),  # the street face
         (0, 2), (0, 6), (0, 10),  # the hall's west side, under the eaves
         (1, 11), (7, 11),  # the back, either side of the chimney
         (8, 2),  # the hall's east corner by the woodyard
         (11, 3),  # the wing's front corner
         (12, 4), (12, 10)]  # the wing's east gable


def frame(b):
    """Depth: posts a block proud of the walls under the overhangs, tie beams across the gables at the eaves."""
    posts(b, PROUD, 1, 4, FRAME)
    for x in range(1, 8):  # the street gable's tie beam (the loft window's sill) and the back one's
        log(b, x, 5, 1, FRAME, axis="x")
        log(b, x, 5, 11, FRAME, axis="x")
    for z in range(4, 11):  # the wing gable's
        log(b, 12, 5, z, FRAME, axis="z")


def roof(b):
    def hall(t):
        gable_roof(t, 0, 8, 1, 11, 5, ROOF, axis="z", gable=PLASTER, gable_at=(2, 10), steep=True, eave_trim=TRIM)

    def wing(t):
        gable_roof(t, 8, 12, 3, 11, 5, ROOF, axis="x", gable=PLASTER, gable_at=(8, 11), eave_trim=TRIM, closed=True)
    roofs(b, hall, wing)
    # the wing's ridge carried on over the hall's eave row, so it dies into the steep slope with no notch
    stairs(b, 7, 8, 6, ROOF, "south")
    stairs(b, 7, 8, 8, ROOF, "north")
    b.set(7, 8, 7, ROOF.full)
    slab(b, 7, 9, 7, ROOF)


def street_gable(b):
    """The front: the door with a stone step and a lantern, a flower box window either side, the loft window on the tie
    beam, a king post and a collar beam up the gable."""
    door(b, 4, 1, 2, "spruce_door", "south")
    stairs(b, 4, 0, 1, COBBLE, "south")
    log(b, 4, 3, 2, FRAME, axis="x")
    lantern(b, 4, 4, 1, hanging=True)  # from the tie beam, over the step
    for x, plants in ((2, ["potted_red_tulip"]), (6, ["potted_cornflower"])):
        window(b, x, 2, 2, "north", height=2, flowers=(SHUTTER, plants), lintel=TRIM)
    window(b, 4, 6, 2, "north", height=2)  # its sill is the tie beam
    posts(b, [(4, 2)], 8, 11, FRAME)  # the king post over the loft window
    for x in (3, 5):  # and a collar beam across it, so the gable isn't one white triangle
        log(b, x, 8, 2, FRAME, axis="x")


def side_windows(b):
    """Every side window alike: two panes tall, a sill of upside-down stairs on the curb and a lintel under the eave
    (no shutters: they would cover the plaster panels either side, and the walls would read as one dark mass)."""
    for z in (4, 8):
        window(b, 1, 2, z, "west", height=2, sill=TRIM, lintel=TRIM)
    window(b, 9, 2, 4, "north", height=2, sill=TRIM, lintel=TRIM)
    window(b, 11, 2, 7, "east", height=2, sill=TRIM, lintel=TRIM)
    window(b, 9, 2, 10, "south", height=2, sill=TRIM, lintel=TRIM)
    # the back of the hall: flower boxes like the front
    for x, plants in ((2, ["potted_allium"]), (6, ["potted_oxeye_daisy"])):
        window(b, x, 2, 10, "south", height=2, flowers=(SHUTTER, plants), lintel=TRIM)
    for x in (2, 6):  # little loft windows either side of the chimney, on the back tie beam
        window(b, x, 6, 10, "south", height=2)
    posts(b, [(11, 7)], 6, 7, FRAME)  # the wing gable's stud


def dormer(b):
    """A little gabled dormer on the hall's long west slope (the side a passer-by sees most of): it breaks the big roof
    and lights the stair and the loft. Its face stands in the wall line (x 1), its roof one block out over the eave."""
    for z in (5, 7):
        posts(b, [(1, z)], 6, 7, FRAME)
    glass_column(b, 1, 6, 7, 6)
    b.set(1, 8, 6, PLASTER.at(1, 8, 6))
    for x in (0, 1):
        stairs(b, x, 7, 4, ROOF, "south")
        stairs(b, x, 8, 5, ROOF, "south")
        slab(b, x, 9, 6, ROOF)
        stairs(b, x, 8, 7, ROOF, "north")
        stairs(b, x, 7, 8, ROOF, "north")


def chimney_stack(b):
    """The stone breast on the back gable (the hearth's back), the stack up through the overhang past the ridge."""
    for x in (3, 4, 5):
        for z in (11, 12):
            for y in range(0, 5):
                b.set(x, y, z, (MOSSY if y == 0 else CURB).at(x, y, z))
    for z in (11, 12):  # shoulders, then a stack two deep up past the ridge
        stairs(b, 3, 5, z, COBBLE, "east")
        stairs(b, 5, 5, z, COBBLE, "west")
        for y in range(5, 14):
            b.set(4, y, z, CURB.at(4, y, z))
    slab(b, 4, 14, 12, STONE_BRICK)
    chimney(b, 4, 11, 14, 13, CURB)  # (just the smoke: the campfire on top)
    # the hearth: a campfire in the back wall, stone round it
    for x, y in ((3, 1), (3, 2), (3, 3), (5, 1), (5, 2), (5, 3), (4, 2), (4, 3), (4, 4)):
        b.set(x, y, 10, "stone_bricks")
    b.set(4, 1, 10, "campfire", facing="north", lit=True, signal_fire=False, waterlogged=False)


def hearth_room(b):
    # The stair up the west wall, front to back, the loft's floor open over it; a chest under its top
    for i in range(5):
        stairs(b, 2, 1 + i, 4 + i, WOOD_FAM, "south")
    b.set(2, 1, 8, "chest", facing="east", type="single", waterlogged=False)
    # The dining table for four by the east wall
    for z in (4, 5):
        fence(b, 5, 1, z, f"{WOOD}_fence")
        b.set(5, 2, z, f"{WOOD}_pressure_plate", powered=False)  # a wooden top (white cloth read as a stone slab in game)
    chair(b, 6, 1, 4, WOOD_FAM, "west")
    chair(b, 6, 1, 5, WOOD_FAM, "west")
    chair(b, 5, 1, 3, WOOD_FAM, "south")
    chair(b, 5, 1, 6, WOOD_FAM, "north")
    # The fireside: two chairs facing each other across the hearth's rug, a mantel shelf over the fire
    chair(b, 3, 1, 9, WOOD_FAM, "east")
    b.furniture("chair", 3, 1, 9, "east", wood=WOOD)
    chair(b, 5, 1, 9, WOOD_FAM, "west")
    b.furniture("chair", 5, 1, 9, "west", wood=WOOD)
    for x, z in ((3, 8), (4, 8), (5, 8), (4, 9), (3, 7), (4, 7), (5, 7)):
        b.set(x, 1, z, "red_carpet")
    shelf(b, 4, 3, 9, "north", WOOD)
    clock(b, 6, 1, 9, "west", WOOD)
    picture(b, 6, 3, 7, "west", "green", WOOD)
    hanging_pot(b, 3, 4, 3, "potted_fern", WOOD)
    lantern(b, 4, 4, 5, hanging=True)
    lantern(b, 5, 4, 8, hanging=True)


def kitchen(b):
    b.set(7, 1, 8, "air")
    b.set(7, 2, 8, "air")
    box(b, 8, 5, 5, 10, 5, 9, f"{WOOD}_planks")  # the kitchen's ceiling (the wing's attic is shut)
    for z in (5, 6, 7, 8):  # counters: spruce cupboards under a smooth stone top, the sink-side under the window
        b.set(10, 1, z, f"{WOOD}_planks")
        b.set(10, 2, z, "smooth_stone_slab", type="bottom", waterlogged=False)
        furnish(b, "counter", 10, 1, z, "west", cells=((0, 0, 0), (0, 1, 0)), wood=WOOD)
    b.set(10, 1, 9, "furnace", facing="west", lit=False)
    b.set(8, 1, 9, "chest", facing="north", type="single", waterlogged=False)
    for z in (5, 8):
        shelf(b, 10, 3, z, "west", WOOD)
    shelf(b, 8, 3, 5, "south", WOOD, crockery="potted_red_mushroom")
    hanging_pot(b, 9, 4, 7, "potted_fern", WOOD)
    lantern(b, 9, 4, 5, hanging=True)
    b.set(9, 1, 6, "brown_carpet")  # not under the hanging herbs: a villager on a carpet there hits its head


def loft(b):
    box(b, 2, 5, 3, 6, 5, 9, f"{WOOD}_planks")
    b.clear(2, 5, 6, 2, 5, 7)  # over the stair
    b.set(2, 5, 8, WOOD_FAM.stairs, facing="south", half="bottom", shape="straight", waterlogged=False)
    for x, z in ((3, 6), (3, 7), (2, 5)):
        fence(b, x, 6, z, f"{WOOD}_fence")
    b.bed(3, 6, 4, "light_blue", facing="north")
    b.bed(5, 6, 4, "yellow", facing="north")
    nightstand(b, 4, 6, 3, "south", WOOD)
    floor_lamp(b, 6, 6, 3, WOOD)
    for x in (3, 4, 5):
        b.set(x, 6, 5, "light_gray_carpet")
    couch(b, 5, 6, 9, "north", WOOD, "blue", length=2, arms=False)
    b.set(2, 6, 3, "chest", facing="east", type="single", waterlogged=False)
    picture(b, 4, 7, 9, "north", "blue", WOOD)
    # A boarded ceiling over the middle of the loft on two collar beams, so the bedroom isn't a tunnel of bare brick
    # (the roof's inner faces show only low down on the sides)
    box(b, 3, 9, 3, 5, 9, 9, f"{WOOD}_planks")
    for z in (4, 7):
        for x in (3, 4, 5):
            log(b, x, 8, z, FRAME, axis="x")
    lantern(b, 4, 8, 6, hanging=True)  # from the ceiling boards


def woodyard(b):
    for x in (9, 10, 11):
        log(b, x, 0, 2, "oak_log", axis="x")
    log(b, 10, 1, 2, "oak_log", axis="x")
    log(b, 11, 1, 2, "oak_log", axis="x")
    for x, z in ((12, 1), (12, 2), (11, 0), (0, 0)):
        bush(b, x, 0, z)
    lantern(b, 9, 1, 2)


WOOD_FAM = SPRUCE


def house_t1_a():
    """13 x 16 x 13: the Hearthstead (see the notes at the top of this file)."""
    b = Build(13, 16, 13)
    curb(b)
    hall_walls(b)
    frame(b)
    roof(b)
    beams(b)
    street_gable(b)
    side_windows(b)
    dormer(b)
    chimney_stack(b)
    kitchen(b)
    hearth_room(b)
    loft(b)
    woodyard(b)
    b.fill_air()
    return b


if __name__ == "__main__":
    house_t1_a().save(MAIN_STRUCTURES, "house_t1_a")
