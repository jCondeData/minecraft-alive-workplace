# Stone Wall: the brief

Family id(s): `stone_wall`, `stone_wall_2` (never renamed)   Variants: village (none drawn), modded (none), looks (none)

## What it is for
The straight piece of a Town's or City's stone village wall: the **segment** of the `stone` wall kit
(`data/aliveworkplace/wall_kits/stone.json`: segment `stone_wall`, corner tower `wall_tower`, gate `gatehouse`,
ranks town to city). Once a village is raided or a bandit camp is near, the Steward proposes a wall along the plan's
wall line, and on approval his builders lay it out piece by piece (`city/Walls`): towers at the corners and at least
every 28 blocks, gatehouses where roads cross, and copies of this segment filling every stretch between. A Town that
had a Palisade replaces it with this kit a piece at a time. The player can also place it from the Blueprint Table
(`StarterBlueprints.DEFENCES`) or the creative tab. Once finished it makes the village **walled** (`Sieges.walled`),
so raids become sieges: raiders throw ladders up its outer face (`threat/Ladders`) or pile sand ramps against it
(`threat/SandRamps`), and during a siege the village's archers are set on its walkway as **battle stations**
(`guard/BattleStations`), and a guard on the walkway throws ladders down. Nobody lives or works in it.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `stone_wall` | The kit's segment: closes the line, a walkway of archers' stations, climbable by ladders and ramps | In `DEFENCES` (Blueprint Table, creative tab); not in `blueprint_unlocks/starting.json`; never rebuilt by the Grand Rebuild; not marked on the village map (`VillageMaps`: `stone_wall` -> null); a hidden `aliveworkplace:built/stone_wall` advancement |
| II | `stone_wall_2` | Hoardings: the walkway roofed over, and a gallery out over the wall's outer foot with **murder holes** in its floor; the walkway still a station all along | An upgrade by name (`BlueprintUpgrades.baseOf`: `_2` of `stone_wall`), built by a builder over a finished Stone Wall at **the same origin and rotation**; player-placed only (the Steward's wall layout only ever lays `stone_wall`); `built/stone_wall_2` advancement |

## Must have (each level)
- Job block(s): **none**. No villager point of interest (bed, barrel, composter, lectern, bell, ...): the Steward
  lays dozens of copies round a village, and every POI in each would be claimed.
- Beds and residents: none.
- Storage: none read. No containers.
- Furniture kinds a job or test needs: none (there is no `blueprint_furniture/stone_wall.json`).
- **No gate blocks of any kind**: no fence gate, door or iron bars (`Sieges.isGate`). A ram besieging a village walled
  only by a Stone Wall must break nothing of it (`QaThreatRepairGameTests.qaARamNeverBreaksAWallWithNoGate`), and the
  wall must not count as a gate build (`Sieges.isGated`); a gate in it would be rammed and never shut at night.
- **A way up from the village side** (z = 0 side) to the walkway: the owner's rules (`check.py`) refuse a standing
  level nothing leads up to. Villagers cannot path up ladders; guards are lifted onto a station once they stand within
  3.5 blocks of it below (`BattleStations.CLIMB_REACH`), so every walkway spot must be within 3.5 blocks, flat, of
  walkable ground on the village side.
