"""
Basements (ROADMAP 26.15): the Level 4 of the homes and workshops that top out at III, each its Level III with a
furnished, lit room dug under it and a way down from inside; and the cellars some jobs have from Level 1 (the
Butcher's Shop and its ice-lined freezer, here; the smith's coal store, the cook's cold larder and the Storehouse's
vault are drawn with their builds, from the helpers below).

A basement is drawn under y = 0 (Build(..., basement=BASEMENT), kit.cellar): its floor at y = -4, three blocks of room,
the ground floor's own floor its ceiling. The builder digs it out with the rest of the site (CLEAR) and builds it bottom
up; StarterBlueprints.Entry's basement moves it back under the ground when the blueprint loads.

Furnishing rules: no job block in a basement but the job's own (a barrel, a lectern or a brewing stand down there would
give a villager a job), so casks are spruce log ends, stock is in chests; lanterns hang from the floor overhead.
"""
from kit import *

BASEMENT = 4  # floor at y -4, room y -3..-1
CELLAR_STONE = Mix((6, "cobblestone"), (3, "mossy_cobblestone"), (2, "stone"), seed=61)
COAL_STONE = Mix((5, "cobblestone"), (3, "cobbled_deepslate"), (2, "stone"), seed=62)
ICE_WALL = Mix((7, "packed_ice"), (1, "snow_block"), seed=63)
FLOOR = -BASEMENT
LOW, MID, TOP = -3, -2, -1  # the cellar's three courses: on the floor, waist height, under the ceiling


def chest(b, x, y, z, facing):
    b.set(x, y, z, "chest", facing=facing, type="single", waterlogged=False)


def casks(b, cells, axis, wood="spruce_log", high=2):
    """A rack of casks against a wall: logs lying end-on to the room (`axis` across the wall), `high` courses."""
    for x, z in cells:
        for y in range(LOW, LOW + high):
            log(b, x, y, z, wood, axis=axis)


def shelf(b, cells, fam, things=()):
    """A shelf at waist height along a wall (top slabs at y -2) with small things standing on it."""
    for i, (x, z) in enumerate(cells):
        slab(b, x, MID, z, fam, top=True)
        if things:
            thing = things[i % len(things)]
            if thing:
                b.set(x, TOP, z, thing) if isinstance(thing, str) else b.set(x, TOP, z, thing[0], **thing[1])


def ham(b, x, z, meat="red_terracotta"):
    """A side of meat hanging from the ceiling on a chain: chain at y -1, the meat at y -2 (a head-height thing, so
    hang it over a counter or along a wall, not in the walkway)."""
    b.set(x, TOP, z, "chain", axis="y", waterlogged=False)
    b.set(x, MID, z, meat)


def candle(b, x, y, z, n=2):
    b.set(x, y, z, "candle", candles=n, lit=True, waterlogged=False)


def hang_light(b, x, z):
    lantern(b, x, TOP, z, hanging=True)


# --- the Level 4 cellars ---------------------------------------------------------------------------------------------
def root_cellar(b, x0, z0, x1, z1, ladder):
    """A root cellar (walls x0..x1, z0..z1): cobbled walls under a spruce beam, an earth floor, bins of pumpkins and
    melons, hay, sacks (chests), mushrooms in pots on a shelf, a sorting table; two lanterns."""
    cellar(b, x0, z0, x1, z1, wall=CELLAR_STONE, floor="packed_mud", ladder=ladder, beam="spruce_log")
    ix0, iz0, ix1, iz1 = x0 + 1, z0 + 1, x1 - 1, z1 - 1
    # The back wall: bins of the harvest, hay at the ends
    b.set(ix0, LOW, iz1, "hay_block", axis="y")
    b.set(ix0, MID, iz1, "hay_block", axis="x")
    for x, crop in zip(range(ix0 + 1, ix1), ("pumpkin", "melon", "pumpkin", "melon", "carved_pumpkin", "pumpkin")):
        b.set(x, LOW, iz1, crop) if crop != "carved_pumpkin" else b.set(x, LOW, iz1, crop, facing="north")
        trapdoor(b, x, MID, iz1, "spruce_trapdoor", "north", open_=True)  # the bin's board
    # The west wall: sacks (chests) and a shelf of potted mushrooms over them
    for z in range(iz0 + 1, iz1):
        chest(b, ix0, LOW, z, "east")
    shelf(b, [(ix0, z) for z in range(iz0 + 1, iz1)], SPRUCE, ["potted_brown_mushroom", None, "potted_red_mushroom"])
    b.set(ix0, LOW, iz0, "hay_block", axis="z")
    # The sorting table in the middle: a top slab on a post, a candle
    cx, cz = (ix0 + ix1) // 2, (iz0 + iz1) // 2
    fence(b, cx, LOW, cz, "spruce_fence")
    b.set(cx, MID, cz, "spruce_pressure_plate", powered=False)
    hang_light(b, ix0 + 2, cz)
    hang_light(b, ix1 - 1, cz)


