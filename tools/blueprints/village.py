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


def house_name(fit_out):
    """The village house a fit-out furnishes (its name in VILLAGE_HOUSES, or the builder's workshop)."""
    if fit_out is workshop_fit_out:
        return "builders_workshop"
    return next(name for name, f in VILLAGE_HOUSES.items() if f is fit_out)


def village_house(style, fit_out, job=None):
    """The village house for the job {@code fit_out} furnishes, in {@code style}: each job's house has its own shape
    (village_looks.py, ROADMAP 26.11) drawn in the style's materials. The street connects at the front (z = 0, left as
    the ground it lands on) to the door at (4, 1, 2); inside a bed, a villager and {@code fit_out(b, style)}: the job block
    and what goes with it (drawn for the interior x 1-5, z 2-6 of the first houses, moved in by one).
    The villager: with no {@code job}, a jobless one from the village's worker pool, who takes the job block by
    themselves (the block's own job). With a {@code job} (a job that shares a vanilla block since ROADMAP 21.1a, such as
    the orchard keeper's composter), one who already has that job and takes the house's block for it."""
    from village_looks import DESIGNS, PALETTES
    draw, (w, h, d) = DESIGNS[house_name(fit_out)]
    s = VILLAGE_STYLES[style]
    b = Build(w, h, d)
    for x in range(w):
        b.set(x, 0, 0, "structure_void")
    b.set(4, 1, 0, "jigsaw", orientation="north_up")
    b.set_nbt(4, 1, 0, Compound({
        "name": String("minecraft:building_entrance"), "target": String("minecraft:building_entrance"),
        "pool": String("minecraft:empty"), "final_state": String("minecraft:structure_void"),
        "joint": String("aligned"), "id": String("minecraft:jigsaw"),
        "selection_priority": Int(0), "placement_priority": Int(0)}))
    draw(b, PALETTES[style])
    fit_out(Shifted(b, 1, 1), style)
    b.bed(6, 1, 6, s.bed, facing="south")
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
        # in the open middle of the room: no fit-out uses x = 5. A job block the job can't take by itself (a nurse's Healing
        # Machine) is taken through the tag (work/Stations.takeHouseBlock).
        tags = ("aliveworkplace_house_worker",) if getattr(fit_out, "claims_job_block", False) else ()
        b.villager(5, 1, 5, job, VILLAGER_TYPES[style], tags)
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
    chair(b, 2, 1, 3, "oak_stairs", "west")


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


def winery_room(b, style):
    """A cauldron (the vintner's vat), a chest of fruit and bottles, and casks: spruce logs laid so their ends show (no
    barrel: that's a fisherman's job block). The vintner presses the village's cider and wine (ROADMAP 34.13)."""
    b.set(1, 1, 6, "cauldron")
    loot_chest(b, 1, 1, 5, "village_winery")
    b.set(1, 1, 3, "spruce_log", axis="x")
    b.set(1, 2, 3, "spruce_log", axis="x")
    b.set(1, 1, 2, "spruce_log", axis="x")


def tailors_room(b, style):
    """A loom (the tailor's), a chest of wool, string and dyes, and bolts of coloured wool: the tailor sews the village's
    clothes (ROADMAP 34.13)."""
    b.set(1, 1, 6, "loom", facing="east")
    loot_chest(b, 1, 1, 5, "village_tailors_shop")
    b.set(1, 1, 3, "red_wool")
    b.set(1, 2, 3, "yellow_wool")
    b.set(1, 1, 2, "blue_wool")


def print_shop_room(b, style):
    """A cartography table (the printer's press), a chest of paper and ink, and shelves of books: the printer prints the
    village's books and its Gazette (ROADMAP 34.14). No lectern: that would make a librarian."""
    b.set(1, 1, 6, "cartography_table")
    loot_chest(b, 1, 1, 5, "village_print_shop")
    b.set(1, 1, 3, "bookshelf")
    b.set(1, 2, 3, "bookshelf")
    b.set(1, 1, 2, "bookshelf")


def jewellers_room(b, style):
    """A stonecutter (the jeweller's bench), a chest of gold and stones, and an amethyst cluster on a block of polished
    stone: the jeweller cuts the village's rings, brooches and circlets (ROADMAP 34.14)."""
    b.set(1, 1, 6, "stonecutter", facing="east")
    loot_chest(b, 1, 1, 5, "village_jewellers_workshop")
    b.set(1, 1, 3, "polished_andesite")
    b.set(1, 2, 3, "amethyst_cluster", facing="up", waterlogged=False)
    b.set(1, 1, 2, "smooth_stone")


def crafters_room(b, style):
    """The Crafter's Workbench (with its Crafter already in the job, like the luxury houses' workers), a chest of boards, logs and sticks,
    and a stack of cut boards and slabs: the Crafter makes what the village's builders are short of (ROADMAP 37.16)."""
    b.set(1, 1, 6, "aliveworkplace:crafters_workbench", facing="east")
    loot_chest(b, 1, 1, 5, "village_crafters_workshop")
    b.set(1, 1, 3, "stripped_birch_log", axis="x")
    b.set(1, 2, 3, "birch_slab", type="bottom", waterlogged=False)
    b.set(1, 1, 2, "oak_slab", type="double", waterlogged=False)


