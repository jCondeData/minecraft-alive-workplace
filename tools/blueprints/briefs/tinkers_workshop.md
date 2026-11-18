# Tinker's Workshop: the brief

Family id(s): `tinkers_workshop`, `tinkers_workshop_2` (never renamed; "Tinker's Workshop" and "Tinker's Workshop II" in
`en_us.json`, `blueprint.aliveworkplace.tinkers_workshop` / `_2`; hidden advancements `built/tinkers_workshop` / `_2`)
Variants: village (none: the village house `village/<style>_tinkers_shop` is a different family with its own id and
contract, `village.py`'s `tinkers_shop`; it is not part of this brief), modded (none to draw: `modded_library.py` makes
`structure/modded/tinkers_workshop.nbt` and `_2` from the vanilla drawings and the ship step reruns it;
`ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`, `withoutTheModsEveryBuildIsTheVanillaBuild`), looks (none).
The Table's style buttons (`BlueprintStyles`) restyle the drawing automatically. ROADMAP item: 50.3 (Milestone 50, the
workshops, same method as 49).

## What it is for
The workplace of the **Tinkerer** (`ModVillagers.TINKERER`) and the vanilla **Toolsmith**. The Tinkerer is a crafter for
the builders (`craft/TinkererWork`, wiki `crafter.md`, `job-lists.md`): he makes the redstone and metal parts they wait
for (rails, pistons, hoppers, lanterns, iron bars: the `#aliveworkplace:tinkering` tag), firing raw ore into ingots in
his bench's own little forge (no furnace block is read for it), takes iron from the chests by his workstation and the
village's stores, carries what he made to the builder's chests, and between jobs mends the village's hurt iron golems
within 32 blocks. His job sites are the vanilla **smithing table** (shared with the Toolsmith and, with Cobblemon, the
Ball Smith: `work/Stations`) or the mod's Tinker's Bench (`ModVillagers.TINKERER`: `TINKERS_BENCH_POI` or
`PoiTypes.TOOLSMITH`). The Steward builds this building by himself in a `workshops` zone when a Tinkerer **or a
Toolsmith** is wanted and has no workstation (`steward_rules/workplace_tinkers_workshop.json`, priority 62, cooldown 2
days), and his renewal of an old Tinkerer's or Toolsmith's house rebuilds it as this one
(`steward_renewal/tinkerer.json`, `toolsmith.json`). Once finished, a shared block inside it (its bounds plus 2 blocks,
`StewardJobs.BUILDING_MARGIN`) goes to a Tinkerer while nobody of that job works inside (`StewardJobs.BUILDING_JOBS`),
so the Steward's morning jobs make the smithing table here a Tinkerer's, not a Toolsmith's. On the Village Map it is a
Workshops building (`VillageMaps.kindOf`, by name). It is not in the starting set or the rare list
(`blueprint_unlocks/`); no questbook chapter names it.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `tinkers_workshop` | The Tinkerer's (or Toolsmith's) workstation, a smithing table, and storage by it | Steward rule `workplace_tinkers_workshop`; renewal lists `tinkerer.json`, `toolsmith.json` |
| II | `tinkers_workshop_2` | No code or test asks for anything new. It must earn its place by function: the natural one is more storage within the workstation's reach (the Tinkerer's sources are every store within 8 across and 4 up or down of his block) | By name (`BlueprintUpgrades`: `<name>_2` upgrades `<name>`). A builder who finishes Level I sells Level II (`UpgradeOffers.offer`, price `4 + solid blocks / 40`, clamped 6-32 emeralds) |

