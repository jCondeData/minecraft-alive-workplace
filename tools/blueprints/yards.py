"""
Yards for the outdoor trades.

Compost Yard: I an open timber shed on a mossy cobble plinth with two slatted compost bays, the Compost Bin under the
eaves, a fenced yard with a gate, lamp posts and a flower bed; II a third bay and a potting shed at the east end.

Sifting Shed: I a stone-footed lean-to over the Sieve, heaps of gravel and sand in stone bins, a chest of finds;
II a second Sieve under a longer roof, and a covered sand bin.
"""
from kit import *

YARD = Mix((5, "coarse_dirt"), (2, "podzol"), (1, "rooted_dirt"), seed=31)
HEAP = Mix((4, "coarse_dirt"), (3, "rooted_dirt"), (2, "podzol"), (1, "mud"), seed=32)
MOSSY_PLINTH = Mix((5, "cobblestone"), (3, "mossy_cobblestone"), (1, "stone"), seed=33)
SHED_FRAME = "spruce_log"
SHED_WALL = "spruce_planks"
SHED_ROOF = DARK_OAK


def compost_shed(b, x0, x1):
    """The open shed (walls x {x0}-{x1}, z 4-8, three high): posts every four blocks, a planked back wall with windows,
    a dark oak beam round the top, compost bays between the posts with a board across the front."""
    plinth(b, x0, 4, x1, 8, MOSSY_PLINTH)
    posts(b, [(x, z) for x in range(x0, x1 + 1, 4) for z in (4, 8)], 1, 3, SHED_FRAME)
    for x in range(x0 + 1, x1):
        if (x - x0) % 4:
            for y in (1, 2, 3):
                b.set(x, y, 8, SHED_WALL)
    for x in range(x0 + 2, x1, 4):
        window(b, x, 2, 8, "south", shutters="spruce_trapdoor")
    for z in (5, 6, 7):
        for x in (x0, x1):
            for y in (1, 2, 3):
                b.set(x, y, z, SHED_WALL)
        pane(b, x0, 2, 6)
        pane(b, x1, 2, 6)
    beam_ring(b, x0, 4, x1, 8, 4, "stripped_dark_oak_log")
    # the bays: a board along the front, a heap behind it, moss on the heap
    for x in range(x0 + 1, x1):
        if (x - x0) % 4 == 0:
            continue
        slab(b, x, 1, 5, SPRUCE)
        for z in (6, 7):
            b.set(x, 1, z, HEAP.at(x, 1, z))
        if (x + x0) % 3 == 0:
            b.set(x, 2, 7, "moss_carpet")


def compost_roof(b, x0, x1):
    """A dark oak gable roof over the shed, ridge running along it, planked gables with a little window."""
    gable_roof(b, x0 - 1, x1 + 1, 3, 9, 4, SHED_ROOF, axis="x", gable=SHED_WALL, gable_at=(x0, x1))
    for x in (x0, x1):
        pane(b, x, 5, 6)


def compost_front(b, x1, gate_x):
    """The yard in front: a mossy cobble kerb round a bed of coarse earth, fenced along the front with a gate, lamp
    posts at the corners."""
    box(b, 0, 0, 0, x1, 0, 3, YARD)
    for x in range(0, x1 + 1):
        b.set(x, 0, 0, MOSSY_PLINTH.at(x, 0, 0))
    for z in range(1, 4):
        b.set(0, 0, z, MOSSY_PLINTH.at(0, 0, z))
        b.set(x1, 0, z, MOSSY_PLINTH.at(x1, 0, z))
    for x in range(0, x1 + 1):
        if x == gate_x:
            b.set(x, 1, 0, "spruce_fence_gate", facing="south", open=False, powered=False, in_wall=False)
        else:
            fence(b, x, 1, 0, "spruce_fence")
    for x in (0, x1):
        lamp_post(b, x, 1, 0, post="spruce_fence", height=2)


