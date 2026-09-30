#!/usr/bin/env python3
"""What changed, and which tests (if any) touch it: the tester's starting point.

  scope.py [repo] [--since REV] [--until REV]

--since defaults to the newest release tag (v*) before HEAD, or HEAD~1 without tags. Uncommitted
and untracked files are always included. Lists, most urgent first:
  - tests that were deleted, or a drop in the number of @GameTest methods (never acceptable
    without a reason in the commit message)
  - changed main classes that no test mentions (write tests for these first)
  - changed mixins (high risk: they change vanilla behaviour for everyone)
  - changed classes and the test methods that mention them
  - changed data and assets (recipes, loot tables, lang, blueprints, textures) with the kind of
    test or check each needs
"""
import argparse
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from modsrc import repo_root, source_sets, java_files, read, strip_comments, GAMETEST_RE  # noqa: E402

DATA_HINTS = [
    (r"/recipes?/", "recipe: a GameTest that crafts it through the real RecipeManager"),
    (r"/loot_tables?/|/loot_table/", "loot table: a GameTest where a survival player breaks the block and gets the drop"),
    (r"/tags/", "tag: a GameTest asserting the tag contains (and doesn't contain) the right entries"),
    (r"/lang/", "lang: run langcheck.py --since; render every new sentence with its real arguments in a test"),
    (r"/models?/|/blockstates?/|/items/", "model/blockstate: client screenshot, and the log audit for missing models"),
    (r"/textures?/", "texture: look at it (screenshot scene or preview), lint with minecraft-pixel-art if available"),
    (r"\.nbt$|/structures?/|blueprint", "structure/blueprint: build it in a GameTest or render it; check it isn't truncated"),
    (r"worldgen|/structure_set|/template_pool|/processor_list", "worldgen: boot a real server (pack test) and locate it"),
    (r"\.mixins\.json$", "mixin config: every mixin still applies (log audit: 'Mixin apply failed')"),
    (r"fabric\.mod\.json$", "mod metadata: dependencies/entrypoints; boot a real server and client"),
    (r"\.accesswidener$|\.classtweaker$", "access widener: build every node; boot a real server"),
    (r"\.gradle(\.kts)?$|\.toml$|gradle\.properties$", "build config: full ./gradlew build on every node, jar contents"),
]


def git(root, *args, check=True):
    r = subprocess.run(["git", "-C", str(root), *args], capture_output=True, text=True)
    if check and r.returncode:
        sys.exit(f"git {' '.join(args)} failed: {r.stderr.strip()}")
    return r.stdout


def default_since(root):
    tags = git(root, "tag", "--list", "v*", "--sort=-creatordate", "--merged", "HEAD", check=False).split()
    head = git(root, "rev-parse", "HEAD").strip()
    for t in tags:
        if git(root, "rev-list", "-n1", t).strip() != head:
            return t
    return "HEAD~1"


