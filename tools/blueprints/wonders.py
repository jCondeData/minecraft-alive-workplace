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
    lectern(b, *_kyv(w, 6, 3, 0), OPP[w.out])
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
        for j, z in enumerate(range(16, 25)):
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
    b.set(x1 - 1, 4, 23, "purple_bed", facing="north", part="foot", occupied=False)
    b.set(x1 - 1, 4, 24, "purple_bed", facing="north", part="head", occupied=False)
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


# --- the Stadium (with Cobblemon) ---------------------------------------------------------------------------------------
# On paper (35.23), 47 x 16 x 37, the front (the gateway, on the north long side) at z = 0:
# - An oval bowl round a battle field. The field, 31 x 21 (x 8-38, z 8-28), is grass ruled in white concrete: its edge,
#   the half-way line (x 23), a centre circle of radius 3 round the centre spot (23, 18). No logos. Round it a smooth
#   stone apron, then a barrier two high (stone bricks under a smooth stone cap).
# - The stands follow a rounded oblong (the field's rectangle shrunk by 5 with corners of radius 5): five tiers along the
#   long sides (north and south), three at the ends (east and west), stepping from five to three round the curves. Each
#   tier is one up and one back, a smooth stone tread with a carpet seat on it, red and white in sixteen wedges round
#   the bowl (the village's own colours once it has a Village Banner: those two dyes take the place of red and white
#   when the plan is drawn).
# - The back wall (the outer face) stands two over the top tier: a smooth stone footing, stone bricks with pilasters
#   every four cells, a band of red and white terracotta under a smooth stone cornice, a stone brick parapet with
#   lanterns.
# - The two trainer boxes, cut into the stands at the field's ends (x 5-7 and 39-41, z 17-19), each a stone room under
#   a spruce roof with a Training Post facing the field; the professor's box raised in the middle of the south stand
#   (x 21-25, z 30-33) with a lectern behind its rail, under a spruce roof.
# - The covered stand on the west: a lean-to canopy of spruce slabs over the west end's three tiers, on four spruce
#   columns standing on the barrier, the back wall carried up to meet it, lanterns under it.
# - The scoreboard wall on the east: a board 11 wide over the east back wall, black terracotta in a stone frame with
#   the score in white and a red header, on two buttresses.
# - The gateway on the north: a tunnel five wide through the north stand to the field, open over the lower tiers; over
#   its mouth two piers rising to 13 carry a lintel banded red and white with an arch under it, standing banners on the
#   piers and banners hung inside.
# - Four floodlight towers outside the bowl's curves: stone brick shafts 2 x 2 to 10, an iron platform, a ring of
#   redstone lamps wired round a daylight detector set to light them at night.
# - Outside, in the corners: the nurse's booth on the north-west (a striped stall with a Healing Machine and a brewing
#   stand), and six pitches for market stalls (smooth stone plots with a lamp post), one in each corner with a stall.
# - Palette (60/30/10): stone bricks and smooth stone; spruce; red and white terracotta and wool; iron at the lights.
# - Stages, each ending tidy: I the Field (the ground across the whole bowl, the field and its lines, the apron, the
#   barrier, the trainer boxes and the professor's box); II the Lower Stands (the first three tiers all round); III the
#   Upper Stands and the Covered Stand (the upper tiers, the back wall, the gate's piers to the wall top, the canopy, the
#   scoreboard); IV the Gate and the Floodlights (the gateway arch and banners, the four towers, the nurse's booth and
#   the market pitches).
SW, SH, SD = 47, 20, 37
SCX, SCZ = 23, 18  # the field's centre
S_FIELD = (8, 8, 38, 28)  # x0, z0, x1, z1
S_CORE = (12, 12, 34, 24)  # the stands' rounded oblong: this rectangle grown by its corner radius
S_RADIUS = 5
S_BODY = Mix((7, "stone_bricks"), (2, "cracked_stone_bricks"), (1, "mossy_stone_bricks"), seed=51)
S_GROUND = Mix((6, "stone_bricks"), (3, "smooth_stone"), (1, "cracked_stone_bricks"), seed=52)
S_TUNNEL = (21, 25)  # the gateway tunnel's x (z 0-6)
S_PIERS = ((19, 20), (26, 27))  # the gate piers' x (z 0-2)
S_BOXES = {"east": (39, 41), "west": (5, 7)}  # the trainer boxes' x (z 17-19)
S_PROF = (21, 25, 30, 33)  # the professor's box: x0, x1, z0, z1
S_SEATS = ("red_carpet", "white_carpet")
S_BANNER = [("white", "minecraft:stripe_middle"), ("white", "minecraft:border")]


