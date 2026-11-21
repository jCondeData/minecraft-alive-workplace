# Ferry House: the brief

Family id(s): `ferry_house` (one level, never renamed; "Ferry House" in `en_us.json`, both
`blueprint.aliveworkplace.ferry_house` and `village_piece.aliveworkplace.ferry_house`).
Variants: village (`village/<style>_ferry_house` for the five village styles `plains`, `desert`, `savanna`, `snowy`,
`taiga`, saved by `generate.py` through `village.py`'s `village_house` with no job; their own contract, see "The
village variant"), modded (none to draw: `modded_library.py` makes `structure/modded/ferry_house.nbt` from the vanilla
drawing, and the ship step reruns it), looks (none). The Table's style buttons (`BlueprintStyles`) restyle the drawing
automatically.

## What it is for
The Ferryman's workplace and the village's stop on the travel network. The Ferryman (`ModVillagers.FERRYMAN`) works at
a **Travel Post** (`aliveworkplace:travel_post`, its POI `travel_post`; it is both his held and his acquirable job site,
so a jobless villager takes a free post by himself). A player right-clicks the post to learn it; the Ferryman sells
Travel Tickets to every other post the player knows (emeralds, or CobbleDollars through a list screen while the player
stays within 8 blocks of him), priced by distance (`travel/Ferrymen`). A ticket is used **within 16 blocks of any post**
(`Ferrymen.USE_RANGE`): the player sits in a boat where they stand, the nearest Ferryman **within 10 blocks**
(`FerryRides.FERRYMAN_RANGE`) gets in and rows, and the player lands **next to the destination post**
(`Ferrymen.landing`). At work the Ferryman walks to his post and strolls within **3 blocks** of it, looking at players
within 4 (`FerrymanPackages.work`).

How it gets built: a player picks it at the Blueprint Table (it is in `StarterBlueprints.WORKPLACES`, in the Table for
everyone; finishing it grants the hidden advancement `built/ferry_house`). The Steward builds it by himself in a
`market` zone when a Ferryman is wanted and has no workstation (`steward_rules/workplace_ferry_house.json`, priority 62,
cooldown 2 days), **only on a shore** (see "Fits"), and his renewal of an old Ferryman's house rebuilds it as this one
(`steward_renewal/ferryman.json`). In world generation, every village style grows the village variant (pool weight 4,
`VillageHouses.houses()`, "common, so most villages end up on the travel network") with a jobless villager who takes
the post.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `ferry_house` | The Ferryman's job site (one Travel Post), his bed, a place for passengers to wait | In the Table for everyone (`StarterBlueprints.WORKPLACES`); Steward rule `workplace_ferry_house` (shore only); renewal list `ferryman.json`; advancement `built/ferry_house` |

**One level only.** There is no `ferry_house_2` and none may be drawn: a second level is a new feature (a Java
`StarterBlueprints` entry, upgrade offers) and would change the counted upgrades in
`BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (exactly 64).

## Must have
- **Job block: exactly one `aliveworkplace:travel_post`** in the file
  (`WorkplacesGameTests.workplaceBuilt_ferry_house`: "hasn't one Travel Post", and "job blocks built" counts posts in
  the built box). **No other job block of any job**, vanilla or the mod's (the `workplaces.py` family contract:
  "exactly one of the job's block; no other vanilla job block"): no barrel, lectern, composter, cauldron, smoker, blast
  furnace, brewing stand, loom, stonecutter, grindstone, crafting/fletching/smithing/cartography table, bell, beehive or
  any of the mod's workstations (mailbox, shop counter, storehouse, training post ...). A barrel or a crafting table "for
  looks" would pull in a Fisherman or a Carpenter. A furnace, a chest, a campfire or a boat item is not a job block.
- **The post is taken by a Ferryman walking in from the front.** It is reachable on foot from the ground outside,
  within a player's reach (2 across, 1 up or down) of a walked cell
  (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`), and a Ferryman waiting in front of
  the build walks in and holds it as his job site (`workplaceBuilt_ferry_house`). The Travel Post is a full block: if it
  is built in under a solid block, the villager is sent to **an open cell beside it** (feet and head free, a solid floor
  under it), trying the side it faces first (`work/JobSiteApproach`, B168), so keep at least one such cell beside it.
- **Room round the post** (code, no test pins it to the drawing): players must be able to stand by it and right-click
  it; the Ferryman strolls within 3 blocks of it, so that ground should be walkable floor, not a wall or water; and an
  arriving passenger is put on **the first standable cell within 3 blocks across and 2 down to 3 up of the post**
  (`Ferrymen.landing`, `Walker.canStand`), else on top of the post. Leave such a cell beside it (indoors or out).
