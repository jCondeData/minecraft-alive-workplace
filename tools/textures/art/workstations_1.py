"""Workstations, part 1: the Apiary, Ball Workbench, Blueprint Table, Carpenter's Bench, Chopping Block, Compost Bin,
Drop Box, Feed Trough, Flower Stand, Fossil Lab, Fruit Basket and the Grave's headstone, redrawn in the style of
builders.py (approved by the owner, 2026-09-29). Each keeps what the old texture showed (tools/textures/generate.py)."""
import random

from artlib import *  # noqa: F401,F403
from builders import BLUEPRINT, INK, OAK, STEEL, bench_face

SPRUCE = P("spruce_planks")
DARK_OAK = P("dark_oak_planks")
BAMBOO = P("bamboo_planks")                                           # the Apiary's yellow paint
BIRCH = P("birch_planks")
BARK = P("oak_log")                                                   # 6 bark shades, darkest first
STONE = P("stone")
CALCITE = P("calcite")
MOSS = P("moss")
GLASS = P("glass")
LAPIS = P("lapis")
GOLD = P("gold")
PAPER = P("paper")                                                    # #c1c1c1 #d6d6d6 #e9eaeb #fcfcf2
FLINT = P("flint")                                                    # dark greys, none pure black
HONEY = Ramp(["#c86a08", "#d87803", "#e88c08", "#faab1c", "#ffce5d", "#ffe47f"], name="honeycomb block")
HAY = Ramp(["#8b7110", "#947d10", "#ac8d08", "#bda510", "#cdb208"], name="hay block top")
RED = Ramp(["#9c1017", "#b4131e", "#dd1725", "#ff5e69"], name="apple")
LEAF = Ramp(["#177c04", "#4a8f28", "#55ab2d"], name="flower leaves")
GREEN_PAINT = Ramp(["#3f7935", "#578c4d", "#5b9c51"], name="cornflower stems")
POT = Ramp(["#67392d", "#794334", "#894c3b"], name="flower pot")
BRASS = Ramp(["#8a6a26", "#b08a3a", "#c8a24a", "#e0c070"], name="brass")   # the Builder's buckle is #c8a24a
COMPOST = Ramp(["#3e250e", "#4a3018", "#523c18", "#6a4418", "#8b5920"], name="composter compost")
CHARCOAL = FLINT[1]
HANDLE = ["#553a1f", "#7a5a34"]                                       # the Builder's hammer handle (spruce)
STICK = {"a": "#493615", "B": "#896727", "C": "#684e1e", "e": "#281e0b"}  # the vanilla stick: lit, wood, wood, shadow
BLOOMS = {
    "red": Ramp(["#9b221a", "#bf2529", "#ed302c"]),                   # poppy
    "yellow": Ramp(["#f19d25", "#fed639", "#ffec4f"]),                # dandelion
    "blue": Ramp(["#2a4cc7", "#466aeb", "#728ff1"]),                  # cornflower
    "pink": Ramp(["#da6a92", "#ec7ea1", "#f4a7c0"]),                  # pink wool
}


def tiled(motif, legend=None, ramp=None):
    """A small ASCII motif repeated over the whole 16x16 tile."""
    m = grid(motif, legend, ramp)
    s = Sprite(16, 16)
    for y in range(16):
        for x in range(16):
            s.a[y, x] = m.a[y % m.h, x % m.w]
    return s


def legs(s, wood, x0=1, x1=14, y0=2, y1=14):
    """Two legs (a lit column and a shaded one each), as on the crafting table's sides."""
    for x, c in ((x0, wood[5]), (x0 + 1, wood[1]), (x1 - 1, wood[4]), (x1, wood[1])):
        s.fill(rect(x, y0, x, y1), c)


# --- Apiary -----------------------------------------------------------------------------------------------------
def hive_face(seed):
    """Boards painted pale yellow (the bamboo planks' lighter shades) in an oak frame."""
    s = planks_tile(BAMBOO[2:7], seed=seed)
    frame(s, OAK[0])
    return s


