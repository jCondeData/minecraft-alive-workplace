"""Workstations, part 2: the Guard Post, Inn Counter, Kitchen Stove, Leader's Podium, Mailbox, Miner's Bench, Music
Stand, Nether Brazier, Nurse Station, Postal Desk, Scholar's Desk and Shop Counter, redrawn in the style of
builders.py (approved by the owner, 2026-09-29). Each keeps the design of its old texture (tools/textures/generate.py):
built from its material's vanilla tile (the wood matches the block's bottom face), a 1 px frame in the darkest shade,
the unique feature on the front and top."""
from artlib import *  # noqa: F401,F403

# --- materials (darkest first) -------------------------------------------------------------------
OAK = P("oak_planks")                                                     # [0] frame, [1:6] the planks tile
# frame: spruce bark (the planks' own darkest shade is too close to the tile to read as a frame)
SPRUCE = Ramp(["#3b2713"] + list(P("spruce_planks"))[1:], name="spruce")
STONE_BRICKS = P("stone_bricks")
SMOOTH = P("smooth_stone")
BLACKSTONE = P("blackstone")
ANDESITE = Ramp(["#5a5d5a", "#6a6d73", "#737773", "#7c7f80", "#868887", "#8a9090", "#a4a59c", "#b4b2ac"],
                name="polished_andesite")                                 # measured from vanilla polished_andesite
CAST = Ramp(["#242528", "#2e3033", "#383a3e", "#414448", "#4a4d52", "#55585d", "#686c72"], name="cast_iron")
STEEL = Ramp(["#5a5a5a", "#8c8c8c", "#b4b4b4", "#d2d2d2"], name="steel")  # as builders.py
GOLD = Ramp(["#b26411", "#dc9613", "#e9b115", "#fad64a", "#fdf55f"], name="gold")  # vanilla gold ingot + outline
PAPER = Ramp(["#a89c7e", "#c9bd9c", "#e2d8bb", "#efe8d2"], name="paper")
TEXT = "#7a7060"                                                           # writing on paper
INK = "#23222c"                                                            # ink, notes, the ink pot
RED = Ramp(["#6e1818", "#902120", "#a42822", "#b8342c", "#cc4a3c"], name="red")  # wool_red, one lighter step
LEATHER = P("leather")
IRON_TOOL = P("iron_tool")
STICK = P("stick")


def bench_face(seed, wood=OAK):
    """A workstation face: the planks tile with a 1 px frame in the darkest shade, like the crafting table."""
    s = planks_tile(wood[1:6], boards=4, seed=seed)
    frame(s, wood[0])
    return s


def stone_face(tile, dark):
    """A stone workstation face: its stone tile with a 1 px frame in the darkest shade, like the furnace."""
    return frame(tile, dark)


def pickaxe(head=IRON_TOOL):
    """A pickaxe (8x8), handle at the bottom-left like an item: a curved head, the stick's two browns."""
    return grid("""
        ..llll..
        .....lf.
        .....wfs
        ....v..s
        ...w...s
        ..v....s
        .w......
        v.......
    """, {"l": head[3], "f": head[2], "s": head[1], "w": STICK[1], "v": STICK[0]})


def open_book(binding):
    """An open book seen from above (9x7): two pages of writing, the gutter, the cover's edge below."""
    return grid("""
        .pppsppp.
        pttqsqttp
        pppqsqppp
        pttqsqtpp
        pppqsqppp
        ptpqsqttp
        ccccccccc
    """, {"p": PAPER[3], "q": PAPER[2], "s": PAPER[0], "t": TEXT, "c": binding})


# --- Guard Post -----------------------------------------------------------------------------------
SHIELD = Ramp(["#1d335e", "#2b4c8c", "#365ba0", "#4a70b4"], name="shield")


