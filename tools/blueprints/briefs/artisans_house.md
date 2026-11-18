# Artisan's House: the brief

Family id(s): `artisans_house`, `artisans_house_2`, `artisans_house_3` (never renamed; names "Artisan's House",
"Artisan's House II", "Artisan's House III" in `en_us.json`; hidden advancements `aliveworkplace:built/artisans_house*`)
Variants: village (none drawn: the Table's style buttons and `BlueprintStyles` restyle the drawing automatically; I and II
must each take **at least one style**, and I must take **`cherry`**, see "Rules tests check"), modded (none to draw:
`modded_library.py` makes `structure/modded/artisans_house*.nbt` from the vanilla drawing and the ship step reruns it),
looks (none).

## What it is for
The Artisans' home: a **grade I home** (no `homes/` file names it, so its grade is its tier: I is grade I, II grade II,
III grade III, `HomeGrades.grade`). A villager whose class wants a grade I home (an Artisan, or a Peasant household that
has everything else the Artisan class needs) is content once their bed is in it, and the hall's "What next?" tip for them
goes away; the II is what a Burgher wants. It is **sold only by Builders**, never by the Blueprint Table
(`StarterBlueprints.SOLD_ONLY`): every Builder from **Apprentice** (level 2) up sells Artisan's House I for **10
emeralds** (`ModTrades.builderHomes`, `ARTISANS_HOUSE_PRICE`), and each upgrade comes from the builder who finished the
tier below (`UpgradeOffers`). The Steward builds one by himself in a **`homes` zone** when **one household** wants a
grade I home (`steward_rules/homes_class_artisans_house.json`, priority 48, every 3 days at most), and his `homes_class`
rule upgrades a household's Artisan's House when its next tier is the grade they want. On the village map it is a home
(`VillageMaps`: `Kind.HOMES`). Every bed in it is a home (`Homes.of`: the finished build whose box a villager's bed is
inside). Its rooms are furnished (37.4): with a furniture mod each spot becomes that mod's piece, without one the
vanilla stand-in. Each level is an upgrade on the same spot, origin and front.

No job is worked here: no code reads a workstation, job block or container in this family. (The old one had a
workroom; that is story, not a requirement.)

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `artisans_house` | A grade I home for **exactly two** sleepers; the furnished-room kinds below; a chimney | Sold by any Builder level 2+ (10 emeralds); Steward rule `homes_class_artisans_house` (homes zone, one household wanting grade I) |
| II | `artisans_house_2` | Grade II (from the tier, not from anything drawn); **exactly three** beds | Upgrade of I (`BlueprintUpgrades`: id + `_2`); sold once, by the builder who finished I |
| III | `artisans_house_3` | Grade III; a **cellar exactly 4 deep** under II, reached from inside; above the ground it is II, block for block | Upgrade of II; sold once, by the builder who finished II (`CellarGameTests.levelThreesLineUpOverTheirLevelTwos`) |

