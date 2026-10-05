"""Workstations, part 3: the Sieve, Storehouse, Teacher's Desk, Tinker's Bench, Trade Board, Training Dummy, Training
Post, Travel Post, Tutor's Desk, Undertaker's Table and Village Hall, in the style of the Builder's Bench (builders.py):
each face is its wood's vanilla planks tile with a 1 px frame in the darkest shade, and the job's feature drawn on it
as ASCII."""
import random

from artlib import *  # noqa: F401,F403

OAK = P("oak_planks")                     # 7 shades, darkest first
SPRUCE = P("spruce_planks")
SPRUCE_EDGE = Ramp(["#3b2713"] + list(SPRUCE)[1:], name="spruce")  # outlines: spruce bark (planks too close)
DARK_OAK = P("dark_oak_planks")           # 6 shades
STEEL = Ramp(["#5a5a5a", "#8c8c8c", "#b4b4b4", "#d2d2d2"], name="steel")       # as the Builder's Bench
IRON = Ramp(["#26272d", "#383a47", "#494b5f", "#6b6f85"], name="dark iron")     # the smithing table's
BRASS = Ramp(["#8a5a16", "#b8872f", "#d9a441", "#f0c96a"], name="brass")
GOLD = Ramp(["#b26411", "#dc9613", "#e9b115", "#fad64a"], name="gold")
PAPER = Ramp(["#856e36", "#bca160", "#e8d5a3", "#f9e8bf", "#fcfcf2"], name="parchment")   # the cartography table's
INK = "#7d6c55"                           # writing on paper
RED = Ramp(["#8e2020", "#a43434", "#c82f2f", "#dc4a4a"], name="red")          # the target block's
CREAM = Ramp(["#ebd7ba", "#f3ebdf", "#f7f2eb"], name="cream")
BIRCH = P("birch_planks")
CORK = ["#8e6035", "#9c6a3b", "#aa7743", "#b8844d"]
GRAVEL = ["#726b69", "#968e8e", "#b0aeae"]
BOOKS = {                                  # the bookshelf's spines: (light, dark)
    "R": ("#bc1616", "#911111"), "B": ("#3163a3", "#2a4e7a"), "G": ("#758e11", "#5e720d"),
    "O": ("#773007", "#632805"), "T": ("#118e6b", "#0c6b50"), "Y": ("#a89b0a", "#897e08"),
}
BOOK_LEGEND = {k: v[0] for k, v in BOOKS.items()} | {k.lower(): v[1] for k, v in BOOKS.items()}


def bench_face(seed, wood=OAK, edge=None):
    """A workstation face: the planks tile with a 1 px frame in the darkest shade, like the crafting table."""
    s = planks_tile(wood[1:6], boards=4, seed=seed)
    frame(s, edge or wood[0])
    return s


def drawer(s, x, y, knob, wood=OAK, label=False):
    """The Builder's Bench drawer (10 x 6) with a knob of two colours (light, dark), and a card in a brass holder above
    the knob if asked (like a catalogue drawer)."""
    s.paste(grid("""
        0000000000
        0555555540
        0544444430
        0544hj4430
        0433333320
        0000000000
    """, {"h": knob[0], "j": knob[1]}, ramp=wood), x, y)
    if label:
        s.paste(grid("""
            bppb
        """, {"p": PAPER[3], "b": BRASS[2]}), x + 3, y + 2)