def guard_post():
    """Spruce (its bottom is spruce planks). Top: two iron straps across the lid; front: a sword standing point-up
    beside a round blue shield with an iron rim and a gold boss; side: iron studs."""
    top = bench_face(21, SPRUCE)
    for y in (4, 10):
        top.fill(rect(1, y, 14, y), STEEL[2])
        top.fill(rect(1, y + 1, 14, y + 1), STEEL[0])
        for x in (2, 13):
            top.put(x, y, STEEL[3])
            top.put(x, y + 1, STEEL[1])

    front = bench_face(22, SPRUCE)
    front.paste(grid("""
        ............
        .l..........
        .lm.........
        .lm....rrr..
        .lm...rhbbR.
        .lm..rhbbbbR
        .lm..rbbybbR
        .lm..rbbobBR
        .lm...RbbBR.
        kddk...RRR..
        .ww.........
        .ww.........
        .gg.........
    """, {"l": STEEL[3], "m": STEEL[1], "d": STEEL[1], "k": STEEL[0], "w": LEATHER.outline_light, "g": GOLD[2],
          "r": STEEL[2], "R": STEEL[0], "b": SHIELD[2], "B": SHIELD[1], "h": SHIELD[3], "y": GOLD[3],
          "o": GOLD[1]}), 2, 1)

    side = bench_face(23, SPRUCE)
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12), (7, 7)):
        side.paste(grid("""
            lm
            dk
        """, {"l": STEEL[3], "m": STEEL[2], "d": STEEL[1], "k": STEEL[0]}), x, y)
    return [top.save(block("guard_post_top")), front.save(block("guard_post_front")),
            side.save(block("guard_post_side"))]


# --- Inn Counter ----------------------------------------------------------------------------------
def inn_counter():
    """Spruce. Top: the guest book open, a quill beside it and a gold service bell; front: a moulded top over two
    raised panels, a brass plaque between them; side: the panels without the plaque."""
    top = bench_face(31, SPRUCE)
    top.paste(open_book(LEATHER[0]), 1, 5)                               # the guest book, the bell, the quill
    top.paste(grid("""
        ..k..
        .lyY.
        .yyY.
        lyyYo
        ooooo
    """, {"k": STEEL[0], "l": GOLD[4], "y": GOLD[3], "Y": GOLD[2], "o": GOLD[0]}), 10, 2)
    top.paste(grid("""
        ..fF
        .fF.
        .F..
        n...
    """, {"f": PAPER[3], "F": PAPER[1], "n": INK}), 11, 9)

    def panels(face):
        face.fill(rect(1, 1, 14, 1), SPRUCE[6])                          # the moulding under the counter top
        face.fill(rect(1, 2, 14, 2), P("spruce_planks")[0])
        for x0 in (2, 9):
            face.paste(grid("""
                ggggg
                gllhg
                glfsg
                glfsg
                glfsg
                glfsg
                glfsg
                glfsg
                ghssg
            """, {"g": "#4d3317", "l": "#96703f", "h": SPRUCE[5], "f": SPRUCE[6], "s": SPRUCE[1]}), x0, 5)
        face.fill(rect(2, 14, 13, 14), SPRUCE[2])

    front = bench_face(32, SPRUCE)
    panels(front)
    front.paste(grid("""
        oyyo
        oYYo
    """, {"o": GOLD[0], "y": GOLD[3], "Y": GOLD[2]}), 6, 3)
    side = bench_face(33, SPRUCE)
    panels(side)
    return [top.save(block("inn_counter_top")), front.save(block("inn_counter_front")),
            side.save(block("inn_counter_side"))]


# --- Kitchen Stove --------------------------------------------------------------------------------
FIRE = Ramp(["#8e2a0c", "#b13f00", "#c96c03", "#dfa21b", "#efcd56"], name="fire")   # campfire flame, one deeper red
COPPER = P("copper")
STEW = Ramp(["#5e3818", "#7d4c22", "#9a6431", "#c9914e"], name="stew")


