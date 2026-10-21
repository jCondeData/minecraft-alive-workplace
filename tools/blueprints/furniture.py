"""
Furniture spots (ROADMAP 37.2, docs/design/M37.md): every piece of vanilla furniture a blueprint draws, written beside
the template as data/aliveworkplace/blueprint_furniture/<blueprint>.json so the game can swap it for the piece of an
installed furniture mod (37.3). Nothing changes in the world by itself.

A spot: its kind, its position (the blueprint's coordinates as the game reads them: y = 0 the floor layer, a basement below it), the way a person using
it faces, its wood and/or colour, and the vanilla blocks it is drawn with (relative to the position). Found two ways:

  * detected in the finished drawing: chairs (a stair seat beside a table, as check.py's chair rule finds them), tables
    (check.py's is_table: a post with a carpet or plate on it, a free-standing upside-down slab or stair) and beds;
  * recorded by the drawing itself with Build.furniture(...) for the kinds a drawing can't be read for (a couch, a
    shelf, a lamp, blinds...), as 37.4's furnished rooms do.

Each spot's blocks are checked against the template before the file is written (a mismatch is a CHECK line and the
spot is left out).
"""
import json
import os
import re

from check import AIR, FACING, OPPOSITE, TABLE_LEGS, _open_top, _short, _thin, is_table

KINDS = ("chair", "stool", "bench", "couch", "table", "side_table", "desk", "counter", "cupboard", "shelf", "nightstand",
         "drawer", "bed", "lamp", "clock", "picture", "blinds", "crockery", "trellis", "hanging_pot")
WOODS = ("dark_oak", "oak", "spruce", "birch", "jungle", "acacia", "mangrove", "cherry", "bamboo", "crimson", "warped")
COLOURS = ("light_blue", "light_gray", "white", "orange", "magenta", "yellow", "lime", "pink", "gray", "cyan", "purple",
           "blue", "brown", "green", "red", "black")
_WOOD = re.compile(r"minecraft:(?:stripped_)?(" + "|".join(WOODS) + r")_")
_COLOUR = re.compile(r"minecraft:(" + "|".join(COLOURS) + r")_")
FURNITURE_ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src/main/resources/data/aliveworkplace/blueprint_furniture")


def wood_of(name):
    m = _WOOD.match(name)
    return m.group(1) if m else None


def colour_of(name):
    m = _COLOUR.match(name)
    return m.group(1) if m else None


def _state(block):
    name, props = block
    props = dict(props)
    return name + ("[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]" if props else "")


def main_wood(b):
    """The wood most of the build's wooden blocks are of (planks, logs, stairs...), or oak."""
    count = {}
    for name, _ in b.blocks.values():
        w = wood_of(name)
        if w:
            count[w] = count.get(w, 0) + 1
    return max(sorted(count), key=lambda w: count[w]) if count else "oak"


def _spot(kind, pos, facing, cells, wood=None, colour=None):
    spot = {"kind": kind, "pos": list(pos), "facing": facing}
    if wood:
        spot["wood"] = wood
    if colour:
        spot["colour"] = colour
    spot["cells"] = cells  # [(dx, dy, dz), ...]
    return spot


_LEG_SIDES = ("north", "south", "east", "west")


_SIDE = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
_TOPS = ("_carpet", "pressure_plate")


def _leg(blocks, p):
    """A fence or wall post that is a table's leg: every side it joins leads to another such leg with a cloth or plate
    on it (a long table), not into a fence line or a wall."""
    block = blocks.get(p)
    if block is None:
        return False
    s, props = _short(block), block[1]
    if "gate" in s or not (s.endswith("_fence") or s.endswith("_wall")):
        return False
    x, y, z = p
    for side, (dx, dz) in _SIDE.items():
        if props.get(side, "false") not in ("false", "none"):
            q = (x + dx, y, z + dz)
            other = blocks.get(q)
            top = blocks.get((q[0], y + 1, q[2]))
            if other is None or not _short(other).endswith(("_fence", "_wall")) or top is None or not _short(top).endswith(_TOPS):
                return False
    return True