def compost_yard():
    """11 x 8 x 10: an open timber shed on a mossy cobble plinth, two slatted compost bays under a dark oak roof, the
    Compost Bin and a chest under the eaves, a fenced yard with a gate, lamp posts and a flower bed."""
    b = Build(11, 8, 10)
    compost_shed(b, 1, 9)
    compost_roof(b, 1, 9)
    compost_front(b, 10, 5)
    b.set(4, 1, 3, "aliveworkplace:compost_bin", facing="south")
    b.set(3, 1, 3, "chest", facing="south", type="single", waterlogged=False)
    b.set(7, 1, 3, "hay_block", axis="x")
    lantern(b, 3, 3, 4, hanging=True)
    lantern(b, 7, 3, 4, hanging=True)
    flower_bed(b, 7, 1, 9, 2, 0, ["poppy", "dandelion", "fern", "cornflower", None])
    b.set(1, 1, 1, "potted_brown_mushroom")
    b.fill_air()
    return b


def compost_yard_2():
    """15 x 8 x 10: a third bay (the shed runs on to the east) and a potting shed at that end — a door, a window with
    a flower box, a workbench, pots and a second chest."""
    base = compost_yard()
    b = base.grow(15, 8, 10)
    b.clear(9, 1, 5, 9, 7, 7)  # the old east gable end comes down; the shed runs on
    compost_front(b, 14, 5)
    compost_shed(b, 1, 13)
    compost_roof(b, 1, 13)
    b.clear(10, 2, 0, 10, 3, 0)  # the old corner lamp: the fence runs on
    # the potting shed in the new east bay: walled in, a door in front, a bench, pots and a chest
    for x in (10, 11, 12):
        for y in (1, 2, 3):
            b.set(x, y, 4, SHED_WALL)
    door(b, 11, 1, 4, "spruce_door", "south")
    window(b, 12, 2, 4, "north", flowers=("spruce_trapdoor", ["potted_red_tulip"]))
    for y in (1, 2, 3):
        b.set(9, y, 5, SHED_WALL)
        b.set(9, y, 6, SHED_WALL)
        b.set(9, y, 7, SHED_WALL)
    b.clear(10, 1, 5, 12, 2, 7)
    b.set(12, 1, 7, "crafting_table")
    slab(b, 11, 1, 7, SPRUCE, top=True)
    slab(b, 10, 1, 7, SPRUCE, top=True)
    b.set(10, 2, 7, "potted_fern")
    b.set(11, 2, 7, "potted_oak_sapling")
    b.set(12, 1, 5, "chest", facing="west", type="single", waterlogged=False)
    lantern(b, 11, 6, 6, hanging=True)  # from the ridge
    b.set(13, 1, 3, "hay_block", axis="z")
    b.set(13, 2, 3, "hay_block", axis="x")
    b.fill_air()
    return b


SIFT_FLOOR = Mix((4, "polished_andesite"), (3, "andesite"), (2, "gravel"), seed=34)


def sifting_hall(b, depth):
    """The shed both tiers share: gable to the street, walls x 1-7, z 1-{depth}: a stone brick course, spruce planks above
    between log posts, a wide opening in front under a beam, a tie beam across for the builder to reach the ridge from."""
    plinth(b, 1, 1, 7, depth, STONE_MIX, floor=SIFT_FLOOR)
    walls(b, 1, 1, 7, depth, 1, 1, "stone_bricks")
    walls(b, 1, 1, 7, depth, 2, 4, SHED_WALL)
    posts(b, [(x, z) for x in (1, 7) for z in range(1, depth + 1, 3)] + [(1, depth), (7, depth)], 1, 4, SHED_FRAME)
    for x in range(2, 7):  # the opening
        for y in range(1, 4):
            b.set(x, y, 1, "air")
    for x in range(1, 8):
        log(b, x, 4, 1, "stripped_spruce_log", axis="x")
    for z in range(3, depth, 3):
        window(b, 1, 2, z + 1, "west", shutters="spruce_trapdoor", sill=STONE_BRICK)
        window(b, 7, 2, z + 1, "east", shutters="spruce_trapdoor", sill=STONE_BRICK)
    for x in range(2, 7):
        log(b, x, 4, 4, "stripped_spruce_log", axis="x")
    ridge = gable_roof(b, 0, 8, 0, depth + 1, 4, SHED_ROOF, axis="z", gable=SHED_WALL, gable_at=(1, depth), eave_trim=SPRUCE)
    for z in (1, depth):
        pane(b, 4, 6, z)
        pane(b, 4, 7, z)
    return ridge


