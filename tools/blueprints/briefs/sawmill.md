# Sawmill: the brief

Family id(s): `sawmill` (one level, never renamed; "Sawmill" in `en_us.json`, `blueprint.aliveworkplace.sawmill`;
hidden advancement `built/sawmill`)
Variants: village (none), modded (none: `modded_library.py` skips `CREATE_ONLY`), looks (none). The Table's style
buttons (`BlueprintStyles`) restyle it automatically; `QaTableCreate1117eCompatTests.everyCreateBuildHasMaterialsAPreviewAndLiveStyles`
only asks that every listed style has materials and a preview (automatic).

ROADMAP 50.2 (with the Carpenter's Workshop and the Mason's Yard). One version (not a flagship).

## What it is for
One of the nine **Create-powered builds** (ROADMAP 39.8, `StarterBlueprints.MORE_CREATE_BUILDS`): a working Create
machine and a workplace. It is at the Blueprint Table **only with Create installed and `createBuilds` on**
(`CreateBuilds.listed`), and still loads by id without them (a blueprint already handed out keeps working). A builder
builds it from his chests; Create's moving parts go in last, by group (shafts and gearboxes, then cogwheels, then belts,
`kinetic_parts/create.json`, `Contraptions`), and still water is poured from a bucket at the end (`MaterialRules`: still
water is DECORATION, flowing water comes by itself and is never placed). The moment the builder is done the machine
must turn **by itself**: a belt line runs through **two mechanical saws** to a **depot** ("saws on a belt line feeding a
depot, a water wheel", ROADMAP 39.8). About 100 ticks after it is finished the builder checks every kinetic part and
tells the owner which "don't turn" (`Contraptions.CHECK_AFTER`, `message.aliveworkplace.contraption_stuck`). The
workplace half is a **Lumberjack's** job block (a fletching table: `ModVillagers.LUMBERJACK` takes a Chopping Block or
a fletching table, the Fletcher POI) with his chest: he fells natural trees within 16 blocks of his job block
(`LumberjackWork.RADIUS`) and brings the logs back to the chests round it. No villager works the saws (there is no
`machine_work` file for saws); the Mechanic (39.2) puts broken drive parts back from the blueprint.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `sawmill` | A self-starting drive turning a belt through two mechanical saws to a depot; a Lumberjack's job block and his chest | At the Table with Create and `createBuilds` (expansion M39); `StarterBlueprints.expansionSwitch` is `createBuilds` |

**One level only.** A `sawmill_2` would be a new feature (a `StarterBlueprints` entry, upgrade offers).

## Must have
- **Job block: one `minecraft:fletching_table`**, a job site once built (both tests below assert the block id at the
  pinned spot and that `getPoiManager().getType(...)` is present). It is the Lumberjack's (and a vanilla Fletcher's)
  job site. No other job block is tested, but every vanilla or mod job block takes a villager for its own job; the old
  drawing's **barrel is a Fisherman's job site**, so prefer none (a barrel also counts as storage, see below).
- **The machine line (Create blocks, by id; checked in `libs/create-fabric-6.0.10.0+mc1.21.1.jar`):**
  - two `create:mechanical_saw`, both turning (`Speed` read from block entity data by name, not 0);
  - one `create:belt` (at least one segment at the pinned spot below), whole: every segment names a controller
    segment of its own belt, numbered 0 to its length - 1 once each (`assertBeltsWhole`);
  - one `create:depot` at the end of the line;
  - a drive that **starts itself** when the last block goes in. Only the Windmill gets a push from the builder
    (`CreateBuilds.finished` acts on the Windmill's id alone), so the Sawmill's drive must be a water wheel turned by
    water that the builder pours as a **source block** (falling and running water must come by itself from that
    source; the builder never places flowing water). The source must be reachable to pour from a bucket.
  - **Every kinetic part turns**: every shaft, gearbox, cogwheel and belt in the drawing (the parts `Contraptions`
    checks), so the owner never reads "N parts don't turn". No decorative, unconnected shaft or cogwheel.
  - **Enough stress capacity** for everything on the network: an over-stressed Create network stops (Speed 0) and
    fails both tests.
  - Create block states that matter (from `create_builds.py`'s header, checked in the jar): a water wheel's `facing` is
    its axle's direction; a shaft's, gearbox's and cogwheel's `axis` their own; a belt's `part` (`start`, `middle`,
    `end`), `facing`, `slope` and `casing`; a saw's `facing`, `axis_along_first` and `flipped` (two saws geared in a
    chain turn opposite ways; the old drawing flips the second so both cut the same way).
- **Storage.** At least **one chest within 8 blocks across and 4 up or down of the fletching table**
  (`SupplyContainers.RADIUS`/`VERTICAL`): the Lumberjack's drop-off and where he takes saplings, bone meal and his axe
  (`LumberjackWork.containerNear`, `SupplyContainers.find`); with none he walks to his job block and has nowhere to put
  the logs. The depot, belt and saws never count as storage (`Kinetics.keepsOwnItems`). Drawn empty (builders never
  copy containers). Every chest reachable on foot (`QaBuildReachGameTests`).
- **Beds and residents.** None required (no code or test counts a resident here; the old one has no bed). A bed, if
  drawn, counts as a village bed and must pass the bed checks below.
- **Furniture spots.** No kind is required. Every piece drawn is recorded as a spot (`rebuild.py`) and must pass
  `FurnitureSpotGameTests`, `FloatingFurnishingsGameTests` and `QaChairFacingGameTests`.
