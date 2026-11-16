"""Copperline (ROADMAP 47.1, docs/design/copperline/): the GUI sprites every screen of the mod is drawn with.

- The nine-slices (window, plaque, panel, well (+focus, +error), tooltip, toast, button normal/hover/pressed/off,
  copper normal/hover/pressed, danger) are drawn here as letter grids on the Copperline palette, one grid per state, so
  the states of a piece share every pixel but the ones that change (a hover only adds the Lantern edge, a press only moves
  the light). Each gets its nine-slice .mcmeta.
- The 8 px status glyphs and the 16 px Hall section icons start from the designer's sheets (docs/design/copperline/
  sprites/glyphs.png, icons.png, our own art) and are locked onto the palette: every pixel is snapped to the nearest
  Copperline color, or to one of the few material colors the sheet uses for wood, wheat, grass and skin (MATERIALS
  below, each named), so no stray shade gets in.

Out: textures/gui/sprites/copperline/<name>.png (+.mcmeta), glyph/<name>.png and icon/<name>.png, used by the client's
CopperStyle as aliveworkplace:copperline/... sprites.
"""
import json

from artlib import ASSETS, ROOT
from PIL import Image

# The Copperline palette (docs/design/copperline/README.md), one letter each.
PALETTE = {
    "v": "0B0C10",  # Void: outlines, the deepest shadow
    "9": "16171D",  # Deepslate 900: window background, wells
    "8": "22242E",  # Deepslate 800: panels, cards
    "7": "2E313D",  # Deepslate 700: raised parts, hover
    "6": "3D4150",  # Deepslate 600: pressed, dividers
    "s": "50556A",  # Slate Line: lines, disabled outline
    "t": "A3A8B8",  # Tuff: second text
    "q": "F2EFE6",  # Quartz: main text
    "c": "E0864A",  # Copper: the plaque, the one main button, the selected mark
    "C": "F7B47C",  # Copper Light
    "d": "9C4F2B",  # Copper Dark
    "l": "FFD25E",  # Lantern: hover and focus, warnings
    "g": "4FC3A1",  # Verdigris: good
    "r": "FF6B5E",  # Redstone: bad, refused
    "b": "7FA8FF",  # Lapis: info
    "a": "C59BFF",  # Amethyst: rare
}
# The material colors the icon sheet draws things in (not used on any frame or text).
MATERIALS = {
    "6F4A2A": "dark wood", "B8854F": "wood", "D9AE74": "light wood, bread", "A8704F": "skin shade", "D8A27A": "skin",
    "E8C56A": "brass", "C98A1E": "gold shade", "6FBF4A": "grass", "3E7F2E": "grass shade", "1E5E50": "verdigris shade",
    "7C55C2": "amethyst shade", "7D8194": "stone",
}

OUT = ASSETS / "textures/gui/sprites/copperline"
SHEETS = ROOT / "docs/design/copperline/sprites"


def _rgb(hexs):
    return tuple(int(hexs[i:i + 2], 16) for i in (0, 2, 4))


def _grid(rows):
    rows = [r.strip() for r in rows.strip().splitlines()]
    img = Image.new("RGBA", (len(rows[0]), len(rows)), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == len(rows[0]), row
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), _rgb(PALETTE[ch]) + (255,))
    return img


