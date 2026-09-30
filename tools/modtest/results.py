#!/usr/bin/env python3
"""Summarise GameTest runs: what passed, what failed and why, and which tests are flaky.

  results.py <run> [<run> ...] [--save out.json] [--baseline old.json]

A <run> is a Gradle console log, a server log (latest.log / .log.gz) or a JUnit XML report
(Fabric writes one with -Dfabric-api.gametest.report-file). Several runs of the SAME code are
compared: a test that fails in some runs and passes in others is flaky (a real bug in the test
or the mod, usually timing, randomness or neighbouring tests), and a test failing in every run
is broken. --baseline compares with a saved summary and warns when tests disappeared.
Exit code 1 when anything failed or tests disappeared.
"""
import argparse
import gzip
import json
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

SUMMARY_OK = re.compile(r"All (\d+) required tests passed")
SUMMARY_FAIL = re.compile(r"(\d+) required tests? failed")
OPTIONAL_FAIL = re.compile(r"(\d+) optional tests? failed")
FAIL_AT = re.compile(r"(?:^|\s)([\w:./-]+) failed at (-?\d+, ?-?\d+, ?-?\d+)!?\s*(.*)")
PREFIX = re.compile(r"^\[[^\]]*\]\s*\[[^\]]*\]:?\s*(?:\([^)]*\)\s?)?")
FAIL_NEW = re.compile(r"^\s*-\s+([\w:./-]+):\s*(.+?)(?: on tick (\d+))?\s*$")
FAIL_LIST = re.compile(r"^\s*-\s+([\w:./-]+)\s*$")
RUNNING = re.compile(r"(\d+) tests are now running")


def read_text(p):
    if str(p).endswith(".gz"):
        with gzip.open(p, "rt", encoding="utf-8", errors="replace") as fh:
            return fh.read()
    return Path(p).read_text(encoding="utf-8", errors="replace")


def parse_log(p):
    text = read_text(p)
    r = dict(source=str(p), total=None, failed={}, passed_all=False)
    for m in RUNNING.finditer(text):
        r["total"] = int(m.group(1))
    ok = SUMMARY_OK.findall(text)
    if ok:
        r["total"] = int(ok[-1])
        r["passed_all"] = True
    lines = [PREFIX.sub("", l) for l in text.splitlines()]
    for i, line in enumerate(lines):
        m = FAIL_AT.search(line)
        if m:
            r["failed"].setdefault(m.group(1), m.group(3).strip()[:300] or "failed")
            continue
        if SUMMARY_FAIL.search(line):
            for nxt in lines[i + 1:i + 400]:
                m2 = FAIL_NEW.match(nxt)
                if m2 and ":" in nxt:
                    r["failed"].setdefault(m2.group(1), m2.group(2)[:300])
                    continue
                m3 = FAIL_LIST.match(nxt)
                if m3:
                    r["failed"].setdefault(m3.group(1), "failed")
                    continue
                if nxt.strip() and not nxt.strip().startswith("-"):
                    break
    if r["failed"]:
        r["passed_all"] = False
    return r


def parse_junit(p):
    root = ET.parse(p).getroot()
    r = dict(source=str(p), total=0, failed={}, passed_all=True)
    for tc in root.iter("testcase"):
        r["total"] += 1
        f = tc.find("failure") if tc.find("failure") is not None else tc.find("error")
        if f is not None:
            name = tc.get("name") or "?"
            r["failed"][name] = (f.get("message") or (f.text or "")).strip()[:300]
            r["passed_all"] = False
    return r


def parse(p):
    return parse_junit(p) if str(p).endswith(".xml") else parse_log(p)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("runs", nargs="+")
    ap.add_argument("--save")
    ap.add_argument("--baseline")
    a = ap.parse_args()
    runs = [parse(p) for p in a.runs]
    print(f"# Test results: {len(runs)} run(s)\n")
    print("| run | tests | failed |")
    print("|---|---|---|")
    for r in runs:
        print(f"| {Path(r['source']).name} | {r['total'] if r['total'] is not None else '?'} | {len(r['failed'])} |")
    fails = defaultdict(list)
    for i, r in enumerate(runs):
        for name, msg in r["failed"].items():
            fails[name].append((i, msg))
    bad = False
    if fails:
        same_suite = len({r["total"] for r in runs}) <= 1
        broken = {n: v for n, v in fails.items() if len(v) == len(runs)}
        flaky = {n: v for n, v in fails.items() if len(v) < len(runs)} if same_suite else {}
        if not same_suite:
            # different test counts: these are runs of different code, so a test failing in one run
            # may simply not exist in another. Report per run instead of calling anything flaky.
            broken = {n: v for n, v in fails.items()}
            print("\nThe runs have different numbers of tests, so they aren't compared for flakiness "
                  "(run the same code several times for that).")
        if broken:
            bad = True
            print("\n## Failing" + (" in every run" if len(runs) > 1 and same_suite else "") + "\n")
            for n, v in sorted(broken.items()):
                where = "" if len(v) == len(runs) or len(runs) == 1 else f" (run {', '.join(str(i + 1) for i, _ in v)})"
                print(f"- `{n}`{where}: {v[0][1]}")
        if flaky:
            bad = True
            print(f"\n## Flaky (failed in some of {len(runs)} runs)\n")
            for n, v in sorted(flaky.items(), key=lambda kv: -len(kv[1])):
                print(f"- `{n}`: failed {len(v)}/{len(runs)}: {v[0][1]}")
    else:
        print("\nAll tests passed in every run." if all(r["passed_all"] for r in runs) else
              "\nNo failures parsed, but a run did not report 'All N required tests passed': read the log.")
        bad = not all(r["passed_all"] for r in runs)
    totals = [r["total"] for r in runs if r["total"] is not None]
    if a.baseline and Path(a.baseline).exists():
        base = json.loads(Path(a.baseline).read_text())
        if totals and base.get("total") and min(totals) < base["total"]:
            bad = True
            print(f"\n**Tests disappeared:** {base['total']} in the baseline, {min(totals)} now. Find out why.")
    if a.save:
        Path(a.save).write_text(json.dumps(dict(total=max(totals) if totals else None,
                                                failed=sorted(fails), runs=[r["source"] for r in runs]), indent=1))
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    import signal
    if hasattr(signal, "SIGPIPE"):
        signal.signal(signal.SIGPIPE, signal.SIG_DFL)   # quiet when piped into head
    main()
