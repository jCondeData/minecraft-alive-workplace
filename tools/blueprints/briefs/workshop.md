# Workshop: the brief

Family id(s): `workshop` (one level, never renamed; "Workshop" in `en_us.json`, `blueprint.aliveworkplace.workshop`;
hidden advancement `built/workshop`)
Variants: village (none), modded (none: `modded_library.py` skips `CREATE_ONLY`; there is no `structure/modded/workshop.nbt`),
looks (none). The Table's style buttons (`BlueprintStyles`) restyle it automatically;
`QaTableCreate1117eCompatTests.everyCreateBuildHasMaterialsAPreviewAndLiveStyles` only asks that every listed style has
materials and a preview (automatic).

**Which "workshop" this is.** `aliveworkplace:workshop` is the **Create-powered Workshop** (`StarterBlueprints.WORKSHOP`,
ROADMAP 39.4, drawn today in `tools/blueprints/create_builds.py`'s `workshop()`), one of `FIRST_CREATE_BUILDS` with the
Watermill and the Windmill. It is **not** the Builder's Workshop (`builders_workshop`), the village houses grown by
`VillageHouses.workshop(style)`, the questbook's "workshop" step (that one is `builders_workshop`), or the crafting
kind `Crafting.Kind.WORKSHOP`. ROADMAP 50.7 (with the Glassblower's, Jeweler's and Crafter's Workshops). One version
(not a flagship).

## What it is for
One of the **Create-powered builds**: a working Create machine and a workplace. It is at the Blueprint Table **only with
Create installed and `createBuilds` on** (`CreateBuilds.listed`), unlocked from the start, and still loads by id
without them (a blueprint already handed out keeps working). A builder builds it from his chests; Create's moving parts
go in last, by group (shafts and gearboxes, then cogwheels, then belts: `kinetic_parts/create.json`, `Contraptions`),
and still water is poured from a bucket at the end (`MaterialRules`: still water is DECORATION; flowing water comes by
itself and is never placed). The moment the builder is done the machine must turn **by itself**: a drive turning a
**belt** and a **mechanical press over a depot** (ROADMAP 39.4: "a water wheel driving a belt to a depot under a
mechanical press"). About 100 ticks after it is finished the builder checks every part with a `Speed` and tells the
owner which "don't turn" (`Contraptions.CHECK_AFTER`, `message.aliveworkplace.contraption_stuck`).

The workplace half is the **Armorer's**: his job block is the vanilla **blast furnace**, and his machine work
(`machine_work/press.json`, `mechanic/MachineWork`) is the press: in his work hours he takes iron and copper ingots
from the store by his blast furnace, puts them on the depot under the press, and brings the iron and copper sheets back
to that store (up to 64 of each kept). The Mechanic (39.2) mends the build's drive parts from the blueprint, the belt
included (B669: the whole belt laid again between its two pulley shafts for one belt connector).

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `workshop` | A self-starting drive turning a belt and a mechanical press over a depot; the Armorer's blast furnace and the store by it | Unlocked from the start, at the Table only with Create and `createBuilds` (`StarterBlueprints.expansionSwitch` puts `CREATE_ONLY` under `createBuilds`) |

**One level only.** A `workshop_2` would be a new feature (a `StarterBlueprints` entry, upgrade offers).

## Must have
- **Job block: one `minecraft:blast_furnace`**, a job site once built. Every test below asserts the block id at its
  pinned spot and that `getPoiManager().getType(...)` is present there (it is the first expectation in
  `CreateBuildsCompatTests.build`). It is the Armorer's workstation, and the press's machine-work file names the
  Armorer (`"job": "minecraft:armorer"`).
- **Other job blocks** (code, not a test): every vanilla or mod job block takes a villager for its own trade (the
  Village Hall lists it as a free workstation and `StewardJobs` hands it out). The old drawing has a **smithing table**
  (a Toolsmith's and Tinkerer's job site) besides the blast furnace; no code or test of this build needs it. Prefer no
  job block but the blast furnace: no barrel (Fisherman), composter, lectern, loom, stonecutter, grindstone, cauldron,
  brewing stand, smoker, fletching, smithing or cartography table, crafting table (a Carpenter's, `B635GameTests`), bell,
  or Create Toolbox (the Mechanic's). A chest, furnace or anvil is not a job block.
- **The machine line (Create blocks, by id; checked in `libs/create-fabric-6.0.10.0+mc1.21.1.jar`):**
  - one `create:mechanical_press` with one `create:depot` **exactly two blocks below it** (same x and z):
    `machine_work/press.json` feeds and collects at `[0, -2, 0]` from the press, and only from a `create:depot`.
  - at least one `create:belt`, **whole**: every segment names a controller segment of its own belt, numbered 0 to its
    length - 1 once each (`QaCreateBuildsTurnedCompatTests.assertBeltsWhole`; it fails "the Workshop has no belt"
    without one, and `QaMechanicFlippedBelt1103eCompatTests` fails "has no belt"). The belt must be one Create's own
    belt connector can lay again between its two pulley shafts (a straight run, within Create's length limit, nothing
    in the way), because the Mechanic mends it that way (B669, `compat/create/CreateBelts`).
  - the press **and** the belt segment at the pinned spot turn (`Speed` read from block entity data by name, not 0;
    `press.json`'s `min_speed` is 1).
  - a drive that **starts itself** when the last block goes in. Only the Windmill gets a push from the builder
    (`CreateBuilds.finished` acts on the Windmill's id alone), so the Workshop's drive must be turned by water the
    builder pours as a **source block** (falling and running water must come by itself from that source; the builder
    never places flowing water), or by any other self-starting source a builder can place. The source must be
    reachable to pour from a bucket.
  - **Every kinetic part turns**: every shaft, gearbox, cogwheel and belt in the drawing (all blocks with a `Speed` go
    in the turning check), so the owner never reads "N parts don't turn". No decorative, unconnected shaft or cogwheel.
  - **Enough stress capacity** for everything on the network: an over-stressed Create network stops (Speed 0) and
    fails every test below.
  - Create block states that matter (`create_builds.py`'s header, checked in the jar): a water wheel's `facing` is its
    axle's direction; a shaft's, gearbox's and cogwheel's `axis` their own; a mechanical press turns about its
    `facing` axis; a belt's `part` (`start`, `middle`, `end`), `facing`, `slope` and `casing`, its pulleys turning about
    the axis across its `facing`. Each belt segment costs a belt connector (create.md, 39.4), and a belt is laid in one
    go.
- **Storage.** At least **one chest within 8 blocks across and 4 up or down of the blast furnace**
  (`SupplyContainers.RADIUS` / `VERTICAL`, read by `VillageMachines.stores`): the Armorer's store, where the press's
  ingots come from and the sheets go back to (`MachineWork`). With none, the Armorer never presses. The chest and the
  press must both stand **inside the build's own outline** (`VillageMachines.village`: only machines and chests inside a
  building the builders finished count), and the press within 32 blocks of the blast furnace (`MachineWork.REACH`).
  The depot, belt and press never count as storage (`Kinetics.keepsOwnItems`). Drawn empty (builders never copy
  containers; no loot table). Every chest stands against a wall, a solid block, a bed or another chest
  (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`) and is reachable on foot.
- **Beds and residents.** None required (no code or test counts a resident here; the old one has no bed). A bed, if
  drawn, counts as a village bed and must pass the bed checks below.
- **Furniture spots.** No kind is required (there is no `blueprint_furniture/workshop.json` today). Every piece drawn is
  recorded as a spot (`rebuild.py`) and must pass `FurnitureSpotGameTests`, `FloatingFurnishingsGameTests` and
  `QaChairFacingGameTests`.
- **A way in on the z = 0 side** that a villager 1.95 blocks tall walks through (`QaDoorwayClearanceGameTests`), the
  blast furnace and every chest reachable on foot from outside within 2 across and 1 up or down
  (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`; it runs without Create, so Create's
  blocks are air there); anything upstairs reached by stairs or a ladder from inside.
- **Built whole, nothing skipped**: every block placeable by a builder from the materials list and standing as drawn
  when done (`plan.unfinished` empty, `site.skipped() == 0`). A block that needs a support placed after it, or that the
  running water would wash out, fails this.

## Fits
- **Plot: at most 20 wide x 20 deep x 18 tall.** Width: the compat tests set the origin at helper
  x = min(9, 29 - width) in the 30-wide `huge_area`, with the builder's bench at (2, 2, 2), the builder at (3, 2, 3) and
  the material barrels from x 1 to 6 at z 5; at 20 wide or less the build and its 2-block landscaping stay clear of
  them. Depth: `QaMechanicFlippedBelt1103eCompatTests.aFlippedTurnedWorkshopsBrokenBeltIsMendedWhole` turns the build a
  quarter, so its depth becomes its width in that test, and the Mechanic's toolbox (2, 2, 10) and store (2, 2, 12) stand
  just west of it: 20 deep at most keeps them clear. Height: built from helper y = 2 in the compat `huge_area`, which is
  **20 tall** (its `.snbt`), so 18 tall at most (blocks built above an area are never cleared).
- y = 0 sits on the clicked block; the front is the z = 0 side.
- **Build time: within 30,000 ticks** (`CreateBuildsCompatTests` and `QaCreateBuildsTurnedCompatTests`, build delay 1,
  builders' help off): the build, the last water and the machine turning. `QaMechanicFlippedBelt1103eCompatTests` has
  **40,000 ticks** for the build plus breaking and mending the belt. The old drawing is 403 blocks.
- **Declared size:** `StarterBlueprints.WORKSHOP` is `new Vec3i(14, 10, 9)`; the tests place the build from it and the
  Table hands blueprints out with it, so the ship step sets it to the new size (also `docs/wiki/create.md`, "14 x 10 x 9").
- **Front and way in:** the z = 0 side faces the player who places it; the way in is there.
- **Neighbours:** none fixed (no Steward rule, renewal or village plan places it).

## Rules tests check
Pinned coordinates (template spots; the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `CreateBuildsCompatTests.aBuilderBuildsTheWorkshopAndItTurns` (compat suite, with Create): built unturned from
  barrels holding exactly its materials; when done every block as drawn, nothing skipped, and
  `minecraft:blast_furnace` at **(10, 1, 6)** (the first expectation: must be a job site), `create:depot` at
  **(4, 1, 2)**, `create:belt` at **(4, 1, 4)**; the press at **(4, 3, 2)** turns (the "machine").
- `QaCreateBuildsTurnedCompatTests.aFlippedWorkshopIsBuiltAndItsBeltDrivesThePress` (`Mirror.FRONT_BACK`, flipped left
  to right) and `aWorkshopTurnedRoundIsBuiltAndItsBeltDrivesThePress` (`Rotation.CLOCKWISE_180`): built whole from
  exactly its materials, nothing skipped, the blast furnace at (10, 1, 6) a job site, the press (4, 3, 2) and the belt
  (4, 1, 4) turning, every belt whole, at least one belt. So the line must still turn mirrored: the wheel must still
  meet its water, gears still mesh.
- `QaMechanicFlippedBelt1103eCompatTests.aWorkshopsBrokenBeltIsMendedWhole` (as drawn),
  `aFlippedWorkshopsBrokenBeltIsMendedWhole` (flipped) and `aFlippedTurnedWorkshopsBrokenBeltIsMendedWhole` (flipped and
  turned a quarter): built from exact materials, every belt whole as built; then a player breaks the middle segment of
  the first belt and mines the pulley shafts Create leaves, and a Mechanic at a toolbox (helper (2, 2, 10)) with 4 belt
  connectors and 8 shafts in a chest (helper (2, 2, 12)) puts the whole belt back: every segment where it stood, whole,
  the press (4, 3, 2) and the belt (4, 1, 4) turning again, one connector used, none ever on the ground.
- In code, not a test: the showcase scene `create_builds` (`JobScenes`) sets the Watermill, the Windmill and the
  Workshop down in a row (each `size().getX() + 3` after the last) and passes when the press at **(4, 3, 2)** turns
  (`MachineWork.turning`); a much wider build moves the camera's framing (the ship step's call).

By id only (the drawing doesn't matter beyond the rules above): `CreateBuildsGameTests.hiddenWithoutCreate`,
`theSwitchHoldsThemBack`, `noContraptionsWithoutCreate` (a site and turning check of `workshop`, saved and loaded);
`QaTurnCheckSaves1031GameTests` (turning checks of `workshop`, saved and loaded); `CreateBuildsCompatTests.theCreateBuildsAreInTheTable`;
`QaTableCreate1117eCompatTests` (materials list real items, at least one of Create's; a preview with at least one Create
block; every style live; `getBlueprintHandsOutEveryCreateBuild`); `QaCreateSwitchesOff1102CompatTests.createBuildsOffTakesThemOffTheTableButTheyStillLoadById`.

- `QaCreateGoneUpkeepGameTests.aCreateBuildStandingWithoutCreateNeedsNoRepair`: the build placed without Create (its
  Create blocks gone to air) at helper (2, 2, 2) in the 30-tall area must need **no repair**, as placed and after 30
  ticks of its water running. So when the wheel and every Create block are gone, the loose water must not wash out or
  break any drawn block (nothing water breaks, such as a torch, a carpet, a button, a rail or a plant, in the path the
  water could take).

Library-wide checks it must pass (they read every build, `BlueprintLibrary.list(server, true)`, Create's blocks as air
in the main suite): `QaBuildReachGameTests`, `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`,
`QaLedgerFramesHaulGameTests`, `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `B648GameTests`,
`QaWindowsAndWards1028BugTests`, `QaBuilders1028GameTests` (no pane or bar joined on one side with air on the other),
`BlueprintsStayGameTests` (lamps, paths, bed foot and head), `QaFurnishedRooms1031GameTests` (no standing lantern on a
room's floor; every indoor chest against a wall, a solid block, a bed or another chest), `FurnitureSpotGameTests`,
`FloatingFurnishingsGameTests`, `StyleGameTests.chimneysStayWholeInEveryStyle` (only if a campfire stands on a stack).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair, no bed right inside a door; `open_panes`), STYLE.md's checks and `holes.py`:
`rebuild.py --candidate` prints no CHECK line, and `holes.py` passes. The ship step removes `workshop` from
`owner_rules_baseline.json` (2 today), keeps the old drawing in `blueprint_history/` (`history.py`), takes `workshop`
out of `create_builds.py`'s `CREATE_BUILDS` into `generate.py`'s rebuilt list, and updates the wiki (`create.md`,
`docs/guide/create-builds.md`), `StarterBlueprints.WORKSHOP`'s size and javadoc, and the scenes.

## Free
Everything not listed above: shape, plan, roof, materials, colors, storeys, a yard or none, where the line, the drive
and the water run (within the pinned spots, which the ship step may move), how the press is driven, what else stands
in the shop (an anvil, a furnace), a bed or none, more chests, lighting, and what the shop shows from the street.
