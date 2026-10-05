#!/usr/bin/env python3
"""The nightly showcase's scene catalog: every screenshot scene, the job it belongs to, and the stills the page shows.

    python3 tools/showcase/scenes.py list                  # every scene, one per line
    python3 tools/showcase/scenes.py matrix [--last F]     # GitHub Actions matrix: scenes split into shards
    python3 tools/showcase/scenes.py env SCENE             # KEY=VALUE lines for tools/screenshots/run.sh
    python3 tools/showcase/scenes.py check                 # every harness scene is in the catalog, and back
    python3 tools/showcase/scenes.py changed BASE [HEAD]   # the scenes a commit range added or changed ("all" if unsure)
    python3 tools/showcase/scenes.py matrix --only "a b"   # the matrix for just those scenes (ROADMAP 22.8)

A scene is one run of the screenshot client (tools/screenshots/run.sh, SCENE=<name>). The harness
(src/devclient/.../ScreenshotHarness.java) stages it, films it and records a pass/fail check in showcase.json.

Stills: 2-4 per scene, as (picture, label). A picture is a screenshot's file name without ".png", or a glob with a
pick: "frame_*@first", "frame_*@middle", "frame_*@last", "30_*@spread" (4 spread over the matches, labelled from
their names). A new scene goes here in the same commit as its harness code (ROADMAP 22.3, 22.4).
"""
import argparse
import fnmatch
import json
import math
import os
import re
import sys

# Page sections, in the README's order ("All the jobs at a glance"), then the village-wide pieces.
GROUPS = [
    "Picking a job", "Items", "Builder", "Miner", "Lumberjack", "Orchard Keeper", "Farmer", "Beekeeper", "Florist", "Scholar", "Sifter",
    "Tinkerer", "Composter", "Netherworker", "Undertaker", "Innkeeper", "Teacher", "Rancher", "Fisherman", "Porter",
    "Carpenter", "Mason", "Leatherworker (dyer)", "Chef", "Armorer (smelter)", "Toolsmith", "Weaponsmith", "Fletcher",
    "Shepherd", "Butcher (herder)", "Cleric (alchemist)", "Librarian (scribe)", "Cartographer (explorer)", "Postman",
    "Guard", "Nurse", "Shopkeeper", "Ferryman", "Bard", "Trainer", "Trainer Leader", "Move Tutor", "Ball Smith",
    "Pokémon Trader", "Fossil Scientist", "Berry Breeder", "Steward", "Camp Cook", "Village Hall", "Legends", "Everyone at work", "Build families",
]

START, WORKING, DONE = ("01_start", "Start"), ("work_*@middle", "At work"), ("03_done", "Done")


def S(name, group, title, what, est, stills, env=None, cobblemon=False, mega=False):
    """One scene. est: seconds the scene itself takes on GitHub's runner (the client's start-up is added)."""
    return {"name": name, "group": group, "title": title, "what": what, "est": est, "stills": stills,
            "env": env or {}, "cobblemon": cobblemon, "mega": mega}


def job(name, group, title, what, est=150, extra=(), **kw):
    """A scene staged by the harness's job scenes (JobScenes.java): start, at work, done, plus any extra stills."""
    return S(name, group, title, what, est, [START, WORKING, DONE, *extra][:4], **kw)


