# Warehouse: the brief

Family id(s): `warehouse` (one level, never renamed; "Warehouse" in `en_us.json`, `blueprint.aliveworkplace.warehouse`;
hidden advancement `built/warehouse`)
Variants: village (none), modded (**two drawings to make**: the vanilla build `structure/warehouse.nbt` and its Tom's
Simple Storage copy `structure/modded/warehouse.nbt`, drawn together from one plan by `storage_halls.py`;
`modded_library.py` leaves it alone, `OWN_COPIES`), looks (none drawn; the Table's style buttons restyle it automatically).

ROADMAP 37.17, the biggest of the three storage builds (`StarterBlueprints.STORAGE_HALLS`, with the Storage Hall and
the Terminal Nook). Not a flagship: one version.

## What it is for
A big Storehouse (37.17: "a big Warehouse (two floors, rows of racks, a Crafting Terminal, a loading bay with a
cart)"; only the height and the crafting terminal are tested). Its one **Storehouse block** takes a Porter and is the
village's store: the village, porters, builders, the board and the Village Map count it as one Storehouse (a trade
building on the map, `VillageMaps.Kind.TRADE`). Every store within **8 blocks across and 4 up or down** of that block
(`SupplyContainers.RADIUS`, `VERTICAL`, a box) is the store, so a store on an upper floor counts only within 4 blocks
above the block. With Tom's Simple Storage installed (and `moddedLibrary` and `furnishedBuilds` on) a new one is built
from the Tom's copy: every chest and barrel on both floors joins one Tom's network through one Inventory Connector, the
Storehouse reads the network through it (each item once, 37.10), and the crafting terminal shows the store in Tom's own
screen. Without Tom's it is the vanilla build, every chest and barrel its own store. At the Blueprint Table while
Milestone 37's expansion is open (`moddedStorageNetworks`); hidden while it is closed but still loads by id.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `warehouse` | A Storehouse block with 48+ stores in its reach, on two floors; with Tom's, one network of them and a crafting terminal | At the Table while M37 is open (`StarterBlueprints.expansionSwitches`: `moddedStorageNetworks`); `ONE_TIER` |

**One level only.** A `warehouse_2` would be a new feature.

## Must have
**The vanilla build** (`StorageHallGameTests.theStorageBuildsAreAtTheTableAsVanillaBuilds`, `aBuilderBuildsTheWarehouseAsAStorehouse`):
- **Exactly one Storehouse block**, a job site once built. No other job block is needed; each one drawn (a barrel is a
  Fisherman's, a crafting table a Carpenter's) may pull a villager to a job.
- **At least 48 chest and barrel blocks** (`minecraft:chest` or `minecraft:barrel`; a double chest counts 2 here).
- **Every chest and barrel of the build is in the Storehouse's store**: within 8 across and 4 up or down of the
  Storehouse block (all 48+ inside a 17 x 9 x 17 box round it); the test wants the store found inside the build's box
  to be exactly the build's number of stores (a double chest is one store). Drawn empty.
- **Two floors:** the declared height is at least 8 (`QaModdedStorageCompatTests`: "not two floors" otherwise); the
  upper floor reached by stairs or a ladder from inside.
