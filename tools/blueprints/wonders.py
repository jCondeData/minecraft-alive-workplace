#!/usr/bin/env python3
"""
The Wonders' designs (ROADMAP Milestone 35, docs/design/M35.md), each drawn stage by stage and saved with
kit.wonder_stages() as its stage parts (structure/wonder/<id>/stage_<n>_<k>.nbt, at most 48 a side) and its data file.

    python3 tools/blueprints/wonders.py      # the test folly and every Wonder (generate.py also saves the Wonders)

The test folly (35.3) is the GameTests' and showcase's small Wonder, saved as `aliveworkplace_test:wonder/test_folly/...`
with its whole tower as drawn beside it (`aliveworkplace_test:wonder_drawn/test_folly`), so a GameTest can check the
merged parts against it. On paper: a 9 x 9 plinth of andesite (mossy at the foot of the corners); a 7 x 7 stone-brick
tower on it in two storeys of 5 with chiseled corner posts, a centred arched door on the front (z = 0) with a lantern
either side, one window per face and storey, a cornice of stone-brick stairs at each floor line; a deepslate-tile dome
stepped in three rings on a drum, a lantern on a blackstone finial on top. Stage I: plinth, ground storey and its floor.
Stage II: the upper storey and its cornice. Stage III: the dome and finial.

The real Wonders are each one function drawing it whole on a StagedBuild, stage by stage, and one entry in WONDERS with
its data file's other fields. save_wonder() saves the parts and writes data/aliveworkplace/wonders/<id>.json with those
parts filled in, so the two always agree. A new Wonder: draw it in a function here, add its entry to WONDERS, add it to
WonderBlueprintGameTests.WONDERS (with its block count) and to the wonder_gallery scene's list.

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
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from kit import *  # noqa: E402,F401

WONDER_DATA = os.path.join(ROOT, "src/main/resources/data/aliveworkplace/wonders")


# --- the test folly (35.3) --------------------------------------------------------------------------------------
FOLLY_W, FOLLY_H, FOLLY_D = 9, 19, 9
FOLLY_BRICK = Family("stone_bricks", "stone_brick_stairs", "stone_brick_slab", wall="stone_brick_wall")
FOLLY_TILE = Family("deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab", wall="deepslate_tile_wall")
FOLLY_FACES = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}


def folly_storey(b, y0):
    """A storey of the 7 x 7 tower (x, z 1 to 7) from y0, 5 tall: walls, corner posts, a window centred on each face."""
    for y in range(y0, y0 + 5):
        for x in range(1, 8):
            for z in range(1, 8):
                if x in (1, 7) or z in (1, 7):
                    corner = x in (1, 7) and z in (1, 7)
                    b.set(x, y, z, "chiseled_stone_bricks" if corner and y in (y0, y0 + 4) else
                          "polished_andesite" if corner else
                          ("mossy_stone_bricks" if y0 == 1 and y == 1 and (x + z) % 3 == 0 else "stone_bricks"))
                else:
                    b.set(x, y, z, "air")
    # windows: two-tall glass panes in the middle of each face, a stair hood over them
    for y in (y0 + 1, y0 + 2):
        b.set(4, y, 7, "glass_pane")
        b.set(1, y, 4, "glass_pane")
        b.set(7, y, 4, "glass_pane")
        if y0 != 1:
            b.set(4, y, 1, "glass_pane")


def folly_cornice(b, y):
    """A ring of upside-down stone-brick stairs round the tower at y, one out from the wall (x, z 0 and 8), and a floor."""
    for i in range(0, 9):
        b.set(i, y, 0, "stone_brick_stairs", facing="south", half="top")
        b.set(i, y, 8, "stone_brick_stairs", facing="north", half="top")
        if 0 < i < 8:
            b.set(0, y, i, "stone_brick_stairs", facing="east", half="top")
            b.set(8, y, i, "stone_brick_stairs", facing="west", half="top")
    for x in range(1, 8):
        for z in range(1, 8):
            b.set(x, y, z, "smooth_stone" if (x, z) != (4, 4) else "chiseled_stone_bricks")


def folly_stage_1():
    b = Build(FOLLY_W, FOLLY_H, FOLLY_D)
    for x in range(FOLLY_W):
        for z in range(FOLLY_D):
            edge = x in (0, 8) or z in (0, 8)
            b.set(x, 0, z, ("mossy_cobblestone" if edge and (x in (0, 8)) and (z in (0, 1, 7, 8)) else "andesite") if edge else "polished_andesite")
    folly_storey(b, 1)
    # the door: an arched opening, iron-bound oak, lanterns either side
    b.door(4, 1, 1, "spruce_door", "north")
    b.set(4, 3, 1, "stone_brick_stairs", facing="north", half="top")
    b.set(3, 3, 0, "lantern", hanging=False)  # on the cornice-less face: a lantern on a wall post each side
    b.set(5, 3, 0, "lantern", hanging=False)
    b.set(3, 1, 0, "stone_brick_wall")
    b.set(5, 1, 0, "stone_brick_wall")
    b.set(3, 2, 0, "stone_brick_wall")
    b.set(5, 2, 0, "stone_brick_wall")
    b.set(4, 0, 0, "stone_brick_slab", type="double")
    folly_cornice(b, 6)
    return b


def folly_stage_2():
    b = folly_stage_1()
    folly_storey(b, 7)
    folly_cornice(b, 12)
    return b


def folly_stage_3():
    b = folly_stage_2()
    # the drum: a ring of stone bricks with chiseled posts, on the upper cornice's floor
    for x in range(1, 8):
        for z in range(1, 8):
            if x in (1, 7) or z in (1, 7):
                b.set(x, 13, z, "chiseled_stone_bricks" if x in (1, 4, 7) and z in (1, 4, 7) else "stone_bricks")
    # the dome: stepped rings of deepslate-tile stairs, each filled behind, a tile cap
    for y, lo, hi in ((14, 1, 7), (15, 2, 6), (16, 3, 5)):
        for x in range(lo, hi + 1):
            for z in range(lo, hi + 1):
                if x == lo:
                    b.set(x, y, z, "deepslate_tile_stairs", facing="east", half="bottom")
                elif x == hi:
                    b.set(x, y, z, "deepslate_tile_stairs", facing="west", half="bottom")
                elif z == lo:
                    b.set(x, y, z, "deepslate_tile_stairs", facing="south", half="bottom")
                elif z == hi:
                    b.set(x, y, z, "deepslate_tile_stairs", facing="north", half="bottom")
                else:
                    b.set(x, y, z, "deepslate_tiles")
    # the finial: a polished blackstone post and a lantern standing on it
    b.set(4, 17, 4, "polished_blackstone_wall")
    b.set(4, 18, 4, "lantern", hanging=False)
    return b


def test_folly():
    return folly_stage_3()


FOLLY_FILES = [
    os.path.join(ROOT, "src/gametest/resources/data/aliveworkplace_test/wonders/test_folly.json"),
    os.path.join(ROOT, "src/devclient/resources/data/aliveworkplace_test/wonders/test_folly.json"),
]


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


# --- the Lighthouse ------------------------------------------------------------------------------------------------------
# On paper (35.11), 25 x 46 x 33, the front (the land side, where the village is) at z = 0 and the sea from z = 11 back:
# - The tower: round, centred on (12, 21), tapering from 13 across at its foot (y 3) to 9 across under the gallery (y 37):
#   solid masonry round its stair to 8, the wall three thick to 22 and two above; a stone base (y 3-6) with the door on
#   the land side, then bands of white and red terracotta (white 7-11, red 12-15, white 16-20, red 21-24, white 25-29,
#   red 30-33, white 34-37), two-tall windows on the four sides in the lower three white bands. Inside: a spiral stair
#   of spruce round a spruce log newel from the door to the lantern room, and rooms round it floored at 13, 18, 23, 28
#   and 33, lit by lanterns.
# - The gallery at 38: a stone floor 13 across on a ring of stone with stair corbels under it (36-37), iron railings.
# - The lantern room (39-41): an octagon of glass panes 7 across with copper corner posts and a door onto the gallery,
#   sea lanterns up its middle on the newel; over it a ring of 24 redstone lamps (42) with redstone dust on them that an
#   inverted daylight detector (on the north lamp, open to the sky) lights at night; a stepped cut copper cupola (43-44)
#   and a lightning rod for a vane (45).
# - The mole: a round stepped stone platform in the water (y 0 to 2, 19, 17 and 15 across) the tower stands on, mossy
#   at the waterline, with steps up to the door on the land side and from the jetty, mooring posts round its foot,
#   joined to the quay by a causeway; its arm reaches west along the back (a parapet, mooring posts, a harbour light),
#   sheltering the harbour the boathouse opens on.
# - The quay (z 8-10): stone along the shore with mooring posts; the timber jetty (x 22-24) runs from it 22 out to sea on
#   spruce log posts, a deck of top slabs (no foundation under it, as the Fisher's Hut's), a railing on the sea side
#   with lanterns, and at its end a ferry stop: the Travel Post under a little gabled shelter on four posts.
# - The boathouse (x 0-8, z 2-13): spruce boards in a spruce log frame on a stone footing under a slate roof, half on
#   the shore, its open sea end over the water with walkways either side of the channel.
# - The keeper's cottage (x 14-24, z 0-8): a stone ground storey with spruce corner posts, an upper storey of white
#   terracotta in a spruce frame, spruce gables under a slate roof, a chimney; below the fisherman's barrel, a furnace,
#   a table and stools, a ladder up to the keeper's bed; flower beds and lanterns at its front, beds by the path.
# - Palette: stone bricks and andesite at the base (mossy low down), white and red terracotta, spruce, copper. Glass
#   panes only. Stages: I the mole, the quay, the jetty and the boathouse; II the tower to 29; III the tower's top, the
#   corbels, the gallery and its railing; IV the lantern room, the lamps, the cupola and vane, and the keeper's cottage.
LW, LH, LD = 25, 46, 33
LC = (12, 21)  # the tower's centre
L_SHORE = 11  # the first row of sea (the renders and the gallery put water from here back)
L_MOLE = Mix((5, "stone_bricks"), (3, "andesite"), (2, "mossy_stone_bricks"), (1, "cracked_stone_bricks"), seed=31)
L_STONE = Mix((7, "stone_bricks"), (2, "andesite"), (1, "cracked_stone_bricks"), seed=32)
L_QUAY = Mix((6, "stone_bricks"), (3, "andesite"), (1, "mossy_stone_bricks"), seed=33)
L_COTTAGE = Mix((6, "cobblestone"), (3, "stone_bricks"), (2, "andesite"), (1, "mossy_cobblestone"), seed=34)
L_BANDS = ((7, 11, "white"), (12, 15, "red"), (16, 20, "white"), (21, 24, "red"), (25, 29, "white"), (30, 33, "red"),
           (34, 37, "white"))
L_WINDOWS = (9, 16, 26)  # the lower row of each two-tall window
L_FLOORS = (13, 18, 23, 28, 33)
L_TOP = 37  # the tower's last layer under the gallery
L_GALLERY = 38
SPIRAL = [(1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1), (0, -1), (1, -1)]  # clockwise from the east
SPIRAL_FACING = ["south", "west", "west", "north", "north", "east", "east", "south"]  # towards the next step


def disc(r, c=LC):
    """The cells within {@code r} of the centre {@code c}."""
    n = int(r) + 1
    return {(c[0] + dx, c[1] + dz) for dx in range(-n, n + 1) for dz in range(-n, n + 1) if math.hypot(dx, dz) <= r}


def outline(cells, corners=False):
    """The cells of {@code cells} on its edge: with a face neighbour outside (or any neighbour, with corners, so the
    ring is joined face to face all round, for a railing or redstone)."""
    near = [(1, 0), (-1, 0), (0, 1), (0, -1)] + ([(1, 1), (1, -1), (-1, 1), (-1, -1)] if corners else [])
    return {(x, z) for x, z in cells if any((x + dx, z + dz) not in cells for dx, dz in near)}


def tower_radius(y):
    """The tower's outer radius at y: 6.4 (13 across) at its foot, 4.4 (9 across) under the gallery."""
    return 6.4 - 2.0 * (min(max(y, 3), L_TOP) - 3) / (L_TOP - 3)


