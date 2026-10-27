#!/usr/bin/env python3
"""
The modded build library (ROADMAP 37.12): a beautified copy of every home and workshop, made from the vanilla
template that ships today, which stays exactly as it is (the vanilla library).

    python3 tools/blueprints/modded_library.py [--out DIR] [name...]

Each copy is written to data/aliveworkplace/structure/modded/<name>.nbt. The game (blueprint/ModdedLibrary) builds it
in place of <name> when the switch moddedLibrary is on and every block of the copy is a real block on the server; a
missing mod and the vanilla build is built, furnished as before (37.3). The copy keeps the vanilla build's shape, its
doors, job blocks, beds and furniture spots (37.2: the furnishing tables still turn those into Handcrafted pieces), and
dresses the rest with the pack's building mods:

- Supplementaries: plaster (white concrete) and the birch infill of timber frames become daub, braced beside posts.
  (No doormats: Supplementaries' doormat holds items, so every job would take it for a chest.)
- Chipped: covered wooden floors become herringbone boards of the same wood.
- Rechiseled: polished andesite floors and paving become andesite tiles; covered cobblestone floors brick paving.
- Carved Wood: chests, bookshelves and ladders are the build's own wood (a spruce house gets spruce chests).
- Handcrafted: a set of crockery on every table, a stack of books on bookshelves in the rooms, pots by barrels.

Nothing here is random at play time: every choice is by position, so a copy is the same on every run and an upgrade
dresses the cells it shares with its base the same way.
"""
import argparse
import glob
import json
import os
import re
import sys

import nbtlib
from nbtlib import Compound, Int, List, String

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
DATA = os.path.join(ROOT, "src/main/resources/data/aliveworkplace")
STRUCTURES = os.path.join(DATA, "structure")
SPOTS = os.path.join(DATA, "blueprint_furniture")
STARTER = os.path.join(ROOT, "src/main/java/io/github/jcondedata/aliveworkplace/blueprint/StarterBlueprints.java")
OUT = os.path.join(STRUCTURES, "modded")

# Lists in StarterBlueprints that are not homes or workshops: decorations, walls, the Create-only and train builds.
NOT_BUILDINGS = ("DECORATIONS", "MORE_DECORATIONS", "DEFENCES", "CREATE_ONLY", "TRAINS_ONLY", "MEGA_ONLY", "ARENAS")
# Decorations listed outside those lists (DECORATIONS is built from a stream in StarterBlueprints).
EXTRA_DECORATIONS = {"well", "well_2", "street_lamp", "park_bench", "fountain", "gazebo", "market_square", "chapel"}
# Builds that draw their own modded copy (storage_halls.py, 37.17: the Tom's Simple Storage versions): never dressed here.
OWN_COPIES = {"storage_hall", "terminal_nook", "warehouse"}

WOODS = ("dark_oak", "spruce", "birch", "oak")
FULL_FLOOR = re.compile(r"minecraft:(oak|spruce|dark_oak|birch)_planks")
# Cells a floor may have above it and still be a floor (furniture, carpets, people's room).
OPEN_ABOVE = re.compile(r"minecraft:(air|cave_air|.*_carpet|.*_pressure_plate|.*_stairs|.*_slab|.*_fence|.*_bed|"
                        r"lantern|candle|.*_candle|chest|barrel|.*_trapdoor|potted_.*|flower_pot|crafting_table|"
                        r"lectern|.*_door|ladder|bookshelf)")
SOLID_SKIP = re.compile(r"minecraft:(air|cave_air|structure_void|jigsaw|water)")


def hashed(x, y, z, salt=0):
    """A fixed number 0..99 for a cell (the same everywhere this runs)."""
    h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ (salt * 2654435761)
    h = (h ^ (h >> 13)) * 1274126177
    return (h ^ (h >> 16)) % 100


def entries(lists, source):
    """The ids (paths) of the entries in the named lists of StarterBlueprints."""
    names = {m.group(1): m.group(2) for m in re.finditer(r"Entry (\w+) = (?:new Entry\(AliveWorkplace\.id\(\"|\w+\(\")([a-z0-9_/]+)\"", source)}
    out = set()
    for lst in lists:
        m = re.search(r"List<Entry> " + lst + r" = List\.of\(([^;]*)\);", source)
        if m:
            for name in re.findall(r"[A-Z][A-Z0-9_]+", m.group(1)):
                if name in names:
                    out.add(names[name])
    return out


def buildings():
    """Every home and workshop: the top-level blueprints but decorations, walls and the builds of other mods."""
    with open(STARTER) as f:
        source = f.read()
    skip = entries(NOT_BUILDINGS, source) | EXTRA_DECORATIONS | OWN_COPIES
    found = []
    for path in sorted(glob.glob(os.path.join(STRUCTURES, "*.nbt"))):
        name = os.path.basename(path)[:-4]
        if name not in skip and not name.startswith("test_"):
            found.append(name)
    return found


