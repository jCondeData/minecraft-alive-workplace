"""The Arena (ROADMAP 28.16): three tiers, in the Blueprint Table only with Cobblemon installed, sold by an Expert
Trainer Leader. Where the Festival Cup (28.17-28.22) is fought.

An original design, drawn to STYLE.md and the sketch in docs/design/M28-arena.png. Front (z = 0, the way in) is north;
the ring runs east-west across the build, its centre on the build's middle line (x 12), so every tier is symmetric and
the upgrades only grow back (an upgrade shares its base's origin and front).

Arena (25 x 8 x 19):
- the battle ring, 15 x 9 (x 5-19, z 5-13): a packed-mud floor inside a white line, a white half-way line (x 12) and a
  centre circle round the centre spot (12, 9); a polished andesite kerb round it (x 4-20, z 4-14);
- a trainer's box on a dais at each end (x 1-3 and 21-23, z 8-10): a stone dais a block up, a spruce rail round three
  sides, a step down to the ring, a lamp post beside it (x 1 and 23, z 7);
- benches for 12 along the back long side: two stepped rows of six (z 16 and 17, x 9-11 and 13-15, the aisle at x 12)
  on a stone base, a backrest wall at z 18 with a hedge;
- two banner poles at the front (x 4 and 20, z 2) with red banners, a notice board at the front-west corner (x 1-3,
  z 1), a gravel way in (x 11-13, z 0-3), lawn everywhere else.

Arena II (25 x 9 x 29): stands on both long sides (30 seats: at the front two rows of six, x 7-9 and 15-17, z 2-3; at
the back the benches become three rows of six, z 16-18, a back wall at z 19), a stone gate arch over the way in (posts
x 10 and 14, z 1), lanterns on posts round the ring, and behind the back stand a fair lane (z 22-28): a cobbled lane
(z 23-24) with two striped kiosks behind it (x 5-7 and 17-19, z 26-28), stock beside them and lamp posts at its ends.

Arena III (25 x 12 x 29): the back stand becomes a covered grandstand on stone terraces (four rows of ten, x 7-11
and 13-17, z 16-19, a back wall at z 20, a slate lean-to roof on posts), a trainers' room at each end (x 0-4 and
20-24, z 15-21; a Healing Machine, a bench and a barrel in each, the door facing its box) and the champion's pole on
a plinth before the gate (12, 0), tall enough to fly a Cup banner (28.22).

The spots the mod knows (Arenas.java; ArenaGameTests checks them against these files): the ring centre (12, 1, 9),
the boxes (2, 2, 9) and (22, 2, 9), the seats (SEATS below), the fair lane (12, 1, 24; II and III), the champion's pole
top (12, 6, 0; III) and the notice board (2, 2, 1).
"""
from kit import *
from decor import bush, disc, kiosk

W = 25
RING = (5, 5, 19, 13)  # x0, z0, x1, z1: the white outer line included
CENTRE = (12, 9)
BOXES = ((2, 9), (22, 9))
LINE = "white_concrete"
MUD = "packed_mud"
KERB = "polished_andesite"
DAIS = Mix((7, "stone_bricks"), (2, "cracked_stone_bricks"), (1, "mossy_stone_bricks"), seed=161)
TERRACE = Mix((6, "stone_bricks"), (3, "polished_andesite"), (1, "cracked_stone_bricks"), seed=162)
WALL = Mix((7, "stone_bricks"), (2, "cobblestone"), (1, "mossy_stone_bricks"), seed=163)
SEAT = "spruce_stairs"
RAIL = "spruce_fence"
LAWN = "grass_block"
WAY = "gravel"
LANE = Mix((5, "cobblestone"), (3, "stone"), (2, "andesite"), seed=164)
ROOF = DEEPSLATE_TILE
ROOM_ROOF = DEEPSLATE_TILE

