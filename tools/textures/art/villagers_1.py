"""Profession outfits, set 1: ball smith, bard, beekeeper, carpenter, chef, composter, ferryman, florist, fossil
scientist, guard, innkeeper, lumberjack, miner, netherworker, nurse. Redrawn in the approved style of builders.py
(owner, 2026-09-29): each keeps its old headwear, colours and accessory; sparse overlays, one ramp per garment, folds,
a darker hem, the back painted, sleeves on every arm face or on none, nothing under the level badge (jacket front
x 4-7, rows 10-13)."""
from artlib import *  # noqa: F401,F403

# ------------------------------------------------------------------------------------------------ shared colours
LINEN = Ramp(["#a9a99e", "#c4c3b9", "#dcdbd2", "#ebeae3", "#f6f5ef"], name="linen")      # warm off-white cloth
LAB = Ramp(["#9fa4a6", "#bcc1c2", "#d5d9d9", "#e5e8e8", "#f2f4f4"], name="lab")          # cool white (lab coat)
STEEL = Ramp(["#5d6166", "#7f858a", "#a3a9ae", "#c4c9cd", "#dde0e2"], name="steel")
GOLD = Ramp(["#94701c", "#bb9127", "#dcb338", "#f0d36a"], name="gold")                  # braid, buckles
BRASS = Ramp(["#7a5c22", "#a37d34", "#c9a24a"], name="brass")
WOOD = Ramp(["#553a1f", "#7a5a34", "#9a7446"], name="wood")                             # handles
BELT = P("leather")
DARK_BELT = Ramp(["#3b2616", "#4f3320", "#644229", "#7a5434"], name="dark_belt")


def put_all(face, pixels):
    """(x, y, colour) triples on one face."""
    for x, y, c in pixels:
        face.put(x, y, c)


def bow(t, ramp, row=9):
    """The apron's waist tie knotted at the back: a knot and two short tails."""
    b = t.face("jacket", "back")
    put_all(b, [(3, row, ramp[2]), (4, row, ramp[3]), (3, row + 1, ramp[1]), (4, row + 1, ramp[2]),
                (2, row + 2, ramp[1]), (5, row + 2, ramp[1])])


def back_straps(t, colour, rows=range(0, 9), xs=(1, 6)):
    """A bib apron's neck straps, over the shoulders and down the back to the waist tie."""
    b = t.face("jacket", "back")
    for y in rows:
        for x in xs:
            b.put(x, y, colour)


def bib_apron(t, r, top=2, bottom=17, left=1, right=6, strap=None, tie=None):
    """vg.apron with straps down the back and a knotted tie: the back of every bib apron in this set."""
    vg.apron(t, r, top=top, bottom=bottom, left=left, right=right, ties=True)
    strap = strap or r[1]
    front = t.face("jacket", "front")
    for y in range(0, top):
        front.put(left, y, strap)
        front.put(right, y, strap)
    back_straps(t, strap)
    bow(t, tie or r)


def dome_top(t, r, base=3, ring=2, light=None):
    """A hat's top face drawn clean: one colour, a darker ring round the edge (a dome, not a slab), a lit
    corner at the front-left. r = the hat's ramp."""
    top = t.face("hat", "top")
    for y in range(8):
        for x in range(8):
            edge = x in (0, 7) or y in (0, 7)
            top.put(x, y, r[ring] if edge else r[base])
    top.put(1, 6, light or r[min(len(r) - 1, base + 1)])
    top.put(2, 6, light or r[min(len(r) - 1, base + 1)])
    top.put(1, 5, light or r[min(len(r) - 1, base + 1)])
    return top


def crown(t, r, rows, band=None, base=2, lit=True, weave=0.0):
    """The hat's side faces over `rows`: lit top row, base colour, optional band on the last row. weave > 0 adds
    the cloth helper's short 2 px runs (straw, felt) at that density."""
    for i, side in enumerate(vg.SIDES):
        f = t.face("hat", side)
        if weave:
            vg._cloth(f, r, rows, seed=11 + i, base_index=base, noise=weave, hem=False, top_light=1 if lit else 0)
        else:
            for y in rows:
                for x in range(f.w):
                    f.put(x, y, r[base + 1] if (lit and y == rows[0]) else r[base])
        if band is not None:
            for x in range(f.w):
                f.put(x, rows[-1], band)


