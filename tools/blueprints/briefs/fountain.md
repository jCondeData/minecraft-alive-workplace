# Fountain: the brief

Family id(s): `fountain` (never renamed; one level, no `fountain_2`)   Variants: village (none drawn: a Steward zone's
style is applied to the drawing automatically by `BlueprintStyles`), modded (none: `modded_library.py` lists it in
`EXTRA_DECORATIONS` and never dresses it), looks (none)

Not in this family: the fountain inside Manor II's walled garden (`houses.py`, part of `manor_2`..`manor_4`, and the
`FOUNTAIN` note in `holes.py`), the one in the Market Square (`market_square`), and the Wonder's cistern fountain
(`wonders.py`). They share an old Python helper (`decor.fountain_basin`) but are other families; nothing here changes
them.

## What it is for
A village decoration with real water in it, worth more beauty than a Well. A finished Fountain within a Village
Hall's claimed area adds 3 beauty (and so 3 % wellbeing, up to the 10 % cap) to the village, adds 3 to the beauty need
of every villager whose bed is within 16 blocks of its origin, and in a drought takes away every villager's "The wells
are low" mood penalty, as a Well does. The Steward builds one by himself in a village's Gardens zone when a village of
five or more is still ugly. The player gets it as a found blueprint (village chest loot) or from a trader, which
unlocks it at the Blueprint Table for the whole village.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `fountain` | 3 beauty points (`Decorations.POINTS`); ends the drought mood penalty (`disasters/drought.json`, `unless_built`); counts as a Decorations-kind build on the village map (`VillageMaps`, by id); its own "built" advancement (`advancement/built/fountain.json`, by id) | Starts **locked** at the Table (hint `screen.aliveworkplace.table.hint.market`); unlocked by using a found Fountain blueprint on the Table, buying one from any trader, or a village level-up (tier I, so it goes early). Found in five village chest loot tables (`village_carpenters_workshop`, `village_storehouse`, `village_kitchen`, `village_builders_workshop`, `village_inn`). The Steward's rule `steward_rules/fountain.json`: beauty < 3, villagers >= 5, none built yet, zone `gardens`, priority 32 (after the Well and the Park Bench, before the Gazebo), at most one. No upgrade exists or is asked for. |

