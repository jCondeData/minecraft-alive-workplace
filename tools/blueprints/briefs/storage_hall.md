# Storage Hall: the brief

Family id(s): `storage_hall` (one level, never renamed; "Storage Hall" in `en_us.json`,
`blueprint.aliveworkplace.storage_hall`; hidden advancement `built/storage_hall`)
Variants: village (none), modded (**two drawings to make**: the vanilla build `structure/storage_hall.nbt` and its Tom's
Simple Storage copy `structure/modded/storage_hall.nbt`, drawn together from one plan by `storage_halls.py`;
`modded_library.py` leaves it alone, `OWN_COPIES`), looks (none drawn; the Table's style buttons restyle it automatically).

ROADMAP 37.17, one of the three storage builds (`StarterBlueprints.STORAGE_HALLS`, with the Terminal Nook and the
Warehouse). Not a flagship: one version.

## What it is for
A Storehouse built as a hall (owner, 2026-10-22: "put some terminal builds as blueprints"; 37.17: "a hall of 24+
chests and barrels round an Inventory Connector on Inventory Cables, a Storage Terminal and a Crafting Terminal on a
counter by the door, filing cabinets for the paper and maps"). Its one **Storehouse block** takes a Porter and is the
village's store: the village, porters, builders, the board and the Village Map count it as one Storehouse (on the map
as a trade building, `VillageMaps.Kind.TRADE`). Every store within **8 blocks across and 4 up or down** of that block
(`SupplyContainers.RADIUS`, `VERTICAL`, a box) is the store. With Tom's Simple Storage installed (and `moddedLibrary`
and `furnishedBuilds` on) a new one is built from the Tom's copy: every chest, barrel and cabinet joins one Tom's
network through its Inventory Connector, the Storehouse reads the network through the connector (each item counted
once, 37.10), and the terminals show the store in Tom's own screen. Without Tom's it is the vanilla build, every
chest and barrel its own store. At the Blueprint Table while Milestone 37's expansion is open (`moddedStorageNetworks`);
hidden while it is closed but still loads by id.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `storage_hall` | A Storehouse block with 24+ stores in its reach; with Tom's, one network of them with a storage terminal, a crafting terminal and filing cabinets | At the Table while M37 is open (`StarterBlueprints.expansionSwitches`: `moddedStorageNetworks`); `ONE_TIER` |

**One level only.** A `storage_hall_2` would be a new feature.

## Must have
**The vanilla build** (`StorageHallGameTests.theStorageBuildsAreAtTheTableAsVanillaBuilds`, `aBuilderBuildsTheStorageHallAsAStorehouse`):
- **Exactly one Storehouse block**, a job site once built (`getPoiManager().getType(...)` present). No other job block
  is needed; each one drawn (a barrel is a Fisherman's, a crafting table a Carpenter's) may pull a villager to a job.
- **At least 24 chest and barrel blocks** (`minecraft:chest` or `minecraft:barrel`; a double chest counts 2 here).
- **Every chest and barrel of the build is in the Storehouse's store**: within 8 across and 4 up or down of the
  Storehouse block; the test wants the store found inside the build's box to be exactly the build's number of stores
  (a double chest is one store). Drawn empty.
- **No modded block** but the Storehouse block.
- Every store reachable on foot within 2 across and 1 up or down (`QaBuildReachGameTests`), openable (nothing solid on
  a chest's lid), every indoor chest against a wall, a solid block, a bed or another chest (`QaFurnishedRooms1031GameTests`).
- **Beds and residents:** none required. **Furniture kinds:** none required (no `blueprint_furniture/storage_hall.json`);
  any piece drawn is recorded as a spot and must pass the furniture checks below.
- **Built whole, nothing skipped** (`plan.unfinished` empty, `site.skipped() == 0`): every block placeable by a builder
  from the materials list.

**The Tom's copy** (`QaModdedStorageCompatTests.theTomsStorageBuildsHoldWhatTheSpecLists`,
`StorageHallsCompatTests.withTomsTheStorageBuildsUseTheirTomsCopies`, `aBuilderBuildsTheStorageHallOnTomsNetwork`;
block ids from Tom's Simple Storage 2.3.0):
- exactly one Storehouse block; at least one `toms_storage:inventory_connector`; at least one inventory cable (any
  `toms_storage:inventory_cable*`: plain, framed or a cable connector); at least one `toms_storage:storage_terminal`
  and at least one `toms_storage:crafting_terminal`; at least one `toms_storage:filing_cabinet`; at least 24 chests,
  barrels or trapped chests.
- **The connector joins every store of the build and nothing outside it:** Tom's connector joins the inventories
  touching it or each other, passing through Inventory Trim, within 16 blocks of it (`StorageNetworks.TOMS_RANGE`), plus
  the one inventory each cable connector faces along its cables; the test wants exactly the drawn number of members, all
  inside the build's box, and the Storehouse's store to list the connector and none of its members. So the connector
  stands within the Storehouse block's reach, and every chest, barrel and cabinet is on the network.
- **The terminals are on the network** and usable: a player standing before a terminal's screen (or, for a screen set
  facing up into a counter, on the floor north of it) right-clicks it and gets Tom's terminal menu listing the
  network's items. Items put into the first and the last member count once through the Storehouse and once in the
  terminal.
- **Missing without Tom's: only Tom's blocks**, the connector among them (`StorageHallGameTests`: the copy's missing
  blocks are all `toms_storage:`), so the copy uses vanilla blocks and Tom's only.
