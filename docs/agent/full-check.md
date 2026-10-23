# The full check before release (ROADMAP 21.2)

Done in pieces by night runs; each piece lands with `land --keep-open`. Newest first.

## Pieces

### Network packets (lane-a-1003-2232, 2026-10-03)
- **Every payload over the wire** (`NetworkPayloadsGameTests.everyPayloadSurvivesTheWire`): all nine (the table's
  Open, RequestDetails, Details, Take, UploadChunk and UploadResult, mail's Send, the preview's Request and Data) come
  back unchanged, at their limits too (a full 30,000-byte upload piece, a 254-character file name, a 256-character
  letter in two-byte letters, a block at the world's corner). Passes.
- **Oversized client packets** (`oversizedClientPacketsAreRefused`): an upload piece over 30,000 bytes, a file name
  over 256 characters, a recipient name over 32 and a letter over the codec's limit are refused. Passes.
- **The mailbox's Send button through the real packet path** (`theMailboxSendPacketPostsAParcel`, the first test of
  `Mail.send` at all): a blank or unknown name posts nothing and keeps the top row; a real name posts the items with the
  trimmed letter first; a Send with no mailbox open does nothing. Passes.
- **The table's Take button through the packet path** (`theTableTakePacketGivesTheBlueprint`): one Blank Blueprint
  for one blueprint; an unknown id costs nothing. Passes.
- **3 mutants**, all killed: Send's handler given its arguments swapped, Take's handler unwired, the letter not
  trimmed. 31 mutants in all; no bug in the code.
- **The 14 mixins `inventory.py` lists as untested** are a name-matching gap only (it counts a mixin as tested when a
  test names its class). Each has a behaviour test through the game's own path: `PoiManagerMixin` in
  `StationsBugGameTests.oldWorldsBlocksBecomeWorkstationsWhenTheChunkLoads`, `ServerLevelMixin` in
  `JobSiteTicketsGameTests`, `ExplosionMixin` in `WardingEdgeGameTests`/`QaWardingGameTests` (real `explode`),
  `PlayerListMixin` in `FerryEdgeGameTests`/`FerryLeaveQaGameTests`, `AbstractVillagerMixin` in `ShopGameTests`
  (`notifyTrade`), `VillagerPanicTriggerMixin` in `GuardGameTests` (the guard never panics), `VillagerSeatMixin` in
  `RidingSpecGameTests` (a villager's seat is a player's), `ZombieVillagerMixin` in `StructureVillagerGameTests`; the
  four accessors are used by the features they serve, and `VillagerModelMixin` is client-only (the showcase's riding
  scenes show it). No new test needed.

### The job switchboard: VillagerGoalPackagesMixin (lane-a-1003-2132, 2026-10-03)
- **Every job's work package** (`GoalPackagesMixinGameTests.eachOfOurJobsGetsItsOwnWorkPackage`): each of our 30
  professions gets exactly the package its job class builds, none gets vanilla's, and every profession
  `ModVillagers.isWorker` names has a case (a new job without a branch would stand idle). Passes.
- **Upgraded vanilla jobs** (`upgradedVanillaJobsPutTheirWorkFirstAndGateVanillas`): mason, armorer, toolsmith,
  weaponsmith, fletcher, shepherd, butcher, leatherworker, cleric, librarian, cartographer and fisherman run our work
  first (two behaviours for the weaponsmith and librarian, in order) and vanilla's routine behind a gate, except the
  always-run schedule update. Passes.
- **Guards** (`guardsGetTheirRaidAndCombatPackages`): their raid, pre-raid and core packages are `GuardPackages`',
  and a farmer's or builder's are not. Passes.
- **3 mutants**, all killed: the chef given the carpenter's package, the guard's pre-raid branch switched off, and
  `UpgradedJob` leaving vanilla's routine ungated. 28 mutants in all; no bug in the code.

### VillagerMixin behaviours (lane-a-1003-1532, 2026-10-03)
- **Day plans** (`VillagerMixinGameTests.eachKindOfVillagerGetsItsDayPlan`): after `refreshBrain`, a builder and a
  netherworker keep the builders' working day, a guard the guard shifts, a bard the bard's evening; a baby builder,
  guard or bard, a librarian and a farmer with no field of ours keep vanilla's plans. Passes.
