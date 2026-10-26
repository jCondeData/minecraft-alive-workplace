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
    - three canvas tents (white and light grey wool on dark oak) down the west side, a lamp pole beside each mouth;
    - the Warlord's tent at the back, bigger and darker (grey and black wool, a dark oak ridge), a red runner up to the
      loot chest inside,
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
        # the pole beside the mouth, not in it: the tent is two high only down its middle, under the ridge (B178)
        fence(b, 6, 1, z0 + 1, "dark_oak_fence")
        fence(b, 6, 2, z0 + 1, "dark_oak_fence")
        b.bed(3, 1, z0 + 2, "white" if cloth == "white_wool" else "light_gray", facing="west")
    side_tent(2, "white_wool")
    side_tent(7, "light_gray_wool")
    side_tent(12, "white_wool")
    lantern(b, 6, 3, 8)
    lantern(b, 6, 3, 13)

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
    # a red runner down the middle, under the ridge: on the sides, two high under the black wool, a carpet's 1/16 put
    # a villager's head into the cloth and it stood at the carpet's edge for good (B178)
    for z in range(13, 16):
        b.set(11, 1, z, "red_carpet")
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


# The Jolly Roger: a black banner with the white skull (ROADMAP 32.8).
JOLLY_ROGER = (("white", "skull"),)

# The Pirate Ship's waterline: the sea's top block is at this y of the template (the hull below it sits in the water).
PIRATE_SHIP_WATERLINE = 2


