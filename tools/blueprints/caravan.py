"""
The Lost Caravan (ROADMAP 31.19): the builds of its trail, set down by the arc engine (story/ArcEffects, rule `road`,
`after` and `between`) once a player comes within 96 blocks. Each one's bottom layer is the ground: where nothing is
drawn there, the world's ground stays, so they lie on whatever the land is.

  story/caravan_wreck     13 x 7 x 12: the settlers' covered wagon on its side, its floor standing up like a wall, its
                          canvas torn at the back and hoops showing, the upper wheels lying flat in the air (one gone,
                          broken in the grass), crates (composters), barrels, hay and a sack spilled out of its open end,
                          a lantern in the grass, the ruts where it slid off the road;
  story/raiders_stockade  19 x 9 x 17: a pillager stockade, a palisade of sharpened dark oak round a trampled yard, its gate
                          standing open under the ominous banner, a tent, a fire ringed with log seats, a loot chest
                          (the bandit camps' loot, chests/bandit_camp), a cage of iron bars with an iron gate and a lever outside it (the
                          captive's, 31.20), an archery target and a fletching table;
  story/trail_1 .. _5     3 x 4 x 3 marks between them: trampled coarse dirt and a dropped lantern; a smashed barrel; a
                          torn white banner on a fence post; cart ruts of path blocks; an arrow-studded crate.

Design on paper: the wreck's main mass is the wagon (7 long, its canvas arching 4 out from the floor), the secondary
masses the flat wheels and the spill; the stockade's main mass the palisade ring (19 x 17, stakes 3 to 5 high), the
tent and the cage the secondary ones. Palette: spruce and white wool for the settlers, dark oak, gray wool and iron for
the raiders; trampled earth under both.
"""
from kit import *
from bandits import tent, TRAMPLED, ominous_banner
from nbtlib import Compound, List, String

SCUFFED = Mix((4, "coarse_dirt"), (3, "dirt"), (2, "grass_block"), (1, "dirt_path"), seed=59)
TORN_BANNER = Compound({"id": String("minecraft:banner"), "patterns": List[Compound]([
    Compound({"color": String("light_gray"), "pattern": String("minecraft:triangles_bottom")}),
    Compound({"color": String("light_gray"), "pattern": String("minecraft:curly_border")}),
    Compound({"color": String("brown"), "pattern": String("minecraft:stripe_bottom")})])})


def keep_ground(b):
    """Saves the build with the world's own ground where nothing is drawn in the bottom layer."""
    b.fill_air()
    for x in range(b.w):
        for z in range(b.d):
            if b.blocks.get((x, 0, z), ("",))[0] == "minecraft:air":
                del b.blocks[(x, 0, z)]
    return b


def flat_wheel(b, x, y, z, broken=()):
    """A wagon wheel lying flat (its axle upright): a hub of stripped dark oak, a rim of closed trapdoors round it."""
    log(b, x, y, z, "stripped_dark_oak_log", axis="y")
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if (dx, dz) != (0, 0) and (dx, dz) not in broken:
                trapdoor(b, x + dx, y, z + dz, "dark_oak_trapdoor", "north")


