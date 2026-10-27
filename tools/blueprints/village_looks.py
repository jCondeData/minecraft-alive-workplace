"""
The looks of our village houses (ROADMAP 26.11): every job's house has its own shape, drawn once and rendered through
the five village styles' materials. Before, all of them were one timber house per style with a different room inside;
the owner: "make new completely different exteriors ... nothing should look identical".

    builders_workshop    a saltbox studio (a tall short slope, a long low one) and a fenced yard with a timber crane
    carpenters_workshop  a long saw shed: a closed workshop and an open shed of trestles under one roof, log piles
    kitchen              a hipped cookhouse with a domed brick bread oven bulging out of its side and a tall flue
    post_office          two storeys, the upper one jettied over the street, a dovecote on the ridge
    guard_house          a squat stone blockhouse, arrow slits, a corbelled battlement and a watch hut on the roof
    clinic               a pavilion with its gable to the street and a stone cross window, a long ward across the back
    ferry_house          a boathouse under a gambrel roof, a signal mast with a lamp and an upturned boat on trestles
    trainers_house       a long low dojo under a hipped roof, a veranda on posts along its front, banners
    leaders_hall         a stone hall behind a portico of columns and a pediment, on a stone stylobate
    ball_workshop        a round apricorn-drying tower (open slatted loft, copper spire) beside a low workshop
    trade_hall           a market hall: a timber upper storey over the shop and an open stone arcade, a cupola
    school               a hall with a slim clock tower over the door, tall windows
    storehouse           a tall granary, stone below and boards above, a hoist beam with a bale out of the gable
    orchard_house        a low hipped cottage beside a little fenced orchard of blossoming fruit trees
    fossil_lab           a stone lab under a stepped glass skylight, a skeleton of bone on show beside it
    flower_shop          a shop with a glasshouse wing full of flower beds, flower beds along the front
    ranch_house          a log cabin with its gable to the street and a stable wing with stall gates and a hay loft
    schoolhouse          a one-room clapboard schoolhouse under a steep roof with a lamp belfry on its front gable
    inn_room             a two-storey inn, a balcony gallery along the front and a chimney up the gable
    mortuary             a narrow chapel with a steep dark roof, buttresses, lancet windows and a small graveyard
    tinkers_shop         a workshop with a glazed monitor roof along the ridge and a tall brick chimney stack
    sifting_shed         a long lean-to of slabs over a workroom and an open shed with gravel heaps and a sieve
    compost_yard         a hut under a mossy turf roof with azaleas on it, three compost bays beside it
    winery               a stone cellar house with casks before it and a pergola hung with glow-berry grapes
    tailors_shop         a shop with two display windows, a striped awning, banners and a mansard roof
    print_shop           a tall narrow house with crow-stepped gables and a lamp on an iron arm
    jewellers_workshop   a stone strongroom with barred windows, a dome and an amethyst finial, gem pedestals
    crafters_workshop    a board workshop, its gable to the street, a hatch counter and a lean-to parts store of boards and logs
    pokemon_center       a white house under a red hipped roof, a glazed front under a canopy
    camp_kitchen         an A-frame cook hut down to the ground, a fire pit with a spit and log seats beside it
    berry_nursery        a cottage under a stepped thatch of hay, berry beds watered by a channel
    daycare              a cottage with a cross gable and a round window, a playground with a swing and a sandpit
    gem_grotto           a mound of stone and moss with amethyst growing out of it and a geode-banded doorway

What every one keeps (VillageGameTests check it): the street jigsaw at (4, 1, 0) facing north and the front row (z 0) left
as the ground; the door at (4, 1, 2) with a step before it; the room x 2-6, z 3-7 the job's fit-out is drawn in (village.py)
with the bed at (6, 1, 6-7) and the villager at (5, 1, 5) or the spawn jigsaw at (4, 0, 5); the west wall at x 1; exactly
one job block (no barrel, composter, cauldron, crafting table or other workstation for looks); no trapdoor in the front
walk row (z 1, y 0-1: B69); a ladder to every furnished upper floor. The luxury houses (winery, tailor's, print shop,
jeweller's) stay at most 10 wide: their test places two side by side 11 apart.
"""
from kit import *


class Palette:
    def __init__(self, **k):
        self.__dict__.update(k)


# The village styles' materials (the families the old style table used: oak, cobble and calcite on the plains; sandstone
# and jungle wood in the desert; acacia and white terracotta on the savanna; stone, calcite and spruce in the snow; spruce
# and mossy cobble in the taiga).
PALETTES = {
    "plains": Palette(
        base=STONE_MIX, ashlar=Mix((7, "stone_bricks"), (2, "cobblestone"), (1, "cracked_stone_bricks"), seed=11),
        sfam=STONE_BRICK, frame="oak_log", strip="stripped_oak_log", log="oak_log",
        wall=Mix((7, "calcite"), (3, "white_concrete"), seed=9), boards="oak_planks", wood=OAK, roof=SPRUCE, roof2=DARK_OAK,
        door="oak_door", trap="oak_trapdoor", fence="oak_fence", gate="oak_fence_gate", bed="red", floor="oak_planks",
        flat=False, parapet="cobblestone_wall", leaves="oak_leaves", cloth=("red", "white"), soil="grass_block",
        yard=Mix((5, "coarse_dirt"), (3, "gravel"), (2, "dirt"), seed=301)),
    "desert": Palette(
        base="smooth_sandstone", ashlar="cut_sandstone", sfam=SMOOTH_SANDSTONE, frame="cut_sandstone",
        strip="stripped_jungle_log", log="stripped_jungle_log", wall="sandstone", boards="jungle_planks", wood=JUNGLE,
        roof=SMOOTH_SANDSTONE, roof2=SMOOTH_SANDSTONE, door="jungle_door", trap="jungle_trapdoor", fence="jungle_fence",
        gate="jungle_fence_gate", bed="yellow", floor="smooth_sandstone", flat=True, parapet="sandstone_wall",
        leaves="jungle_leaves", cloth=("orange", "white"), soil="grass_block",
        yard=Mix((5, "sand"), (3, "smooth_sandstone"), (2, "coarse_dirt"), seed=302)),
    "savanna": Palette(
        base=STONE_MIX, ashlar=Mix((5, "stone_bricks"), (3, "cobblestone"), (2, "andesite"), seed=12), sfam=COBBLE,
        frame="acacia_log", strip="stripped_acacia_log", log="acacia_log", wall="white_terracotta", boards="acacia_planks",
        wood=ACACIA, roof=ACACIA, roof2=DARK_OAK, door="acacia_door", trap="acacia_trapdoor", fence="acacia_fence",
        gate="acacia_fence_gate", bed="orange", floor="acacia_planks", flat=False, parapet="cobblestone_wall",
        leaves="acacia_leaves", cloth=("orange", "yellow"), soil="grass_block",
        yard=Mix((5, "coarse_dirt"), (3, "red_sand"), (2, "dirt"), seed=303)),
    "snowy": Palette(
        base=Mix((6, "stone_bricks"), (3, "cobblestone"), (2, "diorite"), seed=7),
        ashlar=Mix((7, "stone_bricks"), (2, "diorite"), (1, "cracked_stone_bricks"), seed=13), sfam=STONE_BRICK,
        frame="stripped_spruce_log", strip="stripped_spruce_log", log="spruce_log", wall="calcite", boards="spruce_planks",
        wood=SPRUCE, roof=DARK_OAK, roof2=SPRUCE, door="spruce_door", trap="spruce_trapdoor", fence="spruce_fence",
        gate="spruce_fence_gate", bed="light_blue", floor="spruce_planks", flat=False, parapet="stone_brick_wall",
        leaves="spruce_leaves", cloth=("light_blue", "white"), soil="grass_block",
        yard=Mix((5, "gravel"), (3, "coarse_dirt"), (2, "snow_block"), seed=304)),
    "taiga": Palette(
        base=Mix((6, "cobblestone"), (2, "mossy_cobblestone"), (2, "stone"), seed=8),
        ashlar=Mix((6, "cobblestone"), (2, "mossy_cobblestone"), (2, "stone_bricks"), seed=14), sfam=COBBLE,
        frame="spruce_log", strip="stripped_spruce_log", log="spruce_log", wall="spruce_planks",
        boards=Mix((6, "spruce_planks"), (1, "stripped_spruce_wood"), seed=15), wood=SPRUCE, roof=DARK_OAK, roof2=COBBLE,
        door="spruce_door", trap="spruce_trapdoor", fence="spruce_fence", gate="spruce_fence_gate", bed="brown",
        floor="spruce_planks", flat=False, parapet="cobblestone_wall", leaves="spruce_leaves", cloth=("brown", "green"),
        soil="podzol", yard=Mix((5, "coarse_dirt"), (3, "podzol"), (2, "gravel"), seed=305)),
}


# --- helpers ---------------------------------------------------------------------------------------------------
def ax(name, axis="y"):
    if isinstance(name, Mix):
        return {}
    return {"axis": axis} if ("_log" in name or name.endswith("_wood") or name in ("bone_block", "hay_block")) else {}


def post(b, P, x, z, y0, y1, name=None):
    name = name or P.frame
    for y in range(y0, y1 + 1):
        b.set(x, y, z, resolve(name, x, y, z), **ax(name))


def plate(b, P, x0, z0, x1, z1, y, name=None):
    """A top plate (or floor beam) round the walls: frame logs laid along them, or a course of the frame stone."""
    name = name or P.frame
    if "_log" in name:
        beam_ring(b, x0, z0, x1, z1, y, name)
    else:
        b.ring(x0, z0, x1, z1, y, name)


def body(b, P, x0, z0, x1, z1, top, wall=None, posts_at=(), base=None, floor=None, frame=True, plate_=True):
    """Walls round (x0, z0)-(x1, z1) from y 1 to {@code top} on a plinth with a floor, frame posts at the corners (and
    {@code posts_at}), a top plate at top + 1. Returns the plate's y (where the roof's eaves go)."""
    plinth(b, x0, z0, x1, z1, base or P.base, floor=floor or P.floor)
    walls(b, x0, z0, x1, z1, 1, top, wall or P.wall)
    if frame:
        for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)) + tuple(posts_at):
            post(b, P, x, z, 1, top)
    if plate_:
        plate(b, P, x0, z0, x1, z1, top + 1)
    return top + 1


def flat_top(b, P, x0, z0, x1, z1, y, parapet=True, beams=True):
    """A flat roof over the walls (x0, z0)-(x1, z1) at y, a parapet round it and, under it, the roof beams' ends."""
    box(b, x0, y, z0, x1, y, z1, P.roof.full)
    if parapet:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    wall_block(b, x, y + 1, z, P.parapet)
    if beams and y >= 3:
        for z in range(z0 + 1, z1, 2):
            for x in (x0 - 1, x1 + 1):
                if 0 <= x < b.w and b.get(x, y - 1, z) is None:
                    log(b, x, y - 1, z, P.strip, axis="x")
        for x in range(x0 + 1, x1, 2):
            if z1 + 1 < b.d and b.get(x, y - 1, z1 + 1) is None:
                log(b, x, y - 1, z1 + 1, P.strip, axis="z")
    return y + 1


def roof(b, P, kind, x0, z0, x1, z1, y, axis="z", fam=None, gable=None, flat_ok=True, steep=False, trim=True,
         over=1):
    """A roof over the walls (x0, z0)-(x1, z1), its eaves at y, {@code over} past the walls: a gable (ridge along
    {@code axis}) or a hip. In a flat-roofed style (desert) a flat roof with a parapet instead, unless the roof's shape is
    the house's signature ({@code flat_ok=False})."""
    fam = fam or P.roof
    if P.flat and flat_ok:
        return flat_top(b, P, x0, z0, x1, z1, y)
    ox0, ox1, oz0, oz1 = x0 - over, x1 + over, z0 - over, z1 + over
    if kind == "hip":
        return hip_roof(b, ox0, ox1, oz0, oz1, y, fam, steep=steep)
    return gable_roof(b, ox0, ox1, oz0, oz1, y, fam, axis=axis, gable=gable or P.wall,
                      gable_at=(x0, x1) if axis == "x" else (z0, z1), steep=steep,
                      eave_trim=P.wood if trim and not steep else None)


def win(b, P, x, y, z, out, h=1, w=1, shut=True, sill=True, glass="glass_pane"):
    window(b, x, y, z, out, width=w, height=h, glass=glass, shutters=P.trap if shut else None,
           sill=P.wood if sill else None)


