# Roadmap: Alive Workplace 1.0

**Goal (owner, 2026-09-29):** a public 1.0 that is polished rather than bigger. The mod already has MineColonies'
jobs and colony layer; 1.0 is about making them look and feel finished. In priority order:

1. **Builders and blueprints**: the flagship job never needs babysitting.
2. **Visuals and textures**: every block, item, screen and villager looks vanilla-quality.
3. **Performance at scale**: big villages stay smooth.

Everything done before this plan (Milestones 1–20, and the 0.137 work: fishing from boats, ferry rides, cavalry,
homes; about 215 items) is in `docs/roadmap-history.md`. Read it when you
need to know how something was built. The owner's decisions are at the bottom of this file. Don't change them
without asking.

## How to work this file

Several sessions work from this file, sometimes at the same time: the owner's chat, and scheduled runs at night.
`tools/agent/sessions.py` does the bookkeeping, so these rules hold even when two sessions start at once. The
details are in `docs/agent/sessions.md`.

- **Pick with `sessions.py status --as <you>`.** It shows who is working on what, and names your next item: open
  bugs first, then vetoed items and change requests, then the first open item of the lowest-numbered milestone
  that no other session is working in. Two sessions never work in the same milestone at once, because its items
  touch the same code. Bugs are the exception.
- **Claim, then work on the item's branch.** `sessions.py claim <id> --as <you>` pushes the claim to `main` and
  switches you to the branch `item/<id>`, continuing earlier work if there is some. Git refuses the second of two
  simultaneous claims, so no item is ever done twice.
  - Commit on that branch, and push it at least every 30 minutes. A scheduled run can be cut off at any moment,
    and the push is also your sign of life.
  - A claim with no claim or push for 6 hours (75 minutes for a night run, which lasts about an hour) is stale and
    may be taken over (`claim` says so; mention it in your handoff).
- **Skip what can't move.** If an item waits for the owner, for time (nightly runs) or for work still under review,
  run `sessions.py pause <id> --as <you> --blocked "<what it waits for>" --note "<what's done, what's next>"`,
  ask if it's the owner's call, and take the next item. Check blocked items again at the start of each session: when
  what one waited for has happened, `sessions.py unblock <id> --as <you> --note "<what happened>"`. An item is never
  a reason to stop working.
- **"Done when" is the spec.** Build to it; the tester checks against it. If an item has no "Done when", or it
  can't be met as written, block it with `owner: <your question>`, ask in your next message, and take the next item.
  Don't invent the spec.
- **Two words for finished:**
  - **Built**: `./gradlew build` is green, the "Done when" is met, an independent tester's Check passed (see
    CLAUDE.md), and the bot playtest is done. Then `sessions.py land <id> --as <you>` merges the newest `main` in,
    builds again, ticks the item `[x] (review: pending <date>)` and pushes. Send the review package right after
    (`docs/agent/review.md`).
  - **Accepted without review**: when nothing a player can see or feel changed (tests, tooling, internal fixes), land
    with `--no-review` instead. It's marked `(approved auto <date>)` and listed in your next message; no package.
  - **Accepted**: the owner approved it. Mark it `(approved <date>)`.
- **Reviews don't block work, up to a point.** Carry on while the owner reviews. With 4 items pending review, only
  take work that doesn't build on them: bugs, tests, measurements, or items in other areas.
- **His replies** can reach any session. Record each one on `main` straight away, in his words:
  `sessions.py reply "<his reply>" --as <you>`.
  - `approve 23.3` (or several: `approve 23.3, B1`): marked `(approved <date>)`.
  - `veto 23.3: <why>`: unticked and marked `(vetoed <date>: <why>)`; it's redone next. Revert it from `main` first if
    it can't be reworked quickly, or if later work would be built on it: claim it, `git revert` its commits on the
    branch, and `land <id> --as <you> --keep-open`, which lands without ticking.
  - `change 23.3: <what>`: a sub-item `23.3a` (then `23.3b`, …) with his words, done next.
  - Items are numbered `<milestone>.<n>`, bugs `B<n>`. New items and bugs get the next free number. Numbers are never
    reused or renumbered.
