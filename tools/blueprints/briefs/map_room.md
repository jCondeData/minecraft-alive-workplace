# Map Room: the brief

Family id(s): `map_room` (never renamed)   Variants: modded (none to draw: `modded_library.py` makes
`structure/modded/map_room.nbt` from the vanilla drawing; the ship step runs it again), village (none), looks (none).
(The `map_room` function in `wonders.py` is a room inside a Wonder, not this family.)

## What it is for
The Cartographer's workplace (ROADMAP 27.13). Its heart is a vanilla **cartography table**, the Cartographer's job site
(`PoiTypes.CARTOGRAPHER`); the cartographer works there as vanilla's does. The Steward asks for it when a cartographer
has no workstation (`steward_rules/workplace_map_room.json`: civic zone). It is a **rare** build (`blueprint_unlocks/rare.json`):
the Blueprint Table shows it as a dark ??? until the village unlocks it, and it turns up in some loot chests (pirate
ship, piglin hoard, war camp, coven hut, bandit camp, pharaoh's tomb, by id). It shows on the Village Map as a LEARNING
building (`VillageMaps`, by id), and has its advancement `advancement/built/map_room.json`. Its spec gives it a tall
narrow tower with a lookout at the top, and a test holds that (see below).

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `map_room` | The cartography table (the job block), a tower with a lookout at the top and a way up to it | Rare: hidden until unlocked (`rare.json`); asked for by the Steward's `workplace_map_room` rule (Hamlet rank) |

**No upgrade:** `QaNewWorkplacesGameTests.qaNewWorkplaceNamesAndText` fails if `map_room_2` is in the library.

## Must have (each level)
- **Job block: exactly one `minecraft:cartography_table`**
  (`QaNewWorkplacesGameTests.qaYardLodgeAndMapRoomHoldTheirBlocks`; `CraftWorkplacesGameTests.craftWorkplaceBuilt_map_room`
  counts exactly one in the blueprint).
  - **Reachable on foot from the ground outside**, within 2 across and 1 up or down of a walked cell
    (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`).
  - **A cartographer waiting nearby must claim it** within the test's time after the build, and his job site must be
    that table, inside the build's box (`CraftWorkplacesGameTests.build`). No other vanilla job site that would pull him
    or another villager away is needed (a lectern is the librarian's, a barrel the fisherman's).
- **The tower:** height **≥ 1.5 x the larger of width and depth** (`size.y >= wide + wide / 2`, integer division)
  (`qaYardLodgeAndMapRoomHoldTheirBlocks`: "the Map Room isn't a narrow tower").
- **The lookout:** at least one non-air block at **y ≥ 3/4 of the height** (above any basement) (`qaYardLodgeAndMapRoomHoldTheirBlocks`).
  In spirit (the spec, the wiki): somewhere to stand at the top.
- **A way up:** at least one **ladder or stairs block** (`Blocks.LADDER` or `#stairs`) (`qaYardLodgeAndMapRoomHoldTheirBlocks`).
  Every chest and the table must be walked to (the walk climbs ladders), so any upper floor holding one needs a real way up.
- Beds and residents: **none required.** No code counts beds; the cartographer comes from the village.
- Storage: **none required.** No code reads a chest here. Any chest or barrel must be reachable (`QaBuildReachGameTests`)
  and indoor ones stand against a wall (library-wide furnished-room checks).
- Furniture kinds a job or test needs: none. If `blueprint_furniture/map_room.json` records spots, each spot's blocks
  must be exactly the template's there (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`).
- No entities in the drawing, and no calcite, jigsaw or structure void (`CraftWorkplacesGameTests.build`).

## Fits
- **Plot** (`CraftWorkplacesGameTests.build`, in the 30 x 30 x 30 `huge_area`: bench at helper (3, 2, 3), supply chests
  at (3, 2, 6/8/10), origin at helper (12, 2, 8)): at most **16 wide (x) x 26 tall (y) x 20 deep (z)**, keeping 2 blocks
  spare inside the area for landscaping. With the tower rule, the footprint's larger side is at most 17 for height 26
  (say ≤ 16 x 16). y = 0 sits on the clicked block; the front is the z = 0 side.
- **Materials:** the whole list fits in **81 stacks** (three chests of 27) (`CraftWorkplacesGameTests.build`).
- **Build time:** one builder at build delay 2, then the worker takes the table, within **12,000 ticks** (the timeout).
- Declared size: `StarterBlueprints.MAP_ROOM` (9, 16, 9) today; `CraftWorkplacesGameTests.build` asserts the drawing's
  size equals it, and so does `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`; the ship step
  changes the `Vec3i` to the new drawing.
- Front and way in: a door or open way on the z = 0 side. Every wooden door leaves a villager 1.95 blocks of room over
  its step (`QaDoorwayBugGameTests.everyListedBuildsDoorwaysLeaveAVillagerRoomOverTheStep`).
- Neighbours: none planned. The Steward places it on a civic plot; the ground under the footprint may step up to
  `Plots.MAX_STEP` (4) blocks, which the builder levels.

## Rules tests check
Pinned to the drawing:
- `QaNewWorkplacesGameTests.qaYardLodgeAndMapRoomHoldTheirBlocks`: exactly one cartography table; height ≥ 1.5 x the
  larger footprint side; a non-air block in the top quarter; at least one ladder or stairs.
- `CraftWorkplacesGameTests.craftWorkplaceBuilt_map_room` (generator `builds`): size equals `MAP_ROOM`; no entities,
  calcite, jigsaws or structure voids; one cartography table; ≤ 81 stacks; built by one builder; a cartographer claims
  that table, inside the build's box; within 12,000 ticks.
- `QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`: the table and every chest, barrel and bed
  head, walked to from outside.
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`, `QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`,
  `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`, `QaChairFacingGameTests` (seats beside a table
  face it), `QaDoorwayBugGameTests`, `FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`.
- Modded copy: `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`, `ModdedLibraryCompatTests.everyModdedCopyCanBeBuiltWithThePacksMods`
  (same size), `QaCarvedLibraryCompatTests.everyModdedCopyKeepsItsJobSites` (the table at the same cell): regenerate with
  `modded_library.py`.
- Refresh: old drawings are in `blueprint_history/map_room/` and `blueprint_history/modded/map_room/` (key `a12542b71`);
  `QaRefreshHistory1028GameTests.qaEveryShippedOlderDrawingIsOfferedARefresh` and
  `QaModdedRefresh1115cCompatTests.redrawnModdedCopiesAreOfferedFromTheirShippedDrawingOnly` expect a building standing
  as the old drawing to be offered the new one (the new drawing must differ).
- By id only: `CraftWorkplacesGameTests.craftWorkplacesAreInTheBlueprintTable` (in the library, a starter, LEARNING on
  the map), `craftWorkplaceRule_map_room_cartographer` (civic, Hamlet), `QaNewWorkplacesGameTests.qaNewWorkplaceNamesAndText`
  ("Map Room", no `map_room_2`), `qaOnlyTheBandstandIsADecoration` (counts 0 for beauty),
  `qaNewWorkplaceRules_cartographer`, `QaBlueprintLibraryVerify1108bGameTests` (rare: hidden, not listed by id),
  `QaBuiltAdvancements1116bGameTests.everyShippedBlueprintHasItsAdvancement`.
- The owner's rules (`check.py`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no CHECK line, and
  `holes.py` passes.

## Free
Everything not listed above: shape (as long as it is a tower by the ratio), plan, size within the plot, materials,
colors, style, roof form, which floor the table is on (reachable), chests, shelves, seating, the form of the lookout and
of the way up, lighting, ground and planting.
