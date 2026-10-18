#!/usr/bin/env python3
"""Keeps the player guide (docs/guide/) a guide for players: no backend talk, and every picture and link real.

  guidecheck.py [repo]     check; exit 1 with one line per problem

The guide is what the owner sends friends (docs/guide/README.md says who keeps it true). docs/wiki/ is the builders'
reference; the guide must never read like it. A page is every docs/guide/*.md except README.md. What is checked:

  - No roadmap id (a bare number such as 22.10 or 28.3a, or a bug id such as B76), no "ROADMAP", no "GameTest" (any
    case) and no path under src/. A version with three parts (1.21.1, 1.7.3) is fine: write other decimals in words.
  - Every page starts with a "# " title and is linked from index.md; every link to a .md page names one that exists.
  - Every picture from the showcase site (SITE/<scene>/still-N.jpg or anim.gif) names a scene in
    tools/showcase/scenes.py, and still-N is within that scene's stills, so a renamed or trimmed scene fails here
    instead of leaving a broken picture.
  - The wiki layout's blocks (docs/guide/README.md): every page but index.md has an ```infobox block with
    "categories:", each "related:" page exists, and each portal in index.md's ```portals block names a category some
    page is in.
  - "expansion:" (a page written early for an expansion that is still switched off; site.py leaves it out until the
    switch flips) names a gated expansion: M27 to M35, M37 or M38 (Expansions.MILESTONES). Gated pages are checked like every other page, and links to them are
    fine: the published site shows those as plain text.
"""
import importlib.util
import re
import sys
from pathlib import Path

GUIDE = "docs/guide"
SITE = "https://jcondedata.github.io/minecraft-alive-workplace/"

FORBIDDEN = [
    (re.compile(r"(?<![\w.])\d{1,2}\.\d{1,2}[a-z]?(?!\w|\.\d)"), "a roadmap id (write numbers like this in words)"),
    (re.compile(r"\bB\d{1,3}\b"), "a bug id"),
    (re.compile(r"roadmap", re.I), '"ROADMAP"'),
    (re.compile(r"gametest", re.I), '"GameTest"'),
    (re.compile(r"(?<![\w-])src/"), "a path under src/"),
]
EXPANSIONS = {f"M{n}" for n in range(27, 36)} | {"M37", "M38"}
LINK = re.compile(r"\]\(([^)\s]+)\)")
PICTURE = re.compile(re.escape(SITE) + r"([^/)\s]+)/(still-(\d+)\.jpg|anim\.gif)")


def scene_stills(root):
    """Each showcase scene's name and how many numbered stills it has (None: it spreads a pattern, so only still-1 is sure)."""
    f = Path(root, "tools/showcase/scenes.py")
    spec = importlib.util.spec_from_file_location("guidecheck_scenes", f)
    module = importlib.util.module_from_spec(spec)
    sys.path.insert(0, str(f.parent))
    spec.loader.exec_module(module)
    out = {}
    for s in module.SCENES:
        stills = s.get("stills", [])
        spread = any("@spread" in stem for stem, _ in stills)
        out[s["name"]] = None if spread else len(stills)
    return out


def check(root):
    root = Path(root)
    guide = root / GUIDE
    problems = []
    if not guide.is_dir():
        return [f"{GUIDE}: missing"]
    pages = sorted(p for p in guide.glob("*.md") if p.name != "README.md")
    if not (guide / "index.md").exists():
        problems.append(f"{GUIDE}/index.md: missing (the guide's front page)")
    stills = scene_stills(root)
    linked_from_index = set()
    for page in pages:
        text = page.read_text(encoding="utf-8")
        name = f"{GUIDE}/{page.name}"
        if not text.startswith("# "):
            problems.append(f"{name}: must start with a '# ' title")
        for n, line in enumerate(text.splitlines(), 1):
            plain = PICTURE.sub("", line)
            plain = re.sub(r"https?://\S+", "", plain)
            for pattern, what in FORBIDDEN:
                m = pattern.search(plain if what != "a path under src/" else line)
                if m:
                    problems.append(f"{name}:{n}: {what}: '{m.group(0)}'")
            for m in PICTURE.finditer(line):
                scene, _, num = m.group(1), m.group(2), m.group(3)
                if scene not in stills:
                    problems.append(f"{name}:{n}: picture of '{scene}', which is no showcase scene")
                elif num is not None:
                    count = stills[scene]
                    if (count is None and int(num) != 1) or (count is not None and not 1 <= int(num) <= count):
                        problems.append(f"{name}:{n}: '{scene}' has no still-{num}.jpg")
            for m in LINK.finditer(line):
                target = m.group(1).split("#")[0]
                if target.endswith(".md") and "://" not in target:
                    if not (guide / target).exists():
                        problems.append(f"{name}:{n}: links {target}, which doesn't exist")
                    if page.name == "index.md":
                        linked_from_index.add(target)
    used = set()
    portals = []
    for page in pages:
        name = f"{GUIDE}/{page.name}"
        info, kind = {}, None
        for line in page.read_text(encoding="utf-8").splitlines():
            m = re.match(r"^```(\w*)\s*$", line)
            if m:
                kind = m.group(1) if kind is None else None
                continue
            if kind == "infobox":
                key, _, value = (p.strip() for p in line.partition(":"))
                info[key.lower()] = value
            elif kind == "portals" and line.strip():
                portals.append(line.lstrip("- ").partition("|")[0].strip())
        cats = [c.strip() for c in info.get("categories", "").split(",") if c.strip()]
        used.update(cats)
        if page.name != "index.md" and not cats:
            problems.append(f"{name}: needs an ```infobox block with 'categories:' (docs/guide/README.md)")
        for e in (x.strip() for x in info.get("expansion", "").split(",")):
            if e and e.upper() not in EXPANSIONS:
                problems.append(f"{name}: expansion '{e}' is none of M27 to M35, M37 or M38")
        for r in (x.strip() for x in info.get("related", "").split(",")):
            if r and not (guide / r).exists():
                problems.append(f"{name}: related page {r} doesn't exist")
    for p in portals:
        if p not in used:
            problems.append(f"{GUIDE}/index.md: portal '{p}' is no page's category")
    for page in pages:
        if page.name != "index.md" and page.name not in linked_from_index:
            problems.append(f"{GUIDE}/index.md: doesn't link {page.name}")
    return problems


def main(argv):
    root = argv[1] if len(argv) > 1 else str(Path(__file__).resolve().parents[2])
    problems = check(root)
    pages = len([p for p in Path(root, GUIDE).glob("*.md") if p.name != "README.md"])
    if problems:
        print(f"guidecheck: {len(problems)} problem(s) (the rules: {GUIDE}/README.md):", file=sys.stderr)
        for p in problems:
            print("  " + p, file=sys.stderr)
        return 1
    print(f"guidecheck: OK ({pages} pages)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
