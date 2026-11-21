# School: the brief

Family id(s): `school` (one level, never renamed; "School" in `en_us.json`, `blueprint.aliveworkplace.school` and the
village piece's name; `QaWorkplaceCopiesGameTests` reads "School")
Variants: village (`village/<style>_school` for the five village styles `plains`, `desert`, `savanna`, `snowy`,
`taiga`, saved by `generate.py` through `village.py`'s `staffed_house` with the job `aliveworkplace:tutor`; grown only
with Cobblemon, `VillageHouses.COBBLEMON_HOUSES`; its own contract, see "The village variant"), modded (none to draw:
`modded_library.py` makes `structure/modded/school.nbt` from the vanilla drawing and the ship step reruns it), looks
(none: `BlueprintStyles` restyles the drawing automatically).
Not this family: the **Schoolhouse** (`schoolhouse`, `schoolhouse_2`, the Teacher's) has its own brief.

## What it is for
The **Move Tutor's** workplace (Cobblemon only). A Tutor (`ModVillagers.TUTOR`, job sites a Tutor's Desk or a
**Training Post**) stands at his post and teaches the player's Pokémon moves they can't learn by levelling, for
CobbleDollars or emeralds (`tutor/Tutors`, `MoveLessons`; the lesson screen closes when the player walks more than 8
blocks from the tutor, `Tutors.REACH`). The building gives one Tutor his Training Post and a bed. A Training Post is
shared: a book next to it makes a Tutor, a Poké Ball a Trainer, a gold block a Trainer Leader (`Stations`), and a
Tutor already in the job takes it by himself. It is in the Blueprint Table (and the creative tab) only with Cobblemon
(`StarterBlueprints.COBBLEMON_WORKPLACES`). The Steward builds it in a **civic** zone when a Tutor has no workstation
(`steward_rules/workplace_school.json`, priority 62, cooldown 2 days, needs Cobblemon), and his renewal of an old
Tutor's house rebuilds it as this one (`steward_renewal/tutor.json`). A finished School (like a Schoolhouse) gives
homes within **48 blocks of its origin** the **school** service (`services/school.json`; class needs read it). Finishing
it grants the hidden advancement `built/school`. In world generation, Cobblemon villages grow the village variant (pool
weight 2) with a Tutor already in the job.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `school` | The Tutor's Training Post, his bed | Table with Cobblemon; Steward rule `workplace_school` (civic, Hamlet+); renewal `tutor.json` |

**One level only.** No `school_2` may be drawn: a second level is a new feature and would change the counted upgrades
in `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (exactly 64).

## Must have
- **Job block: exactly one `aliveworkplace:training_post`** in the file (`WorkplacesGameTests.workplaceBuilt_school`:
  "hasn't one Training Post", and exactly one built inside the box). **No other job block of any job**: no Tutor's
  Desk (a second Tutor job site), no Leader's Podium, no lectern, barrel, composter, smoker, loom, stonecutter,
  grindstone, cauldron, brewing stand, crafting/fletching/smithing/cartography table, bell or any of the mod's
  workstations (`workplaces.py` contract: "exactly one of the job's block ... no other vanilla job block"). A chest is
  not a job block.
- **The post is reachable on foot from outside** through the way in, within 2 across / 1 up or down of a walked cell
  (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`), and a Tutor waiting in front walks in
  and claims it (`workplaceBuilt_school`). A player stands by it for lessons, so room to stand within 8 blocks of it.
- **Beds and residents: at least one bed**, the Tutor's (`workplaces.py`: "a bed for the worker"; every bed counts for
  the Steward, `StewardConditions.beds`). Its head is reachable on foot (`QaBuildReachGameTests`) and its foot has its
  head one block towards its facing (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`). No bed right
  inside a doorway (`check.py`). More beds are free.
- **Storage: none required** (no Tutor code reads a chest). Any chest is drawn empty (builders never fill containers),
  is reachable on foot, and stands against a wall, a solid block or a bed (`QaFurnishedRooms1031GameTests`, if the
  furniture spots make it a furnished build). **No barrel** (a Fisherman's job block).
- **Furniture spots**: no kind is required by any test for this id. Every piece drawn is recorded as a spot
  (`rebuild.py`); each spot's blocks are exactly the template's (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`);
  nothing floats (`FloatingFurnishingsGameTests`); every chair faces its table, turned and mirrored too
  (`QaChairFacingGameTests`).
- **A way in on the z = 0 side** on the ground floor that a villager 1.95 tall walks through (`QaDoorwayClearanceGameTests`;
  no carpet just inside a 2-tall doorway, B170). Any upper floor or loft holding the bed or post is reached by ladder or
  stairs from inside (B149).
- **No trapdoor in the front walk row**: no `*_trapdoor` at z = 1, y = 0 or 1
  (`VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow` reads `structure/school.nbt`).
- **No calcite, jigsaw or structure void, and no entity** in the file (`workplaceBuilt_school`: "still has calcite, a
  jigsaw or structure voids", "comes with a villager").
- Every block is one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).