def wine_cellar(b, x0, z0, x1, z1, ladder=None, wall=MOSSY_BRICK_MIX, wood="spruce_log", fam=SPRUCE, keep=()):
    """A wine cellar (walls x0..x1, z0..z1): stone-brick walls, racks of casks two high along both side walls, a tasting
    table with two chairs and a candle, chests of bottles and decorated pots on the back wall, lanterns over the table.
    keep: cells (x, z) left clear (a stair's foot)."""
    cellar(b, x0, z0, x1, z1, wall=wall, floor="stone_bricks", ladder=ladder, beam="dark_oak_log")
    ix0, iz0, ix1, iz1 = x0 + 1, z0 + 1, x1 - 1, z1 - 1
    keep = set(keep) | ({(ladder[0], ladder[1])} if ladder else set())
    near = {(x + dx, z + dz) for x, z in keep for dx in (-1, 0, 1) for dz in (-1, 0, 1)}
    for x in (ix0, ix1):
        casks(b, [(x, z) for z in range(iz0, iz1) if (x, z) not in near], "x", wood)
    for x in range(ix0 + 1, ix1):
        if (x, iz1) in near:
            continue
        if (x - ix0) % 3 == 1:
            b.set(x, LOW, iz1, "decorated_pot", facing="north", cracked=False, waterlogged=False)
        else:
            chest(b, x, LOW, iz1, "north")
    cx, cz = (ix0 + ix1) // 2, (iz0 + iz1) // 2
    if (cx, cz) not in near:
        slab(b, cx, LOW, cz, fam, top=True)
        candle(b, cx, MID, cz, 3)
        chair(b, cx - 1, LOW, cz, fam, "east")
        chair(b, cx + 1, LOW, cz, fam, "west")
    for lx, lz in ((cx, cz - 1 if cz - 1 > iz0 else cz), (cx, iz1 - 1)):
        if (lx, lz) in keep:
            lx += 1 if (lx + 1, lz) not in keep else -1
        hang_light(b, lx, lz)


def store_room(b, x0, z0, x1, z1, ladder, wall=CELLAR_STONE, floor="spruce_planks", cloth=("white_wool", "light_blue_wool")):
    """A shop's stockroom (walls x0..x1, z0..z1): chests two high along the walls, bolts of cloth, crates (spruce planks
    with a trapdoor lid), a packing table; lanterns."""
    cellar(b, x0, z0, x1, z1, wall=wall, floor=floor, ladder=ladder, beam="spruce_log")
    ix0, iz0, ix1, iz1 = x0 + 1, z0 + 1, x1 - 1, z1 - 1
    lx, lz = ladder[0], ladder[1]
    for x in range(ix0, ix1 + 1):
        if abs(x - lx) <= 1 and lz == iz0:
            continue
        for y in (LOW, MID):
            chest(b, x, y, iz0, "south")
    for z in range(iz0 + 1, iz1):
        if abs(z - lz) <= 1 and lx == ix1:
            continue
        b.set(ix1, LOW, z, cloth[z % 2])
        b.set(ix1, MID, z, cloth[(z + 1) % 2])
    for x in range(ix0 + 1, ix1):
        if abs(x - lx) <= 1 and lz == iz1:
            continue
        b.set(x, LOW, iz1, "spruce_planks")
        trapdoor(b, x, MID, iz1, "spruce_trapdoor", "north")  # the crate's lid
    cx, cz = (ix0 + ix1) // 2, (iz0 + iz1) // 2
    slab(b, cx, LOW, cz, SPRUCE, top=True)
    slab(b, cx + 1, LOW, cz, SPRUCE, top=True)
    b.set(cx, MID, cz, "white_carpet")
    hang_light(b, cx - 1, cz)
    hang_light(b, cx + 2, cz)