- **Beds and residents.** At least **one bed**, the Ferryman's (the workplace contract in `workplaces.py`: "a bed for
  the worker"; every bed counts for the Steward, `StewardConditions.beds`). Its head is reachable on foot
  (`QaBuildReachGameTests`), its foot has its head one block toward its facing
  (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`), and no bed stands right inside a doorway (B178,
  `check.py`). More beds are free. **No villager** in the file.
- **Storage: none read.** No job or test reads a container in the Ferry House. Chests are allowed (drawn empty: builders
  never fill or copy containers; no loot table in the buildable); if any, each must be reachable on foot
  (`QaBuildReachGameTests`) and an indoor one stands against a wall, a solid block, a bed or another chest
  (`QaFurnishedRooms1031GameTests`). A barrel is forbidden (it is a job block).
- **Furniture kinds a job or test needs: none.** The Ferry House is not in `FurnishedWorkplacesGameTests.workplaces()`,
  so no kind is required. If the recipe records furniture spots (`blueprint_furniture/ferry_house.json`, written by
  `rebuild.py`; today it holds one bed spot, which is allowed here), each spot's blocks are exactly the template's there
  (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`), no spot covers a job block or a container
  (`FurnishedWorkplacesGameTests.noSpotCoversAJobBlockABedOrAContainer`), nothing small stands on air
  (`FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece`), and every chair stair faces its table, turned
  and mirrored too (`QaChairFacingGameTests`).
- **A way in on the z = 0 side** (the front), on the ground floor, that a villager 1.95 blocks tall walks through
  (`QaDoorwayClearanceGameTests`, `QaDoorwayBugGameTests`); any upper floor or loft holding a bed, chest or the post is
  reached by a ladder or stairs from inside (B149, `QaBuildReachGameTests`).
- **No trapdoor in the front walk row:** no `*_trapdoor` at z = 1, y 0 or 1
  (`VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow` reads `structure/ferry_house.nbt` directly, B69).
- **No calcite anywhere**, and no jigsaw, structure void or entity (villager, boat, item frame, painting, armor stand ...
  none) in the file (`WorkplacesGameTests.workplaceBuilt_ferry_house`: "still has calcite, a jigsaw or structure
  voids", "comes with a villager"). The test uses calcite to catch an old village house shipped as the buildable; it is
  a block rule, not a look.
- **No water in the file is needed, and none is read.** The ride's boat is launched where the player stands, on land or
  water (`FerryRides.start`). Any water drawn must be source blocks held in on every side and below.
- Every block is one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).

## Fits
- **The front is the waterfront.** The Steward places a Ferry House only where there is **water within 4 blocks in
  front of its z = 0 edge** (`Plots.SHORE` = 4, `Plots.SHORE_BUILDINGS`; checked along the whole width of the front, in
  the 4 rows beyond it), and the same z = 0 side must face the village's road or hall (`Plots.faces`). Water behind or
  beside it doesn't count. So the z = 0 side is both the side the door faces and the side that looks onto the water.
- **The whole footprint stands on land.** The Ferry House is **not** in `Plots.JETTIES`: none of its rows may be over
  water (the plot is refused when more than a tenth of its columns are water, `Plots.Reason.WATER`), its floor goes on
  the shore's ground level, and the water may be 0-4 blocks beyond the front edge. A pier or landing stage drawn in the
  front rows is built on dry ground, not over the water (making it a real jetty would be a code change to `JETTIES`,
  out of scope). The ground under it may vary by at most 4 (`Plots.MAX_STEP`); the Steward keeps 2 blocks clear round
  the plot (`Plots.CLEAR`, `MARGIN`).