# The seats (stairs a villager sits on), as template spots (x, y, z): the y is the seat itself.
FRONT_SEATS = [(x, 1 + r, 3 - r) for r in range(2) for x in (7, 8, 9, 15, 16, 17)]
BENCH_SEATS = [(x, 1 + r, 16 + r) for r in range(2) for x in (9, 10, 11, 13, 14, 15)]
BACK_SEATS = [(x, 1 + r, 16 + r) for r in range(3) for x in (9, 10, 11, 13, 14, 15)]
GRAND_X = (7, 8, 9, 10, 11, 13, 14, 15, 16, 17)
GRAND_SEATS = [(x, 1 + r, 16 + r) for r in range(4) for x in GRAND_X]
SEATS = {1: BENCH_SEATS, 2: FRONT_SEATS + BACK_SEATS, 3: FRONT_SEATS + GRAND_SEATS}
FAIR_LANE = (12, 1, 24)
CHAMPION_POLE = (12, 6, 0)
NOTICE_BOARD = (2, 2, 1)


def lawn(b, d):
    for x in range(W):
        for z in range(d):
            b.set(x, 0, z, LAWN, snowy=False)


def ring(b):
    """The ring: packed mud in a white line, the half-way line and the centre circle; a kerb round it."""
    x0, z0, x1, z1 = RING
    cx, cz = CENTRE
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            edge = x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)
            line = x in (x0, x1) or z in (z0, z1) or x == cx
            b.set(x, 0, z, KERB if edge else LINE if line else MUD)
    inner = set(disc(cx, cz, 1.5))
    for x, z in disc(cx, cz, 2.5):
        if (x, z) not in inner:
            b.set(x, 0, z, LINE)
    b.set(cx, 0, cz, "white_glazed_terracotta", facing="north")  # the centre spot


def box(b, cx, cz, toward):
    """A trainer's box on a dais at (cx, cz): the dais a block up, a rail round three sides, a step towards the ring."""
    back = cx - 1 if toward == "east" else cx + 1
    front = cx + 1 if toward == "east" else cx - 1
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 2, cz + 3):
            b.set(x, 0, z, DAIS)
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 1, cz + 2):
            b.set(x, 1, z, "polished_andesite")
    for z in (cz - 2, cz + 2):  # stairs up the dais's sides
        for x in range(cx - 1, cx + 2):
            stairs(b, x, 1, z, ANDESITE, "north" if z > cz else "south")
    for z in range(cz - 1, cz + 2):
        fence(b, back, 2, z, RAIL)
    for x in (cx, back):
        fence(b, x, 2, cz - 1, RAIL)
        fence(b, x, 2, cz + 1, RAIL)
    stairs(b, front + (1 if toward == "east" else -1), 1, cz, ANDESITE, "west" if toward == "east" else "east")
    lamp_post(b, back, 1, cz - 3, post=RAIL, height=2)


def benches(b, rows, seats_x=(9, 10, 11, 13, 14, 15)):
    """The back stand: {@code rows} stepped rows from z 16, seats facing the ring, the aisle at x 12, a wall behind."""
    xs = range(min(seats_x) - 1, max(seats_x) + 2)
    for r in range(rows):
        z = 16 + r
        for x in xs:
            for y in range(0, 1 + r):
                b.set(x, y, z, TERRACE)
        for x in seats_x:
            stairs(b, x, 1 + r, z, SEAT, "south")
        for x in (min(xs), max(xs)):  # the ends: a stepped side wall a block over the seats
            b.set(x, 1 + r, z, TERRACE)
        stairs(b, 12, 1 + r, z, STONE_BRICK, "south")  # the aisle: steps up to the back
    zb = 16 + rows
    for x in xs:
        for y in range(0, rows + 2):
            b.set(x, y, zb, WALL)
    for x in (min(xs), 12, max(xs)):
        for y in range(0, rows + 2):
            b.set(x, y, zb, "stone_bricks")
        b.set(x, rows + 2, zb, "chiseled_stone_bricks")
    return zb


def banner_pole(b, x, z, color="red"):
    b.set(x, 0, z, "chiseled_stone_bricks")
    for y in range(1, 5):
        log(b, x, y, z, "stripped_spruce_log")
    fence(b, x, 5, z, RAIL)
    b.set(x, 4, z - 1, f"{color}_wall_banner", facing="north")
    b.set(x, 4, z + 1, f"{color}_wall_banner", facing="south")


