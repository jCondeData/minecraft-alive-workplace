# Lookout Tower: the brief

Family id(s): `lookout_tower`, `lookout_tower_2`, `lookout_tower_3`, `lookout_tower_4` (never renamed)
Variants: village (none drawn: a village's style is applied to the drawing automatically by `BlueprintStyles`), modded
(none to draw: `modded_library.py` makes `structure/modded/lookout_tower*.nbt` from the vanilla drawing, and
`ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy` needs one per level), looks (none).
Furniture spots today: `blueprint_furniture/lookout_tower_3.json` and `_4.json` (two bed spots each); I and II have none.

## What it is for
The village's watchtower. It gives the village **a day's warning of raids** and gives its archers **battle stations**.
A player buys Level I from a Journeyman Builder (level 3, 12 emeralds: `ModTrades`, `BlueprintUnlocks.BUILDER_SALES`;
the Town questbook chapter asks for it) or finds it in village chest loot (builder's, carpenter's, inn, kitchen and
storehouse chests), and hands it to a Builder. The Steward builds one by himself in a `defences` zone when the village
is **guards short** (fewer than one guard per 10 villagers) and has none (`steward_rules/lookout_tower.json`, priority
55, at most 1); in a Town the Barracks comes first. Finished, it does two jobs, in every style and tier
(`Warnings.isTower` and `BattleStations.hasStations` walk the upgrades down to `lookout_tower`):
1. **A day's warning** (`guard/Warnings`): a guard on the night watch, or a Scout, whose Guard Post (job block) is
   within 24 blocks (flat) of the tower's **middle** sees raids coming a day ahead. Without one the Defense page says
   "No lookout" and What next? says "build a Lookout Tower and post a guard by it".
2. **Battle stations** (`guard/BattleStations`): in a siege (or on a warned day), archer guards whose Guard Post is
   within 24 blocks climb to the tower's stations and shoot from there (24 blocks, 28 with Ramparts; a quarter more
   damage to a foe 3 or more blocks below). It is **not** a wall piece: it never makes a village walled
   (`Sieges.isDefence` is false for it), so its doors, bars and gates are never siege breaches.

Each level is an upgrade on the same spot and origin (`BlueprintUpgrades`: id + `_2`, `_3`, `_4`).

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `lookout_tower` | The tower: a way in on the ground, battle stations at its top, the warning source | Journeyman Builder (level 3), 12 emeralds; village chest loot; Steward rule `lookout_tower`; advancement `built/lookout_tower` |
| II | `lookout_tower_2` | **A guard's job block** at the tower (`StarterBlueprints` Javadoc: "A Guard Post at the foot of the ladder"), so the tower brings its own watchman: a guard working there is within 24 blocks of its middle. Today also a chest for gear and a bell | Upgrade of I |
| III | `lookout_tower_3` | **A second guard's job block and bunks** ("a guardhouse beside the tower: a second Guard Post and two bunks") | Upgrade of II |
| IV | `lookout_tower_4` | **A basement exactly 4 deep** under the build (`StarterBlueprints.Entry` basement 4; the wiki's basements page lists it as an armory and lock-up); nothing above the ground needs to change | Upgrade of III; a builder who finished a III sells it (`UpgradeOffers`, `BasementGameTests`) |

## Must have (each level)
- **Battle stations** (every level; read from the blueprint by `BattleStations.read`, once per blueprint). A station is
  a block of the template at template **y >= 2** whose top face is sturdy (full block, top slab, upside-down stair...),
  with **no block of the template in the two cells above it**, and **not a merlon** (a station spot one block below and
  beside it makes it a merlon). The archer stands on the cell above. Any cell that meets the rule is a station,
  wherever it is (a roof top or a ledge counts too). Archers take the station that sees most of the ground in front of
  the breach (`BattleStations.view`: one right behind a merlon sees none of the near ground), then the highest, so the
  top needs open shooting cells at standing height on every side. No test pins the Lookout Tower's stations, but this
  is the tower's job: every level must have them.
- **Getting up to a station:** villagers can't path up a ladder. A guard sent to a station walks until he is **within
  3.5 blocks (flat) of it and at least 1.5 below**, then is set on it (`BattleStations`, `CLIMB_REACH`). So **every
  station must be within 3.5 blocks across of ground a villager can walk to** (inside through the door, or round the
  foot). A player still needs a way up from inside; no code reads whether it is a ladder or stairs.
- **Job blocks.** A guard's job block is a **Guard Post** (`aliveworkplace:guard_post`) or a **grindstone** (the
  weaponsmith's: a villager takes it, is handed a sword and becomes the guard): `ModVillagers.GUARD` accepts either.
  I: none required. II: at least one, inside the build. III: at least two. IV: III's. Within 24 blocks of the middle
  (any spot in a build this size is).
- **Beds and residents.** Every bed is a home (`Homes.of`); bed heads are what `StewardConditions.beds` counts.
  - I and II: **no beds**. If II had more beds than I, the Steward's `homes_upgrade` rule (any family, priority 92,
    `StewardConditions.addsBeds`) would pick lookout towers to upgrade whenever beds are short.
  - III: the bunks for the guards (today 2). III adding beds over II means a Steward short of beds may upgrade a
    finished II; that is today's behavior and acceptable.
  - IV: **no more beds than III** (same rule).
  - Every bed's head lies inside the footprint and stays a home turned any way
    (`HomesSpecGameTests.everyBedOfEveryStarterHouseIsAtHomeTurnedAnyWay`); doors and beds keep both halves turned and
    mirrored (`QaMirroredBuildsGameTests.everyBuildsDoorsBedsAndDoubleChestsStayWholeTurnedAndMirrored`).
