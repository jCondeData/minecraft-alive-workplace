"""
The Bandit King's fort (ROADMAP 31.15), set down a stage a dawn by the Bandit King's story arc (guard/BanditFort.java),
each stage standing on the one before like a blueprint upgrade (same 29 x 18 x 29 box, same origin, same front):

  story/bandit_fort    a palisade of sharpened spruce logs round a yard of trampled earth, a gate at the front (z 0)
                       standing open under a log lintel, a fire ringed with seats, four tents, a horse pen, a cart and a
                       log pile where the keep will stand;
  story/bandit_fort_2  two watchtowers on the front corners (log posts on stone footings, a plank platform reached by a
                       ladder, a fence parapet and a dark oak roof) and a lean-to stable where the pen was;
  story/bandit_fort_3  the King's keep at the back of the yard: a 13 x 9 hall, cobblestone to the shoulder on a stone
                       plinth, spruce frame and plank infill above, a dark oak gable roof with its ridge along the long
                       side, a stone chimney; his war banner (black, a red skull) over the door, a throne on a dais
                       between two more, the treasure chest (chests/bandit_king), a store lean-to on its west side, and a
                       postern door in the palisade behind it (shut in the file; the fort's code may open it).

Design on paper: main mass the keep (13 x 9, eaves at y 8, ridge at y 13), secondary masses the towers (5 x 5, roof at
y 8 to 10) and the lean-tos (stable on the east palisade, store on the keep's west wall). Palette: cobblestone and stone
low, spruce logs and planks, dark oak roofs; wool for the tents. The bottom layer (y 0) is the ground: where nothing is
drawn there, the world's ground stays.
"""
from kit import *
from bandits import tent, TRAMPLED
from nbtlib import Compound, List, String

W, H, D = 29, 18, 29
GATE = (13, 14, 15)
KING_BANNER = Compound({"id": String("minecraft:banner"), "patterns": List[Compound]([
    Compound({"color": String("red"), "pattern": String("minecraft:skull")}),
    Compound({"color": String("red"), "pattern": String("minecraft:border")})])})


def stake(b, x, z, tall, post=False):
    """A palisade stake on the ground: a spruce log from y 1, sharpened with a fence post (every other one taller; a
    post, every seventh, taller again and stripped at the foot)."""
    top = 5 if post else 4 if tall else 3
    for y in range(1, top + 1):
        log(b, x, y, z, "spruce_log")
    fence(b, x, top + 1, z, "spruce_fence")


def war_banner(b, x, y, z, **props):
    """The King's war banner: black with a red skull and border (standing, or on a wall with facing=...)."""
    b.set_nbt(x, y, z, KING_BANNER)
    b.set(x, y, z, "black_wall_banner" if "facing" in props else "black_banner", **props)