def notice_board(b):
    """Two posts with a board between under a little roof, a sign on it facing the street (x 1-3, z 1)."""
    for x in (1, 3):
        b.set(x, 0, 1, "cobblestone")
        for y in (1, 2, 3):
            log(b, x, y, 1, "spruce_log")
    b.set(2, 0, 1, "cobblestone")
    b.set(2, 1, 1, "spruce_trapdoor", facing="north", half="top", open=False, powered=False, waterlogged=False)
    for y in (2, 3):
        b.set(2, y, 1, "dark_oak_planks")
    for x in (1, 2, 3):
        slab(b, x, 4, 1, "spruce_slab")
    b.set(2, 2, 0, "spruce_wall_sign", facing="north", waterlogged=False)
    b.set(2, 3, 0, "spruce_wall_sign", facing="north", waterlogged=False)


def way_in(b, z1):
    for x in (11, 12, 13):
        for z in range(0, z1 + 1):
            b.set(x, 0, z, WAY)


def arena_ground(b, d):
    lawn(b, d)
    way_in(b, 3)
    ring(b)
    for (cx, cz), toward in zip(BOXES, ("east", "west")):
        box(b, cx, cz, toward)


def arena():
    """25 x 8 x 19: a 15 x 9 battle ring (packed mud, white lines, a centre circle), a trainer's box on a dais at each
    end with a lamp post, benches for 12 along the back, two banner poles and a notice board at the front."""
    b = Build(W, 8, 19)
    arena_ground(b, 19)
    benches(b, 2)
    for x in (7, 17):  # bushes at the ends of the benches
        bush(b, x, 1, 17)
        bush(b, x, 1, 16)
    banner_pole(b, 4, 2)
    banner_pole(b, 20, 2)
    notice_board(b)
    b.fill_air()
    return b


# --- Arena II -----------------------------------------------------------------------------------------------------
def front_stand(b, x0, x1):
    """Two rows of seats facing the ring (z 3 low, z 2 a block up), a backrest wall at z 1, stepped side walls."""
    for x in range(x0 - 1, x1 + 2):
        for z in (1, 2, 3):
            b.set(x, 0, z, TERRACE)
        b.set(x, 1, 2, TERRACE)
        for y in (1, 2, 3):
            b.set(x, y, 1, WALL)
    for x in range(x0, x1 + 1):
        stairs(b, x, 1, 3, SEAT, "north")
        stairs(b, x, 2, 2, SEAT, "north")
        b.set(x, 0, 0, LAWN, snowy=False)
        b.set(x, 1, 0, ["poppy", "cornflower", "oxeye_daisy"][x % 3])
    for x in (x0 - 1, x1 + 1):
        b.set(x, 1, 3, TERRACE)
        b.set(x, 2, 2, TERRACE)
        b.set(x, 4, 1, "stone_bricks")
        stairs(b, x, 2, 3, STONE_BRICK, "north")
        stairs(b, x, 3, 2, STONE_BRICK, "north")
    for x in range(x0, x1 + 1):
        slab(b, x, 4, 1, STONE_BRICK)


def gate_arch(b):
    """A stone arch over the way in: posts at x 10 and 14 (z 1), a round head, a crest with a lantern."""
    for x in (10, 14):
        b.set(x, 0, 1, "chiseled_stone_bricks")
        for y in range(1, 6):
            b.set(x, y, 1, "stone_bricks")
    for x in range(10, 15):
        b.set(x, 6, 1, "stone_bricks" if x in (10, 14) else "polished_andesite")
    stairs(b, 11, 5, 1, STONE_BRICK, "west", top=True)
    stairs(b, 13, 5, 1, STONE_BRICK, "east", top=True)
    slab(b, 12, 5, 1, STONE_BRICK, top=True)
    for x in (11, 13):
        stairs(b, x, 7, 1, STONE_BRICK, "east" if x == 11 else "west")
    b.set(12, 7, 1, "chiseled_stone_bricks")
    lantern(b, 12, 8, 1)
    for x in (10, 14):
        lantern(b, x, 7, 1)
    for x in (11, 12, 13):
        b.set(x, 0, 1, "polished_andesite")


def ring_lanterns(b):
    for x, z in ((4, 4), (20, 4), (4, 14), (20, 14), (8, 4), (16, 4), (8, 14), (16, 14)):
        lamp_post(b, x, 1, z, post=RAIL, height=1)


