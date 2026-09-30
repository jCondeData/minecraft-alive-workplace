#!/usr/bin/env python3
"""Read every Minecraft log a test run produced and list what went wrong, grouped and counted.

  logaudit.py <log or dir> [...] [--mod aliveworkplace] [--allow allow.txt] [--strict] [--baseline old.log]

Directories are searched for latest.log, server.log and crash reports: the LAST run of each
server. Audit after the final clean run (mutation runs overwrite latest.log with deliberately
broken code). --rotated adds the older rotated logs (*.log.gz, debug.log). Each problem is sorted into a category, most serious first:
  crash     crash reports, exceptions in the tick loop, OutOfMemoryError, watchdog kills
  test      GameTest failures and timeouts
  mixin     mixins that failed to apply or found no target
  error     ERROR lines and uncaught exceptions (with the first lines of the stack)
  data      recipes, tags, loot tables, models, textures or lang that failed to load
  perf      "Can't keep up", ticks that took seconds
  warn      WARN lines
Lines that mention --mod (the mod id or its package) are marked [ours]. --allow takes a file of
regexes (one per line) for known, harmless messages; a few are built in (Fabric dev warnings).
--baseline shows only problems that are new compared with an older log (a previous release).
Exit code 1 if there is any crash, test failure or mixin problem, or any error/data problem
that is ours; with --strict, any problem at all that isn't allowed.
"""
import argparse
import gzip
import re
import sys
from collections import OrderedDict
from pathlib import Path

LINE_RE = re.compile(r"^\[?[\d:.\- T]+\]?\s*\[([^\]/]+)/(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\]")
CATS = [
    ("crash", re.compile(r"Crash Report|Exception in server tick loop|OutOfMemoryError|Watchdog|"
                         r"server watchdog|Considering it to be crashed|This crash report has been saved|"
                         r"Encountered an unexpected exception|Preparing crash report", re.I)),
    ("test", re.compile(r"required tests? failed|failed at -?\d+|GameTestTimeoutException|Test timed out|"
                        r"GameTestAssertException|test .* failed", re.I)),
    ("mixin", re.compile(r"Mixin apply (for mod \S+ )?failed|Mixin transformation of|InvalidMixinException|"
                         r"InjectionError|Critical injection failure|could not find any targets matching|"
                         r"MixinApplyError|@Shadow field .* was not located|Unable to locate obfuscation mapping", re.I)),
    ("data", re.compile(r"Couldn't parse|Failed to load|Parsing error loading|Couldn't load tag|Unknown registry key|"
                        r"Missing model|Unable to load model|Missing textures? in model|Unable to resolve texture|"
                        r"Couldn't load (?:loot table|recipe|advancement)|Unknown (?:recipe|loot table)|"
                        r"Failed to (?:decode|parse|read)|Invalid (?:recipe|loot|tag)|Couldn't find template|"
                        r"Unable to find translation|No key \w+ in MapLike", re.I)),
    ("perf", re.compile(r"Can't keep up!|took \d{4,} ?ms|Tick took", re.I)),
]
BUILTIN_ALLOW = [
    r"Dev warning - Untranslated Item Tags detected",
    r"TranslationConventionLogWarnings\)\s*$",
    r"Class path entries reference missing files",
    r"Found existing config file",
    r"Environment: Environment\[",
    r"Failed to verify authentication|Failed to fetch user properties",  # offline dev servers
    r"Ambiguity between arguments",                                     # brigadier noise
    r"Failed to load properties from file: server\.properties",         # first boot of a test server
    r"Failed to load eula\.txt",
]
NORM = [(re.compile(r"^\[?[\d:.\- T]+\]?\s*"), ""), (re.compile(r"-?\d+(\.\d+)?"), "#"),
        (re.compile(r"@[0-9a-f]{5,}"), "@…"), (re.compile(r"[0-9a-f]{8}-[0-9a-f-]{27}"), "<uuid>")]


def norm(s):
    for r, rep in NORM:
        s = r.sub(rep, s)
    return s.strip()[:220]


def open_log(p):
    if str(p).endswith(".gz"):
        return gzip.open(p, "rt", encoding="utf-8", errors="replace")
    return open(p, encoding="utf-8", errors="replace")


