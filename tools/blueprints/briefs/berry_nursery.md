# Berry Nursery: the brief

Family id(s): `berry_nursery`, `berry_nursery_2` (never renamed; "Berry Nursery", "Berry Nursery II" in `en_us.json`,
`blueprint.aliveworkplace.berry_nursery*`)   Variants: village (`village/<style>_berry_nursery` for `plains`,
`desert`, `savanna`, `snowy`, `taiga`; their own contract, see "The village variant"), modded (none to draw:
`modded_library.py` makes `structure/modded/berry_nursery(_2).nbt` from the vanilla drawings; the ship step runs it
again), looks (none)

## What it is for
The **Berry Breeder**'s workplace (ROADMAP 28.9, 28.13), a Pokémon job: it exists only with Cobblemon installed
(`StarterBlueprints.COBBLEMON_ONLY`) and while the config switch `berryBreeders` is on
(`StarterBlueprints.EXPANSION_SWITCH`). Its job block is a **composter**: a jobless villager standing by it,
sneak-clicked by a player holding any Cobblemon berry, becomes a Berry Breeder with that composter as her job site
(`work/Stations`, the `PoiTypes.FARMER` station; without a berry a villager who finds the composter becomes a vanilla
Farmer, or this mod's Composter with bone meal). From then on (`berry/BerryBreederWork`, `berry/BerryBreeders`) she
breeds new berries in **her plot, the farmland round the composter**: she plants the two parent berries of the next
mutation in alternating rows so every plant touches one of the other kind, puts Growth and Surprise Mulch from her
chests on them, picks the fruit, notes new berries in the Village Hall's book, and takes the harvest back to **her
chests by the composter**. With no container near the composter she stands idle ("no_chest"); with no farmland in
reach, "no_farmland"; with farmland but no bed that has a partner bed beside it, she never plants ("no_room"). The
blueprint is sold by a **Journeyman Berry Breeder** for 10 emeralds (`ModTrades.berryBreederTrades`,
`BlueprintUnlocks.VILLAGER_SALES`), and shows on the Village Map as a FARMS building (`VillageMaps.kindOf`, by id). No
Steward rule builds it; the player sites it. Finishing each level grants `advancement/built/berry_nursery(_2)`. In
world generation, villages (with Cobblemon and `pokemonVillageHouses` on) grow the village variant with a Berry
Breeder already in it.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `berry_nursery` | The composter (the job block), the breeder's chest(s) by it, her first farmland beds (paired, watered) | Sold by a Journeyman Berry Breeder (10 emeralds); in the Table only with Cobblemon and `berryBreeders` on |
| II | `berry_nursery_2` | Level I grown: **more farmland beds** (today a second set, under cover), every one still in the breeder's plot round the same composter | Its upgrade (`BlueprintUpgrades.upgradeOf`, by the `_2` id); unlocked as an upgrade. No cost is read from the drawing |

No third level: `PokemonBuildsGameTests.eachPokemonJobUpgradeKeepsItsBase` requires exactly one upgrade per Pokémon job
build (upgrades x 2 == `JOB_BUILDS.size()`).

## Must have (each level)
- **Job block: one `minecraft:composter`, at the same template cell in both levels** (today (5, 1, 8)).
  - It must be a job site when built (vanilla's Farmer POI) (`PokemonBuildsCompatTests.build`: "the job block ... is
    no job site").
  - **The same cell in Level I and Level II**: `QaJobBuildsCompatTests` holds one cell for both tiers, and so does the
    dev-client `PokemonBuildsScene`.
  - **A standing spot within 3 blocks** (x and z within ±3, y within ±1) of it: air at feet and head with no collision
    and no fluid, on a block whose top face is sturdy (farmland, a bottom slab, a dirt path and water don't count). The test
    puts a villager on the *nearest* such spot and hands her an Oran Berry; she must become a Berry Breeder working at
    *this* composter (`QaJobBuildsCompatTests.qaEachBuildsJobBlockTakesAVillagerWithItsItem`).
  - **Reachable on foot from the ground outside**: within 2 across and 1 up or down of a walked cell
    (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`).
  - **Only one composter.** No test counts them in the buildables, but the breeder's plot and chests are measured
    from her composter, and a second one is a second job site that pulls in a vanilla Farmer.
- **Storage: at least one chest within 8 blocks across (x and z each) and 4 up or down of the composter**
  (`SupplyContainers.find(level, station)`, `RADIUS` 8, `VERTICAL` 4). Without it the breeder idles ("no_chest"): she
  fetches berries and mulch from it and puts every harvest back into it (the nearest container first). She must get
  within 3 blocks of it on foot (`BerryBreederWork.REACH`). Every chest and barrel is walked to from outside
  (`QaBuildReachGameTests`); indoor chests stand against a wall or another chest (library-wide furnished-room checks).
  Drawn empty. Note: a barrel also counts as her container but is a vanilla Fisherman's job site; no test forbids one
  here.
- **Farmland beds (the breeder's plot): what the code reads.**
  - **In her plot:** every bed is `minecraft:farmland` within **8 blocks across (x and z) of the composter** and from
    **3 below to 1 above the composter's y** (`BerryBreeders.plot`: composter ±8, y −2..+1; `beds` reads farmland one
    below that). Farmland outside this box is not hers.
  - **Paired side by side along x:** she plants only on a bed that has **another farmland cell directly east or west of
    it** (`BerryBreederWork.pairs`: `beds.contains(bed.east()) || beds.contains(bed.west())`), and the row kind
    alternates with x (`rowKind`). A farmland cell with water or a kerb on both its x sides is never planted. (The old
    drawing put a water channel between every two farmland rows, so no bed in it had a partner; don't repeat that.)
    Since the player may turn the blueprint a quarter turn in the Table, beds that also have a farmland neighbour along
    z keep working when turned.
  - **The cell above each farmland stays air** (she plants only where `level.getBlockState(bed).isAir()`), and she must
    get within 3 blocks of each bed on foot.
  - **The farmland stays farmland:** every farmland cell has a water source within vanilla's hydration reach (4 across
    in x and z, same level or one above), or it dries to dirt and the build test's "every block right" fails. No solid
    block over farmland (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`). Every water cell is a
    source the builder pours, held in on all sides and below.
  - The build tests pin two cells of farmland per level and one of water in Level I (see "Rules tests check"); the ship
    step moves them to the new drawing.
- **Level II adds farmland beds** (`aBuilderBuildsBerryNurseryII`: "the greenhouse's beds, within the breeder's reach
  of the composter"): at least one new pinned farmland cell beyond Level I's, all of them inside the plot box above.
  How many and under what cover is free.
- **Beds and residents: none.** No code counts beds here; the breeder comes from the village (a jobless villager given
  a berry). A bed would make it count as a home. (The village variant has one: see below.)
- **Furniture kinds a job or test needs:** none. There is no `blueprint_furniture/berry_nursery*.json` today; if the
  recipe records furniture spots, each spot's blocks must be exactly the template's there
  (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`), seats face their tables (`QaChairFacingGameTests`), and
  nothing small stands on air (`FloatingFurnishingsGameTests`).
- **No entities** in the drawing (no villager, frame, painting, armor stand), no jigsaw or structure void, and every
  block one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).

## Kept cells (the upgrade)
- Level II has the same origin and front as Level I and is at least its size on every axis; **Level I has more than 150
  non-air blocks**, and **at least 60 %** of them stand at the same template cell with the same state in Level II
  (`PokemonBuildsGameTests.eachPokemonJobUpgradeKeepsItsBase`). So Level I sits in the low-x, low-z corner of Level
  II's plot and Level II adds toward +x, +z and up.
- The composter and every chest stay in the same cell in Level II (builders never break containers,
  `MaterialRules.isProtected`), and the new beds stay inside the plot box round that composter.

## Fits
- **Plot** (`PokemonBuildsCompatTests.build`, both levels in the compat `huge_area`, **30 x 20 x 30**): the bench at
  helper (2, 2, 2), the builder at (3, 2, 3), supply barrels along z = 5 at x 1-6 (stacking upward); the build's origin
  at helper (min(9, 29 − width), 2, min(3, 29 − depth)), and the area needs 2 blocks spare round the build for its
  landscaping. So **Level II at most 19 wide (x) x 18 tall (y) x 25 deep (z)** (wider than 22 pushes the origin into the
  barrels; taller than 18 leaves the area). Level I at most Level II's size. `QaJobBuildsCompatTests` places both levels
  at helper (1, 1, 1) in the same area, which these limits also fit.
- y = 0 sits on the clicked block; the front is the z = 0 side.
- **Build time and block budget:** one builder alone, build delay 1, builders' help off, from barrels holding exactly
  its materials, the day set to 2,000: Level I must reach DONE with nothing skipped within **30,000 ticks**
  (`aBuilderBuildsTheBerryNursery`) and Level II within **60,000 ticks** (`aBuilderBuildsBerryNurseryII`). The builder
  stops at dusk (about 9,000 ticks after the test's start), so **each level must be buildable in one working day,
  about 9,000 ticks**: keep **Level I to about 600 placed blocks** (and over 150) and **Level II to about 1,000 placed
  blocks** in all, counting water and farmland (the old drawings are 233 and 682; earlier rebuilds had to cut a one-level
  workshop to about 1,000).
- **Declared sizes:** `StarterBlueprints.BERRY_NURSERY` (13, 6, 11) and `BERRY_NURSERY_2` (13, 12, 20) today; the ship
  step changes the `Vec3i`s to the new drawing (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).
- **Front and way in:** the z = 0 side faces the player who places it. No door is required; without one the builder's
  path starts one block in front of the middle of the z = 0 edge (`Paths.doorstep`'s fallback), so that spot should be
  ground a villager walks onto, leading in to the composter. Any wooden doorway leaves a villager 1.95 blocks of room
  (`QaDoorwayBugGameTests`, `QaDoorwayClearanceGameTests`).
- **Neighbours:** none planned. No Steward rule or village plan places the buildable.

## The village variant (`village/<style>_berry_nursery`, five styles)
Village houses share one contract read by template coordinate (`world/VillagePieces`, `world/VillageHouses`,
`village.py`'s `village_house` and `berry_nursery_room`, the shell in `village_looks.py`'s `berry_nursery`, today
12 x 11 x 11). The designer draws only the shell; every style keeps:
- **The street joint:** a `minecraft:jigsaw` at **(4, 1, 0)**, `north_up`, `minecraft:building_entrance`; the front
  row z = 0, y = 0, x 0-8 places nothing; no structure void left in the world
  (`VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`).
- **The front door at (4, 1, 2)** with a step before it at (4, 0, 1); **no trapdoor at z = 1, y 0-1**
  (`VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow`).
- **The room** template x 2-6, z 3-7, y ≤ 4 (`VillagePieces.ROOM_*`), and **the bed foot (6, 1, 6), head (6, 1, 7),
  facing south** (`VillagePieces` finds every village house of ours by that bed head).
- **The fit-out** (`berry_nursery_room`): the composter at **(2, 1, 6)** over a hidden water source at (2, 0, 6);
  **exactly 6 farmland cells** at y 0, (1, 3), (1, 4), (1, 5), (1, 6), (3, 5), (3, 6), all wet (moisture 7); one
  chest at **(1, 1, 2)** with `LootTable` `aliveworkplace:chests/village_berry_nursery`. The shell may add **no
  farmland in template x −2..6, y 0, z 2..7** (`VillageCompatTests.aVillageBerryNurseryComesWithItsBreeder` counts
  exactly 6 there, every one wet), **no other chest**, and **no other job block of any profession** (exactly one
  composter: `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses` "should have one Composter", one job block;
  `QaPokemonVillageHousesCompatTests.everyStyleOfEveryPokemonHouseHasOneJobBlockAndItsWorker`).
- **The villager:** exactly one `minecraft:villager` entity, profession `aliveworkplace:berry_breeder`, at (5, 1, 5),
  and no worker-pool spawn jigsaw. Placed, she must take that composter as her job site; exactly one villager by the
  house. The placing test clips at template x −1..10, y 0..11, z −1..11 (`VillageCompatTests.pokemonHouse`): the
  composter, the chest and the villager stand inside it. No structure void and no lava in the file.

## Rules tests check
Pinned to the drawing (the ship step moves coordinates to the new drawing, keeping what each checks, dropping none):
- `PokemonBuildsCompatTests.aBuilderBuildsTheBerryNursery`: built from exactly its materials within 30,000 ticks, every
  block right, none skipped; `minecraft:composter` at (5, 1, 8), a job site; `minecraft:farmland` at (2, 0, 2);
  `minecraft:water` at (3, 0, 2).
- `PokemonBuildsCompatTests.aBuilderBuildsBerryNurseryII`: the same within 60,000 ticks; composter at (5, 1, 8);
  farmland at (2, 0, 13) and at (10, 0, 16).
- `QaJobBuildsCompatTests.qaEachBuildsJobBlockTakesAVillagerWithItsItem`: both levels placed as drawn, a
  `minecraft:composter` job site at (5, 1, 8); the nearest standing spot within 3 blocks; an Oran Berry makes a Berry
  Breeder working at that composter. (`qaEachJobsJourneymanSellsTheirBuild`: by id only.)
- `PokemonBuildsScene` (dev client): the composter at (5, 1, 8) in both levels.
- `PokemonBuildsGameTests.eachPokemonJobUpgradeKeepsItsBase`: II ≥ I on every axis; I > 150 non-air blocks; ≥ 60 % kept.
- `QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`: every chest, barrel and job block
  walked to from outside.
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: size equals the declared size; every
  block buildable.
- `QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`,
  `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds` (a standing lantern, candle or torch stands on a
  block whose top holds it; no dirt path or farmland under a solid block), `QaChairFacingGameTests`,
  `QaDoorwayBugGameTests`, `QaDoorwayClearanceGameTests`, the library-wide pane and furnished-room checks
  (`B648GameTests`, `QaFurnishedRooms1031GameTests`, `FurnishedRoomsGameTests`), `QaMirroredBuildsGameTests`.
- Modded copies: `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`,
  `ModdedLibraryCompatTests.everyModdedCopyCanBeBuiltWithThePacksMods`, `QaCarvedLibraryCompatTests.everyModdedCopyKeepsItsJobSites`
  (the composter at the same cell): regenerate with `modded_library.py`.
- Refresh: once the old drawings go into `blueprint_history/`, `QaRefreshHistory1028GameTests.qaEveryShippedOlderDrawingIsOfferedARefresh`
  expects a building standing as the old drawing to be offered the new one (the new drawing must differ).
- Village variant: the tests listed in its section above.
- By id only (the drawing doesn't matter): `PokemonBuildsGameTests.thePokemonJobBuildsNeedCobblemon`,
  `thePokemonJobBuildsAreOnTheMap` (FARMS), `thePokemonJobBuildsAreTranslated`,
  `PokemonBuildsCompatTests.thePokemonJobBuildsAreInTheTable`, `ExpansionBuildsGameTests.laterExpansionsBuildsWaitForTheirSwitch`
  (`berryBreeders`), `QaBuiltAdvancements1116bGameTests.everyShippedBlueprintHasItsAdvancement`, `QaTableJobsGameTests`
  (the Table's jobs line: a composter never names the Berry Breeder).
- The owner's rules (`check.py`: posts at fence corners, lamps on tables or posts or hanging, crockery only by a stove or
  on a table with a chair, no bed right inside a door; `open_panes`), STYLE.md's checks and `holes.py`:
  `rebuild.py --candidate` prints no CHECK line, and `holes.py` passes. The ship step removes `berry_nursery*` from
  `owner_rules_baseline.json` if listed, keeps the old drawings in `blueprint_history/` (`history.py`), and regenerates
  the modded copies.

## Free
Everything not listed above: shape, plan, size within the plot, materials, colors, style, roof form, whether the beds
are open, fenced or under glass, how many beds beyond the pinned ones (within the plot box and the block budget), where
the composter, chest and beds stand relative to each other (within the reaches above), how the water is hidden or shown,
paths, seating, extra rooms, lighting, planting, and the village shell outside its fixed cells.