L_SOLID = 8  # the tower is solid masonry up to here, round its stair
L_THICK = 22  # its wall is three thick up to here, then two


def core():
    """The spiral stair's 3 x 3 well round the newel."""
    return {(LC[0] + dx, LC[1] + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1)}


def tower_wall(y):
    r = tower_radius(y)
    if y <= L_SOLID:
        return disc(r) - core()
    return disc(r) - disc(r - (3 if y <= L_THICK else 2))


def tower_inside(y):
    return disc(tower_radius(y)) - tower_wall(y)


def toward(dx, dz):
    """The facing from a cell towards the tower's middle along its larger offset."""
    if abs(dx) >= abs(dz):
        return "west" if dx > 0 else "east"
    return "north" if dz > 0 else "south"


def ray(y, d):
    """The tower wall's cells at y straight out from the middle in direction d (dx, dz), outermost first."""
    wall = tower_wall(y)
    out = []
    for k in range(1, 9):
        c = (LC[0] + d[0] * k, LC[1] + d[1] * k)
        if c in wall:
            out.append(c)
    return list(reversed(out))


def band(y):
    for lo, hi, colour in L_BANDS:
        if lo <= y <= hi:
            return colour
    return None


# --- stage I: the mole and the quay --------------------------------------------------------------------------------------
def mole(b):
    tiers = [disc(9.4), disc(8.4), disc(7.4)]
    for y, cells in enumerate(tiers):
        rim = outline(cells)
        for x, z in cells:
            if y == 0:
                b.set(x, 0, z, L_MOLE.at(x, 0, z) if (x, z) in rim else L_STONE.at(x, 0, z))
            elif y == 1:
                b.set(x, 1, z, L_MOLE.at(x, 1, z) if (x, z) in rim else L_STONE.at(x, 1, z))
            else:
                b.set(x, 2, z, "polished_andesite" if (x, z) in rim else "stone_bricks")
    # steps up from the causeway to the door (the land side), and from the jetty
    for x in (11, 12, 13):
        for y, cells in ((1, tiers[1]), (2, tiers[2])):
            z = min(z for cx, z in cells if cx == x)
            stairs(b, x, y, z, STONE_BRICK, "south")
    for z in (20, 21, 22):
        for y, cells in ((1, tiers[1]), (2, tiers[2])):
            x = max(x for x, cz in cells if cz == z)
            stairs(b, x, y, z, STONE_BRICK, "west")
    # mooring posts round the mole's foot, on the sea side
    ring = sorted(outline(tiers[0]) - tiers[1])
    for angle in (30, 70, 110, 150, 205):
        a = math.radians(angle)
        x, z = min(ring, key=lambda c: math.hypot(c[0] - LC[0] - 9 * math.cos(a), c[1] - LC[1] - 9 * math.sin(a)))
        log(b, x, 1, z, "spruce_log")
    # the causeway to the quay
    for x in range(10, 15):
        b.set(x, 0, L_SHORE, "polished_andesite" if x in (10, 14) else L_STONE.at(x, 0, L_SHORE))


