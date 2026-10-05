"""
Walls and gates: a wooden palisade, a stone wall, a wall tower and a gatehouse. Front (z 0) is the inside of the
village — the walkway, the ladders — so a player standing in the village places them facing out. At night the guards
shut the gatehouse's gates (see guard/Gates.java).
"""
from kit import *


def stakes(b, x, z, low):
    """One palisade stake: a spruce log, pointed with a fence post (every other one a block taller)."""
    top = 3 if low else 4
    for y in range(0, top + 1):
        log(b, x, y, z, "spruce_log")
    fence(b, x, top + 1, z, "spruce_fence")


def palisade():
    """7 x 6 x 2: a wooden palisade — a row of spruce logs sharpened to points, every other one taller, a plank walkway
    on the inside to look over it from, and a ladder up."""
    b = Build(7, 6, 2)
    for x in range(7):
        stakes(b, x, 1, x % 2 == 1)
    for x in range(7):
        if x != 3:
            slab(b, x, 2, 0, SPRUCE, top=True)
    for y in range(0, 3):
        b.set(3, y, 0, "ladder", facing="north", waterlogged=False)
    b.fill_air()
    return b


def palisade_gate():
    """7 x 6 x 2: a gate in the palisade — three fence gates between the stakes under a log lintel (the guards shut them
    at night), lanterns on the posts either side."""
    b = Build(7, 6, 2)
    for x in (0, 1, 5, 6):
        stakes(b, x, 1, x % 2 == 1)
    for x in range(2, 5):
        b.set(x, 0, 1, "spruce_fence_gate", facing="south", open=False, powered=False, in_wall=False)
        log(b, x, 3, 1, "spruce_log", axis="x")
        fence(b, x, 4, 1, "spruce_fence")
    for x in (1, 5):
        lantern(b, x, 0, 0)
    b.fill_air()
    return b


def palisade_tower():
    """5 x 10 x 5: a log watch platform to turn a palisade's corner (ROADMAP 27.18) — four spruce posts on a cobbled
    footing, the sides and back closed with stakes to shoulder height, a way in at the front, a ladder up the back to a
    plank platform with a fence parapet, and a dark oak roof on the posts with a lantern under it. It fits inside the
    Wall Tower's 5 x 10 x 5, so a Town's stone tower takes its place cleanly."""
    b = Build(5, 10, 5)
    corners = ((0, 0), (4, 0), (0, 4), (4, 4))
    for x, z in corners:  # the footing: a stone under each post
        b.set(x, 0, z, STONE_MIX.at(x, 0, z))
    posts(b, corners, 1, 7, "spruce_log")
    # the lower walls: stakes on the sides and back (a taller one in the middle of each), open at the front but for
    # a stake either side of the way in
    for i in (1, 2, 3):
        for x, z in ((0, i), (4, i), (i, 4)):
            top = 3 if i == 2 else 2
            for y in range(0, top + 1):
                log(b, x, y, z, "stripped_spruce_log" if y == 0 else "spruce_log")
            if top == 2:
                fence(b, x, 3, z, "spruce_fence")
    for x in (1, 3):
        for y in range(0, 3):
            log(b, x, y, 0, "spruce_log")
        fence(b, x, 3, 0, "spruce_fence")
    # the platform: a beam ring on the posts, a plank floor with the ladder's hole, a fence parapet
    beam_ring(b, 0, 0, 4, 4, 4, "spruce_log")
    box(b, 1, 5, 1, 3, 5, 3, "spruce_planks")
    beam_ring(b, 0, 0, 4, 4, 5, "stripped_spruce_log")
    b.set(2, 5, 3, "air")
    for y in range(0, 6):
        b.set(2, y, 3, "ladder", facing="north", waterlogged=False)
    for i in (1, 2, 3):
        for x, z in ((i, 0), (i, 4), (0, i), (4, i)):
            fence(b, x, 6, z, "spruce_fence")
    # the roof on the posts: a low pyramid of dark oak stairs in two steps, a slab cap, a lantern under it
    for i in range(5):
        stairs(b, i, 8, 0, DARK_OAK, "south")
        stairs(b, i, 8, 4, DARK_OAK, "north")
    for i in (1, 2, 3):
        stairs(b, 0, 8, i, DARK_OAK, "east")
        stairs(b, 4, 8, i, DARK_OAK, "west")
    for i in (1, 2, 3):
        stairs(b, i, 9, 1, DARK_OAK, "south")
        stairs(b, i, 9, 3, DARK_OAK, "north")
    stairs(b, 1, 9, 2, DARK_OAK, "east")
    stairs(b, 3, 9, 2, DARK_OAK, "west")
    slab(b, 2, 9, 2, DARK_OAK)
    lantern(b, 2, 8, 2, hanging=True)
    # a light at the way in, hanging from the beam
    lantern(b, 2, 3, 0, hanging=True)
    b.fill_air()
    return b


