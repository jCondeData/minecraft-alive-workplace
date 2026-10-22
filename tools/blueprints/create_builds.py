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


FOUNDRY_BRICK = Mix((7, "bricks"), (2, "granite"), (1, "polished_granite"), seed=398)
FOUNDRY_FRAME = "stripped_dark_oak_log"


def brass_foundry():
    """13 x 14 x 11 (ROADMAP 39.8): the Brass Foundry. A nine-square brick hall on a stone-brick plinth, framed in dark
    oak under a deepslate gable, a brick chimney smoking at the back corner; the water wheel on its west wall (header
    tank and mill race as the Watermill's). Inside, the wheel's shaft turns a belt running out to a depot by the door,
    and from the belt's pulley a gearbox sends the drive up a shaft to two stacked steps of large and small cogwheels
    (8 RPM to 16 to 32: a mixer only works from 30) and on to a mechanical mixer over a basin heated by a blaze
    burner (the chef's mixer, 39.3: the basin keeps what it makes, facing down). The chef's smoker and the store chest
    by the front wall."""
    b = Build(13, 14, 11)
    plinth(b, 3, 1, 11, 9, BRICK_WALL_MIX, floor="polished_andesite")
    walls(b, 3, 1, 11, 9, 1, 5, FOUNDRY_BRICK)
    posts(b, [(3, 1), (6, 1), (8, 1), (11, 1), (3, 9), (7, 9), (11, 9), (11, 5)], 1, 5, FOUNDRY_FRAME)
    beam_ring(b, 3, 1, 11, 9, 3, FOUNDRY_FRAME)  # a floor-line beam halfway up the tall walls
    beam_ring(b, 3, 1, 11, 9, 6, FOUNDRY_FRAME)
    gable_roof(b, 2, 12, 0, 10, 7, DEEPSLATE_TILE, axis="x", gable="bricks", eave_trim=DARK_OAK)
    door(b, 7, 1, 1, "dark_oak_door", "south")
    stairs(b, 7, 0, 0, STONE_BRICK, "south")
    for x in (6, 8):
        b.set(x, 0, 0, "stone_bricks")
    for x in (6, 8):
        lantern(b, x, 1, 0)
    for x in (4, 10):
        window(b, x, 2, 1, "north", shutters="dark_oak_trapdoor", sill=BRICK)
        window(b, x, 4, 1, "north", height=1)
    window(b, 11, 2, 3, "east", shutters="dark_oak_trapdoor", sill=BRICK)
    window(b, 11, 2, 7, "east", shutters="dark_oak_trapdoor", sill=BRICK)
    window(b, 5, 2, 9, "south", shutters="dark_oak_trapdoor", sill=BRICK)
    window(b, 9, 4, 9, "south")
    chimney(b, 10, 10, 0, 12, "bricks")
    # The drive: wheel, shaft, belt out to the depot; from the belt's pulley a gearbox, up to the gear train and the mixer
    _header_tank(b, 1, 5, 3)
    _wheel(b, 1, 1, 6)
    b.set(3, 1, 6, CREATE + "shaft", axis="x")
    belt = dict(facing="north", slope="horizontal", casing=False, waterlogged=False)
    b.set(4, 1, 6, CREATE + "belt", part="start", **belt)
    b.set(4, 1, 5, CREATE + "belt", part="middle", **belt)
    b.set(4, 1, 4, CREATE + "belt", part="middle", **belt)
    b.set(4, 1, 3, CREATE + "belt", part="end", **belt)
    b.set(4, 1, 2, CREATE + "depot")
    b.set(5, 1, 6, CREATE + "gearbox", axis="z")
    b.set(5, 2, 6, CREATE + "shaft", axis="y")
    b.set(5, 3, 6, CREATE + "large_cogwheel", axis="y")  # 8 RPM
    b.set(6, 3, 7, CREATE + "cogwheel", axis="y")  # 16, and up its axle
    b.set(6, 4, 7, CREATE + "large_cogwheel", axis="y")
    b.set(7, 4, 8, CREATE + "cogwheel", axis="y")  # 32
    b.set(8, 4, 8, CREATE + "mechanical_mixer")
    b.set(8, 3, 8, "air")
    b.set(8, 2, 8, CREATE + "basin", facing="down")
    b.set(8, 1, 8, CREATE + "blaze_burner", blaze="smouldering", facing="north")
    # The chef's corner and the store
    b.set(9, 1, 2, "smoker", facing="north", lit=False)
    b.set(10, 1, 2, "chest", facing="north", type="single", waterlogged=False)
    b.set(10, 1, 4, "barrel", facing="up", open=False)
    b.set(10, 1, 5, "barrel", facing="up", open=False)
    b.set(10, 2, 4, "barrel", facing="west", open=False)
    b.set(9, 1, 8, "anvil", facing="north")
    lantern(b, 7, 5, 4, hanging=True)
    lantern(b, 10, 2, 5)
    for x in range(4, 11):  # a tie beam across the hall, the lantern under it
        b.set(x, 6, 4, FOUNDRY_FRAME, axis="x")
    b.fill_air()
    return b


