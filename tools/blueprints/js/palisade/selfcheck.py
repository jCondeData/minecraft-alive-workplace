#!/usr/bin/env python3
"""The Palisade kit's self-check (ROADMAP 52.3): asserts the pins of the three briefs (tools/blueprints/briefs/
palisade.md, palisade_gate.md, palisade_tower.md) against the candidates in build/rebuild/ (or the live structures
with --live). Battle stations are counted the way guard/BattleStations.read counts them (a block at y >= 2 with a
sturdy top, no blueprint block at y + 1 and y + 2, not a merlon). Prints each check and exits 1 on any failure.

    python3 tools/blueprints/js/palisade/selfcheck.py [--live]
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
BP = os.path.normpath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, BP)
from holes import load  # noqa: E402
from kit import MAIN_STRUCTURES, ROOT  # noqa: E402

LIVE = "--live" in sys.argv
FAILS = []


def nbt(family, id_):
    return os.path.join(MAIN_STRUCTURES if LIVE else os.path.join(ROOT, "build", "rebuild", family), id_ + ".nbt")


def blocks(path):
    size, cells, states = load(path, props=True)
    at = {p: (n.replace("minecraft:", ""), states[p]) for p, n in cells.items() if n and n not in ("minecraft:air", "minecraft:structure_void")}
    return size, at


def check(ok, what):
    print(("ok   " if ok else "FAIL ") + what)
    if not ok:
        FAILS.append(what)


def sturdy_top(name, st):
    if name.endswith("_slab"):
        return st.get("type") in ("top", "double")
    if name.endswith("_stairs"):
        return st.get("half") == "top"
    thin = ("fence", "ladder", "trapdoor", "lantern", "dripstone", "rod", "door", "wall", "pane", "bars", "chain", "carpet", "button")
    return not any(t in name for t in thin)


def stations(at):
    spots = {p for p, (n, st) in at.items() if p[1] >= 2 and (p[0], p[1] + 1, p[2]) not in at
             and (p[0], p[1] + 2, p[2]) not in at and sturdy_top(n, st)}
    return sorted(p for p in spots if not any((p[0] + dx, p[1] - 1, p[2] + dz) in spots
                                              for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))))


MOTION_FREE = ("ladder", "lantern", "torch", "button", "carpet", "flower", "vine")


def blocks_motion(at, p):
    return p in at and not any(t in at[p][0] for t in MOTION_FREE)


GATES = ("fence_gate", "_door", "iron_bars")

# --- palisade / palisade_2 ---------------------------------------------------------------------------------------------
(w1, h1, d1), p1 = blocks(nbt("palisade", "palisade"))
(w2, h2, d2), p2 = blocks(nbt("palisade", "palisade_2"))
check(7 <= w1 <= 9 and w1 == w2, f"palisade width {w1} in 7..9, palisade_2 the same ({w2})")
check(d1 <= 4, f"palisade depth {d1} <= 4")
check(d2 > d1 and d2 <= 5, f"palisade_2 depth {d2} deeper than Level I and <= 5 (the corner tower's width)")
check(h1 <= 12 and h2 <= 12, f"heights {h1}, {h2} <= 12")
s1 = stations(p1)
check(len(s1) >= 4, f"palisade has {len(s1)} battle stations (>= 4)")
s2in = [p for p in stations(p2) if p[2] < d1]
check(len(s2in) >= len(s1), f"palisade_2 has {len(s2in)} stations at z < {d1} (>= Level I's {len(s1)})")
for x in range(7):
    if x != 3:
        check((x, 2, 0) in p2 or (x, 3, 0) in p2, f"palisade_2 fighting step at x {x} (y 2 or 3, z 0)")
drip = [(p, st) for p, (n, st) in p2.items() if n == "pointed_dripstone"]
check(len(drip) >= 6, f"palisade_2 has {len(drip)} pointed dripstone (>= 6)")
check(all(st.get("tip_direction") == "up" for _, st in drip), "every dripstone points up")
check(all(p[2] >= d1 for p, _ in drip), f"every dripstone at z >= {d1}")
check(all(sturdy_top(*p2[(p[0], p[1] - 1, p[2])]) if (p[0], p[1] - 1, p[2]) in p2 else p[1] == 0 for p, _ in drip),
      "every dripstone stands on a sturdy block (or the ground)")
kept = sum(1 for p, b in p1.items() if p2.get(p) == b)
check(kept >= 0.6 * len(p1), f"palisade_2 keeps {kept}/{len(p1)} of Level I's blocks with identical states (>= 60 %)")
moving = sum(1 for p in p1 if blocks_motion(p1, p))
check(moving > 10, f"palisade has {moving} motion-blocking blocks (> 10)")
check(not any((x, 0, 4) in p1 for x in (1, 3, 5)), "the witches' spawn cells (1|3|5, 0, 4) are outside the palisade")
check(not any(any(g in n for g in GATES) for n, _ in p1.values()) and not any(any(g in n for g in GATES) for n, _ in p2.values()),
      "no gate blocks in the segment")
VOLATILE = {"north", "east", "south", "west", "up", "down", "shape", "waterlogged", "powered", "open"}  # MaterialRules ignores these
cols = {}
for (x, y, z), (n, st) in p1.items():
    cols.setdefault(x, {})[(y, z)] = (n, {k: v for k, v in st.items() if k not in VOLATILE})
check(all(cols[x] == cols[0] for x in range(w1)), "every column of the palisade is the same (copies agree at any overlap)")
tallest = max(y for (_, y, _) in p1) + 1
check(tallest <= 8, f"a raider climbs at most {tallest} (<= 8)")
check(not any("chest" in n or "barrel" in n for n, _ in list(p1.values()) + list(p2.values())), "no containers")

# --- palisade_gate -------------------------------------------------------------------------------------------------------
(wg, hg, dg), g = blocks(nbt("palisade_gate", "palisade_gate"))
check((wg, dg) == (7, 2), f"gate is 7 x {dg} (7 x 2)")
gates = sorted(p for p, (n, _) in g.items() if any(t in n for t in GATES))
check(gates == [(2, 0, 1), (3, 0, 1), (4, 0, 1)], f"the gate blocks are exactly (2|3|4, 0, 1): {gates}")
check(all(g[p][0] == "spruce_fence_gate" for p in gates), "all three are spruce fence gates")
check(all(g[p][1].get("facing") in ("north", "south") and g[p][1].get("open") == "false" for p in gates),
      "drawn shut, panels along x (facing north or south)")
check(all(not blocks_motion(g, (x, 1, 1)) for x in (2, 3, 4)), "open over each gate at y 1")
check(all(not blocks_motion(g, (x, y, 0)) for x in (2, 3, 4) for y in (0, 1)), "the gateway's inside cells (z 0, y 0..1) are walkable")
check(all(blocks_motion(g, (x, y, 1)) for x in (0, 1, 5, 6) for y in (0, 1)), "the back row stops a mob at y 0 and 1 but the gateway")
check(len(g) - len(gates) > 10, f"{len(g) - len(gates)} other blocks (> 10)")
check(not any("ladder" in n for n, _ in g.values()), "nothing climbable (no ladder)")
check(any(g.get((x, 2, z), ("", {}))[0] for x in (2, 3, 4) for z in (0, 1)), "a lintel over the way (y 2) holds a ravager")
print(f"     gate stations: {len(stations(g))}")

# --- palisade_tower ------------------------------------------------------------------------------------------------------
(wt, ht, dt), t = blocks(nbt("palisade_tower", "palisade_tower"))
check((wt, ht, dt) == (5, 10, 5), f"tower is {wt} x {ht} x {dt} (5 x 10 x 5)")
ladders = [p for p, (n, _) in t.items() if n == "ladder"]
check(2 <= len(ladders) <= 16, f"{len(ladders)} ladders (2..16)")
check((2, 5, 3) in ladders, "a ladder at (2, 5, 3)")
for p in ladders:
    f = t[p][1].get("facing")
    back = {"north": (0, 0, 1), "south": (0, 0, -1), "west": (1, 0, 0), "east": (-1, 0, 0)}[f]
    q = (p[0] + back[0], p[1], p[2] + back[2])
    check(q in t and sturdy_top(*t[q]) and "ladder" not in t[q][0], f"ladder {p} hangs on a solid block {q}")
check(any((2, y, 2) in t for y in range(1, ht)), "something solid in the centre column above y 0")
check(len(t) > 100, f"{len(t)} blocks (> 100)")
st = stations(t)
check(len(st) >= 4, f"tower has {len(st)} battle stations")
# a guard reaches a station from walkable cells: the ground in front (z -1) or inside floor cells (air at y 0..1, open
# to the front through air) within 3.5 blocks flat and at least 1.5 below
walk = {(x, -1) for x in range(5)}
todo = [(x, 0) for x in range(5)]
while todo:
    x, z = todo.pop()
    def passable(p):  # air, a ladder, or a trapdoor hung open (a board on one side of the cell)
        return p not in t or "ladder" in t[p][0] or t[p][0].endswith("_door") or "trapdoor" in t[p][0] and t[p][1].get("open") == "true"
    if (x, z) in walk or not (0 <= x < 5 and 0 <= z < 5) or not passable((x, 0, z)) or not passable((x, 1, z)):
        continue
    walk.add((x, z))
    todo += [(x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)]
far = [p for p in st if min(((p[0] - x) ** 2 + (p[2] - z) ** 2) ** 0.5 for x, z in walk) > 3.5]
check(not far, f"every tower station within 3.5 of a walkable cell ({len(walk)} cells): {far or 'all'}")
check(not any("chest" in n or "barrel" in n or n == "water" or n == "lava" for n, _ in t.values()), "no containers or liquids")
walls = [p for p, (n, st) in t.items() if "trapdoor" in n and p != (2, 4, 3)]
check(not walls, f"no trapdoor in the tower's walls (only the hatch): {walls or 'none'}")
check(not any(n == "lightning_rod" for n, _ in list(p1.values()) + list(p2.values())), "no lightning rods on the palisade")
front_door = [x for x in range(5) if (x, 0, 0) not in t or "door" in t[(x, 0, 0)][0]]
check(all(x >= 2 for x in front_door), f"a way in on the front avoids x 0..1 ({front_door})")

print(f"\n{'FAILED: ' + str(len(FAILS)) if FAILS else 'ALL GREEN'}  (palisade stations {len(s1)}, palisade_2 inside {len(s2in)}, tower {len(st)})")
sys.exit(1 if FAILS else 0)
