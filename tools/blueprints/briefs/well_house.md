# Well House: the brief

Family id(s): `well_house` (one tier, never renamed)   Variants: village (none drawn: a village's style is applied to
the drawing automatically by `BlueprintStyles`), modded (none to draw: `modded_library.py` makes
`structure/modded/well_house.nbt` from the vanilla drawing; the ship step runs it again), looks (none)

Not in this family: `well` and `well_2` (the beauty decoration, with its own brief, `briefs/well.md`). The Well House
gives no beauty (it isn't in `Decorations.POINTS`) and does not end the drought's mood penalty (`drought.json`'s
`unless_built` lists only `well`, `well_2` and `fountain`). Those are code and data, not the designer's.

## What it is for
The Well Keeper's workplace, the village's answer to a drought (ROADMAP 32.17, 32.19). A jobless villager standing by
its cauldron, handed a shovel by a player, becomes a Well Keeper with that cauldron as his job site
(`Stations.choose`). From then on he fills one bucket at a time at the water source nearest his cauldron, and carries it
to the fields in a drought (wetting the farmland round where he pours) or, between droughts, to the village's
cauldrons. When a village has no Well Keeper and drought is switched on, the Village Hall's "What next?" suggests
building the Well House ("Drought can come here: build a Well House", with a cauldron as the icon). The player takes the
blueprint from the Blueprint Table (it is listed only while the config switch `wellKeepers` is on) and gives it to a
Builder. On the Village Map it is a CARE building (`VillageMaps`, by id). It has no upgrade.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `well_house` | The Well Keeper's one job block (a cauldron) and the water he fills his bucket at | Blueprint Table, in the one-tier list (`StarterBlueprints.ONE_TIER`); offered only while `wellKeepers` is on (`StarterBlueprints.EXPANSION_SWITCH`); no unlock file, no loot, no Steward rule. No `well_house_2`: none may be added (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase` counts exactly 64 upgrades) |

## Must have
- **Job block: exactly one cauldron, empty, and no other job block of any kind.**
  - Exactly one `minecraft:cauldron`; **zero** `minecraft:water_cauldron` (a cauldron drawn with water in it is a second
    kind and fails the test), and zero of every other block on the test's list: blast furnace, smithing table,
    grindstone, stonecutter, fletching table, cartography table, **barrel**, lectern, composter, smoker, brewing stand,
    loom, **crafting table**, beehive, jukebox (`AnsweringHousesGameTests.theHousesHaveWhatTheirJobsNeed`).
  - Exactly one block in the whole drawing that is any profession's job site, vanilla's or this mod's (bench, mailbox,
    drop box, sieve, compost bin, shop counter, and the rest registered in `ModVillagers`), and it is the cauldron
    (`QaAnsweringHousesGameTests.eachHouseHasOneJobSiteAndItIsItsOwnJobs`).
  - A spot a villager can stand on within **4.5 blocks** of the cauldron (`Stations.REACH`: a jobless villager there
    takes the nearest free cauldron in that reach). The build test pins that spot today at template (7, 3, 8), the
    cauldron at (8, 3, 9); the ship step moves both to the new drawing.
  - The cauldron can be **walked to from the ground outside**: within 2 blocks across and 1 up or down of a cell a
    villager reaches on foot (level, one up, up to three down, ladders; doors and trapdoors open)
    (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`).
- **Water the Well Keeper can actually dip his bucket in** (code: `WellKeeperWork.findWater`, `Walker.reach`):
  - He fills at the water source **nearest his cauldron** (searched outward by Manhattan distance, up to 24 blocks across
    and 6 up or down) whose block above isn't water. He then has to get his **eyes within 3.0 blocks of that water
    block's centre**, standing on a spot he can walk to.
  - So the water source closest to the cauldron must be one he can stand beside and reach. A source at the bottom of a
    deep shaft is out of his reach; if it is nearer the cauldron than a reachable one, he walks to it, finds no spot,
    and gives up. (No test checks this today; it is what the building is for.)
- **Every water cell is a source block** the builder pours from a water bucket (each costs one water bucket on the
  list), held in on every side and below so it never flows out of the build. Waterlogged blocks count as water.
- **The test's well and trough** (`AnsweringHousesGameTests.theHousesHaveWhatTheirJobsNeed`, written to 32.19's
  "a roofed well shaft down to water with the Well Keeper's cauldron, a trough and a bench"): see "Rules tests check"
  for the exact assertions. In function terms:
  - a **well shaft** one block across at its mouth, with a **water source at its bottom** and **two open (air) cells**
    straight above the water;
  - a **closed ring of eight kerb blocks** round the mouth, three above the water, the centre left open;
  - a **chain** hanging over the shaft (the test finds the shaft by the drawing's chain: draw **no other chain**);
  - a **trough** holding at least **3 water sources**, level with the kerb;
  - **seats** (at least 6 stair blocks of the kind the test counts) and a **roof** (stair blocks of the kind the test
    looks for, at or above y = 7).
- **No entities** in the drawing: no item frames, paintings, armor stands or mobs (`theHousesHaveWhatTheirJobsNeed`
  asserts the blueprint's entity list is empty).
- Beds and residents: **none**. No code counts beds here, and a bed would be the only kind of block that turns it into
  a home (a bed is a home POI, not a job site), so leave beds out.
- Storage: none read. No job or test reads a container in the Well House, and a barrel is forbidden (it is a job block).
  A chest would be allowed but serves no job.
- Furniture kinds a job or test needs: none. There is no `blueprint_furniture/well_house.json` today and none is
  needed. If the recipe records furniture spots anyway, each spot's blocks must be exactly the template's there
  (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`) and nothing small may stand on air.
