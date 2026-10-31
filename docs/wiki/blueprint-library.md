# Blueprint library (unlocks)

The Blueprint Table as a collection: each village starts with the basics and unlocks the rest, so buying a blueprint
from a builder is worth it again. The owner's playtest found builders selling what the Table gave away.

Roadmap items: 26.10, 36.6, 36.6a, 36.6b, 36.10, 36.13, B419

## 1. What a player sees

- **Unlocked builds** show in color, first in the list, and Get Blueprint works as before (for a Blank Blueprint).
- **Locked builds** show grayed out with a line saying how to get one ("Sold by a Journeyman Builder", "A market-day
  trader", "A questbook reward", "Found in chests", "Sold by a builder once the tier below stands", "Sold by a
  villager of its trade"). Their 3D preview is still drawn, dimmed, with the materials; Get Blueprint is grayed out.
- **Rare builds** (the builder-only grander homes, the Wonders, and `rare.json`'s Chapel, Market Square, Map Room,
  Bandstand, Guildhall, Nether Gate and Library III) are a dark **???** at the end of the list, with no name, preview or
  materials, until the village unlocks one.
- The header reads "<unlocked> of <listed> unlocked" while anything is locked.
- **What a build brings** (36.6a, the owner's change to 36.6, whose description lines are gone): under the size line,
  wrapped, "Jobs: Fisherman (1), Butcher/Chef/Rancher (3)" (one entry per kind of job block in the build, the jobs that
  share it joined by a slash with the one a jobless villager takes by itself first, and how many of that block the build
  holds), then "Beds: 2" when it has beds. A build with neither shows nothing there. Uploaded, scanned and imported
  builds get the lines too, from their own blocks; a build in a style shows its styled blocks'. A job that needs a mod
  that isn't installed (Cobblemon's Ball Smith or Trainer) isn't named, so a Training Post alone shows no Jobs line.
- **Search** (36.10): a box over the list ("Search: name, id or job" until something is typed, focused when the Table
  opens) narrows the list live, ignoring case, by a build's name, id or the jobs it brings ("inn", "fishers_hut",
  "fisherman", "smith"); every word typed must be found. A build's levels and styles are kept under it: when any of
  them matches, the build and all its levels are listed together, the build first, even where the full list keeps an
  unlocked build and its locked levels apart. A "???" is listed only while the box is empty. The header reads "<shown>
  of <total> match" while searching; nothing found reads "No builds match "zzq"." and "Try a name, an id or a job." in
  the list. A selected build the search hides gives way to the first match (the pane always shows a listed build); with
  nothing found the pane keeps what it showed. An empty box lists everything as before.
- **Stored blueprints** (36.13): the header's **Library** and **Stored (N)** tabs. The Stored tab lists the blueprints
  kept in this table, alike ones together in the order the first went in, each with its icon and "2 stored"; the
  header reads "<stored> of 200 stored". The pane shows the picked one's name, "2 stored in this table", its tooltip
  lines (size, style, placement) and its build in 3D, and says that stored blueprints belong to the table. Buttons:
  **Take One Out** (into the inventory; with no free slot it stays and the action bar says "Your inventory is full: the
  blueprint stays in the table"), **Store Held** (the main-hand blueprint) and **Store All** (every Blueprint and Wonder
  Plan carried; Blank Blueprints stay). Breaking the table drops everything stored in it.
