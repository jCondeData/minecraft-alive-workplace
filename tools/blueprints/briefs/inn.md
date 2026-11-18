# Inn: the brief

Family id(s): `inn`, `inn_2`, `inn_3` (never renamed; names "Inn", "Inn II", "Inn III" in `en_us.json`)
Variants: village (none in this family: the worldgen village piece `inn_room` in `village_looks.py` is a separate
family), modded (none to draw: `modded_library.py` makes `structure/modded/inn*.nbt` from the vanilla drawing and the
ship step reruns it; `QaModdedRefresh1115cCompatTests.redrawnModdedCopiesAreOfferedFromTheirShippedDrawingOnly` needs
the modded Inn buildable with the pack's mods), looks (none: `BlueprintStyles` restyles the drawing; tests use the Inn in
`stonework` and `cherry` and the Inn III in `sandstone`, by id only).

## What it is for
The village's inn. An **Innkeeper** works its **Shop Counter** (a jobless villager near it handed any bed becomes the
Innkeeper, `Stations`: within 4.5 blocks; the Steward gives the job at a Shop Counter standing inside a finished Inn, box
+ 2, as Innkeeper, not Shopkeeper, `StewardJobs.BUILDING_JOBS`). Each morning, while the inn has room (2 guests, more
under the Open Gates edict or the Colossus) and **a free bed within 32 blocks of the counter**, a **traveller** arrives,
spawned on a standable spot within 6 blocks across and 2 up or down of the counter, and claims a free bed
(`Innkeepers.tend`, `arrive`, `standingSpot`, `hasFreeBed`). Players hire travellers for emeralds. **Legends** visit "the
inn" the same way (`LegendGuests`, the Master Architect, Grand Chef, Pokemon Professor). With Cobblecuisine the
Innkeeper serves each guest a drink from the **chests or barrels within 8 across and 4 up or down of the counter**
(`InnDrinks`, `SupplyContainers.RADIUS`), else from the village store. While a building is being moved, its residents
sleep in free beds round the inn's counter (`MoveResidents`). A finished Inn (any level, any style) gives homes within 48
blocks the **tavern** service (`services/tavern.json`). The Steward builds one in a **market** zone when an Innkeeper has
no workstation (`steward_rules/workplace_inn.json`, Hamlet and up) or when a Town is **4 or more beds short**
(`homes_inn.json`, at most one); the village map counts it as a home (`VillageMaps`). It starts locked on the Blueprint
Table (not in `starting.json`). Its rooms are furnished (37.4): with a furniture mod each spot becomes that mod's
piece, without one the vanilla stand-in. Each level is an upgrade on the same spot, origin and front.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `inn` | The working inn: the Shop Counter with a walk-in space behind it, six guest beds, the furnished rooms | Blueprint Table once unlocked; Steward rules `workplace_inn` (market zone, Hamlet+) and `homes_inn` (Town+, 4 beds short) |
| II | `inn_2` | Nothing the code reads (the old one added a side outbuilding); still exactly six beds, one counter, no basement | Upgrade of I (`BlueprintUpgrades`: id + `_2`); sold by the builder who finished I (`UpgradeOffers`) |
| III | `inn_3` | A **cellar exactly 4 deep** under II, reached from inside; above the ground it is II, block for block | Upgrade of II; a builder who finished II sells it (`CellarGameTests.levelThreesLineUpOverTheirLevelTwos`) |

## Must have (each level)
- **Job block: exactly one `aliveworkplace:shop_counter`**, and no old `aliveworkplace:inn_counter` (it can't be crafted).
  No other job block is read by any inn code; any other Shop Counter inside the box would also become an Innkeeper's.
  The counter is the bar's working face (all asserted by `QaInnRedrawn1111cGameTests.theInnsDrawingsHangNothingInTheAir`
  for I, II and III):
  - it faces **west** (its front into the room, the bar on the east side of the room);
  - the cell **behind it (east) is air two high**: the walk-in space where the Innkeeper stands;
  - the cell **in front of it (west) is air or a carpet**;
  - the cells **north and south of it are `minecraft:dark_oak_planks`** (the bar either side of it).
  These four are pinned to the old drawing by that test; meet them, or say in `plan.mjs` which one your design changes
  and why, so the ship step can re-anchor it (it keeps what each checks, drops none).
