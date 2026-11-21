# Crafter's Workshop: the brief

Family id(s): `crafters_workshop` (one level, never renamed; "Crafter's Workshop" in `en_us.json`, both
`blueprint.aliveworkplace.crafters_workshop` and `village_piece.aliveworkplace.crafters_workshop`; hidden advancement
`built/crafters_workshop`)
Variants: village (`village/<style>_crafters_workshop` for the five village styles `plains`, `desert`, `savanna`,
`snowy`, `taiga`, saved by `generate.py` through `village.py`'s `village_house` with the room `crafters_room` and the job
`aliveworkplace:crafter`; it has its own contract, see "The village variant"), modded (none to draw:
`modded_library.py` makes `structure/modded/crafters_workshop.nbt` from the vanilla drawing, turning chests into Carved
Wood chests, and the ship step reruns it), looks (none). ROADMAP item: 50.7 (Milestone 50, the workshops, same method
as 49).

## What it is for
**A retired building that must keep standing.** It was the Crafter's workplace (ROADMAP 37.14, 37.16); the owner
retired the Crafter and his workshop on 2026-10-21 (37.14a, 37.16a: "existing workshops stay as plain buildings").
Today nobody works here: no villager takes a Crafter's Workbench (`ModVillagers.CRAFTER`'s acquirable test is
`holder -> false`), a Crafter from an old save is made jobless the first time he loads (`craft/Crafters`), and the
crafting for builders is done by each job's own chain (wiki `crafter.md`). The Blueprint Table no longer offers the
workshop (`StarterBlueprints.JOB_ON`: `crafters` is always off), the Steward has no rule that builds it, and villages no
longer grow it (`VillageHouses.LUXURY_HOUSES`: `crafters_workshop -> false`). What still reaches it: a workshop a
player already built or a village already grew (the hall's Repairs page can **Refresh** it to the latest drawing,
`build/Refreshes`), a blueprint already bought or an unfinished build site (both load the drawing **by id**), and an
operator's command (`BlueprintLibrary.list(server, true)`). On the Village Map it is a Workshops building
(`VillageMaps.kindOf`, by name). The new drawing is what those players get: a working building of the village's
workshop quarter with no job of its own.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `crafters_workshop` | A workshop building that stands in the village's workshop quarter; no workstation, no worker | Not offered (`StarterBlueprints.offered` false), no Steward rule, no renewal list, no questbook chapter; loads by id |

**One level only.** It is in `StarterBlueprints.ONE_TIER`; there is no `crafters_workshop_2` and none may be drawn (a
second level is a new feature for a retired job, and would change the counted upgrades in
`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`, exactly 64).

## Must have
- **No Crafter's Workbench (`aliveworkplace:crafters_workbench`) anywhere in the buildable drawing.** The item can't be
  had any more: no recipe, no creative tab, no trade, no data list offers it
  (`QaCrafterWorkbenchGameTests.noRecipeMakesTheCraftersWorkbench`, `noCreativeTabShowsTheCraftersWorkbench`,
  `noTradeOffersTheCraftersWorkbench`), so a builder working a bought blueprint or a fresh build site of a drawing that
  needs one waits for it forever. A Refresh doesn't keep an old one either (`Refreshes.kept` keeps only acquirable job
  sites, beds slept in, containers and the builder's bench). The old drawing has one; the new one must not.
- **Never name the workbench in a furniture spots file.** `QaCrafterWorkbenchGameTests.noDataListOffersTheCraftersWorkbench`
  reads every JSON under `blueprint_furniture/` (the buildable's and the five village files) and fails if one contains
  `aliveworkplace:crafters_workbench`. The village variant's workbench must stay out of its spots file (today the spots
  are only the bed).
