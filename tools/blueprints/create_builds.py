"""ROADMAP 39.4: the Create-powered builds (design note docs/design/M39.md, "The builds"). Listed at the Blueprint Table
only with Create installed. Each turns as soon as it's done: the Watermill and the Workshop by water falling through
their wheel from a header tank into a mill race, the Windmill once its bearing is started.

Create's blocks by id alone (checked in libs/create-fabric-6.0.10.0+mc1.21.1.jar): a water wheel's and a windmill
bearing's `facing` is their axle's direction, a shaft's, a gearbox's and a cogwheel's `axis` their own, a mechanical
press turns about its `facing` axis, a millstone takes its drive from below only, and a belt's pulleys (`part` start
and end) turn about the axis across its `facing`."""
from kit import *

CREATE = "create:"
MILL_STONE = Mix((5, "stone_bricks"), (3, "cobblestone"), (2, "mossy_stone_bricks"), seed=391)
MILL_FRAME = "spruce_log"


def _header_tank(b, x, z, y):
    """The header tank and its falling water beside a wheel at (x, y - 2, z + 1) turning about x: a water source held at
    (x, y, z) in stone bricks, falling through the wheel's north blades into a mill race at y 0 that runs south past
    the wheel's foot (walled, so the water stays in it). The water is placed last, so the wheel turns once it's done."""
    for dx, dz in ((-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1), (1, 1)):
        b.set(x + dx, y, z + dz, "stone_bricks")
    for dx in (-1, 1):  # the tank's legs, clear of the wheel
        for yy in range(0, y):
            b.set(x + dx, yy, z - 1, "stone_bricks" if yy % 3 == 0 else MILL_FRAME, **({} if yy % 3 == 0 else {"axis": "y"}))
    b.set(x, y + 1, z, "stone_brick_slab", type="bottom", waterlogged=False)  # a lid over the source, so it reads as a tank
    b.set(x, y, z, "water", level=0)
    for yy in range(0, y):  # the water as it falls (it comes by itself; the builder pours only the source)
        b.set(x, yy, z, "water", level=8)
    for i, zz in enumerate(range(z + 1, z + 4)):  # and as it runs down the race
        b.set(x, 0, zz, "water", level=i + 1)
    # The race: walled on both sides from the tank's foot to its end
    b.set(x, 0, z - 1, "stone_bricks")
    for zz in range(z, z + 4):
        b.set(x - 1, 0, zz, MILL_STONE)
        b.set(x + 1, 0, zz, MILL_STONE)
    b.set(x, 0, z + 4, "stone_bricks")


def _wheel(b, x, y, z):
    """A water wheel at (x, y, z), its axle along x, and a shaft east from it."""
    b.set(x, y, z, CREATE + "water_wheel", facing="east")
    b.set(x + 1, y, z, CREATE + "shaft", axis="x")


def watermill():
    """11 x 11 x 9: the Watermill. A seven-square stone-and-spruce mill house under a dark oak gable, its water wheel
    on the west wall turned by water falling from a header tank into a walled mill race. The wheel's shaft runs
    through the wall into a gearbox on the floor with the millstone over it (the farmer's machine, 39.3); the farmer's
    composter and the store chest beside it, hay by the back wall."""
    b = Build(11, 11, 9)
    plinth(b, 3, 1, 9, 7, MILL_STONE, floor="spruce_planks")
    walls(b, 3, 1, 9, 7, 1, 3, "stripped_spruce_log", axis="y")
    for x in range(4, 9):  # light infill between the posts
        for y in range(1, 4):
            b.set(x, y, 1, "birch_planks")
            b.set(x, y, 7, "birch_planks")
    for z in range(2, 7):
        for y in range(1, 4):
            b.set(3, y, z, MILL_STONE)  # the wheel wall is stone, against the spray
            b.set(9, y, z, "birch_planks")
    posts(b, [(3, 1), (5, 1), (7, 1), (9, 1), (3, 7), (6, 7), (9, 7), (9, 4)], 1, 3, MILL_FRAME)
    beam_ring(b, 3, 1, 9, 7, 4, MILL_FRAME)
    gable_roof(b, 2, 10, 0, 8, 5, DARK_OAK, axis="x", gable="birch_planks", eave_trim=SPRUCE)
    door(b, 6, 1, 1, "spruce_door", "south")
    stairs(b, 6, 0, 0, STONE_BRICK, "south")
    window(b, 4, 2, 1, "north", shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 8, 2, 1, "north", shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 9, 2, 4, "east", shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 6, 2, 7, "south", shutters="spruce_trapdoor", sill=SPRUCE)
    for x in (5, 7):
        lantern(b, x, 1, 0)  # either side of the door, on the step's flanks
        b.set(x, 0, 0, "stone_bricks")
    # The drive: wheel, shaft through the wall, gearbox, millstone
    _header_tank(b, 1, 3, 3)
    _wheel(b, 1, 1, 4)
    b.set(3, 1, 4, CREATE + "shaft", axis="x")
    b.set(4, 1, 4, CREATE + "gearbox", axis="z")
    b.set(4, 2, 4, CREATE + "millstone")
    # The farmer's corner and the store
    b.set(7, 1, 2, "composter", level=0)
    b.set(8, 1, 5, "chest", facing="west", type="single", waterlogged=False)
    b.set(8, 1, 6, "hay_block", axis="y")
    b.set(7, 1, 6, "hay_block", axis="x")
    b.set(8, 2, 6, "hay_block", axis="y")
    lantern(b, 6, 3, 4, hanging=True)
    for x in range(4, 9):  # a tie beam across the room
        b.set(x, 4, 4, MILL_FRAME, axis="x")
    b.fill_air()
    return b


