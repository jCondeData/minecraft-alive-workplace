"""
The Village Hall build: a home for the Village Hall block, in three levels that replace each other on the same spot
(front at z = 0), like the homes. Every level keeps the hall block at (6, 1, 10), facing the door, and the bell under
the porch at (6, 4, 0): an upgrade never moves either, so the village never loses its hall while the builder works.

Design (after the Minecraft Architect review):
- I, 13 x 13 x 13: a timber hall (x 1-11, z 3-11, walls four high): a cobbled plinth, a spruce frame (posts at x 1, 4,
  8, 11 and z 3, 7, 11) with birch infill under a deepslate-tile gable (ridge along x, a 1-block overhang with a soffit),
  the gable ends studded with a king post and a small window. A gabled porch on two log posts over the door, its ridge
  running up into the main roof, the village bell hung from its front beam and a lantern either side of the door. The
  notice board (four notices over a shelf) in the west bay, a flower-boxed window, a bench and a lamp on the east,
  banners on the front corner posts. Inside: the
  Village Hall at the head of a red runner, two banners behind it, four benches facing it, lanterns on two tie beams.
- II, 19 x 13 x 13: a meeting room grows out of the east wall (x 11-17, z 4-10, two blocks shallower, so its ridge sits
  a block lower): a council table with eight seats, bookshelves, its own door between flower boxes and lanterns, a
  bench out front. The hall's east window becomes the doorway between them.
- III, 24 x 24 x 13: a stone bell-and-clock tower (x 18-22, z 5-9) at the end of the meeting room: quoined corners on
  a stepped stone footing, a door to the street, a clock face on the front and the east, a cornice, an open belfry
  with the great bell, a steep hipped deepslate cap. Banners on its front, lamp posts at the street.

Blocks are the five build styles' base set (spruce, birch, cobblestone, stone bricks, andesite, deepslate tiles), so
each style swaps them (data/aliveworkplace/blueprint_styles) and the hall reads right in all of them.
"""
from kit import *

FRAME = "spruce_log"
INFILL = "birch_planks"
ROOF = DEEPSLATE_TILE
TRIM = SPRUCE
BANNER = "blue"
HALL_POS = (6, 1, 10)


def hall_body(b):
    """The hall's walls x 1-11, z 3-11 (four high) with its frame, windows, door and the room inside."""
    plinth(b, 1, 3, 11, 11, STONE_MIX, floor="spruce_planks")
    walls(b, 1, 3, 11, 11, 1, 4, INFILL)
    posts(b, [(1, 3), (4, 3), (8, 3), (11, 3), (1, 11), (4, 11), (8, 11), (11, 11), (1, 7), (11, 7)], 1, 4, FRAME)
    beam_ring(b, 1, 3, 11, 11, 5, FRAME)
    # The door in the middle bay, a lintel over it and a step before it
    door(b, 6, 1, 3, "spruce_door", "south")
    log(b, 6, 3, 3, FRAME, axis="x")
    # A window with a flower box in the east bay, the notice board in the west one (no shutters: the banners hang on
    # the corner posts beside them)
    window(b, 9, 2, 3, "north", width=2, height=2, flowers=(TRIM.trapdoor, ["potted_cornflower", "potted_allium"]))
    for x in (1, 11):
        b.set(x, 3, 2, f"{BANNER}_wall_banner", facing="north")
    # Side and back windows, shuttered, on sills
    for z in (5, 9):
        window(b, 1, 2, z, "west", height=2, shutters=TRIM.trapdoor, sill=TRIM)
        window(b, 11, 2, z, "east", height=2, shutters=TRIM.trapdoor, sill=TRIM)
    for x in (2, 9):
        window(b, x, 2, 11, "south", width=2, height=2, shutters=TRIM.trapdoor, sill=TRIM)
    pane(b, 6, 3, 11)
    # Inside: the Village Hall at the head of a red runner, banners behind it, benches facing it
    b.set(*HALL_POS, "aliveworkplace:village_hall", facing="north")
    for z in range(4, 10):
        b.set(6, 1, z, "red_carpet")
    for x in (4, 8):
        b.set(x, 3, 10, f"{BANNER}_wall_banner", facing="north")
    for x in (5, 7):
        b.set(x, 1, 10, "potted_fern")
        b.set(x, 0, 10, "polished_andesite")
    b.set(6, 0, 10, "polished_andesite")
    for z in (5, 7):
        for x in (2, 3, 4, 8, 9, 10):
            stairs(b, x, 1, z, TRIM, "north")
    for z in (6, 9):  # tie beams for the lights
        for x in range(2, 11):
            log(b, x, 5, z, FRAME, axis="x")
        lantern(b, 3, 4, z, hanging=True)
        lantern(b, 9, 4, z, hanging=True)


