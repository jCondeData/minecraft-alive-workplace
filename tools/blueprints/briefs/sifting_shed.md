# Sifting Shed: the brief

Family id(s): `sifting_shed`, `sifting_shed_2` (never renamed; "Sifting Shed" and "Sifting Shed II" in `en_us.json`,
`blueprint.aliveworkplace.sifting_shed` / `_2`, and `village_piece.aliveworkplace.sifting_shed` for the village house;
hidden advancements `advancement/built/sifting_shed.json` and `_2.json`, awarded by id)
Variants: village (`village/<style>_sifting_shed` for the five styles `plains`, `desert`, `savanna`, `snowy`, `taiga`,
saved by `generate.py` through `village.py`'s `staffed_house` with the job `aliveworkplace:sifter` and the fit-out
`sifting_shed_house`; the shell is `village_looks.py`'s `sifting_shed`, `DESIGNS["sifting_shed"]`; Repurposed Structures'
villages reuse these five through `VillageHouses.MODDED_HOUSE_POOLS`; its own contract, see "The village variant"),
modded (none to draw: `modded_library.py` makes `structure/modded/sifting_shed(_2).nbt` from the vanilla drawings, turning
chests into Carved Wood chests; the ship step reruns it), looks (none: the Table's style buttons restyle it through
`BlueprintStyles`; no test names a styled Sifting Shed).

## What it is for
**The Sifter's workplace (and a Leatherworker's).** The Sifter (`aliveworkplace:sifter`) shakes gravel, sand, dirt and
soul sand through his sieve for what's hidden in them (flint, seeds, nuggets, the odd gem; with Cobblemon now and then an
evolution stone), and sells the finds. His job block is the vanilla **cauldron**, the Leatherworker's block, which he
shares with the Leatherworker, the Vintner (config `vintners`) and the Well Keeper (config `wellKeepers`)
(`work/Stations`, the `PoiTypes.LEATHERWORKER` station; `ModVillagers.SIFTER` takes `SIEVE_POI` or `LEATHERWORKER`). The
mod's own Sieve block has **no recipe** (no `recipe/` file; tagged `c:hidden_from_recipe_viewers`), so no player can hand
a builder one: the drawing uses a cauldron. A villager becomes the Sifter when a player sneak-right-clicks him by the
cauldron holding **gravel, sand, red sand or soul sand**. A village's Steward also gives a jobless villager that job at
**a free cauldron inside a finished Sifting Shed's box (plus 2 blocks round it)** for as long as nobody of that job works
at one there (`city/StewardJobs.BUILDING_JOBS`, `BUILDING_MARGIN`, `buildingJob`, `worked`, `jobAt`); a free cauldron
there once a Sifter already works in the shed goes to its own job, the **Leatherworker**. At work (`sift/SifterWork`) he
walks to **within 2.5 blocks of his cauldron** (`walker.walkTo(level, villager, sieve, 2.5)`) and, standing there, takes
one siftable block at a time from **the chests by the cauldron** (`SupplyContainers.find`), rolls its loot table
(`aliveworkplace:sifting/<block>`), and puts the finds **into those same chests** (what doesn't fit is dropped on the
ground). With nothing to sift he posts a request for 16 gravel. The Porter takes the finds out of those chests and leaves
the siftable blocks (`store/Porters`). The Steward builds one by himself in a `workshops` zone
(`steward_rules/workplace_sifting_shed.json`: a **Sifter or a Leatherworker** is wanted and has no workstation, Hamlet rank
and up, priority 62, cooldown 2 days). When the Steward renews an old Sifter's **or an old Leatherworker's** house
(including vanilla leatherworker houses) he rebuilds it as this one (`steward_renewal/sifter.json`,
`steward_renewal/leatherworker.json`; it is the only building on both lists). On the Village Map it is a Farms building
(`hall/VillageMaps.kindOf`, by id). It is in the Blueprint Table for everyone (in `StarterBlueprints.ALL`, behind no
expansion switch, sold by no villager). Villages grow the village variant (pool weight 1, `VillageHouses`) with a Sifter
already at work in it. A Cobblemon Pasture Block of Ground or Rock partners within 16 blocks of the cauldron speeds him up
(`work/Partners.RADIUS`); that is the player's to place, not the drawing's.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `sifting_shed` | One cauldron and a chest beside it for blocks to sift in and finds out: one Sifter at work | Blueprint Table; Steward rule `workplace_sifting_shed` (workshops zone); renewal lists `sifter.json`, `leatherworker.json` |
| II | `sifting_shed_2` | **A second workstation**: a second cauldron with storage in its reach (the documented purpose, `StarterBlueprints`: "a second Sieve"). In play the Steward gives it to a Leatherworker once the shed has its Sifter; a player makes a second Sifter there with gravel. Each cauldron needs its standing spot and its chest | Upgrade of I (`BlueprintUpgrades`: id + `_2`); sold by the builder who finished I (`UpgradeOffers`) |

