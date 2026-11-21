# Flower Shop: the brief

Family id(s): `flower_shop`, `flower_shop_2` (never renamed; "Flower Shop", "Flower Shop II" in `en_us.json`,
`blueprint.aliveworkplace.flower_shop*`; the village house is `village_piece.aliveworkplace.flower_shop`, "Flower Shop";
hidden advancements `built/flower_shop`, `built/flower_shop_2`)
Variants: village (`village/<style>_flower_shop` for the five village styles `plains`, `desert`, `savanna`, `snowy`,
`taiga`, saved by `generate.py` through `village.py`'s `village_house` with the fit-out `flower_shop` and the job
`aliveworkplace:florist`; Repurposed Structures' villages reuse these five, `VillageHouses.MODDED_HOUSE_POOLS`; the
village variant has its own contract, see "The village variant"), modded (none to draw: `modded_library.py` makes
`structure/modded/flower_shop*.nbt` from the vanilla drawings and the ship step reruns it), looks (none: the Table's
style buttons, `BlueprintStyles`, restyle the drawing automatically).

## What it is for
The **Florist**'s workplace (`ModVillagers.FLORIST`). Her job block in a shipped build is a **composter**
(`PoiTypes.FARMER`, a block several jobs share, `work/Stations`): a villager is made a Florist by handing her **any
flower** (`#minecraft:flowers`) at a composter, and the Steward gives a free composter **inside a finished Flower Shop**
to a Florist while the building has none (`StewardJobs.BUILDING_JOBS`: `"flower_shop"` -> Florist; the building's box is
its blueprint box plus `BUILDING_MARGIN` 2). Her own Flower Stand block was retired in 21.1a and can't be crafted
(`StationsGameTests.theReplacedBlocksCantBeCrafted`), so a builder could never get one: don't draw it. Her shift
(`flower/FloristWork`): **with no container within 8 blocks across and 4 up or down of her job block she does nothing
at all** (`SupplyContainers.find`, `RADIUS` 8, `VERTICAL` 4; status "no_chest"). Otherwise she keeps a **garden round
her job block, 5 blocks across and 2 up or down** (`FloristWork.GARDEN`): bone meal from her chests on **grass blocks
with air above** brings up flowers (and grass, which she weeds), bone meal on a tall flower gives another; she **picks
every small flower, short grass and fern standing in that garden** into her chests (up to 64 flowers kept), and fills
**every empty flower pot within 24 blocks across and 6 up or down** of her job block with a flower. Between those she
makes flower pots, decorated pots, dyes and moss carpet for builders at the same block (`craft/FloristCraftWork`, 46.3).
The **Steward** builds Level I by himself in a **`market`** zone when a Florist is wanted and has no workstation
(`steward_rules/workplace_flower_shop.json`, priority 62, cooldown 2 days, Hamlet and up), and it is the Florist's house
the Steward renews to (`steward_renewal/florist.json`: the first building that fits). The Village Map shows it as a farm
(`VillageMaps.kindOf`, by name: `Kind.FARMS`). It belongs to **no expansion switch** (always in the Blueprint Table;
not in `StarterBlueprints.EXPANSION_SWITCH` or `SOLD_ONLY`). In world generation the five village styles grow the
village variant (pool weight 2, `VillageHouses.houses()`, always on) with a Florist already in the job and a chest of
bone meal, flowers, a flower pot and seeds (loot table `chests/village_flower_shop`).

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `flower_shop` | The composter (her job block); a chest within reach of it; whatever garden the design gives her | In the Blueprint Table from the start (`StarterBlueprints.ALL`); Steward rule `workplace_flower_shop` (market zone, Hamlet+) |
| II | `flower_shop_2` | Nothing the code counts beyond Level I: still exactly one composter; a larger room or garden is the design's choice | Upgrade of I (`BlueprintUpgrades`: id + `_2`); sold by the builder who finished I (`UpgradeOffers`, price by size) |

No third level and no other id: `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` counts exactly 64 upgrades
library-wide, and Level I must have its upgrade.

