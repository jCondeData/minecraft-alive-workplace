"""
Checks a build for the mistakes that show in the game, run on every save (the rules follow the lint of the Minecraft
Architect building skill): blocks nothing holds up, lanterns hanging from nothing, lanterns, pots, carpets and torches
standing on air, ladders with no wall behind them, doors missing a half, sand and gravel over air. The bottom layer
(y = 0) stands on the ground, so it holds itself up. And two the owner met in a playtest (B148, B149): chairs with their
backs to the table, and upper floors no stairs or ladder lead up to; and since B154 open-air stands and terraces too.
Since B537-B539: beds missing their other half (or with it on the wrong side), lanterns, candles and torches standing
on a block whose top can't hold them (a lectern, a chest, a bottom slab...), and dirt paths or farmland under a block
the game counts as solid (a fence, a wall, a slab...), which it turns to dirt. Since B639: "cots" of a carpet on a
bottom slab, two in a row, where a bed belongs (the owner saw them as broken beds no one can sleep in). Since B648:
glass panes and iron bars whose run ends in the air (joined on one side only), the Terrace's broken windows (B640). Since B656:
glass joined to nothing with air beside it, a lone thin post (between two stairs it can't join, or a block out from
a wall).
"""

AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:structure_void", "minecraft:jigsaw"}
FACING = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}
STANDS = ("lantern", "flower_pot", "potted_", "_carpet", "candle", "torch", "pressure_plate", "rail")
GRAVITY = ("minecraft:sand", "minecraft:red_sand", "minecraft:gravel", "concrete_powder", "anvil")


def holds_centre(block):
    """Whether a lantern can hang under {@code block}: its underside is solid at the middle (a full block, a bottom slab
    or bottom-half stairs, a fence, wall, chain or pane) — not top-half stairs or slabs, trapdoors, leaves on nothing."""
    name, props = block
    short = name.split(":")[1]
    if short.endswith("_stairs") or short.endswith("_slab"):
        return props.get("half", props.get("type")) in ("bottom", "double")
    return not (short.endswith("_trapdoor") or short.endswith("_carpet") or short.endswith("lantern") or "button" in short
                or short.endswith("_sign") or short.endswith("_banner") or "torch" in short)


# What the game lets a lantern, a candle or a torch stand on: Block.canSupportCenter, a solid middle in the block's top
# face. These blocks' tops are lower than a full block, slanted or hollow, so the game drops what stands on them (B538).
NO_CENTRE_TOP = ("lectern", "chest", "_bed", "_carpet", "pressure_plate", "campfire", "enchanting_table",
                 "brewing_stand", "flower_pot", "potted_", "_leaves", "dirt_path", "farmland", "cake", "candle", "lantern",
                 "_head", "skull", "stonecutter", "daylight_detector", "composter", "cauldron", "_button", "_sign",
                 "_banner", "torch", "rail", "sea_pickle", "turtle_egg", "snow")
CENTRE_STANDS = ("lantern", "soul_lantern", "torch", "soul_torch", "redstone_torch")


def holds_on_top(block):
    """Whether a lantern, candle or torch can stand on {@code block} (its top face has a solid middle)."""
    name, props = block
    short = name.split(":")[1]
    if short.endswith("_slab"):
        return props.get("type") in ("top", "double")
    if short.endswith("_trapdoor"):
        return props.get("half") == "top" and props.get("open") != "true"
    if short == "snow":
        return props.get("layers") == "8"
    return not any(t in short for t in NO_CENTRE_TOP)


# What turns a dirt path or farmland under it to dirt: a block the game counts as solid (BlockState.isSolid: a
# collision box at least a block tall, or most of a block), e.g. a fence, a wall, a slab, stairs, a door, a banner
# (B539). Not a fence gate (the game lets a path run under one), not small or walk-through things.
# Another mod's block (a Cobblemon crop on farmland) is left alone: nothing here says how solid it is.
SMALL = ("lantern", "candle", "flower_pot", "potted_", "_head", "skull", "sea_pickle", "turtle_egg", "_stem", "_crop",
         "_mushroom", "_fungus", "_roots", "sweet_berry_bush")


def turns_path(block):
    """Whether the game turns a dirt path or farmland under {@code block} to dirt."""
    if block is None or not block[0].startswith("minecraft:"):
        return False
    s = _short(block)
    if "fence_gate" in s or any(t in s for t in SMALL):
        return False
    # Signs and banners have no collision but the game still counts them as solid (forceSolidOn)
    return s.endswith(("_door", "trapdoor", "ladder", "_sign", "_banner")) or not passable(block)


