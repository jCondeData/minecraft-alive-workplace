# Trade Hall: the brief

Family id(s): `trade_hall` (one level, never renamed; "Trade Hall" in `en_us.json`, `blueprint.aliveworkplace.trade_hall`
and `village_piece.aliveworkplace.trade_hall`). Variants: village (`village/<style>_trade_hall` for `plains`, `desert`,
`savanna`, `snowy`, `taiga`, saved by `generate.py` through `village.py`'s `staffed_house` with the job
`aliveworkplace:pokemon_trader`; see "The village variant"), modded (none), looks (none). The Table's style buttons
restyle the drawing automatically.

## What it is for
The Pokémon Trader's workplace (Cobblemon only). The Trader (`ModVillagers.POKEMON_TRADER`) works at a **Shop Counter**
(`aliveworkplace:shop_counter`; the Trade Board is his other job site) and players trade Pokémon with him there
(`trader/PokemonTraders`). The building gives him that counter and a bed. It is in the Blueprint Table only with
Cobblemon (`StarterBlueprints.COBBLEMON_WORKPLACES`); the Steward builds it in a `market` zone when a Trader is wanted
with no workstation and Cobblemon is loaded (`steward_rules/workplace_trade_hall.json`, priority 62, cooldown 2 days),
and renews an old Trader's house as this one (`steward_renewal/pokemon_trader.json`). Villages with Cobblemon grow the
village variant (`VillageHouses.COBBLEMON_HOUSES`, weight 2) with a Trader already in the job. Advancement
`built/trade_hall` by id.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `trade_hall` | One Trader's Shop Counter and his bed | Table with Cobblemon; Steward rule `workplace_trade_hall`; renewal `pokemon_trader.json` |

**One level only**: a `trade_hall_2` would be a new feature and change the count in
`BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (exactly 64).

## Must have
- **Job block: exactly one `aliveworkplace:shop_counter`** in the file and in the built box
  (`WorkplacesGameTests.workplaceBuilt_trade_hall`: "hasn't one Shop Counter", "job blocks built"). **No Trade Board**
  (the Trader's other job site) and **no other job block of any job**, vanilla or the mod's (`workplaces.py` contract;
  the Shop Counter is also the Shopkeeper's and Innkeeper's, so a second one draws them in).
- **The counter is reachable on foot from outside** within a player's reach of a walkable cell
  (`QaBuildReachGameTests`), and a Trader waiting in front walks in and claims it as his `JOB_SITE`
  (`workplaceBuilt_trade_hall`).
- **Beds and residents: at least one bed**, the Trader's (`workplaces.py`: "a bed for the worker"; it counts as a village
  bed, `StewardConditions.beds`). Head reachable on foot, head one block toward its facing
  (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`), not right inside a doorway (`check.py`).
