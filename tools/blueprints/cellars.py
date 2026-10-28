"""
Cellars for the builds that stopped at Level II (ROADMAP 36.7): eight Level IIIs, each its Level II (same footprint,
origin and front, every block above the ground kept) with a furnished, lit room dug under it and a ladder down from
inside under a hatch in the floor. Drawn like the Level 4s of 26.15 (basements.py): the basement under y = 0, its floor
at y = -4, the ground floor's own floor its ceiling; StarterBlueprints.Entry's basement moves it back under the ground.

  Inn III                 an ale and wine cellar under the inn        (walls x 1-11, z 2-11)
  Winery III              a barrel vault under the barrel cellar      (walls x 2-10, z 3-9)
  Farmstead III           a root cellar under the farmhouse           (walls x 1-7,  z 2-8)
  Guildhall III           a strongroom under the hall's west end      (walls x 1-8,  z 4-12)
  Barracks III            an armoury and lock-up under the barracks   (walls x 1-11, z 2-8)
  Research Lab III        a lower lab under the lab                   (walls x 1-9,  z 1-9)
  Jeweller's Workshop III a vault under the shop                      (walls x 1-7,  z 1-7)
  Artisan's House III     a storeroom under the house                 (walls x 1-7,  z 2-10)

The ladders stand where the floor above is clear two blocks up and away from every worker's chest and job block, so
the jobs upstairs work as they did. No job block down here (basements.py's rule): casks are log ends, stock is chests.
"""
from kit import *
from basements import (BASEMENT, CELLAR_STONE, LOW, MID, TOP, FLOOR, armoury, casks, candle, chest, hang_light,
                       root_cellar, shelf, store_room, wine_cellar)


def inn_3():
    """Upgrade of the Inn II: an ale and wine cellar under the inn, down a ladder in the taproom's north-east corner:
    racks of wine casks along both side walls, a row of oak ale casks and sacks of malt (hay) along the front wall,
    bottles in chests and pots at the back, a tasting table. 19 x 16 x 13, 4 below."""
    from starter import inn_2
    b = inn_2().grow(19, 16, 13, basement=BASEMENT)
    wine_cellar(b, 1, 2, 11, 11, ladder=(10, 3, "west"))
    # The ale: oak casks end-on along the front wall, two high, malt beside them
    for x in range(4, 8):
        for y in (LOW, MID):
            log(b, x, y, 3, "oak_log", axis="z")
    b.set(3, LOW, 3, "hay_block", axis="y")
    b.set(8, LOW, 3, "hay_block", axis="x")
    b.set(8, MID, 3, "hay_block", axis="z")
    b.fill_air()
    return b


def barrel_vault(b, x0, z0, x1, z1, ladder):
    """A vintner's barrel vault (walls x0..x1, z0..z1): casks two high round the back and the east wall under a vault of
    upside-down stone-brick stairs, a tasting table with a candle and two chairs, a chest of bottles; lanterns."""
    cellar(b, x0, z0, x1, z1, wall=MOSSY_BRICK_MIX, floor="stone_bricks", ladder=ladder, beam=None)
    ix0, iz0, ix1, iz1 = x0 + 1, z0 + 1, x1 - 1, z1 - 1
    lx, lz = ladder[0], ladder[1]
    casks(b, [(x, iz1) for x in range(ix0 + 1, ix1 + 1)], "z", "spruce_log")
    casks(b, [(ix1, z) for z in range(iz0 + 1, iz1)], "x", "dark_oak_log")
    # The vault's haunches: upside-down stairs along the walls under the ceiling, stepping the room in
    for x in range(ix0, ix1 + 1):
        for z, facing in ((iz0, "north"), (iz1, "south")):
            if (x, z) != (lx, lz) and abs(x - lx) + abs(z - lz) > 1:
                stairs(b, x, TOP, z, STONE_BRICK, facing, top=True)
    for z in range(iz0 + 1, iz1):
        for x, facing in ((ix0, "west"), (ix1, "east")):
            if (x, z) != (lx, lz) and abs(x - lx) + abs(z - lz) > 1:
                stairs(b, x, TOP, z, STONE_BRICK, facing, top=True)
    chest(b, ix0, LOW, iz1, "east")
    chest(b, ix0 + 1, LOW, iz0, "south") if (ix0 + 1, iz0) != (lx, lz) and abs(ix0 + 1 - lx) > 1 else None
    cx, cz = (ix0 + ix1) // 2, (iz0 + iz1) // 2
    slab(b, cx, LOW, cz, SPRUCE, top=True)
    candle(b, cx, MID, cz, 3)
    chair(b, cx - 1, LOW, cz, SPRUCE, "east")
    chair(b, cx + 1, LOW, cz, SPRUCE, "west")
    hang_light(b, cx, cz - 1)
    hang_light(b, cx, cz + 1)


