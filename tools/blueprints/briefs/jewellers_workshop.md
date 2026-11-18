# Jeweler's Workshop: the brief

Family id(s): `jewellers_workshop`, `jewellers_workshop_2`, `jewellers_workshop_3` (never renamed; "Jeweler's Workshop",
"Jeweler's Workshop II", "Jeweler's Workshop III" in `en_us.json`, `blueprint.aliveworkplace.jewellers_workshop*`; hidden
advancements `built/jewellers_workshop`, `_2`, `_3`; the village house is `village_piece.aliveworkplace.jewellers_workshop`).
Variants: village (`village/<style>_jewellers_workshop` for the five styles `plains`, `desert`, `savanna`, `snowy`,
`taiga`: `village_looks.py`'s `DESIGNS["jewellers_workshop"]` plus `village.py`'s fit-out `jewellers_room` and job
`aliveworkplace:jeweller`; its own contract, see "The village variant"), modded (none to draw: `modded_library.py` makes
`structure/modded/jewellers_workshop*.nbt` from the vanilla drawings and the ship step reruns it), looks (none: the
Table's style buttons, `BlueprintStyles`, restyle the drawing). ROADMAP item: 50.7 (Milestone 50, the workshops).

## What it is for
The **Jeweler**'s workplace (`ModVillagers.JEWELLER`, config `jewellers`, Milestone 34's Classes and luxuries). The
Jeweler's one job block is a **stonecutter**: a villager becomes a Jeweler when handed a **gold nugget** at a stonecutter
(`work/Stations`, `Jewellers.isNugget`); a jobless villager who finds one by himself becomes a vanilla Mason, and an
amethyst shard makes a Gem Grower. At the stonecutter he cuts rings, brooches and circlets (and, with Cobblemon and Mega
Showdown, Mega Stones from makings in the village store only) through the luxury engine (`jeweller/JewellerWork`,
`craft/LuxuryWork`), taking gems and metal from **the containers within 8 blocks across and 4 up or down of the
stonecutter** (`SupplyContainers.find(level, station)`, `RADIUS` 8, `VERTICAL` 4), the village store and other workers'
chests, and putting what he makes back into those chests. **With no container in that reach he makes nothing at all**
(`LuxuryWork.choose`: "nowhere to put what's made"). Players come to buy. It is not in a new village's starting set: a
**Journeyman Jeweler sells the Level I blueprint** (`ModTrades.journeymanBlueprint`), a builder who finished Level I sells
Level II and one who finished Level II sells Level III (`UpgradeOffers`). The **Steward** builds Level I by himself in a
**`market`** zone (not `workshops`) when a Jeweler is wanted and has no workstation
(`steward_rules/workplace_jewellers_workshop.json`, priority 62, cooldown 2 days, Hamlet and up). On the Village Map it is
a Workshops building (`VillageMaps.kindOf`, by name). All three levels hide from the Table while the `jewellers` switch is
off (`StarterBlueprints` expansion switch). In world generation the five village styles grow the village variant (pool
weight 2, only while `jewellers` is on) with a Jeweler already in the job and a chest of gold and stones (loot table
`chests/village_jewellers_workshop`).

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `jewellers_workshop` | The shop: the stonecutter with a lantern over it, the Jeweler's chest in reach, an amethyst cluster in a glass case, an iron door for players and a wooden door the Jeweler uses (test-pinned below); **no iron bars** | Sold by a Journeyman Jeweler; Steward rule `workplace_jewellers_workshop` (`market` zone, Hamlet+) |
| II | `jewellers_workshop_2` | A strong room / vault: **at least 10 chests** behind **at least 10 iron bars**, the build now has **exactly 3 iron doors** (test-pinned) | Upgrade of I by name (`BlueprintUpgrades`: id + `_2`); sold by the builder who finished I (`UpgradeOffers.offer`, price from solid blocks / 40, clamped 6-32 emeralds) |
| III | `jewellers_workshop_3` | A cellar vault dug **4 blocks** under Level II, reached by a ladder under a trapdoor hatch in the ground floor; Level II above it unchanged | Upgrade of II by name; one of the eight cellared Level IIIs (`StarterBlueprints.LEVEL_3_CELLARS`, `WITH_BASEMENTS`); sold by the builder who finished II |

**Exactly three levels.** No `_4`: `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` counts exactly 64 upgrades, and
`CellarGameTests.levelThreesLineUpOverTheirLevelTwos` exactly 8 cellared Level IIIs.

