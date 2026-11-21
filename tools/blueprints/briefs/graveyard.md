# Graveyard: the brief

Family id(s): `graveyard`, `graveyard_2` (never renamed; "Graveyard" and "Graveyard II" in `en_us.json`,
`blueprint.aliveworkplace.graveyard(_2)`; advancements `built/graveyard` and `built/graveyard_2`)
Variants: village (none drawn: a village's style is applied to the drawing automatically by `BlueprintStyles`), modded
(none to draw: `modded_library.py` makes `structure/modded/graveyard(_2).nbt` from the vanilla drawings and the ship
step reruns it), looks (none).
Not this family: the village **Mortuary** (`village/<style>_mortuary`, `village_looks.py` / `village.py`), the
Undertaker's house in generated villages, is a separate village piece with its own contract.

## What it is for
The village's resting place for the dead and the **Undertaker's** workplace. When a villager dies they leave a grave
(`aliveworkplace:grave`, holding the villager's data) where they fell. The Undertaker (`ModVillagers.UNDERTAKER`, job
site the **Undertaker's Table** or a brewing stand) works from his job block: with a golden apple, a healing potion or a
totem in the chest near it he walks to a grave and brings the villager back (`UndertakerWork`, `Graves.revive`); with
none, he walks to each grave lying about the village, lifts it, and the grave moves to the **next free grave slot of
the village's Graveyard**, then he walks over to it (`Graveyards`, B222, config `gravesToGraveyard`). Graves resting in
the Graveyard are revived from there and get their funeral there (36.16: mourners gather round the grave). The
Graveyard is found from the village's finished builds by id (`Graveyards.isGraveyard`: any tier, any style), its slots
worked out from its placement and rotation each time; nothing about it is saved. A village with a hall and no
Graveyard lays graves in a plot of 12 by the hall instead. The Steward builds it in the **civic** zone when an
Undertaker has no workstation (`steward_rules/workplace_graveyard.json`, priority 62) or when a villager died in the
last 30 days in a village of 8 or more with no Graveyard (`steward_rules/graveyard.json`, priority 30, max 1), and his
renewal of an old Undertaker's house rebuilds it as this one (`steward_renewal/undertaker.json`). On the Village Map it
is a CARE building (`VillageMaps`, by id).

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `graveyard` | The Undertaker's job block, the chest he takes revival items from, and the **grave slots** (at least 32) | Locked at the start (not in `blueprint_unlocks/starting.json`, not rare): unlocked the usual ways (bought, a builder's level-up, a found Blueprint, `/workplace unlock`); Steward rules `workplace_graveyard` and `graveyard` (civic, Hamlet+); renewal `undertaker.json` |
| II | `graveyard_2` | **No new function** (the code treats both tiers alike: same slots, same size). It is the upgrade by name (`BlueprintUpgrades`), built over a finished Level I with the same origin and front | Upgrade of `graveyard`; no `graveyard_3` may be added (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase` counts exactly 64 upgrades) |

## Must have (each level)
- **Grave slots: at least 32 cells where a grave can be laid, the same cells in both levels.** The code holds ONE list for
  every tier (`Graveyards.YARD_SLOTS`, in the template's own coordinates, filled in order) and lays a grave only in a
  slot that is **free** (`Graveyards.free`):
  - the slot cell is air (or a block that can be replaced, like short grass) with no water in it;
  - the block **under** it has a sturdy top and is not a grave;
  - the cell **above** it is air (or replaceable).
  Today's slots are all at **y = 0, so the grave lies on the ground under the build** (y = -1, the world's own ground
  the builder levels): in each slot cell nothing may be drawn at y = 0 or at y = 1 (no floor, path, slab, carpet, pot,
  lamp, post, fence, stair or eave). A drawn floor at y = 0 over the slots kills them. Today's list (B1423), in fill
  order, each row's left side (x 1 to 4) then its mirror (x 12 - x): z = 6 at x 1, 2, 4; z = 8 at x 1, 2 (the nook
  behind, by the chapel wall); z = 4 at x 1 to 4; z = 3 at x 1, 3, 4 (Level II's posts stand at (2, 3) and (10, 3));
  z = 1 at x 1 to 4. The walks left free: the cross-walks z = 2 and z = 5 (x 1 to 4, 8 to 11, stepped into from the way
  in, x 5 to 7), the back walk z = 7 and the gap to it at (3, 6) and (9, 6), so every slot has a cell a villager stands on
  right beside it when the yard is full (a path to a grave ends one block above it, so a cell two blocks off on the
  ground is out of reach). The designer may keep these 32 cells free, or give a new ordered list of at
  least 32 cells (all at y = 0 so the tests' flat-floor placements stay valid); the ship step then rewrites
  `YARD_SLOTS` and the tests' pinned first slots (see "Rules tests check"). Level II must leave every slot of Level I
  free (an upgrade that stands a post or a lamp on a slot loses it for good, since there is one list for both).
- **Each slot reachable on foot**: the Undertaker walks to within 2.5 blocks of a grave to revive it, lay it or hold its
  funeral, and mourners walk to within 3 of it (`UndertakerWork`, `Mourning.gather`). So every slot has a cell a
  villager can stand on within 2 blocks, reached on foot from the job block and from the street outside.
- **A way in a villager can use.** Villagers open doors, never fence gates (the mod has no code that opens a gate for a
  walker, and vanilla pathing treats a closed gate as a fence). If the yard is enclosed, at least one way in is an open
  gap or a door, not only a closed fence gate; a 1-block-tall wall a villager can't step over counts as closed.
- **Job block: exactly one Undertaker job site**, the `aliveworkplace:undertakers_table` (only the Undertaker takes it)
  or a `minecraft:brewing_stand` (the Undertaker accepts the Cleric's site too, but a jobless villager by it becomes a
  Cleric first, and Nurses and Physicians share it; the old drawing used one). The Steward's `workplace_graveyard` rule
  builds this for an Undertaker without a workstation, and a renewal's worker "takes the new workstation"
  (`Renewals`), so there must be one. **No other job block of any job** (barrel, lectern, composter, smoker, any
  crafting/smithing/cartography/fletching table, stonecutter, grindstone, loom, cauldron, bell, any of the mod's
  workstations): it would hire a second worker nobody asked for, and the Table's Jobs line lists it.
  The job block can be **walked to from the ground outside** (within 2 across, 1 up or down of a walked cell;
  `QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`).
- **Storage: at least one chest within 8 blocks across and 4 up or down of the job block** (`SupplyContainers.find`
  round the job site, `RADIUS` 8, vertical 4): the Undertaker fetches golden apples, healing potions and totems from
  it; without one he only ever posts a request. Reachable on foot (same QaBuildReach test). Drawn empty (builders never
  fill containers). No barrel (a Fisherman's job block).
- **At least one plain `minecraft:candle` in Level I.** The showcase scene `chandler` (`JobScenes`, ROADMAP 46.5,
  `tools/showcase/scenes.py`) starts a builder on the Graveyard with every material but candles and films the Beekeeper
  making the candles it needs; with no candle in the drawing nothing is made and the nightly scene fails. Each candle
  stands on a block whose top holds its middle (`BlueprintsStayGameTests`).
- **No `aliveworkplace:grave` block in the drawing**: a drawn grave is an empty grave the Undertaker would find (grave POI)
  and tend, and it would sit on a slot. Headstones are free decoration: no code reads them.
- **No entities** (no item frames, armor stands, paintings or mobs), no calcite, jigsaw or structure void.
- **Beds and residents: none required** (no code counts beds here; the Undertaker sleeps in a home). A bed is allowed
  but counts as a home bed for the Steward; if drawn, its head is reachable and its foot has its head one block towards
  its facing (`BlueprintsStayGameTests`), and it is not right inside a doorway (`check.py`).
- **Furniture spots**: no kind is required by any job or test, and there is no `blueprint_furniture/graveyard.json`
  today. Every piece the recipe records becomes a spot: each spot's blocks must be exactly the template's
  (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`), nothing floats (`FloatingFurnishingsGameTests`), every
  chair faces its table, turned and mirrored too (`QaChairFacingGameTests`).
- Every block is one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`)
  and stays as placed once built (the hillside tests assert `plan.unfinished` is empty after the build: no leaves that
  decay, no falling blocks over air, no water that flows, no lamp or candle on a block that can't hold it).

## Fits
- **Plot: exactly 13 wide (x) x 13 deep (z), at most 16 tall**, both levels the same footprint (Level II may not be
  smaller than Level I in any axis). Why:
  - `BuilderGameTests.buildsGraveyard` builds Level I at helper (9, 2, 9) in the 22 x 18 x 22 `big_area` (x 9..21,
    z 9..21, y 2..17), the bench and barrels at x 1..5;
  - the 13 x 13 box is hard-coded in `Graveyards.SIZE` (a grave anywhere inside it counts as resting there, and its
    middle decides which village owns it, B788) and in the tests `B788GameTests.SIZE` and `QaGraveyardRim1102GameTests.SIZE`;
  - today's slots span x 1..11, z 1..8.
  A different footprint means rewriting `Graveyards`, its slot list and three test classes; don't. A different
  **height** is fine: the ship step changes the `Vec3i` of both `StarterBlueprints.GRAVEYARD` and `GRAVEYARD_2` (sizes
  must match the files, `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`), `Graveyards.SIZE` and the two
  tests' `SIZE` constants. Today all are 13 x 11 x 13.
- y = 0 sits on the clicked block; the front is the z = 0 side.
- **Front and way in:** the z = 0 side is the way in (the "gate side" in `Graveyards`), facing the player who places it
  and the road of a Steward civic plot. No code pins the door or gate cells.
- **Neighbours:** a civic building in a Steward zone; the Steward keeps 2 blocks clear round its plot (`Plots.CLEAR`)
  and wants the ground within 4 blocks of level (`Plots.MAX_STEP`). A renewal takes it only where 13 x 13 fits the old
  Undertaker's house footprint plus 3 a side (`Renewals.MARGIN`).
- **Material budget:** Level I's whole list fits **four barrels (108 stacks)**, each item rounded up to whole stacks
  (`buildsGraveyard`); with a foundation up to the game rule's depth on a hillside, plus one stack of dirt, it fits
  **eight barrels (216 stacks)** (`TerrainStallGameTests`). The old drawings are 325 (I) and 391 (II) blocks.

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- **Build time.** `BuilderGameTests.buildsGraveyard` (`big_area`, origin (9, 2, 9), batch `starter_builds_3`): one
  builder, build delay 2, builders' help off, stocked with exactly the plan's materials, must finish Level I within
  **30,000 ticks**. `TerrainStallGameTests.b23GraveyardOnAHillsideNeverStalls` (origin (9, 10, 8)),
  `b23AFarGraveyardNeverStalls` (origin (9, 10, 14)) and `QaB23TurnedHillsideGameTests.qaB23GraveyardTurnedOnAHillsideNeverStalls`
  (origin (9, 10, 20), turned counterclockwise, covering x 9..21, z 8..20) build it on a one-in-one slope in
  `huge_area`: each must finish within **40,000 ticks**, the builder on shift must **never go 600 ticks without placing,
  digging or skipping**, and every block must stand as drawn afterwards. Keep the block count modest and the build easy
  to reach.
- **No roof, overhang, barge or eave over another roof** (a builder stuck on a barge stair above a lower roof timed out
  another family's build test), and nothing a builder must stand on air to place.
- **The grave slots** (no test builds the Graveyard and then lays a grave in it; these tests record a finished
  Graveyard on a flat test floor and read `YARD_SLOTS`, so they pin the slot list, and the slot rule above is what makes
  the real build work): `B222GameTests.firstSlot` and its four tests (`strayGraveGoesToTheGraveyardAndIsRevivedThere`,
  `reloadMidMoveKeepsTheGrave`, `switchOffLeavesGravesWhereTheyFall`, `noGraveyardMeansAPlotNearTheHall`) pin the first
  slot at template **(1, 0, 6)**; `QaVillageReachGraves1101GameTests.slot` pins the first two at **(1, 0, 6), (2, 0, 6)**
  (`gravesLeftWithTheSwitchOffAreMovedLaterIntoTheRowsInOrder`, `aGraveMovedToTheGraveyardKeepsTradesAndPartnerThroughRevival`)
  and puts a grave inside the box at (6, 0, 9); `B788GameTests` pins (1, 0, 6) for both villages' Graveyards
  (`aNeighborsGraveyardIsNotThisVillages`, `anUndertakerNeverLaysAGraveInTheNeighborsGraveyard` and the rest).
  `B779B780GameTests`, `QaVillageFixesVerify1102GameTests.qaWhereVillagesOverlapAGraveGoesToTheNearerHallsGraveyard`,
  `QaVillageReachGraves1101BugTests`, `QaVillagerFixes1104c2GameTests.qaWithAnAppleInTheChestAStreetGraveIsRevivedNotMoved`
  and `QaGraveyardRim1102GameTests` place it by id and read its slots and 13 x 13 box.
- **The upgrade.** `BuilderGameTests.starterUpgradesKeepMostOfTheirBase`: `graveyard_2` is at least as big as
  `graveyard` in every axis and keeps **at least 60 %** of Level I's non-air blocks, the same block in the same state at
  the same cell; exactly 64 upgrades in the library.
- **Reach.** `QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`: the job block, every chest and
  any bed head within 2 across and 1 up or down of a cell reached on foot from outside (doors, trapdoors and gates
  count as open in this test; real villagers don't open gates, see "A way in").

By id only: `WorkplacesGameTests.workplaceRule_graveyard_undertaker` (civic, Hamlet), `StewardCivicRulesGameTests.aDeathInAVillageOfEightGetsAGraveyard`,
`QaStewardCivicBoundariesGameTests.aGraveyardIsWishedForWithinThirtyDaysOfADeathAndFromEightVillagers`,
`QaBuiltAdvancements1116bGameTests`, `QaTableJobsGameTests.everyListedBuildShowsItsOwnJobsAndBeds` (the Table's Jobs line
matches the drawing's job blocks), `ModdedLibraryGameTests`.

Library-wide: `B648GameTests` / `QaWindowsAndWards1028BugTests` (no pane or bar run ending against air),
`BlueprintsStayGameTests`, `QaDoorwayClearanceGameTests` and `QaDoorwayBugGameTests` (a 1.95-tall villager passes every
doorway; no carpet just inside a 2-tall one), `QaChairFacingGameTests`, `QaMirroredBuildsGameTests`,
`QaTurnedFramesGameTests`, `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`,
`StyleGameTests.chimneysStayWholeInEveryStyle` (only if a campfire stands on a solid stack), the style and book tests
that restyle every build (`QaBlueprintBook1117eGameTests`).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair, no bed right inside a door; `open_panes`), STYLE.md's checks and `holes.py`:
`rebuild.py --candidate` prints no CHECK line and `holes.py` passes. Saves: there is **no
`blueprint_history/graveyard/` yet**; the ship step saves both old drawings there (`history.py`) and regenerates the
modded variants.

## Free
Everything not listed above: the shape, plan, roof, materials and colors; whether there is a building at all beside the
yard (a chapel, a mortuary, a shelter, none); where the job block and chest stand (within the rules); the walls,
gate, paths, headstones, trees, lamps and planting; what Level II adds, as long as it keeps 60 % of Level I and every
slot free.
