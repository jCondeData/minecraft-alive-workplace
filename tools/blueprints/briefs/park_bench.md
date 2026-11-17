# Park Bench: the brief

Family id(s): `park_bench` (never renamed; one level, no `park_bench_2`)   Variants: village (none drawn: a Steward
zone's style is applied to the drawing automatically by `BlueprintStyles`), modded (none: `modded_library.py` lists it
in `EXTRA_DECORATIONS` and never dresses it), looks (none)

Not in this family: the benches drawn inside other builds (the Arena's stands, `arena.py`'s `benches`; `kit.py`'s
`bench_seat`; the Pokemon nursery's `nursery_bench`). The old Python helper `decor.bench` is used only by this family.

## What it is for
The smallest village decoration: somewhere in the village to sit, which a player (or the Steward) has a Builder put up
to make the village a little prettier. A finished Park Bench within a Village Hall's claimed area adds 1 beauty (and so
wellbeing) to the village, adds 1 to the beauty need of every villager whose bed is within 16 blocks of its origin, and
counts as one finished building toward the village's rank (Hamlet to Village needs 5). The Steward builds one by himself
in a village's Gardens zone when a village of five or more is still ugly. Nobody lives or works in it; it hands out no
job and no meeting place. Its name is "Park Bench", so the finished build must be recognizably a place to sit.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `park_bench` | 1 beauty point (`hall/Decorations.POINTS`); counts as a building for `VillageRanks` (by id); **no** mark on the village map (`VillageMaps`: `park_bench` -> `null`, too small to mark); its own hidden "built" advancement (`advancement/built/park_bench.json`, by id, title from `blueprint.aliveworkplace.park_bench`) | In `StarterBlueprints.DECORATIONS` (so: the Table, the creative tab, the market-day traders' pool, excluded from the Grand Rebuild and from the Master Architect's rebuilds). Starts **locked** at the Table with the hint `screen.aliveworkplace.table.hint.market`. Found as a blueprint in five village chest loot tables (`village_carpenters_workshop`, `village_storehouse`, `village_kitchen`, `village_builders_workshop`, `village_inn`). The Steward's rule `steward_rules/park_bench.json`: beauty < 3, villagers >= 5, none built yet, zone `gardens`, priority 33 (after the Well, before the Fountain and the Gazebo), at most one. No upgrade exists or is asked for. |

## Must have
- Job block(s): **none.** No code looks for a workstation in it. Don't draw in any block a villager takes a job, a bed
  or a meeting place from (job blocks, beds, a bell, a beehive, a barrel, a composter, a lectern...): vanilla villagers
  would claim it, and the decorations are held to "no point of interest" (`MoreDecorationsGameTests.moreDecorationsHoldNoJobBlock`
  checks the 26.14 set; this family follows the same rule).
- Beds and residents: none. No code counts beds here.
- Storage: none. No job or test reads a container in it (and builders never break containers on a take-down, so a
  drawn one would stay behind).
- Furniture kinds a job or test needs: none (there is no `blueprint_furniture/park_bench.json`, and none is needed; the
  37.6 furniture-mod seats are read only in homes, `FurnitureSeats.homeBeauty`).
- Anything else the code or a test reads: nothing. No water, light, sign, bell, lectern or door is read for this family.
  The only functional requirement beyond the rules below is the purpose: a seat a villager or player could sit on.

## Every block must be one a survival builder can place
- No block a builder can't place (`MaterialRules.classify` == SKIP: flowing water, fire, technical blocks):
  `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`.