- **Storage: none required** (no code reads the Trader's chests). Any chest drawn: empty, reachable, against a wall or
  solid block (`QaBuildReachGameTests`, `QaFurnishedRooms1031GameTests`); no barrel (the Fisherman's job block).
- **Furniture spots:** none required by a test (not a home). Each spot the drawing has must match its template
  (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); nothing small on air (`FloatingFurnishingsGameTests`);
  chair stairs face their table (`QaChairFacingGameTests`).
- **A way in on the z = 0 side**, ground floor, that a 1.95-tall villager walks through (`QaDoorwayClearanceGameTests`;
  B170: no carpet just inside a 2-tall doorway). Any upper floor with the bed, a chest or the counter is reached by ladder
  or stairs from inside (B149).
- **No trapdoor at z = 1, y 0 or 1** (`VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow` reads
  `structure/trade_hall.nbt`).
- **No calcite, jigsaw, structure void or entity** in the file (`workplaceBuilt_trade_hall`: "comes with a villager").
- Every block placeable by a builder (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).

## Fits
- **Plot: at most 18 wide x 28 tall x 20 deep.** `workplaceBuilt_trade_hall` builds it in `huge_area` at helper
  (12, 2, 10) (x 12..29, z 10..29), its bench and chests at x 3 to the west. y = 0 sits on the clicked block; the front
  is the z = 0 side.
- **Material budget: at most 81 stacks** (three chests of 27, "needs N stacks"). Today 496 blocks.
- **Build time: within 12,000 ticks**, build plus the Trader walking in and claiming the counter (build delay 2).
- **Declared size:** `StarterBlueprints.TRADE_HALL` is `workplace("trade_hall", 11, 17, 11)`; the ship step updates it.
- **Front and way in:** the Trader waits at helper (18, 2, 7) = template **(6, 0, -3)** and must walk in by the front.
- **Neighbours:** Steward `market` zone, 2 blocks kept clear round the plot (`Plots.CLEAR`), ground within 4 of level
  (`Plots.MAX_STEP`); a renewal needs the old house's footprint plus 3 a side (`Renewals.MARGIN`), so smaller renews more.

## The village variant (`village/<style>_trade_hall`, five styles)
Shared contract (`village.py` `village_house`, `world/VillagePieces`; the designer draws only the look,
`village_looks.py` `DESIGNS["trade_hall"]`, today 13 x 17 x 11):
- **Street joint:** `minecraft:jigsaw` at **(4, 1, 0)**, `north_up`, `building_entrance`, final state structure_void; the
  whole row z = 0 at y = 0 places nothing (`VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`).
- **Front door at (4, 1, 2)**, step at (4, 0, 1); no trapdoor at z = 1, y 0-1.
- **The room:** x 2-6, z 3-7, y up to 4 (`VillagePieces.ROOM_*`): kept on a hall look change; walls at x 1, x 7, z 2, z 8.
- **Bed foot (6, 1, 6), head (6, 1, 7), facing south** (`VillagePieces.find` reference).
- **Fit-out** (`village.py` `trade_hall`, moved in by one): the Shop Counter at **(2, 1, 7)** facing east, a stair seat at
  (2, 1, 5), a potted fern at (2, 1, 4). **Exactly one job block in the whole template**
  (`VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`: one Shop Counter, "has N job blocks").
- **The villager:** one, already a Pokémon Trader, at **(5, 1, 5)**; (5, 1, 5) and (5, 2, 5) clear air over a floor
  block at (4, 0, 5) (else he suffocates); no worker-pool jigsaw.
- Listed by id in the pools: `everyVillageTypeCanGrowTheOtherHouses`,
  `VillageCompatTests.repurposedStructuresVillagesGetOurHouses`.

## Rules tests check
- `WorkplacesGameTests.workplaceBuilt_trade_hall` (HOUSE (12, 2, 10), WORKER (18, 2, 7), BENCH (3, 2, 3), CHESTS x 3):
  built from its supply report; exactly one Shop Counter built; the Trader holds it as `JOB_SITE`, still a Trader.
- `VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow`, `everyVillageTypeCanGrowTheOtherHouses`,
  `everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`.
- By id only: `WorkplacesGameTests.workplaceRule_trade_hall_pokemon_trader` (Cobblemon only),
  `QaWorkplaceCopiesGameTests` ("Trade Hall"), `PieceLooksGameTests.everyLookSentenceReads`.
- Library-wide: `QaBuildReachGameTests`, `QaDoorwayClearanceGameTests`, `BlueprintsStayGameTests`,
  `QaFurnishedRooms1031GameTests` (no standing lantern on a floor), `QaChairFacingGameTests`,
  `FloatingFurnishingsGameTests`, `FurnitureSpotGameTests`, `B648GameTests` / `QaWindowsAndWards1028BugTests` /
  `QaBuilders1028GameTests`, `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`,
  `StyleGameTests.chimneysStayWholeInEveryStyle`.
- The owner's rules (`check.py`), STYLE.md, `holes.py`; `rebuild.py --candidate` prints no CHECK line. The ship step keeps
  the old drawings in `blueprint_history/` and regenerates the village variants and `blueprint_furniture/trade_hall.json`.

## Free
Everything else: shape, plan, roof, materials, colors, storeys, where the counter, bed and chests stand (within the
rules), extra beds and chests, an open trading floor or none, and the village variant's look outside its room.
