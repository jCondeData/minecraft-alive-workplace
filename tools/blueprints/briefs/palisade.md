# Palisade: the brief

Family id(s): `palisade`, `palisade_2` (never renamed)   Variants: village (none drawn), modded (none), looks (none
drawn; the generic blueprint styles in `blueprint_styles/` may swap blocks of any build, nothing to draw for them)

`palisade_gate` and `palisade_tower` are the same wall kit's other two pieces and have their own briefs; this brief
only says what the segment owes them (see "The kit contract").

## What it is for
The wall segment of the **Palisade** wall kit (`data/aliveworkplace/wall_kits/palisade.json`: `segment` =
`aliveworkplace:palisade`, `corner_tower` = `palisade_tower`, `gate` = `palisade_gate`, ranks Hamlet to Village). When a
village was raided in the last 7 days or bandits camp nearby, the Steward proposes a wall along the City Plan's wall
line; once approved, builders lay the line out (`city/Walls.layout`) and build it piece by piece, the Palisade segment
being by far the most repeated piece (dozens round a village), each at its own ground height. A Town later replaces the
palisade with the Stone kit a piece at a time. The segment is also a player build from the Blueprint Table (in
`StarterBlueprints.DEFENCES`, the creative tab, never rebuilt by the Grand Rebuild). In a siege it is part of the
village's defences: raiders cannot ram it (only gate blocks are rammed), they climb it with ladders or sand ramps, and
the village's archers stand on its walkway as **battle stations** (`guard/BattleStations`). Witches of a Coven raid keep
their distance from it and throw over it. `palisade_2` (Palisade II, a defense upgrade, 32.20) is clicked by the player
over a finished Palisade and adds a fighting step on the inside and a ditch of upward dripstone stakes on the outside.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `palisade` | a wall segment with a walkway of at least 4 battle stations; the Steward's kit segment; a defence for sieges | Blueprint Table (managed, locked until found; not a market-day build); creative tab; `built/palisade` advancement |
| II | `palisade_2` | a fighting step along the inside with at least as many battle stations as level I's walkway, and a ditch of at least 6 pointed dripstone stakes pointing up, outside the base's footprint | Upgrade (`BlueprintUpgrades`: `<name>_2`), Table hint "upgrade", never sold on market day; built over the finished Palisade at the same origin and front, so only what's new is built; `built/palisade_2` advancement. The Steward's walls never build it (the kit names only `palisade`) |

## Must have (each level)
- Job block(s): none. Nobody works here; no blueprint_furniture file exists or is needed.
- Beds and residents: none.
- Storage: none read.
- Furniture kinds a job or test needs: none.
- **Battle stations** (`BattleStations.read`, the rule every count below uses): a station is a non-air block of the
  blueprint at **y >= 2** (so one stands at y >= 3) whose **top face is sturdy** (a full block, a top slab, an upside-down
  stair; not a fence, ladder, bottom slab or dripstone), with **no blueprint block at y + 1 and y + 2** above it, and that
  is **not a "merlon"** (a spot is dropped when a station-candidate one block lower stands beside it on any of the four
  sides). Only the blueprint's own blocks count, not the world.
  - Level I: **at least 4 stations** (`QaDefenceUpgradeBugTests.palisadeIIsFightingStepIsABattleStation`).
  - Level II: **its stations with z < level I's depth are at least as many as level I's stations** (same test). So every
    station added to level I raises the bar level II's inside must meet.
- **Fighting step (level II)**: for **every x in 0..6 except 3**, a non-air block at **(x, 2, 0) or (x, 3, 0)**
  (`QaDefenceUpgradeGameTests.theUpgradesAddWhatTheItemPromises`). x = 3 is free.
- **Ditch stakes (level II)**: **at least 6 `pointed_dripstone`**, every one with `tip_direction = up`, every one at
  **z >= level I's depth** (outside the base's footprint), same test. A blueprint starts on top of the clicked block and
  can't dig below it, and a pointed dripstone pointing up needs a sturdy block under it to stand and be placed.
- **Level II keeps level I**: at least **60 % of level I's non-air blocks** are in level II at the same position with
  the **identical block state** (`SiegeGameTests.theDefenceUpgradesAreKnown`). Draw both levels together; II grows out of I.
- **Something to stop a mob**: level I has **more than 10 blocks that block motion** (`CovenGameTests.raidingWitchesKeepTheirDistanceFromAWall`
  reads them through `Sieges.wallBlocks`).