- **No job block of any job** (code, not a test: 37.16a's "plain building"). Every acquirable workstation drawn is a
  free job block the Village Hall lists and `StewardJobs` hands to a jobless villager, and vanilla villagers take them by
  themselves: no crafting table (the Carpenter's, `CRAFTING_TABLE_POI`; the hall lists it as "1 free Crafting Table",
  `B635GameTests`), no Carpenter's Bench, Furni Crafter, smithing table, stonecutter, loom, barrel, composter, lectern,
  grindstone, cauldron, brewing stand, blast furnace, smoker, fletching or cartography table, bell, nor any of the mod's
  workstations. A chest, furnace or anvil is not a job block.
- **Beds and residents:** none required (no code counts a resident here; the old one has one bed). A bed is free; if
  drawn, it counts as a village bed (`StewardConditions.beds`), its head is reachable on foot
  (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`), its foot has its head one block
  towards its facing (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`), its head is against a wall
  if the build has a spots file with any piece other than a bed (`FurnishedRoomsGameTests.everyBedHasItsHeadToAWall`), and
  no bed stands right inside a doorway (`check.py`).
- **Storage:** none required (no job reads it). Chests are free; each one drawn is reachable on foot
  (`QaBuildReachGameTests`), drawn empty (builders never fill containers; no loot table in the buildable), and, if the
  build has a spots file with furniture, stands against a wall, a solid block, a bed or another chest
  (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`). A barrel is a job block (above), so no barrel.
- **Furniture spots:** no kind is required (not a home, not a listed workplace). Every piece drawn is recorded as a spot
  (`rebuild.py`), each spot's blocks are exactly the template's (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`),
  nothing small stands on air (`FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece`), every chair stair
  faces its table (`QaChairFacingGameTests`), and with any furniture beyond a bed: every furnished room has its own light
  (`FurnishedRoomsGameTests.everyFurnishedRoomHasALight`, `furniture.py`'s `dark_rooms`), no standing lantern on a room's
  floor (`QaFurnishedRooms1031GameTests.noLanternStandsOnARoomsFloor`), nothing in a door's way
  (`FurnishedRoomsGameTests.noFurnitureStandsInADoorsWay`).
- **A way in on the front (z = 0) side**, on the ground floor, that a villager 1.95 blocks tall walks through
  (`QaDoorwayClearanceGameTests`; B170). Any upper floor or loft with a chest or bed is reached by a ladder or stairs
  from inside (B149, `QaBuildReachGameTests`).
- Every block is one a builder can place, and the file holds no jigsaw, structure void or entity
  (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`; `village.py`'s `workplace` contract:
  "no jigsaws, structure voids or villager").

## Fits
- **Plot: no GameTest builds it**, so no test area limits it, and no build-to-DONE tick budget or material budget
  applies. The one size rule: `StarterBlueprints.CRAFTERS_WORKSHOP` declares `(13, 11, 11)` and
  `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable` checks the file matches; staying within **13 wide x 11 tall
  x 11 deep** needs no Java change, a different size means the ship step edits that line (and its javadoc, which
  describes the old look) and `modded_library.py` reads it from there. A workshop that already stands is refreshed in
  place at the same origin and facing, so a bigger footprint spills over what players built round it (free, but keep
  it in mind). The old drawing is 453 blocks.
- y = 0 sits on the clicked block; the front is the z = 0 side.
- **Front and way in:** the z = 0 side faces the player who places it; the way in is there.
- **Neighbours:** none planned (no Steward rule or renewal places it any more).

## The village variant (`village/<style>_crafters_workshop`, five styles)
Villages no longer grow it, but the five pieces must stay and load (`CrafterWorkshopGameTests.villagesNoLongerGrowTheWorkshop`:
"the <style> workshop's piece is gone"), because villages already grew them. Redrawing its look is optional (lane's
call). If it is redrawn, the designer draws only the look (`village_looks.py` `DESIGNS["crafters_workshop"]`, today
10 x 11 x 11); `village.py` adds the jigsaw, room fit-out, bed and villager, and every style keeps exactly:
- **The street joint:** a `minecraft:jigsaw` at **(4, 1, 0)**, orientation `north_up`, name and target
  `minecraft:building_entrance`, final state `minecraft:structure_void`; **the front row z = 0 at y = 0 places nothing**
  (x 0-8 checked), and placing it the way a village does leaves no structure_void in the world
  (`VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`, placed at helper (2, 2, 2) and
  clipped to x -1..10, y 0..11, z -1..11).
- **The front door at (4, 1, 2)** (that test reads the block there as not air), with a step before it at (4, 0, 1);
  **no `*_trapdoor` at z = 1, y 0-1** (`VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow`, B69).
- **The room** template x 2-6, z 3-7, y up to 4 (`VillagePieces.ROOM_*`), bounded by the west wall at x = 1, the front
  wall at z = 2, x = 7 and z = 8; the bed at foot (6, 1, 6), head (6, 1, 7), facing south (the shared village-house
  layout `village.py` draws into).
- **The fit-out** (`village.py`'s `crafters_room`): **exactly one Crafter's Workbench**, today at (2, 1, 7), and a chest
  at (2, 1, 6) with `LootTable` `aliveworkplace:chests/village_crafters_workshop`, plus a log and slabs. `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`:
  "should have one Crafter's Workbench", and **exactly one job block of any job in the whole template** (the workbench;
  the look may add no workstation of any kind).
- **The villager:** one villager entity already a Crafter (`aliveworkplace:crafter`) at (5, 1, 5) (entity 5.5, 1, 5.5)
  and **no** worker-pool spawn jigsaw (`everyVillageTypeCanGrowTheOtherHouses`: "should come with a aliveworkplace:crafter");
  (5, 1, 5) and (5, 2, 5) stay clear air over floor. (He turns jobless when he loads, `craft/Crafters`; that's the code's
  job, not the drawing's.)
- It must **not** be in any house pool (`everyVillageTypeCanGrowTheOtherHouses`: "can't grow"; `VillageHouses.houseNames()`
  is pinned without it).

## Rules tests check
By id (the drawing only has to exist, load and be named):
- `CrafterWorkshopGameTests.theWorkshopIsNoLongerOfferedButStillLoads`: not offered, not in the Table's list, still loads
  by id, `VillageMaps.kindOf` is `WORKSHOPS`, both lang keys exist.
- `CrafterWorkshopGameTests.theStewardNoLongerPlansAWorkshop`: no Steward rule names it (don't add one).
- `CrafterWorkshopGameTests.villagesNoLongerGrowTheWorkshop`: the five village pieces load and are in no pool.
- `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`, `withoutTheModsEveryBuildIsTheVanillaBuild` (it is in
  `ONE_TIER`): the modded copy exists (regenerated by the ship step).
- `QaCrafterWorkbenchGameTests.noDataListOffersTheCraftersWorkbench`: no spots file names the workbench (above).

Drawing rules:
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: size equals the declared size; every
  block buildable.
- `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`, `everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`,
  `noVillageHouseHasATrapdoorInItsFrontWalkRow` (village variant, above).

Library-wide checks it must pass (each reads every blueprint, this one included, through `BlueprintLibrary.list(server, true)`
or every `.nbt`): `QaBuildReachGameTests` (chests and bed heads within reach of a walked cell), `QaDoorwayClearanceGameTests`,
`QaChairFacingGameTests`, `BlueprintsStayGameTests`, `B648GameTests` and `QaWindowsAndWards1028BugTests` (no pane or bar
joined on one side with air on the other), `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`,
`QaLedgerFramesHaulGameTests` (frames and paintings hang turned and mirrored), `FurnitureSpotGameTests`,
`FloatingFurnishingsGameTests`, and with furniture beyond a bed `FurnishedRoomsGameTests` and `QaFurnishedRooms1031GameTests`.
Not in `StarterBlueprints.ALL`, so `QaMirroredBuildsGameTests`, `QaTurnedFramesGameTests`, `QaTurnedModBlocksGameTests`,
`HomesSpecGameTests` and `StyleGameTests` (offered builds only) don't read it.

No showcase scene, devclient scene or GameTest pins a coordinate of the buildable drawing.

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair, no bed right inside a door; `open_panes`), STYLE.md's checks and `holes.py`:
`rebuild.py --candidate` prints no CHECK line, and `holes.py` passes. Saves stay safe: the ship step keeps the old
drawings in `blueprint_history/` (`history.py`, so a standing workshop is offered the Refresh) and regenerates the modded
copy, the village variants (if redrawn) and the spots files (`blueprint_furniture/crafters_workshop.json`,
`village/<style>_crafters_workshop.json`) with the family.

## Free
Everything not listed above: shape, plan, roof, materials, colors, storeys, what kind of workshop it reads as from the
street, rooms, a yard or store or none, beds, chests, furniture and lighting (within the rules above), and the village
variant's look outside its room in each of the five styles.
