"""Tool and weapon icons from shape templates + material kits.

Most awkward tool icons fail on anatomy, not colour: a head too big for the icon, a head that
doesn't sit on the handle, a detail sticking out where nothing should be. The templates here follow
the anatomy of well-liked vanilla-style weapon packs (lean shapes, small heads, long stick handles,
one flat outline colour round the metal) and are drawn in shading ZONES rather than colours, so any
material drops in. Templates: hammer, warhammer, mace, wrench, pickaxe, dagger.

    from tools import *
    s = tool("hammer", head=KITS["iron"])                        # vanilla stick handle
    s = tool("wrench", head=KITS["iron"], accent=kit("#2a5bd7"))  # blue grip
    s = tool("mace", head=KITS["gold"])
    s.save(".../textures/item/steel_hammer.png")

Then make it yours: copy the template text, change the shape, and render your own with
render(art, head=..., handle=..., accent=...).

Zone letters in a template (anything else must be '.'):
    head    g glint  l light  f face  s shade  k deep     L / D  head outline
    handle  B wood light  C wood dark                    A / E  handle outline (lit / shadow side)
    accent  y light  x face  z shade  (grips, guards)     M / N  accent outline
    accent2 u light  v face  w shade  (a 4th material)     U / V  its outline
    p       a butt cap in the head's light colour (optional; the templates don't use one)
    G F S K head glint / face / shade / deep as BARE pixels: spikes, studs that must stay 1 px
    Y / X   accent light / face as bare pixels
            Attach bare pixels ORTHOGONALLY to the shape: a pixel touching only diagonally floats.
Outlines you don't write are added by Sprite.outline(), each material with its own. With the
default outline="flat" the metal and accent parts get ONE outline colour all round (iron #444444)
and the wooden handle keeps the vanilla stick's lit/shadow pair; outline="vanilla" gives the metal
a near-black shadow side like vanilla's own tools. Outline letters (L D A E M N U V) and bare
letters never get an outline of their own, so a spike drawn as one 'D' or 'G' pixel stays one
pixel. Keep non-outline letters off the canvas edge (row/column 0 and 15) unless you write the
outline yourself: there is no room for one there.
"""
import sys

import numpy as np

from px import Sprite, Ramp, rgba, hsv, from_hsv, mix, grow, LIGHT  # noqa: F401

ZONES = "glfsk"  # head zones, index into a kit: deep, shade, face, light, glint


def _kit(colours, outline, name):
    """A material kit: Ramp of 5 roles [deep, shade, face, light, glint] + outline (lit, shadow)."""
    return Ramp(colours, outline=outline, name=name)


# Colour roles measured from the vanilla tools of each material (pickaxe, axe, shovel, hoe, sword;
# handle pixels excluded): face = the most used head colour, light/glint the lighter ones, etc.
KITS = {
    "wood": _kit(["#594319", "#6b511f", "#755821", "#866526", "#9c7a3c"], ("#372910", "#20180a"), "wood"),
    "stone": _kit(["#6c6c6c", "#787777", "#7f7f7f", "#9a9a9a", "#b3b1af"], ("#494949", "#181818"), "stone"),
    "iron": _kit(["#6b6b6b", "#969696", "#c1c1c1", "#d8d8d8", "#ffffff"], ("#444444", "#181818"), "iron"),
    "gold": _kit(["#b26411", "#dc9613", "#e9b115", "#fdff76", "#ffffff"], ("#825d16", "#3f2e0e"), "gold"),
    "diamond": _kit(["#156355", "#1e8a77", "#27b29a", "#33ebcb", "#a4fdf0"], ("#0e3f36", "#082520"), "diamond"),
    "netherite": _kit(["#322727", "#4f3c3e", "#5d565d", "#706770", "#867b86"], ("#4a2940", "#231012"), "netherite"),
    "copper": _kit(["#803921", "#9c4e31", "#d66d48", "#fc9982", "#fdd4cb"], ("#5f2c1c", "#421c11"), "copper"),
}
KITS["golden"] = KITS["gold"]
KITS["wooden"] = KITS["wood"]

# Handles: Ramp [dark wood, light wood] + outline. Vanilla tools: the stick; netherite: its own.
HANDLES = {
    "stick": Ramp(["#684e1e", "#896727"], outline=("#493615", "#281e0b"), name="stick"),
    "netherite": Ramp(["#3b393b", "#734543"], outline=("#2f2122", "#231012"), name="netherite_handle"),
    "dark": Ramp(["#3a2a1a", "#5a4128"], outline=("#2a1e12", "#150e08"), name="dark_wood"),
    "pale": Ramp(["#9c8458", "#c2a870"], outline=("#6b5530", "#3a2c14"), name="pale_wood"),
}