def entry(b, P, hood=True, step=None, lamp=True):
    """The front door at (4, 1, 2), a step before it, a hood over it with a lantern under."""
    door(b, 4, 1, 2, P.door, "south")
    stairs(b, 4, 0, 1, step or P.sfam, "south")
    if hood:
        stairs(b, 3, 3, 1, P.wood, "west", top=True)
        stairs(b, 5, 3, 1, P.wood, "east", top=True)
        slab(b, 4, 4, 1, P.wood)
        if lamp:
            lantern(b, 4, 3, 1, hanging=True)


def tie(b, P, y, x0=2, x1=6, z=5, lamps=(4,)):
    """A tie beam across the room at y (wall to wall), lanterns hanging under it."""
    for x in range(x0, x1 + 1):
        if b.get(x, y, z) is None:
            log(b, x, y, z, P.strip, axis="x")
    for x in lamps:
        lantern(b, x, y - 1, z, hanging=True)


def ladder_up(b, x, y0, y1, z, facing="west"):
    for y in range(y0, y1 + 1):
        b.set(x, y, z, "ladder", facing=facing, waterlogged=False)


def leaves(b, x, y, z, name):
    if 0 <= x < b.w and 0 <= z < b.d and b.get(x, y, z) is None:
        b.set(x, y, z, name, distance=1, persistent=True, waterlogged=False)


def tree(b, x, z, h, trunk, crown):
    """A small tree: a trunk {@code h} high and a crown of leaves round its top (crown: one or two leaf ids)."""
    for y in range(1, h + 1):
        log(b, x, y, z, trunk)
    names = crown if isinstance(crown, tuple) else (crown,)
    for dy in (0, 1):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if dx == dz == 0 and dy == 0:
                    continue
                if dy == 1 and abs(dx) + abs(dz) == 2:
                    continue
                leaves(b, x + dx, h - 1 + dy + 1 if dy else h, z + dz, names[(x + dx * 3 + dz * 5 + dy) % len(names)])
    leaves(b, x, h + 1, z, names[0])
    leaves(b, x, h + 2, z, names[-1])


def gambrel(b, fam, x0, x1, z0, z1, y, gable, gable_at):
    """A gambrel roof (ridge along z) over x0..x1 (overhang included, odd span), eaves at y: a steep lower slope (two up
    per step), a shallow upper one; the gables at {@code gable_at} filled with {@code gable}. Returns the ridge's y."""
    half = (x1 - x0) // 2
    heights = [y]
    for i in range(1, half + 1):
        heights.append(heights[-1] + (2 if i == 1 else 1))
    for z in range(z0, z1 + 1):
        for i in range(half + 1):
            yy = heights[i]
            lo = heights[i - 1] + 1 if i else yy
            for x, face in ((x0 + i, "east"), (x1 - i, "west")):
                for y2 in range(lo, yy):
                    b.set(x, y2, z, fam.full)
                if i == half:
                    slab(b, x, yy, z, fam)
                else:
                    stairs(b, x, yy, z, fam, face)
    for z in gable_at:
        for i in range(1, half + 1):
            for x in (x0 + i, x1 - i):
                for y2 in range(y, heights[i - 1] + 1):
                    if b.get(x, y2, z) is None:
                        b.set(x, y2, z, resolve(gable, x, y2, z))
    return heights[-1]


def slab_lean(b, fam, x0, x1, z_hi, z_lo, y_lo):
    """A lean-to over x0..x1 rising half a block a row from z_lo (eaves at y_lo) to z_hi: a bottom slab, then a stair
    rising towards z_hi, then a slab a block up... Returns {z: (y, False)}: the walls under a row go up to y - 1."""
    step_ = 1 if z_hi > z_lo else -1
    face = "south" if step_ > 0 else "north"
    out = {}
    for i, z in enumerate(range(z_lo, z_hi + step_, step_)):
        y = y_lo + i // 2
        for x in range(x0, x1 + 1):
            if i % 2:
                stairs(b, x, y, z, fam, face)
            else:
                slab(b, x, y, z, fam)
        out[z] = (y, False)
    return out


# --- the houses ----------------------------------------------------------------------------------------------------
def builders_workshop(b, P):
    """Saltbox studio (walls x 1-7, z 2-8), the crane yard x 8-12."""
    if P.flat:
        # a stepped flat roof: the tall drawing loft to the west, the low workroom east
        plinth(b, 1, 2, 7, 8, P.base, floor=P.floor)
        walls(b, 1, 2, 3, 8, 1, 7, P.wall)
        walls(b, 3, 2, 7, 8, 1, 4, P.wall)
        for x in range(4, 7):
            for z in range(3, 8):
                b.blocks.pop((x, 5, z), None)
        flat_top(b, P, 1, 2, 3, 8, 8, beams=False)
        flat_top(b, P, 3, 2, 7, 8, 5)
        for z in (3, 4, 5, 6, 7):
            b.set(3, 5, z, P.roof.full)
            b.set(3, 6, z, P.wall)
            b.set(3, 7, z, P.wall)
        for x in (1, 3):
            post(b, P, x, 2, 1, 7)
            post(b, P, x, 8, 1, 7)
        post(b, P, 7, 2, 1, 4)
        post(b, P, 7, 8, 1, 4)
        win(b, P, 2, 5, 2, "north", h=2, shut=False, sill=False)
        win(b, P, 2, 5, 8, "south", h=2, shut=False, sill=False)
        tie(b, P, 5, lamps=(4,))
    else:
        prof = {0: 9, 1: 10, 2: 11, 3: 10, 4: 9, 5: 8, 6: 7, 7: 6, 8: 5}
        plinth(b, 1, 2, 7, 8, P.base, floor=P.floor)
        for x in range(1, 8):
            for z in range(2, 9):
                if x in (1, 7) or z in (2, 8):
                    for y in range(1, prof[x]):
                        b.set(x, y, z, resolve(P.boards, x, y, z))
        for z in range(1, 10):
            for x in range(0, 9):
                if x == 2:
                    slab(b, x, prof[x], z, P.roof)
                    b.set(x, prof[x] - 1, z, P.boards if not isinstance(P.boards, Mix) else P.boards.at(x, 0, z))
                else:
                    stairs(b, x, prof[x], z, P.roof, "east" if x < 2 else "west")
            stairs(b, 8, 4, z, P.roof, "east", top=True)
        for x, z in ((1, 2), (3, 2), (5, 2), (7, 2), (1, 8), (3, 8), (5, 8), (7, 8)):
            post(b, P, x, z, 1, prof[x] - 1)
        plate(b, P, 1, 2, 7, 8, 4)
        for x in (2, 4):
            win(b, P, x, 6, 2, "north", h=2, shut=False, sill=False)
            win(b, P, x, 6, 8, "south", h=2, shut=False, sill=False)
        tie(b, P, 4)
    entry(b, P)
    for x in (2, 6):
        win(b, P, x, 2, 2, "north")
    for z in (4,):
        win(b, P, 1, 2, z, "west")
    win(b, P, 7, 2, 4, "east", shut=False)
    win(b, P, 4, 2, 8, "south")
    door(b, 7, 1, 5, P.door, "west")  # not beside the bed at (6, 6): a villager walked over it into the lintel (B178)
    # the yard
    box(b, 8, 0, 1, 12, 0, 10, P.yard)
    for x in range(8, 13):
        if x != 10:
            fence(b, x, 1, 1, P.fence)
        fence(b, x, 1, 10, P.fence)
    for z in range(2, 10):
        fence(b, 12, 1, z, P.fence)
    b.set(10, 1, 1, P.gate, facing="north", open=False, powered=False, in_wall=False)
    lantern(b, 9, 2, 1)
    lantern(b, 12, 2, 1)
    for x in (10, 11):
        for z in (6, 7, 8):
            b.set(x, 0, z, "stone_bricks")
    for y in range(1, 12):
        b.set(11, y, 7, P.log, **ax(P.log))
    for x in range(8, 13):
        if x != 11:
            log(b, x, 10, 7, P.strip, axis="x")
    stairs(b, 10, 9, 7, P.wood, "west", top=True)
    stairs(b, 11, 1, 6, STONE_BRICK, "south")
    stairs(b, 11, 1, 8, STONE_BRICK, "north")
    stairs(b, 10, 1, 7, STONE_BRICK, "west")
    b.set(12, 9, 7, "cobblestone")
    for y in range(6, 10):
        b.set(9, y, 7, "chain", axis="y", waterlogged=False)
    b.set(9, 5, 7, "stone_bricks")
    for x, z, h, what in ((9, 3, 2, "stone_bricks"), (10, 3, 1, "stone_bricks"), (11, 3, 2, "cobblestone"),
                          (11, 4, 1, "cobblestone")):
        for y in range(1, h + 1):
            b.set(x, y, z, what)
    for x in (8, 9, 10):
        b.set(x, 1, 9, P.wood.full)
        slab(b, x, 2, 9, P.wood)


def carpenters_workshop(b, P):
    """A closed workshop (x 1-7) and an open saw shed (x 8-11) under one long roof, its ridge along the street."""
    body(b, P, 1, 2, 7, 8, 3, wall=P.boards, posts_at=((3, 2), (5, 2), (1, 5), (7, 5), (3, 8), (5, 8)))
    box(b, 8, 0, 3, 11, 0, 7, P.yard)
    for x, z in ((11, 2), (11, 5), (11, 8), (9, 2), (9, 8)):
        b.set(x, 0, z, resolve(P.base, x, 0, z))
        post(b, P, x, z, 1, 3)
    for x in range(8, 12):
        b.set(x, 0, 2, resolve(P.base, x, 0, 2))
        b.set(x, 0, 8, resolve(P.base, x, 0, 8))
    plate(b, P, 1, 2, 11, 8, 4)
    for z in (3, 7):  # knee braces in the open bays
        stairs(b, 11, 3, z, P.wood, "south" if z == 3 else "north", top=True)
    if P.flat:
        flat_top(b, P, 1, 2, 11, 8, 5)
    else:
        gable_roof(b, 0, 12, 1, 9, 4, P.roof2 if P.roof2 is not COBBLE else P.roof, axis="x", gable=P.boards,
                   gable_at=(1, 7), eave_trim=P.wood)
        # the east end an open truss: a collar beam and a king post
        for x in (11,):
            for z in range(3, 8):
                log(b, x, 5, z, P.strip, axis="z")
            post(b, P, x, 5, 6, 7, P.strip)
    entry(b, P)
    for x in (2, 6):
        win(b, P, x, 2, 2, "north")
    for z in (4, 6):
        win(b, P, 1, 2, z, "west")
    win(b, P, 4, 2, 8, "south")
    door(b, 7, 1, 4, P.door, "east")  # not beside the bed at (6, 6): a villager walked over it into the lintel (B178)
    tie(b, P, 4)
    # in the shed: a log on two trestles, a plank bench, boards stood on end
    for z in (4, 6):
        fence(b, 9, 1, z, P.fence)
        fence(b, 10, 1, z, P.fence)
    for z in (4, 5, 6):
        log(b, 9, 2, z, P.log, axis="z")
    stairs(b, 10, 2, 4, P.wood, "north", top=True)
    stairs(b, 10, 2, 6, P.wood, "south", top=True)
    b.set(10, 2, 5, P.wood.slab, type="top", waterlogged=False)
    for z in (3, 7):
        b.set(11, 1, z, P.boards if not isinstance(P.boards, Mix) else P.boards.at(11, 1, z))
    lantern(b, 10, 3, 5, hanging=True)
    log(b, 10, 4, 5, P.strip, axis="z")
    # log piles: their ends to the street before the shed, along the back wall under the eaves
    for x in (8, 9, 10, 11):
        log(b, x, 1, 1, P.log, axis="z")
    for x in (9, 10):
        log(b, x, 2, 1, P.log, axis="z")
    for x in range(1, 12):
        log(b, x, 1, 9, P.log, axis="x")
        if 3 <= x <= 9:
            log(b, x, 2, 9, P.log, axis="x")
        b.set(x, 0, 9, resolve(P.base, x, 0, 9))