SAW_FRAME = "spruce_log"


def sawmill():
    """15 x 11 x 12 (ROADMAP 39.8): the Sawmill. An eleven-by-ten timber mill, spruce posts and oak plank walls over a
    cobblestone plinth under a dark oak gable, a log store under a lean-to on its east side; the water wheel on the
    west wall (header tank and mill race as the Watermill's). Inside, the wheel's shaft turns a belt running to the
    front, through two mechanical saws facing up (each driven from the belt's far pulley by a column of cogwheels; the
    second is flipped, as the two turn opposite ways, so both cut the same way) to a depot by the door. The
    lumberjack's fletching table, the store chest and stacked logs."""
    b = Build(15, 11, 12)
    plinth(b, 3, 1, 11, 10, STONE_MIX, floor="spruce_planks")
    walls(b, 3, 1, 11, 10, 1, 3, "oak_planks")
    posts(b, [(3, 1), (6, 1), (8, 1), (11, 1), (3, 10), (7, 10), (11, 10), (11, 4), (11, 7), (3, 4)], 1, 3, SAW_FRAME)
    beam_ring(b, 3, 1, 11, 10, 4, SAW_FRAME)
    gable_roof(b, 2, 12, 0, 11, 5, DARK_OAK, axis="z", gable="oak_planks", eave_trim=SPRUCE)
    door(b, 7, 1, 1, "spruce_door", "south")
    stairs(b, 7, 0, 0, STONE_BRICK, "south")
    for x in (6, 8):
        b.set(x, 0, 0, "cobblestone")
        lantern(b, x, 1, 0)
    window(b, 5, 2, 1, "north", shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 10, 2, 1, "north", shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 9, 2, 10, "south", shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 5, 2, 10, "south", shutters="spruce_trapdoor", sill=SPRUCE)
    window(b, 7, 6, 1, "north", sill=SPRUCE)  # a little window up in the front gable
    # The log store: a lean-to on the east wall, logs stacked under it
    for z in range(3, 9):
        b.set(12, 0, z, "cobblestone")
        for y in (1, 2):
            b.set(12, y, z, "spruce_log" if (y + z) % 3 else "oak_log", axis="z")
    for z in (2, 9):
        fence(b, 12, 1, z, "spruce_fence")
        fence(b, 12, 2, z, "spruce_fence")
    for z in range(2, 10):
        stairs(b, 12, 3, z, SPRUCE, "west")
    # The drive: wheel, shaft, belt to the front through two saws to the depot
    _header_tank(b, 1, 7, 3)
    _wheel(b, 1, 1, 8)
    b.set(3, 1, 8, CREATE + "shaft", axis="x")
    belt = dict(facing="north", slope="horizontal", casing=False, waterlogged=False)
    b.set(4, 1, 8, CREATE + "belt", part="start", **belt)
    b.set(4, 1, 7, CREATE + "belt", part="middle", **belt)
    b.set(4, 1, 6, CREATE + "belt", part="middle", **belt)
    b.set(4, 1, 5, CREATE + "belt", part="end", **belt)
    b.set(4, 1, 4, CREATE + "mechanical_saw", facing="up", axis_along_first=True, flipped=False)
    b.set(4, 1, 3, CREATE + "mechanical_saw", facing="up", axis_along_first=True, flipped=True)
    b.set(4, 1, 2, CREATE + "depot")
    for z in (5, 4, 3):
        b.set(5, 1, z, CREATE + "cogwheel", axis="x")
    # The lumberjack's corner and the store
    b.set(10, 1, 2, "fletching_table")
    b.set(10, 1, 3, "chest", facing="west", type="single", waterlogged=False)
    for x, z in ((9, 9), (10, 9), (10, 8)):
        b.set(x, 1, z, "stripped_spruce_log", axis="x")
    b.set(10, 2, 9, "stripped_spruce_log", axis="x")
    b.set(7, 1, 9, "barrel", facing="up", open=False)
    lantern(b, 7, 3, 5, hanging=True)
    for z in range(2, 10):  # a tie beam along the mill, the lantern under it
        b.set(7, 4, z, SAW_FRAME, axis="z")
    b.fill_air()
    return b