## Must have (each level)
- **Job block: exactly one, a `minecraft:composter`**, in I and in II, inside the build. No other block any job takes
  as its job site anywhere in the file: no second composter (a second one would go to a Farmer, an Orchard Keeper or the
  Composter job), no Flower Stand, no barrel, lectern, loom, smoker, cauldron, crafting table or other workstation for
  looks, and no job block of this mod. (No buildable-level test counts this today; the village variant's test does, see
  below, and `StewardJobs.buildingJob` hands out every free shared block inside the box to the Florist while she has
  none.) A chest, bed, flower pot or furnace is not a job block.
- **Her chest within reach of the composter:** at least one `minecraft:chest` within **8 across and 4 up or down** of
  the composter (`SupplyContainers.RADIUS` / `VERTICAL`). No test checks this, but without it she grows, picks, pots and
  makes nothing. Chests are drawn empty (builders never fill or copy containers).
- **Her garden (if the building is to be her garden):** grass blocks (`minecraft:grass_block`) with **air directly above**
  inside the box 5 across and 2 up or down of the composter are the only cells she can grow flowers on
  (`FloristWork.growable`); she walks to each (reach 3). No test checks it; a shop with no such grass works, but she
  grows nothing there. A builder places grass from dirt (`MaterialRules`: grass_block costs dirt).
- **Small flowers in that 11 x 5 x 11 box are her crop, not decoration:** every small flower (`#minecraft:small_flowers`
  except the wither rose), short grass and fern standing in it is picked and goes into her chest
  (`FloristWork.pickable`). Flowers meant to stay belong in pots (potted flowers are never touched) or outside the box.
  Empty `minecraft:flower_pot`s within 24 of the composter get filled by her over time.
- **The composter is reached on foot from the street in front.** The composter, every chest and every bed head are
  within a player's reach (2 across, 1 up or down) of a cell walked to from the ground outside, by levels, one-block
  steps up, drops of up to 3, ladders, doors and trapdoors (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`).
  If the composter is built in under solid blocks, some side of it needs a floor cell with 2 blocks of head room
  (`work/JobSiteApproach`, `BuiltInJobSiteGameTests`). Every wooden doorway has room for a villager 1.95 blocks tall
  (`QaDoorwayClearanceGameTests`: no carpet just inside a 2-tall doorway).
- **Beds and residents:** none required (no code counts the Flower Shop's beds; the old drawings have none). Any bed
  drawn counts as a village bed and shows on the Table as "Beds: N"; its head must be reachable on foot, it stays whole
  (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`), and no bed right inside a doorway (`check.py`).
