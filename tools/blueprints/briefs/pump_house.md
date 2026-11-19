# Pump House: the brief

Family id(s): `pump_house` (one level, never renamed; "Pump House" in `en_us.json`, `blueprint.aliveworkplace.pump_house`;
hidden advancement `built/pump_house`)
Variants: village (none), modded (none: Create-only builds are not in `modded_library.py`), looks (none). The Table's
style buttons (`BlueprintStyles`) restyle it automatically;
`QaTableCreate1117eCompatTests.everyCreateBuildHasMaterialsAPreviewAndLiveStyles` only asks that every listed style has
materials and a preview (automatic).

ROADMAP 50.13 (with the Train Station, the Village Train and the Terminal Nook). One version (not a flagship).

## What it is for
One of the nine **Create-powered builds** (ROADMAP 39.8, `StarterBlueprints.MORE_CREATE_BUILDS`, the last of the six;
the owner's words: "a mechanical pump, pipes and a fluid tank"): a working Create machine and a workplace. It is at the
Blueprint Table **only with Create installed and `createBuilds` on** (`CreateBuilds.listed`). Without them it still
loads by id. A builder builds it from his chests. Create's drive parts go in last, by group (shafts and gearboxes, then
cogwheels, then belts: `kinetic_parts/create.json`, `Contraptions`). Still water is poured from a bucket at the end
(`MaterialRules`: still water is DECORATION). Flowing water comes by itself and is never placed. The moment the builder
is done, the machine must turn **by itself**: a **mechanical pump** driven by the drive, between **fluid pipes**, with a
**fluid tank** two blocks tall. About 100 ticks after it is finished the builder checks every kinetic part and tells the
owner which "don't turn" (`Contraptions.CHECK_AFTER`, `message.aliveworkplace.contraption_stuck`).