def caravan_wreck():
    W, H, D = 13, 7, 12
    b = Build(W, H, D)
    # the ground: a scuffed oval where the wagon came to rest, the ruts where it slid off the road (front, z 0)
    for x in range(W):
        for z in range(D):
            dx, dz = (x - 6) / 6.5, (z - 5.5) / 6.0
            if dx * dx + dz * dz <= 1.0:
                b.set(x, 0, z, SCUFFED.at(x, 0, z))
    for z in range(0, 3):
        b.set(5, 0, z, "dirt_path")
        b.set(8, 0, z, "dirt_path")
    z0, z1 = 2, 8
    for z in range(z0, z1 + 1):
        # the floor, standing up on its side: planks between two rails
        log(b, 6, 1, z, "stripped_spruce_log", axis="z")
        b.set(6, 2, z, "spruce_planks")
        log(b, 6, 3, z, "stripped_spruce_log", axis="z")
        # the sideboards, one lying on the ground, one over the hollow
        slab(b, 4, 1, z, SPRUCE)
        slab(b, 5, 1, z, SPRUCE)
        slab(b, 4, 3, z, SPRUCE, top=True)
        slab(b, 5, 3, z, SPRUCE, top=True)
        # the canvas arching out from the sideboards
        for x, y in ((3, 1), (2, 1), (2, 2), (2, 3), (3, 4), (4, 4)):
            b.set(x, y, z, "white_wool")
        # the reach pole under the floor
        log(b, 7, 2, z, "stripped_spruce_log", axis="z")
    # torn: a hole in the side, and at the back the canvas gone, its hoop showing, scraps blown into the grass
    b.blocks.pop((2, 2, 4))
    for z in (z1,):
        for x, y in ((2, 2), (2, 3), (3, 4)):
            b.blocks.pop((x, y, z))
        fence(b, 2, 2, z, "spruce_fence")
        fence(b, 2, 3, z, "spruce_fence")
    for x, z in ((0, 9), (1, 10), (3, 10)):
        b.set(x, 0, z, SCUFFED.at(x, 0, z))
    b.set(0, 1, 9, "white_carpet")
    b.set(1, 1, 10, "white_carpet")
    b.set(3, 1, 10, "white_carpet")
    # inside the hollow: what didn't fall out
    b.set(3, 2, 6, "hay_block", axis="z")
    b.set(4, 2, 5, "barrel", facing="east", open=False)
    # the wheels: the lower pair pressed flat in the grass, the upper pair in the air, one of them broken off
    for z in (3, 7):
        log(b, 7, 1, z, "stripped_dark_oak_log", axis="y")
        for dz in (-1, 0, 1):
            trapdoor(b, 8, 1, z + dz, "dark_oak_trapdoor", "north")
        fence(b, 7, 3, z, "dark_oak_fence")
    flat_wheel(b, 7, 4, 3)
    # the broken wheel in the grass: its hub on its side, half its rim, a spoke snapped off
    log(b, 10, 1, 7, "stripped_dark_oak_log", axis="x")
    for x, z in ((9, 6), (10, 6), (11, 7), (11, 8)):
        trapdoor(b, x, 1, z, "dark_oak_trapdoor", "north")
    trapdoor(b, 9, 1, 9, "dark_oak_trapdoor", "east", open_=True)
    # the shaft, snapped, lying askew before it
    log(b, 7, 1, 1, "stripped_spruce_log", axis="z")
    log(b, 8, 1, 0, "stripped_spruce_log", axis="z")
    # the spill out of the open front: barrels on their sides, crates, a split one, hay, a sack, a pumpkin
    b.set(4, 1, 1, "barrel", facing="north", open=False)
    b.set(3, 1, 0, "barrel", facing="west", open=True)
    b.set(2, 1, 1, "composter", level=0)
    b.set(1, 1, 3, "composter", level=0)
    b.set(1, 2, 3, "spruce_trapdoor", facing="west", half="bottom", open=False, powered=False, waterlogged=False)
    trapdoor(b, 0, 1, 5, "spruce_trapdoor", "east", open_=True)
    slab(b, 1, 1, 6, SPRUCE)
    b.set(5, 1, 0, "hay_block", axis="x")
    b.set(1, 1, 2, "brown_wool")
    b.set(10, 1, 2, "pumpkin")
    b.set(11, 1, 4, "barrel", facing="up", open=False)
    # the lantern that lit their way, dropped in the grass behind
    b.set(9, 0, 10, "grass_block", snowy=False)
    lantern(b, 9, 1, 10)
    return keep_ground(b)


def stake(b, x, z, tall):
    """A palisade stake: a dark oak log from y 1, every other one taller and sharpened with a fence post."""
    for y in range(1, 5 if tall else 4):
        log(b, x, y, z, "dark_oak_log")
    if tall:
        fence(b, x, 5, z, "dark_oak_fence")


CAGE = (12, 10, 16, 14)  # x0, z0, x1, z1: the cage at the back right of the yard
CAGE_DOOR = (14, 1, 10)
LOOT_CHEST = (7, 1, 14)


