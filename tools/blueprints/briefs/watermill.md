# Watermill: the brief

Family id(s): `watermill` (one level, never renamed; "Watermill" in `en_us.json`, `blueprint.aliveworkplace.watermill`;
hidden advancement `built/watermill`)
Variants: village (none), modded (none: a Create build, `StarterBlueprints.CREATE_ONLY`), looks (none). The Table's
style buttons (`BlueprintStyles`) restyle it automatically; `QaTableCreate1117eCompatTests.everyCreateBuildHasMaterialsAPreviewAndLiveStyles`
only asks that every listed style has materials and a preview (automatic).

ROADMAP 50.5 (with the Fisher's Hut and the Ferry House). One version (not a flagship).

## What it is for
The first of the **Create-powered builds** (ROADMAP 39.4, `StarterBlueprints.FIRST_CREATE_BUILDS`, and the first entry
of `CREATE_ONLY`): a working Create machine and a workplace, "a water wheel turning a millstone". It is at the Blueprint
Table **only with Create installed and `createBuilds` on** (`CreateBuilds.listed`), and still loads by id without them
(a blueprint already handed out keeps working; a site started with Create is finished without it). A builder builds it
from his chests; Create's moving parts go in last, by group (shafts and gearboxes, then cogwheels, then belts:
`kinetic_parts/create.json`, `Contraptions`), and still water is poured from a bucket at the end (`MaterialRules`: a
still source is poured once the walls are up; flowing water comes by itself and is never placed). The moment the
builder is done the **millstone must turn by itself** from a water wheel (only the Windmill gets a push from the
builder: `CreateBuilds.finished` acts on the Windmill's id alone). About 100 ticks later the builder (or the village's
Mechanic, if his toolbox is in reach) checks every kinetic part and tells the owner "checked the Watermill: ..." which
parts don't turn (`Contraptions.CHECK_AFTER`, `message.aliveworkplace.contraption_stuck`).