def quay(b):
    """The quay along the shore (x 9-24, z 8-10), its edge of polished andesite with mooring posts, and the path from
    the front (x 11-13) with a lamp post either side of its foot."""
    for x in range(9, LW):
        for z in range(8, 11):
            b.set(x, 0, z, "polished_andesite" if z == 10 else L_QUAY.at(x, 0, z))
    for x in (9, 16, 19):
        wall_block(b, x, 1, 10, "andesite_wall")
    for z in range(0, 8):
        for x in (11, 12, 13):
            b.set(x, 0, z, "polished_andesite" if x != 12 else L_QUAY.at(x, 0, z))
    for x in (11, 12, 13):
        stairs(b, x, 0, 0, STONE_BRICK, "south")
    for x in (10, 14):
        b.set(x, 0, 1, "polished_andesite")
        lamp_post(b, x, 1, 1, post="spruce_fence", height=2)


def jetty_l(b):
    """The jetty (x 22-24, z 11-32): top-slab deck on spruce log posts, a railing on the sea side, a ferry stop at the
    end (the Travel Post between two lantern posts)."""
    for x in (22, 23, 24):
        for z in range(L_SHORE, LD):
            slab(b, x, 0, z, SPRUCE, top=True)
    for z in (13, 17, 21, 25, 29):
        for x in (22, 24):
            log(b, x, 0, z, "spruce_log")
    for z in range(L_SHORE, LD - 1):
        fence(b, 24, 1, z, "spruce_fence")
        if z >= 28:
            fence(b, 22, 1, z, "spruce_fence")
    for z in (14, 20, 26):
        lantern(b, 24, 2, z)
    # the ferry stop: four posts under a little gabled roof of spruce, the Travel Post under it, a lantern hanging
    for x in (22, 24):
        for z in (LD - 3, LD - 1):
            for y in (0, 1, 2):
                log(b, x, y, z, "spruce_log")
    for z in range(LD - 4, LD):
        stairs(b, 22, 3, z, SPRUCE, "east")
        stairs(b, 24, 3, z, SPRUCE, "west")
        slab(b, 23, 4, z, SPRUCE)
        b.set(23, 3, z, "spruce_planks")
    fence(b, 23, 1, LD - 1, "spruce_fence")
    b.set(23, 1, LD - 2, "aliveworkplace:travel_post", facing="north")
    lantern(b, 23, 2, LD - 3, hanging=True)


