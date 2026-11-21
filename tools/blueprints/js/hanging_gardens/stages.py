#!/usr/bin/env python3
"""The Hanging Gardens's candidate saver (ROADMAP 52.6): runs recipe.mjs once per stage (STAGE=1..4, each the whole Wonder as it
stands after that stage), loads each into a kit Build, checks each stage standing on the stages before it the way
wonders.save_wonder does (check.py, the owner's rules included), saves the stage parts with kit.wonder_stages and each
whole stage as <id>.nbt, all into build/rebuild/hanging_gardens/ (never src/main/resources). Then run holes.py on the wholes.
    MC_BUILDER=<skill> python3 stages.py

Shipped (52.6): wonders.WONDERS["hanging_gardens"]["draw"] is staged() below, so the mod's parts and data file are saved with
    MC_BUILDER=<minecraft-builder skill> python3 -c "import sys; sys.path.insert(0, 'tools/blueprints'); import wonders; wonders.save_wonder('hanging_gardens')"
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
BP = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, BP)

from check import check, reachable  # noqa: E402
from holes import load  # noqa: E402
from kit import ROOT, Build, StagedBuild, wonder_stages  # noqa: E402

OUT = os.path.join(ROOT, "build", "rebuild", "hanging_gardens")


def stage_build(n):
    env = dict(os.environ, STAGE=str(n))
    env.pop("CUT", None)
    subprocess.run(["node", os.path.join(env["MC_BUILDER"], "scripts", "run-build.mjs"), "recipe.mjs", "--out-dir", "out",
                    "--data-version", "3955"], cwd=HERE, env=env, check=True, stdout=subprocess.DEVNULL)
    (w, h, d), cells, states = load(os.path.join(HERE, "out", f"hanging-gardens-s{n}.nbt"), props=True)
    b = Build(w, h, d)
    for (x, y, z), name in cells.items():
        if name and name != "minecraft:air":
            b.set(x, y, z, name, **states[(x, y, z)])
    return b


def staged():
    """The Hanging Gardens as a kit StagedBuild, one stage per recipe snapshot: wonders.WONDERS["hanging_gardens"]["draw"] (52.6,
    shipped with wonders.save_wonder; needs node and MC_BUILDER). The recipe never replaces an earlier stage's block, so
    each snapshot only adds to the one before."""
    snapshots = [stage_build(n) for n in range(1, 5)]
    last = snapshots[-1]
    b = StagedBuild(last.w, last.h, last.d)
    for n, s in enumerate(snapshots, 1):
        b.begin(n)
        for pos, block in s.blocks.items():
            if block[0] == "minecraft:air" or pos in b.blocks:
                continue
            b.blocks[pos] = block
    return b


def main():
    stages = [stage_build(n) for n in range(1, 5)]
    before = set()
    problems = 0
    for n, s in enumerate(stages, 1):
        drawn = sum(1 for block in s.blocks.values() if block[0] != "minecraft:air")
        s.fill_air()
        sealed = sum(1 for block in s.blocks.values() if block[0] != "minecraft:air") - drawn
        if sealed:  # kit.seal_slots / seal_peeks filled a slot the recipe left: draw it there, so verify() counts it too
            print(f"  CHECK wonder/hanging_gardens stage {n}: kit.fill_air added {sealed} blocks the recipe doesn't draw")
            problems += 1
        mine = {p for p, block in s.blocks.items() if block[0] != "minecraft:air"} - before
        for problem in check(s, only=mine, whole=n == len(stages)):
            print(f"  CHECK wonder/hanging_gardens stage {n}: {problem}")
            problems += 1
        print(f"wonder/hanging_gardens stage {n}: {len(mine)} blocks")
        before |= mine
        s.save(OUT, f"hanging_gardens_stage_{n}")
    # every stage, as the game finishes it (stair corners, wall and pane joins), keeps every block the stage before left
    # exactly as it was: kit.StagedBuild would silently drop a change, so it is refused here
    for n in range(1, len(stages)):
        for pos, block in stages[n - 1].blocks.items():
            if block[0] != "minecraft:air" and stages[n].blocks.get(pos) != block:
                print(f"  CHECK wonder/hanging_gardens stage {n + 1} changes {block} at {pos} to {stages[n].blocks.get(pos)}")
                problems += 1
    # the ramp test (QaWonderDesigns1105GameTests.qaGardensRampReachesEveryFarmAndTheCrown): from the ground round the
    # site a player gets within 2 across and 1 up or down of every chest, and stands at y 24 or higher
    for n, s in enumerate(stages, 1):
        blocks = {p: (b[0], dict(b[1])) for p, b in s.blocks.items() if b[0] != "minecraft:air"}
        _, seen = reachable(blocks, s.w, s.h, s.d)
        top = max(L for _, _, L in seen) / 2
        for (cx, cy, cz), b in blocks.items():
            if b[0] == "minecraft:chest" and not any(abs(x - cx) + abs(z - cz) <= 2 and abs(L / 2 - cy) <= 1 for x, z, L in seen):
                print(f"  CHECK wonder/hanging_gardens stage {n}: the chest at {cx},{cy},{cz} can't be walked to")
                problems += 1
        print(f"wonder/hanging_gardens stage {n}: highest place to stand, feet at y {top}")
        if n == len(stages) and top < 24:
            print(f"  CHECK wonder/hanging_gardens: nowhere to stand at y 24 or higher")
            problems += 1
    parts = wonder_stages("aliveworkplace", "hanging_gardens", [stage_build(n) for n in range(1, 5)], [OUT])
    print(f"{sum(len(p) for p in parts)} parts; whole: {len(before)} blocks")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
