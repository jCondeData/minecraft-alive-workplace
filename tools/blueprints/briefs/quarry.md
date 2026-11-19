# Quarry: the brief

Family id(s): `quarry` (one level; never renamed; "Quarry" in `en_us.json`, `blueprint.aliveworkplace.quarry`, spelled
exactly so: `QuarryBuildGameTests.quarryBuildWordsAreWritten`)
Variants: village (none: no `structure/village/*quarry*`, no village pool names it), modded (none to draw:
`modded_library.py` makes `structure/modded/quarry.nbt` from the vanilla drawing and the ship step reruns it;
`ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`, `withoutTheModsEveryBuildIsTheVanillaBuild`), looks (none).
The Table's style buttons (`BlueprintStyles`) restyle the drawing automatically; a styled copy is still a Quarry to the
code (`QuarryBuilds.isQuarry` goes by `BlueprintStyles.base`).

## What it is for
A pit the Miner works like a farm (ROADMAP 36.12, `mine/QuarryBuilds`). The building is a working rim round a patch of
**ground left as it lies** (the pit), plus the Miner's workstation and chests beside it. Every 5 seconds
`QuarryBuilds.hire` looks at each finished Quarry and gives its pit to the Miner whose job site is **the build's blast
furnace** (a Miner's job block is a blast furnace or the old Miner's Bench, `ModVillagers.MINER`; a player makes one by
standing a villager by the blast furnace and sneak-right-clicking them with a pickaxe, `Miners.employ`). No Quarry
Marker is needed. The Miner digs the pit layer by layer from the top, cutting stairs that spiral down its walls, starting
at the **edge cell of the pit nearest the furnace** (`QuarrySite.planStairs`). They carry what they dig to the chests near
the furnace (`MinerWork.deposit`: every storage block within **8 across and 4 up or down** of the furnace,
`SupplyContainers.RADIUS` 8, `VERTICAL` 4), take pickaxes, torches and ladders from them, and tend furnaces near the
furnace with the ore (`work/Furnaces.tend`). When the pit is dug out they wait by those chests
("waiting at the shed for the pit to grow back") while `QuarryBuilds.regrow` fills it back from the bottom up with
stone and ore (100 blocks a day by default, `quarryRegrowth`), and dig again once a layer's worth has grown. Only air
inside the pit grows back; nothing outside the pit is ever touched. `quarryBuild` off hides the build and stops it all.
In the Blueprint Table it shows "Jobs: Armorer/Miner (1)" (`table/BuildJobs`: a blast furnace is also vanilla's Armorer
block, so a jobless villager may take it as an Armorer by itself). On the Village Map it is a Workshops building
(`VillageMaps.kindOf`, by name). Finishing it grants the hidden advancement `built/quarry`. It is a one-tier build
(`StarterBlueprints.ONE_TIER`), in neither `blueprint_unlocks/starting.json` nor `rare.json`.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `quarry` | The pit (ground left undrawn), one blast furnace (the Miner's job block), the Miner's chest(s) by it | `StarterBlueprints.QUARRY` in `ONE_TIER`; offered only while `quarryBuild` is on (`StarterBlueprints.offered`) |

**Exactly one level.** No `quarry_2`: a second level is a new feature (`BlueprintUpgrades` would make it an upgrade, and
`QuarryBuilds` knows one pit and one furnace).

## Must have
- **The pit: a box of cells left OUT of the file (structure void), not air and not blocks.** Today it is template x 1-8,
  z 2-10, y -1 down to -10 (`QuarryBuilds.PIT_MIN` (1, -10, 2), `PIT_MAX` (8, -1, 10)): its top layer is the ground
  just under the build's y = 0, so the builder neither digs nor fills it and the ground there stays what it was. **No
  block and no air may be drawn anywhere inside that box** (`QuarryBuildGameTests.theQuarryIsInTheTableWithItsJobsLine`:
  "the blueprint draws ... in the pit"). Air drawn there would have the builder hollow out the pit before the Miner
  arrives; a block drawn there would be dug as rock.
  - Size the code and tests need: at least **4 across in x and in z** (`QaQuarryBuildGameTests.theMinersFindsReachTheShedsChest`
    puts ore at `pit.maxX() - 3`, `pit.minZ() + 3`), at least 3 in x, z and depth for the spiral stairs
    (`QuarrySite.STAIRS_MIN`), and a volume of **at least 500 blocks** so a dug-out pit takes 5 or more days to fill at
    100 a day (`QuarryBuildGameTests.aFullPitTakesDays`; today 8 x 9 x 10 = 720, 7.2 days, as the wiki says).
    **At most 10 deep** while the tests build it at helper y 12 in the 30-tall `huge_area` (its ring of test ground runs
    one below the pit's floor, at the area's floor, y 1).
  - **Keep the space over the pit open:** the Miner stands on each layer as they dig it (a villager is 1.95 tall), so
    nothing solid in the two cells above the pit's top (template y 0 and 1 over the pit). Anything higher (a hoist, a
    beam, a hanging light) is free. The cells over the pit are drawn as air today, so the builder clears them.
