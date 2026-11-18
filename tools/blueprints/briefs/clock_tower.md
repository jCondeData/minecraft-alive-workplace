# Clock Tower: the brief

Family id(s): `clock_tower` (one level, never renamed; "Clock Tower" in `en_us.json`,
`blueprint.aliveworkplace.clock_tower`; hidden advancement `built/clock_tower`)
Variants: village (none), modded (none: `modded_library.py` skips `CREATE_ONLY`), looks (none). The Table's style
buttons (`BlueprintStyles`) restyle it automatically; `QaTableCreate1117eCompatTests.everyCreateBuildHasMaterialsAPreviewAndLiveStyles`
only asks that every listed style has materials and a preview (automatic).
Not this family: the clock tower drawn into **Village Hall III** (`village_hall.py`) and the **School**'s village look
(`village_looks.py`) are parts of other families.

ROADMAP 51.6 (with the Chapel, the Graveyard and the Post Office). One version (not a flagship).

## What it is for
One of the nine **Create-powered builds** (ROADMAP 39.8, `StarterBlueprints.MORE_CREATE_BUILDS`, fifth in the Table's
order): "a cuckoo clock and shafts, a purely decorative build with a working mechanism". It is at the Blueprint Table
**only with Create installed and `createBuilds` on** (`CreateBuilds.listed`; `StarterBlueprints.expansionSwitch` is
`createBuilds`), and still loads by id without them (a blueprint already handed out keeps working). A builder builds it
from his chests; Create's moving parts go in last, by group (shafts and gearboxes, then cogwheels, then belts,
`kinetic_parts/create.json`, `Contraptions`), and still water is poured from a bucket at the end (`MaterialRules`:
still water is DECORATION, flowing water comes by itself and is never placed). The moment the builder is done a
**`create:cuckoo_clock` must turn by itself** and keep the time (the player guide: "It's there to look at, and the
clock keeps the time"). About 100 ticks after it is finished the builder checks every kinetic part and tells the owner
which "don't turn" (`Contraptions.CHECK_AFTER`, `message.aliveworkplace.contraption_stuck`). No villager works the
clock (there is no `machine_work` file for it); the Mechanic (39.2) puts broken drive parts back from the blueprint.

The workplace half is small: one **Cartographer's** job block (a cartography table, the vanilla Cartographer POI; the
mod's Printer also works at one, `ModVillagers`). No code gives this building any other job, service, Steward rule,
renewal or village plan.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `clock_tower` | A self-starting drive turning a cuckoo clock; a Cartographer's job block | At the Table with Create and `createBuilds` (expansion M39) |

**One level only.** A `clock_tower_2` would be a new feature (a `StarterBlueprints` entry, upgrade offers).

## Must have
- **Job block: one `minecraft:cartography_table`**, a job site once built (`CreateBuildsCompatTests.aBuilderBuildsTheClockTowerAndItTurns`
  asserts the block id at its pinned spot and that `getPoiManager().getType(...)` is present: it is the test's first
  expectation, the one that must be a job site). Every other vanilla or mod job block pulls in another trade: the old
  drawing's **barrel is a Fisherman's job site**, so prefer none (a smoker, composter, lectern, loom, stonecutter,
  grindstone, cauldron, brewing stand, smithing/fletching table, bell and the like each take a villager for their own
  job). A chest is not a job block.
- **The machine (Create blocks, by id; checked in `libs/create-fabric-6.0.10.0+mc1.21.1.jar`):**
  - one `create:cuckoo_clock` that **turns** (`Speed` read from its block entity data by name must not be 0; it is the
    test's "machine"). It takes its drive only through its back: the shaft enters from the cell behind the face (the
    side opposite its `facing`); the old drawing has `facing=north` fed from the cell at z + 1. A cuckoo clock is a
    block with a face on one side; the test asserts it at a pinned spot, which the ship step moves.
  - at least one `create:gearbox` and one `create:shaft` (the test pins one of each by id today, as "shafts up the
    tower" and the turn to the clock; the ship step moves the spots but keeps a gearbox and a shaft to check);
  - a drive that **starts itself** when the last block goes in. Only the Windmill gets a push from the builder
    (`CreateBuilds.finished` acts on the Windmill's id alone), so the drive must be a water wheel turned by water the
    builder pours as a **source block** (falling and running water must come by itself from that source; the builder
    never places flowing water). The source must be reachable to pour from a bucket.
  - **Every kinetic part turns**: every shaft, gearbox, cogwheel and belt in the drawing (the parts `Contraptions`
    checks), so the owner never reads "N parts don't turn". No decorative, unconnected shaft or cogwheel.
  - **Enough stress capacity** for everything on the network (an over-stressed Create network stops, Speed 0, and fails
    the test).
  - Create block states that matter (from `create_builds.py`'s header, checked in the jar): a water wheel's `facing` is
    its axle's direction; a shaft's, gearbox's and cogwheel's `axis` their own; a large and a small cogwheel mesh only
    diagonally adjacent on parallel axes.
- **Beds and residents.** None required (no code or test counts a resident here; the old one has no bed). A bed, if
  drawn, counts as a village bed and must pass the bed checks below.
- **Storage.** None required (no job here reads a chest). Any chest is drawn empty (builders never copy containers), is
  reachable on foot (`QaBuildReachGameTests`) and, indoors, stands against a wall, a solid block, a bed or another chest
  (`QaFurnishedRooms1031GameTests`). Create's blocks never count as storage (`Kinetics.keepsOwnItems`).
- **Furniture spots.** No kind is required (no `blueprint_furniture/clock_tower.json` today; not a home). Every piece
  drawn is recorded as a spot (`rebuild.py`) and must pass `FurnitureSpotGameTests`, `FloatingFurnishingsGameTests` and
  `QaChairFacingGameTests`.
- **A way in on the z = 0 side** that a villager 1.95 blocks tall walks through (`QaDoorwayClearanceGameTests`), the
  cartography table and every chest reachable on foot from outside within 2 across and 1 up or down
  (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`; it runs without Create, so Create's
  blocks are air there); anything upstairs reached by stairs or a ladder from inside (B149).
- **No entity** and no jigsaw or structure void in the file.
- **Built whole, nothing skipped**: every block must be placeable by a builder from the materials list and stand as
  drawn when done (`plan.unfinished` empty, `site.skipped() == 0`). A block that needs a support placed after it, or
  that the running water would wash out, fails this.

## Fits
- **Plot: at most 20 wide x 24 deep x 27 tall.**
  - Width: `CreateBuildsCompatTests` sets the origin at helper x = min(9, 29 - width) in a 30-wide area, the builder's
    bench at (2, 2, 2), the builder at (3, 2, 3) and the material barrels from x = 1 to 6 at z = 5 (stacked up from
    y = 2); at 20 wide or less the build and its 2-block landscaping stay clear of them.
  - Depth: the same test sets the origin at z = min(3, 29 - depth); `QaReleaseRefresh1028GameTests` builds it at helper
    (7, 2, 2) with a builder's bench at (3, 2, 27), so deeper than 24 reaches the bench.
  - **Height: the test areas.** Both tests that build it use **`huge_area`, which is 30 x 20 x 30** (only 20 tall), and
    build from helper y = 2: anything taller than 18 rises above that area's top (the old 19-tall tower already pokes
    one row out). If the new one is taller than 18, the ship step moves `aBuilderBuildsTheClockTowerAndItTurns` to
    `TALL_AREA` (`aliveworkplace_compat:tall_area`, 30 x 30 x 30, as the Brass Foundry's test did) and puts
    `clock_tower` on `QaReleaseRefresh1028GameTests`' tall list (as the Townhouse is). **`tall_area` is 30 tall and the
    build starts at y = 2, so 27 is the hard limit**: `QaCreateGoneUpkeepGameTests` places it at (2, 2, 2) in the
    30-tall area and clears only y 2-28 round it. Anything taller can't be tested.
- y = 0 sits on the clicked block; the front is the z = 0 side.
- **Build time: within 30,000 ticks** (the compat test's timeout, build delay 1, builders' help off): the whole build,
  the last water poured and the clock turning. Keep it to **about 900 placed blocks or fewer** (the old drawing is 541):
  a 1,410-block School timed out at its 12,000-tick test, its builder stuck on a barge stair above a lower roof, while
  about 900 passed. **No roof, overhang, barge or eave over another roof** (that is what stuck the builder).
- **Declared size:** `StarterBlueprints.CLOCK_TOWER` is `new Entry(id("clock_tower"), new Vec3i(11, 19, 9))`; the tests
  place the build from it, the showcase scene spaces its row by it and the Table hands blueprints out with it, so the
  ship step sets it to the new size.
- **Front and way in:** the z = 0 side faces the player who places it; the way in is there. The showcase scene
  `create_builds_more` sets it in the back row facing the camera, so the clock should be seen from the front (free, but
  the scene films that side).
- **Neighbours:** none fixed (no Steward rule, no village plan, no renewal, not in the starting set or on the Village
  Map's named list). In the scene the Greenhouse stands to its left and the Pump House to its right, 3 blocks apart
  (spaced by the declared widths).

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `CreateBuildsCompatTests.aBuilderBuildsTheClockTowerAndItTurns` (compat suite, with Create; `HUGE_AREA`, timeout
  30,000, batch `clock_tower_build`): built unturned from barrels holding exactly its materials; when done every block
  as drawn, nothing skipped, and `minecraft:cartography_table` at **(8, 1, 2)** (a job site), `create:cuckoo_clock` at
  **(6, 10, 1)**, `create:gearbox` at **(6, 10, 5)**, `create:shaft` at **(6, 5, 5)**; the cuckoo clock at (6, 10, 1)
  turns (`Speed` not 0).
- `QaReleaseRefresh1028GameTests.qaReleaseRefresh_clock_tower` (main suite, `huge_area`, 200 ticks): a Clock Tower
  standing as 0.151.0 drew it (`blueprint_history/clock_tower/cd1557d8c.nbt`) at helper (7, 2, 2) is offered "Refresh to
  the latest look" on the Village Hall's Repairs page, and the refresh's plan carried out leaves it equal to a fresh
  build of today's drawing. The new drawing must differ from that old one (it will) and fit the area (see Height).
- Not tested, but a player can turn or flip any build at the Table (`QaCreateBuildsTurnedCompatTests` turns the Brass
  Foundry, Pump House and Workshop, not this one): the wheel should still meet its water and the drive still reach the
  clock turned or flipped (no gear that only meshes one way round).
- In code, not a test: `JobScenes` `create_builds_more` reads the clock at (6, 10, 1) (it must turn for the scene to
  pass, `MachineWork.turning`); the scene places the template directly, so the water must start the wheel with no
  builder.
- `QaCreateGoneUpkeepGameTests.aCreateBuildStandingWithoutCreateNeedsNoRepair`: the build placed without Create (its
  Create blocks gone to air) at helper (2, 2, 2) in the 30-tall area must need **no repair**, as placed and after 30
  ticks of its water running. So with the wheel, shafts, gearboxes and clock gone, the loose water must not wash out or
  break any drawn block (nothing water breaks, such as a torch, a carpet, a button, a rail or a plant, where it could
  run, including down an empty shaft line), and nothing drawn may rest on or hang from a Create block (the clock or a
  gearbox gone to air leaves it floating).

By id only (the drawing doesn't matter beyond the rules above): `CreateBuildsGameTests.hiddenWithoutCreate` (names
`CLOCK_TOWER` among the Create-only builds; needs `blueprint.aliveworkplace.clock_tower` in `en_us.json`),
`theSwitchHoldsThemBack`; `CreateBuildsCompatTests.theCreateBuildsAreInTheTable`; `QaTableCreate1117eCompatTests`
(materials list real items, **at least one of Create's**; a preview with at least one Create block; every style live;
`getBlueprintHandsOutEveryCreateBuild`); `QaCreateSwitchesOff1102CompatTests.createBuildsOffTakesThemOffTheTableButTheyStillLoadById`.

Library-wide checks it must pass (they read every listed build, Create's blocks as air in the main suite):
`QaBuildReachGameTests`, `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`, `QaLedgerFramesHaulGameTests` /
`QaTurnedFramesGameTests` (frames and paintings hang turned and mirrored), `QaMirroredBuildsGameTests` (doors, beds and
double chests whole, turned and mirrored), `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`,
`B648GameTests`, `QaWindowsAndWards1028BugTests`, `QaBuilders1028GameTests` (no pane or bar joined on one side with air
on the other), `BlueprintsStayGameTests` (lamps on a block that holds them, paths, bed foot and head),
`QaFurnishedRooms1031GameTests` (no standing lantern on a room's floor; every indoor chest against a wall, a solid
block, a bed or another chest), `FurnitureSpotGameTests`, `FloatingFurnishingsGameTests`,
`StyleGameTests.chimneysStayWholeInEveryStyle` (only if a campfire stands on a stack).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair, no bed right inside a door; `open_panes`), STYLE.md's checks and `holes.py`:
`rebuild.py --candidate` prints no CHECK line, and `holes.py` passes. The ship step removes `clock_tower` from
`owner_rules_baseline.json` (2 today), saves today's drawing in `blueprint_history/clock_tower/` (`history.py`; a site
begun on it finishes as drawn, and keep `cd1557d8c.nbt`, which the refresh test reads), regenerates through
`generate.py` (`MORE_CREATE_BUILDS`), and updates `create_builds.py`'s entry, `StarterBlueprints.CLOCK_TOWER`'s size,
the wiki (`docs/wiki/create.md`, `docs/guide/create-builds.md`), the scene and the pinned coordinates above.

## Free
Everything not listed above: shape, plan, roof, materials, colors, storeys, where the wheel, the water and the drive run
and where the clock sits (within the rules; the ship step moves the pinned spots), more than one clock face (each one
driven and turning), a bell or none (a bell is a village meeting point that draws villagers: prefer none), a chest or none, a bed or none, lighting, and what the tower shows from the
street.
