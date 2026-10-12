---
description: Run one build lane (about 3 hours). Optional argument a or b; no argument picks the lane that ran least recently.
---

Build lane X for Alive Workplace, Jesse's Minecraft mod (Fabric 1.21.1, for his Cobbleverse friends server). The goal
is every expansion released by 2026-10-17. Speed comes from building feature after feature straight on main without
ever thinning the content. Jesse has given you full control: work on your own and don't wait for him.

Lanes start by hand now (no scheduled routines): Jesse opens a chat and says "get started" or `/get-started`. Two lanes
exist. Lane A owns lane a's and lane c's shares of the roadmap; lane B owns lane b's and lane d's.

Argument: `$ARGUMENTS`
- `a` or `b`: you are that lane.
- empty: look at `git log origin/main --format='%ci %s' -40` and take the lane whose tag (`[lane-a-…]`/`[lane-c-…]`
  for A, `[lane-b-…]`/`[lane-d-…]` for B) appeared least recently; if neither appears, take A.

Below, x is your lane's letter (a or b) and y its second share (c for lane A, d for lane B).

1. Make sure the repo jCondeData/minecraft-alive-workplace is attached with push access (add_repo, access "push") and
   cloned. Work on `main`. Run `date -u +%m%d-%H%M` and call yourself lane-x-<that>.
2. Read CLAUDE.md, then "Sprint mode: a build lane's run" and "Red main" in docs/agent/sessions.md, and follow them.
   You own TWO of the four shares of the roadmap (items split by number mod 4): your own, listed by
   `python3 tools/agent/sessions.py next --as lane-x-<time>`, and lane y's, listed by
   `python3 tools/agent/sessions.py next --as lane-y-<time>`. Do your own list first and take lane y's items when it
   is empty or blocked. Check wip/lane-x and wip/lane-y branches for unfinished work first.
3. You are the coordinator: build each feature with a fresh subagent (one per feature, as sessions.md says). Commit
   each feature locally; every 2-3 features run the full build and push to main when green. Don't film scenes locally
   and don't make review packages: GitHub films the scenes. Sonnet subagents only for translations and routine tests.
4. Red main: lane A is on red duty. Check the latest CI run on main at the start and before each push; lane A fixes a
   red main first. Lane B leaves a red main to lane A (both lanes may run at once) unless it has been red for over an
   hour with no fix on main, as in sessions.md "Red main". Test-only and showcase-only bugs can wait.
5. At minute 140 start no new feature: finish what's in flight, run the full build and push to main by about minute
   170 (never past 175). Only a feature that truly can't be finished goes to wip/lane-x with a message saying what's
   left.
6. Never publish to Modrinth or CurseForge, change the license, bump the version, force-push, or delete anything on
   GitHub other than your own wip branch.
7. Don't message Jesse. End the run with a two-line summary (what landed, what's next).
