# Gem Grotto: the brief

Family id(s): `gem_grotto`, `gem_grotto_2` (never renamed; "Gem Grotto", "Gem Grotto II" in `en_us.json`,
`blueprint.aliveworkplace.gem_grotto*`; advancements `built/gem_grotto`, `built/gem_grotto_2`)   Variants: village
(`village/<style>_gem_grotto` for `plains`, `desert`, `savanna`, `snowy`, `taiga`: `village_looks.py`'s
`DESIGNS["gem_grotto"]` shell plus `village.py`'s fit-out `gem_grotto_room` and job `gem_grower`; their own contract,
see "The village variant"), modded (none to draw: `modded_library.py` makes `structure/modded/gem_grotto(_2).nbt` from
the vanilla drawings; the ship step runs it again), looks (none).

## What it is for
The **Gem Grower**'s workplace (ROADMAP 28.11, 28.13; `ModVillagers.GEM_GROWER`, config switch `gemGrowers`). Her one
job block is a **stonecutter**: a jobless villager standing by it, sneak-clicked by a player holding an **amethyst
shard**, becomes a Gem Grower with that stonecutter as her job site (`Stations.choose`: the nearest free stonecutter).
Round it she tends **gem beds** (`gem/GemGrowerWork`, `GemGrowers`, data in `data/aliveworkplace/gem_beds/`): she finds
every bed's "touch" block **within 16 blocks of the stonecutter on every axis** (`GemGrowers.RANGE`, a cube scan),
picks only ripe clusters within 2 blocks of a touch block (`CLUSTER_REACH`), never the touch block itself, and carries
the picking to **her chests: the containers within 8 blocks across and 4 up or down of the stonecutter**
(`SupplyContainers.find(level, cutter, null)`, `RADIUS` 8, `VERTICAL` 4). The beds are: **budding amethyst** (vanilla;
the player brings one from a geode, a builder can never place it); with Cobblemon, **tumblestones planted against lava
or magma** (`#cobblemon:tumblestone_heat_source`, up to 4 plantings round one heat block, each a bud sitting on a sturdy
face beside it); with Cobblemon 1.8, **Type Gem Blocks set on Deepslate Crystal Cores**; and at the stonecutter itself,
Blank TMs from glass and shards in her chests. It **works without Cobblemon** and is the only Pokémon-job build in the
Blueprint Table without it (`StarterBlueprints.VANILLA_JOB_BUILDS`). A Journeyman Gem Grower sells the Level I blueprint
for 12 emeralds (`ModTrades`, `BlueprintUnlocks.VILLAGER_SALES`); both levels hide from the Table while `gemGrowers` is
off (`StarterBlueprints.EXPANSION_SWITCH`). On the Village Map it is a FARMS building (`VillageMaps.kindOf`, by id).
No Steward rule or village plan places the buildable: the player sites it. With Cobblemon and `pokemonVillageHouses`
on, villages grow the village variant with a Gem Grower already at work (`VillageHouses`, weight 1).

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `gem_grotto` | The stonecutter (job block), her chest(s) in reach, a **lava pool of 6 sources behind glass** with places round it where tumblestones can be set over the lava, and an **amethyst niche** (an amethyst block with a cluster on it) where a player can later set a budding amethyst | Sold by a Journeyman Gem Grower (12 emeralds); in the Table with or without Cobblemon while `gemGrowers` is on |
| II | `gem_grotto_2` | Level I grown: **four Deepslate Crystal Core spots** (drawn as plain deepslate; `StarterBlueprints.withFeatures` swaps them for `cobblemon:deepslate_crystal_core` when Cobblemon 1.8 is there), each with room for a Type Gem Block and its cluster | The upgrade by id (`BlueprintUpgrades.upgradeOf`, id + `_2`); sold by the builder who finished Level I. No cost is read from the drawing |

**Exactly two levels.** No `_3`: `PokemonBuildsGameTests.eachPokemonJobUpgradeKeepsItsBase` requires exactly one
upgrade per Pokémon-job build (`upgrades * 2 == JOB_BUILDS.size()`).

