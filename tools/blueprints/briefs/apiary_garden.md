# Apiary Garden: the brief

Family id(s): `apiary_garden`, `apiary_garden_2` (never renamed; "Apiary Garden", "Apiary Garden II" in `en_us.json`,
`blueprint.aliveworkplace.apiary_garden*`)
Variants: village (none: no `village/<style>_apiary_garden` piece exists), modded (none to draw: `modded_library.py`
makes `structure/modded/apiary_garden*.nbt` from the vanilla drawings, turning chests into Carved Wood ones, and the
ship step reruns it), looks (none to draw: the Table's style buttons, `BlueprintStyles`, restyle the drawing
automatically).

## What it is for
The **Beekeeper**'s workplace (`ModVillagers.BEEKEEPER`). Her job site is **any `minecraft:beehive` or bee nest, or
this mod's Apiary block** (`aliveworkplace:apiary`): the profession's `heldJobSite` / `acquirableJobSite` accept all
three (`ModVillagers`). A villager is made a Beekeeper by handing him a glass bottle or shears at a beehive
(`work/Stations`; nobody takes a beehive by himself), and a Beekeeper without a job site takes the nearest beehive
nobody works at within 16 blocks (`Stations.retakeHive`). A beehive keeps no count of its workers, so **every beehive
in the build that nobody works at is listed by the Village Hall as one more free Beekeeper workstation**
(`VillageHalls.freeStations`, B657) and the Steward may hand each one to a different jobless villager; the Table's
details show "Jobs: Beekeeper (N)" with N counted from the drawing (`QaTableJobsGameTests`). From whichever hive is her
job site she works all shift (`bee/BeekeeperWork`): **harvests every beehive or nest within 16 blocks** of it when full
(honey bottles with glass bottles from her chests, honeycomb with shears; the bees stay in the hive if the hive is
smoked by a campfire under it, otherwise they fly out), **plants flowers** from her chests round any hive with fewer
than 4 flowers near it, **breeds bees** with flowers while there are fewer than 3 a hive, and **puts the harvest in
her chests**. Between those she makes candles, colored candles and honeycomb blocks for builders at her hive
(`craft/BeekeeperCraftWork`, no crafting table needed). Her chests are **the containers within 8 blocks across and 4 up
or down of her job-site hive** (`SupplyContainers.find(level, station)`, `RADIUS` 8, `VERTICAL` 4); **with none in
that reach she does nothing at all** and reads "needs a chest by the beehive" (`BeekeeperWork.work`, `no_chest`). The
**Steward** builds Level I by himself in a `gardens` zone when a Beekeeper is wanted and has no workstation
(`steward_rules/workplace_apiary_garden.json`, priority 62, cooldown 2 days, Hamlet and up), and a renewal turns a
Beekeeper's old house into one when it fits (`steward_renewal/beekeeper.json`, `Renewals.fit`). The Village Map shows
it as a farm (`VillageMaps.kindOf`: `FARMS`). Finishing each level grants the hidden advancement `built/apiary_garden`,
`built/apiary_garden_2`. No expansion switch hides it.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `apiary_garden` | The Beekeeper's working garden: beehives (each a job site and a hive to harvest), flowers round them, her chest(s) within reach of every hive | In the Table (`StarterBlueprints.ALL`), unlocked the usual ways (builder level-ups, a bought or found blueprint); Steward rule `workplace_apiary_garden` (gardens zone, Hamlet+); renewal list `beekeeper` |
| II | `apiary_garden_2` | More hives and the flowers to keep them (more honey, more Beekeeper places); still a chest within reach of every hive | Upgrade of I (`BlueprintUpgrades`: id + `_2`); sold by the builder who finished I (`UpgradeOffers`) |

No third level and no other id: `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` counts exactly 64 upgrades
library-wide. How many hives each level has is the designer's, with at least one in I and more in II than in I.

## Must have (each level)
- **Job blocks: beehives** (`minecraft:beehive`, drawn with `honey_level` 0 and no bees; the builder places no
  entity), at least one. An `aliveworkplace:apiary` block is allowed too (a second kind of Beekeeper job site; it would
  add a line to the Table's jobs) but not required. **No other workstation** of any job (a composter, barrel, lectern,
  smoker, crafting table, cauldron, Builder's Bench, Flower Stand, ...): the Steward builds this for a Beekeeper, and the
  Hall would list any other block as a free job of another trade. A chest, campfire or bed is not a job block.
- **A chest within reach of EVERY beehive** (and of the Apiary block, if drawn): at least one `minecraft:chest` within
  **8 blocks across (x and z, each) and 4 up or down** of each hive. Any hive can become a Beekeeper's job site (the
  nearest free one is taken), and a Beekeeper at a hive with no chest in that box idles for good. No test checks this;
  the code does. Chests are drawn empty (builders never fill or copy containers).
