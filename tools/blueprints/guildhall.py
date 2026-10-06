"""
The Guildhall (ROADMAP 30.17): where a village's chartered guild meets. A guild is founded once a finished Guildhall
stands in the village (any style: the Blueprint Table's styles swap its woods and stones).

Design (after the Minecraft Architect review):
- I, 19 x 15 x 14: a two-storey hall, walls x 1-17, z 4-12 — a stone ground storey with a timber-framed plaster storey
  over it (spruce posts at x 1, 4, 7, 11, 14, 17, beams at y 5 and 9), under a slate roof running along the front (eaves
  y 9, ridge y 14). A two-storey entrance bay at the middle of the front (x 7-11, z 1-4) under its own cross gable
  (ridge y 12, dying into the main slope); a guild banner either side of the door, lamps at the step. A stone chimney
  up the back wall. Inside: a long table down the hall with benches either side and a high seat at each end, the hearth
  at the back with the guild charter framed over the mantel, banners either side of it; stairs up the west wall to the
  guild's archive (shelves, a desk, chests for the records).
- II, 26 x 20 x 14: a stone tower wing on the east (walls x 18-24, z 3-9, a block proud of the front), through doors
  where the hall's east windows were: the meeting room on its ground floor (a round table with four chairs), a chart
  room over it level with the archive, a ringing loft, and an open belfry with the guild bell under a slate hip roof.
- Palette: stone brick (cracked here and there) on a mixed stone plinth, spruce frame, white plaster, slate roof with
  spruce soffits; blue-and-gold banners and a red runner for colour. One glass type (panes).
"""
from kit import *
from nbtlib import Byte, Compound, Double, Int, List, String

GUILD_ROOF = DEEPSLATE_TILE
GUILD_FRAME = "spruce_log"
GUILD_PLASTER = Mix((8, "white_concrete"), (2, "polished_diorite"), seed=41)
GUILD_STONE = BRICK_WALL_MIX
GUILD_BANNER = [("yellow", "minecraft:rhombus"), ("blue", "minecraft:circle"), ("yellow", "minecraft:border")]
CHARTER_ITEM = "aliveworkplace:guild_charter"

HALL_POSTS_X = (1, 4, 7, 11, 14, 17)
HALL_POSTS_Z = (4, 7, 9, 12)


def guild_banner(b, x, y, z, facing, wall=True):
    """A blue guild banner with a gold lozenge, roundel and border (a wall banner hanging on the block behind it)."""
    b.set_nbt(x, y, z, Compound({"id": String("minecraft:banner"), "patterns": List[Compound]([
        Compound({"color": String(c), "pattern": String(p)}) for c, p in GUILD_BANNER])}))
    if wall:
        b.set(x, y, z, "blue_wall_banner", facing=facing)
    else:
        b.set(x, y, z, "blue_banner", rotation=facing)


def framed_item(b, x, y, z, facing, item=None):
    """An item frame in the air block (x, y, z), hanging on the block behind it and looking {@code facing}. A builder
    always puts it up empty (blueprints never hand out items); placed as a structure it shows {@code item}."""
    dx, dz = DIRS[facing]
    nbt = Compound({
        "id": String("minecraft:item_frame"),
        "TileX": Int(x), "TileY": Int(y), "TileZ": Int(z),  # its own block, as vanilla saves it (StructureTemplateMixin)
        "Facing": Byte({"north": 2, "south": 3, "west": 4, "east": 5}[facing]),
    })
    if item:
        nbt["Item"] = Compound({"id": String(item), "count": Int(1)})
    b.entities = getattr(b, "entities", [])
    b.entities.append(Compound({
        "pos": List[Double]([Double(x + 0.5 - dx * 0.46875), Double(y + 0.5), Double(z + 0.5 - dz * 0.46875)]),
        "blockPos": List[Int]([Int(x), Int(y), Int(z)]),
        "nbt": nbt,
    }))


def table_slab(b, x, z, y=1):
    slab(b, x, y, z, DARK_OAK, top=True)


