#!/usr/bin/env python3
"""
Generates the structure files that ship with Alive Workplace, plus the gametest fixtures.

    pip install nbtlib
    python3 tools/blueprints/generate.py

The kit (Build and the building techniques) is kit.py, the starter blueprints starter.py, the village houses village.py.
If you change a build's size, update StarterBlueprints.java (a gametest checks they match).
"""
from kit import *
from starter import *
from village import *
from decor import *
from houses import *
from defence import *
from workshops import *
from countryside import *
from nether import *
from yards import *
from bandits import *
from legend_sites import *
from pokemon import *
from pokemon_jobs import *
from guildhall import *
from arena import *


# --- Gametest fixtures ------------------------------------------------------------------
def test_hut():
    """5x4x5 hut: floor, walls with a door and a torch, flat roof. Needs 25 cobblestone,
    55 oak planks, 1 oak door, 1 torch."""
    b = Build(5, 4, 5)
    b.fill(0, 0, 0, 4, 0, 4, "cobblestone")
    for y in (1, 2):
        b.ring(0, 0, 4, 4, y, "oak_planks")
    b.door(2, 1, 0, "oak_door", facing="south")
    b.set(2, 2, 3, "wall_torch", facing="north")
    b.fill(0, 3, 0, 4, 3, 4, "oak_planks")
    b.fill_air()
    return b


def test_hut_2():
    """The test hut's upgrade: a lantern on the roof (a builder sells it once they've built the hut)."""
    b = test_hut().grow(5, 5, 5)
    b.set(2, 4, 2, "lantern", hanging=False, waterlogged=False)
    b.fill_air()
    return b


def test_area(name, w, h, d, folder=TEST_AREAS):
    """Flat smooth-stone floor at y=0, empty above. Saved as SNBT in the *packed* layout the gametest
    loader expects (palette of state strings, `data` entries with a `state` string)."""
    os.makedirs(folder, exist_ok=True)
    data = [Compound({"pos": List[Int]([Int(x), Int(0), Int(z)]), "state": String("minecraft:smooth_stone")})
            for x in range(w) for z in range(d)]
    tag = Compound({
        "DataVersion": Int(DATA_VERSION),
        "size": List[Int]([Int(w), Int(h), Int(d)]),
        "data": List[Compound](data),
        "entities": List[Compound]([]),
        "palette": List[String]([String("minecraft:smooth_stone")]),
    })
    path = os.path.join(folder, name + ".snbt")
    with open(path, "w") as f:
        f.write(nbtlib.serialize_tag(tag))
    print(f"{name}: test area {w}x{h}x{d} -> {os.path.relpath(path, ROOT)}")


