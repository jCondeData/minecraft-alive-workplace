# Fisher's Hut: the brief

Family id(s): `fishers_hut`, `fishers_hut_2` (never renamed; "Fisher's Hut" and "Fisher's Hut II" in `en_us.json`,
`blueprint.aliveworkplace.fishers_hut` / `_2`, spelled exactly so: `QaNewWorkplacesGameTests.qaNewWorkplaceNamesAndText`;
hidden advancements `built/fishers_hut` / `built/fishers_hut_2`)
Variants: village (none: no `structure/village/*fisher*`, no village pool names it), modded (none to draw:
`modded_library.py` makes `structure/modded/fishers_hut.nbt` and `_2` from the vanilla drawings and the ship step reruns
it; `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`, `withoutTheModsEveryBuildIsTheVanillaBuild`), looks
(none). The Table's style buttons (`BlueprintStyles`) restyle the drawing automatically; the shore and jetty rules
below are keyed on the base id, so they hold for every style.

## What it is for
The Fisherman's workplace, **on a shore, with a jetty out over the water**. A Fisherman's job block is the vanilla
**barrel** (`PoiTypes.FISHERMAN`): a jobless villager by it becomes a Fisherman. Handed a fishing rod (`fish/Fishers`),
he is hired: he looks for water within **16 blocks across and from 6 below to 3 above his barrel**
(`FisherWork.findWater`, `RADIUS` 16), and **water off a jetty is fished before any other**
(`FisherWork.offAJetty`, `JETTY_FIRST`): he walks out and stands on the jetty's deck to cast. His catch, his spare rods
and any boat (with a boat he rows out to open water within 24 blocks for treasure) are kept in **every store within 8
across and 4 up or down of the barrel**, the barrel itself included (`SupplyContainers.find(level, barrel, null)`,
`RADIUS` 8, `VERTICAL` 4). At Level II he also **smokes his catch**: every furnace or smoker in that same reach of the
barrel is tended when he drops off (`work/Furnaces.tend` with `Furnaces::isFish`: raw fish in, cooked fish out into the
stores, fuel from them). A player picks it at the Blueprint Table (in every new village's starting set,
`blueprint_unlocks/starting.json`) and hands it to a builder; the Steward builds it by himself in a `farms` zone when a
Fisherman has no workstation (`steward_rules/workplace_fishers_hut.json`: `worker_without_workstation` for
`minecraft:fisherman`, `wanted: true`; priority 62, cooldown 2 days), only on a plot that passes the shore check
(`city/Plots`, "Fits" below), and his renewal of an old Fisherman's house rebuilds it as this one
(`steward_renewal/fisherman.json`: fits the old footprint plus `Renewals.MARGIN` = 3 blocks a side, front where the old
door was). On the Village Map it is a Farms building (`VillageMaps.kindOf`, by name). It counts nothing for beauty
(`QaNewWorkplacesGameTests.qaOnlyTheBandstandIsADecoration`). The Table lists "Jobs: Fisherman (1)" from the barrel
(`TableJobsGameTests.theTableSendsAShippedBuildsJobsAndBeds`); at Level II the smoker also lists Butcher/Chef/Rancher,
whose job block it is too (accepted today: the test counts it as an extra job block with no worker of its own).

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `fishers_hut` | The Fisherman's barrel, in a building on the shore, and the jetty (the template's first 5 rows) he fishes from | Starting set (`starting.json`); Steward rule `workplace_fishers_hut`; renewal list `fisherman.json` |
| II | `fishers_hut_2` | A smoker (exactly one) within reach of the barrel, so his catch is smoked; still the same barrel and the same 5-row jetty | By name (`BlueprintUpgrades`: `<name>_2` upgrades `<name>`). A builder who finishes Level I sells Level II (`UpgradeOffers.offer`, up to 3 uses, price `4 + solid blocks / 40`, clamped 6-32 emeralds) |