def winery_3():
    """Upgrade of the Winery II: a barrel vault under the barrel cellar, down a ladder in its north-west corner. 13 x 14 x
    20, 4 below."""
    from workshops import winery_2
    b = winery_2().grow(13, 14, 20, basement=BASEMENT)
    barrel_vault(b, 2, 3, 10, 9, ladder=(3, 4, "south"))
    b.fill_air()
    return b


def farmstead_3():
    """Upgrade of the Farmstead II: a root cellar under the farmhouse, down a ladder by the kitchen. 15 x 12 x 22, 4
    below."""
    from countryside import farmstead_2
    b = farmstead_2().grow(15, 12, 22, basement=BASEMENT)
    root_cellar(b, 1, 2, 7, 8, ladder=(6, 4, "west"))
    b.fill_air()
    return b


def cage_jamb(b, x, z):
    """A stone post at the side of a cage's way in (x, z), from the floor to the bars over the way in: the bars beside
    the opening join it, not the air (B648: the last bars stood as a thin strip ending in the doorway)."""
    for y in (LOW, MID, TOP):
        b.set(x, y, z, "stone_bricks")


def strongroom(b, x0, z0, x1, z1, ladder):
    """A guild's strongroom (walls x0..x1, z0..z1): its treasure caged behind iron bars at the north end (gold, emerald,
    lapis, chests of the guild's dues), a counting table with a candle and chairs, the ledgers' chests two high along
    the back wall; lanterns."""
    cellar(b, x0, z0, x1, z1, wall=MOSSY_BRICK_MIX, floor="polished_andesite", ladder=ladder, beam="dark_oak_log")
    ix0, iz0, ix1, iz1 = x0 + 1, z0 + 1, x1 - 1, z1 - 1
    cage = iz0 + 2  # the bars' row
    for x in range(ix0 + 1, ix1 + 1):
        for y in (LOW, MID, TOP):
            b.set(x, y, cage, "iron_bars", waterlogged=False)
    b.set(ix0, TOP, cage, "iron_bars", waterlogged=False)  # over the cage's way in, by the west wall
    cage_jamb(b, ix0 + 1, cage)
    for x, (low, mid) in zip(range(ix0 + 1, ix1 + 1), (("gold_block", None), ("emerald_block", "gold_block"),
                                                        ("lapis_block", None), ("chest", "chest"), ("chest", None))):
        for y, what in ((LOW, low), (MID, mid)):
            if what == "chest":
                chest(b, x, y, iz0, "south")
            elif what:
                b.set(x, y, iz0, what)
    hang_light(b, ix0 + 2, iz0 + 1)
    # The room: the ledgers along the back wall, the counting table in the middle
    for x in range(ix0 + 1, ix1 + 1):
        for y in (LOW, MID):
            chest(b, x, y, iz1, "north")
    cx, cz = (ix0 + ix1) // 2 + 1, cage + 2
    for x in (cx, cx + 1):
        slab(b, x, LOW, cz, DARK_OAK, top=True)
        chair(b, x, LOW, cz + 1, DARK_OAK, "north")
    candle(b, cx, MID, cz, 3)
    hang_light(b, cx, cz)
    hang_light(b, cx + 1, iz1 - 1)


def guildhall_3():
    """Upgrade of the Guildhall II: a strongroom under the hall's west end, down a ladder by the west wall. 26 x 20 x 14,
    4 below."""
    from guildhall import guildhall_2
    b = guildhall_2().grow(26, 20, 14, basement=BASEMENT)
    b.entities = list(getattr(guildhall_2(), "entities", []))
    strongroom(b, 1, 4, 8, 12, ladder=(2, 8, "east"))
    b.fill_air()
    return b


def barracks_3():
    """Upgrade of the Barracks II: an armoury and lock-up under the barracks, down a ladder by the east wall. 19 x 10 x
    11, 4 below."""
    from defence import barracks_2
    b = barracks_2().grow(19, 10, 11, basement=BASEMENT)
    armoury(b, 1, 2, 11, 8, ladder=(10, 6, "west"))
    b.fill_air()
    return b