def guildhall_hall(b):
    """The hall and its entrance bay: walls, frame, floors, windows, roofs (no furniture)."""
    # Plinth and floors
    for x in range(1, 18):  # a stone skirt round the back and sides roots it
        stairs(b, x, 0, 13, STONE_BRICK, "north")
    plinth(b, 1, 4, 17, 12, FOUNDATION_MIX, floor="spruce_planks")
    plinth(b, 7, 1, 11, 4, FOUNDATION_MIX, floor="spruce_planks")
    # Ground storey: stone, quoins of polished andesite at the corners
    walls(b, 1, 4, 17, 12, 1, 4, GUILD_STONE)
    walls(b, 7, 1, 11, 4, 1, 4, GUILD_STONE)
    for x, z in ((1, 4), (17, 4), (1, 12), (17, 12), (7, 1), (11, 1)):
        box(b, x, 1, z, x, 4, z, "polished_andesite")
    b.clear(8, 1, 4, 10, 3, 4)  # the entrance bay opens into the hall under an arch
    for x, face in ((8, "east"), (10, "west")):
        stairs(b, x, 3, 4, STONE_BRICK, face, top=True)
    b.set(9, 3, 4, "air")
    # Floor beams and the upper floor
    beam_ring(b, 1, 4, 17, 12, 5, GUILD_FRAME)
    beam_ring(b, 7, 1, 11, 4, 5, GUILD_FRAME)
    box(b, 2, 5, 5, 16, 5, 11, "spruce_planks")
    box(b, 8, 5, 2, 10, 5, 3, "spruce_planks")
    b.clear(2, 5, 7, 2, 5, 11)  # the stairwell
    # Upper storey: plaster between spruce posts
    walls(b, 1, 4, 17, 12, 6, 8, GUILD_PLASTER)
    walls(b, 7, 1, 11, 4, 6, 8, GUILD_PLASTER)
    posts(b, [(x, z) for x in HALL_POSTS_X for z in (4, 12)] + [(x, z) for x in (1, 17) for z in HALL_POSTS_Z]
          + [(7, 1), (11, 1)], 6, 8, GUILD_FRAME)
    b.clear(8, 6, 4, 10, 8, 4)  # the archive runs on into the entrance bay
    # Windows. Front: stone sills and lintels below, flower boxes above
    for x in (2, 5, 12, 15):
        window(b, x, 2, 4, "north", width=2, height=2, sill=STONE_BRICK, lintel=STONE_BRICK)
        window(b, x, 7, 4, "north", width=2, sill=SPRUCE)
    window(b, 8, 6, 1, "north", width=3, height=2, flowers=("spruce_trapdoor", ["potted_cornflower", "potted_dandelion", "potted_cornflower"]))
    # Back: the same bays, the hearth in the middle one
    for x in (3, 6, 13, 16):  # (a south-looking window runs west from x)
        window(b, x, 2, 12, "south", width=2, height=2, sill=STONE_BRICK, lintel=STONE_BRICK)
        window(b, x, 7, 12, "south", width=2, shutters="spruce_trapdoor", sill=SPRUCE)
    # Sides: two bays of two and a single one between
    for x, out, first in ((1, "west", (6, 11)), (17, "east", (5, 10))):
        for z in first:
            window(b, x, 2, z, out, width=2, height=2, sill=STONE_BRICK, lintel=STONE_BRICK)
            window(b, x, 7, z, out, width=2, shutters="spruce_trapdoor", sill=SPRUCE)
        window(b, x, 2, 8, out, height=2, sill=STONE_BRICK, lintel=STONE_BRICK)
        window(b, x, 7, 8, out, sill=SPRUCE)
    # Roofs: the main gable along the front, the entrance bay's cross gable dying into it
    roofs(b,
          lambda t: gable_roof(t, 0, 18, 3, 13, 9, GUILD_ROOF, axis="x", gable=GUILD_PLASTER, gable_at=(1, 17),
                               ridge=GUILD_ROOF, eave_trim=SPRUCE),
          lambda t: gable_roof(t, 6, 12, 0, 7, 9, GUILD_ROOF, axis="z", gable=GUILD_PLASTER, gable_at=(1,),
                               ridge=GUILD_ROOF, eave_trim=SPRUCE))
    beam_ring(b, 1, 4, 17, 12, 9, GUILD_FRAME)  # (the gables were filled over the top beam)
    for x in range(7, 12):
        log(b, x, 9, 1, GUILD_FRAME, axis="x")
    for x in (1, 17):  # timbered gables: two studs, a window and a king post under the ridge
        for z in (6, 10):
            for y in (10, 11):
                log(b, x, y, z, GUILD_FRAME)
        pane(b, x, 10, 8)
        pane(b, x, 11, 8)
        for y in (12, 13):
            log(b, x, y, 8, GUILD_FRAME)
    pane(b, 9, 10, 1)  # the entrance gable: a window and a king post
    log(b, 9, 11, 1, GUILD_FRAME)
    for x in range(2, 17):  # a collar beam under the ridge (the builder stands on it to lay the ridge)
        log(b, x, 12, 8, GUILD_FRAME, axis="x")


