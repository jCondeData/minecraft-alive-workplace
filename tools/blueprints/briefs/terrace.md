# Terrace: the brief

Family id(s): `terrace`, `terrace_2` (never renamed; names "Terrace" and "Terrace II" in `en_us.json`)
Variants: village (none drawn), modded (none to draw: `modded_library.py` makes `structure/modded/terrace*.nbt` from the
vanilla drawing, and its crockery pass needs the kitchens below), looks (none drawn: the Table's style buttons restyle
it through `BlueprintStyles`).

## What it is for
**Several households under one roof**: the Terrace is the village's cheap way to many beds. Level I is **two homes
side by side, four beds**; Terrace II **adds a third home, six beds in all** (the player reads both counts in the
questbook: "two row houses under one roof, with four beds", "Add a third home to your Terrace: six beds in all").
Level I is on the Blueprint Table from the start (`blueprint_unlocks/starting.json`); II is locked until the tier below
stands (its hint reads "Sold by a builder once the tier below stands", or "A questbook reward" with FTB Quests) and turns
up as a blueprint in village chests (five `loot_table/chests/village_*.json`). The Steward builds a Terrace in a `homes`
zone by himself when a Village or bigger is **three or more beds short** and no upgrade would add beds
(`steward_rules/homes_terrace.json`, priority 90); his renewal of old homes tries it third, taking the first building
with at least as many beds as the old house (`steward_renewal/homes.json`). It is a tier I home and II a tier II home
(`HomeGrades`, by tier; no `homes/terrace.json`). On the village map it is a home (`VillageMaps`). Every bed in it is a
home (`Homes.of`). Each home is a household's own: its own door, its own kitchen with a table and chair, its own
bedroom with two beds, and its own way up to that bedroom. Level II is an upgrade on the same spot and origin.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `terrace` | Two homes side by side along the front (x), each a kitchen and a bedroom with two beds: **exactly 4 beds** | On the Table from the start; Steward rule `homes_terrace` (3+ beds short, Village rank); questbook "A Terrace" (`built/terrace`) |
| II | `terrace_2` | **A third home** like the first two: **exactly 6 beds** in all | Upgrade of I (`BlueprintUpgrades`: id + `_2`); sold by a builder once I stands, or a questbook reward; village chest loot; questbook "Terrace II" (`built/terrace_2`) |

## Must have (each level)
- **Homes and beds.** I: two homes, II: three, each with **exactly two beds** (I: 4 `HOME` POIs, II: 6). Several tests
  place I and assert exactly 4 beds (`TerraceSleepGameTests`, `QaHallBeds1103cGameTests`, `QaBedsHauler1105cGameTests`,
  `QaTerraceUpstairs1104c2BugTests`). The homes stand **side by side along x**: sorted by x, beds 0 and 1 are the west
  home's and beds 2 and 3 the east home's (the same tests take beds by that order, also with the Terrace turned a
  quarter and sorted by blueprint x). **All bed heads of I are on one floor** (`TerraceSleepGameTests` asserts one
  height, today template y = 6). Every bed has its head to a wall (`FurnishedRoomsGameTests.everyBedHasItsHeadToAWall`),
  lies inside the box and is a home turned or mirrored any way (`HomesSpecGameTests.everyBedOfEveryStarterHouseIsAtHomeTurnedAnyWay`).
- **Each bedroom has open floor beside its beds**: a standable cell (air, air above, sturdy floor below) on the beds'
  level within 3 blocks (Manhattan) of the bed, where the tests stand a villager (`QaTerraceUpstairs1104c2BugTests`,
  `QaBedsHauler1105cGameTests.upstairsBy`).
- **Each home's beds are reached from that home's own front door**, inside that home. A villager standing in the
  *other* home's ground floor goes out, in by his own door and up to his own bed (`TerraceSleepGameTests.aVillagerOfTheWestHomeClimbsItsLadderNotTheEastOnesAndSleeps`,
  `QaHallBeds1103cGameTests.aTerraceVillagerReloadedStillClimbsHisOwnLadder`); a villager up in one home's bedroom gets
  down and over to the other home's free bed (`QaBedsHauler1105cGameTests`, B925). The way up can be a ladder or stairs:
  the code handles both (`BedLadders` claims a bed up a ladder; vanilla walks stairs). A door on that way must be one a
  villager opens (wooden, or standing open: an iron door blocks the claim, B876).
