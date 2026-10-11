"""
The Wonders' designs (ROADMAP 35.9 onwards, docs/design/M35.md): each Wonder is one function drawing it whole on a
StagedBuild, stage by stage, and one entry in WONDERS with its data file's other fields. generate.py saves every Wonder
with save_wonder(): the stage parts (structure/wonder/<id>/stage_<n>_<k>.nbt, at most 48 a side, by kit.wonder_stages)
and its data file (data/aliveworkplace/wonders/<id>.json) with those parts filled in, so the two always agree.

A new Wonder: draw it in a function here, add its entry to WONDERS, add it to WonderBlueprintGameTests.WONDERS (with its
block count) and to the wonder_gallery scene's list.

The Great Cathedral (35.9), 39 x 53 x 55, front (the west front, with the towers) at z = 0. Design on paper:
- Cross plan. The body is 27 wide (x 6-32 with its buttresses): a nave (x 15-23 inside, arcade walls x 14 and 24) of
  five bays (piers every 6, z 9-39) between two aisles (x 9-13 and 25-29, outer walls x 8 and 30). Transepts cross it at
  z 39-45 (x 1-37, so 39 wide with their buttresses); the choir runs on to a rounded apse (radius 5 round (19, 48)),
  ending at z 54 with its buttresses.
- Heights: aisle walls to 10 under lean-to roofs; nave, transept, choir and apse walls to 23 under one steep pitch (two
  up for one in) of deepslate tiles: nave ridge 35, the narrower transepts' ridge 31 dying into it, the apse a half cone.
  The two west towers (x 6-14 and 24-32, z 1-9) rise to 44 under stone spires reaching 52.
- West front: a deep door, three pointed arches stepping in (z 1-3) to three dark oak doors (z 4) under a carved
  tympanum, lanterns on the jambs, flower beds at the towers' feet; over it the rose window (radius 3, centre y 17) of
  white, light blue, yellow and red glass in a polished andesite ring; the gable over it with a slit window.
- Flanks: flying buttresses at every pier (piers x 6-7 with pinnacles, the flyer climbing over the aisle roof to the
  clerestory at y 19), tall 3-wide lancets in the aisles (y 3-8) and the clerestory (y 17-22).
- Transepts: gable windows (3 wide, y 5-17) over a door in each end, corner buttresses, two lower gabled annexes on
  their east side (the sacristy and a side chapel), each under its own steep roof.
- Towers: clasping buttresses to y 17, slit windows, open belfries (3-wide arches y 35-39 on every face) with three
  bells (two in the north-west tower, one in the other) on a dark oak beam, a parapet with corner pinnacles, spires.
- Inside: polished andesite piers with red-and-gold banners and lanterns in the arcade arches, dark oak pews either
  side of a red runner, lanterns under the aisle roofs, a raised sanctuary in the apse with an altar of gold and candles,
  three side chapels with lecterns (one in each transept, one in the south-east annex) and the sacristy with a brewing
  stand (an Undertaker's place) in the north-east annex. Ladders up both towers to the belfries and the roof.
- Palette (60/30/10): stone bricks, mossy and cracked low down; polished andesite piers, arches and trim; deepslate
  tile roofs; dark oak; gold only at the altar. Light blue glass in the lancets (one glass type: panes throughout).
- Stages, each ending tidy: I the foundations (the whole floor, the plinth and its skirt of steps) and the west front
  (the towers to their first roof at 23, the portal); II the nave (aisles, buttresses, arcade, clerestory, roof, pews,
  the west wall with the rose's ring waiting for its glass); III the transepts and the choir (with the annexes, chapels,
  altar and the crossing); IV the towers' upper half, belfries, bells and spires, and the rose window's glass.
"""
import json
import math
import os
import re
from kit import *

WONDER_DATA = os.path.join(ROOT, "src/main/resources/data/aliveworkplace/wonders")


class Weathered(Mix):
    """Stone bricks: mossy and cracked in clusters low down (y 1-2), now and then cracked above."""

    LOW = Mix((6, "stone_bricks"), (3, "mossy_stone_bricks"), (1, "cracked_stone_bricks"), seed=11)
    MID = Mix((14, "stone_bricks"), (1, "mossy_stone_bricks"), (1, "cracked_stone_bricks"), seed=12)
    HIGH = Mix((24, "stone_bricks"), (1, "cracked_stone_bricks"), seed=13)

    def __init__(self):
        super().__init__((1, "stone_bricks"))

    def at(self, x, y, z):
        return (self.LOW if y <= 2 else self.MID if y <= 4 else self.HIGH).at(x, y, z)


