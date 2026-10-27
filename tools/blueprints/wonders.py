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
WonderBlueprintGameTests.WONDERS (with its block count) and to the wonder_gallery scene's list. A Wonder that needs a
mod (the Stadium, Cobblemon) gets "load_conditions" in its entry and its checks in a compat test instead.

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
import zlib

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
    # the windows go where the glass meets the curved wall's stone on two sides (B648: at 18 and 162 degrees they
    # stood where the wall turned, joined on one side only and ending in the air on the other)
    ws = set(wall)
    framed = [c for c in wall if sum((c[0] + dx, c[1] + dz) in ws for dx, dz in DIRS.values()) >= 2]
    for angle in (18, 54, 90, 126, 162):
        x, z = min(framed, key=lambda c: abs(math.degrees(math.atan2(c[1] - APSE[1], c[0] - APSE[0])) - angle))
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


def l_band(y):
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
    colour = l_band(y)
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


# --- the Great Library ---------------------------------------------------------------------------------------------------
# On paper (35.13), 41 x 34 x 41, the front (the portico and its grand stair) at z = 0:
# - A cross plan round a rotunda. The rotunda, centred on (20, 22), is 19 across (a drum two thick, r 7.4-9.4) rising to
#   24 under a shallow copper dome (25-30) with ribs, an oculus and a glass lantern (31-32) under a copper cap (33).
# - Four wings of reading halls, 13 wide (walls 6 either side of their middle line) and 17 with their pilasters, run out
#   from the drum: the front one (the vestibule, door at z 8), the back one to z 38, the west one to x 2, the east one
#   to x 38. Each has two storeys: the ground storey (3-7) of bookshelf stacks between the pilasters, a band at 8, the
#   upper storey (9-13) and a cornice at 14 under a deepslate-tile roof (ridge 22) with a gable window at its end.
#   Bays every 4 (pilasters of smooth stone a block proud, a tall window in each bay, stone sills and lintels).
# - Inside a wing: stacks out from the walls every 4 under a gallery of dark oak on both sides at 8 (two floors of
#   bookshelf galleries), carrels under the windows, reading tables with lanterns down the double-height middle, a
#   ladder up to each gallery, a dark oak ceiling with lanterns. A lectern in each wing (a scholar's); in the west wing
#   the map room, a carpet map round a cartography table.
# - The rotunda: bookshelves round the drum on both storeys, a gallery ring at 8 joined to the wings' galleries; at the
#   centre an Enchanting Table ringed by fifteen bookshelves (the sixteenth place left open towards the door) and the
#   librarian's lectern; clerestory windows in the drum above the roofs.
# - The portico: six quartz columns (a wider middle bay before the door) under a quartz entablature and a pediment,
#   over a grand stair of three steps, on a podium of smooth stone (three high, darker at its foot) under the whole.
# - Palette (60/30/10): polished diorite walls, smooth stone pilasters, bands and podium; dark oak floors, shelves'
#   galleries and furniture; deepslate-tile roofs; quartz on the portico, a copper dome that weathers green. Panes only.
# - Stages, each ending tidy: I the Stacks (the podium and floors, the ground storeys and their stacks, the map room);
#   II the Galleries (the upper storeys, galleries, ceilings and roofs, the drum to 14 and its gallery); III the
#   Rotunda (the drum's clerestory and cornice, the enchanting table and its ring, the librarian's lectern); IV the
#   Dome and the Portico.
GW, GH, GD = 41, 34, 41
GC = (20, 22)  # the rotunda's centre
G_DRUM = 9.4  # the drum's outer radius (19 across)
G_HALL = 7.4  # the rotunda's inside
G_GALLERY = 3.9  # the rotunda gallery's inner edge
G_WALL = "polished_diorite"
G_TRIM = "smooth_stone"
G_BASE = "polished_andesite"
G_FLOOR = "dark_oak_planks"
G_SHELF = "bookshelf"
G_QUARTZ = "smooth_quartz"


def g_r(x, z):
    return math.hypot(x - GC[0], z - GC[1])


def in_drum(x, z):
    return g_r(x, z) <= G_DRUM


class Wing:
    """One wing of the cross: k counts in from its end wall (k = 0), v across it (-6 and 6 its side walls)."""

    def __init__(self, name, axis, outer, sign):
        self.name, self.axis, self.outer, self.sign = name, axis, outer, sign
        centre = GC[1] if axis == "z" else GC[0]
        self.length = abs(centre - outer)  # k from the end wall to the rotunda's centre

    def at(self, k, v):
        u = self.outer - self.sign * k
        return (GC[0] + v, u) if self.axis == "z" else (u, GC[1] + v)

    def set(self, b, k, y, v, name, **props):
        x, z = self.at(k, v)
        b.set(x, y, z, resolve(name, x, y, z), **props)

    def has(self, k, v):
        """Whether the wing has a cell here (the drum owns its own)."""
        x, z = self.at(k, v)
        return 0 <= x < GW and 0 <= z < GD and not in_drum(x, z)

    def ks(self):
        return range(-2, self.length + 1)

    @property
    def out(self):
        """The facing outward along the wing (towards its end)."""
        if self.axis == "z":
            return "south" if self.sign > 0 else "north"
        return "east" if self.sign > 0 else "west"

    def side(self, v):
        """The facing towards the side wall at v's sign."""
        if self.axis == "z":
            return "east" if v > 0 else "west"
        return "south" if v > 0 else "north"


G_WINGS = (Wing("front", "z", 8, -1), Wing("back", "z", 38, 1), Wing("west", "x", 2, -1), Wing("east", "x", 38, 1))
G_FRONT, G_BACK, G_WEST, G_EAST = G_WINGS


def wing_inside(w, k, v):
    return w.has(k, v) and 1 <= k and abs(v) <= 5


def g_building_cells():
    """The cells the building stands on: the wings with their pilasters and a block of ledge, the drum's, the portico."""
    cells = set()
    for w in G_WINGS:
        for k in w.ks():
            for v in range(-9, 10):
                x, z = w.at(k, v)
                if 0 <= x < GW and 0 <= z < GD and (w is not G_FRONT or k >= 0):
                    cells.add((x, z))
    cells |= disc(10.4, GC)
    cells |= {(x, z) for x in range(10, 31) for z in range(3, 9)}  # the portico's platform
    return cells


def g_terrace():
    """The podium: a square terrace (z 3 back to the end), the grand stair before it."""
    return {(x, z) for x in range(GW) for z in range(3, GD)}


def g_gardens():
    """The beds in the terrace's four corners: two clear of the building and one in from the balustrade."""
    building = g_building_cells()
    near = {(x + dx, z + dz) for x, z in building for dx in (-1, 0, 1) for dz in (-1, 0, 1)}
    return {(x, z) for x, z in g_terrace() if (x, z) not in near and 1 <= x < GW - 1 and 4 <= z < GD - 1}


# --- stage I: the stacks -------------------------------------------------------------------------------------------------
def g_podium(b):
    cells = g_terrace()
    rim = outline(cells, corners=True)
    beds = g_gardens()
    for x, z in cells:
        edge = (x, z) in rim
        b.set(x, 0, z, G_BASE if edge else "stone_bricks")
        b.set(x, 1, z, G_TRIM if edge else "stone_bricks")
        if (x, z) in beds:
            b.set(x, 2, z, "grass_block", snowy=False)
        else:
            b.set(x, 2, z, G_TRIM)
    # the floors: dark oak in the wings, the rotunda's rings of diorite and andesite round the table
    for w in G_WINGS:
        for k in w.ks():
            for v in range(-5, 6):
                if wing_inside(w, k, v):
                    w.set(b, k, 2, v, G_FLOOR)
    for x, z in disc(G_HALL, GC):
        r = g_r(x, z)
        b.set(x, 2, z, "polished_diorite" if r <= 3.4 else G_BASE if r <= 4.4 else G_FLOOR)
    for x in range(11, 30):
        for z in range(3, 8):
            b.set(x, 2, z, "polished_diorite" if 12 <= x <= 28 and 4 <= z <= 7 else G_QUARTZ)


PANE = dict(north=False, south=False, east=False, west=False, waterlogged=False)


def wing_storey(b, w, y0, y1):
    """A wing's walls from y0 to y1: two leaves along its sides (diorite outside at 7, smooth stone inside at 6) with a
    deep window in every bay (y0+1 to y1-1), pilasters a block proud at 8 with sills and lintels between them; the end
    wall with a three-wide window in the middle (the vestibule's door below), pilasters before it."""
    for k in w.ks():
        if k < 0:
            continue
        for s in (-1, 1):
            if not w.has(k, 7 * s):
                continue
            for y in range(y0, y1 + 1):
                window = k % 4 == 2 and y0 < y < y1
                if window:
                    w.set(b, k, y, 7 * s, "glass_pane", **PANE)
                else:
                    w.set(b, k, y, 7 * s, G_WALL)
                if w.has(k, 6 * s) and not window and k > 0:
                    w.set(b, k, y, 6 * s, G_TRIM)
            if not w.has(k, 8 * s):
                continue
            inward = OPP[w.side(s)]
            if k % 4 == 2:
                stairs(b, *_kyv(w, k, y0, 8 * s), DIORITE, inward, top=True)
                stairs(b, *_kyv(w, k, y1, 8 * s), DIORITE, inward, top=True)
            if k % 4 == 0:
                for y in range(y0, y1 + 1):
                    w.set(b, k, y, 8 * s, G_TRIM)
    for v in range(-6, 7):
        for y in range(y0, y1 + 1):
            front_door = w is G_FRONT and y0 == 3
            if abs(v) <= 1 and y0 < y < y1 and not front_door:
                w.set(b, 0, y, v, "glass_pane", **PANE)
            elif front_door and abs(v) <= 1 and y <= 5:
                continue
            else:
                w.set(b, 0, y, v, G_QUARTZ if front_door and abs(v) <= 1 else G_WALL)
    for v in range(-5, 6):  # the end wall's inner leaf
        for y in range(y0, y1 + 1):
            if abs(v) <= 1 and (y0 < y < y1 or (w is G_FRONT and y0 == 3 and y <= 5)):
                continue
            if wing_inside(w, 1, v):
                w.set(b, 1, y, v, G_TRIM)
    for v in (-7, -2, 2, 7):
        for y in range(y0, y1 + 1):
            if w is G_FRONT and y > 11:
                continue  # under the portico's entablature
            w.set(b, -1, y, v, G_TRIM)
    if w is not G_FRONT:
        for v in (-1, 0, 1):
            stairs(b, *_kyv(w, -1, y0, v), DIORITE, OPP[w.out], top=True)


def _kyv(w, k, y, v):
    x, z = w.at(k, v)
    return x, y, z


def wing_band(b, w, y):
    """The course of smooth stone round a wing's walls and pilasters at y."""
    for k in w.ks():
        for v in range(-8, 9):
            if not w.has(k, v):
                continue
            side = abs(v) == 7 and k >= 0 or abs(v) == 6 and k > 0
            pil = abs(v) == 8 and k >= 0 and k % 4 == 0
            end = k == 0 and abs(v) <= 7
            endpil = k == -1 and abs(v) in (2, 7) and not (w is G_FRONT and y > 11)
            if side or pil or end or endpil:
                w.set(b, k, y, v, G_TRIM)


def stacks(b, w, y0, y1, depth):
    """Bookshelf stacks out from the side walls every 4 (k = 4, 8, ...), {@code depth} deep."""
    for k in range(4, w.length + 1, 4):
        for s in (-1, 1):
            for d in range(depth):
                v = s * (5 - d)
                if wing_inside(w, k, v):
                    for y in range(y0, y1 + 1):
                        w.set(b, k, y, v, G_SHELF)


def reading_room(b, w):
    """Carrels under the windows, reading tables with lanterns down the middle, the wing's lectern."""
    for k in range(2, w.length + 1, 4):
        for s in (-1, 1):
            if wing_inside(w, k, 5 * s) and wing_inside(w, k, 4 * s) and w.has(k, 6 * s):
                slab(b, *_kyv(w, k, 3, 5 * s), DARK_OAK, top=True)
                stairs(b, *_kyv(w, k, 3, 4 * s), DARK_OAK, w.side(-s))
        if k >= 6 and all(wing_inside(w, k, v) for v in range(-2, 3)) and all(
                wing_inside(w, kk, 0) for kk in (k - 1, k + 1)):
            for v in (-1, 0, 1):
                slab(b, *_kyv(w, k, 3, v), DARK_OAK, top=True)
            for s in (-1, 1):
                stairs(b, *_kyv(w, k, 3, 2 * s), DARK_OAK, w.side(s))
            lantern(b, *_kyv(w, k, 4, 0))
    for k in range(4, w.length + 1, 4):  # low double-sided bookcases down the middle between the tables
        if all(wing_inside(w, kk, v) for kk in (k - 1, k, k + 1) for v in (-2, 0, 2)):
            for v in (-1, 0, 1):
                for y in (3, 4):
                    w.set(b, k, y, v, G_SHELF)
                slab(b, *_kyv(w, k, 5, v), DARK_OAK)
    if w is G_FRONT:
        lectern(b, *_kyv(w, 2, 3, 3), w.side(-1))
        lantern(b, *_kyv(w, 2, 3, -3))
    elif w is G_WEST:
        map_room(b, w)
    else:
        lectern(b, *_kyv(w, 2, 3, 0), OPP[w.out])


def lectern(b, x, y, z, facing):
    b.set(x, y, z, "lectern", facing=facing, has_book=False, powered=False)


G_MAP = {".": "blue_carpet", "g": "lime_carpet", "s": "yellow_carpet", "f": "green_carpet"}
G_MAP_ROWS = [".gfg..", "gffsg."]


def map_room(b, w):
    """The west wing's end bay: a carpet map of seas and islands round the cartography table, the scholar's lectern."""
    for i, row in enumerate(G_MAP_ROWS):
        for j, c in enumerate(row):
            k, v = 2 + i, j - 3
            if i >= 2:
                continue
            if wing_inside(w, k, v) and abs(v) <= 3:
                b.set(*_kyv(w, k, 3, v), G_MAP[c])
    b.set(*_kyv(w, 2, 3, 0), "cartography_table")
    # The lectern takes the middle of the reading table at k 6: its lantern moves to the table's slab beside it, since
    # a lectern's slanted top can't hold one (B538)
    lectern(b, *_kyv(w, 6, 3, 0), OPP[w.out])
    b.set(*_kyv(w, 6, 4, 0), "air")
    lantern(b, *_kyv(w, 6, 4, 1))
    for s in (-1, 1):
        stairs(b, *_kyv(w, 6, 3, 2 * s), DARK_OAK, w.side(s))


def drum_cut(x, z, half):
    """Whether (x, z) lies on one of the four axes through the drum, within {@code half} of it."""
    return abs(x - GC[0]) <= half or abs(z - GC[1]) <= half


def drum_ring(r_in=G_HALL, r_out=G_DRUM):
    return {(x, z) for x, z in disc(r_out, GC) if g_r(x, z) > r_in}


G_DIAGONALS = (45, 135, 225, 315)


def rays(cells, angles, perp):
    """The cells of {@code cells} within {@code perp} of a ray out from the rotunda's centre at any of {@code angles}."""
    out = set()
    for a in angles:
        c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
        for x, z in cells:
            dx, dz = x - GC[0], z - GC[1]
            if abs(dx * s - dz * c) <= perp and dx * c + dz * s > 0:
                out.add((x, z))
    return out


def drum_storey(b, y0, y1, arch_half, arch_top, window_rows):
    """The drum from y0 to y1: open on the four axes (arch_half either side, up to arch_top), a window on each
    diagonal in {@code window_rows} (panes in its outer cells, the inner ones open)."""
    ring = drum_ring()
    windows = rays(ring, G_DIAGONALS, 0.75)
    for y in range(y0, y1 + 1):
        for x, z in ring:
            if y <= arch_top and drum_cut(x, z, arch_half if y < arch_top else arch_half - 1):
                continue
            if y in window_rows and (x, z) in windows:
                if g_r(x, z) > G_DRUM - 0.8:
                    pane(b, x, y, z)
                continue
            b.set(x, y, z, G_TRIM if y == y0 == 3 else G_WALL)


def rotunda_shelves(b, y0, y1, cut_half, cut_top):
    ring = drum_ring(G_HALL - 1, G_HALL)
    windows = rays(ring, G_DIAGONALS, 0.75)
    for x, z in ring - windows:
        for y in range(y0, y1 + 1):
            if y <= cut_top and drum_cut(x, z, cut_half):
                continue
            b.set(x, y, z, G_SHELF)


def stage_g1(b):
    g_podium(b)
    for w in G_WINGS:
        wing_storey(b, w, 3, 7)
        wing_band(b, w, 8)
        stacks(b, w, 3, 7, 3)
        reading_room(b, w)
    drum_storey(b, 3, 8, 2, 7, (4, 5, 6))
    rotunda_shelves(b, 3, 7, 2, 7)
    # the vestibule's door (triple, dark oak) under its quartz lintel, lanterns either side
    for v, hinge in ((-1, "left"), (0, "left"), (1, "right")):
        door(b, *_kyv(G_FRONT, 0, 3, v), "dark_oak_door", "north", hinge=hinge)
    for v in (-3, 3):
        lantern(b, *_kyv(G_FRONT, -1, 3, v))


