#!/usr/bin/env python3
"""
Draws the mod's pixel-art textures (all original; no vanilla assets are copied).

    python3 tools/textures/generate.py

Outputs into src/main/resources/assets/aliveworkplace/textures/.
"""
import os
import random
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
TEX = os.path.join(ROOT, "src/main/resources/assets/aliveworkplace/textures")
T = (0, 0, 0, 0)


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def jitter(c, rnd, amount=8):
    d = rnd.randint(-amount, amount)
    return tuple(max(0, min(255, v + d)) for v in c[:3]) + (c[3],)


def save(img, *parts):
    path = os.path.join(TEX, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print("wrote", os.path.relpath(path, ROOT))


# --- wood helpers -----------------------------------------------------------------------------
WOOD = [rgb("#b8874f"), rgb("#a67943"), rgb("#9a6d3b")]
WOOD_DARK = rgb("#6e4a26")
WOOD_EDGE = rgb("#553619")


def planks(img, rnd, y0=0, y1=16):
    for y in range(y0, y1):
        band = (y // 4) % 2
        for x in range(16):
            base = WOOD[band] if (x + (y // 4) * 5) % 16 else WOOD_DARK
            img.putpixel((x, y), jitter(base, rnd, 6))
        if y % 4 == 3:
            for x in range(16):
                img.putpixel((x, y), jitter(WOOD[2], rnd, 4))


# --- Builder's Bench --------------------------------------------------------------------------
def bench_top():
    rnd = random.Random(1)
    img = Image.new("RGBA", (16, 16))
    planks(img, rnd)
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, WOOD_EDGE)
    # Blueprint sheet
    paper, grid, line = rgb("#2d5fa6"), rgb("#4d7fc4"), rgb("#e8f1ff")
    for y in range(2, 12):
        for x in range(2, 13):
            img.putpixel((x, y), grid if (x % 3 == 0 or y % 3 == 0) else paper)
    for x in range(2, 13):
        img.putpixel((x, 12), rgb("#1f467f"))
    # House sketch
    for x, y in [(5, 9), (6, 9), (7, 9), (8, 9), (9, 9), (5, 8), (9, 8), (5, 7), (9, 7), (5, 6), (9, 6),
                 (6, 5), (8, 5), (7, 4), (7, 8), (7, 7)]:
        img.putpixel((x, y), line)
    # Pencil
    for i, (x, y) in enumerate([(9, 14), (10, 13), (11, 12), (12, 11), (13, 10)]):
        img.putpixel((x, y), rgb("#f0c23a") if i < 4 else rgb("#e89aa0"))
    img.putpixel((8, 14), rgb("#3a2a1a"))
    # Ruler
    for x in range(2, 8):
        img.putpixel((x, 14), rgb("#d9d2b0") if x % 2 else rgb("#bfb58c"))
    save(img, "block", "builders_bench_top.png")


def bench_side(front=False):
    rnd = random.Random(2 if front else 3)
    img = Image.new("RGBA", (16, 16))
    planks(img, rnd, 3, 16)
    for y in range(0, 3):
        for x in range(16):
            img.putpixel((x, y), jitter(WOOD_EDGE if y != 1 else WOOD_DARK, rnd, 4))
    for y in range(3, 16):
        img.putpixel((0, y), WOOD_EDGE)
        img.putpixel((15, y), WOOD_EDGE)
    if front:
        # Drawer
        for y in range(9, 14):
            for x in range(3, 13):
                edge = y in (9, 13) or x in (3, 12)
                img.putpixel((x, y), rgb("#4a2f16") if edge else jitter(rgb("#7b5530"), rnd, 5))
        img.putpixel((7, 11), rgb("#c9c9c9"))
        img.putpixel((8, 11), rgb("#c9c9c9"))
        # Rolled blueprints in the cubby
        for y in range(4, 8):
            for x in range(3, 13):
                img.putpixel((x, y), rgb("#3a2410"))
        for cx in (4, 7, 10):
            for dy in range(4, 8):
                img.putpixel((cx, dy), rgb("#2d5fa6"))
                img.putpixel((cx + 1, dy), rgb("#4d7fc4"))
            img.putpixel((cx, 4), rgb("#e8f1ff"))
    else:
        # Hanging saw
        for y in range(4, 11):
            for x in range(3, 6):
                img.putpixel((x, y), rgb("#cfd3d6") if x < 5 else rgb("#9ea4a8"))
            if y % 2 == 0:
                img.putpixel((2, y), rgb("#9ea4a8"))
        for y in range(4, 7):
            img.putpixel((6, y), rgb("#7a4a22"))
        # Hammer
        for y in range(5, 13):
            img.putpixel((11, y), rgb("#7a4a22"))
        for x in range(9, 14):
            img.putpixel((x, 4), rgb("#5c6166"))
            img.putpixel((x, 5), rgb("#40454a"))
    save(img, "block", "builders_bench_front.png" if front else "builders_bench_side.png")


# --- Blueprint item ---------------------------------------------------------------------------
def blueprint_item():
    img = Image.new("RGBA", (16, 16), T)
    paper, grid, edge, line, dark = rgb("#2f64b2"), rgb("#4b80cc"), rgb("#16345f"), rgb("#eef5ff"), rgb("#224c8c")
    for y in range(2, 15):
        for x in range(1, 15):
            if x in (1, 14) or y in (2, 14):
                img.putpixel((x, y), edge)
            else:
                img.putpixel((x, y), grid if (x % 3 == 1 or y % 3 == 1) else paper)
    # Rolled top edge
    for x in range(1, 15):
        img.putpixel((x, 1), rgb("#6d9be0"))
        img.putpixel((x, 2), dark)
    img.putpixel((0, 1), edge)
    img.putpixel((15, 1), edge)
    # House sketch
    for x, y in [(4, 12), (5, 12), (6, 12), (7, 12), (8, 12), (9, 12), (10, 12), (11, 12),
                 (4, 11), (11, 11), (4, 10), (11, 10), (4, 9), (11, 9), (4, 8), (11, 8),
                 (5, 7), (10, 7), (6, 6), (9, 6), (7, 5), (8, 5), (7, 12), (7, 11), (7, 10), (8, 10), (8, 11),
                 (9, 9), (10, 9)]:
        img.putpixel((x, y), line)
    # Dog-eared corner
    img.putpixel((13, 13), rgb("#9fc0f0"))
    img.putpixel((12, 13), rgb("#9fc0f0"))
    img.putpixel((13, 12), rgb("#9fc0f0"))
    save(img, "item", "blueprint.png")
    return img


# --- Blueprint Table -------------------------------------------------------------------------
DARK_WOOD = [rgb("#6b4a2c"), rgb("#5e4026"), rgb("#523720")]


def table_top():
    rnd = random.Random(11)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), jitter(DARK_WOOD[(y // 3) % 3], rnd, 5))
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, rgb("#3b2714"))
    # Big sheet pinned to the board
    paper, grid, line = rgb("#2d5fa6"), rgb("#4b7fc6"), rgb("#e8f1ff")
    for y in range(2, 14):
        for x in range(2, 14):
            img.putpixel((x, y), grid if (x % 4 == 1 or y % 4 == 1) else paper)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        img.putpixel((x, y), rgb("#d23c3c"))  # pins
    # Floor plan sketch
    for x in range(4, 12):
        img.putpixel((x, 4), line)
        img.putpixel((x, 11), line)
    for y in range(4, 12):
        img.putpixel((4, y), line)
        img.putpixel((11, y), line)
    for y in range(4, 8):
        img.putpixel((8, y), line)
    img.putpixel((7, 11), paper)  # door gap
    # T-square along the bottom edge
    for x in range(1, 15):
        img.putpixel((x, 14), rgb("#e0c890"))
    for y in range(9, 15):
        img.putpixel((14, y), rgb("#c9ac6c"))
    save(img, "block", "blueprint_table_top.png")


def table_side(front=False):
    rnd = random.Random(12 if front else 13)
    img = Image.new("RGBA", (16, 16), T)
    for y in range(0, 16):
        for x in range(16):
            img.putpixel((x, y), jitter(DARK_WOOD[1], rnd, 5))
    for x in range(16):
        img.putpixel((x, 0), rgb("#3b2714"))
        img.putpixel((x, 1), rgb("#e0c890"))  # paper edge peeking over the top
        img.putpixel((x, 2), rgb("#3b2714"))
    for y in range(3, 16):
        for x in (0, 1, 14, 15):
            img.putpixel((x, y), jitter(DARK_WOOD[2], rnd, 4))
    if front:
        # Rack of rolled plans
        for y in range(4, 10):
            for x in range(2, 14):
                img.putpixel((x, y), rgb("#2a1a0c"))
        for cx in (3, 6, 9, 12):
            for y in range(4, 10):
                img.putpixel((cx, y), rgb("#2d5fa6"))
            img.putpixel((cx, 4), rgb("#e8f1ff"))
        for x in range(2, 14):
            img.putpixel((x, 12), rgb("#3b2714"))
        img.putpixel((7, 13), rgb("#c9c9c9"))
        img.putpixel((8, 13), rgb("#c9c9c9"))
    else:
        for y in range(5, 14):
            for x in range(3, 13):
                if (x + y) % 5 == 0:
                    img.putpixel((x, y), jitter(DARK_WOOD[0], rnd, 4))
    save(img, "block", "blueprint_table_front.png" if front else "blueprint_table_side.png")


def blank_blueprint_item():
    img = Image.new("RGBA", (16, 16), T)
    paper, grid, edge = rgb("#3a6fbd"), rgb("#5286d4"), rgb("#1a3a69")
    for y in range(2, 15):
        for x in range(2, 14):
            if x in (2, 13) or y in (2, 14):
                img.putpixel((x, y), edge)
            else:
                img.putpixel((x, y), grid if (x % 3 == 2 or y % 3 == 2) else paper)
    for x in range(2, 14):
        img.putpixel((x, 1), rgb("#79a6ea"))
    save(img, "item", "blank_blueprint.png")


# --- Builder villager overlay -----------------------------------------------------------------
def builder_overlay():
    img = Image.new("RGBA", (64, 64), T)
    rnd = random.Random(7)
    hat, hat_shade, hat_hi, hat_edge = rgb("#f2c230"), rgb("#d9a41e"), rgb("#ffe27a"), rgb("#b07f12")

    # Hard hat on the hat layer (texture offset 32,0; box 8x10x8): top face + top 4 rows of the sides.
    for y in range(0, 8):
        for x in range(40, 48):
            c = hat_hi if (x - 40 in (3, 4) and y < 7) else hat
            img.putpixel((x, y), jitter(c, rnd, 5))
    for y in range(8, 12):
        for x in range(32, 64):
            if y == 11:
                c = hat_edge
            elif y == 8 and (x - 32) % 8 in (3, 4):
                c = hat_hi
            else:
                c = hat_shade if y == 10 else hat
            img.putpixel((x, y), jitter(c, rnd, 5))
    # Front ridge highlight
    for y in range(8, 11):
        img.putpixel((43, y), hat_hi)
        img.putpixel((44, y), hat_hi)
    # Brim (hat_rim, 16x16 face at 31,48): a thin ring just outside the head
    for i in range(3, 13):
        for p in ((31 + i, 48 + 3), (31 + i, 48 + 12), (31 + 3, 48 + i), (31 + 12, 48 + i)):
            img.putpixel(p, hat_edge)

    # Hi-vis vest on the robe (texture offset 0,38; box 8x20x6): top face + torso part of the sides.
    vest, vest_shade, stripe = rgb("#f5821f"), rgb("#d8691a"), rgb("#e6ecee")
    for y in range(38, 44):
        for x in range(6, 14):
            img.putpixel((x, y), jitter(vest, rnd, 5))
    for y in range(44, 55):
        for x in range(0, 28):
            face_x = x
            if 6 <= x < 14 and x in (9, 10):
                continue  # vest opening down the front
            c = stripe if y in (49, 52) else (vest_shade if y == 54 else vest)
            img.putpixel((face_x, y), jitter(c, rnd, 4))
    # Tool belt
    for y in (55, 56):
        for x in range(0, 28):
            img.putpixel((x, y), jitter(rgb("#6b4423") if y == 55 else rgb("#4e2f1a"), rnd, 4))
    img.putpixel((9, 55), rgb("#cfcfcf"))
    img.putpixel((10, 55), rgb("#cfcfcf"))
    img.putpixel((9, 56), rgb("#9a9a9a"))
    img.putpixel((10, 56), rgb("#9a9a9a"))
    # Hammer hanging on the left side, tape measure on the front
    for y in range(57, 61):
        img.putpixel((16, y), rgb("#7a4a22"))
    img.putpixel((15, 57), rgb("#55595e"))
    img.putpixel((17, 57), rgb("#55595e"))
    for y in (57, 58):
        for x in (12, 13):
            img.putpixel((x, y), rgb("#f2c230"))
    save(img, "entity", "villager", "profession", "builder.png")
    save(img, "entity", "zombie_villager", "profession", "builder.png")


# --- Miner's Bench: a stone workbench with an iron plate and a pickaxe ---------------------------
STONE = [rgb("#8a8a8a"), rgb("#7c7c7c"), rgb("#949494")]
STONE_DARK = rgb("#5e5e5e")


def stone(img, rnd, y0=0, y1=16):
    for y in range(y0, y1):
        for x in range(16):
            mortar = y % 8 == 7 or (x + (8 if (y // 8) % 2 else 0)) % 16 == 0
            img.putpixel((x, y), jitter(STONE_DARK if mortar else STONE[(x * 7 + y * 3) % 3], rnd, 6))


def pickaxe(img, ox, oy, head=rgb("#c8c8c8"), head_dark=rgb("#8f8f8f"), handle=rgb("#8a5a2b")):
    """A 9x9 pickaxe drawn diagonally with its top-left at (ox, oy)."""
    for i in range(7):
        img.putpixel((ox + 1 + i, oy + 7 - i), handle)
    for x, y in ((0, 2), (1, 1), (2, 0), (3, 0), (4, 0), (5, 1), (6, 2), (7, 3), (8, 4), (2, 1), (6, 3)):
        if 0 <= ox + x < 16 and 0 <= oy + y < 16:
            img.putpixel((ox + x, oy + y), head if (x + y) % 3 else head_dark)


def miners_bench_top():
    rnd = random.Random(11)
    img = Image.new("RGBA", (16, 16))
    stone(img, rnd)
    for x in range(3, 13):
        for y in range(3, 13):
            img.putpixel((x, y), jitter(rgb("#b9bdc2") if (x + y) % 5 else rgb("#9ea3a8"), rnd, 4))
    for i in range(3, 13):
        for p in ((i, 3), (i, 12), (3, i), (12, i)):
            img.putpixel(p, rgb("#6d7176"))
    pickaxe(img, 4, 4, head=rgb("#4a4a4a"), head_dark=rgb("#2f2f2f"), handle=rgb("#7a4a22"))
    save(img, "block", "miners_bench_top.png")


def miners_bench_side(front=False):
    rnd = random.Random(12 if front else 13)
    img = Image.new("RGBA", (16, 16))
    stone(img, rnd, 4, 16)
    planks(img, rnd, 0, 4)
    for x in range(16):
        img.putpixel((x, 3), WOOD_EDGE)
    if front:
        # A rack with a pickaxe and a lantern
        for x in range(2, 14):
            img.putpixel((x, 6), WOOD_DARK)
        pickaxe(img, 2, 6)
        for y in range(7, 12):
            for x in (11, 12, 13):
                img.putpixel((x, y), rgb("#f5c542") if 8 <= y <= 10 and x == 12 else rgb("#3d3d3d"))
    save(img, "block", "miners_bench_front.png" if front else "miners_bench_side.png")


def quarry_marker_item():
    img = Image.new("RGBA", (16, 16), T)
    # A wooden stake with a red and white flag
    for y in range(3, 16):
        img.putpixel((5, y), rgb("#8a5a2b"))
        img.putpixel((6, y), rgb("#6e4a26"))
    img.putpixel((5, 15), rgb("#55595e"))
    img.putpixel((6, 15), rgb("#55595e"))
    for y in range(2, 9):
        for x in range(7, 15 - (y - 2) // 2):
            img.putpixel((x, y), rgb("#d9362b") if (x + y) % 4 < 2 else rgb("#f2f2f2"))
    for y in range(2, 9):
        img.putpixel((7, y), rgb("#a3281f"))
    save(img, "item", "quarry_marker.png")


def field_marker_item():
    img = Image.new("RGBA", (16, 16), T)
    # A wooden stake with a green flag and a wheat ear
    for y in range(3, 16):
        img.putpixel((5, y), rgb("#8a5a2b"))
        img.putpixel((6, y), rgb("#6e4a26"))
    img.putpixel((5, 15), rgb("#55595e"))
    img.putpixel((6, 15), rgb("#55595e"))
    for y in range(2, 9):
        for x in range(7, 15 - (y - 2) // 2):
            img.putpixel((x, y), rgb("#4f9e2f") if (x + y) % 4 < 2 else rgb("#6fbf3f"))
    for y in range(2, 9):
        img.putpixel((7, y), rgb("#3a7a22"))
    for (x, y) in ((10, 4), (11, 5), (10, 6), (11, 7), (12, 4)):
        img.putpixel((x, y), rgb("#e0c55a"))
    save(img, "item", "field_marker.png")


def miner_overlay():
    img = Image.new("RGBA", (64, 64), T)
    rnd = random.Random(21)
    hat, hat_shade, hat_edge, lamp, lamp_hi = rgb("#4f5357"), rgb("#3e4145"), rgb("#2b2d30"), rgb("#f5c542"), rgb("#fff3b0")
    for y in range(0, 8):
        for x in range(40, 48):
            img.putpixel((x, y), jitter(hat, rnd, 5))
    for y in range(8, 12):
        for x in range(32, 64):
            c = hat_edge if y == 11 else (hat_shade if y == 10 else hat)
            img.putpixel((x, y), jitter(c, rnd, 5))
    # Headlamp on the front of the helmet (front face of the hat layer is x 40..47, y 8..15)
    for x, y in ((43, 8), (44, 8), (43, 9), (44, 9)):
        img.putpixel((x, y), lamp_hi if y == 8 else lamp)
    img.putpixel((42, 9), rgb("#8f8f8f"))
    img.putpixel((45, 9), rgb("#8f8f8f"))
    for i in range(3, 13):
        for p in ((31 + i, 48 + 3), (31 + i, 48 + 12), (31 + 3, 48 + i), (31 + 12, 48 + i)):
            img.putpixel(p, hat_edge)
    # Leather apron and a belt with a pickaxe
    apron, apron_shade = rgb("#7a5230"), rgb("#5f3f24")
    for y in range(38, 44):
        for x in range(6, 14):
            img.putpixel((x, y), jitter(apron, rnd, 5))
    for y in range(44, 58):
        for x in range(6, 14):
            img.putpixel((x, y), jitter(apron_shade if y > 55 else apron, rnd, 5))
    for x in range(0, 28):
        img.putpixel((x, 55), jitter(rgb("#3d2616"), rnd, 3))
    for y in range(56, 61):
        img.putpixel((17, y), rgb("#7a4a22"))
    for x in (15, 16, 18, 19):
        img.putpixel((x, 56), rgb("#8f8f8f"))
    save(img, "entity", "villager", "profession", "miner.png")
    save(img, "entity", "zombie_villager", "profession", "miner.png")


# --- Chopping Block: a stump with an axe in it --------------------------------------------------
BARK = [rgb("#5b4027"), rgb("#4d3520"), rgb("#6a4a2d")]
RINGS = [rgb("#c9a26b"), rgb("#b58d57")]


def axe(img, ox, oy):
    """A small axe with its head at (ox, oy), handle running down-left."""
    for i in range(6):
        img.putpixel((ox - 1 - i, oy + 2 + i), rgb("#8a5a2b"))
    for x, y in ((0, 0), (1, 0), (2, 0), (0, 1), (1, 1), (2, 1), (3, 1), (0, 2), (1, 2), (2, 2), (0, 3)):
        img.putpixel((ox - 1 + x, oy - 1 + y), rgb("#c8c8c8") if (x + y) % 3 else rgb("#8f8f8f"))


def chopping_top():
    rnd = random.Random(31)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            r = max(abs(x - 7.5), abs(y - 7.5))
            if r > 6.5:
                c = BARK[(x + y) % 3]
            else:
                c = RINGS[int(r) % 2]
            img.putpixel((x, y), jitter(c, rnd, 5))
    for x in range(2, 14):  # the notch the axe left
        img.putpixel((x, 7), rgb("#6e4a26"))
    axe(img, 11, 5)
    save(img, "block", "chopping_block_top.png")


def chopping_side(front=False):
    rnd = random.Random(32 if front else 33)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = BARK[(x // 3 + y // 5) % 3]
            if x % 5 == 0:
                c = rgb("#3e2a18")
            img.putpixel((x, y), jitter(c, rnd, 6))
    if front:
        for x in range(4, 12):
            img.putpixel((x, 3), rgb("#c9a26b"))
        axe(img, 12, 1)
    save(img, "block", "chopping_block_front.png" if front else "chopping_block_side.png")


def lumberjack_overlay():
    img = Image.new("RGBA", (64, 64), T)
    rnd = random.Random(41)
    hat, hat_shade, fold = rgb("#2f6b3a"), rgb("#255630"), rgb("#1e4527")
    # A green knit beanie
    for y in range(0, 8):
        for x in range(40, 48):
            img.putpixel((x, y), jitter(hat, rnd, 5))
    for y in range(8, 12):
        for x in range(32, 64):
            c = fold if y >= 10 else (hat_shade if (x % 2) else hat)
            img.putpixel((x, y), jitter(c, rnd, 5))
    # A red and black plaid vest over the robe
    red, dark = rgb("#b3262c"), rgb("#2a1a1a")
    for y in range(38, 44):
        for x in range(6, 14):
            img.putpixel((x, y), red if (x // 2 + y // 2) % 2 else dark)
    for y in range(44, 56):
        for x in range(0, 28):
            if 6 <= x < 14 and x in (9, 10):
                continue
            plaid = (x // 2) % 2 == 0 or (y // 2) % 2 == 0
            img.putpixel((x, y), jitter(red if plaid else dark, rnd, 4))
    for x in range(0, 28):
        img.putpixel((x, 56), jitter(rgb("#4e2f1a"), rnd, 3))
    save(img, "entity", "villager", "profession", "lumberjack.png")
    save(img, "entity", "zombie_villager", "profession", "lumberjack.png")


# --- Postal Desk and Mailbox ----------------------------------------------------------------------
def postal_desk_top():
    rnd = random.Random(51)
    img = Image.new("RGBA", (16, 16))
    planks(img, rnd)
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, WOOD_EDGE)
    # Green blotter with two envelopes and a stamp
    for y in range(3, 12):
        for x in range(2, 11):
            img.putpixel((x, y), jitter(rgb("#3f6b45"), rnd, 4))
    for (ex, ey) in ((3, 4), (5, 8)):
        for y in range(ey, ey + 3):
            for x in range(ex, ex + 5):
                img.putpixel((x, y), jitter(rgb("#ece6d4"), rnd, 3))
        img.putpixel((ex + 4, ey), rgb("#c0392b"))
        img.putpixel((ex + 1, ey + 1), rgb("#b8ad94"))
        img.putpixel((ex + 2, ey + 1), rgb("#b8ad94"))
    # Ink pot and quill
    for (x, y) in ((12, 4), (13, 4), (12, 5), (13, 5)):
        img.putpixel((x, y), rgb("#1d1f2b"))
    for i in range(4):
        img.putpixel((13 - i, 9 + i), rgb("#f2f2f2"))
    save(img, "block", "postal_desk_top.png")


def postal_desk_side(front=False):
    rnd = random.Random(52 if front else 53)
    img = Image.new("RGBA", (16, 16))
    planks(img, rnd)
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, WOOD_EDGE)
    if front:
        # Pigeonholes with letters in some of them
        for cy in range(3):
            for cx in range(3):
                x0, y0 = 2 + cx * 4, 2 + cy * 4
                for y in range(y0, y0 + 4):
                    for x in range(x0, x0 + 4):
                        edge = x in (x0, x0 + 3) or y in (y0, y0 + 3)
                        img.putpixel((x, y), WOOD_EDGE if edge else rgb("#3b2714"))
                if (cx + cy) % 2 == 0:
                    for x in range(x0 + 1, x0 + 3):
                        img.putpixel((x, y0 + 2), rgb("#ece6d4"))
                        img.putpixel((x, y0 + 1), rgb("#ece6d4") if cx == 1 else rgb("#d9cfb4"))
    else:
        # A drawer with a brass handle
        for y in range(5, 11):
            for x in range(3, 13):
                edge = x in (3, 12) or y in (5, 10)
                if edge:
                    img.putpixel((x, y), WOOD_EDGE)
        img.putpixel((7, 7), rgb("#d4a93a"))
        img.putpixel((8, 7), rgb("#d4a93a"))
        img.putpixel((7, 8), rgb("#a67f22"))
        img.putpixel((8, 8), rgb("#a67f22"))
    save(img, "block", "postal_desk_front.png" if front else "postal_desk_side.png")


MAIL_BLUE = [rgb("#3a5f9e"), rgb("#355890"), rgb("#4068a8")]


def mailbox_textures():
    rnd = random.Random(61)
    side = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            side.putpixel((x, y), jitter(MAIL_BLUE[(x + y) % 3 == 0], rnd, 4))
    for i in range(16):
        side.putpixel((i, 0), rgb("#5a82c4"))
        side.putpixel((i, 15), rgb("#25406b"))
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        side.putpixel((x, y), rgb("#9fb3d6"))
    save(side, "block", "mailbox_side.png")

    front = side.copy()
    for x in range(4, 12):
        front.putpixel((x, 5), rgb("#10182a"))
        front.putpixel((x, 6), rgb("#1b2740"))
    for y in range(9, 12):
        for x in range(5, 11):
            front.putpixel((x, y), rgb("#e8e2cc") if y != 11 else rgb("#b8ad94"))
    front.putpixel((12, 10), rgb("#d4a93a"))
    save(front, "block", "mailbox_front.png")

    top = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            top.putpixel((x, y), jitter(rgb("#4a74b8") if x % 4 else rgb("#3f65a3"), rnd, 3))
    save(top, "block", "mailbox_top.png")

    post = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            post.putpixel((x, y), jitter(WOOD[1] if x % 3 else WOOD_DARK, rnd, 5))
    save(post, "block", "mailbox_post.png")

    flag = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            flag.putpixel((x, y), jitter(rgb("#c62828"), rnd, 6))
    save(flag, "block", "mailbox_flag.png")


def postman_overlay():
    img = Image.new("RGBA", (64, 64), T)
    rnd = random.Random(71)
    cap, cap_shade, band = rgb("#2b4c8c"), rgb("#233f75"), rgb("#1a1a1a")
    # A navy peaked cap with a gold badge
    for y in range(0, 8):
        for x in range(40, 48):
            img.putpixel((x, y), jitter(cap, rnd, 4))
    for y in range(8, 12):
        for x in range(32, 64):
            c = band if y == 11 else (cap_shade if y == 10 else cap)
            img.putpixel((x, y), jitter(c, rnd, 4))
    img.putpixel((43, 9), rgb("#e0b83a"))
    img.putpixel((44, 9), rgb("#e0b83a"))
    # Visor on the rim ring (front edge)
    for i in range(3, 13):
        img.putpixel((31 + i, 48 + 3), band)
    # Leather satchel strap across the robe and the bag on the hip
    strap, bag, bag_dark = rgb("#7a4a22"), rgb("#8f5a2c"), rgb("#5e3a1a")
    for y in range(44, 56):
        x = 6 + (y - 44) * 8 // 12
        img.putpixel((x, y), strap)
        img.putpixel((min(x + 1, 13), y), strap)
    for y in range(50, 57):
        for x in range(14, 20):
            img.putpixel((x, y), jitter(bag_dark if y in (50, 56) or x in (14, 19) else bag, rnd, 4))
    img.putpixel((16, 52), rgb("#ece6d4"))
    img.putpixel((17, 52), rgb("#ece6d4"))
    save(img, "entity", "villager", "profession", "postman.png")
    save(img, "entity", "zombie_villager", "profession", "postman.png")


# --- Guard Post: a weapon rack with a sword and a shield ------------------------------------------
def guard_post_top():
    rnd = random.Random(81)
    img = Image.new("RGBA", (16, 16))
    planks(img, rnd)
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, WOOD_EDGE)
    # Two iron bands across the top
    for x in range(16):
        for y in (4, 11):
            img.putpixel((x, y), jitter(rgb("#8f9499"), rnd, 5))
    save(img, "block", "guard_post_top.png")


def guard_post_side(front=False):
    rnd = random.Random(82 if front else 83)
    img = Image.new("RGBA", (16, 16))
    planks(img, rnd)
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, WOOD_EDGE)
    if front:
        # A sword hanging point-down next to a round blue shield with a gold boss
        for y in range(2, 13):
            img.putpixel((4, y), rgb("#d9dde0") if y < 10 else rgb("#6e4a26"))
            img.putpixel((5, y), rgb("#b8bec3") if y < 10 else rgb("#553619"))
        for x in range(2, 8):
            img.putpixel((x, 10), rgb("#8f9499"))
        for y in range(3, 14):
            for x in range(8, 15):
                dx, dy = x - 11, y - 8.5
                if dx * dx + dy * dy <= 11:
                    img.putpixel((x, y), jitter(rgb("#2b4c8c") if dx * dx + dy * dy <= 7 else rgb("#8f9499"), rnd, 4))
        img.putpixel((11, 8), rgb("#e0b83a"))
        img.putpixel((11, 9), rgb("#c89d2a"))
    else:
        # Iron studs
        for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12), (7, 7), (8, 8)):
            img.putpixel((x, y), rgb("#8f9499"))
    save(img, "block", "guard_post_front.png" if front else "guard_post_side.png")


def guard_overlay():
    img = Image.new("RGBA", (64, 64), T)
    rnd = random.Random(91)
    iron, iron_shade, iron_dark = rgb("#c8cdd1"), rgb("#a9afb4"), rgb("#7d8388")
    # An iron helmet
    for y in range(0, 8):
        for x in range(40, 48):
            img.putpixel((x, y), jitter(iron, rnd, 5))
    for y in range(8, 13):
        for x in range(32, 64):
            c = iron_dark if y == 12 else (iron_shade if y >= 11 else iron)
            img.putpixel((x, y), jitter(c, rnd, 4))
    for y in range(8, 12):  # nose guard
        img.putpixel((43, y), iron_dark)
        img.putpixel((44, y), iron_dark)
    # A blue tabard with a gold stripe over the robe
    blue, blue_dark, gold = rgb("#2b4c8c"), rgb("#223d70"), rgb("#e0b83a")
    for y in range(38, 44):
        for x in range(6, 14):
            img.putpixel((x, y), jitter(blue, rnd, 4))
    for y in range(44, 58):
        for x in range(0, 28):
            c = gold if x in (9, 10) else (blue_dark if y == 57 else blue)
            img.putpixel((x, y), jitter(c, rnd, 4))
    save(img, "entity", "villager", "profession", "guard.png")
    save(img, "entity", "zombie_villager", "profession", "guard.png")


# --- Nurse Station: a white counter with a pink heart, tonics on the front -------------------------
PINK, PINK_DARK = rgb("#e86a9a"), rgb("#c24d7c")


def heart(img, ox, oy):
    shape = ["01100110", "11111111", "11111111", "01111110", "00111100", "00011000"]
    for y, row in enumerate(shape):
        for x, c in enumerate(row):
            if c == "1":
                img.putpixel((ox + x, oy + y), PINK if y < 4 else PINK_DARK)


def nurse_station_top():
    rnd = random.Random(101)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), jitter(rgb("#f1efe9"), rnd, 4))
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, rgb("#c9c4b8"))
    heart(img, 4, 5)
    save(img, "block", "nurse_station_top.png")


def nurse_station_side(front=False):
    rnd = random.Random(102 if front else 103)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), jitter(rgb("#ebe8e0"), rnd, 4))
    for i in range(16):
        img.putpixel((i, 0), rgb("#c9c4b8"))
        img.putpixel((i, 15), rgb("#a9a498"))
    for x in range(16):
        img.putpixel((x, 7), PINK_DARK)
    if front:
        # A shelf of tonic bottles
        for i, col in enumerate((rgb("#e86a9a"), rgb("#6ac1e8"), rgb("#e8d56a"), rgb("#e86a9a"))):
            x0 = 2 + i * 3
            for y in range(9, 14):
                for x in range(x0, x0 + 2):
                    img.putpixel((x, y), col if y > 10 else rgb("#d9f2f7"))
            img.putpixel((x0, 8), rgb("#8a5a2b"))
            img.putpixel((x0 + 1, 8), rgb("#8a5a2b"))
        heart(img, 4, 1)
    save(img, "block", "nurse_station_front.png" if front else "nurse_station_side.png")


def nurse_overlay():
    img = Image.new("RGBA", (64, 64), T)
    rnd = random.Random(111)
    white, shade = rgb("#f4f4f2"), rgb("#dcdcd8")
    # A small white cap with a pink heart on the front
    for y in range(0, 8):
        for x in range(40, 48):
            img.putpixel((x, y), jitter(white, rnd, 3))
    for y in range(8, 11):
        for x in range(32, 64):
            img.putpixel((x, y), jitter(shade if y == 10 else white, rnd, 3))
    img.putpixel((43, 9), PINK)
    img.putpixel((44, 9), PINK)
    # A white apron with a pink trim over the robe
    for y in range(44, 58):
        for x in range(6, 14):
            img.putpixel((x, y), jitter(white if y < 57 else PINK, rnd, 3))
    for y in range(38, 44):
        for x in range(6, 14):
            img.putpixel((x, y), jitter(white, rnd, 3))
    save(img, "entity", "villager", "profession", "nurse.png")
    save(img, "entity", "zombie_villager", "profession", "nurse.png")


# --- Shop Counter: a dark wood counter with a till and a little scale ----------------------------------
SPRUCE = [rgb("#7a5a3a"), rgb("#6e5034"), rgb("#654a30")]


def spruce(img, rnd):
    for y in range(16):
        for x in range(16):
            base = SPRUCE[(y // 4) % 2] if (x + (y // 4) * 5) % 16 else rgb("#4f3a24")
            img.putpixel((x, y), jitter(base, rnd, 5))
        if y % 4 == 3:
            for x in range(16):
                img.putpixel((x, y), jitter(SPRUCE[2], rnd, 4))


def shop_counter_top():
    rnd = random.Random(121)
    img = Image.new("RGBA", (16, 16))
    spruce(img, rnd)
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, rgb("#3b2a19"))
    # A green felt mat with coins, and an emerald
    for y in range(3, 10):
        for x in range(3, 12):
            img.putpixel((x, y), jitter(rgb("#2f6b3a"), rnd, 4))
    for (x, y) in ((5, 5), (6, 5), (5, 6), (8, 7), (9, 7)):
        img.putpixel((x, y), rgb("#e0b83a"))
    for (x, y, c) in ((12, 11, "#17dd62"), (13, 11, "#0e9b44"), (12, 12, "#0e9b44"), (13, 12, "#17dd62")):
        img.putpixel((x, y), rgb(c))
    save(img, "block", "shop_counter_top.png")


def shop_counter_side(front=False):
    rnd = random.Random(122 if front else 123)
    img = Image.new("RGBA", (16, 16))
    spruce(img, rnd)
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, rgb("#3b2a19"))
    if front:
        # A striped awning over a price board
        for y in range(1, 5):
            for x in range(1, 15):
                img.putpixel((x, y), rgb("#c62828") if (x // 2) % 2 == 0 else rgb("#f2f2f2"))
        for y in range(7, 13):
            for x in range(3, 13):
                img.putpixel((x, y), rgb("#2a2a2a") if x in (3, 12) or y in (7, 12) else rgb("#3e4a3e"))
        for (x, y) in ((5, 9), (6, 9), (8, 9), (9, 10), (5, 11), (10, 9)):
            img.putpixel((x, y), rgb("#e8e2cc"))
    save(img, "block", "shop_counter_front.png" if front else "shop_counter_side.png")


def shopkeeper_overlay():
    img = Image.new("RGBA", (64, 64), T)
    rnd = random.Random(131)
    # A green visor band round the head
    for y in range(8, 10):
        for x in range(32, 64):
            img.putpixel((x, y), jitter(rgb("#2f8f4e"), rnd, 4))
    for i in range(3, 13):
        img.putpixel((31 + i, 48 + 3), rgb("#2f8f4e"))
    # A green apron with a pocket of coins
    for y in range(44, 58):
        for x in range(6, 14):
            img.putpixel((x, y), jitter(rgb("#3a8f55"), rnd, 4))
    for y in range(49, 52):
        for x in range(8, 12):
            img.putpixel((x, y), rgb("#2c6e41"))
    img.putpixel((9, 49), rgb("#e0b83a"))
    img.putpixel((10, 49), rgb("#e0b83a"))
    save(img, "entity", "villager", "profession", "shopkeeper.png")
    save(img, "entity", "zombie_villager", "profession", "shopkeeper.png")


# --- Travel Post and tickets -----------------------------------------------------------------------
def travel_post_top():
    rnd = random.Random(141)
    img = Image.new("RGBA", (16, 16))
    planks(img, rnd)
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, WOOD_EDGE)
    # A compass rose
    for i in range(3, 13):
        img.putpixel((7, i), rgb("#3b2714"))
        img.putpixel((i, 7), rgb("#3b2714"))
    img.putpixel((7, 3), rgb("#c62828"))
    img.putpixel((7, 4), rgb("#c62828"))
    save(img, "block", "travel_post_top.png")


def travel_post_side(front=False):
    rnd = random.Random(142 if front else 143)
    img = Image.new("RGBA", (16, 16))
    planks(img, rnd)
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, WOOD_EDGE)
    # Two arrow boards pointing different ways
    for y0, right in ((3, True), (9, False)):
        for y in range(y0, y0 + 4):
            for x in range(2, 14):
                img.putpixel((x, y), jitter(rgb("#d9c49a"), rnd, 4))
        tip = 14 if right else 1
        for dy in range(4):
            img.putpixel((tip, y0 + dy), rgb("#d9c49a") if dy in (1, 2) else WOOD_EDGE)
        for x in range(4, 12, 2):
            img.putpixel((x, y0 + 1 + (x // 2) % 2), rgb("#3b2714"))
    if front:
        for (x, y) in ((7, 0), (8, 0), (7, 15), (8, 15)):
            img.putpixel((x, y), rgb("#2b4c8c"))
    save(img, "block", "travel_post_front.png" if front else "travel_post_side.png")


def ticket_item():
    img = Image.new("RGBA", (16, 16), T)
    rnd = random.Random(151)
    for y in range(4, 12):
        for x in range(1, 15):
            edge = x in (1, 14) or y in (4, 11)
            img.putpixel((x, y), rgb("#b89b62") if edge else jitter(rgb("#eadcb4"), rnd, 4))
    for y in range(5, 11, 2):  # the perforated stub
        img.putpixel((11, y), rgb("#b89b62"))
    for x in range(3, 10):
        img.putpixel((x, 6), rgb("#2b4c8c"))
    for x in range(3, 8):
        img.putpixel((x, 8), rgb("#8a8a8a"))
    img.putpixel((12, 7), rgb("#c62828"))
    img.putpixel((13, 8), rgb("#c62828"))
    save(img, "item", "travel_ticket.png")


def ferryman_overlay():
    img = Image.new("RGBA", (64, 64), T)
    rnd = random.Random(161)
    straw, straw_dark = rgb("#e3c86b"), rgb("#b89b3e")
    # A straw hat
    for y in range(0, 8):
        for x in range(40, 48):
            img.putpixel((x, y), jitter(straw, rnd, 6))
    for y in range(8, 11):
        for x in range(32, 64):
            img.putpixel((x, y), jitter(straw_dark if y == 10 else straw, rnd, 5))
    for i in range(1, 15):
        for p in ((31 + i, 48 + 1), (31 + i, 48 + 14), (31 + 1, 48 + i), (31 + 14, 48 + i)):
            img.putpixel(p, straw_dark)
    # A blue and white striped shirt
    for y in range(44, 56):
        for x in range(0, 28):
            img.putpixel((x, y), jitter(rgb("#2b4c8c") if (y // 2) % 2 else rgb("#eeeeee"), rnd, 3))
    save(img, "entity", "villager", "profession", "ferryman.png")
    save(img, "entity", "zombie_villager", "profession", "ferryman.png")


def delivery_note_item():
    img = Image.new("RGBA", (16, 16), T)
    rnd = random.Random(171)
    for y in range(2, 14):
        for x in range(3, 13):
            edge = x in (3, 12) or y in (2, 13)
            img.putpixel((x, y), rgb("#b8ad94") if edge else jitter(rgb("#ece6d4"), rnd, 3))
    # An arrow from one box to another
    for (x, y) in ((5, 5), (6, 5), (5, 6), (6, 6)):
        img.putpixel((x, y), rgb("#8a5a2b"))
    for (x, y) in ((9, 10), (10, 10), (9, 11), (10, 11)):
        img.putpixel((x, y), rgb("#8a5a2b"))
    for i in range(6):
        img.putpixel((6 + i // 2, 7 + i // 2), rgb("#2b4c8c"))
    img.putpixel((9, 9), rgb("#2b4c8c"))
    img.putpixel((8, 9), rgb("#2b4c8c"))
    img.putpixel((9, 8), rgb("#2b4c8c"))
    save(img, "item", "delivery_note.png")


def price_tag_item():
    """A paper tag with a gold ring and a string, and a coin mark on it."""
    img = Image.new("RGBA", (16, 16), T)
    rnd = random.Random(173)
    # The tag: a paper rectangle cut to a point on the left
    for y in range(5, 12):
        for x in range(4, 14):
            cut = x - 4 < abs(y - 8) - 1
            if cut:
                continue
            edge = x == 13 or y in (5, 11) or x - 4 == abs(y - 8) - 1
            img.putpixel((x, y), rgb("#b8a27a") if edge else jitter(rgb("#f1e3bf"), rnd, 3))
    # The hole with a gold ring, and the string going up and away
    img.putpixel((6, 8), rgb("#e0b83a"))
    for (x, y) in ((5, 7), (4, 6), (3, 5), (3, 4), (2, 3), (2, 2)):
        img.putpixel((x, y), rgb("#8a8a8a"))
    # A gold coin mark
    for (x, y) in ((9, 7), (10, 7), (8, 8), (11, 8), (9, 9), (10, 9)):
        img.putpixel((x, y), rgb("#c99a1a"))
    img.putpixel((9, 8), rgb("#f2d15a"))
    img.putpixel((10, 8), rgb("#f2d15a"))
    save(img, "item", "price_tag.png")


# --- Music Stand: sheet music on a wooden stand ---------------------------------------------------------
def music_stand_top():
    rnd = random.Random(181)
    img = Image.new("RGBA", (16, 16))
    planks(img, rnd)
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, WOOD_EDGE)
    for y in range(3, 13):
        for x in range(3, 13):
            img.putpixel((x, y), jitter(rgb("#ece6d4"), rnd, 3))
    for y in (5, 8, 11):
        for x in range(4, 12):
            img.putpixel((x, y), rgb("#8a8a8a"))
    for (x, y) in ((5, 4), (8, 6), (10, 9), (6, 10)):
        img.putpixel((x, y), rgb("#1d1f2b"))
    save(img, "block", "music_stand_top.png")


def music_stand_side(front=False):
    rnd = random.Random(182 if front else 183)
    img = Image.new("RGBA", (16, 16))
    planks(img, rnd)
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, WOOD_EDGE)
    # A big note
    for y in range(3, 11):
        img.putpixel((10, y), rgb("#1d1f2b"))
    for (x, y) in ((11, 3), (12, 4), (12, 5), (7, 10), (8, 10), (9, 10), (7, 11), (8, 11), (9, 11), (8, 9)):
        img.putpixel((x, y), rgb("#1d1f2b"))
    if front:
        for x in range(2, 14):
            img.putpixel((x, 13), rgb("#c62828"))
    save(img, "block", "music_stand_front.png" if front else "music_stand_side.png")


def bard_overlay():
    img = Image.new("RGBA", (64, 64), T)
    rnd = random.Random(191)
    cap, feather = rgb("#7b2d8f"), rgb("#f2e6c9")
    # A purple cap with a feather
    for y in range(0, 8):
        for x in range(40, 48):
            img.putpixel((x, y), jitter(cap, rnd, 5))
    for y in range(8, 11):
        for x in range(32, 64):
            img.putpixel((x, y), jitter(cap, rnd, 5))
    for y in range(2, 8):
        img.putpixel((46 - y // 3, y), feather)
    # A purple and gold tunic
    for y in range(44, 56):
        for x in range(0, 28):
            img.putpixel((x, y), jitter(rgb("#e0b83a") if x % 7 == 3 else cap, rnd, 4))
    save(img, "entity", "villager", "profession", "bard.png")
    save(img, "entity", "zombie_villager", "profession", "bard.png")


# --- Training Post: a sparring post with a red and white target ---------------------------------------
def training_post_top():
    rnd = random.Random(201)
    img = Image.new("RGBA", (16, 16))
    planks(img, rnd)
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, WOOD_EDGE)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 6.5:
                img.putpixel((x, y), rgb("#c62828") if int(d) % 3 != 2 else rgb("#f2f2f2"))
    save(img, "block", "training_post_top.png")


def training_post_side(front=False):
    rnd = random.Random(202 if front else 203)
    img = Image.new("RGBA", (16, 16))
    planks(img, rnd)
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, WOOD_EDGE)
    # A red band round the post, and on the front a white star
    for y in (6, 7, 8, 9):
        for x in range(1, 15):
            img.putpixel((x, y), jitter(rgb("#c62828"), rnd, 5))
    if front:
        for (x, y) in ((7, 5), (8, 5), (6, 7), (7, 7), (8, 7), (9, 7), (7, 8), (8, 8), (6, 10), (9, 10), (7, 6), (8, 6), (7, 9), (8, 9)):
            img.putpixel((x, y), rgb("#f2f2f2"))
    save(img, "block", "training_post_front.png" if front else "training_post_side.png")


def trainer_overlay():
    img = Image.new("RGBA", (64, 64), T)
    rnd = random.Random(211)
    red, white = rgb("#c62828"), rgb("#f2f2f2")
    # A red and white cap
    for y in range(0, 8):
        for x in range(40, 48):
            img.putpixel((x, y), jitter(red if y < 5 else white, rnd, 4))
    for y in range(8, 11):
        for x in range(32, 64):
            img.putpixel((x, y), jitter(white if y == 10 else red, rnd, 4))
    for i in range(3, 13):
        img.putpixel((31 + i, 48 + 3), red)
    # A sporty jacket: blue with a white stripe
    for y in range(44, 56):
        for x in range(0, 28):
            img.putpixel((x, y), jitter(white if y in (48, 49) else rgb("#1f5fa8"), rnd, 4))
    save(img, "entity", "villager", "profession", "trainer.png")
    save(img, "entity", "zombie_villager", "profession", "trainer.png")


# --- Leader's Podium: polished stone with gold trim and a star -----------------------------------------
def leaders_podium(face):
    rnd = random.Random({"top": 221, "side": 222, "front": 223}[face])
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), jitter(rgb("#9a9d9f"), rnd, 5))
    gold = rgb("#e0b83a")
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, gold)
    if face in ("top", "front"):
        star = ["...##...", "...##...", "########", ".######.", "..####..", ".##..##.", "##....##"]
        for y, row in enumerate(star):
            for x, c in enumerate(row):
                if c == "#":
                    img.putpixel((4 + x, 4 + y), gold if face == "front" else rgb("#c62828"))
    save(img, "block", "leaders_podium_" + face + ".png")


def leader_overlay():
    img = Image.new("RGBA", (64, 64), T)
    rnd = random.Random(231)
    gold, dark = rgb("#e0b83a"), rgb("#2a2a2a")
    # A black cap with a gold band
    for y in range(0, 8):
        for x in range(40, 48):
            img.putpixel((x, y), jitter(dark, rnd, 4))
    for y in range(8, 11):
        for x in range(32, 64):
            img.putpixel((x, y), jitter(gold if y == 9 else dark, rnd, 4))
    for i in range(3, 13):
        img.putpixel((31 + i, 48 + 3), dark)
    # A long dark coat with gold buttons
    for y in range(44, 58):
        for x in range(0, 28):
            img.putpixel((x, y), jitter(rgb("#3a3a4a"), rnd, 4))
    for y in range(45, 57, 3):
        img.putpixel((9, y), gold)
        img.putpixel((10, y), gold)
    save(img, "entity", "villager", "profession", "trainer_leader.png")
    save(img, "entity", "zombie_villager", "profession", "trainer_leader.png")


# --- Tutor's Desk: an open book and an inkwell on a desk ------------------------------------------------
def tutors_desk_top():
    rnd = random.Random(241)
    img = Image.new("RGBA", (16, 16))
    planks(img, rnd)
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, WOOD_EDGE)
    # An open book: two pages with lines of text and a spine down the middle
    for y in range(3, 12):
        for x in range(2, 12):
            img.putpixel((x, y), jitter(rgb("#efe7d2"), rnd, 3))
    for y in range(3, 12):
        img.putpixel((7, y), rgb("#8a5a2b"))
    for y in (5, 7, 9):
        for x in list(range(3, 6)) + list(range(8, 11)):
            img.putpixel((x, y), rgb("#6d6d7a"))
    # An inkwell and a quill
    for (x, y) in ((12, 11), (13, 11), (12, 12), (13, 12)):
        img.putpixel((x, y), rgb("#1d1f2b"))
    for i in range(4):
        img.putpixel((13 - i // 2 + 1, 10 - i), rgb("#f2f2f2"))
    save(img, "block", "tutors_desk_top.png")


def tutors_desk_side(front=False):
    rnd = random.Random(242 if front else 243)
    img = Image.new("RGBA", (16, 16))
    planks(img, rnd)
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, WOOD_EDGE)
    # A row of book spines under the desk top
    colors = ["#a33b3b", "#3b5ea3", "#3b8a4a", "#c9a23a", "#6b3ba3"]
    for x in range(1, 15):
        c = rgb(colors[x % len(colors)])
        for y in range(3, 8 if x % 3 else 7):
            img.putpixel((x, y), jitter(c, rnd, 6))
    if front:
        # A drawer with a brass knob
        for x in range(3, 13):
            img.putpixel((x, 9), WOOD_EDGE)
            img.putpixel((x, 13), WOOD_EDGE)
        for y in range(9, 14):
            img.putpixel((3, y), WOOD_EDGE)
            img.putpixel((12, y), WOOD_EDGE)
        img.putpixel((7, 11), rgb("#e0b83a"))
        img.putpixel((8, 11), rgb("#e0b83a"))
    save(img, "block", "tutors_desk_front.png" if front else "tutors_desk_side.png")


def tutor_overlay():
    img = Image.new("RGBA", (64, 64), T)
    rnd = random.Random(251)
    black, gold, robe = rgb("#1f1f24"), rgb("#e0b83a"), rgb("#2f5d3a")
    # A mortarboard with a gold tassel
    for y in range(0, 8):
        for x in range(40, 48):
            img.putpixel((x, y), jitter(black, rnd, 3))
    img.putpixel((43, 3), gold)
    img.putpixel((44, 4), gold)
    for y in range(8, 10):
        for x in range(32, 64):
            img.putpixel((x, y), jitter(black, rnd, 3))
    for y in (10, 11):
        img.putpixel((47, y), gold)
    # A green scholar's robe with a white collar
    for y in range(44, 58):
        for x in range(0, 28):
            img.putpixel((x, y), jitter(rgb("#f2f2f2") if y == 44 else robe, rnd, 4))
    save(img, "entity", "villager", "profession", "tutor.png")
    save(img, "entity", "zombie_villager", "profession", "tutor.png")


# --- Trade Board: a cork board with notes pinned to it -------------------------------------------------
def trade_board(face):
    rnd = random.Random({"top": 261, "side": 262, "front": 263}[face])
    img = Image.new("RGBA", (16, 16))
    planks(img, rnd)
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, WOOD_EDGE)
    if face in ("side", "front"):
        # Cork
        for y in range(2, 14):
            for x in range(2, 14):
                img.putpixel((x, y), jitter(rgb("#b88a55"), rnd, 10))
    if face == "front":
        # Three notes, each with a red pin and a line of writing
        for (nx, ny) in ((3, 3), (9, 4), (5, 9)):
            for y in range(ny, ny + 4):
                for x in range(nx, nx + 4):
                    img.putpixel((x, y), jitter(rgb("#f2ecd9"), rnd, 3))
            img.putpixel((nx + 1, ny), rgb("#c62828"))
            img.putpixel((nx + 1, ny + 2), rgb("#6d6d7a"))
            img.putpixel((nx + 2, ny + 2), rgb("#6d6d7a"))
    if face == "side":
        img.putpixel((7, 7), rgb("#f2ecd9"))
        img.putpixel((8, 7), rgb("#f2ecd9"))
    save(img, "block", "trade_board_" + face + ".png")


def pokemon_trader_overlay():
    img = Image.new("RGBA", (64, 64), T)
    rnd = random.Random(271)
    teal, strap, white = rgb("#1f7a78"), rgb("#6b4a2b"), rgb("#f2f2f2")
    # A teal cap with a white peak line
    for y in range(0, 8):
        for x in range(40, 48):
            img.putpixel((x, y), jitter(teal, rnd, 4))
    for y in range(8, 11):
        for x in range(32, 64):
            img.putpixel((x, y), jitter(white if y == 10 else teal, rnd, 4))
    # A teal waistcoat with a satchel strap across it
    for y in range(44, 56):
        for x in range(0, 28):
            img.putpixel((x, y), jitter(teal, rnd, 4))
    for i in range(12):
        img.putpixel((2 + i, 44 + i), strap)
        img.putpixel((3 + i, 44 + i), strap)
    save(img, "entity", "villager", "profession", "pokemon_trader.png")
    save(img, "entity", "zombie_villager", "profession", "pokemon_trader.png")


# --- Fruit Basket: a wicker basket heaped with fruit ---------------------------------------------------
WICKER = [rgb("#c9a063"), rgb("#b08546"), rgb("#9a7038")]


def wicker(img, rnd, y0=0, y1=16):
    for y in range(y0, y1):
        for x in range(16):
            weave = ((x // 2) + (y // 2)) % 2
            c = WICKER[weave] if (x + y) % 5 else WICKER[2]
            img.putpixel((x, y), jitter(c, rnd, 6))


def fruit_basket(face):
    rnd = random.Random({"top": 281, "side": 282, "front": 283}[face])
    img = Image.new("RGBA", (16, 16))
    wicker(img, rnd)
    rim = rgb("#7a5328")
    if face == "top":
        # Fruit heaped inside the rim: apples, berries, a yellow apricorn and a cocoa pod
        for y in range(2, 14):
            for x in range(2, 14):
                img.putpixel((x, y), jitter(rgb("#5e3d1c"), rnd, 4))
        fruit = [((3, 3), rgb("#c62828")), ((8, 2), rgb("#e8b923")), ((3, 8), rgb("#2e7d32")),
                 ((8, 8), rgb("#c62828")), ((10, 5), rgb("#1565c0")), ((6, 10), rgb("#e07a1f"))]
        for (fx, fy), c in fruit:
            for y in range(fy, fy + 3):
                for x in range(fx, fx + 3):
                    if (x - fx, y - fy) not in ((0, 0), (2, 0), (0, 2), (2, 2)):
                        img.putpixel((x, y), jitter(c, rnd, 10))
            img.putpixel((fx + 1, fy + 1), tuple(min(255, v + 60) for v in c[:3]) + (255,))  # shine
        for (bx, by) in ((12, 11), (11, 12), (12, 12), (5, 5), (6, 6)):
            img.putpixel((bx, by), rgb("#8e1b3a"))
        for i in range(16):
            for p in ((i, 0), (i, 15), (0, i), (15, i), (i, 1), (i, 14), (1, i), (14, i)):
                img.putpixel(p, jitter(rim, rnd, 5))
    else:
        for x in range(16):
            img.putpixel((x, 0), jitter(rim, rnd, 5))
            img.putpixel((x, 1), jitter(rim, rnd, 5))
            img.putpixel((x, 15), jitter(rim, rnd, 5))
        if face == "front":
            # A little wooden tag with a leaf on it
            for y in range(6, 11):
                for x in range(5, 11):
                    img.putpixel((x, y), jitter(rgb("#e8d5a8"), rnd, 3))
            for (lx, ly) in ((7, 8), (8, 7), (8, 8), (9, 7)):
                img.putpixel((lx, ly), rgb("#3f8a3a"))
            img.putpixel((7, 9), rgb("#6b4a2b"))
    save(img, "block", "fruit_basket_" + face + ".png")


def orchard_keeper_overlay():
    img = Image.new("RGBA", (64, 64), T)
    rnd = random.Random(291)
    straw, straw_dark, band = rgb("#e3c26b"), rgb("#c9a44a"), rgb("#b3262c")
    # A wide straw sun hat with a red band
    for y in range(0, 8):
        for x in range(40, 48):
            img.putpixel((x, y), jitter(straw if (x + y) % 3 else straw_dark, rnd, 5))
    for y in range(8, 12):
        for x in range(32, 64):
            c = band if y == 8 else (straw if (x + y) % 3 else straw_dark)
            img.putpixel((x, y), jitter(c, rnd, 5))
    # A green gardening apron with a fruit-stained pocket
    apron, pocket = rgb("#4e7d3a"), rgb("#3e6630")
    for y in range(44, 58):
        for x in range(4, 24):
            img.putpixel((x, y), jitter(apron, rnd, 4))
    for y in range(49, 54):
        for x in range(8, 14):
            img.putpixel((x, y), jitter(pocket, rnd, 3))
    img.putpixel((10, 50), rgb("#c62828"))
    img.putpixel((12, 51), rgb("#8e1b3a"))
    save(img, "entity", "villager", "profession", "orchard_keeper.png")
    save(img, "entity", "zombie_villager", "profession", "orchard_keeper.png")


# --- Ball Workbench: a smith's bench with Poké Ball halves on it ---------------------------------------
def ball_workbench(face):
    rnd = random.Random({"top": 301, "side": 302, "front": 303}[face])
    img = Image.new("RGBA", (16, 16))
    planks(img, rnd)
    iron, iron_dark = rgb("#9aa0a6"), rgb("#6b7075")
    if face == "top":
        # A steel plate with a finished ball and two halves waiting to be joined
        for y in range(1, 15):
            for x in range(1, 15):
                img.putpixel((x, y), jitter(iron if (x + y) % 7 else iron_dark, rnd, 5))

        def ball(cx, cy, top, bottom=rgb("#f2f2f2"), r=3):
            for y in range(cy - r, cy + r + 1):
                for x in range(cx - r, cx + r + 1):
                    if (x - cx) ** 2 + (y - cy) ** 2 <= r * r + 1:
                        c = top if y < cy else (rgb("#1f1f1f") if y == cy else bottom)
                        img.putpixel((x, y), c)
            img.putpixel((cx, cy), rgb("#f2f2f2"))
        ball(5, 5, rgb("#d32f2f"))
        ball(11, 10, rgb("#1e63c4"))
        for x in range(9, 14):
            img.putpixel((x, 3), rgb("#e8b923"))  # a gold ingot
            img.putpixel((x, 4), rgb("#c99a14"))
    else:
        for x in range(16):
            for y in (0, 1):
                img.putpixel((x, y), jitter(iron_dark, rnd, 4))
        if face == "front":
            # A red and white ball painted on the front
            for y in range(6, 13):
                for x in range(5, 12):
                    if (x - 8) ** 2 + (y - 9) ** 2 <= 10:
                        img.putpixel((x, y), rgb("#d32f2f") if y < 9 else (rgb("#1f1f1f") if y == 9 else rgb("#f2f2f2")))
            img.putpixel((8, 9), rgb("#f2f2f2"))
    save(img, "block", "ball_workbench_" + face + ".png")


def ball_smith_overlay():
    img = Image.new("RGBA", (64, 64), T)
    rnd = random.Random(311)
    leather, dark, lens = rgb("#7a4a26"), rgb("#4e2f1a"), rgb("#8fd3ff")
    # A leather cap with goggles pushed up on it
    for y in range(0, 8):
        for x in range(40, 48):
            img.putpixel((x, y), jitter(leather, rnd, 5))
    for y in range(8, 10):
        for x in range(32, 64):
            img.putpixel((x, y), jitter(dark if y == 9 else leather, rnd, 4))
    for x in (41, 42, 45, 46):
        img.putpixel((x, 8), lens)
    # A leather apron with a red and white ball stitched on
    for y in range(44, 58):
        for x in range(4, 24):
            img.putpixel((x, y), jitter(leather, rnd, 4))
    for y in range(48, 53):
        for x in range(10, 15):
            if (x - 12) ** 2 + (y - 50) ** 2 <= 5:
                img.putpixel((x, y), rgb("#d32f2f") if y < 50 else (rgb("#1f1f1f") if y == 50 else rgb("#f2f2f2")))
    save(img, "entity", "villager", "profession", "ball_smith.png")
    save(img, "entity", "zombie_villager", "profession", "ball_smith.png")


# --- Storehouse: a stack of crates with the porter's ledger -------------------------------------------
def storehouse(face):
    rnd = random.Random({"top": 321, "side": 322, "front": 323}[face])
    img = Image.new("RGBA", (16, 16))
    planks(img, rnd)
    frame, frame_dark = rgb("#6e4a26"), rgb("#553619")
    rope, rope_dark = rgb("#d8c08a"), rgb("#a8905a")
    # A dark frame round the edge: every face is the side of a crate
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, jitter(frame_dark, rnd, 4))
        for p in ((i, 1), (i, 14), (1, i), (14, i)):
            img.putpixel(p, jitter(frame, rnd, 4))
    if face == "top":
        # The lid: two cross battens and a rope tied round it
        for i in range(2, 14):
            img.putpixel((i, 7), jitter(frame, rnd, 4))
            img.putpixel((i, 8), jitter(frame_dark, rnd, 4))
            img.putpixel((7, i), jitter(rope, rnd, 6))
            img.putpixel((8, i), jitter(rope_dark, rnd, 6))
        for (x, y) in ((6, 6), (9, 6), (6, 9), (9, 9), (7, 7), (8, 8)):
            img.putpixel((x, y), rope)
    elif face == "side":
        # A diagonal brace, like a shipping crate
        for i in range(2, 14):
            img.putpixel((i, 15 - i), jitter(frame, rnd, 4))
            img.putpixel((i, 16 - i if i > 2 else 13), jitter(frame_dark, rnd, 4))
    else:
        # The ledger hanging on a nail: a paper board with lines and a tally
        paper, ink = rgb("#ece3c8"), rgb("#4a3b2a")
        for y in range(4, 13):
            for x in range(4, 12):
                img.putpixel((x, y), jitter(paper, rnd, 3))
        for x in range(4, 12):
            img.putpixel((x, 4), jitter(frame_dark, rnd, 3))  # the clip
        for y in (6, 8, 10):
            for x in range(5, 11):
                if (x + y) % 4:
                    img.putpixel((x, y), ink)
        for x in (8, 9, 10):
            img.putpixel((x, 11), rgb("#b3262c"))  # a red tick at the bottom
        img.putpixel((7, 3), rgb("#9aa0a6"))
        img.putpixel((8, 3), rgb("#9aa0a6"))
    save(img, "block", "storehouse_" + face + ".png")


def porter_overlay():
    img = Image.new("RGBA", (64, 64), T)
    rnd = random.Random(331)
    cap, cap_dark = rgb("#6b5a3e"), rgb("#4f422d")
    # A flat wool cap
    for y in range(0, 8):
        for x in range(40, 48):
            img.putpixel((x, y), jitter(cap, rnd, 5))
    for y in range(8, 11):
        for x in range(32, 64):
            img.putpixel((x, y), jitter(cap_dark if y == 10 else cap, rnd, 5))
    for i in range(3, 13):
        img.putpixel((31 + i, 48 + 3), cap_dark)  # the peak
    # A canvas vest with leather carrying straps and a coil of rope on the belt
    vest, strap, rope = rgb("#8c8a6a"), rgb("#6b4226"), rgb("#d8c08a")
    for y in range(44, 58):
        for x in range(4, 24):
            img.putpixel((x, y), jitter(vest, rnd, 5))
    for y in range(44, 58):
        for x in (7, 8, 19, 20):
            img.putpixel((x, y), jitter(strap, rnd, 4))
    for x in range(4, 24):
        img.putpixel((x, 53), jitter(strap, rnd, 3))
    for (x, y) in ((12, 54), (13, 54), (14, 54), (11, 55), (15, 55), (12, 56), (13, 56), (14, 56)):
        img.putpixel((x, y), rope)
    save(img, "entity", "villager", "profession", "porter.png")
    save(img, "entity", "zombie_villager", "profession", "porter.png")


# --- Carpenter's Bench: a woodworking bench with a saw, a square and shavings -----------------------------
def carpenters_bench(face):
    rnd = random.Random({"top": 341, "side": 342, "front": 343}[face])
    img = Image.new("RGBA", (16, 16))
    planks(img, rnd)
    steel, steel_dark, handle = rgb("#b9bfc4"), rgb("#7d8489"), rgb("#8a5a2b")
    if face == "top":
        # A lighter worktop with a hand saw lying across it and curls of shavings
        for y in range(1, 15):
            for x in range(1, 15):
                img.putpixel((x, y), jitter(rgb("#c9a06a") if (x + y) % 6 else rgb("#b88f5b"), rnd, 5))
        for i in range(8):
            x, y = 3 + i, 4 + i // 2
            img.putpixel((x, y), steel)
            img.putpixel((x, y + 1), steel_dark if i % 2 else steel)
        for (x, y) in ((11, 8), (12, 8), (11, 9), (12, 9), (13, 9)):
            img.putpixel((x, y), handle)
        for (x, y) in ((4, 11), (5, 12), (6, 11), (9, 12), (10, 13), (12, 3), (13, 4)):
            img.putpixel((x, y), rgb("#e8cf9c"))  # shavings
        # A carpenter's square in the corner
        for i in range(2, 7):
            img.putpixel((i, 2), steel_dark)
            img.putpixel((2, i), steel_dark)
    else:
        leg = rgb("#6e4a26")
        for y in range(16):
            for x in (1, 2, 13, 14):
                img.putpixel((x, y), jitter(leg, rnd, 4))
        for x in range(16):
            img.putpixel((x, 0), jitter(rgb("#553619"), rnd, 3))
            img.putpixel((x, 1), jitter(rgb("#6e4a26"), rnd, 3))
        if face == "front":
            # A vise on the front and a hammer hanging under the top
            for y in range(3, 7):
                for x in range(5, 11):
                    img.putpixel((x, y), jitter(steel_dark if y in (3, 6) else steel, rnd, 4))
            img.putpixel((7, 7), steel_dark)
            img.putpixel((8, 7), steel_dark)
            for y in range(9, 14):
                img.putpixel((8, y), handle)
            for x in range(6, 11):
                img.putpixel((x, 9), steel_dark)
    save(img, "block", "carpenters_bench_" + face + ".png")


def carpenter_overlay():
    img = Image.new("RGBA", (64, 64), T)
    rnd = random.Random(351)
    # A pencil behind the ear (on the side of the head) and a canvas apron with a hammer loop and a folding rule
    for (x, y) in ((33, 10), (34, 10), (35, 10)):
        img.putpixel((x, y), rgb("#e8b923"))
    img.putpixel((36, 10), rgb("#3a3a3a"))
    apron, pocket, rule = rgb("#b99a6b"), rgb("#9c7f55"), rgb("#e8b923")
    for y in range(44, 58):
        for x in range(4, 24):
            img.putpixel((x, y), jitter(apron, rnd, 5))
    for y in range(49, 54):
        for x in range(6, 12):
            img.putpixel((x, y), jitter(pocket, rnd, 3))
    for y in range(47, 53):
        img.putpixel((16, y), rule)
        img.putpixel((17, y), rule if y % 2 else rgb("#3a3a3a"))
    for x in range(4, 24):
        img.putpixel((x, 45), jitter(rgb("#6b4226"), rnd, 3))
    save(img, "entity", "villager", "profession", "carpenter.png")
    save(img, "entity", "zombie_villager", "profession", "carpenter.png")


# --- Kitchen Stove: an iron range with a pot on top and a fire in the oven -------------------------------
def kitchen_stove(face):
    rnd = random.Random({"top": 361, "side": 362, "front": 363}[face])
    img = Image.new("RGBA", (16, 16))
    iron, iron_dark, iron_light = rgb("#4a4d52"), rgb("#2f3134"), rgb("#6b6f75")
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), jitter(iron if (x + y) % 5 else iron_dark, rnd, 5))
    for i in range(16):
        for p in ((i, 0), (0, i), (15, i), (i, 15)):
            img.putpixel(p, jitter(iron_dark, rnd, 3))
    if face == "top":
        # A copper pot of stew on one burner, a ring on the other
        for y in range(2, 11):
            for x in range(2, 11):
                d = (x - 6) ** 2 + (y - 6) ** 2
                if d <= 16:
                    img.putpixel((x, y), jitter(rgb("#b8733a") if d > 9 else rgb("#d98a2b"), rnd, 6))
        for (x, y) in ((5, 5), (7, 6), (6, 7)):
            img.putpixel((x, y), rgb("#f3c35a"))  # bubbles
        for y in range(9, 15):
            for x in range(9, 15):
                d = (x - 11.5) ** 2 + (y - 11.5) ** 2
                if 4 <= d <= 9:
                    img.putpixel((x, y), rgb("#c0392b"))
    else:
        for x in range(1, 15):
            img.putpixel((x, 2), jitter(iron_light, rnd, 4))
        if face == "front":
            # The oven door: a window onto the fire and a brass handle
            for y in range(5, 13):
                for x in range(3, 13):
                    img.putpixel((x, y), jitter(iron_dark, rnd, 3))
            for y in range(7, 11):
                for x in range(5, 11):
                    img.putpixel((x, y), jitter(rgb("#f39c12") if y > 8 else rgb("#e74c3c"), rnd, 10))
            for x in range(5, 11):
                img.putpixel((x, 4), rgb("#c9a227"))
        else:
            for y in (6, 8, 10):
                for x in range(4, 12):
                    img.putpixel((x, y), iron_dark)  # vents
    save(img, "block", "kitchen_stove_" + face + ".png")


def chef_overlay():
    img = Image.new("RGBA", (64, 64), T)
    rnd = random.Random(371)
    white, shade = rgb("#f4f4f4"), rgb("#d9d9d9")
    # A tall white chef's hat
    for y in range(0, 8):
        for x in range(40, 48):
            img.putpixel((x, y), jitter(white if (x + y) % 4 else shade, rnd, 3))
    for y in range(8, 16):
        for x in range(32, 64):
            img.putpixel((x, y), jitter(white if (x * 3 + y) % 7 else shade, rnd, 3))
    # A white apron with a red neckerchief
    for y in range(44, 58):
        for x in range(4, 24):
            img.putpixel((x, y), jitter(white if (x + y) % 6 else shade, rnd, 3))
    for x in range(8, 20):
        img.putpixel((x, 44), rgb("#c0392b"))
        img.putpixel((x, 45), rgb("#a93226"))
    save(img, "entity", "villager", "profession", "chef.png")
    save(img, "entity", "zombie_villager", "profession", "chef.png")


def icon(blueprint):
    big = Image.new("RGBA", (128, 128), T)
    scaled = blueprint.resize((128, 128), Image.NEAREST)
    big.alpha_composite(scaled)
    path = os.path.join(ROOT, "src/main/resources/assets/aliveworkplace/icon.png")
    big.save(path)
    print("wrote", os.path.relpath(path, ROOT))


if __name__ == "__main__":
    bench_top()
    bench_side(front=False)
    bench_side(front=True)
    icon(blueprint_item())
    builder_overlay()
    table_top()
    table_side(front=False)
    table_side(front=True)
    blank_blueprint_item()
    miners_bench_top()
    miners_bench_side(front=False)
    miners_bench_side(front=True)
    quarry_marker_item()
    field_marker_item()
    miner_overlay()
    chopping_top()
    chopping_side(front=False)
    chopping_side(front=True)
    lumberjack_overlay()
    postal_desk_top()
    postal_desk_side(front=False)
    postal_desk_side(front=True)
    mailbox_textures()
    postman_overlay()
    guard_post_top()
    guard_post_side(front=False)
    guard_post_side(front=True)
    guard_overlay()
    nurse_station_top()
    nurse_station_side(front=False)
    nurse_station_side(front=True)
    nurse_overlay()
    shop_counter_top()
    shop_counter_side(front=False)
    shop_counter_side(front=True)
    shopkeeper_overlay()
    travel_post_top()
    travel_post_side(front=False)
    travel_post_side(front=True)
    ticket_item()
    ferryman_overlay()
    delivery_note_item()
    price_tag_item()
    music_stand_top()
    music_stand_side(front=False)
    music_stand_side(front=True)
    bard_overlay()
    training_post_top()
    training_post_side(front=False)
    training_post_side(front=True)
    trainer_overlay()
    for face in ("top", "side", "front"):
        leaders_podium(face)
    leader_overlay()
    tutors_desk_top()
    tutors_desk_side(front=False)
    tutors_desk_side(front=True)
    tutor_overlay()
    for face in ("top", "side", "front"):
        trade_board(face)
    pokemon_trader_overlay()
    for face in ("top", "side", "front"):
        fruit_basket(face)
    orchard_keeper_overlay()
    for face in ("top", "side", "front"):
        ball_workbench(face)
    ball_smith_overlay()
    for face in ("top", "side", "front"):
        storehouse(face)
    porter_overlay()
    for face in ("top", "side", "front"):
        carpenters_bench(face)
    carpenter_overlay()
    for face in ("top", "side", "front"):
        kitchen_stove(face)
    chef_overlay()
