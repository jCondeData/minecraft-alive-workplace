# Ranch: the brief

Family id(s): `ranch`, `ranch_2` (never renamed; "Ranch" and "Ranch II" in `en_us.json`,
`blueprint.aliveworkplace.ranch` / `_2`; hidden advancements `built/ranch` / `built/ranch_2`).
Variants: village (none: the five `village/<biome>_ranch_house` pieces are a different family, the "Ranch House"
village piece, `village.py` / `village_looks.py`, and are not part of this rebuild), modded (none to draw:
`modded_library.py` makes `structure/modded/ranch.nbt` and `_2` from the vanilla drawings and the ship step reruns it;
`ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`, `withoutTheModsEveryBuildIsTheVanillaBuild`), looks (none).
The Table's style buttons (`BlueprintStyles`) restyle the drawing automatically.

## What it is for
The **Rancher's** workplace. A Rancher works at his job block and looks after the horses, donkeys, llamas and camels
**within 16 blocks across and 6 up or down of it** (`RanchWork.RADIUS`, `RancherWork.HERD`): he breaks in wild ones,
saddles and armors the tamed ones with saddles, horse armor and carpets **from the storage by his job block**, and
breeds pairs (golden carrots or apples, hay bales, cactus, also taken from that storage) up to 8 of a kind; with
Cobblemon he also grooms the Pokémon in Pasture Blocks nearby (`PokemonChores`). **Without a storage block in reach of
his job block he does nothing at all** and shows "needs a chest by the smoker" (`RanchWork.tick`: `SupplyContainers.find`
empty, so he returns). The job block is a **smoker**, the block the Rancher shares with the Butcher and the Chef
(`Stations`: hand a villager by it a saddle or a golden carrot and he becomes a Rancher), or the mod's **Feed Trough**
(`ModVillagers.RANCHER` holds `FEED_TROUGH_POI` or `PoiTypes.BUTCHER`).

How a village gets one: the Ranch starts **locked** at the Blueprint Table (not in `blueprint_unlocks/starting.json`;
unlocked by a builder's level-up, `BlueprintUnlocks.PER_LEVEL`, or found as a Ranch blueprint in five village loot chests:
builder's workshop, carpenter's workshop, inn, kitchen, storehouse). The Steward builds Level I by himself in a `farms`
zone when a Rancher is wanted and has no workstation (`steward_rules/workplace_ranch.json`, priority 62, cooldown 2 days)
and when a Village or bigger is short of food (2 meals an adult) and has no Ranch (`food_ranch.json`, priority 78, max 1);
his renewal of an old Rancher's house rebuilds it as a Ranch (`steward_renewal/rancher.json`). Once a Ranch stands, the
Steward gives a free shared job block (a smoker) **inside the Ranch's box, or up to 2 blocks outside it**, to a Rancher
while nobody of that job works there (`StewardJobs.BUILDING_JOBS` "ranch", `BUILDING_MARGIN` 2). On the Village Map it
is a farm ("farms, ranches and gardens", `VillageMaps`). Level II is an upgrade on the same spot and origin.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `ranch` | A Rancher's job block with storage in reach of it, and room for the herd round it | Locked at first (builder level-ups, loot); Steward rules `workplace_ranch` (Hamlet and up) and `food_ranch` (Village and up); renewal list `rancher.json` |
| II | `ranch_2` | No code reads what II adds. It must keep I's job block and storage and give the Rancher more (more herd room, more storage, more stalls: the designer's choice) | Upgrade of I (`BlueprintUpgrades`: id + `_2`); locked with a hint, unlocked like I; a builder who finishes I sells II (`UpgradeOffers`, price from its solid blocks) |

**Exactly two levels.** No `ranch_3`: `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` counts exactly 64 starter
upgrades.

**Upgrade rules the code reads** (`BlueprintUpgrades`, `BuilderGameTests.starterUpgradesKeepMostOfTheirBase`):
- Level II is built over a finished Level I at the same origin and front; only what changed is built.
- Level II is **at least as big as Level I in x, y and z**.
- Level II keeps **at least 60 % of Level I's non-air blocks unchanged** (same block, same state, same cell). Draw the
  two together, II growing out of I.
- Level I's job block and storage stay in the same cells in II (a builder never breaks a container or a job block
  while clearing, `MaterialRules.isProtected`; the Rancher keeps his block).

## Must have (each level)
- **Job block: at least one smoker** (`minecraft:smoker`) **or Feed Trough** (`aliveworkplace:feed_trough`), standing
  **inside the build's box** (the Steward's `BUILDING_JOBS` hand-out reaches 2 blocks past it, no further). The
  Steward's hand-out only works for a *shared* block, so a smoker is the one he gives to a Rancher; a Feed Trough is the
  Rancher's alone. No test fixes the count. A smoker is also the Butcher's and the Chef's block, and every
  extra job block of any job is a block another villager may take: a barrel (Fisherman), any cauldron, water cauldrons
  included (Leatherworker), a composter, a lectern... Draw one only on purpose.