# --- stage II: the galleries ---------------------------------------------------------------------------------------------
def galleries(b, w):
    """The wing's galleries at 8 over its stacks (and across its inner end, to the rotunda's gallery), the railing, the
    ladders up, lanterns under them, the upper stacks and the ceiling with its lanterns."""
    walk = set()
    for k in w.ks():
        for v in range(-5, 6):
            if not wing_inside(w, k, v):
                continue
            x, z = w.at(k, v)
            if abs(v) >= 3 or g_r(x, z) <= G_DRUM + 2.1:
                walk.add((k, v))
    for k, v in walk:
        w.set(b, k, 8, v, G_FLOOR)
    for k, v in walk:
        open_side = any((k + dk, v + dv) not in walk and wing_inside(w, k + dk, v + dv)
                        for dk, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if open_side and abs(v) <= 3:
            fence(b, *_kyv(w, k, 9, v), "dark_oak_fence")
    for s in (-1, 1):
        for y in range(3, 10):
            w.set(b, 3, y, 4 * s, "ladder", facing=w.out, waterlogged=False)
        for k in range(2, w.length + 1, 4):
            if wing_inside(w, k, 3 * s) and (k, 3 * s) in walk:
                lantern(b, *_kyv(w, k, 7, 3 * s), hanging=True)
    # the upper stacks: along the walls between the windows, and out from them at the pilasters
    for k in w.ks():
        for s in (-1, 1):
            if wing_inside(w, k, 5 * s) and k % 4 != 2 and k > 1:
                for y in range(9, 14):
                    w.set(b, k, y, 5 * s, G_SHELF)
            if wing_inside(w, k, 4 * s) and k % 4 == 0 and k > 0:
                for y in range(9, 14):
                    w.set(b, k, y, 4 * s, G_SHELF)
    for k in w.ks():
        for v in range(-5, 6):
            if wing_inside(w, k, v):
                w.set(b, k, 14, v, G_FLOOR)
        if k % 4 == 2 and wing_inside(w, k, 0):
            lantern(b, *_kyv(w, k, 13, 0), hanging=True)
        if k % 4 == 0 and k > 0:  # a dark oak beam across the ceiling at each pilaster
            for v in range(-3, 4):
                if wing_inside(w, k, v) and b.get(*_kyv(w, k, 13, v)) is None:
                    log(b, *_kyv(w, k, 13, v), "dark_oak_log", axis="z" if w.axis == "x" else "x")
    for k in w.ks():  # and two along it over the galleries' railings: a coffered ceiling
        for v in (-3, 3):
            if k > 1 and wing_inside(w, k, v) and b.get(*_kyv(w, k, 13, v)) is None:
                log(b, *_kyv(w, k, 13, v), "dark_oak_log", axis=w.axis)


def wing_roofs(b):
    """The four wings' roofs, drawn together (where two meet, the higher wins), clipped round the drum, each with a
    diorite gable over its end wall and a window in it."""
    draws = []
    for w in G_WINGS:
        x0, z0 = w.at(0 if w is G_FRONT else -1, -8)
        x1, z1 = w.at(w.length, 8)
        gx = w.at(0, 0)

        def draw(t, w=w, x0=x0, x1=x1, z0=z0, z1=z1, gx=gx):
            gable_at = (gx[1],) if w.axis == "z" else (gx[0],)
            gable_roof(t, min(x0, x1), max(x0, x1), min(z0, z1), max(z0, z1), 15, DEEPSLATE_TILE, axis=w.axis,
                       gable=G_WALL, gable_at=gable_at, eave_trim=DIORITE)
        draws.append(draw)
    t = Build(GW, GH, GD)
    roofs(t, *draws)
    for (x, y, z), block in t.blocks.items():
        if not in_drum(x, z) and b.get(x, y, z) is None:
            b.blocks[(x, y, z)] = block
    for w in G_WINGS:
        for v in range(-6, 7):  # a string course across each gable, as a pediment's
            if b.get(*_kyv(w, 0, 15, v)) == ("minecraft:" + G_WALL, ()):
                w.set(b, 0, 15, v, G_TRIM)
        for y, vs in ((17, (0,)), (18, (-1, 0, 1)), (19, (0,))):
            for v in vs:
                pane(b, *_kyv(w, 0, y, v))


def rotunda_gallery(b):
    ring = {(x, z) for x, z in disc(G_HALL, GC) if g_r(x, z) > G_GALLERY}
    for x, z in ring:
        b.set(x, 8, z, G_FLOOR)
    for x, z in ring:
        if any((x + dx, z + dz) not in ring and g_r(x + dx, z + dz) <= G_GALLERY for dx, dz in DIRS.values()):
            fence(b, x, 9, z, "dark_oak_fence")
    for a in range(8):
        ang = math.radians(22.5 + 45 * a)
        x, z = round(GC[0] + 4.6 * math.cos(ang)), round(GC[1] + 4.6 * math.sin(ang))
        if (x, z) in ring:
            lantern(b, x, 7, z, hanging=True)


def stage_g2(b):
    for w in G_WINGS:
        wing_storey(b, w, 9, 13)
        wing_band(b, w, 14)
        galleries(b, w)
    drum_storey(b, 9, 14, 1, 11, (10, 11, 12))
    rotunda_shelves(b, 9, 13, 1, 11)
    rotunda_gallery(b)
    wing_roofs(b)


# --- stage III: the rotunda ----------------------------------------------------------------------------------------------
def stage_g3(b):
    ring = drum_ring()
    outer = {(x, z) for x, z in ring if g_r(x, z) > G_DRUM - 0.8}
    windows = rays(ring, [22.5 + 45 * a for a in range(8)], 0.6)
    pilasters = rays(outer, [45 * a for a in range(8)], 0.5)
    for y in range(15, 25):
        for x, z in ring:
            if b.get(x, y, z) is not None:
                continue
            if (x, z) in windows and 17 <= y <= 22:
                if (x, z) in outer:
                    pane(b, x, y, z)
                continue
            b.set(x, y, z, G_TRIM if (x, z) in pilasters or y in (16, 24) else G_WALL)
    # the cornice round the drum's top, under the dome
    for x, z in drum_ring(G_DRUM, G_DRUM + 1):
        if b.get(x, 24, z) is None:
            stairs(b, x, 24, z, DIORITE, toward(x - GC[0], z - GC[1]), top=True)
    # the enchanting table at the centre, fifteen bookshelves two out round it (open towards the door), the librarian's
    # lectern before the gap
    b.set(GC[0], 3, GC[1], "enchanting_table")
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if max(abs(dx), abs(dz)) == 2 and (dx, dz) != (0, -2):
                b.set(GC[0] + dx, 3, GC[1] + dz, G_SHELF)
    lectern(b, GC[0], 3, GC[1] - 4, "north")
    for a in (45, 135, 225, 315):  # reading desks round the ring, between the arches, a chair at each
        c, s_ = math.cos(math.radians(a)), math.sin(math.radians(a))
        x, z = round(GC[0] + 5 * c), round(GC[1] + 5 * s_)
        slab(b, x, 3, z, DARK_OAK, top=True)
        lantern(b, x, 4, z)
        cx, cz = round(GC[0] + 4 * c), round(GC[1] + 4 * s_)
        stairs(b, cx, 3, cz, DARK_OAK, toward(x - GC[0], z - GC[1]))
    upper_gallery(b)
    for dx in (-2, 2):
        lantern(b, GC[0] + dx, 3, GC[1] - 4)


def upper_gallery(b):
    """The rotunda's second gallery at 14, under the clerestory: a ring of dark oak with a railing, bookshelves round
    the drum between the windows (15-19), lanterns hanging under it, a ladder up from the first gallery."""
    ring = {(x, z) for x, z in disc(G_HALL, GC) if g_r(x, z) > 4.9}
    for x, z in ring:
        b.set(x, 14, z, G_FLOOR)
    for x, z in ring:
        if any((x + dx, z + dz) not in ring and g_r(x + dx, z + dz) <= 4.9 for dx, dz in DIRS.values()):
            fence(b, x, 15, z, "dark_oak_fence")
    shelves = drum_ring(G_HALL - 1, G_HALL)
    for x, z in shelves - rays(shelves, [22.5 + 45 * a for a in range(8)], 0.9):
        for y in range(15, 20):
            b.set(x, y, z, G_SHELF)
    for a in range(8):
        ang = math.radians(22.5 + 45 * a)
        x, z = round(GC[0] + 5.6 * math.cos(ang)), round(GC[1] + 5.6 * math.sin(ang))
        if (x, z) in ring and b.get(x, 13, z) is None:
            lantern(b, x, 13, z, hanging=True)
    for y in range(9, 15):
        b.set(GC[0] - 3, y, GC[1] + 5, "ladder", facing="north", waterlogged=False)


# --- stage IV: the dome and the portico ----------------------------------------------------------------------------------
def dome(b):
    """A shallow dome of cut copper (25-30) on the drum, eight ribs of copper, coffered inside in smooth stone and
    diorite, an oculus under the glass lantern."""
    ribs = set()
    for a in range(8):
        ang = math.radians(45 * a)
        for r10 in range(0, 100):
            r = r10 / 10
            ribs.add((round(GC[0] + r * math.cos(ang)), round(GC[1] + r * math.sin(ang))))
    for y in range(25, 31):
        for x, z in disc(G_DRUM, GC):
            r = g_r(x, z)
            outside = (r / G_DRUM) ** 2 + ((y - 24.5) / 6.5) ** 2 <= 1
            inside = (r / (G_DRUM - 1.5)) ** 2 + ((y - 24.5) / 5) ** 2 <= 1
            lining = (r / (G_DRUM - 2.5)) ** 2 + ((y - 24.5) / 4) ** 2 <= 1
            if outside and not inside and not (y == 30 and r <= 1.4):
                b.set(x, y, z, "copper_block" if (x, z) in ribs else "cut_copper")
            elif inside and not lining and r > 1.4:
                # the coffers inside: a frame of smooth stone on the ribs and every other course, diorite between
                b.set(x, y, z, G_TRIM if (x, z) in ribs or y % 2 == 0 else "polished_diorite")
    # the lantern: glass round the oculus, a copper cap, a lantern hanging inside
    room = disc(2.4, GC)
    for y in (31, 32):
        for x, z in outline(room):
            pane(b, x, y, z)
    for x, z in room:
        dx, dz = x - GC[0], z - GC[1]
        if (x, z) in outline(room):
            stairs(b, x, 33, z, CUT_COPPER, toward(dx, dz))
        else:
            b.set(x, 33, z, "cut_copper")
    for dx, dz in ((2, 1), (2, -1), (-2, 1), (-2, -1), (1, 2), (-1, 2), (1, -2), (-1, -2)):
        for y in (31, 32):  # copper posts either side of each face's window
            b.set(GC[0] + dx, y, GC[1] + dz, "cut_copper")
    lantern(b, GC[0], 32, GC[1], hanging=True)


G_COLUMNS = (12, 15, 18, 22, 25, 28)


def portico(b):
    """Six quartz columns on the platform's front edge (z 3), a solid entablature over the platform (12-13), a pediment
    of quartz under a deepslate-tile roof, the grand stair of three steps before it with lanterns on its cheeks."""
    for x in G_COLUMNS:
        b.set(x, 3, 3, "chiseled_quartz_block")
        for y in range(4, 11):
            b.set(x, y, 3, "quartz_pillar", axis="y")
        b.set(x, 11, 3, "chiseled_quartz_block")
    for y in (12, 13):
        for x in range(11, 30):
            for z in range(2, 8):
                b.set(x, y, z, "quartz_block" if z == 2 else G_QUARTZ)
    for x in range(11, 30):
        stairs(b, x, 11, 2, QUARTZ, "south", top=True)
    for z in range(2, 8):
        for x, f in ((10, "east"), (30, "west")):
            stairs(b, x, 13, z, QUARTZ, f, top=True)
    # the portico's side walls (antae) of diorite, from the front wall to the last column but one, a window in each
    for x in (11, 29):
        for z in range(5, 8):
            for y in range(3, 12):
                if z == 6 and 5 <= y <= 8:
                    pane(b, x, y, z)
                else:
                    b.set(x, y, z, G_TRIM if z == 5 or y in (3, 11) else G_WALL)
    # the pediment: a low roof of deepslate-tile slabs (half a block up for each block in, as a temple's), its two
    # gables of smooth quartz with a round window in the front one
    for x in range(10, 31):
        i = min(x - 10, 30 - x)
        y = 14 + i // 2
        for z in range(1, 8):
            slab(b, x, y, z, DEEPSLATE_TILE, top=i % 2 == 1)
        for z in (2, 7):
            for yy in range(14, y):
                b.set(x, yy, z, G_QUARTZ)
    for y, xs in ((15, (20,)), (16, (19, 20, 21)), (17, (20,))):
        for x in xs:
            pane(b, x, y, 2)
    # the grand stair
    for x in range(12, 29):
        stairs(b, x, 0, 0, QUARTZ, "south")
        b.set(x, 0, 1, G_TRIM)
        stairs(b, x, 1, 1, QUARTZ, "south")
        b.set(x, 0, 2, G_TRIM)
        b.set(x, 1, 2, G_TRIM)
        stairs(b, x, 2, 2, QUARTZ, "south")
    for x in (11, 29):
        for z in range(0, 3):
            b.set(x, 0, z, G_BASE)
            b.set(x, 1, z, G_TRIM)
            b.set(x, 2, z, G_TRIM)
        lantern(b, x, 3, 0)
        wall_block(b, x, 3, 2, "diorite_wall")
        lantern(b, x, 4, 2)


def terrace_gardens(b):
    """The four corner gardens: azalea bushes and flowers on their beds, a balustrade of diorite round the terrace's
    edge (open at the grand stair), lamps on its corners."""
    plants = ["azalea", None, "poppy", "allium", None, "azure_bluet", "flowering_azalea", "oxeye_daisy", None, "cornflower"]
    beds = g_gardens()
    hedge = outline(beds, corners=True)
    for x, z in beds:
        if (x, z) in hedge:
            b.set(x, 3, z, "azalea_leaves", distance=1, persistent=True, waterlogged=False)
            continue
        p = plants[zlib.crc32(f"{x},{z},lib".encode()) % len(plants)]
        if p:
            b.set(x, 3, z, p)
    terrace = g_terrace()
    for x, z in outline(terrace, corners=True):
        if z == 3 and 10 <= x <= 30:
            continue
        wall_block(b, x, 3, z, "diorite_wall")
    for x, z in ((0, 3), (GW - 1, 3), (0, GD - 1), (GW - 1, GD - 1), (9, 3), (31, 3)):
        lantern(b, x, 4, z)
    # in each garden a bench of dark oak facing the building, with a lamp post at its end
    for gx, gz, facing in ((5, 9, "north"), (35, 9, "north"), (5, 36, "south"), (35, 36, "south")):
        for dx in (-1, 0, 1):
            stairs(b, gx + dx, 3, gz, DARK_OAK, facing)
        lamp_post(b, gx + 2, 3, gz, post="dark_oak_fence", height=2)


def stage_g4(b):
    dome(b)
    portico(b)
    terrace_gardens(b)


def great_library():
    b = StagedBuild(GW, GH, GD)
    b.begin(1)
    stage_g1(b)
    b.begin(2)
    stage_g2(b)
    b.begin(3)
    stage_g3(b)
    b.begin(4)
    stage_g4(b)
    return b


def great_library_stage(stage=None):
    """For the renders only: the Great Library as it stands after a stage (whole by default)."""
    stages = great_library().stage_builds()
    return stages[(stage or len(stages)) - 1]


def great_library_section(cut_x=GC[0]):
    """For the renders only: the whole Library cut open along x = cut_x (the east half taken away), so a view from the
    east looks into the rotunda, the wings' galleries and under the dome."""
    s = great_library_stage()
    out = Build(GW, GH, GD)
    out.blocks = {p: v for p, v in s.blocks.items() if p[0] <= cut_x}
    return out


# --- the Observatory -----------------------------------------------------------------------------------------------------
# On paper (35.17), 31 x 43 x 31, the front (the grand stair and the courtyard) at z = 0:
# - A raised terrace, 31 x 28 and four high (y 0-3), of deepslate bricks under a polished blackstone rim and balustrade,
#   a stair of five wide steps up its front (z 0-2) between two lamp posts.
# - The courtyard (z 4-12) paved in deepslate tiles ruled by polished blackstone lines, with amethyst set in it: a
#   compass star round the sundial, studs along the path, a ring round the tower's foot. On the west the sundial: a round
#   stone face (smooth stone slabs ringed by polished blackstone stairs, chiseled hour stones at the quarters) round its
#   gnomon (a polished blackstone wall rising to a lightning rod). On the east the orrery: four blackstone posts and a
#   ring beam carrying a gold sun on a chain at the middle and five planets on chains round it (iron, copper, lapis,
#   redstone and raw gold, each on its own orbit and height).
# - The tower, round, centred on (15, 20): 15 across and 30 high (y 4-33), deepslate bricks with tiles and cracked ones
#   in them, three thick to the second floor, two above; eight polished blackstone pilasters up its height, blackstone
#   bands at the floors (13, 23), a plinth at its foot; tall windows on the four sides of each storey; its door on the
#   courtyard under a blackstone hood. Inside: a spiral stair of dark oak round a newel to the top, floors at 13 and 23
#   (the Seer's study below with bookshelves and a lectern, a store of barrels and chests above), lanterns.
# - The top (y 33): a floor right across, a walk round the dome behind a blackstone parapet: the viewing platform.
# - The dome (34-39), 12 across, of cut copper, with a slit of tinted glass three wide from its foot to its crown on the
#   courtyard side and a door onto the walk at the back.
# - The great telescope: a barrel of cut copper 12 long and two thick on a blackstone mount inside the dome, leaning up
#   and out of the slit over the courtyard, a ring of copper at its mouth and an eyepiece at its foot.
# - The low wings either side of the tower (x 1-7 and 23-29, z 15-25), deepslate brick walls under hipped deepslate-tile
#   roofs: the star-chart room on the west (a cartography table on a carpet chart of the night sky, a lectern, chart
#   shelves) and the Seer's lodging on the east (a bed, a brewing stand, a barrel, a table), each with a door onto the
#   courtyard and one into the tower.
# - Palette (60/30/10): deepslate bricks and tiles; polished blackstone trim; cut copper; amethyst in the paving. Panes.
# - Stages, each ending tidy: I the Terrace and the Courtyard (the terrace, the stair, the paving, the sundial and the
#   two wings); II the Tower (its walls, floors, stair and rooms, the top floor and the parapet); III the Dome (its shell
#   and slit, the door, its lamps); IV the Great Telescope and the Orrery.
OW, OH, OD = 31, 43, 31
OC = (15, 20)  # the tower's centre
O_TOP = 33  # the tower's top floor
O_FLOORS = (13, 23)
O_WALL = Mix((7, "deepslate_bricks"), (2, "deepslate_tiles"), (1, "cracked_deepslate_bricks"), seed=41)
O_TERRACE = Mix((6, "deepslate_bricks"), (3, "cobbled_deepslate"), (1, "cracked_deepslate_bricks"), seed=42)
BLACKSTONE = Family("polished_blackstone", "polished_blackstone_stairs", "polished_blackstone_slab",
                    wall="polished_blackstone_wall")
BLACKSTONE_BRICK = Family("polished_blackstone_bricks", "polished_blackstone_brick_stairs",
                          "polished_blackstone_brick_slab", wall="polished_blackstone_brick_wall")
O_DOME_R = 5.9
O_SUNDIAL = (7, 8)
O_ORRERY = (23, 8)


def o_r(x, z):
    return math.hypot(x - OC[0], z - OC[1])


def o_wall(y):
    outer = disc(7.4, OC)
    return outer - disc(4.4 if y < O_FLOORS[1] else 5.4, OC)


def o_inside(y):
    return disc(7.4, OC) - o_wall(y)


def o_core():
    return {(OC[0] + dx, OC[1] + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1)}


O_DIRS = ((0, -1), (0, 1), (-1, 0), (1, 0))


def o_ray(y, d):
    wall = o_wall(y)
    out = [(OC[0] + d[0] * k, OC[1] + d[1] * k) for k in range(1, 9)]
    return list(reversed([c for c in out if c in wall]))


def o_pilasters():
    """Eight cells a block proud of the tower, on its diagonals and between the windows."""
    ring = outline(disc(8.4, OC)) - disc(7.4, OC)
    out = []
    for angle in range(22, 360, 45):
        a = math.radians(angle + 0.5)
        out.append(min(ring, key=lambda c: math.hypot(c[0] - OC[0] - 8 * math.cos(a), c[1] - OC[1] - 8 * math.sin(a))))
    return out


O_WINGS = ((1, 7, "west"), (23, 29, "east"))  # x from, x to, the side; z 15-25


# --- stage I: the terrace and the courtyard -----------------------------------------------------------------------------
def o_terrace(b):
    cells = {(x, z) for x in range(OW) for z in range(3, OD)}
    rim = outline(cells, corners=True)
    tower = disc(8.4, OC)
    for x, z in cells:
        for y in range(0, 3):
            b.set(x, y, z, "polished_blackstone_bricks" if (x, z) in rim and y == 2 else O_TERRACE.at(x, y, z))
        b.set(x, 3, z, o_paving(x, z, rim, tower))
    # the stair up the front: five steps wide, four high (z 0-2 and into the rim), lamp posts either side
    for x in range(13, 18):
        for i, z in enumerate((0, 1, 2)):
            for y in range(0, i):
                b.set(x, y, z, O_TERRACE.at(x, y, z))
            stairs(b, x, i, z, DEEPSLATE_BRICK, "south")
        b.clear(x, 3, 3, x, 3, 3)
        stairs(b, x, 3, 3, DEEPSLATE_BRICK, "south")
        for y in range(0, 3):
            b.set(x, y, 3, O_TERRACE.at(x, y, 3))
    for x in (12, 18):
        for z in (0, 1, 2):
            for y in range(0, 4 if z == 2 else 1 + z):
                b.set(x, y, z, "polished_blackstone_bricks")
    for x in (12, 18):
        for y in range(4, 6):
            wall_block(b, x, y, 3, "polished_blackstone_wall")
        lantern(b, x, 6, 3)
    # the balustrade round the rim, open at the stair
    for x, z in rim:
        if z == 3 and 12 <= x <= 18:
            continue
        wall_block(b, x, 4, z, "polished_blackstone_wall")
    for x, z in ((0, 3), (30, 3), (0, 30), (30, 30), (0, 14), (30, 14)):
        b.set(x, 4, z, "polished_blackstone_bricks")
        lamp_post(b, x, 5, z, post="polished_blackstone_wall", height=1)


def o_paving(x, z, rim, tower):
    if (x, z) in rim:
        return "polished_blackstone"
    sx, sz = O_SUNDIAL
    d = math.hypot(x - sx, z - sz)
    if 4 <= z <= 13 and (x - sx == 0 or z - sz == 0 or abs(x - sx) == abs(z - sz)) and 3 <= d <= 4.3:
        return "amethyst_block"  # the compass star's points
    if 13 <= x <= 17 and 4 <= z <= 12:  # the path to the door
        if x in (13, 17):
            return "polished_blackstone"
        return "amethyst_block" if x == 15 and z % 3 == 0 else "deepslate_tiles"
    if (x, z) in outline(tower) and (x + z) % 4 == 0:
        return "amethyst_block"
    if x % 6 == 3 or z % 6 == 3:
        return "polished_blackstone"
    return "deepslate_tiles"


def o_sundial(b):
    """The sundial: a round face of smooth stone ringed by blackstone stairs, chiseled stones at the four quarters, the
    gnomon a blackstone wall leaning to a lightning rod."""
    sx, sz = O_SUNDIAL
    face = disc(2.4, O_SUNDIAL)
    ring = outline(disc(3.4, O_SUNDIAL)) - face
    for x, z in face:
        if (x, z) == O_SUNDIAL:
            continue
        if abs(x - sx) + abs(z - sz) == 2 and (x == sx or z == sz):
            b.set(x, 4, z, "chiseled_polished_blackstone")
        else:
            slab(b, x, 4, z, "smooth_stone_slab")
    for x, z in disc(3.4, O_SUNDIAL) - face:
        dx, dz = x - sx, z - sz
        facing = ("west" if dx > 0 else "east") if abs(dx) >= abs(dz) else ("north" if dz > 0 else "south")
        stairs(b, x, 4, z, BLACKSTONE, facing)
    b.set(sx, 4, sz, "polished_blackstone")
    wall_block(b, sx, 5, sz, "polished_blackstone_wall")
    wall_block(b, sx, 6, sz, "polished_blackstone_wall")
    b.set(sx, 7, sz, "lightning_rod", facing="up", powered=False, waterlogged=False)


def o_wing(b, x0, x1, side):
    z0, z1 = 15, 25
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            corner = x in (x0, x1) and z in (z0, z1)
            for y in range(4, 8):
                if corner:
                    b.set(x, y, z, "polished_blackstone_bricks")
                elif edge:
                    b.set(x, y, z, "polished_blackstone" if y == 4 else O_WALL.at(x, y, z))
    # windows: two each side, one in the end
    outer = x0 if side == "west" else x1
    inner = x1 if side == "west" else x0
    for z in (17, 23):
        for y in (5, 6):
            pane(b, outer, y, z)
    mid = (x0 + x1) // 2
    for x in (mid - 2, mid + 2):
        for y in (5, 6):
            pane(b, x, y, z1)
    # the doors: onto the courtyard, and into the tower
    door(b, mid, 4, z0, "dark_oak_door", "north")
    stairs(b, mid, 7, z0 - 1, BLACKSTONE, "south", top=True)
    lantern(b, mid - 1, 4, z0 - 1)
    b.clear(inner, 4, 20, inner, 5, 20)
    hip_roof(b, x0 - 1 if side == "west" else x0, x1 if side == "west" else x1 + 1, z0 - 1, z1 + 1, 8, DEEPSLATE_TILE)
    # the ceiling and lamps
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if b.get(x, 8, z) is None:
                b.set(x, 8, z, "dark_oak_planks")
    for z in (18, 22):
        lantern(b, mid, 7, z, hanging=True)
    if side == "west":
        o_chart_room(b, x0, x1)
    else:
        o_lodging(b, x0, x1)


O_SKY = ["bbbbbybbb", "bybbbbbyb", "bbbpbbbbb", "bbbbbbyb.", "ybbbybbbb", "bbbbbbbpb", "bybbbbbbb", "bbbbybbyb",
         "bbbbbbbbb"]


def o_chart_room(b, x0, x1):
    """The star-chart room: a carpet chart of the night sky (black, with yellow stars and a purple planet) round a
    cartography table, a lectern with the star book, shelves of charts and a map on the wall."""
    colours = {"b": "black_carpet", "y": "yellow_carpet", "p": "purple_carpet", ".": "white_carpet"}
    for i, x in enumerate(range(x0 + 1, x1)):
        for j, z in enumerate(range(17, 25)):  # a block in from the doorway: under its lintel a carpet is a step too high (B173)
            b.set(x, 4, z, colours[O_SKY[j % len(O_SKY)][i % 9]])
    mid = (x0 + x1) // 2
    b.set(mid, 4, 20, "cartography_table")
    b.set(mid, 4, 22, "lectern", facing="north", has_book=False, powered=False)
    for z in (16, 17, 23, 24):
        b.set(x0 + 1, 4, z, "chiseled_bookshelf", facing="east", slot_0_occupied=False, slot_1_occupied=False,
              slot_2_occupied=False, slot_3_occupied=False, slot_4_occupied=False, slot_5_occupied=False)
        b.set(x0 + 1, 5, z, "bookshelf")
    b.set(x0 + 1, 4, 20, "barrel", facing="up", open=False)


def o_lodging(b, x0, x1):
    """The Seer's lodging: a bed, a brewing stand on a table, a barrel, a chair and rugs."""
    mid = (x0 + x1) // 2
    for x in range(x0 + 1, x1):
        for z in range(16, 25):
            if (x + z) % 2 == 0 and 2 <= x - x0 <= 4 and 18 <= z <= 22:
                b.set(x, 4, z, "purple_carpet")
    b.bed(x1 - 1, 4, 23, "purple", "south")  # the head against the south wall, one block towards its facing (B537)
    b.set(x1 - 1, 4, 16, "polished_blackstone")
    b.set(x1 - 1, 5, 16, "brewing_stand", has_bottle_0=False, has_bottle_1=False, has_bottle_2=False)
    b.set(x1 - 1, 4, 17, "barrel", facing="up", open=False)
    b.set(x1 - 1, 4, 18, "dark_oak_stairs", facing="east", half="bottom", shape="straight", waterlogged=False)
    b.set(x1 - 1, 4, 22, "bookshelf")


def stage_o1(b):
    o_terrace(b)
    o_sundial(b)
    for x0, x1, side in O_WINGS:
        o_wing(b, x0, x1, side)


# --- stage II: the tower -------------------------------------------------------------------------------------------------
def o_spiral(b, y0, y1):
    for y in range(y0, y1 + 1):
        n = y - 4
        dx, dz = SPIRAL[n % 8]
        stairs(b, OC[0] + dx, y, OC[1] + dz, DARK_OAK, SPIRAL_FACING[n % 8])
        log(b, OC[0], y, OC[1], "dark_oak_log")


def o_tower(b):
    for y in range(4, O_TOP):
        band = y == 4 or y in O_FLOORS or y == O_TOP - 1
        for x, z in o_wall(y):
            b.set(x, y, z, "polished_blackstone" if band else O_WALL.at(x, y, z))
    # the plinth round its foot and the pilasters up its height, ending in a cap under the parapet
    for x, z in outline(disc(8.4, OC)) - disc(7.4, OC):
        if x == OC[0] and z < OC[1] or z == OC[1]:
            continue  # not before the door or in the wings' doorways: off a step, a player's head hits the lintel
        if b.get(x, 4, z) is None:
            stairs(b, x, 4, z, BLACKSTONE, o_toward(x, z))
    for x, z in o_pilasters():
        for y in range(4, O_TOP - 1):
            if b.get(x, y, z) is None or y == 4 and b.stage_of.get((x, y, z)) is None:
                b.set(x, y, z, "polished_blackstone_bricks")
        stairs(b, x, O_TOP - 1, z, BLACKSTONE, o_toward(x, z), top=True)
    # windows on the four sides of each storey, two tall, with a blackstone sill
    for y in (8, 17, 27):
        for d in O_DIRS:
            if y == 8 and d != (0, 1):  # the ground storey: the door on the front, the wings either side
                continue
            for yy in (y, y + 1, y + 2):
                cells = o_ray(yy, d)
                pane(b, cells[0][0], yy, cells[0][1])
                for c in cells[1:]:
                    b.clear(c[0], yy, c[1], c[0], yy, c[1])
            x, z = o_ray(y - 1, d)[0]
            stairs(b, x + d[0], y - 1, z + d[1], BLACKSTONE, o_toward(x + d[0], z + d[1]), top=True)
    # the door onto the courtyard under a hood, and the openings into the wings
    cells = o_ray(4, (0, -1))
    x, z = cells[0]
    for c in cells[1:]:
        b.clear(c[0], 4, c[1], c[0], 5, c[1])
    door(b, x, 4, z, "dark_oak_door", "north")
    for xx in (x - 1, x, x + 1):
        stairs(b, xx, 7, z - 1, BLACKSTONE, "south", top=True)
    for d in ((-1, 0), (1, 0)):
        for c in o_ray(4, d):
            b.clear(c[0], 4, c[1], c[0], 5, c[1])
    o_spiral(b, 4, O_TOP)
    # floors (the stair well left open) and the top: right across, a parapet round its edge
    for y in O_FLOORS:
        for x, z in o_inside(y + 1):
            if (x, z) not in o_core() and b.get(x, y, z) is None:
                b.set(x, y, z, "dark_oak_planks")
    top = disc(7.4, OC)
    for x, z in top:
        if (x, z) in o_core():
            continue
        b.set(x, O_TOP, z, "polished_blackstone" if (x, z) in outline(top) else "deepslate_tiles")
    for x, z in outline(top, corners=True):
        wall_block(b, x, O_TOP + 1, z, "polished_blackstone_wall")
    for x, z in ((OC[0] - 7, OC[1]), (OC[0] + 7, OC[1]), (OC[0], OC[1] + 7)):
        b.set(x, O_TOP + 1, z, "polished_blackstone_bricks")
        lantern(b, x, O_TOP + 2, z)
    o_rooms(b)


def o_toward(x, z):
    return toward(x - OC[0], z - OC[1])


def o_rooms(b):
    """The Seer's study on the ground floor, the store above, the instrument room under the top, lanterns in each."""
    y = 4
    inside = o_inside(y)
    for x, z in inside:
        r = o_r(x, z)
        if r >= 3.6 and (x, z) not in o_core() and b.get(x, y, z) is None:
            if abs(x - OC[0]) >= 2 and abs(z - OC[1]) >= 2:
                b.set(x, y, z, "bookshelf")
                if b.get(x, y + 1, z) is None:
                    b.set(x, y + 1, z, "bookshelf")
    b.set(OC[0] + 2, 4, OC[1] + 3, "lectern", facing="north", has_book=False, powered=False)
    for y in (4, O_FLOORS[0] + 1, O_FLOORS[1] + 1):
        for dx, dz in ((2, -2), (-2, 2)):
            if b.get(OC[0] + dx, y, OC[1] + dz) is None:
                lantern(b, OC[0] + dx, y, OC[1] + dz)
    y = O_FLOORS[0] + 1
    for x, z in sorted(o_inside(y)):
        if o_r(x, z) >= 4.4 and abs(x - OC[0]) >= 2 and abs(z - OC[1]) >= 2 and b.get(x, y, z) is None:
            b.set(x, y, z, "barrel" if (x + z) % 2 else "chest", **({"facing": "up", "open": False} if (x + z) % 2 else
                  {"facing": o_toward(x, z), "type": "single", "waterlogged": False}))
    y = O_FLOORS[1] + 1
    b.set(OC[0] - 3, y, OC[1] - 2, "cartography_table")
    b.set(OC[0] + 3, y, OC[1] + 2, "lectern", facing="west", has_book=False, powered=False)
    b.set(OC[0] + 3, y, OC[1] - 2, "chiseled_bookshelf", facing="west", slot_0_occupied=False, slot_1_occupied=False,
          slot_2_occupied=False, slot_3_occupied=False, slot_4_occupied=False, slot_5_occupied=False)


def stage_o2(b):
    o_tower(b)


# --- stage III: the dome ------------------------------------------------------------------------------------------------
def o_telescope():
    """The telescope's barrel, lower end first: 12 long and two high, from the mount up and out of the slit."""
    cells = []
    for i in range(12):
        z = OC[1] + 2 - i
        y = O_TOP + 2 + int(i * 0.45)
        cells += [(OC[0], y, z), (OC[0], y + 1, z)]
    return cells


O_DRUM = 2  # the dome's drum: two courses of blackstone under the copper


def o_dome_cells():
    """The drum (O_TOP + 1 to O_TOP + O_DRUM) and the copper hemisphere on it, as {cell: distance from its centre}."""
    out = {}
    base = O_TOP + O_DRUM
    for y in range(O_TOP + 1, base + 7):
        for x in range(OC[0] - 7, OC[0] + 8):
            for z in range(OC[1] - 7, OC[1] + 8):
                d = math.sqrt((x - OC[0]) ** 2 + (max(y - base, 0)) ** 2 + (z - OC[1]) ** 2)
                if O_DOME_R - 1.3 < d <= O_DOME_R or (d <= O_DOME_R and y == base + 6):
                    out[(x, y, z)] = d
    return out


def stage_o3(b):
    scope = set(o_telescope())
    cells = o_dome_cells()
    doorway = {(x, y, z) for (x, y, z) in cells if x == OC[0] and z > OC[1] and y in (O_TOP + 1, O_TOP + 2)}
    for (x, y, z) in sorted(cells):
        if (x, y, z) in scope or (x, y, z) in doorway:
            continue
        if abs(x - OC[0]) <= 1 and z < OC[1] and y >= O_TOP + 3:
            b.set(x, y, z, "tinted_glass")
        elif y <= O_TOP + O_DRUM:
            b.set(x, y, z, "polished_blackstone_bricks" if y == O_TOP + 1 else "polished_blackstone")
        else:
            b.set(x, y, z, "cut_copper")
    b.set(OC[0], O_TOP + O_DRUM + 6, OC[1], "lightning_rod", facing="up", powered=False, waterlogged=False)
    zd = max(z for (x, y, z) in doorway)
    door(b, OC[0], O_TOP + 1, zd, "dark_oak_door", "south")
    for x, z in ((OC[0] - 3, OC[1] + 2), (OC[0] + 3, OC[1] - 1), (OC[0] - 3, OC[1] - 2)):
        lantern(b, x, O_TOP + 1, z)
    b.set(OC[0] + 3, O_TOP + 1, OC[1] + 2, "lectern", facing="west", has_book=False, powered=False)


# --- stage IV: the great telescope and the orrery ------------------------------------------------------------------------
def o_great_telescope(b):
    cells = o_telescope()
    x0, y0, z0 = cells[0]
    for y in range(O_TOP + 1, y0):
        if y < y0 - 1:
            wall_block(b, x0, y, z0, "polished_blackstone_wall")
        else:
            b.set(x0, y, z0, "chiseled_polished_blackstone")
    for x, y, z in cells:
        b.set(x, y, z, "cut_copper")
    # the eyepiece at its foot, a collar of copper round its mouth
    xe, ye, ze = cells[0]
    stairs(b, xe, ye, ze + 1, CUT_COPPER, "north")
    xm, ym, zm = cells[-1]
    for dx in (-1, 1):
        slab(b, xm + dx, ym, zm, CUT_COPPER, top=True)
        slab(b, xm + dx, ym + 1, zm, CUT_COPPER)


O_PLANETS = ((2, 0, 7, "iron_block"), (0, 3, 6, "copper_block"), (-3, -1, 7, "lapis_block"),
             (1, -3, 6, "redstone_block"), (-2, 2, 6, "raw_gold_block"))


def o_orrery(b):
    """Four blackstone posts and a ring beam (y 4-9) over a blackstone dais; the gold sun on a chain at the middle, five
    planets on chains round it, each at its own height."""
    ox, oz = O_ORRERY
    for x, z in disc(4.4, O_ORRERY):
        if b.get(x, 4, z) is None:
            slab(b, x, 4, z, BLACKSTONE)
    posts_at = [(ox - 3, oz - 3), (ox + 3, oz - 3), (ox - 3, oz + 3), (ox + 3, oz + 3)]
    for x, z in posts_at:
        b.set(x, 4, z, "polished_blackstone_bricks")
        for y in range(5, 10):
            wall_block(b, x, y, z, "polished_blackstone_wall")
    for x in range(ox - 3, ox + 4):
        for z in range(oz - 3, oz + 4):
            if x in (ox - 3, ox + 3) or z in (oz - 3, oz + 3) or x == ox or z == oz:
                if (x, z) in posts_at:
                    b.set(x, 10, z, "polished_blackstone_bricks")
                else:
                    slab(b, x, 10, z, BLACKSTONE, top=True)
    b.set(ox, 10, oz, "chiseled_polished_blackstone")
    chain(b, ox, 9, oz)
    chain(b, ox, 8, oz)
    b.set(ox, 7, oz, "gold_block")
    for dx, dz, y, name in O_PLANETS:
        x, z = ox + dx, oz + dz
        if not (x in (ox - 3, ox + 3) or z in (oz - 3, oz + 3) or x == ox or z == oz):
            # hang from a beam of its own across the ring
            for xx in range(ox - 3, ox + 4):
                if b.get(xx, 10, z) is None:
                    slab(b, xx, 10, z, BLACKSTONE, top=True)
        for yy in range(y + 1, 10):
            chain(b, x, yy, z)
        b.set(x, y, z, name)


def chain(b, x, y, z):
    b.set(x, y, z, "chain", axis="y", waterlogged=False)


def stage_o4(b):
    o_great_telescope(b)
    o_orrery(b)


def observatory():
    b = StagedBuild(OW, OH, OD)
    b.begin(1)
    stage_o1(b)
    b.begin(2)
    stage_o2(b)
    b.begin(3)
    stage_o3(b)
    b.begin(4)
    stage_o4(b)
    return b


def observatory_stage(stage=None):
    """For the renders only: the Observatory as it stands after a stage (whole by default)."""
    stages = observatory().stage_builds()
    return stages[(stage or len(stages)) - 1]


def observatory_section(cut_x=OC[0]):
    """For the renders only: the whole Observatory cut open along x = cut_x (the east half taken away)."""
    s = observatory_stage()
    out = Build(OW, OH, OD)
    out.blocks = {p: v for p, v in s.blocks.items() if p[0] <= cut_x}
    return out


# --- the Great Wall (35.15) ----------------------------------------------------------------------------------------------
# The Great Wall, 97 x 25 x 15. It is placed like the other walls: the player stands in the village and places it facing
# out, so its front (z = 0) is the village side, with its stairs, and the field is behind it (z 14). Design on paper:
# - A curtain 7 thick (z 3-9) and 12 high: solid masonry to y 8, the walk paved on it at 9 (a player stands at 10), a
#   breastwork corbelled out one over the field (z 10) on a row of upside-down stairs, its merlons to y 11; a low parapet
#   of stone brick wall on the village side, with braziers (campfires on chiseled stone) along it.
# - Five towers' worth of masses in a symmetric row (the east half mirrors the west about x = 48): the end towers (x 2-10
#   and 86-94), the quarter towers (20-28 and 68-76), 9 x 9 (z 3-11, flush with the curtain on the village side and two
#   out on the field side), 18 high (y 0-17) under hipped spruce roofs (an 11 x 11 overhang, eaves of upside-down
#   stairs), and the Great Gate's gatehouse in the middle (x 40-56, 17 wide, z 2-12), as high, under a long hipped roof:
#   the fifth tower, between the quarter towers.
# - Each tower (35.15a): solid masonry from the ground to the walk, on a solid base a block out all round (two courses
#   of deepslate bricks under a battered step of stairs, the plinth stepping out round it again), angle buttresses up
#   its free corners capped with stairs, a cordon of upside-down stairs round it at the walk with lanterns on its
#   corners; on the field side a banner over a blind slit (and on the outer side at the ends), on the village side a
#   lantern in a niche under a keystone and a hood, a banner above. Over the base the walk-level room (doors onto the
#   walk, a guard's grindstone, a barrel, a window to the field, and on the side facing the gate an alcove 3 wide and 3
#   high where a sentry golem stands, open to the walk), a stair on up to the top room (windows on three sides) under an
#   attic floor; a cornice at 13, a banner to the field, a watch lantern on the roof's peak.
# - Along the runs (35.15a): a banner in each bay between the buttresses on the field side, hung under the breastwork,
#   an arrow slit through the breastwork over it, taller chiseled merlons under slabs over the buttresses, vines down
#   the low courses beside them; lanterns on the parapet between the braziers, and on the outer runs' village side a
#   lantern on a corbel in each bay. The gatehouse has the towers' angle buttresses and cordon.
# - The Great Gate: a gateway 5 wide (x 46-50) through the gatehouse, paved, vaulted (y 1-6), a keystone over each mouth,
#   lanterns in the vault; across it spruce fence gates two high under a portcullis of iron bars, which runs up a slot
#   into the hall and hangs from a windlass beam on a post. Either side a guardroom with its own door and a stair round
#   its walls; the hall at the walk with doors onto it; the top room with a door onto a bretèche over the gate (a gallery
#   on two courses of corbels, crenellated). Braziers on posts at both mouths, banners either side of them.
# - The plinth: deepslate bricks, battered on the field side (a full course and a step out at y 0, a step at 1, two
#   blocks out from every face), a step of stairs along the village side. A buttress every four or five blocks on the
#   field side of the runs, and on the village side of the outer runs; on the inner runs (x 29-39 and 57-67) a stair of
#   ten steps up the village side to a landing by the gatehouse, behind a cobblestone wall.
# - Palette (60/30/10): stone bricks to the field and in the towers (mossy and cracked ones on the low courses), cobblestone
#   to the village and at the heart; a deepslate brick plinth; spruce roofs, floors and doors; iron bars and chiseled stone
#   as accents. Panes.
# - Stages, each ending tidy: I the Great Gate (the gatehouse whole, its plinth, gate and roof); II the West Run (the west
#   runs to under the walk, the west towers to their top room's floor, the west stair, their plinth); III the East Run
#   (the same, mirrored); IV the Walk and the Towers' Crowns (the walk, breastwork, merlons, parapet and braziers on every
#   run; the towers' top rooms, cornices, roofs and banners).
WW, WH, WD = 97, 25, 15
W_MID = 48  # the Great Gate's middle: the east half is the west half mirrored about it
W_IN, W_OUT = 3, 9  # the curtain's inner and outer faces (z); the village is on the z = 0 side, and its stairs
W_LIP = 10  # the corbelled breastwork (z)
W_WALK = 9  # the walk's paving (y): a player stands on it at 10, the merlons' tops are at 12
W_TOWERS = (2, 20)  # the west half's towers' west x, each 9 x 9 (z 3-11); the east half's mirror them
W_TZ = (3, 11)
W_GATE = (40, 56)  # the gatehouse, x 40-56 (17 wide), z 2-12
W_GZ = (2, 12)
W_ARCH = (46, 50)  # the gateway
W_RUNS = ((11, 19), (29, 39))  # the west half's runs of curtain
W_FLIGHT = 29  # the stair up from the village: steps x 29-38, a landing at 39 (z 0-2)
W_BRAZIERS = (12, 15, 18, 30, 33, 36)  # on the inner parapet (the east half's mirror them)
W_LAMPS = (13, 17, 31, 35)  # lanterns on the inner parapet between them (35.15a)
W_PORTCULLIS = 10  # the portcullis's z in the gateway
W_TOP = 17  # the last course of the towers and the gatehouse (their attic floor); the roofs from 18


class Courses(Mix):
    """The wall's stone bricks by course: mossy and cracked in clusters on the low courses, now and then cracked above."""

    LOW = Mix((5, "stone_bricks"), (3, "mossy_stone_bricks"), (2, "cracked_stone_bricks"), seed=51)
    MID = Mix((12, "stone_bricks"), (1, "mossy_stone_bricks"), (2, "cracked_stone_bricks"), seed=52)
    HIGH = Mix((20, "stone_bricks"), (1, "cracked_stone_bricks"), seed=53)

    def __init__(self):
        super().__init__((1, "stone_bricks"))

    def at(self, x, y, z):
        return (self.LOW if y <= 3 else self.MID if y <= 6 else self.HIGH).at(x, y, z)


class Rubble(Mix):
    """The village side's cobblestone, with stone bricks through it and moss low down."""

    LOW = Mix((6, "cobblestone"), (3, "mossy_cobblestone"), (2, "stone_bricks"), seed=54)
    HIGH = Mix((8, "cobblestone"), (3, "stone_bricks"), (1, "mossy_cobblestone"), seed=55)

    def __init__(self):
        super().__init__((1, "cobblestone"))

    def at(self, x, y, z):
        return (self.LOW if y <= 3 else self.HIGH).at(x, y, z)


W_FACE = Courses()
W_RUBBLE = Rubble()
W_PLINTH = Mix((8, "deepslate_bricks"), (2, "cracked_deepslate_bricks"), seed=56)
W_PAVING = Mix((5, "stone_bricks"), (2, "cobblestone"), (1, "cracked_stone_bricks"), seed=57)
W_BANNER = [("white", "minecraft:triangles_top"), ("light_gray", "minecraft:stripe_bottom"), ("white", "minecraft:border")]


class Mirror:
    """Draws through x -> 96 - x, east and west swapped: the Wall's east half is its west half mirrored. The kit's
    helpers draw through it as on a build."""

    FLIP = {"east": "west", "west": "east", "left": "right", "right": "left"}

    def __init__(self, b, on):
        self.b, self.on = b, on

    def x(self, x):
        return WW - 1 - x if self.on else x

    def set(self, x, y, z, name, **props):
        if self.on:
            for k in ("facing", "hinge"):
                if k in props:
                    props[k] = self.FLIP.get(props[k], props[k])
        self.b.set(self.x(x), y, z, name, **props)

    def get(self, x, y, z):
        return self.b.get(self.x(x), y, z)

    def clear(self, x0, y0, z0, x1, y1, z1):
        self.b.clear(self.x(x0), y0, z0, self.x(x1), y1, z1)

    def set_nbt(self, x, y, z, nbt):
        self.b.set_nbt(self.x(x), y, z, nbt)


def halves(b):
    return Mirror(b, False), Mirror(b, True)


def w_footprint():
    """The masonry's cells (x, z) at its foot: the runs, the towers and the gatehouse."""
    cells = set()
    for m in (lambda x: x, lambda x: WW - 1 - x):
        for x0, x1 in W_RUNS:
            cells |= {(m(x), z) for x in range(x0, x1 + 1) for z in range(W_IN, W_OUT + 1)}
        for t in W_TOWERS:  # the tower on its solid base, a block out all round (35.15a)
            cells |= {(m(x), z) for x in range(t - 1, t + 10) for z in range(W_TZ[0] - 1, W_TZ[1] + 2)}
    cells |= {(x, z) for x in range(W_GATE[0], W_GATE[1] + 1) for z in range(W_GZ[0], W_GZ[1] + 1)}
    return cells


def w_stage_of(x):
    """Which stage lays the foot round column x: the gatehouse's in I, the west's in II, the east's in III."""
    return 1 if W_GATE[0] <= x <= W_GATE[1] else 2 if x < W_MID else 3


def w_plinth(b, stage):
    """The battered plinth round this stage's foot: on the outer side deepslate bricks stepping out two blocks (a full
    course and a step of stairs at y 0, a step at y 1); on the village side one step of stairs along the faces. Cells
    already drawn (doors' steps, the stair, braziers) and the gateway's paving are left as they are."""
    foot = w_footprint()

    def near(x, z, cells):
        return any((x + dx, z + dz) in cells for dx in (-1, 0, 1) for dz in (-1, 0, 1))

    def facing(x, z, cells):
        for d, (dx, dz) in DIRS.items():
            if (x + dx, z + dz) in cells:
                return d
        for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
            if (x + dx, z + dz) in cells:
                return "north" if dz < 0 else "south"
        return None

    cols = [(x, z) for x in range(WW) for z in range(WD) if w_stage_of(x) == stage and not W_ARCH[0] <= x <= W_ARCH[1]]
    ring1 = {(x, z) for x in range(WW) for z in range(WD) if (x, z) not in foot and near(x, z, foot)}
    for x, z in cols:
        if (x, z) in foot:
            continue
        inner = z < W_IN
        if (x, z) in ring1:
            d = facing(x, z, foot)
            square = any((x + dx, z + dz) in foot for dx, dz in DIRS.values())
            if inner:
                if square and b.get(x, 0, z) is None:
                    stairs(b, x, 0, z, DEEPSLATE_BRICK, d)
                continue
            if b.get(x, 0, z) is None:
                b.set(x, 0, z, W_PLINTH)
            if b.get(x, 1, z) is None:
                stairs(b, x, 1, z, DEEPSLATE_BRICK, d)
        elif not inner and near(x, z, ring1) and b.get(x, 0, z) is None:
            stairs(b, x, 0, z, DEEPSLATE_BRICK, facing(x, z, ring1))


def w_banner(b, x, y, z, facing):
    b.set_nbt(x, y, z, Compound({"id": String("minecraft:banner"), "patterns": List[Compound]([
        Compound({"color": String(c), "pattern": String(p)}) for c, p in W_BANNER])}))
    b.set(x, y, z, "blue_wall_banner", facing=facing)


def brazier(b, x, y, z):
    b.set(x, y, z, "campfire", facing="south", lit=True, signal_fire=False, waterlogged=False)


def climb(b, cells, y0, fam):
    """A stair up the cells, a step a cell from y0, each rising towards the next; the head room over each step is cleared
    (through a floor, too). Returns the y of the last step."""
    y = y0
    for i, (x, z) in enumerate(cells):
        y = y0 + i
        nx, nz = cells[i + 1] if i + 1 < len(cells) else (2 * x - cells[i - 1][0], 2 * z - cells[i - 1][1])
        b.clear(x, y + 1, z, x, y + 2, z)
        stairs(b, x, y, z, fam, {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}[(nx - x, nz - z)])
    return y


# --- stage I: the Great Gate ---------------------------------------------------------------------------------------------
def gate_shell(p):
    """The gatehouse's west half (x 40-48): its walls to the attic floor, the passage wall, the vault over the gateway."""
    x0, z0, z1 = W_GATE[0], W_GZ[0], W_GZ[1]
    for y in range(0, W_TOP + 1):
        for x in range(x0, W_MID + 1):
            for z in range(z0, z1 + 1):
                edge = x == x0 or z in (z0, z1)
                if edge:
                    p.set(x, y, z, W_PLINTH if y <= 1 else W_FACE)
                elif y == 0:
                    p.set(x, y, z, "cobblestone")
                elif x == W_ARCH[0] - 1 and y <= 8:
                    p.set(x, y, z, W_FACE)  # the passage's wall
                elif x >= W_ARCH[0] and 7 <= y <= 8:
                    p.set(x, y, z, "cobblestone")  # the masonry over the vault
                elif y in (9, 13, W_TOP):
                    p.set(x, y, z, "spruce_planks")
    # the gateway, through: paved, vaulted, a keystone over each mouth
    p.clear(W_ARCH[0], 1, z0, W_MID, 6, z1)
    for z in range(z0, z1 + 1):
        stairs(p, W_ARCH[0], 6, z, STONE_BRICK, "east", top=True)
    for z in range(0, WD):
        for x in range(W_ARCH[0], W_MID + 1):
            if z == 0:
                stairs(p, x, 0, z, STONE_BRICK, "south")
            elif z == WD - 1:
                stairs(p, x, 0, z, STONE_BRICK, "north")
            else:
                p.set(x, 0, z, W_PAVING)
    for z in (z0, z1):
        p.set(W_MID, 7, z, "chiseled_stone_bricks")
    lantern(p, W_MID, 6, 5, hanging=True)
    lantern(p, W_MID, 6, 8, hanging=True)
    # braziers at both mouths of the gateway, on posts either side
    for z in (0, WD - 1):
        p.set(W_ARCH[0] - 1, 0, z, "stone_bricks")
        p.set(W_ARCH[0] - 1, 1, z, "chiseled_stone_bricks")
        brazier(p, W_ARCH[0] - 1, 2, z)


def gate_portcullis(p):
    """The Great Gate: spruce fence gates across the gateway, two high, under a portcullis of iron bars that runs up a
    slot in the vault to the hall, where it hangs from a windlass beam between two posts."""
    z = W_PORTCULLIS
    for x in range(W_ARCH[0], W_MID + 1):
        for y in (1, 2):
            p.set(x, y, z, "spruce_fence_gate", facing="south", open=False, powered=False, in_wall=False)
        for y in range(3, 11):
            if not (x == W_ARCH[0] and y == 6):
                p.set(x, y, z, "iron_bars", north=False, south=False, east=False, west=False, waterlogged=False)
    p.set(W_ARCH[0] - 1, 10, z, "stripped_spruce_log", axis="y")
    for x in range(W_ARCH[0] - 1, W_MID + 1):
        log(p, x, 11, z, "spruce_log", axis="x")


def gate_rooms(p):
    """The west half's rooms: the guardroom on the ground (a door from the village, a stair up round its walls, an arrow
    slit), the hall at the walk (a door onto the walk, the stair on up) and the top room (windows on three sides)."""
    x0, z0, z1 = W_GATE[0], W_GZ[0], W_GZ[1]
    # the guardroom: its door, a step and a lantern by it, a hood over it
    door(p, 42, 1, z0, "spruce_door", "north")
    stairs(p, 42, 0, z0 - 1, STONE_BRICK, "south")
    for x in (41, 42, 43):
        stairs(p, x, 3, z0 - 1, STONE_BRICK, "south", top=True)
    p.set(41, 0, z0 - 1, "chiseled_stone_bricks")
    lantern(p, 41, 1, z0 - 1)
    climb(p, [(44, z) for z in range(4, 12)] + [(43, 11)], 1, STONE_BRICK)
    p.clear(42, 2, z1, 42, 4, z1)  # an arrow slit to the field
    p.set(41, 1, 11, "barrel", facing="up", open=False)
    lantern(p, 41, 1, 4)
    # the hall at the walk: the door onto it, a window to the field, a stair on up, lanterns under its ceiling
    door(p, x0, 10, 5, "spruce_door", "west")
    pane(p, 43, 10, z1)
    pane(p, 43, 11, z1)
    climb(p, [(x, z0 + 1) for x in range(41, 45)], 10, SPRUCE)
    lantern(p, 43, 12, 7, hanging=True)
    p.set(42, 10, 9, "grindstone", face="floor", facing="east")
    # the top room: windows on three sides, a door onto the bretèche over the gate
    for x, z in ((43, z0), (43, z1), (W_MID, z0)):
        pane(p, x, 14, z)
        pane(p, x, 15, z)
    pane(p, x0, 14, 7)
    pane(p, x0, 15, 7)
    lantern(p, 44, 16, 7, hanging=True)


def gate_breteche(p):
    """A bretèche over the gate on the field side: a gallery on two courses of corbels, its breastwork crenellated."""
    z1 = W_GZ[1]
    for x in range(W_ARCH[0] - 1, W_MID + 1):
        stairs(p, x, 12, z1 + 1, STONE_BRICK, "north", top=True)
        p.set(x, 13, z1 + 1, "stone_bricks")
        stairs(p, x, 13, z1 + 2, STONE_BRICK, "north", top=True)
        p.set(x, 14, z1 + 2, W_FACE)
        if (x - W_ARCH[0] + 1) % 2 == 0:
            p.set(x, 15, z1 + 2, W_FACE)
    for y in (14, 15):
        p.set(W_ARCH[0] - 1, y, z1 + 1, W_FACE)
    door(p, W_MID, 14, z1, "spruce_door", "south")


def gate_piers(p):
    """Buttresses either side of the gateway on both faces (the field side's carry the bretèche's corbels), and a string
    course of upside-down stairs round the gatehouse at 13, the hall's ceiling."""
    z0, z1 = W_GZ
    x = W_ARCH[0] - 2
    for y in range(0, 13):
        p.set(x, y, z0 - 1, W_PLINTH if y <= 1 else W_FACE)
    for y in range(0, 12):
        p.set(x, y, z1 + 1, W_PLINTH if y <= 1 else W_FACE)
    stairs(p, x, 12, z1 + 1, STONE_BRICK, "north")
    for xx in range(W_GATE[0] - 1, W_MID + 1):
        for z in range(z0 - 1, z1 + 2):
            if (xx == W_GATE[0] - 1 or z in (z0 - 1, z1 + 1)) and p.get(xx, 13, z) is None:
                d = "south" if z == z0 - 1 else "north" if z == z1 + 1 else "east"
                stairs(p, xx, 13, z, STONE_BRICK, d, top=True)


def gate_dress(p):
    """35.15a: the gatehouse dressed as the towers are: angle buttresses at its corners capped with stairs, and the
    cordon of upside-down stairs round it at the walk."""
    x0, (z0, z1) = W_GATE[0], W_GZ
    for x, z, d in ((x0, z0 - 1, "south"), (x0, z1 + 1, "north"), (x0 - 1, z1, "east")):
        p.set(x, 0, z, W_PLINTH)
        p.set(x, 1, z, W_PLINTH)
        p.set(x, 2, z, W_PLINTH)
        for y in range(3, 8):
            p.set(x, y, z, W_FACE)
        stairs(p, x, 8, z, STONE_BRICK, d)
    ring = [(x, z0 - 1, "south") for x in range(x0, W_MID + 1)] + [(x, z1 + 1, "north") for x in range(x0 - 1, W_MID + 1)]
    ring.append((x0 - 1, z1, "east"))
    for x, z, d in ring:
        if p.get(x, W_WALK, z) is None and not W_ARCH[0] <= x <= W_ARCH[1]:
            stairs(p, x, W_WALK, z, STONE_BRICK, d, top=True)


def gate_roof(b):
    x0, x1, z0, z1 = W_GATE[0] - 1, W_GATE[1] + 1, W_GZ[0] - 1, W_GZ[1] + 1
    eaves(b, x0, x1, z0, z1, W_TOP)
    hip_roof(b, x0, x1, z0, z1, W_TOP + 1, SPRUCE)


def eaves(b, x0, x1, z0, z1, y):
    """Upside-down spruce stairs round a roof's overhang, under its lowest ring."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x in (x0, x1) or z in (z0, z1)) and b.get(x, y, z) is None:
                d = "south" if z == z0 else "north" if z == z1 else "east" if x == x0 else "west"
                stairs(b, x, y, z, SPRUCE, d, top=True)


def stage_w1(b):
    for p in halves(b):
        gate_shell(p)
        gate_portcullis(p)
        gate_rooms(p)
        gate_breteche(p)
        gate_piers(p)
        gate_dress(p)
        for z, d in ((W_GZ[0] - 1, "north"), (W_GZ[1] + 1, "south")):
            w_banner(p, 42, 8, z, d)
    gate_roof(b)
    w_plinth(b, 1)


# --- stages II and III: the runs -----------------------------------------------------------------------------------------
def w_bays(x0, x1):
    """A run's buttresses (x) and the middles of the bays between them and its ends (rounded towards the run's middle)."""
    piers = [(x0 + x1) // 2] if x1 - x0 < 10 else [x0 + 3, x1 - 3]
    edges = [x0 - 1] + piers + [x1 + 1]
    centre = (x0 + x1) / 2
    mids = [int(m) if m == int(m) else int(m + (0.5 if m < centre else -0.5)) for m in ((a + c) / 2 for a, c in zip(edges, edges[1:]))]
    return piers, mids


def w_run(p, x0, x1):
    """A run of curtain to under the walk (y 0-8): a deepslate plinth, stone brick facing to the field, cobblestone to
    the village, cobblestone at its heart; a buttress every four or five blocks on the field side, and on the village side
    where no stair is."""
    for x in range(x0, x1 + 1):
        for z in range(W_IN, W_OUT + 1):
            for y in range(0, W_WALK):
                if y <= 1 and z in (W_IN, W_OUT):
                    p.set(x, y, z, W_PLINTH)
                elif z == W_OUT:
                    p.set(x, y, z, W_FACE)
                elif z == W_IN:
                    p.set(x, y, z, W_RUBBLE)
                else:
                    p.set(x, y, z, "cobblestone")
    piers, mids = w_bays(x0, x1)
    for x in piers:
        p.set(x, 0, W_OUT + 1, W_PLINTH)
        for y in range(1, W_WALK):
            p.set(x, y, W_OUT + 1, W_FACE)
        if x1 - x0 < 10:  # the outer run's inner face: no stair there
            p.set(x, 0, W_IN - 1, W_PLINTH)
            for y in range(1, 7):
                p.set(x, y, W_IN - 1, W_RUBBLE)
            stairs(p, x, 7, W_IN - 1, STONE_BRICK, "south")
    # 35.15a: a banner in each bay on the field side, hung under the breastwork; on the outer run's village side a
    # lantern on a corbel in each bay
    for x in range(x0, x1 + 1):  # vines down the low courses beside the buttresses, weathering in clusters
        n = (0, 3, 1, 4, 2, 0, 2, 3)[(x * 5 + x0) % 8] if any(abs(x - q) == 1 for q in piers) else 0
        for y in range(6, 6 - n, -1):
            if p.get(x, y, W_OUT + 1) is None:
                p.set(x, y, W_OUT + 1, "vine", north=True, south=False, east=False, west=False, up=False)
    for mid in mids:
        w_banner(p, mid, 7, W_OUT + 1, "south")
        if x1 - x0 < 10:
            stairs(p, mid, 4, W_IN - 1, STONE_BRICK, "south", top=True)
            lantern(p, mid, 5, W_IN - 1)


def w_flight(p):
    """The stair up from the village to the walk: ten steps along the inner face, on solid masonry, a parapet of
    cobblestone wall on its open side, to a landing by the gatehouse."""
    for i in range(10):
        x = W_FLIGHT + i
        for y in range(0, i + 1):
            p.set(x, y, 0, W_RUBBLE)
        wall_block(p, x, i + 1, 0, "cobblestone_wall")
        for z in (1, 2):
            for y in range(0, i):
                p.set(x, y, z, W_RUBBLE)
            stairs(p, x, i, z, STONE_BRICK, "east")
    x = W_FLIGHT + 10
    for z in (0, 1, 2):
        for y in range(0, W_WALK):
            p.set(x, y, z, W_RUBBLE)
        p.set(x, W_WALK, z, W_PAVING)
    wall_block(p, x, W_WALK + 1, 0, "cobblestone_wall")


def w_tower_base(p, t, outer_end):
    """The tower's solid base (35.15a): a block out from the tower all round, two courses of deepslate bricks under a
    battered step of stairs (the plinth steps out round it again); angle buttresses up the corners to the walk, capped
    with stairs; a cordon of upside-down stairs round it at the walk (carrying on the breastwork's corbels on the field
    side); a banner over a blind slit on the field side (and the outer side at the ends); a lantern in a niche under a
    keystone and a banner on the village side."""
    z0, z1 = W_TZ
    for x in range(t - 1, t + 10):
        for z in range(z0 - 1, z1 + 2):
            if t <= x <= t + 8 and z0 <= z <= z1 or p.get(x, 0, z) is not None:
                continue  # the tower itself, the curtain it stands in, the stair from the village
            p.set(x, 0, z, W_PLINTH)
            p.set(x, 1, z, W_PLINTH)
            d = "south" if z == z0 - 1 else "north" if z == z1 + 1 else "east" if x == t - 1 else "west"
            stairs(p, x, 2, z, DEEPSLATE_BRICK, d)
    # angle buttresses at the corners, on the faces that are free (not in the curtain)
    piers = [(t, z1 + 1, "north"), (t + 8, z1 + 1, "north"), (t - 1, z1, "east"), (t + 9, z1, "west"),
             (t, z0 - 1, "south"), (t + 8, z0 - 1, "south")]
    if outer_end:
        piers.append((t - 1, z0, "east"))
    for x, z, d in piers:
        if p.get(x, 3, z) is not None or (x >= W_FLIGHT and z < z0):
            continue
        p.set(x, 2, z, W_PLINTH)
        for y in range(3, 8):
            p.set(x, y, z, W_FACE)
        stairs(p, x, 8, z, STONE_BRICK, d)
    # the cordon at the walk, round the free faces
    ring = [(x, z1 + 1, "north") for x in range(t - 1, t + 10)] + [(t - 1, z1, "east"), (t + 9, z1, "west")]
    ring += [(x, z0 - 1, "south") for x in range(t, t + 9)]
    if outer_end:
        ring += [(t - 1, z, "east") for z in range(z0 - 1, z1)]
    for x, z, d in ring:
        if p.get(x, W_WALK, z) is None and not (x >= W_FLIGHT and z < z0):
            stairs(p, x, W_WALK, z, STONE_BRICK, d, top=True)
    for x, z in ((t - 1, z1 + 1), (t + 9, z1 + 1), (t, z0 - 1), (t + 8, z0 - 1)):
        if p.get(x, W_WALK, z) is not None and p.get(x, W_WALK + 1, z) is None:
            lantern(p, x, W_WALK + 1, z)  # lanterns on the cordon's corners
    # the field side: a blind slit under a banner; the same on the outer side at the ends
    x = t + 4
    p.clear(x, 3, z1, x, 5, z1)
    p.set(x, 6, z1, "chiseled_stone_bricks")
    w_banner(p, x, 7, z1 + 1, "south")
    if outer_end:
        p.clear(t, 3, 7, t, 5, 7)
        p.set(t, 6, 7, "chiseled_stone_bricks")
        w_banner(p, t - 1, 7, 7, "west")
    # the village side: a lantern in a niche, a keystone over it, a hood of stairs, a banner above
    p.clear(x, 3, z0, x, 4, z0)
    lantern(p, x, 3, z0)
    p.set(x, 5, z0, "chiseled_stone_bricks")
    for xx in (x - 1, x, x + 1):
        stairs(p, xx, 5, z0 - 1, STONE_BRICK, "south", top=True)
    w_banner(p, x, 7, z0 - 1, "north")


def w_tower(p, t, west_door):
    """A tower (x t to t + 8, z 3-11) to the floor of its top room (y 13): solid to the walk on its decorated base
    (35.15a), then the walk-level room, which has doors onto the walk, the guard's grindstone and, on the side facing
    the gate, the alcove where the sentry golem stands; a stair on up."""
    z0, z1 = W_TZ
    for y in range(0, 14):
        for x in range(t, t + 9):
            for z in range(z0, z1 + 1):
                edge = x in (t, t + 8) or z in (z0, z1)
                if edge:
                    p.set(x, y, z, W_PLINTH if y <= 1 else W_FACE)
                elif y < W_WALK:
                    p.set(x, y, z, "cobblestone")
                elif y in (W_WALK, 13):
                    p.set(x, y, z, "spruce_planks")
    w_tower_base(p, t, west_door is None)
    x = t + 4
    # the walk-level room: doors onto the walk, the alcove on the gate's side, the grindstone, windows
    door(p, t + 8, 10, 5, "spruce_door", "east")
    if west_door:
        door(p, t, 10, 5, "spruce_door", "west")
    else:
        pane(p, t, 10, 7)
        pane(p, t, 11, 7)
    p.clear(t + 7, 10, 7, t + 8, 12, 9)
    for z in (7, 8, 9):
        for y in range(10, 13):
            p.set(t + 6, y, z, "stone_bricks")
    for z in (6, 10):
        for y in range(10, 13):
            p.set(t + 7, y, z, "stone_bricks")
    p.set(t + 4, 10, z0 + 1, "grindstone", face="floor", facing="east")
    p.set(t + 2, 10, z0 + 1, "barrel", facing="up", open=False)
    lantern(p, t + 2, 11, z0 + 1)
    for z in (z0, z1):
        pane(p, x, 10, z)
        pane(p, x, 11, z)
    climb(p, [(xx, z1 - 1) for xx in range(t + 2, t + 6)], 10, SPRUCE)


def w_side(p):
    for x0, x1 in W_RUNS:
        w_run(p, x0, x1)
    w_flight(p)
    w_tower(p, W_TOWERS[0], None)
    w_tower(p, W_TOWERS[1], True)


# --- stage IV: the walk and the towers' crowns ---------------------------------------------------------------------------
def w_walk(p, x0, x1):
    """The walk on a run: paved, its breastwork corbelled out over the field with merlons on it, a low parapet on the
    village side with braziers on it (a gap where the stair comes up)."""
    for x in range(x0, x1 + 1):
        for z in range(W_IN, W_OUT + 1):
            if p.get(x, W_WALK, z) is None:
                p.set(x, W_WALK, z, W_PAVING)
        if p.get(x, W_WALK - 1, W_LIP) is not None:
            p.set(x, W_WALK, W_LIP, W_FACE)  # over a buttress, the pier carries the breastwork
        else:
            stairs(p, x, W_WALK, W_LIP, STONE_BRICK, "north", top=True)
        pier = p.get(x, W_WALK - 1, W_LIP) is not None
        banner = p.get(x, 7, W_LIP) is not None
        if banner:
            pass  # 35.15a: an arrow slit through the breastwork over each bay's banner
        else:
            p.set(x, W_WALK + 1, W_LIP, W_FACE)
        if (x - w_bays(x0, x1)[1][0]) % 2 == 0:  # merlons in step with the bays' banners
            if pier:  # over a buttress a taller merlon, chiseled, under a slab
                p.set(x, W_WALK + 2, W_LIP, "chiseled_stone_bricks")
                slab(p, x, W_WALK + 3, W_LIP, STONE_BRICK)
            else:
                p.set(x, W_WALK + 2, W_LIP, W_FACE)
        if x in W_BRAZIERS:
            p.set(x, W_WALK + 1, W_IN, "chiseled_stone_bricks")
            brazier(p, x, W_WALK + 2, W_IN)
        elif not W_FLIGHT + 9 <= x <= W_FLIGHT + 10:
            wall_block(p, x, W_WALK + 1, W_IN, "stone_brick_wall")
            if x in W_LAMPS:  # 35.15a: lanterns on the parapet between the braziers
                lantern(p, x, W_WALK + 2, W_IN)


def w_crown(p, t, outer_end):
    """A tower's top room (y 14-16, windows on three sides), its attic floor, a cornice under it, the hipped spruce
    roof, a banner to the field."""
    z0, z1 = W_TZ
    for x in range(t - 1, t + 10):
        for z in range(z0 - 1, z1 + 2):
            if x in (t - 1, t + 9) or z in (z0 - 1, z1 + 1):
                d = "south" if z == z0 - 1 else "north" if z == z1 + 1 else "east" if x == t - 1 else "west"
                stairs(p, x, 13, z, STONE_BRICK, d, top=True)
    for y in range(14, W_TOP + 1):
        for x in range(t, t + 9):
            for z in range(z0, z1 + 1):
                if x in (t, t + 8) or z in (z0, z1):
                    p.set(x, y, z, W_FACE)
                elif y == W_TOP:
                    p.set(x, y, z, "spruce_planks")
    for x, z in ((t + 4, z0), (t, 7), (t + 8, 7)):
        pane(p, x, 14, z)
        pane(p, x, 15, z)
    lantern(p, t + 4, 16, 7, hanging=True)
    w_banner(p, t + 4, 15, z1 + 1, "south")
    eaves(p, t - 1, t + 9, z0 - 1, z1 + 1, W_TOP)
    top = hip_roof(p, t - 1, t + 9, z0 - 1, z1 + 1, W_TOP + 1, SPRUCE)
    p.set(t + 4, top, 7, "spruce_planks")  # 35.15a: a watch lantern on the peak
    lantern(p, t + 4, top + 1, 7)


def stage_w4(b):
    for p in halves(b):
        for x0, x1 in W_RUNS:
            w_walk(p, x0, x1)
        w_crown(p, W_TOWERS[0], True)
        w_crown(p, W_TOWERS[1], False)


def great_wall():
    b = StagedBuild(WW, WH, WD)
    b.begin(1)
    stage_w1(b)
    b.begin(2)
    w_side(Mirror(b, False))
    w_plinth(b, 2)
    b.begin(3)
    w_side(Mirror(b, True))
    w_plinth(b, 3)
    b.begin(4)
    stage_w4(b)
    return b


def great_wall_stage(stage=None):
    """For the renders only: the Great Wall as it stands after a stage (whole by default)."""
    stages = great_wall().stage_builds()
    return stages[(stage or len(stages)) - 1]


def great_wall_piece(x0, x1, stage=None):
    """For the renders only: the Wall from x0 to x1 (a stretch close up)."""
    s = great_wall_stage(stage)
    out = Build(x1 - x0 + 1, WH, WD)
    out.blocks = {(x - x0, y, z): v for (x, y, z), v in s.blocks.items() if x0 <= x <= x1}
    out.nbt = {(x - x0, y, z): v for (x, y, z), v in getattr(s, "nbt", {}).items() if x0 <= x <= x1}
    return out


def great_wall_eye(x_eye):
    """For the renders only: the Wall cropped evenly about column x_eye and padded to 32 high, so that the preview
    renderer (it orbits the middle of the lawn it adds: x and z halfway, y at (height + 1) / 2.6) at a tiny distance
    (DIST=0.3) stands on the walk at a player's eye: x_eye + 0.5, y 11.7, z 7.5."""
    k = min(x_eye, WW - 1 - x_eye)
    s = great_wall_stage()
    out = Build(2 * k + 1, 32, WD)
    out.blocks = {(x - x_eye + k, y, z): v for (x, y, z), v in s.blocks.items() if abs(x - x_eye) <= k}
    out.nbt = {(x - x_eye + k, y, z): v for (x, y, z), v in getattr(s, "nbt", {}).items() if abs(x - x_eye) <= k}
    return out


