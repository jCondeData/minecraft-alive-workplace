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
pick: "frame_*@first", "frame_*@middle", "frame_*@last", "30_*@spread" (4 spread over the matches, labeled from
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
    "Carpenter", "Crafter", "Mason", "Leatherworker (dyer)", "Chef", "Armorer (smelter)", "Toolsmith", "Weaponsmith", "Fletcher",
    "Shepherd", "Butcher (herder)", "Cleric (alchemist)", "Librarian (scribe)", "Cartographer (explorer)", "Postman",
    "Guard", "Threats", "Nurse", "Shopkeeper", "Ferryman", "Bard", "Trainer", "Trainer Leader", "Move Tutor", "Ball Smith",
    "Pokémon Trader", "Fossil Scientist", "Berry Breeder", "Steward", "Camp Cook", "Gem Grower", "Jeweler", "Vintner", "Tailor", "Printer", "Habitat Keeper", "Daycare Keeper", "Furnicrafter", "Mechanic", "Create", "Village Hall", "Realm", "Realm builds", "Legends", "Wonders", "Everyone at work", "Build families",
]

START, WORKING, DONE = ("01_start", "Start"), ("work_*@middle", "At work"), ("03_done", "Done")


def S(name, group, title, what, est, stills, env=None, cobblemon=False, mega=False, cobblemon18=False, furniture=False,
      create=False, waystones=False):
    """One scene. est: seconds the scene itself takes on GitHub's runner (the client's start-up is added).
    cobblemon18: a Cobblemon-1.8-only feature, always filmed with Cobblemon 1.8.1 (-Pcobblemon18=true, ROADMAP 28.14).
    furniture: filmed with the pack's furniture mods (-Pfurniture=true, ROADMAP 37.4).
    create: filmed with the owner's Create port (libs/, -Pcreate=true; Milestone 39).
    waystones: filmed with Waystones and Balm (-Pwaystones=true; Milestone 41)."""
    return {"name": name, "group": group, "title": title, "what": what, "est": est, "stills": stills,
            "env": env or {}, "cobblemon": cobblemon or cobblemon18, "mega": mega, "cobblemon18": cobblemon18,
            "furniture": furniture, "create": create, "waystones": waystones}


def job(name, group, title, what, est=150, extra=(), **kw):
    """A scene staged by the harness's job scenes (JobScenes.java): start, at work, done, plus any extra stills."""
    return S(name, group, title, what, est, [START, WORKING, DONE, *extra][:4], **kw)


