#!/usr/bin/env python3
"""The Wall Tower's self-check (ROADMAP 52.3): asserts what the brief pins (tools/blueprints/briefs/wall_tower.md)
against the candidates in build/rebuild/wall_tower/ (wall_tower.nbt, wall_tower_2.nbt). Exits 1 on any failure.

    python3 tools/blueprints/js/wall_tower/selfcheck.py
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "stone_wall"))
from kitcheck import POI, Checks, kept, read, short, stations  # noqa: E402
import check as owner  # noqa: E402  (tools/blueprints/check.py, on the path kitcheck set)

ok = Checks()
SIDES = {"z 0": lambda p: p[2] == 0, "z 4": lambda p: p[2] == 4, "x 0": lambda p: p[0] == 0, "x 4": lambda p: p[0] == 4}


def common(cells, states, size):
    doors = [(p, states[p].get("half")) for p, n in cells.items() if short(n).endswith("_door")]
    lower = [p for p, h in doors if h == "lower"]
    ok(len(lower) == 1 and len(doors) == 2, f"exactly one door, both halves ({doors})")
    ok(all(0 <= x <= 4 and 0 <= y <= 3 and 0 <= z <= 4 for x, y, z in lower), "its lower half within x 0-4, y 0-3, z 0-4")
    ok(all(z == 0 for x, y, z in lower), "the door on the z 0 face (the village side)")
    ok(not any(short(n).endswith(("fence_gate", "iron_bars")) for n in cells.values()), "no fence gate, no iron bars")
    ok(not any(t in short(n) for n in cells.values() for t in POI), "no bed, no job block or other POI")
    blocks = {p: (n, states[p]) for p, n in cells.items()}
    ok(not owner.reach_problems(blocks, *size), "check.py: every standing level has a way up")
    st = stations(cells, states)
    ok(len(st) > 0, f"{len(st)} battle stations")
    ok(all(min(x + 1, 5 - x, z + 1, 5 - z) <= 3.5 for x, y, z in st), "every station within 3.5 (flat) of the ground round the foot")
    nodes, seen = owner.reachable(blocks, *size)
    ok(all((x, z, 2 * (y + 1)) in seen for x, y, z in st), "a player climbs from the door to stand on every station")
    return st


# --- Level I --------------------------------------------------------------------------------------------------------
path, size, cells, states = read("wall_tower", "wall_tower")
print(f"wall_tower  {os.path.relpath(path)}")
ok(size == (5, 10, 5), f"plot exactly 5 x 10 x 5 (got {size})")
st = common(cells, states, size)
ys = {y for _, y, _ in st}
ok(len(ys) == 1, f"all stations on one level, the top (stood on at y {sorted(y + 1 for y in ys)})")
ok(max(ys) == max(y for (_, y, _) in st), "and it is the highest station level")
for side, on in SIDES.items():
    ok(any(on(p) for p in st), f"side {side} has open shooting cells (crenels) at standing height: {[p for p in st if on(p)]}")
print(f"  stations: {len(st)} {st}")
base_cells, base_states = cells, states

# --- Level II -------------------------------------------------------------------------------------------------------
path, size, cells, states = read("wall_tower", "wall_tower_2")
print(f"wall_tower_2  {os.path.relpath(path)}")
ok(size == (5, 12, 5), f"plot exactly 5 x 12 x 5 (got {size})")
st = common(cells, states, size)
gallery = max(y for _, y, _ in st)
g = [p for p in st if p[1] == gallery]
for side, on in SIDES.items():
    n = [p for p in g if on(p)]
    ok(len(n) == 2, f"gallery (stood on at y {gallery + 1}): exactly two stations on row {side}: {n}")
ok(sum(1 for p in g if any(on(p) for on in SIDES.values())) >= 8 - 0, "at least 8 stations on the outer rows")
ok(all(any((x, yy, z) in cells for yy in range(y + 3, size[1])) for x, y, z in g),
   "every gallery station roofed (a block 3 or more over the block stood on)")
same, total = kept(base_cells, base_states, cells, states)
ok(same >= 0.6 * total, f"keeps {same} of Level I's {total} blocks exactly ({100 * same // total}% >= 60%)")
print(f"  stations: {len(st)} {st}")

print("PASS" if not ok.failed else f"{ok.failed} FAILED")
sys.exit(1 if ok.failed else 0)