**Exactly two levels.** No `fishers_hut_3`: a third level would change the counted upgrades in
`BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (exactly 64) and is a new feature.

**Upgrade rules the code reads** (`BlueprintUpgrades`, `BuilderGameTests.starterUpgradesKeepMostOfTheirBase`):
- Level II is built **over a finished Level I at the same origin and the same front** (only what changed is built), so
  Level I's cells must sit at the same template coordinates in Level II.
- Level II is **at least as big as Level I in x, y and z**.
- Level II **keeps at least 60 % of Level I's non-air blocks unchanged** (the same block, same state, same position;
  today 509 of 512). Draw the two together, Level II growing out of Level I.
- Level I's barrel stays where it is in Level II (a Fisherman already working there keeps his block). The jetty stays
  the first 5 rows in both.

## Must have (each level)
- **Job blocks: exactly one `minecraft:barrel` in each level; no smoker in Level I and exactly one `minecraft:smoker`
  in Level II** (`QaNewWorkplacesGameTests.qaHutCottageAndBandstandHoldTheirBlocks`; `CountryWorkplacesGameTests`'
  build test counts every one). **No barrel used as storage or decoration**: a second barrel fails both tests and would
  take a second Fisherman. **Zero of each of these**: blast furnace, smithing table, grindstone, stonecutter, fletching
  table, cartography table, lectern, composter, cauldron, brewing stand, loom, crafting table, jukebox and **furnace**
  (the build test lists `Blocks.FURNACE` among the job blocks and wants 0 of it; any other smoker or a furnace in Level
  II fails too). Also none of the mod's workstations (Blueprint Table, Builder's Bench, mailbox, shop counter, ...) and
  no water cauldron (the Leatherworker's site): each takes a villager for another job.
- **The barrel is reachable on foot from outside**, within 2 across and 1 up or down of a cell a villager walks to
  (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`), and a real Fisherman arriving **from
  the land behind the building** (see "Fits") walks to it and claims it as his job site inside the build's box
  (`CountryWorkplacesGameTests.countryWorkplaceBuilt_fishers_hut` / `_fishers_hut_2`). If it is built in under solid
  blocks, some side of it needs a floor cell with 2 blocks of head room for him to stand on (`work/JobSiteApproach`).
- **The barrel within fishing range of the jetty's water:** the water at and beside the jetty's end must be within 16
  blocks across (x and z each) of the barrel and no more than 6 below or 3 above it (`FisherWork.findWater`), or he never
  fishes from the jetty. Today's barrel is 11 rows behind the jetty's end.
- **Level II's smoker within 8 across and 4 up or down of the barrel** (`SupplyContainers.furnaces(level, barrel)`):
  otherwise it is never tended and Level II adds nothing. Reachable on foot like the barrel (`QaBuildReachGameTests`).
