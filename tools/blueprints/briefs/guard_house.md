# Guard House: the brief

Family id(s): `guard_house` (one level, never renamed; "Guard House" in `en_us.json`, both
`blueprint.aliveworkplace.guard_house` and `village_piece.aliveworkplace.guard_house`; `QaWorkplaceCopiesGameTests` and
`PieceLooksGameTests` read "Guard House"; hidden advancement `built/guard_house`).
Variants: village (`village/<style>_guard_house` for the five village styles `plains`, `desert`, `savanna`, `snowy`,
`taiga`, saved by `generate.py` through `village.py`'s `staffed_house` with the job `aliveworkplace:guard`; grown in
every village, with or without Cobblemon; its own contract, see "The village variant"), modded (none to draw:
`modded_library.py` makes `structure/modded/guard_house.nbt` from the vanilla drawing and the ship step reruns it),
looks (none: `BlueprintStyles` restyles the drawing automatically).

## What it is for
The workplace of the village's watch: the **Guard** (`ModVillagers.GUARD`) and the vanilla **Weaponsmith**, who share
the vanilla **grindstone** (`PoiTypes.WEAPONSMITH`; the Guard's other job site is the mod's Guard Post block). The
grindstone is the Guard's **post**: he patrols up to 14 blocks round it by day and 6 at night (`GuardPatrol.DAY_RADIUS`,
`NIGHT_RADIUS`), fights monsters within 24 of it (`Guards.RADIUS`), spars with a Training Dummy within 12 across and 4
up or down of it when one stands there (`GuardPatrol.DUMMY_RADIUS`), and **takes his best weapon, armor, arrows and
potions from the chests within 8 blocks across and 4 up or down of the grindstone** (`GuardPatrol`:
`SupplyContainers.find(level, post, ...)`, `RADIUS` 8, `VERTICAL` 4). The village's crafters deliver his gear **to
those chests** (`WeaponsmithWork`, `FletcherWork`, `SmelterWork`, `AlchemistWork`: "brought to the chests by their
Guard Post"); **with no chest there, the Weaponsmith skips that guard** (`WeaponsmithWork`: `theirs.isEmpty()` → no job).
A **Weaponsmith** at the same grindstone takes his materials from the chests by it and mends the village's worn gear
there (`craft/WeaponsmithWork`, `mend/MendingWork`: what's left goes back "in the chests by the grindstone"). With config
`firewardens` on, a villager given a water bucket there becomes a **Firewarden** (fills buckets at water within 16 of
the grindstone, `FirewardenWork.WATER_REACH`).

A jobless villager takes a grindstone by himself as a Weaponsmith (vanilla); a player makes a Guard by standing a
villager next to the grindstone, holding a sword and sneak-right-clicking him (`work/Stations`, within **4.5 blocks**
of a free grindstone, `Stations.REACH`; the questbook says "Stand a villager right next to the Guard House's
grindstone"). It is in the Blueprint Table without Cobblemon (`StarterBlueprints.WORKPLACES`) and in every new
village's starting set (`blueprint_unlocks/starting.json`); the questbook's "A guard before the first raid" (chapter 2,
Hamlet) completes on `built/guard_house`. The Steward builds it in a **`defences`** zone when a Guard **or a
Weaponsmith** has no workstation, below Town rank (`steward_rules/workplace_guard_house.json`: `worker_without_workstation`
for `aliveworkplace:guard` and `minecraft:weaponsmith`, `wanted: true`, not `rank_at_least town`; priority 62, cooldown
2 days; from a Town the Barracks takes over), and his renewal of an old Guard's or Weaponsmith's house rebuilds it as
this one first, else as the Barracks (`steward_renewal/guard.json`, `weaponsmith.json`). In world generation every
vanilla village style grows the village variant (pool weight 3, `VillageHouses.houses()`) with a Guard already in the
job and a chest of starting gear (loot table `chests/village_guard_house`). The village variant is also the house the
Village Hall's **House looks** page is tested on (`PieceLooksGameTests`, `QaHouseLooks*`; see "The village variant").

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `guard_house` | The post (one grindstone: a Guard's or Weaponsmith's workstation), the worker's bed, a chest in reach of the grindstone | Table starting set (`starting.json`); Steward rule `workplace_guard_house` (defences, below Town); renewal lists `guard.json`, `weaponsmith.json`; questbook |

**One level only.** No `guard_house_2` may be drawn: a second level is a new feature (a `StarterBlueprints` entry,
upgrade offers) and would change the counted upgrades in `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (exactly
64). The Barracks is a separate family (the Town tier), not an upgrade of this one.

## Must have
- **Job block: exactly one `minecraft:grindstone`** in the file (`WorkplacesGameTests.workplaceBuilt_guard_house`:
  "hasn't one Grindstone", and exactly one grindstone built inside the box). **No second grindstone anywhere**, inside or
  out (the count is of the whole file). **No `aliveworkplace:guard_post`** (also the Guard's job site: the arriving Guard
  might take it instead of the grindstone, which the test wants). **No other job block of any job** (`workplaces.py`
  contract: "exactly one of the job's block ... no other vanilla job block"): no barrel, lectern, composter, smoker,
  blast furnace, loom, stonecutter, cauldron (any kind, a water cauldron included), brewing stand,
  crafting/fletching/smithing/cartography table or any of the mod's workstations. An anvil, a chest, a bell, a Training
  Dummy or a target is not a job block.
- **The grindstone is taken by a Guard walking in from the front.** It is reachable on foot from the ground outside,
  within 2 across and 1 up or down of a cell a villager walks to
  (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`), and a Guard waiting in front of the
  build walks in and holds it as his job site (`workplaceBuilt_guard_house`). If it is built in under a solid block,
  some side of it needs a floor cell with 2 blocks of head room (`work/JobSiteApproach`). Leave room by it for a player
  and a villager to stand side by side (the sword-and-sneak-click way of making a Guard needs the villager within 4.5
  blocks of it).
- **Storage the jobs read: at least one chest within 8 blocks across and 4 up or down of the grindstone** (above:
  without one, no Weaponsmith delivers this Guard a weapon, he has nowhere to pick up gear, and a Weaponsmith there has
  no materials or drop-off). No test counts it; the code reads it. Chests are drawn empty (builders never fill
  containers; the build test fails on anything a builder would have to copy). Every chest is reachable on foot
  (`QaBuildReachGameTests`) and an indoor chest stands against a wall, a solid block, a bed or another chest
  (`QaFurnishedRooms1031GameTests`, if furniture spots make it a furnished build). **No barrel** (a Fisherman's job
  block).
- **Beds and residents: at least one bed**, the worker's (`workplaces.py`: "a bed for the worker"; every bed counts for
  the Steward, `StewardConditions.beds`). Its head is reachable on foot (`QaBuildReachGameTests`), its foot has its head
  one block towards its facing (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`), it stays whole
  turned and mirrored (`QaMirroredBuildsGameTests`), and no bed stands right inside a doorway (`check.py`). More beds are
  free. No villager in the file.
- **Furniture spots** (`blueprint_furniture/guard_house.json`, rewritten by `rebuild.py`; today one bed spot): no kind
  is required by any test (`guard_house` is not in `FurnishedWorkplacesGameTests.workplaces()`). No spot's cells may
  cover the grindstone, a container or a bed that isn't the spot's own
  (`FurnishedWorkplacesGameTests.noSpotCoversAJobBlockABedOrAContainer`). Each spot's blocks are exactly the
  template's (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); nothing small stands on air
  (`FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece`); every chair stair faces its table, turned and
  mirrored too (`QaChairFacingGameTests`). Any spot kind other than bed, chair and table makes it a "furnished" build
  for `QaFurnishedRooms1031GameTests` (indoor chests against something, no standing lantern on a room's floor).
- **A way in on the z = 0 side** on the ground floor that a villager 1.95 tall walks through
  (`QaDoorwayClearanceGameTests`; no carpet just inside a 2-tall doorway, B170). **No fence gate or trapdoor on the only
  way in** (villagers open wooden doors, not gates or trapdoors). Any upper floor, loft or roof holding the bed, a chest
  or the grindstone is reached by ladder or stairs from inside (B149, `check.py`, `QaBuildReachGameTests`).
- **No trapdoor in the front walk row**: no `*_trapdoor` at z = 1, y = 0 or 1
  (`VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow` reads `structure/guard_house.nbt`; B69 was first seen
  with this house's guard).
- **No calcite, jigsaw or structure void, and no entity** in the file (`workplaceBuilt_guard_house`: "still has
  calcite, a jigsaw or structure voids", "comes with a villager").
- Every block is one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).

## Fits
- **Plot: at most 18 wide x 18 tall x 20 deep.** `WorkplacesGameTests.workplaceBuilt_guard_house` builds it in the
  30 x 20 x 30 `huge_area` at helper (12, 2, 10) (x 12..29, y 2..19, z 10..29; blocks above an area's height are never
  cleared). The builder's bench (3, 2, 3), his stock chests (3, 2, 6/8/10) and the builder (5, 2, 3) stand west of it.
  y = 0 sits on the clicked block; the front is the z = 0 side.
- **Material budget: at most 81 stacks** (three chests of 27 with exactly what the supply report says is missing, each
  item rounded up to whole stacks). The old drawing is 482 placed blocks.
- **Build time:** the whole build, then the Guard walking in and claiming the grindstone, within the test's **12,000
  ticks** (build delay 2, builders' help off, the clock held in working hours). Keep it to **about 900 placed blocks at
  most** (a 1,410-block School timed out there; about 900 passed), and **no roof, overhang, barge or eave over another
  roof** (that School's builder got stuck on a barge stair above a lower roof).
- **Declared size:** `StarterBlueprints.GUARD_HOUSE` is `workplace("guard_house", 11, 13, 11)` today (no basement); if
  the size changes the ship step changes that line (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable` asserts
  the file's size equals the entry); `modded_library.py` reads the sizes from that file. A basement is allowed only
  with the `Entry`'s basement field set (then `QaBasementGameTests` applies; a cellar never holds a job block).
- **Front and way in:** the z = 0 side faces the player who places it, the road of a Steward `defences` plot, and the
  old door's side in a renewal. The waiting Guard stands at helper (18, 2, 7) = template **(6, 0, -3)**, three blocks
  before the front edge, on the level of the build's y = 0, and must walk in by the front.
- **Renewal fit (no test):** `Renewals.fit` takes the Guard House for an old Weaponsmith's or Guard's house only where
  it fits the old footprint plus `Renewals.MARGIN` = 3 on each side and behind, front on the old door's side; otherwise
  it tries the (bigger) Barracks. A smaller footprint renews more old houses (free).
- **Neighbours:** a workplace in a Steward `defences` zone; the Steward keeps 2 blocks clear round its plot
  (`Plots.CLEAR`, `Plots.MARGIN`) and wants the ground under it within 4 blocks of level (`Plots.MAX_STEP`). The
  Guard's patrol, a Training Dummy and any water a Firewarden uses are measured from the grindstone; nothing else in the
  drawing is read for them.

## The village variant (`village/<style>_guard_house`, five styles)
Village houses share one contract that the Java reads by template coordinate (`world/VillagePieces`,
`world/VillageHouses`, `village.py`'s `village_house`, `village_looks.py` header). The designer draws only the look
(`village_looks.py` `DESIGNS["guard_house"]`, today 9 x 12 x 11); `village.py` adds the jigsaw, fit-out, bed and
villager. Every style keeps it exactly:
- **The street joint:** a `minecraft:jigsaw` at **(4, 1, 0)**, orientation `north_up`, name and target
  `minecraft:building_entrance`, final state `minecraft:structure_void`; **the whole front row z = 0 at y = 0 places
  nothing** (it stays the ground the house landed on), and placing it the way a village does leaves no structure_void
  in the world (`VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`).
- **The front door at (4, 1, 2)** (lower half facing south), a step before it at (4, 0, 1); no trapdoor at z = 1,
  y 0-1 (`noVillageHouseHasATrapdoorInItsFrontWalkRow`).
- **The room:** template **x 2-6, z 3-7, y up to 4** (`VillagePieces.ROOM_MIN_X ... ROOM_MAX_Y`), bounded by the west
  wall at x = 1, the front wall at z = 2, an east wall at x = 7 and a back wall at z = 8. A house is named by its room
  (the house and style whose room blocks stand best, over 50 %), and a House looks change rebuilds **everything outside
  the room** in another style, leaving the room as it is; a block outside the room that holds up a room block (the wall
  behind a ladder, the beam a lantern hangs from) is left out of the outside rebuild too.
- **The bed: foot (6, 1, 6), head (6, 1, 7), facing south, and the only bed in the template.** `VillagePieces.find`
  finds every village house of ours by a bed head at that cell (the plains Builder's Workshop's), and
  `QaHouseLooks1108cGameTests.aFinishedRebuildKeepsEveryItemTheJobBlockAndTheBed` takes the room's bed as the
  resident's home.
- **The fit-out** (`village.py`'s `guard_house`, moved in by one): the **grindstone at (2, 1, 7)** (floor, facing east),
  the **chest at (2, 1, 6)** with `LootTable` `aliveworkplace:chests/village_guard_house`, an anvil at (2, 1, 4).
  `PieceLooksGameTests` and `QaHouseLooks1108cGameTests` pin `GRINDSTONE` (2, 1, 7) and `ROOM_CHEST` (2, 1, 6).
  **Exactly one grindstone and exactly one job block of any job in the whole template**
  (`VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`: "should have one Grindstone", "has N job blocks"): the look
  outside the room adds no workstation.
- **The villager:** one entity, already a **Guard** (`aliveworkplace:guard`), at **(5, 1, 5)**, and no worker-pool
  spawn jigsaw (same test: "should come with a aliveworkplace:guard"); (4, 0, 5) is the style's floor. Keep (5, 1, 5)
  and (5, 2, 5) clear air with floor under them.
- A ladder or stairs to every furnished upper floor (`village_looks.py` header).
- **The House looks tests' budgets** (the plains house placed at helper (13, 2, 10) of the `huge_area`, so at most 17
  wide x 20 deep x 18 tall): changing the plains house to the **desert outside** must need **at most 27 stacks** of
  blocks the plains house doesn't already have at the same cell (`PieceLooksGameTests.stock` and
  `QaHouseLooks1108cGameTests.stock` put it all in **one** chest of 27), and the builder must finish it inside
  `PieceLooksGameTests.ownerGivesAHouseANewOutside`'s **12,000 ticks** from day time 2,000 with the clock *not* held
  (he stops at 11,000, so about 9,000 ticks of work; today the desert outside differs in 334 cells of 351). Also
  `aRebuildSurvivesSaveAndReload` (12,000), `aFinishedRebuildKeepsEveryItemTheJobBlockAndTheBed` (14,000),
  `changingTheLookMidRebuildEndsWithTheLastPick` (16,000, desert then taiga). Each style's outside must also stand at
  least 60 % as drawn to be recognized as that look (`VillagePieces.OUTSIDE_MATCH`), so two styles' outsides must not be
  nearly the same blocks.
- In every vanilla village style's pool, by id, without Cobblemon (`everyVillageTypeCanGrowTheOtherHouses` pins
  `VillageHouses.houseNames()`, `guard_house` first), and in Repurposed Structures' villages
  (`VillageCompatTests.repurposedStructuresVillagesGetOurHouses`). No test pins the outer size beyond the budget above;
  vanilla's generator refuses a piece that overlaps another, so a much bigger house grows less often.

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing if needed, keeping what each checks, dropping none):
- `WorkplacesGameTests.workplaceBuilt_guard_house` (the `builds()` generator; `HOUSE` (12, 2, 10), `WORKER` (18, 2, 7),
  `BENCH` (3, 2, 3), `CHESTS`, `BUILDER` (5, 2, 3); timeout 12,000): no entities, calcite, jigsaws or voids; exactly one
  grindstone in the file and in the built box; built from its supply report (at most 81 stacks); the worker, a Guard
  with no job site, let go after the build, must hold that grindstone as his `JOB_SITE`, still a Guard.
