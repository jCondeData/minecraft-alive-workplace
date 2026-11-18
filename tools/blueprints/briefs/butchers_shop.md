# Butcher's Shop: the brief

Family id(s): `butchers_shop` (one level; never renamed; "Butcher's Shop" in `en_us.json`,
`blueprint.aliveworkplace.butchers_shop`; hidden advancement `built/butchers_shop`)
Variants: village (none: no `structure/village/*butcher*`, no village pool or Steward rule names it), modded (none to
draw: `modded_library.py` makes `structure/modded/butchers_shop.nbt` from the vanilla drawing and the ship step reruns
it; `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`, `withoutTheModsEveryBuildIsTheVanillaBuild`), looks
(none). The Table's style buttons (`BlueprintStyles`) restyle the drawing automatically.

## What it is for
The Butcher's workplace, with a **freezer** cellar under it from the start (the owner, 2026-10-16: "a butcher maybe has
a freezer"). Its job block is the vanilla **smoker** (`PoiTypes.BUTCHER`): a jobless villager who takes it becomes a
Butcher (or, at random, the mod's Chef or Rancher, who share the smoker: `work/Stations`, 36.17). The mod's Butcher is
the village's **herder** (`ranch/HerderWork`, wired in `VillagerGoalPackagesMixin`): he breeds the cows, pigs,
chickens and rabbits within **16 blocks across and 6 up or down of his smoker** (`RanchWork.RADIUS`, `animals`), picks
up their drops, milks cows into buckets, and keeps it all in **the chests within 8 blocks across and 4 up or down of the
smoker** (`SupplyContainers.find(level, station, null)`, `RADIUS` 8, `VERTICAL` 4): breeding food and empty buckets
come from them, eggs, milk, meat and leather go into them, and with **no chest in that reach he does no herding at
all** (`RanchWork.tick`: `own.isEmpty()` returns). Porters (`store/Porters`) leave him his breeding food and up to 16
buckets, bottles and bone meal; the village's Chef cooks from a Butcher's chests (`ChefWork`, `Village.stashes`). A
player picks it at the Blueprint Table and hands it to a builder; nobody else builds it (no Steward rule, no renewal:
the Steward builds a Kitchen for a Butcher without a workstation, `steward_rules/workplace_kitchen.json`). On the
Village Map it is a Workshops building (`VillageMaps.kindOf`, by name). It counts nothing for beauty.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `butchers_shop` | The Butcher's smoker; his chests in reach of it; the freezer cellar 4 deep under it, ice-lined (packed ice and blue ice: see "Must have"), with a way down from inside, lit, and its chests | A one-tier build (`StarterBlueprints.ONE_TIER`): in the Blueprint Table and the creative tab, **locked at first** (not in `blueprint_unlocks/starting.json`; `BlueprintUnlocks.MANAGED`) and unlocked by a builder's level-up; no expansion switch (always offered) |

**Exactly one level.** No `butchers_shop_2`: an upgrade would change the counted upgrades in
`BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (exactly 64) and the `ONE_TIER` list, and is a new feature.

## Must have
- **Job block: one `minecraft:smoker`** (the Butcher's). It must be **reachable on foot from outside** through the way
  in: within 2 across and 1 up or down of a cell a villager walks to (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`).
  If it is built in under solid blocks, some side of it (its front first) needs a floor cell with 2 blocks of head room
  for him to stand on (`work/JobSiteApproach`, `BuiltInJobSiteGameTests.besideABuiltInWorkstation`; walled in on every
  side, nobody takes it). Put it above the ground: the wiki promises "the Butcher's Shop has the smoker upstairs" and
  "a cellar never hands a villager a job" (`basements.md`), and his animals and drops are out on the ground.
- **No other job block** anywhere, cellar included: no barrel, composter, cauldron (any kind), lectern, loom,
  stonecutter, grindstone, smithing table, fletching table, cartography table, blast furnace, brewing stand, beehive, jukebox
  or the mod's workstations (Blueprint Table, Builder's Bench, mailbox, shop counter, ...), and **no crafting table** (the Carpenter's site, `ModVillagers.CRAFTING_TABLE_POI`,
  `Stations`). Each would take a villager for another job. No test counts them for this family; the POI code does.
  A chest, furnace or anvil is not a job block. (Store stock in chests, not barrels.)
