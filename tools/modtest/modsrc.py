"""Shared helpers: find a Fabric mod's source sets and read Java sources."""
import os
import re
from pathlib import Path

TEST_DIR_RE = re.compile(r"test", re.I)


def repo_root(start="."):
    p = Path(start).resolve()
    for q in [p, *p.parents]:
        if (q / "settings.gradle.kts").exists() or (q / "settings.gradle").exists():
            return q
    return p


def source_sets(root):
    """(main java dirs, test java dirs). Test sets: any src/<name>/java whose name contains 'test'
    (gametest, compattest, test, testmod...)."""
    main, tests = [], []
    src = Path(root) / "src"
    for d in sorted(src.glob("*/java")) if src.exists() else []:
        (tests if TEST_DIR_RE.search(d.parent.name) else main).append(d)
    for sub in sorted(Path(root).glob("*/src/*/java")):          # multi-project layouts (common/, fabric/)
        if "versions" in sub.parts or "build" in sub.parts:
            continue
        (tests if TEST_DIR_RE.search(sub.parent.name) else main).append(sub)
    return main, tests


def java_files(dirs):
    for d in dirs:
        for f in sorted(Path(d).rglob("*.java")):
            yield f


def read(f):
    try:
        return Path(f).read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""


def mod_package(main_dirs):
    """The mod's root package: the deepest package that contains every main class (roughly)."""
    pkgs = []
    for f in java_files(main_dirs):
        m = re.search(r"^\s*package\s+([\w.]+)\s*;", read(f), re.M)
        if m:
            pkgs.append(m.group(1).split("."))
    if not pkgs:
        return ""
    common = pkgs[0]
    for p in pkgs[1:]:
        n = 0
        while n < min(len(common), len(p)) and common[n] == p[n]:
            n += 1
        common = common[:n]
    return ".".join(common)


def strip_comments(src):
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    return re.sub(r"//[^\n]*", "", src)


GAMETEST_RE = re.compile(r"@GameTest\b[^\n]*\n(?:\s*@[^\n]*\n)*\s*public\s+void\s+(\w+)\s*\(", re.M)


def test_methods(test_dirs):
    """{(file, method)} for every @GameTest (and JUnit @Test) method."""
    out = []
    for f in java_files(test_dirs):
        s = read(f)
        for m in GAMETEST_RE.finditer(s):
            out.append((f, m.group(1)))
        for m in re.finditer(r"@Test\b[^\n]*\n(?:\s*@[^\n]*\n)*\s*(?:public\s+)?void\s+(\w+)\s*\(", s):
            out.append((f, m.group(1)))
    return out
