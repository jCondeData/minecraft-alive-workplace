"""Our houses in village generation: one per job and village type (see world/VillageHouses)."""
from kit import *


# --- Village houses: one per job and village type, added to the vanilla house pools ------------------------
class VillageStyle:
    def __init__(self, plinth, frame, infill, roof, door, trapdoor, fence, bed, floor, flat=False):
        self.plinth, self.frame, self.infill, self.roof, self.door = plinth, frame, infill, roof, door
        self.trapdoor, self.fence, self.bed, self.floor, self.flat = trapdoor, fence, bed, floor, flat


VILLAGE_STYLES = {
    "plains": VillageStyle(STONE_MIX, "oak_log", "white_terracotta", OAK, "oak_door", "oak_trapdoor", "oak_fence", "red", "oak_planks"),
    "desert": VillageStyle("smooth_sandstone", "cut_sandstone", "sandstone", SMOOTH_SANDSTONE, "jungle_door", "jungle_trapdoor",
                           "jungle_fence", "yellow", "smooth_sandstone", flat=True),
    "savanna": VillageStyle(STONE_MIX, "acacia_log", "orange_terracotta", ACACIA, "acacia_door", "acacia_trapdoor", "acacia_fence",
                            "orange", "acacia_planks"),
    "snowy": VillageStyle(Mix((6, "stone_bricks"), (3, "cobblestone"), (2, "diorite"), seed=7), "stripped_spruce_log", "white_terracotta",
                          SPRUCE, "spruce_door", "spruce_trapdoor", "spruce_fence", "light_blue", "spruce_planks"),
    "taiga": VillageStyle(Mix((6, "cobblestone"), (2, "mossy_cobblestone"), (2, "stone"), seed=8), "spruce_log", "spruce_planks",
                          SPRUCE, "spruce_door", "spruce_trapdoor", "spruce_fence", "brown", "spruce_planks"),
}
VILLAGE_STRUCTURES = os.path.join(MAIN_STRUCTURES, "village")


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


def village_house(style, fit_out):
    """9 x 10 x 10. The street connects at the front (z = 0, left as the ground it lands on); a timber-framed house on a
    stone plinth (walls x 1-7, z 2-8) with its gable to the street, shuttered windows, a lantern by the door; inside a bed,
    a villager spawn and {@code fit_out(b, style)}: the job block and what goes with it (drawn for the interior x 1-5,
    z 2-6 of the first houses, moved in by one). Desert houses have a flat roof with a parapet instead."""
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
    for x in (2, 6):
        window(b, x, 2, 2, "north", shutters=s.trapdoor)
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
    else:
        gable_roof(b, 0, 8, 1, 9, 4, s.roof, axis="z", gable=s.infill, gable_at=(2, 8))
        b.set(4, 5, 2, s.frame, **frame_axis)
        pane(b, 4, 6, 2)
        b.set(4, 5, 8, s.frame, **frame_axis)
        lantern(b, 3, 3, 1, hanging=True) if False else None
        b.set(5, 3, 1, "lantern", hanging=True, waterlogged=False) if False else None
    b.set(4, 3, 1, "air")
    fit_out(Shifted(b, 1, 1), style)
    b.bed(6, 1, 6, s.bed, facing="south")
    lantern(b, 4, 3, 5, hanging=True)
    b.set(4, 4, 5, s.frame, **({"axis": "x"} if "log" in s.frame else {}))
    b.set(4, 0, 5, "jigsaw", orientation="up_north")
    floor_name = s.floor
    b.set_nbt(4, 0, 5, Compound({
        "name": String("minecraft:bottom"), "target": String("minecraft:bottom"),
        "pool": String(f"aliveworkplace:village/{style}/workers"), "final_state": String("minecraft:" + floor_name),
        "joint": String("rollable"), "id": String("minecraft:jigsaw"),
        "selection_priority": Int(0), "placement_priority": Int(0)}))
    b.fill_air()
    return b


