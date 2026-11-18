# The Trail Marks: the brief

Family id(s): `story/trail_1`, `story/trail_2`, `story/trail_3`, `story/trail_4`, `story/trail_5` (never renamed:
`CaravanTrail.MARKS` lists them by id, `arcs/lost_caravan.json` names each in its `"each"` list, and
`CaravanTrail.isTrail` gives these ids, and only these, the uneven-ground fitting)   Variants: village (none),
modded (none), looks (none). Five separate pieces, one template each, no levels or upgrades.

Not in this family: the Caravan Wreck (`story/caravan_wreck`) and the Raiders' Stockade (`story/raiders_stockade`),
the trail's two ends (their own briefs).

## What it is for
The Lost Caravan story (ROADMAP 31.19/31.20, `docs/wiki/lost-caravan.md`). A caravan goes missing; its wreck lies
on the road, and the raiders who took it hold the captive carter in a stockade 120 to 180 blocks further on, on the
side away from the villages. The five marks are the tracks between the two: the quest "Follow the tracks"
(`follow_trail`) asks the player to reach them **in order**, mark 1 first. Each counts when the player stands within
**6 blocks (across the ground) of its middle column** (`CaravanTrail.RADIUS`), and then the action bar says only which
of the eight compass directions the next mark lies in ("The tracks go on to the north-east"; after mark 5, the way to
the stockade). The marks are set evenly between the wreck and the stockade (`CaravanTrail.between`: 6 gaps over 120
to 180 blocks, so **20 to 30 blocks apart**, and up to 32 blocks further when a mark had to move to find fit ground).
So the player stands at one mark, is told a direction to within 45 degrees, and must **find the next one by eye, 20
to 30+ blocks away, across whatever land is there** (plains, forest edge, desert, snow, a hillside), over a whole
trail of 120+ blocks. Being seen and recognised as "the next mark" from that distance, from any side, is part of
what they are for (the 52.5 triage critic's finding: today only mark 3 catches the eye). They also tell the story in
order: what the raiders and the dragged-off caravan left behind on the way. Nobody lives, works or trades at a mark;
it is never built by a builder, never on the Blueprint Table, never on the village map: the arc engine sets it down
whole (`StructureTemplate.placeInWorld`) the first time a player comes within 96 blocks (`Arcs.NEAR`).

## Levels
| Mark | Id | Its part in the story (function, not looks) | What code or a test reads from it |
|---|---|---|---|
| 1 | `story/trail_1` | The first sign past the wreck: a lantern someone dropped | A `minecraft:lantern` at **(0, 1, 2)** (`theFiveMarksLieOnUnevenGround`) |
| 2 | `story/trail_2` | A smashed barrel from the caravan's load | A `minecraft:barrel` at **(1, 1, 1)** (same test) |
| 3 | `story/trail_3` | A torn white banner (the settlers' color) | A `minecraft:white_banner` (the standing banner, not `white_wall_banner`) at **(1, 3, 1)** and a `minecraft:spruce_fence` at **(1, 1, 1)** (same test). Also the mark `QaLostCaravanGameTests` builds alone (below) |
| 4 | `story/trail_4` | Cart ruts where the wagon's goods were dragged | A `minecraft:dirt_path` at **(0, 0, 1)**, in the ground layer (same test) |
| 5 | `story/trail_5` | An arrow-studded crate: the raiders were here | A `minecraft:composter` at **(1, 1, 1)** and a `minecraft:tripwire_hook` at **(0, 1, 1)** (same test) |

All coordinates are template cells (x, y, z) from the template's corner, read with the template unrotated. The
block ids are pinned: the test asserts `state.is(Blocks.X)` at exactly that cell. Each pinned block's properties
(facing, open, hanging, a banner's patterns, a composter's level) are free unless a rule below says otherwise. A
rebuild that wants a prop elsewhere must move the test's coordinate with it in the same commit (same meaning, new
spot), never drop the check (52.5 critic, note 1).

## Must have (each mark)
- Job block(s): none read. (A barrel and a composter are pinned above as story props; no villager is ever near.)
- Beds and residents: none. No entity is spawned at a mark.
- Storage: none read. Mark 2's barrel is never opened by code; no loot table is set or expected.
- Furniture kinds a job or test needs: none. There is no `blueprint_furniture/trail_*.json`; if the rebuild's plan
  records furniture spots, `rebuild.py` writes them and the furnished-rooms rules then apply to them.
- **The ground layer (y = 0) is the ground itself.** The template's y = 0 replaces the world's top ground block (it is
  not set on top of it, unlike a builder's blueprint); everything in the box above y = 0 is cleared first. A y = 0
  cell saved as **air cuts a hole** in the ground; a cell left out of the file (structure void) keeps the world's own
  ground there. `rebuild.py` saves every undrawn cell as air, so either every footprint cell of y = 0 is drawn as
  ground, or the ship step strips the air from y = 0 the way the old drawing's `keep_ground` did.
- Mark 1's lantern at (0, 1, 2) must pass **both** lamp checks, which the old one fails (it is in
  `owner_rules_baseline.json` today, and the rebuild must print no CHECK line): `check.py`'s owner rule "a lamp under
  2 blocks tall standing on a floor" (a lantern on the ground with walkable ground beside it is refused) and
  `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds` (a standing lantern's block below must hold its
  middle: a bottom-half slab, a bottom trapdoor or a composter does not). So it hangs (`hanging=true`, with a block
  above it that holds it) or stands on a stand both checks accept (a post, a top-half slab, solid furniture such as a
  barrel). Since its stand would be at (0, 0, 2), that cell of the ground layer may be the stand.
- Mark 3's banner at (1, 3, 1) must be held up (`check.py`'s "nothing holds it up" check; a standing banner needs a
  solid block under it), so (1, 2, 1) holds it; the template is at least 4 tall.
