# Gatehouse: the brief

Family id(s): `gatehouse`, `gatehouse_2` (never renamed)   Variants: village (none drawn), modded (none), looks (none)

Two tiers. `gatehouse_2` upgrades `gatehouse` by name (`BlueprintUpgrades.baseOf`). The code follows any later tier
or style back to `gatehouse` (`Sieges.isGated`, `isDefence`, `BattleStations.hasStations`), so a tier III would need no
code change. There is no `blueprint_furniture/gatehouse*.json`, and none is needed.

## What it is for
The way through a Town's or City's stone wall: the `gate` piece of the **stone wall kit**
(`data/aliveworkplace/wall_kits/stone.json`: segment `stone_wall`, corner tower `wall_tower`, gate `gatehouse`, ranks
town to city). When the Steward's approved wall line crosses one of the plan's roads, `city/Walls.layout` puts a
Gatehouse there in place of a segment, and an ordinary Builder builds it. A Town that had a Palisade replaces its
Palisade Gate with this, one piece at a time (`Walls.replaces`). A player can also place it from its blueprint
(Blueprint Table and creative tab, with `StarterBlueprints.DEFENCES`). Nobody lives or works in it. What it does in the
game:
- **Night:** in a village with a Village Hall and at least one guard, the hall's round (`guard/Gates`) shuts **every
  fence gate of the blueprint** at dusk (day time 12,500) and opens them before dawn (23,500). Players can still open
  them by hand.
- **Siege** (`threat/Sieges`): a finished Gatehouse makes the village "walled", so a raid by a ram culture is a siege.
  At once the gates shut and the **portcullis drops**: under every iron bar the blueprint draws, bars fall through the
  air below it down to the first drawn block. The gate build nearest the raiders is the **breach**; rams break its
  gate blocks (fence gate 60 hit points, iron bars 150, door 80), the middle way first, from the outside in, low
  blocks before high ones. The bars go up again and the gates open at the first dawn after. It holds "about a minute"
  where a Palisade Gate holds about ten seconds (wiki `sieges.md`; the tests below measure it).
- **Battle stations** (`guard/BattleStations`): archers stand on its walkway over the gate and shoot the raiders
  coming at the portcullis; knights hold a spot 3 blocks inside the breach, medics 6.
- **Gatehouse II** adds a **barbican**: a second gate and portcullis out in front. In a siege both portcullises drop;
  the rams must break all of the outer gate line before they walk in to the inner one, so it holds longer.
