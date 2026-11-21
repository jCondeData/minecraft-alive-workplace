#!/usr/bin/env python3
"""The Stone Wall's self-check (ROADMAP 52.3): asserts every cell the brief pins (tools/blueprints/briefs/stone_wall.md)
against the candidates in build/rebuild/stone_wall/ (stone_wall.nbt, stone_wall_2.nbt). Exits 1 on any failure.

    python3 tools/blueprints/js/stone_wall/selfcheck.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from kitcheck import GATES, POI, Checks, collides, kept, read, short, stations, sturdy_top  # noqa: E402
import check as owner  # noqa: E402  (tools/blueprints/check.py, on the path kitcheck set)

ok = Checks()

# --- Level I --------------------------------------------------------------------------------------------------------
path, size, cells, states = read("stone_wall", "stone_wall")
print(f"stone_wall  {os.path.relpath(path)}")
ok(size == (7, 6, 3), f"plot exactly 7 x 6 x 3 (got {size})")
st = stations(cells, states)
walk = [(x, 3, 1) for x in range(7)]
ok(all(p in st for p in walk), f"the walk (x, 3, 1), x 0..6, is seven stations (stations: {st})")
ok(all((x, 4, 1) not in cells and (x, 5, 1) not in cells for x in range(7)), "(x, 4, 1) and (x, 5, 1) clear for every x")
ok(all((x, 3, 1) in st for x in (1, 3, 5)), "the siege tests' stands (1/3/5, 4, 1) are stations")
ok(not any(p[1] == 2 for p in st), "nothing standable at y 2 (no station one lower beside the walk)")
# the outer face, row z = 2
ok(all(all((x, y, 2) in cells and collides(cells[(x, y, 2)]) and sturdy_top(cells[(x, y, 2)], states[(x, y, 2)])
           for y in range(4)) for x in range(7)), "outer row z 2 solid full blocks from y 0 to y 3 in every column")
ok(all(p[2] <= 2 for p in cells), "nothing past z 2")
# the ladder column x = 3: rungs on (3, 0..4, 2), its top free at y 5 and 6
rungs = next(y for y in range(0, 12) if (3, y, 2) not in cells and (3, y + 1, 2) not in cells)
ok(rungs == 5 and all(collides(cells[(3, y, 2)]) for y in range(5)), f"ladder column (3, y, 2) gives exactly 5 rungs (got {rungs})")
ok((3, 4, 1) not in cells and (3, 5, 1) not in cells, "from the ladder's top the raider crosses inward: (3, 4..5, 1) clear")
# sand ramps: each outer column a plain vertical face, solid from the ground to its top, air over it, under 8 high
for x in range(7):
    top = max(y for (cx, y, cz) in cells if cx == x and cz == 2)
    ok(all((x, y, 2) in cells for y in range(top + 1)) and top + 1 < 8,
       f"outer column x {x}: plain face y 0..{top}, air over it, {top + 1} high (< 8)")
ok(all((x, 1, 2) in cells for x in (0, 3, 6)), "outer row y 1 solid at x 0, 3, 6 (the ring-of-walls siege test)")
ok(not any(short(n).endswith(GATES) for n in cells.values()), "no gate block (door, fence gate, iron bars)")
ok(not any(t in short(n) for n in cells.values() for t in POI), "no villager point of interest")
ok(len(cells) > 10, f"more than 10 non-gate blocks ({len(cells)})")
# the way up from the village side: the owner's reach rule walks a player from the ground up to the walk
blocks = {p: (n, states[p]) for p, n in cells.items()}
nodes, seen = owner.reachable(blocks, *size)
ok(all((x, 1, 8) in seen for x in range(7)), "a player walks from the village ground up the flight onto every walk cell")
ok(not owner.reach_problems(blocks, *size), "check.py: no standing level without a way up")
base_cells, base_states, base_st = cells, states, st
print(f"  stations: {len(st)} {st}")

# --- Level II -------------------------------------------------------------------------------------------------------
path, size, cells, states = read("stone_wall", "stone_wall_2")
print(f"stone_wall_2  {os.path.relpath(path)}")
ok(size == (7, 9, 4), f"plot exactly 7 x 9 x 4 (got {size})")
st = stations(cells, states)
row = [p for p in st if p[1] == 3 and p[2] == 1]
ok(len(row) == 7, f"exactly seven stations at y 3, z 1 (got {len(row)})")
ok(len(st) == 7, f"and no other stations (all stations: {st})")
ok(all((x, 4, 1) not in cells and (x, 5, 1) not in cells for x in range(7)), "the walk's (x, 4..5, 1) still empty")
ok(not any(p[1] == 2 for p in st), "nothing standable at y 2 beside the walk")
ok(any(short(n).endswith("_stairs") and y >= 6 for (x, y, z), n in cells.items()), "a stairs block at y >= 6 (the roof)")
ok(all(any(y > 5 and (x, y, 1) in cells for y in range(6, 9)) for x in range(7)), "the walk roofed over in every column")
holes_ = [x for x in range(7) if (x, 4, 3) not in cells]
boards = [x for x in range(7) if (x, 4, 3) in cells]
ok(len(holes_) >= 2 and len(boards) >= 2, f"gallery floor (x, 4, 3): {len(holes_)} murder holes {holes_}, {len(boards)} boards {boards}")
ok(all(not any((x, y, 3) in cells for y in range(0, 4)) for x in holes_), "every murder hole open straight down to the foot")
same, total = kept(base_cells, base_states, cells, states)
ok(same >= 0.6 * total, f"keeps {same} of Level I's {total} blocks exactly ({100 * same // total}% >= 60%)")
ok(not any(short(n).endswith(GATES) for n in cells.values()), "no gate block")
ok(not any(t in short(n) for n in cells.values() for t in POI), "no villager point of interest")
blocks = {p: (n, states[p]) for p, n in cells.items()}
ok(not owner.reach_problems(blocks, *size), "check.py: no standing level without a way up")
print(f"  stations: {len(st)} {st}")

print("PASS" if not ok.failed else f"{ok.failed} FAILED")
sys.exit(1 if ok.failed else 0)
