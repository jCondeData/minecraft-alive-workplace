#!/usr/bin/env python3
"""The nightly showcase's scene catalog: every screenshot scene, the job it belongs to, and the stills the page shows.

    python3 tools/showcase/scenes.py list                  # every scene, one per line
    python3 tools/showcase/scenes.py matrix [--last F]     # GitHub Actions matrix: scenes split into shards
    python3 tools/showcase/scenes.py env SCENE             # KEY=VALUE lines for tools/screenshots/run.sh
    python3 tools/showcase/scenes.py check                 # every harness scene is in the catalog, and back

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
    "Pokémon Trader", "Fossil Scientist", "Village Hall", "Everyone at work", "Build families",
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
      [("01_items_chest", "All 13 in a chest"), ("03_hand_blueprint", "Blueprint in hand"),
       ("04_hand_rally_banner", "Rally Banner in hand"), ("07_hand_field_marker", "Field Marker in hand")]),
    # Builder
    S("builders", "Builder", "Three builders, three starter builds", "the builders finished all three builds", 420,
      [("02_builder_closeup", "A builder at work"), ("03_finished_wide", "All three finished"),
       ("04_cottage", "Starter Cottage"), ("06_lookout_tower", "Lookout Tower")]),
    S("preview", "Builder", "Ghost preview and the status over a builder", "the builder made progress on the previewed site", 120,
      [("20_preview_start", "Ghost preview"), ("21_preview_half_built", "Half built"), ("22_status_closeup", "Status over the builder")]),
    S("missing", "Builder", "What a build is still missing", "the blueprint's tooltip lists what the chests are short of", 60,
      [("00_missing_site", "The placed blueprint and the Blueprint Table"), ("01_blueprint_missing", "Still-missing tooltip")]),
    S("table", "Builder", "Blueprint Table", "the table screen opened and a file was uploaded", 60,
      [("10_table_library", "Library"), ("11_table_upload", "Upload a file"), ("12_table_uploaded", "Uploaded")]),
    S("shapes", "Builder", "Shape Planner", "the Shape Planner screen opened with its materials", 45,
      [("01_shapes_screen", "Shape Planner"), ("02_shapes_info", "Size and blocks")]),
    S("style_menu", "Builder", "Build it in a style", "the style picker opened and a style was chosen", 45,
      [("01_style_screen", "Style picker"), ("02_style_chosen", "Dark oak chosen")]),
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
    job("netherworker", "Netherworker", "A trip to the Nether", "the netherworker came back from the Nether with loot", 240),
    job("undertaker", "Undertaker", "Bringing a worker back from the grave", "the undertaker revived the villager"),
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
    S("guard_pokemon", "Guard", "A guard with Pokémon at his side", "the guard and the Pokémon killed the husks", 180,
      [("10_guard_armor", "In armor"), ("frame_*@middle", "Pokémon join in"), ("50_guard_done", "Husks down")],
      cobblemon=True),
    job("nurse", "Nurse", "Healing and curing villagers", "the nurse healed and cured the villagers"),
    # Shopkeeper
    S("shop", "Shopkeeper", "The shop", "the shop screen opened with prices", 60,
      [("01_shop_menu", "Shop screen"), ("02_shop_counter", "The counter")], cobblemon=True),
    S("counter", "Shopkeeper", "Setting prices at the Shop Counter", "the price list and a Price Tag's screen opened", 45,
      [("01_counter_screen", "Price list"), ("02_counter_price", "A price"), ("03_price_tag", "Price Tag")]),
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
    # Village-wide
    S("hall", "Village Hall", "The Village Hall and its screen", "the Village Hall screen opened", 60,
      [("01_hall_block", "The hall"), ("02_hall_people", "People"), ("03_hall_builder", "A builder"),
       ("05_hall_requests", "Requests")]),
    S("hall_pages", "Village Hall", "The chronicle and trade routes", "the chronicle and trade-route pages opened", 45,
      [("01_hall_chronicle", "Chronicle"), ("02_hall_routes", "Trade routes")]),
    S("staff", "Everyone at work", "Every workstation with its villager", "every villager took their job", 45,
      [("01_staff", "Every workstation"), ("02_staff_above", "From above")]),
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


def matrix(last):
    """Longest scenes first, each onto the shard with the least work so far."""
    total = sum(seconds(s, last) for s in SCENES)
    count = max(4, min(MAX_SHARDS, math.ceil(total / SHARD_TARGET)))
    shards = [[0, []] for _ in range(count)]
    for s in sorted(SCENES, key=lambda s: -seconds(s, last)):
        shard = min(shards, key=lambda x: x[0])
        shard[0] += seconds(s, last)
        shard[1].append(s["name"])
    shards = [s for s in shards if s[1]]
    return {"include": [{"shard": f"{i + 1:02d}", "scenes": " ".join(names), "minutes": round(t / 60)}
                        for i, (t, names) in enumerate(sorted(shards, key=lambda x: -x[0]))]}


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
    sub.add_parser("list")
    m = sub.add_parser("matrix")
    m.add_argument("--last", help="the previous run's showcase.json (durations)")
    e = sub.add_parser("env")
    e.add_argument("scene")
    sub.add_parser("check")
    a = p.parse_args()
    if a.cmd == "list":
        print("\n".join(s["name"] for s in SCENES))
    elif a.cmd == "matrix":
        print(json.dumps(matrix(load_last(a.last)), separators=(",", ":")))
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