- **The jetty** (the template's rows z = 0..4; `Plots.JETTIES` = 5 for both ids, asserted by
  `QaNewWorkplacesGameTests.qaAFishersHutTwoWantsAShore`, and `jettyStands` reads `z < 5`):
  - It is exactly the first **5 rows from the front**. The building's own front (its "root") is at z = 5; nothing else
    of the build lies in those rows over the water except what is listed here and what stands on it.
  - **Its deck is at y = 0, one block above the water's surface, and made of top slabs**: at least **9 slabs** in the
    bottom layer (y = 0) of rows 0..4, every one a **top** slab, with nothing but water under it after the build
    (`CountryWorkplacesGameTests.jettyStands`). A bottom slab, or any block that is not in the top half of its space,
    gets a footing filled down to the bed (`BuildPlan.foundation`) and breaks "water under the deck".
  - **At least 4 posts: blocks in `#minecraft:logs` in the bottom layer of rows 0..4**, which the builder takes down to
    the bed (the footing is the same log); `jettyStands` asserts each post's two cells below are that log
    (`QaJettyPost1114gBugTests.jettyPostsReachTheBed` builds Level II twelve times for this), and
    `qaHutCottageAndBandstandHoldTheirBlocks` wants at least 4 logs in the drawing.
  - **Its end is over water:** every non-air block of the bottom layer at z = 0 must stand over water, or the plot is
    refused (`Plots.jettyShore`, `Reason.SHORE`). The water under the jetty may be at most 3 deep (`MAX_JETTY_DEPTH`;
    deeper is `Reason.DEEP`).
  - **A block in the bottom layer in every one of the 5 rows** (the plot tests put their deep hole under row 2 of Level I
    and under row 1 of Level II and expect `DEEP`: a row with nothing at y = 0 is never measured).
  - **The Fisherman stands on it to fish:** in Level I he must cast with his feet at y = 1 on the jetty, today pinned at
    **x 4..6, z 0..4** (`CountryWorkplacesGameTests.fishermanFishesFromTheJetty`). The deck is joined on foot to the
    building's way in, so he walks from his barrel out to its end.
  - **No bank within 4 blocks of the water off its end.** Water counts as "off a jetty" only when a deck cell over water
    is right beside it and **no spot a villager can stand on whose ground is not over water lies within 4 blocks**
    (`FisherWork.offAJetty`, `JETTY_CLEAR` 4; the ground checked is 1 and 2 above the water's surface). A post whose top
    is left bare at deck height is such a bank (its column is log down to the bed), as is any full block in the jetty rows
    (it gets a footing) or the building's own floor on land closer than that. Every post top within 4 blocks of the
    end's water carries something nobody can stand on at deck height, and the building's own front stays at z = 5.
- **Storage.** No chest is required: the barrel is his store and the code reads every store within 8 across and 4 up
  or down of it. A chest there adds room for the catch, rods and a boat (today one chest in Level I, two in Level II).
  Chests are drawn empty (builders never fill or copy containers). Every chest is reachable on foot
  (`QaBuildReachGameTests`) and an indoor chest stands against a wall, a solid block, a bed or another chest
  (`QaFurnishedRooms1031GameTests`). **Never a barrel** for storage (above).
- **Beds and residents.** None: no code or test asks this family for a bed or a resident, and the old one has none. A
  bed is free; if drawn, it counts as a village bed for the Steward (`StewardConditions.beds`), its head must be
  reachable on foot (`QaBuildReachGameTests`), its foot has its head one block towards its facing
  (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`) and it stays whole turned and mirrored
  (`QaMirroredBuildsGameTests`).
- **A boat shed, nets, a smokehouse room, a chimney:** no code reads any of them. Free.
- **Furniture spots.** No kind is required by any test or code for these ids (`blueprint_furniture/fishers_hut.json` and
  `_2.json` today hold one chair and one table each, recorded from the old drawing; not a home). Every furniture piece
  the new drawing has is recorded as a spot (`rebuild.py`), each spot's blocks exactly the template's
  (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); nothing small stands on air
  (`FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece`); every chair stair faces its table, turned and
  mirrored too (`QaChairFacingGameTests`).
- **Ways in.** The way in from the jetty (the z = 0 side) and **a way in from the land behind or round the side**, both
  at ground level, both passable by a villager 1.95 blocks tall; a wooden doorway has room for him
  (`QaDoorwayClearanceGameTests`; B170: a carpet just inside a 2-tall doorway stopped a worker). Vanilla villagers open
  wooden doors but not fence gates or trapdoors: no gate or trapdoor on the only way to the barrel or to the jetty. Any
  upper floor or loft holding the barrel, the smoker or a chest is reached by a ladder or stairs from inside
  (`QaBuildReachGameTests`).
- **No entity** (villager, frame, painting ...) and no jigsaw or structure void in either file (`build`: "comes with a
  villager"). Every block is one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).

## Fits
- **The front is the water side.** y = 0 sits on the clicked block; the front is the z = 0 side, and for this family
  that is the jetty's end, out over the water. The Steward turns a plot so its front faces the nearest road or the hall
  (`Plots`, `Reason.FACING`), so the jetty points that way.
- **Shore plot rules** (`Plots.tryAt` / `jettyShore`, both levels): the jetty's rows may be over water; of the rest of
  the footprint, at most a tenth may be over water (`Reason.WATER`: the building itself stands on land); the ground under
  it rises at most 4 (`MAX_STEP`); the jetty's end over water, water within 4 blocks of the building's front (z = 5), at
  most 3 deep under the jetty. The water's surface is one below y = 0.
- **Hard plot limit, both levels: at most 18 wide x 20 deep x 18 tall.** `CountryWorkplacesGameTests.build` puts it at
  helper (12, 2, 8) in the 30 x 20 x 30 `huge_area`: x 12..29 gives 18 wide, y 2..19 gives **18 tall** (blocks above an
  area's height are never cleared), and the workers spawn behind it at helper (13 + 3i, 2, 8 + depth + 1), so the depth
  is at most 20. The test's water is x 9..27, z 1..12 (template x -3..15, z -7..4), two deep on a stone bed: every deck
  slab of the jetty must be at template x <= 15 to have water under it. The builder's bench (3, 2, 3), his chests at
  x = 3, z 6/8/10 and the builder (5, 2, 3) stand west of it.
- **Without editing the plot tests** (pinned; the ship step may move them, keeping what each checks):
  - Level I **12 or 13 deep, its jetty's end within the middle 13 columns**: `PlotGameTests.aFishersHutGoesOnAShoreWithShallowWaterUnderItsJetty`
    centres it at (16, 13) turned to face west, box x 10..22, z 7..19 (13 x 13 today), the jetty x 10..14, a pond at
    z 7..19, a hole 4 deep at x 12 (row 2).
  - Level II **16 to 20 deep**: `QaNewWorkplacesGameTests.qaAFishersHutTwoWantsAShore` centres it at (17, 14) turned to
    face west (19 rows today, x 8..26, the jetty x 8..12), water 2 deep at x 4..11 and z 2..26, a hole 4 deep at x 9.
- **Material budget: at most 81 stacks per level** (the build test stocks three chests of 27 with exactly what the
  supply report says is missing, each item rounded up to whole stacks; Level II is built from nothing in its own test).
  The old drawings are 512 and 788 blocks.
- **Build time:** the whole build (posts taken down to the bed in the water), then the Fisherman walking in from behind
  and claiming the barrel, and for Level I his first catch from the jetty, within **14,000 ticks** (build delay 2,
  builders' help off). A much bigger build risks it.
- **Declared size:** `StarterBlueprints.FISHERS_HUT` is `new Vec3i(13, 13, 13)` and `FISHERS_HUT_2`
  `new Vec3i(14, 13, 19)` today (no basement); if a size changes the ship step changes those lines
  (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable` and the build test assert the file's size equals the
  entry), and `modded_library.py` reads the sizes from that file. A basement is a new feature (no `Entry` basement here).
- **Front and way in:** onto the jetty at the front; the arriving workers wait on the land **behind** the build, at
  template **(1, 0, depth + 1)**, and must walk in from there to the barrel, then out along the deck.
- **Neighbours:** a workplace in a Steward `farms` zone; the Steward keeps 2 blocks clear round its plot (`Plots.CLEAR`)
  and 2 more inside the zone (`Plots.MARGIN`). A renewal takes it only where it fits the old Fisherman's house's
  footprint plus 3 a side, so a smaller footprint renews more old houses (free).

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing if needed, keeping what each checks, dropping none):
- `CountryWorkplacesGameTests.countryWorkplaceBuilt_fishers_hut` and `_fishers_hut_2` (the `builds()` generator;
  `SHORE_HOUSE` (12, 2, 8), water x 9..27, z 1..12, two deep): the file's size equals the entry; no entity; job-block
  counts as above (barrel 1; smoker 0 / 1; furnace and the rest 0); built from its supply report (at most 81 stacks);
  `jettyStands` (every log at y = 0, z < 5 reaches the bed; every slab there built, water under it; at least 4 posts and
  9 deck slabs); a Fisherman spawned behind it holds the barrel inside the box as his `JOB_SITE`; for Level I,
  `fishermanFishesFromTheJetty`: one bobber on the water, his feet at y = 1, x 4..6, z 0..4, at least one catch.
