"""
Legends found in the world (ROADMAP 29.9): three small scenes set down at once (not built) beside a vanilla structure
when a player who could take the Legend home stands in it (see legend/LegendSites). As with the bandit camp, the bottom
layer is the ground itself; where nothing is drawn there the world's ground stays.

- traveller_camp: a wanderer's night stop beside a ruined portal: a bedroll under a canvas fly, a campfire ringed with
  stones, a log to sit on, a barrel with a map on it and a lantern on a post.
- prisoner_cage: an iron-bar cage on a cobbled floor under a dark oak lid, at the foot of a pillager outpost's tower:
  straw to sleep on, a bowl-less cauldron and a lantern on the roof. Break any bar and the prisoner is free.
- castaway_camp: a castaway's camp on the beach nearest a shipwreck: a raft with a mast and a torn sail, a driftwood
  lean-to over a bed of carpet, and a signal fire on a hay bale.
"""
from kit import *
from nbtlib import Byte, Compound, Double, Float, Int, List, String

CAMP_GROUND = Mix((5, "coarse_dirt"), (3, "dirt_path"), (2, "dirt"), (1, "gravel"), seed=29)
CAGE_FLOOR = Mix((6, "cobblestone"), (3, "mossy_cobblestone"), (1, "gravel"), seed=9)
BEACH = Mix((6, "sand"), (2, "gravel"), (1, "coarse_dirt"), seed=17)

# Where each one's Legend stands (the Java side reads the same spots: LegendSites).
TRAVELLER_SPOT = (4, 1, 5)
PRISONER_SPOT = (2, 1, 2)
CASTAWAY_SPOT = (4, 1, 4)


def _keep_world_ground(b):
    for x in range(b.w):
        for z in range(b.d):
            if b.blocks.get((x, 0, z), ("",))[0] == "minecraft:air":
                del b.blocks[(x, 0, z)]


def _map_on(b, x, y, z):
    """An item frame lying on top of the block below (x, y, z), with an empty map in it."""
    b.entities = getattr(b, "entities", [])
    b.entities.append(Compound({
        "pos": List[Double]([Double(x + 0.5), Double(y + 0.03125), Double(z + 0.5)]),
        "blockPos": List[Int]([Int(x), Int(y), Int(z)]),
        "nbt": Compound({
            "id": String("minecraft:item_frame"),
            "Facing": Byte(1),
            "Item": Compound({"id": String("minecraft:map"), "count": Int(1)}),
            "ItemRotation": Byte(1),
            "ItemDropChance": Float(1.0),
        }),
    }))


def traveller_camp():
    """7 x 4 x 7: a bedroll under a canvas fly, a fire ringed with stones, a barrel with a map, a lantern post."""
    b = Build(7, 4, 7)
    for x in range(7):
        for z in range(7):
            dx, dz = (x - 3) / 3.5, (z - 3) / 3.5
            if dx * dx + dz * dz <= 1.0:
                b.set(x, 0, z, CAMP_GROUND.at(x, 0, z))
    # the fire, on stone, ringed with cobblestone slabs on the ground side
    b.set(3, 0, 3, "cobblestone")
    b.set(3, 1, 3, "campfire", facing="south", lit=True, signal_fire=False, waterlogged=False)
    for x, z in ((2, 3), (4, 3), (3, 2)):
        slab(b, x, 1, z, COBBLE)
    # a log to sit on, south of the fire
    log(b, 2, 1, 1, "stripped_spruce_log", axis="x")
    log(b, 3, 1, 1, "stripped_spruce_log", axis="x")
    # the bedroll on the west side, under a canvas fly on two poles
    b.bed(1, 1, 4, "brown", facing="south")
    for z in (3, 6):
        fence(b, 0, 1, z, "spruce_fence")
        fence(b, 0, 2, z, "spruce_fence")
    for z in range(3, 7):
        b.set(0, 3, z, "white_wool")
        b.set(1, 3, z, "white_wool")
    # the barrel with the map on it, and his pack (a chest's worth on a hay bale)
    b.set(5, 1, 4, "barrel", facing="up", open=False)
    _map_on(b, 5, 2, 4)
    b.set(5, 1, 5, "hay_block", axis="x")
    # a lantern on a post at the path's edge
    fence(b, 6, 1, 2, "spruce_fence")
    fence(b, 6, 2, 2, "spruce_fence")
    lantern(b, 6, 3, 2)
    b.fill_air()
    _keep_world_ground(b)
    return b