def pirate_ship():
    """
    The drowned pirates' Pirate Ship (ROADMAP 32.8), 9 x 20 x 27, set down at anchor on the sea with its bow (z = 0)
    to the village. Design:
    - a dark oak hull, 9 wide amidships, tapering to a stem at the bow and a little at the stern transom; it draws three
      blocks (y 0 to 2 sit in the water, PIRATE_SHIP_WATERLINE), with a pale stripped spruce wale along each side at y 3
      and a spruce deck at y 4 inside a dark oak rail; a raised forecastle over the bow; a dry hold below the deck;
    - two masts of dark oak: the foremast (z 8) with one square black sail, the taller mainmast (z 15) with a bigger
      one and the crow's nest at its top; the sails belly forward in the wind; a black jib from the bowsprit;
    - the captain's cabin at the stern (z 20 to 25): dark oak walls on stripped log posts, glazed windows on three
      sides, a door under a quarterdeck that reaches one block forward over it with two lanterns hanging there; the loot
      chest inside at the back, a red carpet, a lantern;
    - the quarterdeck on the cabin's roof with its own rail, the Jolly Roger (a black banner with the white skull) on a
      staff at the stern, a lantern at each back corner;
    - gunports (dispensers) three a side, barrels lashed on deck, the anchor chain down from a cathead at the bow.
    Everything outside the hull is left out (structure void), so the sea stays where it is; only the hold is dry.
    """
    W, H, D = 9, 20, 27
    C = 4  # the keel's x
    b = Build(W, H, D)

    def hw_z(z):  # the hull's half-width along its length: the stem at the bow, a narrower transom at the stern
        return {0: 0, 1: 1, 2: 2, 3: 3}.get(z, 3 if z == D - 1 else 4)

    def hw_y(y):  # and up its height: the keel, the bilge, full width from y 3
        return {0: 0, 1: 2, 2: 3}.get(y, 4)

    def in_hull(x, y, z):
        if y < 0 or y > 4 or not 0 <= z < D:
            return False
        if y == 0 and not 2 <= z <= D - 2:
            return False
        return abs(x - C) <= min(hw_z(z), hw_y(y))

    hold = set()
    for y in range(0, 5):
        for z in range(D):
            for x in range(W):
                if not in_hull(x, y, z):
                    continue
                shell = y == 4 and abs(x - C) == hw_z(z) or any(not in_hull(x + dx, y + dy, z + dz)
                                                               for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))
                if y == 4:
                    if shell:
                        b.set(x, y, z, "dark_oak_planks")
                    else:
                        b.set(x, y, z, "spruce_planks")  # the deck
                elif shell:
                    if y == 3 and abs(x - C) == 4:
                        log(b, x, y, z, "stripped_spruce_log", axis="z")  # the wale
                    else:
                        b.set(x, y, z, "dark_oak_planks")
                else:
                    hold.add((x, y, z))
    # the stem and bowsprit, rising at the bow
    log(b, C, 5, 0, "dark_oak_log")
    log(b, C, 6, 0, "dark_oak_log")
    log(b, C, 5, 1, "dark_oak_log")
    fence(b, C, 7, 0, "dark_oak_fence")
    lantern(b, C, 8, 0)
    # the forecastle: a raised deck over the bow, three stairs up to it
    for z in range(1, 6):
        for x in range(C - hw_z(z), C + hw_z(z) + 1):
            if (x, z) != (C, 1):
                b.set(x, 5, z, "dark_oak_planks" if abs(x - C) == hw_z(z) else "spruce_planks")
            fence(b, x, 6, z, "dark_oak_fence") if abs(x - C) == hw_z(z) else None
    for x in range(C - 1, C + 2):
        stairs(b, x, 5, 6, "spruce_stairs", "north")
    # the rail round the deck as far as the cabin
    for z in range(6, 19):
        for x in (C - hw_z(z), C + hw_z(z)):
            if (x, 5, z) not in b.blocks:
                fence(b, x, 5, z, "dark_oak_fence")
    # the cathead and the anchor chain going down into the sea
    log(b, 0, 5, 3, "dark_oak_log", axis="x")
    for y in range(1, 5):
        b.set(0, y, 3, "chain", axis="y", waterlogged=False)
    # gunports: dispensers looking out of the hull, three a side
    for z in (10, 12, 17):
        b.set(0, 4, z, "dispenser", facing="west", triggered=False)
        b.set(W - 1, 4, z, "dispenser", facing="east", triggered=False)
    # the hatch to the hold, barrels lashed on deck
    trapdoor(b, C, 4, 11, "spruce_trapdoor", "north", half="top")
    for x, y, z in ((1, 5, 7), (1, 5, 8), (1, 6, 7), (7, 5, 18), (6, 5, 18), (7, 6, 18), (2, 5, 13)):
        b.set(x, y, z, "barrel", facing="up", open=False)

    # the foremast and its sail, bellied forward
    for y in range(1, 16):
        log(b, C, y, 8, "dark_oak_log")
    for x in range(1, 8):
        if x != C:
            log(b, x, 14, 8, "dark_oak_log", axis="x")
    for x in range(2, 7):
        if x != C:
            log(b, x, 8, 8, "dark_oak_log", axis="x")
    for y in range(9, 14):
        for x in range(1, 8):
            belly = 10 <= y <= 12 and 2 <= x <= 6
            b.set(x, y, 6 if belly else 7, "black_wool")
    lantern(b, 2, 7, 8, hanging=True)
    lantern(b, 6, 7, 8, hanging=True)
    # the jib, from the bowsprit up towards the foremast
    for z in range(2, 6):
        for y in range(7, 7 + z):
            b.set(C, y, z, "black_wool")

    # the mainmast, its sail and the crow's nest
    for y in range(1, 20):
        log(b, C, y, 15, "dark_oak_log")
    for x in range(0, 9):
        if x != C:
            log(b, x, 16, 15, "dark_oak_log", axis="x")
    for x in range(1, 8):
        if x != C:
            log(b, x, 9, 15, "dark_oak_log", axis="x")
    for y in range(10, 16):
        for x in range(1, 8):
            belly = 11 <= y <= 14 and 2 <= x <= 6
            b.set(x, y, 13 if belly else 14, "black_wool")
    for x in range(3, 6):
        for z in range(14, 17):
            if (x, z) != (C, 15):
                slab(b, x, 17, z, "dark_oak_slab")
                fence(b, x, 18, z, "dark_oak_fence")
    lantern(b, 3, 19, 16)
    b.set(C, 18, 14, "air")  # (the lookout's way in, beside the mast)

    # the captain's cabin at the stern
    for y in range(5, 9):
        for z in range(20, 26):
            for x in range(W):
                edge_x, edge_z = x in (0, W - 1), z in (20, 25)
                if not (edge_x or edge_z):
                    continue
                if edge_x and edge_z:
                    log(b, x, y, z, "stripped_dark_oak_log")
                else:
                    b.set(x, y, z, "dark_oak_planks")
    for z in (22, 23):
        pane(b, 0, 6, z)
        pane(b, W - 1, 6, z)
    for x in (2, 3, 5, 6):
        pane(b, x, 6, 25)
    pane(b, 2, 6, 20)
    pane(b, 6, 6, 20)
    b.door(C, 5, 20, "dark_oak_door", facing="north")
    # the quarterdeck: the cabin's roof, one block out over the door on posts, a rail, the Jolly Roger
    for x in range(W):
        for z in range(19, 26):
            b.set(x, 9, z, "dark_oak_planks")
    for y in range(5, 9):
        fence(b, 0, y, 19, "dark_oak_fence")
        fence(b, W - 1, y, 19, "dark_oak_fence")
    lantern(b, 3, 8, 19, hanging=True)
    lantern(b, 5, 8, 19, hanging=True)
    for x in range(W):
        for z in range(19, 26):
            if x in (0, W - 1) or z in (19, 25):
                fence(b, x, 10, z, "dark_oak_fence")
    b.set(1, 10, 19, "air")  # the way up from the main deck: a ladder on the cabin's front, a hatch in the quarterdeck
    b.set(1, 9, 19, "dark_oak_trapdoor", facing="north", half="top", open=True, powered=False, waterlogged=False)
    for y in range(5, 9):
        b.set(1, y, 19, "ladder", facing="north", waterlogged=False)
    for y in range(10, 14):
        fence(b, C, y, 25, "dark_oak_fence")
    b.set_nbt(C, 14, 25, Compound({"id": String("minecraft:banner"), "patterns": List[Compound]([
        Compound({"color": String(c), "pattern": String("minecraft:" + p)}) for c, p in JOLLY_ROGER])}))
    b.set(C, 14, 25, "black_banner", rotation="8")
    lantern(b, 0, 11, 25)
    lantern(b, W - 1, 11, 25)
    # inside: red carpet, the loot chest at the back, a chart table, a lantern
    for x in range(1, W - 1):
        for z in range(22, 25):  # a block in from the door: on a carpet under its lintel a villager hits its head (B173)
            b.set(x, 5, z, "red_carpet")
    b.set_nbt(C, 5, 24, Compound({"LootTable": String("aliveworkplace:chests/pirate_ship"), "id": String("minecraft:chest")}))
    b.set(C, 5, 24, "chest", facing="north", type="single", waterlogged=False)
    b.set(2, 5, 24, "cartography_table")
    b.set(6, 5, 24, "barrel", facing="up", open=False)
    b.set(7, 5, 21, "dark_oak_stairs", facing="west", half="bottom", shape="straight", waterlogged=False)
    lantern(b, C, 8, 22, hanging=True)
    # the stern rail behind the cabin
    for x in range(C - hw_z(D - 1), C + hw_z(D - 1) + 1):
        fence(b, x, 5, D - 1, "dark_oak_fence")
    # a boarding ladder up the bow's port side from the water to the forecastle, through a gap in its rail (B149: the
    # decks had no way up from the sea)
    for y in range(1, 6):
        b.set(1, y, 2, "ladder", facing="west", waterlogged=y <= 2)
    b.set(2, 6, 2, "air")

    b.fill_air()
    # Everything but the hold is left out where nothing is drawn: the sea (and the air over it) stays as it is.
    for pos, block in list(b.blocks.items()):
        if block[0] == "minecraft:air" and pos not in hold:
            del b.blocks[pos]
    return b