def archive(b, x0, z0, x1, z1, keep=()):
    """A library's archive (walls x0..x1, z0..z1): bookshelves round the walls, stacks of chests for the old ledgers, a
    reading table with a candle and two chairs; lanterns."""
    cellar(b, x0, z0, x1, z1, wall=MOSSY_BRICK_MIX, floor="stone_bricks", beam="dark_oak_log")
    ix0, iz0, ix1, iz1 = x0 + 1, z0 + 1, x1 - 1, z1 - 1
    near = {(x + dx, z + dz) for x, z in keep for dx in (-1, 0, 1) for dz in (-1, 0, 1)}
    for x in range(ix0, ix1 + 1):
        for z in (iz0, iz1):
            if (x, z) not in near:
                for y in (LOW, MID):
                    b.set(x, y, z, "bookshelf")
    for z in range(iz0 + 1, iz1):
        for x in (ix0, ix1):
            if (x, z) not in near:
                chest(b, x, LOW, z, "east" if x == ix0 else "west")
                b.set(x, MID, z, "bookshelf")
    cx, cz = (ix0 + ix1) // 2, iz0 + 2
    for x in (cx, cx + 1):
        slab(b, x, LOW, cz, DARK_OAK, top=True)
        chair(b, x, LOW, cz - 1, DARK_OAK, "south")
    candle(b, cx, MID, cz, 3)
    hang_light(b, cx, cz)
    hang_light(b, cx + 1, iz1 - 1)
    hang_light(b, ix0 + 1, iz1 - 1)


def herb_store(b, x0, z0, x1, z1, ladder, hatch="spruce_trapdoor"):
    """A healer's cool store (walls x0..x1, z0..z1): white walls under a birch beam, shelves of potted herbs and
    mushrooms, bunches of drying kelp and moss, chests of remedies; a sorting table and lanterns."""
    cellar(b, x0, z0, x1, z1, wall=Mix((5, "stone_bricks"), (2, "polished_diorite"), (1, "mossy_stone_bricks"), seed=64),
           floor="polished_diorite", ladder=ladder, beam="stripped_birch_log", hatch=hatch)
    ix0, iz0, ix1, iz1 = x0 + 1, z0 + 1, x1 - 1, z1 - 1
    pots = ["potted_fern", "potted_brown_mushroom", "potted_azure_bluet", "potted_red_mushroom", "potted_lily_of_the_valley"]
    for z in range(iz0, iz1 + 1):
        if (ix1, z) != (ladder[0], ladder[1]):
            chest(b, ix1, LOW, z, "west")
    shelf(b, [(ix1, z) for z in range(iz0, iz1 + 1)], BIRCH, pots)
    for x in range(ix0 + 1, ix1):
        b.set(x, LOW, iz0, "dried_kelp_block" if x % 2 else "moss_block")
        trapdoor(b, x, MID, iz0, "birch_trapdoor", "south", open_=True)
    for x in range(ix0 + 1, ix1):
        if (x, iz1) != (ladder[0], ladder[1]) and abs(x - ladder[0]) > 0:
            slab(b, x, LOW, iz1, BIRCH, top=True)
    shelf_things = [("potted_crimson_fungus", {}), None, ("potted_warped_fungus", {})]
    for i, x in enumerate(range(ix0 + 1, ix1)):
        if (x, iz1) != (ladder[0], ladder[1]) and shelf_things[i % 3]:
            b.set(x, MID, iz1, shelf_things[i % 3][0])
    cx, cz = (ix0 + ix1) // 2, (iz0 + iz1) // 2
    hang_light(b, cx - 1, cz)
    hang_light(b, cx + 2, cz)