def guildhall_hearth(b):
    """The hearth in the middle of the back wall: a stone chimney breast with a fire in it, a spruce mantel, the guild
    charter framed over it and a candle either side; outside, the chimney up the back wall."""
    for x in (8, 9, 10):
        for y in range(1, 5):
            b.set(x, y, 11, GUILD_STONE.at(x, y, 11))
    b.set(9, 1, 11, "campfire", facing="north", lit=True, signal_fire=False, waterlogged=False)
    b.set(9, 2, 11, "chiseled_stone_bricks")
    for x in (8, 9, 10):
        stairs(b, x, 3, 10, SPRUCE, "south", top=True)
    for x in (8, 10):
        b.set(x, 4, 10, "candle", candles=2, lit=True, waterlogged=False)
    framed_item(b, 9, 4, 10, "north", CHARTER_ITEM)
    # the chimney: a broad stone foot, shoulders, then a stack past the eaves with a smoking fire on top
    for x in (8, 9, 10):
        for y in range(0, 5):
            b.set(x, y, 13, STONE_MIX.at(x, y, 13))
    stairs(b, 8, 5, 13, COBBLE, "east")
    stairs(b, 10, 5, 13, COBBLE, "west")
    chimney(b, 9, 13, 5, 13)


def guildhall_rooms(b):
    """The long table, benches, banners and lamps of the hall; the stairs and the archive above."""
    # The long table down the hall: dark oak, nine long, two wide, benches either side, a high seat at each end
    for x in range(5, 14):
        for z in (7, 8):
            table_slab(b, x, z)
        stairs(b, x, 1, 6, SPRUCE, "north")
        stairs(b, x, 1, 9, SPRUCE, "south")
    for z in (7, 8):
        stairs(b, 4, 1, z, DARK_OAK, "west")
        stairs(b, 14, 1, z, DARK_OAK, "east")
    for x in (6, 12):
        b.set(x, 2, 7, "candle", candles=3, lit=True, waterlogged=False)
    b.set(9, 2, 8, "potted_cornflower")
    b.set(9, 2, 7, "candle", candles=1, lit=True, waterlogged=False)
    for x in (6, 9, 12):
        lantern(b, x, 4, 8 if x == 9 else 7, hanging=True)
    # Banners either side of the hearth; a red runner from the door to the table
    guild_banner(b, 7, 4, 11, "north")
    guild_banner(b, 11, 4, 11, "north")
    for z in (2, 3, 4, 5):
        b.set(9, 1, z, "red_carpet")
    for x, z in ((16, 5), (16, 11), (2, 5)):
        b.set(x, 1, z, "potted_fern")
    b.set(16, 1, 7, "chest", facing="west", type="single", waterlogged=False)
    # Stairs up the west wall, a rail round the stairwell
    for i, z in enumerate((10, 9, 8, 7)):
        stairs(b, 2, 1 + i, z, SPRUCE, "north")
    b.set(2, 1, 11, "spruce_planks")
    for z in (7, 8, 9, 10, 11):
        fence(b, 3, 6, z, "spruce_fence")
    # The archive: shelves along the back, a desk with a chair, chests for the guild's records, a blue rug
    for x in range(7, 12):
        for y in (6, 7):
            b.set(x, y, 11, "bookshelf")
    for x in (8, 9, 10):
        table_slab(b, x, 8, y=6)
    stairs(b, 9, 6, 9, DARK_OAK, "south")
    b.set(8, 7, 8, "candle", candles=2, lit=True, waterlogged=False)
    b.set(10, 7, 8, "potted_dandelion")
    for z in (7, 9):
        b.set(16, 6, z, "chest", facing="west", type="single", waterlogged=False)
    for x in range(6, 13):
        for z in (6, 7):
            b.set(x, 6, z, "blue_carpet")
    for x in (5, 13):
        lantern(b, x, 11, 8, hanging=True)
    guild_banner(b, 9, 8, 2, "south")  # over the entrance bay's window seat, inside
    for x in (8, 10):
        stairs(b, x, 6, 2, SPRUCE, "south")


