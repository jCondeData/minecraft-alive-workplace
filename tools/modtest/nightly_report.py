#!/usr/bin/env python3
"""The nightly mutation and repeat results as one Markdown report (ROADMAP 22.7).

    python3 tools/modtest/nightly_report.py --dir build/nightly --out build/nightly/report.md
    # exit 1 when there is something to fix: a surviving mutant, a flaky new test, or a shard that didn't finish

Reads what the nightly jobs leave in --dir: mutants-<k>.md (tools/modtest/mutate.py's output, one per shard),
repeats.xml (the repeat run's JUnit report: tools/modtest/newtests.py's tests via RepeatNewTests), repeats.txt
(the tests repeated, one Class#method per line) and since.txt (the commit the window starts at).
"""
import argparse
import glob
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

COPY = re.compile(r"^repeat_(\d+)_(\w+?)_(\w+)$")


def read(path):
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read()


def mutants(folder):
    """(lines for the report, killed, total, survivors, problems)."""
    out, killed, total, survivors, problems = [], 0, 0, [], []
    files = sorted(glob.glob(os.path.join(folder, "mutants-*.md")))
    for path in files:
        text = read(path) or ""
        shard = re.sub(r"\D", "", os.path.basename(path))
        if "no mutable lines" in text or "no mutants left" in text:
            continue
        rows = re.findall(r"^\d+\. (KILLED|SURVIVED|TIMEOUT|NO-COMPILE)\s+(\S+) \(([^,]+),", text, re.M)
        if not rows and "Killed " not in text:
            problems.append(f"mutation shard {shard} didn't finish (see its log)")
            continue
        for status, where, op in rows:
            if status == "NO-COMPILE":
                continue
            total += 1
            if status == "SURVIVED":
                survivors.append((where, op))
            else:
                killed += 1
        block = re.search(r"## Survivors.*", text, re.S)
        if block:
            out.append(block.group(0).replace("## Survivors: bugs no test would catch (write a test that fails on each)", "").strip())
    return out, killed, total, survivors, problems


def repeats(folder):
    """({test: (failed copies, copies, first reason)}, problems), from the repeat run's JUnit report (repeats.xml)."""
    import xml.etree.ElementTree as ET
    tests = [t.strip() for t in (read(os.path.join(folder, "repeats.txt")) or "").splitlines() if t.strip()]
    if not tests:
        return {}, []
    report = os.path.join(folder, "repeats.xml")
    if not os.path.exists(report):
        return {}, ["the repeat run left no JUnit report (see repeats.log)"]
    copies, failed = {}, {}
    for case in ET.parse(report).getroot().iter("testcase"):
        m = COPY.match(case.get("name", ""))
        if not m:
            continue
        key = case.get("name").split("_", 2)[2]
        copies[key] = copies.get(key, 0) + 1
        bad = case.find("failure")
        if bad is None:
            bad = case.find("error")
        if bad is not None:
            failed.setdefault(key, []).append((bad.get("message") or bad.text or "failed").strip()[:300])
    out = {}
    for test in tests:
        key = test.replace("#", "_").lower()
        out[test] = (len(failed.get(key, [])), copies.get(key, 0), (failed.get(key) or [""])[0])
    return out, []


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--dir", required=True)
    p.add_argument("--out", required=True)
    a = p.parse_args()
    since = (read(os.path.join(a.dir, "since.txt")) or "?").strip()
    lines = [f"## Nightly mutation and repeats (changes since `{since[:10]}`)", ""]
    survivor_blocks, killed, total, survivors, problems = mutants(a.dir)
    if total == 0 and not problems:
        lines.append("**Mutants:** none planted (no mutable lines changed in the last 24 hours).")
    else:
        lines.append(f"**Mutants:** {killed} of {total} killed" + (f", **{len(survivors)} survived**" if survivors else "") + ".")
    if survivor_blocks:
        lines += ["", "Survivors (a bug here would ship unnoticed: write a test that fails on each):", ""] + survivor_blocks
    reps, rep_problems = repeats(a.dir)
    problems += rep_problems
    flaky = {t: v for t, v in reps.items() if v[0] > 0}
    unrun = [t for t, v in reps.items() if v[1] == 0]
    lines.append("")
    if not reps and not rep_problems:
        lines.append("**Repeats:** no GameTests added in the last 24 hours.")
    else:
        lines.append(f"**Repeats:** {len(reps)} new test(s), each 10 times (3 for builds over 10 minutes)"
                     + (f"; **{len(flaky)} flaky**" if flaky else "; all passed every time") + ".")
        for t, (bad, n, why) in sorted(reps.items()):
            mark = "FLAKY" if bad else ("NOT RUN" if n == 0 else "ok")
            lines.append(f"- {mark}: `{t}` failed {bad} of {n}" + (f": {why}" if why else ""))
    for t in unrun:
        problems.append(f"{t} had no copies in the repeat run")
    if problems:
        lines += ["", "**Problems:**"] + [f"- {x}" for x in problems]
    report = "\n".join(lines) + "\n"
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    with open(a.out, "w", encoding="utf-8") as f:
        f.write(report)
    print(report)
    sys.exit(1 if survivors or flaky or problems else 0)


if __name__ == "__main__":
    main()