def armoury(b, x0, z0, x1, z1, ladder):
    """A watch's armoury and lock-up (walls x0..x1, z0..z1): a cell behind iron bars at the west end, an anvil, chests
    of kit, hay targets, a long table; lanterns."""
    cellar(b, x0, z0, x1, z1, wall=MOSSY_BRICK_MIX, floor=Mix((3, "cobblestone"), (2, "stone_bricks"), seed=65), ladder=ladder,
           beam="spruce_log")
    ix0, iz0, ix1, iz1 = x0 + 1, z0 + 1, x1 - 1, z1 - 1
    # The cell: two blocks deep at the west end, bars across with a gap for its door
    for z in range(iz0, iz1 + 1):
        for y in (LOW, MID, TOP):
            if z != iz0 + 1 or y == TOP:
                b.set(ix0 + 2, y, z, "iron_bars", waterlogged=False)
    for z in (iz0, iz0 + 2):  # stone posts either side of the cell's door, so the bars meet them (B648), not the air
        for y in (LOW, MID, TOP):
            b.set(ix0 + 2, y, z, "stone_bricks")
    b.set(ix0, LOW, iz1, "hay_block", axis="y")
    b.set(ix0 + 1, LOW, iz0, "spruce_trapdoor", facing="north", half="bottom", open=False, powered=False, waterlogged=False)
    # The armoury
    b.set(ix0 + 3, LOW, iz1, "anvil", facing="east")
    for x in range(ix0 + 4, ix1 + 1):
        if (x, iz1) != (ladder[0], ladder[1]):
            chest(b, x, LOW, iz1, "north")
    for z in range(iz0, iz1):
        if (ix1, z) != (ladder[0], ladder[1]):
            b.set(ix1, LOW, z, "hay_block", axis="y")
            b.set(ix1, MID, z, "target")
    hang_light(b, ix0 + 1, iz0 + 1)
    hang_light(b, ix0 + 4, iz0 + 1)
    hang_light(b, ix1 - 1, iz0 + 1)


# --- the Level 4 builds ----------------------------------------------------------------------------------------------
def starter_cottage_4():
    """Upgrade of Starter Cottage III: a root cellar under the cottage (walls x 1-9, z 2-8), down a ladder by the
    kitchen door. 17 x 16 x 11, 4 below the ground."""
    from starter import starter_cottage_3
    b = starter_cottage_3().grow(17, 16, 11, basement=BASEMENT)
    root_cellar(b, 1, 2, 9, 8, ladder=(8, 3, "west"))
    b.fill_air()
    return b


def stone_house_4():
    """Upgrade of Stone House III: a wine cellar under the front house (walls x 1-9, z 1-7), down a ladder by the front
    door. 11 x 16 x 14, 4 below the ground."""
    from houses import stone_house_3
    b = stone_house_3().grow(11, 16, 14, basement=BASEMENT)
    wine_cellar(b, 1, 1, 9, 7, ladder=(7, 2, "south"))
    b.fill_air()
    return b


def townhouse_4():
    """Upgrade of the Burgher's Townhouse III: a vaulted cellar under the house (walls x 1-9, z 2-10) down a flight of
    stone stairs from the hall: casks of wine, the coal store, chests of the household's stock. 15 x 26 x 18, 4 below."""
    from houses import townhouse_3
    b = townhouse_3().grow(15, 26, 18, basement=BASEMENT)
    wine_cellar(b, 1, 2, 9, 10, wall=BRICK_WALL_MIX, wood="dark_oak_log", fam=DARK_OAK, keep=[(8, z) for z in range(3, 8)])
    cellar_stairs(b, 8, 3, "south", STONE_BRICK)
    for z in (4, 5):
        fence(b, 7, 1, z, "dark_oak_fence")  # a rail along the stairwell
    # The coal store in the north-west corner, a lantern by the stairs' foot
    for x, z, y in ((2, 3, LOW), (3, 3, LOW), (2, 4, LOW), (2, 3, MID)):
        b.set(x, y, z, "coal_block")
    b.fill_air()
    return b


