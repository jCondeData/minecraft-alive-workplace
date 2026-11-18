# Winery: the brief

Family id(s): `winery`, `winery_2`, `winery_3` (never renamed; "Winery", "Winery II", "Winery III" in `en_us.json`,
`blueprint.aliveworkplace.winery*`; the village house is `village_piece.aliveworkplace.winery`, "Winery")
Variants: village (`village/<style>_winery` for the five village styles `plains`, `desert`, `savanna`, `snowy`,
`taiga`, saved by `generate.py` through `village.py`'s `village_house` with the fit-out `winery_room` and the job
`aliveworkplace:vintner`; Repurposed Structures' villages reuse these five, `VillageHouses.MODDED_HOUSE_POOLS`; the
village variant has its own contract, see "The village variant"), modded (none to draw: `modded_library.py` makes
`structure/modded/winery*.nbt` from the vanilla drawings, turning chests and ladders into Carved Wood ones, and the ship
step reruns it), looks (none: the Table's style buttons, `BlueprintStyles`, restyle the drawing automatically).

## What it is for
The **Vintner**'s workplace (`ModVillagers.VINTNER`, config `vintners`, part of Milestone 34's Classes and luxuries;
open to Peasants). The Vintner's one job block is a **cauldron**, the vat: a villager is made a Vintner by handing him
sweet berries, glow berries or an apple at a cauldron (`work/Stations`; a jobless villager who finds a cauldron by
himself becomes a vanilla Leatherworker instead). At the vat he makes the luxury drinks (cider, berry wine, vintage wine:
`vintner/VintnerWork`, the shared `craft/LuxuryWork` engine) for the village store, taking makings from **his own
chests, the containers within 8 blocks across and 4 up or down of the vat** (`SupplyContainers.find(level, station)`,
`RADIUS` 8, `VERTICAL` 4), from the village store and stashes, and putting what he makes back into those chests. **With
no container within that reach he makes nothing at all** (`LuxuryWork.choose`: "nowhere to put what's made"). Goods age
in those chests ("Pressed on day N"). Pokemon of the grass, bug and fairy types help him (`Partners`). The Winery is
not in a new village's starting set: a **Journeyman Vintner sells its blueprint** (`ModTrades.journeymanBlueprint`,
`BlueprintUnlocks.VILLAGER_SALES`), and a builder who finished one level sells the next (`UpgradeOffers`). The
**Steward** builds it by himself in a `workshops` zone when a Vintner is wanted and has no workstation
(`steward_rules/workplace_winery.json`, priority 62, cooldown 2 days, Hamlet and up). The Village Map shows it as a
workshop (`VillageMaps.kindOf`). All three levels are hidden from the Table while the `vintners` switch / Milestone 34
gate is closed (`StarterBlueprints.expansionSwitch`). Finishing each level grants the hidden advancement
`built/winery`, `built/winery_2`, `built/winery_3`. In world generation, the five village styles grow the village
variant (pool weight 2, `VillageHouses.houses()`, only while `vintners` is on, `VillageHouses.LUXURY_HOUSES`) with a
Vintner already in the job and a chest of fruit and bottles (loot table `chests/village_winery`).

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `winery` | The vat (one cauldron), the Vintner's chests within reach of it, a storey of chests and casks below the vat (test-pinned, see Must have) | Sold by a Journeyman Vintner; Steward rule `workplace_winery` (workshops zone, Hamlet+) |
| II | `winery_2` | A garden of fruit: at least 16 sweet berry bushes and at least 6 glow-berry vines bearing berries (test-pinned); still one cauldron | Upgrade of I (`BlueprintUpgrades`: id + `_2`); sold by the builder who finished I |
| III | `winery_3` | A **cellar exactly 4 deep** under II, reached from inside; above the ground it is II, block for block | Upgrade of II; in `StarterBlueprints.WITH_BASEMENTS` and `LEVEL_3_CELLARS`; sold by the builder who finished II |

No fourth level and no other id: `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` counts exactly 64 upgrades
library-wide and `CellarGameTests.levelThreesLineUpOverTheirLevelTwos` exactly 8 cellared Level IIIs.