# --- The Pokémon jobs' village houses (ROADMAP 28.15): only with Cobblemon (and config pokemonVillageHouses) -----
# Each keeps its one job block and comes with its villager already in the job: Cobblemon's blocks (the Healing Machine,
# the Campfire Pot, the Pasture Block) are never taken by a jobless villager, and a composter or a stonecutter would
# make a farmer or a mason.
def loot_chest(b, x, y, z, table, facing="east"):
    """A chest of the house's loot (each table loads only with Cobblemon: its items are Cobblemon's)."""
    b.set(x, y, z, "chest", facing=facing, type="single", waterlogged=False)
    b.set_nbt(x, y, z, Compound({"LootTable": String("aliveworkplace:chests/" + table), "id": String("minecraft:chest")}))


def pokemon_center_room(b, style):
    """The nurse's counter along the back wall: Cobblemon's PC, the Healing Machine (the nurse's, where she heals your
    team) and a quartz counter top with flowers on it; a red carpet from the door to the counter, a white mat where the
    nurse stands and a waiting bench by the west wall."""
    b.set(1, 1, 6, "cobblemon:pc", facing="north", part="bottom", on=False, waterlogged=False)
    b.set(1, 2, 6, "cobblemon:pc", facing="north", part="top", on=False, waterlogged=False)
    b.set(2, 1, 6, "cobblemon:healing_machine", facing="north")
    b.set(3, 1, 6, "smooth_quartz")
    b.set(3, 2, 6, "potted_allium")
    b.set(2, 1, 5, "white_carpet")
    for z in (3, 4, 5):  # a block in from the door: on a carpet under its lintel a villager hits its head (B173)
        b.set(3, 1, z, "red_carpet")
    for z in (3, 4):  # the waiting bench, its back to the west wall
        b.set(1, 1, z, "quartz_stairs", facing="west", half="bottom", shape="straight", waterlogged=False)
    b.set(1, 1, 2, "potted_red_tulip")


pokemon_center_room.claims_job_block = True  # a nurse takes a Healing Machine only by a honey bottle


def camp_kitchen_room(b, style):
    """The Camp Cook's Campfire Pot (Cobblemon's campfire with a red pot on it) on a stone hearth at the back, the grain
    store beside it (Hearty Grain bales), her chest of makings on the other side and a bench by the west wall."""
    for x in (1, 2, 3):
        for z in (5, 6):
            b.set(x, 0, z, "stone_bricks")
    b.set(2, 0, 6, "mud_bricks")
    b.set(2, 1, 6, "cobblemon:campfire", facing="north", lid=False)
    b.set_nbt(2, 1, 6, Compound({"id": String("cobblemon:campfire_pot"),
                                 "PotComponent": Compound({"id": String("cobblemon:campfire_pot_red"), "count": Int(1)})}))
    b.set(1, 1, 6, "cobblemon:hearty_grain_bale", axis="y")
    b.set(1, 2, 6, "cobblemon:hearty_grain_bale", axis="z")
    b.set(1, 1, 5, "cobblemon:hearty_grain_bale", axis="y")
    loot_chest(b, 3, 1, 6, "village_camp_kitchen", facing="north")
    for z in (3, 4):  # the bench, its back to the west wall
        b.set(1, 1, z, "spruce_stairs", facing="west", half="bottom", shape="straight", waterlogged=False)
    b.set(1, 1, 2, "potted_fern")


def berry_nursery_room(b, style):
    """The Berry Breeder's composter at the back over a hidden water source (it wets every bed), two pairs of farmland
    beds in the floor either side of it and by the west wall, her chest of berries and mulch by the door."""
    b.set(2, 0, 6, "water", level=0)
    b.set(2, 1, 6, "composter", level=0)
    for x, z in ((1, 5), (1, 6), (3, 5), (3, 6), (1, 3), (1, 4)):
        b.set(x, 0, z, "farmland", moisture=7)
    loot_chest(b, 1, 1, 2, "village_berry_nursery")
    b.set(1, 2, 2, "potted_flowering_azalea_bush")


def daycare_room(b, style):
    """The Daycare Keeper's Pasture Block in the back corner, a straw floor (hay) round it, a nest of hay with a soft
    white blanket in it, her chest and a lantern on a post by the nursery."""
    for x in (1, 2, 3):
        for z in (4, 5, 6):
            b.set(x, 0, z, "hay_block", axis="y")
    b.set(1, 1, 6, "cobblemon:pasture", facing="north", part="bottom", on=False, waterlogged=False)
    b.set(1, 2, 6, "cobblemon:pasture", facing="north", part="top", on=False, waterlogged=False)
    b.set(2, 1, 6, "hay_block", axis="x")
    b.set(2, 2, 6, "white_carpet")
    loot_chest(b, 3, 1, 6, "village_daycare", facing="north")
    b.set(1, 1, 3, "spruce_fence", north=False, south=False, east=False, west=False, waterlogged=False)
    b.set(1, 2, 3, "lantern", hanging=False, waterlogged=False)
    b.set(1, 1, 2, "potted_azalea_bush")