def ring_brim(t, r, width=4, lip=None):
    """A brim on the rim plane's front face only, `width` px out from the head (4 = the whole plane)."""
    rim = t.face("hat_rim", "front")
    lo, hi = 4 - width, 11 + width
    for y in range(16):
        for x in range(16):
            if 4 <= x < 12 and 4 <= y < 12:
                continue
            if not (lo <= x <= hi and lo <= y <= hi):
                continue
            if (x in (lo, hi)) and (y in (lo, hi)):
                continue                                     # round the corners
            edge = x in (lo, hi) or y in (lo, hi)
            rim.put(x, y, (lip or r[1]) if edge else r[2 if (x + y) % 5 else 3])


# ------------------------------------------------------------------------------------------------ outfits
def ball_smith():
    """A brown leather cap with brass-rimmed goggles pushed up on it, a leather apron with a red and white ball
    stitched on low at the front (clear of the level badge), straps and a knotted tie at the back."""
    t = vg.VillagerTexture()
    lea = vg.cloth("#7a4a26")
    strap = "#3b2414"
    lens = ["#5aa6d6", "#8fd3ff", "#c4ebff"]

    crown(t, lea, range(0, 4), band=lea[1])
    dome_top(t, lea, base=2, ring=1)
    f = t.face("hat", "front")                          # goggles: brass rims round two lenses on row 1
    put_all(f, [(1, 0, BRASS[2]), (2, 0, BRASS[2]), (5, 0, BRASS[2]), (6, 0, BRASS[2]),
                (0, 1, strap), (3, 1, BRASS[1]), (4, 1, BRASS[1]), (7, 1, strap),
                (1, 1, lens[2]), (2, 1, lens[1]), (5, 1, lens[2]), (6, 1, lens[1]),
                (1, 2, BRASS[0]), (2, 2, BRASS[0]), (5, 2, BRASS[0]), (6, 2, BRASS[0])])
    for side in ("west", "east", "back"):               # the goggles' strap round the cap
        g = t.face("hat", side)
        for x in range(8):
            g.put(x, 1, strap)

    bib_apron(t, lea, top=2, bottom=19)
    front = t.face("jacket", "front")                   # the ball, x 1-5 rows 14-18 (clear of the badge)
    ball = grid("""
        .Rrr.
        Rrrrd
        kkwkk
        wwwws
        .sss.
    """, {"r": "#b8262a", "R": "#d9463b", "d": "#8e1c22", "k": "#2e2020", "w": "#ece8de", "s": "#c9c4b6"})
    front.paste(ball, 1, 14)
    return t.save_profession(ASSETS, "ball_smith", hat="full")


def bard():
    """A purple cap with a gold band and a cream feather, a purple tunic with gold trim at the neck and hem, and a
    lute slung on the back (its strap crosses the chest above the arms)."""
    t = vg.VillagerTexture()
    purple = vg.cloth("#70307f")
    feather = ["#b9a988", "#e0d3b4", "#f4ead2"]
    lute = Ramp(["#5a3a1f", "#7e5431", "#a06e40", "#bf8a55"], name="lute")

    crown(t, purple, range(0, 3), band=GOLD[1])
    dome_top(t, purple, base=2, ring=1)
    f = t.face("hat", "front")                          # the feather, tucked in the band at the front-left corner
    put_all(f, [(6, 2, feather[0]), (6, 1, feather[1]), (7, 1, feather[2]), (7, 0, feather[2])])
    top = t.face("hat", "top")
    put_all(top, [(7, 7, feather[1]), (6, 6, feather[2]), (7, 6, feather[1]), (6, 5, feather[1]), (5, 4, feather[2]),
                  (5, 3, feather[1])])

    length = 15
    for side in vg.SIDES:
        g = t.face("jacket", side)
        vg._cloth(g, purple, range(0, length), seed=3, noise=0.04)
        if side in ("front", "back"):
            vg._folds(g, purple, range(2, length), (1, 5), seed=4 + (side == "back"))
        for x in range(g.w):
            g.put(x, 0, GOLD[2])
            g.put(x, length - 1, GOLD[1])
    vg._cloth(t.face("jacket", "top"), purple, hem=False, noise=0)

    front = t.face("jacket", "front")                   # the lute's strap over the left shoulder
    put_all(front, [(6, 0, BELT[1]), (5, 1, BELT[1]), (4, 2, BELT[1])])
    b = t.face("jacket", "back")
    b.paste(grid("""
        .k..
        .21.
        .21.
        .21.
        .21.
        .21.
        .21.
        .33.
        3322
        3kk2
        2kk1
        2221
        .10.
    """, {"k": "#2e1c0e"}, ramp=lute), 2, 1)
    put_all(b, [(1, 0, BELT[1]), (0, 1, BELT[1])])
    return t.save_profession(ASSETS, "bard", hat="full")