# --- the Hanging Gardens (35.19) -----------------------------------------------------------------------------------------
# The Hanging Gardens, 47 x 33 x 44: a stepped garden mountain of four terraces on a 39 x 39 foot (x 1-39, z 4-42), the
# falls' pool beside it on the east (x 40-46) and the first ramp along its front (z 1-3). Design on paper:
# - Four terraces, each 6 high and 4 in from the one below: I 39 across (y 0-6), II 31 (6-12), III 23 (12-18), IV, the
#   crown, 15 (18-24). Each is faced all round with an arcade: 3-wide arches between cut sandstone columns (chiseled
#   capitals, upside-down stairs for the arch heads), 3-wide piers at the corners, a terracotta band with a chiseled
#   keystone over each arch, a cut sandstone coping. Behind the arcade a gallery 3 deep runs all round under the ledge's
#   soil (the walk of each level, lit by glow berries hanging in the arch heads and lanterns at its corners); its back
#   wall is the next terrace's face. Inside the first terrace the Cistern Vaults: a hall 29 x 29 of columns with vault
#   springers round a pool and its chain pump, lanterns between the columns, a doorway from the gallery on every side.
#   The upper terraces' cores are earth.
# - Each ledge (4 wide) is a garden on the gallery's roof: a hedge of azalea and oak leaves on the coping, two rows of
#   beds, a path along the arcade above. The beds: fields of wheat, carrots and beetroots between water channels, sweet
#   berry beds, glow berry arbors (oak posts and slabs, the berries hanging under them), flower beds with azalea bushes,
#   beehives. On each level's corners an oak, a flowering azalea tree, a farm corner (a composter and a chest: four
#   village farms) or a lamp-lit landing. Vines hang down the columns and over the arches; a lantern on the coping at
#   every corner and over the middle column of each side.
# - A ramp spirals up: along the first terrace's front from the ground, then up the second terrace's east face, the
#   third's back and the crown's west face, each a stair 2 wide on a solid body with a balustrade and a landing.
# - The crown: a cistern 7 x 7 in the middle (two courses high, water two deep, a fountain on a pillar in it, lanterns on
#   its corners), a walk round it, an oak and two flowering azalea trees, beehives, a glow berry arbor, a farm corner,
#   flowers and berries. The cistern pours east down a channel to the crown's edge: the falls drop down the east side
#   from basin to basin (one on each ledge, its lip notched in the coping) into a pool at the foot, with lily pads. The
#   arcade behind the falls is blind (mossy niches).
# - Palette (60/30/10): smooth and cut sandstone; terracotta trim; oak; moss, grass and leaves. Lanterns.
# - Stages, each ending tidy: I the first terrace, the Cistern Vaults, the first ramp and the second terrace's floor; II
#   the second terrace, its ramp and the third's floor; III the third the same; IV the crown, its garden and cistern,
#   the last ramp and the falls (the basins' and the pool's water).
HW, HH, HD = 47, 33, 44
H_OX, H_OZ = 1, 4  # the first terrace's north-west corner
H_C = (20, 23)  # the middle (x, z)
H_FALLS = (22, 24)  # the falls' z (they run down the east side)
H_FALLS_PIER = (20, 26)  # the arcade's blind bays behind the falls (z)
H_POOL = (40, 46, 18, 28)  # the pool with its rim: x from, x to, z from, z to
H_WALL = "smooth_sandstone"
H_COL = "cut_sandstone"
H_CAP = "chiseled_sandstone"
H_BAND = "terracotta"
H_SIDES = ("N", "E", "S", "W")
H_OUT = {"N": "north", "E": "east", "S": "south", "W": "west"}
H_CROPS = (("wheat", 7), ("carrots", 7), ("beetroots", 3))
H_FLOWERS = ["poppy", "allium", "azure_bluet", "oxeye_daisy", "cornflower", "red_tulip", "white_tulip", "pink_tulip",
             "lily_of_the_valley", "dandelion", "flowering_azalea", "azalea", "fern", "short_grass", None]