**Level II over Level I** (`eachPokemonJobUpgradeKeepsItsBase`): same origin and front; II is at least I's size in x, y
and z; Level I has **more than 150 non-air blocks**; **at least 60 % of Level I's non-air blocks stand in the same cell
with the same state in Level II**. Draw them together, II growing out of I. The stonecutter, the lava, its glass and the
niche keep their cells (the tests pin one cell for both levels, below).

## Must have (each level)
- **Job block: exactly one `minecraft:stonecutter`, at the same template cell in I and II** (today (4, 1, 2)).
  - It must be a job site (POI) when built and when placed as drawn (`PokemonBuildsGameTests.buildGrotto`,
    `QaJobBuildsGameTests.qaEachGemGrottosStonecutterTakesAGemGrower`, `QaJobBuildsCompatTests.qaEachBuildsJobBlockTakesAVillagerWithItsItem`).
  - **A standing spot within 3 blocks** (x and z within ±3, y within ±1): feet and head cells with no collision and no
    fluid, on a block whose top is sturdy. Both tests put a jobless villager on the nearest such spot and hand her an
    amethyst shard; she must take the job at *this* stonecutter. A second stonecutter anywhere in the build would be a
    second Gem Grower's (or a Mason's) site and can steal the job ("the nearest free stonecutter"); draw one only.
  - Reachable on foot from the ground outside (below, `QaBuildReachGameTests`). Her own reach is 3.0 blocks
    (`GemGrowerWork.REACH`, `Walker.reach`).
- **Storage: at least one chest or barrel within 8 blocks across (x and z each) and 4 up or down of the stonecutter.**
  With none she can't put her picking anywhere ("no_chest") and can't plant or make Blank TMs (`GemGrowerWork.carry`,
  `fetch`, `GemGrowers.makeBlankTms`). No test counts them; the old one has one chest and one barrel in reach. Chests
  stand against a wall, solid block, bed or chest (library-wide furnished-room checks) and are drawn empty.
- **The lava pool (I and II): exactly 6 `minecraft:lava` sources** (a lava bucket each: the plan must cost exactly 6
  lava buckets, `buildGrotto`: "the lava costs N buckets") and **no other lava anywhere**, **never flowing**: held in on
  every side and below by solid blocks the builder places, so none runs out when poured or while the walls round it go
  up (the builder pours liquids first, B961). Today the six are at y = 1, x 5-7, z 5-6.
  - **Behind glass:** plain `minecraft:glass` blocks (not panes, not stained) between the lava and the room, at the
    lava's level, on the side the grower and players stand (today (5..7, 1, 4)) (`buildGrotto`). No open lava where
    anyone walks.
  - **Nothing burns:** no fire may appear anywhere in the build's box plus 2 blocks round it (`buildGrotto`, and the
    take-down test's whole area). Lava sets fire to air cells beside flammable blocks up to about 3 blocks above and
    1-2 beside it: **no flammable block** (any wood, planks, logs, wool, carpet, leaves, hay, bookshelves, scaffolding,
    banners...) within 3 blocks of the lava in any direction up or sideways.
  - **Tumblestone spots (spec: "tumblestone ledges round the lava"):** for the lava to be a tumblestone bed, each lava
    cell needs a free air cell beside it (the top first: `GemGrowers.plantSpot` tries up, north, south, east, west,
    down) that has **a sturdy full block face on one of its sides** to hold the bud (`GemGrowers.placed`; lava itself
    is no support). Those spots and the clusters on them must be within the grower's 3-block reach from a cell she can
    stand on, on her side of the glass. No test checks this without Cobblemon, but it is the pool's whole job.
  - Taken down, the builder must scoop all 6 sources back into buckets with no lava or fire left
    (`QaTakeDown1105GameTests.gemGrottoTakenDownScoopsItsLavaBackIntoBuckets`), so each source must be reachable from
    where the builder stood to pour it.
