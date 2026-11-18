# Palisade Tower: the brief

Family id(s): `palisade_tower` (never renamed; one level, no `_2`)   Variants: village (none drawn; a style a Steward
zone names is applied by `BlueprintStyles` when one exists, and `Walls.stands` reads any style of it as the same piece),
modded (none), looks (none)

Not in this family: the segment `palisade` / `palisade_2`, the gate `palisade_gate`, and the stone kit's `stone_wall`
and `wall_tower` each have their own brief. This brief only says where they touch the tower.

## What it is for
The corner and spacer piece of the **Palisade** wall kit (`data/aliveworkplace/wall_kits/palisade.json`:
`"corner_tower": "aliveworkplace:palisade_tower"`, Hamlet to Village). When a raided village's Steward lays out a wall
(`city/Walls.layout`), a Palisade Tower goes at **every corner** of the wall line, at **the end of an open line**, and
**at least every 28 blocks** along a long run, and the village's builders build it like any other wall piece. In a
siege it is a **defence**: archers climb it and shoot from its top (`guard/BattleStations`), a ram may pick any door
or gate in it as the breach (`threat/Sieges`), and it counts toward "the village is walled". When the village becomes
a Town, the stone kit's `wall_tower` is built on the same spot in its place (a piece at a time). A player can also
take it from the Blueprint Table's defences (`StarterBlueprints.DEFENCES`). Sentries (posted Iron Golems,
`guard/WallSentries`) stand wherever the player's Patrol Map puts them; they read nothing from this drawing.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `palisade_tower` | A wall kit's corner tower: battle stations for archers, ladders up, part of the wall that counts as one building | Laid out and opened by the Steward's wall (`Walls.round`, config `stewardWalls`); in the Blueprint Table with the defences (locks by id in `BlueprintUnlocks`, nothing in the drawing). No upgrade: `palisade_tower_2` must not be added (the starter-upgrade count, 64, is pinned by `BuilderGameTests.starterUpgradesKeepMostOfTheirBase`). |

## Must have (each level)
- Job block(s): none. No code looks for a workstation, bell or bench in it.
- Beds and residents: none. Nothing counts beds here.
- Storage: none read. (Builders never break containers, so a container drawn in would stay behind when a Town's stone
  tower replaces it.)
- Furniture kinds a job or test needs: none. There is no `blueprint_furniture/palisade_tower.json`, and none is needed.
- **Battle stations** (what the tower is for; `BattleStations.read`, read from the drawing once per blueprint): a station
  is a block at blueprint **y >= 2** whose top face is sturdy (full top: planks, logs, a top slab; not a fence, a
  bottom slab, a ladder or a trapdoor), with **two blocks of air above it** in the drawing, and that is **not a merlon**
  (a spot one block above a station beside it is dropped). The tower must have stations, the more the better, and
  they should look out over the outside (the back, z = 4, and the sides): an archer picks the station that sees most
  of the ground 2 to 16 blocks out in front of the breach.
- **Stations a guard can get onto** (`BattleStations`, about line 501): villagers cannot path up ladders; a guard walks
  toward his station and is lifted onto it once he stands **at least 1.5 blocks below it and within 3.5 blocks of it
  horizontally**. So every station should be within 3.5 blocks (flat distance) of ground or floor a villager can walk
  to from the village side, e.g. a walk-in ground floor under the top, or stations close to the inside face.
- **Ladders, at least 2, at most 16, one of them at blueprint (2, 5, 3)** (see the tests below). Ladders are placed in
  the DECORATION stage, last; each must have a solid face to hang on so the builder can finish it.
- **Something solid in the centre column (x 2, z 2), above y = 0**: once built, the centre column's top is the tower's
  "ground" when the wall is laid out again, which is how the code recognises a tower standing on its own spot (B94,
  B283; `WallGameTests.noSecondTowerOpensOnATowerStillBeingBuilt` asserts the re-laid tower sits higher than the
  built one). Any non-air, non-liquid, non-replaceable block in that column does it.