def boathouse(b):
    """x 0-8 under its roof, walls x 1 and 7, z 2-13; the land half floored, the sea half (z 11-13) a channel between two
    walkways, open to the sea at the back."""
    frame = (2, 6, 9, 13)
    for x in range(1, 8):
        for z in range(2, L_SHORE):
            b.set(x, 0, z, (L_QUAY.at(x, 0, z) if x in (1, 7) or z == 2 else "spruce_planks"))
        b.set(x, 0, 1, L_QUAY.at(x, 0, 1))
    for z in range(L_SHORE, 14):
        for x in (2, 6):
            slab(b, x, 0, z, SPRUCE, top=True)
        for x in (1, 7):
            if z in frame:
                log(b, x, 0, z, "spruce_log")
            else:
                slab(b, x, 0, z, SPRUCE, top=True)
    for y in range(1, 5):
        for z in range(2, 14):
            for x in (1, 7):
                if z in frame:
                    log(b, x, y, z, "spruce_log")
                else:
                    b.set(x, y, z, "spruce_planks")
        for x in range(2, 7):
            b.set(x, y, 2, "spruce_planks")
            if x in (2, 6):
                b.set(x, y, 13, "spruce_planks")
    for x in range(1, 8):
        log(b, x, 5, 2, "spruce_log", axis="x")
        log(b, x, 5, 13, "spruce_log", axis="x")
    for z in range(3, 13):
        log(b, 1, 5, z, "spruce_log", axis="z")
        log(b, 7, 5, z, "spruce_log", axis="z")
    log(b, 3, 4, 13, "spruce_log", axis="x")
    log(b, 4, 4, 13, "spruce_log", axis="x")
    log(b, 5, 4, 13, "spruce_log", axis="x")
    gable_roof(b, 0, 8, 1, 14, 5, DEEPSLATE_TILE, axis="z", gable="spruce_planks", gable_at=(2, 13), eave_trim=SPRUCE)
    pane(b, 4, 7, 2)
    pane(b, 4, 7, 13)
    # the door on the land end, lanterns on posts either side; windows in the side bays
    door(b, 4, 1, 2, "spruce_door", "north")
    stairs(b, 4, 0, 1, STONE_BRICK, "south")
    for x in (2, 6):
        fence(b, x, 1, 1, "spruce_fence")
        lantern(b, x, 2, 1)
    for x in (1, 7):
        for z in (4, 11):
            pane(b, x, 2, z)
            pane(b, x, 3, z)
    for z in (6, 9):
        for x in range(2, 7):
            log(b, x, 5, z, "spruce_log", axis="x")
    lantern(b, 4, 4, 6, hanging=True)
    lantern(b, 4, 4, 9, hanging=True)
    # a workbench, a stack of timber and planks for mending boats
    b.set(6, 1, 3, "crafting_table")
    for z in (4, 5, 6):
        log(b, 2, 1, z, "spruce_log", axis="z")
    for z in (7, 8):
        slab(b, 6, 1, z, SPRUCE)