- Mark 4's dirt path at (0, 0, 1) must have nothing the game counts as solid directly above it at (0, 1, 1) (a
  fence, slab, wall, banner, sign, trapdoor or full block): the game turns it to dirt, and both `check.py` and
  `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds` refuse it (a fence gate is the one exception).
- Mark 5's tripwire hook at (0, 1, 1) faces away from the block it is fixed to; it needs that block beside it.
- No fluid (no water or lava cell, no waterlogged block): nothing reads one, and the code only sets a mark on dry
  ground (`CaravanTrail.fits`).

## Fits
- How it lands (`ArcEffects.build`): turned a **random quarter** (`level.random.nextInt(4)` when the arc picks the
  spot), so there is no front: the player may come at it from any side, and everything must work in all four
  rotations. **Centred** on the spot: the corner is the spot's ground column minus `size / 2` in x and z, and after
  placing, the mark's "middle" (the spot the 6-block count uses) is recomputed as the centre of the placed box at
  y = 1. Before placing, everything above y = 0 in the box is removed and under each column up to 4 blocks of air or
  replaceable blocks below y = 0 are filled with dirt, so it lies on a hump or a hollow as a camp does.
- Where it may land (`CaravanTrail.fits`, over the whole box, ground to top): no water at or just above any column's
  top; no more than `max(1, columns x 20 / 195)` columns more than 1 block above or below the middle's ground (a 3 x 3
  allows 1, a 5 x 5 allows 2, a 7 x 7 allows 5), and no more than that many columns with a log 1 or 2 above the
  ground; nothing a player built in it or 4 below it; and only natural blocks (air, plants, leaves, logs, dirt, sand,
  stone, snow, gravel, clay...) anywhere in its box. Failing that it tries columns 8, 16, 24 and 32 blocks round
  the spot, else the mark is skipped (it still counts when the player reaches its column). So a bigger or taller
  box is harder to place and moves more often.