## Must have (each level)
- **Job block: exactly one, and it is a plain `minecraft:cauldron`** (empty, not a water, lava or powder-snow cauldron)
  in every level, I, II and III (`QaLuxuryWorkshopsGameTests.everyTierHasOneJobBlockAndItIsItsWorkersOwn`: exactly one
  block in the whole file that *any* job takes as its job site, and it is the cauldron a Vintner takes;
  `LuxuryWorkshopsGameTests.luxuryWorkshopBuilt_winery` / `_winery_2`: exactly one `CAULDRON` and none of blast furnace,
  smithing table, grindstone, stonecutter, fletching table, cartography table, barrel, lectern, composter, smoker,
  brewing stand, loom, crafting table). So **no barrel anywhere** (a Fisherman's job block;
  `QaLuxuryWorkshopsGameTests.noWineryHasABarrelAndNoPrintShopALectern`, and the `luxury_workshops` scene checks it
  too), no second cauldron of any kind, no lectern or other workstation for looks, and no job block of this mod
  (Builder's Bench, Shop Counter, ...). A chest, furnace, campfire or bed is not a job block.
- **The vat stands at template y 3 or higher** (`LuxuryWorkshopsGameTests.theBuildsHaveWhatTheirTradesNeed`, I and II:
  `vat >= 3`), and **below it, at y lower than the vat's y - 1** (two or more blocks under the vat's own layer):
  - **at least 4 `minecraft:chest`** (same test: "the cellar has N chests under a vat at y ..."), and
  - **at least 12 casks: `minecraft:spruce_log` blocks laid on their side** (axis x or z, not y) (same test: "only N
    casks down in the cellar").
  This is a block rule a test checks, not a look: the casks must be spruce logs because a barrel would be a job block.
  How the lower storey is reached, lit and shaped is free.
- **The Vintner's chests within reach of the vat:** at least one chest within **8 across and 4 up or down** of the
  cauldron (`SupplyContainers.RADIUS` / `VERTICAL`). No test checks this, but without it the Vintner makes nothing.
  Chests 2 to 4 below the vat meet this and the rule above at once. Drawn empty (builders never fill or copy
  containers; no loot table in the buildable).
- **The vat is reached on foot from the street in front.** A Vintner standing 3 blocks before the front edge walks in
  (up to the vat's floor, which is at least 3 above the ground) and claims the cauldron as his job site
  (`luxuryWorkshopBuilt_winery`, `_winery_2`; see "Rules tests check"). The cauldron, every chest and every bed head
  are within a player's reach (2 across, 1 up or down) of a cell walked to from the ground outside, by levels, one-block
  steps up, drops of up to 3, ladders, doors and trapdoors (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`).
  Every wooden doorway has room for a villager 1.95 blocks tall (`QaDoorwayClearanceGameTests`).
- **Beds and residents:** no bed is required (no code counts the Winery's beds and the old drawing has none). Any bed
  drawn counts as a village bed for the Steward and shows on the Table as "Beds: N"; its head must be reachable on foot
  and its foot has its head one block towards its facing (`BlueprintsStayGameTests`); no bed right inside a doorway
  (`check.py`).
- **Storage:** chests only (at least the 4 under the vat, above); **no barrel**. Every chest stands against a wall, a
  solid block, a bed or another chest (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`). Double
  chests stay whole turned and mirrored (`QaMirroredBuildsGameTests`, B438; the old Winery was hit by this).
- **Level II's garden** (`theBuildsHaveWhatTheirTradesNeed`): **Level I has no `minecraft:sweet_berry_bush`; Level II has
  at least 16 of them** (any age), **and at least 6 `minecraft:cave_vines` with `berries=true`** (glow berries; plain
  `cave_vines_plant` cells don't count). Each must be able to survive where it stands (a bush on dirt, grass or similar;
  a vine hanging from a block or another vine), since the build is pasted and updated.
- **Furniture spots:** no kind is required by any test (the Winery is not a home: `FurnishedHomesGameTests` does not
  list it). Every furniture piece drawn is recorded as a spot (`rebuild.py`); each spot's blocks are exactly the
  template's blocks there (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); nothing small stands on air
  (`FloatingFurnishingsGameTests`); every chair faces its table, turned and mirrored too (`QaChairFacingGameTests`);
  every piece on a furnished room's floor has block light above 7 from that room's own lantern, hung or on a table
  (`FurnishedRoomsGameTests.everyFurnishedRoomHasALight`); no standing lantern on a room's floor
  (`QaFurnishedRooms1031GameTests.noLanternStandsOnARoomsFloor`).
- **No entity** (villager, frame, painting, armor stand) in any level's file (`luxuryWorkshopBuilt_*`: "comes with a
  villager"); no jigsaw or structure void in the buildables.
- Every block is one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).
- **Winery III's cellar** (`BasementGameTests.basementsLoadUnderTheGround`, `QaBasementGameTests`, `CellarGameTests`,
  `QaBlueprintTableGameTests.everyBasementBuildPreviewsItsWholeDepth`): drawn at the bottom of the file, its floor at
  file y 0 and Level II's y 0 at file y 4: **exactly 4 deep** (the file is II's height + 4; the declared size stays
  II's above-ground size with basement 4); at least one **`minecraft:lantern`** below the ground (that block, not
  another light); at least one ladder or stairs on the cellar's top layer (y -1) with passable cells above it at ground
  y 0 and y 1; **a trapdoor hatch at ground y 0 over a cellar column**; no dark cell (block light above 0 on every
  standable cell down there); walkable from the ground outside down to the cellar floor and back out; every chest down
  there within 2 across / 1 up or down of a walked cell. **No job block in the cellar** (the one-job-block rule above).

