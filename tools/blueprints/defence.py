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


def stone_wall():
    """7 x 6 x 3: a stone wall two blocks thick with battlements along its outer edge, a walkway on top, a ladder up the
    inside and a lantern by it."""
    b = Build(7, 6, 3)
    for x in range(7):
        for z in (1, 2):
            b.set(x, 0, z, STONE_MIX.at(x, 0, z))
            for y in range(1, 4):
                b.set(x, y, z, BRICK_WALL_MIX.at(x, y, z))
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
                    b.set(x, y, z, "stone_bricks" if (x in (x0, x0 + 2) and z in (1, 3)) else BRICK_WALL_MIX.at(x, y, z))
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
    lantern(b, 5, 3, 3, hanging=True)
    # Ladders up the towers' inner faces, to the walkway
    for x in (1, 9):
        for y in range(0, 7):
            b.set(x, y, 0, "ladder", facing="north", waterlogged=False)
    b.fill_air()
    return b
