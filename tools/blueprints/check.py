"""
Checks a build for the mistakes that show in the game, run on every save (the rules follow the lint of the Minecraft
Architect building skill): blocks nothing holds up, lanterns hanging from nothing, lanterns, pots, carpets and torches
standing on air, ladders with no wall behind them, doors missing a half, sand and gravel over air. The bottom layer
(y = 0) stands on the ground, so it holds itself up. And two the owner met in a playtest (B148, B149): chairs with their
backs to the table, and upper floors no stairs or ladder lead up to; and since B154 open-air stands and terraces too.
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


def check(b, only=None, whole=None):
    """What's wrong with build {@code b}, as lines of text (empty if nothing). {@code only}: the positions to report on
    (a Wonder's stage, checked standing on the stages before it); None for all of them. {@code whole}: whether the
    build is finished, so its upper floors must be reachable (default: when {@code only} is None)."""
    blocks = {p: (n, dict(props)) for p, (n, props) in b.blocks.items() if n not in AIR}
    problems = []
    # Floating: flood from the bottom layer through face and edge neighbours (stair roofs meet row to row at an edge).
    steps = [(dx, dy, dz) for dx in (-1, 0, 1) for dy in (-1, 0, 1) for dz in (-1, 0, 1) if 1 <= abs(dx) + abs(dy) + abs(dz) <= 2]
    seen = {p for p in blocks if p[1] == 0}
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
    for (x, y, z), (name, props) in sorted(blocks.items()):
        if only is not None and (x, y, z) not in only:
            continue
        short = name.split(":")[1]
        below = blocks.get((x, y - 1, z))
        above = blocks.get((x, y + 1, z))
        bad = None
        if short == "lantern" or short == "soul_lantern":
            if props.get("hanging") == "true":
                bad = (above is None and "hangs from nothing") or (not holds_centre(above) and "hangs from " + above[0][10:]
                                                                   + " (the game drops it: nothing under its middle)")
            else:
                bad = y > 0 and below is None and "stands on air"
        elif short == "ladder":
            dx, dy, dz = FACING[props["facing"]]
            bad = (x - dx, y, z - dz) not in blocks and "has no wall behind it"
        elif short.endswith("_door"):
            other = (x, y + 1, z) if props.get("half") == "lower" else (x, y - 1, z)
            bad = (other not in blocks or blocks[other][0] != name) and "is missing a half"
        elif any(s in short for s in STANDS) and "wall_" not in short:
            bad = y > 0 and below is None and "stands on air"
        elif any(name.startswith(g) or g in name for g in GRAVITY):
            bad = y > 0 and below is None and "will fall"
        if bad:
            problems.append(f"{short}@{x},{y},{z} {bad}")
    problems += chair_problems(blocks, only)
    if (only is None) if whole is None else whole:
        problems += reach_problems(blocks, b.w, b.h, b.d)
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


def walk_nodes(blocks, w, h, d):
    """Where a player can stand, as (x, z, feet height in half blocks) -> True when on a ladder (or vine)."""
    def free(x, y, z):
        return y >= h or passable(blocks.get((x, y, z)))

    def support(x, y, z):
        if y < 0:
            return True  # the ground the build stands on
        block = blocks.get((x, y, z))
        if block is None or passable(block) or _thin(block):
            return False
        return not (_short(block).endswith("_slab") and block[1].get("type") == "bottom")

    nodes = {}
    for x in range(-1, w + 1):
        for z in range(-1, d + 1):
            for y in range(0, h + 1):
                block = blocks.get((x, y, z))
                if free(x, y, z) and free(x, y + 1, z):
                    climb = block is not None and any(t in _short(block) for t in CLIMB)
                    if climb or support(x, y - 1, z):
                        nodes[(x, z, 2 * y)] = climb
                s = _short(block)
                half = (s.endswith("_slab") and block[1].get("type") == "bottom") or s.endswith("_bed") or (
                    s.endswith("_stairs") and block[1].get("half") == "bottom")
                if half and free(x, y + 1, z) and free(x, y + 2, z):
                    nodes[(x, z, 2 * y + 1)] = False
    return nodes


def reach_problems(blocks, w, h, d):
    """Upper floors a player can't get to: places to stand two or more blocks over the ground, under a roof, four or
    more together, that can't be walked, stepped (stairs and slabs) or climbed (ladders) to from the ground floor."""
    nodes = walk_nodes(blocks, w, h, d)
    by_col = {}
    for (x, z, L) in nodes:
        by_col.setdefault((x, z), []).append(L)

    def clear(x, z, lo, hi):
        return all(y >= h or passable(blocks.get((x, y, z))) for y in range(max(lo, 0), hi + 1))

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

    seen = {n for n in nodes if n[2] <= 2}
    todo = list(seen)
    while todo:
        for m in moves(todo.pop()):
            if m not in seen:
                seen.add(m)
                todo.append(m)
    # Places to stand that weren't reached, two or more blocks up, inside the build: under something (a room) or open
    # to the sky (a stand, a terrace, a balcony, a wall's walk: B154)
    def roofed(x, z, L):
        return any(not passable(blocks.get((x, y, z))) for y in range(L // 2 + 2, h))

    lost = {n for n in nodes if n not in seen and n[2] >= 6 and 0 <= n[0] < w and 0 <= n[1] < d}
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
            problems.append(f"an upper floor {where} has no stairs or ladder up to it")
        elif sum(1 for g in group if not roofed(*g) and seated(*g)) >= 4:  # a row of seats under the sky, not a roof
            problems.append(f"an open stand or terrace {where} has no stairs or ladder up to it")
    return problems
