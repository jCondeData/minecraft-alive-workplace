# Wall Tower: the brief

Family id(s): `wall_tower`, `wall_tower_2` (never renamed)
Variants: village (none drawn), modded (none: there is no `structure/modded/wall_tower*.nbt`), looks (none).
No `blueprint_furniture/wall_tower*.json` exists today; none is needed.

## What it is for
The corner and mid-run tower of the **stone wall kit** (`data/aliveworkplace/wall_kits/stone.json`: segment
`stone_wall`, `corner_tower` `wall_tower`, gate `gatehouse`, for a **Town** and a **City**). Once a village was raided
in the last 7 days or a bandit camp is near, the Steward proposes a wall; approved, `city/Walls` lays the line out into
pieces and the village's builders put them up one by one, a Wall Tower at every corner and at least every 28 blocks of
wall. A Town that still has a palisade replaces it a piece at a time with this kit (the Wall Tower takes the
`palisade_tower`'s place). A player can also take it from the Blueprint Table's defences
(`StarterBlueprints.DEFENCES`, the creative tab) or find it in camp and lair chest loot (war camp, bandit camp, coven
hut, pirate ship, piglin hoard, pharaoh's tomb) and hand it to a Builder anywhere.

Finished, it does three jobs:
1. **Battle stations** (`guard/BattleStations`): in a siege, archer guards whose Guard Post is within 24 blocks climb
   to its top and shoot from there (24 blocks reach, 28 with Ramparts; a quarter more damage to a foe 3 or more blocks
   below).
2. **A day's warning** (`guard/Warnings`): a guard on the night watch, or a Scout, whose post is within 24 blocks (flat)
   of the tower's middle sees raids coming a day ahead. Any tier counts (`Warnings.isTower` walks the upgrades).
3. **A wall piece** (`threat/Sieges`): it makes the village walled, so a raid by a besieging culture is a siege; its
   door is a gate block the rams can break.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `wall_tower` | A tower with one door, a way up from inside, and battle stations on one level at its top | Blueprint Table defences; loot; the stone wall kit's `corner_tower` |
| II | `wall_tower_2` | A **roofed archers' gallery** on top: **two stations on each of the four outer sides**, every gallery station roofed | Upgrade of I (`BlueprintUpgrades`: id + `_2`), on the same origin and front; a plain click on a finished I lines it up over I exactly. Not a kit piece (the kit lays out level I only) |

## Must have (each level)
- **Job blocks:** none. No job reads a block in it.
- **Beds and residents:** none. **No bed in either level**: a bed head is counted by `StewardConditions.beds`, and a II
  with more beds than I would make the Steward's home-upgrade rule pick wall towers (`StewardConditions.addsBeds`).
- **Storage:** none read by code.
- **Furniture kinds:** none needed. (If the recipe writes furniture spots, each spot's blocks must be the template's
  blocks there, `FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`.)
- **Exactly one door** (both halves), on the ground floor, with its lower half within template x 0-4, y 0-3, z 0-4
  (`QaSiegeGameTests.aTowerDoorBreaksWholeWithNothingDropped` searches that box for it). It is the tower's only gate
  block: **no fence gates, no iron bars, no second door** anywhere in either level. `Sieges.isGate` makes every fence
  gate, door and iron bars block of a wall build something the rams hack at and the builders mend, the breach is the
  gate block nearest the raiders, and the night round (`guard/Gates`) never shuts a tower's fence gates (it isn't a
  gate build: `SiegeGameTests.theDefenceUpgradesAreKnown` asserts `!Sieges.isGated(wall_tower_2)`).
- **Battle stations** (read from the blueprint by `BattleStations.read`, once per blueprint). A station is a block of the
  template at template y >= 2 whose top face is sturdy (full block, top slab, upside-down stair...), with **no block of
  the template in the two cells above it**, and **not a merlon** (a station spot one block below and beside it makes it
  a merlon). The archer stands on the cell above it, so stations stand at template y >= 3.
  - Level I: its stations are **all on one level, the top**, and they are what an archer takes (today 7 free cells of
    the top floor).
  - Level II: on the gallery level, **exactly two stations on each outer side** (the x = 0, x = 4, z = 0 and z = 4
    rows, counted separately per side, so a corner cell counts for two sides), and **every gallery station has a block
    of the template somewhere in its column 3 or more above the block stood on** (roofed).
- **Sight from the top:** an archer on a station sees and hits raiders on the ground in front of the tower 22 to 25
  blocks out, and from the station on the raiders' side sees the ground just in front of the breach (`BattleStations.view`:
  a station right behind a merlon sees none of the near ground; the archer takes the one that sees most). So every side
  of the top must have open shooting cells at standing height.