def bed_problem(blocks, p, props):
    """What's wrong with the bed half at {@code p}: the head is one block from the foot towards its facing (B537)."""
    dx, _, dz = FACING[props["facing"]]
    foot = props.get("part") == "foot"
    q = (p[0] + dx, p[1], p[2] + dz) if foot else (p[0] - dx, p[1], p[2] - dz)
    other = blocks.get(q)
    if other is not None and other[0] == blocks[p][0] and other[1].get("facing") == props["facing"] \
            and other[1].get("part") == ("head" if foot else "foot"):
        return None
    return (f"({'foot' if foot else 'head'}, facing {props['facing']}) has no {'head' if foot else 'foot'} at "
            f"{q[0]},{q[1]},{q[2]} ({_short(other)} there): a half bed no one can sleep in")


def check(b, only=None, whole=None):
    """What's wrong with build {@code b}, as lines of text (empty if nothing). {@code only}: the positions to report on
    (a Wonder's stage, checked standing on the stages before it); None for all of them. {@code whole}: whether the
    build is finished, so its upper floors must be reachable (default: when {@code only} is None)."""
    blocks = {p: (n, dict(props)) for p, (n, props) in b.blocks.items() if n not in AIR}
    problems = []
    # A basement (26.15) is dug into the ground: under y = 0, a cell the drawing leaves out is the earth round it
    deep = getattr(b, "basement", 0)
    drawn = {p for p, (n, _) in b.blocks.items() if n != "minecraft:structure_void"}

    def grounded(x, y, z):
        return y == 0 or y == -deep or (y < 0 and (x, y - 1, z) not in drawn)
    # Floating: flood from the bottom layer through face and edge neighbours (stair roofs meet row to row at an edge).
    steps = [(dx, dy, dz) for dx in (-1, 0, 1) for dy in (-1, 0, 1) for dz in (-1, 0, 1) if 1 <= abs(dx) + abs(dy) + abs(dz) <= 2]
    seen = {p for p in blocks if p[1] in (0, -deep)}
    todo = list(seen)
    while todo:
        x, y, z = todo.pop()
        for dx, dy, dz in steps:
            q = (x + dx, y + dy, z + dz)
            if q in blocks and q not in seen:
                seen.add(q)
                todo.append(q)
    floating = sorted(p for p in blocks if p not in seen and (only is None or p in only))
    if floating:
        problems.append(f"{len(floating)} block(s) held up by nothing, e.g. " + "; ".join(
            f"{blocks[p][0].split(':')[1]}@{p[0]},{p[1]},{p[2]}" for p in floating[:5]))
    # A Wonder's stage can also spoil what an earlier stage left: a lectern under its lantern (B538), a wall on its path
    # (B539), a block over half of its bed. So the blocks beside this stage's are checked again.
    near = None if only is None else only | {(x + dx, y + dy, z + dz) for x, y, z in only
                                             for dx, dy, dz in ((0, 1, 0), (0, -1, 0), (1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1))}
    for (x, y, z), (name, props) in sorted(blocks.items()):
        if near is not None and (x, y, z) not in near:
            continue
        short = name.split(":")[1]
        if (x, y, z) not in (only or ()) and only is not None and not (
                short.endswith("_bed") or short in ("dirt_path", "farmland") or short in CENTRE_STANDS or short.endswith("candle")):
            continue
        below = blocks.get((x, y - 1, z))
        above = blocks.get((x, y + 1, z))
        bad = None
        if short == "lantern" or short == "soul_lantern":
            if props.get("hanging") == "true":
                bad = (above is None and "hangs from nothing") or (not holds_centre(above) and "hangs from " + above[0][10:]
                                                                   + " (the game drops it: nothing under its middle)")
            else:
                bad = not grounded(x, y, z) and below is None and "stands on air"
        elif short.endswith("_bed"):
            bad = bed_problem(blocks, (x, y, z), props)
        elif short in ("dirt_path", "farmland") and turns_path(above):
            bad = "is under " + _short(above) + " (the game turns it to dirt: grass, dirt or coarse dirt there)"
        elif short == "ladder":
            dx, dy, dz = FACING[props["facing"]]
            bad = (x - dx, y, z - dz) not in blocks and "has no wall behind it"
        elif short.endswith("_door"):
            other = (x, y + 1, z) if props.get("half") == "lower" else (x, y - 1, z)
            bad = (other not in blocks or blocks[other][0] != name) and "is missing a half"
        elif short.startswith("potted_") and above is not None and above[0] == "minecraft:chain":
            pass  # a hanging pot (37.5): a flower pot needs nothing under it in game, the chain holds it to the beam
        elif any(s in short for s in STANDS) and "wall_" not in short:
            bad = not grounded(x, y, z) and below is None and "stands on air"
        elif any(name.startswith(g) or g in name for g in GRAVITY):
            bad = not grounded(x, y, z) and below is None and "will fall"
        if not bad and (short in CENTRE_STANDS or short.endswith("candle")) and props.get("hanging") != "true" \
                and below is not None and not holds_on_top(below):
            bad = "stands on " + _short(below) + " (its top can't hold it: the game drops it)"
        if bad:
            problems.append(f"{short}@{x},{y},{z} {bad}")
    problems += cot_problems(blocks, only)
    problems += pane_end_problems(blocks, only, getattr(b, "open_panes", {}))
    problems += chair_problems(blocks, only)
    problems += doorway_problems(blocks, only)
    problems += headroom_problems(blocks, b.w, b.h, b.d, deep, drawn, only)
    if (only is None) if whole is None else whole:
        problems += reach_problems(blocks, b.w, b.h, b.d, deep, drawn)
    return problems


