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
    miner_overlay()
    chopping_top()
    chopping_side(front=False)
    chopping_side(front=True)
    lumberjack_overlay()