# --- the Great Cathedral ------------------------------------------------------------------------------------------------
CW_, CH_, CD_ = 39, 53, 55
STONE = Weathered()
PLINTH = Mix((5, "stone_bricks"), (3, "mossy_stone_bricks"), (2, "cracked_stone_bricks"), seed=14)
TRIM = "polished_andesite"
CARVED = "chiseled_stone_bricks"
ROOF = DEEPSLATE_TILE
GLASS = "light_blue_stained_glass_pane"
PIERS = (15, 21, 27, 33)  # the nave's piers (z); 9 is the towers' back wall, 39 the crossing
BAYS = (12, 18, 24, 30, 36)  # the five bays' middles
APSE = (19, 48)
TOWERS = (6, 24)  # the towers' west x (each 9 square, z 1-9)
BANNER = [("yellow", "minecraft:straight_cross"), ("yellow", "minecraft:border")]


class Side:
    """One half of the cathedral: the north half as drawn, or mirrored across x = 19."""

    def __init__(self, mirrored):
        self.m = mirrored

    def x(self, x):
        return CW_ - 1 - x if self.m else x

    def f(self, facing):
        return {"east": "west", "west": "east"}.get(facing, facing) if self.m else facing


SIDES = (Side(False), Side(True))


def apse_distance(x, z):
    return math.hypot(x - APSE[0], z - APSE[1])


def plan():
    """The cathedral's footprint: every (x, z) inside or under its outer walls."""
    cells = set()

    def rect(x0, x1, z0, z1):
        cells.update((x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1))

    rect(6, 32, 1, 9)  # the towers and the porch between them
    rect(8, 30, 9, 39)  # nave and aisles
    rect(1, 37, 39, 45)  # transepts and crossing
    rect(14, 24, 45, 48)  # choir
    rect(2, 8, 45, 51)  # sacristy
    rect(30, 36, 45, 51)  # south-east chapel
    cells.update((x, z) for x in range(13, 26) for z in range(48, 55) if apse_distance(x, z) <= 5.4)
    return cells


def apse_wall():
    inside = {(x, z) for x in range(13, 26) for z in range(48, 55) if apse_distance(x, z) <= 5.4}
    around = inside | {(x, z) for x in range(14, 25) for z in range(45, 48)}
    return sorted(c for c in inside if any((c[0] + dx, c[1] + dz) not in around for dx, dz in DIRS.values()))


def apse_cell(angle, r):
    """The cell {@code r} out from the apse's centre at {@code angle} degrees (0 = east along +x, 90 = due east end)."""
    return round(APSE[0] + r * math.cos(math.radians(angle))), round(APSE[1] + r * math.sin(math.radians(angle)))


def column(b, x, z, y0, y1, name):
    for y in range(y0, y1 + 1):
        b.set(x, y, z, resolve(name, x, y, z))


def lancet(b, along, fixed, middle, y0, y1, width=3, glass=GLASS):
    """A pointed window in a wall: {@code width} wide round {@code middle}, rows y0-y1 and the point one higher.
    along: "z" for a wall running along z at x = fixed, "x" for one along x at z = fixed."""
    half = width // 2
    for y in range(y0, y1 + 2):
        for k in range(-half, half + 1):
            if y == y1 + 1 and k != 0:
                continue
            x, z = (fixed, middle + k) if along == "z" else (middle + k, fixed)
            pane(b, x, y, z, glass)


def carve(b, along, fixed, lo, hi, y0, y1):
    """A pointed arch opening (air) in a wall from lo to hi along it, straight to y1, then stepping in to its point."""
    y = y0
    while lo <= hi:
        for k in range(lo, hi + 1):
            x, z = (fixed, k) if along == "z" else (k, fixed)
            b.clear(x, y, z, x, y, z)
        if y >= y1:
            lo, hi = lo + 1, hi - 1
        y += 1


def banner(b, x, y, z, facing):
    b.set_nbt(x, y, z, Compound({"id": String("minecraft:banner"), "patterns": List[Compound]([
        Compound({"color": String(c), "pattern": String(p)}) for c, p in BANNER])}))
    b.set(x, y, z, "red_wall_banner", facing=facing)