# --- Cots (B639) ---------------------------------------------------------------------------------------------------
def _cot_half(blocks, p):
    """Whether {@code p} is a carpet lying on a bottom slab laid on the floor: the half of a stand-in bed (a slab on
    trestles or posts is a table or a tray)."""
    block, below = blocks.get(p), blocks.get((p[0], p[1] - 1, p[2]))
    floor = blocks.get((p[0], p[1] - 2, p[2]))
    return block is not None and _short(block).endswith("_carpet") and below is not None \
        and _short(below).endswith("_slab") and below[1].get("type", "bottom") == "bottom" \
        and floor is not None and not _short(floor).endswith(TABLE_LEGS + ("_slab", "_stairs"))


def cot_problems(blocks, only=None):
    """Carpets on bottom slabs two in a row: a stand-in bed. It looks like a broken bed and no one can sleep in it, so a
    bed belongs there (B639: the Clinic's ward)."""
    problems = []
    for p in sorted(blocks):
        if (only is not None and p not in only) or not _cot_half(blocks, p):
            continue
        x, y, z = p
        for q in ((x + 1, y, z), (x, y, z + 1)):
            if _cot_half(blocks, q) and blocks[q][0] == blocks[p][0]:
                problems.append(f"{_short(blocks[p])}@{x},{y},{z} lies on a slab beside another (to {q[0]},{q[1]},{q[2]}):"
                                " a cot of slab and carpet where a bed belongs")
    return problems


# --- Window glass ending in air (B640, B648) -------------------------------------------------------------------------
def is_pane(block):
    return block is not None and (_short(block).endswith("glass_pane") or _short(block) == "iron_bars")


def _may_join(block, side):
    """Whether glass may join {@code block} on its {@code side} (the way from the glass to the block), as the game does
    (IronBarsBlock.attachsTo: glass, a wall, or a face that is sturdy). Generous where unsure: not air, not a slab or a
    carpet, not a stair whose back (its facing side, full in both halves) is turned away from the glass."""
    if block is None:
        return False
    s = _short(block)
    props = block[1]
    if s.endswith("_stairs"):
        return props.get("facing") == OPPOSITE[side]
    if s.endswith("_slab"):
        return props.get("type") == "double"
    return not s.endswith(("_carpet", "_pressure_plate", "lantern", "_button", "torch", "_sign", "_banner", "flower_pot",
                            "chain", "_rod", "ladder", "_door", "_trapdoor"))