class Template:
    def __init__(self, path):
        self.file = nbtlib.load(path)
        self.root = self.file
        self.palette = self.root["palette"]
        self.size = [int(v) for v in self.root["size"]]
        self.cells = {}
        for i, b in enumerate(self.root["blocks"]):
            p = tuple(int(v) for v in b["pos"])
            self.cells[p] = i
        self.index = {}
        for i, p in enumerate(self.palette):
            self.index[self.key(str(p["Name"]), {k: str(v) for k, v in p.get("Properties", {}).items()})] = i

    @staticmethod
    def key(name, props):
        return name + "[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]"

    def name(self, p):
        i = self.cells.get(p)
        if i is None:
            return "minecraft:structure_void"
        return str(self.palette[int(self.root["blocks"][i]["state"])]["Name"])

    def props(self, p):
        i = self.cells.get(p)
        if i is None:
            return {}
        return {k: str(v) for k, v in self.palette[int(self.root["blocks"][i]["state"])].get("Properties", {}).items()}

    def inside(self, p):
        return all(0 <= p[i] < self.size[i] for i in range(3))

    def state(self, name, props):
        k = self.key(name, props)
        if k not in self.index:
            entry = Compound({"Name": String(name)})
            if props:
                entry["Properties"] = Compound({a: String(b) for a, b in props.items()})
            self.palette.append(entry)
            self.index[k] = len(self.palette) - 1
        return self.index[k]

    def set(self, p, name, props=None):
        s = self.state(name, props or {})
        i = self.cells.get(p)
        if i is None:
            self.root["blocks"].append(Compound({"pos": List[Int]([Int(v) for v in p]), "state": Int(s)}))
            self.cells[p] = len(self.root["blocks"]) - 1
        else:
            b = self.root["blocks"][i]
            b["state"] = Int(s)
            if "nbt" in b:
                del b["nbt"]  # a swapped block keeps no data of the old one (builders never copy contents anyway)

    def solid(self, p):
        return not SOLID_SKIP.fullmatch(self.name(p))

    def covered(self, p, reach=10):
        x, y, z = p
        return any(self.solid((x, yy, z)) for yy in range(y + 2, min(self.size[1], y + reach)))


def basement_depth(t, spots):
    """How far up the file draws the build (B641): a build with a basement (26.15) is drawn with the basement at the
    bottom of its file, while its spots file counts y from the ground floor as the game does (StarterBlueprints
    moves the basement under the ground); the depth at which every spot's blocks are found in the file."""
    for d in range(0, 9):
        if all(t.name((s["pos"][0] + b["at"][0], s["pos"][1] + b["at"][1] + d, s["pos"][2] + b["at"][2])) == b["state"].split("[")[0]
               for s in spots for b in s["blocks"]):
            return d
    return 0


def spot_cells(name, t):
    """Every cell of the build's furniture spots (37.2), in the file's own coordinates: left exactly as drawn, so its
    spots file still matches; and over each table, where its crockery goes."""
    path = os.path.join(SPOTS, name + ".json")
    cells, tables = set(), []
    if os.path.exists(path):
        with open(path) as f:
            spots = json.load(f)["spots"]
            depth = basement_depth(t, spots)
            for s in spots:
                x, y, z = s["pos"]
                y += depth
                top = 0
                for b in s["blocks"]:
                    ax, ay, az = b["at"]
                    cells.add((x + ax, y + ay, z + az))
                    top = max(top, ay)
                if s["kind"] == "table":
                    tables.append(((x, y + top + 1, z), s.get("facing", "north")))
    return cells, tables


def main_wood(t):
    counts = {w: 0 for w in WOODS}
    for p in t.cells:
        n = t.name(p)
        for w in WOODS:
            if n.startswith("minecraft:" + w + "_") or n.startswith("minecraft:stripped_" + w + "_"):
                counts[w] += 1
                break
    return max(counts, key=lambda w: counts[w])


CARVED = {
    "minecraft:chest": "carved_wood:{w}_chest",
    "minecraft:bookshelf": "carved_wood:{w}_bookshelf",
    "minecraft:ladder": "carved_wood:{w}_ladder",
}
CARVED_WOODS = {"chest": ("oak", "spruce", "birch", "dark_oak"), "bookshelf": ("spruce", "birch", "dark_oak"),
                "ladder": ("spruce", "birch", "dark_oak")}
SIDES = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}


# What a pot may stand on: a built floor (never farmland, soil or a crop bed, which a placed block would trample).
FLOOR = re.compile(r"(minecraft|chipped|rechiseled):.*(planks|stone|bricks|tiles|paving|deepslate|andesite|diorite|granite|stone_bricks)")
FRAME = re.compile(r"minecraft:(stripped_)?\w+_(log|wood)")


