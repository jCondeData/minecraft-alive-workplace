# Research Lab: the brief

Family id(s): `research_lab`, `research_lab_2`, `research_lab_3` (never renamed; "Research Lab", "Research Lab II",
"Research Lab III" in `en_us.json`, `blueprint.aliveworkplace.research_lab*`). Variants: village (none: no worldgen
piece), modded (none to draw: `modded_library.py` makes `structure/modded/research_lab*.nbt` from the vanilla drawings
and the ship step reruns it, `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`), looks (none: the Table's
style buttons, `BlueprintStyles`, restyle the drawing by themselves: Stonework, Sandstone, Dark Oak, Cherry, Apricorn).

## What it is for
The **Fossil Scientist**'s workplace (`ModVillagers.FOSSIL_SCIENTIST`, ROADMAP 21.1c; a Cobblemon job). The job's one
workstation is **Cobblemon's Fossil Analyzer** (`cobblemon:fossil_analyzer`, POI `aliveworkplace:fossil_analyzer`). It
never takes a villager by itself (`Stations.byItself`): a player stands a jobless villager **within 4.5 blocks** of a
free analyzer (`Stations.REACH`) and sneak-right-clicks them with a fossil. Players then hand the scientist fossils and
8 emeralds; while a revival is queued the scientist **walks to within 2.5 blocks of the analyzer** and works there
(`FossilWork.tick`, `walker.walkTo(..., lab, 2.5)`), and the finished Pokémon goes straight to the player. The job reads
**no chest, no bed and no other block** of the building. Without Cobblemon the analyzer's cell loads as air (the guide
says "the analyzer's spot stays empty") and the building is only a building; it is still listed in the Blueprint Table
(it is in neither `COBBLEMON_ONLY` nor `COBBLEMON_WORKPLACES`). It starts locked and is **sold by a Journeyman Fossil
Scientist** for 12 emeralds (`ModTrades.fossilScientistTrades`, `BlueprintUnlocks.VILLAGER_SALES`); a traveling trader
may sell it on market day. The **Steward** builds Level I by himself in a **`civic`** zone from Hamlet up when a Fossil
Scientist is wanted and has no workstation, only with Cobblemon (`steward_rules/workplace_research_lab.json`, priority
62, cooldown 2 days), and offers it when renewing an old Fossil Scientist's house (`steward_renewal/fossil_scientist.json`,
its only building: it must fit the old footprint plus a margin, front where the old door was). The Village Map draws it
as learning (`VillageMaps.kindOf`, by id, a blue banner); every finished level counts toward the village's rank.
Finishing each level grants the hidden advancement `built/research_lab`, `_2`, `_3` (by id). Each level is an upgrade on
the same spot, origin and front.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `research_lab` | The Fossil Analyzer, with room to stand by it and walk to it | Locked; sold by a Journeyman Fossil Scientist (12 emeralds); Steward rule `workplace_research_lab` (civic, Hamlet+, Cobblemon) |
| II | `research_lab_2` | More building grown out of I (today a museum hall; nothing in code needs what); the analyzer stays | Upgrade of I (`BlueprintUpgrades`: id + `_2`); sold by the builder who finished I (`UpgradeOffers`) |
| III | `research_lab_3` | A **cellar exactly 4 deep** under II, reached from inside (today a lower lab); above the ground it is II, block for block | Upgrade of II; sold by the builder who finished II (`CellarGameTests.levelThreesLineUpOverTheirLevelTwos`); one of the eight `LEVEL_3_CELLARS` |

Ids, three levels and the I to II to III chain are fixed: `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` counts
exactly 64 starter upgrades and `CellarGameTests` exactly 8 cellared Level IIIs.

## Must have (each level)
- **Job block: exactly one `cobblemon:fossil_analyzer`**, in I, II and III, above the ground. Code, not a test: the tests
  run without Cobblemon, where that cell is air, so **no test will notice if it is missing or unreachable**. It needs:
  - a floor cell a villager can stand on **within 2.5 blocks** of it (where the scientist works) and within 4.5 (where
    the player hands the fossil), walked to on foot from the ground outside through the way in;
  - to be within a player's reach (2 across, 1 up or down) of such a walked cell (the rule
    `QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo` states for every job block).