## Kept cells (every upgrade)
- II has the same origin and front as I and is at least its size in x, y and z; **at least 60 % of I's non-air blocks
  stay the same block in the same cell** (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`). Levels grow out of
  each other (REBUILD.md): II adds its garden to I's building.
- **III above the ground is II exactly**, the same size: the only allowed change is a trapdoor at y 0 over a cellar
  column (`CellarGameTests.levelThreesLineUpOverTheirLevelTwos`). So the spot for the hatch, with clear room above it
  and away from the vat and the chests, is already in II.
- II has **no basement** (`QaBenchMovesCellars1104bGameTests.aBuildWithNoBasementIsNeverOfferedACellar`).
- Every chest stays in the same cell as the same block in every later level (builders never break containers or copy
  their contents, `MaterialRules.isProtected`), and the vat stays where it is (one cauldron per level).

## Fits
- **Plot (I and II): at most 18 wide x 18 tall x 22 deep** above the ground. `luxuryWorkshopBuilt_winery` and
  `_winery_2` (`CraftWorkplacesGameTests.build`) place the build at helper (12, 2, 8) in the 30 x 20 x 30 `huge_area`,
  so x 12..29, y 2..19, z 8..29; the builder's bench and its three chests stand at x 3, z 3-10, west of it.
- **Plot (III):** II's footprint and height, plus the 4-deep cellar under it.
- y = 0 sits on the clicked block; the front is the z = 0 side.
- **Declared sizes:** `StarterBlueprints.WINERY` (`13, 14, 12`), `WINERY_2` (`13, 14, 20`), `WINERY_3`
  (`13, 14, 20`, basement 4) today; if a size changes the ship step changes those `Vec3i`s
  (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`, `luxuryWorkshopBuilt_*`: "is <size>"), and
  `modded_library.py` reads the sizes from that file.
- **Material budget: at most 81 stacks each for I and for II**, each built from bare ground (three chests of 27 stocked
  with exactly what the supply report says is missing, each item rounded up to whole stacks).