def stone_wall():
    """7 x 6 x 3: a stone wall two blocks thick with battlements along its outer edge, a walkway on top, a ladder up the
    inside and a lantern by it."""
    b = Build(7, 6, 3)
    for x in range(7):
        for z in (1, 2):
            b.set(x, 0, z, STONE_MIX.at(x, 0, z))
            for y in range(1, 4):
                b.set(x, y, z, (MOSSY_BRICK_MIX if y == 1 else BRICK_WALL_MIX).at(x, y, z))
        # battlements: merlons and crenels along the outside
        if x % 2 == 0:
            b.set(x, 4, 2, "stone_bricks")
            slab(b, x, 5, 2, STONE_BRICK)
        else:
            slab(b, x, 4, 2, STONE_BRICK)
    for y in range(0, 4):
        b.set(3, y, 0, "ladder", facing="north", waterlogged=False)
    b.fill_air()
    return b


def wall_tower():
    """5 x 10 x 5: a square stone tower to end a wall or turn its corner — a door on the inside, arrow slits, a ladder up
    to a floor under the battlements."""
    b = Build(5, 10, 5)
    plinth(b, 0, 0, 4, 4, STONE_MIX, floor="cobblestone")
    walls(b, 0, 0, 4, 4, 1, 6, BRICK_WALL_MIX)
    walls(b, 0, 0, 4, 4, 1, 2, MOSSY_BRICK_MIX)  # moss low down
    for i in range(1, 4):  # a spruce band at the top floor, between the corners
        log(b, i, 6, 0, "spruce_log", axis="x")
        log(b, i, 6, 4, "spruce_log", axis="x")
        log(b, 0, 6, i, "spruce_log", axis="z")
        log(b, 4, 6, i, "spruce_log", axis="z")
    for x, z in ((0, 0), (4, 0), (0, 4), (4, 4)):
        box(b, x, 1, z, x, 6, z, "stone_bricks")
    door(b, 2, 1, 0, "spruce_door", "south")
    for side, (x, z) in (("north", (2, 4)), ("west", (0, 2)), ("east", (4, 2))):
        for y in (3, 4):
            wall_block(b, x, y, z, "stone_brick_wall")
    # the floor up top, with the ladder's hole
    box(b, 1, 6, 1, 3, 6, 3, "spruce_planks")
    for y in range(1, 7):
        b.set(3, y, 3, "ladder", facing="north", waterlogged=False)
    # battlements
    for x in range(5):
        for z in range(5):
            if x in (0, 4) or z in (0, 4):
                if (x + z) % 2 == 0:
                    b.set(x, 7, z, "stone_bricks")
                    slab(b, x, 8, z, STONE_BRICK)
                else:
                    slab(b, x, 7, z, STONE_BRICK)
    lantern(b, 1, 7, 1)
    b.fill_air()
    return b


def gate_tower(b, x0):
    """One of the gatehouse's two towers, 3 x 3 at x0..x0+2, z 1-3, seven high with battlements."""
    for x in range(x0, x0 + 3):
        for z in range(1, 4):
            b.set(x, 0, z, STONE_MIX.at(x, 0, z))
            for y in range(1, 7):
                if x in (x0, x0 + 2) or z in (1, 3) or y == 6:
                    b.set(x, y, z, "stone_bricks" if (x in (x0, x0 + 2) and z in (1, 3)) else (MOSSY_BRICK_MIX if y <= 2 else BRICK_WALL_MIX).at(x, y, z))
            if (x + z) % 2 == 0:
                b.set(x, 7, z, "stone_bricks")
                slab(b, x, 8, z, STONE_BRICK)
            else:
                slab(b, x, 7, z, STONE_BRICK)
    wall_block(b, x0 + 1, 3, 3, "stone_brick_wall")  # an arrow slit to the outside


