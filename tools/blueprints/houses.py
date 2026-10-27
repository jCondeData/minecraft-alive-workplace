"""
Houses in tiers: homes that grow with the village. Each tier keeps the one before it and adds beds. Every kind of home
has its own shell (ROADMAP 26.11, the owner: "nothing should look identical"):

- Stone House: a stone croft — rubble walls, crow-stepped gables standing proud of a slate roof, a chimney breast up
  the west gable, a woodpile under a lean-to. II rises a full storey in stone (no timber), III adds a crow-gabled stone
  wing at the back.
- Artisan's House: a narrow workshop house under a red tile gambrel roof, its upper floor clad in weatherboards.
- Townhouse: a plastered town house with a slate mansard roof, a balcony over the shop and a shop awning.
- Manor: a hipped-roof stone mansion behind a forecourt.
(The Starter Cottage is the timber-framed one and the Terrace a brick row: starter.py.)
"""
from kit import *
from decor import fountain_basin

STONE_HOUSE_WALL = STONE_MIX
STONE_HOUSE_QUOIN = "stone_bricks"
STONE_HOUSE_FRAME = "dark_oak_log"
STONE_HOUSE_ROOF = DEEPSLATE_TILE
STONE_HOUSE_UPPER = Mix((6, "cobblestone"), (4, "stone"), (2, "andesite"), (1, "mossy_cobblestone"), seed=25)
STONE_HOUSE_STEP = "stone_bricks"  # the eaves course


def roof_top(b, x, z, y0):
    """The highest roof block (a stair or a slab) in column (x, z) from y0 up, or None."""
    ys = [y for (bx, y, bz), (n, _) in b.blocks.items() if bx == x and bz == z and y >= y0
          and (n.endswith("_stairs") or n.endswith("_slab"))]
    return max(ys) if ys else None


def crow_steps(b, axis, at, lo, hi, y0, name, cap=STONE_BRICK):
    """Turns the gable wall at {@code at} (an x when axis is "x", the wall running along z from lo to hi; a z when axis
    is "z") into a crow-stepped gable: the wall rises above the roof slope in pairs of steps, the middle one highest,
    each step capped with a slab of {@code cap}. Draw the roof first, its verge flush with the wall; y0 is its lowest
    row. Panes and logs (the top plate) in the gable stay. Returns the top y of the middle step."""
    def cell(v, y):
        return (at, y, v) if axis == "x" else (v, y, at)

    def top(v):
        x, _, z = cell(v, 0)
        return roof_top(b, x, z, y0)

    mid = (lo + hi) // 2
    heights = {mid: (top(mid) or y0) + 1}
    k = 1
    while mid - k >= lo:
        for d in (k, k + 1):  # a pair of steps a block over the roof beside the inner one
            for v in (mid - d, mid + d):
                if lo <= v <= hi:
                    heights[v] = (top(mid - k) or y0) + 1
        k += 2
    for v, h in heights.items():
        for y in range(y0, h + 1):
            p = cell(v, y)
            old = b.blocks.get(p)
            if old and ("pane" in old[0] or "_log" in old[0]):
                continue  # windows and the top plate stay
            b.set(*p, resolve(name, *p))
        if h + 1 < b.h:
            slab(b, *cell(v, h + 1), cap)
    return heights[mid]


def slate_roof(b, y, gable, face):
    """The Stone House's roof (x 1-9 between its crow-stepped gables, eaves z 0-8, its lowest row at {@code y}) with a
    wall dormer over the door bay: the front wall rises through the eave into a little gable of {@code face} with a
    window in it (x 5, y + 1), under its own roof, whose slopes die into the main one and whose ridge runs back to it."""
    roofs(b,
          lambda t: gable_roof(t, 1, 9, 0, 8, y, STONE_HOUSE_ROOF, axis="x", gable=gable, gable_at=(1, 9), ridge=STONE_HOUSE_ROOF),
          lambda t: gable_roof(t, 3, 7, 0, 2, y + 1, STONE_HOUSE_ROOF, axis="z", gable=face, gable_at=(1,), ridge=STONE_HOUSE_ROOF))
    pane(b, 5, y + 1, 1)


def stone_house_woodpile(b):
    """Split logs stacked against the east wall behind the side window, under a slab lean-to."""
    for z in (6, 7):
        for y in (1, 2):
            log(b, 10, y, z, "oak_log", axis="x")
        slab(b, 10, 3, z, SPRUCE)


def stone_house_ground_floor(b):
    """The ground floor every tier shares (walls x 1-9, z 1-7, four high): a cobbled plinth, rubble walls with stone
    brick quoins, a dark oak floor plate, shuttered windows with flower boxes at the front, the door under a little
    hood, the floor above and a ladder up to it, a woodpile outside; inside a bed, a kitchen corner and a table."""
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
    # The table stands by the west window, out of the door's way (B260: the door opens onto a clear walk to the ladder)
    fence(b, 3, 1, 4, "dark_oak_fence")
    b.set(3, 2, 4, "dark_oak_pressure_plate", powered=False)
    stairs(b, 3, 1, 3, SPRUCE, "north")
    box(b, 4, 1, 5, 5, 1, 6, "brown_carpet")
    for y in range(1, 6):
        b.set(8, y, 3, "ladder", facing="west", waterlogged=False)
    # Furnished (37.4): a second seat at the table, a nightstand and a lamp by the bed, a shelf of cups over the rug, a
    # clock against the east wall beside the nightstand (B260: not by the door, where a tall clock blocks the way in), a
    # picture on the front wall between the door and the window, blinds at the front and back windows
    stairs(b, 3, 1, 5, SPRUCE, "south")
    nightstand(b, 8, 1, 6, "north", "dark_oak")
    floor_lamp(b, 6, 1, 6, "dark_oak")
    shelf(b, 4, 2, 6, "north", "dark_oak")
    clock(b, 8, 1, 5, "west", "dark_oak")
    picture(b, 6, 3, 2, "south", "red", "dark_oak")
    for x in (2, 3, 7, 8):
        blinds(b, x, 3, 2, "south", "dark_oak")
    for x in (3, 7):
        blinds(b, x, 3, 6, "north", "dark_oak")
    stone_house_woodpile(b)


