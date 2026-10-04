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

## Still to do
- An old world saved by 0.138.0 opened with the next version (at the release check).
- Performance on GitHub's machines once B14 is fixed (the dev-container numbers above stand meanwhile).
