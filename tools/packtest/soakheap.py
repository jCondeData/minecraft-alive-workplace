#!/usr/bin/env python3
"""
25.4's soak verdict: is the heap after a full GC flat over the soak (25.2's target: within 5%)?

    python3 tools/packtest/soakheap.py build/packtest/server/soak-heap.txt [settle minutes, 10]

Reads the readings `PERF=true SOAK_MINUTES=60 tools/packtest/run.sh` writes, one "minute megabytes" pair a line, prints
each one and a "Soak verdict:" line. The readings before the settle minute are shown but not judged: the builds and the
villagers' memories fill up first. Every later reading must be within 5% of the first settled one. Exits 1 when the
heap isn't flat or fewer than two settled readings were taken.
"""
import sys

LIMIT = 0.05


def verdict(rows, settle=10):
    """(flat or not, the verdict line) for (minute, MB) readings."""
    settled = [(m, mb) for m, mb in rows if m >= settle]
    if len(settled) < 2 or settled[0][1] <= 0:
        return False, f"Soak verdict: not judged ({len(rows)} reading(s), {len(settled)} from minute {settle} on; need 2)."
    base = settled[0][1]
    worst = max(settled, key=lambda r: abs(r[1] - base))
    flat = all(abs(mb - base) <= LIMIT * base for _, mb in settled)
    return flat, (f"Soak verdict: heap {'flat' if flat else 'NOT flat'}: {base} MB at minute {settled[0][0]}, "
                  f"{settled[-1][1]} MB at minute {settled[-1][0]}, furthest {100 * (worst[1] - base) / base:+.1f}% "
                  f"at minute {worst[0]} (target: within {100 * LIMIT:.0f}%).")


def main(path, settle=10):
    try:
        rows = [tuple(int(x) for x in line.split()[:2]) for line in open(path) if line.strip()]
    except FileNotFoundError:
        rows = []
    for minute, mb in rows:
        print(f"Soak heap: minute {minute}: {mb} MB")
    flat, line = verdict(rows, settle)
    print(line)
    return 0 if flat else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "build/packtest/server/soak-heap.txt",
                  int(sys.argv[2]) if len(sys.argv) > 2 else 10))