def tomb_camp():
    """
    The desert raiders' Tomb Camp (ROADMAP 32.9), 19 x 11 x 19, set down at once like the other camps. Design:
    - the ground is the desert's own: sand with a little smooth sandstone and red sand worn into it;
    - a half-buried sandstone tomb at the back (9 x 9): cut sandstone walls with a chiseled band and an orange
      terracotta stripe, a stepped roof of three shrinking courses, a doorway between two chiseled pillars; dunes of sand
      heaped against its sides and back up to two blocks, so it seems to rise out of the desert;
    - inside, a smooth sandstone floor with a terracotta inlay, torches in the corners, urns (decorated-looking flower
      pots) and the Pharaoh's sarcophagus at the back: the loot chest boxed in smooth sandstone with a chiseled head;
    - in front of it the obelisk, the highest thing in the camp: a cut sandstone needle on a stepped base, a chiseled
      band near the top and a gold cap;
    - two sun-bleached awnings (white and pale grey cloth on stripped birch posts) over the camp's seats and a fire pit;
    - bone blocks half sunk in the sand, a skull, dead bushes scattered about.
    """
    W, H, D = 19, 11, 19
    b = Build(W, H, D)
    ground = Mix((14, "sand"), (2, "smooth_sandstone"), (1, "red_sand"), (1, "sandstone"), seed=97)
    for x in range(W):
        for z in range(D):
            b.set(x, 0, z, ground.at(x, 0, z))

    # the tomb: x 5..13, z 9..17, the doorway in its front (z = 9)
    X0, X1, Z0, Z1 = 5, 13, 9, 17
    wall = Mix((7, "cut_sandstone"), (2, "sandstone"), (1, "smooth_sandstone"), seed=98)
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            edge = x in (X0, X1) or z in (Z0, Z1)
            b.set(x, 0, z, "smooth_sandstone" if edge else ("orange_terracotta" if (x + z) % 4 == 0 and 7 <= x <= 11 and 11 <= z <= 15 else "smooth_sandstone"))
            if edge:
                for y in range(1, 6):
                    if y == 3:
                        b.set(x, y, z, "orange_terracotta")
                    elif y == 5:
                        b.set(x, y, z, "chiseled_sandstone" if (x + z) % 2 == 0 else "cut_sandstone")
                    else:
                        b.set(x, y, z, wall.at(x, y, z))
    # the blue inlay in the middle of the floor, as the desert temples have
    b.set(9, 0, 13, "blue_terracotta")
    # the stepped roof: three courses, each one in from the last
    for i, y in enumerate((6, 7, 8)):
        for x in range(X0 + i, X1 + 1 - i):
            for z in range(Z0 + i, Z1 + 1 - i):
                edge = x in (X0 + i, X1 - i) or z in (Z0 + i, Z1 - i)
                if edge or y == 8:
                    b.set(x, y, z, "smooth_sandstone" if i < 2 else "cut_sandstone")
    for x in range(X0 + 1, X1):
        for z in range(Z0 + 1, Z1):
            b.set(x, 6, z, "smooth_sandstone")
    b.set(9, 9, 13, "chiseled_sandstone")
    # the doorway: 3 wide, 3 high, between chiseled pillars that stand a block proud
    for x in (8, 9, 10):
        for y in (1, 2, 3):
            b.set(x, y, Z0, "air")
    for x in (7, 11):
        for y in range(1, 5):
            b.set(x, y, Z0 - 1, "chiseled_sandstone" if y in (1, 4) else "cut_sandstone")
    for x in range(7, 12):
        b.set(x, 4, Z0, "chiseled_sandstone")
        if 7 < x < 11:
            b.set(x, 4, Z0 - 1, "cut_sandstone")  # the lintel over the doorway
        stairs(b, x, 5, Z0 - 1, "sandstone_stairs", "north")
    b.set(8, 0, Z0 - 1, "smooth_sandstone")
    b.set(9, 0, Z0 - 1, "smooth_sandstone")
    b.set(10, 0, Z0 - 1, "smooth_sandstone")
    # inside: torches, urns, the sarcophagus
    for x, z in ((X0 + 1, Z0 + 1), (X1 - 1, Z0 + 1), (X0 + 1, Z1 - 1), (X1 - 1, Z1 - 1)):
        b.set(x, 1, z, "torch")
    for x, z in ((X0 + 1, 13), (X1 - 1, 13)):
        b.set(x, 1, z, "flower_pot")
        b.set(x, 1, z + 1, "decorated_pot", facing="north", cracked=False, waterlogged=False)
    for z in (14, 15, 16):
        b.set(8, 1, z, "smooth_sandstone")
        b.set(10, 1, z, "smooth_sandstone")
    b.set(9, 1, 16, "chiseled_sandstone")
    b.set(9, 2, 16, "skeleton_skull", rotation="0")
    b.set_nbt(9, 1, 15, Compound({"LootTable": String("aliveworkplace:chests/pharaohs_tomb"), "id": String("minecraft:chest")}))
    b.set(9, 1, 15, "chest", facing="north", type="single", waterlogged=False)
    slab(b, 9, 1, 14, "smooth_sandstone_slab")
    b.set(8, 2, 14, "candle", candles=3, lit=True, waterlogged=False)
    b.set(10, 2, 14, "candle", candles=2, lit=True, waterlogged=False)
    # the dunes heaped against the sides and back: two high next to the wall, one high a block further out
    for x in range(X0 - 2, X1 + 3):
        for z in range(Z0 + 1, D):
            if X0 <= x <= X1 and z <= Z1:
                continue
            dist = max(X0 - x, x - X1, z - Z1, 0)
            if dist == 1:
                for y in (1, 2):
                    b.set(x, y, z, "sand")
            elif dist == 2 and (x + z) % 3 != 0:
                b.set(x, 1, z, "sand")
    for x in range(X0 - 1, X1 + 2):
        if x in (X0 - 1, X1 + 1):
            b.set(x, 1, Z0, "sand")

    # the obelisk, front left: a stepped base, a needle, a gold cap
    OX, OZ = 3, 4
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            b.set(OX + dx, 1, OZ + dz, "smooth_sandstone")
    for dx, dz, f in ((0, -2, "north"), (0, 2, "south"), (-2, 0, "west"), (2, 0, "east")):
        stairs(b, OX + dx, 1, OZ + dz, "smooth_sandstone_stairs", "south" if f == "north" else "north" if f == "south" else "east" if f == "west" else "west")
    for y in range(2, 9):
        b.set(OX, y, OZ, "chiseled_sandstone" if y in (2, 7) else "cut_sandstone")
    b.set(OX, 9, OZ, "gold_block")
    b.set(OX, 10, OZ, "end_rod", facing="up")

    # two sun-bleached awnings over the seats and the fire pit, front right
    def awning(x0, z0, w, d, cloth):
        for x, z in ((x0, z0), (x0 + w - 1, z0), (x0, z0 + d - 1), (x0 + w - 1, z0 + d - 1)):
            for y in (1, 2):
                fence(b, x, y, z, "birch_fence")
        for x in range(x0, x0 + w):
            for z in range(z0, z0 + d):
                b.set(x, 3, z, cloth if (x + z) % 2 == 0 else "white_wool")
    awning(10, 2, 4, 3, "light_gray_wool")
    awning(15, 5, 3, 4, "white_wool")
    b.set(11, 1, 3, "barrel", facing="up", open=False)
    log(b, 12, 1, 4, "stripped_birch_log", axis="x")
    log(b, 16, 1, 7, "stripped_birch_log", axis="z")
    # the fire pit between them, ringed with sandstone
    b.set(13, 0, 7, "sandstone")
    b.set(13, 1, 7, "campfire", facing="south", lit=True, signal_fire=False, waterlogged=False)
    for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        slab(b, 13 + dx, 1, 7 + dz, "sandstone_slab")

    # bones half sunk in the sand, a skull, dead bushes
    for x, z, axis in ((1, 9, "x"), (2, 15, "z"), (16, 1, "y"), (17, 13, "x"), (7, 2, "z")):
        b.set(x, 0, z, "bone_block", axis=axis)
    b.set(6, 1, 5, "bone_block", axis="z")
    b.set(15, 1, 16, "skeleton_skull", rotation="6")
    for x, z in ((0, 1), (6, 0), (9, 5), (17, 3), (1, 12), (18, 10), (0, 17), (14, 12), (4, 8), (12, 0)):
        b.set(x, 1, z, "dead_bush")
    b.fill_air()
    return b