def raiders_stockade():
    W, H, D = 19, 9, 17
    b = Build(W, H, D)
    gate = (8, 9, 10)
    for x in range(W):
        for z in range(D):
            ring = x in (0, W - 1) or z in (0, D - 1)
            b.set(x, 0, z, STONE_MIX.at(x, 0, z) if ring else TRAMPLED.at(x, 0, z))
            if ring and not (z == 0 and x in gate):
                stake(b, x, z, (x + z) % 2 == 0)
    for z in range(0, 9):
        for x in gate:
            b.set(x, 0, z, "dirt_path")
    # the gate: taller posts, a log lintel, the gates standing open, the ominous banner over it, lanterns under
    for x in (7, 11):
        for y in range(1, 6):
            log(b, x, y, 0, "dark_oak_log")
        fence(b, x, 6, 0, "dark_oak_fence")
    for x in gate:
        log(b, x, 5, 0, "stripped_dark_oak_log", axis="x")
        b.set(x, 1, 0, "dark_oak_fence_gate", facing="north", open=True, powered=False, in_wall=False)
    b.blocks.pop((9, 1, 0))  # the middle one hangs open
    ominous_banner(b, 9, 4, 0, facing="north")
    lantern(b, 8, 4, 0, hanging=True)
    lantern(b, 10, 4, 0, hanging=True)
    # the tent at the back left, its mouth to the yard, a bedroll in it
    tent(b, 2, 10, 5, "gray_wool", "stripped_dark_oak_log")
    b.bed(4, 1, 12, "gray", facing="south")
    # the fire in the middle, on stones, logs to sit on, the banner on its pole
    b.set(9, 0, 8, "cobblestone")
    b.set(9, 1, 8, "campfire", facing="south", lit=True, signal_fire=False, waterlogged=False)
    for x, z in ((8, 8), (10, 8), (9, 7), (9, 9)):
        slab(b, x, 1, z, COBBLE)
    log(b, 6, 1, 7, "stripped_dark_oak_log", axis="z")
    log(b, 6, 1, 8, "stripped_dark_oak_log", axis="z")
    log(b, 12, 1, 8, "stripped_dark_oak_log", axis="z")
    log(b, 12, 1, 9, "stripped_dark_oak_log", axis="z")
    for y in (1, 2, 3):
        fence(b, 11, y, 6, "dark_oak_fence")
    ominous_banner(b, 11, 4, 6, rotation="8")
    # the loot chest by the tent, barrels of plunder round it
    b.set_nbt(*LOOT_CHEST, Compound({"LootTable": String("aliveworkplace:chests/bandit_camp"), "id": String("minecraft:chest")}))
    b.set(*LOOT_CHEST, "chest", facing="north", type="single", waterlogged=False)
    b.set(8, 1, 15, "barrel", facing="up", open=False)
    b.set(7, 1, 15, "barrel", facing="north", open=False)
    b.set(8, 2, 15, "composter", level=0)
    # the cage: dark oak posts, iron bars, a lid of logs and slabs, an iron gate with a lever outside, straw inside
    x0, z0, x1, z1 = CAGE
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            b.set(x, 0, z, "cobblestone" if (x + z) % 3 else "mossy_cobblestone")
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        for y in (1, 2):
            log(b, x, y, z, "stripped_dark_oak_log")
    for y in (1, 2):
        for x in range(x0 + 1, x1):
            b.set(x, y, z0, "iron_bars")
            b.set(x, y, z1, "iron_bars")
        for z in range(z0 + 1, z1):
            b.set(x0, y, z, "iron_bars")
            b.set(x1, y, z, "iron_bars")
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                log(b, x, 3, z, "dark_oak_log", axis="x" if z in (z0, z1) else "z")
            else:
                slab(b, x, 3, z, DARK_OAK)
    b.door(*CAGE_DOOR, "iron_door", "north")
    b.set(CAGE_DOOR[0] + 1, 1, CAGE_DOOR[2] - 1, "stone_bricks")
    b.set(CAGE_DOOR[0] + 1, 2, CAGE_DOOR[2] - 1, "lever", face="floor", facing="north", powered=False)
    lantern(b, x0, 4, z0)
    b.set(15, 1, 13, "hay_block", axis="x")
    b.set(13, 1, 13, "cauldron")
    # a lookout in the front right corner: a plank platform on posts over the palisade, a ladder, a fence parapet
    for x, z in ((15, 1), (17, 1), (15, 3), (17, 3)):
        for y in range(1, 5):
            log(b, x, y, z, "dark_oak_log")
    for x in range(15, 18):
        for z in range(1, 4):
            if b.get(x, 4, z) is None:
                b.set(x, 4, z, "dark_oak_planks")
    for x in range(14, 19):
        for z in range(0, 5):
            if x in (14, 18) or z in (0, 4):
                if b.get(x, 5, z) is None or "fence" in b.get(x, 5, z)[0]:
                    fence(b, x, 5, z, "dark_oak_fence")
                if b.get(x, 4, z) is None:
                    slab(b, x, 4, z, DARK_OAK, top=True)
    for y in range(1, 4):
        log(b, 16, y, 3, "dark_oak_log")
    for y in range(1, 5):
        b.set(16, y, 4, "ladder", facing="south", waterlogged=False)
    b.blocks.pop((16, 5, 4), None)
    lantern(b, 15, 5, 1)
    # archery by the lookout, a fletching table and a crate by the gate
    b.set(13, 1, 2, "target", power=0)
    b.set(13, 1, 3, "hay_block", axis="y")
    b.set(12, 1, 3, "hay_block", axis="z")
    b.set(3, 1, 2, "fletching_table")
    b.set(2, 1, 2, "composter", level=0)
    b.set(2, 1, 3, "barrel", facing="up", open=False)
    # lamp posts by the fire
    for x, z in ((5, 5), (14, 6)):
        fence(b, x, 1, z, "dark_oak_fence")
        lantern(b, x, 2, z)
    b.fill_air()
    return b


