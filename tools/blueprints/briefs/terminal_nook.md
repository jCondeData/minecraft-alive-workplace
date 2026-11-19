# Terminal Nook: the brief

Family id(s): `terminal_nook` (one level, never renamed; "Terminal Nook" in `en_us.json`,
`blueprint.aliveworkplace.terminal_nook`; hidden advancement `built/terminal_nook`)
Variants: village (none), modded (**two drawings to make**: the vanilla build `structure/terminal_nook.nbt` and its
Tom's Simple Storage copy `structure/modded/terminal_nook.nbt`, drawn together from one plan; today both come from
`storage_halls.py` through `generate.py` (`save_storage_halls`), and `modded_library.py` leaves the Nook alone
(`OWN_COPIES`), so nothing else dresses or draws its copy), looks (none drawn; the Table's style buttons restyle it
automatically).

ROADMAP 37.17, the smallest of the three storage builds (`StarterBlueprints.STORAGE_HALLS`, with the Storage Hall and
the Warehouse). Not a flagship: one version. Part of ROADMAP 50.13.

## What it is for
A small store room for the village's goods, the cheap way to get a Storehouse (37.17: "a small room that fits beside
a house"). Its one **Storehouse block** takes a Porter and makes it the village's store: the village, porters,
builders, the Storehouse board and the Village Map count it as one Storehouse (on the map as a trade building,
`VillageMaps.Kind.TRADE`). Every store within **8 blocks across and 4 up or down** of that block
(`SupplyContainers.RADIUS`, `VERTICAL`, a box) is the store, and a builder anywhere in the village's reach takes his
materials from it. With Tom's Simple Storage installed (and `moddedLibrary` and `furnishedBuilds` on) a new one is
built from the Tom's copy: the chests round one Inventory Connector form a Tom's network, the Storehouse reads the
network through the connector (each item counted once, 37.10), and one terminal shows the store in Tom's own screen.
Without Tom's it is the vanilla build, every chest and barrel its own store. At the Blueprint Table while Milestone 37's
expansion is open (`moddedStorageNetworks`); hidden while it is closed but still loads by id.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `terminal_nook` | A Storehouse block with a handful of stores in its reach; with Tom's, a network of chests on one connector and one terminal | At the Table while M37 is open (`StarterBlueprints.expansionSwitches`: `moddedStorageNetworks`); `ONE_TIER` |

**One level only.** A `terminal_nook_2` would be a new feature.

## Must have
**The vanilla build** (`StorageHallGameTests.theStorageBuildsAreAtTheTableAsVanillaBuilds`,
`aBuilderBuildsTheTerminalNookAsAStorehouse`; `QaStorageHallGameTests`):
- **Exactly one Storehouse block** (`aliveworkplace:storehouse`), a job site once built
  (`getPoiManager().getType(...)` present). No other job block is needed; each one drawn may pull a villager to a job
  (a barrel is a Fisherman's job site, a crafting table a Carpenter's), and every job block must pass the reach check.
- **At least 7 chest and barrel blocks** (`minecraft:chest` or `minecraft:barrel`, counted as blocks: a double chest
  counts 2 here).
- **Every chest and barrel of the build is in the Storehouse's store**: within 8 across and 4 up or down of the
  Storehouse block; the store found inside the build's box must be exactly the build's number of stores (a double
  chest is one store). Drawn empty.
- **Every store is a real, separate container a player can break**: the QA tests put a different item in each store,
  read the board's "N chests, M items" line, take each item back out through the store and expect each counted once.