def bandit_fort():
    b = Build(W, H, D)
    # the yard: trampled earth inside the palisade, a path from the gate to the fire and on to the back
    for x in range(1, W - 1):
        for z in range(1, D - 1):
            b.set(x, 0, z, TRAMPLED.at(x, 0, z))
    for z in range(0, 16):
        for x in GATE:
            b.set(x, 0, z, "dirt_path")
    # the palisade: a ring of stakes, every other one taller, on coarse dirt
    for x in range(W):
        for z in range(D):
            if x in (0, W - 1) or z in (0, D - 1):
                b.set(x, 0, z, STONE_MIX.at(x, 0, z))
                if not (z == 0 and x in GATE):
                    stake(b, x, z, (x + z) % 2 == 0, post=(x % 7 == 0 and z in (0, D - 1)) or (z % 7 == 0 and x in (0, W - 1)))
    # the gate: tall posts either side, a log lintel with points over it, the gates standing open, lanterns under it
    for x in (12, 16):
        for y in range(1, 6):
            log(b, x, y, 0, "spruce_log")
        fence(b, x, 6, 0, "spruce_fence")
        b.set(x, 7, 0, "skeleton_skull", rotation="0")
    for x in GATE:
        log(b, x, 5, 0, "stripped_spruce_log", axis="x")
        fence(b, x, 6, 0, "spruce_fence")
        b.set(x, 1, 0, "spruce_fence_gate", facing="north", open=True, powered=False, in_wall=False)
    lantern(b, 13, 4, 0, hanging=True)
    lantern(b, 15, 4, 0, hanging=True)
    # the fire in the middle of the yard, ringed with stones, logs to sit on
    b.set(14, 0, 11, "cobblestone")
    b.set(14, 1, 11, "campfire", facing="south", lit=True, signal_fire=False, waterlogged=False)
    for x, z in ((13, 11), (15, 11), (14, 10), (14, 12)):
        slab(b, x, 1, z, COBBLE)
    for z in (10, 11, 12):
        log(b, 11, 1, z, "stripped_spruce_log", axis="z")
        log(b, 17, 1, z, "stripped_spruce_log", axis="z")
    # four tents, two each side of the path, their open fronts to the yard's middle
    tent(b, 3, 5, 5, "brown_wool", "stripped_dark_oak_log")
    tent(b, 21, 5, 5, "gray_wool", "stripped_dark_oak_log")
    tent(b, 3, 13, 5, "brown_wool", "stripped_dark_oak_log")
    b.bed(4, 1, 6, "brown", facing="south")
    b.bed(6, 1, 6, "brown", facing="south")
    b.bed(22, 1, 6, "gray", facing="south")
    b.bed(24, 1, 6, "gray", facing="south")
    b.bed(5, 1, 14, "brown", facing="south")
    # a horse pen on the east side, where the stable will be
    for z in range(14, 23):
        fence(b, 21, 1, z, "spruce_fence")
    for x in range(22, 28):
        fence(b, x, 1, 14, "spruce_fence")
        fence(b, x, 1, 22, "spruce_fence")
    b.set(21, 1, 18, "spruce_fence_gate", facing="east", open=False, powered=False, in_wall=False)
    b.set(26, 1, 16, "hay_block", axis="y")
    b.set(26, 1, 20, "hay_block", axis="x")
    b.set(27, 1, 18, "water_cauldron", level=3)
    # a cart of plunder by the gate, barrels and a target
    for x in (3, 4, 5):
        b.set(x, 1, 2, "spruce_planks")
    trapdoor(b, 3, 1, 1, "dark_oak_trapdoor", "north", open_=True)
    trapdoor(b, 5, 1, 1, "dark_oak_trapdoor", "north", open_=True)
    b.set(3, 2, 2, "hay_block", axis="x")
    b.set(4, 2, 2, "pumpkin")
    b.set(5, 2, 2, "barrel", facing="up", open=False)
    b.set(23, 1, 2, "barrel", facing="up", open=False)
    b.set(24, 1, 2, "barrel", facing="north", open=False)
    b.set(23, 2, 2, "barrel", facing="up", open=False)
    b.set(22, 1, 2, "target", power=0)
    # the log pile and a chopping block where the keep will stand
    for x in range(10, 15):
        for y in (1, 2):
            log(b, x, y, 21, "spruce_log", axis="x")
            log(b, x, y if y == 1 else 1, 22, "spruce_log", axis="x")
    b.set(17, 1, 21, "stripped_oak_log", axis="y")
    # lamp posts by the tents
    for x, z in ((9, 7), (19, 7), (9, 15)):
        fence(b, x, 1, z, "spruce_fence")
        lantern(b, x, 2, z)
    b.fill_air()
    # where nothing is drawn in the ground layer, the world's ground stays
    for x in range(W):
        for z in range(D):
            if b.blocks.get((x, 0, z), ("",))[0] == "minecraft:air":
                del b.blocks[(x, 0, z)]
    return b


def watchtower(b, x0, ladder_x, ladder_facing):
    """A 5 x 5 watchtower on a front corner of the palisade (x0 its west side, z 0 to 4)."""
    corners = ((x0, 0), (x0 + 4, 0), (x0, 4), (x0 + 4, 4))
    for x, z in corners:
        b.set(x, 0, z, STONE_MIX.at(x, 0, z))
    posts(b, corners, 1, 7, "spruce_log")
    beam_ring(b, x0, 0, x0 + 4, 4, 5, "stripped_spruce_log")
    box(b, x0 + 1, 5, 1, x0 + 3, 5, 3, "spruce_planks")
    b.set(ladder_x, 5, 2, "air")
    for y in range(1, 6):
        b.set(ladder_x, y, 2, "ladder", facing=ladder_facing, waterlogged=False)
    for i in (1, 2, 3):
        for x, z in ((x0 + i, 0), (x0 + i, 4), (x0, i), (x0 + 4, i)):
            fence(b, x, 6, z, "spruce_fence")
    # the roof on the posts: a low pyramid of dark oak stairs, a slab cap, a lantern under it
    for i in range(5):
        stairs(b, x0 + i, 8, 0, DARK_OAK, "south")
        stairs(b, x0 + i, 8, 4, DARK_OAK, "north")
    for i in (1, 2, 3):
        stairs(b, x0, 8, i, DARK_OAK, "east")
        stairs(b, x0 + 4, 8, i, DARK_OAK, "west")
    for i in (1, 2, 3):
        stairs(b, x0 + i, 9, 1, DARK_OAK, "south")
        stairs(b, x0 + i, 9, 3, DARK_OAK, "north")
    stairs(b, x0 + 1, 9, 2, DARK_OAK, "east")
    stairs(b, x0 + 3, 9, 2, DARK_OAK, "west")
    slab(b, x0 + 2, 10, 2, DARK_OAK)
    b.set(x0 + 2, 9, 2, "spruce_planks")
    lantern(b, x0 + 2, 8, 2, hanging=True)