- **Plot: at most 18 wide (x) x 18 tall (y) x 11 deep (z), and at least 10 deep.**
  - `WorkplacesGameTests.workplaceBuilt_ferry_house` builds it in the 30 x 20 x 30 `huge_area` at helper (12, 2, 10):
    x 12..29, z 10..29 (**width ≤ 18**, depth ≤ 20), and the top must stay at helper y 19 or lower (**height ≤ 18**:
    blocks built above an area's height are never cleared). Its builder's bench (3, 2, 3) and stock chests
    (3, 2, 6/8/10) stand west of it.
  - **Depth (z) 10 or 11**: `PlotGameTests.aFerryHouseGoesOnTheShore` centres it at helper (16, 13) turned
    counter-clockwise (front to the west), so its footprint runs x 11..20 today, and asserts water 4 blocks in front
    (x 7) fits while water 5 in front (x 6) is refused: that holds only while the box's west edge stays at x 11, i.e.
    `16 - depth / 2 = 11` (integer halving), depth 10 or 11. (The wiki records the decision: "the Ferry House kept its
    plot size so the shore rule and its tests stay as they are".) A different depth means the ship step moves the
    test's water cells, keeping "4 in front fits, 5 refused, water behind refused": avoid it.
  - Width ≤ 19 also keeps the same test's plot inside its `homes` zone (z 2..25 with the 2-block margin).
  - `PlotGameTests.aFishersHutGoesOnAShoreWithShallowWaterUnderItsJetty` ("a Ferry House by deep water") places it at
    helper (13, 13), turned the same way, with water 4 deep at x 6..7: the footprint's west edge (13 - depth / 2) must
    be at x 8..11, so again **depth ≤ 11**, and it must fit by deep water (depth of water doesn't matter).
  - y = 0 sits on the clicked block; the front is the z = 0 side. No basement.
- **Material budget: at most 81 stacks** (`workplaceBuilt_ferry_house` stocks three chests of 27 with exactly the supply
  report, each item rounded up to whole stacks). The old drawing is 337 blocks.
- **Build time:** `workplaceBuilt_ferry_house`: the whole build, then the Ferryman walking in and taking the post,
  within **12,000 ticks** (build delay 2, builders' help off).
- **Declared size:** `StarterBlueprints.FERRY_HOUSE` is `workplace("ferry_house", 9, 10, 10)` today. If the size
  changes the ship step changes that line (sizes must match; `modded_library.py` reads the sizes from that file).
- **Front and way in:** the z = 0 side faces the player who places it and the road of a Steward plot. The arriving
  Ferryman waits at helper (18, 2, 7) = template **(6, 0, -3)**, three blocks in front of the front edge, and walks in
  by the front to the post.
- **Neighbours:** a workplace in a Steward `market` zone, on the shore. A renewal takes it only where it fits the old
  house's footprint plus 3 a side (`Renewals.MARGIN`), so a smaller footprint renews more old houses (free).

## The village variant (`village/<style>_ferry_house`, five styles)
Village houses share one contract that the Java reads by template coordinate (`world/VillagePieces`,
`world/VillageHouses`, `village.py`'s `village_house`, `village_looks.py` header). The designer draws only the look
(`village_looks.py` `DESIGNS["ferry_house"]`, today 11 x 12 x 11); `village.py` adds the jigsaws, fit-out and bed.
Village Ferry Houses are generated wherever the village is: **no water may be assumed** (no shore rule applies).
Every style keeps exactly:
- **The street joint:** a `minecraft:jigsaw` at **(4, 1, 0)**, `north_up`, `minecraft:building_entrance`, final state
  `minecraft:structure_void`; **the front row z = 0 at y = 0 places nothing**, and placing it the way a village does
  leaves no structure void in the world
  (`VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`).
- **The front door at (4, 1, 2)** with a step before it at (4, 0, 1); no trapdoor at z = 1, y 0-1
  (`noVillageHouseHasATrapdoorInItsFrontWalkRow`). The west wall at x = 1.
- **The room:** template x 2-6, z 3-7, y up to 4 (`VillagePieces.ROOM_*`); a house is named by its room, and a hall
  look change rebuilds everything outside the room in another style, keeping the room.
- **The bed: foot (6, 1, 6), head (6, 1, 7), facing south** (`VillagePieces.find`'s reference cell for every village
  house of ours).
- **The fit-out** (`village.py`'s `ferry_house`, placed one block in on x and z): the Travel Post at **(2, 1, 7)**
  facing east, two seat stairs at (2, 1, 5) and (2, 1, 4), a block at (2, 1, 6) with a lantern on it at (2, 2, 6).
  `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`: "should have one Travel Post" and **exactly one job block of
  any job in the whole template** (the look outside the room may add no workstation, barrel, composter, cauldron ...).
- **The villager: none in the file; a jobless one from the worker pool.** A `minecraft:jigsaw` at **(4, 0, 5)**,
  `up_north`, name and target `minecraft:bottom`, pool `aliveworkplace:village/<style>/workers`, final state the
  style's floor; and **an empty entity list** (`everyVillageTypeCanGrowTheOtherHouses`: "should spawn a jobless villager
  from the worker pool"). He takes the post by himself. Keep (4, 1, 5) and (4, 2, 5) clear air.
- Furniture spots (`blueprint_furniture/village/<style>_ferry_house.json`, one bed spot each today) are regenerated with
  the family and must match the templates.
- Every pool lists it, by id (`everyVillageTypeCanGrowTheOtherHouses`, which also pins `VillageHouses.houseNames()`'s
  order; `VillageCompatTests.repurposedStructuresVillagesGetOurHouses`). No test pins its outer size; a much bigger
  house grows less often (free).

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `WorkplacesGameTests.workplaceBuilt_ferry_house` (the `builds()` generator; `HOUSE` (12, 2, 10), `WORKER` (18, 2, 7),
  `BENCH`, `CHESTS`): no entities, calcite, jigsaws or voids; exactly one Travel Post in the file and in the built box;
  built from its supply report (≤ 81 stacks); the worker, a Ferryman with no job site held still during the build and
  let go after it, must hold the post as his `JOB_SITE` while still a Ferryman, within 12,000 ticks.
- `PlotGameTests.aFerryHouseGoesOnTheShore`: at (16, 13) turned counter-clockwise, refused with no water, refused with
  water 5 in front, fits with water 4 in front, refused with water only behind (see "Fits" for the depth this pins).
- `PlotGameTests.aFishersHutGoesOnAShoreWithShallowWaterUnderItsJetty` (its last step): a Ferry House plot fits at
  (13, 13) by water 4 deep at x 6..7.
- `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`, `everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`,
  `noVillageHouseHasATrapdoorInItsFrontWalkRow` (the buildable's file too).
- In code, not a test: `Plots` shore rule (water within 4 in front of z = 0, no jetty rows); `Ferrymen.landing` (a
  standable cell within 3 of the post); `FerrymanPackages` (strolls within 3 of the post); `JobSiteApproach` (open cell
  beside a built-in post); `VillagePieces` room box and bed reference; `village.py` fit-out cells and the spawn jigsaw.

By id only (the drawing doesn't matter beyond the rules above): `WorkplacesGameTests.workplaceRule_ferry_house_ferryman`;
`QaWorkplaceCopiesGameTests` (the name "Ferry House", in the Table; the workplace sentences);
`ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`, `withoutTheModsEveryBuildIsTheVanillaBuild`;
`QaBuiltAdvancements1116bGameTests.everyShippedBlueprintHasItsAdvancement` (`advancement/built/ferry_house.json`).

Library-wide checks it must pass: `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`,
`B648GameTests` and `QaWindowsAndWards1028BugTests` / `QaBuilders1028GameTests` (no pane or bar joined on one side with
air on the other), `QaFurnishedRooms1031GameTests` (no standing lantern on a room's floor; indoor chests against a
wall), `BlueprintsStayGameTests` (a standing lantern, candle or torch on a block whose top holds its middle; no dirt
path or farmland under a solid block; beds whole), `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`,
`QaBuildReachGameTests`, `QaLedgerFramesHaulGameTests` (frames and paintings hang turned and mirrored; none may be in
this file anyway: no entities), `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`,
`QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`, `StyleGameTests.chimneysStayWholeInEveryStyle`
(only if a campfire stands on a solid stack). Modded copies: `ModdedLibraryCompatTests.everyModdedCopyCanBeBuiltWithThePacksMods`
(same size as the vanilla drawing), `QaCarvedLibraryCompatTests.everyModdedCopyKeepsItsJobSites` (the post at the same
cell, no job site added): regenerate with `modded_library.py`. Refresh: the buildable has no `blueprint_history/ferry_house/`
yet (the village variants have theirs); the ship step puts the old drawing there, and
`QaRefreshHistory1028GameTests.qaEveryShippedOlderDrawingIsOfferedARefresh` then expects a Ferry House standing as the
old drawing to be offered the new one (the new drawing must differ, and fit that test's area).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove, furnace, smoker or counter or on a table with a chair, no bed right inside a door; `open_panes`), STYLE.md's
checks and `holes.py`: `rebuild.py --candidate` prints no CHECK line, and `holes.py` passes. Saves stay safe: the ship
step keeps the old drawings in `blueprint_history/` and regenerates the village and modded variants and the furniture
spots with the family.

## Free
Everything not listed above: shape, plan, roof, materials, colors, storeys, whether the post stands indoors, under a
roof or in the open, where the post, bed and seats stand (within the reaches above), whether there is a waiting room,
a boat store, a landing stage on land, a light or a mast, how many beds and chests beyond the minimum, lighting, ground
and planting, and the village variant's look outside its room in each of the five styles.
