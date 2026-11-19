# Berry Farm: the brief

Family id(s): `berry_farm`, `berry_farm_2` (never renamed; "Berry Farm" and "Berry Farm II" in `en_us.json`,
`blueprint.aliveworkplace.berry_farm` / `_2`; hidden advancements `built/berry_farm` and `built/berry_farm_2`, awarded
by id, `QaBuiltAdvancements1116bGameTests.deconstructionsAwardNothing`)
Variants: village (none: no `structure/village/*berry_farm*`), modded (none to draw: `modded_library.py` makes
`structure/modded/berry_farm*.nbt` from the vanilla drawings and the ship step reruns it), looks (none: the Table's style
buttons, Stonework, Sandstone, Dark Oak, Cherry and Apricorn, restyle it through `BlueprintStyles`; no test names a
styled Berry Farm).

## What it is for
**The Orchard Keeper's workplace** and a small food source for the village. The Orchard Keeper's job block is the
vanilla **composter**, which he shares with the Farmer, Florist, Composter and Berry Breeder (`work/Stations`). A villager
becomes the Orchard Keeper when a player sneak-right-clicks him by the composter with sweet berries, glow berries or an
apple. A village's Steward also gives a jobless villager that job at **a free composter inside a finished Berry Farm's box
(plus 2 blocks round it)** for as long as nobody of that job works there (`city/StewardJobs.BUILDING_JOBS`,
`BUILDING_MARGIN`, `buildingJob`, `worked`). At work (`orchard/OrchardWork`) he walks round everything within **16 blocks
across, 4 down and 10 up of his composter** and picks whatever is ripe without breaking the plant. Ripe means a sweet
berry bush of age 2 or more, cave vines with berries, full-grown cocoa, and with Cobblemon its apricorns and berry plants
(`orchard/Fruit.isRipe`). He reaches **5.5 blocks** with his picking pole (`OrchardWork.REACH`) and carries about 48
fruit at a time to **the chests by his composter**, where the Porter and the Chef take the fruit (`store/Porters`). The
Steward builds one by himself in a `farms` zone (`steward_rules/workplace_berry_farm.json`: an Orchard Keeper is wanted
and has no workstation, Hamlet rank and up, priority 62, cooldown 2 days; `steward_rules/food_berry_farm.json`: the
village is short of food and has no Berry Farm, priority 80, at most 1). When the Steward renews an old Orchard Keeper's
house he rebuilds it as this one (`steward_renewal/orchard_keeper.json`, the only building on that list). On the Village
Map it is a Farms building (`hall/VillageMaps.kindOf`, by name). It counts nothing for beauty (`Decorations.points` 0).
An Expert Orchard Keeper sells Level I for 10 emeralds (`ModTrades`), and the Table lists it as one sold by its villager
(`BlueprintUnlocks.VILLAGER_SALES`).

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `berry_farm` | One Orchard Keeper's composter, a chest for his harvest near it, and a planted fruit garden of sweet berry bushes he can pick | Blueprint Table; Expert Orchard Keeper trade (10 emeralds); Steward rules `workplace_berry_farm` and `food_berry_farm`; renewal list `orchard_keeper.json` |
| II | `berry_farm_2` | **More fruit, and a second kind**: the player guide promises glow berries (cave vines with berries) besides more sweet berry bushes, all in the same keeper's reach. Still **one** Orchard Keeper | Upgrade of I (`BlueprintUpgrades`: id + `_2`); sold by the builder who finished I (`UpgradeOffers.offer`) |

**Exactly two levels**: no `berry_farm_3` (it would change `BuilderGameTests.starterUpgradesKeepMostOfTheirBase`'s
count of 64 upgrades). No basement in either level (no `Entry` basement; a cellar would need `StarterBlueprints`,
`WITH_BASEMENTS` and the basement tests).

## Must have (each level)
- **Job block: exactly one `minecraft:composter` per level, inside the build's box.** The code wants one: the Steward
  gives the Orchard Keeper job at a composter in the box only while no Orchard Keeper works at one there, so **a second
  composter is handed to a Farmer** (`StewardJobs.worked` and `jobAt`; `StewardJobsGameTests.anOrchardKeeperAtTheBerryFarmsComposter`
  checks exactly that: a second composter in the farm goes to a Farmer). No test counts composters in the drawing, so
  this is the code's rule, not a test's.