- **The same shape as the vanilla build** (`ModdedLibraryCompatTests.everyModdedCopyCanBeBuiltWithThePacksMods`): the
  same size; every cell the vanilla build fills is filled in the copy, and no cell it leaves as air is filled; doors,
  beds, lecterns, barrels and smithing tables the same block in both. So each Tom's block stands in a cell where the
  vanilla build has some other non-air block, one for one (today: a chest for the connector and each filing cabinet,
  a counter block and a counter stair for the terminals, floor boards for cables, logs for trims), and a barrel can
  never become a Tom's block. **Any job block stays the same block in both** (B388: a crafting terminal is no job site,
  so a copy once took the Carpenter's crafting table away).

## Fits
- **Plot: at most 19 wide x 17 deep x 18 tall.** All its tests build or paste it in the 30 x 20 x 30 `huge_area`:
  `StorageHallGameTests` and `StorageHallsCompatTests` at helper (min(9, 28 - width), 2, min(3, 28 - depth)) with the
  builder's bench at (2, 2, 2), the builder at (3, 2, 3) and the material barrels from x 1-6 at z = 5 (at 19 wide the
  origin stays at x 9, clear of them); `QaStorageHallGameTests` pastes it at (9, 2, 3) with a test hut built at
  (19-23, 2, 20-24) from a Blueprint Table at (26, 2, 26), so deeper than 17 overlaps the hut (or the ship step moves it).
  Height: from helper y = 2 in a 20-tall area.
- **Build time:** within 30,000 ticks at build delay 1, builders' help off, vanilla and Tom's.
- **Declared size:** `StarterBlueprints.STORAGE_HALL` is `new Vec3i(13, 14, 14)` (no basement); the tests place the
  build from it and the `.nbt` must match (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`), so the ship step
  sets it to the new size.
- y = 0 sits on the clicked block; the front is the z = 0 side.
- **Front and way in:** the z = 0 side faces the player who places it; the way in is there, and a villager 1.95 blocks
  tall walks through it (`QaDoorwayClearanceGameTests`).
- **Neighbours:** none fixed (no Steward rule, no village plan places it). The showcase scene `storage_hall`
  (`StorageHallScene`) sets the Tom's Hall at x 0 and a vanilla one at x -18, so wider than 18 overlaps (the ship step
  moves them).

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `StorageHallGameTests.aBuilderBuildsTheStorageHallAsAStorehouse`: built from barrels holding exactly its materials,
  every block right, nothing skipped, the **Storehouse at (6, 1, 5)** a job site, its store inside the box exactly
  **31** stores (`build(helper, STORAGE_HALL, (6, 1, 5), 31)`).
- `QaStorageHallGameTests` (`HALL_STOREHOUSE` (6, 1, 5), `HALL_STORES` 31):
  `aChestOfTheHallBrokenLeavesTheRestServingWithNothingDuplicated`: the pasted Hall's store is exactly 31; the test
  hut's materials spread in all but the farthest store and 10 diamonds in the farthest (it must be a chest or barrel
  a player breaks); broken, the board counts one chest and 10 items fewer, exactly 10 diamonds lie on the ground, and a
  builder still builds the hut from the other stores, nothing duplicated. The pasted Hall (every step set as the
  builder sets it) must stand whole, with the Storehouse block where pinned.
- `StorageHallsCompatTests.aBuilderBuildsTheStorageHallOnTomsNetwork` (compat, with Tom's): built from the copy, every
  block right, nothing skipped; the **connector at (6, 1, 8)** joins exactly **29** inventories, all inside the build;
  the **Storehouse at (6, 1, 5)** lists the connector and none of them; 10 + 5 cobblestone put in the first and last
  chest or barrel count 15 through the store; the **storage terminal at (5, 1, 5), screen up** (the player on the
  floor north of it) opens Tom's terminal menu listing 15.
- `StorageHallGameTests.theStorageBuildsAreAtTheTableAsVanillaBuilds` and `QaModdedStorageCompatTests.theTomsStorageBuildsHoldWhatTheSpecLists`:
  the counts in "Must have".

By id only: `StorageHallGameTests.withMilestone37ClosedTheyAreHidden`; `QaModdedStorageCompatTests.theStorageSentencesAreInTheLangFile`;
`QaModdedRefresh1115cCompatTests.redrawnModdedCopiesAreOfferedFromTheirShippedDrawingOnly` (a Hall standing as the
shipped modded drawing `eaa1c261a` in `blueprint_history/modded/storage_hall/` is offered a refresh, one standing as
the new copy is not); `ModdedLibraryGameTests` (it has a copy; without the mods the vanilla build).

Library-wide checks it must pass: `QaBuildReachGameTests`, `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`,
`QaLedgerFramesHaulGameTests`, `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `B648GameTests`,
`QaWindowsAndWards1028BugTests` / `QaBuilders1028GameTests` (no pane or bar joined on one side with air on the other),
`BlueprintsStayGameTests`, `QaFurnishedRooms1031GameTests` (no standing lantern on a room's floor; every indoor chest
against something), `FurnitureSpotGameTests`, `FloatingFurnishingsGameTests`, `StyleGameTests.chimneysStayWholeInEveryStyle`
(only if a campfire stands on a stack).

The owner's rules (`check.py`; `open_panes`), STYLE.md's checks and `holes.py`, for both drawings: `rebuild.py
--candidate` prints no CHECK line, and `holes.py` passes. The ship step keeps both old drawings in `blueprint_history/`
(`history.py`), regenerates through `generate.py` (`save_storage_halls`), re-pins the coordinates and counts above,
and updates the wiki (`furniture-and-storage.md`, "Storage builds") and the `storage_hall` scene.

## Free
Everything not listed above: shape, plan, roof, materials, colors, storeys, porch or none, where the counter, the
terminals, the Storehouse block and the stores stand (within reach of each other), how the network is joined (touching,
trims or cables), how many stores beyond 24, other furniture, and what the hall shows from the street.