def _sails(b, cx, cy, z):
    """Four arms of sails, two wide in a pinwheel, round a hub of planks at (cx, cy, z): sail frames by the hub, white
    sails out to three blocks. 24 sails in all (Create's windmill needs 8)."""
    b.set(cx, cy, z, "spruce_planks")
    arms = ([(cx, cy + i) for i in (1, 2, 3)] + [(cx + 1, cy + i) for i in (1, 2, 3)]
            + [(cx, cy - i) for i in (1, 2, 3)] + [(cx - 1, cy - i) for i in (1, 2, 3)]
            + [(cx + i, cy) for i in (1, 2, 3)] + [(cx + i, cy - 1) for i in (1, 2, 3)]
            + [(cx - i, cy) for i in (1, 2, 3)] + [(cx - i, cy + 1) for i in (1, 2, 3)])
    for x, y in arms:
        near = max(abs(x - cx), abs(y - cy)) == 1
        b.set(x, y, z, CREATE + ("sail_frame" if near else "white_sail"), facing="north")


def windmill():
    """9 x 17 x 9: the Windmill. A seven-square stone tower with a spruce frame under a spruce hip roof; on its
    front, a windmill bearing carries 24 sails in a pinwheel. The bearing's shaft runs back to a gearbox and straight
    down the middle of the tower to a pair of gearboxes on the floor, with the millstone (the farmer's machine, 39.3)
    on the second. The builder starts the bearing when the build is done (or leaves a sign saying how)."""
    b = Build(9, 17, 9)
    plinth(b, 1, 1, 7, 7, MILL_STONE, floor="spruce_planks")
    walls(b, 1, 1, 7, 7, 1, 9, STONE_MIX)
    posts(b, [(1, 1), (7, 1), (1, 7), (7, 7)], 1, 9, MILL_FRAME)
    beam_ring(b, 1, 1, 7, 7, 5, MILL_FRAME)
    beam_ring(b, 1, 1, 7, 7, 10, MILL_FRAME)
    hip_roof(b, 0, 8, 1, 8, 11, SPRUCE)
    door(b, 4, 1, 1, "spruce_door", "south")
    stairs(b, 4, 0, 0, STONE_BRICK, "south")
    for x in (3, 5):
        b.set(x, 0, 0, "stone_bricks")
        lantern(b, x, 1, 0)
    window(b, 7, 3, 4, "east", shutters="spruce_trapdoor", sill=STONE_BRICK)
    window(b, 1, 3, 4, "west", shutters="spruce_trapdoor", sill=STONE_BRICK)
    window(b, 4, 7, 7, "south", shutters="spruce_trapdoor", sill=STONE_BRICK)
    window(b, 7, 7, 4, "east", shutters="spruce_trapdoor", sill=STONE_BRICK)
    # The drive: bearing on the front wall, sails before it, the shaft down the middle to the millstone
    b.set(4, 9, 1, CREATE + "windmill_bearing", facing="north")
    _sails(b, 4, 9, 0)
    b.set(4, 9, 2, CREATE + "shaft", axis="z")
    b.set(4, 9, 3, CREATE + "shaft", axis="z")
    b.set(4, 9, 4, CREATE + "gearbox", axis="x")
    for y in range(2, 9):
        b.set(4, y, 4, CREATE + "shaft", axis="y")
    b.set(4, 1, 4, CREATE + "gearbox", axis="x")
    b.set(4, 1, 5, CREATE + "gearbox", axis="x")
    b.set(4, 2, 5, CREATE + "millstone")
    b.set(2, 1, 6, "composter", level=0)
    b.set(6, 1, 6, "chest", facing="west", type="single", waterlogged=False)
    b.set(6, 1, 2, "hay_block", axis="y")
    lantern(b, 6, 2, 2)  # on the hay bale
    b.fill_air()
    return b


