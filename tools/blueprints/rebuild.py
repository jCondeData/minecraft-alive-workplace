#!/usr/bin/env python3
"""
The rebuild kit's adapter (ROADMAP 49.1, tools/blueprints/REBUILD.md; generalised from 36.23's house_t1_b.py): a
family's JavaScript recipe (tools/blueprints/js/<family>/, the minecraft-builder skill) writes an .nbt and
furniture.json; this loads them into a kit Build, records every furniture piece of the plan as a furniture spot (those
furniture.detect doesn't find by itself: chairs at a table, tables and beds are found), and saves it the way every
blueprint is saved: check.py's checks (the owner's rules included), finish() for stair corners and fence/pane joins,
and the furniture spots.

    (cd tools/blueprints/js/<family> && node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955)
    python3 tools/blueprints/rebuild.py <family> <id> --candidate     # build/rebuild/<family>/<id>.nbt, for the critic
    python3 tools/blueprints/rebuild.py <family> <id>                 # the live structure/<id>.nbt, once the critic says SHIP

--nbt <path> picks the recipe's output when out/ holds more than one .nbt (default: the newest). --basement <n> and
--cellar x0,z0,x1,z1: a drawing with a cellar (its .nbt holds n layers under the ground at the bottom).
"""
import argparse
import glob
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import furniture  # noqa: E402
from holes import load  # noqa: E402
from kit import MAIN_STRUCTURES, ROOT, Build  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
CANDIDATES = os.path.join(ROOT, "build", "rebuild")


def load_recipe(nbt, spots_file, basement=0, cellars=()):
    """The recipe's .nbt as a kit Build, with the plan's furniture spots recorded. basement: the file holds that many
    layers of cellar at its bottom (a LiftedCanvas), moved back under y = 0; cellars: the inside boxes (x0, z0, x1, z1)
    dug out under the ground."""
    (w, h, d), cells, states = load(nbt, props=True)
    b = Build(w, h - basement, d, basement)
    b.cellars = [tuple(c) for c in cellars]
    for (x, y, z), name in cells.items():
        if name is None or name == "minecraft:air":
            continue
        b.set(x, y - basement, z, name, **states[(x, y, z)])
    b.fill_air()
    with open(spots_file) as f:
        plan = json.load(f)["spots"]
    found = {tuple(c) for s in furniture.detect(b) for c in s["cells"]}
    for s in plan:
        px, py, pz = s["pos"]
        cells = [(px + dx, py + dy, pz + dz) for dx, dy, dz in s["cells"]]
        if any(c in found for c in cells):
            continue  # a chair at the table, the table or a bed: found and recorded by furniture.detect
        b.furniture(s["kind"], px, py, pz, s["facing"], cells=s["cells"], wood=s.get("wood"), colour=s.get("colour"))
    return b


def main(argv=None):
    a = argparse.ArgumentParser(description="Save a family's JavaScript recipe output as a blueprint (REBUILD.md).")
    a.add_argument("family")
    a.add_argument("id")
    a.add_argument("--candidate", action="store_true", help="save to build/rebuild/<family>/, not the live structure")
    a.add_argument("--nbt")
    a.add_argument("--basement", type=int, default=0, help="layers of cellar at the bottom of the recipe's .nbt")
    a.add_argument("--cellar", action="append", default=[], help="x0,z0,x1,z1: a cellar's inside, dug out")
    args = a.parse_args(argv)
    recipe = os.path.join(HERE, "js", args.family)
    nbt = args.nbt or max(glob.glob(os.path.join(recipe, "out", "*.nbt")), key=os.path.getmtime, default=None)
    if not nbt:
        a.error(f"no .nbt in {recipe}/out: run the recipe first")
    cellars = [tuple(int(v) for v in c.split(",")) for c in args.cellar]
    b = load_recipe(nbt, os.path.join(recipe, "furniture.json"), args.basement, cellars)
    folder = os.path.join(CANDIDATES, args.family) if args.candidate else MAIN_STRUCTURES
    b.save(folder, args.id)
    if args.candidate:  # outside structure/, so save() wrote no spots file: keep them beside the candidate
        with open(os.path.join(folder, args.id + ".furniture.json"), "w") as f:
            json.dump({"spots": getattr(b, "spots", []) + furniture.detect(b)}, f, indent=1, default=list)  # with the beds, tables and chairs found in the drawing
    return 0


if __name__ == "__main__":
    sys.exit(main())