def stone_house():
    """11 x 11 x 9: a stone croft — rubble walls with stone brick quoins on a cobbled plinth, shuttered windows with
    flower boxes, a slate roof between crow-stepped gables, a chimney breast up the west gable, a woodpile on the east —
    a bed downstairs and one in the attic."""
    b = Build(11, 11, 9)
    stone_house_ground_floor(b)
    # The attic bedroom (B260): the bed's head against the west gable under its window, a nightstand by it, a chest at
    # its foot, a rug, and a lantern hung from the ridge
    b.bed(3, 6, 4, "light_blue", facing="west")
    nightstand(b, 2, 6, 5, "east", "dark_oak")
    b.set(4, 6, 4, "chest", facing="east", type="single", waterlogged=False)
    box(b, 5, 6, 4, 7, 6, 4, "light_gray_carpet")  # a runner under the ridge (the slopes leave no headroom over a rug)
    lantern(b, 5, 8, 4, hanging=True)
    slate_roof(b, 5, STONE_HOUSE_WALL, STONE_HOUSE_WALL)  # the dormer lights the attic bedroom
    for x in (1, 9):
        crow_steps(b, "x", x, 1, 7, 5, STONE_HOUSE_WALL)
        pane(b, x, 7, 4)
    chimney(b, 0, 4, 0, 9, STONE_MIX)
    b.fill_air()
    return b


def stone_house_upper_floor(b):
    """Stone House II's upper storey: rubble walls like the ground floor's (no timber), stone brick quoins and an eaves
    course, deep windows with stone sills front and back, two beds."""
    walls(b, 1, 1, 9, 7, 6, 8, STONE_HOUSE_UPPER)
    posts(b, [(1, 1), (9, 1), (1, 7), (9, 7)], 6, 8, STONE_HOUSE_QUOIN)
    walls(b, 1, 1, 9, 7, 9, 9, STONE_HOUSE_STEP)
    for x in (2, 8):
        window(b, x, 6, 1, "north", height=2, sill=STONE_BRICK)
    window(b, 5, 6, 1, "north", height=2, shutters="dark_oak_trapdoor", sill=STONE_BRICK)
    for x in (2, 5, 8):
        window(b, x, 6, 7, "south", height=2, sill=STONE_BRICK)
    for x in (1, 9):
        window(b, x, 6, 2, "west" if x == 1 else "east", height=2, sill=STONE_BRICK)
    # Upstairs (B260): two beds with their heads to the back wall between its windows, a nightstand by each, a chest
    # at the foot of one, a bookshelf, a rug in the middle and a couch under the front window; the ladder comes up at
    # (8, 6, 3) into the clear
    b.bed(3, 6, 5, "light_blue", facing="south")
    b.bed(7, 6, 5, "white", facing="south")
    b.set(7, 6, 4, "chest", facing="north", type="single", waterlogged=False)
    b.set(8, 6, 6, "bookshelf")
    b.set(8, 7, 6, "potted_fern")
    box(b, 4, 6, 3, 6, 6, 4, "light_gray_carpet")
    # Furnished (37.4): a nightstand by each bed, a couch under the front window, a picture on the east wall
    nightstand(b, 4, 6, 6, "north", "dark_oak")
    nightstand(b, 6, 6, 6, "north", "dark_oak")
    couch(b, 3, 6, 2, "south", "dark_oak", "light_gray", length=2, arms=False)
    picture(b, 8, 7, 4, "west", "light_gray", "dark_oak")
    for x in range(2, 9):  # a tie beam under the ridge to hang the lamp from (and to stand on)
        log(b, x, 9, 4, STONE_HOUSE_FRAME, axis="x")
    lantern(b, 5, 8, 4, hanging=True)


def stone_house_2():
    """Upgrade of the Stone House (same origin and front): the ground floor stays, the roof comes off, and the walls
    rise a full storey in stone with two bedrooms, under a new slate roof between taller crow-stepped gables, a chimney
    stack on each. 11 x 16 x 9."""
    b = Build(11, 16, 9)
    stone_house_ground_floor(b)
    stone_house_upper_floor(b)
    slate_roof(b, 9, STONE_HOUSE_UPPER, STONE_HOUSE_UPPER)  # the dormer over the middle bay, above the upstairs window
    for x in (1, 9):
        crow_steps(b, "x", x, 1, 7, 9, STONE_HOUSE_UPPER)
        pane(b, x, 11, 4)
    chimney(b, 0, 4, 0, 14, STONE_MIX)
    b.fill_air()
    return b


def stone_house_3():
    """Upgrade of Stone House II (same origin and front): a stone wing at the back, through a doorway in the back wall,
    with two more beds under its own lower roof, its back gable crow-stepped like the house's. 11 x 16 x 14."""
    b = stone_house_2().grow(11, 16, 14)
    # The back windows' sills come off (they're inside now) and a doorway opens between them
    for x in (3, 7):
        b.set(x, 1, 8, "air")
    for y in (1, 2):
        b.set(5, y, 7, "air")
    for x in (4, 5):  # the rug's back row comes up: on a carpet by the doorway's lintel a villager hits its head (B173)
        b.set(x, 1, 6, "air")
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
    gable_roof(b, 1, 9, 8, 12, 5, STONE_HOUSE_ROOF, axis="z", gable=STONE_HOUSE_WALL, gable_at=(12, 12), ridge=STONE_HOUSE_ROOF)
    b.blocks.update(keep)
    for x in range(3, 8):  # the top plate across the back gable (the roof's gable filled over it)
        log(b, x, 5, 12, STONE_HOUSE_FRAME, axis="x")
    crow_steps(b, "z", 12, 2, 8, 5, STONE_HOUSE_WALL)
    pane(b, 5, 7, 12)  # a little window in the wing's gable
    # Inside: two beds, a chest between them, a rug
    b.bed(3, 1, 10, "green", facing="south")  # heads to the wing's back wall (B260)
    b.bed(7, 1, 10, "lime", facing="south")
    b.set(5, 1, 11, "chest", facing="north", type="single", waterlogged=False)
    box(b, 4, 1, 9, 6, 1, 10, "green_carpet")
    b.fill_air()
    return b