def apiary():
    """A painted hive box. Top: a frame of honeycomb with two cells capped with wax; front: the entrance slit with its
    landing board and a bee flying in; sides: the painted boards with a hand-hold."""
    top = hive_face(301)
    cell = {"l": HONEY[4], "m": HONEY[3], "s": HONEY[2]}
    wax = {"l": "#fff9cf", "m": HONEY[5], "s": HONEY[4]}
    top.fill(rect(2, 2, 13, 13), HONEY[0])
    for r in range(-1, 5):
        for c in range(-1, 5):
            x, y = 2 + c * 4 + (2 if r % 2 else 0), 2 + r * 4
            stamp = grid("""
                .lm.
                lmms
                .ms.
            """, wax if (c, r) in ((1, 1), (2, 2)) else cell)
            for dy in range(3):
                for dx in range(4):
                    if 2 <= x + dx <= 13 and 2 <= y + dy <= 13 and stamp.a[dy, dx, 3]:
                        top.a[y + dy, x + dx] = stamp.a[dy, dx]
    top.fill(rect(1, 1, 14, 1) | rect(1, 1, 1, 14), OAK[4])            # the comb's wooden frame
    top.fill(rect(1, 14, 14, 14) | rect(14, 2, 14, 14), OAK[3])
    top.puts([(1, 1), (14, 1), (1, 14), (14, 14)], STEEL[1])            # nailed at the corners

    side = hive_face(302)
    side.paste(grid("""
        .hhhh.
        hkkkkl
        .llll.
    """, {"k": "#43241b", "h": OAK[1], "l": BAMBOO[7]}), 5, 7)         # a hand-hold cut into the box
    side.puts([(2, 2), (13, 2), (2, 13), (13, 13)], STEEL[1])         # nail heads

    front = hive_face(303)
    front.paste(grid("""
        ....wv.
        ....ww.
        ..YbYbk
        ..ybybk
        dd.....
    """, {"w": "#f1f2e0", "v": "#7cc9d1", "Y": "#fed668", "y": "#edc343", "b": "#43241b", "k": "#341911",
          "d": OAK[2]}), 7, 3)
    front.paste(grid("""
        d.
        .d
    """, {"d": OAK[2]}), 4, 8)
    front.paste(grid("""
        .kkkkkk.
        .hhhhhh.
        llllllll
        oooooooo
    """, {"k": "#341911", "h": "#43241b", "l": BAMBOO[7], "o": BAMBOO[0]}), 4, 10)
    return [top.save(block("apiary_top")), side.save(block("apiary_side")), front.save(block("apiary_front"))]


# --- Ball Workbench ------------------------------------------------------------------------------------------------
def poke_ball(top):
    """A 7x7 ball: the coloured top half outlined in its own dark, a charcoal band with a white button, a white bottom
    half outlined in grey; lit from the top-left."""
    return grid("""
        ..ooo..
        .oLTTo.
        oLTTTTo
        kkkbkkk
        gWWWWsg
        .gWssg.
        ..ggg..
    """, {"o": top[0], "L": top[3], "T": top[2], "k": CHARCOAL, "b": PAPER[3], "W": PAPER[2], "s": PAPER[1],
          "g": "#6c6c6c"})                                               # the white half's grey outline


def steel_edge(s):
    """The steel plate's edge under the top (a steel row with rivets, a shadow row below it) and a steel gusset where
    each leg meets it."""
    s.fill(rect(1, 1, 14, 1), STEEL[1])
    s.puts([(2, 1), (13, 1)], STEEL[3])
    s.fill(rect(1, 2, 14, 2), OAK[1])
    s.puts([(3, 2), (4, 2), (11, 2), (12, 2)], STEEL[2])
    s.puts([(3, 3), (12, 3)], STEEL[0])


