# Big villages stay smooth

How the mod keeps a server with many working villagers running smoothly: the benchmark that measures it, and the
limits on the mod's own heavier jobs (milestone 25).

Roadmap items: 25.1, 25.3

## What a player sees

Nothing new to look at: villages with many lumberjacks and builders simply don't make the server stutter. A
lumberjack in a wood full of log pillars or log cabins looks a few trunks over at a time, so it may take a moment
longer to pick its tree there. A tree farm's saplings are found without going over every block of the farm. A
builder waiting for materials no longer recounts every other build of the village block by block each time.

## How it works

- **The benchmark (25.1).** `PERF=true VILLAGES=3 PLOTS=25 tools/packtest/run.sh` boots the Cobbleverse pack's server
  with the mod, places three villages of 25 plots each (150 busy workers) with `/workplace benchmark`, and measures
  the server's tick times, the heap after a garbage collection and a profile of the server thread
  (`tools/packtest/perf.py`). The nightly tests repeat it; the method and every run's numbers are in
  `docs/performance.md`.
- **Our own time a tick (25.3).** In the benchmark only (`-Daliveworkplace.benchmark=true`), every job behaviour and
  tick handler of ours is timed and added up per server tick; the log gets a line a minute (p50, p99, max and the
  biggest call) and a line for each tick with over 50 ms of ours. On a normal server none of this runs.
- **Tree searches a few trees at a time (25.3).** A lumberjack works out at most 6 trees (a flood fill of a trunk's
  logs and leaves each) in one tick, nearest and wanted kinds first, and goes on next tick where it stopped: trunks
  that turned out not to be trees, or trees it can't get at, are remembered for a minute. Felling itself stays in one
  tick, so leaves never hang in the air.
- **Sapling search by chunk section (25.3).** Looking for a sapling to feed bone meal to, the lumberjack skips every
  16×16×16 section of its tree farm that has no sapling in it, instead of checking each block.
- **Other builds' needs reused (25.3).** What another build still needs (to keep its materials back in shared chests)
  is counted from its whole plan once and reused for up to 100 ticks (5 seconds) while that build hasn't moved on. A
  block placed or a supply run of its builder counts it again at once.

## Switches

none

## Saved data

none (the caches above live only in memory; a reloaded lumberjack starts its search again)

## Items, blocks, jobs, commands

- `/workplace benchmark <plots>`: places a benchmark village's busy workers. Only on a server started with
  `-Daliveworkplace.benchmark=true` (the pack test's measuring modes).

## Decisions

- 25.2 (owner's targets, used until confirmed): our code under 15% of the server tick at 150 workers, and no tick over
  50 ms caused by us.
- 25.3: felling a tree stays in one tick: spread over ticks, the leaves would be seen hanging in the air.
- 25.3: a per-level budget for villager path searches was tried and taken out again: it showed no gain in the
  benchmark and was shared unfairly by every villager in a level.

## Known limits

- A lumberjack in a very large log-pillar forest takes a few ticks longer to settle on a tree.
- A build whose blocks a player breaks or places by hand is recounted within 5 seconds, not at once.
- The targets are measured on a loaded container locally; GitHub's runner gives the comparable numbers
  (`docs/performance.md`).

## Proof

- `LumberjackGameTests.treeSearchesWorkOutAFewTreesAtATime`: one search works out at most 6 trunks and the next ones
  go on to find the oak past 16 log pillars.
- `LumberjackGameTests.lumberjackFindsTheTreeBeyondLogPillars`: a real lumberjack fells that oak and leaves the
  pillars standing.
- `LumberjackGameTests.lumberjackReloadedMidSearchStillFellsTheTree`: saved and loaded mid-search, it still fells it.
- `BuildReserveGameTests.aSitesNeedIsCountedAgainOnceItMovesOn`: the reused count, and a fresh count once the site
  moves on.
- `BenchmarkCostGameTests.theTickTimerCountsOurWorkOnceAndResetsEachTick`,
  `BenchmarkCostGameTests.theTickTimerDoesNothingWhenOff`: the per-tick timer.
- Showcase scenes: none (nothing new to see).
