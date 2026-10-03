# The full check before release (ROADMAP 21.2)

Done in pieces by night runs; each piece lands with `land --keep-open`. Newest first.

## Pieces

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
- 3 more mutants, and tests for `BuildSite.java` lines 244 and 271 (above).
- The next `inventory.py` gaps: 14 mixins, 7 network payloads and 9 config keys no test mentions.
