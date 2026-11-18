# Village Hall: the brief

Family id(s): `village_hall`, `village_hall_2`, `village_hall_3` (never renamed; "Village Hall", "Village Hall II",
"Village Hall III" in `en_us.json` under `blueprint.aliveworkplace.<id>`)
Variants: village (none: no `village/<style>_village_hall`, `village.py` draws no hall), modded (none to draw:
`modded_library.py` makes `structure/modded/village_hall{,_2,_3}.nbt` from the vanilla drawings and the ship step reruns
it), looks (none: no `outside/` or `looks/` copy). The Table's style buttons (`BlueprintStyles`) restyle the drawing
automatically; no test names a styled hall.
Not this family, though they also hold the hall block: `research/town_hall`, `guildhall`, `royal_hall`.

A flagship family (ROADMAP 51.1): two designers (A and B) work from this one brief.

## What it is for
The home of the **Village Hall block** (`aliveworkplace:village_hall`), the block that makes a group of villagers a
village the mod looks after: names, needs, rank, beds and food counts, the treasury, the chronicle, edicts, quests, the
Steward. Everyone living within 64 blocks of the block (`VillageHalls.RADIUS`) is its village. A player picks the
Village Hall at the Blueprint Table (it is in every new village's starting set, `blueprint_unlocks/starting.json`, and
on a Table with no village yet) and hands it to a builder; when the builder places the hall block, it becomes a hall
like one a player put down: owned by the player who handed over the blueprint, with "founded by" in its chronicle
(`BuilderWork` → `VillageHallBlock.built`). The player then right-clicks the block **inside** to open the hall's screen
(questbook chapter 1, "Build the Village Hall": "right-click the Village Hall block inside"). Later a **Steward** is
appointed to it: the player sneak-clicks a villager standing by the hall with a City Plan (`Stewards.appoint`: within
5.5 blocks of the block's centre); the hall block is his job site (`ModVillagers.STEWARD`, the `village_hall` point of
interest, one place). The village's **bell** is its meeting point: villagers gather there, festivals gather there
(`Festivals.square`: the meeting bell nearest the hall), the raid alarm rings it (`VillageRaids.ringTheBell`), guards
rally to the nearest bell (`GuardRally`) and builders lay paths towards it (`Paths.destination`). The levels are civic
upgrades: II gives the village a council room, III makes it the village's landmark (questbook: "everyone nearby will
know where your village's heart is").

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `village_hall` | The hall block indoors with room in front of it, a meeting bell, seating (benches) for the village | Starting set (`starting.json`); quest "Build the Village Hall" (reward unlocks II) |
| II | `village_hall_2` | Level I plus a council room: a table with chairs (the quest: "a meeting room, with a council table") | Unlocked by Level I's quest reward (`/workplace unlock ... village_hall_2`); cost = what II needs more than I (`BlueprintUpgrades`, automatic) |
| III | `village_hall_3` | Level II plus whatever makes it the village's landmark (free; the quest's icon is a bell) | Unlocked by Level II's quest reward; cost automatic |

**Exactly three levels.** No `village_hall_4`: `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` counts exactly 64
starter upgrades. Upgrades are found by name (`<id>_2` after `<id>`, `BlueprintUpgrades`) and built on the old level's
spot and turn, only where they differ.

