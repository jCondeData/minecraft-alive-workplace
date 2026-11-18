# Schoolhouse: the brief

Family id(s): `schoolhouse`, `schoolhouse_2` (never renamed; "Schoolhouse", "Schoolhouse II" in `en_us.json`)
Variants: village (`village/<style>_schoolhouse` for `plains`, `desert`, `savanna`, `snowy`, `taiga`, saved by
`generate.py` through `village.py`'s `staffed_house` with the job `aliveworkplace:teacher`; its own contract, see "The
village variant"), modded (none to draw: `modded_library.py` makes `structure/modded/schoolhouse*.nbt` from the vanilla
drawings and the ship step reruns it), looks (none: `BlueprintStyles` restyles the drawing; tests use Schoolhouse II in
`spruce` and every style, by id).
Not this family: the Cobblemon **School** (`school`, the Move Tutor's) has its own brief.

## What it is for
The village school. A **Teacher** (`ModVillagers.TEACHER`, job sites a **lectern** or a Teacher's Desk) works at the
lectern: every 20 ticks, children within **32 blocks** of it who haven't been to school are told to walk to it, and
every child within **5 blocks** of it (measured from the lectern block's bottom centre) has lessons, while the Teacher
walks to within 2.5 blocks of it (`school/TeacherWork`). A child with 2,400 ticks of lessons is schooled and starts
their first job a level up (`Schools`). A player makes a Teacher by handing a villager next to the lectern a book
(`Stations`; the questbook names "the Schoolhouse's lectern" and "a book", `chapters.py`). A lectern is also the
Librarian's, so the Steward gives a free lectern **inside a finished Schoolhouse (its box + 2)** as a Teacher's job,
not a Librarian's (`StewardJobs.BUILDING_JOBS`). A finished Schoolhouse (any level, any style) gives homes within **48
blocks of its origin** the **school** service (`services/school.json`; class needs read it: Artisan, Burgher, Noble).
The Steward builds it in a **civic** zone when a Teacher has no workstation (`workplace_schoolhouse.json`, priority 62)
or when the village has **3 or more children** and no Schoolhouse of any level (`schoolhouse.json`, priority 45, at
most one); his renewal of an old Teacher's house rebuilds it as this one (`steward_renewal/teacher.json`). The village
map counts it as a learning building (`VillageMaps`, by id). It starts **locked** on the Blueprint Table
(`QaQuestbookClaims1116eGameTests`: LOCKED at first; it comes as a blueprint from village loot chests); the questbook
asks for it (`built("schoolhouse")`), and finishing each level grants `built/schoolhouse` / `built/schoolhouse_2`. Its
schoolroom is furnished (37.5): benches, a desk and pictures, which with CobbleFurnies become **Poké Ball desks** (the
owner's choice) and with Handcrafted its benches. Level II is an upgrade on the same spot, origin and front.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `schoolhouse` | The schoolroom: the Teacher's lectern, room for a class within 5 blocks of it, benches, a desk, pictures | Locked at first; Steward rules `workplace_schoolhouse` and `schoolhouse` (civic, Hamlet+) |
| II | `schoolhouse_2` | Nothing the code reads (the old one added a fenced schoolyard); keeps one lectern and the furniture kinds | Upgrade of I (`BlueprintUpgrades`: id + `_2`); sold by the builder who finished I (`UpgradeOffers`) |

## Must have (each level)
- **Job block: exactly one `minecraft:lectern`** (the village variant's test and the questbook name it; `Stations`
  turns a book there into a Teacher; `BUILDING_JOBS` reads a lectern). **No other job block of any job**: no Teacher's
  Desk (a second Teacher job site), no second lectern, barrel, composter, smoker, loom, stonecutter, grindstone,
  cauldron, brewing stand, crafting/fletching/smithing/cartography table or any of the mod's workstations (each would
  take a villager for another job). A bell is not a job block (no code reads one here: free).
- **The lectern is reachable on foot from outside** through the front way in, within 2 across / 1 up or down of a
  walked cell (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`), with a cell beside it
  the Teacher stands on.
- **The class:** standable floor (sturdy floor, two clear blocks above) **within 5 blocks of the lectern**, reached on
  foot from the front door, where children called from 32 blocks away end up (`TeacherWork.CLASS`, `RADIUS`; children
  walk to within 2 blocks of the lectern). Several children at once.
- **Beds and residents: none required.** Any bed drawn counts as a village bed and gets **no furniture spot** (a
  workplace's beds stay vanilla, `FurnishedWorkplacesGameTests.noSpotCoversAJobBlockABedOrAContainer`).
- **Storage: none.** No code reads a chest here, and the showcase scene `furnished_workplaces` expects Schoolhouse I to
  hold **0 chests or barrels** (`FurnishedWorkplacesScene.storage`); a chest added means the ship step changes that
  count. Any chest stands against a wall, a solid block or a bed (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`).
- **Furniture spots** (`blueprint_furniture/<id>.json`, written through `rebuild.py`):
  - **Every level has at least one `bench`, one `desk` and one `picture` spot**
    (`FurnishedWorkplacesGameTests.everyWorkplaceIsFurnished`).
  - **Every spot is one the furniture mods fill**: with Handcrafted, CobbleFurnies and Beautify every bench is
    Handcrafted's and **every desk is `cobblefurnies:poke_ball_desk`**; only a wall picture may stay vanilla
    (`FurnishedWorkplacesCompatTests.theHealingCenterAndSchoolhouseGetPokeBallDesks`, I and II). Every styled level's
    plan has at least one desk and all are Poké Ball desks (`QaStyledPokeDesks1104dCompatTests.aStyledSchoolhouseIsPlannedWithPokeBallDesks`),
    on the same spots as the plain build (`QaFurnishedViews1104cGameTests.aStyledHealingCenterIsFurnishedOnTheSameSpotsAsThePlainOne`).
    Without CobbleFurnies installed the desks plan vanilla (`withoutCobbleFurniesTheDesksFollowTheOrder`).
  - No spot covers the lectern, a bed or a container (`noSpotCoversAJobBlockABedOrAContainer`); each spot's blocks are
    exactly the template's (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); nothing floats
    (`FloatingFurnishingsGameTests`); **no standing lantern on a room's floor** (`QaFurnishedRooms1031GameTests.noLanternStandsOnARoomsFloor`;
    its exemption "schoolhouse 5, 11, 4" is the old belfry's and covers nothing new); chairs face their tables
    (`QaChairFacingGameTests`).
- **A front door** on the ground floor at the z = 0 side, walkable by a villager 1.95 tall (`QaDoorwayClearanceGameTests`);
  any upper floor with something reachable is reached by ladder or stairs from inside (B149).
- **No calcite, jigsaw, structure void or entity**; every block one a builder can place
  (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).

## Kept cells (the upgrade)
- II has the same origin and front as I and is at least its size in x, y and z; **at least 60 % of I's non-air blocks
  stay the same block in the same cell** (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`).
- The lectern (and any chest or bed) stays in the same cell as the same block in II (a builder never breaks containers
  or job blocks, `MaterialRules.isProtected`).
- II has **no basement** (`QaBenchMovesCellars1104bGameTests.aBuildWithNoBasementIsNeverOfferedACellar`).

## Fits
- Plot (y = 0 sits on the clicked block; the front is the z = 0 side):
  - Level I: **at most 13 wide x 16 tall x 13 deep** (`BuilderGameTests.buildsSchoolhouse` at helper (9, 2, 9) in the
    22 x 18 x 22 `big_area`, the builder's barrels at x 1-5, z 4).
  - Level II: **at most 21 wide x 18 tall x 27 deep** (planned at helper (9, 2, 3) in the 30 x 20 x 30 `huge_area`,
    `QaStyledPokeDesks1104dCompatTests`).
  - The showcase places I at x 0, 20, 40, 60 beside the Library and Healing Center: I at most 20 wide.
- Declared sizes: `StarterBlueprints.SCHOOLHOUSE` (`11, 14, 12`), `SCHOOLHOUSE_2` (`16, 14, 12`) today; if a size changes
  the ship step changes those `Vec3i`s (sizes must match).
- Material budget: Level I fits the four barrels `buildStarter` stocks, **at most 108 stacks**.
- Build time: Level I with exactly its materials within **30,000 ticks** (`buildsSchoolhouse`).
- Front and way in: the z = 0 side faces the player who places it and the street of a Steward civic plot; the door is
  there; the upgrade keeps that front.
- Neighbours: a civic-zone build; the Steward keeps 2 blocks clear round its plot and wants the ground within 4 blocks
  of level (`Plots`). A renewal takes it only where it fits the old house's footprint plus 3 a side (`Renewals.MARGIN`).

## The village variant (`village/<style>_schoolhouse`, five styles)
The shared village-house contract (`village.py` `village_house`, `village_looks.py` header, `world/VillagePieces`); the
designer draws only the look (`village_looks.py` `DESIGNS["schoolhouse"]`, today 9 x 17 x 12):
- **Street joint:** a `minecraft:jigsaw` at **(4, 1, 0)**, `north_up`, `minecraft:building_entrance`, final state
  structure_void; the front row z = 0 at y = 0 places nothing
  (`VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`).
- **Front door at (4, 1, 2)** facing south, a step at (4, 0, 1); no trapdoor at z = 1, y 0-1
  (`noVillageHouseHasATrapdoorInItsFrontWalkRow`).
- **The room:** x 2-6, z 3-7, up to y 4 (`VillagePieces` room box), walls at x 1, x 7, z 2, z 8.
- **Bed: foot (6, 1, 6), head (6, 1, 7), facing south.**
- **Fit-out** (`village.py` `schoolhouse`, moved in by one): the lectern at **(2, 1, 7)** facing east, two bench stairs
  at (4, 1, 5) and (4, 1, 6) facing west, a bookshelf at (2, 1, 4), a flower pot at (2, 2, 4). **Exactly one lectern
  and exactly one job block of any job in the whole template** (`VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`).
- **The villager:** one entity, already a **Teacher**, at **(5, 1, 5)**, no worker-pool spawn jigsaw; keep (5, 1, 5)
  and (5, 2, 5) air with floor under it.
- Pool weight 2 in every vanilla village (`VillageHouses.houses()`; `houseNames()` order pinned by the same test).

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- Showcase `FurnishedWorkplacesScene` "03_schoolroom": camera at (5.5, 3.2, 3.2) looking at (5.5, 1.5, 8) from I's
  origin, framing the benches facing the desk; Schoolhouse I storage count 0.
- `QaFurnishedRooms1031GameTests.NOT_A_ROOM` entries "schoolhouse 5, 11, 4" and "schoolhouse_2 5, 11, 4" (the old
  belfry lantern): drop them, never add one.
- The village variant's cells above.

Built in the world: `BuilderGameTests.buildsSchoolhouse` (every block right). By id only:
`WorkplacesGameTests.workplaceRule_schoolhouse_teacher`; `StewardCivicRulesGameTests.threeChildrenGetASchoolhouse` (any
tier counts as built); `QaStewardCivicBoundariesGameTests` (2 children: none, 3: one); `ServiceGameTests.eachServiceIsFoundByItsBuild`;
`QaClassPerksGameTests.aStyledUpgradeCountsAsItsBase` (spruce Schoolhouse II gives "school");
`QaFurnishedViews1104cGameTests.everyHealingCenterAndSchoolhouseInEveryStyleHasPokeBallDesks` (at least 30 styled
Healing Centers and Schoolhouses); `QaQuestbookClaims1116eGameTests`; `ModdedLibraryGameTests`; showcase `steward_civic`.

Library-wide: `B648GameTests` / `QaWindowsAndWards1028BugTests` (no pane or bar run ending against air),
`BlueprintsStayGameTests`, `QaDoorwayClearanceGameTests`, `QaBuildReachGameTests`, `QaMirroredBuildsGameTests`,
`QaTurnedFramesGameTests`, `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`,
`StyleGameTests.chimneysStayWholeInEveryStyle` (only if a campfire stands on a solid stack),
`QaRefreshHistory1028GameTests.qaEveryShippedOlderDrawingIsOfferedARefresh`.

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair; `open_panes`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no
CHECK line for either level and `holes.py` passes. `blueprint_history/schoolhouse*/` and `modded/schoolhouse*/` already
hold old drawings; the ship step adds the current ones and regenerates the village and modded variants and the spots.

## Free
Everything not listed above: shape, plan, roof, materials, colors, storeys, a bell, belfry, porch or yard or none, what
II adds beyond keeping 60 % of I and its lectern, how many benches, desks and pictures beyond one each, where the
lectern stands within the rules, beds, lighting, and the village variant's look outside its room.