def is_furniture_table(blocks, p):
    """A table a person sits at, stricter than check.is_table (which also takes roof edges and ledges): a table leg
    (_leg) standing on something with a carpet or pressure plate on it, or an upside-down slab or stair on a lone
    post."""
    if not is_table(blocks, p):
        return False
    x, y, z = p
    s = _short(blocks[p])
    floor = blocks.get((x, y - 1, z))
    if s.endswith("_fence") or s.endswith("_wall"):
        return floor is not None and _leg(blocks, p)
    return floor is not None and _short(floor).endswith(("_fence", "_wall")) and "gate" not in _short(floor) \
        and blocks.get((x, y - 2, z)) is not None and all(floor[1].get(side, "false") in ("false", "none") for side in _SIDE)


def detect(b):
    """The chairs, tables and beds in the drawing (each cell in at most one spot)."""
    blocks = {p: (n, dict(props)) for p, (n, props) in b.blocks.items() if n not in AIR}
    fallback = main_wood(b)
    spots, used = [], set()

    def table_cells(p):
        x, y, z = p
        s = _short(blocks[p])
        if (s.endswith("_fence") or s.endswith("_wall")) and "gate" not in s:
            return [p, (x, y + 1, z)]
        below = blocks.get((x, y - 1, z))
        if below is not None and _short(below).endswith(TABLE_LEGS) and "gate" not in _short(below) and below[1].get("axis", "y") == "y":
            return [(x, y - 1, z), p]
        return [p]

    for p in sorted(blocks):
        name, props = blocks[p]
        if name.endswith("_bed") and props.get("part") == "foot":
            fx, _, fz = FACING[props["facing"]]
            head = (p[0] + fx, p[1], p[2] + fz)
            if blocks.get(head, ("",))[0] == name:
                spots.append(_spot("bed", p, props["facing"], [p, head], fallback, colour_of(name)))
                used.update((p, head))
    tables = [p for p in sorted(blocks) if p not in used and is_furniture_table(blocks, p)]
    for p in tables:
        cells = [c for c in table_cells(p) if c in blocks and c not in used]
        if p not in cells:
            continue
        top = cells[-1]
        colour = colour_of(blocks[top][0]) if blocks[top][0].endswith("_carpet") else None
        wood = next((wood_of(blocks[c][0]) for c in cells if wood_of(blocks[c][0])), None) or fallback
        spots.append(_spot("table", cells[0], "north", cells, wood, colour))
        used.update(cells)
    table_cells_all = {c for s in spots if s["kind"] == "table" for c in s["cells"]}
    for p in sorted(blocks):
        name, props = blocks[p]
        if p in used or not name.endswith("_stairs") or props.get("half") != "bottom" or not _open_top(blocks.get((p[0], p[1] + 1, p[2]))):
            continue
        x, y, z = p
        under = blocks.get((x, y - 1, z))
        if under is None or _short(under).endswith(("_stairs", "_slab")):
            continue
        toward = OPPOSITE[props["facing"]]  # the sitter faces away from the backrest
        dx, _, dz = FACING[toward]
        front = (x + dx, y, z + dz)
        if front not in table_cells_all:
            continue
        spots.append(_spot("chair", p, toward, [p], wood_of(name) or fallback))
        used.add(p)
    return spots


def spots_of(b):
    """Detected spots plus those the drawing recorded (Build.furniture), absolute cells. A recorded piece an upgrade has
    since built over (a cell no longer holds the block it was drawn with) has no spot: it isn't there any more."""
    def still_there(s):
        return all(b.blocks.get(tuple(c), ("minecraft:air",))[0] == d for c, d in zip(s["cells"], s.get("drawn", ())))
    return detect(b) + [dict(s) for s in getattr(b, "spots", []) if still_there(s)]


def to_json(b, spots):
    """The file's content: cells relative to the spot, with their block states. Positions are the drawing's (a basement
    is below y = 0, as the game's Blueprint has it, not shifted up as in the .nbt file)."""
    shift = 0
    out = []
    blocks = {p: (n, dict(props)) for p, (n, props) in b.blocks.items() if n not in AIR}
    for s in sorted(spots, key=lambda s: (s["pos"][1], s["pos"][2], s["pos"][0], s["kind"])):
        px, py, pz = s["pos"]
        entry = {"kind": s["kind"], "pos": [px, py + shift, pz], "facing": s["facing"]}
        for key in ("wood", "colour"):
            if s.get(key):
                entry[key] = s[key]
        entry["blocks"] = [{"at": [cx - px, cy - py, cz - pz], "state": _state(b.blocks[(cx, cy, cz)])} for cx, cy, cz in s["cells"]]
        entry["support"] = support(blocks, s)  # B260: a modded piece goes only where what it needs is there
        out.append(entry)
    return {"spots": out}


