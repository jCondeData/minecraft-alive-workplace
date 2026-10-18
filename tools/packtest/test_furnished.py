#!/usr/bin/env python3
"""Tests for furnished.py's verdict (37.11): both builds finished with Cozy Home pieces and nothing off passes;
anything skipped, wrong, unfinished, missing or without Cozy Home fails. Run after changing it:

  python3 tools/packtest/test_furnished.py
"""
import json
import os
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from furnished import parse, verdict, write_config  # noqa: E402

PREFIX = "[12:00:00] [Server thread/INFO]: "
GOOD = (PREFIX + "Furnished result: starter_cottage finished, 0 skipped, 0 blocks wrong, 0 pieces not standing, pieces "
        "beautify 4, cozyhome 9, handcrafted 6, Cozy Home blocks spruce_chair spruce_grandfather_clock spruce_table; "
        "inn finished, 0 skipped, 0 blocks wrong, 0 pieces not standing, pieces beautify 8, cozyhome 14, handcrafted 12, "
        "Cozy Home blocks oak_lamp oak_sofa; order cozyhome, handcrafted, cobblefurnies, beautify; in 41200 ticks")

order, builds = parse(GOOD)
assert order == ["cozyhome", "handcrafted", "cobblefurnies", "beautify"], order
assert builds["inn"]["pieces"] == {"beautify": 8, "cozyhome": 14, "handcrafted": 12}, builds["inn"]
assert builds["starter_cottage"]["blocks"] == ["spruce_chair", "spruce_grandfather_clock", "spruce_table"]
assert verdict(GOOD) == (True, []), verdict(GOOD)

# A skipped step, a wrong block or a piece not standing fails.
for good, bad in (("0 skipped", "2 skipped"), ("0 blocks wrong", "3 blocks wrong"), ("0 pieces not standing", "1 pieces not standing")):
    ok, problems = verdict(GOOD.replace(good, bad, 1))
    assert not ok and len(problems) == 1 and problems[0].startswith("starter_cottage"), problems

# An unfinished Inn fails, with its stage.
ok, problems = verdict(GOOD.replace("inn finished", "inn unfinished (STRUCTURE, missing 2 kinds)"))
assert not ok and problems == ["inn: unfinished (STRUCTURE, missing 2 kinds)"], problems

# No Cozy Home piece in a build (the mod not loaded, or not chosen) fails.
ok, problems = verdict(GOOD.replace("beautify 4, cozyhome 9, handcrafted 6, Cozy Home blocks spruce_chair spruce_grandfather_clock spruce_table",
                                    "handcrafted 19"))
assert not ok and "no cozyhome piece" in problems[0], problems
ok, problems = verdict(GOOD.replace("pieces beautify 8, cozyhome 14, handcrafted 12, Cozy Home blocks oak_lamp oak_sofa", "pieces none"))
assert not ok and problems[0].startswith("inn: no cozyhome"), problems

# The default order (Handcrafted first) isn't the test's.
ok, problems = verdict(GOOD.replace("order cozyhome, handcrafted", "order handcrafted, cozyhome"))
assert not ok and "doesn't start with Cozy Home" in problems[0], problems

# A build missing from the line, or no line at all, fails.
ok, problems = verdict(GOOD.split("; inn")[0] + "; order cozyhome; in 5 ticks")
assert not ok and problems == ["inn: not in the result"], problems
assert verdict(None)[0] is False

# The config: furnishedBuilds on and the order written, the file's other options kept.
with tempfile.TemporaryDirectory() as d:
    path = os.path.join(d, "config", "aliveworkplace.json")
    write_config(path, ["cozyhome", "handcrafted"])
    os.makedirs(d, exist_ok=True)
    with open(path) as f:
        assert json.load(f) == {"furnishedBuilds": True, "furnitureMods": ["cozyhome", "handcrafted"]}
    with open(path, "w") as f:
        json.dump({"workerPathRange": 64, "furnitureMods": []}, f)
    write_config(path, ["cozyhome"])
    with open(path) as f:
        assert json.load(f) == {"workerPathRange": 64, "furnitureMods": ["cozyhome"], "furnishedBuilds": True}

print("test_furnished: 11 checks pass")
