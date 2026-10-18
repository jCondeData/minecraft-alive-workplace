"""
The Village Hall build: a home for the Village Hall block, in three levels that replace each other on the same spot
(front at z = 0), like the homes. Every level keeps the hall block at (6, 1, 10), facing the door, and the bell under
the porch at (6, 4, 0): an upgrade never moves either, so the village never loses its hall while the builder works.

Design (after the Minecraft Architect review, and redrawn after the owner built it in game and found the windows and
logs out of place):
- I, 13 x 13 x 13: a timber hall (x 1-11, z 3-11, walls four high) on a cobbled plinth with a flared stair skirt round
  it. A spruce frame: vertical posts at the corners and at even bays (front and back x 1, 4, 8, 11; sides z 3, 6, 8,
  11), one horizontal top plate at y 5 all round (drawn after the roof, so the gables never cut it). Every window is
  the same 2 x 2 of panes at y 2-3, filling a 2-wide bay between two posts: flower boxes on the street front, sills on
  the sides and back. The middle bays stay calm: the door on the front, plain infill behind the hall block at the back,
  a 1-wide panel between the side windows. The gable ends sit on the plate with two studs carrying the side posts up to
  the roof and a single attic pane between them. A deepslate-tile gable (ridge along x, a 1-block overhang with a
  soffit) and a gabled porch on two log posts over the door, its ridge running up into the main roof, the village bell
  hung from its front beam, a lantern either side of the door and a banner on each porch post. A flower-boxed window
  fills each front bay either side of the door. Inside: the
  Village Hall at the head of a red runner, two banners behind it, four benches facing it, lanterns on two tie beams
  over the side posts.
- II, 19 x 13 x 13: a meeting room grows out of the east wall (x 11-17, z 4-10, two blocks shallower, so its ridge sits
  a block lower), framed the same way (posts at x 14 and 17, z 7), its 2 x 2 windows in the same bays front and back
  and on the east, on the same skirted plinth. The hall's middle side bay opens as the doorway between them; its side
  windows are walled up. Inside: a council table with eight seats, low bookshelves under the back windows.
- III, 24 x 24 x 13: a stone bell-and-clock tower (x 18-22, z 5-9) at the end of the meeting room: quoined corners on
  a stepped stone footing, a door to the street between two lamp posts, a clock face on the front and the east, a
  cornice, an open belfry with the great bell, a steep hipped deepslate cap. Banners on its front.

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


FRONT_POSTS = (1, 4, 8, 11)  # front and back: 2-wide window bays either side of the 3-wide door bay
SIDE_POSTS = (3, 6, 8, 11)  # the sides: 2-wide window bays either side of a 1-wide panel


def hall_body(b):
    """The hall's walls x 1-11, z 3-11 (four high) with its frame, windows, door and the room inside."""
    plinth(b, 1, 3, 11, 11, STONE_MIX, floor="spruce_planks")
    skirt(b, 1, 3, 11, 11, COBBLE)
    walls(b, 1, 3, 11, 11, 1, 4, INFILL)
    posts(b, [(x, z) for x in FRONT_POSTS for z in (3, 11)] + [(x, z) for x in (1, 11) for z in SIDE_POSTS], 1, 4, FRAME)
    # The door in the middle bay of the front, a step before it
    door(b, 6, 1, 3, "spruce_door", "south")
    # Every window the same: 2 x 2 panes at y 2-3 filling a 2-wide bay. Flower boxes on both front bays either side of
    # the door (the owner, 2026-10-15: a notice board with blank signs there read as a boarded-up window), sills on the
    # sides and the back.
    window(b, 2, 2, 3, "north", width=2, height=2, flowers=(TRIM.trapdoor, ["potted_allium", "potted_cornflower"]))
    window(b, 9, 2, 3, "north", width=2, height=2, flowers=(TRIM.trapdoor, ["potted_cornflower", "potted_allium"]))
    for z in (4, 9):
        window(b, 1, 2, z + 1, "west", width=2, height=2, sill=TRIM)  # runs north from z + 1
        window(b, 11, 2, z, "east", width=2, height=2, sill=TRIM)
    for x in (2, 9):
        window(b, x + 1, 2, 11, "south", width=2, height=2, sill=TRIM)  # runs west from x + 1
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
    for z in (6, 8):  # tie beams over the side posts, for the lights
        for x in range(2, 11):
            log(b, x, 5, z, FRAME, axis="x")
        lantern(b, 3, 4, z, hanging=True)
        lantern(b, 9, 4, z, hanging=True)
    # Furnished (37.5): the rows are benches facing the hall block, pots hanging over the runner
    for z in (5, 7):
        for x in (2, 3, 4, 8, 9, 10):
            furnish(b, "bench", x, 1, z, "south", wood="spruce")
    for x in (5, 7):
        hanging_pot(b, x, 4, 8, "potted_blue_orchid", "spruce")


