# Brass Foundry: the brief

Family id(s): `brass_foundry` (one level, never renamed; "Brass Foundry" in `en_us.json`,
`blueprint.aliveworkplace.brass_foundry`; hidden advancement `built/brass_foundry`)
Variants: village (none), modded (none: `modded_library.py` skips `CREATE_ONLY`), looks (none). The Table's style
buttons (`BlueprintStyles`) restyle it automatically; `QaTableCreate1117eCompatTests.everyCreateBuildHasMaterialsAPreviewAndLiveStyles`
only asks that every listed style has materials and a preview (automatic).

ROADMAP 50.3 (with the Smithy and the Tinker's Workshop). One version (not a flagship).

## What it is for
One of the nine **Create-powered builds** (ROADMAP 39.8, `StarterBlueprints.MORE_CREATE_BUILDS`, first in the Table's
order): a working Create machine and a workplace. It is at the Blueprint Table **only with Create installed and
`createBuilds` on** (`CreateBuilds.listed`), and still loads by id without them (a blueprint already handed out keeps
working). ROADMAP 39.8 names its machine: "a mixer and a basin over a blaze burner, belts out". A builder builds it from
his chests; Create's moving parts go in last, by group (shafts and gearboxes, then cogwheels, then belts,
`kinetic_parts/create.json`, `Contraptions`), and still water is poured from a bucket at the end (`MaterialRules`: still
water is DECORATION, flowing water comes by itself and is never placed). The moment the builder is done the machine must
turn **by itself**, the mixer fast enough to mix. About 100 ticks after it is finished the builder checks every kinetic
part and tells the owner which "don't turn" (`Contraptions.CHECK_AFTER`, `message.aliveworkplace.contraption_stuck`).

The workplace half is the **Chef's**: his job block here is a smoker (`ModVillagers.CHEF` takes a Kitchen Stove or the
Butcher's POI, which is the smoker; a vanilla Butcher also claims a smoker). The Chef cooks the village's menu from the
chests by his workstation (`ChefWork`), and with Create his machine work (39.3, `machine_work/mixer.json`) is this
mixer: while it turns at **30 RPM or more** he takes ingredients from the store by his workstation, pours 1,000 mB of
water into the **basin two blocks under the mixer**, lets the mixer make dough (Create's `create:mixing` recipes, keep
32) and carries what the basin holds back to the store (`MachineWork`). Only the village's own machine and chests count:
both must stand **inside the finished building's outline** (`VillageMachines.village`, `stores`). The Mechanic (39.2)
puts broken drive parts back from the blueprint.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `brass_foundry` | A self-starting drive geared up to turn a mechanical mixer at 30+ RPM over a basin and a blaze burner; a belt out to a depot; the Chef's smoker and his chest | At the Table with Create and `createBuilds` (expansion M39) |

**One level only.** A `brass_foundry_2` would be a new feature (a `StarterBlueprints` entry, upgrade offers).

## Must have
- **Job block: one `minecraft:smoker`**, a job site once built (both compat tests assert the block id at the pinned
  spot and that `getPoiManager().getType(...)` is present). No other job block is tested, but every vanilla or mod job
  block takes a villager for its own job: the old drawing's **three barrels are Fisherman's job sites**, so prefer none
  (a barrel also counts as storage, see below). A blast furnace, smithing table, grindstone, composter, cauldron,
  lectern and the like each pull in another trade.