# --- Sieve -----------------------------------------------------------------------------------------------------------
def sieve():
    """Top: the tray's string mesh over the open dark space, with a little heap of gravel on it; side: the top rail,
    gravel on the mesh seen edge-on with dust falling through, the bottom rail, then two legs with the open space
    between them; front: the same, with a bucket of sifted finds standing under the mesh."""
    dark = "#3b2c1e"
    string = "#d6cfb8"
    g = {"d": dark, "s": string, "a": GRAVEL[0], "b": GRAVEL[1], "c": GRAVEL[2]}
    top = bench_face(461)
    top.paste(grid("""
        dsddsddsddsd
        ssssssssssss
        dsddsddsddsd
        dsddsddsddsd
        ssssscbsssss
        dsddcbbaddsd
        dsdcbbaaddsd
        ssssaaasssss
        dsddsddsddsd
        dsddsddscbsd
        ssssssssssss
        dsddsddsddsd
    """, g), 2, 2)

    def side_face(seed):
        s = bench_face(seed)
        s.paste(grid("""
            0000000000000000
            0555555555555550
            0444434444443440
            0ddddddcbdddddd0
            0dddddcbbaddcdd0
            0ssssssssssssss0
            0ddddaddddddadd0
            0444444344444440
            0111111111111110
            0443000000004430
            0443000000004430
            0443555555554430
            0443444344444430
            0443000000004430
            0443000000004430
            0000000000000000
        """, g, ramp=OAK), 0, 0)
        return s

    side = side_face(462)
    front = side_face(463)
    front.paste(grid("""
        .0000.
        0yknc0
        232221
        211110
        211110
        .1110.
        .0000.
    """, {"y": GOLD[3], "k": "#3d3c3c", "n": GRAVEL[1], "c": GRAVEL[2]}, ramp=STEEL), 5, 8)
    return [top.save(block("sieve_top")), side.save(block("sieve_side")), front.save(block("sieve_front"))]


# --- Storehouse ------------------------------------------------------------------------------------------------------
def storehouse():
    """Every face is the side of a crate: a second, darker ring of battens inside the frame. Top: the lid with a
    batten across it and a rope tied round it, knotted in the middle; side: a diagonal brace; front: the porter's
    ledger, a clipboard hanging on a nail."""
    rope = {"l": "#e3d4a4", "m": "#c9b37e", "n": "#9c8350"}

    def crate(seed):
        s = bench_face(seed)
        for i in range(1, 15):
            for x, y in ((i, 1), (i, 14), (1, i), (14, i)):
                s.put(x, y, OAK[1])
        for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):     # nails at the corners
            s.put(x, y, STEEL[2])
        return s

    top = crate(321)
    top.paste(grid("""
        .....lm0....
        .....mn0....
        .....lm0....
        .....mn0....
        66666lm06666
        4444llmn0444
        0000mmnn0000
        .....mn0....
        .....lm0....
        .....mn0....
        .....lm0....
        .....mn0....
    """, rope, ramp=OAK), 2, 2)

    side = crate(322)
    for i in range(2, 14):                               # a diagonal brace, bottom left to top right
        side.put(i, 15 - i, OAK[6])
        if i > 2:
            side.put(i, 16 - i, OAK[0])
    side.put(3, 12, STEEL[1])                            # the brace nailed at both ends
    side.put(12, 3, STEEL[1])

    front = crate(323)
    front.paste(grid("""
        ...mn...
        00mmnn00
        0pppppp0
        0piiiip0
        0pppppp0
        0piiipp0
        0pppppp0
        0piiiip0
        0ppprrp0
        0qqqqqq0
        00000000
    """, {"m": STEEL[3], "n": STEEL[1], "p": PAPER[3], "q": PAPER[2], "i": INK, "r": RED[2]}, ramp=OAK), 4, 3)
    return [top.save(block("storehouse_top")), side.save(block("storehouse_side")),
            front.save(block("storehouse_front"))]


# --- Teacher's Desk --------------------------------------------------------------------------------------------------
BOOK = {"P": PAPER[4], "p": PAPER[3], "q": PAPER[2], "i": INK}


