#!/usr/bin/env python3
"""Tests for wikicheck.py, on a throwaway copy of a tiny made-up mod (nothing touches the real wiki, except the last
check, which runs the checker on this repo). Run after changing the checker:

  python3 tools/modtest/test_wikicheck.py
"""
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
JAVA = "src/main/java/mod"
TESTS = "src/gametest/java/mod/test"

GUILDS_PAGE = """# Guilds

A Master with a charter leads a guild.

Roadmap items: 30.17,
30.18

## What a player sees

A charter makes a guild. `/workplace guilds` isn't a checked form, nor is `Guilds.PER_RANK` or `docs/wiki/README.md`.

## 2. How it works

Guilds are data: `aliveworkplace:builders`.

## Switches

| Key | Default | What it does |
|---|---|---|
| `guilds` | on | Guilds |
| `workplaceBuildDelay` | 8 | Ticks per block |

Chests within `supplyRadius` blocks.

## Saved data

On the hall: `guilds` and `lastTaxDay` (a saved field, not a config key).

## Items, blocks, jobs, commands

Guild Charter (`aliveworkplace:guild_charter`, `item.aliveworkplace.guild_charter`).

## Decisions

- 30.17: one guild per trade.

## Known limits

None.

## Proof

GameTests: `GuildGameTests`, `GuildGameTests.guildsLoadFromData`, `perksWaitForTheGuildhall`. Showcase scenes:
`guilds` (the guilds at work).
"""

FILES = {
    "ROADMAP.md": """# Roadmap

## Bugs

- [x] **B1** (approved auto 2026-10-01) (verified 2026-10-02: a (nested) note) A flake nobody saw.
- [x] **B2** (approved 2026-10-02) A bug players saw, fixed before the wiki.

## Milestone 30: Edicts (1.4)

- [x] **30.2** (approved auto 2026-10-05) One pace, one cap.
- [x] **30.17** (review: pending 2026-10-06) **Guild Charters.**
  More text about it.
  - [x] **30.17a** (approved 2026-10-06) Change from the owner: a harder recipe.
- [ ] **30.18** **More guilds.**
- [ ] **30.19** (claimed: lane-a, 2026-10-06 10:00Z) **Even more guilds.**
""",
    f"{JAVA}/WorkplaceConfig.java": """package mod;
public final class WorkplaceConfig {
	/** Chests near a workstation. */
	public int supplyRadius = 8;
	public boolean guilds = Expansions.on(Expansions.M30);
	public List<Integer> mythicLegendCap = new ArrayList<>(DEFAULT);
	private int configVersion = VERSION;
	static final int VERSION = 2;
	public static final String FILE = "mod.json";
}
""",
    f"{JAVA}/registry/ModGameRules.java": """package mod.registry;
public final class ModGameRules {
	public static final GameRules.Key<GameRules.IntegerValue> BUILD_DELAY =
		Platform.get().intRule("workplaceBuildDelay", GameRules.Category.MOBS, 8, 1, Integer.MAX_VALUE);
}
""",
    f"{JAVA}/registry/ModItems.java": """package mod.registry;
public final class ModItems {
	public static final Item GUILD_CHARTER = Reg.item("guild_charter", Item::new, new Item.Properties());
}
""",
    "src/main/resources/assets/aliveworkplace/lang/en_us.json":
        '{"item.aliveworkplace.guild_charter": "Guild Charter", "guild.aliveworkplace.builders": "Builders\' Guild"}\n',
    "src/main/resources/data/aliveworkplace/guilds/builders.json": "{}\n",
    f"{TESTS}/GuildGameTests.java": """package mod.test;
public class GuildGameTests {
	//$ gametest_ticks AREA '200'
	@GameTest(template = AREA, timeoutTicks = 200)
	public void guildsLoadFromData(GameTestHelper helper) {
	}

	//$ gametest AREA
	@GameTest(template = AREA)
	public void perksWaitForTheGuildhall(GameTestHelper helper) {
	}
}
""",
    "tools/showcase/scenes.py": 'SCENES = [{"name": "guilds"}, {"name": "hall"}]\n',
    "tools/modtest/wiki_baseline.txt": "# ticked before the wiki existed\nB2  # a bug\n30.17a\n",
    "docs/wiki/README.md": "# Wiki\n\n- [Guilds](guilds.md): the guilds.\n",
    "docs/wiki/guilds.md": GUILDS_PAGE,
}

failures = []


def check(name, cond, detail=""):
    print(("ok   " if cond else "FAIL ") + name)
    if not cond:
        failures.append(name)
        if detail:
            print("     " + detail.strip().replace("\n", "\n     "))


