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
from workplaces import *
from countryside import *
from nether import *
from yards import *
from bandits import *
from bandit_fort import bandit_fort, bandit_fort_2, bandit_fort_3
from caravan import caravan_wreck, raiders_stockade, trail_1, trail_2, trail_3, trail_4, trail_5, TRAIL
from legend_sites import *
from pokemon import *
from pokemon_looks import *
from pokemon_jobs import *
from guildhall import *
from arena import *
from answering import *
from realm import *
from village_hall import village_hall, village_hall_2, village_hall_3
from wonders import WONDERS, save_wonder
from styles import styled
from basements import LEVEL_4, cutaway
from cellars import LEVEL_3
from create_builds import *
from quarry import quarry
from waystones import waystone_plaza, save_all as save_waystone_plazas
from storage_halls import storage_hall, terminal_nook, warehouse, save_all as save_storage_halls

# The blueprints redrawn with the rebuild kit (REBUILD.md): rebuild.py saves them, generate.py leaves them alone
REBUILT = {"well", "well_2", "well_house", "fountain", "lily_pond", "footbridge",
           # 52.2, the street pieces
           "park_bench", "sundial", "flower_planters", "fire_pit", "gazebo", "topiary_garden", "stag_statue",
           # 50.2, the workshops: tools/blueprints/js/<family>/ (MC_BUILDER=<minecraft-builder skill>; masons_yard: npm
           # run build with LEVEL=1|2), then python3 tools/blueprints/rebuild.py <family> <id>
           "masons_yard", "masons_yard_2", "sawmill", "carpenters_workshop",
           # 50.3, the forges: js/smithy (MC_BUILDER=..., sh build.sh writes candidates; live: rebuild.py smithy <id>
           # --nbt out/<id>.nbt --basement 4 --cellar "$(cat out/cellar.txt)"), js/tinkers_workshop (LEVEL=1|2) and
           # js/brass_foundry (Create only), then rebuild.py <family> <id>
           "smithy", "smithy_2", "tinkers_workshop", "tinkers_workshop_2", "brass_foundry",
           # 50.4, the Butcher's Shop: js/butchers_shop (MC_BUILDER=..., sh build.sh writes the candidate; live:
           # rebuild.py butchers_shop butchers_shop --nbt out/butchers-shop.nbt --basement 4 --cellar "$(cat out/cellar.txt)")
           "butchers_shop",
           # 50.4, the Winery: js/winery (MC_BUILDER=..., sh build.sh writes the candidates; LIVE=1 sh build.sh the live
           # winery, winery_2 and winery_3, III with --basement 4 --cellar "$(cat out/cellar.txt)")
           "winery", "winery_2", "winery_3",
           # 50.5, the Watermill (Create only): js/watermill (MC_BUILDER=..., sh build.sh writes the candidate; LIVE=1 sh
           # build.sh the live one); the Ferry House (the buildable only; its village pieces stay village.py's):
           # js/ferry_house, the same way
           "watermill", "ferry_house",
           # 50.1, the Builder's Workshop (Version B): js/builders_workshop_b, then rebuild.py builders_workshop_b
           # builders_workshop; its village pieces are that blueprint restyled (village.py builders_workshop)
           "builders_workshop",
           "camp_kitchen", "camp_kitchen_2",
           # 52.3, the walls (the palisade_tower follows)
           "stone_wall", "stone_wall_2", "wall_tower", "wall_tower_2", "palisade_gate", "palisade", "palisade_2",
           # 51.4, the Library (four levels) and the Schoolhouse: tools/blueprints/js/{library,schoolhouse}/
           # (MC_BUILDER=<minecraft-builder skill>; library and schoolhouse: build.sh with LEVEL=1..4, library_4 with
           # --basement 4 --cellar out/cellar.txt), then python3 tools/blueprints/rebuild.py <family> <id>. The School
           # (round 3, "the Half Court"): js/school (npm run cut), then rebuild.py school school; its village pieces keep
           # their own looks (village.py's staffed house)
           "library", "library_2", "library_3", "library_4", "schoolhouse", "schoolhouse_2", "school",
           # 49.9, the Ranch: js/ranch (MC_BUILDER=..., sh build.sh writes the candidates; LIVE=1 sh build.sh the live
           # ranch and ranch_2)
           "ranch", "ranch_2",
           # 49.8, the Weaver's Cottage: js/weavers_cottage (MC_BUILDER=..., sh build.sh writes the candidates; LIVE=1 sh
           # build.sh the live weavers_cottage and weavers_cottage_2)
           "weavers_cottage", "weavers_cottage_2",
           # 49.9, the Farmstead: js/farmstead (MC_BUILDER=..., sh build.sh writes the candidates; LIVE=1 sh build.sh the
           # live farmstead, farmstead_2 and farmstead_3, III with its 4-deep cellar); countryside.py and cellars.py keep
           # the old drawings, which ship in blueprint_history/
           "farmstead", "farmstead_2", "farmstead_3",
           # 51.6, the Post Office ("the Signal Tower") and the Graveyard ("the Lantern Yard"): js/post_office (npm run
           # cut), then rebuild.py post_office post_office --nbt out/post-office.nbt; js/graveyard (npm run cut, LEVEL=2
           # for graveyard_2), then rebuild.py graveyard <id> --nbt out/<graveyard|graveyard-2>.nbt. The post office's
           # village pieces keep their own looks (village.py's staffed house)
           "post_office", "graveyard", "graveyard_2",
           # 51.6, the Clock Tower ("the Water Clock", Create only): js/clock_tower (npm run cut), then rebuild.py
           # clock_tower clock_tower --nbt out/clock-tower.nbt
           "clock_tower",
           # 51.6, the Chapel ("the Blossom Court"): js/chapel (npm run cut), then rebuild.py chapel chapel --nbt
           # build/rebuild/chapel/out/chapel.nbt
           "chapel",
           # 51.7, the Market Cross ("the Mercat Drum"): js/market_cross (npm run cut), then rebuild.py market_cross
           # market_cross --nbt build/rebuild/market_cross/out/market-cross.nbt (decor.py's market_cross() is the old one)
           "market_cross",
           # 51.7, the Market Corner (round 2) and the Market Square ("the Market House"): js/market_corner and
           # js/market_square (npm run cut), then rebuild.py market_corner market_corner and rebuild.py market_square
           # market_square --nbt build/rebuild/market_square/out/market-square.nbt (decor.py's are the old ones)
           "market_corner", "market_square",
           # 52.4, the portals: js/nether_gate (LEVEL=1|2), js/waystone_plaza (LOOK=<culture>) and js/mega_shrine
           # (MC_BUILDER=...; npm run build writes candidates; live: rebuild.py <family> <id> [--nbt <out .nbt>])
           "nether_gate", "nether_gate_2", "waystone_plaza", "mega_shrine",
           # 52.5, the story and legend sites: js/{founder_statue,shift_bell,prisoner_cage,castaway_camp,traveller_camp}
           # (MC_BUILDER=...), then rebuild.py <family> legend/<family> (story/shift_bell) --nbt <out .nbt>; the
           # Traveler's Camp's map frame afterwards: python3 tools/blueprints/js/traveller_camp/frames.py <live .nbt>
           "founder_statue", "shift_bell", "prisoner_cage", "castaway_camp", "traveller_camp",
           # 50.6, the Fletcher's Lodge, the Tailor's Shop and the Print Shop (the buildables only; the village tailor's and
           # print shops stay village.py's): js/<family> (MC_BUILDER=..., sh build.sh writes the candidates; LIVE=1 sh build.sh
           # the live ones)
           "fletchers_lodge", "fletchers_lodge_2", "tailors_shop", "tailors_shop_2", "print_shop", "print_shop_2",
           # 50.7, the Glassblower's, Jeweler's and Crafter's Workshops and the Workshop (Create only; the buildables only:
           # the village jeweler's and crafter's pieces stay village.py's): js/<family> (MC_BUILDER=..., sh build.sh writes
           # the candidates; LIVE=1 sh build.sh the live ones, the jeweler's III with its Deep Vault)
           "glassblowers_workshop", "jewellers_workshop", "jewellers_workshop_2", "jewellers_workshop_3",
           "crafters_workshop", "workshop",
           # 52.5, the trail marks ("the Carter's Rags"): js/trail (MC_BUILDER=...; npm run build writes candidates, npm
           # run live the five live story/trail_N); their old drawings in caravan.py are no longer saved
           "trail_1", "trail_2", "trail_3", "trail_4", "trail_5",
           # 52.5, the Raiders' Stockade, the Bandit Fort (I-III) and the Caravan Wreck: js/{raiders_stockade,bandit_fort,
           # caravan_wreck} (MC_BUILDER=...; npm run build writes candidates, npm run live the live drawings, the
           # stockade's loot table included); their old drawings in caravan.py and bandit_fort.py are no longer saved
           "raiders_stockade", "bandit_fort", "bandit_fort_2", "bandit_fort_3", "caravan_wreck",
           # 52.5, the Bandit Camp (the Lookout Camp): js/bandit_camp (MC_BUILDER=...; npm run build writes the candidate,
           # npm run live the live camp/bandit_camp through its ship.py: the loot chest's table, the undrawn ground left
           # out); its old drawing in bandits.py is no longer saved
           "bandit_camp",
           # 51.8, the defences: js/barracks and js/lookout_tower (MC_BUILDER=..., sh build.sh; LIVE=1 for the barracks'
           # live drawings, III with --basement 4 --cellar out/cellar.txt; the Lookout Tower's IV the same way),
           # js/guard_house, js/fire_station and js/gatehouse (npm run cut, LEVEL=2 for gatehouse_2), then rebuild.py
           # <family> <id> --nbt out/<id with dashes>.nbt. The old drawings (defence.py, starter.py, cellars.py,
           # basements.py, workplaces.py, answering.py) ship in blueprint_history/; the Guard House's village pieces keep
           # their own looks (village.py's staffed house)
           "barracks", "barracks_2", "barracks_3", "guard_house", "fire_station", "gatehouse", "gatehouse_2",
           "lookout_tower", "lookout_tower_2", "lookout_tower_3", "lookout_tower_4",
           # 51.5, the Leader's Hall (round 1, "the Challenge Hall") and the Trade Hall (round 3, "the Ball Tower"):
           # tools/blueprints/js/{leaders_hall,trade_hall}/ (MC_BUILDER=<minecraft-builder skill>; node
           # "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955), then python3
           # tools/blueprints/rebuild.py <family> <family>; their village pieces keep their own looks (village.py's
           # staffed house)
           "leaders_hall", "trade_hall",
           # 50.8, the Clinic ("the pavilion ward"; the buildable only: the village clinics stay village.py's staffed
           # house): js/clinic (MC_BUILDER=..., sh build.sh writes the candidate; LIVE=1 sh build.sh the live one; then
           # modded_library.py clinic)
           "clinic",
           # 50.8, the Physician's House ("the still room and ward round a physic garden") and the Healing Center (I-IV,
           # "the Spring Infirmary", IV with its Cistern cellar): js/physicians_house and js/healing_center (MC_BUILDER=...,
           # sh build.sh writes the candidates; LIVE=1 sh build.sh the live ones; then modded_library.py <ids>)
           "physicians_house", "healing_center", "healing_center_2", "healing_center_3", "healing_center_4",
           # 50.9, the Berry Farm (I-II, "the berry pergola"), the Apiary Garden (I-II, "the Skep Wall") and the Greenhouse
           # (Create only, "the Leat House"): js/berry_farm, js/apiary_garden and js/greenhouse (MC_BUILDER=..., sh build.sh
           # writes the candidates; LIVE=1 sh build.sh the live ones; then modded_library.py for the berry farm and apiary)
           "berry_farm", "berry_farm_2", "apiary_garden", "apiary_garden_2", "greenhouse",
           # 51.9, the Royal Hall ("the Brick Palace"): js/royal_hall (MC_BUILDER=...; sh build.sh writes candidates,
           # LIVE=1 sh build.sh saves realm/royal_hall and realm/royal_hall_2)
           "royal_hall", "royal_hall_2",
           # 50.11, the Flower Shop ("the glasshouse florist"; the buildables only: the five village flower shops keep
           # their own looks, village.py and village_looks.py): js/flower_shop (MC_BUILDER=..., sh build.sh writes the
           # candidates; LIVE=1 sh build.sh the live ones; then modded_library.py flower_shop flower_shop_2)
           "flower_shop", "flower_shop_2",
           # 50.11, the Supply Shop (I-IV, "the hoist house"; IV with its cellar): js/supply_shop (MC_BUILDER=..., sh
           # build.sh writes the candidates; LIVE=1 sh build.sh the live ones; then modded_library.py <ids>)
           "supply_shop", "supply_shop_2", "supply_shop_3", "supply_shop_4",
           # 50.11, the Market Stall (I-III, "the shop board"): js/market_stall (MC_BUILDER=..., sh build.sh writes the
           # candidates; LIVE=1 sh build.sh the live ones; then modded_library.py market_stall market_stall_2 market_stall_3)
           "market_stall", "market_stall_2", "market_stall_3",
           # 50.9, the Compost Yard (I-II, "the Bin Barn"): js/compost_yard (MC_BUILDER=..., sh build.sh writes the
           # candidates; LIVE=1 sh build.sh the live ones; then modded_library.py compost_yard compost_yard_2)
           "compost_yard", "compost_yard_2",
           # 50.10, the Quarry ("the Headframe", no basement) and the Sifting Shed (I-II, "the Tipple"): js/quarry and
           # js/sifting_shed (MC_BUILDER=..., sh build.sh writes the candidates; LIVE=1 sh build.sh the live ones; then
           # modded_library.py quarry sifting_shed sifting_shed_2)
           "quarry", "sifting_shed", "sifting_shed_2",
           # 50.12, the Trainer's House ("the Battle Court"; the buildable only: the five village trainer's houses keep
           # their own looks, village.py and village_looks.py): js/trainers_house (MC_BUILDER=..., sh build.sh writes the
           # candidate; LIVE=1 sh build.sh the live one; then modded_library.py trainers_house)
           "trainers_house",
           # 50.10, the Crushing Mill ("the Fall House") and the Windmill ("the Stage Mill"), Create only: js/crushing_mill
           # and js/windmill (MC_BUILDER=..., sh build.sh writes the candidates; LIVE=1 sh build.sh the live ones)
           "crushing_mill", "windmill",
           # 50.12, the Habitat Garden (I-II, "the Stone Ring"): js/habitat_garden (MC_BUILDER=..., sh build.sh writes
           # the candidates; LIVE=1 sh build.sh the live ones; then modded_library.py habitat_garden habitat_garden_2);
           # pokemon_jobs.habitat_garden* are the old drawings, kept in blueprint_history/
           "habitat_garden", "habitat_garden_2",
           # 50.12, the Arena (I-III, "the Market Stadium"): js/arena (MC_BUILDER=..., sh build.sh writes the candidates;
           # LIVE=1 sh build.sh the live ones; no modded copies); arena.py's ARENAS are the old drawings, kept in
           # blueprint_history/
           "arena", "arena_2", "arena_3",
           # 50.12, the Ball Workshop ("the Drying Loft"; the buildable only: the five village ball workshops keep their
           # own looks, village.py and village_looks.py): js/ball_workshop (MC_BUILDER=..., sh build.sh writes the
           # candidate; LIVE=1 sh build.sh the live one; then modded_library.py ball_workshop); workplaces.py's
           # ball_workshop_build is the old drawing, kept in blueprint_history/
           "ball_workshop",
           # 50.12, the Research Lab (I-III, "the Strata Lab"; III with its 4-deep Dig): js/research_lab (MC_BUILDER=...,
           # sh build.sh writes the candidates; LIVE=1 sh build.sh the live ones, III with --basement 4 --cellar
           # out/cellar.txt; then modded_library.py research_lab research_lab_2 research_lab_3); starter.research_lab*
           # and cellars.research_lab_3 are the old drawings, kept in blueprint_history/
           "research_lab", "research_lab_2", "research_lab_3"}


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