- **If the tester still fails it after 3 rounds**, don't land it. Run
  `sessions.py pause <id> --as <you> --blocked "tester: <why>" --note "<what's left>"`. The work stays on its branch,
  and `main` never sees it.
- **Releases ship accepted work only**, and only the owner's chat cuts them, so two sessions never bump the version.
  Release when no item on `main` is pending review or vetoed, and something new has been accepted since the last
  release. Before the bump, run the tester's Full tier. If items are pending for days, ask the owner whether to
  release with them. He can say `release` (listing what's pending), or wait.
- **Bugs jump the queue.**
  - A bug the owner reports, or one the tester finds outside the item it's testing, goes into "Bugs" below
    (`sessions.py bug "<what, when, expected>" --as <you>`). It gets a failing test and is fixed before the next item.
  - Problems the tester finds in the item itself are part of building that item.
  - Every bug fix gets a review package, small when nothing visible changed.
- **Stay in scope.** Build what the item says. A problem you notice elsewhere goes into "Bugs" or the Notes, not into
  this change.

Status marks go right after the item's number, like `- [ ] **23.3** (claimed: chat, 2026-09-30 14:05Z) **Title…**`.
`sessions.py` writes them all. Item branches don't edit this file, except to add text to an item they're building;
everything else goes to `main` through `sessions.py`, so sessions never collide here.
- `[ ]` open
- `(claimed: <who>, <UTC time>)` being worked on
- `(paused: item/<id>, <time>)` unfinished work on its branch, free to continue
- `(blocked: <what it waits for>)` waiting on someone or something
- `[x] (review: pending <date>)` built and waiting for the owner
- `[x] (approved <date>)` accepted; `[x] (approved auto <date>)` accepted without review (nothing to see)
- `[ ] (vetoed <date>: <why>)` to redo

---

## Bugs (fix before the next item)

Each fix lands together with the failing test that proves it, never a failing test on `main` on its own. Fixed bugs
stay in the list, ticked, so their numbers stay unique.

- [x] **B1** (approved auto 2026-09-29) Four bugs from the first tester run: the homes and rank tips showed the wrong
  numbers, a blueprint name ending in a huge number crashed, and a bed at the far corner of a large building didn't
  count as home. Fixed with the owner's `HomesSpecGameTests` (d36b3d1).
- [ ] **B2** (paused: item/B2, 2026-09-30 07:23Z) Flaky test: `VillageGameTests.aVillagerMovesIntoTheWorkshop` (failed once on CI, commit 49ae713). Done
  when: 100 repeats pass (the repeat generator in the tester skill), or the cause is fixed. Hint from the last
  session: look for leftover blocks from earlier batches blocking the way to the bench; 17 local runs in a row passed.
  It failed once more in the tester's full-suite run (in a mutant run whose mutant couldn't touch it); 10 of 10 alone
  and 3 more full runs passed. Still unexplained.
- [x] **B3** (approved auto 2026-09-29) Five bugs from the tester's pass over 0.131–0.137, fixed with tests (acbac37):
  strangers' arrows hurt a protected village's villagers and animals; a stranger with a Village Ledger could empty a
  protected treasury; guards stayed on their horses, and fishers on the water, after their shift; night-raid bandits
  could join vanilla raids.

## Milestone 21: Finish 0.138.0

The evening of 2026-09-29 left 0.138.0 nearly ready: riding (approved), every texture redrawn, the tester set up and
its first findings fixed. Nothing is released until these are done.

- [ ] **21.1** **The texture rebuild, shown in game.** Every texture was redrawn with the pixel-art skill (2eeb6be);
  178 of 182 pass its lint. Done when:
  - the other 4 pass, or each has its reason in the Notes;
  - one review package with the `preview.py audit` contact sheet, in-game shots of the workstations in a village and
    of every profession's outfit (`SCENE=staff`) and a zombie villager, and a GIF of villagers working in the new
    outfits.