# The piglin banner: black, with the snout (the pattern a piglin banner pattern item gives) in gold over a dark border.
PIGLIN_BANNER = (("gray", "gradient_up"), ("yellow", "piglin"), ("orange", "border"))


def piglin_banner(b, x, y, z, **props):
    """The piglins' black banner with the gold snout at (x, y, z): standing ({@code rotation}) or, with {@code facing}, on a wall."""
    b.set_nbt(x, y, z, Compound({"id": String("minecraft:banner"), "patterns": List[Compound]([
        Compound({"color": String(c), "pattern": String("minecraft:" + p)}) for c, p in PIGLIN_BANNER])}))
    b.set(x, y, z, "black_wall_banner" if "facing" in props else "black_banner", **props)


def blackstone_outpost():
    """
    The piglins' Blackstone Outpost (ROADMAP 32.10), 21 x 12 x 19, set down at once like the other camps. Design:
    - the ground is a broken platform of blackstone and its bricks with netherrack worn through it and gilded blackstone
      here and there, ragged at the edges where the Overworld's own ground shows (the bottom layer is left out there);
    - at the back, on a dais of polished blackstone bricks reached by stairs, the lit portal: a frame of obsidian with
      crying obsidian at its corners and heaped beside it (only obsidian holds a portal, so the sides stay plain);
      the raiders step out of it (its portal blocks are what VillageRaids looks for, 32.10);
    - front left, the basalt pillar, the highest thing in the camp: polished basalt on a blackstone base, its top
      flying the piglins' black banner with the gold snout;
    - front right, the hoglin pen: crimson fences and a gate on crimson nylium, crimson fungus to root at, a shroomlight;
    - back right, the hoard: gold blocks heaped against a broken wall of polished blackstone bricks, a raw gold block,
      and the loot chest (chests/piglin_hoard);
    - back left, a broken bastion wall with blackstone walls on top and lanterns;
    - in the middle, a campfire ringed with blackstone slabs; lanterns on blackstone posts at the way in (no soul fire:
      piglins shun it).
    """
    W, H, D = 21, 12, 19
    b = Build(W, H, D)
    ground = Mix((8, "blackstone"), (4, "polished_blackstone_bricks"), (2, "cracked_polished_blackstone_bricks"), (3, "netherrack"),
                 (1, "gilded_blackstone"), seed=131)
    cx, cz = 10, 9
    for x in range(W):
        for z in range(D):
            # an oval, ragged at the rim (the world's ground shows through where it's broken off)
            r = ((x - cx) / 10.6) ** 2 + ((z - cz) / 9.6) ** 2
            if r <= 0.78 or r <= 1.0 and (x * 7 + z * 13) % 5 != 0:
                b.set(x, 0, z, ground.at(x, 0, z))

    # the dais and the portal at the back: dais x 6..14, z 13..17, the frame at z = 15
    for x in range(6, 15):
        for z in range(13, 18):
            b.set(x, 0, z, "polished_blackstone")
            b.set(x, 1, z, "polished_blackstone_bricks" if (x + z) % 5 else "cracked_polished_blackstone_bricks")
    for x in range(7, 14):
        stairs(b, x, 1, 12, "polished_blackstone_brick_stairs", "south")
    for z in (13, 17):
        stairs(b, 5, 1, z, "polished_blackstone_brick_stairs", "west")
        stairs(b, 15, 1, z, "polished_blackstone_brick_stairs", "east")
    PX0, PX1, PY0, PY1, PZ = 8, 12, 2, 7, 15
    for x in range(PX0, PX1 + 1):
        for y in range(PY0, PY1 + 1):
            corner = x in (PX0, PX1) and y in (PY0, PY1)
            if x in (PX0, PX1) or y in (PY0, PY1):
                b.set(x, y, PZ, "crying_obsidian" if corner else "obsidian")
            else:
                b.set(x, y, PZ, "nether_portal", axis="x")
    # crying obsidian heaped by the frame, and a chiseled step at its foot
    for x, y, z in ((7, 2, 15), (7, 2, 16), (13, 2, 15), (13, 3, 15), (13, 2, 14), (7, 2, 14)):
        b.set(x, y, z, "crying_obsidian" if (x + y + z) % 2 else "obsidian")
    b.set(10, 1, 14, "chiseled_polished_blackstone")
    # two broken pillars of the old bastion flank the dais, one taller than the other
    for x, top in ((6, 6), (14, 4)):
        for y in range(2, top + 1):
            b.set(x, y, 16, "polished_blackstone_bricks" if y % 3 else "chiseled_polished_blackstone")
        b.set(x, top + 1, 16, "blackstone_slab", type="bottom", waterlogged=False)
    b.set(7, 6, 16, "polished_blackstone_brick_wall")
    b.set(7, 5, 16, "chain", axis="y", waterlogged=False)
    lantern(b, 7, 4, 16, hanging=True, soul=False)
    for x in (6, 14):
        b.set(x, 2, 13, "blackstone_wall")
        lantern(b, x, 3, 13, soul=False)

    # the basalt pillar, front left, flying the banner
    BX, BZ = 4, 5
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            b.set(BX + dx, 1, BZ + dz, "polished_blackstone" if dx == 0 or dz == 0 else "blackstone")
    for dx, dz, f in ((0, -2, "south"), (0, 2, "north"), (-2, 0, "east"), (2, 0, "west")):
        stairs(b, BX + dx, 1, BZ + dz, "blackstone_stairs", f)
    for y in range(2, 9):
        b.set(BX, y, BZ, "polished_basalt" if y in (2, 8) else "basalt", axis="y")
    b.set(BX, 9, BZ, "chiseled_polished_blackstone")
    piglin_banner(b, BX, 10, BZ, rotation="0")
    piglin_banner(b, BX, 7, BZ + 1, facing="south")

    # the hoglin pen, front right: x 14..19, z 2..8
    QX0, QX1, QZ0, QZ1 = 14, 19, 2, 8
    for x in range(QX0, QX1 + 1):
        for z in range(QZ0, QZ1 + 1):
            b.set(x, 0, z, "crimson_nylium" if (x + 2 * z) % 4 else "netherrack")
            if x in (QX0, QX1) or z in (QZ0, QZ1):
                fence(b, x, 1, z, "crimson_fence")
    b.set(QX0, 1, 5, "crimson_fence_gate", facing="east", open=False, in_wall=False, powered=False)
    for x, z in ((16, 4), (18, 6), (17, 7)):
        b.set(x, 1, z, "crimson_fungus")
    b.set(15, 1, 7, "crimson_roots")
    b.set(QX1, 2, QZ0, "shroomlight")
    b.set(QX1, 2, QZ1, "shroomlight")

    # the hoard, back right: gold heaped against a broken wall, the chest
    for z in range(13, 18):
        b.set(18, 1, z, "polished_blackstone_bricks")
        if z in (13, 14, 15):
            b.set(18, 2, z, "cracked_polished_blackstone_bricks" if z == 14 else "polished_blackstone_bricks")
        if z == 14:
            b.set(18, 3, z, "blackstone_wall")
    for x in range(16, 18):
        for z in range(14, 18):
            b.set(x, 0, z, "gilded_blackstone" if (x + z) % 2 else "polished_blackstone")
    for x, y, z in ((17, 1, 14), (17, 1, 15), (17, 2, 15), (16, 1, 16), (17, 1, 16)):
        b.set(x, y, z, "gold_block")
    b.set(17, 1, 17, "raw_gold_block")
    b.set_nbt(16, 1, 14, Compound({"LootTable": String("aliveworkplace:chests/piglin_hoard"), "id": String("minecraft:chest")}))
    b.set(16, 1, 14, "chest", facing="west", type="single", waterlogged=False)
    lantern(b, 18, 4, 14, soul=False)

    # the broken bastion wall, back left
    for x in range(1, 6):
        for y in range(1, 5 - (x == 1) - (x == 5) * 2):
            b.set(x, y, 17, "polished_blackstone_bricks" if (x + y) % 3 else "cracked_polished_blackstone_bricks")
    for x in (2, 3, 4):
        b.set(x, 4, 17, "blackstone_wall")
    b.set(3, 3, 16, "chiseled_polished_blackstone")
    lantern(b, 3, 5, 17, soul=False)
    b.set(2, 1, 16, "blackstone_slab", type="bottom", waterlogged=False)

    # the fire, ringed with slabs, and the way in lit by lanterns hung from blackstone posts
    b.set(cx, 1, 8, "campfire", facing="south", lit=True, signal_fire=False, waterlogged=False)
    for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        slab(b, cx + dx, 1, 8 + dz, "blackstone_slab")
    for x in (8, 12):
        for y in (1, 2, 3):
            b.set(x, y, 1, "blackstone_wall")
        lantern(b, x, 4, 1, soul=False)
    b.fill_air()
    for x in range(W):  # where the platform is broken off, the world's own ground stays
        for z in range(D):
            if b.blocks.get((x, 0, z), ("",))[0] == "minecraft:air":
                del b.blocks[(x, 0, z)]
    return b