- **Gate blocks (fence gates, doors, iron bars) are optional but live**: every one in a finished defence is on the
  siege's list (`Sieges.pieces`); the tower's door, if it has one, can be picked as the **breach** and rammed (as the
  Wall Tower's door is, `QaSiegeGameTests.aTowerDoorBreaksWholeWithNothingDropped`). Its fence gates are not opened
  and shut at night (only kit gates are, `Sieges.isGated` / `guard/Gates`). Without one, the tower can never be the breach.
- Every block must be one a builder places and that stays where it is put: none classified `SKIP`, nothing that pops
  off or flows (no liquids, no block hanging from air), so a build from exactly its list ends with nothing unfinished.
- Anything else the code reads: nothing. It is not a "tower" for night-watch warnings (`Warnings.towerIds` lists the
  Lookout Towers and `wall_tower` only); the village map shows it as a defence by id (`VillageMaps`).

## Fits
- Plot: **exactly 5 x 5 footprint (x by z), height at most 10**; declared `StarterBlueprints.PALISADE_TOWER = (5, 10, 5)`
  (if the height changes, the ship step changes that `Vec3i`). y = 0 sits on the clicked block; the front is the
  z = 0 side. Why exactly 5 x 5 x 10:
  - the layout spaces segments by the tower's width (`towerW` = size x), and the stone kit's `wall_tower` is 5 x 10 x 5:
    the same width puts the Town's stone tower on exactly this tower's spot, and a stone tower's build only clears its
    own 5 x 10 x 5 box, so anything of the palisade tower outside it would be left standing;
  - the corner tower reaches out from the line by its depth (size z) and back along the run by its width: the wall
    tests lay small square walls in the 30 x 30 test area (`WallGameTests.builtPiecesStandWithTheirBlocksInTheWorld`,
    a square of 10 round a hall at 14: a corner tower then fills helper x/z 0..4 exactly, so 5 is the most);
  - `B120GameTests.b120ACrewBuildsAPalisadeTowerOnTopOfAnotherUnhurt` stacks a second tower 9 above the first one's
    y = -1 base: it assumes a height of 10.
- Front and way in: the **front (z = 0) is on the wall line and faces the village**; the back (z = 4) faces outward
  (`Walls.rotationFor`: "its outside, the z = size - 1 side"; `Sieges` treats the back as the outside too). A way in
  on the front must avoid columns x = 0 and 1 (see Neighbours). No path is laid to it.
- Neighbours (from `Walls.layout`, each piece at its own ground height, foundation filled under it on a slope):
  - **Corner tower:** the next run's first segment butts onto the tower's **x = 0 side, at z = 0..1** (the segment is
    2 deep; `palisade_2` is 4 deep, its z = 2..3 a ground-level stake ditch). The previous run's last segment butts onto
    the tower's **front (z = 0), at x = 0..1** (x = 0..3 at ground level with `palisade_2`). The x = 4 side and the back
    face open ground outside the wall.
  - **Mid-run tower (every 28 blocks):** segments (or a gate) butt onto **both sides, x = 0 and x = 4, at z = 0..1**;
    the whole front faces the village.
  - **End of an open line:** the last segment butts onto the **x = 4 side at z = 0..1**.
  - Towers and gates never overlap any piece (`WallGameTests.aSquareWallLineGetsSegmentsFourTowersAndAGateOnTheRoad`).
  - Walk heights, for reference only (no code joins walks; each piece's stations are read on their own and guards are
    lifted onto them): today's `palisade` walk is stood on at y = 3 (a top slab at y = 2, on z = 0), `palisade_2` at
    y = 4, and `palisade_gate` has no walk. The rebuilt palisade and gate may change these; a walk that meets theirs is
    the designer's choice, not a rule.

## Rules tests check
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: the `.nbt`'s size equals
  `StarterBlueprints.PALISADE_TOWER` (5, 10, 5 today) and no block is classified `SKIP`.
- `WallGameTests.aBuilderBuildsThePalisadeTower` (`huge_area`, origin helper (6, 2, 6), no rotation, 4,000 ticks from
  morning, real builder from the village chest): it finishes; every non-air block of the drawing has something there;
  **more than 100 non-air blocks**; **a ladder at blueprint (2, 5, 3)**. The old one took about 1,500 ticks of work for
  153 blocks: much more work than that risks the timeout.
- `WallGameTests.noSecondTowerOpensOnATowerStillBeingBuilt` and `QaWallTowerGameTests` /
  `QaWallTowerBugGameTests` (a square-8 palisade wall round a hall at helper (14, 2, 14), the first piece is a corner
  tower): with every block but the ladders placed, the tower laid out again sits higher (the centre column); no second
  piece opens on it; saved and reloaded mid-DECORATION it is still one tower with its ladders open;
  `aBuilderKilledMidDecorationLosesNoLadders` needs **at least 2 ladders**; the ladder ledger counts **16 ladders** in the
  chest, so the tower may use at most 16; `aTowerBuiltForRealHandsOutEveryLadderItPlacesOrReturns` (6,000 ticks) builds
  it for real and every ladder step ends up a ladder.
- Both `WallGameTests.village` and `QaWallTowerGameTests.village` stock one chest with the old tower's materials (spruce
  logs, stripped spruce logs, spruce fences, planks, dark oak stairs and slabs, 16 ladders, 8 lanterns, stone, dirt).
  **The ship step must restock both to the new list**: that changes the setup, not what the tests check.
- `QaStewardMaterials1031GameTests.aPalisadeTowerBuiltFromExactlyItsListUsesExactlyItsList` (`big_area`, origin helper
  (6, 2, 9), 5,000 ticks, two chests): built from exactly its list it finishes, and every item is built, in a chest, in
  the bag or on the ground (nothing lost, nothing extra). The list must fit two chests.
- `B120GameTests` (`huge_area`, origin (12, 2, 12), free materials): one builder, a crew of three, and a crew building on
  top of another tower all finish with **no block of the plan unfinished** and nobody hurt;
  `QaHeightFreeUploadGameTests.aHelperInACrewFallsUnhurt` needs it to be still going when a second builder joins.
- Layout, by size only: `WallGameTests.aSquareWallLineGetsSegmentsFourTowersAndAGateOnTheRoad` (a tower covers each
  corner; tower centres within 4 blocks of the line), `QaWallGameTests.aTowerAtLeastEvery28BlocksAlongALongRun`,
  `WallGameTests.builtPiecesStandWithTheirBlocksInTheWorld`, and `QaWallTowerGameTests.assertNoTwoOverlap`.
- By id only (the drawing doesn't matter): `WallGameTests` (the palisade kit's `cornerTower` is `palisade_tower`),
  `QaWallGameTests` (kit file parsing), `SiegeGameTests` (DEFENCES are defences with stations).
- The owner's rules (`check.py`) and STYLE.md's checks: `rebuild.py --candidate` prints no CHECK line, and `holes.py`
  passes.

## Free
Everything not listed above: how many floors and where, how it is closed or open, where the stations are (as long as
there are reachable ones), how the ladders run beyond the one at (2, 5, 3), whether it has a door, materials, roof,
lighting and style.