def workshop():
    """14 x 10 x 9: the Workshop. A nine-wide brick-and-spruce shop under a spruce gable with a water wheel on its west
    wall (header tank and mill race as the Watermill's). Inside, the wheel's shaft drives a belt running to the front,
    which ends at a depot under a mechanical press (the armorer's machine, 39.3); the press is driven from the belt's
    far pulley by a column of cogwheels. The armorer's blast furnace and the store chest by the back wall."""
    b = Build(14, 10, 9)
    plinth(b, 3, 1, 11, 7, MILL_STONE, floor="polished_andesite")
    walls(b, 3, 1, 11, 7, 1, 3, "bricks")
    posts(b, [(3, 1), (5, 1), (9, 1), (11, 1), (3, 7), (7, 7), (11, 7), (11, 4)], 1, 3, MILL_FRAME)
    beam_ring(b, 3, 1, 11, 7, 4, MILL_FRAME)
    gable_roof(b, 2, 12, 0, 8, 5, SPRUCE, axis="x", gable="spruce_planks", eave_trim=DARK_OAK)
    door(b, 7, 1, 1, "spruce_door", "south")
    stairs(b, 7, 0, 0, STONE_BRICK, "south")
    for x in (6, 8):
        b.set(x, 0, 0, "stone_bricks")
        lantern(b, x, 1, 0)
    window(b, 4, 2, 1, "north", shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 10, 2, 1, "north", shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 11, 2, 3, "east", shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 9, 2, 7, "south", shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 5, 2, 7, "south", shutters="spruce_trapdoor", sill=SPRUCE)
    # The drive: wheel, shaft, belt to the front, depot, press driven up a cogwheel column from the belt's end
    _header_tank(b, 1, 4, 3)
    _wheel(b, 1, 1, 5)
    b.set(3, 1, 5, CREATE + "shaft", axis="x")
    belt = dict(facing="north", slope="horizontal", casing=False, waterlogged=False)
    b.set(4, 1, 5, CREATE + "belt", part="start", **belt)
    b.set(4, 1, 4, CREATE + "belt", part="middle", **belt)
    b.set(4, 1, 3, CREATE + "belt", part="end", **belt)
    b.set(4, 1, 2, CREATE + "depot")
    b.set(4, 3, 2, CREATE + "mechanical_press", facing="east")
    for y in (1, 2, 3):
        b.set(5, y, 3, CREATE + "cogwheel", axis="x")
    b.set(5, 3, 2, CREATE + "cogwheel", axis="x")
    # The armorer's corner and the store
    b.set(10, 1, 6, "blast_furnace", facing="north", lit=False)
    b.set(9, 1, 6, "chest", facing="north", type="single", waterlogged=False)
    b.set(10, 1, 5, "anvil", facing="east")
    b.set(8, 1, 6, "smithing_table")
    lantern(b, 7, 3, 4, hanging=True)
    for x in range(4, 11):  # a tie beam across the shop
        b.set(x, 4, 4, MILL_FRAME, axis="x")
    b.fill_air()
    return b


BALLAST = Mix((5, "gravel"), (3, "andesite"), (2, "cobblestone"), seed=397)