def lower_lab(b, x0, z0, x1, z1, ladder):
    """A scholar's lower lab (walls x0..x1, z0..z1): bookshelves down the west wall, a stone bench along the back with
    specimens on it (amethyst, tinted jars, a skull of bone, potted cacti and fungi), chests of samples on the east
    wall, a work table with candles in the middle; lanterns. No lectern down here (it would give someone a job)."""
    cellar(b, x0, z0, x1, z1, wall=Mix((5, "stone_bricks"), (3, "polished_andesite"), (1, "cracked_stone_bricks"), seed=66),
           floor="polished_andesite", ladder=ladder, beam="stripped_spruce_log")
    ix0, iz0, ix1, iz1 = x0 + 1, z0 + 1, x1 - 1, z1 - 1
    lx, lz = ladder[0], ladder[1]
    for z in range(iz0, iz1):
        b.set(ix0, LOW, z, "bookshelf")
        b.set(ix0, MID, z, "bookshelf")
    specimens = [("amethyst_cluster", {"facing": "up", "waterlogged": False}), ("tinted_glass", {}), ("potted_cactus", {}),
                 ("bone_block", {"axis": "y"}), ("potted_crimson_fungus", {}), ("tinted_glass", {})]
    for i, x in enumerate(range(ix0, ix1 + 1)):
        b.set(x, LOW, iz1, "polished_andesite")
        name, props = specimens[i % len(specimens)]
        b.set(x, MID, iz1, name, **props)
    for z in range(iz0 + 2, iz1):
        chest(b, ix1, LOW, z, "west")
    cx, cz = (ix0 + ix1) // 2, (iz0 + iz1) // 2
    for x in (cx - 1, cx, cx + 1):
        b.set(x, LOW, cz, "smooth_stone_slab", type="top", waterlogged=False)
    candle(b, cx - 1, MID, cz, 2)
    b.set(cx + 1, MID, cz, "amethyst_cluster", facing="up", waterlogged=False)
    hang_light(b, cx - 1, cz)
    hang_light(b, cx + 1, cz)
    hang_light(b, cx, iz1 - 1)


def research_lab_3():
    """Upgrade of the Research Lab II: a lower lab under the lab, down a ladder by its front wall. 18 x 10 x 11, 4
    below."""
    from starter import research_lab_2
    b = research_lab_2().grow(18, 10, 11, basement=BASEMENT)
    lower_lab(b, 1, 1, 9, 9, ladder=(8, 2, "south"))
    b.fill_air()
    return b


def jewel_vault(b, x0, z0, x1, z1, ladder):
    """A jeweller's vault (walls x0..x1, z0..z1): the stock caged at the back behind iron bars (gold, raw gold, amethyst,
    emerald, chests of cut stones on top), chests of rough stones along the east wall, a sorting table with a candle and
    two chairs; lanterns."""
    cellar(b, x0, z0, x1, z1, wall=MOSSY_BRICK_MIX, floor="polished_andesite", ladder=ladder, beam="stripped_spruce_log")
    ix0, iz0, ix1, iz1 = x0 + 1, z0 + 1, x1 - 1, z1 - 1
    for x, (low, mid) in zip(range(ix0 + 1, ix1 + 1), (("gold_block", "chest"), ("amethyst_block", "chest"),
                                                        ("raw_gold_block", "chest"), ("emerald_block", "amethyst_cluster"))):
        b.set(x, LOW, iz1, low)
        if mid == "chest":
            chest(b, x, MID, iz1, "north")
        else:
            b.set(x, MID, iz1, mid, facing="up", waterlogged=False)
    for x in range(ix0 + 1, ix1 + 1):
        for y in (LOW, MID, TOP):
            b.set(x, y, iz1 - 1, "iron_bars", waterlogged=False)
    b.set(ix0, TOP, iz1 - 1, "iron_bars", waterlogged=False)  # over the way into the cage, by the west wall
    cage_jamb(b, ix0 + 1, iz1 - 1)
    for z in range(iz0 + 1, iz1 - 1):
        chest(b, ix1, LOW, z, "west")
    tx, tz = ix0 + 2, iz0 + 1
    slab(b, tx, LOW, tz, SPRUCE, top=True)
    candle(b, tx, MID, tz, 2)
    chair(b, tx - 1, LOW, tz, SPRUCE, "east") if (tx - 1, tz) != (ladder[0], ladder[1]) else None
    chair(b, tx, LOW, tz + 1, SPRUCE, "north")
    hang_light(b, tx + 1, tz)
    hang_light(b, ix0 + 1, iz1 - 2)


def jewellers_workshop_3():
    """Upgrade of the Jeweller's Workshop II: a vault under the shop, down a ladder in its north-west corner. 9 x 10 x
    14, 4 below."""
    from workshops import jewellers_workshop_2
    b = jewellers_workshop_2().grow(9, 10, 14, basement=BASEMENT)
    jewel_vault(b, 1, 1, 7, 7, ladder=(2, 2, "south"))
    b.fill_air()
    return b


def artisans_house_3():
    """Upgrade of the Artisan's House II: a storeroom under the house (chests, bolts of cloth, crates, a packing table),
    down a ladder in the back corner of the hall. 9 x 15 x 18, 4 below."""
    from houses import artisans_house_2
    b = artisans_house_2().grow(9, 15, 18, basement=BASEMENT)
    store_room(b, 1, 2, 7, 10, ladder=(2, 9, "north"), cloth=("brown_wool", "light_gray_wool"))
    b.fill_air()
    return b


LEVEL_3 = (("inn_3", inn_3), ("winery_3", winery_3), ("farmstead_3", farmstead_3), ("guildhall_3", guildhall_3),
           ("barracks_3", barracks_3), ("research_lab_3", research_lab_3), ("jewellers_workshop_3", jewellers_workshop_3),
           ("artisans_house_3", artisans_house_3))