def ball_workbench():
    """An oak bench with a steel plate on top. Top: a red ball, a blue one and a gold ingot on the plate; sides: the
    plate's edge and the legs; front: a red and white ball painted on."""
    top = bench_face(311)
    top.fill(rect(1, 1, 14, 14), STEEL[1])
    top.fill(rect(1, 1, 14, 1) | rect(1, 1, 1, 14), STEEL[2])
    top.fill(rect(1, 14, 14, 14) | rect(14, 1, 14, 14), STEEL[0])
    top.paste(poke_ball(RED), 2, 2)
    top.paste(poke_ball(LAPIS), 7, 7)
    top.paste(grid("""
        .hhhh
        hgggd
        ooooo
    """, {"h": GOLD[3], "g": GOLD[2], "d": GOLD[0], "o": "#b26411"}), 9, 2)

    side = bench_face(312)
    legs(side, OAK, y0=3)
    steel_edge(side)

    front = bench_face(313)
    legs(front, OAK, y0=3)
    steel_edge(front)
    front.paste(poke_ball(RED), 5, 6)
    return [top.save(block("ball_workbench_top")), side.save(block("ball_workbench_side")),
            front.save(block("ball_workbench_front"))]


# --- Blueprint Table -----------------------------------------------------------------------------------------------
def blueprint_table():
    """Dark oak. Top: a big blueprint pinned at its corners, a floor plan drawn on its grid, a T-square along the back;
    front: a drawer under the top and a rack of rolled plans below it; sides: the sheet's edge over the top, the legs."""
    top = bench_face(321, DARK_OAK)
    top.paste(grid("""
        r1111111111r
        122232223221
        12wwwwwwwww1
        12w222w222w1
        12w222w222w1
        12w322ww2ww1
        12w2222222w1
        12w2222222w1
        12www2wwwww1
        122232223221
        r0000000000r
    """, {"r": "#c4412f", "w": INK}, ramp=BLUEPRINT), 2, 2)
    top.paste(grid("""
        h...........
        hkkkkkkkkkkk
    """, {"k": BIRCH[5], "h": BIRCH[3]}), 2, 13)

    front = bench_face(322, DARK_OAK)
    front.fill(rect(2, 1, 13, 1), BLUEPRINT[2])
    front.paste(grid("""
        0000000000
        0544hj4430
        0433333320
        0000000000
    """, {"h": STEEL[3], "j": STEEL[1]}, ramp=DARK_OAK), 3, 2)
    front.fill(rect(2, 6, 13, 9), DARK_OAK[0])                            # the rack, rolled plans standing in it
    for x in (3, 5, 9, 11):
        front.fill(rect(x, 6, x, 9), BLUEPRINT[2])
        front.fill(rect(x + 1, 6, x + 1, 9), BLUEPRINT[1])
        front.put(x, 6, INK)
        front.put(x + 1, 6, BLUEPRINT[3])
    front.paste(grid("""
        555555555555
        044444444430
        043333333320
        000000000000
    """, ramp=DARK_OAK), 2, 10)                                             # the rack's front board

    side = bench_face(323, DARK_OAK)
    side.fill(rect(2, 1, 13, 1), BLUEPRINT[2])
    legs(side, DARK_OAK, y0=3)
    side.fill(rect(3, 11, 12, 11), DARK_OAK[5])                            # a stretcher between the legs
    side.fill(rect(3, 12, 12, 12), DARK_OAK[0])
    side.paste(grid("""
        i3333333
        22222221
    """, {"i": INK}, ramp=BLUEPRINT), 4, 9)                                  # a rolled plan lying on it
    return [top.save(block("blueprint_table_top")), front.save(block("blueprint_table_front")),
            side.save(block("blueprint_table_side"))]


