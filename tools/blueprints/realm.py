"""
The realm's two builds (ROADMAP 33.14): the Colony Camp a colony starts from, and the Royal Hall of a realm's capital.

Colony Camp (camp/colony_camp, 15 x 6 x 13, placed at once like the settlers' camp; front z = 0):
- A timber platform in the middle (x 5-9, z 6-10) under a striped canvas awning on four posts: the colony's Village Hall
  stands on it (the mod places the hall at (7, 1, 7) when the colony is founded; ColonyArrival.HALL_IN_CAMP).
- Two covered wagons either side (x 0-2 and 12-14, z 3-9), their tongues to the front; hay, pumpkins and a barrel aboard
  (the barrel at the tail, with steps up to it).
- The Blueprint Table at the front left with a lamp on a post (the builder's workstation); the Travel Post at the front
  right (placed by the mod at (10, 0, 3); ColonyArrival.POST_IN_CAMP) with a lantern on a post beside it.
- A campfire at the front with two log seats and a cooking pot (a cauldron on the fire's side); the supply chest at the
  back with crates (barrels and stacked wood) round it; three bedrolls at the back; a flagpole with a white banner for
  the village's arms.
- Palette: spruce and dark oak timber, white and red canvas, stone at the hearth. The camp is never built, so the canvas
  may use quartz stairs as the settlers' camp does.

Royal Hall (realm/royal_hall, 19 x 16 x 25):
- A stone great hall, walls x 2-16, z 3-19 (15 x 17), seven blocks to the eaves, pilasters of polished andesite every
  four blocks with stone buttresses outside, under a steep-feeling deepslate gable roof running front to back (ridge
  y 15). A gabled entrance porch (x 7-11, z 0-3) with a round-arched door and the realm's banners either side; a rose
  window in the front gable.
- Inside: a red runner up the middle to a raised dais at the back (two steps) with the seat of state under a canopy of
  banners; banners down both walls between tall windows (four bays a side, 4 high); a minstrels' gallery over the door
  (reached by a ladder), beams across the hall with lanterns hanging under them.
- The realm's vault behind the dais: a lower stone annex (x 6-12, z 19-23) under its own roof with four chests and
  barred windows, through a door in the back wall.
- Palette: stone brick (cracked here and there) on a stone-brick-and-cobble plinth, polished andesite trim, dark oak
  wood, deepslate tile roof; red-and-gold banners and runner for colour. One glass type (panes).

Royal Hall II (realm/royal_hall_2, 27 x 22 x 25): adds, on the east, a square keep tower (x 18-24, z 2-8) of four floors
with a crenellated top, and the treasury wing (x 17-25, z 10-19) — a long low stone room with chests along both walls,
a counting table and barred windows, under its own lower roof, through doors from the hall's east bays.
"""
from kit import *
from nbtlib import Compound, List, String

ROYAL_ROOF = DEEPSLATE_TILE
ROYAL_STONE = BRICK_WALL_MIX
ROYAL_TRIM = "polished_andesite"
ROYAL_WOOD = DARK_OAK
ROYAL_BANNER = [("yellow", "minecraft:triangle_bottom"), ("yellow", "minecraft:flower"), ("yellow", "minecraft:border")]


def royal_banner(b, x, y, z, facing):
    """A red banner with a gold chevron, flower and border, hanging on the wall block behind it."""
    b.set_nbt(x, y, z, Compound({"id": String("minecraft:banner"), "patterns": List[Compound]([
        Compound({"color": String(c), "pattern": String(p)}) for c, p in ROYAL_BANNER])}))
    b.set(x, y, z, "red_wall_banner", facing=facing)