- Plot (all from the tests below, with the template unrotated):
  - **At least 3 x 3** (x by z): the test inspects the 3 x 3 cells from the corner, and the pins need z of at least 3
    for mark 1 and a height of at least 4 for mark 3.
  - **Width along x odd** (3, 5, ...): the test asserts the placed middle's x equals the spot's x ("mark N moved"); with
    an even width the box's centre is half a block off and the rounding (Java's integer division) moves it one block
    whenever the test runs at positive x, which fails.
  - **Mark 1: exactly 3 wide along x.** Its spot is helper x 3 and the test's player wall (cobblestone at helper
    (1, 4..5, 15)) stands at x 1, z 15: a wider mark 1 has it in its box, `fits` refuses, the mark moves and the test
    fails.
  - **Marks 2 to 5: at most 5 wide along x.** The test sets the five 6 blocks apart along x (spots at helper x 3, 9,
    15, 21, 27, in order) and a mark's box may not reach a mark already placed (its blocks are not natural ground);
    mark 5 at x 27 must also stay inside the 30-block test area. (A 7 is possible only for a mark whose neighbours are
    3 wide, and only if the test's spacing is kept; 5 is the simple limit.)
  - Depth along z: no test needs more than 3; the tests' areas allow up to about 25. Rotation swaps x and z in the
    game, so the depth is also a width there.
  - Height: no code limit; the tests' area is 30 tall with the ground at helper y 3 or 4, so at most about 25.
  - The player must be able to count it: the count is within 6 blocks of the box's centre, so a player standing on
    the edge of a mark wider than about 12 would not count it.
- Front and way in: none (random rotation). Nobody enters it.
- Neighbours: none in the game (marks are 20 to 30 blocks apart, the wreck and stockade further); in the test, the
  other marks 6 blocks apart along x.

## Rules tests check
- `CaravanTrailGameTests.theFiveMarksLieOnUnevenGround` (`tall_area`, 30 x 30 x 30; dirt at helper y 2, grass at y 3,
  then a grass step at y 4 on every even x for z 12..18, and a two-deep hole at (16, 2..3, 16)). For i = 0..4 it
  builds `MARKS[i]` unrotated with its spot at helper (3 + 6i, 3, 15), in order, and asserts:
  - it is built at once, not waiting and not skipped (`placed == 1`, `!skipped`, an origin), so it fits at the exact
    spot on that ground;
  - "mark N moved": the placed middle's x equals the spot's x (see Fits: odd width, no overlap, not over the wall);
  - "a hole under mark N": for x, z in 0..2 the cell under the ground layer (y = -1) is not air;
  - "earth over mark N": for x, z in 0..2 the cell at y = 1 is not `grass_block` (so no grass block at y = 1 in those
    cells of the drawing);
  - the pinned blocks of the table above, at their template cells from the corner.
- `QaLostCaravanGameTests.aMarkIsPlacedWhenAPlayerComesWithin96` (`tall_area`, mark 3 alone, spot helper (15, 4, 15)
  on flat grass): not built with a player 97 blocks away, **built (not skipped) at 96**. So `story/trail_3` must fit
  on flat open grass at its spot.
- `QaLostCaravanGameTests.aMarkIsNeverPlacedOverAPlayersBlocks` (mark 3, spot helper (15, 4, 15), a player's planks
  at the spot and ground the ledger marks at (16, 3, 15)): it is placed elsewhere or skipped, the player's blocks are
  untouched, and if placed its middle is more than 3 blocks from the spot in x or z. (Any footprint passes: a large
  one is simply skipped.)
- `QaLostCaravanGameTests` (`onlyMark3`): the real arc's place `trail#3` holds exactly `story/trail_3`; the arc's
  `"each"` list keeps the five ids in order.
- `CaravanTrailGameTests.followTrailCountsTheMarksInOrderOnly` and `theRulesPlaceTheTrail`,
  `QaLostCaravanGameTests` (the trail's text, reload, three players): they read the marks' spots, order and count
  (5), not their blocks; they hold whatever the drawing is, as long as the five ids stay.
- Every shipped structure (these five are read too):
  - `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`: standing lanterns, candles and torches on a block
    whose top holds their middle; no dirt path or farmland under a solid block; every bed with its other half.
  - `B648GameTests.b648NoShippedGlassEndsInTheAir`, `QaBuilders1028GameTests.qaB648NoShippedGlassEndsInTheAirAsTheGameJoinsIt`
    and `QaWindowsAndWards1028BugTests.qaNoShippedGlassEndsInTheAirAsTheGameJoinsIt`: no iron bar or glass pane joined
    on one side with open air on the other.
  - `BlueprintsStayGameTests` counts every `.nbt` (at least 300): the files stay.
- The owner's rules (`check.py`, run by `rebuild.py`: no CHECK line; `story/trail_1` leaves `owner_rules_baseline.json`
  when it is rebuilt) and STYLE.md's checks, with `holes.py` passing.
- Ship step: `docs/wiki/lost-caravan.md` describes each mark ("trampled coarse dirt and a dropped lantern; a smashed
  barrel...") and links `media/trail-marks.png`; both change with the new drawing. The old drawing is
  `tools/blueprints/caravan.py` (`trail_1` .. `trail_5`, saved by `generate.py`); the ship step stops `generate.py`
  from writing these five over the rebuilt files.

## Free
Everything else: what each mark is made of, its shape, height and size within the limits above, what else lies on
it, its patch of ground, and how it reads from afar, as long as each of the five is still that mark of the story and
a player can pick it out from the last one.