def breakwater(b):
    """The mole's arm reaching west along the back (x 0-8, z 28-31), sheltering the harbour the boathouse opens on: a
    parapet on the sea side, mooring posts on the harbour side, a harbour light at its end."""
    for x in range(0, 9):
        for z in range(28, 32):
            if b.get(x, 0, z) is None:
                b.set(x, 0, z, L_MOLE.at(x, 0, z))
        if b.get(x, 1, 31) is None:
            b.set(x, 1, 31, "stone_bricks")
            wall_block(b, x, 2, 31, "stone_brick_wall")
    for x in (2, 6):
        log(b, x, 1, 28, "spruce_log")
    for z in (28, 29, 30):
        b.set(0, 1, z, "stone_bricks")
    for y in (2, 3):
        b.set(0, y, 30, "polished_andesite")
    lantern(b, 0, 4, 30)


def yard(b):
    """Beds of flowers and herbs either side of the path to the quay, edged with stone."""
    flower_bed(b, 9, 2, 10, 7, 0, ["poppy", "fern", None, "oxeye_daisy", "cornflower", "fern"])
    flower_bed(b, 14, 2, 14, 7, 0, ["fern", "azure_bluet", None])


def stage_l1(b):
    mole(b)
    breakwater(b)
    yard(b)
    quay(b)
    jetty_l(b)
    boathouse(b)


# --- stage II: the tower -------------------------------------------------------------------------------------------------
def tower_layer(b, y):
    colour = band(y)
    for x, z in tower_wall(y):
        if colour:
            b.set(x, y, z, f"{colour}_terracotta")
        elif y == 6:
            b.set(x, y, z, "polished_andesite")
        else:
            b.set(x, y, z, L_STONE.at(x, y, z))


def spiral(b, y0, y1):
    """The spiral stair's steps from y0 to y1 and the newel under them."""
    for y in range(y0, y1 + 1):
        n = y - 3
        dx, dz = SPIRAL[n % 8]
        stairs(b, LC[0] + dx, y, LC[1] + dz, SPRUCE, SPIRAL_FACING[n % 8])
        log(b, LC[0], y, LC[1], "spruce_log")


def tower_floor(b, y):
    for x, z in tower_inside(y):
        if abs(x - LC[0]) > 1 or abs(z - LC[1]) > 1:
            b.set(x, y, z, "spruce_planks")


def windows(b, y):
    for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        for yy in (y, y + 1):
            cells = ray(yy, d)
            pane(b, cells[0][0], yy, cells[0][1])
            for c in cells[1:]:
                b.clear(c[0], yy, c[1], c[0], yy, c[1])
        x, z = ray(y - 1, d)[0]
        ox, oz = x + d[0], z + d[1]
        stairs(b, ox, y - 1, oz, STONE_BRICK, toward(d[0], d[1]), top=True)


def tower_door(b):
    cells = ray(3, (0, -1))
    x, z = cells[0]
    door(b, x, 3, z, "spruce_door", "north")
    for c in cells[1:]:
        b.clear(c[0], 3, c[1], c[0], 4, c[1])
    stairs(b, x, 5, z - 1, STONE_BRICK, "south", top=True)
    for xx in (x - 2, x + 2):
        wall_block(b, xx, 3, z - 1, "stone_brick_wall")
        lantern(b, xx, 4, z - 1)


def tower_lights(b, y):
    """Lanterns standing on a floor, in two corners."""
    inside = tower_inside(y)
    for d in ((2, 1), (-2, -1)):
        c = (LC[0] + d[0], LC[1] + d[1])
        if c in inside:
            lantern(b, c[0], y + 1, c[1])


