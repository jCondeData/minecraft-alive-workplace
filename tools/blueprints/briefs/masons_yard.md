# Mason's Yard: the brief

Family id(s): `masons_yard`, `masons_yard_2` (never renamed; "Mason's Yard" and "Mason's Yard II" in `en_us.json`,
`blueprint.aliveworkplace.masons_yard` / `_2`, spelled exactly so: `QaNewWorkplacesGameTests.qaNewWorkplaceNamesAndText`)
Variants: village (none: no `structure/village/*masons_yard*`, no village pool names it), modded (none to draw:
`modded_library.py` makes `structure/modded/masons_yard.nbt` and `_2` from the vanilla drawings and the ship step reruns
it; `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`, `withoutTheModsEveryBuildIsTheVanillaBuild`), looks
(none). The Table's style buttons (`BlueprintStyles`) restyle the drawing automatically; no test names a styled yard.

## What it is for
The Mason's workplace. A Mason's job block is the vanilla **stonecutter** (`PoiTypes.MASON`). The building gives one
Mason (Level I) or two (Level II) a stonecutter each. In this mod the Mason is a crafter for the builders
(`craft/CraftJobs`: every stonecutter cut is his, and anything made of stone or bricks): he fetches ingredients from
his own chests and the village's, makes the piece at his stonecutter and carries it to the builder's chests
(`craft/CrafterWork`); his "own chests" are every storage block within **8 blocks across and 4 up or down** of his
stonecutter (`SupplyContainers.find(level, station, ...)`, `RADIUS` 8, `VERTICAL` 4), and with none, what he carries
home is dropped by his workstation. A player picks it at the Blueprint Table (in every new village's starting set,
`blueprint_unlocks/starting.json`) and hands it to a builder; the Steward builds it by himself in a `workshops` zone
when a Mason has no workstation (`steward_rules/workplace_masons_yard.json`: `worker_without_workstation` for
`minecraft:mason`, `wanted: true`; priority 62, cooldown 2 days), and his renewal of an old Mason's house rebuilds it as
this one (`steward_renewal/mason.json`: fits the old footprint plus `Renewals.MARGIN` = 3 blocks a side, front where
the old door was). On the Village Map it is a Workshops building (`VillageMaps.kindOf`, by name). Questbook chapter 2
asks the player to build one (`chapters.py`, `built("masons_yard")`); finishing each level grants the hidden
advancement `built/masons_yard` / `built/masons_yard_2`. It counts nothing for beauty (`Decorations.points` 0,
`QaNewWorkplacesGameTests.qaOnlyTheBandstandIsADecoration`). Jewellers and Gem Growers also work at a stonecutter
(`ModVillagers.JEWELLER`, `GEM_GROWER`), but a jobless villager by one becomes a Mason.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `masons_yard` | One Mason's workstation (one stonecutter), a fenced yard (at least 8 fence or wall blocks), his chest by the stonecutter | Starting set (`starting.json`); Steward rule `workplace_masons_yard`; renewal list `mason.json`; questbook chapter 2 |
| II | `masons_yard_2` | A second stonecutter (exactly two), so a second Mason works there; still fenced | By name (`BlueprintUpgrades`: `<name>_2` upgrades `<name>`). A builder who finishes Level I sells Level II (`UpgradeOffers.offer`, up to 3 uses, price `4 + solid blocks / 40`, clamped 6-32 emeralds) |