- **No modded block** but the Storehouse block.
- Every chest, barrel and job block reachable on foot within 2 across and 1 up or down (`QaBuildReachGameTests`),
  openable (nothing solid on a chest's lid), every indoor chest against a wall, a solid block, a bed or another chest
  (`QaFurnishedRooms1031GameTests`).
- **Beds and residents:** none. **Furniture kinds:** none required (no `blueprint_furniture/terminal_nook.json`); any
  piece drawn is recorded as a spot and must pass the furniture checks below.
- **Built whole, nothing skipped** (`plan.unfinished` empty, `site.skipped() == 0`): every block placeable by a builder
  from the materials list, and the finished build standing as drawn when pasted step by step.
- **A structure stage long enough to restart in**: `QaStorageHallGameTests.theNookBuildFinishesAfterARestartMidBuild`
  saves and reloads the build after **300 ticks in its STRUCTURE stage** (build delay 1); a build whose structure is
  laid in under 300 ticks never gets its restart and fails.

**The Tom's copy** (`QaModdedStorageCompatTests.theTomsStorageBuildsHoldWhatTheSpecLists`,
`StorageHallsCompatTests.withTomsTheStorageBuildsUseTheirTomsCopies`, `aBuilderBuildsTheTerminalNookOnTomsNetwork`;
block ids from Tom's Simple Storage 2.3.0):
- exactly one Storehouse block; **exactly one** `toms_storage:inventory_connector`; **exactly one terminal**
  (`toms_storage:storage_terminal` plus `toms_storage:crafting_terminal` together = 1; today's test pins a
  storage terminal); **at least 6** chests, barrels or trapped chests. Cables, cable connectors, trims and filing
  cabinets are allowed but not required.
- **The connector joins its stores and nothing outside the build:** Tom's connector joins the inventories touching it
  or each other, passing through Inventory Trim, within 16 blocks of it (`StorageNetworks.TOMS_RANGE`), plus the one
  inventory each cable connector faces along its cables; the test wants exactly the drawn number of members, all inside
  the build's box, at least one of them a chest or barrel (cobblestone goes in the first and last chest or barrel), and
  the Storehouse's store to list the connector and none of the members. So the connector stands within the Storehouse
  block's reach. A store left off the network (today the barrel) is allowed: it is then the Storehouse's own store.
- **The terminal is on the network** and usable: it reads the block on its `facing` side (or under it, `pos=down`,
  a terminal set into a counter, screen up); a player standing on the floor in front of its screen (for a screen facing
  up, on the floor north of it) right-clicks it and gets Tom's terminal menu (`TerminalMenu`) listing the network's
  items. 10 + 5 cobblestone put in the first and last member count 15 through the Storehouse and 15 in the terminal.
- **Missing without Tom's: only Tom's blocks**, the connector among them (`StorageHallGameTests`: the copy's missing
  blocks are all `toms_storage:`), so the copy uses vanilla blocks and Tom's only; no other mod's block.
- **The same shape as the vanilla build** (`ModdedLibraryCompatTests.everyModdedCopyCanBeBuiltWithThePacksMods`): the
  same size and basement; every cell the vanilla build fills is filled in the copy, and no cell it leaves as air is
  filled; doors, beds, lecterns, barrels and smithing tables the same block in both; and the copy must differ somewhere.
  So each Tom's block stands in a cell where the vanilla build has some other non-air block, one for one (today: a
  chest for the connector, a counter block for the terminal), and a barrel can never become a Tom's block. **Any job
  block stays the same block in both** (B388: a terminal is no job site).

## Fits
- **Plot: at most 12 wide x 12 deep x 18 tall.** `QaBuilderStockGameTests.aBuiltTerminalNookFeedsTheVillagesBuilder`
  places the Nook's template at helper (18, 2, 18) in the 30 x 20 x 30 `huge_area`, so it ends at x/z 29 and y 19. The
  other tests: `StorageHallGameTests` and `StorageHallsCompatTests` at (min(9, 28 - width), 2, min(3, 28 - depth)) with
  the builder's bench at (2, 2, 2), the builder at (3, 2, 3) and the material barrels from x 1-6 at z = 5;
  `QaStorageHallGameTests` pastes it at (9, 2, 3) (test hut at (19-23, 2, 19-23), table at (26, 2, 26)) and at (2, 2, 2)
  for the reach tests. The showcase scene `storage_hall` (`StorageHallScene`) puts it at x 18 with the Warehouse at
  x 32 (14 wide at most, already within the limit).
- **Where the Storehouse block stands, for the reach tests** (`aNookExactlyAtTheVillagesReachIsCounted`,
  `aNookOneBlockPastTheVillagesReachIsNotCounted`): a Blueprint Table goes exactly 12 east and 16 south of the
  Storehouse block, level with it, with the Nook at (2, 2, 2) and the test hut at (20, 2, 22) (5 x 4 x 5). So in the
  template the Storehouse block's z is at most 11 (the table stays in the area), and either its x is at most 5 or its
  z at most 3 (the table stays clear of the hut); otherwise the ship step moves the hut.
- **Build time:** within 30,000 ticks at build delay 1, builders' help off, vanilla and Tom's (and the feed test's
  40,000 covers the Nook plus a test hut).