def stage_l2(b):
    for y in range(3, 30):
        tower_layer(b, y)
    tower_door(b)
    for y in L_WINDOWS:
        windows(b, y)
    spiral(b, 3, 29)
    tower_lights(b, L_SOLID)
    for y in L_FLOORS:
        if y < 30:
            tower_floor(b, y)
            tower_lights(b, y)


# --- stage III: the gallery ----------------------------------------------------------------------------------------------
def stage_l3(b):
    for y in range(30, L_TOP + 1):
        tower_layer(b, y)
    spiral(b, 30, L_TOP)
    for y in L_FLOORS:
        if y >= 30:
            tower_floor(b, y)
            tower_lights(b, y)
    # the corbels: every other cell round the tower at 36 an upside-down stair, a ring of stone over them at 37
    ring36 = sorted(disc(5.4) - disc(tower_radius(36)), key=lambda c: math.atan2(c[1] - LC[1], c[0] - LC[0]))
    for i, (x, z) in enumerate(ring36):
        if i % 2 == 0 and (x, z) not in tower_wall(36):
            stairs(b, x, 36, z, STONE_BRICK, toward(x - LC[0], z - LC[1]), top=True)
    for x, z in disc(5.4) - disc(tower_radius(L_TOP)):
        b.set(x, L_TOP, z, "stone_bricks")
    # the gallery's floor, the stairwell up into the lantern room left open, and its railing
    gallery = disc(6.4)
    well = {(LC[0] + 0, LC[1] + 1), (LC[0] + 1, LC[1] + 1)}
    for x, z in gallery:
        if (x, z) in well or (x, z) == LC:
            continue
        b.set(x, L_GALLERY, z, "polished_andesite" if (x, z) in outline(gallery) else "stone_bricks")
    n = L_GALLERY - 3
    dx, dz = SPIRAL[n % 8]
    stairs(b, LC[0] + dx, L_GALLERY, LC[1] + dz, SPRUCE, SPIRAL_FACING[n % 8])
    log(b, LC[0], L_GALLERY, LC[1], "spruce_log")
    for x, z in outline(gallery, corners=True):
        pane(b, x, L_GALLERY + 1, z, "iron_bars")


# --- stage IV: the lantern and the keeper's house -------------------------------------------------------------------------
def octagon():
    return {(LC[0] + dx, LC[1] + dz) for dx in range(-3, 4) for dz in range(-3, 4) if abs(dx) + abs(dz) <= 5}


def lantern_room(b):
    room = octagon()
    ring = outline(room, corners=True)
    posts_ = {(LC[0] + dx, LC[1] + dz) for dx in (-2, 2) for dz in (-2, 2)}
    door_at = (LC[0], LC[1] - 3)
    for y in range(39, 42):
        for x, z in ring:
            if (x, z) in posts_:
                b.set(x, y, z, "cut_copper")
            elif (x, z) != door_at or y == 41:
                pane(b, x, y, z)
        b.set(LC[0], y, LC[1], "sea_lantern")
    door(b, door_at[0], 39, door_at[1], "spruce_door", "north")
    # the lamps over the glass, the ceiling inside them, the dust on the lamps and the detector that drives them
    for x, z in ring:
        b.set(x, 42, z, "redstone_lamp", lit=False)
    for x, z in room - ring:
        b.set(x, 42, z, "cut_copper")
    detector = (LC[0], LC[1] - 3)
    b.set(detector[0], 43, detector[1], "daylight_detector", inverted=True, power=0)
    for x, z in ring:
        if (x, z) == detector:
            continue
        sides = {}
        for face, (dx, dz) in DIRS.items():
            sides[face] = "side" if (x + dx, z + dz) in ring else "none"
        b.set(x, 43, z, "redstone_wire", power=0, **sides)
    # the copper cupola, stepped in twice, and the vane
    inner = room - ring
    for x, z in inner:
        dx, dz = x - LC[0], z - LC[1]
        if max(abs(dx), abs(dz)) == 2:
            stairs(b, x, 43, z, CUT_COPPER, toward(dx, dz))
        else:
            b.set(x, 43, z, "cut_copper")
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if (dx, dz) == (0, 0):
                b.set(LC[0], 44, LC[1], "cut_copper")
            else:
                stairs(b, LC[0] + dx, 44, LC[1] + dz, CUT_COPPER, toward(dx, dz) if abs(dx) != abs(dz) else
                       ("west" if dx > 0 else "east"))
    b.set(LC[0], 45, LC[1], "lightning_rod", facing="up", powered=False, waterlogged=False)


