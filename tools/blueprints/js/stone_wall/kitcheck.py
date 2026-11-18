"""The stone wall kit's self-check helpers (ROADMAP 52.3), shared by stone_wall/selfcheck.py and wall_tower/selfcheck.py:
read a candidate .nbt and answer the questions the game's code asks of it, without the game.

  stations(cells, states)   BattleStations.read: a block at y >= 2 with a sturdy top (Direction.UP, SupportType.FULL),
                            no block of the template in the two cells over it, and not a merlon (a spot one lower beside).
  sturdy_top / collides     the block-shape questions those rules need (full cubes, top slabs, upside-down stairs ...).
"""
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "blueprints"))
from holes import load  # noqa: E402

CANDIDATES = os.path.join(ROOT, "build", "rebuild")
AIR = {None, "minecraft:air", "minecraft:cave_air", "minecraft:structure_void"}
# blocks whose top face is never a full sturdy face (nor, for the second list, a collision box at all)
THIN_TOP = ("_wall", "_fence", "ladder", "lantern", "_door", "_trapdoor", "_pane", "chain", "chest", "torch", "_carpet", "_bars",
            "lever", "_button", "_sign", "_banner", "flower_pot", "potted_", "lightning_rod", "candle", "_rod")
NO_COLLISION = ("torch", "lever", "_button", "_sign", "_banner", "_carpet_no", "vine", "short_grass", "fern", "flower")
GATES = ("_door", "fence_gate", "iron_bars")
POI = ("_bed", "barrel", "composter", "lectern", "bell", "blast_furnace", "smoker", "cartography_table", "brewing_stand",
       "fletching_table", "smithing_table", "stonecutter", "loom", "grindstone", "cauldron")


def read(family, ident):
    path = os.path.join(CANDIDATES, family, ident + ".nbt")
    size, cells, states = load(path, props=True)
    cells = {p: n for p, n in cells.items() if n not in AIR}
    return path, size, cells, states


def short(name):
    return name.split(":")[-1] if name else "air"


def sturdy_top(name, props):
    s = short(name)
    if name in AIR or any(t in s for t in THIN_TOP):
        return False
    if s.endswith("_slab"):
        return props.get("type") in ("top", "double")
    if s.endswith("_stairs"):
        return props.get("half") == "top"
    return True


def collides(name):
    return name not in AIR and not any(t in short(name) for t in NO_COLLISION)


def spots(cells, states):
    out = set()
    for (x, y, z), n in cells.items():
        if y >= 2 and (x, y + 1, z) not in cells and (x, y + 2, z) not in cells and sturdy_top(n, states[(x, y, z)]):
            out.add((x, y, z))
    return out


def stations(cells, states):
    sp = spots(cells, states)
    return sorted(p for p in sp if not any((p[0] + dx, p[1] - 1, p[2] + dz) in sp
                                           for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))))


def kept(base_cells, base_states, cells, states):
    same = sum(1 for p, n in base_cells.items() if cells.get(p) == n and states.get(p) == base_states[p])
    return same, len(base_cells)


class Checks:
    def __init__(self):
        self.failed = 0

    def __call__(self, ok, what):
        print(("  ok    " if ok else "  FAIL  ") + what)
        if not ok:
            self.failed += 1
        return ok