def gem_grotto_room(b, style):
    """The Gem Grower's stonecutter, an amethyst niche in the back corner (a budding amethyst with a cluster on it: world
    generation may place one, a builder can't), and a ledge of magma along the back wall behind glass panes with
    tumblestones growing on it (no open lava in a village); her chest by the door."""
    b.set(1, 1, 6, "budding_amethyst")
    b.set(1, 2, 6, "amethyst_cluster", facing="up", waterlogged=False)
    b.set(1, 1, 5, "amethyst_block")
    b.set(1, 2, 5, "medium_amethyst_bud", facing="up", waterlogged=False)
    for x, stone in ((2, "cobblemon:small_budding_tumblestone"), (3, "cobblemon:medium_budding_tumblestone")):
        b.set(x, 1, 6, "magma_block")
        b.set(x, 2, 6, stone, facing="up", waterlogged=False)
        for y in (1, 2):
            pane(b, x, y, 5)
    b.set(4, 1, 6, "polished_blackstone")
    pane(b, 4, 2, 6)
    for y in (1, 2):  # the case's corner, so its glass meets round it (B648: both runs ended in the air there)
        pane(b, 4, y, 5)
    b.set(1, 1, 3, "stonecutter", facing="east")
    loot_chest(b, 1, 1, 2, "village_gem_grotto")


VILLAGE_HOUSES = {"trainers_house": trainers_house, "guard_house": guard_house, "clinic": clinic, "post_office": post_office,
                  "leaders_hall": leaders_hall, "school": school, "trade_hall": trade_hall, "orchard_house": orchard_house,
                  "ball_workshop": ball_workshop, "ferry_house": ferry_house, "storehouse": storehouse_room,
                  "carpenters_workshop": carpenters_workshop, "kitchen": kitchen,
                  "fossil_lab": fossil_lab, "flower_shop": flower_shop, "ranch_house": ranch_house,
                  "schoolhouse": schoolhouse, "inn_room": inn_room, "mortuary": mortuary,
                  "tinkers_shop": tinkers_shop, "sifting_shed": sifting_shed_house,
                  "compost_yard": compost_yard_house,
                  "winery": winery_room, "tailors_shop": tailors_room,
                  "print_shop": print_shop_room, "jewellers_workshop": jewellers_room,
                  "crafters_workshop": crafters_room,
                  "pokemon_center": pokemon_center_room, "camp_kitchen": camp_kitchen_room,
                  "berry_nursery": berry_nursery_room, "daycare": daycare_room, "gem_grotto": gem_grotto_room}

# The job of each house's villager where it isn't the job block's own (see village_house): these share their block
# with a vanilla job, or with another of ours, since ROADMAP 21.1a.
HOUSE_JOBS = {"guard_house": "guard", "clinic": "nurse", "post_office": "postman", "leaders_hall": "trainer_leader",
              "school": "tutor", "trade_hall": "pokemon_trader", "orchard_house": "orchard_keeper",
              "ball_workshop": "ball_smith", "carpenters_workshop": "carpenter", "kitchen": "chef",
              "fossil_lab": "fossil_scientist", "flower_shop": "florist", "ranch_house": "rancher",
              "schoolhouse": "teacher", "inn_room": "innkeeper", "mortuary": "undertaker", "tinkers_shop": "tinkerer",
              "sifting_shed": "sifter", "compost_yard": "composter",
              "winery": "vintner", "tailors_shop": "tailor",
              "print_shop": "printer", "jewellers_workshop": "jeweller", "crafters_workshop": "crafter",
              "pokemon_center": "nurse", "camp_kitchen": "camp_cook", "berry_nursery": "berry_breeder",
              "daycare": "daycare_keeper", "gem_grotto": "gem_grower"}




# --- Buildable workplaces for the Blueprint Table (ROADMAP 27.11) ---------------------------------------------
# One workplace per job the Steward asks for. Each has its own look since ROADMAP 26.11 (workplaces.py); before, they
# were the plains village house with the job's room inside.
WORKPLACES = ("builders_workshop", "carpenters_workshop", "kitchen", "post_office", "guard_house", "clinic", "ferry_house")
# Made of Cobblemon jobs' places: in the Blueprint Table only with Cobblemon (StarterBlueprints.COBBLEMON_WORKPLACES).
COBBLEMON_WORKPLACES = ("trainers_house", "leaders_hall", "ball_workshop", "trade_hall", "school")


def workplace(name):
    """The buildable workplace {@code name}, each with its own look (workplaces.py): no jigsaws, structure voids or
    villager (the worker who wants it moves in), no loot in its chests (builders never fill containers), its front at
    z = 0. Its size is in StarterBlueprints.java."""
    from workplaces import WORKPLACE_BUILDS
    return WORKPLACE_BUILDS[name]()