def gatehouse():
    """11 x 9 x 5: a stone gatehouse — two towers either side of a gateway three wide under an arch, the gate a row of
    spruce fence gates (the guards shut them at night), a walkway over the arch with battlements, lanterns under it
    and ladders up the towers' inner faces."""
    b = Build(11, 9, 5)
    gate_tower(b, 0)
    gate_tower(b, 8)
    # The wall over the gateway: x 3-7, z 1-3, from y 4
    for x in range(3, 8):
        for z in range(1, 4):
            for y in (4, 5):
                b.set(x, y, z, BRICK_WALL_MIX.at(x, y, z))
        if x % 2 == 1:
            b.set(x, 6, 3, "stone_bricks")
            slab(b, x, 7, 3, STONE_BRICK)
        else:
            slab(b, x, 6, 3, STONE_BRICK)
    # The arch: the gateway's top corners rounded off with upside-down stairs
    for z in range(1, 4):
        stairs(b, 3, 3, z, STONE_BRICK, "east", top=True)
        stairs(b, 7, 3, z, STONE_BRICK, "west", top=True)
    # The gate: three fence gates across the middle of the gateway, a cobbled floor through it
    for x in range(3, 8):
        for z in range(0, 5):
            b.set(x, 0, z, "cobblestone" if z in (1, 2, 3) else "stone_bricks")
    for x in range(4, 7):
        b.set(x, 1, 2, "spruce_fence_gate", facing="south", open=False, powered=False, in_wall=False)
    for x in (3, 7):
        b.set(x, 1, 2, "stone_bricks")
        b.set(x, 2, 2, "stone_bricks")
    lantern(b, 5, 3, 1, hanging=True)
    # The portcullis, drawn up under the outer arch, and lanterns either side of the way in
    for x in range(4, 7):
        b.set(x, 3, 3, "iron_bars")
    for x in (3, 7):
        lantern(b, x, 1, 4)
    # Ladders up the towers' inner faces, to the walkway
    for x in (1, 9):
        for y in range(0, 7):
            b.set(x, y, 0, "ladder", facing="north", waterlogged=False)
    b.fill_air()
    return b


# --- Barracks: a stone hall for guards --------------------------------------------------------------------------------
BARRACKS_FRAME = "dark_oak_log"


def barracks_hall(b):
    """The barracks every tier shares (walls x 1-11, z 2-8): two grindstones at the back (the guards': hand the
    villager at each a sword) with the armory chest and an anvil between them, two bunks, barred windows, a slate hip
    roof, training dummies out front."""
    plinth(b, 1, 2, 11, 8, STONE_MIX, floor="spruce_planks")
    walls(b, 1, 2, 11, 8, 1, 3, BRICK_WALL_MIX)
    posts(b, [(1, 2), (11, 2), (1, 8), (11, 8)], 1, 3, "stone_bricks")
    beam_ring(b, 1, 2, 11, 8, 4, BARRACKS_FRAME)
    door(b, 6, 1, 2, "dark_oak_door", "south")
    stairs(b, 6, 0, 1, STONE_BRICK, "south")
    b.set(6, 3, 2, "chiseled_stone_bricks")
    for x in (3, 9):
        window(b, x, 2, 2, "north", height=1, glass="iron_bars", sill=STONE_BRICK)
        window(b, x, 2, 8, "south", height=1, glass="iron_bars")
    for x, side in ((1, "west"), (11, "east")):
        window(b, x, 2, 5, side, height=1, glass="iron_bars", sill=STONE_BRICK)
    # The guards' grindstones (their wheels turned to the room), the armory chest, an anvil
    b.set(3, 1, 7, "grindstone", face="floor", facing="north")
    b.set(9, 1, 7, "grindstone", face="floor", facing="north")
    b.set(6, 1, 7, "chest", facing="north", type="single", waterlogged=False)
    b.set(7, 1, 7, "anvil", facing="east")
    b.set(5, 1, 7, "chest", facing="north", type="single", waterlogged=False)
    # Two bunks by the front wall
    b.bed(2, 1, 4, "red", facing="north")
    b.bed(10, 1, 4, "red", facing="north")
    # A tie beam with the lamps
    for x in range(2, 11):
        log(b, x, 4, 5, BARRACKS_FRAME, axis="x")
    lantern(b, 4, 3, 5, hanging=True)
    lantern(b, 8, 3, 5, hanging=True)
    hip_roof(b, 0, 12, 1, 9, 4, DEEPSLATE_TILE)
    # Depth: lintels over the windows, buttresses down the sides and the back, a gabled stone porch over the door
    for x in (3, 9):
        stairs(b, x, 3, 1, STONE_BRICK, "south", top=True)
        stairs(b, x, 3, 9, STONE_BRICK, "north", top=True)
    for z in (3, 7):
        for x, face in ((0, "east"), (12, "west")):
            b.set(x, 1, z, "stone_bricks")
            stairs(b, x, 2, z, STONE_BRICK, face)
    for x in (4, 8):
        b.set(x, 1, 9, "stone_bricks")
        stairs(b, x, 2, 9, STONE_BRICK, "north")
    for x in (5, 7):
        for y in (1, 2, 3):
            b.set(x, y, 0, "stone_bricks" if y != 2 else "chiseled_stone_bricks")
        b.set(x, 3, 1, "stone_bricks")
    for x in range(5, 8):
        log(b, x, 4, 0, BARRACKS_FRAME, axis="x")
    gable_roof(b, 4, 8, 0, 2, 5, DEEPSLATE_TILE, axis="z", gable="stone_bricks", gable_at=(0,), eave_trim=STONE_BRICK)
    b.set(6, 5, 0, "chiseled_stone_bricks")
    for x in range(5, 8):
        b.set(x, 0, 0, "stone_bricks")
        b.set(x, 0, 1, "stone_bricks")
    b.set(6, 0, 0, "stone_brick_stairs", facing="south", half="bottom", shape="straight", waterlogged=False)
    lantern(b, 6, 3, 0, hanging=True)
    # The yard out front: two training dummies and a lamp
    b.set(2, 0, 0, "aliveworkplace:training_dummy", facing="south")
    b.set(10, 0, 0, "aliveworkplace:training_dummy", facing="south")
    lamp_post(b, 12, 0, 0, "dark_oak_fence", height=2)