def bandit_fort_2():
    b = bandit_fort().grow(W, H, D)
    # the two watchtowers on the front corners, ladders up the palisade inside
    watchtower(b, 0, 1, "east")
    watchtower(b, W - 5, W - 2, "west")
    # the stable: a lean-to against the east palisade where the pen was, two stalls
    b.clear(21, 1, 14, 27, 1, 22)
    for z in range(14, 23):
        for x in range(21, 28):
            b.set(x, 0, z, Mix((3, "coarse_dirt"), (1, "dirt"), seed=77).at(x, 0, z))
    posts(b, ((21, 14), (21, 18), (21, 22)), 1, 3, "spruce_log")
    for z in range(14, 23):
        log(b, 21, 4, z, "spruce_log", axis="z")
    for x in range(22, 28):
        for y in range(1, 4):
            b.set(x, y, 14, "spruce_planks")
            b.set(x, y, 22, "spruce_planks")
        log(b, x, 4, 14, "spruce_log", axis="x")
        log(b, x, 4, 22, "spruce_log", axis="x")
        fence(b, x, 1, 18, "spruce_fence")
    for z in (15, 17, 19, 21):
        fence(b, 21, 1, z, "spruce_fence")
    b.set(21, 1, 16, "spruce_fence_gate", facing="east", open=False, powered=False, in_wall=False)
    b.set(21, 1, 20, "spruce_fence_gate", facing="east", open=False, powered=False, in_wall=False)
    # the lean-to roof: dark oak slabs from the palisade, a stair eave over the yard
    for z in range(13, 24):
        stairs(b, 20, 4, z, DARK_OAK, "east")
        for x in range(21, 28):
            slab(b, x, 5, z, DARK_OAK)
    # stalls: hay, water, lanterns from the roof
    b.set(27, 1, 15, "hay_block", axis="y")
    b.set(27, 1, 19, "hay_block", axis="y")
    b.set(27, 1, 16, "water_cauldron", level=3)
    b.set(27, 1, 20, "water_cauldron", level=3)
    lantern(b, 24, 4, 16, hanging=True)
    lantern(b, 24, 4, 20, hanging=True)
    b.fill_air()
    for x in range(W):
        for z in range(D):
            if b.blocks.get((x, 0, z), ("",))[0] == "minecraft:air":
                del b.blocks[(x, 0, z)]
    return b