def fair_lane(b):
    """Behind the back stand: a cobbled lane (z 23-24) with lamp posts at its ends, two striped kiosks behind it
    facing the lane, stock beside them, and a gravel way round each end of the stand to it."""
    for x in range(2, 23):
        for z in (23, 24):
            b.set(x, 0, z, LANE)
    for z in range(15, 23):  # the ways round the back stand to the lane
        for x in (5, 19):
            if b.get(x, 0, z) is None or b.get(x, 0, z)[0] == "minecraft:grass_block":
                b.set(x, 0, z, WAY)
    kiosk(b, 5, 26, ("red_wool", "white_wool"), ["melon", ("lantern", {"hanging": False, "waterlogged": False}), "pumpkin"])
    kiosk(b, 17, 26, ("yellow_wool", "white_wool"),
          [("hay_block", {"axis": "y"}), ("lantern", {"hanging": False, "waterlogged": False}), "potted_red_tulip"])
    for x, z in ((4, 27), (16, 27)):
        b.set(x, 1, z, "barrel", facing="up", open=False)
    for x, z in ((8, 27), (20, 27)):
        b.set(x, 1, z, "hay_block", axis="y")
    for x in (2, 22):
        lamp_post(b, x, 1, 25, post=RAIL, height=2)
    for x in (10, 14):
        stairs(b, x, 1, 26, SEAT, "south")  # a bench for the fair's customers
    for x, z in ((11, 26), (13, 26)):
        bush(b, x, 1, z)
    b.set(12, 1, 26, "potted_azalea_bush")
    b.set(12, 0, 26, "cobblestone")


def arena_2():
    """Upgrade of the Arena (25 x 9 x 29): stands on both long sides (30 seats), a gate arch, lanterns round the ring
    and a fair lane with two striped kiosks behind the back stand."""
    b = arena().grow(W, 9, 29)
    lawn_rows(b, 19, 29)
    b.clear(8, 1, 18, 16, 4, 18)  # Arena I's back wall: the third row goes there
    benches(b, 3)
    front_stand(b, 7, 9)
    front_stand(b, 15, 17)
    gate_arch(b)
    ring_lanterns(b)
    fair_lane(b)
    b.fill_air()
    return b


def lawn_rows(b, z0, z1):
    for x in range(W):
        for z in range(z0, z1):
            b.set(x, 0, z, LAWN, snowy=False)


# --- Arena III ----------------------------------------------------------------------------------------------------
def grandstand(b):
    """Four rows of ten on stone terraces (z 16-19, x 7-11 and 13-17, the aisle at x 12), full side walls (x 6 and 18),
    a back wall at z 20 with two windows, a slate lean-to roof on spruce posts rising to the back, a plank ceiling under
    it and lanterns hanging from its front beam. Gravel ways at x 5 and 19 lead past it to the fair lane."""
    for x in range(6, 19):
        for z in range(16, 21):
            for y in range(1, 9):
                b.blocks.pop((x, y, z), None)
    benches(b, 4, seats_x=GRAND_X)
    for z in range(16, 20):  # the ends: full side walls up under the roof
        for x in (6, 18):
            for y in range(1, 8 if z < 18 else 9):
                b.set(x, y, z, WALL)
    for x in range(6, 19):  # the back wall up to the roof
        for y in range(6, 10):
            b.set(x, y, 20, WALL)
    for x in (6, 9, 12, 15, 18):
        for y in range(0, 10):
            b.set(x, y, 20, "stone_bricks")
    for x in range(7, 18):  # a timber band across the back, and buttresses on it towards the fair lane
        if x not in (9, 12, 15):
            log(b, x, 5, 20, "spruce_log", axis="x")
    for x in (9, 15):
        for y in range(0, 5):
            b.set(x, y, 21, "stone_bricks")
        stairs(b, x, 5, 21, STONE_BRICK, "north")
    for x in (7, 8, 16, 17):
        for y in (7, 8):
            pane(b, x, y, 20)
    for x in (6, 18):  # the front posts
        for y in range(1, 7):
            log(b, x, y, 15, "spruce_log")
    for x in range(6, 19):  # the beam over the front
        log(b, x, 7, 15, "spruce_log", axis="x")
    for x in range(6, 19):  # the lean-to roof: slab, stair, slab, stair... up to the back wall
        slab(b, x, 8, 14, ROOF)
        stairs(b, x, 8, 15, ROOF, "south")
        slab(b, x, 9, 16, ROOF)
        stairs(b, x, 9, 17, ROOF, "south")
        slab(b, x, 10, 18, ROOF)
        stairs(b, x, 10, 19, ROOF, "south")
        b.set(x, 10, 20, ROOF.full)  # the wall's top course, in the roof's slate
    for x in range(7, 18):  # the ceiling under the slope
        for z, y in ((16, 8), (17, 8), (18, 9), (19, 9)):
            b.set(x, y, z, "spruce_planks")
    for x in (6, 18):
        for z, y in ((16, 8), (17, 8), (18, 9), (19, 9)):
            b.set(x, y, z, WALL)
    for x in (9, 15):
        lantern(b, x, 6, 15, hanging=True)