- **Declared size:** `StarterBlueprints.TERMINAL_NOOK` is `new Vec3i(9, 10, 9)` (no basement); the tests place the
  build from it and the `.nbt` must match (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`),
  so the ship step sets it to the new size.
- y = 0 sits on the clicked block; the front is the z = 0 side.
- **Front and way in:** the z = 0 side faces the player who places it; the way in is there, and a villager 1.95 blocks
  tall walks through it (`QaDoorwayClearanceGameTests`).
- **Neighbours:** none fixed (no Steward rule, no village plan places it; it is meant to stand beside a house).

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `StorageHallGameTests.aBuilderBuildsTheTerminalNookAsAStorehouse`: built from barrels holding exactly its materials,
  every block right, nothing skipped, the **Storehouse at (2, 1, 4)** a job site, its store inside the box exactly
  **8** stores (`build(helper, TERMINAL_NOOK, (2, 1, 4), 8)`).
- `StorageHallGameTests.theStorageBuildsAreAtTheTableAsVanillaBuilds`: listed, named, `ONE_TIER`, a `TRADE` building,
  has a Tom's copy missing only Tom's blocks (the connector among them), chosen as the vanilla build without Tom's, one
  Storehouse, no modded block, **at least 7** chest and barrel blocks.
- `QaStorageHallGameTests` (`NOOK_STOREHOUSE` (2, 1, 4), `NOOK_STORES` 8):
  - `aFinishedNookFeedsASecondBuildersHutWithNothingLostOrDuplicated`: the built Nook's store is exactly 8; the hut's
    materials (3 over) spread across its stores; a second builder at a table 24 blocks off builds the hut from them, and
    exactly the 3 over of each is left in the Nook, his bag and on the ground.
  - `theNooksBoardCountsEachItemOnce`: the pasted Nook's store is exactly 8; a different item in each store
    (the test has 8 items, one per store: the ship step adds items if the count rises); the board says "8 chests" and
    the true item total; taking each item through the store gives exactly what was put in.
  - `theNookBuildFinishesAfterARestartMidBuild`: restarted after 300 ticks of STRUCTURE, it finishes whole, the
    Storehouse block in place, 8 stores, the material barrels empty and the builder's bag empty.
  - `aNookExactlyAtTheVillagesReachIsCounted` / `aNookOneBlockPastTheVillagesReachIsNotCounted`: the store holds the
    test hut's materials; a table 20 blocks from the Storehouse block counts it with a 20-block village and not with
    a 19-block one (the status then says the Storehouse is too far). The pasted Nook (every step set as the builder
    sets it) must stand whole, with the Storehouse block where pinned.
- `QaBuilderStockGameTests.aBuiltTerminalNookFeedsTheVillagesBuilder`: the vanilla template placed with
  `StructureTemplate.placeInWorld` at (18, 2, 18), the **Storehouse at template (2, 1, 4)** (hard-coded
  `origin.offset(2, 1, 4)`), its store at least 2 containers; cobblestone and a door in the first store feed a builder
  30 blocks off, and the store is emptied of the cobblestone.
- `StorageHallsCompatTests.aBuilderBuildsTheTerminalNookOnTomsNetwork` (compat, with Tom's): built from the copy,
  every block right, nothing skipped; the **connector at (4, 1, 6)** joins exactly **6** inventories, all inside the
  build; the **Storehouse at (2, 1, 4)** lists the connector and none of them; 10 + 5 cobblestone count 15 through the
  store; the **storage terminal at (4, 1, 5), screen north** (the player on the floor at (4, 1, 4)) opens Tom's
  terminal menu listing 15.
- `QaModdedStorageCompatTests.theTomsStorageBuildsHoldWhatTheSpecLists`: the copy's counts in "Must have" (one
  Storehouse, exactly one connector, exactly one terminal, 6+ chests and barrels).
- Showcase `StorageHallScene` (`SCENE=storage_hall`): the Tom's Nook's connector at (4, 1, 6) joins 6; the still
  `04_nook_inside` looks from (4.5, 1, 3.5) to (4.5, 1.2, 6.5) (the ship step moves both).

By id only: `StorageHallGameTests.withMilestone37ClosedTheyAreHidden`;
`QaModdedStorageCompatTests.theStorageSentencesAreInTheLangFile` (the name key); `ModdedLibraryGameTests` (it has a
copy; without the mods the vanilla build).

Library-wide checks it must pass (both drawings where they apply): `QaBuildReachGameTests`, `QaDoorwayClearanceGameTests`,
`QaChairFacingGameTests`, `QaLedgerFramesHaulGameTests`, `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`,
`B648GameTests`, `QaWindowsAndWards1028BugTests` / `QaBuilders1028GameTests` (no pane or bar joined on one side with air
on the other), `BlueprintsStayGameTests`, `QaFurnishedRooms1031GameTests` (no standing lantern on a room's floor; every
indoor chest against something), `FurnitureSpotGameTests`, `FloatingFurnishingsGameTests` (nothing that falls off what
it stands on: a lantern can't stand on a chest), `StyleGameTests.chimneysStayWholeInEveryStyle` (only if a campfire
stands on a stack), `ModdedLibraryCompatTests.everyModdedCopyCanBeBuiltWithThePacksMods`.

The owner's rules (`check.py`; `open_panes`), STYLE.md's checks and `holes.py`, for both drawings: `rebuild.py
--candidate` prints no CHECK line, and `holes.py` passes. The ship step keeps both old drawings in `blueprint_history/`
(`terminal_nook/` and `modded/terminal_nook/` already hold `6865f401b`; `history.py`), regenerates through `generate.py`
(`save_storage_halls`), sets `StarterBlueprints.TERMINAL_NOOK`'s size, re-pins the coordinates and counts above (in
`StorageHallGameTests`, `QaStorageHallGameTests`, `QaBuilderStockGameTests`, `StorageHallsCompatTests`,
`StorageHallScene`), and updates the wiki (`furniture-and-storage.md`, "Storage builds": today it says "9 x 10 x 9" and
"six chests round a connector along its back wall") and the `storage_hall` scene's caption in `tools/showcase/scenes.py`.

## Free
Everything not listed above: shape, plan, roof, materials, colors, storeys, size within 12 x 12 x 18, where the
Storehouse block, the connector, the terminal and the stores stand (within reach of each other), how the network is
joined (touching, trims or cables), how many stores beyond the minimums, other furniture, and what it shows from the street.