def barracks():
    """13 x 10 x 11: a stone barracks with two grindstones for two guards (hand a villager by each a sword), an armory
    chest, bunks and training dummies out front."""
    b = Build(13, 10, 11)
    barracks_hall(b)
    b.fill_air()
    return b


def barracks_2():
    """Upgrade of the Barracks (same origin and front): a wing to the east, through a doorway, with two more
    grindstones (two more guards) and bunks under its own roof. 19 x 10 x 11."""
    b = barracks().grow(19, 10, 11)
    # The east window's shutters/sill come off; the window becomes the doorway into the wing
    b.clear(12, 1, 4, 12, 3, 6)
    for y in (1, 2):
        b.set(11, y, 5, "air")
    b.set(11, 3, 5, "stone_bricks")
    # Wing walls: x 11-17, z 3-8 (the barracks' east wall is its west side)
    plinth(b, 11, 3, 17, 8, STONE_MIX, floor="spruce_planks")
    b.set(11, 0, 5, "spruce_planks")
    for y in range(1, 4):
        for x in range(12, 18):
            b.set(x, y, 3, BRICK_WALL_MIX.at(x, y, 3))
            b.set(x, y, 8, BRICK_WALL_MIX.at(x, y, 8))
        for z in range(4, 8):
            b.set(17, y, z, BRICK_WALL_MIX.at(17, y, z))
    posts(b, [(17, 3), (17, 8)], 1, 3, "stone_bricks")
    for x in range(12, 18):
        log(b, x, 4, 3, BARRACKS_FRAME, axis="x")
        log(b, x, 4, 8, BARRACKS_FRAME, axis="x")
    for z in range(4, 8):
        log(b, 17, 4, z, BARRACKS_FRAME, axis="z")
    window(b, 17, 2, 5, "east", height=1, glass="iron_bars", sill=STONE_BRICK)
    window(b, 14, 2, 3, "north", height=1, glass="iron_bars", sill=STONE_BRICK)
    b.set(13, 1, 7, "grindstone", face="floor", facing="north")
    b.set(16, 1, 7, "grindstone", face="floor", facing="north")
    b.set(15, 1, 7, "chest", facing="north", type="single", waterlogged=False)
    b.bed(13, 1, 5, "red", facing="north")
    b.bed(16, 1, 5, "red", facing="north")
    for x in range(12, 17):
        log(b, x, 4, 6, BARRACKS_FRAME, axis="x")
    lantern(b, 14, 3, 6, hanging=True)
    # Its roof, lower than the barracks', the main roof's eave kept over the join
    keep = {p: v for p, v in b.blocks.items() if p[0] == 12 and p[1] >= 4}
    gable_roof(b, 12, 18, 2, 9, 4, DEEPSLATE_TILE, axis="x", gable=BRICK_WALL_MIX, gable_at=(17, 17))
    b.blocks.update(keep)
    b.fill_air()
    return b
