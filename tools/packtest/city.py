#!/usr/bin/env python3
"""The City run's verdict (CITY=true tools/packtest/run.sh, 27.22), from the mod's "City result:" line in server.log.

  python3 tools/packtest/city.py server.log

Passes only when every build the Steward started finished, none in Keep Clear, no stall, no item off, and his p95 cost
a tick is under 0.5 ms. It also prints the wall: how many of the layout's pieces stand (B1363: the line used to read
"0 pieces standing" whatever stood, because the soak switched the wall off before it counted).
"""
import re
import sys

WALL = re.compile(r"; wall (?P<state>[^;(]*(?:\([^)]*\))?) \(wanted: (?P<wanted>true|false), (?P<standing>\d+)"
                  r"(?: of (?P<pieces>\d+))? pieces standing(?:; layout: (?P<segments>\d+) segments, (?P<towers>\d+) towers, "
                  r"(?P<gates>\d+) gates)?\)")


def result_line(text):
    return next((l for l in text.splitlines() if "City result:" in l), None)


def wall(line):
    """The wall part of the line: {state, wanted, standing, pieces, segments, towers, gates} (None where the line
    doesn't say), or None when the line has no wall part."""
    m = WALL.search(line)
    if not m:
        return None
    out = {"state": m.group("state").strip(), "wanted": m.group("wanted") == "true"}
    for k in ("standing", "pieces", "segments", "towers", "gates"):
        out[k] = int(m.group(k)) if m.group(k) is not None else None
    return out


def wall_summary(line):
    w = wall(line)
    if w is None:
        return "Wall: not in the result line."
    if w["pieces"] is None:
        return f"Wall: {w['state']}, {w['standing']} pieces standing (the layout's size isn't in this line)."
    return (f"Wall: {w['state']}, {w['standing']} of {w['pieces']} pieces standing "
            f"(layout: {w['segments']} segments, {w['towers']} towers, {w['gates']} gates).")


def verdict(line):
    m = re.search(r"City result: (\d+)/(\d+) builds", line)
    p95 = re.search(r"p95 ([\d.]+) ms", line)
    return bool(m and m.group(1) == m.group(2) and int(m.group(2)) > 0 and "Keep Clear: none;" in line
                and re.search(r"; 0 stalls; items off: none;", line) and p95 and float(p95.group(1)) < 0.5)


def main():
    with open(sys.argv[1], errors="replace") as f:
        line = result_line(f.read())
    if line is None:
        sys.exit("No City result line: the run didn't end.")
    print(wall_summary(line))
    ok = verdict(line)
    print("City passed." if ok else "The city run did not pass.")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
