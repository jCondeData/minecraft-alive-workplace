---
description: Run one lane (about 3 hours). Argument bugs, quick, big, c, d or create; no argument picks the big-update lane that ran least recently.
---

Run one lane for Alive Workplace, Jesse's Minecraft mod (Fabric 1.21.1, for his Cobbleverse friends server). Jesse
has given you full control: work on your own and don't wait for him. Lanes start by hand (no scheduled routines).

Argument: `$ARGUMENTS`
- `bugs` (or `a`): you are **lane a, the bug lane**. You own every bug in ROADMAP "Bugs", Milestone 26's open items
  (the release checks) and red duty. Jesse talks to this lane's chat directly: when he describes a bug, file it at once
  with `python3 tools/agent/sessions.py bug "what, when, expected; Test: name" --as lane-a-<time>` (add
  `(urgent: owner)` if he says it's urgent), fix it with a fresh subagent, push, and reply to him in plain words with
  what was wrong and what changed (a screenshot or GIF when he'd see it in game). Player-visible bugs come before
  test-only flakes.
- `quick` (or `b`): you are **lane b, the quick lane**: Milestone 36's quick additions, one subagent each.
- `big`, `c`, `d` or `create` (= `e`): you are a **big-update lane**: c prefers Milestone 37 (furniture and storage),
  d Milestone 38 (Cobblemon depth), e Milestone 39 (Create, with the owner's port in `libs/`); each takes the others'
  milestones when its own is empty or blocked. `big` alone: look at `git log origin/main --format='%ci %s' -40` and
  take the one of c, d and e whose tag (`[lane-c-…]`, …) appeared least recently; if none, take c.
- empty: as `big`.

Steps:
1. Make sure the repo jCondeData/minecraft-alive-workplace is attached with push access (add_repo, access "push") and
   cloned. Work on `main`. Run `date -u +%m%d-%H%M` and call yourself lane-x-<that> (x your lane's letter).
2. Read CLAUDE.md, then "Sprint mode: a build lane's run" and "Red main" in docs/agent/sessions.md, and follow them.
   `python3 tools/agent/sessions.py next --as lane-x-<time>` lists your lane's items, best first. Check `wip/lane-x`
   for unfinished work first. A big-update lane starting a milestone reads its intro and design note first.
3. You are the coordinator: build each item with a fresh subagent (one per item, as sessions.md says). Commit each
   locally; every 2-3 items run the full build and push to main when green. Don't film scenes locally and don't make
   review packages: GitHub films the scenes. Sonnet subagents only for translations and routine tests.
4. Red main: the bug lane checks the latest CI run on main at the start and before each push and fixes a red main
   first. The quick and big lanes never take a bug: they file one they find (`sessions.py bug`) and keep building;
   when only tests that also fail on main's latest CI run fail, they may push and name them in the commit message.
5. At minute 140 start no new item: finish what's in flight, run the full build and push to main by about minute 170
   (never past 175). Only an item that truly can't be finished goes to wip/lane-x with a message saying what's left.
6. Never publish to Modrinth or CurseForge, change the license, bump the version, force-push, or delete anything on
   GitHub other than your own wip branch.
7. Quick and big lanes don't message Jesse; end the run with a two-line summary (what landed, what's next). The bug
   lane answers him about each bug he raised.
