#!/usr/bin/env python3
"""
The Wonders' goods (ROADMAP 35.7, changed by the owner in 35.7a and 35.7b) and their pacing.

    python3 tools/blueprints/wonder_goods.py           # the pacing table (hours of play per stage and Wonder)
    python3 tools/blueprints/wonder_goods.py --write   # rewrites only the "goods" of data/aliveworkplace/wonders/*.json

The owner's target: a Wonder is a month's work for a group playing every other day, 3 hours a night: about 15 sessions,
45 hours of play. Slow progress towards a big goal with lots of little wins, never busy work, and no "have X of
something" counts. So each stage asks for a few hard things found by exploring and searching ("found" lines, one of
each: a Heart of the Sea, an Echo Shard, a Nether Star) and one rare good from an upgraded workshop (a Journeyman's
luxury, two of them: Vintage Wine, a Gold Circlet, an Illuminated Book, Noble Robes). Each found line has a helper: a
rare worker (a Legend, or a Master of a trade) who, while in the village, finds it in the background in play time
(WonderGoods.search), so every rare worker who arrives is a little win on the way.

wonders.py takes each Wonder's goods from GOODS, so its save_wonder() writes the same lines; --write here changes only
the goods, without drawing the Wonders again.

How the hours are reckoned (pacing()): FINDS gives the hours a group of 3 or 4 with iron and diamond gear spends to find
one of each thing, from setting out to coming home with it (finding the structure, getting in, the drop's odds). A stage
takes the hours of the finds no helper covers, one after another, plus OVERHEAD for the workshop goods and the build
of the stage after its dedication, gathering its materials (what doesn't run beside the searching); finds a helper
covers cost the group nothing, but the stage can't be dedicated before the slowest helper is done, unless searching for
that one itself is quicker. A helper searches for one and a half times the group's hours (HELPER_SHARE) of play,
counted in Minecraft days (3 a real hour): slower than the group, so a helper frees the group rather than doing the
stage for it. TYPICAL is a Town with Masters of the rarer trades and the commonest Legend, the Pathfinder.
"""
import glob
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DATA = os.path.join(ROOT, "src/main/resources/data/aliveworkplace/wonders")

# hours of play a group spends finding one (see the docstring), and where (the hint on the stone, in en_us.json)
FINDS = {
    "minecraft:coast_armor_trim_smithing_template": 1.5,   # a shipwreck's chests
    "minecraft:ghast_tear": 1.5,                            # Ghasts over the Nether's lava seas
    "minecraft:torchflower_seeds": 1.5,                     # dug by a Sniffer (with the Pitcher Pod's Sniffer)
    "minecraft:sentry_armor_trim_smithing_template": 2.0,  # pillager outposts
    "minecraft:dune_armor_trim_smithing_template": 2.0,    # desert temples
    "minecraft:prize_pottery_sherd": 2.0,                   # desert temples' suspicious sand
    "minecraft:shulker_shell": 2.0,                         # Shulkers in an End city (on the Elytra's trip)
    "minecraft:heart_of_the_sea": 2.5,                      # buried treasure, by a treasure map
    "minecraft:wild_armor_trim_smithing_template": 2.5,    # jungle temples
    "minecraft:angler_pottery_sherd": 2.5,                  # warm ocean ruins' suspicious sand
    "minecraft:trident": 3.0,                               # Drowned, a rare drop
    "minecraft:wither_skeleton_skull": 3.0,                 # Wither Skeletons in a fortress
    "minecraft:netherite_ingot": 3.0,                       # Ancient Debris and gold
    "minecraft:netherite_upgrade_smithing_template": 3.0,  # bastion remnants
    "minecraft:rib_armor_trim_smithing_template": 3.0,     # Nether fortresses
    "minecraft:sniffer_egg": 3.0,                           # warm ocean ruins' suspicious sand
    "minecraft:heart_pottery_sherd": 3.0,                   # trail ruins' suspicious gravel
    "minecraft:ominous_trial_key": 3.0,                     # an ominous trial spawner, under Trial Omen
    "minecraft:flow_armor_trim_smithing_template": 3.0,    # trial chambers' vaults
    "minecraft:diamond_horse_armor": 3.0,                   # dungeons, temples, strongholds, End cities
    "minecraft:music_disc_relic": 3.5,                      # trail ruins' suspicious gravel
    "minecraft:echo_shard": 4.0,                            # ancient cities
    "minecraft:eye_armor_trim_smithing_template": 4.0,     # strongholds
    "minecraft:music_disc_otherside": 4.0,                  # dungeons and strongholds
    "minecraft:music_disc_pigstep": 4.0,                    # bastion remnants
    "minecraft:music_disc_precipice": 4.0,                  # trial chambers' vaults
    "minecraft:sponge": 4.0,                                # ocean monuments
    "minecraft:pitcher_pod": 4.0,                           # dug by a Sniffer hatched from a found egg
    "minecraft:conduit": 4.0,                               # a Heart of the Sea and eight Nautilus Shells
    "minecraft:totem_of_undying": 5.0,                      # Evokers: woodland mansions and raids
    "minecraft:enchanted_golden_apple": 5.0,                # old chests: mineshafts, temples, bastions, ancient cities
    "minecraft:vex_armor_trim_smithing_template": 5.0,     # woodland mansions
    "minecraft:tide_armor_trim_smithing_template": 5.0,    # an ocean monument's Elder Guardians
    "minecraft:heavy_core": 7.0,                            # trial chambers' ominous vaults, a rare reward
    "minecraft:nether_star": 8.0,                           # the Wither (three skulls, then the fight)
    "minecraft:elytra": 10.0,                               # an End city's ship, past the Ender Dragon
}
HELPER_SHARE = 1.5
DAYS_PER_HOUR = 3
OVERHEAD = 2.5
TARGET = 45


