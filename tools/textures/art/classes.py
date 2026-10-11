"""Dressed by class (ROADMAP 34.17): the layer a villager's social class wears over their job's outfit, and the elder's
face (34.19's look). Each is three versions, every one a different idea, for the owner's picker page
(tools/textures/picks/classes_page.py); until he picks, version 1 of each is the texture the game uses:
textures/entity/villager/class/<class>.png (artisan, burgher, noble) and class/elder.png. Peasants wear no class layer:
their job's outfit is the owner-approved look.

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

SIDES = ("front", "west", "back", "east")
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


def arms_rows(t, rows, r, sides=SIDES, lit=True):
    """Bands round both arms (the arm box is shared by the two arms)."""
    for side in sides:
        panel(t.face("arm", side), r, rows, range(4), lit=lit, hem=False)


# ------------------------------------------------------------------------------------------------ artisan
# Skilled hands: workshop leathers, ochre and workman's blue. Each idea sits at the neck, the waist or the arms, so the job's
# apron and hat stay in front.

def artisan_v1():
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


def artisan_v2():
    """A wide leather tool belt: a hammer and a chisel in its loops on the hips, a pocket of nails on the front, a
    loop of files at the back."""
    t = vg.VillagerTexture()
    iron = Ramp(["#4f4f55", "#7b7b82", "#a9a9b0", "#d6d6dc"], name="iron")
    wood = Ramp(["#5a3b1e", "#7c5530", "#9b7042"], name="handle")
    f = jacket(t, "front")
    for x in range(8):                                        # the belt: rows 8..9, above the badge
        put(f, x, 8, LEATHER[3])
        put(f, x, 9, LEATHER[1])
    put(f, 1, 8, GOLD[3])                                     # the buckle on the right of the front
    put(f, 1, 9, GOLD[1])
    put(f, 2, 8, GOLD[2])
    put(f, 2, 9, GOLD[0])
    for y in range(10, 13):                                   # a leather pocket of nails, left of the badge
        for x in range(0, 3):
            put(f, x, y, LEATHER[2] if y < 12 else LEATHER[0])
    put(f, 0, 10, LEATHER[3])
    put(f, 1, 10, iron[3])                                    # nail heads
    put(f, 2, 10, iron[2])
    for side in ("west", "east", "back"):
        s = jacket(t, side)
        for x in range(s.w):
            put(s, x, 8, LEATHER[3] if side != "back" else LEATHER[2])
            put(s, x, 9, LEATHER[1])
    w = jacket(t, "west")                                     # the hammer, hanging head up
    put(w, 2, 7, iron[3])
    put(w, 3, 7, iron[2])
    put(w, 4, 7, iron[1])
    for y in range(8, 14):
        put(w, 3, y, wood[2] if y % 2 else wood[1])
    put(w, 3, 14, wood[0])
    e = jacket(t, "east")                                     # the chisel: a wooden grip, a steel blade
    for y in range(8, 12):
        put(e, 2, y, wood[2] if y % 2 else wood[1])
    put(e, 2, 12, iron[2])
    put(e, 2, 13, iron[3])
    put(e, 3, 10, LEATHER[0])                                 # its loop
    b = jacket(t, "back")                                     # a loop of three files
    for x in (2, 4, 6):
        put(b, x, 10, iron[2])
        put(b, x, 11, iron[1])
    for x in range(1, 8):
        put(b, x, 12, LEATHER[0])
    return t


def artisan_v3():
    """Shirt sleeves rolled to the elbow (bare forearms, a linen roll at the elbow) under an ochre waistcoat with
    brass buttons."""
    t = vg.VillagerTexture()
    vest = vg.cloth("#9a6a2a")
    f = jacket(t, "front")                                    # the waistcoat: open down the middle (the job shows)
    panel(f, vest, range(0, 10), (0, 1, 2), hem=False)
    panel(f, vest, range(0, 10), (5, 6, 7), hem=False)
    for x in (0, 1, 2, 5, 6, 7):
        put(f, x, 9, vest[0])                                 # its hem
    put(f, 2, 9, GOLD[1])                                     # the lowest button, below the arms
    put(f, 2, 1, GOLD[3])
    put(f, 5, 1, vest[3])                                     # a watch pocket's edge
    put(f, 6, 1, vest[3])
    for side, edge in (("west", 5), ("east", 0)):
        panel(jacket(t, side), vest, range(0, 10), range(6), edge=edge)
    b = jacket(t, "back")
    panel(b, vest, range(0, 10), range(8))
    for x in (2, 3, 4, 5):                                    # the back's buckled strap
        put(b, x, 7, vest[1])
    put(b, 3, 7, GOLD[2])
    # rolled sleeves: a linen roll at the elbow end of the upper arm, bare forearms crossed
    for side in SIDES:
        a = t.face("arm", side)
        for x in range(4):
            put(a, x, 5, LINEN[3])
            put(a, x, 6, LINEN[2])
            put(a, x, 7, LINEN[1])
    for side, lit in (("front", 3), ("top", 3), ("bottom", 1)):
        m = t.face("arms_middle", side)
        for y in range(m.h):
            for x in range(m.w):
                c = SKIN[lit] if y == (0 if side != "top" else m.h - 1) else SKIN[2]
                if x == 4:
                    c = SKIN[0]                               # the seam where the forearms cross
                if x in (0, 7):
                    c = LINEN[2]                              # the roll, seen at each end
                put(m, x, y, c)
    return t


# ------------------------------------------------------------------------------------------------ burgher
# Townsfolk of standing: dark broadcloth, white linen, a little gold. Sober, never showy.

def burgher_v1():
    """A white falling-band collar, dark cuffs at the wrists, and a gold watch chain looped below the arms."""
    t = vg.VillagerTexture()
    dark = vg.cloth("#2b3048")
    f = jacket(t, "front")
    for x in range(8):
        put(f, x, 0, LINEN[3] if x < 4 else LINEN[2])
    for x in (0, 1, 2, 5, 6, 7):
        put(f, x, 1, LINEN[2] if x < 4 else LINEN[1])
    put(f, 3, 1, LINEN[1])                                    # the collar's two tabs, parted in the middle
    put(f, 4, 1, LINEN[0])
    for x in (1, 2, 5, 6):
        put(f, x, 2, LINEN[1])
    for side in ("west", "east"):
        s = jacket(t, side)
        for x in range(6):
            put(s, x, 0, LINEN[2])
            put(s, x, 1, LINEN[1])
    b = jacket(t, "back")
    for x in range(8):
        put(b, x, 0, LINEN[2])
        put(b, x, 1, LINEN[1] if x not in (0, 7) else LINEN[0])
    for x in range(1, 7):
        put(b, x, 2, LINEN[0])
    # dark cuffs where the hands meet, and at the elbow end of the sleeve
    m = t.face("arms_middle", "front")
    for y in range(4):
        for x in (1, 2, 5, 6):
            put(m, x, y, dark[3] if y == 0 else dark[1] if y == 3 else dark[2])
    for side in ("top", "bottom"):
        mm = t.face("arms_middle", side)
        for y in range(mm.h):
            for x in (1, 2, 5, 6):
                put(mm, x, y, dark[2])
    for side in SIDES:
        a = t.face("arm", side)
        for x in range(4):
            put(a, x, 6, dark[3])
            put(a, x, 7, dark[1])
    # the watch chain: from a buttonhole at the middle, looped to a pocket on the right hip
    chain(f, [(3, 9), (2, 10), (1, 10), (0, 9)])
    put(f, 0, 10, GOLD[2])                                    # the watch's bow in the pocket
    put(f, 0, 11, dark[1])                                    # the pocket's mouth
    put(f, 1, 11, dark[1])
    w = jacket(t, "west")
    put(w, 5, 9, GOLD[1])                                     # the chain's end round the side
    return t


def burgher_v2():
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


def burgher_v3():
    """A short shoulder cape in bottle-green wool, closed at the throat with a pewter clasp, falling to the elbows."""
    t = vg.VillagerTexture()
    slate = vg.cloth("#2f5240")
    pewter = Ramp(["#5b5f66", "#8a8f98", "#b8bcc4"], name="pewter")
    f = jacket(t, "front")
    for x in range(8):
        put(f, x, 0, slate[3])
    for x in (0, 1, 2, 5, 6, 7):
        put(f, x, 1, slate[2])
        put(f, x, 2, slate[1] if x in (2, 5) else slate[2])
    put(f, 3, 0, pewter[2])                                   # the clasp at the throat
    put(f, 4, 0, pewter[1])
    put(f, 3, 1, pewter[0])
    put(f, 4, 1, pewter[0])
    for side, edge in (("west", 5), ("east", 0)):
        s = jacket(t, side)
        panel(s, slate, range(0, 7), range(6), edge=edge)
    b = jacket(t, "back")
    panel(b, slate, range(0, 8), range(8))
    for y in range(2, 7):                                     # two soft folds
        put(b, 2, y, slate[1])
        put(b, 5, y, slate[1])
    put(b, 3, 4, slate[3])
    for x in range(8):
        put(b, x, 7, slate[0])
    # over the shoulders: the cape covers the upper arms
    arms_rows(t, range(0, 4), slate, sides=("west", "back", "east", "front"))
    for side in ("west", "back", "east", "front"):
        a = t.face("arm", side)
        for x in range(4):
            put(a, x, 3, slate[0])                            # its hem
    panel(t.face("arm", "top"), slate, range(4), range(4), hem=False)
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


def noble_v1():
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


def noble_v2():
    """An emerald velvet half-cape over the right shoulder, edged and dotted in gold embroidery."""
    t = vg.VillagerTexture()
    velvet = vg.cloth("#1f5e45")
    f = jacket(t, "front")                                    # where it comes over the right shoulder
    for y in range(0, 3):
        for x in range(0, 3):
            put(f, x, y, velvet[3] if y == 0 else velvet[2])
    put(f, 3, 0, GOLD[2])                                     # the gold edge running to the clasp
    put(f, 3, 1, GOLD[1])
    put(f, 2, 2, GOLD[1])
    put(f, 3, 2, GOLD[3])                                     # the clasp
    for y in range(9, 15):                                    # its fall below the arms, on the right
        put(f, 0, y, velvet[2] if y < 14 else GOLD[1])
    put(f, 1, 9, velvet[1])
    w = jacket(t, "west")
    panel(w, velvet, range(0, 15), range(6), hem=False, edge=0)
    for x in range(6):
        put(w, x, 14, GOLD[1] if x % 2 else GOLD[2])          # the embroidered hem
    for x, y in ((2, 4), (4, 7), (1, 9), (3, 11)):
        put(w, x, y, GOLD[2])                                 # embroidered dots
    put(w, 5, 0, GOLD[3])
    b = jacket(t, "back")                                     # the back: from the right side to the middle, cut on the slant
    for y in range(0, 15):
        x0 = max(1, 4 - y // 4)
        for x in range(x0, 8):
            put(b, x, y, velvet[3] if y == 0 else velvet[2])
        put(b, x0, y, GOLD[2] if y % 2 else GOLD[1])           # its gold edge
    for x in range(1, 8):
        put(b, x, 14, GOLD[1] if x % 2 else GOLD[2])
    for x, y in ((5, 3), (6, 7), (4, 9), (6, 11)):
        put(b, x, y, GOLD[3])
    for y in range(3, 13):
        if y not in (7, 11):
            put(b, 6, y, velvet[1])                           # a fold between the dots
    e = jacket(t, "east")
    for x in range(6):
        put(e, x, 0, GOLD[1])                                 # the cord round the left of the neck
    return t


def noble_v3():
    """A royal-blue sash from the left shoulder to the right hip, held at the shoulder by a jewelled gold clasp,
    tied in a bow at the hip."""
    t = vg.VillagerTexture()
    blue = vg.cloth("#2a4a9e")
    ruby = ["#7a0f1e", "#c0283a", "#f06a78"]
    f = jacket(t, "front")
    # above the arms: the sash at the left shoulder (viewer's right) with the clasp
    for x, y in ((7, 0), (6, 0), (7, 1), (5, 1), (6, 1), (4, 2), (5, 2)):
        put(f, x, y, blue[3] if y == 0 else blue[2])
    put(f, 6, 0, GOLD[3])                                     # the clasp: a ruby set in gold
    put(f, 7, 0, GOLD[2])
    put(f, 6, 1, ruby[1])
    put(f, 7, 1, GOLD[1])
    put(f, 6, 2, GOLD[0])
    # below the arms: the sash reaches the right hip
    for x, y in ((3, 9), (2, 9), (2, 10), (1, 10), (1, 11), (0, 11), (0, 12)):
        put(f, x, y, blue[2] if (x + y) % 2 else blue[1])
    put(f, 3, 9, blue[3])
    w = jacket(t, "west")                                     # the bow and its tails on the right hip
    for x, y in ((5, 11), (4, 11), (5, 12), (3, 11), (3, 12), (4, 12)):
        put(w, x, y, blue[2])
    put(w, 4, 11, blue[3])
    put(w, 4, 12, blue[0])                                    # the knot
    for y in range(13, 17):
        put(w, 3, y, blue[1])
        put(w, 5, y, blue[2] if y < 16 else blue[0])
    put(w, 3, 16, GOLD[2])                                    # gold fringe on the tails
    put(w, 5, 17, GOLD[2])
    e = jacket(t, "east")                                     # over the left shoulder
    put(e, 0, 0, blue[3])
    put(e, 1, 0, blue[2])
    b = jacket(t, "back")                                     # down the back, left shoulder to right hip
    for y in range(0, 12):
        x = min(7, y * 7 // 11)
        put(b, x, y, blue[2])
        if x + 1 < 8:
            put(b, x + 1, y, blue[1])
    for y in range(0, 12, 3):
        x = min(7, y * 7 // 11)
        put(b, x, y, blue[3])
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


def beard(t, c=GREY, long=False):
    """A beard round the mouth and the jaw, up the sides of the face to the ears."""
    h = t.face("head", "front")
    for x in (0, 7):
        put(h, x, 7, c[2])
    for x in (2, 5):
        put(h, x, 7, c[3])                                    # the moustache, either side of the nose
    for x in (0, 1, 6, 7):
        put(h, x, 8, c[2])
    for x in range(8):
        put(h, x, 9, c[1] if x in (0, 7) else c[2])
    if long:
        for x in (2, 5):
            put(h, x, 8, c[3])
        n = t.face("nose", "front")                           # the beard below the nose's tip
        put(n, 0, 3, c[3])
        put(n, 1, 3, c[2])
        bt = t.face("head", "bottom")                         # under the chin, seen from below
        for x in range(8):
            put(bt, x, 0, c[1])
    for side, xs in (("west", (5, 6, 7)), ("east", (0, 1, 2))):
        s = t.face("head", side)
        for x in xs:
            for y in range(7, 10):
                near = (x == 7) if side == "west" else (x == 0)
                put(s, x, y, c[2] if near else c[1])
        ear = 4 if side == "west" else 3                       # the sideburn up to the ear
        put(s, ear, 6, c[1])
        put(s, ear, 7, c[1])


def elder_v1():
    """Grey brows and a short grey beard."""
    t = vg.VillagerTexture()
    brows(t.face("head", "front"))
    beard(t)
    return t


def elder_v2():
    """White hair at the temples and round the back of the head, the brows gone grey with it."""
    t = vg.VillagerTexture()
    white = GREY
    h = t.face("head", "front")
    brows(h)
    for y in range(2, 8):                                     # the temples on the face's edges, down to the cheek
        put(h, 0, y, white[3] if y < 5 else white[2])
        put(h, 7, y, white[3] if y < 5 else white[2])
    for y in (3, 4):
        put(h, 1, y, white[4] if y == 3 else white[3])
        put(h, 6, y, white[4] if y == 3 else white[3])
    for side in ("west", "east"):
        s = t.face("head", side)
        for y in range(2, 8):
            for x in range(8):
                front = (x == 7) if side == "west" else (x == 0)
                ear = x in (3, 4)
                if ear and y >= 5:
                    continue                                  # the ear stays bare
                c = white[4] if y == 2 else white[3] if front else white[2]
                if y >= 6 and not front:
                    c = white[1]
                put(s, x, y, c)
    b = t.face("head", "back")
    for y in range(2, 9):
        for x in range(8):
            put(b, x, y, white[3] if y == 2 else white[2] if y < 7 else white[1])
    for x in (2, 5):
        for y in range(3, 7):
            put(b, x, y, white[1])                            # combed strands
    tp = t.face("head", "top")                                # thin on top: a fringe round the crown's edge
    for x in range(8):
        put(tp, x, 0, white[2])
    for y in range(8):
        put(tp, 0, y, white[2])
        put(tp, 7, y, white[2])
    return t


def elder_v3():
    """Round wire spectacles and a full grey beard."""
    t = vg.VillagerTexture()
    wire = Ramp(["#4a3a1e", "#8a6a2a", "#c9a24a"], name="wire")
    h = t.face("head", "front")
    for x in (1, 2, 5, 6):
        put(h, x, 5, wire[2])                                 # the rims' lit top, over the brow
        put(h, x, 7, wire[1])                                 # and their lower edge
    for x in (0, 3, 4, 7):                                    # the rims either side of each eye, the bridge between
        put(h, x, 6, wire[1])
    put(h, 3, 5, GREY[2])                                     # grey brow hairs between the lenses
    put(h, 4, 5, GREY[2])
    for side, xs in (("west", range(2, 7)), ("east", range(1, 6))):
        s = t.face("head", side)                              # the arms back to the ears
        for x in xs:
            put(s, x, 6, wire[0])
    beard(t, long=True)
    h.put(0, 7, GREY[1])
    h.put(7, 7, GREY[1])
    return t


# ------------------------------------------------------------------------------------------------ the set

VERSIONS = {
    "artisan": [("v1", "Neckerchief and belt pouch", artisan_v1), ("v2", "Leather tool belt", artisan_v2),
                ("v3", "Rolled cuffs and a waistcoat", artisan_v3)],
    "burgher": [("v1", "White collar, dark cuffs, watch chain", burgher_v1),
                ("v2", "Buttoned coat edge, pocket square", burgher_v2), ("v3", "Short cape", burgher_v3)],
    "noble": [("v1", "Fur-trimmed mantle, chain of office", noble_v1),
              ("v2", "Velvet half-cape, gold embroidery", noble_v2), ("v3", "Sash with a jewelled clasp", noble_v3)],
    "elder": [("v1", "Grey brows and a short beard", elder_v1), ("v2", "White hair at the temples", elder_v2),
              ("v3", "Spectacles and a grey beard", elder_v3)],
}
# What the game wears until the owner picks (34.18 swaps his picks in here).
PICKED = {"artisan": "v1", "burgher": "v1", "noble": "v1", "elder": "v1"}


def draw_version(name, version):
    return dict((v, fn) for v, _, fn in VERSIONS[name])[version]()


def classes():
    """The picked version of each class layer and the elder's face, at textures/entity/villager/class/<name>.png."""
    out = []
    for name, version in PICKED.items():
        path = CLASS_DIR / f"{name}.png"
        draw_version(name, version).save(path)
        out.append(path)
    return out


DRAW = [classes]

if __name__ == "__main__":
    run(DRAW)
