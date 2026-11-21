# Barracks: the brief

Family id(s): `barracks`, `barracks_2`, `barracks_3` (never renamed; "Barracks", "Barracks II", "Barracks III" in
`en_us.json`, `blueprint.aliveworkplace.barracks*`). Variants: village (none: no worldgen piece), modded (none to draw:
`modded_library.py` makes `structure/modded/barracks*.nbt` from the vanilla drawing and the ship step reruns it,
`ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`), looks (none: the Table's styles, `BlueprintStyles`, restyle
the drawing by themselves).

## What it is for
The town's guard quarters. A **Guard** works at a **grindstone**: a jobless villager standing within 4.5 blocks of a free
grindstone and handed a sword becomes a Guard there (`Stations`, `REACH`). A **Weaponsmith** also works at a grindstone
(an iron ingot, or vanilla's own job pick). From a grindstone the Guard patrols, sleeps and takes better gear from the
**chests within 8 blocks across and 4 up or down of it** (`GuardPatrol`, `SupplyContainers.find`), and by day spars at
the nearest **Training Dummy** (`aliveworkplace:training_dummy`) **within 12 across and 4 up or down** of it, walking to
within 2.5 blocks of the dummy (`GuardPatrol.findDummy`, `DUMMY_RADIUS`, `train`), which earns experience up to Expert.
The village's Weaponsmith brings swords to the chests by each Guard's grindstone (`WeaponsmithWork`) and mends worn gear
found in them (`MendingWork`). The **Steward** gives a grindstone inside a finished Barracks (its box plus 2 blocks) to a
Guard, not a Weaponsmith, while no Guard works there (`StewardJobs.BUILDING_JOBS`, `BUILDING_MARGIN`). The Steward builds
one in the **Defences** zone of a **Town or bigger**: when the town is guards short and has none (`steward_rules/barracks.json`,
at most one, priority 57) or when a Guard or Weaponsmith has no workstation (`workplace_barracks.json`); and offers it as
the second choice, after the Guard House, when renewing an old house of a Guard or Weaponsmith (`steward_renewal/guard.json`,
`weaponsmith.json`: the first building that fits the old footprint plus a margin, front where the old door was). Its
beds are village beds (`Homes`). The village map draws it as a defence (`VillageMaps`, by id). It starts locked; the
questbook's "A Town" reward unlocks Level I (`BlueprintUnlocks.QUESTBOOK`), and the quest "Guards: the Barracks" is done
when it is built (`chapters.py`, `built("barracks")`). Advancements `built/barracks*` go by id. Each level is an upgrade
on the same spot, origin and front.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `barracks` | Two guards: two grindstones, two beds, their gear chests, a Training Dummy in reach | Locked; unlocked by the questbook's "A Town" reward; Steward rules `barracks` and `workplace_barracks` (Town+, Defences) |
| II | `barracks_2` | Two more guards: four grindstones and four beds in all, with chests and a dummy in reach of the new ones | Upgrade of I (`BlueprintUpgrades`: id + `_2`); sold by the builder who finished I (`UpgradeOffers`) |
| III | `barracks_3` | A **cellar exactly 4 deep** under II, reached from inside (today an armoury and lock-up); above the ground it is II, block for block | Upgrade of II; a builder who finished II sells it (`CellarGameTests.levelThreesLineUpOverTheirLevelTwos`); one of the eight `LEVEL_3_CELLARS` |

Ids, three levels and the I to II to III chain are fixed: `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` counts
exactly 64 starter upgrades and `CellarGameTests` exactly 8 cellared Level IIIs.

## Must have (each level)
- **Job blocks: grindstones, and no other workstation.** **Level I exactly 2, Level II and III exactly 4**, all above the
  ground. The counts are promised to the player (questbook, `chapters.py`: "Build the Barracks, with two grindstones and two
  bunks", "Do this at each of its two grindstones"; `docs/guide/barracks-build.md`: "Barracks II adds a wing with two more
  grindstones and two more bunks"); no test counts them, so a different count means the ship step rewrites those texts.
  No lectern, smithing table, barrel, composter, loom, stonecutter, blast furnace, smoker, cartography/fletching table,
  cauldron, brewing stand or mod workstation anywhere (cellar included): any of them would pull a jobless villager into
  another job. A Guard Post (`aliveworkplace:guard_post`) is a Guard's block too, but the quest and the Weaponsmith's
  workplace rule ask for grindstones. Each grindstone:
  - can be walked to from the ground outside, within a player's reach (2 across, 1 up or down) of a cell reached on foot
    (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`), with a floor cell a villager can stand
    on within 4.5 blocks (where the player hands the sword);
  - has **at least one chest within 8 across and 4 up or down** (the Guard's gear chest, the Weaponsmith's drop-off);
  - has **a Training Dummy within 12 across and 4 up or down**, with a standable cell within 2.5 blocks of the dummy that a
    villager can walk to (code, not a test: without it the guards never train).
- **Beds and residents: Level I exactly 2 beds, Level II and III exactly 4** (the same quest and guide texts; one per
  guard). Every bed's head reachable on foot (`QaBuildReachGameTests`), its foot one block from its head toward its facing
  (`BlueprintsStayGameTests`), and at home in the building turned and mirrored every way
  (`HomesSpecGameTests.everyBedOfEveryStarterHouseIsAtHomeTurnedAnyWay`). Beds stay whole turned and mirrored
  (`QaMirroredBuildsGameTests`).
- **Storage:** the chests above (at least one in reach of each grindstone). Every chest drawn empty, reachable on foot,
  standing against a wall, a solid block, a bed or another chest (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`).
  Double chests stay whole turned and mirrored (`QaMirroredBuildsGameTests`).
- **Training Dummies:** at least one per level (see the grindstones). It faces north or south (`TrainingDummyBlock`) and
  turns with the build (`QaTurnedModBlocksGameTests.everyFacingBlockTurnsWithItsBuild`).
- **Furniture spots** (`blueprint_furniture/<id>.json`, written from the recipe's `furniture.json` by `rebuild.py`): no
  test needs any kind here; today only the beds are spots. Every spot's blocks are exactly the template's there
  (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); nothing floats (`FloatingFurnishingsGameTests`); no
  standing lantern on a room's floor (`QaFurnishedRooms1031GameTests.noLanternStandsOnARoomsFloor`); every chair stair
  faces its table, turned and mirrored too (`QaChairFacingGameTests`). The ship step's furnished shots
  (`SCENE=rebuild_barracks`) find the rooms from the spots, so give every room at least one.
- **A way in on the z = 0 side** that a 1.95-tall villager walks through (`QaDoorwayClearanceGameTests`' rule, `check.py`);
  any upper floor reached by stairs or a ladder from inside.
- **III's cellar** (`BasementGameTests.basementsLoadUnderTheGround`, `QaBasementGameTests`, `CellarGameTests`):
  `StarterBlueprints.BARRACKS_3` has `basement` **4**: the cellar's floor at y = -4, the file `size.y + 4` tall; **at least
  one `minecraft:lantern` below y = 0** (the test looks for that block); a ladder or stairs below y = 0, and **one ladder or
  stair at y = -1 with passable cells above it at y = 0 and y = 1** (so II must already leave that cell at y = 1 open);
  **a trapdoor at y = 0 over a cellar column** (the hatch, III's only change above the ground); no dark standable cell
  anywhere in the cellar (block light above 0); walkable from the ground outside down to the cellar floor and back; every
  chest or barrel down there within 2 across and 1 up or down of a walked cell. No job block down there (`cellars.py`'s
  rule).

## Kept cells (every upgrade)
- II has the same origin and front as I, is at least its size in x, y and z, and **keeps at least 60% of I's non-air
  blocks as the same block in the same cell** (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`).
- **Every chest of I stays a chest in the same cell in II** (a builder never breaks a container while clearing,
  `MaterialRules.isProtected`; a chest left where II wants something else would block the upgrade). Keep I's grindstones
  and beds where they are too: guards already work and sleep there.
- **III above the ground is II exactly**, the same size; the only allowed change is a trapdoor at y = 0 over the cellar
  (`CellarGameTests.levelThreesLineUpOverTheirLevelTwos`). So the hatch's surroundings and the way to it are already in II.
  II has no basement (`QaBenchMovesCellars1104bGameTests.aBuildWithNoBasementIsNeverOfferedACellar`).

## Fits
- Plot (y = 0 sits on the clicked block; the front is the z = 0 side), from where the tests place each level:
  - Level I: **at most 13 wide x 13 deep** (`BuilderGameTests.buildsBarracks` at helper (9, 2, 9) in the 22 x 18 x 22
    `big_area`, the builder's barrels at x 1-5, z 4).
  - Levels II and III: **at most 24 wide x 21 deep x 13 tall above the ground**, plus III's 4-deep cellar
    (`CellarGameTests.barracksThreeDigsItsArmoury` places II at helper (6, 7, 9) in the 30 x 20 x 30 `huge_area`, on earth
    5 deep, under its ceiling). Since II is at least I's height, **every level is at most 13 tall**.
  - `QaReleaseRefresh1028GameTests` also puts III up at (7, 2, 2) in `huge_area` (at most 23 wide, 18 tall); the limits above
    are tighter.
- Declared sizes: `StarterBlueprints.BARRACKS` (13, 10, 11), `BARRACKS_2` (19, 10, 11), `BARRACKS_3` (19, 10, 11, basement
  4) today; the ship step changes them to the new drawing
  (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: sizes match, every block one a builder can place).
- Material budget: Level I fits the four barrels `buildStarter` stocks, **at most 108 stacks** (each item rounded up to
  whole stacks: "test needs more barrels").
- Build time: Level I from bare ground with exactly its materials within **30,000 ticks** (`buildsBarracks`), every block
  right at the end; III over a standing II on real ground, from the supply report, within **24,000 ticks**, the cellar dug
  out, II's chests still where they were, and **fewer than 60% of III's structure and decoration steps placed** (the cellar
  is the smaller part) (`barracksThreeDigsItsArmoury`).
- Front and way in: the z = 0 side faces the player who places it; when the Steward places it, its front faces the nearest
  road on the plan, else the hall (`Plots`); when it renews an old house, the front goes where the old door was
  (`Renewals`). Every upgrade keeps that front.
- Neighbours: placed by the Steward in a Defences zone, or anywhere by a player; no party walls. A smaller footprint lets
  the Steward renew more old guard houses into it (it must fit the old footprint plus a margin), but nothing requires it.

## Rules tests check
- `BuilderGameTests.buildsBarracks`: Level I built from bare ground from its own materials, every block right.
- `CellarGameTests.barracksThreeDigsItsArmoury`: III over a finished II (above), and `levelThreesLineUpOverTheirLevelTwos`
  (III's size equals II's, basement 4, no block above the ground changed but the hatch, the hatch present, offered by
  the builder who built II).
- `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (II over I, III over II) and
  `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`.
- `BasementGameTests.basementsLoadUnderTheGround`, `QaBasementGameTests` (way up through the ground floor, no dark spot,
  walkable with its chests), `QaBlueprintTableGameTests.everyBasementBuildPreviewsItsWholeDepth`.
- `QaReleaseRefresh1028GameTests.qaReleaseRefresh_barracks_3`: a Barracks III standing as 0.151.0 drew it (history key
  `cd1557d8c`) is offered "Refresh to the latest look" and refreshed to equal a fresh build of the new drawing (chests,
  beds and job blocks aside). The new drawing must differ from that old one (it will).
- By id only (nothing built): `WorkplacesGameTests` (`workplaceRule_barracks_guard` at Town, `_weaponsmith` at City;
  no Barracks wished in a hamlet), `StewardCivicRulesGameTests.guardsShortGetALookoutTowerAndATownABarracksFirst`,
  `PluralClusterGameTests` and `QaStewardWords1103cGameTests` (the "guards short" wording),
  `QaQuestbookClaims1116eBugTests.qaTheBarracksQuestGivesTheRaidSizeWithItsGuards`,
  `QaRaidSize1116dGameTests.qaTheBarracksQuestNamesEveryPartOfTheSize` (the quest's raid sentence).
- Library-wide: `QaBuildReachGameTests`, `QaDoorwayClearanceGameTests`' rule, `BlueprintsStayGameTests` (lamps on a block
  that holds them, no path under a solid block, beds whole), `QaMirroredBuildsGameTests`, `QaTurnedModBlocksGameTests`,
  `HomesSpecGameTests`, `QaFurnishedRooms1031GameTests`, `QaChairFacingGameTests`, `FloatingFurnishingsGameTests`,
  `FurnitureSpotGameTests`, `B648GameTests` / `QaWindowsAndWards1028BugTests` / `QaBuilders1028GameTests` (no pane or bar
  joined to air), `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `StyleGameTests.chimneysStayWholeInEveryStyle`,
  `ModdedLibraryGameTests`.
- The owner's rules (`check.py`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no CHECK line. The
  family is not in `owner_rules_baseline.json`, so it must come out clean.

## Free
Everything else: shape, plan, roof, materials, colors, storeys, rooms, where the grindstones, beds, chests and dummies
stand (within the reaches above), how many chests and dummies, what II adds besides its two guards' places, and what
the cellar holds.