# name: (grid, border left, top, right, bottom)
NINE = {
    # The window: a 2 px Void outline with cut corners and a copper line under its top edge (the plaque hangs over it).
    "window": ("""
        .vvvvvvvv.
        vvvvvvvvvv
        vvCCCCCCvv
        vvddddddvv
        vv999999vv
        vv999999vv
        vv999999vv
        vv999999vv
        vvvvvvvvvv
        .vvvvvvvv.""", 2, 4, 2, 2),
    # The plaque: copper, lit at the top and left, shaded at the bottom and right, framed in Void.
    "plaque": ("""
        .vvvvvvv.
        vvvvvvvvv
        vvCCCCCvv
        vvCcccdvv
        vvCcccdvv
        vvCcccdvv
        vvdddddvv
        vvvvvvvvv
        .vvvvvvv.""", 3, 3, 3, 3),
    # A panel or card: Deepslate 800 with a lighter top edge.
    "panel": ("""
        .vvvvvv.
        v666666v
        v688888v
        v888888v
        v888888v
        v888888v
        v888888v
        .vvvvvv.""", 2, 2, 1, 1),
    # A well (a field, a list, an item slot): sunk into the window, its top in shadow.
    "well": ("""
        .666666.
        6vvvvvv6
        6v99999 6
        6999999 6
        6999999 6
        6999999 6
        6999999 6
        .666666.""".replace(" ", ""), 2, 2, 1, 1),
    "well_focus": ("""
        .llllll.
        lvvvvvvl
        lv99999l
        l999999l
        l999999l
        l999999l
        l999999l
        .llllll.""", 2, 2, 1, 1),
    "well_error": ("""
        .rrrrrr.
        rvvvvvvr
        rv99999r
        r999999r
        r999999r
        r999999r
        r999999r
        .rrrrrr.""", 2, 2, 1, 1),
    # The tooltip: Void inside a Slate Line frame with a copper line along the top.
    "tooltip": ("""
        .ssssss.
        sccccccs
        svvvvvvs
        svvvvvvs
        svvvvvvs
        svvvvvvs
        svvvvvvs
        .ssssss.""", 2, 2, 1, 1),
    # The toast: a panel with a Lantern bar down its left.
    "toast": ("""
        .vvvvvvvv.
        vll666666v
        vll688888v
        vll888888v
        vll888888v
        vll888888v
        vll888888v
        .vvvvvvvv.""", 4, 2, 1, 1),
    # The neutral button: raised Deepslate 700 with a light top and a dark lip; hover adds the Lantern edge; pressed
    # sinks (the light gone, the shadow on top); off is flat with a Slate Line outline.
    "button": ("""
        .vvvvvv.
        v666666v
        v677777v
        v777777v
        v777777v
        v777777v
        v999999v
        .vvvvvv.""", 2, 2, 1, 2),
    "button_hover": ("""
        .llllll.
        lssssssl
        ls6666 6l
        l666666l
        l666666l
        l666666l
        l999999l
        .llllll.""".replace(" ", ""), 2, 2, 1, 2),
    "button_pressed": ("""
        .vvvvvv.
        vvvvvvvv
        v966666v
        v666666v
        v666666v
        v666666v
        v666666v
        .vvvvvv.""", 2, 2, 1, 1),
    "button_off": ("""
        .ssssss.
        s888888s
        s888888s
        s888888s
        s888888s
        s888888s
        s888888s
        .ssssss.""", 1, 1, 1, 1),
    # The copper (main) button, in the same three states.
    "copper": ("""
        .vvvvvv.
        vCCCCCCv
        vCcccccv
        vccccccv
        vccccccv
        vccccccv
        vddddddv
        .vvvvvv.""", 2, 2, 1, 2),
    "copper_hover": ("""
        .llllll.
        lqqqqqql
        lqCCCCCl
        lCCCCCCl
        lCCCCCCl
        lCCCCCCl
        lddddddl
        .llllll.""", 2, 2, 1, 2),
    "copper_pressed": ("""
        .vvvvvv.
        vvvvvvvv
        vdccccc v
        vccccccv
        vccccccv
        vccccccv
        vccccccv
        .vvvvvv.""".replace(" ", ""), 2, 2, 1, 1),
    # The danger button (Demolish, Dismiss): Redstone, dark text, a Void lip.
    "danger": ("""
        .vvvvvv.
        vqrrrrrv
        vrrrrrrv
        vrrrrrrv
        vrrrrrrv
        vrrrrrrv
        v999999v
        .vvvvvv.""", 2, 2, 1, 2),
}

# glyphs.png: 8 x 4 glyphs of 8x8, in this order.
GLYPHS = [
    "up", "down", "steady", "good", "bad", "warning", "lock", "star",
    "heart", "coin", "clock", "refresh", "search", "plus", "minus", "lantern",
    "happy", "content", "unhappy", "more", "open", "bed", "person", "bolt",
    "info", "sun", "leaf", "shield", "bread", "raid", "less", "dot",
]
# icons.png: 8 x 4 icons of 16x16; the Hall's ten sections (HallSections.Section's order) and the coin.
ICONS = {
    "overview": (0, 0), "people": (1, 0), "homes": (2, 0), "buildings": (3, 0), "defense": (4, 0), "quests": (5, 0),
    "trade": (6, 0), "calendar": (7, 0), "laws": (0, 1), "map": (1, 1), "coin": (0, 3),
}


def _locked():
    colors = [_rgb(h) for h in PALETTE.values()] + [_rgb(h) for h in MATERIALS]
    return colors


def lock(img):
    """Every pixel snapped onto the palette (or a named material); alpha made 0 or 255."""
    colors = _locked()
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = img.getpixel((x, y))
            if a < 128:
                continue
            best = min(colors, key=lambda c: (c[0] - r) ** 2 + (c[1] - g) ** 2 + (c[2] - b) ** 2)
            out.putpixel((x, y), best + (255,))
    return out


def _save(img, path, mcmeta=None):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    written = [path]
    if mcmeta is not None:
        meta = path.with_name(path.name + ".mcmeta")
        meta.write_text(json.dumps(mcmeta, indent=2) + "\n")
        written.append(meta)
    return written


def copperline_nine_slices():
    written = []
    for name, (rows, left, top, right, bottom) in NINE.items():
        img = _grid(rows)
        meta = {"gui": {"scaling": {"type": "nine_slice", "width": img.width, "height": img.height,
                                    "border": {"left": left, "top": top, "right": right, "bottom": bottom}}}}
        written += _save(img, OUT / f"{name}.png", meta)
    return written


def copperline_glyphs():
    sheet = Image.open(SHEETS / "glyphs.png").convert("RGBA")
    written = []
    for i, name in enumerate(GLYPHS):
        x, y = (i % 8) * 8, (i // 8) * 8
        written += _save(lock(sheet.crop((x, y, x + 8, y + 8))), OUT / "glyph" / f"{name}.png")
    return written


def copperline_icons():
    sheet = Image.open(SHEETS / "icons.png").convert("RGBA")
    written = []
    for name, (cx, cy) in ICONS.items():
        written += _save(lock(sheet.crop((cx * 16, cy * 16, cx * 16 + 16, cy * 16 + 16))), OUT / "icon" / f"{name}.png")
    return written


DRAW = [copperline_nine_slices, copperline_glyphs, copperline_icons]

if __name__ == "__main__":
    from artlib import run
    run(DRAW)
