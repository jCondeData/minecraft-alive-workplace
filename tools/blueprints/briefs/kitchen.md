# Kitchen: the brief

Family id(s): `kitchen` (one level, never renamed; "Kitchen" in `en_us.json`, both `blueprint.aliveworkplace.kitchen`
and `village_piece.aliveworkplace.kitchen`). Not the Camp Kitchen (`camp_kitchen`, a different family, already rebuilt).
Variants: village (`village/<style>_kitchen` for the five village styles `plains`, `desert`, `savanna`, `snowy`,
`taiga`, saved by `generate.py` through `village.py`'s `staffed_house` with the job `aliveworkplace:chef`; their own
contract, see "The village variant"), modded (none to draw: `modded_library.py` makes `structure/modded/kitchen.nbt`
from the vanilla drawing, and the ship step reruns it), looks (none). The Table's style buttons (`BlueprintStyles`)
restyle the drawing automatically.

## What it is for
The Chef's workplace. The Chef (`ModVillagers.CHEF`) works at a **smoker** (vanilla's Butcher job site, which the Chef
shares; the mod's retired Kitchen Stove is his other job site) and cooks the village's meals: bread, pies, stews,
roasts and, with Cobblemon, snacks and dishes asked for at the hall's board (`craft/ChefWork`, `Chefs`). He takes the
makings from **the chests by his smoker** (and from the Storehouse's and the Butcher's chests) and puts every dish back
into the chests by his smoker; with no chest there he has nowhere to put what he cooks and does nothing
(`ChefWork.choose`: `SupplyContainers.find` empty → no job). Those same chests are part of the village's food store
(`VillageNeeds.store`: the chests by every smoker in the village). Under the building is the cook's **cold larder**, a
cellar four blocks deep reached by a ladder under a hatch (ROADMAP 26.15, `docs/wiki/basements.md`); a Kitchen finished
before it had one is offered "Dig the cellar: Kitchen" by the village's builders (`build/Cellars`, 4 emeralds).

A player picks it at the Blueprint Table (it is in every new village's starting set, `blueprint_unlocks/starting.json`;
questbook chapter "A Chef: the Kitchen", `built("kitchen")`; finishing it grants the hidden advancement
`built/kitchen`). The Steward builds it by himself in a `market` zone when a Chef **or a Butcher** is wanted and has no
workstation (`steward_rules/workplace_kitchen.json`, priority 62, cooldown 2 days), and his renewal of an old Chef's or
Butcher's house rebuilds it as this one (`steward_renewal/chef.json`, `butcher.json`). A jobless villager takes a smoker
by himself (it is a vanilla acquirable job site), as a Chef or a Butcher; a player can also sneak-click a jobless
villager near it with raw meat, raw fish or a potato to make him the Chef there (`work/Stations`, reach 4.5). In world
generation, plains, desert, savanna, snowy and taiga villages grow the village variant (pool weight 2,
`VillageHouses.houses()`) with a Chef already in the job and a chest of food (loot table `chests/village_kitchen`).

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `kitchen` | The Chef's workstation (one smoker), his bed, his chest(s) by the smoker, a dining table with chairs and crockery, and the cold larder: a cellar exactly 4 deep with a way down from inside | In the Table's starting set (`starting.json`); Steward rule `workplace_kitchen`; renewal lists `chef.json`, `butcher.json`; questbook; "Dig the cellar" job (4 emeralds) for a Kitchen standing without its cellar |

**One level only.** There is no `kitchen_2` and none may be drawn: a second level is a new feature (a Java
`StarterBlueprints` entry, upgrade offers) and would change the counted upgrades in
`BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (exactly 64).

## Must have
- **Job block: exactly one `minecraft:smoker`** in the file
  (`WorkplacesGameTests.workplaceBuilt_kitchen`: "hasn't one Smoker", and "job blocks built" counts smokers in the
  built box). **No other job block of any job**, vanilla or the mod's (the `workplaces.py` family contract: "exactly
  one of the job's block; no other vanilla job block"): in particular **no `aliveworkplace:kitchen_stove`** (also the
  Chef's job site, so the arriving Chef might take it and not the smoker), no second smoker, and no barrel, composter,
  lectern, loom, stonecutter, grindstone, cauldron, brewing stand, blast furnace, crafting/fletching/smithing/
  cartography table, bell or any of the mod's workstations, above ground or in the cellar
  (`basements.py`: "no job block in a basement but the job's own"). A furnace, a chest or a campfire is not a job block.
- **The smoker is taken by a Chef walking in from the front.** It is reachable on foot from the ground outside, within
  a player's reach (2 across, 1 up or down) of a walked cell (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`),
  and a Chef waiting in front of the build walks in and holds it as his job site (`workplaceBuilt_kitchen`). If the
  smoker is built in under a solid block (both it and the block above it solid), the villager is sent to **an open
  cell beside it** (feet and head free of collision and fluid, a solid floor under it), trying the side the smoker faces
  first (`work/JobSiteApproach`, B168): so keep at least one such cell beside it, ideally on its facing side.