def entries(path):
    """Yield (level, first line, continuation lines) per log entry."""
    cur = None
    with open_log(path) as fh:
        for raw in fh:
            line = raw.rstrip("\n")
            m = LINE_RE.match(line)
            if m or cur is None:
                if cur:
                    yield cur
                cur = [m.group(2) if m else "", line, []]
            else:
                cur[2].append(line)
    if cur:
        yield cur


def classify(level, text):
    for cat, rx in CATS:
        if rx.search(text):
            return cat
    if level in ("ERROR", "FATAL"):
        return "error"
    if re.search(r"(?:^|\s)(?:[\w.$]+(?:Exception|Error))(?::|\s*$)", text) and re.search(r"\n\s+at ", text):
        return "error"
    if level in ("WARN", "WARNING"):
        return "warn"
    return None


def audit(paths, mod, allow):
    found = OrderedDict()
    for p in paths:
        for level, first, cont in entries(p):
            text = first + ("\n" + "\n".join(cont[:12]) if cont else "")
            cat = classify(level, text)
            if not cat or any(a.search(text) for a in allow):
                continue
            key = (cat, norm(first))
            ours = bool(mod and re.search(mod, text, re.I))
            if key not in found:
                found[key] = dict(cat=cat, first=first.strip()[:300], stack=[c for c in cont if c.strip()][:6],
                                  count=0, ours=ours, file=str(p))
            found[key]["count"] += 1
            found[key]["ours"] |= ours
    return found


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("paths", nargs="+")
    ap.add_argument("--mod", help="regex for the mod id / package, e.g. 'aliveworkplace|alive.workplace'")
    ap.add_argument("--allow", help="file of regexes for known harmless messages")
    ap.add_argument("--baseline", nargs="*", help="older logs: report only problems not in them")
    ap.add_argument("--strict", action="store_true")
    ap.add_argument("--rotated", action="store_true",
                    help="in directories, also read rotated logs (*.log.gz, debug.log): older runs, e.g. mutants")
    a = ap.parse_args()

    files = []
    pat = r"\.log(\.gz)?$|crash-.*\.txt$" if a.rotated else r"^(latest|server)\.log$|crash-.*\.txt$"
    for p in map(Path, a.paths):
        if p.is_dir():
            files += sorted(q for q in p.rglob("*") if q.is_file() and re.search(pat, q.name))
        elif p.exists():
            files.append(p)
    if not files:
        sys.exit("no log files found")
    allow = [re.compile(x) for x in BUILTIN_ALLOW]
    if a.allow:
        allow += [re.compile(l.strip()) for l in Path(a.allow).read_text().splitlines() if l.strip() and not l.startswith("#")]
    found = audit(files, a.mod, allow)
    if a.baseline:
        base_files = []
        for p in map(Path, a.baseline):
            base_files += sorted(p.rglob("*.log*")) if p.is_dir() else [p]
        old = audit(base_files, a.mod, allow)
        found = OrderedDict((k, v) for k, v in found.items() if k not in old)

    order = ["crash", "test", "mixin", "error", "data", "perf", "warn"]
    print(f"# Log audit: {len(files)} files" + (" (new since baseline)" if a.baseline else ""))
    for f in files:
        print(f"- {f}")
    total = 0
    for cat in order:
        items = [v for v in found.values() if v["cat"] == cat]
        if not items:
            continue
        items.sort(key=lambda v: (not v["ours"], -v["count"]))
        print(f"\n## {cat} ({sum(v['count'] for v in items)} lines, {len(items)} distinct)\n")
        for v in items[:40]:
            total += 1
            tag = " [ours]" if v["ours"] else ""
            print(f"- {v['count']}x{tag} `{v['first']}`")
            for s in v["stack"][:4]:
                print(f"    {s.strip()[:200]}")
        if len(items) > 40:
            print(f"- ... {len(items) - 40} more")
    if not found:
        print("\nNo problems found.")
    bad = any(v["cat"] in ("crash", "test", "mixin") or (v["ours"] and v["cat"] in ("error", "data")) for v in found.values())
    if a.strict and found:
        bad = True
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    import signal
    if hasattr(signal, "SIGPIPE"):
        signal.signal(signal.SIGPIPE, signal.SIG_DFL)   # quiet when piped into head
    main()