- `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`, `everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`,
  `noVillageHouseHasATrapdoorInItsFrontWalkRow` (the village variant's cells above, and the buildable's front walk row).
- House looks, all on `village/plains_guard_house` at helper (13, 2, 10) with `GRINDSTONE` (2, 1, 7) and
  `ROOM_CHEST` (2, 1, 6): `PieceLooksGameTests` (`ownerGivesAHouseANewOutside`, `onlyTheLeaderChoosesALook`,
  `aRebuildWaitsForMaterials`, `aRebuildSurvivesSaveAndReload`, `everyLookSentenceReads`,
  `pickingTheOutsideItHasStartsNothing`, `withNoBuilderAPickIsExplained`, `aLookIsNotHandedToAnotherPlayersHiredBuilder`,
  `anUnknownStyleIsRefused`, `changingTheLookMidRebuildEndsWithTheLastPick`: the outside rebuilt, no plan step inside
  the room, the room, grindstone and chest contents kept, the house then found with the new look);
  `QaHouseLooks1108cGameTests` (`aHouseTornDownAfterTheListingIsGone`, `aBenchAt128BlocksReachesTheHouseAnd129DoesNot`,
  `aBusyBuilderQueuesUpToFiveWaitingBuilds`, `aFinishedRebuildKeepsEveryItemTheJobBlockAndTheBed`,
  `onlyTheLeaderTheirFriendsAndOperatorsChoose`); `QaHouseLooks1108cBugTests`, `QaHouseLooksVerify1108bGameTests`
  (`aHouseStrippedOfItsOutsideWithItsRoomStandingIsStillThere`: the room alone still names the house),
  `QaHouseLookSaves1108eGameTests`, `QaBuilderOwnership1104bGameTests` (the `qaOwn1104bLook*` tests; the house's
  centre taken as origin + (4, 0, 5)).
