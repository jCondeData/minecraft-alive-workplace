# Pokémon Center: the brief

Family id(s): `pokemon_center`, `pokemon_center_2` (never renamed; "Pokémon Center", "Pokémon Center II" in `en_us.json`,
`blueprint.aliveworkplace.pokemon_center*`). Variants: village (`village/<style>_pokemon_center` for `plains`, `desert`,
`savanna`, `snowy`, `taiga`; their own contract, see "The village variant"), modded (none to draw: `modded_library.py`
makes `structure/modded/pokemon_center(_2).nbt` from the vanilla drawings; the ship step runs it again), **looks: `lodge`
and `plaza`** (`looks/lodge/pokemon_center(_2)`, `looks/plaza/pokemon_center(_2)`; see "The looks"). The Table's style
buttons restyle every drawing automatically (`BlueprintStyles`), so draw no palette variants.

## What it is for
The Pokémon clinic, a Cobblemon building: it exists for players only with Cobblemon installed (`StarterBlueprints.COBBLEMON_ONLY`:
in the Blueprint Table, library and creative tab only with Cobblemon; operators can still load it). Its heart is Cobblemon's
**Healing Machine** (`cobblemon:healing_machine`), the job site of the **Nurse** (`aliveworkplace:nurse`): a jobless
villager sneak-clicked with a **honey bottle** becomes a Nurse at it (`Stations`; it is never taken by itself, and a
jobless villager who does take it gets Cobblemon's own nurse job, `Stations.insteadByItself`). A player right-clicks
the Nurse and his team goes into her machine and comes out healed, free; a second player waits while it runs; on shift
she keeps the machine charged (`Nurses.treat`, `Nurses.machine`, `NurseWork`). Like any Nurse she also cures the village's
ill with remedies taken from **the containers by her job site** (`NurseWork` → `SupplyContainers.find`). The Steward
builds one by himself in a **civic** zone once the village is Village rank, at most one, Cobblemon loaded
(`steward_rules/pokemon_center.json`, priority 50); the hall's "What next?" suggests one to a Village or bigger that has
none of any tier, style or look (`VillageAdvice.wantsPokemonCenter`). It is unlocked in every new village from the start
with Cobblemon (`blueprint_unlocks/starting.json`), and a Journeyman Nurse sells any of its three looks for 14 emeralds
(`ModTrades`). On the Village Map it is CARE (`VillageMaps`, by id). Finishing any look or style awards
`built/pokemon_center(_2)` (questbook chapter 5 counts the first).

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `pokemon_center` | The working clinic: the Healing Machine (the Nurse's job site) with Cobblemon's PC beside it, the containers by it, a way in from the front | Starting set with Cobblemon; Journeyman Nurse (14 emeralds, a random look); Steward rule above |
| II | `pokemon_center_2` | A **Pasture Block** (`cobblemon:pasture`) and a **Shop Counter** (`aliveworkplace:shop_counter`, the Pokémon Trader's job site: a Poké Ball picks it, `Stations`), both reachable; nothing else the code reads | Upgrade of I (`BlueprintUpgrades`: id + `_2`); offered by the builder who finished I (`UpgradeOffers`). No cost read from the drawing |

Exactly two levels: `StarterBlueprints.POKEMON_CENTER_LOOKS` and `POKEMON_CENTER_2_LOOKS` are three entries each, one
per look, and `PokemonCenterGameTests` asserts that.

## Must have (each level)
- **Job block: exactly one `cobblemon:healing_machine`**, at **template (6, 1, 7)** in Level I and kept there in II (see
  pinned tests). It must be reachable on foot from the ground outside (a Nurse walks to her job site to work); the cell
  **(6, 1, 8)** (the side the old one is worked from) must be a cell a villager can stand in: the scene spawns the Nurse
  there (`PokemonCenterScene`, `MACHINE.south()`). One machine only: each one is a Nurse's job site.
- **Cobblemon's PC (`cobblemon:pc`, two blocks tall) at template (10, 1, 7)** (lower half; upper at (10, 2, 7)) in Level I
  and kept in II (`PokemonCenterCompatTests.aBuilderBuildsThePokemonCenter` checks a Cobblemon block there).
- **Storage: at least one chest or barrel within 8 across (x and z each) and 4 up or down of the Healing Machine**
  (`SupplyContainers.RADIUS`/`VERTICAL`): the Nurse's remedies come out of and go back into these; without one she posts a
  request and says "no remedy". Every chest, barrel, bed head and job block is within 2 across and 1 up or down of a cell
  walked from outside (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`).
- **Level II adds:** a **Pasture Block** (`cobblemon:pasture`, two blocks tall, lower half pinned, today (6, 1, 22)) and
  a **Shop Counter** (pinned, today (6, 1, 14)) (`aBuilderBuildsPokemonCenterII` checks a Cobblemon block or the Shop
  Counter at each; `PokemonCenterScene` checks the Pasture Block). Note: a Shop Counter and a barrel are vanilla-style
  job sites a jobless villager can take; a Pasture Block is taken only with honey or an egg (Habitat or Daycare Keeper).
- **Beds and residents: none required.** No code or test counts beds here. Any bed you add makes its sleeper's home this
  building (`Homes.of`), and if the recipe records bed spots they go in `blueprint_furniture/pokemon_center_2.json`.
- **Furniture spots:** none required by any job or test. If the recipe records any, each spot's blocks equal the
  template's (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`), nothing floats (`FloatingFurnishingsGameTests`),
  every chest stands against a wall, solid block or bed and no standing lantern sits on a room's floor
  (`QaFurnishedRooms1031GameTests`, for builds with spots).
- **Blocks:** only vanilla, ours and Cobblemon's. Without Cobblemon its blocks load as air and the builder skips them,
  and every other block must be placeable (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`);
  no other mod's block (a furniture mod's piece would be a hole without that mod). With Cobblemon the plan must need at
  least one Cobblemon item (`PokemonCenterCompatTests.build`).
- **A front door** on the ground floor on the z = 0 side, with headroom of at least 1.95 over its step and no carpet
  under its lintel (`QaDoorwayClearanceGameTests` sweep, B173), opening onto no wall (B487).
- **Size of Level I: more than 300 non-air blocks** (`PokemonCenterGameTests.pokemonCenterIIKeepsTheHall`, and for every
  look `thePokemonCenterLooksCountAsPokemonCenters`).

## Kept cells (the upgrade)
- II has the same origin and front as I and is at least its size in x, y and z; **at least 60 % of I's non-air blocks
  stay the same block state in the same cell in II** (`PokemonCenterGameTests.pokemonCenterIIKeepsTheHall`; each look's
  II against its own I in `thePokemonCenterLooksCountAsPokemonCenters`).
- The Healing Machine, the PC, every chest and barrel and every other block-entity block stays in the same cell as the
  same block in II (builders never break those while clearing, `MaterialRules.isProtected`).

## Fits
- Plot (y = 0 sits on the clicked block; the front is the z = 0 side), from where tests place each level:
  - Level I: **at most 13 wide x 16 tall x 13 deep** (`BuilderGameTests.buildsPokemonCenter` at (9, 2, 9) in the
    22 x 18 x 22 `big_area`; the builder's barrels, bench and chest stand at x 1-5).
  - Level II: **at most 20 wide x 18 tall x 26 deep** (`BuilderGameTests.buildsPokemonCenterII` and
    `PokemonCenterCompatTests.aBuilderBuildsPokemonCenterII` at (9, 2, 3) in the 30 x 20 x 30 `huge_area`, with a block of
    landscaping margin round it; the compat test's barrels stand at x 1-6, z 5).
  - Showcase (`PokemonCenterScene`): I at x 0 and II at x 34 side by side; the ship step may move these.
- **Material budget: each level fits four barrels, at most 108 stacks** of non-Cobblemon materials (`buildStarter`
  asserts it for I and II and for each look).
- **Build time:** Level I with exactly its materials inside **12,000 ticks** from day time 2,000 (`buildsPokemonCenter`,
  the builder clocks off at dusk); II inside 50,000 (`buildsPokemonCenterII`) and 60,000 with Cobblemon's blocks (compat);
  I with Cobblemon inside 30,000. Keep Level I lean (the old one is 560 blocks).
- Declared sizes: `StarterBlueprints.POKEMON_CENTER` (13, 10, 12), `_2` (13, 17, 26) today; the ship step updates them to
  the new drawing (sizes must match exactly).
- Front and way in: the z = 0 side faces the civic street; the front door there on the ground floor; II keeps it.
- Neighbours: none; placed by the Steward in a civic zone (`StewardWishes.plotFor` tries the village's look first, then
  the other two where it doesn't fit) or anywhere by a player.

## The looks (`lodge`, `plaza`)
- **What picks them:** `BlueprintLooks.LOOKS` maps `pokemon_center` to `["lodge", "plaza"]`. A village's Steward builds
  the look `BlueprintLooks.pick(id, hall)` gives by **a hash of the hall's position** (the same look every time for that
  village, all three turning up over villages); `forVillage` puts it first and the others after in his plot request. The
  look names must **not** be village cultures (`StarterCottageLooksGameTests`: `byCulture(pokemon_center)` is false; a
  family whose looks are all `desert`/`savanna`/`snowy`/`taiga` would be picked by culture instead). A Journeyman Nurse
  sells a random look. Files: `structure/looks/<look>/pokemon_center(_2).nbt`, ids never renamed; names fixed in
  `en_us.json` ("Pokémon Center: Mountain Lodge", "... II: Sunny Plaza", asserted for the lodge II).
- **What reads them as the Pokémon Center:** everything goes by `BlueprintLooks.base` and `StewardConditions.family`
  (family = `pokemon_center`): the Steward's `built_count_below`, the hall's advice, the Village Map (CARE), upgrades
  (`looks/lodge/pokemon_center` → `looks/lodge/pokemon_center_2`, never another look's), advancements
  (`QaBuiltAdvancements1116bGameTests.styleAndLookVariantsAwardTheirBlueprint`), Cobblemon-only listing.
- **A look is another shape, not another palette** (a palette swap is a style, which the Table makes by itself).
- **Every look keeps the same contract as the drawing:** two levels; I with the Healing Machine at (6, 1, 7) and the PC
  at (10, 1, 7) and a container by the machine; II adding a Pasture Block and a Shop Counter; I > 300 non-air blocks;
  II keeps ≥ 60 % of its own I and every block entity in place; ≤ 108 stacks each level; the front at z = 0; only
  vanilla, ours and Cobblemon's blocks. Plots: Lodge II at most **21 wide x 18 tall x 26 deep** (compat at (8, 2, 3)
  with margin; `buildsPokemonCenterLodgeII` at (6, 2, 3); the turned walk below at (4, 2, 25)); Plaza II as II above;
  each look's I as Level I above (both built at (9, 2, 9) in `big_area`).
- **Look-pinned cells today** (the ship step moves them, keeping what each checks): Lodge II Pasture Block (6, 1, 15),
  Shop Counter (16, 1, 6), machine (6, 1, 7) (`aBuilderBuildsTheLodgePokemonCenterII`, scene); Plaza II Pasture Block
  (6, 1, 21), Shop Counter (6, 1, 14), machine (6, 1, 7) (`aBuilderBuildsThePlazaPokemonCenterII`, scene).

## The village variant (`village/<style>_pokemon_center`, five styles)
Shared contract (`village.py` `village_house` and `pokemon_center_room`, `world/VillagePieces`, `village_looks.py`
`DESIGNS["pokemon_center"]`, today 11 x 11 x 11); grown only with Cobblemon and config `pokemonVillageHouses` on, weight 2
(`VillageHouses`). Every style keeps:
- **Size:** the placing test clips at template x -1..10, y 0..11, z -1..11 (`VillageCompatTests.pokemonHouse`): at most
  11 wide x 12 tall x 12 deep, the machine and the Nurse inside that box.
- **The street joint:** a `minecraft:jigsaw` at **(4, 1, 0)**, `north_up`, `minecraft:building_entrance`; the front row
  z = 0, y = 0 places nothing (`VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`).
- **The front door at (4, 1, 2)**, a step before it at (4, 0, 1); no trapdoor at z = 1, y 0-1
  (`VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow`).
- **The room** template x 2-6, z 3-7, y ≤ 4 (`VillagePieces.ROOM_*`); **bed foot (6, 1, 6), head (6, 1, 7), facing
  south** (`VillagePieces` finds our village houses by that bed head).
- **The fit-out:** exactly **one** `cobblemon:healing_machine` and **no other job block of any profession** (no barrel,
  brewing stand, Shop Counter, Pasture Block...) (`QaPokemonVillageHousesCompatTests.everyStyleOfEveryPokemonHouseHasOneJobBlockAndItsWorker`,
  `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`). **No chest at all** (`VillageCompatTests.pokemonHouse`
  asserts 0 chests for the Pokémon Center, though `loot_table/chests/village_pokemon_center.json` exists).
- **The villager:** exactly one `minecraft:villager` entity in the file, profession `aliveworkplace:nurse` (today at
  (5, 1, 5), on a floor with two clear cells above), and no worker-pool jigsaw. Placed, she takes that machine as her job
  site and after 100 ticks stands unhurt in open space inside the house, feet at most 2 above its floor
  (`VillageCompatTests.aVillagePokemonCenterComesWithItsNurse`). No structure void and no lava in the file.

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `PokemonCenterCompatTests.aBuilderBuildsThePokemonCenter` (I at (9, 2, 3) in `huge_area`, with Cobblemon): built from
  exactly its materials, every block right, none skipped; a Cobblemon block at (6, 1, 7) and (10, 1, 7).
- `PokemonCenterCompatTests.aBuilderBuildsPokemonCenterII`: the same for II; a Cobblemon block or the Shop Counter at
  (6, 1, 22) and (6, 1, 14). `aBuilderBuildsTheLodgePokemonCenterII`, `aBuilderBuildsThePlazaPokemonCenterII`: as listed
  under "The looks".
- `PokemonCenterScene` (showcase `pokemon_center`): a Healing Machine block entity at (6, 1, 7) of I, the lodge and the
  plaza; a Pasture Block at II's, Lodge II's and Plaza II's pinned cell; the Nurse spawned at (6, 1, 8); the camera eye
  at template (8.5, 2.7, 4.2) inside the hall, looking at the machine: that cell and the line to the machine stay open.
- `QaDoorwayBugGameTests.aVillagerWalksInByThePokemonLodgesFrontDoor` (Lodge II at (4, 2, 4)): a villager from (6, 1, 13)
  outside reaches (6, 1, 8) inside through the door at (6, 1, 10); `QaBuilderFixes1027bGameTests.aVillagerWalksOutByTheTurnedLodgesFrontDoor`:
  the same the other way, turned counterclockwise at (4, 2, 25).
- `QaLonePanes1031GameTests` (the plaza look, B656): Plaza I built (at (8, 2, 8)) and Plaza II built turned and mirrored
  (at (4, 2, 4)) have every pane, bar, fence and wall joined as the game joins it and no lone pane; across all 24 turns
  and mirrors of both plaza levels more than 100 panes are judged, none ending in air. Its arch check (chiseled sandstone
  keystones at (3, 3, 2) and (9, 3, 2), arch glass under them) is the old drawing's; the ship step re-aims it at the new
  plaza's windows, keeping the join checks and the pane count.
- `BuilderGameTests.buildsPokemonCenter`, `buildsPokemonCenterII`, `buildsPokemonCenterLodge(II)`, `buildsPokemonCenterPlaza(II)`
  (without Cobblemon: all but Cobblemon's blocks built, ≤ 108 stacks); `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`.
- `PokemonCenterGameTests`: `pokemonCenterIIKeepsTheHall` and `thePokemonCenterLooksCountAsPokemonCenters` (above),
  `aStewardBuildsHisVillagesLook` (three looks, the hash pick, the lodge upgrades to the lodge).

By id only (the drawing doesn't matter beyond the rules above): `PokemonCenterGameTests.thePokemonCenterNeedsCobblemon`,
`theHallSuggestsAPokemonCenter`, `thePokemonCenterIsTranslated`; `PokemonCenterCompatTests` (the Nurse's machine tests use
their own machine, not the drawing; `thePokemonCenterIsInTheTable`); `QaPokemonJobsCompatTests`; `ArenaGameTests.theHallSuggestsAnArena`;
`StewardRulesGameTests` (rule `pokemon_center` loads); `QaBlueprintLibraryVerify1108bGameTests` (in the starting set with
Cobblemon); `QaBuiltAdvancements1116bGameTests`; `StarterCottageLooksGameTests` (looks by hash, not culture);
`QaRefreshHistory1028GameTests` (the ship step keeps the old drawings in `blueprint_history/`).

Shared checks over every shipped blueprint: `QaBuildReachGameTests`, `QaDoorwayClearanceGameTests` (1.95 headroom over
every wooden door's step), `QaChairFacingGameTests`, `B648GameTests` (no pane or bar run ends against open air),
`BlueprintsStayGameTests`, `QaMaterialListGameTests`, `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`,
`StyleGameTests.chimneysStayWholeInEveryStyle` (no chimney required). The owner's rules (`check.py`: posts at fence
corners, lamps on tables or posts, crockery only by a stove or on a table with a chair, `open_panes`), STYLE.md's checks
and `holes.py`: `rebuild.py --candidate` prints no CHECK line for any level or look, and `holes.py` passes.

## Free
Everything not listed above: shape, plan, rooms, roofs, materials, colors, style, storeys, where the PC's neighbours,
containers, Pasture Block, Shop Counter and doors stand within the rules, whether there are beds, a counter, benches, a
garden or a pen, what each look is beyond being a different shape that fits its name, and the village variant's look.