## Must have (each level)
- **Beds and residents.**
  - **Level I: exactly 2 bed heads** (`QaGranderHomes1107GameTests.anArtisansHouseBuiltAcrossASaveAndReloadEndsFinishedWithExactlyItsMaterials`:
    "the Artisan's House has N beds, the wiki says two"; the `grand_houses` showcase scene checks 2 and grade I).
  - **Level II: exactly 3 bed heads** (the `grand_houses` showcase scene checks 3 and grade II; II keeps I's two in their
    cells, see "Kept cells").
  - **Level III: II's three**, nothing more above ground (III above the ground is II exactly), and **no bed in the
    cellar** (`QaGranderHomes1107GameTests.everyBedOfTheArtisansHouseGivesItsGrade` places III turned and mirrored with its
    above-ground box at y = 2 of `huge_area`, and every bed head must lie inside the test area: a cellar bed would be
    below it).
  - Every bed head inside its level's box (each gives grade 1, 2, 3 in I, II, III, also turned 90 and mirrored, and
    after a save and reload; the home's name is the blueprint's display name).
  - Every bed has its head against a wall or a window in one, never out in the room
    (`FurnishedRoomsGameTests.everyBedHasItsHeadToAWall`).
  - **Every bed can be walked to from the front door**, and every floor with a bed is reached from inside (a home is no
    use to a villager who can't path to its bed).
- **Job blocks.** None. No code reads one in this family. (Any vanilla job site you add, such as a loom or a barrel,
  would let a jobless villager take that job there; that is allowed, not required.)
- **Storage.** None is read by any job or test. Every chest indoors stands against a wall, a solid block, a bed or
  another chest, never alone in a room (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`). Every block
  with block-entity data stays in its cell in every later level (see "Kept cells").
- **Furniture spots** (`blueprint_furniture/<id>.json`, written by the recipe's `furniture.json` through `rebuild.py`):
  - **I and II each have at least one spot of each kind bed, chair, table, nightstand, lamp, couch**
    (`FurnishedHomesGameTests.everyHomeHasItsFurnishedRooms`: the Artisan's House set is ROOMS + couch; the full set of
    shelf, crockery, clock, picture, blinds is welcome but not required). III's spots are II's above the ground.
  - Without a furniture mod every level plans as its vanilla build (`FurnishedHomesGameTests.withoutFurnitureModsEveryHomeIsVanilla`).
  - Each spot's blocks are exactly the template's blocks there (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`).
  - Nothing floats: a lamp stands on a floor or hangs, a picture and blinds are on a wall, a shelf on a floor or wall,
    everything else on a floor, and every spot has its `support` written (`FurnishedRoomsGameTests.everyFurnishedPieceHasWhatItStandsOn`,
    `FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece`).
  - No spot within two blocks of a door, in or out, along the way through it (`FurnishedRoomsGameTests.noFurnitureStandsInADoorsWay`).
  - Every spot standing on an indoor floor has block light above 7 at it from the room's own light, a lantern hung or
    on a table (`FurnishedRoomsGameTests.everyFurnishedRoomHasALight`); no standing lantern on a room's floor
    (`QaFurnishedRooms1031GameTests.noLanternStandsOnARoomsFloor`).
- **A chimney in Level I: at least one campfire standing on a column of full solid blocks**, and every block of that
  column stays full and solid in every style (`StyleGameTests.chimneysStayWholeInEveryStyle`: "no chimney found in
  artisans_house"; a campfire on a floor or a slab doesn't count). II and III may keep it (they keep most of I anyway).
- **Glass**: in every level no pane or bar is joined on one side only with air on the other (`B648GameTests.b648NoShippedGlassEndsInTheAir`,
  `check.py`'s `open_panes`). No count is required.
- **Level III's cellar** (`BasementGameTests.basementsLoadUnderTheGround`, `QaBasementGameTests`, `CellarGameTests`,
  `QaBlueprintTableGameTests`' basement preview, all of `StarterBlueprints.WITH_BASEMENTS` / `LEVEL_3_CELLARS`): drawn at
  the bottom of the file, its floor at file y = 0 and the ground floor at file y = 4, exactly 4 deep (the file is II's
  height + 4); at least one `minecraft:ladder` or stairs below ground; at least one `minecraft:lantern` below ground; a
  ladder or stair on the cellar's top layer (ground y -1) with room to climb out above it at ground y 0 and 1; **a
  trapdoor (the hatch) at ground y 0 over a cellar column**; no dark floor cell (block light above 0 on every standable
  cell down there); walkable from the ground all round the build down to the cellar floor and back out; every chest or
  barrel down there within 2 across and 1 up or down of a walked cell.
- **A front door** on the ground floor at the z = 0 side.

## Kept cells (every upgrade, and every refresh)
- Each level has the same origin and front as the one below and is at least its size in x, y and z, and **at least
  60 % of the level below's non-air blocks stay the same block (same state) in the same cell**
  (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`, II over I and III over II).
- **III above the ground is II exactly**, the same size (`b3.size().equals(b2.size())`): the only allowed change is a
  trapdoor at y 0 over a cellar column, the hatch (`CellarGameTests.levelThreesLineUpOverTheirLevelTwos`). So whatever III
  needs above the ground (the hatch's cell, room round it, the way to it from the door) is already in II.
- II has **no basement** (`QaBenchMovesCellars1104bGameTests.aBuildWithNoBasementIsNeverOfferedACellar`; only III is
  listed with one).
- Every block with block-entity data (chest, barrel, furnace, ...: anything but beds, campfires, signs, banners, skulls
  and bells) stays in the same cell as the same block in every later level (a builder never breaks those while
  clearing, `MaterialRules.isProtected`), and every bed stays where it was.
- **A refresh (36.21) never moves a container, a slept-in bed or a job site** (`Refreshes.kept`). Players' houses stand
  from every older drawing (the live `.nbt`s and `blueprint_history/artisans_house*/` and `blueprint_history/modded/artisans_house*/`:
  `42864073a`, `aec09cb26`, `cd1557d8c`, `f7fe6315a`, `v0.145.0`, `v0.151.1`; all of them have the cells below, in
  template cells (x, y, z), ground floor y = 1 on a floor at y = 0, cellar below 0). **The new drawing must not put a
  door, a stair, a ladder or a walkway on any of them**, nor anything a kept container there would block; best is a
  container, furniture or plain interior floor space there:
  - Level I (all drawings alike): barrels (5, 1, 1), (6, 1, 3), (2, 1, 6); chests (2, 1, 7) facing east and (4, 6, 2)
    facing south. Bed heads (2, 6, 2), (6, 6, 2), feet (2, 6, 3), (6, 6, 3) (facing north).
  - Level II: all of I's, and loom (5, 1, 11) facing west (a vanilla job site: kept), barrel (5, 1, 12), chest
    (5, 1, 15) facing west. Bed cells (3, 1, 14) and (3, 1, 15) (head at z 14 in `42864073a` and `aec09cb26`, at z 15
    in `cd1557d8c` and today's).
  - Level III: all of II's, and ten chests in the cellar facing south, at (2..6, -3, 3) and two high at (2..6, -2, 3).
  - Not kept (free): the crafting table (2, 1, 3) and the anvil (2, 1, 5).
  - The refresh-equals-fresh-build check (`RefreshMatchesFreshBuildGameTests.differences`) skips chests and POI blocks
    (barrels, the loom and the crafting table are POIs) but not other blocks, so no kept cell needs a particular block in
    the new drawing.

## Fits
- Plot (y = 0 sits on the clicked block; the front is the z = 0 side), from where the tests place each level:
  - Levels I and II: **at most 21 wide x 18 tall x 22 deep** (`BuilderGameTests.buildsArtisansHouse`, `buildsArtisansHouseII`,
    `QaGranderHomes1107GameTests.anArtisansHouseBuiltAcrossASaveAndReloadEndsFinishedWithExactlyItsMaterials` and
    `QaGranderHomes1107bGameTests.aStyledArtisansHouseBuiltForRealSellsItsIIInTheSameStyle` build at (9, 2, 8) in the
    30 x 20 x 30 `huge_area`, with the builder's bench at (2, 2, 2), a chest at (2, 2, 4) and barrels at x 1-5, z 4).
  - Level III: II above the ground plus the 4-deep cellar (refreshed at (7, 2, 2) in `huge_area`; placed turned and
    mirrored from (1, 2, 1) there).
  - Level I's box must contain template cell **(5, 6, 10)** and II's too (pinned, see "Rules tests check"): I at least
    6 wide, 7 tall and 11 deep.
- Declared sizes: `StarterBlueprints.ARTISANS_HOUSE` (`9, 15, 12`), `_2` (`9, 15, 18`), `_3` (`9, 15, 18`, basement 4)
  today; if a size changes the ship step changes those `Vec3i`s (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`:
  sizes match, every block one a builder can place; `BasementGameTests`: III's file is its size + 4 tall).
- Material budget: I and II each fit the four barrels the build tests stock, **at most 108 stacks** (each item rounded up
  to whole stacks; asserted in `buildStarter` and the reload test).
- Build time (exact materials, builder alone, 0 skipped, every block right): I within **40,000 ticks** (`buildsArtisansHouse`),
  II within **50,000** (`buildsArtisansHouseII`); I across a save and reload mid-STRUCTURE (after 40 structure steps)
  within 50,000, plain and in `cherry`.
- Front and way in: the z = 0 side faces the player who places it and the road of a Steward `homes` plot; the front
  door goes there on the ground floor; every upgrade keeps that front.
- Neighbours: a free-standing home in a Steward `homes` zone, or anywhere a player puts it; no party walls.

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none; the first is
shared with the Townhouse and the Stone House, so keeping it is much cheaper than moving it):
- `HomeGradeGameTests.aBedInTheTownhouseCountsIIAndInItsUpgradeIII`: a bed at template cell **(5, 6, 10)** (helper
  (7, 8, 12), origin (2, 2, 2), not turned) is inside Artisan's House I (grade 1) and II (grade 2).
- `QaGranderHomesGameTests.theTipReadsRightForEveryClassAndCount`: a Burgher's bed at template cell (1, 1, 1) (origin
  (21, 2, 2) in `big_area`) is inside I (he "wants a grade II home") and II (he wants nothing).

Built whole (every block right, 0 skipped, supplies exact): `BuilderGameTests.buildsArtisansHouse`, `buildsArtisansHouseII`;
`QaGranderHomes1107GameTests.anArtisansHouseBuiltAcrossASaveAndReloadEndsFinishedWithExactlyItsMaterials` (finished,
nothing of its materials left anywhere, exactly 2 beds built and each a grade I home, its builder then sells II and not
III); `QaGranderHomes1107bGameTests.aStyledArtisansHouseBuiltForRealSellsItsIIInTheSameStyle` (the **cherry** I built
across a reload, at least 2 beds of grade 1, its builder sells the cherry II).

Styles: `QaGranderHomes1107bGameTests.aStyledArtisansHouseBuiltForRealSellsItsIIInTheSameStyle` needs the Table to offer
`cherry` for I, i.e. **I contains at least one block `blueprint_styles/cherry.json` swaps** (any oak or spruce block,
stripped or not; dark oak stairs or slabs; deepslate tiles or their stairs or slabs; cobblestone or mossy cobblestone or
their stairs, slabs or walls; andesite or polished andesite or their forms). `everyStyledTierLeadsToTheNextTierInItsOwnStyle`:
I and II each take at least one style (some block some style swaps). Which blocks you use beyond that is free.

Beds give their grade: `QaGranderHomes1107GameTests.everyBedOfTheArtisansHouseGivesItsGrade` (I, II, III turned and
mirrored at (1, 2, 1) in `huge_area`, every bed inside the area, grades 1, 2, 3, garden beauty 0, the home's name the
blueprint's display name, unchanged after a save and reload).

Refresh (36.21): `QaReleaseRefresh1028GameTests` (`qaReleaseRefresh_artisans_house_2`, `qaReleaseRefresh_artisans_house_3`:
built from the `cd1557d8c` drawing at (7, 2, 2) in `huge_area`, the old cellar dug out, refreshed through the hall's
Repairs page, ends equal to a fresh build of the new drawing, chests and POI blocks aside); `QaRefreshHistory1028GameTests.qaEveryShippedOlderDrawingIsOfferedARefresh`
(every older drawing in `blueprint_history/artisans_house*/` that differs from the new one is offered a refresh). The
ship step copies today's live drawings (including I's, never redrawn since `42864073a`) into `blueprint_history/`
before replacing them.

By id only (the drawing doesn't matter beyond the rules above): `HomeGradeGameTests` (grades, the shipped Steward rule,
which builders sell it, never handed out by the Table), `QaGranderHomesGameTests`, `QaGranderHomes1107GameTests`
(`eachUpgradeComesOnlyFromTheBuilderWhoFinishedTheTierBelow`, the Table refusals), `QaBuilderFixes1107bGameTests`,
`CellarGameTests` (III upgrades II, sold by II's builder), `BuilderGameTests.starterUpgradesKeepMostOfTheirBase`'s count
of 64 upgrades.

The owner's rules (`check.py`: posts at fence corners, lamps on tables or posts, crockery only by a stove, furnace,
smoker or counter or on a table with a chair, headroom; `open_panes`), STYLE.md's checks and `holes.py`:
`rebuild.py --candidate` prints no CHECK line for any level, and `holes.py` passes. The ship step removes
`modded/artisans_house_3` from `owner_rules_baseline.json`.

## Free
Everything not listed above: shape, plan, rooms, roofs, materials (beyond one block the cherry style swaps), colors,
style, how many storeys, whether there is a workroom, a shop, a yard or a loft, which floor holds which bed, what II adds
(beyond its third bed and keeping 60 % of I), what the cellar holds and where its ladder stands, where furniture,
chests and the chimney stand within the rules above, lighting, and the declared sizes within the plot.