# --- The settlers' camp: what a Settler's Wagon sets up ------------------------------------------------------------
def settlers_camp():
    """11 x 5 x 9: a settlers' camp in the wild — a covered wagon on four wheels with the supply chest on its open
    tailboard (steps up to it), a
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
    # The load: two hay bales, a pumpkin and a melon under the cover, the supply chest (a loot table fills it) on the
    # open tailboard past the canvas, its lid to the tail (B424: in the middle, between the hay and the pumpkin, no
    # player could reach it; B590: under the cover's last arch, the wool on top kept its lid shut)
    b.set(4, 2, 4, "hay_block", axis="x")
    b.set(5, 2, 4, "hay_block", axis="x")
    b.set(6, 2, 4, "pumpkin")
    b.set(7, 2, 4, "melon")
    b.set_nbt(8, 2, 4, Compound({"LootTable": String("aliveworkplace:chests/settlers_wagon"), "id": String("minecraft:chest")}))
    b.set(8, 2, 4, "chest", facing="east", type="single", waterlogged=False)
    # Steps up to the tail: a crate with a stair on it and a stair before it, ending level with the wagon's bed
    b.set(9, 0, 4, "stripped_spruce_wood", axis="y")
    stairs(b, 9, 1, 4, SPRUCE, "west")
    stairs(b, 10, 0, 4, SPRUCE, "west")
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
ARTISAN_FRAME = "dark_oak_log"
ARTISAN_BOARDS = "stripped_birch_log"  # weatherboards: logs laid along the wall
ARTISAN_ROOF = BRICK  # red clay tiles


def boards(b, x0, z0, x1, z1, y0, y1, name=ARTISAN_BOARDS):
    """Weatherboarded walls round (x0, z0)-(x1, z1) from y0 to y1: logs laid along each wall, so the boards run level."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                log(b, x, y, z, name, axis="x")
        for z in range(z0 + 1, z1):
            for x in (x0, x1):
                log(b, x, y, z, name, axis="z")


def gambrel_roof(b, x0, x1, z0, z1, y, fam, steep_rows=2, gable_at=(), gable=None, gable_axis="x"):
    """A gambrel (barn) roof over (x0, z0)-(x1, z1) with its ridge along z (overhang included), its lowest row at y: the
    first {@code steep_rows} rows in from each eave rise two blocks a step (a stair over a full block of the roof), the
    rest one, and a full block caps an odd span. The ends at z {@code gable_at} are closed up to the roof with
    {@code gable} (logs laid along {@code gable_axis} if a log). Returns the top y."""
    full = fam.full
    heights = []
    h = y
    i = 0
    while x0 + i <= x1 - i:
        if i > 0:
            h += 2 if i <= steep_rows else 1
        lo, hi = x0 + i, x1 - i
        for z in range(z0, z1 + 1):
            if lo == hi:
                b.set(lo, heights[-1], z, full)  # the ridge, flush with the stairs either side
            else:
                for x, face in ((lo, "east"), (hi, "west")):
                    stairs(b, x, h, z, fam, face)
                    if i > 0:
                        for yy in range(heights[-1] + 1, h):
                            b.set(x, yy, z, full)
        heights.append(h if lo != hi else heights[-1])
        i += 1
    if gable:
        for z in gable_at:
            for x in range(x0 + 1, x1):
                i = min(x - x0, x1 - x)
                top = heights[i] if x0 + i != x1 - i else heights[i]
                for yy in range(y, top):
                    if b.get(x, yy, z) is None or b.get(x, yy, z)[0] == "minecraft:air":
                        if "_log" in gable:
                            log(b, x, yy, z, gable, axis=gable_axis)
                        else:
                            b.set(x, yy, z, resolve(gable, x, yy, z))
    return max(heights)


def artisans_house_body(b):
    """The Artisan's House every tier shares: a narrow house with its gable to the street (walls x 1-7, z 2-10). The
    workroom below in stone (a workbench, an anvil, tool racks, a chest of stock), the upper floor clad in pale
    weatherboards and jettied a block out over the street on brackets, two beds up there, all under a red tile gambrel
    roof with a loft door and a hoist beam in its street gable."""
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
        stairs(b, x, 4, 1, DARK_OAK, "south", top=True)
    boards(b, 1, 1, 7, 10, 6, 8)
    posts(b, [(1, 1), (7, 1), (1, 10), (7, 10)], 6, 8, ARTISAN_FRAME)
    beam_ring(b, 1, 1, 7, 10, 9, ARTISAN_FRAME)
    for x in (2, 6):
        window(b, x, 6, 1, "north", height=2, shutters="dark_oak_trapdoor",
               flowers=("dark_oak_trapdoor", ["potted_red_tulip"] if x == 2 else ["potted_cornflower"]))
    window(b, 4, 7, 10, "south", height=1)
    for x in (3, 5):  # the workroom's back windows (inside the wing from II on)
        glass_column(b, x, 2, 3, 10)
    for z in (5, 9):
        window(b, 1, 6, z, "west", height=2, shutters="dark_oak_trapdoor")
        window(b, 7, 6, z, "east", height=2, shutters="dark_oak_trapdoor")
    # The roof: a red tile gambrel, its gable to the street, weatherboarded gables; a loft door in the street gable
    # under a hoist beam (a lamp hangs from it), a window in the back one
    gambrel_roof(b, 0, 8, 0, 11, 9, ARTISAN_ROOF, steep_rows=2, gable_at=(1, 10), gable=ARTISAN_BOARDS)
    for z in (1, 10):
        for x in (2, 6):
            pane(b, x, 11, z)
    door(b, 4, 10, 1, "dark_oak_door", "south")
    log(b, 4, 13, 0, ARTISAN_FRAME, axis="z")
    lantern(b, 4, 12, 0, hanging=True)
    pane(b, 4, 11, 10)
    chimney(b, 8, 6, 0, 13, BRICK_WALL_MIX)
    # In the workroom: a workbench by the window, an anvil, a grindstone-free tool wall of item racks (barrels), stock
    b.set(2, 1, 3, "crafting_table")
    b.set(2, 1, 5, "anvil", facing="east")
    b.set(2, 1, 6, "barrel", facing="up", open=False)
    b.set(2, 1, 7, "chest", facing="east", type="single", waterlogged=False)
    b.set(6, 1, 3, "barrel", facing="up", open=False)
    b.set(6, 2, 3, "potted_fern")
    fence(b, 4, 1, 6, "spruce_fence")
    b.set(4, 2, 6, "spruce_pressure_plate", powered=False)
    chair(b, 4, 1, 7, SPRUCE, "north")
    box(b, 3, 1, 4, 5, 1, 5, "brown_carpet")
    for y in range(1, 6):
        b.set(6, y, 9, "ladder", facing="north", waterlogged=False)  # the top rung in the hatch through the floor
    lantern(b, 4, 4, 4, hanging=True)
    # Upstairs: two beds under the roof, a chest between them, a rug, a lamp
    b.bed(2, 6, 3, "orange", facing="north")
    b.bed(6, 6, 3, "orange", facing="north")
    b.set(4, 6, 2, "chest", facing="south", type="single", waterlogged=False)
    box(b, 3, 6, 5, 5, 6, 7, "orange_carpet")
    b.set(2, 6, 8, "bookshelf")
    lantern(b, 2, 7, 8)
    # Furnished (37.4): a nightstand and a lamp at the beds' heads, a couch under the back window
    nightstand(b, 3, 6, 2, "south", "spruce")
    floor_lamp(b, 5, 6, 2, "spruce")
    couch(b, 3, 6, 9, "north", "spruce", "orange", length=2, arms=False)
    # Out front: a lamp on the wall post and a barrel by the door
    b.set(5, 1, 1, "barrel", facing="up", open=False)
    lantern(b, 5, 2, 1)


