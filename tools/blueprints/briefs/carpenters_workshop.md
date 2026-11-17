# Carpenter's Workshop: the brief

Family id(s): `carpenters_workshop` (one level, never renamed; "Carpenter's Workshop" in `en_us.json`, both
`blueprint.aliveworkplace.carpenters_workshop` and `village_piece.aliveworkplace.carpenters_workshop`)
Variants: village (`village/<style>_carpenters_workshop` for the five village styles `plains`, `desert`, `savanna`,
`snowy`, `taiga`, saved by `generate.py` through `village.py`'s `staffed_house` with the job `aliveworkplace:carpenter`;
Repurposed Structures' villages reuse these five, `VillageHouses.MODDED_HOUSE_POOLS`; the village variant has its own
contract, see "The village variant"), modded (none to draw: `modded_library.py` makes
`structure/modded/carpenters_workshop.nbt` from the vanilla drawing, turning chests into Carved Wood chests, and the
ship step reruns it), looks (none). The Table's style buttons (`BlueprintStyles`) restyle the drawing automatically.

## What it is for
The Carpenter's workplace. The Carpenter (`ModVillagers.CARPENTER`) works at a **crafting table** (or the mod's
Carpenter's Bench; both are his job sites) and makes what the village's builders are waiting for: he saws spare logs
into planks, stairs, doors, fences, chests and the like, and carries them to the chests the builder takes from
(`craft/CrafterWork`, wiki `crafter.md`). The building gives one Carpenter that table, a bed, and a chest of his own by
the table: whatever is left in his bag when a job falls through goes into the storage within **8 blocks across and 4
up or down** of his table (`CrafterWork.tidy`/`roomAtHome`, `SupplyContainers.find`; with none there it is dropped on
the ground, B773). A player picks it at the Blueprint Table (it is in every new village's starting set,
`blueprint_unlocks/starting.json`); the Steward builds it by himself in a `workshops` zone when a Carpenter is wanted
and has no workstation (`steward_rules/workplace_carpenters_workshop.json`, priority 62, cooldown 2 days), and his
renewal of an old Carpenter's house rebuilds it as this one (`steward_renewal/carpenter.json`). A crafting table is not
in the game's `acquirable_job_site` tag, so a jobless villager does not take it by himself: a villager who already is a
Carpenter does, or the hall's People page (Job ▾) or a sneak-click with planks makes one the Carpenter there (B635).
Questbook chapter 2 asks the player to build one (`chapters.py`, `built("carpenters_workshop")`), and finishing it
grants the hidden advancement `built/carpenters_workshop`. In world generation, plains, desert, savanna, snowy and
taiga villages grow the village variant (pool weight 2, `VillageHouses.houses()`) with a Carpenter already in the job
and a chest of wood (loot table `chests/village_carpenters_workshop`).

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `carpenters_workshop` | One Carpenter's workstation (a crafting table), his bed, his chest by the table | In the Table's starting set (`starting.json`); Steward rule `workplace_carpenters_workshop`; renewal list `carpenter.json`; questbook chapter 2 |

