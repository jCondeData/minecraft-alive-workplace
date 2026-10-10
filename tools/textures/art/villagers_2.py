"""Villager outfits, second set: orchard keeper, berry breeder, camp cook, habitat keeper, daycare keeper, gem grower, Pokemon trader, porter, postman, rancher, scholar, shopkeeper, sifter,
teacher, tinkerer, trainer, trainer leader, tutor and undertaker. Drawn in the style of the Builder's outfit
(builders.py): the villager helpers for the garments, then the details by hand. Each keeps the headwear, colours and
accessory of the outfit it replaces.

Face-local coordinates: on a front face x 0 is the villager's right (the viewer's left); the west face is the
villager's right side (x 5 of the jacket's touches the front), the east face the left side (x 0 touches the front);
the back face's x 0 meets the east side. The level badge covers jacket-front x 4..7, rows 10..13: pockets and tools go
on x 0..3."""
from artlib import *  # noqa: F401,F403

SIDES = ("front", "west", "back", "east")
GOLD = Ramp(["#9c6a16", "#c98f1f", "#e6b53a", "#f4d670"], name="gold")
BRASS = Ramp(["#6e4a1c", "#9a6e2c", "#c4913e", "#dcb466"], name="brass")
STEEL = Ramp(["#5a5a5a", "#8c8c8c", "#b4b4b4", "#d2d2d2"], name="steel")
LEATHER = vg.cloth("#7a4a26")                        # straps and bags
LINEN = "#e6e2d4"                                    # white cloth: pale, never pure white
LINEN_SHADE = "#c9c2ae"
ROPE = Ramp(["#8f7446", "#b39660", "#d2b983"], name="rope")


def paint(face, pixels, c):
    for x, y in pixels:
        face.put(x, y, c)


def lip(t, r):
    """A 1 px brim all round the hat on the rim plane (the head is plane rows/cols 4..11): the front edge lit, the
    back in shadow."""
    rim = t.face("hat_rim", "front")
    for i in range(3, 13):
        rim.put(3, i, r[1])
        rim.put(12, i, r[1])
        rim.put(i, 3, r[0])
        rim.put(i, 12, r[2])


def crown_top(t, r):
    """Repaint the hat's top face in plain cloth (the helper's top has a weave that reads as speckle on smooth
    materials): a lit face, the row touching the front brightest, the back row and the sides a shade darker."""
    top = t.face("hat", "top")
    b = len(r) // 2
    top.fill(r[b + 1])
    top.fill(r[b], rows=[0])
    top.fill(r[b], cols=[0, top.w - 1])
    top.fill(r[-1], rows=[top.h - 1])


def apron_back(t, r, xs=(1, 6), rows=range(0, 9)):
    """The apron's straps down the back to the waist tie (the apron helper ties it at row 9)."""
    back = t.face("jacket", "back")
    for x in xs:
        for y in rows:
            back.put(x, y, r[3] if y == rows[0] else r[1])   # lit where it comes over the shoulder
    back.put(3, 9, r[0])                                  # the knot
    back.put(4, 9, r[0])


def pocket(t, r, top=12, bottom=15):
    """A patch pocket on the jacket front at x 0..3 (left of the badge): lit top edge, a shade darker than the apron."""
    f = t.face("jacket", "front")
    for y in range(top, bottom + 1):
        for x in range(0, 4):
            f.put(x, y, r[3] if y == top else r[1])
    return f


def bag(face, x0, y0, w, h, r=LEATHER, flap=2):
    """A leather bag on a side face: lit top, a flap `flap` rows deep with a shadowed edge, a dark bottom."""
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            c = r[3] if y == y0 else r[0] if y == y0 + h - 1 else r[1] if y == y0 + flap else r[2]
            face.put(x, y, c)


def strap_diagonal(face, x0, y0, x1, y1, c, width=2):
    """A strap across a face from (x0, y0) to (x1, y1), `width` px wide, one step per row."""
    n = abs(y1 - y0)
    for i in range(n + 1):
        y = y0 + (i if y1 >= y0 else -i)
        x = round(x0 + (x1 - x0) * i / max(1, n))
        for k in range(width):
            face.put(x + k if x1 >= x0 else x - k, y, c)


ZOMBIE_EYES = {1: "#ffffff", 2: "#992b2b", 5: "#992b2b", 6: "#ffffff"}   # zombie villager head front, row 6


def zombie_lenses(t, lens, written, row=6, tint=0.45):
    """Glasses that tint the eyes: the zombie copy tints the zombie's red eyes instead of the villager's green ones."""
    z = t.copy()
    u0, v0 = vg.UV["hat"]["front"][:2]
    for x, c in ZOMBIE_EYES.items():
        z.a[v0 + row, u0 + x] = rgba(mix(rgba(c), rgba(lens), tint))
    for p in written:
        if "zombie_villager" in str(p) and str(p).endswith(".png"):
            z.save(p)
    return written


# ------------------------------------------------------------------------------------------------ outfits

def orchard_keeper():
    """A wide straw sun hat with a red band and a leaf tucked in it, and an olive gardening apron with a pocket of
    picked fruit."""
    t = vg.VillagerTexture()
    straw = Ramp(["#947a24", "#ac8c2f", "#c1a137", "#d1bc45", "#dfcc6f"], name="straw")
    red = vg.cloth("#b3262c")
    green = vg.cloth("#4e7d3a")
    vg.hat(t, straw, style="brim", band=red[2], noise=0.1)   # straw: a light weave
    crown_top(t, straw)
    front = t.face("hat", "front")                       # a leaf tucked in the band
    front.put(5, 2, "#5f9a3a")
    front.put(6, 2, "#3f7a2a")
    front.put(6, 3, "#3f7a2a")

    vg.apron(t, green, top=2, bottom=17, left=1, right=6, ties=True)
    apron_back(t, green)
    f = pocket(t, green)
    f.put(1, 11, "#c62828")                              # an apple and a dark berry peeking out
    f.put(2, 11, "#e0554e")
    f.put(1, 12, "#8f0f16")
    f.put(2, 12, "#aa1a1e")
    f.put(2, 10, "#3f7a2a")
    f.put(3, 12, "#5e1a3a")
    return t.save_profession(ASSETS, "orchard_keeper", hat="full")


def berry_breeder():
    """A plum headscarf with a sprig of two berries (a blue Oran and a red Cheri, the first pair she breeds), a sage
    gardening apron whose hem is stitched in alternating blue and red like her paired rows, and a pocket of berries
    with a seed dibber; mulch on the hem."""
    t = vg.VillagerTexture()
    plum = vg.cloth("#7a3d6e")
    sage = vg.cloth("#7f9a63")
    oran = ["#2f4fa8", "#4f78d0"]                       # dark, lit
    cheri = ["#a3202a", "#d8414a"]
    leaf = ["#3f7a2a", "#5f9a3a"]
    mulch = ["#4a3320", "#5e4229"]

    vg.hat(t, plum, style="beanie", crown=3, noise=0.05)
    crown_top(t, plum)
    back = t.face("hat", "back")                        # the knot and its tails at the back
    back.fill(plum[1], rows=[3])
    paint(back, [(3, 4), (4, 4), (3, 5)], plum[2])
    back.put(4, 5, plum[0])
    front = t.face("hat", "front")                      # the sprig, tucked in on the villager's left
    front.put(5, 1, leaf[1])
    front.put(6, 1, leaf[0])
    front.put(5, 2, oran[1])
    front.put(6, 2, cheri[1])
    front.put(6, 3, cheri[0])

    vg.apron(t, sage, top=2, bottom=17, left=1, right=6, ties=True)
    apron_back(t, sage)
    jf = t.face("jacket", "front")
    for x in range(1, 7):                               # the hem: paired rows, blue and red
        jf.put(x, 17, oran[0] if x % 2 else cheri[0])
    f = pocket(t, sage)
    f.put(1, 11, oran[1])                               # berries peeking out of the pocket
    f.put(1, 12, oran[0])
    f.put(2, 11, cheri[1])
    f.put(2, 12, cheri[0])
    f.put(2, 10, leaf[1])
    f.put(3, 10, "#9a7446")                             # the dibber's handle
    f.put(3, 11, "#7a5a34")
    paint(jf, [(1, 16), (5, 16)], mulch[1])             # mulch on the apron
    jf.put(6, 16, mulch[0])
    return t.save_profession(ASSETS, "berry_breeder", hat="full")


def camp_cook():
    """A rust-red bandana knotted at the back with a sprig of Hearty Grains tucked in it, a canvas camp apron over the
    robe, its hem scorched by the campfire, and in the pocket a wooden ladle and a Poke Snack wrapped in leaf (ROADMAP
    28.8)."""
    t = vg.VillagerTexture()
    rust = vg.cloth("#9a3b22")
    canvas = vg.cloth("#a8946a")
    grain = ["#b08a3a", "#d8b85a"]                      # Hearty Grains: dark, lit
    soot = ["#3a3330", "#4e4540"]
    wood = ["#7a5a34", "#9a7446"]

    vg.hat(t, rust, style="beanie", crown=3, noise=0.05)
    crown_top(t, rust)
    back = t.face("hat", "back")                        # the knot and its two tails
    back.fill(rust[1], rows=[3])
    paint(back, [(3, 4), (4, 4), (4, 5)], rust[2])
    back.put(3, 5, rust[0])
    front = t.face("hat", "front")                      # the grain sprig on the villager's left
    front.put(6, 1, grain[1])
    front.put(5, 1, grain[0])
    front.put(6, 2, grain[0])
    front.put(5, 2, "#5f7a2a")

    vg.apron(t, canvas, top=2, bottom=17, left=1, right=6, ties=True)
    apron_back(t, canvas)
    jf = t.face("jacket", "front")
    for x in range(1, 7):                               # the hem, scorched by the campfire
        jf.put(x, 17, soot[x % 2])
    paint(jf, [(2, 16), (5, 16)], soot[1])
    jf.put(6, 15, "#c8641e")                            # an ember burn
    f = pocket(t, canvas)
    f.put(1, 10, wood[1])                               # the ladle: its bowl above the pocket, the handle inside
    f.put(2, 10, wood[1])
    f.put(1, 11, wood[0])
    f.put(2, 11, wood[1])
    f.put(3, 11, "#4f7a2a")                             # a snack wrapped in leaf
    f.put(3, 12, "#d8b85a")
    return t.save_profession(ASSETS, "camp_cook", hat="full")