def manor_4():
    """Upgrade of the Noble's Manor III: a great wine cellar under the hall and the west room (walls x 2-12, z 7-15),
    down a stone stair beside the hall's table. 26 x 18 x 24, 4 below."""
    from houses import manor_3
    b = manor_3().grow(26, 18, 24, basement=BASEMENT)
    wine_cellar(b, 2, 7, 12, 15, wall=MOSSY_BRICK_MIX, wood="dark_oak_log", fam=DARK_OAK, keep=[(7, z) for z in range(8, 14)])
    cellar_stairs(b, 7, 12, "north", STONE_BRICK)
    # Columns down the middle of the long room hold up the hall's floor
    for x, z in ((5, 11), (9, 11)):
        for y in (LOW, MID, TOP):
            b.set(x, y, z, "stone_bricks" if y != MID else "chiseled_stone_bricks")
    b.fill_air()
    return b


def supply_shop_4():
    """Upgrade of the Supply Shop III: a stockroom under the shop (walls x 1-9, z 2-8), down a ladder behind the counter.
    17 x 11 x 16, 4 below."""
    from starter import supply_shop_3
    b = supply_shop_3().grow(17, 11, 16, basement=BASEMENT)
    store_room(b, 1, 2, 9, 8, ladder=(2, 7, "east"))
    b.fill_air()
    return b


def library_4():
    """Upgrade of the Library III: an archive under the reading room (walls x 1-11, z 2-10), down a stair along the back
    of the room. 18 x 15 x 20, 4 below."""
    from starter import library_3
    b = library_3().grow(18, 15, 20, basement=BASEMENT)
    archive(b, 1, 2, 11, 10, keep=[(x, 8) for x in range(4, 10)])
    cellar_stairs(b, 8, 8, "west", STONE_BRICK)
    for x in (6, 7):
        fence(b, x, 1, 7, "dark_oak_fence")
    b.fill_air()
    return b


def healing_center_4():
    """Upgrade of the Healing Center III: a cool store of herbs and remedies under the front (walls x 1-9, z 2-8), down a
    ladder in the back room. 11 x 10 x 22, 4 below."""
    from starter import healing_center_3
    b = healing_center_3().grow(11, 10, 22, basement=BASEMENT)
    herb_store(b, 1, 2, 9, 8, ladder=(2, 7, "east"), hatch=None)  # in the back room's nook behind the brewing stand
    b.fill_air()
    return b


def lookout_tower_4():
    """Upgrade of the Lookout Tower III: an armoury and lock-up under the tower and the guardroom (walls x 1-12, z 1-5),
    down a ladder by the guardroom door. 14 x 23 x 7, 4 below."""
    from starter import lookout_tower_3
    b = lookout_tower_3().grow(14, 23, 7, basement=BASEMENT)
    armoury(b, 1, 1, 12, 5, ladder=(8, 2, "south"))
    b.fill_air()
    return b


