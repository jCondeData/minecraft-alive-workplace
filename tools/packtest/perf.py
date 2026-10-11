#!/usr/bin/env python3
"""
Reads a Java Flight Recorder file from the pack test's performance mode and says how much of the server thread's
time went into Alive Workplace code, and where.

    python3 tools/packtest/perf.py build/packtest/server/perf.jfr
    python3 tools/packtest/perf.py season.jfr --days server.log   # 30.22: also our share in each "Season day" of the log
    python3 tools/packtest/perf.py --spikes soak-1.jfr soak-2.jfr ...  # 25.4: all of them together, then the long ticks
    python3 tools/packtest/perf.py perf.jfr --ticks server.log   # 25.3: also our code's own time a tick, as the mod timed it

--ticks (25.3): in the benchmark the mod times its own code each tick (command/OurTickCost: our job behaviours and tick
handlers, with System.nanoTime) and logs a "Benchmark: our code a tick" line a minute and a "Benchmark: a tick with"
line for each tick with over 50 ms of ours. These are measured, not sampled: they tell a long tick of ours from ticks
run back to back to catch up, which the profile alone can't. With --spikes, each long stretch also says whether a tick
of ours over 50 ms was logged within a second of it.

--spikes (25.4, the soak's p99 spikes): the benchmark load logs no tick times of its own, so the long ticks are found in
the profile itself. Between ticks the server thread sleeps and isn't sampled; a run of its samples with no gap over
SPIKE_GAP ms is one busy stretch, and a stretch of 50 ms or more is a long tick (or ticks run back to back to catch up).
Each one is named by its share of our code and the frames it spent most samples in, and the garbage collector's pauses
are listed beside them, because a pause stops the sampler too (it shows as a gap, not as a stretch). An estimate: time
the thread spends blocked inside a tick (waiting for a chunk, say) isn't sampled either.
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


SPIKE_GAP = 25    # ms: a busy tick at 20 TPS leaves the thread asleep for 30 ms or more before the next one
SPIKE_MS = 50     # 25.2's target: no tick over 50 ms caused by us


def duration_ms(text):
    """A JFR duration ("12.3 ms", "1.02 s", "850 us", "40 ns") in milliseconds."""
    m = re.match(r"([\d.]+)\s*(ns|us|ms|s|min)", text.strip())
    if not m:
        return 0.0
    return float(m.group(1)) * {"ns": 1e-6, "us": 1e-3, "ms": 1.0, "s": 1000.0, "min": 60000.0}[m.group(2)]


def gc_pauses(path):
    """The collector's pauses in one recording: (start second, longest pause ms, name, cause)."""
    out = subprocess.run(["jfr", "print", "--events", "jdk.GarbageCollection", path],
                         capture_output=True, text=True, check=True).stdout
    for block in out.split("jdk.GarbageCollection {")[1:]:
        when = re.search(r"startTime = (\d\d):(\d\d):(\d\d)\.(\d\d\d)", block)
        pause = re.search(r"longestPause = ([^\n]+)", block)
        name = re.search(r'name = "([^"]*)"', block)
        cause = re.search(r'cause = "([^"]*)"', block)
        if when and pause:
            second = int(when.group(1)) * 3600 + int(when.group(2)) * 60 + int(when.group(3)) + int(when.group(4)) / 1000
            yield second, duration_ms(pause.group(1)), name.group(1) if name else "?", cause.group(1) if cause else "?"


def stretches(times_frames, gap_ms=SPIKE_GAP):
    """Splits the server thread's samples (second, frames), in time order, into busy stretches."""
    run = []
    for second, frames in times_frames:
        if run and (second - run[-1][0]) * 1000 > gap_ms:
            yield run
            run = []
        run.append((second, frames))
    if run:
        yield run


