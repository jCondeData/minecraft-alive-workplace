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
| 2026-10-05 | 5b489b7 | 150 (65 of 76 sites working when measured) | 2.3 / 3.5 (p99 7.3) | 18.3 / 32.8 (p99 66.1) at the start, then 8.8 / 16.5 (p99 21.1) once the builds settled | 1613 MB | 13.1% (204 of 1556 samples; 49.8% villager brains, 26.5% pathfinding, both ours included) | [nightly-tests 37297003813](https://github.com/jCondeData/minecraft-alive-workplace/actions/runs/37297003813) (the nightly's performance step, same load as the Sunday job) |

## A season under the edicts (30.22)

`SEASON=true tools/packtest/run.sh`: a City of 35 villagers (16 with jobs) on the pack server, 8 in-game days at full
speed, profiled for the whole run (`perf.py season.jfr --days server.log`). The run fails when our share of the server
thread is 15% or more on any day, or any tick over 50 ms had over 50 ms of our code (its length times our share of its
profile samples). The daily numbers are in `docs/design/M30.md`.

| Date | Run | Sprint | Our share, day 1 | Our share, days 2 to 8 | Ticks over 50 ms | Of them ours |
|---|---|---|---|---|---|---|
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
| 2026-10-04 | Client (`SCENE=config PERF_STACK=true`) | 96 mods, all nine performance mods loaded; the scene's 2 checks pass and the world renders behind the screen; only warnings are other mods' optional mixins and the container's missing sound device |
| 2026-10-05 | Benchmark load on the pack's stack (nightly-tests 37297003813, 5b489b7) | Runs clean with the six server mods: our share 13.1% (target under 15%); busy p99 66.1 ms at the start, 21.1 ms later (target: none over 50 ms from us, not yet attributed); heap 1613 MB after GC once, but no 60-minute soak yet, so the flat-heap target is unmeasured |
| 2026-10-04 | Pack boot (`tools/packtest/run.sh`, 0.138.0 + 25.5) | 252 mods loaded, the six server mods among them; no error from Alive Workplace or the performance mods (the 37 error lines are the pack's own: empty registries, data fixers, Cobblemon dex files) |

## Targets (25.2)

To be proposed from the first measurement and confirmed by the owner. Meanwhile 25.3 works to these:
- our code under 15% of the server tick at 150 workers;
- no tick over 50 ms caused by us;
- heap flat (±5%) over a 60-minute soak;
- no regression over 10% between releases.