CRUSH_STONE = Mix((5, "cobblestone"), (3, "andesite"), (2, "stone"), seed=399)


def crushing_mill():
    """14 x 12 x 11 (ROADMAP 39.8): the Crushing Mill. A nine-by-nine andesite-and-cobble mill house framed in spruce
    under a spruce gable; the water wheel on its west wall (header tank and mill race as the Watermill's). Inside, a
    pair of crushing wheels (their axles along z, a block apart: Create puts its crusher in the gap) turned the opposite
    ways through a train of gearboxes, a short belt over them dropping into the gap, and under the gap a pit where the
    crushed ore lands (the miner's crusher, 39.3: fed from the gap, collected from the pit) with an item drain in front
    of it. The miner's blast furnace and the store chest by the front wall."""
    b = Build(14, 12, 11)
    plinth(b, 3, 1, 11, 9, BRICK_WALL_MIX, floor="polished_andesite")
    walls(b, 3, 1, 11, 9, 1, 4, CRUSH_STONE)
    posts(b, [(3, 1), (6, 1), (8, 1), (11, 1), (3, 9), (7, 9), (11, 9), (11, 5)], 1, 4, MILL_FRAME)
    beam_ring(b, 3, 1, 11, 9, 5, MILL_FRAME)
    gable_roof(b, 2, 12, 0, 10, 6, SPRUCE, axis="x", gable="spruce_planks", eave_trim=DARK_OAK)
    door(b, 7, 1, 1, "spruce_door", "south")
    stairs(b, 7, 0, 0, STONE_BRICK, "south")
    for x in (6, 8):
        b.set(x, 0, 0, "stone_bricks")
        lantern(b, x, 1, 0)
    window(b, 4, 2, 1, "north", shutters="spruce_trapdoor", sill=STONE_BRICK)
    window(b, 10, 2, 1, "north", shutters="spruce_trapdoor", sill=STONE_BRICK)
    window(b, 11, 2, 3, "east", shutters="spruce_trapdoor", sill=STONE_BRICK)
    window(b, 11, 2, 7, "east", shutters="spruce_trapdoor", sill=STONE_BRICK)
    window(b, 9, 2, 9, "south", shutters="spruce_trapdoor", sill=STONE_BRICK)
    # The drive: wheel, shaft, gearboxes up and across to both wheels (the two sides of the second gearbox turn the
    # opposite ways), and from the top gearbox a shaft to the feed belt over the gap
    _header_tank(b, 1, 6, 3)
    _wheel(b, 1, 1, 7)
    b.set(3, 1, 7, CREATE + "shaft", axis="x")
    b.set(4, 1, 7, CREATE + "gearbox", axis="z")
    b.set(4, 2, 7, CREATE + "gearbox", axis="z")
    b.set(4, 3, 7, CREATE + "gearbox", axis="z")
    b.set(5, 2, 7, CREATE + "gearbox", axis="y")
    b.set(6, 2, 7, CREATE + "shaft", axis="x")
    b.set(7, 2, 7, CREATE + "gearbox", axis="y")
    b.set(5, 2, 6, CREATE + "shaft", axis="z")
    b.set(7, 2, 6, CREATE + "shaft", axis="z")
    b.set(5, 2, 5, CREATE + "crushing_wheel", axis="z")
    b.set(7, 2, 5, CREATE + "crushing_wheel", axis="z")
    b.set(6, 2, 5, "structure_void")  # Create's crusher, between the wheels
    b.set(6, 2, 6, CREATE + "andesite_casing")  # closing the crusher at the back
    b.set(5, 3, 7, CREATE + "shaft", axis="x")
    belt = dict(facing="north", slope="horizontal", casing=False, waterlogged=False)
    b.set(6, 3, 7, CREATE + "belt", part="start", **belt)
    b.set(6, 3, 6, CREATE + "belt", part="end", **belt)
    # The pit under the gap: closed at the sides and back, open to the front where the drain stands
    b.set(6, 1, 5, "air")
    for x, z in ((5, 5), (7, 5), (6, 6), (5, 4), (7, 4)):
        b.set(x, 1, z, CREATE + "andesite_casing")
    b.set(6, 1, 3, CREATE + "item_drain")
    # The miner's corner and the store
    b.set(9, 1, 2, "blast_furnace", facing="north", lit=False)
    b.set(10, 1, 2, "chest", facing="north", type="single", waterlogged=False)
    b.set(10, 1, 8, "barrel", facing="up", open=False)
    b.set(9, 1, 8, "barrel", facing="up", open=False)
    b.set(10, 2, 8, "barrel", facing="west", open=False)
    lantern(b, 9, 4, 5, hanging=True)
    for x in range(8, 11):  # a tie beam over the store, the lantern under it
        b.set(x, 5, 5, MILL_FRAME, axis="x")
    b.fill_air()
    return b