# --- the Colony Camp --------------------------------------------------------------------------------------------------
def covered_wagon(b, x0, z0, outer):
    """A covered wagon three wide (x0..x0+2) and seven long (z0..z0+6), its tongue out to the front (z0 - 1, z0 - 2),
    steps up to its tail coming in from the {@code outer} side."""
    for x in range(x0, x0 + 3):
        for z in range(z0, z0 + 7):
            b.set(x, 1, z, "spruce_planks")
    for z in (z0 + 1, z0 + 5):  # two axles on four wheels
        for x in range(x0, x0 + 3):
            fence(b, x, 0, z, "dark_oak_fence")
        trapdoor(b, x0 - 1, 0, z, "dark_oak_trapdoor", "west", open_=True)
        trapdoor(b, x0 + 3, 0, z, "dark_oak_trapdoor", "east", open_=True)
    for z in (z0, z0 + 6):
        for x in (x0, x0 + 2):
            fence(b, x, 2, z, "spruce_fence")
    for z in range(z0 + 1, z0 + 6):  # the canvas, rounded over the top
        b.set(x0, 2, z, "white_wool")
        b.set(x0 + 2, 2, z, "white_wool")
        b.set(x0 + 1, 3, z, "white_wool")
        stairs(b, x0, 3, z, SMOOTH_QUARTZ, "east")
        stairs(b, x0 + 2, 3, z, SMOOTH_QUARTZ, "west")
    fence(b, x0 + 1, 1, z0 - 1, "dark_oak_fence")
    fence(b, x0 + 1, 1, z0 - 2, "dark_oak_fence")
    # the load: hay and a pumpkin, the barrel at the open tail end where a player can reach it (B424: between the hay
    # and the pumpkin it couldn't be opened)
    b.set(x0 + 1, 2, z0 + 2, "hay_block", axis="z")
    b.set(x0 + 1, 2, z0 + 3, "hay_block", axis="z")
    b.set(x0 + 1, 2, z0 + 4, "pumpkin")
    b.set(x0 + 1, 2, z0 + 5, "barrel", facing="south", open=False)
    # steps up to the tail, turning in from the outer side: a stair onto a crate's stair, level with the wagon's bed
    out = -1 if outer == "west" else 1
    b.set(x0 + 1, 0, z0 + 7, "stripped_spruce_wood", axis="y")
    stairs(b, x0 + 1, 1, z0 + 7, SPRUCE, "north")
    stairs(b, x0 + 1 + out, 0, z0 + 7, SPRUCE, OPP[outer])


def colony_camp():
    """15 x 6 x 13: a colony's first camp — two covered wagons, the Village Hall under a canvas awning on a timber
    platform, the Blueprint Table with a lamp, supply crates round the chest, a campfire with log seats and a cooking
    pot, three bedrolls, the Travel Post with a lantern and a flagpole. Placed at once (camp/ isn't in the Blueprint
    Table); the mod adds the hall and the post."""
    b = Build(15, 6, 13)
    # The platform and its awning
    for x in range(5, 10):
        for z in range(6, 11):
            b.set(x, 0, z, "spruce_planks")
    for x in range(5, 10):
        slab(b, x, 0, 11, SPRUCE)
    for x in (6, 8):
        stairs(b, x, 0, 5, SPRUCE, "north")
    stairs(b, 7, 0, 5, SPRUCE, "north")
    for x, z in ((5, 6), (9, 6), (5, 10), (9, 10)):
        log(b, x, 0, z, "stripped_dark_oak_log")
        for y in (1, 2, 3):
            fence(b, x, y, z, "dark_oak_fence")
    for x in range(4, 11):
        for z in range(5, 12):
            b.set(x, 4, z, "red_wool" if x % 2 == 0 else "white_wool")
    for x in range(4, 11):  # the canvas's scalloped edges, front and back
        trapdoor(b, x, 3, 4, "spruce_trapdoor", "north", open_=True)
        trapdoor(b, x, 3, 12, "spruce_trapdoor", "south", open_=True)
    lantern(b, 7, 3, 8, hanging=True)
    # The hall's spot (7, 1, 7) stays open; a little rug and seat around it
    b.set(7, 1, 6, "red_carpet")
    b.set(6, 1, 9, "potted_fern")
    b.set(8, 1, 9, "potted_dandelion")
    # Two covered wagons either side
    covered_wagon(b, 1, 4, "west")
    covered_wagon(b, 11, 4, "east")
    # The Blueprint Table at the front left, a lamp on a post
    b.set(3, 0, 1, "aliveworkplace:blueprint_table", facing="north")
    lamp_post(b, 4, 0, 0, "spruce_fence", height=2)
    # The Travel Post's spot (10, 0, 3) with a lantern on a post beside it
    lamp_post(b, 11, 0, 2, "dark_oak_fence", height=2)
    # The campfire with log seats and a cooking pot
    b.set(7, 0, 2, "campfire", facing="north", lit=True, signal_fire=False, waterlogged=False)
    log(b, 5, 0, 2, "stripped_spruce_log", axis="z")
    log(b, 9, 0, 2, "stripped_spruce_log", axis="z")
    b.set(7, 0, 0, "cauldron")
    # The supply chest at the back, crates round it
    b.set_nbt(7, 0, 12, Compound({"id": String("minecraft:chest")}))
    b.set(7, 0, 12, "chest", facing="north", type="single", waterlogged=False)
    b.set(6, 0, 12, "barrel", facing="up", open=False)
    b.set(8, 0, 12, "barrel", facing="up", open=False)
    b.set(6, 1, 12, "stripped_spruce_wood", axis="y")
    b.set(5, 0, 12, "stripped_spruce_wood", axis="y")
    b.set(9, 0, 12, "hay_block", axis="x")
    # Three bedrolls at the back corners
    b.bed(1, 0, 12, "brown", facing="west")
    b.bed(3, 0, 12, "green", facing="west")
    b.bed(13, 0, 12, "brown", facing="east")
    # The flagpole at the back right, a white banner on top for the village's arms
    log(b, 11, 0, 11, "stripped_dark_oak_log")
    for y in range(1, 5):
        fence(b, 11, y, 11, "dark_oak_fence")
    b.set(11, 5, 11, "white_banner", rotation=8)
    b.fill_air()
    return b


