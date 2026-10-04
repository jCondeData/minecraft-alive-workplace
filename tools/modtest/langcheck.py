#!/usr/bin/env python3
"""Translation checks: text players read is where silent bugs hide.

  langcheck.py [repo] [--since REV]

Reads assets/*/lang/en_us.json and the Java sources and reports:
  - keys the code uses literally that en_us.json doesn't have (players see the raw key)
  - literal translatable("key", args...) calls whose argument count differs from the string's
    placeholders (%s, %d, %1$s): missing text or the wrong value in the wrong place
  - key families (x and x.<suffix>, e.g. a title and its detail line) whose placeholder counts
    differ: if one argument list fills both, one of them shows the wrong values
  - strings that mix %s with positional %1$s (easy to get wrong)
  - with --since: keys added or changed since REV. Render each in a GameTest with its REAL
    arguments (Component.translatable(key, args).getString() works on the server) and compare
    the sentence with the spec. A static check can't see which arguments a dynamic call passes.
"""
import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from modsrc import repo_root, source_sets, java_files, read, strip_comments  # noqa: E402

# Title/detail pairs whose different placeholder counts were checked against the code (ROADMAP 24.5), with why
# they're right. A pair listed here is no longer reported; change the reason if the code that fills them changes.
CHECKED_PAIRS = {
    "item.aliveworkplace.village_map.legend": "VillageMaps fills the legend line with its own three values, the title with the village name",
    "advice.aliveworkplace.homes.how": "VillageAdvice passes one list of four values to both; the title uses the first two",
    "advice.aliveworkplace.rank.how": "VillageAdvice passes one list of four values to both; .how picks %2$s-%4$s by position",
    "screen.aliveworkplace.daycare.levels": "CobblemonDaycare fills the levels line with its own two values",
}

PH = re.compile(r"%(?:(\d+)\$)?[sd]")
CALL = re.compile(r"translatable(?:WithFallback)?\(\s*\"([a-z0-9_.\-]+)\"\s*(\)|,)")


def placeholders(s):
    """(arguments the string reads, mixes %s with %1$s). %s takes the next argument, %2$s argument 2."""
    marks = PH.findall(s)
    positional = [int(m) for m in marks if m]
    plain = sum(1 for m in marks if not m)
    return max([plain] + positional), bool(positional and plain)


def count_args(src, start):
    """Count top-level arguments after the key in a call starting at the '(' ... ',' position."""
    depth, n, i = 1, 1, start
    while i < len(src) and depth:
        ch = src[i]
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        elif ch == "," and depth == 1:
            n += 1
        elif ch == '"':
            i += 1
            while i < len(src) and src[i] != '"':
                i += 2 if src[i] == "\\" else 1
        i += 1
    return n


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("repo", nargs="?", default=".")
    ap.add_argument("--since")
    a = ap.parse_args()
    root = repo_root(a.repo)
    langs = sorted(root.glob("src/*/resources/assets/*/lang/en_us.json"))
    if not langs:
        sys.exit("no en_us.json found")
    namespaces = {p.parent.parent.name for p in langs}
    lang = {}
    for p in langs:
        lang.update(json.loads(read(p)))
    main_dirs, _ = source_sets(root)
    missing, argmismatch = [], []
    used = set()
    for f in java_files(main_dirs):
        src = strip_comments(read(f))
        for m in CALL.finditer(src):
            key = m.group(1)
            used.add(key)
            if key not in lang:
                ours = any(key.startswith(ns + ".") or ("." + ns + ".") in key for ns in namespaces)
                if ours and not key.endswith("."):
                    missing.append((key, f))       # vanilla / other mods' keys live in their own lang files
                continue
            nargs = 0 if m.group(2) == ")" else count_args(src, m.end())
            want, _ = placeholders(lang[key])
            if nargs != want:
                argmismatch.append((key, nargs, want, f))
    fams, mixed = [], []
    for k, v in lang.items():
        n, mix = placeholders(v)
        if mix:
            mixed.append(k)
        head, _, tail = k.rpartition(".")
        if k in CHECKED_PAIRS:
            continue
        if head in lang and n and placeholders(lang[head])[0] and n != placeholders(lang[head])[0]:
            fams.append((head, placeholders(lang[head])[0], k, n))
    print(f"# Lang check: {len(lang)} keys in {', '.join(str(p.relative_to(root)) for p in langs)}\n")
    print(f"## Keys used in code but missing from en_us.json ({len(missing)})\n")
    for k, f in missing[:60]:
        print(f"- `{k}` ({f.name})")
    print(f"\n## Literal calls whose argument count doesn't match the string ({len(argmismatch)})\n")
    for k, n, w, f in argmismatch[:60]:
        print(f"- `{k}`: {n} argument(s), string has {w} placeholder(s) ({f.name}): `{lang[k][:90]}`")
    print(f"\n## Title/detail pairs with different placeholder counts ({len(fams)})\n")
    print("If one argument list fills both, one of them shows the wrong values. Check the code that builds them.\n")
    for head, hn, k, n in fams[:60]:
        print(f"- `{head}` ({hn}): `{lang[head][:70]}`\n  `{k}` ({n}): `{lang[k][:90]}`")
    if mixed:
        print(f"\n## Strings mixing %s and positional %1$s ({len(mixed)})\n")
        for k in mixed:
            print(f"- `{k}`: `{lang[k][:90]}`")
    if a.since:
        changed = []
        for p in langs:
            rel = p.relative_to(root)
            old = subprocess.run(["git", "-C", str(root), "show", f"{a.since}:{rel}"], capture_output=True, text=True).stdout
            try:
                before = json.loads(old) if old else {}
            except json.JSONDecodeError:
                before = {}
            now = json.loads(read(p))
            changed += [k for k, v in now.items() if before.get(k) != v]
        print(f"\n## Added or changed since {a.since} ({len(changed)}): render each with its real arguments in a test\n")
        for k in changed:
            print(f"- `{k}` = `{lang.get(k, '')[:110]}`")
    sys.exit(1 if missing or argmismatch else 0)


if __name__ == "__main__":
    import signal
    if hasattr(signal, "SIGPIPE"):
        signal.signal(signal.SIGPIPE, signal.SIG_DFL)   # quiet when piped into head
    main()
