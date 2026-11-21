# Camp Kitchen: the brief

Family id(s): `camp_kitchen`, `camp_kitchen_2` (never renamed)   Variants: village (`village/<style>_camp_kitchen` for
`plains`, `desert`, `savanna`, `snowy`, `taiga`; their own contract, see "The village variant"), modded (none to draw:
`modded_library.py` makes `structure/modded/camp_kitchen(_2).nbt` from the vanilla drawings; the ship step runs it
again), looks (none)

## What it is for
The Camp Cook's workplace (ROADMAP 28.8, 28.13), a Pokémon job: it exists only with Cobblemon installed. Its heart is
Cobblemon's **Campfire Pot** (the block `cobblemon:campfire` with a pot fitted on it). A jobless villager standing by
the pot, sneak-clicked by a player holding Hearty Grains, becomes a Camp Cook with that pot as her job site
(`Stations.choose`, the `CAMPFIRE_POT_POI` station). From then on she cooks in the pot itself (`CampCookWork`): she takes
the makings for the next dish on her menu out of **the chests by the pot**, fills the pot's grid, shuts the lid, waits,
and puts the dish back into those chests (or takes it to a Storehouse that ordered it). Without a chest near the pot she
stands idle ("no_chest"). The blueprint is sold by a Journeyman Camp Cook for 12 emeralds (`ModTrades`;
`BlueprintUnlocks.VILLAGER_SALES`), is offered only while the config switch `campCooks` is on
(`StarterBlueprints.EXPANSION_SWITCH`), and shows on the Village Map as a FARMS building (`VillageMaps`, by id). In
world generation, villages (with Cobblemon) grow the village variant with a Camp Cook already in it.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `camp_kitchen` | The Campfire Pot (the job block), the cook's chest(s) by it, a store of Hearty Grain bales | Sold by the Camp Cook's Journeyman (12 emeralds); in the Table only with Cobblemon (`COBBLEMON_ONLY`) and `campCooks` on |
| II | `camp_kitchen_2` | Level I grown: a **Hearty Grain plot** (ripe Hearty Grains on farmland) and a **smokehouse with a smoker** | Its upgrade (`BlueprintUpgrades.upgradeOf`, by the `_2` id); unlocked as an upgrade (`BlueprintUnlocks`: tier 2). No cost is read from the drawing |

No third level: `PokemonBuildsGameTests.eachPokemonJobUpgradeKeepsItsBase` requires exactly one upgrade per Pokémon job
build, and `BuilderGameTests` counts the starter upgrades.

## Must have (each level)
- **Job block: one Campfire Pot, at the same template cell in both levels.**
  - Drawn as `cobblemon:campfire` (`facing` any, `lid=false`). It has no item of its own: the builder places a campfire
    and fits a red pot on it (`ModdedBlocks.ownCosts` / `afterPlaced`), and it then costs one campfire and one
    `cobblemon:campfire_pot_red`. The finished pot must have its pot on (`PokemonBuildsCompatTests.build`).
  - It must be a job site when built (a POI): `cobblemon:campfire` is registered as `CAMPFIRE_POT_POI`.
  - **The same cell in Level I and Level II** (today template (6, 1, 5)): `QaJobBuildsCompatTests` and
    `PokemonBuildsScene` hold one cell for both tiers, and Level II keeps Level I's blocks where they were.
  - **A standing spot within 3 blocks** (x, z within ±3, y within ±1) of the pot: air at feet and head with no
    collision and no fluid, on a block whose top is sturdy. The test puts the villager on the nearest such spot and
    hands him Hearty Grains; he must take the job at *this* pot (`QaJobBuildsCompatTests.qaEachBuildsJobBlockTakesAVillagerWithItsItem`).
  - **Reachable on foot from the ground outside**, within 2 across and 1 up or down of a walked cell
    (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`), and the cook must be able to get her
    eyes within **3.0 blocks** of it from a cell she can walk to (`CampCookWork.REACH`, `Walker.reach`).
  - One pot only: the cook works one pot; a second Campfire Pot would be a second Camp Cook's job site.
- **Storage: at least one chest by the pot** (`CampCookWork` → `SupplyContainers.find`): within **8 blocks across**
  (x and z each) and **4 up or down** of the pot. These hold her makings and her dishes; a furnace or smoker never
  counts as one. Every chest and barrel can be walked to from outside (`QaBuildReachGameTests`). Indoor chests stand
  against a wall (library-wide furnished-room checks).
- **Hearty Grain bales** (`cobblemon:hearty_grain_bale`): at least one, at a spot the build test pins (today (3, 1, 7)
  in Level I, kept in Level II) (`PokemonBuildsCompatTests.aBuilderBuildsTheCampKitchen`).
- **Level II adds:**
  - **A smoker** (`minecraft:smoker`) at a pinned spot (today (16, 1, 7)) (`aBuilderBuildsCampKitchenII`).
  - **A Hearty Grain plot:** ripe `cobblemon:hearty_grains` (age 6, lower half at the pinned spot, upper half above)
    standing on `minecraft:farmland` (today grains at (2, 1, 12) on farmland at (2, 0, 12)) (`aBuilderBuildsCampKitchenII`).
    Farmland must stay farmland after the build, so every farmland cell has water within vanilla's 4-block hydration
    reach; every water cell is a source the builder pours, held in on all sides and below.
- Beds and residents: **none** in the buildable levels. No code counts beds here; the cook comes from the village (a
  jobless villager given Hearty Grains). Beds would make it a home. (The village variant has one bed: see below.)
- Furniture kinds a job or test needs: seats are not required by any code. There is no
  `blueprint_furniture/camp_kitchen*.json` today; if the recipe records furniture spots, each spot's blocks must be
  exactly the template's there (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`).