- **Every hive harvestable on foot:** the Beekeeper must stand within **3 blocks** of a hive to harvest it
  (`BeekeeperWork.REACH`, `Walker.reach`), and within 3 of a chest to take or put things. A hive she can't get to is
  given up after 600 tries. Every hive and every chest is reached on foot from the ground outside the plot.
- **Every hive works as a hive** (the game's rules, which the job depends on): its front face (the `facing` side) opens
  onto air, so bees can leave and come back; there are flowers within reach of it.
- **Flowers round every hive:** the job counts blocks in `#minecraft:flowers` within **4 across, 3 below to 2 above**
  each hive and plants more while there are **fewer than 4** (`BeekeeperWork.flowersNear`, `FLOWERS_PER_HIVE`). Drawn
  with at least 4 flowers in that box per hive, she has nothing to plant on day one. She plants only on **grass or dirt
  (not mud) with air above, within 3 across and 3 below to 1 above a hive** (`flowerSpot`), so leave some such ground
  there if the garden is to refill itself. Every plant stands on a block it can survive on (the build is pasted and
  updated): a flower on grass, dirt, podzol or similar.
- **Smoke (optional, read by the code):** a hive with a lit campfire close enough below it, by the game's own smoke rule
  (`BeehiveBlockEntity.isFireNearby`: a lit campfire up to 5 blocks under the hive, the smoke's way up not blocked) keeps
  its bees in when harvested; without it the bees fly out (they don't attack villagers). Not required.
- **Beds and residents:** none required; no code counts beds here, and the Beekeeper lives elsewhere. Any bed drawn
  counts as a village bed (the Table shows "Beds: N"); its head must be reachable on foot and its foot has its head one
  block towards its facing (`BlueprintsStayGameTests`); no bed right inside a doorway (`check.py`).
- **Storage:** chests (at least the one per hive's reach above). **No barrel** (a Fisherman's job block, see Job
  blocks). Every chest stands against a wall, a solid block, a bed or another chest
  (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`) and is within a player's reach (2 across, 1 up or
  down) of a cell walked to from the ground outside (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`).
  Double chests stay whole turned and mirrored (`QaMirroredBuildsGameTests`).
- **Furniture spots:** no kind is required by any test (not a home: `FurnishedHomesGameTests` doesn't list it; there is
  no `blueprint_furniture/apiary_garden*.json` today). Any furniture drawn is recorded as a spot (`rebuild.py`), and
  then: each spot's blocks are exactly the template's (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`);
  nothing small stands on air (`FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece`); every chair faces
  its table (`QaChairFacingGameTests`); a furnished room's floor pieces get block light above 7 from that room's own
  lantern (`FurnishedRoomsGameTests.everyFurnishedRoomHasALight`); no standing lantern on a room's floor
  (`QaFurnishedRooms1031GameTests.noLanternStandsOnARoomsFloor`).
- **No entity** (villager, bee, frame, painting, armor stand), no jigsaw, no structure void in either file.
- Every block is one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).
- Every wooden doorway, if any, has room for a villager 1.95 blocks tall (`QaDoorwayClearanceGameTests`).

## Kept cells (the upgrade)
- II has the same origin and front as I and is at least its size in x, y and z; **at least 60 % of I's non-air blocks
  stay the same block in the same cell** (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`). II grows out of I
  (REBUILD.md): it adds to I's garden, it is not a different garden.
- Every chest and every hive of I stays in the same cell as the same block in II (builders never break containers or
  copy their contents, `MaterialRules.isProtected`; a hive that moved would turn out its bees and its Beekeeper's job
  site).
- II has no basement (`QaBenchMovesCellars1104bGameTests.aBuildWithNoBasementIsNeverOfferedACellar`).

## Fits
- **Plot (I): at most 13 wide x 16 tall x 13 deep.** `BuilderGameTests.buildsApiaryGarden` builds Level I at helper
  (9, 2, 9) in the 22 x 18 x 22 `big_area` (so x 9..21, y 2..17, z 9..21); the bench (2, 2, 2), its chest (2, 2, 4),
  the four stocked barrels (1/3/4/5, 2, 4) and the builder (3, 2, 3) stand north-west of it. (`huge_area`, 30 x 20 x 30,
  is not used for this family.)
- **Plot (II): no test builds it**; keep it within 18 wide x 16 tall x 18 deep so it stays a garden-zone plot (the
  builder soak now spaces plots by each build's own width, B690, but a small plot keeps the soak compact).
- **Smaller is better for renewals:** a Beekeeper's old house is replaced by Level I only if I fits that house's
  footprint plus 3 blocks round it, its front on the old front's line centred on the old door (`Renewals.fit`,
  `MARGIN` 3).
- y = 0 sits on the clicked block; the front is the z = 0 side.
- **Declared sizes:** `StarterBlueprints.APIARY_GARDEN` (`11, 8, 11`) and `APIARY_GARDEN_2` (`17, 8, 11`) today; if a
  size changes the ship step changes those `Vec3i`s (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`), and
  `modded_library.py` reads the sizes from that file.
- **Material budget (I): at most 108 stacks** (`buildStarter` stocks four barrels of 27 with exactly what
  `BuildPlan.materials()` lists, each item in whole stacks, and fails "test needs more barrels" above that).
- **Build time and block budget:** `buildsApiaryGarden` (`starter_builds_3` batch, timeout 30,000 ticks, build delay 2,
  builders' help off) starts at day time 2,000 and does **not** hold the clock: the builder stops at dusk, so Level I
  must reach DONE in about **9,000 working ticks**, with every block as drawn (`assertBuilt`: no unfinished block).
  Earlier rebuilds had to cut a one-level workshop to about 1,000 placed blocks for that; budget **Level I at most
  about 800 non-air blocks** (a garden makes the builder walk more between cells), and Level II's new blocks about the
  same. The old drawings are 325 (I) and 460 (II) non-air blocks.
- **Front and way in:** the z = 0 side faces the player who places it and the road of a Steward plot; the way in to the
  hives and the chest is from that side.
- **Neighbours:** a plot in a Steward `gardens` zone; the Steward keeps 2 blocks clear round its plot (`Plots.CLEAR`)
  and wants the ground under it within 4 blocks of level (`Plots.MAX_STEP`). No party walls; nothing placed beside it.

## Rules tests check
- `BuilderGameTests.buildsApiaryGarden`: Level I, placed at helper (9, 2, 9) unturned, built by one builder from
  exactly its material list (at most 108 stacks) with no block left wrong, within the 30,000-tick timeout from day
  time 2,000 (see Build time).
- `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (II over I, 60 %, exactly 64 upgrades library-wide),
  `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable` (both levels).
- `WorkplacesGameTests.workplaceRule_apiary_garden_beekeeper` (`rules()` generator, case `workplace_apiary_garden`):
  the rule builds `aliveworkplace:apiary_garden` in `gardens` at Hamlet, and the blueprint loads (by id only).
- `QaTableJobsGameTests.everyListedBuildShowsItsOwnJobsAndBeds`: the Table's Jobs/Beds line equals the job blocks and
  beds counted in the drawing (computed, so any count passes).
- By id only: `QaBuiltAdvancements1116bGameTests` (the `built/` advancements), `ModdedLibraryGameTests` (a modded copy
  exists), `TablePreviewGameTests`, the showcase scene `build_fixes` (`devclient/BuildFixesScene`: both levels placed;
  its still `50_apiary_garden_front` is captioned "the beds sit below the fence" in `tools/showcase/scenes.py`, which
  the ship step rewords to fit the new drawing).
- Library-wide checks it must pass: `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds` (a standing
  lantern on a block whose top holds its middle; no dirt path under a solid block such as a fence); `B648GameTests`,
  `QaWindowsAndWards1028BugTests`, `QaBuilders1028GameTests` (no pane or bar joined on one side with air on the other);
  `QaBuildReachGameTests`; `QaMirroredBuildsGameTests`; `QaFurnishedRooms1031GameTests`; `FurnishedRoomsGameTests`;
  `FloatingFurnishingsGameTests`; `FurnitureSpotGameTests`; `QaDoorwayClearanceGameTests`; `QaChairFacingGameTests`;
  `QaLedgerFramesHaulGameTests`; `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural` (every material
  has a plural in `en_us.json`); `StyleGameTests.chimneysStayWholeInEveryStyle` (only if a campfire stands on a stack of
  solid blocks: the stack stays solid in every style).
- The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
  stove or on a table with a chair, no bed right inside a door; `open_panes`), STYLE.md's checks and `holes.py`:
  `rebuild.py --candidate` prints no CHECK line. The family is not in `owner_rules_baseline.json`. The ship step keeps
  the old drawings in `blueprint_history/` (`history.py`) and regenerates the modded copies and furniture spots.

## Free
Everything not listed above: shape, plan, enclosure or none, shelter or none, roof, materials, colors, how many hives
(at least one in I, more in II) and how they are raised, which flowers and how many beyond 4 a hive, campfires, beds,
chests beyond one in reach of every hive, lighting (within the light rules), and where on the plot Level II grows.