def artisans_house():
    """9 x 15 x 12: the Artisan's House (grade I) — a narrow house under a red tile gambrel roof, its gable to the
    street with a loft door and a hoist beam, a stone workroom below and two beds above in a weatherboarded upper floor
    jettied out on brackets, a brick chimney up the side."""
    b = Build(9, 15, 12)
    artisans_house_body(b)
    b.fill_air()
    return b


def artisans_house_2():
    """Upgrade of the Artisan's House (same origin and front): a stone workshop wing at the back, through a doorway in
    the back wall, with a long bench, a loom, a smoker-free stove of a campfire-less brick hearth and a third bed in a
    corner, under its own lower red tile gambrel. 9 x 15 x 18."""
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
    gambrel_roof(b, 1, 7, 11, 17, 4, ARTISAN_ROOF, steep_rows=1, gable_at=(16,), gable=ARTISAN_BOARDS)
    b.blocks.update(keep)
    window(b, 2, 2, 14, "west", height=1, shutters="dark_oak_trapdoor", sill=STONE_BRICK)
    window(b, 6, 2, 14, "east", height=1, shutters="dark_oak_trapdoor", sill=STONE_BRICK)
    window(b, 4, 2, 16, "south", height=1, sill=STONE_BRICK)
    lantern(b, 4, 3, 13, hanging=True)
    # The workshop: a long bench, a loom, a stonecutter-free saw horse, the third bed in the corner
    for z in (11, 12):
        stairs(b, 3, 1, z, SPRUCE, "east", top=True)
    b.set(5, 1, 11, "loom", facing="west")
    b.set(5, 1, 12, "barrel", facing="up", open=False)
    b.bed(3, 1, 14, "brown", facing="south")  # its head to the back wall (B260)
    b.set(5, 1, 15, "chest", facing="west", type="single", waterlogged=False)
    b.set(4, 1, 15, "brown_carpet")
    b.fill_air()
    return b


TOWNHOUSE_WALL = Mix((6, "stone_bricks"), (3, "polished_andesite"), (1, "cracked_stone_bricks"), seed=31)  # the shop
TOWNHOUSE_INFILL = Mix((8, "smooth_sandstone"), (2, "sandstone"), seed=32)  # warm stucco, like Bath stone
TOWNHOUSE_TRIM = "stone_bricks"
TOWNHOUSE_ROOF = DEEPSLATE_TILE


def townhouse_mansard(b):
    """The Townhouse's slate mansard (x 0-10, z 0-11): a steep lower slope all round (stairs over full blocks) with
    dormer windows in it front and back, and a flat slab deck on top. The loft is inside the steep slope."""
    hip_roof(b, 0, 10, 0, 11, 13, TOWNHOUSE_ROOF, steep=True, rings=3)
    box(b, 3, 18, 3, 7, 18, 8, TOWNHOUSE_ROOF.slab, type="bottom", waterlogged=False)
    for z in (1, 10):
        for x in (3, 7):
            pane(b, x, 14, z)  # dormers in the steep slope, under the stair above them
    for x in (1, 9):
        pane(b, x, 14, 5)
        pane(b, x, 14, 6)