def prisoner_cage():
    """5 x 5 x 5: an iron-bar cage on a cobbled floor, roofed in dark oak, with straw inside."""
    b = Build(5, 5, 5)
    for x in range(5):
        for z in range(5):
            b.set(x, 0, z, CAGE_FLOOR.at(x, 0, z))
    # corner posts of dark oak, bars between them
    for x, z in ((0, 0), (4, 0), (0, 4), (4, 4)):
        for y in (1, 2):
            log(b, x, y, z, "stripped_dark_oak_log")
    for y in (1, 2):
        for i in range(1, 4):
            for x, z in ((i, 0), (i, 4), (0, i), (4, i)):
                b.set(x, y, z, "iron_bars")
    # the lid: dark oak slabs on a frame of logs
    for x in range(5):
        for z in range(5):
            if x in (0, 4) or z in (0, 4):
                log(b, x, 3, z, "dark_oak_log", axis="x" if z in (0, 4) else "z")
            else:
                slab(b, x, 3, z, DARK_OAK)
    lantern(b, 2, 4, 2)
    # straw to sleep on, a cauldron for water
    b.set(1, 1, 3, "hay_block", axis="z")
    b.set(3, 1, 3, "cauldron")
    b.fill_air()
    return b


def castaway_camp():
    """9 x 4 x 7: a raft drawn up on the sand, a driftwood lean-to, a signal fire on a hay bale."""
    b = Build(9, 4, 7)
    for x in range(9):
        for z in range(7):
            dx, dz = (x - 4) / 4.5, (z - 3) / 3.5
            if dx * dx + dz * dz <= 1.0:
                b.set(x, 0, z, BEACH.at(x, 0, z))
    # the raft: oak slabs lashed together, a mast with a torn sail
    for x in range(0, 3):
        for z in range(0, 3):
            if (x, z) != (1, 1):
                slab(b, x, 1, z, OAK)
    for y in (1, 2, 3):
        fence(b, 1, y, 1, "oak_fence")
    b.set(1, 3, 0, "white_wool")
    b.set(1, 2, 0, "white_carpet")
    # the signal fire on a hay bale, with driftwood stacked by it
    b.set(4, 0, 2, "hay_block", axis="y")
    b.set(4, 1, 2, "campfire", facing="south", lit=True, signal_fire=True, waterlogged=False)
    log(b, 3, 1, 1, "stripped_oak_log", axis="z")
    log(b, 5, 1, 1, "stripped_oak_log", axis="x")
    # the lean-to: two posts at the front, a sloping roof of spruce falling to the back
    for x in (6, 8):
        fence(b, x, 1, 3, "spruce_fence")
        fence(b, x, 2, 3, "spruce_fence")
    for x in range(6, 9):
        slab(b, x, 3, 3, SPRUCE)
        slab(b, x, 2, 4, SPRUCE, top=True)
        slab(b, x, 2, 5, SPRUCE)
        b.set(x, 1, 6, "spruce_planks")
    for x in (7,):
        b.set(x, 1, 4, "light_gray_carpet")
        b.set(x, 1, 5, "light_gray_carpet")
    b.set(6, 1, 5, "barrel", facing="up", open=False)
    b.fill_air()
    _keep_world_ground(b)
    return b


# The Old Sage (29.14): where they stand in the hut (legend/OldSage reads the same spot).
SAGE_SPOT = (4, 1, 5)
HUT_STONE = Mix((5, "mossy_cobblestone"), (4, "cobblestone"), (1, "andesite"), seed=14)


def hermit_hut():
    """12 x 10 x 11: the Old Sage's hut, set down 150-250 blocks out from a village: a 7 x 7 room of mossy cobblestone in
    a spruce frame under a dark oak roof, a stone chimney, a lectern before bookshelves, a cauldron of water, and an
    herb garden in a fence beside the door."""
    b = Build(12, 10, 11)
    # the ground: a worn path to the door, coarse dirt round the hut
    for x in range(12):
        for z in range(11):
            b.set(x, 0, z, CAMP_GROUND.at(x, 0, z))
    for z in range(0, 3):
        b.set(4, 0, z, "dirt_path")
    # plinth and walls: mossy stone, spruce posts at the corners and beside the door, a beam round the top
    plinth(b, 1, 2, 7, 8, HUT_STONE)
    for x in range(1, 8):
        for z in range(2, 9):
            if x in (1, 7) or z in (2, 8):
                for y in (1, 2):
                    b.set(x, y, z, HUT_STONE.at(x, y, z))
                b.set(x, 3, z, "spruce_planks")
            else:
                b.set(x, 0, z, "spruce_planks")
    posts(b, [(1, 2), (7, 2), (1, 8), (7, 8), (3, 2), (5, 2), (1, 5), (7, 5)], 1, 3, "spruce_log")
    beam_ring(b, 1, 2, 7, 8, 4, "stripped_spruce_log")
    # the door, with a step and a lantern by it
    door(b, 4, 1, 2, "spruce_door", "north")
    stairs(b, 4, 0, 1, COBBLE, "south")
    lantern(b, 3, 1, 1)
    # windows with shutters: one each side, one at the back
    window(b, 1, 2, 4, "west", shutters="spruce_trapdoor")
    window(b, 7, 2, 4, "east", shutters="spruce_trapdoor")
    window(b, 4, 2, 8, "south", shutters="spruce_trapdoor", sill=COBBLE)
    # the roof, ridge front to back, gables of spruce
    gable_roof(b, 0, 8, 1, 9, 4, DARK_OAK, axis="x", gable="spruce_planks", eave_trim=SPRUCE)
    # the chimney up the east side, smoking
    chimney(b, 8, 6, 0, 7, HUT_STONE)
    # inside: shelves of books along the back, the lectern before them, the cauldron, a bed, a hanging lantern
    for x in (2, 3, 5, 6):
        for y in (1, 2):
            b.set(x, y, 7, "bookshelf")
    b.set(2, 1, 6, "bookshelf")
    b.set(4, 1, 6, "lectern", facing="north", has_book=False, powered=False)
    b.set(6, 1, 3, "water_cauldron", level=3)
    b.bed(2, 1, 4, "green", facing="south")
    b.set(6, 1, 6, "barrel", facing="up", open=False)
    b.set(6, 2, 6, "potted_fern")
    for x in range(2, 7):  # a tie beam across the room, the lantern hung from it
        log(b, x, 4, 5, "stripped_spruce_log", axis="x")
    lantern(b, 4, 3, 5, hanging=True)
    # the herb garden east of the door: a fenced bed of herbs and flowers, a composter
    flower_bed(b, 9, 1, 11, 4, 0, ["fern", "allium", "lily_of_the_valley", "oxeye_daisy", "azure_bluet", "sweet_berry_bush"],
               soil="podzol")
    for z in range(0, 6):
        fence(b, 8, 1, z, "spruce_fence") if z != 2 else None
    for x in range(9, 12):
        fence(b, x, 1, 0, "spruce_fence")
        fence(b, x, 1, 5, "spruce_fence")
    b.set(8, 1, 2, "spruce_fence_gate", facing="west", open=False, in_wall=False, powered=False)
    b.set(10, 1, 6, "composter", level=0)
    lamp_post(b, 11, 1, 6)
    b.fill_air()
    _keep_world_ground(b)
    return b


