"""Our houses in village generation: one per job and village type (see world/VillageHouses)."""
from kit import *


# --- Village houses: one per job and village type, added to the vanilla house pools ------------------------
class VillageStyle:
    def __init__(self, plinth, frame, infill, roof, door, trapdoor, fence, bed, floor, flat=False):
        self.plinth, self.frame, self.infill, self.roof, self.door = plinth, frame, infill, roof, door
        self.trapdoor, self.fence, self.bed, self.floor, self.flat = trapdoor, fence, bed, floor, flat


# (each roof contrasts with its walls: a spruce roof over oak and plaster, dark oak over spruce, acacia over pale walls)
VILLAGE_STYLES = {
    "plains": VillageStyle(STONE_MIX, "oak_log", Mix((7, "calcite"), (3, "white_concrete"), seed=9), SPRUCE, "oak_door", "oak_trapdoor",
                           "oak_fence", "red", "oak_planks"),
    "desert": VillageStyle("smooth_sandstone", "cut_sandstone", "sandstone", SMOOTH_SANDSTONE, "jungle_door", "jungle_trapdoor",
                           "jungle_fence", "yellow", "smooth_sandstone", flat=True),
    "savanna": VillageStyle(STONE_MIX, "acacia_log", "white_terracotta", ACACIA, "acacia_door", "acacia_trapdoor", "acacia_fence",
                            "orange", "acacia_planks"),
    "snowy": VillageStyle(Mix((6, "stone_bricks"), (3, "cobblestone"), (2, "diorite"), seed=7), "stripped_spruce_log", "calcite",
                          DARK_OAK, "spruce_door", "spruce_trapdoor", "spruce_fence", "light_blue", "spruce_planks"),
    "taiga": VillageStyle(Mix((6, "cobblestone"), (2, "mossy_cobblestone"), (2, "stone"), seed=8), "spruce_log", "spruce_planks",
                          DARK_OAK, "spruce_door", "spruce_trapdoor", "spruce_fence", "brown", "spruce_planks"),
}
VILLAGE_STRUCTURES = os.path.join(MAIN_STRUCTURES, "village")
# The villager type of each village style's villagers.
VILLAGER_TYPES = {"plains": "minecraft:plains", "desert": "minecraft:desert", "savanna": "minecraft:savanna",
                  "snowy": "minecraft:snow", "taiga": "minecraft:taiga"}


class Shifted:
    """A build seen moved by (dx, dz): the fit-outs were drawn for the first, smaller house."""

    def __init__(self, b, dx, dz):
        self.b, self.dx, self.dz = b, dx, dz

    def set(self, x, y, z, name, **props):
        self.b.set(x + self.dx, y, z + self.dz, name, **props)

    def set_nbt(self, x, y, z, nbt):
        self.b.set_nbt(x + self.dx, y, z + self.dz, nbt)

    def bed(self, x, y, z, color, facing):
        self.b.bed(x + self.dx, y, z + self.dz, color, facing)

    def get(self, x, y, z):
        return self.b.get(x + self.dx, y, z + self.dz)