- **The away netherworker** (`nobodyTradesWithAnAwayNetherworker`): clicking him while his Nether trip is saved does
  nothing and opens no trade; once the trip is over he trades again. Passes.
- **2 mutants** planted by hand in `VillagerMixin`: the away check's `PASS` removed (killed: "should do nothing, not
  CONSUME") and the bard plan given to babies (survived at first: no baby bard was tested; the case was added, then
  killed). 25 mutants in all; no bug in the code.

### Build-site edges, config keys, the last mutants (lane-a-1003-1432, 2026-10-03)
- **`BuildSite.java` lines 244 and 271** (last run's survivors): `BuildSiteStepsGameTests` steps a test-hut site
  through its stages the way the builder does. While the builder retries the steps it put off, the material look-ahead
  lists exactly those (from the one it's on), then the decoration; progress is 0% while clearing, counts each block,
  counts the whole stage while retrying, and is 100% once landscaping starts and when done. Both pass.
- **5 more mutants** on those lines (`mutate.py --file BuildSite.java --lines 244-279`): 5 of 5 killed (275 constant,
  271 `==`, 244 `<`, 249 `&&`, 245 the deleted `add`). With the 17 above, 22 mutants in all; no bug in the code.
- **The 9 config keys no test read** (`inventory.py`): `ConfigGameTests.everyRadiusAndVillageNumberIsReadClampedAndApplied`
  reads `lumberjackRadius`, `fisherRadius`, `partnerRadius`, `explorerRange`, `postmanRange`, `villageRadius`,
  `villageHallRadius`, `villageGrowthCap` and `treasuryPerWorker` from the file, clamps each at both ends of its
  range, defaults it when missing, and checks `apply()` puts it into effect.Passes;
  its one mutant (line 148, village sharing in gametests) is killed. 23 mutants in all.

### Saved data, mutants and the flake sweep (lane-a-1003-1333, 2026-10-03)
- **Saved data** (`inventory.py`'s biggest gap: 69 of 72 villager fields had no save-and-reload test):
  `SavedDataGameTests` sets a value on every field in `ModAttachments` (found by reflection, so a new field without a
  sample fails the test), saves and loads the villager twice and compares; a plain villager loads with none of them,
  and a removed field stays removed. Both pass: nothing is lost.
- **17 mutants** (of the 20; `mutate.py`, each file against its own test classes):
  - `work/Stations.java` 6 of 7 killed. The survivor (`<= REACH*REACH` to `<`, beehives) is equivalent: squared
    distances between block centres are whole numbers and 4.5² isn't, so it can't change anything.
  - `travel/FerryRides.java` 4 of 7 killed. Two real gaps, now tested in `FerryEdgeGameTests` (each fails on its
    mutant, checked): a player already riding something goes straight to the post with no boat
    (`aPlayerAlreadyRidingGoesStraightThere`), and only the ferryman rows when a plain villager stands closer
    (`onlyTheFerrymanRows`). The third (landing half a block off, `+ 0.5` to `- 0.5`) is harmless.
  - `build/BuildSite.java` 0 of 3 killed (`BuilderGameTests` only). Line 448, a damaged save's checks loosened: now
    tested by `BuildSiteSaveGameTests` (a site missing its placement, id or owner is skipped, no crash). Still
    untested: line 244 (`i < deferred.size()` to `<=`, the material list while retrying deferred steps) and line 271
    (`progress` when the plan or stage is at its edge).
  - No bugs found in the code itself: every survivor was a missing test.
- **Flake sweep over the whole suite**: the nightly's 5 full-suite runs were green in run 37112224528 (2026-10-03,
  commit b830663). https://github.com/jCondeData/minecraft-alive-workplace/actions/runs/37112224528

### Performance with many homes (night-1003-1046, 2026-10-03)
- `PERF=true PLOTS=40 tools/packtest/run.sh` on a dev container (7 GB, slow CPU; the real Cobbleverse 1.7.42 server,
  jar 0.138.0 from `main` 7e06692): 80 workers on 40 plots (a builder on each, plus miners, lumberjacks, porters,
  carpenters, masons). 40 of 47 sites working when measured, builds 0–100% after 2.5 minutes, quarries 24% dug.
  - Ticks with the workers: P50 58.8 ms / P95 114.5 ms right after they start, then P50 21.0 ms / P95 49.7 ms
    (the container's idle number is useless: it was still generating the 221 forced chunks).
  - Server thread profile (2962 samples): Alive Workplace code 11.8%, all villager AI 40.7% (ours included),
    villager pathfinding 17.7%. Ours by entry point: BuilderWork 6.9%, MinerWork 1.8%, LumberjackWork 1.3%, the rest
    under 1% each; the hottest of our own frames are `BuilderWork.breakBlock` 1.5% and `place` 1.1%. Nothing of ours
    stands out: the cost is vanilla villager AI and pathfinding, which 80 villagers cost anyway.
- **Found B14**: the nightly soak on GitHub has been measuring an idle server. In nightly run 37112224528 every build
  was still "Clearing the site · starting" at 0% with no villager found, villager AI 0% of the server thread. So the
  nightly "green" soak proved nothing about performance. `run.sh` now fails the soak when no site is working, so the
  next nightly goes red (and opens the issue) until B14 is fixed.
  **Cause** (lane-c-1003-1333): the Cobbleverse .mrpack stores all 1,646 overrides with Unix mode 000. Root (our
  dev containers) reads them anyway; GitHub's runner user can't, so every `cp` of the pack's configs, datapacks and
  bundled mods failed (hidden by `|| true`) and both nightly pack runs booted with every mod on its defaults. As a
  normal user, the old steps leave 0 of the pack's 223 configs readable. `run.sh` now `chmod`s the overrides after
  unzipping and stops if fewer configs arrive than the pack has.

### Ferry edges (night-1003-0845, 2026-10-03)
- **Leaving the game mid-ride**: `FerryEdgeGameTests.aPlayerWhoLeavesMidRideIsLanded` closes the player's connection
  20 ticks into a ride (the way quitting does) and checks they are landed by the far post, out of the boat, the boat
  gone and the ferryman back on his jetty. **Found B13**: the ride was ended from Fabric's DISCONNECT event, which fires
  from netty's `channelInactive` on the network thread when a client quits, so the teleport, boat and ferryman moves
  ran off the server thread. Fixed in B13 (a `PlayerList.remove` mixin ends the ride on the server thread before the
  save). Mutant check: with the mixin call removed the test fails ("left at ..., not by the far post").
- **A Travel Ticket between dimensions**: `FerryEdgeGameTests.aTicketToAnotherDimensionTakesYouThere` uses a ticket
  for a post in the Nether at an Overworld post: a boat ride, then the player is in the Nether by the post, standing
  on its floor, the post among the places they know, no boat left in the Overworld, the ferryman home. Passes.
- Already covered before this check: a ride boat left behind by a restart is taken away when it loads
  (`RidingSpecGameTests.aFerryBoatLeftByARestartIsTakenAway`); getting out early lands you
  (`aFerryPassengerWhoGetsOutEarlyLands`).

### Pack boot and soak (from the nightly workflow)
- Nightly run 36991976288 (2026-10-02, commit aeee531): full build, flake sweep, translation check, log audit, real
  modpack boot and performance soak all green.
  https://github.com/jCondeData/minecraft-alive-workplace/actions/runs/36991976288

### Old world opened with the new version (chat, 2026-10-02)
- A world saved by the real 0.137.0 jar on the Cobbleverse pack, with all 26 retired job blocks and their workers,
  opened with 0.138.0: 26/26 blocks, jobs and job sites kept, no errors from our mod (ROADMAP 21.4).

### Performance on GitHub's machines (lane-a-1004-0932, 2026-10-04)
- Nightly run 37193458879 (6b24c85, 2026-10-04), after B14's fix: the performance soak on GitHub's runner with the
  real pack, 80 workers on 40 plots, 39 of 46 sites working when measured; ticks 7.3 / 5.2 ms on average with the
  workers (p99 42.1 / 15.9 ms). From tonight the same step runs 25.1's 150 workers in three villages
  (docs/performance.md).
  https://github.com/jCondeData/minecraft-alive-workplace/actions/runs/37193458879

### A world saved by 0.138.0 opened with this version (lane-a-1004-0932, 2026-10-04)
- The real Cobbleverse pack (Java 21) with the released v0.138.0 jar: `/workplace benchmark 10` (20 workers: 9
  builders, 2 miners and their neighbours), a few minutes of work, saved and stopped. The same world opened with main
  (739d02e): `KEEP_WORLD=true SITES_ONLY=true tools/packtest/run.sh`.
- All 11 sites came back with their builders and miners, 9 of 11 working (as before the restart), no error or
  exception from our mod in the log. 9 sites went on (Berry Farm 91% → 100%, Research Lab 57% → 67%, quarries 29% →
  33%, ...). Two sites laying their foundation showed less progress after the restart (Lookout Tower 12% → 1%,
  Healing Center 19% → 7%): filed as B46.

### QA stage 2: pack, upgrade, benchmark and config matrix (qa-stage2, 2026-10-23)
- **Benchmark** (`PERF=true PLOTS=40`, dev container, 7 GB, Cobbleverse 1.7.42, Java 21, no Gradle daemons running),
  the released 0.147.0 jar against main 9ca9c84, back to back. Use these to compare next time:

  | jar | idle | with workers (2 tick queries) | heap after GC | our share of the server thread | sites working |
  |---|---|---|---|---|---|
  | 0.147.0 | 4.0 ms | 32.6 / 11.9 ms | 1418 MB | 22.4% | 39 of 46 |
  | main 9ca9c84 | 3.6 ms | 35.0 / 10.9 ms | 1379 MB | 23.7% | 39 of 46 |

  The mean of the two queries is 22.3 ms (0.147.0) against 23.0 ms (main), 3% apart, which is within the run-to-run
  noise. An earlier 0.147.0 run with Gradle daemons still in memory gave 44.1 / 15.5 ms. Kill the daemons first
  (`./gradlew --stop` and the Kotlin daemons), or the server is OOM-killed or slowed.
- **Upgrade**: the 0.147.0 benchmark world (45 sites) opened with main (`KEEP_WORLD=true SITES_ONLY=true`): 43 sites
  listed, 37 working, and no site lost progress. Most went up (Fletcher's Lodge 57% → 87%, quarries 44% → 60%); the
  rest were unchanged, most of them still clearing at 0%. The two that
  were gone had finished in between (Nether Gate at 95%, a Market Stall at 100% levelling). `logaudit.py` against the
  old jar's log: no new problems.
- **Pack boot** (main): no error from our mod. Neither jar's villages had any of the six workstations the script looks
  for, so this check can't tell whether our houses spawn. The script's `blueprint\(s\) available` grep no longer
  matches the command's "131 blueprints available" line.
- **Config matrix** (`QaStageTwoGameTests`, `QaStageTwoCompatTests`): each of the 107 switches, turned off in the
  config (on, for those off by default), changes its own system's flag and no other switch drives the same flags.
  With the gates closed as in a release, the 79 expansion switches change nothing. Every switch off at once still
  lets a Builder build. 4 of 4 planted mutants were killed: a switch wired to the wrong flag, a switch ignored, a
  switch inverted and a gate dropped. `villageHabitats` was the one switch whose flag no test had turned off; it now
  has a compat test.

### QA stage 3: mutation sweep, untested items and entities, visual pass (qa-stage3, 2026-10-23)
- **Mutation sweep** (`mutate.py --file ... --no-baseline`, each file against the test classes that use it; the compat
  set's baseline, 74 tests, passed first): **20 mutants, 11 killed by the existing tests, 17 after the new ones.**
  - Compat, 5 of 5 killed: Tom's/Sophisticated storage networks (`StorageNetworks` 406, 204), Raid Dens (`RaidDensRaids`
    82), Megas (`CobblemonMegas` 87), Create builds (`CreateBuilds` 60).
  - GameTests, 6 of 15 killed at first (`BuildSite` 317 and 876, `FreeJobsPage` 207, `BedLadders` 101, `MaterialRules`
    312, `VillageHalls` 201). Six survivors now fail a test in `QaStageThreeGameTests` (each checked on its mutant):
    `FreeJobsPage` 509, a builder moved to a free table from the hall page doesn't walk there (no test went through the
    page for a builder); `BedLadders` 194, the ladder walk to a bed allowed to leave its 8-block reach; `MaterialRules`
    358, chests and barrels no longer protected while clearing; `BuildSite` 940, the scaffolding forgotten over a save;
    `VillageHalls` 230, a villager whose bed is on the village's edge not called home; `BuildSiteManager` 325, the
    blocks a repair gave up on forgotten over a save.
  - Three survivors stay: `BuilderWork` 1252 (the landscaping shortcut that skips every hole short of dirt at once:
    only slower without it, so only a timing test would see it), `BuilderWork` 457 (stocking up before clearing for a
    helper or with free materials: no visible difference found) and `BuildSiteManager` 316 (`<` to `<=` over the saved
    list: reading one past the end gives an empty entry that is skipped, so it's equivalent).
  - The run that didn't count: `BuildSite` 906 "killed" while the sweep was being stopped.
- **Items, components and entities no test used** (stage 1's inventory), in `QaStageThreeGameTests`, through the
  player's own entry points (right-clicks through `ServerPlayerGameMode`, crafting through the recipe manager, saves
  through `ItemStack.save`/`parse`, the network through `ItemStack.STREAM_CODEC`):
  - Delivery Note: up to 9 kinds from the other hand, a 10th not taken, a kind removed by a second click, a block that
    isn't a container refused, a sneak-click clears the marks but keeps the kinds, the route over a save and the
    network.
  - Scan Tool: two corners then sneak-right-click the air makes a 3×2×2 blueprint from one Blank Blueprint; unmarked,
    a plain click on the air and a 49-wide box are refused (48 is taken); an anvil name with a full stop is kept whole.
  - Village Banner recipe: any banner plus one gold ingot anywhere in the grid keeps its colour and patterns; two gold,
    two banners, a stray stick, a banner or gold alone make none.
  - Data components (`patrol`, `city_plan_hall`, `colony_charter`, `city_plan` with every part set, `made_day`, `scan`,
    `route`, `village_banner`) come back equal after a save and over the network; `supply_report` goes over the
    network and is never saved.
  - Entities: none of the four can be `/summon`ed; the bobber isn't saved and goes when its fisherman dies or walks
    17 blocks off; a child in a cradle is saved with its seat and let go when the cradle breaks; a stand seat with no
    Cup lets its villager go outside the stair; a furniture seat loaded from a save stands its villager up.
  - 18 tests, each run 5 times in its own batch (`RepeatNewTests`): 90 of 90 passed.
  - The first full build failed `BlueprintUnlocksGameTests.startingSetUnlockedRestLockedRareHidden` ("a ??? build's id
    went to the client"): the scan test had named its build "St. Mary's Chapel", the scan is listed in the Blueprint
    Table for everyone, and that test looks for the word "chapel" on the wire. The scan is now "St. Ives Mill". Scans
    stay in the test world's `generated/` folder between local runs.
- **Two bugs**, failing tests on branch `tests/qa-stage3-delivery-note` (`QaStageThreeBugTests`):
  - **B333**: right-clicking a chest or barrel with a Delivery Note opens it (a `ChestMenu`) and the note stays blank,
    so a route can't be marked the way the CHANGELOG says. No test had clicked through the server; the old tests set
    the route directly.
  - **B334**: with 9 kinds on the note, a 10th in the other hand is answered "Stop carrying Emerald" though it was
    never on the note.
- **Visual pass** from last night's showcase stills (the real client on GitHub, not run here): `storage_hall`,
  `hall_jobs` and `create_builds_more` show no missing textures or untranslated text. In `hall_jobs` the porter's
  overhead lines run off the frame because the camera stands close; `create_builds_more`'s stills are too far away to
  show the machines turning.

### QA stage 4: every entry point clicked like a player (qa-stage4, 2026-10-23)
B333's lesson (a Delivery Note on a chest opened the chest; the tests had set the route directly) hunted everywhere.
- **The entry points.** Items with their own click: Blueprint, Scan Tool, Settler's Wagon, Colony Charter, Field
  Marker, Patrol Map, Village Banner, Delivery Note, Quarry Marker, Wonder Plan, Rally Banner, Remedy. Blocks with their
  own click: Builder's Bench (and Postal Desk), Bounty Board, Grave, Village Hall, Mailbox, Shop Counter, Drop Box,
  Storehouse, Blueprint Table, Travel Post, Foundation Stone. Fourteen use-entity handlers through `Platform`
  (`BuilderEvents` holds most villager clicks: jobs, hires, markers, Legends), two use-block handlers (Cup matches,
  village protection), nine client packets, and `/workplace`. No item uses `interactLivingEntity`, so every villager
  click goes through the use-entity callbacks, which run before vanilla's trade screen. Before this stage only the
  Gift, Stations, Caravan, site-list, protection and two payload tests sent the real packets; most items and hires
  were tested by calling their handler (`Fields.assign`, `PatrolMapItem.giveTo`, `RemedyItem.give`, `makeCamp`...).
- **`QaStageFourGameTests` (21) and `QaStageFourCompatTests` (2)**, all clicking the way the client does: a block click is
  `ServerPlayerGameMode.useItemOn` for the main hand then the off hand, each followed by the item's own use; a villager
  click is the real `ServerboundInteractPacket`; a screen's change is the real custom payload; commands go through the
  dispatcher as a survival player who isn't an operator. Expectations from README, CHANGELOG and en_us.json. They cover:
  every marker from the off hand, in survival and creative, and a sneak-click clearing it; a Field Marker corner on a
  composter; the bench and a Travel Post clicked holding a stick or sword; a Field Marker taken by a Farmer with a
  composter (start line, marker used); a Quarry Marker on a Miner with no furnace, sneaking and not; a blank Delivery
  Note on a Postman; a Patrol Map sneak-given to a guard; an unplaced blueprint on a builder, sneaking and not; the
  Remedy (one used, the bottle back, dropped with a full bag, nothing used in creative); nine sneak-click hires
  (Armorer, Toolsmith, Weaponsmith, Fletcher, Shepherd, Butcher, Cleric, Librarian, Cartographer), each next to a
  rival block the same item picks another job at; a stranger refused another's worker; the Nurse; the quarry depths
  (4, 8, 16, 32, 64, strip mine) from sneak-clicks; eight patrol points at most; twelve rally guards, the thirteenth
  refused, one let go, raise and lower; the shop log with an empty hand; the Settler's Wagon on the ground and a second
  one refused; the Scan Tool's save with a full bag; the material list from a blueprint and on a bench; the README's
  command table (sites, mail, quests, standing, friend list, strip in either hand; the four op commands refused); the
  City Plan's edit packet from a stranger (refused, told) and from the owner; a Delivery Note on a Cobblemon pasture's
  either half; a Habitat Keeper's lure spot from a marked Field Marker, and her lure picker from a sneak-click.
- **Flakes**: the 19 GameTests then written ran 5 times (whole runs): all passed every time. **Mutants**: 7 planted in
  `RallyBannerItem`, `RemedyItem` and `Hiring`, 6 killed at once; the survivor (`Hiring` 27, the refusal naming "?"
  instead of the employer) is now killed by `aStrangerCannotHireAnothersWorker`. (Two players made in one tick share a
  name in these tests; the stranger is made 3 ticks later.)
- **Full build** green: 2632 GameTests and 494 compat tests passed; the log audit found no errors (18 expected warnings).
- **Three bugs**, failing tests on branch `tests/qa-stage4-clicks` (`QaStageFourBugTests`):
  - **B335**: the Scan Tool on a chest, barrel, crafting table, furnace, trapdoor or Builder's Bench opens, flips or
    answers the block and marks no corner.
  - **B336**: the shop owner's sneak-right-click on the counter shows no sales log while he holds anything.
  - **B337**: a Book and Quill on a builder's Blueprint Table opens the table, no material list (B279 says "their
    Builder's Bench"; maybe only the docs need changing).
- B333's fix (pulled during this stage) covers the pasture too: that compat test failed before it and passes now.
- **Not tested**: `/workplace friend add` and `remove` (the GameTest server has no profile cache, so a player name
  can't be looked up: "An unexpected error"); the Bounty Board, Grave, Mailbox, Drop Box, Storehouse and Foundation
  Stone clicks; the story, guild, sickness and Legend villager clicks beyond the existing tests; the Colony Charter,
  Table and Preview packets beyond the existing tests; a real client (no screenshots this stage).

### QA stage 5: the blocks and packets stage 4 left unclicked (qa-stage5, 2026-10-23)
- **`QaStageFiveGameTests` (13)**, clicking with stage 4's `press` (main hand then off hand, block first) on a player
  whose connection keeps what the server sends, so a screen that opens by packet (the Blueprint Table's `Open`, the
  preview's `Data`, `Details`, `UploadResult`) is seen too. Each block is clicked six ways (empty hand, dirt or a sword in
  the main hand, dirt in the off hand only, sneaking empty-handed, a full bag), in survival and creative, and the block
  in the hand must not be placed: the Grave (a real one, dug by `Graves.onDeath` for a named farmer) shows its epitaph
  with the name; the Foundation Stone its status; the Bounty Board with no hall near says so (a Captain's Insignia
  isn't used up); a Mailbox placed by a player opens for him every way, and another player is refused, told whose it
  is; the Drop Box opens; a Storehouse placed by a player opens its requests board; the Blueprint Table sends its
  library, also with a blueprint in either hand. Packets, sent as the client sends them: the Stored tab (Store from 30
  blocks or at a chest's position does nothing; TakeStored claiming 64 hands back the one stored; a diamond, far away,
  a chest, a full bag and a second take give nothing, "not stored"); the library's Take ("take a copy for a Blank
  Blueprint": none without a blank, "need_blank"; far, a chest or unknown, path-like or empty ids cost no blank; one
  blank, one Market Stall; a full bag drops it at the feet, never lost; free in creative); details and preview requests
  (the Market Stall's details and preview; the same preview not resent at once; bad ids get nothing; spoofed upload
  sizes and offsets all refused, the player still connected); the mailbox's Send (no name, an unknown name with the
  name in it, nothing to send; from a player with no mailbox open, nothing at all); a Captain's Insignia from a real
  killed captain, right-clicked from the main hand on the board and on the hall, paid.
- **Mutants**: 4 planted (`MailboxBlock.mayOpen` always true, `TableLibrary.takeOut` without the distance check,
  `PreviewNetworking`'s repeat guard off, the epitaph only for an empty hand): all 4 killed.
- **Flakes**: the 13 ran 5 more times on their own: one failure, a test's fault (the insignia tests' hall stood on
  after them and a later "no hall near" board found it); fixed by removing the hall and board when the test ends and
  looking only 4 blocks round the no-hall board. Then 5 runs green (4 after the first fix, 1 after the second).
- **Full build** green: 2645 GameTests and 494 compat tests passed; the log audit found no errors (18 warnings, as in stage 4).
- **One bug**, failing tests on branch `tests/qa-stage5-clicks` (`QaStageFiveBugTests`):
  - **B338**: a Captain's Insignia in the off hand (main hand empty), right-clicked on the Bounty Board or the village
    hall, opens the page and pays nothing; from the main hand it pays.
- **Not tested**: the Colony Charter (switched off until 1.7); handing in Wonder goods at the Foundation Stone (Wonders
  are switched off; only the status was clicked); the Storehouse board's own clicks (handing items to a worker); a
  real upload from a player allowed to upload; a real client. Noted, not filed: `PreviewNetworking` remembers every id
  a client asks for until it leaves, so a modified client sending endless made-up ids grows the server's memory.

## Still to do
- B335, B336 and B337 (the bug lane), with the tests on `tests/qa-stage4-clicks`; B338 with `tests/qa-stage5-clicks`.
- The clicks stages 4 and 5 left untested (above).
- A timing test for `BuilderWork` 1252 if the landscaping shortcut ever matters to a player.