- **The Miner's way into the pit.** The stairs start at the pit's edge cell nearest the blast furnace
  (`QuarrySite.planStairs`), at the pit's top layer: a villager must walk on the ground from the furnace to that edge
  and step onto it. So between the furnace and the nearest part of the pit's edge there is an opening a villager walks
  through (no fence, wall, fence gate or 2-high step in the way; vanilla villagers open wooden doors but not fence
  gates). No GameTest walks the real drawing to the pit (the tests lay their own ground); the showcase scene does (see
  "Rules tests check"), and on a live server a Miner who can't walk in only hops down when there is no way at all
  (`MinerWork.approach`). A rim or fence round the rest of the pit is free.
- **Job block: exactly one `minecraft:blast_furnace`, at today's template (14, 1, 7)** (`QuarryBuilds.FURNACE`;
  `theQuarryIsInTheTableWithItsJobsLine`: "no blast furnace at (14, 1, 7)"; `hire` gives the pit only to the Miner whose
  job site is exactly that cell). It stands outside the pit. Its facing is free. It must be reachable on foot from
  outside within a player's reach (2 across, 1 up or down) of a cell a villager walks to
  (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`), so a player can stand a villager by it
  and make them a Miner. **No other job block is needed**, and any other one draws another job to the build and shows
  on its Jobs line (the old drawing's crafting table is the mod's Carpenter block, `ModVillagers` `CRAFTING_TABLE_POI`;
  nothing in `mine/` reads a crafting table: pickaxe crafting doesn't need one). A filled cauldron, barrel, composter,
  lectern, stonecutter, smoker and the rest each take a villager for another job.
- **Storage: at least one chest within 8 across and 4 up or down of the blast furnace** (`SupplyContainers.find`
  round the furnace: the Miner's drop-off for every find, and where the owner leaves pickaxes, torches and ladders;
  `QaQuarryBuildGameTests.theMinersFindsReachTheShedsChest`: coal mined in the pit reaches "the shed's chest"; with none
  the Miner can't deposit). A pit dug out yields hundreds of blocks, so more than one chest is useful (today one double
  chest); how many beyond one is free. Chests are drawn empty. Every chest is reachable on foot
  (`QaBuildReachGameTests`) and an indoor chest stands against a wall, a solid block, a bed or another chest
  (`QaFurnishedRooms1031GameTests`). A chest **inside the pit box** is impossible (nothing is drawn there).
- **Beds and residents.** None: no code or test asks this build for a bed or a resident. A bed is free; if drawn it
  counts as a village bed for the Steward (`StewardConditions.beds`), its head must be reachable
  (`QaBuildReachGameTests`), its foot has its head one block towards its facing
  (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`) and it stays whole turned and mirrored
  (`QaMirroredBuildsGameTests`).
- **Furniture spots.** No kind is required (no `blueprint_furniture/quarry.json` today; not a home). Every furniture
  piece the drawing has is recorded as a spot (`rebuild.py`), and each spot's blocks are exactly the template's blocks
  there (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); nothing small stands on air
  (`FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece`); every chair stair faces its table
  (`QaChairFacingGameTests`).