- Anything else the code reads: nothing. No bell, sign, lectern or door is read for this family.

## Fits
- Plot: at most **18 wide (x) x 28 tall (y) x 22 deep (z)**. The build test places it at helper (12, 2, 8) in the
  30 x 30 x 30 `huge_area` (`AnsweringHousesGameTests.answeringHouseBuilt_well_house`), with the builder's bench and
  three chests at x 3..5. Leave a little ground spare round it for the path the builder lays. y = 0 sits on the clicked
  block; the front is the z = 0 side.
- **Material budget: the whole list fits three chests, at most 81 stacks** (each item rounded up to whole stacks; same
  test). **Build time:** one builder alone, with the build delay at 2, finishes it within **12,000 ticks**.
- Declared size: `StarterBlueprints.WELL_HOUSE` is `11, 13, 12` today. If the new size differs, the ship step changes
  that `Vec3i` to match (`AnsweringHousesGameTests.answeringHousesAreInTheBlueprintTable` and
  `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).
- Front and way in: the z = 0 side faces the player who places it. No door is required. Without a door, the builder's
  path starts one block in front of the middle of the z = 0 edge (`Paths.doorstep`'s fallback), so that spot should be
  ground a villager can walk onto, leading in to the cauldron. (A door drawn in moves the path's start to that door.)
- Neighbours: none planned. No Steward rule or village plan places it; the player sites it. The showcase gallery
  (`defences` scene) places builds 48 blocks apart.

## Rules tests check
Pinned to the drawing (the ship step moves coordinates and heights to the new drawing, keeping what each checks,
dropping none):
- `AnsweringHousesGameTests.theHousesHaveWhatTheirJobsNeed`, today word for word:
  - exactly 1 `cauldron`, 0 `water_cauldron` and 0 of every other block on its list (above); no entities;
  - the drawing's first `minecraft:chain` gives the shaft's x and z. At that x, z: `water` at y = 0, and air at y = 1 and y = 2;
  - exactly **8** blocks of the `#minecraft:stone_bricks` tag at y = 3 in the 3 x 3 round the shaft (so the centre
    isn't one);
  - at least **3** `water` at y = 3;
  - at least **6** `minecraft:spruce_stairs` anywhere (the benches);
  - at least one `minecraft:dark_oak_stairs` at y >= 7 (the roof).

  The block kinds above are **hard requirements** (lane-d-1117-1308: changing a test's named block would loosen it):
  the kerb ring is `#minecraft:stone_bricks` blocks, at least 6 `spruce_stairs` stand in the drawing, and at least one
  `dark_oak_stairs` is at y >= 7 (relative to the shaft's water at y = 0; the ship step may move heights, keeping
  each offset). Everything else about materials is free.
- `AnsweringHousesGameTests.answeringHouseBuilt_well_house` (generated by `builds()`): built from nothing at helper
  (12, 2, 8) in `huge_area` by one builder from exactly its supply list in three chests (at most 81 stacks), the site
  finishes within 12,000 ticks with the cauldron in place. Then a jobless villager at the standing spot (today template
  (7, 3, 8), spawned one above) is handed an iron shovel by a player, becomes a Well Keeper, and his job site is that
  cauldron.
- `QaAnsweringHousesGameTests.eachHouseHasOneJobSiteAndItIsItsOwnJobs`: one job-site block in all, the Well Keeper's.
- `QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`: the cauldron (and any chest or bed) can
  be walked to from outside.
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: the `.nbt`'s size equals the declared
  size, and every block in it is one a builder can place (none classified `SKIP`).
- `QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`: its material list matches the
  builder's plan and fits a written book (any drawing passes this if it is buildable).
- `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`: a standing lantern, candle or torch stands on a
  block whose top holds its middle; no dirt path or farmland under a solid block.
- `QaChairFacingGameTests` (both): a seat beside a table opens toward it, also turned and mirrored.
- Modded copy: `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy` (a copy must exist),
  `ModdedLibraryCompatTests.everyModdedCopyCanBeBuiltWithThePacksMods` (same size as the vanilla drawing) and
  `QaCarvedLibraryCompatTests.everyModdedCopyKeepsItsJobSites` (the cauldron at the same cell, no job site added): the
  ship step regenerates the copy with `modded_library.py`.
- Refresh: once the old drawing goes into `blueprint_history/well_house/`,
  `QaRefreshHistory1028GameTests.qaEveryShippedOlderDrawingIsOfferedARefresh` expects a building standing as the old
  drawing to be offered a refresh to the new one (the new drawing must differ, and fit the 30 x 28 x 30 area).
- By id only (the drawing doesn't matter): `AnsweringHousesGameTests.answeringHousesAreInTheBlueprintTable` (in the
  Table and `ONE_TIER`, named, CARE on the map), `whatNextSuggestsTheHouseOfAMissingAnsweringJob` and
  `everyNewSentenceHasItsText`, `QaAnsweringHousesGameTests` (What next? boundaries),
  `ExpansionBuildsGameTests.laterExpansionsBuildsWaitForTheirSwitch` (`wellKeepers`),
  `QaBuiltAdvancements1116bGameTests.everyShippedBlueprintHasItsAdvancement` (`advancement/built/well_house.json`).
- The owner's rules (`check.py`: posts at fence corners, lamps on tables or posts, crockery only by a stove, furnace,
  smoker or counter or on a table with a chair), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no
  CHECK line, and `holes.py` passes.

## Free
Everything not listed above: shape, plan, size within the plot and budget, materials, colors, style, roof form, what
holds the water and how much beyond the shaft and trough, where the cauldron, shaft, trough and seats stand relative
to each other (within the rules above), extra rooms or lean-tos, lighting, ground and planting.
