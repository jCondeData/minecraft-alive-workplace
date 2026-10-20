#!/usr/bin/env python3
"""guidecheck.py's own tests: each kind of slip in a guide page fails, and a clean guide passes.

    python3 tools/modtest/test_guidecheck.py
"""
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import guidecheck  # noqa: E402

SCENES = '''SCENES = [
    {"name": "builders", "stills": [("02_a", "A"), ("03_b", "B")]},
    {"name": "decor", "stills": [("30_*@spread", "")]},
]
'''
SITE = guidecheck.SITE
INDEX = "# Guide\n\n```portals\nBuilder | x\n```\n\n- [Builder](builder.md)\n"
BOX = "```infobox\ntype: Job\nWorkstation: Blueprint Table\ncategories: Builder, Jobs\nrelated: index.md\n```\n\n"
GOOD = (f"# Builder\n\n{BOX}Hand them a blueprint. Minecraft 1.21.1 and Cobblemon 1.7.3 are fine, and so is 28 blocks.\n\n"
        f"![A builder]({SITE}builders/still-2.jpg)\n\n![Decor]({SITE}decor/still-1.jpg)\n\n![GIF]({SITE}builders/anim.gif)\n")


GATED = "# Drought\n\n```infobox\ntype: Disaster\nexpansion: M32\ncategories: Disasters\n```\n\nNo rain.\n"


def site_gating():
    """site.py leaves a gated page out (no page, not in the index, the lists or All pages) and links to it go plain."""
    import importlib.util
    spec = importlib.util.spec_from_file_location("guide_site", Path(__file__).resolve().parents[1] / "playerguide/site.py")
    site = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(site)
    problems = []
    with tempfile.TemporaryDirectory() as d:
        guide = Path(d, "guide")
        guide.mkdir()
        (guide / "index.md").write_text("# Guide\n\nHello.\n\n## Village\n\n- [Builder](builder.md): builds\n\n"
                                        "## Threats\n\n### Weather\n\n- [Drought](drought.md): no rain\n")
        (guide / "builder.md").write_text(GOOD + "\nIn a [drought](drought.md) too.\n")
        (guide / "drought.md").write_text(GATED + "\nSee the [builder](builder.md).\n")
        site.GUIDE, site.CHANGELOG = str(guide), str(Path(d, "none.md"))
        for off, gated in (({"32"}, True), (set(), False)):
            out = Path(d, "site" + str(gated))
            s = site.Site(off=off)
            s.write(str(out))
            folder = out / site.FOLDER
            every = " ".join(f.read_text() for f in folder.glob("*.html"))
            index = (folder / "index.html").read_text()
            builder = (folder / "builder.html").read_text()
            if (folder / "drought.html").exists() == gated:
                problems.append(f"drought.html published: {not gated}, with its switch {'off' if gated else 'on'}")
            if gated and ("drought.html" in every or "Threats" in index or "Weather" in index):
                problems.append("a link to the gated page, or its empty index section or subsection, is still published")
            if not gated and "Weather" not in index:
                problems.append("the index's subsection is missing once the switch is on")
            if gated and "In a drought too." not in builder:
                problems.append("the link's words are gone from the published page")
            if gated and "<b>1</b> articles" not in index:
                problems.append("the gated page is in the article counter")
            if not gated and 'href="drought.html"' not in builder:
                problems.append("the link is missing once the switch is on")
    return problems


def run(builder, index=INDEX, extra=None):
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        (root / "tools/showcase").mkdir(parents=True)
        (root / "tools/showcase/scenes.py").write_text(SCENES)
        guide = root / "docs/guide"
        guide.mkdir(parents=True)
        (guide / "index.md").write_text(index)
        (guide / "builder.md").write_text(builder)
        (guide / "README.md").write_text("Rules: ROADMAP 22.10, GameTest, src/ (the README isn't a page)\n")
        for name, text in (extra or {}).items():
            (guide / name).write_text(text)
        return guidecheck.check(root)