- **Getting up:** villagers can't path up a ladder. A guard sent to a station walks until he is **within 3.5 blocks
  (flat) of it and at least 1.5 below**, then is set on it with a ladder's sound (`BattleStations`, `CLIMB_REACH`).
  So **every station must be within 3.5 blocks across of ground a villager can walk to** (inside through the door, or
  round the foot). A player still needs a way up from inside to the top; no code reads whether it is a ladder or
  stairs.
- **Level II keeps at least 60 % of level I's solid blocks** in the same cells with the same block states
  (`SiegeGameTests.theDefenceUpgradesAreKnown`). II grows out of I; it is drawn over I's origin and front.
- **Buildable:** every block one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`:
  no block `MaterialRules` classes as SKIP), and level I's materials fit in **4 barrels (108 stacks)** and build within
  20,000 ticks (`BuilderGameTests.buildsWallTower`).

## Fits
- Plot: **I: 5 x 10 x 5 (width x height x depth); II: 5 x 12 x 5**, exactly as `StarterBlueprints.WALL_TOWER` /
  `WALL_TOWER_2` declare (the sizes test above checks the `.nbt` against them). The footprint stays **5 x 5**: the wall
  layout reads the tower's x size as its width along the run and its z size as its depth outward, every test that
  stands a tower assumes 5 x 5, and II's side rows are x/z = 0 and 4. y = 0 sits on the clicked block's top; the front
  is the z = 0 side.
- Front and way in: **the door is on the z = 0 face.** `Walls` turns every piece so its z = 0 side faces the village and
  z = 4 faces out; a door on the outside face would be a gate block nearer the raiders than the gatehouse's gates.
- Neighbours (the **stone wall kit contract**, shared with the briefs for `stone_wall`, `palisade`, `palisade_gate` and
  `palisade_tower`; read in `city/Walls.layout`):
  - **Mid-run** (at least every 28 blocks): the tower's inside face (z = 0) sits on the wall line, and wall segments
    butt its two ends, the x = 0 and x = 4 sides, over the full depth of a segment (`stone_wall` is 3 deep,
    `stone_wall_2` 4 deep). Its z = 0 face looks into the village.
  - **At a corner**: the tower stands outside the corner, filling it outside both runs. One run's segments butt one end
    (x = 0 or x = 4, depending on which way the wall turns); the other run's segments butt the **z = 0 face's three or
    four cells next to that same end**. So at a corner the middle of the z = 0 face is against a wall segment, and the
    only free part of that face is the far one or two cells. The way in must still reach the door from the village side
    on a mid-run tower; at a corner, guards reach the top only by the 3.5-block climb rule above.
  - Each piece sits at its own ground height (`Walls.groundY`), so on a slope neighbours are at different heights. **No
    code reads walk heights or whether tower and segment walks meet**: guards never walk the wall, they climb at the
    foot of their station. Lining the tower's levels up with the `stone_wall` walk (today a stand height of y = 4) and
    the `gatehouse` walk (today y = 6) is a design choice for the kit's designers, not a requirement.
  - A Town's stone tower replaces a finished `palisade_tower` on the same spot (`Walls.replaces`: any footprint overlap
    within 24 blocks up or down). `palisade_tower` is also 5 x 5.
  - The tower's middle (x 2, z 2) is where `Warnings` measures the 24 blocks from.

## Rules tests check
Coordinates below are today's pins; the shipping lane moves a pin to the new drawing, keeping what the test checks.
- `BuilderGameTests.buildsWallTower`: a builder builds level I whole from 4 barrels within 20,000 ticks.
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: both sizes as declared; every block placeable.
- `BattleStationGameTests.anArcherTakesTheTowersTopAndShootsFromIt` (tower at (5, 2, 13), hall to its back, south): the
  stations are all at one height (today 7, standing at template y 7) and include the one on the raiders' side with the
  most ground in sight (today template (1, 7, 3), behind the crenel at (1, 7, 4), not the merlon at (2, 7, 4)); the
  archer is on it within 400 ticks and from there hits a raider 22 blocks off; the blueprint is read once.
- `BattleStationGameTests.aSiegeBegunJustAfterAWatchGetsItsStationsAtOnce`, `anArcherTurnsFromAHiddenFoeToOneInSight`
  (from the station he sees and hits a raider in plain sight), `aBrokenStationIsGivenUpForAnother` (the block under his
  station is broken: he takes another station of the tower), `onTheGroundAnArcherClosesInFirst` (the test fills the
  top's station cells, today template x 1-3, z 1-3 at y 7, and then the tower must have **no** station left).
- `QaBattleStationGameTests.anArcherWhosePostIsJustBeyond24GetsNoStation` and
  `fromAStationAFoeJustBeyond24IsShotOnlyWithRamparts` (tower in the area's corner at (1, 2, 1)): it has stations; from
  one the archer has a raider about 25 blocks off in plain sight below.
- `QaSiegeGameTests.aTowerDoorBreaksWholeWithNothingDropped`: a door's lower half within template (0..4, 0..3, 0..4);
  with an axe raider just off the door's outer face it is the siege's breach, takes 20 axe blows, both halves go, nothing
  drops, the report says "The gate was broken", and the siege line says nothing of a portcullis (the tower has none).
- `QaSiegeGameTests.theHeightBonusStartsThreeBlocksBelowAStation`: the archer reaches the tower's top (south-east
  station) within 450 ticks.
- `QaStationsAfterRaid1115dGameTests`, `WarningGameTests`, `WarnedHallGoneGameTests`, `QaDayWarning1101GameTests`,
  `QaSiegeWarnings1031dGameTests`, `QaWarnedHallGone*`, `QaVillageBugs1111cGameTests`, `QaStandDown1114dGameTests`,
  `QaVillagerVerify1104c3GameTests` (the "keeps watch from a tower" line), `QaVillageReach1102GameTests`,
  `QaZoneNearerHall1115cGameTests`: stand a finished level I by a hall and rely on the 5 x 5 footprint, the warning
  source and the stations above; none pins another block.
- `SiegeGameTests.theDefenceUpgradesAreKnown`: II is a defence with stations, upgrades I, keeps >= 60 % of I's solid
  blocks unchanged, isn't a gate build; on the gallery level (today stations standing at template y 8, the block stood
  on at y 7) **exactly 2 stations on each of the rows z = 0, z = 4, x = 0, x = 4**.
- `QaDefenceUpgradeGameTests.theUpgradesAddWhatTheItemPromises`: II's gallery has >= 8 stations on its outer rows and
  a block 3 or more above every gallery station in its column.
- `QaDefenceUpgradeGameTests.anUpgradeLinesUpOnlyOverItsOwnFinishedBase`: II lines up over a finished I (same origin) on
  a plain click, not on a shift-click or over an unfinished one; clicked on a solid block of I at y >= 1; Gatehouse II
  never lines up over a Wall Tower.
- `WallGameTests` (`bothWallKitsLoadWithTheirBlueprintsAndRanks`, `aSquareWallLineGetsSegmentsFourTowersAndAGateOnTheRoad`,
  `builtPiecesStandWithTheirBlocksInTheWorld`, `noSecondTowerOpensOnATowerStillBeingBuilt`):
  the kit loads `wall_tower` as its corner tower; layout reads its size (towers at the corners, no tower overlapping
  another piece).
- The owner's rules (`check.py`: no lantern standing on a floor, fence/wall runs turn corners on a post, no stray
  crockery) with **zero** problems: `wall_tower` leaves `owner_rules_baseline.json` when it ships. And `holes.py` and
  STYLE.md's checks.

## Free
Everything not listed above: how it looks, its materials, shape, roof, crown, rooms below the top, where the way up
runs, lights, and whether its levels line up with the wall walks.