def candles(b, x, y, z, n=3):
    b.set(x, y, z, "white_candle", candles=n, lit=False, waterlogged=False)


def buttress(b, x, z, y1, cap_facing):
    """A stone buttress column from the plinth to y1, capped with a stair leaning on the wall."""
    column(b, x, z, 1, y1, STONE)
    stairs(b, x, y1 + 1, z, STONE_BRICK, cap_facing)


# The buttresses' feet (laid with the foundations) and their columns.
def tower_buttresses():
    """(x, z, top, cap facing) of the towers' clasping buttresses."""
    out = []
    for tx in TOWERS:
        for x in (tx, tx + 8):
            out.append((x, 0, 17, "south"))
        out.append((tx - 1 if tx == 6 else tx + 9, 1, 17, "east" if tx == 6 else "west"))
    return out


def transept_buttresses():
    return [(x, z, 16, "east" if x == 0 else "west") for x in (0, 38) for z in (39, 45)]


def apse_buttresses():
    out = []
    for angle in (36, 72, 108, 144):  # between the apse's windows
        x, z = apse_cell(angle, 6.2)
        dx, dz = x - APSE[0], z - APSE[1]
        out.append((x, z, 15, ("west" if dx > 0 else "east") if abs(dx) > abs(dz) else "north"))
    return out


def aisle_piers():
    return [(s.x(x), z) for s in SIDES for z in PIERS for x in (6, 7)]


# --- stage I: the foundations and the west front ----------------------------------------------------------------------
def foundations(b):
    cells = plan()
    edge = {c for c in cells if any((c[0] + dx, c[1] + dz) not in cells for dx, dz in DIRS.values())}
    for x, z in cells:
        if (x, z) in edge:
            b.set(x, 0, z, PLINTH.at(x, 0, z))
        elif 15 <= x <= 23 or z >= 39:
            b.set(x, 0, z, TRIM if (x + z) % 2 == 0 else "stone_bricks")
        else:
            b.set(x, 0, z, "stone_bricks")
    feet = [(x, z) for x, z, _, _ in tower_buttresses() + transept_buttresses() + apse_buttresses()] + aisle_piers()
    for x, z in feet:
        b.set(x, 0, z, PLINTH.at(x, 0, z))
    # flower beds at the towers' feet, between their buttresses
    for tx in TOWERS:
        flower_bed(b, tx + 1, 0, tx + 7, 0, 0, ["poppy", "azure_bluet", None, "oxeye_daisy", "cornflower", None])
    # steps up to the west door, and a skirt of steps round the rest
    for x in range(15, 24):
        if x in (15, 23):
            column(b, x, 0, 0, 5, TRIM)
            lantern(b, x, 6, 0)
        else:
            stairs(b, x, 0, 0, STONE_BRICK, "south")
    for x in range(CW_):
        for z in range(CD_):
            if (x, z) in cells or b.get(x, 0, z):
                continue
            for facing, (dx, dz) in DIRS.items():
                if (x + dx, z + dz) in cells:
                    stairs(b, x, 0, z, STONE_BRICK, facing)
                    break


def tower_box(tx):
    return tx, tx + 8, 1, 9  # x0, x1, z0, z1


def tower_shell(b, tx, y0, y1):
    """The tower's walls from y0 to y1: two thick, polished andesite at the corners."""
    x0, x1, z0, z1 = tower_box(tx)
    walls(b, x0, z0, x1, z1, y0, y1, STONE)
    walls(b, x0 + 1, z0 + 1, x1 - 1, z1 - 1, y0, y1, STONE)
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        column(b, x, z, y0, y1, TRIM)


def slit(b, tx, y0, y1):
    """Slit windows through the thick wall, on the front and the outer side."""
    x0, x1, z0, z1 = tower_box(tx)
    mid, outer = tx + 4, ((x0, 1) if tx == 6 else (x1, -1))
    for y in range(y0, y1 + 1):
        pane(b, mid, y, z0, GLASS)
        b.clear(mid, y, z0 + 1, mid, y, z0 + 1)
        pane(b, outer[0], y, 5, GLASS)
        b.clear(outer[0] + outer[1], y, 5, outer[0] + outer[1], y, 5)


