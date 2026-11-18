# Print Shop: the brief

Family id(s): `print_shop`, `print_shop_2` (never renamed; "Print Shop" and "Print Shop II" in `en_us.json`,
`blueprint.aliveworkplace.print_shop*`; the village house is `village_piece.aliveworkplace.print_shop`, "Print Shop").
Variants: village (`village/<style>_print_shop` for the five village styles `plains`, `desert`, `savanna`, `snowy`,
`taiga`, saved by `generate.py` through `village.py`'s `village_house` with the fit-out `print_shop_room` and the job
`aliveworkplace:printer`; Repurposed Structures' villages reuse these five; its own contract, see "The village
variant"), modded (none to draw: `modded_library.py` makes `structure/modded/print_shop*.nbt` from the vanilla drawings,
turning the chests into Carved Wood ones, and the ship step reruns it; `QaCarvedLibraryCompatTests.theHomesAndWorkshopsHaveCopies`
requires the Print Shop's copy), looks (none: the Table's style buttons, `BlueprintStyles`, restyle the drawing).

## What it is for
The **Printer**'s workplace (`ModVillagers.PRINTER`, config `printers`, part of Milestone 34's Classes and luxuries; a
Printer must be Artisan class or better). The Printer's one job block is a **cartography table**, the press: a villager
is made a Printer by handing him an **ink sac** at a cartography table (`work/Stations`, `Printers.isInk`); a jobless
villager who finds a cartography table by himself becomes a vanilla Cartographer instead. At the press he prints books,
the Village Gazette and the Illuminated Book (`printer/PrinterWork`, the shared `craft/LuxuryWork` engine) for the
village store, taking makings from **the containers within 8 blocks across and 4 up or down of the press**
(`SupplyContainers.find(level, station)`, `RADIUS` 8, `VERTICAL` 4), the village store and stashes, and putting what he
prints back into those chests (the Gazettes in them count towards the store's 8). **With no container within that reach
he prints nothing at all** (`LuxuryWork.choose`: "nowhere to put what's made"). Players come to buy from him. The Print
Shop is not in a new village's starting set: a **Journeyman Printer sells its blueprint** (`ModTrades.journeymanBlueprint`,
`BlueprintUnlocks.VILLAGER_SALES`), and a builder who finished Level I sells Level II (`UpgradeOffers`). The **Steward**
builds it by himself in a **`workshops`** zone when a Printer is wanted and has no workstation
(`steward_rules/workplace_print_shop.json`, priority 62, cooldown 2 days, Hamlet and up). The Village Map shows it as a
workshop (`VillageMaps.kindOf`). Both levels are hidden from the Table while the `printers` switch / Milestone 34 gate
is closed (`StarterBlueprints.expansionSwitch`). Finishing each level grants the hidden advancement `built/print_shop`,
`built/print_shop_2`. In world generation the five village styles grow the village variant (pool weight 2, only while
`printers` is on, `VillageHouses.LUXURY_HOUSES`) with a Printer already in the job and a chest of paper and ink (loot
table `chests/village_print_shop`).

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `print_shop` | The press (one cartography table) under a skylight, the Printer's one chest within reach of it, a counter for buyers, a desk, paper and ink on show (test-pinned counts below); **no bookshelf** | Sold by a Journeyman Printer; Steward rule `workplace_print_shop` (workshops zone, Hamlet+) |
| II | `print_shop_2` | A reading room: **at least 24 bookshelves** (test-pinned); still one cartography table, still under its skylight | Upgrade of I (`BlueprintUpgrades`: id + `_2`); sold by the builder who finished I |

**Two levels only.** No `print_shop_3` may be drawn: `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` counts
exactly 64 upgrades library-wide.

