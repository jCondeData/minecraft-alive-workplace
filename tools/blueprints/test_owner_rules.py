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


class AdversarialOwnerRules(unittest.TestCase):
    """QA (lane-b-1121-1538), from 49.1's spec rather than the code: the rules' edges from both sides."""

    def test_soul_lantern_on_the_floor_is_refused(self):
        b = room()
        b.set(4, 1, 4, "soul_lantern", hanging=False)
        self.assertTrue(any("soul_lantern@4,1,4 stands on the floor" in p for p in owner(b)))

    def test_lantern_on_the_bare_ground_is_refused(self):
        b = Build(5, 3, 5)
        b.set(2, 0, 2, "lantern", hanging=False)
        self.assertTrue(any("lantern@2,0,2 stands on the floor (the ground)" in p for p in owner_problems(
            {p: (n, dict(s)) for p, (n, s) in b.blocks.items()})))

    def test_lantern_on_a_barrel_or_bookshelf_passes(self):
        b = room()
        b.set(2, 1, 2, "barrel", facing="up")
        b.set(2, 2, 2, "lantern", hanging=False)
        b.set(6, 1, 6, "bookshelf")
        b.set(6, 2, 6, "lantern", hanging=False)
        self.assertEqual([], [p for p in owner(b) if "lantern" in p])

    def test_balcony_railing_corner_over_air_is_refused(self):
        b = room()
        b.fill(0, 1, 0, 6, 1, 6, "oak_planks")  # a raised deck, its railing one up
        for x in range(1, 5):
            b.set(x, 2, 1, "spruce_fence")
        for z in range(2, 6):
            b.set(5, 2, z, "spruce_fence")
        problems = [p for p in owner_problems({p: (n, dict(s)) for p, (n, s) in b.blocks.items()}) if "runs @" in p]
        self.assertEqual(1, len(problems), problems)
        self.assertIn("no post @5,2,1", problems[0])

    def test_straight_railing_and_a_t_join_are_not_corners(self):
        b = room()
        for x in range(1, 8):
            b.set(x, 1, 1, "oak_fence")
        for z in range(2, 6):
            b.set(4, 1, z, "oak_fence")  # a T: the branch joins the run's middle
        self.assertEqual([], [p for p in owner(b) if "runs @" in p])

    def test_fence_meeting_a_wall_run_is_not_this_rule(self):
        b = room()
        for x in range(1, 5):
            b.set(x, 1, 1, "oak_fence")
        for z in range(2, 6):
            b.set(5, 1, z, "cobblestone_wall")
        self.assertEqual([], [p for p in owner(b) if "runs @" in p])

    def test_plates_on_a_table_with_no_chair_and_no_kitchen_are_refused(self):
        b = room()
        b.set(4, 1, 4, "oak_fence")
        b.set(4, 2, 4, "white_carpet")
        b.set(4, 3, 4, "flower_pot")
        b.furniture("crockery", 4, 3, 4, "north", wood="oak", colour="white")
        self.assertTrue(any("flower_pot@4,3,4 is crockery outside" in p for p in owner(b)))

    def test_plates_just_outside_the_kitchen_reach_are_refused_and_just_inside_pass(self):
        for dx, refused in ((5, True), (4, False)):
            b = room()
            b.set(1, 1, 1, "smoker", facing="south")
            b.set(1 + dx, 1, 1, "spruce_slab", type="top")
            b.set(1 + dx, 2, 1, "flower_pot")
            b.furniture("crockery", 1 + dx, 2, 1, "south", wood="spruce", colour="white")
            self.assertEqual(refused, any("crockery outside" in p for p in owner(b)), dx)

    def test_a_pressure_plate_is_not_crockery(self):
        b = room()
        b.set(4, 1, 4, "oak_pressure_plate")
        self.assertEqual([], [p for p in owner(b) if "crockery" in p])


    def test_plates_two_floors_above_the_kitchen_are_refused(self):
        b = Build(9, 9, 9)
        b.fill(0, 0, 0, 8, 0, 8, "stone")
        b.fill(0, 4, 0, 8, 4, 8, "oak_planks")
        b.set(4, 1, 4, "smoker", facing="south")
        b.set(4, 5, 4, "oak_fence")
        b.set(4, 6, 4, "white_carpet")
        b.set(4, 7, 4, "flower_pot")
        b.furniture("crockery", 4, 7, 4, "north", wood="oak", colour="white")
        self.assertTrue(any("flower_pot@4,7,4 is crockery outside" in p for p in owner(b)))

    def test_an_upside_down_stair_by_the_table_is_not_a_chair(self):
        b = room()
        b.set(4, 1, 4, "oak_fence")
        b.set(4, 2, 4, "white_carpet")
        b.set(3, 1, 4, "oak_stairs", facing="west", half="top")
        b.set(4, 3, 4, "flower_pot")
        b.furniture("crockery", 4, 3, 4, "north", wood="oak", colour="white")
        self.assertTrue(any("flower_pot@4,3,4 is crockery outside" in p for p in owner(b)))

    def test_a_modded_pressure_plate_is_not_crockery(self):
        b = room()
        b.set(4, 1, 4, "somemod:copper_pressure_plate")
        self.assertEqual([], [p for p in owner(b) if "crockery" in p])


class QaCornerRule1121c(unittest.TestCase):
    """QA (lane-b-1121-2138): corners the owner would see that the first tests don't draw."""

    def corner(self, a, b_block):
        b = room()
        for x in range(1, 5):
            b.set(x, 1, 1, a)
        for z in range(2, 6):
            b.set(5, 1, z, b_block)
        return [p for p in owner(b) if "runs @" in p]

    def test_two_woods_meeting_still_need_a_post(self):
        problems = self.corner("oak_fence", "spruce_fence")
        self.assertEqual(1, len(problems), problems)
        self.assertIn("no post @5,1,1", problems[0])

    def test_iron_bars_corner_needs_a_post(self):
        problems = self.corner("iron_bars", "iron_bars")
        self.assertEqual(1, len(problems), problems)
        self.assertIn("no post @5,1,1", problems[0])

    def test_one_block_gap_short_of_the_corner_is_not_a_corner(self):
        b = room()
        for x in range(1, 4):
            b.set(x, 1, 1, "oak_fence")  # stops at x = 3, a gap at 4, so the runs never meet
        for z in range(2, 6):
            b.set(5, 1, z, "oak_fence")
        self.assertEqual([], [p for p in owner(b) if "runs @" in p])


if __name__ == "__main__":
    unittest.main()
