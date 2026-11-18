# Smithy: the brief

Family id(s): `smithy`, `smithy_2` (never renamed; "Smithy" and "Smithy II" in `en_us.json`,
`blueprint.aliveworkplace.smithy` / `_2`, spelled exactly so: `QaNewWorkplacesGameTests.qaNewWorkplaceNamesAndText`)
Variants: village (none: no `structure/village/*smithy*`, no village pool names it; village loot chests carry a
`smithy_2` blueprint by id only), modded (none to draw: `modded_library.py` makes `structure/modded/smithy.nbt` and `_2`
from the vanilla drawings and the ship step reruns it; `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`,
`withoutTheModsEveryBuildIsTheVanillaBuild`), looks (none). The Table's style buttons (`BlueprintStyles`) restyle the
drawing automatically; the Steward's renewal builds it restyled (see "Rules tests check").

## What it is for
The smiths' workplace, and the Miner's. Its job blocks are three vanilla ones: the **blast furnace** (the Armorer's,
`PoiTypes.ARMORER`; the mod's **Miner** works at one too, `ModVillagers.MINER`), the **smithing table** (Toolsmith) and
the **grindstone** (Weaponsmith). The Miner uses the blast furnace as his bench: he takes pickaxes, torches and what he
needs from every storage block within **8 blocks across and 4 up or down** of it and puts what he mines there
(`MinerWork`, `SupplyContainers.find(level, bench, ...)`, `RADIUS` 8, `VERTICAL` 4; the questbook tells the player
"Keep pickaxes and torches in a chest by the blast furnace"). A player picks it at the Blueprint Table (in every new
village's starting set, `blueprint_unlocks/starting.json`) and hands it to a builder; the Steward builds it by himself
in a `workshops` zone when an Armorer or a Miner has no workstation (`steward_rules/workplace_smithy.json`:
`worker_without_workstation` for `minecraft:armorer` and `aliveworkplace:miner`, `wanted: true`; priority 62, cooldown
2 days); his renewal of an old Armorer's or Miner's house rebuilds it as this one (`steward_renewal/armorer.json`,
`miner.json`). On the Village Map it is a Workshops building (`VillageMaps.kindOf`, by name). Questbook chapter
"A Miner" asks the player to build one (`chapters.py`, `built("smithy")`); finishing each level grants the hidden
advancement `built/smithy` / `built/smithy_2`. It counts nothing for beauty (`Decorations.points` 0,
`QaNewWorkplacesGameTests.qaOnlyTheBandstandIsADecoration`). **Both levels have a cellar** (a basement 4 deep, the
smith's coal store) that a builder digs with the build, and that "Dig the cellar" digs under a Smithy built before it
had one (`Cellars`, wiki `cellars.md`, `basements.md`).

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `smithy` | One blast furnace, one smithing table, one grindstone (three workers: Miner or Armorer, Toolsmith, Weaponsmith); an anvil, quench water, a smoking campfire; a cellar 4 deep with a way down from inside; storage by the blast furnace | Starting set (`starting.json`); Steward rule `workplace_smithy`; renewal lists `armorer.json`, `miner.json`; questbook |
| II | `smithy_2` | A **second blast furnace** (exactly two, so an Armorer and a Miner both work there), and **more coal and ore store** than Level I; still the cellar | By name (`BlueprintUpgrades`: `<name>_2` upgrades `<name>`). Locked at first with a hint, unlocked by a builder's level-up (`BlueprintUnlocksGameTests`, `QaBlueprintLibraryVerify1108bGameTests`); a builder who finishes Level I sells Level II (`UpgradeOffers.offer`, up to 3 uses, price `4 + solid blocks / 40`, clamped 6-32 emeralds) |

**Exactly two levels.** No `smithy_3`: a third level would change the counted upgrades in
`BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (exactly 64) and is a new feature.

**Upgrade rules the code reads** (`BlueprintUpgrades`, `BuilderGameTests.starterUpgradesKeepMostOfTheirBase`):
- Level II is built **over a finished Level I at the same origin and the same front** (only what changed is built), so
  Level I's cells, the cellar's included, sit at the same template coordinates in Level II.
- Level II is **at least as big as Level I in x, y and z** (above-ground size) and has the same 4-deep basement.
- Level II **keeps at least 60 % of Level I's non-air blocks unchanged**, cellar blocks counted: the same block with the
  same state at the same position (today it keeps 612 of 622). Draw the two together, Level II growing out of Level I.
- Level II over a Level I that has no cellar yet digs it: its plan clears every cell that is air in both levels' cellars
  (`CellarGameTests.oldSmithyDigsOnlyItsCellar`).
- Level I's job blocks stay where they are in Level II (a worker already there keeps his block); the second blast
  furnace is new.

## Must have (each level)
- **Job blocks, exact counts** (`QaNewWorkplacesGameTests.qaSmithyHoldsItsForge`; `CraftWorkplacesGameTests.craftWorkplaceBuilt_smithy`
  / `_smithy_2`, one per worker):
  - `minecraft:blast_furnace`: exactly **1** in Level I, exactly **2** in Level II.
  - `minecraft:smithing_table`: exactly **1** in each level.
  - `minecraft:grindstone`: exactly **1** in each level.
  - **No other job block of any job**, cellar included: none of stonecutter, fletching table, cartography table,
    barrel, lectern, composter, cauldron, smoker, brewing stand, loom or **crafting table** (that test counts each and
    wants 0), and none of the mod's workstations (Blueprint Table, Builder's Bench, Tinker's Bench, Guard Post, ...):
    each takes a villager for another job. A water, lava or powder-snow cauldron is a Leatherworker's job site too, so
    **no cauldron of any kind**. A chest or a furnace is not a job block; neither is an anvil.
- **Each job block is reachable on foot from outside**, through the way in, within a player's reach (2 across, 1 up or
  down) of a cell a villager walks to (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`), and
  a real worker standing in front of the building walks in and claims it (`craftWorkplaceBuilt_*`, see "Rules tests
  check"). In Level II the Armorer and the Miner must each claim a *different* blast furnace ("two workers share ...").
- **At least one anvil** (`#minecraft:anvil`), **at least one campfire** (`#minecraft:campfires`, "chimney smoke") and
  **at least one block of quench water** in each level (`qaSmithyHoldsItsForge`). The water must be a `minecraft:water`
  block (the test also accepts a water cauldron, but a cauldron is a Leatherworker's job block, above) and must stand
  **above the ground** (y >= 0): no water or lava anywhere in the cellar's box (`assertNoFluidInBasement`, below). If a
  campfire stands on a stack of full blocks, every block of that stack must stay a full, solid block in every style
  (`StyleGameTests.chimneysStayWholeInEveryStyle`).
- **Level II's coal and ore store: more of it than Level I.** The test counts every `coal_block`, `raw_iron_block`,
  `raw_copper_block`, `raw_gold_block`, coal or iron ore, chest and barrel in the whole file (cellar included) and wants
  Level II's count higher than Level I's (`qaSmithyHoldsItsForge`). (No barrel: it is a job block.)
- **Storage.** At least **one chest within 8 across and 4 up or down of each blast furnace** (the Miner's own chests,
  `SupplyContainers.find`; the questbook promises "a chest by the blast furnace"). At least **one container above the
  ground** in Level I (`QaBenchMovesCellars1104bGameTests.aCellarDigLeavesTheContainersAboveGroundAndTheirContents`:
  "precondition: the Smithy has no container above ground"). Chests are drawn empty (builders never fill or copy
  containers). Every chest, the cellar's too, is reachable on foot (`QaBuildReachGameTests`,
  `QaBasementGameTests.everyBasementAndItsChestsCanBeWalkedToFromOutside`), and an indoor chest stands against a wall, a
  solid block, a bed or another chest (`QaFurnishedRooms1031GameTests`).
- **The cellar (both levels; `StarterBlueprints.WITH_BASEMENTS`, `Entry` basement 4):**
  - Drawn at the bottom of the file, exactly **4 layers** under the ground floor: its floor at y = -4, its room y -3..-1,
    the ground floor's y = 0 its ceiling (`BasementGameTests.basementsLoadUnderTheGround`: the file is the declared
    height + 4; the plan spans from 4 below the origin). Under the ground, every cell outside the cellar is left out of
    the file (a structure void, not air): the kit does this with `rebuild.py --basement 4 --cellar x0,z0,x1,z1`.
  - It has **cells on its floor layer** (y = -4) (`QaBlueprintTableGameTests.everyBasementBuildPreviewsItsWholeDepth`:
    the deepest cell is the basement's floor), and an **open floor spot**: an air cell with a drawn block under it
    (`QaBenchMovesCellars1104bGameTests.aChestInTheHalfDugCellarSurvivesTheDig`: "no floor spot in the Smithy's cellar").
  - **More than 4 air cells** in it (`QaRepairCellarGameTests.digTheCellarBoundaryIsMoreThanHalf`); "Dig the cellar"
    counts its air cells (`Cellars.needsDigging`: offered when more than half of them are solid in the world).
  - **A way down from inside**: a `minecraft:ladder` or stairs under the ground (`basementsLoadUnderTheGround`), with one
    of them on y = -1 with room to climb out above it (passable at y = 0 and y = 1:
    `QaBasementGameTests.everyBasementsWayDownComesUpThroughTheGroundFloor`). Where the way down passes y = 0 it is air,
    a ladder, a trapdoor or stairs, and a ladder may run on up to y = 1; nothing else of the way down may stand at
    y >= 0, because "Dig the cellar" builds only the cells under the ground plus those (`Cellars.cellarOnly`), and the
    tests then check the **whole** Smithy is as drawn (`CellarGameTests.oldSmithyDigsOnlyItsCellar`,
    `reloadMidDigFinishesTheCellar`; `QaBasementFluids1104fGameTests.lavaInTheEarthBesideASmithysCellarDigBurnsNothing`;
    `QaBasementGround1104BugTests.waterInTheEarthBesideASmithysCellarDigLeavesNoWaterInIt`;
    `QaBuilderGaps1105GameTests.waterloggedBlocksBesideASmithysCellarDigLeaveItDryAndAsDrawn`).
  - **A room over the cellar**: at least one air cell at y = 1 in a column the cellar lies under that is not on the way
    down (`CellarGameTests.playerBlockInTheRoom`, "has no room over its cellar"; `QaBenchMovesCellars1104b`'s
    `roomSpot`): a player's block left there survives a dig.
  - **Lit**: at least one `minecraft:lantern` under the ground (`basementsLoadUnderTheGround`; a soul lantern doesn't
    count), and no air cell on the cellar's floor with air over it has block light 0
    (`QaBasementGameTests.noBasementLeavesADarkSpotForMonsters`). Lanterns hang or stand on something, never on a floor
    (owner's rule).
  - **Walkable**: a villager walks from the ground all round the build down into it and back out
    (`everyBasementAndItsChestsCanBeWalkedToFromOutside`: level, one up, up to three down, ladders; doors and trapdoors
    open), and every chest down there is within 2 across and 1 up or down of a cell reached that way.
  - **Dry and closed**: no water or lava (source, flowing, or in a block not drawn waterlogged) anywhere in the box of
    its drawing, walls and floor included, after a dig beside wet or hot earth (`QaBasementGround1104BugTests`,
    `QaBasementFluids1104fGameTests`, `QaBuilderGaps1105GameTests`, their `assertNoFluidInBasement`); with lava in the earth beside and under it and
    fire spread on, no fire within 2 blocks of the finished build (`lavaInTheEarthBesideASmithysCellarDigBurnsNothing`,
    `noFire`), so nothing in the cellar's shell beside the earth may catch from lava.
  - **No job block down there but the build's own** (the counts above cover the whole file).
- **Beds and residents.** None required: no test or code asks this family for a bed, and the Steward's renewal carries
  no sleepers over to it. A bed is free; if drawn, every bed counts as a village bed for the Steward
  (`StewardConditions.beds`), its head must be reachable on foot (`QaBuildReachGameTests`), its foot has its head one
  block towards its facing (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`) and it stays whole turned
  and mirrored (`QaMirroredBuildsGameTests.everyBuildsDoorsBedsAndDoubleChestsStayWholeTurnedAndMirrored`).
- **Furniture spots.** No kind is required by any test for these ids (no `blueprint_furniture/smithy*.json` today; not
  a home, `FurnishedHomesGameTests` doesn't list it). Every furniture piece the drawing has is recorded as a spot
  (`rebuild.py`), and each spot's blocks are exactly the template's blocks there
  (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); nothing small stands on air
  (`FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece`); every chair stair faces its table, turned and
  mirrored too (`QaChairFacingGameTests`).
- **A way in on the z = 0 side** (the front), at ground level, that a villager 1.95 blocks tall walks through; if it is
  a wooden doorway it has room for him (`QaDoorwayClearanceGameTests`; B170: a carpet just inside a 2-tall doorway
  stopped a worker for good). No fence gate or trapdoor on the only way in (villagers open wooden doors, not gates).
  Any upper floor or loft holding a job block or chest is reached by a ladder or stairs from inside (B149,
  `QaBuildReachGameTests`).
- **No entity** (villager, frame, painting ...) and no jigsaw in either file (`craftWorkplaceBuilt_*`: "comes with a
  villager").
- Every block is one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).

## Fits
- **Plot** (above the ground; the cellar goes 4 under it, inside the same footprint). y = 0 sits on the clicked
  block; the front is the z = 0 side.
  - **Level I: at most 14 wide x 12 deep x 20 tall.** 14 x 12 is the Steward's renewal of vanilla's plains armorer
    house (9 x 8, its door on a long side) plus `Renewals.MARGIN` = 3 a side, with the front on the old door's wall
    (`RenewalGameTests.anArmorersHouseBecomesASmithyAndTheArmorerWorksIt`: "reaches past ... by more than 3"; `Renewals.fit`).
    20 tall: `lavaInTheEarthBesideASmithysCellarDigBurnsNothing` builds it in the 30-tall `tall_area` on ground at y 9,
    origin (8, 10, 9). Today's pins also assume **at most 10 deep**:
    `CellarGameTests.staleDigTradeGoesAndCannotStartABuild` stands two Smithies at (9, 7, 9) and (1, 7, 19) in the
    30 x 30 `huge_area`; deeper than 10 they overlap and the ship step must move one (deeper than 11 the second leaves
    the area).
  - **Level II: at most 18 wide x 22 deep x 28 tall**, and at least Level I's size each way
    (`CraftWorkplacesGameTests.craftWorkplaceBuilt_smithy_2` builds it at helper (12, 2, 8) in the 30 x 30 x 30
    `huge_area`, so x 12..29, z 8..29; the builder's bench at (3, 2, 3) and his chests at x = 3, z 6/8/10 stand west
    of it).
- **Material budget: at most 81 stacks per level** (`craftWorkplaceBuilt_*` stocks three chests of 27 with exactly what
  the supply report says is missing, each item rounded up to whole stacks; Level II is built from nothing in its own
  test, cellar and all). The old drawings are 622 and 743 blocks.
- **Build time:** the whole build, cellar dug and fitted, then the workers walking in and claiming their blocks, within
  **12,000 ticks** (build delay 2, builders' help off). A much bigger build risks it.
- **Declared size:** `StarterBlueprints.SMITHY` is `new Vec3i(13, 12, 10)` with basement 4 and `SMITHY_2`
  `new Vec3i(13, 12, 13)` with basement 4 today (the size above the ground; the file is 4 taller). If a size changes the
  ship step changes those lines (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`, `craftWorkplaceBuilt_*`,
  `basementsLoadUnderTheGround` assert the file matches the entry), and `modded_library.py` reads the sizes from that file.
  The basement stays exactly 4 in both levels.
- **Front and way in:** the z = 0 side faces the player who places it and the road of a Steward plot; the way in is
  there. The arriving workers wait 3 blocks in front of the front edge, at template **(1, 0, -3), (4, 0, -3),
  (7, 0, -3)** and, in Level II, **(10, 0, -3)** (helper (13 + 3i, 2, 5)), level with the build's y = 0, and must walk
  in from there (at most one block up onto the floor).
- **Neighbours:** a workplace in a Steward `workshops` zone; the Steward keeps 2 blocks clear round its plot
  (`Plots.CLEAR`) and wants the ground under it within 4 blocks of level (`Plots.MAX_STEP`). A renewal takes it only
  where it fits the old house's footprint plus 3 a side (above), so a smaller Level I renews more old houses (free).

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing if needed, keeping what each checks, dropping none):
- `CraftWorkplacesGameTests.craftWorkplaceBuilt_smithy` and `craftWorkplaceBuilt_smithy_2` (the `builds()` generator;
  `HOUSE` (12, 2, 8), `BENCH` (3, 2, 3), `CHESTS`, `BUILDER` (5, 2, 3)): the file's size equals the `StarterBlueprints`
  entry; no entity; job-block counts as above; built from its supply report (at most 81 stacks); then the workers
  spawned at helper (13 + 3i, 2, 5) with the trade's brain: Level I a **Miner, a Toolsmith and a Weaponsmith**; Level II
  an **Armorer, a Miner, a Toolsmith and a Weaponsmith**. Each must hold a block of his job inside the box as his
  `JOB_SITE`, keep his job, no two sharing one, within the 12,000 ticks.
- `BasementGameTests.noMaterialsStillDigsAndKeepsContainers` (origin (9, 7, 9), ground at y 6): template
  **(3, -2, 5) is air in the cellar** (dug while the builder waits for materials); a chest put at (5, -2, 5) ("in the
  coal store's room") and a barrel at (5, -2, 9) ("outside its back wall") keep their contents. Builders never break
  containers, so those two pass wherever they fall; (3, -2, 5) must be cellar air, or the ship step moves it.
- `CellarGameTests.staleDigTradeGoesAndCannotStartABuild`: two Level I Smithies at (9, 7, 9) and (1, 7, 19) (see "Fits").
- `RenewalGameTests.anArmorersHouseBecomesASmithyAndTheArmorerWorksIt`: vanilla `plains_armorer_house_1` at (12, 2, 4)
  in a Stonework zone is proposed as `smithy` restyled "stonework" ("Renew the old house ... as a Smithy (Stonework)"),
  fits within the old box plus 3 a side, is built, and the level-2 Armorer works its **blast furnace inside the new
  box** 100 ticks later.
- No test pins a job block, chest, door or ladder coordinate; where they stand is free within the rules above.

The cellar's "Dig the cellar" and repair tests (each places Level I as drawn or as an "old build", its cellar filled
with dirt over stone and the way down's y = 0 cells floored over, then digs it; all need the cellar rules above and
the whole Smithy as drawn after the dig): `CellarGameTests.oldSmithyDigsOnlyItsCellar`,
`staleDigTradeGoesAndCannotStartABuild`, `noOfferWhenTheCellarIsThere`, `reloadMidDigFinishesTheCellar` (a dig's steps
stay at y <= 1; nothing above touched; nothing skipped after a reload);
`QaBenchMovesCellars1104bGameTests.aCellarDigLeavesTheContainersAboveGroundAndTheirContents`,
`aChestInTheHalfDugCellarSurvivesTheDig`; `QaBasementFluids1104fGameTests.lavaInTheEarthBesideASmithysCellarDigBurnsNothing`;
`QaBasementGround1104BugTests.waterInTheEarthBesideASmithysCellarDigLeavesNoWaterInIt`;
`QaBuilderGaps1105GameTests.waterloggedBlocksBesideASmithysCellarDigLeaveItDryAndAsDrawn`;
`QaBuilderGaps1105bBugTests.aSmithyTornDownByHandIsNoLongerOfferedOrDug`, `aSmithyDamagedButStandingIsStillOfferedAndDug`;
`QaBuilderFixes1107bGameTests.aSmithyUnderAQuarterStandingIsGoneAndOverAQuarterIsBack`;
`QaCellarTornDownSaves1107eGameTests` (four `qaASmithy...1107e` tests: a quarter of the blocks on and above the ground
standing is the line, `Cellars.STANDING`); `QaRepairCellarGameTests.digTheCellarBoundaryIsMoreThanHalf`,
`theBlueprintClickedOnAnOldBuildLinesUp` and `aTurnedCellarDugWithMissingMaterials` (Level I turned 90 degrees at
(20, 7, 9): at most 21 deep and 21 wide); `QaRepairCellarBugTests`.

Content checks: `QaNewWorkplacesGameTests.qaSmithyHoldsItsForge` (anvil, campfire, water at least 1; smithing table 1;
grindstone 1; blast furnaces 1 / 2; Level II's ore store bigger); `BuilderGameTests.starterUpgradesKeepMostOfTheirBase`
(the upgrade rules above); the basement-wide checks over `WITH_BASEMENTS`: `BasementGameTests.basementsLoadUnderTheGround`,
`QaBasementGameTests` (way up through the ground floor, no dark spot, walkable with its chests),
`QaBlueprintTableGameTests.everyBasementBuildPreviewsItsWholeDepth`.

By id only (the drawing doesn't matter beyond the rules above): `CraftWorkplacesGameTests.craftWorkplacesAreInTheBlueprintTable`
(both in the Table, starter blueprints, on the Village Map as Workshops), `aWorkerAtHisBlockWantsNoWorkplace` (a
Toolsmith or Weaponsmith without a block never asks for a Smithy), the `rules()` generator's
`craftWorkplaceRule_smithy_armorer` and `_miner`; `QaNewWorkplacesGameTests.rulesForTheirTradesOnly`,
`qaNewWorkplaceNamesAndText`, `qaOnlyTheBandstandIsADecoration`; `BlueprintUnlocksGameTests`,
`QaBlueprintLibraryVerify1108bGameTests`, `QaBuilderSwitches1103GameTests`, `QaWorldSavesMoreGameTests` (Smithy in the
starting set, Smithy II locked then unlocked); `QaQuestbookClaims1116eGameTests.qaEveryBuildAQuestSaysToPickIsOnANewVillagesTable`;
`QaRepairCellarGameTests.everySentenceRendersWithRealArguments`; `QaModdedRefresh1115cCompatTests` (an old
`modded/smithy_2` from history refreshes); `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`,
`withoutTheModsEveryBuildIsTheVanillaBuild`.

Library-wide checks it must pass: `B648GameTests` and `QaWindowsAndWards1028BugTests` / `QaBuilders1028GameTests`
(no pane or bar joined on one side with air on the other, as stored and as the game joins it),
`QaFurnishedRooms1031GameTests` (no standing lantern on a room's floor; every indoor chest against a wall, a solid
block, a bed or another chest), `BlueprintsStayGameTests` (lamps on a block that holds them; no dirt path or farmland
under a solid block; beds whole), `QaMirroredBuildsGameTests` (doors, beds and double chests whole, turned and
mirrored), `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`, `QaBuildReachGameTests`,
`QaLedgerFramesHaulGameTests` / `QaTurnedFramesGameTests` (frames and paintings hang turned and mirrored),
`QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`,
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `StyleGameTests.chimneysStayWholeInEveryStyle`.

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair; `open_panes`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate --basement 4
--cellar ...` prints no CHECK line for either level, and `holes.py` passes. The old `smithy_2` (and its modded copy) is
listed in `owner_rules_baseline.json`; the rebuild must come out clean and the ship step removes those two entries.

Saves (for the ship step, not the designer): ids and the basement depth never change; the old drawings go to
`blueprint_history/` (`history.py`; a site begun on the old drawing finishes as drawn) and the modded copies are
regenerated. `Cellars.needsDigging` and "Dig the cellar" read the **current** drawing for every finished Smithy, so
the ship step must check what a Smithy standing on the old drawing (its old cellar dug, or not) is offered after the
swap, and that a dig can't cut a new way down through an old Smithy's floor.

## Free
Everything not listed above: shape, plan, roof, materials, colors, how open or enclosed the forge is, how many storeys,
a bed or none, how many chests beyond the minimums, where the job blocks, anvil, water, campfire, chests, cellar and
its way down stand (within the rules above), what the cellar holds beyond its chest-reach and light rules, lighting,
and what Level II adds beyond its second blast furnace and bigger ore store. (The wiki's one-line description in
`docs/wiki/new-workplaces.md` and the cellar line in `basements.md` are updated by the ship step to match.)