- **Job blocks.** None is read by a job or test. Every block with block-entity data stays in its cell in every later
  level (see "Kept cells").
- **Storage.** None is read by any job. Every chest stands against a wall, a solid block, a bed's foot or its other
  half (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`).
- **Kitchens** (B281, B641), one per home on its ground floor:
  - **Exactly one `table` and one `chair` furniture spot per home**, on the ground floor (I: 2 and 2, II: 3 and 3;
    across I and II exactly 5 tables), each chair nearer its own home's table than any other
    (`FurnishedRoomsGameTests.everyTerraceHomeHasItsTableAndChairSpots`, `QaClinicTerrace1028GameTests`,
    `FloatingFurnishingsGameTests.everyHalfOfTheTerracesHasItsPiecesAndNothingFloating`, which counts 9 x 5 tables).
  - **The cell above every table spot is air in the drawing**: the modded library puts a crockery set there (at least 2
    in I, 3 in II, `TerraceFurnitureCompatTests.everyKitchenOfANewTerraceGetsTheModsPiecesTurnedAndMirrored`), and with
    a furniture mod's lower pieces nothing may stand on a table and float (`FloatingFurnishingsGameTests`).
  - **Every kitchen alike**: built by a builder with furniture pieces, turned and mirrored, every home's table block,
    what is on it, the block above that, and its chair are the same as in the first home
    (`QaClinicTerrace1028GameTests.qaABuiltTurnedMirroredTerraceFurnishesBothKitchensAlike`, `...TerraceIIFurnishesEveryKitchenAlike`).
  - Every table and chair spot is one the pack's furniture mods fill (Handcrafted and Beautify, also with CobbleFurnies
    first): a spot shape and wood their pieces resolve, none left vanilla (`TerraceFurnitureCompatTests.everyTerraceHomeGetsTheModsFurniture`);
    without a furniture mod everything stays vanilla (same `FurnishedRoomsGameTests` test).
  - Each kitchen is lit by its own light (`FurnishedRoomsGameTests.everyFurnishedRoomHasALight`: block light above 7 at
    every piece on a room's floor, from a lantern hung or on a table; not on these tables, see above).
- **Other furniture spots** (`blueprint_furniture/<id>.json`, written by the recipe's `furniture.json` through
  `rebuild.py`): a `bed` spot for every bed. No other kind is required (the Terrace isn't in
  `FurnishedHomesGameTests`' list), and more kinds are welcome. Every spot's blocks are exactly the template's there
  (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); nothing stands on air
  (`FurnishedRoomsGameTests.everyFurnishedPieceHasWhatItStandsOn`); no piece within two blocks of a door along the way
  through it (`noFurnitureStandsInADoorsWay`); with the furniture choice applied turned and mirrored, nothing rests on
  nothing and the builder's last look moves nothing (`FloatingFurnishingsGameTests`).
- **Front glass in Level I**: plain `glass_pane`s on the front (16 cells pinned today, at least 2 so that the reload
  test can save between them), each joined on both sides as the game joins it once built, also after a save and reload
  mid-windows; every pane, fence and wall in the built Terrace joined exactly as the game joins it, at least 16 of them
  (`B640GameTests.b640TheTerracesFrontPanesJoinTheirNeighbours`, `b640TheFrontPanesJoinAfterASaveAndReloadMidWindows`).
  In every level no pane or bar is joined on one side only with air on the other (`B648GameTests`, `check.py`'s
  `open_panes`).
- **A chimney is optional**, but any campfire on a stack must stand on full solid blocks that stay solid in every style
  (`StyleGameTests.chimneysStayWholeInEveryStyle`).
- **Front doors**: one per home on the ground floor, on the z = 0 side. Doors, beds and double chests stay whole turned
  and mirrored (`QaMirroredBuildsGameTests`).

## Kept cells (every upgrade, and every refresh)
- II has the same origin and front as I and is at least its size in x, y and z
  (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`), and **at least 60 % of I's non-air blocks stay the same block
  in the same cell** in II (same test).