def habitat_keeper():
    """A moss-green bush hat with a brim and a Saccharine leaf tucked in its tan band, an olive field vest with a
    honey-gold neckerchief, a brass spyglass in the vest pocket for her sightings, and a honey jar with a dipper on a
    strap at the hip (ROADMAP 28.10)."""
    t = vg.VillagerTexture()
    moss = vg.cloth("#5a6e34")
    olive = vg.cloth("#6f6a3a")
    honey = ["#b8741a", "#e09a26", "#f4c24a"]          # dark, mid, lit
    leaf = ["#3f6a2a", "#6a9a3a"]
    vg.hat(t, moss, style="brim", band="#a88a5a", noise=0)
    crown_top(t, moss)
    front = t.face("hat", "front")                      # the Saccharine leaf in the band, on the villager's left
    front.put(6, 2, leaf[1])
    front.put(5, 2, leaf[0])
    front.put(6, 1, leaf[1])
    vg.vest(t, olive, length=10, open_front=True, noise=0)
    for side in SIDES:                                  # the neckerchief, honey-gold, knotted at the front
        t.face("jacket", side).fill(honey[1], rows=[0])
    jf = t.face("jacket", "front")
    jf.fill(honey[2], rows=[0])
    paint(jf, ((3, 1), (4, 1)), honey[1])
    jf.put(3, 2, honey[0])
    f = pocket(t, olive, top=11, bottom=14)             # the spyglass sticking up out of the vest pocket
    for y in range(8, 12):
        f.put(1, y, BRASS[3] if y == 8 else BRASS[2])
        f.put(2, y, BRASS[1] if y == 8 else BRASS[0])
    f.put(1, 9, "#3a2a1a")                              # the leather grip ring
    f.put(2, 9, "#2a1e12")
    west = t.face("jacket", "west")                     # the honey jar on the villager's right hip, its dipper in it
    strap_diagonal(west, 0, 2, 2, 9, LEATHER[1], width=1)
    for y in range(11, 15):
        for x in range(1, 4):
            west.put(x, y, honey[2] if y == 11 else honey[1] if x < 3 else honey[0])
    paint(west, ((1, 10), (2, 10), (3, 10)), "#d8d2c0")  # the cloth lid
    west.put(2, 9, "#8a6a3e")                           # the dipper's handle
    west.put(2, 8, "#a8844e")
    return t.save_profession(ASSETS, "habitat_keeper", hat="full")


def daycare_keeper():
    """A soft rose headscarf knotted at the brow, a sage-green dress under a linen pinafore apron with a bib, a
    spotted Pokémon egg peeking out of the apron pocket, and a rolled wool blanket on a strap at the hip for the
    hatchlings (ROADMAP 28.12)."""
    t = vg.VillagerTexture()
    rose = vg.cloth("#c46a7e")
    sage = vg.cloth("#6e8a64")
    linen = vg.cloth("#d8d2bc")
    egg = ["#c9c2a4", "#ece6cc", "#f8f4e2"]           # dark, mid, lit
    spot = "#5aa04a"
    vg.hat(t, rose, style="band", noise=0)
    front = t.face("hat", "front")                      # the scarf's knot, over the villager's left brow
    front.put(5, 3, rose[3])
    front.put(6, 3, rose[1])
    vg.vest(t, sage, length=10, open_front=False, noise=0)
    vg.apron(t, linen, top=2, bottom=17, ties=True, bib=True)
    vg.sleeves(t, sage, noise=0)
    f = pocket(t, linen, top=11, bottom=14)             # the egg in the apron pocket: lit top-left, a green spot
    paint(f, ((1, 9), (2, 9)), egg[2])
    paint(f, ((0, 10), (3, 10)), egg[1])
    f.put(1, 10, egg[2])
    f.put(2, 10, spot)
    paint(f, ((0, 11), (3, 11)), egg[0])
    f.put(1, 11, egg[1])
    f.put(2, 11, egg[1])
    west = t.face("jacket", "west")                     # the rolled blanket on the villager's right hip
    strap_diagonal(west, 0, 2, 2, 9, LEATHER[1], width=1)
    for y in range(11, 14):
        for x in range(0, 5):
            west.put(x, y, rose[3] if y == 11 else rose[2] if y == 12 else rose[1])
    west.put(2, 12, "#e6e2d4")                          # the rolled-in end
    return t.save_profession(ASSETS, "daycare_keeper", hat="partial")


def gem_grower():
    """A slate-grey bandana knotted round the head with an amethyst shard pinned at the front, a heavy leather apron
    (heat-proof, for the beds by the lava) over a stone-grey shirt, leather gloves, a brass hand lens in the apron
    pocket for judging the clusters, and a pouch of amethyst shards at the hip (ROADMAP 28.11)."""
    t = vg.VillagerTexture()
    slate = vg.cloth("#5e6470")
    stone = vg.cloth("#7d7f84")
    hide = vg.cloth("#6e4a2c")
    amethyst = ["#5a3a8a", "#8a5ac0", "#c69ae8"]       # dark, mid, lit
    vg.hat(t, slate, style="band", noise=0)
    front = t.face("hat", "front")                      # the shard pinned on the band, over the villager's left eye
    front.put(5, 3, amethyst[2])                        # (on the band itself, its tip just above it)
    front.put(5, 4, amethyst[1])
    front.put(6, 4, amethyst[0])
    vg.vest(t, stone, length=10, open_front=False, noise=0)
    vg.apron(t, hide, top=2, bottom=17, ties=True, bib=True)
    vg.sleeves(t, stone, gloves=vg.cloth("#8a6a3e", n=4), noise=0)
    f = pocket(t, hide, top=11, bottom=14)              # the hand lens in the apron pocket: a brass ring with glass
    f.put(1, 9, BRASS[3])
    f.put(2, 9, BRASS[2])
    f.put(1, 10, BRASS[1])
    f.put(2, 10, "#b8dce6")
    f.put(1, 11, BRASS[0])
    west = t.face("jacket", "west")                     # the shard pouch on the villager's right hip
    strap_diagonal(west, 0, 2, 2, 9, LEATHER[1], width=1)
    for y in range(11, 15):
        for x in range(1, 4):
            west.put(x, y, hide[2] if y == 11 else hide[1] if x < 3 else hide[0])
    west.put(1, 10, amethyst[1])                        # shards peeking out of the pouch
    west.put(2, 9, amethyst[2])
    west.put(3, 10, amethyst[0])
    return t.save_profession(ASSETS, "gem_grower", hat="partial")


def pokemon_trader():
    """A teal cap with a white band, a teal waistcoat, and a satchel on a leather strap with a red-and-white clasp."""
    t = vg.VillagerTexture()
    teal = vg.cloth("#1f7a78")
    vg.hat(t, teal, style="cap", visor=2, band=LINEN, noise=0)
    crown_top(t, teal)
    vg.vest(t, teal, length=11, open_front=True, noise=0)
    strap_diagonal(t.face("jacket", "front"), 0, 0, 7, 9, LEATHER[1])   # over the right shoulder, to the left hip
    strap_diagonal(t.face("jacket", "back"), 7, 0, 0, 9, LEATHER[1])
    east = t.face("jacket", "east")                      # the satchel on the villager's left hip
    bag(east, 0, 9, 5, 7)
    east.put(2, 11, "#c62828")                           # the clasp: red over white
    east.put(2, 12, LINEN)
    return t.save_profession(ASSETS, "pokemon_trader", hat="full")


def porter():
    """A flat wool cap, a canvas vest under leather carrying straps, a crate strapped to the back and a coil of rope at
    the hip."""
    t = vg.VillagerTexture()
    cap = vg.cloth("#6b5a3e")
    canvas = vg.cloth("#8c8a6a")
    vg.hat(t, cap, style="cap", visor=2, noise=0)
    crown_top(t, cap)
    paint(t.face("hat", "top"), ((3, 3), (4, 3)), cap[1])   # the button on top
    vg.vest(t, canvas, length=11, open_front=False, noise=0)
    for side in ("front", "back"):
        f = t.face("jacket", side)
        for x in (1, 6):
            for y in range(0, 11):
                f.put(x, y, LEATHER[3] if y == 0 else LEATHER[1])
    vg.belt(t, LEATHER, row=10, buckle=STEEL[2])
    front = t.face("jacket", "front")                    # a coil of rope on the belt (x 0..3): a ring
    paint(front, ((1, 11), (2, 11)), ROPE[2])
    paint(front, ((0, 12), (3, 12)), ROPE[1])
    paint(front, ((1, 13), (2, 13)), ROPE[0])
    back = t.face("jacket", "back")                      # a crate on the back, strapped on
    oak = P("oak_planks")
    for y in range(1, 9):
        for x in range(1, 7):
            edge = x in (1, 6)
            c = oak[6] if y == 1 else oak[0] if y == 8 else oak[1] if edge else oak[2] if y == 4 else oak[5]
            back.put(x, y, c)
    back.fill(LEATHER[1], rows=[6], cols=range(1, 7))
    return t.save_profession(ASSETS, "porter", hat="full")


def postman():
    """A navy peaked cap with a shiny black peak and a gold badge, a navy tunic, and a leather mailbag on a strap with
    letters sticking out."""
    t = vg.VillagerTexture()
    navy = vg.cloth("#2b4c8c")
    peak = Ramp(["#15171f", "#1f2230", "#2b2f40", "#3e4458", "#555d74"], name="peak")
    vg.hat(t, navy, style="cap", visor=2, brim_ramp=peak, band=peak[2], noise=0)
    crown_top(t, navy)
    hf = t.face("hat", "front")                          # the badge
    hf.put(3, 1, GOLD[3])
    hf.put(4, 1, GOLD[2])
    hf.put(3, 2, GOLD[2])
    hf.put(4, 2, GOLD[1])
    vg.vest(t, navy, length=11, open_front=False, noise=0)
    strap_diagonal(t.face("jacket", "front"), 7, 0, 0, 9, LEATHER[1])   # over the left shoulder, to the right hip
    strap_diagonal(t.face("jacket", "back"), 0, 0, 7, 9, LEATHER[1])
    west = t.face("jacket", "west")                      # the mailbag on the villager's right hip
    bag(west, 0, 8, 6, 8, flap=3)
    paint(west, ((1, 7), (2, 7), (4, 7)), LINEN)          # letters sticking out of the top
    west.put(3, 7, LINEN_SHADE)
    west.put(3, 11, GOLD[2])                             # the flap's buckle
    return t.save_profession(ASSETS, "postman", hat="full")


def rancher():
    """A wide-brimmed brown hat with a pinched crown and a dark band, a red neckerchief, an open leather vest, a belt
    with a silver buckle and a coiled lasso at the hip."""
    t = vg.VillagerTexture()
    felt = vg.cloth("#7a5230")
    red = vg.cloth("#b3262c")
    leather = vg.cloth("#8a5a33")
    vg.hat(t, felt, style="brim", band="#3a2414", noise=0)
    crown_top(t, felt)
    top = t.face("hat", "top")
    for y in range(1, 7):                                # the crease down the crown
        top.put(3, y, felt[1])
        top.put(4, y, felt[3])
    vg.vest(t, leather, length=10, open_front=True, noise=0)
    vg.belt(t, vg.cloth("#4a2e18"), row=10, buckle=STEEL[3])
    for side in SIDES:                                   # neckerchief round the neck, knotted at the front
        t.face("jacket", side).fill(red[2], rows=[0])
    jf = t.face("jacket", "front")
    jf.fill(red[3], rows=[0])
    paint(jf, ((2, 1), (3, 1), (4, 1), (5, 1)), red[2])
    paint(jf, ((3, 2), (4, 2)), red[1])
    west = t.face("jacket", "west")                      # a coiled lasso on the villager's right hip
    paint(west, ((1, 11), (2, 11), (3, 11), (0, 12), (4, 12), (0, 13), (4, 13), (1, 14), (2, 14), (3, 14)), ROPE[1])
    paint(west, ((1, 11), (2, 11)), ROPE[2])
    return t.save_profession(ASSETS, "rancher", hat="full")