def kitchen(b, P):
    """Plastered cookhouse under a hip, a domed brick oven on its east side, herb beds before it."""
    y = body(b, P, 1, 2, 7, 8, 3, posts_at=((1, 5), (7, 5)))
    roof(b, P, "hip", 1, 2, 7, 8, y)
    entry(b, P)
    for x in (2, 6):
        win(b, P, x, 2, 2, "north", sill=False)
    for z in (4, 6):
        win(b, P, 1, 2, z, "west")
    win(b, P, 7, 2, 3, "east", shut=False)
    win(b, P, 4, 2, 8, "south")
    tie(b, P, 4)
    # the oven: a brick dome bulging out of the east wall, its flue up past the eaves
    for x in (8, 9):
        for z in (4, 5, 6):
            b.set(x, 0, z, "bricks")
            b.set(x, 1, z, "bricks")
    for z in (4, 5, 6):
        b.set(8, 2, z, "bricks")
    stairs(b, 9, 2, 4, BRICK, "south")
    stairs(b, 9, 2, 6, BRICK, "north")
    stairs(b, 9, 2, 5, BRICK, "west")
    stairs(b, 8, 3, 4, BRICK, "south")
    stairs(b, 8, 3, 6, BRICK, "north")
    chimney(b, 8, 5, 3, 9 if not P.flat else 7, "bricks")
    for z in (4, 6):
        b.set(9, 1, z, "bricks")
    b.set(9, 1, 5, "iron_bars", north=False, south=False, east=False, west=False, waterlogged=False)
    # herb beds either side of the path
    herbs = ("fern", "allium", "azure_bluet", "lily_of_the_valley", "fern", "oxeye_daisy")
    flower_bed(b, 1, 1, 3, 1, 0, herbs, soil="grass_block")
    flower_bed(b, 5, 1, 7, 1, 0, herbs, soil="grass_block")


def post_office(b, P):
    """A stone ground floor, a timber upper floor jettied over the street, a dovecote on the ridge."""
    plinth(b, 1, 2, 7, 8, P.base, floor=P.floor)
    walls(b, 1, 2, 7, 8, 1, 4, P.ashlar)
    for x, z in ((1, 2), (7, 2), (1, 8), (7, 8)):
        post(b, P, x, z, 1, 4, P.sfam.full)
    # the floor at y 5, jettied out to z 1 at the front on stair brackets
    box(b, 2, 5, 2, 6, 5, 7, P.boards)
    plate(b, P, 1, 1, 7, 8, 5, P.strip if "_log" not in P.frame else P.frame)
    for x in (1, 3, 5, 7):
        stairs(b, x, 4, 1, P.wood, "south", top=True)
    walls(b, 1, 1, 7, 8, 6, 8, P.wall)
    for x, z in ((1, 1), (3, 1), (5, 1), (7, 1), (1, 8), (4, 8), (7, 8), (1, 5), (7, 5)):
        post(b, P, x, z, 6, 8)
    plate(b, P, 1, 1, 7, 8, 9)
    if P.flat:
        top = flat_top(b, P, 1, 1, 7, 8, 9)
        dove_y, dz = top, 5
    else:
        ridge = gable_roof(b, 0, 8, 0, 9, 9, P.roof2 if P.roof2 is not COBBLE else P.roof, axis="z", gable=P.wall,
                           gable_at=(1, 8), eave_trim=P.wood)
        dove_y, dz = ridge + 1, 5
    # the dovecote: a little box of boards with holes and a perch, a cap
    for dzz in (4, 5):
        b.set(4, dove_y, dzz, P.boards if not isinstance(P.boards, Mix) else P.boards.at(4, dove_y, dzz))
        b.set(4, dove_y + 1, dzz, P.boards if not isinstance(P.boards, Mix) else P.boards.at(4, dove_y + 1, dzz))
    b.set(4, dove_y + 1, 4, "air")
    fence(b, 4, dove_y + 1, 3, P.fence)
    for dzz in (3, 4, 5, 6):
        slab(b, 4, dove_y + 2, dzz, P.wood)
    entry(b, P, hood=False)
    lantern(b, 4, 4, 1, hanging=True)
    for x in (2, 6):
        win(b, P, x, 2, 2, "north")
    for z in (4, 6):
        win(b, P, 1, 2, z, "west")
        win(b, P, 7, 2, z, "east", shut=False)
    for x in (2, 4, 6):
        win(b, P, x, 7, 1, "north", shut=x != 4, sill=True)
    for z in (3, 6):
        win(b, P, 1, 7, z, "west", shut=False)
        win(b, P, 7, 7, z, "east", shut=False)
    win(b, P, 4, 2, 8, "south")
    # inside: a ladder to the sorting room upstairs, its lamps
    ladder_up(b, 6, 1, 5, 3)
    lantern(b, 4, 4, 5, hanging=True)
    tie(b, P, 9, x0=2, x1=6, z=5)
    b.set(2, 6, 7, "bookshelf")
    b.set(3, 6, 7, "bookshelf")
    b.set(4, 6, 6, f"{P.cloth[0]}_carpet")
    b.set(4, 6, 5, f"{P.cloth[0]}_carpet")


def guard_house(b, P):
    """A squat stone blockhouse with arrow slits, a corbelled battlement and a timber watch hut on the roof."""
    plinth(b, 1, 2, 7, 8, P.base, floor=P.floor)
    walls(b, 1, 2, 7, 8, 1, 5, P.ashlar)
    for x, z in ((1, 2), (7, 2), (1, 8), (7, 8)):
        post(b, P, x, z, 1, 5, P.base if isinstance(P.base, Mix) else P.sfam.full)
    # the corbels and the battlement walk
    for x in range(0, 9):
        stairs(b, x, 5, 1, P.sfam, "south", top=True)
        stairs(b, x, 5, 9, P.sfam, "north", top=True)
    for z in range(2, 9):
        stairs(b, 0, 5, z, P.sfam, "east", top=True)
        stairs(b, 8, 5, z, P.sfam, "west", top=True)
    box(b, 0, 6, 1, 8, 6, 9, P.ashlar)
    for x in range(0, 9):
        for z in range(1, 10):
            if x in (0, 8) or z in (1, 9):
                if (x + z) % 2 == 0:
                    b.set(x, 7, z, resolve(P.ashlar, x, 7, z))
                else:
                    wall_block(b, x, 7, z, P.sfam.wall or P.parapet)
    # the watch hut on the roof, at the back
    for x, z in ((3, 6), (6, 6), (3, 8), (6, 8)):
        post(b, P, x, z, 7, 9, P.log if "_log" in P.log else P.frame)
    for x in (4, 5):
        b.set(x, 7, 8, resolve(P.boards, x, 7, 8))
        fence(b, x, 7, 6, P.fence)
    for z in (7,):
        b.set(3, 7, z, resolve(P.boards, 3, 7, z))
        b.set(6, 7, z, resolve(P.boards, 6, 7, z))
    for x in range(2, 8):
        stairs(b, x, 10, 5, P.wood, "south")
        stairs(b, x, 10, 9, P.wood, "north")
        b.set(x, 10, 6, P.wood.full)
        b.set(x, 10, 8, P.wood.full)
        slab(b, x, 11, 7, P.wood)
        b.set(x, 10, 7, P.wood.full)
    lantern(b, 4, 9, 7, hanging=True)
    # the way up: a ladder inside to a hatch in the roof
    ladder_up(b, 6, 1, 6, 3)
    entry(b, P, hood=False)
    stairs(b, 4, 3, 1, P.sfam, "south", top=True)
    lantern(b, 4, 4, 1)
    for x in (2, 6):
        for y in (2, 3):
            b.set(x, y, 2, "iron_bars", north=False, south=False, east=False, west=False, waterlogged=False)
    for z in (4, 6):
        for y in (2, 3):
            b.set(1, y, z, "iron_bars", north=False, south=False, east=False, west=False, waterlogged=False)
            b.set(7, y, z, "iron_bars", north=False, south=False, east=False, west=False, waterlogged=False)
    b.set(2, 4, 1, f"{P.cloth[0]}_wall_banner", facing="north")
    b.set(6, 4, 1, f"{P.cloth[0]}_wall_banner", facing="north")
    lantern(b, 4, 5, 5, hanging=True)


def clinic(b, P):
    """A pavilion, its gable to the street with a stone cross window, and a long ward across the back."""
    plinth(b, 1, 2, 7, 8, P.ashlar, floor=P.floor)
    walls(b, 1, 2, 7, 8, 1, 4, P.wall)
    for x, z in ((1, 2), (7, 2), (1, 8), (7, 8)):
        post(b, P, x, z, 1, 4, P.sfam.full)
    plate(b, P, 1, 2, 7, 8, 5, P.sfam.full)
    # the ward: x 1-11, z 8-11
    plinth(b, 1, 8, 11, 11, P.ashlar, floor=P.floor)
    walls(b, 1, 8, 11, 11, 1, 3, P.wall)
    for x, z in ((1, 11), (11, 8), (11, 11), (6, 11)):
        post(b, P, x, z, 1, 3, P.sfam.full)
    plate(b, P, 8, 8, 11, 11, 4, P.sfam.full)
    plate(b, P, 1, 11, 11, 11, 4, P.sfam.full)
    for y in (1, 2):
        b.set(4, y, 8, "air")
    b.set(4, 3, 8, resolve(P.ashlar, 4, 3, 8))
    if P.flat:
        flat_top(b, P, 1, 8, 11, 11, 4)
        flat_top(b, P, 1, 2, 7, 8, 5)
    else:
        def ward(t):
            gable_roof(t, 0, 12, 7, 12, 4, P.roof, axis="x", gable=P.wall, gable_at=(1, 11), eave_trim=P.wood, closed=True)

        def pav(t):
            gable_roof(t, 0, 8, 1, 9, 5, P.roof, axis="z", gable=P.wall, gable_at=(2, 8), eave_trim=P.wood, closed=True)
        roofs(b, ward, pav)
    # the cross window over the door: a stone mullion and transom
    for y in (5, 6, 7):
        b.set(4, y, 2, P.sfam.full)
    for x in (3, 5):
        b.set(x, 6, 2, P.sfam.full)
        pane(b, x, 5, 2)
        pane(b, x, 7, 2)
    entry(b, P)
    for x in (2, 6):
        win(b, P, x, 2, 2, "north", h=2, shut=False)
    for z in (4, 6):
        win(b, P, 1, 2, z, "west", h=2, shut=False)
        win(b, P, 7, 2, z, "east", h=2, shut=False)
    for x in (3, 9):
        win(b, P, x, 2, 11, "south", h=2, shut=False)
    win(b, P, 11, 2, 10, "east", h=2, shut=False)
    win(b, P, 9, 2, 8, "north", shut=False)
    # the ward's beds: white, heads to the back wall (B639: they were cots of slab and carpet, read as broken beds)
    for x in (8, 10):
        b.bed(x, 1, 9, "white", facing="south")
    tie(b, P, 5)
    tie(b, P, 4, x0=2, x1=10, z=10, lamps=(9,))


def ferry_house(b, P):
    """A boathouse under a gambrel roof, a signal mast with a lamp, an upturned boat on trestles."""
    body(b, P, 1, 2, 7, 8, 3, wall=P.boards, posts_at=((3, 2), (5, 2), (1, 5), (7, 5)))
    if P.flat:
        flat_top(b, P, 1, 2, 7, 8, 4)
    else:
        gambrel(b, P.roof, 0, 8, 1, 9, 4, P.boards, (2, 8))
    entry(b, P)
    for x in (2, 6):
        win(b, P, x, 2, 2, "north")
    for z in (4, 6):
        win(b, P, 1, 2, z, "west")
        win(b, P, 7, 2, z, "east", shut=False)
    if not P.flat:
        win(b, P, 4, 6, 2, "north", shut=False, sill=False)
        win(b, P, 4, 6, 8, "south", shut=False, sill=False)
    tie(b, P, 4)
    # a boardwalk along the front
    for x in (1, 2, 3, 5, 6, 7):
        slab(b, x, 0, 1, P.wood)
    # the signal mast with a yard and a lamp, mooring posts
    for y in range(0, 9):
        b.set(9, y, 2, P.strip, axis="y")
    for x in (8, 10):
        fence(b, x, 7, 2, P.fence)
    lantern(b, 8, 6, 2, hanging=True)
    lantern(b, 10, 6, 2, hanging=True)
    b.set(9, 9, 2, "lightning_rod", facing="up", powered=False, waterlogged=False)
    for x in (8, 10):
        fence(b, x, 1, 1, P.fence)
    # an upturned rowing boat on trestles beside the house
    for z in (4, 8):
        fence(b, 9, 1, z, P.fence)
        fence(b, 10, 1, z, P.fence)
    for z in range(5, 8):
        for x in (9, 10):
            b.set(x, 2, z, P.wood.full)
        slab(b, 9, 3, z, P.wood)
        slab(b, 10, 3, z, P.wood)
    for x in (9, 10):
        stairs(b, x, 2, 4, P.wood, "north", top=True)
        stairs(b, x, 2, 8, P.wood, "south", top=True)
    if P.flat:  # a beam end over the hull, so the gap between it and the wall isn't a pit
        log(b, 8, 3, 6, P.strip, axis="x")


