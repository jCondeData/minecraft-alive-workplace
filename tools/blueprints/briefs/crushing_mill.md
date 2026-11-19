# Crushing Mill: the brief

Family id(s): `crushing_mill` (one level, never renamed; "Crushing Mill" in `en_us.json`,
`blueprint.aliveworkplace.crushing_mill`; hidden advancement `built/crushing_mill`)
Variants: village (none), modded (none: Create-only builds are not in `modded_library.py`), looks (none). The
Table's style buttons (`BlueprintStyles`) restyle it automatically;
`QaTableCreate1117eCompatTests.everyCreateBuildHasMaterialsAPreviewAndLiveStyles` only asks that every listed style has
materials and a preview (automatic).

ROADMAP 50.10 (with the Quarry, the Sifting Shed, the Gem Grotto and the Windmill). One version (not a flagship).

## What it is for
One of the nine **Create-powered builds** (ROADMAP 39.8, `StarterBlueprints.MORE_CREATE_BUILDS`; the owner's words:
"crushing wheels fed by a belt, with a drain"): a working Create machine and a workplace. It is at the Blueprint Table
**only with Create installed and `createBuilds` on** (`CreateBuilds.listed`) and still loads by id without them. A
builder builds it from his chests; Create's drive parts go in last, by group (shafts and gearboxes, then cogwheels,
then belts: `kinetic_parts/create.json`, `Contraptions`), and still water is poured from a bucket at the end
(`MaterialRules`: still water is DECORATION; flowing water comes by itself and is never placed). The moment the
builder is done, the machine must turn **by itself**: a pair of **crushing wheels** turning towards each other, so
Create's crusher appears in the gap between them. About 100 ticks after it is finished the builder checks every
kinetic part and tells the owner which "don't turn" (`Contraptions.CHECK_AFTER`,
`message.aliveworkplace.contraption_stuck`).