def spikes(paths, top=10, log=None):
    ours_long = [second for second, _, _ in our_ticks(log)[1]] if log else None
    long = []
    pauses = []
    for path in paths:
        mine = sorted((second, frames) for thread, frames, second in samples(path) if thread == "Server thread" and second >= 0)
        for run in stretches(mine):
            # The sampler's period is about 10 ms (settings=profile): a stretch lasts one period past its last sample.
            ms = (run[-1][0] - run[0][0]) * 1000 + 10
            if ms < SPIKE_MS:
                continue
            ours = sum(1 for _, frames in run if any(f.startswith(OURS) for f in frames))
            where_ours = collections.Counter()
            where_all = collections.Counter()
            for _, frames in run:
                inside = [f for f in frames if f.startswith(OURS)]
                if inside:
                    where_ours[inside[-1][len(OURS):] + " > " + inside[0][len(OURS):]] += 1
                if frames:
                    where_all[frames[0]] += 1
            long.append((ms, path, run[0][0], len(run), ours, where_ours, where_all))
        pauses.extend((second, ms, name, cause, path) for second, ms, name, cause in gc_pauses(path))
    print(f"--- long ticks in the profiles (busy stretches of {SPIKE_MS} ms or more, gaps over {SPIKE_GAP} ms split them)")
    seen = sum(n for _, _, _, n, _, _, _ in long)
    ours = sum(o for _, _, _, _, o, _, _ in long)
    over = [x for x in long if x[0] * x[4] / max(1, x[3]) > SPIKE_MS]
    print(f"long stretches: {len(long)}; our code in {100 * ours / max(1, seen):.1f}% of their samples; "
          f"with over {SPIKE_MS} ms of our code (length x our share): {len(over)}")
    for ms, path, start, n, o, where_ours, where_all in sorted(long, key=lambda x: -x[0])[:top]:
        clock = f"{int(start // 3600) % 24:02d}:{int(start // 60) % 60:02d}:{start % 60:06.3f}"
        line = f"  {ms:.0f} ms at {clock} ({path.rsplit('/', 1)[-1]}): ours {o} of {n} samples"
        if where_ours:
            line += "; ours in " + ", ".join(f"{k} x{v}" for k, v in where_ours.most_common(2))
        line += "; innermost " + ", ".join(f"{k} x{v}" for k, v in where_all.most_common(3))
        if ours_long is not None:
            day = int(start) % 86400
            timed = any(day - 1 <= s <= day + ms / 1000 + 1 for s in ours_long)
            line += "; a tick of ours over 50 ms logged then: " + ("yes" if timed else "no")
        print(line)
    big = [p for p in pauses if p[1] >= 20]
    worst = max((p[1] for p in pauses), default=0)
    print(f"GC pauses: {len(pauses)}, {len(big)} of them 20 ms or longer (longest {worst:.0f} ms)")
    for second, ms, name, cause, path in sorted(big, key=lambda p: -p[1])[:5]:
        clock = f"{int(second // 3600) % 24:02d}:{int(second // 60) % 60:02d}:{second % 60:06.3f}"
        print(f"  {ms:.0f} ms at {clock} ({path.rsplit('/', 1)[-1]}): {name}, {cause}")


def our_ticks(log):
    """25.3: the mod's own timer from the server log: (window lines [(p50, p99, max, biggest)], long ticks [(second, ms, biggest)])."""
    windows, longs = [], []
    for line in open(log, errors="replace"):
        m = re.search(r"Benchmark: our code a tick over the last \d+ ticks: p50 ([\d.]+) ms, p99 ([\d.]+) ms, max ([\d.]+) ms \(biggest call: (.*?)\);", line)
        if m:
            windows.append((float(m.group(1)), float(m.group(2)), float(m.group(3)), m.group(4)))
        m = re.search(r"\[(\d\d):(\d\d):(\d\d)\].*Benchmark: a tick with ([\d.]+) ms of our code \(biggest call: (.*)\)", line)
        if m:
            longs.append((int(m.group(1)) * 3600 + int(m.group(2)) * 60 + int(m.group(3)), float(m.group(4)), m.group(5)))
    return windows, longs


def print_our_ticks(log):
    windows, longs = our_ticks(log)
    print(f"--- our code a tick, timed by the mod (25.3; {len(windows)} one-minute windows in {log.rsplit('/', 1)[-1]})")
    if not windows:
        print("  no \"Benchmark: our code a tick\" lines (an older jar, or the load never started)")
        return
    worst = max(windows, key=lambda w: w[2])
    print(f"  max {worst[2]:.1f} ms ({worst[3]}); worst minute's p99 {max(w[1] for w in windows):.1f} ms; "
          f"median p50 {sorted(w[0] for w in windows)[len(windows) // 2]:.1f} ms")
    print(f"  ticks with over {SPIKE_MS} ms of our code: {len(longs)}")
    for second, ms, what in sorted(longs, key=lambda x: -x[1])[:5]:
        print(f"    {ms:.1f} ms at {second // 3600:02d}:{second // 60 % 60:02d}:{second % 60:02d} (biggest call: {what})")


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
    paths = path if isinstance(path, list) else [path]
    windows = day_windows(log) if log else []
    by_day = {label: [0, 0] for label, _, _ in windows}
    slow = long_ticks(log) if log else []
    slow_starts = [t[0] for t in slow]
    total = 0
    ours = 0
    top_self = collections.Counter()
    entry = collections.Counter()
    context = collections.Counter()
    for thread, frames, second in (x for p in paths for x in samples(p)):
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
    ticks_log = None
    if "--ticks" in sys.argv:
        at = sys.argv.index("--ticks")
        ticks_log = sys.argv[at + 1]
        del sys.argv[at:at + 2]
    if "--spikes" in sys.argv:
        files = [a for a in sys.argv[1:] if a != "--spikes"]
        main(files)
        spikes(files, log=ticks_log)
        if ticks_log:
            print_our_ticks(ticks_log)
        sys.exit(0)
    if ticks_log and len(sys.argv) == 1:
        print_our_ticks(ticks_log)
        sys.exit(0)
    args = [a for a in sys.argv[1:] if a != "--days"]
    main(args[0] if args else "build/packtest/server/perf.jfr", args[1] if "--days" in sys.argv and len(args) > 1 else None)
    if ticks_log:
        print_our_ticks(ticks_log)
