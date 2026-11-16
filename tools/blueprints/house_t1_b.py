"""
House T1, Version B (ROADMAP 36.23): the adapter between the JavaScript recipe and our kit.

The house is drawn by tools/blueprints/js/house_t1_b/recipe.mjs (the minecraft-builder skill: a plan, a parts library,
a recipe that writes the .nbt; see its plan.mjs for the design notes, rooms and furniture footprints). This loads
that .nbt into a kit Build, records every furniture piece of the plan as a furniture spot (furniture.json, written by
the recipe beside it), and saves it the way every blueprint is saved: check.py's checks, finish() for stair corners
and fence/pane joins, and blueprint_furniture/house_t1_b.json.

    (cd tools/blueprints/js/house_t1_b && MC_BUILDER=<skill folder> npm run build)
    python3 tools/blueprints/house_t1_b.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import furniture  # noqa: E402
from holes import load  # noqa: E402
from kit import MAIN_STRUCTURES, Build  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
RECIPE = os.path.join(HERE, "js", "house_t1_b")
NBT = os.path.join(MAIN_STRUCTURES, "house_t1_b.nbt")


def load_b(path=NBT):
    """The recipe's house as a kit Build, with the plan's furniture spots recorded (those the drawing can't be read
    for: the chairs at its table, the table and the beds are found by furniture.detect)."""
    (w, h, d), cells, states = load(path, props=True)
    b = Build(w, h, d)
    for (x, y, z), name in cells.items():
        if name is None or name == "minecraft:air":
            continue
        b.set(x, y, z, name, **states[(x, y, z)])
    b.fill_air()
    with open(os.path.join(RECIPE, "furniture.json")) as f:
        plan = json.load(f)["spots"]
    found = {tuple(c) for s in furniture.detect(b) for c in s["cells"]}
    for s in plan:
        px, py, pz = s["pos"]
        cells = [(px + dx, py + dy, pz + dz) for dx, dy, dz in s["cells"]]
        if any(c in found for c in cells):
            continue  # a chair at the table, the table or a bed: found and recorded by furniture.detect
        b.furniture(s["kind"], px, py, pz, s["facing"], cells=s["cells"], wood=s.get("wood"), colour=s.get("colour"))
    return b


if __name__ == "__main__":
    load_b().save(MAIN_STRUCTURES, "house_t1_b")