- **Below ground (the basement), if kept.** Today the entry has a basement 5 deep (`StarterBlueprints.QUARRY =
  workplace("quarry", 17, 9, 12, 5)`, in `WITH_BASEMENTS`): a drawn way down beside the pit. No code needs it (the
  Miner cuts their own stairs inside the pit). If the new drawing keeps anything below y = 0 other than the pit's void,
  these hold (`BasementGameTests.basementsLoadUnderTheGround`, `QaBasementGameTests.noBasementLeavesADarkSpotForMonsters`,
  `QaBlueprintTableGameTests.everyBasementBuildPreviewsItsWholeDepth`): some **ladder or stairs at y < 0**; a plain
  **`minecraft:lantern` at y < 0** (and by the owner's rule it hangs or stands on a post, table or slab, never on the
  floor); **every air cell below y = 0 that has a sturdy floor and air above is lit** (block light above 0), and at
  least one such cell exists; something is drawn on the basement's bottom layer (the Table's preview reaches it); the
  file is exactly the declared height plus the basement. Cells below ground that aren't the pit may also be void (left
  as ground; today 944 cells are). The two `QaBasementGameTests` walk checks skip the Quarry by name (an open pit under
  the sky). Dropping the basement altogether is allowed: the ship step then sets the entry's basement to 0 and takes
  `QUARRY` out of `WITH_BASEMENTS` (and the two skips), and none of these apply.
- **No entity** (villager, frame, painting ...) and no jigsaw in the file; every drawn block is one a builder can place
  (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).
- **A front on the z = 0 side** at ground level that a player and a villager can walk in from; if a doorway leads to
  the furnace and chests it gives a 1.95-tall villager room (`QaDoorwayClearanceGameTests`; B170: a carpet just inside
  a 2-tall doorway stopped a worker for good), and any upper floor holding a chest is reached by a ladder or stairs
  (B149, `QaBuildReachGameTests`).

## Fits
- **Plot:** today the declared size is **17 wide x 9 tall x 12 deep above ground, plus a 5-deep basement**
  (`StarterBlueprints.QUARRY`; `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable` and
  `BasementGameTests.basementsLoadUnderTheGround` assert the file matches it, and the ship step changes the entry if the
  size changes). The tests place it at helper (2, 12, 2) in the 30 x 30 x 30 `huge_area`: so **at most 28 wide x 28
  deep x 18 tall above ground**, and the pit (with one block of test ground round it) inside that area and clear of
  helper x 21-24, z 21-24 near the floor (`QuarryBuildGameTests.theQuarryMarkerStillWorks` marks a free area there).
  y = 0 sits on the clicked block; the front is the z = 0 side.
- **Pinned positions (saves).** The pit and the blast furnace are read from Java constants, not from the file, for
  every Quarry ever finished (`QuarryBuilds.pit` / `furnace` transform `PIT_MIN`, `PIT_MAX`, `FURNACE` from the build's
  recorded origin, turn and mirror). Quarries already standing on the owner's live server were finished on the old
  drawing. **Keep the pit at x 1-8, z 2-10, y -1 to -10 and the blast furnace at (14, 1, 7)**, and an old Quarry's
  Miner, when hired again, still digs the old pit from the old furnace. If the design must move either, say so in
  `plan.mjs` with the new cells: the ship step must then change those constants **and** make `QuarryBuilds` keep the
  old cells for Quarries finished on the old drawing (a code change; without it an old Quarry's next hire would dig
  through its own shed). Saying nothing and moving them breaks saves.
- **Build time / budget:** no test builds this blueprint from a supply report, so no stack budget is pinned; the
  material list must still fit a book (`QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`).
- **Front and way in:** the z = 0 side faces the player who places it. No Steward rule, renewal or village plan places
  it, so it has no street or neighbour it must meet.

## Rules tests check
Pinned to today's coordinates (the ship step moves them to the new drawing if needed, keeping what each checks, dropping
none):
- `QuarryBuildGameTests.theQuarryIsInTheTableWithItsJobsLine`: the Table's Jobs line has the Miner; it is in `ONE_TIER`
  and offered; **no blueprint entry inside the pit box** (`PIT_MIN`..`PIT_MAX`); **a blast furnace at `FURNACE`**.
- The ground the other Quarry tests lay instead of building the file (`QuarryBuildGameTests.quarry(...)`,
  `QaQuarryBuildGameTests.quarry(...)`): test ground at the build's y -1 over x -1..16, z 0..11, a granite ring one
  block round the pit on every layer, the shed's floor at y 0 over x 11-15, z 3-9, the blast furnace at `FURNACE`
  and a chest at `FURNACE` two blocks north (z - 2). These are what the tests assume of the drawing: if the furnace,
  pit or footprint move, the ship step moves them.
