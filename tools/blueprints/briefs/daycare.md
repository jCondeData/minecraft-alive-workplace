# Daycare: the brief

Family id(s): `daycare`, `daycare_2` (never renamed; "Daycare", "Daycare II" in `en_us.json`,
`blueprint.aliveworkplace.daycare*`)   Variants: village (`village/<style>_daycare` for `plains`, `desert`, `savanna`,
`snowy`, `taiga`; their own contract, see "The village variant"), modded (none to draw: `modded_library.py` makes
`structure/modded/daycare(_2).nbt` from the vanilla drawings; the ship step runs it again), looks (none)
Not this family: the **Rancher's daycare** (`ranch/Daycare`, `CobblemonDaycare`, the `daycare` showcase scene) is a
screen on a Rancher, not a building; nothing in it reads this blueprint.

## What it is for
The **Daycare Keeper**'s workplace (ROADMAP 28.12, 28.13), a Pokémon job: it exists only with Cobblemon installed
(`StarterBlueprints.COBBLEMON_ONLY`, `BlueprintLibrary`) and while the config switch `daycareKeepers` is on
(`StarterBlueprints.EXPANSION_SWITCH`). Its job block is **Cobblemon's Pasture Block** (`cobblemon:pasture`, the mod's
`PASTURE_POI`, one worker per block): a jobless villager standing by it, sneak-clicked by a player holding an **egg**,
becomes a Daycare Keeper with that Pasture Block as her job site (`work/Stations`, the Pasture station; a honey bottle
there makes a Habitat Keeper instead; nobody takes a Pasture Block by themselves). From then on
(`daycare/DaycareKeeperWork`) she walks to **within 3 blocks of her Pasture Block** and stays by it, looks in every 100
ticks, finds the eggs of the dawns passed and every minute "keeps the pairs company" there (a partner show cued at the
Pasture Block: hearts and a purr, `partner_shows/daycare_keeper_normal_keeps_the_pair_company.json`). The pairs
themselves (up to 3, one per player), the eggs and the screen live in her saved data and a list menu
(`DaycareKeepers`, `CobblemonDaycareKeeper`): **no Pokémon is placed in the world, no chest is read, no hay, water or
fence is read**. A player opens her screen by right-clicking her with an empty hand within 8 blocks. The blueprint is
sold by a **Journeyman Daycare Keeper** for 14 emeralds (`ModTrades.daycareKeeperTrades`,
`BlueprintUnlocks.VILLAGER_SALES`) and shows on the Village Map as a **CARE** building (`VillageMaps.kindOf`, by id). No
Steward rule builds it; the player sites it. Finishing each level grants `advancement/built/daycare(_2)`. In world
generation, villages (with Cobblemon and `pokemonVillageHouses` on) grow the village variant with a Daycare Keeper
already in it.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `daycare` | One Pasture Block (the job block) with a standing spot by it, reached on foot from the front | Sold by a Journeyman Daycare Keeper (14 emeralds); in the Table only with Cobblemon and `daycareKeepers` on |
| II | `daycare_2` | Level I grown: **a second Pasture Block**, a second job site, so a second keeper (3 more pairs) can work there | Its upgrade (`BlueprintUpgrades.upgradeOf`, by the `_2` id). No cost is read from the drawing |

No third level: `PokemonBuildsGameTests.eachPokemonJobUpgradeKeepsItsBase` requires exactly one upgrade per Pokémon job
build (upgrades x 2 == `JOB_BUILDS.size()`).