def hall_roof(b):
    """The main gable (ridge along x at y 10) with studded gable ends, then the porch's cross gable meeting it."""
    gable_roof(b, 0, 12, 2, 12, 5, ROOF, axis="x", gable=INFILL, gable_at=(1, 11), eave_trim=TRIM)
    for x in (1, 11):
        log(b, x, 6, 7, FRAME)
        pane(b, x, 7, 7)
        log(b, x, 8, 7, FRAME)
        for z in (5, 9):  # studs over the side windows, up to the roof
            for y in (6, 7):
                log(b, x, y, z, FRAME)


def hall_porch(b):
    """The porch before the door: log posts on stone, a stone deck, a wide step, beams carrying a cross gable whose ridge
    runs into the main roof. The bell hangs from the front beam; lanterns hang either side of the door."""
    for x in (4, 8):
        b.set(x, 0, 0, "stone_bricks")
        posts(b, [(x, 0)], 1, 4, FRAME)
    for x in range(4, 9):
        for z in (1, 2):
            b.set(x, 0, z, resolve(STONE_MIX, x, 0, z))
    for x in (5, 6, 7):
        stairs(b, x, 0, 0, COBBLE, "south")
    for x in (4, 8):
        for z in (0, 1, 2):
            log(b, x, 5, z, FRAME, axis="z")
    for x in (5, 6, 7):
        log(b, x, 5, 0, FRAME, axis="x")
        log(b, x, 5, 2, FRAME, axis="x")
    gable_roof(b, 3, 9, 0, 3, 6, ROOF, axis="z", gable=INFILL, gable_at=(0,), eave_trim=ROOF)
    log(b, 6, 7, 0, FRAME)
    pane(b, 6, 8, 0)
    b.set(6, 4, 0, "bell", attachment="ceiling", facing="east", powered=False)
    lantern(b, 5, 4, 2, hanging=True)
    lantern(b, 7, 4, 2, hanging=True)


def notice_board(b):
    """The west bay of the front (x 2-3): four notices on the wall over a shelf, under the eaves' soffit."""
    for x in (2, 3):
        trapdoor(b, x, 1, 2, TRIM.trapdoor, "south", half="top")
        for y in (2, 3):
            b.set(x, y, 3, "spruce_planks")  # the board
            b.set(x, y, 2, "spruce_wall_sign", facing="north", waterlogged=False)


def village_hall():
    """13 x 13 x 13: the timber hall, its porch with the bell, the notice board, a bench and a lamp out front."""
    b = Build(13, 13, 13)
    hall_body(b)
    hall_roof(b)
    hall_porch(b)
    notice_board(b)
    stairs(b, 9, 0, 1, TRIM, "south")
    stairs(b, 10, 0, 1, TRIM, "south")
    lamp_post(b, 12, 0, 1, "spruce_fence", height=2)
    b.fill_air()
    return b