- Every block with block-entity data in I (barrel, chest, furnace, ...) stays in the same cell as the same block in II
  (a builder never breaks those while clearing, `MaterialRules.isProtected`), and every bed stays where it was.
- **A refresh (36.21) never moves a container** (`Refreshes.kept`: containers, slept-in beds, job blocks). Every older
  drawing (the live `.nbt` and `blueprint_history/terrace*/11b48d517.nbt`, `2ef62ac02.nbt`, all three the same here) has
  these, in template cells (x, y, z). **The new drawing must not put a door or a walkway on any of them**, nor anything a
  kept container would block:
  - Terrace I: barrels (0, 0, 1), (4, 1, 7), (10, 1, 7); furnaces (2, 1, 7), (8, 1, 7) (facing north); chests (4, 6, 2),
    (8, 6, 2) (facing south).
  - Terrace II: all of I's, and furnace (12, 1, 7), barrel (14, 1, 7), chest (14, 6, 2).
  - **The furnace cells must hold a north-facing furnace in the new drawing.** The refresh-equals-fresh-build check
    (`RefreshMatchesFreshBuildGameTests.differences`) skips chests and POI blocks (a barrel is one), not furnaces: a kept
    furnace where the new drawing wants anything else fails it.
  - Old bed heads (slept-in beds are kept too): I (2, 6, 4), (3, 6, 4), (9, 6, 4), (10, 6, 4), feet one less in z;
    II also (12, 6, 4), (13, 6, 4). Better not a door or a walkway either.

