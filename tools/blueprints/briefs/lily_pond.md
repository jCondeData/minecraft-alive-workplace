# Lily Pond: the brief

Family id(s): `lily_pond` (never renamed)   Variants: village (none drawn), modded (none: decorations are never dressed
by `modded_library.py`), looks (none)

Not in this family: the `footbridge` (another of 26.14's decorations that holds water) has its own brief.

## What it is for
A village decoration with real water in it: a small pond a player has a Builder build to make a village prettier. A
finished Lily Pond within a Village Hall's reach adds beauty (and so wellbeing) to the village and to homes near it.
It hands out no job and no meeting place, and nobody lives or works in it. The player gets it from the Blueprint Table
(locked at first, "A market-day trader": a market-day trader may sell it, a builder's level-up can unlock it, or a found
Lily Pond blueprint used on the Table unlocks it). It is marked on the village map as a decoration, has a hidden
`aliveworkplace:built/lily_pond` advancement for quests, and the Grand Rebuild never rebuilds it. A player can also
have it **taken down** (the placed blueprint handed to a builder while sneaking): every block comes away, the water is
scooped back into buckets, and nothing is left behind or flooded.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `lily_pond` | 2 beauty points (`hall/Decorations.POINTS`); a decoration mark on the village map (`hall/VillageMaps`, kind DECORATIONS) | In `StarterBlueprints.MORE_DECORATIONS` (so in `DECORATIONS`: the Table, the creative tab, the market-day traders' pool, excluded from the Grand Rebuild). Starts locked with the hint `screen.aliveworkplace.table.hint.market` (`MoreDecorationsGameTests.flowerPlantersStartUnlockedTheRestComeFromMarketDay`). |

One level only: there is no `lily_pond_2` and none may be added (an upgrade file would make `BlueprintUpgrades` offer
one, and the 26.14 decorations are one tier each).

## Must have
- Job block(s): **none, and none allowed.** No block in the drawing may be a villager point of interest (no job block,
  bed, bell, beehive, barrel, composter, cauldron, lectern...): `MoreDecorationsGameTests.moreDecorationsHoldNoJobBlock`
  and `QaDecorations1108GameTests.theTableListsEveryNewDecorationWithANameMaterialsAndAWayToGetIt` check every block
  with `PoiTypes.forState`.
- Beds and residents: none. No code counts beds here.
- Storage: **none allowed.** No job reads a container here, and builders never break containers on a take-down, so a
  drawn chest or barrel would still stand afterwards and fail the take-down tests ("taken down still has N of its built
  blocks standing").
- Furniture kinds a job or test needs: none (there is no `blueprint_furniture/lily_pond.json`, and none is needed).
- **Water.** At least **10 water source blocks** (`QaDecorations1104fGameTests.lilyPondOnAHillsideBesideAPitKeepsItsWaterIn`
  asserts the plan pours at least 10). Rules for the water, each one read by code or a test:
  - Every water cell is a **plain still water source** (`minecraft:water`, level 0), poured by the builder from a
    bucket in the DECORATION pass. The Material List asks exactly **one water bucket per source** and the take-down
    tests require that count to equal the number of source cells in the plan (`QaTakeDown1105GameTests`, "asks for N
    liquid buckets for N sources").
  - **No flowing water** (level 1-15): `MaterialRules.classify` calls it SKIP, and
    `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable` refuses any SKIP block.
  - **No other block that holds water**: no seagrass, tall seagrass, kelp, bubble column, or waterlogged sea pickle or
    coral. They count as liquid cells in the take-down tests but don't cost exactly one water bucket each, or aren't
    scooped like a source, so the bucket counts come out wrong. Waterlogged stairs, slabs and the like are built
    **dry** (`MaterialRules.forPlacement`), so they never add water either.
  - **The drawing holds its own water, on any ground.** Directly under every water source there must be a drawn block
    that isn't air or liquid (so no water in the bottom layer, y = 0), and beside each source on all four sides there
    must be another water source or a drawn block that water can't flow into or wash away (not air, a plant, a flower, a
    carpet, a torch, a rail...). The hillside test builds it with the ground one block *higher* than y = 0 under its
    east third, two blocks higher at its east edge, and a 1-deep pit dug under its west bank. 200 ticks after the build,
    every drawn source must still be a source and there must be **no water anywhere else** in the test area. The take-down
    tests also turn it a quarter and mirror it, and it must hold there too.
  - Anything resting on the water (a lily pad) can stand only on a water source.
- Anything else the code or a test reads: nothing. No bell, sign, lectern or door is read for this family.

## Every block must be one a survival builder can place and take back
- No block a builder can't place (`MaterialRules.classify` == SKIP, e.g. flowing water, fire, air-like technical
  blocks): `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`.
- No creative-only block and no material a survival player can't get (bedrock, barrier, light, structure or command
  blocks, spawners, budding amethyst, farmland, dirt path, infested blocks, petrified slab...):
  `QaDecorations1108GameTests.theTableListsEveryNewDecorationWithANameMaterialsAndAWayToGetIt` (its `UNOBTAINABLE` and
  `CREATIVE_BLOCKS` lists).
- Built from **exactly** its Material List it must finish **as drawn with 0 skipped steps**, and taken down it must give
  every item back exactly (`QaDecorations1104fGameTests` and `QaTakeDown1105GameTests`). So nothing may change by
  itself after it is placed, in the up to 16,000 ticks the tests wait: no dirt beside grass (it turns to grass), no
  concrete powder near the water (it sets), no ice (it melts), no crops or saplings that
  grow, nothing that needs a block a later pass puts down to survive. Plants stand in their own soil (the owner's rule).
- Stocking: the tests put the whole list into 4 containers of 27 slots, and a water bucket takes a slot of its own, so
  **(water sources) + (stacks of every other item) <= 108** (`BuilderGameTests.buildStarter`, "test needs more barrels";
  `QaDecorations1104fGameTests.exact` and `QaTakeDown1105GameTests.stock`, "more stacks than chests").

## Fits
- Plot: at most **9 (x) by 7 (z)** footprint, at most **8** tall. `MoreDecorationsGameTests.moreDecorationsAreDecorationsWithBeautyAndAName`
  allows up to 9 x 8 x 9 for any decoration, but the Lily Pond tests pin x <= 9 and z <= 7:
  - `QaTakeDown1105GameTests.aPlayersOwnPoolBesideTheLilyPondIsLeftAlone` puts the player's own stone basin at helper
    x 18-20 (z 10-14, y 2-3), one block east of a footprint that starts at x 9, so the pond may be at most 9 wide in x;
  - `QaTakeDown1105GameTests.aTurnedMirroredLilyPondComesDownDryWithItsBucketsBack` (origin (17, 2, 16), turned
    clockwise and mirrored front to back) and every take-down test require the footprint inside helper x/z 6..21, y 1..17;
  - `QaDecorations1104fGameTests.lilyPondOnAHillsideBesideAPitKeepsItsWaterIn` is set up for a footprint of x 6-14,
    z 6-12 at origin (6, 2, 6) (the rises at x 12 and 14 and the pit at x 3-7, z 7-11 are placed against that
    footprint). Keeping 9 x 7 keeps every test's setup meaning what it says.
- Declared size: `StarterBlueprints.LILY_POND` is `9, 3, 7` today. If the new drawing's size differs, the ship step
  changes that `Vec3i` to match (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`), and the
  wiki's size column.
- y = 0 sits on the clicked block; the front is the z = 0 side.
- Front and way in: no door is needed. When the builder finishes, his path back to his bench starts one block in front
  of the middle of the z = 0 edge (`Paths.doorstep`'s fallback), so that spot should be ground a villager can walk onto
  from the front. (A door drawn in would move the path's start to that door.)
- Neighbours: placed freely by the player (the Steward has no rule that builds it); nothing is placed beside it by a
  village plan. Walls and roads go round its footprint.

## Rules tests check
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: the `.nbt`'s size equals
  `StarterBlueprints.LILY_POND`'s, and no block in it is SKIP.
- `MoreDecorationsGameTests.moreDecorationsHoldNoJobBlock`: no POI block.
- `MoreDecorationsGameTests.moreDecorationsAreDecorationsWithBeautyAndAName` (by id: in `DECORATIONS`, beauty >= 1, a
  lang name, size within 9 x 8 x 9) and `flowerPlantersStartUnlockedTheRestComeFromMarketDay` (by id: locked, market hint).
- `MoreDecorationsGameTests.buildsLilyPond`: a builder with exactly the plan's materials in 4 barrels builds it at origin
  (9, 2, 9) in `big_area` within **12,000 ticks**, every block as drawn.
- `QaDecorations1104fGameTests.lilyPondFromExactlyItsList`: built from exactly the held blueprint's Material List,
  finished as drawn, **0 skipped**.
- `QaDecorations1104fGameTests.lilyPondOnAHillsideBesideAPitKeepsItsWaterIn`: on the hillside beside the pit (above),
  finishes as drawn with 0 skipped in 12,000 ticks; at least 10 water sources; 200 ticks later all still sources where
  drawn and no other fluid in helper x/z 0..21, y 0..12.
- `QaDecorations1104fBugTests.lilyPondTakenDownLeavesNoWaterAndGivesBackItsCost` (via `QaDecorations1104fGameTests.takeDown`):
  built from exactly its list, then taken down; 100 ticks after, none of its STRUCTURE or DECORATION blocks stand, no
  fluid and no fire anywhere in helper x/z 0..21, y 1..12, the builder's bag is empty, every non-bucket item comes back
  exactly, and water buckets + empty buckets equal the water buckets paid. 16,000 ticks for all of it.
- `QaTakeDown1105GameTests` (each 18,000 ticks for the build, the take-down and 100 ticks after; none of its blocks
  standing, no liquid left in or round it, the bag empty, every item back, buckets neither made nor lost):
  `lilyPondTakenDownAcrossASaveAndReloadEndsDryWithItsBucketsBack` (a save and reload when half the sources are
  scooped), `lilyPondTakenDownWithNoEmptyBucketMakesNoBucketAndLeavesNoWater`,
  `lilyPondTakenDownWithFourEmptyBucketsFillsOnlyFour`, `lilyPondTakenDownWithBucketsInHisBagAndAChestFillsEveryOne`
  (5 empties: the pond needs at least 5 sources for this to mean anything, which 10 covers),
  `aTurnedMirroredLilyPondComesDownDryWithItsBucketsBack`, `aPlayersOwnPoolBesideTheLilyPondIsLeftAlone`.
- `QaDecorations1108GameTests.aLilyPondSurvivesARestartMidBuildAndFinishesAsDrawn`: from exactly its list, the server
  restarted once mid-STRUCTURE (or just after it); it must finish as drawn with 0 skipped **before tick 8,800**. This is
  the tightest time budget of all the tests: keep the number of blocks to place modest enough for a builder (build
  delay 2) to finish well inside it.
- `QaDecorations1108GameTests.theTableListsEveryNewDecorationWithANameMaterialsAndAWayToGetIt`: listed on the Table
  with a size and at least one block, its name "Lily Pond" (by id, American English), materials all obtainable, no
  creative or POI block, its `built/lily_pond` advancement exists.
- The owner's rules (`check.py`) and STYLE.md's checks: `rebuild.py --candidate` prints no CHECK line, and `holes.py`
  passes.

## Free
Everything not listed above: the pond's shape and depth, how many sources beyond 10 (within the 108-slot stocking
limit and the time budget), what holds the water, everything round it (banks, plants, seats, paths, lights), materials,
height up to 8, lighting and style.