## Fits
- **Plot: at most 18 wide x 18 tall x 20 deep.** `WorkplacesGameTests.workplaceBuilt_school` builds it in the
  30 x 20 x 30 `huge_area` at helper (12, 2, 10) (x 12..29, y 2..19, z 10..29); the builder's bench and chests stand at
  x 3. y = 0 sits on the clicked block; the front is the z = 0 side.
- **Material budget: at most 81 stacks** (three chests of 27, each item rounded up to whole stacks). The old drawing is
  759 blocks.
- **Build time:** the whole build, then the Tutor walking in and claiming the post, within **12,000 ticks** (build delay
  2, builders' help off).
- **Declared size:** `StarterBlueprints.SCHOOL` is `workplace("school", 17, 11, 13)` today; if the size changes the
  ship step changes that line (sizes must match); `modded_library.py` reads the sizes from that file.
- **Front and way in:** the z = 0 side faces the player who places it and the road of a Steward civic plot. The
  waiting Tutor stands at helper (18, 2, 7) = template **(6, 0, -3)**, three blocks before the front edge, and must
  walk in by the front.
- **Neighbours:** a workplace in a Steward civic zone; the Steward keeps 2 blocks clear round its plot (`Plots.CLEAR`)
  and wants the ground within 4 blocks of level (`Plots.MAX_STEP`). A renewal takes it only where it fits the old
  house's footprint plus 3 a side (`Renewals.MARGIN`), so a smaller footprint renews more old houses (free).

## The village variant (`village/<style>_school`, five styles)
The shared village-house contract (`village.py` `village_house`, `village_looks.py` header, `world/VillagePieces`);
the designer draws only the look (`village_looks.py` `DESIGNS["school"]`, today 11 x 16 x 11):
- **Street joint:** a `minecraft:jigsaw` at **(4, 1, 0)**, `north_up`, `minecraft:building_entrance`, final state
  structure_void; the whole front row z = 0 at y = 0 places nothing
  (`VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`).
- **Front door at (4, 1, 2)** facing south, a step at (4, 0, 1); no trapdoor at z = 1, y 0-1.
- **The room:** x 2-6, z 3-7, up to y 4 (`VillagePieces` room box: a house is named by its room; a hall look change
  rebuilds everything outside it), bounded by walls at x 1, x 7, z 2, z 8.
- **Bed: foot (6, 1, 6), head (6, 1, 7), facing south.**
- **Fit-out** (`village.py` `school`, moved in by one): the Training Post at **(2, 1, 7)** facing east, bookshelves at
  (2, 1, 6), (2, 2, 6), (2, 1, 4), a flower pot at (2, 2, 4). **Exactly one Training Post and exactly one job block of
  any job in the whole template** (`VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`).
- **The villager:** one entity, already a **Tutor**, at **(5, 1, 5)**, no worker-pool spawn jigsaw (same test); keep
  (5, 1, 5) and (5, 2, 5) air with floor under it.
- Listed in Cobblemon villages' pools and Repurposed Structures' (`VillageCompatTests`, by id).

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `WorkplacesGameTests.workplaceBuilt_school` (`HOUSE` (12, 2, 10), `WORKER` (18, 2, 7), `BENCH`, `CHESTS`): built from
  its supply report; the Tutor (no job site) let go must hold the one Training Post as his `JOB_SITE`, still a Tutor.
  It must reach DONE within the test's 12,000 ticks: at most about 900 placed blocks, and no overhang, barge or eave
  over another roof (a 1,410-block design timed out there, its builder stuck on a barge stair above a lower roof).
- `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`, `everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`,
  `noVillageHouseHasATrapdoorInItsFrontWalkRow` (the village variant's cells above; the buildable's front walk row).

By id only: `WorkplacesGameTests.workplaceRule_school_tutor` (civic, Hamlet, held back without Cobblemon),
`ServiceGameTests.eachServiceIsFoundByItsBuild` ("school" gives the school service), `QaWorkplaceCopiesGameTests`,
`ModdedLibraryGameTests`, `VillageCompatTests.repurposedStructuresVillagesGetOurHouses`.

Library-wide: `B648GameTests` / `QaWindowsAndWards1028BugTests` (no pane or bar run ending against air),
`BlueprintsStayGameTests`, `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`, `QaBuildReachGameTests`,
`QaMirroredBuildsGameTests`, `QaTurnedFramesGameTests`, `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`,
`StyleGameTests.chimneysStayWholeInEveryStyle` (only if a campfire stands on a solid stack).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair, no bed right inside a door; `open_panes`), STYLE.md's checks and `holes.py`:
`rebuild.py --candidate` prints no CHECK line and `holes.py` passes. Saves: there is **no `blueprint_history/school/`
yet**; the ship step must save the old drawing there (`history.py`) and regenerate the village and modded variants and
`blueprint_furniture/school.json` and `village/<style>_school.json`.

## Free
Everything not listed above: shape, plan, roof, materials, colors, rooms and storeys, a courtyard or none, where the
post, bed and any chest stand (within the rules), extra beds, lighting, and the village variant's look outside its room.
