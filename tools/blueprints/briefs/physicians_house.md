# Physician's House: the brief

Family id(s): `physicians_house` (one tier, never renamed; "Physician's House" in `en_us.json`,
`blueprint.aliveworkplace.physicians_house`; advancement `built/physicians_house`)   Variants: village (none drawn: a
village's style is applied automatically by `BlueprintStyles`), modded (none to draw: `modded_library.py` makes
`structure/modded/physicians_house.nbt` from the vanilla drawing; the ship step reruns it), looks (none)

## What it is for
The **Physician**'s workplace (`ModVillagers.PHYSICIAN`, config switch `physicians`), the village's answer to the
plague (ROADMAP 32.18, 32.19). A jobless villager standing by its **brewing stand**, handed a **glistering melon slice**
by a player, becomes a Physician with that stand as his job site (`Stations.choose`; the stand is the Cleric's POI, which
the Physician shares). In a plague he takes a **Remedy from the chest by his stand** (or brews one there from a glass
bottle, a glistering melon slice, a honey bottle and a fermented spider eye; `PhysicianWork.ready` / `brew`), walks
house to house to each patient's bedside, cures them or sends them home to rest, and puts the empty bottle back in that
chest. Players give him Remedies "to the Physician's chests" (`request.aliveworkplace.remedy_physician`). When a
**Town or City** has no Physician, What next? suggests building this house (`Disasters.ANSWER_HOUSES`, `houseTips`).
The player takes the blueprint from the Blueprint Table (offered only while `physicians` is on,
`StarterBlueprints.expansionSwitch`) and gives it to a Builder. On the Village Map it is a CARE building
(`VillageMaps.kindOf`, by id). No Steward rule, no unlock file, no loot, no trade sells it.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `physicians_house` | The brewing stand, the chest by it, a ward of exactly four screened beds, a herb garden | Blueprint Table, `StarterBlueprints.ONE_TIER` and `ANSWERING_HOUSES`; no `_2` may be added (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase` counts exactly 64 upgrades) |

## Must have
- **Job block: exactly one `minecraft:brewing_stand`, and no other job block of any kind.**
  - Zero of every other block on the test's list: blast furnace, smithing table, grindstone, stonecutter, fletching
    table, cartography table, **barrel**, lectern, composter, cauldron, water cauldron, smoker, loom, **crafting
    table**, beehive, jukebox (`AnsweringHousesGameTests.theHousesHaveWhatTheirJobsNeed`).
  - Exactly one block in the whole drawing is any profession's job site, vanilla's or this mod's (bench, mailbox, shop
    counter, ...), and it is the stand (`QaAnsweringHousesGameTests.eachHouseHasOneJobSiteAndItIsItsOwnJobs`).
  - A floor cell a villager can stand on, clear for two blocks up, within **4.5 blocks** of the stand
    (`Stations.REACH`). The build test pins it today at template (6, 1, 8) (spawned one above), the stand at (6, 1, 10);
    the ship step moves both to the new drawing.
  - The stand can be walked to from the ground outside (within 2 across and 1 up or down of a cell reached on foot:
    level, one up, up to three down, ladders, doors and trapdoors;
    `QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`).
- **Storage: at least one `minecraft:chest` within 8 blocks across and 4 up or down of the stand**
  (`SupplyContainers.find`, `RADIUS` 8, `VERTICAL` 4). No test checks it, but without it the Physician has nowhere to
  take or brew a Remedy, and cures nobody. **The old drawing has none; the new one must.** No barrel (a job block). Every
  indoor chest stands against a wall, solid block, bed or chest (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`),
  is reachable on foot, and is drawn empty.
