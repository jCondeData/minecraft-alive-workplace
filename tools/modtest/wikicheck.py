#!/usr/bin/env python3
"""Keeps the wiki (docs/wiki/, ROADMAP 22.9) true: a page may only name things that exist, and every ticked roadmap
item a player can see has a page.

  wikicheck.py [repo]            check; exit 1 with one line per problem
  wikicheck.py [repo] --prune    also drop ids from the baseline that a page covers now (it never adds any)

A page is every docs/wiki/*.md except README.md. What is checked (the README's "Page format" says the same):

  - The eight sections are there (SECTIONS), as "## Name" or "## 1. Name".
  - A "Roadmap items:" line lists the roadmap ids the page covers ("none" for a page with none); each id must be an
    item in ROADMAP.md.
  - Names in `backticks` of these fixed forms must exist:
      `lowerCamelCase`            a config key (WorkplaceConfig), a game rule (ModGameRules) or a GameTest method
                                  (not checked in the Saved data section, where such a name is a saved field)
      first cell of a Switches table row    a config key or a game rule
      `aliveworkplace:some_id`    a registered id (item, block, job...) or a data file data/aliveworkplace/<kind>/<id>.*
      `some.lang.key`             (lowercase, three or more dotted parts) a key in lang/en_us.json
      `SomethingTests`            a test class; `SomethingTests.method`: that method in it
      after the words "Showcase scene(s)" in the Proof section: `name` is a scene in tools/showcase/scenes.py
    Other backticked words (saved fields, class names, commands, file paths) are not checked.
  - config-switches.md lists every config key and game rule in its Switches tables.
  - README.md links every page, and every page it links exists.
  - Every ticked item with a player-visible change (marked "review: ..." or "approved <date>" by sessions.py; "approved
    auto" is work with nothing to see) is on some page's "Roadmap items:" line, or in tools/modtest/wiki_baseline.txt:
    the items ticked before the wiki existed, which the QA lane's backfill shrinks. Never add to the baseline.
"""
import argparse
import importlib.util
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from modsrc import repo_root, source_sets, java_files, read, test_methods  # noqa: E402

WIKI = "docs/wiki"
BASELINE = "tools/modtest/wiki_baseline.txt"
ALL_SWITCHES = "config-switches.md"
SECTIONS = ["What a player sees", "How it works", "Switches", "Saved data", "Items, blocks, jobs, commands",
            "Decisions", "Known limits", "Proof"]

ITEM = re.compile(r"^\s*- \[([ x])\] \*\*(B\d+[a-z]?|\d+\.\d+[a-z]?)\*\*(.*)$")
ITEM_ID = re.compile(r"^(B\d+[a-z]?|\d+\.\d+[a-z]?)$")
HEADING = re.compile(r"^##\s+(?:\d+\.\s*)?(.+?)\s*$")
TOKEN = re.compile(r"`([^`\n]+)`")
CAMEL = re.compile(r"^[a-z][a-z0-9]*(?:[A-Z][a-z0-9]*)+$")
MOD_ID = re.compile(r"^aliveworkplace:([a-z0-9_/.\-]+)$")
LANG_KEY = re.compile(r"^[a-z][a-z0-9_]*(?:\.[A-Za-z0-9_]+){2,}$")
TEST_CLASS = re.compile(r"^([A-Z]\w*Tests?)(?:\.([a-z]\w*))?$")
SCENE = re.compile(r"^[a-z][a-z0-9_]*$")
FILE_ENDINGS = ("md", "json", "py", "java", "png", "nbt", "txt", "toml", "kts", "yml", "sh", "mcmeta", "jar")
LINK = re.compile(r"\]\(([^)#\s]+\.md)(?:#[^)]*)?\)")


def split_marks(rest):
    """' (approved auto 2026-10-01) (verified: x) Title' -> ['approved auto 2026-10-01', 'verified: x'] (as sessions.py)."""
    marks, i = [], 0
    while True:
        j = i
        while j < len(rest) and rest[j] == " ":
            j += 1
        if j >= len(rest) or rest[j] != "(":
            return marks
        depth, k = 0, j
        while k < len(rest):
            depth += {"(": 1, ")": -1}.get(rest[k], 0)
            if depth == 0:
                break
            k += 1
        if depth:
            return marks
        marks.append(rest[j + 1:k])
        i = k + 1


def roadmap_items(text):
    """{id: (ticked, a player sees it)} for every item line of ROADMAP.md."""
    items = {}
    for line in text.splitlines():
        m = ITEM.match(line)
        if not m:
            continue
        marks = split_marks(m.group(3))
        visible = any(x.startswith("review") or (x.startswith("approved") and not x.startswith("approved auto"))
                      for x in marks)
        items[m.group(2)] = (m.group(1) == "x", visible)
    return items


def config_keys(root):
    """The options of config/aliveworkplace.json: WorkplaceConfig's public fields."""
    keys = set()
    for f in Path(root, "src/main/java").rglob("WorkplaceConfig.java"):
        keys |= set(re.findall(r"^\s*public\s+(?!static\b)[\w<>, ]+?\s+(\w+)\s*=", read(f), re.M))
    return keys