**Exactly two levels**: no `sifting_shed_3` (it would change `BuilderGameTests.starterUpgradesKeepMostOfTheirBase`'s
count of 64 upgrades). No basement in either level (none in `StarterBlueprints`; a cellar would need `WITH_BASEMENTS` and
the basement tests).

## Must have (each level)
- **Job blocks: exactly one `minecraft:cauldron` in Level I, exactly two in Level II, inside the build's box.** Draw them
  **empty** (`minecraft:cauldron`, not a water, lava or snow cauldron): a builder can't be handed a filled one.
- **No other job block anywhere in the drawing**: any acquirable job site (barrel, lectern, smoker, furnace, blast
  furnace, brewing stand, composter, crafting table (the Carpenter's), fletching table, cartography table, grindstone,
  stonecutter, smithing table, loom, bell, any of the mod's workstations) pulls a villager into another job. So storage
  is **chests**, never barrels.
- **Storage: at least one `minecraft:chest` within 8 blocks across (x and z) and 4 up or down of each cauldron**
  (`SupplyContainers.find`, `RADIUS` 8, `VERTICAL` 4). Without one the Sifter has nothing to sift and nowhere to put the
  finds: he idles, "needs" (the request board asks for gravel). Level II: at least two chests, one by each cauldron.
  Every storage block in that reach is read as his (decorated pots and hoppers too), so put no other storage there by
  accident. Chests are drawn empty (`BuilderGameTests.blueprintDataNeverHandsOutItems`). A chest indoors stands against a
  wall, a solid block, a bed or another chest (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`).
- **A standing spot within 2.5 blocks of each cauldron** that a villager can walk to from outside: he only sifts while
  standing there (`SifterWork.tick`).
- **The Sifter gets in on foot**: a real villager walks from outside the front to that spot and to the chests.
  **Villagers do not open fence gates** (vanilla, kept by `work/PathObstacles`); they open wooden doors. So a closed-in
  shed's way in is a wooden door or an open gap, never only a gate. (`QaBuildReachGameTests` treats a gate as open, so
  this one is the code's rule, not a test's.)
- **Every chest and cauldron reachable on foot** from the ground round the build: within 2 across and 1 up or down of a
  walked cell (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`).
- **Beds and residents: none required, none counted.** The Sifter is a worker, not a resident. A bed, if drawn, makes
  the build a home and must pass the bed tests (`HomesSpecGameTests`, `FurnishedRoomsGameTests.everyBedHasItsHeadToAWall`,
  `QaMirroredBuildsGameTests`, `BlueprintsStayGameTests`).
- **Furniture spots: none required.** There is no `blueprint_furniture/sifting_shed*.json` today, and no job or test needs
  a furniture kind. If the recipe adds spots, every furnished-room check applies (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`,
  `FurnishedRoomsGameTests.everyFurnishedRoomHasALight`, `everyFurnishedPieceHasWhatItStandsOn`, `noFurnitureStandsInADoorsWay`,
  `FloatingFurnishingsGameTests`, `QaChairFacingGameTests`, `QaFurnishedRooms1031GameTests`).
- **No entity** in either file (no villager, animal, frame, painting, armor stand), no jigsaw or structure void, and
  every block one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).
- **Gravity blocks** (gravel, sand, concrete powder...), if drawn, stand on something solid: nothing that falls over air
  (`check.py`).
- **Glass**: no pane or bar joined on one side only with air on the other (`B648GameTests`, `check.py`'s `open_panes`).
- A wooden doorway leaves a villager 1.95 blocks (`QaDoorwayClearanceGameTests`, `check.py`'s `doorway_problems`). A raised
  floor holding a cauldron or a chest is reached by stairs or a ladder.
- A campfire on a chimney stands on blocks that stay solid in every style (`StyleGameTests.chimneysStayWholeInEveryStyle`).

## Kept cells (the upgrade, and every refresh)
- **II over I**: same origin and front, II at least I's size in x, y and z, and **at least 60 % of I's non-air blocks
  stay the same block (same state) in the same cell** (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`). I's
  cauldron and chest stay where they are in II. Draw both levels together.
- **A refresh never moves a container or a job site** (`build/Refreshes.kept`: protected blocks, someone's bed, every
  acquirable job site). A standing old Sifting Shed keeps these template cells (x, y, z) when refreshed to the new drawing:
  - Both levels: **cauldron (4, 1, 4)**, **chest (6, 1, 6)** (single, facing west).
  - Old Level II also: **cauldron (4, 1, 9)** and **chest (6, 1, 10)** (single, facing west).
  - The new drawing must not put a wall, a door, or a path cell a villager needs on these cells. **The cauldrons matter
    most**: a refreshed old shed keeps its old cauldrons, so a new cauldron elsewhere leaves it with one too many, and
    the extra goes to a Leatherworker (or a Vintner or Well Keeper). The cleanest answer is the new cauldrons at
    (4, 1, 4) and, in II, (4, 1, 9); otherwise the ship step must show a refreshed old Sifting Shed ends with the
    level's count of working cauldrons. (`RefreshMatchesFreshBuildGameTests` skips chests and job sites, so no refresh
    test catches this.)
  - The buildable has no `blueprint_history/sifting_shed*` yet; the ship step adds it (`history.py`), and then
    `QaRefreshHistory1028GameTests.qaEveryShippedOlderDrawingIsOfferedARefresh` expects every old drawing that differs to
    be offered the new one.

## Fits
- Plot (y = 0 sits on the clicked block; the front is the z = 0 side):
  - **Level I: at most 13 wide x 13 deep x 16 tall.** `BuilderGameTests.buildsSiftingShed` builds it in `big_area`
    (22 x 18 x 22) at helper origin (9, 2, 9); the builder's bench (2, 2, 2), his chest (2, 2, 4) and four stock barrels
    at x 1-5, z 4 stand west and north of it. Blocks above the area's height are never cleared.
  - **Level II: at most 13 wide x 14 deep x 16 tall.** `BuilderGameTests.buildsSiftingShedII` builds it at helper
    origin (9, 2, 8). At least I's size in every direction.
- **Declared sizes**: `StarterBlueprints.SIFTING_SHED` is `new Vec3i(9, 9, 9)` and `SIFTING_SHED_2` `new Vec3i(11, 9, 13)`
  today; if a size changes the ship step changes those lines and their one-line comments
  (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable` asserts file size = entry; `modded_library.py` reads the
  sizes from that file).
- **Material budget (each level): at most 108 stacks** (`buildStarter` stocks four barrels of 27 slots with exactly the
  plan's materials in whole stacks; more fails "test needs more barrels"). Unstackable items take a slot each.
- **Build time and block budget (both levels; each is built whole from bare ground by its own test)**:
  `buildsSiftingShed` and `buildsSiftingShedII` must reach DONE with every block right (`assertBuilt`) within **30,000
  ticks**. The builder works only from day time 1,000 to 11,000 (`ModVillagers.BUILDER_SCHEDULE`), so the first working
  day gives about **9,000 working ticks**; treat that as the budget. Test builders lay about one block per 10 ticks with
  walking and fetching. **Keep Level I to about 500-650 placed (non-air) blocks and Level II to about 900, never over 900
  for either** (the old ones are 279 and 445). A real upgrade builds only what changed from I.
- **Front and way in**: the z = 0 side faces the player who places it (the gold edge of the outline) and the road of a
  Steward plot. The Sifter walks in from that side.
- **Neighbours**: a workplace in a Steward `workshops` zone; the Steward keeps 2 blocks clear round its plot and wants
  the ground under it within 4 blocks of level (`city/Plots.MARGIN`, `MAX_STEP`).
- **Steward renewal fit** (`steward_renewal/sifter.json`, `leatherworker.json`, `city/Renewals.fit`): when an old
  Sifter's or Leatherworker's house is renewed, Level I goes on its plot with its front on the old house's front line,
  centred on the old door, within the old footprint **plus 3 blocks to each side and back** (`Renewals.MARGIN`); if it
  doesn't fit, that house isn't renewed. Vanilla leatherworker houses are small, so a smaller Level I footprint renews
  more old houses.

## The village variant (`village/<style>_sifting_shed`, five styles)
Village houses share one contract read by template coordinate (`world/VillagePieces`, `world/VillageHouses`,
`village.py`'s `village_house`, the `village_looks.py` header). The designer draws only the shell
(`village_looks.py` `DESIGNS["sifting_shed"]`, today 13 x 9 x 11); `village.py` adds the jigsaw, fit-out, bed and
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
- **The fit-out** (`sifting_shed_house`, moved in by one): the cauldron at **(2, 1, 7)**, a chest at **(2, 1, 6)** facing
  east with `LootTable` `aliveworkplace:chests/village_sifting_shed`, today also gravel (2, 1, 4) and sand (2, 1, 3).
  `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`: "should have one Cauldron" and **exactly one job block of any
  job in the whole template**: the shell may add no cauldron, barrel, composter, crafting table or other workstation for
  looks.
- **The villager:** one villager entity, already a Sifter (`aliveworkplace:sifter`), at **(5, 1, 5)**, and no worker-pool
  spawn jigsaw (same test: "should come with a aliveworkplace:sifter"); (4, 0, 5) is the style's floor. Keep (5, 1, 5)
  and (5, 2, 5) clear air with floor under it.
- A ladder to every furnished upper floor (the `village_looks.py` header).
- Every pool lists it: `everyVillageTypeCanGrowTheOtherHouses` (also pins `VillageHouses.houseNames()`'s order),
  `VillageCompatTests.repurposedStructuresVillagesGetOurHouses`, by id only. The old village pieces are already kept in
  `blueprint_history/village/<style>_sifting_shed/`; the ship step adds the current ones with `history.py`.
- No test pins the village shell's outer size; a much bigger shell grows less often (vanilla refuses overlapping pieces).

## Rules tests check
Built whole:
- `BuilderGameTests.buildsSiftingShed`: `big_area`, origin (9, 2, 9), batch `starter_builds_8`, 30,000 ticks, materials
  from at most four barrels of 27; reaches DONE with every block of the plan right.
- `BuilderGameTests.buildsSiftingShedII`: the same for Level II at origin (9, 2, 8), batch `starter_builds_7`.

By id only (the drawing doesn't matter beyond the box rules above):
- `WorkplacesGameTests` (generated `workplaceRule_sifting_shed_*` cases: the `workplace_sifting_shed` rule for both
  `aliveworkplace:sifter` and `minecraft:leatherworker`, `workshops` zone, Hamlet), `StewardRulesGameTests` (rule list).
- `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses` and the other village tests above (village variant).
- `QaBuiltAdvancements1116bGameTests` (`built/sifting_shed`, `built/sifting_shed_2`).
- `SifterGameTests` (`siftingTablesLoad`, `siftingGravelTurnsUpFlint`, `aSifterAtWork`) test the job on its own blocks,
  not the drawing: a chest beside the sieve is what they prove works.

Library-wide checks it must pass: `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (60 % kept, II no smaller,
64 upgrades) and `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`; `QaBuildReachGameTests`;
`QaMirroredBuildsGameTests` (doors, beds and double chests whole turned and mirrored); `QaTurnedModBlocksGameTests`;
`QaTurnedFramesGameTests`; `HomesSpecGameTests` (only if a bed is drawn); `QaDoorwayClearanceGameTests`; `B648GameTests`;
`QaFurnishedRooms1031GameTests`; `BlueprintsStayGameTests` (lamps on a block that holds them; no farmland or dirt path
under a solid block; beds whole); `QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`;
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural` (a new material needs a plural in `en_us.json`);
`StyleGameTests.chimneysStayWholeInEveryStyle`; `ModdedLibraryGameTests` and
`QaCarvedLibraryCompatTests.everyModdedCopyKeepsItsJobSites` (the modded copy keeps the cauldrons in the same cells and
adds no job site; regenerate with `modded_library.py`); the furniture tests above if spots are added.

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair, upper floors by stairs or a ladder, nothing that falls over air; `open_panes`),
STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no CHECK line for either level, and `holes.py` passes.
The rebuild must come out clean.

## Free
Everything not listed above: shape, plan, materials, colors, style, whether it is an open shed, a walled workshop or a
yard, how the heaps, bins and screens of sifting are shown (with any blocks that are not job sites or storage), where the
cauldrons and chests stand (within the kept cells and the reaches above), paths, fences, lighting, seats, and what Level II
adds beyond its second cauldron and its storage; for the village variant, the whole shell outside its fixed cells.
