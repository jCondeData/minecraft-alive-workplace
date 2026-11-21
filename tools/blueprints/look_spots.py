#!/usr/bin/env python3
"""A biome look is the same building (ROADMAP 49.10a/b): checks a look's candidates against the shipped family, level by
level, before the critic sees them.

    python3 tools/blueprints/look_spots.py <look folder> <base id>     e.g. stone_house_desert stone_house

For each level (<base id>, <base id>_2, ... while the shipped drawing exists):
  - every furniture spot (kind, pos, facing) of the shipped blueprint_furniture/<id>.json is among the candidate's
    spots (build/rebuild/<look folder>/<id>.furniture.json); a lamp on another chain length is listed, not failed;
  - every bed, chest, barrel, furnace, smoker, crafting table, door and ladder of the shipped .nbt is the same block in
    the same cell of the candidate (build/rebuild/<look folder>/<id>.nbt).
Exits 1 on any difference."""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, HERE)
from holes import load  # noqa: E402

DATA = os.path.join(ROOT, "src/main/resources/data/aliveworkplace")
KEEP = ("bed", "chest", "barrel", "furnace", "crafting_table", "smoker", "door", "ladder")
# Blocks whose POI counts as a job block in the looks' GameTests (every POI but a bed's), by type and count (49.10g;
# the Manor's snowy lightning rod failed its test in 49.10f because nothing checked this before the ship step).
POI = ("barrel", "blast_furnace", "brewing_stand", "cartography_table", "cauldron", "water_cauldron", "lava_cauldron",
       "powder_snow_cauldron", "composter", "fletching_table", "grindstone", "lectern", "loom", "smithing_table",
       "smoker", "stonecutter", "lightning_rod", "bell", "beehive", "bee_nest", "lodestone")


def jobs(cells):
    out = {}
    for n in cells.values():
        if n and n.split(":")[-1] in POI:
            k = n.split(":")[-1].replace("water_", "").replace("lava_", "").replace("powder_snow_", "")
            out[k] = out.get(k, 0) + 1
    return out


def main(argv):
    look, base = argv[0], argv[1]
    ok, level = True, 1
    while True:
        id_ = base if level == 1 else f"{base}_{level}"
        shipped_nbt = os.path.join(DATA, "structure", id_ + ".nbt")
        if not os.path.exists(shipped_nbt):
            break
        shipped = json.load(open(os.path.join(DATA, "blueprint_furniture", id_ + ".json")))["spots"]
        cand = json.load(open(os.path.join(ROOT, "build/rebuild", look, id_ + ".furniture.json")))["spots"]
        key = lambda s: (s["kind"], tuple(s["pos"]), s["facing"])  # noqa: E731
        have = {key(s): s for s in cand}
        missing = [key(s) for s in shipped if key(s) not in have]
        chain = [key(s)[1] for s in shipped if key(s) in have and len(s.get("blocks", [])) != len(have[key(s)].get("cells", []))]
        _, a = load(shipped_nbt)
        _, b = load(os.path.join(ROOT, "build/rebuild", look, id_ + ".nbt"))
        moved = [(p, n, b.get(p)) for p, n in a.items()
                 if n and any(k in n for k in KEEP) and "trapdoor" not in n and b.get(p) != n]
        print(f"{id_}: {len(shipped)} shipped spots, {len(missing)} missing{' ' + str(missing) if missing else ''}; "
              f"{len(moved)} beds/containers/job blocks/doors moved{' ' + str(moved[:8]) if moved else ''}")
        ja, jb = jobs(a), jobs(b)
        if ja != jb:
            print(f"  job blocks differ: shipped {ja}, look {jb}")
            ok = False
        if chain:
            print(f"  lamps on another chain length (allowed): {chain}")
        ok = ok and not missing and not moved
        level += 1
    print("SAME BUILDING" if ok else "DIFFERENT")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
