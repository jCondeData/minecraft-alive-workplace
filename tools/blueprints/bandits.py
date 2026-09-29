"""
The bandit camp: set down at once (not built) when bandits make camp near a village (see guard/BanditCamps). Its bottom
layer is the ground itself (trampled earth and paths; where nothing is drawn the world's ground stays), the rest stands on
it: two canvas tents, the chief's bigger tent with the loot chest, a fire ringed with log seats, a skull on a stake, a
cart of stolen goods and a lookout post.
"""
from kit import *
from nbtlib import Compound, List, String

TRAMPLED = Mix((5, "coarse_dirt"), (3, "dirt_path"), (2, "dirt"), (1, "gravel"), seed=41)


def tent(b, x0, z0, length, cloth, trim, open_front=True):
    """A ridge tent, five wide and three high, {@code length} deep from z0, open at the front (low z)."""
    for z in range(z0, z0 + length):
        b.set(x0, 1, z, cloth)
        b.set(x0 + 4, 1, z, cloth)
        b.set(x0 + 1, 2, z, cloth)
        b.set(x0 + 3, 2, z, cloth)
        b.set(x0 + 2, 3, z, trim)
    # the back closed; the ridge pole shows at the front
    back = z0 + length - 1
    for x in range(x0 + 1, x0 + 4):
        b.set(x, 1, back, cloth)
    b.set(x0 + 2, 2, back, cloth)
    fence(b, x0 + 2, 1, z0 - 1, "dark_oak_fence")
    fence(b, x0 + 2, 2, z0 - 1, "dark_oak_fence")
    if not open_front:
        b.set(x0 + 2, 2, z0, cloth)


def bandit_camp():
    """15 x 6 x 13: tents round a fire in a clearing of trampled earth."""
    b = Build(15, 6, 13)
    # the clearing: an oval of trampled earth, a path in from the front
    for x in range(15):
        for z in range(13):
            dx, dz = (x - 7) / 7.5, (z - 6) / 6.5
            if dx * dx + dz * dz <= 1.0:
                b.set(x, 0, z, TRAMPLED.at(x, 0, z))
    for z in range(0, 5):
        b.set(7, 0, z, "dirt_path")
    # the fire, ringed with stones, logs to sit on
    b.set(7, 0, 6, "cobblestone")
    b.set(7, 1, 6, "campfire", facing="south", lit=True, signal_fire=False, waterlogged=False)
    for x, z in ((6, 6), (8, 6), (7, 5), (7, 7)):
        slab(b, x, 1, z, COBBLE)
    log(b, 5, 1, 5, "stripped_spruce_log", axis="z")
    log(b, 5, 1, 6, "stripped_spruce_log", axis="z")
    log(b, 9, 1, 7, "stripped_spruce_log", axis="z")
    log(b, 9, 1, 6, "stripped_spruce_log", axis="z")
    # two tents on the left and right, the chief's at the back
    tent(b, 0, 2, 4, "brown_wool", "stripped_dark_oak_log")
    tent(b, 10, 2, 4, "gray_wool", "stripped_dark_oak_log")
    b.bed(1, 1, 4, "brown", facing="south")
    b.bed(3, 1, 4, "brown", facing="south")
    b.bed(11, 1, 4, "gray", facing="south")
    b.bed(13, 1, 4, "gray", facing="south")
    for z in range(8, 13):
        for x in (4, 10):
            b.set(x, 1, z, "red_wool")
        for x in (5, 9):
            b.set(x, 2, z, "red_wool")
        for x in (6, 8):
            b.set(x, 3, z, "red_wool")
        log(b, 7, 4, z, "stripped_dark_oak_log", axis="z")
    for x in range(5, 10):
        b.set(x, 1, 12, "red_wool")
    for x in (6, 7, 8):
        b.set(x, 2, 12, "red_wool")
    b.set(7, 3, 12, "red_wool")
    lantern(b, 7, 3, 10, hanging=True)
    b.set_nbt(7, 1, 11, Compound({"LootTable": String("aliveworkplace:chests/bandit_camp"), "id": String("minecraft:chest")}))
    b.set(7, 1, 11, "chest", facing="north", type="single", waterlogged=False)
    b.set(6, 1, 11, "hay_block", axis="x")
    b.bed(8, 1, 10, "red", facing="south")
    # the chief's banner beside the tent, a skull on a stake, a lookout post
    b.set_nbt(11, 1, 8, Compound({"id": String("minecraft:banner"), "patterns": List[Compound]([
        Compound({"color": String("red"), "pattern": String("minecraft:skull")}),
        Compound({"color": String("red"), "pattern": String("minecraft:border")})])}))
    b.set(11, 1, 8, "black_banner", rotation="0")
    fence(b, 3, 1, 8, "dark_oak_fence")
    b.set(3, 2, 8, "skeleton_skull", rotation="0")
    for y in (1, 2, 3):
        fence(b, 13, y, 10, "spruce_fence")
    lantern(b, 13, 4, 10)
    # a cart of stolen goods by the path
    for x in (3, 4, 5):
        b.set(x, 1, 1, "spruce_planks")
    trapdoor(b, 3, 1, 0, "dark_oak_trapdoor", "north", open_=True)
    trapdoor(b, 5, 1, 0, "dark_oak_trapdoor", "north", open_=True)
    b.set(3, 2, 1, "hay_block", axis="x")
    b.set(4, 2, 1, "pumpkin")
    b.set(5, 2, 1, "chest", facing="north", type="single", waterlogged=False)
    fence(b, 6, 1, 1, "dark_oak_fence")
    b.fill_air()
    # where nothing is drawn in the ground layer, the world's ground stays
    for x in range(15):
        for z in range(13):
            if b.blocks.get((x, 0, z), ("",))[0] == "minecraft:air":
                del b.blocks[(x, 0, z)]
    return b