def main():
    tmp = tempfile.mkdtemp(prefix="wikicheck-test-")
    try:
        run(tmp)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    print(f"\n{len(failures)} failed" if failures else "\nall passed")
    sys.exit(1 if failures else 0)


def run(tmp):
    count = [0]

    def repo(changes=None, remove=()):
        """A fresh copy of the made-up mod with some files replaced ({path: text, or a function of the old text})."""
        count[0] += 1
        root = os.path.join(tmp, f"repo{count[0]}")
        files = dict(FILES)
        for path, change in (changes or {}).items():
            files[path] = change(files.get(path, "")) if callable(change) else change
        for path in remove:
            del files[path]
        for path, text in files.items():
            full = os.path.join(root, path)
            os.makedirs(os.path.dirname(full), exist_ok=True)
            with open(full, "w", encoding="utf-8") as f:
                f.write(text)
        return root

    def wikicheck(root, *args):
        r = subprocess.run([sys.executable, os.path.join(HERE, "wikicheck.py"), root, *args], text=True,
                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        return r.returncode, r.stdout

    def fails(name, needle, changes=None, remove=()):
        code, out = wikicheck(repo(changes, remove))
        check(name, code == 1 and needle in out, out)

    def passes(name, changes=None, remove=()):
        code, out = wikicheck(repo(changes, remove))
        check(name, code == 0 and "wikicheck: OK" in out, out)
        return out

    def page(old, new):
        return {"docs/wiki/guilds.md": lambda s: s.replace(old, new)}

    out = passes("a true page passes")
    check("the summary counts pages, names and items",
          "1 pages, 10 names checked, 1 of 3 player-visible ticked items have a page, 2 wait in the baseline" in out, out)

    # Names that no longer exist.
    fails("a config key that is gone fails, with the page and line",
          "docs/wiki/guilds.md:23: `supplyRadius` isn't a config key, a game rule or a GameTest any more",
          {f"{JAVA}/WorkplaceConfig.java": lambda s: s.replace("supplyRadius", "chestReach")})
    fails("a config key gone from a Switches table fails", "`guilds` isn't a config key (WorkplaceConfig) or a game rule",
          {f"{JAVA}/WorkplaceConfig.java": lambda s: s.replace("boolean guilds", "boolean guildsOn")})
    fails("a game rule that is gone fails", "`workplaceBuildDelay` isn't a config key (WorkplaceConfig) or a game rule",
          {f"{JAVA}/registry/ModGameRules.java": lambda s: s.replace("workplaceBuildDelay", "workplaceDelay")})
    fails("a private or static field is no config key", "`configVersion` isn't a config key",
          page("`supplyRadius` blocks", "`configVersion` blocks"))
    fails("an item id that is gone fails", "`aliveworkplace:guild_charter` isn't a registered id or a data file",
          {f"{JAVA}/registry/ModItems.java": lambda s: s.replace("guild_charter", "charter")})
    fails("a data file that is gone fails", "`aliveworkplace:builders` isn't a registered id or a data file",
          remove=["src/main/resources/data/aliveworkplace/guilds/builders.json"])
    fails("a lang key that is gone fails", "`item.aliveworkplace.guild_charter` isn't a key in lang/en_us.json",
          {"src/main/resources/assets/aliveworkplace/lang/en_us.json": '{"guild.aliveworkplace.builders": "x"}\n'})
    fails("a test class that is gone fails", "`GuildGameTests` isn't a test class any more",
          {f"{TESTS}/GuildsGameTests.java": FILES[f"{TESTS}/GuildGameTests.java"]}, remove=[f"{TESTS}/GuildGameTests.java"])
    fails("a test that is gone from its class fails", "GuildGameTests has no test guildsLoadFromData any more",
          {f"{TESTS}/GuildGameTests.java": lambda s: s.replace("guildsLoadFromData", "guildsLoad")})
    fails("a test named alone that is gone fails", "`perksWaitForTheGuildhall` isn't a config key, a game rule or a GameTest",
          {f"{TESTS}/GuildGameTests.java": lambda s: s.replace("perksWaitForTheGuildhall", "perksWait")})
    fails("a scene that is gone fails", "`guilds` isn't a scene in tools/showcase/scenes.py",
          {"tools/showcase/scenes.py": 'SCENES = [{"name": "hall"}]\n'})
    passes("a lowercase word is a scene only after \"Showcase scenes\" in Proof",
           page("GameTests: `GuildGameTests`,", "GameTests (`nosuchscene`): `GuildGameTests`,"))
    passes("names inside a code block aren't checked",
           page("## Decisions\n", "## Decisions\n\n```\n`noSuchKey` `aliveworkplace:no_such_id`\n```\n"))

    # The page format.
    fails("a page without the Roadmap items line fails", "no \"Roadmap items:\" line", page("Roadmap items: 30.17,\n30.18\n", ""))
    fails("a page missing a section fails", "no \"## Known limits\" section", page("## Known limits\n\nNone.\n", ""))
    fails("an id on the line that isn't in the roadmap fails", "roadmap item 30.99 isn't in ROADMAP.md",
          page("30.18\n", "30.18, 30.99\n"))
    fails("a word on the line that is no id fails", "\"soon\" on the Roadmap items line isn't a roadmap id",
          page("30.18\n", "30.18, soon\n"))
    passes("\"Roadmap items: none\" is a line", {
        "docs/wiki/guilds.md": lambda s: s.replace("Roadmap items: 30.17,\n30.18\n", "Roadmap items: none\n"),
        "tools/modtest/wiki_baseline.txt": "B2 30.17 30.17a\n"})
    fails("a page the index doesn't link fails", "no link to guilds.md", {"docs/wiki/README.md": "# Wiki\n"})
    fails("an index link to a page that isn't there fails", "links elders.md, which doesn't exist",
          {"docs/wiki/README.md": "# Wiki\n\n- [Guilds](guilds.md)\n- [Elders](elders.md#proof)\n"})
    code, out = wikicheck(repo(remove=["docs/wiki/README.md"]))
    check("no index at all fails", code == 1 and "docs/wiki/README.md is missing" in out, out)

    # Every ticked player-visible item has a page.
    fails("a newly ticked review item with no page fails, by its id", "on no wiki page: 30.18.",
          {"ROADMAP.md": lambda s: s.replace("- [ ] **30.18** ", "- [x] **30.18** (review: pending 2026-10-09) "),
           "docs/wiki/guilds.md": lambda s: s.replace("Roadmap items: 30.17,\n30.18\n", "Roadmap items: 30.17\n")})
    fails("an item the owner approved counts as player-visible", "on no wiki page: 30.17a.",
          {"tools/modtest/wiki_baseline.txt": "B2\n",
           "docs/wiki/guilds.md": lambda s: s.replace("- 30.17: one", "- 30.17, 30.17a: one")})
    fails("an id only mentioned in the text doesn't cover the item", "on no wiki page: 30.17.",
          page("Roadmap items: 30.17,\n30.18\n", "Roadmap items: 30.18\n"))
    passes("work with nothing to see (approved auto) and unticked items need no page",
           {"ROADMAP.md": lambda s: s.replace("- [ ] **30.18** ", "- [x] **30.18** (approved auto 2026-10-09) ")})
    passes("a baseline id passes without a page, whatever the file's layout",
           {"tools/modtest/wiki_baseline.txt": "30.17a B2\n"})
    fails("with no baseline file the old items fail too", "on no wiki page: B2, 30.17a.",
          remove=["tools/modtest/wiki_baseline.txt"])

    # The baseline only shrinks.
    root = repo({"tools/modtest/wiki_baseline.txt": "# why\nB2 30.17  # covered now\n30.17a\n9.9\n"})
    code, out = wikicheck(root)
    check("covered or vanished baseline ids are a note, not a failure",
          code == 0 and "2 baseline ids have a page now or left the roadmap (30.17, 9.9)" in out, out)
    code, out = wikicheck(root, "--prune")
    left = open(os.path.join(root, "tools/modtest/wiki_baseline.txt")).read()
    check("--prune drops them and keeps the rest and the comments", code == 0 and left == "# why\nB2\n30.17a\n", left)
    root = repo(remove=["tools/modtest/wiki_baseline.txt"])
    wikicheck(root, "--prune")
    check("--prune never adds to (or creates) the baseline",
          not os.path.exists(os.path.join(root, "tools/modtest/wiki_baseline.txt")))

    # The config page lists every switch.
    switches = FILES["docs/wiki/guilds.md"].replace("# Guilds", "# Config switches").replace(
        "Roadmap items: 30.17,\n30.18\n", "Roadmap items: none\n")
    both = {"docs/wiki/config-switches.md": switches,
            "docs/wiki/README.md": "# Wiki\n\n- [Guilds](guilds.md)\n- [Config](config-switches.md)\n"}
    fails("config-switches.md must list every config key and game rule",
          "config-switches.md: `supplyRadius` has no row in the Switches tables", both)
    passes("config-switches.md with every key in its tables passes", {**both, "docs/wiki/config-switches.md":
           switches.replace("| `guilds` | on | Guilds |", "| `guilds` | on | Guilds |\n| `supplyRadius` | 8 | Chests |"
                            "\n| `mythicLegendCap` | 0, 0, 1, 2 | Mythic Legends by rank |")})

    # The real wiki.
    code, out = wikicheck(REPO)
    check("this repo's own wiki passes", code == 0 and "wikicheck: OK" in out, out)


if __name__ == "__main__":
    main()