STATUE_SQUARE = Mix((6, "stone_bricks"), (2, "cracked_stone_bricks"), (1, "andesite"), seed=23)


def founder_statue():
    """7 x 8 x 7: the Founder's statue (ROADMAP 29.23), built by the village's builders near the hall. A paved square
    with a lamp post at each front corner; a stepped 5 x 5 plinth of stone bricks (chiseled corners, a ring of stairs
    up to a 3 x 3 polished andesite pedestal) with a copper plaque set into its front; on it the Founder in stone, robe
    and shoulders of polished andesite, a chiseled head, one arm down and the right hand raised over the village. The
    head is the top of the middle column: legend/Founder.stands looks for it there."""
    b = Build(7, 8, 7)
    # The square round it, and two lamp posts flanking the front (z = 0)
    for x in range(7):
        for z in range(7):
            edge = x in (0, 6) or z in (0, 6)
            b.set(x, 0, z, "polished_andesite" if edge else STATUE_SQUARE.at(x, 0, z))
    for x in (0, 6):
        wall_block(b, x, 1, 0, "stone_brick_wall")
        lantern(b, x, 2, 0)
    # The plinth: a 5 x 5 step with chiseled corners, then a ring of stairs round the 3 x 3 pedestal
    for x in range(1, 6):
        for z in range(1, 6):
            corner = x in (1, 5) and z in (1, 5)
            b.set(x, 1, z, "chiseled_stone_bricks" if corner else "stone_bricks")
    for x in range(1, 6):
        for z in range(1, 6):
            if x in (1, 5) or z in (1, 5):
                if not (x in (1, 5) and z in (1, 5)):
                    side = "north" if z == 1 else "south" if z == 5 else "west" if x == 1 else "east"
                    stairs(b, x, 2, z, STONE_BRICK, OPP[side])
                else:
                    slab(b, x, 2, z, STONE_BRICK)
            else:
                b.set(x, 2, z, "polished_andesite")
    # The copper plaque, set into the front of the pedestal under the stairs' lip
    b.set(3, 1, 1, "waxed_cut_copper")
    # The Founder: a two-block robe, shoulders and chest, the left arm down, the right arm raised, a chiseled head
    b.set(3, 3, 3, "polished_andesite")
    b.set(3, 4, 3, "polished_andesite")
    b.set(3, 5, 3, "polished_andesite")
    stairs(b, 2, 5, 3, ANDESITE, "east", top=True)   # the left shoulder, its arm hanging
    wall_block(b, 2, 4, 3, "andesite_wall")
    stairs(b, 4, 5, 3, ANDESITE, "west", top=True)   # the right shoulder
    wall_block(b, 4, 6, 3, "andesite_wall")          # the right arm, raised
    b.set(4, 7, 3, "stone_button", face="floor", facing="south", powered=False)  # the raised hand
    b.set(3, 6, 3, "chiseled_stone_bricks")          # the head
    b.set(3, 7, 3, "stone_slab", type="bottom", waterlogged=False)  # a cap of hair
    b.fill_air()
    return b