- Counted as part of the wall (one building for the village's rank, `Walls.countsAsBuildings`); marked as a defence
  on the village map (`hall/VillageMaps`, by id); the Great Wall wonder requires one finished `gatehouse`
  (`wonders/great_wall.json`, by id); hidden advancements `aliveworkplace:built/gatehouse` and `built/gatehouse_2`;
  the questbook's Town chapter unlocks it (`BlueprintUnlocks.QUESTBOOK`, `/workplace unlock`). All by id, none read the
  drawing.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `gatehouse` | The wall's gateway: three fence gates the guards shut at night, a portcullis that drops in a siege, a walkway of archers' stations over the gate | In the Blueprint Table and creative tab with `DEFENCES`; a questbook reward unlocks it (`BlueprintUnlocks.QUESTBOOK`); unlock rules by id, not by the drawing; placed by `Walls` at every road crossing of a stone wall |
| II | `gatehouse_2` | A barbican in front: a second line of three fence gates and a second portcullis, murder holes in the passage roof between the two gates, at least one archers' station on the barbican | An upgrade by name, built by a builder over a finished Gatehouse at **the same origin and rotation** (clicked onto it, it lines up; `BlueprintItem`). Player-placed only: `Walls` only ever lays `gatehouse` |

## Must have (each level)

### Gate blocks (the cells are pinned by tests: keep them exactly)
`Sieges.isGate` counts every **fence gate, door (lower half) and iron bars** of the blueprint as a gate block; `Gates`
swings every fence gate; `Sieges.drop` drops a portcullis under every iron bar. So the build has **exactly these gate
blocks and no others** (no door, no iron-bar window, no decorative fence gate anywhere):
- **Level I: three `minecraft:spruce_fence_gate` at (4, 1, 2), (5, 1, 2), (6, 1, 2)**, drawn **shut** (`open=false`),
  their panels across the way (facing north or south, so a shut gate closes its cell between z = 1 and z = 3).
  - Spruce, not any fence gate: `BuilderGameTests.builderUpgradesAFinishedGatehouse` asserts `SPRUCE_FENCE_GATE` there.
  - Shut: `RaidGameTests` (the round opens 3 in the morning, after nothing moved without a guard).
- **Level I: three `minecraft:iron_bars` at (4, 3, 3), (5, 3, 3), (6, 3, 3)** (the portcullis, drawn up), with
  **(4..6, 1, 3) and (4..6, 2, 3) empty** in the blueprint (air or not drawn) and **(4..6, 0, 3) a drawn solid block**
  where the bars stop. Exactly **six** bars drop (`siege.portcullis.size() == 6`): any empty cell under them at y = 0,
  or any other iron bar in the build, changes that count.
- **Level II: everything of level I's gates and bars, at the same cells, plus the barbican's: three
  `spruce_fence_gate` at (4, 1, 8), (5, 1, 8), (6, 1, 8)**, shut, across the way; **three `iron_bars` at (4, 3, 7),
  (5, 3, 7), (6, 3, 7)**, with (4..6, 1..2, 7) empty and **(4..6, 0, 7) drawn solid**. Six fence gates and twelve
  dropped bars in all.
- Gate lines: `Sieges.pickBreach` groups gate blocks by depth (z), cells a step apart in one line. The inner line is
  z 2..3, the barbican's z 7..8; the gap between them (z 4..6) must stay at least 2 so they are two lines, and no
  gate block may sit in it.

### The way through
- **Walk level y = 1** all through the gateway (the gates stand at y = 1 on a floor at y = 0). Ground outside the build
  is one step lower: the way must be enterable from the outside face and leave onto the ground at the village face
  (z = 0) with steps of at most one block.
- **Headroom:** over the gateway column x = 5 (the middle way), nothing but gate blocks at y = 1 and y = 2 from the
  outside face to z = 0 (a pillager, 1.95 tall, walks out through it once the middle gate and bars are broken).
- **The ram's stand (level I):** (4..6, 1..3, 4) empty, with a floor at (4..6, 0, 4): a ravager (2.2 tall, about 2
  wide) has to stand right against the portcullis, because it strikes only within 1.75 blocks of its body
  (`Sieges.REACH`) and the fence gate is behind the bars.
- **Level II, the barbican's way:** (4..6, 1..3, 9) empty with a floor at y = 0 in front of the outer gate;
  (4..6, 2..3, 8) empty over the outer gates; and **the passage between the gate lines, every cell (4..6, 1..3, 4..6),
  empty** (not drawn, or air) so the ram walks in from the outer gate to the inner portcullis
  (`QaDefenceUpgradeGameTests.theUpgradesAddWhatTheItemPromises`: "the barbican's passage is blocked").
- **Level II, murder holes:** over that passage, at y = 4 for x 4..6, z 4..6: **at least 4 cells drawn (a roof) and
  at least 1 hole** (both (x, 4, z) and (x, 5, z) empty) (same test).

