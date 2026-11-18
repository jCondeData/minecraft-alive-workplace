# Caravan Wreck: the brief

Family id(s): `story/caravan_wreck` (one template, never renamed)   Variants: village (none), modded (none), looks
(none). One drawing, set down turned any of the four ways at random.

## What it is for
The **first place of the Lost Caravan** story arc (ROADMAP 31.19 and 31.20, `arcs/lost_caravan.json`,
`story/CaravanTrail`, `story/LostCaravanStory`). It is never built by a builder and never offered at the Table: the
arc engine sets it down in one go (`ArcEffects.build`) once a player comes within 96 blocks (`Arcs.NEAR`). When a
caravan on a road longer than 300 blocks goes missing, the sending village's players get a map marked with this
wreck (`give_map` at `wreck`), and the wreck lies a third of the way along the caravan's road (or 150 blocks out when
that ground isn't fit). It is what is left of **the settlers' covered wagon**, run off the road by raiders: the
player who comes within 12 blocks of it (`LostCaravanStory.FIND`, measured from the wreck's middle) reads "The
caravan's wreck: tracks lead away from the road", chapter 1 ends, and the quest "Follow the tracks" starts them on
the five trail marks (`story/trail_1` to `trail_5`) that lead 120 to 180 blocks on to the raiders' stockade, where the
carter is caged and the lost goods are kept. So the wreck is the story's first scene and its clue: a wrecked
settlers' wagon by the road, its load spilled, the start of a trail. The story it must tell is that one (the wiki and
the showcase call it "the settlers' wagon on its side" with its goods spilled); how it tells it is free.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| (one) | `story/caravan_wreck` | The wreck the player finds; its middle is the spot "found" is measured from | None: set down by `ArcEffects.build` for the arc, never built, upgraded or sold. `lostCaravanChance` in the config decides how often the arc starts |

## Must have
- **The six blocks the tests read** (`CaravanTrailGameTests.theWreckLiesOnUnevenGround`, template coordinates with
  the template unturned). Each block id is a requirement; the spot may move only if the ship step changes the test's
  coordinate with it (same meaning, new spot; never delete a check):
  - `minecraft:spruce_planks` at **(6, 2, 5)**, "the wagon's floor";
  - `minecraft:white_wool` at **(2, 1, 5)**, "the canvas". The showcase reads this cell too (`JobScenes`, scene
    `caravan_trail`, step `01_wreck`: `p.origin.offset(2, 1, 5)` is white wool), so a move changes both;
  - `minecraft:stripped_dark_oak_log` at **(7, 4, 3)**, "the upper wheel's hub";
  - `minecraft:stripped_dark_oak_log` at **(10, 1, 7)**, "the broken wheel";
  - `minecraft:lantern` at **(9, 1, 10)**, "the lantern in the grass". It must also pass the owner's lamp rule
    (below): it may **not** stand on the ground. Its block below must be a slab, fence, wall, chain, trapdoor, stair,
    rod or a piece of furniture (barrel, composter, chest...), or the lantern hangs (`hanging=true`). The old one
    stands on grass and is the wreck's one baselined owner-rule problem;
  - `minecraft:composter` at **(2, 1, 1)**, "a spilled crate".
- **No villager or other mob in the file.** Nothing is spawned at the wreck. Any entity hung on a block (an item
  frame, a painting) must be saved with the block it hangs on, or vanilla drops it.
- **Containers: none required, none filled.** No code opens, fills or reads a chest, barrel or composter in the wreck
  and no loot table is set (the lost goods are in the stockade's chest, `LostCaravanStory.setUp`). A container in the
  drawing is an empty prop. No `blueprint_furniture/caravan_wreck.json` exists and no test needs one.
- **Job blocks, beds, residents: none.** No code counts them here.

## Fits
- **How it is set down** (`ArcEffects.build` with `CaravanTrail.fits`):
  - The arc turns it a random way (`place`: `level.random.nextInt(4)`; the test and the showcase use the unturned
    template) and puts **its middle** (`size / 2` on x and z) on the ground column of the arc's spot, then on columns
    round it out to 32 blocks until one fits.
  - **Fit ground** (`CaravanTrail.fits`): no water in or just above any column; at most `columns * 20 / 195` columns
    (at least 1) more than a block above or below the middle's ground; as many tree trunks at most; nothing a player
    built in the box or 4 below; only natural ground, plants and trees in the whole box from the ground up to the
    template's top. A bigger or taller template finds fit ground less often.
  - **Template y = 0 is the ground layer**: it replaces the world's top block. A y = 0 cell the drawing leaves out (no
    block saved there, as the old drawing's `keep_ground` does) keeps the world's own ground. Never save **air** at
    y = 0: the code fills the ground first (every replaceable cell from y = 0 to 3 below becomes dirt) and the template
    is placed after, so a saved air cell makes a hole the test catches.
  - **Everything in the box from y = 1 up is cleared first** (a hump of hill, grass, a bush), so the wreck sits on
    whatever the land is: flat, a step up or down, a filled hollow.
- **Plot: at least 78 columns on the ground (9 x 9 does), at most 16 x 16, at most 18 tall** (y 0 to 17):
  - 78 columns at least: the test's ground has 8 uneven columns (a 2-high hump at helper x/z 11-12 and a 2-deep hole at
    x 18-19, z 17-18, both inside any footprint 7 or more wide); `fits` allows `columns * 20 / 195` of them, so a
    smaller footprint is refused at the test's middle, the build moves 8 blocks off, and the origin check fails.
  - 16 x 16 at most: a player is "finding" the wreck within 12 blocks of its middle (`FIND`); within 16 x 16 every
    corner is within 12, so walking onto any part of it finds it. The test's own limits are wider (x at most 27, or the
    footprint takes in the player's cobblestone at helper (1, 4, 15) and `fits` refuses it; z at most 30, the 30 x 30
    `tall_area`), and so is the showcase's (the camera stands 10 x and 11.5 z from the middle, 7 up: a footprint 22 or
    more on x, or 24 or more on z, swallows it).
  - 18 tall at most: the showcase clears the scene up to 17 above the ground. The road check (below) looks only 7 high.
  - Note for the ship step: `CaravanTrail.road` tests the road spot with `fitAround(level, onRoad, 6)`, a 13 x 13 box
    7 tall. A footprint over 13 or a height over 7 isn't fully covered by that check, so the wreck may land up to 32
    blocks off the road; raise that `6` to the new half-size if the wreck grows.
