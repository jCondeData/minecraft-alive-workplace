#!/usr/bin/env python3
"""Leaves only the named GameTest classes in the test mod's entrypoints (a CI or local run of just those tests).
    python3 tools/modtest/only_tests.py RepeatNewTests [OtherGameTests …]
    python3 tools/modtest/only_tests.py --compat RepeatNewTests [QaSomeCompatTests …]   # the compat suite's mod
Edits src/gametest/resources/fabric.mod.json (with --compat src/compattest/resources/fabric.mod.json) in place: never
commit the result (`git checkout` it afterwards). Names are simple class names of registered entrypoints.
"""
import json
import sys
PATH = "src/gametest/resources/fabric.mod.json"
COMPAT_PATH = "src/compattest/resources/fabric.mod.json"
names = sys.argv[1:]
path = PATH
if names and names[0] == "--compat":
    path = COMPAT_PATH
    names = names[1:]
if not names:
    sys.exit(__doc__)
with open(path, encoding="utf-8") as f:
    mod = json.load(f)
known = mod["entrypoints"]["fabric-gametest"]
by_name = {k.rsplit(".", 1)[-1]: k for k in known}
missing = [n for n in names if n not in by_name]
if missing:
    sys.exit(f"not registered in {path}: {', '.join(missing)}")
mod["entrypoints"]["fabric-gametest"] = [by_name[n] for n in names]
with open(path, "w", encoding="utf-8") as f:
    json.dump(mod, f, indent="\t")
    f.write("\n")
print(f"{path}: only {', '.join(names)}")
