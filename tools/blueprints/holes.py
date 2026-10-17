#!/usr/bin/env python3
"""
Finds the holes a player would see in a shipped blueprint (.nbt), without the game:

  roof   an empty cell (air, or a structure void) in a roof: open to the sky, ringed at its level by the building with a
         roof block (stairs or a slab) beside it — a pit in the roof surface, not a courtyard.
  meet   an empty cell open to the sky and empty under it, with roof blocks on two opposite sides at its level or just
         above: a gap right through, where two roofs (a wing's and the main one's, a cross gable's) should meet.
  stray  a single empty cell wedged in the build: a block above and below it and blocks on both sides along a line
         (a wall, a beam, a fence or wall line), or a post under it and beams on two sides (the corner of a frame),
         so the neighbours say a block belongs there.
  peek   an empty cell shut in under a roof (a stair or slab right over it, or one above that) and seen from outside
         over the open top half of a roof stair beside it: where one roof meets another at a corner or a valley and a
         stair, shaped as the game shapes it, leaves a quarter of a block open into the attic behind (kit.seal_peeks
         fills them).

Not reported (whitelisted by pattern): chimney tops and anything over the highest block of a column next to it (open
sky outside), courtyards and yards (an open patch of more than four cells at its level, or any patch below y 3),
pockets of an attic under a roof slope (out of sight), doorways and windows (two or
more empty cells stacked: an opening, not a missing block), and cells by a door, a ladder, a bed or a lantern.

    python3 tools/blueprints/holes.py            # every blueprint under src/main/resources
    python3 tools/blueprints/holes.py starter_cottage_3 graveyard_2   # names (or paths) to check
    python3 tools/blueprints/holes.py --summary  # counts only
    python3 tools/blueprints/holes.py --all      # with the ones in ALLOW (on purpose) too

Exits 1 when anything not in ALLOW is found.
"""
import os
import sys
import nbtlib

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
STRUCTURES = os.path.join(ROOT, "src/main/resources/data/aliveworkplace/structure")

OPEN = {"minecraft:air", "minecraft:cave_air", "minecraft:structure_void"}
# Blocks too small to close a gap: what sits in a cell doesn't make it a wall.
THIN = ("lantern", "torch", "carpet", "pressure_plate", "flower_pot", "potted_", "button", "_sign", "banner", "rail",
        "candle", "ladder", "vine", "lever", "tripwire", "chain", "_bars", "pane", "trapdoor", "flower", "sapling",
        "grass", "fern", "bush", "tulip", "poppy", "dandelion", "orchid", "allium", "bluet", "daisy", "cornflower",
        "lily", "rose", "peony", "lilac", "sunflower", "campfire", "scaffolding", "snow", "water", "lava", "fire",
        "head", "skull", "bell", "redstone_wire", "string", "cobweb", "glow_lichen", "moss_carpet", "leaves")
# Next to these an empty cell is on purpose (a door's swing, a ladder's climb, the space over a bed, a lantern's hang).
NEAR_OK = ("_door", "ladder", "_bed", "lantern", "fence_gate", "scaffolding", "jigsaw")
# Found on purpose: (blueprint, position) -> why. Listed with --all; they don't fail the run.
LOG_NOTCH = "a log-cabin corner: the crossing logs stick out on every other course"
TENT = "inside a tent: a low space by the bed or the hay"
FOUNTAIN = "the garden fountain's top under the house's back eave, not a roof"
EMBRASURE = "an embrasure between the hoarding's posts"
ALLOW = {
    ("fletchers_lodge", (8, 2, 8)): LOG_NOTCH, ("fletchers_lodge_2", (8, 2, 2)): LOG_NOTCH,
    ("fletchers_lodge_2", (8, 2, 8)): LOG_NOTCH,
    ("manor_2", (9, 4, 17)): FOUNTAIN, ("manor_3", (9, 4, 17)): FOUNTAIN, ("manor_4", (9, 4, 17)): FOUNTAIN,
    ("stone_wall_2", (1, 5, 2)): EMBRASURE, ("stone_wall_2", (5, 5, 2)): EMBRASURE,
    ("camp/bandit_camp", (5, 1, 11)): TENT, ("camp/colony_camp", (2, 2, 5)): TENT, ("camp/colony_camp", (2, 2, 9)): TENT,
    ("camp/colony_camp", (12, 2, 5)): TENT, ("camp/colony_camp", (12, 2, 9)): TENT,
    ("camp/settlers_camp", (3, 2, 4)): TENT, ("camp/settlers_camp", (7, 2, 4)): TENT, ("camp/war_camp", (9, 1, 15)): TENT,
    ("camp/coven_hut", (7, 1, 4)): "under the stilted deck, behind its steps: out of sight",
}
# Village Hall III's tower rises out of the meeting room's roof: on its west, under the cornice and the cap's eave, the
# air against the tower wall is open over the meeting room's roof (village_hall.py leaves that column open: closed,
# it stood out as a stray wall a block off the tower)
TOWER_EAVE = "open air under the tower's west cornice and eave, over the meeting room's roof"
ALLOW.update({("village_hall_3", (17, y, z)): TOWER_EAVE for y in range(5, 16) for z in range(4, 11)})
# The Lantern Pergola (26.14) is an open lattice of slats with azalea grown over some of the gaps, not a roof
ALLOW.update({("lantern_pergola", p): "a pergola's open slats, not a roof"
              for p in ((2, 5, 1), (1, 5, 5), (2, 5, 5), (3, 5, 3))})