def townhouse_body(b):
    """The Burgher's Townhouse every tier shares: three storeys, its front to the street (walls x 1-9, z 2-10). A
    rusticated stone shop below with a shop window either side of the door; two storeys of warm stucco above with stone
    quoins and string courses, the first jettied over the street with a little iron balcony at each window; bedrooms on
    both; a slate mansard roof with a loft in it."""
    # The shop: a stone plinth, rusticated stone walls with stone brick quoins, shop windows
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
    # The first floor: jettied a block over the street on stone brackets, stucco between stone quoins and string courses
    walls(b, 1, 1, 9, 10, 5, 5, TOWNHOUSE_TRIM)
    box(b, 2, 5, 2, 8, 5, 9, "spruce_planks")
    for x in (1, 4, 6, 9):
        stairs(b, x, 4, 1, STONE_BRICK, "south", top=True)
    walls(b, 1, 1, 9, 10, 6, 8, TOWNHOUSE_INFILL)
    quoins = [(1, 1), (9, 1), (1, 10), (9, 10)]
    posts(b, quoins, 6, 8, TOWNHOUSE_TRIM)
    walls(b, 1, 1, 9, 10, 9, 9, TOWNHOUSE_TRIM)
    box(b, 2, 9, 2, 8, 9, 9, "spruce_planks")
    # The second floor: the same
    walls(b, 1, 1, 9, 10, 10, 12, TOWNHOUSE_INFILL)
    posts(b, quoins, 10, 12, TOWNHOUSE_TRIM)
    walls(b, 1, 1, 9, 10, 13, 13, TOWNHOUSE_TRIM)  # the cornice
    for y in (6, 10):
        for x in (2, 5, 8):
            if y == 6:  # tall windows, each with a little balcony: a slab on the jetty's edge behind iron railings
                window(b, x, y, 1, "north", height=2, lintel=STONE_BRICK)
                slab(b, x, 5, 0, STONE_BRICK, top=True)
                b.set(x, 6, 0, "iron_bars", north=False, south=False, east=False, west=False, waterlogged=False)
            else:
                window(b, x, y, 1, "north", height=2, shutters="dark_oak_trapdoor", sill=STONE_BRICK)
        for x in (2, 8):
            window(b, x, y, 10, "south", height=2, sill=STONE_BRICK)
        for z in (2, 5, 8):
            window(b, 1, y, z, "west", height=2)
            window(b, 9, y, z, "east", height=2)
    # The loft floor and the slate mansard over it
    box(b, 2, 13, 2, 8, 13, 9, "spruce_planks")
    townhouse_mansard(b)
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
    shelf(b, 7, 3, 9, "north", "spruce")  # cups over the stove (37.4)
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
    # Furnished (37.4): a lamp by the beds, a clock and a picture on the east wall, blinds over the front windows
    floor_lamp(b, 5, 6, 9, "spruce")
    clock(b, 8, 6, 6, "west", "spruce")
    picture(b, 8, 7, 7, "west", "blue", "spruce")
    for x in (2, 5, 8):
        blinds(b, x, 8, 2, "south", "spruce")
    # Second floor: the second bedroom and a sitting corner
    b.bed(7, 10, 8, "light_gray", facing="south")
    b.set(8, 10, 2, "barrel", facing="up", open=False)
    chair(b, 4, 10, 3, SPRUCE, "south")
    fence(b, 4, 10, 4, "dark_oak_fence")
    b.set(4, 11, 4, "dark_oak_pressure_plate", powered=False)
    box(b, 5, 10, 6, 7, 10, 7, "light_gray_carpet")
    lantern(b, 8, 11, 2)
    lantern(b, 5, 12, 6, hanging=True)  # B281: the sitting corner and the couch need the room's light too
    # Furnished (37.4): a second seat at the table, a couch on the back wall, a nightstand by the bed, a picture
    stairs(b, 4, 10, 5, SPRUCE, "south")
    couch(b, 4, 10, 9, "north", "spruce", "light_gray", length=2)
    nightstand(b, 8, 10, 9, "north", "spruce")
    picture(b, 8, 11, 6, "west", "light_gray", "spruce")
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
    # The back door into the garden, under a slab hood
    door(b, 5, 1, 10, "dark_oak_door", "south")
    for x in (4, 5, 6):
        slab(b, x, 3, 11, STONE_BRICK, top=True)
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
    # (B260: the bed's head to the west wall with a nightstand by it, the chest at its foot, the lamp on a stand)
    b.bed(3, 14, 3, "green", facing="west")
    nightstand(b, 2, 14, 2, "east", "dark_oak")
    lantern(b, 2, 15, 2)  # B281: a light on the nightstand (the lamp by the far wall doesn't reach the bed)
    b.set(4, 14, 3, "chest", facing="east", type="single", waterlogged=False)
    box(b, 4, 14, 6, 6, 14, 7, "green_carpet")
    floor_lamp(b, 6, 14, 8, "dark_oak")


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


# --- Grander homes (ROADMAP 34.16): the Noble's Manor ---------------------------------------------------------------
# The house stands back from the street behind a gravelled forecourt (z 0-6), walls x 2-16, z 7-15; Manor II adds the
# walled formal garden behind (z 15-23), Manor III the gatehouse on the street (z 1-4) and the ballroom wing on the east
# (walls x 16-24, z 7-15). The ball's venue is the ballroom's middle, MANOR_BALLROOM (NobleBalls.BALLROOM).
MANOR_WALL = Mix((7, "stone_bricks"), (2, "stone"), (1, "cracked_stone_bricks"), seed=41)
MANOR_QUOIN = "polished_andesite"
MANOR_FRAME = "dark_oak_log"
MANOR_ROOF = DEEPSLATE_TILE
MANOR_BALLROOM = (20, 1, 11)


def _bush(b, x, y, z):
    b.set(x, y, z, "oak_leaves", distance=1, persistent=True, waterlogged=False)


