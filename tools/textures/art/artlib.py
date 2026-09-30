"""Shared setup for the texture recipes: the pixel-art library (../pxlib) on the path, and where the mod's
textures go. Every recipe module starts with `from artlib import *`."""
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "pxlib"))

from px import *  # noqa: E402,F401,F403
import villager as vg  # noqa: E402,F401

ROOT = Path(__file__).resolve().parents[3]
# ALIVE_ASSETS writes somewhere else (drafts, comparisons) instead of the mod's resources.
ASSETS = Path(os.environ.get("ALIVE_ASSETS", ROOT / "src/main/resources/assets/aliveworkplace"))


def block(name):
    """Where a block texture goes: textures/block/<name>.png."""
    return ASSETS / "textures" / "block" / f"{name}.png"


def item(name):
    """Where an item texture goes: textures/item/<name>.png."""
    return ASSETS / "textures" / "item" / f"{name}.png"


def run(draws):
    """Draws every texture of a module (its DRAW list), or only the ones named on the command line."""
    wanted = set(sys.argv[1:])
    for draw in draws:
        if not wanted or draw.__name__ in wanted:
            for path in draw() or []:
                print("wrote", os.path.relpath(path, ROOT))


def grid(art, legend=None, ramp=None):
    """Sprite.from_ascii for an indented triple-quoted block (the closing quotes may be indented too)."""
    return Sprite.from_ascii(art.strip(), legend, ramp)
