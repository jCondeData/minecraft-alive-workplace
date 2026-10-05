#!/usr/bin/env python3
"""
Draws every texture of the mod (all original; vanilla textures are only compared against, never copied).

    python3 tools/textures/generate.py            # everything
    python3 tools/textures/generate.py apiary     # only the recipes with these names

The recipes are in tools/textures/art/, one module per set, drawn with the owner's minecraft-pixel-art skill library
(copied into tools/textures/pxlib):
- builders.py        the Builder's Bench and the Builder's outfit (the approved style reference)
- workstations_*.py  every other workstation block
- villagers_*.py     every other profession's outfit (and its zombie copy)
- items.py           the item icons, each drawn like vanilla draws its kind (books, maps, sticks...)
- gui.py             screen backgrounds drawn like vanilla's workstation screens (the Village Hall's)
Outputs go to src/main/resources/assets/aliveworkplace/textures/ (or $ALIVE_ASSETS), plus the mod's icon.png.
Check them with tools/textures/pxlib/lint.py and preview.py (see the pixel-art skill).
"""
import importlib
import os
import sys
from pathlib import Path

ART = Path(__file__).resolve().parent / "art"
sys.path.insert(0, str(ART))

import artlib  # noqa: E402
from PIL import Image  # noqa: E402

MODULES = ["builders", "workstations_1", "workstations_2", "workstations_3", "villagers_1", "villagers_2", "items", "gui"]


def icon():
    """The mod's icon: the Blueprint item, 8x."""
    path = artlib.ASSETS / "icon.png"
    Image.open(artlib.item("blueprint")).convert("RGBA").resize((128, 128), Image.NEAREST).save(path)
    return [path]


if __name__ == "__main__":
    wanted = set(sys.argv[1:])
    draws = [d for m in MODULES for d in importlib.import_module(m).DRAW] + [icon]
    unknown = wanted - {d.__name__ for d in draws}
    if unknown:
        sys.exit("no such recipe: " + ", ".join(sorted(unknown)))
    for draw in draws:
        if not wanted or draw.__name__ in wanted:
            for path in draw() or []:
                print("wrote", os.path.relpath(path, artlib.ROOT))