- Anything else the code reads: nothing (no bell, sign, lectern or door is read). Note for the designer: barrels and
  the smoker are vanilla job sites (fisherman, butcher). No test forbids them in the buildable levels, but each one can
  pull a jobless villager to a vanilla job; the Level II smoker is required anyway.
- No entities in the drawing (no villager, frame, painting, armor stand) and no block a builder can't place
  (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`; blueprint data never carries free items
  or mobs).

## Fits
- **Plot** (`PokemonBuildsCompatTests.build`, both levels in the 30 x 30 x 30 `huge_area`; the bench at helper (2, 2, 2)
  and supply barrels along z = 5 at x 1-6; origin at helper (min(9, 29 - width), 2, min(3, 29 - depth)), and the area
  needs 2 blocks spare round the build for its landscaping): Level II at most **20 wide (x) x 27 tall (y) x 24 deep
  (z)** (21 wide today pushes the origin to x 8, which still clears the barrels; wider than 22 runs into them). Level
  I at most Level II's size. y = 0 sits on the clicked block; the front is the z = 0 side.
- **Level II grows out of Level I at the same origin**: Level II is at least as big as Level I on every axis, and at
  least **60 %** of Level I's non-air blocks stand **at the same template cell with the same state** in Level II; Level I
  has **more than 150** non-air blocks (`PokemonBuildsGameTests.eachPokemonJobUpgradeKeepsItsBase`). So Level I sits in
  the low-x, low-z corner of Level II's plot, and Level II adds toward +x and +z (and up), not round all sides.
- **Build time:** one builder alone, build delay 1, from barrels holding exactly its materials, finishes Level I within
  **30,000 ticks** and Level II within **60,000 ticks**, with nothing skipped.
- Declared sizes: `StarterBlueprints.CAMP_KITCHEN` (13, 10, 11) and `CAMP_KITCHEN_2` (21, 10, 19) today; the ship step
  changes the `Vec3i`s to the new drawing.
- Front and way in: the z = 0 side faces the player who places it. No door is required; without one the builder's path
  starts one block in front of the middle of the z = 0 edge (`Paths.doorstep`'s fallback), so that spot should be
  ground a villager walks onto, leading in to the pot. Any wooden door leaves a villager 1.95 blocks of room over its
  step (`QaDoorwayBugGameTests.everyListedBuildsDoorwaysLeaveAVillagerRoomOverTheStep`).
- Neighbours: none planned. No Steward rule or village plan places the buildable; the player sites it.

## The village variant (`village/<style>_camp_kitchen`, five styles)
Village houses share one contract read by template coordinate (`world/VillagePieces`, `world/VillageHouses`,
`village.py`'s `village_house` and `camp_kitchen_room`, `village_looks.py`'s `camp_kitchen` shell). Every style keeps:
- **Size:** today 12 x 11 x 11 (`village_looks.py` `DESIGNS`). The placing test clips at template x -1..10, y 0..11,
  z -1..11 (`VillageCompatTests.pokemonHouse`): the pot, the chest and the villager must be inside that box.
- **The street joint:** a `minecraft:jigsaw` at **(4, 1, 0)**, `north_up`, `minecraft:building_entrance`; the front
  row z = 0, y = 0, x 0-8 places nothing (stays the ground); no structure void left in the world
  (`VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`).
- **The front door at (4, 1, 2)** with a step before it at (4, 0, 1); **no trapdoor at z = 1, y 0-1**
  (`VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow`).
- **The room** template x 2-6, z 3-7, y ≤ 4 (`VillagePieces.ROOM_*`), and **the bed foot (6, 1, 6), head (6, 1, 7),
  facing south**: `VillagePieces` finds every village house of ours by that bed head, so it must stay exactly there.
- **The fit-out:** exactly **one** Campfire Pot (`cobblemon:campfire` with block-entity NBT `PotComponent` =
  `cobblemon:campfire_pot_red`, so it has its pot on when placed) and **no other job block of any profession** (no
  barrel, smoker, composter, crafting table...) (`QaPokemonVillageHousesCompatTests.everyStyleOfEveryPokemonHouseHasOneJobBlockAndItsWorker`,
  `VillageCompatTests.aVillageCampKitchenComesWithItsCook`, and without Cobblemon
  `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`: exactly one `cobblemon:campfire` block by name and zero job
  blocks). Exactly **one chest**, with `LootTable` `aliveworkplace:chests/village_camp_kitchen`. Today: pot (3, 1, 7),
  chest (4, 1, 7).
- **The villager:** exactly **one** `minecraft:villager` entity in the file, profession `aliveworkplace:camp_cook`
  (today at (5, 1, 5)), and **no** worker-pool spawn jigsaw. Placed, she must take that pot as her job site; exactly
  one villager by the house. No structure void and no lava in the file.

## Rules tests check
Pinned to the drawing (the ship step moves coordinates to the new drawing, keeping what each checks, dropping none):
- `PokemonBuildsCompatTests.aBuilderBuildsTheCampKitchen`: built from exactly its materials, every block right, none
  skipped; `cobblemon:campfire` at (6, 1, 5), a job site with its pot on; `cobblemon:hearty_grain_bale` at (3, 1, 7).
- `PokemonBuildsCompatTests.aBuilderBuildsCampKitchenII`: the same, plus `minecraft:smoker` at (16, 1, 7),
  `cobblemon:hearty_grains` at (2, 1, 12) and `minecraft:farmland` at (2, 0, 12).
- `QaJobBuildsCompatTests.qaEachBuildsJobBlockTakesAVillagerWithItsItem`: both levels placed as drawn, a
  `cobblemon:campfire` job site at (6, 1, 5); the nearest standing spot within 3 blocks; Hearty Grains make a Camp Cook
  working at that pot. (`qaEachJobsJourneymanSellsTheirBuild`: by id only.)
- `PokemonBuildsScene` (dev client): the pot at (6, 1, 5) in both levels.
- `PokemonBuildsGameTests.eachPokemonJobUpgradeKeepsItsBase`: II ≥ I on every axis; I > 150 non-air blocks; ≥ 60 % kept.
- `QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`: every chest, barrel and job block
  walked to from outside.
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: size equals the declared size; every
  block buildable.
- `QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`,
  `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds` (a standing lantern, candle or torch stands on a
  block whose top holds its middle; no dirt path or farmland under a solid block), `QaChairFacingGameTests` (both: a
  seat beside a table opens toward it, turned and mirrored), `QaDoorwayBugGameTests` (doorways 1.95 tall).
- Modded copies: `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`,
  `ModdedLibraryCompatTests.everyModdedCopyCanBeBuiltWithThePacksMods` (same size as the vanilla drawing),
  `QaCarvedLibraryCompatTests.everyModdedCopyKeepsItsJobSites` (the pot at the same cell): regenerate with
  `modded_library.py`.
- Refresh: once the old drawings go into `blueprint_history/`, `QaRefreshHistory1028GameTests.qaEveryShippedOlderDrawingIsOfferedARefresh`
  expects a building standing as the old drawing to be offered the new one (the new drawing must differ).
- Village variant: the tests listed in its section above.
- By id only (the drawing doesn't matter): `PokemonBuildsGameTests.thePokemonJobBuildsNeedCobblemon`,
  `thePokemonJobBuildsAreOnTheMap` (FARMS), `thePokemonJobBuildsAreTranslated` ("Camp Kitchen II"),
  `PokemonBuildsCompatTests.thePokemonJobBuildsAreInTheTable`, `ExpansionBuildsGameTests.laterExpansionsBuildsWaitForTheirSwitch`
  (`campCooks`), `QaBuiltAdvancements1116bGameTests.everyShippedBlueprintHasItsAdvancement`
  (`advancement/built/camp_kitchen(_2).json`), `QaPokemonVillageHousesCompatTests` / `VillageCompatTests` pool listings.
- The owner's rules (`check.py`: posts at fence corners, lamps on tables or posts or hanging, crockery only by a stove,
  furnace, smoker or counter or on a table with a chair), STYLE.md's checks and `holes.py`: `rebuild.py --candidate`
  prints no CHECK line, and `holes.py` passes.

## Free
Everything not listed above: shape, plan, size within the plot, materials, colors, style, roof form, whether the
kitchen is open or walled, where the pot, chests, bales, smokehouse and plot stand relative to each other (within the
reaches above), seating, extra rooms, lighting, ground and planting, and the village shell outside its fixed cells.
