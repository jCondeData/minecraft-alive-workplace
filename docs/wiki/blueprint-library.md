# Blueprint library (unlocks)

The Blueprint Table as a collection: each village starts with the basics and unlocks the rest, so buying a blueprint
from a builder is worth it again. The owner's playtest found builders selling what the Table gave away.

Roadmap items: 26.10, 36.6, 36.6a, 36.6b

## 1. What a player sees

- **Unlocked builds** show in colour, first in the list, and Get Blueprint works as before (for a Blank Blueprint).
- **Locked builds** show greyed out with a line saying how to get one ("Sold by a Journeyman Builder", "A market-day
  trader", "A questbook reward", "Found in chests", "Sold by a builder once the tier below stands", "Sold by a
  villager of its trade"). Their 3D preview is still drawn, dimmed, with the materials; Get Blueprint is greyed out.
- **Rare builds** (the builder-only grander homes, the Wonders, and `rare.json`'s Chapel, Market Square, Map Room,
  Bandstand, Guildhall, Nether Gate and Library III) are a dark **???** at the end of the list, with no name, preview or
  materials, until the village unlocks one.
- The header reads "<unlocked> of <listed> unlocked" while anything is locked.
- **What a build brings** (36.6a, the owner's change to 36.6, whose description lines are gone): under the size line,
  wrapped, "Jobs: Fisherman (1), Butcher/Chef/Rancher (3)" (one entry per kind of job block in the build, the jobs that
  share it joined by a slash with the one a jobless villager takes by itself first, and how many of that block the build
  holds), then "Beds: 2" when it has beds. A build with neither shows nothing there. Uploaded, scanned and imported
  builds get the lines too, from their own blocks; a build in a style shows its styled blocks'.
- Each unlock says in chat: "The village's library has a new blueprint: Smithy II" (to the buyer, the player using a
  blueprint on the Table, or, for a builder's level-up, every player in the village).

The starting set (18): Starter Cottage, Stone House, Terrace, Farmstead, Storehouse, Smithy, Mason's Yard, Fletcher's
Lodge, Fisher's Hut, Weaver's Cottage, Builder's Workshop, Carpenter's Workshop, Kitchen, Guard House, Clinic, Post
Office, Library, Pokémon Center (listed only with Cobblemon). The builder's own trades (Market Stall to Supply Shop)
start locked, so they are worth buying.

Ways to unlock, all for the village round the nearest hall:

1. Buying a Blueprint from any villager or trader (builders, the other jobs' blueprint trades, market-day traders).
   The item is still sold.
2. A builder of the village levelling up: the next 3 locked builds (lowest tier first, then by id; never a rare one).
3. Questbook rewards (FTB Quests, The Builder chapter): Starter Cottage II and III, Stone House II and III, Terrace II.
4. A Blueprint used on the Table, however it was had: found in a chest (village storehouses, carpenters', builders'
   workshops, inns and kitchens; pirate ships, war camps, coven huts, piglin hoards, bandit camps and the pharaoh's
   tomb), bought, or given. A Wonder Plan used on the Table names its Wonder there (view only).

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

## 3. Switches

| Key | Default | Expansion | What it does |
|---|---|---|---|
| `blueprintUnlocks` | on |  | Off: every build is unlocked and the Table lists exactly what it did before (no ???, the builder-only homes not listed). Off in GameTests; the unlock tests turn it on. |

## 4. Saved data

- On the Village Hall: `blueprintUnlocks` (the unlocked builds' ids) and `blueprintUnlocksSeeded` (false in an old
  save, which seeds on first use).
- The overworld's `aliveworkplace_blueprint_unlocks` saved data: the server-wide set of Tables far from any hall.

## 5. Items, blocks, jobs, commands

- `/workplace unlock <player> <blueprint>`: unlocks a build in the village the player is in (operators, questbook).
- Using a Blueprint or a Wonder Plan on the Blueprint Table unlocks its build.
- Lang: `message.aliveworkplace.blueprint_unlocks.new`, `message.aliveworkplace.table.locked`,
  `message.aliveworkplace.table.locked_hidden`, `message.aliveworkplace.table.wonder_only`, the
  `screen.aliveworkplace.table.hint.*` lines, `screen.aliveworkplace.table.unlocked_count`, and (36.6a)
  `screen.aliveworkplace.table.jobs`, `.job_block`, `.jobs_separator` and `.beds`.

## 6. Decisions

The owner's choices (ROADMAP Design decisions, 2026-10-15): the Table is a collection; the basics (about 15 to 20)
start unlocked; unlocks belong to the village; existing villages keep what they have standing or building; imports
are always unlocked; five ways to unlock (buying, level-ups, questbook, loot, market traders); a rare build is a
hidden "???"; a server switch, default on. Sorted unlocked first rather than a filter toggle (one less button).
The owner (2026-10-18, 36.6a): no description lines; the Table says only what jobs a build brings and its beds.

## 7. Known limits

- A blueprint unlocked while far from every hall goes into the server-wide set; a village that has already been seeded
  doesn't take it in later.
- The questbook hint shows only with FTB Quests installed; the five upgrade tiers say "Sold by a builder once the
  tier below stands" without it.
- A Table between two villages uses the nearer hall.
- Only the mod's own chest loot tables hold blueprints; vanilla villages' chests don't.
- The job line counts job blocks, not workers: a build with two smokers shows "(2)" even if one villager works there.

## 8. Proof

GameTests: `BlueprintUnlocksGameTests` (7): `BlueprintUnlocksGameTests.startingSetUnlockedRestLockedRareHidden` (the
starting set unlocked, the Market Stall and Smithy II locked with their hints, unlocked first, the ??? entries with
stand-in ids and no size, and no rare id in the encoded packet), `BlueprintUnlocksGameTests.lockedGetBlueprintIsRefused`,
`BlueprintUnlocksGameTests.buyingFromABuilderUnlocksIt` (a builder's and a wandering trader's sale),
`BlueprintUnlocksGameTests.levelUpsCommandsAndFoundBlueprintsUnlock` (a level-up, `/workplace unlock`, a found Chapel
used on the Table through the player's own click, and blueprints in a storehouse chest's loot),
`BlueprintUnlocksGameTests.anOldSaveUnlocksWhatStands`, `BlueprintUnlocksGameTests.unlocksSurviveSaveAndReload`,
`BlueprintUnlocksGameTests.theSwitchOffUnlocksEverything`. `TableGameTests` and `TablePreviewGameTests` pass unchanged
with the switch off. `TableJobsGameTests` (5, 36.6a): `TableJobsGameTests.sharedJobBlocksAreCountedAndJoined` (the owner's example:
"Jobs: Fisherman (1), Butcher/Chef/Rancher (3)", "Beds: 2"), `TableJobsGameTests.bedsOnlyAndNeither`,
`TableJobsGameTests.aSwitchedOffJobIsLeftOut` (config tailors off), `TableJobsGameTests.theTableSendsAShippedBuildsJobsAndBeds`
(the Fishers' Hut and the Starter Cottage through `TableServer.details` and over the wire), `TableJobsGameTests.theLinesAreInEnglish`.

Showcase scenes: `table` and `table_preview` (the dev client runs with the switch on, so the Table there shows the
starting set unlocked and the rest locked or ???; `table` checks the Starter Cottage's "Beds:" line shows).
