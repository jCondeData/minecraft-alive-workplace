# Burgher's Townhouse: the brief

Family id(s): `townhouse`, `townhouse_2`, `townhouse_3`, `townhouse_4` (never renamed; display names "Burgher's
Townhouse", "... II", "... III", "... IV" in `en_us.json`)
Variants: village (none drawn: a village's style is applied to the drawing automatically by `BlueprintStyles`), modded
(none to draw: `modded_library.py` makes `structure/modded/townhouse*.nbt` from the vanilla drawing), looks (none).

## What it is for
The Burgher class's home: a **grade II** home for a well-off household (`homes/townhouse.json` gives it grade II, so its
upgrades are grade III; `HomeGrades`). A Burgher household needs a grade II home (`classes/burgher.json`); a Noble living
in one still wants grade III. It is **sold by villagers only**, never listed or handed out by the Blueprint Table
(`StarterBlueprints.SOLD_ONLY`): a Builder sells Level I for 14 emeralds (`ModTrades.TOWNHOUSE_PRICE`) once the village
round his nearest hall has a household of Artisans or higher; a Burgher-led market's traders also sell it
(`burgher.json`, `market_traders`); each upgrade is sold by the builder who finished the tier below (`UpgradeOffers`).
The Steward builds Level I himself in a `homes` zone when **two or more households want a grade II home**
(`steward_rules/homes_class_townhouse.json`, priority 46, village rank or higher), and upgrades a home for a class that
wants a better grade (`homes_class.json`) or when beds are short and the next tier adds beds (`homes_upgrade.json`).
Each bed is a home: a villager whose bed is in it lives in this house at its grade (`Homes.of`), and the Hall lists
"Lives in the Burgher's Townhouse". Its rooms are furnished (37.4): with a furniture mod each spot becomes that mod's
piece, without one the vanilla stand-in. Each level is an upgrade on the same spot and origin.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `townhouse` | A grade II home with **at least 2 beds** (a couple's household), every furnished-room kind including the couch, a chimney | Sold by a builder once the village has Artisans (14 emeralds) or by Burgher market traders; Steward rule `homes_class_townhouse` |
| II | `townhouse_2` | A grade III home with **more beds than I** (so `StewardConditions.addsBeds` is true and the Steward's `homes_upgrade` picks it when beds are short) | Upgrade of I (`BlueprintUpgrades`: id + `_2`); sold by the builder who finished I |
| III | `townhouse_3` | Grade III; at least II's size; no bed count is tested (do not drop a bed II has: a resident would lose their home) | Upgrade of II; sold by the builder who finished II |
| IV | `townhouse_4` | A **cellar exactly 4 blocks deep** under the house (`StarterBlueprints.Entry` basement 4), reached from inside | Upgrade of III; a builder who finished a III sells it (`BasementGameTests.basementsLoadUnderTheGround`) |

## Must have (each level)
- **Beds and residents.** Count is bed heads (`StewardConditions.beds`). I: **at least 2**
  (`QaGranderHomes1107bGameTests.aTownhouseBuiltAcrossASaveAndReloadEndsFinishedAndSellsItsII` asserts >= 2); II: more
  than I. Every bed lies inside the build's footprint, so it gives the house's grade turned and mirrored any way
  (I grade II, II and III grade III: `QaGranderHomes1107GameTests.everyBedOfTheTownhouseGivesItsGrade`,
  `HomesSpecGameTests.everyBedOfEveryStarterHouseIsAtHomeTurnedAnyWay`). **Every bed head of I, II and III is at most
  17 blocks above y = 0** (that test places the house on y = 2 of the 20-tall `huge_area` and asserts each head is inside
  the area). Every bed's head is against a wall or a window (`FurnishedRoomsGameTests.everyBedHasItsHeadToAWall`), and each
  bed reachable from the front door (check.py's reach check).
- **Job blocks.** None is required by code or test (the Burgher's jobs work elsewhere). Any job block or container
  drawn is free, but see "Kept cells".
- **Storage.** No job reads the house's containers. Every indoor chest stands against a wall, a solid block, a bed's
  foot or its other half (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`); double chests stay whole
  turned and mirrored (`QaMirroredBuildsGameTests`).
- **A chimney in Level I**: at least one campfire standing on a stack of full solid blocks that stays solid in every
  style (`StyleGameTests.chimneysStayWholeInEveryStyle` names `townhouse`).
- **Glass in Level I**: **at least 6** glass panes or iron bars (any `IronBarsBlock`), every one, and every fence and wall,
  standing exactly as the game joins it once built, and none ending a run against open air
  (`QaWindowsAndWards1028GameTests.qaABuiltTownhousesGlassJoinsAfterAReloadMidStructure`; all levels:
  `QaWindowsAndWards1028BugTests.qaNoShippedGlassEndsInTheAirAsTheGameJoinsIt`).
- **Styles.** Levels I and II each use at least one block a Table style swaps, so the Table offers it at least one style
  (`QaGranderHomes1107bGameTests.everyStyledTierLeadsToTheNextTierInItsOwnStyle`).
- **Furniture spots** (`blueprint_furniture/<id>.json`, written by the recipe's `furniture.json` through `rebuild.py`):
  - **Every level, I to IV**, has at least one spot of each kind **bed, chair, table, nightstand, lamp, couch, shelf,
    crockery, clock, picture, blinds** (`FurnishedHomesGameTests.everyHomeHasItsFurnishedRooms`: the full set, couch
    included, from Level I on; unlike the Stone House, I is not let off the couch).
  - Every level has a table or chair spot, and **every table and chair spot takes a furniture mod's piece** (Handcrafted,
    Beautify, CobbleFurnies, in either pack order), none left as the vanilla stand-in
    (`compattest QaMultiHomeFurniture1031CompatTests.noTableOrChairOfATownhouseOrInnStaysVanilla`): use the parts
    library's table and chair footprints.
  - Each spot's blocks are exactly the template's blocks there (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`);
    every piece stands on, hangs from or leans against what its kind needs (lamp: floor or ceiling; shelf: floor or wall;
    picture and blinds: wall; the rest: floor) and has its support written
    (`FurnishedRoomsGameTests.everyFurnishedPieceHasWhatItStandsOn`); nothing small stands on air, vanilla or with lower
    modded pieces (`FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece`).
  - **No spot within two blocks of any door**, either side, at either of the door's two heights
    (`FurnishedRoomsGameTests.noFurnitureStandsInADoorsWay`).
  - **Every floor-standing piece indoors is in block light above 7** from the build's own lights
    (`FurnishedRoomsGameTests.everyFurnishedRoomHasALight`), and **no lantern stands on a room's floor**: hung, or on a
    table, post or ledge (`QaFurnishedRooms1031GameTests.noLanternStandsOnARoomsFloor`).
  - Frames and paintings hang right turned and mirrored (`QaTurnedFramesGameTests`); facing blocks turn with the build
    (`QaTurnedModBlocksGameTests`); doors and beds stay whole turned and mirrored (`QaMirroredBuildsGameTests`).
- **Level IV's cellar** (`BasementGameTests.basementsLoadUnderTheGround`, `QaBasementGameTests`, `QaBlueprintTableGameTests.
  everyBasementBuildPreviewsItsWholeDepth`, all of `StarterBlueprints.WITH_BASEMENTS`): drawn at the bottom of the file,
  its floor at file y = 0 and the ground floor at y = 4, exactly 4 deep, the file exactly declared height + 4; at least
  one ladder or stairs and at least one `minecraft:lantern` below ground; a ladder or stair on the cellar's top layer
  (y = -1) with two passable blocks above it, coming up inside the ground floor; no dark spot a monster could stand on;
  walkable from the ground outside down to the cellar floor and back out, with every chest or barrel down there within
  2 blocks across and 1 up or down of a spot reached that way. Its above-ground part is III's (or grows from it).
- **A front door** on the ground floor at the z = 0 side, with ground a villager can stand on outside it
  (`Paths.doorstep` takes the lowest door within the box's bottom four layers and the first walkable spot outside).

## Kept cells (every upgrade)
- Each level has the same origin and front as the one below and is at least its size in x, y and z, and **keeps at
  least 60% of the lower level's non-air blocks identical in the same cells** (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`).
- Every block with block-entity data (chest, barrel, smoker, furnace, lectern, ...; not beds, campfires, signs, banners,
  bells, skulls) in a level stays in the same cell as the same block in every later level: a builder never breaks those
  while clearing (`MaterialRules.isProtected`), so an upgrade that moves one can't finish.

## Fits
- Plot (from the tests that place each level by coordinates; the ship step moves coordinates, not areas):
  - Level I: **at most 15 wide (x) x 16 deep (z)**: `QaRefreshFurnishedGameTests.aFurnishedOldTownhouseMirroredIsRefreshedToAFreshBuild`
    places it at (15, 2, 15) of the 30 x 30 `huge_area` mirrored left to right, so it runs from z = 15 back toward z = 0.
    Height: up to 28 fits the `tall_area` (`BuilderGameTests.buildsTownhouse` at (9, 2, 8)); the `huge_area` tests
    (glass, refresh) are 20 tall, so a house over 18 pokes above them (it does today, and they pass).
  - Level II: at most **21 x 22** (`BuilderGameTests.buildsTownhouseII` at (9, 2, 8) in the 30 x 30 x 30 `tall_area`).
  - Level III: at most **21 x 22 x 28** (`BuilderGameTests.buildsTownhouseIII` at (9, 2, 8) in `tall_area`), and turned a
    quarter it still fits 30 x 30 (`everyBedOfTheTownhouseGivesItsGrade` places III's box turned and mirrored).
  - Level IV: III above the ground plus the 4-deep cellar.
- Declared sizes: `StarterBlueprints.TOWNHOUSE` (`11, 20, 12`), `_2` (`11, 20, 18`), `_3` (`15, 26, 18`), `_4`
  (`15, 26, 18`, basement 4) today; if a size changes the ship step changes those `Vec3i`s
  (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`, `BasementGameTests.basementsLoadUnderTheGround`).
- Material budget: Level I's, II's and III's whole lists each fit the four barrels `buildStarter` stocks (**at most 108
  stacks**, each item rounded up to whole stacks); every block one a builder can place.
- Build time: I within 60,000 ticks (`buildsTownhouse`, and the glass test), II within 70,000 (`buildsTownhouseII`),
  III within 90,000 (`buildsTownhouseIII`); I across a save and reload from exactly its materials within 70,000.
- Front and way in: the z = 0 side faces the player who places it and the road of a Steward plot; the front door goes
  there on the ground floor; every upgrade keeps that front.
- Neighbours: a home in a Steward `homes` zone (`VillageMaps` counts `townhouse` as HOMES); the Steward finds it a plot
  and keeps ground clear round it. Nothing is built against it.

## Rules tests check
Built whole (every block right, 0 skipped, supplies exact): `BuilderGameTests.buildsTownhouse`, `buildsTownhouseII`,
`buildsTownhouseIII`; `QaGranderHomes1107bGameTests.aTownhouseBuiltAcrossASaveAndReloadEndsFinishedAndSellsItsII` (a
reload mid-STRUCTURE, no materials left over, >= 2 beds each giving grade II, the builder then sells II and not III);
`QaWindowsAndWards1028GameTests.qaABuiltTownhousesGlassJoinsAfterAReloadMidStructure` (I at (8, 2, 8) of `huge_area`,
>= 6 panes or bars, all joined, none ending in air); the basement tests (IV).

Beds and grades: `QaGranderHomes1107GameTests.everyBedOfTheTownhouseGivesItsGrade` (I, II, III turned and mirrored, every
bed head inside the area, grades 2, 3, 3, garden beauty 0, the home named for its tier);
`HomesSpecGameTests.everyBedOfEveryStarterHouseIsAtHomeTurnedAnyWay`.

Refresh (36.21): a Townhouse built from an older drawing in `blueprint_history/townhouse*/`, refreshed through the
Hall's Repairs page, ends equal to a fresh build of the new drawing (chests, slept-in beds, job blocks and players' blocks
aside), nothing floating or doubled: `QaReleaseRefresh1028GameTests` (`qaReleaseRefresh_townhouse`, `_2`, `_3`, from
`cd1557d8c`, at (7, 2, 2) of `huge_area`), `QaRefreshFurnishedGameTests.aFurnishedOldTownhouseMirroredIsRefreshedToAFreshBuild`
(from `42864073a`, furnished by that day's spots file, mirrored), `B648GameTests.b648AnOldTownhouseIsOfferedTheNewRailings`
(the `cd1557d8c` Townhouse is offered a refresh that changes >= 3 pieces). Shipping a new drawing adds today's drawing to
`blueprint_history/` so standing Townhouses are offered the refresh (`QaRefreshHistory1028GameTests.qaEveryShippedOlderDrawingIsOfferedARefresh`).

Generic (every starter, furnished home or basement; see "Must have"): `FurnishedHomesGameTests`, `FurnitureSpotGameTests`,
`FurnishedRoomsGameTests`, `QaFurnishedRooms1031GameTests`, `FloatingFurnishingsGameTests`, `QaMirroredBuildsGameTests`,
`QaTurnedFramesGameTests`, `QaTurnedModBlocksGameTests`, `StyleGameTests.chimneysStayWholeInEveryStyle`,
`QaWindowsAndWards1028BugTests`, `BasementGameTests`, `QaBasementGameTests`, `QaBlueprintTableGameTests`,
`ModdedLibraryGameTests`, `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` and
`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`, compat `QaMultiHomeFurniture1031CompatTests`.

By id only (the drawing doesn't matter beyond the bed rules above): `HomeGradeGameTests`, `QaHomeGradeFilesLaneC1107GameTests`,
`QaGranderHomesGameTests`, `QaGranderHomes1107GameTests` (sales, unlocks, chat lines), `QaGranderHomes1107BugTests`,
`QaBuilderFixes1107bGameTests`, `ManorGameTests`, `BlueprintUnlocksGameTests`, `StyleGameTests`, `ClassMigrationGameTests`.

The owner's rules (`check.py`: posts at fence corners, lamps on tables or posts, crockery only by a stove, furnace,
smoker or counter or on a table with a chair; doorways, headroom, every bed and room reachable), STYLE.md's checks and
`holes.py`: `rebuild.py --candidate` prints no CHECK line for any level, and `holes.py` passes.

## Free
Everything not listed above: shape, plan, rooms, roofs, materials, colors, style, number of storeys, whether it has a
shop, a garden, a tower or a study, where the furniture, chests and any job blocks stand (within the rules above), how
II adds its beds and what III adds, what the cellar holds, lighting.