def beekeeper():
    """A cream brimmed hat with a dark mesh veil hanging over the face (the eyes show through), a cream bee jacket
    with sleeves, honey-leather gloves and a honey patch on the front."""
    t = vg.VillagerTexture()
    cream = Ramp(["#a79f86", "#c3bb9f", "#dcd3b8", "#ebe3ca", "#f5efdc"], name="cream")
    mesh, mesh_side = "#56524a", "#9a9486"
    honey = Ramp(["#9c5f1a", "#c47f22", "#e0a526", "#f0c55a"], name="honey")

    crown(t, cream, range(0, 4), band=cream[1])
    dome_top(t, cream)
    ring_brim(t, cream, width=4)
    f = t.face("hat", "front")                          # the veil: threads on every other row and column
    for y in range(4, 10):
        for x in range(8):
            if y in (5, 7, 9) or x in (0, 2, 5, 7):
                f.put(x, y, mesh)
    for x in (1, 2, 5, 6):                              # let the eyes show
        f.put(x, 6, (0, 0, 0, 0))
    for side in ("west", "east", "back"):               # lighter, open mesh round the sides and back
        g = t.face("hat", side)
        for y in range(4, 9):
            for x in range(8):
                if y in (5, 7) or x % 2 == 1:
                    g.put(x, y, mesh_side)

    length = 14
    for side in vg.SIDES:                               # the jacket: to the hips, the robe shows below
        g = t.face("jacket", side)
        vg._cloth(g, cream, range(0, length), seed=5, noise=0)
        if side in ("front", "back"):
            vg._folds(g, cream, range(2, length), (1, 5), seed=6 + (side == "back"))
    vg._cloth(t.face("jacket", "top"), cream, hem=False, noise=0)
    vg.sleeves(t, cream, gloves=vg.cloth("#c9953a", n=4), cuff=cream[1], noise=0)
    front = t.face("jacket", "front")
    front.paste(grid("""
        33
        21
    """, ramp=honey), 1, 11)
    return t.save_profession(ASSETS, "beekeeper", hat="full")


def carpenter():
    """No hat: a pencil tucked behind the ear. A canvas apron with a pocket and a yellow folding rule at the front,
    a hammer in a loop at the side, straps and a knotted tie at the back."""
    t = vg.VillagerTexture()
    canvas = vg.cloth("#b99a6b")
    pocket = vg.cloth("#9c7f55")
    rule = ["#b88a16", "#e8b923"]

    side = t.face("hat", "west")                        # the pencil over the ear, pointing forward
    put_all(side, [(2, 5, "#d98a8a"), (3, 5, rule[1]), (4, 5, rule[1]), (5, 5, rule[0]), (6, 5, "#d9b98a"),
                   (7, 5, "#3a3a3a")])

    bib_apron(t, canvas, top=2, bottom=17, strap="#6b4226")
    front = t.face("jacket", "front")
    for y in range(13, 17):                             # pocket at x 1-3 (the badge is at x 4-7, rows 10-13)
        for x in range(1, 4):
            front.put(x, y, pocket[3] if y == 13 else pocket[2])
    put_all(front, [(2, 10, rule[1]), (2, 11, "#5a4a2a"), (2, 12, rule[1]), (3, 12, rule[0])])  # rule in the pocket
    side = t.face("jacket", "west")                     # a hammer in the loop at the villager's right side
    put_all(side, [(2, 10, STEEL[3]), (3, 10, STEEL[2]), (4, 10, STEEL[1]), (3, 11, WOOD[1]), (3, 12, WOOD[0]),
                   (3, 13, WOOD[1])])
    return t.save_profession(ASSETS, "carpenter")


