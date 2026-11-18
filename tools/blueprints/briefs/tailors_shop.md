# Tailor's Shop: the brief

Family id(s): `tailors_shop`, `tailors_shop_2` (never renamed; "Tailor's Shop", "Tailor's Shop II" in `en_us.json`,
`blueprint.aliveworkplace.tailors_shop*`; the village house is `village_piece.aliveworkplace.tailors_shop`, "Tailor's
Shop"; hidden advancements `built/tailors_shop`, `built/tailors_shop_2`)
Variants: village (`village/<style>_tailors_shop` for the five village styles `plains`, `desert`, `savanna`, `snowy`,
`taiga`, saved by `generate.py` through `village.py`'s `village_house` with the fit-out `tailors_room` and the job
`aliveworkplace:tailor`; Repurposed Structures' villages reuse these five, `VillageHouses.MODDED_HOUSE_POOLS`; the
village variant has its own contract, see "The village variant"), modded (none to draw: `modded_library.py` makes
`structure/modded/tailors_shop*.nbt` from the vanilla drawings and the ship step reruns it), looks (none: the Table's
style buttons, `BlueprintStyles`, restyle the drawing automatically).

## What it is for
The **Tailor**'s workplace (`ModVillagers.TAILOR`, config `tailors`, part of Milestone 34's Classes and luxuries; open
to Peasants). The Tailor's one job block is a **loom** (`PoiTypes.SHEPHERD`): a villager is made a Tailor by handing him
**string** at a loom (`work/Stations`); a jobless villager who finds a loom by himself becomes a vanilla Shepherd
instead, and shears turn a Tailor back into a Shepherd. At the loom he sews the luxury garments (work clothes, fine
clothes, noble robes: `tailor/TailorWork`, the shared `craft/LuxuryWork` engine, recipes in `luxury_recipes/`) from wool,
string and dyes, taking makings from **his own chests, the containers within 8 blocks across and 4 up or down of the
loom** (`SupplyContainers.find(level, station, null)`, `RADIUS` 8, `VERTICAL` 4), from the village store and other
workers' stashes, and putting what he makes back into those chests. **With no container within that reach he makes
nothing at all** (`LuxuryWork.choose`: "nowhere to put what's made"). The Tailor's Shop is not in a new village's
starting set: a **Journeyman Tailor sells its blueprint** for 12 emeralds (`ModTrades.journeymanBlueprint`), and a
builder who finished Level I sells Level II (`UpgradeOffers`). The **Steward** builds it by himself in a **`market`**
zone (not workshops) when a Tailor is wanted and has no workstation (`steward_rules/workplace_tailors_shop.json`,
priority 62, cooldown 2 days, Hamlet and up). The Village Map shows it as a workshop (`VillageMaps.kindOf`, by name).
Both levels belong to the `tailors` expansion switch (`StarterBlueprints.expansionSwitch`,
`ExpansionBuildsGameTests.laterExpansionsBuildsWaitForTheirSwitch`). In world generation, the five village styles grow
the village variant (pool weight 2, `VillageHouses.houses()`, only while `tailors` is on, `VillageHouses.LUXURY_HOUSES`)
with a Tailor already in the job and a chest of wool, string and dyes (loot table `chests/village_tailors_shop`).

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `tailors_shop` | The loom; the Tailor's chest(s) within reach of it; bolts of wool on show: at least 8 wool blocks in at least 6 colours (test-pinned, see Must have); **no ladder** | Sold by a Journeyman Tailor (12 emeralds); Steward rule `workplace_tailors_shop` (market zone, Hamlet+) |
| II | `tailors_shop_2` | **At least 12 more wool blocks** than Level I, and **a ladder of at least 9 rungs** (the test's "a ladder through both floors"); still one loom | Upgrade of I (`BlueprintUpgrades`: id + `_2`); sold by the builder who finished I |

No third level and no other id: `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` counts exactly 64 upgrades
library-wide, and `QaLuxuryWorkshopsGameTests.shops()` lists exactly these two tiers.

## Must have (each level)
- **Job block: exactly one, and it is a `minecraft:loom`**, in I and in II
  (`QaLuxuryWorkshopsGameTests.everyTierHasOneJobBlockAndItIsItsWorkersOwn`: exactly one block in the whole file that
  *any* job takes as its job site, it is a loom, and a Tailor takes it; `LuxuryWorkshopsGameTests.luxuryWorkshopBuilt_tailors_shop`
  / `_tailors_shop_2`: exactly one `LOOM` and none of blast furnace, smithing table, grindstone, stonecutter, fletching
  table, cartography table, barrel, lectern, composter, cauldron, smoker, brewing stand, crafting table). So **no
  barrel and no lectern anywhere** (`QaLuxuryWorkshopsGameTests.noWineryHasABarrelAndNoPrintShopALectern` checks every
  luxury tier), no composter (the test's "block that would make another job" for this shop), no second loom, no
  workstation for looks, and no job block of this mod (Builder's Bench, Shop Counter, mailbox, ...). A chest, furnace,
  bed or banner is not a job block.
- **The Tailor's chest within reach of the loom:** at least one `minecraft:chest` within **8 across and 4 up or down**
  of the loom (`SupplyContainers.RADIUS` / `VERTICAL`). No test checks this, but without it the Tailor sews nothing.
  Chests are drawn empty (builders never fill or copy containers; no loot table in the buildables).
- **Bolts of wool** (`LuxuryWorkshopsGameTests.theBuildsHaveWhatTheirTradesNeed`; only blocks in `#minecraft:wool`
  count, the sixteen colored wool blocks: carpets, banners and beds do not):
  - **Level I: at least 8 wool blocks, in at least 6 different colors** (distinct wool blocks).
  - **Level II: at least Level I's wool count + 12.**
- **Ladders** (same test, `count(..., LADDER)`): **Level I has no `minecraft:ladder` at all; Level II has at least 9
  ladder blocks.** So any upper floor in I is reached by stairs, and II's ladder run (9 or more rungs in total) must
  be one a villager can actually use, since any chest or bed up there must be walked to (below).
- **The loom is reached on foot from the street in front.** The arriving Tailor, spawned three blocks before the front
  edge, walks in and claims the loom as his job site (`luxuryWorkshopBuilt_*`; see "Rules tests check"). The loom, every
  chest and every bed head are within a player's reach (2 across, 1 up or down) of a cell walked to from the ground
  outside, by levels, one-block steps up, drops of up to 3, ladders, doors and trapdoors
  (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`). If the loom is built in under solid
  blocks, some side of it needs a floor cell with 2 blocks of head room to stand on (`work/JobSiteApproach`,
  `BuiltInJobSiteGameTests.besideABuiltInWorkstation`). Every wooden doorway has room for a villager 1.95 blocks tall
  (`QaDoorwayClearanceGameTests`; no carpet just inside a 2-tall doorway). No fence gate or trapdoor on the only way in.
- **Beds and residents:** none required (no code counts the Tailor's Shop's beds; the old drawings have none). Any bed
  drawn counts as a village bed for the Steward and shows on the Table as "Beds: N"; its head must be reachable on foot,
  its foot has its head one block towards its facing (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`),
  and no bed right inside a doorway (`check.py`).
- **Storage:** chests only; **no barrel**. Every indoor chest stands against a wall, a solid block, a bed or another
  chest (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`). Double chests, doors and beds stay whole
  turned and mirrored (`QaMirroredBuildsGameTests.everyBuildsDoorsBedsAndDoubleChestsStayWholeTurnedAndMirrored`).
- **Furniture spots:** no kind is required by any test (no `blueprint_furniture/tailors_shop*.json` today; not a home,
  `FurnishedHomesGameTests` does not list it). Every piece drawn is recorded as a spot by `rebuild.py`; each spot's
  blocks are exactly the template's there (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); nothing small
  stands on air (`FloatingFurnishingsGameTests`); every chair stair faces its table, turned and mirrored too
  (`QaChairFacingGameTests`); every piece on a furnished room's floor has block light above 7 from that room's own
  lantern, hung or on a table (`FurnishedRoomsGameTests.everyFurnishedRoomHasALight`); no standing lantern on a room's
  floor (`QaFurnishedRooms1031GameTests.noLanternStandsOnARoomsFloor`). Frames and paintings, if any, hang turned and
  mirrored (`QaTurnedFramesGameTests`, `QaLedgerFramesHaulGameTests.everyBlueprintsFramesAndPaintingsHangTurnedAndMirrored`).
- **No entity** (villager, frame entity, armor stand, ...) in either level's file (`luxuryWorkshopBuilt_*`: "comes with a
  villager"); no jigsaw or structure void in the buildables; no basement (neither level is in `WITH_BASEMENTS`).
- Every block is one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).

## Kept cells (the upgrade)
- II has the same origin and front as I and is **at least I's size in x, y and z**; **at least 60 % of I's non-air
  blocks stay the exact same block state in the same cell** (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`).
  Levels grow out of each other (REBUILD.md): II adds its extra wool and its ladder to I's building.
- Every chest stays in the same cell as the same block in II (builders never break containers or copy their contents,
  `MaterialRules.isProtected`), and the loom stays where it is (one loom per level).

## Fits
- **Plot (I and II): at most 18 wide x 18 tall x 22 deep** above the ground. `luxuryWorkshopBuilt_tailors_shop` and
  `_tailors_shop_2` (`CraftWorkplacesGameTests.build`) place the build at helper (12, 2, 8) in the 30 x 20 x 30
  `huge_area` (20 tall), so x 12..29, y 2..19, z 8..29; the builder's bench at (3, 2, 3) and its three chests at
  (3, 2, 6/8/10) stand west of it, the builder at helper `BUILDER` beside them.
- y = 0 sits on the clicked block; the front is the z = 0 side.
- **Declared sizes:** `StarterBlueprints.TAILORS_SHOP` (`11, 11, 12`) and `TAILORS_SHOP_2` (`11, 15, 12`) today; if a size
  changes the ship step changes those `Vec3i`s (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`,
  `luxuryWorkshopBuilt_*`: "is <size>"), and `modded_library.py` reads the sizes from that file.
- **Material budget: at most 81 stacks each for I and for II**, each built from bare ground (three chests of 27 stocked
  with exactly what the supply report says is missing, each item rounded up to whole stacks;
  `luxuryWorkshopBuilt_*`: "needs N stacks").
- **Build time and block budget (the dusk lesson):** `CraftWorkplacesGameTests.build` sets day time **2,000**, and the
  builder works only in the villager WORK shift, so he **stops at dusk, about day time 9,000**, and does not resume
  before the test's **12,000-tick** timeout ends. The whole build (build delay 2, builders' help off) must reach DONE in
  about **7,000 ticks**, and the Tailor must then still walk in and claim the loom inside the same 12,000. Level II is
  built **from bare ground**, not on top of I. The old drawings are 532 (I) and 767 (II) non-air blocks and pass; a
  1,410-block build timed out at 12,000 ticks elsewhere (`chapel.md`). **Keep Level II at or under about 800 placed
  (non-air) blocks**, and Level I smaller, with no roof, barge or eave stair over another roof for the builder to get
  stuck on.
- **Front and way in:** the z = 0 side faces the player who places it and the road of a Steward plot. The arriving
  Tailor is spawned at helper (13, 2, 5) = template **(1, 0, -3)**, three blocks before the front edge, and must find
  his way in from there.
- **Neighbours:** a workplace in a Steward `market` zone; the Steward keeps 2 blocks clear round its plot
  (`Plots.CLEAR`) and wants the ground under it within 4 blocks of level (`Plots.MAX_STEP`). No party walls.

## The village variant (`village/<style>_tailors_shop`, five styles)
Village houses share one contract that the Java reads by template coordinate (`world/VillagePieces`,
`world/VillageHouses`, `village.py`'s `village_house`, the `village_looks.py` header). The designer draws only the look
(`village_looks.py` `DESIGNS["tailors_shop"]`, today 9 x 12 x 11); `village.py` adds the jigsaw, fit-out, bed and
villager. Every style keeps exactly:
- **At most 10 wide** (`VillageGameTests.theTailorsShopsTailorTakesItsLoom` places the plains and the desert house 11
  apart, at helper (1, 1, 4) and (12, 1, 4) in the 22 x 18 x 22 `big_area`; so also at most 18 deep and 17 tall).
- **The street joint:** a `minecraft:jigsaw` at **(4, 1, 0)**, orientation `north_up`, name and target
  `minecraft:building_entrance`, final state `minecraft:structure_void`; **the front row z = 0 at y = 0 places nothing**
  (x 0-8 stay the ground the house landed on), and placing it as a village does leaves **no structure_void block in the
  world** (`VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`).
- **The front door at (4, 1, 2)** (lower half facing south), a step before it at (4, 0, 1); **no trapdoor at z = 1,
  y 0-1** (`VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow`).
- **The room:** template **x 2-6, z 3-7, y up to 4** (`VillagePieces.ROOM_MIN_X ... ROOM_MAX_Y`), bounded by the west
  wall at x = 1: a house is recognised by its room, and a hall look change rebuilds everything outside the room.
- **The bed: foot (6, 1, 6), head (6, 1, 7), facing south** (`VillagePieces.find` uses this cell to find every house).
- **The fit-out** (`village.py`'s `tailors_room`, moved in by one): exactly one **loom at (2, 1, 7)**, a chest at
  **(2, 1, 6)** with `LootTable` `aliveworkplace:chests/village_tailors_shop`, and wool at (2, 1, 4), (2, 2, 4) and
  (2, 1, 3). **Exactly one job block in the whole template**, and **no composter, barrel or lectern** anywhere in it
  (`VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`; `QaLuxuryWorkshopsGameTests.everyStylesPieceHasItsBlockAndAWorkerWhoTakesIt`:
  one loom, one job block, 0 forbidden blocks): the look outside the room may add no workstation.
- **The villager:** **exactly one entity, a villager already a Tailor**, at **(5, 1, 5)**, and no worker-pool spawn
  jigsaw; keep (5, 1, 5) and (5, 2, 5) clear air with floor under it.
  `VillageGameTests.theTailorsShopsTailorTakesItsLoom`: placed as a village places it, his job site is that loom and he
  keeps the job.
- A ladder to every furnished upper floor (`village_looks.py` header).
- Every pool lists it at weight 2 while `tailors` is on (`QaLuxuryWorkshopsGameTests.eachVillagePieceWeighsTwoInEveryStyle`,
  `LuxuryWorkshopsGameTests.villagesGrowTheHousesOnlyWhileTheirJobsAreOn`), by id.

## Rules tests check
Pinned coordinates and counts (the ship step moves coordinates to the new drawing, keeping what each checks, dropping
none):
- `LuxuryWorkshopsGameTests.theBuildsHaveWhatTheirTradesNeed`: I has >= 8 `#minecraft:wool` blocks in >= 6 distinct
  wool blocks; II has >= I's wool count + 12; I has 0 ladders and II >= 9 ladder blocks.
- `LuxuryWorkshopsGameTests.luxuryWorkshopBuilt_tailors_shop`, `luxuryWorkshopBuilt_tailors_shop_2`
  (`CraftWorkplacesGameTests.build`, `HOUSE` (12, 2, 8), `BENCH` (3, 2, 3), `CHESTS` (3, 2, 6/8/10), day time 2,000, build
  delay 2, builders' help off, worker at (13, 2, 5), 12,000 ticks): size matches the declared size; no entity; exactly
  one loom and none of the other workstations listed above; at most 81 stacks; built to DONE by a builder; then a Tailor
  from the street holds a loom inside the build's box as his `JOB_SITE` and is still a Tailor.
- `QaLuxuryWorkshopsGameTests.everyTierHasOneJobBlockAndItIsItsWorkersOwn`, `noWineryHasABarrelAndNoPrintShopALectern`
  (I and II).
- `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (II over I: not smaller, 60 % kept),
  `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`.
- Village variant: `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`, `everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`,
  `noVillageHouseHasATrapdoorInItsFrontWalkRow`, `theTailorsShopsTailorTakesItsLoom`;
  `QaLuxuryWorkshopsGameTests.everyStylesPieceHasItsBlockAndAWorkerWhoTakesIt`, `eachVillagePieceWeighsTwoInEveryStyle`;
  `LuxuryWorkshopsGameTests.villagesGrowTheHousesOnlyWhileTheirJobsAreOn`; `VillageCompatTests.repurposedStructuresVillagesGetOurHouses`.
- Not a test, but pinned: the showcase scene `luxury_workshops` (`devclient/LuxuryWorkshopsScene.PIECES`) expects the
  loom at template (2, 1, 5) in I and II and at (2, 1, 7) in the plains village house; its still `16_tailors_shop_2`
  has a caption in `tools/showcase/scenes.py`. The ship step moves the first to the new loom and rewrites the caption.

By id only (the drawing doesn't matter beyond the rules above): `LuxuryWorkshopsGameTests.luxuryWorkshopRule_tailors_shop`
(the Steward asks for it in the `market` zone), `luxuryWorkshopsAreInTheBlueprintTable` (in the Table, a starter
blueprint, a workshop on the Village Map, named); `LuxuryWorkshopsGameTests.journeymenSellTheirBuildingsBlueprint`,
`QaLuxuryWorkshopsGameTests.theJourneymansBlueprintIsTheTablesOwnAndReadsInEnglish`, `eachStewardRuleHoldsForItsOwnJobOnly`;
`ExpansionBuildsGameTests.laterExpansionsBuildsWaitForTheirSwitch`; `TablePreviewGameTests`; `QaTableJobsGameTests`
(the Table's jobs and beds lines are computed from the drawing);
`ModdedLibraryGameTests` (a modded copy exists, vanilla without the mods).

Library-wide checks it must pass: `B648GameTests` and `QaWindowsAndWards1028BugTests` / `QaBuilders1028GameTests` (no
pane or bar joined on one side with air on the other), `QaFurnishedRooms1031GameTests`, `FurnishedRoomsGameTests`,
`BlueprintsStayGameTests` (lamps on a block that holds them; no dirt path or farmland under a solid block; beds whole),
`QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`, `QaBuildReachGameTests`, `QaMirroredBuildsGameTests`,
`QaTurnedFramesGameTests`, `QaLedgerFramesHaulGameTests`, `FurnitureSpotGameTests`, `FloatingFurnishingsGameTests`,
`QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`,
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `StyleGameTests.chimneysStayWholeInEveryStyle`
(only if a campfire stands on a solid stack).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair, no bed right inside a door; `open_panes`), STYLE.md's checks and `holes.py`:
`rebuild.py --candidate` prints no CHECK line. Today `owner_rules_baseline.json` excuses one finding on
`tailors_shop_2` and one on `modded/tailors_shop_2`; the new drawings must come out clean, and the ship step removes
those entries. The ship step also keeps the old drawings in `blueprint_history/` (`history.py`; one older drawing of
each is already there) so a site begun on them finishes as drawn, and regenerates the modded copies, the village
variants and the furniture spots.

## Free
Everything not listed above: shape, plan, roof, materials and colors (beyond wool blocks of six or more colors for the
bolts), how many storeys and rooms, where the loom, chests, wool, ladder and door stand (within the rules above), how
upper floors are reached in I (stairs, not ladders), beds (none needed), chests and furniture beyond the minimums, a
shop front or display or none, lighting (within the light rules), and the village variant's look outside its room in
each of the five styles.