def manor_house(b):
    """The Manor every tier shares: a stone house of two storeys (walls x 2-16, z 7-15) round a double-height hall with
    a grand stair up to a gallery, the dining room west of it and a parlour east, two bedrooms over them, under a slate
    roof with dormers front and back and a chimney up each gable; a gravelled forecourt with clipped hedges before it."""
    # The forecourt: gravel, an andesite apron at the door, clipped hedges down both sides, two lamps
    box(b, 4, 0, 0, 14, 0, 6, "gravel")
    box(b, 8, 0, 5, 10, 0, 6, "polished_andesite")
    for z in range(0, 7):
        for x in (3, 15):
            b.set(x, 0, z, "grass_block", snowy=False)
            _bush(b, x, 1, z)
    for x in (5, 13):
        lamp_post(b, x, 1, 3, "dark_oak_fence", height=2)
    # The shell: plinth, stone walls, andesite quoins and pilasters at the hall's walls, dark oak string courses
    plinth(b, 2, 7, 16, 15, FOUNDATION_MIX, floor="spruce_planks")
    for x in range(7, 12):
        for z in range(8, 15):
            b.set(x, 0, z, "polished_andesite" if (x + z) % 2 else "polished_diorite")  # the hall's chequered floor
    walls(b, 2, 7, 16, 15, 1, 9, MANOR_WALL)
    posts(b, [(2, 7), (16, 7), (2, 15), (16, 15), (6, 7), (12, 7), (6, 15), (12, 15)], 1, 9, MANOR_QUOIN)
    beam_ring(b, 2, 7, 16, 15, 5, MANOR_FRAME)
    beam_ring(b, 2, 7, 16, 15, 10, MANOR_FRAME)
    # Inside walls between the hall and the rooms either side (doorways below and off the gallery above)
    for x in (6, 12):
        for z in range(8, 15):
            for y in range(1, 10):  # through the floor too, or a slot runs along the wall at floor level
                b.set(x, y, z, MANOR_WALL.at(x, y, z))
        for y in (1, 2):
            b.set(x, y, 9, "air")
        for y in (6, 7):
            b.set(x, y, 13, "air")
    # The upper floor over the rooms, and the gallery across the back of the hall
    box(b, 3, 5, 8, 5, 5, 14, "spruce_planks")
    box(b, 13, 5, 8, 15, 5, 14, "spruce_planks")
    box(b, 7, 5, 13, 11, 5, 14, "dark_oak_planks")
    # The grand stair: three wide up the middle of the hall to the gallery, solid underneath
    for i, z in enumerate(range(8, 13)):
        for x in (8, 9, 10):
            stairs(b, x, i + 1, z, DARK_OAK, "south")
            for y in range(1, i + 1):
                b.set(x, y, z, "dark_oak_planks")
    for x in (7, 11):  # newel posts at the stair foot (on the gallery they stood in the bedrooms' doorways: B149)
        fence(b, x, 1, 8, "dark_oak_fence")
    # The front door under a slab hood, a lamp either side of the step
    door(b, 9, 1, 7, "dark_oak_door", "south")
    # A fanlight over the door, not a lintel: the grand stair starts right inside, and a villager (1.95 tall) climbing
    # its first step a full block up while its back is still in the doorway needs three blocks of room there (B173).
    # The open trapdoor lies flat on the outside face, clear of the head of anyone on the step.
    trapdoor(b, 9, 3, 7, "dark_oak_trapdoor", "south", open_=True)
    for x in (8, 9, 10):
        slab(b, x, 3, 6, DARK_OAK, top=True)
    for x in (8, 10):
        lantern(b, x, 1, 6)
    # The back door (the garden's from II on)
    door(b, 9, 1, 15, "dark_oak_door", "north")
    # Windows: shuttered below, tall above, the hall's tall window over the door
    for z, out in ((7, "north"), (15, "south")):
        for x in (4, 7, 11, 14):  # (the hall's either side of the door without shutters, which would crowd it)
            window(b, x, 2, z, out, height=2, shutters="dark_oak_trapdoor" if x in (4, 14) else None, sill=STONE_BRICK)
        for x in (4, 14):
            window(b, x, 6, z, out, height=3, sill=STONE_BRICK)
        window(b, 9, 6, z, out, height=3, sill=STONE_BRICK if z == 15 else None)
    for x, out in ((2, "west"), (16, "east")):
        for y, h in ((2, 2), (6, 3)):
            window(b, x, y, 12, out, height=h, sill=STONE_BRICK)
    # The roof: slate, hipped all round (the Manor's alone among the homes), a dormer over each bedroom front and back
    roofs(b,
          lambda t: hip_roof(t, 1, 17, 6, 16, 10, MANOR_ROOF),
          *[lambda t, x=x, z0=z0: gable_roof(t, x - 2, x + 2, z0, z0 + 2, 11, MANOR_ROOF, axis="z", gable=MANOR_WALL,
                                              gable_at=(z0 + 1,), ridge=MANOR_ROOF)
            for x in (4, 14) for z0 in (6, 14)])
    for x in (4, 14):
        for z in (7, 15):
            pane(b, x, 12, z)
    for x in (2, 16):
        chimney(b, x, 9, 1, 16, STONE_MIX)
    # The hall's chandelier on a tie beam under the ridge
    for x in range(3, 16):
        log(b, x, 10, 11, MANOR_FRAME, axis="x")
    b.set(9, 9, 11, "chain", axis="y", waterlogged=False)
    lantern(b, 9, 8, 11, hanging=True)
    lantern(b, 7, 9, 11, hanging=True)
    lantern(b, 11, 9, 11, hanging=True)
    b.set(7, 1, 14, "potted_fern")
    b.set(11, 1, 14, "potted_fern")
    # The dining room (west): a long table, chairs down both sides, the sideboard
    for z in (10, 11, 12):
        fence(b, 4, 1, z, "dark_oak_fence")
        b.set(4, 2, z, "dark_oak_pressure_plate", powered=False)
        stairs(b, 3, 1, z, DARK_OAK, "west")
        stairs(b, 5, 1, z, DARK_OAK, "east")
    b.set(3, 1, 14, "barrel", facing="up", open=False)
    b.set(4, 1, 14, "chest", facing="north", type="single", waterlogged=False)
    b.set(5, 1, 14, "barrel", facing="up", open=False)
    lantern(b, 3, 2, 14)
    lantern(b, 4, 4, 11, hanging=True)
    # Furnished (37.4): a clock in the corner, a picture over the chairs, blinds over the windows
    clock(b, 3, 1, 8, "south", "dark_oak")
    picture(b, 3, 3, 11, "east", "purple", "dark_oak")
    blinds(b, 4, 4, 8, "south", "dark_oak")
    blinds(b, 4, 4, 14, "north", "dark_oak")
    # The parlour (east): the stove, books, a chair by the fire, stores
    b.set(15, 1, 8, "smoker", facing="west", lit=False)
    b.set(15, 1, 10, "crafting_table")
    b.set(15, 1, 14, "bookshelf")
    b.set(14, 1, 14, "bookshelf")
    stairs(b, 13, 1, 11, SPRUCE, "west")
    box(b, 14, 1, 9, 14, 1, 10, "red_carpet")  # clear of the doorway: on a carpet by its lintel a villager hits its head (B173)
    b.set(13, 1, 14, "barrel", facing="up", open=False)
    lantern(b, 14, 4, 11, hanging=True)
    # Furnished (37.4): a couch under the front window facing the fire chair, a lamp, cups on a shelf over the stove
    couch(b, 14, 1, 8, "south", "dark_oak", "red", length=2, arms=False)
    floor_lamp(b, 13, 1, 13, "dark_oak")
    shelf(b, 15, 3, 8, "west", "dark_oak")
    # Upstairs west: the main bedroom (two beds), a wardrobe chest, a rug
    # (B260: the beds' heads to the front wall, the chest at their foot)
    b.bed(3, 6, 9, "purple", facing="north")
    b.bed(4, 6, 9, "purple", facing="north")
    b.set(3, 6, 10, "chest", facing="south", type="single", waterlogged=False)
    box(b, 3, 6, 11, 5, 6, 12, "purple_carpet")
    b.set(3, 6, 14, "bookshelf")
    lantern(b, 3, 7, 14)
    # Furnished (37.4): a nightstand and a lamp, a picture over the beds, blinds over the front window
    nightstand(b, 5, 6, 8, "south", "dark_oak")
    floor_lamp(b, 5, 6, 14, "dark_oak")
    picture(b, 3, 8, 8, "south", "purple", "dark_oak")
    blinds(b, 4, 9, 8, "south", "dark_oak")
    # Upstairs east: the second bedroom
    b.bed(15, 6, 9, "light_blue", facing="north")
    b.set(13, 6, 8, "chest", facing="east", type="single", waterlogged=False)
    box(b, 13, 6, 11, 15, 6, 12, "light_blue_carpet")
    b.set(15, 6, 14, "barrel", facing="up", open=False)
    lantern(b, 15, 7, 14)
    # Furnished (37.4): a nightstand, a little couch on the back wall, a picture
    nightstand(b, 14, 6, 8, "south", "dark_oak")
    couch(b, 13, 6, 14, "north", "dark_oak", "light_blue", length=2, arms=False)
    picture(b, 15, 8, 13, "west", "light_blue", "dark_oak")