def problems(b, spots):
    """Spots whose cells aren't drawn (each spot's blocks must be the blocks in the template there), or overlap."""
    out, seen = [], set()
    for s in spots:
        if s["kind"] not in KINDS:
            out.append(f"furniture spot of unknown kind {s['kind']} at {s['pos']}")
        for c in s["cells"]:
            block = b.blocks.get(tuple(c))
            if block is None or block[0] in AIR:
                out.append(f"furniture spot {s['kind']}@{s['pos']}: nothing drawn at {c}")
            if tuple(c) in seen:
                out.append(f"furniture spot {s['kind']}@{s['pos']}: {c} is in another spot too")
            seen.add(tuple(c))
    return out


# The workplaces furnished in 37.5: their beds are beds a villager needs (the Healing Center's ward, the shopkeeper's
# and the cook's), read by the game as vanilla beds, so they get no bed spot and are never swapped.
WORKPLACES = re.compile(r"(supply_shop|library|healing_center|schoolhouse|village_hall|kitchen|print_shop)(_\d+)?$")


# --- Rooms that read as rooms (B260, the owner 2026-10-20: "cluttered and unfinished") --------------------------------
# What each kind stands on or against in the vanilla drawing. A modded piece is only used where its own need is met
# (the furnishing tables' "needs", FurnishingTable.Piece): a Beautify picture frame stands on a table, never on a wall.
NEEDS = {"lamp": ("floor", "ceiling"), "shelf": ("floor", "wall"), "picture": ("wall",), "blinds": ("wall",),
         "trellis": ("wall",), "hanging_pot": ("ceiling",)}  # every other kind: the floor
_NOT_SOLID = ("_door", "_trapdoor", "_bed", "_carpet", "_pressure_plate", "_banner", "_sign", "ladder", "torch", "button",
              "lantern", "flower_pot", "potted_", "candle", "_pane", "glass", "_bars", "chain", "item_frame", "rail")
_GLAZED = ("_pane", "glass", "_bars")


def _name(blocks, p):
    got = blocks.get(p)
    return "minecraft:air" if got is None else got[0]


def solid(blocks, p):
    """A block that holds a piece up or stands behind it: not air, not a thin or small thing, not a door or a bed."""
    n = _name(blocks, p)
    return n not in AIR and not n.endswith(_NOT_SOLID) and not any(t in n for t in ("glass", "potted_", "lantern")) \
        and not _thin(blocks.get(p))


def _walled(blocks, p):
    """Solid, or a window (blinds and pictures hang at windows and on walls alike)."""
    return solid(blocks, p) or any(t in _name(blocks, p) for t in _GLAZED)


def support(blocks, s):
    """What a spot stands on or against in the drawing: "floor" (a solid block under its lowest cell at its position),
    "wall" (behind it, against the way it faces, at any of its cells) and "ceiling" (over its top cell)."""
    px, py, pz = s["pos"]
    cells = [tuple(c) for c in s["cells"]]
    out = []
    low = min(c[1] for c in cells)
    if any(solid(blocks, (c[0], c[1] - 1, c[2])) for c in cells if c[1] == low):
        out.append("floor")
    bx, _, bz = FACING[OPPOSITE[s["facing"]]]
    if any(_walled(blocks, (c[0] + bx, c[1], c[2] + bz)) for c in cells):
        out.append("wall")
    top = max(c[1] for c in cells)
    if any(solid(blocks, (c[0], c[1] + 1, c[2])) for c in cells if c[1] == top):
        out.append("ceiling")
    return out


def indoors(blocks, p, reach=12):
    """Inside a room: a wall (or window) every way out within {@code reach} and a roof over it (a lamp beside a door
    step outside is a porch light, not a lantern left on the floor)."""
    x, y, z = p
    if not any(solid(blocks, (x, y + d, z)) for d in range(1, reach)):
        return False
    return all(any(_walled(blocks, (x + dx * d, y, z + dz * d)) or _name(blocks, (x + dx * d, y, z + dz * d)).endswith("_door")
                   for d in range(1, reach)) for dx, _, dz in FACING.values())


