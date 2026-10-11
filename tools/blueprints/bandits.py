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


# The ominous banner, as vanilla's raid captains carry it (Raid.getLeaderBannerInstance).
OMINOUS = (("cyan", "rhombus"), ("light_gray", "stripe_bottom"), ("gray", "stripe_center"), ("light_gray", "border"),
           ("black", "stripe_middle"), ("light_gray", "half_horizontal"), ("light_gray", "circle"), ("black", "border"))


def ominous_banner(b, x, y, z, **props):
    """The ominous banner at (x, y, z): a standing one ({@code rotation}) or, with {@code facing}, one on a wall."""
    b.set_nbt(x, y, z, Compound({"id": String("minecraft:banner"), "patterns": List[Compound]([
        Compound({"color": String(c), "pattern": String("minecraft:" + p)}) for c, p in OMINOUS])}))
    b.set(x, y, z, "white_wall_banner" if "facing" in props else "white_banner", **props)


def war_camp():
    """
    The pillager warband's War Camp (ROADMAP 32.7), 23 x 9 x 19, set down at once like the bandit camp. Design:
    - a ring of sharpened spruce logs (3 and 4 high in turn, so the top is ragged), corners cut, a gateway in the middle
      of the front (z = 0) under a dark oak lintel, its gates swung open;
    - inside, trampled earth; the fire in the middle of the yard with log seats;
    - three canvas tents (white and light grey wool on dark oak) down the west side;
    - the Warlord's tent at the back, bigger and darker (grey and black wool, a dark oak ridge), the loot chest inside,
      the ominous banner over its door;
    - a ravager pen in the north-east corner: a dark oak fence with a gate, hay and a trough;
    - a siege yard in the south-east: logs stacked to dry, ladders leaning on a rack, a half-built ram on its sled;
    - a pole by the fire flying the ominous banner, the highest thing in the camp.
    """
    W, D = 23, 19
    b = Build(W, 9, D)
    camp_ground = Mix((5, "coarse_dirt"), (3, "dirt_path"), (2, "dirt"), (1, "gravel"), (1, "packed_mud"), seed=73)

    def inside(x, z):
        return 0 < x < W - 1 and 0 < z < D - 1 and not cut(x, z)

    def cut(x, z):  # the corners cut off the ring, two blocks each way
        return min(x, W - 1 - x) + min(z, D - 1 - z) < 2

    for x in range(W):
        for z in range(D):
            if inside(x, z):
                b.set(x, 0, z, camp_ground.at(x, 0, z))
    # the palisade: every block of the ring that isn't cut off and isn't the gateway
    ring = []
    for x in range(W):
        for z in range(D):
            edge = x in (0, W - 1) or z in (0, D - 1) or (min(x, W - 1 - x) + min(z, D - 1 - z) == 2 and (x in (1, W - 2) or z in (1, D - 2)))
            if edge and not cut(x, z):
                ring.append((x, z))
    for i, (x, z) in enumerate(sorted(ring)):
        if z == 0 and 10 <= x <= 12:
            continue  # the gateway
        b.set(x, 0, z, "coarse_dirt")
        top = 4 if (x + z) % 2 == 0 else 3
        for y in range(1, top + 1):
            log(b, x, y, z, "spruce_log")
        fence(b, x, top + 1, z, "spruce_fence")  # the sharpened point
    # the gateway: dark oak posts, a lintel, the gates swung in
    for x in (9, 13):
        for y in range(1, 6):
            log(b, x, y, 0, "dark_oak_log")
    for x in range(9, 14):
        log(b, x, 6, 0, "dark_oak_log", axis="x")
    for x in range(10, 13):
        b.set(x, 0, 0, "dirt_path")
    b.set(10, 1, 1, "dark_oak_fence_gate", facing="west", open=True, in_wall=False, powered=False)
    b.set(12, 1, 1, "dark_oak_fence_gate", facing="east", open=True, in_wall=False, powered=False)
    lantern(b, 9, 7, 0)
    lantern(b, 13, 7, 0)
    # a path from the gate to the fire
    for z in range(1, 6):
        b.set(11, 0, z, "dirt_path")

    # the fire, a ring of seats round it
    b.set(11, 0, 7, "cobblestone")
    b.set(11, 1, 7, "campfire", facing="south", lit=True, signal_fire=False, waterlogged=False)
    log(b, 9, 1, 6, "stripped_spruce_log", axis="z")
    log(b, 9, 1, 7, "stripped_spruce_log", axis="z")
    log(b, 13, 1, 7, "stripped_spruce_log", axis="z")
    log(b, 13, 1, 8, "stripped_spruce_log", axis="z")
    b.set(10, 1, 5, "barrel", facing="up", open=False)
    # the pole with the ominous banner
    for y in range(1, 7):
        fence(b, 14, y, 10, "dark_oak_fence")
    ominous_banner(b, 14, 7, 10, rotation="0")

    # three canvas tents down the west side, opening east onto the yard
    def side_tent(z0, cloth):
        for x in range(2, 6):
            b.set(x, 1, z0, cloth)
            b.set(x, 1, z0 + 4, cloth)
            b.set(x, 2, z0 + 1, cloth)
            b.set(x, 2, z0 + 3, cloth)
            log(b, x, 3, z0 + 2, "stripped_dark_oak_log", axis="x")
        b.set(2, 1, z0 + 1, cloth)
        b.set(2, 1, z0 + 2, cloth)
        b.set(2, 1, z0 + 3, cloth)
        b.set(2, 2, z0 + 2, cloth)
        fence(b, 6, 1, z0 + 2, "dark_oak_fence")
        fence(b, 6, 2, z0 + 2, "dark_oak_fence")
        b.bed(3, 1, z0 + 2, "white" if cloth == "white_wool" else "light_gray", facing="west")
    side_tent(2, "white_wool")
    side_tent(7, "light_gray_wool")
    side_tent(12, "white_wool")
    lantern(b, 6, 3, 9)
    lantern(b, 6, 3, 14)

    # the Warlord's tent at the back: 7 wide, 5 deep, open to the south (the yard)
    for z in range(13, 18):
        for x in (8, 14):
            b.set(x, 1, z, "gray_wool")
        for x in (9, 13):
            b.set(x, 2, z, "gray_wool")
        for x in (10, 12):
            b.set(x, 3, z, "black_wool")
        log(b, 11, 4, z, "dark_oak_log", axis="z")
    for x in range(9, 14):
        b.set(x, 1, 17, "gray_wool")
    for x in (10, 11, 12):
        b.set(x, 2, 17, "gray_wool")
    b.set(11, 3, 17, "black_wool")
    for y in (1, 2, 3):
        fence(b, 11, y, 12, "dark_oak_fence")
    ominous_banner(b, 11, 3, 13, facing="south")
    lantern(b, 11, 3, 15, hanging=True)
    for x in range(9, 14):
        for z in range(13, 17):
            b.set(x, 0, z, "spruce_planks")
    for x in range(10, 13):
        for z in range(14, 17):
            b.set(x, 1, z, "red_carpet")
    b.set_nbt(11, 1, 16, Compound({"LootTable": String("aliveworkplace:chests/war_camp"), "id": String("minecraft:chest")}))
    b.set(11, 1, 16, "chest", facing="north", type="single", waterlogged=False)
    b.set(9, 1, 16, "barrel", facing="up", open=False)
    b.set(13, 1, 16, "crafting_table")
    b.set(9, 1, 14, "anvil", facing="north")

    # the ravager pen, north-east: a dark oak fence round hay and a trough
    for x in range(16, 21):
        for z in range(11, 17):
            if x in (16, 20) or z in (11, 16):
                if not (x == 16 and z == 13):
                    fence(b, x, 1, z, "dark_oak_fence")
            b.set(x, 0, z, Mix((3, "coarse_dirt"), (2, "mud"), (1, "dirt"), seed=5).at(x, 0, z))
    b.set(16, 1, 13, "dark_oak_fence_gate", facing="east", open=False, in_wall=False, powered=False)
    b.set(19, 1, 15, "hay_block", axis="x")
    b.set(18, 1, 15, "hay_block", axis="y")
    b.set(19, 2, 15, "hay_block", axis="z")
    b.set(17, 1, 12, "water_cauldron", level=3)
    b.set(18, 1, 12, "cauldron")
    lantern(b, 16, 2, 11)

    # the siege yard, south-east: logs stacked to dry, ladders on a rack, a ram on its sled
    for x in range(16, 21):
        log(b, x, 1, 2, "stripped_spruce_log", axis="x")
        log(b, x, 2, 2, "spruce_log", axis="x")
    for x in (17, 19):
        log(b, x, 3, 2, "spruce_log", axis="x")
    for x in (16, 18, 20):
        log(b, x, 1, 5, "dark_oak_log", axis="y")
        log(b, x, 2, 5, "dark_oak_log", axis="y")
    for x in range(16, 21):
        log(b, x, 3, 5, "dark_oak_log", axis="x")
    for x in (16, 18, 20):
        b.set(x, 1, 6, "ladder", facing="south", waterlogged=False)
        b.set(x, 2, 6, "ladder", facing="south", waterlogged=False)
        b.set(x, 3, 6, "ladder", facing="south", waterlogged=False)
    # the ram: a long log on a sled of slabs, an iron head
    for z in range(7, 10):
        slab(b, 17, 1, z, "dark_oak_slab")
        slab(b, 19, 1, z, "dark_oak_slab")
    log(b, 18, 1, 7, "stripped_spruce_log", axis="z")
    log(b, 18, 1, 8, "stripped_spruce_log", axis="z")
    log(b, 18, 1, 9, "stripped_spruce_log", axis="z")
    b.set(18, 1, 10, "iron_block")
    b.set(15, 1, 3, "smithing_table")
    b.set(15, 1, 4, "grindstone", face="floor", facing="north")
    lantern(b, 15, 2, 3)
    b.fill_air()
    # outside the ring the world's ground stays
    for x in range(W):
        for z in range(D):
            if b.blocks.get((x, 0, z), ("",))[0] == "minecraft:air":
                del b.blocks[(x, 0, z)]
    return b