The pump has **no worker**: no `machine_work/*.json` names a pump or a tank (only the millstone, the press, the mixer and
the crushing wheels have one), and nothing in the code reads the tank's fluid. The pump is the build's working show
piece. The player guide (`docs/guide/create-builds.md`) and the wiki (`docs/wiki/create.md`) tell the player it draws
water from a pool that fills itself up again and pumps it into the tank. The design should make that true, or the ship
step rewrites those two pages to match. The workplace is the **Leatherworker's**: the job block is a **cauldron**
(vanilla's Leatherworker job site). He makes item frames, leads, leather and leather armor for builders and the
Storehouse (`craft/CrafterWork`, wiki `candles-leather.md`), taking the makings from and delivering to the chests by his
cauldron.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `pump_house` | A drive that starts itself and turns a mechanical pump, with fluid pipes on the pump and a two-high fluid tank; a cauldron job block and its chest | At the Table with Create and `createBuilds` (expansion M39); `StarterBlueprints.expansionSwitch` is `createBuilds` |

**One level only.** A `pump_house_2` would be a new feature (a `StarterBlueprints` entry, upgrade offers).

## Must have
- **Job block: one empty `minecraft:cauldron`**, a job site once built. Both tests assert the block id
  `minecraft:cauldron` at the pinned spot, so a water, lava or powder snow cauldron is a different id and fails, and they
  assert that `getPoiManager().getType(...)` is present. It must be the **first** pinned block. No other job block is
  needed. Every other vanilla or mod job block brings in a villager of its own job. **A barrel is a Fisherman's job
  site** (the old drawing has four), so prefer chests for storage. A barrel also counts as storage, see below.
- **The machine (Create blocks, by id; checked in `libs/create-fabric-6.0.10.0+mc1.21.1.jar`):**
  - **one `create:mechanical_pump`, turning** (its saved `Speed` is not 0). This is the "machine" both tests read. A pump
    takes its drive along its own axle (its `facing` axis), from a shaft end or as a small cog meshing with a cogwheel
    beside it on the same axis. The old drawing meshes it with a cogwheel.
  - **one `create:cogwheel` that turns**, pinned by both tests (the turned test reads its `Speed`). The pinned cogwheel
    sits beside the pump and drives it. Whatever the new drive is, the drawing keeps a cogwheel in it and the ship step
    pins that one.
  - **two `create:fluid_pipe`**, pinned one on each side of the pump (intake and outlet), plus any more the design wants.
  - **a `create:fluid_tank` two blocks tall** (two tank blocks, one on the other; both pinned). Create joins touching
    tank blocks into one tank (`MaterialRules.JOINED`), so a tank block drawn against another tank block becomes part of
    the same tank.
  - **No open pipe end that pushes fluid out into the world.** A pump pushing out of an open pipe end places water or
    fluid blocks in the world. Those blocks change drawn cells or wash things out, and the build then fails "every block as
    drawn" (`plan.unfinished`). Every outlet ends in the tank (or another fluid container). The intake may end open over
    water.
  - **Water the pump draws from must still be there when the build is checked.** Each drawn water source the pump takes
    from must stand as drawn when the test looks: a pool that refills itself (the old drawing has four sources in a 2 x 2,
    so it refills), never a lone source the pump drains to air. The water is poured from a bucket as **source blocks**,
    reachable to pour.
  - **A drive that starts itself** when the last block goes in. Only the Windmill gets a push from the builder
    (`CreateBuilds.finished` acts on the Windmill's id alone). So the drive must be something like a water wheel turned by
    water that the builder pours as a **source block**, reachable to pour from a bucket. Falling and running water must
    come by itself from that source; the builder never places flowing water.
  - **Every kinetic part turns.** That means every shaft, gearbox, cogwheel and belt in the drawing (what `Contraptions`
    checks), so the owner never reads "N parts don't turn". No decorative, unconnected shaft or cogwheel. A belt, if any,
    must be whole: every segment names a controller segment of its own belt, numbered 0 to its length - 1 once each, also
    when the build is turned (`assertBeltsWhole`).
  - **Enough stress capacity** for everything on the network. An over-stressed network stops (Speed 0) and fails both
    tests.
  - Create block states that matter (`create_builds.py`'s header, checked in the jar):
    - a water wheel's `facing` is its axle's direction;
    - a shaft's, a gearbox's and a cogwheel's `axis` is their own;
    - a mechanical pump's `facing` is its axle and the way it pushes;
    - a fluid pipe's six sides say where it connects (`down: true` over the intake water in the old drawing);
    - a fluid tank's `top`, `bottom` and `shape`.
    Draw them as Create would settle them. `MaterialRules.matches` ignores properties that depend on neighbours.
- **Storage.** At least **one chest within 8 blocks across and 4 up or down of the cauldron** (`SupplyContainers.RADIUS`
  and `VERTICAL`; `CrafterWork` finds the Leatherworker's chests round his job block). This is where he fetches makings
  from and delivers to. No pump-house test pins it, but without one his crafting has nowhere to put what he makes. The
  pump, the pipes, the tank and the wheel never count as storage. Draw it empty (builders never copy containers).
- **Walking.** The cauldron and every chest, barrel or bed must be reachable on foot from outside: from a walked spot
  within 2 blocks across and 1 up or down (`QaBuildReachGameTests`). That test runs without Create, so Create's blocks are
  air there. You also need a way in on the z = 0 side that a villager 1.95 blocks tall walks through
  (`QaDoorwayClearanceGameTests`). Anything upstairs is reached by stairs or a ladder from inside.
- **Beds and residents.** None required: no code or test counts a resident here, and the old one has no bed. A bed, if
  drawn, counts as a village bed and must pass the bed checks below.
- **Furniture spots.** No kind is required (there is no `blueprint_furniture/pump_house.json`). Every piece drawn is
  recorded as a spot (`rebuild.py`) and must pass `FurnitureSpotGameTests`, `FloatingFurnishingsGameTests` and
  `QaChairFacingGameTests`.
- **Built whole, nothing skipped.** Every block can be placed by a builder from the materials list and stands as drawn
  when done (`plan.unfinished` empty, `site.skipped() == 0`). A block fails this if it needs a support placed after it,
  if the running water would wash it out, or if pumped water would change it.

## Fits
- **Plot: at most 20 wide x 20 deep x 18 tall.** y = 0 sits on the clicked block; the front is the z = 0 side.
  - Width: `CreateBuildsCompatTests` sets the origin at helper x = min(9, 29 - width) in the 30 x 20 x 30 compat
    `huge_area`. The builder's bench is at (2, 2, 2), the builder at (3, 2, 3) and the material barrels run from x = 1 to 6
    at z = 5. At 20 wide or less, the build and its 2 blocks of landscaping stay clear of them.
  - Depth: `QaCreateBuildsTurnedCompatTests` builds it **turned a quarter** (`Rotation.COUNTERCLOCKWISE_90`), so its depth
    lies along x there: corner x = min(9, 29 - depth), with barrels at x = 1 to 6, z = 0. More than 20 deep crowds the
    barrels. In the showcase scene `create_builds_more` (`JobScenes`) it stands in the back row with the Greenhouse and
    the Clock Tower, 16 blocks behind the front row. Nothing stands behind it, but a much wider build pushes the row out of
    the fixed camera's frame.
  - Height: built from helper y = 2 in the 20-tall compat `huge_area`, so 18 at most (blocks above an area's height are
    never cleared). Taller means the ship step moves both tests to `tall_area`, as the Brass Foundry's and the Clock
    Tower's were. `QaCreateGoneUpkeepGameTests` places it at (2, 2, 2) in the 30-tall `tall_area`, so it fits.
- **Build time: within 30,000 ticks** (both tests' timeout; day time 2,000, build delay 1, builders' help off). That
  covers the build, the last water and the pump turning. Builders work only until day time 11,000
  (`ModVillagers.BUILDER_SCHEDULE`), so the first day is about 9,000 ticks and the next starts near tick 23,000. The old
  drawing is 416 placed blocks (water included). Stay well under about 850.
- **Materials**: every block comes from the materials list (the tests stock barrels with exactly `plan.materials()`). A
  new material needs a plural in `en_us.json` (`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`).
- **Declared size:** `StarterBlueprints.PUMP_HOUSE` is `new Vec3i(14, 10, 9)`. The tests place the build from it, the
  scene spaces its row by it, the turned test's belt check reads its outline from it, and the Table hands blueprints out
  with it. So the ship step sets it to the new size.
- **Front and way in:** the z = 0 side faces the player who places it, and the way in is there.
- **Neighbours:** none fixed (no Steward rule, no village plan, no renewal places it). In the scene, its row neighbours
  stand 3 blocks to each side.

## Rules tests check
Pinned coordinates are template coordinates. The ship step moves them to the new drawing, keeping what each one checks
and dropping none.
- `CreateBuildsCompatTests.aBuilderBuildsThePumpHouseAndItTurns` (compat suite, with Create): built from barrels holding
  exactly its materials. When done, every block is as drawn and nothing is skipped. These blocks stand at their spots:
  - `minecraft:cauldron` at **(10, 1, 2)**, the first expectation, which must be a job site;
  - `create:mechanical_pump` at **(6, 1, 4)**;
  - `create:fluid_pipe` at **(5, 1, 4)** and **(7, 1, 4)**;
  - `create:fluid_tank` at **(8, 1, 4)** and **(8, 2, 4)**;
  - `create:cogwheel` at **(6, 1, 5)**.

  The pump at (6, 1, 4) turns (Speed not 0).
- `QaCreateBuildsTurnedCompatTests.aPumpHouseTurnedTheOtherWayStillPumps`: the same build **turned a quarter**
  (`Rotation.COUNTERCLOCKWISE_90`) from exactly its materials. It must be built whole with nothing skipped, the cauldron at
  (10, 1, 2) a job site, the pump (6, 1, 4) **and** the cogwheel (6, 1, 5) both turning, and every belt whole. So the
  drive must still turn when turned: the wheel still meets its water and the gears still mesh.
- In code, not a test: `JobScenes` `create_builds_more` places the structure as drawn (not built: `placeInWorld`, water
  included) and waits for the pump at (6, 1, 4) to turn (`MachineWork.turning`, 0.01).

By id only (the drawing doesn't matter beyond the rules above):
- `CreateBuildsGameTests.hiddenWithoutCreate` (names `PUMP_HOUSE`) and `theSwitchHoldsThemBack`;
- `CreateBuildsCompatTests.theCreateBuildsAreInTheTable`;
- `QaTableCreate1117eCompatTests`: materials list real items, at least one of them Create's; a preview with at least one
  Create block; every style live; `getBlueprintHandsOutEveryCreateBuild`;
- `QaCreateSwitchesOff1102CompatTests.createBuildsOffTakesThemOffTheTableButTheyStillLoadById`.

**Without Create**: `QaCreateGoneUpkeepGameTests.aCreateBuildStandingWithoutCreateNeedsNoRepair`. The blueprint must load
by id with every Create block read as air. The test places the build at helper (2, 2, 2) and it must need **no repair**,
both as placed and after 30 ticks of its water running. With the wheel, pump, pipes and tank gone, the loose water (the
drive's and the pool's) runs into their cells. It must not wash out or break any drawn block in the path it could take:
nothing water breaks, such as a torch, a carpet, a button, a rail or a plant. Otherwise the build is plain blocks with
air where Create's were. Nothing else changes for a pack without Create: it isn't at the Table.

Library-wide checks it must pass (they read every listed build, with Create's blocks as air in the main suite):
- `QaBuildReachGameTests`, `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`, `QaLedgerFramesHaulGameTests`;
- `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `B648GameTests`, `QaWindowsAndWards1028BugTests`;
- `QaBuilders1028GameTests`: no pane or bar joined on one side with air on the other;
- `BlueprintsStayGameTests`: lamps, paths, bed foot and head;
- `QaFurnishedRooms1031GameTests`: no standing lantern on a room's floor; every indoor chest against a wall, a solid
  block, a bed or another chest;
- `FurnitureSpotGameTests`, `FloatingFurnishingsGameTests`;
- `StyleGameTests.chimneysStayWholeInEveryStyle`, only if a campfire stands on a stack.

The owner's rules (`check.py`: posts at fence and wall corners; lamps on tables or posts, or hung; crockery only by a
stove or on a table with a chair; `open_panes`), STYLE.md's checks and `holes.py` all apply: `rebuild.py --candidate`
prints no CHECK line, and `holes.py` passes.

The ship step:
- removes `pump_house` from `owner_rules_baseline.json` (2 today);
- keeps the old drawing in `blueprint_history/` (`history.py`);
- regenerates through `generate.py` (`MORE_CREATE_BUILDS`);
- updates `create_builds.py`'s entry, the `StarterBlueprints` size and comment, the wiki (`create.md`) and the guide
  (`docs/guide/create-builds.md`), and the scene's pinned pump spot.

## Free
Everything not listed above is the designer's choice:
- shape, plan, roof, materials, colors and storeys;
- what drives the pump, as long as it starts itself;
- where the water, the gear train, the pipes, the pool and the tank run, within the pinned kinds (the ship step moves
  the spots);
- more pipes or tanks, more chests, a bed or none;
- lighting, and what the pump house shows from the street.