SCENES = [
    # Picking a job (ROADMAP 21.1a): one block, several jobs, each picked with its item
    S("stations", "Picking a job", "One composter, four jobs", "the villager took each job its item picks at the composter", 45,
      [("02_orchard_keeper", "Sweet berries: Orchard Keeper"), ("03_florist", "A flower: Florist"),
       ("04_composter", "Bone meal: Composter"), ("05_farmer", "Wheat: back to Farmer")]),
    # Retraining (ROADMAP 26.6): a working Librarian made a Builder for emeralds at the Blueprint Table
    S("retrain", "Picking a job", "Retraining a Librarian as a Builder",
      "the Journeyman Librarian was shown the price, then paid for and retrained as a Novice Builder at the Blueprint Table", 40,
      [("01_librarian", "A Journeyman Librarian by a Blueprint Table"), ("02_offer", "First click: the price"),
       ("03_builder", "Second click: paid, now a Builder")]),
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
       ("14_cancelled", "Canceled: the blueprint is back, still placed"), ("16_slope_building", "Moved onto a slope: foundation filled")]),
    S("missing", "Builder", "What a build is still missing, and its material list", "the blueprint's tooltip lists what the chests are short of, and a Book and Quill becomes the list", 60,
      [("00_missing_site", "The placed blueprint and the Blueprint Table"), ("01_blueprint_missing", "Still-missing tooltip"),
       ("02_material_list", "The material list (a book)"), ("03_material_list_page", "What's still to bring")]),
    # 36.10: the search box over the library: "smith" narrows it to the forges, a search for nothing says so
    S("table", "Builder", "Blueprint Table", "the table screen opened with the Starter Cottage's beds (and any jobs) under its size, \"smith\" typed in the search box narrowed the library to the builds that bring a smith, a search for nothing said so, and a file was uploaded", 65,
      [("10_table_library", "Library"), ("10s_table_search", "Searched for \"smith\": the list narrowed"),
       ("11_table_upload", "Upload a file"), ("12_table_uploaded", "Uploaded")]),
    # 36.13: the Blueprint Table keeps bought blueprints: Store All, listed with counts, one taken back out
    S("table_library", "Builder", "Blueprint Table: a library for your blueprints",
      "the table's Stored tab started empty, Store All put the four blueprints carried on its shelf (two Starter Cottages listed as 2 stored), and Take One Out handed a cottage back into the player's hand", 40,
      [("01_stored_empty", "The Stored tab, empty"), ("02_stored", "Stored, with how many of each"),
       ("03_taken_out", "One taken out: the count goes down"), ("04_in_hand", "Back in hand")]),
    # 26.9: the selected build in 3D in the table's library, turning, dragged, large, and an uploaded file
    S("table_preview", "Builder", "Blueprint Table: see a build before you pick it",
      "the table's library showed the selected build in 3D: the starter cottage turning and dragged round, then in Sandstone from the style row, the largest build scaled to fit with its full materials list, and an uploaded .litematic", 60,
      [("01_preview_house", "A starter house"), ("03_preview_style", "Picked in Sandstone"),
       ("04_preview_large", "A large build, scaled to fit"), ("06_preview_imported", "An uploaded build")]),
    # 36.4: the Signpost's boards name the villages and builds each way
    S("signpost", "Builder", "The Signpost's boards say where things are",
      "a Signpost at a crossroads between two villages had its four boards written as it was finished: Millford east, Ashford west, the Well south, Welcome north", 40,
      [("01_signpost_crossroads", "The signpost at the crossroads"), ("02_board_east", "East: the next village"),
       ("03_board_west", "West: a village further off"), ("04_board_south", "South: the Well")]),
    # 36.5: a long entry read without clicking it: held while the mouse goes into the pane, scrolled there
    S("hall_reading", "Village Hall", "Reading a long entry without clicking it",
      "a long chronicle entry was hovered, stayed read out as the mouse moved into the pane and scrolled there to its last line; the Storehouse board did the same", 45,
      [("01_chronicle_hovered", "A long entry hovered"), ("02_chronicle_held", "Held with the mouse in the pane"),
       ("03_chronicle_last_line", "Scrolled to its last line"), ("05_board_last_line", "The Storehouse board, scrolled")]),
    # 36.1: a build with a basement in the table's 3D preview: the cellar under a faint ground plane, fitted whole
    S("table_basement", "Builder", "Blueprint Table: basements in the preview",
      "the table's library showed Starter Cottage IV with its cellar under a faint ground plane, turning, then the Storehouse and its vault, each size line saying how deep it goes", 45,
      [("01_cottage_cellar", "Starter Cottage IV and its cellar"), ("02_cottage_turned", "Turned"),
       ("03_storehouse_vault", "The Storehouse and its vault"), ("04_storehouse_turned", "Turned")]),
    S("shapes", "Builder", "Shape Planner", "the Shape Planner screen opened with its materials", 45,
      [("01_shapes_screen", "Shape Planner"), ("02_shapes_info", "Size and blocks")]),
    S("scan", "Builder", "The Scan Tool: your own build as a blueprint", "a hut was marked with the Scan Tool and saved as a blueprint", 30,
      [("01_scan_marked", "Two corners marked"), ("02_scan_saved", "Saved as a blueprint")]),
    S("style_menu", "Builder", "Build it in a style", "the style picker opened, a style was chosen and the build mirrored", 50,
      [("01_style_screen", "Style picker"), ("02_style_chosen", "Dark oak chosen"), ("03_style_mirrored", "Mirrored")]),
    # Miner
    S("quarry", "Miner", "A miner digs out a quarry", "the miner dug out the whole quarry", 420,
      [("frame_*@first", "Start"), ("frame_*@middle", "Digging"), ("50_quarry_done", "Dug out")]),
    # Miners make their own pickaxe (ROADMAP 36.11): a worn stone pickaxe breaks, a new one is made at the blast furnace
    job("miner_pickaxe", "Miner", "Making a new pickaxe from what she mined",
        "the miner's worn pickaxe broke, she made a stone pickaxe at the blast furnace from the cobblestone she dug and dug on to the end", 150,
        extra=[("04_making_pickaxe", "Making the pickaxe at the blast furnace")]),
    # The Quarry build (ROADMAP 36.12): the Miner at its blast furnace digs its pit without a marker, and it grows back
    job("quarry_build", "Miner", "A Quarry that grows back",
        "the Miner at the Quarry's blast furnace took its pit without a marker and dug, and the mined rock grew back from the bottom up", 300),
    # Village-wide reach (ROADMAP 36.20): a quarry marked 110 blocks out, far outside the village, and the miner walks out to it
    job("far_site", "Miner", "A quarry far outside the village",
        "the miner took a quarry marked 110 blocks out, far outside the village, walked out to it and dug it", 240,
        extra=[("04_far_site_arrived", "Arrived at the far quarry")]),
    # Miners dig deeper as they level up (ROADMAP 36.22): a Master's quarry down to bedrock, 96 deep, filmed through a window at the bottom
    job("deep_quarry", "Miner", "A Master digs down to bedrock",
        "a Master miner took a quarry down to bedrock, 96 blocks deep, and dug its bottom layers far past the 64 every miner digs, keeping the spiral stair", 240),
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
    # B222: graves lying about the village go to its Graveyard (here the plot by the hall, the village having no Graveyard)
    job("undertaker_graveyard", "Undertaker", "Laying a grave to rest", "the undertaker moved Mira's grave from the street to the plot by the hall"),
    # 36.16: a death matters: the village is told who died, the family mourns, the undertaker leads the funeral
    job("village_mourns", "Undertaker", "The village mourns", "the undertaker led Mira's funeral at her grave, and her husband and son mourned her", 160),
    # 36.17: a block several jobs share gives one at random, likelier the fewer the village has
    job("random_job", "Picking a job", "Four composters, four jobs at random",
        "four jobless villagers took four composters by themselves, and not all became farmers", 120),
    # Legends (ROADMAP 29.2): the engine, with a Legend of the scene's own: the plain outfit, Master level, a pace power
    job("legend", "Legends", "A villager becomes a Legend",
        "a villager became a Legend, a Master who speeds up the builder beside them"),
    job("innkeeper", "Innkeeper", "A traveler checks in", "a traveler came to stay at the inn", 120,
        [("04_hire_screen", "Hire a traveler")]),
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
      [("*_porter@middle", "Carrying"), ("20_storehouse_closeup", "The storehouse"),
       ("21_request_board", "The Storehouse board in the Blueprint Table's style: requests listed, the store at a glance"),
       ("22_request_read_out", "A request read out in the pane")]),
    # Modded storage networks in the Storehouse: part one (ROADMAP 37.9) a Sophisticated Storage controller and its linked
    # barrels and chests, part two (37.10) a Tom's Simple Storage inventory connector and its network, each one store
    S("modded_storehouse", "Porter", "The Storehouse reads a Sophisticated controller and a Tom's Simple Storage network each as one store",
      "the Storehouse's store is the controller alone, its five linked blocks counted once, with the board's 'Linked through a storage controller: 5 blocks'; "
      "then a Storehouse's store is the inventory connector, each item counted once, the board names the 3 linked blocks and the terminal opens",
      100, [("01_storehouse", "The Storehouse with a Sophisticated controller on its linked barrels and chests"),
            ("02_board", "The Storehouse board: one store, and the line 'Linked through a storage controller: 5 blocks'"),
            ("04_board", "The Storehouse board: linked through an inventory connector, 3 blocks"),
            ("05_terminal", "The village's goods in Tom's own terminal screen")], cobblemon=True, furniture=True),
    job("dropbox", "Porter", "Emptying a Drop Box", "the porter emptied the Drop Box into the store", 120,
        [("04_dropbox_screen", "Drop Box")]),
    # Crafters
    S("carpenter", "Carpenter", "Making a builder's woodwork", "the carpenter made woodwork for the builder", 150,
      [("*_carpenter@first", "Start"), ("*_carpenter@middle", "Working"), ("30_carpenter_closeup", "At the crafting table")]),
    # Job chains (ROADMAP 37.14a, which retired the Crafter): several jobs filling one builder's Market Stall list from raw
    # makings, each its own kind a step at a time (the Carpenter's woodwork, the dyer's red wool, the Tinkerer's lantern)
    S("job_chains", "Carpenter", "Several jobs filling one builder's list, each making its own kind a step at a time",
      "several jobs filled the builder's list", 150,
      [("*_job_chains@first", "Start: only raw makings in the builder's chest"), ("*_job_chains@middle", "The jobs working through their chains"),
       ("30_job_chains_closeup", "At the workstations")]),
    # The pack's furniture from logs (ROADMAP 37.15, 37.14a): Handcrafted's chairs by the Carpenter at the crafting table's
    # recipe, CobbleFurnies' by the Furnicrafter at its Furni Crafter, for a builder's dining corner
    S("crafter_furniture", "Carpenter", "Making the pack's furniture from logs: Handcrafted chairs by the Carpenter, a CobbleFurnies chair by the Furnicrafter",
      "the Carpenter and the Furnicrafter made the Handcrafted and CobbleFurnies chairs from logs and the builder built them", 150,
      [("*_crafter_furniture@first", "Start: a table and oak logs in the chest"), ("*_crafter_furniture@middle", "Sawing, cutting and crafting"),
       ("30_crafter_furniture_corner", "The dining corner, built")], cobblemon=True, furniture=True),
    job("mason", "Mason", "Cutting stone for a builder", "the mason cut stone bricks the builder needed", 240),
    job("dyer", "Leatherworker (dyer)", "Dyeing wool for a builder", "the dyer made colored wool the builder needed", 240),
    # Builder crews (ROADMAP 23.1a): a builder and three helpers on one build, about a third of the time alone
    job("crew", "Builder", "A crew of four on one build", "four builders built the stone house together, each placing a share of it", 150),
    # Scaffolding (ROADMAP 35.6, 35.6a): a 14-high pillar with an overhanging cap, built from a column of vanilla
    # scaffolding the builder climbs, filmed close up at the builder's height, and taken down after
    S("scaffolding", "Builder", "Scaffolding up a pillar, and down again",
      "the builder put up scaffolding to finish the pillar and its cap, then took it all down into the chest", 300,
      [START, ("02_on_scaffolding", "Up the scaffolding, working high on the pillar"),
       ("02_taking_down", "Taking the scaffolding down"), DONE]),
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
    # B279: the material list of a build under way, from the builder himself: a Book and Quill, or the whole list in chat
    S("builder_list", "Builder", "What the build still needs, from the builder",
      "a Book and Quill used on the builder became the rest of his build's list with the chest's share checked off, and a sneak-click with an empty hand put the whole list in chat", 40,
      [("00_half_built", "Halfway up, a Book and Quill in hand"), ("01_list_book", "The list: how far along, what's counted"),
       ("02_list_page", "What's still to bring, checked off"), ("03_chat_list", "Sneak-click: the whole list in chat")]),
    # B290: a builder at a workshop's table counts the village Storehouse, and a sneak-click moves him to another table
    S("builder_table_move", "Builder", "Moving a builder to another Blueprint Table",
      "the builder at the workshop's table counted the Storehouse's stone, and a sneak-click on the second table moved him there with both tables standing", 40,
      [("00_counts_the_storehouse", "At the workshop's table, the Storehouse counted"), ("01_moved", "Sneak-click: he works at the new table"),
       ("02_status_names_the_table", "His status names the table")]),
    # B280: the Terrace a block above the lawn round it, landscaped: the pit filled, no raised band of dirt on the grass
    S("terrace_grade", "Builder", "Levelling leaves the lawn level round a raised build",
      "round the finished Terrace the lawn stayed level: the pit was filled, nothing was raised above the grass and no sand or gravel was left", 120,
      [("01_before", "Before: a bump a block above the lawn, a pit beside it"), ("02_front", "Landscaped: the lawn as it was, the pit filled"),
       ("03_corner", "The corner: no band of dirt on the grass")]),
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
      [("02_pokemon_center_2", "Pokémon Center II"),
       ("05_lodge", "Mountain Lodge look, both tiers"),
       ("06_plaza", "Sunny Plaza look, both tiers"),
       ("03_healing", "In the Healing Machine")], cobblemon=True),
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
    # Mega Stones from the village (ROADMAP 38.6): a Journeyman Jeweller cuts a Mega Stone for the village's Master
    job("mega_jeweller", "Jeweler", "Mega Stones: the Jeweler cuts one for the village's Master",
        "the jeweler cut a Mega Stone for a Pokémon on the village Master's team from a Mega Crystal, gold and emeralds the player put in the Storehouse",
        cobblemon=True, mega=True),
    # The Mega Shrine (ROADMAP 38.7): on a festival day a village trainer Mega Evolves their ace at the shrine's pedestal
    job("mega_shrine", "Trainer", "The Mega Shrine: a festival show",
        "the village trainer walked up to the Mega Shrine's pedestal and Mega Evolved their ace, and the villagers watching got +3 mood",
        cobblemon=True, mega=True),
    # More Mega trainers (ROADMAP 38.5): a City's Trainer Leader wears the Mega Bracelet and Mega Evolves against the player
    S("mega_trainers", "Trainer Leader", "Mega trainers: a City's Leader Mega Evolves",
      "the City's Trainer Leader wears the Mega Bracelet and their lead Mega Evolved in a battle against the player", 420,
      [("01_mega_leader", "The Leader's Mega Bracelet"), ("mega_battle_*@middle", "Mid-battle"), ("99_mega_battle_end", "Battle over")],
      cobblemon=True, mega=True),
    S("arena", "Trainer Leader", "The Arena, three tiers",
      "every tier's ring center, boxes and seats are where Arenas says, and Arena III has a Healing Machine in each trainers' room", 50,
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
    # Cobblecuisine on the Chef's menu (ROADMAP 38.8): a Journeyman Chef cooks a salad and a curry from their own recipes
    # Cobblecuisine's dishes on request (ROADMAP 38.8a): asked for on the hall board's Dishes tab, cooked by the Chef
    job("cuisine_kitchen", "Chef", "Cobblecuisine: dishes asked for at the hall's board",
        "asked at the hall board's Dishes tab, the Journeyman Chef cooked 4 Mixed Vegetable Salads and 2 Beanburger Curries "
        "from their own recipes into the chest; the Fruit Punch (no Calcium in the store) is still waiting and the cake is locked for his level",
        extra=[("04_board_dishes", "The board's Dishes tab: the punch still asked for, the cakes locked")], cobblemon=True),
    # Campfire Pot recipes (ROADMAP 38.11): the Chef cooks the pot's candies and sweets, found by recipe type
    job("campfire_pot", "Chef", "Campfire Pot recipes: candies from the Chef",
        "the Chef cooked EXP Candies and Berry Sweets from the Campfire Pot's recipes into the chest",
        cobblemon=True),
    # The Furnicrafter (ROADMAP 38.12, CobbleFurnies): furniture the storehouse ordered, made from the store's materials
    job("furnicrafter", "Furnicrafter", "The Furnicrafter: CobbleFurnies furniture from the store's materials",
        "the Furnicrafter made the Oak Chairs, Red Lamps and Poke Ball Desk the storehouse ordered from the planks, wool, "
        "redstone and concrete in its store",
        cobblemon=True, furniture=True),
    # Bean farms (ROADMAP 38.9): the farmer cuts ripe Cobblecuisine beans, leaves the green ones and sows the bare rows
    job("bean_farm", "Farmer", "Cobblecuisine: beans for the menu",
        "the farmer cut the ripe Cobblecuisine beans into the chest, left the green ones and sowed the bare rows",
        cobblemon=True),
    # Partner treats (ROADMAP 38.10): a builder's pastured Machop eats a Poké Puff from the store, once a day
    job("partner_treats", "Builder", "Cobblecuisine: a partner's treat of the day",
        "the builder's Machop ate a Poké Puff from the storehouse chest as its treat of the day",
        cobblemon=True),
    job("gem_grower", "Gem Grower", "A ripe amethyst cluster picked, the budding block kept",
        "the gem grower picked the ripe amethyst cluster and left the budding amethyst", 150),
    job("jeweller", "Jeweler", "Making amethyst rings at the stonecutter", "the jeweler made amethyst rings at the stonecutter"),
    job("habitat_keeper", "Habitat Keeper", "A snack set out, a log slathered, a shiny spotted",
        "the habitat keeper set out a snack, slathered the log and spotted a shiny Eevee", 150, cobblemon=True),
    # Village-wide
    S("hall", "Village Hall", "The Village Hall, its screen and calendar", "the Village Hall screen and its calendar page opened", 75,
      [("02_hall_people", "The hall's screen in the Blueprint Table's style: figures and toolbar, pages and people, the village at a glance"),
       ("03_hall_builder", "A builder read out in the pane"), ("08_hall_calendar", "The calendar"),
       ("10_hall_pinned_scale3", "A clicked villager kept in the pane, GUI scale 3")]),
    # Painting the plan (ROADMAP 27.3): the City Plan screen over a real village, at GUI scales 2 and 4
    S("city_plan", "Village Hall", "Painting the City Plan", "the City Plan screen opened with Homes, Workshops and Gardens zones in three styles", 60,
      [("01_city_plan_start", "The first zone"), ("02_city_plan_scale2", "Three zones in three styles, GUI scale 2"),
       ("03_city_plan_scale4", "The finished plan, GUI scale 4")]),
    # The plan on the ground and on the hall's map (ROADMAP 27.4)
    S("city_plan_ground", "Village Hall", "The plan on the ground", "zone edges, a street and the wall line show on the ground while the player holds the City Plan, and the hall's map of the plan hangs framed by the hall", 60,
      [("01_city_plan_ground", "Zone edges and the street on the ground"), ("02_city_plan_wall", "The wall line at the village's corner"),
       ("03_city_plan_framed", "The plan on the hall's map, framed")]),
    # The hall's sidebar (ROADMAP 40.1): each of the ten sections, at GUI scales 2 and 3
    S("hall_sidebar", "Village Hall", "The hall's sidebar and its sections",
      "each of the hall's ten sections opened from the sidebar, at GUI scales 2 and 3", 70,
      [("01_overview_scale2", "Overview: the village at a glance and what it needs next, GUI scale 2"),
       ("02_people_scale2", "People, GUI scale 2"), ("05_defense_scale2", "Defense, GUI scale 2"),
       ("12_people_scale3", "People, GUI scale 3")]),
    # The People table (ROADMAP 40.2): Name, Job, Level, Mood, then one villager's row opened
    S("hall_people", "Village Hall", "The People table",
      "the hall's People section as a table of everyone (a builder, a guard, workers, a jobless villager, a nitwit and a "
      "child), then the first row opened to that villager's details with Job and Bed buttons", 45,
      [("01_people_table", "The People table: Name, Job, Level and Mood, with the village's counts on top"),
       ("02_people_row_open", "A row opened: traits, what they're doing, needs, mood and why, class, and the Job and Bed buttons"),
       ("03_people_story", "Story on the open row: their life story, as shift-click opened it from the old list (B547)")]),
    # Job ▾ in the People table (ROADMAP 40.3): every job, the free ones with how many, the price, two clicks to pay
    S("hall_people_job", "Village Hall", "Changing a job from the People table",
      "Job ▾ on a librarian's row opens the list of every job (those with a free block say how many, the rest are grayed "
      "with the block to place), the price by her level on top; a click on Mason shows the price, a second pays and she "
      "is a Novice Mason", 50,
      [("01_job_list", "Job ▾ opened: the price by level on top, the free jobs with how many blocks, the rest grayed"),
       ("02_job_picked", "Mason clicked once: \"Click again to pay\" under it, nothing paid yet"),
       ("03_job_changed", "Clicked again: paid, and her row says Mason")]),
    # Bed ▾ in the People table (ROADMAP 40.4): the village's beds, free first with where they are, a swap, a bed given
    S("hall_people_bed", "Village Hall", "Giving a villager a bed from the People table",
      "Bed ▾ on Dara's row opens the village's beds (the free ones first with how far and which way from the hall, "
      "Jem's to swap with), a click makes the nearest free bed hers, and opened again it says \"their bed now\"", 50,
      [("01_bed_list", "Bed ▾ opened: the free beds with where they are, Jem's bed to swap, the note on wagon beds and cradles"),
       ("02_bed_given", "The nearest free bed clicked: hers from tonight"),
       ("03_bed_after", "Opened again: her bed on top as \"their bed now\", the other free bed, Jem's to swap")]),
    S("hall_pages", "Village Hall", "The chronicle and trade routes", "the chronicle and trade-route pages opened", 45,
      [("01_hall_chronicle", "Chronicle"), ("02_hall_routes", "Trade routes: the Trade page's Routes tab")]),
    # Free job blocks (ROADMAP 36.9): the hall lists the job blocks nobody works at; picking a jobless villager gives one
    S("hall_jobs", "Village Hall", "Free job blocks: filling an empty Storehouse from the hall",
      "the empty Storehouse was listed with its building and distance, and Dara, picked for it, became the Porter", 50,
      [("01_free_jobs", "The Free job blocks page: the Storehouse and a bench"), ("02_who_takes_it", "Who could take the Storehouse"),
       ("03_dara_porter", "Dara picked: now the Porter, walking over")]),
    # Change job from the hall (ROADMAP 36.18): a working villager's page, the free jobs with the price, paid on a second click
    S("hall_retrain", "Village Hall", "Changing a villager's job from the hall",
      "Dara, a Journeyman Librarian, became the Porter from the hall's Change job for 20 emeralds, at once, her lectern left free", 55,
      [("01_worker_page", "Dara's page in the residents list, with Change job"), ("02_price", "The free Storehouse and the Builder, with her price"),
       ("03_dara_porter", "Paid: the list shows her as the Porter straight away"), ("04_walking", "Dara walks to her new Storehouse")]),
    # The Builder asks before repairing a house (ROADMAP 36.15): the question in chat and on the hall's Repairs page, then Yes
    S("repair_ask", "Village Hall", "The Builder asks before repairing a house",
      "Bram found 5 blocks missing from the Starter Cottage and asked in chat; the hall's Repairs page listed it waiting for an answer, "
      "its view had Yes and Not now, and after Yes the page showed Bram repairing it", 50,
      [("01_repair_question", "The question in chat: Repair it? [Yes] [Not now]"), ("02_repairs_page", "The hall's Repairs page: the cottage, waiting"),
       ("03_yes_or_not_now", "The cottage's view: Yes, repair it, or Not now"), ("04_repairing", "After Yes: being repaired by Bram")]),
    # Bring an old house up to its latest look (ROADMAP 36.21): a Stone House built from its first drawing, the hall's Refresh
    # button with what changes, then the house once Bram has refreshed it
    S("refresh_old_house", "Village Hall", "Refresh an old house to its latest look",
      "A Stone House built from its drawing before B260 (as on the live server) was offered Refresh to the latest look on the "
      "hall's Repairs page; a click showed what changes, the button sent Bram, and he took the old leftovers away and put the "
      "new pieces in, nothing doubled", 230,
      [("01_old_house", "Before: the Stone House as its drawing before B260 built it"), ("02_repairs_page", "The Repairs page: the house, to refresh"),
       ("03_what_changes", "What changes, and the Refresh to the latest look button"), ("04_after", "After: the house in its latest look")]),
    # Upgrade a built home from the Village Hall (ROADMAP 36.14): the Upgrades page, a blocked row, and a click sending a Builder
    S("hall_upgrade", "Village Hall", "Upgrading a built home from the hall",
      "the Upgrades page listed the Starter Cottage with its level, cost and free space and the Stone House II blocked by a garden fence, "
      "and a click on the cottage sent Bram to build its level II", 50,
      [("01_upgrades_page", "The Upgrades page: the cottage, its next level, cost and free space"),
       ("02_blocked_by_a_fence", "The Stone House II: blocked by a fence where its level III goes"),
       ("03_bram_builds_it", "Clicked: Bram builds the Starter Cottage II")]),
    # The price board (ROADMAP 33.4): the hall's Trade page on Prices, a good's tooltip, and the name icon's Known for line
    S("price_board", "Realm", "The price board: what a village is known for and short of",
      "the hall's Trade page showed Thornholm's prices: Timber and Wool starred, Bread marked short, arrows on what moved, "
      "Timber's tooltip with the dearer and the cheaper village, and the name icon's Known for line", 60,
      [("01_price_board", "The Trade page, Prices tab"), ("02_price_tooltip", "Timber: both prices, and where it's dearer and cheaper"),
       ("03_hall_known_for", "The hall's name icon: Known for, Short of")]),
    # The Merchant Prince's board (ROADMAP 33.23): half the spread, and prices from every village in caravan range
    S("merchant_prince_board", "Legends", "The Merchant Prince's board",
      "the Merchant Prince settled by Thornholm's hall; its Prices tab sells at only 5% over, and Timber's tooltip "
      "names Dunmore, in caravan range but no partner, as where it's dearest", 50,
      [("01_prince_prices", "The Prices tab with the Prince: 5% over, half the usual spread"),
       ("02_prince_dunmore", "Timber: dearest in Dunmore, a village that isn't a partner")]),
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
    # Caravans you can see (ROADMAP 33.7): a carter and two pack llamas leave for Ashford, then Ashford's caravan arrives and unloads
    job("caravan", "Realm", "Caravans you can see: one leaves, one arrives",
        "Thornholm's carter led two llamas with chests and red carpets from the Storehouse toward Ashford and was gone at the village's edge; "
        "then Ashford's caravan came in from that edge, its llamas stood by the Storehouse while the logs were unloaded, and it walked back out", 170),
    # The Colony Charter (ROADMAP 33.8): bought on the Colonies tab, its map with the ring and the chosen spot, its tooltip
    S("colony_charter", "Realm", "The Colony Charter: choosing where a colony goes",
      "Thornholm, a City, sold its owner a Colony Charter on the hall's Colonies tab (the treasury paid 20 emeralds, the player 12); "
      "the charter's map showed the loaded land like a vanilla map on parchment, a banner for Thornholm, Ashford and Farholt, the ring "
      "256 to 1,024 blocks out and the circles round the other halls; a click put the red cross 612 blocks north-east, and the tooltip says so", 75,
      [("01_colonies_tab", "The Colonies tab: a charter for 32 emeralds, the treasury pays 20"), ("02_colony_map", "The charter's map: banners, the ring, a place pointed at"),
       ("03_colony_spot", "A click: the red cross, 612 blocks north-east"), ("04_colony_tooltip", "The charter's tooltip names the spot")]),
    # The settlers set out (ROADMAP 33.9): the order at the hall, the gathering, the walk out, on the road
    S("colony_departure", "Realm", "The settlers set out for the colony",
      "the owner right-clicked Thornholm's hall with the Colony Charter: Dara and Tomas volunteered and the Storehouse gave their supplies; "
      "they gathered at the hall, the bell rang and they walked out toward the spot, and once out of sight the Colonies tab read "
      "\"On the road to Newbrook\"", 95,
      [("01_colony_order", "The Colonies tab: the order getting ready, with its call-off"), ("02_settlers_gathered", "The settlers gathered at the hall"),
       ("03_walking_out", "The bell has rung: they walk out toward the spot"), ("04_on_the_road", "On the road to Newbrook")]),
    # A colony is born (ROADMAP 33.10): the settlers arrive, make camp with a hall and a Travel Post, and the builder starts
    S("colony", "Realm", "A colony is born",
      "Thornholm's settlers reached the spot and made camp facing home, with Newbrook's Village Hall and a Travel Post in front of "
      "the wagon; Tomas took the camp's Blueprint Table and started the Sandstone Starter Cottage beside the camp, the Sandstone "
      "Storehouse queued on the other side, and the first walls went up; the owner got the settlers' letter", 85,
      [("01_the_spot", "The spot, before the settlers arrive"), ("02_camp_made", "The camp, Newbrook's hall and its Travel Post"),
       ("03_builder_at_work", "Tomas starts the Sandstone cottage"), ("04_first_walls", "The first walls go up")]),
    # Legends of the realm (ROADMAP 33.23): a colony founded with the Founder's blessing gets his statue beside its camp
    S("founder_colony", "Legends", "A colony founded with the Founder's blessing",
      "Thornholm's settlers, sent with the Founder's blessing, reached the spot and made camp; both chronicles say the colony "
      "was founded with the Founder's blessing, and the Founder's statue stands beside the camp", 50,
      [("01_the_spot", "The spot, before the settlers arrive"), ("02_camp_made", "The camp, Newbrook's hall and its Travel Post"),
       ("03_founder_statue", "The Founder's statue beside the colony's camp")]),
    # Founding a realm (ROADMAP 33.11): the Realm tab founds the Barony, adds the owner's villages, the rank-up fireworks
    S("realm", "Realm", "Founding a realm: the Barony, then a County with fireworks",
      "on Thornholm's Realm tab the owner, a Town's, founded the Barony of Thornholm and added Ashford with one click; adding "
      "Farholt made three villages with a Town capital, the County of Thornholm, and fireworks went up over the hall; a blue "
      "banner with a gold cross clicked on the arms set them, and the tab lists the members, the capital crowned", 75,
      [("01_found_a_realm", "The Realm tab of a Town: Found a realm"), ("02_barony", "The Barony of Thornholm: Ashford added"),
       ("03_fireworks", "Farholt joins: a County, fireworks over the capital"), ("04_county_tab", "The County of Thornholm, its arms and members")]),
    # Other players' villages and choosing the capital (ROADMAP 33.12): the invitation letter, the answer, the capital vote
    S("realm_invite", "Realm", "An invitation to another player's village, and a vote on the capital",
      "Rowan, who rules the Barony of Thornholm, invited Ashford, the player's Town: the letter came to the player, who read it; "
      "Ashford's Realm tab offered Join the Barony of Thornholm or Decline, and the player joined, then put Ashford forward as "
      "capital: Rowan owns the other two villages, so the tab shows a vote for a day, its count and the two buttons", 70,
      [("01_the_letter", "Rowan's letter: an invitation for Ashford"), ("02_the_invitation", "Ashford's Realm tab: Join or Decline"),
       ("03_the_vote", "The vote: move the capital to Ashford?"), ("04_vote_for", "Vote for Ashford, with every village you own")]),
    # Trade pacts and alliances (ROADMAP 33.18): the offer's letter, the Pacts tab with Accept and Decline, the alliance
    S("pacts", "Realm", "A trade pact or alliance with another player's village",
      "Rowan, who owns Thornholm, offered Ashford, the player's Town, an alliance: the letter came to the player, who read it; "
      "Ashford's Pacts tab offered Accept an alliance with Thornholm or Decline, and the player accepted: the tab shows the "
      "alliance, its gifts switch and its End button", 65,
      [("01_the_letter", "Rowan's letter: an alliance offered to Ashford"), ("02_the_offer", "Ashford's Pacts tab: Accept or Decline"),
       ("03_the_alliance", "The alliance with Thornholm, and its End button")]),
    # Feuds and war (ROADMAP 33.20): the feud's letter, the Pacts tab with Offer peace, and with PvP on a guard fighting the rival owner
    S("feud", "Realm", "A feud with another player's village, and war on a PvP server",
      "Rowan, who owns Thornholm, declared a feud on Ashford, the player's Town: the letter came to the player, who read it; "
      "Ashford's Pacts tab shows the feud with Thornholm and its Offer peace button. A day later Rowan raised it to war (PvP "
      "is on): the player walks into Thornholm and its guards, sword and shield, come for them; nothing else may be harmed", 70,
      [("01_the_letter", "Rowan's letter: a feud declared on Ashford"), ("02_the_feud", "Ashford's Pacts tab: the feud and Offer peace"),
       ("08_war", "In the war, Thornholm's guards fight Ashford's owner")]),
    # The Realm Map (ROADMAP 33.21): the Realm tab's map button, the map in hand and hung in an item frame
    S("realm_map", "Realm", "The Realm Map: the realm drawn on a map",
      "the County of Thornholm (Thornholm, Ashford, Farholt; blue arms), allied with Rowan's Brookmere and in a feud with "
      "Grimfell, a colony's settlers on the road and caravan routes: the player clicked the Realm tab's Draw the Realm Map "
      "with an empty map and got the realm on it, blue banners for the members (Thornholm named the capital), a light blue "
      "ally, a red rival, dotted routes and a red cross for the colony on the road; then hung it in an item frame", 60,
      [("01_map_button", "The Realm tab's Draw the Realm Map button"), ("02_map_in_hand", "The Realm Map in hand"),
       ("03_map_in_frame", "The Realm Map in an item frame")]),
    # The weekly leaderboard (ROADMAP 33.19): the chat at Sunday 19:00, the winner's ribbon, the Trade page's toggle
    S("leaderboard", "Realm", "The weekly leaderboard in chat",
      "A week of numbers is in for Ashford (the player's Town), and Rowan's Thornholm and Farholt (a realm); the clock "
      "reaches Sunday 19:00 and the chat reads out the Biggest, Richest, Happiest, Most Legends and Greatest realm, the "
      "top three each with owner and number; Ashford won the Happiest, so its hall's name icon wears the ribbon, and the "
      "Trade page has the toggle that leaves a village off", 65,
      [("01_the_leaderboard", "The week's leaderboard, read out in chat"), ("02_the_ribbon", "Ashford's name icon wears the Happiest ribbon"),
       ("03_the_toggle", "The Trade page's toggle: on the weekly leaderboard")]),
    # The realm treasury (ROADMAP 33.13): the levy from every member, the icon's movements, a gift of emeralds
    S("realm_treasury", "Realm", "The realm treasury: the levy and its movements",
      "Thornholm founded a realm and added Ashford and Farholt, a County; four mornings each member paid the 10% levy on its "
      "takings into the realm treasury, and the Realm tab's treasury icon lists what it holds of 512 emeralds and the movements "
      "by day; a shift-click with twelve emeralds gave them all, and the levy's icon shows 10%", 60,
      [("01_treasury_tooltip", "The treasury: four mornings of the levy"), ("02_after_a_gift", "Twelve emeralds given with a shift-click"),
       ("03_the_levy", "The levy: 10% of each member's takings")]),
    # Shared research (ROADMAP 33.15): the capital's Realm tab with the realm topics; a member's levels from the capital
    S("realm_research", "Realm", "Shared research: the Realm row and levels from the capital",
      "Thornholm, capital of the Barony with Ashford a member, knows Drill II, Swift Hands III and Hearth I; its research "
      "screen has the Realm tab on the bottom row, which lists the eight realm topics: Royal Roads I done and Common Coin "
      "40% along, paid from the realm treasury; the Granaries' tooltip shows its price in paper and treasury emeralds and "
      "what it needs (Concord I, Royal Roads II); Ashford's own research screen shows Drill II from the capital", 60,
      [("01_the_realm_tab", "The capital's research screen: the Realm tab"), ("02_the_realm_row", "The Realm row: the eight realm topics"),
       ("03_a_realm_topic", "A realm topic: its price and what it needs"), ("04_from_the_capital", "Ashford: Drill from the capital")]),
    # Realm edicts (ROADMAP 33.22): the capital's Book of Edicts with the realm's slots; the Royal Highway proclaimed
    S("realm_edicts", "Realm", "Realm edicts: the capital's Book of Edicts",
      "Thornholm, capital of the Barony of Thornholm with Ashford a member, opens its Book of Edicts: the realm's row has "
      "one free realm slot (a Barony's) and three locked ones, and the six realm edicts follow the village's; the Royal "
      "Highway's tooltip shows its boost, its upkeep from the realm treasury, its reform and the count across the realm; "
      "two clicks proclaim it into the realm slot, and Ashford's own book shows it in force from the capital", 60,
      [("01_realm_slots", "The capital's book: the realm's slots"), ("02_royal_highway", "The Royal Highway: boost, cost and reform"),
       ("03_in_force", "Proclaimed: in force in every member"), ("04_a_members_book", "Ashford's book: in force from the capital")]),
    # Relief in a raid (ROADMAP 33.17): zombies raid Ashford; the capital's and Brookfield's riders come on horseback
    S("relief", "Realm", "Relief in a raid: riders from the realm",
      "zombies raid Ashford at night; Thornholm, the capital, and Brookfield, a member with 6 guards, send three riders, who "
      "arrive on horseback at Ashford's edge from their homes' sides, named \"Brookfield's guard\", their shields in the "
      "realm's red, and ride into the raiders (the ride is shortened to 3 seconds for the film)", 40,
      [("01_relief", "The raid on Ashford"), ("05_relief", "The riders arrive"), ("10_relief", "They ride into the zombies"),
       ("15_relief", "The fight")]),
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
    # Legends found in the world (ROADMAP 29.9): a traveler's camp, a prisoner's cage and a castaway's camp; a bar broken frees the prisoner
    S("legend_sites", "Legends", "Legends found at ruins, outposts and wrecks",
      "the three camps of Legends found in the world, and a prisoner freed from the outpost cage", 40,
      [("01_three_camps", "A traveler's camp, a prisoner's cage, a castaway's camp"), ("02_cage_opened", "A bar broken: the prisoner is free")]),
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
    # The Professor's Thesis (ROADMAP 31.21, Cobblemon): the request, a catch counting, the published thesis
    S("professors_thesis", "Village Hall", "The Professor's Thesis: the request, a catch, the published book",
      "the Professor's Thesis: Professor Alder by the hall asking for help with a thesis on Life in the Cold, the chapter's intro in chat; a Snorunt caught in a snowy biome going in the field notes; then the published thesis, the book 'Professor Alder: Life in the Cold' open on its cover page with the player as co-author", 50,
      [("01_request", "The professor's request"), ("02_catch", "A catch counts in the field notes"),
       ("03_published", "The published thesis")], cobblemon=True),
    # The Pokémon Ranger (ROADMAP 29.22, Cobblemon): settled by the hall in a red vest, an Alpha calmed, a wild Pikachu befriended
    S("legend_ranger", "Legends", "The Pokémon Ranger calms an Alpha and befriends a Pikachu",
      "the Pokémon Ranger in a red vest and capture styler by the hall, an Alpha calmed with sparkles, and a wild Pikachu befriended into the owner's pasture", 50,
      [("01_ranger", "The Ranger by the hall"), ("02_alpha_calmed", "A wild level-60 Machamp calmed: sparkles and a chime"),
       ("03_befriended", "A wild Pikachu led into the owner's Pasture Block")], cobblemon=True),
    # The Master Architect (ROADMAP 29.12): the top-tier Stone House, finished, redrawn in the Grand style and rebuilt
    job("legend_architect", "Legends", "The Master Architect redraws a house in the Grand style",
        "the Master Architect handed a builder the Stone House redrawn in the Grand style, and it was rebuilt", 360),
    # Basements (ROADMAP 26.15): a builder digs Starter Cottage IV's root cellar under a finished Cottage III, then the
    # camera goes down into it
    S("basement", "Builder", "Level 4: a root cellar dug under the cottage",
      "a builder dug a root cellar under a finished Starter Cottage III and built the Level 4 over it, then the camera went down the ladder into it",
      420, [START, ("02_root_cellar", "In the root cellar: bins, sacks, a shelf, the lights"), ("02_ladder_up", "The ladder up to the cottage"), DONE]),
    # Cellars (ROADMAP 36.7): the Inn III cut away to its ale and wine cellar; an old Smithy, finished before it had a
    # cellar, gets its coal store dug by its builder ("Dig the cellar")
    S("cellars", "Builder", "Level III cellars, and digging an old build's cellar",
      "the Inn III stood cut away to its ale and wine cellar, and a builder dug the cellar under an old Smithy built before it had one",
      300, [START, ("02_inn_cellar", "The Inn III cut away: its ale and wine cellar"), ("02_smithy_cellar", "The old Smithy's coal store, dug"), DONE]),
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
        "the Beastmaster tamed a war dog for the guard with bones from the chest and fitted it with wolf armor from the scutes; the dog followed its guard and fought the zombies beside them", 90),
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
      "Open Gates was proclaimed: the inn took in two travelers a morning and is full with four guests", 45,
      [("01_open_gates_inn", "Four travelers at the inn after two mornings"), ("02_open_gates_book", "Open Gates in the Book of Edicts")]),
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
    # The Village Banner (ROADMAP 30.13): a street of stone houses under the village's colors, a knight's painted
    # shield, a neighbor by its banner on the routes page, the village by its own in the Book of Edicts
    S("village_banner", "Village Hall", "The Village Banner: the village's colors",
      "three builders hung the colors over their houses' doors, the knight's plain shield came out painted in them, and the routes page and the Book of Edicts showed the villages by their banners", 240,
      [("01_village_banner_street", "A street of finished houses under the village's banners"),
       ("02_village_banner_knight", "A knight with his shield painted in the colors"),
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
      "the player chartered a Master miner, weaponsmith, lumberjack, farmer, shepherd, scholar, nurse, innkeeper and guard, the nine guilds were founded in nine finished Guildhalls, each master's card on the hall's list named their guild (and its pace where it has one), the farmer's farm reached 24 blocks, the shepherd bred up to 12, research cost a quarter less, the ill got well in 2 days, the nurse looked 48 blocks out, a 16-emerald traveler cost 12, guards trained up to Master and hit 10% harder, and the Book of Edicts' guild row showed every guild, five a page with a More guilds button", 40,
      [("01_guilds_miner", "Brokk, Guild Master of the Miners' Guild, working faster through it"),
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
      "Bram's card on the hall's list showed an elder two days from his time, the charm's tooltip said what it does, Dara (an ordinary elder) refused it and the player kept it, Bram (a Master farmer) took it and it was used up, the chronicle said he will never leave us, and after 405 elder days Dara passed in the night and left a grave while Bram stayed, his card wearing the gold leaf badge", 40,
      [("03_ageless_elder_refused", "Dara, an ordinary elder, won't take it: the reason, and the charm kept"),
       ("04_ageless_elder_accepted", "Bram, a Master, takes it: he will never leave us"),
       ("05_ageless_elder_night", "405 elder days on, at nightfall: Dara's grave, and Bram still here"),
       ("06_ageless_elder_badge", "Bram's card now: the gold leaf badge")]),
    # Elders (ROADMAP 34.19): the day Bram becomes one, his card, his talk, and his slower walk beside a young villager
    S("elders", "Village Hall", "Elders: a quiet old age",
      "a day on, Bram had been grown 1200 days and the chronicle said \"Bram is an elder now\" once; his card on the hall's list said \"Elder · grown 1200 days\" with \"a quiet old age\" first in his mood (fed, a bed of his own); he said \"In my day this was all fields.\"; and walking side by side with young Tom he covered about 15% less ground in the same time", 35,
      [("01_elders_card", "Bram's card: an elder, grown 1200 days, with a quiet old age"),
       ("02_elders_talk", "\"In my day this was all fields.\""),
       ("03_elders_walk", "The same walk, side by side: Bram, the elder, falls behind Tom")]),
    # Dressed by class (ROADMAP 34.18): the four classes in three jobs each, and an elder, in daylight
    S("class_outfits", "Village Hall", "Dressed by class: the four classes and an elder",
      "twelve villagers stood in four columns, Peasant, Artisan, Burgher and Noble, each a farmer, a librarian and a toolsmith, every one wearing their class over a job outfit that still reads; the client was told each one's class and stage of life; Bram, an elder Noble, wore gray brows and a beard as well", 25,
      [("01_class_outfits_rows", "Peasant, Artisan, Burgher, Noble (left to right), each a farmer, a librarian and a toolsmith"),
       ("02_class_outfits_elder", "Bram, an elder Noble farmer, beside a grown Noble toolsmith")]),
    # Retirement and apprentices (ROADMAP 34.20): Tom shadows his father Bram, takes over his composter, and Bram mentors Ida
    S("retirement", "Village Hall", "Retirement and apprentices",
      "the hall's round picked Tom, Bram's jobless son, and \"learning from Bram\" showed over his head; two days on Tom had Bram's composter and the farmer's job as a Journeyman (one level below Bram's Expert), Bram was retired and kept his trades, the chronicle noted the retirement and the hand-over, Bram's card on the hall's list said \"Retired Farmer · mentors the young\", and Ida, a Novice farmer, was \"taught by Bram\" and learned 50% faster", 30,
      [("01_retirement_learning", "Tom shadows his father: \"learning from Bram\""),
       ("02_retirement_card", "Two days on: Bram's card, retired"),
       ("03_retirement_taught", "Bram mentors Ida, a Novice farmer: \"taught by Bram\"")]),
    # Family trees (ROADMAP 34.21): three generations, the hall's family names, the Family page and the Printer's tree
    S("family_tree", "Village Hall", "Family trees: three generations",
      "Bram (who has died) and Ilse founded a family; their daughter Dara married Tomas and their baby Mira was the family's third generation, which the chronicle noted; the hall's list gave Dara her family name in brackets (as in \"Dara (Ashby)\") while her own name stayed Dara; Dara's page had a Family button and her Family page showed her parents, Tomas and Mira; Mira's showed her grandparents with Bram marked dead; and Odo the Printer printed the family's tree for 2 emeralds, which opened as a book", 30,
      [("01_family_tree_hall", "The hall's list: Dara with her family name"),
       ("02_family_tree_page", "Dara's Family page: parents, partner, child"),
       ("03_family_tree_grandparents", "Mira's grandparents, Bram marked dead"),
       ("04_family_tree_book", "The Printer's family tree, as a book")]),
    S("tonics", "Village Hall", "Tonics: all six",
      "the miner drank the Miner's Brew she was offered and her status line showed her 25% faster with 19 minutes left; a Toolsmith, a Scholar, an Orchard Keeper and a Lumberjack drank the four new tonics", 40,
      [("01_tonics_offer", "Dara the miner, and Miner's Brew in hand"),
       ("02_tonics_tooltip", "What the brew does, for whom, who makes it"),
       ("04_tonics_status", "Her status line: 25% faster (Miner's Brew, 19 min left)"),
       ("09_tonics_all_six", "A Toolsmith, a Scholar, an Orchard Keeper and a Lumberjack drink theirs")]),
    S("hall_quests", "Village Hall", "Quests, advice, the village map and the festival",
      "the quests, advice, village map, mercenaries and festival opened", 60,
      [("01_hall_quests", "Quests"), ("02_hall_advice", "What next?"), ("03_hall_map", "Village map"),
       ("05_hall_festival", "Festival")]),
    # The waystone network on the hall's Map (ROADMAP 41.2, with Waystones): discovered, not yet, and a broken stone
    S("hall_waystones", "Village Hall", "The waystone network on the hall's Map",
      "the hall's Map listed the waystone network: this village (discovered), one not discovered yet and one whose waystone is broken", 45,
      [("01_waystone_plaza", "Ashford's Waystone Plaza by the hall, its stone named after the village"),
       ("02_hall_waystones", "The Map: the waystone network")], waystones=True),
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
    # What friendship and titles are worth (ROADMAP 31.12): festival gifts from friends, and a Hero's discounted trade screen
    S("festival_gifts", "Village Hall", "Festival gifts from friends, and cheaper trades for a Hero",
      "at the festival Bo the mason and Dara the farmer, with 6 hearts or more, each brought their family's gift (16 stone bricks, 6 apples) and Ana with 3 hearts didn't; then Dara's trade screen for a Hero of the village with ten hearts: 20 emeralds cost 12, 5 cost 3", 45,
      [("01_festival_gifts", "Two friends bring their festival gifts, one each"),
       ("02_discounted_trade", "A Hero with ten hearts: 40% off Dara's trades")]),
    # The bounty board and bandit captains (ROADMAP 31.13): the board's posters, the Wanted Poster, the captain, the claim
    S("bounty", "Village Hall", "The bounty board: posters, a wanted captain and his insignia",
      "a Bounty Board by the hall shows two posters; its Bounties tab shows Grimwald the Red's poster (his quirk, where he was seen, the reward); Ironhide stands in full iron with his band; his insignia is handed in for 15 emeralds", 45,
      [("01_board", "The Bounty Board shows a poster for each open bounty"),
       ("02_poster", "The Bounties tab: the Wanted Poster's tooltip"),
       ("03_captain", "Grimwald the Red, Ironhide, and his band"),
       ("04_claim", "The insignia handed in: paid once")]),
    # Rogue Pokémon bounties (ROADMAP 31.14, Cobblemon): the poster, the huge glowing rogue, the bounty paid for the win
    S("bounty_rogue", "Village Hall", "A rogue Pokémon bounty: the poster, the Rogue Ursaring, the reward",
      "a Town's Bounty Board with a poster on a Rogue Ursaring (level 50, where it was last seen, 24 emeralds); the rogue, half again as big and glowing for the player who took its bounty; beaten, and the bounty paid once", 45,
      [("01_poster", "The Bounties tab: the rogue's poster"),
       ("02_rogue", "The Rogue Ursaring, huge and glowing for its bounty's taker"),
       ("03_claim", "Beaten: the bounty paid")], cobblemon=True),
    # The Bandit King, I: the fort and the King (ROADMAP 31.15): the fort's three stages set down one after another
    S("bandit_fort", "Village Hall", "The Bandit King's fort rising a stage at a time",
      "the Bandit King's fort set down a stage at a time, as his story arc does a stage a dawn: a palisade of sharpened logs round a yard with a fire, tents and a gate, four bandits in it; then two watchtowers with ladders and a stable; then the King's keep with his war banner over the door and the King inside", 45,
      [("01_palisade", "Stage 1: the palisade, the gate, the fire and the tents"),
       ("02_towers", "Stage 2: two watchtowers and a stable"),
       ("03_keep", "Stage 3: the King's keep, his banner over the door")]),
    # The Bandit King, II: the story (ROADMAP 31.16): the banners taken, the fort rising a stage a day, the storm, the King's fall
    S("bandit_king", "Village Hall", "The Bandit King's story: the banners, the fort, the storm, the King's fall",
      "the Bandit King's story arc: three war banners, black with a red skull, flown over his camps and taken; the next dawn his fort rises, a stage a day; at the storm the allies won stand before the gate (3 guards of a neighboring village, 6 mercenaries) and the keep's postern is open, as Pell the deserter told; then the King falls", 60,
      [("01_banners", "Three war banners of the Bandit King"),
       ("05_fort_day_3", "Day 3: the keep, its postern open"),
       ("06_storm", "The storm: 3 guards and 6 mercenaries"),
       ("07_king_falls", "The Bandit King falls")]),
    # The Lost Caravan, I: the wreck and the trail (ROADMAP 31.19): the wreck, a mark of the trail and the stockade
    S("caravan_trail", "Village Hall", "The Lost Caravan's trail: the wreck, a mark and the raiders' stockade",
      "the builds the Lost Caravan's story lays along its trail, each set down the arc's way on fit ground: the settlers' covered wagon on its side with a wheel broken off, crates and barrels spilled and a lantern in the grass; a mark of the trail, a torn white banner on a fence post, with 'The tracks go on to the east' over the hotbar; then the raiders' stockade, a palisade round a tent, a fire, a loot chest and an iron-barred cage", 40,
      [("01_wreck", "The wreck: the settlers' wagon on its side"),
       ("02_mark", "A mark of the trail, the tracks pointing on"),
       ("03_stockade", "The raiders' stockade and its cage")]),
    # The Lost Caravan, II: the story (ROADMAP 31.20): the wreck found, the trail, the carter freed and walked home
    S("lost_caravan", "Village Hall", "The Lost Caravan: the wreck, the trail, freeing Tobin and walking him home",
      "the Lost Caravan's story: the settlers' wagon on its side with 'The caravan's wreck: tracks lead away from the road' over the hotbar; a torn banner on the trail with the tracks pointing on; Tobin the carter in the raiders' cage, its iron gate open and his 'You came for me! Take me home, I'll keep up.' in chat; then Tobin walking after the player out of the stockade", 45,
      [("01_wreck", "The wreck found by the road"),
       ("02_trail", "The trail, the tracks pointing on"),
       ("03_freed", "Tobin in the raiders' cage, the gate open"),
       ("04_home", "Tobin follows the player home")]),
    # The eight reforms as stories (ROADMAP 31.22): The Shift Bell, from the Toolsmith's ask to the bell ringing
    S("reform_story", "Village Hall", "The Shift Bell as a story: the Toolsmith's ask, the three steps, the bell ringing",
      "the Shift Bell told as a story: Long Shifts in force, the Toolsmith by the hall asks for a bell to ring the long shifts in and out, his words in chat; the three steps (24 clocks, 128 gold, 300 bread) handed in a day apart; the ending sets the Shift Bell, a bell under a little slab roof between two dark oak posts on stone brick feet, on the grass beside the hall; at the start of the next work day it rings", 45,
      [("01_ask", "The Toolsmith asks for the Shift Bell"),
       ("02_steps", "The three steps handed in: Long Shifts reformed"),
       ("03_bell", "The Shift Bell beside the hall"),
       ("04_ringing", "The bell rings the work day in")]),
    # The Sickness, I: eight far herbs (ROADMAP 31.17): a herb_patch of each on its own ground, and the Three-Herb Tonic
    S("herbs", "Village Hall", "The eight far herbs, each on its ground, and the Three-Herb Tonic",
      "eight herb patches planted the way a story arc plants them, 5 to 9 each on its own ground: Sunthistle on red sand, Frostbloom on snow, Peakmoss on stone and the pale Glowcap on podzol under leaves in front; Sea Lavender on gravel, Mirewort on mud, Jungle Orchid and Petal Balm on grass behind; then the Three-Herb Tonic, a bottle of green tonic, in hand", 40,
      [("01_patches", "The eight far herbs, a patch of each on its own ground"),
       ("02_tonic", "The Three-Herb Tonic in hand")]),
    # The Sickness, II: the Gray Cough (ROADMAP 31.18): the coughing village, a far herb's patch, the brew, the cure round
    S("sickness", "Village Hall", "The Gray Cough: the coughing village, a far herb, the brew and the cure going round",
      "four of eight villagers coughing gray smoke with the Gray Cough; a patch of Sea Lavender, one of the far herbs, on its gravel; Nurse Ada at her brewing stand with 'Brewing the cure' and 40% over her head; then she takes the Three-Herb Tonic round, villager by villager, hearts over each, until everyone is well", 45,
      [("01_coughing", "Four villagers coughing the Gray Cough, and a patch of Sea Lavender, a far herb"),
       ("02_brewing", "Brewing the cure: the nurse at her stand"),
       ("03_cure_round", "The cure goes round"),
       ("04_all_well", "Everyone well again")]),
    # Heart events (ROADMAP 31.7): at two hearts Dara walks up and tells where she comes from; her life story page.
    # At ten hearts (31.8) she tells what she dreams of and gives her keepsake.
    S("heart_event", "Village Hall", "A heart event: Dara tells where she comes from, and her keepsake",
      "at two hearts Dara walked up to the player, faced them and told where she comes from, line by line over her head and in the chat; it went into the chronicle and onto her life story page; at ten hearts she told what she dreams of and gave her keepsake", 75,
      [("02_telling", "Two hearts: she walks up and tells it, a line every three seconds"), ("03_told", "The last line, each also in the chat in gray"),
       ("04_life_story", "Shift-click her in the hall: her life story"), ("05_keepsake", "Ten hearts: her dream told, and her keepsake in your hand")]),
    # A personal request (ROADMAP 31.9): the ask, the accept, the hand-in, the thanks
    S("personal_request", "Village Hall", "A personal request: Dara asks for a taste of home",
      "at three hearts Dara walked up and asked the player for a taste of home, with [I'll help] and [Not now] in the chat; accepted, the pumpkin pie was handed in from the quest journal's Personal tab, she said her thanks and the helper got the emeralds and the friendship", 60,
      [("01_ask", "Three hearts: she walks up and asks, with [I'll help] and [Not now] in the chat"), ("02_accept", "[I'll help]: the request is open"),
       ("03_hand_in", "The journal's Personal tab: the pie to hand in"), ("04_thanks", "Handed in: her thanks, the emeralds and the friendship")]),
    # A letter to family (ROADMAP 31.10): the letter carried between two villages, and the reply
    S("letter", "Village Hall", "A letter to family: to Mira in Elmford, and the reply home",
      "Dara asked the player to carry a sealed letter to Mira in Elmford, a village 40 blocks away; Mira read it and wrote back, and the reply came home: the request done, paid, and a line in both villages' chronicles", 60,
      [("01_ask", "Three hearts: Dara asks for a letter carried to Mira in Elmford"), ("02_letter", "[I'll help]: the sealed letter in hand"),
       ("03_delivered", "In Elmford: Mira reads it and writes back"), ("04_reply", "Home again: the reply to Dara, and her thanks")]),
    # Reputation and titles (ROADMAP 31.11): a new title told to the server, the title in chat, the hall's tooltip
    S("titles", "Village Hall", "Reputation and titles: a Hero of Thornholm",
      "the player's deeds made them a Hero of Thornholm, told to the whole server in gold; their next chat line carries [Hero of Thornholm], and the hall's name tag tooltip shows their standing, their honor and the three best regarded", 45,
      [("01_hero", "300 standing: a Hero of Thornholm, told to the whole server"), ("02_chat", "A chat line with the title before it"),
       ("03_tooltip", "The hall's name tag: your standing, your honors and the best regarded")]),
    # My work (ROADMAP 31.8): the 4-heart event of each job family, a villager of each beginning theirs
    S("heart_events_work", "Village Hall", "Heart events: the work story of every job family",
      "at four hearts a villager of each job family (and one without a trade) began the story of their work, over their head and in the chat", 45,
      [("[0-9][0-9]_*@spread", "")]),
    S("story_arc", "Village Hall", "A story arc: its chapters told, the Story tab and the chronicle",
      "a story arc began (its chapters told in chat), the Story tab showed the first chapter ticked and the second with its quest, and the chronicle kept them", 50,
      [("01_story_announced", "The chapters told in chat as they begin"), ("02_story_tab", "The Story tab: chapter 1 ticked, chapter 2 running with its quest"),
       ("03_story_chronicle", "The chronicle's story lines")]),
    # Classes at the Village Hall (ROADMAP 34.6): a household rises at dawn, then the hall's Classes tab and page
    S("classes", "Village Hall", "Classes: a household rises, and the Classes page",
      "a Peasant couple rose to Artisan at dawn with golden sparkles and a chat line, and the hall's Classes page counts each class's needs", 45,
      [("01_classes_rise", "Odo and Pia rise to Artisan at dawn"), ("02_classes_tab", "The Classes tab: households of each class"),
       ("03_classes_page", "The Burgher button: needs counted, what it gives, who's closest")]),
    # The Wonders page and the Wonder Plan (ROADMAP 35.3): the folly's card, then the plan placed with its see-through tower
    S("wonders_page", "Wonders", "The Wonders page and the Wonder Plan",
      "the hall's Wonders page showed the Test Folly's card with every requirement ticked and the cap; Draw up the plans took 32 emeralds and gave a Wonder Plan, which placed north of the hall shows the whole tower as see-through blocks", 30,
      [("01_wonders_page", "The Wonders page: the folly's card, every line ticked, the cap"), ("02_wonder_plan_preview", "The Wonder Plan in hand: the whole tower see-through")]),
    # The Foundation Stone (ROADMAP 35.4): the folly's plan handed to the builder, the stone going down, stage I starting
    S("wonder_site", "Wonders", "The Foundation Stone: a Wonder begins",
      "a Wonder Plan placed north of the hall was handed to the village's builder: the Foundation Stone (carved stone, brass plaque) went down at the folly's front corner, the start was announced in chat, and stage I, the Ground Storey, began from the chests by the stone; right-clicking the stone told the stage, its progress and its material list", 40,
      [("01_wonder_site_plan", "The plan placed: the whole folly see-through, the builder at his bench"), ("02_wonder_site_stone", "Handed over: the Foundation Stone at the front corner, the chests beside it"),
       ("03_wonder_site_status", "Stage I under way, and the stone's status in chat")]),
    # Crews (ROADMAP 35.5): ten builders on the folly, each on a column of its own
    S("wonder_crew", "Wonders", "Crews: every builder on one Wonder",
      "ten builders of the village raised the folly together: the plan handed to one, every builder called from the stone, and each took a column of its own, never more than two layers above the lowest unfinished one; the stone named the lead and the crew of ten", 60,
      [("01_wonder_crew_gathering", "Every builder called: the crew gathers at the folly"), ("02_wonder_crew_columns", "Ten builders, each on a column of its own"),
       ("03_wonder_crew_status", "The stone names the lead and the crew of ten")]),
    # A Wonder's goods (ROADMAP 35.7): the stone's lines half filled, the porter bringing the books, then the dedication
    S("wonder_goods", "Wonders", "A Wonder's goods and its dedication",
      "the folly's stage II waited for its three lines: the stone's screen showed the flowers and the potions in and the books short, \"no Librarian in the village\"; the porter carried the two books from the storehouse to the stockpile, and the stage was dedicated: the bell rang, the villagers off work gathered at the stone, the Bard played beside it, the guards stood in line, the goods were taken and the chronicle said so", 55,
      [("01_wonder_goods_screen", "The stone's screen: flowers and potions in, the books short"), ("02_wonder_goods_porter", "The porter carries the books to the stockpile"),
       ("03_wonder_goods_dedication", "The dedication: the village gathers at the stone, the Bard plays, the guards in line")]),
    # A Wonder's found goods (ROADMAP 35.7a, 35.7b): the stone asks for things found by exploring; a rare worker finds one
    S("wonder_finds", "Wonders", "A Wonder's found goods, and a rare worker who finds one",
      "the Great Library's stage I asked for things found by exploring, not stacks: the stone showed the Heart Pottery Sherd in, the Pathfinder searching for the Dune Armor Trim Template (day 5 of 9) and the Illuminated Book short; then the Pathfinder brought the template, chat and the chronicle said so, and the stone showed it brought by him", 30,
      [("01_wonder_finds_screen", "The stone: the sherd in, the Pathfinder searching (day 5 of 9), the book short"),
       ("02_wonder_finds_brought", "The Pathfinder brings the template: chat says so, the stone shows it brought")]),
    # A finished Wonder (ROADMAP 35.8): the folly's time-lapse with a crew of ten, ending in its festival's bigger fireworks
    S("wonder_finished", "Wonders", "A finished Wonder: the folly's time-lapse and its festival",
      "a crew of ten raised the folly stage by stage on a sped-up server, each stage's goods handed in at the stone; once finished every player was told, its mood and beauty powers came on, and that evening's festival had the bigger show, gold rockets going up from the folly's top", 600,
      [("11_wonder_finished_stage_1", "Stage I, the Ground Storey, up"), ("frame_*@middle", "The crew at work, the camera going round"),
       ("13_wonder_finished_stage_3", "The Dome on: the folly finished"), ("19_wonder_finished_fireworks", "The festival's fireworks over the folly")]),
    # The Stadium (ROADMAP 35.24, with Cobblemon): its time-lapse, then a Stadium Day with full stands and a battle with a visitor
    S("wonder_stadium", "Wonders", "The Stadium: its time-lapse, and a Stadium Day",
      "a crew of ten raised the Stadium stage by stage round its field on a sped-up server; once finished its powers came on (Stadium Days every second afternoon, the Festival Cup held here) and after the festival's fireworks came the next Stadium Day: visiting trainers stood in the two trainer boxes, the village's folk off work filled the stands, the village's trainer fought a visitor in a bout settled by rank and team (in chat and the chronicle), and the player walked onto the field and challenged a visitor to a real battle", 900,
      [("11_wonder_stadium_stage_1", "Stage I, the Field, laid"), ("14_wonder_stadium_stage_4", "The Gate and the Floodlights: the Stadium finished"),
       ("21_wonder_stadium_stands", "A Stadium Day: visitors in the boxes, the stands full"),
       ("22_wonder_stadium_battle", "The player's battle against a visiting trainer")], cobblemon=True),
    # The Lighthouse (ROADMAP 35.12): its time-lapse beside a sea the harness digs, ending at night with the lamps lit
    S("wonder_lighthouse", "Wonders", "The Lighthouse: its time-lapse by the sea, lit at night",
      "a crew of ten raised the Lighthouse stage by stage beside a sea the harness dug and filled, the jetty running out over the water; once finished its powers came on (caravans twice as far in half the time, ferry fares halved, a day's warning of pirates) and the jetty's Travel Post joined the network under the village's name; after the festival's fireworks, at midnight the lantern's redstone lamps shone over the sea", 780,
      [("11_wonder_lighthouse_stage_1", "Stage I, the Mole and the Quay, by the sea"),
       ("14_wonder_lighthouse_stage_4", "The Lantern and the Keeper's House: finished"), ("19_wonder_lighthouse_fireworks", "The festival's fireworks over the tower"),
       ("20_wonder_lighthouse_night", "Midnight: the lamps lit over the sea")]),
    # The Hanging Gardens (ROADMAP 35.20): its time-lapse, ending the next morning with the falls running and farmers on the terraces
    S("wonder_hanging_gardens", "Wonders", "The Hanging Gardens: its time-lapse, and farmers on its terraces",
      "a crew of ten raised the Hanging Gardens terrace by terrace on a sped-up server, each stage's goods handed in at the stone; once finished its powers came on (a quarter more harvest across the realm, babies on 12 meals and two a day), and after the festival's fireworks, the next morning the falls ran from the crown's cistern into the pool and a farmer at each of the four farm corners took on that terrace's fields by themselves", 860,
      [("11_wonder_hanging_gardens_stage_1", "Stage I, the First Terrace and the Cistern Vaults, up"), ("frame_*@middle", "The crew at work, the camera going round"),
       ("14_wonder_hanging_gardens_stage_4", "The Crown Garden and the Falls: the Gardens finished"),
       ("20_wonder_hanging_gardens_terraces", "The next morning: the falls running, farmers on the terraces")]),
    # The Great Library (ROADMAP 35.14): its time-lapse, ending in the rotunda on the scholar's screen with the second tier
    S("wonder_great_library", "Wonders", "The Great Library: its time-lapse, and the second research tier",
      "a crew of ten raised the Great Library stage by stage on a sped-up server, each stage's goods handed in at the stone; once finished its power came on and, after the festival's fireworks, inside the rotunda by the enchanting table a scholar's research screen showed the Great Library's row open: Swift Hands IV-V, Scholarship, Engineering, Cartography, Trade Law and Pharmacy", 840,
      [("11_wonder_great_library_stage_1", "Stage I, the Stacks, up"), ("frame_*@middle", "The crew at work, the camera going round"),
       ("14_wonder_great_library_stage_4", "The Dome and the Portico: the Library finished"),
       ("20_wonder_great_library_research", "In the rotunda: the scholar's screen with the Great Library's row open")]),
    # The Great Cathedral (ROADMAP 35.10): its time-lapse with the crew, ending at dusk with a blessed wedding at its altar
    S("wonder_great_cathedral", "Wonders", "The Great Cathedral: its time-lapse, and a wedding at its altar",
      "a crew of ten raised the Great Cathedral stage by stage on a sped-up server, each stage's goods handed in at the stone; once finished its powers came on (Undertakers free and village-wide, blessed weddings, 8 beauty), and at dusk a couple were wed at its altar before the guests: the wedding was blessed and its bells rang", 840,
      [("11_wonder_great_cathedral_stage_1", "Stage I, the Foundations and the West Front, up"), ("frame_*@middle", "The crew at work, the camera going round"),
       ("14_wonder_great_cathedral_stage_4", "The Towers and the Rose Window: the Cathedral finished"), ("19_wonder_great_cathedral_wedding", "Dusk: a blessed wedding at the altar")]),
    # The Colossus (ROADMAP 35.22): its time-lapse with the scaffolding going up and coming down, ending at night from the road
    S("wonder_colossus", "Wonders", "The Colossus: its time-lapse, and the lantern lit over the road",
      "a crew of ten raised the Founder's statue stage by stage on a sped-up server, the scaffolding going up beside the robe, the raised arm and the head and coming down as each stage was done; once finished its powers came on (the realm 8 happier and its own village 12, travelers twice as often and twice as likely Experts, 8 beauty) and the plaque over the road arch was written with the village's name and its founding day; after the festival's fireworks, at midnight the lantern shone over the road", 840,
      [("11_wonder_colossus_stage_1", "Stage I, the Plinth and the Road Arch, up"), ("frame_*@middle", "The crew at work on its scaffolding, the camera going round"),
       ("14_wonder_colossus_stage_4", "The Head and the Lantern: the Colossus finished"), ("20_wonder_colossus_night", "Midnight from the road: the lantern lit over the arch")]),
    # The Great Wall (ROADMAP 35.16): its time-lapse, ending at midnight with a raid gathering on the open side while its sentries watch
    S("wonder_great_wall", "Wonders", "The Great Wall: its time-lapse, and a night raid kept to the open side",
      "a crew of ten raised the Great Wall stage by stage on a sped-up server, the Great Gate first, each stage's goods handed in at the stone; once finished its four iron golem sentries took up their posts in the towers' alcoves and its powers came on; after the festival's fireworks, at midnight a raid set out for the village and gathered on its open side, outside the arc the Wall covers from the hall, while the sentries stepped out onto the walk to watch it and the Great Gate stood shut", 840,
      [("11_wonder_great_wall_stage_1", "Stage I, the Great Gate, up"), ("frame_*@middle", "The crew at work, the camera going round"),
       ("14_wonder_great_wall_stage_4", "The Walk and the Towers' Crowns: the Wall finished"), ("20_wonder_great_wall_night", "Midnight: the raid gathers on the open side, the sentries on the walk")]),
    # The Observatory (ROADMAP 35.18): its time-lapse, then the Sky Chart in hand and the Coming days page
    S("wonder_observatory", "Wonders", "The Observatory: its time-lapse, the Sky Chart and the Coming days",
      "a crew of twelve raised the Observatory stage by stage on a sped-up server, the terrace and courtyard first, then the tower, the copper dome and the great telescope with the orrery; once finished its powers came on and, after the festival's fireworks, the hall's search charted the land round the village: the Sky Chart in hand shows about 6,144 blocks with a marker on every structure found, each named in its tooltip; then the hall's calendar page showed the Coming days, a festival in three days, a wedding tomorrow, a caravan on the road, the market day, the full moon and the night's raid risk", 840,
      [("11_wonder_observatory_stage_1", "Stage I, the Terrace and the Courtyard, up"),
       ("14_wonder_observatory_stage_4", "The Great Telescope and the Orrery: the Observatory finished"), ("20_wonder_observatory_sky_chart", "The Sky Chart in hand"),
       ("21_wonder_observatory_coming_days", "The hall's calendar with the Coming days")]),
    # Switching classes on (ROADMAP 34.22): a village from before 1.8 finds its places; the hall's pages before and after
    S("class_switch_on", "Village Hall", "Switching classes on: a village finds its places",
      "a village saved before 1.8 found its places when classes were switched on: before, with classes still off, the hall's Classes tab had no one to count; the hall's rounds seeded everyone (the couple in a builder's cottage Artisans, the others Peasants), the chronicle said so once, and the tab and the Classes page said \"Settling in: 7 days left\" with each class's luxuries to come", 30,
      [("01_class_switch_on_before", "Before: the Classes tab with classes still off"),
       ("02_class_switch_on_tab", "After: the tab counts each class, settling in for 7 days"),
       ("03_class_switch_on_page", "The Classes page's clock: what each class will need")]),
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
    # The Defense page (ROADMAP 32.3): the guards icon opens it; the lair, its named captain, its strength, the last attacks
    S("defence_page", "Village Hall", "The Defense page: the bandit camp and its chief",
      "the hall's guards icon opened the Defense page: the bandit camp, its named chief, its strength and the last attacks", 45,
      [("01_defence_page", "The camp's chief, by name"), ("02_defence_strength", "The camp's strength"),
       ("03_defence_attacks", "The last attacks")]),
    # Raid dens (ROADMAP 38.2, Cobblemon Raid Dens): the dens a village knows, on the Defense page
    S("raid_dens", "Village Hall", "Raid dens: the village knows the dens around it",
      "a raid den crystal glows by the village and another rests up the valley; the hall's Defense page lists both with their tier, type and state", 45,
      [("01_raid_den", "A raid den by the hall"), ("02_raid_den_page", "The Defense page: tier 3, Fire, active"),
       ("03_raid_den_resting", "A beaten den, resting")], cobblemon=True),
    # Raid helpers (ROADMAP 38.1a, Cobblemon Raid Dens): the player hires the village's trainers to help in a den's raid
    S("den_helpers", "Village Hall", "Raid helpers: hire the village's trainers for a raid",
      "two of the village's trainers, hired on the hall's Defence page, stand by the glowing raid den as the player's raid starts; each cheered them on with one of the raid's cheer items", 45,
      [("01_den_helpers_page", "The Defence page: hire a raid helper by the den"), ("02_den_helpers_raid", "The helpers by the den as the raid starts")], cobblemon=True),
    # At peace (ROADMAP 32.21): the owner sets the village at peace on the Defense page; the camp leaves at the next dawn
    S("at_peace", "Village Hall", "At peace: no raids, camps or disasters",
      "the hall's owner set the village at peace on the Defense page: no raids, camps or disasters come, and the bandit camp leaves at the next dawn", 45,
      [("01_at_peace", "The Defense page: At peace"), ("02_at_peace_guards", "The guards icon says so")]),
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
    S("workplaces", "Build families", "A workplace for every worker: 12 builds, each with its own look (26.11)",
      "every workplace was placed", 90, [("30_*@spread", "")]),
    S("decor", "Build families", "Decorations", "every decoration was placed", 120, [("30_*@spread", "")]),
    # Sieges I (ROADMAP 32.4): a ravager ram breaks a Palisade Gate, the cracks showing
    job("siege_gate", "Threats", "A siege: the ram at the gate",
        "the ravager ram broke a gate block of the Palisade Gate, the cracks showing, and turned on the next", 120),
    # Pillager warbands (ROADMAP 32.7): the war camp and its Warlord, and the warband's siege on a palisaded Town
    job("warband", "Threats", "A pillager warband: the war camp and its siege",
        "a pillager warband made its war camp south of the Town and laid siege to its Palisade Gate: the ravager ram broke a gate block", 150),
    # Drowned pirates (ROADMAP 32.8): the Pirate Ship at anchor at dusk, the crew wading ashore, the ship sinking
    job("pirates", "Threats", "Drowned pirates: the ship, the landing, the sinking",
        "the pirate ship anchored off the Town at dusk, its drowned crew waded ashore, and when its captain fell it burned and sank, leaving its chest on a raft", 150),
    # Village fire (ROADMAP 32.16): a roof catching, and the Firewarden putting it out with buckets of water
    job("fire", "Threats", "Village fire: the roof catches, the Firewarden puts it out",
        "the Starter Cottage's roof caught fire and the Firewarden ran over from the grindstone and put it out with buckets of water", 150),
    # Threats spare the builders' work (ROADMAP 32.16a): a raider's creeper blows up beside a finished cottage
    job("threat_spared", "Threats", "A raider's blast leaves the builders' work standing",
        "a raider's creeper blew up right beside the Starter Cottage: the dirt mound a player had left there went, every block the builders placed still stood", 120),
    # Drought (ROADMAP 32.17): dry fields in a drought, and the Well Keeper carrying water to them from the pond
    job("drought", "Threats", "Drought: the dry fields and the Well Keeper's buckets",
        "the fields were dry in the drought, and the Well Keeper carried water from the pond to them, bucket after bucket", 150),
    # Plague (ROADMAP 32.18): the Physician going house to house with Remedies, then the hall's list of the ill
    job("plague", "Threats", "Plague: the Physician goes house to house with the Remedy",
        "a plague broke out; the Physician took Remedies from the chest and went house to house: two of the ill were cured, the third was seen and sent home to bed, as the hall's list showed", 150),
    # The Mechanic (ROADMAP 39.2, with Create): mending a finished drive line from its blueprint and tending a millstone
    job("mechanic", "Mechanic", "The Mechanic: a shaft and a cogwheel put back, the millstone fed and emptied",
        "the Mechanic put back the shaft and the cogwheel missing from the finished drive line, fed the turning millstone the store's wheat and brought its flour back to the chest",
        150, create=True),
    # Villagers work Create's machines (ROADMAP 39.3, with Create): the farmer feeds a turning millstone from the store
    job("create_mill", "Create", "The farmer works the millstone: wheat in, flour back to the store",
        "the Farmer took wheat from the chest to the turning millstone and brought the flour it ground back to the chest",
        150, create=True),
    # The Create-powered builds (ROADMAP 39.4, with Create): the Watermill, the Windmill and the Workshop, every machine turning
    job("create_builds", "Create", "The Watermill, the Windmill and the Workshop, their machines turning",
        "the Watermill's wheel, the Windmill's sails and the Workshop's wheel and belt all turned, and with them both millstones and the press",
        120, create=True),
    # More Create builds (ROADMAP 39.8, with Create): the Brass Foundry, Sawmill, Crushing Mill, Greenhouse, Clock Tower and Pump House, every machine turning
    # A waystone by every Village Hall (ROADMAP 41.1): with Waystones, the builder raises the Waystone Plaza by the hall
    job("village_waystone", "Builder", "A waystone by the Village Hall",
        "the builder raised the Waystone Plaza by the Village Hall, and its stone became a working waystone named after the village",
        180, waystones=True),
    # Caravans travel by waystone (ROADMAP 41.3): with Waystones, a caravan walks to its village's stone and steps out at the other's
    job("waystone_caravan", "Realm", "Caravans travel by waystone",
        "Thornholm's carter led his two llamas from the Storehouse to the village's waystone and stepped into it; "
        "then Ashford's caravan stepped out of the same stone, unloaded its logs at the Storehouse and walked back into the waystone",
        170, waystones=True),
    job("create_builds_more", "Create", "Six more Create builds, their machines turning",
        "the six builds' wheels turned, and with them the Brass Foundry's mixer, the Sawmill's saws, the Crushing Mill's wheels, the Greenhouse's belt, the Clock Tower's cuckoo clock and the Pump House's pump",
        120, create=True),
    # Trains link villages (ROADMAP 39.7, 39.7a, 39.7b and 39.7d, with Create): two Train Stations, track between them, the train built by a builder, its Conductor in the seat calling the stop, and the caravan's goods in its chest
    job("village_train", "Create", "Trains link villages: the builder's train, its Conductor, the goods in its chest",
        "Thornholm's builder built the train, its Conductor took the seat, and the caravan's 32 oak logs filled its chest at Thornholm and came out of it at Ashford",
        240, create=True),
    # Builders build contraptions (ROADMAP 39.5, with Create): a saved drive put up again, its moving parts last, then checked
    job("create_contraption", "Create", "A contraption from a blueprint: built in order, and it turns",
        "the builder put up the saved contraption, its shafts, cogwheels and belt last, and checked it: every part turned",
        200, create=True),
    # Create schematics at the Blueprint Table (ROADMAP 39.6, with Create): an uploaded schematic imported by its written Schematic, then built
    job("create_schematic", "Create", "A Create schematic at the Blueprint Table, built",
        "the written Schematic used on the Blueprint Table imported its uploaded file, and the builder put the schematic's kiosk up",
        150, create=True),
    # Desert raiders (ROADMAP 32.9): the Tomb Camp, a sand ramp rising against a Stone Wall, a husk getting up again
    job("desert_raiders", "Threats", "Desert raiders: the tomb camp, the sand ramp, the Pharaoh's curse",
        "the desert raiders' tomb camp stood west of the Town; a husk piled a sand ramp against the Stone Wall and climbed over, and a husk that fell near the Pharaoh got up again", 150),
    # Piglin incursions (ROADMAP 32.10): the Blackstone Outpost, piglins through its portal, one carrying off gold
    job("piglins", "Threats", "Piglin incursions: the outpost, the portal, the plunder",
        "the piglins' blackstone outpost stood west of the Town; a raid stepped out of its portal and a piglin passing the village's chests carried off a stack of gold", 150),
    # Scouts (ROADMAP 32.12): the scout on a rise with the spyglass, then the Scouting Report map in hand
    job("scouts", "Threats", "Scouts: a look at the lair, a map home",
        "the scout walked up the rise, raised the spyglass at the bandit camp and came home with the Scouting Report; the player held its map, the camp marked with a red X", 150,
        [("04_report_in_hand", "The Scouting Report in hand")]),
    # Marching on the lair (ROADMAP 32.13): the war party and the Siege-breakers into the war camp, the Warlord falling, the loot
    job("war_party", "Threats", "Marching on the lair: the war party, the Warlord, the loot",
        "the player led three guards and the five Siege-breakers into the pillagers' war camp; the band turned out to meet them, the Warlord fell at the front, and the camp's loot chest was theirs", 150,
        [("04_loot", "The war camp's loot chest")]),
    # A broken camp comes down (B209): the chief falls, the village forgets the camp, its tents and fire come down;
    # the grassy rise it levelled comes back as it was (B225)
    job("camp_cleared", "Threats", "A broken bandit camp comes down, the land back as it was",
        "the bandit chief fell; his camp was forgotten by the village at once and came down top first, its loot spilling out, and the grassy rise it had levelled came back as it was", 90),
    # Witch covens (ROADMAP 32.11): the Coven Hut in the swamp at night, a hex, witches throwing potions over a palisade
    job("coven", "Threats", "Witch covens: the hut, the hex, potions over the wall",
        "the witches' coven hut stood in the swamp north of the village at night; villagers were hexed in a swirl of witch-light, and the witches stood off the palisade throwing their potions over it", 150),
    # Sieges II (ROADMAP 32.5): pillagers ladder a Stone Wall, a guard throws the ladder down
    job("siege_ladders", "Threats", "A siege: ladders over the wall",
        "pillagers set a ladder against the Stone Wall and one climbed over; the guard on the walkway threw the ladder down", 120),
    # Sieges III (ROADMAP 32.6): archers on battle stations shoot down at a siege
    job("warning", "Threats", "A day's warning",
        "the archer posted by the Wall Tower saw the raid coming and the chat said so; at noon the gates shut and the portcullis dropped, and at dusk the archer was on the tower before any raider came", 150),
    job("battle_stations", "Threats", "A siege: battle stations",
        "the two archers took battle stations on the Gatehouse and the Wall Tower and shot down at the raiders coming at the portcullis", 150),
    # With the answering jobs' houses since 32.19 (the Fire Station, the Well House and the Physician's House) and the
    # defense upgrades since 32.20 (Palisade II, Stone Wall II, Wall Tower II, Gatehouse II, each beside its base)
    S("defences", "Build families", "Walls and gates with their upgrades, and the answering jobs' houses",
      "every wall and gate was placed beside its upgrade (Palisade II's stake ditch, Stone Wall II's hoardings, Wall Tower II's roofed gallery, Gatehouse II's barbican), then the Fire Station, the Well House and the Physician's House", 120, [("30_*@spread", "")]),
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
    # The four luxury workshops (ROADMAP 34.13, 34.14): both tiers of each, then the four as villages grow them
    S("luxury_workshops", "Build families", "The Winery, the Tailor's Shop, the Print Shop and the Jeweler's Workshop, both tiers, and in a village",
      "the Winery, the Tailor's Shop, the Print Shop and the Jeweler's Workshop stand in both tiers with their job blocks, and the village's four came with their workers",
      150, [("10_winery", "The Winery: the stair up to the press room, the cellar door under it"),
           ("16_tailors_shop_2", "Tailor's Shop II: the cutting room and the drying loft"),
           ("18_print_shop", "The Print Shop: brick and timber, the skylight over the press"),
           ("25_jewellers_workshop_2_back", "Jeweler's Workshop II: the strong room behind")]),
    # Grander homes (ROADMAP 34.15): the Artisan's House and the Burgher's Townhouse, every tier, then in the five styles
    # 34.16 adds the Noble's Manor (I, II, III), and the Manor III in the five styles
    S("grand_houses", "Build families", "The Artisan's House (I, II), the Burgher's Townhouse (I, II, III) and the Noble's Manor (I, II, III), and all three in the five styles",
      "the Artisan's House (I, II), the Townhouse (I, II, III) and the Manor (I, II, III) stand with their beds and grades, all three in the five styles, the Ball's spot on each ballroom floor",
      200, [("14_townhouse", "The Burgher's Townhouse: a brick shop below, two jettied timber stories, a slate gable"),
           ("20_manor", "The Noble's Manor: a stone house round a hall with a grand stair, slate roof and dormers"),
           ("23_manor_2_back", "Manor II: the walled formal garden, its hedges, fountain and gravel walks"),
           ("24_manor_3", "Manor III: the gatehouse on the street and the ballroom wing")]),
    # B304: in the Terrace (two homes, each bedroom up a ladder) a villager without a bed takes a free one next door
    S("terrace_sleep", "Build families", "A free bed up the Terrace's other ladder: claimed, climbed to and slept in",
      "the villager without a bed claimed a free bed of the Terrace's west home, climbed its ladder and slept in it; the neighbours slept in the east home", 90,
      [("01_dusk", "Dusk: the east home's beds are the neighbours', a villager without a bed by its door"),
       ("02_ladder", "He has claimed a free bed next door and climbs the west home's ladder"),
       ("03_asleep", "Asleep in the west home's bedroom")]),
    # Furnished homes (ROADMAP 37.4): Starter Cottage II as a server with the pack's furniture mods builds it, room by room
    # B260: and the owner's Stone House, its way in clear and its attic a bedroom
    # B281: and the Terrace's two kitchens. Four stills on the card (the selftest's 2-4, B272): the owner's B260 and B281
    # rooms; the harness still films the cottage's 02_dining, 03_bedroom, 05_sitting and 06_vanilla_dining
    # B679: the card shows four stills (the selftest's limit); the harness still films 08_stone_attic and 09_terrace_west
    S("furnished_home", "Build families", "Furnished homes: the Stone House's doorway and attic bedroom, the Terrace's two lit kitchens and the Clinic's ward (Handcrafted, Beautify, CobbleFurnies)",
      "every furniture spot of Starter Cottage II holds the piece this server chose, pieces of at least two furniture mods; nothing stands in the Stone House's way in and none of its pieces floats; both Terrace kitchens hold modded pieces; the Clinic's ward has four whole beds and nothing open under its ridges",
      110, [("07_stone_doorway", "The Stone House from inside: a clear way from the door to the ladder, the table by the west window, the clock by the bed"),
           ("10_terrace_east", "The Terrace's east kitchen: its own mods' table (against the party wall) and chair, and its own lantern"),
           ("11_clinic_ward", "The Clinic's ward: four white beds, heads to the back wall (B639: no more cots of slab and carpet)"),
           ("12_clinic_ceiling", "The Clinic's ward ceiling: closed where the two stair runs meet at the ridge (B639)")],
      cobblemon=True, furniture=True),
    # Any mod's spruce chair will do (ROADMAP 37.13): the Builder's chairs follow the chest, Handcrafted's or CobbleFurnies'
    S("furniture_any_mod", "Build families", "Any mod's spruce chair will do: the Builder uses the furniture in its chest",
      "two Starter Cottage IIs planned with Handcrafted's chairs: the one whose chest held only CobbleFurnies' chairs has CobbleFurnies' chairs in the same places and facing, every other piece as planned",
      60, [("01_chests", "The Builder's Bench and a chest holding only CobbleFurnies' chairs"),
           ("02_handcrafted_dining", "The cottage whose chest held Handcrafted's chairs: Handcrafted chairs at the table"),
           ("03_cobblefurnies_dining", "The cottage whose chest held CobbleFurnies' chairs: CobbleFurnies chairs in the same places"),
           ("04_both", "The two cottages side by side")], cobblemon=True, furniture=True),
    # Furnished workplaces (ROADMAP 37.5): the Supply Shop, Library, Schoolhouse and Healing Center as a server with the
    # pack's furniture mods builds them
    S("furnished_workplaces", "Build families", "Furnished workplaces: shop counters, Library desks, school benches (Handcrafted, Beautify, CobbleFurnies)",
      "every furniture spot of the four workplaces holds the piece this server chose, pieces of at least two mods, and each keeps only its own chests and barrels as storage",
      80, [("01_shop_counter", "Inside the shop: Handcrafted counters either side of the Shop Counter, a shelf of cups, a picture, hanging pots"),
           ("02_library_desks", "The Library's reading desks and chairs under hanging pots, the scholar's lectern beyond"),
           ("03_schoolroom", "The schoolroom's benches facing the blackboard and the teacher's Poké Ball desk"),
           ("04_clinic_benches", "The Healing Center's waiting benches, pictures and the nurse's Poké Ball desk")], cobblemon=True, furniture=True),
    # Villagers use the furniture (ROADMAP 37.6): Handcrafted chairs at supper, Handcrafted fancy beds at bedtime
    S("seats_and_beds", "Build families", "Villagers sit on Handcrafted chairs at supper and sleep in its fancy beds",
      "two villagers sit on the furniture mod's chairs at supper, then get up at bedtime and sleep in its fancy beds",
      60, [("01_supper", "Supper: two villagers seated on Handcrafted chairs at the table"),
           ("02_bedtime", "Bedtime: the same two asleep in Handcrafted fancy beds")], cobblemon=True, furniture=True),
    # Carved Wood and Rechiseled in the styles (ROADMAP 37.7): the Library in Spruce, the Starter Cottage in Grand
    S("carved_styles", "Build families", "The Spruce style's Carved Wood shelves and lectern, and the Grand style's Rechiseled floors",
      "the spruce Library's bookshelves are all Carved Wood's and its lectern a Carved Wood lectern that is a Librarian's job site, and the Grand cottage stands with Rechiseled blocks",
      40, [("01_library_front", "The Library in the Spruce style"),
           ("02_library_shelves", "Inside: Carved Wood spruce bookshelves round the spruce lectern"),
           ("03_grand_cottage", "The Starter Cottage in the Grand style"),
           ("04_grand_cottage_floor", "Inside the Grand cottage: Rechiseled dark oak flooring and andesite wall panels")], cobblemon=True, furniture=True),
    # The two build libraries (ROADMAP 37.12): the Starter Cottage II from the vanilla and the modded library, the modded Inn II
    S("modded_library", "Build families", "The modded build library: the same cottage in vanilla blocks and dressed with the pack's building mods",
      "the vanilla Starter Cottage II stands beside its modded copy (daub walls with braces, herringbone floors, Carved Wood chests, crockery on the table) and the modded Inn II, built from the modded library because its mods are installed",
      40, [("01_both_cottages", "The Starter Cottage II from the vanilla library (left) and the modded library (right)"),
           ("02_modded_cottage", "The modded cottage: Supplementaries daub for the plaster, braced beside the posts"),
           ("04_modded_cottage_inside", "Inside: Chipped herringbone floors, Handcrafted furniture and crockery, Carved Wood chests"),
           ("05_modded_inn", "The Inn II from the modded library")], cobblemon=True, furniture=True),
    # The storage builds (ROADMAP 37.17, with Tom's Simple Storage): the Storage Hall, the Terminal Nook and the Warehouse
    # built of Tom's blocks, and the vanilla Storage Hall beside them
    S("storage_hall", "Porter", "Storage builds with Tom's Simple Storage: the Storage Hall, the Terminal Nook and the Warehouse",
      "the three storage builds stand built of Tom's Simple Storage, their inventory connectors joining 29, 6 and 60 chests, barrels and cabinets, and the vanilla Storage Hall beside them has none of Tom's blocks",
      50, [("01_halls", "The Storage Hall without Tom's (left) and with it (right)"),
           ("02_hall_counter", "The counter by the door: a storage terminal and a crafting terminal either side of the Storehouse"),
           ("04_nook_inside", "The Terminal Nook: six chests round the connector, the terminal before it"),
           ("06_warehouse_inside", "The Warehouse: racks of chests and barrels on Inventory Trim, the crafting terminal at the counter")],
      cobblemon=True, furniture=True),
    # The Village Hall build in its three levels, walked round as a player sees it, then in Sandstone and Cherry
    S("village_hall", "Build families", "The Village Hall build (I, II, III), and Hall III in Sandstone and Cherry",
      "Village Hall I, II and III and Hall III in Sandstone and Cherry stand finished, each with its hall block and bell in place",
      90, [("10_village_hall_front", "Village Hall I: a timber hall with the bell under its porch"),
           ("12_village_hall_east", "Village Hall I from the side: posts at even bays, matching windows"),
           ("26_village_hall_3_back", "Village Hall III from behind: the meeting room and the bell-and-clock tower"),
           ("28_sandstone_village_hall_3_front", "Village Hall III in Sandstone")]),
    # The builds fixed after the owner's playtest of 2026-10-16, each from the front, both sides and the back
    S("build_fixes", "Build families", "Playtest fixes: Starter Cottage II, Storehouse, Terrace, Weaver's Cottage, Smithy, Apiary Garden",
      "the twelve fixed builds stand finished side by side",
      70, [("13_starter_cottage_2_back", "Starter Cottage II from behind: the upstairs windows in one row"),
           ("26_terrace_front", "The Terrace: steps only where they lead somewhere"),
           ("42_smithy_front", "The Smithy: its lanterns hung or on a post"),
           ("50_apiary_garden_front", "The Apiary Garden: the beds sit below the fence")]),
    S("realm_builds", "Realm builds", "The Colony Camp, the Royal Hall and the Royal Hall II, and all three in the five styles",
      "the Colony Camp, the Royal Hall and the Royal Hall II stand as drawn and in the five styles, the camp with its table and chest, each hall with its vault and on the map as a hall",
      200, [("10_colony_camp", "The Colony Camp: two wagons, the Village Hall under a striped awning, the campfire and the Travel Post"),
           ("12_royal_hall", "The Royal Hall: a stone great hall with a gabled porch and a rose window"),
           ("15_royal_hall_2_back", "Royal Hall II from behind: the keep tower, the treasury wing and the vault")]),
    # Wonders (M35): every Wonder's design placed whole (ROADMAP 35.9; each design adds its stills)
    S("wonder_gallery", "Wonders", "The Wonders, each placed whole", "every Wonder stands whole, every part of every stage placed", 90,
      # each Wonder from the front (even) and from behind (odd): 10 the Great Cathedral, 12 the Lighthouse, 14 the Great
      # Library, 16 the Observatory, 18 the Great Wall, 20 the Hanging Gardens, 22 the Colossus, 24 the Stadium (Cobblemon's,
      # so the scene is filmed with it); four spread over them on the card, the rest in its gallery
      [("[12][0-9]_*@spread", "")], cobblemon=True),
    S("village_habitat", "Habitat Keeper", "The village's own Habitat Block (Cobblemon 1.8)",
      "the Expert keeper put a natural Habitat Block under the garden's center stone and the hall lists today's Pokémon", 120,
      [("01_garden", "The finished Habitat Garden"), ("02_habitat", "Its center stone is now a Habitat Block"),
       ("03_hall_line", "Close up: it still looks like moss")], cobblemon18=True),
    S("camp", "Build families", "A Settler's Wagon camp", "the camp was set up", 45,
      [("01_camp", "The camp"), ("02_camp_back", "From behind")]),
    # Job plates (B221): looking at a working villager shows its job and level over its head, after its own name if it has
    # one; a jobless villager shows nothing
    S("job_plate", "Everyone at work", "Look at a villager to see its job and level",
      "a Journeyman fisherman's plate reads its job and level, a named builder's her name first, a jobless villager's nothing", 15,
      [("01_fisherman_plate", "Fisherman, Journeyman"), ("02_named_plate", "Dara — Builder, Expert"),
       ("03_jobless_no_plate", "A jobless villager: no plate")]),
]

BY_NAME = {s["name"]: s for s in SCENES}
STARTUP = 30  # seconds: Gradle, the client's start-up and loading the world, per scene (GitHub runner)
SHARD_TARGET = 30 * 60  # seconds of scenes per shard, to finish well within the hour
# The most shards a run may use. GitHub runs 20 jobs at once on a free plan and queues the rest, so a run with more
# shards than that takes longer but no shard does. It was 18 until the catalog passed 170 scenes: if those all ran
# twice as long as estimated, 18 shards came to about 54 minutes each (B100). It was 36 until the catalog passed 270
# scenes: at twice their estimates those came to 46 minutes a shard (B199); 48 gives about 34, with room to grow. A
# queued job's wait doesn't count toward its timeout-minutes, and GitHub's matrix cap is 256 jobs.
MAX_SHARDS = 48


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
    out = {"SCENE": scene, "COBBLEMON": "true" if s["cobblemon"] else "false", "MEGA": "true" if s["mega"] else "false",
           "FURNITURE": "true" if s.get("furniture") else "false"}
    if s["cobblemon18"]:
        out["COBBLEMON18"] = "true"
    out["CREATE"] = "true" if s.get("create") else "false"
    out["WAYSTONES"] = "true" if s.get("waystones") else "false"
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