# --- the Royal Hall ---------------------------------------------------------------------------------------------------
HALL_X0, HALL_X1, HALL_Z0, HALL_Z1 = 2, 16, 3, 19
PILASTERS_Z = (3, 7, 11, 15, 19)
SIDE_BAYS_Z = (5, 9, 13, 17)


def royal_hall_shell(b):
    """The great hall, its porch and the vault: plinths, walls, pilasters and buttresses, windows, roofs."""
    # Plinths and floors
    plinth(b, HALL_X0, HALL_Z0, HALL_X1, HALL_Z1, FOUNDATION_MIX, floor="polished_andesite")
    plinth(b, 7, 1, 11, 3, FOUNDATION_MIX, floor="polished_andesite")
    plinth(b, 6, 19, 12, 23, FOUNDATION_MIX, floor="spruce_planks")
    for x in range(3, 16):  # a chequered floor down the hall
        for z in range(4, 19):
            b.set(x, 0, z, "polished_andesite" if (x + z) % 2 == 0 else "stone_bricks")
    # Walls: stone brick, seven blocks to the eaves
    walls(b, HALL_X0, HALL_Z0, HALL_X1, HALL_Z1, 1, 7, ROYAL_STONE)
    walls(b, 7, 1, 11, 3, 1, 4, ROYAL_STONE)
    walls(b, 6, 19, 12, 23, 1, 4, ROYAL_STONE)
    # Pilasters on the side walls, buttresses outside them; quoins up the corners
    for z in PILASTERS_Z:
        for x, out in ((HALL_X0, "west"), (HALL_X1, "east")):
            box(b, x, 1, z, x, 7, z, ROYAL_TRIM)
            ox = x - 1 if out == "west" else x + 1
            for y in (0, 1, 2):
                b.set(ox, y, z, STONE_MIX.at(ox, y, z))
            stairs(b, ox, 3, z, STONE_BRICK, out)
    for x in (HALL_X0 + 4, HALL_X1 - 4):
        box(b, x, 1, HALL_Z0, x, 7, HALL_Z0, ROYAL_TRIM)
        box(b, x, 1, HALL_Z1, x, 7, HALL_Z1, ROYAL_TRIM)
    beam_ring(b, HALL_X0, HALL_Z0, HALL_X1, HALL_Z1, 7, "stripped_dark_oak_log")
    # Tall windows, four bays a side, four high
    for z in SIDE_BAYS_Z:
        window(b, HALL_X0, 2, z, "west", height=4, sill=STONE_BRICK, lintel=STONE_BRICK)
        window(b, HALL_X1, 2, z, "east", height=4, sill=STONE_BRICK, lintel=STONE_BRICK)
    # Front: windows either side of the porch, under the gallery
    for x in (4, 14):
        window(b, x, 2, HALL_Z0, "north", height=3, sill=STONE_BRICK, lintel=STONE_BRICK)
    # Back: windows either side of the vault
    for x in (4, 14):
        window(b, x, 2, HALL_Z1, "south", height=4, sill=STONE_BRICK, lintel=STONE_BRICK)
    # The porch: a round arch at the front, the door into the hall behind it
    b.clear(8, 1, 1, 10, 3, 1)
    stairs(b, 8, 3, 1, STONE_BRICK, "east", top=True)
    stairs(b, 10, 3, 1, STONE_BRICK, "west", top=True)
    b.clear(9, 1, 2, 9, 2, 2)
    b.clear(8, 1, 2, 8, 2, 2)
    b.clear(10, 1, 2, 10, 2, 2)
    door(b, 9, 1, HALL_Z0, "dark_oak_door", "north")
    b.set(9, 3, HALL_Z0, "chiseled_stone_bricks")
    # The vault's door from the dais's side, its barred windows
    door(b, 11, 1, HALL_Z1, "dark_oak_door", "south")
    for z in (21,):
        b.set(6, 2, z, "iron_bars")
        b.set(12, 2, z, "iron_bars")
    b.set(9, 2, 23, "iron_bars")
    # Roofs: the hall's gable front to back, the porch's and the vault's lower gables dying into it
    roofs(b,
          lambda t: gable_roof(t, 1, 17, 2, 20, 7, ROYAL_ROOF, axis="z", gable=ROYAL_STONE, gable_at=(3, 19),
                               ridge=ROYAL_ROOF, eave_trim=ROYAL_WOOD),
          lambda t: gable_roof(t, 6, 12, 0, 4, 5, ROYAL_ROOF, axis="z", gable=ROYAL_STONE, gable_at=(1,),
                               ridge=ROYAL_ROOF, eave_trim=ROYAL_WOOD),
          lambda t: gable_roof(t, 5, 13, 18, 24, 5, ROYAL_ROOF, axis="z", gable=ROYAL_STONE, gable_at=(23,),
                               ridge=ROYAL_ROOF, eave_trim=ROYAL_WOOD))
    # The rose window in the front gable, and a small one at the back
    for x, y in ((9, 10), (8, 11), (10, 11), (9, 12), (9, 11)):
        pane(b, x, y, HALL_Z0)
    pane(b, 9, 10, HALL_Z1)
    pane(b, 9, 11, HALL_Z1)
    b.set(9, 13, HALL_Z0, "chiseled_stone_bricks")
    # The gables: a string course over the eaves, the pilasters carried up, a stone surround to the rose window
    for z in (HALL_Z0, HALL_Z1):
        for x in range(3, 16):
            if b.get(x, 8, z) is not None:
                b.set(x, 8, z, ROYAL_TRIM)
        for x in (6, 12):
            for y in (9, 10):
                if b.get(x, y, z) is not None:
                    b.set(x, y, z, ROYAL_TRIM)
    for x, y in ((8, 10), (10, 10), (8, 12), (10, 12)):
        b.set(x, y, HALL_Z0, "chiseled_stone_bricks")
    for z in range(4, 19):  # a collar beam under the ridge (the builder stands on it to lay the ridge)
        log(b, 9, 12, z, "stripped_dark_oak_log", axis="z")


