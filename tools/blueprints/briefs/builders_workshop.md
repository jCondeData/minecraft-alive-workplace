# Builder's Workshop: the brief

Family id(s): `builders_workshop` (one level, never renamed; "Builder's Workshop" in `en_us.json`, both
`blueprint.aliveworkplace.builders_workshop` and `village_piece.aliveworkplace.builders_workshop`)
Variants: village (`village/<style>_builders_workshop` for the five village styles `plains`, `desert`, `savanna`,
`snowy`, `taiga`, saved by `generate.py`; Repurposed Structures' villages reuse these five, `VillageHouses.MODDED_HOUSE_POOLS`;
the village variant has its own contract, see "The village variant"), modded (none to draw: `modded_library.py` makes
`structure/modded/builders_workshop.nbt` from the vanilla drawing and the ship step reruns it), looks (none).
The Table's style buttons (`BlueprintStyles`) restyle the drawing automatically; no test names a styled workshop.

A flagship family (ROADMAP 50.1): two designers (A and B) work from this one brief.

## What it is for
The Builder's own workplace. A Builder's job block is the **Blueprint Table** (`ModVillagers.BUILDER`: a Blueprint
Table or an old Builder's Bench is his job site). The building gives one Builder a table to work from, a bed and a
stash of building supplies beside the table. A player picks it at the Blueprint Table (it is in every new village's
starting set, `blueprint_unlocks/starting.json`) and hands it to a builder; the Steward builds it by himself in a
`workshops` zone when a Builder has no workstation (`steward_rules/workplace_builders_workshop.json`, priority 62,
cooldown 2 days), and his renewal of an old Builder's house rebuilds it as this one
(`steward_renewal/builder.json`: the first building that fits the old footprint plus 3 blocks a side). Once it is
built, a builder (or a jobless villager) walks in and takes its table; the builder's supplies are then the chests
around that table (`SupplyContainers`: every storage block within **8 blocks across and 4 up or down** of his table),
plus the village's Storehouse stock. A player sneak-clicks the table to move a builder there (`BenchMoves`), and a
player can open the table's Blueprint Table screen. Questbook chapter 2 asks the player to build one
(`chapters.py`, `built("builders_workshop")`), and finishing it grants the hidden advancement `built/builders_workshop`.
In world generation, about every other plains, desert, savanna, snowy or taiga village grows the village variant with
a jobless villager who takes its table and becomes the village's Builder, and a chest of building supplies.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `builders_workshop` | One Builder's workstation (the Blueprint Table), his bed, his supply chest(s) by the table | In the Table's starting set (`starting.json`); Steward rule `workplace_builders_workshop`; renewal list `builder.json`; questbook chapter 2 |

**One level only.** There is no `builders_workshop_2` and none may be drawn: a second level is a new feature (a Java
`StarterBlueprints` entry, upgrade offers) and would change the counted upgrades in
`BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (exactly 64).

## Must have
- **Job block: exactly one `aliveworkplace:blueprint_table`** in the file
  (`WorkplacesGameTests.workplaceBuilt_builders_workshop`: "hasn't one Blueprint Table"). **No other job block of any
  job**, vanilla or the mod's: no Builder's Bench (also a Builder's job site: the arriving builder must take the
  *table*, the test checks his `JOB_SITE` is that table), and no barrel, crafting table, composter, smoker, lectern,
  loom, stonecutter, grindstone, cauldron, brewing stand, blast furnace, fletching/smithing/cartography table, bell or
  any of the mod's workstations (each takes a villager for another job; `workplaces.py` header: "exactly one of the
  job's block ... no other vanilla job block"). A chest or furnace is not a job block.
- **The table is reachable on foot from outside**, through the way in, within a player's reach (2 across, 1 up or down)
  of a cell a villager walks to (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`), and a
  builder villager waiting in front of the building walks in and claims it (`workplaceBuilt_builders_workshop`, see
  "Rules tests check").