SCENES = [
    # Picking a job (ROADMAP 21.1a): one block, several jobs, each picked with its item
    S("stations", "Picking a job", "One composter, four jobs", "the villager took each job its item picks at the composter", 45,
      [("02_orchard_keeper", "Sweet berries: Orchard Keeper"), ("03_florist", "A flower: Florist"),
       ("04_composter", "Bone meal: Composter"), ("05_farmer", "Wheat: back to Farmer")]),
    # Items (ROADMAP 21.1b): the owner's picked icons in a chest and in hand
    S("items", "Items", "Every item's icon", "every item's icon shows in a chest, in the hotbar and in hand", 50,
      [("01_items_chest", "All 14 in a chest"), ("03_hand_blueprint", "Blueprint in hand"),
       ("04_hand_rally_banner", "Rally Banner in hand"), ("07_hand_field_marker", "Field Marker in hand")]),
    # Builder
    S("builders", "Builder", "Three builders, three starter builds", "the builders finished all three builds", 420,
      [("02_builder_closeup", "A builder at work"), ("03_finished_wide", "All three finished"),
       ("04_cottage", "Starter Cottage"), ("06_lookout_tower", "Lookout Tower")]),
    # Builder soak (ROADMAP 23.1): 10 builders, the whole starter set on hilly woods, chests only, no help (6 days, sprinted)
    S("soak", "Builder", "The builder soak: 10 builders, every starter build", "10 builders finished every starter build with nothing duplicated or lost", 1200,
      [("01_soak_start", "Hilly woods, 10 benches and their chests"), ("frame_*@middle", "Halfway"), ("02_soak_done", "Every build finished"),
       ("03_soak_close", "Close up")]),
    S("preview", "Builder", "Ghost preview and the status over a builder", "the builder made progress on the previewed site", 120,
      [("20_preview_start", "Ghost preview"), ("21_preview_half_built", "Half built"), ("22_status_closeup", "Over the builder: progress, what it is short of, where it takes from")]),
    S("placing", "Builder", "Placing a build: turn, mirror, cancel, move onto a slope", "on the slope the builder filled a foundation", 240,
      [("10_placed", "Placed: the ghost"), ("12_mirrored", "Turned and mirrored where it stands"),
       ("14_cancelled", "Cancelled: the blueprint is back, still placed"), ("16_slope_building", "Moved onto a slope: foundation filled")]),
    S("missing", "Builder", "What a build is still missing, and its material list", "the blueprint's tooltip lists what the chests are short of, and a Book and Quill becomes the list", 60,
      [("00_missing_site", "The placed blueprint and the Blueprint Table"), ("01_blueprint_missing", "Still-missing tooltip"),
       ("02_material_list", "The material list (a book)"), ("03_material_list_page", "What's still to bring")]),
    S("table", "Builder", "Blueprint Table", "the table screen opened and a file was uploaded", 60,
      [("10_table_library", "Library"), ("11_table_upload", "Upload a file"), ("12_table_uploaded", "Uploaded")]),
    S("shapes", "Builder", "Shape Planner", "the Shape Planner screen opened with its materials", 45,
      [("01_shapes_screen", "Shape Planner"), ("02_shapes_info", "Size and blocks")]),
    S("scan", "Builder", "The Scan Tool: your own build as a blueprint", "a hut was marked with the Scan Tool and saved as a blueprint", 30,
      [("01_scan_marked", "Two corners marked"), ("02_scan_saved", "Saved as a blueprint")]),
    S("style_menu", "Builder", "Build it in a style", "the style picker opened, a style was chosen and the build mirrored", 50,
      [("01_style_screen", "Style picker"), ("02_style_chosen", "Dark oak chosen"), ("03_style_mirrored", "Mirrored")]),
    # Miner
    S("quarry", "Miner", "A miner digs out a quarry", "the miner dug out the whole quarry", 420,
      [("frame_*@first", "Start"), ("frame_*@middle", "Digging"), ("50_quarry_done", "Dug out")]),
    # Lumberjack
    S("forest", "Lumberjack", "Felling and replanting four trees", "the lumberjack felled all four trees", 240,
      [("frame_*@first", "Start"), ("frame_*@middle", "Felling"), ("50_forest_done", "Felled and replanted")]),
    # Orchard Keeper
    S("orchard", "Orchard Keeper", "Picking berries, cocoa and apricorns", "the orchard keeper picked every fruit", 240,
      [("10_orchard_start", "Start"), ("20_orchard_partner", "With a Pokémon partner"), ("50_orchard_done", "All picked")],
      cobblemon=True),
    # Farmer
    S("farm", "Farmer", "A farmer works a field", "the farmer harvested and replanted the field", 180,
      [("frame_*@first", "Ripe field"), ("frame_*@middle", "Harvesting"), ("50_farm_done", "Replanted")]),
    job("beekeeper", "Beekeeper", "Harvesting a full beehive", "the beekeeper harvested the hive into the chest"),
    job("florist", "Florist", "Growing and picking flowers", "the florist grew and picked flowers"),
    job("scholar", "Scholar", "Research at the lectern", "the scholar finished a level of research", 150,
        [("04_research_screen", "Research screen")]),
    job("sifter", "Sifter", "Sifting gravel", "the sifter sifted gravel into loot"),
    job("tinkerer", "Tinkerer", "Mending an iron golem", "the tinkerer mended the iron golem"),
    job("composter", "Composter", "Turning scraps into bone meal", "the composter made bone meal"),
    job("steward", "Steward", "The Steward's morning rounds", "the steward walked his morning rounds and came back to the hall", 200),
    job("netherworker", "Netherworker", "A trip to the Nether", "the netherworker came back from the Nether with loot", 240),
    job("undertaker", "Undertaker", "Bringing a worker back from the grave", "the undertaker revived the villager"),
    # Legends (ROADMAP 29.2): the engine, with a Legend of the scene's own: the plain outfit, Master level, a pace power
    job("legend", "Legends", "A villager becomes a Legend",
        "a villager became a Legend, a Master who speeds up the builder beside them"),
    job("innkeeper", "Innkeeper", "A traveller checks in", "a traveller came to stay at the inn", 120,
        [("04_hire_screen", "Hire a traveller")]),
    job("teacher", "Teacher", "Lessons for the village children", "the teacher schooled the children", 180),
    job("rancher", "Rancher", "Breaking in a horse", "the rancher tamed and saddled the horse"),
    S("daycare", "Rancher", "Pokémon daycare", "the daycare screen opened with a boarder", 75,
      [("01_daycare_screen", "Daycare"), ("02_daycare_boarder", "A boarder")], cobblemon=True),
    # Fisherman
    S("fish", "Fisherman", "Fishing from the shore", "the fisherman caught a fish", 120,
      [("60_fish_0", "Casting"), ("60_fish_3", "The bobber out"), ("61_fish_caught", "Caught")]),
    S("extras", "Fisherman", "Out in a boat, on horseback, on the ferry", "the fisher boated, the guard rode and the ferry arrived", 150,
      [("70_boat_4", "A fisher out in a boat"), ("71_cavalry_1", "A guard on horseback"),
       ("72_ferry_1", "Ferry ride"), ("72_ferry_arrived", "Arrived")]),
    # Porter
    S("porter", "Porter", "Carrying goods to the storehouse", "the porter stocked the storehouse and the board opened", 90,
      [("*_porter@first", "Start"), ("*_porter@middle", "Carrying"), ("20_storehouse_closeup", "The storehouse"),
       ("21_request_board", "Requests board")]),
    job("dropbox", "Porter", "Emptying a Drop Box", "the porter emptied the Drop Box into the store", 120,
        [("04_dropbox_screen", "Drop Box")]),
    # Crafters
    S("carpenter", "Carpenter", "Making a builder's woodwork", "the carpenter made woodwork for the builder", 150,
      [("*_carpenter@first", "Start"), ("*_carpenter@middle", "Working"), ("30_carpenter_closeup", "At the crafting table")]),
    job("mason", "Mason", "Cutting stone for a builder", "the mason cut stone bricks the builder needed", 240),
    job("dyer", "Leatherworker (dyer)", "Dyeing wool for a builder", "the dyer made coloured wool the builder needed", 240),
    # Builder crews (ROADMAP 23.1a): a builder and three helpers on one build, about a third of the time alone
    job("crew", "Builder", "A crew of four on one build", "four builders built the stone house together, each placing a share of it", 150),
    S("chef", "Chef", "Cooking at the smoker", "the chef cooked food into the chest", 120,
      [("*_chef@first", "Start"), ("*_chef@middle", "Cooking"), ("*_chef@last", "Done")]),
    job("smelter", "Armorer (smelter)", "Smelting ore in the blast furnace", "the armorer smelted the ore into ingots"),
    job("toolsmith", "Toolsmith", "An axe for the lumberjack", "the toolsmith made the tool a worker asked for"),
    job("weaponsmith", "Weaponsmith", "Mending a worn pickaxe", "the weaponsmith mended the pickaxe"),
    job("fletcher", "Fletcher", "A bow for the guard", "the fletcher made a bow for the guard"),
    job("shepherd", "Shepherd", "Shearing sheep", "the shepherd sheared the sheep"),
    job("herder", "Butcher (herder)", "Milking a cow", "the herder milked the cow"),
    job("alchemist", "Cleric (alchemist)", "Brewing healing potions", "the alchemist brewed healing potions", 180),
    job("scribe", "Librarian (scribe)", "Enchanting a guard's sword", "the librarian enchanted the guard's sword"),
    job("explorer", "Cartographer (explorer)", "An expedition", "the explorer came back from an expedition with finds", 240),
    # Postman
    S("mail", "Postman", "Mailbox and a delivery", "the mailbox screen opened and the postman delivered the parcel", 150,
      [("01_mailbox_screen", "Mailbox screen"), ("frame_*@middle", "On the way"), ("50_mail_delivered", "Delivered")]),
    # Guard
    S("guard", "Guard", "A guard fights off three husks", "the guard killed the husks and sparred with the dummy", 150,
      [("10_guard_armor", "In armor"), ("50_guard_done", "Husks down"), ("55_training_dummy", "Training Dummy"),
       ("60_guard_training", "Sparring")]),
    S("partners_engine", "Builder", "A Pokémon partner helps the builder", "the Machop carried the planks to the work and came back", 60,
      [("01_partner_carries", "Shouldering the planks"), ("02_partner_at_work", "At the work"), ("03_partner_back", "Back, empty-handed")],
      cobblemon=True),
    # One pace, one cap (ROADMAP 30.2): a builder past the speed cap, then ill
    S("pace", "Builder", "One pace, one cap: a builder past the speed cap",
      "the capped builder's status line says \"at the cap\", and ill they're held back", 40,
      [("01_pace_capped", "Three partners, a kept village, research, a happy mood: 100% faster, at the cap"),
       ("02_pace_ill", "The same builder ill: held back to the usual pace")], cobblemon=True),
    S("partners_land", "Builder", "Pokémon partners at work: building and the land",
      "the Machamp carried the beams and the Wartortle watered the farmer's patch", 60,
      [("01_partners_carry", "Beams on its shoulder"), ("02_partners_at_work", "At work"), ("03_watered_field", "The patch watered")],
      cobblemon=True),
    S("partners_forge", "Postman", "Pokémon partners at work: the post and the forge",
      "a Pidgeotto took the air mail up out of sight and landed back, and a Charmander breathed fire into the blast furnace", 90,
      [("01_fire_and_take_off", "Fire into the furnace; the air mail takes off"), ("02_air_mail_climbs", "Up and out of sight"),
       ("03_back_down", "Back down, empty-handed")], cobblemon=True),
    S("partners_all", "Everyone at work", "Pokémon partners at work: everyone else",
      "each of the twelve workers' partners came to their work (dig, cast, study, lesson, cure, compost, grow, harvest, sift, depart, set out, tame)", 120,
      [("[12][0-9]_*@spread", "")], cobblemon=True),
    S("guard_pokemon", "Guard", "A guard with Pokémon at his side", "the guard and the Pokémon killed the husks", 180,
      [("10_guard_armor", "In armor"), ("frame_*@middle", "Pokémon join in"), ("50_guard_done", "Husks down")],
      cobblemon=True),
    job("nurse", "Nurse", "Healing and curing villagers", "the nurse healed and cured the villagers"),
    S("pokemon_center", "Nurse", "The Pokémon Center",
      "both tiers stand, and the nurse put the team in her Healing Machine and every Pokémon came out full", 60,
      [("01_pokemon_center", "Pokémon Center"), ("02_pokemon_center_2", "Pokémon Center II"),
       ("03_healing", "In the Healing Machine"), ("04_healed", "All healed")], cobblemon=True),
    # Shopkeeper
    S("shop", "Shopkeeper", "The shop", "the shop screen opened with prices", 60,
      [("01_shop_menu", "Shop screen"), ("02_shop_counter", "The counter")], cobblemon=True),
    S("counter", "Shopkeeper", "Setting prices at the Shop Counter", "the price list, the owner's sales log and a Price Tag's screen opened", 55,
      [("01_counter_screen", "Price list"), ("02_counter_price", "A price"), ("03_counter_sales", "Sales log"), ("04_price_tag", "Price Tag")]),
    # Ferryman
    S("ferry_menu", "Ferryman", "Buying a travel ticket", "the ferryman's ticket screen opened", 45,
      [("01_ferry_screen", "Destinations"), ("02_ferry_confirm", "Confirm the fare")]),
    job("bard", "Bard", "Playing at the jukebox", "the bard played music"),
    # Cobblemon
    S("battle", "Trainer", "A battle with a Master trainer", "the battle ran to the end and a Mega Evolution happened", 420,
      [("battle_*@first", "Battle starts"), ("battle_*@middle", "Mid-battle"), ("99_battle_end", "Battle over")],
      cobblemon=True, mega=True),
    S("leader", "Trainer Leader", "Challenging the Trainer Leader", "the Trainer Leader took the challenge", 90,
      [("01_leader", "At the podium"), ("02_leader_battle", "The battle starts")], cobblemon=True),
    S("tutor", "Move Tutor", "The Move Tutor's lessons", "the lesson screen opened", 60,
      [("01_tutor_screen", "Lessons"), ("02_tutor_lesson", "A lesson")], cobblemon=True),
    S("smith", "Ball Smith", "A Ball Smith at work", "the ball smith made balls", 150,
      [("01_smith_working", "At the smithing table"), ("02_smith_done", "Balls made")], cobblemon=True),
    S("smith_orders", "Ball Smith", "Choosing which balls to make", "the orders screen opened", 60,
      [("01_orders_screen", "Orders"), ("02_orders_picked", "A ball picked")], cobblemon=True),
    S("trader", "Pokémon Trader", "The Pokémon Trader's offers", "the trade screen opened", 60,
      [("01_trader_offer", "An offer"), ("02_trader_party", "A Pokémon that fits"), ("03_trader_refused", "One that doesn't")],
      cobblemon=True),
    job("fossil", "Fossil Scientist", "Reviving a fossil", "the fossil scientist revived the fossil", 150, cobblemon=True),
    job("berry_breeder", "Berry Breeder", "Breeding a Lum Berry", "the berry breeder bred a Lum Berry from Oran and Cheri", 150,
        [("04_berry_book", "The berry book")], cobblemon=True),
    job("camp_cook", "Camp Cook", "Cooking a Poké Snack in the Campfire Pot", "the camp cook cooked a Poké Snack in the Campfire Pot", 150,
        cobblemon=True),
    # Village-wide
    S("hall", "Village Hall", "The Village Hall, its screen and calendar", "the Village Hall screen and its calendar page opened", 75,
      [("02_hall_people", "People, under the page row"), ("03_hall_builder", "A builder"), ("08_hall_calendar", "The calendar"),
       ("09_hall_scale4", "At GUI scale 4")]),
    # Painting the plan (ROADMAP 27.3): the City Plan screen over a real village, at GUI scales 2 and 4
    S("city_plan", "Village Hall", "Painting the City Plan", "the City Plan screen opened with Homes, Workshops and Gardens zones in three styles", 60,
      [("01_city_plan_start", "The first zone"), ("02_city_plan_scale2", "Three zones in three styles, GUI scale 2"),
       ("03_city_plan_scale4", "The finished plan, GUI scale 4")]),
    # The plan on the ground and on the hall's map (ROADMAP 27.4)
    S("city_plan_ground", "Village Hall", "The plan on the ground", "zone edges, a street and the wall line show on the ground while the player holds the City Plan, and the hall's map of the plan hangs framed by the hall", 60,
      [("01_city_plan_ground", "Zone edges and the street on the ground"), ("02_city_plan_wall", "The wall line at the village's corner"),
       ("03_city_plan_framed", "The plan on the hall's map, framed")]),
    S("hall_pages", "Village Hall", "The chronicle and trade routes", "the chronicle and trade-route pages opened", 45,
      [("01_hall_chronicle", "Chronicle"), ("02_hall_routes", "Trade routes")]),
    S("long_shifts", "Village Hall", "Edicts: Long Shifts proclaimed",
      "Long Shifts was proclaimed: the hall's list shows the \"long shifts\" mood and the chronicle keeps it", 45,
      [("01_long_shifts_list", "The builder: 20% faster, and \"long shifts\" in their mood"),
       ("02_long_shifts_chronicle", "The chronicle: the edict proclaimed")]),
    # Legends (ROADMAP 29.3): a Mythic Legend announced to the whole server in gold, and the chronicle's nether-star line
    S("legend_announce", "Legends", "A Mythic Legend is announced", "a Mythic Legend's coming was announced in chat and written in the chronicle", 45,
      [("01_legend_chat", "The announcement"), ("02_legend_chronicle", "The chronicle line")]),
    # Legends on the hall (ROADMAP 29.4): a Legend in the stand-in outfit with a gold name and the sparkle, the hall's
    # list with her first, and the Legends page's cards at GUI scales 2 and 4
    S("legends_hall", "Legends", "Legends on the hall, and how they look",
      "a Legend in her outfit with a gold name and sparkle, first on the hall's list, and the Legends page's cards", 60,
      [("01_legend_look", "A Legend: outfit, gold name, sparkle"), ("03_legend_list", "First on the hall's list"),
       ("04_legends_page_scale2", "The Legends page, GUI scale 2"), ("05_legends_page_scale4", "The Legends page, GUI scale 4")]),
    S("hall_quests", "Village Hall", "Quests, advice, the village map and the festival",
      "the quests, advice, village map, mercenaries and festival opened", 60,
      [("01_hall_quests", "Quests"), ("02_hall_advice", "What next?"), ("03_hall_map", "Village map"),
       ("05_hall_festival", "Festival")]),
    S("hall_treasury", "Village Hall", "The treasury, protection and the Village Ledger",
      "the treasury was collected, the village protected and the hall opened from a Village Ledger", 60,
      [("01_hall_treasury", "Treasury and protection"), ("02_hall_collected", "Collected"), ("03_ledger_held", "Village Ledger"),
       ("04_ledger_opens", "Opened from afar")]),
    S("outfits", "Everyone at work", "Every outfit, and as a zombie", "every profession shows its outfit, as a villager and a zombie", 60,
      [("[0-9][0-9]_*@spread", "")]),
    S("guide", "Everyone at work", "The Guide Book", "a new player is given the Guide Book; every page opens with its picture", 60,
      [("[0-9][0-9]_guide_*@spread", "")]),
    S("config", "Everyone at work", "The settings screen (Mod Menu)", "every setting fits its button; a switch turned off is saved", 30,
      [("01_config_numbers", "Distances and numbers"), ("02_config_switches", "Switches"), ("03_config_festivals_off", "Festivals off")]),
    S("words", "Everyone at work", "Every new or reworded message, in chat", "every message reads without a raw key or placeholder", 30,
      [("[0-9][0-9]_words@spread", "")]),
    S("staff", "Everyone at work", "Every workstation with its villager", "every workstation stands with its worker", 45,
      [("01_staff", "Every workstation"), ("02_staff_close_1", "Builder to Village Hall"),
       ("03_staff_close_2", "Village Hall to Trainer"), ("04_staff_above", "From above")]),
    # Build families
    S("gallery", "Build families", "Every starter blueprint", "every starter build was placed", 240,
      [("30_*@spread", "")]),
    S("workshops", "Build families", "Tinker's Workshops and Nether Gates", "every workshop and gate was placed", 60,
      [("30_*@spread", "")]),
    S("decor", "Build families", "Decorations", "every decoration was placed", 120, [("30_*@spread", "")]),
    S("defences", "Build families", "Walls and gates", "every wall and gate was placed", 90, [("30_*@spread", "")]),
    S("styles", "Build families", "Cottage II and Stone House II in every style", "every style was placed", 120,
      [("30_*@spread", "")]),
    S("village", "Build families", "One village of each type", "every village type generated, none leaving structure_void", 360,
      [("40_workshop_*@spread", "")], env={"WORKSHOP_WEIGHT": "200"}),
    S("camp", "Build families", "A Settler's Wagon camp", "the camp was set up", 45,
      [("01_camp", "The camp"), ("02_camp_back", "From behind")]),
]