def teachers_desk():
    """Top: an open book with a red cover, an apple and a stick of chalk; front: a chalkboard in the desk's wooden
    frame with sums chalked on it and a chalk ledge; side: a drawer with a brass knob."""
    top = bench_face(371)
    top.paste(grid("""
        cPPPPqPPPPc
        cpiiiqiiipc
        cppppqppppc
        cpiipqiiipc
        cppppqppppc
        cpiiiqiippc
        cqqqqqqqqqc
        CCCCCCCCCCC
    """, BOOK | {"c": "#911111", "C": "#5e0b0b"}), 2, 5)
    top.paste(grid("""
        .sg
        Rrr
        rrd
    """, {"s": "#542409", "g": "#4a7a2a", "R": "#dd1725", "r": "#b4131e", "d": "#9c1017"}), 12, 1)
    top.paste(grid("""
        eef
    """, {"e": "#e4e6de", "f": "#b9bcb2"}), 10, 14)

    front = bench_face(373)
    front.paste(grid("""
        gggggggggggg
        ggwggwwwgggg
        gwwwgwgwgggg
        ggwggwwwgghg
        gggggggggghh
        gwwwgwwgwwwg
        gggggggggggg
        gwwgwwwgwwgg
        gggggggggggg
        ghhvvggggggg
        gggggggggggg
        555555555ww5
    """, {"g": "#2c4436", "h": "#33503f", "v": "#5d7a69", "w": "#d9ded6"}, ramp=OAK), 2, 2)

    side = bench_face(372)
    drawer(side, 3, 5, (BRASS[3], BRASS[1]), label=True)
    return [top.save(block("teachers_desk_top")), side.save(block("teachers_desk_side")),
            front.save(block("teachers_desk_front"))]


# --- Tinker's Bench --------------------------------------------------------------------------------------------------
STONE = {"a": "#686868", "b": "#858585", "c": "#504e4e"}                        # the furnace's
FIRE = {"k": "#211614", "e": "#c35d1b", "o": "#ff8f00", "y": "#ffd800", "w": "#ffff97"}
REDSTONE = {"r": "#aa0f01", "R": "#e8311a"}


def tinkers_bench():
    """A spruce bench bound in dark iron (like the smithing table). Top: a vise in one corner, a brass gear and a
    little redstone wire; front: a hammer, tongs and a file hung under the iron band, above the glowing mouth of a
    small forge; side: a drawer with an iron pull."""
    def face(seed, band=True):
        s = bench_face(seed, SPRUCE, edge=IRON[0])
        if band:                                          # the iron band round the bench's top edge, with rivets
            for x in range(1, 15):
                s.put(x, 1, IRON[3] if x in (2, 13) else IRON[2])
        return s

    top = face(481, band=False)
    top.paste(grid("""
        13331
        01110
        ..0..
        13331
        01110
        ..1..
    """, ramp=STEEL), 2, 2)
    top.paste(grid("""
        ..3.2..
        .33322.
        33...22
        .3.0.1.
        22...11
        .21111.
        ..1.1..
    """, ramp=BRASS), 7, 7)
    top.paste(grid("""
        .rrrr.
        .....r
        .....R
    """, REDSTONE), 8, 3)
    top.paste(grid("""
        r..
        r..
        .rr
    """, REDSTONE), 3, 11)

    front = face(483)
    front.paste(grid("""
        322..2..w.
        .w..2.1.v.
        .v..2.1.2.
        .w..1.0.1.
    """, {"w": OAK[5], "v": OAK[2]}, ramp=STEEL), 3, 2)
    front.paste(grid("""
        bbbbbbbbbb
        akkkkkkkka
        akkkkkkkka
        akkekkekka
        akeoekoeka
        aeoyoeyoea
        aoywyoyyoa
        cccccccccc
    """, STONE | FIRE), 3, 6)

    side = face(482)
    drawer(side, 3, 5, (STEEL[3], STEEL[1]), wood=SPRUCE_EDGE)
    return [top.save(block("tinkers_bench_top")), side.save(block("tinkers_bench_side")),
            front.save(block("tinkers_bench_front"))]


# --- Trade Board -----------------------------------------------------------------------------------------------------
NOTE = {"P": PAPER[4], "p": PAPER[3], "q": PAPER[2], "i": INK, "r": RED[2]}


def cork(s, x0, y0, x1, y1, seed):
    """A cork panel: small clusters of four close browns (a natural material: low contrast, no noise)."""
    c = clusters(CORK, weights=[1, 3, 2, 1], seed=seed, base=CORK[1], size=(2, 3), density=0.5)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            s.put(x, y, c.get(x, y))