## Must have (each level)
- **Job block: `cobblemon:pasture`, two blocks tall** (its `part=bottom` half at the job cell, `part=top` right above;
  only the bottom half is the job site, `ModVillagers.PASTURE_POI`).
  - **Level I has exactly one; Level II has exactly two.** Level I's stays **at the same template cell in Level II**
    (today (7, 1, 12)): `QaJobBuildsCompatTests` and the dev-client `PokemonBuildsScene` hold one cell for both tiers,
    and `aBuilderBuildsDaycareII` pins both of II's (today (7, 1, 12) and (7, 1, 20)).
  - Each is a job site when built (`PokemonBuildsCompatTests.build`: the first expected block "is no job site"; the
    Pasture Block must be the first one listed).
  - **A standing spot within 3 blocks** (x and z within ±3, y within ±1) of the pinned Pasture Block: air at feet and
    head with no collision and no fluid, on a block whose top face is sturdy (a bottom slab, a carpet on air, a dirt
    path, farmland and water don't count). The test puts a villager on the *nearest* such spot and hands her an egg;
    she must become a Daycare Keeper working at *this* Pasture Block (`QaJobBuildsCompatTests.qaEachBuildsJobBlockTakesAVillagerWithItsItem`,
    both levels). An egg picks the **nearest free** Pasture Block within 4.5 blocks of the villager (`Stations.REACH`),
    so **in Level II the second Pasture Block must be farther from that spot than the first** (keep the two well
    apart; each needs its own standing spot).
  - **Reachable on foot from the ground outside**: within 2 across and 1 up or down of a walked cell
    (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`); the keeper walks to within 3 blocks
    of hers (`DaycareKeeperWork.REACH`) and players come to her to open the screen.
  - **No other job block of any job** anywhere in either level. Note: a **barrel** is a vanilla Fisherman's job site
    and a composter a Farmer's (the old drawing had barrels; no buildable test forbids them, but each pulls in another
    job). Containers that should hold nothing for a job: use chests.
- **Beds and residents: none.** No code counts beds here; the keeper comes from the village (a jobless villager given
  an egg). A bed would make it count as a home. (The village variant has one: see below.)
- **Storage: none required.** The keeper reads no container. Any chest drawn is the player's: it stands against a wall,
  a solid block or a bed indoors (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`), is walked to
  from outside (`QaBuildReachGameTests`), drawn empty, and stays in the same cell in Level II.
- **Furniture kinds a job or test needs:** none. There is no `blueprint_furniture/daycare*.json` today; if the recipe
  records furniture spots, each spot's blocks must be exactly the template's (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`),
  no spot covers the Pasture Block or a container (`FurnishedWorkplacesGameTests.noSpotCoversAJobBlockABedOrAContainer`),
  seats face their tables (`QaChairFacingGameTests`), nothing small stands on air (`FloatingFurnishingsGameTests`) and no
  standing lantern on a room's floor (`QaFurnishedRooms1031GameTests.noLanternStandsOnARoomsFloor`).
- **No entities** in the drawing (no villager, Pokémon, frame, painting, armor stand), no jigsaw or structure void, and
  every block one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).
  Every water cell is a source the builder pours, held in on all sides and below.

## Kept cells (the upgrade)
- Level II has the same origin and front as Level I and is at least its size on every axis; **Level I has more than 150
  non-air blocks**, and **at least 60 %** of them stand at the same template cell with the same state in Level II
  (`PokemonBuildsGameTests.eachPokemonJobUpgradeKeepsItsBase`). So Level I sits in the low-x, low-z corner of Level
  II's plot and Level II adds toward +x, +z and up.
- Level I's Pasture Block (both halves) and every chest stay in the same cell in Level II (builders never break
  containers or job blocks, `MaterialRules.isProtected`).

## Fits
- **Plot** (`PokemonBuildsCompatTests.build`, both levels in the compat `huge_area`, 30 x 20 x 30): the bench at
  helper (2, 2, 2), the builder at (3, 2, 3), supply barrels along z = 5 at x 1-6 (stacking upward); the build's origin
  at helper (min(9, 29 − width), 2, min(3, 29 − depth)), with room round the build for its landscaping. So **Level II
  at most 19 wide (x) x 18 tall (y) x 27 deep (z)** (today's 19 x 11 x 27 passes; wider than 20 pushes the origin
  toward the barrels, deeper than 27 leaves no landscaping room, taller than 18 leaves the area). Level I at most Level
  II's size. `QaJobBuildsCompatTests` places both levels at helper (1, 1, 1) in the same area, which these limits fit.
- y = 0 sits on the clicked block; the front is the z = 0 side.
- **Build time and block budget:** one builder alone, build delay 1, builders' help off, from barrels holding exactly
  its materials, the day set to 2,000: Level I must reach DONE with nothing skipped within **30,000 ticks**
  (`aBuilderBuildsTheDaycare`) and Level II within **60,000 ticks** (`aBuilderBuildsDaycareII`). The builder stops at
  dusk (about 9,000 ticks after the start), so keep **Level I to about 600 placed blocks** (and over 150) and **Level
  II to about 1,000** in all, counting water (the old drawings are 448 and 735).
- **Declared sizes:** `StarterBlueprints.DAYCARE` (15, 11, 18) and `DAYCARE_2` (19, 11, 27) today; the ship step changes
  the `Vec3i`s to the new drawing (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).
- **Front and way in:** the z = 0 side faces the player who places it. No door is required; without one the builder's
  path starts one block in front of the middle of the z = 0 edge (`Paths.doorstep`'s fallback), so that spot should be
  ground a villager walks onto, leading in to the Pasture Block(s). Any doorway leaves a villager 1.95 blocks of room
  (`QaDoorwayBugGameTests`, `QaDoorwayClearanceGameTests`); a gate or door on the way in must be one a villager opens
  (a wooden door or a fence gate).
- **Neighbours:** none planned. No Steward rule or village plan places the buildable.

## The village variant (`village/<style>_daycare`, five styles)
Village houses share one contract read by template coordinate (`world/VillagePieces`, `world/VillageHouses`,
`village.py`'s `village_house` and `daycare_room`, the shell in `village_looks.py`'s `daycare`, today 12 x 12 x 11).
The designer draws only the shell; every style keeps:
- **The street joint:** a `minecraft:jigsaw` at **(4, 1, 0)**, `north_up`, `minecraft:building_entrance`; the front
  row z = 0, y = 0, x 0-8 places nothing; no structure void left in the world
  (`VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`).
- **The front door at (4, 1, 2)** with a step before it at (4, 0, 1); **no trapdoor at z = 1, y 0-1**
  (`VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow`).
- **The room** template x 2-6, z 3-7, y ≤ 4 (`VillagePieces.ROOM_*`), and **the bed foot (6, 1, 6), head (6, 1, 7),
  facing south** (`VillagePieces` finds every village house of ours by that bed head).
- **The fit-out** (`daycare_room`): the Pasture Block at **(1, 1, 6)** (bottom) and (1, 2, 6) (top); **one chest at
  (3, 1, 6)** with `LootTable` `aliveworkplace:chests/village_daycare`. The rest of `daycare_room` (hay floor, nest,
  lantern post, pot) is read by no test. The shell may add **no other chest** (`VillageCompatTests.pokemonHouse`: exactly
  1 chest, its loot table loads) and **no other job block of any profession** (exactly one Pasture Block, one job
  block: `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`, `VillageCompatTests.aVillageDaycareComesWithItsKeeper`,
  `QaPokemonVillageHousesCompatTests.everyStyleOfEveryPokemonHouseHasOneJobBlockAndItsWorker`).
- **The villager:** exactly one `minecraft:villager` entity, profession `aliveworkplace:daycare_keeper`, at (5, 1, 5),
  and no worker-pool spawn jigsaw. Placed, she must take that Pasture Block as her job site, settle 100 ticks unhurt,
  overlap no block, and stay inside the house at most 2 above its floor; exactly one villager by the house. The placing
  test clips at template x −1..10, y 0..11, z −1..11 (`VillageCompatTests.pokemonHouse`): the Pasture Block, the chest
  and the villager stand inside it. No structure void and no lava in the file.

## Rules tests check
Pinned to the drawing (the ship step moves coordinates to the new drawing, keeping what each checks, dropping none):
- `PokemonBuildsCompatTests.aBuilderBuildsTheDaycare`: built from exactly its materials within 30,000 ticks, every block
  right, none skipped; `cobblemon:pasture` at (7, 1, 12), a job site; `minecraft:hay_block` at (7, 0, 4) (a pin of
  the old drawing only: the ship step points it at any block of the new one).
- `PokemonBuildsCompatTests.aBuilderBuildsDaycareII`: the same within 60,000 ticks; `cobblemon:pasture` at (7, 1, 12)
  (a job site) **and** at (7, 1, 20); `minecraft:hay_block` at (14, 1, 5) (again only a pin of something new in II).
- `QaJobBuildsCompatTests.qaEachBuildsJobBlockTakesAVillagerWithItsItem`: both levels placed as drawn, a
  `cobblemon:pasture` job site at (7, 1, 12); the nearest standing spot within 3 blocks; an egg makes a Daycare Keeper
  working at that Pasture Block. (`qaEachJobsJourneymanSellsTheirBuild`: by id only.)
- `PokemonBuildsScene` (dev client): the Pasture Block at (7, 1, 12) in both levels.
- `PokemonBuildsGameTests.eachPokemonJobUpgradeKeepsItsBase`: II ≥ I on every axis; I > 150 non-air blocks; ≥ 60 % kept.
- `QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`: every chest, barrel and job block
  walked to from outside.
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: size equals the declared size; every
  block buildable.
- `QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`,
  `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds` (a standing lantern, candle or torch stands on a
  block whose top holds it; no dirt path or farmland under a solid block), `QaChairFacingGameTests`,
  `QaDoorwayBugGameTests`, `QaDoorwayClearanceGameTests`, the library-wide pane and furnished-room checks
  (`B648GameTests`, `QaWindowsAndWards1028BugTests`, `QaFurnishedRooms1031GameTests`, `FurnishedRoomsGameTests`),
  `QaMirroredBuildsGameTests`, `QaTurnedFramesGameTests`.
- Modded copies: `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`,
  `ModdedLibraryCompatTests.everyModdedCopyCanBeBuiltWithThePacksMods`, `QaCarvedLibraryCompatTests.everyModdedCopyKeepsItsJobSites`
  (the Pasture Blocks at the same cells): regenerate with `modded_library.py`.
- History and refresh: `blueprint_history/daycare*/cd1557d8c.nbt` (and `modded/daycare*`) stay
  (`QaReleaseRefresh1028GameTests`, `QaModdedClinicHistoryBugTests` name them); the ship step adds the current drawings
  (`history.py`), and `QaRefreshHistory1028GameTests.qaEveryShippedOlderDrawingIsOfferedARefresh` expects a building
  standing as an old drawing to be offered the new one (the new drawing must differ).
- Village variant: the tests listed in its section above.
- By id only (the drawing doesn't matter): `PokemonBuildsGameTests.thePokemonJobBuildsNeedCobblemon`,
  `thePokemonJobBuildsAreOnTheMap` (Daycare II is CARE), `thePokemonJobBuildsAreTranslated`,
  `PokemonBuildsCompatTests.thePokemonJobBuildsAreInTheTable`, `ExpansionBuildsGameTests` (`daycareKeepers`),
  `QaBuiltAdvancements1116bGameTests.everyShippedBlueprintHasItsAdvancement`,
  `QaServiceJobs1031GameTests` (the Daycare blueprint at Journeyman for 14 emeralds).
- The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hanging, crockery only by
  a stove or on a table with a chair, no bed right inside a door; `open_panes`), STYLE.md's checks and `holes.py`:
  `rebuild.py --candidate` prints no CHECK line for either level, and `holes.py` passes. The ship step removes
  `daycare*` from `owner_rules_baseline.json` if listed and regenerates the modded copies.

## Free
Everything not listed above: shape, plan, size within the plot, materials, colors, style, roof form, whether there is a
barn, nursery, hatchery, paddock, fence, trough, hay or water at all (no code reads any of them), how the two Pasture
Blocks of Level II are set out (within the rules above), chests or none, paths, seating, extra rooms, lighting,
planting, and the village shell outside its fixed cells.