def village_house(style, fit_out, job=None):
    """9 x 10 x 10. The street connects at the front (z = 0, left as the ground it lands on); a timber-framed house on a
    stone plinth (walls x 1-7, z 2-8) with its gable to the street, shuttered windows, a lantern over the door; inside a bed,
    a villager and {@code fit_out(b, style)}: the job block and what goes with it (drawn for the interior x 1-5,
    z 2-6 of the first houses, moved in by one). Desert houses have a flat roof with a parapet instead, the roof beams'
    ends showing under it.
    The villager: with no {@code job}, a jobless one from the village's worker pool, who takes the job block by
    themselves (the block's own job). With a {@code job} (a job that shares a vanilla block since ROADMAP 21.1a, such as
    the orchard keeper's composter), one who already has that job and takes the house's block for it."""
    s = VILLAGE_STYLES[style]
    b = Build(9, 10, 10)
    for x in range(9):
        b.set(x, 0, 0, "structure_void")
    b.set(4, 1, 0, "jigsaw", orientation="north_up")
    b.set_nbt(4, 1, 0, Compound({
        "name": String("minecraft:building_entrance"), "target": String("minecraft:building_entrance"),
        "pool": String("minecraft:empty"), "final_state": String("minecraft:structure_void"),
        "joint": String("aligned"), "id": String("minecraft:jigsaw"),
        "selection_priority": Int(0), "placement_priority": Int(0)}))
    plinth(b, 1, 2, 7, 8, s.plinth, floor=s.floor)
    walls(b, 1, 2, 7, 8, 1, 3, s.infill)
    frame_axis = {"axis": "y"} if "log" in s.frame else {}
    for x, z in ((1, 2), (7, 2), (1, 8), (7, 8), (1, 5), (7, 5)):
        for y in (1, 2, 3):
            b.set(x, y, z, s.frame, **frame_axis)
    if "log" in s.frame:
        beam_ring(b, 1, 2, 7, 8, 4, s.frame)
    else:
        walls(b, 1, 2, 7, 8, 4, 4, s.frame)
    door(b, 4, 1, 2, s.door, "south")
    stairs(b, 4, 0, 1, s.roof, "south")
    for x, plant in ((2, "potted_red_tulip"), (6, "potted_oxeye_daisy")):
        window(b, x, 2, 2, "north", shutters=s.trapdoor, flowers=(s.trapdoor, [plant]))
    for z in (4, 6):
        window(b, 1, 2, z, "west", shutters=s.trapdoor)
        window(b, 7, 2, z, "east", shutters=s.trapdoor)
    if s.flat:
        # A flat roof: a sandstone slab over the walls, a parapet of walls, an awning of wool over the door
        box(b, 1, 5, 2, 7, 5, 8, s.roof.full)
        for x in range(1, 8):
            for z in (2, 8):
                wall_block(b, x, 6, z, "sandstone_wall")
        for z in range(3, 8):
            wall_block(b, 1, 6, z, "sandstone_wall")
            wall_block(b, 7, 6, z, "sandstone_wall")
        for x in range(3, 6):
            b.set(x, 4, 1, "orange_wool" if x != 4 else "white_wool")
        lantern(b, 3, 3, 1, hanging=True)
        lantern(b, 5, 3, 1, hanging=True)
        # The ends of the roof beams stick out of the walls under the parapet
        for z in (3, 5, 7):
            log(b, 0, 4, z, "stripped_jungle_log", axis="x")
            log(b, 8, 4, z, "stripped_jungle_log", axis="x")
        for x in (2, 4, 6):
            log(b, x, 4, 9, "stripped_jungle_log", axis="z")
    else:
        gable_roof(b, 0, 8, 1, 9, 4, s.roof, axis="z", gable=s.infill, gable_at=(2, 8), eave_trim=s.roof)
        b.set(4, 5, 2, s.frame, **frame_axis)
        pane(b, 4, 6, 2)
        b.set(4, 5, 8, s.frame, **frame_axis)
        chimney(b, 4, 9, 0, 8, s.plinth)  # up the back gable
        # A hood over the door
        stairs(b, 3, 3, 1, s.roof, "west", top=True)
        stairs(b, 5, 3, 1, s.roof, "east", top=True)
        slab(b, 4, 4, 1, s.roof)
    b.set(4, 3, 1, "air")
    if not s.flat:
        lantern(b, 4, 3, 1, hanging=True)  # under the hood's slab, over the way in
    fit_out(Shifted(b, 1, 1), style)
    b.bed(6, 1, 6, s.bed, facing="south")
    lantern(b, 4, 3, 5, hanging=True)
    for x in range(2, 7):  # a tie beam across the room, wall to wall, the lantern hanging from it
        b.set(x, 4, 5, s.frame, **({"axis": "x"} if "log" in s.frame else {}))
    floor_name = s.floor
    if job is None:
        b.set(4, 0, 5, "jigsaw", orientation="up_north")
        b.set_nbt(4, 0, 5, Compound({
            "name": String("minecraft:bottom"), "target": String("minecraft:bottom"),
            "pool": String(f"aliveworkplace:village/{style}/workers"), "final_state": String("minecraft:" + floor_name),
            "joint": String("rollable"), "id": String("minecraft:jigsaw"),
            "selection_priority": Int(0), "placement_priority": Int(0)}))
    else:
        b.set(4, 0, 5, floor_name)
        b.villager(5, 1, 5, job, VILLAGER_TYPES[style])  # in the open middle of the room: no fit-out uses x = 5
    b.fill_air()
    return b