def test_pillar():
    """5x15x5 (ROADMAP 35.6): a 14-high stone-brick pillar with a 5x5 polished andesite cap overhanging it by two on
    every side. Nothing to stand on near its top or under its cap: the builder puts up scaffolding. Needs 14 stone
    bricks and 25 polished andesite."""
    b = Build(5, 15, 5)
    b.fill(2, 0, 2, 2, 13, 2, "stone_bricks")
    b.fill(0, 14, 0, 4, 14, 4, "polished_andesite")
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
    # ROADMAP 49.3: the Starter Cottage (I-IV) comes from the rebuild kit (tools/blueprints/js/starter_cottage_b/build.sh,
    # LIVE=1 to save the live drawings); its old Python drawing in starter.py and basements.py is no longer saved
    # ROADMAP 50.11: the Market Stall comes from the rebuild kit (REBUILT); starter.market_stall* are the old drawings,
    # kept in blueprint_history/
    for name, draw in (("market_stall", market_stall), ("market_stall_2", market_stall_2), ("market_stall_3", market_stall_3)):
        if name not in REBUILT:
            draw().save(MAIN_STRUCTURES, name)
    for name, draw in (("lookout_tower", lookout_tower), ("lookout_tower_2", lookout_tower_2), ("lookout_tower_3", lookout_tower_3)):
        if name not in REBUILT:  # ROADMAP 51.8: the Lookout Tower comes from the rebuild kit
            draw().save(MAIN_STRUCTURES, name)
    # ROADMAP 50.8: the Healing Center (I-IV) comes from the rebuild kit (REBUILT); starter.healing_center* and
    # basements.healing_center_4 are the old drawings, kept in blueprint_history/
    for name, draw in (("healing_center", healing_center), ("healing_center_2", healing_center_2),
                       ("healing_center_3", healing_center_3)):
        if name not in REBUILT:
            draw().save(MAIN_STRUCTURES, name)
    # ROADMAP 50.11: the Supply Shop (I-IV) comes from the rebuild kit (REBUILT); starter.supply_shop* and
    # basements.supply_shop_4 are the old drawings, kept in blueprint_history/
    for name, draw in (("supply_shop", supply_shop), ("supply_shop_2", supply_shop_2), ("supply_shop_3", supply_shop_3)):
        if name not in REBUILT:
            draw().save(MAIN_STRUCTURES, name)
    storehouse().save(MAIN_STRUCTURES, "storehouse")
    storehouse_2().save(MAIN_STRUCTURES, "storehouse_2")
    storehouse_3().save(MAIN_STRUCTURES, "storehouse_3")
    # ROADMAP 50.9: the Berry Farm and the Apiary Garden come from the rebuild kit (REBUILT); starter.berry_farm* and
    # starter.apiary_garden* are the old drawings, kept in blueprint_history/
    for name, draw in (("berry_farm", berry_farm), ("berry_farm_2", berry_farm_2)):
        if name not in REBUILT:
            draw().save(MAIN_STRUCTURES, name)
    # ROADMAP 50.12: the Research Lab comes from the rebuild kit (REBUILT); starter.research_lab* are the old drawings
    for name, draw in (("research_lab", research_lab), ("research_lab_2", research_lab_2)):
        if name not in REBUILT:
            draw().save(MAIN_STRUCTURES, name)
    # ROADMAP 49.6: the Terrace (I-II) comes from the rebuild kit (tools/blueprints/js/terrace/build.sh, then
    # rebuild.py terrace <id>); its old Python drawing in starter.py is no longer saved
    inn().save(MAIN_STRUCTURES, "inn")
    inn_2().save(MAIN_STRUCTURES, "inn_2")
    # ROADMAP 51.4: the Schoolhouse and the Library come from the rebuild kit (REBUILT); their old drawings are kept in
    # blueprint_history/
    for name, draw in (("ranch", ranch), ("ranch_2", ranch_2)):
        if name not in REBUILT:  # 49.9: the Ranch comes from the rebuild kit
            draw().save(MAIN_STRUCTURES, name)
    for name, draw in (("apiary_garden", apiary_garden), ("apiary_garden_2", apiary_garden_2)):
        if name not in REBUILT:
            draw().save(MAIN_STRUCTURES, name)
    # ROADMAP 50.11: the Flower Shop comes from the rebuild kit (REBUILT); starter.flower_shop_building* are the old
    # drawings, kept in blueprint_history/
    for name, draw in (("flower_shop", flower_shop_building), ("flower_shop_2", flower_shop_building_2)):
        if name not in REBUILT:
            draw().save(MAIN_STRUCTURES, name)
    for name, draw in (("graveyard", graveyard), ("graveyard_2", graveyard_2)):
        if name not in REBUILT:  # ROADMAP 51.6: the Graveyard comes from the rebuild kit
            draw().save(MAIN_STRUCTURES, name)
    town_hall().save(os.path.join(MAIN_STRUCTURES, "research"), "town_hall")
    for name, draw in (("village_hall", village_hall), ("village_hall_2", village_hall_2), ("village_hall_3", village_hall_3)):
        draw().save(MAIN_STRUCTURES, name)
    # ROADMAP 52.1: the well, the fountain, the lily pond, the footbridge and the well house come from the rebuild kit
    # (tools/blueprints/js/<family>/, saved by tools/blueprints/rebuild.py), so their old Python drawings no longer
    # overwrite them
    # (52.2: the park bench and the gazebo too)
    for name, draw in (("street_lamp", street_lamp), ("park_bench", park_bench), ("gazebo", gazebo),
                       ("market_square", market_square)):
        if name not in REBUILT:
            draw().save(MAIN_STRUCTURES, name)
    for name, draw in MORE_DECORATIONS:  # ROADMAP 26.14
        if name not in REBUILT:
            draw().save(MAIN_STRUCTURES, name)
    for name, draw in CREATE_BUILDS + MORE_CREATE_BUILDS:  # ROADMAP 39.4 and 39.8: listed only with Create
        if name in REBUILT:
            continue  # ROADMAP 50.2, 50.3, 50.5 and 50.7: drawn by the rebuild kit
        draw().save(MAIN_STRUCTURES, name)
    for name, draw in TRAIN_STATION:  # ROADMAP 39.7: listed only with Create and villageTrains
        draw().save(MAIN_STRUCTURES, name)
    village_train().save(os.path.join(MAIN_STRUCTURES, "train"), "village_train")  # ROADMAP 39.7c: the village's Create train
    # ROADMAP 38.7 mega_shrine and 41.1 waystone_plaza (all five looks) come from the rebuild kit since 52.4
    # ROADMAP 49.4: the Stone House (I-IV) comes from the rebuild kit (tools/blueprints/js/stone_house/build.sh, then
    # rebuild.py stone_house <id>); its old Python drawing in houses.py and basements.py is no longer saved
    # ROADMAP 49.5: the Townhouse (I-IV) comes from the rebuild kit too (tools/blueprints/js/townhouse/build.sh, then
    # rebuild.py townhouse <id>); its old Python drawing is no longer saved
    # ROADMAP 49.7: the Noble's Manor (I-IV) comes from the rebuild kit (tools/blueprints/js/manor/build.sh, LIVE=1 to
    # save the live drawings); its old Python drawing in houses.py and basements.py is no longer saved
    # ROADMAP 49.8: the Artisan's House (I-III) comes from the rebuild kit too (tools/blueprints/js/artisans_house/build.sh,
    # LIVE=1 to save the live drawings); its old Python drawing in houses.py and cellars.py is no longer saved
    settlers_camp().save(os.path.join(MAIN_STRUCTURES, "camp"), "settlers_camp")
    if "bandit_camp" not in REBUILT:  # 52.5: the Bandit Camp comes from the rebuild kit
        bandit_camp().save(os.path.join(MAIN_STRUCTURES, "camp"), "bandit_camp")
    for name, draw in (("bandit_fort", bandit_fort), ("bandit_fort_2", bandit_fort_2), ("bandit_fort_3", bandit_fort_3)):
        if name not in REBUILT:  # 52.5: the Bandit Fort comes from the rebuild kit
            draw().save(os.path.join(MAIN_STRUCTURES, "story"), name)  # ROADMAP 31.15: the Bandit King's fort
    if "shift_bell" not in REBUILT:  # 52.5: the Shift Bell comes from the rebuild kit
        shift_bell().save(os.path.join(MAIN_STRUCTURES, "story"), "shift_bell")  # ROADMAP 31.22: The Shift Bell's ending
    for name, draw in (("caravan_wreck", caravan_wreck), ("raiders_stockade", raiders_stockade)):  # ROADMAP 31.19
        if name not in REBUILT:  # 52.5: the wreck and the stockade come from the rebuild kit
            draw().save(os.path.join(MAIN_STRUCTURES, "story"), name)
    for i, draw in enumerate(TRAIL, 1):
        if f"trail_{i}" not in REBUILT:  # 52.5: the trail marks come from the rebuild kit
            draw().save(os.path.join(MAIN_STRUCTURES, "story"), f"trail_{i}")
    war_camp().save(os.path.join(MAIN_STRUCTURES, "camp"), "war_camp")
    pirate_ship().save(os.path.join(MAIN_STRUCTURES, "camp"), "pirate_ship")  # ROADMAP 32.8
    tomb_camp().save(os.path.join(MAIN_STRUCTURES, "camp"), "tomb_camp")  # ROADMAP 32.9
    blackstone_outpost().save(os.path.join(MAIN_STRUCTURES, "camp"), "blackstone_outpost")  # ROADMAP 32.10
    coven_hut().save(os.path.join(MAIN_STRUCTURES, "camp"), "coven_hut")  # ROADMAP 32.11
    colony_camp().save(os.path.join(MAIN_STRUCTURES, "camp"), "colony_camp")  # ROADMAP 33.14
    for name, draw in (("royal_hall", royal_hall), ("royal_hall_2", royal_hall_2)):  # ROADMAP 33.14
        if name not in REBUILT:  # 51.9: the Royal Hall comes from the rebuild kit
            draw().save(os.path.join(MAIN_STRUCTURES, "realm"), name)
    for name, draw in (("traveller_camp", traveller_camp), ("prisoner_cage", prisoner_cage), ("castaway_camp", castaway_camp),
                       ("hermit_hut", hermit_hut), ("founder_statue", founder_statue)):
        if name not in REBUILT:  # 52.5: all but the Hermit's Hut come from the rebuild kit
            draw().save(os.path.join(MAIN_STRUCTURES, "legend"), name)
    for name, draw in (("palisade", palisade), ("palisade_gate", palisade_gate), ("palisade_tower", palisade_tower), ("stone_wall", stone_wall), ("wall_tower", wall_tower),
                       ("gatehouse", gatehouse), ("barracks", barracks), ("barracks_2", barracks_2),
                       ("palisade_2", palisade_2), ("stone_wall_2", stone_wall_2), ("wall_tower_2", wall_tower_2), ("gatehouse_2", gatehouse_2)):
        if name not in REBUILT:  # 52.3: the stone wall, the wall tower, the palisade gate and the palisade are rebuilt
            draw().save(MAIN_STRUCTURES, name)
    # ROADMAP 50.3: the Tinker's Workshop and the Smithy come from the rebuild kit (REBUILT)
    for name, draw in (("fletchers_lodge", fletchers_lodge), ("fletchers_lodge_2", fletchers_lodge_2), ("map_room", map_room)):
        if name not in REBUILT:  # ROADMAP 50.6: the Fletcher's Lodge comes from the rebuild kit
            draw().save(MAIN_STRUCTURES, name)
    for name, draw in (("farmstead", farmstead), ("farmstead_2", farmstead_2), ("fishers_hut", fishers_hut), ("fishers_hut_2", fishers_hut_2),
                       ("weavers_cottage", weavers_cottage), ("weavers_cottage_2", weavers_cottage_2), ("bandstand", bandstand)):
        if name not in REBUILT:  # 49.8: the Weaver's Cottage comes from the rebuild kit
            draw().save(MAIN_STRUCTURES, name)
    for name, draw in (("winery", winery), ("winery_2", winery_2), ("tailors_shop", tailors_shop), ("tailors_shop_2", tailors_shop_2)):
        if name not in REBUILT:  # ROADMAP 50.4 and 50.6: the Winery and the Tailor's Shop come from the rebuild kit
            draw().save(MAIN_STRUCTURES, name)  # ROADMAP 34.13
    for name, draw in (("print_shop", print_shop), ("print_shop_2", print_shop_2), ("jewellers_workshop", jewellers_workshop),
                       ("jewellers_workshop_2", jewellers_workshop_2)):
        if name not in REBUILT:  # ROADMAP 50.6 and 50.7: the Print Shop and the Jeweler's Workshop come from the rebuild kit
            draw().save(MAIN_STRUCTURES, name)  # ROADMAP 34.14
    if "quarry" not in REBUILT:  # ROADMAP 50.10: the Quarry comes from the rebuild kit; quarry.quarry is the old drawing
        quarry().save(MAIN_STRUCTURES, "quarry")  # ROADMAP 36.12: the Miner's pit that grows back
    # ROADMAP 37.14 crafters_workshop and 46.6 glassblowers_workshop come from the rebuild kit since 50.7 (REBUILT);
    # workshops.crafters_workshop and glassblowers_workshop are the old drawings, kept in blueprint_history/
    save_storage_halls(MAIN_STRUCTURES, os.path.join(MAIN_STRUCTURES, "modded"))  # ROADMAP 37.17: with their Tom's copies
    for name, draw in (("fire_station", fire_station), ("physicians_house", physicians_house)):  # the well house: 52.1
        if name not in REBUILT:  # ROADMAP 51.8 / 50.8: the Fire Station and the Physician's House come from the rebuild kit
            draw().save(MAIN_STRUCTURES, name)  # ROADMAP 32.19
    for name, draw in LEVEL_4:  # ROADMAP 26.15: the Level 4s, each with a basement
        if name not in REBUILT:  # ROADMAP 51.4 and 50.8: library_4 and healing_center_4 come from the rebuild kit
            draw().save(MAIN_STRUCTURES, name)
    # ROADMAP 50.4: the Butcher's Shop (with its freezer) comes from the rebuild kit (REBUILT); basements.butchers_shop
    # is the old drawing, kept in blueprint_history/
    for name, draw in LEVEL_3:  # ROADMAP 36.7: Level IIIs with a cellar for the builds that stopped at II
        if name not in REBUILT:  # ROADMAP 50.4, 50.7 and 50.12: winery_3, jewellers_workshop_3 and research_lab_3 come from the rebuild kit
            draw().save(MAIN_STRUCTURES, name)
    # ROADMAP 52.4: nether_gate and nether_gate_2 come from the rebuild kit (js/nether_gate)
    # ROADMAP 51.6: the Chapel comes from the rebuild kit (REBUILT); chapel() is the old drawing, kept in blueprint_history/
    # ROADMAP 50.9: the Compost Yard comes from the rebuild kit (REBUILT); yards.compost_yard* are the old drawings,
    # kept in blueprint_history/
    for name, draw in (("compost_yard", compost_yard), ("compost_yard_2", compost_yard_2)):
        if name not in REBUILT:
            draw().save(MAIN_STRUCTURES, name)
    # ROADMAP 50.10: the Sifting Shed comes from the rebuild kit (REBUILT); sifting_shed* here are the old drawings
    for name, draw in (("sifting_shed", sifting_shed), ("sifting_shed_2", sifting_shed_2)):
        if name not in REBUILT:
            draw().save(MAIN_STRUCTURES, name)
    pokemon_center().save(MAIN_STRUCTURES, "pokemon_center")
    pokemon_center_2().save(MAIN_STRUCTURES, "pokemon_center_2")
    for look, tier1, tier2 in POKEMON_CENTER_LOOKS:  # ROADMAP 28.7a: the Center's other looks
        tier1().save(os.path.join(MAIN_STRUCTURES, LOOK_FOLDER, look), "pokemon_center")
        tier2().save(os.path.join(MAIN_STRUCTURES, LOOK_FOLDER, look), "pokemon_center_2")
    for name, draw in JOB_BUILDS:  # ROADMAP 28.13: the Pokémon jobs' builds (52.2: the camp kitchen is rebuilt)
        if name not in REBUILT:
            draw().save(MAIN_STRUCTURES, name)
    guildhall().save(MAIN_STRUCTURES, "guildhall")
    guildhall_2().save(MAIN_STRUCTURES, "guildhall_2")
    for name, draw in ARENAS:  # ROADMAP 28.16: the Arena's three tiers (50.12: rebuilt, REBUILT; arena.py is the old drawing)
        if name not in REBUILT:
            draw().save(MAIN_STRUCTURES, name)
    for style in VILLAGE_STYLES:
        builders_workshop(style).save(VILLAGE_STRUCTURES, f"{style}_builders_workshop")
        for name, fit_out in VILLAGE_HOUSES.items():
            job = HOUSE_JOBS.get(name)
            staffed_house(style, fit_out, "aliveworkplace:" + job if job else None).save(VILLAGE_STRUCTURES, f"{style}_{name}")
        # ROADMAP 49.2: the Daub House (its blueprint comes from the rebuild kit, tools/blueprints/rebuild.py) as a village home
        daub_house_village(style).save(VILLAGE_STRUCTURES, f"{style}_{DAUB_HOUSE}")
    for name in WORKPLACES + COBBLEMON_WORKPLACES:
        if name in REBUILT:
            continue  # ROADMAP 50.2, 50.5 and 50.8: drawn by the rebuild kit (below)
        workplace(name).save(MAIN_STRUCTURES, name)
    for wonder_id in WONDERS:  # ROADMAP 35.9 on: the Wonders' stage parts and data files
        save_wonder(wonder_id)
    test_hut().save(TEST_STRUCTURES, "test_hut")
    test_hut_2().save(TEST_STRUCTURES, "test_hut_2")
    test_pillar().save(TEST_STRUCTURES, "test_pillar")
    test_pillar().save(DEVCLIENT_STRUCTURES, "test_pillar")  # the scaffolding scene's (35.6)
    test_area("build_area", 17, 8, 17)
    test_area("big_area", 22, 18, 22)
    test_area("huge_area", 30, 20, 30)
    test_area("tall_area", 30, 30, 30)  # ROADMAP 30.17: the Guildhall II's 20-tall tower, built from y = 2
    test_area("colony_area", 44, 20, 44)  # ROADMAP 33.10: a colony's camp with a build on either side is 35 blocks across
    test_area("build_area", 17, 8, 17, COMPAT_AREAS)
    test_area("huge_area", 30, 20, 30, COMPAT_AREAS)
    test_area("tall_area", 30, 30, 30, COMPAT_AREAS)  # ROADMAP 50.3: the Brass Foundry is 21 tall, built from y = 2
