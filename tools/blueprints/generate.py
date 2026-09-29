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
    for style in VILLAGE_STYLES:
        builders_workshop(style).save(VILLAGE_STRUCTURES, f"{style}_builders_workshop")
        for name, fit_out in VILLAGE_HOUSES.items():
            staffed_house(style, fit_out).save(VILLAGE_STRUCTURES, f"{style}_{name}")
    test_hut().save(TEST_STRUCTURES, "test_hut")
    test_hut_2().save(TEST_STRUCTURES, "test_hut_2")
    test_area("build_area", 17, 8, 17)
    test_area("big_area", 22, 18, 22)
    test_area("build_area", 17, 8, 17, COMPAT_AREAS)