def helper_days(item):
    return max(1, round(FINDS[item] * DAYS_PER_HOUR * HELPER_SHARE))


# the rare workers (35.7b): the Legends (data/aliveworkplace/legends) and Masters of the rarer trades
def legend(name):
    return {"type": "legend", "id": "aliveworkplace:" + name}


def master(job):
    return {"type": "job_level", "job": job, "level": 5, "count": 1}


PATHFINDER = legend("pathfinder")
OLD_SAGE = legend("old_sage")
SEER = legend("seer")
BEASTMASTER = legend("beastmaster")
BARD_LAUREATE = legend("bard_laureate")
GRAND_CHEF = legend("grand_chef")
MERCHANT_PRINCE = legend("merchant_prince")
GOLEM_SMITH = legend("golem_smith")
FOUNDER = legend("founder")
POKEMON_RANGER = legend("pokemon_ranger")  # only with Cobblemon: the Stadium's
NETHERWORKER = master("aliveworkplace:netherworker")
SIFTER = master("aliveworkplace:sifter")
FISHERMAN = master("minecraft:fisherman")
# the helpers a typical village has when it starts a Wonder (a Town): Masters of its trades and one Legend, the Pathfinder
TYPICAL = [NETHERWORKER, SIFTER, FISHERMAN, PATHFINDER]


def found(item, helper):
    """One of something found by exploring, and the rare worker who'd find it for the village."""
    return {"item": item, "count": 1, "found": True, "helper": {"days": helper_days(item), "condition": helper}}


def made(item, count, job):
    """A rare good made by an upgraded workshop (a Journeyman's luxury, data/aliveworkplace/luxury_recipes)."""
    return {"item": item, "count": count, "job": job}


VINTAGE_WINE = ("aliveworkplace:vintage_wine", "aliveworkplace:vintner")
BERRY_WINE = ("aliveworkplace:berry_wine", "aliveworkplace:vintner")
GOLD_CIRCLET = ("aliveworkplace:gold_circlet", "aliveworkplace:jeweller")
EMERALD_BROOCH = ("aliveworkplace:emerald_brooch", "aliveworkplace:jeweller")
ILLUMINATED_BOOK = ("aliveworkplace:illuminated_book", "aliveworkplace:printer")
NOBLE_ROBES = ("aliveworkplace:noble_robes", "aliveworkplace:tailor")
FINE_CLOTHES = ("aliveworkplace:fine_clothes", "aliveworkplace:tailor")


def two(good):
    return made(good[0], 2, good[1])