- **Beds and residents.** At least **one bed**, the Builder's (the family contract in `workplaces.py`: "a bed for the
  worker"; every bed counts as a village bed for the Steward, `StewardConditions.beds`, and the Table shows "Beds: N").
  Its head is reachable on foot (`QaBuildReachGameTests`) and its foot has its head one block towards its facing
  (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`). More beds are free.
- **Storage.** At least **one chest within the builder's supply reach of the table** (8 across, 4 up or down,
  `SupplyContainers.RADIUS`/`VERTICAL`): it is the builder's own stash, which the old builds, the wiki
  (`builder.md`: "the workshop's table, with its own chest beside it") and B290/B297 rely on. The chest is drawn empty
  (builders never fill or copy containers; no loot table in the buildable). Every chest is reachable on foot
  (`QaBuildReachGameTests`). More chests or shelves are free; **no barrel** (a Fisherman's job block). With Carved Wood
  installed the modded copy turns each chest into the build's wood's chest (automatic).
- **Furniture spots.** No kind is required by any test for this id (it is not a home: `FurnishedHomesGameTests` does
  not list it). Every furniture piece the drawing has is recorded as a spot (`rebuild.py`), and each spot's blocks are
  exactly the template's blocks there (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); nothing small
  stands on air, vanilla or furnished (`FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece`); every
  chair stair faces its table, turned and mirrored too (`QaChairFacingGameTests`).
- **A way in on the z = 0 side** (the front), on the ground floor, that a villager 1.95 blocks tall walks through:
  every wooden doorway has room for him (`QaDoorwayClearanceGameTests`; B170: a carpet just inside a 2-tall doorway
  stopped a worker for good). Any upper floor or loft holding a bed, chest or the table is reached by a ladder or
  stairs from inside (B149, `QaBuildReachGameTests`).
- **No trapdoor in the front walk row**: no block named `*_trapdoor` at z = 1, y = 0 or 1
  (`VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow`, which reads `structure/builders_workshop.nbt` and
  every village variant).
- **No calcite anywhere in the buildable drawing**, and no jigsaw, structure void or entity (villager, frame, painting
  ... none) in the file (`WorkplacesGameTests.workplaceBuilt_builders_workshop`: "still has calcite, a jigsaw or
  structure voids", "comes with a villager"). This is a block rule a test checks, not a look: the test uses calcite to
  catch the old village house shipped as the buildable.
- Every block is one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).

## Fits
- **Plot: at most 18 wide x 18 tall x 20 deep** (`WorkplacesGameTests.workplaceBuilt_builders_workshop` builds it at
  helper (12, 2, 10) in the 30 x 20 x 30 `huge_area`, so x 12..29, y 2..19, z 10..29; the builder's bench and chests
  stand at x 3, west of it). y = 0 sits on the clicked block; the front is the z = 0 side.
- **Material budget: at most 81 stacks** (that test stocks three chests of 27 with exactly what the supply report says
  is missing, each item rounded up to whole stacks). The old drawing is 465 blocks.
- **Build time:** the whole build, then the worker walking in and claiming the table, within **12,000 ticks**
  (build delay 2, builders' help off). A much bigger build risks it.
- **Declared size:** `StarterBlueprints.BUILDERS_WORKSHOP` is `workplace("builders_workshop", 13, 13, 11)` today; if the
  size changes the ship step changes that line (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: sizes
  match), and `modded_library.py` reads the sizes from that file.
- **Front and way in:** the z = 0 side faces the player who places it and the road of a Steward plot; the way in is
  there. The arriving worker waits 3 blocks in front of the front edge, at template x = 6 (see below), and must walk in
  by the front.
- **Neighbours:** a workplace in a Steward `workshops` zone; the Steward keeps 2 blocks clear round its plot
  (`Plots.CLEAR`) and wants the ground under it within 4 blocks of level (`Plots.MAX_STEP`). A renewal takes it only
  where it fits the old house's footprint plus 3 a side, so a smaller footprint renews more old houses (free).

## The village variant (`village/<style>_builders_workshop`, five styles)
Village houses share one contract that the Java reads by template coordinate (`world/VillagePieces`,
`world/VillageHouses`, `village.py`'s `village_house`). Every village variant keeps it exactly:
- **Size:** placed at helper (4, 1, 4) in the 22 x 18 x 22 `big_area` (`VillageGameTests.workshopTest`), so **at most
  18 wide x 17 tall x 18 deep**. Today 13 x 13 x 11 (`village_looks.py` `DESIGNS`).
- **The street joint:** a `minecraft:jigsaw` at **(4, 1, 0)**, orientation `north_up`, name and target
  `minecraft:building_entrance`, final state `minecraft:structure_void`; **the whole front row z = 0 at y = 0, x 0-8,
  places nothing** (it stays the ground the house landed on), and placing the template the way a village does leaves
  **no structure_void block in the world** (`VillageGameTests.aVillagePlacedWorkshopLeavesNoStructureVoidInTheWorld`,
  `everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`).
- **The front door at (4, 1, 2)** (a door's lower half facing south; that test reads "door" as the block at (4, 1, 2)
  not being air), with a step before it at (4, 0, 1); no trapdoor at z = 1, y 0-1.
- **The room:** template **x 2-6, z 3-7, y up to 4** is "the room" (`VillagePieces.ROOM_MIN_X ... ROOM_MAX_Y`): the
  house is recognised by its room (over 50 % of the room's blocks standing), and a hall look change rebuilds everything
  *outside* the room in another style (`PieceLooks`, the `outside/<style>/builders_workshop` blueprints), keeping the
  room. So the room is the shell's inside at ground level, bounded by the west wall at x = 1, the front wall at z = 2,
  an east wall at x = 7 and a back wall at z = 8; whatever the look puts inside that box counts as the room. Anything
  that hangs or leans on a block outside the room (a ladder's wall, a lantern's beam) is kept by the look change.
- **The bed: foot (6, 1, 6), head (6, 1, 7), facing south.** `VillagePieces.find` takes **the plains workshop's bed
  head** as the reference cell for finding *every* village house of ours (all share that cell), so this bed must stay
  exactly there in every style; moving it breaks finding the Guard House and every other house too (the hall's house
  looks, `QaHouseLooks*`, `QaBuilderOwnership1104bGameTests`).
- **The fit-out** (drawn by `village.py`'s `workshop_fit_out`, moved in by one): exactly one Blueprint Table at
  **(2, 1, 7)** facing east (`everyVillageTypeCanGrowABuildersWorkshop`: "should have one Blueprint Table"), and a chest
  at **(2, 1, 6)** with `LootTable` `aliveworkplace:chests/village_builders_workshop` ("should have a supply chest").
  Today it also has a crafting table at (2, 1, 3) and scaffolding at (6, 1, 3); note the crafting table is a
  Carpenter's job site (`ModVillagers.CARPENTER`), against "exactly one job block", which the ship step should weigh.
- **The villager:** no villager entity in the file. A `minecraft:jigsaw` at **(4, 0, 5)**, orientation `up_north`,
  name/target `minecraft:bottom`, pool `aliveworkplace:village/<style>/workers`, joint `rollable`, final state the
  style's floor block, spawns a jobless villager standing at (4, 1, 5) (`everyVillageTypeCanGrowABuildersWorkshop`:
  "needs a street connection and a villager spawn"); keep (4, 1, 5) and (4, 2, 5) clear.
- **Moving in:** a jobless villager at template (3, 1, 5) inside, and one at template **(0, 1, 6)** outside by the west
  wall, each becomes a Builder within 2,400 ticks (`VillageGameTests.aVillagerMovesIntoTheWorkshop`,
  `aVillagerOutsideTheWorkshopFindsItsTable`), the house placed as a village places it (its air skipped). So (3, 1, 5)
  and (0, 1, 6) are standable air with room for a villager, and from (0, 1, 6) there is a walk round to the front door.
- Every pool lists it: `everyVillageTypeCanGrowABuildersWorkshop` (vanilla pools), `VillageCompatTests.repurposedStructuresVillagesGetOurHouses`
  (Repurposed Structures' pools) by id only.

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `WorkplacesGameTests.workplaceBuilt_builders_workshop` (the `builds()` generator; `WORKER`, `HOUSE`, `BENCH`,
  `CHESTS`): the house at helper (12, 2, 10); the waiting worker at helper (18, 2, 7), i.e. template (6, 0, -3), three
  blocks before the front at x = 6. Built from its supply report, then the worker (a Builder with no job site) is let
  go and must hold the table as his `JOB_SITE` while still a Builder; exactly one table built inside the box.
- `VillageGameTests.workshopTest` (`aVillagerMovesIntoTheWorkshop` spawn helper (7, 2, 9) = template (3, 1, 5);
  `aVillagerOutsideTheWorkshopFindsItsTable` spawn helper (4, 2, 10) = template (0, 1, 6); its failure clues read the
  table at `origin.offset(2, 1, 7)`).
- `VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`: placed at helper (2, 2, 2),
  clipped to x -1..10, y 0..11, z -1..11; the 9 ground cells x 0-8 at z 0 stay dirt; the door at (4, 1, 2).
- In code, not a test: `VillagePieces` room box and bed-head reference; `village.py` fit-out cells (above).

By id only (the drawing doesn't matter beyond the rules above): `WorkplacesGameTests.workplaceRule_builders_workshop_builder`,
`wantedJobWithNoFreeBlockWishesItsBuilding`, `noWantedJobWithoutAJoblessVillager`; `QaWorkplaceCopiesGameTests`
(the name "Builder's Workshop", in the Table, loads); `QaQuestbookClaims1116eGameTests.qaEveryBuildAQuestSaysToPickIsOnANewVillagesTable`;
`QaBlueprintLibraryVerify1108bGameTests.aNewVillageStartsWithTheWikisSetAndTheRestLockedOrRare` (starting set);
`ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`, `withoutTheModsEveryBuildIsTheVanillaBuild`.

Library-wide checks it must pass: `B648GameTests` and `QaWindowsAndWards1028BugTests` / `QaBuilders1028GameTests`
(no pane or bar joined on one side with air on the other, as stored and as the game joins it),
`QaFurnishedRooms1031GameTests` (no standing lantern on a room's floor; every indoor chest against a wall, a solid
block, a bed or another chest), `BlueprintsStayGameTests`, `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`,
`QaBuildReachGameTests`, `QaLedgerFramesHaulGameTests` (frames and paintings hang turned and mirrored),
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `StyleGameTests.chimneysStayWholeInEveryStyle`
(only if a campfire stands on a solid stack: it must stay solid in every style).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair; `open_panes`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no
CHECK line, and `holes.py` passes. Saves stay safe: the ship step keeps the old drawing in `blueprint_history/`
(`history.py`; the village variants already have `blueprint_history/village/<style>_builders_workshop/`) and regenerates
the village and modded variants with the family.

## Free
Everything not listed above: shape, plan, roof, materials, colors, how many rooms and storeys, a yard or none, a second
door, what the builder's trade shows from the street, where the table, chest and bed stand inside the building (within
the rules above, and the village variant's fixed cells), how many beds and chests beyond the minimum, lighting, and the
village variant's look outside its room in each of the five styles.
