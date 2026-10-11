#!/usr/bin/env python3
"""
25.4's soak verdict: is the heap after a full GC flat over the soak (25.2's target: within 5%)?

    python3 tools/packtest/soakheap.py build/packtest/server/soak-heap.txt [settle minutes, 10]

Reads the readings `PERF=true SOAK_MINUTES=60 tools/packtest/run.sh` writes, one "minute megabytes" pair a line, prints
each one and a "Soak verdict:" line. The readings before the settle minute are shown but not judged: the builds and the
villagers' memories fill up first. No later reading may be more than 5% above the first settled one. Only growth
fails: a heap that falls is not a leak (the owner's soak 38166107551 fell 10.2% as the builds finished and was
flagged by the old two-sided check), so a fall is reported but passes. Exits 1 when the heap grew past the limit or
fewer than two settled readings were taken.
"""
import sys

LIMIT = 0.05


def verdict(rows, settle=10):
    """(flat or not, the verdict line) for (minute, MB) readings."""
    settled = [(m, mb) for m, mb in rows if m >= settle]
    if len(settled) < 2 or settled[0][1] <= 0:
        return False, f"Soak verdict: not judged ({len(rows)} reading(s), {len(settled)} from minute {settle} on; need 2)."
    base = settled[0][1]
    worst = max(settled, key=lambda r: r[1] - base)
    flat = all(mb - base <= LIMIT * base for _, mb in settled)
    low = min(settled, key=lambda r: r[1])
    fall = (f", lowest {100 * (low[1] - base) / base:+.1f}% at minute {low[0]} (a fall is not a leak)"
            if low[1] < base * (1 - LIMIT) else "")
    return flat, (f"Soak verdict: heap {'no growth' if flat else 'GREW'}: {base} MB at minute {settled[0][0]}, "
                  f"{settled[-1][1]} MB at minute {settled[-1][0]}, highest {100 * (worst[1] - base) / base:+.1f}% "
                  f"at minute {worst[0]}{fall} (target: no more than {100 * LIMIT:.0f}% growth).")


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