- **Storage the job reads:** at least **one chest within 8 across and 4 up or down of the smoker** (above; without it
  the Butcher never works). A chest in the freezer counts only if it is within 4 blocks below the smoker (today's freezer
  chests are exactly 4 below). Chests are drawn empty (builders never fill or copy containers). Every chest is reachable
  on foot (`QaBuildReachGameTests`; the freezer's: `QaBasementGameTests.everyBasementAndItsChestsCanBeWalkedToFromOutside`),
  and an indoor chest stands against a wall, a solid block, a bed or another chest (`QaFurnishedRooms1031GameTests`,
  once `rebuild.py` records the build's furniture).
- **The freezer** (`StarterBlueprints.BUTCHERS_SHOP` = `new Entry(..., new Vec3i(13, 11, 13), 4)`, in
  `WITH_BASEMENTS`):
  - Drawn at the bottom of the file, exactly **4 layers** under the ground floor: its floor at y = -4, its room
    y -3..-1, the ground floor's y = 0 its ceiling (`BasementGameTests.basementsLoadUnderTheGround`: the file is the
    declared height + 4; the plan spans from 4 below the origin). Under the ground, every cell outside the freezer is
    left out of the file (a structure void, not air): the kit does this with `rebuild.py --basement 4 --cellar x0,z0,x1,z1`.
    The freezer lies under the build's own ground floor (`basements.md`, "Known limits": none reaches under a yard).
  - **Ice that the tests count:** at least one `minecraft:packed_ice` (`QaBasementSeals1107bGameTests.aButchersFreezerDugBesideWaterStaysDryAndFrozen`
    counts the drawing's packed ice, wants more than 0 and every one still packed ice after the build) and a
    `minecraft:blue_ice` on the freezer's floor layer (y = -4; both building tests assert one at a pinned spot, see
    "Rules tests check").
  - **Dry, and nothing in it that melts.** No water or lava anywhere in the box of the cellar's drawing after a build
    with water in the earth beside and under it (`assertNoFluidInBasement`, west, east and south of the cellar and under
    its floor's middle); nothing drawn waterlogged down there. Plain `minecraft:ice` melts to water by a lantern's light
    and frosted ice and snow layers melt too: either breaks "dry" or "as drawn". Packed ice, blue ice and snow blocks
    don't melt.
  - **Cells on its floor layer** (y = -4): the Table's preview's deepest cell is the basement's floor
    (`QaBlueprintTableGameTests.everyBasementBuildPreviewsItsWholeDepth`). Every cell drawn as air down there is dug out
    and stays empty (`BasementGameTests`' `assertBasementEmpty`).
  - **A way down from inside:** a `minecraft:ladder` or stairs under the ground (`basementsLoadUnderTheGround`), one of
    them on y = -1 with room to climb out above it (passable at y = 0 and y = 1:
    `QaBasementGameTests.everyBasementsWayDownComesUpThroughTheGroundFloor`).
  - **Lit:** at least one `minecraft:lantern` under the ground (`basementsLoadUnderTheGround`; a soul lantern doesn't
    count), and no air cell on the freezer's floor with air over it at block light 0
    (`QaBasementGameTests.noBasementLeavesADarkSpotForMonsters`). Lanterns hang or stand on something, never on a floor
    (owner's rule).
  - **Walkable:** a villager walks from the ground all round the build down into it and back out
    (`everyBasementAndItsChestsCanBeWalkedToFromOutside`: level, one up, up to three down, ladders; doors and trapdoors
    open), and every chest down there is within 2 across and 1 up or down of a cell reached that way.
- **Beds and residents.** None: no code or test asks this family for a bed or a resident. A bed is free; if drawn, it
  counts as a village bed for the Steward (`StewardConditions.beds`), its head must be reachable on foot
  (`QaBuildReachGameTests`) and its foot has its head one block towards its facing
  (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`).
- **Animals.** Nothing in the drawing is read for the herd: a pen, hay or a gate is free. (His animals are wherever they
  stand within 16 of the smoker; villagers don't open fence gates.)
- **Furniture spots.** No kind is required (no `blueprint_furniture/butchers_shop.json` today; not a home). Every piece
  the drawing has is recorded as a spot by `rebuild.py`, each spot's blocks exactly the template's
  (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); nothing small stands on air
  (`FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece`); every chair stair faces its table
  (`QaChairFacingGameTests`).
- **A way in on the z = 0 side** (the front), at ground level, that a villager 1.95 blocks tall walks through; a
  wooden doorway has room for him (`QaDoorwayClearanceGameTests`; B170: a carpet just inside a 2-tall doorway stopped a
  worker). No fence gate or trapdoor on the only way in. Any upper floor or loft holding a chest is reached by a ladder
  or stairs from inside (`QaBuildReachGameTests`).
- **No entity** (villager, frame, painting ...) and no jigsaw in the file.
- Every block is one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).

## Fits
- **Plot** (above the ground; the freezer goes 4 under it, inside the same footprint). y = 0 sits on the clicked block;
  the front is the z = 0 side. **At most 21 wide x 21 deep x 13 tall.**
  - `BasementGameTests.butchersShopGoesUpWithItsFreezer` builds it in the 30 x 20 x 30 `huge_area` (20 tall, not 30),
    ground at y 6, origin helper (9, 7, 9): x 9..29 and z 9..29 give 21 x 21, and y 7..19 gives **13 tall** (blocks
    above an area's height are never cleared and leak into later batches). The builder's bench at (2, 7, 2), his chest
    at (2, 7, 4), the stock barrels at x 1..6 from z 5 and the builder at (3, 7, 3) stand west of it.
  - `QaBasementSeals1107bGameTests.aButchersFreezerDugBesideWaterStaysDryAndFrozen` builds it in the 30-tall
    `tall_area`, ground at y 9, origin (8, 10, 9): at most 22 x 21 x 20 there.
- **Declared size:** `StarterBlueprints.BUTCHERS_SHOP` is `new Vec3i(13, 11, 13)` with basement 4 today (the size above
  the ground; the file is 4 taller). If the size changes the ship step changes that line
  (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`, `basementsLoadUnderTheGround` assert the file matches),
  and `modded_library.py` reads the sizes from that file. The basement stays exactly 4.
- **Material and time budget:** both building tests stock exactly what the plan needs and want the whole build, freezer
  dug and fitted, finished as drawn with **no step skipped** (`skipped() == 0`) within **24,000 ticks** (build delay 2,
  builders' help off). The old drawing is 649 blocks; a much bigger build risks the time.
- **Front and way in:** the z = 0 side faces the player who places it; the way in is there.
- **Neighbours:** none planned: no village plan, Steward zone or renewal places it; a player puts it where he likes.

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing if needed, keeping what each checks, dropping none):
- `BasementGameTests.butchersShopGoesUpWithItsFreezer` (`huge_area`, origin (9, 7, 9), stocked with exactly the plan's
  materials): the build finishes as drawn (`assertBuilt`: `plan.unfinished` empty), every air cell of the basement is
  dug and empty (`assertBasementEmpty`), nothing skipped, and today's pins: **`minecraft:smoker` at (8, 1, 5)**,
  **`minecraft:blue_ice` at (5, -4, 5)** (the freezer's floor) and **`minecraft:ladder` at (3, -1, 3)** (the way
  down's top rung).
- `QaBasementSeals1107bGameTests.aButchersFreezerDugBesideWaterStaysDryAndFrozen` (`tall_area`, origin (8, 10, 9),
  water put in the earth at (minX - 1, -2, midZ), (maxX + 1, -3, midZ), (midX, -2, maxZ + 1) and (midX, minY - 1, midZ)
  of the cellar's drawn extent; each must be earth, not a cell of the build): built as drawn, no water or lava in the
  cellar's box, **blue ice at (5, -4, 5)**, every packed ice block of the drawing still packed ice (and more than 0),
  the builder alive.
- No test pins a chest or door coordinate; where they stand is free within the rules above.

Basement-wide checks over `WITH_BASEMENTS`: `BasementGameTests.basementsLoadUnderTheGround` (depth 4, size, file height,
plan box, a ladder or stairs and a lantern under the ground); `QaBasementGameTests` (way up through the ground floor,
no dark spot, walkable with its chests); `QaBlueprintTableGameTests.everyBasementBuildPreviewsItsWholeDepth`.

By id only (the drawing doesn't matter beyond the rules above): `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`
(declared size, placeable blocks); `ModdedLibraryGameTests` (a modded copy, and the vanilla build without the mods);
`BlueprintUnlocks` (locked, unlocked by a level-up).

Library-wide checks it must pass: `B648GameTests` and `QaWindowsAndWards1028BugTests` (no pane or bar joined on one side
with air on the other, as stored and as the game joins it), `QaBuildReachGameTests`, `QaDoorwayClearanceGameTests`,
`QaChairFacingGameTests`, `BlueprintsStayGameTests` (lamps on a block that holds them; no dirt path or farmland under a
solid block; beds whole), `QaFurnishedRooms1031GameTests` / `FurnitureSpotGameTests` / `FloatingFurnishingsGameTests`
(once its furniture is recorded), `QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`,
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `StyleGameTests.chimneysStayWholeInEveryStyle` (a
campfire on a stack of full blocks keeps every block of that stack full and solid in every style). The mirrored and
turned-frame checks (`QaMirroredBuildsGameTests`, `QaTurnedFramesGameTests`) cover `StarterBlueprints.ALL` only, not
this one-tier build; doors, beds and double chests should still stay whole turned and mirrored.

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair; `open_panes`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate --basement 4
--cellar ...` prints no CHECK line, and `holes.py` passes. The old drawing is not in `owner_rules_baseline.json`; the
new one must come out clean too.

Saves (for the ship step, not the designer): the id and the basement depth never change; the old drawing goes to
`blueprint_history/butchers_shop/` (`history.py`; one older drawing is already there) so a site begun on it finishes
as drawn, and the modded copy is regenerated. `Cellars.needsDigging` offers "Dig the cellar" for any finished build
with a basement whose open cells are mostly solid, reading the drawing it was built from (`Cellars.asBuilt`, 50.3); the
ship step checks that a Butcher's Shop standing on the old drawing is not offered a dig of the new freezer.

## Free
Everything not listed above: shape, plan, roof, materials, colors, how many storeys, the shop front, a pen or none, a
bed or none, how many chests beyond the one in the smoker's reach, where the smoker, chests, door, freezer and its way
down stand (within the rules above), what the freezer holds beyond its ice, chest-reach and light rules, and lighting.
(The wiki's description in `docs/wiki/basements.md` is updated by the ship step to match.)
