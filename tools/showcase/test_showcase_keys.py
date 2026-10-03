#!/usr/bin/env python3
"""Raw text keys the mod owns but whose key has no "aliveworkplace" in it (entity.minecraft.villager.<job>,
structure.minecraft.<village>) fail a scene; another mod's untranslated strings don't. Found by the tester (ROADMAP 22.4).

  python3 tools/showcase/test_showcase_keys.py
"""
import json
import os
import shutil
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)

import test_showcase as t  # noqa: E402


def main():
    tmp = tempfile.mkdtemp(prefix="showcase-bugs-")
    try:
        # Bug: text the mod owns but whose key has no "aliveworkplace" in it (every profession's name,
        # entity.minecraft.villager.<job>, 59 such entity keys, 22 structure names and 8 game rules in our en_us.json)
        # is treated as another mod's: Showcase.missingKey files it under otherMissingKeys and process.py makes it a
        # note. Repro: delete "entity.minecraft.villager.builder" from en_us.json and run SCENE=hall: the hall's
        # tooltip shows "entity.minecraft.villager.builder" on screen and the scene is not failed for it.
        lang = json.load(open(os.path.join(ROOT, "src/main/resources/assets/aliveworkplace/lang/en_us.json"), encoding="utf-8"))
        key = "entity.minecraft.villager.builder"
        assert key in lang, "the test's premise: the mod's own lang file defines the builder's job name"
        r, _ = t.judge(tmp, "hall", other_keys=[key])
        t.check("judge: our own profession name shown as a raw key fails the scene (raw text keys fail, spec 22.4)",
                not r["pass"] and any(key in x for x in r["reasons"]), (r["pass"], r["reasons"], r["warnings"]))
        r, _ = t.judge(tmp, "village", other_keys=["structure.minecraft.village_plains"])
        t.check("judge: our own structure name shown as a raw key fails the scene", not r["pass"], (r["pass"], r["warnings"]))
        # Not a false alarm: a key no file of ours defines stays a note (Cobblemon's HUD asks for "0%").
        r, _ = t.judge(tmp, "battle", other_keys=["0%"])
        t.check("judge: another mod's untranslated string is still only a note", r["pass"], r["reasons"])
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    print(f"\n{len(t.failures)} failed" if t.failures else "\nall passed")
    sys.exit(1 if t.failures else 0)


if __name__ == "__main__":
    main()
