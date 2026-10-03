# Sessions: lanes around the clock

Several sessions work on this repo at the same time, around the clock (owner, 2026-10-03: 2.0 by 2026-10-17):
- **Build lanes** (`--as lane-<letter>-<MMDD>-<HHMM>`, the UTC start from `date -u +%m%d-%H%M`): scheduled tasks
  that start a fresh run every hour. Each builds roadmap items back to back.
- **The QA lane** (`--as qa-<MMDD>-<HHMM>`): a scheduled task that tests what the others landed and turns what it
  finds into bugs.
- **The owner's chat** (`--as chat`) talks with Jesse live: his replies, bugs and ideas first, then roadmap items.
- **The digests** (`--as digest-<MMDD>-<HHMM>`): twice a day, one session sends Jesse the review packages and a short
  report, and records his replies. Lanes never message him themselves.

Every run starts fresh from the repo, so everything it needs is written down here and in CLAUDE.md. They don't get in
each other's way, because:
- a claim stops two sessions doing the same item (git refuses the second of two simultaneous claims). It goes stale
  after 75 minutes without a push for a scheduled run, 6 hours for the chat, so work a cut-off run leaves behind is
  soon free again;
- two sessions never work in the same milestone at once, because its items touch the same code (bugs excepted);
- a session holds at most 3 live claims;
- work lives on its item's branch until `land` has merged the newest `main` in and built it green, so `main` is always
  green, and a run that is cut off loses nothing it pushed.

## Contents
- The helper
- A build lane's run
- The QA lane
- Review packages and the digests
- Merging cleanly
- Rules
- The owner's chat

## The helper

`python3 tools/agent/sessions.py <command>`. Every roadmap change goes through it, straight to `main`, retrying if
another session pushed first. `python3 tools/agent/test_sessions.py` tests it on throwaway local repos.

| Command | What it does |
|---|---|
| `status --as <you>` | Who is working on what, paused work, busy milestones, reviews pending, items not yet verified, and your next item (for `qa-…`: what to verify). |
| `brief` | What every session reads first: the roadmap's rules, Bugs, decisions and Notes, without the 4,000 lines of milestones. |
| `show <id> [<id>…]` | One item's full text, with its milestone's heading. |
| `claim <id> --as <you>` | Claims the item and switches you to `item/<id>`, continuing earlier work if there is some. Refuses items that are claimed, blocked, in a milestone another session is working in, or a fourth claim of yours. |
| `land <id> --as <you>` | Merges the newest `main` in, runs the full build, ticks `[x] (review: pending)` and pushes to `main`; repeats if `main` moved. `--no-review` when nothing a player sees changed. `--keep-open` lands part of an item, or the revert of a vetoed one, without ticking. |
| `pause <id> --as <you> --note "done: …; next: …"` | Pushes unfinished work to `item/<id>` and frees the item. Add `--blocked "<why>"` when it can't go on. |
| `unblock <id> --as <you> --note "<what happened>"` | Opens a blocked item again. |
| `review <id> FILE… --as <you> --message "<text>"` | Hands in a review package: commits it to the `reviews` branch for the next digest. |
| `verify <id>… --as <you> --note "<what was tested>"` | QA: marks landed items `(verified …)`. |
| `ship --as <you>` | QA: lands the passing tests on a `qa/…` branch (merge `main`, build, push). |
| `reply "<his words>" --as <you>` | Records the owner's `approve` / `veto` / `change`. |
| `bug "<what, when, expected; Test: name>" --as <you>` | Adds the next `B<n>` under Bugs. |
| `handoff "<in progress, next, traps>" --as <you>` | Replaces your lane's previous handoff in the Notes. |

When `land` stops, its exit code says why:
- **2**: your claim was taken over. Push your work as `item/<id>-<you>`, say so in your handoff, and leave the item.
- **3**: merging `main` in gives conflicts. `git merge origin/main`, fix the files, add them by name, commit, land
  again.
- **4**: the build failed on top of the newest `main`. If it's the setup (Java, memory, a download), fix that; if it's
  a test or compile error, fix it on your branch. Then land again.

**Running `land` (and `ship`).** Each shell command starts fresh, and the build takes minutes, so run it in the
background and poll the log:

```
export COMMIT_TRAILERS="<attribution lines, if your system prompt asks for them>"; \
mkdir -p build; nohup sh -c 'python3 tools/agent/sessions.py land <id> --as <you>; echo "land exit $?"' \
  > build/land.log 2>&1 &
```

Then `tail -5 build/land.log` every minute or so until it prints `land exit`, and start on the next item meanwhile
(reading code, planning; not a second Gradle run). It finds Java in `~/.local/jdk-25` by itself; if yours is
elsewhere, put the `export JAVA_HOME=…` line from CLAUDE.md in front.

## A build lane's run

A scheduled task starts each run on the hour. A run can be cut off at any time, so:

