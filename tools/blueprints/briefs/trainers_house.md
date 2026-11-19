# Trainer's House: the brief

Family id(s): `trainers_house` (one level, never renamed; "Trainer's House" in `en_us.json`,
`blueprint.aliveworkplace.trainers_house` and `village_piece.aliveworkplace.trainers_house`; `QaWorkplaceCopiesGameTests`
reads the name; advancement `built/trainers_house` by id). Variants: village (`village/<style>_trainers_house` for
`plains`, `desert`, `savanna`, `snowy`, `taiga`: `village_looks.py` `DESIGNS["trainers_house"]` plus `village.py`'s
`trainers_house` fit-out, see "The village variant"), modded (none to draw: `modded_library.py` makes
`structure/modded/trainers_house.nbt` from the vanilla drawing; the ship step reruns it), looks (none: the Table's styles,
`BlueprintStyles`, restyle the drawing by themselves). Cobblemon's (Milestone 50.12): without Cobblemon nothing about it
changes for the player (it is not in the Table and villages don't grow it).

## What it is for
The Pokémon **Trainer's** workplace and home. A Trainer (`ModVillagers.TRAINER`) works at a **Training Post**
(`aliveworkplace:training_post`, the `TRAINING_POST_POI`): by day he walks to it and strolls within 4 blocks of it
(`TrainerPackages`: `WalkToJobSite`, `StrollAroundPoi` radius 4), turning to look at players within 6, and players
right-click him there to battle (`Trainers`, `TrainerBattles`); he ranks up Novice to Master from every battle. A jobless
villager takes a free Training Post **by himself** and becomes a Trainer (only with Cobblemon:
`VillagerGoalPackagesMixin`, B697); a player standing a villager within 4.5 blocks of it (`Stations.REACH`) and
sneak-right-clicking with a Poké Ball makes a Trainer, a gold block a **Trainer Leader**, a book a **Move Tutor** (the
same block is all three jobs' site, `Stations`). The guide promises "Its one bed is the worker's own"
(`docs/guide/trainers-house-build.md`). It is in the Blueprint Table only with Cobblemon
(`StarterBlueprints.COBBLEMON_WORKPLACES`); the Steward builds it in a `civic` zone from Hamlet rank when a Trainer has
no workstation and Cobblemon is loaded (`steward_rules/workplace_trainers_house.json`, priority 62, cooldown 2 days), and
renews an old Trainer's house as this one (`steward_renewal/trainer.json`). Villages with Cobblemon grow the village
variant more than any other house (`VillageHouses`, weight 6, `COBBLEMON_HOUSES`), so most villages have a Trainer to
battle.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `trainers_house` | One Trainer's Training Post, room round it for him and his challengers, and his bed | Table with Cobblemon; Steward rule `workplace_trainers_house` (civic, Hamlet+); renewal `trainer.json` |

**One level only.** A `trainers_house_2` would be a new feature and change the count in
`BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (exactly 64). The Table's twelve workplace copies stay twelve
(`QaWorkplaceCopiesGameTests.theTwelveCopiesAreInTheTableByTheirNames`).

## Must have
- **Job block: exactly one `aliveworkplace:training_post`** in the file and in the built box
  (`WorkplacesGameTests.workplaceBuilt_trainers_house`: "hasn't one Training Post", "job blocks built: ..."). **No other
  job block of any job** anywhere: no barrel, lectern, composter, cauldron, smoker, blast furnace, grindstone, loom,
  stonecutter, brewing stand, bell, crafting/fletching/smithing/cartography table, nor any of the mod's workstations
  (Leader's Podium, Tutor's Desk, Shop Counter, Mailbox...): each would pull a villager into another job (`workplaces.py`
  contract). A target block, hay, bookshelf or chest is not a job block. The Training Post faces a horizontal direction
  and turns with the build (`QaTurnedModBlocksGameTests`).
- **The post reached on foot from the front way in**, within a player's reach (2 across, 1 up or down) of a walkable cell
  (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`). A Trainer waiting in front of the house
  walks in and must hold it as his `JOB_SITE` (`workplaceBuilt_trainers_house`), so the walk from outside to a cell
  beside it must work for a 1.95-tall villager.
- **Room to battle by the post** (code, not a test): the Trainer strolls within 4 blocks of it and players come up to him
  there; give the post standable floor round it, enough for him and a player or two.
- **Beds and residents: exactly one bed, the Trainer's** (the guide's promise above; it counts as a village bed,
  `StewardConditions.beds`). Whole (head and foot, the head one block toward its facing), its head reachable on foot
  (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`, `QaBuildReachGameTests`), not right inside a
  doorway (`check.py`). More beds would change the guide's text in the ship step.
- **Storage: none required** (no code reads the Trainer's chests). Any chest drawn: empty, reachable on foot, standing
  against a wall, a solid block, a bed or another chest (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`).
  No barrel (the Fisherman's job block).
- **Furniture spots** (`blueprint_furniture/trainers_house.json`, written from the recipe's `furniture.json` by
  `rebuild.py`): no kind required by a test (not a home). Today only the bed is a spot. Every spot's blocks are exactly the
  template's there (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); no spot covers the post, a chest or
  another spot's bed; nothing small on air (`FloatingFurnishingsGameTests`); no standing lantern on a room's floor
  (`QaFurnishedRooms1031GameTests.noLanternStandsOnARoomsFloor`); chair stairs face their table (`QaChairFacingGameTests`).
- **A way in on the z = 0 side** that a 1.95-tall villager walks through (`QaDoorwayClearanceGameTests`' rule, `check.py`;
  B170: no carpet just inside a 2-tall doorway). Any upper floor holding the bed, a chest or the post is reached by stairs
  or a ladder from inside (B149).
- **No trapdoor at z = 1, y 0 or 1** (`VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow` reads
  `structure/trainers_house.nbt`).
- **No calcite, jigsaw, structure void or entity** in the file (`workplaceBuilt_trainers_house`: "comes with a villager",
  "still has calcite..."); every block one a builder can place
  (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).

## Fits
- **Plot: at most 18 wide (x) x 20 deep (z) x 18 tall.** `workplaceBuilt_trainers_house` builds it in the 30 x 20 x 30
  `huge_area` at helper (12, 2, 10): x 12..29, z 10..29, y 2..19 under the area's top; the bench at (3, 2, 3), the
  builder at (5, 2, 3) and the three supply chests at x 3 are to the west.
- **Material budget: at most 81 stacks** (three chests of 27; "needs N stacks"). The old drawing is 453 blocks.
- **Build time: within 12,000 ticks** at build delay 2, the build plus the Trainer walking in and claiming the post (the
  clock is held in working hours). The Leader's Hall (~1,000 blocks) needed 9,343 ticks and the Trade Hall (~1,400) 11,391,
  so both were moved to a 20,000 timeout; this one stays at 12,000. Keep it under about 900 placed blocks.
- **Declared size:** `StarterBlueprints.TRAINERS_HOUSE = workplace("trainers_house", 13, 10, 13)` (w, h, d) today; the ship
  step sets the new one (and the guide's infobox).
- **Front and way in:** y = 0 sits on the clicked block; the front is the z = 0 side. The waiting Trainer stands at helper
  (18, 2, 7) = template **(6, 0, -3)**, three blocks in front of the z = 0 edge, and must walk in by the front. When the
  Steward places it, its front faces the nearest road on the plan, else the hall (`Plots`); a renewal puts the front
  where the old door was (`Renewals`).
- **Neighbours:** a Steward `civic` plot, 2 blocks kept clear round it (`Plots.CLEAR`, `MARGIN`), ground within 4 of
  level (`Plots.MAX_STEP`); a renewal must fit the old house's footprint plus 3 a side (`Renewals.MARGIN`), so smaller
  renews more. No party walls.

## The village variant (`village/<style>_trainers_house`, five styles, pool weight 6)
The shared village-house contract (`village_looks.py` header, `village.py` `village_house`, `world/VillagePieces`); the
designer draws only the look (`DESIGNS["trainers_house"]`, today 13 x 10 x 10):
- **Street joint:** `minecraft:jigsaw` at **(4, 1, 0)**, `north_up`, `building_entrance`; the whole row z = 0 at y = 0 is
  structure void, left as the ground
  (`VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`).
- **Front door at (4, 1, 2)**, a step at (4, 0, 1); no trapdoor at z = 1, y 0-1.
- **The room:** x 2-6, z 3-7, walls at x 1, x 7, z 2, z 8; the fit-out (`village.py` `trainers_house`, moved in by one)
  puts the **Training Post at (2, 1, 7) facing east**, targets at (2, 1, 4) and (2, 2, 4), a bookshelf at (2, 1, 5) with a
  flower pot on it.
- **Bed foot (6, 1, 6), head (6, 1, 7), facing south** (`VillagePieces`' reference bed).
- **The villager: a jobless one from the worker pool** (not a staffed Trainer): a `minecraft:jigsaw` at **(4, 0, 5)**,
  `up_north`, pool `aliveworkplace:village/<style>/workers`, final state the floor; **no entity in the file**; (4, 1, 5)
  and (4, 2, 5) open so he doesn't suffocate. He takes the post by himself
  (`VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`: "should spawn a jobless villager from the worker pool",
  "should have one Training Post", **exactly one job block** in the whole template).
- In the pools only with Cobblemon (`everyVillageTypeCanGrowTheOtherHouses` without it;
  `VillageCompatTests.pokemonVillageHousesSwitchOffLeavesThemOut`: it stays in the pools when `pokemonVillageHouses` is
  off). A ladder to every furnished upper floor. No test pins its outer size.

## Rules tests check
- `WorkplacesGameTests.workplaceBuilt_trainers_house` (HOUSE (12, 2, 10), WORKER (18, 2, 7), BENCH (3, 2, 3), CHESTS
  x 3, 12,000 ticks): no entity, calcite, jigsaw or structure void; exactly one Training Post in the file; built from its
  supply report in at most 81 stacks; exactly one Training Post built; the worker, still a Trainer, holds it as `JOB_SITE`.
- `VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow` (the buildable file and the five village files),
  `everyVillageTypeCanGrowTheOtherHouses`, `everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`.
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable` (size equals the declared one, every block
  buildable) and `starterUpgradesKeepMostOfTheirBase` (no new level).
- By id only: `WorkplacesGameTests.workplaceRule_trainers_house_trainer` (civic, Hamlet, Cobblemon only),
  `QaWorkplaceCopiesGameTests` ("Trainer's House"), `VillageCompatTests.pokemonVillageHousesSwitchOffLeavesThemOut`,
  `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`, `QaCarvedLibraryCompatTests.everyModdedCopyKeepsItsJobSites`
  (the modded copy keeps the post in the same cell).
- Library-wide: `QaBuildReachGameTests`, `QaDoorwayClearanceGameTests`' rule, `BlueprintsStayGameTests` (lamps on a block
  that holds them, no path under a solid block, beds whole), `QaMirroredBuildsGameTests`, `QaTurnedModBlocksGameTests`,
  `QaFurnishedRooms1031GameTests`, `QaChairFacingGameTests`, `FloatingFurnishingsGameTests`, `FurnitureSpotGameTests`,
  `B648GameTests` / `QaWindowsAndWards1028BugTests` / `QaBuilders1028GameTests` (no pane or bar run ending in air),
  `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `StyleGameTests.chimneysStayWholeInEveryStyle`
  (only with a campfire on a stack).
- The owner's rules (`check.py`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no CHECK line. The
  family is not in `owner_rules_baseline.json`, so it must come out clean. The ship step saves the old drawings under
  `blueprint_history/` (the village ones exist at `ac8dc5225`) so standing houses can be refreshed, and regenerates the
  village variants, the modded copy and `blueprint_furniture/trainers_house.json`.

## Free
Everything else: shape, plan, roof, materials, colors, storeys, rooms, whether the post stands indoors, under cover or in
the open (within the reaches above), sparring targets, a ring or yard or none, chests, lighting, and the village
variant's look outside its room.