- The showcase scene `quarry_build` (`devclient/JobScenes`, `tools/showcase/scenes.py`) places the **real file**, spawns
  the Miner on the cell **west of the furnace** (`furnace.west()`: it must be a free cell a villager can stand in) and
  films them walking from the furnace to the pit and digging; its hill of test rock spans the build's x -2..17,
  z -1..12 and its camera is pinned. This is the only proof that a Miner gets from the furnace into the real pit.

Behavior checks that need the pit and furnace as above (they don't read the drawing beyond them):
`QuarryBuildGameTests.theMinerMinesTheQuarrysPit`, `minedBlocksGrowBackFromTheBottomUp`, `aFullPitTakesDays` (pit volume
at least 500), `theQuarryBuildCarriesOnAfterAReload`, `switchedOffNothingHappens`, `theQuarryMarkerStillWorks`;
`QaQuarryBuildGameTests.aHoleAtTheTopWaitsForTheHoleBelow`, `aWholeRefillStaysInThePitAndInTheMix` (nothing outside the
pit changes: rim, steps and air above), `playerBlocksInThePitAreNeverReplaced`, `theRefillStateSurvivesASave`,
`switchedOffAHiredPitStopsGrowing`, `theShedsFurnaceBrokenMidShift`, `theMinersFindsReachTheShedsChest` (pit at least 4
across in x and z; a chest within reach of the furnace).

By id only: `QuarryBuildGameTests.theRockGrowsBackInAWeightedMix`, `aQuarryBuildSurvivesASave`,
`quarryBuildWordsAreWritten`; `QaQuarryBuildGameTests.theRefillRateAtItsExtremes`; `QaConfigWiringGameTests`
(`quarryRegrowth`); `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`,
`withoutTheModsEveryBuildIsTheVanillaBuild`.

Library-wide checks it must pass: `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`;
`BasementGameTests.basementsLoadUnderTheGround`, `QaBasementGameTests.noBasementLeavesADarkSpotForMonsters` and
`QaBlueprintTableGameTests.everyBasementBuildPreviewsItsWholeDepth` (while it has a basement, above);
`QaBuildReachGameTests` (furnace, chests, any bed reachable on foot); `B648GameTests` and
`QaWindowsAndWards1028BugTests` / `QaBuilders1028GameTests` (no pane or bar joined on one side with air on the other);
`QaFurnishedRooms1031GameTests` (no standing lantern on a room's floor; indoor chests against something);
`BlueprintsStayGameTests` (lamps on a block that holds them; no dirt path or farmland under a solid block; beds whole);
`QaMirroredBuildsGameTests` (doors, beds and double chests whole, turned and mirrored); `QaDoorwayClearanceGameTests`;
`QaChairFacingGameTests`; `QaLedgerFramesHaulGameTests` / `QaTurnedFramesGameTests` (frames and paintings, if any);
`FurnitureSpotGameTests`, `FloatingFurnishingsGameTests`; `QaMaterialListGameTests`;
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`; `StyleGameTests.chimneysStayWholeInEveryStyle`
(only if a campfire stands on a solid stack).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables, slabs or posts or hung, crockery only
by a stove or on a table with a chair; `open_panes`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints
no CHECK line and `holes.py` passes. The old `quarry` and `modded/quarry` are in `owner_rules_baseline.json` (a lantern
standing on the floor below ground) and the old drawing also prints "an upper floor ... has no stairs or ladder up to
it"; the rebuild must come out clean, and the ship step removes both baseline entries. Saves stay safe: the ship step
keeps the old drawing in `blueprint_history/` (`history.py`) and regenerates the modded copy.

## Free
Everything not listed above: shape, plan, materials, colors, roof or none, a shed or none, a hoist, crane, rim, curb or
fence (with the Miner's opening to the pit), drawn steps down or none, a basement or none, how many chests beyond one,
where the chests and the way in stand (within reach of the furnace), lighting, and the size within the plot. (The
wiki's description in `docs/wiki/villager-jobs.md`, "A Quarry build that refills", is updated by the ship step to
match.)