- **Storage.** No job reads a container in it. Any chest or barrel in IV's basement must be reachable (below).
- **Furniture spots.** None needed by any job. Each spot the recipe writes must be exactly the template's blocks there
  (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`) and nothing small may stand on air, furnished or not
  (`FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece`). It is not in `FurnishedHomesGameTests`' list
  of homes, so no room kinds are required.
- **A bell is optional.** If there is one, it is a vanilla meeting point (Festivals, the village fire alarm and paths
  use the nearest bell), so it should be where villagers can walk to.
- **Level IV's basement** (all of `StarterBlueprints.WITH_BASEMENTS`): drawn at the bottom of the file, its floor at
  file y = 0 and the ground floor at y = 4 (`rebuild.py ... --basement 4`), exactly 4 deep; at least one ladder or
  stairs and at least one `minecraft:lantern` below ground (`BasementGameTests.basementsLoadUnderTheGround`); some
  ladder or stair on the basement's top layer (y = -1) with two passable cells above it, so the way down opens from the
  ground floor (`QaBasementGameTests.everyBasementsWayDownComesUpThroughTheGroundFloor`); no dark standable cell down
  there (`noBasementLeavesADarkSpotForMonsters`, block light from the build's own lights); walkable from the ground all
  round the build and back out, and every chest or barrel below ground within 2 blocks across and 1 up or down of a
  spot that walk reaches (`everyBasementAndItsChestsCanBeWalkedToFromOutside`). Its above-ground part is III's.
- **Buildable:** every block one a builder can place (no block `MaterialRules` classes as SKIP).

## Kept cells (every upgrade)
- Each level has the same origin and front as the one below and is at least its size in x, y and z, and keeps **at
  least 60 % of the level below's solid blocks** in the same cells with the same block states
  (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`, which also counts exactly 64 starter upgrades: keep all three).
- Every block with block-entity data (chest, barrel, ...; beds, bells, signs, banners and campfires excepted) stays in
  the same cell in every later level: a builder never breaks those while clearing (`MaterialRules.isProtected`), so an
  upgrade that moves one can't finish.

## Fits
- Declared sizes (`StarterBlueprints`, checked against the `.nbt` by
  `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`; the ship step updates them):
  I `7, 14, 7`; II `7, 23, 7`; III `14, 23, 7`; IV `14, 23, 7` + basement 4 (file 27 high) today.
- Plot limits from the tests that stand it by coordinates (width x height x depth, x by z):
  - **Level I: at most 13 x 16 x 13** (`BuilderGameTests.buildsLookoutTower` builds it at (9, 2, 9) in the
    22 x 18 x 22 `big_area`; blocks above the area's top are never cleared). **At least 4 x 4**:
    `QaVillageReach1102GameTests` needs the corner of a tower whose middle is in a small zone to fall outside it.
  - **Levels I and II, placed raw at (0, 2, 0) in the 30 x 20 x 30 `huge_area` with the hall at (15, 2, 12)**
    (`QaWarningGameTests`, `QaThreatSwitches1110d*`): the box must not reach the hall's cell (x size <= 15 or z size
    <= 12), and the cell 8 east of the middle at ground level must lie outside the box (x size <= 16), because the test
    puts the archer's Guard Post there.
  - **Level IV** (and so III): the refresh test places it at (7, 2, 2) in `huge_area` (x size <= 23, z <= 28); if it
    is taller than today's the ship step moves `qaReleaseRefresh_lookout_tower_4` to `tall_area` as it did for the
    Townhouse and Clock Tower.
- y = 0 sits on the clicked block's top; the front is the z = 0 side.
- **Middle:** `Warnings` measures the 24 blocks from template (x size / 2, z size / 2) of whichever level stands.
  Grow III and IV so that middle is still where a guard by the tower would be.
