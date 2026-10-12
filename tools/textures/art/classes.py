"""Dressed by class (ROADMAP 34.17, the owner's picks in 34.17a): the layer a villager's social class wears over their
job's outfit, and the elder's face (34.19's look), at textures/entity/villager/class/<class>.png (artisan, burgher,
noble) and class/elder.png. The owner picked one of three versions per row on 2026-10-11 (Artisan 1, Burgher 2,
Noble 1, Elder 1); only those are drawn here. Peasants wear no class layer: their job's outfit is the owner-approved
look.

The rules every class layer keeps, so the job stays readable under it:
- never on the head: the job's hat (and the biome's) stays as it is;
- nothing under the level badge (jacket-front x 4..7, rows 10..13);
- the back painted (the layer reads from behind too), but the job's apron, robe front and hat stay in view;
- clean cloth (no weave): these are good clothes.
The elder's layer is the other way round: only the face (the head box, under any hat), nothing on the body.

Face-local coordinates as in villagers_2.py: on a front face x 0 is the villager's right (the viewer's left); the west
face is the villager's right side (its x 5 touches the front), the east face the left side (x 0 touches the front); the
back face's x 0 meets the east side. The arm boxes are shared by both arms; their row 7 is the elbow end, and the
crossed forearms (arms_middle front) meet in the middle, x 3..4."""
from artlib import *  # noqa: F401,F403

CLASS_DIR = ASSETS / "textures" / "entity" / "villager" / "class"

GOLD = Ramp(["#8a5c12", "#b8841c", "#e0b03a", "#f6dc78"], name="gold")
LINEN = Ramp(["#a9a28e", "#c9c2ae", "#e2ddcc", "#f2efe4"], name="linen")      # white cloth: pale, never pure white
ERMINE = Ramp(["#b9b3a4", "#d8d3c6", "#ece8de", "#f7f5ef"], name="ermine")
ERMINE_TAIL = "#24222a"
LEATHER = vg.cloth("#7a4a26")
SKIN = Ramp(["#905e43", "#b37b62", "#b78272", "#be886c"], name="skin")      # the villager's own skin (villager.png)
BADGE = {(x, y) for x in range(4, 8) for y in range(10, 14)}               # jacket front, under the level badge


# ------------------------------------------------------------------------------------------------ painting helpers

def put(face, x, y, c):
    """Puts a pixel, refusing the level badge's area on the jacket front."""
    if face.part == "jacket" and face.side == "front" and (x, y) in BADGE:
        raise ValueError(f"({x}, {y}) is under the level badge")
    face.put(x, y, c)


def panel(face, r, rows, cols, lit=True, hem=True, edge=None):
    """Clean cloth over rows x cols of a face: the mid shade, a lit top row, a darker hem row; `edge` a column (or
    columns) given the shadow shade, where the cloth folds round a corner."""
    rows, cols = list(rows), list(cols)
    m = len(r) // 2
    for y in rows:
        for x in cols:
            c = r[m]
            if lit and y == rows[0]:
                c = r[m + 1]
            elif hem and y == rows[-1]:
                c = r[m - 2]
            elif edge is not None and x in (edge if isinstance(edge, (list, tuple, set)) else (edge,)):
                c = r[m - 1]
            put(face, x, y, c)


def chain(face, pixels, r=GOLD):
    """A gold chain: links alternate the lit and the mid shade, so it reads as links and not a wire."""
    for i, (x, y) in enumerate(pixels):
        put(face, x, y, r[3] if i % 2 == 0 else r[1])


def jacket(t, side):
    return t.face("jacket", side)


# ------------------------------------------------------------------------------------------------ artisan
# Skilled hands: workshop leather and workman's blue, at the neck and the hip, so the job's apron and hat stay in front.