def band(b, tx, y, inner=True, back="all"):
    """A projecting course of upside-down stairs round the tower at y (a string course or, at the top, a corbel table).
    inner: also on the side over the nave; back: "all", "aisle" (only beside the aisle roof, low down) or None."""
    x0, x1, z0, z1 = tower_box(tx)
    inner_x = x1 + 1 if tx == 6 else x0 - 1
    outer_x = x0 - 1 if tx == 6 else x1 + 1
    for x in range(x0 - 1, x1 + 2):
        if x != inner_x or inner:
            stairs(b, x, y, z0 - 1, STONE_BRICK, "south", top=True)
            if back == "all" or back == "aisle" and (x <= 12 if tx == 6 else x >= 26):
                stairs(b, x, y, z1 + 1, STONE_BRICK, "north", top=True)
    for z in range(z0, z1 + 1):
        stairs(b, outer_x, y, z, STONE_BRICK, "east" if tx == 6 else "west", top=True)
        if inner:
            stairs(b, inner_x, y, z, STONE_BRICK, "west" if tx == 6 else "east", top=True)


def ladder(b, tx, y0, y1):
    x0, x1, z0, z1 = tower_box(tx)
    lx, face = (x0 + 2, "east") if tx == 6 else (x1 - 2, "west")
    for y in range(y0, y1 + 1):
        b.set(lx, y, z1 - 2, "ladder", facing=face, waterlogged=False)


def tower_lower(b, tx):
    x0, x1, z0, z1 = tower_box(tx)
    tower_shell(b, tx, 1, 22)
    box(b, x0 + 2, 12, z0 + 2, x1 - 2, 12, z1 - 2, "dark_oak_planks")
    box(b, x0, 23, z0, x1, 23, z1, "stone_bricks")
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        b.set(x, 23, z, TRIM)
    slit(b, tx, 4, 8)
    slit(b, tx, 14, 19)
    band(b, tx, 11, inner=False, back=None)
    band(b, tx, 22, inner=False, back="aisle")
    # into the tower from the porch, and a ladder up its outer wall
    inner = (x1, x1 - 1) if tx == 6 else (x0, x0 + 1)
    for x in inner:
        carve(b, "z", x, 5, 5, 1, 2)
    ladder(b, tx, 1, 23)
    for x, z, top, facing in tower_buttresses():
        if (tx == 6) == (x < 19):
            buttress(b, x, z, top, facing)


def west_portal(b):
    """The deep west door: three pointed arches stepping in from the front to the doors at z 4."""
    for z in range(1, 5):
        for x in range(15, 24):
            column(b, x, z, 1, 10, STONE)
    box(b, 15, 10, 1, 23, 10, 1, CARVED)
    for z, (lo, hi, top) in zip((1, 2, 3), ((16, 22, 6), (17, 21, 5), (18, 20, 4))):
        # the arch's ring in polished andesite, then the opening
        for k in (lo - 1, hi + 1):
            column(b, k, z, 1, top, TRIM)
        carve(b, "x", z, lo, hi, 1, top)
    box(b, 18, 3, 4, 20, 4, 4, CARVED)
    b.set(19, 5, 4, CARVED)
    for x, hinge in ((18, "left"), (19, "left"), (20, "right")):
        door(b, x, 1, 4, "dark_oak_door", "north", hinge=hinge)
    lantern(b, 19, 5, 3, hanging=True)
    # a pointed gablet over the outer arch, standing out from the wall, its tip carved
    for k in range(4):
        stairs(b, 15 + k, 7 + k, 0, STONE_BRICK, "east")
        stairs(b, 23 - k, 7 + k, 0, STONE_BRICK, "west")
        for x in range(16 + k, 23 - k):
            b.set(x, 7 + k, 0, STONE.at(x, 7 + k, 0))
    b.set(19, 11, 0, CARVED)
    wall_block(b, 19, 12, 0, "stone_brick_wall")


def stage_1(b):
    foundations(b)
    for tx in TOWERS:
        tower_lower(b, tx)
    west_portal(b)