def chef():
    """A white toque, pleated all round with a band, a white bib apron and a red neckerchief knotted at the throat."""
    t = vg.VillagerTexture()
    white = LINEN
    red = vg.cloth("#c0392b")

    for side in vg.SIDES:                               # the toque: pleats (lit ridge + shadow) over a band
        f = t.face("hat", side)
        for y in range(0, 5):
            for x in range(8):
                c = white[3]
                if y < 4 and x % 3 == 2:
                    c = white[2]
                if y < 4 and x % 3 == 0:
                    c = white[4]
                if y == 4:
                    c = white[1]
                f.put(x, y, c)
    top = dome_top(t, white, base=3, ring=2, light=white[4])
    for x, y in ((3, 3), (4, 3), (3, 4), (4, 4)):
        top.put(x, y, white[2])                         # the puff gathered in the middle

    bib_apron(t, white, top=3, bottom=17)
    for side in vg.SIDES:                               # neckerchief round the neck
        g = t.face("jacket", side)
        for x in range(g.w):
            g.put(x, 0, red[2])
    front = t.face("jacket", "front")
    put_all(front, [(3, 1, red[3]), (4, 1, red[2]), (3, 2, red[1]), (4, 2, red[0])])
    return t.save_profession(ASSETS, "chef", hat="full")


def composter():
    """A straw hat with a narrow brim and a green band, a green rubber apron with earth on the hem and a trowel in
    the pocket."""
    t = vg.VillagerTexture()
    straw = vg.cloth("#d9c27a")
    rubber = vg.cloth("#4f7a3a")
    earth = ["#4a3320", "#5e4229"]

    crown(t, straw, range(0, 4), band=rubber[1], weave=0.12)
    dome_top(t, straw)
    ring_brim(t, straw, width=2)

    bib_apron(t, rubber, top=2, bottom=18)
    front = t.face("jacket", "front")
    for y in range(13, 17):
        for x in range(1, 4):
            front.put(x, y, rubber[3] if y == 13 else rubber[1])
    put_all(front, [(2, 11, WOOD[2]), (2, 12, STEEL[2]),                 # trowel handle in the pocket
                    (5, 17, earth[1]), (6, 17, earth[0]), (1, 18, earth[1]), (2, 18, earth[0])])
    return t.save_profession(ASSETS, "composter", hat="full")


def ferryman():
    """A straw boater with a navy band and a brim, a white shirt with navy stripes (sleeves too)."""
    t = vg.VillagerTexture()
    straw = vg.cloth("#e3c86b")
    navy = vg.cloth("#2b4c8c")
    shirt = LINEN

    crown(t, straw, range(0, 4), weave=0.12)
    for side in vg.SIDES:
        f = t.face("hat", side)
        for x in range(8):
            f.put(x, 2, navy[2])
            f.put(x, 3, straw[1])
    dome_top(t, straw)
    ring_brim(t, straw, width=3)

    def stripes(face, rows, horizontal=True):
        for y in rows:
            for x in range(face.w):
                k = y if horizontal else x
                face.put(x, y, navy[2] if k % 3 == 1 else (shirt[3] if y == rows[0] else shirt[2]))

    for side in vg.SIDES:
        stripes(t.face("jacket", side), range(0, 12))
        g = t.face("jacket", side)
        for x in range(g.w):
            g.put(x, 11, shirt[1])
    for side in vg.SIDES:
        stripes(t.face("arm", side), range(0, 8))
    for side in ("top", "bottom"):
        t.face("arm", side).fill(shirt[2])
    for side in ("front", "top", "bottom"):
        stripes(t.face("arms_middle", side), range(0, 4), horizontal=False)
    mid = t.face("arms_middle", "front")
    for y in range(4):
        mid.put(4, y, shirt[1])
    return t.save_profession(ASSETS, "ferryman", hat="full")