The workplace is the **Miner's crusher** (39.3, `machine_work/crushing.json`): a Miner whose job block is within 32
blocks of the crusher (`MachineWork.REACH`) takes raw iron, copper or gold from the chest by his job block, walks to
the crusher, **drops** it into the gap (`feed: at [0,0,0], drop`), and later **picks up the crushed ore lying in the
block straight under the gap** (`collect: at [0,-1,0], drop`: item entities within that cell, 0.25 slack) and carries
it back to the chest, until the chest holds 64 of each crushed ore (batch 16, `min_speed` 0.02). Only the village's own
machine and chests are used (B243, `VillageMachines`): both must stand inside the finished building's outline. The job
block is a **blast furnace**: the Miner takes it (`ModVillagers.MINER` accepts the Miner's Bench or the Armorer POI),
and so does a vanilla Armorer, our Smelter (`Smelters`), who smelts at it with the same chest.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `crushing_mill` | A self-starting drive turning two crushing wheels the opposite ways at one speed (Create's crusher valid in the gap), a belt, an item drain, a pit under the gap; a blast furnace job block and its chest | At the Table with Create and `createBuilds` (expansion M39); `StarterBlueprints.expansionSwitch` is `createBuilds` |

**One level only.** A `crushing_mill_2` would be a new feature (a `StarterBlueprints` entry, upgrade offers).

## Must have
- **Job block: one `minecraft:blast_furnace`**, a job site once built (both tests below assert the block id at the
  pinned spot and that `getPoiManager().getType(...)` is present). Within 32 blocks of the crusher (any spot in a
  16-wide plot is). No other job block is needed; every other vanilla or mod job block draws a villager of its own job,
  and **a barrel is a Fisherman's job site** (the old drawing has three), so prefer none (a barrel also counts as
  storage, see below).
- **The machine (Create blocks, by id; checked in `libs/create-fabric-6.0.10.0+mc1.21.1.jar`):**
  - **two `create:crushing_wheel`** one empty cell apart on a line square to their shared axle, both turning, **the
    opposite ways at exactly the same speed** (`a == -b` on their saved `Speed`), each turning *into* the gap, so the
    crusher between them is `valid=true`.
  - **The gap cell is left out of the drawing: a structure void**, never air and never a block. The wheels place
    `create:crushing_wheel_controller` there themselves; a drawn air cell there is a CLEAR step that the controller then
    fails (`BuildPlan.unfinished`), and the controller has no item, so it can't be drawn either.
  - **The cell straight under the gap is open** (drawn air) **with a floor under it**, so the crushed ore falls out of
    the crusher and lies there for the Miner to collect (`collect [0,-1,0]`). Not a depot, belt, chute, drain, hopper
    or any container there: Create would put the output into it and the Miner would find nothing. Keep water out of
    the gap and that cell (items would drift off).
  - **one `create:belt`** (a test pins a segment of it), whole: every segment names a controller segment of its own
    belt, numbered 0 to its length - 1 once each, also when the build is flipped (`assertBeltsWhole`). The owner's
    spec has the belt feeding the wheels (over the gap); no test checks where it carries to.
  - **one `create:item_drain`** (a test pins it; the owner's spec: "with a drain"). No code works it.
  - **a drive that starts itself** when the last block goes in. Only the Windmill gets a push from the builder
    (`CreateBuilds.finished` acts on the Windmill's id alone), so the drive must be a water wheel turned by water the
    builder pours as a **source block**, reachable to pour from a bucket (falling and running water must come by itself
    from that source; the builder never places flowing water).
  - **Every kinetic part turns**: every shaft, gearbox, cogwheel and belt in the drawing (what `Contraptions` checks),
    so the owner never reads "N parts don't turn". No decorative, unconnected shaft or cogwheel.
  - **Enough stress capacity** for everything on the network (crushing wheels are heavy users): an over-stressed
    network stops (Speed 0) and fails both tests.
  - Create block states that matter (`create_builds.py`'s header, checked in the jar): a water wheel's `facing` is its
    axle's direction; a shaft's, gearbox's, cogwheel's and crushing wheel's `axis` their own; a belt's `part` (`start`,
    `middle`, `end`), `facing`, `slope` and `casing`.
- **Storage.** At least **one chest within 8 blocks across and 4 up or down of the blast furnace**
  (`SupplyContainers.RADIUS`/`VERTICAL`) and **inside the build's outline** (`VillageMachines.stores`): the Miner's
  store, where the raw ore comes from and the crushed ore goes back to (and the Smelter's). With none, the crusher is
  never fed. The wheels, crusher, belt and drain never count as storage (`Kinetics.keepsOwnItems`). Drawn empty
  (builders never copy containers).
- **Walking.** The Miner must get, on foot, **within 3 blocks of the gap** (feeding and collecting both happen there,
  `Walker.reach(..., 3.0)`) and within 3 blocks of the chest; the blast furnace and every chest reachable on foot from
  outside within 2 across and 1 up or down (`QaBuildReachGameTests`; it runs without Create, so Create's blocks are air
  there). A way in on the z = 0 side that a villager 1.95 blocks tall walks through (`QaDoorwayClearanceGameTests`);
  anything upstairs reached by stairs or a ladder from inside.
- **Beds and residents.** None required (no code or test counts a resident here; the old one has no bed). A bed, if
  drawn, counts as a village bed and must pass the bed checks below.
- **Furniture spots.** No kind is required (there is no `blueprint_furniture/crushing_mill.json`). Every piece drawn is
  recorded as a spot (`rebuild.py`) and must pass `FurnitureSpotGameTests`, `FloatingFurnishingsGameTests` and
  `QaChairFacingGameTests`.
- **Built whole, nothing skipped**: every block placeable by a builder from the materials list and standing as drawn
  when done (`plan.unfinished` empty, `site.skipped() == 0`). A block that needs a support placed after it, or that the
  running water would wash out, fails this.

## Fits
- **Plot: at most 20 wide x 16 deep x 18 tall.** y = 0 sits on the clicked block; the front is the z = 0 side.
  - Width: the compat tests set the origin at helper x = min(9, 29 - width) in the 30 x 20 x 30 `huge_area`, with the
    builder's bench at (2, 2, 2), the builder at (3, 2, 3) and the material barrels from x = 1 to 6 (at z = 5, or z = 0
    in the flipped test); at 20 wide or less the build and its 2-block landscaping stay clear of them.
  - Depth: the showcase scene `create_builds_more` (`JobScenes`) sets the Crushing Mill in the front row (with the
    Brass Foundry and the Sawmill), 16 blocks before the second row (Greenhouse, Clock Tower, Pump House): deeper than
    16 overlaps it (or the ship step moves the row).
  - Height: built from helper y = 2 in the 20-tall `huge_area`: 18 at most (blocks above an area's height are never
    cleared). Taller means the ship step moves both tests to `tall_area`, as the Brass Foundry's were.
    `QaCreateGoneUpkeepGameTests` places it at (2, 2, 2) in the 30-tall `tall_area`: fits.
- **Build time: within 30,000 ticks** (both tests' timeout; day time 2,000, build delay 1, builders' help off): the
  build, the last water, and the wheels turning. Builders work only until day time 11,000
  (`ModVillagers.BUILDER_SCHEDULE`), so the first day is about 9,000 ticks and the next starts near tick 23,000. The
  old drawing is 517 placed blocks (water included); stay near that, well under 850.
- **Materials**: every block from the materials list (the tests stock barrels with exactly `plan.materials()`); a new
  material needs a plural in `en_us.json` (`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`).
- **Declared size:** `StarterBlueprints.CRUSHING_MILL` is `new Vec3i(14, 12, 11)`; the tests place the build from it,
  the scene spaces its row by it and the Table hands blueprints out with it, so the ship step sets it to the new size.
  The crusher and the chest must sit inside that box (`VillageMachines` reads the outline from it).
- **Front and way in:** the z = 0 side faces the player who places it; the way in is there.
- **Neighbours:** none fixed (no Steward rule, no village plan, no renewal places it). In the scene its row
  neighbours stand 3 blocks to each side.

## Rules tests check
Pinned coordinates (template coordinates; the ship step moves them to the new drawing, keeping what each checks,
dropping none):
- `CreateBuildsCompatTests.aBuilderBuildsTheCrushingMillAndItTurns` (compat suite, with Create): built from barrels
  holding exactly its materials; when done every block as drawn, nothing skipped, and `minecraft:blast_furnace` at
  **(9, 1, 2)** (the first expectation: must be a job site), `create:crushing_wheel` at **(5, 2, 5)** and **(7, 2, 5)**,
  `create:crushing_wheel_controller` at **(6, 2, 5)** (the gap), `create:item_drain` at **(6, 1, 3)**, `create:belt` at
  **(6, 3, 7)**; the wheel at (5, 2, 5) turns (Speed not 0); the two wheels' Speeds are exact opposites; the crusher's
  `valid` is `true`.
- `QaCreateBuildsTurnedCompatTests.aFlippedCrushingMillStillCrushes`: the same build **flipped left to right**
  (`Mirror.FRONT_BACK`) from exactly its materials: built whole, nothing skipped, the blast furnace at (9, 1, 2) a job
  site, both wheels turning, the opposite ways at one speed, the crusher valid, every belt whole. So the drive must
  still turn mirrored: the wheel still meets its water, gears still mesh, and both wheels still turn *into* the gap.
- In code, not a test: `JobScenes` `create_builds_more` places the structure as drawn (not built: `placeInWorld`,
  water included) and waits for the wheel at (5, 2, 5) to turn (`MachineWork.turning`).

By id only (the drawing doesn't matter beyond the rules above): `CreateBuildsGameTests.hiddenWithoutCreate`,
`theSwitchHoldsThemBack`; `CreateBuildsCompatTests.theCreateBuildsAreInTheTable`; `QaTableCreate1117eCompatTests`
(materials list real items, at least one of Create's; a preview with at least one Create block; every style live;
`getBlueprintHandsOutEveryCreateBuild`); `QaCreateSwitchesOff1102CompatTests.createBuildsOffTakesThemOffTheTableButTheyStillLoadById`.

- `QaCreateGoneUpkeepGameTests.aCreateBuildStandingWithoutCreateNeedsNoRepair`: the build placed without Create (its
  Create blocks gone to air) at helper (2, 2, 2) must need **no repair**, as placed and after 30 ticks of its water
  running. So with the wheel and every Create block gone, the loose water must not wash out or break any drawn block
  (nothing water breaks, such as a torch, a carpet, a button, a rail or a plant, in the path it could take).

Library-wide checks it must pass (they read every listed build, Create's blocks as air in the main suite):
`QaBuildReachGameTests`, `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`, `QaLedgerFramesHaulGameTests`,
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `B648GameTests`, `QaWindowsAndWards1028BugTests`,
`QaBuilders1028GameTests` (no pane or bar joined on one side with air on the other), `BlueprintsStayGameTests`
(lamps, paths, bed foot and head), `QaFurnishedRooms1031GameTests` (no standing lantern on a room's floor; every
indoor chest against a wall, a solid block, a bed or another chest), `FurnitureSpotGameTests`,
`FloatingFurnishingsGameTests`, `StyleGameTests.chimneysStayWholeInEveryStyle` (only if a campfire stands on a stack).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair; `open_panes`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no
CHECK line, and `holes.py` passes. The ship step removes `crushing_mill` from `owner_rules_baseline.json` (2 today),
keeps the old drawing in `blueprint_history/` (`history.py`), regenerates through `generate.py` (`MORE_CREATE_BUILDS`),
and updates `create_builds.py`'s entry, the `StarterBlueprints` comment, the wiki (`create.md`,
`docs/guide/create-builds.md`) and the scene.

## Free
Everything not listed above: shape, plan, roof, materials, colors, storeys, where the wheel, the water, the gear
train, the belt and the drain run (within the pinned spots, which the ship step may move), a bed or none, more chests,
lighting, and what the mill shows from the street.
