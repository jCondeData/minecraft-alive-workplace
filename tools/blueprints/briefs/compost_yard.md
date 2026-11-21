# Compost Yard: the brief

Family id(s): `compost_yard`, `compost_yard_2` (never renamed; "Compost Yard" and "Compost Yard II" in `en_us.json`,
`blueprint.aliveworkplace.compost_yard` / `_2`, and `village_piece.aliveworkplace.compost_yard` for the village house;
hidden advancements `advancement/built/compost_yard.json` and `_2.json`, awarded by id)
Variants: village (`village/<style>_compost_yard` for the five styles `plains`, `desert`, `savanna`, `snowy`, `taiga`,
saved by `generate.py` through `village.py`'s `staffed_house` with the job `aliveworkplace:composter` and the fit-out
`compost_yard_house`; the shell is `village_looks.py`'s `compost_yard`, `DESIGNS["compost_yard"]`; Repurposed Structures'
villages reuse these five through `VillageHouses.MODDED_HOUSE_POOLS`; its own contract, see "The village variant"),
modded (none to draw: `modded_library.py` makes `structure/modded/compost_yard(_2).nbt` from the vanilla drawings, turning
chests into Carved Wood chests; the ship step reruns it), looks (none: the Table's style buttons restyle it through
`BlueprintStyles`; no test names a styled Compost Yard).

## What it is for
**The Composter's workplace.** The Composter (`aliveworkplace:composter`) turns the village's scraps (seeds, saplings,
leaves, crop waste, spoiled food, rotten flesh) into bone meal for the florists, lumberjacks, orchard keepers and farmers.
His job block is the vanilla **composter**, which he shares with the Farmer, Orchard Keeper, Florist and Berry Breeder
(`work/Stations`, the `PoiTypes.FARMER` station). A villager becomes the Composter when a player sneak-right-clicks him by
the composter holding **bone meal**. A village's Steward also gives a jobless villager that job at **a free composter
inside a finished Compost Yard's box (plus 2 blocks round it)** for as long as nobody of that job works at one there
(`city/StewardJobs.BUILDING_JOBS`, `BUILDING_MARGIN`, `buildingJob`, `worked`). At work (`compost/CompostWork`) he walks
to **within 2.5 blocks of his composter** (`walker.walkTo(level, villager, bin, 2.5)`) and, standing there, takes one
scrap at a time from **the chests by the composter** (or, when those are empty, the Storehouse's), puts it in, and every
5 layers drops **a bone meal into those same chests** (`CompostWork.compost`, `sources`; with no chest the bone meal is
dropped on the ground). With nothing to compost he posts a request for rotten flesh. The Porter takes the bone meal out
of those chests and leaves the scraps (`store/Porters`). The Steward builds one by himself in a `farms` zone
(`steward_rules/workplace_compost_yard.json`: a Composter is wanted and has no workstation, Hamlet rank and up, priority
62, cooldown 2 days). When the Steward renews an old Composter's house he rebuilds it as this one
(`steward_renewal/composter.json`, the only building on that list). On the Village Map it is a Farms building
(`hall/VillageMaps.kindOf`, by id). It is in the Blueprint Table for everyone (in `StarterBlueprints.ALL`, behind no
expansion switch, sold by no villager). Villages grow the village variant (pool weight 1, `VillageHouses`) with a
Composter already at work in it.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `compost_yard` | One Composter's composter and a chest beside it for scraps in and bone meal out | Blueprint Table; Steward rule `workplace_compost_yard` (farms zone); renewal list `composter.json` |
| II | `compost_yard_2` | **More storage for the same one Composter**: at least one more chest within his composter's reach (the old II's documented purpose: "a third bay and a potting shed"). Still **one** composter | Upgrade of I (`BlueprintUpgrades`: id + `_2`); sold by the builder who finished I (`UpgradeOffers`) |

**Exactly two levels**: no `compost_yard_3` (it would change `BuilderGameTests.starterUpgradesKeepMostOfTheirBase`'s
count of 64 upgrades). No basement in either level (none in `StarterBlueprints`; a cellar would need `WITH_BASEMENTS` and
the basement tests).

## Must have (each level)
- **Job block: exactly one `minecraft:composter` per level, inside the build's box.** A second composter would be handed
  to a Farmer: the Steward routes a composter in the box to the Composter only while no Composter works there
  (`StewardJobs.worked`, `jobAt`). Draw it empty (level 0). Not the mod's Compost Bin (`aliveworkplace:compost_bin`): it
  has no recipe, so no player can supply a builder with one.