def trainers_room(b, x0, toward):
    """A stone room x0..x0+4, z 15-21 (spruce frame, a slate gable roof along z): the door at the front facing the box,
    Cobblemon's Healing Machine against the inner wall, a bench opposite, a barrel, a lantern from the ridge beam."""
    x1 = x0 + 4
    cx = x0 + 2
    outer = x0 if toward == "east" else x1
    inner = x1 if toward == "east" else x0
    step_in = -1 if toward == "east" else 1  # from the inner wall into the room
    for x in range(x0, x1 + 1):
        for z in range(15, 22):
            b.set(x, 0, z, "stone_bricks" if x in (x0, x1) or z in (15, 21) else "spruce_planks")
            for y in range(1, 9):
                b.blocks.pop((x, y, z), None)
    walls(b, x0, 15, x1, 21, 1, 4, WALL)
    beam_ring(b, x0, 15, x1, 21, 5, "spruce_log")
    for x, z in ((x0, 15), (x1, 15), (x0, 21), (x1, 21), (x0, 18), (x1, 18)):
        for y in range(1, 5):
            log(b, x, y, z, "spruce_log")
    door(b, cx, 1, 15, "spruce_door", "north")
    stairs(b, cx, 0, 14, ANDESITE, "south")
    for z in (16, 20):
        for y in (2, 3):
            pane(b, outer, y, z)
    window(b, cx, 2, 21, "south", height=2, sill=STONE_BRICK)
    b.set(inner + step_in, 1, 19, "cobblemon:healing_machine", facing="west" if toward == "east" else "east")
    for z in (18, 19):
        stairs(b, outer - step_in, 1, z, SEAT, "west" if toward == "east" else "east")
    b.set(inner + step_in, 1, 20, "barrel", facing="up", open=False)
    b.set(inner + step_in, 1, 17, "potted_azalea_bush")
    for z in range(16, 21):
        log(b, cx, 5, z, "spruce_log", axis="z")
    lantern(b, cx, 4, 18, hanging=True)
    gable_roof(b, x0, x1, 14, 22, 6, ROOM_ROOF, axis="z", gable=WALL, gable_at=(15, 21), eave_trim=SPRUCE_FAM)


def champion_pole(b):
    """On a plinth before the gate (12, 0): a spruce pole with a log at the top for the Cup banner and a gold cap."""
    b.set(12, 0, 0, "chiseled_stone_bricks")
    b.set(12, 1, 0, "stone_bricks")
    for y in range(2, 6):
        fence(b, 12, y, 0, RAIL)
    log(b, 12, 6, 0, "stripped_spruce_log")
    b.set(12, 7, 0, "gold_block")


def arena_3():
    """Upgrade of Arena II (25 x 12 x 29): a covered grandstand on stone terraces (40 seats), a trainers' room with a
    Healing Machine at each end, and the champion's pole before the gate."""
    b = arena_2().grow(W, 12, 29)
    grandstand(b)
    trainers_room(b, 0, "east")
    trainers_room(b, 20, "west")
    champion_pole(b)
    b.fill_air()
    return b


SPRUCE_FAM = Family("spruce_planks", "spruce_stairs", "spruce_slab", fence="spruce_fence")

ARENAS = (("arena", arena), ("arena_2", arena_2), ("arena_3", arena_3))