def kitchen_stove():
    """A cast-iron range. Top: a copper pot of stew on one burner, a red-hot coil on the other; front: a rail, the
    oven door with a window onto the fire and a brass handle; side: the rail and three vents."""
    def iron(seed):
        return stone_face(stone_tile(CAST[2:6], seed=seed), CAST[0])

    def rail(face):
        face.fill(rect(2, 2, 13, 2), STEEL[2])
        face.put(4, 2, STEEL[3])
        face.fill(rect(1, 3, 14, 3), CAST[0])
        face.put(1, 2, STEEL[0])
        face.put(14, 2, STEEL[0])

    top = iron(41)
    top.paste(grid("""
        ...LLc...
        .LLssscc.
        .LsaaaaC.
        LsaabaaaC
        csaaaabaC
        csabaaaaC
        .caaaaaC.
        .cCaaaCD.
        ...CDD...
    """, {"L": COPPER[3], "c": COPPER[2], "C": COPPER[0], "D": COPPER.outline_dark, "s": STEW[1], "a": STEW[2],
          "b": STEW[3]}), 1, 1)
    top.paste(grid("""
        .hhhh.
        h.gg.h
        hg..gh
        hg..gh
        h.gg.h
        .hhhh.
    """, {"h": FIRE[1], "g": FIRE[3]}), 9, 9)

    front = iron(42)
    rail(front)
    front.paste(grid("""
        ..hjjjjh..
        0000000000
        0655555550
        06kkkkkk30
        06krkkrk30
        06rfrrfr30
        06fyffyf30
        0433333320
        0000000000
    """, {"h": GOLD[0], "j": GOLD[3], "k": CAST[0], "r": FIRE[1], "f": FIRE[2], "y": FIRE[3]}, ramp=CAST), 3, 4)

    side = iron(43)
    rail(side)
    side.puts([(2, 6), (13, 6), (2, 12), (13, 12)], STEEL[1])          # bolts
    for y in (6, 9, 12):
        side.fill(rect(4, y, 11, y), CAST[0])
        side.fill(rect(4, y + 1, 11, y + 1), CAST[6])
    return [top.save(block("kitchen_stove_top")), front.save(block("kitchen_stove_front")),
            side.save(block("kitchen_stove_side"))]


# --- Leader's Podium ------------------------------------------------------------------------------
def leaders_podium():
    """Polished andesite (its bottom) with a gold trim inside the frame, gold studs at its corners; a gold star
    inlaid on the front, a red one on the top."""
    def face(seed):
        s = stone_face(stone_tile(ANDESITE[1:8], seed=seed), ANDESITE[0])
        s.fill(rect(1, 1, 14, 1) | rect(1, 1, 1, 14), GOLD[2])
        s.fill(rect(2, 14, 14, 14) | rect(14, 2, 14, 14), GOLD[1])
        s.puts([(1, 1), (14, 1), (1, 14), (14, 14)], GOLD[3])
        return s

    def star(ramp):
        return grid("""
            ...lm...
            ...lm...
            ..lmmm..
            lllmmmmd
            .lmmmmd.
            ..lmmd..
            .lm..md.
            .l....d.
        """, {"l": ramp[3], "m": ramp[2], "d": ramp[1]})

    top = face(51)
    top.paste(star(RED), 4, 4)
    front = face(52)
    front.paste(star(GOLD), 4, 4)
    side = face(53)
    return [top.save(block("leaders_podium_top")), front.save(block("leaders_podium_front")),
            side.save(block("leaders_podium_side"))]


# --- Mailbox --------------------------------------------------------------------------------------
MAIL = Ramp(["#1f3561", "#2a4677", "#355890", "#3f67a5", "#5a82c4"], name="mail")