def trainers_house(b, P):
    """A long low dojo, a veranda on posts along its front under the hipped roof, banners on the posts."""
    y = body(b, P, 1, 2, 11, 8, 3, posts_at=((3, 2), (5, 2), (7, 2), (9, 2), (3, 8), (7, 8), (11, 5), (1, 5)))
    for x in range(1, 12):
        if x != 4:
            slab(b, x, 0, 1, P.wood)
    for x in (1, 7, 11):
        post(b, P, x, 1, 1, 3)
    for x in range(1, 12):
        log(b, x, 4, 1, P.strip, axis="x")
    if P.flat:
        flat_top(b, P, 1, 1, 11, 8, 5)
        box(b, 1, 4, 1, 11, 4, 1, P.strip, axis="x")
    else:
        hip_roof(b, 0, 12, 0, 9, 4, P.roof2 if P.roof2 is not COBBLE else P.roof)
    entry(b, P, hood=False)
    lantern(b, 4, 3, 1, hanging=True)
    for x in (2, 6, 8, 10):
        win(b, P, x, 2, 2, "north", shut=False)
    for z in (4, 6):
        win(b, P, 1, 2, z, "west", shut=False)
        win(b, P, 11, 2, z, "east", shut=False)
    for x in (2, 5, 9):
        win(b, P, x, 2, 8, "south", shut=False)
    for x in (1, 11):
        b.set(x, 3, 0, f"{P.cloth[0]}_wall_banner", facing="north")
    b.set(7, 3, 0, f"{P.cloth[1]}_wall_banner", facing="north")
    tie(b, P, 4, x0=2, x1=10, lamps=(4, 8))
    # practice targets at the dojo's east end
    for z in (3, 7):
        b.set(12, 0, z, resolve(P.base, 12, 0, z))
        b.set(12, 1, z, "target")


def leaders_hall(b, P):
    """A stone hall behind a portico of four columns under a pediment, on a stylobate the width of the front."""
    plinth(b, 1, 2, 7, 10, P.base, floor=P.floor)
    walls(b, 1, 2, 7, 10, 1, 5, P.ashlar)
    for x, z in ((1, 2), (7, 2), (1, 10), (7, 10), (1, 6), (7, 6)):
        post(b, P, x, z, 1, 5, P.sfam.full)
    plate(b, P, 1, 2, 7, 10, 6, P.sfam.full)
    # the stylobate and the columns
    for x in range(0, 9):
        b.set(x, 0, 1, P.sfam.full)
    for x in (1, 3, 5, 7):
        for y in range(1, 6):
            wall_block(b, x, y, 1, P.sfam.wall or P.parapet)
    for x in range(0, 9):
        b.set(x, 6, 1, P.sfam.full)
    if P.flat:
        flat_top(b, P, 1, 1, 7, 10, 7)
        for x in range(2, 7):
            b.set(x, 8, 1, P.sfam.full)
        b.set(4, 9, 1, P.sfam.full)
        for x in (3, 5):
            stairs(b, x, 9, 1, P.sfam, "east" if x == 3 else "west")
    else:
        gable_roof(b, 0, 8, 0, 11, 7, P.roof2 if P.roof2 is not COBBLE else P.roof, axis="z", gable=P.ashlar,
                   gable_at=(1, 10))
        # the pediment's cornice
        for x in range(0, 9):
            stairs(b, x, 6, 0, P.sfam, "south", top=True)
        b.set(4, 9, 1, "chiseled_stone_bricks" if P.sfam is STONE_BRICK else P.sfam.full)
    door(b, 4, 1, 2, P.door, "south")
    for x in (2, 6):
        win(b, P, x, 2, 2, "north", h=2, shut=False, sill=False)
    for z in (4, 8):
        win(b, P, 1, 2, z, "west", h=3, shut=False)
        win(b, P, 7, 2, z, "east", h=3, shut=False)
    lantern(b, 4, 5, 1, hanging=True)
    tie(b, P, 6, z=6)
    b.set(4, 1, 4, "red_carpet")  # a block in from the door: on a carpet under its lintel a villager hits its head (B173)


def round_cells(cx, cz):
    ring = {(cx - 1, cz - 2), (cx, cz - 2), (cx + 1, cz - 2), (cx - 2, cz - 1), (cx + 2, cz - 1), (cx - 2, cz),
            (cx + 2, cz), (cx - 2, cz + 1), (cx + 2, cz + 1), (cx - 1, cz + 2), (cx, cz + 2), (cx + 1, cz + 2)}
    inside = {(x, z) for x in range(cx - 1, cx + 2) for z in range(cz - 1, cz + 2)}
    return ring, inside


def ball_workshop(b, P):
    """A low workshop and a round tower with an open drying loft of slats under a copper spire."""
    y = body(b, P, 1, 2, 7, 8, 3, wall=P.boards, posts_at=((3, 2), (5, 2), (1, 5)))
    roof(b, P, "gable", 1, 2, 7, 8, y, axis="x", gable=P.boards)
    cx, cz = 10, 5
    ring, inside = round_cells(cx, cz)
    for x, z in ring:
        b.set(x, 0, z, resolve(P.base, x, 0, z))
        for yy in range(1, 9):
            b.set(x, yy, z, resolve(P.base if yy <= 3 else P.ashlar, x, yy, z))
    for x, z in inside:
        b.set(x, 0, z, P.floor if not isinstance(P.floor, Mix) else P.floor.at(x, 0, z))
    for x, z in ring | inside:
        b.set(x, 9, z, P.wood.full)
    # the loft: slatted fences round it, apricorns (carpets) laid out to dry on its floor
    for x, z in ring:
        fence(b, x, 10, z, P.fence)
    apricorns = ("red_carpet", "yellow_carpet", "blue_carpet", "lime_carpet", "pink_carpet", "black_carpet", "white_carpet")
    hip_roof(b, cx - 2, cx + 2, cz - 2, cz + 2, 11, CUT_COPPER, steep=True)
    # under the spire's foot the loft is two high: a carpet there leaves a villager 1.94 (B197), so the apricorns
    # dry only where the spire rises over the floor
    for i, (x, z) in enumerate(sorted(inside)):
        if (x, z) != (cx + 1, cz) and all(b.blocks.get((x, yy, z), ("minecraft:air",))[0] == "minecraft:air" for yy in (11, 12)):
            b.set(x, 10, z, apricorns[i % len(apricorns)])
    b.set(cx, 15, cz, "lightning_rod", facing="up", powered=False, waterlogged=False)
    # the way in from the workshop and up the tower
    for yy in (1, 2):
        b.set(7, yy, 5, "air")
        b.set(8, yy, 5, "air")
    ladder_up(b, cx + 1, 1, 9, cz, "west")
    for yy in (4, 7):
        pane(b, cx, yy, cz - 2)
        pane(b, cx + 2, yy, cz)
        pane(b, cx, yy, cz + 2)
    lantern(b, cx, 8, cz, hanging=True)
    entry(b, P)
    for x in (2, 6):
        win(b, P, x, 2, 2, "north")
    for z in (4, 6):
        win(b, P, 1, 2, z, "west")
    win(b, P, 4, 2, 8, "south")
    tie(b, P, 4)


def trade_hall(b, P):
    """The shop (x 1-7) and an open arcade (x 8-11) under a timber upper storey, a cupola on the ridge."""
    plinth(b, 1, 2, 7, 8, P.base, floor=P.floor)
    walls(b, 1, 2, 7, 8, 1, 4, P.ashlar)
    box(b, 8, 0, 2, 11, 0, 8, P.sfam.full)
    for x, z in ((11, 2), (11, 5), (11, 8), (8, 2), (8, 8)):
        post(b, P, x, z, 1, 4, P.sfam.full)
    for x, f in ((9, "west"), (10, "east")):  # arches between the piers
        stairs(b, x, 4, 2, P.sfam, f, top=True)
        stairs(b, x, 4, 8, P.sfam, f, top=True)
    for z, f in ((3, "north"), (4, "south"), (6, "north"), (7, "south")):
        stairs(b, 11, 4, z, P.sfam, f, top=True)
    box(b, 2, 5, 3, 10, 5, 7, P.boards)
    plate(b, P, 1, 2, 11, 8, 5, P.strip)
    walls(b, 1, 2, 11, 8, 6, 8, P.wall)
    for x, z in ((1, 2), (3, 2), (5, 2), (7, 2), (9, 2), (11, 2), (1, 8), (11, 8), (5, 8), (1, 5), (11, 5)):
        post(b, P, x, z, 6, 8)
    plate(b, P, 1, 2, 11, 8, 9)
    if P.flat:
        flat_top(b, P, 1, 2, 11, 8, 10)
    else:
        ridge = gable_roof(b, 0, 12, 1, 9, 9, P.roof, axis="x", gable=P.wall, gable_at=(1, 11), eave_trim=P.wood)
        for x in (5, 7):
            fence(b, x, ridge + 1, 5, P.fence)
        lantern(b, 6, ridge + 1, 5, hanging=True)  # from the little roof: the ridge slab under it can't hold one
        for x in (5, 6, 7):
            slab(b, x, ridge + 2, 5, P.roof)
        b.set(6, ridge + 3, 5, "lightning_rod", facing="up", powered=False, waterlogged=False)
    entry(b, P)
    for x in (2, 6):
        win(b, P, x, 2, 2, "north")
    for x in (2, 4, 6, 8, 10):
        win(b, P, x, 7, 2, "north", shut=False, sill=False)
    for z in (4, 6):
        win(b, P, 1, 2, z, "west")
        win(b, P, 1, 7, z, "west", shut=False)
        win(b, P, 11, 7, z, "east", shut=False)
    for yy in (1, 2):
        b.set(7, yy, 3, "air")  # out to the arcade
    lantern(b, 4, 4, 5, hanging=True)
    lantern(b, 10, 4, 5, hanging=True)
    # market stalls under the arcade
    for z in (3, 4):
        stairs(b, 10, 1, z, P.wood, "east", top=True)
    b.set(10, 2, 3, "potted_red_tulip")
    b.set(10, 1, 6, "hay_block", axis="y")
    b.set(10, 1, 7, resolve(P.boards, 10, 1, 7))
    slab(b, 10, 2, 7, P.wood)


def school(b, P):
    """A hall with a slim clock tower over the door and tall windows."""
    y = body(b, P, 1, 2, 9, 9, 4, wall=P.wall, base=P.ashlar, posts_at=((2, 2), (6, 2), (9, 5), (1, 5), (5, 9)))
    roof(b, P, "gable", 1, 2, 9, 9, y, axis="x", fam=P.roof2 if P.roof2 is not COBBLE else P.roof)
    # the tower: x 3-5 at z 1, rising past the ridge, a clock face, an open belfry, a pyramid cap
    for x in (3, 5):
        post(b, P, x, 1, 1, 11, P.sfam.full)
    for yy in range(3, 12):
        b.set(4, yy, 1, resolve(P.ashlar, 4, yy, 1))
        for x in (3, 4, 5):
            if b.get(x, yy, 2) is None or yy > 5:
                b.set(x, yy, 2, resolve(P.ashlar, x, yy, 2))
    b.set(4, 8, 1, "chiseled_stone_bricks" if P.sfam is STONE_BRICK else ("chiseled_sandstone" if P.flat else "polished_diorite"))
    for yy in (10, 11):
        b.set(4, yy, 1, "air")
    lantern(b, 4, 10, 1)
    b.set(4, 9, 1, resolve(P.ashlar, 4, 9, 1))
    hip_roof(b, 2, 6, 0, 3, 12, P.roof2 if P.roof2 is not COBBLE else P.roof, steep=True)
    entry(b, P, hood=False)
    lantern(b, 4, 3, 1, hanging=True)
    for x in (2, 7):
        win(b, P, x, 2, 2, "north", h=2, shut=False)
    for z in (4, 7):
        win(b, P, 1, 2, z, "west", h=2, shut=False)
        win(b, P, 9, 2, z, "east", h=2, shut=False)
    for x in (3, 7):
        win(b, P, x, 2, 9, "south", h=2, shut=False)
    tie(b, P, 5, x0=2, x1=8, lamps=(4, 7))