def artisan():
    """A blue neckerchief knotted at the back of the neck, and a leather belt pouch on the hip with a brass stud."""
    t = vg.VillagerTexture()
    kerchief = vg.cloth("#2f5f8a")            # workman's blue: it stands out on the warm job colours
    f = jacket(t, "front")                                    # the kerchief's point, above the crossed arms
    for x in range(0, 8):
        put(f, x, 0, kerchief[3] if x in (0, 7) else kerchief[2])
    for x in range(1, 7):
        put(f, x, 1, kerchief[2] if x not in (1, 6) else kerchief[1])
    put(f, 3, 2, kerchief[1])
    put(f, 4, 2, kerchief[1])
    put(f, 2, 1, kerchief[3])                                     # a fold lit from the top left
    for side in ("west", "east"):                             # round the neck
        s = jacket(t, side)
        for x in range(6):
            put(s, x, 0, kerchief[2])
    b = jacket(t, "back")                                     # the knot and its two tails
    for x in range(8):
        put(b, x, 0, kerchief[2])
    put(b, 3, 1, kerchief[0])
    put(b, 4, 1, kerchief[1])
    put(b, 3, 2, kerchief[2])
    put(b, 4, 2, kerchief[1])
    put(b, 2, 3, kerchief[2])
    put(b, 5, 3, kerchief[1])
    # the pouch: a strap round the hips at row 9 (above the badge), the bag on the right hip, seen from the front corner
    for side in ("west", "back", "east"):
        s = jacket(t, side)
        for x in range(s.w):
            put(s, x, 9, LEATHER[1])
    for x in range(0, 4):
        put(f, x, 9, LEATHER[1])
    w = jacket(t, "west")
    for y in range(10, 14):
        for x in range(2, 6):
            c = LEATHER[3] if y == 10 else LEATHER[0] if y == 13 else LEATHER[2]
            put(w, x, y, c)
    for x in range(2, 6):
        put(w, x, 11, LEATHER[1])                             # the flap's edge
    put(w, 3, 11, GOLD[2])                                    # brass stud
    for y in range(10, 13):                                   # the pouch's front edge, from the front
        put(f, 0, y, LEATHER[2] if y < 12 else LEATHER[0])
    put(f, 0, 10, LEATHER[3])
    return t


# ------------------------------------------------------------------------------------------------ burgher
# Townsfolk of standing: dark broadcloth, white linen, a little gold. Sober, never showy.

def burgher():
    """The buttoned edge of a dark frock coat down the front, its tails behind, and a pocket square at the breast."""
    t = vg.VillagerTexture()
    coat = vg.cloth("#2f2a3a")
    f = jacket(t, "front")
    panel(f, coat, range(0, 20), (0, 1), hem=True, edge=1)    # the coat's buttoned edge, the villager's right
    for x in (6, 7):
        for y in range(0, 3):
            put(f, x, y, coat[3] if y == 0 else coat[2])      # its other lapel, above the arms
    for x in (6, 7):
        for y in range(14, 20):
            put(f, x, y, coat[0] if y == 19 else coat[2])     # and its skirt below the badge
    for y in (9, 12, 15, 18):
        put(f, 1, y, GOLD[2])                                 # brass buttons down the edge
    put(f, 6, 1, LINEN[3])                                    # the pocket square: linen, a red peak
    put(f, 7, 1, LINEN[2])
    put(f, 6, 0, "#c0392b")
    for side, edge in (("west", 5), ("east", 0)):
        panel(jacket(t, side), coat, range(0, 20), range(6), edge=edge)
    b = jacket(t, "back")
    panel(b, coat, range(0, 20), range(8))
    for y in range(11, 20):                                   # the tails: a vent up the middle
        put(b, 3, y, coat[0])
        put(b, 4, y, coat[1])
    put(b, 2, 10, GOLD[1])                                    # two buttons at the waist
    put(b, 5, 10, GOLD[1])
    return t


# ------------------------------------------------------------------------------------------------ noble
# The great families: velvet, fur and gold. The richest layer, still clear of the hat, the apron and the badge.

def ermine(face, rows, cols, spots):
    """Ermine fur: creamy white with the black tail tips (`spots`)."""
    rows = list(rows)
    for y in rows:
        for x in cols:
            put(face, x, y, ERMINE[3] if y == rows[0] else ERMINE[2] if y != rows[-1] else ERMINE[1])
    for x, y in spots:
        put(face, x, y, ERMINE_TAIL)