def florist():
    """A crown of leaves and flowers round the head, a leaf-green apron with a pocket of cut flowers."""
    t = vg.VillagerTexture()
    leaf = vg.cloth("#3f8a3a")
    green = vg.cloth("#6aa05a")
    flowers = ["#d83a3a", "#f2c93b", "#f1ede0", "#4a7fd6", "#e889b8"]

    for i, side in enumerate(vg.SIDES):                 # the wreath: a band of leaves with a flower every 3 px
        f = t.face("hat", side)
        for x in range(8):
            f.put(x, 0, leaf[3] if x % 3 == 0 else leaf[2])
            f.put(x, 1, leaf[1] if x % 3 == 2 else leaf[2])
        for x in (1, 5):
            f.put(x, 2, leaf[0])                        # leaf tips hanging below the band
        for k, x in enumerate((0, 3, 6)):
            f.put(x + (i % 2), 0, flowers[(i * 3 + k) % len(flowers)])
    top = t.face("hat", "top")                          # seen from above: a ring of leaves and flowers
    for y in range(8):
        for x in range(8):
            if x in (0, 7) or y in (0, 7):
                top.put(x, y, leaf[2] if (x + y) % 3 else leaf[3])
    for k, (x, y) in enumerate(((0, 2), (3, 0), (7, 1), (7, 5), (4, 7), (1, 7), (0, 5))):
        top.put(x, y, flowers[k % len(flowers)])

    bib_apron(t, green, top=2, bottom=17)
    front = t.face("jacket", "front")
    for y in range(13, 16):
        for x in range(1, 4):
            front.put(x, y, green[3] if y == 13 else green[1])
    put_all(front, [(1, 11, flowers[0]), (2, 10, flowers[1]), (3, 11, flowers[4]),
                    (1, 12, leaf[2]), (2, 11, leaf[2]), (2, 12, leaf[1]), (3, 12, leaf[2])])
    return t.save_profession(ASSETS, "florist", hat="full")


def fossil_scientist():
    """Goggles pushed up on the forehead (a face accessory: the biome's hat still shows), a white lab coat with
    sleeves, open at the front, and a brush in the pocket."""
    t = vg.VillagerTexture()
    frame, strap = "#3a3a3a", "#4a4a48"
    lens = ["#6fb4de", "#9ad7ff"]

    f = t.face("hat", "front")
    put_all(f, [(0, 3, frame), (3, 3, frame), (4, 3, frame), (7, 3, frame),
                (1, 2, frame), (2, 2, frame), (5, 2, frame), (6, 2, frame),
                (1, 4, frame), (2, 4, frame), (5, 4, frame), (6, 4, frame),
                (1, 3, lens[1]), (2, 3, lens[0]), (5, 3, lens[1]), (6, 3, lens[0])])
    for side in ("west", "east", "back"):
        g = t.face("hat", side)
        for x in range(8):
            g.put(x, 3, strap)

    length = 18                                         # to the knees: the robe's hem shows below
    for side in vg.SIDES:
        g = t.face("jacket", side)
        vg._cloth(g, LAB, range(0, length), seed=7, noise=0)
        if side in ("front", "back"):
            vg._folds(g, LAB, range(2, length), (1, 5) if side == "back" else (1,), seed=8)
    vg._cloth(t.face("jacket", "top"), LAB, hem=False, noise=0)
    vg.sleeves(t, LAB, cuff=LAB[1], noise=0)
    front = t.face("jacket", "front")
    clear = (0, 0, 0, 0)
    put_all(front, [(3, 0, clear), (4, 0, clear), (3, 1, LAB[1]), (4, 1, LAB[1])])      # the collar's V
    for y in range(9, length):                          # worn open below the waist: the robe shows between
        front.put(3, y, LAB[1])
        front.put(4, y, clear)
    for y in range(2, 9):
        front.put(3, y, LAB[1])
    for y in range(13, 16):                             # pocket (x 0-2) with a fossil brush in it
        for x in range(0, 3):
            front.put(x, y, LAB[3] if y == 13 else LAB[2])
    for x in range(0, 3):
        front.put(x, 16, LAB[1])
    put_all(front, [(1, 11, "#d9c28c"), (1, 12, "#a8834f"), (1, 13, WOOD[1])])
    return t.save_profession(ASSETS, "fossil_scientist")