def load(path, props=False):
    """(size, cells): cells maps (x, y, z) to a block id, or to None for a structure void (left out of the file).
    props=True: (size, cells, states), states mapping each cell to its block's properties (a dict)."""
    root = nbtlib.load(path)
    size = tuple(int(v) for v in root["size"])
    palette = [str(p["Name"]) for p in root["palette"]]
    palette_props = [{k: str(v) for k, v in p.get("Properties", {}).items()} for p in root["palette"]]
    cells, states = {}, {}
    for e in root["blocks"]:
        x, y, z = (int(v) for v in e["pos"])
        cells[(x, y, z)] = palette[int(e["state"])]
        states[(x, y, z)] = palette_props[int(e["state"])]
    return (size, cells, states) if props else (size, cells)


_DIRS = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
_OPP = {"north": "south", "south": "north", "west": "east", "east": "west"}
_CW = {"north": "east", "east": "south", "south": "west", "west": "north"}
_CCW = {v: k for k, v in _CW.items()}


def _open_sides(facing, shape):
    """The sides of a (bottom-half) stair where its top half doesn't close the whole face: all but the back, and for
    an inner corner all but the back and the side its extra quarter is on (an outer corner is open all round)."""
    if shape == "inner_left":
        return {_OPP[facing], _CW[facing]}
    if shape == "inner_right":
        return {_OPP[facing], _CCW[facing]}
    if shape in ("outer_left", "outer_right"):
        return set(_DIRS)
    return {_OPP[facing], _CW[facing], _CCW[facing]}


def peeks(size, cells, states):
    """[((x, y, z), cover)] the "peek" holes: an empty cell beside a roof stair that is open to the sky, on a side the
    stair's top half leaves open, shut in under a roof (a stair or a slab right over it, or over the empty cell above
    it: the cover) and on all four sides (none of them open sky or outside the build: then it's a nook under the eaves,
    not a hole into the attic), up from y 3, and not by a door, a ladder, a bed or a lantern."""
    w, h, d = size

    def name(p):
        return cells.get(p, "minecraft:structure_void") or "minecraft:structure_void"

    def inside(x, y, z):
        return 0 <= x < w and 0 <= y < h and 0 <= z < d

    top = {}
    for (x, y, z), b in cells.items():
        if b not in OPEN and b is not None:
            top[(x, z)] = max(top.get((x, z), -1), y)
    found = {}
    for (x, y, z), b in cells.items():
        if not b or not b.endswith("_stairs"):
            continue
        p = states.get((x, y, z), {})
        if p.get("half") != "bottom" or top.get((x, z)) != y:
            continue
        for side in _open_sides(p.get("facing", "north"), p.get("shape", "straight")):
            dx, dz = _DIRS[side]
            q = (x + dx, y, z + dz)
            if y < 3 or not inside(*q) or name(q) not in OPEN or q in found:
                continue
            cover = next((c for c in ((q[0], y + 1, q[2]), (q[0], y + 2, q[2])) if name(c) not in OPEN), None)
            if cover is None or not (name(cover).endswith("_stairs") or name(cover).endswith("_slab")):
                continue
            if any(not inside(q[0] + ex, y, q[2] + ez) or (name((q[0] + ex, y, q[2] + ez)) in OPEN
                                                           and top.get((q[0] + ex, q[2] + ez), -1) < y)
                   for ex, ez in _DIRS.values()):
                continue
            if any(t in name((q[0] + i, y + j, q[2] + k)) for i in (-1, 0, 1) for j in (-1, 0, 1) for k in (-1, 0, 1)
                   for t in NEAR_OK):
                continue
            found[q] = name(cover)
    return sorted(found.items())