The workplace half is the **Farmer's**: the millstone is the Farmer's machine (`machine_work/millstone.json`: job
`minecraft:farmer`, recipe type `create:milling`, it keeps up to 64 `create:wheat_flour` in the store, 16 at a time,
works only while the millstone's `Speed` is at least 1). A Farmer whose workstation (a **composter**) is within 32
blocks of the millstone (`MachineWork.REACH`) takes wheat from the store chest by his composter, walks it to the
millstone, drops it in (feed and collect spot: the millstone itself), and carries the flour back to the store. Only a
millstone and chests **inside a building the builders finished** count (`VillageMachines`, B243: inside the blueprint's
outline), so the machine and the store belong in this build. The village's Mechanic (39.2) also tends a turning
millstone and puts broken drive parts back from the blueprint.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `watermill` | A self-starting water wheel driving a millstone; the Farmer's job block (a composter) and the store chest he mills from | At the Table with Create and `createBuilds` (expansion M39); `StarterBlueprints.expansionSwitch` is `createBuilds` |

**One level only.** A `watermill_2` would be a new feature (a `StarterBlueprints` entry, upgrade offers).

## Must have
- **Job block: one `minecraft:composter`**, a job site once built (both build tests assert the block id at the pinned
  spot and that `getPoiManager().getType(...)` is present). It is the Farmer's job site and the post `MachineWork`
  measures from. No other job block is tested, but every vanilla or mod job block takes a villager for its own job: a
  **barrel is a Fisherman's job site**, a lectern a Librarian's and so on, so prefer none (a barrel would also count as
  storage).
- **The machine (Create blocks, by id; checked in `libs/create-fabric-6.0.10.0+mc1.21.1.jar`):**
  - one `create:millstone`, **turning** (`Speed` read from its block entity data by name, not 0; the Farmer needs at
    least 1). **A millstone takes its drive from below only** (`create_builds.py` header): the tests pin a
    `create:gearbox` directly under it, so the drive comes up into its underside through a gearbox.
  - one `create:water_wheel` (pinned below), turned by water the builder pours as a **source block**; falling and
    running water must come by itself from that source (the builder never places flowing water). The source must be
    reachable to pour from a bucket once the walls are up.
  - **Every kinetic part turns**: every shaft, gearbox and cogwheel (and belt, if any) in the drawing, the parts
    `Contraptions` checks, so the owner never reads "N parts don't turn". No decorative, unconnected shaft or cogwheel.
  - **Enough stress capacity** for everything on the network: an over-stressed Create network stops (Speed 0) and
    fails both build tests.
  - Create block states that matter (from `create_builds.py`'s header, checked in the jar): a water wheel's `facing`
    is its axle's direction; a shaft's, gearbox's and cogwheel's `axis` their own.
- **Storage.** At least **one chest within 8 blocks across and 4 up or down of the composter**
  (`SupplyContainers.RADIUS`/`VERTICAL`, read through `VillageMachines.stores`) and inside the build: the Farmer's
  store, where the wheat comes from and the flour goes. With none, he has no machine work. The millstone never counts
  as storage (`Kinetics.keepsOwnItems`). Drawn empty (builders never copy containers).
- **On foot:** the Farmer walks to the store chest and to the millstone and must get within 3 blocks of each
  (`MachineWork`, `walker.reach(..., 3.0)`); the composter and every chest reachable on foot from outside within 2
  across and 1 up or down (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`; it runs
  without Create, so Create's blocks are air there); anything upstairs reached by stairs or a ladder from inside.
- **A way in on the z = 0 side** that a villager 1.95 blocks tall walks through (`QaDoorwayClearanceGameTests`).
- **Beds and residents.** None required (no code or test counts a resident here; the old one has no bed). A bed, if
  drawn, counts as a village bed and must pass the bed checks below.
- **Furniture spots.** No kind is required. Every piece drawn is recorded as a spot (`rebuild.py`) and must pass
  `FurnitureSpotGameTests`, `FloatingFurnishingsGameTests` and `QaChairFacingGameTests`.
- **Built whole, nothing skipped**: every block placeable by a builder from the materials list and standing as drawn
  when done (`plan.unfinished` empty, `site.skipped() == 0`). A block that needs a support placed after it, or that the
  running water would wash out, fails this.
- **Nothing rests on a Create block.** Without Create the Create blocks are air (below), so a block that only stands
  on, hangs from or leans on the wheel, a shaft, the gearbox or the millstone falls and the build wants a repair.

## Fits
- **Plot: at most 17 wide x 17 deep x 16 tall** (keeps every test area as it is):
  - `QaCreateGoneGameTests` (main suite, no Create) builds it at helper (5, 2, 5) in the 22 x 18 x 22 `big_area`:
    17 wide, 17 deep and 16 tall is all that fits.
  - The compat build tests place it in the 30 x 20 x 30 `huge_area` from helper y = 2 (18 tall at most), at
    x = min(9, 29 - width), with the builder's bench at (2, 2, 2), the builder at (3, 2, 3) and the material barrels
    at x 1-6 (z 5, or z 0 in the flipped test); at 20 wide or less the build and its 2 blocks of landscaping stay
    clear of them.
  - Anything bigger means the ship step moves those tests (to `tall_area`, as the Brass Foundry's were) without
    dropping what they check.
- y = 0 sits on the clicked block; the front is the z = 0 side.
- **Build time:** within **30,000 ticks** with Create (both compat build tests' timeout, build delay 1, builders' help
  off: the build, the water and the millstone turning), and within **12,000 ticks** without Create
  (`QaCreateGoneGameTests`). The old drawing is 340 blocks.
- **Declared size:** `StarterBlueprints.WATERMILL` is `new Vec3i(11, 11, 9)` (width, height, depth); the tests place
  the build from it and the Table hands blueprints out with it, so the ship step sets it to the new size.
- **Front and way in:** the z = 0 side faces the player who places it; the way in is there.
- **Neighbours:** none fixed (no Steward rule, no village plan, no renewal places it). The showcase scene
  `create_builds` sets the Watermill, the Windmill and the Workshop side by side, each 3 blocks past the last one's
  width, facing one fixed camera; a much wider Watermill pushes the other two along (the ship step re-aims if needed).

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `CreateBuildsCompatTests.aBuilderBuildsTheWatermillAndItTurns` (compat suite, with Create): built unturned from
  barrels holding exactly its materials; when done every block as drawn, nothing skipped, and `minecraft:composter` at
  **(7, 1, 2)** (the first expectation: must be a job site), `create:water_wheel` at **(1, 1, 4)**, `create:gearbox` at
  **(4, 1, 4)**; the machine at **(4, 2, 4)** (the millstone, right over the gearbox) turns.
- `QaCreateBuildsTurnedCompatTests.aFlippedWatermillIsBuiltAndItTurns`: the same build **flipped left to right**
  (`Mirror.FRONT_BACK`) from exactly its materials: built whole, nothing skipped, the composter at (7, 1, 2) a job
  site, the machine at (4, 2, 4) turning. So the drive must still turn mirrored: the wheel must still meet its water.
- `QaCreateBuildsTurnedCompatTests.aWatermillUnderWayWhenTheSwitchGoesOffIsFinished`: `createBuilds` goes off once the
  builder is past the 10th step of the STRUCTURE stage; the build is still finished whole, the composter at (7, 1, 2) a
  job site and (4, 2, 4) turning. (Any building has more than 10 structure blocks.)
- In code, not a test: `JobScenes` `create_builds` places the Watermill's structure and waits for the machine at
  (4, 2, 4) to turn (`MachineWork.turning`); `tools/showcase/scenes.py` describes it ("both millstones").

Without Create (main suite; Create's blocks read as air):
- `QaCreateGoneGameTests.aCreateBuildStartedWithCreateIsFinishedWithoutIt`: a Watermill site started by id is finished
  from its materials (no Create item wanted) within 12,000 ticks, or let go; it never stands for good.
- `QaCreateGoneUpkeepGameTests.aCreateBuildStandingWithoutCreateNeedsNoRepair`: the build placed without Create at
  helper (2, 2, 2) must need **no repair**, as placed and after 30 ticks of its water running. So with the wheel and
  every Create block gone to air, the loose water must not wash out or break any drawn block (nothing water breaks,
  such as a torch, a carpet, a button, a rail or a plant, in the path the water could take), and nothing drawn may need
  a Create block to stand.

By id only (the drawing doesn't matter beyond the rules above): `CreateBuildsGameTests.hiddenWithoutCreate`,
`theSwitchHoldsThemBack`; `CreateBuildsCompatTests.theCreateBuildsAreInTheTable`; `QaTableCreate1117eCompatTests`
(materials list real items, at least one of Create's; a preview with at least one Create block; every style live;
`getBlueprintHandsOutEveryCreateBuild`); `QaCreateSwitchesOff1102CompatTests.createBuildsOffTakesThemOffTheTableButTheyStillLoadById`;
`QaContraptionLines1104eCompatTests` (the turn-check lines, read with the id `watermill` and the name "Watermill");
`QaCopperCard1117eCompatTests` (the first Create build: its materials include at least one Create item, which the
builder's card lists as "Needs ..."; its finished toast reads "Watermill").

Library-wide checks it must pass (they read every listed build, Create's blocks as air in the main suite):
`QaBuildReachGameTests`, `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`, `QaLedgerFramesHaulGameTests`,
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `B648GameTests`, `QaWindowsAndWards1028BugTests`,
`QaBuilders1028GameTests` (no pane or bar joined on one side with air on the other), `BlueprintsStayGameTests`
(lamps, paths, bed foot and head), `QaFurnishedRooms1031GameTests` (no standing lantern on a room's floor; every
indoor chest against a wall, a solid block, a bed or another chest), `FurnitureSpotGameTests`,
`FloatingFurnishingsGameTests`, `StyleGameTests.chimneysStayWholeInEveryStyle` (only if a campfire stands on a stack).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair; `open_panes`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no
CHECK line, and `holes.py` passes. The ship step removes `watermill` from `owner_rules_baseline.json` (2 today), keeps
the old drawing in `blueprint_history/` (`history.py`), regenerates through `generate.py` (`create_builds.py`'s
`CREATE_BUILDS`), and updates the declared size, the wiki (`docs/wiki/create.md`, `docs/guide/create-builds.md`) and
the scenes.

## Free
Everything not listed above: shape, plan, roof, materials, colors, storeys, a yard, a grain store or none, where the
wheel, the water and the drive run (within the pinned spots, which the ship step may move), more shafts or gears as
long as all turn, a bed or none, more chests, lighting, and what the mill shows from the street.