### Sealed everywhere else
With the gates shut and the portcullis down, no raider may get from the outside (the z = 4 side, and II's z = 9 side)
to the village side through, over or round the build. In the siege yards the build's **row z = 3 stands in the
yard's front wall** (a wall 4 high, y 0..3 in blueprint terms), the wall meets the build's two end columns there,
and rows z = 0..2 stand inside the yard. So:
- every route from the outside into rows z <= 2, or out of the build's ends (x = 0, x = 10) at z <= 2, is closed at
  y 1..3 at least, except through the gate blocks;
- nothing on the outside faces that a mob can step or jump up onto to get over (no ladder, stair or slab run on the
  outside);
- the Gatehouse must be **at least three times as slow** to get through as the Palisade Gate measured first in the
  same yard (`SiegeGameTests.aGatehouseTakesThreeTimesAsLong`), and Gatehouse II slower than Gatehouse I
  (`aGatehouseIITakesLongerThanAGatehouse`): any way round the gates breaks both.

### Battle stations (the walkway)
A station is a blueprint block at y >= 2 with a sturdy top, nothing drawn in the two cells above it, and no such spot
one block lower beside it (`BattleStations.read`). A guard is lifted onto it once he stands within 3.5 blocks (flat)
of it and at least 1.5 below (villagers can't path up ladders: `CLIMB_REACH`). The tests pin the walkway over the
gate. Positions below are where one **stands** (the floor block is one lower):
- **Stations standing at (4, 6, 2) and (6, 6, 2)**: floor blocks (4, 5, 2) and (6, 5, 2), with (x, 6, 2) and
  (x, 7, 2) empty.
- **They see the most.** An archer takes, among his free stations, the one that sees the most of the ground 2 to 16
  blocks out in front of the breach (5, 1, 3) (raiders' eyes in his line of sight, `BattleStations.view`), ties going
  to the highest, then the nearest to (5, y, 7). `QaBattleStationViewGameTests.theStationOverTheGateIsTheOneThatSeesMost`
  asserts he picks **(4, 6, 2) or (6, 6, 2)**, that it sees at least as much as every other station of the build,
  and **strictly more than the spot standing at (5, 6, 2)** (whatever is there). So: nothing in front of those two
  spots may cut the line down to the ground outside; any station that sees as much must stand lower than y = 6;
  and (5, 6, 2) must see less (a merlon or other block in front of it, or no station there).
- **Every station an archer may take on the Gatehouse stands at y = 6**, within the 11 x 5 footprint
  (`BattleStationGameTests.anArcherOverTheGateSeesAndHitsARaiderInFrontOfIt`: "isn't on the Gatehouse's walkway").
- **Reachable within 450 ticks** by a guard starting on the village side (all the station tests): the over-gate
  stations within 3.5 blocks flat of ground a villager can walk to from the village side (the inside of the gateway
  and the ground at the village face count).
- From his station the archer must hit a raider walking at the gate **before it is 2 blocks from the face at z = 5**
  (`QaBattleStationViewGameTests.aRaiderComingAtTheGateIsShotOnTheWayIn`), and one standing 12 out
  (`anArcherOverTheGateSeesAndHits...`).
- **Level II: at least one station with z >= 4** (on the barbican) (`SiegeGameTests.theDefenceUpgradesAreKnown`).
- Knights and medics stand 3 and 6 blocks inside the breach, on the ground nearest that point (2 above to 3 below):
  for level I that is the inside of the gateway at z = 0 and the ground in front of the village face. Keep the inner
  end of the gateway standable.

### Level II over level I
- **Same origin, same front.** II is I plus the barbican out in front (z 5..9). It **keeps at least 60% of level I's
  non-air blocks exactly** (same state, same cell) (`SiegeGameTests.theDefenceUpgradesAreKnown`), and the builder,
  upgrading a standing Gatehouse, places **under 80% of II's structure and decoration steps**
  (`BuilderGameTests.builderUpgradesAFinishedGatehouse`). Level I's gates and bars stay where they are.
- **What the upgrade still needs fits 27 stacks** (one chest) and includes spruce fence gates and iron bars (same
  test); 54 stacks at most in `QaDefenceUpgradeGameTests.anUpgradedGatehouseIsOneGatehouseII`.

### Nothing else
- Job blocks: none. Beds and residents: none. No villager point of interest (bell, lectern, barrel, composter, bed,
  workstation): the Steward lays one at every road crossing and every POI would be claimed.
- Storage: none read. Furniture: none needed.
- No sign, bell or other block that code reads.

## Fits
- **Plot, level I: exactly 11 wide (x) by 5 deep (z); at most 16 tall** (declared `StarterBlueprints.GATEHOUSE =
  (11, 9, 5)`). **Level II: exactly 11 wide by 10 deep; at most 16 tall** (`GATEHOUSE_2 = (11, 9, 10)`). y = 0 sits on
  the clicked block. Width and depth are fixed, not limits:
  - the width puts the middle way at x = 5 = width / 2: `Walls.run` centres the gate on the road at `d - gateW / 2`,
    and the yard tests put the build's origin at `14 - width / 2` with the gates at x 4..6;
  - the showcase scenes `battle_stations` and `warning` (`devclient/JobScenes`) stand a Wall Tower one block east of
    an 11-wide Gatehouse; `QaStationsAfterRaid1115d` and the warning tests put a Wall Tower at x 21 beside a
    Gatehouse at x 9;
  - the depth 5 puts row z = 3 in the yard's front wall with one row (z = 4) outside it (`SiegeGameTests.walls`:
    `z0 = FRONT - 3` for both tiers); raiders are spawned 9.5 (I) and 12.5 (II) rows out from the origin, so a deeper
    build would swallow them; the station test measures from the face at z = 5;
  - height: the builder test runs in `big_area` (18 tall, origin y = 2); if a height differs from 9, the ship step
    changes the `Vec3i` to match (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`). If
    level I is taller than 9, II must draw every cell I has above y = 8, air included:
    `aGatehouseIITakesLongerThanAGatehouse` clears only up to y = 8 before placing II over I's spot.
- **Front: z = 0 faces the village (the inside); the high z side faces out.** `Walls.add` turns each piece so its far
  z side faces away from the hall (any of four rotations, never mirrored); `Sieges.pickBreach` takes the blueprint's
  back as the outside. The tests also place it turned a quarter and mirrored (`QaGates1103dGameTests`): it must work
  in every rotation.
- **Neighbours (the wall kit contract):** its z = 0 face sits on the wall line, flush with the stone wall segments
  (7 wide, 3 deep) that butt against its two end columns x = 0 and x = 10, and with the corner towers (5 x 5). Each
  piece stands at its own ground height (`Walls.groundY`), so neighbours may be higher or lower; no code joins
  walkways from piece to piece. Two road crossings within one gate's width share one gate; a crossing too near a
  corner gets none.
- **Material list:** every block a builder can place (`MaterialRules`, no SKIP kinds); level I within four barrels of
  27 stacks (`BuilderGameTests.buildsGatehouse`, 30,000 ticks); the upgrade as above.

## Rules tests check
Yards: `SiegeGameTests.yard` (also in `QaDefenceUpgradeGameTests`) walls a yard 4 high in `huge_area`, the build's
row z = 3 in its front wall (helper z 15), origin x = 14 - width / 2, a villager in a stone cell inside, raiders to
the south.
- `SiegeGameTests.aGatehouseTakesThreeTimesAsLong`: when the siege begins, bars at (4..6, 3, 3) still there and
  dropped at (4..6, 1..2, 3); gates at (4..6, 1, 2) shut; exactly 6 bars dropped; (5, 1, 3) has 150 hit points and
  is the breach; the pillager through in at least 3x the Palisade Gate's time and within 2800 ticks; (5, 1, 3),
  (5, 2, 3) and (5, 1, 2) broken; the gate (5, 1, 2), not the dropped bar, listed for the builder.
- `SiegeGameTests.aGatehouseIITakesLongerThanAGatehouse`: the Gatehouse through within 2600 ticks; then II: bars at
  (4..6, 3, z) and dropped at (4..6, 1..2, z) for z 3 and 7, both gate rows shut, 12 bars dropped, breach (5, 1, 8);
  through slower than I and within 8500 ticks; (5, 1, 8), (5, 1, 7), (5, 1, 3), (5, 1, 2) broken; (5, 1, 8) and
  (5, 1, 2) listed for the builder, not (5, 1, 7).
- `SiegeGameTests.aRaiderBesideTheWallGoesInWhenTheWayOpens`, `aRaiderBackOutsideGoesInAgain`: the middle way open
  within 2000 ticks; a pillager teleported beside the yard, or back outside, gets in within 600.
- `SiegeGameTests.thePortcullisIsDownWhileTheSiegeLasts`: the hall's Defence page reads exactly "3 gates standing",
  "Portcullis: drawn up" / "down"; the six bars drop and join up, stay until dawn, then go; the drawn-up three stay.
- `SiegeGameTests.theDefenceUpgradesAreKnown`: II is in `DEFENCES`, upgrades `gatehouse`, is a gate build and a
  defence with stations, keeps >= 60% of I; the round opens and shuts exactly **6** fence gates; a station at z >= 4.
- `SiegeGameTests.onlyAWalledVillageIsBesieged`: both ids are defences.
- `QaDefenceUpgradeGameTests`: `anUpgradeLinesUpOnlyOverItsOwnFinishedBase` (II clicked on a solid block at y >= 1 of
  a finished Gatehouse lines up on its origin; not over a Wall Tower); `anUpgradedGatehouseIsOneGatehouseII` (built
  over I within 12,000 ticks from at most 54 stacks; one finished build on the spot; a fence gate at (5, 1, 8); the
  round moves 6 gates, not 9); `theUpgradesAddWhatTheItemPromises` (the passage and murder holes above);
  `bothPortcullisesOfGatehouseIIAreDrawnUpAtDawn` (gates at (4..6, 1, 2) and (4..6, 1, 8); bars dropped at
  (4..6, 1..2, 3) and (4..6, 1..2, 7), drawn up again at dawn, the drawn-up ones at y 3 kept);
  `theBarbicansGateIsTheFirstBreach` (the first block broken is a barbican gate, within 2200 ticks; no non-gate
  block of II ever broken).
- `BuilderGameTests.buildsGatehouse` (`big_area`, origin (9, 2, 9)): a builder with exactly the plan's materials
  finishes it with every block as drawn. `builderUpgradesAFinishedGatehouse` (`big_area`, origin (5, 2, 5)): the
  upgrade clicked at (0, 3, 2) of the finished Gatehouse lines up; it needs 1..27 stacks including iron bars and
  spruce fence gates; built, `SPRUCE_FENCE_GATE` at (4..6, 1, 8) and (4..6, 1, 2), `IRON_BARS` at (4..6, 3, 7) and
  (4..6, 3, 3); under 80% of II's steps placed.
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: sizes match, nothing unplaceable.
- `BattleStationGameTests.anArcherOverTheGateSeesAndHitsARaiderInFrontOfIt` (Gatehouse at (8, 2, 8), hall north):
  the breach's outside is south; the archer on a station at y + 6 inside the footprint within 450 ticks; sees and
  hits a raider 12 out without coming down.
- `QaBattleStationViewGameTests` (same layout): `theStationOverTheGateIsTheOneThatSeesMost` (station (4 or 6, 6, 2),
  best view, better than (5, 6, 2)); `anArcherPushedOffHisStationsMiddleStepsBack` (pushed half a block sideways he is
  still on it and steps back); `afterARestartHeTakesTheSameStationAgain`; `aRaiderComingAtTheGateIsShotOnTheWayIn`
  (hit at least 2 out from z = 5).
- Gate rounds with three gates: `RaidGameTests` (a gate at (5, 1, 2); the round moves 3 each way),
  `QaGates1103dGameTests` (exactly 3 fence gates found in the world, also turned and mirrored; dusk 12,500 and dawn
  23,500 edges; one gate left), `QaGatesLastGuard1111cGameTests` (the fence gates found in the box equal the hall's
  count), `QaGatesNoGuardLeft1111d`, `QaGatesDawnEdge1111d`, `QaGatesLastGuardNight1111dBug`,
  `QaGuardlessGates1110dBug`, `SeerWarningGameTests`, `WarningGameTests`, `QaSiegeWarnings1031d`: gates > 0, all shut
  or open as the round says.
- Portcullis on a warned day: `WarnedHallGoneGameTests`, `QaWarnedHallGone1111dBugTests`,
  `QaWarnedHallGoneVerify1111dGameTests` (bars dropped at noon and drawn up; the player's own bars in the slots stay;
  counts all iron bars round the Gatehouse), `QaVillageBugs1111cGameTests`, `QaSiegeGameTests` (the portcullis waits
  for the first dawn after, through a restart).
- `QaStationsAfterRaid1115dGameTests`, `QaStandDown1114dGameTests`: an archer takes a Gatehouse or Wall Tower station
  and stands down after the raid (Gatehouse at x 9..19, a Wall Tower at x 21).
- `WallGameTests.bothWallKitsLoadWithTheirBlueprintsAndRanks`: the stone kit's gate is `aliveworkplace:gatehouse`;
  `aSquareWallLineGetsSegmentsFourTowersAndAGateOnTheRoad`: one gate where the street crosses.
- `QaWonders1101GameTests`: the Great Wall asks for "finished gatehouse x1" (by id).
- The owner's rules (`check.py`, including a way up to every standing level) and STYLE.md's checks; `holes.py` must
  pass.

## Free
Everything not listed above: how it looks and what it is made of (except the spruce fence gates and the iron bars at
their cells), how high it rises (within 16), how the walkway is reached, its rooms or towers, lights and trim, and the
barbican's form, as long as every pinned cell, the 11 x 5 and 11 x 10 plots, the sealed outside and the walkway's view
hold.