def meeting_room(b):
    """Hall II's meeting room, x 11-17, z 4-10, through the hall's east wall."""
    # The hall's east shutters and sills come off; the south-east window becomes the doorway, the other is walled up
    b.clear(12, 1, 4, 12, 4, 10)
    for y in (1, 2):
        b.set(11, y, 5, "air")
    b.set(11, 3, 5, INFILL)
    for y in (2, 3):
        b.set(11, y, 9, INFILL)
    plinth(b, 11, 4, 17, 10, STONE_MIX, floor="spruce_planks")
    b.set(11, 0, 5, "spruce_planks")
    for y in range(1, 5):
        for x in range(12, 18):
            b.set(x, y, 4, INFILL)
            b.set(x, y, 10, INFILL)
        for z in range(5, 10):
            b.set(17, y, z, INFILL)
    posts(b, [(17, 4), (17, 10), (17, 7)], 1, 4, FRAME)
    for x in range(12, 18):
        log(b, x, 5, 4, FRAME, axis="x")
        log(b, x, 5, 10, FRAME, axis="x")
    for z in range(5, 10):
        log(b, 17, 5, z, FRAME, axis="z")
    # Its door in the middle of the front between two posts, a lantern hanging from a beam end over each, a lintel and
    # a step; a window with a flower box either side
    posts(b, [(13, 4), (15, 4)], 1, 4, FRAME)
    door(b, 14, 1, 4, "spruce_door", "south")
    log(b, 14, 3, 4, FRAME, axis="x")
    stairs(b, 14, 0, 3, COBBLE, "south")
    window(b, 12, 2, 4, "north", height=2, flowers=(TRIM.trapdoor, ["potted_poppy"]))
    window(b, 16, 2, 4, "north", height=2, flowers=(TRIM.trapdoor, ["potted_dandelion"]))
    window(b, 17, 2, 5, "east", width=2, height=2, shutters=TRIM.trapdoor, sill=TRIM)
    window(b, 17, 2, 8, "east", width=2, height=2, shutters=TRIM.trapdoor, sill=TRIM)
    window(b, 14, 2, 10, "south", height=2, sill=TRIM)
    # The council table with eight seats, bookshelves along the back, lights on a tie beam
    for x in (13, 14, 15):
        slab(b, x, 1, 7, TRIM, top=True)
        stairs(b, x, 1, 6, TRIM, "north")
        stairs(b, x, 1, 8, TRIM, "south")
    stairs(b, 12, 1, 7, TRIM, "west")
    stairs(b, 16, 1, 7, TRIM, "east")
    for x in (12, 13, 15, 16):
        for y in (1, 2):
            b.set(x, y, 9, "bookshelf")
    b.set(13, 3, 9, "potted_azure_bluet")
    b.set(15, 3, 9, "potted_oxeye_daisy")
    b.set(16, 3, 7, f"{BANNER}_wall_banner", facing="west")
    for x in range(12, 17):
        log(b, x, 5, 7, FRAME, axis="x")
    lantern(b, 13, 4, 7, hanging=True)
    lantern(b, 15, 4, 7, hanging=True)
    # Its roof, a block lower than the hall's, running east from the hall's gable
    gable_roof(b, 13, 18, 3, 11, 5, ROOF, axis="x", gable=INFILL, gable_at=(17, 17), eave_trim=TRIM)
    log(b, 17, 6, 7, FRAME)
    pane(b, 17, 7, 7)
    for x in (13, 15):  # beam ends out through the soffit over the door, lanterns hanging from them
        log(b, x, 4, 3, FRAME, axis="z")
        lantern(b, x, 3, 3, hanging=True)
    # A bench before it
    stairs(b, 16, 0, 2, TRIM, "south")
    stairs(b, 17, 0, 2, TRIM, "south")


def village_hall_2():
    """19 x 13 x 13: Village Hall I with a meeting room on the east."""
    b = village_hall().grow(19, 13, 13)
    meeting_room(b)
    b.fill_air()
    return b