- Built from **exactly** its Material List, it must finish **as drawn** (`BuilderGameTests.assertBuilt`: no unfinished
  block). So nothing may change by itself after it is placed: no dirt beside grass, no saplings or crops that grow,
  nothing that needs a block a later pass puts down to survive. Plants stand in their own soil (the owner's rule).
- Stocking: the test puts the whole list into 4 barrels of 27 slots, so the material list must be **at most 108 stacks**
  (`BuilderGameTests.buildStarter`, "test needs more barrels").

## Fits
- Plot: hard limits from the tests: a footprint of at most **13 x 13** (x by z) and at most **16** tall (origin helper
  (9, 2, 9) in the 22 x 18 x 22 `big_area`). The real limit is tighter: the Steward must find room for it in a Gardens
  zone with a 2-block margin round it (`city/Plots.java`, `MARGIN = 2`, reads the blueprint's size), and
  `PlotGameTests.noSameHouseMirroredTheSameWayNearby` needs it to find a plot in a small test village where the Well's
  7 x 7 just fits; it is the village's cheapest, smallest decoration (1 beauty, the second thing an ugly village asks
  for), so keep the footprint **no bigger than 7 x 7** and small enough to sit beside a road. The old one was 5 x 3 x 2.
- Declared size: `StarterBlueprints.PARK_BENCH` is `new Vec3i(5, 3, 2)` today. If the new size differs, the ship step
  changes that `Vec3i` to match (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`), and
  takes `("park_bench", park_bench)` out of `generate.py`'s loop (and adds it to `REBUILT`) so the old Python drawing
  stops overwriting the new `.nbt`.
- y = 0 sits on the clicked block; the front is the z = 0 side.
- Front and way in: the Steward turns the plot so the z = 0 side faces the road, or else the hall
  (`PlotGameTests.plotOnFlatGroundFacesTheRoad`). So the seat should face the front (z = 0) and be reachable from there.
  There is no door: a lane to it starts one block in front of the middle of the z = 0 edge (`Paths.doorstep`'s
  fallback), so that spot should be ground a villager can walk onto from the front.
- Neighbours: placed freely as a decoration in the Gardens zone (or anywhere by a player); walls and roads go round its
  footprint. Tests that "finish" Park Benches by id record up to five of them 2 blocks apart (origins only, no blocks
  placed), so nothing depends on its size there.

## Rules tests check
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: the `.nbt` exists, its size equals
  `StarterBlueprints.PARK_BENCH`'s, and no block in it is SKIP.
- `BuilderGameTests.buildsParkBench` (batch `decorations`, `big_area`, origin (9, 2, 9), no rotation): a builder with
  exactly the plan's materials in 4 barrels builds it from bare ground within **6,000 ticks** (build delay 2), every
  block as drawn. This is half the Fountain's budget: keep the block count modest (the old one had 30 cells).
- By id only (the drawing doesn't matter, the id and name do):
  - `VillageHallGameTests.decorationsMakeAVillagePrettier` (a Fountain and a Park Bench add beauty 4: the bench is 1);
  - `StewardCivicRulesGameTests.beautyBelowReadsTheDecorationsPoints` (a bench adds 1 point) and
    `anUglyVillageOfFiveGetsAWellABenchAFountainAndAGazeboInThatOrder` (wished for in `gardens`, second of four, only
    from five villagers and beauty under 3; once built it's not wished again);
  - `PlotGameTests.noSameHouseMirroredTheSameWayNearby` (when the Well can't be placed, the search takes the Park Bench
    and must find a plot for it);
  - `VillageHallGameTests` (rank test: five finished benches count as five buildings), `QaQuestbookClaims1116eGameTests`,
    `QaHiredRank1107cGameTests`, `QaHiredRank1107dBugTests`, `BuiltAdvancementGameTests` (benches as finished
    buildings for ranks and advancements);
  - `MasterArchitectGameTests.aRebuildPicksAnUpgrade` (a builder busy on a Park Bench) and
    `QaMasterArchitectGameTests.decorationsOnlyOrNoBuilderHandsNothing` (a decoration is never rebuilt);
  - the name stays **Park Bench** (lang key `blueprint.aliveworkplace.park_bench`, American English).
- The owner's rules (`check.py`) and STYLE.md's checks: `rebuild.py --candidate` prints no CHECK line, and `holes.py`
  passes.

## Free
Everything not listed above: what the seat is made of and how it is shaped, how many seats, everything round it
(planting, a lamp or none, paving, a backdrop), materials, footprint and height within 7 x 7 and the 6,000-tick budget,
and style.
