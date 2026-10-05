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


DRAW = [orchard_keeper, berry_breeder, camp_cook, habitat_keeper, daycare_keeper, gem_grower, pokemon_trader, porter, postman, rancher, scholar, shopkeeper, sifter, teacher, tinkerer,
        trainer, trainer_leader, tutor, undertaker, steward, legend, legend_placeholder, master_architect, pathfinder, old_sage]

if __name__ == "__main__":
    run(DRAW)
