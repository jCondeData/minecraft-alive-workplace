# Fletcher's Lodge: the brief

Family id(s): `fletchers_lodge`, `fletchers_lodge_2` (never renamed; "Fletcher's Lodge" and "Fletcher's Lodge II" in
`en_us.json`, `blueprint.aliveworkplace.fletchers_lodge` / `_2`, spelled exactly so:
`QaNewWorkplacesGameTests.qaNewWorkplaceNamesAndText`; hidden advancements `built/fletchers_lodge` / `_2`)
Variants: village (none: no `structure/village/*fletcher*`, no village pool names it), modded (none to draw:
`modded_library.py` makes `structure/modded/fletchers_lodge.nbt` and `_2` from the vanilla drawings and the ship step
reruns it; `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`, `withoutTheModsEveryBuildIsTheVanillaBuild`),
looks (none). The Table's style buttons (`BlueprintStyles`) restyle the drawing automatically.

## What it is for
The workplace of the two trades that share the vanilla **fletching table** (`PoiTypes.FLETCHER`): the **Fletcher** and
the mod's **Lumberjack** (`work/Stations`; a jobless villager who takes a fletching table becomes either, at random,
36.17 RandomJobs). The **Fletcher** makes the guards' gear (`craft/FletcherWork`): a bow or crossbow for a guard without
one, and spectral arrows 8 at a time, crafted from **his own chests** (every storage block within **8 blocks across and
4 up or down** of his fletching table: `SupplyContainers.find(level, station, ...)`, `RADIUS` 8, `VERTICAL` 4) plus the
porters' and armorers' stashes, and carried to the chests by the Guard Post; with no chest of his own, what he carries
home is dropped by his table (`CrafterWork`). The **Lumberjack** (`wood/LumberjackWork`) fells natural trees within
**16 blocks across, 6 down and 10 up** of his fletching table (`RADIUS` 16), replants, feeds saplings bone meal, and
takes his **axe, saplings and bone meal from the chests within 8 across and 4 up or down of that table** and puts the
logs back there: **with no chest in that reach he never gets an axe and does nothing**. A player picks the lodge at
the Blueprint Table (in every new village's starting set, `blueprint_unlocks/starting.json`) and hands it to a
builder; the Steward builds it by himself in a `workshops` zone when a Fletcher or Lumberjack has no workstation
(`steward_rules/workplace_fletchers_lodge.json`: `worker_without_workstation` for `minecraft:fletcher` and
`aliveworkplace:lumberjack`, `wanted: true`; priority 62, cooldown 2 days), and his renewal of an old Fletcher's or
Lumberjack's house rebuilds it as Level I (`steward_renewal/fletcher.json`, `lumberjack.json`; see "Fits"). On the
Village Map it is a Workshops building (`VillageMaps.kindOf`, by name). It counts nothing for beauty
(`Decorations.points` 0, `QaNewWorkplacesGameTests.qaOnlyTheBandstandIsADecoration`).

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `fletchers_lodge` | One fletching table (one worker: a Fletcher or a Lumberjack), his chest in reach of it, at least 20 logs and a target or hay block (see "Must have") | Starting set (`starting.json`); Steward rule `workplace_fletchers_lodge`; renewal lists `fletcher.json`, `lumberjack.json` |
| II | `fletchers_lodge_2` | A second fletching table (exactly two), so a Fletcher and a Lumberjack both work there, each with a chest in reach | By name (`BlueprintUpgrades`: `<name>_2` upgrades `<name>`). A builder who finishes Level I sells Level II (`UpgradeOffers.offer`, up to 3 uses, price `4 + solid blocks / 40`, clamped 6-32 emeralds) |

**Exactly two levels.** No `fletchers_lodge_3`: a third level would change the counted upgrades in
`BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (exactly 64) and is a new feature.

**Upgrade rules the code reads** (`BlueprintUpgrades`, `BuilderGameTests.starterUpgradesKeepMostOfTheirBase`):
- Level II is built **over a finished Level I at the same origin and the same front** (only what changed is built), so
  Level I's cells must sit at the same template coordinates in Level II.
- Level II is **at least as big as Level I in x, y and z**.
- Level II **keeps at least 60 % of Level I's non-air blocks unchanged**: the same block with the same state at the
  same position (today it keeps 330 of 370). Draw the two together, Level II growing out of Level I.
- Level I's fletching table stays where it is in Level II (the worker already there keeps his block); the second
  table is new.

## Must have (each level)
- **Job blocks: exactly one `minecraft:fletching_table` in Level I and exactly two in Level II**
  (`QaNewWorkplacesGameTests.qaYardLodgeAndMapRoomHoldTheirBlocks`: "fletching tables"; `CraftWorkplacesGameTests.craftWorkplaceBuilt_fletchers_lodge`
  / `_fletchers_lodge_2`: one per worker). **No other job block of any job**: none of blast furnace, smithing table,
  grindstone, stonecutter, cartography table, barrel, lectern, composter, cauldron (any kind), smoker, brewing stand,
  loom or **crafting table** (that test counts each and wants 0), and none of the mod's workstations, **the Chopping
  Block above all** (the Lumberjack's other block: he could take it instead, and the test wants his job site to be a
  fletching table), nor the Blueprint Table, Builder's Bench, mailbox, Fruit Basket, ... Each would take a villager
  for another job. A chest or furnace is not a job block. (Store stock in chests, not barrels.)
- **Each fletching table is reachable on foot from outside**, through the way in, within 2 across and 1 up or down of
  a cell a villager walks to (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`), and a real
  worker standing in the street in front walks in and claims it (`craftWorkplaceBuilt_*`). If a table is built in under
  solid blocks, some side of it needs a floor cell with 2 blocks of head room to stand on (`work/JobSiteApproach`). In
  Level II the two workers must each claim a *different* table ("two workers share ...").