def pane_end_problems(blocks, only=None, allowed=None):
    """A glass pane or iron bars joined on one side only, with air on the other: the run of glass stops half way across
    its last block, a thin strip with a gap beside it (B640: the Terrace's bay windows). A window's glass meets a jamb (a
    block it joins) at both ends. Its stored connections are kit.finish's, what the game works out as it is placed.
    {@code allowed}: the build's open_panes, ends meant to stand free (a lamp's arm), each with its reason; the same
    list is in B648GameTests.ALLOWED."""
    problems = []
    for p in sorted(blocks):
        if (only is not None and p not in only) or not is_pane(blocks[p]) or p in (allowed or {}):
            continue
        props = blocks[p][1]
        joined = [d for d in FACING if str(props.get(d, "false")).lower() == "true"]
        x, y, z = p
        if not joined:
            # B656: joined to nothing (between two stairs it can't join, or standing out from a wall), with air beside
            # it: a lone thin post, the glass meets no jamb at all. The stored connections are kit.finish's guess, which
            # misses some blocks the game joins (a stair's back, a quartz pillar), so a lone post is only one that has
            # nothing beside it the game could join (_may_join).
            open_sides = [d for d, (dx, _, dz) in FACING.items() if (x + dx, y, z + dz) not in blocks]
            if open_sides and not any(_may_join(blocks.get((x + dx, y, z + dz)), d) for d, (dx, _, dz) in FACING.items()):
                problems.append(f"{_short(blocks[p])}@{x},{y},{z} joins nothing, with air to the {open_sides[0]}: a "
                                f"lone post, give it a jamb at both ends")
            continue
        if len(joined) != 1:
            continue
        dx, _, dz = FACING[OPPOSITE[joined[0]]]
        if (x + dx, y, z + dz) not in blocks:
            problems.append(f"{_short(blocks[p])}@{x},{y},{z} joins only to the {joined[0]}, with air to the "
                            f"{OPPOSITE[joined[0]]}: the glass ends half way, give it a jamb")
    return problems


# --- Chairs at tables (B148) -------------------------------------------------------------------------------------
# A stair's facing is the side its backrest is on, so a chair faces its table when the table is on the other side.
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}
TABLE_LEGS = ("_fence", "_wall", "_log", "_wood", "_stem", "_hyphae", "chain")
# What may stand on a table top or a seat: small things, not another block of furniture.
ON_TOP = ("lantern", "candle", "flower_pot", "potted_", "_carpet", "pressure_plate", "torch", "_head", "skull",
          "_banner", "_sign", "button", "cake", "rail")


def _short(block):
    return block[0].split(":")[1] if block else "air"


def _open_top(block):
    return block is None or any(t in _short(block) for t in ON_TOP)


def is_table(blocks, p):
    """Whether the block at {@code p} is a table top: a pressure plate or carpet on a fence or wall post, or an
    upside-down slab or stair standing free (on a post or on nothing), with nothing but small things on it."""
    block = blocks.get(p)
    if block is None:
        return False
    x, y, z = p
    s, props = _short(block), block[1]
    above, below = blocks.get((x, y + 1, z)), blocks.get((x, y - 1, z))
    if (s.endswith("_fence") or s.endswith("_wall")) and "gate" not in s:
        return above is not None and any(t in _short(above) for t in ("pressure_plate", "_carpet"))
    top = (s.endswith("_slab") and props.get("type") == "top") or (s.endswith("_stairs") and props.get("half") == "top")
    return top and _open_top(above)


def chair_problems(blocks, only=None):
    """Chair stairs (bottom-half stairs on a floor with nothing on the seat) beside a table that don't face it: the
    table must be on the seat's open side (opposite the stair's facing), the backrest away from it."""
    problems = []
    for (x, y, z), block in sorted(blocks.items()):
        if only is not None and (x, y, z) not in only:
            continue
        s, props = _short(block), block[1]
        if not s.endswith("_stairs") or props.get("half") != "bottom" or not _open_top(blocks.get((x, y + 1, z))):
            continue
        under = blocks.get((x, y - 1, z))
        if under is None or _short(under).endswith("_stairs") or _short(under).endswith("_slab"):
            continue  # a roof or a flight of steps, not a seat on a floor
        fx, _, fz = FACING[props["facing"]]
        if _short(blocks.get((x + fx, y + 1, z + fz))).endswith(("_stairs", "_slab")):
            continue  # a roof's bottom row: the next row steps up behind it
        front = (x - fx, y, z - fz)
        if not is_table(blocks, front) and not (passable(blocks.get(front)) and not passable(blocks.get((front[0], y - 1, front[2])))):
            continue  # no floor for the sitter's feet in front of it: an eave, a cornice, a step
        tables = [f for f, (dx, _, dz) in FACING.items() if is_table(blocks, (x + dx, y, z + dz))]
        if not tables or OPPOSITE[props["facing"]] in tables:
            continue
        problems.append(f"chair {s}@{x},{y},{z} faces {props['facing']}, its back to the table on its "
                        f"{'/'.join(tables)} side (to face it: facing={OPPOSITE[tables[0]]})")
    return problems


# --- Doorways a villager fits through (B170, B173, B178, B487) -------------------------------------------------
# A villager is 1.95 blocks tall. Under a lintel two blocks up it fits with 0.05 to spare, so any step up in or beside
# the doorway (a carpet is 1/16, a slab half a block) puts its head into the lintel: it stands in the doorway for good.
VILLAGER_HEIGHT = 1.95