def workshop_fit_out(b, style):
    """A Builder's Bench, a chest of building supplies, a crafting table and a stack of scaffolding."""
    b.set(1, 1, 6, "aliveworkplace:builders_bench", facing="east")
    b.set(1, 1, 5, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_builders_workshop"), "id": String("minecraft:chest")}))
    # (no barrel: it is a fisherman's job block and would steal the villager)
    b.set(1, 1, 2, "crafting_table")
    b.set(5, 1, 2, "scaffolding", bottom=False, distance=0, waterlogged=False)


def builders_workshop(style):
    """The builder's workshop: a Builder's Bench, a chest of building supplies, a bed and a villager spawn, so a builder
    moves in on its own."""
    return village_house(style, workshop_fit_out)


def staffed_house(style, fit_out):
    """The same house fitted out by {@code fit_out(b, style)} for another job."""
    return village_house(style, fit_out)


def trainers_house(b, style):
    """A Training Post, a sparring target and a trophy shelf."""
    b.set(1, 1, 6, "aliveworkplace:training_post", facing="east")
    b.set(1, 1, 3, "target")
    b.set(1, 2, 3, "target")
    b.set(1, 1, 5, "bookshelf")
    b.set(1, 2, 5, "flower_pot")


def guard_house(b, style):
    """A Guard Post and a chest of starting gear."""
    b.set(1, 1, 6, "aliveworkplace:guard_post", facing="east")
    b.set(1, 1, 5, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_guard_house"), "id": String("minecraft:chest")}))
    b.set(1, 1, 3, "anvil", facing="north")


# No vanilla job blocks in these houses (a cauldron, lectern, barrel...): the villager who moves in could
# take that job instead of ours.
def clinic(b, style):
    """A Nurse Station, a cot and flowers."""
    b.set(1, 1, 6, "aliveworkplace:nurse_station", facing="east")
    b.set(1, 1, 3, "white_carpet")
    b.set(1, 1, 4, "potted_poppy")


def post_office(b, style):
    """A Postal Desk with a sorting shelf."""
    b.set(1, 1, 6, "aliveworkplace:postal_desk", facing="east")
    b.set(1, 1, 5, "chiseled_bookshelf", facing="east")
    b.set(1, 1, 3, "bookshelf")


def leaders_hall(b, style):
    """The Trainer Leader's Podium between two polished andesite pillars, and a target to spar with."""
    b.set(1, 1, 6, "aliveworkplace:leaders_podium", facing="east")
    b.set(1, 1, 5, "polished_andesite")
    b.set(1, 2, 5, "polished_andesite")
    b.set(1, 1, 3, "target")
    b.set(1, 2, 3, "lantern", hanging=False, waterlogged=False)


def school(b, style):
    """A Tutor's Desk and bookshelves."""
    b.set(1, 1, 6, "aliveworkplace:tutors_desk", facing="east")
    b.set(1, 1, 5, "bookshelf")
    b.set(1, 2, 5, "bookshelf")
    b.set(1, 1, 3, "bookshelf")
    b.set(1, 2, 3, "flower_pot")


def trade_hall(b, style):
    """A Trade Board and somewhere to sit down and haggle."""
    b.set(1, 1, 6, "aliveworkplace:trade_board", facing="east")
    b.set(1, 1, 4, "oak_stairs", facing="east", half="bottom", shape="straight", waterlogged=False)
    b.set(1, 1, 3, "potted_fern")


def orchard_house(b, style):
    """A Fruit Basket, a chest for the harvest and an indoor bed of sweet berry bushes to pick."""
    b.set(1, 1, 6, "aliveworkplace:fruit_basket", facing="east")
    b.set(1, 1, 5, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_orchard"), "id": String("minecraft:chest")}))
    for x in (2, 3):
        b.set(x, 0, 6, "grass_block", snowy=False)
        b.set(x, 1, 6, "sweet_berry_bush", age=3)
    b.set(1, 1, 3, "potted_azalea_bush")


def ball_workshop(b, style):
    """A Ball Workbench, a chest of copper and dye, and an anvil."""
    b.set(1, 1, 6, "aliveworkplace:ball_workbench", facing="east")
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
    """A Carpenter's Bench, a chest of wood and a sawhorse: the carpenter makes what the village's builders are waiting for."""
    b.set(1, 1, 6, "aliveworkplace:carpenters_bench", facing="east")
    b.set(1, 1, 5, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_carpenters_workshop"), "id": String("minecraft:chest")}))
    b.set(1, 1, 3, "oak_log", axis="z")
    b.set(1, 1, 2, "oak_fence", north=False, south=False, east=False, west=False, waterlogged=False)


def kitchen(b, style):
    """A Kitchen Stove, a chest of the village's produce and a little table: the chef cooks for the village."""
    b.set(1, 1, 6, "aliveworkplace:kitchen_stove", facing="east")
    b.set(1, 1, 5, "chest", facing="east", type="single", waterlogged=False)
    b.set_nbt(1, 1, 5, Compound({"LootTable": String("aliveworkplace:chests/village_kitchen"), "id": String("minecraft:chest")}))
    b.set(1, 1, 3, "oak_fence", north=False, south=False, east=False, west=False, waterlogged=False)
    b.set(1, 2, 3, "oak_pressure_plate", powered=False)
    b.set(2, 1, 3, "oak_stairs", facing="west", half="bottom", shape="straight", waterlogged=False)


def fossil_lab(b, style):
    """A Fossil Lab, a shelf of books and a glass case: the fossil scientist revives fossils here (with Cobblemon)."""
    b.set(1, 1, 6, "aliveworkplace:fossil_lab", facing="east")
    b.set(1, 1, 5, "bookshelf")
    b.set(1, 1, 3, "glass")
    b.set(1, 2, 3, "bone_block", axis="y")


VILLAGE_HOUSES = {"trainers_house": trainers_house, "guard_house": guard_house, "clinic": clinic, "post_office": post_office,
                  "leaders_hall": leaders_hall, "school": school, "trade_hall": trade_hall, "orchard_house": orchard_house,
                  "ball_workshop": ball_workshop, "ferry_house": ferry_house, "storehouse": storehouse_room,
                  "carpenters_workshop": carpenters_workshop, "kitchen": kitchen,
                  "fossil_lab": fossil_lab}


