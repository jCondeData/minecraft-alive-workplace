# Market Stall: the brief

Family id(s): `market_stall`, `market_stall_2`, `market_stall_3` (never renamed; "Market Stall", "Market Stall II",
"Market Stall III" in `en_us.json`, `blueprint.aliveworkplace.market_stall*`; hidden advancements `built/market_stall`,
`built/market_stall_2`, `built/market_stall_3`; the questbook's hamlet chapter asks for `built/market_stall`)
Variants: village (none: no `village/<style>_market_stall` exists and no village pool lists it), modded (none to draw:
`modded_library.py` makes `structure/modded/market_stall{,_2,_3}.nbt` from the vanilla drawing; the ship step reruns
it), looks (none). Styles are applied by `BlueprintStyles` automatically (`styled/cherry/aliveworkplace/market_stall`,
`styled/spruce/...` are used by `QaLibraryUnlocks1107cGameTests` and resolve from the plain drawing; nothing to draw).

## What it is for
The village's first trading place: a street stall where goods are set out, which grows into a shop. Level I is a
stall with no job and no residents; Level II adds a **Shop Counter**, where a villager becomes the **Shopkeeper**
(the first player to open the counter owns the shop) and sells what the stores near the counter hold at the prices
on the counter's list, paying the takings into those stores (`shop/Shops`); Level III adds a storeroom whose stores
are within the counter's reach, so the shop has more stock to sell from. On the village map it is a **Trade**
building (`VillageMaps.kindOf`, by id). A Journeyman Builder sells the Level I blueprint (8 emeralds); the Steward
builds one in the Market zone once the village is ranked Village; builders who finish a level sell the next.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `market_stall` | A stall with goods on show. No job block, no bed, no cellar | Starts **locked** (hint `screen.aliveworkplace.table.hint.builder.2`); unlocked by buying it from a **Journeyman (level 2) Builder for 8 emeralds** (`ModTrades`, `BlueprintUnlocks.BUILDER_SALES`), using a found one on a Table (a styled one unlocks the plain one), or a builder's level-up; in village chest loot (`village_inn`, `village_kitchen`, `village_storehouse`, `village_builders_workshop`, `village_carpenters_workshop`). Steward rule `steward_rules/market_stall.json`: at least 1 villager, none built yet (an upgraded one counts), rank Village, zone `market`, max 1 |
| II | `market_stall_2` | **One Shop Counter** (`aliveworkplace:shop_counter`: the Shopkeeper's job site, `SHOP_COUNTER_POI`), and stores within its reach for the shop's stock and takings. Keeps I | Upgrade of `market_stall` (`BlueprintUpgrades`: id + `_2`); a builder who finishes a Level I adds it to his trades (`UpgradeOffers`, price `4 + solid blocks / 40`, clamped 6-32 emeralds); clicked onto a finished Level I it lines up over it |
| III | `market_stall_3` | **More stores, all within reach of the Level II Shop Counter** (8 across, 4 up or down, `SupplyContainers.RADIUS`/`VERTICAL`, a box), so the Shopkeeper sells from them too. Keeps II. **The last level:** there is no `market_stall_4` | Upgrade of `_2`, sold the same way after a Level II is finished |

The upgrade count in `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (64) must not change: all three levels
stay, each an upgrade of the one before, and no fourth.

## Must have (each level)
- **Job blocks.**
  - Level I: none required. Each POI block drawn pulls a villager to a job (a barrel is the Fisherman's, a composter the
    Farmer's, a crafting table the Carpenter's), so add one only on purpose. The old drawing has barrels and a
    composter for looks; nothing requires them (but see the Carpenter test below: barrels and a composter count as
    woodwork if drawn).
  - Levels II and III: **exactly one Shop Counter**, the same cell in both (an upgrade keeps it). Its facing turns with the
    build (`QaTurnedModBlocksGameTests`). It must be reachable on foot (below), with room before it for the Shopkeeper
    to stand and a player to trade.
- **Beds and residents: none, at any level.** Level I must have no bed: `MasterArchitectGameTests.aRebuildPicksAnUpgrade`
  relies on the stall being "no beds" (a Legend's Architect upgrades homes, a blueprint with a bed, first; a stall with a
  bed would be picked before the cottage). The Shopkeeper lives elsewhere.
- **Storage.** Level I: none read. Level II: the stores the shop sells from and pays into are every chest, barrel or
  other store within 8 across and 4 up or down of the Shop Counter; at least one is needed for the shop to work, and every
  one must be in that box. Level III: more stores than II, all in that box. Every store drawn empty (builders never copy
  contents), openable (nothing solid on a chest's lid), and reachable on foot (below). Every indoor chest against a wall,
  a solid block, a bed or another chest (`QaFurnishedRooms1031GameTests`). A double chest is one store.
- **No cellar at any level.** `QaBenchMovesCellars1104bGameTests.aBuildWithNoBasementIsNeverOfferedACellar` asserts the
  Level I has `basement() == 0`; II and III are not in `StarterBlueprints.WITH_BASEMENTS` or `LEVEL_3_CELLARS`.
- **Furniture kinds a job or test needs:** none (there is no `blueprint_furniture/market_stall*.json`). Any seat, table or
  other piece drawn is recorded as a spot by `rebuild.py` and must pass `FurnitureSpotGameTests`,
  `FloatingFurnishingsGameTests` and `QaChairFacingGameTests`.
- **At least two flower pots on Level I** (potted or empty), each in the decoration pass and **standing on a planned solid
  block that is not a container or other block entity** (not on a barrel or chest): `QaBuildsGameTests.potsWhoseSupportIsBrokenMidBuildAreSetDown`
  and `aPotUnderminedThenAReloadMidDecorationIsStillSetDown` break the block under one pot in DECORATION and another in
  LANDSCAPE, and fail with "the Market Stall has N flower pots standing on blocks, not 2" otherwise.
- **Woodwork the Carpenter can make, from spruce logs, on Level I** (`CarpenterOwnChestB1151GameTests`, three tests): the
  Level I material list must
  - include **spruce logs** used as logs (the test throws "the Market Stall takes no spruce logs any more");
  - include **at least 20 pieces** of the Carpenter's woodwork from this list: spruce planks, spruce slabs, barrels,
    spruce trapdoors, spruce fences, composters ("the Market Stall has only N pieces of the Carpenter's");
  - keep that woodwork to what **about 28-30 spruce logs** make (today about 101 planks' worth: 28 planks, 19 slabs,
    6 barrels, 5 trapdoors, 2 fences, 1 composter): `logsTheBuilderCarriesAreNotHeldBackInHisChestToo` gives the
    Carpenter only 32 spare logs for all of it (whole crafts round up, up to 24 pieces over), and
    `theStallsWoodworkIsMadeOnceFromTheBuildersOwnLogsAndBuilt` wants at least 16 of 64 spare logs left;
  - fit everything else (every non-woodwork material as stacks, plus two stacks of logs) in **one barrel of 27 slots**
    ("the stock takes N slots"): at most about 25 stacks of non-woodwork materials.
  Any other wooden piece (stairs, doors, other woods) is fine; it is stocked as it is, not made by the Carpenter.
- **Something the Shepherd dyes, on Level I:** at least one of red wool, red carpet or white carpet. The `dyer` showcase
  scene withholds exactly those three from the stall's list and checks the Shepherd made at least one at his loom; the
  `job_chains` scene also wants the Shepherd (or the Tinkerer, who makes a lantern from raw iron) to make something for
  the stall. These are nightly scene checks, not GameTests, but a red scene lands in the `nightly-tests` issue.
- **Nothing that changes after it is placed**: no crops, saplings or growing plants, nothing that falls without a block
  under it, nothing that flows; every pot, candle, lantern and carpet resting on something (`QaBuildsGameTests`' floating
  check after the build, `BlueprintsStayGameTests`); every block one a survival player can get and a builder can place
  (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: no block classified `SKIP`).

## Kept cells (every upgrade)
- II is at least I's size in every dimension, III at least II's, on the **same origin and front** (the upgrade is clicked
  onto the finished build and lines up over it exactly), and each keeps **at least 60 %** of the level below's non-air
  blocks exactly, same cell and same state (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`).
- **Level II must need at least one item Level I has none of** (`QaUpgradeEntry1116bGameTests` withholds up to 8 of the
  most-needed such item: "the Market Stall II needs nothing the stall hasn't got" otherwise), and what II adds over I
  must fit **two chests (54 stacks)**.
- The Shop Counter keeps its cell from II to III, and every store III adds is within its reach.

## Fits
- **Level I: at most 12 wide (x) x 6 deep (z) x 16 tall, with width and depth both even, each at least 4, and at least
  4 tall.** Where each limit comes from:
  - `QaBuilderFixes1027bGameTests.evenAndOddFootprintsAreMeasuredTheSameWay` is the Steward's same-blueprint range test
    **for an even footprint**: the stall turned a quarter round, centred at (11, 13) in a Homes zone x 6-29, z 2-25 with
    the plot margin of 2 (`Plots.MARGIN`), must be a plot, so its depth is at most 7; and an unturned twin must sit a whole
    number of blocks off, so width and depth must have the same parity. Both odd would still pass but the test would no
    longer cover an even footprint, so both even: depth 4 or 6.
  - `BuilderGameTests.buildsMarketStall` and the three `CarpenterOwnChestB1151GameTests` place it at helper (9, 2, 9) in
    the 22 x 18 x 22 `big_area` (bench (2, 2, 2) or Blueprint Table (3, 2, 3), material barrels at x 1-5, z 3-5, the
    Carpenter's crafting table at (16, 2, 3)): at most 13 wide, 13 deep, 16 tall.
  - `QaBuildsGameTests.aStallClickedOntoTallGrassLeavesTheLowerTerraceBesideItLow` clicks it onto (14, ·, 9) in
    `huge_area` facing away from the player and asserts the footprint stays within x 7-21 and z < 24.
  - `QaUpgradeEntry1116bGameTests` lines Level II up by clicking the finished Level I at **origin + (3, 0, 3)**, so that
    cell must be inside Level I's footprint; and it breaks **three full solid blocks (no block entity) in Level I's layers
    y 0 to (height - 4)** ("setup: found only N solid blocks of the stall to break"), so keep Level I at least 4 tall
    with three such blocks (posts, a counter, a plinth...) low down.
  - `B60GameTests` (origin (14, 2, 14) in `huge_area`, ground raised 5 blocks round it): keep within 11 x 11 so the
    raised ring stays in the test's area.
- **Level I build budget:** at most **108 stacks** (`buildStarter` loads exactly the plan's materials into four barrels of
  27, "test needs more barrels"), and built from bare ground within **6,000 ticks** at build delay 2 by one builder
  (`buildsMarketStall`, the tightest budget of the starter builds: today's Level I is 134 blocks). The Carpenter tests
  give 9,000 ticks including his sawing.
- **Level II: at most 21 wide x 9 deep x 16 tall.** `QaUpgradeEntry1116bGameTests` stands it at helper (1, 2, 1) in
  `big_area` with the bench at (3, 2, 10), chests at (5, 2, 10) and (7, 2, 10), the builder at (3, 2, 12) and the Village
  Hall at (16, 2, 16); the upgrade over a Level I with three broken blocks, a wait for withheld materials and a restart
  must finish within 9,000 ticks with every block as drawn, no scaffolding left and the builder's bag empty.
- **Level III:** no test pins a size beyond "at least II's" (it is the Steward's market-zone build, 2 blocks clear round
  it, `Plots.CLEAR`, ground within 4 of level). Keep it a street-side shop, not a hall.
- **Declared sizes (current drawings):** `StarterBlueprints.MARKET_STALL` (8, 7, 6), `MARKET_STALL_2` (15, 7, 6),
  `MARKET_STALL_3` (15, 9, 13), no basement. The `.nbt` must match (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`),
  so the ship step sets them to the new sizes (and the comments "8x7x6" / "8x6" in `PlotGameTests` and
  `QaBuilderFixes1027bGameTests`).
- y = 0 sits on the clicked block; the front is the z = 0 side.
- **Front and way in:** the z = 0 side faces the player who places it and, on a Steward plot, the nearest road or the hall
  (`Plots.Reason.FACING`). Level I needs no door; a lane to a build without one ends at the middle of the z = 0 edge, one
  block out (`Paths.doorstep`'s fallback), so that spot should be ground a villager can walk onto. People browse and buy
  from the front: the goods (Level I) and the Shop Counter (II, III) are reached from the street side. Any door drawn
  (the old III has one into its storeroom) passes `QaDoorwayClearanceGameTests` (a villager 1.95 tall walks through).
- **Neighbours:** the Steward places it in a `market` zone among storehouses and the Market Square; nothing is built
  against it, and it must stay 2 blocks clear of other builds. No village plan places it by coordinates.

## Rules tests check
Pinned to the drawing (the ship step re-checks each against the new one, keeping what it checks, dropping none):
- `BuilderGameTests.buildsMarketStall`: Level I from bare ground with exactly its plan's materials, 6,000 ticks.
- `BuilderGameTests.starterUpgradesKeepMostOfTheirBase`, `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`.
- `QaBuildsGameTests.aStallClickedOntoTallGrassLeavesTheLowerTerraceBesideItLow` (placement onto tall grass, grading
  round it), `potsWhoseSupportIsBrokenMidBuildAreSetDown`, `aPotUnderminedThenAReloadMidDecorationIsStillSetDown`
  (two pots on solid, non-container blocks; nothing floating after).
- `B60GameTests` (builder hops out onto raised ground beside the Level I site).
- `CarpenterOwnChestB1151GameTests.theStallsWoodworkIsMadeOnceFromTheBuildersOwnLogsAndBuilt`,
  `reloadedMidDeliveryTheWoodworkStillArrivesOnce`, `logsTheBuilderCarriesAreNotHeldBackInHisChestToo`,
  `theBuildsOwnLogsAreNeverSawnAndTheLineNamesTheLogs` (spruce logs, woodwork count and budget above; the builder's
  shortage line names "Spruce Log"; built with `plan.unfinished` empty).
- `QaUpgradeEntry1116bGameTests.anUpgradeBoughtFromTheBuilderOverABrokenStallWaitsRestartsAndFinishes` and
  `anUpgradeBlueprintUsedOnTheTableUnlocksItThenStartsTheUpgrade` (I to II, above).
- `QaBuilderFixes1027bGameTests.evenAndOddFootprintsAreMeasuredTheSameWay` (even footprint, depth at most 7).
- `QaBenchMovesCellars1104bGameTests.aBuildWithNoBasementIsNeverOfferedACellar` (no cellar).
- `MasterArchitectGameTests.aRebuildPicksAnUpgrade` (no bed).
- `StewardHomesRulesGameTests.aVillageWithNoStallGetsOneInTheMarket` (wished for in `market` at rank Village; a
  `market_stall_2` counts as built), `StewardRulesGameTests` (the rule list).

By id only (the drawing doesn't matter, the id and name do): `QaBuiltAdvancements1116bGameTests`,
`QaBlueprintUnlocksGameTests`, `QaBlueprintUnlocksBugTests`, `BlueprintUnlocksGameTests`, `QaLibraryUnlocks1107cGameTests`,
`QaBlueprintLibraryVerify1108bGameTests`, `QaBuilderSwitches1103GameTests`, `QaBuilderFixes1027GameTests`, `TableGameTests`,
`TableLibraryGameTests`, `QaTableEdges1102GameTests`, `QaBlueprintTableGameTests`, `QaStageOne/Four/FiveGameTests`,
`QaBuilderWords1031GameTests`, `NetworkPayloadsGameTests`, `QaWorldSavesMoreGameTests`, `QaPersonalRequestsGameTests`,
`ExpansionBuildsGameTests`, `QaQuestbookClaims1116eGameTests`, `CopperlineBlueprintsGameTests`, `PlotGameTests`,
`QaStewardPlotsGameTests`, `StewardPlanningCostGameTests` (these place it by origin only; a bigger footprint extends away
from the spot they test), `QaCobblemonThreatsCompatTests`.

Library-wide checks every level must pass: `QaBuildReachGameTests` (every chest, barrel and job block, the Shop Counter
included, within 2 across and 1 up or down of a spot walked to from outside), `QaDoorwayClearanceGameTests`,
`QaMirroredBuildsGameTests` (doors, beds and double chests stay whole turned and mirrored), `QaTurnedModBlocksGameTests`,
`QaTurnedFramesGameTests`, `QaFurnishedRooms1031GameTests` (no standing lantern on a room's floor; indoor chests against
something), `BlueprintsStayGameTests`, `B648GameTests` and `QaWindowsAndWards1028BugTests` (no pane or bar joined on one
side with air on the other), `QaChairFacingGameTests`, `FurnitureSpotGameTests`, `FloatingFurnishingsGameTests`,
`QaMaterialListGameTests`, `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`,
`StyleGameTests.chimneysStayWholeInEveryStyle` (only if a campfire stands on a stack), `ModdedLibraryGameTests` and
`ModdedLibraryCompatTests` (the regenerated modded copies).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on a table, side table, slab or post or hung,
crockery only by a stove or on a table with a chair; `open_panes`), STYLE.md's checks and `holes.py`: `rebuild.py
--candidate` prints no CHECK line and `holes.py` passes (`market_stall` has no entry in `owner_rules_baseline.json`).

Scenes the ship step updates to the new drawing (not GameTests, but filmed nightly): `carpenter` and `job_chains`
(`ScreenshotHarness` hard-codes the Level I's raw stock), `dyer` (`JobScenes`), the move-row scene (`JobScenes`, a placed
Level I), `GiftedScene`, `WorkHornScene`, `CopperlineBlueprintsScene`, `TableLibraryScene`, the `05_market_stall` still.

Saves (for the ship step, not the designer): ids never change; a site begun on the old drawing must still finish, so the
old drawings go to `blueprint_history/market_stall*/` (`history.py`), `market_stall*` join `generate.py`'s `REBUILT`, and the
wiki rows that describe it (`blueprint-library.md`, `steward.md`'s "even Market Stall", `crafter.md`, `weaver.md`) follow.

## Free
Everything not listed above: shape, roof or awning or none, materials beyond the test-pinned ingredients, colors, what
goods are shown, how the levels grow (sideways, back or up, within the limits), where the Shop Counter and stores
stand (within reach of each other), seating, lighting, planting, paving and style.