def storehouse(b, P):
    """A tall granary: stone below, boards above, a hoist beam out of the gable with a bale on its chain, a lean-to."""
    plinth(b, 1, 2, 7, 8, P.base, floor=P.floor)
    walls(b, 1, 2, 7, 8, 1, 4, P.ashlar)
    plate(b, P, 1, 2, 7, 8, 5)
    box(b, 2, 5, 3, 6, 5, 7, P.boards)
    walls(b, 1, 2, 7, 8, 6, 9, P.boards)
    for x, z in ((1, 2), (7, 2), (1, 8), (7, 8), (3, 2), (5, 2), (1, 5), (7, 5), (4, 8)):
        post(b, P, x, z, 6, 9)
    plate(b, P, 1, 2, 7, 8, 10)
    if P.flat:
        top = flat_top(b, P, 1, 2, 7, 8, 10)
        hy = top
    else:
        ridge = gable_roof(b, 0, 8, 1, 9, 10, P.roof, axis="z", gable=P.boards, gable_at=(2, 8), eave_trim=P.wood)
        hy = ridge - 1
    for z in (0, 1, 2, 3):
        log(b, 4, hy, z, P.strip, axis="z")
    for yy in range(hy - 2, hy):
        b.set(4, yy, 0, "chain", axis="y", waterlogged=False)
    b.set(4, hy - 3, 0, "hay_block", axis="y")
    # the loft door over the street door, the loft's hay, a ladder up
    door(b, 4, 6, 2, P.door, "south")
    for x in (3, 5):
        fence(b, x, 6, 1, P.fence)
    b.set(4, 5, 1, P.wood.full)
    entry(b, P, hood=False)
    lantern(b, 4, 4, 1, hanging=True)
    for x in (2, 6):
        win(b, P, x, 2, 2, "north")
    for z in (4, 6):
        win(b, P, 1, 2, z, "west")
        win(b, P, 1, 7, z, "west", shut=False)
    ladder_up(b, 6, 1, 5, 3)
    for x, z in ((2, 7), (3, 7), (2, 6)):
        b.set(x, 6, z, "hay_block", axis="y")
    lantern(b, 4, 4, 5, hanging=True)
    tie(b, P, 10, z=5)
    # the lean-to on the east: hay and logs under it
    for z in (3, 8):
        b.set(10, 0, z, resolve(P.base, 10, 0, z))
        post(b, P, 10, z, 1, 3)
    for z in range(2, 10):
        stairs(b, 8, 5, z, P.wood, "west")
        stairs(b, 9, 4, z, P.wood, "west")
        stairs(b, 10, 3, z, P.wood, "west")
        b.set(8, 4, z, resolve(P.boards, 8, 4, z)) if z in (2, 9) else None
    for z in (4, 5):
        b.set(8, 1, z, "hay_block", axis="z")
        b.set(9, 1, z, "hay_block", axis="z")
    b.set(8, 2, 4, "hay_block", axis="x")
    for z in (6, 7):
        log(b, 8, 1, z, P.log, axis="x")
        log(b, 9, 1, z, P.log, axis="x")
    log(b, 8, 2, 6, P.log, axis="x")


def orchard_house(b, P):
    """A low hipped cottage and a fenced orchard of blossoming fruit trees."""
    y = body(b, P, 1, 2, 7, 8, 3, posts_at=((1, 5), (7, 5), (4, 8)))
    roof(b, P, "hip", 1, 2, 7, 8, y, fam=P.roof)
    chimney(b, 1, 7, 4, 7 if not P.flat else 6, P.base)
    entry(b, P)
    for x in (2, 6):
        win(b, P, x, 2, 2, "north")
    for z in (4, 6):
        win(b, P, 1, 2, z, "west")
    win(b, P, 7, 2, 3, "east")
    win(b, P, 4, 2, 8, "south")
    tie(b, P, 4)
    box(b, 8, 0, 1, 12, 0, 9, P.soil, **({"snowy": False} if P.soil in ("grass_block", "podzol") else {}))
    for x in range(8, 13):
        if x != 10:
            fence(b, x, 1, 1, P.fence)
        fence(b, x, 1, 9, P.fence)
    for z in range(2, 9):
        fence(b, 12, 1, z, P.fence)
    b.set(10, 1, 1, P.gate, facing="north", open=False, powered=False, in_wall=False)
    lantern(b, 12, 2, 1)
    crown = (P.leaves if P.leaves != "jungle_leaves" else "oak_leaves", "flowering_azalea_leaves")
    tree(b, 9, 4, 3, P.log if "_log" in P.log else "oak_log", crown)
    tree(b, 11, 7, 3, P.log if "_log" in P.log else "oak_log", crown)
    ladder_up(b, 10, 1, 2, 4, "east")
    b.set(9, 1, 7, "hay_block", axis="y")
    stairs(b, 8, 1, 6, P.wood, "east")


def fossil_lab(b, P):
    """A stone lab under a stepped glass skylight, a fossil skeleton of bone on show beside it."""
    plinth(b, 1, 2, 7, 8, P.base, floor=P.floor)
    walls(b, 1, 2, 7, 8, 1, 4, P.ashlar)
    for x, z in ((1, 2), (7, 2), (1, 8), (7, 8), (1, 5), (7, 5)):
        post(b, P, x, z, 1, 4, P.sfam.full)
    b.ring(1, 2, 7, 8, 5, P.sfam.full)
    if P.flat:
        flat_top(b, P, 1, 2, 7, 8, 5)
        box(b, 3, 5, 4, 5, 5, 6, "glass")
    else:
        fam = P.roof2 if P.roof2 is not COBBLE else P.roof
        hip_roof(b, 0, 8, 1, 9, 5, fam, rings=2)
        box(b, 2, 7, 3, 6, 7, 7, "glass")
        box(b, 3, 8, 4, 5, 8, 6, "glass")
        slab(b, 4, 9, 5, fam)
    entry(b, P)
    for x in (2, 6):
        win(b, P, x, 2, 2, "north", h=2, shut=False, glass="glass")
    for z in (4, 6):
        win(b, P, 1, 2, z, "west", h=2, shut=False, glass="glass")
    win(b, P, 4, 2, 8, "south", h=2, shut=False, glass="glass")
    lantern(b, 4, 3, 5, hanging=True)
    for x in range(2, 7):
        log(b, x, 4, 5, P.strip, axis="x")
    # the skeleton on a stone plinth: legs, a spine, ribs, a neck and skull
    for z in range(2, 9):
        b.set(9, 0, z, P.sfam.full)
    for z in (3, 7):
        post(b, P, 9, z, 1, 2, "bone_block")
    for z in range(2, 9):
        b.set(9, 3, z, "bone_block", axis="z")
    for z in (4, 5, 6):
        for x in (8, 10):
            b.set(x, 3, z, "bone_block", axis="x")
            b.set(x, 2, z, "bone_block", axis="y")
    b.set(9, 4, 2, "bone_block", axis="y")
    b.set(9, 5, 2, "bone_block", axis="z")
    b.set(9, 5, 1, "bone_block", axis="z")
    for x in (8, 10):
        b.set(x, 0, 1, P.sfam.full)
        lantern(b, x, 1, 1)


def flower_shop(b, P):
    """A shop with its gable to the street and a glasshouse wing full of flower beds; flowers before them both."""
    y = body(b, P, 1, 2, 7, 8, 3, posts_at=((1, 5), (7, 5)))
    roof(b, P, "gable", 1, 2, 7, 8, y, axis="z")
    # the glasshouse: x 8-12, z 2-8
    plinth(b, 8, 2, 12, 8, P.base, floor=P.floor)
    for x in range(8, 13):
        for z in range(2, 9):
            if x in (8, 12) or z in (2, 8):
                for yy in (1, 2, 3):
                    b.set(x, yy, z, "glass")
    for x, z in ((12, 2), (12, 8), (12, 5), (10, 2), (10, 8)):
        post(b, P, x, z, 1, 3)
    plate(b, P, 8, 2, 12, 8, 4)
    for z in range(2, 9):
        b.set(9, 5, z, "glass")
        b.set(11, 5, z, "glass")
        b.set(10, 6, z, "glass")
        b.set(10, 5, z, "glass")
    for x in range(9, 12):
        for z in (3, 4, 6, 7):
            b.set(x, 0, z, "grass_block", snowy=False)
    plants = ("poppy", "dandelion", "blue_orchid", "allium", "azure_bluet", "red_tulip", "oxeye_daisy", "cornflower")
    for i, (x, z) in enumerate((x, z) for x in range(9, 12) for z in (3, 4, 6, 7)):
        b.set(x, 1, z, plants[i % len(plants)])
    for yy in (1, 2):
        b.set(7, yy, 4, "air")
        b.set(8, yy, 4, "air")
    b.set(7, 3, 4, resolve(P.ashlar, 7, 3, 4))
    entry(b, P)
    for x in (2, 6):
        win(b, P, x, 2, 2, "north")
        b.set(x, 2, 1, "potted_red_tulip" if x == 2 else "potted_cornflower")
    for z in (4, 6):
        win(b, P, 1, 2, z, "west")
    win(b, P, 4, 2, 8, "south")
    if not P.flat:
        win(b, P, 4, 5, 2, "north", shut=False, sill=False)
    tie(b, P, 4)
    flower_bed(b, 8, 1, 12, 1, 0, ("poppy", "dandelion", "allium", "oxeye_daisy", "cornflower"))
    lantern(b, 10, 4, 5, hanging=True)


def ranch_house(b, P):
    """A log cabin with its gable to the street and a stable wing: stall gates, a hay loft, a weathervane."""
    plinth(b, 1, 2, 7, 8, P.base, floor=P.floor)
    cabin = P.log
    for yy in range(1, 4):
        for x in range(1, 8):
            log(b, x, yy, 2, cabin, axis="x")
            log(b, x, yy, 8, cabin, axis="x")
        for z in range(3, 8):
            log(b, 1, yy, z, cabin, axis="z")
            log(b, 7, yy, z, cabin, axis="z")
    for x, z in ((1, 2), (7, 2), (1, 8), (7, 8)):  # the corner notches: the logs' ends crossing
        for yy in (1, 3):
            log(b, x, yy, z, cabin, axis="z")
    plate(b, P, 1, 2, 7, 8, 4, cabin)
    ridge = roof(b, P, "gable", 1, 2, 7, 8, 4, axis="z", gable=P.boards)
    if not P.flat:
        fence(b, 4, ridge + 1, 5, P.fence)
        b.set(4, ridge + 2, 5, "lightning_rod", facing="up", powered=False, waterlogged=False)
    # the stable: x 8-12, z 2-10, stall gates to the street, a loft door with hay over them
    plinth(b, 8, 2, 12, 10, P.base, floor=Mix((3, "coarse_dirt"), (1, "dirt"), seed=70))
    walls(b, 8, 2, 12, 10, 1, 3, P.boards)
    for x, z in ((8, 2), (12, 2), (8, 10), (12, 10), (10, 2), (12, 6)):
        post(b, P, x, z, 1, 3)
    for x in (9, 11):
        b.set(x, 1, 2, P.gate, facing="north", open=False, powered=False, in_wall=False)
        b.set(x, 2, 2, "air")
    if P.flat:
        flat_top(b, P, 8, 2, 12, 10, 4)
    else:
        gable_roof(b, 8, 12, 1, 11, 4, P.roof, axis="z", gable=P.boards, gable_at=(2, 10))
        b.set(10, 5, 2, "air")
        b.set(10, 5, 3, "hay_block", axis="z")
    for x in (9, 11):
        b.set(x, 1, 9, "hay_block", axis="y")
    b.set(10, 1, 9, "hay_block", axis="x")
    lantern(b, 10, 2, 9)
    for z in (4, 7):
        win(b, P, 12, 2, z, "east", shut=False, sill=False)
    entry(b, P)
    for x in (2, 6):
        win(b, P, x, 2, 2, "north")
    for z in (4, 6):
        win(b, P, 1, 2, z, "west")
    win(b, P, 4, 2, 8, "south")
    tie(b, P, 4)


def schoolhouse(b, P):
    """A one-room clapboard schoolhouse under a steep roof, a lamp belfry on its front gable, tall windows."""
    y = body(b, P, 1, 2, 7, 10, 4, wall=P.boards, posts_at=((3, 2), (5, 2), (1, 6), (7, 6)))
    if P.flat:
        top = flat_top(b, P, 1, 2, 7, 10, y)
        by, bz = top, 2
    else:
        ridge = roof(b, P, "gable", 1, 2, 7, 10, y, axis="z", gable=P.boards, steep=True,
                     fam=P.roof2 if P.roof2 is not COBBLE else P.roof)
        by, bz = ridge + 1, 2
        pane(b, 4, 8, 2)
        pane(b, 4, 8, 10)
    for x in (3, 4, 5):
        b.set(x, by, bz, resolve(P.boards, x, by, bz))
    for x in (3, 5):
        fence(b, x, by + 1, bz, P.fence)
    for x in (3, 4, 5):
        slab(b, x, by + 2, bz, P.wood)
    lantern(b, 4, by + 1, bz, hanging=True)
    entry(b, P)
    for x in (2, 6):
        win(b, P, x, 2, 2, "north", h=2)
    for z in (4, 8):
        win(b, P, 1, 2, z, "west", h=2)
        win(b, P, 7, 2, z, "east", h=2)
    win(b, P, 4, 2, 10, "south", h=2)
    tie(b, P, 5, z=6)