def kit(base, precious=False, name=""):
    """A material kit around a face colour for materials vanilla doesn't have (a coloured grip, a
    modded metal): [deep, shade, face, light, glint] + outline pair. Steps are relative to the
    face's brightness, so dark bases (obsidian) get close steps and light bases don't blow out.
    Hue follows vanilla: tools barely shift hue (diamond, copper, iron: under 3 degrees); only
    precious=True shifts shadows toward red and lights toward yellow the way gold does (for
    yellows/oranges). Reds and pinks keep their hue and just gain saturation in the shadows (no
    mauve). Always check the result next to the vanilla material in a preview."""
    h, s, v = hsv(base)
    shift = 0.0
    if precious and 20 <= h <= 75:        # gold-like: shadows redder, lights yellower
        shift = 7.0

    def at(val, sat, dh):
        return from_hsv((h + dh) % 360, max(0.0, min(1.0, sat)), max(0.0, min(1.0, val)))

    if v > 0.5:
        light, glint = v + (1 - v) * 0.4, v + (1 - v) * 0.78
    else:                                  # dark materials: small relative steps
        light, glint = v * 1.35 + 0.02, v * 1.8 + 0.05
    cols = [at(v * 0.58, s * 1.12, -2 * shift), at(v * 0.78, s * 1.06, -shift), at(v, s, 0),
            at(light, s * 0.82, shift), at(glint, s * 0.4, 2 * shift)]
    outline = (at(v * 0.45, s * 1.15, -2 * shift), at(v * 0.24, s * 1.2, -2 * shift))
    return _kit(cols, outline, name or f"kit({base})")


def _flat(k):
    """Kit copy whose outline is one colour all round (the lit outline)."""
    return k.with_outline(k.outline_light, k.outline_light)


def render(art, head, handle=None, accent=None, accent2=None, warn=True, outline="flat"):
    """Template/zone ASCII -> Sprite. head/accent/accent2: kits (KITS[...] or kit(...));
    handle: HANDLES[...].

    outline="flat" (default): metal and accent parts get ONE outline colour all round (iron
    #444444), the crisp look of well-liked weapon packs; the wooden handle keeps the stick's
    two-tone outline. outline="vanilla": lit outline on the top/left and a near-black one on the
    bottom/right, like vanilla's own tools (heavier at small sizes)."""
    handle = handle or HANDLES["stick"]
    if outline not in ("flat", "vanilla"):
        raise ValueError("outline must be 'flat' or 'vanilla'")
    if outline == "flat":
        head = _flat(head)
        accent = _flat(accent) if accent is not None else None
        accent2 = _flat(accent2) if accent2 is not None else None
    rows = [r.strip() for r in art.strip("\n").split("\n")]
    w = max(len(r) for r in rows)
    if any(len(r) != w for r in rows):
        raise ValueError("template rows must all be the same width")
    s = Sprite(w, len(rows))
    acc = accent or head
    acc2 = accent2 or acc
    bare = set("LDAEMNUVYXGFSK")
    col = {
        "Y": acc[3], "X": acc[2],
        "G": head[4], "F": head[2], "S": head[1], "K": head[0],
        "g": head[4], "l": head[3], "f": head[2], "s": head[1], "k": head[0],
        "L": head.outline_light, "D": head.outline_dark, "p": head[3],
        "B": handle[-1], "C": handle[0], "A": handle.outline_light, "E": handle.outline_dark,
        "y": acc[3], "x": acc[2], "z": acc[1], "M": acc.outline_light, "N": acc.outline_dark,
        "u": acc2[3], "v": acc2[2], "w": acc2[1], "U": acc2.outline_light, "V": acc2.outline_dark,
    }
    material = {}
    for chars, kit_ in (("glfskpGFSK", head), ("BC", handle), ("yxzYX", acc), ("uvw", acc2)):
        for ch in chars:
            material[ch] = kit_
    body = s.mask()
    mat = {}
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch in ". ":
                continue
            if ch not in col:
                raise ValueError(f"unknown zone letter {ch!r} at ({x},{y}); see tools.py docstring")
            s.put(x, y, col[ch])
            body[y, x] = ch not in bare
            if ch in material:
                mat[(x, y)] = material[ch]
    if warn:
        h_, w_ = body.shape
        edge = [(x, y) for y in range(h_) for x in range(w_) if body[y, x] and x in (0, w_ - 1) or
                body[y, x] and y in (0, h_ - 1)]
        if edge:
            print(f"tools.render: {len(edge)} filled pixel(s) on the canvas edge get no outline, e.g. {edge[:3]}; "
                  "move the shape in or write L/D there yourself", file=sys.stderr)
    _outline(s, body, mat)
    return s


def _outline(s, body, mat, light=LIGHT):
    """1 px outline round `body`, each ring pixel in the outline of the MATERIAL (template letter)
    it borders, not the colour: two kits can share a colour (iron and gold both have a white
    glint), and a colour lookup would give an iron blade a gold rim."""
    ring = grow(body, 1) & ~body & (s.a[..., 3] == 0)
    for y, x in zip(*np.nonzero(ring)):
        k = None
        for dx, dy in ((0, 1), (1, 0), (0, -1), (-1, 0)):
            if (x + dx, y + dy) in mat and body[y + dy, x + dx]:
                k = mat[(x + dx, y + dy)]
                break
        if k is None:
            continue
        sx, sy = Sprite._nb_dir(body, x, y)
        lit = (sx * light[0] + sy * light[1]) < 0
        s.put(x, y, k.outline_light if lit else k.outline_dark)