def raise_of(block):
    """How far standing in the cell with {@code block} lifts the feet above the cell's floor (0: not a step up). A bed
    counts (B178): it is 9/16 high, under a villager's 0.6 step, so a villager's path runs over one like a slab."""
    if block is None:
        return 0
    s, props = _short(block), block[1]
    if s.endswith("_bed"):
        return 9 / 16
    if s.endswith("_carpet"):
        return 1 / 16
    if s.endswith("_stairs") and props.get("half") == "bottom":
        return 1  # its back half: a villager's 0.6 is still half in the doorway when it climbs onto that (B173, the Manor)
    if s.endswith("_slab") and props.get("type") == "bottom":
        return 0.5
    if s.endswith("trapdoor") and props.get("half") == "bottom" and props.get("open") != "true":
        return 3 / 16
    if s == "snow":
        return (int(props.get("layers", "1")) - 1) / 8
    return 0


def doorway_problems(blocks, only=None):
    """Doorways (doors, and open gaps up to three wide under a lintel) where a step up in the doorway or right in front
    of or behind it leaves a villager less than its height under the lintel."""
    def open_at(x, y, z):
        return passable(blocks.get((x, y, z)))

    def lintel(x, y, z):
        """Room from the floor at y to the lintel over the doorway cell, or None when there is no lintel two up."""
        top = blocks.get((x, y + 2, z))
        if open_at(x, y + 2, z):
            return None
        return 2.5 if _short(top).endswith("_slab") and top[1].get("type") == "top" else 2

    def opening(x, y, z):
        return open_at(x, y, z) and open_at(x, y + 1, z) and lintel(x, y, z) is not None

    doorways = {}  # (x, y, z) -> the axis people walk through it along
    for (x, y, z), block in blocks.items():
        if only is not None and (x, y, z) not in only:
            continue
        s, props = _short(block), block[1]
        if s.endswith("_door") and props.get("half") == "lower" and opening(x, y, z):
            doorways[(x, y, z)] = (0, 1) if props["facing"] in ("north", "south") else (1, 0)
    # Open gaps (and the gap between a double door's leaves): a run of openings across a wall, walls at both ends
    for (x, y0, z), floor in list(blocks.items()):
        c = (x, y0 + 1, z)
        if passable(floor) or _thin(floor) or raise_of(floor) or _short(floor).endswith("_slab") or c in doorways or only is not None and c not in only or not opening(*c):
            continue
        y = c[1]
        for ax, az in ((0, 1), (1, 0)):
            sx, sz = az, ax  # across the doorway
            run = [c]
            for d in (1, -1):
                q = (x + d * sx, y, z + d * sz)
                while run is not None and opening(*q) and len(run) <= 3:
                    run.append(q)
                    q = (q[0] + d * sx, y, q[2] + d * sz)
                if run is None or opening(*q) or open_at(q[0], y + 1, q[2]) or _thin(blocks[(q[0], y + 1, q[2])]):
                    run = None  # too wide, or no wall at the end (a post isn't one: an arbor's): not a doorway
            if run is None or len(run) > 3:
                continue
            # People walk through it: open (or a step) on both sides, at head height too, and taller on one side at
            # least (a doorway is lower than the rooms it joins: not the top of a crate under a cellar's ceiling)
            sides = [(x + d * ax, y, z + d * az) for d in (1, -1)]
            if all((open_at(*q) or raise_of(blocks.get(q))) and open_at(q[0], y + 1, q[2]) for q in sides) \
                    and any(open_at(q[0], y + 2, q[2]) for q in sides):
                doorways.setdefault(c, (ax, az))
    def lift_of(cell):
        """How far a villager's feet rise in {@code cell}: a step (raise_of), or a whole block's top (B487: a sill of
        upside-down stairs left under a window made a door, a dais or a raised floor right behind the door)."""
        block = blocks.get(cell)
        if raise_of(block):
            return raise_of(block)
        if block is None or _thin(block):
            return 0
        return 0 if passable(block) else 1

    problems = []
    for (x, y, z), (ax, az) in sorted(doorways.items()):
        room = lintel(x, y, z)
        for cell in ((x, y, z), (x + ax, y, z + az), (x - ax, y, z - az)):
            lift = lift_of(cell)
            door = _short(blocks.get((x, y, z)))
            if lift == 1 and cell != (x, y, z) and door.endswith("_door") and door != "iron_door" \
                    and not open_at(cell[0], y + 1, cell[2]) and not _thin(blocks[(cell[0], y + 1, cell[2])]):
                over = _short(blocks.get((cell[0], y + 1, cell[2])))
                problems.append(f"doorway@{x},{y},{z}: {_short(blocks.get(cell))}@{cell[0]},{y},{cell[2]} and {over} "
                                f"over it: the door opens onto a wall (an iron door may: no villager opens one)")
            elif lift and open_at(cell[0], y + 1, cell[2]) and room - lift < VILLAGER_HEIGHT:
                where = "in" if cell == (x, y, z) else "beside"
                problems.append(f"doorway@{x},{y},{z}: {_short(blocks.get(cell))}@{cell[0]},{y},{cell[2]} {where} it "
                                f"leaves {room - lift:.2f} under the lintel, a villager needs {VILLAGER_HEIGHT}")
    return problems