- **The machine (Create blocks, by id; checked in `libs/create-fabric-6.0.10.0+mc1.21.1.jar`):**
  - one `create:mechanical_mixer` turning at **at least 30 RPM** (`Speed` read from block entity data by name; both
    tests assert `|Speed| >= 30`, and `mixer.json`'s `min_speed` is 30: below it the Chef feeds it nothing). A water
    wheel turns at 8 RPM, so the drive must be geared up (large cogwheel to small doubles the speed);
  - one `create:basin` **exactly two blocks under the mixer**, the block between them empty (the mixer's head works
    there; `mixer.json` feeds and collects at `[0, -2, 0]`). The basin's output `facing` is `down`, so it keeps what it
    makes for the Chef to collect (a basin facing sideways pushes its result out where nobody collects it);
  - one `create:blaze_burner` (the filled burner, not `create:empty_blaze_burner`) directly under the basin (pinned by
    the test; ROADMAP 39.8's "a basin over a blaze burner");
  - a `create:belt` (at least one segment at the pinned spot below) running out to a `create:depot` ("belts out"),
    whole: every segment names a controller segment of its own belt, numbered 0 to its length - 1 once each
    (`assertBeltsWhole`), and the belt turns;
  - a drive that **starts itself** when the last block goes in. Only the Windmill gets a push from the builder
    (`CreateBuilds.finished` acts on the Windmill's id alone), so the drive must be a water wheel turned by water the
    builder pours as a **source block** (falling and running water must come by itself from that source; the builder
    never places flowing water). The source must be reachable to pour from a bucket.
  - **Every kinetic part turns**: every shaft, gearbox, cogwheel and belt in the drawing (the parts `Contraptions`
    checks), so the owner never reads "N parts don't turn". No decorative, unconnected shaft or cogwheel.
  - **Enough stress capacity** for everything on the network at its speed: an over-stressed Create network stops
    (Speed 0) and fails both tests. Stress grows with RPM, so the geared-up mixer is the heavy load.
  - Create block states that matter (from `create_builds.py`'s header, checked in the jar): a water wheel's `facing`
    is its axle's direction; a shaft's, gearbox's and cogwheel's `axis` their own; a large and a small cogwheel mesh
    only diagonally adjacent on parallel axes; a belt's `part` (`start`, `middle`, `end`), `facing`, `slope` and
    `casing`, its pulleys turning about the axis across its `facing`.
- **Storage.** At least **one chest within 8 blocks across and 4 up or down of the smoker** (`SupplyContainers.RADIUS` /
  `VERTICAL`) **and inside the building's outline** (`VillageMachines.stores`): the Chef's store for his menu and for
  the mixer's ingredients and dough. With none, machine work has no store and drops its load at his feet. The depot,
  belt, basin, burner and mixer never count as storage (`Kinetics.keepsOwnItems`). Drawn empty (builders never copy
  containers). Every chest reachable on foot (`QaBuildReachGameTests`) and an indoor chest stands against a wall, a
  solid block, a bed or another chest (`QaFurnishedRooms1031GameTests`).
- **The Chef can walk to his machine and store**: `MachineWork` walks him to within 3 blocks of the store and of the
  mixer (`Walker.reach(..., 3.0)`), from the smoker's side of the building. No test checks this (it falls back when
  there's no spot), but a mixer he can't get near is fed from wherever he gives up.
- **Beds and residents.** None required (no code or test counts a resident here; the old one has no bed). A bed, if
  drawn, counts as a village bed and must pass the bed checks below.
- **Furniture spots.** No kind is required (no `blueprint_furniture/brass_foundry.json` today; not a home). Every piece
  drawn is recorded as a spot (`rebuild.py`) and must pass `FurnitureSpotGameTests`, `FloatingFurnishingsGameTests` and
  `QaChairFacingGameTests`.
- **A way in on the z = 0 side** that a villager 1.95 blocks tall walks through (`QaDoorwayClearanceGameTests`), the
  smoker and every chest reachable on foot from outside within 2 across and 1 up or down
  (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`; it runs without Create, so Create's
  blocks are air there); anything upstairs reached by stairs or a ladder from inside.
- **No entity** and no jigsaw or structure void in the file.
- **Built whole, nothing skipped**: every block must be placeable by a builder from the materials list and stand as
  drawn when done (`plan.unfinished` empty, `site.skipped() == 0`). A block that needs a support placed after it, or
  that the running water would wash out, fails this.

## Fits
- **Plot: at most 20 wide x 16 deep x 27 tall.** Width: the compat tests set the origin at helper
  x = min(9, 29 - width) in the 30-wide `huge_area`, with the builder's bench at (2, 2, 2), the builder at (3, 2, 3)
  and the material barrels from x = 1 to 6; at 20 wide or less the build and its 2-block landscaping stay clear of them.
  **Depth counts as width too**: the quarter-turned test lays the template's depth along x (origin x =
  min(9, 29 - depth)). Depth: the showcase scene `create_builds_more` (`JobScenes`) sets the Foundry in the front row
  16 blocks before the second row (Greenhouse, Clock Tower, Pump House), so deeper than 16 overlaps it (or the ship
  step moves the row). Height: built from helper y = 2 in a 30-tall area; `QaCreateGoneUpkeepGameTests` places it at
  (2, 2, 2) and clears y 2-28 round it.
- y = 0 sits on the clicked block; the front is the z = 0 side.
- **Build time: within 30,000 ticks** (both tests' timeout, build delay 1, builders' help off): the build, the last
  water and the mixer up to speed. The old drawing is 561 blocks.
- **Declared size:** `StarterBlueprints.BRASS_FOUNDRY` is `new Vec3i(13, 14, 11)`; the tests place the build from it,
  the scene spaces its row by it and the Table hands blueprints out with it, so the ship step sets it to the new size.
- **Front and way in:** the z = 0 side faces the player who places it; the way in is there.
- **Neighbours:** none fixed (no Steward rule, no village plan, no renewal, not in the starting set or on the Village
  Map's named list).

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `CreateBuildsCompatTests.aBuilderBuildsTheBrassFoundryAndItTurns` (compat suite, with Create): built unturned from
  barrels holding exactly its materials; when done every block as drawn, nothing skipped, and `minecraft:smoker` at
  **(9, 1, 2)** (the first expectation: must be a job site), `create:mechanical_mixer` at **(8, 4, 8)**,
  `create:basin` at **(8, 2, 8)**, `create:blaze_burner` at **(8, 1, 8)**, `create:depot` at **(4, 1, 2)**,
  `create:belt` at **(4, 1, 4)**; the mixer at (8, 4, 8) turns (the "machine") at **30 RPM or more**.
- `QaCreateBuildsTurnedCompatTests.aBrassFoundryTurnedAQuarterStillMixes`: the same build **turned a quarter**
  (`Rotation.CLOCKWISE_90`) from exactly its materials: built whole, nothing skipped, the smoker at (9, 1, 2) a job
  site, the mixer at (8, 4, 8) and the belt at (4, 1, 4) turning, the mixer at 30 RPM or more, every belt whole.
  So the wheel must still meet its water and the gears still mesh turned.
- Not tested, but a player can flip any build at the Table (`Mirror.FRONT_BACK`, as the Sawmill's test does): it should
  still turn and mix flipped (no gear or wheel that only meshes one way round).
- In code, not a test: `JobScenes` `create_builds_more` reads the machine at (8, 4, 8) (it must turn for the scene to
  pass, `MachineWork.turning`); `mixer.json` reads the basin at the mixer's (0, -2, 0).

By id only (the drawing doesn't matter beyond the rules above): `CreateBuildsGameTests.hiddenWithoutCreate`,
`theSwitchHoldsThemBack` (and the check that `CREATE_ONLY` holds the six 39.8 builds); `CreateBuildsCompatTests.theCreateBuildsAreInTheTable`;
`QaTableCreate1117eCompatTests` (materials list real items, at least one of Create's; a preview with at least one
Create block; every style live; `getBlueprintHandsOutEveryCreateBuild`);
`QaCreateSwitchesOff1102CompatTests.createBuildsOffTakesThemOffTheTableButTheyStillLoadById`.

- `QaCreateGoneUpkeepGameTests.aCreateBuildStandingWithoutCreateNeedsNoRepair`: the build placed without Create (its
  Create blocks gone to air) at helper (2, 2, 2) must need **no repair**, as placed and after 30 ticks of its water
  running. So when the wheel and every Create block are gone, the loose water must not wash out or break any drawn
  block (nothing water breaks, such as a torch, a carpet, a button, a rail or a plant, in the path the water could
  take), and nothing drawn may rest on a Create block (the basin, burner or mixer gone to air leaves it floating).

Library-wide checks it must pass (they read every listed build, Create's blocks as air in the main suite):
`QaBuildReachGameTests`, `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`, `QaLedgerFramesHaulGameTests` /
`QaTurnedFramesGameTests` (frames and paintings hang turned and mirrored), `QaMirroredBuildsGameTests` (doors, beds and
double chests whole, turned and mirrored), `QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`,
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `B648GameTests`, `QaWindowsAndWards1028BugTests`,
`QaBuilders1028GameTests` (no pane or bar joined on one side with air on the other), `BlueprintsStayGameTests`
(lamps on a block that holds them, paths, bed foot and head), `QaFurnishedRooms1031GameTests` (no standing lantern on a
room's floor; every indoor chest against a wall, a solid block, a bed or another chest), `FurnitureSpotGameTests`,
`FloatingFurnishingsGameTests`, `StyleGameTests.chimneysStayWholeInEveryStyle` (only if a campfire stands on a stack:
it must stay solid in every style).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair; `open_panes`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no
CHECK line, and `holes.py` passes. The ship step removes `brass_foundry` from `owner_rules_baseline.json` (2 today),
keeps the old drawing in `blueprint_history/` (`history.py`; a site begun on the old drawing finishes as drawn),
regenerates through `generate.py` (`MORE_CREATE_BUILDS`), and updates `create_builds.py`'s entry, the wiki
(`docs/wiki/create.md`, `docs/guide/create-builds.md`), the scene and the pinned coordinates above.

## Free
Everything not listed above: shape, plan, roof, materials, colors, storeys, a chimney or none, where the wheel, the
water, the gear train, the belt and the depot run (within the pinned spots, which the ship step may move), how the
drive is geared up, a bed or none, more chests, anvils or tables, lighting, and what the foundry shows from the street.