def inn_room(b, P):
    """A two-storey inn: a stone tavern below, rooms above opening onto a balcony along the front, a chimney."""
    plinth(b, 1, 2, 9, 8, P.base, floor=P.floor)
    walls(b, 1, 2, 9, 8, 1, 4, P.ashlar)
    plate(b, P, 1, 2, 9, 8, 5)
    box(b, 2, 5, 3, 8, 5, 7, P.boards)
    walls(b, 1, 2, 9, 8, 6, 8, P.wall)
    for x, z in ((1, 2), (3, 2), (5, 2), (7, 2), (9, 2), (1, 8), (5, 8), (9, 8), (1, 5), (9, 5)):
        post(b, P, x, z, 6, 8)
    plate(b, P, 1, 2, 9, 8, 9)
    # the balcony: a floor out to z 0 on posts and brackets, a rail along it
    for x in range(1, 10):
        b.set(x, 5, 1, P.wood.full)
        b.set(x, 5, 0, P.wood.full)
        fence(b, x, 6, 0, P.fence)
    for x in (1, 9):
        post(b, P, x, 0, 1, 4)
    for x in (3, 5, 7):
        stairs(b, x, 4, 0, P.wood, "south", top=True)
    if P.flat:
        flat_top(b, P, 1, 2, 9, 8, 9)
        cy = 11
    else:
        gable_roof(b, 0, 10, 1, 9, 9, P.roof, axis="x", gable=P.wall, gable_at=(1, 9), eave_trim=P.wood)
        cy = 13
        win(b, P, 1, 10, 5, "west", shut=False, sill=False)
    chimney(b, 10, 5, 0, cy, P.base) if b.w > 10 else None
    entry(b, P, hood=False)
    lantern(b, 4, 4, 1, hanging=True)
    door(b, 4, 6, 2, P.door, "south")
    for x in (2, 7):
        win(b, P, x, 2, 2, "north")
        win(b, P, x, 7, 2, "north", shut=False, sill=False)
    for z in (4, 6):
        win(b, P, 1, 2, z, "west")
        win(b, P, 1, 7, z, "west", shut=False)
    win(b, P, 9, 2, 6, "east", shut=False)
    win(b, P, 9, 7, 6, "east", shut=False)
    b.set(8, 3, 1, f"{P.cloth[0]}_wall_banner", facing="north")
    ladder_up(b, 8, 1, 5, 4)
    lantern(b, 4, 4, 5, hanging=True)
    lantern(b, 7, 4, 5, hanging=True)
    tie(b, P, 9, x0=2, x1=8, z=5, lamps=(5,))
    fence(b, 3, 6, 6, P.fence)
    b.set(3, 7, 6, f"{P.cloth[1]}_carpet")
    chair(b, 2, 6, 6, P.wood, "east")
    chair(b, 4, 6, 6, P.wood, "west")
    b.set(6, 6, 4, f"{P.cloth[0]}_carpet")


def mortuary(b, P):
    """A narrow chapel: buttresses, lancet windows, a steep dark roof with a spirelet; a graveyard beside it."""
    plinth(b, 1, 2, 7, 9, P.base, floor=P.floor)
    walls(b, 1, 2, 7, 9, 1, 4, P.ashlar)
    plate(b, P, 1, 2, 7, 9, 5, P.sfam.full)
    for z in (4, 7):
        for x, f in ((0, "east"), (8, "west")):
            b.set(x, 0, z, resolve(P.base, x, 0, z))
            b.set(x, 1, z, resolve(P.ashlar, x, 1, z))
            b.set(x, 2, z, resolve(P.ashlar, x, 2, z))
            stairs(b, x, 3, z, P.sfam, f)
    fam = P.roof2 if P.roof2 is not COBBLE else P.roof
    if P.flat:
        fam = P.roof
    ridge = gable_roof(b, 0, 8, 1, 10, 5, fam, axis="z", gable=P.ashlar, gable_at=(2, 9), steep=True)
    for yy in (ridge + 1, ridge + 2):
        wall_block(b, 4, yy, 2, P.sfam.wall or P.parapet)
    b.set(4, ridge + 3, 2, "lightning_rod", facing="up", powered=False, waterlogged=False)
    entry(b, P, hood=False)
    b.set(4, 4, 1, P.sfam.full)
    lantern(b, 4, 3, 1, hanging=True)
    for x in (2, 6):
        win(b, P, x, 2, 2, "north", h=2, shut=False)
    for z in (3, 5, 8):
        win(b, P, 1, 2, z, "west", h=2, shut=False)
        win(b, P, 7, 2, z, "east", h=2, shut=False)
    pane(b, 4, 7, 2)
    pane(b, 4, 7, 9)
    tie(b, P, 5, z=6)
    # the graveyard: x 9-10, z 2-10, headstones with candles, a railing of bars at the front
    for x in (9, 10):
        for z in range(2, 11):
            b.set(x, 0, z, P.soil, **({"snowy": False} if P.soil in ("grass_block", "podzol") else {}))
        b.set(x, 1, 1, "iron_bars", north=False, south=False, east=False, west=False, waterlogged=False)
    for z in range(2, 11):
        b.set(10, 1, z, "iron_bars", north=False, south=False, east=False, west=False, waterlogged=False) if z in (2, 10) else None
    for z in (3, 6, 9):
        wall_block(b, 10, 1, z, P.sfam.wall or P.parapet)
        b.set(10, 2, z, "candle", candles=1, lit=False, waterlogged=False)
    b.set(9, 1, 5, "potted_lily_of_the_valley")


def tinkers_shop(b, P):
    """A workshop with a glazed monitor roof along its ridge and a tall brick chimney stack."""
    y = body(b, P, 1, 2, 7, 8, 3, wall=P.ashlar, posts_at=((1, 5), (7, 5)), frame=False)
    plate(b, P, 1, 2, 7, 8, y, P.sfam.full)
    if P.flat:
        flat_top(b, P, 1, 2, 7, 8, y)
        my = y + 1
        for x in (2, 6):
            for z in (4, 6):
                post(b, P, x, z, my, my + 1, P.sfam.full)
        for x in range(3, 6):
            for z in (4, 6):
                pane(b, x, my, z)
                pane(b, x, my + 1, z)
        for z in (5,):
            pane(b, 2, my, z)
            pane(b, 6, my, z)
            pane(b, 2, my + 1, z)
            pane(b, 6, my + 1, z)
        box(b, 2, my + 2, 4, 6, my + 2, 6, P.roof.full)
        for x in range(3, 6):
            b.set(x, y, 5, "air")
    else:
        fam = P.roof
        gable_roof(b, 0, 8, 1, 9, y, fam, axis="x", gable=P.ashlar, gable_at=(1, 7), eave_trim=P.wood)
        # the monitor: glass sides on the ridge, its own little roof
        for x in range(1, 8):
            for z in (4, 6):
                b.set(x, y + 3, z, P.ashlar if x in (1, 7) else "air")
            b.set(x, y + 4, 5, "air")
        for x in range(2, 7):
            for z in (4, 6):
                pane(b, x, y + 3, z)
                pane(b, x, y + 4, z)
        for x in (1, 7):
            for yy in (y + 3, y + 4):
                for z in (4, 5, 6):
                    b.set(x, yy, z, resolve(P.ashlar, x, yy, z))
        gable_roof(b, 0, 8, 3, 7, y + 5, fam, axis="x", gable=P.ashlar, gable_at=(1, 7))
        for x in range(2, 7):
            b.set(x, y + 3, 5, "air")
        b.set(4, y + 7, 5, "lightning_rod", facing="up", powered=False, waterlogged=False)
    # the stack: 2 x 2 of brick, a cap
    for xx in (8, 9):
        for zz in (6, 7):
            for yy in range(0, 13):
                b.set(xx, yy, zz, "bricks")
    for xx, zz, f in ((8, 5, "south"), (9, 5, "south"), (8, 8, "north"), (9, 8, "north")):
        stairs(b, xx, 12, zz, BRICK, f, top=True)
    for zz in (6, 7):
        stairs(b, 10, 12, zz, BRICK, "west", top=True)
    for xx in (8, 9):
        for zz in (6, 7):
            b.set(xx, 13, zz, "campfire", facing="north", lit=True, signal_fire=False, waterlogged=False) if (xx, zz) == (8, 6) else None
    entry(b, P)
    for x in (2, 6):
        for yy in (2, 3):
            b.set(x, yy, 2, "iron_bars", north=False, south=False, east=False, west=False, waterlogged=False)
    for z in (4, 6):
        for yy in (2, 3):
            b.set(1, yy, z, "iron_bars", north=False, south=False, east=False, west=False, waterlogged=False)
    tie(b, P, y, lamps=(2,) if P.flat else (4,))


def sifting_shed(b, P):
    """A long lean-to of slabs over a workroom and an open shed with heaps of gravel and sand and a sieve."""
    plinth(b, 1, 2, 7, 8, P.base, floor=P.floor)
    lean = slab_lean(b, P.wood if P.flat else P.roof, 0, 12, 10, 1, 4)
    # walls up to the slabs (a top slab sits on the full block under it; a bottom slab on the one below)
    for z in range(2, 9):
        yy, top = lean[z]
        wtop = yy if top else yy - 1
        for x in (1, 7):
            for y2 in range(1, wtop + 1):
                b.set(x, y2, z, resolve(P.boards, x, y2, z))
    for z in (2, 8):
        yy, top = lean[z]
        wtop = yy if top else yy - 1
        for x in range(1, 8):
            for y2 in range(1, wtop + 1):
                b.set(x, y2, z, resolve(P.boards, x, y2, z))
    for z in (2, 5, 8):
        yy, top = lean[z]
        for x in (1, 7):
            post(b, P, x, z, 1, yy if top else yy - 1)
        post(b, P, 12, z, 1, yy if top else yy - 1)
        b.set(12, 0, z, resolve(P.base, 12, 0, z))
    box(b, 8, 0, 2, 11, 0, 8, P.yard)
    entry(b, P)
    for x in (2, 6):
        win(b, P, x, 2, 2, "north")
    for z in (4, 6):
        win(b, P, 1, 2, z, "west")
    win(b, P, 7, 2, 6, "east", shut=False)
    win(b, P, 4, 2, 8, "south", shut=False)
    tie(b, P, 4, lamps=(4,))
    # heaps and the sieve
    for x, z, h, what in ((9, 7, 2, "gravel"), (10, 7, 1, "gravel"), (9, 6, 1, "gravel"), (11, 3, 2, "sand"),
                          (11, 4, 1, "sand"), (10, 3, 1, "sand")):
        for y2 in range(1, h + 1):
            b.set(x, y2, z, what)
    for x, z in ((8, 4), (10, 4), (8, 5), (10, 5)):
        fence(b, x, 1, z, P.fence)
    for x in (8, 9, 10):
        for z in (4, 5):
            trapdoor(b, x, 2, z, P.trap, "north", half="top")
    lantern(b, 10, 3, 6, hanging=True)
    log(b, 10, 4, 6, P.strip, axis="z")


def compost_yard(b, P):
    """A hut under a mossy turf roof with azaleas on it, three compost bays beside it."""
    y = body(b, P, 1, 2, 7, 8, 3, wall=P.boards, posts_at=((1, 5), (7, 5)))
    if P.flat:
        flat_top(b, P, 1, 2, 7, 8, y)
    else:
        ridge = gable_roof(b, 0, 8, 1, 9, y, MOSSY_COBBLE, axis="x", gable=P.boards, gable_at=(1, 7), eave_trim=P.wood)
        for x in range(0, 9):
            b.set(x, ridge, 5, "moss_block")
        for x in (2, 6):
            b.set(x, ridge + 1, 5, "flowering_azalea")
        b.set(4, ridge + 1, 5, "azalea")
    entry(b, P)
    for x in (2, 6):
        win(b, P, x, 2, 2, "north")
    for z in (4, 6):
        win(b, P, 1, 2, z, "west")
    win(b, P, 4, 2, 8, "south")
    tie(b, P, 4)
    # the bays: x 8-12, z 2-8, board walls, three heaps of different ripeness
    for x in range(8, 13):
        b.set(x, 0, 2, resolve(P.base, x, 0, 2))
        slab(b, x, 1, 2, P.wood) if x not in (8, 10, 12) else None
    for x in (8, 10, 12):
        for z in range(2, 9):
            b.set(x, 1, z, resolve(P.boards, x, 1, z))
        post(b, P, x, 2, 1, 2)
        post(b, P, x, 8, 1, 2)
    for x in range(8, 13):
        b.set(x, 1, 8, resolve(P.boards, x, 1, 8))
    for x, top in ((9, "coarse_dirt"), (11, "podzol")):
        for z in range(3, 8):
            b.set(x, 0, z, "rooted_dirt")
            b.set(x, 1, z, top, **({"snowy": False} if top == "podzol" else {}))
        b.set(x, 2, 5, top, **({"snowy": False} if top == "podzol" else {}))
    b.set(9, 2, 4, "coarse_dirt")
    b.set(11, 2, 6, "hay_block", axis="y")
    lantern(b, 10, 3, 2)
    lantern(b, 12, 3, 8)