def royal_hall_inside(b):
    """The runner, the dais and seat of state, banners down the walls, the gallery, the beams and lanterns, the vault."""
    # A red runner from the door to the dais
    for z in range(5, 15):  # a block in from the door: on a carpet under its lintel a villager hits its head (B173)
        b.set(9, 1, z, "red_carpet")
    # The dais: two steps up across the back of the hall
    for x in range(4, 15):
        stairs(b, x, 1, 15, STONE_BRICK, "north")
        for z in (16, 17, 18):
            b.set(x, 1, z, "polished_andesite")
        if 7 <= x <= 11:
            stairs(b, x, 2, 16, STONE_BRICK, "north")
            for z in (17, 18):
                b.set(x, 2, z, "polished_andesite")
    for x in (3, 15):
        for z in (15, 16, 17, 18):
            b.set(x, 1, z, "polished_andesite")
    # The seat of state: a dark oak throne with arms and a gold back, banners behind
    stairs(b, 9, 3, 17, ROYAL_WOOD, "north")
    trapdoor(b, 8, 3, 17, "dark_oak_trapdoor", "west", open_=True)
    trapdoor(b, 10, 3, 17, "dark_oak_trapdoor", "east", open_=True)
    b.set(9, 3, 18, "gold_block")
    b.set(9, 4, 18, "gold_block")
    royal_banner(b, 7, 5, 18, "north")
    royal_banner(b, 11, 5, 18, "north")
    royal_banner(b, 9, 6, 18, "north")
    for x in (7, 11):
        b.set(x, 3, 17, "candle", candles=3, lit=True, waterlogged=False)
    b.set(4, 2, 17, "potted_poppy")
    b.set(14, 2, 17, "potted_poppy")
    # Banners down both walls on the pilasters
    for z in (7, 11, 15):
        royal_banner(b, HALL_X0 + 1, 5, z, "east")
        royal_banner(b, HALL_X1 - 1, 5, z, "west")
    # Benches along the walls for the court
    for z in range(5, 14):
        if z in (7, 11):
            continue
        stairs(b, 3, 1, z, ROYAL_WOOD, "east")
        stairs(b, 15, 1, z, ROYAL_WOOD, "west")
    # The gallery over the door: a dark oak floor with a rail, a ladder up the front wall
    for x in range(3, 16):
        for z in (4, 5):
            slab(b, x, 4, z, ROYAL_WOOD, top=True)
        fence(b, x, 5, 6, "dark_oak_fence")
    for x in (5, 13):
        for y in (1, 2, 3):
            fence(b, x, y, 6, "dark_oak_fence")
        slab(b, x, 4, 6, ROYAL_WOOD, top=True)
    b.clear(3, 4, 4, 3, 4, 4)
    for y in range(1, 6):
        b.set(3, y, 4, "ladder", facing="south", waterlogged=False)
    b.set(3, 5, 6, "air")
    stairs(b, 7, 5, 4, ROYAL_WOOD, "south")
    stairs(b, 11, 5, 4, ROYAL_WOOD, "south")
    # Tie beams across the hall at the wall plate, with lanterns hanging under them (in a builder's reach)
    for z in (9, 13):
        for x in range(3, 16):
            log(b, x, 7, z, "stripped_dark_oak_log", axis="x")
        for x in (6, 12):
            lantern(b, x, 6, z, hanging=True)
    lantern(b, 9, 11, 16, hanging=True)
    # Wall lights by the dais
    for x in (4, 14):
        lantern(b, x, 2, 16)
    # The vault: four chests along its walls, a lantern
    for x, z, f in ((7, 21, "east"), (7, 22, "east"), (11, 21, "west"), (11, 22, "west")):
        b.set(x, 1, z, "chest", facing=f, type="single", waterlogged=False)
    b.set(9, 1, 22, "barrel", facing="up", open=False)
    lantern(b, 9, 2, 22)  # on the barrel (a chest's lid won't hold one)
    for z in range(20, 24):  # a collar beam under the vault's ridge (the builder stands on it to lay the ridge)
        log(b, 9, 7, z, "stripped_dark_oak_log", axis="z")


