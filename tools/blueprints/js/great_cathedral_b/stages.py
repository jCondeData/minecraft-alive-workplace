#!/usr/bin/env python3
"""The Great Cathedral (version B)'s stage candidates (ROADMAP 52.6, briefs/great_cathedral.md): runs recipe.mjs once per stage (STAGE=n,
the Wonder as it stands after stage n; the recipe's verify() refuses a stage that changes or takes away an earlier
stage's block), loads the four snapshots into kit Builds, checks each stage standing on the stages before it the way
wonders.save_wonder does (check.py, the owner's rules included), saves the stage parts the way kit.wonder_stages does,
and runs holes.py on each stage as it stands. Everything goes to build/rebuild/great_cathedral_b/ (never
src/main/resources): wonder/great_cathedral_b/stage_<n>_<k>.nbt (the parts) and great_cathedral_b_stage<n>.nbt (the whole
Wonder after stage n, for renders and holes.py).

    MC_BUILDER=<minecraft-builder skill> python3 tools/blueprints/js/great_cathedral_b/stages.py

To ship it (the ship step, after the critic): wonders.WONDERS["great_cathedral_b"]["draw"] = staged() below, then
    MC_BUILDER=<minecraft-builder skill> python3 -c "import sys; sys.path.insert(0, 'tools/blueprints'); import wonders; wonders.save_wonder('great_cathedral_b')"
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
BLUEPRINTS = os.path.normpath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, BLUEPRINTS)

import holes  # noqa: E402
from check import check  # noqa: E402
from kit import ROOT, Build, StagedBuild, wonder_stages  # noqa: E402

OUT = os.path.join(ROOT, "build", "rebuild", "great_cathedral_b")
STAGES = 4


def run_recipe(n):
    env = dict(os.environ, STAGE=str(n))
    env.pop("CUT", None)
    builder = env.get("MC_BUILDER", "/tmp/mcb")
    subprocess.run(["node", os.path.join(builder, "scripts", "run-build.mjs"), "recipe.mjs", "--out-dir", "out",
                    "--data-version", "3955"], cwd=HERE, env=env, check=True, stdout=subprocess.DEVNULL)
    return os.path.join(HERE, "out", f"great-cathedral-b-s{n}.nbt")  # the runner writes the name with a hyphen


def stage_build(n):
    (w, h, d), cells, states = holes.load(run_recipe(n), props=True)
    b = Build(w, h, d)
    for pos, name in cells.items():
        if name and name != "minecraft:air":
            b.set(*pos, name, **states[pos])
    return b


def staged():
    """The Great Cathedral (version B) as a kit StagedBuild, one stage per recipe snapshot (for wonders.WONDERS["great_cathedral_b"]["draw"]
    at the ship step; needs node and MC_BUILDER). Each snapshot only adds to the one before (the recipe refuses
    otherwise)."""
    snapshots = [stage_build(n) for n in range(1, STAGES + 1)]
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
    stages = [stage_build(n) for n in range(1, STAGES + 1)]
    problems = 0
    before = set()
    for n, s in enumerate(stages, 1):
        drawn = sum(1 for block in s.blocks.values() if block[0] != "minecraft:air")
        s.fill_air()
        sealed = sum(1 for block in s.blocks.values() if block[0] != "minecraft:air") - drawn
        if sealed:
            print(f"  CHECK wonder/great_cathedral_b stage {n}: kit.fill_air added {sealed} blocks the recipe doesn't draw")
            problems += 1
        mine = {p for p, block in s.blocks.items() if block[0] != "minecraft:air"} - before
        for problem in check(s, only=mine, whole=n == len(stages)):
            print(f"  CHECK wonder/great_cathedral_b stage {n}: {problem}")
            problems += 1
        print(f"wonder/great_cathedral_b stage {n}: {len(mine)} blocks")
        before |= mine
    parts = wonder_stages("aliveworkplace", "great_cathedral", [stage_build(n) for n in range(1, STAGES + 1)], [OUT])
    print(f"{sum(len(p) for p in parts)} parts; whole: {len(before)} blocks")
    paths = []
    for n, s in enumerate(stages, 1):
        s.save(OUT, f"great_cathedral_b_stage{n}", checked=False)
        paths.append(os.path.join(OUT, f"great_cathedral_b_stage{n}.nbt"))
    bad = holes.main(paths)
    return 1 if problems or bad else 0


if __name__ == "__main__":
    sys.exit(main())
