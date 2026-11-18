# Library: the brief

Family id(s): `library`, `library_2`, `library_3`, `library_4` (never renamed; "Library", "Library II", "Library III",
"Library IV" in `en_us.json`; advancements `built/library` ... `built/library_4`)
Variants: village (none: there is no `village/<style>_library`), modded (none to draw: `modded_library.py` makes
`structure/modded/library*.nbt` from the vanilla drawing and the ship step reruns it; `QaCarvedLibraryCompatTests.theHomesAndWorkshopsHaveCopies`
wants `library`'s copy complete), looks (none of its own: every style in `blueprint_styles/` (`spruce`, `stonework`,
`dark_oak`, ...) is applied by `BlueprintStyles` automatically, and two compat tests build styled copies, see "Rules").

Not in this family: the Great Library (`great_library`, a Wonder: `LibraryPowerGameTests`, `WonderFindsGameTests`,
the "library" research state in `ScholarWork`) and the Blueprint Table's "library" (the list of blueprints).

## What it is for
The village's place of learning. Its **lecterns** are job sites (the vanilla `LIBRARIAN` POI, one ticket each) shared
by three jobs (36.17, `Stations`): a jobless villager who takes one becomes a **Scholar**, a **Librarian** or a
**Teacher** at random, and a player can make the villager beside it a Scholar (questbook: "stand a villager right next
to the Library's lectern, hold paper and sneak-right-click"). Scholars research the village's topics there
(`ScholarWork`: they walk to the lectern and pay paper, books and emeralds from the village store, a Printer's chests,
then chests within 8 across and 4 up or down of their lectern). A finished Library of any level (an upgrade or a styled
copy counts as its base) is the **library service** (`services/library.json`, range 48), which Burgher and Noble
households need (`classes/`), and it draws as a blue LEARNING banner on the hall's map (`VillageMaps`). From Level III a
**Librarian** with an Enchanting Table near their lectern enchants the workers' gear (`EnchantWork`). The Steward builds
it in the `civic` zone; it is in every Blueprint Table from the start.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `library` | **One lectern** (one Scholar/Librarian/Teacher); the library service | In every Table from the start (`blueprint_unlocks/starting.json`); the Steward's `library` rule (6+ villagers, no Scholar, none built) and `workplace_library` rule (a Librarian or Scholar without a workstation, `wanted`) build it in `civic`; the Steward's renewal lists for `librarian` and `scholar` (`steward_renewal/`); questbook "Build the Library and get a Scholar" (`built/library`) |
| II | `library_2` | **A second lectern** ("room for a second Scholar", questbook); keeps I | Upgrade of `library` (`BlueprintUpgrades`: id + `_2`), sold by the builder who finished I (`UpgradeOffers`); questbook "Library II" (`built/library_2`) |
| III | `library_3` | **A third lectern** (for the Librarian who enchants) and **an Enchanting Table powered by 15 bookshelves** within reach of it; keeps II | Upgrade of `_2`; **rare** (`blueprint_unlocks/rare.json`: a dark ??? at the Table until unlocked; `QaBlueprintLibraryVerify1108bGameTests`); in six loot tables (`pirate_ship`, `piglin_hoard`, `war_camp`, `coven_hut`, `bandit_camp`, `pharaohs_tomb`); questbook "Library III" (`built/library_3`) |
| IV | `library_4` | **A basement 4 deep under it** (an archive, 26.15), with a way down, light and stores; Level III above it kept | Upgrade of `_3` (one of `StarterBlueprints.LEVEL_4`); a builder who finished III sells it (`BasementGameTests.basementsLoadUnderTheGround`) |

The upgrade count in `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (64) must not change: all four levels stay,
each an upgrade of the one before.

## Must have (each level)
- **Job blocks: lecterns.** Level I exactly **one** `minecraft:lectern` (`CarvedStylesCompatTests.aSpruceLibraryHasCarvedShelvesAndALibrarian`
  counts the styled copy's lecterns and wants 1); II at least two, III at least three (questbook text; no test counts
  them), IV keeps III's. Every lectern plain `minecraft:lectern` (the styles swap it), reachable on foot
  (`QaBuildReachGameTests`), with **a free standing spot beside it**: within 2 blocks (Manhattan) and 1 up or down, an
  air cell with air above and a full block below (`CarvedStylesCompatTests.standNear`: a jobless villager is put there and
  must take the lectern). No Scholar's Desk is drawn (the Scholar also takes a lectern). Any other job block (barrel,
  composter, brewing stand, crafting table, ...) pulls a villager to another job: draw one only on purpose, and never
  in Level I (see Storage).
- **Level III's enchanting room.** One `minecraft:enchanting_table` with **15 bookshelves counted the way vanilla counts
  them** (`EnchantingTableBlock.BOOKSHELF_OFFSETS`: two blocks out, one level up or level, with air between table and
  shelf; `EnchantWork.shelves`; the questbook promises "ringed by fifteen bookshelves"), standing **within 8 blocks across
  and 2 up or down of the Librarian's lectern** (`EnchantWork.table` searches that box round the lectern). Since any
  lectern may be taken by the Librarian, put it in reach of every lectern if you can; at least the one Level III adds.
  The table must not be one of the 15 shelves' blockers: nothing but air in the cells between.
- **Storage.** **Level I: no container at all** (no chest, barrel, furnace, hopper, brewing stand or anything else that
  holds items): `FurnishedWorkplacesCompatTests.theLibraryWithHandcraftedAndBeautify` wants exactly 0 stores, and the
  `furnished_workplaces` scene expects 0. II and III: none required. `EnchantWork` takes the Librarian's lapis from
  chests within 8 across and 4 up or down of their lectern, then the Storehouse, so a chest in reach of Level III's
  lectern is useful (no test reads it). Every chest or barrel drawn: plain `minecraft:chest`/`barrel`, empty, openable,
  reachable on foot within 2 across and 1 up or down (`QaBuildReachGameTests`, in the basement too
  `QaBasementGameTests`), and indoors against a wall, solid block, bed or chest (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`).