def s_field(x, z):
    return S_FIELD[0] <= x <= S_FIELD[2] and S_FIELD[1] <= z <= S_FIELD[3]


def s_offset(x, z):
    dx = max(S_CORE[0] - x, 0, x - S_CORE[2])
    dz = max(S_CORE[1] - z, 0, z - S_CORE[3])
    return dx, dz


def s_ring(x, z):
    """How far out from the field a cell is: 0 the apron, 1 the barrier, 2 to 6 the tiers, then the wall."""
    dx, dz = s_offset(x, z)
    return max(0, math.ceil(math.hypot(dx, dz) - S_RADIUS - 1e-9))


def s_tiers(x, z):
    """The tiers the stand has here: five along the sides, three at the ends, between round the curves."""
    dx, dz = s_offset(x, z)
    if dx == 0:
        return 5
    s = dz / math.hypot(dx, dz)
    return round(3 + 2 * s * s)


def s_in_box(x, z):
    if 17 <= z <= 19 and any(x0 <= x <= x1 for x0, x1 in S_BOXES.values()):
        return True
    return S_PROF[0] <= x <= S_PROF[1] and S_PROF[2] <= z <= S_PROF[3]


def s_tunnel(x, z):
    return S_TUNNEL[0] <= x <= S_TUNNEL[1] and z <= 6


def s_pier(x, z):
    return z <= 1 and any(a <= x <= b for a, b in S_PIERS)


def s_tread(n):
    """The height of tier n's tread: each tier two up and one back, the first one over the barrier."""
    return 1 + 2 * n


S_TUNNEL_HEIGHT = {0: 7, 1: 7, 2: 6, 3: 4, 4: 3}  # the tunnel's clear height under each row (z); open at 5 and 6


def s_plan():
    """Each cell of the bowl: 'field', 'apron', 'barrier', ('tier', n) or ('wall', top)."""
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
    stands = dict(plan)
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


def s_put(b, x, y, z, name, **props):
    """A block where nothing is yet (the stands run round the boxes and the tunnel drawn before them)."""
    if b.get(x, y, z) is None:
        b.set(x, y, z, name, **props)


def s_seat(x, z):
    wedge = int((math.atan2(z - SCZ, x - SCX) + math.pi) / (2 * math.pi) * 16) % 16
    return S_SEATS[wedge % 2]


def s_covered(x, z):
    """The west end under the canopy."""
    return x <= 7 and 9 <= z <= 27


def s_banner(b, x, y, z, facing=None, rotation=None):
    b.set_nbt(x, y, z, Compound({"id": String("minecraft:banner"), "patterns": List[Compound]([
        Compound({"color": String(c), "pattern": String(p)}) for c, p in S_BANNER])}))
    if facing:
        b.set(x, y, z, "red_wall_banner", facing=facing)
    else:
        b.set(x, y, z, "red_banner", rotation=rotation)


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
        elif kind == "apron" or s_tunnel(x, z):
            b.set(x, 0, z, "smooth_stone")
        else:
            b.set(x, 0, z, S_GROUND)
    for (x, z), kind in S_PLAN.items():
        if kind == "barrier" and not s_in_box(x, z) and not s_tunnel(x, z):
            b.set(x, 1, z, S_BODY)
            b.set(x, 2, z, "smooth_stone")