- **Storage in reach of the job block**: at least one chest or barrel (any item storage; a furnace or smoker doesn't
  count) **within 8 blocks across (x and z) and 4 up or down** of every job block a Rancher may hold
  (`SupplyContainers.find`, `RADIUS` 8, `VERTICAL` 4). He walks to it (to within 3 blocks, `walkTo(..., 3.0)`) for
  saddles, armor, carpets, food and berries, and puts what he picks up in it. Drawn empty (builders never fill or copy
  containers). Every indoor chest stands against a wall, a solid block, a bed or another chest
  (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`).
- **Herd room**: open, walkable ground for horses within 16 blocks of the job block. No code reads a fence, pen,
  paddock, stall or gate; whether the herd is penned, and how, is free.
- **Reached on foot from outside, without a fence gate.** Every chest, barrel, bed head and job block within 2 across and
  1 up or down of a cell a walker reaches from all round the build (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`;
  that walker opens doors, trapdoors and gates). Real villagers open wooden doors but **not fence gates**: the Rancher
  must get from the village to his job block, his storage and his herd, so a gate may be on a pen but not on the only way
  in. (Today's drawing reaches its smoker only through a closed fence gate; don't carry that over.) A wooden doorway on
  the way in has room for a villager 1.95 tall (`QaDoorwayClearanceGameTests`; B170: a carpet just inside a 2-tall
  doorway stopped a worker for good). An upper floor or loft holding storage or a job block is reached by a ladder or
  stairs from inside.
- **Beds and residents.** None required: no test or code asks this family for a bed. A bed is free; if drawn, it
  counts as a village bed for the Steward, its head must be reachable on foot (`QaBuildReachGameTests`), it stays whole
  (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`), its head to a wall
  (`FurnishedRoomsGameTests.everyBedHasItsHeadToAWall`, for a `bed` furniture spot).
- **Furniture spots.** No kind is required (no `blueprint_furniture/ranch*.json` today; not a home). Every furniture
  piece drawn is recorded as a spot (`rebuild.py`), each spot's blocks exactly the template's
  (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); nothing small stands on air
  (`FurnishedRoomsGameTests.everyFurnishedPieceHasWhatItStandsOn`, `FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece`);
  no piece within two blocks of a door along the way through it (`noFurnitureStandsInADoorsWay`); every chair stair
  faces its table, turned and mirrored too (`QaChairFacingGameTests`).
- **A chimney or campfire is optional**, but a campfire on a stack must stand on full solid blocks that stay solid in
  every style (`StyleGameTests.chimneysStayWholeInEveryStyle`).
- **No entity** (villager, horse, frame, painting...) and no jigsaw in either file; every block one a builder can place
  (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).
- Doors, beds and double chests stay whole turned and mirrored (`QaMirroredBuildsGameTests.everyBuildsDoorsBedsAndDoubleChestsStayWholeTurnedAndMirrored`).

## Kept cells (every upgrade, and every refresh)
**A refresh (36.21) never moves a container or a job block** (`Refreshes.kept`: containers, slept-in beds, every
acquirable job site, the builder's bench). A Ranch standing on an older drawing keeps these blocks where they are when
it is refreshed to the new drawing, so **the new drawing must not put a wall, a door or a walkway on any of them**, nor
anything a kept block would block or float under; best is the same block in the same cell, or open floor inside a room
where the old block does no harm. Every older drawing (the live `structure/ranch.nbt`, `structure/ranch_2.nbt`, and
`blueprint_history/ranch_2/aaa1360c9.nbt`; the modded copies have the same cells) has these, in template cells (x, y, z):
- Ranch I: **smoker (6, 1, 10)** facing north (the Rancher's job block); **chest (5, 1, 10)** facing north; **water
  cauldrons (7, 1, 10), (9, 0, 2), (10, 0, 2)** (Leatherworker job sites, so kept too).
- Ranch II: all of I's, and **barrel (14, 1, 10)** facing up (storage and the Fisherman's job site).
- Nothing else of the old drawings is kept (fence gates, hay, lanterns are not).

## Fits
- **Plot** (y = 0 sits on the clicked block; the front is the z = 0 side):
  - **Level I: at most 13 wide x 16 tall x 13 deep** (`BuilderGameTests.buildsRanch` builds it at helper (9, 2, 9) in
    the 22 x 18 x 22 `big_area`, the builder's barrels at x <= 5, z = 4).
  - **Steward renewal fit** (`Renewals.fit`, `MARGIN` 3; the family is on `steward_renewal/rancher.json`): turned so its
    front faces where the old house's door was, the new Ranch keeps the **old house's front line** and may reach only
    **3 blocks past the old footprint to each side and to the back**; a Ranch that doesn't fit isn't proposed. A
    smaller Level I renews more old houses (free).
  - **Level II: at least Level I's size each way**; no test places it, so the limit is the shared test areas: keep it
    **at most 30 x 28 x 30** so the huge-area refresh-history check (`QaRefreshHistory1028GameTests`, old and new drawn
    over each other in a 30-cube) can stand it.
- **Declared sizes:** `StarterBlueprints.RANCH` is `new Vec3i(13, 10, 13)` and `RANCH_2` `new Vec3i(20, 13, 13)` today;
  if a size changes, the ship step changes those lines (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`
  asserts the file matches the entry), and `modded_library.py` reads the sizes from that file.