- **Beds: exactly four beds** (four `#minecraft:beds` FOOT halves in the file; `theHousesHaveWhatTheirJobsNeed`), the
  quarantine ward. **Each bed's FOOT must have `minecraft:white_wool` one or two cells toward -z (north) AND one or two
  cells toward +z (south)** (the screens; template axes, not the bed's facing). No code makes the ward a hospital or
  gives it residents: villagers may claim these beds as homes like any bed, and the Table shows "Beds: 4". Each bed's
  head is reachable on foot; the head lies one block toward its facing from the foot
  (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`); no bed right inside a doorway (`check.py`).
- **Herb garden: at least 8 blocks in `#minecraft:flowers` or `minecraft:fern`** (same test). Potted plants do not
  count (a potted fern is `potted_fern`, not in either), nor does `large_fern`; each half of a tall flower counts.
- **Furniture spots:** `blueprint_furniture/physicians_house.json` today records only the four beds (`kind: bed`).
  `rebuild.py` regenerates it from the new drawing; each spot's blocks must be exactly the template's there
  (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); nothing small stands on air
  (`FloatingFurnishingsGameTests`); chair stairs face their table, turned and mirrored (`QaChairFacingGameTests`); every
  piece on a furnished room's floor has block light above 7 from that room's own hung or table lantern
  (`FurnishedRoomsGameTests.everyFurnishedRoomHasALight`); no standing lantern on a room's floor
  (`QaFurnishedRooms1031GameTests.noLanternStandsOnARoomsFloor`).
- **No entities** at all (no frames, paintings, armor stands, mobs; `theHousesHaveWhatTheirJobsNeed`). No jigsaw or
  structure void; no basement. Every block is one a builder can place.
- Doorways: room for a villager 1.95 blocks tall (`QaDoorwayClearanceGameTests`, `DoorwayHeadroomGameTests`; no carpet
  just inside a 2-tall doorway). Doors, beds and double chests stay whole turned and mirrored
  (`QaMirroredBuildsGameTests`).

## Fits
- **Plot: at most 18 wide (x) x 18 tall (y) x 22 deep (z).** `answeringHouseBuilt_physicians_house` places it at helper
  (12, 2, 8) in `huge_area`, which is **30 x 20 x 30 with a barrier roof at y 20**: x 12..29, y 2..19, z 8..29. The
  builder's bench (3, 2, 3), its three chests (3, 2, 6/8/10) and the builder (5, 2, 3) stand west of it. Leave a block or
  two of ground spare round it for the builder's path. y = 0 sits on the clicked block; the front is the z = 0 side.
- **Declared size:** `StarterBlueprints.PHYSICIANS_HOUSE` is `13, 12, 13` today; if the size changes, the ship step
  changes that `Vec3i` (`answeringHousesAreInTheBlueprintTable`: "is <size>";
  `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`; `modded_library.py` reads it).
- **Material budget: at most 81 stacks** (three chests of 27, each item rounded up to whole stacks; the build test).
- **Build time (the dusk lesson):** the build test sets day time **2,000**, build delay 2, builders' help off, one
  builder, a **12,000-tick** timeout. The builder works only in the WORK shift and **stops at dusk (about day time
  9,000-11,000)**, so the whole house must reach DONE within one working day, about **7,000 ticks**, with time left for
  the job check. The old drawing is **649** non-air blocks and passes; rebuilds over about **1,100** blocks timed out.
  **Keep it at or under about 800 placed blocks**, with no roof over another roof for the builder to get stuck on.
- **Front and way in:** the z = 0 side faces the player who places it. The builder's path starts at the door
  (`Paths.doorstep`; with no door, one block before the middle of the z = 0 edge), so the way from the front leads in to
  the stand, the chest and every bed head.
- **Neighbours:** none planned. No Steward rule or village plan places it; the player sites it. The `defences`
  showcase scene (`ScreenshotHarness`, `DEFENCES` + `ANSWERING_HOUSES`) spreads the builds by their size, no coordinate
  inside the build pinned.

## Rules tests check
Pinned to the drawing (the ship step moves coordinates to the new drawing, keeping what each checks, dropping none):
- `AnsweringHousesGameTests.theHousesHaveWhatTheirJobsNeed`: exactly 1 `brewing_stand`, 0 of every other listed job
  block; no entities; **exactly 4** bed FOOT halves; for each foot, `white_wool` at foot -z 1 or -z 2, and at foot +z 1
  or +z 2; **>= 8** blocks in `#minecraft:flowers` or `minecraft:fern`. These block kinds (white wool, flowers/fern) are
  hard requirements: changing a test's named block would loosen it.
- `AnsweringHousesGameTests.answeringHouseBuilt_physicians_house` (generated by `builds()`): built from bare ground at
  helper (12, 2, 8) in `huge_area` from exactly its supply list (<= 81 stacks), the site finishes within 12,000 ticks
  starting at day time 2,000 with the stand in place; then a NoAI jobless villager at the standing spot (template
  (6, 1, 8) + one up today) is handed a glistering melon slice, becomes a Physician, and his `JOB_SITE` is that stand.
- `QaAnsweringHousesGameTests.eachHouseHasOneJobSiteAndItIsItsOwnJobs`: one job-site block in all, the Physician's.
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`; `QaBuildReachGameTests` (stand, chests,
  bed heads walkable); `QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`;
  `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`.
- Modded copy: `ModdedLibraryGameTests` (a copy exists; `ANSWERING_HOUSES` is in its list),
  `ModdedLibraryCompatTests.everyModdedCopyCanBeBuiltWithThePacksMods`, `QaCarvedLibraryCompatTests.everyModdedCopyKeepsItsJobSites`
  (the stand at the same cell, no job site added): the ship step regenerates it.
- Refresh: the ship step keeps the old drawing in `blueprint_history/physicians_house/` (`history.py`; none there today)
  so a site begun on it finishes as drawn; `QaRefreshHistory1028GameTests.qaEveryShippedOlderDrawingIsOfferedARefresh`
  then wants the new drawing to differ.
- By id only (the drawing doesn't matter): `answeringHousesAreInTheBlueprintTable` (Table, `ONE_TIER`, named, CARE),
  `whatNextSuggestsTheHouseOfAMissingAnsweringJob`, `QaAnsweringHousesGameTests.thePhysiciansHouseIsSuggestedFromATownUp`,
  `ExpansionBuildsGameTests.laterExpansionsBuildsWaitForTheirSwitch` (`physicians`),
  `QaBuiltAdvancements1116bGameTests.everyShippedBlueprintHasItsAdvancement`, `TablePreviewGameTests`, `QaTableJobsGameTests`.
- Library-wide: `B648GameTests`, `QaWindowsAndWards1028BugTests` / `QaBuilders1028GameTests` (no pane or bar joined on
  one side with air on the other), `BlueprintsStayGameTests` (lamps on a block that holds them; no dirt path or farmland
  under a solid block; beds whole), `FurnishedRoomsGameTests`, `QaFurnishedRooms1031GameTests`, `FurnitureSpotGameTests`,
  `FloatingFurnishingsGameTests`, `QaChairFacingGameTests`, `QaDoorwayClearanceGameTests`, `QaMirroredBuildsGameTests`,
  `QaTurnedFramesGameTests`, `StyleGameTests.chimneysStayWholeInEveryStyle` (only if a campfire stands on a solid stack).
- The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
  stove or on a table with a chair, no bed right inside a door; `open_panes`), STYLE.md's checks and `holes.py`:
  `rebuild.py --candidate` prints no CHECK line.
- Not a test, but read: the Physician needs the chest within 8 across / 4 up-down of his stand (above).

## Free
Everything not listed above: shape, plan, roof, materials and colors (beyond white wool screens and flowers or ferns),
storeys and rooms, where the stand, chest, beds, screens, garden and door stand (within the rules), more chests and
furniture, lighting (within the light rules).