# --------------------------------------------------------------------------- templates
# Drawn for this skill (not traced from any game or mod) in the lean style of well-liked
# vanilla-style weapon packs: a long vanilla-stick handle from the bottom-left corner, a SMALL head
# (about 11-20 metal pixels, not a 7x7 block), fills 2-3 px wide shaded glint/light on the
# upper-left edge and shade on the lower-right, one flat outline colour round the metal. See
# references/tools.md for the anatomy of each.

TEMPLATES = {
    # hammer (picked by the user from two rounds of variants): a head across the handle, on the
    # diagonal, with a wide striking face at EACH end and a narrower neck where the handle enters,
    # so the two faces read as two blocks. A head of even thickness reads as a sausage or a bar;
    # a level (axis-aligned) T head reads clearly but looks off-style next to vanilla tools.
    "hammer": """
................
......gl........
.....glff.......
......lffs......
........fs......
.........ffs....
.........Bffs...
........C.lfss..
.......B...fsk..
......C.........
.....B..........
....C...........
...B............
..C.............
.B..............
................
""",
    # war hammer (picked by the user): a heavy block at the top-left, a thin neck running across
    # the handle into a long beak that hooks down at the end (the D pixel is the hook's tip), a
    # steel haft (accent2 letters v/w, iron by default) and a leather grip (accent y/x). Menace
    # comes from the contrast: bulky block, thin long hooked beak, weapon hilt instead of a stick.
    # Uniformly bulky heads read as a shoe or a clothes iron; spikes and pommels added nothing.
    "warhammer": """
................
......gl........
.....glfl.......
....glffs.......
.....lffs.......
.......sfs......
.........fs.....
........v.fs....
.......w...fs...
......v.....ss..
.....x.......k..
....y........k..
...x........D...
..y.............
.x..............
................
""",
    # mace: a 4x4 ball centred on the handle end (the outline rounds its corners) and four 1 px
    # BARE spikes (G/F) on its outline, none on the handle side. Light spikes read as a morning
    # star; outline-coloured or shade-coloured spikes vanish and the head reads as a lump.
    "mace": """
................
...........G....
................
.........glff...
.......G.lffs...
.........ffss.F.
.........fssk...
........C.......
.......B...F....
......C.........
.....B..........
....C...........
...B............
..C.............
.B..............
................
""",
    # open-end wrench (picked by the user): a C-shaped jaw, one prong along the top and one down
    # the right, opening toward the top-right with REAL transparent pixels inside (a gap only 1-2 px
    # wide fills with outline and reads as a club or a torch); a 3 px shaft; a wooden grip
    # (accent y/x/z, wood by default). Prongs parallel to the shaft read as a fork or a trident.
    "wrench": """
................
.........gll....
........gll.....
........lf....f.
........lf...fs.
........lfffss..
.......gffss....
......gfss......
.....gfs........
....gfs.........
...yxz..........
..yxz...........
.yxz............
.xz.............
................
................
""",
    # pickaxe: an arc, two arms of equal length that are mirror images across the handle line (one
    # along the top, one down the right side), 2 px thick at the bend and 1 px at the lit tips, the
    # handle tip poking out past the bend. A sharp right-angle corner reads as a carpenter's
    # square; an arm longer than the other makes it lopsided and it reads as a hoe.
    "pickaxe": """
................
................
.............C..
.....glllff.B...
........sfff....
..........Cfs...
.........B.ss...
........C..sf...
.......B....f...
......C.....l...
.....B......g...
....C...........
...B............
..C.............
.B..............
................
""",
    # dagger: a blade band 3 px wide (glint edge, face, shade), a crossguard in the accent material
    # that is symmetric about the blade's axis, and a short grip. Without the guard a blade reads as
    # a stick or a chisel; a lopsided guard reads as an axe beard; a longer blade makes it a sword.
    "dagger": """
................
................
............gl..
...........gfs..
..........gfs...
.........gfs....
.....y..gfs.....
.....xygfs......
......xxs.......
......Bxx.......
.....C..zz......
....B...........
...C............
..B.............
................
................
""",
}


# Second materials a template uses when you don't pass them: the wrench's grip is wood, the war
# hammer has a steel haft and a leather grip. Pass accent= / accent2= to change them.
DEFAULTS = {
    "wrench": {"accent": kit("#896727", name="wood grip")},
    "warhammer": {"accent": kit("#6b3a22", name="leather grip"), "accent2": KITS["iron"]},
}


def tool(name, head, handle=None, accent=None, accent2=None, outline="flat"):
    """Render a named template with material kits. See TEMPLATES for the shapes and DEFAULTS for
    the second materials each one uses unless you pass accent/accent2."""
    if name not in TEMPLATES:
        raise KeyError(f"no template {name!r}; have: {', '.join(sorted(TEMPLATES))}")
    d = DEFAULTS.get(name, {})
    accent = accent if accent is not None else d.get("accent")
    accent2 = accent2 if accent2 is not None else d.get("accent2")
    return render(TEMPLATES[name], head, handle, accent, accent2, outline=outline)
