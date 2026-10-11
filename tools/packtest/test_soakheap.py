#!/usr/bin/env python3
"""Tests for soakheap.py's verdict: growth past 5% fails, a fall passes. Run after changing it:

  python3 tools/packtest/test_soakheap.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from soakheap import verdict  # noqa: E402

# The owner's soak 38166107551: the heap fell 10.2% from minute 10. Not a leak, so it passes.
FELL = [(0, 1637), (10, 1634), (20, 1597), (30, 1567), (40, 1578), (50, 1572), (60, 1467)]
ok, line = verdict(FELL)
assert ok, line
assert "a fall is not a leak" in line, line

# Growth of 10% from minute 10 fails.
ok, line = verdict([(0, 1500), (10, 1600), (20, 1650), (30, 1760)])
assert not ok, line
assert "GREW" in line, line

# Within 5% either way passes; exactly at the limit passes.
assert verdict([(10, 1000), (20, 1030), (30, 980), (40, 1050)])[0]

# Readings before the settle minute aren't judged, even when they are far off.
assert verdict([(0, 400), (10, 1000), (20, 1010)])[0]

# Fewer than two settled readings: not judged, fails.
ok, line = verdict([(0, 1000), (10, 1000)])
assert not ok and "not judged" in line, line

print("test_soakheap: 5 checks pass")