def room_problems(b, spots):
    """B260's rules for a furnished build: every piece stands on (or hangs from, or against) what its kind needs; no
    piece in a doorway's way (two cells in and out of every door); every bed's head against a solid block; no lantern
    standing on the bare floor (hang it or put it on a table); every chest against a wall or by a bed."""
    blocks = {p: (n, dict(props)) for p, (n, props) in b.blocks.items() if n not in AIR}
    out = []
    taken = {}
    for s in spots:
        need = NEEDS.get(s["kind"], ("floor",))
        has = support(blocks, s)
        if not any(n in has for n in need):
            out.append(f"furniture {s['kind']}@{s['pos']} has no {' or '.join(need)} to stand on (it floats)")
        for c in s["cells"]:
            taken[tuple(c)] = s
    for p, (n, props) in sorted(blocks.items()):
        x, y, z = p
        if n.endswith("_door") and props.get("half") == "lower":
            fx, _, fz = FACING[props["facing"]]
            for d in (1, 2, -1, -2):
                for dy in (0, 1):
                    q = (x + fx * d, y + dy, z + fz * d)
                    if q in taken:
                        out.append(f"furniture {taken[q]['kind']}@{taken[q]['pos']} stands in the way of the door at {p}")
        elif n.endswith("_bed") and props.get("part") == "head":
            fx, _, fz = FACING[props["facing"]]
            if not _walled(blocks, (x + fx, y, z + fz)):  # a wall, or a window in it
                out.append(f"bed@{p}: its head is not against a wall ({_name(blocks, (x + fx, y, z + fz))} behind it)")
        elif n == "minecraft:lantern" and props.get("hanging") != "true":
            # On the floor: standing where people stand (two of the cells beside it open over the same floor), not
            # on a table, a counter, a barrel or a wall top
            beside = [(x + dx, y, z + dz) for dx, _, dz in FACING.values()]
            level = sum(1 for q in beside if _name(blocks, q) in AIR | {"minecraft:air"} and solid(blocks, (q[0], y - 1, q[2])))
            under = _name(blocks, (x, y - 1, z))
            table = under.endswith(("_slab", "_stairs", "_fence", "_wall", "barrel", "chest", "bookshelf", "_table", "_log",
                                    "_wood", "lectern", "_stem", "_hyphae"))
            if (solid(blocks, (x, y - 1, z)) and not table or under.endswith("_carpet")) and level >= 2 \
                    and indoors(blocks, p):
                out.append(f"lantern@{p} stands on the floor: hang it or put it on a table")
        elif n in ("minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel") and props.get("facing") in FACING:
            fx, _, fz = FACING[props["facing"]]
            near = [(x + dx, y, z + dz) for dx, _, dz in FACING.values()]
            if indoors(blocks, p) and not _walled(blocks, (x - fx, y, z - fz)) and not any(_name(blocks, q).endswith("_bed") for q in near):
                out.append(f"{_short(blocks[p])}@{p} stands in the room: put it against a wall or at the foot of a bed")
    return out


def write(b, rel):
    """Writes (or removes, when there are none) the spots file of the blueprint at structure path {@code rel}; returns
    the CHECK lines."""
    spots = spots_of(b)
    if WORKPLACES.match(os.path.basename(rel)):
        spots = [s for s in spots if s["kind"] != "bed"]
    bad = problems(b, spots)
    if getattr(b, "spots", None) and not bad:  # a furnished build (37.4, 37.5): its rooms must read as rooms (B260)
        bad = room_problems(b, spots)
    path = os.path.normpath(os.path.join(FURNITURE_ROOT, rel + ".json"))
    if bad:
        return bad
    if not spots:
        if os.path.exists(path):
            os.remove(path)
        return []
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = to_json(b, spots)
    with open(path, "w") as f:
        f.write('{"spots": [\n' + ",\n".join("  " + json.dumps(spot) for spot in data["spots"]) + "\n]}\n")
    with open(path) as f:
        return verify(b, json.load(f))  # each spot's blocks are the blocks in the template there


def verify(b, data):
    """The check generate.py makes against a written file: each spot's blocks are the blocks in the template there."""
    shift = 0
    out = []
    for s in data["spots"]:
        px, py, pz = s["pos"]
        for cell in s["blocks"]:
            dx, dy, dz = cell["at"]
            p = (px + dx, py + dy - shift, pz + dz)
            got = b.blocks.get(p)
            if got is None or _state(got) != cell["state"]:
                out.append(f"furniture spot {s['kind']}@{s['pos']}: the template has {got and _state(got)} at {p}, not {cell['state']}")
    return out