## Fits
- Plot (y = 0 sits on the clicked block; the front is the z = 0 side), from where the tests place each level:
  - Level I: **at most 13 wide x 16 tall x 13 deep** (`BuilderGameTests.buildsTerrace` at (9, 2, 9) in the 22 x 18 x 22
    `big_area`, the builder's barrels at x <= 5, z = 4); turned a quarter at (20, 2, 8) in the 30-cube `huge_area`
    (`QaHallBeds1103cGameTests`), and with the B280 landscaping ring round it at (8, 5, 10) there.
  - Level II: **at most 23 wide x 28 tall x 14 deep** (`QaReleaseRefresh1028GameTests` at (7, 2, 2) and
    `QaClinicTerrace1028GameTests` turned and mirrored at (16, 2, 6), both in `huge_area`), and at least I's size.
- Declared sizes: `StarterBlueprints.TERRACE` (`13, 16, 10`) and `TERRACE_2` (`18, 16, 10`) today; if a size changes the
  ship step changes those `Vec3i`s (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: sizes
  match, every block one a builder can place).
- Material budget: I fits the four barrels `buildStarter` stocks (**at most 108 stacks**, each item rounded up to whole
  stacks).
- Build time: I within 30,000 ticks (`buildsTerrace`); I and II each built by a builder, turned and mirrored, within
  24,000 (`QaClinicTerrace1028GameTests`); I with its glass within 60,000 (`B640GameTests`); the B280 landscaping within 4,000.
- Front and way in: the z = 0 side faces the player who places it and the road of a Steward plot; every home's front door
  goes there on the ground floor; II keeps that front and adds its third home along x.
- Neighbours: a home in a Steward `homes` zone (`VillageMaps` counts `terrace` as HOMES); the Steward finds it a plot.
  Landscaping round it leaves nothing raised above the lawn (`B280LandscapeGradeGameTests.b280TerraceLeavesNoRaisedBandRoundIt`,
  footprint from the declared size).

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `TerraceSleepGameTests`, `QaHallBeds1103cGameTests`, `TerraceSleepCompatTests` (Handcrafted fancy beds): a villager
  spawned in the east home's ground floor, today template (8, 1, 5), by its way up; bed heads all at y = 6.
- `FurnishedRoomsGameTests.everyTerraceHomeHasItsTableAndChairSpots` and `TerraceFurnitureCompatTests.everyTerraceHomeGetsTheModsFurniture`:
  home *n*'s kitchen spots at y = 1 with 1 + 5n < x < 6 + 5n (party walls at x 1, 6, 11, 16 today).
- `FloatingFurnishingsGameTests.aTerraceFurnishedBeforeB281KeepsItsBedsAndIsBroughtToBothKitchens` and
  `TerraceFurnitureCompatTests.aTerraceFurnishedBeforeTheFixIsRefreshedWithBothKitchensModded`: "the east kitchen's
  spots" are the spots at y = 1, x >= 7, exactly 2 of them (its table and chair); 4 beds = 8 fancy bed halves; after the
  refresh a crockery set on each of the 2 kitchen tables.
- `B640GameTests.FRONT`: the 16 front pane cells, today (4, 5, 7, 8; y 2-3; z 0) and (3, 4, 8, 9; y 10-11; z 1).

Built whole (every block right, 0 skipped, supplies exact): `BuilderGameTests.buildsTerrace`; `B640GameTests` (two
builds, glass joined); `QaClinicTerrace1028GameTests` (I and II, turned and mirrored, kitchens alike, nothing floating);
`B280LandscapeGradeGameTests` (landscaped, no raised band).

Beds and sleeping (B304, B876, B925): `TerraceSleepGameTests` (own home's way up; a homeless villager takes a free bed
next door; ladder paths), `QaHallBeds1103cGameTests` (turned a quarter; save and reload), `QaBedsHauler1105cGameTests`
(up in the wrong bedroom: goes down and over to the free bed; one taken at nightfall; save and reload on the way; both
homes full: steals nothing), `QaTerraceUpstairs1104c2BugTests`, `TerraceSleepCompatTests`.

Refresh (36.21): refresh tests compare **a refreshed old building with a fresh build of the new drawing**.
`QaReleaseRefresh1028GameTests` (`qaReleaseRefresh_terrace`, `_terrace_2`: built from the `11b48d517` drawing at
(7, 2, 2) in `huge_area`, refreshed through the hall's Repairs page, refreshed from that drawing, ends equal to a fresh
build of the new one, chests and POI blocks aside, so the furnaces above count); `B640GameTests.b640AnOldTerraceIsOfferedTheNewBays`
(the `11b48d517` drawing is offered a refresh putting in at least 12 blocks); `TerraceFurnitureCompatTests` and
`FloatingFurnishingsGameTests` (an old furnished Terrace refreshed: both kitchens modded, beds kept);
`QaRefreshHistory1028GameTests` (every older drawing in `blueprint_history/terrace*/` that differs from the new one is
recognised). `blueprint_history/` already holds the old drawings.

By id only (the drawing doesn't matter beyond the bed counts above): `StewardHomesRulesGameTests` (a Village three beds
short gets a Terrace first), `DaubHouseGameTests`, `StewardRulesGameTests`, `HomeGradeGameTests`,
`QaHomeGradeFilesLaneC1107GameTests`, `QaGranderHomesGameTests` (I tier I, II tier II), `ManorGameTests`,
`BlueprintUnlocksGameTests`, `QaBlueprintUnlocksGameTests`, `QaBlueprintLibraryVerify1108bGameTests` (II's hint),
`QaQuestbookClaims1116eGameTests`.

The owner's rules (`check.py`: posts at fence corners, lamps on tables or posts, crockery only in a kitchen or on a table
with a chair, upper floors reached by stairs or a ladder; `open_panes`), STYLE.md's checks and `holes.py`:
`rebuild.py --candidate` prints no CHECK line for either level, and `holes.py` passes.

## Free
Everything not listed above: shape, plan, materials, colors, style, how many storeys, ladder or stairs, where in each
home the kitchen and bedroom go (within the rules above), what each home holds beyond its kitchen and two beds, more
furniture kinds, gardens or yards, chimneys, lighting.
