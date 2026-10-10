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
    "Guard", "Threats", "Nurse", "Shopkeeper", "Ferryman", "Bard", "Trainer", "Trainer Leader", "Move Tutor", "Ball Smith",
    "Pokémon Trader", "Fossil Scientist", "Berry Breeder", "Steward", "Camp Cook", "Gem Grower", "Jeweller", "Vintner", "Tailor", "Printer", "Habitat Keeper", "Daycare Keeper", "Village Hall", "Realm", "Legends", "Everyone at work", "Build families",
]

START, WORKING, DONE = ("01_start", "Start"), ("work_*@middle", "At work"), ("03_done", "Done")


def S(name, group, title, what, est, stills, env=None, cobblemon=False, mega=False, cobblemon18=False):
    """One scene. est: seconds the scene itself takes on GitHub's runner (the client's start-up is added).
    cobblemon18: a Cobblemon-1.8-only feature, always filmed with Cobblemon 1.8.1 (-Pcobblemon18=true, ROADMAP 28.14)."""
    return {"name": name, "group": group, "title": title, "what": what, "est": est, "stills": stills,
            "env": env or {}, "cobblemon": cobblemon or cobblemon18, "mega": mega, "cobblemon18": cobblemon18}


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
    job("vintner", "Vintner", "Pressing cider at the cauldron", "the vintner pressed cider in the cauldron, purple splashes and all"),
    job("tailor", "Tailor", "Sewing work clothes at the loom", "the tailor sewed work clothes at the loom"),
    job("printer", "Printer", "Printing the Village Gazette", "the printer printed the Village Gazette at the cartography table", 150,
        [("04_gazette_read", "The Gazette, opened and read")]),
    job("sifter", "Sifter", "Sifting gravel", "the sifter sifted gravel into loot"),
    job("tinkerer", "Tinkerer", "Mending an iron golem", "the tinkerer mended the iron golem"),
    job("composter", "Composter", "Turning scraps into bone meal", "the composter made bone meal"),
    job("steward", "Steward", "The Steward's morning rounds", "the steward walked his morning rounds and came back to the hall", 200),
    # Roads (ROADMAP 27.15): an approved street on the plan, routed and laid 3 wide in Stonework by the village's builder
    job("roads", "Steward", "A street between two houses",
        "the builder laid the approved Stonework street between the two houses, 3 wide, segment by segment", 480),
    # Lamps, bridges and steps (ROADMAP 27.16): a street drawn over a river is bridged and lit, shown at night at the end
    job("bridges", "Steward", "A bridge over the river, and the street lit at night",
        "the builder bridged the river 9 wide (2 pillars, rails, a stair up at each end) and lit the street with Street Lamps", 600),
    # Roads between villages (ROADMAP 27.17): two villages with a trade route each build their half, and the halves meet
    job("caravan_road", "Steward", "The road from one village to the other",
        "each village's builder laid its half of the Stonework road to the other, and the halves met halfway", 800),
    # Walls (ROADMAP 27.18): a palisade goes up round a small village along its wall line, and its gate shuts at night
    job("walls", "Steward", "A palisade going up round the village, its gate shut at night",
        "the builders raised the village's palisade along the wall line (towers at its corners, a gate on the road) "
        "and the guards shut the gate at nightfall", 700),
    # The Steward's rules (ROADMAP 27.6): the day's wishes over his head, and /workplace steward explain in chat
    S("steward_rules", "Steward", "The Steward's rules and wishes",
      "the Steward ranked today's wishes from his rules, and explain listed rules that held and that didn't", 45,
      [("01_steward_wish", "His first wish, over his head"), ("02_steward_explain_end", "Explain: the last rules and today's wishes"),
       ("03_steward_explain_top", "Explain: the first rules")]),
    # The Steward's desk (ROADMAP 27.8): proposals on the hall's What next? page, Show me, Approve and the builder setting off
    S("steward_desk", "Steward", "The Steward's desk",
      "the hall's What next? page became the Steward's desk with three proposals, Show me lit one's outline, and approving it started the build", 45,
      [("01_steward_desk", "The desk: his modes, three proposals and the tips"), ("02_steward_proposal", "A proposal's page"),
       ("03_steward_show_me", "Show me: the outline in the world"), ("04_builder_sets_off", "Approved: the builder sets off")]),
    # Safe by design (ROADMAP 27.19): one shopping list for his waiting builds, a player's block left in the way
    S("steward_safety", "Steward", "The Steward's shopping list, and a player's block left alone",
      "two of the Steward's builds wait for materials: the desk and the Storehouse board show one shopping list adding up both, new builds wait, and the build with a player's block in the way says so", 30,
      [("01_shopping_list_on_the_desk", "The desk: one shopping list for both builds"), ("02_players_block_in_the_way", "A player's block is in the way"),
       ("03_shopping_list_on_the_storehouse_board", "The Storehouse board's shopping list")]),
    # Old houses (ROADMAP 27.20): three vanilla plains houses in a renew zone, counted on the desk, outlined by Show me
    S("old_houses", "Steward", "Old houses, found and measured",
      "the Steward found the three vanilla houses in the renew zone, the desk says 2 of them can be renewed (one keeps a chest), and Show me outlined each", 30,
      [("01_old_houses_on_the_desk", "The desk: Old houses: 3, 2 can be renewed"), ("02_show_me_outlines", "Show me: each house outlined")]),
    # Old villages renewed (ROADMAP 27.21): a time-lapse of a vanilla two-bed house taken down and rebuilt as a Stone House (Cherry)
    S("renewal", "Steward", "An old village house renewed",
      "the old plains house was scanned and taken down, a Stone House in Cherry went up on its plot, and both villagers who slept there have its beds", 60,
      [("01_old_house", "The old plains house in a renew zone"), ("02_going_up", "Taken down, the Stone House going up"),
       ("03_stone_house_cherry", "The Stone House (Cherry), its villagers moved in")]),
    # The Steward gives jobs (ROADMAP 27.9): the morning's jobs as one proposal, approved, the villagers off to their blocks
    S("steward_jobs", "Steward", "The Steward gives out jobs",
      "the Steward proposed jobs for three jobless villagers, approving made them a farmer, a guard and a fletcher, and they walked to their new workstations", 45,
      [("01_steward_jobs_desk", "The desk: Give 3 villagers jobs"), ("02_steward_jobs_proposal", "Who goes where"),
       ("03_jobs_walking", "Approved: off to their new work"), ("04_jobs_at_work", "At their workstations")]),
    # The Steward's homes rules (ROADMAP 27.10): Run the village, three mornings, the Homes zone filling with houses
    S("steward_homes", "Steward", "The Steward fills the Homes zone",
      "over three mornings in Run the village, the Steward read the homes rules and started the builds himself, and the builders filled the Homes zone", 200,
      [("01_steward_homes_day_1", "Day 1: the first home"), ("02_steward_homes_day_2", "Day 2"),
       ("03_steward_homes_day_3", "Day 3: the zone filling up")]),
    # The 1.1 yardstick (ROADMAP 27.22): a village from a plan, its Steward running it for 6 days (sprinted); the GIF leads the 1.1 notes
    S("city_timelapse", "Steward", "A village from a plan: 6 days in Run the village",
      "the village grew into its plan: every build the Steward started finished, each in its zone, nothing duplicated or lost", 1800,
      [("01_city_start", "Day 1: the hall, the plan's zones, streets and wall line"), ("frame_*@middle", "Halfway"),
       ("02_city_done", "Day 8: the village grown into its plan"), ("03_city_close", "Close up")]),
    S("steward_civic", "Steward", "The village asks, the Steward builds",
      "the village asked for light, beauty and a school, and the Steward started a street lamp, a well and a schoolhouse himself", 240,
      [("01_steward_civic_day_1", "Day 1: a lamp by the dark beds and a well"), ("02_steward_civic_day_2", "Day 2: children came, so a schoolhouse")]),
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
    S("daycare_keeper", "Daycare Keeper", "A pair left at the daycare, eggs collected",
      "the daycare keeper took a pair of Eevee and two eggs were collected", 90,
      [("01_daycare_keeper_screen", "The daycare"), ("03_daycare_keeper_pair", "How well they get along"), ("04_daycare_keeper_eggs", "Eggs waiting"),
       ("05_daycare_keeper_collected", "Collected")], cobblemon=True),
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
      "both tiers stand in all three looks, and the nurse put the team in her Healing Machine and every Pokémon came out full", 70,
      [("01_pokemon_center", "Pokémon Center"), ("02_pokemon_center_2", "Pokémon Center II"),
       ("05_lodge", "Mountain Lodge look, both tiers"), ("06_plaza", "Sunny Plaza look, both tiers"),
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
    S("arena", "Trainer Leader", "The Arena, three tiers",
      "every tier's ring centre, boxes and seats are where Arenas says, and Arena III has a Healing Machine in each trainers' room", 50,
      [("01_arena_front", "Arena"), ("03_arena_2_front", "Arena II: stands, gate arch, fair lane"),
       ("05_arena_3_front", "Arena III: the covered grandstand"), ("06_arena_3_above", "Arena III from above")], cobblemon=True),
    # The Festival Cup (ROADMAP 28.17): the hall's Cup page for a Town host with an Arena, two trade partners on its
    # circuit, the player signed up, the seeds and the roll of champions
    S("cup_page", "Trainer Leader", "The Festival Cup's page",
      "the hall's Cup page shows the Grand Cup's card with its rules, the circuit and who each village sends, and the player signed up", 40,
      [("01_cup_card", "The Cup's card and rules"), ("02_cup_circuit", "The circuit and its Leaders"),
       ("03_cup_champions", "The roll of champions"), ("04_cup_seeds", "The seeds")], cobblemon=True),
    # The Cup's bouts (ROADMAP 28.18): two Trainer Leaders' Grand Cup bout at the Arena, their Pokémon coming out one at a
    # time beside the ring and trading moves until one side has none left, and the result read out
    S("cup_bout", "Trainer Leader", "A Festival Cup bout between villagers",
      "both trainers' Pokémon came out beside the ring, the bout ended with a winner on the Cup's results, and no Pokémon was left at the ring", 110,
      [("01_bout_start", "Both Pokémon out"), ("bout_*@middle", "Trading moves"), ("02_bout_result", "The result"),
       ("03_ring_cleared", "The ring cleared")], cobblemon=True),
    # Players in the Cup (ROADMAP 28.20): the player's bout called in chat with [I'm ready], Mewtwo left out under the
    # Grand Cup's rules, a real Cobblemon battle at the ring with the stands behind, and the win and purse read out
    S("cup_match", "Trainer Leader", "A player's Festival Cup bout",
      "the player's bout was called, a real battle started at the ring with only the eligible Pokémon, and the win is on the Cup's results", 60,
      [("01_call", "The call, with [I'm ready]"), ("02_battle", "The battle at the ring"), ("03_result", "The win and the purse")], cobblemon=True),
    # The Cup's day (ROADMAP 28.19), sped up: the delegates walk in from their villages' side, the fair on the Arena's fair
    # lane with the theme's wares, the stands at noon, the champion's fireworks, the delegates gone by dawn
    S("cup_day", "Trainer Leader", "A Festival Cup's day, morning to champion",
      "two delegates came, the fair sold the theme's wares, villagers sat in the stands for the final, the chronicle has the Cup and the delegates left by dawn", 90,
      [("01_fair", "The fair"), ("02_delegate_arrives", "A delegate walks in"), ("03_stands", "The stands"),
       ("04_champion", "The champion's fireworks")]),
    # The Cup's champions (ROADMAP 28.21): the Cup banner on Arena III's champion's pole over the holder, then on top of
    # Ashford's hall when the title passes, the roll of champions and "Holders of the Thornholm Cup" on Ashford's hall
    S("cup_champions", "Trainer Leader", "The Festival Cup's champions",
      "the Cup banner flew on the champion's pole, came down when the title passed and stood on the new holder's hall, which names it holder", 40,
      [("01_pole_banner", "The Cup banner on the champion's pole"), ("02_hall_banner", "The banner over the new holder's hall"),
       ("03_roll_of_champions", "The roll of champions"), ("04_holders_tooltip", "Holders of the Thornholm Cup")], cobblemon=True),
    # The eight Cup themes (ROADMAP 28.22): for each theme in order, the host's Cup page with that theme's card and rules,
    # then a fair trader's trades with the theme's wares
    S("cup_themes", "Trainer Leader", "The eight Festival Cup themes",
      "each of the eight themes' Cup page shows its card and rules, and its fair sells its wares", 50,
      [("0*_page@spread", "The themes' Cup pages"), ("01_blossom_cup_fair", "The Blossom Cup's fair"),
       ("04_workers_cup_page", "The Workers' Cup's rules"), ("08_grand_cup_fair", "The Grand Cup's fair")], cobblemon=True),
    S("leader", "Trainer Leader", "Challenging the Trainer Leader", "the Trainer Leader took the challenge", 90,
      [("01_leader", "At the podium"), ("02_leader_battle", "The battle starts")], cobblemon=True),
    S("tutor", "Move Tutor", "The Move Tutor's lessons", "the lesson screen opened", 60,
      [("01_tutor_screen", "Lessons"), ("02_tutor_lesson", "A lesson")], cobblemon=True),
    S("smith", "Ball Smith", "A Ball Smith at work", "the ball smith made balls", 150,
      [("01_smith_working", "At the smithing table"), ("02_smith_done", "Balls made")], cobblemon=True),
    S("smith_orders", "Ball Smith", "Choosing which balls to make", "the orders screen opened", 60,
      [("01_orders_screen", "Orders"), ("02_orders_picked", "A ball picked")], cobblemon=True),
    S("trader", "Pokémon Trader", "The Pokémon Trader's own trade screen", "the trade screen opened", 60,
      [("01_trader_offer", "The offers as cards"), ("02_trader_party", "A Pokémon that fits"), ("04_trader_confirm", "Click again to trade"), ("05_trader_traded", "Traded: come back tomorrow")],
      cobblemon=True),
    job("fossil", "Fossil Scientist", "Reviving a fossil", "the fossil scientist revived the fossil", 150, cobblemon=True),
    job("berry_breeder", "Berry Breeder", "Breeding a Lum Berry", "the berry breeder bred a Lum Berry from Oran and Cheri", 150,
        [("04_berry_book", "The berry book")], cobblemon=True),
    job("camp_cook", "Camp Cook", "Cooking a Poké Snack in the Campfire Pot", "the camp cook cooked a Poké Snack in the Campfire Pot", 150,
        cobblemon=True),
    job("gem_grower", "Gem Grower", "A ripe amethyst cluster picked, the budding block kept",
        "the gem grower picked the ripe amethyst cluster and left the budding amethyst", 150),
    job("jeweller", "Jeweller", "Making amethyst rings at the stonecutter", "the jeweller made amethyst rings at the stonecutter"),
    job("habitat_keeper", "Habitat Keeper", "A snack set out, a log slathered, a shiny spotted",
        "the habitat keeper set out a snack, slathered the log and spotted a shiny Eevee", 150, cobblemon=True),
    # Village-wide
    S("hall", "Village Hall", "The Village Hall, its screen and calendar", "the Village Hall screen and its calendar page opened", 75,
      [("02_hall_people", "The hall's own screen: figures, toolbar, people"), ("03_hall_builder", "A builder"), ("08_hall_calendar", "The calendar"),
       ("09_hall_scale4", "The hall's screen at GUI scale 4")]),
    # Painting the plan (ROADMAP 27.3): the City Plan screen over a real village, at GUI scales 2 and 4
    S("city_plan", "Village Hall", "Painting the City Plan", "the City Plan screen opened with Homes, Workshops and Gardens zones in three styles", 60,
      [("01_city_plan_start", "The first zone"), ("02_city_plan_scale2", "Three zones in three styles, GUI scale 2"),
       ("03_city_plan_scale4", "The finished plan, GUI scale 4")]),
    # The plan on the ground and on the hall's map (ROADMAP 27.4)
    S("city_plan_ground", "Village Hall", "The plan on the ground", "zone edges, a street and the wall line show on the ground while the player holds the City Plan, and the hall's map of the plan hangs framed by the hall", 60,
      [("01_city_plan_ground", "Zone edges and the street on the ground"), ("02_city_plan_wall", "The wall line at the village's corner"),
       ("03_city_plan_framed", "The plan on the hall's map, framed")]),
    S("hall_pages", "Village Hall", "The chronicle and trade routes", "the chronicle and trade-route pages opened", 45,
      [("01_hall_chronicle", "Chronicle"), ("02_hall_routes", "Trade routes: the Trade page's Routes tab")]),
    # The price board (ROADMAP 33.4): the hall's Trade page on Prices, a good's tooltip, and the name icon's Known for line
    S("price_board", "Realm", "The price board: what a village is known for and short of",
      "the hall's Trade page showed Thornholm's prices: Timber and Wool starred, Bread marked short, arrows on what moved, "
      "Timber's tooltip with the dearer and the cheaper village, and the name icon's Known for line", 60,
      [("01_price_board", "The Trade page, Prices tab"), ("02_price_tooltip", "Timber: both prices, and where it's dearer and cheaper"),
       ("03_hall_known_for", "The hall's name icon: Known for, Short of")]),
    # Trading at the board (ROADMAP 33.5): buying a bundle of Timber on the Prices tab, the treasury before and after
    S("board_trade", "Realm", "Trading at the board: buying a bundle",
      "a click on Timber on the hall's Prices tab bought a bundle of 16 logs out of the Storehouse's chest for 1 emerald, "
      "and the treasury on the page went from 12.4 to 13.4 emeralds", 60,
      [("01_board_before", "The Prices tab, the treasury before: 12.4 emeralds"), ("02_board_bought", "Timber clicked: 16 logs for 1 emerald"),
       ("03_board_after", "The treasury after: 13.4 emeralds")]),
    # Caravans that trade (ROADMAP 33.6): what the caravan to Ashford sells and earns on the Routes tab, then the sale in the chronicle
    S("caravan_trade", "Realm", "Caravans that trade: what goes, what it earns, and the sale",
      "the hall's Routes tab said the caravan to Ashford sells Timber ×2 for 2.77 emeralds, the caravan sold it there, "
      "and the chronicle says Sold 32 Timber to Ashford for 2.77 emeralds", 60,
      [("01_routes_earnings", "The Routes tab: what the caravan sells in Ashford and what it earns"),
       ("02_chronicle_sold", "The chronicle: Sold 32 Timber to Ashford for 2.77 emeralds")]),
    # House looks (ROADMAP 23.10a): the leader picks another style's outside for a village house; the builder rebuilds it
    S("piece_look", "Village Hall", "House looks: a new outside for a village house",
      "the hall's Builds button opened House looks, the Guard House's view showed the five outsides, and choosing the desert one had the builder rebuild its outside", 140,
      [("01_plains_house", "The plains Guard House"), ("02_house_looks", "House looks on the hall"),
       ("03_look_picker", "The five outsides"), ("04_new_outside", "Rebuilt with the desert outside")]),
    S("long_shifts", "Village Hall", "Edicts: Long Shifts proclaimed",
      "Long Shifts was proclaimed: the hall's list shows the \"long shifts\" mood and the chronicle keeps it", 45,
      [("01_long_shifts_list", "The builder: 20% faster, and \"long shifts\" in their mood"),
       ("02_long_shifts_chronicle", "The chronicle: the edict proclaimed")]),
    # Legends (ROADMAP 29.3): a Mythic Legend announced to the whole server in gold, and the chronicle's nether-star line
    S("legend_announce", "Legends", "A Mythic Legend is announced", "a Mythic Legend's coming was announced in chat and written in the chronicle", 45,
      [("01_legend_chat", "The announcement"), ("02_legend_chronicle", "The chronicle line")]),
    # Research trees as data (ROADMAP 29.11): the test tree's own tab, worked by a Legend who lives in the village
    S("research_trees", "Legends", "A Legend's research tree",
      "a Legend's research tree has its own tab on the research screen: levels done, one in progress, an exclusive pick taken", 45,
      [("01_tree_tab", "The tree's tab"), ("02_tree_exclusive", "An exclusive pick taken")]),
    # Legends on the hall (ROADMAP 29.4): a Legend in the stand-in outfit with a gold name and the sparkle, the hall's
    # list with her first, and the Legends page's cards at GUI scales 2 and 4
    S("legends_hall", "Legends", "Legends on the hall, and how they look",
      "a Legend in her outfit with a gold name and sparkle, first on the hall's list, and the Legends page's cards", 60,
      [("01_legend_look", "A Legend: outfit, gold name, sparkle"), ("03_legend_list", "First on the hall's list"),
       ("04_legends_page_scale2", "The Legends page, GUI scale 2"), ("05_legends_page_scale4", "The Legends page, GUI scale 4")]),
    # Needs and strikes (ROADMAP 29.5): a Legend with no home of her own on strike, picketing by the hall under a red line
    # Gifted villagers (ROADMAP 29.6): Wren, a Night Owl builder, building a market stall at midnight, and her gift in
    # gold on the hall's list
    S("gifted", "Legends", "A Night Owl builds by moonlight", "a Night Owl builder placed blocks at midnight, and the hall's list shows her gift in gold", 60,
      [("01_night_owl_building", "Building at midnight"), ("04_night_owl_building", "Still at it"),
       ("05_gifted_list", "The hall's list: her gift in gold")]),
    # Gifted born (ROADMAP 29.7): Wren, daughter of two schooled Master farmers, grows up Gifted, and the chronicle says so
    S("gifted_born", "Legends", "A child of two Masters grows up Gifted", "a child of two schooled Masters grew up Gifted, and the chronicle says so", 45,
      [("01_wren_grown_up", "Wren grows up, with her parents"), ("02_born_chronicle", "The chronicle: grown up Gifted")]),
    # Legends who visit (ROADMAP 29.8): a Legend comes to the inn as a guest, shows her terms, and settles once a home is ready
    S("legend_guest", "Legends", "A Legend visits the inn, and settles", "a Legend came to the inn as a guest, showed their terms, and settled once a home was ready", 50,
      [("01_guest_arrives", "A Legend walks into the inn"), ("02_terms", "Her terms: what she brings, what she wants"),
       ("03_settled", "A home is ready: she settles"), ("04_settled_chronicle", "The chronicle: settled in the village")]),
    # Legends found in the world (ROADMAP 29.9): a traveller's camp, a prisoner's cage and a castaway's camp; a bar broken frees the prisoner
    S("legend_sites", "Legends", "Legends found at ruins, outposts and wrecks",
      "the three camps of Legends found in the world, and a prisoner freed from the outpost cage", 40,
      [("01_three_camps", "A traveller's camp, a prisoner's cage, a castaway's camp"), ("02_cage_opened", "A bar broken: the prisoner is free")]),
    # The Old Sage (ROADMAP 29.14): the hermit's hut, a riddle with a wrong answer refused and a hint, the Ancient Lore tab
    S("legend_sage", "Legends", "The Old Sage's hut, riddles and Ancient Lore",
      "the Old Sage's hut, a riddle asked and a wrong answer refused with a hint, and the Ancient Lore tab", 50,
      [("01_hermit_hut", "The hermit's hut, the Sage inside"), ("02_riddle", "A riddle, a shake of the head, a hint"),
       ("03_ancient_lore_tab", "The Ancient Lore tab")]),
    # The Merchant Prince (ROADMAP 29.17): settled by the hall in his crimson coat, and the hall's bank page
    S("legend_merchant_prince", "Legends", "The Merchant Prince, the hall's bank and a trade fair",
      "the Merchant Prince settled by the hall in his crimson coat, the hall's bank page with emeralds put in, and a trade fair", 60,
      [("01_merchant_prince", "The Merchant Prince by the hall"), ("02_bank_page", "The bank page: 80 emeralds put in"),
       ("03_trade_fair", "A trade fair: six traders, bunting and fireworks round the hall")]),
    # The Pokémon Professor (ROADMAP 29.21, Cobblemon): settled by the hall in a white lab coat, hints about the party, the Pokédex tab
    S("legend_professor", "Legends", "The Pokémon Professor's hints and the Pokédex",
      "the Pokémon Professor in a white lab coat by the hall, hints about the player's party, and the Pokédex research tab", 50,
      [("01_professor", "The Professor by the hall"), ("02_professor_hints", "Hints: IVs in words, nature, hidden ability, EVs"),
       ("03_pokedex_tab", "The Pokédex tab")], cobblemon=True),
    # The Pokémon Ranger (ROADMAP 29.22, Cobblemon): settled by the hall in a red vest, an Alpha calmed, a wild Pikachu befriended
    S("legend_ranger", "Legends", "The Pokémon Ranger calms an Alpha and befriends a Pikachu",
      "the Pokémon Ranger in a red vest and capture styler by the hall, an Alpha calmed with sparkles, and a wild Pikachu befriended into the owner's pasture", 50,
      [("01_ranger", "The Ranger by the hall"), ("02_alpha_calmed", "A wild level-60 Machamp calmed: sparkles and a chime"),
       ("03_befriended", "A wild Pikachu led into the owner's Pasture Block")], cobblemon=True),
    # The Master Architect (ROADMAP 29.12): the top-tier Stone House, finished, redrawn in the Grand style and rebuilt
    job("legend_architect", "Legends", "The Master Architect redraws a house in the Grand style",
        "the Master Architect handed a builder the Stone House redrawn in the Grand style, and it was rebuilt", 360),
    # The Pathfinder (ROADMAP 29.13): an expedition with the player through a forest to a staged Stronghold
    job("legend_pathfinder", "Legends", "The Pathfinder leads the player through a forest",
        "the Pathfinder led the player through a forest to a staged Stronghold and planted a banner at its entrance", 120),
    # The Seer (ROADMAP 29.16): the arrival at the Chapel under a full moon, the dawn foretelling in chat
    job("legend_seer", "Legends", "The Seer comes under a full moon and foretells at dawn",
        "the Seer came to the Chapel at midnight under a full moon, and at dawn foretold the night, the festival, the market and the next guest in chat", 90),
    # The Golem Smith (ROADMAP 29.15): a Hauler forged from the chest; the hauler, a farmhand and a wall sentry at work
    job("legend_golem_smith", "Legends", "The Golem Smith's golems at work",
        "the Golem Smith forged a Hauler Golem, and the hauler, a farmhand and a wall sentry went to work", 150),
    # The Grand Chef (ROADMAP 29.18): a banquet called after work, the village gathered round the hall, the feast at supper
    job("legend_grand_chef", "Legends", "The Grand Chef's banquet",
        "the Grand Chef called the village to a banquet: everyone gathered round the hall and at supper each grown-up ate two meals of the eight kinds in the store", 90),
    # The Bard Laureate (ROADMAP 29.19): the anthem composed and played over the hall, then a work song among the workers
    job("legend_bard", "Legends", "The Bard Laureate's anthem and work song",
        "the Bard Laureate settled and composed the village's anthem, which rang out over the hall in note-block notes; then they sang a work song among the busiest workers, notes rising round them", 90),
    # The Beastmaster (ROADMAP 29.20): a war dog tamed for a guard, then the guard and the dog against zombies
    job("legend_beastmaster", "Legends", "The Beastmaster's war dog",
        "the Beastmaster tamed a war dog for the guard with bones from the chest and fitted it with wolf armour from the scutes; the dog followed its guard and fought the zombies beside them", 90),
    # The Founder (ROADMAP 29.23): the village's builder raises the Founder's statue by the hall from the barrels
    job("legend_founder", "Legends", "The Founder's statue going up",
        "the Founder asked for a statue, and the village's builder raised it by the hall from the materials in the barrels: a stepped plinth, a copper plaque and the Founder in stone with a hand raised", 360),
    job("legend_strike", "Legends", "A Legend on strike", "a Legend on strike left her stonecutter and picketed by the Village Hall under a red line", 120),
    # Strange moods (ROADMAP 29.10): a Master cleric claims her brewing stand under a purple line, the chest by it fills,
    # and she makes a named Masterwork, hung in an item frame, and becomes a Legend
    job("strange_mood", "Legends", "A strange mood and a Masterwork",
        "a Master cleric taken by a strange mood claimed her brewing stand, the chest was filled, and she made a Masterwork and became a Legend", 120),
    S("edicts", "Village Hall", "The Book of Edicts",
      "the Book of Edicts opened from the hall's lectern button, with Long Shifts in force, at GUI scales 2 and 4", 45,
      [("01_edicts_book", "The Book on the hall's screen: Long Shifts in force, two free slots, one locked for a City"), ("02_edicts_scale4", "The Book at GUI scale 4")]),
    # Reforms (ROADMAP 30.5): The Shift Bell's step on the quest page, then the fireworks and the chronicle line
    S("reform", "Village Hall", "Reforms: The Shift Bell",
      "Long Shifts' reform step was on the quest page; its three steps handed in, fireworks went up over the hall and the chronicle kept the reform", 60,
      [("01_reform_step", "The Shift Bell's first step, below the daily quests"), ("02_reform_fireworks", "Reformed: fireworks over the hall"),
       ("03_reform_chronicle", "The chronicle: the edict reformed")]),
    # Free Bread and Large Families (ROADMAP 30.6): the "free bread" mood in the hall's list; two births in one day
    S("free_bread", "Village Hall", "Edicts: Free Bread",
      "Free Bread was proclaimed: the hall's list shows the \"free bread\" mood and the Book tells its cost and reform", 45,
      [("01_free_bread_list", "The fed builder: \"free bread\" in the hall's list"), ("02_free_bread_book", "Free Bread in the Book of Edicts")]),
    S("large_families", "Village Hall", "Edicts: Large Families",
      "Large Families was proclaimed: two babies were born in one day and the chronicle kept both births", 45,
      [("01_large_families_babies", "Two babies born half a day apart"), ("02_large_families_chronicle", "The chronicle: both births")]),
    # Open Gates (ROADMAP 30.7): an inn with four guests, two a morning
    S("open_gates", "Village Hall", "Edicts: Open Gates",
      "Open Gates was proclaimed: the inn took in two travellers a morning and is full with four guests", 45,
      [("01_open_gates_inn", "Four travellers at the inn after two mornings"), ("02_open_gates_book", "Open Gates in the Book of Edicts")]),
    S("festival_season", "Village Hall", "Edicts: Festival Season",
      "Festival Season was proclaimed: the festival morning took its 5 emeralds from the treasury", 45,
      [("01_festival_season_before", "The festival icon: every 4 days, 5 emeralds, the treasury holds 10"),
       ("02_festival_season_after", "The festival icon after the morning: today, the treasury holds 5"),
       ("03_festival_season_treasury", "The treasury after the festival was paid for")]),
    S("tithe", "Village Hall", "Edicts: Tithe",
      "Tithe was proclaimed: the librarian's 20-emerald trade costs 22 and her 4-emerald one still 4", 40,
      [("01_tithe_without", "A librarian's trades without the Tithe"), ("02_tithe_with", "The same trades under the Tithe: 22, 10, 6, 4")]),
    # Curfew (ROADMAP 30.9): the village going indoors at dusk, the street empty, the guard on watch
    S("curfew", "Village Hall", "Edicts: Curfew",
      "Curfew was proclaimed: at dusk the villagers went to bed, the street emptied and the guard kept watch", 60,
      [("01_curfew_evening", "Evening: the villagers still out in the street"), ("02_curfew_dusk", "Dusk: everyone indoors and asleep, the guard on watch"),
       ("03_curfew_book", "Curfew in the Book of Edicts")]),
    # Conscription (ROADMAP 30.10): a night raid beaten back by the villagers with the militia's swords, beside the guard
    S("conscription", "Village Hall", "Edicts: Conscription",
      "Conscription was proclaimed: in a night raid a farmer, a builder and a librarian took up swords and beat the raiders back beside the guard", 60,
      [("01_conscription_raid", "The raid comes: the villagers take up the militia's stone swords"),
       ("02_conscription_fight", "Villagers and the guard beat the raiders back"),
       ("03_conscription_book", "Conscription in the Book of Edicts")]),
    # The Work Horn (ROADMAP 30.11): two builders before and during a rush, then the Book's last row
    S("work_horn", "Village Hall", "The Work Horn: a rush",
      "the hall's owner blew the Work Horn and the two builders placed more blocks a minute during the rush than before it", 75,
      [("01_work_horn_before", "Two builders at their usual pace"), ("02_work_horn_blown", "The Work Horn blown"),
       ("03_work_horn_rush", "The rush: 50% faster, sparks over the builders"), ("04_work_horn_book", "Used today, on the Book of Edicts' last row")]),
    # The Cradle (ROADMAP 30.12): a child asleep in a cradle at night, then a time-lapse of a newborn growing up
    S("cradle", "Village Hall", "The Cradle: a nursery village",
      "a child slept seated in the cradle at night, and a newborn was still a child half-way and grew up within 12000 ticks in the nursery village", 45,
      [("01_cradle_night", "A child asleep in the cradle at night"), ("02_cradle_newborn", "Morning: a newborn"),
       ("03_cradle_growing", "Half-way: 6000 ticks"), ("04_cradle_grown", "Grown up in half the time (12000 ticks)")]),
    # Seasons and the Harvest Idol (ROADMAP 30.14): two wheat fields through a harvest season, one with an idol
    S("harvest_idol", "Village Hall", "The Harvest Idol: crops grow faster in harvest season",
      "in autumn (harvest season) the wheat field within the idol's 32 blocks grew more stages half-way than the field out of its reach", 50,
      [("01_harvest_idol", "The Harvest Idol, sparkling in harvest season"), ("02_harvest_idol_fields", "Two fields sown: the idol's (right) and one out of its reach"),
       ("03_harvest_idol_growing", "Half-way: the idol's field is ahead"), ("04_harvest_idol_ripe", "The idol's field ripens first")]),
    # The Village Banner (ROADMAP 30.13): a street of stone houses under the village's colours, a knight's painted
    # shield, a neighbour by its banner on the routes page, the village by its own in the Book of Edicts
    S("village_banner", "Village Hall", "The Village Banner: the village's colours",
      "three builders hung the colours over their houses' doors, the knight's plain shield came out painted in them, and the routes page and the Book of Edicts showed the villages by their banners", 240,
      [("01_village_banner_street", "A street of finished houses under the village's banners"),
       ("02_village_banner_knight", "A knight with his shield painted in the colours"),
       ("03_village_banner_routes", "The trade routes page: Redfield by its banner"),
       ("04_village_banner_book", "The Book of Edicts under the village's own banner")]),
    # Guilds (ROADMAP 30.17): the Guildhall II with its Guild Master inside, the Builders' Guild on the Book's last row
    S("guildhall", "Village Hall", "Guild Charters and the Guildhall",
      "the player granted a Master builder a Guild Charter, the Builders' Guild was founded in the finished Guildhall II, and the Book of Edicts' last row showed it", 30,
      [("01_guildhall_front", "The Guildhall II: the hall and its tower wing with the bell"),
       ("02_guildhall_master", "Dara, Guild Master of the Builders' Guild, at the head of the long table"),
       ("03_guildhall_book", "The Builders' Guild on the Book of Edicts' last row: founded")]),
    # The Miners', Smiths', Woodsmen's (ROADMAP 30.18), Harvest, Herders' and Scholars' Guilds (30.19): each Guild Master's card, then the Book's guild row
    S("guilds", "Village Hall", "Nine guilds: Miners', Smiths', Woodsmen's, Harvest, Herders', Scholars', Healers', Merchants' and Wardens'",
      "the player chartered a Master miner, weaponsmith, lumberjack, farmer, shepherd, scholar, nurse, innkeeper and guard, the nine guilds were founded in nine finished Guildhalls, each master's card on the hall's list named their guild (and its pace where it has one), the farmer's farm reached 24 blocks, the shepherd bred up to 12, research cost a quarter less, the ill got well in 2 days, the nurse looked 48 blocks out, a 16-emerald traveller cost 12, guards trained up to Master and hit 10% harder, and the Book of Edicts' guild row showed every guild, five a page with a More guilds button", 40,
      [("01_guilds_miner", "Brokk, Guild Master of the Miners' Guild, working faster through it"),
       ("02_guilds_smith", "Hilde, Guild Master of the Smiths' Guild"),
       ("03_guilds_woodsman", "Rowan, Guild Master of the Woodsmen's Guild"),
       ("04_guilds_farmer", "Wren, Guild Master of the Harvest Guild"),
       ("05_guilds_shepherd", "Ebba, Guild Master of the Herders' Guild"),
       ("06_guilds_scholar", "Odo, Guild Master of the Scholars' Guild"),
       ("08_guilds_nurse", "Mira, Guild Master of the Healers' Guild"),
       ("09_guilds_innkeeper", "Mara, Guild Master of the Merchants' Guild"),
       ("10_guilds_guard", "Wulf, Guild Master of the Wardens' Guild"),
       ("07_guilds_book", "The Book of Edicts' guild row, page one: five guilds, founded, each with its perk, and the More guilds button"),
       ("12_guilds_book_more", "The guild row's next page: the Scholars', Healers', Merchants' and Wardens' Guilds")]),
    # Tonics (ROADMAP 30.15): a miner given Miner's Brew, its tooltip, the drink, then her status line
    # Edicts in the village's talk (ROADMAP 30.21): lines under Long Shifts, then after The Shift Bell
    S("village_talk", "Village Hall", "Village talk: Long Shifts and The Shift Bell",
      "three villagers by the hall talked of Long Shifts while it was in force, then of The Shift Bell once it was reformed", 30,
      [("01_village_talk_long_shifts", "Long Shifts in force: \"Long shifts again... my back.\""),
       ("02_village_talk_shift_bell", "Reformed: \"The shift bell's rung. Home we go.\"")]),
    # The Evergreen Charm (ROADMAP 34.19a): an ordinary elder refuses it, a Master takes it and outlives his days; the other leaves a grave
    S("ageless_elder", "Village Hall", "The Evergreen Charm: an ageless elder",
      "Bram's card on the hall's list showed an elder two days from his time, the charm's tooltip said what it does, Dara (an ordinary elder) refused it and the player kept it, Bram (a Master farmer) took it and it was used up, the chronicle said he will never leave us, and after 45 elder days Dara passed in the night and left a grave while Bram stayed, his card wearing the gold leaf badge", 40,
      [("03_ageless_elder_refused", "Dara, an ordinary elder, won't take it: the reason, and the charm kept"),
       ("04_ageless_elder_accepted", "Bram, a Master, takes it: he will never leave us"),
       ("05_ageless_elder_night", "45 elder days on, at nightfall: Dara's grave, and Bram still here"),
       ("06_ageless_elder_badge", "Bram's card now: the gold leaf badge")]),
    # Elders (ROADMAP 34.19): the day Bram becomes one, his card, his talk, and his slower walk beside a young villager
    S("elders", "Village Hall", "Elders: a quiet old age",
      "a day on, Bram had been grown 120 days and the chronicle said \"Bram is an elder now\" once; his card on the hall's list said \"Elder · grown 120 days\" with \"a quiet old age\" first in his mood (fed, a bed of his own); he said \"In my day this was all fields.\"; and walking side by side with young Tom he covered about 15% less ground in the same time", 35,
      [("01_elders_card", "Bram's card: an elder, grown 120 days, with a quiet old age"),
       ("02_elders_talk", "\"In my day this was all fields.\""),
       ("03_elders_walk", "The same walk, side by side: Bram, the elder, falls behind Tom")]),
    S("tonics", "Village Hall", "Tonics: all six",
      "the miner drank the Miner's Brew she was offered and her status line showed her 25% faster with 19 minutes left; a Toolsmith, a Scholar, an Orchard Keeper and a Lumberjack drank the four new tonics", 40,
      [("01_tonics_offer", "Dara the miner, and Miner's Brew in hand"), ("02_tonics_tooltip", "What the brew does, for whom, who makes it"),
       ("03_tonics_drunk", "She drinks it: 25% faster for 20 minutes"), ("04_tonics_status", "Her status line: 25% faster (Miner's Brew, 19 min left)"),
       ("05_tonics_hand_smiths_draught", "Smith's Draught in hand, all six tonics in the hotbar"),
       ("06_tonics_hand_scholars_infusion", "Scholar's Infusion in hand"), ("07_tonics_hand_harvest_cordial", "Harvest Cordial in hand"),
       ("08_tonics_hand_woodsmans_broth", "Woodsman's Broth in hand"),
       ("09_tonics_all_six", "A Toolsmith, a Scholar, an Orchard Keeper and a Lumberjack drink theirs")]),
    S("hall_quests", "Village Hall", "Quests, advice, the village map and the festival",
      "the quests, advice, village map, mercenaries and festival opened", 60,
      [("01_hall_quests", "Quests"), ("02_hall_advice", "What next?"), ("03_hall_map", "Village map"),
       ("05_hall_festival", "Festival")]),
    S("quest_journal", "Village Hall", "The quest journal, a tracked quest and a quest map",
      "the quest journal opened on its four tabs, a quest was tracked (its bar at the top of the screen) and a quest map was held", 50,
      [("01_journal_tabs", "The journal: Village, Personal, Story and Bounties"), ("02_journal_tracked", "A tracked quest's bar at the top"),
       ("03_quest_map", "A quest map, the place marked with a red X")]),
    S("friendship", "Village Hall", "Friendship: hearts with a villager",
      "a trade and a quest done for Dara put hearts on: the action bar shows them while you look at her, and the hall's tooltip shows your hearts and her best friends", 45,
      [("01_trade_hearts", "A trade: Dara's hearts in the action bar"), ("02_quest_hearts", "Her quest done: a third heart, with a puff"),
       ("03_hall_tooltip", "The hall: your hearts and her best friends")]),
    # Gifts (ROADMAP 31.6): wrapping on the crafting grid, giving, the hearts, the name day in the hall's tooltip
    S("gifts", "Village Hall", "Gifts: wrap an item and give it to a villager",
      "Gift Wrap and a Golden Carrot on the crafting grid made a Gift; Dara the farmer unwrapped it and loved it, her hearts went up in the action bar, and the hall's tooltip says when her name day is", 55,
      [("01_wrapping", "Gift Wrap and a Golden Carrot make a Gift"), ("02_giving", "Dara unwraps it: her line, bits of carrot, hearts"),
       ("03_hearts", "Her hearts went up; a second gift the same day is handed back"), ("04_hall_name_day", "The hall: when her name day is")]),
    # Heart events (ROADMAP 31.7): at two hearts Dara walks up and tells where she comes from; her life story page
    S("heart_event", "Village Hall", "A heart event: Dara tells where she comes from",
      "at two hearts Dara walked up to the player, faced them and told where she comes from, line by line over her head and in the chat; it went into the chronicle and onto her life story page", 60,
      [("01_walks_up", "Two hearts: Dara sets out towards you"), ("02_telling", "She tells it, a line every three seconds"),
       ("03_told", "The last line, each also in the chat in grey"), ("04_life_story", "Shift-click her in the hall: her life story")]),
    S("story_arc", "Village Hall", "A story arc: its chapters told, the Story tab and the chronicle",
      "a story arc began (its chapters told in chat), the Story tab showed the first chapter ticked and the second with its quest, and the chronicle kept them", 50,
      [("01_story_announced", "The chapters told in chat as they begin"), ("02_story_tab", "The Story tab: chapter 1 ticked, chapter 2 running with its quest"),
       ("03_story_chronicle", "The chronicle's story lines")]),
    # Classes at the Village Hall (ROADMAP 34.6): a household rises at dawn, then the hall's Classes tab and page
    S("classes", "Village Hall", "Classes: a household rises, and the Classes page",
      "a Peasant couple rose to Artisan at dawn with golden sparkles and a chat line, and the hall's Classes page counts each class's needs", 45,
      [("01_classes_rise", "Odo and Pia rise to Artisan at dawn"), ("02_classes_tab", "The Classes tab: households of each class"),
       ("03_classes_page", "The Burgher button: needs counted, what it gives, who's closest")]),
    # Higher jobs need higher classes (ROADMAP 34.8): a Peasant refused the Scholar's paper, a Burgher taking it
    S("class_jobs", "Village Hall", "Higher jobs need higher classes",
      "a Peasant was refused the Scholar's paper (the job needs a Burgher), and a Burgher took it", 30,
      [("01_class_jobs_refused", "Dara, a Peasant, is refused: a Scholar must be a Burgher"),
       ("02_class_jobs_taken", "Bram, a Burgher, becomes the Scholar")]),
    # What each class gives (ROADMAP 34.7): the Noble's Ball in place of every other festival, and the takings by class
    S("noble_ball", "Village Hall", "The Noble's Ball, and the takings by class",
      "the village's Noble household turned the festival into a Noble's Ball: guests at the hall, gold fireworks at dusk, the player a Hero for the night, and the takings split by class", 45,
      [("01_noble_ball_guests", "The ball: the guests gather at the hall"), ("02_noble_ball_fireworks", "Gold fireworks at dusk"),
       ("03_noble_ball_takings", "The name tag: the day's takings by class")]),
    # The Defence page (ROADMAP 32.3): the guards icon opens it; the lair, its named captain, its strength, the last attacks
    S("defence_page", "Village Hall", "The Defence page: the bandit camp and its chief",
      "the hall's guards icon opened the Defence page: the bandit camp, its named chief, its strength and the last attacks", 45,
      [("01_defence_page", "The camp's chief, by name"), ("02_defence_strength", "The camp's strength"),
       ("03_defence_attacks", "The last attacks")]),
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
    S("gallery", "Build families", "Every starter blueprint, with the Smithy, Mason's Yard, Fletcher's Lodge, Map Room, "
      "Farmstead, Fisher's Hut (on its shore), Weaver's Cottage and Bandstand",
      "every starter build was placed", 300,
      [("30_*@spread", "")]),
    S("workshops", "Build families", "Tinker's Workshops and Nether Gates", "every workshop and gate was placed", 60,
      [("30_*@spread", "")]),
    S("workplaces", "Build families", "A workplace for every worker: the 12 village houses a builder can build",
      "every workplace was placed", 90, [("30_*@spread", "")]),
    S("decor", "Build families", "Decorations", "every decoration was placed", 120, [("30_*@spread", "")]),
    # Sieges I (ROADMAP 32.4): a ravager ram breaks a Palisade Gate, the cracks showing
    job("siege_gate", "Threats", "A siege: the ram at the gate",
        "the ravager ram broke a gate block of the Palisade Gate, the cracks showing, and turned on the next", 120),
    S("defences", "Build families", "Walls and gates", "every wall and gate was placed", 90, [("30_*@spread", "")]),
    S("styles", "Build families", "Cottage II and Stone House II in every style", "every style was placed", 120,
      [("30_*@spread", "")]),
    # With Cobblemon since 28.15: the Pokémon jobs' houses grow too (POKEMON_HOUSE_WEIGHT makes them likely)
    S("village", "Build families", "One village of each type, with the Pokémon jobs' houses",
      "every village type generated, none leaving structure_void, and a Pokémon jobs' house grew with its worker in the job, standing free", 420,
      [("40_workshop_*@first", "A builder's workshop"), ("40_workshop_*@last", "Another of our houses"),
       ("41_pokemon_*@first", "A Pokémon jobs' house"), ("41_pokemon_*@last", "Another Pokémon jobs' house")],
      env={"WORKSHOP_WEIGHT": "200", "POKEMON_HOUSE_WEIGHT": "60"}, cobblemon=True),
    S("pokemon_builds", "Build families", "Builds for the Pokémon jobs, both tiers",
      "all ten builds stand with their job blocks", 120, [("[12][0-9]_*@spread", "")], cobblemon=True),
    S("village_habitat", "Habitat Keeper", "The village's own Habitat Block (Cobblemon 1.8)",
      "the Expert keeper put a natural Habitat Block under the garden's centre stone and the hall lists today's Pokémon", 120,
      [("01_garden", "The finished Habitat Garden"), ("02_habitat", "Its centre stone is now a Habitat Block"),
       ("03_hall_line", "Close up: it still looks like moss")], cobblemon18=True),
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
    if s["cobblemon18"]:
        out["COBBLEMON18"] = "true"
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