def noble():
    """A crimson mantle trimmed with ermine at the shoulders, and a gold chain of office across the chest."""
    t = vg.VillagerTexture()
    crimson = vg.cloth("#8c1d2a")
    f = jacket(t, "front")
    ermine(f, range(0, 2), range(8), [(1, 0), (6, 0)])        # the fur collar
    chain(f, [(0, 2), (1, 2), (2, 3), (5, 3), (6, 2), (7, 2)])  # the chain dips under the crossed arms
    put(f, 3, 2, GOLD[2])                                     # its medallion
    put(f, 4, 2, GOLD[1])
    for x in (3, 4):
        put(f, x, 3, GOLD[0])
    for side, back_cols, front_col in (("west", (0, 1, 2), 3), ("east", (3, 4, 5), 2)):
        s = jacket(t, side)
        ermine(s, range(0, 2), range(6), [(2, 0)])
        panel(s, crimson, range(2, 19), back_cols, lit=False, edge=back_cols[0] if side == "west" else back_cols[-1])
        for y in range(2, 19):
            put(s, front_col, y, ERMINE[2] if y % 4 else ERMINE_TAIL)   # the mantle's fur edge
        put(s, front_col, 1, GOLD[2])                          # the chain round the side
    b = jacket(t, "back")
    ermine(b, range(0, 2), range(8), [(2, 0), (5, 1)])
    panel(b, crimson, range(2, 19), range(8), lit=False)
    for y in range(4, 18):                                     # long velvet folds
        put(b, 2, y, crimson[1])
        put(b, 5, y, crimson[1])
        if 6 <= y <= 15:
            put(b, 3, y, crimson[3])
    for x in range(8):
        put(b, x, 18, ERMINE[2] if x % 3 else ERMINE_TAIL)     # the fur hem
    return t


# ------------------------------------------------------------------------------------------------ elder
# 34.19's look: a face layer on the head box, under any hat (the job's or the biome's), nothing on the body. Head front
# rows: 5 the brow, 6 the eyes, 7..9 the cheeks and mouth (the nose hides x 3..4 of rows 7..9).

GREY = Ramp(["#6f6c69", "#94918c", "#b8b5ae", "#d8d6d0", "#eeede8"], name="grey")


def brows(h, c=GREY):
    """Bushy grey brows: the unibrow parted and lightened, a lifted hair at each outer end."""
    for x in (0, 1, 2, 5, 6, 7):
        put(h, x, 5, c[2] if x in (0, 7) else c[3])
    put(h, 3, 5, SKIN[2])
    put(h, 4, 5, SKIN[2])
    put(h, 0, 4, c[1])
    put(h, 7, 4, c[1])


def beard(t, c=GREY):
    """A short beard round the mouth and the jaw, up the sides of the face to the ears."""
    h = t.face("head", "front")
    for x in (0, 7):
        put(h, x, 7, c[2])
    for x in (2, 5):
        put(h, x, 7, c[3])                                    # the moustache, either side of the nose
    for x in (0, 1, 6, 7):
        put(h, x, 8, c[2])
    for x in range(8):
        put(h, x, 9, c[1] if x in (0, 7) else c[2])
    for side, xs in (("west", (5, 6, 7)), ("east", (0, 1, 2))):
        s = t.face("head", side)
        for x in xs:
            for y in range(7, 10):
                near = (x == 7) if side == "west" else (x == 0)
                put(s, x, y, c[2] if near else c[1])
        ear = 4 if side == "west" else 3                       # the sideburn up to the ear
        put(s, ear, 6, c[1])
        put(s, ear, 7, c[1])


def elder():
    """Grey brows and a short grey beard."""
    t = vg.VillagerTexture()
    brows(t.face("head", "front"))
    beard(t)
    return t


# ------------------------------------------------------------------------------------------------ the set

# The owner's picks (2026-10-11): Artisan version 1, Burgher version 2, Noble version 1, Elder version 1.
LAYERS = {"artisan": artisan, "burgher": burgher, "noble": noble, "elder": elder}


def classes():
    """Each class layer and the elder's face, at textures/entity/villager/class/<name>.png."""
    out = []
    for name, draw in LAYERS.items():
        path = CLASS_DIR / f"{name}.png"
        draw().save(path)
        out.append(path)
    return out


DRAW = [classes]

if __name__ == "__main__":
    run(DRAW)