def s_trainer_box(b, side):
    x0, x1 = S_BOXES[side]
    inner = x1 if side == "east" else x0  # the box's back, against the stand
    back = inner + (1 if side == "east" else -1)
    toward = "west" if side == "east" else "east"
    for z in (16, 20):
        for x in range(x0, x1 + 1):
            if S_PLAN.get((x, z)) != "apron":
                for y in (1, 2, 3):
                    b.set(x, y, z, S_BODY if y < 3 else "smooth_stone")
    for z in range(16, 21):
        for y in (1, 2, 3):
            b.set(back, y, z, S_BODY if y < 3 else "smooth_stone")
    for x in range(x0, x1 + 1):
        for z in range(16, 21):
            slab(b, x, 4, z, SPRUCE)
    b.set(inner, 1, 18, "aliveworkplace:training_post", facing=toward)
    lantern(b, inner, 3, 17, hanging=True)
    lantern(b, inner, 3, 19, hanging=True)
    for z in range(17, 20):
        b.set(x0 if side == "east" else x1, 0, z, "white_concrete")  # the box's line on the apron


def s_professor_box(b):
    x0, x1, z0, z1 = S_PROF
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            side = x in (x0, x1) or z == z1
            for y in range(1, 6 if side else 3):
                b.set(x, y, z, "smooth_stone" if y in (2, 5) and side else S_BODY)
            slab(b, x, 6, z, SPRUCE)
    for x in range(x0 + 1, x1):
        fence(b, x, 3, z0, "spruce_fence")
    b.set(SCX, 3, z1 - 1, "lectern", facing="north", has_book=False, powered=False)
    stairs(b, SCX - 1, 3, z1 - 1, SPRUCE, "south")
    stairs(b, SCX + 1, 3, z1 - 1, SPRUCE, "south")
    lantern(b, SCX, 5, z0 + 1, hanging=True)


def stage_s1(b):
    s_ground(b)
    for side in S_BOXES:
        s_trainer_box(b, side)
    s_professor_box(b)


# --- stages II and III: the stands -----------------------------------------------------------------------------------------
def s_tier(b, x, z, n):
    """The column of tier n: solid to its tread, its seat on top. The tunnel runs under it, open under the first two
    rows."""
    if s_in_box(x, z) or s_pier(x, z):
        return
    y0 = 1
    if s_tunnel(x, z):
        if z not in S_TUNNEL_HEIGHT:
            return
        y0 = S_TUNNEL_HEIGHT[z] + 1
    top = s_tread(n)
    if S_PLAN[(x, z)] == ("tier", s_tiers(x, z)) and not s_tunnel(x, z):
        y0 = 5  # the concourse runs round under the top tier, behind the back wall's arches
    for y in range(y0, top):
        s_put(b, x, y, z, S_BODY)
    s_put(b, x, top, z, "smooth_stone")
    s_put(b, x, top + 1, z, s_seat(x, z))


def stage_s2(b):
    for (x, z), kind in S_PLAN.items():
        if isinstance(kind, tuple) and kind[0] == "tier" and kind[1] <= 3:
            s_tier(b, x, z, kind[1])
    lantern(b, SCX, 3, 4, hanging=True)  # in the tunnel


def s_wall_order():
    """The wall cells in order round the bowl (by their angle from the centre), for the pilasters and the band."""
    cells = [c for c, k in S_PLAN.items() if isinstance(k, tuple) and k[0] == "wall"]
    return sorted(cells, key=lambda c: math.atan2(c[1] - SCZ, c[0] - SCX))