def bandit_fort_3():
    b = bandit_fort_2().grow(W, H, D)
    x0, x1, z0, z1 = 8, 20, 17, 25
    b.clear(9, 1, 19, 19, 3, 24)  # the log pile goes
    b.clear(17, 1, 21, 17, 1, 21)
    # the plinth and the floor
    plinth(b, x0, z0, x1, z1, STONE_MIX, floor="dark_oak_planks")
    # the lower walls: cobblestone to the shoulder, posts at the bays (door in the middle bay of the front)
    walls(b, x0, z0, x1, z1, 1, 3, Mix((7, "cobblestone"), (2, "stone"), (1, "mossy_cobblestone"), seed=31))
    walls(b, x0, z0, x1, z1, 5, 7, "stripped_birch_wood", axis="y")
    beam_ring(b, x0, z0, x1, z1, 4, "dark_oak_log")
    beam_ring(b, x0, z0, x1, z1, 8, "dark_oak_log")
    frame = [(x, z) for x in (x0, 12, 16, x1) for z in (z0, z1)] + [(x, z) for x in (x0, x1) for z in (21,)]
    posts(b, frame, 1, 7, "spruce_log")
    # windows centred in the bays, stacked: shuttered below, small above
    for x in (10, 18):
        window(b, x, 2, z0, "north", shutters="dark_oak_trapdoor", sill=SPRUCE)
        window(b, x, 6, z0, "north")
        window(b, x, 2, z1, "south", shutters="dark_oak_trapdoor", sill=SPRUCE)
        window(b, x, 6, z1, "south")
    for z in (19, 23):
        window(b, x1, 2, z, "east", shutters="dark_oak_trapdoor", sill=SPRUCE)
        window(b, x1, 6, z, "east")
    # the door, its step, the war banner over it, lanterns on posts either side
    b.door(14, 1, z0, "dark_oak_door", "south")
    stairs(b, 14, 0, z0 - 1, COBBLE, "south")
    war_banner(b, 14, 4, z0 - 1, facing="north")
    b.set(14, 3, z0, "chiseled_stone_bricks")
    for x in (12, 16):
        fence(b, x, 1, z0 - 1, "dark_oak_fence")
        lantern(b, x, 2, z0 - 1)
    for z in range(15, z0 - 1):
        b.set(14, 0, z, "dirt_path")
    # the roof: dark oak along the long side, a spruce eave trim, the gables in planks
    gable_roof(b, x0 - 1, x1 + 1, z0 - 1, z1 + 1, 8, DARK_OAK, axis="x", gable="birch_planks", gable_at=(x0, x1),
               ridge=DARK_OAK, eave_trim=SPRUCE)
    for x in (x0, x1):
        b.set(x, 11, 21, "spruce_log", axis="y")
    # the chimney up the back wall, and the store lean-to on the west wall
    chimney(b, 13, z1 + 1, 0, 14)
    for z in range(19, 24):
        for y in (1, 2):
            b.set(5, y, z, "spruce_planks")
        b.set(5, 0, z, STONE_MIX.at(5, 0, z))
    for x in (5, 6, 7):
        for y in (1, 2):
            b.set(x, y, 19, "spruce_planks")
            b.set(x, y, 23, "spruce_planks")
        b.set(x, 0, 19, STONE_MIX.at(x, 0, 19))
        b.set(x, 0, 23, STONE_MIX.at(x, 0, 23))
    posts(b, ((5, 19), (5, 23)), 1, 3, "spruce_log")
    b.door(5, 1, 21, "spruce_door", "east")
    b.set(4, 0, 21, "cobblestone_stairs", facing="east", half="bottom", shape="straight", waterlogged=False)
    lantern(b, 4, 1, 20)
    for z in range(19, 24):
        b.set(5, 3, z, "spruce_planks")
    for z in (19, 23):
        for x, y in ((6, 3), (6, 4), (7, 3), (7, 4), (7, 5)):
            b.set(x, y, z, "spruce_planks")
    for z in range(18, 25):
        stairs(b, 4, 3, z, DARK_OAK, "east")
        stairs(b, 5, 4, z, DARK_OAK, "east")
        stairs(b, 6, 5, z, DARK_OAK, "east")
        stairs(b, 7, 6, z, DARK_OAK, "east")
    b.set(6, 1, 20, "barrel", facing="up", open=False)
    b.set(6, 1, 22, "barrel", facing="up", open=False)
    b.set(7, 1, 22, "barrel", facing="up", open=False)
    # inside: a red runner to the throne on its dais, war banners either side, the treasure chest by it
    for z in range(z0 + 1, z1 - 2):
        b.set(14, 1, z, "red_carpet")
    for x in range(12, 17):
        slab(b, x, 1, 23, SPRUCE)
        slab(b, x, 1, 24, SPRUCE)
    stairs(b, 14, 2, 24, DARK_OAK, "south")
    b.set(13, 2, 24, "gold_block")
    b.set(15, 2, 24, "gold_block")
    war_banner(b, 12, 2, 24, rotation="8")
    war_banner(b, 16, 2, 24, rotation="8")
    b.set_nbt(18, 1, 24, Compound({"LootTable": String("aliveworkplace:chests/bandit_king"), "id": String("minecraft:chest")}))
    b.set(18, 1, 24, "chest", facing="north", type="single", waterlogged=False)
    b.set(19, 1, 24, "barrel", facing="up", open=False)
    b.set(10, 1, 24, "barrel", facing="up", open=False)
    # a long table down the hall, tie beams with lanterns
    for z in (19, 20, 21):
        log(b, 10, 1, z, "stripped_spruce_log", axis="z")
    for x in range(x0 + 1, x1):
        log(b, x, 8, 21, "dark_oak_log", axis="x")
    lantern(b, 11, 7, 21, hanging=True)
    lantern(b, 17, 7, 21, hanging=True)
    # the postern: a door in the palisade behind the keep, a path round to it
    for y in range(1, 7):
        b.blocks.pop((14, y, D - 1), None)
    b.door(14, 1, D - 1, "spruce_door", "south")
    log(b, 14, 3, D - 1, "stripped_spruce_log", axis="x")
    log(b, 14, 4, D - 1, "spruce_log")
    fence(b, 14, 5, D - 1, "spruce_fence")
    for x in range(13, 16):
        b.set(x, 0, D - 2, "dirt_path")
    b.fill_air()
    for x in range(W):
        for z in range(D):
            if b.blocks.get((x, 0, z), ("",))[0] == "minecraft:air":
                del b.blocks[(x, 0, z)]
    return b
