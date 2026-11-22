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

    def test_lantern_on_a_rug_is_still_on_the_floor(self):
        b = room()
        b.set(4, 1, 4, "red_carpet")
        b.set(4, 2, 4, "lantern", hanging=False)
        self.assertTrue(any("lantern@4,2,4 stands on the floor" in p for p in owner(b)))

    def test_lantern_on_a_wide_rug_is_still_on_the_floor_and_on_a_table_cloth_passes(self):
        # B1444: a rug wider than the lamp; a carpet on a fence post is a table and stays allowed
        b = room()
        b.fill(3, 1, 3, 5, 1, 5, "red_carpet")
        b.set(4, 2, 4, "lantern", hanging=False)
        b.set(1, 1, 1, "oak_fence")
        b.set(1, 2, 1, "white_carpet")
        b.set(1, 3, 1, "lantern", hanging=False)
        problems = [p for p in owner(b) if "lantern" in p]
        self.assertEqual(1, len(problems), problems)
        self.assertIn("lantern@4,2,4 stands on the floor", problems[0])

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


class ShippedModdedCrockery(unittest.TestCase):
    """The owner's rule 3 on what modded_library.py ships: Handcrafted's crockery set goes on a table at a meal (a seat
    beside it) or in a kitchen (a stove, furnace, smoker or sink in reach). A shop's sales counter is a `counter` spot
    too, so check.py counts it as a kitchen; this reads the shipped files without that, so plates on a shop's display
    table are caught (lane-b-1121-1838, B1450)."""

    def test_modded_crockery_sits_at_a_meal_or_in_a_kitchen(self):
        import glob
        from check import baselined, crockery_problems, load_blueprint
        known = baselined()  # drawn before the owner's rules and listed until their rebuild (Village Hall II)
        root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..",
                            "src/main/resources/data/aliveworkplace/structure")
        wrong = []
        for path in sorted(glob.glob(os.path.join(root, "modded", "*.nbt"))):
            name = "modded/" + os.path.basename(path)[:-4]
            if name in known:
                continue
            b = load_blueprint(path, name)
            blocks = {q: (v[0], dict(v[1])) for q, v in b.blocks.items()}
            spots = [s for s in b.spots if s["kind"] != "counter"]
            wrong += [name + ": " + line for line in crockery_problems(blocks, spots) if "crockery_combo@" in line]
        self.assertEqual([], wrong)