- **No other job block anywhere in the drawing**: any acquirable job site (barrel, lectern, smoker, furnace, blast
  furnace, brewing stand, cauldron, **crafting table** (the Carpenter's: `ModVillagers.CRAFTING_TABLE_POI`), fletching
  table, cartography table, grindstone, stonecutter, smithing table, loom, bell, any of the mod's workstations) pulls a
  villager into another job. **The old Level II had a crafting table: the new one must not.** So storage is
  **chests**, never barrels.
- **Storage: at least one `minecraft:chest` within 8 blocks across (x and z) and 4 up or down of the composter**
  (`SupplyContainers.find`, `RADIUS` 8, `VERTICAL` 4). Without one the Composter has no scraps to take and nowhere to put
  the bone meal: he idles, "needs scraps to compost". Level II: at least two such chests. Every storage block in that
  reach is read as his (decorated pots and hoppers too), so put no other storage there by accident. Chests are drawn
  empty (`BuilderGameTests.blueprintDataNeverHandsOutItems`). A chest indoors stands against a wall, a solid block, a bed
  or another chest (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`).
- **A standing spot within 2.5 blocks of the composter** that a villager can walk to from outside: he only composts
  while standing there (`CompostWork.tick`).
- **The Composter gets in on foot**: a real villager walks from outside the front to that spot and to the chests.
  **Villagers do not open fence gates** (vanilla, kept by `work/PathObstacles`); they open wooden doors. So a closed-in
  yard's way in is a wooden door or an open gap, never only a gate. (`QaBuildReachGameTests` treats a gate as open, so
  this one is the code's rule, not a test's.)
- **Every chest and the composter reachable on foot** from the ground round the build: within 2 across and 1 up or down
  of a walked cell (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`).
- **Beds and residents: none required, none counted.** The Composter is a worker, not a resident. A bed, if drawn,
  makes the build a home and must pass the bed tests (`HomesSpecGameTests`, `FurnishedRoomsGameTests.everyBedHasItsHeadToAWall`,
  `QaMirroredBuildsGameTests`, `BlueprintsStayGameTests`).
- **Furniture spots: none required.** There is no `blueprint_furniture/compost_yard*.json` today, and no job or test
  needs a furniture kind. If the recipe adds spots, every furnished-room check applies (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`,
  `FurnishedRoomsGameTests.everyFurnishedRoomHasALight`, `everyFurnishedPieceHasWhatItStandsOn`, `noFurnitureStandsInADoorsWay`,
  `FloatingFurnishingsGameTests`, `QaChairFacingGameTests`, `QaFurnishedRooms1031GameTests`).
- **No entity** in either file (no villager, animal, frame, painting, armor stand), no jigsaw or structure void, and
  every block one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).
- **Glass**: no pane or bar joined on one side only with air on the other (`B648GameTests`, `check.py`'s `open_panes`).
- A wooden doorway leaves a villager 1.95 blocks (`QaDoorwayClearanceGameTests`, `check.py`'s `doorway_problems`). A raised
  floor holding the composter or a chest is reached by stairs or a ladder.
- A campfire on a chimney stands on blocks that stay solid in every style (`StyleGameTests.chimneysStayWholeInEveryStyle`).

## Kept cells (the upgrade, and every refresh)
- **II over I**: same origin and front, II at least I's size in x, y and z, and **at least 60 % of I's non-air blocks
  stay the same block (same state) in the same cell** (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`). I's
  composter and chest stay where they are in II. Draw both levels together.
- **A refresh never moves a container or a job site** (`build/Refreshes.kept`: protected blocks, someone's bed, every
  acquirable job site). A standing old Compost Yard keeps these template cells (x, y, z) when refreshed to the new drawing:
  - Both levels: **composter (4, 1, 3)**, **chest (3, 1, 3)** (single, facing south).
  - Old Level II also: **chest (12, 1, 5)** (facing west) and **crafting table (12, 1, 7)**.
  - The new drawing must not put a wall, a door, or a path cell a villager needs on these cells. **The composter matters
    most**: a refreshed old yard keeps its old composter, so a new composter elsewhere leaves it with two, and the second
    goes to a Farmer. The cleanest answer is the new composter at (4, 1, 3); otherwise the ship step must show a refreshed
    old Compost Yard ends with one working composter. (`RefreshMatchesFreshBuildGameTests` skips chests and job sites, so
    no refresh test catches this.) The old II's crafting table stays on a refresh whatever is drawn; leave (12, 1, 7)
    where it does no harm (not inside a wall or doorway).
  - The buildable has no `blueprint_history/compost_yard*` yet; the ship step adds it (`history.py`), and then
    `QaRefreshHistory1028GameTests.qaEveryShippedOlderDrawingIsOfferedARefresh` expects every old drawing that differs to
    be offered the new one.

## Fits
- Plot (y = 0 sits on the clicked block; the front is the z = 0 side):
  - **Level I: at most 13 wide x 13 deep x 16 tall.** `BuilderGameTests.buildsCompostYard` builds it in `big_area`
    (22 x 18 x 22) at helper origin (9, 2, 9); the builder's bench (2, 2, 2), his chest (2, 2, 4) and four stock barrels
    at x 1-5, z 4 stand west and north of it. Blocks above the area's height are never cleared.
  - **Level II: at most 15 wide x 13 deep x 16 tall.** `BuilderGameTests.buildsCompostYardII` builds it at helper
    origin (7, 2, 9) ("15 wide: further left"). At least I's size in every direction.
- **Declared sizes**: `StarterBlueprints.COMPOST_YARD` is `new Vec3i(11, 8, 10)` and `COMPOST_YARD_2` `new Vec3i(15, 8, 10)`
  today; if a size changes the ship step changes those lines (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`
  asserts file size = entry; `modded_library.py` reads the sizes from that file).
- **Material budget (each level): at most 108 stacks** (`buildStarter` stocks four barrels of 27 slots with exactly the
  plan's materials in whole stacks; more fails "test needs more barrels"). Unstackable items take a slot each.
- **Build time and block budget (both levels; each is built whole from bare ground by its own test)**:
  `buildsCompostYard` and `buildsCompostYardII` set day time 2,000, build delay 2, builders' help off, and must reach DONE
  with every block right (`assertBuilt`) within **30,000 ticks**. The builder works only from day time 1,000 to 11,000
  (`ModVillagers.BUILDER_SCHEDULE`), so the first working day gives about **9,000 working ticks**; treat that as the
  budget (spilling into the next morning is fragile and slow on a real server). Test builders lay about one block per 10
  ticks with walking and fetching. **Keep Level I to about 500-650 placed (non-air) blocks and Level II to about 900,
  never over 900 for either** (the old ones are 281 and 394). A real upgrade builds only what changed from I.
- **Front and way in**: the z = 0 side faces the player who places it (the gold edge of the outline) and the road of a
  Steward plot. The Composter walks in from that side.
- **Neighbours**: a workplace in a Steward `farms` zone; the Steward keeps 2 blocks clear round its plot and wants the
  ground under it within 4 blocks of level (`city/Plots.MARGIN`, `MAX_STEP`). The builder soak lays every Level I 18
  blocks apart in rows 26 deep (`command/Soak`), which the 13 x 13 limit already keeps.
- **Steward renewal fit** (`steward_renewal/composter.json`, `city/Renewals.fit`): when an old Composter's house is
  renewed, Level I goes on its plot with its front on the old house's front line, centred on the old door, within the old
  footprint **plus 3 blocks to each side and back** (`Renewals.MARGIN`); if it doesn't fit, that house isn't renewed. A
  smaller Level I footprint renews more old houses.

## The village variant (`village/<style>_compost_yard`, five styles)
Village houses share one contract read by template coordinate (`world/VillagePieces`, `world/VillageHouses`,
`village.py`'s `village_house`, the `village_looks.py` header). The designer draws only the shell
(`village_looks.py` `DESIGNS["compost_yard"]`, today 13 x 10 x 11); `village.py` adds the jigsaw, fit-out, bed and
villager. Every style keeps exactly:
- **The street joint:** a `minecraft:jigsaw` at **(4, 1, 0)**, `north_up`, name and target `minecraft:building_entrance`,
  final state `minecraft:structure_void`; the front row z = 0, y = 0 places nothing (x 0-8 are checked) and placing it
  as a village does leaves no structure_void in the world
  (`VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`).
- **The front door at (4, 1, 2)** (that test reads the block there as not air), a step before it at (4, 0, 1), and **no
  trapdoor at z = 1, y 0-1** (`VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow`).
- **The room:** template x 2-6, z 3-7, y up to 4 (`VillagePieces.ROOM_*`): a house is named by its room, and a hall's
  look change rebuilds everything outside it in another style, keeping the room. The shell's walls bound it: west at
  x = 1, front at z = 2, east at x = 7, back at z = 8.
- **The bed: foot (6, 1, 6), head (6, 1, 7), facing south** (`VillagePieces` finds every village house of ours by that
  bed head).
- **The fit-out** (`compost_yard_house`, moved in by one): the composter at **(2, 1, 7)**, a chest at **(2, 1, 6)** facing
  east with `LootTable` `aliveworkplace:chests/village_compost_yard`, today also a hay bale (2, 1, 4) and coarse dirt
  (2, 1, 3). `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`: "should have one Composter" and **exactly one job
  block of any job in the whole template**: the shell may add no composter (no compost "bays" made of composters), barrel,
  cauldron, crafting table or other workstation for looks.
- **The villager:** one villager entity, already a Composter (`aliveworkplace:composter`), at **(5, 1, 5)**, and no
  worker-pool spawn jigsaw (same test: "should come with a aliveworkplace:composter"); (4, 0, 5) is the style's floor.
  Keep (5, 1, 5) and (5, 2, 5) clear air with floor under it.
- A ladder to every furnished upper floor (the `village_looks.py` header).
- Every pool lists it: `everyVillageTypeCanGrowTheOtherHouses` (also pins `VillageHouses.houseNames()`'s order),
  `VillageCompatTests.repurposedStructuresVillagesGetOurHouses`, by id only. The old village pieces are already kept in
  `blueprint_history/village/<style>_compost_yard/`; the ship step adds the current ones with `history.py`.
- No test pins the village shell's outer size; a much bigger shell grows less often (vanilla refuses overlapping pieces).

## Rules tests check
Built whole:
- `BuilderGameTests.buildsCompostYard`: `big_area`, origin (9, 2, 9), batch `starter_builds_7`, 30,000 ticks, materials
  from at most four barrels of 27; reaches DONE with every block of the plan right.
- `BuilderGameTests.buildsCompostYardII`: the same for Level II at origin (7, 2, 9), batch `starter_builds_8`.

By id only (the drawing doesn't matter beyond the box rules above):
- `WorkplacesGameTests` (generated `workplaceRule_compost_yard_composter`: the `workplace_compost_yard` rule for
  `aliveworkplace:composter`, `farms` zone, Hamlet), `StewardRulesGameTests` (rule list).
- `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses` and the other village tests above (village variant).
- `QaBuiltAdvancements1116bGameTests` (`built/compost_yard`, `built/compost_yard_2`).
- `CompostGameTests` (`whatComposts`, `rottenFleshBecomesBoneMeal`, `aComposterAtWork`) test the job on its own blocks,
  not the drawing: a chest 2 blocks from the bin is what they prove works.

Library-wide checks it must pass: `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (60 % kept, II no smaller,
64 upgrades) and `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`; `QaBuildReachGameTests`;
`QaMirroredBuildsGameTests` (doors, beds and double chests whole turned and mirrored); `QaTurnedModBlocksGameTests`;
`QaTurnedFramesGameTests`; `HomesSpecGameTests` (only if a bed is drawn); `QaDoorwayClearanceGameTests`; `B648GameTests`;
`QaFurnishedRooms1031GameTests`; `BlueprintsStayGameTests` (lamps on a block that holds them; no farmland or dirt path
under a solid block; beds whole); `QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`;
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural` (a new material needs a plural in `en_us.json`);
`StyleGameTests.chimneysStayWholeInEveryStyle`; `ModdedLibraryGameTests` and
`QaCarvedLibraryCompatTests.everyModdedCopyKeepsItsJobSites` (the modded copy keeps the composter in the same cell and adds
no job site; regenerate with `modded_library.py`); the furniture tests above if spots are added.

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair, upper floors by stairs or a ladder, nothing that falls over air; `open_panes`),
STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no CHECK line for either level, and `holes.py` passes.
The old drawings have no entries in `owner_rules_baseline.json`; the rebuild must come out clean.

## Free
Everything not listed above: shape, plan, materials, colors, style, whether it is an open yard, a shed, a walled pen or
a roofed barn, how the scraps and heaps are shown (with any blocks that are not job sites or storage), where the composter
and chests stand (within the kept cells and the reaches above), paths, fences, lighting, seats, planting, and what Level II
adds beyond its extra storage; for the village variant, the whole shell outside its fixed cells.