def royal_hall_front(b):
    """The way in: a step, the realm's banners either side of the arch, lamps, flower beds."""
    stairs(b, 9, 0, 0, STONE_BRICK, "north")
    for x in (8, 10):
        b.set(x, 0, 0, "polished_andesite")
    royal_banner(b, 8, 3, 0, "north")
    royal_banner(b, 10, 3, 0, "north")
    for x in (5, 13):
        lamp_post(b, x, 0, 1, "dark_oak_fence", height=2)
    flower_bed(b, 2, 1, 4, 1, 0, ["red_tulip", "dandelion", "red_tulip"])
    flower_bed(b, 14, 1, 16, 1, 0, ["red_tulip", "dandelion", "red_tulip"])


def royal_hall():
    """19 x 16 x 25: a stone great hall with a raised dais and the seat of state, banners down the walls, a gallery,
    tall windows and the realm's vault behind. Not in the Blueprint Table: the capital's Realm tab draws it."""
    b = Build(19, 16, 25)
    royal_hall_shell(b)
    royal_hall_inside(b)
    royal_hall_front(b)
    b.fill_air()
    return b


def royal_hall_2():
    """Upgrade of the Royal Hall (same origin and front): a keep tower and a treasury wing on the east. 27 x 22 x 25."""
    base = royal_hall()
    b = base.grow(27, 22, 25)
    b.entities = list(getattr(base, "entities", []))
    # The east side's buttresses and sills where the wing and tower stand come off
    b.clear(17, 0, 2, 17, 7, 19)
    # --- the treasury wing: x 17-25, z 10-19, through doors where the hall's two back east windows were
    X0, X1, Z0, Z1 = 17, 25, 10, 19
    plinth(b, X0, Z0, X1, Z1, FOUNDATION_MIX, floor="spruce_planks")
    walls(b, X0, Z0, X1, Z1, 1, 5, ROYAL_STONE)
    for x, z in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1), (21, Z0), (21, Z1)):
        box(b, x, 1, z, x, 5, z, ROYAL_TRIM)
    beam_ring(b, X0, Z0, X1, Z1, 5, "stripped_dark_oak_log")
    for z in (13, 17):  # the hall's east windows become doors into the wing
        for y in range(2, 6):
            b.set(HALL_X1, y, z, ROYAL_STONE.at(HALL_X1, y, z))
        door(b, HALL_X1, 1, z, "dark_oak_door", "east")
        b.set(X0, 1, z, "air")
        b.set(X0, 2, z, "air")
    b.clear(X0, 1, Z0 + 1, X0, 4, Z1 - 1)  # one room with the hall's wall
    for z in (12, 15, 18):
        window(b, X1, 2, z, "east", height=2, glass="iron_bars", sill=STONE_BRICK)
    for x in (19, 23):
        window(b, x, 2, Z1, "south", height=2, glass="iron_bars", sill=STONE_BRICK)
    # chests along both walls, a counting table with candles in the middle
    for x in (19, 20, 22, 23):
        b.set(x, 1, Z0 + 1, "chest", facing="south", type="single", waterlogged=False)
    for z in (12, 13, 15, 16):
        b.set(X1 - 1, 1, z, "chest", facing="west", type="single", waterlogged=False)
    for x in (20, 21, 22):
        slab(b, x, 1, 15, ROYAL_WOOD, top=True)
    chair(b, 21, 1, 16, ROYAL_WOOD, "north")
    b.set(20, 2, 15, "candle", candles=3, lit=True, waterlogged=False)
    b.set(22, 2, 15, "potted_fern")
    lantern(b, 21, 2, 15)
    b.set(18, 1, 18, "barrel", facing="up", open=False)
    lantern(b, 18, 2, 18)
    roofs(b, lambda t: gable_roof(t, X0, X1 + 1, Z0 - 1, Z1 + 1, 6, ROYAL_ROOF, axis="z", gable=ROYAL_STONE,
                                  gable_at=(Z0, Z1), ridge=ROYAL_ROOF, eave_trim=ROYAL_WOOD))
    for z in range(Z0 + 1, Z1):  # a collar beam under the wing's ridge (the builder stands on it to lay the ridge)
        for x in (21, 22):
            log(b, x, 7, z, "stripped_dark_oak_log", axis="z")
    # --- the keep tower: x 18-24, z 2-8, four floors and a crenellated top
    T0, T1, U0, U1 = 18, 24, 2, 8
    plinth(b, T0, U0, T1, U1, FOUNDATION_MIX, floor="spruce_planks")
    skirt(b, T0, U0, T1, U1, STONE_BRICK)
    walls(b, T0, U0, T1, U1, 1, 18, ROYAL_STONE)
    for x, z in ((T0, U0), (T1, U0), (T0, U1), (T1, U1)):
        box(b, x, 1, z, x, 18, z, ROYAL_TRIM)
    for y in (5, 9, 13):  # floors
        box(b, T0 + 1, y, U0 + 1, T1 - 1, y, U1 - 1, "spruce_planks")
        b.clear(T0 + 1, y, U1 - 1, T0 + 1, y, U1 - 1)
    for y in range(1, 18):  # a ladder up the back corner
        b.set(T0 + 1, y, U1 - 1, "ladder", facing="north", waterlogged=False)
    box(b, T0 + 1, 17, U0 + 1, T1 - 1, 17, U1 - 1, "spruce_planks")
    b.set(T0 + 1, 17, U1 - 1, "ladder", facing="north", waterlogged=False)
    b.clear(T0 + 1, 18, U0 + 1, T1 - 1, 18, U1 - 1)
    # crenellations on the wall top, a cornice of upside-down stairs below
    for x in range(T0, T1 + 1):
        for z in (U0, U1):
            if (x - T0) % 2 == 0:
                b.set(x, 19, z, ROYAL_STONE.at(x, 19, z))
    for z in range(U0, U1 + 1):
        for x in (T0, T1):
            if (z - U0) % 2 == 0:
                b.set(x, 19, z, ROYAL_STONE.at(x, 19, z))
    for x in range(T0, T1 + 1):
        stairs(b, x, 17, U0 - 1, STONE_BRICK, "north", top=True)
        stairs(b, x, 17, U1 + 1, STONE_BRICK, "south", top=True)
    for z in range(U0, U1 + 1):
        stairs(b, T1 + 1, 17, z, STONE_BRICK, "east", top=True)
    # the realm's banner on a pole on the roof
    for y in (18, 19, 20):
        fence(b, 21, y, 5, "dark_oak_fence")
    b.set(21, 21, 5, "red_banner", rotation=8)
    lantern(b, 19, 18, 3)
    lantern(b, 23, 18, 3)
    # the tower's door from the hall (where the front east window was), windows up its faces
    for y in range(2, 6):
        b.set(HALL_X1, y, 5, ROYAL_STONE.at(HALL_X1, y, 5))
    door(b, HALL_X1, 1, 5, "dark_oak_door", "east")
    for z in (5, 13):  # the bench seats inside the new doors come off: a step a villager can't take under a lintel (B173)
        b.set(HALL_X1 - 1, 1, z, "air")
    b.set(T0, 1, 5, "air")
    b.set(T0, 2, 5, "air")
    door(b, 21, 1, U0, "dark_oak_door", "north")
    stairs(b, 21, 0, U0 - 1, STONE_BRICK, "north")
    for y in (6, 10, 14):
        window(b, 21, y, U0, "north", height=2, sill=STONE_BRICK)
        window(b, T1, y, 5, "east", height=2, sill=STONE_BRICK)
    window(b, T1, 2, 5, "east", height=2, sill=STONE_BRICK)
    # inside: a guard room below, a council room, the archive, the watch room
    royal_banner(b, 21, 3, U1 - 1, "north")
    b.set(T1 - 1, 6, U1 - 1, "bookshelf")
    b.set(T1 - 1, 7, U1 - 1, "bookshelf")
    b.set(T1 - 1, 10, U0 + 1, "chest", facing="west", type="single", waterlogged=False)
    lantern(b, 21, 4, 5, hanging=True)
    lantern(b, 21, 8, 5, hanging=True)
    lantern(b, 21, 12, 5, hanging=True)
    lantern(b, 21, 16, 5, hanging=True)
    b.fill_air()
    return b