- Front and way in: **a door on the ground floor facing the z = 0 side**, with a spot a villager can stand on outside
  it; paths start at the door nearest an edge of the build (`Paths.doorstep`). Every upgrade keeps that front.
- Neighbours: placed on its own in a `defences` zone (`VillageMaps` marks it DEFENCE); no party walls, no kit
  neighbours. A tower that already stands on a server is refreshed or upgraded in place, so a bigger box grows into
  whatever stands beside it.

## Rules tests check
Coordinates are today's; the ship step moves a pin to the new drawing, keeping what each test checks.
- `BuilderGameTests.buildsLookoutTower` (I): a builder builds it whole from exactly its material list in **4 barrels
  (at most 108 stacks)**, every block right, within **9,000 ticks** (today's I places 312 blocks).
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable` (all four) and
  `starterUpgradesKeepMostOfTheirBase` (II, III, IV; above).
- `BasementGameTests.basementsLoadUnderTheGround` and the three `QaBasementGameTests` checks (IV; above).
- `QaWarningGameTests.aPostTwentyFourBlocksFromALookoutTowerWatchesTwentyFiveDoesNot` (I): a post exactly 24 blocks
  east of the middle watches, 25 does not; `anUpgradedLookoutTowerStillWatches` (II);
  `thePlayersInTheVillageAndTheFarOwnerAreToldAStrangerFarOffIsNot` (I). All place the raw `.nbt` (see Fits).
- `QaThreatSwitches1110dGameTests.atThreatLevelZeroNoWarningAtLevelOneTheVillageIsWarned` and the three
  `QaThreatSwitches1110dBugTests` (I placed raw, the village's one tower, a post 8 east of its middle);
  `QaWarnAgain1110dGameTests` uses the same village.
- `QaVillageReach1102GameTests.defencesInAZonePastTheRadiusCountForSiegesAndWarnings` (I, turned half round): its
  middle counts for the village's zone, its corner lies outside it, and it never makes the village walled.
- `QaBuiltAdvancements1116bGameTests` (`ownerOnlineFarAwayIsTheOnlyOneAwarded`): finishing I at (1, 2, 1) in
  `big_area` awards `built/lookout_tower` to its owner.
- Turned and mirrored, all four levels: `QaMirroredBuildsGameTests` (doors, beds, double chests whole),
  `QaTurnedFramesGameTests.everyBuildsFramesAndPaintingsHangTurnedAndMirrored` (any frame or painting hangs on its wall),
  `QaTurnedModBlocksGameTests.everyFacingBlockTurnsWithItsBuild`.
- Refresh (36.21): `QaReleaseRefresh1028GameTests` `qaReleaseRefresh_lookout_tower_4` (IV built from 0.151.0's drawing,
  `blueprint_history/lookout_tower_4/cd1557d8c.nbt`, refreshed through the hall ends equal to a fresh build of the new
  drawing) and `QaRefreshHistory1028GameTests` (every shipped older drawing that differs is recognised). The ship step
  adds the old drawings of every level to `blueprint_history/` so standing towers are offered the refresh.
- By id only (the drawing doesn't matter): `StewardCivicRulesGameTests.guardsShortGetALookoutTowerAndATownABarracksFirst`,
  `PluralClusterGameTests`, `QaStewardWords1103cGameTests`, `BlueprintUnlocksGameTests`,
  `QaBlueprintLibraryVerify1108bGameTests`, `QaQuestbookClaims1116eGameTests`, `ModdedLibraryGameTests`,
  `QaDayWarning1101GameTests.whatNextAsksForALookoutUntilAGuardKeepsWatch`, `QaThreatFixes1104dGameTests`,
  `WarningGameTests`.
- Showcase: the `builders` scene (`ScreenshotHarness`, `06_lookout_tower`) has a builder put up I at (12, -60, 0)
  beside a Starter Cottage at x 0 and finish it within the scene's time.
- The owner's rules (`check.py`: no lantern standing on a floor, fence/wall runs turn corners on a post, crockery only
  where it belongs) with **zero** problems (the family isn't in `owner_rules_baseline.json`), STYLE.md's checks and
  `holes.py`: `rebuild.py --candidate` prints no CHECK line for any level.
- Player text the ship step keeps true: the Town questbook quest calls it "a stone tower with a lookout platform on
  top" (`aliveworkplace_story_4_town.snbt`, `tools/questbook/chapters.py`); if the new one is not stone, the ship step
  changes that sentence, not the design.

## Free
Everything not listed above: how it looks, its materials, shape, roof, crown, rooms, where the way up runs, where the
job blocks and bunks stand, the bell, what the basement holds, lights, and whether II and III's additions are rooms,
wings or floors.
