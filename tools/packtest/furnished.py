#!/usr/bin/env python3
"""The pack test's furnished builds (ROADMAP 37.11), used by tools/packtest/run.sh with FURNISHED=true.

  furnished.py --config <aliveworkplace.json> <mod,mod,...>   # before the boot: furnishedBuilds on, furnitureMods in that order
  furnished.py <server.log>                                   # after the run: judges the "Furnished result:" line

The verdict passes only when the Starter Cottage and the Inn both finished, with nothing skipped, no block other than
planned, every furniture piece standing, and pieces of the first mod in the order (Cozy Home on the pack test) in each.
"""
import json
import os
import re
import sys

BUILDS = ("starter_cottage", "inn")
PART = re.compile(r"^(\S+) (finished|unfinished.*?), (\d+) skipped, (\d+) blocks wrong, (\d+) pieces not standing, "
                  r"pieces (.*?)(?:, Cozy Home blocks (.*))?$")


def write_config(path, mods):
    """Sets furnishedBuilds on and furnitureMods to mods in the config file, keeping its other options."""
    config = {}
    if os.path.exists(path):
        with open(path) as f:
            config = json.load(f)
    config["furnishedBuilds"] = True
    config["furnitureMods"] = mods
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "w") as f:
        json.dump(config, f, indent=2)


def parse(line):
    """The order and each build's figures from a "Furnished result:" line."""
    body = line.split("Furnished result:", 1)[1].strip()
    order, builds = [], {}
    for part in body.split("; "):
        if part.startswith("order "):
            order = [m.strip() for m in part[len("order "):].split(",") if m.strip()]
            continue
        m = PART.match(part)
        if m is None:
            continue
        pieces = {}
        if m.group(6) != "none":
            for item in m.group(6).split(", "):
                mod, n = item.rsplit(" ", 1)
                pieces[mod] = int(n)
        builds[m.group(1)] = {
            "finished": m.group(2) == "finished", "state": m.group(2), "skipped": int(m.group(3)),
            "wrong": int(m.group(4)), "not_standing": int(m.group(5)), "pieces": pieces,
            "blocks": (m.group(7) or "").split(),
        }
    return order, builds


def verdict(line):
    """(passed, the reasons it didn't) for a result line, or for None when the run didn't end."""
    if line is None:
        return False, ["No Furnished result line: the run didn't end."]
    order, builds = parse(line)
    problems = []
    if not order or order[0] != "cozyhome":
        problems.append(f"the order in effect doesn't start with Cozy Home: {', '.join(order) or 'none'}")
    first = order[0] if order else "cozyhome"
    for name in BUILDS:
        b = builds.get(name)
        if b is None:
            problems.append(f"{name}: not in the result")
            continue
        if not b["finished"]:
            problems.append(f"{name}: {b['state']}")
        for key, words in (("skipped", "steps skipped"), ("wrong", "blocks not as planned"), ("not_standing", "pieces not standing")):
            if b[key]:
                problems.append(f"{name}: {b[key]} {words}")
        if b["pieces"].get(first, 0) == 0:
            problems.append(f"{name}: no {first} piece (pieces: {b['pieces'] or 'none'})")
    return not problems, problems


def main(argv):
    if len(argv) >= 3 and argv[0] == "--config":
        write_config(argv[1], [m.strip().lower() for m in argv[2].split(",") if m.strip()])
        return 0
    if len(argv) != 1:
        print(__doc__)
        return 2
    with open(argv[0], errors="replace") as f:
        line = next((l for l in f if "Furnished result:" in l), None)
    ok, problems = verdict(line)
    if line is not None:
        order, builds = parse(line)
        for name in BUILDS:
            b = builds.get(name)
            if b:
                print(f"Furnished {name}: {b['state']}, {b['skipped']} skipped, {b['wrong']} blocks wrong, "
                      f"{b['not_standing']} pieces not standing, pieces {b['pieces']}, Cozy Home blocks {' '.join(b['blocks']) or 'none'}")
    for p in problems:
        print("  " + p)
    print("Furnished passed." if ok else "The furnished run did not pass.")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