def scan(size, cells, only_slots=False):
    """[(kind, (x, y, z), why)] for one blueprint. only_slots: just the slots under an overhang, as
    [(x, y, z), (dx, dz), down, up]: the wall is towards (dx, dz), the lower roof's block at y = down, the overhang's at
    y = up (kit.seal_slots fills them)."""
    w, h, d = size

    def at(x, y, z):
        if not (0 <= x < w and 0 <= y < h and 0 <= z < d):
            return "outside"
        return cells.get((x, y, z), "minecraft:structure_void")

    def empty(x, y, z):
        return at(x, y, z) in OPEN

    def solid(x, y, z):
        b = at(x, y, z)
        return b not in OPEN and b != "outside" and not any(t in b for t in THIN)

    def roofish(x, y, z):
        b = at(x, y, z)
        return b.endswith("_stairs") or b.endswith("_slab")

    def framish(x, y, z):
        b = at(x, y, z)
        return b.endswith("_log") or b.endswith("_wood") or b.endswith("_fence") or b.endswith("_wall")

    def slot_side(x, y, z, down):
        """A wall on one side and, on the other, the open sky over the same lower roof running on outwards: the cell is
        in the slot where a lower roof slides in under a higher one's overhang (a canopy tucked under an overhang, with
        nothing beyond it, is fine)."""
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            ox, oz = x - dx, z - dz
            if solid(x + dx, y, z + dz) and not roofish(x + dx, y, z + dz) and sky(ox, y, oz):
                t = top.get((ox, oz), -1)
                if t >= 0 and roofish(ox, t, oz) and abs(t - down) <= 1:
                    return dx, dz
        return None

    def near_ok(x, y, z):
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    if any(t in at(x + dx, y + dy, z + dz) for t in NEAR_OK):
                        return True
        return False

    # the highest non-empty block of each column (-1: none)
    top = {}
    for (x, y, z), b in cells.items():
        if b not in OPEN and b is not None:
            top[(x, z)] = max(top.get((x, z), -1), y)

    def sky(x, y, z):
        return empty(x, y, z) and y > top.get((x, z), -1)

    def alley(x, y, z, ax, az):
        """Whether the gap at (x, y, z) is the sky over a way between two buildings: empty straight down to the ground
        and, low down, open in a line (along ax, az) out of the site."""
        for yy in range(y, 0, -1):
            if not empty(x, yy, z):
                return False
        for s in (1, -1):
            i = 1
            while empty(x + s * i * ax, 1, z + s * i * az):
                i += 1
            if at(x + s * i * ax, 1, z + s * i * az) == "outside":
                return True
        return False

    found = []
    seen_roof = set()
    for y in range(1, 0 if only_slots else h):
        # sky-open cells at this level, grouped into patches (4-connected)
        cells_y = {(x, z) for x in range(w) for z in range(d) if sky(x, y, z)}
        done = set()
        for start in sorted(cells_y):
            if start in done:
                continue
            patch, todo, edge = {start}, [start], False
            while todo:
                x, z = todo.pop()
                for nx, nz in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
                    if not (0 <= nx < w and 0 <= nz < d):
                        edge = True
                    elif (nx, nz) in cells_y and (nx, nz) not in patch:
                        patch.add((nx, nz))
                        todo.append((nx, nz))
                    elif (nx, nz) not in cells_y and top.get((nx, nz), -1) < y:
                        edge = True  # runs into open air under the sky: the outside, not a pit
            done |= patch
            if edge or len(patch) > 4 or y < 3:  # a yard, or a flower bed or a path at ground level
                continue
            for x, z in patch:
                floor = at(x, y - 1, z)
                standing = floor.endswith("_slab") or (solid(x, y - 1, z) and not floor.endswith("_stairs"))
                if any(roofish(x + dx, y + dy, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) for dy in (0, 1)) \
                        and not near_ok(x, y, z) and not standing:  # (on a floor: a tower top between merlons)
                    found.append(("roof", (x, y, z), "pit in the roof, open to the sky"))
                    seen_roof.add((x, y, z))
    for (x, y, z) in [(x, y, z) for x in range(w) for y in range(1, h) for z in range(d)]:
        if not empty(x, y, z) or (x, y, z) in seen_roof or near_ok(x, y, z):
            continue
        if not sky(x, y, z):
            up = next((yy for yy in range(y + 1, h) if not empty(x, yy, z)), None)
            down = next((yy for yy in range(y - 1, -1, -1) if not empty(x, yy, z)), None)
            if up is not None and down is not None and roofish(x, up, z) and roofish(x, down, z) and up - down > 1 \
                    and down >= 3 and slot_side(x, y, z, down):
                if only_slots:
                    found.append(((x, y, z), slot_side(x, y, z, down), down, up))
                else:
                    found.append(("meet", (x, y, z), "slot between a lower roof and the overhang of a higher one"))
                continue
        if only_slots:
            continue
        if sky(x, y, z):
            for (ax, az) in ((1, 0), (0, 1)):
                sides = [any(roofish(x + s * ax, y + dy, z + s * az) for dy in (0, 1)) for s in (1, -1)]
                under = next((yy for yy in range(y - 1, -1, -1) if not empty(x, yy, z)), -1)
                if all(sides) and empty(x, y - 1, z) and not alley(x, y, z, az, ax) and not roofish(x, under, z):
                    # (over another roof's slope it's a valley between two gables, not a gap)
                    found.append(("meet", (x, y, z), "gap between two roofs"))
                    break
            continue
        hatch = any("trapdoor" in at(x + dx, y, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if solid(x, y + 1, z) and solid(x, y - 1, z) and not roofish(x, y + 1, z) and not hatch:
            # (under a stair or a slab it's a pocket of the attic under the slope, out of sight)
            frame = [solid(x + dx, y, z + dz) and framish(x + dx, y, z + dz) for dx, dz in ((1, 0), (0, 1), (-1, 0), (0, -1))]
            if framish(x, y - 1, z) and any(frame[i] and frame[(i + 1) % 4] for i in range(4)):
                found.append(("stray", (x, y, z), "gap in the corner of a frame (posts and beams)"))
                continue
            for (ax, az) in ((1, 0), (0, 1)):
                if solid(x + ax, y, z + az) and solid(x - ax, y, z - az):
                    shut = sum(solid(x + dx, y, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                    # an empty cell in a line: both ends of the line and the cells over and under it are blocks, and
                    # it's shut on three sides or more (a one-wide passage or crawl space is open on two)
                    if shut >= 3:
                        found.append(("stray", (x, y, z), "empty cell wedged between blocks"))
                    break
    return found


def basements():
    """{name: depth} of the builds with a basement (26.15), read from StarterBlueprints.java: their files hold the
    basement at the bottom, so the checks here move them back down (the basement, under y 0, is left out: it has no
    roof to hole)."""
    import re
    java = open(os.path.join(ROOT, "src/main/java/io/github/jcondedata/aliveworkplace/blueprint/StarterBlueprints.java")).read()
    found = {m.group(1): int(m.group(2)) for m in re.finditer(r'AliveWorkplace\.id\("([^"]+)"\), new Vec3i\([^)]*\), (\d+)\)', java)}
    found.update({m.group(1): int(m.group(2)) for m in re.finditer(r'workplace\("(\w+)", \d+, \d+, \d+, (\d+)\)', java)})
    return found


def blueprints(args):
    if args:
        for a in args:
            if os.path.exists(a):
                yield a
            else:
                for dirpath, _, files in os.walk(STRUCTURES):
                    if a + ".nbt" in files:
                        yield os.path.join(dirpath, a + ".nbt")
        return
    for dirpath, _, files in sorted(os.walk(STRUCTURES)):
        for f in sorted(files):
            # a Wonder's stage part stands on the stages before it: on its own it's all holes
            if f.endswith(".nbt") and not (os.path.basename(dirpath) != "wonder" and "wonder" in dirpath and f.startswith("stage_")):
                yield os.path.join(dirpath, f)


def main(argv):
    summary = "--summary" in argv
    show_all = "--all" in argv
    args = [a for a in argv if not a.startswith("--")]
    total = {"roof": 0, "meet": 0, "stray": 0, "peek": 0}
    builds = allowed = 0
    under = basements()
    for path in blueprints(args):
        size, cells, states = load(path, props=True)
        name = os.path.relpath(path, STRUCTURES)[:-4]
        deep = under.get(name, 0)
        if deep:
            size = (size[0], size[1] - deep, size[2])
            cells = {(x, y - deep, z): c for (x, y, z), c in cells.items() if y >= deep}
            states = {(x, y - deep, z): c for (x, y, z), c in states.items() if y >= deep}
        found = scan(size, cells) + [("peek", q, f"shut in under {cover.split(':')[-1]}, seen past a roof stair's open top")
                                     for q, cover in peeks(size, cells, states)]
        kept = [f for f in found if (name, f[1]) not in ALLOW]
        allowed += len(found) - len(kept)
        found = found if show_all else kept
        if found:
            builds += 1
            kinds = {k: sum(1 for f in found if f[0] == k) for k in total}
            for k, n in kinds.items():
                total[k] += n
            print(f"{name}: " + ", ".join(f"{n} {k}" for k, n in kinds.items() if n))
            if not summary:
                for kind, pos, why in found:
                    print(f"  {kind} {pos} {why}")
    print(f"TOTAL {sum(total.values())} ({total['roof']} roof, {total['meet']} meet, {total['stray']} stray, {total['peek']} peek) in {builds} blueprints"
          + ("" if show_all else f"; {allowed} more on purpose (ALLOW; --all lists them)"))
    return 1 if any(total.values()) else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