if __name__ == "__main__":
    starter_cottage().save(MAIN_STRUCTURES, "starter_cottage")
    starter_cottage_2().save(MAIN_STRUCTURES, "starter_cottage_2")
    starter_cottage_3().save(MAIN_STRUCTURES, "starter_cottage_3")
    market_stall().save(MAIN_STRUCTURES, "market_stall")
    market_stall_2().save(MAIN_STRUCTURES, "market_stall_2")
    market_stall_3().save(MAIN_STRUCTURES, "market_stall_3")
    lookout_tower().save(MAIN_STRUCTURES, "lookout_tower")
    lookout_tower_2().save(MAIN_STRUCTURES, "lookout_tower_2")
    lookout_tower_3().save(MAIN_STRUCTURES, "lookout_tower_3")
    healing_center().save(MAIN_STRUCTURES, "healing_center")
    healing_center_2().save(MAIN_STRUCTURES, "healing_center_2")
    healing_center_3().save(MAIN_STRUCTURES, "healing_center_3")
    supply_shop().save(MAIN_STRUCTURES, "supply_shop")
    supply_shop_2().save(MAIN_STRUCTURES, "supply_shop_2")
    supply_shop_3().save(MAIN_STRUCTURES, "supply_shop_3")
    storehouse().save(MAIN_STRUCTURES, "storehouse")
    storehouse_2().save(MAIN_STRUCTURES, "storehouse_2")
    storehouse_3().save(MAIN_STRUCTURES, "storehouse_3")
    berry_farm().save(MAIN_STRUCTURES, "berry_farm")
    berry_farm_2().save(MAIN_STRUCTURES, "berry_farm_2")
    research_lab().save(MAIN_STRUCTURES, "research_lab")
    research_lab_2().save(MAIN_STRUCTURES, "research_lab_2")
    terrace().save(MAIN_STRUCTURES, "terrace")
    terrace_2().save(MAIN_STRUCTURES, "terrace_2")
    inn().save(MAIN_STRUCTURES, "inn")
    inn_2().save(MAIN_STRUCTURES, "inn_2")
    school_building().save(MAIN_STRUCTURES, "schoolhouse")
    school_building_2().save(MAIN_STRUCTURES, "schoolhouse_2")
    library().save(MAIN_STRUCTURES, "library")
    library_2().save(MAIN_STRUCTURES, "library_2")
    library_3().save(MAIN_STRUCTURES, "library_3")
    ranch().save(MAIN_STRUCTURES, "ranch")
    ranch_2().save(MAIN_STRUCTURES, "ranch_2")
    apiary_garden().save(MAIN_STRUCTURES, "apiary_garden")
    apiary_garden_2().save(MAIN_STRUCTURES, "apiary_garden_2")
    flower_shop_building().save(MAIN_STRUCTURES, "flower_shop")
    flower_shop_building_2().save(MAIN_STRUCTURES, "flower_shop_2")
    graveyard().save(MAIN_STRUCTURES, "graveyard")
    graveyard_2().save(MAIN_STRUCTURES, "graveyard_2")
    town_hall().save(os.path.join(MAIN_STRUCTURES, "research"), "town_hall")
    for name, draw in (("well", well), ("well_2", well_2), ("street_lamp", street_lamp), ("park_bench", park_bench),
                       ("fountain", fountain), ("gazebo", gazebo), ("market_square", market_square)):
        draw().save(MAIN_STRUCTURES, name)
    stone_house().save(MAIN_STRUCTURES, "stone_house")
    stone_house_2().save(MAIN_STRUCTURES, "stone_house_2")
    stone_house_3().save(MAIN_STRUCTURES, "stone_house_3")
    settlers_camp().save(os.path.join(MAIN_STRUCTURES, "camp"), "settlers_camp")
    bandit_camp().save(os.path.join(MAIN_STRUCTURES, "camp"), "bandit_camp")
    for name, draw in (("traveller_camp", traveller_camp), ("prisoner_cage", prisoner_cage), ("castaway_camp", castaway_camp),
                       ("hermit_hut", hermit_hut), ("founder_statue", founder_statue)):
        draw().save(os.path.join(MAIN_STRUCTURES, "legend"), name)
    for name, draw in (("palisade", palisade), ("palisade_gate", palisade_gate), ("palisade_tower", palisade_tower), ("stone_wall", stone_wall), ("wall_tower", wall_tower),
                       ("gatehouse", gatehouse), ("barracks", barracks), ("barracks_2", barracks_2)):
        draw().save(MAIN_STRUCTURES, name)
    tinkers_workshop().save(MAIN_STRUCTURES, "tinkers_workshop")
    tinkers_workshop_2().save(MAIN_STRUCTURES, "tinkers_workshop_2")
    for name, draw in (("smithy", smithy), ("smithy_2", smithy_2), ("masons_yard", masons_yard), ("masons_yard_2", masons_yard_2),
                       ("fletchers_lodge", fletchers_lodge), ("fletchers_lodge_2", fletchers_lodge_2), ("map_room", map_room)):
        draw().save(MAIN_STRUCTURES, name)
    for name, draw in (("farmstead", farmstead), ("farmstead_2", farmstead_2), ("fishers_hut", fishers_hut), ("fishers_hut_2", fishers_hut_2),
                       ("weavers_cottage", weavers_cottage), ("weavers_cottage_2", weavers_cottage_2), ("bandstand", bandstand)):
        draw().save(MAIN_STRUCTURES, name)
    nether_gate().save(MAIN_STRUCTURES, "nether_gate")
    nether_gate_2().save(MAIN_STRUCTURES, "nether_gate_2")
    chapel().save(MAIN_STRUCTURES, "chapel")
    compost_yard().save(MAIN_STRUCTURES, "compost_yard")
    compost_yard_2().save(MAIN_STRUCTURES, "compost_yard_2")
    sifting_shed().save(MAIN_STRUCTURES, "sifting_shed")
    sifting_shed_2().save(MAIN_STRUCTURES, "sifting_shed_2")
    pokemon_center().save(MAIN_STRUCTURES, "pokemon_center")
    pokemon_center_2().save(MAIN_STRUCTURES, "pokemon_center_2")
    for name, draw in JOB_BUILDS:  # ROADMAP 28.13: the Pokémon jobs' builds
        draw().save(MAIN_STRUCTURES, name)
    guildhall().save(MAIN_STRUCTURES, "guildhall")
    guildhall_2().save(MAIN_STRUCTURES, "guildhall_2")
    for name, draw in ARENAS:  # ROADMAP 28.16: the Arena's three tiers
        draw().save(MAIN_STRUCTURES, name)
    for style in VILLAGE_STYLES:
        builders_workshop(style).save(VILLAGE_STRUCTURES, f"{style}_builders_workshop")
        for name, fit_out in VILLAGE_HOUSES.items():
            job = HOUSE_JOBS.get(name)
            staffed_house(style, fit_out, "aliveworkplace:" + job if job else None).save(VILLAGE_STRUCTURES, f"{style}_{name}")
    for name in WORKPLACES + COBBLEMON_WORKPLACES:
        workplace(name).save(MAIN_STRUCTURES, name)
    test_hut().save(TEST_STRUCTURES, "test_hut")
    test_hut_2().save(TEST_STRUCTURES, "test_hut_2")
    test_area("build_area", 17, 8, 17)
    test_area("big_area", 22, 18, 22)
    test_area("huge_area", 30, 20, 30)
    test_area("tall_area", 30, 30, 30)  # ROADMAP 30.17: the Guildhall II's 20-tall tower, built from y = 2
    test_area("build_area", 17, 8, 17, COMPAT_AREAS)
    test_area("huge_area", 30, 20, 30, COMPAT_AREAS)