def cottage(b):
    """The keeper's cottage: walls x 15-23, z 1-7. A stone ground storey (y 1-4) with spruce corner posts on a stone plinth,
    a spruce-framed upper storey of white terracotta (y 6-8) on a spruce floor, a slate roof over x 14-24, z 0-8 with
    spruce gables, a chimney up the east gable. Below: the kitchen (the fisherman's barrel, a furnace, a table and
    stools); a ladder up to the bedroom."""
    x0, x1, z0, z1 = 15, 23, 1, 7
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            b.set(x, 0, z, L_COTTAGE.at(x, 0, z) if x in (x0, x1) or z in (z0, z1) else "spruce_planks")
    for y in range(1, 9):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
                    continue
                corner = x in (x0, x1) and z in (z0, z1)
                post = (z in (z0, z1) and x in (17, 19, 21)) or (x in (x0, x1) and z == 4)
                if y == 5:
                    continue
                if corner or (y > 5 and post):
                    log(b, x, y, z, "spruce_log")
                else:
                    b.set(x, y, z, L_COTTAGE.at(x, y, z) if y < 5 else "white_terracotta")
    beam_ring(b, x0, z0, x1, z1, 5, "spruce_log")
    box(b, x0 + 1, 5, z0 + 1, x1 - 1, 5, z1 - 1, "spruce_planks")
    beam_ring(b, x0, z0, x1, z1, 9, "spruce_log")
    # the upper storey's floor beams show as a course of stair brackets under it, front and back
    for x in range(x0, x1 + 1):
        stairs(b, x, 4, z0 - 1, SPRUCE, "south", top=True)
        stairs(b, x, 4, z1 + 1, SPRUCE, "north", top=True)
    gable_roof(b, 14, 24, 0, 8, 9, DEEPSLATE_TILE, axis="x", gable="spruce_planks", gable_at=(x0, x1), eave_trim=SPRUCE)
    # the door, windows in the bays of both storeys, one in each gable
    door(b, 18, 1, z0, "spruce_door", "north")
    stairs(b, 18, 0, 0, STONE_BRICK, "south")
    window(b, 22, 2, z0, "north", height=2)
    window(b, 16, 2, z0, "north", height=2)
    for x in (16, 22):
        window(b, x, 2, z1, "south", height=2, sill=STONE_BRICK)
    for x in (18, 20):
        window(b, x, 6, z0, "north", height=2, sill=SPRUCE)
        window(b, x, 6, z1, "south", height=2, sill=SPRUCE)
    for xx in (x0, x1):
        pane(b, xx, 2, 3)
        pane(b, xx, 3, 3)
        pane(b, xx, 11, 4)
    # the chimney up the east gable's outside
    for y in range(0, 14):
        b.set(24, y, 5, L_COTTAGE.at(24, y, 5))
    b.set(24, 14, 5, "campfire", facing="north", lit=True, signal_fire=False, waterlogged=False)
    # below: the fisherman's barrel, the stove, a table and stools; a ladder up through the floor
    b.set(16, 1, 6, "barrel", facing="up", open=False)
    b.set(22, 1, 6, "furnace", facing="west", lit=False)
    b.set(22, 1, 2, "crafting_table")
    fence(b, 19, 1, 4, "spruce_fence")
    b.set(19, 2, 4, "spruce_pressure_plate", powered=False)
    stairs(b, 18, 1, 4, SPRUCE, "west")
    stairs(b, 20, 1, 4, SPRUCE, "east")
    for y in range(1, 6):
        b.set(16, y, 2, "ladder", facing="east", waterlogged=False)
    lantern(b, 19, 4, 4, hanging=True)
    # above: the keeper's bed, a chest-free shelf of books, a lantern from the ridge beam
    b.bed(21, 6, 3, "red", "north")
    stairs(b, 18, 6, 6, SPRUCE, "north")
    b.set(17, 6, 6, "bookshelf")
    b.set(22, 6, 6, "bookshelf")
    for x in range(x0 + 1, x1):
        log(b, x, 9, 4, "spruce_log", axis="x")
    lantern(b, 19, 8, 4, hanging=True)
    # lanterns on posts either side of the door, a flower bed under each front window
    for x in (17, 19):
        b.set(x, 0, 0, L_COTTAGE.at(x, 0, 0))
        fence(b, x, 1, 0, "spruce_fence")
        lantern(b, x, 2, 0)
    flower_bed(b, 15, 0, 16, 0, 0, ["poppy", "cornflower"])
    flower_bed(b, 21, 0, 23, 0, 0, ["oxeye_daisy", "azure_bluet", "poppy"])


def stage_l4(b):
    lantern_room(b)
    cottage(b)


def lighthouse():
    b = StagedBuild(LW, LH, LD)
    b.begin(1)
    stage_l1(b)
    b.begin(2)
    stage_l2(b)
    b.begin(3)
    stage_l3(b)
    b.begin(4)
    stage_l4(b)
    return b


