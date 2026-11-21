# Ball Workshop: the brief

Family id(s): `ball_workshop` (one level, never renamed; "Ball Workshop" in `en_us.json`,
`blueprint.aliveworkplace.ball_workshop` and `village_piece.aliveworkplace.ball_workshop`; hidden advancement
`built/ball_workshop`). Variants: village (`village/<style>_ball_workshop` for `plains`, `desert`, `savanna`, `snowy`,
`taiga`, saved by `generate.py` through `village.py`'s `staffed_house` with the job `aliveworkplace:ball_smith`; see "The
village variant"), modded (none to draw: `modded_library.py` makes `structure/modded/ball_workshop.nbt` from the vanilla
drawing and the ship step reruns it; `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`,
`withoutTheModsEveryBuildIsTheVanillaBuild`), looks (none). The Table's style buttons restyle the drawing automatically.
ROADMAP item: 50.12 (Milestone 50, same method as 49).

## What it is for
The Ball Smith's workplace (Cobblemon only). The Ball Smith (`ModVillagers.BALL_SMITH`) works at a **smithing table**
(the vanilla Toolsmith's block, which he shares with the Toolsmith and the Tinkerer: an apricorn picks him,
`work/Stations`). All shift he takes apricorns and ball metals from **the chests near his smithing table**, makes Poké
Balls with Cobblemon's recipes a batch at a time, and puts the balls back in those chests (`smith/BallSmithWork`: every
read and write goes through `SupplyContainers.find(level, bench)`, the chests within 8 blocks across and 4 up or down of
the table; with none he has nothing to make and nowhere to put it). A Steel or Fire Pokémon partner sparks at the table
and carries the batch to the first of those chests (`work/Partners`, `PartnerShows`). Players set his orders by talking
to him (`BallSmiths`). The building is in the Blueprint Table only with Cobblemon
(`StarterBlueprints.COBBLEMON_WORKPLACES`); the Steward builds it in a `workshops` zone (from Hamlet) when a Ball Smith is
wanted with no workstation and Cobblemon is loaded (`steward_rules/workplace_ball_workshop.json`, priority 62, cooldown
2 days), and renews an old Ball Smith's house as this one (`steward_renewal/ball_smith.json`). Villages with Cobblemon
grow the village variant (`VillageHouses.COBBLEMON_HOUSES`, weight 2) with a Ball Smith already in the job.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `ball_workshop` | The Ball Smith's smithing table, the chests he works from, his bed | Table with Cobblemon; Steward rule `workplace_ball_workshop`; renewal `ball_smith.json` |

**One level only**: a `ball_workshop_2` would become an upgrade by name and change
`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`'s count of exactly 64. That is a new feature, not a rebuild.

## Must have
- **Job block: exactly one `minecraft:smithing_table`** in the file and in the built box
  (`WorkplacesGameTests.workplaceBuilt_ball_workshop`: "hasn't one Smithing Table", "job blocks built"). Its facing is
  free.