1. **Start** (about five minutes): name yourself from the time, then follow CLAUDE.md's "Start".
   **Health first**: the latest CI run on `main`, the nightly test run, the showcase run (failed scenes are at the top
   of https://jcondedata.github.io/minecraft-alive-workplace/) and any open `nightly-tests` issue. The GitHub API works
   without a login for this public repo: `https://api.github.com/repos/jCondeData/minecraft-alive-workplace/actions/runs?per_page=5`.
   Anything red is a bug: if nobody has added it, add it with `sessions.py bug`; bugs go first.
2. **Items back to back**, as `status` names them, following CLAUDE.md's "Work" and "Finish each item". Push your
   branch at least every 30 minutes.
3. **Wrap up at about 50 minutes** after your start: land what's green, pause what isn't, write the handoff. The next
   run of your lane starts on the hour and continues from your branch and handoff.
4. **No message to the owner.** Review packages go through `sessions.py review`; the digest sends them. End the run
   with a 2-line summary in the session (what landed, what's next) for the record.

## The QA lane

The QA lane is the independent tester. It never builds features; it tests what landed and turns problems into bugs.
Each run, after "Health first":

1. `sessions.py status --as qa-…` lists the landed items not yet verified. Take up to six, oldest first, from one
   milestone (all bugs together count as one).
2. For them, follow the minecraft-mod-tester skill's Check tier, with these cuts (the slow parts run on GitHub):
   - scope with `scope.py --since <the commit before the items' land commits>`, read the items' text (`show`) and the
     changed code; inventory the touched areas;
   - write the adversarial tests from the spec (the item's Done when), not from the code: the happy path through the
     player's entry points, then the ways it breaks, every boundary from both sides, and every new sentence a player
     reads;
   - run them on the real code with `runGameTest`; mutation and the 10x repeats run on GitHub every night (roadmap
     22.7): read their results for these items instead. Until 22.7 is live, plant 3 mutants in the riskiest changed
     lines yourself;
   - visual items: check their scenes on the showcase page or the item's showcase run; run the client yourself only
     for a scene that failed or is missing.
3. Passing tests go on a branch `qa/<topic>-<MMDD>` and onto `main` with `sessions.py ship`. A test that fails because
   the mod is wrong is a bug: push it to `tests/<topic>` (never to `main` on its own) and add the bug with
   `sessions.py bug "<what, expected>; Test: <name> on tests/<topic>"`. The builder who fixes it merges that branch.
4. `sessions.py verify <ids> --as qa-… --note "<n tests, what they cover; mutants; scenes>"` for every item with no
   open bug. An item with a bug stays unverified until the fix lands; then verify it with the fix.
5. When nothing is waiting: the release check if one is due, then the QA milestone's own items (Milestone 21's full
   check, 22.x), then hunting flakes from the nightly results.

**The release check** (before a version bump, when the chat or a digest asks): every item since the last release is
verified; the last nightly run (suite 5x, log audit, pack boot, soak), the nightly mutation and repeats, and the
showcase are green, or each failure is a known bug outside the release; a world saved by the last release opens with
the new jar and nothing is lost. Report it in the Notes.

## Review packages and the digests

- A lane makes the package as `docs/agent/review.md` says and hands it in with `sessions.py review`, never with a
  message of its own. Keep each file under 8 MB: a JPEG sheet and a short 640-wide MP4.
- **The digests** (8 AM and 6 PM Central) are scheduled sessions for Jesse:
  1. fetch the `reviews` branch and send every package not yet sent (SendUserFile), one message per package, then
     mark them sent (`(sent)` in their `message.md`, committed to `reviews`);
  2. send one short report: what landed since the last digest (one line each, the invisible ones too), what the QA
     lane verified and found, the lanes' health (any lane with no landing in 4 hours, any red run), releases, and
     only the decisions that are his;
  3. stay for his replies and record each one with `sessions.py reply`; when he says `release`, run the release
     (CLAUDE.md "Releasing") after the QA lane's release check.

## Merging cleanly

Several branches change the same shared files every hour. To keep merges automatic:
- add registry entries, lang keys, config keys, test-class registrations and README rows in their sorted place or
  their own section, never all at the end of a file, where every branch collides;
- keep shared classes thin: register a new system from its own class, not by growing a big shared one;
- CHANGELOG lines merge by themselves (`.gitattributes`).

## Rules

- **Never rebase or force-push**, on any branch. `land` merges instead, so every push is a fast-forward.
- **Code reaches `main` only through `land`** (and QA tests through `ship`). Roadmap bookkeeping reaches it only
  through the helper. The one exception is a doc change the owner asks for: switch to `main`, pull, change, commit,
  push; if the push is refused, pull and push again.
- **Don't take over a live claim** unless the owner asked for that item (`claim <id> --force`).
- **One version bump at a time:** only the owner's chat or a digest he told to `release`.
- **Owner questions never block a lane:** each item says what to do meanwhile; build that, and ask through the
  digest.
- Each run has its own machine, so the memory rules in CLAUDE.md are about your own processes.

## The owner's chat

- Start with `sessions.py status --as chat`. Lanes are almost always working: take an item in a free milestone, or
  tell Jesse which lane holds the one he wants and claim it with `--force` if he asks.
- Record every reply he gives with `sessions.py reply`, whichever session sent the package.
- Releases: here or in a digest he told to (ROADMAP "Releases ship verified, accepted work only").
