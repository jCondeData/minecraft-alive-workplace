# Sessions: the owner's chat and the night shift

Two kinds of session work on this repo, and they may run at the same time:
- **The owner's chat** (`--as chat`) talks with Jesse live. His replies, bugs and ideas come first, then roadmap
  items. It is the only session that cuts releases, and the only one that touches files on his computer.
- **Night runs** (`--as night-<MMDD>-<HHMM>`, the UTC start time from `date -u +%m%d-%H%M`) are scheduled sessions,
  several each night. Each starts fresh from the repo, so everything it needs must be written down here.

Both follow the CLAUDE.md session loop and the roadmap rules. They don't get in each other's way, because:
- a claim stops two sessions doing the same item (git refuses the second of two simultaneous claims). It goes
  stale after 6 hours without a push for the chat, and after 75 minutes for a night run, so work a cut-off run
  leaves behind is soon free again;
- two sessions never work in the same milestone at once, because its items touch the same code;
- work lives on its item's branch until `land` has merged the newest `main` in and built it green, so `main` is always
  green and releasable, and a session that is cut off loses nothing it pushed.

## Contents
- The helper
- A session, step by step
- Rules
- The night shift
- The owner's chat

## The helper

`python3 tools/agent/sessions.py <command>`. Every roadmap change goes through it, straight to `main`, retrying if
another session pushed first.

| Command | What it does |
|---|---|
| `status --as <you>` | Who is working on what (live or stale), paused work, busy milestones, reviews pending, and your next item. |
| `claim <id> --as <you>` | Claims the item and switches you to `item/<id>`, continuing earlier work if there is some. Refuses items that are claimed, blocked, or in a milestone another session is working in. |
| `land <id> --as <you>` | Merges the newest `main` in, runs `./gradlew --max-workers=1 build`, ticks `[x] (review: pending)` and pushes to `main`. Repeats if `main` moved meanwhile. `--keep-open` lands without ticking: part of a long item, or the revert of a vetoed one. `--no-review` marks it accepted: only when nothing a player can see or feel changed. |
| `pause <id> --as <you> --note "done: …; next: …"` | Pushes your unfinished work to `item/<id>` with the note, and frees the item for the next session. Add `--blocked "owner: <question>"` or `--blocked "tester: <why>"` when it can't go on. |
| `unblock <id> --as <you> --note "<what happened>"` | Opens a blocked item again once what it waited for has happened. |
| `reply "<his words>" --as <you>` | Records the owner's `approve` / `veto` / `change`. |
| `bug "<what, when, expected; Test: name>" --as <you>` | Adds the next `B<n>` under Bugs. |
| `handoff "<in progress, next, traps>" --as <you>` | Replaces your kind's (chat or night) previous handoff in the Notes. |

When `land` stops, its exit code says why:
- **2**: your claim was taken over (the owner asked another session for this item). Push your work as
  `item/<id>-<you>`, say so in your handoff, and leave the item.
- **3**: merging `main` in gives conflicts. `git merge origin/main`, fix the files, add them by name, commit, and land
  again.
- **4**: the build failed on top of the newest `main`. Fix it on your branch and land again.

**Running `land`.** Each shell command starts fresh, and the build takes several minutes, so run it as one command
in the background and poll the log:

```
export COMMIT_TRAILERS="<attribution lines, if your system prompt asks for them>"; \
mkdir -p build; nohup sh -c 'python3 tools/agent/sessions.py land <id> --as <you>; echo "land exit $?"' \
  > build/land.log 2>&1 &
```

Then `tail -5 build/land.log` every minute or so until it prints `land exit`. It finds Java in `~/.local/jdk-25`
by itself; if yours is elsewhere, put the `export JAVA_HOME=…` line from CLAUDE.md in front. The helper adds
`$COMMIT_TRAILERS` to every commit it makes, and names your session in each commit message.

## A session, step by step

1. `git fetch`, then `sessions.py status --as <you>`. Read the Bugs, the Notes and the latest handoffs.
2. `sessions.py claim <id> --as <you>`. If it says you're continuing earlier work, the last commit message on the
   branch says what's done and what's next.
3. Work on `item/<id>`. Commit in small steps, and `git push -u origin item/<id>` at least every 30 minutes, with a
   commit message that says what's done and what's next. The push is your sign of life: a claim with no push for
   6 hours (75 minutes for a night run) goes stale and may be taken over.
4. Finish as CLAUDE.md says (build, tester, bot playtest), then `sessions.py land <id> --as <you>`, then send the
   review package.
5. Out of time, or stuck on something only the owner can answer: `sessions.py pause`.
6. Before you stop: `sessions.py handoff`.

## Rules

- **Never rebase or force-push**, on any branch. `land` merges instead, so every push is a fast-forward.
- **Code reaches `main` only through `land`.** Roadmap bookkeeping reaches it only through the helper. The one
  exception is a small doc change the owner asks for (a rule for CLAUDE.md, a new roadmap item): switch to `main`,
  pull, change, commit, push; if the push is refused, pull and push again.
- **Don't take over a live claim** unless the owner asked for that item (`claim <id> --force`). The session that had
  it notices at its next `land` or `pause` and steps aside.
- **One version bump at a time:** only the owner's chat releases.
- The helper has tests: after changing it, run `python3 tools/agent/test_sessions.py` (local repos only).
- The container is yours alone: another session runs on another machine, so the 7 GB rules in CLAUDE.md are about
  your own processes.

## The night shift

A scheduled prompt starts each run (see `docs/owner/prompts.md`, section 10). The runs are spread over the night.
A run can be cut off without warning (one stopped at exactly 60 minutes), so:

1. **Start:** note the time and name yourself from it. Push at least every 20–30 minutes.
2. **Health first.** Check the latest CI run on `main`, the nightly test run and the showcase run (its failed scenes
   are at the top of https://jcondedata.github.io/minecraft-alive-workplace/), and any open `nightly-tests` issue
   (the GitHub API works without a login for this public repo:
   `https://api.github.com/repos/jCondeData/minecraft-alive-workplace/actions/runs?per_page=5`). Anything red is a
   bug: add it with `sessions.py bug` and fix it first.
3. **Then roadmap items**, as `status` names them, one at a time.
4. **When `status` says nothing is free** (4 items waiting for review, everything else blocked or busy), do work
   that doesn't build on pending items: the safety-net items (Milestone 22), or the tester's Full tier if none are
   open, with its findings added as bugs.
5. **On Sunday nights**, the first run does the weekly health check (`docs/owner/prompts.md`, section 8) instead of
   items, as far as its hour allows, and reports what it didn't get to.
6. **Send each review package right after its `land`**, not at the end: the run can stop at any moment.
7. **Wrap up after about 45 minutes**, or when you're done: land what's green, pause what isn't, write the handoff.
   Then send the owner one short morning message: what landed (the packages he already has, and one line each for
   work accepted without review), what's paused and what's next, bugs found, and anything that needs him. He reads it on his phone in the morning, so lead with what
   he can see in game.

## The owner's chat

- Start with `sessions.py status --as chat`. If a night run is live, it names its milestone: take an item elsewhere,
  or tell the owner which item it holds.
- If he wants the item a night run holds, claim it with `--force` and merge its branch in when it steps aside.
- Record every reply he gives about a review package with `sessions.py reply`, even when the package came from a
  night run.
- Releases: only here (ROADMAP "Releases ship accepted work only").
