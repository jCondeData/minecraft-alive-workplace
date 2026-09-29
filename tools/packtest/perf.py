#!/usr/bin/env python3
"""
Reads a Java Flight Recorder file from the pack test's performance mode and says how much of the server thread's
time went into Alive Workplace code, and where.

    python3 tools/packtest/perf.py build/packtest/server/perf.jfr
"""
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
        yield (thread.group(1) if thread else "?"), frames


def main(path):
    total = 0
    ours = 0
    top_self = collections.Counter()
    entry = collections.Counter()
    context = collections.Counter()
    for thread, frames in samples(path):
        if thread != "Server thread":
            continue
        total += 1
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
    for label in CONTEXT:
        print(f"  {100 * context[label] / total:5.1f}%  {label}")
    print("by entry point (outermost frame of ours):")
    for name, n in entry.most_common(12):
        print(f"  {100 * n / total:5.1f}%  {name}")
    print("by innermost frame of ours:")
    for name, n in top_self.most_common(15):
        print(f"  {100 * n / total:5.1f}%  {name}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "build/packtest/server/perf.jfr")