- **No other workstation anywhere** (cellar included) unless you choose it knowing it is another villager's job site: a
  lectern, cartography table, brewing stand, barrel, composter, smithing table, loom, grindstone, stonecutter, fletching
  table, cauldron, smoker, blast furnace, or a mod job block (Scholar's Desk, Training Post, Fossil Lab...) is claimed
  by vanilla's job search and pulls a jobless villager into that job. The old drawing has none; `cellars.py` puts no job
  block in a cellar.
- **Beds and residents: none required.** No code counts a resident here. Any bed drawn is a village bed (shown on the
  Table as "Beds: N"), must have its head reachable on foot and its foot one block from its head toward its facing
  (`BlueprintsStayGameTests`), stay whole turned and mirrored (`QaMirroredBuildsGameTests`), and not stand right inside a
  doorway (`check.py`).
- **Storage: none required** above the ground (the job reads no chest). Any chest drawn: empty, reachable on foot
  (`QaBuildReachGameTests`), against a wall, a solid block, a bed or another chest
  (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`); double chests stay whole turned and mirrored.
- **Furniture spots** (`blueprint_furniture/<id>.json`, written from the recipe's `furniture.json` by `rebuild.py`; the
  family has none today): no test needs any kind here. Every spot's blocks are exactly the template's
  (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); no spot covers the analyzer, a container or a bed;
  nothing small stands on air (`FloatingFurnishingsGameTests`); no standing lantern on a room's floor
  (`QaFurnishedRooms1031GameTests.noLanternStandsOnARoomsFloor`); every chair stair faces its table, turned and mirrored
  (`QaChairFacingGameTests`); a furnished room has its own light (`FurnishedRoomsGameTests.everyFurnishedRoomHasALight`).
  The ship step's furnished shots (`SCENE=rebuild_research_lab`) find rooms from the spots, so give every room one.
- **A way in from the z = 0 side** that a 1.95-tall villager walks through (`QaDoorwayClearanceGameTests`' rule,
  `check.py`); any upper floor reached by stairs or a ladder from inside. No villager or entity in the files.
- **III's cellar** (`BasementGameTests.basementsLoadUnderTheGround`, `QaBasementGameTests`, `CellarGameTests`):
  `StarterBlueprints.RESEARCH_LAB_3` has `basement` **4**: the cellar's floor at y = -4, the file `size.y + 4` tall; **at
  least one `minecraft:lantern` below y = 0** (that block, by name); a ladder or stairs below y = 0, with **one ladder or
  stair at y = -1 under passable cells at y = 0 and y = 1** (so II must already leave that y = 1 cell open); **a trapdoor
  at y = 0 over a cellar column** (the hatch, III's only change above the ground); no dark standable cell in the cellar;
  walkable from the ground outside down to the cellar floor and back; every chest down there within 2 across and 1 up or
  down of a walked cell. Not the analyzer's place (it stays above).
- Every block one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).

## Kept cells (every upgrade)
- II has the same origin and front as I, is at least its size in x, y and z, and **keeps at least 60 % of I's non-air
  blocks as the same block in the same cell** (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`).
- **The analyzer keeps its cell from I to II to III.** It has a block entity, and a builder never breaks a block with one
  while clearing (`MaterialRules.isProtected`): an analyzer moved in II would leave the old one standing and the new cell
  unbuildable, and the scientist's job site would stay at the old one. The same holds for every chest (or any other
  block-entity block) I draws: same block, same cell in II. `CellarGameTests.researchLabThreeDigsItsLowerLab` asserts
  every chest of II is still a chest in its cell after III is built.
- **III above the ground is II exactly**, the same size; the only allowed change is a trapdoor at y = 0 over the cellar
  (`CellarGameTests.levelThreesLineUpOverTheirLevelTwos`). II has no basement.