**One level only.** There is no `carpenters_workshop_2` and none may be drawn: a second level is a new feature (a Java
`StarterBlueprints` entry, upgrade offers) and would change the counted upgrades in
`BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (exactly 64).

## Must have
- **Job block: exactly one `minecraft:crafting_table`** in the file, the vanilla block itself, not a modded or
  wood-coloured table (`WorkplacesGameTests.workplaceBuilt_carpenters_workshop`: "hasn't one Crafting Table";
  `B635GameTests.aBuiltWorkshopsTableIsAFreeCarpenterBlock` reads the hall's line "Carpenter · 1 free Crafting Table",
  so a second crafting table breaks it). **No other job block of any job**, vanilla or the mod's: no Carpenter's Bench
  (also the Carpenter's job site, and one a jobless villager takes by himself: the arriving Carpenter must hold the
  *crafting table*, the test checks his `JOB_SITE` is that table), no Blueprint Table or Builder's Bench, and no
  barrel, composter, smoker, lectern, loom, stonecutter, grindstone, cauldron, brewing stand, blast furnace,
  fletching/smithing/cartography table, bell or any of the mod's workstations (each takes a villager for another job;
  `workplaces.py` header: "exactly one of the job's block ... no other vanilla job block"). A chest or furnace is not a
  job block.
- **The table is reachable on foot from outside**, through the way in, within a player's reach (2 across, 1 up or down)
  of a cell a villager walks to (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`), and a
  Carpenter waiting in front of the building walks in and claims it (`workplaceBuilt_carpenters_workshop`, see "Rules
  tests check").
- **Beds and residents.** At least **one bed**, the Carpenter's (the family contract in `workplaces.py`: "a bed for
  the worker"; every bed counts as a village bed for the Steward, `StewardConditions.beds`, and the Table shows
  "Beds: N"). Its head is reachable on foot (`QaBuildReachGameTests`) and its foot has its head one block towards its
  facing (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`). No bed right inside a doorway (B178,
  `check.py`). More beds are free.
- **Storage.** At least **one chest within 8 across and 4 up or down of the crafting table** (`SupplyContainers.RADIUS`
  / `VERTICAL`): the Carpenter's own chest, where `CrafterWork` tidies his bag and, when the builder has no chests by
  his table, delivers what he made. Drawn empty (builders never fill or copy containers; no loot table in the
  buildable). Every chest is reachable on foot (`QaBuildReachGameTests`). More chests are free; **no barrel** (a
  Fisherman's job block). With Carved Wood installed the modded copy turns each chest into the build's wood's chest
  (automatic).
- **Furniture spots.** No kind is required by any test for this id (it is not a home: `FurnishedHomesGameTests` does
  not list it). Every furniture piece the drawing has is recorded as a spot (`rebuild.py`), and each spot's blocks are
  exactly the template's blocks there (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); nothing small
  stands on air, vanilla or furnished (`FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece`); every
  chair stair faces its table, turned and mirrored too (`QaChairFacingGameTests`).
- **A way in on the z = 0 side** (the front), on the ground floor, that a villager 1.95 blocks tall walks through:
  every wooden doorway has room for him (`QaDoorwayClearanceGameTests`; B170: a carpet just inside a 2-tall doorway
  stopped a worker for good). Any upper floor or loft holding a bed, chest or the table is reached by a ladder or
  stairs from inside (B149, `QaBuildReachGameTests`).
- **No trapdoor in the front walk row**: no block named `*_trapdoor` at z = 1, y = 0 or 1
  (`VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow`, which reads `structure/carpenters_workshop.nbt` and
  every village variant).
- **No calcite anywhere in the buildable drawing**, and no jigsaw, structure void or entity (villager, frame, painting
  ... none) in the file (`WorkplacesGameTests.workplaceBuilt_carpenters_workshop`: "still has calcite, a jigsaw or
  structure voids", "comes with a villager"). A block rule a test checks, not a look: the test uses calcite to catch an
  old village house shipped as the buildable.
- Every block is one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).

## Fits
- **Plot: at most 18 wide x 18 tall x 16 deep.** Two tests build it in the 30 x 20 x 30 `huge_area`:
  `WorkplacesGameTests.workplaceBuilt_carpenters_workshop` at helper (12, 2, 10), so x 12..29, y 2..19, z 10..29
  (18 x 18 x 20; its builder's bench and chests stand at x 3, west of it), and
  `B635GameTests.aBuiltWorkshopsTableIsAFreeCarpenterBlock` at helper (9, 2, 6), with a jobless villager (no AI) standing
  at helper (12, 2, 22) = template **(3, 0, 16)** and the Village Hall block at helper (15, 2, 24) = template
  **(6, 0, 18)**: the build must cover neither, so **depth at most 16** (z 0..15). y = 0 sits on the clicked block; the
  front is the z = 0 side.
- **The table within 20 blocks of template (6, 0, 18)** (B635's hall reaches 20 blocks, `VillageHalls.freeStations`
  uses a straight-line distance; a table in a far front corner of a wide build falls outside it).
- **Material budget: at most 81 stacks** (`workplaceBuilt_carpenters_workshop` stocks three chests of 27 with exactly
  what the supply report says is missing, each item rounded up to whole stacks). The old drawing is 459 blocks.
- **Build time:** `workplaceBuilt_carpenters_workshop`: the whole build, then the Carpenter walking in and claiming the
  table, within **12,000 ticks** (build delay 2, builders' help off). `B635`: the whole build within **9,000 ticks**
  (build delay 1, free materials, the builder walking from a table at helper (2, 2, 2)). A much bigger build risks both.
- **Declared size:** `StarterBlueprints.CARPENTERS_WORKSHOP` is `workplace("carpenters_workshop", 13, 11, 11)` today;
  if the size changes the ship step changes that line (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: sizes
  match), and `modded_library.py` reads the sizes from that file.
- **Front and way in:** the z = 0 side faces the player who places it and the road of a Steward plot; the way in is
  there. The arriving Carpenter waits at helper (18, 2, 7) = template **(6, 0, -3)**, three blocks before the front
  edge, and must walk in by the front.
- **Neighbours:** a workplace in a Steward `workshops` zone; the Steward keeps 2 blocks clear round its plot
  (`Plots.CLEAR`) and wants the ground under it within 4 blocks of level (`Plots.MAX_STEP`). A renewal takes it only
  where it fits the old house's footprint plus 3 a side (`Renewals.MARGIN`), so a smaller footprint renews more old
  houses (free).

## The village variant (`village/<style>_carpenters_workshop`, five styles)
Village houses share one contract that the Java reads by template coordinate (`world/VillagePieces`,
`world/VillageHouses`, `village.py`'s `village_house`, `village_looks.py` header). The designer draws only the look
(`village_looks.py` `DESIGNS["carpenters_workshop"]`, today 13 x 11 x 11); `village.py` adds the jigsaw, fit-out, bed
and villager. Every village variant keeps it exactly:
- **The street joint:** a `minecraft:jigsaw` at **(4, 1, 0)**, orientation `north_up`, name and target
  `minecraft:building_entrance`, final state `minecraft:structure_void`; **the whole front row z = 0 at y = 0 places
  nothing** (it stays the ground the house landed on; x 0-8 are checked), and placing it the way a village does leaves
  **no structure_void block in the world** (`VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`).
- **The front door at (4, 1, 2)** (a door's lower half facing south; that test reads "door" as the block at (4, 1, 2)
  not being air), with a step before it at (4, 0, 1); no trapdoor at z = 1, y 0-1.
- **The room:** template **x 2-6, z 3-7, y up to 4** is "the room" (`VillagePieces.ROOM_MIN_X ... ROOM_MAX_Y`): a house
  is named by its room (the house and style whose room blocks stand best, over 50 %), and a hall look change rebuilds
  everything *outside* the room in another style, keeping the room. So the room is the shell's inside at ground level,
  bounded by the west wall at x = 1, the front wall at z = 2, an east wall at x = 7 and a back wall at z = 8. The
  carpenter's fit-out is what tells this house's room from the other houses' rooms; keep it.
- **The bed: foot (6, 1, 6), head (6, 1, 7), facing south.** `VillagePieces.find` takes the plains Builder's Workshop's
  bed head as the reference cell for finding *every* village house of ours, so this bed stays exactly there in every
  style.
- **The fit-out** (`village.py`'s `carpenters_workshop`, moved in by one): exactly one crafting table at **(2, 1, 7)**
  and a chest at **(2, 1, 6)** with `LootTable` `aliveworkplace:chests/village_carpenters_workshop`; today also a log at
  (2, 1, 4) and a fence post at (2, 1, 3) (a sawhorse). `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`: "should
  have one Crafting Table", and **exactly one job block of any job in the whole template** ("has N job blocks"): the look
  outside the room may add no workstation either (no barrel, composter, cauldron, crafting table ... for looks).
- **The villager:** **one villager entity, already a Carpenter** (`aliveworkplace:carpenter`), standing at
  **(5, 1, 5)** (entity position 5.5, 1, 5.5), and **no** worker-pool spawn jigsaw (`everyVillageTypeCanGrowTheOtherHouses`:
  "should come with a aliveworkplace:carpenter"); (4, 0, 5) is the style's floor block. Keep (5, 1, 5) and (5, 2, 5)
  clear air with floor under it, or he suffocates.
- Every pool lists it: `everyVillageTypeCanGrowTheOtherHouses` (vanilla pools; also pins `VillageHouses.houseNames()`'s
  order), `VillageCompatTests.repurposedStructuresVillagesGetOurHouses` (Repurposed Structures' pools), by id only.
- No test pins the village variant's outer size; vanilla's village generator refuses a piece that overlaps another, so
  a much bigger house grows less often (free).

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `WorkplacesGameTests.workplaceBuilt_carpenters_workshop` (the `builds()` generator; `WORKER`, `HOUSE`, `BENCH`,
  `CHESTS`): the house at helper (12, 2, 10); the waiting Carpenter at helper (18, 2, 7), i.e. template (6, 0, -3).
  Built from its supply report, then the worker (a Carpenter with no job site) is let go and must hold the crafting
  table as his `JOB_SITE` while still a Carpenter; exactly one crafting table built inside the box.
- `B635GameTests.aBuiltWorkshopsTableIsAFreeCarpenterBlock` (`WORKSHOP` (9, 2, 6), `HALL` (15, 2, 24), Pip at
  (12, 2, 22), `BLUEPRINT_TABLE` (2, 2, 2)): the built table is a `CRAFTING_TABLE_POI`, listed on the hall's Free job
  blocks page as a Carpenter's, the Job ▾ line reads "Carpenter · 1 free Crafting Table", and one click makes Pip the
  Carpenter at it.
- `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses` and
  `everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor` (placed at helper (2, 2, 2), clipped to x
  -1..10, y 0..11, z -1..11; the 9 ground cells x 0-8 at z 0 stay dirt; the door at (4, 1, 2)).
- In code, not a test: `VillagePieces` room box and bed-head reference; `village.py` fit-out cells and the villager at
  (5, 1, 5) (above).

By id only (the drawing doesn't matter beyond the rules above): `WorkplacesGameTests.workplaceRule_carpenters_workshop_carpenter`,
`wantedJobWithNoFreeBlockWishesItsBuilding` (no Carpenter's Workshop wished when nobody wants one);
`QaWorkplaceCopiesGameTests` (the name "Carpenter's Workshop", in the Table);
`QaQuestbookClaims1116eGameTests.qaEveryBuildAQuestSaysToPickIsOnANewVillagesTable`;
`QaBlueprintLibraryVerify1108bGameTests.aNewVillageStartsWithTheWikisSetAndTheRestLockedOrRare` (starting set);
`ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`, `withoutTheModsEveryBuildIsTheVanillaBuild`;
`PieceLooksGameTests.everyLookSentenceReads` (the house name).

Library-wide checks it must pass: `B648GameTests` and `QaWindowsAndWards1028BugTests` / `QaBuilders1028GameTests`
(no pane or bar joined on one side with air on the other, as stored and as the game joins it),
`QaFurnishedRooms1031GameTests` (no standing lantern on a room's floor; every indoor chest against a wall, a solid
block, a bed or another chest), `BlueprintsStayGameTests`, `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`,
`QaBuildReachGameTests`, `QaLedgerFramesHaulGameTests` (frames and paintings hang turned and mirrored),
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `StyleGameTests.chimneysStayWholeInEveryStyle`
(only if a campfire stands on a solid stack: it must stay solid in every style).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair, no bed right inside a door; `open_panes`), STYLE.md's checks and `holes.py`:
`rebuild.py --candidate` prints no CHECK line, and `holes.py` passes. Saves stay safe: the ship step keeps the old
drawing in `blueprint_history/` (`history.py`; `blueprint_history/carpenters_workshop/` and
`blueprint_history/village/<style>_carpenters_workshop/` exist already) and regenerates the village and modded variants
and the furniture spots (`blueprint_furniture/carpenters_workshop.json` and `village/<style>_carpenters_workshop.json`)
with the family.

## Free
Everything not listed above: shape, plan, roof, materials, colors, how many rooms and storeys, an open shed or yard or
none, a second door, what the carpenter's trade shows from the street, where the table, chest and bed stand inside the
building (within the rules above, and the village variant's fixed cells), how many beds and chests beyond the minimum,
lighting, and the village variant's look outside its room in each of the five styles.