M = "minecraft:"
GOODS = {
    "great_cathedral": [  # the Seer is its own Legend, so not a helper here
        [found(M + "prize_pottery_sherd", SIFTER), found(M + "ghast_tear", NETHERWORKER), two(FINE_CLOTHES)],
        [found(M + "music_disc_otherside", BARD_LAUREATE), found(M + "echo_shard", OLD_SAGE), found(M + "heart_of_the_sea", PATHFINDER),
         two(ILLUMINATED_BOOK)],
        [found(M + "totem_of_undying", MERCHANT_PRINCE), found(M + "enchanted_golden_apple", GRAND_CHEF),
         found(M + "eye_armor_trim_smithing_template", OLD_SAGE), two(NOBLE_ROBES)],
        [found(M + "elytra", PATHFINDER), found(M + "nether_star", GOLEM_SMITH), found(M + "music_disc_relic", BARD_LAUREATE), two(GOLD_CIRCLET)],
    ],
    "lighthouse": [  # the Merchant Prince is its own
        [found(M + "heart_of_the_sea", PATHFINDER), found(M + "coast_armor_trim_smithing_template", FISHERMAN), two(EMERALD_BROOCH)],
        [found(M + "trident", BEASTMASTER), found(M + "sponge", FOUNDER), found(M + "angler_pottery_sherd", GRAND_CHEF), two(GOLD_CIRCLET)],
        [found(M + "tide_armor_trim_smithing_template", FISHERMAN), found(M + "conduit", SEER), found(M + "totem_of_undying", OLD_SAGE),
         two(ILLUMINATED_BOOK)],
        [found(M + "nether_star", GOLEM_SMITH), found(M + "elytra", PATHFINDER), found(M + "ghast_tear", NETHERWORKER), two(VINTAGE_WINE)],
    ],
    "great_library": [  # the Old Sage is its own
        [found(M + "heart_pottery_sherd", SIFTER), found(M + "dune_armor_trim_smithing_template", PATHFINDER),
         made(ILLUMINATED_BOOK[0], 1, ILLUMINATED_BOOK[1])],
        [found(M + "eye_armor_trim_smithing_template", SEER), found(M + "music_disc_relic", BARD_LAUREATE),
         found(M + "wild_armor_trim_smithing_template", PATHFINDER), two(NOBLE_ROBES)],
        [found(M + "echo_shard", SEER), found(M + "enchanted_golden_apple", GRAND_CHEF), found(M + "vex_armor_trim_smithing_template", MERCHANT_PRINCE),
         two(ILLUMINATED_BOOK)],
        [found(M + "ominous_trial_key", PATHFINDER), found(M + "nether_star", GOLEM_SMITH), found(M + "music_disc_otherside", BARD_LAUREATE),
         two(GOLD_CIRCLET)],
    ],
    "observatory": [  # the Seer is its own
        [found(M + "heart_of_the_sea", MERCHANT_PRINCE), found(M + "ghast_tear", NETHERWORKER), made(M + "spyglass", 2, M + "toolsmith")],
        [found(M + "echo_shard", OLD_SAGE), found(M + "eye_armor_trim_smithing_template", PATHFINDER), found(M + "music_disc_precipice", BARD_LAUREATE),
         two(ILLUMINATED_BOOK)],
        [found(M + "ominous_trial_key", GOLEM_SMITH), found(M + "totem_of_undying", MERCHANT_PRINCE), found(M + "wither_skeleton_skull", NETHERWORKER),
         two(GOLD_CIRCLET)],
        [found(M + "nether_star", GOLEM_SMITH), found(M + "elytra", PATHFINDER), found(M + "shulker_shell", BEASTMASTER), two(NOBLE_ROBES)],
    ],
    "great_wall": [  # the Golem Smith is its own
        [found(M + "sentry_armor_trim_smithing_template", BEASTMASTER), found(M + "ghast_tear", NETHERWORKER), two(EMERALD_BROOCH)],
        [found(M + "totem_of_undying", MERCHANT_PRINCE), found(M + "ominous_trial_key", PATHFINDER), found(M + "rib_armor_trim_smithing_template", SEER),
         two(VINTAGE_WINE)],
        [found(M + "wither_skeleton_skull", FOUNDER), found(M + "netherite_upgrade_smithing_template", BEASTMASTER),
         found(M + "vex_armor_trim_smithing_template", MERCHANT_PRINCE), two(GOLD_CIRCLET)],
        [found(M + "heavy_core", PATHFINDER), found(M + "nether_star", FOUNDER), found(M + "netherite_ingot", OLD_SAGE), two(NOBLE_ROBES)],
    ],
    "hanging_gardens": [  # the Grand Chef is its own
        [found(M + "sniffer_egg", BEASTMASTER), found(M + "wild_armor_trim_smithing_template", PATHFINDER), two(BERRY_WINE)],
        [found(M + "pitcher_pod", BEASTMASTER), found(M + "torchflower_seeds", OLD_SAGE), found(M + "heart_pottery_sherd", SIFTER), two(VINTAGE_WINE)],
        [found(M + "enchanted_golden_apple", SEER), found(M + "heart_of_the_sea", PATHFINDER), found(M + "sponge", BARD_LAUREATE), two(GOLD_CIRCLET)],
        [found(M + "totem_of_undying", MERCHANT_PRINCE), found(M + "nether_star", GOLEM_SMITH), found(M + "conduit", FOUNDER), two(NOBLE_ROBES)],
    ],
    "colossus": [  # the Founder is its own
        [found(M + "heart_of_the_sea", PATHFINDER), found(M + "coast_armor_trim_smithing_template", MERCHANT_PRINCE), two(EMERALD_BROOCH)],
        [found(M + "trident", BEASTMASTER), found(M + "sponge", SEER), found(M + "netherite_upgrade_smithing_template", NETHERWORKER),
         two(GOLD_CIRCLET)],
        [found(M + "tide_armor_trim_smithing_template", FISHERMAN), found(M + "totem_of_undying", MERCHANT_PRINCE),
         found(M + "wither_skeleton_skull", OLD_SAGE), two(NOBLE_ROBES)],
        [found(M + "heavy_core", GOLEM_SMITH), found(M + "nether_star", BEASTMASTER), found(M + "elytra", PATHFINDER), two(VINTAGE_WINE)],
    ],
    "stadium": [  # with Cobblemon only; the Pokemon Professor is its own
        [found(M + "diamond_horse_armor", BEASTMASTER), found(M + "coast_armor_trim_smithing_template", MERCHANT_PRINCE),
         made("cobblemon:great_ball", 2, "aliveworkplace:ball_smith")],
        [found(M + "trident", FISHERMAN), found(M + "music_disc_pigstep", BARD_LAUREATE), found(M + "netherite_upgrade_smithing_template", NETHERWORKER),
         two(GOLD_CIRCLET)],
        [found(M + "totem_of_undying", MERCHANT_PRINCE), found(M + "ominous_trial_key", PATHFINDER), found(M + "flow_armor_trim_smithing_template", GOLEM_SMITH),
         made("cobblemon:ultra_ball", 2, "aliveworkplace:ball_smith")],
        [found(M + "nether_star", GOLEM_SMITH), found(M + "heavy_core", POKEMON_RANGER), found(M + "enchanted_golden_apple", GRAND_CHEF), two(NOBLE_ROBES)],
    ],
}