- **Storage the jobs read: at least one chest within 8 across and 4 up or down of *each* fletching table** (above:
  without one the Lumberjack never works and the Fletcher drops his goods). No test counts it; the code reads it. Today
  each level has one chest, 4 and 8 blocks from the two tables. Chests are drawn empty (builders never fill or copy
  containers). Every chest is reachable on foot (`QaBuildReachGameTests`) and an indoor chest stands against a wall, a
  solid block, a bed or another chest (`QaFurnishedRooms1031GameTests`).
- **At least 20 blocks in `#minecraft:logs`** in each level (any log, wood, stripped log or stripped wood counts) and
  **at least one `minecraft:target` or `minecraft:hay_block`** in each level
  (`qaYardLodgeAndMapRoomHoldTheirBlocks`: "logs (a log cabin and pile)" >= 20, "targets or hay (the straw target)"
  >= 1). Where and how they are used is free. Logs in a building are safe from the Lumberjack: a tree needs natural
  leaves, and leaves a builder places are made persistent (`Trees`, `MaterialRules`).
- **Beds and residents.** None required: no test or code asks this family for a bed, and the old lodge has none. A bed
  is free; if drawn, it counts as a village bed for the Steward (`StewardConditions.beds`), its head must be reachable
  on foot (`QaBuildReachGameTests`), its foot has its head one block towards its facing
  (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`) and it stays whole turned and mirrored
  (`QaMirroredBuildsGameTests.everyBuildsDoorsBedsAndDoubleChestsStayWholeTurnedAndMirrored`).
- **Furniture spots.** No kind is required (no `blueprint_furniture/fletchers_lodge*.json` today; not a home). Every
  piece the drawing has is recorded as a spot by `rebuild.py`, each spot's blocks exactly the template's
  (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); nothing small stands on air
  (`FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece`); every chair stair faces its table, turned and
  mirrored too (`QaChairFacingGameTests`).
- **A way in on the z = 0 side** (the front), at ground level, that a villager 1.95 blocks tall walks through; a
  wooden doorway has room for him (`QaDoorwayClearanceGameTests`; B170: a carpet just inside a 2-tall doorway stopped a
  worker for good). **No fence gate or trapdoor on the only way in**: villagers open wooden doors, not gates or
  trapdoors, and the build test needs each worker to walk from the street to his table. Any upper floor or loft
  holding a table or chest is reached by a ladder or stairs from inside (B149, `QaBuildReachGameTests`).
- **No entity** (villager, frame, painting ...) and no jigsaw or structure void in either file
  (`craftWorkplaceBuilt_*`: "comes with a villager").
- Every block is one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).

## Fits
- **Plot (the build test): at most 18 wide x 22 deep x 18 tall**, both levels.
  `CraftWorkplacesGameTests.craftWorkplaceBuilt_*` builds it at helper (12, 2, 8) in the 30 x 20 x 30 `huge_area`
  (20 tall, not 30): x 12..29, z 8..29, y 2..19 (blocks above an area's height are never cleared and leak into later
  batches). The builder's bench at (3, 2, 3), his chests at x = 3, z 6/8/10 and the builder at (5, 2, 3) stand west
  of it. y = 0 sits on the clicked block; the front is the z = 0 side.
- **Plot (the renewal, Level I only): it must fit a vanilla plains fletcher house's footprint**
  (`QaRenewalGameTests.qaAFletchersHouseBecomesAFletchersLodgeFacingTheOldDoor`, `qaABuildingTooBigForThePlotIsPassedOver`;
  `Renewals.fit`): the lodge's front goes on the old door's side, flush with the old house's front edge, centred on the
  old door; it may reach `Renewals.MARGIN` = 3 past the old footprint on each side and behind. So Level I is **at most
  (old width along the door side + 6) wide and (old depth + 3) deep**. `plains_fletcher_house_1`'s door is on its
  11-long side and the house is 9 deep (its template; the measured house body, `OldHouses.Measure`, is smaller, about
  9 x 7). **Today's 11 wide x 10 deep passes; treat about 15 wide x 10 deep as the limit** and have the ship step run
  both tests on the new size. Too big and the renewal finds nothing to build (both tests fail).
- **Material budget: at most 81 stacks per level** (the build test stocks three chests of 27 with exactly what the
  supply report says is missing, each item rounded up to whole stacks; Level II is built from nothing in its own test,
  not over Level I).
- **Time and block budget (the lesson from earlier rebuilds):** the build test sets day time 2,000 and its timeout is
  12,000 ticks, but a builder works only until day time 11,000 (`ModVillagers.BUILDER_SCHEDULE`), i.e. about test tick
  9,000, and not again until test tick 23,000, after the timeout. After the build the workers still have to walk in and
  claim their tables. So **each level must be built in about 7,000 ticks**. Builders in tests lay about one block per
  10 ticks with the walking and fetching (B259: 145 blocks in about 1,500 ticks), so **keep each level to about 650
  non-air blocks** (the old drawings are 370 and 458; Level II counts in full, since its test builds it from nothing).
- **Declared size:** `StarterBlueprints.FLETCHERS_LODGE` is `new Vec3i(11, 11, 10)` and `FLETCHERS_LODGE_2`
  `new Vec3i(13, 11, 10)` today (no basement); if a size changes the ship step changes those lines
  (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable` and `craftWorkplaceBuilt_*` assert the file's size equals
  the entry), and `modded_library.py` reads the sizes from that file. A basement is allowed only with the `Entry`'s
  basement field set (and then `QaBasementGameTests` applies; a cellar never hands a villager a job).