## Must have
- Job block(s): none. No code looks for a workstation in it. (Vanilla villagers claim any workstation block as a job
  site, so one drawn in would be taken as a job; the decoration tests of 26.14 hold their decorations to "no block a
  villager takes a job or a meeting from", and the Fountain was the model for them.)
- Beds and residents: none. No code counts beds here.
- Storage: none read. No job or test reads a container in it (a container drawn in is free, but builders never break
  containers on a take-down, so it would stay behind).
- Furniture kinds a job or test needs: none (there is no `blueprint_furniture/fountain.json`, and none is needed).
- **Water:** at least one **water source block** in the drawing.
  - `QaTakeDown1105GameTests.fountainTakenDownLeavesNoWaterAndGivesBackItsCost` refuses a Fountain that "pours no
    liquid".
  - **Every water cell must be a source.** Flowing water in the drawing is a block the builder can't place
    (`MaterialRules.classify` gives it `SKIP`), and `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`
    fails on it. A "spilling" or cascading look has to come from still sources, not from drawn flowing water.
  - **Every source must be held in so no water ever flows out of its cell**, in the world, after it is poured: the
    take-down test scans the whole test area (helper x/z 0..21, y 1..17) and fails on any water block, source or
    flowing, left in or round the footprint, and the build must finish "as drawn" (a cell the drawing keeps as air
    that water runs into is not as drawn). Raised basins are fine (the old one had one) as long as nothing runs over
    their edge.
  - Each source costs one water bucket on the material list, and the list must ask for exactly one bucket per source
    (the builder pours them in the decoration pass and scoops each one back into an empty bucket on a take-down).
    A waterlogged block is built dry and costs nothing extra, except sea pickles and living coral, which are built
    waterlogged and cost a bucket each (and count as sources).
- Anything else the code or a test reads: nothing. No bell, sign, lectern, door or cauldron is read for this family.
  (A Well Keeper fills buckets from any water source with a non-water block above it within his reach, so a Fountain
  can serve him too; no code or test asks it to.)

## Fits
- Plot: hard limits from the tests: a footprint of **at most 13 x 13** (x by z) and a height of **at most 16**
  (origin helper (9, 2, 9) in the 22 x 18 x 22 `big_area`; the take-down test asserts the whole plan lies inside helper
  x/z 6..21, y 1..17). The Steward must still find room for it in a Gardens zone with a 2-block margin round it and
  other builds (`city/Plots.java`, `MARGIN = 2`, reads the blueprint's size), so every block of width costs plots in small villages; the
  old one was 7 x 7 x 5. y = 0 sits on the clicked block; the front is the z = 0 side.
- Declared size: `StarterBlueprints.FOUNTAIN` is `new Vec3i(7, 5, 7)` today. If the new size differs, the ship step
  changes that `Vec3i` to match (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`), and
  `generate.py`'s `("fountain", fountain)` entry stops overwriting the new `.nbt`.
- Materials budget (test-enforced): the whole material list, as stacks, must fit **108 stacks** (four barrels in
  `BuilderGameTests.buildStarter`, four chests in the take-down test). **Water buckets don't stack: every source is a
  whole stack of its own.**
- Front and way in: the Steward turns the plot so the z = 0 side faces the road, or else the hall
  (`PlotGameTests.plotOnFlatGroundFacesTheRoad`). There is no door: a lane to it starts one block in front of the
  middle of the z = 0 edge (`Paths.doorstep`'s fallback), so that spot should be ground a villager can walk onto from
  the front. (A door drawn in would move the lane's start to that door.)
- Neighbours: placed freely as a decoration in the Gardens zone; walls and roads go round its footprint. No test places
  blocks round it by coordinates (the tests that "finish" a Fountain at a spot only record it by id and origin).

## Rules tests check
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: the `.nbt`'s size equals
  `StarterBlueprints.FOUNTAIN`'s size, and every block in it is one a builder can place (none classified `SKIP`:
  no flowing water, no technical blocks).
- `BuilderGameTests.buildsFountain` (batch `decorations`, `big_area`, origin (9, 2, 9), no rotation): a builder with
  exactly the plan's materials in four barrels builds it from bare ground within **12,000 ticks** (build delay 2),
  with nothing left unfinished.
- `QaTakeDown1105GameTests.fountainTakenDownLeavesNoWaterAndGivesBackItsCost` (`big_area`, origin (9, 2, 9), 18,000
  ticks): the plan lies inside helper x/z 6..21, y 1..17; it pours at least one liquid source; its list asks exactly
  one water bucket per source; built from exactly its list it finishes as drawn with **0 skipped steps**, the builder
  then holding one bucket (full or empty) per source; handed back sneaking it is taken down: no built block stands
  (containers excepted), no water anywhere in the area, no fire, the builder's bag empty, and every item paid comes
  back (buckets neither made nor lost).
- By id only (the drawing doesn't matter, the id does):
  - `VillageHallGameTests.decorationsMakeAVillagePrettier` (a Fountain and a Park Bench add beauty 4);
  - `ClassGameTests.beautyNeedCountsDecorationsNearHome` (a fountain is 3, counted within 16 blocks of the bed) and
    `ClassGameTests.buildingNeedLooksForAFinishedBuildInTheVillage` (one outside the village's reach doesn't count),
    and the class soak test that records ten Fountains beside houses;
  - `QaDisastersGameTests.aFountainOrWellIITakesTheMoodAwayAFarWellDoesNot` (a finished Fountain removes "The wells
    are low");
  - `StewardCivicRulesGameTests.anUglyVillageOfFiveGetsAWellABenchAFountainAndAGazeboInThatOrder` (wished for in
    `gardens`, third of four, only from five villagers and beauty under 3);
  - the Table unlock tests: `BlueprintUnlocksGameTests` (locked at first, a trader's sale unlocks it),
    `QaBlueprintUnlocksGameTests`, `QaBlueprintUnlocksBugTests`, `QaBuilderFixes1027GameTests` (a found Fountain in
    either hand unlocks it once) and `QaBlueprintLibraryVerify1108bGameTests` (the line "The village's library has a
    new blueprint: Fountain", so its name stays **Fountain**, lang key `blueprint.aliveworkplace.fountain`; a level-up
    unlocks it among the first, as a tier-I build by id);
  - `QaBuilderMindChange1102GameTests` and `QaTakeDownGameTests` quote "Canceled the build of Fountain." from the
    guide (the name, not the drawing).
- The owner's rules (`check.py`) and STYLE.md's checks: `rebuild.py --candidate` prints no CHECK line, and `holes.py`
  passes.

## Free
Everything not listed above: how many sources beyond one and where, what holds them (as long as none can flow out),
everything round the water, seating, lighting, planting, materials, shapes, height and footprint within 13 x 13 x 16,
and style.