- **Build time:** `luxuryWorkshopBuilt_winery` and `_winery_2`: the whole build (build delay 2, builders' help off)
  and then the Vintner walking in and claiming the vat, within **12,000 ticks**. The old drawings are 701 (I) and 1,085
  (II) blocks; a much bigger build risks the timeout.
- **Front and way in:** the z = 0 side faces the player who places it and the road of a Steward plot. The arriving
  Vintner is spawned at helper (13, 2, 5) = template **(1, 0, -3)**, three blocks before the front edge, and must find
  his way in from there.
- **Neighbours:** a workplace in a Steward `workshops` zone; the Steward keeps 2 blocks clear round its plot
  (`Plots.CLEAR`) and wants the ground under it within 4 blocks of level (`Plots.MAX_STEP`). No party walls.

## The village variant (`village/<style>_winery`, five styles)
Village houses share one contract that the Java reads by template coordinate (`world/VillagePieces`,
`world/VillageHouses`, `village.py`'s `village_house`, `village_looks.py` header). The designer draws only the look
(`village_looks.py` `DESIGNS["winery"]`, today 10 x 11 x 11); `village.py` adds the jigsaw, fit-out, bed and villager.
Every style keeps exactly:
- **At most 10 wide** (`VillageGameTests.theWinerysVintnerTakesItsVat` places the plains and the desert house 11 apart,
  at helper (1, 1, 4) and (12, 1, 4) in the 22 x 18 x 22 `big_area`; so also at most 18 deep and 17 tall).
- **The street joint:** a `minecraft:jigsaw` at **(4, 1, 0)**, orientation `north_up`, name and target
  `minecraft:building_entrance`, final state `minecraft:structure_void`; **the front row z = 0 at y = 0 places nothing**
  (x 0-8 stay the ground the house landed on), and placing it as a village does leaves **no structure_void block in the
  world** (`VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`).
- **The front door at (4, 1, 2)** (lower half facing south), a step before it at (4, 0, 1); **no trapdoor at z = 1,
  y 0-1** (`VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow`).
- **The room:** template **x 2-6, z 3-7, y up to 4** (`VillagePieces.ROOM_MIN_X ... ROOM_MAX_Y`), bounded by the west
  wall at x = 1: a house is recognised by its room, and a hall look change rebuilds everything outside the room.
- **The bed: foot (6, 1, 6), head (6, 1, 7), facing south** (`VillagePieces.find` uses this cell to find every house).
- **The fit-out** (`village.py`'s `winery_room`, moved in by one): exactly one **cauldron at (2, 1, 7)**, a chest at
  **(2, 1, 6)** with `LootTable` `aliveworkplace:chests/village_winery`, and spruce-log casks at (2, 1, 4), (2, 2, 4) and
  (2, 1, 3). **Exactly one job block in the whole template** and **no barrel or lectern** anywhere in it
  (`VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`: "should have one Cauldron", "has N job blocks";
  `QaLuxuryWorkshopsGameTests.everyStylesPieceHasItsBlockAndAWorkerWhoTakesIt`): the look outside the room may add no
  workstation.
- **The villager:** **exactly one entity, a villager already a Vintner**, at **(5, 1, 5)**, and no worker-pool spawn
  jigsaw; (4, 0, 5) is the style's floor block. Keep (5, 1, 5) and (5, 2, 5) clear air with floor under it.
  `VillageGameTests.theWinerysVintnerTakesItsVat`: placed as a village places it, his job site is that cauldron and he
  keeps the job.
- Every pool lists it at weight 2 while `vintners` is on (`QaLuxuryWorkshopsGameTests.eachVillagePieceWeighsTwoInEveryStyle`,
  `LuxuryWorkshopsGameTests.villagesGrowTheHousesOnlyWhileTheirJobsAreOn`), by id.

## Rules tests check
Pinned coordinates and counts (the ship step moves coordinates to the new drawing, keeping what each checks, dropping
none):
- `LuxuryWorkshopsGameTests.theBuildsHaveWhatTheirTradesNeed` (I and II): vat y >= 3; >= 4 chests and >= 12 horizontal
  spruce logs at y < vat y - 1; no barrel; I has 0 and II >= 16 sweet berry bushes; II >= 6 cave vines with berries.
- `LuxuryWorkshopsGameTests.luxuryWorkshopBuilt_winery`, `luxuryWorkshopBuilt_winery_2` (`CraftWorkplacesGameTests.build`,
  `HOUSE` (12, 2, 8), `BENCH` (3, 2, 3), `CHESTS` (3, 2, 6/8/10), worker at (13, 2, 5)): size matches the declared
  size; no entity; exactly one cauldron and none of the other workstations listed above; at most 81 stacks; built by a
  builder; then the Vintner from the street holds a cauldron inside the build's box as his `JOB_SITE` and is still a
  Vintner, within 12,000 ticks.
- `QaLuxuryWorkshopsGameTests.everyTierHasOneJobBlockAndItIsItsWorkersOwn`, `noWineryHasABarrelAndNoPrintShopALectern`
  (I, II, III).
- `CellarGameTests.levelThreesLineUpOverTheirLevelTwos`, `BasementGameTests.basementsLoadUnderTheGround`,
  `QaBasementGameTests` (all three), `QaBlueprintTableGameTests.everyBasementBuildPreviewsItsWholeDepth` (III).
- `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (II over I, III over II),
  `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`.
- Village variant: `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`, `everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`,
  `noVillageHouseHasATrapdoorInItsFrontWalkRow`, `theWinerysVintnerTakesItsVat`;
  `QaLuxuryWorkshopsGameTests.everyStylesPieceHasItsBlockAndAWorkerWhoTakesIt`, `eachVillagePieceWeighsTwoInEveryStyle`;
  `VillageCompatTests.repurposedStructuresVillagesGetOurHouses`.
- Not a test, but pinned: the showcase scene `luxury_workshops` (`devclient/LuxuryWorkshopsScene.PIECES`) expects the
  cauldron at template (7, 4, 4) in I and II and at (2, 1, 7) in the plains village house; the ship step moves the
  first to the new vat.

By id only (the drawing doesn't matter beyond the rules above): `LuxuryWorkshopsGameTests.luxuryWorkshopRule_winery`,
`luxuryWorkshopsAreInTheBlueprintTable` (in the Table, a starter blueprint, a workshop on the Village Map, named);
`QaLuxuryWorkshopsGameTests.theJourneymansBlueprintIsTheTablesOwnAndReadsInEnglish`, `eachStewardRuleHoldsForItsOwnJobOnly`;
`ExpansionBuildsGameTests.laterExpansionsBuildsWaitForTheirSwitch`; `TablePreviewGameTests` (the Winery previews with
its expansion open); `QaTableJobsGameTests` (the Table's jobs and beds lines are computed from the drawing);
`ModdedLibraryGameTests` (a modded copy exists, vanilla without the mods).

Library-wide checks it must pass: `B648GameTests` and `QaWindowsAndWards1028BugTests` / `QaBuilders1028GameTests` (no
pane or bar joined on one side with air on the other), `QaFurnishedRooms1031GameTests`, `FurnishedRoomsGameTests`,
`BlueprintsStayGameTests`, `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`, `QaBuildReachGameTests`,
`QaMirroredBuildsGameTests`, `QaLedgerFramesHaulGameTests`, `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`,
`StyleGameTests.chimneysStayWholeInEveryStyle` (only if a campfire stands on a solid stack).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair, no bed right inside a door; `open_panes`), STYLE.md's checks and `holes.py`:
`rebuild.py --candidate` prints no CHECK line. The ship step removes `winery*` and `modded/winery*` from
`owner_rules_baseline.json`, keeps the old drawings in `blueprint_history/` (`history.py`), and regenerates the modded
copies, the village variants and the furniture spots (`blueprint_furniture/winery*.json`, `village/<style>_winery.json`).

## Free
Everything not listed above: shape, plan, roof, materials (beyond the spruce-log casks and the plain cauldron the tests
name), colors, how many storeys and rooms, how the lower storey with the chests and casks sits against the ground, how
the vat is reached from the street, where the garden of Level II goes and how it is laid out, where Level III's cellar
and hatch go, beds (none needed), chests and furniture beyond the minimums, lighting (within the light rules), and the
village variant's look outside its room in each of the five styles.