**Exactly two levels.** No `tinkers_workshop_3`: it would change the counted upgrades in
`BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (exactly 64) and is a new feature.

**Upgrade rules the code reads** (`BlueprintUpgrades`, `BuilderGameTests.starterUpgradesKeepMostOfTheirBase`):
- Level II is built **over a finished Level I at the same origin and the same front** (only what changed is built), so
  Level I's cells sit at the same template coordinates in Level II.
- Level II is **at least as big as Level I in x, y and z**.
- Level II **keeps at least 60 % of Level I's non-air blocks unchanged** (same block, same state, same position).
  Draw the two together, Level II growing out of Level I.
- Level I's smithing table stays where it is in Level II (a Tinkerer already working there keeps his block).

## Must have (each level)
- **Job block: a `minecraft:smithing_table`** (the vanilla block). It is the only block that serves both trades the
  Steward builds this for: a Tinker's Bench alone would leave the rule's Toolsmith with no workstation
  (`workplace_tinkers_workshop.json` lists `aliveworkplace:tinkerer` and `minecraft:toolsmith`; `PoiTypes.TOOLSMITH` is
  the smithing table only). One per level is what the code expects (one worker); each extra smithing table or Tinker's
  Bench is another workstation the Steward hands to another villager. No test counts it, but the drawing must have one.
- **No other trade's job block** (code, not a test: every acquirable job block is a free workstation the Village Hall
  lists and `StewardJobs` gives to a jobless villager, and vanilla villagers take them by themselves): no barrel
  (Fisherman), composter, lectern, loom, stonecutter, grindstone, cauldron, brewing stand, blast furnace, smoker,
  fletching or cartography table, bell, and none of the mod's workstations; also no crafting table (the hall lists it
  as a free Carpenter's block, `B635GameTests`). The old drawing breaks this (seven barrels and a crafting table); the
  new one should not. A chest, furnace or anvil is not a job block.
- **The smithing table is reachable on foot from outside**, within a player's reach (2 across, 1 up or down) of a cell
  a villager walks to (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`), and inside the
  building's bounds (so `BUILDING_JOBS` finds it).
- **Beds and residents:** none required (the old one has no bed; no test or code asks for one). If the designer adds
  a bed: its head is reachable on foot (`QaBuildReachGameTests`), its foot has its head one block towards its facing
  (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`), it is found as a home turned and mirrored any way
  (`HomesSpecGameTests.everyBedOfEveryStarterHouseIsAtHomeTurnedAnyWay`), no bed right inside a doorway (`check.py`).
- **Storage: at least one chest within 8 blocks across and 4 up or down of the smithing table**
  (`SupplyContainers.RADIUS` / `VERTICAL`): the Tinkerer's own chest, where he takes iron from
  (`TinkererWork` sources, `QaCrafterOwnChest1111GameTests.ironInTheTinkerersOwnChestBecomesTheBuildersBars` tests the
  rule with a bench and chest it places itself) and where `CrafterWork` tidies his bag (with none, it is dropped on the
  ground). Drawn empty (builders never fill or copy containers; no loot table).
- **At least one double chest** (a left and a right half, joined) in Level I:
  `QaMirroredBuildsGameTests.aMirroredTinkersWorkshopIsBuiltWithItsDoubleChestWhole` fails with "has no double chest to
  check" without one, and checks both halves come out joined when the build is placed mirrored. Every chest stands
  against a wall, a solid block, a bed or another chest (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`)
  and is reachable on foot (`QaBuildReachGameTests`).
- **Furniture spots:** no kind is required by any test for this id (not a home). Every furniture piece drawn is
  recorded as a spot (`rebuild.py`; there is no `blueprint_furniture/tinkers_workshop.json` today), each spot's blocks
  are exactly the template's (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`), nothing small stands on air
  (`FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece`), every chair stair faces its table, turned and
  mirrored too (`QaChairFacingGameTests`).
- **A way in that a villager 1.95 blocks tall walks through** (`QaDoorwayClearanceGameTests`; B170), on the ground
  floor. Any upper floor or loft holding a chest, bed or job block is reached by a ladder or stairs from inside (B149,
  `QaBuildReachGameTests`).
- Every block is one a builder can place, and the file holds no jigsaw, structure void or entity
  (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).

## Fits
- **Plot, Level I: at most 13 wide x 11 tall x 13 deep.** `BuilderGameTests.buildsTinkersWorkshop` builds it in the
  22 x 18 x 22 `big_area` at helper (9, 2, 9) (x 9..21, z 9..21), side by side with another test in its batch
  (`starter_builds_4`). Height: `TerrainStallGameTests.b23TinkersWorkshopOnAHillsideNeverStalls` and
  `QaB23TurnedHillsideGameTests.qaB23TinkersWorkshopTurnedOnAHillsideNeverStalls` set its floor at helper y = 10 in the
  20-tall `huge_area`; the old drawing's 11 already reaches the area's top, so taller means moving those tests' origins
  (the ship step's call, not the designer's).
- **Plot, Level II: at most 13 wide x 16 tall x 14 deep**, and at least Level I in every axis
  (`BuilderGameTests.buildsTinkersWorkshopII` at helper (9, 2, 8) in `big_area`: x 9..21, y 2..17, z 8..21).
- y = 0 sits on the clicked block; the front is the z = 0 side.
- **Material budget: at most 108 stacks per level** (`buildStarter` stocks four barrels of 27 with exactly the plan's
  materials, each item rounded up to whole stacks, and fails "test needs more barrels"). The hillside tests allow 216
  stacks including the foundation on the slope, B38 270. The old drawings are about 28 and 36 stacks (433 and 592 blocks).