- **Storage: at least one chest within 8 blocks across (x and z each) and 4 up or down of the smoker**
  (`SupplyContainers.RADIUS` / `VERTICAL`; `ChefWork.choose` cooks nothing without one), and **at least one chest or
  barrel on or above y = 0** (`QaBuilderGaps1105bGameTests.aKitchensDigTheCellarKeepsItsChestsAndLeavesAWalkableWayDown`:
  setup fails with "has no chest or barrel above the ground"). Since no barrel is allowed, that means a chest. Drawn
  empty (builders never fill or copy containers; no loot table in the buildable). Every chest is reachable on foot
  (`QaBuildReachGameTests`; in the cellar, `QaBasementGameTests.everyBasementAndItsChestsCanBeWalkedToFromOutside`).
  Indoor chests stand against a wall, a solid block, a bed or another chest (`QaFurnishedRooms1031GameTests`). More
  chests are free.
- **Beds and residents.** At least **one bed**, the Chef's (the workplace contract in `workplaces.py`: "a bed for the
  worker"; every bed counts for the Steward, `StewardConditions.beds`). Its head is reachable on foot
  (`QaBuildReachGameTests`), its foot has its head one block toward its facing
  (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`), and no bed stands right inside a doorway (B178,
  `check.py`). More beds are free. No villager in the file.
- **The cold larder (basement), exactly 4 deep.** `StarterBlueprints.KITCHEN` is declared with a basement of 4 and is
  in `WITH_BASEMENTS`; the drawing's lowest blocks are at template **y = -4** (the cellar's floor), the room above it
  at y -3..-1, and the ground floor's floor (y = 0) its ceiling. Checks (`BasementGameTests.basementsLoadUnderTheGround`):
  the blueprint's basement is exactly 4, the file is the declared height + 4 tall, there is **a ladder or stairs
  below y = 0**, and **a `minecraft:lantern` below y = 0**. Further:
  - **The way down is a ladder under a closed hatch in the ground floor**
    (`QaBuilderGaps1105bGameTests.assertWayDownWalkable`): a trapdoor at **y = 0**, drawn closed, standing over the
    cellar's footprint; **the two cells above it (y 1 and 2) free of collision** (or ladder); under it a ladder of
    **at least 2 rungs**, each rung on a wall that holds it, down to a floor at the cellar's bottom (y -4 or, through a
    step, at most one higher). Stairs alone fail ("no hatch in the floor over the cellar").
  - The way down comes up through the ground floor: a ladder or stair on y = -1 with room to climb out above it at
    y 0 and 1 (`QaBasementGameTests.everyBasementsWayDownComesUpThroughTheGroundFloor`).
  - **The cellar lies under the building's floor**, with **at least one open floor cell at y = 1 over the cellar**
    (air, with a block under it at y = 0) that is **not** in the hatch's or ladder's column: the dig test puts a
    player's block there and expects it to stay (`aKitchensDigTheCellarKeeps...`: "no open floor over the cellar").
  - No dark spot: every air cell on the basement's layers with a sturdy floor under it and air over it has block light
    above 0 (`QaBasementGameTests.noBasementLeavesADarkSpotForMonsters`).
  - Every cellar chest is within reach (2 across, 1 up or down) of a cell walked to from outside, and the walk gets
    back out (`QaBasementGameTests.everyBasementAndItsChestsCanBeWalkedToFromOutside`).
  - The cellar is dug and built even with "leave the ground" and "foundation" off, and every spot it leaves as air
    under y = 0 is dug out (`BasementGameTests.cellarIsBuiltWithTheGroundLeftAlone`).
  - Cellar-only dig (`Cellars.cellarOnly`): what it rebuilds is every block under y = 0 plus, in the cellar's columns,
    the hatch/air/ladder/stair cells at y = 0 and a ladder at y = 1. After such a dig the whole Kitchen must stand
    exactly as drawn and every chest above ground keeps its contents, so nothing above the ground may depend on a
    cellar block being placed later than it.
- **Furniture spots** (`blueprint_furniture/kitchen.json`, written by `rebuild.py`):
  - The kinds **chair, table and crockery**, at least one spot of each
    (`FurnishedWorkplacesGameTests.everyWorkplaceIsFurnished`, `workplaces()`: `"kitchen" → CHAIR, TABLE, CROCKERY`).
  - **No bed spot** (a workplace's beds stay vanilla), and no spot's cells cover a job block, a container or a bed
    (`FurnishedWorkplacesGameTests.noSpotCoversAJobBlockABedOrAContainer`).
  - Each spot's blocks are exactly the template's blocks there (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`);
    nothing small stands on air (`FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece`); every chair
    stair faces its table, turned and mirrored too (`QaChairFacingGameTests`).
  - Crockery stands within 4 across and 3 up or down of a smoker, furnace, stove or counter spot, or on a table with a
    chair at it (`check.py` rule 3).