def manor():
    """19 x 18 x 17: the Noble's Manor (grade III) — a stone house of two storeys behind a gravelled forecourt with
    clipped hedges: a chequered hall with a grand stair up to a gallery, the dining room and parlour either side, two
    bedrooms above, a slate roof with dormers and a chimney up each gable."""
    b = Build(19, 18, 17)
    manor_house(b)
    b.fill_air()
    return b


def manor_garden(b):
    """Manor II's walled formal garden behind the house (z 15-23): stone walls with caps, gravel walks round a fountain
    and across, four clipped-hedge parterres with flowers in them, lamps at the walks' corners, a gate at the back."""
    for z in range(15, 24):
        for x in (0, 18):
            for y in (0, 1, 2):
                b.set(x, y, z, MANOR_WALL)
            slab(b, x, 3, z, STONE_BRICK)
    for x in range(1, 18):
        for y in (0, 1, 2):
            b.set(x, y, 23, MANOR_WALL)
        slab(b, x, 3, 23, STONE_BRICK)
    for x in (1, 17):  # the walls meet the house's back corners
        for y in (0, 1, 2):
            b.set(x, y, 15, MANOR_WALL)
        slab(b, x, 3, 15, STONE_BRICK)
    for x in (0, 18):
        b.set(x, 3, 23, "stone_bricks")
        lantern(b, x, 4, 23)
    for y in (1, 2):
        b.set(9, y, 23, "air")
    b.set(9, 1, 23, "dark_oak_fence_gate", facing="south", in_wall=True, open=False, powered=False)
    # The walks: a ring of gravel round the fountain, out to the side walls and the gate
    for x in range(6, 13):
        for z in range(16, 23):
            if x in (6, 12) or z in (16, 22):
                b.set(x, 0, z, "gravel")
    for x in list(range(1, 6)) + list(range(13, 18)):
        b.set(x, 0, 19, "gravel")
    fountain_basin(b, 9, 19)
    # The parterres: clipped box hedges round a bed of flowers, one in each corner
    for x0 in (1, 13):
        for z0 in (16, 20):
            for x in range(x0, x0 + 5):
                for z in range(z0, z0 + 3):
                    if z == z0 + 1 and x0 < x < x0 + 4:
                        continue
                    b.set(x, 0, z, "grass_block", snowy=False)
                    _bush(b, x, 1, z)
            flower_bed(b, x0 + 1, z0 + 1, x0 + 3, z0 + 1, 0, ["red_tulip", "white_tulip", "oxeye_daisy", "allium"])
    for x in (6, 12):
        for z in (16, 22):
            lamp_post(b, x, 1, z, "dark_oak_fence", height=2)


def manor_2():
    """Upgrade of the Manor (same origin and front): a walled formal garden behind, through the back door — clipped
    hedges, a fountain and gravel walks. 19 x 18 x 24."""
    b = manor().grow(19, 18, 24)
    manor_garden(b)
    b.fill_air()
    return b