def mailbox():
    """Not a cube: the model shows parts of each texture on a post, a box and a flag (models/block/mailbox.json).
    Box: front = front[x 3-12, y 1-7], back = side[x 3-12, y 1-7], sides = side[x 2-13, y 1-7],
    top = top[x 3-12, y 2-13], bottom = side[x 3-12, y 2-13]. Post: post[x 6.5-9.5, y 0-8].
    Flag: default UVs of a 1x2x7 (down) or 1x7x2 (up) bar, rows 0-6 around x 2-13.
    Blue painted metal with rivets and a slot, a wooden post, a red flag."""
    side = Sprite(16, 16, MAIL[3])
    side.fill(rect(0, 8, 15, 15), MAIL[2])                               # the underside (bottom face)
    side.fill(rect(0, 1, 15, 1), MAIL[4])                                # lit top edge
    side.fill(rect(0, 7, 15, 7), MAIL[1])                                # bottom edge
    for x in (2, 13):
        side.fill(rect(x, 2, x, 6), MAIL[1])
    for x in (3, 12):
        side.fill(rect(x, 2, x, 6), MAIL[2])
    side.puts([(4, 2), (11, 2), (4, 6), (11, 6)], MAIL[4])               # rivets in the corners

    front = side.copy()
    front.fill(rect(4, 2, 11, 6), MAIL[3])                               # no rivets: the slot and latch instead
    front.fill(rect(5, 3, 10, 3), "#131d33")
    front.fill(rect(5, 4, 10, 4), MAIL[4])
    front.put(7, 5, GOLD[3])
    front.put(8, 5, GOLD[1])

    top = Sprite(16, 16, MAIL[3])
    top.fill(rect(3, 2, 12, 2) | rect(3, 2, 3, 13), MAIL[2])
    top.fill(rect(3, 13, 12, 13) | rect(12, 2, 12, 13), MAIL[1])
    for x in (5, 9):
        top.fill(rect(x, 3, x, 12), MAIL[4])
        top.fill(rect(x + 1, 3, x + 1, 12), MAIL[2])

    post = Sprite(16, 16)
    cols = {2: OAK[0], 3: OAK[5], 0: OAK[3], 1: OAK[1]}
    for x in range(16):
        post.fill(rect(x, 0, x, 15), cols[x % 4])
    for x, y0, y1, c in ((7, 2, 3, OAK[4]), (8, 5, 6, OAK[2]), (3, 9, 11, OAK[4]), (12, 12, 14, OAK[2])):
        post.fill(rect(x, y0, x, y1), c)

    flag = Sprite(16, 16, RED[3])
    flag.fill(rect(0, 0, 15, 0), RED[4])
    flag.fill(rect(0, 6, 15, 6), RED[1])
    return [side.save(block("mailbox_side")), front.save(block("mailbox_front")), top.save(block("mailbox_top")),
            post.save(block("mailbox_post")), flag.save(block("mailbox_flag"))]


# --- Miner's Bench --------------------------------------------------------------------------------
LANTERN = {"k": "#495065", "K": "#6b7288", "y": "#fdfd8b", "Y": "#f9c966", "o": "#f09149"}  # vanilla lantern
NICHE = ["#3a393c", "#474649"]


def miners_bench():
    """Stone bricks under an oak worktop. Top: a smooth stone slab set in the bricks with a pickaxe on it; front: a
    pickaxe and a lantern hanging in a niche under a peg rail; side: the worktop edge over the bricks."""
    def face(seed):
        s = stone_bricks_tile(STONE_BRICKS, seed=seed)
        board = planks_tile(OAK[1:6], boards=4, seed=seed)
        s.paste(board.region(0, 0, 16, 4), 0, 0)
        frame(s, STONE_BRICKS[0])
        s.fill(rect(0, 0, 15, 0) | rect(0, 0, 0, 3) | rect(15, 0, 15, 3), OAK[0])
        return s

    top = frame(stone_bricks_tile(STONE_BRICKS, seed=61), STONE_BRICKS[0])
    top.fill(rect(3, 3, 12, 12), SMOOTH[5])
    top.fill(rect(3, 3, 12, 3) | rect(3, 3, 3, 12), SMOOTH[1])
    top.fill(rect(4, 12, 12, 12) | rect(12, 4, 12, 12), SMOOTH[0])
    top.paste(pickaxe(Ramp(["#2e2e2e", "#444444", "#5a5a5a", "#7a7a7a"])), 4, 4)

    front = face(62)
    front.fill(rect(2, 4, 13, 11), NICHE[1])                             # the niche and its sill
    front.fill(rect(2, 5, 13, 5), NICHE[0])
    front.fill(rect(2, 12, 13, 12), SMOOTH[6])
    front.fill(rect(2, 4, 13, 4), OAK[3])                                # peg rail
    front.puts([(4, 4), (12, 4)], OAK[1])
    front.paste(pickaxe(), 3, 5)
    front.paste(grid("""
        .k.
        kKk
        kyk
        kYk
        kok
        kkk
    """, LANTERN), 11, 5)

    side = face(63)
    return [top.save(block("miners_bench_top")), front.save(block("miners_bench_front")),
            side.save(block("miners_bench_side"))]


