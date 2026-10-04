"""The Pokémon Center (ROADMAP 28.7): two tiers, in the Blueprint Table only with Cobblemon installed.

An original design, drawn to STYLE.md. Not the Healing Center (a small white clinic under a red gable with dark posts):
the Center is a wider, brighter hall with a glass front between white quartz pillars, a red band under a red hipped
roof, a flat quartz canopy over the door, and a light, open counter inside.

Pokémon Center (13 x 10 x 12):
- one hall, walls x 1-11, z 2-10, four blocks high; quartz pillars at the corners and every 3-4 blocks (front 1, 5, 7, 11:
  two glass bays and the door between them), a red terracotta band (y 5) round the top, a red nether brick hip roof
  with a 1-block overhang and a quartz cap;
- the front: two three-wide glass bays three high (one glass type, panes), a birch door between under a pane, a quartz
  canopy on two pillars over the door with a lantern;
- inside: a counter across the hall (z 7) with Cobblemon's Healing Machine in the middle (the nurse's place) and a PC
  at its east end; behind it shelves of potions (barrels below, coloured candles in rows on top: vials on a shelf);
  benches (birch stairs) along both side walls; tie beams across for the lanterns (and for the builder to reach the
  roof from);
- outside: flower beds either side of the canopy, a lamp post at each front corner.

Pokémon Center II (13 x 17 x 26): the hall plus a two-storey lodge behind (walls x 2-10, z 11-17): downstairs a trade
corner with a Shop Counter (hand the villager there a Poké Ball: a Pokémon Trader) and a ladder up; upstairs a lodge
with four beds (homes); a gable roof along z (ridge higher than the hall's, which it meets), a chimney; and a fenced
garden behind (z 19-25) with a Pasture Block in the middle, a path, bushes and flowers in beds.
"""
from kit import *

PC_WALL = Mix((8, "white_concrete"), (2, "polished_diorite"), seed=28)
PC_PILLAR = "quartz_pillar"
PC_BAND = "red_terracotta"
PC_ROOF = RED_NETHER_BRICK
PC_FLOOR = Mix((7, "smooth_quartz"), (3, "polished_diorite"), seed=29)
PC_BASE = Mix((6, "polished_andesite"), (3, "stone_bricks"), (1, "andesite"), seed=30)
CANDLES = ("red_candle", "pink_candle", "light_blue_candle", "lime_candle")


def pc_hall(b):
    skirt(b, 1, 2, 11, 10, ANDESITE)
    plinth(b, 1, 2, 11, 10, PC_BASE, floor=PC_FLOOR)
    walls(b, 1, 2, 11, 10, 1, 4, PC_WALL)
    front = [(x, 2) for x in (1, 5, 7, 11)]
    sides = [(x, z) for x in (1, 11) for z in (6, 10)]
    back = [(x, 10) for x in (4, 8)]
    for x, z in front + sides + back:
        for y in range(1, 5):
            b.set(x, y, z, PC_PILLAR, axis="y")
    for x in range(1, 12):
        for z in (2, 10):
            b.set(x, 5, z, PC_BAND)
    for z in range(2, 11):
        for x in (1, 11):
            b.set(x, 5, z, PC_BAND)
    # The glass front: two bays three wide, three high, the door between under a pane
    for x0 in (2, 8):
        for x in range(x0, x0 + 3):
            for y in (1, 2, 3):
                pane(b, x, y, 2)
            b.set(x, 4, 2, PC_WALL.at(x, 4, 2))
    door(b, 6, 1, 2, "birch_door", "south")
    pane(b, 6, 3, 2)
    # The sides and the back: windows centred in their bays, sills of quartz
    for z in (4, 8):
        window(b, 1, 2, z, "west", width=1, height=2, sill=QUARTZ)
        window(b, 11, 2, z, "east", width=1, height=2, sill=QUARTZ)
    for x in (2, 10):
        window(b, x, 2, 10, "south", width=1, height=2, sill=QUARTZ)
    # Inside: the counter across the hall, the Healing Machine in the middle, a PC at its east end
    for x in range(3, 10):  # x 2 stays open: the way behind the counter
        if x != 6:
            b.set(x, 1, 7, "white_concrete")
            slab(b, x, 2, 7, "smooth_quartz_slab")
    b.set(6, 1, 7, "cobblemon:healing_machine", facing="north")
    b.set(10, 1, 7, "cobblemon:pc", facing="north", part="bottom", on=False, waterlogged=False)
    b.set(10, 2, 7, "cobblemon:pc", facing="north", part="top", on=False, waterlogged=False)
    # Shelves of potions behind it: barrels below, candles in rows on top
    for i, x in enumerate((2, 3, 4, 8, 9, 10)):
        b.set(x, 1, 9, "barrel", facing="north", open=False)
        b.set(x, 2, 9, CANDLES[i % len(CANDLES)], candles=2 + i % 3, lit=False, waterlogged=False)
    b.set(5, 1, 9, "potted_azalea_bush")
    b.set(7, 1, 9, "potted_azalea_bush")
    # Benches along the side walls, a plant at each end
    for z in (3, 4, 5):
        stairs(b, 2, 1, z, "birch_stairs", "west")
        stairs(b, 10, 1, z, "birch_stairs", "east")
    for z in range(3, 7):
        b.set(6, 1, z, "red_carpet")
    # Tie beams across the hall under the roof, lanterns hanging from them
    for z in (4, 8):
        for x in range(2, 11):
            log(b, x, 5, z, "stripped_birch_log", axis="x")
        for x in (4, 8):
            lantern(b, x, 4, z, hanging=True)


