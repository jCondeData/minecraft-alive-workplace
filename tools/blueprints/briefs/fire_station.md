# Fire Station: the brief

Family id(s): `fire_station` (one tier, never renamed)   Variants: village (none drawn: a village's style is applied to
the drawing automatically by `BlueprintStyles`), modded (none to draw: `modded_library.py` makes
`structure/modded/fire_station.nbt` from the vanilla drawing; the ship step runs it again), looks (none)

## What it is for
The Firewarden's workplace, the village's answer to the village fire (ROADMAP 32.16, 32.19). A jobless villager standing
by its grindstone, handed a **water bucket** by a player, becomes a Firewarden with that grindstone as his job site
(`Stations.choose`; the grindstone is shared with the Weaponsmith, iron ingot, and the Guard, sword). From then on he
keeps two buckets of water, filled at the water nearest his grindstone, runs to any fire the village has noticed and
throws them over it; from Journeyman he rings the village's bell first and two off-duty villagers join a bucket brigade.
When a village is wooden enough for a fire (at least 3 blocks in 10 of its builds are wood), has no Firewarden and fires
are switched on, the Village Hall's "What next?" suggests building the Fire Station (with the grindstone as the icon).
The player takes the blueprint from the Blueprint Table (listed only while the config switch `firewardens` is on) and
gives it to a Builder. On the Village Map it is a DEFENCE building (`VillageMaps`, by id). It has no upgrade.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `fire_station` | The Firewarden's one job block (a grindstone), the water he fills his buckets at, the bucket racks, and a watch loft reached by ladder | Blueprint Table, in the one-tier list (`StarterBlueprints.ONE_TIER`); offered only while `firewardens` is on (`StarterBlueprints.EXPANSION_SWITCH`, `TablePreviewGameTests` and `ExpansionBuildsGameTests.laterExpansionsBuildsWaitForTheirSwitch` use it as a gated build); no unlock file, no loot, no Steward rule. No `fire_station_2`: none may be added (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase` counts exactly 64 upgrades) |

## Must have
- **Job block: exactly one grindstone, and no other job block of any kind.**
  - Exactly one `minecraft:grindstone`, and zero of every other block on the test's list: blast furnace, smithing
    table, stonecutter, fletching table, cartography table, **barrel**, lectern, composter, **cauldron**, **water
    cauldron**, smoker, brewing stand, loom, **crafting table**, beehive, jukebox
    (`AnsweringHousesGameTests.theHousesHaveWhatTheirJobsNeed`).
  - Exactly one block in the whole drawing that is any profession's job site, vanilla's or this mod's (bench, mailbox,
    drop box, sieve, compost bin, shop counter and the rest registered in `ModVillagers`), and it is the grindstone
    (`QaAnsweringHousesGameTests.eachHouseHasOneJobSiteAndItIsItsOwnJobs`).
  - A spot a villager can stand on within **4.5 blocks** of the grindstone's centre (`Stations.REACH`: a jobless
    villager there takes the nearest free grindstone in that reach). The build test pins that spot today at template
    (5, 1, 6), with the grindstone found by search; the ship step moves the spot to the new drawing.
  - The grindstone can be **walked to from the ground outside**: within 2 blocks across and 1 up or down of a cell a
    villager reaches on foot (level, one up, up to three down, ladders; doors and trapdoors open)
    (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`).
- **Water the Firewarden can actually fill his buckets at** (code: `FirewardenWork.findWater`, `Walker.reach`):
  - He fills at the water **nearest his grindstone**: the first water source (or water cauldron, which is forbidden
    here) found outward from the grindstone by Manhattan distance, up to **16 blocks across and 6 up or down**. Any
    source counts, waterlogged blocks included. He must then get his **eyes within 3.0 blocks of that water block's
    centre** from a spot he can stand on and walk to.
  - So the water source closest to the grindstone must be one he can walk up beside and reach. A waterlogged block, a
    sealed tank or a deep pool that is nearer the grindstone than the reachable water would be chosen first and leave
    him with nowhere to stand. (No test checks this today; it is what the building is for.)
  - **Every water cell is a source block** the builder pours from a water bucket (each costs one water bucket on the
    list), held in on every side and below so it never flows out of the build.
- **The test's floor, basin, racks, loft and ladder** (`AnsweringHousesGameTests.theHousesHaveWhatTheirJobsNeed`,
  written to 32.19's "a stone ground floor with the Firewarden's grindstone, a water basin and buckets on racks, under a
  timber watch loft"): see "Rules tests check" for the exact assertions. In function terms:
  - a **ground floor walled in stone bricks** (at least 80 plain or cracked stone bricks in the layers y = 1 to 4);
  - a **basin** of at least **2 water** blocks in the layer y = 1 (plain water, not waterlogged blocks);
  - **bucket racks**: at least **6 empty flower pots** (the "pails": a bucket has no block), each standing on a
    **top-half spruce slab** (the shelf);
  - a **watch loft** in the layers y = 6 to 7 with at least **20 spruce planks** and at least **8 glass panes** there
    (its windows to watch the village roofs from);
  - a **ladder** of at least **5 ladder blocks** (up to the loft).
- **The loft must be reachable**: `check.py` refuses an upper floor with no stairs or ladder up to it.
- **No entities** in the drawing: no item frames, paintings, armor stands or mobs (`theHousesHaveWhatTheirJobsNeed`
  asserts the blueprint's entity list is empty).
- Beds and residents: **none required**. No code counts beds or residents here (the Firewarden lives wherever he
  sleeps). A bed would be allowed but would make the building a home in the village's count; if one is drawn, its head
  must be walkable to (QaBuildReach), its foot must point at its head (`BlueprintsStayGameTests`) and its room must pass
  `check.py`'s light check.
- Storage: none read. No job or test reads a container here, and a **barrel is forbidden** (it is a job block). A chest
  would be allowed (it must then be walkable to) but serves no job.
- Furniture kinds a job or test needs: none. There is no `blueprint_furniture/fire_station.json` today and none is
  needed. If the recipe records furniture spots anyway, each spot's blocks must be exactly the template's there
  (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`) and nothing small may stand on air.