- **Build time:** `buildsTinkersWorkshop` 30,000 ticks, `buildsTinkersWorkshopII` 40,000, the hillside tests and
  `B38GameTests.b38TheTinkersWorkshop{Builder,Crew}NeverWaitsWithNothingMissing` 40,000 (build delay 2);
  `aMirroredTinkersWorkshopIsBuiltWithItsDoubleChestWhole` **12,000 ticks** (free materials, build delay 1). A much
  bigger Level I risks the last one.
- **Declared sizes:** `StarterBlueprints.TINKERS_WORKSHOP` is `(12, 11, 10)` and `TINKERS_WORKSHOP_2` `(12, 11, 14)`
  today; the ship step changes those lines to the new sizes
  (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`), and their javadoc (which still says "a Tinker's Bench
  inside").
- **Front and way in:** a Steward plot turns the z = 0 side to the nearest road (or the hall) (`Plots`), so the way in
  belongs on that side.
- **Neighbours:** a workplace in a Steward `workshops` zone; the Steward keeps 2 blocks clear round its plot
  (`Plots.CLEAR`) and wants the ground under it within 4 blocks of level (`Plots.MAX_STEP`). A renewal takes it only
  where it fits the old house's footprint plus 3 a side (`Renewals.MARGIN`), so a smaller Level I renews more old
  houses (free).

## Rules tests check
Pinned by id and placement (the ship step keeps what each checks, moving origins only if a size forces it):
- `BuilderGameTests.buildsTinkersWorkshop` / `buildsTinkersWorkshopII`: built whole from bare ground from exactly the
  plan's materials; every block matches after the build.
- `TerrainStallGameTests.b23TinkersWorkshopOnAHillsideNeverStalls` (origin helper (9, 10, 8)) and
  `QaB23TurnedHillsideGameTests.qaB23TinkersWorkshopTurnedOnAHillsideNeverStalls` (helper (18, 10, 8), turned clockwise):
  on a hillside rising to the east, the builder never goes 600 ticks on shift without placing, and finishes with every
  block right. A deep foundation or a wide landscape makes this harder.
- `B38GameTests` (three tests, origin helper (12, 2, 12) in `huge_area`, alone, with a helper builder, and with a
  variant in the bag): never 600 ticks waiting with nothing missing; finishes. The variant test needs at least two kinds
  of block item in the plan.
- `QaMirroredBuildsGameTests.aMirroredTinkersWorkshopIsBuiltWithItsDoubleChestWhole` (origin helper (20, 2, 8),
  `Mirror.FRONT_BACK`, so the build spans x 20 back to 21 - width): at least one double chest, joined when built
  mirrored.
- `WorkplacesGameTests.workplaceRule_tinkers_workshop_tinkerer` / `_toolsmith`: the rule builds `tinkers_workshop` in
  `workshops` and the blueprint loads (by id only).

Library-wide checks it must pass: `QaBuildReachGameTests`, `QaMirroredBuildsGameTests.everyBuildsDoorsBedsAndDoubleChestsStayWholeTurnedAndMirrored`,
`QaTurnedModBlocksGameTests`, `QaTurnedFramesGameTests` (frames and paintings hang turned and mirrored),
`BlueprintsStayGameTests`, `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`, `QaFurnishedRooms1031GameTests` (no
standing lantern on a room's floor; every indoor chest against a wall), `FurnitureSpotGameTests`,
`FloatingFurnishingsGameTests`, `B648GameTests` and `QaWindowsAndWards1028BugTests` (no pane or bar joined on one side
with air on the other), `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`,
`StyleGameTests.chimneysStayWholeInEveryStyle` (only if a campfire stands on a solid stack), `ModdedLibraryGameTests`,
`HomesSpecGameTests` (only if it has a bed).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair, no bed right inside a door; `open_panes`), STYLE.md's checks and `holes.py`:
`rebuild.py --candidate` prints no CHECK line, and `holes.py` passes. Saves stay safe: the ship step keeps the old
drawings in `blueprint_history/` (`history.py`) and regenerates the modded copies and the furniture spots with the family.

## Free
Everything not listed above: shape, plan, roof, materials, colors, storeys, a forge or yard or none, what the trade shows
from the street, where the smithing table and chests stand (within the rules above), beds, how many chests beyond one
double chest, lighting, and what Level II adds beyond the upgrade rules.