# --- Music Stand ----------------------------------------------------------------------------------
INKS = Ramp(["#1b1a22", "#23222c", "#34333f", "#4a4d5e"], name="ink")


def music_stand():
    """Oak. Top: a sheet of music (a staff and three notes); side: a big eighth note; front: the note over the red
    ledge the music rests on."""
    top = bench_face(71)
    top.paste(grid("""
        pppppppppp
        llllllllll
        pppppIpppp
        lllllIllIl
        ppIppIppIp
        llIlHNllIl
        ppIppppHNp
        lHNlllllll
        pppppppppp
        llllllllll
        qqqqqqqqqq
    """, {"p": PAPER[3], "q": PAPER[1], "l": PAPER[0], "I": INKS[1], "N": INKS[1], "H": INKS[3]}), 3, 2)

    def note(face):
        face.paste(grid("""
            ....sf.
            ....sgf
            ....s.f
            ....s.f
            ....s..
            ....s..
            ..hns..
            .hnns..
            .nd....
        """, {"s": INKS[1], "f": INKS[1], "g": INKS[2], "h": INKS[3], "n": INKS[1], "d": INKS[0]}), 4, 3)

    side = bench_face(72)
    note(side)
    front = bench_face(73)
    note(front)
    front.fill(rect(2, 13, 13, 13), RED[3])
    front.fill(rect(2, 14, 13, 14), RED[1])
    return [top.save(block("music_stand_top")), front.save(block("music_stand_front")),
            side.save(block("music_stand_side"))]


# --- Nether Brazier -------------------------------------------------------------------------------
PORTAL = Ramp(["#3e00ae", "#5405c1", "#6e13cf", "#8a3cf0", "#b56cf5"], name="portal")
OBSIDIAN = Ramp(["#100c1c", "#271e3d", "#3b2754"], name="obsidian")
GILDED = Ramp(["#451b03", "#7e450e", "#da910f", "#fcee4b"], name="gilded")     # the gold of gilded blackstone
SOUL = "#46d3d6"