def scholar():
    """A black square cap with a gold tassel at the side, a dark blue gown with white bands at the collar and a
    gold-lined hood hanging at the back."""
    t = vg.VillagerTexture()
    black = vg.cloth("#2a2630")
    gown = vg.cloth("#243a66")
    vg.hat(t, black, style="beanie", crown=2, noise=0)
    crown_top(t, black)
    top = t.face("hat", "top")
    paint(top, ((3, 3), (4, 3), (3, 4), (4, 4)), GOLD[2])  # the button, the cord to the side
    paint(top, ((0, 4), (1, 4), (2, 4)), GOLD[1])
    west = t.face("hat", "west")
    for y in range(0, 4):
        west.put(4, y, GOLD[2] if y < 3 else GOLD[1])
    vg.robe(t, gown, length=18, sleeves_too=False, body_too=False, noise=0)
    jf = t.face("jacket", "front")
    paint(jf, ((3, 0), (4, 0), (3, 1), (4, 1)), LINEN)   # the bands
    paint(jf, ((3, 2), (4, 2)), LINEN_SHADE)
    back = t.face("jacket", "back")                      # the hood: gold lining folded over the shoulders
    rows = {0: range(0, 8), 1: range(1, 7), 2: range(1, 7), 3: range(2, 6), 4: range(2, 6), 5: range(3, 5)}
    for y, xs in rows.items():
        for x in xs:
            edge = x in (min(xs), max(xs))
            back.put(x, y, gown[1] if (edge and y > 0) else GOLD[2] if y < 2 else GOLD[1])
    return t.save_profession(ASSETS, "scholar", hat="full")


def shopkeeper():
    """A green eyeshade (a band and a visor), and a green apron with a pocket of coins."""
    t = vg.VillagerTexture()
    green = vg.cloth("#3a8f55")
    visor = vg.cloth("#4aa164")
    for side in SIDES:                                   # the band round the head, just above the visor
        f = t.face("hat", side)
        f.fill(green[3], rows=[2])
        f.fill(green[1], rows=[3])
    rim = t.face("hat_rim", "front")                     # the visor
    for y in range(12, 15):
        for x in range(3, 13):
            if y == 14 and x in (3, 12):
                continue
            rim.put(x, y, visor[1] if (y == 14 or x in (3, 12)) else visor[3])
    vg.apron(t, green, top=2, bottom=17, left=1, right=6, ties=True)
    apron_back(t, green)
    f = pocket(t, green)
    paint(f, ((1, 11), (2, 11)), GOLD[3])                # coins in the pocket
    f.put(1, 12, GOLD[2])
    f.put(2, 12, GOLD[1])
    return t.save_profession(ASSETS, "shopkeeper", hat="full")


