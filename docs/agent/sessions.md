# Sessions: sprint mode

Owner, 2026-10-03: "name of the game is productivity." The first chat built most of the mod (48,000 lines, about 80
features, a release every hour) in four days by building feature after feature straight on `main`. The setup that
followed (claims, item branches, `land`, an hourly QA lane, a review package per item) spent its runs on bookkeeping:
on 2026-10-03, 40 runs made 311 commits, 211 of them bookkeeping and 125 merges, and built one feature. So:

- **Build lanes a and b** (`--as lane-<letter>-<MMDD>-<HHMM>`, the UTC start): a fresh run every 3 hours, working
  about 170 minutes. Lane a owns odd milestones and odd bugs, lane b even ones (`sessions.py next`).
- **The QA lane** (`--as qa-<MMDD>-<HHMM>`): overnight only (11 PM to 6 AM Central). Its bugs are waiting in the
  morning.
- **The owner's chat** (`--as chat`) talks with Jesse live and takes any item.
- **The digests** (`--as digest-<MMDD>-<HHMM>`), 8 AM and 6 PM Central: they send the review packages and a short
  report, record his replies, and the evening one releases.

## Contents
- Sprint mode: a build lane's run
- The helper
- The QA lane
- Review packages and the digests
- Merging cleanly
- Rules
- The owner's chat

## Sprint mode: a build lane's run

1. **Start, in one step** (aim for 5 minutes): name yourself, `git pull`, `sessions.py next --as <you>`, the latest CI
   run on `main` (red is the first thing you fix), and Java setup (CLAUDE.md). Don't read the brief or ROADMAP whole;
   `sessions.py show <id>` for your items. If an `item/<id>` or `wip/<lane>` branch from an earlier run has work for
   one of your items, merge it into `main` and finish it.
2. **Build features back to back**, like the first chat did:
   - read the code you'll change, build the item's whole Done when (art, builds, text, scene), and write its
     GameTests: the happy path and the likeliest ways it breaks. Iterate with `runGameTest` and only your test classes;
   - visible to a player: run its scene once, look at the pictures, hand in the package (`sessions.py review`), then
     `sessions.py done <id> --review`. Not visible: `sessions.py done <id>`;
   - CHANGELOG line, `./gradlew --max-workers=1 build` (in the background; plan the next item meanwhile), commit the
     work with its tick, push to `main`. If the push is refused: `git pull --no-rebase`, build again only if the pull
     brought in code under `src/`, push. Two or three small items may share one build;
   - commit messages say what's done and what's next: they are the handoff. No claim, pause, land or handoff
     commits.
3. **Wrap up at about 170 minutes, and never past 175:** the next run of your lane starts at 180, and with no claims it
   takes the same item and builds it twice (on 2026-10-04 a 229-minute run overlapped the next one, which redid 23.4
   and 23.5 and threw them away). Push what's green. Unfinished work goes to `wip/<lane>` (e.g. `wip/lane-a`) with
   a commit message saying what's left; the next run of your lane continues it. Log the cost
   (`python3 tools/agent/usage.py --log --as <you> --note "<items>"`) if that script exists.