GLASS_FRAME = "stripped_spruce_log"


def greenhouse():
    """15 x 11 x 13 (ROADMAP 39.8): the Greenhouse. An eleven-square glass house on a stone-brick knee wall, framed in
    stripped spruce, under a stepped glass roof on spruce rafters (one glass type: glass blocks); the water wheel on its
    west wall (header tank and mill race as the Watermill's). Inside, beds of farmland watered by two channels, and down
    the aisle between them a belt from the wheel's shaft, running to a depot by the front: the harvest line, with a
    mechanical harvester and a mechanical plough set on it, ready to be fixed to a contraption (Create's harvester and
    plough only work moving). The farmer's composter and the store chest in the back corner."""
    b = Build(15, 11, 13)
    x0, z0, x1, z1 = 3, 1, 13, 11
    plinth(b, x0, z0, x1, z1, BRICK_WALL_MIX, floor="polished_andesite")
    walls(b, x0, z0, x1, z1, 1, 1, MOSSY_BRICK_MIX)  # the knee wall
    walls(b, x0, z0, x1, z1, 2, 3, "glass")
    posts(b, [(3, 1), (6, 1), (10, 1), (13, 1), (3, 11), (6, 11), (10, 11), (13, 11),
              (3, 4), (3, 8), (13, 4), (13, 8)], 1, 3, GLASS_FRAME)
    beam_ring(b, x0, z0, x1, z1, 4, GLASS_FRAME)
    for x in range(x0 + 1, x1):  # tie beams under the rafters
        for z in (4, 8):
            b.set(x, 4, z, GLASS_FRAME, axis="x")
    # The roof: eaves of spruce stairs, then steps of glass up to a spruce ridge beam; rafters over the posts
    rafters = (1, 4, 8, 11)
    for z in range(0, 13):
        stairs(b, 2, 4, z, SPRUCE, "east")
        stairs(b, 14, 4, z, SPRUCE, "west")
        for i in range(5):
            y = 5 + i
            for x in (3 + i, 13 - i):
                if z in (0, 12):
                    b.set(x, y, z, "spruce_planks")  # the verge boards
                elif z in rafters:
                    b.set(x, y, z, GLASS_FRAME, axis="z")
                else:
                    b.set(x, y, z, "glass")
        b.set(8, 10, z, "spruce_log", axis="z")
    for z in (1, 11):  # the gable ends: glass in the triangle, a king post up the middle
        for i in range(1, 5):
            for x in range(3 + i, 14 - i):
                b.set(x, 4 + i, z, "glass")
        for y in range(5, 10):
            b.set(8, y, z, GLASS_FRAME, axis="y")
    door(b, 8, 1, 1, "spruce_door", "south")
    b.set(8, 4, 1, GLASS_FRAME, axis="x")
    stairs(b, 8, 0, 0, STONE_BRICK, "south")
    for x in (7, 9):
        b.set(x, 0, 0, "stone_bricks")
        lantern(b, x, 1, 0)
        b.set(x, 1, 1, MOSSY_BRICK_MIX)
    # The beds: farmland either side of the belt, a water channel through each
    for z in range(3, 9):
        b.set(4, 0, z, "water", level=0)
        b.set(10, 0, z, "water", level=0)
        for x in (5, 7, 8, 9, 11, 12):
            b.set(x, 0, z, "farmland", moisture=7)
            name, age = (("wheat", (x + z) % 4) if x in (5, 7, 8) else ("carrots" if x == 11 else "potatoes", (x * z) % 4))
            b.set(x, 1, z, name, age=age)
    b.set(4, 0, 9, "stone_bricks")
    b.set(10, 0, 9, "stone_bricks")
    # The drive: wheel, shaft through the wall, the belt down the aisle to the depot at the front
    _header_tank(b, 1, 8, 3)
    _wheel(b, 1, 1, 9)
    for x in (3, 4, 5):
        b.set(x, 1, 9, CREATE + "shaft", axis="x")
    belt = dict(facing="north", slope="horizontal", casing=False, waterlogged=False)
    b.set(6, 1, 9, CREATE + "belt", part="start", **belt)
    for z in range(4, 9):
        b.set(6, 1, z, CREATE + "belt", part="middle", **belt)
    b.set(6, 1, 3, CREATE + "belt", part="end", **belt)
    b.set(6, 1, 2, CREATE + "depot")
    # A cart of andesite casing on the belt, a harvester on its front and a plough on its back (each held by its back)
    b.set(6, 2, 8, CREATE + "andesite_casing")
    b.set(6, 2, 7, CREATE + "mechanical_harvester", facing="north", waterlogged=False)
    b.set(6, 2, 9, CREATE + "mechanical_plough", facing="south", waterlogged=False)
    # The farmer's corner and the store
    b.set(12, 1, 10, "composter", level=0)
    b.set(11, 1, 10, "chest", facing="north", type="single", waterlogged=False)
    b.set(12, 1, 9, "barrel", facing="up", open=False)
    b.set(9, 1, 10, "hay_block", axis="y")
    b.set(4, 1, 2, "potted_fern")
    b.set(4, 0, 2, "polished_andesite")
    lantern(b, 8, 3, 4, hanging=True)
    lantern(b, 8, 3, 8, hanging=True)
    b.fill_air()
    return b