## Must have (each level)
- **Job block: exactly one `aliveworkplace:village_hall`**, at template **(6, 1, 10), facing north** (towards the
  z = 0 front), in all three levels, with no block data in the file
  (`VillageHallBuildGameTests.everyLevelKeepsTheHallAtItsHeart`: one hall, at that cell, facing north, `nbt == null`).
  **This cell is fixed, not a pin to move:** an upgrade and a refresh (36.21) build over a standing hall, and a builder
  never breaks a hall block (it holds the village's records, `MaterialRules.facingFree`); a new drawing with its hall
  anywhere else would leave old halls with a second hall block, a second village. The quest also promises "the Village
  Hall block stays where it is while they build". So y = 1 is the hall's floor level at that spot: (6, 0, 10) is solid
  floor under it.
- **The hall block indoors and reachable on foot** from the front door: a villager walks in by the shut front door to
  template (6, 1, 8) (`QaDoorwayClearanceGameTests.aVillagerWalksInByTheVillageHallsShutFrontDoor`), and the Steward
  stands within 5.5 blocks of it to be appointed and walks to it to work (a job site): keep standable floor with 2 blocks
  of headroom right in front of its face, at (6, 1, 9), reached from the door. A player must reach it to right-click it.
- **The meeting bell: a `minecraft:bell` at template (6, 4, 0) in all three levels**
  (`VillageHallBuildGameTests.everyLevelKeepsTheHallAtItsHeart`: "has no bell under the porch" is its failure text, but
  the test checks only that a bell is at that cell). It hangs or stands there as a working bell (any `attachment`; a
  hanging bell needs the block above it, a standing one the block below). Villagers must be able to gather near it: open
  ground reachable on foot from the street within a few blocks of it. **Every other bell** (Level III may add one) is a
  meeting point too: the village's meeting bell is the one **nearest the hall block**, so any extra bell must be farther
  from (6, 1, 10) than (6, 4, 0) is (about 10.4 blocks), and any bell guards may run to should be reachable on foot.
- **Beds and residents: none required.** The hall is a workplace, not a home (nobody is counted living in it). Beds are
  free, but each one counts as a village bed and gets no furniture spot (`FurnishedWorkplacesGameTests`: "a workplace's
  beds stay vanilla"); a bed's head must be reachable on foot (`QaBuildReachGameTests`).
- **Storage: none required.** No job reads a chest here. Chests or barrels are free, drawn empty, reachable on foot
  (`QaBuildReachGameTests`), and indoors against a wall, a solid block, a bed or another chest (`QaFurnishedRooms1031GameTests`).
- **No other workstation unless you mean it:** any vanilla or mod job block (lectern, composter, crafting table, barrel,
  ...) is a real job site that pulls a villager into that job. Bookshelves, tables and seats are not job blocks.
- **Furniture spots** (`FurnishedWorkplacesGameTests.everyWorkplaceIsFurnished`, by kind, at least one each):
  - Level I: `bench` and `hanging_pot` (a pot hung from a ceiling or beam; its spot's support is the ceiling).
  - Levels II and III: `bench`, `hanging_pot`, `table` and `chair` (the council room).
  - No spot covers the hall block, the bell (both points of interest), a bed or a container
    (`FurnishedWorkplacesGameTests.noSpotCoversAJobBlockABedOrAContainer`).
  - Every furniture piece in the drawing is recorded as a spot (`rebuild.py`), each spot's blocks exactly the
    template's there (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); nothing small stands on air
    (`FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece`); every chair stair faces its table, turned and
    mirrored too (`QaChairFacingGameTests`); no seat right inside a 2-high doorway (B173).
- **A front door on the z = 0 side** at template **(6, 1, 3)**, its lower half a wooden door whose `facing` points into
  the building (south, +z), with a solid lintel block right above its upper half at (6, 3, 3) and nothing raising the
  floor in the cells just outside and inside it, (6, 1, 2) and (6, 1, 4)
  (`QaDoorwayClearanceGameTests.aCarpetMovedUnderTheVillageHallsAndSupplyShopsLintelIsCaught`: a door at (6, 1, 3); it
  passes the 1.95-block headroom sweep as shipped, and must *fail* it once a carpet is put at (6, 1, 4), so the opening
  is exactly 2 high with a lintel, not open to the sky). This door is a pin the ship step may move, but then both
  QaDoorway tests move with it; keeping it there is simplest. Every wooden doorway leaves a villager 1.95 blocks of
  room from the step to the lintel (`QaDoorwayClearanceGameTests`).
- **No entity** (villager, item frame, painting...) in the file unless it is a frame or painting that hangs right
  turned and mirrored (`QaLedgerFramesHaulGameTests`); no jigsaw or structure void.
- Every block is one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).

## Fits
- **Level I plot: at most 13 wide x 16 tall x 13 deep** (`VillageHallBuildGameTests.aFinishedVillageHallWorksLikeAPlacedOne`
  builds it at helper (9, 2, 9) in the 22 x 18 x 22 `big_area`, with the builder's bench at (2, 2, 2), the builder at
  (3, 2, 3) and four supply barrels at x 1-5, z 4, west and north of it). y = 0 sits on the clicked block; the front
  is the z = 0 side.
- **Level I material budget: at most 108 stacks** (that test stocks four barrels of 27 with exactly the plan's
  materials, each item rounded up to whole stacks; items that stack to 16 or 1 eat stacks fast). The old Level I is
  658 blocks. **Build time:** the whole build to DONE within **30,000 ticks** (build delay 2, builders' help off), every
  block right afterwards (`plan.unfinished` empty).
- **Levels II and III:** no test builds them today; the ship step adds a build-to-DONE test for each, so keep each
  within **26 wide x 28 tall x 26 deep** (`tall_area` is 30 x 30 x 30, built from y = 2, with the bench and barrels
  beside it). A smaller plot upgrades in more villages: the upgrade's space check refuses any new cell holding another
  build or a non-natural block (wiki, Upgrades, 36.14).
- **Each level grows out of the last, in the same template frame:** Level II is at least Level I's size in x, y and z,
  III at least II's (`starterUpgradesKeepMostOfTheirBase`: "is smaller than"), and **each keeps at least 60 % of its
  base's non-air blocks unchanged, same cell, same state** (same test). Template coordinates start at 0 and the upgrade is
  placed on the old one's origin, so a level can only grow east (+x), back (+z) and up, or into air that the level below
  already left inside its own plot. The front (z = 0) never moves.
- **Declared sizes:** `StarterBlueprints.VILLAGE_HALL` 13 x 13 x 13, `VILLAGE_HALL_2` 19 x 13 x 13, `VILLAGE_HALL_3`
  24 x 24 x 13 today; the ship step changes them to the new sizes (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).
- **Front and way in:** the z = 0 side faces the player who places it; the way in is the front door there. The walk-in
  test's villager starts at template (6, 0, -1), in front of the door.
- **Neighbours:** none planned. The Steward never places a hall; the player puts it "in the middle of your village"
  (questbook), so expect other builds on every side, with roads leading to it (`Paths.destination` aims builders' paths at
  the meeting bell or the hall).

## Rules tests check
Pinned coordinates (the hall cell is fixed for saves; the others the ship step may move, keeping what each checks):
- `VillageHallBuildGameTests.everyLevelKeepsTheHallAtItsHeart`: in each level one Village Hall at (6, 1, 10) facing north
  with no data; a bell at (6, 4, 0); all three in `StarterBlueprints.ALL`, offered, no expansion switch; on the village
  map as a hall (`VillageMaps.kindOf`, by name); a name in `en_us.json`; I upgrades to II, II to III.
- `VillageHallBuildGameTests.aFinishedVillageHallWorksLikeAPlacedOne`: Level I built whole from four barrels within
  30,000 ticks (plot, budget above); no hall in reach before the build; afterwards the hall block at origin + (6, 1, 10)
  facing north, the builder's nearest hall, owned by the player, a founding in its chronicle.
- `QaDoorwayClearanceGameTests.aVillagerWalksInByTheVillageHallsShutFrontDoor`: Level I placed at helper (8, 2, 8) in
  `huge_area`; a nitwit at template (6, 0, -1) walks in by the shut front door to (6, 1, 8) within 500 ticks.
- `QaDoorwayClearanceGameTests.aCarpetMovedUnderTheVillageHallsAndSupplyShopsLintelIsCaught`: Level I's door at
  (6, 1, 3), as above.
- `FurnishedWorkplacesGameTests.everyWorkplaceIsFurnished` and `noSpotCoversAJobBlockABedOrAContainer`: the kinds above.
- `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (sizes grow, 60 % kept, 64 upgrades) and
  `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`.

By id only (the drawing doesn't matter beyond the rules above): `QaQuestbookClaims1116eGameTests` (unlocked on a Table
with no village and in a new village), `QaBlueprintLibraryVerify1108bGameTests.aNewVillageStartsWithTheWikisSetAndTheRestLockedOrRare`
(starting set), `ModdedLibraryGameTests` (a modded copy of every home and workshop; vanilla without the mods). The block's
own tests (`QaEveryBlockSaveGameTests`, `QaItemComponentSavesGameTests`, `QaStageThreeGameTests`) don't read the drawing.

Library-wide checks it must pass: `QaBuildReachGameTests`, `BlueprintsStayGameTests` (lamps, paths, beds whole),
`QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`, `QaFurnishedRooms1031GameTests` (no standing lantern on a room's
floor; indoor chests against something), `B648GameTests` / `QaWindowsAndWards1028BugTests` / `QaBuilders1028GameTests`
(no pane or bar joined on one side with air on the other), `QaLedgerFramesHaulGameTests`,
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `StyleGameTests.chimneysStayWholeInEveryStyle` (only
if a campfire stands on a solid stack).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair; `open_panes`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no
CHECK line, and `holes.py` passes.

For the ship step (not the designer): stop `generate.py` drawing the three ids (add them to `REBUILT`); drop
`holes.py`'s `TOWER_EAVE` allowance for `village_hall_3` and the `modded/village_hall_2`/`_3` lines in
`owner_rules_baseline.json`; update the `StarterBlueprints` sizes and comments; rewrite the Level II and III quest
descriptions in `tools/questbook/chapters.py` (they describe the old rooms) to say what the new levels add; keep the
old drawings with `history.py`; rerun `modded_library.py`.

## Free
Everything not listed above: shape, plan, roof, materials, colors, storeys, how the council room and Level III's
addition work and look, where the benches, table and chairs stand, extra doors, beds or chests, lighting, and where the
bell hangs from, as long as it is a working bell at (6, 4, 0).
