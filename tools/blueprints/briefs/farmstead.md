# Farmstead: the brief

Family id(s): `farmstead`, `farmstead_2`, `farmstead_3` (never renamed; "Farmstead", "Farmstead II", "Farmstead III" in
`en_us.json`, `blueprint.aliveworkplace.farmstead` / `_2` / `_3`, the first two spelled exactly so:
`QaNewWorkplacesGameTests.qaNewWorkplaceNamesAndText`; hidden advancements `built/farmstead`, `_2`, `_3`; the questbook's
"The first job: a Farmer" completes on `built/farmstead`)
Variants: village (none: no `structure/village/*farmstead*`), modded (none to draw: `modded_library.py` makes
`structure/modded/farmstead*.nbt` from the vanilla drawings and the ship step reruns it), looks (none: the Table's style
buttons restyle it through `BlueprintStyles`; no test names a styled Farmstead).

## What it is for
**The Farmer's workplace** (ROADMAP 27.14) and the village's first food. A Farmer's job block is the vanilla
**composter** (a jobless villager who takes it becomes a Farmer). In this mod a village Farmer with no field **takes on
the field by his composter by himself** (`farm/Fields.adoptOwnFarm`, game rule `villageFarms`) as soon as there is a
chest near the composter for the harvest, then works it (`farm/FieldWork`): harvests ripe crops, replants, tills, sows
from the chests, carries the harvest (wheat, carrots, potatoes) to the chests, bakes wheat into bread at the composter
and composts surplus seeds into bone meal. The questbook tells the player exactly this: "A villager without a job takes
its composter and becomes a Farmer. They farm the field next to it by themselves." A Farmer may sleep in its bed.
It is in every new village's starting set (`blueprint_unlocks/starting.json`); the Steward builds it by himself in a
`farms` zone when a Farmer has no workstation (`steward_rules/workplace_farmstead.json`: `worker_without_workstation`
for `minecraft:farmer`, Hamlet rank and up, priority 62, cooldown 2 days); his renewal of an old Farmer's house rebuilds
it as this one (`steward_renewal/farmer.json`, the only building on that list). On the Village Map it is a Farms
building (`VillageMaps.kindOf`, by name). It counts nothing for beauty (`Decorations.points` 0). Village chests carry
Farmstead II blueprints (five `loot_table/chests/village_*.json`).

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `farmstead` | One Farmer's composter, a chest for his harvest near it, **one bed**, one field of **exactly 9 x 5 farmland** round a water channel, with crops on it, and a scarecrow's head (a carved pumpkin or jack o'lantern) | Starting set; Steward rule `workplace_farmstead`; renewal list `farmer.json`; questbook "The first job: a Farmer" |
| II | `farmstead_2` | **A second Farmer**: a second composter (exactly 2 in all) with its own chest near it, and **a second field** (at least 72 farmland in all) | Upgrade of I (`BlueprintUpgrades`: id + `_2`); sold by the builder who finished I (`UpgradeOffers.offer`); village chest loot |
| III | `farmstead_3` | **A cellar exactly 4 deep** dug under II, reached from inside; above the ground it is II, block for block | Upgrade of II, sold by the builder who finished II (`CellarGameTests.levelThreesLineUpOverTheirLevelTwos`); one of the eight `LEVEL_3_CELLARS` |

**Exactly three levels**: no `farmstead_4` (it would change `BuilderGameTests.starterUpgradesKeepMostOfTheirBase`'s
count of 64 upgrades).

## Must have (each level)
- **Job blocks: exactly one `minecraft:composter` in I and exactly two in II** (`QaNewWorkplacesGameTests.qaFarmsteadHoldsItsFieldAndBed`;
  `CountryWorkplacesGameTests.countryWorkplaceBuilt_farmstead` / `_farmstead_2`: one per worker). **No other job
  block**: that test counts blast furnace, smithing table, grindstone, stonecutter, fletching table, cartography table,
  **barrel**, lectern, cauldron, smoker, brewing stand, **crafting table**, jukebox and **furnace** and wants 0 of each.
  So no barrel for storage, no furnace or smoker in the kitchen, no workbench. None of the mod's workstations either
  (each takes a villager for another job). III keeps II's two composters and adds no job block in the cellar (the code
  rule in `basements.py`: a barrel, lectern or brewing stand down there gives a villager a job; III isn't counted by
  that test, but a third composter would take a third Farmer).