# --- Carpenter's Bench ---------------------------------------------------------------------------------------------
def carpenters_bench():
    """Oak. Top: a hand saw lying across the worktop, curls of shavings, a carpenter's square in the corner;
    front: a vise under the top and a hammer hanging below it; sides: the legs and a stretcher."""
    top = bench_face(331)
    top.paste(grid("""
        ccccc
        jjjjc
        ...jc
        ...jc
        ...jc
    """, {"c": STEEL[3], "j": STEEL[1]}), 9, 2)
    top.paste(grid("""
        .........hhh.
        llllllllhHoh.
        .mmmmmmmhhhhh
        ..tmtmtmt.hh.
    """, {"l": STEEL[3], "m": STEEL[2], "t": STEEL[0], "h": HANDLE[1], "H": "#9a7442", "o": HANDLE[0]}), 1, 9)
    shavings = {"s": BIRCH[6], "S": BIRCH[3]}
    for x, y in ((3, 3), (11, 13)):
        top.paste(grid("""
            ss.
            .sS
        """, shavings), x, y)
    top.paste(grid("""
        .s
        sS
    """, shavings), 5, 6)

    front = bench_face(332)
    legs(front, OAK)
    front.paste(grid("""
        oooooooo
        llllllll
        mmmmmmmm
        dddddddd
        ...ss...
        .hhhhhhg
    """, {"o": OAK[0], "l": STEEL[3], "m": STEEL[2], "d": STEEL[0], "s": STEEL[1], "h": HANDLE[1], "g": HANDLE[0]}),
        4, 1)
    front.paste(grid("""
        lhhhh
        ggggg
        ..w..
        ..v..
        ..w..
    """, {"l": STEEL[3], "h": STEEL[2], "g": STEEL[0], "w": HANDLE[1], "v": HANDLE[0]}), 6, 9)

    side = bench_face(333)
    legs(side, OAK)
    side.fill(rect(3, 11, 12, 11), OAK[6])                                 # a stretcher between the legs
    side.fill(rect(3, 12, 12, 12), OAK[0])
    side.puts([(2, 11), (13, 11)], STEEL[3])                               # bolts through the legs
    side.puts([(2, 12), (13, 12)], STEEL[0])
    return [top.save(block("carpenters_bench_top")), front.save(block("carpenters_bench_front")),
            side.save(block("carpenters_bench_side"))]


# --- Chopping Block ------------------------------------------------------------------------------------------------
def bark_tile(seed):
    """Oak bark the vanilla way: vertical streaks 2-6 px long, mostly two mid shades, dark cracks and a few light
    ridges; wraps at the edges."""
    rnd = random.Random(seed)
    s = Sprite(16, 16, BARK[3])
    for x in range(16):
        y, filled = rnd.randrange(16), 0
        while filled < 16:
            n = rnd.randint(2, 6)
            c = rnd.choices([BARK[3], BARK[4], BARK[2], BARK[5]], [5, 4, 2, 1])[0]
            for k in range(n):
                s.put(x, (y + k) % 16, c)
            y, filled = y + n, filled + n
    for _ in range(7):
        x, y = rnd.randrange(16), rnd.randrange(16)
        for k in range(rnd.randint(3, 6)):
            s.put(x, (y + k) % 16, BARK[1] if 0 < k else BARK[0])
    return s


AXE = """
    .LL......
    LllL.aBe.
    LlfsLaCe.
    LlfsssBe.
    LfssddCe.
    .Ldd.aBe.
    ....aCe..
    ...aBe...
    ...Ce....
"""
AXE_COLOURS = {"L": "#444444", "d": "#2b2b2b", "l": STEEL[3], "f": STEEL[2], "s": STEEL[1], **STICK}