def expect(name, problems, fragment):
    ok = any(fragment in p for p in problems) if fragment else not problems
    print(("ok   " if ok else "FAIL ") + name + ("" if ok else f": {problems}"))
    return ok


def site_odd_lines():
    """site.py's renderer finishes on lines no block rule takes: a picture with words after it hung it forever (B256)."""
    import importlib.util
    import threading
    spec = importlib.util.spec_from_file_location("guide_site", Path(__file__).resolve().parents[1] / "playerguide/site.py")
    site = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(site)
    md = f"# Page\n\n![A builder]({SITE}builders/still-2.jpg) Words after it.\nMore words.\n\n#### Deep heading\n\n![broken\n"
    out = {}
    worker = threading.Thread(target=lambda: out.update(body=site.render(md)[1]), daemon=True)
    worker.start()
    worker.join(5)
    if worker.is_alive():
        return ["render() never finished on a picture with words after it"]
    return [] if "Words after it." in out["body"] and "More words." in out["body"] else [f"words lost: {out['body']}"]


def main():
    results = [
        expect("a clean guide passes", run(GOOD), None),
        expect("a roadmap id fails", run(GOOD + "\nSee 22.10 for more.\n"), "roadmap id"),
        expect("a roadmap id with a letter fails", run(GOOD + "\nAs 21.1a said.\n"), "roadmap id"),
        expect("a roadmap id at a sentence's end fails", run(GOOD + "\nItem 28.3.\n"), "roadmap id"),
        expect("a bug id fails", run(GOOD + "\nFixed in B76.\n"), "bug id"),
        expect("ROADMAP fails", run(GOOD + "\nOn the roadmap.\n"), "ROADMAP"),
        expect("GameTest fails", run(GOOD + "\nA GameTest proves it.\n"), "GameTest"),
        expect("a path under src/ fails", run(GOOD + "\nIn src/main/resources/x.json.\n"), "src/"),
        expect("a picture of no scene fails", run(GOOD + f"\n![x]({SITE}nothing/still-1.jpg)\n"), "no showcase scene"),
        expect("a still past the scene's stills fails", run(GOOD + f"\n![x]({SITE}builders/still-3.jpg)\n"), "no still-3"),
        expect("a spread scene's still-2 fails", run(GOOD + f"\n![x]({SITE}decor/still-2.jpg)\n"), "no still-2"),
        expect("a link to a missing page fails", run(GOOD + "\nSee [x](missing.md).\n"), "doesn't exist"),
        expect("a page the index doesn't link fails", run(GOOD, extra={"orphan.md": "# Orphan\n"}), "doesn't link orphan.md"),
        expect("a page without a title fails", run("Builder\n"), "title"),
        expect("a page without categories fails", run(GOOD.replace("categories: Builder, Jobs\n", "")), "categories"),
        expect("a page without an infobox fails", run(GOOD.replace(BOX, "")), "categories"),
        expect("a related page that doesn't exist fails", run(GOOD.replace("index.md\n```", "gone.md\n```")),
               "related page gone.md"),
        expect("a gated page and links to it pass",
               run(GOOD + "\nSee [the drought](drought.md).\n", index=INDEX + "- [Drought](drought.md)\n",
                   extra={"drought.md": GATED}), None),
        expect("a gated page is still checked", run(GOOD, index=INDEX + "- [Drought](drought.md)\n",
                                                    extra={"drought.md": GATED + "\nSee 32.4.\n"}), "roadmap id"),
        expect("an unknown expansion fails", run(GOOD, index=INDEX + "- [Drought](drought.md)\n",
                                                 extra={"drought.md": GATED.replace("M32", "M99")}), "expansion 'M99'"),
        expect("a portal no page is in fails", run(GOOD, index=INDEX.replace("Builder |", "Nowhere |")),
               "portal 'Nowhere'"),
        expect("site.py leaves a gated page out until its switch flips", site_gating(), None),
        expect("site.py finishes on a picture with words after it", site_odd_lines(), None),
    ]
    failed = results.count(False)
    print(f"{len(results) - failed} of {len(results)} passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