- **Each composter is walked to from the street**: a real Farmer spawned in front of the build (template (1, 0, -2), and
  (4, 0, -2) for II's second) walks in and claims a composter **inside the build's box**, two Farmers two different
  composters (`countryWorkplaceBuilt_*`), and each is within a player's reach of a walked cell
  (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`). Villagers open wooden doors, not fence
  gates or trapdoors.
- **A chest within 8 blocks across (x and z) and 4 up or down of every composter** (`SupplyContainers.find`, `RADIUS` 8,
  `VERTICAL` 4). Without one the Farmer never takes on his field (`Fields.adoptOwnFarm`), and the built test fails.
  The **nearest** store to the composter is where he carries the harvest (`FieldWork.containerNear`), and the built test
  looks for it in a container at **template y = 1, x 0-14, z 0-12** (`farmerWorksTheField`): in I keep every store the
  composter reaches inside that box at y = 1, or at least make the nearest one so. At least one `minecraft:chest` in
  every level (`qaFarmsteadHoldsItsFieldAndBed`). Chests are drawn empty; every chest is reachable on foot
  (`QaBuildReachGameTests`); an indoor chest stands against a wall, a solid block, a bed or another chest
  (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`).
- **The field (Level I): farmland whose bounding box is exactly 9 x 5** (either way round) **and one block high**
  (`qaFarmsteadHoldsItsFieldAndBed`: "the Farmstead's field is a x c, not 9 x 5"; "isn't level"). All of I's farmland
  counts, so no other farmland anywhere in I (a herb bed of farmland would widen the box).
  - **At least 4 water blocks inside the field's box**, and **every farmland block has water within 4 blocks in x and
    z, at its own level or one above** (same test; otherwise it dries to dirt).
  - **One joined field the Farmer finds from his composter** (`Fields.farmNear`): the farmland nearest the composter
    (within 16 across, 4 up or down) and everything joined to it through farmland (a step of one up or down allowed) or
    water on the same level, at most 32 a side. The whole 9 x 5 must join, so the water channel and any break in the
    rows must not cut it in two (a solid block across the channel, such as a scarecrow's footing, is fine only where
    farmland still joins round it).
  - **Pinned cells**: the Farmer's field must take in template **(9, 0, 2) and (13, 0, 10)**
    (`CountryWorkplacesGameTests.farmerWorksTheField`), i.e. today the field is x 9-13, z 2-10, on y 0. The ship step may
    move these two pins to the new field's corners, keeping what the test checks.
  - **Crops drawn on it**: the test ripens every crop block at y 1 over x 9-13, z 2-10 and wants **at least 6
    harvested** and some wheat, carrots or potatoes in the chests, so draw **at least 6 crop blocks of wheat, carrots
    or potatoes** on the field (beetroot doesn't count as harvest stored). The build-reserve tests need Level I to cost
    **at least 3 carrots and at least 1 wheat seed** (`QaBuildReserve1104bGameTests`: "need > 2 && seeds > 0";
    `BuildReserveGameTests`, `QaBuildReserve1101GameTests`): so at least 3 carrot crops and 1 wheat crop in I.
  - Nothing solid stands on farmland (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`, `check.py`'s
    `turns_path`: the game turns it to dirt), and no dirt path under a solid block either.
- **Level II's second field: at least 72 farmland in all** (`qaFarmsteadHoldsItsFieldAndBed`: "not two fields"), all on
  one level (the "isn't level" check applies to II too: every farmland in II at the same y), at least 4 water in the
  box round all of it, every farmland with water within 4 at its level or one above. The second composter needs a field
  of its own by it the same way (the code; no test runs II's second Farmer's work).
- **The scarecrow's head: at least 1 `carved_pumpkin` or `jack_o_lantern`** in I and II (`qaFarmsteadHoldsItsFieldAndBed`).
- **Beds: exactly 1 in Level I** (`qaFarmsteadHoldsItsFieldAndBed` counts bed heads). II and III: no test counts them
  (free; I's bed stays, see "Kept cells"). Every bed counts as a village bed for the Steward (`StewardConditions.beds`).
  Its head is against a wall or a window (`FurnishedRoomsGameTests.everyBedHasItsHeadToAWall`), its head one block from
  its foot towards its facing (`BlueprintsStayGameTests`), reachable on foot (`QaBuildReachGameTests`), whole turned and
  mirrored (`QaMirroredBuildsGameTests`).
- **No entity in any file** (`countryWorkplaceBuilt_*`: "comes with a villager"): no villager, animal, frame or painting.
- **Furniture spots** (`blueprint_furniture/<id>.json`, written by the recipe's `furniture.json` through `rebuild.py`):
  a `bed` spot for the bed. No other kind is required (not a home: not in `FurnishedHomesGameTests`), more are welcome.
  Because the family has spots, the furnished-room checks apply to every level: each spot's blocks are exactly the
  template's (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); every piece on a room's floor indoors has
  block light above 7 from a hung lamp or one on a table (`FurnishedRoomsGameTests.everyFurnishedRoomHasALight`);
  nothing stands on air (`everyFurnishedPieceHasWhatItStandsOn`, `FloatingFurnishingsGameTests`); no piece in a door's
  way (`noFurnitureStandsInADoorsWay`); every chair faces its table (`QaChairFacingGameTests`); no standing lantern on a
  room's floor (`QaFurnishedRooms1031GameTests`).
- **Level III's cellar** (`BasementGameTests.basementsLoadUnderTheGround`, `QaBasementGameTests`, `CellarGameTests`,
  `QaBlueprintTableGameTests.everyBasementBuildPreviewsItsWholeDepth`; all of `StarterBlueprints.WITH_BASEMENTS` /
  `LEVEL_3_CELLARS`): drawn under ground y 0, exactly 4 deep (file height = II's height + 4, the `Entry`'s basement 4);
  at least one `minecraft:ladder` or stairs below ground; at least one `minecraft:lantern` below ground; a ladder or stair
  on the cellar's top layer (y -1) with room to climb out above it at y 0 and y 1; **a trapdoor (the hatch) at y 0 over a
  cellar column**; block light above 0 on every standable cell down there; walkable from the ground all round the build
  down to the cellar floor and back out; every chest or barrel down there within 2 across and 1 up or down of a walked
  cell. No bed down there (free otherwise: what the cellar is for and holds).
- **A way in on the z = 0 side** at ground level that a villager 1.95 blocks tall walks through
  (`QaDoorwayClearanceGameTests`; check.py's `doorway_problems`); any upper floor or loft holding a chest, bed or
  composter is reached by a ladder or stairs from inside (`QaBuildReachGameTests`).
- **Glass**: no pane or bar joined on one side only with air on the other (`B648GameTests`, `check.py`'s `open_panes`).
- **A chimney is optional**, but a campfire on a stack must stand on blocks that stay solid in every style
  (`StyleGameTests.chimneysStayWholeInEveryStyle`).
- No jigsaw or structure void; every block one a builder can place
  (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).

## Kept cells (every upgrade, and every refresh)
- **II over I, III over II**: same origin and front, each at least the size of the one below in x, y and z, and **at least
  60 % of the level below's non-air blocks stay the same block (same state) in the same cell**
  (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`). So I's field, composter, chest and bed sit at the same cells
  in II. Draw the levels together.
- **III above the ground is II exactly**, the same size (`b3.size().equals(b2.size())`): the only allowed change above
  ground is the hatch, a trapdoor at y 0 over a cellar column (`CellarGameTests.levelThreesLineUpOverTheirLevelTwos`).
  So the hatch's cell, the room round it and the way to it from the door are already in II.
- II has **no basement** (`QaBenchMovesCellars1104bGameTests.aBuildWithNoBasementIsNeverOfferedACellar`).
- Every chest (any block with block-entity data) stays in its cell as the same block in every later level (a builder
  never breaks those while clearing, `MaterialRules.isProtected`); the composters and the bed stay where they were too.
- **A refresh (36.21) never moves a container, a slept-in bed or a job site** (`Refreshes.kept`: protected blocks,
  someone's bed, every acquirable job site, which a composter is). Every older drawing has these, in template cells
  (x, y, z), all the same across the live `farmstead*.nbt`, their modded copies (spruce chests there), and
  `blueprint_history/farmstead*/a12542b71.nbt` (and `modded/farmstead_3/v0.150.0.nbt`):
  - Farmstead I: **composter (8, 1, 4)**; **chests (6, 1, 3)** facing west and **(8, 1, 6)** facing east (single);
    **bed** foot (6, 1, 6), head (6, 1, 7), facing south.
  - Farmstead II: all of I's, and **composter (8, 1, 13)**, **chest (8, 1, 19)** facing east.
  - Farmstead III: all of II's, and cellar **chests (2, -3, 4), (2, -3, 5), (2, -3, 6)** facing east. (Its old ladder
    ran (6, -3..-1, 4) under a hatch at (6, 0, 4); not kept, but a kept chest must not block the new way down.)
  - **The new drawing must not put a door, a walkway, a wall or the field on any of these cells**, nor anything a kept
    chest, composter or bed would block. **Composters matter most**: a refreshed old Farmstead keeps its old composter,
    so a new composter elsewhere leaves it with two in I (three in II), i.e. an extra Farmer and the wrong count. The
    cleanest answer is composters at (8, 1, 4) and (8, 1, 13) in the new drawing; otherwise the ship step must show a
    refreshed old Farmstead ends with exactly I's one / II's two working composters.
    (`RefreshMatchesFreshBuildGameTests.differences` skips chests and job-site blocks, so no refresh test catches this.)
  - `QaRefreshHistory1028GameTests.qaEveryShippedOlderDrawingIsOfferedARefresh`: every older drawing that differs from
    the new one is offered a refresh (automatic once the ship step keeps the old drawings with `history.py`).
    `QaModdedRefresh1115cCompatTests.redrawnModdedCopiesAreOfferedFromTheirShippedDrawingOnly` (compat): a Farmstead
    standing as the modded `a12542b71` drawing is offered a refresh, and one standing as today's modded copy is not; the
    modded copy must stay complete with the pack's mods (`ModdedLibrary.complete`).

## Fits
- Plot (y = 0 sits on the clicked block; the front is the z = 0 side), from where the tests place each level:
  - Level I and II: **at most 18 wide x 26 deep x 28 tall** (`CountryWorkplacesGameTests.countryWorkplaceBuilt_*` at
    helper `HOUSE` (12, 2, 4) in the 30-cube `huge_area`; the builder's bench (3, 2, 3), the builder (5, 2, 3) and his
    chests at x 3, z 6/8/10 stand west of it).
  - Level I also **at most 21 deep** (`QaModdedRefresh1115cCompatTests` places I at (9, 2, 9) in `huge_area`).
  - Level III: II's size exactly, plus the 4-deep cellar.
  - The reserve tests (`BuildReserveGameTests`, `QaBuildReserve1101GameTests`, `QaBuildReserve1104bGameTests`) only plan
    I at (6, 2, 6) of `huge_area`, never build it; one needs 40 queued Farmsteads to need more than 8,192 blocks in all,
    so **I needs at least 206 blocks to place** (`otherBuildsNeedsAreRecountedAFewAtATime`).
- **Declared sizes**: `StarterBlueprints.FARMSTEAD` (`15, 12, 13`), `FARMSTEAD_2` (`15, 12, 22`), `FARMSTEAD_3`
  (`15, 12, 22`, basement 4) today; if a size changes the ship step changes those `Vec3i`s (the built test and
  `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable` assert file size = entry; `BasementGameTests` III's file
  height = size + 4).
- **Material budget: at most 81 stacks per level** (the built test stocks three chests of 27 with what the supply report
  says is missing, each item rounded up to whole stacks; II is built from nothing in its own test).
- **Build time**: the whole build, then the Farmer(s) walking in and claiming composters, and for I the Farmer taking on
  the field, harvesting 6 and storing some, all within **14,000 ticks** (build delay 2, builders' help off).
- **Front and way in**: the z = 0 side faces the player who places it ("the gold edge of the outline is the front") and
  the road of a Steward plot. The arriving Farmers wait 2 blocks in front of the front edge at template (1, 0, -2) and
  (4, 0, -2), on the build's y = 0 level, and must walk in from there to the composters.
- **Neighbours**: a workplace in a Steward `farms` zone; the Steward keeps 2 blocks clear round its plot and wants the
  ground under it within 4 blocks of level (`city/Plots`).
- **Steward renewal fit** (`steward_renewal/farmer.json` lists only `farmstead`; `Renewals.fit`): when an old Farmer's
  house is renewed, Level I goes on its plot with its front (z = 0) on the old house's front line, centred on the old
  door, and must lie within the old footprint **plus 3 blocks to each side and 3 back** (`Renewals.MARGIN`); it never
  steps forward of the old front. If it doesn't fit, that old house isn't renewed at all. So a smaller Level I
  footprint (above all its depth) renews more old farms; the size is free within the plot above, but this is the trade.

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing if needed, keeping what each checks, dropping none):
- `CountryWorkplacesGameTests.farmerWorksTheField` (inside `countryWorkplaceBuilt_farmstead`): the Farmer's field box
  holds template (9, 0, 2) and (13, 0, 10); crops ripened over (9..13, 1, 2..10); harvest looked for in containers over
  (0..14, 1, 0..12).
- `CountryWorkplacesGameTests.build`: Farmers spawned at template (1 + 3i, 0, -2).

Content checks: `QaNewWorkplacesGameTests.qaFarmsteadHoldsItsFieldAndBed` (I: exactly 1 bed, exactly 1 composter, a
9 x 5 field one high; II: exactly 2 composters, at least 72 farmland; both: at least 1 chest, at least 1 carved pumpkin
or jack o'lantern, at least 4 water in the field box, every farmland within 4 of water at its level or one above).

Built whole: `CountryWorkplacesGameTests.countryWorkplaceBuilt_farmstead` and `_farmstead_2` (size = entry, no entity,
the job-block counts above, at most 81 stacks, built, each Farmer holding his own composter inside the box; I's Farmer
then takes on the field, harvests at least 6 and stores some).

Cellar: `CellarGameTests.levelThreesLineUpOverTheirLevelTwos`, `BasementGameTests.basementsLoadUnderTheGround`,
`QaBasementGameTests` (way up, no dark spot, walked to and back, chests in reach),
`QaBlueprintTableGameTests.everyBasementBuildPreviewsItsWholeDepth`, `QaBenchMovesCellars1104bGameTests` (II has none).

By id only (the drawing doesn't matter beyond the rules above): `CountryWorkplacesGameTests.countryWorkplacesAreInTheBlueprintTable`
(I and II in the Table, starter blueprints, Farms on the map), `countryWorkplaceRule_farmstead_farmer`;
`QaNewWorkplacesGameTests.qaNewWorkplaceRules_farmer`, `qaNewWorkplaceNamesAndText`, `qaOnlyTheBandstandIsADecoration`;
`BlueprintUnlocksGameTests`, `QaBlueprintLibraryVerify1108bGameTests` (starting set), `QaQuestbookClaims1116eGameTests`;
the reserve tests above (carrots and wheat seeds needed, block count).

Library-wide checks it must pass: `B648GameTests`, `QaFurnishedRooms1031GameTests`, `FurnishedRoomsGameTests`,
`FurnitureSpotGameTests`, `FloatingFurnishingsGameTests`, `BlueprintsStayGameTests` (lamps on a block that holds them;
no farmland or dirt path under a solid block; beds whole), `QaMirroredBuildsGameTests`, `QaDoorwayClearanceGameTests`,
`QaChairFacingGameTests`, `QaBuildReachGameTests`, `QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`,
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `StyleGameTests.chimneysStayWholeInEveryStyle`,
`ModdedLibraryGameTests` (a modded copy of each, the vanilla build without the mods).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair, upper floors by stairs or a ladder; `open_panes`), STYLE.md's checks and `holes.py`:
`rebuild.py --candidate` prints no CHECK line for any level, and `holes.py` passes. The old drawings are in
`owner_rules_baseline.json` (`farmstead` 2, `_2` 2, `_3` 2, modded 2/2/3 findings); the rebuild must come out clean and
the ship step removes those entries. Saves stay safe: the ship step keeps the old drawings in `blueprint_history/` and
regenerates the modded copies with the family.

## Free
Everything not listed above: shape, plan, roof, materials, colors, style, how many storeys, where the house, field(s),
composters, chests and bed stand (within the pins, the kept cells and the rules above), what the second level adds
beyond its Farmer, composter, chest and field (a barn, a loft, stalls or none), what the cellar is and holds, which
crops beyond the minimums, the scarecrow beyond its head, a kitchen or none, more furniture, beds in II, lighting,
paths, yards and chimneys.