def trade_board():
    """A cork board in an oak frame. Front: three notes pinned to it, each with a red pin and writing; side: the cork
    with one small note; top: the frame's plain boards."""
    top = bench_face(261)

    side = bench_face(262)
    cork(side, 2, 2, 13, 13, 262)
    side.paste(grid("""
        prp
        pip
        qqq
    """, NOTE), 7, 6)

    front = bench_face(263)
    cork(front, 2, 2, 13, 13, 263)
    front.paste(grid("""
        prpp
        piip
        pppp
        pipp
        qqqq
    """, NOTE), 3, 3)
    front.paste(grid("""
        pprp
        pppp
        piip
        qqqq
    """, NOTE), 9, 4)
    front.paste(grid("""
        pprpp
        piiip
        ppppp
        piipp
        qqqqq
    """, NOTE), 6, 9)
    return [top.save(block("trade_board_top")), side.save(block("trade_board_side")),
            front.save(block("trade_board_front"))]


# --- Training Dummy -------------------------------------------------------------------------------------------------
# Natural materials (the skill's natural-block rules): no frame, no baked light, low contrast, small clusters.
# The model shows only parts of some textures: the post's pole is u 7-9, v 2-14 (top: 7-9, 7-9); the straw arms and tuft
# are u 6-10, v 7-9; the body is burlap u 4-12, v 3-13 (sides u 5-11); the head is the whole burlap / face texture.
BURLAP = ["#8f6f45", "#a3804f", "#b28e5c", "#c19d6b"]
ROPE = ["#9a7c50", "#b99a68", "#cbb07e"]
STRAW = P("straw")
POST = ["#3f2a17", "#4d331d", "#5c3e24", "#6a4a2c", "#77563a"]
STITCH = "#3b2a18"