**Exactly two levels.** No `masons_yard_3`: a third level would change the counted upgrades in
`BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (exactly 64) and is a new feature.

**Upgrade rules the code reads** (`BlueprintUpgrades`, `BuilderGameTests.starterUpgradesKeepMostOfTheirBase`):
- Level II is built **over a finished Level I at the same origin and the same front** (only what changed is built), so
  Level I's cells must sit at the same template coordinates in Level II.
- Level II is **at least as big as Level I in x, y and z**.
- Level II **keeps at least 60 % of Level I's non-air blocks unchanged**: the same block with the same state at the same
  position (today it keeps 360 of 363). Draw the two together, Level II growing out of Level I.
- Level I's stonecutter stays where it is in Level II (a Mason already working there keeps his block); the second
  stonecutter is new.

## Must have (each level)
- **Job blocks: exactly one `minecraft:stonecutter` in Level I and exactly two in Level II**
  (`QaNewWorkplacesGameTests.qaYardLodgeAndMapRoomHoldTheirBlocks`: "stonecutters ... the spec says 1 / 2";
  `CraftWorkplacesGameTests.craftWorkplaceBuilt_masons_yard` / `_masons_yard_2`: one per worker). **No other job
  block of any job**: none of blast furnace, smithing table, grindstone, fletching table, cartography table, barrel,
  lectern, composter, cauldron, smoker, brewing stand, loom or **crafting table** (that test counts each and wants 0;
  a crafting table is the Carpenter's), and none of the mod's workstations (Blueprint Table, Builder's Bench, Glass
  Kiln, ...): each takes a villager for another job. A filled cauldron counts as the Leatherworker's job site too, so
  no cauldron of any kind. A chest or furnace is not a job block.
- **Each stonecutter is reachable on foot from outside**, through the way in, within a player's reach (2 across, 1 up
  or down) of a cell a villager walks to (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`),
  and a real Mason standing in front of the yard walks in and claims it (`craftWorkplaceBuilt_*`, see "Rules tests
  check"). In Level II two Masons must each claim a *different* stonecutter ("two workers share ...").
- **The fence: at least 8 blocks in `#minecraft:fences` or `#minecraft:walls`** in each level
  (`qaYardLodgeAndMapRoomHoldTheirBlocks`: "fences or walls (the yard's fence)"). Fence gates are not in either tag and
  do not count. Every fence or wall run joins at its corners (`check.py` owner rule; see below).
- **No gate or other barrier a villager can't pass on the only way in.** Vanilla villagers open wooden doors but not
  fence gates or trapdoors: the reach test's walk treats a gate as open, but the real build test needs a Mason to walk
  from the street to his stonecutter. A gate is fine only beside an open way in.
- **Storage.** At least **one chest within 8 across and 4 up or down of each stonecutter** (`CrafterWork`'s own chests,
  `SupplyContainers.find(level, station, ...)`: the Mason's ingredients and what he brings home). No test counts it;
  the code reads it, and without one his leftovers are dropped on the ground. Today Level I has one chest, Level II two
  (one per stonecutter). Chests are drawn empty (builders never fill or copy containers). Every chest is reachable on
  foot (`QaBuildReachGameTests`) and an indoor chest stands against a wall, a solid block, a bed or another chest
  (`QaFurnishedRooms1031GameTests`). **No barrel** (a Fisherman's job block).
- **Beds and residents.** None required: no test or code asks this family for a bed, and the old yard has none. A bed
  is free; if drawn, every bed counts as a village bed for the Steward (`StewardConditions.beds`), its head must be
  reachable on foot (`QaBuildReachGameTests`), its foot has its head one block towards its facing
  (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`) and it stays whole turned and mirrored
  (`QaMirroredBuildsGameTests.everyBuildsDoorsBedsAndDoubleChestsStayWholeTurnedAndMirrored`).
- **Furniture spots.** No kind is required by any test for these ids (no `blueprint_furniture/masons_yard*.json` today;
  not a home, `FurnishedHomesGameTests` doesn't list it). Every furniture piece the drawing has is recorded as a spot
  (`rebuild.py`), and each spot's blocks are exactly the template's blocks there
  (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); nothing small stands on air
  (`FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece`); every chair stair faces its table, turned and
  mirrored too (`QaChairFacingGameTests`).
- **A way in on the z = 0 side** (the front), at ground level, that a villager 1.95 blocks tall walks through; if it is
  a wooden doorway it has room for him (`QaDoorwayClearanceGameTests`; B170: a carpet just inside a 2-tall doorway
  stopped a worker for good). Any upper floor or loft holding a stonecutter or chest is reached by a ladder or stairs
  from inside (B149, `QaBuildReachGameTests`).
- **No entity** (villager, frame, painting ...) and no jigsaw or structure void in either file
  (`craftWorkplaceBuilt_*`: "comes with a villager").
- Every block is one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).

## Fits
- **Plot: at most 18 wide x 22 deep x 28 tall**, both levels (`CraftWorkplacesGameTests.craftWorkplaceBuilt_*` builds
  it at helper (12, 2, 8) in the 30 x 30 x 30 `huge_area`, so x 12..29, y 2..29, z 8..29; the builder's bench at (3, 2, 3)
  and his chests at x = 3, z 6/8/10 stand west of it). y = 0 sits on the clicked block; the front is the z = 0 side.
- **Material budget: at most 81 stacks per level** (that test stocks three chests of 27 with exactly what the supply
  report says is missing, each item rounded up to whole stacks; Level II is built from nothing in its own test, not
  over Level I). The old drawings are 363 and 388 blocks.
- **Build time:** the whole build, then the Mason(s) walking in and claiming their stonecutters, within **12,000
  ticks** (build delay 2, builders' help off). A much bigger build risks it.
- **Declared size:** `StarterBlueprints.MASONS_YARD` and `MASONS_YARD_2` are both `new Vec3i(11, 9, 11)` today (no
  basement); if a size changes the ship step changes those lines (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`
  and `craftWorkplaceBuilt_*` assert the file's size equals the entry), and `modded_library.py` reads the sizes from that
  file. A basement is allowed only with the `Entry`'s basement field set (and then `QaBasementGameTests` applies).
- **Front and way in:** the z = 0 side faces the player who places it and the road of a Steward plot; the way in is
  there. The arriving workers wait 3 blocks in front of the front edge, at template **(1, 0, -3)** and, in Level II,
  **(4, 0, -3)** (helper (13 + 3i, 2, 5)), on the ground level of the build's y = 0, and must walk in from there.
- **Neighbours:** a workplace in a Steward `workshops` zone; the Steward keeps 2 blocks clear round its plot
  (`Plots.CLEAR`) and wants the ground under it within 4 blocks of level (`Plots.MAX_STEP`). A renewal takes it only
  where it fits the old Mason's house's footprint plus 3 a side, so a smaller footprint renews more old houses (free).

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing if needed, keeping what each checks, dropping none):
- `CraftWorkplacesGameTests.craftWorkplaceBuilt_masons_yard` and `craftWorkplaceBuilt_masons_yard_2` (the `builds()`
  generator; `HOUSE` (12, 2, 8), `BENCH` (3, 2, 3), `CHESTS`, `BUILDER` (5, 2, 3)): the file's size equals the
  `StarterBlueprints` entry; no entity; job-block counts as above; built from its supply report (at most 81 stacks);
  then one Mason (Level II: two) spawned at helper (13 + 3i, 2, 5) with the trade's brain must hold a stonecutter
  inside the box as his `JOB_SITE`, still a Mason, no two sharing one, within the 12,000 ticks.
- No test pins a cell inside the drawing (no stonecutter, chest or door coordinate is read by any test or Java class);
  where things stand is free within the rules above.

Content checks: `QaNewWorkplacesGameTests.qaYardLodgeAndMapRoomHoldTheirBlocks` (stonecutters 1 / 2; fences or walls
at least 8 in each level); `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (the upgrade rules above).

By id only (the drawing doesn't matter beyond the rules above): `CraftWorkplacesGameTests.craftWorkplacesAreInTheBlueprintTable`
(both in the Table, starter blueprints, on the Village Map as Workshops), `aWorkerAtHisBlockWantsNoWorkplace`, the
`rules()` generator's `craftWorkplaceRule_masons_yard_mason`; `QaNewWorkplacesGameTests.rulesForTheirTradesOnly`
(`qaNewWorkplaceRules_mason`), `qaNewWorkplaceNamesAndText`, `qaOnlyTheBandstandIsADecoration`;
`QaBlueprintLibraryVerify1108bGameTests.aNewVillageStartsWithTheWikisSetAndTheRestLockedOrRare` (starting set);
`QaQuestbookClaims1116eGameTests.qaEveryBuildAQuestSaysToPickIsOnANewVillagesTable`;
`ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`, `withoutTheModsEveryBuildIsTheVanillaBuild`.

Library-wide checks it must pass: `B648GameTests` and `QaWindowsAndWards1028BugTests` / `QaBuilders1028GameTests`
(no pane or bar joined on one side with air on the other, as stored and as the game joins it),
`QaFurnishedRooms1031GameTests` (no standing lantern on a room's floor; every indoor chest against a wall, a solid
block, a bed or another chest), `BlueprintsStayGameTests` (lamps on a block that holds them; no dirt path or farmland
under a solid block; beds whole), `QaMirroredBuildsGameTests` (doors, beds and double chests whole, turned and
mirrored), `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`, `QaBuildReachGameTests`,
`QaLedgerFramesHaulGameTests` / `QaTurnedFramesGameTests` (frames and paintings hang turned and mirrored),
`QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`,
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `StyleGameTests.chimneysStayWholeInEveryStyle`
(only if a campfire stands on a solid stack: it must stay solid in every style).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair; `open_panes`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no
CHECK line for either level, and `holes.py` passes. The old `masons_yard_2` (and its modded copy) is listed in
`owner_rules_baseline.json` for a lamp standing on a floor; the rebuild must come out clean and the ship step removes
those two baseline entries. Saves stay safe: the ship step keeps the old drawings in `blueprint_history/` (`history.py`;
a site begun on the old drawing finishes as drawn) and regenerates the modded copies with the family.

## Free
Everything not listed above: shape, plan, roof or none, materials, colors, how open or enclosed the yard is, a shed,
lean-to, hoist, crane, stock piles or none, how many storeys, a bed or none, how many chests beyond the minimum, where
the stonecutters, chests and way in stand (within the rules above), lighting, and what Level II adds beyond its second
stonecutter. (The wiki's one-line description in `docs/wiki/new-workplaces.md` is updated by the ship step to match.)
