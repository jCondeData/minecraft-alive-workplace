# Leader's Hall: the brief

Family id(s): `leaders_hall` (never renamed)   Variants: village (`village/<style>_leaders_hall` for `plains`,
`desert`, `savanna`, `snowy`, `taiga`; their own contract, see "The village variant"), modded (none to draw:
`modded_library.py` makes `structure/modded/leaders_hall.nbt` from the vanilla drawing; the ship step runs it again),
looks (none)

## What it is for
The Trainer Leader's workplace, a Pokémon job: it exists only with Cobblemon installed. Its heart is one of our
**Training Posts** (`aliveworkplace:training_post`, the `TRAINING_POST_POI`). A jobless villager by the post, given a
gold block, becomes a Trainer Leader with that post as her job site (`Stations`: the post is shared by the Trainer
(Poké Ball), the Trainer Leader (gold block) and the Tutor (book)). The leader is the village's strongest trainer (she
starts at Expert, pays three times a trainer's prize; one leader per 64 blocks takes the challenges, `Trainers`); she
works at her post (`TrainerPackages`), and players come to battle her. The Steward asks for the hall when a Trainer
Leader has no workstation (`steward_rules/workplace_leaders_hall.json`: civic zone, only with Cobblemon). It is one of
the twelve buildable workplace copies (`StarterBlueprints.COBBLEMON_WORKPLACES`: in the Blueprint Table only with
Cobblemon), named "Leader's Hall", with its advancement `advancement/built/leaders_hall.json`.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `leaders_hall` | The Training Post (the job block) and the way in to it | In the Table only with Cobblemon; asked for by the Steward's `workplace_leaders_hall` rule (Hamlet rank) |

No upgrade: no `leaders_hall_2` exists and no code or test expects one. Adding a level is a new id and a spec change,
not part of this rebuild.

## Must have (each level)
- **Job block: exactly one `aliveworkplace:training_post`** in the whole drawing
  (`WorkplacesGameTests.workplaceBuilt_leaders_hall` counts exactly one in the blueprint and exactly one built).
  - **No second Training Post**, and no other block that is a job site for a job the Trainer Leader would compete with:
    a second post would be a second trainer's or tutor's job site.
  - **Reachable on foot from the ground outside**, within 2 across and 1 up or down of a walked cell
    (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`).
  - **A villager walking in from the front must get to it.** The build test stands the worker in front of the middle
    of the z = 0 side, 3 blocks out (template about (6, 0, -3) for a 13-wide plan), and he must claim this post in one
    working day after the build (`WorkplacesGameTests.build`). Nothing on the way may trap a 1.95-tall villager: B170 was
    a carpet just inside a doorway two blocks high, where he hit his head stepping up and stood in the doorway for good.
- Beds and residents: **none required.** No code counts beds here; the leader comes from the village. A bed is free
  (the old one has one); if it has one, its head is reachable (`QaBuildReachGameTests`) and, if the recipe records it
  as a furniture spot, the spot matches the template (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`).
- Storage: **none required.** No code reads a chest here. Any chest or barrel must be reachable (`QaBuildReachGameTests`)
  and indoor ones stand against a wall (library-wide furnished-room checks). A barrel is the fisherman's job site.
- Furniture kinds a job or test needs: none. If `blueprint_furniture/leaders_hall.json` records spots, each one's blocks
  must be exactly the template's (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`).
- No entities in the drawing (no villager, frame, painting, armor stand), no calcite, jigsaw or structure void
  (`WorkplacesGameTests.build`: "comes with a villager", "still has calcite, a jigsaw or structure voids").

## Fits
- **Plot** (`WorkplacesGameTests.build`, in the 30 x 30 x 30 `huge_area`: bench at helper (3, 2, 3), supply chests at
  (3, 2, 6/8/10), origin at helper (12, 2, 10)): at most **16 wide (x) x 26 tall (y) x 18 deep (z)**, which keeps 2
  blocks spare inside the area for landscaping. y = 0 sits on the clicked block; the front is the z = 0 side.
