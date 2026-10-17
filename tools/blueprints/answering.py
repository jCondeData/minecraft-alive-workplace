"""
Houses for the jobs that answer a disaster (ROADMAP 32.19): the Fire Station (the Firewarden's grindstone), the Well
House (the Well Keeper's cauldron) and the Physician's House (the brewing stand). Each has its job's block and no other
job block, so the villager handed the job's item there takes that job and no passer-by takes another.
Front is z = 0 (north), y = 0 sits on the clicked block.
"""

from kit import *
from decor import MOSSY_BRICK_MIX

# --- Fire Station: a stone engine room under a timber watch loft -------------------------------------------------
FIRE_FRAME = "dark_oak_log"
FIRE_LOFT = "spruce_planks"


def fire_station():
    """11 x 13 x 11: a stone ground floor (x 1-9, z 1-9) with the Firewarden's grindstone at the back, a water basin
    in the corner and buckets on two racks (pails on spruce shelves), a ladder up to a timber watch loft with windows
    on every side under a steep-ish dark oak roof; a red door, lamp posts either side of the front."""
    b = Build(11, 14, 11)
    # Ground floor: stone walls 4 high on a cobbled plinth, a stone brick floor
    plinth(b, 1, 1, 9, 9, STONE_MIX, floor="polished_andesite")
    walls(b, 1, 1, 9, 9, 1, 4, BRICK_WALL_MIX)
    posts(b, [(1, 1), (9, 1), (1, 9), (9, 9)], 1, 4, "stone_bricks")
    for x, z in ((0, 3), (0, 7), (10, 3), (10, 7)):  # buttresses down the sides
        b.set(x, 0, z, "stone_bricks")
        b.set(x, 1, z, "stone_bricks")
        stairs(b, x, 2, z, STONE_BRICK, "east" if x == 0 else "west")
    # The front: a wide red double door under a chiseled lintel, a step, a window either side
    door(b, 5, 1, 1, "crimson_door", "south", hinge="left")
    b.set(5, 3, 1, "chiseled_stone_bricks")
    b.set(5, 4, 1, "red_terracotta")
    stairs(b, 5, 0, 0, STONE_BRICK, "south")
    for x in (3, 7):
        window(b, x, 2, 1, "north", height=1, sill=STONE_BRICK)
        stairs(b, x, 3, 0, STONE_BRICK, "south", top=True)
    for z, side in ((5, "west"), (5, "east")):
        window(b, 1 if side == "west" else 9, 2, z, side, height=1, sill=STONE_BRICK)
    window(b, 3, 2, 9, "south", height=1, sill=STONE_BRICK)
    # The loft floor (y 5) on a dark oak beam ring, with a hole for the ladder
    beam_ring(b, 1, 1, 9, 9, 5, FIRE_FRAME)
    b.fill(2, 5, 2, 8, 5, 8, FIRE_LOFT)
    for y in range(1, 6):
        b.set(7, y, 8, "ladder", facing="north", waterlogged=False)
    # The Firewarden's grindstone at the back, a bench by it
    b.set(5, 1, 8, "grindstone", face="floor", facing="north")
    stairs(b, 4, 1, 8, SPRUCE, "south")
    # The water basin, in the back west corner: a pool rimmed with polished andesite against the walls
    for x, z in ((2, 6), (3, 6), (3, 7), (3, 8)):
        b.set(x, 1, z, "polished_andesite")
    b.set(2, 1, 7, "water", level=0)
    b.set(2, 1, 8, "water", level=0)
    # Bucket racks down the east wall either side of its window: spruce shelves with pails, and over the basin
    for z in (3, 4, 6, 7):
        slab(b, 8, 2, z, SPRUCE, top=True)
        b.set(8, 3, z, "flower_pot")
    for z in (3, 4):
        slab(b, 2, 3, z, SPRUCE, top=True)
        b.set(2, 4, z, "flower_pot")
    lantern(b, 5, 4, 4, hanging=True)
    # The watch loft (y 6-7): timber walls, a window in every bay to watch the roofs from
    walls(b, 1, 1, 9, 9, 6, 7, FIRE_LOFT)
    posts(b, [(1, 1), (5, 1), (9, 1), (1, 5), (9, 5), (1, 9), (5, 9), (9, 9)], 6, 7, FIRE_FRAME)
    for x in (3, 7):
        b.set(x, 6, 1, "glass_pane")
        b.set(x, 7, 1, "glass_pane")
        b.set(x, 6, 9, "glass_pane")
        b.set(x, 7, 9, "glass_pane")
    for z in (3, 7):
        b.set(1, 6, z, "glass_pane")
        b.set(1, 7, z, "glass_pane")
        b.set(9, 6, z, "glass_pane")
        b.set(9, 7, z, "glass_pane")
    lantern(b, 4, 6, 6)
    b.set(4, 6, 4, "spruce_stairs", facing="east", half="bottom", shape="straight", waterlogged=False)  # the watchman's stool
    beam_ring(b, 1, 1, 9, 9, 8, FIRE_FRAME)
    gable_roof(b, 0, 10, 0, 10, 8, DARK_OAK, axis="z", gable=FIRE_LOFT, gable_at=(1, 9), eave_trim=SPRUCE)
    # A little window in each gable
    b.set(5, 9, 1, "glass_pane")
    b.set(5, 9, 9, "glass_pane")
    # Lamp posts either side of the step, a red stripe of terracotta along the lintel
    lamp_post(b, 4, 0, 0, "dark_oak_fence", height=1)
    lamp_post(b, 6, 0, 0, "dark_oak_fence", height=1)
    for x in (4, 6):
        b.set(x, 4, 1, "red_terracotta")
    b.fill_air()
    return b