def manor_gatehouse(b):
    """Manor III's gatehouse on the street (walls x 6-12, z 1-4): an arched passage through to the forecourt, a porter's
    room over it under a slate hip roof, and the forecourt walled in from it to the house and the ballroom wing."""
    for x in range(3, 16):  # the forecourt's front rows (gravel, hedges) make way
        b.clear(x, 0, 0, x, 3, 0)
    b.clear(5, 0, 3, 5, 3, 3)
    b.clear(13, 0, 3, 13, 3, 3)
    plinth(b, 6, 1, 12, 4, FOUNDATION_MIX, floor="gravel")
    for y in range(1, 4):  # solid piers either side of the passage
        for z in range(1, 5):
            for x in (6, 7, 11, 12):
                b.set(x, y, z, "stone_bricks" if (x, z) in ((6, 1), (12, 1), (6, 4), (12, 4)) else MANOR_WALL.at(x, y, z))
    for z in (1, 4):  # the arch
        stairs(b, 8, 3, z, STONE_BRICK, "east", top=True)
        stairs(b, 10, 3, z, STONE_BRICK, "west", top=True)
        b.set(9, 4, z, "chiseled_stone_bricks")
    beam_ring(b, 6, 1, 12, 4, 4, MANOR_FRAME)
    box(b, 7, 4, 2, 11, 4, 3, "dark_oak_planks")
    walls(b, 6, 1, 12, 4, 5, 6, MANOR_WALL)  # (low, so a builder on the room's floor reaches the roof's top)
    posts(b, [(6, 1), (12, 1), (6, 4), (12, 4)], 5, 6, MANOR_QUOIN)
    beam_ring(b, 6, 1, 12, 4, 7, MANOR_FRAME)
    hip_roof(b, 5, 13, 0, 5, 7, MANOR_ROOF)
    for y in range(1, 5):  # the ladder up from the passage, through a hatch in the floor
        b.set(8, y, 3, "ladder", facing="east", waterlogged=False)
    for z, out in ((1, "north"), (4, "south")):
        window(b, 9, 5, z, out, height=2, shutters="dark_oak_trapdoor", sill=STONE_BRICK)
    window(b, 6, 5, 2, "west", height=2)
    window(b, 12, 5, 2, "east", height=2)
    lantern(b, 9, 3, 2, hanging=True)
    # The porter's room: a bench, a barrel, a lamp
    stairs(b, 10, 5, 2, SPRUCE, "east")
    b.set(11, 5, 3, "barrel", facing="up", open=False)
    lantern(b, 11, 6, 3)  # on the barrel, not the floor (B260)
    # The forecourt walls: along the street, down the west side to the garden, on the east to the ballroom wing
    for x in list(range(0, 6)) + list(range(13, 19)):
        for y in (0, 1, 2):
            b.set(x, y, 1, MANOR_WALL)
        slab(b, x, 3, 1, STONE_BRICK)
    for z in range(2, 15):
        for y in (0, 1, 2):
            b.set(0, y, z, MANOR_WALL)
        slab(b, 0, 3, z, STONE_BRICK)
    for z in range(2, 7):
        for y in (0, 1, 2):
            b.set(18, y, z, MANOR_WALL)
        slab(b, 18, 3, z, STONE_BRICK)
    for x in (0, 18):
        b.set(x, 3, 1, "stone_bricks")
        lantern(b, x, 4, 1)


def manor_ballroom(b):
    """Manor III's ballroom wing on the east (walls x 16-24, z 7-15, the house's east wall its west): one tall room with
    a chequered floor, tall windows between pilasters, three chandeliers on a tie beam, a musicians' dais with a jukebox
    and note blocks, under a lower slate roof. A doorway from the parlour."""
    plinth(b, 16, 7, 24, 15, FOUNDATION_MIX)
    for x in range(17, 24):
        for z in range(8, 15):
            b.set(x, 0, z, "polished_andesite" if (x + z) % 2 else "polished_diorite")
    for y in range(1, 7):
        for x in range(17, 25):
            for z in (7, 15):
                b.set(x, y, z, MANOR_WALL.at(x, y, z))
        for z in range(8, 15):
            b.set(24, y, z, MANOR_WALL.at(24, y, z))
    posts(b, [(24, 7), (24, 15), (19, 7), (21, 7), (19, 15), (21, 15)], 1, 6, MANOR_QUOIN)
    for x in range(17, 25):
        log(b, x, 7, 7, MANOR_FRAME, axis="x")
        log(b, x, 7, 15, MANOR_FRAME, axis="x")
    for z in range(8, 15):
        log(b, 24, 7, z, MANOR_FRAME, axis="z")
    for x in range(17, 24):  # the tie beam the chandeliers hang from
        log(b, x, 7, 11, MANOR_FRAME, axis="x")
    keep = {p: v for p, v in b.blocks.items() if p[0] <= 17 and p[1] >= 10}
    # Its roof hipped at the east end like the house's, running in under the house's eaves at the west
    hip = Build(b.w, b.h, b.d)
    hip_roof(hip, 9, 25, 6, 16, 7, MANOR_ROOF)
    b.blocks.update({p: v for p, v in hip.blocks.items() if p[0] >= 17})
    b.blocks.update(keep)
    for z, out in ((7, "north"), (15, "south")):
        for x in (18, 20, 22):
            window(b, x, 2, z, out, height=3, sill=STONE_BRICK, lintel=STONE_BRICK)
    for z in (9, 13):
        window(b, 24, 2, z, "east", height=3, sill=STONE_BRICK, lintel=STONE_BRICK)
    # The doorway from the parlour
    for y in (1, 2):
        b.set(16, y, 12, "air")
    b.set(16, 3, 12, MANOR_WALL.at(16, 3, 12))  # the old window's top pane walled up over the doorway
    b.blocks.pop((17, 1, 12), None)  # and its sill (inside now)
    # Chandeliers, the dais, pots in the corners, benches down the sides
    for x in (18, 20, 22):
        b.set(x, 6, 11, "chain", axis="y", waterlogged=False)
        lantern(b, x, 5, 11, hanging=True)
    for x in (18, 19, 21, 22):
        slab(b, x, 1, 14, DIORITE, top=False)
    b.set(20, 1, 14, "jukebox", has_record=False)
    b.set(17, 1, 14, "note_block", instrument="harp", note=0, powered=False)
    b.set(23, 1, 14, "note_block", instrument="harp", note=0, powered=False)
    for x, z in ((17, 8), (23, 8)):
        b.set(x, 1, z, "potted_azalea_bush")
    for z in (9, 10):
        stairs(b, 23, 1, z, DARK_OAK, "east")
    floor_lamp(b, 23, 1, 12, "dark_oak")  # a standing lamp by the seats, not a lantern on the floor (B260)


def manor_3():
    """Upgrade of Manor II (same origin and front): a gatehouse on the street with an arched passage and a porter's
    room, the forecourt walled in, and a ballroom wing on the east where the Noble's Ball is held. 26 x 18 x 24."""
    b = manor_2().grow(26, 18, 24)
    manor_gatehouse(b)
    manor_ballroom(b)
    b.fill_air()
    return b