def guard():
    """An iron helmet with a ridge and a nose guard, a blue tabard with gold trim and a gold bell on the front,
    a leather belt and a sword at the left hip."""
    t = vg.VillagerTexture()
    helm = STEEL
    blue = vg.cloth("#2b4c8c")

    for side in vg.SIDES:
        f = t.face("hat", side)
        for y in range(0, 5):
            for x in range(8):
                f.put(x, y, helm[3] if y == 0 else (helm[1] if y == 4 else helm[2]))
    top = dome_top(t, helm, base=2, ring=1, light=helm[4])
    for y in range(8):
        top.put(3, y, helm[4])
        top.put(4, y, helm[3])
    f = t.face("hat", "front")
    for y in range(0, 7):
        f.put(3, y, helm[4] if y < 4 else helm[3])
        f.put(4, y, helm[3] if y < 4 else helm[1])

    length = 19
    for side in vg.SIDES:
        g = t.face("jacket", side)
        rows = range(0, length) if side in ("front", "back") else range(0, 11)
        vg._cloth(g, blue, rows, seed=9, noise=0.04)
        if side in ("front", "back"):
            vg._folds(g, blue, range(2, length), (1, 5), seed=10 + (side == "back"))
            for x in range(8):
                g.put(x, length - 1, GOLD[1])
        for x in range(g.w):
            g.put(x, 0, GOLD[2])
    vg._cloth(t.face("jacket", "top"), blue, hem=False, noise=0)
    for side, rows in (("front", list(range(0, 10)) + list(range(14, length))), ("back", range(0, length))):
        g = t.face("jacket", side)                      # the gold stripe down the middle (not under the badge)
        for y in rows:
            g.put(3, y, GOLD[2] if side == "front" else GOLD[1])
            g.put(4, y, GOLD[1] if side == "front" else GOLD[0])
    vg.belt(t, DARK_BELT, row=10, buckle=GOLD[2])
    side = t.face("jacket", "east")                     # the sword at the left hip: hilt above the belt, scabbard below
    put_all(side, [(2, 8, helm[3]), (2, 9, WOOD[1]), (1, 10, helm[3]), (2, 10, helm[2]), (3, 10, helm[1])])
    for y in range(11, 18):
        side.put(2, y, "#3b2a1c" if y < 17 else helm[1])
    return t.save_profession(ASSETS, "guard", hat="full")


def innkeeper():
    """No hat. A red neckerchief knotted at the throat, a white waist apron that wraps round the hips and ties at
    the back, and a tea towel tucked into the apron string."""
    t = vg.VillagerTexture()
    red = vg.cloth("#b3262c")
    apron = LINEN

    for side in vg.SIDES:
        g = t.face("jacket", side)
        for x in range(g.w):
            g.put(x, 0, red[2])
    front = t.face("jacket", "front")
    put_all(front, [(3, 1, red[3]), (4, 1, red[2]), (3, 2, red[1]), (4, 2, red[0])])

    for side in vg.SIDES:                               # the waist apron, rows 10-18, round the front and sides
        g = t.face("jacket", side)
        cols = range(g.w) if side != "back" else (0, 1, 6, 7)
        for y in range(10, 19):
            for x in cols:
                g.put(x, y, apron[3] if y == 10 else (apron[1] if y == 18 else apron[2]))
        for x in range(g.w):
            g.put(x, 9, apron[1])                       # the apron string
    vg._folds(front, apron, range(10, 19), (2, 5), seed=12)
    vg._folds(t.face("jacket", "west"), apron, range(10, 19), (2,), seed=13)
    vg._folds(t.face("jacket", "east"), apron, range(10, 19), (2,), seed=14)
    bow(t, apron)
    # a red tea towel with a white stripe, tucked in the string at x 0-1 (the badge is at x 4-7)
    put_all(front, [(0, 9, red[3]), (1, 9, red[2]), (0, 10, red[2]), (1, 10, red[1]), (0, 11, apron[3]),
                    (1, 11, apron[2]), (0, 12, red[2]), (1, 12, red[1]), (0, 13, red[1]), (1, 13, red[0])])
    return t.save_profession(ASSETS, "innkeeper")


