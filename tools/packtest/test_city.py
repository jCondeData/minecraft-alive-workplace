#!/usr/bin/env python3
"""Tests for city.py, the City run's verdict and wall count (27.22, B1363). Run after changing it or CitySoak's line:

  python3 tools/packtest/test_city.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from city import result_line, verdict, wall, wall_summary  # noqa: E402

PREFIX = "[12:00:00] [Server thread/INFO]: "
HEAD = (PREFIX + "City result: 136/136 builds the Steward started finished (2 building, 69 road, 64 wall, 1 take down) in "
        "190000 ticks (7.9 days, 6 of them his); outside their zone or in Keep Clear: none; 40 meals eaten from the store; "
        "0 stalls; items off: none; Steward cost per tick (1 village, 144000 ticks): p50 0.012 ms, p95 0.391 ms, "
        "p99 1.204 ms, worst 64.012 ms (slowest call: roads and walls); ")
TAIL = "; old houses: none found; renewals going: 0; on the desk: nothing"
# The line as CitySoak.state writes it since B1363: the pieces standing, out of the layout's, and the layout by kind.
GOOD = HEAD + "rank town; wall approved (palisade) (wanted: true, 64 of 66 pieces standing; layout: 48 segments, 16 towers, 2 gates)" + TAIL
# The line before B1363 (the wall switched off before it was counted).
OLD = HEAD + "rank town; wall approved (palisade) (wanted: true, 0 pieces standing)" + TAIL

assert result_line("noise\n" + GOOD + "\nmore") == GOOD
assert result_line("no result here") is None

w = wall(GOOD)
assert w == {"state": "approved (palisade)", "wanted": True, "standing": 64, "pieces": 66, "segments": 48, "towers": 16,
             "gates": 2}, w
assert wall_summary(GOOD) == "Wall: approved (palisade), 64 of 66 pieces standing (layout: 48 segments, 16 towers, 2 gates).", wall_summary(GOOD)

# The old line still parses, and says it doesn't know the layout's size.
w = wall(OLD)
assert w["standing"] == 0 and w["pieces"] is None and w["gates"] is None, w
assert "isn't in this line" in wall_summary(OLD)

# No wall drawn: "wall none".
NONE = HEAD + "rank village; wall none (wanted: false, 0 of 0 pieces standing; layout: 0 segments, 0 towers, 0 gates)" + TAIL
w = wall(NONE)
assert w["state"] == "none" and not w["wanted"] and w["pieces"] == 0, w
assert wall("City result: 1/1 builds") is None

# The verdict is the same as before: every build finished, nothing misplaced, no stall, no item off, p95 under 0.5 ms.
assert verdict(GOOD)
assert verdict(OLD)
assert not verdict(GOOD.replace("136/136", "135/136"))
assert not verdict(GOOD.replace("0/0", "x").replace("136/136", "0/0"))
assert not verdict(GOOD.replace("Keep Clear: none;", "Keep Clear: inn at 1 2 3;"))
assert not verdict(GOOD.replace("; 0 stalls;", "; 2 stalls;"))
assert not verdict(GOOD.replace("items off: none;", "items off: oak_log +3;"))
assert not verdict(GOOD.replace("p95 0.391 ms", "p95 0.612 ms"))

print("test_city: all checks passed")
