# Village Train: the brief

Family id(s): `village_train`, saved live as **`structure/train/village_train.nbt`** (the code reads the id
`aliveworkplace:train/village_train`, `CaravanTrains.TRAIN`; never renamed or moved). Save it with
`rebuild.py village_train train/village_train`.
Variants: none (no village, modded or culture looks; it is never at the Blueprint Table and never restyled).

ROADMAP 50.13 (with the Train Station, which has its own brief). One version, one level. Create only: without Create
nothing reads it (`CaravanTrains.live()`), and the file may hold Create block ids freely.

## What it is for
The caravans' **Create train** between two villages that both have a Train Station on one track network (ROADMAP
39.7a-d). It is not a building: when the first village of a linked pair has no train yet, `CaravanTrains.openTrain`
opens a site of the drawing's blocks **standing on the straight track just past its station** (a road-segment site
named "Village Train", `blueprint.aliveworkplace.village_train`), and an idle builder of the village builds it from the
Builder's Bench chests like any build, on the ground as it is (no levelling, nothing cleared). When every block stands
and the site is finished, `CreateTrains.assemble` covers the drawing's whole bounding box with **one Create super glue**,
sits the village's **Conductor** (else the nearest jobless grown-up within 24 blocks of the station) in the train's
**seat**, and has the **station assemble it** into one Create carriage, then gives it a cyclic Create schedule: to the
other station, wait 10 s, back, wait, forever. Create drives it. A caravan waits at the station, its goods go into the
**carriage's chest**, ride, and come out at the other village's station. If the driver dies, another villager by the
train is sat in the empty conductor seat (`CreateTrains.seat`).

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `train/village_train` | One Create carriage: a bogey on the track, a driver's seat that can drive both ways, one chest for the goods | Opened by `CaravanTrains.build` when both stations are aimed and linked and Create says the train isn't built; behind `villageTrains` and Create. No Table, no cost list, no upgrade |

## Must have
Every one of these is read by code or a test (classes cited). All positions are in the drawing's own frame (below).

- **Exactly one bogey, `create:small_bogey`, at (0, 0, 0)**, the cell of the drawing nearest the station.
  - The code puts the drawing's origin one block above the first track block past the station and turns the bogey's
    `axis` to the track (`CreateTrains.parts`). Create refuses a train whose frontmost bogey isn't there
    (`train_assembly.frontmost_bogey_at_station`).
  - Only `small_bogey`: it is the only bogey a builder can place (`MaterialRules.PLACED_WITH`: it is built with one
    `create:railway_casing`), and the only one Create lets carry a carriage alone (`single_bogey_carriage`).
  - A second bogey is possible only at (3, 0, 0): one glued carriage takes at most two (`too_many_bogeys`), and
    Create wants them at least 3 apart (`bogeys_too_close`, checked in the jar). It costs a fourth railway casing
    (see the test stock below). `CreateTrains.ownBogeysOnly` counts the drawing's bogeys by id (path ends in `bogey`).
- **Exactly one Create seat** (any `create:*_seat`, a `SeatBlock`; the tests stock `create:brown_seat`), with **a
  `create:controls` directly in front of it and one directly behind it along the track (x - 1 and x + 1, same y and
  z), each facing the seat** (the one at x - 1 faces east, the one at x + 1 faces west).
  - That makes it a conductor seat both ways (Create's `CarriageContraption.inControl`: controls next to the seat
    whose `facing` points back at it). The schedule runs there and back on one line, so the train must be driven
    both ways; a train with controls one way only can't come back.
  - Only one seat: `assemble` sits the conductor in the first `SeatBlock` it finds, and `seat` fills only conductor
    seats. A second seat could get the conductor and leave the train with no driver.
  - **No controls block may face across the track** (north or south in the drawing): Create refuses the whole train
    (`sideways_controls`). More controls than the two aren't needed.
  - **The cell straight above the seat is open** (air, or a block a head can be in): the seated villager's head is
    there for the whole ride. A suffocating driver dies and every ride test fails (the train stands driverless).
- **Exactly one chest** (`minecraft:chest`, single): the caravan's goods ride in it (Create's mounted storage of the
  carriage, `CreateTrains.chest`/`load`/`unload`/`cargo`). The train's whole storage must take 32 logs and 5 wheat
  **and refuse** 27 more stacks of stone (`CreateTrainCompatTests.aVillageTrainIsAssembledAtAStationAndRunsToTheOther`:
  "more went into the chest than it holds"). So one single chest and no other container: no barrel, second chest,
  double chest, vault, shulker box or toolbox anywhere on the train.