- **No gate blocks in the segment**: a fence gate, door or iron bars has hit points (`Sieges.hitPoints`) and is what
  rams break, and a ladder or sand ramp will not lean on one; the segment is the part that is not a gate (that is
  `palisade_gate`'s job). Nothing tests this for the segment, but `Sieges` reads every defence's gate blocks.
- **Climbable height** (code fact, a balance choice): raiders' ladders get over a wall column up to 10 high
  (`Ladders.MAX_HEIGHT`), sand ramps up to 8 (`SandRamps.MAX_HEIGHT`); higher, they cannot. The palisade is the low,
  early kit; keep where a raider must climb at 8 or lower unless the owner decides otherwise.
- Every block must be one a builder can place and a survival player can get (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`:
  nothing classified `SKIP`); no container (builders never break containers when taking the wall down or replacing it
  with stone).

## Fits
- Plot: y = 0 sits on the clicked block (each piece on its own ground; the foundation fills under it on a slope:
  `site.setLevelGround(false)`). The front is the z = 0 side, which is the **inside of the wall, facing the village**;
  z = size - 1 is the outside (`Walls.rotationFor`: the piece's z = max side is turned away from the hall).
  - Width (x): **7 to 9**. At least 7 because the fighting-step test checks x = 0..6; at most 9 because
    `QaDefenceUpgradeGameTests.anUpgradeLinesUpOnlyOverItsOwnFinishedBase` places the Palisade at x = 2 and the Stone
    Wall at x = 11 of the same area; and the layout test needs at least 30 segments round an 80-block square (with
    today's 7-wide gate and 5-wide tower that holds up to width 11).
  - Depth (z), level I: **at most 4**: `CovenGameTests.raidingWitchesKeepTheirDistanceFromAWall` places the Palisade at
    helper (12, 2, 6) and spawns witches at the build's (1, 0, 4), (3, 0, 4) and (5, 0, 4), which must be outside it.
    Level II is deeper than level I (its stakes are at z >= level I's depth).
  - Height: the declared 6 today; anything up to 12 fits every test area that builds it (`big_area`, 18 tall, from
    y = 2), but see "Climbable height" above.
  - Declared sizes: `StarterBlueprints.PALISADE` is `7, 6, 2` and `PALISADE_2` is `7, 6, 4` today. If the new drawings
    differ, the ship step changes those `Vec3i`s to match (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`)
    and nothing else needs to: `Walls.layout` reads the sizes from the blueprints.
- Front and way in: no door and no way through (that is the gate's job). The way up for archers is free: villagers
  can't path up ladders, so a guard who comes within 3.5 blocks of the foot of his station and below it is set on it
  (`BattleStations.CLIMB_REACH`); a ladder or steps are allowed, not required.
- Neighbours: see the kit contract below. Never marked on the village map (`VillageMaps`: `palisade` -> none).

## The kit contract (what `city/Walls` and `guard/` read)
- **Placement**: `Walls.layout` lays every piece with its **z = 0 face on the wall line** and its depth going outward;
  it reads only each piece's **size** (`segW` = the segment's x, the gate's x, the tower's x, and each one's z).
  Segments stand side by side along x, ends touching: segment, segment, ..., a gate in place of a segment wherever a
  road crosses, a tower at every corner (filling the corner outside both runs) and at least every 28 blocks.
- **Segments overlap their neighbour**: a stretch that isn't a whole number of segments is filled by spreading
  whole segments evenly, so **neighbouring copies overlap by anything from 1 to width - 1 columns** (`Walls.run`). The
  overlapping columns are built by both sites. So the segment's columns must agree where two copies overlap: the safe
  drawing repeats the same column (or a pattern that matches itself at every shift), or the overlap leaves one copy's
  blocks over the other's. `QaStewardMaterials1031GameTests.twoOverlappingPalisadesFromOneSharedChestLoseNothing`
  builds two copies 5 apart (origins x = 3 and x = 8) from one shared chest stocked with exactly both lists: **both
  must finish**, and no spruce log or spruce fence may go missing (it counts only those two items).
- **Ends**: a segment's x = 0 and x = width - 1 ends meet another segment, a gate (same z = 0 line) or a tower's side.
  Neighbours may stand a block or two higher or lower on a slope (each piece at its own ground height,
  `QaWallGameTests.eachPieceSitsAtItsOwnGroundHeight`), so an end must read right with no neighbour beside it at that
  height. A segment deeper than the tower's width (5 today) would stick out past the corner tower.
- **Walk heights**: no code checks that the segment's walkway lines up with the gate's or the tower's; each piece's
  battle stations are read on its own. The designers of `palisade`, `palisade_gate` and `palisade_tower` should agree on
  one walk height and inside line between them (and Palisade II's step) and state it in each plan.
- **Replaced by stone**: a Town lays the Stone kit's pieces over the palisade's spots (`Walls.replaces`), one at a time;
  the stone piece clears only its own box, so palisade blocks outside the stone segment's footprint would be left
  standing.
- **Counts**: a whole wall counts as one building for the village's rank (`Walls.countsAsBuildings`); a site whose
  builder died stays open for the next builder. Nothing in the drawing affects these.

## Rules tests check
- `QaDefenceUpgradeBugTests.palisadeIIsFightingStepIsABattleStation` (`EMPTY_STRUCTURE`): level I has >= 4 battle
  stations; level II has, at z < level I's depth, at least as many.
- `QaDefenceUpgradeGameTests.theUpgradesAddWhatTheItemPromises` (`EMPTY_STRUCTURE`): level II has >= 6 pointed
  dripstone, all `tip_direction = up`, all at z >= level I's depth; a block at (x, 2, 0) or (x, 3, 0) for x in 0..6 except 3.
- `SiegeGameTests.theDefenceUpgradesAreKnown`: `palisade_2` is in `DEFENCES`, upgrades `palisade` by name, is a defence
  with stations, and keeps >= 60 % of `palisade`'s non-air blocks with identical states at the same positions.
- `SiegeGameTests.onlyAWalledVillageIsBesieged`: `palisade` is a defence (by id; automatic).
- `QaDefenceUpgradeGameTests.anUpgradeLinesUpOnlyOverItsOwnFinishedBase` (`huge_area`): the Palisade placed at helper
  (2, 2, 3) beside a Stone Wall at (11, 2, 3); it needs a solid block at y >= 1 to click on; Palisade II clicked on a
  finished Palisade lines up over it exactly (and not on a shift-click or an unrecorded one).
- `BuilderGameTests.buildsPalisade` (`big_area`, origin (9, 2, 9), 12,000 ticks): a builder stocked with exactly the
  plan's materials in four barrels at helper (1..5, 2, 4) finishes it with every block as drawn (the list must fit in
  4 x 27 stacks).
- `QaStewardMaterials1031GameTests.aPalisadeBuiltFromExactlyItsListUsesExactlyItsList` (`big_area`, origin (6, 2, 9),
  3,000 ticks): built from exactly its list, it finishes and every listed item is built in, in the chest or in the bag.
- `QaStewardMaterials1031GameTests.twoOverlappingPalisadesFromOneSharedChestLoseNothing` (origins (3, 2, 9) and
  (8, 2, 9), 5,000 ticks): see the kit contract.
- `CovenGameTests.raidingWitchesKeepTheirDistanceFromAWall`: the Palisade at helper (12, 2, 6) has > 10 motion-blocking
  blocks, leaves the witches' spawn cells (1..5, 0, 4) free, and the witches stay >= 10 blocks from it.
- `WallGameTests.bothWallKitsLoadWithTheirBlueprintsAndRanks`: `palisade` loads and is a wall piece.
- `WallGameTests.aSquareWallLineGetsSegmentsFourTowersAndAGateOnTheRoad`: >= 30 segments round an 80-block square;
  towers and gates overlap nothing, only neighbouring segments overlap (width 7 to 11 with today's gate and tower).
- `WallGameTests.builtPiecesStandWithTheirBlocksInTheWorld`, `QaWallGameTests.eachPieceSitsAtItsOwnGroundHeight`,
  `QaWallTowerGameTests` (no two open sites overlap unless both are segments), `WallGameTests.theWholeWallCountsAsOneBuildingForTheRank`:
  read the layout from the segment's size; any size inside the limits above passes.
- `QaSiegeDripstone1108dBugTests.aDrownedPiratesTridentLeavesThePalisadesDripstoneSpikeStanding`: a raider's trident
  leaves a finished build's dripstone spike standing (uses its own spike; no drawing rule).
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: both `.nbt` sizes equal the declared
  `Vec3i`s and every block is placeable.
- The owner's rules (`check.py`) and STYLE.md's checks: `rebuild.py --candidate` prints no CHECK line, and `holes.py`
  passes.

## Free
Everything not listed above: what the wall is made of, its profile and tops, how archers get up, its look from inside
and outside, lighting, and what the ditch and step look like, within width 7-9, depth <= 4 (level I) and the station,
step, stake, 60 % and overlap rules.