def nether_brazier():
    """Polished blackstone bricks with gilded bands. Top: a stone bowl of glowing embers with soul-fire sparks inside
    a gold trim; front: a purple portal swirl in an obsidian frame set in the stone; side: the bands."""
    def face(seed):
        s = frame(bricks_tile(BLACKSTONE[1:], BLACKSTONE[1], seed=seed), BLACKSTONE[0])
        return s

    def bands(s, flecks):
        for y in (1, 13):
            s.fill(rect(1, y, 14, y), GILDED[2])
            s.fill(rect(1, y + 1, 14, y + 1), GILDED[1])
            s.puts([(2, y), (13, y)], GILDED[3])
        for x, y in flecks:                                              # gold in the stone, as in gilded blackstone
            s.put(x, y, GILDED[2])
            s.put(x + 1, y, GILDED[1])
            s.put(x, y + 1, GILDED[0])

    top = face(81)
    top.fill(rect(1, 1, 14, 1) | rect(1, 1, 1, 14), GOLD[2])
    top.fill(rect(2, 14, 14, 14) | rect(14, 2, 14, 14), GOLD[1])
    top.puts([(1, 1), (14, 1), (1, 14), (14, 14)], GOLD[3])
    top.paste(grid("""
        ...KKKK...
        .KKrooCkk.
        .KrCcooCk.
        KrcoyyoCrk
        KroyYycCrk
        KrCyYYyork
        KcCoyoCcrk
        .KroCcork.
        .kkrccrkk.
        ...kkkk...
    """, {"K": BLACKSTONE[5], "k": BLACKSTONE[1], "c": "#2a1410", "C": "#4a2014", "r": FIRE[0], "o": FIRE[2],
          "y": FIRE[3], "Y": FIRE[4]}), 3, 3)
    top.puts([(8, 5), (5, 8)], SOUL)

    front = face(82)
    side = face(83)
    bands(front, [(2, 9), (12, 5)])
    bands(side, [(3, 5), (10, 9)])
    front.paste(grid("""
        oooooo
        o2342O
        o1234O
        o4123O
        o3412O
        o2341O
        o1234O
        OOOOOO
    """, {"o": OBSIDIAN[1], "O": OBSIDIAN[0]}, ramp=PORTAL), 5, 4)
    return [top.save(block("nether_brazier_top")), front.save(block("nether_brazier_front")),
            side.save(block("nether_brazier_side"))]


# --- Nurse Station --------------------------------------------------------------------------------
PINK = Ramp(["#b0466f", "#d6658f", "#ec7ea1", "#f4a7c0"], name="pink")    # concrete/wool pink
WHITE_BIRCH = Ramp(["#b8a875", "#c8b77a", "#dbd2a1", "#e3dbaf", "#eee6d5", "#f6f2de"],
                   name="white_birch")    # the pale birch of the birch door/trapdoor; frame and seams in birch planks
TONICS = ["#d6658f", "#5ea9d6", "#d9c24e", "#d6658f"]


def heart(tall=True):
    return grid("""
        .mm..mm.
        mlmmmmmm
        mmmmmmmd
        .mmmmmd.
        ..mmmd..
        ...md...
    """ if tall else """
        .mm..mm.
        mlmmmmmm
        .mmmmmd.
        ..mmmd..
        ...md...
    """, {"l": PINK[3], "m": PINK[2], "d": PINK[1]})


def nurse_station():
    """White birch (the pale birch of the birch door), with a birch frame like its bottom. Top: a pink heart; front: a
    pink heart over a pink stripe and a row of tonic bottles; side: the stripe."""
    def face(seed):
        return bench_face(seed, WHITE_BIRCH)

    def stripe(s):
        s.fill(rect(1, 7, 14, 7), PINK[2])
        s.fill(rect(1, 8, 14, 8), PINK[1])

    top = face(91)
    h = Sprite(10, 8)
    h.paste(heart(), 1, 1)
    h.fill(grow(h.mask()) & ~h.mask(), PINK[0])                       # a darker rim, so it holds on the pale wood
    top.paste(h, 3, 4)

    front = face(92)
    stripe(front)
    front.paste(heart(False), 4, 1)
    for i, c in enumerate(TONICS):
        front.paste(grid("""
            ww
            gG
            lc
            cC
        """, {"w": "#8a6a3e", "g": "#e4f1f2", "G": "#b8d6dc", "l": mix(c, "#ffffff", 0.35), "c": c,
              "C": mix(c, "#000000", 0.25)}), 2 + i * 3, 10)
    side = face(93)
    stripe(side)
    for x, y in ((2, 2), (13, 2), (2, 12), (13, 12)):                   # brass nails
        side.put(x, y, GOLD[3])
        side.put(x, y + 1, GOLD[1])
    return [top.save(block("nurse_station_top")), front.save(block("nurse_station_front")),
            side.save(block("nurse_station_side"))]