def guildhall_front(b):
    """The way in: a step and pavers, a guild banner either side of the door, a lamp at each corner, flower beds."""
    door(b, 9, 1, 1, "dark_oak_door", "south")
    stairs(b, 9, 0, 0, STONE_BRICK, "south")
    b.set(8, 0, 0, "polished_andesite")
    b.set(10, 0, 0, "polished_andesite")
    stairs(b, 9, 3, 0, STONE_BRICK, "south", top=True)  # a hood over the door
    b.set(9, 3, 1, "chiseled_stone_bricks")
    guild_banner(b, 8, 3, 0, "north")
    guild_banner(b, 10, 3, 0, "north")
    for x in (6, 12):
        lamp_post(b, x, 0, 1, "spruce_fence", height=2)
    flower_bed(b, 1, 2, 5, 2, 0, ["red_tulip", "cornflower", "dandelion", None])
    flower_bed(b, 13, 2, 17, 2, 0, ["red_tulip", "cornflower", "dandelion", None])


def guildhall():
    """19 x 15 x 14: a two-storey guildhall — stone below, timber and plaster above, under a slate roof with a gabled
    entrance bay — with a long table, benches, the charter framed over the hearth and banners by the door."""
    b = Build(19, 15, 14)
    guildhall_hall(b)
    guildhall_hearth(b)
    guildhall_rooms(b)
    guildhall_front(b)
    b.fill_air()
    return b