- **A way in on the z = 0 side** (the front), on the ground floor, that a villager 1.95 blocks tall walks through
  (`QaDoorwayClearanceGameTests`, `QaDoorwayBugGameTests`); any upper floor or loft holding a bed, chest or the smoker
  is reached by a ladder or stairs from inside (B149, `QaBuildReachGameTests`).
- **No trapdoor in the front walk row:** no `*_trapdoor` at z = 1, y 0 or 1 (B69). Note:
  `VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow` reads the **file's** own y, and this file starts 4
  lower because of the cellar, so the test looks at template y -4 and -3 at z = 1; keep **both** z = 1 at template
  y 0-1 (the rule) and z = 1 at template y -4..-3 (what the test reads) free of trapdoors. The cellar hatch must not
  stand at z = 1.
- **No calcite anywhere**, and no jigsaw, structure void or entity (villager, frame, painting ... none) in the file
  (`WorkplacesGameTests.workplaceBuilt_kitchen`: "still has calcite, a jigsaw or structure voids", "comes with a
  villager"). The test uses calcite to catch an old village house shipped as the buildable; it is a block rule, not a
  look.
- Every block is one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).

## Fits
- **Plot: at most 18 wide (x) x 13 tall (y, above the ground) x 16 deep (z), plus the 4-deep cellar under it.**
  - `WorkplacesGameTests.workplaceBuilt_kitchen` builds it in the 30 x 20 x 30 `huge_area` at helper (12, 2, 10):
    x 12..29, z 10..29 (**width ≤ 18**, depth ≤ 20). Its builder's bench (3, 2, 3) and stock chests (3, 2, 6/8/10)
    stand west of it.
  - `BasementGameTests.cellarIsBuiltWithTheGroundLeftAlone` builds it in the same 20-tall `huge_area` on ground at
    helper y 7, origin (9, 7, 9): the top must stay at helper y 19 or lower, so **height ≤ 13** (blocks built above an
    area's height are never cleared); width ≤ 21, depth ≤ 21. A mound at template **(-2, 0, 6)** must stay (outside
    the build). The stock chest and barrels stand at helper x 1-6, west of it.
  - `QaBuilderGaps1105bGameTests.aKitchensDigTheCellar...` places it in the 30 x 30 x 30 `tall_area` at helper
    (8, 10, 9): width ≤ 22, depth ≤ 21, height ≤ 20.
  - `PlotGameTests.aFerryHouseGoesOnTheShore` ("a Kitchen there") requires a Steward plot for it centred at helper
    (16, 13), turned counter-clockwise (front to the west), inside the `homes` zone x 6..29, z 2..25 with
    `Plots.MARGIN` 2 round it: its **depth** (lying along x when turned) must be **≤ 16**, its width ≤ 19.
  - y = 0 sits on the clicked block; the front is the z = 0 side; the cellar is template y -4..-1.
- **Material budget: at most 81 stacks**, cellar included (`workplaceBuilt_kitchen` stocks three chests of 27 with
  exactly the supply report, each item rounded up to whole stacks). The old drawing is 526 blocks, cellar included.
