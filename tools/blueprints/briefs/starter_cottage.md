# Starter Cottage: the brief

Family id(s): `starter_cottage`, `starter_cottage_2`, `starter_cottage_3`, `starter_cottage_4` (never renamed)
Variants: village (none drawn: a village's style, `dark_oak`, `sandstone`, `cherry`, `spruce`, `stonework`, `grand`,
`apricorn` with Cobblemon, is applied to the drawing automatically by `BlueprintStyles`, and several tests build or check
the styled cottage, see "Blocks the tests name"), modded (none to draw: `modded_library.py` makes
`structure/modded/starter_cottage*.nbt` from the vanilla drawing and the ship step reruns it), looks (none).

A flagship family: two designers (A and B) work from this one brief.

## What it is for
The first home of every village. A player takes it from the Blueprint Table (it is in every Table's starting set) or
buys it from a Novice Builder, hands it to a Builder, and a villager moves in: its bed is a home, and the Table shows
what the house brings ("Jobs: Fisherman (1), Carpenter (1)", "Beds: 1"). The Steward builds one by himself when a
hamlet is exactly one bed short; a new colony's builder builds one first, beside the camp. It is furnished room by
room (37.4): with a furniture mod installed, each furniture spot is built as that mod's piece, without one as the
vanilla stand-in. Each level is an upgrade on the same spot and origin: the builder builds only what changed, and the
family grows from a one-bed starter home to a two-bed house, then a house with a kitchen, then one with a cellar.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `starter_cottage` | A home: exactly **1 bed**; exactly two job blocks, **one barrel and one crafting table**; a chest; a chimney with a campfire on it; every furnished-room kind (below) | In every Table from the start (`blueprint_unlocks/starting.json`); sold by a Novice Builder (`ModTrades`); in settlers' wagon and village chest loot; a colony's first build (`ColonyArrival`); the Steward's `homes_starter_cottage` rule builds it in a `homes` zone when the village is exactly 1 bed short, below Village rank, and no finished home's upgrade adds beds |
| II | `starter_cottage_2` | **More beds than I** (the Steward's `homes_upgrade` and the Hall's Upgrades page answer "beds short" with an upgrade only when the next level has more beds, `StewardConditions.addsBeds`); a chimney campfire high up (below); keeps I | Upgrade of `starter_cottage` (`BlueprintUpgrades`: id + `_2`); a questbook reward for building I (`BlueprintUnlocks.QUESTBOOK`; without FTB Quests, sold by a builder once I stands); the Master Architect picks it to rebuild a finished I |
| III | `starter_cottage_3` | **The same number of beds as II** (no more); a kitchen with a **smoker** in floor space **outside II's footprint**, reached through a **new doorway knocked through one of II's outside walls**, with **its own outside door**; keeps II | Upgrade of `_2`; questbook reward for building II (or sold by a builder once II stands) |
| IV | `starter_cottage_4` | A **cellar dug under the house, exactly 4 blocks deep** (`StarterBlueprints.Entry` basement 4): a closed room with a way down from inside and a light; keeps III | Upgrade of `_3`; a builder who finished a III sells it (`UpgradeOffers`, `BasementGameTests.basementsLoadUnderTheGround`) |

The upgrade count in `starterUpgradesKeepMostOfTheirBase` (64) must not change: all four levels stay.

## Must have (each level)
- **Job blocks.** Level I: exactly one barrel (Fisherman) and one crafting table (Carpenter) and **no other job block of
  any kind** (no smoker, cauldron, composter, lectern, loom, stonecutter, grindstone, brewing stand, blast furnace,
  fletching/smithing/cartography table, beehive, jukebox, or any of the mod's job blocks): the Table must read exactly
  "Jobs: Fisherman (1), Carpenter (1)" (`QaTableJobsGameTests.theStarterCottageShowsTheOwnersExample`). The order matters:
  the Table lists job blocks in the order the game lists the template's blocks, which is, among blocks saved without
  block-entity data, lowest y first, then lowest x, then lowest z (blocks saved with block-entity data come after all
  the others). So the barrel must come before the crafting table in that order; check it by running the test.
  A furnace or chest is not a job block. Levels II-IV: no job block is required by code except III's smoker; any others
  are free, but every job block of a level stays in the same cell in every later level (see "Kept cells").
- **Beds and residents.** Count is bed heads (`StewardConditions.beds`, `BuildJobs`). I: exactly 1. II: at least 2 (more
  than I). III: exactly II's count. IV: free, but every earlier bed stays. Each bed is a villager's home: it lies inside
  the build's footprint (`Homes.of` gives the bed's villager this house). At least one bed of Level I has air over its
  head cell and a sturdy floor under it (a villager can stand at its head while it is built: `B92GameTests.b92ABedIsNeverPutDownOnAVillagerStandingAtItsHead`).
  Level I's bed is a red bed and the bed II adds is a light blue bed (tests name the block, see below).
- **Storage.** Level I holds at least one chest (the modded copy turns it into the house's wood's Carved Wood chest,
  `ModdedLibraryCompatTests`). No job or test reads a container's contents here; more storage is free.
- **Furniture spots** (`blueprint_furniture/<id>.json`, written by the recipe's `furniture.json` through `rebuild.py`).
  Every level has at least one spot of each of these kinds: **bed, chair, table, nightstand, lamp, couch, shelf, crockery,
  clock, picture, blinds** (`FurnishedHomesGameTests.everyHomeHasItsFurnishedRooms`, all four ids).
  - Level I has **exactly two chair spots** (`FurnitureAnyModCompatTests`: both chairs become Handcrafted's or
    CobbleFurnies'), at least one table and one bed spot.
  - Every Level I spot's wood is **spruce** (`FurnitureSpotGameTests.starterCottageSpotsAreWhereItsChairsAndTablesAre`).
  - Each chair spot is the vanilla chair stand-in (a stair) whose sitter faces a table spot directly in front of it, and
    each table spot's first block is the table stand-in (a fence) (same test).
  - The first chair spot has a table or desk spot within 3 blocks across and 1 up or down (the Trainer's room with
    CobbleFurnies, `TrainersRoomCompatTests`: "a chair and a desk").
  - A villager can step up onto a chair: at least 2 blocks clear over the seat (no blind or shelf over it;
    `DoorwayHeadroomGameTests.aVillagerSitsDownOnTheStarterCottagesChairUnderTheWindow`, and check.py's headroom check).
  - Each spot's blocks are exactly the template's blocks there (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`).
- **Chimney campfire.** Level I has a campfire standing on a stack of full solid blocks (a chimney), and that stack stays
  full and solid in every style (`StyleGameTests.chimneysStayWholeInEveryStyle`: "no chimney found in starter_cottage").
  Level II has a campfire at template **y > 10** on such a stack, which a builder can place **from somewhere he can stand
  on the build itself** (its roof), within his 5.5-block reach, with no scaffolding (`ChimneyCampfireGameTests.builderPutsTheCampfireOnATallChimneyFromTheRoof`).
  A campfire is not protected, so an upgrade may move it up.
- **Level IV's cellar** (`BasementGameTests`, `QaBasementSeals1107bGameTests`, `QaBasementGround1104GameTests`, the
  `QaBasementFluids*` tests):
  - drawn at the bottom of the file, its floor at file y = 0 and the ground floor at y = 4; exactly 4 deep
    (`QaBlueprintTableGameTests`: the preview says "4 below ground");
  - at least one `minecraft:ladder` or stairs block below y = 0 (the way down) and at least one `minecraft:lantern` below
    y = 0 (`basementsLoadUnderTheGround`);
  - a closed room: every below-ground cell of the build is room (air), wall, floor or furniture, nothing outside its
    walls below ground is part of the build, and every air cell is dug out (aquifer, lava and cave tests put water, lava
    and caves just outside the walls and under the floor; the builder must finish it dry with nothing burned);
  - the way down opens from inside the house.
- **A door.** Level I has a door (the see-through preview must show a door's top half,
  `BuilderGameTests.previewCarriesTheBlocksToBuild`), on the lowest floor near an edge (a builder's path to the village
  starts outside the door nearest an edge, `Paths.doorstep`).

## Kept cells (every upgrade)
- Each level is at least the size of the one below in x, y and z, has the same origin and front, and keeps **at least
  60 %** of the level below's non-air blocks at the same position in exactly the same state
  (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`). Level IV's above-ground size equals III's.
- **Every block with block-entity data in a level (chest, barrel, furnace, smoker, ...: anything but beds, campfires,
  signs, banners, skulls and bells) stays in the same cell as the same block in every later level.** A builder never
  breaks those while clearing (`MaterialRules.isProtected`), so an upgrade that moves one can't be finished.
- Every bed stays in its cell through every later level (the upgrade tests check Level I's bed and II's bed are still
  there after II, III and IV).
- What an upgrade must build: Level II places **under 80 %** of its structure and decoration steps over a finished I
  (`BuilderGameTests.builderUpgradesAFinishedCottage`); III places **under 40 %** of its own over a finished II
  (`builderGrowsACottageSideways`); IV under 80 % over a finished III (`levelFourDigsAndFinishesItsBasement`).
- What an upgrade still needs fits **27 stacks** (one chest) for II and III, and two chests for II with 16 scaffolding
  (`QaBuilderUpgrade1116bGameTests`). Level II needs at least one item Level I contains none of (that test withholds it),
  and Level II's plan has at least 60 structure steps.

## Fits
- Plot (max width x depth x height, from the test areas that place each level by coordinates; the ship step moves the
  coordinates, not the areas):
  - Level I: **13 x 13 x 16** (`BuilderGameTests.buildsStarterCottage` at (9, 2, 9) in the 22 x 18 x 22 `big_area`;
    also `QaBuilderDisruptionGameTests`, `QaBuilderSuppliesGameTests`). For `ColonyArrivalGameTests` the cottage's centre
    ends at least 10 blocks from the camp's middle, so a Level I much narrower than about 7 risks it.
  - Level II: **14 x 14 x 16** (`builderUpgradesAFinishedCottage` at (8, 2, 8) in `big_area`;
    `QaBuilderUpgrade1116bGameTests` puts the cottage at x 1.. with the builder's bench at x 16).
  - Level III: **19 x 16 x 16** (`builderGrowsACottageSideways` at (3, 2, 6) in `big_area`).
  - Level IV: III's size above the ground plus the 4-deep cellar.
  - y = 0 sits on the clicked block; the front is the z = 0 side.
- Material budget: Level I's whole list is **more than 27 stacks** (the builder's bag must not hold it all:
  `QaBuilderSuppliesGameTests.onABuildBiggerThanTheBagItNeverHoldsMoreThan27Stacks`) and **at most 108 stacks**
  (`buildsStarterCottage` stocks four barrels), counting each item rounded up to whole stacks.
- Build time: Level I is built from nothing within **9,000 ticks** (`buildsStarterCottage`), 12,000 with the disruption
  and supplies tests; II and III upgrades within 14,000; IV within 24,000. A much bigger Level I risks these.
- Declared sizes: `StarterBlueprints.STARTER_COTTAGE` (`11, 12, 10`), `_2` (`11, 16, 11`), `_3` (`17, 16, 11`), `_4`
  (`17, 16, 11`, basement 4) today. If a size changes, the ship step changes those `Vec3i`s
  (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`), and the loot tables that carry Level I's size
  (`settlers_wagon.json`, `village_builders_workshop.json`).
- Front and way in: the blueprint's z = 0 side faces the player who places it, the road a Steward plot faces, and the
  camp a colony builds beside; the front door goes there, on the ground floor. Each upgrade keeps the same front.
- Neighbours: a home in a Steward `homes` zone (`VillageMaps` counts it as HOMES), placed freely; the Steward keeps 2
  blocks clear round its plot. The Hall's Upgrades page builds each next level only where its space is free, so a level
  that grows past the one below needs that ground clear (grass, dirt, trees and water are cleared by the builder).

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `BuilderGameTests.builderUpgradesAFinishedCottage`: II clicked onto a finished I lines up exactly (the click is on a
  wall block of I); built from what it still needs; I's red bed and II's light blue bed stand; under 80 % placed.
- `BuilderGameTests.builderGrowsACottageSideways`: III over a finished II: a mound of dirt and grass where III's new
  part goes is cleared (that cell is air), a cell of II's outside wall is now an open doorway, III has a smoker, an
  oak door of its own into the new part, and a lantern outside; I's and II's beds stand; under 40 % placed.
- `BasementGameTests.levelFourDigsAndFinishesItsBasement`: IV over III on real ground: built as drawn, every cellar air
  cell dug out, I's red bed kept, the ladder and the cellar floor built, under 80 % placed.
- `DoorwayHeadroomGameTests.aVillagerSitsDownOnTheStarterCottagesChairUnderTheWindow` (Level I): a villager in the room
  walks onto a chair and sits up on it.
- `FurnitureSeatsCompatTests` (compat): a fancy bed set inside a finished Level I's footprint is the sleeper's home in
  this cottage, and the furnished home adds beauty.
- `QaBuildsGameTests` (B280): Level I clicked onto a one-block bump must cover x 13..17, z 8..11 round the click and stay
  inside x 7..25, z < 26.
- Others that place the cottage by coordinates: `QaSageSeerGameTests` (a cottage at (2, 1, 2), a Chapel at (16, 1, 2)),
  `QaBlueprintHandlingGameTests` (two cottages 24 apart), `QaRefreshFurnishedGameTests` (an old drawing refreshed to the
  new one equals a fresh build, nothing floating; the house in one quarter of `huge_area`), `BuilderChaosGameTests`
  (14, 2, 14), `WonderPlanGameTests`, `VillageHallGameTests`, `MasterArchitectGameTests`, `QaMasterArchitectGameTests`,
  `QaAnsweringHousesGameTests`, `B92GameTests`, `QaBasementSeals1107bGameTests`, `QaBasementGround1104GameTests`,
  `ChimneyCampfireGameTests`.

Built whole (every block right, **0 skipped**, the builder alive, the supplies exact):
- `BuilderGameTests.buildsStarterCottage`; `QaBuilderSuppliesGameTests`; `QaBuilderDisruptionGameTests`;
  `BuilderChaosGameTests`; the basement tests; compat: `QaSpruceCottageBugTests` (the Spruce cottage with Carved Wood,
  lanterns included), `CarvedStylesCompatTests.theGrandStyleWithRechiseledBuildsWithNothingSkipped`,
  `FurnishedHomesCompatTests` (furnished with Handcrafted; waits for a missing couch by name).
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: every block one a builder can place.

Blocks the tests name (this is all the tests require of materials; everything else is free):
- Level I contains at least one each of: `spruce_log`, `cobblestone`, `oak_door`, `glass_pane`, `chest`, a spruce (or
  dark oak) stair (`StyleGameTests.aStyleSwapsTheBlocks`: in Dark Oak it must show `dark_oak_log`, `cobbled_deepslate`,
  `spruce_door`, `glass_pane`, `chest`; in Sandstone `sandstone` and `jungle_stairs`, and no cobblestone left).
- Level I's main wood (the wood with the most blocks) is spruce, it has `birch_planks` in a wall cell open on at least
  one side (the modded copy makes it daub), and a covered `spruce_planks` floor with room above it (the copy makes it
  herringbone) (`ModdedLibraryCompatTests`, `QaCarvedLibraryCompatTests`: the vanilla cottage has birch planks, the
  copy has `supplementaries:daub`, `chipped:herringbone_spruce_planks`, `carved_wood:spruce_chest`).
- Level I has spruce or oak planks and birch planks or white terracotta (the Grand style's Rechiseled flooring and
  andesite squares, `CarvedStylesCompatTests`).
- Level I's only `minecraft:spruce_*` blocks are logs, wood, planks, stairs, slabs, fences, fence gates, doors,
  trapdoors, pressure plates and buttons (the Apricorn style must leave no `minecraft:spruce_` block, and must show
  apricorn planks, log and stairs: `CobblemonCompatTests.blueprintsComeInApricornWood`).
- Level I's bed is `red_bed` (`FurnishedBuildCompatTests`, `ModdedLibraryCompatTests`: the vanilla cottage's bed); the
  chair stand-in is `spruce_stairs` (`FurnishedBuildCompatTests`). The bed II adds is `light_blue_bed`
  (`builderUpgradesAFinishedCottage`).

By id only (the drawing doesn't matter): `StewardHomesRulesGameTests` (beds as above), `BuiltAdvancementGameTests`,
`QaBuiltAdvancements1116bGameTests`, `TableGameTests`, `TableSearchGameTests`, `TablePreviewGameTests` (Level I's preview
is its own blocks, hollowed; IV's shows cells below y = 0), `BlueprintUnlocksGameTests`, `QaBlueprintLibraryVerify1108bGameTests`,
`HomeGradeGameTests`, `QaGranderHomesGameTests`, `QaRenewalGameTests`, `SiegeGameTests`, `ArenaGameTests`,
`PokemonCenterGameTests`, `ColonyArrivalGameTests` (the styled cottage is the first site), the questbook tests and the
Table/preview network tests.

The owner's rules (`check.py`: posts at fence corners, lamps on tables or posts, crockery only by a stove, furnace,
smoker or counter or on a table with a chair; the crockery spot counts), STYLE.md's checks and `holes.py`:
`rebuild.py --candidate` prints no CHECK line for any level, and `holes.py` passes.

## Free
Everything not listed above: the house's shape, plan, rooms, roofs, chimney and porch, every material and color beyond
"Blocks the tests name", where the furniture and job blocks stand (within the rules above), how II adds its beds, where
III's kitchen grows and how big it is (within the plot and the 40 % rule), what the cellar holds, lighting and style.