- **Level I, the walkway (the stations).** A battle station is a block of the blueprint at y >= 2 with a sturdy top
  and two blocks of air (nothing in the blueprint) over it, that has no such spot one block lower beside it
  (`BattleStations.read`). The walkway is the row **z = 1, its floor block at y = 3** (stood on at y = 4), with
  (x, 4, 1) and (x, 5, 1) clear: the siege tests stand villagers on it at **x = 1, 3 and 5** (helper (12/14/16, 6,
  FRONT - 1) with the wall's origin at helper (11, 2, FRONT - 2)). Keep it along the whole width (x 0..6): the
  upgrade must have all seven, and it keeps most of the base (below).
- **Level I, the outer face (z = 2) closes the wall and is climbable.**
  - Every column x = 0..6 of row z = 2 is solid from y = 0 up to at least y = 3, with no gap a mob can walk or jump
    through: in the siege yards this row closes a 7-wide hole in a 4-high wall, and raiders may only get in over it.
    Nothing may stick out past z = 2 (the plot is 3 deep).
  - **The ladder column, x = 3, exactly five rungs.** `Ladders.height` climbs the column (3, y, 2) from y = 0 to the
    first height where it and the block above are both free of collision. That must be **y = 5**: (3, 0..4, 2) give
    collision (solid, so a rung can hang on its south face), (3, 5, 2) and (3, 6, 2) are clear (`LadderGameTests`
    and `QaLadderThrow1107dGameTests`: `RUNGS = 5`, foot at (3, 0, 3)).
  - From the ladder's top a raider crosses the wall's top inward and drops into the village: (3, 4..5, 1) clear (the
    walkway) and a column on the village side within 8 blocks of the foot open from the ground to over the wall's top
    (`Ladders.inside`).
  - The guard who throws the ladder down stands on the walkway within 2 blocks of the top rung at (3, 4, 3)
    (`Ladders.THROW_REACH`): the walkway at z = 1 is that spot.
  - Sand ramps (`SandRamps`, max 8 high): each outer column is a plain vertical face, solid from the ground to its
    top with air straight above its top, so a ramp's top block lands level with it.
  - Under 10 blocks high everywhere on the outer face (`Ladders.MAX_HEIGHT`), under 8 for ramps.
- **Level II, on top of level I.**
  - **The walkway: exactly seven stations at y = 3, z = 1** (x 0..6): (x, 3, 1) solid with a sturdy top, (x, 4, 1) and
    (x, 5, 1) empty for every x, nothing standable at y = 2 beside them (`SiegeGameTests.theDefenceUpgradesAreKnown`:
    "roofed walkway has N stations, not seven").
  - **The roof: at least one stairs block at y >= 6** (`QaDefenceUpgradeGameTests.theUpgradesAddWhatTheItemPromises`:
    "walkway has no roof").
  - **The gallery's murder holes: row z = 3 at y = 4**, x 0..6: at least 2 cells empty (air or not drawn) and at
    least 2 not (the same test: "gallery floor has N murder holes and N boards"). So level II is at least 4 deep.
  - It keeps at least 60% of level I's non-air blocks exactly (same state at the same position)
    (`SiegeGameTests.theDefenceUpgradesAreKnown`: "keeps only N of its base's M blocks"). Draw II as I plus what it adds.
  - No gate blocks, as level I.

## Fits
- **Plot, level I: exactly 7 wide (x) by 6 tall by 3 deep (z)**, `StarterBlueprints.STONE_WALL = (7, 6, 3)`. Treat this
  as fixed, not a limit: the wall-line layout spaces the segments by the blueprint's width, and about twenty siege
  and village tests place the Stone Wall by hand at hard-coded origins whose geometry assumes 7 x 3 (the yard's
  7-wide gap at x 11..17, the ladder foot at (3, 0, 3), the ring of walls in `LadderGameTests.aSiegeGathersOutsideTheRingOfWalls`,
  the zone offsets in `QaVillageReach1102GameTests`). Changing it means changing those tests; don't.
- **Plot, level II: 7 wide by 9 tall by 4 deep**, `STONE_WALL_2 = (7, 9, 4)`, same origin as I, the extra row on the
  outside (z = 3). Width and depth are fixed by the tests above; if the height changes, the ship step changes the
  `Vec3i` to match (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).
- y = 0 sits on the clicked block (each piece of a laid wall on its own ground height, `Walls.groundY`; the foundation
  fills under it on a slope).
- **Front and way in: z = 0 is the inside, the village side; z = 2 (II: z = 3) is the outside, facing the enemy.**
  There is no way through the wall (roads cross at gatehouses).
- **Neighbours: the wall kit contract (shared with `wall_tower`, `gatehouse`, and the palisade kit's
  `palisade`, `palisade_gate`, `palisade_tower`).** `Walls.layout`:
  - Every piece is placed with its **z = 0 face on the wall line** and its far z side outward (`Walls.rotationFor`):
    turned by any of the four rotations, never mirrored. Each piece sits at its own ground height, so neighbours
    may stand higher or lower by any amount; no code joins one piece's walkway to the next.
  - Along a run the pieces are **butted end to end** in x: a tower (5 wide, 5 deep) at each corner and at least every
    28 blocks, a gatehouse (11 wide, 5 deep) on each road crossing, and segments filling the stretches between.
    A segment's x = 0 and x = 6 ends meet a tower's or gatehouse's side (both deeper than the segment, all
    flush on the line at z = 0), or another segment.
  - **Segments overlap each other.** A stretch that isn't a whole number of segments gets its segments spread evenly,
    "sitting a little over one another": two copies can overlap by anything from 1 to 6 columns, the one built later
    replacing the earlier's blocks (air included) where they meet. Every column of the segment must work beside every
    column of another copy: the outer face stays closed, the walkway stays a walkway. A stretch shorter than one
    segment is left empty between its neighbours.
  - A Town replaces each finished Palisade piece standing on its spot with the stone kit's piece (`Walls.replaces`:
    the footprints share a column).
  - Sentries (`guard/WallSentries`) read nothing of the drawing: a sentry golem holds any point a Patrol Map gives
    it, on foot (it can't climb). No requirement.
- Material list: everything a survival player can get, small enough for four barrels of 27 stacks
  (`BuilderGameTests.buildsStoneWall`).

## Rules tests check
- `BuilderGameTests.buildsStoneWall` (`big_area`, origin (9, 2, 9), 12,000 ticks): a builder with exactly the plan's
  materials in barrels finishes it with no block wrong.
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: the `.nbt` sizes equal (7, 6, 3) and
  (7, 9, 4).
- `WallGameTests.bothWallKitsLoadWithTheirBlueprintsAndRanks`: `stone_wall` loads and is the stone kit's segment.
- `LadderGameTests` (all, `huge_area`, Stone Wall at helper (11, 2, 13), rotation none, in the gap of a 4-high yard
  wall at z 15): `aClimberLaddersTheStoneWallAndTheRestFollow` (one ladder of exactly 5 rungs up (3, 0..4, 3), facing
  south; the climber and a follower get in only by it), `aGuardOnTheWalkwayThrowsTheLadderDown` (a guard placed at
  (3, 4, 1) of the wall throws it), `laddersAreTakenAwayAlsoAfterARestart`,
  `aClimberAwayFromItsLadderAfterARestartFinishesItInsteadOfASecond`, `aLadderGoesUpInAirBesideAPlayersBlockNotOverIt`,
  and `aSiegeGathersOutsideTheRingOfWalls` (eight copies in a ring, all four rotations; the outer row at y = 1 is solid
  at x 0, 3 and 6 of each).
- `QaLadderThrow1107dGameTests` (same yard): villagers stood on the walkway at (1, 4, 1) and (5, 4, 1); 5 rungs.
- The same yard in `DesertRaiderGameTests.aSandRampReachesTheTopOfTheStoneWallAndIsGoneAfterTheSiege` (the ramp's top
  is level with the wall's: solid beside its highest block, air above; h(h+1)/2 sand blocks), `QaRaiderCulturesGameTests`
  and `QaRaiderCulturesBugTests` (sand and plants at the outside foot), `QaSandRampEdgeGameTests`,
  `QaSandRampSteps1103d2GameTests`, `QaSandRampHeight1103dGameTests`, `QaThreatsM32GameTests`,
  `QaCampsDesert1104d3GameTests`, `QaLadderHeight1103dGameTests`, `QaSiegeLadderBreach1103dGameTests`,
  `QaSiegeCaps1103dGameTests`, `QaSiegeConverts1109dGameTests` and the ladder-over tests built on it: every one needs
  the Stone Wall as the village's finished wall build, and those with it in the yard's gap need the outer row closed.
- `QaThreatRepairGameTests.qaARamNeverBreaksAWallWithNoGate`: more than 10 non-gate blocks, no gate block, nothing
  broken by a ram in 800 ticks.
- `SiegeGameTests.theDefenceUpgradesAreKnown`: `stone_wall_2` is in `DEFENCES`, upgrades `stone_wall`, is a defence
  with stations, keeps >= 60% of the base's blocks, and has exactly 7 stations at y = 3, z = 1.
- `QaDefenceUpgradeGameTests.anUpgradeLinesUpOnlyOverItsOwnFinishedBase` (II clicked on a solid block at y >= 1 of a
  finished Stone Wall lines up on its origin) and `theUpgradesAddWhatTheItemPromises` (murder holes and boards at
  (x, 4, 3); stairs at y >= 6).
- Village tests that record finished Stone Walls by their middle (`Builders.spot`) in all rotations:
  `OrphanHallPointGameTests`, `QaVillageReach1102GameTests`, `QaVillageReach1102BugTests`,
  `QaZoneNearerHall1115bGameTests`, `QaVillageMoverFixes1115c3GameTests` (sizes, not shape).
- The owner's rules (`check.py`, including a way up to every standing level) and STYLE.md's checks; `holes.py` must
  pass (its two `stone_wall_2` EMBRASURE allowances belong to the old drawing).

## Free
Everything not listed above: materials, the look of the outer face and its battlements, how the walkway is reached,
the hoardings' form, lights and trim, as long as every pinned cell, the 7 x 6 x 3 and 7 x 9 x 4 plots and the
"any overlap with another copy still works" rule hold.