# --- Headroom over steps anywhere (B197) --------------------------------------------------------------------------
# Not only in doorways: a step a villager walks onto lifts it by the step's height. The game's path finder lets a
# villager stand where the two cells its body is in (1.95 rounds up to 2) are open, wherever the feet are inside the
# lower one, so a carpet (or a shut trapdoor, or snow) under a ceiling two blocks over the floor is a place to walk:
# the villager steps to its edge, head in the ceiling (1.94 of room), and stands there for good. On a slab, a stair or
# a bed the feet are in the cell above it, so the finder wants the two cells over the step open: under solid blocks
# that is room enough, but not under a shut hatch (a trapdoor) in the second cell, which the finder walks under.
def ceiling_over(blocks, x, y, z, top):
    """How far over the floor of cell (x, y, z) the first thing a head hits is, or None when nothing is (up to
    {@code top}, the build's height)."""
    for y2 in range(y + 1, top + 1):
        block = blocks.get((x, y2, z))
        gap = y2 - y
        if block is not None and _short(block).endswith("trapdoor") and block[1].get("open") != "true":
            return gap + (13 / 16 if block[1].get("half") == "top" else 0)  # shut, a hatch is solid to walk under
        if passable(block):
            continue
        s, props = _short(block), block[1]
        if s.endswith("_slab") and props.get("type") == "top":
            return gap + 0.5
        if s.endswith("lantern") and props.get("hanging") == "true":
            return gap + 1 / 16
        return gap
    return None


def headroom_problems(blocks, w, h, d, deep=0, drawn=None, only=None):
    """Steps (raise_of) a villager walks onto, from the floor beside them or from a step beside them, that leave it
    less than its height under the ceiling. A villager walks where a player walks (reachable) and where the path
    finder takes it (the cells its body is in open, see above); a table's carpet, a seat on a post, a bench under an
    eave too low to stand on, a roof's stairs nobody gets to aren't walked onto."""
    nodes, seen = reachable(blocks, w, h, d, deep, drawn)
    problems = []
    for (x, y, z), block in sorted(blocks.items()):
        if only is not None and (x, y, z) not in only:
            continue
        lift = raise_of(block)
        feet = y if passable(block) else y + 1  # a carpet, trapdoor or snow is in the feet's cell; a slab under it
        if not lift or not all(passable(blocks.get((x, c, z))) for c in (feet, feet + 1)):
            continue
        under = blocks.get((x, y - 1, z))
        earth = under is None and y <= 0 and (drawn is None or (x, y - 1, z) not in drawn)
        if _short(block).endswith("_carpet") and not earth and (passable(under) or _thin(under)):
            continue  # a carpet on nothing a villager stands on: a table top on a post, an awning
        room = ceiling_over(blocks, x, y, z, h)
        if room is None or room - lift >= VILLAGER_HEIGHT:
            continue
        # Stepped onto: a place a player gets to on this cell, or beside it on the same floor (or a step up from it)
        levels = (2 * y, 2 * y + 1, 2 * y + 2)
        near = [(x, z)] + [(x + dx, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))]
        if not any((cx, cz, L) in seen for cx, cz in near for L in levels):
            continue
        problems.append(f"{_short(block)}@{x},{y},{z} leaves {room - lift:.2f} under the ceiling, a villager needs "
                        f"{VILLAGER_HEIGHT} (raise the ceiling or take the step away)")
    return problems


