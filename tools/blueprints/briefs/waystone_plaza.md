# Waystone Plaza: the brief

Family id(s): `waystone_plaza` (never renamed; one level)   Variants: **looks, one per village culture**: plains is the
drawing itself (`structure/waystone_plaza.nbt`), and `structure/looks/<culture>/waystone_plaza.nbt` for `desert`,
`savanna`, `snowy` and `taiga` (`VillageWaystones.LOOKS`, `BlueprintLooks`). All five are drawn together, each in its
culture's own materials, and each must meet every rule below. Village styles: none. Modded: none.

## What it is for
A waystone by every Village Hall (ROADMAP 41.1), offered only with the **Waystones** mod installed and the
`villageWaystones` switch on. The village's Steward proposes it **once**, in a Civic zone nearest the hall
(`steward_rules/waystone_plaza.json`: `mod_loaded waystones`, `waystones_offered`, none built yet, priority 45, max 1).
It is a Steward build only, not listed at the Blueprint Table. The builder builds it from the store like any build,
the waystone included. When the builder sets the stone down, it becomes a **working waystone of the mod's, named after
the village** (`VillageWaystones.placed`, `round`, `renamed`); players discover it and travel to it the usual way. The
village's stones make a network on the hall's Map (41.2), and caravans and settlers between two villages that both have
a standing stone **walk to their stone, step into it, and step out beside the other village's** (41.3,
`VillageWaystones.beside`). A broken stone is a hole in a finished build: the builder's upkeep puts it back (only from
a waystone item of the same kind) and it is named again. A Mover can move the plaza and the stone stays the same
waystone (45.1). Nobody lives or works here.