def pc_roof(b):
    """A low red hipped roof (three courses, a 1-block overhang, the eaves on the band) round a flat white top."""
    top = hip_roof(b, 0, 12, 1, 11, 6, PC_ROOF, rings=3)
    box(b, 3, top - 1, 4, 9, top - 1, 8, "smooth_quartz")


def pc_canopy(b):
    """A flat quartz canopy out from the band over the door, a lantern under it, steps up to the door."""
    for x in range(4, 9):
        for z in (0, 1):
            slab(b, x, 4, z, "smooth_quartz_slab", top=True)
    for x in range(5, 8):
        b.set(x, 0, 0, "polished_andesite")
        b.set(x, 0, 1, "polished_andesite")
    b.set(6, 4, 1, "smooth_quartz")  # a full block in the canopy for the lantern to hang from
    lantern(b, 6, 3, 1, hanging=True)


def pc_garden_front(b):
    """Flower beds either side of the canopy along the plinth, a lamp post at each front corner."""
    for x in (1, 2, 3, 9, 10, 11):
        b.set(x, 0, 1, "grass_block", snowy=False)
    for x, plant in ((1, "red_tulip"), (2, "oxeye_daisy"), (3, "red_tulip"), (9, "red_tulip"), (10, "oxeye_daisy"),
                     (11, "red_tulip")):
        b.set(x, 1, 1, plant)
    for x in (0, 12):
        lamp_post(b, x, 0, 0, post="birch_fence", height=3)


def pokemon_center():
    """13 x 10 x 12: a bright hall with a glass front under a red hipped roof; Cobblemon's Healing Machine on the
    counter (the nurse's place), a PC beside it, shelves of potions behind, benches along the walls."""
    b = Build(13, 10, 12)
    pc_hall(b)
    pc_roof(b)
    pc_canopy(b)
    pc_garden_front(b)
    b.fill_air()
    return b