- [ ] **21.2** **The full check before release** (the tester's Full tier; the chat started it and ran out of time). Do
  it in pieces that fit a one-hour night run, landing each piece with `land --keep-open`:
  - the real Cobbleverse pack boot and the soak: the nightly GitHub workflow runs these, so read its result;
  - an old world saved by 0.136.0 or 0.137.0 opened with the new version: nothing lost, nothing crashes;
  - leaving the game in the middle of a ferry ride, and a travel ticket between dimensions;
  - performance with many homes;
  - 20 mutants and a flake sweep over the whole suite, and the biggest gaps from `inventory.py`.

  Done when: each part is a passing test or a green nightly result, every finding is a Bug, and the report is linked
  in the Notes. Players see nothing new, so the last piece lands with `--no-review`.
- [ ] **21.3** (blocked: owner, keep camels as cavalry?) **Camels.** The game counts a saddled camel as a horse, so a
  guard rides one too, but the changelog promises horses, donkeys and mules. The tester's test `aCamelIsNotCavalry`
  (branch `tests/check-0.137-riding-protection`) waits on his answer. Camels out: land that test with the fix. Camels
  in: turn the test around and add camels to the changelog.
- [ ] **21.4** (blocked: waits for 21.1 approved and 21.2 done) **Release 0.138.0.** Only the owner's chat releases
  (CLAUDE.md, "Releasing"). Done when: the GitHub release has the jar.

## Milestone 22: Safety net

Before polishing, make sure nothing regresses unnoticed.

- [x] **22.1** (approved auto 2026-09-29) **Tester set up** (f83f7b4): `tools/modtest/` with `allow.txt` and
  `baseline.json`, the nightly workflow, and the definition of done in CLAUDE.md. The nightly workflow runs at about
  10 PM Central (`17 3 * * *`), before the night runs, which read its result first.
- [ ] **22.2** Save/reload tests for every value `inventory.py` lists as "never saved and reloaded" (69 at 0.136.0), done in
  batches: builder, miner and lumberjack data first. Done when: that list is empty, or each remaining entry has a reason.
- [ ] **22.3** A bot scene for every player-visible feature, so any feature can be shown again on demand
  (`tools/screenshots/run.sh SCENE=…`). Done when: every job and every screen in the README has a scene. A new feature
  gets its scene in the same commit.

## Milestone 23: Builders never need babysitting (priority 1)

MineColonies players' most common complaints are builders that get stuck, don't say what they need, stop when
inventories are full, or only work while a player stands nearby. Ours must do none of that. Check what already exists
first; many items below are "verify and harden", not "build".

- [ ] **23.1** **The builder soak test.**
  - The setup: 10 builders build the full starter set on hilly, forested ground, with materials only in chests and the
    storehouse and no player help, over 2 in-game days.
  - Run it twice. As a long GameTest or pack-server scenario (`tools/packtest`) for the numbers. As a scene in the
    screenshot client for the time-lapse GIF.
  - Add a log line whenever a builder makes no progress for 30 seconds, and count those lines.

  Done when:
  - every build finishes;
  - no item is duplicated or lost (count them before and after);
  - the stuck count is 0, or each case has become a Bug;
  - the GIF is in the review package.

  This test is the yardstick for the rest of the milestone.
- [ ] **23.2** **Stuck recovery, proven.** Builders stuck on water, lava, holes, fences, doors, their own scaffolding, or in
  unloaded chunks. Done when: a chaos test (the tester skill's `ChaosTests`, 5 seeds) finishes every time, and the
  recovery (hop, re-path, step back) never breaks a placed block.
- [ ] **23.3** **"What do you need?" at a glance.** The player can always see what a build is missing and where the builder
  looks for it, without opening screens every 30 seconds. MineColonies players install separate HUD mods for exactly
  this. Look at what exists (the "still missing" tooltip, the requests board, the overhead status), then close the
  gaps. Done when, from a single look (hovering the site or the builder), you can see:
  - what fraction is built;
  - which 3 items are missing most, with counts;
  - which chest or storehouse the builder takes from.
- [ ] **23.4** **A material list you can take away.** A checklist of everything a blueprint needs, minus what's in the supply
  chests, as a written book or the blueprint's tooltip pages (like Create's Schematicannon). Done when: a GameTest
  checks the numbers against a known blueprint and a screenshot shows it.
- [ ] **23.5** **Self-healing supply.** Builders use the storehouse and porters without being told. A full builder inventory
  never stops work. Wanted items go on the requests board automatically. Done when: the soak test passes with the
  materials split across 3 chests and the storehouse.
- [ ] **23.6** **Working when no one is near.** Decide with the owner what a village does when no player is in range: keep
  working through `KeepLoaded` tickets, or pause. The game rule `workplaceKeepWorkLoaded` already exists: start from
  it. Add a config option (default: the owner's choice), and document it
  in the README. Done when: both settings are tested, and there are no chunk-loading surprises (count the tickets
  before and after the soak).
- [ ] **23.7** **Imports that just work.** `.litematic`, `.schem` and `.nbt` files in common sizes and versions, including big
  builds (48×8×48), unknown modded blocks and old formats. Done when: a test corpus of permissively licensed or
  self-made sample files imports, or fails with a clear message that says which block or format was the problem.
- [ ] **23.8** **Placing a build feels good.**
  - Rotation and mirroring before placing.
  - The ghost preview shows exactly where it goes.
  - The site can be moved or cancelled, with its materials returned.
  - Sloped ground is handled (foundation fill and landscaping).

  Done when: a review package shows each step, and a cancel test returns every material.
- [ ] **23.9** (blocked: owner, optional: his call) **Owner-built signature builds.** The owner builds 3–5
  flagship buildings in-game. Claude tidies them with the Architect skill, and they are scanned in with the Scan Tool
  and shipped as blueprints with upgrades. This gives the mod human-made content; see 26.1. Done when: each is in the
  Blueprint Table with its upgrades, and a builder has built it in a test.
- [ ] **23.10** **Every shipped build reviewed.** One gallery package per build family (houses, workshops, defences,
  decorations, village pieces in five styles), each build shown front and back. Vetoed builds get redrawn with the
  Architect skill. Done when: every family's package has been sent.

## Milestone 24: Everything looks finished (priority 2)

Textures go through the minecraft-pixel-art skill. The owner judges them in review packages (his taste decides;
recent picks: lean shapes, heads that are thick where they work and thin where they join, wood for tools, a steel
haft and leather grip for weapons, no decoration the shape doesn't need). Use pick sheets (`preview.py pick`) when a
texture has no clear direction yet.

- [ ] **24.1** **Villager outfits.** Each of our professions has a vanilla-style outfit and a zombie version
  (`preview.py villager`: every biome, zombie, back view). Done when: every profession passes `lint.py`, and one sheet
  of all professions is in a review package.
- [ ] **24.2** **Tools and weapons.** Every tool or weapon item uses the owner-picked templates (hammer, wrench, war hammer)
  or has been through a pick round. Done when: every tool or weapon item passes `lint.py`, and its package is sent.
- [ ] **24.3** **Known visual bugs.** Riding looks right now (villagers sit in saddles and boats and the ferry
  floats; approved 2026-09-29). Left: any clipping, floating or z-fighting in the scenes (`SCENE=extras`, `staff`,
  `village`, …). Done when: each one found has a before/after in a review package, or a package shows the scenes
  clean.
- [ ] **24.4** **Screens.** Village Hall, Blueprint Table, requests board, mailbox, shop and research screens are readable at
  GUI scales 2–4, have no clipped or overlapping text, and use vanilla-style panels. Done when: screenshots of each
  screen at scales 2 and 4 are in a review package, with no clipping visible.
- [ ] **24.5** **Words.** `langcheck.py` is clean. Every tooltip and message has been read in context (screenshots), with
  consistent names for jobs, blocks and items (the README job table is the reference). Done when: `langcheck.py` is
  clean, and a package shows every new or changed message in context.

## Milestone 25: Big villages stay smooth (priority 3)

Baseline at 0.136.0: 80 busy workers in the full pack took the tick from about 2 ms to about 7 ms, and our code was
about 12% of that. For villager mods, the cost is pathfinding to distant points of interest and brain ticks.

- [ ] **25.1** **Measure first.** A repeatable benchmark:
  - 150 workers over 3 villages in the Cobbleverse pack;
  - `tick query` p50/p95, heap after GC and a JFR profile (`tools/packtest`, `PERF=true`);
  - run on GitHub's runner and recorded in `docs/performance.md`.

  Done when: the numbers are recorded and the nightly job repeats them weekly.
- [ ] **25.2** **Targets.** Propose these after 25.1's first measurement, and ask the owner to confirm them. Carry on
  with 25.3 in the meantime, using these numbers:
  - our code under 15% of the server tick at 150 workers;
  - no tick over 50 ms caused by us;
  - heap flat (±5%) over a 60-minute soak;
  - no regression over 10% between releases.

  Done when: the owner confirms the targets and they are written into `docs/performance.md`.
- [ ] **25.3** **The usual fixes, each only if the profile says so:**
  - point-of-interest searches nearest-first, stopping at the first reachable one, with results cached;
  - checks spread over ticks per villager, instead of every villager every tick;
  - a global budget for path requests per tick;
  - flood fills and area scans capped and incremental.

  Never touch the world from another thread: C2ME and similar mods crash on it. Done when: the benchmark meets the
  targets.
- [ ] **25.4** **The pack's performance stack.** Boot and soak with Sodium, Lithium, C2ME, FerriteCore, ModernFix,
  EntityCulling, ImmediatelyFast, Krypton and ScalableLux (Cobbleverse's set). Done when: no errors, and numbers within
  target.
- [ ] **25.5** **Server owner controls.** Config caps like MineColonies' (max workers per village, how far workers path, the
  far-from-players behaviour from Milestone 23). Needs systems (moods, sickness, raids, festivals) are easy to switch
  off, because "babysitting" is the top complaint about big colony mods. Done when: each key is documented in the README
  and tested switched off (the tester's config matrix).

## Milestone 26: Release 1.0

- [ ] **26.1** (blocked: owner, which release channel) **Release channel.** Modrinth's content rules have been enforced
  since 2026-09-27:
  - A project made "entirely or almost entirely" with generative AI, with "little-to-no human input beyond prompting or
    testing", can only be unlisted; it can't appear in public search.
  - AI-assisted projects with significant human work are allowed, if they are marked "Contains AI-generated content".
  - No image on the page may be "created or derived from generative AI output".

  CurseForge only requires a disclaimer on AI-altered showcase images that could mislead. Options:
  - CurseForge and GitHub now, with Modrinth unlisted.
  - Ask Modrinth moderation first, describing the owner's part (the design, every decision, his signature builds).
  - Add more human-made work first (signature builds, hand-picked textures, a description he writes himself).

  Record the choice under Design decisions. Done when: he has chosen. Until then 26.2–26.4 are prepared for both
  sites.
- [ ] **26.2** **Store page kit**, for the chosen sites:
  - a one-line summary (no formatting, doesn't repeat the name);
  - a description that says what it adds, why to get it, and what to know first (server and client both need it,
    Fabric API, optional Cobblemon);
  - the name **Alive Workplace**: Minecraft's brand rules forbid leading with "Minecraft";
  - an icon and gallery images (Modrinth: only if its image rule allows them);
  - one GIF per headline job;
  - the AI disclosure where required;
  - client = required, server = required.

  Done when: the kit is in a review package, as it would look on each site.
- [ ] **26.3** **Hygiene:**
  - a Mod Menu config screen and links (issues, source);
  - GitHub issue forms (steps, `latest.log`, crash report);
  - the version `1.0.0+1.21.1`;
  - a CHANGELOG cleaned up for 1.0 readers;
  - GPL-3.0 notices and MineColonies attribution checked;
  - `mod-publish-plugin` set up with a dry run.

  Done when: each of these is in place, and the dry run succeeds.
- [ ] **26.4** **Release candidate.** A Full tester run, a pack boot, the performance benchmark, and the last 3 nightly
  runs green (blocked until they have run). Then the owner's own play session in the pack's client (carried over from
  Milestone 5). Done when: all of these have passed, and the owner has played.
- [ ] **26.5** (blocked: owner, says go) **Publish.** Done when: the pages are live and linked from the README.

## After 1.0 (not now)

- The 26.x node (Milestone 19, phases 3–4): on hold until the owner says go.
- New jobs and systems: add them here and discuss them with the owner before starting.

---

## Design decisions (from the owner)

- Target **Fabric 1.21.1** to match the Cobbleverse pack. The mod is needed on both the server and the clients.
- **Builders are the priority.** Giving villagers jobs is the core of the mod.
- Trainers: normal villagers get a Trainer job at different levels, and **level up as you battle them** (like trading).
  They range from really easy to extremely difficult. **No badges and no gym leaders.** Each village gets a **Trainer
  Leader** that starts difficult and pays money only.
- Cobblemon integration must be optional.
- License: GPL-3.0-or-later (lets us adapt MineColonies code, which is GPL-3.0-or-later, with attribution).
- Builds from the internet come in as files (.litematic/.schem/.nbt); we don't scrape sites. Only ship our own
  original builds.
- Villagers act as a cohesive unit (Milestone 6). Automatic where possible. Keyed on blocks, never on a particular
  structure, because packs add their own villages. Village farmers harvest into nearby chests.
- *My NPCs* does some of what we want "in a different way" the owner doesn't love. Rebuild the roles that fit the pack
  as villager jobs, not as admin-configured NPCs.
- **As big as MineColonies, if not bigger** (2026-09-28; the long-term goal: for now, 1.0 polish comes first), built in
  parts. Ideas can come from MineColonies (GPL-3.0;
  code only adapted with attribution). What stays ours: it runs on Fabric 1.21.1 in the pack, works with the villagers
  and villages already in the world, and Cobblemon runs through every part.
- **Builds should look sleek and interesting** (2026-09-29). Every build we ship follows `tools/blueprints/STYLE.md` and
  is checked in a render first. Open-source builds may be used only if their licence allows it and they're credited.
- **Village protection** (2026-09-29): a setting on the Village Hall, off unless its owner turns it on.
- **Multi-version** (2026-09-29): follow the minecraft-mod-engineer skill. Keep today's feature packages; no partial
  26.3 release; jar names `<ver>+<mc>`.
- **Textures** (2026-09-29; replaces "no new pixel art for now"): all texture, item, outfit and GUI art goes through the
  owner's minecraft-pixel-art skill (recipes in `tools/textures/art`, drawn by `tools/textures/generate.py`), and he
  approves it in review packages. **Each thing is drawn the way vanilla draws its kind**: a book lies on the diagonal
  like vanilla's books, a tool follows vanilla's tools, a map vanilla's maps, a workstation is built from its
  material's tile like the crafting table.
- **1.0 first** (2026-09-29): the next goal is a public 1.0, polished rather than bigger. Priorities: builders and
  blueprints, then visuals and textures, then performance at scale.
- **Review after every job** (2026-09-29): after each finished item, playtest with the bot and send a review package.
  The owner approves or vetoes. Work continues while he reviews, but only approved work is released.
- **Hands-off** (2026-09-29): the owner reviews only what he can see or feel in game, from screenshots and GIFs.
  Work with nothing to see (tests, tooling, internal fixes) is accepted automatically once the tester passes, and is
  only listed in the next message. He wants to be asked only for real decisions.

## Notes / blocked

- (Sessions: anything that needs the owner, and the link to the latest Full test report. Handoffs go here through
  `sessions.py handoff "<in progress, next, traps>" --as <you>`, which keeps one per kind of session (chat, night):
  carry over anything still true from the previous one.)
- Old branches: `wip/treasury` (unfinished treasury work from before 0.136.0; the treasury has shipped since, so check
  before reusing any of it) and `tests/check-0.137-riding-protection` (the camel test, 21.3).
- 2026-09-29: the plan was installed by the planning chat. The chat that built 0.137 had started two test files for the
  full check (21.2) that were never pushed; write them again as part of 21.2.
- **night handoff** (night-0930-0646, 2026-09-30 07:23Z): night-0930-0646: B2 (flaky workshop test) paused on item/B2 with diagnostics, nothing landed. It reproduces only inside the full suite: register 100 copies of the test (RepeatTests template, one batch each) next to everything else, about 1 failure per 175 copies; the three failures read all had the villager stuck under the test floor with the bench free. Trap: the Maven 429 on first build, just retry after 20 s. Idea to check separately (not filed as a bug, unverified): the workshop template's bottom layer has air cells around the walls, which may cut holes in the ground in real villages; look at a render before filing. Next: B2 again, else 21.x per status.
