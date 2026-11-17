# Well: the brief

Family id(s): `well`, `well_2` (never renamed)   Variants: village (none drawn: a Steward zone's style, e.g. `cherry`,
is applied to the drawing automatically by `BlueprintStyles`), modded (none: decorations are never dressed by
`modded_library.py`), looks (none)

Not in this family: `well_house` (a one-tier CARE building of its own, the Well Keeper's answer to droughts) is a
separate family with its own brief.

## What it is for
A village decoration with real water in it. A finished Well near a Village Hall adds beauty (and so wellbeing) to the
village and to the homes within 16 blocks of it; in a drought it takes away every villager's "The wells are low" mood
penalty; and its water is a place a Well Keeper (a villager at a cauldron) can dip a bucket to carry to the fields.
The Steward builds one by himself when a village of five or more has little beauty. The player gets it from the
Blueprint Table (locked until the market unlock) and Level II also from village chests' loot.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `well` | 2 beauty points (`Decorations.POINTS`); ends the drought mood penalty; water a Well Keeper can use | Blueprint Table, locked with the hint `screen.aliveworkplace.table.hint.market` (`BlueprintUnlocksGameTests`); the Steward's rule `steward_rules/well.json` builds it in a `gardens` zone when beauty < 3, villagers >= 5, none built yet |
| II | `well_2` | 3 beauty points instead of I's 2 (an upgrade on the same spot counts instead of its base); still ends the drought penalty (`drought.json` lists both ids); counts as tier II for a class's `building` need | Upgrade of `well` (the id `well` + `_2`, `BlueprintUpgrades`); built on Level I's spot by the builder, who only builds what changed; also found as a blueprint in village chest loot |

## Must have (each level)
- Job block(s): none. No code looks for a workstation in either level.
- Beds and residents: none. No code counts beds here.
- Storage: none read. No job or test reads a container in the Well (a container drawn in is free, but builders never
  break containers on a take-down, so it stays behind).
- Furniture kinds a job or test needs: none. Since 52.1 `blueprint_furniture/well_2.json` records the Market Well's
  six bench seats (spruce-stair chairs) as furniture spots; no job reads them.
- **Water (Level I and Level II):** at least one **water source block** in the drawing.
  - `QaTakeDown1105GameTests.wellTakenDownLeavesNoWaterAndGivesBackItsCost` refuses a Well that "pours no liquid".
  - Every water cell must be a source the builder pours from a bucket, held in so it never flows out of the build
    (the list asks one water bucket per source, and after the take-down no water, source or flowing, may be left in
    or round the footprint).
  - At least one source must have a **non-water block above it** (air is fine) and a spot a villager can stand on within
    3 blocks of it: that is what a Well Keeper looks for (`WellKeeperWork.findWater`, `walker.reach(..., 3.0)`).
- Anything else the code or a test reads: nothing. No bell, sign, lectern or door is read for this family.

## Fits
- Plot: **exactly 7 x 7 footprint (x by z) at Level I**, odd so its centre is a block (3, 3); Level II the same 7 x 7
  footprint (it must be at least Level I's size in every axis, `starterUpgradesKeepMostOfTheirBase`, and about 20
  GameTests place Wells by hard-coded coordinates for a 7 x 7, see below). Height: at most **10** for either level
  (PlotGameTests clears only 10 blocks of air above its ground; the take-down test allows 16). Level II at least as
  tall as Level I. y = 0 sits on the clicked block; the front is the z = 0 side.
- Declared sizes: `StarterBlueprints.WELL` is `7, 9, 7` and `WELL_2` is `7, 10, 7` today (52.1). If a new height differs, the
  ship step changes those two `Vec3i`s to match (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).
- Front and way in: the Steward turns the plot so the z = 0 side faces the road, or else the hall
  (`PlotGameTests.plotOnFlatGroundFacesTheRoad`). There is no door: a lane to it starts one block in front of the
  middle of the z = 0 edge (`Paths.doorstep`'s fallback), so that spot should be ground a villager can walk onto from the
  front. (A door drawn in would move the lane's start to that door instead.)
- Neighbours: placed freely as a decoration; the Steward keeps 2 blocks clear round its plot and other builds, and
  walls and roads go round its footprint. Level II grows on Level I's exact spot and origin.

## Rules tests check
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: each `.nbt`'s size equals its
  `StarterBlueprints` size, and every block in it is one a builder can place (none classified `SKIP`).
- `BuilderGameTests.starterUpgradesKeepMostOfTheirBase`: `well_2` is at least `well`'s size in x, y and z, and keeps at
  least **60 %** of `well`'s non-air blocks at the same position in the same exact state (the upgrade is Level I plus
  more, never a different well). The count of starter upgrades (64) must not change: `well_2` stays.
- `BuilderGameTests.buildsWell`: a builder with exactly the plan's materials in barrels builds `well_2` (from nothing,
  origin helper (9, 2, 9) in `big_area`) within 12,000 ticks, and no block is left unfinished.
- `QaTakeDown1105GameTests.wellTakenDownLeavesNoWaterAndGivesBackItsCost` (Level I, origin (9, 2, 9) in `big_area`):
  the footprint must lie inside helper x/z 6..21, y 1..17; it pours at least one water source; its list asks exactly
  one water bucket per source; built from exactly its list it finishes as drawn with **0 skipped steps**; taken down,
  no built block stands, no water (source or flowing) is left anywhere in or round it, no fire, and every item paid
  comes back (buckets neither made nor lost).
- The 7 x 7 footprint is pinned by tests that place or measure a Well by coordinates:
  - `PlotGameTests` (all its plot tests: the spot "x 8..14, z 10..16", "4 of 49 over water", the 8-wide zone too
    narrow for 7 plus 2 each side, the margin 2 and 4 west of the spot);
  - `QaWallsRoadsAroundBuildings1101GameTests` (asserts the Well is 7 wide);
  - `QaBuilderFixes1027bGameTests.sameRangeIsTwentyFourCentreToCentreOnEverySide` (7 x 7, odd, centre to centre);
  - `QaRoadGameTests.qaARoadGoesRoundABuildingAndAPlayersBlocks` (a street at z 8 must go round a Well at (10, 2, 6));
  - `B303GameTests` (barrels the test puts inside a Well's footprint at (2, 2, 5));
  - and the Steward tests that check a Well plot at `SPOT` (11, 2, 13), turned a quarter: `StewardPlanningCostGameTests`,
    `QaPactsBoardStewardGameTests`, `QaStewardPlotFitGameTests`, `QaStewardPlotsGameTests`, `QaStewardSameRangeGameTests`
    and the other `QaSteward*`/`Steward*` classes that name `well`.
- By id only (the drawing doesn't matter, the id and its tier do): `VillageHallGameTests` (beauty +2, then +3 with Level
  II on the same spot), `ClassGameTests.beautyNeedCountsDecorationsNearHome` (a well is beauty 2) and
  `ClassGameTests.buildingNeedLooksForAFinishedBuildInTheVillage` (`well_2` is tier II, not III),
  `DroughtGameTests` "droughtWell" (a finished Well removes the mood reason), `QaDisastersGameTests`,
  `SignpostGameTests`/`QaSignpostGameTests` (a Well named on a signpost board), `BlueprintUnlocksGameTests` (locked,
  market hint), `WallGameTests` (a Well isn't a wall piece), `VillageTrainGameTests`, `QaBuilderSwitchesGameTests`.
- The owner's rules (`check.py`) and STYLE.md's checks: `rebuild.py --candidate` prints no CHECK line, and `holes.py`
  passes.

## Free
Everything not listed above: what holds the water and how much of it beyond one reachable source, everything round
the water, what Level II adds (as long as it keeps 60 % of Level I and stays in the 7 x 7, 10-tall plot), materials,
shapes, lighting, roofs and style.