- **Materials:** the whole list fits in **81 stacks** (three chests of 27) (`WorkplacesGameTests.build`).
- **Build time:** one builder alone at build delay 2, then the worker walks in and takes the post, all within
  **12,000 ticks** (the test's timeout).
- Declared size: `StarterBlueprints.LEADERS_HALL` (13, 14, 14) today; the ship step changes the `Vec3i` to the new
  drawing (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).
- Front and way in: a door (or open way) on the z = 0 side that the worker walks in by. **No trapdoor at z = 1, y 0-1**:
  `VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow` reads the buildable `structure/leaders_hall.nbt` too.
  Every wooden door leaves a villager 1.95 blocks of room over its step
  (`QaDoorwayBugGameTests.everyListedBuildsDoorwaysLeaveAVillagerRoomOverTheStep`).
- Neighbours: none planned. The Steward places it on a civic plot; the ground under the footprint may step up to
  `Plots.MAX_STEP` (4) blocks, which the builder levels.

## The village variant (`village/<style>_leaders_hall`, five styles)
Village houses share one contract read by template coordinate (`world/VillagePieces`, `world/VillageHouses`,
`village.py`'s `village_house` and `leaders_hall` room, `village_looks.py`'s `leaders_hall` shell). It grows only with
Cobblemon (`VillageHouses.COBBLEMON_HOUSES`; weight 5, `VillageHouses`). Every style keeps:
- **Size:** today 9 x 13 x 11 or 12 (`village_looks.py` `DESIGNS`: (9, 13, 12)).
- **The street joint:** a `minecraft:jigsaw` at **(4, 1, 0)**, `minecraft:building_entrance`; the front row z = 0, y = 0
  places nothing; no structure void left in the world
  (`VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`).
- **The front door at (4, 1, 2)**; **no trapdoor at z = 1, y 0-1** (`VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow`).
- **The room** template x 2-6, z 3-7, y ≤ 4 (`VillagePieces.ROOM_*`), and **the bed foot (6, 1, 6), head (6, 1, 7),
  facing south**: `VillagePieces` finds every village house of ours by that bed head.
- **The fit-out:** exactly **one** Training Post and **no other job block** of any profession
  (`VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`: one `TRAINING_POST`, job sites == 1). Today the post is at
  (2, 1, 7).
- **The villager:** exactly **one** `minecraft:villager` entity, profession `aliveworkplace:trainer_leader` (today at
  (5.5, 1, 5.5)), and no worker-pool spawn jigsaw (`everyVillageTypeCanGrowTheOtherHouses`).
- In every style's house pool, vanilla and Repurposed Structures, with Cobblemon (`VillageCompatTests.repurposedStructuresVillagesGetOurHouses`).

## Rules tests check
Pinned to the drawing:
- `WorkplacesGameTests.workplaceBuilt_leaders_hall` (generator `builds`): no entities, calcite, jigsaws or structure
  voids; exactly one Training Post; ≤ 81 stacks; built by one builder; a Trainer Leader waiting in front walks in and
  claims that post as his job site; within 12,000 ticks.
- `QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`: the post, and every chest, barrel and bed
  head, walked to from outside.
- `VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow`: no trapdoor at z = 1, y ≤ 1 (buildable and village).
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: size equals the declared size; every block
  buildable.
- `QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`,
  `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds` (a standing lamp stands on a block whose top holds
  it; no dirt path or farmland under a solid block), `QaChairFacingGameTests` (seats beside a table face it),
  `QaDoorwayBugGameTests` (doorways 1.95 tall), `FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`.
- Modded copy: `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`,
  `ModdedLibraryCompatTests.everyModdedCopyCanBeBuiltWithThePacksMods` (same size as the vanilla drawing),
  `QaCarvedLibraryCompatTests.everyModdedCopyKeepsItsJobSites` (the post at the same cell): regenerate with `modded_library.py`.
- Refresh: the old drawing is in `blueprint_history/leaders_hall/`; `QaRefreshHistory1028GameTests.qaEveryShippedOlderDrawingIsOfferedARefresh`
  expects a building standing as an old drawing to be offered the new one (the new drawing must differ).
- Village variant: the tests in its section above.
- By id only (the drawing doesn't matter): `WorkplacesGameTests` rule generator (`workplace_leaders_hall`, civic,
  Hamlet, Cobblemon), `QaWorkplaceCopiesGameTests.theTwelveCopiesAreInTheTableByTheirNames` ("Leader's Hall", only with
  Cobblemon), `QaBuiltAdvancements1116bGameTests.everyShippedBlueprintHasItsAdvancement`.
- The owner's rules (`check.py`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no CHECK line, and
  `holes.py` passes.

## Free
Everything not listed above: shape, plan, size within the plot, materials, colors, style, roof form, where the post
stands (reachable from the front), any bed, chest, seating, arena or extra rooms, lighting, ground and planting, and the
village shell outside its fixed cells.