def workshop_fit_out(b, style):
    """A Blueprint Table, a chest of building supplies, a crafting table and a stack of scaffolding."""
    b.set(1, 1, 6, "aliveworkplace:blueprint_table", facing="east")
    b.set(1, 1, 5, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_builders_workshop"), "id": String("minecraft:chest")}))
    # (no barrel: it is a fisherman's job block and would steal the villager)
    b.set(1, 1, 2, "crafting_table")
    b.set(5, 1, 2, "scaffolding", bottom=False, distance=0, waterlogged=False)


def builders_workshop(style):
    """The builder's workshop: a Blueprint Table, a chest of building supplies, a bed and a villager spawn, so a builder
    moves in on its own (the table's own job)."""
    return village_house(style, workshop_fit_out)


def staffed_house(style, fit_out, job=None):
    """The same house fitted out by {@code fit_out(b, style)} for another job ({@code job}: see village_house)."""
    return village_house(style, fit_out, job)


def trainers_house(b, style):
    """A Training Post, a sparring target and a trophy shelf."""
    b.set(1, 1, 6, "aliveworkplace:training_post", facing="east")
    b.set(1, 1, 3, "target")
    b.set(1, 2, 3, "target")
    b.set(1, 1, 5, "bookshelf")
    b.set(1, 2, 5, "flower_pot")


def guard_house(b, style):
    """A grindstone (the guard's) and a chest of starting gear."""
    b.set(1, 1, 6, "grindstone", face="floor", facing="east")
    b.set(1, 1, 5, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_guard_house"), "id": String("minecraft:chest")}))
    b.set(1, 1, 3, "anvil", facing="north")


# One job block per house: a second vanilla one (a cauldron, lectern, barrel...) would take another villager for
# its vanilla job.
def clinic(b, style):
    """A brewing stand (the nurse's), a cot and flowers."""
    b.set(1, 1, 6, "brewing_stand", has_bottle_0=False, has_bottle_1=False, has_bottle_2=False)
    b.set(1, 1, 3, "white_carpet")
    b.set(1, 1, 4, "potted_poppy")


def post_office(b, style):
    """A Mailbox (the postman's) with a sorting shelf."""
    b.set(1, 1, 6, "aliveworkplace:mailbox", facing="east", has_mail=False)
    b.set(1, 1, 5, "chiseled_bookshelf", facing="east")
    b.set(1, 1, 3, "bookshelf")


def leaders_hall(b, style):
    """The Trainer Leader's Training Post between two polished andesite pillars, and a target to spar with."""
    b.set(1, 1, 6, "aliveworkplace:training_post", facing="east")
    b.set(1, 1, 5, "polished_andesite")
    b.set(1, 2, 5, "polished_andesite")
    b.set(1, 1, 3, "target")
    b.set(1, 2, 3, "lantern", hanging=False, waterlogged=False)


def school(b, style):
    """The Move Tutor's Training Post and bookshelves."""
    b.set(1, 1, 6, "aliveworkplace:training_post", facing="east")
    b.set(1, 1, 5, "bookshelf")
    b.set(1, 2, 5, "bookshelf")
    b.set(1, 1, 3, "bookshelf")
    b.set(1, 2, 3, "flower_pot")


def trade_hall(b, style):
    """The Pokémon Trader's Shop Counter and somewhere to sit down and haggle."""
    b.set(1, 1, 6, "aliveworkplace:shop_counter", facing="east")
    b.set(1, 1, 4, "oak_stairs", facing="east", half="bottom", shape="straight", waterlogged=False)
    b.set(1, 1, 3, "potted_fern")


def orchard_house(b, style):
    """A composter (the orchard keeper's), a chest for the harvest and an indoor bed of sweet berry bushes to pick."""
    b.set(1, 1, 6, "composter", level=0)
    b.set(1, 1, 5, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_orchard"), "id": String("minecraft:chest")}))
    for x in (2, 3):
        b.set(x, 0, 6, "grass_block", snowy=False)
        b.set(x, 1, 6, "sweet_berry_bush", age=3)
    b.set(1, 1, 3, "potted_azalea_bush")


def ball_workshop(b, style):
    """A smithing table (the ball smith's), a chest of copper and dye, and an anvil."""
    b.set(1, 1, 6, "smithing_table")
    b.set(1, 1, 5, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_ball_workshop"), "id": String("minecraft:chest")}))
    b.set(1, 1, 3, "anvil", facing="north")


def ferry_house(b, style):
    """A Travel Post (it joins the travel network under a village name) and a bench for passengers."""
    b.set(1, 1, 6, "aliveworkplace:travel_post", facing="east")
    b.set(1, 1, 4, "spruce_stairs", facing="east", half="bottom", shape="straight", waterlogged=False)
    b.set(1, 1, 3, "spruce_stairs", facing="east", half="bottom", shape="straight", waterlogged=False)
    b.set(1, 2, 5, "lantern", hanging=False, waterlogged=False)
    b.set(1, 1, 5, "spruce_planks")


def storehouse_room(b, style):
    """A Storehouse and four empty chests: the village's store (the porter who moves in fills them)."""
    b.set(1, 1, 6, "aliveworkplace:storehouse", facing="east")
    for z in (4, 5):
        for y in (1, 2):
            b.set(1, y, z, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_storehouse"), "id": String("minecraft:chest")}))
    b.set(1, 1, 3, "hay_block", axis="y")


def carpenters_workshop(b, style):
    """A crafting table (the carpenter's), a chest of wood and a sawhorse: the carpenter makes what the village's builders
    are waiting for."""
    b.set(1, 1, 6, "crafting_table")
    b.set(1, 1, 5, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_carpenters_workshop"), "id": String("minecraft:chest")}))
    b.set(1, 1, 3, "oak_log", axis="z")
    b.set(1, 1, 2, "oak_fence", north=False, south=False, east=False, west=False, waterlogged=False)


def kitchen(b, style):
    """A smoker (the chef's), a chest of the village's produce and a little table: the chef cooks for the village."""
    b.set(1, 1, 6, "smoker", facing="east", lit=False)
    b.set(1, 1, 5, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_kitchen"), "id": String("minecraft:chest")}))
    b.set(1, 1, 3, "oak_fence", north=False, south=False, east=False, west=False, waterlogged=False)
    b.set(1, 2, 3, "oak_pressure_plate", powered=False)
    b.set(2, 1, 3, "oak_stairs", facing="west", half="bottom", shape="straight", waterlogged=False)


def fossil_lab(b, style):
    """The fossil scientist's Fossil Analyzer (Cobblemon's: these houses only grow with Cobblemon, ROADMAP 21.1c), a shelf
    of books and a glass case: they revive fossils here."""
    b.set(1, 1, 6, "cobblemon:fossil_analyzer", facing="east", on=False)
    b.set(1, 1, 5, "bookshelf")
    b.set(1, 1, 3, "glass")
    b.set(1, 2, 3, "bone_block", axis="y")


def flower_shop(b, style):
    """A composter (the florist's), a chest of bone meal and flowers, and pots on the sill: the florist grows the village's
    flowers."""
    b.set(1, 1, 6, "composter", level=0)
    b.set(1, 1, 5, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_flower_shop"), "id": String("minecraft:chest")}))
    b.set(1, 1, 3, "potted_red_tulip")
    b.set(2, 1, 3, "potted_oxeye_daisy")


def ranch_house(b, style):
    """A smoker (the rancher's), a chest of feed and a hay bale: the rancher tames and breeds the horses round the village."""
    b.set(1, 1, 6, "smoker", facing="east", lit=False)
    b.set(1, 1, 5, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_ranch"), "id": String("minecraft:chest")}))
    b.set(1, 1, 3, "hay_block", axis="y")
    b.set(1, 2, 3, "hay_block", axis="x")


def schoolhouse(b, style):
    """A lectern (the teacher's) facing two benches, and a shelf of books: the teacher gives the village's children lessons."""
    b.set(1, 1, 6, "lectern", facing="east", has_book=False, powered=False)
    for z in (4, 5):
        b.set(3, 1, z, "oak_stairs", facing="west", half="bottom", shape="straight", waterlogged=False)
    b.set(1, 1, 3, "bookshelf")
    b.set(1, 2, 3, "flower_pot")


def inn_room(b, style):
    """The innkeeper's Shop Counter, a chest of bread and a stool: the innkeeper takes in travellers (the house's bed is
    for guests)."""
    b.set(1, 1, 6, "aliveworkplace:shop_counter", facing="east")
    b.set(1, 1, 5, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_inn"), "id": String("minecraft:chest")}))
    b.set(2, 1, 4, "oak_stairs", facing="west", half="bottom", shape="straight", waterlogged=False)
    b.set(1, 1, 3, "hay_block", axis="y")


def mortuary(b, style):
    """A brewing stand (the undertaker's), a chest (now and then with a golden apple) and candles: the undertaker brings
    the village's dead back."""
    b.set(1, 1, 6, "brewing_stand", has_bottle_0=False, has_bottle_1=False, has_bottle_2=False)
    b.set(1, 1, 5, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_mortuary"), "id": String("minecraft:chest")}))
    b.set(1, 1, 3, "polished_andesite")
    b.set(1, 2, 3, "candle", candles=3, lit=False, waterlogged=False)


def tinkers_shop(b, style):
    """A smithing table (the tinkerer's), a chest of ore and redstone, an anvil: the tinkerer makes the builders' redstone
    and iron parts and mends the iron golems."""
    b.set(1, 1, 6, "smithing_table")
    b.set(1, 1, 5, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_tinkers_shop"), "id": String("minecraft:chest")}))
    b.set(1, 1, 3, "anvil", facing="north")
    b.set(1, 1, 2, "chain", axis="y", waterlogged=False)


def sifting_shed_house(b, style):
    """A cauldron (the sifter's), a chest of gravel and sand, a heap of gravel and sand: the sifter shakes them through for
    what's hidden in them."""
    b.set(1, 1, 6, "cauldron")
    b.set(1, 1, 5, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_sifting_shed"), "id": String("minecraft:chest")}))
    b.set(1, 1, 3, "gravel")
    b.set(1, 1, 2, "sand")  # (no barrel: that's a fisherman's job block)


def compost_yard_house(b, style):
    """A composter, a chest of scraps and a hay bale: the composter (the job) turns the village's scraps into bone meal."""
    b.set(1, 1, 6, "composter", level=0)
    b.set(1, 1, 5, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_compost_yard"), "id": String("minecraft:chest")}))
    b.set(1, 1, 3, "hay_block", axis="y")
    b.set(1, 1, 2, "coarse_dirt")


VILLAGE_HOUSES = {"trainers_house": trainers_house, "guard_house": guard_house, "clinic": clinic, "post_office": post_office,
                  "leaders_hall": leaders_hall, "school": school, "trade_hall": trade_hall, "orchard_house": orchard_house,
                  "ball_workshop": ball_workshop, "ferry_house": ferry_house, "storehouse": storehouse_room,
                  "carpenters_workshop": carpenters_workshop, "kitchen": kitchen,
                  "fossil_lab": fossil_lab, "flower_shop": flower_shop, "ranch_house": ranch_house,
                  "schoolhouse": schoolhouse, "inn_room": inn_room, "mortuary": mortuary,
                  "tinkers_shop": tinkers_shop, "sifting_shed": sifting_shed_house,
                  "compost_yard": compost_yard_house}

# The job of each house's villager where it isn't the job block's own (see village_house): these share their block
# with a vanilla job, or with another of ours, since ROADMAP 21.1a.
HOUSE_JOBS = {"guard_house": "guard", "clinic": "nurse", "post_office": "postman", "leaders_hall": "trainer_leader",
              "school": "tutor", "trade_hall": "pokemon_trader", "orchard_house": "orchard_keeper",
              "ball_workshop": "ball_smith", "carpenters_workshop": "carpenter", "kitchen": "chef",
              "fossil_lab": "fossil_scientist", "flower_shop": "florist", "ranch_house": "rancher",
              "schoolhouse": "teacher", "inn_room": "innkeeper", "mortuary": "undertaker", "tinkers_shop": "tinkerer",
              "sifting_shed": "sifter", "compost_yard": "composter"}


