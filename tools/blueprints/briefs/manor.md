# Noble's Manor: the brief

Family id(s): `manor`, `manor_2`, `manor_3`, `manor_4` (never renamed; names "Noble's Manor", "Noble's Manor II", "...
III", "... IV" in `en_us.json`)
Variants: village (none drawn: the Table's style buttons and `BlueprintStyles` restyle the drawing automatically; I and
II must each take **at least one style**, `QaGranderHomes1107bGameTests.everyStyledTierLeadsToTheNextTierInItsOwnStyle`),
modded (none to draw: `modded_library.py` makes `structure/modded/manor*.nbt` from the vanilla drawing), looks (none).

## What it is for
The Nobles' home: a **grade III home at every level** (`homes/manor.json` gives the base blueprint grade 3, so I-III are
III; IV keeps its own tier 4, `HomeGrades`, `HomesSpecGameTests.aTierFourHomeIsGrandAndSurvivesAReload`). A household that wants grade III (Nobles; the questbook: "Nobles need a grand home like this
one") is content once its bed is in it; the hall's "What next?" tip for it goes away, and its people get the grand-home
mood (`Homes.TIER_3_MOOD`). It is **sold only by Builders**, never handed out by the Blueprint Table
(`StarterBlueprints.SOLD_ONLY`; it shows there as a "???" and its id never reaches the client): a builder sells Manor I for
**20 emeralds** once his village has a household of Burghers or higher (`ModTrades.builderHomes`, `MANOR_PRICE`), each
upgrade comes from the builder who finished the tier below (`UpgradeOffers`), the Burghers' market traders can bring it
for 12 on market days (`classes/burgher.json`), and the questbook's "Burghers" reward unlocks it
(`BlueprintUnlocks.QUESTBOOK`, `/workplace unlock`). The Steward builds one by himself in a `homes` zone when **one
household** wants grade III in a **Town** or bigger (`steward_rules/homes_class_manor.json`, priority 44). On the village
map it is a home (`VillageMaps`). Every bed in it is a home (`Homes.of`: the finished build a villager's bed is inside).

It is also **where the Noble's Ball is held** (`NobleBalls.venue`, 34.7): in a village with a Noble household every other
festival is a ball, and the guests gather (and dance, and see the gold fireworks) at Manor III's ballroom, else in the
middle of a Manor I or II, else at the Village Hall. From Level II **the home has its own garden**, which
`homes/manor.json` counts as **3 beauty** for the people who sleep there (enough for a Noble's beauty need on its own);
Level I has none. Its rooms are furnished (37.4): with a furniture mod each spot becomes that mod's piece, without one
the vanilla stand-in. Each level is an upgrade on the same spot and origin.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `manor` | A grade III home for one household: **three beds** (the questbook: "Build the Manor: three beds, a grand home from its first tier"); every furnished-room kind; a hall whose middle is the ball's fallback venue | Sold by a builder (20 emeralds) once the village has Burghers or higher; questbook "Burghers" reward; Steward rule `homes_class_manor` (Town rank); market traders (12) |
| II | `manor_2` | **The home's own garden** (the data file counts 3 beauty from tier 2; no code counts its blocks, but the level exists to add it: `StarterBlueprints.MANOR_2`'s note and the wiki say so) | Upgrade of I (`BlueprintUpgrades`: id + `_2`); sold once, by the builder who finished I |
| III | `manor_3` | **A ballroom**: the room the Noble's Ball gathers in, its floor's middle at the cell `NobleBalls.BALLROOM` names | Upgrade of II; sold by the builder who finished II |
| IV | `manor_4` | A **cellar exactly 4 blocks deep** under III, reached from inside (the ballroom still counts: tier 4 >= `BALLROOM_TIER`) | Upgrade of III; sold by a builder who finished a III (`BasementGameTests.basementsLoadUnderTheGround`) |

## Must have (each level)
- **Beds and residents.** **Exactly three beds in I** (the quest text; more or fewer means changing it in
  `tools/questbook/chapters.py` and the `.snbt`), and **an upgrade never removes or moves a bed**, so II-IV have at least
  those three. Every bed's head inside its level's box, also turned and mirrored
  (`QaGranderHomes1107GameTests.everyBedOfTheManorGivesItsGrade`: I, II, III placed `CLOCKWISE_90` + `LEFT_RIGHT` at
  (1, 2, 1) in `huge_area`; every bed head inside the test area, grade 3, 3, 3, garden beauty 0, 3, 3, the home's name
  the blueprint's display name, unchanged after a save and reload). Every bed has its head to a wall or a window in one
  (`FurnishedRoomsGameTests.everyBedHasItsHeadToAWall`) and is a home turned or mirrored any way
  (`HomesSpecGameTests.everyBedOfEveryStarterHouseIsAtHomeTurnedAnyWay`).
- **Every bed, chest, barrel and job block can be walked to from the ground outside** (within two blocks across, one
  up or down, of a spot reached on foot, doors and trapdoors opened; `QaBuildReachGameTests`, which takes every build
  including the sold-only ones). Every floor with a bed is reached from inside, by stairs or a ladder (`check.py`).
- **The ball's venues** (`NobleBalls`):
  - **Level III (and IV above ground): a ballroom whose floor's middle is `NobleBalls.BALLROOM`, today (20, 1, 11)**
    from the origin. That cell must be a standable floor cell (air at y 1 and 2, a sturdy floor under it) inside a room
    reachable from the front door, with room round it for a village's guests. The cell is a constant in the code: if the
    new ballroom's middle is elsewhere the ship step changes `NobleBalls.BALLROOM` and the test pins below; it must stay
    inside III's box turned and mirrored (`QaMirroredManorBallGameTests.theBallGathersInsideAManorPlacedAnyWay`).
  - **Levels I and II: the cell (width / 2, 1, depth / 2)** (integer halves of each level's own declared size) is where
    the guests gather when there is no ballroom (`NobleBalls.venue`; `ManorGameTests.theBallGathersInTheBallroomWhenThereIsOne`
    asserts exactly that cell; the mirrored QA test that it lies inside the box every way placed). It must be a
    standable floor cell inside a room reachable from the front door (the wiki and `NobleBalls` call it "its hall"),
    **in I and again in II**, whose middle moves when II grows deeper or wider.
- **Job blocks.** None is required: no code or test reads a job block in the Manor (a workstation that is there must be
  reachable, above). Every block with block-entity data (chest, barrel, smoker, jukebox, decorated pot...) stays in its
  cell in every later level (see "Kept cells").
- **Storage.** None is read by any job. Every chest stands against a wall, a solid block, a bed's foot or its other
  half, never alone in a room (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`). Doors, beds and double
  chests stay whole turned and mirrored (`QaMirroredBuildsGameTests`).
- **Furniture spots** (`blueprint_furniture/<id>.json`, written by the recipe's `furniture.json` through `rebuild.py`):
  - **Every level, I to IV**, has at least one spot of each kind **bed, chair, table, nightstand, lamp, couch, shelf,
    crockery, clock, picture, blinds** (`FurnishedHomesGameTests.everyHomeHasItsFurnishedRooms`, the FULL set).
  - Without a furniture mod every level plans as its vanilla build (`FurnishedHomesGameTests.withoutFurnitureModsEveryHomeIsVanilla`).
  - Each spot's blocks are exactly the template's blocks there (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`);
    nothing stands on air (`FurnishedRoomsGameTests.everyFurnishedPieceHasWhatItStandsOn`, `FloatingFurnishingsGameTests`);
    no piece in a door's way (`noFurnitureStandsInADoorsWay`, including the doorways an upgrade cuts); every piece on a
    room's floor has block light above 7 from a lantern hung or on a table (`everyFurnishedRoomHasALight`); no standing
    lantern on a furnished room's floor (`QaFurnishedRooms1031GameTests.noLanternStandsOnARoomsFloor`).
- **At least 5 seats at tables in Manor I, each facing its table**
  (`QaChairFacingGameTests.turningOneOfTheManorsChairsRoundIsCaught`: `seen >= 5`). The test recognises a seat and a
  table by their blocks: a seat is a **bottom-half stair** with nothing solid over it and a floor under it; a table
  beside it is a **fence or wall topped with a pressure plate or carpet**, or a **top slab** with no full block on it.
  Seats made any other way don't count toward the 5. Every listed build's seats face their table, also turned four ways
  and mirrored (the same class's other tests).
- **Chimneys: exactly 2 in Manor I, at least 1 in II and III**, a chimney being a campfire standing on a column of full
  solid blocks that stays solid in every style (`StyleGameTests.chimneysStayWholeInEveryStyle`: `== 2` for `manor`; a
  campfire on a floor or slab doesn't count).
- **Glass:** in every level no pane or bar is joined on one side only with air on the other (`B648GameTests.b648NoShippedGlassEndsInTheAir`,
  `check.py`'s `open_panes`).
- **A front door** on the ground floor at the z = 0 side, and a walk in by it that the doorway test can follow (below).
- **Level IV's cellar** (`BasementGameTests.basementsLoadUnderTheGround`, `QaBasementGameTests`, all of
  `StarterBlueprints.WITH_BASEMENTS`): drawn at the bottom of the file, its floor at file y = 0 and the ground floor at
  y = 4, exactly 4 deep (the file is III's height + 4); at least one ladder or stairs and at least one
  `minecraft:lantern` below ground; a ladder or stair on the cellar's top layer climbs out through the ground floor
  (room at y 0 and 1 above it); no dark floor cell (block light above 0 on every standable cell); walkable from the
  ground outside down to the cellar floor and back, and every chest or barrel down there within reach of a walked spot.
  Its above-ground part is III's, cell for cell.

## Kept cells (every upgrade, and every refresh)
- Each level has the same origin and front as the one below and is at least its size in x, y and z
  (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`).
- **At least 60 % of the level below's non-air blocks stay the same block in the same cell** (same test).
- Every block with block-entity data stays in the same cell as the same block in every later level (a builder never
  breaks those while clearing, `MaterialRules.isProtected`), and every bed stays where it was.
- **A refresh (36.21) never moves a container, a jukebox, a slept-in bed or a job block** (`Refreshes.kept`). The
  owner's live server has Manors built from the older drawings (the live `.nbt` and `blueprint_history/manor*/`
  `42864073a`, `4c929e1a2`, `aec09cb26`). Their cells, template (x, y, z); **the new drawing must not put a door, a
  stair, a ladder or a walkway on any of them**, nor anything a kept block would block, and is better holding the same
  block there:
  - Manor I and II: chests (4, 1, 14) facing north, (13, 6, 8) facing east, (3, 6, 10) facing south (live), (5, 6, 8)
    facing west (history); barrels (3, 1, 14), (5, 1, 14), (13, 1, 14), (15, 6, 14); smoker (15, 1, 8) facing west (a
    butcher's job site).
  - Manor III: all of I's, and barrel (11, 5, 3); jukebox (20, 1, 14) (a block entity, and our Bard's job block).
  - Manor IV (file y = template y + 4): all of III's moved up 4, and in the cellar chests (5, 1, 14), (9, 1, 14) facing
    north and decorated pots (4, 1, 14), (10, 1, 14). **A decorated pot is not skipped** by the refresh-equals-fresh-build
    check (`RefreshMatchesFreshBuildGameTests.differences` skips only chests and POI blocks), so those two cells should
    hold decorated pots in the new IV.
  - Old bed heads (kept when slept in): live (3, 6, 8), (4, 6, 8), (15, 6, 8); history (3, 6, 9), (4, 6, 9),
    (15, 6, 9), all facing north, feet one more in z; IV the same 4 higher. Better not a door or a walkway either.

## Fits
- Plot (y = 0 sits on the clicked block; the front is the z = 0 side), from where the tests place each level:
  - Level I: **at most 25 wide x 18 tall x 24 deep** (`DoorwayHeadroomGameTests` places it at (5, 1, 6) in the
    30 x 20 x 30 `huge_area`; `BuilderGameTests.buildsManor` and `QaGranderHomes1107bGameTests` at (1, 2, 5) there).
  - Levels II and III: **at most 29 wide x 18 tall x 25 deep** (`buildsManorII`, `buildsManorIII` at (1, 2, 5) in
    `huge_area`, the builder's bench, chest and barrels at z <= 4 in front of it; `everyBedOfTheManorGivesItsGrade` places
    III turned a quarter from (1, 2, 1), so its footprint also fits 29 x 29 turned). 18 tall is the area's ceiling above
    y = 2: blocks built higher are never cleared between tests.
  - Level IV: III above the ground plus the 4-deep cellar.
- Declared sizes: `StarterBlueprints.MANOR` (`19, 18, 17`), `MANOR_2` (`19, 18, 24`), `MANOR_3` (`26, 18, 24`),
  `MANOR_4` (`26, 18, 24`, basement 4) today; if a size changes the ship step changes those `Vec3i`s
  (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: sizes match, every block one a builder
  can place). A changed size moves the I/II ball venue too (above).
- Material budget: I, II and III each fit the four barrels `buildStarter` stocks (**at most 108 stacks**, each item
  rounded up to whole stacks).
- Build time: I within 60,000 ticks, II within 80,000, III within 100,000 (`buildsManor`, `buildsManorII`,
  `buildsManorIII`); I with exactly its materials across a save and reload mid-STRUCTURE within 70,000
  (`QaGranderHomes1107bGameTests.aManorBuiltAcrossASaveAndReloadEndsFinishedAndSellsItsII`).
- Front and way in: the z = 0 side faces the player who places it (the outline's gold edge) and the road of a Steward
  plot; the front door goes there on the ground floor; every upgrade keeps that front.
- Neighbours: a home in a Steward `homes` zone (`VillageMaps` counts `manor` as HOMES); the Steward finds it a plot.
- Ship step: `generate.py` still saves `manor`, `manor_2`, `manor_3` from `houses.py` and `basements.py` saves `manor_4`;
  the ship step stops those saves (as 49.4 and 49.5 did) and drops `holes.py`'s `ALLOW` entries for the old fountain
  cells, `(9, 4, 17)` in II-IV, unless the new drawing needs the same.

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `NobleBalls.BALLROOM` (20, 1, 11), and in `ManorGameTests.theBallGathersInTheBallroomWhenThereIsOne` the ballroom
  `origin.offset(20, 1, 11)` and the turned one `offset(-11, 1, 20)` (`CLOCKWISE_90` of the same cell), and the comment
  "inside the wing (x 17-23, z 8-14)".
- `ManorGameTests.BED` (3, 6, 10) from the origin: a cell inside I, II and III that a bed of the drawing should stand
  on (`theManorIsGradeIIIAndItsGardenCountsThreeBeauty`: grade 3 in plain, sandstone I, cherry II, grand III; beauty 0
  in I, 3 in II and III; a neighbour outside gets 0).
- `DoorwayHeadroomGameTests.aVillagerWalksInByTheManorsFrontDoorOntoItsStair` and
  `aVillagerOpensTheManorsShutFrontDoorAndClimbsItsStair` (B173, B203): Manor I at (5, 1, 6) in `huge_area`; a villager
  walked from in front of the door, today template (9, 1, 3), to just inside it, (9, 1, 8), succeeds once past that
  z **with its feet at least 0.45 above the ground floor**: today it steps onto the grand stair's first step right
  inside the door. The new drawing needs a raised cell (a step up of half a block or more) on the walk in from the front
  door where the test can end, with headroom over it right by the door (`check.py`'s doorway rule; the B203 copy keeps
  the door shut as built).

Built whole (every block right, 0 skipped, supplies exact): `BuilderGameTests.buildsManor`, `buildsManorII`,
`buildsManorIII`; `QaGranderHomes1107bGameTests.aManorBuiltAcrossASaveAndReloadEndsFinishedAndSellsItsII` (finished,
exactly its materials, beds grade III, its builder then sells II).

Seats, chimneys, beds: `QaChairFacingGameTests.turningOneOfTheManorsChairsRoundIsCaught` (5+ seats at tables in I);
`StyleGameTests.chimneysStayWholeInEveryStyle` (2 in I); `QaGranderHomes1107GameTests.everyBedOfTheManorGivesItsGrade`.

Shared checks that read the drawing (every shipped build): `FurnishedHomesGameTests`, `FurnishedRoomsGameTests`,
`FurnitureSpotGameTests`, `FloatingFurnishingsGameTests`, `QaFurnishedRooms1031GameTests`, `QaMirroredBuildsGameTests`,
`HomesSpecGameTests.everyBedOfEveryStarterHouseIsAtHomeTurnedAnyWay`, `QaBuildReachGameTests`, `B648GameTests`,
`BasementGameTests`, `QaBasementGameTests` (IV), `BuilderGameTests.starterUpgradesKeepMostOfTheirBase`,
`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`, `QaRefreshHistory1028GameTests` (every older drawing in
`blueprint_history/manor*/` that differs from the new one is recognised; `blueprint_history/` already holds them).

By id only (the drawing doesn't matter beyond the rules above): `ManorGameTests` (trades, sold-only, the garden files,
the Steward rule), `QaMirroredManorBallGameTests`, `HomeGradeGameTests`, `QaGranderHomesGameTests`,
`QaGranderHomes1107GameTests`, `QaGranderHomes1107BugTests`, `QaBuilderFixes1107bGameTests`,
`QaGranderHomes1107bGameTests.everyStyledTierLeadsToTheNextTierInItsOwnStyle` (I and II each take a style),
`BlueprintUnlocksGameTests`, `QaBlueprintUnlocksGameTests`, `QaBlueprintLibraryVerify1108bGameTests`,
`TablePreviewGameTests`, `ClassPerkGameTests`. (The `*_manor_3` ids in the Legend tests are the tests' own empty
blueprints, not this drawing.)

The owner's rules (`check.py`: posts at fence corners, lamps on tables or posts, crockery only by a stove, furnace,
smoker or counter or on a table with a chair, upper floors reached by stairs or a ladder, doorway headroom; `open_panes`),
STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no CHECK line for any level, and `holes.py` passes.

## Free
Everything not listed above: shape, plan, rooms, roofs, materials, colors, style, how many storeys, what the garden
holds and where it lies, whether there is a gatehouse, a forecourt or walls, what III adds besides the ballroom, what
the cellar holds, which floor holds which bedroom, where furniture, chests and the chimneys stand within the rules
above, lighting.