def burlap():
    """Coarse sackcloth: a 2 x 2 basket weave, threads lying across in one square and up and down in the next."""
    s = Sprite(16, 16)
    for y in range(16):
        for x in range(16):
            if ((x // 2) + (y // 2)) % 2 == 0:
                s.put(x, y, BURLAP[3] if y % 2 == 0 else BURLAP[2])
            else:
                s.put(x, y, BURLAP[2] if x % 2 == 0 else BURLAP[1])
    return s


def training_dummy():
    """A straw-stuffed burlap sack on a post, for guards to spar with. Burlap: the weave, a rope tied round (rows 2-3)
    and a darker patch sewn on with running stitches; face: X-stitched eyes and a red and white target where a mouth
    would be; straw: loose strands criss-crossed; post: a dark-stained pole with vertical grain and a knot."""
    sack = burlap()
    for x in range(16):                                   # the rope, twisted
        sack.put(x, 2, ROPE[2] if x % 3 != 2 else ROPE[1])
        sack.put(x, 3, ROPE[1] if x % 3 != 1 else ROPE[0])
    sack.paste(grid("""
        ..s.s.
        sppqq.
        .ppqqs
        sqqpp.
        .qqpps
        .s.s..
    """, {"s": STITCH, "p": "#957449", "q": "#86683f"}), 8, 7)

    face = burlap()
    for cx in (4, 11):                                    # X-stitched eyes
        for d in (-1, 0, 1):
            face.put(cx + d, 4 + d, STITCH)
            face.put(cx + d, 4 - d, STITCH)
    face.paste(grid("""
        ..rrrr..
        .rrwwrr.
        rrwwwwrr
        rwwrrwwr
        rwwrrwwr
        rrwwwwrr
        .rrwwrr.
        ..rrrr..
    """, {"r": RED[2], "w": CREAM[1]}), 4, 7)

    straw = Sprite(16, 16, STRAW[2])
    rnd = random.Random(403)
    for _ in range(26):
        x, y, dx = rnd.randrange(16), rnd.randrange(16), rnd.choice((-1, 1))
        c = rnd.choice((STRAW[5], STRAW[5], STRAW[7], STRAW[1], STRAW[0]))
        for k in range(rnd.randint(3, 5)):
            straw.put((x + dx * k) % 16, (y + k) % 16, c)

    post = Sprite(16, 16)
    base = [3, 3, 2, 3, 4, 3, 3, 2, 3, 3, 4, 3, 2, 3, 3, 4]
    for x in range(16):
        for y in range(16):
            post.put(x, y, POST[base[x]])
    rnd = random.Random(404)
    for _ in range(12):                                   # streaks along the grain
        x, y = rnd.randrange(16), rnd.randrange(16)
        c = POST[rnd.choice((1, 2, 4))]
        for k in range(rnd.randint(3, 6)):
            post.put(x, (y + k) % 16, c)
    for kx, ky in ((8, 9), (12, 2), (2, 12)):             # knots: a dark eye with a darker ring
        post.put(kx, ky, POST[0])
        post.put(kx + 1, ky, POST[1])
        post.put(kx, ky - 1, POST[1])
        post.put(kx, ky + 1, POST[1])
    return [sack.save(block("training_dummy_burlap")), face.save(block("training_dummy_face")),
            straw.save(block("training_dummy_straw")), post.save(block("training_dummy_post"))]


# --- Training Post ---------------------------------------------------------------------------------------------------
def training_post():
    """A sparring post: a red and white target painted on top; a red band wrapped round the sides, and on the front
    a white star on the band."""
    top = bench_face(201)
    top.paste(grid("""
        ....rrRR....
        ..rrrrrrdd..
        .rdwwwwvvrr.
        .rwwvvwwwwr.
        rrwwwrrwwwRr
        Rrwwrrddwwrr
        rrwwRrrrwwrd
        rdwwwrrwwwrr
        .rwwwwvvwwr.
        .rrvvwwwwRr.
        ..ddrrrrrr..
        ....RRrr....
    """, {"r": RED[2], "R": RED[3], "d": RED[1], "w": CREAM[1], "v": CREAM[0]}), 2, 2)

    def banded(seed):
        s = bench_face(seed)
        for x in range(1, 15):
            for y, c in ((6, RED[3]), (7, RED[2]), (8, RED[2]), (9, RED[1])):
                s.put(x, y, c)
        return s

    side = banded(203)
    for y in range(6, 10):                                # where the wrap's end overlaps
        side.put(11, y, RED[0])
    front = banded(202)
    front.paste(grid("""
        ...ww...
        ...ww...
        wwwwwwww
        .wwwwww.
        ..wwww..
        .ww..ww.
        .w....w.
    """, {"w": CREAM[2]}), 4, 4)
    return [top.save(block("training_post_top")), side.save(block("training_post_side")),
            front.save(block("training_post_front"))]


# --- Travel Post -----------------------------------------------------------------------------------------------------
SIGN_INK = "#4a3b2a"


def travel_post():
    """A signpost. Top: a compass rose inked on the boards, north in red; sides: two birch arrow boards pointing
    different ways, with writing on them; front: the same, with the route's blue marks painted on the post above and
    below."""
    top = bench_face(141)
    top.paste(grid("""
        .....R.....
        ....RRR....
        .....R.....
        ....kKk....
        ...k.K.k...
        KKKKKoKKKKK
        ...k.K.k...
        ....kKk....
        .....K.....
        .....K.....
        .....K.....
    """, {"R": RED[2], "K": SIGN_INK, "k": "#7a6344", "o": BRASS[3]}), 2, 2)

    right = grid("""
        6666666666..
        44444444444.
        4ii4iii4ii44
        33333333333.
        1111111111..
    """, {"i": SIGN_INK}, ramp=BIRCH)

    def signs(seed):
        s = bench_face(seed)
        s.paste(right, 2, 2)
        s.paste(right.flip_x(), 2, 9)
        return s

    side = signs(143)
    front = signs(142)
    for x, y in ((7, 0), (8, 0), (7, 1), (8, 1), (7, 14), (8, 14), (7, 15), (8, 15)):
        front.put(x, y, "#3163a3" if y in (0, 14) else "#2a4e7a")
    return [top.save(block("travel_post_top")), side.save(block("travel_post_side")),
            front.save(block("travel_post_front"))]


# --- Tutor's Desk ----------------------------------------------------------------------------------------------------
def book_cubby(s, y):
    """A row of books standing in a dark cubby under the desk top, on a shelf (the Builder's Bench's cubby)."""
    s.paste(grid("""
        000000000000
        0Rr00Gg000T0
        0RrBbGg0YyT0
        0RrBbGg0YyT0
        0RrBbGg0YyT0
        0RrBbGg0YyT0
        111111111111
    """, BOOK_LEGEND, ramp=OAK), 2, y)


def tutors_desk():
    """Top: an open book with a green cover, an inkwell and a quill; side: a row of books in a cubby under the desk
    top; front: the books above a drawer with a brass knob."""
    top = bench_face(241)
    top.paste(grid("""
        cPPPPqPPPPc
        cpiiiqiiipc
        cppppqppppc
        cpiipqiiipc
        cppppqppppc
        cpiiiqiippc
        cqqqqqqqqqc
        CCCCCCCCCCC
    """, BOOK | {"c": "#4a6b2f", "C": "#34501f"}), 1, 4)
    top.paste(grid("""
        ..f
        .fe
        .e.
        e..
        Kk.
        kk.
    """, {"f": "#e8e8e8", "e": "#bebebe", "k": "#1d1f2b", "K": "#3a3f5a"}), 12, 7)

    side = bench_face(243)
    book_cubby(side, 4)
    front = bench_face(242)
    book_cubby(front, 2)
    drawer(front, 3, 9, (BRASS[3], BRASS[1]))
    return [top.save(block("tutors_desk_top")), side.save(block("tutors_desk_side")),
            front.save(block("tutors_desk_front"))]


# --- Undertaker's Table ---------------------------------------------------------------------------------------------
LINEN = {"L": "#d8d2c2", "e": "#bdb6a2"}
DRAPE = {"d": "#2b2433", "l": "#382f42", "L": "#443a50", "k": "#1f1a26", "T": "#d2d2dc", "S": "#b7b7c4",
         "s": "#8e8e9c"}
LILY = {"w": "#f7f5ee", "y": "#e8c547", "g": "#4d7a3a", "G": "#3d6a2e"}


def undertakers_table():
    """A dark oak table. Top: a linen runner laid down the middle and over the edges, a white lily on it, and a candle
    in a brass holder at two corners; sides: a dark drape with folds, a silver braid along the top and a fringe; front:
    the drape with a lily embroidered on it."""
    top = bench_face(421, DARK_OAK)
    runner = grid("\n".join(["eLLLLe"] * 16), LINEN)
    top.paste(runner, 5, 0)
    top.paste(grid("""
        .ww.
        wyyw
        ewwe
        .eg.
        .Gg.
        ..g.
        ..g.
    """, LILY | LINEN), 6, 4)
    candle = grid("""
        .f.
        .w.
        .v.
        bBb
    """, {"f": "#ffd257", "w": "#efe7c8", "v": "#cfc49a", "b": BRASS[1], "B": BRASS[3]})
    top.paste(candle, 2, 2)
    top.paste(candle, 11, 10)

    def draped(seed):
        s = bench_face(seed, DARK_OAK)
        s.paste(grid("""
            55545555554555
            TSsTSsTSsTSsTS
            dddddddddddddd
            ddLkdddddLkddd
            ddlkdddddlkddd
            ddlkdddddlkddd
            ddlkddLkdlkddd
            ddlkddlkdlkddd
            ddlkddlkdlkddd
            ddlkddlkdlkddd
            ddlkddlkdlkddd
            ddlkddlkdlkddd
            ddlkddlkdlkddd
            SdSdSdSdSdSdSd
        """, DRAPE, ramp=DARK_OAK), 1, 1)
        return s

    side = draped(422)
    front = draped(423)
    front.paste(grid("""
        .ww.
        wddw
        .ww.
        ..g.
        .Gg.
        ..g.
        ..g.
    """, LILY | {"w": "#e2ddcf", "d": DRAPE["d"]}), 6, 6)
    return [top.save(block("undertakers_table_top")), side.save(block("undertakers_table_side")),
            front.save(block("undertakers_table_front"))]


# --- Village Hall ----------------------------------------------------------------------------------------------------
MAP = {"p": PAPER[3], "P": PAPER[4], "o": PAPER[1], "W": "#6b9bd6", "w": "#4d7fc4", "f": "#6b962f", "F": "#577b26",
       "h": "#be2633", "H": "#8e2020", "s": GOLD[3], "S": "#fff1a0"}


def village_hall():
    """Dark timber. Top: a map of the village on parchment (a river, fields, red roofs and a gold star for the hall);
    front: a gold bell over a cork notice board with papers pinned to it; sides: two shelves of the village's
    records."""
    top = bench_face(361, DARK_OAK)
    top.paste(grid("""
        ppWwpppppppp
        ppWwppfFpppp
        ppWwppFfpppp
        ppWwpppppppp
        pppWwpppphHp
        pppWwppspppp
        pppWwpsSsppp
        pppWwppspppp
        ppWwphHppfFp
        ppWwpppppFfp
        ppWwppphHppp
        ppWwpppppppp
    """, MAP), 2, 2)

    front = bench_face(363, DARK_OAK)
    front.paste(grid("""
        ..32..
        .3321.
        .3221.
        332210
    """, ramp=GOLD), 5, 1)
    cork(front, 2, 5, 13, 12, 363)
    for art, x, y in (("""
        prpp
        piip
        pipp
        qqqq
    """, 3, 6), ("""
        pprp
        piip
        qqqq
    """, 9, 6), ("""
        pprp
        piip
        qqqq
    """, 6, 10), ("""
        rp
        qq
    """, 11, 10)):
        front.paste(grid(art, NOTE), x, y)

    side = bench_face(362, DARK_OAK)
    side.paste(grid("""
        000000000000
        0Rr0Gg000T00
        0RrBGg0OoT00
        0RrBGg0OoTY0
        0RrBGg0OoTY0
        0RrBGg0OoTY0
        555555555555
        000000000000
        0Yy0Bb0Rr0O0
        0YyTBb0RrGO0
        0YyTBbTRrGO0
        0YyTBbTRrGO0
    """, BOOK_LEGEND, ramp=DARK_OAK), 2, 2)
    return [top.save(block("village_hall_top")), side.save(block("village_hall_side")),
            front.save(block("village_hall_front"))]

# --- Cradle (30.12) --------------------------------------------------------------------------------------------------
def cradle():
    """A wooden cradle on rockers with a wool blanket, drawn like vanilla's beds and lectern. Wood: oak planks laid
    across, framed in the darkest shade (a built piece); end: the same with a heart cut in the headboard; rocker: a
    darker oak runner with its grain along it; blanket: white wool in small clusters with a red band at either end and a
    running stitch inside each band, like the bed's cover."""
    wool = P("wool_white")
    red = P("wool_red")
    wood = bench_face(301)
    end = bench_face(302)
    end.paste(grid("""
        .00.00.
        0333330
        0333330
        .03330.
        ..030..
        ...0...
    """, ramp=OAK), 5, 4)
    rocker = planks_tile([OAK[0], OAK[1], OAK[2], OAK[2], OAK[3]], boards=4, seed=303)
    blanket = clusters([wool[1], wool[3], wool[4], wool[5]], seed=304, base=wool[4], size=(1, 2), density=0.35)
    for x in range(16):
        for y, c in ((0, red[1]), (1, red[3]), (2, red[0]), (13, red[1]), (14, red[3]), (15, red[0])):
            blanket.put(x, y, c)
        if x % 3 == 1:
            blanket.put(x, 4, wool[0])
            blanket.put(x, 11, wool[0])
    return [wood.save(block("cradle_wood")), end.save(block("cradle_end")), rocker.save(block("cradle_rocker")),
            blanket.save(block("cradle_blanket"))]


# --- Harvest Idol (30.14) --------------------------------------------------------------------------------------------
WHEAT = P("wheat")                          # the wheat item's ramp, darkest first
TWINE = ["#6e5530", "#8a6c3e", "#a5854f"]   # hemp cord, darker than the straw it ties


def straw_strands(seed, base=4):
    """Bundled straw: strands standing up, each column one shade with breaks where a strand ends, like the hay bale's
    sides but finer; a few dark stalks and pale glints."""
    s = Sprite(16, 16)
    rnd = random.Random(seed)
    cols = [rnd.choice((3, 4, 4, 5, 6)) for _ in range(16)]
    for x in range(16):
        for y in range(16):
            s.put(x, y, STRAW[cols[x]])
    for _ in range(18):                                   # strand ends: a short run of another shade
        x, y = rnd.randrange(16), rnd.randrange(16)
        c = STRAW[rnd.choice((1, 2, 7, 5))]
        for k in range(rnd.randint(2, 4)):
            s.put(x, (y + k) % 16, c)
    return s


def harvest_idol():
    """A straw figure crowned with wheat on a wooden post. Straw: strands standing up; body: the same tied with hemp
    twine at the neck (row 3) and waist (rows 9-10); chest: a gold medallion hung from the neck tie; face: two button
    eyes and a stitched smile; crown: wheat stalks woven on the slant; ear: a wheat ear's kernels in pairs up the stalk;
    post: an oak pole with its grain along it."""
    straw = straw_strands(501)

    def tied(seed):
        s = straw_strands(seed)
        for x in range(16):
            s.put(x, 3, TWINE[1] if x % 3 else TWINE[0])
            s.put(x, 9, TWINE[2] if x % 3 else TWINE[1])
            s.put(x, 10, TWINE[1] if x % 3 != 1 else TWINE[0])
        return s

    body = tied(502)
    chest = tied(503)
    chest.paste(grid("""
        t..t
        .tt.
        .00.
        0330
        0321
        .00.
    """, {"t": TWINE[0]}, ramp=GOLD), 6, 4)

    face = straw_strands(504)
    face.paste(grid("""
        .k..k.
        ......
        .t..t.
        ..tt..
    """, {"k": STITCH, "t": TWINE[0]}), 5, 5)

    crown = Sprite(16, 16)
    for y in range(16):
        for x in range(16):
            band = ((x + y) // 2) % 3                      # stalks laid on the slant, three shades round
            crown.put(x, y, (WHEAT[4], WHEAT[3], WHEAT[2])[band])
            if (x + y) % 6 == 5:
                crown.put(x, y, WHEAT[1])                  # the shadow where one stalk passes under the next

    ear = Sprite(16, 16)
    for x in range(16):
        for y in range(16):
            ear.put(x, y, WHEAT[3] if (y + x) % 2 == 0 else WHEAT[2])
        ear.put(x, 0, WHEAT[4])                            # the pale awns at the tip
    for x in range(0, 16, 2):
        ear.put(x, 3, WHEAT[1])                            # the stalk showing between kernels

    oak = P("oak_log")
    post = Sprite(16, 16)
    base = [3, 3, 2, 3, 4, 3, 3, 2, 3, 3, 4, 3, 2, 3, 3, 4]
    for x in range(16):
        for y in range(16):
            post.put(x, y, oak[base[x]])
    rnd = random.Random(505)
    for _ in range(12):                                    # streaks along the grain
        x, y = rnd.randrange(16), rnd.randrange(16)
        c = oak[rnd.choice((1, 2, 4))]
        for k in range(rnd.randint(3, 6)):
            post.put(x, (y + k) % 16, c)
    for kx, ky in ((8, 7), (7, 11)):                       # knots
        post.put(kx, ky, oak[0])
        post.put(kx, ky - 1, oak[1])
        post.put(kx, ky + 1, oak[1])
    return [straw.save(block("harvest_idol_straw")), body.save(block("harvest_idol_body")),
            chest.save(block("harvest_idol_chest")), face.save(block("harvest_idol_face")),
            crown.save(block("harvest_idol_crown")), ear.save(block("harvest_idol_ear")),
            post.save(block("harvest_idol_post"))]


DRAW = [cradle, harvest_idol, sieve, storehouse, teachers_desk, tinkers_bench, trade_board, training_dummy, training_post, travel_post,
        tutors_desk, undertakers_table, village_hall]

if __name__ == "__main__":
    run(DRAW)
