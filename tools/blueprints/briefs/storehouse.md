# Storehouse: the brief

Family id(s): `storehouse`, `storehouse_2`, `storehouse_3` (never renamed; "Storehouse" in `en_us.json`; hidden
advancements `built/storehouse`, `built/storehouse_2`, `built/storehouse_3`)
Variants: village (`village/<style>_storehouse` for the five village styles `plains`, `desert`, `savanna`, `snowy`,
`taiga`, saved by `generate.py` through `village.py`'s `village_house` with the fit-out `storehouse_room` and no job, so
a jobless villager from the worker pool moves in; Repurposed Structures' villages reuse these five; the village variant
has its own contract, see "The village variant"), modded (none to draw: `modded_library.py` makes
`structure/modded/storehouse*.nbt` from the vanilla drawing, turning chests into Carved Wood chests, and the ship step
reruns it), looks (the **Sophisticated** style, `blueprint_styles/sophisticated.json`, for all three levels, see "Must
have"; every other style, such as the colony's `sandstone`, is applied by `BlueprintStyles` automatically).

A flagship family (`REBUILD.md`): two designers (A and B) work from this one brief. Three levels.

## What it is for
The village's store. Its one **Storehouse block** (`aliveworkplace:storehouse`) is the Porter's job site (the
`storehouse` POI, one ticket: one porter per block) and the requests board a player right-clicks
(`StorehouseBoard`). Every chest, barrel or other item store within **8 blocks across and 4 up or down** of that block
(`SupplyContainers.RADIUS` = 8, `VERTICAL` = 4, a box, not a sphere) is the village's store: the porter carries what
the other workers make into it (he walks to within 3 blocks of its chest nearest the block, `PorterWork.REACH`, and
fills them all), builders count and take materials from it, the board lists what it holds ("N chests", the items, the
shortages), Hauler Golems and drop boxes deliver to it, and the Steward's `storehouse_grow` rule reads how full it is.
A store outside that box is not the village's, however close it looks. It is in every Blueprint Table's starting set,
the Steward builds one in the market zone, a new colony's builder builds it second, and it grows twice as the store
fills. From Level I it has a **vault**: a cellar 4 deep with more of the store in it (26.15).

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `storehouse` | The Storehouse block, the store round it, the vault (cellar exactly 4 deep, `StarterBlueprints.Entry` basement 4) with stores in it, a way down and a light | In every Table from the start (`blueprint_unlocks/starting.json`); sold by a Novice Porter for 6 emeralds (`ModTrades.porterTrades`, `BlueprintUnlocks` villager sale); in the Settlers' Wagon loot; a colony's second build, beside the camp in the mother village's style (`ColonyArrival`); the Steward's `storehouse` rule (no Storehouse POI and at least 3 villagers) and `workplace_storehouse` rule (a porter wants a workstation) build it in the `market` zone; questbook quest "A Porter: the Storehouse" asks for it built |
| II | `storehouse_2` | **More store** than I, every new store within the block's reach; keeps I (vault included) | Upgrade of `storehouse` (`BlueprintUpgrades`: id + `_2`); the Steward's `storehouse_grow` upgrades a finished Storehouse when its stores are at least 80 % full; in village chests' loot (`village_builders_workshop`, `village_carpenters_workshop`, `village_inn`, `village_kitchen`, ...) |
| III | `storehouse_3` | **More store** than II, every new store within reach; keeps II | Upgrade of `_2`; `storehouse_grow` again. **The last level:** there is no `storehouse_4` (`QaStewardHomesBoundariesGameTests.theStorehouseGrowsFromEightyPercentToIIIAndNoFurther`: with III built the Steward no longer wishes to grow it) |

No code counts how many stores an upgrade adds; the upgrade exists because the store is full, so each level must
hold clearly more than the last, all in reach. The upgrade count in `BuilderGameTests.starterUpgradesKeepMostOfTheirBase`
(64) must not change: all three levels stay, each an upgrade of the one before.

## Must have (each level)
- **Job block: exactly one Storehouse block** (its facing is free: it turns with the build, `QaTurnedModBlocksGameTests`,
  and a different facing still counts as built, `MaterialRules.facingFree`). No other job block is needed. A barrel is
  a store **and** a Fisherman's job site, a crafting table a Carpenter's, a composter a Farmer's...: each one drawn
  may pull a villager to a job, so add one only on purpose. The old drawing has barrels; nothing requires them.
- **Storage.** Chests (or barrels) **within 8 across and 4 up or down of the Storehouse block**, upstairs and in the
  vault alike: with the block on the ground floor at y = 1, the vault's stores must stand at y -3 or higher, and
  anything above y 5 is out. Every store drawn empty (builders never copy contents). Every store must be openable
  (nothing solid on a chest's lid) and reachable on foot: within 2 blocks across and 1 up or down of a spot a villager
  walks to from outside (`QaBuildReachGameTests`), in the vault too (`QaBasementGameTests.everyBasementAndItsChestsCanBeWalkedToFromOutside`).
  Every indoor chest against a wall, a solid block, a bed or another chest (`QaFurnishedRooms1031GameTests`).
  A double chest is one store.
- **One lantern standing on top of the Storehouse block, touching at least two stores.** The Sophisticated style
  swaps the lantern that touches the Storehouse block for a storage controller and every chest and barrel for
  Sophisticated ones (`sophisticated.json`: `minecraft:lantern` near `aliveworkplace:storehouse`).
  `SophisticatedControllerCompatTests.theSophisticatedStyleAddsAController`, for each of the three levels: exactly
  **one** controller (so exactly one lantern touches the Storehouse block, on any of its six sides), it stands **directly
  on top** of the Storehouse block, and at least **two** of the cells touching it (six sides) are chests or barrels; no
  vanilla chest or barrel is left after the swap (so every store is a plain `minecraft:chest` or `minecraft:barrel`,
  not a trapped chest or another kind). The plain build has no controller.
- **The vault (all three levels).** A cellar under the ground floor, exactly 4 deep (floor at y -4, room y -3 to -1;
  the `.nbt` is `size.y + 4` tall, `BasementGameTests.basementsLoadUnderTheGround`), with:
  - stores in it (`QaBasementSeals1107bGameTests.aStorehouseVaultDugBesideLavaBurnsNothing` counts chests below
    y = 0 and wants more than none, every one standing after the build);
  - a way down: a **closed trapdoor in the ground floor (y = 0)** over the cellar with **2 blocks clear above it**
    (air or ladder), and a **ladder of at least 2 rungs** under it, each rung on a wall it hangs from, down to a floor
    a player stands on at the cellar's bottom (`QaBuilderGaps1105bGameTests.assertWayDownWalkable`); some ladder or
    stair on y = -1 with room above it on y 0 and 1 (`QaBasementGameTests.everyBasementsWayDownComesUpThroughTheGroundFloor`);
    a ladder or stair below y = 0 (`basementsLoadUnderTheGround`);
  - a lantern below y = 0, and no dark spot: every air cell on the cellar's floor with air over it has block light
    above 0 from the build's own lights (`basementsLoadUnderTheGround`, `QaBasementGameTests.noBasementLeavesADarkSpotForMonsters`);
  - closed walls and floor of full blocks all round (water, lava and fire in the earth beside or under it must not get
    in: `QaBasementSeals1107bGameTests`, `QaBasementGround1104*`, `QaBasementFluids1104f*`);
  - **an open floor cell on the ground floor over the cellar**: an air cell at y = 1 with a block under it, in a
    column over the cellar that is not the way down's (`QaBuilderGaps1105bGameTests` puts a player's gold block there
    and wants it kept: "setup: no open floor over the cellar" otherwise);
  - the cellar dig must leave everything above as drawn: "Dig the cellar: Storehouse" (4 emeralds) on a Storehouse
    finished before it had one digs only the cellar and the way down, and every chest and barrel above the ground keeps
    its contents (`QaBuilderGaps1105bGameTests`). So nothing above y = 0 may depend on the cellar being there.
- **Beds and residents.** None required (the porter lives elsewhere). A bed, if drawn, is a village bed and must pass
  the bed checks below.
- **Furniture kinds.** None required (there is no `blueprint_furniture/storehouse.json`). Any piece drawn is recorded
  as a spot (`rebuild.py`) and must pass `FurnitureSpotGameTests`, `FloatingFurnishingsGameTests`, `QaChairFacingGameTests`.
- **Placeable and whole**: every block buildable from the materials list (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`:
  no block a builder can't place), standing as drawn when done, nothing skipped.

## Kept cells (every upgrade)
- II is at least I's size in every dimension, III at least II's, on the same origin and front (an upgrade is clicked
  onto the finished build and lines up with it); each keeps **at least 60 %** of the level below's non-air blocks
  exactly (same cell, same state) (`starterUpgradesKeepMostOfTheirBase`). So the Storehouse block, the vault and the
  way down stay where Level I put them.
- The single Storehouse block keeps its cell through the levels (it holds the board's orders and owner; a builder
  never breaks it, `MaterialRules.facingFree`), and every store a level adds is within its reach.

## Fits
- **Level I: at most 13 wide x 13 deep x 16 tall** above the ground. `BuilderGameTests.buildsStorehouse` builds it at
  helper (9, 2, 9) in the 22 x 18 x 22 `big_area` (bench (2, 2, 2), chest (2, 2, 4), villager (3, 2, 3), material
  barrels at x 1-5, z 4). Other tests place it at (8, 10, 9) in the 30 x 30 x 30 `tall_area`, (9, 7, 9), (8, 2, 8)
  turned a quarter and (10, 6, 12) in the 30 x 20 x 30 `huge_area`: all roomier.
- **Level I build budget:** at most **108 stacks** of materials, vault included (`buildsStorehouse` loads exactly the
  plan's materials into 4 barrels of 27 and fails with "test needs more barrels"), built within **9,000 ticks** at
  build delay 2.
- **Levels II and III: at most 23 wide x 18 tall x 24 deep.** `QaReleaseRefresh1028GameTests` refreshes each level
  at helper (7, 2, 2) in `huge_area` with the hall at (1, 2, 1) and a bench at (3, 2, 27).
- **Declared sizes:** `StarterBlueprints.STOREHOUSE` (9, 10, 9), `STOREHOUSE_2` (16, 10, 9), `STOREHOUSE_3`
  (16, 10, 16), each with basement 4; the tests place them from these and the `.nbt` must match
  (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`, `basementsLoadUnderTheGround`), so the ship step sets
  them to the new sizes (and the Settlers' Wagon loot's `size` for `storehouse`).
- y = 0 sits on the clicked block; the front is the z = 0 side.
- **Front and way in:** the z = 0 side faces the player who places it, the road of a Steward plot and, for a colony,
  the camp's way (`ColonyArrival.beside`); the way in is there. A villager 1.95 blocks tall walks through it
  (`QaDoorwayClearanceGameTests`); anything upstairs reached by stairs or a ladder from inside.
- **Neighbours:** in a Steward `market` zone; the Steward keeps 2 blocks clear round its plot (`Plots.CLEAR`) and wants
  the ground under it within 4 blocks of level (`Plots.MAX_STEP`); a renewal takes it only where it fits the old
  build's footprint plus 3 a side (`Renewals.MARGIN`). A colony places Level I beside its camp on dry, even ground
  (`ColonyArrival.beside`: at most 2 blocks of rise, on at most 15 % of its columns), so a smaller footprint finds a
  spot more often; `ColonyArrivalGameTests` wants its middle at least 9 blocks from the camp's.

## The village variant (`village/<style>_storehouse`, five styles)
Village houses share one contract that the Java reads by template coordinate (`world/VillagePieces`,
`world/VillageHouses`, `village.py`'s `village_house`, `village_looks.py` header). The designer draws only the look
(`village_looks.py` `DESIGNS["storehouse"]`, today 11 x 16 x 11); `village.py` adds the jigsaws, the fit-out and the
bed. Every village variant keeps it exactly:
- **The street joint:** a `minecraft:jigsaw` at **(4, 1, 0)**, orientation `north_up`, name and target
  `minecraft:building_entrance`, final state `minecraft:structure_void`; **the whole front row z = 0 at y = 0 places
  nothing** (x 0-8 are checked), and placing it as a village does leaves no structure_void in the world
  (`VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`).
- **The front door at (4, 1, 2)** (lower half, facing south), a step before it at (4, 0, 1), no trapdoor at z = 1,
  y 0-1 (`VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow`, B69).
- **The room:** template **x 2-6, z 3-7, y up to 4** (`VillagePieces.ROOM_*`): the house is recognised by its room and
  a hall look change rebuilds everything outside it; walls at x = 1 and 7, z = 2 and 8.
- **The bed: foot (6, 1, 6), head (6, 1, 7), facing south** (`VillagePieces.find`'s reference cell for every village
  house of ours).
- **The fit-out** (`village.py`'s `storehouse_room`, moved in by one): the **Storehouse block at (2, 1, 7)** facing
  east, **four chests at (2, 1-2, 5-6)**, the one at (2, 1, 6) with `LootTable` `aliveworkplace:chests/village_storehouse`;
  today a hay bale at (2, 1, 4). `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`: "should have one Storehouse"
  and **exactly one job block in the whole template** (no barrel, crafting table, composter... for looks anywhere).
- **The villager:** no entity in the file; the `minecraft:jigsaw` at **(4, 0, 5)** (`up_north`, name/target
  `minecraft:bottom`, pool `aliveworkplace:village/<style>/workers`, joint `rollable`, final state the style's floor)
  spawns a jobless villager who takes the Storehouse ("should spawn a jobless villager from the worker pool"); keep
  (4, 1, 5) and (4, 2, 5) clear with floor under.
- A ladder to every furnished upper floor. Pools list it by id (`everyVillageTypeCanGrowTheOtherHouses` also pins
  `VillageHouses.houseNames()`'s order; `VillageCompatTests.repurposedStructuresVillagesGetOurHouses`).
- No test pins the outer size; it is placed at helper (2, 2, 2) in `big_area`, so at most 20 wide x 16 tall x 20 deep.

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `BasementGameTests.reloadMidDigCarriesOn` (Level I at helper (9, 7, 9), saved and loaded mid-dig and mid-structure):
  finishes with nothing skipped, the cellar empty where drawn empty, a **chest at (6, -3, 4)**.
- `B261GameTests.aStorehouseShedIsRepairedFromItsOwnChests` (Level I at helper (10, 6, 12), its structure and
  decoration set): removes the **hay bale at (2, 1, 5)** and the **ladder at (2, 1, 3)**, puts both in the **chest at
  (3, 1, 6)**; a builder at a bench at (4, 1, -2) (in front of the build) repairs them from that chest. The ship step
  picks two placeable blocks and a chest of the new Level I.
- `QaTurnedModBlocksGameTests` (Level I turned a quarter at (8, 2, 8), free materials, 12,000 ticks): the Storehouse
  block faces the turned way. `B448GameTests` (a finished, turned Level I at (8, 2, 8)): the old-facing block still
  counts as built and keeps its orders.
- `QaBuilderGaps1105bGameTests.aStorehousesDigTheCellarKeepsItsChestsAndLeavesAWalkableWayDown` and
  `aStorehouseHalfDugByHandInAnotherShapeIsStillOfferedAndDugAsDrawn` (Level I at (8, 10, 9) in `tall_area`): the
  trade "Dig the cellar: Storehouse" for 4 emeralds, the whole build as drawn after, every chest above ground keeps the
  player's diamonds, the gold block on the open floor over the cellar stays, the walkable way down, the sentence
  "... dug the cellar under the Storehouse and fitted it out." once.
- `QaBasementSeals1107bGameTests.aStorehouseVaultDugBesideLavaBurnsNothing`: lava in the earth outside the vault's
  west, east and south walls and under its floor, fire spread on: built as drawn, no lava or fire in or round it, every
  vault chest in place, the builder alive and not burning.
- `SophisticatedControllerCompatTests.theSophisticatedStyleAddsAController` (compat, all three levels): see "Must have".
- `ColonyArrivalGameTests` (the `sandstone` Storehouse queued second, its middle at least 9 blocks from the camp's
  middle, on the other side from the cottage); `QaColonyArrival1102GameTests`.
- `StewardHomesRulesGameTests.threeVillagersAndNoStorehouseGetOneInTheMarket`, `aStorehouseEightyPercentFullGrows`;
  `QaStewardHomesBoundariesGameTests.theStorehouseGrowsFromEightyPercentToIIIAndNoFurther` (I grows to II, II to
  III, III no further).

By id only (the drawing doesn't matter beyond the rules above): `VillageHallGameTests.decorationsMakeAVillagePrettier`
(not a decoration), `QaBuiltAdvancements1116bGameTests` (`built/storehouse`), `QaReleaseRefresh1028GameTests`
(`storehouse`, `storehouse_2`, `storehouse_3` refresh from 0.151.0's drawing `cd1557d8c` in `blueprint_history/` to the
new one), `QaQuestbookClaims1116eGameTests`, `QaBlueprintLibraryVerify1108bGameTests` (starting set),
`WorkplacesGameTests.workplaceRule_storehouse_porter` (`workplace_storehouse` builds it in `market`),
`ModdedLibraryGameTests` and `ModdedLibraryCompatTests.everyModdedCopyCanBeBuiltWithThePacksMods` (the modded copy:
same size and basement, doors, beds and barrels unchanged), `QaModdedRefresh1115cCompatTests`.

Library-wide checks it must pass: `QaBuildReachGameTests`, `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`,
`QaLedgerFramesHaulGameTests`, `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `B648GameTests`,
`QaWindowsAndWards1028BugTests` / `QaBuilders1028GameTests` (no pane or bar joined on one side with air on the other),
`BlueprintsStayGameTests` (lamps, paths, bed foot and head), `QaFurnishedRooms1031GameTests` (no standing lantern on a
room's floor; every indoor chest against something), `FurnitureSpotGameTests`, `FloatingFurnishingsGameTests`,
`QaBasementGameTests` (3), `StyleGameTests.chimneysStayWholeInEveryStyle` (only if a campfire stands on a stack).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair; `open_panes`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no
CHECK line, and `holes.py` passes (`storehouse` has no entry in `owner_rules_baseline.json`: none may appear). The ship
step keeps the old drawings in `blueprint_history/` (`history.py`; `blueprint_history/storehouse*`, `modded/storehouse*`
and `village/<style>_storehouse` exist), regenerates the modded copies and the village variants, and updates the
declared sizes, the wiki (`basements.md` describes the vault) and the scenes (`BuildFixesScene`, `ColonyScene`, the
`porter` and `modded_storehouse` scenes).

## Free
Everything not listed above: shape, plan, roof, materials, colors, storeys, how the levels grow (sideways, back or up,
within the plots), where the Storehouse block, the stores and the vault stand (within reach of each other), how many
stores beyond "more each level", a yard or loading bay or none, lighting, and what a store looks like from the street.
