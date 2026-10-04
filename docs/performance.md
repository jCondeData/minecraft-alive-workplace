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
| (first run pending) | | 150 | | | | | |

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
| 2026-10-04 | Pack boot (`tools/packtest/run.sh`, 0.138.0 + 25.5) | 252 mods loaded, the six server mods among them; no error from Alive Workplace or the performance mods (the 37 error lines are the pack's own: empty registries, data fixers, Cobblemon dex files) |

## Targets (25.2)

To be proposed from the first measurement and confirmed by the owner. Meanwhile 25.3 works to these:
- our code under 15% of the server tick at 150 workers;
- no tick over 50 ms caused by us;
- heap flat (±5%) over a 60-minute soak;
- no regression over 10% between releases.