def game_rules(root):
    rules = set()
    for f in Path(root, "src/main/java").rglob("ModGameRules.java"):
        rules |= set(re.findall(r"\b(?:boolean|int)Rule\(\s*\"(\w+)\"", read(f)))
    return rules


def mod_ids(root):
    """Ids the code registers (Reg.item("x"...), AliveWorkplace.id("x")) and the mod's data files, by their path
    inside their kind's folder (data/aliveworkplace/edicts/curfew.json -> curfew)."""
    ids = set()
    main, _ = source_sets(root)
    for f in java_files(main):
        ids |= set(re.findall(r"(?:\bReg\.\w+|\bAliveWorkplace\.id|\bid)\(\s*\"([a-z0-9_/.\-]+)\"", read(f)))
    for base in Path(root, "src/main/resources/data").glob("aliveworkplace/*"):
        if base.is_dir():
            for f in base.rglob("*.*"):
                ids.add(f.relative_to(base).with_suffix("").as_posix())
    return ids


def lang_keys(root):
    keys = set()
    for f in Path(root, "src/main/resources/assets").glob("*/lang/en_us.json"):
        keys |= set(json.loads(read(f)))
    return keys


def tests(root):
    """({class: its source}, {every GameTest method name})."""
    _, test_dirs = source_sets(root)
    classes = {f.stem: read(f) for f in java_files(test_dirs)}
    return classes, {name for _, name in test_methods(test_dirs)}


def scenes(root):
    """The showcase scenes' names, from the catalog itself (None when the repo has none)."""
    f = Path(root, "tools/showcase/scenes.py")
    if not f.exists():
        return None
    spec = importlib.util.spec_from_file_location("wikicheck_scenes", f)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return {s["name"] for s in module.SCENES}


def baseline_ids(root):
    f = Path(root, BASELINE)
    if not f.exists():
        return []
    return [w for line in read(f).splitlines() for w in line.split("#")[0].split()]


class Page:
    """One wiki page: its lines with the section each is in, and the ids on its "Roadmap items:" line."""

    def __init__(self, path, text):
        self.path, self.name = path, Path(path).name
        self.sections, self.lines = [], []          # lines: (number, section, text), code blocks left out
        self.items, self.items_line = [], None
        self.switch_rows = set()                    # the first cells of its Switches tables (filled by check_page)
        section, fenced, collecting = None, False, False
        for n, line in enumerate(text.splitlines(), 1):
            if line.strip().startswith("```"):
                fenced = not fenced
                continue
            if fenced:
                continue
            h = HEADING.match(line)
            if h:
                section = h.group(1)
                self.sections.append(section)
            m = re.match(r"^\s*\**Roadmap items:?\**:?\s*(.*)$", line, re.I)
            if m and self.items_line is None:
                self.items_line, collecting, line_items = n, True, m.group(1)
            elif collecting and line.strip() and not h:
                line_items = line
            else:
                collecting, line_items = False, None
            if line_items is not None:
                self.items += [w.strip("*.") for w in re.split(r"[,;\s]+", line_items) if w.strip("*.")]
            self.lines.append((n, section, line))


def check_page(page, facts, problems):
    def bad(n, text):
        problems.append(f"{WIKI}/{page.name}:{n}: {text}")

    have = {s.lower() for s in page.sections}
    for s in SECTIONS:
        if s.lower() not in have:
            bad(1, f"no \"## {s}\" section (the page format, {WIKI}/README.md)")
    if page.items_line is None:
        bad(1, "no \"Roadmap items:\" line (the roadmap ids this page covers, or \"none\")")
    for i in page.items:
        if i.lower() == "none":
            continue
        if not ITEM_ID.match(i):
            bad(page.items_line, f"\"{i}\" on the Roadmap items line isn't a roadmap id (22.9, 28.21a, B12)")
        elif i not in facts["roadmap"]:
            bad(page.items_line, f"roadmap item {i} isn't in ROADMAP.md")

    switches = facts["config"] | facts["rules"]
    scene_part = False                              # inside the Proof section, after "Showcase scene(s)" in a paragraph
    for n, section, line in page.lines:
        if not line.strip() or (section or "").lower() != "proof":
            scene_part = False
        start = 0
        if (section or "").lower() == "proof" and not scene_part:
            m = re.search(r"showcase scenes?", line, re.I)
            if m:
                scene_part, start = True, m.end()
        cell_end = -1                               # a Switches table row: where its first cell ends
        if (section or "").lower() == "switches" and line.lstrip().startswith("|"):
            cell_end = line.find("|", line.index("|") + 1)
        for m in TOKEN.finditer(line):
            t = m.group(1).strip()
            ident = MOD_ID.match(t)
            klass = TEST_CLASS.match(t)
            wrong = None                            # None: a checked name that exists; "": not a checked form
            if m.start() < cell_end:
                page.switch_rows.add(t)
                if t not in switches:
                    wrong = f"`{t}` isn't a config key (WorkplaceConfig) or a game rule (ModGameRules) any more"
            elif CAMEL.match(t):
                if (section or "").lower() == "saved data":
                    wrong = ""
                elif t not in switches and t not in facts["test_methods"]:
                    wrong = f"`{t}` isn't a config key, a game rule or a GameTest any more"
            elif ident:
                if ident.group(1) not in facts["ids"]:
                    wrong = f"`{t}` isn't a registered id or a data file of the mod any more"
            elif klass:
                source = facts["test_classes"].get(klass.group(1))
                if source is None:
                    wrong = f"`{klass.group(1)}` isn't a test class any more"
                elif klass.group(2) and not re.search(r"\bvoid\s+" + klass.group(2) + r"\s*\(", source):
                    wrong = f"`{t}`: {klass.group(1)} has no test {klass.group(2)} any more"
            elif LANG_KEY.match(t) and "/" not in t and t.rsplit(".", 1)[-1] not in FILE_ENDINGS:
                if t not in facts["lang"]:
                    wrong = f"`{t}` isn't a key in lang/en_us.json any more"
            elif scene_part and m.start() >= start and SCENE.match(t) and facts["scenes"] is not None:
                if t not in facts["scenes"]:
                    wrong = f"`{t}` isn't a scene in tools/showcase/scenes.py any more"
            else:
                wrong = ""
            if wrong:
                bad(n, wrong)
            if wrong != "":
                facts["checked"] += 1