def sifter():
    """A dusty brown headscarf knotted at the back, a long canvas apron with a pocket of finds (a gold nugget, a gem)
    and a round sieve hanging on the back."""
    t = vg.VillagerTexture()
    cap = vg.cloth("#7d6243")
    canvas = vg.cloth("#b7a98a")
    crown_top(t, cap)
    for side in SIDES:                                   # the scarf: low over the back, higher at the front
        f = t.face("hat", side)
        for x in range(f.w):
            d = {"front": 0, "back": 7, "west": 7 - x, "east": x}[side]   # distance from the front edge
            depth = 2 + min(2, d // 3)
            for y in range(depth):
                f.put(x, y, cap[3] if y == 0 else cap[1] if y == depth - 1 else cap[2])
    hb = t.face("hat", "back")                           # the knot and its ends
    paint(hb, ((3, 3), (4, 4)), cap[3])
    paint(hb, ((4, 3), (3, 4), (3, 5)), cap[2])
    paint(hb, ((4, 5), (3, 6)), cap[1])
    vg.apron(t, canvas, top=2, bottom=18, left=1, right=6, ties=True)
    apron_back(t, canvas)
    f = pocket(t, canvas)
    f.put(1, 11, "#e8c24a")                              # a gold nugget and a gem
    f.put(1, 12, "#b8901f")
    f.put(2, 12, "#5fd3d0")
    back = t.face("jacket", "back")                      # a round sieve: a wooden hoop round a string mesh
    wood = P("spruce_planks")
    x0, y0 = 1, 1
    for y in range(7):
        for x in range(7):
            corner = x in (0, 6) and y in (0, 6)
            if corner:
                continue
            if x in (0, 6) or y in (0, 6):
                c = wood[6] if (y == 0 or x == 0) else wood[1]
            else:
                c = "#4a3a28" if (x % 2 and y % 2) else "#d8d0bc"
            back.put(x0 + x, y0 + y, c)
    return t.save_profession(ASSETS, "sifter", hat="full")


def teacher():
    """Round glasses, and a green knitted cardigan buttoned down the front with a pencil in the pocket."""
    t = vg.VillagerTexture()
    knit = vg.cloth("#3f6b4a")
    lens = "#cfe6f2"
    vg.glasses(t, frame="#35343c", lens=lens)
    vg.vest(t, knit, length=14, open_front=False, noise=0)
    for side in SIDES:                                   # ribbed hem
        f = t.face("jacket", side)
        for x in range(f.w):
            f.put(x, 12, knit[1] if x % 2 else knit[2])
            f.put(x, 13, knit[0] if x % 2 else knit[1])
    f = t.face("jacket", "front")
    for y in range(0, 14):
        f.put(3, y, knit[1])                             # the button band
    for y in (1, 10):
        f.put(3, y, "#d9c89a")
    for y in (10, 11):                                   # a pocket with a pencil
        for x in range(0, 3):
            f.put(x, y, knit[3] if y == 10 else knit[1])
    f.put(1, 9, "#e2b33c")
    f.put(1, 8, "#d98a8a")
    return zombie_lenses(t, lens, t.save_profession(ASSETS, "teacher", hat=None))


def tinkerer():
    """Brass goggles pushed up on the forehead, and a sooty leather apron with brass rivets and a pocket of parts: a
    screwdriver, a copper gear, redstone."""
    t = vg.VillagerTexture()
    apron = vg.cloth("#6a4a2e")
    hf = t.face("hat", "front")
    lens = ("#9fe0ec", "#4fa9bd")
    for x0 in (0, 4):                                    # two round lenses in brass rims (rows 1-3)
        paint(hf, ((x0 + 1, 1), (x0 + 2, 1)), BRASS[3])
        paint(hf, ((x0, 2), (x0 + 3, 2)), BRASS[2])
        paint(hf, ((x0 + 1, 3), (x0 + 2, 3)), BRASS[1])
        hf.put(x0 + 1, 2, lens[0])
        hf.put(x0 + 2, 2, lens[1])
    for side in ("west", "back", "east"):
        t.face("hat", side).fill("#3b2a1e", rows=[2])
    vg.apron(t, apron, top=2, bottom=18, left=1, right=6, ties=True)
    apron_back(t, apron)
    f = pocket(t, apron)
    paint(f, ((1, 2), (6, 2)), BRASS[3])                 # rivets at the bib's corners
    f.put(1, 10, STEEL[3])                               # a screwdriver
    f.put(1, 11, "#b01a10")
    f.put(2, 11, "#d9a04e")                              # a copper gear
    f.put(3, 11, "#b07a30")
    return t.save_profession(ASSETS, "tinkerer", hat=None)


def trainer():
    """A red cap with a white front panel, and a blue sports jacket with a white stripe round it and down the sleeves."""
    t = vg.VillagerTexture()
    red = vg.cloth("#b82e2a")
    blue = vg.cloth("#2a5f9e")
    vg.hat(t, red, style="cap", visor=3, noise=0)
    crown_top(t, red)
    hf = t.face("hat", "front")
    for y in range(0, 3):
        for x in range(1, 7):
            hf.put(x, y, LINEN if y < 2 else LINEN_SHADE)
    top = t.face("hat", "top")
    for y in (6, 7):
        for x in range(1, 7):
            top.put(x, y, LINEN)
    vg.vest(t, blue, length=11, open_front=False, noise=0)
    for side in SIDES:
        t.face("jacket", side).fill(LINEN, rows=[5])
    t.face("jacket", "front").fill(LINEN, rows=[0])
    vg.sleeves(t, blue, cuff=LINEN, noise=0)
    for side in ("west", "east"):
        a = t.face("arm", side)
        for y in range(a.h - 1):
            a.put(1, y, LINEN)
    return t.save_profession(ASSETS, "trainer", hat="full")


def trainer_leader():
    """A black peaked cap with a gold band and badge, and a long dark coat, double-breasted with gold buttons and a
    gold hem."""
    t = vg.VillagerTexture()
    black = vg.cloth("#2a2a30")
    coat = vg.cloth("#3a3a4a")
    vg.hat(t, black, style="cap", visor=2, band=GOLD[2], noise=0)
    crown_top(t, black)
    hf = t.face("hat", "front")
    hf.put(3, 1, GOLD[3])
    hf.put(4, 1, GOLD[2])
    vg.robe(t, coat, length=18, sleeves_too=False, body_too=False, noise=0)
    jf = t.face("jacket", "front")
    paint(jf, ((2, 1), (5, 1), (2, 15), (5, 15)), GOLD[2])
    for side in SIDES:
        t.face("jacket", side).fill(GOLD[1], rows=[17])
    jf.fill(GOLD[2], rows=[17])
    return t.save_profession(ASSETS, "trainer_leader", hat="full")


def tutor():
    """A black mortarboard with a gold tassel hanging at the front, and a green robe with a white collar."""
    t = vg.VillagerTexture()
    black = vg.cloth("#26242c")
    robe = vg.cloth("#2f5d3a")
    vg.hat(t, black, style="beanie", crown=2, noise=0)
    crown_top(t, black)
    top = t.face("hat", "top")
    paint(top, ((3, 3), (4, 3), (3, 4), (4, 4)), GOLD[2])
    paint(top, ((5, 5), (6, 6), (7, 7)), GOLD[1])
    hf = t.face("hat", "front")
    for y in range(0, 4):
        hf.put(7, y, GOLD[2] if y < 3 else GOLD[1])
    vg.robe(t, robe, length=18, sleeves_too=False, body_too=False, noise=0)
    vg.collar(t, Ramp([LINEN, LINEN_SHADE, LINEN]), rows=1, noise=0)
    return t.save_profession(ASSETS, "tutor", hat="full")


def undertaker():
    """A black top hat with a narrow brim and a dusky purple band, and a long black coat open at the collar over a
    white shirt and a purple cravat, a white lily in the lapel."""
    t = vg.VillagerTexture()
    black = vg.cloth("#2a2630")
    purple = vg.cloth("#5a4a6e")
    vg.hat(t, black, style="beanie", crown=4, band=purple[2], noise=0)
    crown_top(t, black)
    lip(t, black)
    vg.robe(t, black, length=18, sleeves_too=False, body_too=False, noise=0)
    jf = t.face("jacket", "front")
    paint(jf, ((2, 0), (3, 0), (4, 0), (5, 0)), LINEN)   # the shirt collar
    paint(jf, ((3, 1), (4, 1)), purple[3])               # the cravat
    paint(jf, ((3, 2), (4, 2)), purple[1])
    paint(jf, ((2, 1), (2, 2), (5, 1), (5, 2)), black[4])   # the lapels' edges
    jf.put(0, 1, LINEN)                                  # a lily in the buttonhole
    jf.put(1, 1, LINEN_SHADE)
    jf.put(1, 2, "#4f7a3a")
    return t.save_profession(ASSETS, "undertaker", hat="full")


def steward():
    """The Steward (27.5): a clerk's long bottle-green coat with brass buttons down the front and a pale collar, a
    flat clerk's cap of the same cloth, a brown belt and a rolled plan (pale paper tied with blue string) tucked
    at the belt on the villager's right."""
    t = vg.VillagerTexture()
    coat = vg.cloth("#2f5a46")
    vg.hat(t, coat, style="cap", crown=2, visor=1, noise=0)
    crown_top(t, coat)
    vg.robe(t, coat, length=18, sleeves_too=True, body_too=False, noise=0)
    vg.sleeves(t, coat, cuff=LINEN_SHADE, noise=0)
    jf = t.face("jacket", "front")
    paint(jf, ((2, 0), (3, 0), (4, 0), (5, 0)), LINEN)   # the collar
    paint(jf, ((3, 1), (4, 1)), LINEN_SHADE)
    for y in (3, 6, 9, 15):                              # brass buttons down the front edge (not on the badge)
        jf.put(4, y, BRASS[3] if y < 10 else BRASS[2])
    paint(jf, ((3, y) for y in range(2, 18)), coat[1])   # where the coat closes
    vg.belt(t, LEATHER, row=10, buckle=BRASS[2])
    paper = Ramp(["#a89f86", "#cfc6aa", "#e9e2c9", "#f4efdc"], name="paper")
    for y in range(8, 15):                               # the rolled plan, upright at the belt, its end lit
        jf.put(1, y, paper[2] if y > 8 else paper[3])
        jf.put(2, y, paper[1] if y > 8 else paper[2])
    paint(jf, ((1, 9), (2, 9), (1, 13), (2, 13)), "#3c5fa0")   # blue string ties
    jf.put(0, 11, paper[0])
    return t.save_profession(ASSETS, "steward", hat="full")


def legend():
    """A Legend without a trade of their own: a gold circlet on the brow, and a long wine-red mantle with a gold hem,
    a gold-edged collar and a gold clasp at the throat. Plain on purpose: each Legend's own outfit (29.4) is drawn over
    their trade's."""
    t = vg.VillagerTexture()
    wine = vg.cloth("#6e2434")
    vg.hat(t, GOLD, style="band", crown=1, noise=0)
    hat_front = t.face("hat", "front")
    hat_front.put(3, 1, "#5fb3c9")                      # a small blue stone set in the circlet
    hat_front.put(4, 1, "#3c7f95")
    vg.robe(t, wine, length=18, sleeves_too=False, body_too=False, noise=0)
    for side in SIDES:                                   # the gold hem
        f = t.face("jacket", side)
        for x in range(f.w):
            f.put(x, 17, GOLD[1])
            f.put(x, 16, GOLD[2] if side == "front" else GOLD[1])
    jf = t.face("jacket", "front")
    paint(jf, ((1, 0), (2, 0), (5, 0), (6, 0)), GOLD[2])   # the collar's edges
    paint(jf, ((2, 1), (5, 1)), GOLD[1])
    paint(jf, ((3, 0), (4, 0)), wine[3])
    paint(jf, ((3, 1), (4, 1)), GOLD[3])                 # the clasp
    paint(jf, ((3, 2), (4, 2)), GOLD[0])
    return t.save_profession(ASSETS, "legend", hat="partial")


def legend_placeholder():
    """The Legend outfit layer's stand-in (29.4), drawn over any trade's outfit until a Legend has its own
    (textures/entity/villager/legend/<id>.png): a gold circlet with a blue stone round the brow (on the head, so it
    shows under every job's hat rule), and a wine-red cape from the shoulders down the back with a gold hem and a gold
    clasp at the collar. The front stays clear so the trade's own outfit and badge show through."""
    t = vg.VillagerTexture()
    wine = vg.cloth("#6e2434")
    for side in SIDES:                                   # the circlet: a gold band at the brow, just under any hat's
        f = t.face("head", side)                         # brim (head rows 0..3 sit under the hat layer's crown)
        for x in range(f.w):
            f.put(x, 4, GOLD[2] if side in ("front", "west") else GOLD[1])
    hf = t.face("head", "front")
    paint(hf, ((2, 4), (5, 4)), GOLD[3])                 # the setting, and the blue stone in it
    paint(hf, ((3, 4),), "#5fb3c9")
    paint(hf, ((4, 4),), "#3c7f95")
    vg.robe(t, wine, length=18, sleeves_too=False, body_too=False, noise=0)
    jf = t.face("jacket", "front")
    jf.clear(rows=range(2, 20))                          # the front shows the trade's outfit
    jf.clear(rows=range(0, 2), cols=range(3, 5))
    paint(jf, ((0, 0), (1, 0), (6, 0), (7, 0)), GOLD[2])  # the cape's gold-edged collar on the shoulders
    paint(jf, ((0, 1), (1, 1), (6, 1), (7, 1)), wine[2])
    paint(jf, ((2, 0), (5, 0)), GOLD[3])                 # the clasp's two ends
    paint(jf, ((2, 1), (5, 1)), GOLD[1])
    for side, back_cols in (("west", range(0, 3)), ("east", range(3, 6))):
        f = t.face("jacket", side)
        keep = set(back_cols)
        f.clear(rows=range(3, 20), cols=[x for x in range(f.w) if x not in keep])
        f.clear(rows=range(18, 20))
        for x in back_cols:                              # the hem where the cape wraps round
            f.put(x, 17, GOLD[1])
    jb = t.face("jacket", "back")
    jb.clear(rows=range(18, 20))
    for x in range(jb.w):                                # the gold hem along the bottom of the cape
        jb.put(x, 17, GOLD[1])
        jb.put(x, 16, GOLD[2])
    paint(jb, ((3, 0), (4, 0)), GOLD[2])                 # the clasp's chain over the shoulders
    t.face("jacket", "bottom").clear()
    path = ASSETS / "textures" / "entity" / "villager" / "legend" / "placeholder.png"
    t.save(path)
    return [path]


def master_architect():
    """The Master Architect (29.12), drawn over the Builder's outfit: a long deep-blue coat to the shins with paler
    cuffs and a turned-down collar, brass buttons down the front edge, a brown belt with a brass buckle, a brass
    compass hanging at the belt on the villager's left and a rolled drawing (pale paper tied with red string) tucked
    upright at the belt on the right. A gold circlet at the brow marks the Legend under any hat."""
    t = vg.VillagerTexture()
    coat = vg.cloth("#2a4a86")
    for side in SIDES:                                   # the Legend's circlet, under any hat's brim
        f = t.face("head", side)
        for x in range(f.w):
            f.put(x, 4, GOLD[2] if side in ("front", "west") else GOLD[1])
    vg.robe(t, coat, length=18, sleeves_too=True, body_too=False, noise=0)
    vg.sleeves(t, coat, cuff="#7f9ccf", noise=0)
    jf = t.face("jacket", "front")
    paint(jf, ((1, 0), (2, 0), (5, 0), (6, 0)), coat[3])  # the turned-down collar
    paint(jf, ((2, 1), (5, 1)), coat[2])
    paint(jf, ((3, 0), (4, 0)), LINEN)                   # a pale shirt at the throat
    paint(jf, ((3, y) for y in range(2, 18)), coat[0])   # where the coat closes
    for y in (3, 6, 15):                                 # brass buttons down the front edge (the badge sits on 10..13)
        jf.put(4, y, BRASS[3])
    vg.belt(t, LEATHER, row=9, buckle=BRASS[2])
    paper = Ramp(["#a89f86", "#cfc6aa", "#e9e2c9", "#f4efdc"], name="paper")
    for y in range(6, 15):                               # the rolled drawing, upright at the belt, its end lit
        jf.put(1, y, paper[2] if y > 6 else paper[3])
        jf.put(2, y, paper[1] if y > 6 else paper[2])
    paint(jf, ((1, 8), (2, 8), (1, 12), (2, 12)), "#a8322a")   # red string ties
    jf.put(0, 10, paper[0])
    ef = t.face("jacket", "east")                        # the compass on the left hip: a brass ring on a short chain
    ef.put(1, 10, BRASS[1])
    paint(ef, ((1, 11), (2, 11), (0, 12), (3, 12), (1, 13), (2, 13)), BRASS[2])
    paint(ef, ((1, 12), (2, 12)), "#e9e2c9")             # its pale face
    ef.put(2, 12, "#a8322a")                             # and the red needle
    paint(ef, ((0, 11), (3, 11), (0, 13), (3, 13)), BRASS[0])
    path = ASSETS / "textures" / "entity" / "villager" / "legend" / "master_architect.png"
    t.save(path)
    return [path]


def pathfinder():
    """The Pathfinder (29.13), drawn over the Cartographer's outfit: a hooded travel cloak in weathered forest green to
    the shins, its hood up with a gold circlet (the Legend's mark) at the brow; a brown leather pack on the back with a
    rolled blanket on top and straps over the shoulders; a lit lantern hanging at the belt on the villager's left, and a
    brass-buckled belt."""
    t = vg.VillagerTexture()
    cloak = vg.cloth("#3f5a34")
    vg.hat(t, cloak, style="hood")
    hb = t.face("hat", "back")                           # the hood's back: a seam down the middle, a darker hem
    paint(hb, ((4, y) for y in range(1, 8)), cloak[1])
    paint(hb, ((x, 7) for x in range(hb.w)), cloak[1])
    paint(hb, ((x, 0) for x in range(hb.w)), cloak[3])
    for side in SIDES:                                   # the Legend's circlet at the hood's edge
        f = t.face("head", side)
        for x in range(f.w):
            f.put(x, 4, GOLD[2] if side in ("front", "west") else GOLD[1])
    vg.robe(t, cloak, length=18, sleeves_too=True, body_too=False, noise=0)
    vg.sleeves(t, cloak, cuff="#2d4226", noise=0)
    jf = t.face("jacket", "front")
    paint(jf, ((3, y) for y in range(0, 18)), cloak[0])  # where the cloak closes
    paint(jf, ((1, 1), (6, 1)), LEATHER[1])              # the pack's straps over the shoulders
    paint(jf, ((1, 2), (6, 2), (1, 3), (6, 3)), LEATHER[2])
    vg.belt(t, LEATHER, row=9, buckle=BRASS[2])
    jb = t.face("jacket", "back")                        # the pack: leather, a flap, a rolled blanket on top
    for y in range(2, 11):
        for x in range(1, 7):
            jb.put(x, y, LEATHER[2] if x < 6 and y < 10 else LEATHER[1])
    paint(jb, ((x, 4) for x in range(1, 7)), LEATHER[0])  # the flap's edge
    jb.put(3, 5, BRASS[2])
    jb.put(4, 5, BRASS[1])
    roll = Ramp(["#6b2e22", "#8f4130", "#b25a40"], name="blanket")
    for x in range(1, 7):
        jb.put(x, 1, roll[2] if x < 4 else roll[1])
        jb.put(x, 2, roll[1] if x < 4 else roll[0])
    ef = t.face("jacket", "east")                        # the lantern on the left hip: iron cap, lit glass, iron base
    paint(ef, ((1, 10), (2, 10)), "#3c3c44")
    paint(ef, ((1, 11), (2, 11), (1, 12), (2, 12)), "#f2b33d")
    ef.put(1, 11, "#fde08a")
    paint(ef, ((0, 11), (3, 11), (0, 12), (3, 12)), "#4a4a52")
    paint(ef, ((1, 13), (2, 13)), "#3c3c44")
    path = ASSETS / "textures" / "entity" / "villager" / "legend" / "pathfinder.png"
    t.save(path)
    return [path]


def old_sage():
    """The Old Sage (29.14): a long grey wool robe to the feet with a darker hem and a rope belt, its hood down on the
    shoulders; a long white beard from the chin down over the chest; a silver circlet (the Legend's mark) on the brow;
    a gnarled oak staff with a knot of moss carried at the villager's left side."""
    t = vg.VillagerTexture()
    robe_ = vg.cloth("#7d7f84")
    vg.robe(t, robe_, length=20, sleeves_too=True, body_too=True, noise=0)
    vg.sleeves(t, robe_, cuff="#5c5e63", noise=0)
    vg.belt(t, ROPE, row=9)
    beard = Ramp(["#a9a59a", "#cfcbc0", "#e3dfd4"], name="beard")
    hf = t.face("head", "front")                         # the beard: from the cheeks down past the chin
    for y in range(6, hf.h):
        for x in range(hf.w):
            if y >= 7 or x in (0, 1, 6, 7):
                hf.put(x, y, beard[0] if y == hf.h - 1 and x in (0, 7) else beard[1])
    for x in range(2, 6):
        hf.put(x, 9, beard[2] if x in (2, 3) else beard[1])
    for side in ("west", "east"):                       # the beard round the jaw
        f = t.face("head", side)
        for y in range(7, f.h):
            for x in range(f.w):
                if (side == "west" and x >= f.w - 3) or (side == "east" and x < 3):
                    f.put(x, y, beard[0] if y == f.h - 1 else beard[1])
    for side in SIDES:                                   # the silver circlet at the brow
        f = t.face("head", side)
        for x in range(f.w):
            f.put(x, 2, STEEL[2] if side in ("front", "west") else STEEL[1])
    jf = t.face("jacket", "front")                       # the beard falls down the chest
    for y in range(0, 9):
        for x in range(2, 6):
            if y < 7 or x in (3, 4):
                jf.put(x, y, beard[2] if x == 2 else beard[0] if y == 8 or x == 5 else beard[1])
    paint(jf, ((7, y) for y in range(8, 20)), "#5c4024")  # the staff, seen past the robe's edge
    paint(jf, ((7, 12), (7, 17)), "#3f2b17")
    jb = t.face("jacket", "back")                        # the hood down on the shoulders
    paint(jb, ((x, y) for x in range(jb.w) for y in (0, 1, 2)), robe_[1])
    paint(jb, ((x, 3) for x in range(1, jb.w - 1)), robe_[0])
    staff = Ramp(["#3f2b17", "#5c4024", "#7a5732"], name="staff")
    ef = t.face("jacket", "east")                        # the gnarled staff: a crooked shaft, knots and a mossy knob
    shaft = [(1, y) for y in range(3, 20)] + [(2, y) for y in (6, 7, 13)]
    paint(ef, shaft, staff[1])
    paint(ef, ((0, 9), (2, 11), (0, 16)), staff[0])      # the knots
    paint(ef, ((1, 0), (2, 0), (0, 1), (1, 1), (2, 1), (1, 2), (2, 2)), staff[2])
    paint(ef, ((0, 0), (0, 2)), "#4f6b2c")               # moss on the knob
    ef.put(2, 1, "#6d8a3a")
    path = ASSETS / "textures" / "entity" / "villager" / "legend" / "old_sage.png"
    t.save(path)
    return [path]


def seer():
    """The Seer (29.16), drawn over the Legend trade's mantle: a deep purple robe to the feet with its hood up, sewn
    with silver stars (single stitches and a few four-point sparkles), a silver hem and cuffs, a silver circlet (the
    Legend's mark) at the hood's edge with a pale moonstone, and a crescent moon clasp at the throat."""
    t = vg.VillagerTexture()
    robe_ = vg.cloth("#3b2160")
    silver = STEEL
    star, glint = "#c9cfe0", "#eef1fa"
    vg.hat(t, robe_, style="hood")
    hb = t.face("hat", "back")                           # the hood's back: a seam, a darker hem, a star
    paint(hb, ((4, y) for y in range(1, 8)), robe_[1])
    paint(hb, ((x, 7) for x in range(hb.w)), robe_[1])
    paint(hb, ((x, 0) for x in range(hb.w)), robe_[3])
    paint(hb, ((2, 3), (6, 5)), star)
    for side in ("west", "east"):
        f = t.face("hat", side)
        f.put(2 if side == "west" else 5, 4, star)
    for side in SIDES:                                   # the silver circlet at the hood's edge
        f = t.face("head", side)
        for x in range(f.w):
            f.put(x, 4, silver[2] if side in ("front", "west") else silver[1])
    hf = t.face("head", "front")
    paint(hf, ((3, 4),), "#d8e6f2")                      # the moonstone, lit on its top-left
    paint(hf, ((4, 4),), "#9fb4cc")
    vg.robe(t, robe_, length=20, sleeves_too=True, body_too=True, noise=0)
    vg.sleeves(t, robe_, cuff=silver[1], noise=0)
    jf = t.face("jacket", "front")
    paint(jf, ((3, y) for y in range(2, 20)), robe_[0])  # where the robe closes
    paint(jf, ((3, 0), (4, 0)), silver[3])               # the crescent clasp at the throat
    paint(jf, ((2, 1), (3, 1)), silver[2])
    jf.put(4, 1, robe_[2])
    for side in SIDES:                                   # the silver hem
        f = t.face("jacket", side)
        for x in range(f.w):
            f.put(x, 19, silver[1])
    # The stars: single stitches, and four-point sparkles with a bright heart (off the badge, x 4..7 rows 10..13).
    def sparkle(f, x, y):
        f.put(x, y, glint)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            f.put(x + dx, y + dy, star)
    paint(jf, ((1, 3), (6, 4), (0, 9), (2, 15), (6, 17), (1, 18)), star)
    sparkle(jf, 1, 12)
    sparkle(jf, 6, 7)
    jb = t.face("jacket", "back")
    paint(jb, ((1, 2), (6, 1), (3, 6), (0, 11), (7, 13), (2, 17), (5, 18)), star)
    sparkle(jb, 5, 9)
    sparkle(jb, 2, 14)
    for side, pts in (("west", ((1, 3), (4, 8), (2, 14), (4, 17))), ("east", ((3, 2), (1, 9), (3, 15), (1, 17)))):
        f = t.face("jacket", side)
        paint(f, pts, star)
        sparkle(f, 2, 11)
    am = t.face("arms_middle", "front")                  # a star on the folded sleeves
    paint(am, ((1, 1), (6, 2)), star)
    path = ASSETS / "textures" / "entity" / "villager" / "legend" / "seer.png"
    t.save(path)
    return [path]


def golem_smith():
    """The Golem Smith (29.15), drawn over the Tinkerer's outfit: a heavy dark leather smith's apron from the chest to
    the shins with iron rivets and a scorched hem; riveted iron-rimmed goggles down over the eyes on a leather strap;
    iron-banded leather gloves where the crossed arms meet, iron bands round the cuffs; a gold circlet (the Legend's
    mark) above the goggle strap."""
    t = vg.VillagerTexture()
    apron = vg.cloth("#4a3220")
    vg.apron(t, apron, top=1, bottom=18, left=1, right=6, ties=True)
    apron_back(t, apron)
    jf = t.face("jacket", "front")
    paint(jf, ((1, 1), (6, 1)), STEEL[3])                # rivets at the bib's corners
    paint(jf, ((1, 9), (6, 9)), STEEL[2])                # and at the waist
    paint(jf, ((x, 18) for x in range(1, 7)), "#2a1c12")  # the hem, scorched at the forge
    jf.put(2, 17, "#3a281a")
    jf.put(5, 17, "#3a281a")
    glove = vg.cloth("#6b4a2c", n=4)
    vg.sleeves(t, vg.cloth("#5c4a3a"), cuff=STEEL[1], gloves=glove, noise=0)
    af = t.face("arm", "front")                          # iron bands across the gloves' backs
    for x in range(af.w):
        if af.get(x, 1)[3]:
            af.put(x, 1, STEEL[2] if x % 4 else STEEL[3])
    vg.glasses(t, frame=STEEL[1], lens="#e0803a", strap="#3b2a1e")
    hf = t.face("hat", "front")
    paint(hf, ((0, 5), (7, 5)), STEEL[0])                # rivets where the strap meets the rims
    paint(hf, ((3, 7), (4, 7)), STEEL[2])                # the bridge
    cap = vg.cloth("#3b2a1e")                            # a close leather smith's cap over the Tinkerer's pushed-up goggles
    top = t.face("hat", "top")
    for y in range(top.h):
        for x in range(top.w):
            top.put(x, y, cap[2] if y < 2 else cap[1])
    for side in SIDES:
        f = t.face("hat", side)
        for y in range(0, 4):
            for x in range(f.w):
                f.put(x, y, cap[2] if y == 0 and side in ("front", "west") else cap[1] if y < 3 else cap[0])
        for x in range(f.w):                             # the Legend's circlet at the cap's edge
            f.put(x, 4, GOLD[2] if side in ("front", "west") else GOLD[1])
    hf.put(3, 1, STEEL[2])                               # an iron rivet on the cap's front
    hf.put(4, 1, STEEL[1])
    path = ASSETS / "textures" / "entity" / "villager" / "legend" / "golem_smith.png"
    t.save(path)
    return [path]


def merchant_prince():
    """The Merchant Prince (29.17): a long crimson coat to the shins with black cuffs and a turned-down collar, gold
    buttons down the front edge, a white shirt and cravat at the throat, a black belt with a gold buckle and a coin
    purse at the left hip; a wide-brimmed black hat with a gold band and a white feather sweeping back on the left.
    A gold circlet at the brow marks the Legend under the hat."""
    t = vg.VillagerTexture()
    coat = vg.cloth("#9a1f2a")
    felt = vg.cloth("#2a2226")
    vg.hat(t, felt, style="brim", band=GOLD[1])
    for side in SIDES:                                   # the Legend's circlet, under the hat's brim
        f = t.face("head", side)
        for x in range(f.w):
            f.put(x, 4, GOLD[2] if side in ("front", "west") else GOLD[1])
    feather = Ramp(["#b9b4a6", "#dcd8cc", "#f1eee6"], name="feather")
    he = t.face("hat", "east")                           # the feather on the hat's left side, sweeping back and up
    paint(he, ((1, 3), (2, 2), (3, 2), (4, 1), (5, 1), (6, 0), (7, 0)), feather[1])
    paint(he, ((2, 3), (3, 3), (4, 2), (5, 2)), feather[2])
    paint(he, ((6, 1), (7, 1)), feather[0])
    hb = t.face("hat", "back")
    paint(hb, ((0, 0), (1, 0), (0, 1)), feather[1])      # its tip seen from behind
    vg.robe(t, coat, length=18, sleeves_too=True, body_too=False, noise=0)
    vg.sleeves(t, coat, cuff="#1e1a1c", noise=0)
    jf = t.face("jacket", "front")
    paint(jf, ((1, 0), (2, 0), (5, 0), (6, 0)), coat[3])  # the turned-down collar
    paint(jf, ((2, 1), (5, 1)), coat[2])
    paint(jf, ((3, 0), (4, 0), (3, 1), (4, 1)), LINEN)   # the shirt and cravat at the throat
    paint(jf, ((3, 2), (4, 2)), LINEN_SHADE)
    paint(jf, ((3, y) for y in range(3, 18)), coat[0])   # where the coat closes
    for y in (4, 7, 15):                                 # gold buttons down the front edge (the badge sits on 10..13)
        jf.put(4, y, GOLD[3])
        jf.put(2, y, GOLD[2])
    vg.belt(t, vg.cloth("#1e1a1c"), row=9, buckle=GOLD[2])
    ef = t.face("jacket", "east")                        # the coin purse on the left hip: leather, a gold clasp
    paint(ef, ((1, 10), (2, 10)), LEATHER[0])
    paint(ef, ((0, 11), (1, 11), (2, 11), (3, 11), (0, 12), (1, 12), (2, 12), (3, 12)), LEATHER[2])
    paint(ef, ((1, 13), (2, 13)), LEATHER[1])
    paint(ef, ((1, 11), (2, 11)), GOLD[2])
    path = ASSETS / "textures" / "entity" / "villager" / "legend" / "merchant_prince.png"
    t.save(path)
    return [path]


def grand_chef():
    """The Grand Chef (29.18), drawn over the Chef's outfit: a tall, full white toque pleated from the crown to the
    band (deep shadowed pleats, a lit ridge on each, the crown's puff swelling over the edge), a gold band round it (the
    Legend's mark, in place of the Chef's white one), and a gold ladle hanging at the apron's right side: its bowl at the
    hip, the handle up to the apron string."""
    t = vg.VillagerTexture()
    white = Ramp(["#a9a99e", "#c4c3b9", "#dcdbd2", "#ebeae3", "#f6f5ef"], name="linen")
    for side in SIDES:                                   # the toque: four rows of deep pleats, the band at row 4
        f = t.face("hat", side)
        lit = side in ("front", "west")
        for y in range(0, 4):
            for x in range(f.w):
                c = white[3]
                if x % 3 == 2:
                    c = white[1] if y > 0 else white[2]  # the pleat's deep shadow, shallower where the puff swells
                elif x % 3 == 0 and lit:
                    c = white[4]                         # the pleat's lit ridge
                if y == 0 and x % 3 != 2:
                    c = white[4] if lit else white[3]    # the puff swelling over the edge
                f.put(x, y, c)
        for x in range(f.w):                             # the gold band
            f.put(x, 4, GOLD[2] if lit else GOLD[1])
        if side == "front":
            f.put(3, 4, GOLD[3])                         # a gold stud at the brow
            f.put(4, 4, GOLD[3])
    top = t.face("hat", "top")                           # the crown: a big puff, darker at the rim, gathered in the middle
    for y in range(8):
        for x in range(8):
            edge = x in (0, 7) or y in (0, 7)
            top.put(x, y, white[2] if edge else white[3])
    for x, y in ((3, 3), (4, 3), (3, 4), (4, 4)):
        top.put(x, y, white[1])
    for x, y in ((1, 6), (2, 6), (1, 5), (2, 5)):
        top.put(x, y, white[4])
    jf = t.face("jacket", "front")                       # the gold ladle at the apron (x 0..3, clear of the badge)
    paint(jf, ((1, y) for y in range(9, 14)), GOLD[2])   # the handle, hooked over the apron string
    jf.put(2, 9, GOLD[1])
    jf.put(1, 9, GOLD[3])
    paint(jf, ((0, 14), (1, 14), (2, 14)), GOLD[2])      # the bowl
    paint(jf, ((0, 15), (1, 15), (2, 15)), GOLD[1])
    jf.put(0, 14, GOLD[3])
    jf.put(1, 16, GOLD[0])
    path = ASSETS / "textures" / "entity" / "villager" / "legend" / "grand_chef.png"
    t.save(path)
    return [path]

def bard_laureate():
    """The Bard Laureate (29.19), drawn over the Bard's look: a green doublet to the hips with darker sleeves, a row of
    brass buttons and a leather belt; a lute slung on the back (a pear-shaped spruce body with a dark sound hole, its
    neck running up to the right shoulder, the strap across the chest), and a laurel wreath round the brow: two rows of
    leaves, lit on top, the ends crossing at the back."""
    t = vg.VillagerTexture()
    doublet = vg.cloth("#3f7a32")
    dark = vg.cloth("#2c5624")
    vg.robe(t, doublet, length=12, sleeves_too=False, body_too=False, noise=0)
    vg.sleeves(t, dark, cuff=doublet[3], noise=0)
    jf = t.face("jacket", "front")
    paint(jf, ((3, y) for y in range(0, 12)), doublet[0])       # where the doublet closes
    for y in (2, 5, 8):                                        # brass buttons (clear of the badge rows 10..13)
        jf.put(4, y, BRASS[3])
    paint(jf, ((3, 0), (4, 0)), LINEN)                          # the shirt at the throat
    vg.belt(t, LEATHER, row=9, buckle=BRASS[2])
    strap_diagonal(jf, 0, 0, 2, 8, LEATHER[1], width=1)         # the lute's strap across the chest
    wood = Ramp(["#5c3a1a", "#7d5128", "#a06c38", "#c28a4c"], name="spruce")
    jb = t.face("jacket", "back")                               # the lute on the back
    body = [(x, y) for y in range(10, 17) for x in range(1, 7) if not ((y in (10, 16)) and x in (1, 6))]
    paint(jb, body, wood[2])
    paint(jb, ((x, 16) for x in range(2, 6)), wood[0])          # its shadowed bottom
    paint(jb, ((1, y) for y in range(11, 16)), wood[1])
    paint(jb, ((2, 10), (3, 10), (4, 10)), wood[3])            # lit shoulder of the body
    paint(jb, ((3, 13), (4, 13), (3, 14), (4, 14)), "#2a1a0c")   # the sound hole
    paint(jb, ((x, 12) for x in (2, 5)), wood[3])
    for i, y in enumerate(range(9, 1, -1)):                     # the neck up to the right shoulder
        x = 4 + i // 3
        jb.put(x, y, wood[1])
        jb.put(x + 1, y, wood[0])
    paint(jb, ((7, 1), (7, 0), (6, 0)), "#2a1a0c")              # the pegbox
    leaf = Ramp(["#2f5a1c", "#4a8a2a", "#6cb03c", "#94cf5a"], name="laurel")
    for side in SIDES:                                          # the laurel wreath round the brow
        f = t.face("head", side)
        lit = side in ("front", "west")
        for x in range(f.w):
            f.put(x, 3, leaf[2] if (x % 2 == 0) == lit else leaf[1])
            f.put(x, 4, leaf[1] if x % 2 == 0 else leaf[0])
            if x % 2 == 1:
                f.put(x, 2, leaf[3] if lit else leaf[2])        # leaf tips over the band
    hb = t.face("head", "back")
    paint(hb, ((3, 5), (4, 5)), leaf[0])                        # the ends crossing at the back
    path = ASSETS / "textures" / "entity" / "villager" / "legend" / "bard_laureate.png"
    t.save(path)
    return [path]


def beastmaster():
    """The Beastmaster (29.20), drawn over the Rancher's look: a wolf-pelt hood, grey fur over the head with the wolf's
    dark-tipped ears at the crown, its brow and amber glass eyes over the villager's own, and the pelt running down the
    back to a bushy tail; under it a cloak of brown furs to the knees with a shaggy hem, a cream fur collar and cuffs,
    darker fur sleeves and a leather belt with a bone toggle."""
    t = vg.VillagerTexture()
    pelt = Ramp(["#4a4a4e", "#6c6b6e", "#8e8c8c", "#b2aeaa"], name="wolf")
    dark = "#2f2d30"
    cream = Ramp(["#a8957a", "#c8b698", "#e0d2b4"], name="cream fur")
    vg.hat(t, pelt, style="hood", noise=0)
    hf = t.face("hat", "front")                          # the wolf's brow over the face: dark mask, amber eyes
    paint(hf, ((x, 0) for x in range(hf.w)), pelt[2])
    paint(hf, ((x, 1) for x in range(1, hf.w - 1)), pelt[1])
    paint(hf, ((1, 1), (6, 1)), dark)
    paint(hf, ((2, 1), (5, 1)), "#d89a2a")              # the glass eyes
    paint(hf, ((3, 1), (4, 1)), pelt[3])                # the pale blaze down the nose
    paint(hf, ((0, 0), (1, 0), (6, 0), (7, 0)), dark)   # the ears at the front corners
    for side in ("west", "east"):                       # from the side: the ears at the crown, the pale fringe of the pelt
        f = t.face("hat", side)
        paint(f, ((x, 0) for x in range(f.w - 3, f.w) if side == "west"), dark)
        paint(f, ((x, 0) for x in range(0, 3) if side == "east"), dark)
        paint(f, ((x, f.h - 1) for x in range(f.w)), pelt[3] if side == "west" else pelt[2])
        paint(f, ((x, f.h - 2) for x in range(0, f.w, 2)), pelt[3] if side == "west" else pelt[2])
    top = t.face("hat", "top")
    paint(top, ((x, y) for x in (0, 1, 6, 7) for y in (0, 1)), dark)   # the ears from above
    paint(top, ((x, y) for x in range(2, 6) for y in range(2, 8)), pelt[1])
    paint(top, ((3, y) for y in range(2, 8)), pelt[0])  # the dark stripe along the wolf's back
    hb = t.face("hat", "back")
    paint(hb, ((3, y) for y in range(hb.h)), pelt[0])
    paint(hb, ((4, y) for y in range(hb.h)), pelt[1])
    furs = Ramp(["#3e2816", "#5c3c22", "#7a5432", "#986c44"], name="furs")
    vg.robe(t, furs, length=14, sleeves_too=False, body_too=False, noise=0)
    vg.sleeves(t, vg.cloth("#4e3220"), cuff=cream[1], noise=0)
    for side in SIDES:                                  # the shaggy hem: tufts hanging a pixel lower
        f = t.face("jacket", side)
        for x in range(f.w):
            if x % 2 == 0:
                f.put(x, 14, furs[0])
            if (x + 1) % 4 == 0:
                f.put(x, 15, furs[0])
    jf = t.face("jacket", "front")
    paint(jf, ((x, y) for x in range(jf.w) for y in (0, 1)), cream[2])   # the cream fur collar
    paint(jf, ((x, 1) for x in range(0, jf.w, 2)), cream[1])
    paint(jf, ((3, y) for y in range(2, 14)), furs[0])  # where the cloak closes
    vg.belt(t, LEATHER, row=9, buckle="#e8e0c8")        # a bone toggle for a buckle
    jb = t.face("jacket", "back")                       # the pelt down the back, ending in the bushy tail
    for y in range(0, 16):
        for x in range(1, 7):
            if y < 10 or (y < 16 and 2 <= x <= 5 and not (y == 15 and x in (2, 5))):
                jb.put(x, y, pelt[2] if x < 3 else pelt[1])
    paint(jb, ((x, y) for y in range(0, 10) for x in (3, 4)), pelt[0])   # its dark back stripe
    paint(jb, ((x, y) for y in range(13, 16) for x in (3, 4)), pelt[3])  # the pale tip of the tail
    paint(jb, ((1, 10), (6, 10)), pelt[0])
    for side in ("west", "east"):
        f = t.face("jacket", side)
        paint(f, ((x, y) for x in range(f.w) for y in (0, 1)), cream[1] if side == "west" else cream[0])
    path = ASSETS / "textures" / "entity" / "villager" / "legend" / "beastmaster.png"
    t.save(path)
    return [path]


def founder():
    """The Founder (29.23): a burgundy mantle to the shins over the shoulders and down the sleeves, edged in a band of
    ermine-white at the collar; over it a gold chain of office, links running from both shoulders down to a round
    medallion on the chest (above the crossed arms, so it shows from the front), and a gold circlet at the brow."""
    t = vg.VillagerTexture()
    mantle = Ramp(["#3e0f1a", "#5c1626", "#7a2034", "#962c44"], name="burgundy")
    ermine = Ramp(["#bdb6a6", "#dcd6c8", "#eeeae0"], name="ermine")
    for side in SIDES:                                   # the circlet at the brow
        f = t.face("head", side)
        for x in range(f.w):
            f.put(x, 1, GOLD[2] if side in ("front", "west") else GOLD[1])
    vg.robe(t, mantle, length=18, sleeves_too=True, body_too=False, noise=0)
    vg.sleeves(t, mantle, cuff=ermine[1], noise=0)
    jf = t.face("jacket", "front")
    paint(jf, ((x, 0) for x in range(jf.w)), ermine[2])  # the ermine collar
    paint(jf, ((3, y) for y in range(1, 18)), mantle[0])  # where the mantle closes
    # The chain of office, high on the chest where the crossed arms leave it showing: links from each shoulder down
    # to a round medallion, lit and shaded in turn
    paint(jf, ((0, 1), (1, 2), (2, 2), (7, 1), (6, 2), (5, 2)), GOLD[2])
    paint(jf, ((1, 1), (6, 1)), GOLD[0])
    paint(jf, ((3, 2), (4, 2), (3, 3), (4, 3)), GOLD[3])  # the medallion
    paint(jf, ((4, 3),), GOLD[1])
    for side in ("west", "east"):                       # the chain over the shoulders
        f = t.face("jacket", side)
        paint(f, ((x, 0) for x in range(f.w)), ermine[1] if side == "west" else ermine[0])
        paint(f, ((x, 1) for x in range(1, f.w, 2)), GOLD[2] if side == "west" else GOLD[1])
    jb = t.face("jacket", "back")
    paint(jb, ((x, 0) for x in range(jb.w)), ermine[1])
    paint(jb, ((x, 1) for x in range(0, jb.w, 2)), GOLD[1])    # the chain round the back of the neck
    path = ASSETS / "textures" / "entity" / "villager" / "legend" / "founder.png"
    t.save(path)
    return [path]


def pokemon_professor():
    """The Pokemon Professor (29.21): a long white lab coat to the shins, pale linen and never pure white, with white
    sleeves and turned-back cuffs, wide lapels open over a blue shirt and a red tie, a pocket on the right below the
    crossed arms with a red and a blue pen clipped in it, and the coat's centre seam and fold shadows down the front; a vent up the back.
    A gold circlet at the brow marks the Legend."""
    t = vg.VillagerTexture()
    coat = Ramp(["#a6a59b", "#c6c5bc", "#dddcd4", "#ebeae3", "#f5f4ee"], name="labcoat")
    vg.robe(t, coat, length=18, folds=False, sleeves_too=True, body_too=True, noise=0)
    vg.sleeves(t, coat, cuff=coat[1], noise=0)
    for side in SIDES:                                   # the Legend's gold circlet at the brow
        f = t.face("head", side)
        for x in range(f.w):
            f.put(x, 2, GOLD[2] if side in ("front", "west") else GOLD[1])
    hf = t.face("head", "front")
    hf.put(3, 2, GOLD[3])
    hf.put(4, 2, GOLD[3])
    jf = t.face("jacket", "front")
    shirt = Ramp(["#3c5a86", "#56769f", "#7493b8"], name="shirt")
    tie = Ramp(["#8e1f24", "#b3302f", "#cf4a3f"], name="tie")
    paint(jf, ((3, 0), (4, 0)), shirt[2])                # the shirt's collar, lit
    paint(jf, ((3, y) for y in range(1, 6)), shirt[1])   # the shirt in the open front
    paint(jf, ((4, y) for y in range(1, 6)), shirt[0])
    paint(jf, ((3, 1), (4, 1)), tie[2])                  # the tie's knot, then the blade down the shirt
    paint(jf, ((3, 2), (4, 2), (4, 3), (4, 4)), tie[1])
    paint(jf, ((3, 3), (3, 4)), tie[0])
    jf.put(4, 5, tie[0])
    paint(jf, ((2, 0), (5, 0)), coat[4])                 # the lapels: lit edges folding out from the neck
    paint(jf, ((2, 1), (2, 2), (2, 3), (2, 4), (2, 5)), coat[3])
    paint(jf, ((5, 1), (5, 2), (5, 3), (5, 4), (5, 5)), coat[2])
    paint(jf, ((1, 1), (6, 1)), coat[2])
    paint(jf, ((2, 6), (5, 6)), coat[1])                 # where the lapels close
    paint(jf, ((3, y) for y in range(6, 18)), coat[1])   # the coat's closing seam, buttoned
    for y in (7, 9, 14):
        jf.put(4, y, coat[0])
    paint(jf, ((0, 12), (1, 12), (2, 12)), coat[1])      # the pocket on the right (under the crossed arms, where it
    paint(jf, ((x, y) for x in range(3) for y in (13, 14)), coat[2])  # shows), and a red and a blue pen in it
    paint(jf, ((x, 15) for x in range(3)), coat[1])
    jf.put(0, 11, "#cf4a3f")
    jf.put(0, 12, "#8e1f24")
    jf.put(2, 11, "#56769f")
    jf.put(2, 12, "#2c4468")
    paint(jf, ((x, 17) for x in range(jf.w)), coat[1])   # the hem's shadow
    for side in ("west", "east"):                        # side pockets' seams
        f = t.face("jacket", side)
        paint(f, ((x, 12) for x in range(1, 4)), coat[1])
        paint(f, ((x, 17) for x in range(f.w)), coat[1])
    jb = t.face("jacket", "back")                        # the back vent from the hem to the hips
    paint(jb, ((3, y) for y in range(12, 18)), coat[0])
    paint(jb, ((4, y) for y in range(12, 18)), coat[2])
    paint(jb, ((x, 17) for x in range(jb.w)), coat[1])  # the hem's shadow
    path = ASSETS / "textures" / "entity" / "villager" / "legend" / "pokemon_professor.png"
    t.save(path)
    return [path]


def pokemon_ranger():
    """The Pokemon Ranger (29.22): a red field vest, open over a navy shirt, with black side panels and trim, a pocket
    flap on each side of the chest and a yellow stripe across the back; khaki shirt sleeves rolled to the forearm, and on
    the right wrist the capture styler: a grey casing with a cyan screen and a lit red button, worn over the crossed arms
    where it shows from the front. A gold circlet at the brow marks the Legend; a brown satchel strap across the back."""
    t = vg.VillagerTexture()
    red = Ramp(["#6e1a1a", "#9a2624", "#bf3a2e", "#d85a40"], name="ranger red")
    black = Ramp(["#1e1e24", "#2e2e36", "#40404a"], name="trim")
    navy = Ramp(["#1e2a48", "#2c3c62", "#40547e"], name="navy shirt")
    khaki = Ramp(["#7a6a46", "#9c8a5e", "#baa878", "#d0c090"], name="khaki")
    yellow = Ramp(["#b88a1a", "#e0b028", "#f2cc4a"], name="stripe")
    styler = Ramp(["#3a3e44", "#5c6268", "#868c92", "#aab0b4"], name="styler")
    cyan = ["#2a8aa8", "#5cc8e0"]
    for side in SIDES:                                   # the Legend's gold circlet at the brow
        f = t.face("head", side)
        for x in range(f.w):
            f.put(x, 2, GOLD[2] if side in ("front", "west") else GOLD[1])
    hf = t.face("head", "front")
    hf.put(3, 2, GOLD[3])
    hf.put(4, 2, GOLD[3])
    vg.robe(t, navy, length=16, folds=False, sleeves_too=False, body_too=True, noise=0)
    vg.vest(t, red, length=11, open_front=True, noise=0)
    jf = t.face("jacket", "front")
    paint(jf, ((3, y) for y in range(0, 11)), navy[1])   # the shirt in the open front, its collar lit
    paint(jf, ((4, y) for y in range(0, 11)), navy[0])
    paint(jf, ((3, 0), (4, 0)), navy[2])
    paint(jf, ((2, y) for y in range(0, 11)), black[2])  # the vest's black front edges
    paint(jf, ((5, y) for y in range(0, 11)), black[1])
    paint(jf, ((0, 3), (1, 3)), red[0])                  # a chest pocket flap each side, buttoned
    paint(jf, ((6, 3), (7, 3)), red[0])
    jf.put(0, 4, red[3])
    jf.put(7, 4, red[2])
    paint(jf, ((x, 10) for x in range(jf.w) if x not in (3, 4)), black[0])   # the vest's hem
    for side in ("west", "east"):                        # black side panels under the arms
        f = t.face("jacket", side)
        paint(f, ((x, y) for x in (1, 2) for y in range(1, 10)), black[1] if side == "west" else black[0])
        paint(f, ((x, 10) for x in range(f.w)), black[0])
    jb = t.face("jacket", "back")                        # the yellow stripe across the back, the hem, the strap
    paint(jb, ((x, 5) for x in range(jb.w)), yellow[1])
    paint(jb, ((x, 4) for x in range(jb.w)), yellow[2])
    paint(jb, ((x, 10) for x in range(jb.w)), black[0])
    strap_diagonal(jb, 0, 0, 7, 9, LEATHER[1], width=1)
    vg.sleeves(t, khaki, cuff=khaki[3], noise=0)         # rolled khaki sleeves
    mid = t.face("arms_middle", "front")                 # the capture styler on the right wrist, seen over the crossed arms
    for y in range(mid.h):
        for x in range(0, 3):
            mid.put(x, y, styler[2] if y == 0 else styler[1] if y < mid.h - 1 else styler[0])
    mid.put(0, 1, cyan[1])                               # its screen
    mid.put(1, 1, cyan[1])
    mid.put(0, 2, cyan[0])
    mid.put(1, 2, cyan[0])
    mid.put(2, 2, "#e04a3a")                             # the lit button
    for side in ("front", "west", "east", "back"):       # the styler's band round the right arm at the wrist
        f = t.face("arm", side)
        paint(f, ((x, f.h - 2) for x in range(f.w)), styler[1] if side in ("front", "west") else styler[0])
    path = ASSETS / "textures" / "entity" / "villager" / "legend" / "pokemon_ranger.png"
    t.save(path)
    return [path]


def vintner():
    """A straw hat with a green vine band (two grape leaves and a bunch of dark grapes over the brim), a linen shirt
    with the sleeves rolled to the elbow, and a wine-stained canvas apron: purple splashes and a drip down the front,
    a stained hem, and a corkscrew in the pocket (ROADMAP 34.9)."""
    t = vg.VillagerTexture()
    straw = Ramp(["#947a24", "#ac8c2f", "#c1a137", "#d1bc45", "#dfcc6f"], name="straw")
    vine = ["#2f5a22", "#4a7d32", "#6ea34a"]             # dark, mid, lit
    grape = ["#3a1240", "#5e2366", "#86408e"]
    vg.hat(t, straw, style="brim", band=vine[1], noise=0.1)
    crown_top(t, straw)
    front = t.face("hat", "front")                      # leaves on the vine band, and a bunch of grapes by them
    front.put(2, 3, vine[2])
    front.put(3, 2, vine[2])
    front.put(3, 3, vine[0])
    front.put(5, 2, grape[2])
    front.put(6, 2, grape[1])
    front.put(5, 3, grape[1])
    front.put(6, 3, grape[0])
    for side in ("west", "east", "back"):               # the band's leaves going round the crown
        f = t.face("hat", side)
        f.put(2, 3, vine[2])
        f.put(5, 3, vine[0])

    linen = vg.cloth(LINEN)
    canvas = vg.cloth("#c4a676", spread=0.2)
    vg.vest(t, linen, length=10, open_front=False, noise=0)
    vg.apron(t, canvas, top=2, bottom=17, left=1, right=6, ties=True)
    apron_back(t, canvas)
    vg.sleeves(t, linen, noise=0)
    roll = vg.cloth("#c9c2ae")
    for side in ("front", "west", "east", "back"):      # the sleeves rolled up: a thick fold band, the forearm below bare
        f = t.face("arm", side)
        paint(f, ((x, 3) for x in range(f.w)), roll[3])
        paint(f, ((x, 4) for x in range(f.w)), roll[1])
        for y in range(5, f.h):
            paint(f, ((x, y) for x in range(f.w)), "#b48a6a" if y < f.h - 1 else "#9c7458")
    mid = t.face("arms_middle", "front")                 # the bare forearms crossing
    for y in range(mid.h):
        for x in range(mid.w):
            mid.put(x, y, "#9c7458" if x == 4 or y == mid.h - 1 else "#b48a6a")

    wine = ["#4a1236", "#6e1f4e", "#8e3266"]
    j = t.face("jacket", "front")                       # wine splashes and a drip down the apron
    for x, y, c in ((1, 4, 2), (2, 5, 1), (5, 6, 2), (6, 7, 0), (5, 7, 1), (2, 15, 1), (3, 16, 0), (6, 16, 1),
                    (4, 15, 2), (5, 16, 0)):
        j.put(x, y, wine[c])
    for y in range(8, 10):
        j.put(6, y, wine[1])
    f = pocket(t, canvas, top=11, bottom=14)            # a corkscrew in the pocket: a wooden handle and the steel worm
    f.put(1, 10, "#8a5a2e")
    f.put(2, 10, "#6a4220")
    f.put(1, 9, "#b07a40")
    f.put(2, 9, "#8a5a2e")
    f.put(2, 11, STEEL[2])
    f.put(1, 12, STEEL[1])
    f.put(2, 13, STEEL[0])
    return t.save_profession(ASSETS, "vintner", hat="full")


def jeweller():
    """A jeweller's loupe held in one eye (the villager's left: a dark barrel with a pale lens, the `glasses` helper
    with the other eye's frame taken off again), a dark plum velvet waistcoat with gold buttons and a gold watch chain
    to its pocket, over cream shirt sleeves with velvet cuffs and gold cufflinks, and a cut amethyst held in the
    fingers (ROADMAP 34.12). No headwear: the biome's hat stays."""
    t = vg.VillagerTexture()
    velvet = vg.cloth("#43264f", spread=0.24)
    shirt = vg.cloth(LINEN)
    barrel = "#2b2630"
    lens = "#bfe4f2"
    amethyst = ["#5a3a8a", "#8a5ac0", "#c69ae8"]         # dark, mid, lit
    vg.glasses(t, frame=barrel, lens=lens)
    hf = t.face("hat", "front")
    for x, y in ((0, 6), (3, 6), (1, 5), (2, 5), (1, 6), (2, 6), (1, 7), (2, 7)):   # the right eye stays bare
        hf.put(x, y, (0, 0, 0, 0))
    hf.put(5, 5, BRASS[3])                               # the barrel's brass rim, lit from the top-left
    hf.put(6, 5, BRASS[1])

    vg.vest(t, velvet, length=12, open_front=False, noise=0.03)
    j = t.face("jacket", "front")
    for x, y in ((2, 0), (3, 0), (4, 0), (5, 0), (3, 1), (4, 1)):   # the shirt showing in the waistcoat's V neck
        j.put(x, y, shirt[4] if y == 0 else shirt[2])
    for y in range(2, 12):                               # the buttoned front edge, a shade darker
        j.put(4, y, velvet[0])
    for y in (2, 4, 6, 8):                               # four gold buttons
        j.put(3, y, GOLD[3])
    j.put(3, 9, GOLD[0])                                 # the last button's shadow, above the level badge's row
    for x, y, c in ((2, 7, GOLD[2]), (1, 8, GOLD[1]), (0, 8, GOLD[2])):   # the watch chain, from a button to the pocket
        j.put(x, y, c)
    for x in (0, 1):                                     # the watch pocket's welt
        j.put(x, 9, velvet[0])
    back = t.face("jacket", "back")                      # the waistcoat's cinch strap and its gold buckle
    for x in range(1, 7):
        back.put(x, 8, velvet[0])
    back.put(3, 8, GOLD[3])
    back.put(4, 8, GOLD[1])

    vg.sleeves(t, shirt, noise=0)
    for side in ("front", "west", "east", "back"):       # a velvet cuff at each wrist, a gold link on the front
        f = t.face("arm", side)
        paint(f, ((x, f.h - 2) for x in range(f.w)), velvet[1])
    af = t.face("arm", "front")
    af.put(1, af.h - 2, GOLD[2])
    mid = t.face("arms_middle", "front")                 # a cut amethyst held up in the fingers
    mid.put(1, 1, amethyst[2])
    mid.put(2, 1, amethyst[1])
    mid.put(1, 2, amethyst[1])
    mid.put(2, 2, amethyst[0])
    written = t.save_profession(ASSETS, "jeweller", hat=None)
    z = t.copy()                                         # the zombie's eye behind the lens is red, not green
    u0, v0 = vg.UV["hat"]["front"][:2]
    for x in (5, 6):
        z.a[v0 + 6, u0 + x] = rgba(mix(rgba(ZOMBIE_EYES[x]), rgba(lens), 0.45))
    for p in written:
        if "zombie_villager" in str(p) and str(p).endswith(".png"):
            z.save(p)
    return written


def tailor():
    """A neat buttoned waistcoat of slate-blue wool with a pale satin back and a cinch strap, over white shirt sleeves;
    a yellow tape measure round the neck, its two ends hanging down the front at uneven lengths with inch marks; and a
    red pincushion strapped to the wrist with two pins in it (ROADMAP 34.10). No headwear: the biome's hat stays."""
    t = vg.VillagerTexture()
    wool = vg.cloth("#566579", spread=0.22)
    satin = vg.cloth("#a3adb8", spread=0.18)
    shirt = vg.cloth(LINEN)
    tape = ["#b8922a", "#e8c83a", "#f6de6a"]             # the inch marks and the shaded side, the tape, lit
    vg.vest(t, wool, length=12, open_front=False, noise=0.04)
    back = t.face("jacket", "back")                      # the waistcoat's satin back: lit at the shoulders, a dark hem
    for y in range(0, 12):
        for x in range(1, 7):
            back.put(x, y, satin[4] if y == 0 else satin[1] if y == 11 else satin[2] if x in (1, 6) else satin[3])
    for x in range(1, 7):                                # the cinch strap across the small of the back, and its buckle
        back.put(x, 8, wool[1])
    back.put(3, 8, BRASS[3])
    back.put(4, 8, BRASS[1])

    j = t.face("jacket", "front")
    for x, y in ((2, 0), (3, 0), (4, 0), (5, 0), (3, 1), (4, 1)):   # the shirt showing in the waistcoat's V neck
        j.put(x, y, shirt[4] if y == 0 else shirt[2])
    for y in range(2, 12):                               # the buttoned front edge, a shade darker, and its brass buttons
        j.put(4, y, wool[1])
    for y in (2, 5, 8):
        j.put(3, y, BRASS[3])
    j.put(3, 9, BRASS[1])                                # the last button's shadow, above the level badge's row
    for x in (0, 1, 2):                                  # a welt pocket on the villager's right, a thimble's glint in it
        j.put(x, 10, wool[0])
    j.put(1, 10, STEEL[3])

    for side in ("west", "back", "east"):                # the tape measure round the back of the neck
        f = t.face("jacket", side)
        f.fill(tape[1], rows=[0])
        for x in range(2, f.w, 3):
            f.put(x, 0, tape[0])
    for x, end in ((1, 13), (6, 9)):                     # and its two ends down the front (the badge sits under the short one)
        for y in range(0, end + 1):
            j.put(x, y, tape[2] if y == 0 else tape[0] if y % 4 == 3 else tape[1])
        j.put(x, end, BRASS[1])                          # the brass tip

    vg.sleeves(t, shirt, noise=0)
    for side in ("front", "west", "east", "back"):       # a buttoned cuff at each wrist
        f = t.face("arm", side)
        paint(f, ((x, f.h - 2) for x in range(f.w)), shirt[1])
    mid = t.face("arms_middle", "front")                 # the pincushion on one wrist: a red cushion on its strap, two pins
    cushion = ["#8f1a1a", "#c62828", "#e25a4a"]
    mid.put(1, 1, cushion[2])
    mid.put(2, 1, cushion[1])
    mid.put(1, 2, cushion[1])
    mid.put(2, 2, cushion[0])
    mid.put(1, 0, STEEL[3])                              # a steel pin and a gold-headed one
    mid.put(2, 0, GOLD[3])
    for x in (0, 1, 2, 3):                               # the strap round the wrist under it
        mid.put(x, 3, LEATHER[1])
    return t.save_profession(ASSETS, "tailor", hat=None)


def printer():
    """A long printer's apron of grey canvas, blotched with press-black ink and with a rag tucked in its pocket, over
    white shirt sleeves held up by dark sleeve garters with a brass clip; and a green eyeshade: a leather band round
    the brow with a green visor out over the eyes (ROADMAP 34.11). The biome's own hat still shows above the band."""
    t = vg.VillagerTexture()
    canvas = vg.cloth("#9a968a", spread=0.2)
    shirt = vg.cloth(LINEN)
    green = vg.cloth("#2f8a4a", spread=0.3)
    ink = ["#1c1d26", "#33343f", "#55566a"]              # press-black, its edge, a thinned smear
    garter = ["#2a2b36", "#454758"]

    vg.hat(t, LEATHER, style="band", noise=0)            # the eyeshade's band
    vg._brim(t, green, full=False, visor=3)              # and its green visor, out over the eyes
    for side in ("front", "west", "east"):               # the visor's green binding where it meets the band
        f = t.face("hat", side)
        for x in range(f.w):
            f.put(x, 5, green[1])

    vg.sleeves(t, shirt, noise=0)
    for side in ("front", "west", "east", "back"):       # sleeve garters round the upper arms
        f = t.face("arm", side)
        paint(f, ((x, 3) for x in range(f.w)), garter[0])
        paint(f, ((x, 4) for x in range(f.w)), garter[1])
    for side in ("west", "east"):                        # each with a brass clip on the outside
        t.face("arm", side).put(1, 3, BRASS[3])
    for side in ("front", "west", "east", "back"):       # inky cuffs: the work gets on everything
        f = t.face("arm", side)
        paint(f, ((x, f.h - 1) for x in range(f.w)), ink[2])

    vg.apron(t, canvas, top=1, bottom=17, ties=True, bib=True, pocket=canvas[1:4])
    j = t.face("jacket", "front")
    paint(j, ((5, 11), (6, 11), (5, 12), (6, 12), (6, 13)), ink[0])     # a big blot on the skirt, below the crossed arms
    paint(j, ((5, 10), (5, 13), (6, 14)), ink[1])                        # its soaked edge, running down
    paint(j, ((6, 16),), ink[1])                                         # a drip at the hem
    paint(j, ((1, 10), (2, 16)), ink[2])                                 # a thumb print by the pocket, a smear at the hem
    paint(j, ((2, 12), (3, 12)), shirt[3])                               # the wiping rag in the pocket
    j.put(3, 13, ink[1])                                                 # and the ink on it
    mid = t.face("arms_middle", "front")                 # inky fingers where the arms cross
    mid.put(1, 2, ink[1])
    mid.put(2, 3, ink[2])
    return t.save_profession(ASSETS, "printer", hat="partial")


DRAW = [orchard_keeper, berry_breeder, camp_cook, habitat_keeper, daycare_keeper, gem_grower, vintner, tailor, printer,pokemon_trader, porter, postman, rancher, scholar, shopkeeper, sifter, teacher, tinkerer,
        trainer, trainer_leader, tutor, undertaker, steward, legend, legend_placeholder, master_architect, pathfinder, old_sage, seer, golem_smith,
        merchant_prince, grand_chef, bard_laureate, beastmaster, founder, pokemon_professor, pokemon_ranger]
DRAW += [jeweller]   # 34.12

if __name__ == "__main__":
    run(DRAW)
