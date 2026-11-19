# Habitat Garden: the brief

Family id(s): `habitat_garden`, `habitat_garden_2` (never renamed; "Habitat Garden", "Habitat Garden II" in `en_us.json`,
`blueprint.aliveworkplace.habitat_garden*`; advancements `built/habitat_garden`, `built/habitat_garden_2`)   Variants:
village (none: no `village/<style>_habitat_garden` exists and no village plan places it), modded (none to draw:
`modded_library.py` makes `structure/modded/habitat_garden_2.nbt` from the vanilla drawing, and today none for Level I,
see "Modded copies" below; the ship step runs it again), looks (none).

## What it is for
The **Habitat Keeper**'s workplace (ROADMAP 28.10, 28.13, 28.14; `ModVillagers.HABITAT_KEEPER`, config switch
`habitatKeepers`, needs Cobblemon). Her one job block is Cobblemon's **Pasture Block** (`cobblemon:pasture`, two blocks
tall): a jobless villager standing by it, sneak-clicked by a player holding a **honey bottle**, becomes a Habitat Keeper
with that pasture as her job site (`Stations.choose`: the nearest free block within 4.5 blocks where the item picks a job;
a honey bottle also picks the Nurse at a brewing stand or a Healing Machine). A jobless villager never takes a pasture by
itself. Round her pasture she (`habitat/HabitatKeeperWork`, `HabitatKeepers`): **slathers every bare Saccharine log
within 32 blocks across and 16 up or down of the pasture** with honey bottles from her chests (the glass bottle goes
back); sets **Poké Snacks** out on up to 3 lure spots (player-marked within 32 blocks, or by default on grass 16-32
blocks out, so normally outside the garden) and plants Saccharine saplings round them; sights shiny, rare and Alpha wild
Pokémon within 48 blocks. Everything she uses comes from **her chests: the containers within 8 blocks across (x and z
each) and 4 up or down of the pasture** (`SupplyContainers.find(level, pasture, null)`; snacks also from the Camp Cooks'
chests). With Cobblemon 1.8, an **Expert** keeper in a village with a finished Habitat Garden **founds the village's own
Habitat Block in place of the garden's centre stone** (`VillageHabitats.foundingSpot` / `found`): a natural-mode
`cobblemon:habitat_block` that mimics the block it replaces and spawns the biome's Pokémon within 12 blocks of it; she
then visits it every morning; taking the garden down removes it with no drop (`VillageHabitats.onTakenDown`). A
Journeyman Habitat Keeper sells the Level I blueprint for 12 emeralds (`ModTrades`, `BlueprintUnlocks.VILLAGER_SALES`);
both levels are in the Blueprint Table only with Cobblemon (`StarterBlueprints.COBBLEMON_ONLY`) and hide while
`habitatKeepers` is off (`StarterBlueprints.EXPANSION_SWITCH`). On the Village Map it is a FARMS building
(`VillageMaps.kindOf`, by id). No Steward rule or village plan places it: the player sites it.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `habitat_garden` | The Pasture Block (job block), the **centre stone** where the village's Habitat Block will go, a **Saccharine tree** for her to slather, a **pond**, and (needed by the job, missing in the old one) a chest in her reach | Sold by a Journeyman Habitat Keeper (12 emeralds); in the Table with Cobblemon while `habitatKeepers` is on |
| II | `habitat_garden_2` | Level I grown: a **keeper's hide** (ROADMAP 28.13's words: "a keeper's hide on stilts", a raised room she or a player climbs to, with a wooden floor) and a **second pond** | The upgrade by id (`BlueprintUpgrades.upgradeOf`, id + `_2`); sold by the builder who finished Level I. No cost is read from the drawing |

**Exactly two levels.** No `_3`: `PokemonBuildsGameTests.eachPokemonJobUpgradeKeepsItsBase` requires exactly one
upgrade per Pokémon-job build (`upgrades * 2 == JOB_BUILDS.size()`).

**Level II over Level I** (`eachPokemonJobUpgradeKeepsItsBase`): same origin and front; II is at least I's size in x, y
and z; Level I has **more than 150 non-air blocks**; **at least 60 % of Level I's non-air blocks stand in the same cell
with the same state in Level II**. Draw them together, II growing out of I. The pasture and the centre stone keep their
cells and states in both levels (one constant and one test row serve both, below).