# --- Pokémon Center II: a lodge behind the hall and a garden with a Pasture Block ------------------------
def pc_lodge(b):
    """Two storeys behind the hall (walls x 2-10, z 11-17): a trade corner with a Shop Counter downstairs, four beds
    upstairs, a ladder between; a gable roof along z, a chimney on the east side."""
    skirt(b, 2, 11, 10, 17, ANDESITE)
    for x in range(1, 12):  # the skirt's north row would land on the hall's back wall: the hall's plinth stays
        b.set(x, 0, 10, PC_BASE.at(x, 0, 10))
    for z in range(11, 18):
        for x in (2, 10):
            b.set(x, 0, z, PC_BASE.at(x, 0, z))
    for x in range(2, 11):
        b.set(x, 0, 17, PC_BASE.at(x, 0, 17))
    box(b, 3, 0, 11, 9, 0, 16, PC_FLOOR)
    for y in range(1, 10):
        for z in range(11, 18):
            for x in (2, 10):
                b.set(x, y, z, PC_WALL.at(x, y, z))
        for x in range(3, 10):
            b.set(x, y, 17, PC_WALL.at(x, y, 17))
    for x, z in ((2, 14), (10, 14), (2, 17), (10, 17)):
        for y in range(1, 10):
            b.set(x, y, z, PC_PILLAR, axis="y")
    # The floor line between the storeys: a red band outside, birch planks inside
    for z in range(11, 18):
        for x in (2, 10):
            b.set(x, 5, z, PC_BAND)
    for x in range(2, 11):
        b.set(x, 5, 17, PC_BAND)
    box(b, 3, 5, 11, 9, 5, 16, "birch_planks")
    for x in range(3, 10):  # the upstairs' north wall, where the hall's back eave runs into it
        for y in range(6, 10):
            b.set(x, y, 11, PC_WALL.at(x, y, 11))
    for x in (2, 10):  # the hall's back windows now look into the lodge: walled up
        for y in (2, 3):
            b.set(x, y, 10, PC_WALL.at(x, y, 10))
    b.clear(3, 5, 16, 3, 5, 16)  # the ladder's hole
    for y in range(1, 6):
        b.set(3, y, 16, "ladder", facing="east", waterlogged=False)
    for z in range(11, 17):
        log(b, 6, 9, z, "stripped_birch_log", axis="z")  # a beam down the middle, for the lanterns and the builder
    # A door through from behind the counter
    b.set(6, 1, 10, "air")
    b.set(6, 2, 10, "air")
    door(b, 6, 1, 10, "birch_door", "south")
    # Downstairs: the trade corner, its counter facing the door, chests and a bench
    b.set(6, 1, 14, "aliveworkplace:shop_counter", facing="north")
    for x in (5, 7):
        b.set(x, 1, 14, "spruce_planks")
        slab(b, x, 2, 14, "spruce_slab")
    b.set(9, 1, 16, "barrel", facing="up", open=False)
    b.set(8, 1, 16, "barrel", facing="up", open=False)
    stairs(b, 9, 1, 12, "birch_stairs", "east")
    stairs(b, 9, 1, 13, "birch_stairs", "east")
    lantern(b, 6, 4, 13, hanging=True)
    # Upstairs: the lodge, four beds, a lantern
    for x, z in ((4, 11), (8, 11), (8, 14)):
        b.bed(x, 6, z, "red", facing="south")
    b.bed(4, 6, 13, "white", facing="south")
    b.set(5, 6, 16, "chest", facing="north", type="single", waterlogged=False)
    lantern(b, 6, 8, 14, hanging=True)
    # Windows in the bays on both storeys
    for z in (12, 15):
        for y in (2, 7):
            window(b, 2, y, z, "west", height=1, sill=QUARTZ, shutters="birch_trapdoor")
            window(b, 10, y, z, "east", height=1, sill=QUARTZ, shutters="birch_trapdoor")
    for x in (4, 8):
        window(b, x, 7, 17, "south", height=1, sill=QUARTZ)
    gable_roof(b, 1, 11, 10, 18, 10, PC_ROOF, axis="z", gable=PC_WALL, gable_at=(11, 17), eave_trim=BIRCH)
    for z in (11, 17):  # each gable: a king post and a window either side of it
        for y in range(10, 14):
            b.set(6, y, z, PC_PILLAR, axis="y")
        for x in (4, 8):
            pane(b, x, 11, z)
    chimney(b, 11, 15, 0, 13, BRICK_WALL_MIX)


def pc_garden(b):
    """A fenced garden behind the lodge (z 19-25): grass, a path to a Pasture Block in the middle, bushes and flowers
    in the corners, a gate on the west, lamps at the back corners and a back door out of the lodge."""
    walls(b, 1, 18, 11, 25, 0, 0, "stone_bricks")
    box(b, 2, 0, 19, 10, 0, 24, "grass_block", snowy=False)
    for z in range(18, 22):
        b.set(6, 0, z, "dirt_path")
    for x in range(1, 12):
        fence(b, x, 1, 25, "birch_fence")
    for z in range(19, 25):
        fence(b, 1, 1, z, "birch_fence")
        fence(b, 11, 1, z, "birch_fence")
    for x in (1, 11):
        fence(b, x, 1, 18, "birch_fence")
    b.set(1, 1, 22, "birch_fence_gate", facing="west", open=False, in_wall=False, powered=False)
    b.set(6, 1, 22, "cobblemon:pasture", facing="north", part="bottom", on=False, waterlogged=False)
    b.set(6, 2, 22, "cobblemon:pasture", facing="north", part="top", on=False, waterlogged=False)
    for x, z in ((2, 19), (10, 19), (2, 24), (10, 24)):
        b.set(x, 1, z, "azalea")
    for x, z, plant in ((3, 24, "poppy"), (4, 24, "cornflower"), (8, 24, "cornflower"), (9, 24, "poppy"),
                        (3, 19, "oxeye_daisy"), (9, 19, "oxeye_daisy")):
        b.set(x, 1, z, plant)
    for x in (1, 11):
        lantern(b, x, 2, 25)
    b.set(6, 2, 17, "air")
    door(b, 6, 1, 17, "birch_door", "north")
    stairs(b, 6, 0, 18, "polished_andesite_stairs", "north")


def pokemon_center_2():
    """Upgrade of the Pokémon Center: a two-storey lodge behind the hall (a trade corner with a Shop Counter, four beds
    upstairs) and a fenced garden with a Pasture Block. 13 x 17 x 26."""
    b = pokemon_center().grow(13, 17, 26)
    pc_lodge(b)
    pc_garden(b)
    b.fill_air()
    return b