## Fits
- Plot (y = 0 sits on the clicked block; the front is the z = 0 side), from where the tests place each level:
  - Level I: **at most 13 wide x 13 deep x 13 tall** (`BuilderGameTests.buildsResearchLab` builds it at helper (9, 2, 9)
    in the 22 x 18 x 22 `big_area`, the builder's barrels at x 1-5, z 4; the height is capped by II below).
  - Levels II and III: **at most 24 wide x 21 deep x 13 tall above the ground**, plus III's 4-deep cellar
    (`CellarGameTests.researchLabThreeDigsItsLowerLab` places II at helper (6, 7, 9) in the 30 x 20 x 30 `huge_area`, on
    earth 5 deep, the bench at (2, 7, 2)).
- **Build time and materials, Level I:** from bare ground, one builder, exactly its own materials, every block right
  **within 9,000 ticks** (`buildsResearchLab`, batch `starter_builds`: the shortest limit of the family; the old Level I
  has 384 blocks), and its materials fit four barrels, **at most 108 stacks** (each item rounded up to whole stacks:
  "test needs more barrels").
- **Build time, Level III:** over a standing II on real ground, from the supply report, within **24,000 ticks**, the
  cellar dug out, II's chests still where they were, and **fewer than 60 % of III's structure and decoration steps
  placed** (`researchLabThreeDigsItsLowerLab`).
- Declared sizes: `StarterBlueprints.RESEARCH_LAB` (11, 9, 11), `RESEARCH_LAB_2` (18, 10, 11), `RESEARCH_LAB_3`
  (18, 10, 11, basement 4) today; the ship step changes them to the new drawing (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).
- Front and way in: the z = 0 side faces the player who places it; when the Steward places it, its front faces the
  nearest road on the plan, else the hall (`Plots`); when it renews an old house, the front goes where the old door was
  (`Renewals`). Every upgrade keeps that front.
- Neighbours: a Steward `civic` plot (the Steward keeps 2 blocks clear round it, `Plots.CLEAR`, and wants the ground
  within 4 of level, `Plots.MAX_STEP`) or anywhere a player puts it; no party walls. A smaller Level I footprint lets the
  Steward renew more old Fossil Scientists' houses into it, but nothing requires it.

## Rules tests check
- `BuilderGameTests.buildsResearchLab`: Level I built from bare ground from its own materials, every block right, within
  9,000 ticks.
- `CellarGameTests.researchLabThreeDigsItsLowerLab` (above) and `levelThreesLineUpOverTheirLevelTwos` (III's size equals
  II's, basement 4, listed and lockable, no block above the ground changed but the hatch, the hatch present, offered by
  the builder who built II).
- `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (II over I, III over II) and
  `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`.
- `BasementGameTests.basementsLoadUnderTheGround`, `QaBasementGameTests` (way down and up, no dark spot, walkable with
  its chests), `QaBlueprintTableGameTests.everyBasementBuildPreviewsItsWholeDepth`, `QaRepairCellarBugTests`.
- By id only (nothing built): `WorkplacesGameTests.workplaceRule_research_lab_fossil_scientist` (civic, Hamlet, needs
  Cobblemon), `QaBuiltAdvancements1116bGameTests` (`built/research_lab` awarded once).
- Library-wide: `QaBuildReachGameTests`, `QaDoorwayClearanceGameTests`' rule, `BlueprintsStayGameTests` (a standing
  lantern, candle or torch on a top that holds its middle, no dirt path or farmland under a solid block, beds whole),
  `QaMirroredBuildsGameTests`, `QaTurnedModBlocksGameTests` (the analyzer and any facing block turn with the build),
  `QaFurnishedRooms1031GameTests`, `QaChairFacingGameTests`, `FloatingFurnishingsGameTests`, `FurnitureSpotGameTests`,
  `FurnishedRoomsGameTests`, `B648GameTests` / `QaWindowsAndWards1028BugTests` (no glass pane or iron bars joined to
  air), `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `StyleGameTests.chimneysStayWholeInEveryStyle`,
  `ModdedLibraryGameTests`.
- The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
  stove or a table with a chair, no bed right inside a door, `open_panes`), STYLE.md's checks and `holes.py`:
  `rebuild.py --candidate` prints no CHECK line. The family is not in `owner_rules_baseline.json`, so it must come out
  clean.
- Not a test, but player text the ship step updates if the new drawing differs: `StarterBlueprints`' javadoc lines,
  `docs/guide/research-lab-build.md` (its infobox size and materials, and "II adds a museum hall", "III digs a lower lab
  ... by the front wall") and `docs/wiki/cellars.md`'s Research Lab III row.

## Free
Everything else: shape, plan, roof, materials, colors, storeys, rooms, where the analyzer stands (within the reaches
above, kept in its cell), what II adds besides growing out of I, what the cellar holds, and whether there are chests,
beds, bookshelves, a skylight or a skeleton at all.