- **Build time:** `workplaceBuilt_kitchen`: the whole build, then the Chef walking in and taking the smoker, within
  **12,000 ticks** (build delay 2, builders' help off). `cellarIsBuiltWithTheGroundLeftAlone`: the whole build within
  **20,000 ticks** (build delay 2); the cellar dig within **20,000 ticks**. A much bigger build risks these.
- **Declared size:** `StarterBlueprints.KITCHEN` is `workplace("kitchen", 11, 12, 11, 4)` today (the size above the
  ground; basement 4). If the size changes the ship step changes that line (sizes must match;
  `modded_library.py` reads the sizes from that file).
- **Front and way in:** the z = 0 side faces the player who places it and the road of a Steward plot (`Plots` checks
  the front faces out); the way in is there. The arriving Chef waits at helper (18, 2, 7) = template **(6, 0, -3)**,
  three blocks in front of the front edge, and walks in by the front.
- **Neighbours:** a workplace in a Steward `market` zone; the Steward keeps 2 blocks clear round its plot
  (`Plots.CLEAR`, `MARGIN`) and wants the ground under it within 4 blocks of level (`Plots.MAX_STEP`). A renewal takes
  it only where it fits the old house's footprint plus 3 a side (`Renewals.MARGIN`), so a smaller footprint renews more
  old houses (free).

## The village variant (`village/<style>_kitchen`, five styles)
Village houses share one contract that the Java reads by template coordinate (`world/VillagePieces`,
`world/VillageHouses`, `village.py`'s `village_house`, `village_looks.py` header). The designer draws only the look
(`village_looks.py` `DESIGNS["kitchen"]`, today 11 x 12 x 11); `village.py` adds the jigsaw, fit-out, bed and villager.
Every style keeps exactly:
- **The street joint:** a `minecraft:jigsaw` at **(4, 1, 0)**, `north_up`, `minecraft:building_entrance`, final state
  `minecraft:structure_void`; **the front row z = 0 at y = 0 places nothing** (x 0-8 checked), and placing it the way a
  village does leaves no structure void in the world
  (`VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`).
- **The front door at (4, 1, 2)** with a step before it at (4, 0, 1); no trapdoor at z = 1, y 0-1
  (`noVillageHouseHasATrapdoorInItsFrontWalkRow`). The west wall at x = 1.
- **The room:** template x 2-6, z 3-7, y up to 4 (`VillagePieces.ROOM_*`); a house is named by its room, and a hall
  look change rebuilds everything outside the room in another style, keeping the room.
- **The bed: foot (6, 1, 6), head (6, 1, 7), facing south** (`VillagePieces.find`'s reference cell for every village
  house of ours).
- **The fit-out** (`village.py`'s `kitchen`, placed one block in on x and z): the smoker at **(2, 1, 7)**, the chest at
  **(2, 1, 6)** with `LootTable` `aliveworkplace:chests/village_kitchen`, a table at (2, 1, 4) and a chair at (3, 1, 4).
  `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`: "should have one Smoker" and **exactly one job block of
  any job in the whole template** (the look outside the room may add no workstation, no barrel, composter, cauldron
  ... for looks; the old look's oven has no smoker of its own).
- **The villager:** **one villager entity, already a Chef** (`aliveworkplace:chef`), at **(5, 1, 5)** (entity
  position 5.5, 1, 5.5), and **no** worker-pool spawn jigsaw ("should come with a aliveworkplace:chef"). Keep (5, 1, 5)
  and (5, 2, 5) clear air with floor under them.
- No cellar in the village variant (none is read). Every pool lists it, by id
  (`everyVillageTypeCanGrowTheOtherHouses`, which also pins `VillageHouses.houseNames()`'s order;
  `VillageCompatTests.repurposedStructuresVillagesGetOurHouses`). No test pins its outer size; a much bigger house
  grows less often (free).

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `WorkplacesGameTests.workplaceBuilt_kitchen` (the `builds()` generator; `HOUSE` (12, 2, 10), `WORKER` (18, 2, 7),
  `BENCH`, `CHESTS`): no entities, calcite, jigsaws or voids; exactly one smoker in the file and in the built box;
  built from its supply report (≤ 81 stacks); the worker, a Chef with no job site, let go after the build, must hold
  the smoker as his `JOB_SITE` while still a Chef, within 12,000 ticks.
- `BasementGameTests.cellarIsBuiltWithTheGroundLeftAlone`: origin (9, 7, 9) on ground; built exactly, the cellar's
  air dug out, **a ladder at template (2, -1, 4)** (today's top rung; the ship step moves it to the new ladder's top
  rung below y = 0), the mound at (-2, 0, 6) left.
- `BasementGameTests.basementsLoadUnderTheGround`: basement exactly 4, file height = size + 4, a ladder or stairs and a
  `minecraft:lantern` below y = 0.
- `QaBuilderGaps1105bGameTests.aKitchensDigTheCellarKeepsItsChestsAndLeavesAWalkableWayDown`: the Kitchen stood
  without its cellar (cellar cells earth, the y = 0 hatch/ladder/air cells over it cobblestone) is offered "Dig the
  cellar: Kitchen" for 4 emeralds; dug, every block of the whole Kitchen is as drawn, every chest above ground keeps the
  player's diamonds, a gold block on an open y = 1 floor cell over the cellar stays, the way down is a closed hatch
  at y = 0 clear 2 above with ≥ 2 holding ladder rungs to the cellar floor, and the player is told once "... dug the
  cellar under the Kitchen and fitted it out."
- `QaBasementGameTests` (all three: way down through the ground floor, no dark spot, cellar and its chests walked to).
- `PlotGameTests.aFerryHouseGoesOnTheShore`: a Kitchen plot fits at (16, 13), turned counter-clockwise.
- `FurnishedWorkplacesGameTests.everyWorkplaceIsFurnished` (chair, table, crockery) and
  `noSpotCoversAJobBlockABedOrAContainer` (no bed spot; no spot over a job block, container or bed).
- `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`, `everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`,
  `noVillageHouseHasATrapdoorInItsFrontWalkRow` (the buildable's file too, read at file y; see Must have).
- In code, not a test: `VillagePieces` room box and bed-head reference; `village.py` fit-out cells and the villager at
  (5, 1, 5); `JobSiteApproach` (open cell beside a built-in smoker); `ChefWork` / `SupplyContainers` (a chest within 8
  across, 4 up or down of the smoker).

By id only (the drawing doesn't matter beyond the rules above): `WorkplacesGameTests.workplaceRule_kitchen_chef`,
`workplaceRule_kitchen_butcher`; `QaWorkplaceCopiesGameTests` (the name "Kitchen", in the Table; a Chef at his smoker
or a jobless villager asks for one, a Chef without a workstation doesn't hold it);
`QaQuestbookClaims1116eGameTests.qaEveryBuildAQuestSaysToPickIsOnANewVillagesTable`;
`QaBlueprintLibraryVerify1108bGameTests` (starting set); `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`,
`withoutTheModsEveryBuildIsTheVanillaBuild`; `SophisticatedControllerCompatTests` (the storage style must not change a
Kitchen: it is limited by id); `QaBuiltAdvancements1116bGameTests.everyShippedBlueprintHasItsAdvancement`.

Library-wide checks it must pass: `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`,
`B648GameTests` and `QaWindowsAndWards1028BugTests` / `QaBuilders1028GameTests` (no pane or bar joined on one side with
air on the other), `QaFurnishedRooms1031GameTests` (no standing lantern on a room's floor; indoor chests against a
wall), `BlueprintsStayGameTests` (a standing lantern, candle or torch on a block whose top holds its middle; no dirt
path or farmland under a solid block; beds whole), `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`,
`QaBuildReachGameTests`, `QaLedgerFramesHaulGameTests` (frames and paintings hang turned and mirrored),
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`,
`StyleGameTests.chimneysStayWholeInEveryStyle` (only if a campfire stands on a solid stack: it must stay solid in every
style). Modded copies: `ModdedLibraryCompatTests.everyModdedCopyCanBeBuiltWithThePacksMods` (same size as the vanilla
drawing), `QaCarvedLibraryCompatTests.everyModdedCopyKeepsItsJobSites` (the smoker at the same cell): regenerate with
`modded_library.py`. Refresh: the old drawings are in `blueprint_history/kitchen/` already;
`QaRefreshHistory1028GameTests.qaEveryShippedOlderDrawingIsOfferedARefresh` expects a Kitchen standing as an old drawing
to be offered the new one (the new drawing must differ), and `Cellars.asBuilt` digs an old Kitchen's cellar where its
own drawing put it.

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove, furnace, smoker or counter or on a table with a chair, no bed right inside a door; `open_panes`), STYLE.md's
checks and `holes.py`: `rebuild.py --candidate` prints no CHECK line, and `holes.py` passes. Saves stay safe: the ship
step keeps the old drawing in `blueprint_history/` and regenerates the village and modded variants and the furniture
spots (`blueprint_furniture/kitchen.json` and `village/<style>_kitchen.json`) with the family.

## Free
Everything not listed above: shape, plan, roof, materials, colors, storeys, whether the smoker stands free or built in,
where the smoker, chests, bed, table and hatch stand (within the reaches above), the cellar's plan and fit-out beyond
its depth, way down, light and reachable chests, how many beds and chests beyond the minimum, a garden, yard or oven
or none, lighting, and the village variant's look outside its room in each of the five styles.
