# Gazebo: the brief

Family id(s): `gazebo` (never renamed; one level, no `gazebo_2`)   Variants: village (none drawn: a Steward zone's
style is applied to the drawing automatically by `BlueprintStyles`), modded (none: `modded_library.py` lists it in
`EXTRA_DECORATIONS` and never dresses it), looks (none)

## What it is for
A village decoration: a sheltered place to sit outdoors in a park or garden. A finished Gazebo within a Village
Hall's claimed area adds 3 beauty (and so 3 % wellbeing, up to the 10 % cap) to the village, and adds 3 to the beauty
need of every villager whose bed is within 16 blocks of it. The Steward builds one by himself in a village's Gardens
zone when a village of five or more is still ugly, as the last of his four garden wishes (after the Well, the Park
Bench and the Fountain). The player gets it as a found blueprint (village chest loot), from a market-day trader, or by
a village level-up, which unlocks it at the Blueprint Table for the whole village. It has no job and no residents;
nothing in the code reads anything inside it.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `gazebo` | 3 beauty points (`hall/Decorations.POINTS`); counts as a Decorations-kind build on the village map (`hall/VillageMaps`, by id); its own "built" advancement (`advancement/built/gazebo.json`, by id, title from `blueprint.aliveworkplace.gazebo`); in the market-day trader pool (`hall/MarketDays`, all `DECORATIONS`); never rebuilt by the Grand Rebuild (`legend/GrandRebuild.never`, all decorations) | Starts **locked** at the Table (not in `blueprint_unlocks/`; hint `screen.aliveworkplace.table.hint.market`); unlocked by a found Gazebo blueprint used on the Table, a trader's sale, or a village level-up. Found in five village chest loot tables (`village_carpenters_workshop`, `village_storehouse`, `village_kitchen`, `village_builders_workshop`, `village_inn`). Steward rule `steward_rules/gazebo.json`: beauty < 3, villagers >= 5, none built yet, zone `gardens`, priority 31, cooldown 1 day, at most one. No upgrade exists or is asked for. |

## Must have
- Job block(s): none. No code looks for a workstation in it. Vanilla villagers claim any workstation block (a
  lectern, a barrel, a smoker, a composter, a cauldron...) as a job site, and a bell as a meeting point, so one drawn
  in would be taken; keep the decorations' rule (26.14) of no block a villager takes a job or a meeting from. (The
  automatic check of that rule, `MoreDecorationsGameTests`, covers only `MORE_DECORATIONS`, so nothing catches a slip
  here except review.)
- Beds and residents: none. No code counts beds here. (A bed drawn in would be claimed as a home.)
- Storage: none read. A container drawn in is free but stays behind on a take-down (builders never break containers).
- Furniture kinds a job or test needs: none (there is no `blueprint_furniture/gazebo.json`, and none is needed).
  Villagers only sit on blocks in the `#aliveworkplace:villager_seats` tag (furniture-mod seats, `FurnitureSeats`);
  vanilla stairs are not seats to the code, so any seating is for looks only.
- Anything else the code or a test reads: nothing. No water, bell, sign, lectern, door or light level is read for
  this family.

## Fits
- Plot: hard limits from the test: a footprint of **at most 13 x 13** (x by z) and a height of **at most 16**
  (`BuilderGameTests.buildsGazebo` builds it at helper (9, 2, 9) in the 22 x 18 x 22 `big_area`; the stock barrels
  stand at helper z = 4, outside it). The Steward must still find room for it in a Gardens zone with a 2-block margin
  round it and other builds (`city/Plots.java`, `MARGIN = 2`, reads the blueprint's size), so every block of width
  costs plots in small villages; the old one was 7 x 7 x 8. y = 0 sits on the clicked block; the front is the z = 0
  side.
- Declared size: `StarterBlueprints.GAZEBO` is `new Vec3i(7, 8, 7)` today. If the new size differs, the ship step
  changes that `Vec3i` to match (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`), and
  `generate.py`'s `("gazebo", gazebo)` entry (line 146) stops overwriting the new `.nbt`. Update the size line of
  `docs/guide/gazebo-build.md` too.
- Materials budget (test-enforced): the whole material list, as stacks, must fit **108 stacks** (four barrels of 27
  in `BuilderGameTests.buildStarter`; it asserts `stock.size() <= 108`).
- Front and way in: the Steward turns the plot so the z = 0 side faces the road, or else the hall
  (`PlotGameTests.plotOnFlatGroundFacesTheRoad`). With no door, a lane to it starts one block in front of the middle
  of the z = 0 edge (`Paths.doorstep`'s fallback), so that spot should be ground a villager can walk onto, and the
  way in should be from the front. (A door drawn in would move the lane's start to that door.)
- Neighbours: placed freely as a decoration in the Gardens zone; walls and roads go round its footprint. No test
  places blocks round it by coordinates.

## Rules tests check
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: the `.nbt`'s size equals
  `StarterBlueprints.GAZEBO`'s size, and every block in it is one a builder can place (none classified `SKIP` by
  `MaterialRules.classify`: no flowing liquids, no technical blocks).
- `BuilderGameTests.buildsGazebo` (batch `decorations`, `big_area`, origin (9, 2, 9), no rotation): a builder with
  exactly the plan's materials in four barrels builds it from bare ground within **12,000 ticks**, with the site
  closed and **no block left unfinished** (`assertBuilt`: `plan.unfinished` empty). So every block must be one the
  builder can place and keep in place (anything that needs support it doesn't have, or that pops off, fails).
- By id only (the drawing doesn't matter, the id does):
  - `StewardCivicRulesGameTests.anUglyVillageOfFiveGetsAWellABenchAFountainAndAGazeboInThatOrder` (wished for in
    `gardens`, fourth of four, only from five villagers and beauty under 3);
  - `VillageHallGameTests.decorationsMakeAVillagePrettier` (a Gazebo finished 40 blocks beyond the hall's reach adds
    nothing);
  - `CountryWorkplacesGameTests.aBandstandAddsThreeToTheBeauty` (a Bandstand adds 3 "as a Gazebo does": the points, by id);
  - the generic decoration and unlock tests that go through `StarterBlueprints.DECORATIONS` (Table unlocks, the
    market pool), and the name **Gazebo** (lang key `blueprint.aliveworkplace.gazebo`).
- The owner's rules (`check.py`) and STYLE.md's checks: `rebuild.py --candidate` prints no CHECK line, and `holes.py`
  passes.

## Free
Everything not listed above: the shape, roof, seating, planting, lighting, materials, height and footprint within
13 x 13 x 16 (smaller is kinder to small villages), and style.
