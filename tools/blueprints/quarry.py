"""
The Quarry (ROADMAP 36.12): a pit the Miner works like a farm. The pit itself isn't in the file: it is the ground as it
lies, inside a curbed, fenced rim, and the Miner digs it out layer by layer while what they took grows back slowly from
the bottom up (mine/QuarryBuilds). Beside it a flight of steps cut down along its east side, and the Miner's shed with
the blast furnace (the Miner's job block) and their chests.

QuarryBuilds.java holds the pit's corners and the furnace's place: change them together with this file.
"""
from kit import *

QUARRY_BASEMENT = 5
PIT = (1, 2, 8, 10)  # x0, z0, x1, z1 of the pit; it runs from the ground (y -1) down to y -10, left out of the file
RIM = Mix((6, "cobblestone"), (3, "stone_bricks"), (2, "mossy_cobblestone"), seed=3612)
SHED_FOOT = Mix((5, "cobblestone"), (3, "stone_bricks"), (2, "andesite"), seed=3613)


def quarry():
    """17 x 9 x 12 (+5 below ground): a 8 x 9 pit (x 1-8, z 2-10) inside a curb of cobblestone and stone brick with a
    spruce fence on it, a gap at the north for the hoist and an open gate on the east (the Miner's way down the steps); inside the gate a flight of stone steps
    cut down the pit's east side (x 9) to a landing four deep, walled on the far side; a spruce hoist frame over the north
    rim with a lantern on a chain; and east of the pit the Miner's shed (x 11-15, z 3-9): a cobblestone footing,
    spruce plank walls between log posts, a dark oak gable roof, inside the blast furnace, a double chest and a crafting
    table, a lantern, and a woodpile by its back corner."""
    b = Build(17, 9, 12, basement=QUARRY_BASEMENT)
    px0, pz0, px1, pz1 = PIT
    # The rim: a curb ring one block high round the pit and the steps (x 0-10, z 1-11)
    for x in range(0, 11):
        for z in range(1, 12):
            if x in (0, 10) or z in (1, 11):
                b.set(x, 0, z, RIM.at(x, 0, z))
    # The curb over the steps' cut (x 9) is the ground they're cut through: the walk round the pit is on the curb
    for z in range(pz0, pz1 + 1):
        b.set(9, 0, z, "air")
    # A fence on the curb, cobblestone wall posts at the corners and either side of the openings
    for x in range(0, 11):
        for z in range(1, 12):
            if not (x in (0, 10) or z in (1, 11)):
                continue
            corner = x in (0, 10) and z in (1, 11)
            if corner or (z == 1 and x in (3, 6)) or (x == 10 and z in (8, 10)):
                b.set(x, 1, z, "cobblestone_wall")
            elif z == 1 and x in (4, 5):
                pass  # under the hoist: open, the Miner's way in from the front
            elif x == 10 and z == 9:
                b.set(x, 1, z, "spruce_fence_gate", facing="east", open=True, in_wall=False, powered=False)
            else:
                fence(b, x, 1, z, "spruce_fence")
    lantern(b, 0, 2, 1)
    lantern(b, 10, 2, 11)
    lantern(b, 10, 2, 8)
    # The steps: in from the gate at z 9 on the curb's level, down the pit's east side to a landing at y -5
    b.set(9, 0, 9, "air")
    b.set(9, -1, 9, "stone_bricks")  # the top step is the ground, paved
    b.set(9, -1, 10, "stone_bricks")
    for i, z in enumerate((8, 7, 6, 5)):
        y = -2 - i
        stairs(b, 9, y, z, STONE_BRICK, "south")
        for yy in range(y + 1, 0):
            b.set(9, yy, z, "air")
    for z in (4, 3, 2):
        b.set(9, -5, z, "stone_bricks")
        for yy in range(-4, 0):
            b.set(9, yy, z, "air")
    # The steps' far wall (x 10, under the curb) lined with stone bricks, a torch on it halfway down
    for z in range(2, 11):
        for y in range(-QUARRY_BASEMENT, 0):
            b.set(10, y, z, MOSSY_BRICK_MIX.at(10, y, z))
    lantern(b, 9, -4, 2)  # on the landing, at its dead end
    # The hoist over the north rim: two log posts on the curb, a beam across, a chain and lantern over the pit
    for x in (3, 6):
        for y in range(1, 5):
            log(b, x, y, 1, "spruce_log")
    for x in range(2, 8):
        log(b, x, 5, 1, "spruce_log", axis="x")
    b.set(4, 4, 1, "chain", axis="y", waterlogged=False)
    lantern(b, 4, 3, 1, hanging=True)
    b.set(5, 4, 1, "chain", axis="y", waterlogged=False)
    b.set(5, 3, 1, "chain", axis="y", waterlogged=False)
    # The shed: footing, posts, plank walls, a door at the front (north) with a step
    sx0, sz0, sx1, sz1 = 11, 3, 15, 9
    for x in range(sx0, sx1 + 1):
        for z in range(sz0, sz1 + 1):
            edge = x in (sx0, sx1) or z in (sz0, sz1)
            b.set(x, 0, z, SHED_FOOT.at(x, 0, z) if edge else "spruce_planks")
    for x, z in ((sx0, sz0), (sx1, sz0), (sx0, sz1), (sx1, sz1), (sx0, 6), (sx1, 6)):
        for y in range(1, 4):
            log(b, x, y, z, "spruce_log")
    for y in range(1, 4):
        for x in range(sx0, sx1 + 1):
            for z in (sz0, sz1):
                b.blocks.setdefault((x, y, z), ("minecraft:spruce_planks", ()))
        for z in range(sz0, sz1 + 1):
            for x in (sx0, sx1):
                b.blocks.setdefault((x, y, z), ("minecraft:spruce_planks", ()))
    beam_ring(b, sx0, sz0, sx1, sz1, 4, "stripped_spruce_log")
    door(b, 13, 1, sz0, "spruce_door", facing="north")
    stairs(b, 13, 0, sz0 - 1, COBBLE, "south")
    lantern(b, 12, 3, sz0 - 1)  # light at the door: on a post of its own beside the step
    b.set(12, 2, sz0 - 1, "spruce_fence")
    b.set(12, 1, sz0 - 1, "spruce_fence")
    b.set(12, 0, sz0 - 1, "cobblestone")
    # Windows: one each side toward the pit and away from it, and one at the back
    window(b, sx0, 2, 4, "west")
    window(b, sx0, 2, 8, "west")
    window(b, sx1, 2, 5, "east")
    window(b, sx1, 2, 8, "east")
    window(b, 13, 2, sz1, "south")
    # Inside: the blast furnace at the back east, a double chest along the back, a crafting table, a lantern
    b.set(14, 1, 7, "blast_furnace", facing="west", lit=False)
    b.set(12, 1, 8, "chest", facing="north", type="right", waterlogged=False)
    b.set(13, 1, 8, "chest", facing="north", type="left", waterlogged=False)
    b.set(14, 1, 8, "crafting_table")
    b.set(12, 1, 4, "spruce_trapdoor", facing="east", half="top", open=False, powered=False, waterlogged=False)
    b.set(14, 1, 4, "spruce_trapdoor", facing="west", half="top", open=False, powered=False, waterlogged=False)
    b.set(12, 2, 4, "flower_pot")
    lantern(b, 14, 2, 8)  # on the crafting table
    # The roof: dark oak over the spruce walls, ridge along the shed (north-south), its ends closed in planks
    gable_roof(b, sx0 - 1, sx1 + 1, sz0 - 1, sz1 + 1, 4, DARK_OAK, axis="z", gable="spruce_planks", gable_at=(sz0, sz1),
               eave_trim=DARK_OAK)
    # A woodpile behind the shed
    for z in (10, 11):
        log(b, 15, 0, z, "spruce_log", axis="x")
        log(b, 14, 0, z, "spruce_log", axis="x")
    log(b, 15, 1, 10, "spruce_log", axis="x")
    b.fill_air()
    return b