def winery(b, P):
    """A stone cellar house with casks stacked before it and a pergola hung with grapes (glow berries) on its east side."""
    y = body(b, P, 1, 2, 7, 9, 3, wall=P.ashlar, frame=False)
    plate(b, P, 1, 2, 7, 9, y, P.strip)
    roof(b, P, "hip", 1, 2, 7, 9, y, fam=P.roof2 if P.roof2 is not COBBLE else P.roof)
    entry(b, P, hood=False)
    stairs(b, 3, 3, 1, P.sfam, "west", top=True)
    stairs(b, 5, 3, 1, P.sfam, "east", top=True)
    b.set(4, 3, 1, P.sfam.slab, type="top", waterlogged=False)
    lantern(b, 4, 4, 1)
    # casks: logs with their ends to the street, on stone cradles
    for x in (1, 2, 6, 7):
        stairs(b, x, 0, 1, P.sfam, "south")
        log(b, x, 1, 1, P.log if "stripped" not in P.log else "dark_oak_log", axis="z")
    for x in (1, 7):
        log(b, x, 2, 1, P.log if "stripped" not in P.log else "dark_oak_log", axis="z")
    for z in (4, 7):
        win(b, P, 1, 2, z, "west", shut=False)
    win(b, P, 4, 2, 9, "south", shut=False)
    tie(b, P, 4)
    # the pergola: x 8-9, z 2-9
    for z in (2, 5, 9):
        b.set(9, 0, z, resolve(P.base, 9, 0, z))
        post(b, P, 9, z, 1, 3, P.log if "_log" in P.log else P.strip)
    for z in range(2, 10):
        log(b, 9, 4, z, P.strip, axis="z")
        if z % 2 == 0:
            log(b, 8, 4, z, P.strip, axis="x")
    for z in range(2, 10):
        if z % 2 == 1:
            leaves(b, 8, 4, z, "azalea_leaves")
        leaves(b, 9, 5, z, "azalea_leaves" if z % 3 else "flowering_azalea_leaves")
        leaves(b, 8, 5, z, "azalea_leaves")
    for z in (3, 7):
        b.set(8, 3, z, "cave_vines", age=0, berries=True)
    b.set(8, 3, 5, "cave_vines", age=0, berries=True)
    stairs(b, 8, 1, 6, P.wood, "east")
    stairs(b, 8, 1, 7, P.wood, "east")


def tailors_shop(b, P):
    """A shop with two display windows either side of the door, a striped awning, banners and a mansard roof."""
    y = body(b, P, 1, 2, 7, 8, 4, posts_at=((1, 5), (7, 5)))
    for x in (2, 3, 5, 6):  # the display windows
        for yy in (1, 2, 3):
            pane(b, x, yy, 2)
    for x in (1, 7):
        post(b, P, x, 2, 1, 4)
    for x in range(1, 8):
        b.set(x, 4, 1, f"{P.cloth[x % 2]}_wool")
    if P.flat:
        flat_top(b, P, 1, 2, 7, 8, y)
    else:
        fam = P.roof2 if P.roof2 is not COBBLE else P.roof
        hip_roof(b, 0, 8, 1, 9, y, fam, steep=True, rings=2)
        box(b, 2, y + 3, 3, 6, y + 3, 7, fam.slab, type="bottom", waterlogged=False)
        for x in (2, 6):  # dormers in the steep slope
            pane(b, x, y + 1, 2)
    door(b, 4, 1, 2, P.door, "south")
    stairs(b, 4, 0, 1, P.sfam, "south")
    lantern(b, 4, 3, 1, hanging=True)
    for x in (1, 7):
        b.set(x, 3, 1, f"{P.cloth[0]}_wall_banner", facing="north")
    for z in (4, 6):
        win(b, P, 1, 2, z, "west")
    win(b, P, 4, 2, 8, "south")
    tie(b, P, 5)


def print_shop(b, P):
    """A tall narrow house with crow-stepped gables front and back and the shop's lamp on an iron arm."""
    plinth(b, 1, 2, 7, 8, P.base, floor=P.floor)
    walls(b, 1, 2, 7, 8, 1, 8, P.wall)
    for x, z in ((1, 2), (7, 2), (1, 8), (7, 8), (1, 5), (7, 5)):
        post(b, P, x, z, 1, 8)
    plate(b, P, 1, 2, 7, 8, 5)
    plate(b, P, 1, 2, 7, 8, 9)
    if P.flat:
        flat_top(b, P, 1, 2, 7, 8, 10)
        top_of = {x: 10 for x in range(1, 8)}
    else:
        gable_roof(b, 0, 8, 2, 8, 9, P.roof, axis="z", gable=P.ashlar, gable_at=(2, 8))
        top_of = {x: 9 + min(x, 8 - x) for x in range(0, 9)}
    # the crow steps on both gables
    for z in (2, 8):
        for x in range(1, 8):
            stepped = (top_of[x] + 1) - (top_of[x] + 1 - 9) % 2 if not P.flat else 11 + (2 if 3 <= x <= 5 else 0) + (1 if x == 4 else 0)
            for yy in range(9, stepped + 1):
                b.set(x, yy, z, resolve(P.ashlar, x, yy, z))
            slab(b, x, stepped + 1, z, P.sfam)
    entry(b, P)
    for x in (2, 6):
        win(b, P, x, 2, 2, "north")
        win(b, P, x, 6, 2, "north", h=2, shut=False)
    win(b, P, 4, 6, 2, "north", h=2, shut=False)
    for z in (4, 6):
        win(b, P, 1, 2, z, "west")
        win(b, P, 1, 6, z, "west", h=2, shut=False)
        win(b, P, 7, 6, z, "east", h=2, shut=False)
    win(b, P, 4, 2, 8, "south")
    # the lamp on its arm
    b.set(6, 5, 1, "iron_bars", north=False, south=False, east=False, west=False, waterlogged=False)
    b.set(6, 5, 0, "iron_bars", north=False, south=False, east=False, west=False, waterlogged=False)
    lantern(b, 6, 4, 0, hanging=True)
    tie(b, P, 5)
    tie(b, P, 9, lamps=())


def jewellers_workshop(b, P):
    """A stone strongroom with barred windows, a cornice, a dome with an amethyst finial, gems on pedestals."""
    plinth(b, 1, 2, 7, 8, P.base, floor=P.floor)
    walls(b, 1, 2, 7, 8, 1, 4, P.ashlar)
    for x, z in ((1, 2), (7, 2), (1, 8), (7, 8)):
        post(b, P, x, z, 1, 4, P.sfam.full)
    for x in range(0, 9):
        stairs(b, x, 4, 1, P.sfam, "south", top=True)
        stairs(b, x, 4, 9, P.sfam, "north", top=True)
    for z in range(2, 9):
        stairs(b, 0, 4, z, P.sfam, "east", top=True)
        stairs(b, 8, 4, z, P.sfam, "west", top=True)
    box(b, 0, 5, 1, 8, 5, 9, P.sfam.full)
    for x in range(0, 9):
        for z in range(1, 10):
            if (x in (0, 8) or z in (1, 9)) and (x + z) % 2 == 1:
                wall_block(b, x, 6, z, P.sfam.wall or P.parapet)
    dome = (P.roof2 if P.flat or P.roof2 is COBBLE else P.sfam)
    b.ring(2, 3, 6, 7, 6, P.sfam.full)
    hip_roof(b, 2, 6, 3, 7, 7, dome)
    for x, z in ((2, 3), (6, 3), (2, 7), (6, 7)):
        slab(b, x, 7, z, dome)
    b.set(4, 9, 5, "amethyst_block")
    b.set(4, 10, 5, "amethyst_cluster", facing="up", waterlogged=False)
    entry(b, P, hood=False)
    b.set(4, 4, 1, P.sfam.full)
    lantern(b, 4, 3, 1, hanging=True)
    for x in (2, 6):
        for yy in (2, 3):
            b.set(x, yy, 2, "iron_bars", north=False, south=False, east=False, west=False, waterlogged=False)
    for z in (4, 6):
        for yy in (2, 3):
            b.set(1, yy, z, "iron_bars", north=False, south=False, east=False, west=False, waterlogged=False)
            b.set(7, yy, z, "iron_bars", north=False, south=False, east=False, west=False, waterlogged=False)
    for x in (1, 7):  # pedestals with a gem on each
        b.set(x, 0, 1, P.sfam.full)
        wall_block(b, x, 1, 1, P.sfam.wall or P.parapet)
        b.set(x, 2, 1, "amethyst_cluster", facing="up", waterlogged=False)
    lantern(b, 4, 4, 5, hanging=True)


def crafters_workshop(b, P):
    """The Crafter's (ROADMAP 37.16): a board workshop with its plastered gable to the street, a work hatch with a counter
    beside the door, and a lean-to parts store along its east side (logs, stacked boards and slabs) under a slab roof,
    reached by a side door. At most 10 wide, like the luxury houses (their test places two side by side 11 apart)."""
    # the parts store first: the main roof's eave runs over its inner row
    box(b, 8, 0, 3, 9, 0, 8, P.yard)
    for z in (3, 8):
        b.set(9, 0, z, resolve(P.base, 9, 0, z))
        post(b, P, 9, z, 1, 2)
    for x in (8, 9):
        for z in range(3, 9):
            slab(b, x, 3, z, P.wood)
    for z in (6, 7):  # logs piled ends out, two high at the back
        log(b, 9, 1, z, P.log, axis="x")
    log(b, 9, 2, 7, P.log, axis="x")
    for z in (4, 5):  # stacked boards and a slab on top
        slab(b, 9, 1, z, P.wood, double=True)
        slab(b, 9, 2, z, P.wood)
    lantern(b, 8, 2, 6, hanging=True)
    # the workshop
    y = body(b, P, 1, 2, 7, 8, 3, wall=P.boards, posts_at=((3, 2), (5, 2), (1, 5), (7, 5), (3, 8), (5, 8)))
    if P.flat:
        flat_top(b, P, 1, 2, 7, 8, y)
    else:
        fam = P.roof2 if P.roof2 is not COBBLE else P.roof
        gable_roof(b, 0, 8, 1, 9, y, fam, axis="z", gable=P.wall, gable_at=(2, 8), eave_trim=P.wood)
        win(b, P, 4, 6, 2, "north", shut=False)  # the loft's window in the front gable
        win(b, P, 4, 6, 8, "south", shut=False)
    entry(b, P)
    win(b, P, 2, 2, 2, "north")
    # the work hatch: a wide opening over a counter, its shutter propped open above
    for yy in (1, 2):
        pane(b, 6, yy, 2)
    b.set(6, 1, 1, P.wood.slab, type="top", waterlogged=False)
    b.set(6, 3, 1, P.trap, facing="north", half="top", open=False, powered=False, waterlogged=False)
    for z in (4, 6):
        win(b, P, 1, 2, z, "west")
    win(b, P, 4, 2, 8, "south")
    win(b, P, 7, 2, 6, "east", shut=False, sill=False)
    door(b, 7, 1, 4, P.door, "east")  # out to the store (not beside the bed at (6, 6), B178)
    tie(b, P, 4)


def pokemon_center(b, P):
    """A white house under a red hipped roof, a glazed front under a canopy."""
    white = "white_concrete" if P.flat else P.wall
    y = body(b, P, 1, 2, 9, 8, 3, wall=white, posts_at=((9, 5), (1, 5)), frame=False)
    b.ring(1, 2, 9, 8, y, "red_terracotta")
    hip_roof(b, 0, 10, 1, 9, y, BRICK)
    for x in (2, 3, 5, 6):
        pane(b, x, 1, 2)
        pane(b, x, 2, 2)
    door(b, 4, 1, 2, P.door, "south")
    stairs(b, 4, 0, 1, P.sfam, "south")
    for x in range(2, 7):
        slab(b, x, 3, 1, BRICK)
    for x in (2, 6):
        lantern(b, x, 2, 1, hanging=True)
    for z in (4, 6):
        win(b, P, 1, 2, z, "west", shut=False)
        win(b, P, 9, 2, z, "east", shut=False)
    win(b, P, 5, 2, 8, "south", shut=False)
    win(b, P, 8, 2, 2, "north", shut=False)
    tie(b, P, 4, x0=2, x1=8, lamps=(4, 7))