## Must have (each level)
- **Job block: exactly one `cobblemon:pasture` (bottom and top halves), at the same template cell in I and II** (today
  bottom at (14, 1, 7)).
  - It must be a job site (POI) when built and when placed as drawn (`PokemonBuildsCompatTests.build`: "the job block at
    ... is no job site"; `QaJobBuildsCompatTests.qaEachBuildsJobBlockTakesAVillagerWithItsItem`).
  - **A standing spot within 3 blocks** (x and z within ±3, y within ±1): feet and head cells with no collision and no
    fluid, on a block whose top is sturdy (tall grass and flowers have no collision; leaves, water and a lily pad do
    not count). The test puts a jobless villager on the nearest such spot and hands her a honey bottle; she must take
    the job at *this* pasture. **No second pasture, brewing stand or Healing Machine** anywhere in the build: each is a
    block a honey bottle picks a job at and can steal the job ("the nearest free one").
  - Reachable on foot from the ground outside and from the centre stone and the tree (she walks between them). Her reach
    is 3.0 blocks (`HabitatKeeperWork.REACH`).
- **Storage: at least one chest within 8 blocks across (x and z each) and 4 up or down of the pasture.** No test counts
  it, and **the old garden has none** (its only barrel, in II's hide, is 11 blocks from the pasture), so today she can't
  fetch honey, snacks or saplings and shows "no chest" when she has something to put back (`HabitatKeeperWork.carry`,
  `fetch`; the player guide `docs/guide/habitat-keeper.md` tells players to "put a chest by the pasture"). Every chest
  or barrel must be walkable to (`QaBuildReachGameTests`), stand against a wall, solid block, bed or chest (library-wide
  furnished-room checks) and is drawn empty. A **barrel** is also a Fisherman's job site that a jobless villager takes by
  himself; a chest is not.
- **The centre stone (I and II): `minecraft:mossy_cobblestone` at `StarterBlueprints.HABITAT_GARDEN_CENTRE`, the same
  cell in both levels** (today (7, 0, 7); also `HABITAT_CENTRE` in `pokemon_jobs.py`). It is the village's future Habitat
  Block: the founding accepts only mossy cobblestone or a moss block there (`VillageHabitats.isCentreStone`), the test
  asserts mossy cobblestone (`PokemonBuildsCompatTests.centreStone()`), and the Habitat Block mimics whatever stood there.
  Moving it means changing the constant (the ship step's job; the take-down and founding code read only the constant).
  The keeper walks to it to found the habitat (within 3 blocks of a cell she can stand on, reached on foot from the
  pasture). Other mossy cobblestone elsewhere is harmless (only that cell is read). Keep it a block the habitat can
  replace with nothing above it that needs it for support. The Habitat Block spawns the biome's Pokémon within 12 blocks
  of it (Cobblemon's own spawning, not tested): open ground round it is what makes the habitat work.
- **A Saccharine tree (I and II): `cobblemon:saccharine_log` (`axis=y`) with its crown** (test pin today
  (10, 1, 11), `aBuilderBuildsTheHabitatGarden`). Within 32 blocks across and 16 up or down of the pasture (any cell in
  the plot is). Each log must be one she can slather: she walks to within 4 blocks of it, reaching up to 4 blocks
  (`walker.reachingUp(4).reach(log, REACH + 1)`, or standing within about 4.5 blocks), so **every Saccharine log in the
  build is within that of a cell she can stand on**; a log nobody can reach keeps her walking at it. Leaves of the crown
  (and any leaves in the build) are drawn **persistent**, or they decay and the build is not "every block right".
- **A pond (I) and a second pond (II):** `minecraft:water` sources (test pins today: (3, 0, 9) in I, (10, 0, 19) in II).
  Held in on every side and below by solid blocks the builder places, so none flows out when poured (the builder pours
  liquids first) and the build finishes with every block right. Lily pads only on water sources; plants that need water
  nearby (sugar cane) were dropped because the builder plants before he pours (ROADMAP Notes, 28.14), so don't use them.
- **The keeper's hide (II only):** a raised room (ROADMAP 28.13: "a keeper's hide on stilts") reached by ladder or
  stairs, with a **covered wooden floor** (test pin today (3, 3, 17): the vanilla floor, or the modded library's Chipped
  herringbone of the same wood) and a structural block of it (test pin today (2, 5, 16), a log). Any chest, barrel or job
  block in it must be walkable to (`QaBuildReachGameTests`; ladders count), and any doorway leaves 1.95 blocks of
  headroom. That covered wooden floor is also what gives Level II its modded copy (below). If the recipe records it as
  a furnished room, it needs its own light.
- **Beds and residents: none.** No code counts beds here; the keeper comes from the village. No villager, mob or other
  entity in the file.
- **Furniture kinds a job or test needs: none.** There is no `blueprint_furniture/habitat_garden*.json` and the family
  is not in `furniture.py`'s `WORKPLACES`. If the recipe records furniture spots, each spot's blocks must match the
  template (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`), a furnished room has its own light
  (`FurnishedRoomsGameTests.everyFurnishedRoomHasALight`), nothing small stands on air (`FloatingFurnishingsGameTests`),
  every chair faces its table (`QaChairFacingGameTests`).
- **Anything else the code reads:** nothing (no door, bell, sign or lectern is read). Avoid vanilla job blocks a jobless
  villager takes by himself (a composter is the Farmer's and the Berry Breeder's, a beehive the Beekeeper's, a barrel the
  Fisherman's): a garden invites them, and each pulls a villager to another job beside her pasture.
- Every block is one a builder can get and place, and the build finishes with **nothing skipped** (`skipped == 0`,
  every block right): plants on soil they can stand on, two-tall plants whole, lanterns and other hanging or standing
  things on their support.

## Fits
- **Plot, both levels: at most 19 wide (x) x 18 tall (y) x 25 deep (z).** Binding: `PokemonBuildsCompatTests.build`
  puts the origin at helper (min(9, 29 - width), 2, min(3, 29 - depth)) in the compat `huge_area` (30 x 20 x 30), with
  the builder's bench at helper (2, 2, 2) and the supply barrels along z = 5 from x = 1 east, and keeps 2 blocks round the
  build for landscaping; 18 tall is what is left above y = 2. `QaJobBuildsCompatTests` places both levels as drawn at
  helper (1, 1, 1) in the same area. Level I may be smaller than Level II on every axis, never larger.
- y = 0 sits on the clicked block; the front is the z = 0 side. No basement.
- **Build time:** one builder alone, build delay 1, builders' help off, from barrels holding exactly its materials,
  starting at day time 2,000 with the clock not held: **Level I within 30,000 ticks, Level II (built whole from bare
  ground) within 60,000 ticks** (`aBuilderBuildsTheHabitatGarden`, `aBuilderBuildsHabitatGardenII`). The old drawings
  (528 and 959 non-air blocks, much of them plants and water) pass with room; a design much bigger needs a timed run.
- **Declared sizes:** `StarterBlueprints.HABITAT_GARDEN` `(15, 8, 15)`, `HABITAT_GARDEN_2` `(15, 11, 25)` today; the ship
  step changes the `Vec3i`s and their javadoc (which describes the old look). The tests use them for the origin and to
  clear the area, so they must match the drawings.
- **Front and way in:** the z = 0 side faces the player who places it. No door or gate is read. The way from the ground
  outside to the pasture, the centre stone, every Saccharine log, every chest and the hide must be walkable; any
  wooden doorway leaves a villager 1.95 blocks of headroom (`QaDoorwayClearanceGameTests`, `QaDoorwayBugGameTests`).
- **Neighbours:** none planned (no Steward rule, no village plan). The showcase scene `village_habitat`
  (`devclient/VillageHabitatScene`) sets a Village Hall 4 blocks west of the template's x = 0, level with the centre's z,
  and the keeper on the cell 1 west and 1 south of the pasture (today (13, 1, 8)): that cell must be free to stand in,
  or the ship step moves the pin.

## Rules tests check
Pinned to the drawing (the ship step moves coordinates and counts to the new drawing, keeping what each checks,
dropping none):
- `PokemonBuildsCompatTests.aBuilderBuildsTheHabitatGarden` (30,000 ticks): built from exactly its materials, every
  block right, 0 skipped; `cobblemon:pasture` at (14, 1, 7) and a job site; `mossy_cobblestone` at
  `HABITAT_GARDEN_CENTRE`; `cobblemon:saccharine_log` at (10, 1, 11); `water` at (3, 0, 9).
- `PokemonBuildsCompatTests.aBuilderBuildsHabitatGardenII` (60,000 ticks): the same, with the pasture and centre stone
  where they were; the hide's floor at (3, 3, 17) is `spruce_planks`, or `chipped:herringbone_spruce_planks` when the
  modded library is chosen (`FurnitureChoice.moddedLibrary`); `spruce_log` at (2, 5, 16); `water` at (10, 0, 19). (A new
  floor wood changes both names in the test.)
- `QaJobBuildsCompatTests.qaEachBuildsJobBlockTakesAVillagerWithItsItem` (both levels placed as drawn at helper
  (1, 1, 1), with `withFeatures`): a `cobblemon:pasture` job site at (14, 1, 7), a standing spot within 3, and the honey
  bottle makes a Habitat Keeper working at that block.
- `PokemonBuildsGameTests.eachPokemonJobUpgradeKeepsItsBase`: II >= I on every axis; I > 150 non-air; >= 60 % kept.
- Centre stone constant users: `VillageHabitatCompatTests`, `QaVillageHabitatCompatTests`, `QaStageTwoCompatTests`
  (each sets mossy cobblestone at the constant's spot itself and records a finished garden; they need only that the
  centre is one cell, the same for both ids, and mossy cobblestone in the drawing).
- `devclient/PokemonBuildsScene` (not a test, pinned): the pasture at (14, 1, 7) in both levels;
  `devclient/VillageHabitatScene` (the `village_habitat` showcase scene): places Level I as drawn, the pasture at
  (14, 1, 7), the keeper at the pasture + (-1, 0, 1), and must see her found the habitat at the centre within 1,200 ticks.
- `QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`: every chest, barrel, bed head and job
  block within 2 across and 1 up or down of a cell walked to from the ground outside (ladders, doors and trapdoors
  count).
- **Modded copies:** `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy` exempts only `habitat_garden`
  (`OPEN_AIR`), so **Level II must contain something the modded library dresses** (a covered wooden floor, a chest, a
  ladder, plaster or birch infill, a polished andesite or covered cobblestone floor, a table, a barrel or bookshelf)
  or it gets no copy and the test fails. If Level I gains such a thing (a chest does), it gets a copy too, which the test
  allows; the ship step may then drop it from `OPEN_AIR`. `QaCarvedLibraryCompatTests`: each copy keeps its job sites.
  Regenerate with `modded_library.py`.
- Library-wide: `QaDoorwayClearanceGameTests`, `QaDoorwayBugGameTests`, `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`
  (a standing lamp on a block whose top holds it), `B648GameTests` and `QaWindowsAndWards1028BugTests` (no pane or bar
  run ending against air), `QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`,
  `QaChairFacingGameTests`, `FurnitureSpotGameTests`, `FurnishedRoomsGameTests`, `QaFurnishedRooms1031GameTests`,
  `FloatingFurnishingsGameTests`, `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`.
- Refresh: once the old drawings go into `blueprint_history/` (`history.py`), `QaRefreshHistory1028GameTests` expects a
  building standing as the old drawing to be offered the new one.
- By id only (the drawing doesn't matter): `PokemonBuildsGameTests.thePokemonJobBuildsNeedCobblemon` (not in the Table
  without Cobblemon, still loads for operators), `thePokemonJobBuildsAreOnTheMap` (FARMS), `thePokemonJobBuildsAreTranslated`;
  `PokemonBuildsCompatTests.thePokemonJobBuildsAreInTheTable`; `QaJobBuildsCompatTests.qaEachJobsJourneymanSellsTheirBuild`;
  `ExpansionBuildsGameTests` (`habitatKeepers` / M28 gate); `QaBlueprintUnlocksGameTests`;
  `QaBuiltAdvancements1116bGameTests.everyShippedBlueprintHasItsAdvancement`.
- The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, never standing on the
  ground; crockery only by a stove or on a table with a chair; `open_panes`), STYLE.md's checks and `holes.py`:
  `rebuild.py --candidate` prints no CHECK line. The old drawings are baselined in `owner_rules_baseline.json`
  (`habitat_garden`, `habitat_garden_2`, `modded/habitat_garden_2`: 2 each); the ship step removes those names. It also
  keeps the old drawings in `blueprint_history/`, takes the two ids out of `pokemon_jobs.py`'s saves, regenerates the
  modded copies, and updates `docs/guide/habitat-garden-build.md`, `docs/wiki/furniture-and-storage.md` ("the open-air
  Habitat Garden has nothing to dress") and `StarterBlueprints`' javadoc, which describe the old look.
- Untested today (ROADMAP Notes, 28.14): Level II's upgrade rebuilding over a founded Habitat Block. Keeping the centre
  cell the same block in both levels means the upgrade has nothing to change there.

## Free
Everything not listed above: shape, plan, size within the plot, materials (beyond the pasture, the mossy cobblestone
centre, the Saccharine log and water the tests name and the hide's covered wooden floor), colors, style, enclosure,
paths, planting, the tree's crown, the hide's form and placement, how Level II joins Level I, extra chests, lighting and
landscaping.