BY_NAME = {s["name"]: s for s in SCENES}
STARTUP = 30  # seconds: Gradle, the client's start-up and loading the world, per scene (GitHub runner)
SHARD_TARGET = 30 * 60  # seconds of scenes per shard, to finish well within the hour
MAX_SHARDS = 18  # GitHub allows 20 jobs at once on a free plan; leave room for the plan and page jobs


def seconds(scene, last):
    """Last night's measured time for the scene if there is one, else the catalog's estimate plus start-up."""
    measured = last.get(scene["name"])
    return measured if measured else scene["est"] + STARTUP


def load_last(path):
    """Durations from the page's showcase.json of the previous run: {scene: seconds}."""
    if not path or not os.path.exists(path):
        return {}
    try:
        with open(path) as f:
            data = json.load(f)
        return {s["name"]: s["seconds"] for s in data.get("scenes", []) if s.get("seconds")}
    except (ValueError, KeyError, TypeError):
        return {}


def matrix(last, only=None):
    """Longest scenes first, each onto the shard with the least work so far. only: just these scene names."""
    scenes = SCENES if only is None else [s for s in SCENES if s["name"] in only]
    if not scenes:
        return {"include": []}
    total = sum(seconds(s, last) for s in scenes)
    count = max(1 if only is not None else 4, min(MAX_SHARDS, math.ceil(total / SHARD_TARGET)))
    count = min(count, len(scenes))
    shards = [[0, []] for _ in range(count)]
    for s in sorted(scenes, key=lambda s: -seconds(s, last)):
        shard = min(shards, key=lambda x: x[0])
        shard[0] += seconds(s, last)
        shard[1].append(s["name"])
    shards = [s for s in shards if s[1]]
    return {"include": [{"shard": f"{i + 1:02d}", "scenes": " ".join(names), "minutes": round(t / 60)}
                        for i, (t, names) in enumerate(sorted(shards, key=lambda x: -x[0]))]}