- **The amethyst niche (I and II):** at least one `minecraft:amethyst_block` with a `minecraft:amethyst_cluster`
  attached to it (the cluster stands on the block's face, facing away from it) (`buildGrotto`; today the block at
  (0, 1, 6) and the cluster at (1, 1, 6), facing east). It is where a player can swap in a budding amethyst, so it is
  within 16 blocks of the stonecutter and its cluster cells within her reach. **No `budding_amethyst` in the buildable
  levels:** it has no item, so a builder can never get one, and "nothing skipped" fails.
- **Crystal Core spots (Level II): exactly the four cells in `StarterBlueprints.GEM_GROTTO_2_CORES`** (today (4, 1, 13),
  (8, 1, 13), (4, 1, 16), (8, 1, 16), also `GEM_GROTTO_CORES` in `pokemon_jobs.py`), each drawn as plain
  `minecraft:deepslate` (`axis=y`) (`aBuilderBuildsGemGrottoII`; `PokemonBuildsCompatTests.gemGrottoIIGetsCrystalCoresWithCobblemon18`
  finds exactly 4 core cells). Each core must be **within 16 blocks of the stonecutter on every axis**, have a free air
  face (the top first) where a Type Gem Block is set and room round it for the cluster that grows on that block, and
  that cluster within the grower's reach from a standing cell. Moving the cores means changing both lists (the ship
  step's job).
- **Beds and residents: none.** No code counts beds here; the grower comes from the village. No villager, mob or other
  entity in the file.