def check(root, prune=False):
    """(problems, summary line). Reads everything from the checked-in files under root."""
    root = Path(root)
    problems = []
    wiki = root / WIKI
    index = wiki / "README.md"
    if not index.exists():
        return [f"{WIKI}/README.md is missing (the wiki's index and page format, ROADMAP 22.9)"], ""
    roadmap = roadmap_items(read(root / "ROADMAP.md"))
    classes, methods = tests(root)
    facts = {"roadmap": roadmap, "config": config_keys(root), "rules": game_rules(root), "ids": mod_ids(root),
             "lang": lang_keys(root), "test_classes": classes, "test_methods": methods, "scenes": scenes(root),
             "checked": 0}

    pages = [Page(f, read(f)) for f in sorted(wiki.glob("*.md")) if f.name != "README.md"]
    for page in pages:
        check_page(page, facts, problems)

    for page in pages:
        if page.name == ALL_SWITCHES:
            for key in sorted((facts["config"] | facts["rules"]) - page.switch_rows):
                problems.append(f"{WIKI}/{page.name}: `{key}` has no row in the Switches tables (this page lists every "
                                f"config key and game rule with its default)")

    linked = set(LINK.findall(read(index)))
    for page in pages:
        if page.name not in linked:
            problems.append(f"{WIKI}/README.md: no link to {page.name} (every page is listed in the index)")
    for link in sorted(linked):
        if "/" not in link and not (wiki / link).exists():
            problems.append(f"{WIKI}/README.md: links {link}, which doesn't exist")

    covered = {}
    for page in pages:
        for i in page.items:
            covered.setdefault(i, page.name)
    baseline = baseline_ids(root)
    visible = [i for i, (ticked, seen) in roadmap.items() if ticked and seen]
    uncovered = [i for i in visible if i not in covered and i not in baseline]
    if uncovered:
        problems.append(f"ROADMAP.md: ticked with a player-visible change but on no wiki page: {', '.join(uncovered)}. "
                        f"Write or update the feature's page in {WIKI}/ and add the id to that page's "
                        f"\"Roadmap items:\" line")
    stale = [i for i in baseline if i in covered or i not in roadmap]
    if stale and prune:
        f = root / BASELINE
        keep = []
        for line in read(f).splitlines():
            words = line.split("#")[0].split()
            if not words:
                keep.append(line)
            elif [w for w in words if w not in stale]:
                keep.append(" ".join(w for w in words if w not in stale))
        f.write_text("\n".join(keep) + "\n", encoding="utf-8")
        baseline = [i for i in baseline if i not in stale]
        stale = []
    summary = (f"wikicheck: {len(pages)} pages, {facts['checked']} names checked, "
               f"{sum(1 for i in visible if i in covered)} of {len(visible)} player-visible ticked items have a page, "
               f"{len(baseline)} wait in the baseline")
    if stale:
        summary += (f"\nwikicheck: note: {len(stale)} baseline ids have a page now or left the roadmap "
                    f"({', '.join(stale[:12])}{'...' if len(stale) > 12 else ''}): run wikicheck.py --prune")
    return problems, summary


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("repo", nargs="?", default=None)
    ap.add_argument("--prune", action="store_true", help="drop baseline ids that a page covers now")
    a = ap.parse_args()
    root = repo_root(a.repo or Path(__file__).resolve().parent)
    problems, summary = check(root, a.prune)
    if summary:
        print(summary)
    if problems:
        print(f"wikicheck: {len(problems)} problem(s) (the rules: {WIKI}/README.md, \"Page format\"):", file=sys.stderr)
        for p in problems:
            print("  " + p, file=sys.stderr)
        sys.exit(1)
    print("wikicheck: OK")


if __name__ == "__main__":
    main()