def infill(t, p):
    """Daub for a wall cell of birch infill; a brace in a panel's bottom corner beside a post (leaning away from it)."""
    x, y, z = p
    below = t.name((x, y - 1, z))
    if FRAME.fullmatch(below):
        along = (1, 0) if t.name((x, y, z - 1)).endswith("air") or t.name((x, y, z + 1)).endswith("air") else (0, 1)
        left = t.name((x - along[0], y, z - along[1]))
        right = t.name((x + along[0], y, z + along[1]))
        if FRAME.fullmatch(left) and not FRAME.fullmatch(right):
            return "supplementaries:daub_brace", {"flipped": "false"}
        if FRAME.fullmatch(right) and not FRAME.fullmatch(left):
            return "supplementaries:daub_brace", {"flipped": "true"}
    return "supplementaries:daub", None


def beautify(name, src, out):
    t = Template(src)
    keep, tables = spot_cells(name, t)
    wood = main_wood(t)
    changed = {}

    def put(p, block, props=None):
        if p in keep or not t.inside(p):
            return False
        t.set(p, block, props)
        changed[block] = changed.get(block, 0) + 1
        return True

    cells = sorted(t.cells)
    for p in cells:
        if p in keep:
            continue
        n = t.name(p)
        x, y, z = p
        above = t.name((x, y + 1, z))
        # Supplementaries: plaster walls are daub.
        if n == "minecraft:white_concrete":
            put(p, "supplementaries:daub")
        elif n == "minecraft:birch_planks" and not all(t.solid((x + d[0], y, z + d[2])) for d in SIDES.values()):
            # The birch infill of a timber frame is daub, braced where a panel's bottom corner meets a post.
            put(p, *infill(t, p))
        # Chipped: a covered wooden floor (a full layer, room above it) is herringbone boards of its wood.
        elif FULL_FLOOR.fullmatch(n) and OPEN_ABOVE.fullmatch(above) and t.covered(p) \
                and all(t.solid((x + d[0], y, z + d[2])) for d in SIDES.values()):
            put(p, "chipped:herringbone_" + n.split(":")[1])
        # Rechiseled: polished andesite (floors, paving, trim) is andesite tiles; a covered cobblestone floor brick paving.
        elif n == "minecraft:polished_andesite":
            put(p, "rechiseled:andesite_tiles")
        elif n == "minecraft:cobblestone" and above.endswith("air") and t.covered(p) \
                and all(t.solid((x + d[0], y, z + d[2])) for d in SIDES.values()):
            put(p, "rechiseled:cobblestone_brick_paving")
        # Carved Wood: chests, shelves and ladders of the build's own wood.
        elif n in CARVED:
            kind = n.split(":")[1]
            if wood in CARVED_WOODS[kind] and not (kind == "chest" and wood == "oak"):
                props = {k: v for k, v in t.props(p).items() if k in ("facing", "type", "waterlogged")}
                put(p, CARVED[n].format(w=wood), props)

    # Small life, after the swaps (they look at the drawn build only).
    for p in cells:
        n = t.name(p)
        x, y, z = p
        # Handcrafted: books on a bookshelf in a room (one in three), pots beside barrels on a floor.
        if n.startswith("minecraft:bookshelf") or n.startswith("carved_wood:") and n.endswith("_bookshelf"):
            q = (x, y + 1, z)
            if t.inside(q) and t.name(q).endswith("air") and t.covered(p) and hashed(*p, salt=1) < 34:
                put(q, "handcrafted:stackable_book", {"books": "2", "facing": "north", "seed": str(hashed(*p, salt=2) % 100 + 1)})
        elif n == "minecraft:barrel" and hashed(*p, salt=3) < 50:
            for d in SIDES.values():
                q = (x + d[0], y, z + d[2])
                if t.inside(q) and t.name(q).endswith("air") and FLOOR.fullmatch(t.name((q[0], y - 1, q[2]))) and t.covered(q) \
                        and t.name((q[0], y + 1, q[2])).endswith("air"):
                    put(q, "handcrafted:terracotta_wide_pot")
                    break
    # Handcrafted: a set of crockery on every table.
    for top, facing in tables:
        if t.inside(top) and t.name(top).endswith("air"):
            put(top, "handcrafted:wood_crockery_combo", {"facing": facing, "waterlogged": "false"})

    os.makedirs(out, exist_ok=True)
    path = os.path.join(out, name + ".nbt")
    if not changed:
        # An open-air build with nothing to dress (the Habitat Garden): no copy, it is the same in both libraries.
        if os.path.exists(path):
            os.remove(path)
        return changed
    t.file.save(path, gzipped=True)
    return changed


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=OUT)
    ap.add_argument("names", nargs="*")
    args = ap.parse_args()
    names = args.names or buildings()
    total = 0
    for name in names:
        changed = beautify(name, os.path.join(STRUCTURES, name + ".nbt"), args.out)
        total += sum(changed.values())
        print(f"{name}: " + ", ".join(f"{k.split(':')[0]}:{k.split(':')[1]} {v}" for k, v in sorted(changed.items())))
    print(f"{len(os.listdir(args.out))} copies, {total} blocks dressed", file=sys.stderr)


if __name__ == "__main__":
    main()