DEVCLIENT = "src/devclient/java/io/github/jcondedata/aliveworkplace/devclient/"
METHOD = re.compile(r"^\t(?:(?:public|private|protected|static|final|synchronized)\s+)*[\w<>\[\], .?]+\s+(\w+)\([^;]*$")
DISPATCH = re.compile(r'"(\w+)"\.equals\(System\.getProperty\("aliveworkplace\.scene"\)\)')
PUT = re.compile(r'^\t\t(?:SCENES|SCREENS)\.put\("(\w+)"')


def diff_lines(base, head, paths, root="."):
    """{path: [line numbers in the new file that were added or changed, or where lines were removed]}."""
    import subprocess
    out = subprocess.run(["git", "diff", "-U0", "--no-color", base, head, "--", *paths], capture_output=True, text=True,
                         check=True, cwd=root).stdout
    lines, path = {}, None
    for row in out.splitlines():
        if row.startswith("+++ "):
            path = None if row[4:] == "/dev/null" else row[6:]
            if path is not None:
                lines.setdefault(path, [])
        elif row.startswith("--- a/") and path is None:
            lines.setdefault(row[6:], [])
        elif row.startswith("@@") and path is not None:
            m = re.match(r"@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@", row)
            start, count = int(m.group(1)), int(m.group(2) or "1")
            lines[path].extend(range(start, start + count) if count else [max(1, start)])
    return lines