def chopping_block():
    """A stump. Top: the log's rings with a notch the axe left and the axe stuck in it; sides: bark;
    front: bark with a pale chip hacked out of the top edge and the axe."""
    top = Sprite(16, 16)
    rings = [[BARK[1], BARK[2]], [OAK[6], OAK[5]], [OAK[2], OAK[3]], [OAK[5], OAK[4]], [OAK[3], OAK[4]],
             [OAK[5], OAK[5]], [OAK[2], OAK[2]], [OAK[4], OAK[4]]]
    for y in range(16):
        for x in range(16):
            k = int(7.5 - max(abs(x - 7.5), abs(y - 7.5)))
            top.put(x, y, rings[k][((x + 2 * y) // 3) % 2])
    top.fill(rect(2, 6, 13, 6), OAK[0])                                    # the notch
    top.fill(rect(2, 7, 13, 7), OAK[6])                                    # its fresh-cut lip
    top.paste(grid(AXE, AXE_COLOURS), 4, 3)

    side = bark_tile(341)

    front = bark_tile(342)
    front.paste(grid("""
        dllllld
        .dmmmd.
        ..ddd..
    """, {"d": BARK[0], "l": OAK[6], "m": OAK[4]}), 4, 0)
    front.paste(grid(AXE, AXE_COLOURS), 6, 1)
    return [top.save(block("chopping_block_top")), side.save(block("chopping_block_side")),
            front.save(block("chopping_block_front"))]


# --- Compost Bin ---------------------------------------------------------------------------------------------------
def slats(seed):
    """Spruce slats with dark compost showing in the gaps, corner posts nailed on, in a frame."""
    s = bench_face(seed, SPRUCE)
    for y in (3, 7, 11):
        s.fill(rect(1, y, 14, y), COMPOST[0])
    for x, y in ((5, 3), (6, 3), (10, 7), (4, 11), (11, 11), (12, 11)):
        s.put(x, y, COMPOST[3])
    s.puts([(9, 7), (5, 11)], COMPOST[4])
    legs(s, SPRUCE, y0=1)
    for y in (1, 5, 9, 13):
        s.puts([(1, y), (14, y)], STEEL[1])
    return s


def compost_bin():
    """A slatted spruce bin. Top: dark compost inside the rim with greens and eggshell in it; sides: the slats with
    compost in the gaps; front: a hatch at the foot for the finished compost."""
    top = bench_face(351, SPRUCE)
    heap = clusters(COMPOST[1:5], weights=[4, 3, 2, 1], seed=351, size=(2, 3), base=COMPOST[1], density=0.5)
    top.paste(heap.region(2, 2, 12, 12), 2, 2)
    top.fill(rect(2, 2, 13, 2), COMPOST[0])                               # the rim's shadow
    top.fill(rect(1, 1, 14, 1) | rect(1, 1, 1, 14), SPRUCE[6])
    top.fill(rect(1, 14, 14, 14) | rect(14, 2, 14, 14), SPRUCE[1])
    top.paste(grid("""
        .gG.......
        ..g.......
        ..........
        ......eE..
        .......e..
        ..........
        ........gG
        .gG....g..
        gg........
    """, {"g": "#4a6e20", "G": "#6c8031", "e": "#e8e5d2", "E": "#cbc6a5"}), 3, 3)

    side = slats(352)

    front = slats(353)
    front.paste(grid("""
        dddddd
        d4545d
        d45s5d
        d4545d
        dddddd
    """, {"d": SPRUCE[0], "s": STEEL[3]}, ramp=SPRUCE), 5, 9)
    return [top.save(block("compost_bin_top")), side.save(block("compost_bin_side")),
            front.save(block("compost_bin_front"))]


# --- Drop Box ------------------------------------------------------------------------------------------------------
def drop_box():
    """An oak crate. Top: a slot in the lid with a brass rim; sides: a diagonal brace with a paper label pinned over
    it, a down arrow on the label; bottom: two battens nailed across the boards."""
    top = bench_face(361)
    top.paste(grid("""
        hhhhhhhhhh
        hkkkkkkkkm
        hssssssssm
        mmmmmmmmmd
    """, {"h": BRASS[3], "m": BRASS[1], "d": BRASS[0], "k": "#1a150d", "s": "#41230e"}), 3, 6)

    side = bench_face(362)
    for y in range(1, 15):
        for x in range(1, 15):
            c = {14: SPRUCE[6], 15: SPRUCE[4], 16: SPRUCE[2], 17: OAK[0]}.get(x + y)
            if c:
                side.put(x, y, c)
    side.paste(grid("""
        pppkppp
        pqqbqqr
        pqqbqqr
        pbqbqbr
        pqbbbqr
        pqqbqqr
        rrrrrrr
    """, {"p": PAPER[3], "q": PAPER[2], "r": PAPER[0], "b": LAPIS[1], "k": STEEL[0]}), 5, 5)

    bottom = bench_face(363)
    for x in (3, 11):
        bottom.fill(rect(x, 1, x, 14), OAK[6])
        bottom.fill(rect(x + 1, 1, x + 1, 14), OAK[3])
        bottom.fill(rect(x + 2, 1, x + 2, 14), OAK[0])
        bottom.puts([(x + 1, 2), (x + 1, 13)], STEEL[1])
    return [top.save(block("drop_box_top")), side.save(block("drop_box_side")),
            bottom.save(block("drop_box_bottom"))]


# --- Feed Trough ---------------------------------------------------------------------------------------------------
def hay(s, x0, y0, x1, y1, seed):
    """Loose hay: short vertical strands in the hay block's yellows."""
    rnd = random.Random(seed)
    s.fill(rect(x0, y0, x1, y1), HAY[2])
    for x in range(x0, x1 + 1):
        y = y0 + rnd.randrange(3)
        while y <= y1:
            n = rnd.randint(2, 3)
            c = rnd.choice([HAY[0], HAY[1], HAY[3], HAY[4], HAY[3]])
            for k in range(n):
                if y + k <= y1:
                    s.put(x, y + k, c)
            y += n + rnd.randint(1, 2)


CARROT = """
    ..gG
    .oG.
    oO..
    o...
"""
CARROT_COLOURS = {"g": "#036703", "G": "#33be30", "o": "#d36a0d", "O": "#ff8e09"}


def feed_trough():
    """An oak trough full of hay. Top: the hay with carrots in it; sides: hay spilling over the rim and two iron
    bands; front: a horseshoe nailed on."""
    top = bench_face(371)
    hay(top, 2, 2, 13, 13, 371)
    top.fill(rect(2, 2, 13, 2), HAY[0])                                    # the rim's shadow on the hay
    top.paste(grid(CARROT, CARROT_COLOURS), 3, 5)
    top.paste(grid(CARROT, CARROT_COLOURS).flip_x(), 9, 8)

    def side_face(seed):
        s = bench_face(seed)
        s.paste(grid("""
            34434443344434
            4.33..3.4...3.
        """, ramp=HAY), 1, 1)
        for x in (3, 12):
            s.fill(rect(x, 3, x, 14), STEEL[0])
            s.puts([(x, 4), (x, 13)], STEEL[2])
        return s

    side = side_face(372)
    front = side_face(373)
    front.paste(grid("""
        ll..ls
        lk..ks
        lm..ms
        mk..ks
        sm..ms
        .ssss.
    """, {"l": STEEL[3], "m": STEEL[2], "s": STEEL[1], "k": STEEL[0]}), 5, 5)
    return [top.save(block("feed_trough_top")), side.save(block("feed_trough_side")),
            front.save(block("feed_trough_front"))]


# --- Flower Stand --------------------------------------------------------------------------------------------------
def pot(bloom):
    """A flower pot seen from above: a terracotta rim round the soil, leaves, a bloom."""
    return grid("""
        .lll.
        lgBbm
        lBbbm
        mbbgd
        .mdd.
    """, {"l": POT[2], "m": POT[1], "d": POT[0], "g": LEAF[1], "b": bloom[1], "B": bloom[2]})


def flower_band(s):
    """The green painted band along a side, with little painted flowers on it."""
    s.fill(rect(1, 4, 14, 7), GREEN_PAINT[1])
    s.fill(rect(1, 4, 14, 4), GREEN_PAINT[2])
    s.fill(rect(1, 7, 14, 7), GREEN_PAINT[0])
    for x, name in ((1, "pink"), (5, "red"), (9, "yellow"), (13, "blue")):
        s.paste(grid("""
            .B.
            BcB
            .b.
        """, {"B": BLOOMS[name][2], "b": BLOOMS[name][1], "c": BLOOMS["yellow"][0 if name == "yellow" else 2]}),
            x, 4)
    frame(s, OAK[0])


def flower_stand():
    """An oak stand of potted flowers. Top: four pots (red, yellow, blue, pink); sides: a green painted band with
    flowers; front: the band and a bouquet tied with a ribbon."""
    top = bench_face(381)
    for (x, y), name in (((2, 2), "yellow"), ((9, 2), "red"), ((2, 9), "blue"), ((9, 9), "pink")):
        top.paste(pot(BLOOMS[name]), x, y)

    side = bench_face(382)
    flower_band(side)

    front = bench_face(383)
    flower_band(front)
    front.paste(grid("""
        .Yy.Rr.
        .yygrr.
        Bb.g.Pp
        bbggpPp
        ..ggg..
        .kKkKk.
        ..g.g..
    """, {"R": BLOOMS["red"][2], "r": BLOOMS["red"][1], "Y": BLOOMS["yellow"][2], "y": BLOOMS["yellow"][1],
          "B": BLOOMS["blue"][2], "b": BLOOMS["blue"][1], "P": BLOOMS["pink"][2], "p": BLOOMS["pink"][1],
          "g": LEAF[0], "k": "#c7a8e0", "K": "#a882c8"}), 5, 8)
    return [top.save(block("flower_stand_top")), side.save(block("flower_stand_side")),
            front.save(block("flower_stand_front"))]


# --- Fossil Lab ----------------------------------------------------------------------------------------------------
LAB = CALCITE[1:5]                                                    # white bench top, calcite without the glints


def lab_face(seed):
    """The white bench (calcite's clusters) in a frame of calcite's darkest grey."""
    s = stone_tile(LAB, seed=seed)
    frame(s, CALCITE[0])
    return s


def fossil_lab():
    """A white lab bench. Top: a microscope and a lump of amber with a fossil shell in it; sides: the edge of the
    bench top, a kick rail and a cupboard door; front: a glass tank with something growing in it."""
    top = lab_face(391)
    top.paste(grid("""
        .kl.....
        ..kl....
        ..kla...
        ...kAa..
        ..kkkAa.
        ...g..a.
        .mmmmmam
        ......a.
        ...aaaaa
    """, {"k": FLINT[2], "l": FLINT[5], "m": FLINT[4], "g": GLASS[1], "a": FLINT[3], "A": FLINT[4]}), 0, 3)
    top.paste(grid("""
        .oooo.
        oLhhho
        ohsssd
        ohshsd
        ohhssd
        .dddd.
    """, {"o": HONEY[2], "d": HONEY[1], "L": HONEY[5], "h": HONEY[4], "s": COMPOST[4]}), 9, 4)

    def side_face(seed):
        s = lab_face(seed)
        s.fill(rect(1, 3, 14, 3), CALCITE[0])
        s.fill(rect(1, 12, 14, 12), CALCITE[0])
        return s

    side = side_face(392)
    side.paste(grid("""
        dddddddd
        d......l
        d....k.l
        d....j.l
        d......l
        d......l
        llllllll
    """, {"d": CALCITE[0], "l": CALCITE[4], "k": STEEL[2], "j": STEEL[0]}), 4, 4)       # a cupboard door
    front = side_face(393)
    front.paste(grid("""
        kkkkkkkk
        g2333322
        g3w33332
        g3332332
        g3333G32
        g33lGGl2
        g32GeG22
        gggggggg
    """, {"k": "#585858", "g": GLASS[0], "w": GLASS[3], "l": LEAF[2], "G": LEAF[1], "e": LEAF[0]}, ramp=GLASS), 4, 4)
    return [top.save(block("fossil_lab_top")), side.save(block("fossil_lab_side")),
            front.save(block("fossil_lab_front"))]


# --- Fruit Basket --------------------------------------------------------------------------------------------------
WICKER = """
    dllS
    dmms
    lSdl
    msdm
"""
WICKER_COLOURS = {"l": OAK[6], "m": OAK[4], "d": OAK[2], "S": OAK[5], "s": OAK[3]}


def rim(x, y, horizontal=True):
    """One pixel of the basket's braided rim: twists of a lit strand and a shaded one."""
    k = (x if horizontal else y) % 3
    return OAK[6] if k == 0 else OAK[4] if k == 1 else OAK[1]


def basket_face():
    """Wicker: weavers going over and under the stakes; a braided rim along the top, a frame."""
    s = tiled(WICKER, WICKER_COLOURS)
    frame(s, OAK[0])
    for x in range(1, 15):
        s.put(x, 1, rim(x, 1))
        s.put(x, 2, OAK[1])
    return s


def fruit_basket():
    """A wicker basket. Top: fruit heaped inside the braided rim (apples, a yellow and a blue apricorn, an orange,
    berries); sides: the weave with a hand-hold; front: a little wooden tag with a leaf."""
    top = tiled(WICKER, WICKER_COLOURS)
    frame(top, OAK[0])
    top.fill(rect(2, 2, 13, 13), "#4f3218")
    for i in range(1, 15):
        for j in (1, 14):
            top.put(i, j, rim(i, j))
            top.put(j, i, rim(j, i, horizontal=False))
    fruit = {"red": RED, "yellow": BLOOMS["yellow"], "blue": BLOOMS["blue"],
             "green": Ramp(["#2e6924", "#4a8f28", "#7cc31b"]), "orange": Ramp(["#bd6a22", "#f19645", "#f4c05e"])}
    for (x, y), name in (((2, 2), "red"), ((7, 2), "orange"), ((10, 5), "green"), ((4, 6), "blue"),
                         ((8, 9), "red"), ((3, 10), "yellow")):
        r = fruit[name]
        top.paste(grid("""
            .lm.
            lmmd
            mmdd
            .dd.
        """, {"l": r[-1], "m": r[-2], "d": r[0]}), x, y)
    top.puts([(4, 2), (10, 9)], "#684e1e")                                  # apple stems
    top.paste(grid("""
        .b.
        bBb
    """, {"b": "#820b05", "B": "#df467e"}), 11, 11)                         # sweet berries
    top.paste(grid("""
        bB
        .b
    """, {"b": "#820b05", "B": "#df467e"}), 7, 6)

    side = basket_face()
    side.paste(grid("""
        .rrrr.
        rkkkkd
        .dddd.
    """, {"r": OAK[6], "k": "#4f3218", "d": OAK[1]}), 5, 6)                 # a hand-hold in the weave
    front = basket_face()
    front.paste(grid("""
        ..s...
        oooooo
        oppgpo
        opggpo
        opcppo
        oooooo
    """, {"o": BIRCH[0], "p": BIRCH[5], "g": LEAF[1], "c": "#684e1e", "s": "#f1f2e0"}), 5, 5)
    return [top.save(block("fruit_basket_top")), side.save(block("fruit_basket_side")),
            front.save(block("fruit_basket_front"))]


# --- Grave ---------------------------------------------------------------------------------------------------------
def grave():
    """The headstone's face (the model shows u 3-13, v 2-14 of it): stone with a rounded top, an engraved panel with
    two lines of lettering, a carved flower above it and moss creeping up from the ground."""
    s = stone_tile(STONE, seed=401)
    s.paste(grid("""
        dd......dd
        d...dd...d
        ...d..l...
        ....ll....
        .dddddddd.
        .d......l.
        .d.kkkk.l.
        .d......l.
        .d.kkk..l.
        .d......l.
        .llllllll.
        ..........
    """, {"d": "#5e5e5e", "k": "#555555", "l": "#9c9c9c"}), 3, 2)                # carved: shadow above, lit below
    s.paste(grid("""
        m.......M.
        mM.m..mMm.
        MmmMmMmMMm
    """, {"m": MOSS[2], "M": MOSS[4]}), 3, 11)
    return [s.save(block("grave_front"))]


DRAW = [apiary, ball_workbench, blueprint_table, carpenters_bench, chopping_block, compost_bin, drop_box, feed_trough,
        flower_stand, fossil_lab, fruit_basket, grave]

if __name__ == "__main__":
    run(DRAW)