def bell_tower(b):
    """Hall III's tower, walls x 18-22, z 5-9, up to a belfry and a steep cap."""
    x0, x1, z0, z1 = 18, 22, 5, 9
    top = 12  # the cornice
    for y in range(1, top):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                corner = x in (x0, x1) and z in (z0, z1)
                b.set(x, y, z, ("polished_andesite" if corner else resolve(BRICK_WALL_MIX, x, y, z)) if edge else "air")
    box(b, x0, 0, z0, x1, 0, z1, FOUNDATION_MIX)
    b.set(20, 0, 7, "polished_andesite")
    # A stepped footing round it (not against the meeting room)
    for x in range(x0, x1 + 1):
        stairs(b, x, 0, z0 - 1, STONE_BRICK, "south")
        stairs(b, x, 0, z1 + 1, STONE_BRICK, "north")
    for z in range(z0, z1 + 1):
        stairs(b, x1 + 1, 0, z, STONE_BRICK, "west")
    # The door to the street, slit windows up the front and the east
    door(b, 20, 1, z0, "spruce_door", "south")
    b.set(20, 3, z0, "chiseled_stone_bricks")
    b.set(20, 0, z0 - 1, "polished_andesite")
    stairs(b, 20, 0, z0 - 2, STONE_BRICK, "south")
    for y in (5, 6):
        pane(b, 20, y, z0)
        pane(b, x1, y, 7)
    # The ladder up the back wall to the belfry floor
    for y in range(1, top + 1):
        b.set(20, y, z1 - 1, "ladder", facing="north", waterlogged=False)
    # The clock faces (front and east): a white dial, hands at twelve and three
    for (fx, fz, out) in ((20, z0, "north"), (x1, 7, "east")):
        for d in (-1, 0, 1):
            for dy in (-1, 0, 1):
                x, z = (fx + d, fz) if out == "north" else (fx, fz + d)
                b.set(x, 9 + dy, z, "white_concrete")
        b.set(fx, 9, fz, "polished_blackstone")
        hx, hz = step(fx, fz, out)
        b.set(hx, 10, hz, "polished_blackstone_button", face="wall", facing=out, powered=False)
        rx, rz = step(*((fx + 1, fz) if out == "north" else (fx, fz + 1)), out)
        b.set(rx, 9, rz, "polished_blackstone_button", face="wall", facing=out, powered=False)
    # The cornice: a ring of chiseled stone, upside-down stairs out over the walls
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                b.set(x, top, z, "chiseled_stone_bricks")
            elif b.get(x, top, z) is None or "ladder" not in b.get(x, top, z)[0]:
                b.set(x, top, z, "spruce_planks")
    for x in range(x0, x1 + 1):
        stairs(b, x, top, z0 - 1, STONE_BRICK, "south", top=True)
        stairs(b, x, top, z1 + 1, STONE_BRICK, "north", top=True)
    for z in range(z0 - 1, z1 + 2):
        stairs(b, x1 + 1, top, z, STONE_BRICK, "west", top=True)
    # The belfry: corner piers and an open arch on every side, the great bell in the middle
    for y in (top + 1, top + 2):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) and z in (z0, z1):
                    b.set(x, y, z, "polished_andesite")
                elif x in (x0, x1) or z in (z0, z1):
                    b.set(x, y, z, "air" if (x in (19, 20, 21) and z in (z0, z1)) or (z in (6, 7, 8) and x in (x0, x1))
                          else resolve(BRICK_WALL_MIX, x, y, z))
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            b.set(x, top + 3, z, "stone_bricks")
    b.set(20, top + 2, 7, "bell", attachment="ceiling", facing="north", powered=False)
    for x in (19, 21):  # the arches' heads
        b.set(x, top + 2, z0, "stone_brick_stairs", facing="west" if x == 19 else "east", half="top", shape="straight",
              waterlogged=False)
        b.set(x, top + 2, z1, "stone_brick_stairs", facing="west" if x == 19 else "east", half="top", shape="straight",
              waterlogged=False)
    for z in (6, 8):
        for x in (x0, x1):
            b.set(x, top + 2, z, "stone_brick_stairs", facing="north" if z == 6 else "south", half="top", shape="straight",
                  waterlogged=False)
    # The steep hipped cap and a finial
    peak = hip_roof(b, x0 - 1, x1 + 1, z0 - 1, z1 + 1, top + 4, ROOF, steep=True)
    fence(b, 20, peak + 1, 7, "spruce_fence")


def village_hall_3():
    """24 x 24 x 13: Village Hall II with a bell-and-clock tower at the east end of the meeting room."""
    b = village_hall_2().grow(24, 24, 13)
    # The meeting room's roof dies into the tower's west wall
    for y in range(5, 13):
        for z in range(5, 10):
            b.blocks.pop((18, y, z), None)
    # The meeting room's east windows would look into the tower: walled up, their shutters and sills off
    b.clear(18, 1, 4, 18, 4, 10)
    for z in (5, 6, 8, 9):
        for y in (2, 3):
            b.set(17, y, z, INFILL)
    bell_tower(b)
    for x in (19, 21):
        b.set(x, 7, 4, f"{BANNER}_wall_banner", facing="north")
    lamp_post(b, 23, 0, 3, "spruce_fence", height=2)
    lamp_post(b, 18, 0, 2, "spruce_fence", height=2)
    b.fill_air()
    return b