# --- stage II: the nave -------------------------------------------------------------------------------------------------
def roof_layer():
    """The nave, choir and transept roofs together (kit.roofs: the lower transept ridge dies into the nave's)."""
    t = Build(CW_, CH_, CD_)
    roofs(t,
          lambda r: gable_roof(r, 0, 38, 38, 46, 23, ROOF, axis="x", gable=STONE, gable_at=(1, 37), steep=True,
                               eave_trim=STONE_BRICK),
          lambda r: gable_roof(r, 13, 25, 0, 47, 23, ROOF, axis="z", gable=STONE, gable_at=(1,), steep=True,
                               eave_trim=STONE_BRICK))
    out = {}
    for (x, y, z), block in t.blocks.items():
        if z <= 9 and x in (13, 14, 24, 25):
            continue  # inside the towers
        out[(x, y, z)] = block
    return out


def nave_part(x, z):
    return z <= 38 and 13 <= x <= 25


def aisle(b, s):
    X, F = s.x, s.f
    for z in range(10, 39):
        column(b, X(8), z, 1, 10, STONE)
        column(b, X(14), z, 1, 23, STONE)
    column(b, X(14), 39, 1, 23, TRIM)
    for p in PIERS:
        column(b, X(14), p, 1, 10, TRIM)
        b.set(X(14), 11, p, CARVED)
    # the lean-to aisle roof, rising to the clerestory
    for z in range(10, 39):
        for x in range(7, 14):
            if not (x == 7 and z in PIERS):
                stairs(b, X(x), x + 3, z, STONE_BRICK if x == 7 else ROOF, F("east"))
    for c in BAYS:
        lancet(b, "z", X(8), c, 3, 7)
        stairs(b, X(7), 2, c - 1, STONE_BRICK, F("east"), top=True)
        stairs(b, X(7), 2, c, STONE_BRICK, F("east"), top=True)
        stairs(b, X(7), 2, c + 1, STONE_BRICK, F("east"), top=True)
        lancet(b, "z", X(14), c, 17, 21)
    # the arcade: a pointed arch through the nave wall in every bay, a lantern under each
    for p in (9,) + PIERS:
        carve(b, "z", X(14), p + 1, p + 5, 1, 7)
        lantern(b, X(14), 9, p + 3, hanging=True)
        lantern(b, X(11), 13, p + 3, hanging=True)
    # buttress piers with pinnacles and their flyers over the aisle roof to the clerestory
    for p in PIERS:
        column(b, X(6), p, 1, 8, STONE)
        stairs(b, X(6), 9, p, STONE_BRICK, F("east"))
        column(b, X(7), p, 1, 13, STONE)
        b.set(X(7), 14, p, CARVED)
        wall_block(b, X(7), 15, p, "stone_brick_wall")
        wall_block(b, X(7), 16, p, "stone_brick_wall")
        for i in range(6):
            stairs(b, X(8 + i), 14 + i, p, STONE_BRICK, F("east"))
            if i < 5:
                stairs(b, X(9 + i), 14 + i, p, STONE_BRICK, F("west"), top=True)
    # banners on the piers, facing into the nave
    for p in PIERS:
        banner(b, X(15), 6, p, F("east"))


def west_wall(b):
    """The west wall over the portal, two thick, with the rose window's ring in its face and the round opening behind
    waiting for the glass (stage IV), and the gable."""
    for x in range(15, 24):
        column(b, x, 1, 11, 23, STONE)
        column(b, x, 2, 11, 22, STONE)
    for x in range(15, 24):
        for y in range(11, 24):
            d = math.hypot(x - 19, y - 17)
            if d <= 3.2:
                b.clear(x, y, 1, x, y, 2)
            elif d <= 4.3:
                b.set(x, y, 1, TRIM)


def nave(b, roof):
    for (x, y, z), block in roof.items():
        if nave_part(x, z):
            b.blocks[(x, y, z)] = block
    for s in SIDES:
        aisle(b, s)
    west_wall(b)
    for y in range(26, 30):
        pane(b, 19, y, 1, GLASS)
    # the red runner and the pews either side of it
    for z in range(5, 39):
        b.set(19, 1, z, "red_carpet")
    for z in range(11, 38, 2):
        for x in list(range(15, 19)) + list(range(20, 24)):
            stairs(b, x, 1, z, DARK_OAK, "north")


def stage_2(b, roof):
    nave(b, roof)


