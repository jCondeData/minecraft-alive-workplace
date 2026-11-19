# Greenhouse: the brief

Family id(s): `greenhouse` (one level, never renamed; "Greenhouse" in `en_us.json`, `blueprint.aliveworkplace.greenhouse`;
hidden advancement `built/greenhouse`, awarded by id)
Variants: village (none: no `structure/village/*greenhouse*`, no Steward rule, no village plan, no renewal list names
it), modded (none: `modded_library.py` skips `CREATE_ONLY`, `NOT_BUILDINGS`), looks (none drawn: the Table's style
buttons restyle it through `BlueprintStyles`; `QaTableCreate1117eCompatTests.everyCreateBuildHasMaterialsAPreviewAndLiveStyles`
only asks that every listed style has materials and a preview, which is automatic).

ROADMAP 50.9 (with the Berry Farm, Berry Nursery, Apiary Garden and Compost Yard). One version (not a flagship).
Not this family: the "greenhouse" rooms of Flower Shop II (`starter.py`) and Berry Nursery II (`pokemon_jobs.py`) are
parts of those families' drawings.

## What it is for
One of the nine **Create-powered builds** (ROADMAP 39.8, `StarterBlueprints.MORE_CREATE_BUILDS`, fourth in the Table's
order): a working Create machine and a workplace. It is at the Blueprint Table **only with Create installed and
`createBuilds` on** (`CreateBuilds.listed`, `StarterBlueprints.expansionSwitch` = `createBuilds`), and still loads by id
without them. ROADMAP 39.8 names it: "a mechanical harvester and plough on a belt, a water wheel". A builder builds it
from his chests; Create's moving parts go in last, by group (shafts and gearboxes, then cogwheels, then belts,
`kinetic_parts/create.json`), and still water is poured from a bucket at the end (`MaterialRules`: a water source is
DECORATION, flowing water is SKIP and comes by itself). The moment the builder is done the belt must turn **by itself**.
About 100 ticks later the builder checks every kinetic part and tells the owner which "don't turn"
(`Contraptions.CHECK_AFTER`, `message.aliveworkplace.contraption_stuck`).

The workplace half is the **Farmer's**: his job block is the vanilla **composter** (`work/Stations`: a composter makes a
Farmer by vanilla's rules, or an Orchard Keeper, Florist, Composter or Berry Breeder when a player gives the villager that
job's item). A village Farmer with no field **adopts the farm by his composter by himself** (`farm/Fields.adoptOwnFarm`,
every 200 ticks, game rule `villageFarms`) once there is a chest near the composter: the farmland nearest the composter
within 16 blocks across and 4 up or down (`Fields.FARM_SEARCH`, `farmNear`) and all farmland joined to it. He then tends
it (`FieldWork`: harvests ripe crops, replants, takes seeds and bone meal from, and puts the harvest in, the chests by
his composter). The player guide (`docs/guide/create-builds.md`) promises "beds of wheat, carrots and potatoes" and the
farmer's composter; the ship step rewrites that line if the crops change. No villager works the harvester or the plough
(there is no `machine_work` file for them): Create's harvester and plough only work when moving, so here they stand
still, ready for a player to fix them to a contraption of his own. The Mechanic (39.2) puts broken drive parts back from
the blueprint.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `greenhouse` | A self-starting water-wheel drive turning a belt that runs to a depot, a mechanical harvester and a mechanical plough standing on the line; watered farmland planted with crops; the Farmer's composter and his chest | At the Table with Create and `createBuilds` (expansion M39) |

**One level only.** A `greenhouse_2` would be a new feature (a `StarterBlueprints` entry, upgrade offers).

## Must have
- **Job block: exactly one `minecraft:composter`**, a job site once built
  (`CreateBuildsCompatTests.aBuilderBuildsTheGreenhouseAndItTurns` asserts the block id at the pinned spot and that
  `getPoiManager().getType(...)` is present). A second composter makes a second Farmer (both adopt the same farm). No
  other job block: every vanilla or mod job site pulls a villager into another trade, and **the old drawing's barrel is a
  Fisherman's job site**, so storage is chests.