- **Beds and residents: exactly 6 bed heads in every level** (same test; and `theBuiltInnsSixBedsFillWithTravellers`:
  six mornings fill six different beds, the seventh says `no_bed`). **All six are on one guest floor whose y is the
  Shop Counter's y + 5** (`theBuiltInnsSixBedsFillWithTravellers` and `theBuiltInnIIIKeepsItsBarAndBeds` check each
  traveller's bed y == counter y + 5; a pinned relation, the ship step can move it but the beds must share one floor),
  all inside the build's box. No other code counts beds; II and III keep all six in their cells.
- **Every guest bed can be walked to from the front door**, and the guest floor is reached from inside.
- **Walk-in space reached on foot from the front door**: a villager standing just inside the front door finds a path to
  the cell behind the counter (`theBuiltInnsBarTakesAnInnkeeper`), and a jobless villager standing there handed a bed
  becomes the Innkeeper at that counter and keeps the job 200 ticks.
- **Room for an arriving traveller**: a cell with a sturdy floor and two clear blocks above it, no fluid, within 6 across
  and 2 up or down of the counter (`Innkeepers.standingSpot`; the walk-in space or the room in front both count).
- **Storage**: no test counts it. The Innkeeper's drinks come from chests or barrels within 8 across / 4 up or down of the
  counter, so put at least one there. Every chest stands against a wall, a solid block, a bed's foot or its other half
  (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`).
- **A red banner in Inn I's materials** (`FurnishedHomesGameTests.theBuilderWaitsForAMissingPicture`: with every red
  banner held back, the builder must be left waiting for red banners alone). A red picture spot's vanilla stand-in or the
  standing banner below counts.
- **Exactly one standing banner** (not a wall banner) in every level, **on a fence block**, **rotation 8** (facing the
  street, the z = 0 side) (`theInnsDrawingsHangNothingInTheAir`).
- **Furniture spots** (`blueprint_furniture/<id>.json`, written by the recipe's `furniture.json` through `rebuild.py`):
  - **Inn I and II each have at least one spot of every kind bed, chair, table, nightstand, lamp, couch, shelf,
    crockery, clock, picture, blinds** (`FurnishedHomesGameTests.everyHomeHasItsFurnishedRooms`, the FULL set); III has
    II's above the ground.
  - **Inn I has at least 30 spots** (`FurnishedHomesCompatTests.aSavedFurnishedSiteKeepsEveryPiece`) of at least 10
    kinds, and each spot takes the piece its mod order names (`aBuilderBuildsTheInnWithHandcrafted`,
    `aBuilderBuildsTheInnWithCobbleFurnies`: chair, table, bed, nightstand, couch, shelf, crockery, **counter** from
    Handcrafted or CobbleFurnies; lamp, picture, blinds, **hanging_pot** from Beautify; clock vanilla), unless the spot's
    shape can't take that piece.
  - **Every level has a table or chair spot, and every table and chair spot is one the furniture mods fill**
    (`QaMultiHomeFurniture1031CompatTests.noTableOrChairOfATownhouseOrInnStaysVanilla`, more than 20 such spots across
    Townhouse I-IV and Inn I-III).
  - Without a furniture mod every level plans as its vanilla build (`FurnishedHomesGameTests.withoutFurnitureModsEveryHomeIsVanilla`);
    each spot's blocks are exactly the template's there (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`);
    nothing floats (`FloatingFurnishingsGameTests`); no standing lantern on a room's floor
    (`QaFurnishedRooms1031GameTests.noLanternStandsOnARoomsFloor`).