- **Furniture kinds a job or test needs: none.** There is no `blueprint_furniture/gem_grotto*.json` and the family is not
  in `furniture.py`'s `WORKPLACES`. If the recipe records furniture spots, each spot's blocks must match the template
  (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`), a furnished room has its own light
  (`FurnishedRoomsGameTests.everyFurnishedRoomHasALight`), nothing small stands on air
  (`FloatingFurnishingsGameTests`), every chair faces its table (`QaChairFacingGameTests`).
- **Anything else the code reads:** nothing (no door, bell, sign or lectern). Other vanilla job blocks (barrel =
  fisherman, cauldron = leatherworker, ...) are not forbidden in the buildable levels but each can pull a jobless
  villager to another job; the village variant forbids them outright.
- Every block is one a builder can place and the build finishes with **nothing skipped** (`buildGrotto`: `skipped == 0`,
  every block right). Things that hang or stand on something (lanterns, the cluster) need their support.

## Fits
- **Plot, Level I: at most 15 wide (x) x 16 tall (y) x 15 deep (z).** Binding: the take-down test places Level I at helper
  (7, 2, 7) in the 22 x 18 x 22 `big_area` and asserts the plan's box lies inside helper (6, 1, 6)..(21, 17, 21)
  (`QaTakeDown1105GameTests.gemGrottoTakenDownScoopsItsLavaBackIntoBuckets`, "setup: covers ..., outside ..."). Its
  material list must also fit the test's **four chests (108 stacks)** ("setup: more stacks than chests").
- **Plot, Level II: at most 19 wide x 18 tall x 25 deep** (`PokemonBuildsGameTests.aBuilderBuildsGemGrottoII`: origin at
  helper (9, 2, 3) in the 30 x 20 x 30 `huge_area`, so the area ends at 21 x 18 x 27; keep 2 blocks for the
  landscaping inside the area). The builder's bench is at helper (2, 2, 2) and the supply barrels along z = 5 at x 1-6,
  west of the origin. Level I must also fit there (same origin, `aBuilderBuildsTheGemGrotto`).
- y = 0 sits on the clicked block; the front is the z = 0 side. No basement.
- **Build time:** one builder alone, build delay 1, builders' help off, from barrels holding exactly its materials,
  starting at day time 2,000 with the clock not held (he stops at dusk and goes on next morning): **Level I within
  30,000 ticks, Level II (built whole from bare ground) within 60,000 ticks**. The old drawings (638 and 1,048 non-air
  blocks) pass with room; a design much bigger than that needs a timed run before shipping.
- **Declared sizes:** `StarterBlueprints.GEM_GROTTO` `(13, 12, 11)`, `GEM_GROTTO_2` `(13, 12, 20)` today; the ship step
  changes the `Vec3i`s and their javadoc (which describes the old look).
- **Front and way in:** the z = 0 side faces the player who places it. No door is read. The way from the ground outside
  to the stonecutter, every chest and barrel, and any job block must be walkable (below); any wooden doorway leaves a
  villager 1.95 blocks of headroom (`QaDoorwayClearanceGameTests`, `QaDoorwayBugGameTests`).
- **Neighbours:** none planned (no Steward rule, no village plan).

## The village variant (`village/<style>_gem_grotto`, five styles)
In scope only if the designer redraws `village_looks.py`'s `gem_grotto` shell (today 9 x 10 x 11). Village houses share
one contract the Java reads by template coordinate (`world/VillagePieces`, `world/VillageHouses`, `village.py`'s
`village_house`, `village_looks.py` header); the designer draws only the shell, `village.py` adds the jigsaw, fit-out,
bed and villager. Every style keeps:
- **Size:** the placing test clips at template x -1..10, y 0..11, z -1..11 (`VillageCompatTests.pokemonHouse`).
- The street jigsaw at **(4, 1, 0)** facing north, the front row z = 0 at y = 0 left as the ground, the door at
  **(4, 1, 2)** with a step before it, **no trapdoor at z = 1, y 0-1**
  (`VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`,
  `noVillageHouseHasATrapdoorInItsFrontWalkRow`); the room x 2-6, z 3-7, the west wall at x = 1; the bed foot
  (6, 1, 6), head (6, 1, 7); one `aliveworkplace:gem_grower` villager in the file, no worker spawn jigsaw.
- The fit-out (`gem_grotto_room`, template cells): the stonecutter at (2, 1, 4), the chest at (2, 1, 3) with loot table
  `aliveworkplace:chests/village_gem_grotto`, a real `budding_amethyst` at (2, 1, 7) with a cluster on it (world
  generation may place one; a builder can't), an amethyst block at (2, 1, 6), two `magma_block`s at (3, 1, 7) and
  (4, 1, 7) with tumblestones on them behind panes.
- **Exactly one job block, a stonecutter, and no other job block of any profession; exactly one chest; no lava; no
  structure void** (`QaPokemonVillageHousesCompatTests.everyStyleOfEveryPokemonHouseHasOneJobBlockAndItsWorker`,
  `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`).
- **In the window from 3 blocks west, 1 below and 3 north of the stonecutter to 6 east, 4 above and 6 south of it
  (template x -1..8, y 0..5, z 1..10): exactly 2 magma blocks, exactly 1 budding amethyst, no lava**
  (`VillageCompatTests.aVillageGemGrottoComesWithItsGrower`). The shell may add none of those there. Placed as a village
  does, the grower takes that stonecutter and stands unhurt in open space inside.
- `QaReleaseRefresh1028GameTests.REDRAWN` lists the five `village/<style>_gem_grotto` with history key `cd1557d8c`: a
  redrawn shell keeps those history entries and adds its own (`history.py`).

## Rules tests check
Pinned to the drawing (the ship step moves coordinates and counts to the new drawing, keeping what each checks,
dropping none):
- `PokemonBuildsGameTests.aBuilderBuildsTheGemGrotto` / `aBuilderBuildsGemGrottoII` (`buildGrotto`, origin helper
  (9, 2, 3), 30,000 / 60,000 ticks): the plan costs exactly 6 lava buckets; built from exactly its materials, every block
  right, 0 skipped; a stonecutter at (4, 1, 2) that is a job site; in the box (-2, 0, -2)..(size + 1) no fire, and lava
  only as 6 sources at y = 1, x 5-7, z 5-6; plain glass at (5..7, 1, 4); an amethyst block at (0, 1, 6) with an amethyst
  cluster at (1, 1, 6); II: plain deepslate at the four `GEM_GROTTO_2_CORES`.
- `QaJobBuildsGameTests.qaEachGemGrottosStonecutterTakesAGemGrower` (both levels placed as drawn at helper (2, 1, 1) in
  `huge_area`) and `QaJobBuildsCompatTests.qaEachBuildsJobBlockTakesAVillagerWithItsItem` (at (1, 1, 1), with
  `withFeatures`): a stonecutter job site at (4, 1, 2), a standing spot within 3, and the shard makes a Gem Grower
  working at that block.
- `QaTakeDown1105GameTests.gemGrottoTakenDownScoopsItsLavaBackIntoBuckets` (Level I at helper (7, 2, 7) in `big_area`,
  the clock held at 2,000): fits the box above; the list fits 4 chests; the list's lava buckets equal the poured sources;
  built from exactly its list, then taken down with nothing of it standing (containers stay), no liquid and no fire
  left in the area, every non-bucket item given back exactly, the lava back in buckets.
- `PokemonBuildsCompatTests.gemGrottoIIGetsCrystalCoresWithCobblemon18`: exactly 4 core cells, each `deepslate` or
  `cobblemon:deepslate_crystal_core` as Cobblemon has it.
- `PokemonBuildsGameTests.eachPokemonJobUpgradeKeepsItsBase`: II >= I on every axis; I > 150 non-air; >= 60 % kept.
- `devclient/PokemonBuildsScene` (not a test, pinned): the stonecutter at (4, 1, 2) in both levels.
- `QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`: every chest, barrel, bed head and job
  block (stonecutter, cauldron, ...) within 2 across and 1 up or down of a cell walked to from the ground outside.
- Library-wide: `QaDoorwayClearanceGameTests`, `QaDoorwayBugGameTests`, `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`
  (a standing lamp on a block whose top holds it), `B648GameTests` and `QaWindowsAndWards1028BugTests` (no pane or bar
  run ending against air), `QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`,
  `QaChairFacingGameTests`, `FurnitureSpotGameTests`, `FurnishedRoomsGameTests`, `QaFurnishedRooms1031GameTests`,
  `FloatingFurnishingsGameTests`, `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`.
- Modded copies: `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy` (both ids), `QaCarvedLibraryCompatTests`
  (each copy keeps its job sites): regenerate with `modded_library.py`.
- Refresh: once the old drawings go into `blueprint_history/` (`history.py`), `QaRefreshHistory1028GameTests` expects a
  building standing as the old drawing to be offered the new one.
- By id only (the drawing doesn't matter): `PokemonBuildsGameTests.thePokemonJobBuildsNeedCobblemon` (both levels in the
  Table without Cobblemon), `thePokemonJobBuildsAreOnTheMap` (FARMS), `thePokemonJobBuildsAreTranslated`;
  `PokemonBuildsCompatTests.thePokemonJobBuildsAreInTheTable`; `QaJobBuildsGameTests.qaAJourneymanGemGrowerSellsTheGemGrotto`,
  `QaJobBuildsCompatTests.qaEachJobsJourneymanSellsTheirBuild`; `ExpansionBuildsGameTests` (`gemGrowers` / M28 gate);
  `QaBlueprintUnlocksGameTests` (a closed expansion's unlocked Gem Grotto stays off the Table);
  `QaBuiltAdvancements1116bGameTests.everyShippedBlueprintHasItsAdvancement`.
- The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
  stove or on a table with a chair; `open_panes`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no
  CHECK line. The ship step keeps the old drawings in `blueprint_history/`, takes the two ids out of `pokemon_jobs.py`'s
  saves, regenerates the modded copies, and updates `docs/guide/gem-grotto-build.md`, `docs/wiki/workplace-looks.md`
  and `StarterBlueprints`' javadoc, which describe the old look.

## Free
Everything not listed above: shape, plan, size within the plot, materials (beyond the lava, plain glass before it, the
amethyst block and cluster, the deepslate cores and the one stonecutter the tests name, and nothing flammable near the
lava), colors, style, roof form, storeys and rooms, how the core chamber joins Level I, extra heat blocks (magma, kept off
the walking line), extra chests, lighting, landscaping, and the village shell outside its fixed cells.
