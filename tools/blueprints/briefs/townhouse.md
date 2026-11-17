# Burgher's Townhouse: the brief

Family id(s): `townhouse`, `townhouse_2`, `townhouse_3`, `townhouse_4` (never renamed; names "Burgher's Townhouse",
"... II", "... III", "... IV" in `en_us.json`)
Variants: village (none drawn: the Table's style buttons and `BlueprintStyles` restyle the drawing automatically; I
and II must each take **at least one style**, `QaGranderHomes1107bGameTests.everyStyledTierLeadsToTheNextTierInItsOwnStyle`),
modded (none to draw: `modded_library.py` makes `structure/modded/townhouse*.nbt` from the vanilla drawing), looks (none).

## What it is for
The Burghers' home: a **grade II home** (`homes/townhouse.json` gives the base blueprint grade 2, so I is grade II and
II, III and IV are grade III, `HomeGrades`). Villagers of a class that wants a grander home (Burghers want II, Nobles
III) are content once their bed is in it; the hall's "What next?" tip for them goes away. It is **sold only by
Builders**, never by the Blueprint Table (`StarterBlueprints.SOLD_ONLY`): a builder sells Townhouse I for 14 emeralds
once his village has a household of Artisans or higher (`ModTrades.builderHomes`), each upgrade comes from the builder
who finished the tier below (`UpgradeOffers`), and the Burghers' market traders can bring it on market days
(`classes/burgher.json`). The Steward builds one by himself in a `homes` zone when **two households** want a grade II
home in a Village or bigger (`steward_rules/homes_class_townhouse.json`, priority 46), and his `homes_class` rule
upgrades a Townhouse I to II for a household that wants grade III. On the village map it is a home (`VillageMaps`).
Every bed in it is a home (`Homes.of`: the finished build a villager's bed is inside). Its rooms are furnished (37.4):
with a furniture mod each spot becomes that mod's piece, without one the vanilla stand-in. Each level is an upgrade on
the same spot and origin.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `townhouse` | A grade II home for the households that asked for one; every furnished-room kind | Sold by a builder (14 emeralds) once the village has Artisans or higher; Steward rule `homes_class_townhouse`; market traders |
| II | `townhouse_2` | Grade III (the grade comes from the tier, not from anything drawn) | Upgrade of I (`BlueprintUpgrades`: id + `_2`); sold once, by the builder who finished I |
| III | `townhouse_3` | Grade III still (III adds no grade: 34.15); something worth the upgrade | Upgrade of II; sold by the builder who finished II |
| IV | `townhouse_4` | A **cellar exactly 4 blocks deep** under III, reached from inside | Upgrade of III; sold by a builder who finished a III (`BasementGameTests.basementsLoadUnderTheGround`) |

## Must have (each level)
- **Beds and residents.** At least **one bed in every level**, and every bed's head inside that level's box
  (`QaGranderHomes1107GameTests.everyBedOfTheTownhouseGivesItsGrade` places I, II and III turned 90 and mirrored, and
  each bed head must give grade 2, 3, 3). No code counts more than that, but the Steward builds it for **two households**
  wanting grade II, so I has to house at least two households (a couple counts as one household at the lower of their
  two homes, `QaGranderHomesGameTests`). Today it's 3 beds in I and 4 in II-IV. An upgrade never removes a bed.
- **Every bed can be walked to from the front door** (a home is no use to a villager who can't get to its bed); every
  floor with a bed is reached from inside.
- **Job blocks.** None is required: no code or test reads a job block in the Townhouse. Every block with block-entity
  data (chest, barrel, smoker, ...) stays in its cell in every later level (see "Kept cells").
- **Storage.** None is read by any job. Every chest stands against a wall, a solid block, a bed's foot or its other
  half, never alone in a room (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`).
- **Furniture spots** (`blueprint_furniture/<id>.json`, written by the recipe's `furniture.json` through `rebuild.py`):
  - **Every level, I to IV**, has at least one spot of each kind **bed, chair, table, nightstand, lamp, couch, shelf,
    crockery, clock, picture, blinds** (`FurnishedHomesGameTests.everyHomeHasItsFurnishedRooms`, the FULL set).
  - Every level has at least one table or chair spot, and **every table and chair spot is one the pack's furniture mods
    fill** (Handcrafted and Beautify, in either order with CobbleFurnies: a spot shape and wood their pieces resolve;
    across Townhouse I-IV and Inn I-III more than 10 such spots) (`QaMultiHomeFurniture1031CompatTests.noTableOrChairOfATownhouseOrInnStaysVanilla`).
  - Without a furniture mod every level plans as its vanilla build (`FurnishedHomesGameTests.withoutFurnitureModsEveryHomeIsVanilla`).
  - Each spot's blocks are exactly the template's blocks there (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`);
    nothing small stands on air (`FloatingFurnishingsGameTests`); no standing lantern on a furnished room's floor
    (`QaFurnishedRooms1031GameTests.noLanternStandsOnARoomsFloor`: on a table, shelf, counter or ledge, or hung).
- **A chimney: at least one campfire standing on a column of full solid blocks in Townhouse I**, and that column stays
  solid in every style (`StyleGameTests.chimneysStayWholeInEveryStyle` asserts "townhouse" has one; a campfire on a
  floor or slab doesn't count).
- **Glass: at least 6 panes or iron bars in Townhouse I**, and in the built house every pane, bar, fence and wall is
  joined exactly as the game joins it and no glass run ends against open air
  (`QaWindowsAndWards1028GameTests.qaABuiltTownhousesGlassJoinsAfterAReloadMidStructure`). In every level no pane or bar
  is joined on one side only with air on the other (`B648GameTests`, the static check of every shipped structure;
  `check.py`'s `open_panes`).
- **Level IV's cellar** (`BasementGameTests.basementsLoadUnderTheGround`, `QaBasementGameTests`, all of
  `StarterBlueprints.WITH_BASEMENTS`): drawn at the bottom of the file, its floor at file y = 0 and the ground floor at
  y = 4, exactly 4 deep (the file is III's height + 4); at least one ladder or stairs and at least one
  `minecraft:lantern` below ground; a ladder or stair on the cellar's top layer climbs out through the ground floor
  (room at y 0 and 1 above it); no dark floor cell (block light above 0 on every standable cell); walkable from the
  ground outside down to the cellar floor and back, and every chest or barrel down there within reach of a walked spot.
  Its above-ground part is III's.
- **A front door** on the ground floor at the z = 0 side.

## Kept cells (every upgrade)
- Each level has the same origin and front as the one below and is at least its size in x, y and z
  (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`).
- **At least 60 % of the level below's non-air blocks stay the same block in the same cell** (same test).
- Every block with block-entity data stays in the same cell as the same block in every later level (a builder never
  breaks those while clearing, `MaterialRules.isProtected`), and every bed stays where it was.

## Fits
- Plot (y = 0 sits on the clicked block; the front is the z = 0 side), from where the tests place each level:
  - Level I: **at most 15 wide x 28 tall x 16 deep** (`QaRefreshFurnishedGameTests.aFurnishedOldTownhouseMirroredIsRefreshedToAFreshBuild`
    mirrors it at (15, 2, 15) in the 30-cube `huge_area`; also built at (8, 2, 8) there and at (9, 2, 8) in `tall_area`).
  - Levels II and III: **at most 21 wide x 28 tall x 22 deep** (`BuilderGameTests.buildsTownhouseII`, `buildsTownhouseIII`
    at (9, 2, 8) in the 30-cube `tall_area`, with the builder's barrels at x <= 6, z <= 4).
  - Level IV: III above the ground plus the 4-deep cellar.
- Declared sizes: `StarterBlueprints.TOWNHOUSE` (`11, 20, 12`), `_2` (`11, 20, 18`), `_3` (`15, 26, 18`), `_4`
  (`15, 26, 18`, basement 4) today; if a size changes the ship step changes those `Vec3i`s
  (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: sizes match, every block one a builder
  can place).
- Material budget: I, II and III each fit the four barrels `buildStarter` stocks (**at most 108 stacks**, each item
  rounded up to whole stacks).
- Build time: I within 60,000 ticks, II within 70,000, III within 90,000 (`buildsTownhouse`, `buildsTownhouseII`,
  `buildsTownhouseIII`); I with exactly its materials across a save and reload within 70,000
  (`QaGranderHomes1107bGameTests`); I with free materials and a reload mid-STRUCTURE within 60,000 (`QaWindowsAndWards1028GameTests`).
- Front and way in: the z = 0 side faces the player who places it and the road of a Steward plot; the front door goes
  there on the ground floor; every upgrade keeps that front.
- Neighbours: a home in a Steward `homes` zone (`VillageMaps` counts `townhouse` as HOMES); the Steward finds it a plot.

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `HomeGradeGameTests.aBedInTheTownhouseCountsIIAndInItsUpgradeIII`: a bed at template cell (4, 6, 8) (helper (6, 8, 10),
  origin (2, 2, 2)) is inside I, II and III, in plain and styled ids (cherry I, stonework II): grade 2, 3, 3.

Built whole (every block right, 0 skipped, supplies exact): `BuilderGameTests.buildsTownhouse`, `buildsTownhouseII`,
`buildsTownhouseIII`; `QaGranderHomes1107bGameTests.aTownhouseBuiltAcrossASaveAndReloadEndsFinishedAndSellsItsII`
(finished, exactly its materials, beds grade II, its builder then sells II);
`QaWindowsAndWards1028GameTests.qaABuiltTownhousesGlassJoinsAfterAReloadMidStructure` (glass, above).

Beds give their grade: `QaGranderHomes1107GameTests.everyBedOfTheTownhouseGivesItsGrade` (I, II, III turned and
mirrored at (1, 2, 1) in `huge_area`, every bed inside the area, grades 2, 3, 3, garden beauty 0, the home's name the
blueprint's display name, unchanged after a save and reload).

Refresh (36.21): `QaReleaseRefresh1028GameTests` (`qaReleaseRefresh_townhouse`, `_townhouse_2`, `_townhouse_3`: built from
the `cd1557d8c` drawing at (7, 2, 2) in `huge_area`, refreshed through the hall's Repairs page, ends equal to a fresh
build of the new drawing); `QaRefreshFurnishedGameTests.aFurnishedOldTownhouseMirroredIsRefreshedToAFreshBuild` (from
`42864073a`, furnished, mirrored, nothing floating); `B648GameTests.b648AnOldTownhouseIsOfferedTheNewRailings` (the
`cd1557d8c` drawing is offered a refresh of at least 3 changes); `QaRefreshHistory1028GameTests` (every older drawing in
`blueprint_history/townhouse*/` that differs from the new one is recognised). The new drawing must keep these working;
`blueprint_history/` already holds the old drawings.

By id only (the drawing doesn't matter beyond the rules above): `HomeGradeGameTests` (grades, the shipped rule files,
which builders sell it, never handed out by the Table), `QaGranderHomesGameTests`, `QaGranderHomes1107GameTests`,
`QaGranderHomes1107bGameTests.everyStyledTierLeadsToTheNextTierInItsOwnStyle`, `QaGranderHomes1107BugTests`,
`QaBuilderFixes1107bGameTests`, `QaHomeGradeFilesLaneC1107GameTests`, `ManorGameTests`, `BlueprintUnlocksGameTests`,
`StyleGameTests` (sold-only), `ClassMigrationGameTests`, `BasementGameTests` (IV upgrades III).

The owner's rules (`check.py`: posts at fence corners, lamps on tables or posts, crockery only by a stove, furnace,
smoker or counter or on a table with a chair; `open_panes`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate`
prints no CHECK line for any level, and `holes.py` passes.

## Free
Everything not listed above: shape, plan, rooms, roofs, materials, colors, style, how many storeys, whether there is a
shop, a garden, a tower or a study, which floor holds which bedroom, how many beds beyond the minimum, what II and III
add (beyond keeping 60 % of the level below and their beds), what the cellar holds, where furniture, chests and the
chimney stand within the rules above, lighting.
