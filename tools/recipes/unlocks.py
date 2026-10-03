#!/usr/bin/env python3
"""Recipe-book unlocks for the mod's recipes, the way vanilla does it: a hidden advancement per recipe that gives the
recipe as soon as the player holds any of its ingredients (or already has the recipe).

    python3 tools/recipes/unlocks.py        # -> data/aliveworkplace/advancement/recipes/<category>/<recipe>.json

Without these, none of our recipes ever shows in the recipe book (found by the 26.2a tester). Run it again after
adding or changing a recipe; GuideGameTests checks every recipe has its unlock.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "src/main/resources/data/aliveworkplace"


def ingredients(recipe):
    if recipe["type"] == "minecraft:crafting_shaped":
        found = list(recipe["key"].values())
    else:
        found = list(recipe.get("ingredients", []))
    out = []
    for ing in found:
        for one in (ing if isinstance(ing, list) else [ing]):
            key = "#" + one["tag"] if "tag" in one else one["item"]
            if key not in out:
                out.append(key)
    return out


def main():
    made = 0
    for path in sorted((DATA / "recipe").glob("*.json")):
        recipe = json.loads(path.read_text())
        rid = f"aliveworkplace:{path.stem}"
        criteria = {"has_the_recipe": {"conditions": {"recipe": rid}, "trigger": "minecraft:recipe_unlocked"}}
        for key in ingredients(recipe):
            name = "has_" + key.lstrip("#").split(":")[1].replace("/", "_")
            criteria[name] = {"conditions": {"items": [{"items": key}]}, "trigger": "minecraft:inventory_changed"}
        advancement = {
            "parent": "minecraft:recipes/root",
            "criteria": criteria,
            "requirements": [list(criteria)],
            "rewards": {"recipes": [rid]},
        }
        out = DATA / "advancement" / "recipes" / recipe.get("category", "misc") / f"{path.stem}.json"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(advancement, indent=2) + "\n")
        made += 1
    print(f"{made} recipe unlocks in {(DATA / 'advancement' / 'recipes').relative_to(ROOT)}")


if __name__ == "__main__":
    main()