def s_back_wall(b):
    order = s_wall_order()
    for i, (x, z) in enumerate(order):
        top = S_PLAN[(x, z)][1]
        if s_covered(x, z):
            top = 10  # carried up to the canopy
        if x == 44 and 13 <= z <= 23:
            top = 7  # under the scoreboard
        if s_pier(x, z):
            continue
        pilaster = i % 4 == 0
        covered_wall = s_covered(x, z)
        portal = s_tunnel(x, z)  # the gateway's mouth, open to 7 under an arch
        arch = i % 4 != 0 and not portal and not (x == 44 and 13 <= z <= 23) and s_opens(x, z)
        for y in range(1, top + 1):
            if arch and y <= 3:
                continue  # an arch into the concourse
            if y == 4 and not pilaster and not portal:
                b.set(x, y, z, "smooth_stone")  # the arcade's lintel, a course right round
                continue
            if portal and y <= 7:
                if y == 7 and x in S_TUNNEL:
                    stairs(b, x, y, z, STONE_BRICK, "west" if x == S_TUNNEL[0] else "east", top=True)
                continue
            if y == top:
                name = "smooth_stone"
            elif y == top - 1 or (y == 4 + (top - 4) // 2 and not covered_wall):
                name = "chiseled_stone_bricks" if pilaster else ("red_terracotta" if (i // 2) % 2 else "white_terracotta")
            elif portal and y == 8:
                name = "chiseled_stone_bricks"
            else:
                name = "stone_bricks" if pilaster else S_BODY
            b.set(x, y, z, name)
        if s_covered(x, z) or (x == 44 and 13 <= z <= 23) or portal:
            continue  # under the canopy, the scoreboard or the gate's lintel
        if pilaster:
            b.set(x, top + 1, z, "stone_bricks")
            lantern(b, x, top + 2, z)
        else:
            wall_block(b, x, top + 1, z, "stone_brick_wall")
    lantern(b, SCX, 7, 1, hanging=True)  # in the gateway


def s_opens(x, z):
    """Whether a wall cell has the concourse behind it (an arch there leads somewhere)."""
    return any(S_PLAN.get((x + dx, z + dz), ("", 0)) == ("tier", s_tiers(x + dx, z + dz))
               and not s_in_box(x + dx, z + dz) and not s_pier(x + dx, z + dz)
               for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))


def s_piers_low(b):
    """The gate's piers as high as the wall, so the stand ends square against them."""
    for a, c in S_PIERS:
        for x in (a, c):
            for z in (0, 1):
                for y in range(1, 15):
                    if z == 1 and y <= 3:
                        continue  # the concourse comes through into the gateway
                    name = "smooth_stone" if y in (1, 14) else ("chiseled_stone_bricks" if y in (5, 10) else "stone_bricks")
                    b.set(x, y, z, name)


def s_canopy(b):
    """The covered stand's lean-to: spruce slabs stepping up from the barrier to the back wall, on spruce columns."""
    for z in range(9, 28):
        wall_x = min(x for x in range(0, 8) if S_PLAN.get((x, z), ("", 0))[0] == "wall")
        for x in range(wall_x, 8):
            k = 7 - x
            y = 9 + k // 2
            slab(b, x, y, z, SPRUCE, top=k % 2 == 1)
    for z in (12, 15, 21, 24):
        for y in range(3, 9):
            log(b, 6, y, z, "spruce_log")
    for z in (13, 18, 23):
        lantern(b, 5, 9, z, hanging=True)


S_DIGITS = {"2": ["###", "..#", "###", "#..", "###"], "1": [".#.", "##.", ".#.", ".#.", "###"]}


def s_scoreboard(b):
    """The scoreboard over the east wall, facing the field: a stone frame, a red header, the score 2 - 1 (the board
    faces west, so the stands read it north to south)."""
    x = 44
    for z in range(13, 24):
        for y in range(8, 16):
            if z in (13, 23) or y in (8, 15):
                b.set(x, y, z, "smooth_stone" if y == 8 else "stone_bricks")
            elif y == 14:
                b.set(x, y, z, "red_terracotta")
            else:
                b.set(x, y, z, "white_terracotta" if s_score(z, y) else "black_terracotta")
    for z in (12, 24):
        for y in range(0, 16):
            b.set(45, y, z, "smooth_stone" if y in (0, 15) else "stone_bricks")


def s_score(z, y):
    for digit, z0 in (("2", 15), ("1", 19)):
        if z0 <= z < z0 + 3:
            return S_DIGITS[digit][13 - y][z - z0] == "#"
    return z == 18 and y == 11  # the dash


def stage_s3(b):
    s_piers_low(b)
    for (x, z), kind in S_PLAN.items():
        if isinstance(kind, tuple) and kind[0] == "tier" and kind[1] >= 4:
            s_tier(b, x, z, kind[1])
    s_back_wall(b)
    s_canopy(b)
    s_scoreboard(b)


# --- stage IV: the gate and the floodlights ---------------------------------------------------------------------------------
def s_gate(b):
    """The gateway arch over the tunnel's mouth: the piers carried up to 18, a lintel banded red and white on an arch,
    standing banners on the piers, banners hung from the lintel over the stand."""
    for a, c in S_PIERS:
        for x in (a, c):
            for z in (0, 1):
                for y in range(15, 19):
                    b.set(x, y, z, "chiseled_stone_bricks" if y == 18 else "stone_bricks")
    for x in range(S_TUNNEL[0], S_TUNNEL[1] + 1):
        for z in (0, 1):
            b.set(x, 16, z, "red_terracotta" if x % 2 else "white_terracotta")
            b.set(x, 17, z, "stone_bricks")
            b.set(x, 18, z, "smooth_stone")
            slab(b, x, 19, z, STONE_BRICK)
    for z in (0, 1):
        stairs(b, S_TUNNEL[0], 15, z, STONE_BRICK, "west", top=True)
        stairs(b, S_TUNNEL[1], 15, z, STONE_BRICK, "east", top=True)
    for x in (S_PIERS[0][1], S_PIERS[1][0]):
        s_banner(b, x, 19, 0, rotation=8)
    for x in (22, 24):
        s_banner(b, x, 16, 2, facing="south")


def s_floodlight(b, sx, sz):
    """A floodlight tower: a stone shaft at (sx, sz) to 15, an iron head, a ring of eight lamps under redstone wire round
    the daylight detector on the iron at its middle (inverted: it lights them at night)."""
    for y in range(0, 16):
        b.set(sx, y, sz, "smooth_stone" if y in (0, 5, 10) else "stone_bricks")
    head = [(x, z) for x in range(sx - 1, sx + 2) for z in range(sz - 1, sz + 2)]
    for x, z in head:
        if (x, z) == (sx, sz):
            b.set(x, 16, z, "iron_block")
            b.set(x, 17, z, "iron_block")
            b.set(x, 18, z, "daylight_detector", inverted=True, power=0)
        else:
            b.set(x, 16, z, "iron_bars", north=False, south=False, east=False, west=False, waterlogged=False)
            b.set(x, 17, z, "redstone_lamp", lit=False)
            sides = {f: ("side" if (x + dx, z + dz) in head and (x + dx, z + dz) != (sx, sz) else "none")
                     for f, (dx, dz) in DIRS.items()}
            b.set(x, 18, z, "redstone_wire", power=0, **sides)


S_FLOODS = ((1, 1), (45, 1), (1, 35), (45, 35))
S_BARREL = ("barrel", {"facing": "up", "open": False})
S_PITCHES = ((41, 0, [S_BARREL, ("melon", {}), ("pumpkin", {})]),
             (41, 34, [("hay_block", {"axis": "y"}), S_BARREL, None]),
             (3, 34, [S_BARREL, ("cake", {"bites": 0}), ("carved_pumpkin", {"facing": "north"})]),
             (0, 6, None), (44, 6, None), (0, 28, None), (44, 28, None))


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


def stage_s4(b):
    s_gate(b)
    for sx, sz in S_FLOODS:
        s_floodlight(b, sx, sz)
    s_nurse_booth(b, 3, 0)
    for x0, z0, stall in S_PITCHES:
        s_pitch(b, x0, z0, stall)


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
        "goods": [
            goods(("item", "minecraft:sugar_cane", 128, "minecraft:farmer"),
                  ("item", "minecraft:leather", 64, "minecraft:butcher"),
                  ("item", "minecraft:spruce_log", 64, "aliveworkplace:lumberjack")),
            goods(("item", "minecraft:book", 64, "minecraft:librarian"),
                  ("item", "minecraft:black_dye", 64, "minecraft:leatherworker"),
                  ("item", "minecraft:lantern", 16, "aliveworkplace:tinkerer")),
            goods(("item", "minecraft:filled_map", 8, "minecraft:cartographer"),
                  ("item", "minecraft:lapis_lazuli", 32, "aliveworkplace:sifter"),
                  ("item", "minecraft:copper_ingot", 32, "minecraft:armorer")),
            goods(("item", "minecraft:quartz", 64, "aliveworkplace:netherworker"),
                  ("item", "minecraft:cookie", 32, "aliveworkplace:chef"),
                  ("item", "minecraft:bookshelf", 16, "minecraft:librarian")),
        ],
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
        "goods": [
            goods(("item", "minecraft:lapis_lazuli", 32, "aliveworkplace:sifter"),
                  ("item", "minecraft:cooked_chicken", 64, "aliveworkplace:chef"),
                  ("item", "minecraft:filled_map", 8, "minecraft:cartographer")),
            goods(("item", "minecraft:copper_ingot", 64, "minecraft:armorer"),
                  ("item", "minecraft:spyglass", 4, "minecraft:toolsmith"),
                  ("item", "minecraft:amethyst_shard", 32, "aliveworkplace:miner")),
            goods(("item", "minecraft:book", 16, "minecraft:librarian"),
                  ("item", "minecraft:tinted_glass", 8, "aliveworkplace:carpenter"),
                  ("item", "minecraft:feather", 32, "minecraft:butcher")),
            goods(("item", "minecraft:chain", 32, "aliveworkplace:tinkerer"),
                  ("item", "minecraft:gold_ingot", 9, "minecraft:armorer"),
                  ("item", "minecraft:pumpkin_pie", 32, "aliveworkplace:chef")),
        ],
        "powers": [
            {"type": "sky_chart", "size": 1024},
            {"type": "forecast"},
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
        "goods": [
            goods(("item", "minecraft:bone_meal", 128, "aliveworkplace:composter"),
                  ("tag", "minecraft:small_flowers", 64, "aliveworkplace:florist"),
                  ("item", "cobblemon:poke_ball", 32, "aliveworkplace:ball_smith")),
            goods(("item", "minecraft:red_wool", 64, "minecraft:leatherworker"),
                  ("item", "minecraft:white_wool", 64, "minecraft:shepherd"),
                  ("item", "cobblemon:poke_snack", 16, "aliveworkplace:chef"),
                  ("item", "cobblemon:great_ball", 16, "aliveworkplace:ball_smith")),
            goods(("tag", "minecraft:banners", 16, "aliveworkplace:carpenter"),
                  ("item", "minecraft:cooked_beef", 64, "aliveworkplace:chef"),
                  ("tag", "cobblemon:berries", 32, "aliveworkplace:orchard_keeper")),
            goods(("item", "minecraft:daylight_detector", 8, "aliveworkplace:tinkerer"),
                  ("item", "cobblemon:ultra_ball", 16, "aliveworkplace:ball_smith"),
                  ("item", "minecraft:pumpkin_pie", 32, "aliveworkplace:chef")),
        ],
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
        for problem in check(s, only=mine):
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
