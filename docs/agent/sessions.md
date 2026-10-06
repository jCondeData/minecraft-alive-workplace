# Sessions: sprint mode

Owner, 2026-10-03: "name of the game is productivity." The first chat built most of the mod (48,000 lines, about 80
features, a release every hour) in four days by building feature after feature straight on `main`. The setup that
followed (claims, item branches, `land`, an hourly QA lane, a review package per item) spent its runs on bookkeeping:
on 2026-10-03, 40 runs made 311 commits, 211 of them bookkeeping and 125 merges, and built one feature. So:

- **Build lanes a, b, c and d** (`--as lane-<letter>-<MMDD>-<HHMM>`, the UTC start): a fresh run every 3 hours,
  working about 170 minutes. They split milestones and bugs by number mod 4 (owner, 2026-10-04): lane a 1 (21, 25,
  29, 33), lane b 0 (24, 28, 32), lane c 3 (23, 27, 31, 35), lane d 2 (22, 26, 30, 34). `sessions.py next` lists
  each lane's own.
- **The QA lane** (`--as qa-<MMDD>-<HHMM>`): hourly overnight (11 PM to 6 AM Central) and every 2 hours by day
  (owner, 2026-10-05). It owns the bugs only a test or a showcase scene sees, so build lanes stay on features.
- **The owner's chat** (`--as chat`) talks with Jesse live and takes any item.
- **The digests** (`--as digest-<MMDD>-<HHMM>`), 8 AM and 6 PM Central: they send the review packages and a short
  report, record his replies, and the evening one releases.

## Contents
- Sprint mode: a build lane's run
- The helper
- Red main
- The QA lane
- Review packages and the digests
- Merging cleanly
- Rules
- The owner's chat

## Sprint mode: a build lane's run

The speed review of 2026-10-04 (owner: "name of the game is speed"): a lane run's cost is mostly the AI re-reading its
own conversation every step. A 2.5-hour run grows from 95k to 300k-470k tokens and costs $13-25; runs that stayed near
130k cost $2-4. Filming took 10-22% of a run. So each feature gets a fresh conversation, nothing is filmed locally, and
the full build runs once per 2-3 features.

