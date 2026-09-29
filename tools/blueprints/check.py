"""
Checks a build for the mistakes that show in the game, run on every save (the rules follow the lint of the Minecraft
Architect building skill): blocks nothing holds up, lanterns hanging from nothing, lanterns, pots, carpets and torches
standing on air, ladders with no wall behind them, doors missing a half, sand and gravel over air. The bottom layer
(y = 0) stands on the ground, so it holds itself up.
"""

AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:structure_void", "minecraft:jigsaw"}
FACING = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}
STANDS = ("lantern", "flower_pot", "potted_", "_carpet", "candle", "torch", "pressure_plate", "rail")
GRAVITY = ("minecraft:sand", "minecraft:red_sand", "minecraft:gravel", "concrete_powder", "anvil")


def check(b):
    """What's wrong with build {@code b}, as lines of text (empty if nothing)."""
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
    floating = sorted(p for p in blocks if p not in seen)
    if floating:
        problems.append(f"{len(floating)} block(s) held up by nothing, e.g. " + "; ".join(
            f"{blocks[p][0].split(':')[1]}@{p[0]},{p[1]},{p[2]}" for p in floating[:5]))
    for (x, y, z), (name, props) in sorted(blocks.items()):
        short = name.split(":")[1]
        below = blocks.get((x, y - 1, z))
        above = blocks.get((x, y + 1, z))
        bad = None
        if short == "lantern" or short == "soul_lantern":
            if props.get("hanging") == "true":
                bad = above is None and "hangs from nothing"
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
    return problems