# --- Upper floors a player can reach (B149) -----------------------------------------------------------------------
# Heights are in half blocks (a slab's step). A player walks, steps up half a block (stairs, slabs), falls any
# distance and climbs ladders; jumping onto furniture doesn't count as a way up.
PASS_NAMES = {"air", "cave_air", "structure_void", "jigsaw", "ladder", "vine", "torch", "wall_torch", "soul_torch",
              "soul_wall_torch", "redstone_torch", "redstone_wall_torch", "lever", "tripwire", "tripwire_hook", "string",
              "redstone_wire", "glow_lichen", "snow", "water", "light", "cobweb", "scaffolding", "dead_bush",
              "short_grass", "tall_grass", "fern", "large_fern", "seagrass", "tall_seagrass", "kelp", "kelp_plant",
              "poppy", "dandelion", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy", "cornflower",
              "lily_of_the_valley", "torchflower", "wither_rose", "rose_bush", "peony", "lilac", "sunflower",
              "pink_petals", "sugar_cane", "wheat", "carrots", "potatoes", "beetroots", "nether_sprouts",
              "crimson_roots", "warped_roots", "hanging_roots", "spore_blossom", "weeping_vines", "weeping_vines_plant",
              "twisting_vines", "twisting_vines_plant", "cave_vines", "cave_vines_plant", "rail", "powered_rail",
              "detector_rail", "activator_rail", "fire", "soul_fire"}
PASS = ("_door", "_carpet", "pressure_plate", "_button", "_sign", "_banner", "trapdoor", "fence_gate", "_sapling",
        "_tulip", "_propagule")
CLIMB = ("ladder", "vine", "scaffolding")
FURNITURE = ("_bed", "chest", "barrel", "_carpet", "lantern", "candle", "potted_", "flower_pot", "lectern", "hay_block",
             "crafting_table", "cartography_table", "smithing_table", "fletching_table", "loom", "anvil", "cauldron",
             "composter", "brewing_stand", "_banner", "bell", "furnace", "smoker", "grindstone", "stonecutter")
SEATING = ("_bed", "chest", "barrel", "lectern", "crafting_table", "cartography_table", "smithing_table",
           "fletching_table", "loom", "anvil", "brewing_stand", "furnace", "smoker", "grindstone", "stonecutter")
THIN = ("pane", "_bars", "chain", "candle", "_head", "skull", "end_rod", "lightning_rod", "campfire", "cake", "bell",
        "pointed_dripstone", "amethyst_bud", "amethyst_cluster", "lantern", "flower_pot", "potted_")


def passable(block):
    if block is None:
        return True
    s = _short(block)
    return s in PASS_NAMES or s.endswith(PASS)


def _thin(block):
    """Blocks a player can't stand on or step onto: posts, panes, chains, small things."""
    s = _short(block)
    if "gate" in s:
        return False
    return s.endswith("_fence") or s.endswith("_wall") or any(t in s for t in THIN)


def walk_nodes(blocks, w, h, d, deep=0, drawn=None):
    """Where a player can stand, as (x, z, feet height in half blocks) -> True when on a ladder (or vine). Under y = 0
    a build with a basement ({@code deep} blocks) is dug into the ground: a cell not {@code drawn} there is earth."""
    def earth(x, y, z):
        return y < -deep or y < 0 and (drawn is None or (x, y, z) not in drawn)

    def free(x, y, z):
        return y >= h or not earth(x, y, z) and passable(blocks.get((x, y, z)))

    def support(x, y, z):
        if earth(x, y, z):
            return True  # the ground the build stands on
        block = blocks.get((x, y, z))
        if block is None or passable(block) or _thin(block):
            return False
        return not (_short(block).endswith("_slab") and block[1].get("type") == "bottom")

    nodes = {}
    for x in range(-1, w + 1):
        for z in range(-1, d + 1):
            for y in range(-deep, h + 1):
                block = blocks.get((x, y, z))
                if free(x, y, z) and free(x, y + 1, z):
                    climb = block is not None and (any(t in _short(block) for t in CLIMB)
                                                   # a hatch over a ladder: opened, the game lets a player climb on through it
                                                   or _short(block).endswith("trapdoor") and "ladder" in _short(blocks.get((x, y - 1, z))))
                    if climb or support(x, y - 1, z):
                        nodes[(x, z, 2 * y)] = climb
                s = _short(block)
                half = (s.endswith("_slab") and block[1].get("type") == "bottom") or s.endswith("_bed") or (
                    s.endswith("_stairs") and block[1].get("half") == "bottom")
                if half and free(x, y + 1, z) and free(x, y + 2, z):
                    nodes[(x, z, 2 * y + 1)] = False
    return nodes