- **Storage: at least one `minecraft:chest` within 8 blocks across (x and z) and 4 up or down of the composter**
  (`build/SupplyContainers.RADIUS` 8, `VERTICAL` 4). Without one the Farmer never adopts the farm
  (`Fields.adoptOwnFarm` returns false) and has nowhere to put the harvest. The depot and belt never count as storage
  (`Kinetics.keepsOwnItems`). Drawn empty (`BuilderGameTests.blueprintDataNeverHandsOutItems`; builders never copy
  container contents). An indoor chest stands against a wall, a solid block, a bed or another chest
  (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`).
- **The farm (code, and the test pins one cell of it):**
  - `minecraft:farmland` with a crop on it; at least the pinned `minecraft:wheat` on its farmland (the test checks both
    block ids). Crops are planted at age 0 (`MaterialRules.plantedFresh`, B493), farmland costs dirt.
  - **One joined farm.** `Fields.farmNear` starts at the farmland nearest the composter and spreads only through farmland
    (on the same level or one step up or down) and **water on the same level**, within 32 a side
    (`FieldData.MAX_SIDE`). A bed cut off from the rest by an aisle, a path, a wall, a belt or a step of two is **not**
    tended: the Farmer adopts only the patch nearest his composter. All the farmland must touch, or touch through water
    channels at its own level.
  - **Every farmland within 4 blocks across of a water source on its level or one above** (vanilla's hydration). Water
    is poured last, after the farmland is laid, and dry farmland with no crop on it turns back to dirt, which the test
    sees as a wrong block. Farmland drawn with `moisture` 7.
  - **Every crop with light 8 or more, or a view of the sky** (vanilla `CropBlock.canSurvive`: raw brightness >= 8 or
    `canSeeSky`). Under an opaque roof a crop pops off when placed or updated; a lamp, or a roof that lets the sky
    light through, keeps it.
  - No solid block on farmland (`BlueprintsStayGameTests`: no farmland or dirt path under a solid block).
  - The farmland and every crop within 16 across and 4 up or down of the composter (`FARM_SEARCH`), and every crop cell
    one the Farmer can walk next to.
- **The machine (Create blocks, by id; checked in `libs/create-fabric-6.0.10.0+mc1.21.1.jar`):**
  - a `create:belt` (the test pins one middle segment and reads the `Speed` of **both pulleys**, the start and the end:
    both must turn), whole: every segment names a controller of its own belt, numbered 0 to its length - 1 once each
    (a broken belt reads as unfinished; `QaCreateBuildsTurnedCompatTests.assertBeltsWhole` holds the other Create builds
    to it);
  - a `create:depot` (pinned; ROADMAP's "to a depot": at the belt's end);
  - a `create:mechanical_harvester` and a `create:mechanical_plough` (both pinned, by id), standing on or over the belt
    line ("on a belt", ROADMAP 39.8). **They must stand still**: on no bearing, piston, gantry or anything the drive
    could start, because a block carried off as a contraption leaves the world and the test reads it as unfinished
    (only the Windmill is excused, `CreateBuildsCompatTests.build`). Each must be placeable by the builder and stay put
    (each is held by the block at its back; nothing drawn may need a support placed after it);
  - a drive that **starts itself** when the last block goes in: only the Windmill gets a push from the builder
    (`CreateBuilds.finished` acts on the Windmill's id alone), so it is a water wheel turned by water the builder pours
    as a **source block** (falling and running water must come by itself from that source), and the source must be
    reachable to pour from a bucket. The scene places the template directly, so the water must start the wheel with
    no builder at all;
  - **every kinetic part turns**: every shaft, gearbox, cogwheel and belt in the drawing, and any block whose data carries
    `Speed` (`Contraptions`), so the owner never reads "N parts don't turn". No decorative, unconnected shaft or cog;
  - enough stress capacity for everything on the network (an over-stressed network stops at Speed 0 and fails the test);
  - Create block states that matter (`create_builds.py`'s header, checked in the jar): a water wheel's `facing` is its
    axle's direction; a shaft's, gearbox's and cogwheel's `axis` their own; a belt's `part` (`start`, `middle`, `end`),
    `facing`, `slope` and `casing`, its pulleys turning about the axis across its `facing`.
- **Water stays where it is drawn, with or without Create.** No flowing water may reach a crop, farmland's top, a lamp,
  a carpet or anything water breaks. `QaCreateGoneUpkeepGameTests.aCreateBuildStandingWithoutCreateNeedsNoRepair` places
  the build **without Create** (every Create block air, the wheel gone) and after 30 ticks of its water running it must
  need **no repair**: the water that fell on the wheel now falls and runs freely and must still harm nothing drawn, and
  nothing drawn may rest on a Create block (the casing, the belt, the depot gone to air leave it floating).
- **Beds and residents: none required, none counted** (no code or test counts a resident here; the old one has no bed).
  A bed, if drawn, makes it a home (`Homes.at`) and must pass the bed checks (`HomesSpecGameTests`,
  `FurnishedRoomsGameTests.everyBedHasItsHeadToAWall`, `QaMirroredBuildsGameTests`, `BlueprintsStayGameTests`).
- **Furniture spots: none required** (no `blueprint_furniture/greenhouse.json` today; no job or test needs a kind).
  Every piece drawn is recorded as a spot (`rebuild.py`) and must then pass `FurnitureSpotGameTests`,
  `FloatingFurnishingsGameTests`, `QaChairFacingGameTests`, `FurnishedRoomsGameTests.everyFurnishedRoomHasALight` and
  `noFurnitureStandsInADoorsWay`.
- **The Farmer gets in on foot**: a way in on the z = 0 side that a villager 1.95 blocks tall walks through
  (`QaDoorwayClearanceGameTests`, `check.py`'s `doorway_problems`); the composter and every chest reachable on foot from
  the ground round the build, within 2 across and 1 up or down of a walked cell
  (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`, run without Create, so Create's blocks
  are air there). **Villagers don't open fence gates** (vanilla, kept by `work/PathObstacles`), only wooden doors: a gate
  as the only way in, or the only way into the beds, shuts the Farmer out (`QaBuildReachGameTests` opens gates as a
  player does, so it won't catch this). Anything upstairs reached by stairs or a ladder from inside.
- **No entity** (villager, animal, frame, painting) and no jigsaw or structure void in the file; every block one a builder
  can place and that stands as drawn when done (`plan.unfinished` empty, `site.skipped() == 0`).
- **Glass**: no pane or bar joined on one side with air on the other (`B648GameTests`, `QaWindowsAndWards1028BugTests`,
  `QaBuilders1028GameTests`, `check.py`'s `open_panes`).

## Kept cells (a refresh of an old Greenhouse)
A Greenhouse already standing in a world is offered "Refresh to the latest look" once the old drawing is kept in
`blueprint_history/` (`QaRefreshHistory1028GameTests.qaEveryShippedOlderDrawingIsOfferedARefresh`). A refresh **never
moves or breaks** a block with a block entity or an acquirable job site (`build/Refreshes.kept`,
`MaterialRules.isProtected`: every container **and every Create kinetic block**). So in a world with Create the old
drawing's cells (x, y, z) stay as they are, and a new block wanted on one of them can't go in:
- composter (12, 1, 10); chest (11, 1, 10); barrel (12, 1, 9);
- water wheel (1, 1, 9); shafts (2-5, 1, 9); belt (6, 1, 3) to (6, 1, 9); depot (6, 1, 2); harvester (6, 2, 7); plough (6, 2, 9).
This is the code's rule, not a test's (`RefreshMatchesFreshBuildGameTests.differences` skips chests and job sites, and
the main suite has no Create). The cleaner the overlap, the better a refreshed old Greenhouse comes out: a composter
elsewhere leaves it with two (two Farmers), a machine elsewhere leaves the old line standing, unpowered, among the new
blocks. The ship step either keeps the composter (and ideally the line) on these cells or shows a refreshed old
Greenhouse ends with one composter and a turning belt. If the ship step adds `greenhouse` to
`QaReleaseRefresh1028GameTests.REDRAWN`, that test refreshes it in `huge_area` at helper (7, 2, 2) with a bench at
(3, 2, 27), so the plot below also fits there.

## Fits
- **Plot: at most 20 wide x 20 deep x 18 tall.** y = 0 sits on the clicked block; the front is the z = 0 side.
  - Width: `CreateBuildsCompatTests.build` sets the origin at helper x = min(9, 29 - width) in the 30 x 20 x 30
    `huge_area`, with the builder's bench at (2, 2, 2), the builder at (3, 2, 3) and the material barrels from x = 1 to 6
    at z = 5; at 20 wide or less the build and its two blocks of landscaping stay clear of them.
  - Depth: origin z = min(3, 29 - depth); 20 keeps it and its landscaping inside the area and clear of
    `QaReleaseRefresh1028GameTests`' bench at z = 27 (built from z = 2 there).
  - Height: built from helper y = 2 in the 20-tall `huge_area`: 18 at most (blocks above an area's height are never
    cleared). Taller means the ship step moves the test to `tall_area`, as the Brass Foundry's and Clock Tower's were.
  - `QaCreateGoneUpkeepGameTests` places it at (2, 2, 2) in the 30 x 30 x 30 `tall_area` and clears round it: fits.
- **Build time and block budget.** The test sets day time 2,000, build delay 1, builders' help off, and must reach DONE
  (every block right, nothing skipped, the machine turning) within its **30,000-tick** timeout. It does not hold the
  clock: the builder works only until day time 11,000 (`ModVillagers.BUILDER_SCHEDULE`), so the first working day is
  **about 9,000 ticks**, and the next day's work starts only at tick ~23,000. Plan to finish in the first day. Builders
  lay about one block per 10 ticks with the walking and fetching. **Keep it to about 650 placed (non-air) blocks, never
  over 850** (the old drawing is 613, water included).
- **Materials**: every block must come from the materials list (the test stocks barrels with exactly
  `plan.materials()`); a new material needs a plural in `en_us.json`
  (`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`).
- **Declared size**: `StarterBlueprints.GREENHOUSE` is `new Vec3i(15, 11, 13)`; the tests place the build from it, the
  scene spaces its row by it and the Table hands it out with it, so the ship step sets it to the new size.
- **Front and way in**: the z = 0 side faces the player who places it ("the gold edge of the outline is the front");
  the way in is there.
- **Neighbours**: none fixed (no Steward rule, no village plan or zone, not on the Village Map's named list
  (`VillageMaps.kindOf`), not a decoration (`Decorations.points` 0)). Scene `create_builds_more` (`JobScenes`) sets it
  first in the back row, 16 blocks behind the Brass Foundry's row, its neighbours 3 blocks to each side by their sizes.

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `CreateBuildsCompatTests.aBuilderBuildsTheGreenhouseAndItTurns` (compat suite, with Create; `huge_area`, timeout
  30,000, batch `greenhouse_build`): built unturned from barrels holding exactly its materials; when done every block as
  drawn, nothing skipped, and `minecraft:composter` at **(12, 1, 10)** (the first expectation: must be a job site),
  `create:belt` at **(6, 1, 6)**, `create:mechanical_harvester` at **(6, 2, 7)**, `create:mechanical_plough` at
  **(6, 2, 9)**, `create:depot` at **(6, 1, 2)**, `minecraft:farmland` at **(7, 0, 5)**, `minecraft:wheat` at
  **(7, 1, 5)**; the machine (the belt's start pulley) at **(6, 1, 9)** turns, and the belt's far pulley at
  **(6, 1, 3)** turns (`Speed` not 0, read by name).
- In code, not a test: `JobScenes` `create_builds_more` reads the machine at (6, 1, 9) (it must turn for the scene to
  pass, `MachineWork.turning`); the scene places the template directly with no builder.
- Not tested, but a player can turn or flip any build at the Table (`QaCreateBuildsTurnedCompatTests` turns or flips six
  of the Create builds, not this one): the wheel should still meet its water and the belt still turn when turned or
  flipped (no gear that only meshes one way round).
- `QaCreateGoneUpkeepGameTests.aCreateBuildStandingWithoutCreateNeedsNoRepair` (main suite): no repair needed without
  Create, as placed and after 30 ticks of its water running (see "Water stays where it is drawn").

By id only (the drawing doesn't matter beyond the rules above): `CreateBuildsGameTests.hiddenWithoutCreate`,
`theSwitchHoldsThemBack` (and that `CREATE_ONLY` holds the six 39.8 builds), `noContraptionsWithoutCreate`;
`CreateBuildsCompatTests.theCreateBuildsAreInTheTable`; `QaTableCreate1117eCompatTests` (materials list real items, at
least one of Create's; a preview with at least one Create block; every style live; `getBlueprintHandsOutEveryCreateBuild`);
`QaCreateSwitchesOff1102CompatTests.createBuildsOffTakesThemOffTheTableButTheyStillLoadById`.

Library-wide checks it must pass (they read every listed build, Create's blocks as air in the main suite):
`QaBuildReachGameTests`, `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`, `QaTurnedFramesGameTests` (any frame
or painting), `QaMirroredBuildsGameTests` (doors, beds and double chests whole, turned and mirrored),
`QaTurnedModBlocksGameTests` (facing blocks turn with the build),
`QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`,
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `B648GameTests`, `QaWindowsAndWards1028BugTests`,
`QaBuilders1028GameTests`, `BlueprintsStayGameTests` (lamps on a block that holds them; no farmland or dirt path under a
solid block; bed foot and head), `QaFurnishedRooms1031GameTests` (no standing lantern on a room's floor; every indoor
chest against a wall, a solid block, a bed or another chest), `FurnitureSpotGameTests`, `FloatingFurnishingsGameTests`,
`StyleGameTests.chimneysStayWholeInEveryStyle` (only if a campfire stands on a stack: it must stay solid in every style).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair, upper floors by stairs or a ladder, nothing that falls over air; `open_panes`),
STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no CHECK line, and `holes.py` passes. The old drawing
has 2 entries in `owner_rules_baseline.json`; the rebuild must come out clean and the ship step removes them, keeps the
old drawing in `blueprint_history/` (`history.py`; a site begun on it finishes as drawn), regenerates through
`generate.py` (`MORE_CREATE_BUILDS` in `create_builds.py`), and updates the wiki (`docs/wiki/create.md`), the guide
(`docs/guide/create-builds.md`), `StarterBlueprints.GREENHOUSE`'s size and javadoc, the scene and the pinned
coordinates above.

## Free
Everything not listed above: shape, plan, roof, materials, colors, glazing or none (within the crop-light rule), storeys,
where the wheel, the water, the belt, the depot and the composter stand (within the pinned spots, which the ship step may
move, and the kept cells), how many beds of farmland and which crops (wheat at the pinned cell; the guide line is
rewritten to match), how the harvester and plough are mounted, paths, lighting, seats, a potting bench or tool racks,
more chests, a bed or none (with its rules), and what the greenhouse shows from the street.