def guildhall_2():
    """Upgrade of the Guildhall (same origin and front): a stone tower wing on the east with the meeting room, a chart
    room, a ringing loft and an open belfry with the guild bell. 26 x 20 x 14."""
    b = guildhall().grow(26, 20, 14)
    b.entities = list(getattr(guildhall(), "entities", []))
    X0, X1, Z0, Z1 = 18, 24, 3, 9
    b.clear(18, 0, Z0, 18, 14, Z1)  # the hall's verge and the east windows' sills and shutters come off
    # The east windows the tower stands against are walled up; the middle ones become doors
    for z in (5, 6):
        for y in (2, 3):
            b.set(17, y, z, GUILD_STONE.at(17, y, z))
        for y in (6, 7):
            b.set(17, y, z, GUILD_PLASTER.at(17, y, z))
    for y in (10, 11):
        log(b, 17, y, 8, GUILD_FRAME)
    for y in (1, 2):
        b.set(17, y, 8, "air")
    b.set(17, 3, 8, GUILD_STONE.at(17, 3, 8))
    for y in (6, 7):
        b.set(17, y, 8, "air")
    b.set(17, 8, 8, GUILD_PLASTER.at(17, 8, 8))
    # The tower: stone on a plinth with a skirt, quoins at the corners
    plinth(b, X0, Z0, X1, Z1, FOUNDATION_MIX, floor="spruce_planks")
    for x in range(X0, X1 + 2):
        stairs(b, x, 0, Z0 - 1, STONE_BRICK, "south")
        stairs(b, x, 0, Z1 + 1, STONE_BRICK, "north")
    for z in range(Z0, Z1 + 1):
        stairs(b, X1 + 1, 0, z, STONE_BRICK, "west")
    walls(b, X0, Z0, X1, Z1, 1, 15, GUILD_STONE)
    for x, z in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)):
        box(b, x, 1, z, x, 15, z, "polished_andesite")
    door(b, X0, 1, 8, "dark_oak_door", "east")
    door(b, X0, 6, 8, "dark_oak_door", "east")
    # Floors (a ladder up the south-east corner), the belfry floor and the ceiling the bell hangs from
    for y in (5, 9, 12, 15):
        box(b, X0 + 1, y, Z0 + 1, X1 - 1, y, Z1 - 1, "spruce_planks")
    for y in range(1, 13):
        b.set(23, y, 8, "ladder", facing="west", waterlogged=False)
    for y in (5,):  # the hall's floor beam runs on round the tower
        for x in range(X0 + 1, X1):
            log(b, x, y, Z0, GUILD_FRAME, axis="x")
            log(b, x, y, Z1, GUILD_FRAME, axis="x")
        for z in range(Z0 + 1, Z1):
            log(b, X1, y, z, GUILD_FRAME, axis="z")
    # Windows: tall ones to the meeting room and the chart room, small ones to the loft
    for y, h in ((2, 2), (6, 2), (10, 1)):
        window(b, 21, y, Z0, "north", height=h, sill=STONE_BRICK)
        window(b, X1, y, 6, "east", height=h, sill=STONE_BRICK)
        window(b, 21, y, Z1, "south", height=h, sill=STONE_BRICK)
    # A string course under the belfry, the open arches, the bell
    for x in range(X0, X1 + 1):
        stairs(b, x, 12, Z0 - 1, STONE_BRICK, "south", top=True)
        stairs(b, x, 12, Z1 + 1, STONE_BRICK, "north", top=True)
    for z in range(Z0, Z1 + 1):
        stairs(b, X1 + 1, 12, z, STONE_BRICK, "west", top=True)
        if b.get(X0 - 1, 12, z) is None:
            stairs(b, X0 - 1, 12, z, STONE_BRICK, "east", top=True)
    for y in (13, 14):
        for x in (20, 21, 22):
            b.set(x, y, Z0, "air")
            b.set(x, y, Z1, "air")
        for z in (5, 6, 7):
            b.set(X0, y, z, "air")
            b.set(X1, y, z, "air")
    b.set(21, 14, 6, "bell", attachment="ceiling", facing="north", powered=False)
    lantern(b, 20, 13, 5)
    hip_roof(b, X0 - 1, X1 + 1, Z0 - 1, Z1 + 1, 15, GUILD_ROOF)
    # The meeting room: a round table with four chairs, a banner, a lamp
    for x, z in ((21, 5), (20, 6), (21, 6), (22, 6), (21, 7)):
        table_slab(b, x, z)
    for x, z, face in ((21, 4, "north"), (19, 6, "west"), (23, 6, "east"), (21, 8, "south")):
        stairs(b, x, 1, z, DARK_OAK, face)
    b.set(21, 2, 6, "candle", candles=3, lit=True, waterlogged=False)
    lantern(b, 21, 4, 6, hanging=True)
    guild_banner(b, 23, 3, 4, "west")
    b.set(19, 1, 4, "potted_fern")
    # The chart room: chests for the guild's ledgers, shelves, a desk
    for z in (4, 5):
        b.set(19, 6, z, "chest", facing="east", type="single", waterlogged=False)
    for x in (22,):
        for y in (6, 7):
            b.set(x, y, 4, "bookshelf")
    table_slab(b, 21, 6, y=6)
    table_slab(b, 21, 7, y=6)
    stairs(b, 20, 6, 6, DARK_OAK, "west")
    b.set(21, 7, 7, "candle", candles=1, lit=True, waterlogged=False)
    lantern(b, 21, 8, 5, hanging=True)
    # The loft: rope and a lantern
    lantern(b, 20, 10, 5)
    b.fill_air()
    return b