- In code, not a test: `village.py` fit-out cells and the Guard at (5, 1, 5); `VillagePieces` room box and bed-head
  reference; `Stations.REACH` 4.5; `SupplyContainers.RADIUS` 8 / `VERTICAL` 4 from the grindstone.

By id only (the drawing doesn't matter beyond the rules above): `WorkplacesGameTests.workplaceRule_guard_house_guard`
and `_weaponsmith` (defences zone), `wantedJobWithNoFreeBlockWishesItsBuilding`, `wantedJobWithAFreeBlockWishesNothing`,
`wantedJobWaitsForABuilder`, `noWantedJobWithoutAJoblessVillager`; `QaWorkplaceCopiesGameTests` (the name "Guard
House", in the Table without Cobblemon); `CountWordsGameTests`; `QaQuestbookClaims1116eGameTests` and
`QaBlueprintLibraryVerify1108bGameTests.aNewVillageStartsWithTheWikisSetAndTheRestLockedOrRare` (starting set);
`ModdedLibraryGameTests`; `QaCarvedLibraryCompatTests.everyModdedCopyKeepsItsJobSites` (the grindstone at the same cell
in the modded copy: regenerate it); `QaBuiltAdvancements1116bGameTests` (`built/guard_house` exists).

Library-wide: `B648GameTests` / `QaWindowsAndWards1028BugTests` / `QaBuilders1028GameTests` (no pane or bar run ending
against air, as stored and as the game joins it), `BlueprintsStayGameTests` (lamps on a block that holds them; no dirt
path or farmland under a solid block; beds whole), `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`,
`QaBuildReachGameTests`, `QaMirroredBuildsGameTests` (doors, beds and double chests whole, turned and mirrored),
`QaTurnedFramesGameTests` / `QaLedgerFramesHaulGameTests` (frames and paintings hang turned and mirrored),
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`,
`QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`,
`StyleGameTests.chimneysStayWholeInEveryStyle` (only if a campfire stands on a stack of full blocks).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair, no bed right inside a door; `open_panes`), STYLE.md's checks and `holes.py`:
`rebuild.py --candidate` prints no CHECK line and `holes.py` passes. The old drawing has no `owner_rules_baseline.json`
or `holes.py` `ALLOW` entry, and the new one must need none. Saves: there is **no `blueprint_history/guard_house/` yet**
(only the village variants' `blueprint_history/village/<style>_guard_house/`); the ship step must save the old drawing
there (`history.py`; a site begun on the old drawing finishes as drawn) and regenerate the village and modded variants
and `blueprint_furniture/guard_house.json`. Guard Houses already grown in worlds keep their old outside; the House looks
page recognizes a house's look only while 60 % of one style's *current* outside stands, so a new village look means
those old houses stop being listed there (the ship step decides whether that is acceptable; not the designer's call).

## Free
Everything not listed above: shape, plan, roof, materials, colors, storeys, rooms, where the grindstone, bed and chests
stand (within the reaches above), extra beds and chests, an anvil, a bell, a Training Dummy (within 12 of the grindstone
the Guard spars with it), targets, a weapon rack, water (not a cauldron) or none, lighting, and the village variant's
look outside its room.