class WallHungPieces(unittest.TestCase):
    """Every shipped drawing's wall-hung pieces (wall torches, ladders, tripwire hooks, wall buttons and levers) hang on
    a block that can hold them: a pane, bars, a fence or wall, a chain, a carpet, a lantern, a door, a trapdoor, a pot or
    drawn air can't, so the builder defers the piece and skips it, and the finished build is missing it (B1451, B1452,
    B1453; lane-b-1121-1838). Stairs and slabs are left out: whether their side holds depends on their shape."""

    FACING = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}
    HUNG = ("minecraft:wall_torch", "minecraft:soul_wall_torch", "minecraft:redstone_wall_torch", "minecraft:ladder",
            "minecraft:tripwire_hook")
    CANT_HOLD = ("pane", "iron_bars", "fence", "_wall", "chain", "carpet", "flower_pot", "potted_", "lantern",
                 "trapdoor", "_door", "minecraft:air")

    def hung(self, piece, behind, **props):
        """check()'s lines for a {@code piece} at (4, 2, 4) hanging north (on the block south of it) on {@code behind}."""
        b = room()
        b.fill(4, 1, 5, 4, 3, 5, "stone")
        if behind:
            b.set(4, 2, 5, behind)
        b.set(4, 2, 4, piece, facing="north", **props)
        return [p for p in check(b) if "@4,2,4" in p]

    def test_check_refuses_a_hook_torch_ladder_button_or_lever_on_a_pane(self):
        for piece, props in (("tripwire_hook", {"attached": "false", "powered": "false"}), ("wall_torch", {}),
                             ("ladder", {}), ("oak_button", {"face": "wall", "powered": "false"}),
                             ("lever", {"face": "wall", "powered": "false"})):
            for behind in ("glass_pane", "iron_bars", "oak_fence", "cobblestone_wall", "chain", "spruce_trapdoor"):
                problems = self.hung(piece, behind, **props)
                self.assertTrue(any("can't hold one" in p for p in problems), f"{piece} on {behind}: {problems}")

    def test_check_passes_a_hook_on_a_log_or_planks(self):
        for behind in ("oak_log", "spruce_planks", "bricks"):
            self.assertEqual([], self.hung("tripwire_hook", behind, attached="false", powered="false"), behind)

    def test_every_wall_hung_piece_hangs_on_a_block_that_holds_it(self):
        import glob
        from holes import load
        root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..",
                            "src/main/resources/data/aliveworkplace/structure")
        wrong = []
        for path in sorted(glob.glob(os.path.join(root, "**", "*.nbt"), recursive=True)):
            name = os.path.relpath(path, root)[:-4]
            (w, h, d), cells, states = load(path, props=True)
            for p, block in cells.items():
                props = states.get(p, {})
                if block in self.HUNG:
                    facing = props.get("facing")
                elif block and (block.endswith("_button") or block == "minecraft:lever") and props.get("face") == "wall":
                    facing = props.get("facing")
                else:
                    continue
                dx, _, dz = self.FACING[facing]
                behind = (p[0] - dx, p[1], p[2] - dz)
                held_by = cells.get(behind)
                if held_by is None:
                    continue  # outside the drawing or a structure void: the ground or another stage holds it
                if any(t in held_by for t in self.CANT_HOLD):
                    wrong.append(f"{name}: {block[10:]}@{p} hangs on {held_by[10:]}@{behind}")
        self.assertEqual([], wrong, "\n" + "\n".join(wrong))

    def test_check_refuses_a_floor_lever_or_button_on_a_fence_and_passes_it_on_planks(self):
        """B1485: the Quarry's lever stood on a fence post, whose top isn't sturdy, so the builder skipped it."""
        for piece in ("lever", "oak_button"):
            for under, held in (("spruce_fence", False), ("cobblestone_wall", False), ("glass_pane", False),
                                ("spruce_planks", True), ("oak_log", True)):
                b = room()
                b.set(4, 1, 4, under)
                b.set(4, 2, 4, piece, face="floor", facing="east", powered="false")
                problems = [p for p in check(b) if "@4,2,4" in p]
                self.assertEqual(held, problems == [], f"{piece} on {under}: {problems}")

    def test_every_floor_or_ceiling_lever_and_button_has_a_sturdy_face(self):
        import glob
        from holes import load
        root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..",
                            "src/main/resources/data/aliveworkplace/structure")
        wrong = []
        for path in sorted(glob.glob(os.path.join(root, "**", "*.nbt"), recursive=True)):
            name = os.path.relpath(path, root)[:-4]
            _, cells, states = load(path, props=True)
            for p, block in cells.items():
                face = states.get(p, {}).get("face")
                if not block or not (block.endswith("_button") or block == "minecraft:lever") or face not in ("floor", "ceiling"):
                    continue
                q = (p[0], p[1] - 1 if face == "floor" else p[1] + 1, p[2])
                on = cells.get(q)
                if on is None:
                    continue  # outside the drawing or a structure void
                if on == "minecraft:air" or any(t in on for t in self.CANT_HOLD) or (
                        on.endswith(("_slab", "_stairs")) and states[q].get("type", states[q].get("half"))
                        not in (("top", "double") if face == "floor" else ("bottom", "double"))):
                    wrong.append(f"{name}: {block[10:]}@{p} ({face}) on {on[10:]}@{q}")
        self.assertEqual([], wrong, "\n" + "\n".join(wrong))


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
