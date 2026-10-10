#!/usr/bin/env python3
"""
Reads a Java Flight Recorder file from the pack test's performance mode and says how much of the server thread's
time went into Alive Workplace code, and where.

    python3 tools/packtest/perf.py build/packtest/server/perf.jfr
    python3 tools/packtest/perf.py season.jfr --days server.log   # 30.22: also our share in each "Season day" of the log
"""
import bisect
import collections
import re
import subprocess
import sys

OURS = "io.github.jcondedata.aliveworkplace."
# Vanilla code by its intermediary names (the production server runs those), for context.
CONTEXT = {
    "villager brains (all their AI, ours included)": "net.minecraft.class_1646.method_5958",
    "villager pathfinding": "net.minecraft.class_1408.",
}


def samples(path):
    out = subprocess.run(["jfr", "print", "--events", "jdk.ExecutionSample", "--stack-depth", "128", path],
                         capture_output=True, text=True, check=True).stdout
    for block in out.split("jdk.ExecutionSample {")[1:]:
        thread = re.search(r'sampledThread = "([^"]*)"', block)
        frames = re.findall(r"^\s+([\w$.<>/]+)\(", block, re.M)
        when = re.search(r"startTime = (\d\d):(\d\d):(\d\d)\.(\d\d\d)", block)
        second = int(when.group(1)) * 3600 + int(when.group(2)) * 60 + int(when.group(3)) + int(when.group(4)) / 1000 if when else -1
        yield (thread.group(1) if thread else "?"), frames, second


def long_ticks(log):
    """The season run's ticks over 50 ms, from its "Season long ticks" lines: [start s, end s, day, samples, ours]."""
    ticks = []
    for line in open(log, errors="replace"):
        m = re.search(r"Season long ticks day (\d+):((?: \d+\+\d+)+)", line)
        if m:
            for entry in m.group(2).split():
                end, took = (int(x) for x in entry.split("+"))
                ticks.append([(end - took) / 1000, end / 1000, int(m.group(1)), 0, 0, collections.Counter()])
    return sorted(ticks)


def day_windows(log):
    """The season run's days from the server log: (label, first second, last second) by the clock of its lines."""
    marks = []
    for line in open(log, errors="replace"):
        m = re.match(r"\[(\d\d):(\d\d):(\d\d)\].*?(Season: a |Season day (\d+) \((\w+)\))", line)
        if m:
            second = int(m.group(1)) * 3600 + int(m.group(2)) * 60 + int(m.group(3))
            marks.append((second, None if m.group(5) is None else f"day {m.group(5)} ({m.group(6)})"))
    windows = []
    for (start, _), (end, label) in zip(marks, marks[1:]):
        if label:
            windows.append((label, start, end))
    return windows


def main(path, log=None):
    windows = day_windows(log) if log else []
    by_day = {label: [0, 0] for label, _, _ in windows}
    slow = long_ticks(log) if log else []
    slow_starts = [t[0] for t in slow]
    total = 0
    ours = 0
    top_self = collections.Counter()
    entry = collections.Counter()
    context = collections.Counter()
    for thread, frames, second in samples(path):
        if thread != "Server thread":
            continue
        total += 1
        for label, start, end in windows:
            # A day that ran over midnight on the clock ends "before" it starts.
            if start <= second < end or end < start and (second >= start or second < end):
                by_day[label][0] += 1
                by_day[label][1] += any(f.startswith(OURS) for f in frames)
                break
        at = bisect.bisect_right(slow_starts, second) - 1
        if at >= 0 and second <= slow[at][1]:
            slow[at][3] += 1
            inside = [f for f in frames if f.startswith(OURS)]
            if inside:
                slow[at][4] += 1
                slow[at][5][inside[-1][len(OURS):] + " > " + inside[0][len(OURS):]] += 1
        for label, prefix in CONTEXT.items():
            if any(f.startswith(prefix) for f in frames):
                context[label] += 1
        mine = [f for f in frames if f.startswith(OURS)]
        if not mine:
            continue
        ours += 1
        entry[mine[-1][len(OURS):]] += 1  # the outermost of our frames: which job's code started it
        top_self[mine[0][len(OURS):]] += 1  # the innermost: where the time actually went (with what it called)
    if total == 0:
        print("no server thread samples")
        return
    print(f"server thread samples: {total}; in Alive Workplace code: {ours} ({100 * ours / total:.1f}%)")
    for label, (n, mine) in by_day.items():
        print(f"  our share of the tick, {label}: {100 * mine / n:.1f}% ({mine} of {n} samples)" if n else f"  our share of the tick, {label}: no samples")
    if log:
        # No tick over 50 ms caused by us (25.2): our part of a long tick is its length times our share of its samples.
        seen = [t for t in slow if t[3]]
        mine = [(1000 * (t[1] - t[0]) * t[4] / t[3], t) for t in seen]
        ours_over = [(ms, t) for ms, t in mine if ms > 50]
        share = 100 * sum(t[4] for t in seen) / max(1, sum(t[3] for t in seen))
        print(f"  ticks over 50 ms: {len(slow)} ({len(seen)} of them sampled); our code in {share:.1f}% of their samples; "
              f"ticks with over 50 ms of our code: {len(ours_over)}")
        for ms, t in sorted(ours_over, key=lambda x: -x[0])[:5]:
            where = ", ".join(f"{name} x{n}" for name, n in t[5].most_common(3))
            print(f"    day {t[2]}: {ms:.0f} ms of ours in a tick of {1000 * (t[1] - t[0]):.0f} ms ({t[4]} of {t[3]} samples: {where})")
    for label in CONTEXT:
        print(f"  {100 * context[label] / total:5.1f}%  {label}")
    print("by entry point (outermost frame of ours):")
    for name, n in entry.most_common(12):
        print(f"  {100 * n / total:5.1f}%  {name}")
    print("by innermost frame of ours:")
    for name, n in top_self.most_common(15):
        print(f"  {100 * n / total:5.1f}%  {name}")


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if a != "--days"]
    main(args[0] if args else "build/packtest/server/perf.jfr", args[1] if "--days" in sys.argv and len(args) > 1 else None)