def method_at(text, line):
    """The name of the top-level method (one tab in) that line {line} is in, and the line it starts on. A line between
    methods (a field, a comment, a blank line) goes with the method below it: a scene's constants sit above it."""
    rows = text.splitlines()
    for i in range(min(line, len(rows)) - 1, -1, -1):
        m = METHOD.match(rows[i])
        if m:
            end = next((j for j in range(i + 1, len(rows)) if rows[j] == "\t}"), len(rows) - 1)
            if line - 1 <= end:
                return m.group(1), i + 1
            break
    for i in range(min(line, len(rows)), len(rows)):
        m = METHOD.match(rows[i])
        if m:
            return m.group(1), i + 1
    return None, 0


def dispatch_lines(text):
    """ScreenshotHarness: {line number: {scenes}} for the lines of each dispatch block (the if, the call, the return and
    the closing brace), so adding a scene's dispatch films just that scene."""
    rows = text.splitlines()
    out = {}
    for i, row in enumerate(rows):
        names = DISPATCH.findall(row)
        if not names or not row.lstrip().startswith("if"):
            continue
        indent = len(row) - len(row.lstrip())
        j = i
        while j < len(rows) and not rows[j].rstrip().endswith("{"):
            j += 1
            names += DISPATCH.findall(rows[j]) if j < len(rows) else []
        k = j + 1
        while k < len(rows) and not (rows[k].strip() == "}" and len(rows[k]) - len(rows[k].lstrip()) == indent):
            k += 1
        for n in range(i + 1, k + 2):
            out[n] = set(names)
    return out