4. **No messages to the owner**; the digest reports. End with a 2-line summary (what landed, what's next).

Never wait: an owner question goes in the Notes and you take the next item. Never thin an item to go faster.

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

Start on the next item meanwhile (reading code, planning; not a second Gradle run), and look at `tail -5
build/land.log` between those steps. When there's nothing left to do but wait, wait in one command instead of checking
every minute, because every check re-reads the whole conversation and costs as much as a real step:
`timeout 540 sh -c 'until grep -q "land exit" build/land.log; do sleep 20; done'; tail -5 build/land.log` (the same
for a long `runGameTest` or screenshot run). It finds Java in `~/.local/jdk-25` by itself; if yours is elsewhere, put
the `export JAVA_HOME=…` line from CLAUDE.md in front.

## The QA lane

The QA lane is the independent tester, overnight only. It never builds features; it tests what landed and turns
problems into bugs. Only file a bug for something a player would hit or a red check: an untested corner that works is a
test to ship, not a bug. Each run, after checking CI and the nightly issue:

1. `sessions.py status --as qa-…` lists the landed items not yet verified. Take up to six, oldest first, from one
   milestone (all bugs together count as one).
2. For them, follow the minecraft-mod-tester skill's Check tier, with these cuts (the slow parts run on GitHub):
   - scope with `scope.py --since <the commit before the items' land commits>`, read the items' text (`show`) and the
     changed code; inventory the touched areas;
   - write the adversarial tests from the spec (the item's Done when), not from the code: the happy path through the
     player's entry points, then the ways it breaks, every boundary from both sides, and every new sentence a player
     reads;
   - run them on the real code with `runGameTest`; mutation and the 10x repeats run on GitHub every night (roadmap
     22.7), so don't run them yourself: read last night's report instead. It is the summary of the `mutation-report`
     job in the newest `nightly-tests` run (GitHub MCP: `actions_list` `list_workflow_runs` with `resource_id:
     nightly.yml`, then `list_workflow_jobs`; or the run's `nightly-mutation` artifact, `report.md`): mutants killed
     and each survivor's line and change, and each new test's failures out of 10. Survivors and flaky tests are also
     in the `nightly-tests` issue. A survivor in an item's code is a test to write; a flaky new test is a bug. To check
     something sooner, start the workflow by hand with `part: mutation`;
   - visual items: check their scenes on the showcase page or the item's showcase run; run the client yourself only
     for a scene that failed or is missing.
3. Passing tests go on a branch `qa/<topic>-<MMDD>` and onto `main` with `sessions.py ship`. A test that fails because
   the mod is wrong is a bug: push it to `tests/<topic>` (never to `main` on its own) and add the bug with
   `sessions.py bug "<what, expected>; Test: <name> on tests/<topic>"`. The builder who fixes it merges that branch.
4. `sessions.py verify <ids> --as qa-… --note "<n tests, what they cover; mutants; scenes>"` for every item with no
   open bug. An item with a bug stays unverified until the fix lands; then verify it with the fix.
5. When nothing is waiting: the release check if one is due, then the QA milestone's own items (Milestone 21's full
   check, 22.x), then hunting flakes from the nightly results.
6. Before you stop: `python3 tools/agent/usage.py --log --as <you> --note "<what you verified>"`.

**The release check** (before a version bump, when the chat or a digest asks): every item since the last release is
verified; the last nightly run (suite 5x, log audit, pack boot, soak), the nightly mutation and repeats, and the
showcase are green, or each failure is a known bug outside the release; a world saved by the last release opens with
the new jar and nothing is lost. Report it in the Notes.

## Review packages and the digests

- A lane makes the package as `docs/agent/review.md` says and hands it in with `sessions.py review`, never with a
  message of its own. Keep each file under 8 MB: a JPEG sheet and a short 640-wide MP4.
- **The digests** (8 AM and 6 PM Central) are scheduled sessions for Jesse:
  1. `sessions.py pending` lists every item waiting on him and whether its package exists. Send every package not yet
     sent (SendUserFile, one message per package), then mark it sent (`(sent)` in its `message.md`, committed to
     `reviews`). For a pending item with **NO PACKAGE**, make one from what exists (its scene's stills on the
     showcase page, or the item's text for a document) and send it; never just list it.
  2. Send one short report: what landed since the last digest (one line each), what the QA lane found overnight, the
     lanes' health (a lane with no push in 3 hours, a red run on `main`), releases, and only the decisions that are
     his.
  3. **The evening digest releases** (CLAUDE.md "Releasing") when `main` is green and something new landed since the
     last release; pending reviews don't hold it back.
  4. Stay for his replies and record each one with `sessions.py reply`.

## Merging cleanly

Several branches change the same shared files every hour. To keep merges automatic:
- add registry entries, lang keys, config keys, test-class registrations and README rows in their sorted place or
  their own section, never all at the end of a file, where every branch collides;
- keep shared classes thin: register a new system from its own class, not by growing a big shared one;
- CHANGELOG lines merge by themselves (`.gitattributes`).

## Rules

- **Never rebase or force-push**, on any branch. `land` merges instead, so every push is a fast-forward.
- **Build lanes and the chat push straight to `main`** after a green `./gradlew build` (sprint mode). The QA lane
  still lands tests through `ship`. `claim`/`land` still work for anyone finishing an old item branch.
- **Don't take over a live claim** unless the owner asked for that item (`claim <id> --force`).
- **One version bump at a time:** only the evening digest (daily), the owner's chat, or a digest he told to `release`.
- **Owner questions never block a lane:** each item says what to do meanwhile; build that, and ask through the
  digest.
- Each run has its own machine, so the memory rules in CLAUDE.md are about your own processes.

## The owner's chat

- Start with `sessions.py status --as chat`. Lanes are almost always working: take an item in a free milestone, or
  tell Jesse which lane holds the one he wants and claim it with `--force` if he asks.
- Record every reply he gives with `sessions.py reply`, whichever session sent the package.
- Releases: here or in a digest he told to (ROADMAP "Releases ship verified, accepted work only").
