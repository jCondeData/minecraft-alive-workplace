#!/usr/bin/env python3
"""The GameTests added since a commit, as Class#method (for the nightly repeats, ROADMAP 22.7).

    python3 tools/modtest/newtests.py --since REV            # one Class#method per line
    python3 tools/modtest/newtests.py --hours 24             # since the last commit on HEAD older than 24 hours
    python3 tools/modtest/newtests.py --hours 24 --csv       # comma-separated, for ALIVEWORKPLACE_REPEAT

A test is new when its class didn't have a @GameTest method of that name at REV. Only src/gametest (the plain suite);
compat tests need the pack's mods and run in their own task.
"""
import argparse
import re
import subprocess
import sys

DIR = "src/gametest/java/"
TEST = re.compile(r"@GameTest\b[^\n]*\n(?:\s*(?://|@)[^\n]*\n)*\s*public\s+(?:static\s+)?void\s+(\w+)\s*\(\s*GameTestHelper")


def git(*args, root="."):
    return subprocess.run(["git", *args], cwd=root, capture_output=True, text=True, check=True).stdout


def tests_at(rev, root="."):
    """{Class#method} for every @GameTest in src/gametest at {rev} (None: the working tree)."""
    out = set()
    if rev is None:
        files = git("ls-files", DIR, root=root).split()
    else:
        files = git("ls-tree", "-r", "--name-only", rev, DIR, root=root).split()
    for path in files:
        if not path.endswith(".java"):
            continue
        if rev is None:
            with open(f"{root}/{path}", encoding="utf-8") as f:
                text = f.read()
        else:
            text = git("show", f"{rev}:{path}", root=root)
        name = path.rsplit("/", 1)[-1][:-5]
        out |= {f"{name}#{m}" for m in TEST.findall(text)}
    return out


def since_hours(hours, root="."):
    rev = git("rev-list", "-1", f"--before={hours} hours ago", "HEAD", root=root).strip()
    return rev or None


def new_tests(since, root=".", head="HEAD"):
    return sorted(tests_at(head, root) - tests_at(since, root))


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--since")
    p.add_argument("--hours", type=float)
    p.add_argument("--csv", action="store_true")
    p.add_argument("--root", default=".")
    a = p.parse_args()
    since = a.since or (since_hours(a.hours, a.root) if a.hours else None)
    if not since:
        sys.exit("no commit that old: give --since REV")
    found = new_tests(since, a.root)
    print(",".join(found) if a.csv else "\n".join(found))


if __name__ == "__main__":
    main()