def harness_methods(text):
    """ScreenshotHarness: {method: {scenes}} from its dispatch (`if ("x".equals(...)) { xScene(...); }`)."""
    rows = text.splitlines()
    out = {}
    for i, row in enumerate(rows):
        names = DISPATCH.findall(row)
        if not names or not row.lstrip().startswith(("if", "||")):
            continue
        j = i
        while j < len(rows) and not rows[j].rstrip().endswith("{"):
            j += 1
            names += DISPATCH.findall(rows[j]) if j < len(rows) else []
        call = re.match(r"\s*(\w+)(?:\.\w+)?\(", rows[j + 1]) if j + 1 < len(rows) else None
        if call:
            out.setdefault(call.group(1), set()).update(names)
    return out


FIELD = re.compile(r"^\t(?:private |final |static )*(\w+) (\w+) = new \1\(")


def scene_fields(text):
    """ScreenshotHarness: {line number: (class, field)} for each scene object it keeps (`private final XScene x = new
    XScene();`), whose dispatch calls `x.tick(...)`."""
    out = {}
    for i, row in enumerate(text.splitlines()):
        m = FIELD.match(row)
        if m:
            out[i + 1] = (m.group(1), m.group(2))
    return out


def class_scenes(harness_text, cls):
    """The scenes a scene class of its own (PartnersLandScene.java) belongs to: those whose dispatch calls the harness's
    field of that class. Empty when the harness keeps none."""
    methods = harness_methods(harness_text)
    names = set()
    for _, (c, field) in scene_fields(harness_text).items():
        if c == cls:
            names |= methods.get(field, set())
    return names