# --- stage III: the transepts and the choir --------------------------------------------------------------------------
def transept(b, s):
    X, F = s.x, s.f
    # the end wall with its door and gable window, the side walls with lancets
    for z in range(39, 46):
        column(b, X(1), z, 1, 23, STONE)
    for x in range(1, 9):
        column(b, X(x), 39, 1, 23, STONE)
    for x in range(9, 14):
        column(b, X(x), 39, x + 4, 23, STONE)  # over the aisle roof
    for x in range(1, 14):
        column(b, X(x), 45, 1, 23, STONE)
    for z in (39, 45):
        column(b, X(1), z, 1, 23, TRIM)
    lancet(b, "z", X(1), 42, 5, 16)
    door(b, X(1), 1, 42, "dark_oak_door", F("west"))
    for z in (41, 42, 43):
        slab(b, X(0), 4, z, STONE_BRICK)
        b.set(X(1), 3, z, CARVED)
        b.set(X(1), 4, z, CARVED)
    for z in (41, 43):
        lantern(b, X(0), 3, z, hanging=True)
    lancet(b, "x", 39, X(4), 4, 14)
    lancet(b, "x", 45, X(11), 4, 14)
    lancet(b, "x", 45, X(5), 18, 21)
    door(b, X(5), 1, 45, "dark_oak_door", "south")
    # the crossing: the transept opens to it through a tall arch, a wall over it carrying the roofs
    for z in range(40, 45):
        column(b, X(14), z, 1, 23, STONE)
    carve(b, "z", X(14), 40, 44, 1, 13)
    column(b, X(14), 45, 1, 23, TRIM)
    for x, z, top, facing in transept_buttresses():
        if (x < 19) != s.m:
            buttress(b, x, z, top, facing)
    # a side chapel against the west wall: an altar under the window, candles, a lectern
    b.set(X(3), 1, 40, CARVED)
    b.set(X(4), 1, 40, TRIM)
    b.set(X(5), 1, 40, CARVED)
    candles(b, X(3), 2, 40)
    candles(b, X(5), 2, 40)
    b.set(X(4), 1, 42, "lectern", facing="south", has_book=False, powered=False)
    banner(b, X(15), 6, 39, F("east"))
    banner(b, X(15), 6, 45, F("east"))


def annex(b, s):
    """A low gabled room against the transept's east wall: x 2-8, z 45-51."""
    X = s.x
    for z in range(46, 52):
        column(b, X(2), z, 1, 7, STONE)
        column(b, X(8), z, 1, 7, STONE)
    for x in range(3, 8):
        column(b, X(x), 51, 1, 7, STONE)
    box(b, X(3), 7, 46, X(7), 7, 50, "dark_oak_planks")
    lo, hi = sorted((X(1), X(9)))
    gable_roof(b, lo, hi, 46, 52, 7, ROOF, axis="z", gable=STONE, gable_at=(51,), steep=True, eave_trim=STONE_BRICK)
    lancet(b, "z", X(2), 48, 2, 4, width=1)
    lancet(b, "x", 51, X(5), 2, 4, width=1)
    lancet(b, "x", 51, X(5), 9, 10, width=1)
    lantern(b, X(5), 6, 48, hanging=True)


def sacristy(b):
    s = SIDES[0]
    annex(b, s)
    b.set(5, 1, 50, "brewing_stand", has_bottle_0=False, has_bottle_1=False, has_bottle_2=False)
    for x in (3, 7):
        column(b, x, 50, 1, 2, "bookshelf")
    b.set(4, 1, 50, "dark_oak_planks")
    b.set(6, 1, 50, "dark_oak_planks")
    candles(b, 4, 2, 50, 2)
    candles(b, 6, 2, 50, 2)


def east_chapel(b):
    s = SIDES[1]
    annex(b, s)
    for x in (32, 33, 34):
        b.set(x, 1, 50, CARVED if x != 33 else TRIM)
    candles(b, 32, 2, 50)
    candles(b, 34, 2, 50)
    b.set(33, 1, 48, "lectern", facing="north", has_book=False, powered=False)