- **Storage:** chests (no barrel: a barrel is the Fisherman's job block). Every indoor chest stands against a wall, a
  solid block, a bed or another chest (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`). Double
  chests, doors and beds stay whole turned and mirrored
  (`QaMirroredBuildsGameTests.everyBuildsDoorsBedsAndDoubleChestsStayWholeTurnedAndMirrored`).
- **Furniture spots:** no kind is required by any test (no `blueprint_furniture/flower_shop*.json` today; not a home,
  `FurnishedHomesGameTests` does not list it). Every piece drawn is recorded as a spot by `rebuild.py`; each spot's
  blocks are exactly the template's there (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); nothing small
  stands on air, pots included (`FloatingFurnishingsGameTests`); every chair stair faces its table, turned and mirrored
  (`QaChairFacingGameTests`); every piece on a furnished room's floor has block light above 7 from that room's own
  lantern, hung or on a table (`FurnishedRoomsGameTests.everyFurnishedRoomHasALight`); no standing lantern on a room's
  floor (`QaFurnishedRooms1031GameTests.noLanternStandsOnARoomsFloor`). Frames and paintings, if any, hang turned and
  mirrored (`QaTurnedFramesGameTests`, `QaLedgerFramesHaulGameTests`).
- **No entity**, jigsaw or structure void in either buildable level; no basement (neither level is in
  `StarterBlueprints.WITH_BASEMENTS`).
- Every block is one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`),
  and every cell must still read as built when the builder is DONE (`BuilderGameTests.buildsFlowerShop`'s
  `plan.unfinished` is empty): nothing that changes by itself after it is placed.

## Kept cells (the upgrade)
- II has the same origin and front as I and is **at least I's size in x, y and z**, so it grows only east (+x), back
  (+z) and up; **at least 60 % of I's non-air blocks stay the exact same block state in the same cell**
  (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`). Levels grow out of each other (REBUILD.md).
- Every chest stays in the same cell as the same block in II (builders never break containers or copy their contents),
  and the composter stays where it is (one per level).

## Fits
- **Plot, Level I: at most 13 wide x 16 tall x 13 deep.** `BuilderGameTests.buildsFlowerShop` builds Level I at helper
  (9, 2, 9) in the 22 x 18 x 22 `big_area` (so x 9..21, y 2..17, z 9..21; blocks above the area are never cleared); the
  bench at (2, 2, 2), its chest at (2, 2, 4), the builder at (3, 2, 3) and four stock barrels at (1/3/4/5, 2, 4) stand
  north-west of it.
- **Plot, Level II:** no test places it; it must contain I (above) and fit its declared size. The Steward clears a plot
  only for Level I (2 blocks round it, `Plots.CLEAR`), so II grows over ground the village may have used.
- y = 0 sits on the clicked block; the front is the z = 0 side.
- **Declared sizes:** `StarterBlueprints.FLOWER_SHOP` (`9, 11, 9`) and `FLOWER_SHOP_2` (`15, 11, 9`) today; if a size
  changes the ship step changes those `Vec3i`s (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`), and
  `modded_library.py` reads the sizes from that file.
- **Material budget, Level I: at most 108 stacks** (`buildsFlowerShop` stocks four barrels of 27 with exactly
  `plan.materials()`, each item in whole stacks; "test needs more barrels").
- **Build time, Level I:** `buildsFlowerShop` starts at day time 2,000, build delay 2, builders' help off, no clock held,
  timeout **30,000 ticks**: the builder works to dusk, sleeps, and resumes the next morning, so he has roughly two
  working days (about 16,000 ticks). The old Level I is 300 non-air blocks and II 410; a build of well over 1,000 blocks
  risks the timeout (`chapel.md`). Keep the levels modest, with no roof, barge or eave stair over another roof for the
  builder to get stuck on.
- **Front and way in:** the z = 0 side faces the player who places it and, on a Steward plot, the nearest road
  (`Plots.Reason.FACING`). The way in to the composter starts there.
- **Neighbours:** a workplace in a Steward `market` zone; the Steward keeps 2 blocks clear round its plot
  (`Plots.CLEAR`), the zone 2 blocks round the footprint (`Plots.MARGIN`), and wants the ground under it within 4 blocks
  of level (`Plots.MAX_STEP`). No party walls; no second Flower Shop of the same mirroring within 24 blocks
  (`Plots.SAME_RANGE`).

## The village variant (`village/<style>_flower_shop`, five styles)
Village houses share one contract that the Java reads by template coordinate (`world/VillagePieces`,
`world/VillageHouses`, `village.py`'s `village_house`, the `village_looks.py` header). The designer draws only the look
(`village_looks.py` `DESIGNS["flower_shop"]`, today 13 wide x 11 tall x 11 deep; no width limit is test-pinned for this
house, the 10-wide limit is the luxury houses' only); `village.py` adds the jigsaw, fit-out, bed and villager. Every style
keeps exactly:
- **The street joint:** a `minecraft:jigsaw` at **(4, 1, 0)**, orientation `north_up`, name and target
  `minecraft:building_entrance`, final state `minecraft:structure_void`; **the front row z = 0 at y = 0 places nothing**
  (x 0-8 stay the ground the house landed on), and placing it as a village does leaves **no structure_void block in the
  world** (`VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`).
- **The front door at (4, 1, 2)**, a step before it at (4, 0, 1); **no trapdoor at z = 1, y 0-1**
  (`VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow`).
- **The room:** template **x 2-6, z 3-7, y up to 4** (`VillagePieces.ROOM_MIN_X ... ROOM_MAX_Y`), bounded by the west
  wall at x = 1: a house is recognised by its room, and a hall look change rebuilds everything outside the room.
- **The bed: foot (6, 1, 6), head (6, 1, 7), facing south** (`VillagePieces.find` uses this cell to find every house).
- **The fit-out** (`village.py`'s `flower_shop`, moved in by one): the **composter at (2, 1, 7)**, a chest at
  **(2, 1, 6)** with `LootTable` `aliveworkplace:chests/village_flower_shop`, and potted flowers at (2, 1, 4) and
  (3, 1, 4). **Exactly one composter and exactly one job block in the whole template**
  (`VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`: `House(Blocks.COMPOSTER, ModVillagers.FLORIST)`): the look
  outside the room may add no workstation, and no second composter.
- **The villager:** **exactly one entity, a villager already a Florist**, at **(5, 1, 5)**, and no worker-pool spawn
  jigsaw (`everyVillageTypeCanGrowTheOtherHouses`: "should come with a aliveworkplace:florist"); keep (5, 1, 5) and
  (5, 2, 5) clear air with floor under it.
- A ladder to every furnished upper floor (`village_looks.py` header).
- Every style's pool lists it at weight 2 (`VillageHouses.houses()`), and `VillageHouses.houseNames()` keeps
  `flower_shop` in its place in the list `everyVillageTypeCanGrowTheOtherHouses` pins; by id.
- The village garden follows the same Florist rules as above (grass with air above within 5 x 2 of the composter at
  (2, 1, 7) is what she can grow on; small flowers there get picked).

## Rules tests check
Pinned by the drawing (the ship step moves coordinates to the new drawing, keeping what each checks, dropping none):
- `BuilderGameTests.buildsFlowerShop` (Level I, `big_area` at (9, 2, 9), 30,000 ticks): a builder builds it to DONE from
  exactly its material list in at most 4 x 27 stacks, every cell matching (`plan.unfinished` empty).
- `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (II over I: not smaller, 60 % kept, 64 upgrades in all),
  `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable` (both levels).
- `QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo` (both levels: composter, chests, bed heads).
- Village variant: `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses` (one composter, one job block, one Florist
  entity, no worker jigsaw, in all five styles), `everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`,
  `noVillageHouseHasATrapdoorInItsFrontWalkRow`; `VillageCompatTests.repurposedStructuresVillagesGetOurHouses`.

By id only (the drawing doesn't matter beyond the rules above): `WorkplacesGameTests.workplaceRule_flower_shop_florist`
(the Steward asks for `flower_shop` in the `market` zone at Hamlet for a Florist without a workstation, and the blueprint
loads); `TablePreviewGameTests`; `QaTableJobsGameTests` (the Table's jobs and beds lines are computed from the drawing);
`ModdedLibraryGameTests` (a modded copy exists, vanilla without the mods). Not covered by a test of this building but
read by code: `StewardJobs.BUILDING_JOBS` (the composter inside goes to the Florist), `FloristWork` (chest within 8/4,
garden 5/2, pots within 24/6), `VillageMaps.kindOf` (FARMS), `steward_renewal/florist.json`.

Library-wide checks it must pass: `B648GameTests` and `QaWindowsAndWards1028BugTests` / `QaBuilders1028GameTests` (no
pane or bar joined on one side with air on the other), `QaFurnishedRooms1031GameTests`, `FurnishedRoomsGameTests`,
`BlueprintsStayGameTests` (lamps on a block that holds them; no dirt path or farmland under a solid block; beds whole),
`QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`, `QaBuildReachGameTests`, `QaMirroredBuildsGameTests`,
`QaTurnedFramesGameTests`, `QaLedgerFramesHaulGameTests`, `FurnitureSpotGameTests`, `FloatingFurnishingsGameTests`,
`QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`,
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `StyleGameTests.chimneysStayWholeInEveryStyle`
(only if a campfire stands on a solid stack).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair, no bed right inside a door; `open_panes`), STYLE.md's checks (plants in soil) and
`holes.py`: `rebuild.py --candidate` prints no CHECK line. `owner_rules_baseline.json` has no entry for this family
today; the new drawings must come out clean. The ship step keeps the old drawings in `blueprint_history/` (`history.py`;
none of the buildable levels is there yet, only the village pieces) so a site begun on them finishes as drawn, and
regenerates the modded copies, the village variants and the furniture spots.

## Free
Everything not listed above: shape, plan, roof, materials and colors, how many storeys and rooms, whether it is a shop,
a glasshouse, a yard or all three, where the composter, chests, garden and door stand (within the reach rules above),
how big a garden each level gives her (none is required), beds (none needed), chests and furniture beyond the minimums,
potted flowers and empty pots, lighting (within the light rules), and the village variant's look outside its room in
each of the five styles.
