#!/usr/bin/env python3
"""The Observatory's stage candidates (ROADMAP 52.6, briefs/observatory.md): runs recipe.mjs once per stage (STAGE=n,
the Wonder as it stands after stage n), loads the four snapshots into a kit StagedBuild, checks each stage standing on
the stages before it the way wonders.save_wonder does (check.py, the owner's rules included), saves the stage parts
the way kit.wonder_stages does, and runs holes.py on each stage as it stands. Everything goes to
build/rebuild/observatory/ (never src/main/resources): wonder/observatory/stage_<n>_<k>.nbt (the parts) and
observatory_stage<n>.nbt (the whole Wonder after stage n, for renders and holes.py).

    MC_BUILDER=<minecraft-builder skill> python3 tools/blueprints/js/observatory/stages.py

Shipped (52.6): wonders.WONDERS["observatory"]["draw"] is staged() below, so the mod's parts and data file are saved with
    MC_BUILDER=<minecraft-builder skill> python3 -c "import sys; sys.path.insert(0, 'tools/blueprints'); import wonders; wonders.save_wonder('observatory')"
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
BLUEPRINTS = os.path.normpath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, BLUEPRINTS)

import holes  # noqa: E402
from check import check  # noqa: E402
from kit import ROOT, StagedBuild, wonder_stages  # noqa: E402

OUT = os.path.join(ROOT, "build", "rebuild", "observatory")
STAGES = 4


def run_recipe(n):
    builder = os.environ.get("MC_BUILDER", "/tmp/mcb")
    subprocess.run(["node", os.path.join(builder, "scripts", "run-build.mjs"), "recipe.mjs", "--out-dir", "out",
                    "--data-version", "3955"], cwd=HERE, env={**os.environ, "STAGE": str(n)}, check=True,
                   stdout=subprocess.DEVNULL)
    return os.path.join(HERE, "out", "observatory.nbt" if n == STAGES else f"observatory-stage{n}.nbt")  # the runner writes the name with a hyphen


def staged():
    """The Observatory as a kit StagedBuild, one stage per recipe snapshot: wonders.WONDERS["observatory"]["draw"]
    (52.6, shipped with wonders.save_wonder; needs node and MC_BUILDER)."""
    snapshots = [holes.load(run_recipe(n), props=True) for n in range(1, STAGES + 1)]
    (w, h, d), _, _ = snapshots[-1]
    b = StagedBuild(w, h, d)
    for n, (_, cells, states) in enumerate(snapshots, 1):
        b.begin(n)
        for pos, name in cells.items():
            if name is None or name == "minecraft:air" or pos in b.blocks:
                continue
            b.set(*pos, name, **states[pos])
    return b


def main():
    stages = staged().stage_builds()
    problems = 0
    before = set()
    for n, s in enumerate(stages, 1):
        mine = {p for p, block in s.blocks.items() if block[0] != "minecraft:air"} - before
        for problem in check(s, only=mine, whole=n == len(stages)):
            print(f"  CHECK wonder/observatory stage {n}: {problem}")
            problems += 1
        print(f"wonder/observatory stage {n}: {len(mine)} blocks")
        before |= mine
    assert len(stages) == STAGES, f"{len(stages)} stages drawn, the goods have {STAGES} lines"
    parts = wonder_stages("aliveworkplace", "observatory", stages, [OUT])
    print("parts:", parts)
    paths = []
    for n, s in enumerate(stages, 1):
        s.save(OUT, f"observatory_stage{n}", checked=False)
        paths.append(os.path.join(OUT, f"observatory_stage{n}.nbt"))
    bad = holes.main(paths)
    return 1 if problems or bad else 0


if __name__ == "__main__":
    sys.exit(main())