def scenes_for(path, text, lines, catalog_text=None, harness_text=None):
    """The scenes the changed {lines} of {path} belong to, or None when they could touch any scene. {harness_text}: the
    ScreenshotHarness, for a scene class of its own (ROADMAP 22.8: a new scene in its own file films just it)."""
    names = set()
    if path.endswith(".java"):
        rows = text.splitlines()
        # Blank lines and comments change no scene (a removed line's position may be past the end).
        lines = [n for n in lines if n > len(rows) or not (rows[n - 1].strip() == "" or rows[n - 1].strip().startswith(("//", "/*", "*")))]
    if path.endswith("JobScenes.java"):
        rows = text.splitlines()
        for line in lines:
            scene = None
            for i in range(min(line, len(rows)) - 1, -1, -1):
                m = PUT.match(rows[i])
                if m:
                    scene = m.group(1)
                    break
                if METHOD.match(rows[i]) or rows[i] == "\t}":
                    break
            if scene is None:
                return None
            names.add(scene)
        return names
    if path.endswith("ScreenshotHarness.java"):
        methods = harness_methods(text)
        blocks = dispatch_lines(text)
        fields = scene_fields(text)
        for line in lines:
            if line in blocks:
                names |= blocks[line]
                continue
            if line in fields and methods.get(fields[line][1]):
                names |= methods[fields[line][1]]  # the harness's field for a scene class of its own
                continue
            name, _ = method_at(text, line)
            if name not in methods:
                return None
            names |= methods[name]
        return names
    if path.startswith(DEVCLIENT) and path.endswith("Scene.java") and harness_text is not None:
        # A scene class of its own, such as PartnersLandScene: the scenes the harness runs it for (none: unsure).
        found = class_scenes(harness_text, os.path.basename(path)[:-len(".java")])
        return found or None
    if path == "tools/showcase/scenes.py":
        rows = text.splitlines()
        first = next(i for i, r in enumerate(rows) if r.startswith("SCENES = [")) + 1
        last = next(i for i in range(first, len(rows)) if rows[i] == "]") + 1
        for line in lines:
            if not first < line < last:
                return None
            for i in range(line - 1, first - 1, -1):
                m = re.match(r'\s*(?:S|job)\("(\w+)"', rows[i])
                if m:
                    names.add(m.group(1))
                    break
                if rows[i].strip().startswith("#"):
                    break
        return names
    return None


