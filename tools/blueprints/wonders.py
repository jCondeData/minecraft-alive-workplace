#!/usr/bin/env python3
"""
Wonders (ROADMAP Milestone 35), drawn one stage at a time with kit.wonder_stages().

    python3 tools/blueprints/wonders.py

The test folly (35.3) is the first: a three-stage stone tower with a dome, for the GameTests (gametest resources) and the
showcase (the screenshot harness's resources), both as `aliveworkplace_test:wonder/test_folly/stage_<n>_0`. Its whole
tower as drawn is saved beside it (`aliveworkplace_test:wonder_drawn/test_folly`), so a GameTest can check the merged
parts against it.

The folly, on paper: a 9 x 9 plinth of andesite (mossy at the foot of the corners); a 7 x 7 stone-brick tower on it in
two storeys of 5 with chiseled corner posts, a centred arched door on the front (z = 0) with a lantern either side,
one window per face and storey (framed with stairs above), a cornice of stone-brick stairs at each floor line; a
deepslate-tile dome stepped in three rings on a drum, a lantern on a blackstone finial on top. Stage I: plinth, ground storey
and its floor. Stage II: the upper storey and its cornice. Stage III: the dome and finial.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from kit import Build, TEST_STRUCTURES, DEVCLIENT_STRUCTURES, wonder_stages, stairs, Family, ROOT  # noqa: E402

W, H, D = 9, 19, 9
BRICK = Family("stone_bricks", "stone_brick_stairs", "stone_brick_slab", wall="stone_brick_wall")
TILE = Family("deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab", wall="deepslate_tile_wall")
FACES = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}


def storey(b, y0):
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


def cornice(b, y):
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


def stage_1():
    b = Build(W, H, D)
    for x in range(W):
        for z in range(D):
            edge = x in (0, 8) or z in (0, 8)
            b.set(x, 0, z, ("mossy_cobblestone" if edge and (x in (0, 8)) and (z in (0, 1, 7, 8)) else "andesite") if edge else "polished_andesite")
    storey(b, 1)
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
    cornice(b, 6)
    return b


def stage_2():
    b = stage_1()
    storey(b, 7)
    cornice(b, 12)
    return b


def stage_3():
    b = stage_2()
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
    return stage_3()


FOLLY_FILES = [
    os.path.join(ROOT, "src/gametest/resources/data/aliveworkplace_test/wonders/test_folly.json"),
    os.path.join(ROOT, "src/devclient/resources/data/aliveworkplace_test/wonders/test_folly.json"),
]


if __name__ == "__main__":
    parts = wonder_stages("aliveworkplace_test", "test_folly", [stage_1(), stage_2(), stage_3()], [TEST_STRUCTURES, DEVCLIENT_STRUCTURES])
    whole = stage_3()
    whole.fill_air()
    for folder in (TEST_STRUCTURES, DEVCLIENT_STRUCTURES):
        whole.save(os.path.join(folder, "wonder_drawn"), "test_folly")
    # the folly's file: its stages point at the parts just saved (the rest of the file is kept as written)
    with open(FOLLY_FILES[0]) as f:
        folly = json.load(f)
    for stage, stage_parts in zip(folly["stages"], parts):
        stage["parts"] = stage_parts
    text = json.dumps(folly, indent=2) + "\n"
    for path in FOLLY_FILES:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w") as f:
            f.write(text)