def train_station():
    """13 x 9 x 8: the Train Station (ROADMAP 39.7), a village's stop for the caravans' train. A straight run of Create
    track along the back (z 6, the whole width, on a ballast bed of gravel and andesite) with a Create station block on
    the platform's edge beside it (z 4, the middle); the platform of polished andesite under a dark oak shelter on
    spruce posts, closed on the street side by a birch-and-spruce wall with a doorway in the middle (a step before it)
    and two shuttered windows, short return walls at either end. Inside, two benches face the track, lanterns hang
    from the beams and stand on posts at the platform's ends, and hay waits for the next caravan's llamas."""
    b = Build(13, 9, 8)
    for x in range(13):  # the track bed and the track, the whole width so it runs on along the road
        for z in (5, 6, 7):
            b.set(x, 0, z, BALLAST)
        b.set(x, 1, 6, CREATE + "track", shape="xo", turn=False, waterlogged=False)
    plinth(b, 1, 1, 11, 4, BRICK_WALL_MIX, floor="polished_andesite")
    b.set(6, 1, 4, CREATE + "track_station", assembling=False, waterlogged=False)  # beside the track
    # The shelter: a wall on the street side, returns at the ends, open to the track
    for x in range(2, 11):
        for y in range(1, 4):
            b.set(x, y, 1, "birch_planks")
    for x in (2, 10):
        for z in (2,):
            for y in range(1, 4):
                b.set(x, y, z, "birch_planks")
    posts(b, [(2, 1), (5, 1), (7, 1), (10, 1), (2, 4), (10, 4)], 1, 3, "spruce_log")
    for y in (1, 2):  # the doorway in the middle of the wall
        b.set(6, y, 1, "air")
    b.set(6, 3, 1, "stripped_spruce_log", axis="x")
    stairs(b, 6, 0, 0, STONE_BRICK, "south")
    for x in (5, 7):
        b.set(x, 0, 0, "stone_bricks")
    window(b, 3, 2, 1, "north", shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 9, 2, 1, "north", shutters="spruce_trapdoor", sill=SPRUCE)
    beam_ring(b, 2, 1, 10, 4, 4, "spruce_log")
    gable_roof(b, 1, 11, 0, 5, 5, DARK_OAK, axis="x", gable="birch_planks", eave_trim=SPRUCE)
    # Benches facing the track, lanterns, the llamas' hay
    for x in (3, 4, 8, 9):
        bench_seat(b, x, 1, 2, "south", "spruce")
    lantern(b, 4, 3, 4, hanging=True)
    lantern(b, 8, 3, 4, hanging=True)
    lamp_post(b, 1, 1, 4)
    lamp_post(b, 11, 1, 4)
    b.set(3, 1, 4, "hay_block", axis="y")
    b.set(9, 1, 4, "hay_block", axis="x")
    b.fill_air()
    return b


def village_train():
    """4 x 4 x 1: the village train (ROADMAP 39.7c), the carriage a village's builder puts on the track past its Train
    Station for Create's station to assemble. Drawn running east (+x, away from the station; the mod turns it to the
    track): on the bottom row a small bogey (Create's, set on the track, built with railway casing), a railway casing
    hull and the caravan's chest, casing again at the tail; on the deck a driver's controls at the front facing the way
    out, the conductor's brown seat behind them and the controls facing back behind the seat (Create's conductor sits
    between them, so it drives both ways), a spruce rail at the tail; spruce posts at both ends hold a slab canopy, with a lantern hung under it over the chest. One wide, so it
    clears the station's platform; four high, so the conductor sits under the canopy."""
    b = Build(4, 4, 1)
    b.set(0, 0, 0, CREATE + "small_bogey", axis="x", waterlogged=False)
    b.set(1, 0, 0, CREATE + "railway_casing")
    b.set(2, 0, 0, "chest", facing="south", type="single", waterlogged=False)
    b.set(3, 0, 0, CREATE + "railway_casing")
    b.set(0, 1, 0, CREATE + "controls", facing="east", open=False, virtual=False, waterlogged=False)
    b.set(1, 1, 0, CREATE + "brown_seat", waterlogged=False)
    b.set(2, 1, 0, CREATE + "controls", facing="west", open=False, virtual=False, waterlogged=False)
    fence(b, 3, 1, 0, "spruce_fence")  # a rail at the tail
    for x in (0, 3):
        fence(b, x, 2, 0, "spruce_fence")
    lantern(b, 2, 2, 0, hanging=True)
    for x in range(4):
        b.set(x, 3, 0, "spruce_slab", type="bottom", waterlogged=False)
    return b


CREATE_BUILDS = (("watermill", watermill), ("windmill", windmill), ("workshop", workshop))
TRAIN_STATION = (("train_station", train_station),)  # ROADMAP 39.7: listed only with Create and villageTrains