H_HEDGE = Mix((4, "azalea_leaves"), (3, "flowering_azalea_leaves"), (1, "oak_leaves"), (2, "moss_carpet"), seed=61)
H_CANOPY = {"oak": Mix((1, "oak_leaves"),), "azalea": Mix((3, "azalea_leaves"), (2, "flowering_azalea_leaves"), seed=62)}
# What each ledge's sides hold between its corners (t from, t to, kind), and its corners. A ramp's footing is paved; a
# "cross" is where a ramp's landing steps onto the ledge (no hedge there).
H_LEDGES = {
    0: {"N": [(5, 33, "field"), (34, 35, "cross")],
        "E": [(8, 10, "path"), (11, 20, "ramp"), (21, 25, "basin"), (26, 38, "berries")],
        "S": [(5, 35, "field")],
        "W": [(8, 22, "flowers"), (23, 38, "berries")]},
    1: {"N": [(9, 31, "arbor")],
        "E": [(12, 16, "flowers"), (17, 19, "cross"), (20, 20, "flowers"), (21, 25, "basin"), (26, 34, "berries")],
        "S": [(9, 18, "field"), (19, 28, "ramp"), (29, 31, "path")],
        "W": [(12, 34, "field")]},
    2: {"N": [(13, 27, "field")],
        "E": [(16, 20, "hives"), (21, 25, "basin"), (26, 30, "flowers")],
        "S": [(13, 19, "field"), (20, 22, "cross"), (23, 27, "berries")],
        "W": [(16, 19, "flowers"), (20, 29, "ramp"), (30, 30, "path")]},
}
H_CORNERS = {
    0: {("N", "W"): "station", ("N", "E"): "hub", ("S", "E"): "oak", ("S", "W"): "azalea"},
    1: {("N", "W"): "azalea", ("N", "E"): "oak", ("S", "E"): "hub", ("S", "W"): "station"},
    2: {("N", "W"): "oak", ("N", "E"): "azalea", ("S", "E"): "station", ("S", "W"): "hub"},
}
# The ramps: (the terrace whose face it climbs, the side, the first stair's t, the way it climbs along t, its stairs,
# its landing's length). The stairs and the landing are against the face (e -1 and -2), the balustrade at e -3.
H_RAMPS = {0: (0, "N", 27, 1, 7, 3), 1: (1, "E", 11, 1, 6, 3), 2: (2, "S", 28, -1, 6, 3), 3: (3, "W", 29, -1, 6, 3)}
# Where a terrace's arcade is blind (t ranges): behind a ramp and behind the falls.
H_BLIND = {(0, "N"): [(25, 37)], (1, "E"): [(11, 19)], (2, "S"): [(20, 28)], (3, "W"): [(21, 29)]}