# --- Postal Desk ----------------------------------------------------------------------------------
BLOTTER = Ramp(["#2b4d31", "#35603c", "#3f6b45", "#4a7a50"], name="blotter")
CUBBY = ["#2a1b0d", "#3b2714"]                                             # inside a pigeonhole: shadow, back
PARCEL = {"l": "#a88450", "b": "#8a6a3e", "B": "#6b4f2c", "t": "#d8cfa8"}   # brown paper (lit, face, shade), string


def postal_desk():
    """Oak. Top: a green blotter with two envelopes (red stamps), a quill and an ink pot; front: pigeonholes with
    letters and a parcel in some; side: a drawer with a brass handle and a label holder."""
    top = bench_face(101)
    top.paste(grid("""
        bbbbbbbbb
        bpppppsbb
        bpqqqppbb
        bppppppbb
        bbbbbbbbb
        bbbpppppp
        bbbpqqqps
        bbbpppppp
        ddddddddd
    """, {"b": BLOTTER[2], "d": BLOTTER[0], "p": PAPER[3], "q": PAPER[1], "s": RED[3]}), 2, 3)
    top.paste(grid("""
        ..fF
        .fF.
        .F..
        n...
    """, {"f": PAPER[3], "F": PAPER[1], "n": INK}), 11, 2)
    top.paste(grid("""
        .k.
        kIk
        kik
    """, {"k": INK, "I": "#4a4d63", "i": "#2e3145"}), 12, 10)

    front = bench_face(102)
    letter = grid("""
        ppp
        qqq
    """, {"p": PAPER[3], "q": PAPER[1]})
    sealed = grid("""
        pps
        qqq
    """, {"p": PAPER[3], "q": PAPER[1], "s": RED[3]})
    parcel = grid("""
        lll
        btb
        BtB
    """, PARCEL)
    for cy in range(3):
        for cx in range(3):
            x0, y0 = 1 + cx * 5, 1 + cy * 5
            front.fill(rect(x0, y0, x0 + 3, y0 + 3), CUBBY[1])
            front.fill(rect(x0, y0, x0 + 3, y0), CUBBY[0])
            item = {(0, 0): letter, (2, 0): sealed, (1, 1): parcel, (0, 2): sealed, (2, 2): letter}.get((cx, cy))
            if item:
                front.paste(item, x0, y0 + 4 - item.h)

    side = bench_face(103)
    side.paste(grid("""
        0000000000
        0555555540
        054lppl430
        0544hj4430
        0433333320
        0000000000
    """, {"h": GOLD[3], "j": GOLD[1], "l": GOLD[2], "p": PAPER[3]}, ramp=OAK), 3, 5)
    return [top.save(block("postal_desk_top")), front.save(block("postal_desk_front")),
            side.save(block("postal_desk_side"))]


# --- Scholar's Desk -------------------------------------------------------------------------------
BOOKS = [("#7b2d26", "#5c1f1a"), ("#2f4f7a", "#223a5c"), ("#3f6b3a", "#2e5029"), ("#8a6a2a", "#6a501e"),
         ("#5a3a6a", "#44294f")]                                          # (lit spine edge, spine)


