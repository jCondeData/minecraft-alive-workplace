#!/usr/bin/env python3
"""The Great Wall's candidate saver (ROADMAP 52.6): runs recipe.mjs once per stage (STAGE=1..4, each the whole Wonder as it
stands after that stage), loads each into a kit Build, checks each stage standing on the stages before it the way
wonders.save_wonder does (check.py, the owner's rules included), saves the stage parts with kit.wonder_stages and each
whole stage as <id>.nbt, all into build/rebuild/great_wall/ (never src/main/resources). Then run holes.py on the wholes.
    MC_BUILDER=<skill> python3 stages.py

Shipped (52.6): wonders.WONDERS["great_wall"]["draw"] is staged() below, so the mod's parts and data file are saved with
    MC_BUILDER=<minecraft-builder skill> python3 -c "import sys; sys.path.insert(0, 'tools/blueprints'); import wonders; wonders.save_wonder('great_wall')"
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
BP = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, BP)

from check import check  # noqa: E402
from holes import load  # noqa: E402
from kit import ROOT, Build, StagedBuild, wonder_stages  # noqa: E402

OUT = os.path.join(ROOT, "build", "rebuild", "great_wall")


def stage_build(n):
    env = dict(os.environ, STAGE=str(n))
    env.pop("CUT", None)
    subprocess.run(["node", os.path.join(env["MC_BUILDER"], "scripts", "run-build.mjs"), "recipe.mjs", "--out-dir", "out",
                    "--data-version", "3955"], cwd=HERE, env=env, check=True, stdout=subprocess.DEVNULL)
    (w, h, d), cells, states = load(os.path.join(HERE, "out", f"great-wall-s{n}.nbt"), props=True)
    b = Build(w, h, d)
    for (x, y, z), name in cells.items():
        if name and name != "minecraft:air":
            b.set(x, y, z, name, **states[(x, y, z)])
    return b


def staged():
    """The Great Wall as a kit StagedBuild, one stage per recipe snapshot: wonders.WONDERS["great_wall"]["draw"] (52.6,
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
        s.fill_air()
        mine = {p for p, block in s.blocks.items() if block[0] != "minecraft:air"} - before
        for problem in check(s, only=mine, whole=n == len(stages)):
            print(f"  CHECK wonder/great_wall stage {n}: {problem}")
            problems += 1
        print(f"wonder/great_wall stage {n}: {len(mine)} blocks")
        before |= mine
        s.save(OUT, f"great_wall_stage_{n}")
    parts = wonder_stages("aliveworkplace", "great_wall", [stage_build(n) for n in range(1, 5)], [OUT])
    print(f"{sum(len(p) for p in parts)} parts; whole: {len(before)} blocks")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