- `QaJettyPost1114gBugTests.jettyPostsReachTheBed`: the same build of Level II twelve times; every copy must pass.
- `PlotGameTests.aFishersHutGoesOnAShoreWithShallowWaterUnderItsJetty` (Level I, see "Fits"): refused with no water
  (`SHORE`); fits over a pond 2 and 3 deep with the hut's floor at the shore's level; `DEEP` with a hole 4 deep under
  row 2; `SHORE` when the jetty's end is on dry land (water only past it, or only by the hut); `WATER` when the hut's
  own first two rows are in the water.
- `QaNewWorkplacesGameTests.qaAFishersHutTwoWantsAShore` (Level II, see "Fits"): `Plots.SHORE_BUILDINGS` holds it and
  `Plots.JETTIES` gives 5; `SHORE` on dry land, a plot over water 2 deep, `DEEP` over a hole 4 deep.
- No test pins the barrel, smoker, chest or door coordinates; where they stand is free within the rules above.

Content checks: `QaNewWorkplacesGameTests.qaHutCottageAndBandstandHoldTheirBlocks` (barrels exactly 1; smokers 0 / 1;
logs at least 4); `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (the upgrade rules above).

By id only (the drawing doesn't matter beyond the rules above): `CountryWorkplacesGameTests.countryWorkplacesAreInTheBlueprintTable`
(both in the Table, starter blueprints, on the Village Map as Farms), the `rules()` generator's
`countryWorkplaceRule_fishers_hut_fisherman`; `QaNewWorkplacesGameTests` rules for their trades only,
`qaNewWorkplaceNamesAndText`, `qaOnlyTheBandstandIsADecoration`; `TableJobsGameTests.theTableSendsAShippedBuildsJobsAndBeds`
(a Fisherman from the barrel); `TableSearchGameTests.searchFindsABuildById`, `searchFindsBuildsByTheJobsTheyBring` and
`QaBlueprintTableGameTests` (search by id and job); `QaBlueprintLibraryVerify1108bGameTests.aNewVillageStartsWithTheWikisSetAndTheRestLockedOrRare`
(starting set); `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`, `withoutTheModsEveryBuildIsTheVanillaBuild`.

Library-wide checks it must pass: `B648GameTests` and `QaWindowsAndWards1028BugTests` (no pane or bar joined on one side
with air on the other, as stored and as the game joins it), `QaFurnishedRooms1031GameTests` (no standing lantern on a
room's floor; every indoor chest against a wall, a solid block, a bed or another chest), `BlueprintsStayGameTests`
(lamps on a block that holds them; no dirt path or farmland under a solid block; beds whole), `QaMirroredBuildsGameTests`
(doors, beds and double chests whole, turned and mirrored: both levels are in `StarterBlueprints.ALL`),
`QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`, `QaBuildReachGameTests`, `QaTurnedFramesGameTests` (frames and
paintings hang turned and mirrored), `QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`,
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `StyleGameTests.chimneysStayWholeInEveryStyle`
(a campfire on a stack of full blocks keeps every block of that stack full and solid in every style).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair; `open_panes`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no
CHECK line for either level, and `holes.py` passes. The old `fishers_hut` and `fishers_hut_2` (and their modded copies)
are listed in `owner_rules_baseline.json` for a lantern standing on the ground; the rebuild must come out clean and the
ship step removes those four baseline entries. Saves stay safe: the ship step keeps the old drawings in
`blueprint_history/` (`history.py`; a site begun on the old drawing finishes as drawn) and regenerates the modded copies.

## Free
Everything not listed above: shape, plan, roof, materials, colors, how many storeys, how the jetty is laid out within its
5 rows (beyond its deck, posts, end and bank rules), rails, bollards, a boat shed, nets, a smokehouse room or not, a
chimney, a bed or none, how many chests beyond none, where the barrel, smoker, chests and doors stand (within the rules
above), lighting, and what Level II adds beyond its smoker. (The wiki's line in `docs/wiki/new-workplaces.md` is updated
by the ship step to match.)