def choir(b, roof):
    for (x, y, z), block in roof.items():
        if not nave_part(x, z):
            b.blocks[(x, y, z)] = block
    for s in SIDES:
        transept(b, s)
    sacristy(b)
    east_chapel(b)
    for s in SIDES:
        for z in (46, 47):
            column(b, s.x(14), z, 1, 23, STONE)
        lancet(b, "z", s.x(14), 47, 5, 17, width=1)
    wall = apse_wall()
    for x, z in wall:
        column(b, x, z, 1, 23, STONE)
    for angle in (18, 54, 90, 126, 162):
        x, z = min(wall, key=lambda c: abs(math.degrees(math.atan2(c[1] - APSE[1], c[0] - APSE[0])) - angle))
        for y in range(5, 19):
            pane(b, x, y, z, GLASS)
    # the half-cone roof over the apse, matching the nave's pitch where they meet
    for i in range(7):
        r_out = 6.4 - i
        y = 23 + 2 * i
        for x in range(12, 27):
            for z in range(48, 55):
                d = apse_distance(x, z)
                if not (r_out - 1 < d <= r_out):
                    continue
                if d < 0.5:
                    b.set(x, y - 1, z, ROOF.full)
                    slab(b, x, y, z, ROOF)
                    continue
                dx, dz = x - APSE[0], z - APSE[1]
                facing = ("west" if dx > 0 else "east") if abs(dx) > abs(dz) else "north"
                stairs(b, x, y, z, ROOF, facing)
                if i > 0:
                    b.set(x, y - 1, z, ROOF.full)
                elif b.get(x, y - 1, z) is None:
                    stairs(b, x, y - 1, z, STONE_BRICK, facing, top=True)
    for x, z, top, facing in apse_buttresses():
        buttress(b, x, z, top, facing)
    # the sanctuary: a step up, the altar of gold and candles
    for x in range(15, 24):
        stairs(b, x, 1, 46, ANDESITE, "south")
        b.set(x, 1, 47, TRIM)
    for x in range(13, 26):
        for z in range(48, 54):
            if apse_distance(x, z) <= 5.4 and (x, z) not in wall:
                b.set(x, 1, z, TRIM)
    for x in range(17, 22):
        b.set(x, 2, 50, "gold_block" if 18 <= x <= 20 else CARVED)
    for x, n in ((17, 2), (18, 3), (19, 4), (20, 3), (21, 2)):
        candles(b, x, 3, 50, n)
    b.set(19, 2, 47, "red_carpet")
    for z in range(39, 46):
        b.set(19, 1, z, "red_carpet")


def stage_3(b, roof):
    choir(b, roof)


# --- stage IV: the towers and the rose window ------------------------------------------------------------------------
def tower_upper(b, tx, bells):
    x0, x1, z0, z1 = tower_box(tx)
    mid, midz = tx + 4, 5
    tower_shell(b, tx, 24, 33)
    slit(b, tx, 26, 31)
    ladder(b, tx, 24, 34)
    # the belfry: a floor at 34, thinner walls with an open arch on every face, the bells on a beam
    box(b, x0, 34, z0, x1, 34, z1, "stone_bricks")
    box(b, x0 + 1, 34, z0 + 1, x1 - 1, 34, z1 - 1, "dark_oak_planks")
    b.set(x0 + 2 if tx == 6 else x1 - 2, 34, z1 - 2, "ladder", facing="east" if tx == 6 else "west", waterlogged=False)
    walls(b, x0, z0, x1, z1, 35, 43, STONE)
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        column(b, x, z, 34, 43, TRIM)
    band(b, tx, 34)
    band(b, tx, 43)
    for along, fixed, middle in (("x", z0, mid), ("x", z1, mid), ("z", x0, midz), ("z", x1, midz)):
        carve(b, along, fixed, middle - 1, middle + 1, 35, 38)
    for x in range(x0 + 1, x1):
        log(b, x, 40, midz, "dark_oak_log", axis="x")
    for bx in bells:
        b.set(bx, 39, midz, "bell", attachment="ceiling", facing="north", powered=False)
    lx, face = (x0 + 1, "east") if tx == 6 else (x1 - 1, "west")
    for y in range(35, 44):
        b.set(lx, y, z1 - 1, "ladder", facing=face, waterlogged=False)
    # the roof: a parapet with corner pinnacles round a tall hollow stone spire
    box(b, x0, 44, z0, x1, 44, z1, "stone_bricks")
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                wall_block(b, x, 45, z, "stone_brick_wall")
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        b.set(x, 45, z, CARVED)
        for y in (46, 47, 48):
            wall_block(b, x, y, z, "stone_brick_wall")
    hip_roof(b, x0 + 1, x1 - 1, z0 + 1, z1 - 1, 45, STONE_BRICK, steep=True)
    b.set(mid, 51, midz, "stone_bricks")
    wall_block(b, mid, 52, midz, "stone_brick_wall")