def changed(base, head="HEAD", root="."):
    """The scenes a commit range added or changed, or None for every scene (shared code, the tools, the workflow)."""
    paths = [DEVCLIENT, "tools/showcase", "tools/screenshots", ".github/workflows/showcase.yml"]
    names = set()
    for path, lines in diff_lines(base, head, paths, root).items():
        import subprocess
        shown = subprocess.run(["git", "show", f"{head}:{path}"], capture_output=True, text=True, cwd=root)
        if shown.returncode != 0:
            return None  # a removed file
        text = shown.stdout
        harness = subprocess.run(["git", "show", f"{head}:{DEVCLIENT}ScreenshotHarness.java"], capture_output=True, text=True, cwd=root)
        found = scenes_for(path, text, lines, harness_text=harness.stdout if harness.returncode == 0 else None)
        if found is None:
            return None
        names |= found
    return sorted(n for n in names if n in BY_NAME)


def env(scene):
    s = BY_NAME[scene]
    out = {"SCENE": scene, "COBBLEMON": "true" if s["cobblemon"] else "false", "MEGA": "true" if s["mega"] else "false"}
    out.update(s["env"])
    return out


def harness_scenes(root):
    """Scene names the harness knows: the strings compared with aliveworkplace.scene, and JobScenes' names."""
    names = set()
    dev = os.path.join(root, "src/devclient/java/io/github/jcondedata/aliveworkplace/devclient")
    for f in os.listdir(dev):
        text = open(os.path.join(dev, f), encoding="utf-8").read()
        names |= set(re.findall(r'"(\w+)"\.equals\(System\.getProperty\("aliveworkplace\.scene"\)\)', text))
        names |= set(re.findall(r'(?:SCENES|SCREENS)\.put\("(\w+)"', text))
    names.add("builders")  # the default scene
    return names


def pick(files, spec):
    """The picture(s) a still spec names: [(file stem, label)]."""
    pattern, _, how = spec.partition("@")
    matches = sorted(f for f in files if fnmatch.fnmatch(f, pattern))
    if not how:
        return [pattern] if pattern in files else []
    if not matches:
        return []
    if how == "first":
        return [matches[0]]
    if how == "last":
        return [matches[-1]]
    if how == "middle":
        return [matches[len(matches) // 2]]
    if how == "spread":
        n = min(4, len(matches))
        return [matches[round(i * (len(matches) - 1) / max(1, n - 1))] for i in range(n)] if n > 1 else matches
    raise ValueError(f"unknown pick {how!r} in {spec!r}")


def label_from(stem):
    """'30_starter_cottage_2_back' -> 'Starter cottage 2 back'."""
    text = re.sub(r"^\d+_", "", stem).replace("_", " ").strip()
    return text[:1].upper() + text[1:]


def main():
    p = argparse.ArgumentParser()
    sub = p.add_subparsers(dest="cmd", required=True)
    ls = sub.add_parser("list")
    ls.add_argument("--cobblemon", action="store_true", help="only the scenes that load Cobblemon")
    m = sub.add_parser("matrix")
    m.add_argument("--last", help="the previous run's showcase.json (durations)")
    m.add_argument("--only", help="space- or comma-separated scene names (default: every scene)")
    c = sub.add_parser("changed")
    c.add_argument("base")
    c.add_argument("head", nargs="?", default="HEAD")
    e = sub.add_parser("env")
    e.add_argument("scene")
    sub.add_parser("check")
    a = p.parse_args()
    if a.cmd == "list":
        print("\n".join(s["name"] for s in SCENES if s["cobblemon"] or not a.cobblemon))
    elif a.cmd == "matrix":
        only = None
        if a.only and a.only.strip() and a.only.strip() != "all":
            only = set(re.split(r"[\s,]+", a.only.strip()))
            unknown = sorted(only - set(BY_NAME))
            if unknown:
                sys.exit(f"unknown scene(s): {', '.join(unknown)}")
        print(json.dumps(matrix(load_last(a.last), only), separators=(",", ":")))
    elif a.cmd == "changed":
        found = changed(a.base, a.head)
        print("all" if found is None else " ".join(found))
    elif a.cmd == "env":
        for k, v in env(a.scene).items():
            print(f"{k}={v}")
    elif a.cmd == "check":
        root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
        known = harness_scenes(root)
        missing = sorted(known - set(BY_NAME))
        extra = sorted(set(BY_NAME) - known)
        bad_groups = sorted({s["group"] for s in SCENES} - set(GROUPS))
        for n in missing:
            print(f"harness scene {n!r} is not in tools/showcase/scenes.py")
        for n in extra:
            print(f"catalog scene {n!r} is not in the harness")
        for g in bad_groups:
            print(f"group {g!r} is not in GROUPS")
        for s in SCENES:
            if not 1 <= len(s["stills"]) <= 4:
                print(f"{s['name']}: {len(s['stills'])} stills (want 2-4, or one @spread)")
        sys.exit(1 if missing or extra or bad_groups else 0)


if __name__ == "__main__":
    main()