def h_box(k):
    """Terrace k's (0-3) footprint: x from, x to, z from, z to."""
    return H_OX + 4 * k, H_OX + 38 - 4 * k, H_OZ + 4 * k, H_OZ + 38 - 4 * k


def h_cell(k, side, e, t):
    """The cell e in from terrace k's face on {@code side} (e 0 is the face, -1 just outside it), at t along it (x on
    the north and south faces, z on the west and east)."""
    x0, x1, z0, z1 = h_box(k)
    return {"N": (t, z0 + e), "S": (t, z1 - e), "W": (x0 + e, t), "E": (x1 - e, t)}[side]


def h_run(k, side):
    x0, x1, z0, z1 = h_box(k)
    return (x0, x1) if side in ("N", "S") else (z0, z1)


def h_along(side, dt):
    """The way along a face that t grows (dt 1) or shrinks (dt -1)."""
    return ({"N": "east", "S": "east", "W": "south", "E": "south"} if dt > 0 else
            {"N": "west", "S": "west", "W": "north", "E": "north"})[side]


def h_inward(side):
    return OPP[H_OUT[side]]


def h_part(k, side, t):
    """What the arcade is at t: ("pier",), ("col",) or ("bay", its number, 0-2 across it)."""
    a0, a1 = h_run(k, side)
    u, n = t - a0, a1 - a0 + 1
    if u < 3 or u > n - 4:
        return ("pier",)
    if (u - 3) % 4 == 0:
        return ("col",)
    return ("bay", (u - 4) // 4, (u - 4) % 4)


def h_blind(k, side, t):
    """Whether the bay at t is blind: behind a ramp, or behind the falls."""
    part = h_part(k, side, t)
    if part[0] != "bay":
        return False
    a0 = h_run(k, side)[0]
    lo = a0 + 4 + 4 * part[1]
    spans = list(H_BLIND.get((k, side), [])) + ([H_FALLS_PIER] if side == "E" else [])
    return any(lo <= s1 and lo + 2 >= s0 for s0, s1 in spans)


def h_hash(*key):
    return zlib.crc32(",".join(map(str, key)).encode())


def h_leaf(b, x, y, z, name):
    if 0 <= x < HW and 0 <= y < HH and 0 <= z < HD and b.get(x, y, z) is None:
        if name.endswith("_leaves"):
            b.set(x, y, z, name, distance=1, persistent=True, waterlogged=False)
        else:
            b.set(x, y, z, name)


def h_hedge(b, x, y, z):
    h_leaf(b, x, y, z, H_HEDGE.at(x, y, z))


def h_tree(b, x, y, z, kind):
    """An oak (a trunk of 4) or a flowering azalea tree (a trunk of 3, a wide flat crown) standing on the soil at y - 1.
    Leaves only go where nothing is drawn yet."""
    trunk = 4 if kind == "oak" else 3
    for dy in range(trunk):
        log(b, x, y + dy, z, "oak_log")
    leaves = H_CANOPY[kind]
    top = y + trunk - 1
    layers = ([(top - 1, 2), (top, 2), (top + 1, 1), (top + 2, 0)] if kind == "oak" else
              [(top - 1, 2), (top, 2), (top + 1, 1)])
    for ly, r in layers:
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if r and abs(dx) == r and abs(dz) == r and (r == 1 or h_hash(x, z, dx, dz, ly) % 3):
                    continue  # rounded corners, ragged on the wide layers
                if (dx, dz) != (0, 0) or ly > top:
                    h_leaf(b, x + dx, ly, z + dz, leaves.at(x + dx, ly, z + dz))


def h_vine(b, x, y, z, side):
    """A vine just outside a face on {@code side}, clinging to it."""
    if 0 <= x < HW and 0 <= z < HD and b.get(x, y, z) is None:
        face = h_inward(side)
        b.set(x, y, z, "vine", **{d: d == face for d in ("north", "south", "east", "west")}, up=False)


def h_glow(b, x, y, z):
    b.set(x, y, z, "cave_vines", age=25, berries=True)


def h_arch_head(b, k, side, t, y, part):
    """The arch's head at y over bay cell {@code part}: upside-down stairs leaning on the columns, open in the middle."""
    x, z = h_cell(k, side, 0, t)
    if part[2] == 1:
        return
    stairs(b, x, y, z, "smooth_sandstone_stairs", h_along(side, -1 if part[2] == 0 else 1), top=True)


# --- the terraces' arcades, galleries and cores ----------------------------------------------------------------------------
def h_face(b, k, side):
    """Terrace k's arcade on {@code side}, its gallery's ceiling and back wall (the next terrace's foot), the coping."""
    fy = 6 * k
    a0, a1 = h_run(k, side)
    for t in range(a0, a1 + 1):
        part = h_part(k, side, t)
        x, z = h_cell(k, side, 0, t)
        if k == 0:
            b.set(x, 0, z, H_COL if part[0] != "bay" else "smooth_sandstone")
        if part[0] == "pier":
            for y in range(fy + 1, fy + 5):
                b.set(x, y, z, H_COL if t in (a0, a1) else H_WALL)
        elif part[0] == "col":
            for y in range(fy + 1, fy + 4):
                b.set(x, y, z, H_COL)
            b.set(x, fy + 4, z, H_CAP)
        else:
            h_arch_head(b, k, side, t, fy + 4, part)
            if h_blind(k, side, t):
                nx, nz = h_cell(k, side, 1, t)  # a niche one deep: mossy behind the falls
                wet = side == "E" and H_FALLS_PIER[0] <= t <= H_FALLS_PIER[1]
                for y in range(fy + 1, fy + 5):
                    b.set(nx, y, nz, "moss_block" if wet and y < fy + 3 else H_WALL)
        b.set(x, fy + 5, z, H_BAND)
        if not (k < 3 and side == "E" and H_FALLS[0] <= t <= H_FALLS[1]):
            b.set(x, fy + 6, z, H_COL)  # the coping (notched where a basin's lip spills, filled by the falls)
        # the gallery's ceiling (its floor is the terrace's floor) and its back wall
        for e in (1, 2, 3):
            gx, gz = h_cell(k, side, e, t)
            b.set(gx, fy + 5, gz, H_WALL)
        if a0 + 4 <= t <= a1 - 4:
            wx, wz = h_cell(k, side, 4, t)
            for y in range(fy + 1, fy + 6):
                b.set(wx, y, wz, H_WALL)
    # the corners' gallery ceiling
    for ea in (1, 2, 3):
        for eb in (1, 2, 3):
            for sa in ("N", "S"):
                for sb in ("W", "E"):
                    x, z = h_corner_cell(k, (sa, sb), ea, eb)
                    b.set(x, fy + 5, z, H_WALL)


def h_corner_cell(k, corner, ea, eb):
    """The cell ea in from the north or south face and eb in from the west or east face, in that corner."""
    x0, x1, z0, z1 = h_box(k)
    sa, sb = corner
    return (x0 + eb if sb == "W" else x1 - eb), (z0 + ea if sa == "N" else z1 - ea)


def h_glow_and_lights(b, k):
    """Glow berries in the open arches' heads (two in three) and lanterns hung in the gallery's corners and halfway along
    each side."""
    fy = 6 * k
    for side in H_SIDES:
        a0, a1 = h_run(k, side)
        for t in range(a0, a1 + 1):
            part = h_part(k, side, t)
            if part[0] == "bay" and part[2] == 1 and not h_blind(k, side, t) and h_hash(k, side, t) % 3:
                x, z = h_cell(k, side, 0, t)
                h_glow(b, x, fy + 4, z)
        x, z = h_cell(k, side, 2, (a0 + a1) // 2)
        lantern(b, x, fy + 4, z, hanging=True)
    for corner in (("N", "W"), ("N", "E"), ("S", "W"), ("S", "E")):
        x, z = h_corner_cell(k, corner, 2, 2)
        lantern(b, x, fy + 4, z, hanging=True)


def h_vines(b, k):
    """Vines down terrace k's face: long strands on the columns and piers, short ones over the arches, none behind the
    falls; each clings to the coping, the band and the column under it."""
    fy = 6 * k
    for side in H_SIDES:
        a0, a1 = h_run(k, side)
        for t in range(a0, a1 + 1):
            if side == "E" and H_FALLS_PIER[0] - 1 <= t <= H_FALLS_PIER[1] + 1:
                continue
            part = h_part(k, side, t)
            r = h_hash("vine", k, side, t) % 10
            if part[0] == "bay":
                n = (0, 0, 0, 0, 0, 1, 1, 2, 2, 2)[r]
            else:
                n = (0, 0, 0, 2, 3, 3, 4, 4, 5, 5)[r]
            x, z = h_cell(k, side, -1, t)
            for y in range(fy + 6, fy + 6 - n, -1):
                if b.get(x, y, z) is not None:
                    break
                h_vine(b, x, y, z, side)


def h_floor(b, k):
    """Terrace k's floor (y 6k): smooth sandstone over its whole foot, the paving of its gallery and, until the terrace
    goes up, a tidy plaza on the one below, its setting-out inlaid: cut sandstone where the arcade will stand, terracotta
    where the gallery's back wall will."""
    x0, x1, z0, z1 = h_box(k)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = min(x - x0, x1 - x, z - z0, z1 - z)
            if b.get(x, 6 * k, z) is None:
                b.set(x, 6 * k, z, H_COL if edge == 0 else "terracotta" if edge == 4 else "smooth_sandstone")


def h_core(b, k):
    """An upper terrace's core inside its gallery's back wall: earth."""
    x0, x1, z0, z1 = h_box(k)
    for x in range(x0 + 5, x1 - 4):
        for z in range(z0 + 5, z1 - 4):
            for y in range(6 * k + 1, 6 * k + 6):
                b.set(x, y, z, "dirt")


def h_terrace(b, k):
    for side in H_SIDES:
        h_face(b, k, side)
    h_glow_and_lights(b, k)
    if k > 0:
        h_core(b, k)


# --- the Cistern Vaults ----------------------------------------------------------------------------------------------------
H_VAULT_COLS = (-12, -8, -4, 4, 8, 12)  # the columns' x and z from the middle


def h_vaults(b):
    """The hall inside the first terrace (x 6-34, z 9-37): a floor ruled in terracotta on the columns' lines, 36 columns
    with chiseled capitals and springers under a flat vault, a pool in the middle (7 x 7, a cut sandstone kerb) with a
    chain pump hanging into it, lanterns hung between the columns and a doorway from the gallery on every side."""
    cx, cz = H_C
    cols = [(cx + dx, cz + dz) for dx in H_VAULT_COLS for dz in H_VAULT_COLS]
    for x in range(6, 35):
        for z in range(9, 38):
            line = (x - cx) in H_VAULT_COLS or (z - cz) in H_VAULT_COLS
            b.set(x, 0, z, "terracotta" if line and not (abs(x - cx) <= 3 and abs(z - cz) <= 3) else "smooth_sandstone")
            b.set(x, 5, z, H_WALL)
    for x, z in cols:
        for y in range(1, 4):
            b.set(x, y, z, H_COL)
        b.set(x, 4, z, H_CAP)
        for d, (dx, dz) in DIRS.items():
            stairs(b, x + dx, 4, z + dz, "smooth_sandstone_stairs", OPP[d], top=True)
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            x, z = cx + dx, cz + dz
            if max(abs(dx), abs(dz)) == 3:
                b.set(x, 1, z, H_COL)
            else:
                b.set(x, 1, z, "water", level=0)
    for y in (2, 3, 4):
        chain(b, cx, y, cz)
    for dx in (-2, 2):
        for dz in (-2, 2):
            lantern(b, cx + dx, 4, cz + dz, hanging=True)
    for dx in (-10, -6, 6, 10):
        for dz in (-10, -6, 6, 10):
            lantern(b, cx + dx, 4, cz + dz, hanging=True)
    for dx in (-14, 0, 14):
        for dz in (-14, 0, 14):
            if (dx, dz) != (0, 0) and b.get(cx + dx, 4, cz + dz) is None:
                lantern(b, cx + dx, 4, cz + dz, hanging=True)
    # the doorways through the gallery's back wall, arched like the arcade
    for side in H_SIDES:
        mid = H_C[0] if side in ("N", "S") else H_C[1]
        for t in (mid - 1, mid, mid + 1):
            x, z = h_cell(0, side, 4, t)
            for y in (1, 2, 3):
                b.clear(x, y, z, x, y, z)
            if t != mid:
                stairs(b, x, 4, z, "smooth_sandstone_stairs", h_along(side, -1 if t < mid else 1), top=True)
            else:
                b.clear(x, 4, z, x, 4, z)
                h_glow(b, x, 4, z)


# --- the ledges' gardens ------------------------------------------------------------------------------------------------
def h_channels(n):
    """Where a field n long crosses its water channels: every 8, so no farmland is more than 4 from water."""
    out = list(range(3, n, 8)) or [n // 2]
    if n - 1 - out[-1] > 4:
        out.append(n - 1)
    return out


def h_strip(b, k, side, t0, t1, kind):
    """One stretch of ledge k's beds between its corners: e 1 and 2 the beds, e 3 a path along the arcade, e 0 the
    coping with its hedge on it."""
    s = 6 * k + 6  # the soil's layer; plants stand at s + 1
    cells = lambda e, t: h_cell(k, side, e, t)  # noqa: E731
    water = {t0 + i for i in h_channels(t1 - t0 + 1)} if kind == "field" else set()
    seg = 0
    for i, t in enumerate(range(t0, t1 + 1)):
        x, z = cells(0, t)
        if kind != "cross" and not (kind == "basin" and H_FALLS[0] <= t <= H_FALLS[1]):
            h_hedge(b, x, s + 1, z)
        px, pz = cells(3, t)
        if kind in ("field", "berries", "flowers", "arbor", "hives", "path", "cross"):
            b.set(px, s, pz, "dirt_path")
        if kind in ("path", "cross"):
            for e in (1, 2):
                ex, ez = cells(e, t)
                b.set(ex, s, ez, "dirt_path")
        elif kind == "ramp":
            for e in (1, 2, 3):
                ex, ez = cells(e, t)
                b.set(ex, s, ez, "smooth_sandstone")
        elif kind == "basin":
            if t in (t0, t1):  # its rims: a cut sandstone kerb and a slab on it
                for e in (1, 2, 3):
                    ex, ez = cells(e, t)
                    b.set(ex, s, ez, H_COL)
                    slab(b, ex, s + 1, ez, "cut_sandstone_slab")
            # its water, and the notch in the coping its lip spills over, come with the falls (stage IV)
        elif kind == "field":
            if t in water:
                seg += 1
            crop, age = H_CROPS[(seg + k + H_SIDES.index(side)) % 3]
            for e in (1, 2):
                ex, ez = cells(e, t)
                if t in water:
                    b.set(ex, s, ez, "water", level=0)
                else:
                    b.set(ex, s, ez, "farmland", moisture=7)
                    b.set(ex, s + 1, ez, crop, age=age)
        else:
            for e in (1, 2):
                ex, ez = cells(e, t)
                b.set(ex, s, ez, "grass_block", snowy=False)
                r = h_hash(kind, k, side, t, e)
                if kind == "berries":
                    if r % 5:
                        b.set(ex, s + 1, ez, "sweet_berry_bush", age=3)
                    else:
                        b.set(ex, s + 1, ez, "fern")
                elif kind == "flowers":
                    p = H_FLOWERS[r % len(H_FLOWERS)]
                    if p:
                        b.set(ex, s + 1, ez, p)
                elif kind == "hives":
                    if e == 1 and i % 2 == 0:
                        b.set(ex, s + 1, ez, "beehive", facing=h_inward(side), honey_level=0)
                    else:
                        b.set(ex, s + 1, ez, H_FLOWERS[r % 10])
                elif kind == "arbor":
                    h_arbor_cell(b, ex, s, ez, (t - t0) % 4 == 0, r, i % 8 == 4 and e == 1, beam=e == 1 or (t - t0) % 2 == 0)


def h_arbor_cell(b, x, s, z, post, r, light, beam=True):
    """A cell of a glow berry arbor: an oak post at every fourth, a beam of oak slabs along one side and slats across from
    it at every other cell, the berries hanging under them (a lantern now and then), ferns, moss and flowers under."""
    if post:
        fence(b, x, s + 1, z, "oak_fence")
        fence(b, x, s + 2, z, "oak_fence")
    else:
        if not beam:
            pass
        elif light:
            lantern(b, x, s + 2, z, hanging=True)
        elif r % 10 < 7:
            h_glow(b, x, s + 2, z)
        # no moss carpet: under the beam two blocks up, a villager stepping onto one hits its head (B173)
        b.set(x, s + 1, z, ("fern", "short_grass", "short_grass", "lily_of_the_valley", "fern")[r % 5])
    if beam:
        slab(b, x, s + 3, z, "oak_slab")


def h_corner(b, k, corner, kind):
    """A ledge's corner square: the coping and its hedge (a lantern on the very corner), the path round the inside, and
    in the 2 x 2 left: a tree, a farm corner (a composter and a chest), or a landing lit by a lamp."""
    s = 6 * k + 6
    for ea in range(4):
        for eb in range(4):
            x, z = h_corner_cell(k, corner, ea, eb)
            if ea == 0 or eb == 0:
                if ea == 0 and eb == 0:
                    lantern(b, x, s + 1, z)
                elif not (k == 0 and corner == ("N", "E") and ea == 0 and eb == 3):  # the first ramp's landing
                    h_hedge(b, x, s + 1, z)
                continue
            if kind == "hub" or ea == 3 or eb == 3:
                b.set(x, s, z, "dirt_path")
            else:
                b.set(x, s, z, "grass_block", snowy=False)
    sa, sb = corner
    toward_a = OPP[H_OUT[sa]]  # into the terrace from the north or south face
    toward_b = OPP[H_OUT[sb]]
    x11, z11 = h_corner_cell(k, corner, 1, 1)
    x12, z12 = h_corner_cell(k, corner, 1, 2)
    x21, z21 = h_corner_cell(k, corner, 2, 1)
    x22, z22 = h_corner_cell(k, corner, 2, 2)
    if kind in ("oak", "azalea"):
        h_tree(b, x11, s + 1, z11, kind)
        b.set(x12, s + 1, z12, "fern")
        b.set(x21, s + 1, z21, "moss_carpet")
        b.set(x22, s + 1, z22, H_FLOWERS[h_hash(k, corner) % 10])
    elif kind == "station":
        b.set(x11, s + 1, z11, "flowering_azalea")
        b.set(x12, s + 1, z12, "composter", level=0)
        b.set(x21, s + 1, z21, "chest", facing=toward_a, type="single", waterlogged=False)
        if k == 2:
            b.set(x22, s + 1, z22, "beehive", facing=toward_b, honey_level=0)
        else:
            lamp_post(b, x22, s + 1, z22, post="oak_fence", height=1)
    else:
        lamp_post(b, x11, s + 1, z11, post="oak_fence", height=2)


def h_ledge(b, k):
    for side, strips in H_LEDGES[k].items():
        for t0, t1, kind in strips:
            h_strip(b, k, side, t0, t1, kind)
    for corner, kind in H_CORNERS[k].items():
        h_corner(b, k, corner, kind)
    # a lantern on the coping over each side's middle column (not over the falls)
    for side in ("N", "S", "W"):
        a0, a1 = h_run(k, side)
        x, z = h_cell(k, side, 0, (a0 + a1) // 2)
        if b.get(x, 6 * k + 7, z) is not None and name_of(b.get(x, 6 * k + 7, z)).endswith(("leaves", "carpet")):
            b.blocks.pop((x, 6 * k + 7, z))
            lantern(b, x, 6 * k + 7, z)


# --- the ramps --------------------------------------------------------------------------------------------------------------
def h_ramp(b, n):
    """Ramp n: up terrace k's face from the ground or the ledge below to its top, a stair two wide on a solid body
    against the face, a balustrade of sandstone wall on the outer side, a landing, and a lamp at its foot."""
    k, side, t, dt, steps, land = H_RAMPS[n]
    base = 6 * k if k else -1  # the soil it stands on (the ground under the first)
    top = 6 * k + 6
    climb_dir = h_along(side, dt)
    seq = [(t + i * dt, base + 1 + i, "stair") for i in range(steps)]
    seq += [(t + (steps + j) * dt, top, "landing") for j in range(land)]
    end = t + (steps + land) * dt
    for tt, y, what in seq:
        for e in (-1, -2):
            x, z = h_cell(k, side, e, tt)
            for yy in range(base + 1, y):
                b.set(x, yy, z, H_WALL)
            if what == "stair":
                stairs(b, x, y, z, "smooth_sandstone_stairs", climb_dir)
            else:
                b.set(x, y, z, H_COL)
        # the outer face: cut sandstone pilasters every fourth, recesses between them under the balustrade's
        # terracotta base, vines hanging in them
        x, z = h_cell(k, side, -3, tt)
        i = (tt - t) * dt
        if i % 4 == 0:
            for yy in range(base + 1, y):
                b.set(x, yy, z, H_COL)
        elif y - base > 2 and h_hash("ramp vine", n, tt) % 3:
            inner = h_inward(side)
            for yy in range(y - 1, max(base, y - 4), -1):
                b.set(x, yy, z, "vine", **{d: d == inner for d in ("north", "south", "east", "west")}, up=False)
        b.set(x, y, z, H_BAND)
        wall_block(b, x, y + 1, z, "sandstone_wall")
    for e in (-1, -2, -3):  # the landing's end
        x, z = h_cell(k, side, e, end)
        for yy in range(base + 1, top):
            b.set(x, yy, z, H_COL if e == -3 else H_WALL)
        b.set(x, top, z, H_BAND)
        wall_block(b, x, top + 1, z, "sandstone_wall")
    x, z = h_cell(k, side, -3, end)
    lantern(b, x, top + 2, z)
    x, z = h_cell(k, side, -3, t - dt)  # the foot: a post with a lantern
    if b.get(x, base + 1, z) is None or k == 0:
        wall_block(b, x, base + 1, z, "sandstone_wall")
        lantern(b, x, base + 2, z)


# --- the first terrace's foot ---------------------------------------------------------------------------------------------
def h_foot(b):
    """A step of smooth sandstone stairs round the first terrace (not where the pool comes), its corners full blocks;
    beds of flowers and azaleas in front of the front's columns west of the ramp."""
    x0, x1, z0, z1 = h_box(0)
    for side in H_SIDES:
        a0, a1 = h_run(0, side)
        for t in range(a0, a1 + 1):
            x, z = h_cell(0, side, -1, t)
            if side == "E" and H_POOL[2] <= t <= H_POOL[3]:
                continue
            if b.get(x, 0, z) is None:
                stairs(b, x, 0, z, "smooth_sandstone_stairs", h_inward(side))
    for x, z in ((x0 - 1, z0 - 1), (x1 + 1, z0 - 1), (x0 - 1, z1 + 1), (x1 + 1, z1 + 1)):
        b.set(x, 0, z, H_COL)
    for t in range(x0 + 1, 26):
        part = h_part(0, "N", t)
        if part[0] == "bay" and part[2] == 1:
            continue  # a way in to each arch
        for z in (1, 2):
            r = h_hash("bed", t, z)
            b.set(t, 0, z, "grass_block", snowy=False)
            plant = "flowering_azalea" if part[0] == "col" and z == 2 else H_FLOWERS[r % len(H_FLOWERS)]
            if plant:
                b.set(t, 1, z, plant)


# --- the crown ------------------------------------------------------------------------------------------------------------
def h_crown(b):
    """The crown's garden on the fourth terrace (x 13-27, z 16-30, its soil at y 24): the cistern, its walk, its
    channel east to the edge, trees, hives, an arbor of glow berries, a farm corner with its field, flowers and berries."""
    x0, x1, z0, z1 = h_box(3)
    s = 24
    cx, cz = H_C
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            dx, dz = x - cx, z - cz
            if max(abs(dx), abs(dz)) <= 4:
                b.set(x, s, z, "smooth_sandstone")  # the walk, the cistern's rim and floor
            elif x <= x0 + 3 and abs(dz) <= 1:
                b.set(x, s, z, "dirt_path")  # from the last ramp's landing
            elif x >= cx + 4 and abs(dz) <= 2:
                b.set(x, s, z, "smooth_sandstone")  # the channel's bed
            else:
                b.set(x, s, z, "grass_block", snowy=False)
    # the hedge on the coping, open at the landing and the channel; lanterns on the corners
    for x, z in outline({(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)}):
        if (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            lantern(b, x, s + 1, z)
        elif not (x == x0 and abs(z - cz) <= 1) and not (x == x1 and abs(z - cz) <= 2):
            h_hedge(b, x, s + 1, z)
    # the cistern: two courses, chiseled corners with lanterns, a slab cap; its mouth east into the channel
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            x, z = cx + dx, cz + dz
            if max(abs(dx), abs(dz)) != 3:
                continue
            corner = abs(dx) == 3 and abs(dz) == 3
            if not (dx == 3 and abs(dz) <= 1):
                b.set(x, s + 1, z, H_COL)
            b.set(x, s + 2, z, H_CAP if corner else H_WALL)
            if corner:
                b.set(x, s + 3, z, H_CAP)
                lantern(b, x, s + 4, z)
            else:
                slab(b, x, s + 3, z, "smooth_sandstone_slab")
    # the fountain's pillar in the middle
    for y in (s + 1, s + 2):
        b.set(cx, y, cz, H_WALL)
    b.set(cx, s + 3, cz, H_CAP)
    # the channel's kerbs
    for x in range(cx + 4, x1 + 1):
        for dz in (-2, 2):
            slab(b, x, s + 1, cz + dz, "cut_sandstone_slab")
    # trees, hives and the farm corner
    h_tree(b, x0 + 1, s + 1, z0 + 1, "azalea")
    h_tree(b, x1 - 1, s + 1, z0 + 1, "oak")
    h_tree(b, x1 - 1, s + 1, z1 - 1, "azalea")
    b.set(x0 + 1, s + 1, z1 - 1, "beehive", facing="east", honey_level=0)
    b.set(x0 + 2, s + 1, z1 - 1, "beehive", facing="north", honey_level=0)
    b.set(x0 + 1, s + 1, z1 - 2, "flowering_azalea")
    b.set(cx - 2, s + 1, z0 + 1, "composter", level=0)
    b.set(cx - 1, s + 1, z0 + 1, "chest", facing="south", type="single", waterlogged=False)
    lamp_post(b, cx, s + 1, z0 + 1, post="oak_fence", height=1)
    # the crown's field by its farm corner (35.20: the fourth village farm): wheat and carrots either side of a channel
    for x in range(cx + 1, x1 - 1):
        for z in (z0 + 1, z0 + 2):
            if x == cx + 3:
                b.set(x, s, z, "water", level=0)
            else:
                b.set(x, s, z, "farmland", moisture=7)
                crop, age = H_CROPS[0 if x < cx + 3 else 1]
                b.set(x, s + 1, z, crop, age=age)
    # the arbor over the south bed, the berries and flowers in the rest
    for x in range(cx - 4, cx + 5):
        for z in (z1 - 2, z1 - 1):
            r = h_hash("crown arbor", x, z)
            h_arbor_cell(b, x, s, z, (x - cx) % 4 == 0, r, x == cx and z == z1 - 1, beam=z == z1 - 2 or (x - cx) % 2 == 0)
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if b.get(x, s, z) == ("minecraft:grass_block", (("snowy", "false"),)) and b.get(x, s + 1, z) is None:
                r = h_hash("crown", x, z)
                if z <= z0 + 3 and x >= cx + 1:
                    b.set(x, s + 1, z, "sweet_berry_bush", age=3)
                else:
                    p = H_FLOWERS[r % len(H_FLOWERS)]
                    if p:
                        b.set(x, s + 1, z, p)


def h_falls(b):
    """The water: the cistern two deep, its fountain, the channel, the basins on the three ledges with the notches in
    the coping their lips spill over, and the pool at the foot (cut sandstone kerb, lily pads)."""
    cx, cz = H_C
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if (dx, dz) != (0, 0):
                for y in (25, 26):
                    b.set(cx + dx, y, cz + dz, "water", level=0)
    b.set(cx, 28, cz, "water", level=0)  # the fountain: it spills off the pillar's top into the cistern
    x1 = h_box(3)[1]
    for x in range(cx + 3, x1 + 1):
        for z in range(H_FALLS[0], H_FALLS[1] + 1):
            b.set(x, 25, z, "water", level=0)
    for k in range(3):
        for e in range(4):
            for t in range(H_FALLS[0], H_FALLS[1] + 1):
                x, z = h_cell(k, "E", e, t)
                b.set(x, 6 * k + 6, z, "water", level=0)
    px0, px1, pz0, pz1 = H_POOL
    for x in range(px0, px1 + 1):
        for z in range(pz0, pz1 + 1):
            if x == px1 or z in (pz0, pz1):
                b.set(x, 0, z, H_COL)
            else:
                b.set(x, 0, z, "water", level=0)
                if h_hash("lily", x, z) % 5 == 0 and not (x == px0 and H_FALLS[0] <= z <= H_FALLS[1]):
                    b.set(x, 1, z, "lily_pad")
    for x, z in ((px1, pz0), (px1, pz1)):
        lantern(b, x, 1, z)


def h_low_moss(b):
    """Takes up this stage's moss carpets under something two blocks over them (a ledge, a crown): on one a villager
    has 1.94 of room, and stands there with its head in the green for good (B197)."""
    for (x, y, z), (name, _) in list(b.blocks.items()):
        if name == "minecraft:moss_carpet" and (x, y, z) not in b._frozen and b.get(x, y + 1, z) is None \
                and b.get(x, y + 2, z) is not None:
            del b.blocks[(x, y, z)]


def hanging_gardens():
    b = StagedBuild(HW, HH, HD)
    b.begin(1)
    b.fill(H_OX, 0, H_OZ, H_OX + 38, 0, H_OZ + 38, "smooth_sandstone")
    h_terrace(b, 0)
    h_vaults(b)
    h_ramp(b, 0)
    h_ledge(b, 0)
    h_floor(b, 1)
    h_vines(b, 0)
    h_foot(b)
    h_low_moss(b)
    for k in (1, 2):
        b.begin(k + 1)
        h_ramp(b, k)
        h_terrace(b, k)
        h_ledge(b, k)
        h_floor(b, k + 1)
        h_vines(b, k)
        h_low_moss(b)
    b.begin(4)
    h_ramp(b, 3)
    h_terrace(b, 3)
    h_crown(b)
    h_vines(b, 3)
    h_falls(b)
    h_low_moss(b)
    return b


def hanging_gardens_stage(stage=None):
    """For the renders only: the Hanging Gardens as they stand after a stage (whole by default)."""
    stages = hanging_gardens().stage_builds()
    return stages[(stage or len(stages)) - 1]


def h_flow(b):
    """For the renders only: the water run as the game runs it, from each still block: into air beside it where it can
    fall (a level-1 block at the lip), straight down through air (falling water), stopping on water. Says so if any
    would run out flat (a lip with ground under it: the game would flood the garden) or land on anything but water."""
    still = [p for p, v in b.blocks.items() if v[0] == "minecraft:water"]
    problems = []
    for x, y, z in still:
        for dx, dz in DIRS.values():
            nx, nz = x + dx, z + dz
            if not (0 <= nx < b.w and 0 <= nz < b.d) or b.get(nx, y, nz) not in (None, ("minecraft:air", ())):
                continue
            if y > 0 and b.get(nx, y - 1, nz) in (None, ("minecraft:air", ())):
                b.set(nx, y, nz, "water", level=1)
                yy = y - 1
                while yy >= 0 and b.get(nx, yy, nz) in (None, ("minecraft:air", ())):
                    b.set(nx, yy, nz, "water", level=8)
                    yy -= 1
                if yy >= 0 and b.get(nx, yy, nz)[0] != "minecraft:water":
                    problems.append(f"falls from {x},{y},{z} land on {b.get(nx, yy, nz)[0]} at {nx},{yy},{nz}")
            elif y > 0:
                problems.append(f"water at {x},{y},{z} runs out flat to {nx},{y},{nz}")
    for p in problems:
        print("  FLOW", p)
    return b


def hanging_gardens_running(stage=None):
    """For the renders only: a stage with its water running (the falls)."""
    s = hanging_gardens_stage(stage)
    out = Build(HW, HH, HD)
    out.blocks = {p: v for p, v in s.blocks.items() if v[0] != "minecraft:air"}
    out.nbt = dict(getattr(s, "nbt", {}))
    return h_flow(out)


# --- the Colossus (35.21) ------------------------------------------------------------------------------------------------
# The Colossus, 29 x 47 x 29: the Founder's statue over the village's main road, which runs front (z = 0) to back through
# the arch. Design on paper (the figure drawn at a pixel a block, from the vanilla villager model, which is what makes it
# read as one from far off: the tall head, the long nose hanging to the chin, the unibrow, the robe):
# - The plinth, 13 high (y 0-12), stepped in three: A, 27 x 27 (x and z 1-27) and 7 high, on a skirt of deepslate brick
#   stairs, its stone bricks mossy and cracked low down, polished andesite pilasters every five or six blocks standing
#   one out, a cornice of upside-down stairs at y 6; B, 21 x 21 and 4 high, set back three (a terrace round it at y 7
#   with a stone-brick balustrade and nine braziers), a polished andesite band and cornice; C, the pedestal, 15 x 15 and
#   2 high, smooth stone over a polished andesite course.
# - The road arch through A, front to back: 5 wide (x 12-16) and 6 high (y 0-5, the crown 3 wide on haunches of stairs),
#   polished andesite jambs and voussoirs, a chiseled keystone; three lantern niches down each side of the passage; lamp
#   posts at both mouths. The Founder's plaque: five dark oak signs over the front arch on a dark oak panel framed in
#   polished andesite, on B's face above the terrace (35.22 writes them).
# - The figure, 34 high (y 13-46), facing the front (north) up the road: the robe from y 13 to the shoulders at 34 (11 x 9
#   at the hem, flaring with a course of stairs at y 19, then 9 x 7), polished diorite with smooth stone at the hem, the
#   belt (y 24) and down the front; two feet under the hem. The head 9 x 9 and 10 high (y 35-44, a block out from the
#   chest front and back, as the villager's is); the nose 3 x 4, two out, from y 37 down past the chin to 34; the unibrow
#   a polished deepslate ledge one out across both eyes (y 40); the eyes white with emerald pupils on the inner side
#   (y 39). The robe is 22 high, not the villager's 24, so the lantern stands above the head inside the 47. The left arm
#   folds across the chest, its hand on the chest (the villager's folded arms, half of them); the right arm rises clear
#   of the head and holds up the lantern: a cage of glass panes round three glowstone (y 43-45), on a copper plate, under
#   a cut copper cap at y 46 (the top). The cloak: cut copper hanging behind from the shoulders to the pedestal (a gap
#   behind the back, folds at the sides, three pleats, a collar), weathering green over the weeks.
#   The robe and head are shells (the builders lay the stone, not the inside).
# - Palette (60/30/10): stone bricks and deepslate below, polished diorite and smooth stone above (light going up), cut
#   copper; polished andesite trim; a glass type: panes. The arm and the head need scaffolding (35.6).
# - Stages, each ending tidy: I the Plinth and the Road Arch (all of it, the braziers, lamps and plaque); II the Robes
#   (the robe and the feet); III the Arms and the Cloak; IV the Head and the Lantern.
COW, COH, COD = 29, 47, 29
CO_C = 14  # the middle (x and z)
CO_ARCH = (12, 16)  # the road's arch (x)
CO_TOP = 46  # the lantern's cap
CO_HEM = 19  # the hem's last course, its flare
CO_BELT = 24
CO_NECK = 34  # the robe's top (the shoulders); the head is the 10 above it
CO_LAMP_X = (21, 23)  # the raised arm and its lantern (x), clear of the head
CO_FIGURE = "polished_diorite"
CO_TRIM = "smooth_stone"
CO_PIER = "polished_andesite"
CO_COPPER = "cut_copper"
CO_SKIRT = DEEPSLATE_BRICK
CO_FACE = Courses()
CO_UPPER = Mix((10, "stone_bricks"), (1, "cracked_stone_bricks"), seed=71)
CO_PLEATS = (11, 14, 17)  # the cloak's folds (x)
CO_PILASTERS = (1, 6, 11, 17, 22, 27)  # along each face of A (11 and 17 are the arch's jambs on the front and back)


def co_ring(x0, x1, z0, z1):
    return [(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1) if x in (x0, x1) or z in (z0, z1)]


def co_in_arch(x, y):
    """The road's passage: 5 wide to y 4, the crown 3 wide at y 5."""
    return CO_ARCH[0] <= x <= CO_ARCH[1] and y <= 4 or CO_ARCH[0] + 1 <= x <= CO_ARCH[1] - 1 and y == 5


def co_out(x, z, lo, hi):
    """Which way a cell on the ring lo..hi faces out (its side; a corner takes its z side)."""
    return "north" if z == lo else "south" if z == hi else "west" if x == lo else "east"


def co_plinth(b):
    # the skirt and A
    for x, z in co_ring(0, 28, 0, 28):
        if not CO_ARCH[0] <= x <= CO_ARCH[1]:
            stairs(b, x, 0, z, CO_SKIRT, OPP[co_out(x, z, 0, 28)])
    for y in range(7):
        for x in range(1, 28):
            for z in range(1, 28):
                if co_in_arch(x, y):
                    continue
                edge = x in (1, 27) or z in (1, 27)
                b.set(x, y, z, CO_FACE if edge or y == 6 else "stone_bricks")
    # the pilasters, one out on the skirt's line, the cornice over them
    for k in CO_PILASTERS:
        for x, z in ((k, 0), (k, 28), (0, k), (28, k)):
            for y in range(0, 6):
                b.set(x, y, z, CO_PIER)
    for x, z in co_ring(0, 28, 0, 28):
        stairs(b, x, 6, z, "stone_brick_stairs", co_out(x, z, 0, 28), top=True)
    # the arch: jambs, haunches, voussoirs and keystone at both mouths; lantern niches down the passage
    for z in (1, 27):
        for y in range(6):
            b.set(CO_ARCH[0] - 1, y, z, CO_PIER)
            b.set(CO_ARCH[1] + 1, y, z, CO_PIER)
        for x in range(CO_ARCH[0], CO_ARCH[1] + 1):
            b.set(x, 6, z, CO_PIER)
        b.set(CO_C, 6, z, "chiseled_stone_bricks")
    for z in range(1, 28):
        stairs(b, CO_ARCH[0], 5, z, "stone_brick_stairs", "west", top=True)
        stairs(b, CO_ARCH[1], 5, z, "stone_brick_stairs", "east", top=True)
    for z in (7, 14, 21):
        for x in (CO_ARCH[0] - 1, CO_ARCH[1] + 1):
            b.set(x, 2, z, "air")
            lantern(b, x, 1, z)
    # lamp posts at both mouths
    for z in (0, 28):
        for x in (9, 19):
            b.set(x, 0, z, "chiseled_stone_bricks")
            wall_block(b, x, 1, z, "stone_brick_wall")
            lantern(b, x, 2, z)
    # B, its band and cornice, and the terrace's balustrade and braziers
    for y in range(7, 11):
        for x in range(4, 25):
            for z in range(4, 25):
                edge = x in (4, 24) or z in (4, 24)
                corner = x in (4, 24) and z in (4, 24)
                b.set(x, y, z, CO_PIER if corner or (edge and y == 10) else CO_UPPER)
    for x, z in co_ring(3, 25, 3, 25):
        stairs(b, x, 10, z, "polished_andesite_stairs", co_out(x, z, 3, 25), top=True)
    braziers = {(1, 1), (27, 1), (1, 27), (27, 27), (1, 14), (27, 14), (14, 27), (7, 1), (21, 1)}
    for x, z in co_ring(1, 27, 1, 27):
        if (x, z) in braziers:
            b.set(x, 7, z, "campfire", facing="south", lit=True, signal_fire=False, waterlogged=False)
        else:
            wall_block(b, x, 7, z, "stone_brick_wall")
    # the Founder's plaque over the front arch: a dark oak panel framed in polished andesite, five signs on it
    for x in range(10, 19):
        for y in (7, 8, 9):
            b.set(x, y, 4, CO_PIER if x in (10, 18) or y != 8 else "dark_oak_planks")
    for x in range(11, 18):
        b.set(x, 8, 3, "dark_oak_wall_sign", facing="north", waterlogged=False)
    stairs(b, 10, 9, 3, "polished_andesite_stairs", "south", top=True)
    stairs(b, 18, 9, 3, "polished_andesite_stairs", "south", top=True)
    for x in range(11, 18):
        slab(b, x, 9, 3, "polished_andesite_slab", top=True)
    # C, the pedestal
    for x in range(7, 22):
        for z in range(7, 22):
            b.set(x, 11, z, CO_PIER)
            b.set(x, 12, z, CO_TRIM)
    for x, z in co_ring(7, 21, 7, 21):
        if x in (7, 21) and z in (7, 21):
            b.set(x, 12, z, "chiseled_stone_bricks")


def co_shell(b, x0, x1, z0, z1, y, solid, name):
    """One course of the figure: the whole rectangle if solid, else its outline."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if solid or x in (x0, x1) or z in (z0, z1):
                b.set(x, y, z, resolve(name, x, y, z) if not isinstance(name, str) else name)


def co_robe(b):
    for y in range(13, CO_NECK + 1):
        if y <= CO_HEM:
            x0, x1, z0, z1 = 9, 19, 10, 18
        else:
            x0, x1, z0, z1 = 10, 18, 11, 17
        name = CO_TRIM if y in (13, 14, CO_BELT) else CO_FIGURE
        co_shell(b, x0, x1, z0, z1, y, y in (13, CO_NECK), name)
        if y not in (13, 14, CO_BELT):
            b.set(CO_C, y, z0, CO_TRIM)  # the robe's front, closed down the middle
    # the flare: the hem's outer course slopes in under the body
    for x, z in co_ring(9, 19, 10, 18):
        stairs(b, x, CO_HEM, z, DIORITE, OPP[co_side(x, z, 9, 19, 10, 18)])
    # the feet
    for x in (11, 12, 16, 17):
        stairs(b, x, 13, 9, DIORITE, "south")


def co_side(x, z, x0, x1, z0, z1):
    return "north" if z == z0 else "south" if z == z1 else "west" if x == x0 else "east"


def co_arms(b):
    n = CO_NECK
    # the left arm (west): the shoulder, the upper arm down the side, the forearm across the chest, the hand on it
    for y in range(n - 7, n + 1):
        for z in range(12, 16):
            b.set(9, y, z, CO_FIGURE)
    for x in range(9, 15):
        for y in range(n - 8, n - 4):
            for z in range(8, 11):
                b.set(x, y, z, CO_FIGURE)
    for y in range(n - 8, n - 4):
        for z in range(8, 11):
            b.set(14, y, z, CO_TRIM)  # the sleeve's cuff
    for x in (15, 16):
        for y in range(n - 7, n - 4):
            for z in (9, 10):
                b.set(x, y, z, CO_TRIM)
    # the right arm (east): the shoulder, the elbow out, the arm rising clear of the head, the hand round the lantern's foot
    for y in range(n - 3, n + 1):
        for z in range(12, 16):
            b.set(19, y, z, CO_FIGURE)
    for y in range(n - 3, n):
        for z in range(12, 15):
            b.set(20, y, z, CO_FIGURE)
    for x in range(CO_LAMP_X[0], CO_LAMP_X[1] + 1):
        for z in range(12, 15):
            for y in range(n - 3, 41):
                b.set(x, y, z, CO_TRIM if y == 40 else CO_FIGURE)
            b.set(x, 41, z, CO_TRIM)
    # the cloak: cut copper from the shoulders down the back to the pedestal, a gap behind the back, folds at the sides
    for x in range(9, 20):
        for y in range(13, n + 1):
            b.set(x, y, 19, CO_COPPER)
    for x in range(10, 19):
        b.set(x, n, 18, CO_COPPER)
    for y in range(CO_HEM + 1, n):
        b.set(9, y, 18, CO_COPPER)
        b.set(19, y, 18, CO_COPPER)
    for x in range(9, 20):
        stairs(b, x, n + 1, 19, CUT_COPPER, "north")  # the collar
    # its folds: three pleats a block out, from under the shoulders to the hem, ending in a step
    for x in CO_PLEATS:
        for y in range(14, n - 2):
            b.set(x, y, 20, CO_COPPER)
        stairs(b, x, n - 2, 20, CUT_COPPER, "north")
        stairs(b, x, 13, 20, CUT_COPPER, "north")


def co_head(b):
    h0 = CO_NECK + 1  # the head's lowest course; it is 10 high, the villager's
    for y in range(h0, h0 + 10):
        co_shell(b, 10, 18, 10, 18, y, y in (h0, h0 + 9), CO_FIGURE)
    # the face, counted down from the top row as on the villager's texture: the unibrow on row 4, the eyes on row 5, the
    # nose rows 7 to 10 (hanging a row past the chin)
    top = h0 + 9
    for x in range(11, 18):
        slab(b, x, top - 4, 9, "polished_deepslate_slab", top=True)
    for x, name in ((11, "smooth_quartz"), (12, "emerald_block"), (16, "emerald_block"), (17, "smooth_quartz")):
        b.set(x, top - 5, 10, name)
    for x in range(13, 16):
        for y in range(top - 10, top - 6):
            for z in (8, 9):
                b.set(x, y, z, CO_FIGURE)
    # the lantern: a copper plate on the hand, a cage of panes round glowstone, a copper cap at the top
    lo, hi = CO_LAMP_X
    for x in range(lo, hi + 1):
        for z in range(12, 15):
            b.set(x, 42, z, CO_COPPER)
            slab(b, x, CO_TOP, z, CUT_COPPER)
    for y in (43, 44, 45):
        b.set(lo + 1, y, 13, "glowstone")
        for x, z in co_ring(lo, hi, 12, 14):
            pane(b, x, y, z)


def colossus():
    b = StagedBuild(COW, COH, COD)
    b.begin(1)
    co_plinth(b)
    b.begin(2)
    co_robe(b)
    b.begin(3)
    co_arms(b)
    b.begin(4)
    co_head(b)
    return b


def colossus_stage(stage=None):
    """For the renders only: the Colossus as it stands after a stage (whole by default)."""
    stages = colossus().stage_builds()
    return stages[(stage or len(stages)) - 1]


# --- the Stadium (with Cobblemon) ---------------------------------------------------------------------------------------
# On paper (35.23, made bigger with its interiors by 35.23a), 53 x 24 x 43, the front (the gateway, on the north long
# side) at z = 0:
# - An oval bowl round a battle field. The field, 31 x 21 (x 11-41, z 11-31), is grass ruled in white concrete: its
#   edge, the half-way line (x 26), a centre circle of radius 3 round the centre spot (26, 21). No logos. Round it a
#   smooth stone apron, then a barrier two high (stone bricks under a smooth stone cap).
# - The stands follow a rounded oblong (the field's rectangle shrunk by 4, with corners of radius 5): seven tiers along
#   the long sides (north and south), five at the ends (east and west), stepping from seven to five round the curves.
#   Each tier is two up and one back, a smooth stone tread with a carpet seat on it, red and white in sixteen wedges
#   round the bowl (the village's own colours once it has a Village Banner). Aisles of stone brick stairs climb the
#   stands either side of each vomitory, two stairs to a tier, a half step at a time (B154); round the curves the rows
#   are joined edge to edge so a player can walk right round each tier.
# - Under the stands, walkable and lit (every passage 3 high under a ceiling at y 4): the concourse, a corridor two
#   wide running right round inside the back wall under the two top tiers, entered from outside through the arched bays
#   of the facade; inside it a partition wall; inside that, between the partition and the front tier, the rooms. Eight
#   vomitories (two on each long side, one at each end's either side of the trainer boxes) climb from the concourse as
#   stairs through the stand and come out in the front rows. Rooms: on the north the first-aid room in the north-west
#   corner by the nurse's booth (cots, a red-cross banner, candles), two food kiosks either side of the gateway
#   with a serving hatch onto the concourse under a striped lintel (a furnace, a crafting table, cakes on the
#   counter), and a trophy room in the north-east corner; on the south, the main stand, the players' tunnel (three
#   wide, from the concourse to the pitch, under the professor's box and out under a canopy) with the home and away
#   dressing rooms off it (lockers, benches, their team's banner), the referees' room in the south-west corner and the
#   groundsman's store in the south-east (hay, line paint). The trainer boxes have a door at the back onto the
#   concourse.
# - The back wall (the outer face) stands three over the top tier: stone bricks with pilasters every four cells, the
#   bays between them in turn open arches into the concourse (a lantern under each, a banner over it on the straight
#   sides) and barred windows, a band of red and white terracotta half way up and under a smooth stone cornice, a
#   stone brick parapet with lanterns and, on every third pilaster, a banner. Flower beds at the foot of the barred
#   bays.
# - The two trainer boxes, cut into the stands at the field's ends (x 8-10 and 42-44, z 20-22), each a stone room under a
#   spruce roof with a Training Post facing the field; the professor's box raised over the players' tunnel in the middle
#   of the south stand (x 24-28, z 34-37) with a lectern behind its rail, under a spruce roof, a doorway each side onto
#   the second tier. Two covered dugouts of spruce benches on the apron either side of the tunnel's mouth.
# - The covered stand on the west: a lean-to canopy of spruce slabs over the west end's five tiers, on four spruce
#   columns standing on the barrier, the back wall carried up to meet it, lanterns under it.
# - The scoreboard wall on the east: a board 11 wide over the east back wall, black terracotta in a stone frame with
#   the score in white and a red header on both faces (the stands' and the road's), a stone brick cornice, between two
#   buttresses topped with banners.
# - The gateway on the north: a tunnel five wide through the north stand to the field (its ceiling stepping down under
#   the tiers, open over the front two); over its mouth two piers rising to 22 carry a lintel banded red and white with
#   an arch under it, standing banners on the piers and banners hung inside.
# - Four floodlight towers outside the bowl's curves: stone brick shafts to 19 on a stair footing, an iron platform, a
#   ring of redstone lamps wired round a daylight detector set to light them at night.
# - Outside, in the corners: the nurse's booth on the north-west (a striped stall with a Healing Machine and a brewing
#   stand), and seven pitches for market stalls (smooth stone plots, three with a stall, four with a lamp post).
# - Palette (60/30/10): stone bricks and smooth stone; spruce; red and white terracotta and wool; iron at the lights.
# - Stages, each ending tidy: I the Field (the ground across the whole bowl, the field and its lines, the apron, the
#   barrier, the trainer boxes, the professor's box over the players' tunnel); II the Lower Stands (the first four
#   tiers all round, with the rooms' walls under them); III the Upper Stands and the Covered Stand (the upper tiers over
#   the concourse, the back wall, the gate's piers to the wall top, the canopy, the scoreboard); IV the Gate and the
#   Floodlights (the gateway arch and banners, the four towers, the nurse's booth and the market pitches, and the
#   fittings: the rooms' furniture, the concourse's lanterns, the banners, the dugouts and the flower beds).
SW, SH, SD = 53, 24, 43
SCX, SCZ = 26, 21  # the field's centre
S_FIELD = (11, 11, 41, 31)  # x0, z0, x1, z1
S_CORE = (15, 15, 37, 27)  # the stands' rounded oblong: this rectangle grown by its corner radius
S_RADIUS = 5
S_LONG, S_END = 7, 5  # the tiers along the sides and at the ends
S_BODY = Mix((7, "stone_bricks"), (2, "cracked_stone_bricks"), (1, "mossy_stone_bricks"), seed=51)
S_GROUND = Mix((6, "stone_bricks"), (3, "smooth_stone"), (1, "cracked_stone_bricks"), seed=52)
S_TUNNEL = (24, 28)  # the gateway tunnel's x (z 0-9)
S_PIERS = ((22, 23), (29, 30))  # the gate piers' x (z 0-1)
S_BOXES = {"east": (42, 44), "west": (8, 10)}  # the trainer boxes' x (z 20-22 inside, walls at 19 and 23)
S_PROF = (24, 28, 34, 37)  # the professor's box: x0, x1, z0, z1 (over the players' tunnel)
S_PLAYERS = (25, 27)  # the players' tunnel's x (z 32-38)
S_SEATS = ("red_carpet", "white_carpet")
S_BANNER = [("white", "minecraft:stripe_middle"), ("white", "minecraft:border")]
S_AWAY = [("red", "minecraft:stripe_middle"), ("red", "minecraft:border")]
S_CROSS = [("red", "minecraft:straight_cross")]
S_ROOF = 4  # the ceiling of every passage under the stands (clear from 1 to 3)
S_SCORE = (49, 16, 26)  # the scoreboard: x, z0, z1


def s_field(x, z):
    return S_FIELD[0] <= x <= S_FIELD[2] and S_FIELD[1] <= z <= S_FIELD[3]


def s_offset(x, z):
    dx = max(S_CORE[0] - x, 0, x - S_CORE[2])
    dz = max(S_CORE[1] - z, 0, z - S_CORE[3])
    return dx, dz


def s_ring(x, z):
    """How far out from the field a cell is: 0 the apron, 1 the barrier, 2 to 8 the tiers, then the wall."""
    dx, dz = s_offset(x, z)
    return max(0, math.ceil(math.hypot(dx, dz) - S_RADIUS - 1e-9))


def s_tiers(x, z):
    """The tiers the stand has here: seven along the sides, five at the ends, between round the curves."""
    dx, dz = s_offset(x, z)
    if dx == 0:
        return S_LONG
    s = dz / math.hypot(dx, dz)
    return round(S_END + (S_LONG - S_END) * s * s)


def s_in_box(x, z):
    if 20 <= z <= 22 and any(x0 <= x <= x1 for x0, x1 in S_BOXES.values()):
        return True
    return S_PROF[0] <= x <= S_PROF[1] and S_PROF[2] <= z <= S_PROF[3]


def s_tunnel(x, z):
    return S_TUNNEL[0] <= x <= S_TUNNEL[1] and z <= 9


def s_players(x, z):
    """The players' tunnel, from the concourse (z 38) out through the barrier (z 33) to the apron."""
    return S_PLAYERS[0] <= x <= S_PLAYERS[1] and 33 <= z <= 38


def s_pier(x, z):
    return z <= 1 and any(a <= x <= b for a, b in S_PIERS)


def s_tread(n):
    """The height of tier n's tread: each tier two up and one back, the first one over the barrier."""
    return 1 + 2 * n


S_TUNNEL_HEIGHT = {0: 8, 1: 8, 2: 8, 3: 7, 4: 6, 5: 5, 6: 4, 7: 3}  # the gateway's clear height under each row (z)


# --- the rooms under the stands -------------------------------------------------------------------------------------------
# Each room: its inside (x0, x1, z0, z1), all in the band between the partition and the front tier, and its doors (the
# cell in the wall, the way the door faces). The fittings are drawn in stage IV.
S_ROOMS = {
    "first_aid": ((11, 14, 6, 8), [((12, 5), "south")]),
    "trophy_room": ((38, 41, 6, 8), [((40, 5), "south")]),
    "kiosk_west": ((20, 22, 5, 7), [((23, 6), "west")]),
    "kiosk_east": ((30, 32, 5, 7), [((29, 6), "east")]),
    "home": ((20, 23, 35, 37), [((24, 36), "west")]),
    "away": ((29, 32, 35, 37), [((28, 36), "east")]),
    "referees": ((11, 14, 34, 36), [((12, 37), "north")]),
    "groundsman": ((38, 41, 34, 36), [((40, 37), "north")]),
}
def s_over_room(x, z):
    return any(x0 <= x <= x1 and z0 <= z <= z1 for (x0, x1, z0, z1), _ in S_ROOMS.values())


def s_join_rows(plan):
    """Round the curves a ring of cells can touch the next cell of its row only at a corner, the tier below on one side
    and the tier above (two up) on the other, so a player walking along the row can't go on (B154). Lower the cell
    above to the row's tier, until every row is joined edge to edge: the curves come out a little squarer, their
    upper rows a tier lower. All of a round's changes at once, so the four curves stay mirror images; the straight
    sides keep their tiers, and a room under the stands keeps two tiers over it."""
    def tier(c):
        k = plan.get(c)
        return k[1] if isinstance(k, tuple) else (0 if k == "barrier" else None)

    while True:
        lower = {}
        for (x, z), kind in sorted(plan.items()):
            if not isinstance(kind, tuple) or s_in_box(x, z):
                continue
            n = kind[1]
            for sx, sz in ((1, 1), (1, -1)):
                if tier((x + sx, z + sz)) != n or s_in_box(x + sx, z + sz):
                    continue
                sides = [(x + sx, z), (x, z + sz)]
                if sorted(-1 if tier(c) is None else tier(c) for c in sides) != [n - 1, n + 1]:
                    continue
                high = next(c for c in sides if tier(c) == n + 1)
                if 0 not in s_offset(*high) and (n >= 2 or not s_over_room(*high)):
                    lower[high] = min(lower.get(high, n), n)
        if not lower:
            return
        for c, n in lower.items():
            plan[c] = ("tier", n)


def s_plan(join=True):
    """Each cell of the bowl: 'field', 'apron', 'barrier', ('tier', n) or ('wall', top). join=False: the tiers by their
    ring alone, before s_join_rows (the concourse follows those, so it still runs right round under the top two)."""
    plan = {}
    for x in range(SW):
        for z in range(SD):
            if s_field(x, z):
                plan[(x, z)] = "field"
                continue
            r = s_ring(x, z)
            if r == 0:
                plan[(x, z)] = "apron"
            elif r == 1:
                plan[(x, z)] = "barrier"
            elif r <= s_tiers(x, z) + 1:
                plan[(x, z)] = ("tier", r - 1)
    stands = dict(plan)  # the back wall stands three over the ring's top tier, joined or not
    if join:
        s_join_rows(plan)
    for x in range(SW):
        for z in range(SD):
            if (x, z) in stands:
                continue
            near = [stands.get((x + dx, z + dz)) for dx in (-1, 0, 1) for dz in (-1, 0, 1)]
            tiers = [c[1] for c in near if isinstance(c, tuple)]
            if tiers:
                plan[(x, z)] = ("wall", s_tread(max(tiers)) + 3)
    return plan


S_PLAN = s_plan()
S_RINGS = s_plan(join=False)


def s_from_top(x, z):
    """How many rings below the top a stand cell is (0: the top tier's), or None off the stands."""
    kind = S_RINGS.get((x, z))
    if not (isinstance(kind, tuple) and kind[0] == "tier"):
        return None
    return s_tiers(x, z) - kind[1]


def s_concourse(x, z):
    """The concourse: under the two top tiers, right round (the gateway and the players' tunnel cross it)."""
    k = s_from_top(x, z)
    return k is not None and k <= 1 and S_PLAN[(x, z)][1] >= 2


S_HATCHES = {"kiosk_west": (20, 22, 4), "kiosk_east": (30, 32, 4)}  # the serving hatches in the partition: x0, x1, z
# The vomitories: the cells from the partition inwards, one stair up in each, and the way they climb.
S_VOMITORIES = ([([(x, z) for z in (4, 5, 6, 7)], "south") for x in (17, 18, 34, 35)]
                + [([(x, z) for z in (38, 37, 36, 35)], "north") for x in (17, 18, 34, 35)]
                + [([(x, z) for x in (6, 7, 8)], "east") for z in (16, 26)]
                + [([(x, z) for x in (46, 45, 44)], "west") for z in (16, 26)])
S_AISLES = {"north": (15, 19, 32, 36), "south": (15, 19, 32, 36), "west": (17, 24), "east": (17, 24)}  # each a and a + 1


def s_room_cells():
    cells = set()
    for (x0, x1, z0, z1), _ in S_ROOMS.values():
        cells.update((x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1))
    return cells


def s_cuts():
    """What each stand column has instead of solid stone under its tread: {(x, z): {y: block or None (air)}}. The
    concourse, the rooms, the players' tunnel and the trainers' doors are hollow from 1 to 3; the vomitories' stairs climb
    one a cell with the stand cut away over them (two clear over each step); the parapet of a stone brick wall over each
    vomitory's mouth."""
    cuts = {}

    def hollow(x, z, ys=(1, 2, 3)):
        for y in ys:
            cuts.setdefault((x, z), {})[y] = None

    for (x, z), kind in S_PLAN.items():
        if s_concourse(x, z) or (x, z) in S_ROOM_CELLS or s_players(x, z):
            hollow(x, z)
    for (x0, x1, z0, z1), doors in S_ROOMS.values():
        for (x, z), _ in doors:
            hollow(x, z, (1, 2))
    for x0, x1, z in S_HATCHES.values():
        for x in range(x0, x1 + 1):
            cuts.setdefault((x, z), {}).update({1: "spruce_planks", 2: None, 3: "red_wool" if x % 2 else "white_wool"})
    for z in (20,):
        hollow(6, z, (1, 2))  # the west trainer box's door onto the concourse
        hollow(46, z, (1, 2))
    for cells, facing in S_VOMITORIES:
        for h, (x, z) in enumerate(cells, start=1):
            c = cuts.setdefault((x, z), {})
            for y in range(1, h):
                c[y] = S_BODY
            c[h] = ("stairs", facing)
            c[h + 1] = None
            c[h + 2] = None
        x, z = cells[-2]
        cuts.setdefault((x, z), {})[s_tread(S_PLAN[(x, z)][1]) + 1] = "stone_brick_wall"
    return cuts


S_ROOM_CELLS = s_room_cells()
S_CUTS = s_cuts()


def s_aisle(x, z):
    """An aisle cell's stair, as (the way it climbs, "on" or "in"), or None. A tier is two up and one back, so each
    climb takes two stairs one behind the other: one standing on the lower tread ("on"), then one in place of the next
    tier's tread ("in"), whose top is that tier's floor; every step is a half block (B154). The aisles are two wide and
    the climbs take turns between their cells, the odd tiers' in one and the even tiers' in the other, so a player goes
    up a flight, steps across onto the next one's first stair and on up. They climb straight out from the field on
    the long sides and the ends: no "on" stair on the top tier, no "in" on the first."""
    k = s_from_top(x, z)
    if k is None:
        return None
    n = S_PLAN[(x, z)][1]
    if z < S_CORE[1] or z > S_CORE[3]:
        side, u = ("north" if z < S_CORE[1] else "south"), x
    elif x < S_CORE[0] or x > S_CORE[2]:
        side, u = ("west" if x < S_CORE[0] else "east"), z
    else:
        return None
    for a in S_AISLES[side]:
        if u not in (a, a + 1):
            continue
        odd = (u == a)  # this cell's flights climb from the odd tiers
        if k > 0 and (n % 2 == 1) == odd:
            return side, "on"
        if n >= 2 and (n % 2 == 0) == odd:
            return side, "in"
    return None


def s_put(b, x, y, z, name, **props):
    """A block where nothing is yet (the stands run round the boxes and the tunnel drawn before them)."""
    if b.get(x, y, z) is None:
        b.set(x, y, z, name, **props)


def s_seat(x, z):
    wedge = int((math.atan2(z - SCZ, x - SCX) + math.pi) / (2 * math.pi) * 16) % 16
    return S_SEATS[wedge % 2]


def s_covered(x, z):
    """The west end under the canopy."""
    return x <= 10 and 12 <= z <= 30


def s_banner(b, x, y, z, facing=None, rotation=None, base="red", patterns=None):
    b.set_nbt(x, y, z, Compound({"id": String("minecraft:banner"), "patterns": List[Compound]([
        Compound({"color": String(c), "pattern": String(p)}) for c, p in (patterns or S_BANNER)])}))
    if facing:
        b.set(x, y, z, base + "_wall_banner", facing=facing)
    else:
        b.set(x, y, z, base + "_banner", rotation=rotation)


def s_rotation(x, z):
    """The rotation of a standing banner at (x, z) facing out from the field's centre (0 south, 4 west, 8 north)."""
    return round(math.atan2(-(x - SCX), z - SCZ) / (2 * math.pi) * 16) % 16


# --- stage I: the field ---------------------------------------------------------------------------------------------------
def s_line(x, z):
    fx0, fz0, fx1, fz1 = S_FIELD
    if x in (fx0, fx1) or z in (fz0, fz1) or x == SCX:
        return True
    r = math.hypot(x - SCX, z - SCZ)
    return 2.5 <= r < 3.5 or r == 0


def s_ground(b):
    for (x, z), kind in S_PLAN.items():
        if kind == "field":
            b.set(x, 0, z, "white_concrete" if s_line(x, z) else "grass_block", snowy=False)
        elif kind == "apron" or s_tunnel(x, z) or s_players(x, z):
            b.set(x, 0, z, "smooth_stone")
        else:
            b.set(x, 0, z, S_GROUND)
    for x in range(S_TUNNEL[0], S_TUNNEL[1] + 1):
        b.set(x, 0, 0, "smooth_stone")  # the gateway's threshold
    for (x, z), kind in S_PLAN.items():
        if kind == "barrier" and not s_in_box(x, z) and not s_tunnel(x, z) and not s_players(x, z):
            b.set(x, 1, z, S_BODY)
            b.set(x, 2, z, "smooth_stone")


def s_trainer_box(b, side):
    x0, x1 = S_BOXES[side]
    inner = x1 if side == "east" else x0  # the box's back, against the stand
    back = inner + (1 if side == "east" else -1)
    toward = "west" if side == "east" else "east"
    for z in (19, 23):
        for x in range(x0, x1 + 1):
            if S_PLAN.get((x, z)) != "apron":
                for y in (1, 2, 3):
                    b.set(x, y, z, S_BODY if y < 3 else "smooth_stone")
    for z in range(19, 24):
        for y in (1, 2, 3):
            if z == 20 and y < 3:
                continue  # the door onto the concourse
            b.set(back, y, z, S_BODY if y < 3 else "smooth_stone")
    door(b, back, 1, 20, "spruce_door", OPP[toward], hinge="left")
    for x in range(x0, x1 + 1):
        for z in range(19, 24):
            slab(b, x, 4, z, SPRUCE)
    b.set(inner, 1, 21, "aliveworkplace:training_post", facing=toward)
    lantern(b, inner, 3, 20, hanging=True)
    lantern(b, inner, 3, 22, hanging=True)
    for z in range(20, 23):
        b.set(x0 if side == "east" else x1, 0, z, "white_concrete")  # the box's line on the apron


def s_professor_box(b):
    """The professor's box over the players' tunnel: the tunnel's walls and its floor, the box on it with a rail at the
    front, a doorway each side onto the second tier, a lectern and two seats, a spruce roof; the tunnel's mouth under a
    canopy on fence posts on the apron."""
    x0, x1, z0, z1 = S_PROF
    for z in range(33, z1 + 1):
        for x in (x0, x1):
            for y in (1, 2, 3):
                if z == 36 and y < 3:
                    continue  # the dressing rooms' doors
                b.set(x, y, z, S_BODY if y < 3 else "smooth_stone")
    door(b, x0, 1, 36, "spruce_door", "west")
    door(b, x1, 1, 36, "spruce_door", "east")
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            b.set(x, 4, z, "smooth_stone")
            side = x in (x0, x1) or z == z1
            if side:
                for y in range(5, 9):
                    if x in (x0, x1) and z == z0 + 1 and y < 8:
                        continue  # the doorways onto the second tier
                    b.set(x, y, z, "smooth_stone" if y == 8 else S_BODY)
            slab(b, x, 9, z, SPRUCE)
    for x in (x0, x1):  # a half step in each doorway: the tier is a block over the box's floor (B149)
        slab(b, x, 5, z0 + 1, SPRUCE)
    for y in range(1, 5):  # and a ladder up from the players' tunnel through a hatch in the box's floor (B149)
        b.set(x1 - 1, y, z0 + 1, "ladder", facing="west", waterlogged=False)
    for x in range(x0 + 1, x1):
        fence(b, x, 5, z0, "spruce_fence")
    b.set(SCX, 5, z1 - 1, "lectern", facing="north", has_book=False, powered=False)
    stairs(b, SCX - 1, 5, z1 - 1, SPRUCE, "south")
    stairs(b, SCX + 1, 5, z1 - 1, SPRUCE, "south")
    lantern(b, SCX, 8, z0 + 1, hanging=True)
    lantern(b, SCX, 3, 36, hanging=True)  # in the players' tunnel
    for x in range(x0, x1 + 1):
        for z in (32, 33):
            slab(b, x, 4, z, SPRUCE)  # the tunnel mouth's canopy
    for x in (x0, x1):
        for y in (1, 2, 3):
            fence(b, x, y, 32, "spruce_fence")
    lantern(b, SCX, 3, 33, hanging=True)


def stage_s1(b):
    s_ground(b)
    for side in S_BOXES:
        s_trainer_box(b, side)
    s_professor_box(b)


# --- stages II and III: the stands -----------------------------------------------------------------------------------------
def s_tier(b, x, z, n):
    """The column of tier n: solid to its tread, its seat (or an aisle's stair) on top, with what S_CUTS cuts out of it
    (the passages under it, a vomitory's stairs). The gateway runs under it, its ceiling stepping down row by row."""
    if s_in_box(x, z) or s_pier(x, z):
        return
    y0 = 1
    if s_tunnel(x, z):
        if z not in S_TUNNEL_HEIGHT:
            return
        y0 = S_TUNNEL_HEIGHT[z] + 1
    top = s_tread(n)
    cut = S_CUTS.get((x, z), {})
    aisle = s_aisle(x, z)
    for y in range(y0, top + 2):
        if y in cut:
            block = cut[y]
            if block is None:
                continue
            if isinstance(block, tuple):
                stairs(b, x, y, z, STONE_BRICK, block[1])
            elif isinstance(block, str) and block.endswith("_wall"):
                wall_block(b, x, y, z, block)
            else:
                s_put(b, x, y, z, block)
        elif y < top:
            s_put(b, x, y, z, S_BODY)
        elif y == top and aisle and aisle[1] == "in":
            if b.get(x, y, z) is None:
                stairs(b, x, y, z, STONE_BRICK, aisle[0])  # the flight's second stair, in place of the tread
        elif y == top:
            s_put(b, x, y, z, "smooth_stone")
        elif aisle:
            if aisle[1] == "on" and b.get(x, y, z) is None:
                stairs(b, x, y, z, STONE_BRICK, aisle[0])  # the flight's first stair, on the tread
        else:
            s_put(b, x, y, z, s_seat(x, z))


def stage_s2(b):
    for (x, z), kind in S_PLAN.items():
        if isinstance(kind, tuple) and kind[0] == "tier" and kind[1] <= 4:
            s_tier(b, x, z, kind[1])


def s_wall_order():
    """The wall cells in order round the bowl (by their angle from the centre), for the pilasters and the band."""
    cells = [c for c, k in S_PLAN.items() if isinstance(k, tuple) and k[0] == "wall"]
    return sorted(cells, key=lambda c: math.atan2(c[1] - SCZ, c[0] - SCX))


def s_under_board(x, z):
    return x == S_SCORE[0] and S_SCORE[1] <= z <= S_SCORE[2]


def s_out(x, z):
    """The way out from a wall cell on the straight sides, or None on the curves."""
    if z == 1:
        return "north"
    if z == SD - 2:
        return "south"
    if x == 3:
        return "west"
    if x == SW - 4:
        return "east"
    return None


def s_bay(i, x, z):
    """What a wall cell is at its foot: 'pilaster', 'arch' (open into the concourse), 'window' (barred) or 'wall'.
    The bays between the pilasters take turns, an entrance and then a window."""
    if i % 4 == 0:
        return "pilaster"
    if s_tunnel(x, z) or s_under_board(x, z) or not s_opens(x, z):
        return "wall"
    return "arch" if (i // 4) % 2 == 0 else "window"


def s_back_wall(b):
    order = s_wall_order()
    for i, (x, z) in enumerate(order):
        top = S_PLAN[(x, z)][1]
        if s_covered(x, z):
            top = 14  # carried up to the canopy
        if s_under_board(x, z):
            top = 11  # under the scoreboard
        if s_pier(x, z):
            continue
        bay = s_bay(i, x, z)
        pilaster = bay == "pilaster"
        covered_wall = s_covered(x, z)
        portal = s_tunnel(x, z)  # the gateway's mouth, open to 8 under an arch
        for y in range(1, top + 1):
            if bay == "arch" and y <= 3:
                continue  # an arch into the concourse
            if bay == "window" and (y == 2 or (i % 4 == 2 and y in (8, 9))):
                pane(b, x, y, z, "iron_bars")  # the concourse's window, and a slit over it
                continue
            if y == 4 and not pilaster and not portal:
                b.set(x, y, z, "smooth_stone")  # the arcade's lintel, a course right round
                continue
            if portal and y <= 8:
                if y == 8 and x in S_TUNNEL:
                    stairs(b, x, y, z, STONE_BRICK, "west" if x == S_TUNNEL[0] else "east", top=True)
                continue
            if y == top:
                name = "smooth_stone"
            elif y == top - 1 or (y == 4 + (top - 4) // 2 and not covered_wall):
                name = "chiseled_stone_bricks" if pilaster else ("red_terracotta" if (i // 2) % 2 else "white_terracotta")
            elif portal and y == 9:
                name = "chiseled_stone_bricks"
            else:
                name = "stone_bricks" if pilaster else S_BODY
            b.set(x, y, z, name)
        if bay == "arch" and i % 4 == 2:
            lantern(b, x, 3, z, hanging=True)  # the entrance's light
        out = s_out(x, z)
        if pilaster and out and not s_under_board(x, z):
            ox, oz = x + DIRS[out][0], z + DIRS[out][1]
            if 0 <= ox < SW and 0 <= oz < SD and (ox, oz) not in S_PLAN and not s_pier(ox, oz):
                for y in range(0, top - 1):
                    b.set(ox, y, oz, "smooth_stone" if y in (0, 4) else "stone_bricks")
                stairs(b, ox, top - 1, oz, STONE_BRICK, OPP[out])  # a buttress on the straight sides
        if covered_wall or s_under_board(x, z) or portal:
            continue  # under the canopy, the scoreboard or the gate's lintel
        if pilaster:
            b.set(x, top + 1, z, "stone_bricks")
            if not s_pennant(i):
                lantern(b, x, top + 2, z)
        else:
            wall_block(b, x, top + 1, z, "stone_brick_wall")
    # B648: where a window bay meets an arch with no pilaster between, its last bars would end in the arch's opening;
    # the wall's stone stands there instead, a jamb for the bars beside it
    walls_round = set(order)
    for x, z in order:
        for y in range(1, 20):
            here = b.get(x, y, z)
            if here and here[0] == "minecraft:iron_bars" and any(
                    (x + dx, z + dz) in walls_round and b.get(x + dx, y, z + dz) is None
                    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                b.set(x, y, z, S_BODY)
    lantern(b, SCX, 8, 1, hanging=True)  # in the gateway


def s_pennant(i):
    """Whether the pilaster at wall index i flies a banner (every third) instead of a lantern."""
    return (i // 4) % 3 == 1


def s_opens(x, z):
    """Whether a wall cell has the concourse behind it (an arch there leads somewhere)."""
    return any(s_from_top(x + dx, z + dz) == 0 and not s_in_box(x + dx, z + dz) and not s_pier(x + dx, z + dz)
               for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))


def s_piers_low(b):
    """The gate's piers as high as the wall, so the stand ends square against them."""
    for a, c in S_PIERS:
        for x in (a, c):
            for z in (0, 1):
                for y in range(1, 19):
                    if z == 1 and y <= 3:
                        continue  # the concourse comes through into the gateway
                    name = "smooth_stone" if y in (1, 18) else ("chiseled_stone_bricks" if y in (5, 10, 15) else "stone_bricks")
                    b.set(x, y, z, name)


def s_canopy(b):
    """The covered stand's lean-to: spruce slabs stepping up from the apron to the back wall, on spruce columns."""
    for z in range(12, 31):
        wall_x = min(x for x in range(0, 11) if S_PLAN.get((x, z), ("", 0))[0] == "wall")
        for x in range(wall_x, 11):
            k = 10 - x
            y = 12 + k // 2
            slab(b, x, y, z, SPRUCE, top=k % 2 == 1)
    for z in (15, 17, 25, 27):
        for y in range(3, 12):
            log(b, 9, y, z, "spruce_log")
    for z in (15, 21, 27):
        lantern(b, 8, 12, z, hanging=True)


S_DIGITS = {"2": ["###", "..#", "###", "#..", "###"], "1": [".#.", "##.", ".#.", ".#.", "###"]}


def s_scoreboard(b):
    """The scoreboard over the east wall, two-faced: a stone frame, a red header and the score 2 - 1 on each face, a
    cornice of stone brick slabs; two buttresses at its ends. The west face (x) is read from the stands, north to south;
    the east face (x + 1) has the score drawn mirrored, so it reads 2 - 1 too from outside the bowl (B141: one face alone
    read "1 - S" from behind)."""
    x, z0, z1 = S_SCORE
    for face, score in ((x, s_score), (x + 1, lambda z, y: s_score(z0 + z1 - z, y))):
        for z in range(z0, z1 + 1):
            for y in range(12, 20):
                if z in (z0, z1) or y in (12, 19):
                    b.set(face, y, z, "smooth_stone" if y == 12 else "stone_bricks")
                elif y == 18:
                    b.set(face, y, z, "red_terracotta")
                else:
                    b.set(face, y, z, "white_terracotta" if score(z, y) else "black_terracotta")
            slab(b, face, 20, z, STONE_BRICK)
    for z in (z0 - 1, z1 + 1):
        for y in range(0, 20):
            b.set(x + 1, y, z, "smooth_stone" if y in (0, 19) else "stone_bricks")
        s_banner(b, x + 1, 20, z, rotation=4)  # pennants over the board, facing the field


def s_score(z, y):
    for digit, z0 in (("2", 17), ("1", 23)):
        if z0 <= z < z0 + 3:
            return S_DIGITS[digit][17 - y][z - z0] == "#"
    return z == 21 and y == 15  # the dash


def stage_s3(b):
    s_piers_low(b)
    for (x, z), kind in S_PLAN.items():
        if isinstance(kind, tuple) and kind[0] == "tier" and kind[1] >= 5:
            s_tier(b, x, z, kind[1])
    s_back_wall(b)
    s_canopy(b)
    s_scoreboard(b)


# --- stage IV: the gate, the floodlights and the fittings ---------------------------------------------------------------------
def s_gate(b):
    """The gateway arch over the tunnel's mouth: the piers carried up to 22, a lintel banded red and white on an arch,
    standing banners on the piers, banners hung from the wall over the stand; lanterns in the tunnel."""
    for a, c in S_PIERS:
        for x in (a, c):
            for z in (0, 1):
                for y in range(19, 23):
                    b.set(x, y, z, "chiseled_stone_bricks" if y == 22 else "stone_bricks")
    for x in range(S_TUNNEL[0], S_TUNNEL[1] + 1):
        for z in (0, 1):
            b.set(x, 20, z, "red_terracotta" if x % 2 else "white_terracotta")
            b.set(x, 21, z, "stone_bricks")
            b.set(x, 22, z, "smooth_stone")
            slab(b, x, 23, z, STONE_BRICK)
    for z in (0, 1):
        stairs(b, S_TUNNEL[0], 19, z, STONE_BRICK, "west", top=True)
        stairs(b, S_TUNNEL[1], 19, z, STONE_BRICK, "east", top=True)
    for x in (S_PIERS[0][1], S_PIERS[1][0]):
        s_banner(b, x, 23, 0, rotation=8)
    for x in (25, 27):
        s_banner(b, x, 17, 2, facing="south")
    lantern(b, SCX, 6, 4, hanging=True)  # in the tunnel, under its stepped ceiling
    lantern(b, SCX, 3, 7, hanging=True)


def s_floodlight(b, sx, sz):
    """A floodlight tower: a stone shaft at (sx, sz) to 19 on a footing of stairs, an iron head, a ring of eight lamps
    under redstone wire round the daylight detector on the iron at its middle (inverted: it lights them at night)."""
    for y in range(0, 20):
        b.set(sx, y, sz, "smooth_stone" if y in (0, 5, 10, 15) else "stone_bricks")
    for f, (dx, dz) in DIRS.items():
        stairs(b, sx + dx, 0, sz + dz, STONE_BRICK, OPP[f])
    head = [(x, z) for x in range(sx - 1, sx + 2) for z in range(sz - 1, sz + 2)]
    for x, z in head:
        if (x, z) == (sx, sz):
            b.set(x, 20, z, "iron_block")
            b.set(x, 21, z, "iron_block")
            b.set(x, 22, z, "daylight_detector", inverted=True, power=0)
        else:
            b.set(x, 20, z, "iron_bars", north=False, south=False, east=False, west=False, waterlogged=False)
            b.set(x, 21, z, "redstone_lamp", lit=False)
            sides = {f: ("side" if (x + dx, z + dz) in head and (x + dx, z + dz) != (sx, sz) else "none")
                     for f, (dx, dz) in DIRS.items()}
            b.set(x, 22, z, "redstone_wire", power=0, **sides)


S_FLOODS = ((1, 1), (SW - 2, 1), (1, SD - 2), (SW - 2, SD - 2))
S_BARREL = ("barrel", {"facing": "up", "open": False})
S_PITCHES = ((46, 0, [S_BARREL, ("melon", {}), ("pumpkin", {})]),
             (46, 40, [("hay_block", {"axis": "y"}), S_BARREL, None]),
             (4, 40, [S_BARREL, ("cake", {"bites": 0}), ("carved_pumpkin", {"facing": "north"})]),
             (0, 8, None), (50, 8, None), (0, 32, None), (50, 32, None))


def s_pitch(b, x0, z0, stall=None):
    """A market pitch: a 3 x 3 plot of smooth stone with a lamp post at its corner; a stall on it if given."""
    for x in range(x0, x0 + 3):
        for z in range(z0, z0 + 3):
            b.set(x, 0, z, "smooth_stone")
    if stall:
        from decor import kiosk
        kiosk(b, x0, z0, ["red_wool", "white_wool"], stall)
    else:
        lamp_post(b, x0, 1, z0)


def s_nurse_booth(b, x0, z0):
    """The nurse's booth: a striped stall with a Healing Machine and a brewing stand on its counter."""
    for x in range(x0, x0 + 3):
        for z in range(z0, z0 + 3):
            b.set(x, 0, z, "smooth_stone")
    from decor import kiosk
    kiosk(b, x0, z0, ["white_wool", "red_wool"], [
        ("cobblemon:healing_machine", {"facing": "north"}), None,
        ("brewing_stand", {"has_bottle_0": False, "has_bottle_1": False, "has_bottle_2": False})])


def s_lockers(b, x0, x1, z, facing):
    """A row of lockers against a wall: spruce trapdoors standing open against it, two high."""
    for x in range(x0, x1 + 1):
        for y in (1, 2):
            trapdoor(b, x, y, z, "spruce_trapdoor", facing, half="bottom" if y == 1 else "top", open_=True)


def s_rooms(b):
    """The rooms' fittings: lanterns from their ceilings, and each room's furniture."""
    for name, ((x0, x1, z0, z1), doors) in S_ROOMS.items():
        lantern(b, (x0 + x1 + 1) // 2, 3, (z0 + z1) // 2, hanging=True)
        for (x, z), facing in doors:
            if b.get(x, 1, z) is None:
                door(b, x, 1, z, "spruce_door", facing)
    # the first-aid room (by the nurse's booth outside): two cots, a cupboard with candles and a pot, a red cross on the wall
    for x in (11, 14):
        for z in (7, 8):
            b.set(x, 1, z, "white_wool")
    trapdoor(b, 12, 1, 8, "spruce_trapdoor", "north", half="top")
    b.set(12, 2, 8, "white_candle", candles=3, lit=False, waterlogged=False)
    trapdoor(b, 13, 1, 8, "spruce_trapdoor", "north", half="top")
    b.set(13, 2, 8, "flower_pot")
    s_banner(b, 13, 3, 6, facing="south", base="white", patterns=S_CROSS)
    # the trophy room: the cup on a plinth, the club's banners either side
    b.set(39, 1, 8, "polished_andesite")
    b.set(39, 2, 8, "gold_block")
    stairs(b, 38, 1, 8, "polished_andesite_stairs", "west")
    stairs(b, 40, 1, 8, "polished_andesite_stairs", "east")
    for x in (38, 41):
        s_banner(b, x, 3, 6, facing="south")
    lantern(b, 41, 1, 8)
    # the kiosks: a furnace and a crafting table at the back, cakes on the counter
    for x0, cake in ((20, 21), (30, 31)):
        b.set(x0, 1, 7, "crafting_table")
        b.set(x0 + 1, 1, 7, "furnace", facing="north", lit=False)
        b.set(x0 + 2, 1, 7, "hay_block", axis="y")
        b.set(cake, 2, 4, "cake", bites=0)
    # the dressing rooms: lockers against the front wall, a bench facing them, the team's banner on the back wall
    for (x0, x1), patterns, base in (((20, 23), None, "red"), ((29, 32), S_AWAY, "white")):
        s_lockers(b, x0, x1, 35, "south")
        for x in range(x0 + 1, x1):
            stairs(b, x, 1, 36, SPRUCE, "south")
        s_banner(b, (x0 + x1) // 2, 3, 37, facing="north", base=base, patterns=patterns)
    # the referees' room: a table and two chairs, a lectern-free noticeboard of trapdoors
    b.set(12, 1, 35, "spruce_fence", north=False, south=False, east=False, west=False, waterlogged=False)
    slab(b, 12, 2, 35, SPRUCE, top=False)
    stairs(b, 11, 1, 35, SPRUCE, "west")
    stairs(b, 13, 1, 35, SPRUCE, "east")
    s_lockers(b, 11, 14, 34, "south")
    # the groundsman's store: hay, the line paint (white concrete powder) and a pumpkin
    for x, z in ((38, 34), (38, 35), (39, 34)):
        b.set(x, 1, z, "hay_block", axis="y")
    b.set(38, 2, 34, "hay_block", axis="y")
    for x in (40, 41):
        b.set(x, 1, 34, "white_concrete_powder")
    b.set(41, 1, 35, "pumpkin")


def s_concourse_lights(b):
    """Lanterns hung along the concourse, about every six cells round it (from the ceiling over its inner row)."""
    cells = [c for c in S_PLAN if s_concourse(*c) and s_from_top(*c) == 1 and not s_tunnel(*c) and not s_players(*c)]
    cells.sort(key=lambda c: math.atan2(c[1] - SCZ, c[0] - SCX))
    for x, z in cells[::6]:
        lantern(b, x, 3, z, hanging=True)


def s_facade(b):
    """The facade's dressing: a banner over each entrance on the straight sides, a banner on every third pilaster, flower
    beds at the foot of the barred windows."""
    for i, (x, z) in enumerate(s_wall_order()):
        bay = s_bay(i, x, z)
        out = s_out(x, z)
        top = S_PLAN[(x, z)][1]
        if bay == "pilaster" and s_pennant(i) and not (s_covered(x, z) or s_under_board(x, z) or s_tunnel(x, z) or s_pier(x, z)):
            s_banner(b, x, top + 2, z, rotation=s_rotation(x, z))
        if out is None:
            continue
        dx, dz = DIRS[out]
        ox, oz = x + dx, z + dz
        if (not (0 <= ox < SW and 0 <= oz < SD) or (ox, oz) in S_PLAN or s_pier(x, z) or s_pier(ox, oz)
                or b.get(ox, 0, oz) is not None or b.get(ox, 1, oz) is not None):
            continue
        if bay == "arch" and i % 4 == 2:
            s_banner(b, ox, 7, oz, facing=out)
        if bay == "window":
            b.set(ox, 0, oz, "grass_block", snowy=False)
            b.set(ox, 1, oz, ("poppy", "oxeye_daisy", "red_tulip")[i % 3])


def s_dugouts(b):
    """The two dugouts on the apron either side of the players' tunnel: a spruce bench under a slab roof, fence posts at
    its ends."""
    for x0, x1 in ((18, 23), (29, 34)):
        for x in range(x0, x1 + 1):
            slab(b, x, 3, 32, SPRUCE)
            if x in (x0, x1):
                for y in (1, 2):
                    fence(b, x, y, 32, "spruce_fence")
            else:
                stairs(b, x, 1, 32, SPRUCE, "south")


def stage_s4(b):
    s_gate(b)
    for sx, sz in S_FLOODS:
        s_floodlight(b, sx, sz)
    s_nurse_booth(b, 4, 0)
    for x0, z0, stall in S_PITCHES:
        s_pitch(b, x0, z0, stall)
    s_rooms(b)
    s_concourse_lights(b)
    s_facade(b)
    s_dugouts(b)


def stadium():
    b = StagedBuild(SW, SH, SD)
    b.begin(1)
    stage_s1(b)
    b.begin(2)
    stage_s2(b)
    b.begin(3)
    stage_s3(b)
    b.begin(4)
    stage_s4(b)
    return b


def stadium_stage(stage=None):
    """For the renders only: the Stadium as it stands after a stage (whole by default)."""
    stages = stadium().stage_builds()
    return stages[(stage or len(stages)) - 1]


def stadium_interiors(cut=4):
    """For the renders only: the whole Stadium cut off over y = cut - 1, to show the concourse, the rooms and the
    tunnels under the stands from above."""
    s = stadium_stage()
    s.blocks = {p: v for p, v in s.blocks.items() if p[1] < cut}
    s.nbt = {p: v for p, v in getattr(s, "nbt", {}).items() if p in s.blocks}
    return s


# --- the Wonders' data files -------------------------------------------------------------------------------------------
from wonder_goods import GOODS  # each stage's goods and their pacing (35.7a, 35.7b)


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
        "goods": GOODS["great_cathedral"],  # 35.7a/b: tools/blueprints/wonder_goods.py
        "powers": [
            {"type": "undertakers", "search": "village", "revive_by": "morning"},
            # 35.10: the altar is where the couple stand, before the gold (19, 2, 50); the bells ring for weddings, festivals, raids
            {"type": "weddings", "mood": 15, "days": 3, "gifted_factor": 2.0, "altar": [19, 2, 49], "bells": True},
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
        "goods": GOODS["lighthouse"],  # 35.7a/b: tools/blueprints/wonder_goods.py
        "powers": [
            {"type": "caravan_range", "factor": 2},
            {"type": "caravan_speed", "factor": 0.5},
            {"type": "ferry_fares", "factor": 0.5},
            {"type": "early_warning"},
        ],
    },
    "great_library": {
        "draw": great_library,
        "icon": "minecraft:enchanting_table",
        "crew": 12,
        "requirements": [
            {"type": "rank_at_least", "rank": "town"},
            {"type": "legend", "id": "aliveworkplace:old_sage",
             "stand_in": [{"type": "job_level", "job": "aliveworkplace:scholar", "level": 5, "count": 1}]},
            {"type": "research_levels", "count": 20},
        ],
        "goods": GOODS["great_library"],  # 35.7a/b: tools/blueprints/wonder_goods.py
        "powers": [
            {"type": "research_tier", "tier": 2},
        ],
    },
    "observatory": {
        "draw": observatory,
        "icon": "minecraft:spyglass",
        "crew": 12,
        "requirements": [
            {"type": "rank_at_least", "rank": "town"},
            {"type": "legend", "id": "aliveworkplace:seer",
             "stand_in": [{"type": "job_level", "job": "minecraft:cleric", "level": 5, "count": 1}]},
        ],
        "goods": GOODS["observatory"],  # 35.7a/b: tools/blueprints/wonder_goods.py
        "powers": [
            {"type": "sky_chart", "size": 6144},
            {"type": "forecast"},
        ],
    },
    "great_wall": {
        "draw": great_wall,
        "icon": "minecraft:stone_brick_wall",
        "crew": 12,
        "requirements": [
            {"type": "rank_at_least", "rank": "town"},
            {"type": "legend", "id": "aliveworkplace:golem_smith",
             "stand_in": [{"type": "iron_golems", "count": 4},
                          {"type": "job_level", "job": "aliveworkplace:tinkerer", "level": 5, "count": 1}]},
            {"type": "finished", "blueprint": "aliveworkplace:gatehouse", "count": 1},
        ],
        "goods": GOODS["great_wall"],  # 35.7a/b: tools/blueprints/wonder_goods.py
        "powers": [
            {"type": "raid_barrier", "margin_degrees": 10},
            # 35.16: a sentry in each tower's alcove (the cell on the walk's side, at the walk), west to east
            {"type": "sentries", "count": 4, "range": 16,
             "posts": [[t + 8, W_WALK + 1, 8] for t in W_TOWERS] + [[WW - 1 - (t + 8), W_WALK + 1, 8] for t in reversed(W_TOWERS)]},
        ],
    },
    "hanging_gardens": {
        "draw": hanging_gardens,
        "icon": "minecraft:flowering_azalea",
        "crew": 12,
        "requirements": [
            {"type": "rank_at_least", "rank": "town"},
            {"type": "legend", "id": "aliveworkplace:grand_chef",
             "stand_in": [{"type": "job_level", "job": "aliveworkplace:chef", "level": 5, "count": 1},
                          {"type": "meal_kinds", "count": 8}]},
        ],
        "goods": GOODS["hanging_gardens"],  # 35.7a/b: tools/blueprints/wonder_goods.py
        "powers": [
            {"type": "harvest", "scope": "realm", "extra_every": 4},
            {"type": "births", "meals": 12, "per_day": 2},
        ],
    },
    "colossus": {
        "draw": colossus,
        "icon": "minecraft:lantern",
        "crew": 12,
        "requirements": [
            {"type": "rank_at_least", "rank": "city"},
            {"type": "legend", "id": "aliveworkplace:founder",
             "stand_in": [{"type": "rank_at_least", "rank": "city"}]},
        ],
        "goods": GOODS["colossus"],  # 35.7a/b: tools/blueprints/wonder_goods.py
        "powers": [
            {"type": "mood", "scope": "realm", "amount": 8, "home_amount": 12},
            {"type": "travellers", "factor": 2, "expert_factor": 2},
            {"type": "beauty", "points": 8},
        ],
    },
    "stadium": {
        "draw": stadium,
        # only with Cobblemon: without it the file isn't loaded, so the Stadium isn't on the Wonders page
        "load_conditions": [{"condition": "fabric:all_mods_loaded", "values": ["cobblemon"]}],
        "icon": "cobblemon:poke_ball",
        "crew": 12,
        "village_colours": True,  # its red and white are built in the village's banner colours
        "requirements": [
            {"type": "rank_at_least", "rank": "town"},
            {"type": "legend", "id": "aliveworkplace:pokemon_professor",
             "stand_in": [{"type": "pastured_pokemon", "count": 25, "types": 10}]},
        ],
        "goods": GOODS["stadium"],  # 35.7a/b: tools/blueprints/wonder_goods.py
        "powers": [
            {"type": "stadium_days", "every_days": 2, "min_visitors": 2, "max_visitors": 4},
            {"type": "tournament_host"},
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
        for problem in check(s, only=mine, whole=n == len(stages)):
            print(f"  CHECK wonder/{wonder_id} stage {n}: {problem}")
        print(f"wonder/{wonder_id} stage {n}: {len(mine)} blocks")
        before |= mine
    parts = wonder_stages("aliveworkplace", wonder_id, stages, [structures])
    assert len(parts) == len(w["goods"]), f"{wonder_id}: {len(parts)} stages drawn, {len(w['goods'])} with goods"
    key = f"wonder.aliveworkplace.{wonder_id}"
    out = {}
    if "load_conditions" in w:
        out["fabric:load_conditions"] = w["load_conditions"]
    out.update({
        "name": key + ".name",
        "description": key + ".description",
        "icon": w["icon"],
        "enabled": True,
        "crew": w["crew"],
        **({"village_colours": True} if w.get("village_colours") else {}),
        "requirements": w["requirements"],
        "stages": [{"name": f"{key}.stage.{n}", "parts": stage_parts, "goods": w["goods"][n - 1]}
                   for n, stage_parts in enumerate(parts, 1)],
        "powers": w["powers"],
    })
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