- **Front and way in:** the z = 0 side faces the player who places it, the road of a Steward plot, and the old door's
  side in a renewal; the way in is there. The arriving workers wait 3 blocks in front of the front edge, at template
  **(1, 0, -3)** and, in Level II, **(4, 0, -3)** (helper (13 + 3i, 2, 5)), on the level of the build's y = 0, and must
  walk in from there.
- **Neighbours:** a workplace in a Steward `workshops` zone; the Steward keeps 2 blocks clear round its plot
  (`Plots.CLEAR`) and wants the ground under it within 4 blocks of level (`Plots.MAX_STEP`). The Lumberjack's trees are
  wherever they grow within 16 of his table; nothing in the drawing is read for them.

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing if needed, keeping what each checks, dropping none):
- `CraftWorkplacesGameTests.craftWorkplaceBuilt_fletchers_lodge` and `craftWorkplaceBuilt_fletchers_lodge_2` (the
  `builds()` generator; `HOUSE` (12, 2, 8), `BENCH` (3, 2, 3), `CHESTS`, `BUILDER` (5, 2, 3); day time 2,000, build
  delay 2, builders' help off, timeout 12,000): the file's size equals the `StarterBlueprints` entry; no entity;
  job-block counts as above; built from its supply report (at most 81 stacks); then the workers, spawned at helper
  (13 + 3i, 2, 5) with their trade's brain, must each hold a fletching table inside the build's box as `JOB_SITE`,
  keep their job, and no two share one. **Level I's worker is a Lumberjack** (not a Fletcher); **Level II's are a
  Fletcher and a Lumberjack**.
- `QaRenewalGameTests.qaAFletchersHouseBecomesAFletchersLodgeFacingTheOldDoor` (`huge_area`, vanilla
  `plains_fletcher_house_1` placed at (12, 2, 4)): the fletcher house's replacement is `fletchers_lodge`, its box
  within the old footprint plus 3 on every side, turned so its front faces the old door's side, its front edge on the
  old house's edge on that side. `qaABuildingTooBigForThePlotIsPassedOver`: with an arena first on the fletcher list,
  the arena is passed over and the lodge is taken (so the lodge must fit where the arena doesn't).
- No test pins a cell inside the drawing (no table, chest or door coordinate is read by any test or Java class); where
  things stand is free within the rules above.

Content checks: `QaNewWorkplacesGameTests.qaYardLodgeAndMapRoomHoldTheirBlocks` (fletching tables 1 / 2; logs >= 20;
targets or hay >= 1, each level); `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (the upgrade rules above).

By id only (the drawing doesn't matter beyond the rules above): `CraftWorkplacesGameTests.craftWorkplacesAreInTheBlueprintTable`
(both in the Table, starter blueprints, on the Village Map as Workshops), the `rules()` generator's
`craftWorkplaceRule_fletchers_lodge_fletcher` and `_lumberjack`; `QaNewWorkplacesGameTests.rulesForTheirTradesOnly`
(`qaNewWorkplaceRules_fletcher`, `_lumberjack`), `qaNewWorkplaceNamesAndText`, `qaOnlyTheBandstandIsADecoration`;
`QaBlueprintLibraryVerify1108bGameTests.aNewVillageStartsWithTheWikisSetAndTheRestLockedOrRare` (starting set);
`ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`, `withoutTheModsEveryBuildIsTheVanillaBuild`.

Library-wide checks it must pass (it is in `StarterBlueprints.ALL`): `B648GameTests` and `QaWindowsAndWards1028BugTests`
/ `QaBuilders1028GameTests` (no pane or bar joined on one side with air on the other, as stored and as the game joins
it), `QaFurnishedRooms1031GameTests` (no standing lantern on a room's floor; every indoor chest against a wall, a solid
block, a bed or another chest), `BlueprintsStayGameTests` (lamps on a block that holds them; no dirt path or farmland
under a solid block; beds whole), `QaMirroredBuildsGameTests` (doors, beds and double chests whole, turned and
mirrored), `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`, `QaBuildReachGameTests`,
`QaLedgerFramesHaulGameTests` / `QaTurnedFramesGameTests` (frames and paintings hang turned and mirrored),
`QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`,
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `StyleGameTests.chimneysStayWholeInEveryStyle`
(only if a campfire stands on a stack of full blocks: every block of that stack stays full and solid in every style).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair; `open_panes`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no
CHECK line for either level, and `holes.py` passes. The old drawings (and their modded copies) are listed in
`owner_rules_baseline.json` (2 and 3 lanterns standing on the floor) and have log-corner entries in `holes.py`'s
`ALLOW`; the rebuild must come out clean without them, and the ship step removes those baseline and ALLOW entries.
Saves stay safe: the ship step keeps the old drawings in `blueprint_history/` (`history.py`; a site begun on the old
drawing finishes as drawn) and regenerates the modded copies with the family.

## Free
Everything not listed above: shape, plan, roof, materials, colors, how many storeys, what the logs and the
target-or-hay are part of, a range, a drying rack, a log pile or none, a bed or none, how many chests beyond one in
reach of each table, where the tables, chests and way in stand (within the rules above), lighting, and what Level II
adds beyond its second table. (A sapling drawn on dirt would grow into a natural tree the Lumberjack fells; leaves the
builder places never count as a tree. The wiki's one-line description in `docs/wiki/new-workplaces.md` is updated by
the ship step to match.)