- **A front door** on the ground floor at the z = 0 side, on the counter's floor level.
- **Inn III's cellar** (`BasementGameTests.basementsLoadUnderTheGround`, `QaBasementGameTests`, `CellarGameTests`):
  drawn at the bottom of the file, its floor at file y = 0 and the ground floor at y = 4, exactly 4 deep (the file is
  II's height + 4); at least one ladder or stairs on the cellar's top layer climbs out with room above it at ground y 0
  and 1; **a trapdoor hatch at ground y 0 over a cellar column**; no dark floor cell (block light above 0 on every
  standable cell); walkable from the ground outside down to the cellar floor and back; every chest or barrel down there
  within 2 across / 1 up or down of a walked cell.

## Kept cells (every upgrade)
- II has the same origin and front as I and is at least its size in x, y and z; **at least 60 % of I's non-air blocks
  stay the same block in the same cell** (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`).
- **III above the ground is II exactly**, the same size: the only allowed change is a trapdoor at y 0 over the cellar
  (`CellarGameTests.levelThreesLineUpOverTheirLevelTwos`). So everything III needs above the ground (the hatch's
  surroundings, the way to it) is already in II.
- II has **no basement** (`QaBenchMovesCellars1104bGameTests.aBuildWithNoBasementIsNeverOfferedACellar`).
- Every block with block-entity data (chest, barrel, the Shop Counter, beds) stays in the same cell as the same block in
  every later level (a builder never breaks containers or copies their contents, `MaterialRules.isProtected`).

## Fits
- Plot (y = 0 sits on the clicked block; the front is the z = 0 side), from where the tests place each level:
  - Level I: **at most 13 wide x 16 tall x 13 deep** (`BuilderGameTests.buildsInn` at helper (9, 2, 9) in the
    22 x 18 x 22 `big_area`, with the builder's barrels at x 1-5, z 4; also built at (9, 2, 3) and (3, 2, 3) in the 30-cube
    `huge_area`, and turned a quarter at (15, 2, 15) there).
  - Levels II and III: **at most 27 wide x 27 deep**; II at most 28 tall, III at most 23 above the ground plus the 4-deep
    cellar (`QaInnRedrawn1111cGameTests` at (3, 2, 3) and, for III, (3, 7, 3) in `huge_area`).
- Declared sizes: `StarterBlueprints.INN` (`13, 16, 13`), `INN_2` (`19, 16, 13`), `INN_3` (`19, 16, 13`, basement 4)
  today; if a size changes the ship step changes those `Vec3i`s
  (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: sizes match, every block one a builder
  can place).
- Material budget: Inn I fits the four barrels `buildStarter` stocks, **at most 108 stacks** (each item rounded up to
  whole stacks).
- Build time: Inn I with exactly its materials within **30,000 ticks** (`buildsInn`); furnished, vanilla and with each
  furniture mod order, within 40,000 (`FurnishedHomesGameTests.aBuilderBuildsTheFurnishedInnInVanilla`,
  `FurnishedHomesCompatTests.aBuilderBuildsTheInnWith*`), 0 skipped, every block right.
- Front and way in: the z = 0 side faces the player who places it and the street of a Steward `market` plot; the front
  door goes there on the ground floor; every upgrade keeps that front.
- Neighbours: placed by the Steward in a market zone, or anywhere by a player; no party walls.

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `QaInnRedrawn1111cGameTests.theBuiltInnsBarTakesAnInnkeeper`: the front door at build-box (min x + 6, counter y,
  min z + 2), the walker starting one cell in from it; the walk-in cell is `counter.east()`.
- `QaInnRedrawn1111cGameTests` (`innkeeper(...)`): the jobless villager spawns at `counter.east()`.
- Guest floor at counter y + 5 (`theBuiltInnsSixBedsFillWithTravellers`, `theBuiltInnIIIKeepsItsBarAndBeds`).
- `theInnsDrawingsHangNothingInTheAir`: no campfire at x > 11 (the old inn's east wall; "no chimney fire in the
  side building"), and the counter-facing and bar-material checks above.

The drawings (`QaInnRedrawn1111cGameTests.theInnsDrawingsHangNothingInTheAir`, I, II and III): nothing hangs in the
air: every open trapdoor (through the trapdoors under it) stands on a block or on a held sill; every flower pot stands on
something or hangs from a chain; a standing banner or standing lantern stands on something; a hanging lantern hangs from
something. A sill (upside-down stairs) is held when a block is under it or it is part of a run of at least 9
upside-down stairs along its wall. Plus the counter, beds and banner rules under "Must have". (It also needs the old
`blueprint_history/inn/v0.154.0.nbt` kept, which the ship step does.)

Built in the world: `QaInnRedrawn1111cGameTests.theBuiltInnStandsAfterBlockUpdates`, `theBuiltInnIIStandsAfterBlockUpdates`,
`theBuiltInnIIIStandsAfterBlockUpdates` (pasted as the builder leaves it: every block can survive where it is, nothing
drops while it goes up or after every block gets a neighbour update and 40 ticks); `theBuiltInnsBarTakesAnInnkeeper`;
`theBuiltInnsSixBedsFillWithTravellers` (two guests then `full`; with room for ten, six mornings fill six different
guest beds, the seventh `no_bed`); `theBuiltInnIIIKeepsItsBarAndBeds` (III: the bar makes an Innkeeper, the first
morning's traveller sleeps on the guest floor). `BuilderGameTests.buildsInn`; `FurnishedHomesGameTests.aBuilderBuildsTheFurnishedInnInVanilla`,
`theBuilderWaitsForAMissingPicture`; `FurnishedHomesCompatTests.aBuilderBuildsTheInnWithHandcrafted`,
`aBuilderBuildsTheInnWithCobbleFurnies`, `aSavedFurnishedSiteKeepsEveryPiece`, `switchedOffTheRoomsAreVanilla`.

Refresh (36.21): `QaRefreshFurnishedGameTests` (`qa_b642_inn`: the furnished `aec09cb26` drawing turned a quarter at
(15, 2, 15) in `huge_area`, refreshed, ends equal to a fresh build of the new drawing); `QaRefreshHistory1028GameTests.qaEveryShippedOlderDrawingIsOfferedARefresh`
(every older drawing in `blueprint_history/inn*/` that differs from the new one is offered a refresh); `QaModdedRefresh1115cCompatTests` (the modded Inn from
`v0.145.0`). `blueprint_history/` already holds the old drawings.

Shared checks over every shipped blueprint: `StyleGameTests.chimneysStayWholeInEveryStyle` (a campfire on a column of
full solid blocks keeps it solid in every style; no chimney is required in the Inn); `B648GameTests` (no pane or bar run
ends against open air; `check.py`'s `open_panes`).

By id only (the drawing doesn't matter beyond the rules above): `InnGameTests`, `InnCountersB1203GameTests`,
`InnShopCounterB1177GameTests`, every `QaInn*` test other than `QaInnRedrawn1111c`, `QaMoveInn*`, `WorkplacesGameTests`
(`workplace_inn`), `StewardHomesRulesGameTests.aTownFourBedsShortGetsAnInnInTheMarket`, `ServiceGameTests` and
`QaClassPerksGameTests` (tavern), `MasterArchitectGameTests`, `QaMasterArchitectGameTests`, `LegendGuestsGameTests`,
`GrandChefGameTests`, `TableSearchGameTests`, `QaBlueprintTableGameTests`, `QaHallLenientLoad1102GameTests`.

The owner's rules (`check.py`: posts at fence corners, lamps on tables or posts, crockery only by a stove, furnace,
smoker or counter or on a table with a chair; `open_panes`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate`
prints no CHECK line for any level, and `holes.py` passes. The ship step removes `modded/inn_3` from
`owner_rules_baseline.json`.

## Free
Everything not listed above: shape, plan, rooms, roofs, materials (except the bar's two planks), colors, style, storeys,
where the counter, beds, stairs, cellar and storage stand within the rules above, what II adds beyond keeping 60 % of I,
six beds and one counter, what the cellar holds, lighting, and whether there is a kitchen, a hearth, a stable or a yard.