def hall_roof(b):
    """The main gable (ridge along x at y 10), the top plate over it, and the gable ends: two studs carrying the side
    posts up to the roof, an attic pane between them."""
    gable_roof(b, 0, 12, 2, 12, 5, ROOF, axis="x", gable=INFILL, gable_at=(1, 11), eave_trim=TRIM)
    beam_ring(b, 1, 3, 11, 11, 5, FRAME)
    for x in (1, 11):
        for z in (6, 8):
            for y in (6, 7, 8):
                log(b, x, y, z, FRAME)
        pane(b, x, 7, 7)


def hall_porch(b):
    """The porch before the door: log posts on stone, a stone deck, a wide step, beams carrying a cross gable whose ridge
    runs into the main roof. The bell hangs from the front beam; lanterns hang either side of the door; a banner hangs
    on the outer face of each post."""
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
    b.set(3, 3, 0, f"{BANNER}_wall_banner", facing="west")
    b.set(9, 3, 0, f"{BANNER}_wall_banner", facing="east")



def village_hall():
    """13 x 13 x 13: the timber hall, its porch with the bell,."""
    b = Build(13, 13, 13)
    hall_body(b)
    hall_roof(b)
    hall_porch(b)
    b.fill_air()
    return b


def meeting_room(b):
    """Hall II's meeting room, x 11-17, z 4-10, through the hall's east wall, framed like the hall."""
    # The hall's east sills come off and its windows are walled up; the middle bay (between the posts at z 6 and 8)
    # opens as the doorway
    b.clear(12, 1, 4, 12, 4, 10)
    for z in (4, 5, 9, 10):
        for y in (2, 3):
            b.set(11, y, z, INFILL)
    for y in (1, 2):
        b.set(11, y, 7, "air")
    b.set(11, 0, 7, "spruce_planks")
    plinth(b, 11, 4, 17, 10, STONE_MIX, floor="spruce_planks")
    for x in range(12, 18):
        stairs(b, x, 0, 3, COBBLE, "south")
        stairs(b, x, 0, 11, COBBLE, "north")
    for z in range(4, 11):
        stairs(b, 18, 0, z, COBBLE, "west")
    for y in range(1, 5):
        for x in range(12, 18):
            b.set(x, y, 4, INFILL)
            b.set(x, y, 10, INFILL)
        for z in range(5, 10):
            b.set(17, y, z, INFILL)
    posts(b, [(14, 4), (17, 4), (14, 10), (17, 10), (17, 7)], 1, 4, FRAME)
    # The same 2 x 2 windows in its bays: flower boxes on the front, sills on the east and the back
    window(b, 12, 2, 4, "north", width=2, height=2, flowers=(TRIM.trapdoor, ["potted_poppy", "potted_dandelion"]))
    window(b, 15, 2, 4, "north", width=2, height=2, flowers=(TRIM.trapdoor, ["potted_dandelion", "potted_poppy"]))
    window(b, 17, 2, 5, "east", width=2, height=2, sill=TRIM)
    window(b, 17, 2, 8, "east", width=2, height=2, sill=TRIM)
    for x in (13, 16):
        window(b, x, 2, 10, "south", width=2, height=2, sill=TRIM)  # runs west from x
    # The council table with eight seats, low bookshelves under the back windows, lights on a tie beam
    for x in (13, 14, 15):
        slab(b, x, 1, 7, TRIM, top=True)
        stairs(b, x, 1, 6, TRIM, "north")
        stairs(b, x, 1, 8, TRIM, "south")
    stairs(b, 12, 1, 7, TRIM, "west")
    stairs(b, 16, 1, 7, TRIM, "east")
    for x in (12, 13, 15, 16):
        b.set(x, 1, 9, "bookshelf")
    b.set(13, 2, 9, "potted_azure_bluet")
    b.set(15, 2, 9, "potted_oxeye_daisy")
    b.set(16, 3, 7, f"{BANNER}_wall_banner", facing="west")
    # Furnished (37.5): the council table and its eight chairs
    for x in (13, 14, 15):
        furnish(b, "table", x, 1, 7, "north", wood="spruce")
        furnish(b, "chair", x, 1, 6, "south", wood="spruce")
        furnish(b, "chair", x, 1, 8, "north", wood="spruce")
    furnish(b, "chair", 12, 1, 7, "east", wood="spruce")
    furnish(b, "chair", 16, 1, 7, "west", wood="spruce")
    for x in range(12, 17):
        log(b, x, 5, 7, FRAME, axis="x")
    lantern(b, 13, 4, 7, hanging=True)
    lantern(b, 15, 4, 7, hanging=True)
    # Its roof, a block lower than the hall's, running east from the hall's gable; the top plate after it, so the gable
    # never cuts it; a king post up the gable over the east post
    gable_roof(b, 13, 18, 3, 11, 5, ROOF, axis="x", gable=INFILL, gable_at=(17, 17), eave_trim=TRIM)
    for x in range(12, 18):
        log(b, x, 5, 4, FRAME, axis="x")
        log(b, x, 5, 10, FRAME, axis="x")
    for z in range(5, 10):
        log(b, 17, 5, z, FRAME, axis="z")
    for y in (6, 7, 8):
        log(b, 17, y, 7, FRAME)


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
    # (all four sides: on the west it runs out over the meeting room's roof, as the cap's eave does above it)
    for x in range(x0, x1 + 1):
        stairs(b, x, top, z0 - 1, STONE_BRICK, "south", top=True)
        stairs(b, x, top, z1 + 1, STONE_BRICK, "north", top=True)
    for z in range(z0 - 1, z1 + 2):
        stairs(b, x1 + 1, top, z, STONE_BRICK, "west", top=True)
        stairs(b, x0 - 1, top, z, STONE_BRICK, "east", top=True)
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
    b.clear(18, 0, 4, 18, 4, 10)
    for z in (5, 6, 8, 9):
        for y in (2, 3):
            b.set(17, y, z, INFILL)
    bell_tower(b)
    for x in (19, 21):
        b.set(x, 7, 4, f"{BANNER}_wall_banner", facing="north")
    for x in (18, 22):  # a lamp post either side of the tower's door
        lamp_post(b, x, 0, 3, "spruce_fence", height=2)
    # The tower rises out of the meeting room's roof: under its cornice and its cap's eave on the west there's open air
    # against the tower wall, not a slot to close. Closed, it stood out as a stone wall a block west of the tower,
    # its ends jutting past the tower's front and back and its belfry arch walled up (holes.ALLOW, TOWER_EAVE).
    b.open_slots = {(17, z) for z in range(4, 11)}  # the column just west of the tower's walls (x 18-22, z 5-9)
    b.fill_air()
    return b