- **A way in on the z = 0 side** that a villager 1.95 blocks tall walks through (`QaDoorwayClearanceGameTests`), the
  fletching table and every chest reachable on foot from outside within 2 across and 1 up or down
  (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`; it runs without Create, so Create's
  blocks are air there); anything upstairs reached by stairs or a ladder from inside.
- **Built whole, nothing skipped**: every block must be placeable by a builder from the materials list and stand as
  drawn when done (`plan.unfinished` empty, `site.skipped() == 0`). A block that needs a support placed after it, or
  that the running water would wash out, fails this.

## Fits
- **Plot: at most 20 wide x 16 deep x 26 tall.** Width: the compat tests set the origin at helper
  x = min(9, 29 - width) in the 30-wide `huge_area`, with the builder's bench at (2, 2, 2), the builder at (3, 2, 3)
  and the material barrels at x 1-6; at 20 wide or less the build and its 2-block landscaping stay clear of them.
  Depth: the showcase scene `create_builds_more` (`JobScenes`) sets the Sawmill in a front row 16 blocks before the
  Greenhouse's row, so deeper than 16 overlaps it (or the ship step moves the row). Height: built from helper y = 2
  in a 30-tall area, and `QaCreateGoneUpkeepGameTests` clears y 2-28 round it.
- y = 0 sits on the clicked block; the front is the z = 0 side.
- **Build time: within 30,000 ticks** (both tests' timeout, build delay 1, builders' help off), the build, the last
  water and the machine turning. The old drawing is 540 blocks.
- **Declared size:** `StarterBlueprints.SAWMILL` is `new Vec3i(15, 11, 12)`; the tests place the build from it and
  the Table hands blueprints out with it, so the ship step sets it to the new size.
- **Front and way in:** the z = 0 side faces the player who places it; the way in is there.
- **Neighbours:** none fixed (no Steward rule, no village plan, no renewal places it).

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `CreateBuildsCompatTests.aBuilderBuildsTheSawmillAndItTurns` (compat suite, with Create): built unturned from
  barrels holding exactly its materials; when done every block as drawn, nothing skipped, and
  `minecraft:fletching_table` at **(10, 1, 2)** (the first expectation: must be a job site), `create:mechanical_saw` at
  **(4, 1, 4)** and **(4, 1, 3)**, `create:depot` at **(4, 1, 2)**, `create:belt` at **(4, 1, 6)**; the saw at
  (4, 1, 4) turns (the "machine") and the saw at (4, 1, 3) turns.
- `QaCreateBuildsTurnedCompatTests.aFlippedSawmillIsBuiltAndBothSawsTurn`: the same build **flipped left to right**
  (`Mirror.FRONT_BACK`) from exactly its materials: built whole, nothing skipped, the fletching table at (10, 1, 2) a
  job site, the parts at (4, 1, 4), (4, 1, 3) and (4, 1, 6) turning, every belt whole. So the line must still turn
  mirrored: the wheel must still meet its water, gears still mesh.
- In code, not a test: `JobScenes` `create_builds_more` reads the machine at (4, 1, 4) (it must turn for the scene to
  pass, `MachineWork.turning`).
- On a test branch, not on `main`: `QaMechanicFlippedBelt1103eCompatTests` (B669, `tests/mechanic-flipped-belt-1103`)
  builds the flipped Sawmill and breaks its belt; it names the belt by its place in the drawing.

By id only (the drawing doesn't matter beyond the rules above): `CreateBuildsGameTests.hiddenWithoutCreate`,
`theSwitchHoldsThemBack`; `CreateBuildsCompatTests.theCreateBuildsAreInTheTable`; `QaTableCreate1117eCompatTests`
(materials list real items, at least one of Create's; a preview with at least one Create block; every style live;
`getBlueprintHandsOutEveryCreateBuild`); `QaCreateSwitchesOff1102CompatTests.createBuildsOffTakesThemOffTheTableButTheyStillLoadById`.

- `QaCreateGoneUpkeepGameTests.aCreateBuildStandingWithoutCreateNeedsNoRepair`: the build placed without Create (its
  Create blocks gone to air) at helper (2, 2, 2) must need **no repair**, as placed and after 30 ticks of its water
  running. So when the wheel and every Create block are gone, the loose water must not wash out or break any drawn
  block (nothing water breaks, such as a torch, a carpet, a button, a rail or a plant, in the path the water could take).

Library-wide checks it must pass (they read every listed build, Create's blocks as air in the main suite):
`QaBuildReachGameTests`, `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`, `QaLedgerFramesHaulGameTests`,
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `B648GameTests`, `QaWindowsAndWards1028BugTests`,
`QaBuilders1028GameTests` (no pane or bar joined on one side with air on the other), `BlueprintsStayGameTests`
(lamps, paths, bed foot and head), `QaFurnishedRooms1031GameTests` (no standing lantern on a room's floor; every
indoor chest against a wall, a solid block, a bed or another chest), `FurnitureSpotGameTests`,
`FloatingFurnishingsGameTests`, `StyleGameTests.chimneysStayWholeInEveryStyle` (only if a campfire stands on a stack).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair; `open_panes`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no
CHECK line, and `holes.py` passes. The ship step removes `sawmill` from `owner_rules_baseline.json` (2 today), keeps the
old drawing in `blueprint_history/` (`history.py`), regenerates through `generate.py` (`MORE_CREATE_BUILDS`), and
updates `create_builds.py`'s entry, the wiki (`create.md`, `docs/guide/create-builds.md`) and the scene.

## Free
Everything not listed above: shape, plan, roof, materials, colors, storeys, a yard, a log store or none, where the
line, the wheel and the water run (within the pinned spots, which the ship step may move), how the saws are driven,
a bed or none, more chests, lighting, and what the mill shows from the street.
