#!/usr/bin/env python3
"""Progress per release stage, from ROADMAP.md's ticks: python3 tools/agent/progress.py [--bars]

The digests (owner, 2026-10-05) put this at the top of every report, one line per stage, e.g. "1.4  [#####-----] 75%".
Stage names come from each milestone's heading, "(1.4)"; milestones without one keep their number.
"""
import re, sys, pathlib

text = (pathlib.Path(__file__).resolve().parents[2] / "ROADMAP.md").read_text()
for m in re.split(r"\n(?=## Milestone )", text)[1:]:
    head = m.split("\n")[0]
    done = len(re.findall(r"^\s*- \[x\]", m, re.M))
    todo = len(re.findall(r"^\s*- \[ \]", m, re.M))
    if not done + todo:
        continue
    pct = 100 * done // (done + todo)
    name = re.search(r"\((\d\.\d)\)\s*$", head)
    label = name.group(1) if name else "M" + re.search(r"Milestone (\d+)", head).group(1)
    title = re.sub(r"^## Milestone \d+: ", "", re.sub(r"\s*\(.*\)\s*$", "", head))
    print(f"{label:>4}  [{'#' * (pct // 10)}{'-' * (10 - pct // 10)}] {pct:3d}%  {done}/{done + todo}  {title}")