def coven_hut():
    """
    The witches' Coven Hut (ROADMAP 32.11), 17 x 18 x 17, set down at once like the other camps, in a swamp. Design:
    - a pond fills the back two thirds of the ground (water in the bottom layer, lily pads on it), ringed with mud,
      podzol and grass, moss in clusters at the water's edge;
    - over it the hut stands on mangrove stilts: six log posts with mangrove roots tangled round their feet hold a
      mangrove plank platform at y 3, one block wider than the hut, with a deck in front reached by stairs from the
      shore; a mangrove fence rail round the deck, a lantern on each front post;
    - the hut (9 x 7) has a dark oak log frame with mangrove plank infill, purple-tinted panes in the bays with dark oak
      shutters, a dark oak door in the middle of its front (the north, z = 0 side) under a hanging lantern; a dark oak
      gable roof with a one-block overhang, the ridge running along the long side, a king post and two little windows in
      each gable, a mossy cobblestone chimney up the back with a campfire smoking on it;
    - inside: two brewing stands, the coven's loot chest (chests/coven_hut) between bookshelves at the back, barrels
      with potted mushrooms, candles, a lantern, and the black cat;
    - on the shore in front, the coven's cauldron (the one whose breaking breaks the coven) over a lit campfire on a
      mossy cobblestone hearth, a brewing stand and barrels by it; on the other side the herb garden: blue orchids,
      alliums, ferns and mushrooms on podzol inside a mangrove fence.
    """
    W, H, D = 17, 18, 17
    b = Build(W, H, D)
    rim = Mix((5, "grass_block"), (3, "mud"), (3, "podzol"), (1, "moss_block"), seed=211)
    cx, cz = 8, 10
    pond = set()
    for x in range(W):
        for z in range(D):
            r = ((x - cx) / 7.6) ** 2 + ((z - cz) / 6.4) ** 2
            if z >= 5 and (r <= 0.82 or r <= 1.0 and (x * 5 + z * 3) % 4 != 0):
                pond.add((x, z))
                b.set(x, 0, z, "water", level="0")
            else:
                b.set(x, 0, z, rim.at(x, 0, z), **({"snowy": False} if rim.at(x, 0, z) in ("grass_block", "podzol") else {}))
    for x, z in ((2, 9), (14, 12), (5, 15), (11, 15), (13, 7)):
        if (x, z) in pond:
            b.set(x, 1, z, "lily_pad")

    # the stilts and the platform at y 3: x 3..13, z 4..13 (the deck is z 4..5)
    PX0, PX1, PZ0, PZ1, PY = 3, 13, 4, 13, 3
    for x, z in ((PX0, PZ0), (PX1, PZ0), (PX0, PZ1), (PX1, PZ1), (8, PZ0), (8, PZ1)):
        for y in range(0, PY):
            b.set(x, y, z, "mangrove_log", axis="y")
        for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            if (x + dx, z + dz) in pond and (x + dx + z + dz) % 2 == 0:
                b.set(x + dx, 0, z + dz, "mangrove_roots", waterlogged=True)
                if (dx + dz) > 0:
                    b.set(x + dx, 1, z + dz, "mangrove_roots", waterlogged=False)
    for x in range(PX0, PX1 + 1):
        for z in range(PZ0, PZ1 + 1):
            b.set(x, PY, z, "mangrove_planks")
    for x in range(PX0, PX1 + 1):  # beams under the platform's edges, round the posts
        for z in (PZ0, PZ1):
            if x not in (PX0, PX1, 8):
                b.set(x, PY - 1, z, "stripped_mangrove_log", axis="x")
    # the deck's rail, the way up in its middle, lanterns on the front posts
    for x in range(PX0, PX1 + 1):
        if x not in (7, 8, 9):
            fence(b, x, PY + 1, PZ0, "mangrove_fence")
    for z in range(PZ0, 7):
        fence(b, PX0, PY + 1, z, "mangrove_fence")
        fence(b, PX1, PY + 1, z, "mangrove_fence")
    lantern(b, PX0, PY + 2, PZ0)
    lantern(b, PX1, PY + 2, PZ0)
    for x in (7, 8, 9):  # stairs up from the shore
        stairs(b, x, 1, 2, "mangrove_stairs", "south")
        b.set(x, 1, 3, "mangrove_planks")
        stairs(b, x, 2, 3, "mangrove_stairs", "south")
        stairs(b, x, PY, PZ0, "mangrove_stairs", "south")  # the top step in the deck's edge (B149: a block to jump)
        b.set(x, 0, 2, "mud_bricks")
        b.set(x, 0, 3, "mud_bricks")

    # the hut: x 4..12, z 6..12, walls y 4..7
    HX0, HX1, HZ0, HZ1 = 4, 12, 6, 12
    for y in range(PY + 1, PY + 5):
        for x in range(HX0, HX1 + 1):
            for z in range(HZ0, HZ1 + 1):
                if x in (HX0, HX1) or z in (HZ0, HZ1):
                    post = x in (HX0, 8, HX1) and z in (HZ0, HZ1) or z in (HZ0, 9, HZ1) and x in (HX0, HX1)
                    b.set(x, y, z, "dark_oak_log" if post else "mangrove_planks", **({"axis": "y"} if post else {}))
    for x in range(HX0, HX1 + 1):
        for z in (HZ0, HZ1):
            b.set(x, PY + 4, z, "dark_oak_log", axis="x")
    for z in range(HZ0, HZ1 + 1):
        for x in (HX0, HX1):
            b.set(x, PY + 4, z, "dark_oak_log", axis="z")
    door(b, 8, PY + 1, HZ0, "dark_oak_door", "north")
    b.set(8, PY + 3, HZ0, "dark_oak_log", axis="y")
    for x in (6, 10):
        window(b, x, PY + 2, HZ0, "north", glass="purple_stained_glass_pane", shutters="dark_oak_trapdoor")
        window(b, x, PY + 2, HZ1, "south", glass="purple_stained_glass_pane", shutters="dark_oak_trapdoor")
    for z in (7, 11):
        window(b, HX0, PY + 2, z, "west", glass="purple_stained_glass_pane", shutters="dark_oak_trapdoor")
        window(b, HX1, PY + 2, z, "east", glass="purple_stained_glass_pane", shutters="dark_oak_trapdoor")
    top = gable_roof(b, HX0 - 1, HX1 + 1, HZ0 - 1, HZ1 + 1, PY + 5, DARK_OAK, axis="x", gable="mangrove_planks",
                     gable_at=(HX0, HX1), eave_trim=DARK_OAK)
    for x in (HX0, HX1):  # a king post up each gable, a little window beside it
        for y in range(PY + 5, top):
            b.set(x, y, 9, "dark_oak_log", axis="y")
        pane(b, x, PY + 6, 8, "purple_stained_glass_pane")
        pane(b, x, PY + 6, 10, "purple_stained_glass_pane")
    # the chimney up the back slope
    for y in range(PY + 1, PY + 10):
        b.set(10, y, HZ1 + 1, "mossy_cobblestone" if y % 3 else "cobblestone")
    b.set(10, PY + 10, HZ1 + 1, "campfire", lit=True, signal_fire=False, facing="north", waterlogged=False)
    # a lantern hanging over the door from the eave
    lantern(b, 8, PY + 4, HZ0 - 1, hanging=True)

    # inside: floor y 3, standing at y 4
    for x in range(HX0 + 1, HX1):
        for z in range(HZ0 + 1, HZ1):
            b.set(x, PY + 1, z, "air")
    b.set(5, PY + 1, 11, "brewing_stand", has_bottle_0=True, has_bottle_1=True, has_bottle_2=False)
    b.set(11, PY + 1, 11, "brewing_stand", has_bottle_0=False, has_bottle_1=True, has_bottle_2=True)
    for x in (7, 9):
        b.set(x, PY + 1, 11, "bookshelf")
        b.set(x, PY + 2, 11, "bookshelf")
    b.set_nbt(8, PY + 1, 11, Compound({"LootTable": String("aliveworkplace:chests/coven_hut"), "id": String("minecraft:chest")}))
    b.set(8, PY + 1, 11, "chest", facing="north", type="single", waterlogged=False)
    b.set(7, PY + 3, 11, "candle", candles="3", lit=True, waterlogged=False)  # on a bookshelf: not on the chest's lid
    for x, z, pot in ((5, 7, "potted_red_mushroom"), (11, 7, "potted_brown_mushroom")):
        b.set(x, PY + 1, z, "barrel", facing="up", open=False)
        b.set(x, PY + 2, z, pot)
    b.set(5, PY + 1, 9, "barrel", facing="up", open=False)  # (the coven's one cauldron stands outside: breaking it breaks the coven)
    b.set(5, PY + 2, 9, "candle", candles="2", lit=True, waterlogged=False)
    b.set(11, PY + 1, 9, "barrel", facing="up", open=False)
    lantern(b, 11, PY + 2, 9)
    b.set(7, PY + 1, 7, "purple_carpet")
    b.set(8, PY + 1, 8, "purple_carpet")
    b.set(9, PY + 1, 8, "purple_carpet")
    b.set(8, PY + 1, 9, "purple_carpet")
    cat(b, 9, PY + 1, 9)

    # the coven's cauldron on the shore, front right: a mossy cobblestone hearth, a lit campfire, the cauldron over it
    for x in range(11, 16):
        for z in range(0, 4):
            if abs(x - 13) + abs(z - 1.5) <= 3:
                b.set(x, 0, z, "mossy_cobblestone" if (x + z) % 3 else "cobblestone")
    b.set(13, 1, 1, "campfire", lit=True, signal_fire=False, facing="north", waterlogged=False)
    b.set(13, 2, 1, "water_cauldron", level="3")
    b.set(15, 1, 2, "brewing_stand", has_bottle_0=True, has_bottle_1=False, has_bottle_2=True)
    b.set(11, 1, 0, "barrel", facing="up", open=False)
    b.set(11, 2, 0, "potted_red_mushroom")
    b.set(15, 1, 0, "barrel", facing="north", open=False)
    b.set(12, 1, 3, "candle", candles="2", lit=True, waterlogged=False)

    # the herb garden, front left: a fenced podzol bed of swamp herbs and mushrooms
    flower_bed(b, 1, 0, 5, 3, 0, ["blue_orchid", "allium", "fern", "brown_mushroom", "red_mushroom", "blue_orchid", None], soil="podzol")
    for x in range(0, 7):
        for z in (0, 4) if x in (0, 6) else (4,):
            fence(b, x, 1, z, "mangrove_fence")
    for z in range(0, 5):
        fence(b, 0, 1, z, "mangrove_fence")
        if z != 2:
            fence(b, 6, 1, z, "mangrove_fence")
    b.set(6, 1, 2, "mangrove_fence_gate", facing="east", open=False, in_wall=False, powered=False)
    b.set(6, 0, 2, "mud")
    # mushrooms at the stilts' feet and round the rim, moss on the bank
    for x, z, m in ((2, 4, "red_mushroom"), (14, 5, "brown_mushroom"), (1, 12, "brown_mushroom"), (15, 9, "red_mushroom"),
                    (16, 14, "brown_mushroom"), (0, 7, "red_mushroom")):
        if (x, z) not in pond:
            b.set(x, 0, z, "podzol", snowy=False)
            b.set(x, 1, z, m)
    for x, z in ((1, 5), (15, 6), (0, 10), (16, 11)):
        if (x, z) not in pond and b.get(x, 1, z) is None:
            b.set(x, 1, z, "moss_carpet")
    b.fill_air()
    return b


def cat(b, x, y, z):
    """A black cat (the witches' own: vanilla's all_black, the cat of swamp huts) sitting at (x, y, z) when placed."""
    from nbtlib import Double, Int as NInt, Byte
    b.entities = getattr(b, "entities", [])
    b.entities.append(Compound({
        "pos": List[Double]([Double(x + 0.5), Double(y), Double(z + 0.5)]),
        "blockPos": List[NInt]([NInt(x), NInt(y), NInt(z)]),
        "nbt": Compound({"id": String("minecraft:cat"), "variant": String("minecraft:all_black"), "PersistenceRequired": Byte(1),
                         "Sitting": Byte(1)}),
    }))