- **Bookshelves in Level I: at least 30 plain `minecraft:bookshelf`** (`aSpruceLibraryHasCarvedShelvesAndALibrarian`:
  the spruce copy must have >= 30 Carved Wood shelves, which the style makes from vanilla bookshelves; no vanilla
  bookshelf may be left after the swap, so no other shelf block stands in for them).
- **Beds and residents.** None. A workplace's furniture file may have no bed spot (`FurnishedWorkplacesGameTests.noSpotCoversAJobBlockABedOrAContainer`).
- **Furniture kinds (every level, I to IV):** at least one each of **desk, chair, picture, hanging_pot**
  (`FurnishedWorkplacesGameTests.everyWorkplaceIsFurnished`), recorded in `blueprint_furniture/library*.json` by
  `rebuild.py`. No spot's cells may cover a job block, bed or container (`noSpotCoversAJobBlockABedOrAContainer`). The
  desks are never Poké Ball desks (`withoutCobbleFurniesTheDesksFollowTheOrder`; `QaStyledPokeDesks1104dCompatTests.aStyledLibraryKeepsHandcraftedDesks`:
  every styled Library has at least one desk, all Handcrafted). With Handcrafted and Beautify, desks and chairs become
  Handcrafted's, pictures and hanging pots Beautify's (a picture on a wall may stay a banner), all built with nothing
  skipped (`theLibraryWithHandcraftedAndBeautify`). Every chair opens toward its table (`QaChairFacingGameTests`), every
  piece stands on what it needs (`FurnishedRoomsGameTests`, `FloatingFurnishingsGameTests`), nothing in a door's way, and
  every room with a piece on its floor has block light above 7 from a lantern hung or on a table (`everyFurnishedRoomHasALight`).