def reachable(blocks, w, h, d, deep=0, drawn=None):
    """The places to stand (walk_nodes) and those of them a player gets to from the ground floor round the build."""
    nodes = walk_nodes(blocks, w, h, d, deep, drawn)
    by_col = {}
    for (x, z, L) in nodes:
        by_col.setdefault((x, z), []).append(L)

    def clear(x, z, lo, hi):
        return all(y >= h or (y >= 0 or drawn is not None and (x, y, z) in drawn) and passable(blocks.get((x, y, z)))
                   for y in range(max(lo, -deep), hi + 1))

    def moves(node):
        x, z, L = node
        climb = nodes[node]
        out = [(x, z, L2) for L2 in by_col[(x, z)] if L2 == L + 1 or (climb and L2 == L + 2)]
        reach = L + (2 if climb else 1)  # off a ladder's top rung onto a floor; else half a block up
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for L2 in by_col.get((x + dx, z + dz), ()):
                if L2 <= reach and clear(x + dx, z + dz, (L2 + 1) // 2, (max(L, L2) + 3) // 2):
                    out.append((x + dx, z + dz, L2))
        return out

    seen = {n for n in nodes if 0 <= n[2] <= 2}  # the ground floor (a basement's floors are below 0)
    todo = list(seen)
    while todo:
        for m in moves(todo.pop()):
            if m not in seen:
                seen.add(m)
                todo.append(m)
    return nodes, seen


def reach_problems(blocks, w, h, d, deep=0, drawn=None):
    """Upper floors a player can't get to: places to stand two or more blocks over the ground, under a roof, four or
    more together, that can't be walked, stepped (stairs and slabs) or climbed (ladders) to from the ground floor."""
    nodes, seen = reachable(blocks, w, h, d, deep, drawn)
    # Places to stand that weren't reached, two or more blocks up or below ground, inside the build: under something
    # (a room, a basement) or open to the sky (a stand, a terrace, a balcony, a wall's walk: B154)
    def roofed(x, z, L):
        return any(not passable(blocks.get((x, y, z))) for y in range(L // 2 + 2, h))

    lost = {n for n in nodes if n not in seen and (n[2] >= 6 or n[2] < 0) and 0 <= n[0] < w and 0 <= n[1] < d}
    # A floor is furnished: the space over a ceiling, the top of a wall or of a glass case in a tall room, a statue's
    # hollow inside aren't floors anybody means to stand on
    def lived_in(x, z, L):
        y = (L + 1) // 2
        return any(_short(blocks.get(q)).endswith(FURNITURE) or _short(blocks.get(q)) in FURNITURE
                   for q in ((x, y, z), (x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1)))

    # Open to the sky, a place is meant to be stood on when people sit or work there: a seat (a carpet underfoot on a
    # terrace or a stand, not on an awning or a roof one block thick) or furniture beside it. Lanterns, pots, banners
    # and bells don't count: they stand on roofs and parapets too.
    def seated(x, z, L):
        y = (L + 1) // 2
        if L % 2 == 0 and _short(blocks.get((x, y, z))).endswith("_carpet") and not passable(blocks.get((x, y - 2, z))):
            return True
        return any(_short(blocks.get(q)).endswith(SEATING) or _short(blocks.get(q)) in SEATING
                   for q in ((x, y, z), (x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1)))

    problems, done = [], set()
    for n in sorted(lost):
        if n in done:
            continue
        group, todo = {n}, [n]
        while todo:
            x, z, L = todo.pop()
            for m in [(x + dx, z + dz, L + dl) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) for dl in (-1, 0, 1)]:
                if m in lost and m not in group:
                    group.add(m)
                    todo.append(m)
        done |= group
        cols = {(g[0], g[1]) for g in group}
        xs, zs, ys = [g[0] for g in group], [g[1] for g in group], [g[2] // 2 for g in group]
        where = f"at y {min(ys)} (x {min(xs)}-{max(xs)}, z {min(zs)}-{max(zs)}, {len(group)} places to stand)"
        if any({(x + 1, z), (x, z + 1), (x + 1, z + 1)} <= cols for x, z in cols) \
                and any(roofed(*g) and lived_in(*g) for g in group):  # a room, not a beam, a ledge or a sealed hollow
            problems.append(f"{'a basement' if min(ys) < 0 else 'an upper floor'} {where} has no stairs or ladder up to it")
        elif sum(1 for g in group if not roofed(*g) and seated(*g)) >= 4:  # a row of seats under the sky, not a roof
            problems.append(f"an open stand or terrace {where} has no stairs or ladder up to it")
    return problems