# --- the Level 1 cellars -----------------------------------------------------------------------------------------
def coal_store(b, x0, z0, x1, z1, ladder):
    """A smith's coal store (walls x0..x1, z0..z1): heaps of coal behind boards, raw iron, a chest of ore, a lantern."""
    cellar(b, x0, z0, x1, z1, wall=COAL_STONE, floor="cobbled_deepslate", ladder=ladder, beam="dark_oak_log")
    ix0, iz0, ix1, iz1 = x0 + 1, z0 + 1, x1 - 1, z1 - 1
    for x in range(ix0 + 2, ix1 + 1):
        b.set(x, LOW, iz1, "coal_block")
        if x % 2 == 0:
            b.set(x, MID, iz1, "coal_block")
        else:
            trapdoor(b, x, MID, iz1, "dark_oak_trapdoor", "north", open_=True)
    for z in range(iz0, iz1):
        b.set(ix1, LOW, z, "coal_block" if z % 2 else "raw_iron_block")
    b.set(ix1, MID, iz0, "coal_block")
    chest(b, ix0, LOW, iz1, "east")
    chest(b, ix0 + 1, LOW, iz1, "north")
    hang_light(b, (ix0 + ix1) // 2, (iz0 + iz1) // 2)


def cold_larder(b, x0, z0, x1, z1, ladder):
    """A cook's cold larder (walls x0..x1, z0..z1): stone walls, a slab shelf of jars and pumpkins, sides of meat on chains
    over a stone counter, chests of flour and salt, a lantern."""
    cellar(b, x0, z0, x1, z1, wall=CELLAR_STONE, floor="stone_bricks", ladder=ladder, beam="spruce_log")
    ix0, iz0, ix1, iz1 = x0 + 1, z0 + 1, x1 - 1, z1 - 1
    for x in range(ix0 + 1, ix1 + 1):
        b.set(x, LOW, iz1, "smooth_stone")
        ham(b, x, iz1) if x % 2 else b.set(x, MID, iz1, "pumpkin")
    for z in range(iz0 + 2, iz1):
        chest(b, ix1, LOW, z, "west")
    shelf(b, [(ix1, z) for z in range(iz0 + 2, iz1)], SPRUCE, ["potted_brown_mushroom", "flower_pot"])
    b.set(ix1, LOW, iz0, "hay_block", axis="y")
    hang_light(b, (ix0 + ix1) // 2, (iz0 + iz1) // 2)


def vault(b, x0, z0, x1, z1, ladder):
    """The Storehouse's vault (walls x0..x1, z0..z1): stone-brick walls, chests two high round it, a stone pier by
    the strongroom corner, a lantern."""
    cellar(b, x0, z0, x1, z1, wall=MOSSY_BRICK_MIX, floor="polished_andesite", ladder=ladder, beam="spruce_log")
    ix0, iz0, ix1, iz1 = x0 + 1, z0 + 1, x1 - 1, z1 - 1
    for z in range(iz0, iz1 + 1):
        for y in (LOW, MID):
            chest(b, ix1, y, z, "west")
    for x in range(ix0 + 1, ix1):
        for y in (LOW, MID):
            chest(b, x, y, iz1, "north")
    # The strongroom corner by the chests, behind a stone pier (B648: it was one column of bars standing out from the
    # wall with nothing to join on its other side, a thin strip ending in the air)
    for y in (LOW, MID, TOP):
        b.set(ix1 - 1, y, iz0, "polished_andesite")
    hang_light(b, (ix0 + ix1) // 2, (iz0 + iz1) // 2)


def freezer(b, x0, z0, x1, z1, ladder):
    """The Butcher's freezer (walls x0..x1, z0..z1): walls of packed ice, a floor of packed ice with a blue-ice path, sides
    of meat on chains along the walls, chests of the frozen stock, snow heaped in the corners, a lantern (packed and blue
    ice don't melt)."""
    cellar(b, x0, z0, x1, z1, wall=ICE_WALL, floor="packed_ice", ladder=ladder, beam="spruce_log")
    ix0, iz0, ix1, iz1 = x0 + 1, z0 + 1, x1 - 1, z1 - 1
    cx = (ix0 + ix1) // 2
    for z in range(iz0, iz1 + 1):
        b.set(cx, FLOOR, z, "blue_ice")
    for x in (ix0, ix1):
        for z in range(iz0 + 1, iz1):
            if (x, z) != (ladder[0], ladder[1]) and abs(z - ladder[1]) > 0:
                if z % 2:
                    ham(b, x, z)
                else:
                    ham(b, x, z, "bone_block")
                b.set(x, LOW, z, "snow_block" if z % 2 else "packed_ice")
    for x in range(ix0, ix1 + 1):
        if x != cx and (x, iz1) != (ladder[0], ladder[1]):
            chest(b, x, LOW, iz1, "north")
    b.set(ix0, LOW, iz0, "snow_block")
    b.set(ix0, MID, iz0, "snow_block")
    hang_light(b, cx, (iz0 + iz1) // 2)


# --- The Butcher's Shop (Level 1, with its freezer) -------------------------------------------------------------------
# A brick-and-plaster shop under a dark oak roof, ridge along the street (walls x 1-9, z 2-8): a red-and-white awning over
# a wide shop window either side of the door, sides of meat hanging in the windows, the smoker (the butcher's) in a
# brick chimney breast against the east wall, a stone counter, a chest; behind, a fenced pen with hay and a gate (the
# butcher herds round the smoker). Under it the freezer, down a ladder behind the counter.
BUTCHER_FRAME = "dark_oak_log"
BUTCHER_PLASTER = "white_concrete"
BUTCHER_ROOF = DARK_OAK


def butchers_shop():
    """13 x 11 x 13: the Butcher's Shop, its freezer 4 below."""
    b = Build(13, 11, 13, basement=BASEMENT)
    plinth(b, 1, 2, 9, 8, STONE_MIX, floor="smooth_stone")
    walls(b, 1, 2, 9, 8, 1, 1, "bricks")
    walls(b, 1, 2, 9, 8, 2, 4, BUTCHER_PLASTER)
    posts(b, [(1, 2), (9, 2), (1, 8), (9, 8), (4, 2), (6, 2), (5, 8), (1, 5), (9, 5)], 1, 4, BUTCHER_FRAME)
    beam_ring(b, 1, 2, 9, 8, 5, BUTCHER_FRAME)
    # The door, its step and a lantern either side
    door(b, 5, 1, 2, "spruce_door", "south")
    stairs(b, 5, 0, 1, COBBLE, "south")
    log(b, 5, 3, 2, BUTCHER_FRAME, axis="x")
    # Shop windows either side, sides of meat hanging inside them from the beam
    for x in (2, 3, 7, 8):
        for y in (2, 3):
            pane(b, x, y, 2)
    # The awning over the front: a striped valance, carpet on a slab slope
    colors = ("red_wool", "white_wool")
    for x in range(1, 10):
        c = colors[x % 2]
        b.set(x, 4, 1, c)
        if c == colors[0] and x not in (4, 5, 6):
            b.set(x, 3, 1, c)
    lantern(b, 4, 3, 1, hanging=True)
    lantern(b, 6, 3, 1, hanging=True)
    # Inside: the counter across the shop, a chopping slab, hooks over it
    for x in (2, 7, 8):
        b.set(x, 1, 3, "smooth_stone_slab", type="double", waterlogged=False)
    for x in (2, 8):  # sides of meat on hooks over the counter, in the windows
        b.set(x, 4, 3, "chain", axis="y", waterlogged=False)
        b.set(x, 3, 3, "red_terracotta")
    for x in range(2, 9):
        log(b, x, 5, 5, BUTCHER_FRAME, axis="x")  # a tie beam
    lantern(b, 3, 4, 5, hanging=True)
    lantern(b, 7, 4, 5, hanging=True)
    # The back counter: smooth stone, the cleaver's block (a stripped log), and the smoker in its chimney breast
    for z in (6, 7):
        b.set(8, 1, z, "smooth_stone_slab", type="double", waterlogged=False)
    b.set(8, 2, 7, "stripped_oak_log", axis="y")
    b.set(8, 1, 5, "smoker", facing="west", lit=False)
    for y in range(2, 5):
        b.set(8, y, 5, "bricks")
    chimney(b, 10, 5, 0, 9, "bricks")  # up the outside of the east gable
    chest(b, 2, 1, 7, "east")
    b.set(2, 1, 6, "hay_block", axis="y")
    b.set(3, 1, 7, "crafting_table")
    # Windows at the sides and the back
    window(b, 1, 2, 4, "west", height=2, shutters="dark_oak_trapdoor", sill=BRICK)
    window(b, 1, 2, 6, "west", height=2, shutters="dark_oak_trapdoor", sill=BRICK)
    window(b, 9, 2, 3, "east", height=2, shutters="dark_oak_trapdoor", sill=BRICK)
    window(b, 9, 2, 7, "east", height=2, shutters="dark_oak_trapdoor", sill=BRICK)
    window(b, 3, 2, 8, "south", height=2, shutters="dark_oak_trapdoor", sill=BRICK)
    # The roof, ridge along the street; gables with a post and a little window
    gable_roof(b, 0, 10, 1, 9, 5, BUTCHER_ROOF, axis="x", gable=BUTCHER_PLASTER, gable_at=(1, 9), eave_trim=BUTCHER_ROOF)
    for x in (1, 9):
        log(b, x, 6, 5, BUTCHER_FRAME)
        pane(b, x, 7, 5)
    # The back door to the pen
    door(b, 7, 1, 8, "spruce_door", "south")
    b.set(7, 0, 9, "cobblestone")
    # The pen behind: a fence on grass with a gate, hay, a lamp at its corner
    for x in range(1, 12):
        for z in range(9, 13):
            if b.get(x, 0, z) is None:
                b.set(x, 0, z, "grass_block", snowy=False)
    for x in range(1, 12):
        if x != 7:
            fence(b, x, 1, 12, "spruce_fence")
    for z in range(9, 12):
        fence(b, 1, 1, z, "spruce_fence")
        fence(b, 11, 1, z, "spruce_fence")
    for x in (10, 11):
        fence(b, x, 1, 8, "spruce_fence")
    b.set(7, 1, 12, "spruce_fence_gate", facing="south", open=False, powered=False, in_wall=False)
    b.set(3, 1, 11, "hay_block", axis="y")
    b.set(4, 1, 11, "hay_block", axis="x")
    lantern(b, 1, 2, 12)  # on the pen's corner post
    # The freezer under the shop, down a ladder behind the counter (west of the door, against the front wall)
    freezer(b, 1, 2, 9, 8, ladder=(3, 3, "south"))
    b.fill_air()
    return b


LEVEL_4 = (("starter_cottage_4", starter_cottage_4), ("stone_house_4", stone_house_4), ("townhouse_4", townhouse_4),
           ("manor_4", manor_4), ("supply_shop_4", supply_shop_4), ("library_4", library_4),
           ("healing_center_4", healing_center_4), ("lookout_tower_4", lookout_tower_4))


def cutaway(b, cut=None, axis="z"):
    """A copy of build {@code b} for a render (tools/blueprints/render/preview.sh): the ground round its basement drawn
    in (grass on top, dirt, then stone) and everything in front of the cut (z < cut, or x < cut with axis="x") taken
    away, so the cellar shows under the house as in a section drawing."""
    out = Build(b.w, b.h, b.d, b.basement)
    out.blocks = dict(b.blocks)
    out.nbt = dict(getattr(b, "nbt", {}))
    for x in range(b.w):
        for z in range(b.d):
            for y in range(-b.basement, 0):
                if (x, y, z) not in out.blocks:
                    out.blocks[(x, y, z)] = ("minecraft:" + ("grass_block" if y == -1 else "dirt" if y == -2 else "stone"),
                                             (("snowy", "false"),) if y == -1 else ())
    cut = (b.d if axis == "z" else b.w) // 2 if cut is None else cut
    for p in list(out.blocks):
        if (p[2] if axis == "z" else p[0]) < cut:
            del out.blocks[p]
    return out


def above(b):
    """A copy of build {@code b} without its basement, for a render of how it stands on the ground."""
    out = Build(b.w, b.h, b.d)
    out.blocks = {p: v for p, v in b.blocks.items() if p[1] >= 0}
    out.nbt = {p: v for p, v in getattr(b, "nbt", {}).items() if p[1] >= 0}
    return out