- Anything else the code reads: nothing in the drawing. A bell is **not** needed: the Journeyman Firewarden rings the
  nearest meeting point (a bell) within 32 blocks of himself, wherever it is. A bell drawn here would become that
  meeting point for villagers nearby; leave it out unless the design truly wants one.

## Fits
- Plot: at most **18 wide (x) x 18 tall (y) x 22 deep (z)**. The build test places it at helper (12, 2, 8) in the
  `huge_area`, which is really **30 x 20 x 30** (its ceiling is at helper y = 20; a taller drawing has its roof cut), so
  x 12..29, y 2..19, z 8..29; the builder's bench and three chests stand at x 3..5. Leave a little ground spare round
  it for the path the builder lays. y = 0 sits on the clicked block; the front is the z = 0 side.
- **Material budget: the whole list fits three chests, at most 81 stacks** (each item rounded up to whole stacks;
  `answeringHouseBuilt_fire_station`). **Build time:** one builder alone, with the build delay at 2, finishes it within
  **12,000 ticks** (the old drawing is about 11 x 14 x 11).
- Declared size: `StarterBlueprints.FIRE_STATION` is `11, 14, 11` today. If the new size differs, the ship step changes
  that `Vec3i` to match (`AnsweringHousesGameTests.answeringHousesAreInTheBlueprintTable` and
  `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).
- Front and way in: the z = 0 side faces the player who places it. No door is required. With a door, the builder's
  path starts outside the lowest door (`Paths.doorstep`); without one, one block in front of the middle of the z = 0
  edge, so that spot should be ground a villager can walk onto, leading in to the grindstone.
- Neighbours: none planned. No Steward rule or village plan places it; the player sites it. The showcase gallery
  (`defences` scene) places it after the walls and gates, beside the Well House and the Physician's House.

## Rules tests check
Pinned to the drawing (the ship step moves coordinates to the new drawing, keeping what each checks, dropping none):
- `AnsweringHousesGameTests.theHousesHaveWhatTheirJobsNeed`, today word for word:
  - exactly 1 `grindstone`, 0 of every other block on its list (above); no entities;
  - at least **80** blocks that are `minecraft:stone_bricks` or `minecraft:cracked_stone_bricks` (exactly those two;
    mossy or chiseled don't count) with y from 1 to 4;
  - at least **2** `minecraft:water` with y = 1;
  - at least **6** `minecraft:flower_pot` (empty) each directly on a `minecraft:spruce_slab` of type `top`;
  - at least **20** `minecraft:spruce_planks` and at least **8** `minecraft:glass_pane` with y from 6 to 7;
  - at least **5** `minecraft:ladder` anywhere.

  The block kinds and counts above are **hard requirements** (lane-d-1117-1308: changing a test's named block would
  loosen it). The heights are the test's: the ground floor's walls take in y 1..4, the basin is at y 1, the loft's walls
  and windows take in y 6..7. If the design's floors sit elsewhere, the ship step may shift those y values by the same
  offset, keeping every count and block kind. Everything else about materials is free.
- `AnsweringHousesGameTests.answeringHouseBuilt_fire_station` (generated by `builds()`): built from nothing at helper
  (12, 2, 8) in `huge_area` by one builder from exactly its supply list in three chests (at most 81 stacks), the site
  finishes within 12,000 ticks with the grindstone in place. Then a jobless villager at the standing spot (today template
  (5, 1, 6), spawned one above) is handed a water bucket by a player, becomes a Firewarden, and his job site is that
  grindstone.
- `QaAnsweringHousesGameTests.eachHouseHasOneJobSiteAndItIsItsOwnJobs`: one job-site block in all, the Firewarden's.
- `QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`: the grindstone (and any chest or bed)
  can be walked to from outside.
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: the `.nbt`'s size equals the declared
  size, and every block in it is one a builder can place (none classified `SKIP`).
- `QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`: its material list matches the
  builder's plan and fits a written book (any buildable drawing passes).
- `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`: a standing lantern, candle or torch stands on a
  block whose top holds its middle; no dirt path or farmland under a solid block; a bed's foot points at its head.
- `QaChairFacingGameTests` (both): a seat beside a table opens toward it, also turned and mirrored.
- Modded copy: `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy` (a copy must exist),
  `ModdedLibraryCompatTests.everyModdedCopyCanBeBuiltWithThePacksMods` (same size as the vanilla drawing) and
  `QaCarvedLibraryCompatTests.everyModdedCopyKeepsItsJobSites` (the grindstone at the same cell, no job site added): the
  ship step regenerates the copy with `modded_library.py`.
- Refresh: once the old drawing goes into `blueprint_history/fire_station/`,
  `QaRefreshHistory1028GameTests.qaEveryShippedOlderDrawingIsOfferedARefresh` expects a building standing as the old
  drawing to be offered a refresh to the new one (the new drawing must differ from the old).
- By id only (the drawing doesn't matter): `AnsweringHousesGameTests.answeringHousesAreInTheBlueprintTable` (in the
  Table and `ONE_TIER`, named, DEFENCE on the map), `everyNewSentenceHasItsText`,
  `QaAnsweringHousesGameTests.theFireStationIsSuggestedOnlyForAWoodenVillageWithoutAFirewarden` (What next? at a wood
  share of 0.3, not 0.2, not with a Firewarden, not with fires off), `TablePreviewGameTests` (no preview while its
  expansion is closed), `ExpansionBuildsGameTests.laterExpansionsBuildsWaitForTheirSwitch` (`firewardens`),
  `QaBuiltAdvancements1116bGameTests.everyShippedBlueprintHasItsAdvancement` (`advancement/built/fire_station.json`).
- The owner's rules (`check.py`: posts at fence corners, lamps on tables or posts, crockery only by a stove, furnace,
  smoker or counter or on a table with a chair; the old drawing breaks the lamp rule once and is baselined, so the new
  one must break none), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no CHECK line, and `holes.py`
  passes.

## Free
Everything not listed above: shape, plan, size within the plot and budget, materials beyond the test's named blocks,
colors, style, roof form, where the grindstone, basin, racks, ladder and loft stand relative to each other (within the
rules above), how much water beyond the basin, extra rooms, a hose tower or yard, doors, lighting, ground and planting.