def lighthouse_at_sea(stage=None):
    """For the renders only: the Lighthouse (whole, or as it stands after a stage) one block up on a strip of land and
    the sea (water from z 11 back, and round the back and sides), so the jetty and the mole stand in the water."""
    stages = lighthouse().stage_builds()
    s = stages[(stage or len(stages)) - 1]
    pad = 6
    out = Build(LW + 2 * pad, LH + 1, LD + pad)
    for (x, y, z), block in s.blocks.items():
        out.blocks[(x + pad, y + 1, z)] = block
    for x in range(out.w):
        for z in range(out.d):
            if z >= L_SHORE and (x, 1, z) not in out.blocks:
                out.set(x, 0, z, "water", level=0)
    return out


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
    "lighthouse": {
        "draw": lighthouse,
        "icon": "minecraft:sea_lantern",
        "crew": 12,
        "requirements": [
            {"type": "rank_at_least", "rank": "town"},
            {"type": "legend", "id": "aliveworkplace:merchant_prince",
             "stand_in": [{"type": "caravan_routes", "count": 3}]},
            {"type": "coastal"},
        ],
        "goods": [
            goods(("item", "minecraft:cooked_cod", 64, "minecraft:fisherman"),
                  ("item", "minecraft:oak_boat", 8, "aliveworkplace:carpenter"),
                  ("item", "minecraft:coal", 64, "aliveworkplace:miner")),
            goods(("item", "minecraft:chain", 16, "aliveworkplace:tinkerer"),
                  ("item", "minecraft:iron_ingot", 32, "minecraft:armorer"),
                  ("item", "minecraft:white_wool", 32, "minecraft:shepherd")),
            goods(("item", "minecraft:filled_map", 4, "minecraft:cartographer"),
                  ("item", "minecraft:spectral_arrow", 32, "minecraft:fletcher"),
                  ("item", "minecraft:leather", 32, "minecraft:butcher")),
            goods(("item", "minecraft:glowstone_dust", 64, "aliveworkplace:netherworker"),
                  ("item", "minecraft:redstone", 32, "aliveworkplace:miner"),
                  ("item", "minecraft:pumpkin_pie", 16, "aliveworkplace:chef")),
        ],
        "powers": [
            {"type": "caravan_range", "factor": 2},
            {"type": "caravan_speed", "factor": 0.5},
            {"type": "ferry_fares", "factor": 0.5},
            {"type": "early_warning"},
        ],
    },
}


def save_wonder(wonder_id, structures=MAIN_STRUCTURES, data=WONDER_DATA):
    """Saves a Wonder's stage parts (kit.wonder_stages) and writes its data file (docs/design/M35.md's format) with those
    parts. Each stage is checked as it will stand, on the stages before it (check.py's lines, as for any build)."""
    from check import check
    w = WONDERS[wonder_id]
    b = w["draw"]()
    stages = b.stage_builds()
    before = set()
    for n, s in enumerate(stages, 1):
        mine = {p for p, block in s.blocks.items() if block[0] != "minecraft:air"} - before
        for problem in check(s, only=mine):
            print(f"  CHECK wonder/{wonder_id} stage {n}: {problem}")
        print(f"wonder/{wonder_id} stage {n}: {len(mine)} blocks")
        before |= mine
    parts = wonder_stages("aliveworkplace", wonder_id, stages, [structures])
    assert len(parts) == len(w["goods"]), f"{wonder_id}: {len(parts)} stages drawn, {len(w['goods'])} with goods"
    key = f"wonder.aliveworkplace.{wonder_id}"
    out = {
        "name": key + ".name",
        "description": key + ".description",
        "icon": w["icon"],
        "enabled": True,
        "crew": w["crew"],
        "requirements": w["requirements"],
        "stages": [{"name": f"{key}.stage.{n}", "parts": stage_parts, "goods": w["goods"][n - 1]}
                   for n, stage_parts in enumerate(parts, 1)],
        "powers": w["powers"],
    }
    text = json.dumps(out, indent=2)
    text = re.sub(r"\[\s+(-?\d+),\s+(-?\d+),\s+(-?\d+)\s+\]", r"[\1, \2, \3]", text)  # offsets on one line
    os.makedirs(data, exist_ok=True)
    with open(os.path.join(data, wonder_id + ".json"), "w") as f:
        f.write(text + "\n")


def save_test_folly():
    """The test folly (35.3): its parts in the gametest and screenshot-harness resources, the whole as drawn beside them,
    and its stages' parts written into its file (the rest of the file is kept as written)."""
    parts = wonder_stages("aliveworkplace_test", "test_folly", [folly_stage_1(), folly_stage_2(), folly_stage_3()],
                          [TEST_STRUCTURES, DEVCLIENT_STRUCTURES])
    whole = folly_stage_3()
    whole.fill_air()
    for folder in (TEST_STRUCTURES, DEVCLIENT_STRUCTURES):
        whole.save(os.path.join(folder, "wonder_drawn"), "test_folly")
    with open(FOLLY_FILES[0]) as f:
        folly = json.load(f)
    for stage, stage_parts in zip(folly["stages"], parts):
        stage["parts"] = stage_parts
    text = json.dumps(folly, indent=2) + "\n"
    for path in FOLLY_FILES:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w") as f:
            f.write(text)


if __name__ == "__main__":
    save_test_folly()
    for wonder_id in WONDERS:
        save_wonder(wonder_id)