def scholars_desk():
    """Oak. Top: an open tome with a red binding and a little globe on a brass stand; front and side: two shelves of
    books and scrolls, like the vanilla bookshelf; front: a brass nameplate on the lower shelf."""
    top = bench_face(111)
    top.paste(open_book(RED[1]), 2, 6)
    top.paste(grid("""
        .bb.
        bgBb
        bBgB
        .BB.
        .yy.
        yyyy
    """, {"b": "#4f86cc", "B": "#2d5fa6", "g": "#4f9a45", "y": GOLD[1]}), 11, 2)

    def shelves(face, order):
        for y0, row in zip((1, 8), order):
            x = 1
            for w, h, kind in row:
                top_y = y0 + 6 - h
                if kind == "scroll":
                    face.fill(rect(x, top_y, x + w - 1, y0 + 5), PAPER[2])
                    face.fill(rect(x, top_y, x + w - 1, top_y), PAPER[3])
                    face.fill(rect(x + w - 1, top_y + 1, x + w - 1, y0 + 5), PAPER[0])
                else:
                    lit, dark = BOOKS[kind]
                    face.fill(rect(x, top_y, x + w - 1, y0 + 5), dark)
                    face.fill(rect(x, top_y, x, y0 + 5), lit)
                x += w
            face.fill(rect(1, y0 + 6, 14, y0 + 6), OAK[1])

    side = bench_face(112)
    shelves(side, [[(2, 6, 0), (2, 4, 1), (2, 3, "scroll"), (1, 5, 2), (2, 6, 3), (2, 5, 4), (1, 4, 0), (2, 6, 2)],
                   [(2, 5, 1), (1, 6, 3), (2, 6, 4), (2, 4, 0), (2, 5, 2), (2, 3, "scroll"), (1, 5, 1), (2, 6, 0)]])
    front = bench_face(113)
    shelves(front, [[(2, 5, 2), (1, 6, 4), (2, 4, 0), (2, 6, 1), (2, 3, "scroll"), (2, 5, 3), (1, 6, 4), (2, 5, 0)],
                    [(2, 6, 3), (2, 3, "scroll"), (1, 5, 2), (2, 6, 1), (2, 5, 4), (1, 4, 0), (2, 6, 2), (2, 5, 1)]])
    front.fill(rect(6, 14, 9, 14), GOLD[2])
    front.puts([(6, 14), (9, 14)], GOLD[0])
    return [top.save(block("scholars_desk_top")), front.save(block("scholars_desk_front")),
            side.save(block("scholars_desk_side"))]


# --- Shop Counter ---------------------------------------------------------------------------------
FELT = Ramp(["#1f4a28", "#27592f", "#2f6b3a", "#387a43"], name="felt")
EMERALD = P("emerald")
SLATE = Ramp(["#232823", "#2f372f", "#3e4a3e", "#4b584b"], name="slate")
CHALK = "#dcd8c4"
CANVAS = Ramp(["#c9c0a0", "#e3dbaf", "#eee6d5"], name="canvas")              # the awning's white stripes


def shop_counter():
    """Spruce (its bottom). Top: a green felt mat with gold coins, and an emerald; front: a red and white striped
    awning over a slate price board; side: the awning running round the counter."""
    top = bench_face(121, SPRUCE)
    top.paste(grid("""
        fffffffff
        fyoffffff
        fooffffff
        ffffffyof
        fffffoooF
        fyoffffff
        fooffffff
        FFFFFFFFF
    """, {"f": FELT[2], "F": FELT[0], "y": GOLD[3], "o": GOLD[1]}), 3, 3)
    top.paste(grid("""
        .lg.
        lgge
        ggee
        .ee.
    """, {"l": EMERALD[4], "g": EMERALD[3], "e": EMERALD[1]}), 11, 11)

    def awning(face):
        for x in range(1, 15):
            red = (x - 1) // 2 % 2 == 0
            face.fill(rect(x, 1, x, 3), RED[2] if red else CANVAS[1])
            face.put(x, 4, RED[1] if red else CANVAS[0])

    front = bench_face(122, SPRUCE)
    awning(front)
    front.paste(grid("""
        0000000000
        0ssssssss0
        0sccscccs0
        0ssssssss0
        0scccsccs0
        0ssssssss0
        0000000000
    """, {"s": SLATE[2], "c": CHALK}, ramp=SLATE), 3, 6)
    side = bench_face(123, SPRUCE)
    awning(side)
    return [top.save(block("shop_counter_top")), front.save(block("shop_counter_front")),
            side.save(block("shop_counter_side"))]


DRAW = [guard_post, inn_counter, kitchen_stove, leaders_podium, mailbox, miners_bench, music_stand, nether_brazier,
        nurse_station, postal_desk, scholars_desk, shop_counter]

if __name__ == "__main__":
    run(DRAW)