- **No Ball Workbench** (`aliveworkplace:ball_workbench`, the Ball Smith's other job site) **and no other job block of
  any job**, vanilla or the mod's: no barrel, composter, lectern, loom, stonecutter, grindstone, cauldron, brewing stand,
  blast furnace, smoker, fletching or cartography table, bell, crafting table, and none of the mod's workstations
  (`workplaces.py` contract; a second job site takes another villager in, or the Ball Smith claims the wrong one and the
  build test fails on his `JOB_SITE`). An anvil, a chest or a campfire is not a job block.
- **The smithing table is reachable on foot from outside**, within a player's reach of a cell a villager walks to
  (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`), and a Ball Smith waiting in front of
  the house walks in and claims it as his `JOB_SITE` (`workplaceBuilt_ball_workshop`). He walks to within 2.5 blocks of
  it every batch (`BallSmithWork`), so it belongs on the ground floor or on a floor reached by stairs or a ladder from
  inside.
- **Storage: at least one chest within 8 blocks across (x and z) and 4 up or down of the smithing table**
  (`SupplyContainers.RADIUS` / `VERTICAL`, read by `BallSmithWork`: his apricorns, metals and finished balls all live
  there). Any number from one up. Drawn empty (no loot table: builders never fill or copy containers). Every chest stands
  against a wall, a solid block, a bed or its other half (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`)
  and is reachable on foot (`QaBuildReachGameTests`). No barrel (the Fisherman's job block).
- **Beds and residents: at least one bed**, the Ball Smith's (`workplaces.py`: "a bed for the worker"; it counts as a
  village bed, `StewardConditions.beds`). Head reachable on foot, head one block toward its facing
  (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`), not right inside a doorway (`check.py`).
- **Furniture spots:** no kind is required by any test (not a home). Every piece drawn is recorded as a spot
  (`rebuild.py` regenerates `blueprint_furniture/ball_workshop.json`); each spot's blocks match the template
  (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); nothing small stands on air
  (`FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece`); no standing lantern on a room's floor
  (`QaFurnishedRooms1031GameTests.noLanternStandsOnARoomsFloor`); every chair stair faces its table, turned and mirrored
  too (`QaChairFacingGameTests`).
- **A way in on the z = 0 side**, ground floor, that a 1.95-tall villager walks through (`QaDoorwayClearanceGameTests`;
  B170: no carpet, slab or stair under a two-high lintel; B197: no carpet, slab or stair anywhere under a ceiling only two
  blocks high). Any upper floor or loft holding the bed, a chest or the table is reached by a ladder or stairs from
  inside (B149, `QaBuildReachGameTests`).
- **No trapdoor at z = 1, y 0 or 1** (`VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow` reads
  `structure/ball_workshop.nbt` by name).
- **No calcite, jigsaw, structure void or entity** in the file (`workplaceBuilt_ball_workshop`: "comes with a villager",
  "still has calcite, a jigsaw or structure voids").
- Every block placeable by a builder (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`); every
  countable material has a plural in `en_us.json` (`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`).

## Fits
- **Plot: at most 18 wide x 28 tall x 20 deep.** `workplaceBuilt_ball_workshop` builds it in `huge_area` (30 x 30 x 30)
  at helper (12, 2, 10), so x 12..29 and z 10..29; its bench and three chests stand at x 3 to the west. y = 0 sits on the
  clicked block; the front is the z = 0 side.
- **Material budget: at most 81 stacks** (the test's three chests of 27: "needs N stacks"). The old one is 558 blocks.
- **Build time: within 12,000 ticks** (`WorkplacesGameTests.timeout`), the build plus the Ball Smith walking in and
  claiming the table (build delay 2, the clock held in working hours).
- **Declared size:** `StarterBlueprints.BALL_WORKSHOP` is `workplace("ball_workshop", 14, 17, 11)` today; the ship step
  changes it to the new size (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`), with its javadoc and the
  wiki's description (`docs/wiki/workplace-looks.md`), which describe the old looks.
- **Front and way in:** the Ball Smith waits at helper (18, 2, 7) = template **(6, 0, -3)**, in front of the z = 0 side,
  and must walk in by the front. A Steward plot turns the z = 0 side to the nearest road or the hall (`Plots`). Nothing
  of the plan should reach in front of z = 0.
- **Neighbours:** a workshop among others in a Steward `workshops` zone; no party walls, no rows. The Steward needs 2
  blocks of zone margin (`Plots.MARGIN`), 2 blocks clear of every other build (`Plots.CLEAR`) and ground within 4 of
  level under the footprint (`Plots.MAX_STEP`); a renewal takes it only where it fits the old house's footprint plus 3
  a side (`Renewals.MARGIN`). So every block wider or deeper makes the rule and the renewal succeed less often: keep it
  a modest workshop.

## The village variant (`village/<style>_ball_workshop`, five styles)
Shared contract (`village.py` `village_house`, `world/VillagePieces`); the designer draws only the look,
`village_looks.py` `DESIGNS["ball_workshop"]` (one function drawn in each style's palette `P`, today 13 x 16 x 11);
the code adds the joint, the room's fit-out, the bed and the villager:
- **Street joint:** `minecraft:jigsaw` at **(4, 1, 0)**, `north_up`, `building_entrance`, final state structure_void; the
  whole row z = 0 at y = 0 is structure void (the ground it lands on;
  `VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`).
- **Front door at (4, 1, 2)** facing south, its step at (4, 0, 1); no trapdoor at z = 1, y 0-1.
- **The room:** x 2-6, z 3-7, y up to 4 (`VillagePieces.ROOM_*`), kept as it is when a player gives the house another
  style's outside, so every style's outside must fit round the same room (walls at x 1, x 7, z 2, z 8). A house is
  recognised when half its room and 60% of one style's outside stand (`ROOM_MATCH`, `OUTSIDE_MATCH`).
- **Bed foot (6, 1, 6), head (6, 1, 7), facing south** (`VillagePieces` finds houses by this bed).
- **Fit-out** (`village.py` `ball_workshop`, moved in by one): the smithing table at **(2, 1, 7)**, a chest at
  **(2, 1, 6)** facing east with the loot table `aliveworkplace:chests/village_ball_workshop` (the village one is
  stocked), an anvil at (2, 1, 4). **Exactly one job block in the whole template**
  (`VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`: "should have one Smithing Table", "has N job blocks").
- **The villager:** one, already a Ball Smith, at **(5, 1, 5)**; (5, 1, 5) and (5, 2, 5) clear air over a floor block
  at (4, 0, 5) (else he suffocates); no worker-pool jigsaw ("should come with a aliveworkplace:ball_smith").
- Listed by id in the pools: `everyVillageTypeCanGrowTheOtherHouses` (only with Cobblemon),
  `VillageCompatTests.repurposedStructuresVillagesGetOurHouses`.

## Rules tests check
- `WorkplacesGameTests.workplaceBuilt_ball_workshop` (HOUSE (12, 2, 10), WORKER (18, 2, 7), BENCH (3, 2, 3), CHESTS x 3,
  12,000 ticks): no entity, calcite, jigsaw or structure void; exactly one smithing table in the file; built from its
  supply report in at most 81 stacks; exactly one smithing table in the built box; the worker still a Ball Smith and
  holding that table as `JOB_SITE`.
- `VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow` (the blueprint and the five village files),
  `everyVillageTypeCanGrowTheOtherHouses`, `everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`.
- By id or name only: `WorkplacesGameTests.workplaceRule_ball_workshop_ball_smith` (the rule, `workshops` zone, Hamlet,
  Cobblemon), `QaWorkplaceCopiesGameTests.theTwelveCopiesAreInTheTableByTheirNames` ("Ball Workshop", loads, in the table
  only with Cobblemon), `PieceLooksGameTests.everyLookSentenceReads`, `ModdedLibraryGameTests`.
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable` (the size equals the entry's).
- Library-wide: `QaBuildReachGameTests`, `QaDoorwayClearanceGameTests`, `BlueprintsStayGameTests`,
  `QaFurnishedRooms1031GameTests`, `QaChairFacingGameTests`, `FloatingFurnishingsGameTests`, `FurnitureSpotGameTests`,
  `B648GameTests.b648NoShippedGlassEndsInTheAir` and `QaWindowsAndWards1028BugTests` (no pane or bar joined on one side
  with air on the other), `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`,
  `StyleGameTests.chimneysStayWholeInEveryStyle` (only if a campfire stands on a solid stack).
- The owner's rules (`check.py`: posts at fence, wall and pane corners, lamps on tables or posts or hung, crockery only
  by a stove or on a table with a chair, no bed right inside a door; `open_panes`), STYLE.md's checks and `holes.py`:
  `rebuild.py --candidate` prints no CHECK line and `holes.py` passes. The buildable has no baseline line; the five
  village files have (`owner_rules_baseline.json`: desert 4, the others 2), which a redrawn village look must clear. The
  ship step keeps the old drawings in `blueprint_history/` and regenerates the modded copy, the village variants and
  `blueprint_furniture/ball_workshop.json`.

## Free
Everything else: shape, plan, roof, materials, colors, storeys, towers, lofts or yards or none, what shows the trade from
the street, where the table, chests and bed stand (within the rules above), extra beds and chests, an anvil or none, and
the village variant's look outside its room.