- Each unlock says in chat: "The village's library has a new blueprint: Smithy II" (to the buyer, the player using a
  blueprint on the Table, or, for a builder's level-up, every player in the village).

The starting set (18): Starter Cottage, Stone House, Terrace, Farmstead, Storehouse, Smithy, Mason's Yard, Fletcher's
Lodge, Fisher's Hut, Weaver's Cottage, Builder's Workshop, Carpenter's Workshop, Kitchen, Guard House, Clinic, Post
Office, Library, Pokémon Center (listed only with Cobblemon). The builder's own trades (Market Stall to Supply Shop)
start locked, so they are worth buying.

Ways to unlock, all for the village round the nearest hall:

1. Buying a Blueprint from any villager or trader (builders, the other jobs' blueprint trades, market-day traders).
   The item is still sold.
2. A builder of the village leveling up: the next 3 locked builds (lowest tier first, then by id; never a rare one).
3. Questbook rewards (FTB Quests, The Builder chapter): Starter Cottage II and III, Stone House II and III, Terrace II.
4. A Blueprint used on the Table, however it was had: found in a chest (village storehouses, carpenters', builders'
   workshops, inns and kitchens; pirate ships, war camps, coven huts, piglin hoards, bandit camps and the pharaoh's
   tomb), bought, or given. A Wonder Plan used on the Table names its Wonder there (view only). Either hand works: from
   the off hand when the main hand is empty too (B419).

## 2. How it works

- `table/BlueprintUnlocks` holds the logic. `state(level, hall, id)` is UNLOCKED, LOCKED, HIDDEN or VIEW_ONLY (an
  unlocked Wonder: named and previewed, never handed out; Wonders are raised from their plan).
- Only the mod's own builds (the lists in `StarterBlueprints`, any style of them) and the Wonders can be locked. Imports,
  uploads, scans, structure-block saves and other datapacks' builds are always unlocked. Styles share their base's
  unlock; each upgrade tier is its own build.
- The starting and rare sets are data: `data/<ns>/blueprint_unlocks/starting.json` and `rare.json`, each a
  `blueprints` list, merged across namespaces and read on every reload.
- A Table's village is `VillageHalls.nearest` to the Table. With no hall in reach, one server-wide set is used; the
  first time a hall is used it takes that set in too.
- **Old saves:** a hall without the saved mark is seeded on first use with the starting set, every build of the mod
  finished within the hall's radius (`BuildSiteManager.finishedNear`) and every build site under way there.
- `TableServer.listing(level, hall)`: unlocked first, then locked (each with its hint's lang key), then one stand-in
  entry per hidden build (`aliveworkplace:hidden/<n>`, size 0, only the hint key). `details` sends nothing for a hidden
  build, and `take` refuses locked, hidden and view-only builds before a Blank Blueprint is used.
- Sales: a notifyTrade hook on every `AbstractVillager` (villagers and wandering traders). Level-ups: the
  increaseMerchantCareer hook. The command: `/workplace unlock <player> <blueprint>` (operator level), which the
  questbook's command rewards run with elevated permissions. Found blueprints: a pool added to eleven of the mod's chest
  loot tables. Using a Blueprint or a Wonder Plan on the Table: `BlueprintTableBlock.useItemOn`, then the Table opens.
- Expansion gates (`StarterBlueprints.offered`) still apply first.
- Jobs and beds (36.6a): `BuildJobs.of(blueprint)` walks the build's blocks once (cached with its materials): a bed
  counts by its head; any other block's point of interest (`PoiTypes.forState`) is a shared workstation of
  `work/Stations` (its jobs that `Stations.available` allows, so a switched-off job such as the Tailor is left out) or
  another job block (the professions whose job site it is, such as a barrel's Fisherman). Kinds with the same jobs are
  counted together. `TableServer.details` sends them in `TablePayloads.Details` (`jobs`, `beds`), and
  `BuildJobs.lines` makes the pane's lines.
- Search (36.10): `TableServer.listing` sends each build's jobs (profession ids, each once) in `TablePayloads.Entry.jobs`,
  from `TableServer.listedJobs` (kept per build, reusing the materials cache's `BuildJobs.Summary` when there is one).
  `table/TableSearch` does the matching in core: `filter(entries, query)` (words of the query, lower case, each found in
  the display name, the id and each job's name and id; a build's family is its first listed level by
  `BlueprintUpgrades.baseOf`, styles folded by `BlueprintStyles.base`), and `selection(shown, selected)`.
  `BlueprintTableScreen` holds the `EditBox`, refills the `LibraryList` from `filter` on each change and draws the
  no-match lines over the empty list.

- Stored blueprints (36.13): the Table has a block entity now, `table/BlueprintTableBlockEntity` (registered as
  `aliveworkplace:blueprint_table`; a Table placed before 36.13 gets one, empty, the first time it's used): a list of
  single stacks, at most `CAPACITY` (200), grouped by `ItemStack.isSameItemSameComponents` for the list. `TableLibrary`
  does the work on the server: `store(player, table, all)` and `takeOut(player, table, sample)`, both only within 8
  blocks of the table (`TableServer.atTable`), each re-sending the screen's `Open`. `TablePayloads.Open` carries
  `stored` (`Stored(stack, count)`) and `capacity`; the screen sends `Store(table, all)` and `TakeStored(table, stack)`.
  `BlueprintTableBlock.onRemove` drops what's stored. A full table stores what fits and leaves the rest with the player.

## 3. Switches

| Key | Default | Expansion | What it does |
|---|---|---|---|
| `blueprintUnlocks` | on |  | Off: every build is unlocked and the Table lists exactly what it did before (no ???, the builder-only homes not listed). Off in GameTests; the unlock tests turn it on. |

## 4. Saved data

- On the Village Hall: `blueprintUnlocks` (the unlocked builds' ids) and `blueprintUnlocksSeeded` (false in an old
  save, which seeds on first use).
- On each Blueprint Table's block entity (36.13): `stored`, a list of item stacks (absent in an old save: nothing
  stored).
- The overworld's `aliveworkplace_blueprint_unlocks` saved data: the server-wide set of Tables far from any hall.

## 5. Items, blocks, jobs, commands

- `/workplace unlock <player> <blueprint>`: unlocks a build in the village the player is in (operators, questbook).
- Using a Blueprint or a Wonder Plan on the Blueprint Table unlocks its build.
- Lang: `message.aliveworkplace.blueprint_unlocks.new`, `message.aliveworkplace.table.locked`,
  `message.aliveworkplace.table.locked_hidden`, `message.aliveworkplace.table.wonder_only`, the
  `screen.aliveworkplace.table.hint.*` lines, `screen.aliveworkplace.table.unlocked_count`, and (36.6a)
  `screen.aliveworkplace.table.jobs`, `.job_block`, `.jobs_separator` and `.beds`, and (36.10) `.search`, `.search_hint`,
  `.search_count`, `.no_match` and `.no_match_help`, and (36.13) `.tab_library`, `.tab_stored`, `.stored_count`,
  `.stored_row`, `.stored_here`, `.stored_narration`, `.take_out`, `.store_held`, `.store_all`, `.stored_none`,
  `.stored_empty`, `.stored_pick`, `.stored_help`, with `message.aliveworkplace.table.stored`, `.stored_many`,
  `.store_not_held`, `.store_none`, `.store_full`, `.took_out`, `.take_out_full` and `.not_stored`.
- Block entity `aliveworkplace:blueprint_table` (36.13).

## 6. Decisions

The owner's choices (ROADMAP Design decisions, 2026-10-15): the Table is a collection; the basics (about 15 to 20)
start unlocked; unlocks belong to the village; existing villages keep what they have standing or building; imports
are always unlocked; five ways to unlock (buying, level-ups, questbook, loot, market traders); a rare build is a
hidden "???"; a server switch, default on. Sorted unlocked first rather than a filter toggle (one less button).
The owner (2026-10-18, 36.6a): no description lines; the Table says only what jobs a build brings and its beds.
36.10 (lane decision): when the search hides the selected build, the first match is selected, so Get Blueprint never
hands out a build the list doesn't show; words are ANDed ("inn iii" is the Inn III's family); a "???" never matches.
36.13 (lane decisions): a Stored tab in the header rather than a mode of the item; stored blueprints are the table's
(anyone at it can take one out, as with a chest); Wonder Plans can be stored too; a table holds 200; a take-out into a
full inventory is refused rather than dropped, so nothing lands on the floor by surprise.

## 7. Known limits

- A blueprint unlocked while far from every hall goes into the server-wide set; a village that has already been seeded
  doesn't take it in later.
- The questbook hint shows only with FTB Quests installed; the five upgrade tiers say "Sold by a builder once the
  tier below stands" without it.
- A Table between two villages uses the nearer hall.
- Only the mod's own chest loot tables hold blueprints; vanilla villages' chests don't.
- The search matches names in the player's own language, but job and build ids only in English.
- The job line counts job blocks, not workers: a build with two smokers shows "(2)" even if one villager works there.

- Stored blueprints are shared by everyone who can reach the table; there is no lock or owner (36.13).

## 8. Proof

GameTests: `BlueprintUnlocksGameTests` (7): `BlueprintUnlocksGameTests.startingSetUnlockedRestLockedRareHidden` (the
starting set unlocked, the Market Stall and Smithy II locked with their hints, unlocked first, the ??? entries with
stand-in ids and no size, and no rare id in the encoded packet), `BlueprintUnlocksGameTests.lockedGetBlueprintIsRefused`,
`BlueprintUnlocksGameTests.buyingFromABuilderUnlocksIt` (a builder's and a wandering trader's sale),
`BlueprintUnlocksGameTests.levelUpsCommandsAndFoundBlueprintsUnlock` (a level-up, `/workplace unlock`, a found Chapel
used on the Table through the player's own click, and blueprints in a storehouse chest's loot),
`BlueprintUnlocksGameTests.anOldSaveUnlocksWhatStands`, `BlueprintUnlocksGameTests.unlocksSurviveSaveAndReload`,
`BlueprintUnlocksGameTests.theSwitchOffUnlocksEverything`. QA's `QaBlueprintUnlocksGameTests` (7): a found blueprint used twice says so once and the screen that opens lists it unlocked; one village's unlock leaves the other's build locked; a styled id of a locked or ??? build asked for over the network is refused, and handed out once unlocked; `/workplace unlock` again and with an unknown id; an unlocked build of a closed expansion stays off the Table; a level-up tells the players out to the village's edge (not a block beyond) and, with nothing locked, says nothing. `QaBlueprintUnlocksBugTests` (3, B419): a found Market Stall in the off hand with the main hand empty unlocks it, says so and stays in the hand; from the off hand the line comes once and the Table opens once with the build unlocked; a stick in the off hand opens the Table as usual and unlocks nothing. QA's `QaBuilderFixes1027GameTests` (2 of its 9, B419): two found Market Stalls in the off hand unlock it once and both stay in the hand; a build the village already knows, used from the off hand, opens the Table once with no second "new blueprint" line. `TableGameTests` and `TablePreviewGameTests` pass unchanged
with the switch off. `TableJobsGameTests` (5, 36.6a): `TableJobsGameTests.sharedJobBlocksAreCountedAndJoined` (the owner's example:
"Jobs: Fisherman (1), Butcher/Chef/Rancher (3)", "Beds: 2"), `TableJobsGameTests.bedsOnlyAndNeither`,
`TableJobsGameTests.aSwitchedOffJobIsLeftOut` (config tailors off), `TableJobsGameTests.theTableSendsAShippedBuildsJobsAndBeds`
(the Fishers' Hut and the Starter Cottage through `TableServer.details` and over the wire), `TableJobsGameTests.theLinesAreInEnglish`.
QA's `QaTableJobsGameTests` (6): `QaTableJobsGameTests.everyListedBuildShowsItsOwnJobsAndBeds` (all 141 listed builds and
their 777 styles: each count matches the job blocks and beds counted in its own template, in the exact format, every job's
name in English), `QaTableJobsGameTests.theStarterCottageShowsTheOwnersExample` ("Jobs: Fisherman (1), Carpenter (1)",
"Beds: 1"), `QaTableJobsGameTests.aScannedBuildShowsTheJobsAndBedsOfItsBlocks` (two Scan Tool scans, one with neither line),
`QaTableJobsGameTests.aJobWhoseModIsMissingIsLeftOut` (no Cobblemon), `QaTableJobsGameTests.theLinesReadInThePlayersLanguage`
(another language's words and names, three beds), `QaTableJobsGameTests.aSwitchFlippedAfterTheListingReachesTheListingToo`
(B570: config physicians turned off and on again after the Table listed every build; the listing's jobs stay the details').
`TableSearchGameTests` (6, 36.10): `TableSearchGameTests.searchFindsABuildByNameWithItsLevelsUnderIt` ("inn" with Inn
II and III right under the Inn, "INN" the same, "iii" bringing each build with its third level),
`TableSearchGameTests.searchFindsABuildById`, `TableSearchGameTests.searchFindsBuildsByTheJobsTheyBring` ("Fisherman"
and "smith" against the jobs the listing sends, and the jobs over the wire), `TableSearchGameTests.aSearchThatFindsNothingSaysSo`
(and every new line in English), `TableSearchGameTests.aClearedBoxListsEverything` (a "???" too, never found by a
search), `TableSearchGameTests.aSelectedBuildHiddenByTheSearchGivesWayToTheFirstMatch`.

`TableLibraryGameTests` (8, 36.13): `TableLibraryGameTests.storingTheHeldBlueprintPutsItInTheTable` (an empty hand,
a Blank Blueprint and a player 30 blocks away store nothing), `TableLibraryGameTests.storeAllListsEachBlueprintWithItsCount`
(the count: two kinds, 3 and 1, in order, over the wire), `TableLibraryGameTests.takingOneOutGivesItBackAndCountsDown`,
`TableLibraryGameTests.aFullInventoryLeavesTheBlueprintInTheTable`, `TableLibraryGameTests.aFullTableKeepsTheRestWithThePlayer`,
`TableLibraryGameTests.aBrokenTableDropsItsBlueprints`, `TableLibraryGameTests.theTableKeepsItsBlueprintsThroughASaveAndReload`
(and an old save loads empty), `TableLibraryGameTests.theStoredTabReadsInEnglish`.

`QaBlueprintTableGameTests` (13, QA of 36.1, 36.10 and 36.13 through the packets a client sends): the Stored tab's
counts for two kinds down to the last one taken out, a stack in hand, the last free slot and a full bag (nothing
dropped), a table at 199 and at 200, a table broken by a player dropping exactly what it held (and one replaced by
another block), two tables kept apart through a reload, a second player taking one out; the search with mixed case,
tabs and spaces, a name word with a job, a query only a level's id matches, each job of a shared job block; every
basement build's preview depth, floor division at scales 1 to 3, and the "and 4 below ground" line from the preview a
client receives.

Showcase scene `table_library` (36.13): the Stored tab empty, Store All with the counts, Take One Out counting the
cottage down, and the cottage back in hand.

Showcase scenes: `table` and `table_preview` (the dev client runs with the switch on, so the Table there shows the
starting set unlocked and the rest locked or ???; `table` checks the Starter Cottage's "Beds:" line shows, then types "smith" and checks the list narrowed, a search
for nothing lists nothing, and an emptied box lists everything).