def camp_kitchen(b, P):
    """An A-frame cook hut, its roof down to the ground, and a fire pit with a spit and log seats beside it."""
    plinth(b, 1, 2, 7, 8, P.base, floor=P.floor)
    for z in range(2, 9):
        for x in (1, 7):
            b.set(x, 1, z, resolve(P.boards, x, 1, z))
    for x in range(0, 9):
        for z in range(1, 10):
            if x in (0, 8):
                b.set(x, 0, z, resolve(P.base, x, 0, z))
    fam = P.roof2 if P.roof2 is not COBBLE else P.roof
    gable_roof(b, 0, 8, 1, 9, 1, fam, axis="z", gable=P.boards, gable_at=(2, 8), steep=True)
    entry(b, P, hood=False)
    fence(b, 6, 1, 1, P.fence)
    lantern(b, 6, 2, 1)
    for x in (3, 5):
        pane(b, x, 3, 2)
        pane(b, x, 3, 8)
    pane(b, 4, 5, 2)
    pane(b, 4, 5, 8)
    for x in range(2, 7):
        log(b, x, 5, 5, P.strip, axis="x") if x in (3, 4, 5) else None
    lantern(b, 4, 4, 5, hanging=True)
    # the fire pit: a campfire in a stone ring, a spit on two posts, log seats round it, firewood stacked
    for x in range(9, 12):
        for z in range(3, 8):
            b.set(x, 0, z, P.yard.at(x, 0, z) if isinstance(P.yard, Mix) else P.yard)
    b.set(10, 0, 5, resolve(P.base, 10, 0, 5))
    b.set(10, 1, 5, "campfire", facing="north", lit=True, signal_fire=False, waterlogged=False)
    for z in (4, 6):
        stairs(b, 10, 0, z, P.sfam, "south" if z == 6 else "north")
    for x in (9, 11):
        fence(b, x, 1, 5, P.fence)
        fence(b, x, 2, 5, P.fence)
    log(b, 10, 3, 5, P.strip, axis="x")
    log(b, 9, 3, 5, P.strip, axis="x")
    log(b, 11, 3, 5, P.strip, axis="x")
    for x in (9, 10, 11):
        log(b, x, 1, 3, P.log if "_log" in P.log else P.strip, axis="x") if x != 10 else None
        log(b, x, 1, 7, P.log if "_log" in P.log else P.strip, axis="x") if x != 10 else None
    for z in (8, 9):
        for x in (9, 10, 11):
            log(b, x, 1, z, P.log if "_log" in P.log else P.strip, axis="z")
        log(b, 10, 2, z, P.log if "_log" in P.log else P.strip, axis="z")


def berry_nursery(b, P):
    """A cottage under a stepped thatch of hay, raised berry beds beside it watered by a channel."""
    y = body(b, P, 1, 2, 7, 8, 3, posts_at=((1, 5), (7, 5)))
    for i in range(5):
        yy = y + i
        for x in range(0, 9):
            for z in (1 + i, 9 - i):
                b.set(x, yy, z, "hay_block", axis="x")
        if 1 + i >= 9 - i:
            break
        for z in range(2 + i, 9 - i):
            for x in (1, 7):
                if b.get(x, yy, z) is None:
                    b.set(x, yy, z, resolve(P.wall, x, yy, z))
    entry(b, P)
    for x in (2, 6):
        win(b, P, x, 2, 2, "north")
    for z in (4, 6):
        win(b, P, 1, 2, z, "west")
    win(b, P, 1, 5, 5, "west", shut=False, sill=False)
    win(b, P, 7, 5, 5, "east", shut=False, sill=False)
    win(b, P, 4, 2, 8, "south")
    tie(b, P, 4)
    # the beds: x 8-11, z 2-9, a channel down the middle
    for z in range(2, 10):
        for x in (8, 11):
            b.set(x, 0, z, resolve(P.base, x, 0, z))
            fence(b, x, 1, z, P.fence) if z in (2, 9) or x == 11 else None
    for z in range(3, 9):
        for x in (8, 9):
            b.set(x, 0, z, "grass_block", snowy=False)
            b.set(x, 1, z, "sweet_berry_bush", age=3 if (x + z) % 2 else 2)
        b.set(10, 0, z, "water", level=0)
    for z in (2, 9):
        for x in (9, 10):
            b.set(x, 0, z, resolve(P.base, x, 0, z))
    for x in (8, 11):
        b.set(x, 2, 2, "lantern", hanging=False, waterlogged=False) if x == 11 else None


def daycare(b, P):
    """A cottage with a cross gable and a round window over the door, a playground with a swing and a sandpit."""
    y = body(b, P, 1, 2, 7, 8, 3, posts_at=((1, 5), (7, 5), (3, 2), (5, 2)))
    if P.flat:
        flat_top(b, P, 1, 2, 7, 8, y)
        for x in range(3, 6):
            b.set(x, 5, 2, resolve(P.wall, x, 5, 2))
            b.set(x, 6, 2, resolve(P.wall, x, 6, 2))
        b.set(4, 7, 2, resolve(P.wall, 4, 7, 2))
        pane(b, 4, 5, 2)
    else:
        def main(t):
            gable_roof(t, 0, 8, 1, 9, y, P.roof, axis="x", gable=P.wall, gable_at=(1, 7), eave_trim=P.wood)

        def cross(t):
            gable_roof(t, 2, 6, 0, 5, y + 1, P.roof, axis="z", gable=P.wall, gable_at=(1, 5))
        roofs(b, main, cross)
        for x in range(3, 6):
            b.set(x, y + 1, 1, resolve(P.wall, x, y + 1, 1))
        b.set(4, y + 2, 1, resolve(P.wall, 4, y + 2, 1))
        pane(b, 4, y + 1, 1)
    entry(b, P)
    for x in (2, 6):
        win(b, P, x, 2, 2, "north")
    for z in (4, 6):
        win(b, P, 1, 2, z, "west")
    win(b, P, 4, 2, 8, "south")
    tie(b, P, 4)
    # the playground: x 8-11, z 1-9
    for x in range(8, 12):
        for z in range(1, 10):
            b.set(x, 0, z, "grass_block", snowy=False)
    for x in range(8, 12):
        fence(b, x, 1, 9, P.fence)
        if x != 9:
            fence(b, x, 1, 1, P.fence)
    for z in range(2, 9):
        fence(b, 11, 1, z, P.fence)
    b.set(9, 1, 1, P.gate, facing="north", open=False, powered=False, in_wall=False)
    for z in (6, 7, 8):
        b.set(9, 0, z, "sand")
        b.set(10, 0, z, "sand")
    for z in (3, 5):
        post(b, P, 9, z, 1, 3, P.log if "_log" in P.log else P.strip)
    for z in (3, 4, 5):
        log(b, 9, 4, z, P.strip, axis="z")
    for yy in (2, 3):
        b.set(9, yy, 4, "chain", axis="y", waterlogged=False)
    slab(b, 9, 1, 4, P.wood)
    lantern(b, 11, 2, 1)


def gem_grotto(b, P):
    """A mound of stone and moss with amethyst growing out of it and a geode-banded doorway."""
    stone = P.base
    plinth(b, 1, 2, 7, 8, stone, floor=P.floor)
    walls(b, 1, 2, 7, 8, 1, 3, Mix((5, "tuff"), (3, "stone"), (2, "andesite"), seed=61) if not P.flat else P.ashlar)
    # the mound: rings stepping in, stone at the edges and moss on top
    box(b, 1, 4, 2, 7, 4, 8, stone)
    for x in range(0, 9):
        stairs(b, x, 3, 1, P.sfam, "south")
        stairs(b, x, 3, 9, P.sfam, "north")
    for z in range(2, 9):
        stairs(b, 0, 3, z, P.sfam, "east")
        stairs(b, 8, 3, z, P.sfam, "west")
    for x in range(1, 8):
        for z in range(2, 9):
            if x in (1, 7) or z in (2, 8):
                stairs(b, x, 5, z, P.sfam, "south" if z == 2 else "north" if z == 8 else "east" if x == 1 else "west")
    top = "moss_block" if not P.flat else "sandstone"
    box(b, 2, 5, 3, 6, 5, 7, top)
    box(b, 3, 6, 4, 5, 6, 6, top)
    for x, z in ((2, 3), (6, 3), (2, 7), (6, 7)):
        stairs(b, x, 6, z, P.sfam, "south" if z == 3 else "north")
    for x in (3, 4, 5):
        stairs(b, x, 6, 3, P.sfam, "south")
        stairs(b, x, 6, 7, P.sfam, "north")
    for z in (4, 5, 6):
        stairs(b, 2, 6, z, P.sfam, "east")
        stairs(b, 6, 6, z, P.sfam, "west")
    b.set(4, 7, 5, "amethyst_block")
    b.set(4, 8, 5, "amethyst_cluster", facing="up", waterlogged=False)
    for x, z in ((3, 4), (5, 6)):
        b.set(x, 7, z, "amethyst_cluster", facing="up", waterlogged=False)
    b.set(5, 7, 4, "large_amethyst_bud", facing="up", waterlogged=False)
    if not P.flat:
        b.set(3, 7, 6, "moss_carpet")
        b.set(5, 6, 5, "moss_block")
    # the geode doorway: calcite round the door, then smooth basalt
    for yy in (1, 2, 3):
        for x in (3, 5):
            b.set(x, yy, 2, "calcite")
    b.set(4, 3, 2, "calcite")
    for yy in (1, 2, 3, 4):
        b.set(2, yy, 1, "smooth_basalt") if yy <= 3 else None
        b.set(6, yy, 1, "smooth_basalt") if yy <= 3 else None
    for x in (2, 3, 4, 5, 6):
        b.set(x, 4, 1, "smooth_basalt") if x in (2, 6) else stairs(b, x, 4, 1, P.sfam, "south", top=True)
    door(b, 4, 1, 2, P.door, "south")
    stairs(b, 4, 0, 1, P.sfam, "south")
    b.set(4, 4, 1, "smooth_basalt")
    lantern(b, 4, 3, 1, hanging=True)
    for z in (4, 6):
        win(b, P, 1, 2, z, "west", shut=False)
        win(b, P, 7, 2, z, "east", shut=False)
    lantern(b, 4, 3, 5, hanging=True)
    for x in range(2, 7):
        b.set(x, 4, 5, resolve(stone, x, 4, 5))


# name: (draw, (width, height, depth))
DESIGNS = {
    "builders_workshop": (builders_workshop, (13, 13, 11)),
    "carpenters_workshop": (carpenters_workshop, (13, 11, 11)),
    "kitchen": (kitchen, (11, 12, 11)),
    "post_office": (post_office, (9, 17, 10)),
    "guard_house": (guard_house, (9, 12, 11)),
    "clinic": (clinic, (13, 11, 13)),
    "ferry_house": (ferry_house, (11, 12, 11)),
    "trainers_house": (trainers_house, (13, 10, 10)),
    "leaders_hall": (leaders_hall, (9, 13, 12)),
    "ball_workshop": (ball_workshop, (13, 16, 11)),
    "trade_hall": (trade_hall, (13, 17, 11)),
    "school": (school, (11, 16, 11)),
    "storehouse": (storehouse, (11, 16, 11)),
    "orchard_house": (orchard_house, (13, 10, 11)),
    "fossil_lab": (fossil_lab, (11, 11, 11)),
    "flower_shop": (flower_shop, (13, 11, 11)),
    "ranch_house": (ranch_house, (13, 11, 12)),
    "schoolhouse": (schoolhouse, (9, 17, 12)),
    "inn_room": (inn_room, (11, 15, 11)),
    "mortuary": (mortuary, (11, 17, 11)),
    "tinkers_shop": (tinkers_shop, (11, 14, 11)),
    "sifting_shed": (sifting_shed, (13, 9, 11)),
    "compost_yard": (compost_yard, (13, 10, 11)),
    "winery": (winery, (10, 11, 11)),
    "tailors_shop": (tailors_shop, (9, 12, 11)),
    "print_shop": (print_shop, (9, 17, 10)),
    "jewellers_workshop": (jewellers_workshop, (9, 12, 11)),
    "crafters_workshop": (crafters_workshop, (10, 11, 11)),
    "pokemon_center": (pokemon_center, (11, 11, 11)),
    "camp_kitchen": (camp_kitchen, (12, 11, 11)),
    "berry_nursery": (berry_nursery, (12, 11, 11)),
    "daycare": (daycare, (12, 12, 11)),
    "gem_grotto": (gem_grotto, (9, 10, 11)),
}