- Front and way in: **no front**. It is turned at random, so it must read and work from every side. The player only
  needs to come near it; nothing needs to be entered or clicked.
- Neighbours: the caravan's road (no road is drawn by the code; the wreck is on its line), and the first trail mark
  some 20 to 30 blocks on toward the stockade. Nothing else is placed beside it.

## Rules tests check
- `CaravanTrailGameTests.theWreckLiesOnUnevenGround` (`tall_area`, grass at helper y 3, the wreck's middle at helper
  (15, 3, 15), unturned, a hump and a hole in its footprint):
  - it builds at its spot on the first try (`placed == 1`, not skipped) with its corner at helper (9, 3, 9): the corner
    is `middle - size / 2`, so a new size changes that number and the test's `lies(helper, p.origin, 13, 12)` size with
    it (the ship step);
  - **every column of the footprint has a non-air block at y = 0 and at y = -1** ("open ground", "a hole under the
    build");
  - **no `minecraft:dirt` or `minecraft:grass_block` at y = 1 or y = 2 anywhere in the footprint** ("the hump still
    stands"): the drawing itself may not put either block there (other earth blocks are not checked);
  - the player's cobblestone at helper (1, 4..5, 15) still stands (outside the footprint);
  - the six blocks above at their spots.
- Showcase scenes `caravan_trail` (step `01_wreck`: built, and white wool at (2, 1, 5)) and `lost_caravan` (step
  `01_wreck`: built), both with the wreck's middle at (-16, -61, -22) in `JobScenes` (`CARAVAN_WRECK`), the trail mark
  at x 2 and the stockade at x 24.
- By place only (the drawing doesn't matter): `CaravanTrailGameTests.theRulesPlaceTheTrail` and
  `followTrailCountsTheMarksInOrderOnly`, `LostCaravanGameTests`, `QaLostCaravanGameTests` (they read or set the
  wreck's spot, or mark it skipped).
- Every shipped template (all of `structure/`, `story/` included):
  - `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`: a standing lantern, candle or torch on a block
    whose top holds its middle; no dirt path or farmland under a solid block; no half bed;
  - `B648GameTests.b648NoShippedGlassEndsInTheAir`, `QaBuilders1028GameTests.qaB648NoShippedGlassEndsInTheAirAsTheGameJoinsIt`,
    `QaWindowsAndWards1028BugTests.qaNoShippedGlassEndsInTheAirAsTheGameJoinsIt`: a glass pane or iron bar joined on
    only one side doesn't end in air;
  - `StationsSpecGameTests.noStructureOfOursUsesAGoneBlock`: the template loads and uses no removed block.
- The owner's rules (`check.py`, which walks every `.nbt` under `structure/`). `story/caravan_wreck` is in
  `owner_rules_baseline.json` today (1 problem: the lantern standing on the grass); the rebuild must be clean and the
  ship step removes its baseline entry: posts at fence, wall and pane corners; a lantern on a table, slab, post or
  hanging, never on the floor; plates and crockery only by a stove, furnace, smoker or counter or on a table with a
  chair. Plus STYLE.md's checks and `rebuild.py --candidate` printing no CHECK line.
- Ship step (not a design rule): `generate.py` stops drawing `caravan_wreck` from `caravan.py` (the `REBUILT` set);
  the test's corner, size and six coordinates and the showcase's (2, 1, 5) follow the new drawing; the wiki's
  description in `docs/wiki/lost-caravan.md` ("The wreck", 13 x 12) and its render are updated.

## Free
Everything not listed above: what the wreck is made of beyond the six pinned ids, its shape, plan, height and
footprint within the limits, which y = 0 cells it draws and which it leaves to the world's ground, what is spilled
and where, props, lighting (beyond the one pinned lantern) and planting.
