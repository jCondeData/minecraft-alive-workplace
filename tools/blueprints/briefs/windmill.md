# Windmill: the brief

Family id(s): `windmill` (one level, never renamed; "Windmill" in `en_us.json`, `blueprint.aliveworkplace.windmill`;
hidden advancement `built/windmill`)
Variants: village (none), modded (none: a Create build, `StarterBlueprints.CREATE_ONLY`, one of the
`FIRST_CREATE_BUILDS`), looks (none). The Table's style buttons (`BlueprintStyles`) restyle it automatically;
`QaTableCreate1117eCompatTests.everyCreateBuildHasMaterialsAPreviewAndLiveStyles` only asks that every listed style
has materials and a preview (automatic). A style of the Windmill is started too (`CreateBuilds.finished` reads
`BlueprintStyles.base`).

ROADMAP 50.10 (with the Quarry, Sifting Shed, Crushing Mill and Gem Grotto). One version (not a flagship).

## What it is for
One of the three first **Create-powered builds** (ROADMAP 39.4, design note `docs/design/M39.md`, "The builds": "a
`create:windmill_bearing` with `create:white_sail` and `create:sail_frame` sails (at least 8), a shaft down to a
millstone"). It is at the Blueprint Table **only with Create installed and `createBuilds` on** (`CreateBuilds.listed`),
and still loads by id without them (a blueprint already handed out keeps working; a site started with Create is
finished without it). A builder builds it from his chests like any build; Create's drive parts go in last, by group
(shafts and gearboxes, then cogwheels, then belts: `kinetic_parts/create.json`, `Contraptions`). It is the **only**
build whose machine does not turn by itself: the moment the builder finishes, `CreateBuilds.finished` finds every
`create:windmill_bearing` inside the build's declared outline and **uses it with an empty hand** through a stand-in
player standing where the builder stands. Create then lifts the sails off the world as one moving contraption and
turns them, the bearing's drive turns the shafts and the **millstone grinds**. About 100 ticks after the build is done
the builder (or the village's Mechanic, if his toolbox is in reach) checks every kinetic part and tells the owner
"checked the Windmill: ..." which parts don't turn (`Contraptions.CHECK_AFTER`, `message.aliveworkplace.contraption_stuck`).

The workplace half is the **Farmer's**: the millstone is the Farmer's machine (`machine_work/millstone.json`: job
`minecraft:farmer`, recipe type `create:milling`, keeps up to 64 `create:wheat_flour` in the store, 16 at a time, works
only while the millstone's `Speed` is at least 1). A Farmer whose workstation (a **composter**) is within 32 blocks of
the millstone (`MachineWork.REACH`) takes wheat from the store chest by his composter, walks it to the millstone, drops
it in (feed and collect spot: the millstone itself) and carries the flour back. Only a millstone and chests **inside a
building the builders finished** count (`VillageMachines`, B243: inside the blueprint's outline), so the machine and the
store belong in this build. The village's Mechanic (39.2) also tends a turning millstone and puts broken drive parts
(shafts, gearboxes, cogwheels, belts) back from the blueprint.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `windmill` | A windmill bearing with sails the builder starts, driving a millstone; the Farmer's job block (a composter) and the store chest he mills from | At the Table from the start with Create and `createBuilds` (expansion M39); `StarterBlueprints.expansionSwitch` is `createBuilds` |

**One level only.** A `windmill_2` would be a new feature (a `StarterBlueprints` entry, upgrade offers).

## Must have
- **Job block: one `minecraft:composter`**, a job site once built (the build test's first expectation: the block id at
  its pinned spot and `getPoiManager().getType(...)` present). It is the Farmer's job site and the post `MachineWork`
  measures from. No other job block is tested, but every vanilla or mod job block takes a villager for its own job (a
  **barrel is a Fisherman's job site**, a lectern a Librarian's, a smoker a Butcher's...), so prefer none; a barrel
  would also count as storage.
- **The machine (Create blocks, by id; checked in `libs/create-fabric-6.0.10.0+mc1.21.1.jar`):**
  - one `create:millstone`, **turning** once the bearing is started (`Speed` read from its block entity data by name,
    not 0; the Farmer needs at least 1). **A millstone takes its drive from below only** (`MillstoneBlock.hasShaftTowards`
    is true for DOWN alone): a shaft or gearbox must feed straight up into its underside.
  - one `create:windmill_bearing` (pinned, below). **Its `facing` is the side the sails are on; its drive comes out of
    its back only** (`BearingBlock.hasShaftTowards`: the face opposite `facing`), so the shaft to the millstone leaves
    from the block behind it. The builder's empty-hand use reaches it wherever it is, as long as it is **inside the
    declared size** (`CreateBuilds.finished` searches `BlueprintOutline.bounds(placement, WINDMILL.size())`).
  - **The sails: at least 8** blocks of Create's windmill sails (`create:white_sail`, `create:sail_frame`; Create's
    `minimumWindmillSails` is 8, below that the bearing won't assemble and nothing turns). Speed is one RPM per 8 sails,
    at least 1 and at most 16 (`WindmillBearingBlockEntity.getGeneratedSpeed`, `windmillSailsPerRPM` 8). The sails
    are carried with whatever is attached to the block directly in front of the bearing; they are placed with their
    `facing` along the bearing's axis.
  - **What the bearing carries leaves the world.** When it starts, Create picks up the block in front of the bearing,
    every sail, and **every block touching a sail at its sides in the sail's own plane** (Create's
    `BlockMovementChecksImpl`: a sail is attached towards every direction whose axis is not its `facing` axis), plus
    anything hanging off those. So **no wall, roof, beam, lamp or sign may touch a sail edge-on in the sail plane**:
    it would be torn off the building and spun round, the build test would find it missing and the Table's repairs
    would want it back. Blocks in front of or behind the sails (along the bearing's axis) are not attached. Nothing
    unmovable may be attached either, or the bearing won't start.
  - **Every kinetic part turns** once started: every shaft, gearbox and cogwheel (and belt, if any) in the drawing, the
    parts `Contraptions` checks, so the owner never reads "N parts don't turn". No decorative, unconnected shaft or
    cogwheel.
  - **Enough stress capacity** for everything on the network: an over-stressed Create network stops (Speed 0) and fails
    the build test.
  - Create block states that matter (`create_builds.py`'s header, checked in the jar): a windmill bearing's `facing`
    is its axle's direction (the sails' side); a shaft's, gearbox's and cogwheel's `axis` their own; a sail's `facing`
    the bearing's axis.
- **Storage.** At least **one chest within 8 blocks across and 4 up or down of the composter**
  (`SupplyContainers.RADIUS`/`VERTICAL`, read through `VillageMachines.stores`) and inside the build: the Farmer's store,
  where the wheat comes from and the flour goes. With none, he has no machine work. The millstone never counts as
  storage (`Kinetics.keepsOwnItems`). Drawn empty (builders never copy containers).
- **On foot:** the Farmer walks to the store chest and to the millstone and must get within 3 blocks of each
  (`MachineWork`, `walker.reach(..., 3.0)`); the composter and every chest reachable on foot from outside within 2
  across and 1 up or down (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`; it runs
  without Create, so Create's blocks are air there); anything upstairs reached by stairs or a ladder from inside.
- **Doors:** every wooden door leaves a villager 1.95 blocks tall room to walk through (`QaDoorwayClearanceGameTests`);
  the way in is on the front (z = 0) side by the library's convention.
- **Beds and residents.** None required (no code or test counts a resident here; the old one has no bed). A bed, if
  drawn, counts as a village bed and must pass the bed checks below.
- **Furniture spots.** No kind is required (the old drawing has no `blueprint_furniture/windmill.json`). Every piece
  drawn is recorded as a spot (`rebuild.py`) and must pass `FurnitureSpotGameTests`, `FloatingFurnishingsGameTests` and
  `QaChairFacingGameTests`.
- **Built whole, nothing skipped**: every block placeable by a builder from the materials list and standing as drawn
  when done (`plan.unfinished` empty apart from the carried sail plane, `site.skipped() == 0`). A block that needs a
  support placed after it fails this.
- **Nothing rests on a Create block.** Without Create the Create blocks are air (below), so a block that only stands on,
  hangs from or leans on the bearing, a sail, a shaft, a gearbox or the millstone falls and the build wants a repair.

## Fits
- **Plot: at most 20 wide x 27 deep x 18 tall** (keeps every test area as it is):
  - `CreateBuildsCompatTests` places it in the 30 x 20 x 30 compat `huge_area` from helper y = 2 (**18 tall at most**),
    at x = min(9, 29 - width), z = min(3, 29 - depth), with the builder's bench at (2, 2, 2), the builder at (3, 2, 3)
    and the material barrels at x 1-6, z 5 (y 2 and up); at 20 wide or less the build and its 2 blocks of landscaping
    stay clear of them.
  - `QaCreateGoneUpkeepGameTests` places it at helper (2, 2, 2) in the 30-tall `tall_area` and clears x and z 0-29,
    y 2-28 between builds: within 28 x 28 x 27, so the compat area is the limit.
  - Anything bigger means the ship step moves the compat test (to `tall_area`, as the Brass Foundry's was) without
    dropping what it checks.
- y = 0 sits on the clicked block; the front is the z = 0 side.
- **Build time:** within **30,000 ticks** with Create (the compat build test's timeout, build delay 1, builders' help
  off: the build, the start and the millstone turning). The old drawing is 426 blocks.
- **Declared size:** `StarterBlueprints.WINDMILL` is `new Vec3i(9, 17, 9)` (width, height, depth); the test places the
  build from it, the Table hands blueprints out with it, and `CreateBuilds.finished` looks for the bearing only inside
  it, so the ship step sets it to the new size (the docs too: `docs/wiki/create.md` says "9 x 17 x 9").
- **Front and way in:** the z = 0 side faces the player who places it.
- **Neighbours:** none fixed (no Steward rule, no village plan, no renewal, no `blueprint_unlocks` entry places it).
  The showcase scene `create_builds` (`JobScenes`) sets the Watermill, the Windmill and the Workshop side by side, each
  3 blocks past the last one's width, fronts towards one fixed camera on the z = 0 side, and films "the Windmill's
  sails" turning: the sails are seen from the front there; a much wider Windmill pushes the Workshop along (the ship
  step re-aims if needed).

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `CreateBuildsCompatTests.aBuilderBuildsTheWindmillAndStartsIt` (compat suite, with Create, batch `windmill_build`):
  built unturned from barrels holding exactly its materials; when done the site is closed, every block as drawn,
  nothing skipped, `minecraft:composter` at **(2, 1, 6)** (the first expectation: must be a job site),
  `create:windmill_bearing` at **(4, 9, 1)**; the machine at **(4, 2, 5)** (the millstone) has a block entity and
  turns (`Speed` not 0). The "every block as drawn" check leaves out **only cells with z = 0 and y >= 5** for the
  Windmill (`build`'s filter: "a started windmill's sails and hub leave the world as blocks"). So every block the
  bearing carries must lie in that plane, or the ship step narrows the filter to exactly the carried cells; any other
  block the sails pull away (see "What the bearing carries") fails the test.
- In code, not a test: `JobScenes` `create_builds` places the structure, calls `CreateBuilds.finished` and waits for
  the machine at **(4, 2, 5)** to turn (`MachineWork.turning`); `tools/showcase/scenes.py` describes it ("the
  Windmill's sails ... both millstones").

Without Create (main suite; Create's blocks read as air):
- `QaCreateGoneUpkeepGameTests.aCreateBuildStandingWithoutCreateNeedsNoRepair`: the build placed without Create at
  helper (2, 2, 2) must need **no repair**, as placed and after 30 ticks. So with the bearing, the sails, every shaft,
  gearbox and the millstone gone to air, nothing drawn may need them to stand.

By id only (the drawing doesn't matter beyond the rules above): `CreateBuildsGameTests.hiddenWithoutCreate` (hidden,
loads by id, behind `createBuilds`, has its `blueprint.aliveworkplace.windmill` text), `theSwitchHoldsThemBack`;
`CreateBuildsCompatTests.theCreateBuildsAreInTheTable`; `QaTableCreate1117eCompatTests` (materials list real items, at
least one of Create's; a preview with at least one Create block; every style live;
`getBlueprintHandsOutEveryCreateBuild`); `QaCreateSwitchesOff1102CompatTests.createBuildsOffTakesThemOffTheTableButTheyStillLoadById`;
`ArticleGameTests` (the name "Windmill" in a Mechanic line).

Library-wide checks it must pass (they read every listed build, Create's blocks as air in the main suite):
`QaBuildReachGameTests`, `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`, `QaLedgerFramesHaulGameTests`,
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `B648GameTests`, `QaWindowsAndWards1028BugTests`,
`QaBuilders1028GameTests` (no pane or bar joined on one side with air on the other), `BlueprintsStayGameTests`
(lamps, paths, bed foot and head), `QaFurnishedRooms1031GameTests` (no standing lantern on a room's floor; every
indoor chest against a wall, a solid block, a bed or another chest), `FurnitureSpotGameTests`,
`FloatingFurnishingsGameTests`, `StyleGameTests.chimneysStayWholeInEveryStyle` (only if a campfire stands on a stack).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair; `open_panes`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no
CHECK line, and `holes.py` passes. The ship step removes `windmill` from `owner_rules_baseline.json` (2 today), keeps
the old drawing in `blueprint_history/` (`history.py`), takes `windmill` out of `create_builds.py`'s `CREATE_BUILDS`
(regenerating through `generate.py` as the Watermill and Workshop do), and updates the declared size, the pinned spots
in the test and the scene, the test's sail-plane filter if the sails move, and the wiki (`docs/wiki/create.md`).

## Free
Everything not listed above: shape, plan, roof, materials, colors, storeys, where the sails face and how many there
are (8 or more), how the drive runs from the bearing's back to the millstone's underside (more shafts or gears as long
as all turn), a yard, a grain store or none, a bed or none, more chests, lighting, and what the mill shows from the
street.
