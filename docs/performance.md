# Performance

How much server time Alive Workplace costs in a big world, measured the same way every week (ROADMAP milestone 25).

## The benchmark (25.1)

- **What runs:** the real Cobbleverse pack (every mod in it) on a dedicated server with the mod's jar
  (`tools/packtest/run.sh`), Java 21, `-Xmx5G`, view distance 6.
- **The load:** `PERF=true VILLAGES=3 PLOTS=25`: three villages 400 blocks apart, each with 25 plots; on every plot a
  builder putting up a starter build (materials free) and, by turns, a miner with a quarry, a lumberjack, a porter at a
  storehouse, a carpenter or a mason. 150 workers in all, every chunk of the villages force-loaded.
- **What is measured:**
  - `tick query` before the workers arrive (the pack's own cost) and twice with them: average, p50, p95 and p99 of
    the server tick;
  - the heap after a full GC, with everything still loaded and working;
  - a 90-second Java Flight Recorder profile of the server thread: the share of its samples in our code, and where
    (`tools/packtest/perf.py`).
- **Where:** GitHub's `ubuntu-latest` runner (4 CPUs, 16 GB), job `benchmark` in `.github/workflows/nightly.yml`, every
  Sunday at about 11 PM Central (the `41 4 * * 0` schedule), or on demand: run the nightly-tests workflow with
  `part: benchmark`. The numbers are in the run's summary; the full log and the profile are in its `benchmark-<run>`
  artifact.
- **The run fails** when no worker was working while it measured (the numbers would be an idle server's).

## Results

One row per run, newest first. Ticks are milliseconds.

| Date | Commit | Workers | Idle p50 / p95 | Busy p50 / p95 | Heap after GC | Our share of the server thread | Run |
|---|---|---|---|---|---|---|---|
| 2026-10-11 | 09fe0f7 | 150 (68 of 78 sites working when measured) | 2.4 / 3.5 (p99 15.8) | 11.1 / 19.7 (p99 27.7), then 8.3 / 14.1 (p99 19.2) | 1638 MB | 12.9% (153 of 1184 samples). Our code a tick (the mod's timer, 25.3): p50 1.0 to 1.1 ms, p99 4.2 to 12.2 ms, **0 of 2,400 measured ticks over 50 ms** (worst 44.5 ms, `PorterWork` 22.7 ms); one of 155.6 ms during the setup (`BuilderWork`'s biggest call 26.4 ms) | [nightly-tests 38166303120](https://github.com/jCondeData/minecraft-alive-workplace/actions/runs/38166303120) (`part: benchmark`, GitHub's runner, started by the owner) |
| 2026-10-05 | 5b489b7 | 150 (65 of 76 sites working when measured) | 2.3 / 3.5 (p99 7.3) | 18.3 / 32.8 (p99 66.1) at the start, then 8.8 / 16.5 (p99 21.1) once the builds settled | 1613 MB | 13.1% (204 of 1556 samples; 49.8% villager brains, 26.5% pathfinding, both ours included) | [nightly-tests 37297003813](https://github.com/jCondeData/minecraft-alive-workplace/actions/runs/37297003813) (the nightly's performance step, same load as the Sunday job) |

**With Create (lane E QA, 2026-10-25):** the same load run twice back to back on this 7 GB dev container (main
6e82b3d, Fabric Loader 0.19.3, not GitHub's runner, so not a row above), once with the owner's Create port
(`libs/create-fabric-6.0.10.0+mc1.21.1.jar`) added to the pack and once without. Create adds nothing measurable:

| | Idle p50 / p95 | Busy p50 / p95 (p99), first then second window | Heap after GC | Our share | Ticks with over 50 ms of ours |
|---|---|---|---|---|---|
| With Create | 4.3 / 10.0 | 55.5 / 103.1 (1342), then 16.8 / 40.9 (115.5) | 1829 MB | 21.8% of 2731 samples | 45 (23 on the CPU) |
| Without | 5.1 / 8.3 | 50.1 / 95.9 (1392), then 33.2 / 69.2 (126.7) | 1893 MB | 21.2% of 3163 samples | 49 (28 on the CPU) |

None of our Create code (Mechanic, machine work, kinetics) shows in either profile; the share over 15% and the long
BuilderWork ticks are this container's B412, with or without Create.

**The village hall's radius at its top (lane E QA, 2026-10-26):** `villageHallRadius` 160 (the config's top; every
village check, the 36.20 reach included, searches that far for a hall) against the default 64, back to back on this
container with Create on the pack (main e84f78a, `PERF=true PLOTS=40`, 80 workers, Fabric Loader 0.19.3). No cost
seen:

| villageHallRadius | Idle p50 / p95 | Busy p50 / p95 (p99), first then second window | Heap after GC | Our share | Ticks with over 50 ms of ours |
|---|---|---|---|---|---|
| 64 | 0.9 / 1.5 | 11.9 / 26.3 (231.5), then 4.4 / 11.8 (21.0) | 1437 MB | 25.5% of 830 samples | 9 (3 on the CPU) |
| 160 | 0.8 / 2.9 | 13.3 / 37.1 (185.4), then 4.5 / 13.0 (24.1) | 1512 MB | 21.3% of 798 samples | 9 (4 on the CPU) |

Our code a tick (the mod's timer): p50 1.3 then 0.8 ms at 64, 1.2 then 0.8 ms at 160.

## A season under the edicts (30.22)

`SEASON=true tools/packtest/run.sh`: a City of 35 villagers (16 with jobs) on the pack server, 8 in-game days at full
speed, profiled for the whole run (`perf.py season.jfr --days server.log`). The run fails when our share of the server
thread is 15% or more on any day, or any tick over 50 ms had over 50 ms of our code (its length times our share of its
profile samples). The daily numbers are in `docs/design/M30.md`.

| Date | Run | Sprint | Our share, day 1 | Our share, days 2 to 8 | Ticks over 50 ms | Of them ours |
|---|---|---|---|---|---|---|
| 2026-10-13 | B103 after (`SEASON_PART=edicts SEASON_DAYS=1`; nearest hall once a tick, the growing-up warm-up) | 479 ticks a second (2.09 ms a tick) | **14.7%** | not run | 5 | 1: 51 ms (2 of 2 samples, both the harness's pace of every villager at once, `SeasonSoak.everyone` > `Moods.of` > `Homes.building`) |
| 2026-10-13 | B103 midway (same one day; nearest hall once a tick, no fastutil warm-up yet) | 527 ticks a second (1.90 ms a tick) | **17.7%** | not run | 5 | 2: 69 ms (cold `FieldWork` code and a vanilla packet class loaded by a swing) and 53 ms (`Cradles.round`, Lithium loading fastutil's double collections as a child grows up) |
| 2026-10-11 | B103 before (lane-c-1011-1833, main, the same one day) | not recorded | **16.7%** | not run | not recorded | 4: 254 ms `Cradles.round` (the same fastutil load), 127 ms, 84 and 76 ms (the harness's `VillageHalls.nearest` and `Pace.of`) |
| 2026-10-10 | 4 (the reformed half alone, 4 days) | 794 ticks a second (1.26 ms a tick) | **17.2%** | 12.7% to 14.7% | 6 of 96,000 | 1, on day 1: 296 ms of a 296 ms tick, the same lookup (B101) |
| 2026-10-10 | 3 (the fields growing) | 814 ticks a second (1.23 ms a tick) | **17.1%** | 12.4% to 14.1% | 17 of 192,000 (worst 425 ms) | 2, both on day 1: 402 ms of a 424 ms tick in the crafters' first recipe lookup (B101), and a 98 ms tick with one sample, in a farmer's harvest |
| 2026-10-10 | 2 (nothing growing: the store ran out) | 718 ticks a second (1.39 ms a tick) | **16.6%** | 9.2% to 13.1% | not counted properly (it counted the ticks before the sprint) | 307 ms of a 333 ms tick on day 1, the same lookup |
| 2026-10-10 | 1 (composters too far from the chests) | 814 ticks a second (1.23 ms a tick) | **17.1%** | 12.1% to 14.3% | not recorded | not recorded |

## The Steward's cost: a village from a plan (27.22)

- **What runs:** `CITY=true PERF=true tools/packtest/run.sh`, the same pack server as the benchmark. `/workplace city`
  lays out a plains village round a Village Hall at 1000, 75, 1000: a Steward in Run the village, 3 builders, a
  storehouse with its porter, 12 more villagers and an old vanilla house; a plan with Homes (renewing old houses),
  Workshops, Farms, Market, Gardens and Keep Clear, two streets and a wall line, and a raid on record so the village
  wants its wall. The Steward runs it for 6 in-game days at full speed (`/tick sprint`); then he starts nothing new and
  the builders get up to 2 more days to finish what he started. The storehouse is kept stocked as a player keeps it:
  each build he opens has its list put in (and what the list grows by later), counted as stocked.
- **What is measured:** each server tick, the time spent in the Steward's own work (`city/StewardCost`): his planning
  at the hall (rules, wishes, the desk, walls and renewals proposed), his morning rounds, the plot searches, the
  old-house surveys, and the roads and walls ticking at the hall. Per village (one here): p50, p95, p99 and the worst
  tick, with the slowest single call named. The `City result:` line also counts builds started and finished, any
  outside its zone or in Keep Clear, stalls, meals eaten from the store and every item whose count is off.
- **Target:** under 0.5 ms a tick per village at p95.

| Date | Commit | Builds the Steward started → finished | In zone / Keep Clear | Stalls | Items off | Steward p50 / p95 / p99 / worst (ms) | Whole server |
|---|---|---|---|---|---|---|---|
| 2026-10-05 | 27.22 run 3 (food in the store, lists topped up) | 14 → 14 (6 buildings, 8 road segments) in 6 days | all in zone, none in Keep Clear | 0 | none (133 meals eaten, counted) | 0.005 / **0.591** / 1.208 / 881.9 (slowest call: planning, 710 ms at tick 3186) | 515 ticks a second sprinting (1.94 ms a tick); our share 13.2% of 6352 samples |
| 2026-10-05 | 27.22 run 2 (lists not topped up) | 4 → 2 (lamp lanterns short: 16 stalls) | all in zone | 16 | apple -8 (before babies' and banquets' meals were counted) | 0.004 / 0.050 / 1.110 / 135.4 | — |
| 2026-10-05 | 27.22 run 1 (no food in the store) | 11 → 10 (the Farmstead's crops eaten: B84) | all in zone | 7 | carrot -9, potato -8, wheat_seeds -18 | 0.005 / 0.462 / 1.247 / 197.9 | 512 ticks a second sprinting; our share 13.3% |

**Reading it:** the p95 lands on an edge. His planning runs once a second, so 1 tick in 20 carries it (about 0.5 to
1.2 ms) and the 95th percentile falls right between those ticks and the free ones: 0.05, 0.46 and 0.59 ms in three runs
of the same village. Two of three are under the 0.5 ms target, the last is not, and the first planning call took
710 ms (an 882 ms tick). Both are B85. The whole run is cheap otherwise: the server sprinted at about 515 ticks a
second with the village working. A Hamlet (this village's rank till it has 5 finished buildings) lets the Steward keep
one build open at a time, so 6 days give 6 buildings and 8 road segments; the wall (approved on day 1) and the old
house's renewal (on his desk all week) never got a turn: B86.

## The pack's performance stack (25.4)

Cobbleverse ships its own performance mods, and the benchmark, the soak and the nightly pack boot all run with them,
because they run the whole pack: Lithium 0.15.4, C2ME 0.4.0-alpha.0.23, FerriteCore 7.0.3, ModernFix 5.25.1, Krypton
0.2.8 and ScalableLux 0.1.0.1 on the server (Sodium, EntityCulling and ImmediatelyFast are client mods: the server
leaves them out). On Java 21 the loader leaves out two of C2ME's nested modules, its native maths and its density
function compiler, which need Java 25.

The client side: `PERF_STACK=true tools/screenshots/run.sh` (with any `SCENE`) adds the same nine mods, the pack's
versions, to the screenshot client (`tests.screenshot_perf` in `stonecutter.properties.toml`), with those two C2ME
modules left out as on a real Java 21 install.

| Date | Check | Result |
|---|---|---|
| 2026-10-11 | 60-minute soak (nightly-tests [38166107551](https://github.com/jCondeData/minecraft-alive-workplace/actions/runs/38166107551), 09fe0f7, GitHub's runner) | No error of ours. Heap after GC 1,637 / 1,634 / 1,597 / 1,567 / 1,578 / 1,572 / 1,467 MB at minutes 0 to 60: it fell 10.2% from minute 10 and never grew, so no leak sign; the run failed only because the check then was two-sided (now it fails on growth only). Server ticks p50 23.0 ms at the start, 4.4 to 6.8 ms later. Our share 11.4% (843 of 7,388 samples). Our code a tick: median p50 0.6 ms, worst minute's p99 13.9 ms; 7 ticks with over 50 ms of ours in the hour: 1,759.1 ms (`BuilderWork` 1,757.9 ms) at 19:10:30, 752.6 ms (`CrafterWork` 746.0 ms) at 19:10:44, 658.5 ms (`LumberjackWork` 657.2 ms) at 20:00:43, 413.1 ms (`BuilderWork` 411.5 ms) at 19:13:34, 218.4, 114.8 (`MinerWork` 85.4 ms) and 97.8 ms during the setup. The 1.7 s call was a stall, not builder work: the profile has 143 ms of samples then, one in a builder, and it came as the harness's first 90-second recording ended; the 746 and 657 ms calls fell on the second the harness ran its full GC (`jcmd GC.run`, minutes 0 and 50). This build predates the stall figures (GC pauses, time off the CPU), which label such ticks now. Rerun after 25.4's fix: pending |
| 2026-10-04 | Client (`SCENE=config PERF_STACK=true`) | 96 mods, all nine performance mods loaded; the scene's 2 checks pass and the world renders behind the screen; only warnings are other mods' optional mixins and the container's missing sound device |
| 2026-10-05 | Benchmark load on the pack's stack (nightly-tests 37297003813, 5b489b7) | Runs clean with the six server mods: our share 13.1% (target under 15%); busy p99 66.1 ms at the start, 21.1 ms later (target: none over 50 ms from us, not yet attributed); heap 1613 MB after GC once, but no 60-minute soak yet, so the flat-heap target is unmeasured |
| 2026-10-04 | Pack boot (`tools/packtest/run.sh`, 0.138.0 + 25.5) | 252 mods loaded, the six server mods among them; no error from Alive Workplace or the performance mods (the 37 error lines are the pack's own: empty registries, data fixers, Cobblemon dex files) |

### The 60-minute soak

- **What runs:** the benchmark's load (`PERF=true VILLAGES=3 PLOTS=25`) on the pack's server, with its performance
  mods, kept running for an hour: `PERF=true VILLAGES=3 PLOTS=25 SOAK_MINUTES=60 tools/packtest/run.sh`.
- **What is measured:** at minute 0, 10, 20 ... 60 (`HEAP_EVERY=10`), the benchmark's own measurement: a 90-second
  profile (`soak-<n>.jfr`), two `tick query` lines (p50 / p95 / p99), then the heap after a full GC (after the profile
  and the queries, so the collection's pause isn't counted). Each reading prints as `Soak heap after GC, minute N`.
- **The verdict:** `tools/packtest/soakheap.py` judges the readings from minute 10 on (`SOAK_SETTLE`; the builds fill
  up the first minutes) against the minute-10 one and prints the `Soak verdict:` line. The run fails when any is more
  than 5% **above** it, or fewer than two were taken. Only growth fails: a heap that falls is not a leak (decided
  2026-10-11, after the first soak's heap fell 10.2% as its builds finished and the old two-sided check failed it);
  a fall past 5% is still printed (`lowest -N% ... (a fall is not a leak)`). `python3 tools/packtest/test_soakheap.py`
  checks the verdict (growth fails, a fall passes).
- **The p99 spikes:** `perf.py --spikes soak-*.jfr` reads all the profiles together: our share of the server thread,
  then every busy stretch of 50 ms or more (the thread sleeps between ticks, so a run of samples with no gap over 25 ms
  is one tick, or ticks back to back), with our share of its samples and the frames it spent them in, and the garbage
  collector's pauses of 20 ms or more. An estimate: time the thread spends blocked or paused isn't sampled.
- **Our code's own time a tick (25.3):** the profile can't tell a long tick of ours from ticks the server runs back to
  back to catch up, so in the benchmark the mod times itself. Once `/workplace benchmark` has placed its workers,
  every job behaviour of ours (start, tick and stop, `mixin/BehaviorMixin`) and every server and level tick handler of
  ours (wrapped by the platform) is measured with `System.nanoTime()` and added up per server tick
  (`command/OurTickCost`). The log gets a `Benchmark: our code a tick over the last 1200 ticks: p50 / p99 / max` line
  each minute (the max with its biggest single call) and a `Benchmark: a tick with N ms of our code` line for each tick
  with over 50 ms of ours. `perf.py ... --ticks server.log` (run.sh passes it) sums them up and, with `--spikes`, says
  for each long stretch whether a tick of ours over 50 ms was logged then. Only with `-Daliveworkplace.benchmark=true`
  (the pack test's measuring modes); on a normal server the handlers aren't wrapped and the behaviours' check is one
  field read.
  A long tick isn't always ours: a garbage collection pause stops every thread, and on a loaded machine the server
  thread can wait for the CPU, both in the middle of one of our calls. So each tick also reads the collectors' pause
  total and the server thread's CPU time once, and each long tick's line says the tick's own length, its GC pauses,
  the time the thread was off the CPU, and `ours on the CPU`: our time without the larger of the two (an estimate; a
  stall may have fallen outside our calls). The target is judged by that last figure; the minute lines and `perf.py
  --ticks` count both (`ticks with over 50 ms of ours` and `on the CPU without GC pauses`). Whether a class is ours is
  worked out once per class (`ClassValue`), since the check runs for every behaviour of every mob each tick.
  Builders change at most 12 blocks a level a tick between them (`build/BuildBudget`): a place or break is a world
  block update, and with many sites their work timers line up; past the budget a builder waits a tick (nothing saved).
  Recounting what the other builds still need (the materials a builder or crafter keeps back for them, B31 and 23.5)
  reads each of their remaining blocks from the world; a builder fetching from a store with many builds queued at it
  could do every one of them in one call. Since 25.4 those recounts read at most 4,096 blocks a level a tick between
  them (`BuildBudget.RECOUNT_READS_PER_TICK`; the recount that crosses it finishes). Past it, a build's last count is
  reused a little longer, and a build never counted keeps its whole remaining plan back (more than it needs, never
  less) until a later tick counts it; nothing is saved, so a reload changes nothing.
  First local run (2026-10-11, `PERF=true VILLAGES=3 PLOTS=25`, a loaded 7 GB container): p50 2.3 / 4.2 ms and p99
  37.5 / 28.9 ms of ours a tick in the two measured minutes, but 13 of 2,400 ticks with over 50 ms of ours (worst
  118.7 ms; 450.9 ms during the setup), the biggest single calls in `LumberjackWork` (66.8 ms) and `BuilderWork`
  (64.2 ms); share 16.7%. The "no tick over 50 ms from us" target is not met yet (ROADMAP 25.3).
  Second local run (2026-10-11, after the tree-search caps and the reused build counts): p50 1.6 / 1.9 ms and p99
  25.1 / 13.9 ms; 3 of 2,400 ticks with over 50 ms of ours (worst 80.5 ms; 216.8 ms during the setup), the biggest
  single calls `BuilderWork` (53.1 ms) and `LumberjackWork` (27.6 ms); share 17.5%. Still not met.
  Third local run (2026-10-11, with the stall figures and the builders' block budget): p50 1.9 / 2.4 ms and p99
  30.3 / 16.0 ms; 5 of 2,400 ticks with over 50 ms of ours, 4 of them stalls (one GC pause of 47 ms inside a builder's
  call, three with the server thread 42 to 565 ms off the CPU on the loaded container), so 1 over 50 ms on the CPU
  (52.5 ms, many small calls); during the setup 264.9 ms on the CPU as the second village's miners start. Share 16.1%
  (the timer's own check 0.2%, was 1.0%). Still not met.
- **Where:** run the nightly-tests workflow with `part: soak` (job `soak`, 110-minute limit, Java 21). The readings,
  the verdict and the long ticks are in the run's summary; the log, `soak-heap.txt` and the profiles in its
  `soak-<run>` artifact.

## Targets (25.2)

Confirmed by the owner on 2026-10-11 ("do whatever you think is best"): these four stand as proposed.
- our code under 15% of the server tick at 150 workers (first measure 13.1%, later 14.7% to 17.5% in loaded local runs);
- no tick over 50 ms caused by us. 50 ms is one whole server tick, so a longer one is a visible stutter. Judged on
  the steady state: catch-up runs right after a world starts and Java's own garbage-collection pauses are not ours;
- heap flat (+/-5%) over a 60-minute soak, so a server can run for days without growing;
- no regression over 10% between releases.