CLOCK_STONE = Mix((6, "stone_bricks"), (2, "cracked_stone_bricks"), (2, "mossy_stone_bricks"), seed=401)
CLOCK_PLASTER = Mix((9, "white_concrete"), (1, "polished_diorite"), seed=402)
CLOCK_FRAME = "dark_oak_log"


def clock_tower():
    """11 x 19 x 9 (ROADMAP 39.8): the Clock Tower. A seven-square tower: a stone-brick shaft two storeys high, then a
    plastered belfry storey framed in dark oak, under a dark oak hip roof; a cuckoo clock set in the front of the
    belfry under a little stair hood. The water wheel at its foot on the west side (header tank and mill race as the
    Watermill's) turns the clock: its shaft through the wall, gearboxes, a shaft straight up the middle of the tower and
    one forward to the clock's back. Purely for show, with a cartographer's table and a chest by the door."""
    b = Build(11, 19, 9)
    x0, z0, x1, z1 = 3, 1, 9, 7
    plinth(b, x0, z0, x1, z1, BRICK_WALL_MIX, floor="spruce_planks")
    walls(b, x0, z0, x1, z1, 1, 8, CLOCK_STONE)
    walls(b, x0, z0, x1, z1, 9, 12, CLOCK_PLASTER)
    posts(b, [(3, 1), (9, 1), (3, 7), (9, 7)], 1, 12, CLOCK_FRAME)
    posts(b, [(5, 1), (7, 1), (5, 7), (7, 7), (3, 4), (9, 4)], 9, 12, CLOCK_FRAME)
    beam_ring(b, x0, z0, x1, z1, 8, CLOCK_FRAME)
    beam_ring(b, x0, z0, x1, z1, 13, CLOCK_FRAME)
    hip_roof(b, 2, 10, 0, 8, 14, DARK_OAK)
    door(b, 6, 1, 1, "spruce_door", "south")
    stairs(b, 6, 0, 0, STONE_BRICK, "south")
    for x in (5, 7):
        b.set(x, 0, 0, "stone_bricks")
        lantern(b, x, 1, 0)
    window(b, 6, 5, 1, "north", height=2, shutters="dark_oak_trapdoor", sill=STONE_BRICK)
    window(b, 9, 2, 4, "east", shutters="dark_oak_trapdoor", sill=STONE_BRICK)
    window(b, 9, 5, 4, "east", height=2, sill=STONE_BRICK)
    window(b, 6, 5, 7, "south", height=2, sill=STONE_BRICK)
    window(b, 3, 5, 4, "west", height=2, sill=STONE_BRICK)
    for x, z, out in ((6, 7, "south"),):
        window(b, x, 10, z, out, height=2, shutters="dark_oak_trapdoor", sill=DARK_OAK)
    for x, z, out in ((9, 2, "east"), (9, 6, "east"), (3, 2, "west"), (3, 6, "west")):
        window(b, x, 10, z, out, height=2, sill=DARK_OAK)
    # The clock: in the belfry's front, framed by a sill and a hood of stairs, its shaft from behind
    b.set(6, 10, 1, CREATE + "cuckoo_clock", facing="north")
    for x in (5, 6, 7):
        stairs(b, x, 9, 0, DARK_OAK, "south", top=True)
        stairs(b, x, 11, 0, DARK_OAK, "south")
    # The drive: wheel, shaft through the wall, a gearbox and on to the middle, up, and forward to the clock
    _header_tank(b, 1, 4, 3)
    _wheel(b, 1, 1, 5)
    b.set(3, 1, 5, CREATE + "shaft", axis="x")
    b.set(4, 1, 5, CREATE + "shaft", axis="x")
    b.set(5, 1, 5, CREATE + "shaft", axis="x")
    b.set(6, 1, 5, CREATE + "gearbox", axis="z")
    for y in range(2, 10):
        b.set(6, y, 5, CREATE + "shaft", axis="y")
    b.set(6, 10, 5, CREATE + "gearbox", axis="x")
    for z in (4, 3, 2):
        b.set(6, 10, z, CREATE + "shaft", axis="z")
    # The keeper's corner
    b.set(8, 1, 2, "cartography_table")
    b.set(8, 1, 6, "chest", facing="west", type="single", waterlogged=False)
    b.set(4, 1, 2, "barrel", facing="up", open=False)
    lantern(b, 4, 2, 2)
    lantern(b, 6, 7, 3, hanging=True)
    for x in range(4, 9):  # a tie beam across under the belfry, the lantern hung from it
        b.set(x, 8, 3, CLOCK_FRAME, axis="x")
    b.fill_air()
    return b