def count_gametests(root, rev, test_dirs):
    n = 0
    for d in test_dirs:
        rel = d.relative_to(root)
        files = git(root, "ls-tree", "-r", "--name-only", rev, str(rel), check=False).split()
        for f in files:
            if f.endswith(".java"):
                n += len(GAMETEST_RE.findall(git(root, "show", f"{rev}:{f}", check=False)))
    return n


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("repo", nargs="?", default=".")
    ap.add_argument("--since")
    ap.add_argument("--until", default="HEAD")
    a = ap.parse_args()
    root = repo_root(a.repo)
    since = a.since or default_since(root)
    main_dirs, test_dirs = source_sets(root)

    commits = git(root, "log", "--oneline", f"{since}..{a.until}").strip().splitlines()
    changed = {}
    for line in git(root, "diff", "--name-status", "-M", since, a.until).splitlines():
        parts = line.split("\t")
        changed[parts[-1]] = parts[0][0]
    if a.until == "HEAD":
        for line in git(root, "status", "--porcelain").splitlines():
            st, path = line[:2].strip() or "M", line[3:].split(" -> ")[-1]
            changed[path] = "A" if st == "??" else st[0]
    numstat = {}
    for line in git(root, "diff", "--numstat", since).splitlines():
        add, dele, path = line.split("\t")[:3]
        numstat[path] = (add, dele)

    tests_src = {f: strip_comments(read(f)) for f in java_files(test_dirs)}

    def tests_mentioning(cls):
        hits = []
        for f, s in tests_src.items():
            if re.search(r"(?<![\w])" + re.escape(cls) + r"(?![\w])", s):
                names = [m.group(1) for m in GAMETEST_RE.finditer(read(f))]
                hits.append(f"{f.stem} ({len(names)} tests)")
        return hits

    main_rel = [str(d.relative_to(root)) for d in main_dirs]
    test_rel = [str(d.relative_to(root)) for d in test_dirs]
    untested, tested, mixins, data, tests_changed, deleted_tests = [], [], [], [], [], []
    for path, st in sorted(changed.items()):
        ch = numstat.get(path, ("?", "?"))
        if path.endswith(".java") and any(path.startswith(t) for t in test_rel):
            (deleted_tests if st == "D" else tests_changed).append((path, st, ch))
        elif path.endswith(".java") and any(path.startswith(m) for m in main_rel):
            cls = Path(path).stem
            if "/mixin/" in path or cls.endswith("Mixin") or cls.endswith("Accessor"):
                mixins.append((path, st, ch))
            if st == "D":
                continue
            hits = tests_mentioning(cls)
            (tested if hits else untested).append((path, st, ch, hits))
        elif not path.endswith(".java"):
            hint = next((h for pat, h in DATA_HINTS if re.search(pat, path)), None)
            if hint or "/resources/" in path:
                data.append((path, st, hint or "resource: check it loads (log audit) and is used"))

    before, after = count_gametests(root, since, test_dirs), None
    try:
        after = sum(len(GAMETEST_RE.findall(read(f))) for f in java_files(test_dirs))
    except Exception:
        pass

    print(f"# Scope: {since}..{a.until}{' + working tree' if a.until == 'HEAD' else ''}")
    print(f"{len(commits)} commits; {len(changed)} files changed")
    for c in commits[:15]:
        print(f"- {c}")
    if len(commits) > 15:
        print(f"- ... {len(commits) - 15} more")
    print(f"\n@GameTest methods: {before} at {since} -> {after} now", end="")
    print("  **(DROPPED: find out why before anything else)**" if after is not None and after < before else "")
    if deleted_tests:
        print("\n## Deleted test files (justify each or restore)\n")
        for p, st, ch in deleted_tests:
            print(f"- {p}")
    print("\n## Changed code that no test mentions (write tests for these first)\n")
    for p, st, ch, _ in untested:
        print(f"- `{p}` ({st}, +{ch[0]}/-{ch[1]})")
    if not untested:
        print("- none")
    if mixins:
        print("\n## Changed mixins (high risk: they change vanilla behaviour for everyone)\n")
        for p, st, ch in mixins:
            print(f"- `{p}` ({st}): test the vanilla behaviour it changes AND that vanilla still works where it shouldn't apply")
    print("\n## Changed code and the test classes that mention it\n")
    for p, st, ch, hits in tested:
        print(f"- `{p}` (+{ch[0]}/-{ch[1]}): {', '.join(hits[:5])}{' ...' if len(hits) > 5 else ''}")
    if data:
        print("\n## Changed data and assets\n")
        groups = defaultdict(list)
        for p, st, hint in data:
            groups[hint].append((p, st))
        for hint, files in groups.items():
            if len(files) <= 4:
                for p, st in files:
                    print(f"- `{p}` ({st}): {hint}")
            else:
                shown = ", ".join(f"`{Path(p).name}`" for p, _ in files[:3])
                print(f"- {len(files)} files ({shown}, ...): {hint}")
    if tests_changed:
        print("\n## Changed tests (read them: a weakened assertion is a deleted test)\n")
        for p, st, ch in tests_changed:
            print(f"- `{p}` ({st}, +{ch[0]}/-{ch[1]})")


if __name__ == "__main__":
    import signal
    if hasattr(signal, "SIGPIPE"):
        signal.signal(signal.SIGPIPE, signal.SIG_DFL)   # quiet when piped into head
    main()