1. **Start, in one step** (aim for 5 minutes): name yourself, `git pull`, `sessions.py next --as <you>`, the latest CI
   run on `main` (red is the first thing you fix only if you are on red duty: see "Red main"), and Java setup
   (CLAUDE.md). Don't read the brief or ROADMAP whole.
   If an `item/<id>` or `wip/<lane>` branch from an earlier run has work for one of your items, finish it first
   (lane b's `wip/lane-b` may hold 28.x; lanes c and d check `wip/lane-a` and `wip/lane-b` for their milestones once).
2. **You are the coordinator; each feature is built by a fresh subagent.** For each item, start one `Agent`
   (general-purpose, no model override: it uses yours) with a self-contained prompt: the item id, "read CLAUDE.md and
   `sessions.py show <id>`, build its whole Done when, write its GameTests and run only them with `runGameTest`, add
   its showcase scene to the harness and `tools/showcase/scenes.py` if a player sees it, add the CHANGELOG line, update the feature's page in docs/wiki/ (22.9), tick
   it (`sessions.py done <id> --review` if a player sees it, else `done <id>`), commit locally with the work and the
   tick (stage files by name), don't push, and reply in 5 lines: what was built, files, tests, anything left". Keep
   your own context small: read its reply, not its files. One feature per subagent; two only if both are tiny.
3. **Build and push every 2-3 features** (or before the run ends): `./gradlew --max-workers=1 build`; green, push to
   `main` (refused: `git pull --no-rebase`, build again only if the pull brought in code under `src/`, push). Red: give
   the failure to a fresh subagent to fix, then build again. Never push red, with one exception: when the only
   failures are tests that also fail on `main`'s latest CI run (and aren't in your items' test classes), they belong to
   the red-duty lane; push, and name them in the commit message.
4. **Don't film.** The showcase workflow films every changed scene on GitHub after each push, and the nightly one
   films them all; a failing scene lands in the `nightly-tests` issue for the QA lane. Run a scene locally only to
   debug one that fails. No review packages either: the digest makes one per expansion stage from the showcase
   pictures.
5. **Sonnet only for translations and routine tests** (owner, 2026-10-04): a subagent with `model: "sonnet"` may
   write `en_us.json` text or plain GameTests after the feature exists. Everything else, art, builds and design
   included, stays on your model.
6. **At minute 140, start no new feature** (owner, 2026-10-05: on 2026-10-05, 5 items sat on `wip/` branches and
   merging them back took 6 extra subagent jobs). Finish what's in flight, run the build, and push to `main` by about
   170, never past 175: the next run of your lane starts at 180, and with no claims it takes the same item and builds
   it twice. Only a feature that truly can't be finished goes to `wip/<lane>`, with a commit message saying what's
   left. Log the cost (`python3 tools/agent/usage.py --log --as <you> --note "<items>"`).
7. **No messages to the owner**; the digest reports. End with a 2-line summary (what landed, what's next).

Never wait: an owner question goes in the Notes and you take the next item. Never thin an item to go faster.

## Red main

Owner, 2026-10-05. On 2026-10-04/05, 27 of 90 builds on `main` were red, three lanes fixed the same red build at the
same time, and 9 of the 13 bugs the lanes closed were test-only or showcase-only. So a red `main` has **one owner per
window**:

- **Red duty** goes by the run's UTC start hour: 00 and 12 lane a, 03 and 15 lane b, 06 and 18 lane c, 09 and 21
  lane d. At the start, and again before each push, the lane on red duty checks the latest CI run on `main`; red, it
  files the bug (`sessions.py bug`, if nobody has) and hands it to a fresh subagent before its own items. A failure
  that comes back on the next green-then-red cycle is still its own until the window ends.
- **Every other lane keeps building** and doesn't touch the red, even when it sees the failing test locally (step 3
  says when to push anyway). If the red lasts more than an hour and the duty lane's run has ended or stalled, the next
  lane to start takes it.
- **Test-only and showcase-only bugs** (a flake, a scene check, nothing a player would notice; CLAUDE.md rule 7) go to
  the QA lane, which now also runs by day. The red-duty lane takes one only when it is what turns `main` red.

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

The QA lane is the independent tester: hourly overnight and every 2 hours by day. It never builds features; it tests
what landed and turns problems into bugs. It also owns the bugs only a test or a showcase scene sees (a flake, a scene
check, a test-harness fix): take those first, oldest first, unless they turn `main` red (then they are the red-duty
lane's, see "Red main"). Only file a bug for something a player would hit or a red check: an untested corner that works is a
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
5. The wiki (roadmap 22.9): for each item you verify, read its page in `docs/wiki/` against what the tests proved and fix
   any sentence that is wrong or missing (a missing page is written now); `wikicheck.py` must pass.
   When nothing is waiting: backfill one missing wiki page, then the release check if one is due, then the QA milestone's own items (Milestone 21's full
   check, 22.x), then hunting flakes from the nightly results.
6. Before you stop: `python3 tools/agent/usage.py --log --as <you> --note "<what you verified>"`.

**The release check** (before a version bump, when the chat or a digest asks): every item since the last release is
verified; the last nightly run (suite 5x, log audit, pack boot, soak), the nightly mutation and repeats, and the
showcase are green, or each failure is a known bug outside the release; a world saved by the last release opens with
the new jar and nothing is lost. Report it in the Notes.

## Review packages and the digests

- Lanes don't make packages (2026-10-04). The digest makes **one package per expansion stage** (a few related items,
  e.g. "the five new 1.2 jobs") from the showcase page's stills and GIFs, under 8 MB, and sends it; packages a lane
  handed in before still go out as they are.
- **The digests** (8 AM and 6 PM Central) are scheduled sessions for Jesse:
  1. `sessions.py pending` lists every item waiting on him and whether its package exists. Send every package not yet
     sent (SendUserFile, one message per package), then mark it sent (`(sent)` in its `message.md`, committed to
     `reviews`). For a pending item with **NO PACKAGE**, make one from what exists (its scene's stills on the
     showcase page, or the item's text for a document) and send it; never just list it.
  2. Send one short report. **It opens with the progress bars** (owner, 2026-10-05, asked twice): paste the output of
     `python3 tools/agent/progress.py` as a code block, one line per stage (1.1 to 2.0), and say how each moved since the last
     digest. Then what landed since the last digest (one line each), what the QA lane found overnight, the
     lanes' health (a lane with no push in 3 hours, a red run on `main`), releases, and only the decisions that are
     his.
  3. **The evening digest releases** (CLAUDE.md "Releasing") when something new landed since the last release,
     whether or not `main` is green at that moment: CI tags the first green build with the new version (owner,
     2026-10-05). Pending reviews don't hold it back. Report the version and whether it is tagged yet.
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