- **More than 6 blocks** (`parts.size() > 6`, same test), and at least 2 (Create refuses a one-block carriage).
- **Every block touches the bogey through other blocks of the drawing** (face to face). Create's glue joins only
  touching blocks inside its box; a block standing alone stays in the world after assembly. Then the train counts as
  not built, the builder puts a second set up, and `QaTrainSiteRaceCompatTests` fails ("a second set of the train's
  blocks stands at the station").
- **Everything the glue box covers joins the train.** The glue is the drawing's whole bounding box, so whatever stands
  in the world in an empty cell of the box (the station's roof, a lamp, a sign) is glued in and carried off. Only the
  drawing's own blocks are checked for room. Keep empty cells inside the box few, and expect each of them to be
  empty in the world.
- **Every block a builder can place and Create can move:**
  - An item for each block (the builder fetches it from the bench's chests).
  - Support for everything that needs it inside the drawing (a hanging block hangs from a block of the train, not
    from the station).
  - No unbreakable blocks and nothing in Create's `#create:non_movable`.
  - No vanilla job block (a barrel, lectern, composter...): it stands at the station as a POI before assembly, and a
    jobless villager takes it.
  - No kinetic parts (nothing turns on a train; the Mechanic's checks are for buildings).
- Beds and residents: none. The Conductor's job block is the station's `create:track_station` (Train Station brief),
  not anything on the train.
- Furniture kinds: none needed (no `blueprint_furniture/` file; the recipe's `furniture.json` may be empty).

## Fits
- **The drawing's frame:**
  - x runs along the track away from the station (drawn running east; `CreateTrains.parts` turns it).
  - y = 0 is the layer standing on the track (the bogey's).
  - z is across the track.
- **Length: at most 4 along the track (x = 0..3).** The code wants straight track under every x of the drawing
  (`room`: the track at 1..length past the station's own track piece must be the same straight track), and the
  tests' lines give exactly 4. On `CreateTrainCompatTests`' stepped road, a Create curve starts on the 4th block past
  each station (x = 4..7 past `STEPPED_A`, x = 23..20 past `STEPPED_B`), so a 5-long train has "no room" and
  `aTrainOnASteppedRoadRunsWithTheGoodsInItsChest` and its kin fail.
- **Width: 1 (z = 0 only).** The drawing's z+ side lands on the station's side of the track for one direction and on
  the other side for the other (`relative(along.getClockWise(), z)`), and the station and its platform stand beside
  that track. Every drawn cell must be air or replaceable in the world (`canBeReplaced`) or there is "no room for
  the village train".
- **Height: at most 5 layers above the track (y = 0..4).** `CreateTrainCompatTests.line` clears only y 2..7 over track
  at y 2 (the rows a train above it can use: 3..7). Above that the test areas aren't cleared, and the Train
  Station's roof must clear the train past its station block (agree that height with the Train Station's brief).
- Front and way in: none to walk. The x = 0 end faces the station; the driver is sat in by code
  (`SeatBlock.sitDown`), so the seat needn't be reachable on foot, but players can sit in it.
- Neighbours: the station block and its platform beside the track, the road's track running on both ways. The station
  may face either way along its track (towards the other village, `CreateTrains.wanted`), so the train goes up on
  either side of the station block.

## Rules tests check
Create's tests are compat tests (`./gradlew runCompatGameTest`). Without Create, `VillageTrainGameTests` reads nothing of
the drawing.

**The drawing's shape:**
- `CreateTrainCompatTests.aVillageTrainIsAssembledAtAStationAndRunsToTheOther`, and the same lines in
  `CreateTrackCompatTests` (spur, dip, zig-zag), `QaVillageTrainCompatTests` and `QaConductorCompatTests`:
  `train(...)` finds room for every drawn block past the station (count equals `trainBlocks`), more than 6 blocks,
  built counts as built, the station assembles it, the cells either side of the station's track piece are air after
  assembly, the chest takes 32 logs + 5 wheat and refuses 27 stacks more, and the train reaches the other station.
- `CreateTrainCompatTests.aTrainOnASteppedRoadRunsWithTheGoodsInItsChest`: the builder builds it, the train runs on
  the stepped road (the 4-long limit) with the 32 logs in its chest; with an empty barrel the site waits for
  `create:railway_casing` (true as long as there is a bogey).
- `QaTrainSiteRaceCompatTests.aTrainAssembledTheTickItsLastBlockGoesUpLeavesNoSiteOpenAndIsInTheChronicle`: one set of
  parts built, assembled once, no second set standing afterwards (all blocks glued in).

**The bill of materials** (the test barrels and the showcase are stocked with the old train's exact bill):
- Stocked in `CreateTrainCompatTests.builder(helper, true)`, `QaTrainSiteRaceCompatTests.builder` and the showcase
  scene `village_train` (`JobScenes`):
  - 3 `create:railway_casing` (the bogey takes one), 2 `create:controls`, 1 `create:brown_seat`, 1 `minecraft:chest`;
  - 3 `minecraft:spruce_fence`, 4 `minecraft:spruce_slab`, 1 `minecraft:lantern`.
- A drawing whose bill fits inside that stock passes as is. Any other block or a higher count means updating all
  three barrels in the same change, to exactly the new bill: the race test's "exactly one train's parts" stays exact.
- `QaVillageTrainCompatTests.aTrainMissingAPartWaitsThenFinishesWhenItIsBrought` needs two things:
  - **a `minecraft:spruce_fence`** the builder puts up, which the test breaks;
  - **a `minecraft:lantern`**, left out of the barrel at first: the site must wait for it (`missing` holds it),
    then finish.
  - A drawing without either needs that test pointed at another vanilla part of the new drawing, with the same steps
    (one placed part broken, one part held back).
- `ConductorCompatTests` and `TrainDriverCompatTests`, the `QaTrainDriver*` tests and `QaConductorCompatTests`: a
  villager is sat in the one conductor seat, rides the whole trip and calls the stop, and a new driver takes the dead
  one's seat. They need the seat with controls both ways and the open cell above it.

**The checks every drawing gets:**
- `rebuild.py ... --candidate` prints no CHECK line (check.py, the owner's rules), and `holes.py` passes.
- Process:
  - `tools/blueprints/generate.py` (line ~309) rewrites the live file from `create_builds.village_train()` on every
    run. Shipping the new drawing means retiring that call in the same commit.
  - The render tools have no Create textures: Create blocks show missing in renders, so judge them in the scene
    `village_train` (GitHub films it).

## Free
Everything else: what it looks like and what it is made of (outside the test stock above), what goes in the cells
around the seat, controls, chest and bogey, its height up to 5 and its length up to 4.