def mark(fill_ground=TRAMPLED):
    b = Build(3, 4, 3)
    for x in range(3):
        for z in range(3):
            b.set(x, 0, z, fill_ground.at(x, 0, z))
    return b


def trail_1():
    """Trampled coarse dirt, hoofprints of path, and a lantern someone dropped."""
    b = mark(Mix((4, "coarse_dirt"), (2, "dirt_path"), (1, "dirt"), seed=61))
    lantern(b, 0, 1, 2)
    b.set(2, 0, 0, "coarse_dirt")
    b.set(2, 1, 0, "dead_bush")
    return keep_ground(b)


def trail_2():
    """A smashed barrel: the barrel on its side, staves and its lid scattered."""
    b = mark(Mix((3, "coarse_dirt"), (2, "dirt"), seed=62))
    b.set(1, 1, 1, "barrel", facing="west", open=True)
    slab(b, 0, 1, 0, SPRUCE)
    slab(b, 2, 1, 2, SPRUCE)
    trapdoor(b, 2, 1, 0, "spruce_trapdoor", "east")
    b.set(0, 0, 2, "coarse_dirt")
    b.set(0, 1, 2, "dead_bush")
    return keep_ground(b)


def trail_3():
    """A torn white banner on a fence post, a scrap of it in the dirt."""
    b = mark(Mix((3, "coarse_dirt"), (2, "dirt"), (1, "dirt_path"), seed=63))
    fence(b, 1, 1, 1, "spruce_fence")
    fence(b, 1, 2, 1, "spruce_fence")
    b.set_nbt(1, 3, 1, TORN_BANNER)
    b.set(1, 3, 1, "white_banner", rotation="6")
    b.set(2, 1, 0, "white_carpet")
    return keep_ground(b)


def trail_4():
    """Cart ruts: two lines of path with the middle left rough."""
    b = Build(3, 4, 3)
    for z in range(3):
        b.set(0, 0, z, "dirt_path")
        b.set(2, 0, z, "dirt_path")
        b.set(1, 0, z, "coarse_dirt" if z != 1 else "dirt")
    slab(b, 1, 1, 2, SPRUCE)
    return keep_ground(b)


def trail_5():
    """An arrow-studded crate, the arrows' shafts (tripwire hooks) in three of its sides, its lid knocked off."""
    b = mark(Mix((3, "coarse_dirt"), (2, "gravel"), (2, "dirt"), seed=65))
    b.set(1, 1, 1, "composter", level=0)
    for x, z, facing in ((0, 1, "west"), (1, 0, "north"), (2, 1, "east")):
        b.set(x, 1, z, "tripwire_hook", facing=facing, attached=False, powered=False)
    trapdoor(b, 1, 1, 2, "spruce_trapdoor", "south", open_=False)
    return keep_ground(b)


TRAIL = (trail_1, trail_2, trail_3, trail_4, trail_5)