- **Material budget:** Level I fits the four barrels `buildStarter` stocks: **at most 108 stacks** (each item rounded up
  to whole stacks). Each level's material list matches its plan and fits a book
  (`QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`); every countable material has a plural
  (`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`).
- **Build time:** Level I built whole within **30,000 ticks** (`buildsRanch`).
- **Front and way in:** the z = 0 side faces the player who places it and the road of a Steward plot; the way in for
  the Rancher is reachable from there. II keeps I's front and origin.
- **Neighbours:** a farm in a Steward `farms` zone; the Steward keeps 2 blocks clear round its plot (`Plots.CLEAR`) and
  wants the ground under it within 4 blocks of level (`Plots.MAX_STEP`). The herd lives within 16 blocks of the job
  block, so open ground in and round the plot is the Rancher's pasture.

## Rules tests check
Built whole (every block right, 0 skipped, supplies exact): `BuilderGameTests.buildsRanch` (Level I at (9, 2, 9) in
`big_area`, from exactly the plan's materials in four barrels, within 30,000 ticks).

Upgrades and sizes: `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (II at least I's size, keeps 60 % of I, 64
upgrades in all), `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`.

By id only (the drawing doesn't matter beyond the rules above): `WorkplacesGameTests.workplaceRule_ranch_rancher` (the
rule builds `ranch` in `farms`, the blueprint loads); `StewardHomesRulesGameTests.aVillageShortOfFoodWithABerryFarmGetsARanch`
and its food tests (`food_ranch` absent or present); `QaStewardReasonWordsBugTests` ("The store is 1 meal short");
`TableGameTests`, `BlueprintUnlocksGameTests`, `QaBlueprintLibraryVerify1108bGameTests` (in `StarterBlueprints.ALL`,
locked, a hint); `ModdedLibraryGameTests`. No GameTest pins a coordinate of the Ranch; the Rancher's job tests
(`RancherGameTests`, `QaRanchPairs*`, `QaRanchTwoFood1108cGameTests`, `BeastmasterGameTests`, `PastureCompatTests`,
the Daycare compat tests) place a Feed Trough and chests by hand, not the building.

Library-wide checks it must pass: `QaBuildReachGameTests` (above), `QaDoorwayClearanceGameTests`,
`QaMirroredBuildsGameTests`, `QaTurnedFramesGameTests` / `QaTurnedModBlocksGameTests` (frames and mod blocks hang and
face right turned and mirrored), `BlueprintsStayGameTests` (lamps on a block that holds them; no dirt path or farmland
under a solid block; beds whole), `B648GameTests` (no pane or bar joined on one side with air on the other, as stored
and as the game joins it), `QaFurnishedRooms1031GameTests` (no standing lantern on a room's floor; every indoor chest
against a wall, a solid block, a bed or another chest), `FurnitureSpotGameTests`, `FurnishedRoomsGameTests`,
`FloatingFurnishingsGameTests`, `QaChairFacingGameTests`, `StyleGameTests.chimneysStayWholeInEveryStyle`,
`QaMaterialListGameTests`, `QaLaneC1101GameTests`, and `QaRefreshHistory1028GameTests.qaEveryShippedOlderDrawingIsOfferedARefresh`
(once the old drawings move to `blueprint_history/`, a Ranch standing as an old drawing drew it is offered "Refresh to
the latest look").

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair, upper floors reached by stairs or a ladder; `open_panes`), STYLE.md's checks and
`holes.py`: `rebuild.py --candidate` prints no CHECK line for either level, and `holes.py` passes. (Neither level is in
`owner_rules_baseline.json`.)

Saves (for the ship step, not the designer): ids never change; the old drawings go to `blueprint_history/ranch/` and
`ranch_2/` (`history.py`; a site begun on the old drawing finishes as drawn) and the modded copies are regenerated.

## Free
Everything not listed above: shape, plan, roof, materials, colors, style, storeys, whether and how the herd is penned
(fences, gates, stalls, paddock), hay, water troughs (mind that a cauldron is a Leatherworker's block), a bed or none,
how many chests beyond the one in reach, where the job block and storage stand (within the rules above), lighting, and
what Level II adds beyond being bigger and keeping I.