## Must have (each level)
- **Job block: exactly one, a `minecraft:cartography_table`**, in I and in II
  (`QaLuxuryWorkshopsGameTests.everyTierHasOneJobBlockAndItIsItsWorkersOwn`: exactly one block in the whole file that
  *any* job takes as its job site, and it is the cartography table a Printer takes; `LuxuryWorkshopsGameTests.luxuryWorkshopBuilt_print_shop`
  / `_print_shop_2`: exactly one cartography table and none of blast furnace, smithing table, grindstone, stonecutter,
  fletching table, barrel, lectern, composter, cauldron, smoker, brewing stand, loom, crafting table). So **no lectern
  anywhere** (a Librarian's; `QaLuxuryWorkshopsGameTests.noWineryHasABarrelAndNoPrintShopALectern`, the
  `luxury_workshops` scene checks it too), **no barrel**, **no jukebox** (the Bard's job block), no bell-like or mod job
  block (Scholar's Desk, Mailbox, Shop Counter, Postal Desk, Music Stand, ...), no second cartography table. A chest, a
  bookshelf, a candle or a carpet is not a job block.
- **The skylight over the press** (`LuxuryWorkshopsGameTests.thePrintShopAndTheJewellersHaveWhatTheirTradesNeed`, I and
  II): in the press's own column (same x and z), above the table, there is **exactly one non-air block in the whole file,
  and it is `minecraft:glass`** (the full block: not a pane, not stained or tinted glass). Nothing else above it at any
  height: no floor, beam, lantern, chain, slab or roof block. Level II must keep this too: it cannot put a storey or
  a roof over the press.
- **Test-pinned contents** (same test, both levels unless said): at least **40 `minecraft:bricks`** blocks; at least **4
  `minecraft:white_carpet`** (paper) and at least **4 `minecraft:black_candle`** (ink); at least **3
  `minecraft:dark_oak_slab` at template y = 1** (the counter); **Level I has no `minecraft:bookshelf`, Level II has at
  least 24**. These are the only blocks a test names; everything else about materials is free. (The candles and carpets
  must stand on something: `FloatingFurnishingsGameTests`.)
- **The Printer's chest within reach of the press: Level I has exactly one container, a single `minecraft:chest`**,
  within **8 across and 4 up or down** of the cartography table (`FurnishedWorkplacesCompatTests.thePrintShopWithHandcraftedAndBeautify`:
  "has N containers, not 1", and "the job ... doesn't find the chest"). "Container" means any block whose block entity
  holds items, so Level I has **no second chest, no double chest, no trapped chest, barrel, furnace, hopper, dropper,
  dispenser, chiseled bookshelf, decorated pot, jukebox or shulker box**. Level II may add chests (no test counts them);
  every chest it adds should also be within the press's reach, since the Printer only uses those. Chests are drawn empty
  (builders never fill or copy containers; no loot table in the buildables).
- **The press is reached on foot from the street in front.** A Printer standing 3 blocks before the front edge walks in
  and claims the table as his job site (`luxuryWorkshopBuilt_print_shop`, `_print_shop_2`), and a Printer standing at
  the front wall's west end, template (1, 0, 0), walks round whatever stands before the front and in by the door to the
  table within 1,200 ticks (`ShutTrapdoorGameTests.aPrinterBesideThePrintShopsFlowerBoxWalksRoundItAndTakesTheTable`).
  The cartography table, every chest and every bed head are within a player's reach (2 across, 1 up or down) of a cell
  walked to from the ground outside, by levels, one-block steps up, drops of up to 3, ladders, doors and trapdoors
  (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`). Every wooden doorway has room for a
  villager 1.95 blocks tall (`QaDoorwayClearanceGameTests`). Nothing at head height on the walk in (a shut top-half
  trapdoor at head height is a wall to a villager since B156; the B156 Printer stood stuck against one all day).
- **Beds and residents: no bed is required** (no code counts the Print Shop's beds; the old drawing has none). Any bed
  drawn counts as a village bed for the Steward and shows on the Table as "Beds: N"; it gets no furniture spot (a
  workplace's beds stay vanilla: `furniture.py` `WORKPLACES`, `FurnishedWorkplacesGameTests.noSpotCoversAJobBlockABedOrAContainer`);
  its head must be reachable and its foot has its head one block towards its facing (`BlueprintsStayGameTests`); no bed
  right inside a doorway (`check.py`). No villager or other entity in either file (`luxuryWorkshopBuilt_*`: "comes with
  a villager"); no jigsaw or structure void.
- **Furniture spots** (`blueprint_furniture/print_shop.json`, `print_shop_2.json`, written by `rebuild.py`): **each level
  has at least one `desk` spot and at least one `counter` spot** (`FurnishedWorkplacesGameTests.everyWorkplaceIsFurnished`);
  **no `bed` spot**; no spot's cells cover the cartography table, a container or a bed (`noSpotCoversAJobBlockABedOrAContainer`).
  With Handcrafted and Beautify installed the desk and counter become those mods' pieces and the build must still end
  with every block right and nothing skipped, the table still a job site and the chest still the only storage the press
  finds (`FurnishedWorkplacesCompatTests.thePrintShopWithHandcraftedAndBeautify`, Level I): so each spot must have the
  support its kind's piece needs (a desk: floor and a wall behind). Each spot's blocks are exactly the template's
  (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); nothing small stands on air (`FloatingFurnishingsGameTests`);
  every chair faces its table, turned and mirrored too (`QaChairFacingGameTests`); being furnished, every piece on a
  room's floor has block light above 7 from that room's own lantern (`FurnishedRoomsGameTests.everyFurnishedRoomHasALight`),
  no standing lantern on a room's floor and every chest against a wall, solid block, bed or chest
  (`QaFurnishedRooms1031GameTests`).
- Every block is one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).

## Kept cells (the upgrade)
- II has the same origin and front as I and is at least its size in x, y and z; **at least 60 % of I's non-air blocks
  stay the same block in the same cell** (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`). Levels grow out of
  each other (REBUILD.md): II adds its reading room to I's building.
- I's chest stays in the same cell as the same block in II (builders never break containers or copy their contents),
  and the press keeps its cell and its skylight.

## Fits
- **Plot: at most 18 wide x 18 tall x 22 deep** for each level. `luxuryWorkshopBuilt_print_shop` and `_print_shop_2`
  (`CraftWorkplacesGameTests.build`) place the build at helper (12, 2, 8) in the 30 x 20 x 30 `huge_area`, so x 12..29,
  y 2..19, z 8..29; the builder's bench (3, 2, 3) and three stock chests (3, 2, 6/8/10) stand west of it. (The compat
  test places Level I at (9, 2, 3) and the B156 test at (8, 2, 8): both roomier.)
- y = 0 sits on the clicked block; the front is the z = 0 side.
- **Front and way in:** the z = 0 side faces the player who places it and the road of a Steward `workshops` plot. The
  arriving Printer is spawned at helper (13, 2, 5) = template **(1, 0, -3)**, three blocks before the front edge near
  its west end, and must find his way in from there.
- **Declared sizes:** `StarterBlueprints.PRINT_SHOP` (`11, 11, 9`) and `PRINT_SHOP_2` (`11, 11, 16`) today; if a size
  changes the ship step changes those `Vec3i`s (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`,
  `luxuryWorkshopBuilt_*`: "is <size>"); `modded_library.py` reads the sizes from that file.
- **Material budget: at most 81 stacks each for I and for II**, each built from bare ground (three chests of 27 stocked
  with exactly what the supply report says is missing, each item rounded up to whole stacks).
- **Neighbours:** a workplace in a Steward `workshops` zone; the Steward keeps 2 blocks clear round its plot
  (`Plots.CLEAR`) and wants the ground under it within 4 blocks of level (`Plots.MAX_STEP`). No party walls.

## The village variant (`village/<style>_print_shop`, five styles)
Village houses share one contract that the Java reads by template coordinate (`world/VillagePieces`,
`world/VillageHouses`, `village.py`'s `village_house`, `village_looks.py` header). The designer draws only the look
(`village_looks.py` `DESIGNS["print_shop"]`, today 9 x 17 x 10); `village.py` adds the jigsaw, fit-out, bed and villager.
Every style keeps exactly:
- **At most 10 wide** (`VillageGameTests.thePrintShopsPrinterTakesItsPress` places the plains and the desert house 11
  apart, at helper (1, 1, 4) and (12, 1, 4) in the 22 x 18 x 22 `big_area`; so also at most 18 deep and 17 tall).
- **The street joint:** a `minecraft:jigsaw` at **(4, 1, 0)**, `north_up`, name and target `minecraft:building_entrance`,
  final state `minecraft:structure_void`; **the front row z = 0 at y = 0 places nothing**, and placing it as a village
  does leaves no structure_void in the world (`VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`).
- **The front door at (4, 1, 2)** facing south, a step at (4, 0, 1); **no trapdoor at z = 1, y 0-1**
  (`VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow`).
- **The room:** template **x 2-6, z 3-7, y up to 4** (`VillagePieces` room box), bounded by the west wall at x = 1.
- **The bed: foot (6, 1, 6), head (6, 1, 7), facing south.**
- **The fit-out** (`village.py`'s `print_shop_room`, moved in by one): the **cartography table at (2, 1, 7)**, a chest at
  **(2, 1, 6)** with `LootTable` `aliveworkplace:chests/village_print_shop`, bookshelves at (2, 1, 4), (2, 2, 4) and
  (2, 1, 3). **Exactly one job block in the whole template**, and **no lectern or barrel** anywhere
  (`VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`: "should have one Cartography Table", "has N job blocks";
  `QaLuxuryWorkshopsGameTests.everyStylesPieceHasItsBlockAndAWorkerWhoTakesIt`). No skylight rule here.
- **The villager:** exactly one entity, a villager already a **Printer**, at **(5, 1, 5)**; keep (5, 1, 5) and (5, 2, 5)
  clear with floor under it. `thePrintShopsPrinterTakesItsPress`: placed as a village places it, he takes that table
  within 2,400 ticks and keeps the job.
- A ladder to every furnished upper floor (`village_looks.py` header).
- Every pool lists it at weight 2 while `printers` is on (`QaLuxuryWorkshopsGameTests.eachVillagePieceWeighsTwoInEveryStyle`,
  `LuxuryWorkshopsGameTests.villagesGrowThePrintShopAndTheJewellersOnlyWhileTheirJobsAreOn`), by id;
  `everyVillageTypeCanGrowTheOtherHouses` pins `VillageHouses.houseNames()` with `print_shop` 19th.
- `B648GameTests.ALLOWED` and `QaWindowsAndWards1028GameTests.allowed` excuse one iron-bar end at **(6, 5, 0)** in every
  `*_print_shop` village template (the old lamp arm). A new look need not use it; if it doesn't, the ship step removes
  the two exceptions (otherwise every pane or bar run must end against a block, not air).

## Rules tests check
Pinned coordinates and counts (the ship step moves coordinates to the new drawing, keeping what each checks, dropping
none):
- `LuxuryWorkshopsGameTests.thePrintShopAndTheJewellersHaveWhatTheirTradesNeed` (I and II): exactly one non-air block
  above the cartography table in its column, and it is `glass`; no lectern; >= 40 bricks; >= 4 white carpet and >= 4
  black candles; >= 3 dark oak slabs at y = 1; I has 0 bookshelves, II >= 24.
- `LuxuryWorkshopsGameTests.luxuryWorkshopBuilt_print_shop`, `luxuryWorkshopBuilt_print_shop_2` (`CraftWorkplacesGameTests.build`,
  `HOUSE` (12, 2, 8), `BENCH` (3, 2, 3), `CHESTS` (3, 2, 6/8/10), Printer at (13, 2, 5)): size matches the declared
  size; no entity; exactly one cartography table and none of the other workstations; at most 81 stacks; built by a
  builder; then the Printer from the street holds a cartography table inside the build's box as his `JOB_SITE` and is
  still a Printer.
- **Build time, the binding limit.** These tests set day time **2,000** (build delay 2, builders' help off), and the
  builder works only until dusk, **about day time 9,000**: so each level must be finished in about **7,000 ticks**, and
  the Printer must then walk in and claim the table before the villagers' rest (B156 failed at day time 14,001,
  "activity rest"), well inside the 12,000-tick timeout. Measured builder rates are about 120 to 170 placed blocks per
  1,000 ticks (Ferry House: 769 blocks done by tick ~4,560; 952 blocks overran into the next morning; Leader's Hall:
  993 placed by tick 9,000). **Block budget: at most about 750 placed blocks for each level, a hard ceiling of about
  850**, counted for Level II as the whole building (each level is built from bare ground in its own test). The old
  drawings are 420 (I) and 694 (II). **No roof, overhang, barge or eave over another roof** (B1384: builders stood
  thousands of ticks on such cells, skipped = 0). The compat test (Level I, build delay 1) has the same dusk limit.
- `FurnishedWorkplacesCompatTests.thePrintShopWithHandcraftedAndBeautify` (Level I, at (9, 2, 3)): exactly 1 container
  that is not the job block; built to the last block with nothing skipped; every desk/counter spot gets the mod's piece
  where it can stand; the table is a job site; the building's storage found in the world is exactly that chest; the
  press's `SupplyContainers.find` reaches it.
- `FurnishedWorkplacesGameTests.everyWorkplaceIsFurnished` (desk and counter in both levels),
  `noSpotCoversAJobBlockABedOrAContainer` (and no bed spot).
- `QaLuxuryWorkshopsGameTests.everyTierHasOneJobBlockAndItIsItsWorkersOwn`, `noWineryHasABarrelAndNoPrintShopALectern`.
- `ShutTrapdoorGameTests.aPrinterBesideThePrintShopsFlowerBoxWalksRoundItAndTakesTheTable` (Level I placed whole, no
  builder, at helper (8, 2, 8)): today asserts the table at template **(3, 1, 5)** and a trapdoor at **(2, 1, 0)** (the
  old front window box), spawns the Printer at **(1, 0, 0)** and wants the table taken within 1,200 ticks. The new
  drawing need not have a window box: the ship step moves the table cell, and if no shut trapdoor stands at head
  height by the front wall it sets one there in the test itself, so the B156 check (a Printer beside such a box walks
  round it) is kept.
- `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (II over I), `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`.
- Village variant: `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`, `everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`,
  `noVillageHouseHasATrapdoorInItsFrontWalkRow`, `thePrintShopsPrinterTakesItsPress`;
  `QaLuxuryWorkshopsGameTests.everyStylesPieceHasItsBlockAndAWorkerWhoTakesIt`, `eachVillagePieceWeighsTwoInEveryStyle`;
  `VillageCompatTests.repurposedStructuresVillagesGetOurHouses`.
- Not a test, but pinned: the showcase scene `luxury_workshops` (`devclient/LuxuryWorkshopsScene.PIECES`) expects the
  cartography table at template **(3, 1, 5)** in I and II (and no lectern in either); `tools/showcase/scenes.py` captions
  `18_print_shop` "brick and timber, the skylight over the press". The ship step moves the cell and the caption.

By id only (the drawing doesn't matter beyond the rules above): `LuxuryWorkshopsGameTests.luxuryWorkshopRule_print_shop`,
`luxuryWorkshopsAreInTheBlueprintTable` (in the Table, a starter blueprint, a workshop on the Village Map, named),
`journeymenSellTheirBuildingsBlueprint`; `QaLuxuryWorkshopsGameTests.theJourneymansBlueprintIsTheTablesOwnAndReadsInEnglish`,
`eachStewardRuleHoldsForItsOwnJobOnly`, `everyNameAPlayerReadsIsAmericanEnglish`;
`ExpansionBuildsGameTests.laterExpansionsBuildsWaitForTheirSwitch`; `ModdedLibraryGameTests`;
`QaCarvedLibraryCompatTests.theHomesAndWorkshopsHaveCopies` and `everyModdedCopyKeepsItsJobSites` (regenerate the
modded copies).

Library-wide checks it must pass: `B648GameTests` and `QaWindowsAndWards1028BugTests` (no pane or bar run ending
against air), `QaFurnishedRooms1031GameTests`, `FurnishedRoomsGameTests`, `BlueprintsStayGameTests`,
`QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`, `QaBuildReachGameTests`, `QaMirroredBuildsGameTests`,
`QaTurnedFramesGameTests`, `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`,
`QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`, `StyleGameTests.chimneysStayWholeInEveryStyle`
(only if a campfire stands on a solid stack).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair, no bed right inside a door; `open_panes`), STYLE.md's checks and `holes.py`:
`rebuild.py --candidate` prints no CHECK line. The Print Shop is not in `owner_rules_baseline.json` today (the old
drawing passes), so the new one must pass outright. The ship step keeps the old drawings in `blueprint_history/print_shop/`
and `print_shop_2/` (`history.py`; earlier versions are already there), regenerates the modded copies, the village
variants and the furniture spots (`blueprint_furniture/print_shop*.json`, `village/<style>_print_shop.json`), takes
`print_shop`/`print_shop_2` out of `generate.py`'s saves (adds them to `REBUILT`), and updates `docs/wiki/luxury-workshops.md`
and `docs/guide/print-shop-build.md`, which describe the old look.

## Free
Everything not listed above: shape, plan, roof, materials (beyond the bricks, white carpet, black candles, dark oak
slabs at y = 1, bookshelves and the one glass block the tests count), colors, storeys and rooms (none above the press),
where the press, chest, counter and desk stand within the reaches above, how Level II's reading room joins Level I,
beds (none needed), extra chests in Level II, lighting (within the light rules), and the village variant's look outside
its room in each of the five styles.