The Steward tries the hall's culture's look first, then the other four, and builds the first that fits the plot
(`WaystonesCompatTests`: the plazas tried are the culture's look first, five in all), so every look must have the same
size.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `waystone_plaza` and its four looks | The village's waystone, a seat for travelers to wait, a place by the hall | Steward rule only (no Table listing, no loot table). No upgrade exists or may be added |

## Must have
- **Exactly one waystone per look, a Waystones block found by id**: its lower half at **(3, 2, 3)** and its upper half
  at **(3, 3, 3)** (a two-block-tall double block; `half=lower` / `half=upper`). The code knows the stone only by id
  (`VillageWaystones.isStone`: namespace `waystones`, path ending in `waystone`, a lower half), so any `waystones:*waystone`
  works, but each look keeps its culture's kind, which the wiki promises and one test pins:
  - plains `waystones:waystone` (pinned: `QaWaystonePlaza1104d4CompatTests.aBrokenStoneIsntPutBackFromAnotherKindOfWaystoneButIsFromItsOwn`
    asserts the plains stone is exactly `waystones:waystone`);
  - desert `waystones:sandy_waystone`, savanna `waystones:mud_bricks_waystone`, snowy `waystones:deepslate_waystone`,
    taiga `waystones:mossy_waystone` (`docs/wiki/waystones.md`).
  - Keep the block state the old drawing sets (`facing`, `half`, `origin=village`, `waterlogged=false`); which way it
    faces is free.
  - No other block from the `waystones` namespace anywhere (no second stone, no sharestone or portstone).
- **Why (3, 2, 3) is fixed:** about twenty compat test classes hard-code the stone's spot as the plaza origin plus
  `STONE = new BlockPos(3, 2, 3)` and read or break the stone there (`WaystonesCompatTests.stone`, `WaystoneQaCompatTests.STONE`,
  `QaWaystonePlaza1104d4CompatTests.STONE`, `QaWaystoneCross1115d3BugCompatTests.STONE` (via `plan.at(STONE, ...)`),
  `QaWaystoneRaiseStone1107dCompatTests`, `QaWaystoneRenamedBroken1110dCompatTests`, `QaSettlersStepIn1102dCompatTests`,
  `QaCaravanStone1102dCompatTests`, and the rest of the `*Waystone*CompatTests`). Moving it means editing all of them;
  don't.
- **An open spot beside the stone on every side.** Travelers stand on "the nearest open spot beside it" toward where
  they're going: `VillageWaystones.beside` looks at the ring 1 block round the stone first, then 2 blocks out, at the
  stone's level or one up or down, for a cell with **solid footing below and two blocks of non-colliding room**
  (`open`). `WaystoneQaCompatTests.travelersStepInFromAnOpenSpotBesideTheStone` builds the plaza and asserts the spot
  found toward a village to the **south-east (+x, +z)** is beside the stone (within 2), is open with footing, and is
  nearer that village than the stone is. A plaza can be approached from any direction, so leave standing room round
  the stone on all four sides within 1-2 blocks (a dais is fine; the spot may be 1 up or down from the stone's base).
  A ring that is all walls, rails, lamps or benches right round the stone fails the test's direction.
- **The stone must be reachable by the builder** (he places both halves) and **by a walking villager** from the
  plaza's edge (caravans walk to it).
- Job blocks: none, and no villager POI block (no bell, lectern, barrel, composter...). Beds: none. Storage: none (a
  chest here would be taken for the builder's supply).
- Furniture kinds a job or test needs: none (there is no `blueprint_furniture/waystone_plaza.json`).

## Every block must be one a survival builder can place and keep in place
- Built from its material list (with the store holding the culture's waystone item) it must finish as drawn:
  `WaystonesCompatTests.theBuilderRaisesAWorkingWaystoneNamedAfterTheVillage` stocks barrels with exactly the plan's
  materials, has the builder build the plains plaza, asserts **nothing unfinished**, and that a working waystone named
  after the village stands at (3, 2, 3). So no block that changes by itself or needs a later block to stand on.

## Fits
- Plot: **exactly 7 (x) x 5 (y) x 7 (z) for every look**:
  `QaWaystonePlaza1104d4CompatTests.everyStylesPlazaIsSevenByFiveBySevenWithOneWaystone` asserts the size of all five
  and exactly one lower-half waystone each. (The wiki says "7 by 5 by 7" too.) Nothing may stand above y = 4; the
  stone's top is y = 3.
- y = 0 sits on the clicked block; the front is the z = 0 side (the old one had its steps there). The builder's path
  back to his bench starts one block in front of the middle of the z = 0 edge (no door), so leave that cell walkable.
- Neighbours: in a Civic zone near the Village Hall, laid out by the Steward's plot planner. Tests paste two plazas 16
  blocks apart (`PLAZA_A` (3, 2, 3), `PLAZA_B` (19, 2, 19)), well apart at 7 x 7.

## Rules tests check
- `QaWaystonePlaza1104d4CompatTests.everyStylesPlazaIsSevenByFiveBySevenWithOneWaystone`: 7 x 5 x 7, one waystone, all five looks.
- `QaWaystonePlaza1104d4CompatTests.aBrokenStoneIsntPutBackFromAnotherKindOfWaystoneButIsFromItsOwn`: the plains stone at
  (3, 2, 3) is `waystones:waystone`; broken, upkeep puts it back only from a plain waystone.
- `WaystonesCompatTests.theBuilderRaisesAWorkingWaystoneNamedAfterTheVillage`: built from exactly its materials, nothing
  unfinished, a working named stone at (3, 2, 3); `aBrokenWaystoneIsRepairedAndNamedAgain` (breaks (3, 2, 3) and
  (3, 3, 3)); the Steward tries the culture's look first, five looks in all.
- `WaystoneQaCompatTests.travelersStepInFromAnOpenSpotBesideTheStone`: an open standing spot beside the stone toward +x, +z.
- Every other `*Waystone*CompatTests` class reads the stone at origin + (3, 2, 3) of a pasted or built plaza (network,
  map, travel, caravans, settlers, renames, moves, orphans).
- `VillageWaystonesGameTests` (without Waystones): no plaza is offered; reads only the rule.
- The owner's rules (`check.py`) and STYLE.md's checks: `rebuild.py --candidate` prints no CHECK line, `holes.py` passes.
  Note the render (`shoot.mjs`) leaves out the waystone, a modded block: judge the plaza knowing a 2-tall stone stands
  at (3, 2-3, 3).

## Free
Everything else: paving, edging, the dais or none, seats (real ones), lights, planting, materials per culture, and the
whole layout round the stone, as long as each look is 7 x 5 x 7 with its one culture's waystone at (3, 2, 3) and open
standing room beside it.