**Upgrade rules the code reads:**
- **II over I** (`BlueprintUpgrades`, `BuilderGameTests.starterUpgradesKeepMostOfTheirBase`): same origin and front; II is
  at least I's size in x, y and z; **at least 60 % of I's non-air blocks stay the same block in the same cell**. Draw them
  together, II growing out of I. I's stonecutter and chest keep their cells (a Jeweler at work keeps his block; builders
  never break containers).
- **III over II** (`CellarGameTests.levelThreesLineUpOverTheirLevelTwos`), much stricter: III's size **equals** II's
  (x, y, z above ground), its basement is **exactly 4** (`b3.basement() == 4`), and **every block of II at y >= 0 is the
  same in III, with one exception only: a trapdoor at template y = 0 (the hatch) in a column that the cellar occupies**.
  III must have that hatch (a trapdoor at y = 0 over a cellar column, "has no hatch down into its cellar" otherwise). So
  II must already have, at y = 0, a floor cell inside the building, on the walking floor, where a hatch can replace it
  with a ladder below; III can add nothing above ground (no lamp, sign or furniture).

## Must have (each level)
- **Job block: exactly one, a `minecraft:stonecutter`**, in I, II and III
  (`QaLuxuryWorkshopsGameTests.everyTierHasOneJobBlockAndItIsItsWorkersOwn`: exactly one block in the whole file that
  *any* job takes as its job site, and it is a stonecutter a Jeweler takes; `LuxuryWorkshopsGameTests.luxuryWorkshopBuilt_jewellers_workshop`
  / `_2`: exactly one stonecutter and none of blast furnace, smithing table, grindstone, fletching table, cartography
  table, barrel, lectern, composter, cauldron, smoker, brewing stand, loom, crafting table). So **no barrel and no
  lectern anywhere, III's cellar included** (`QaLuxuryWorkshopsGameTests.noWineryHasABarrelAndNoPrintShopALectern`
  checks every tier of all four luxury shops), **no smithing table** (the village pieces' forbidden block), no jukebox,
  bell or mod workstation (Scholar's Desk, Shop Counter, ...). A chest, furnace or anvil is not a job block.
- **A `minecraft:lantern` above the stonecutter in its own column** (same x and z, any height above it; not a soul
  lantern) in I and II (`LuxuryWorkshopsGameTests.thePrintShopAndTheJewellersHaveWhatTheirTradesNeed`). It needs
  something to hang from or stand on (`FloatingFurnishingsGameTests`).
- **The gem case** (same test, I and II): **exactly one `minecraft:amethyst_cluster`** in the whole file, with **at least
  two plain `minecraft:glass_pane`** at the cluster's height and next to it (same y, Manhattan distance 1, so side by side
  horizontally). Not stained panes. (III keeps II's cluster; the test does not count III, so the cellar may hold more.)
  Every pane run must end against a block, not air (`B648GameTests`, `QaWindowsAndWards1028BugTests`).
- **Doors** (same test): **Level I has at least one `minecraft:iron_door`** (two blocks); **Level II has exactly three
  iron doors** (`count == 6` blocks: not two, not four); **each of I and II has at least one wooden door** (any
  `#minecraft:wooden_doors`). A villager can't open an iron door or plan a path through a shut one, so **the Jeweler's way
  from the street to the stonecutter must use only open ways and wooden doors**: the stonecutter can't stand behind an
  iron door or bars. (A player opens an iron door with a button or lever; no test checks that, but without one the door
  is a wall.)
- **At least 40 `minecraft:stone_bricks`** blocks (plain stone bricks, not mossy, cracked or chiseled) in I and in II
  (same test). This and the blocks above are the only blocks a test names.
- **Iron bars: Level I has none at all** (`count == 0`: no barred window either); **Level II has at least 10
  `minecraft:iron_bars`** (same test).
- **The vault (Level II): at least 10 `minecraft:chest` blocks at template z >= 9** (same test, which reads the vault as
  the cells behind z = 8, where the old Level I ended). The simplest fit is a vault behind Level I's footprint, Level I at
  most 9 deep. If Level I is deeper or the vault sits elsewhere, the ship step moves that test line to the new vault's
  cells, keeping "at least 10 chests in the vault".
- **The Jeweler's chest within reach of the stonecutter: at least one container within 8 across and 4 up or down** in
  every level (`LuxuryWork.choose`; nothing is made otherwise). No test counts Level I's containers (unlike the Print
  Shop); the old Level I has exactly one chest. Chests stand against a wall, solid block, bed or chest
  (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`) and are drawn empty (builders never fill or copy
  containers; no loot table in the buildables). The vault's and cellar's chests count as the Jeweler's own stores only
  where they are within that reach (free).
- **Walked in from the street.** A Jeweler spawned three blocks before the front edge at template **(1, 0, -3)** walks in
  and claims the stonecutter as his job site after the build (`luxuryWorkshopBuilt_jewellers_workshop`, `_2`). The
  stonecutter, every chest (the vault's and the cellar's too) and any bed head are within a player's reach (2 across, 1 up
  or down) of a cell walked to from the ground outside, by level floors, one-block steps up, drops of up to 3, ladders,
  doors and trapdoors (this check treats every door as open) (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`).
  Every wooden doorway has room for a villager 1.95 blocks tall (`QaDoorwayClearanceGameTests`); nothing at head height
  on the walk in (a shut top-half trapdoor there is a wall to a villager, B156).
- **The cellar (Level III):** reached by a ladder under the hatch (`QaBuildReachGameTests`: its chests must be walked to).
  The cellar's open cells must be dug out by the builder (`CellarGameTests.assertBasementEmpty`), so every cell left as
  air below y = 0 is one he digs.
- **Beds and residents: no bed is required** (no code counts this building's beds; the old one has none). Any bed drawn
  counts as a village bed; its head is reachable, its foot has its head one block towards its facing
  (`BlueprintsStayGameTests`), and no bed right inside a doorway (`check.py`). No villager or other entity in any file
  ("comes with a villager"); no jigsaw or structure void.
- **Furniture spots: none required.** The Jeweler's Workshop is not in `furniture.py`'s `WORKPLACES` and has no
  `blueprint_furniture/jewellers_workshop*.json` today; no test asks for a desk or counter. Every furniture piece the plan
  records becomes a spot (`rebuild.py`), and then: each spot's blocks are exactly the template's
  (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`), a furnished room must read as a room and have its own
  light (`furniture.py` `room_problems`, `dark_rooms`, printed as CHECK lines; `FurnishedRoomsGameTests.everyFurnishedRoomHasALight`),
  nothing small stands on air (`FloatingFurnishingsGameTests`), every chair faces its table, turned and mirrored too
  (`QaChairFacingGameTests`), no standing lantern on a room's floor (`QaFurnishedRooms1031GameTests`).
- Every block is one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).

## Fits
- **Plot, Level II and III: at most 18 wide x 13 tall (above ground) x 21 deep; Level I at most that too** (II is at least
  I in every axis). Where it comes from:
  - `luxuryWorkshopBuilt_jewellers_workshop` / `_2` (`CraftWorkplacesGameTests.build`) place I and II at helper
    (12, 2, 8) in the 30 x 20 x 30 `huge_area`: x 12..29, y 2..19, z 8..29, so at most 18 x 18 x 22. The builder's bench
    (3, 2, 3) and three stock chests (3, 2, 6/8/10) stand west of it.
  - `CellarGameTests.jewellersThreeDigsItsVault` places II whole at helper **(6, 7, 9)** on earth five deep (y 2-6) in
    the same 20-tall `huge_area` and builds III over it: **above ground the build reaches helper y 7 + height - 1 <= 19,
    so at most 13 tall**, at most 24 wide and 21 deep; the cellar goes down to helper y 3 (stone at y 2-3). Taller means
    moving that test to the 30-tall `tall_area` (the ship step's call).
  - The old drawings are 9 x 10 x 9 (I), 9 x 10 x 14 (II), 9 x 10 x 14 with a 4-deep basement (III).
- y = 0 sits on the clicked block; the front is the z = 0 side. III's basement is 4 deep below that.
- **Front and way in:** the z = 0 side faces the player who places it and the road of a Steward `market` plot (`Plots`).
  The Jeweler arrives from template (1, 0, -3) and finds his way in by a wooden door.
- **Declared sizes:** `StarterBlueprints.JEWELLERS_WORKSHOP` `(9, 10, 9)`, `_2` `(9, 10, 14)`, `_3` `(9, 10, 14)` with
  basement 4 today; the ship step changes those `Vec3i`s and their javadoc (which describes the old look)
  (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`, `luxuryWorkshopBuilt_*`: "is <size>"; III's must equal II's).
- **Material budget: at most 81 stacks each for I and for II**, each built from bare ground (three chests of 27 stocked with
  exactly the supply report's materials, each item rounded up to whole stacks). The old ones are about 23 and 26 stacks.
- **Build time, the binding limit for I and II.** `luxuryWorkshopBuilt_*` set day time 2,000 (build delay 2, builders'
  help off, 12,000-tick timeout) and do not hold the clock: the builder works only until dusk (about day time 11,000,
  B1384), so each level must be finished in about 9,000 ticks and the Jeweler must then walk in and claim the stonecutter
  before the villagers' rest. Measured rates are about 120 to 130 placed blocks per 1,000 ticks (B1384: 993 placed by
  tick 9,000). **Block budget: at most about 750 placed blocks for Level II as a whole building, a hard ceiling of about
  900** (Level I less). The old drawings are 336 (I) and 527 (II) non-air blocks. No roof, overhang or eave over another
  roof cell the builder must stand on (B1384).
- **Cellar time (III):** `jewellersThreeDigsItsVault` has a 24,000-tick timeout from day time 2,000 (build delay 2, clock
  not held): the dig and fit-out must finish within one working day, and the builder must place **fewer than 60 % of III's
  structure and decoration steps** (he keeps Level II). The old III is 680 non-air blocks (about 150 of them in the
  cellar, 36 stacks); keep the cellar about that size.
- **Neighbours:** a workplace in a Steward `market` zone; the Steward keeps 2 blocks clear round its plot (`Plots.CLEAR`)
  and wants the ground under it within 4 blocks of level (`Plots.MAX_STEP`). No party walls.

## The village variant (`village/<style>_jewellers_workshop`, five styles)
In scope only if the designer redraws `village_looks.py`'s `jewellers_workshop` look (today 9 x 12 x 11). Village houses
share one contract the Java reads by template coordinate (`world/VillagePieces`, `world/VillageHouses`, `village.py`'s
`village_house`, `village_looks.py` header); the designer draws only the look, `village.py` adds the jigsaw, fit-out, bed
and villager. Every style keeps:
- **At most 10 wide** (`VillageGameTests.theJewellersWorkshopsJewellerTakesItsBench` places the plains and the desert
  house 11 apart, at helper (1, 1, 4) and (12, 1, 4) in the 22 x 18 x 22 `big_area`).
- The street jigsaw at **(4, 1, 0)** facing north, the front row z = 0 at y = 0 left as the ground, the door at
  **(4, 1, 2)** with a step before it, no trapdoor at z = 1, y 0-1 (`VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`,
  `noVillageHouseHasATrapdoorInItsFrontWalkRow`); the room x 2-6, z 3-7, the west wall at x = 1; the bed foot (6, 1, 6),
  head (6, 1, 7); the villager at (5, 1, 5).
- The fit-out (`jewellers_room`, moved in by one): **the stonecutter at (2, 1, 7)**, a chest at (2, 1, 6) with loot table
  `aliveworkplace:chests/village_jewellers_workshop`. **Exactly one job block** and no smithing table, barrel or lectern
  (`VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`, `QaLuxuryWorkshopsGameTests.everyStylesPieceHasItsBlockAndAWorkerWhoTakesIt`);
  placed as a village does, its Jeweler takes that stonecutter within 2,400 ticks (`theJewellersWorkshopsJewellerTakesItsBench`).
  The iron-door, glass-case and stone-brick rules do not apply here.
- Weight 2 in every style's pool while `jewellers` is on, by id (`QaLuxuryWorkshopsGameTests.eachVillagePieceWeighsTwoInEveryStyle`,
  `LuxuryWorkshopsGameTests.villagesGrowThePrintShopAndTheJewellersOnlyWhileTheirJobsAreOn`); `everyVillageTypeCanGrowTheOtherHouses`
  pins `VillageHouses.houseNames()` with `jewellers_workshop` last of the non-Cobblemon houses.

## Rules tests check
Pinned counts and coordinates (the ship step moves coordinates to the new drawing, keeping what each checks, dropping
none):
- `LuxuryWorkshopsGameTests.thePrintShopAndTheJewellersHaveWhatTheirTradesNeed` (I and II): a `lantern` above the
  stonecutter in its column; exactly 1 `amethyst_cluster` with >= 2 `glass_pane` beside it at its height; >= 2 `iron_door`
  blocks; a wooden door; >= 40 `stone_bricks`. I: 0 `iron_bars`; II: >= 10 `iron_bars`, >= 10 `chest` at z >= 9,
  exactly 6 `iron_door` blocks.
- `LuxuryWorkshopsGameTests.luxuryWorkshopBuilt_jewellers_workshop`, `luxuryWorkshopBuilt_jewellers_workshop_2`
  (`CraftWorkplacesGameTests.build`: house at (12, 2, 8), Jeweler spawned at (13, 2, 5)): size matches the declared size;
  no entity; exactly one stonecutter and none of the other workstations; at most 81 stacks; built by a builder; then the
  Jeweler from the street holds a stonecutter inside the build's box as his `JOB_SITE` and is still a Jeweler.
- `CellarGameTests.levelThreesLineUpOverTheirLevelTwos` (III upgrades II, same size, basement 4, II unchanged but for the
  hatch, a hatch over the cellar, a listed lockable build, sold by II's builder) and `jewellersThreeDigsItsVault` (III
  built over a finished II on earth at (6, 7, 9): every block right, the basement's air dug out, II's chests still where
  they were, under 60 % of the steps placed).
- `QaLuxuryWorkshopsGameTests.everyTierHasOneJobBlockAndItIsItsWorkersOwn`, `noWineryHasABarrelAndNoPrintShopALectern`
  (all three levels).
- `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (II over I; III over II), `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`.
- Not a test, but pinned: the showcase scene `luxury_workshops` (`devclient/LuxuryWorkshopsScene.PIECES`) expects the
  stonecutter at template **(2, 1, 4)** in I and II and **(2, 1, 7)** in `village/plains_jewellers_workshop`, and no barrel
  or lectern; `tools/showcase/scenes.py` captions `25_jewellers_workshop_2_back` "Jeweler's Workshop II: the strong room
  behind". The ship step moves the cells and the caption.
- `QaReleaseRefresh1028GameTests.REDRAWN` lists all three ids with the history key `cd1557d8c` (0.151.0's drawing, put
  up and upgraded in place): the ship step keeps that history entry and adds the new drawing's (`history.py`).

By id only (the drawing doesn't matter beyond the rules above): `LuxuryWorkshopsGameTests.luxuryWorkshopRule_jewellers_workshop`
(the rule builds it in `market`), `luxuryWorkshopsAreInTheBlueprintTable`, `journeymenSellTheirBuildingsBlueprint`;
`QaLuxuryWorkshopsGameTests.theJourneymansBlueprintIsTheTablesOwnAndReadsInEnglish`, `eachStewardRuleHoldsForItsOwnJobOnly`,
`everyNameAPlayerReadsIsAmericanEnglish`; `ExpansionBuildsGameTests.laterExpansionsBuildsWaitForTheirSwitch`;
`CrafterWorkshopGameTests`; `ModdedLibraryGameTests` (regenerate the modded copies).

Library-wide checks it must pass: `B648GameTests` and `QaWindowsAndWards1028BugTests` (no pane or bar run ending
against air), `QaFurnishedRooms1031GameTests`, `FurnishedRoomsGameTests`, `BlueprintsStayGameTests`,
`QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`, `QaBuildReachGameTests`, `QaMirroredBuildsGameTests` (doors,
beds and double chests stay whole turned and mirrored), `QaTurnedFramesGameTests`, `FurnitureSpotGameTests`,
`FloatingFurnishingsGameTests`, `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`,
`QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`, `StyleGameTests.chimneysStayWholeInEveryStyle`
(only if a campfire stands on a solid stack).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair, no bed right inside a door; `open_panes`), STYLE.md's checks and `holes.py`:
`rebuild.py --candidate` prints no CHECK line (III with `--basement 4`). `owner_rules_baseline.json` excuses one finding in
the old `jewellers_workshop_2` and `_3` (and their modded copies); the new drawings must pass outright, and the ship step
drops those four baseline lines. The ship step keeps the old drawings in `blueprint_history/` (`history.py`), takes the
three ids out of `workshops.py`/`cellars.py`'s saves (`generate.py`, `cellars.LEVEL_3`), regenerates the modded copies,
and updates `docs/wiki/luxury-workshops.md`, `docs/wiki/cellars.md` and `docs/guide/jewelers-workshop-build.md`, which
describe the old look.

## Free
Everything not listed above: shape, plan, roof, materials (beyond the stone bricks, iron doors and bars, plain glass
panes, the one amethyst cluster and the lantern the tests count), colors, storeys and rooms, where the stonecutter,
chests, gem case and vault stand within the reaches above, how Level II's vault joins Level I, what the cellar holds
(no job block, no barrel), beds (none needed), extra chests, lighting (within the light rules), and the village
variant's look outside its room in each of the five styles.
