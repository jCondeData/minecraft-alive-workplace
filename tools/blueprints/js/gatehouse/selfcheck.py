#!/usr/bin/env python3
"""The Gatehouse's self-check (ROADMAP 49): asserts every cell and rule the brief pins
(tools/blueprints/briefs/gatehouse.md) against the candidates in build/rebuild/gatehouse/ (gatehouse.nbt,
gatehouse_2.nbt), and works out what each battle station sees the way BattleStations.view does (a raider's eyes on
the ground 2 to 16 blocks out from the breach (5, 1, 3), in the archer's line of sight). Exits 1 on any failure.

    python3 tools/blueprints/js/gatehouse/selfcheck.py
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "stone_wall"))
from kitcheck import GATES, POI, Checks, collides, kept, read, short, stations  # noqa: E402
import check as owner  # noqa: E402  (tools/blueprints/check.py, on the path kitcheck set)

ok = Checks()


# --- what blocks a line of sight (ClipContext.Block.COLLIDER), as boxes in a cell ------------------------------------
def boxes(name, st):
    s = short(name)
    if not collides(name) or s.endswith(("_carpet", "lantern", "chain", "ladder", "_banner")) or "torch" in s:
        return [] if not s.endswith("_carpet") else [(0, 0, 0, 1, 1 / 16, 1)]
    if s.endswith("_slab"):
        t = st.get("type")
        return [(0, 0, 0, 1, 0.5, 1)] if t == "bottom" else [(0, 0.5, 0, 1, 1, 1)] if t == "top" else [(0, 0, 0, 1, 1, 1)]
    if s.endswith("_stairs"):
        top = st.get("half") == "top"
        half = (0, 0.5, 0, 1, 1, 1) if top else (0, 0, 0, 1, 0.5, 1)
        y0, y1 = (0, 0.5) if top else (0.5, 1)
        q = {"north": (0, y0, 0, 1, y1, 0.5), "south": (0, y0, 0.5, 1, y1, 1),
             "west": (0, y0, 0, 0.5, y1, 1), "east": (0.5, y0, 0, 1, y1, 1)}[st.get("facing", "north")]
        return [half, q]
    if s.endswith(("_wall", "_fence")) or "fence_gate" in s and st.get("open") != "true":
        return [(0.25, 0, 0.25, 0.75, 1.5, 0.75)] if not "gate" in s else [(0, 0, 0.375, 1, 1.5, 0.625)]
    if "fence_gate" in s:
        return []
    if s.endswith(("_pane", "iron_bars")):
        return [(0.4375, 0, 0.4375, 0.5625, 1, 0.5625)]
    if s.endswith("_trapdoor"):
        if st.get("open") == "true":
            f = st.get("facing")
            return [{"north": (0, 0, 0.8125, 1, 1, 1), "south": (0, 0, 0, 1, 1, 0.1875),
                     "west": (0.8125, 0, 0, 1, 1, 1), "east": (0, 0, 0, 0.1875, 1, 1)}[f]]
        return [(0, 0.8125, 0, 1, 1, 1)] if st.get("half") == "top" else [(0, 0, 0, 1, 0.1875, 1)]
    if "chest" in s:
        return [(0.0625, 0, 0.0625, 0.9375, 0.875, 0.9375)]
    return [(0, 0, 0, 1, 1, 1)]


def clear_line(cells, states, a, b, steps=600):
    for i in range(1, steps):
        t = i / steps
        p = [a[k] + (b[k] - a[k]) * t for k in range(3)]
        cell = tuple(int(v // 1) for v in p)
        if cell in cells:
            fx, fy, fz = p[0] - cell[0], p[1] - cell[1], p[2] - cell[2]
            for (x0, y0, z0, x1, y1, z1) in boxes(cells[cell], states[cell]):
                if x0 <= fx <= x1 and y0 <= fy <= y1 and z0 <= fz <= z1:
                    return False
    return True


def view(cells, states, st, depth, breach=(5, 1, 3)):
    """How many of the 15 spots out in front of the breach a station standing at st sees (BattleStations.view). The
    ground beyond the plot is one step lower than the way (stood on at y 0); inside the plot, the drawn floor."""
    eye = (st[0] + 0.5, st[1] + 1.62, st[2] + 0.5)
    seen = 0
    for d in range(2, 17):
        z = breach[2] + d
        stand = 0
        if z < depth:  # inside the plot: the first spot to stand on from y 3 down
            stand = next((y for y in range(3, -1, -1) if (breach[0], y - 1, z) in cells and (breach[0], y, z) not in cells
                          and (breach[0], y + 1, z) not in cells), 0)
        at = (breach[0] + 0.5, stand + 1.5, z + 0.5)
        seen += clear_line(cells, states, eye, at)
    return seen


def gate_blocks(cells):
    return {p: n for p, n in cells.items() if short(n).endswith(GATES)}


def pinned(cells, states, z_gates, z_bars, label):
    for x in (4, 5, 6):
        g, b = (x, 1, z_gates), (x, 3, z_bars)
        ok(short(cells.get(g)) == "spruce_fence_gate" and states[g].get("open") == "false"
           and states[g].get("facing") in ("north", "south"), f"{label}: shut spruce fence gate across the way at {g}")
        ok(short(cells.get(b)) == "iron_bars", f"{label}: iron bars (portcullis drawn up) at {b}")
        ok((x, 1, z_bars) not in cells and (x, 2, z_bars) not in cells, f"{label}: ({x}, 1..2, {z_bars}) empty under the bars")
        ok((x, 0, z_bars) in cells and collides(cells[(x, 0, z_bars)]), f"{label}: ({x}, 0, {z_bars}) drawn solid (the bars stop there)")


# --- Level I --------------------------------------------------------------------------------------------------------
path, size, cells, states = read("gatehouse", "gatehouse")
print(f"gatehouse  {os.path.relpath(path)}")
ok(size == (11, 16, 5), f"plot exactly 11 x 16 x 5 (got {size})")
pinned(cells, states, 2, 3, "I")
gb = gate_blocks(cells)
ok(len(gb) == 6 and not any("door" in short(n) for n in gb.values()),
   f"exactly 6 gate blocks (3 gates, 3 bars), no door: {sorted(gb)}")
ok(all((x, y, 4) not in cells for x in (4, 5, 6) for y in (1, 2, 3)), "the ram's stand (4..6, 1..3, 4) empty")
ok(all((x, 0, 4) in cells for x in (4, 5, 6)), "a floor under the ram's stand (4..6, 0, 4)")
ok(all((5, y, z) not in cells or short(cells[(5, y, z)]).endswith(GATES) for y in (1, 2) for z in range(5)),
   "headroom: only gate blocks over the middle way (5, 1..2, 0..4)")
ok(all((5, 0, z) in cells for z in range(5)), "the way paved at y 0 from z 0 to z 4")
# sealed: row z 3 shut at y 1..3 across the plot (the portcullis, dropped, closes x 4..6); the ends at z <= 2
def shut(p):
    return p in cells and collides(cells[p]) and not short(cells[p]).endswith(("ladder", "_trapdoor", "_pane"))


for x in [x for x in range(11) if x not in (4, 5, 6)]:  # in the yard's wall line (z 3), or the tower's field wall (z 4)
    ok(all(shut((x, y, 3)) or shut((x, y, 4)) for y in (1, 2, 3)), f"column x {x} shut at z 3 or z 4, y 1..3")
for x in (0, 10):
    ok(all((x, y, z) in cells and collides(cells[(x, y, z)]) for y in (1, 2, 3) for z in (0, 1, 2)), f"end column x {x} shut at z 0..2, y 1..3")
outside_steps = [(p, n) for p, n in cells.items() if p[2] == 4 and p[1] >= 1 and short(n).endswith(("_stairs", "_slab", "ladder"))
                 and p[1] <= 4]
ok(not outside_steps, f"no stair, slab or ladder a raider could climb on the field face (z 4, y 1..4): {outside_steps}")
st = stations(cells, states)
stands = [(x, y + 1, z) for x, y, z in st]
ok(all(s[1] == 6 for s in stands), f"every station stands at y 6 (stands: {stands})")
ok((4, 6, 2) in stands and (6, 6, 2) in stands, "stations standing at (4, 6, 2) and (6, 6, 2)")
ok(all((x, y, 2) not in cells for x in (4, 6) for y in (6, 7)), "(4 / 6, 6..7, 2) empty")
views = {s: view(cells, states, s, 5) for s in stands}
best = max(views.values())
mid = view(cells, states, (5, 6, 2), 5)
ok(views[(4, 6, 2)] == best and views[(6, 6, 2)] == best, f"the stations over the gate see the most ({views})")
ok(views[(4, 6, 2)] > mid, f"and strictly more than the spot standing at (5, 6, 2) ({views[(4, 6, 2)]} > {mid})")
ok(not any(t in short(n) for n in cells.values() for t in POI), "no villager point of interest")
blocks = {p: (n, states[p]) for p, n in cells.items()}
problems = owner.reach_problems(blocks, *size)
ok(not problems, f"check.py: every standing level has a way up {problems}")
nodes, seen = owner.reachable(blocks, *size)
ok(any(n[0] == 4 and n[1] == 2 and n[2] == 12 for n in seen), "a player walks up from the ground onto the loggia's station (4, 6, 2)")
base_cells, base_states = cells, states
print(f"  stations ({len(st)}): stands {stands}")
print(f"  views: {views}; the spot at (5, 6, 2) sees {mid}")

# --- Level II -------------------------------------------------------------------------------------------------------
path, size, cells, states = read("gatehouse", "gatehouse_2")
print(f"gatehouse_2  {os.path.relpath(path)}")
ok(size == (11, 16, 10), f"plot exactly 11 x 16 x 10 (got {size})")
pinned(cells, states, 2, 3, "II inner")
pinned(cells, states, 8, 7, "II barbican")
gb = gate_blocks(cells)
ok(len(gb) == 12 and sum("fence_gate" in short(n) for n in gb.values()) == 6, f"exactly 12 gate blocks, 6 of them fence gates")
ok(not any(4 <= p[2] <= 6 for p in gb), "no gate block between the lines (z 4..6)")
ok(all((x, y, z) not in cells for x in (4, 5, 6) for y in (1, 2, 3) for z in (4, 5, 6)), "the passage (4..6, 1..3, 4..6) empty")
ok(all((x, y, 9) not in cells for x in (4, 5, 6) for y in (1, 2, 3)) and all((x, 0, 9) in cells for x in (4, 5, 6)),
   "the outer stand (4..6, 1..3, 9) empty on a floor")
ok(all((x, y, 8) not in cells for x in (4, 5, 6) for y in (2, 3)), "(4..6, 2..3, 8) empty over the outer gates")
roof = [(x, z) for x in (4, 5, 6) for z in (4, 5, 6) if (x, 4, z) in cells]
holes = [(x, z) for x in (4, 5, 6) for z in (4, 5, 6) if (x, 4, z) not in cells and (x, 5, z) not in cells]
ok(len(roof) >= 4 and len(holes) >= 1, f"murder holes: {len(roof)} roof cells, holes {holes}")
ok(all((5, y, z) not in cells or short(cells[(5, y, z)]).endswith(GATES) for y in (1, 2) for z in range(10)),
   "headroom over the middle way, z 0..9")
for x in (2, 3, 7, 8):
    ok(all((x, y, z) in cells and collides(cells[(x, y, z)]) for y in (1, 2, 3) for z in range(5, 10)), f"barbican wall x {x} shut at y 1..3")
st2 = stations(cells, states)
ok(any(p[2] >= 4 for p in st2), f"a station on the barbican (z >= 4): {[p for p in st2 if p[2] >= 4][:6]}")
same, total = kept(base_cells, base_states, cells, states)
ok(same >= 0.6 * total, f"keeps {same} of Level I's {total} blocks exactly ({100 * same // total}% >= 60%)")
added = sum(1 for p in cells if base_cells.get(p) != cells[p] or base_states.get(p) != states[p])
def stacks(cs):
    count = {}
    for n in cs.values():
        count[n] = count.get(n, 0) + 1
    return sum(-(-v // 64) for v in count.values()), count


up = {p: n for p, n in cells.items() if base_cells.get(p) != n or base_states.get(p) != states[p]}
need, kinds = stacks(up)
ok(need <= 27 and "minecraft:spruce_fence_gate" in kinds and "minecraft:iron_bars" in kinds,
   f"the upgrade's materials fit one chest: {need} stacks (<= 27), fence gates and iron bars among them")
ok(stacks(base_cells)[0] <= 108, f"Level I's materials fit four barrels: {stacks(base_cells)[0]} stacks (<= 108)")
ok(added < 0.8 * len(cells), f"the upgrade places {added} of II's {len(cells)} blocks ({100 * added // len(cells)}% < 80%)")
ok(not any(t in short(n) for n in cells.values() for t in POI), "no villager point of interest")
blocks = {p: (n, states[p]) for p, n in cells.items()}
problems = owner.reach_problems(blocks, *size)
ok(not problems, f"check.py: every standing level has a way up {problems}")
nodes, seen = owner.reachable(blocks, *size)
ok(any(n[0] == 4 and n[1] == 8 and n[2] == 10 for n in seen), "a player walks from the loggia down onto the barbican's deck (4, 5, 8)")
print(f"  stations ({len(st2)}): on the barbican {[p for p in st2 if p[2] >= 4]}")

print("PASS" if not ok.failed else f"{ok.failed} FAILED")
sys.exit(1 if ok.failed else 0)