- **Level IV's basement.** Exactly 4 deep (`StarterBlueprints.LIBRARY_4` basement 4; the `.nbt` is `size.y + 4` tall,
  its floor at y = -4, `basementsLoadUnderTheGround`, `QaBlueprintTableGameTests.everyBasementBuildPreviewsItsWholeDepth`),
  with: a ladder or stair below y = 0; some ladder or stair on y = -1 with room above it on y 0 and 1
  (`QaBasementGameTests.everyBasementsWayDownComesUpThroughTheGroundFloor`); a lantern below y = 0 and no dark spot (every
  air cell on its floor with air over it has block light above 0 from the build's own lights,
  `noBasementLeavesADarkSpotForMonsters`); walkable from outside down to its floor and back
  (`everyBasementAndItsChestsCanBeWalkedToFromOutside`). Levels I to III have **no cell below y = 0**
  (`everyBasementBuildPreviewsItsWholeDepth`).
- **Placeable and whole**: every block buildable (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`), standing
  as drawn when done, nothing skipped.

## Kept cells (every upgrade)
- II at least I's size in every dimension, III at least II's, IV at least III's, on the same origin and front; each
  keeps **at least 60 %** of the level below's non-air blocks exactly (same cell, same state)
  (`starterUpgradesKeepMostOfTheirBase`). So the lecterns and the enchanting room stay where they were put.
- IV is III with the basement dug under it: the wiki (`basements.md`) promises the house above is left as it was, apart
  from the opening for the way down.

## Fits
- **Level I: at most 13 wide x 13 deep x 16 tall.** `BuilderGameTests.buildsLibrary` builds it at helper (9, 2, 9) in
  the 22 x 18 x 22 `big_area` (bench (2, 2, 2), chest (2, 2, 4), villager (3, 2, 3), material barrels at x 1-5, z 4):
  at most **108 stacks** of materials (4 barrels of 27, "test needs more barrels"), built within **30,000 ticks** at
  build delay 2. The compat tests place it at (9, 2, 3) in the 30 x 20 x 30 `huge_area` (roomier).
- **Level III (and so II): at most 21 wide x 22 deep x 18 tall**, from bare ground in **108 stacks** and **50,000
  ticks** (`BuilderGameTests.buildsLibraryIII` at helper (9, 2, 8) in `huge_area`, same barrels).
- **Level IV:** III's size above the ground (no placement test).
- **Declared sizes:** `StarterBlueprints.LIBRARY` (13, 15, 12), `LIBRARY_2` (18, 15, 12), `LIBRARY_3` (18, 15, 20),
  `LIBRARY_4` (18, 15, 20) basement 4; the `.nbt`s must match, so the ship step sets them to the new sizes.
- y = 0 sits on the clicked block; the front is the z = 0 side (the gold edge of the outline, questbook).
- **Front and way in:** the z = 0 side faces the player who places it and the road of a Steward plot; the way in is
  there. A villager 1.95 tall walks through it (`QaDoorwayClearanceGameTests`); every lectern, chest and the basement
  reached on foot from outside, upper floors by stairs or ladder.
- **Neighbours:** a Steward `civic` plot: 2 blocks clear round it (`Plots.CLEAR`), the zone 2 beyond the footprint
  (`Plots.MARGIN`), the ground under it within 4 of level (`Plots.MAX_STEP`); a renewal of a librarian's or scholar's
  old house takes it only where it fits the old footprint plus 3 a side (`Renewals.MARGIN`), so a smaller Level I fits
  more often.

## Rules tests check
Pinned to the drawing (the ship step moves them to the new one, keeping what each checks):
- `CarvedStylesCompatTests.aSpruceLibraryHasCarvedShelvesAndALibrarian` (spruce Level I at (9, 2, 3), compat): >= 30
  shelves, all swapped; exactly 1 lectern, swapped and a Librarian POI, free when built; a villager set beside it takes
  a lectern job and works there. `QaCarvedStylesCompatTests.aSavedSpruceSiteLoadsWithItsCarvedWoodBlocks`: its plan
  has Carved Wood shelves and a lectern, the same after save and load.
- `FurnishedWorkplacesCompatTests.theLibraryWithHandcraftedAndBeautify` (Level I, compat): every spot furnished as the
  order says, a lectern present and a job site when built, 0 containers. `theHealingCenterAndSchoolhouseGetPokeBallDesks`
  pins the Library's desk as `handcrafted:dark_oak_desk` (from the desk spot's wood): the ship step updates that string
  if the desks' wood changes.
- `QaFurnishedRooms1031GameTests.NOT_A_ROOM` exempts a standing lantern at (14, 9, 6) of II-IV (the old tower's
  lookout): the new drawing needs no exemption (no lantern standing on any floor but a table's), and the ship step drops it.
- Scenes: `FurnishedWorkplacesScene` (library desks view, 0 chests) and `CarvedStylesScene` (front and shelves views)
  have cameras on the old drawing; the ship step re-aims them.

By id only (the drawing doesn't matter beyond the rules above): `ServiceGameTests` (`library_2` gives `library`, the
service files), `QaClassPerksGameTests.aStyledUpgradeCountsAsItsBase` (stonework `library_3`), `ClassGameTests`,
`StewardCivicRulesGameTests.sixVillagersAndNoScholarGetALibrary`, `WorkplacesGameTests` (`workplace_library` for a
Librarian and a Scholar, `civic`), `VillageHallGameTests.aVillageMapIsDrawn` (a finished Level I at (2, 2, 2) gives a
blue "Library" banner), `QaQuestbookClaims1116eGameTests`, `QaBlueprintLibraryVerify1108bGameTests` (III rare),
`TableGameTests`, `BasementGameTests.basementsLoadUnderTheGround` (IV upgrades III and is sold), `ModdedLibraryGameTests`
and `ModdedLibraryCompatTests` (the modded copies: same size and basement, doors, beds and barrels unchanged),
`QaModdedRefresh1115cCompatTests`.

Library-wide checks it must pass: `QaBuildReachGameTests`, `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`,
`QaMirroredBuildsGameTests` (doors and double chests whole turned and mirrored), `QaTurnedFramesGameTests`,
`QaTurnedModBlocksGameTests`, `QaLedgerFramesHaulGameTests`, `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`,
`B648GameTests`, `QaWindowsAndWards1028BugTests` / `QaBuilders1028GameTests` (no pane or bar joined on one side with air
on the other), `BlueprintsStayGameTests`, `FurnishedRoomsGameTests`, `QaFurnishedRooms1031GameTests`,
`FurnitureSpotGameTests`, `FloatingFurnishingsGameTests`, `QaBasementGameTests` (3, Level IV),
`StyleGameTests.chimneysStayWholeInEveryStyle` (only if a campfire stands on a stack).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair; `open_panes`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no
CHECK line and `holes.py` passes. `owner_rules_baseline.json` allows 1 for `library_2`, `_3`, `_4` (and their modded
copies): that was the old drawing's; the new one must have none, and the ship step lowers them to 0. The ship step keeps
the old drawings in `blueprint_history/` (`library*` and `modded/library*` exist), regenerates the modded copies,
updates the declared sizes, and rewrites the player text that describes the old look: the questbook ("a study tower",
"an enchanting room"), `basements.md` ("stair along the back of the reading room") and the `StarterBlueprints` comments.

## Free
Everything not listed above: shape, plan, rooms, roof, materials, colors, storeys, how the levels grow (sideways, back
or up, within the plots), where the lecterns, the enchanting table and the basement stand (within the reaches above),
how many shelves beyond Level I's 30 and III's 15, what the archive holds, lighting, and whether there is a reading
room, a tower, a yard or a porch.