PUMP_BRICK = Mix((8, "bricks"), (2, "granite"), seed=403)


def pump_house():
    """14 x 10 x 9 (ROADMAP 39.8): the Pump House. A small nine-by-seven brick house framed in spruce under a dark oak
    gable, a lean-to store on its east side; the water wheel on its west wall (header tank and mill race as the
    Watermill's). Inside, the wheel's shaft ends in a cogwheel turning a mechanical pump beside it; the pump draws from
    a sump of four water sources in the floor (so it refills) through an intake pipe and pushes up a pipe into a fluid
    tank two blocks tall. The leatherworker's cauldron and the store chest by the front wall."""
    b = Build(14, 10, 9)
    x0, z0, x1, z1 = 3, 1, 11, 7
    plinth(b, x0, z0, x1, z1, BRICK_WALL_MIX, floor="polished_andesite")
    walls(b, x0, z0, x1, z1, 1, 3, PUMP_BRICK)
    posts(b, [(3, 1), (6, 1), (8, 1), (11, 1), (3, 7), (7, 7), (11, 7), (11, 4)], 1, 3, MILL_FRAME)
    beam_ring(b, x0, z0, x1, z1, 4, MILL_FRAME)
    gable_roof(b, 2, 12, 0, 8, 5, DARK_OAK, axis="x", gable="spruce_planks", eave_trim=SPRUCE)
    door(b, 7, 1, 1, "spruce_door", "south")
    stairs(b, 7, 0, 0, STONE_BRICK, "south")
    for x in (6, 8):
        b.set(x, 0, 0, "stone_bricks")
        lantern(b, x, 1, 0)
    window(b, 4, 2, 1, "north", shutters="spruce_trapdoor", sill=BRICK)
    window(b, 10, 2, 1, "north", shutters="spruce_trapdoor", sill=BRICK)
    window(b, 9, 2, 7, "south", shutters="spruce_trapdoor", sill=BRICK)
    window(b, 5, 2, 7, "south", shutters="spruce_trapdoor", sill=BRICK)
    # The lean-to store on the east wall: barrels under a stair roof on fence posts
    for z in range(2, 7):
        b.set(12, 0, z, "cobblestone")
    b.set(12, 1, 3, "barrel", facing="up", open=False)
    b.set(12, 1, 4, "barrel", facing="west", open=False)
    b.set(12, 1, 5, "barrel", facing="up", open=False)
    for z in (2, 6):
        fence(b, 12, 1, z, "spruce_fence")
        fence(b, 12, 2, z, "spruce_fence")
    for z in range(2, 7):
        stairs(b, 12, 3, z, SPRUCE, "west")
    # The drive: wheel, shaft through the wall to a cogwheel, the pump meshing with it
    _header_tank(b, 1, 4, 3)
    _wheel(b, 1, 1, 5)
    for x in (3, 4, 5):
        b.set(x, 1, 5, CREATE + "shaft", axis="x")
    b.set(6, 1, 5, CREATE + "cogwheel", axis="x")
    b.set(6, 1, 4, CREATE + "mechanical_pump", facing="east", waterlogged=False)
    pipe = dict(north=False, south=False, east=False, west=False, up=False, down=False, waterlogged=False)
    b.set(5, 1, 4, CREATE + "fluid_pipe", **{**pipe, "east": True, "down": True})  # the intake, open over the sump
    b.set(7, 1, 4, CREATE + "fluid_pipe", **{**pipe, "west": True, "east": True})
    b.set(8, 1, 4, CREATE + "fluid_tank", top=False, bottom=True, shape="window")
    b.set(8, 2, 4, CREATE + "fluid_tank", top=True, bottom=False, shape="window")
    for x, z in ((4, 3), (5, 3), (4, 4), (5, 4)):  # the sump: four sources, so what the pump draws refills
        b.set(x, 0, z, "water", level=0)
    # The leatherworker's corner and the store
    b.set(10, 1, 2, "cauldron")
    b.set(9, 1, 2, "chest", facing="north", type="single", waterlogged=False)
    b.set(10, 1, 6, "barrel", facing="up", open=False)
    lantern(b, 10, 2, 6)
    lantern(b, 7, 3, 4, hanging=True)
    for x in range(4, 11):  # a tie beam across the room, the lantern under it
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
MORE_CREATE_BUILDS = (("brass_foundry", brass_foundry), ("sawmill", sawmill), ("crushing_mill", crushing_mill),
                      ("greenhouse", greenhouse), ("clock_tower", clock_tower), ("pump_house", pump_house))  # 39.8
TRAIN_STATION = (("train_station", train_station),)  # ROADMAP 39.7: listed only with Create and villageTrains