- **No modded block** but the Storehouse block.
- Every store reachable on foot within 2 across and 1 up or down (`QaBuildReachGameTests`), upstairs too; openable
  (nothing solid on a chest's lid); every indoor chest against a wall, a solid block, a bed or another chest
  (`QaFurnishedRooms1031GameTests`).
- **Beds and residents:** none required. **Furniture kinds:** none required (no `blueprint_furniture/warehouse.json`);
  any piece drawn is recorded as a spot and must pass the furniture checks below.
- **Built whole, nothing skipped**: every block placeable by a builder from the materials list.

**The Tom's copy** (`QaModdedStorageCompatTests.theTomsStorageBuildsHoldWhatTheSpecLists`,
`StorageHallsCompatTests.withTomsTheStorageBuildsUseTheirTomsCopies`, `aBuilderBuildsTheWarehouseOnTomsNetwork`;
block ids from Tom's Simple Storage 2.3.0):
- exactly one Storehouse block; at least one `toms_storage:inventory_connector`; at least one
  `toms_storage:crafting_terminal`.
- **The connector joins every store of the build, on both floors, and nothing outside it:** Tom's connector joins the
  inventories touching it or each other, passing through Inventory Trim, within 16 blocks of it
  (`StorageNetworks.TOMS_RANGE`), plus the one inventory each cable connector faces along its cables; the test wants
  exactly the drawn number of members, all inside the build's box, and the Storehouse's store to list the connector and
  none of its members. So the connector stands within the Storehouse block's reach, every store is within 16 blocks of
  the connector, and every store, upstairs included, is joined to it.
- **The crafting terminal is on the network** and usable: a player standing before its screen right-clicks it and
  gets Tom's terminal menu listing the network's items. Items put into the first and the last member count once through
  the Storehouse and once in the terminal.
- **Missing without Tom's: only Tom's blocks**, the connector among them (the copy uses vanilla blocks and Tom's only).
- **The same shape as the vanilla build** (`ModdedLibraryCompatTests.everyModdedCopyCanBeBuiltWithThePacksMods`): the
  same size; every cell the vanilla build fills is filled in the copy, and no cell it leaves as air is filled; doors,
  beds, lecterns, barrels and smithing tables the same block in both. So each Tom's block stands in a cell where the
  vanilla build has some other non-air block, one for one (today: a chest for the connector, a counter stair for the
  crafting terminal, logs for the trims), and a barrel can never become a Tom's block. **Any job block stays the same
  block in both** (B388: a crafting terminal is no job site, so a copy once took the Carpenter's crafting table away).

## Fits
- **Plot: at most 19 wide x 25 deep x 18 tall.** `StorageHallGameTests` and `StorageHallsCompatTests` build it in the
  30 x 20 x 30 `huge_area` at helper (min(9, 28 - width), 2, min(3, 28 - depth)), with the builder's bench at (2, 2, 2),
  the builder at (3, 2, 3) and the material barrels from x 1-6 at z = 5 (rows stacking up); at 19 wide the origin stays
  at x 9, clear of them. Height: from helper y = 2 in a 20-tall area. At least 8 tall (above).
- **Build time:** within 40,000 ticks at build delay 1, builders' help off, vanilla and Tom's.
- **Declared size:** `StarterBlueprints.WAREHOUSE` is `new Vec3i(19, 16, 16)` (no basement); the tests place the build
  from it and the `.nbt` must match (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`), so the ship step sets
  it to the new size.
- y = 0 sits on the clicked block; the front is the z = 0 side.
- **Front and way in:** the z = 0 side faces the player who places it; the way in is there, and a villager 1.95 blocks
  tall walks through it (`QaDoorwayClearanceGameTests`).
- **Neighbours:** none fixed (no Steward rule, no village plan places it). The showcase scene `storage_hall`
  (`StorageHallScene`) sets it at x 32 beside the Terminal Nook at x 18 (the ship step moves them if needed).

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `StorageHallGameTests.aBuilderBuildsTheWarehouseAsAStorehouse`: built from barrels holding exactly its materials,
  every block right, nothing skipped, the **Storehouse at (8, 2, 6)** a job site, its store inside the box exactly
  **61** stores (`build(helper, WAREHOUSE, (8, 2, 6), 61)`).
- `StorageHallsCompatTests.aBuilderBuildsTheWarehouseOnTomsNetwork` (compat, with Tom's): built from the copy, every
  block right, nothing skipped; the **connector at (9, 1, 6)** joins exactly **60** inventories, all inside the build;
  the **Storehouse at (8, 2, 6)** lists the connector and none of them; 10 + 5 cobblestone put in the first and last
  chest or barrel count 15 through the store; the **crafting terminal at (8, 1, 6), screen west** (the player standing
  west of it) opens Tom's terminal menu listing 15.
- `StorageHallGameTests.theStorageBuildsAreAtTheTableAsVanillaBuilds` and `QaModdedStorageCompatTests.theTomsStorageBuildsHoldWhatTheSpecLists`:
  the counts and the height in "Must have".
- In code, not a test: `StorageHallScene` pins the connector at (9, 1, 6) and 60 members.

By id only: `StorageHallGameTests.withMilestone37ClosedTheyAreHidden` (and "the Warehouse is offered again" once
reopened); `QaModdedStorageCompatTests.theStorageSentencesAreInTheLangFile`;
`QaModdedRefresh1115cCompatTests.redrawnModdedCopiesAreOfferedFromTheirShippedDrawingOnly` (a Warehouse standing as
the shipped modded drawing `eaa1c261a` in `blueprint_history/modded/warehouse/` is offered a refresh, one standing as
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
Everything not listed above: shape, plan, roof, materials, colors, how the two floors are split, a loading bay, a cart
or none, where the counter, the terminal, the Storehouse block and the stores stand (within reach of each other), how
the network is joined (touching, trims or cables), how many stores beyond 48, other furniture, and what it shows from
the street.