# --- Well House: a roofed well on a raised stone platform -----------------------------------------------------------
def well_house():
    """11 x 12 x 12: a stone platform three high (x 1-9, z 2-10) with steps up the front; in its middle a kerbed well
    shaft three deep down to the water, a spruce frame over it with a lantern on a chain; the Well Keeper's cauldron
    in a back corner, a wooden trough of water along the back, benches either side; four spruce posts and a fence
    rail hold a dark oak hip roof over it all."""
    b = Build(11, 13, 12)
    # The platform, darkest at the foot
    for x in range(1, 10):
        for z in range(2, 11):
            for y in range(0, 3):
                edge = x in (1, 9) or z in (2, 10)
                b.set(x, y, z, (MOSSY_BRICK_MIX if y == 0 else BRICK_WALL_MIX).at(x, y, z) if edge else "cobblestone")
            b.set(x, 2, z, "stone_bricks" if x in (1, 9) or z in (2, 10) else "polished_andesite")
    # Steps up the front: three wide, three steps, the last cut into the platform's edge (B149: two left a block to jump)
    for x in (4, 5, 6):
        stairs(b, x, 0, 0, STONE_BRICK, "south")
        b.set(x, 0, 1, "stone_bricks")
        stairs(b, x, 1, 1, STONE_BRICK, "south")
        stairs(b, x, 2, 2, STONE_BRICK, "south")
    for x in (3, 7):
        for y in (0, 1):
            b.set(x, y, 1, "stone_bricks")
        b.set(x, 0, 0, "stone_bricks")
        stairs(b, x, 1, 0, STONE_BRICK, "south")  # the cheek walls step down
    # The shaft: water at the bottom, two of air above it, a kerb round the top
    cx, cz = 5, 5
    b.set(cx, 0, cz, "water", level=0)
    b.set(cx, 1, cz, "air")
    b.set(cx, 2, cz, "air")
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 1, cz + 2):
            if (x, z) != (cx, cz):
                corner = x != cx and z != cz
                b.set(x, 3, z, "stone_bricks" if corner else MOSSY_BRICK_MIX.at(x, 3, z))
    for x in (cx - 1, cx + 1):
        for y in (4, 5):
            fence(b, x, y, cz, "spruce_fence")
    for x in range(cx - 1, cx + 2):
        log(b, x, 6, cz, "spruce_log", axis="x")
    b.set(cx, 5, cz, "chain", axis="y", waterlogged=False)
    lantern(b, cx, 4, cz, hanging=True)
    # The Well Keeper's cauldron in the back east corner; a wooden trough of water along the back west
    b.set(8, 3, 9, "cauldron")
    for x in range(2, 7):
        b.set(x, 3, 8, "stripped_spruce_log", axis="x")
        b.set(x, 3, 10, "stripped_spruce_log", axis="x")
    b.set(2, 3, 9, "stripped_spruce_log", axis="z")
    b.set(6, 3, 9, "stripped_spruce_log", axis="z")
    for x in range(3, 6):
        b.set(x, 3, 9, "water", level=0)
    # Benches either side of the well, facing it, with trapdoor arms
    for z in (4, 5, 6):
        stairs(b, 2, 3, z, SPRUCE, "west")
        stairs(b, 8, 3, z, SPRUCE, "east")
    for x in (2, 8):
        trapdoor(b, x, 3, 3, "spruce_trapdoor", "north", open_=True)
        trapdoor(b, x, 3, 7, "spruce_trapdoor", "south", open_=True)
    # Four corner posts, a rail between them (open at the front steps and on each side's middle)
    for x, z in ((1, 2), (9, 2), (1, 10), (9, 10)):
        for y in range(3, 7):
            log(b, x, y, z, "spruce_log")
    for x in range(2, 9):
        for z in (2, 10):
            if not (z == 2 and 3 <= x <= 7) and not (z == 10 and 2 <= x <= 6):
                fence(b, x, 3, z, "spruce_fence")
    for z in range(3, 10):
        for x in (1, 9):
            if z != 6:
                fence(b, x, 3, z, "spruce_fence")
    beam_ring(b, 1, 2, 9, 10, 7, "spruce_log")
    b.set(3, 4, 2, "lantern", hanging=False, waterlogged=False)
    b.set(7, 4, 2, "lantern", hanging=False, waterlogged=False)
    b.set(3, 3, 2, "spruce_fence")
    b.set(7, 3, 2, "spruce_fence")
    hip_roof(b, 0, 10, 1, 11, 7, DARK_OAK)
    # Moss and a fern or two where the platform meets the ground
    for x, z in ((0, 3), (0, 9), (10, 5), (10, 8), (2, 11), (8, 11)):
        b.set(x, 0, z, "moss_block")
    b.fill_air()
    return b