def lumberjack():
    """A green knit beanie with a turned-up cuff, a red and black buffalo-check vest open at the front, a leather
    belt and a hatchet at the right hip."""
    t = vg.VillagerTexture()
    knit = vg.cloth("#2f6b3a")
    plaid = ["#2a1a1c", "#6e1418", "#b3262c", "#c9403f"]            # black, dark red, red, lit red

    crown(t, knit, range(0, 4))
    for side in vg.SIDES:                               # the turned-up cuff: a lit fold, then its shadowed face
        f = t.face("hat", side)
        for x in range(8):
            f.put(x, 1, knit[1])
            f.put(x, 2, knit[3])
            f.put(x, 3, knit[2])
        f.put(2, 3, knit[1])
        f.put(5, 3, knit[1])
    top = dome_top(t, knit, base=2, ring=1)
    for x, y in ((3, 3), (4, 3), (3, 4), (4, 4)):
        top.put(x, y, knit[1])                          # the crown gathered in the middle

    for side in vg.SIDES:
        g = t.face("jacket", side)
        for y in range(0, 10):
            for x in range(g.w):
                if side == "front" and x in (3, 4):
                    continue
                dark = ((x // 2) % 2) + ((y // 2) % 2)
                c = plaid[2 - dark] if dark else (plaid[3] if y == 0 else plaid[2])
                g.put(x, y, c)
    vg.belt(t, BELT, row=10, buckle="#c8a24a")
    side = t.face("jacket", "west")                     # the hatchet: a steel head on a short haft
    put_all(side, [(2, 11, STEEL[3]), (3, 11, STEEL[2]), (2, 12, STEEL[2]), (3, 12, WOOD[1]), (3, 13, WOOD[0]),
                   (3, 14, WOOD[1])])
    return t.save_profession(ASSETS, "lumberjack", hat="full")


def miner():
    """A dark steel helmet with a lip all round and a lit headlamp in a brass housing, a leather apron, a belt and
    a pickaxe at the right hip."""
    t = vg.VillagerTexture()
    helm = ramp("#50555a", n=5, spread=0.45)
    lea = vg.cloth("#7a5230")
    lamp = ["#e0a82e", "#f5c542", "#fff0a8"]

    crown(t, helm, range(0, 4), band=helm[1])
    dome_top(t, helm, base=2, ring=1)
    rim = t.face("hat_rim", "front")                    # a 1 px lip all round: lit at the front, darker behind
    for i in range(3, 13):
        rim.put(i, 12, helm[2])
        rim.put(i, 3, helm[0])
        rim.put(3, i, helm[1])
        rim.put(12, i, helm[1])
    f = t.face("hat", "front")
    put_all(f, [(2, 1, BRASS[1]), (5, 1, BRASS[0]), (2, 2, BRASS[0]), (5, 2, BRASS[0]),
                (3, 0, BRASS[2]), (4, 0, BRASS[1]),
                (3, 1, lamp[2]), (4, 1, lamp[1]), (3, 2, lamp[1]), (4, 2, lamp[0])])

    bib_apron(t, lea, top=2, bottom=17)
    vg.belt(t, P("leather"), row=10, buckle="#c8a24a")
    side = t.face("jacket", "west")
    put_all(side, [(1, 11, STEEL[2]), (2, 10, STEEL[3]), (3, 10, STEEL[3]), (4, 11, STEEL[1]),
                   (3, 11, WOOD[2]), (3, 12, WOOD[1]), (3, 13, WOOD[2]), (3, 14, WOOD[1])])
    return t.save_profession(ASSETS, "miner", hat="full")


def netherworker():
    """A scorched crimson hood with a short mantle over the shoulders, a dark leather jerkin with two strapped
    gold buckles, singed at the hem."""
    t = vg.VillagerTexture()
    hood = vg.cloth("#7a1f24")
    lea = vg.cloth("#4a3022")
    char = "#231816"

    for side in vg.SIDES:                               # the hood: lit crown, folds falling to a darker edge
        f = t.face("hat", side)
        vg._cloth(f, hood, range(0, 10), seed=15, noise=0)
        if side == "front":
            f.clear(rows=range(2, 10), cols=range(1, 7))
            for x in range(1, 7):
                f.put(x, 1, hood[1])                    # shadowed inner edge over the brow
            for y in range(2, 10):
                f.put(0, y, hood[3])                    # the opening's rim: lit on the left, shaded on the right
                f.put(7, y, hood[1])
        else:
            vg._folds(f, hood, range(1, 10), (2, 5) if side == "back" else (3,), seed=16)
    dome_top(t, hood, base=2, ring=1)
    scorch = "#3d1a19"                                  # singed patches low on the hood
    put_all(t.face("hat", "west"), [(0, 8, scorch), (1, 8, scorch)])
    put_all(t.face("hat", "back"), [(4, 7, scorch), (5, 7, scorch)])
    put_all(t.face("hat", "east"), [(5, 9, scorch), (6, 9, scorch)])

    for side in vg.SIDES:
        g = t.face("jacket", side)
        vg._cloth(g, lea, range(3, 15), seed=13, noise=0.04)
        if side in ("front", "back"):
            vg._folds(g, lea, range(5, 15), (1, 5), seed=14 + (side == "back"))
        for y in range(0, 3):
            for x in range(g.w):
                g.put(x, y, hood[3] if y == 0 else (hood[1] if y == 2 else hood[2]))
        for y in (9, 13):
            for x in range(g.w):
                g.put(x, y, lea[0])
    vg._cloth(t.face("jacket", "top"), hood, hem=False, noise=0)
    front = t.face("jacket", "front")
    put_all(front, [(1, 9, GOLD[2]), (2, 9, GOLD[1]), (1, 13, GOLD[2]), (2, 13, GOLD[1]),
                    (6, 14, char), (5, 14, char)])
    back = t.face("jacket", "back")
    put_all(back, [(1, 14, char), (2, 14, char), (5, 11, char), (6, 11, char)])
    return t.save_profession(ASSETS, "netherworker", hat="full")


def nurse():
    """A small white cap with a pink heart, a white bib apron trimmed in pink (hem, straps, bow)."""
    t = vg.VillagerTexture()
    white = LINEN
    pink = Ramp(["#a83d68", "#c24d7c", "#e86a9a", "#f08db3"], name="pink")

    crown(t, white, range(0, 3), band=white[1])
    dome_top(t, white, base=3, ring=2)
    f = t.face("hat", "front")
    put_all(f, [(2, 0, pink[2]), (4, 0, pink[2]), (2, 1, pink[2]), (3, 1, pink[3]), (4, 1, pink[2]), (3, 2, pink[1])])

    bib_apron(t, white, top=2, bottom=17, strap=pink[2], tie=pink)
    for side in ("west", "east", "back"):               # pink ties round the waist
        g = t.face("jacket", side)
        for x in range(g.w):
            if side != "back" or x not in (3, 4):
                g.put(x, 9, pink[1])
    front = t.face("jacket", "front")
    for x in range(1, 7):
        front.put(x, 17, pink[2])
    return t.save_profession(ASSETS, "nurse", hat="full")


DRAW = [ball_smith, bard, beekeeper, carpenter, chef, composter, ferryman, florist, fossil_scientist, guard, innkeeper,
        lumberjack, miner, netherworker, nurse]

if __name__ == "__main__":
    run(DRAW)