- **No other job block** (the code): any other block that is an acquirable job site (barrel, lectern, smoker, furnace,
  blast furnace, brewing stand, cauldron, crafting table, fletching table, cartography table, grindstone, stonecutter,
  smithing table, loom, or any of the mod's workstations) takes a villager into another job. So storage is **chests**,
  never barrels. (The retired Fruit Basket, `aliveworkplace:fruit_basket`, still works where placed but is not used in new
  drawings: `work/Stations`.)
- **A chest within 8 blocks across (x and z) and 4 up or down of the composter** (`SupplyContainers.find`, `RADIUS` 8,
  `VERTICAL` 4): without one the keeper has nowhere to put the harvest and idles (the same composter's other jobs use
  the same search). He walks to within 3 blocks of the nearest one to unload
  (`OrchardWork`: `walkTo(containerNear(basket), 3.0)`). At least one `minecraft:chest` per level. Chests are drawn empty
  (a blueprint never carries items: `BuilderGameTests.blueprintDataNeverHandsOutItems`). A chest indoors stands against a
  wall, a solid block, a bed or another chest (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`).
- **Fruit the keeper can pick, from the moment it is built**:
  - Level I: sweet berry bushes; Level II: sweet berry bushes and cave vines with glow berries (the documented function
    of each level; no test counts them, so how many is free, but a Berry Farm with nothing to pick fails its purpose).
  - Every fruit plant within **16 across, 4 below and 10 above the composter** (`OrchardWork.RADIUS`, `DOWN`, `UP`).
  - Every fruit plant within **5.5 blocks of a cell a villager can stand on and walk to** from the composter without
    going through a berry bush (`OrchardWork.REACH`; vanilla villagers path round sweet berry bushes, which hurt them).
    Fruit he can't reach is given up on for a while (`OrchardWork`, "Fruit they can't get to").
  - Every plant on a block that holds it, so the builder can place it and it stays: a sweet berry bush on grass, dirt,
    podzol, coarse dirt or farmland; cave vines hanging under a sturdy block or other vines (vanilla's survival rules;
    `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable` refuses blocks a builder can't place).
- **The keeper gets in on foot**: a real villager must be able to walk from outside the front to the composter, to
  within 3 blocks of the chest and to his picking spots. **Villagers do not open fence gates** (vanilla, kept by
  `work/PathObstacles`: "doors, trapdoors and fence gates keep vanilla's rules"); they open wooden doors. So if the
  garden is closed in, its way in is a wooden door or an open gap, not only a gate. (`QaBuildReachGameTests` treats a gate
  as open, as a player opens it, so this one is the code's rule, not a test's.)
- **Every chest and the composter reachable on foot** from the ground round the build: within 2 across and 1 up or down
  of a walked cell (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`).
- **Beds and residents: none required, none counted.** The Orchard Keeper is a worker, not a resident; no test counts
  beds here. A bed, if drawn, makes the build a home for `Homes.at` and then must pass the bed tests
  (`HomesSpecGameTests.everyBedOfEveryStarterHouseIsAtHomeTurnedAnyWay`, `FurnishedRoomsGameTests.everyBedHasItsHeadToAWall`,
  `QaMirroredBuildsGameTests`, `BlueprintsStayGameTests`).
- **Furniture spots: none required.** The family has no `blueprint_furniture/berry_farm*.json` today, and no job or test
  needs a furniture kind. If the recipe adds spots, every furnished-room check applies to that level: each spot's blocks
  exactly the template's (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`), every floor piece indoors lit above
  7 by a hung lamp or one on a table (`FurnishedRoomsGameTests.everyFurnishedRoomHasALight`), nothing on air
  (`everyFurnishedPieceHasWhatItStandsOn`, `FloatingFurnishingsGameTests`), nothing in a door's way
  (`noFurnitureStandsInADoorsWay`), chairs facing their table (`QaChairFacingGameTests`), no standing lantern on a room's
  floor (`QaFurnishedRooms1031GameTests`).
- **No entity in any file**: no villager, animal, frame or painting (the old ones have 0; `QaTurnedFramesGameTests` checks
  any frame or painting that is drawn).
- No jigsaw or structure void; every block one a builder can place
  (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).
- **Glass**: no pane or bar joined on one side only with air on the other (`B648GameTests`, `check.py`'s `open_panes`).
- If a wooden door is drawn, its doorway leaves a villager 1.95 blocks (`QaDoorwayClearanceGameTests`, `check.py`'s
  `doorway_problems`). Any raised floor holding the chest or the composter is reached by a ladder or stairs from inside.
- A chimney is optional, but a campfire on a stack must stand on blocks that stay solid in every style
  (`StyleGameTests.chimneysStayWholeInEveryStyle`).

## Kept cells (the upgrade, and every refresh)
- **II over I**: same origin and front, II at least I's size in x, y and z, and **at least 60 % of I's non-air blocks
  stay the same block (same state) in the same cell** (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`). So I's
  composter, chest and garden stay where they are in II. Draw both levels together.
- Every chest stays in its cell as the same block in II (a builder never breaks containers while clearing,
  `MaterialRules.isProtected`), and so does the composter.
- **A refresh never moves a container or a job site** (`build/Refreshes.kept`: protected blocks, someone's bed, every
  acquirable job site, which a composter is). The live drawings (no `blueprint_history/berry_farm*` yet), their modded
  copies (a `carved_wood:spruce_chest` there) and both levels have these template cells (x, y, z):
  - **composter (4, 1, 7)**, **chest (6, 1, 7)** facing north, single. Nothing else is kept.
  - **The new drawing must not put a wall, a path block a villager needs, a bush or a door on these two cells**, nor
    anything they would block. **The composter matters most**: a refreshed old Berry Farm keeps its old composter, so a
    new composter elsewhere leaves it with two, and the second goes to a Farmer (above). The cleanest answer is the
    composter at (4, 1, 7) in the new drawing; otherwise the ship step must show a refreshed old Berry Farm ends with one
    working composter. (`RefreshMatchesFreshBuildGameTests.differences` skips chests and job-site blocks, so no refresh
    test catches this.)
  - `QaRefreshHistory1028GameTests.qaEveryShippedOlderDrawingIsOfferedARefresh`: every older drawing that differs from
    the new one is offered a refresh (automatic once the ship step keeps the old drawings with `history.py`).

## Fits
- Plot (y = 0 sits on the clicked block; the front is the z = 0 side):
  - **Level I: at most 13 wide x 13 deep x 16 tall.** `BuilderGameTests.buildsBerryFarm` builds it in `big_area`
    (22 x 18 x 22) at helper origin (9, 2, 9), in the batch `starter_builds` beside other builds 5 blocks away; the
    builder's bench (2, 2, 2), his chest (2, 2, 4) and four stock barrels at x 1-5, z 4 stand west and north of it.
    Blocks above the area's height are never cleared.
  - **Level II**: no test builds it; at least I's size in every direction (above). Keep it within reason for a Steward's
    `farms` plot (below).
  - No Berry Farm test uses `huge_area` (30 x 20 x 30).
- **Declared sizes**: `StarterBlueprints.BERRY_FARM` is `new Vec3i(11, 5, 9)` and `BERRY_FARM_2` `new Vec3i(11, 6, 15)`
  today (no basement); if a size changes the ship step changes those lines
  (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable` asserts file size = entry; `modded_library.py` reads the
  sizes from that file).
- **Material budget (Level I): at most 108 stacks** (`buildStarter` stocks four barrels of 27 slots with exactly what the
  plan needs, each item in whole stacks; more fails "test needs more barrels"). Unstackable items take a slot each.
- **Build time and block budget (Level I)**: `BuilderGameTests.buildsBerryFarm` sets day time 2,000 (`setup`), build
  delay 2, builders' help off, and must reach DONE with every block right (`assertBuilt`) within its **9,000-tick**
  timeout. The builder works only until day time 11,000 (`ModVillagers.BUILDER_SCHEDULE`), so the same ~9,000 ticks is
  all the working time there is. Builders in tests lay about one block per 10 ticks with the walking and fetching, and
  earlier rebuilds had to cut to about 1,000 placed blocks for a one-level workshop in a working day. **Keep Level I to
  about 650 placed (non-air) blocks, never over 900** (the old one is 186). Level II has no build test, but a builder
  upgrading a real Berry Farm builds only what changed; keep **II to about 1,000 placed blocks in all** so the upgrade
  fits a working day.
- **Front and way in**: the z = 0 side faces the player who places it ("the gold edge of the outline is the front") and
  the road of a Steward plot. The keeper walks in from that side (see "The keeper gets in on foot").
- **Neighbours**: a workplace in a Steward `farms` zone; the Steward keeps 2 blocks clear round its plot and wants the
  ground under it within 4 blocks of level (`city/Plots.MARGIN`, `MAX_STEP`). The builder soak lays every Level I 18
  blocks apart in rows 26 deep (`command/Soak`, packtest only), which the 13 x 13 limit already keeps.
- **Steward renewal fit** (`steward_renewal/orchard_keeper.json` lists only `berry_farm`; `Renewals.fit`): when an old
  Orchard Keeper's house is renewed, Level I goes on its plot with its front (z = 0) on the old house's front line,
  centred on the old door, and must lie within the old footprint **plus 3 blocks to each side and 3 back**
  (`Renewals.MARGIN`); if it doesn't fit, that old house isn't renewed. A smaller Level I footprint renews more old houses.

## Rules tests check
Built whole: `BuilderGameTests.buildsBerryFarm` (`big_area`, origin (9, 2, 9), 9,000 ticks, materials from at most four
barrels of 27: the build reaches DONE and every block of the plan is right).

By id only (the drawing doesn't matter beyond the size and box rules above):
- `StewardJobsGameTests.anOrchardKeeperAtTheBerryFarmsComposter`: records a finished `berry_farm` at helper (3, 2, 3) and
  sets its own composters at template (3, 0, 3) and (5, 0, 3); both must lie inside the build's box plus 2 (any size of
  at least 6 wide, 1 high and 4 deep does). The first gets an Orchard Keeper, the second a Farmer.
- `StewardHomesRulesGameTests.foodShortGetsABerryFarmInTheFarms` and `aVillageShortOfFoodWithABerryFarmGetsARanch`
  (the food rule, `farms` zone), `WorkplacesGameTests` (the `workplace_berry_farm` rule for `orchard_keeper`, Hamlet),
  `StewardRulesGameTests` (rule list), `QaStewardReasonWordsBugTests` (`food_berry_farm` reason words).
- `QaBuiltAdvancements1116bGameTests.deconstructionsAwardNothing` (`built/berry_farm`).

Library-wide checks it must pass: `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (60 % kept, II no smaller,
64 upgrades) and `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`; `QaBuildReachGameTests`;
`QaMirroredBuildsGameTests` (doors, beds and double chests whole turned and mirrored); `QaTurnedModBlocksGameTests`
(facing blocks turn with the build); `QaTurnedFramesGameTests`; `HomesSpecGameTests` (only if a bed is drawn);
`QaDoorwayClearanceGameTests`; `B648GameTests`; `QaFurnishedRooms1031GameTests`; `BlueprintsStayGameTests` (lamps on a
block that holds them; no farmland or dirt path under a solid block; beds whole);
`QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`;
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural` (a new material needs a plural in `en_us.json`);
`StyleGameTests.chimneysStayWholeInEveryStyle`; `ModdedLibraryGameTests` (a modded copy of each level, the vanilla build
without the mods); the furniture tests above if spots are added.

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair, upper floors by stairs or a ladder, nothing that falls such as gravel over air;
`open_panes`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no CHECK line for either level, and
`holes.py` passes. The old drawings have no entries in `owner_rules_baseline.json`; the rebuild must come out clean.
Saves stay safe: the ship step keeps the old drawings in `blueprint_history/` and regenerates the modded copies.

## Free
Everything not listed above: shape, plan, materials, colors, style, whether it is an open garden, a walled yard or has a
shed or roof, where the composter and chest stand (within the kept cells and the reach rules), how many bushes and vines
and how they are laid out, what else grows there, paths, fences, lighting, seats, beds (with their rules), furniture, and
what Level II adds beyond more fruit and glow berries for the same keeper.