def stage_hours(lines, present):
    """Hours of play for one stage with the helpers {@code present} (conditions) in the village: the group searches for
    what no helper covers, then waits for the slowest helper, unless searching for that one itself is quicker."""
    own, waits = 0.0, []
    for line in lines:
        if not line.get("found"):
            continue
        helper = line["helper"]
        if helper["condition"] in present:
            waits.append((helper["days"] / DAYS_PER_HOUR, FINDS[line["item"]]))
        else:
            own += FINDS[line["item"]]
    waits.sort(reverse=True)
    while waits and waits[0][0] + 0.5 > own + OVERHEAD and own + waits[0][1] + OVERHEAD < waits[0][0] + 0.5:
        own += waits.pop(0)[1]
    return max(own + OVERHEAD, (waits[0][0] + 0.5) if waits else 0.0)


def pacing(present_for):
    out = {}
    for wonder, stages in GOODS.items():
        out[wonder] = [stage_hours(lines, present_for(stages)) for lines in stages]
    return out


def helpers_of(stages):
    return [line["helper"]["condition"] for lines in stages for line in lines if line.get("found")]


def table():
    none = pacing(lambda s: [])
    typical = pacing(lambda s: TYPICAL)
    every = pacing(helpers_of)
    print(f"{'Wonder':16} {'no helpers':>22} {'typical':>22} {'every helper':>22}  helpers")
    for wonder in GOODS:
        row = [f"{' '.join(f'{h:4.1f}' for h in p[wonder])} = {sum(p[wonder]):4.1f}" for p in (none, typical, every)]
        names = sorted({c.get("id", c.get("job", "")).split(":")[1] + ("" if c["type"] == "legend" else " (Master)")
                        for c in helpers_of(GOODS[wonder])})
        print(f"{wonder:16} {row[0]:>22} {row[1]:>22} {row[2]:>22}  {len(names)}: {', '.join(names)}")
    for name, p in (("no helpers", none), ("typical", typical), ("every helper", every)):
        hours = [sum(v) for v in p.values()]
        print(f"{name}: {min(hours):.1f} to {max(hours):.1f} hours, mean {sum(hours) / len(hours):.1f} (target {TARGET})")


def write():
    for path in sorted(glob.glob(os.path.join(DATA, "*.json"))):
        wonder = os.path.basename(path)[:-5]
        if wonder not in GOODS:
            continue
        data = json.load(open(path))
        assert len(data["stages"]) == len(GOODS[wonder]), wonder
        for stage, lines in zip(data["stages"], GOODS[wonder]):
            stage["goods"] = lines
        text = json.dumps(data, indent=2)
        text = re.sub(r"\[\s+(-?\d+),\s+(-?\d+),\s+(-?\d+)\s+\]", r"[\1, \2, \3]", text)  # as wonders.py writes it
        with open(path, "w") as f:
            f.write(text + "\n")
        print("wrote", os.path.relpath(path, ROOT))


if __name__ == "__main__":
    if "--write" in sys.argv:
        write()
    table()