# --- Physician's House: a white ward with a herb garden -------------------------------------------------------------
PHYS_FRAME = "spruce_log"
PLASTER = Mix((9, "white_concrete"), (1, "polished_diorite"), seed=19)


def physicians_house():
    """13 x 11 x 13: a white plastered ward (x 1-11, z 3-11) in a spruce frame under a dark oak roof. Inside, the
    quarantine ward: four beds, two down each side, each between white wool screens; the Physician's brewing stand at
    the back on a counter of herbs. Out front a herb garden either side of the path, lamp posts at the gate."""
    b = Build(13, 12, 13)
    plinth(b, 1, 3, 11, 11, STONE_MIX, floor="birch_planks")
    walls(b, 1, 3, 11, 11, 1, 4, PLASTER)
    posts(b, [(1, 3), (5, 3), (7, 3), (11, 3), (1, 7), (11, 7), (1, 11), (5, 11), (7, 11), (11, 11)], 1, 4, PHYS_FRAME)
    beam_ring(b, 1, 3, 11, 11, 5, PHYS_FRAME)
    door(b, 6, 1, 3, "spruce_door", "south")
    b.set(6, 3, 3, PHYS_FRAME, axis="y")
    # A gabled porch over the door, on two posts, its ridge meeting the main roof
    for x in (5, 7):
        for y in (1, 2, 3):
            fence(b, x, y, 1, "spruce_fence")
    for x in range(4, 9):
        log(b, x, 4, 1, PHYS_FRAME, axis="x")
    for x in (5, 7):
        log(b, x, 4, 2, PHYS_FRAME, axis="z")
    stairs(b, 6, 0, 2, STONE_BRICK, "south")
    for x in (3, 9):
        window(b, x, 2, 3, "north", height=2, shutters="spruce_trapdoor", sill=SPRUCE)
        window(b, x, 2, 11, "south", height=2, shutters="spruce_trapdoor")
    for z in (5, 9):
        window(b, 1, 2, z, "west", height=1, shutters="spruce_trapdoor")
        window(b, 11, 2, z, "east", height=1, shutters="spruce_trapdoor")
    # The ward: four beds, heads to the side walls under the windows, white wool screens between
    for z in (5, 9):
        b.bed(3, 1, z, "white", facing="west")
        b.bed(9, 1, z, "white", facing="east")
    for z in (4, 7, 10):
        for x in (2, 3, 9, 10):
            b.set(x, 1, z, "white_wool")
            b.set(x, 2, z, "white_wool")
    # The Physician's brewing stand at the back, on a counter between pots of herbs
    for x in (5, 7):
        slab(b, x, 1, 10, SPRUCE, top=True)
    b.set(5, 2, 10, "potted_fern")
    b.set(7, 2, 10, "potted_lily_of_the_valley")
    b.set(6, 1, 10, "brewing_stand", has_bottle_0=False, has_bottle_1=False, has_bottle_2=False)
    # A tie beam across the ward with two lamps
    for x in range(2, 11):
        log(b, x, 5, 7, PHYS_FRAME, axis="x")
    lantern(b, 4, 4, 7, hanging=True)
    lantern(b, 8, 4, 7, hanging=True)
    gable_roof(b, 0, 12, 2, 12, 5, DARK_OAK, axis="x", gable=PLASTER, gable_at=(1, 11), eave_trim=SPRUCE)
    for x in (1, 11):
        b.set(x, 7, 7, "glass_pane")
        log(b, x, 6, 7, PHYS_FRAME)
    # The herb garden: two beds either side of the path, edged with spruce trapdoors; the path paved
    herbs = ["fern", "lily_of_the_valley", "allium", "azure_bluet", "oxeye_daisy", "cornflower", None]
    flower_bed(b, 1, 0, 4, 1, 0, herbs)
    flower_bed(b, 8, 0, 11, 1, 0, herbs)
    for x in (5, 6, 7):
        b.set(x, 0, 0, "gravel" if x != 6 else "stone_bricks")
        b.set(x, 0, 1, "stone_bricks" if x == 6 else "gravel")
        if x != 6:
            b.set(x, 0, 2, "stone_bricks")
    for x in range(1, 5):
        trapdoor(b, x, 1, 2, "spruce_trapdoor", "south", open_=True)
    for x in range(8, 12):
        trapdoor(b, x, 1, 2, "spruce_trapdoor", "south", open_=True)
    lantern(b, 6, 3, 1, hanging=True)
    gable_roof(b, 3, 9, 0, 3, 5, DARK_OAK, axis="z", gable=PLASTER, gable_at=(1,), eave_trim=SPRUCE)
    b.fill_air()
    return b