def sifting_shed():
    """9 x 9 x 9: a timber shed on a stone footing, gable to the street and open in front: the Sieve inside, bins of gravel
    and sand, a chest for the finds, a lantern on a chain from the tie beam."""
    b = Build(9, 9, 9)
    sifting_hall(b, 7)
    b.set(4, 1, 4, "aliveworkplace:sieve", facing="north")
    b.set(6, 1, 6, "chest", facing="west", type="single", waterlogged=False)
    for x, z, block in ((2, 6, "gravel"), (2, 5, "gravel"), (3, 6, "sand"), (2, 4, "sand")):
        b.set(x, 1, z, block)
    b.set(2, 2, 6, "gravel")
    trapdoor(b, 3, 1, 5, "spruce_trapdoor", "south", open_=True)
    lantern(b, 5, 3, 4, hanging=True)
    # outside: a lantern on a post and a low heap of gravel by the opening
    lamp_post(b, 0, 0, 0, post="spruce_fence", height=2)
    for x, z in ((8, 0), (8, 1), (7, 0)):
        b.set(x, 0, z, "gravel")
    b.fill_air()
    return b


def sifting_shed_2():
    """11 x 9 x 13: the shed run back to twice the depth with a second Sieve for a second sifter, a side door, and a
    catslide lean-to down the east side over stone-walled bins of sand and gravel."""
    b = sifting_shed().grow(11, 9, 13)
    b.clear(1, 1, 7, 7, 8, 7)  # the old back wall and gable come down
    sifting_hall(b, 11)
    b.set(4, 1, 9, "aliveworkplace:sieve", facing="north")
    b.set(6, 1, 10, "chest", facing="west", type="single", waterlogged=False)
    b.set(6, 1, 6, "chest", facing="west", type="single", waterlogged=False)
    for x, z, block in ((2, 10, "sand"), (2, 9, "sand"), (3, 10, "gravel"), (2, 6, "gravel"), (2, 5, "gravel"), (3, 6, "sand"), (2, 4, "sand")):
        b.set(x, 1, z, block)
    b.set(2, 2, 10, "sand")
    for x in range(2, 7):
        log(b, x, 4, 7, "stripped_spruce_log", axis="x")
    lantern(b, 5, 3, 7, hanging=True)
    # the side door, with a step down and a lantern post
    door(b, 7, 1, 9, "spruce_door", "east")
    stairs(b, 8, 0, 9, STONE_BRICK, "west")
    lamp_post(b, 9, 0, 10, post="spruce_fence", height=2)
    # the lean-to: the east slope runs on down over the bins (a beam under the eave, the bins' stone walls hold it up)
    plinth(b, 8, 2, 10, 6, STONE_MIX, floor=SIFT_FLOOR)
    for z in range(2, 7):
        log(b, 8, 3, z, "stripped_spruce_log", axis="z")
        stairs(b, 9, 3, z, SHED_ROOF, "west")
        stairs(b, 10, 2, z, SHED_ROOF, "west")
        b.set(10, 1, z, "stone_brick_wall")
    for z in (2, 4, 6):
        for x in (8, 9):
            b.set(x, 1, z, "stone_brick_wall")
    for x in (8, 9):
        b.set(x, 1, 3, "sand")
        b.set(x, 1, 5, "gravel")
    lantern(b, 9, 2, 4, hanging=True)
    b.fill_air()
    return b
