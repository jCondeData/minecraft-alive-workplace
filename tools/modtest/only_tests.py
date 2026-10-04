#!/usr/bin/env python3
"""Leaves only the named GameTest classes in the test mod's entrypoints (a CI or local run of just those tests).

    python3 tools/modtest/only_tests.py RepeatNewTests [OtherGameTests …]

Edits src/gametest/resources/fabric.mod.json in place: never commit the result (`git checkout` it afterwards).
"""
import json
import sys

PATH = "src/gametest/resources/fabric.mod.json"
PACKAGE = "io.github.jcondedata.aliveworkplace.test."

names = sys.argv[1:]
if not names:
    sys.exit(__doc__)
with open(PATH, encoding="utf-8") as f:
    mod = json.load(f)
known = set(mod["entrypoints"]["fabric-gametest"])
wanted = [PACKAGE + n for n in names]
missing = [w for w in wanted if w not in known]
if missing:
    sys.exit(f"not registered in {PATH}: {', '.join(missing)}")
mod["entrypoints"]["fabric-gametest"] = wanted
with open(PATH, "w", encoding="utf-8") as f:
    json.dump(mod, f, indent="\t")
    f.write("\n")
print(f"{PATH}: only {', '.join(names)}")