def rose_window(b):
    for x in range(16, 23):
        for y in range(14, 21):
            d = math.hypot(x - 19, y - 17)
            if d > 3.2:
                continue
            spoke = x == 19 or y == 17 or abs(x - 19) == abs(y - 17)
            glass = "yellow" if d < 0.5 else "white" if d < 1.6 or (spoke and d < 2.6) else "light_blue" if d < 2.6 else "red"
            pane(b, x, y, 2, f"{glass}_stained_glass_pane")


def stage_4(b):
    tower_upper(b, 6, (9, 11))
    tower_upper(b, 24, (28,))
    rose_window(b)


def great_cathedral():
    b = StagedBuild(CW_, CH_, CD_)
    roof = roof_layer()
    b.begin(1)
    stage_1(b)
    b.begin(2)
    stage_2(b, roof)
    b.begin(3)
    stage_3(b, roof)
    b.begin(4)
    stage_4(b)
    return b


# --- the Wonders' data files -------------------------------------------------------------------------------------------
def goods(*lines):
    out = []
    for kind, ident, count, job, *extra in lines:
        line = {kind: ident, "count": count}
        if extra:
            line["potion"] = extra[0]
        line["job"] = job
        out.append(line)
    return out


WONDERS = {
    "great_cathedral": {
        "draw": great_cathedral,
        "icon": "minecraft:bell",
        "crew": 12,
        "requirements": [
            {"type": "rank_at_least", "rank": "town"},
            {"type": "legend", "id": "aliveworkplace:seer",
             "stand_in": [{"type": "job_level", "job": "minecraft:cleric", "level": 5, "count": 1}]},
            {"type": "finished", "blueprint": "aliveworkplace:chapel", "count": 3},
        ],
        "goods": [
            goods(("item", "minecraft:bread", 128, "aliveworkplace:chef"),
                  ("item", "minecraft:iron_pickaxe", 8, "minecraft:toolsmith"),
                  ("item", "minecraft:gold_ingot", 16, "minecraft:armorer")),
            goods(("item", "minecraft:white_candle", 64, "minecraft:leatherworker"),
                  ("item", "minecraft:honeycomb", 32, "aliveworkplace:beekeeper"),
                  ("item", "minecraft:white_wool", 64, "minecraft:shepherd")),
            goods(("item", "minecraft:book", 16, "minecraft:librarian"),
                  ("item", "minecraft:potion", 12, "minecraft:cleric", "minecraft:healing"),
                  ("tag", "minecraft:small_flowers", 64, "aliveworkplace:florist")),
            goods(("item", "minecraft:chiseled_stone_bricks", 32, "minecraft:mason"),
                  ("item", "minecraft:cake", 16, "aliveworkplace:chef"),
                  ("item", "minecraft:bone_meal", 64, "aliveworkplace:composter")),
        ],
        "powers": [
            {"type": "undertakers", "search": "village", "revive_by": "morning"},
            {"type": "weddings", "mood": 15, "days": 3, "gifted_factor": 2.0},
            {"type": "beauty", "points": 8},
        ],
    },
}


def save_wonder(wonder_id, structures=MAIN_STRUCTURES, data=WONDER_DATA):
    """Saves a Wonder's stage parts and writes its data file (docs/design/M35.md's format) with those parts."""
    w = WONDERS[wonder_id]
    stages = wonder_stages(w["draw"](), wonder_id, structures)
    assert len(stages) == len(w["goods"]), f"{wonder_id}: {len(stages)} stages drawn, {len(w['goods'])} with goods"
    key = f"wonder.aliveworkplace.{wonder_id}"
    out = {
        "name": key + ".name",
        "description": key + ".description",
        "icon": w["icon"],
        "enabled": True,
        "crew": w["crew"],
        "requirements": w["requirements"],
        "stages": [{"name": f"{key}.stage.{n}",
                    "parts": [{"part": part, "offset": offset} for part, offset in parts],
                    "goods": w["goods"][n - 1]} for n, parts in enumerate(stages, 1)],
        "powers": w["powers"],
    }
    os.makedirs(data, exist_ok=True)
    text = json.dumps(out, indent=2)
    text = re.sub(r"\[\s+(-?\d+),\s+(-?\d+),\s+(-?\d+)\s+\]", r"[\1, \2, \3]", text)  # offsets on one line
    with open(os.path.join(data, wonder_id + ".json"), "w") as f:
        f.write(text + "\n")
