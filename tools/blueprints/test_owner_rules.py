#!/usr/bin/env python3
"""Tests for check.py's owner rules (ROADMAP 49.1): a corner without its post, a floor lantern and plates by a door
are refused; the same pieces done right pass. Small made-up builds, nothing on disk. Run after changing check.py:

  python3 tools/blueprints/test_owner_rules.py
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from check import OWNER, check, owner_problems, report  # noqa: E402
from kit import Build, finish  # noqa: E402


def room():
    """A 9 x 5 x 9 stone floor, nothing on it."""
    b = Build(9, 5, 9)
    b.fill(0, 0, 0, 8, 0, 8, "stone")
    return b


def owner(b):
    finish(b)
    return [p for p in check(b) if p.startswith(OWNER)]


class CornerRule(unittest.TestCase):
    def railing(self, post):
        b = room()
        for x in range(1, 5):
            b.set(x, 1, 1, "oak_fence")
        for z in range(2, 6):
            b.set(5, 1, z, "oak_fence")
        if post:
            b.set(5, 1, 1, "oak_fence")
        return b

    def test_corner_without_its_post_is_refused(self):
        problems = owner(self.railing(post=False))
        self.assertEqual(1, len(problems), problems)
        self.assertIn("no post @5,1,1", problems[0])

    def test_corner_with_its_post_passes(self):
        self.assertEqual([], owner(self.railing(post=True)))

    def test_walls_and_panes_too(self):
        for block in ("cobblestone_wall", "glass_pane"):
            b = room()
            for x in range(1, 4):
                b.set(x, 1, 1, block)
            for z in range(2, 5):
                b.set(4, 1, z, block)
            self.assertTrue(any("no post @4,1,1" in p for p in owner_problems(
                {p: (n, dict(s)) for p, (n, s) in b.blocks.items()})), block)

    def test_a_post_or_log_in_the_corner_passes(self):
        b = self.railing(post=False)
        b.set(5, 1, 1, "spruce_log", axis="y")
        self.assertEqual([], owner(b))


class LampRule(unittest.TestCase):
    def test_floor_lantern_is_refused(self):
        b = room()
        b.set(4, 1, 4, "lantern", hanging=False)
        problems = owner(b)
        self.assertEqual(1, len(problems), problems)
        self.assertIn("lantern@4,1,4 stands on the floor", problems[0])

    def test_lantern_on_a_slab_table_or_post_passes(self):
        b = room()
        b.set(2, 1, 2, "spruce_slab", type="top")
        b.set(2, 2, 2, "lantern", hanging=False)
        b.set(4, 1, 4, "oak_fence")
        b.set(4, 2, 4, "white_carpet")  # a table: a fence leg with a carpet top, and a lamp beside it on a post
        b.set(6, 1, 6, "oak_fence")
        b.set(6, 2, 6, "lantern", hanging=False)
        b.set(2, 1, 6, "oak_slab", type="bottom")
        b.set(2, 2, 6, "lantern", hanging=False)
        self.assertEqual([], owner(b))

    def test_hanging_lantern_passes(self):
        b = room()
        b.fill(0, 4, 0, 8, 4, 8, "oak_planks")
        b.set(4, 3, 4, "lantern", hanging=True)
        self.assertEqual([], [p for p in owner(b) if "lantern" in p])


class CrockeryRule(unittest.TestCase):
    def test_plates_by_a_door_are_refused(self):
        b = room()
        b.fill(0, 1, 8, 8, 3, 8, "stone_bricks")
        b.set(4, 1, 8, "oak_door", half="lower", facing="north")
        b.set(4, 2, 8, "oak_door", half="upper", facing="north")
        b.set(5, 1, 7, "spruce_slab", type="top")
        b.set(5, 2, 7, "flower_pot")
        b.furniture("crockery", 5, 2, 7, "north", wood="spruce", colour="white")
        problems = owner(b)
        self.assertEqual(1, len(problems), problems)
        self.assertIn("flower_pot@5,2,7 is crockery outside a kitchen", problems[0])

    def test_plates_in_a_kitchen_pass(self):
        b = room()
        b.set(2, 1, 2, "furnace", facing="south")
        b.set(3, 1, 2, "spruce_planks")
        b.set(3, 2, 2, "smooth_stone_slab", type="top")
        b.furniture("counter", 3, 1, 2, "south", cells=((0, 0, 0), (0, 1, 0)), wood="spruce")
        b.set(3, 3, 2, "flower_pot")
        b.furniture("crockery", 3, 3, 2, "south", wood="spruce", colour="white")
        self.assertEqual([], owner(b))

    def test_plates_on_a_dining_table_pass(self):
        b = room()
        b.set(4, 1, 4, "oak_fence")
        b.set(4, 2, 4, "white_carpet")
        b.set(3, 1, 4, "oak_stairs", facing="west", half="bottom")  # a chair facing the table
        b.set(4, 3, 4, "flower_pot")
        b.furniture("crockery", 4, 3, 4, "north", wood="oak", colour="white")
        self.assertEqual([], owner(b))

    def test_modded_plate_by_a_door_is_refused(self):
        b = room()
        b.set(4, 1, 4, "handcrafted:wood_plate")
        self.assertTrue(any("crockery outside" in p for p in owner(b)))


class Baseline(unittest.TestCase):
    def test_baselined_blueprint_folds_its_owner_problems_into_one_line(self):
        problems = [OWNER + "lantern@1,1,1 stands on the floor", OWNER + "x", "a door is missing a half"]
        from check import baselined
        listed = next(iter(baselined()), None)
        self.assertIsNotNone(listed, "owner_rules_baseline.json lists the blueprints drawn before the rules")
        folded = report(listed, problems)
        self.assertEqual(2, len(folded))
        self.assertTrue(folded[1].startswith("(baselined) 2 owner-rule problem(s)"))
        self.assertEqual(problems, report("a_new_rebuild_not_listed", problems))


if __name__ == "__main__":
    unittest.main()
