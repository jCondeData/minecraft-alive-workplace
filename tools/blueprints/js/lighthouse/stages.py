#!/usr/bin/env python3
"""The Lighthouse's candidate saver (ROADMAP 49): runs recipe.mjs once per stage (STAGE=1..4, each the whole Wonder as it
stands after that stage), loads each into a kit Build, checks each stage standing on the stages before it the way
wonders.save_wonder does (check.py, the owner's rules included), saves the stage parts with kit.wonder_stages and each
whole stage as <id>.nbt, all into build/rebuild/lighthouse/ (never src/main/resources). Then run holes.py on the wholes.
    MC_BUILDER=<skill> python3 stages.py
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
BP = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, BP)

from check import check  # noqa: E402
from holes import load  # noqa: E402
from kit import ROOT, Build, wonder_stages  # noqa: E402

OUT = os.path.join(ROOT, "build", "rebuild", "lighthouse")


def stage_build(n):
    env = dict(os.environ, STAGE=str(n))
    env.pop("CUT", None)
    subprocess.run(["node", os.path.join(env["MC_BUILDER"], "scripts", "run-build.mjs"), "recipe.mjs", "--out-dir", "out",
                    "--data-version", "3955"], cwd=HERE, env=env, check=True, stdout=subprocess.DEVNULL)
    (w, h, d), cells, states = load(os.path.join(HERE, "out", f"lighthouse-s{n}.nbt"), props=True)
    b = Build(w, h, d)
    for (x, y, z), name in cells.items():
        if name and name != "minecraft:air":
            b.set(x, y, z, name, **states[(x, y, z)])
    return b


def main():
    stages = [stage_build(n) for n in range(1, 5)]
    before = set()
    problems = 0
    for n, s in enumerate(stages, 1):
        s.fill_air()
        mine = {p for p, block in s.blocks.items() if block[0] != "minecraft:air"} - before
        for problem in check(s, only=mine, whole=n == len(stages)):
            print(f"  CHECK wonder/lighthouse stage {n}: {problem}")
            problems += 1
        print(f"wonder/lighthouse stage {n}: {len(mine)} blocks")
        before |= mine
        s.save(OUT, f"lighthouse_stage_{n}")
    parts = wonder_stages("aliveworkplace", "lighthouse", [stage_build(n) for n in range(1, 5)], [OUT])
    print(f"{sum(len(p) for p in parts)} parts; whole: {len(before)} blocks")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
