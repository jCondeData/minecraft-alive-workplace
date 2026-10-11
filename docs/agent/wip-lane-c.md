# wip/lane-c: what's left (lane-c-1011-1833, 2026-10-11 21:05Z)

Nothing from this run reached `main`. Everything is on this branch, on top of main at 25563eba:

- **35.2** Wonder data, requirements and caps: done, its tests pass (24/24), ticked here only.
- **35.3** Wonders page and Wonder Plan: done, its tests pass (14/14), ticked `--review` here only.
- **35.4** Foundation Stone: about one third (stage build sites, save/reload, cancel; 17/17 Wonder tests). Left: the
  Foundation Stone block and art, handing the plan to a builder, right-click status, sneak-break cancel, the
  announcement and chronicle line, stage I foundation-only, a real-builder GameTest, scene `wonder_site`, CHANGELOG
  and wiki. See commit e014ea6c's message.
- **B103** progress: `-XDstringConcat=inline` in build.gradle.kts (commit f04129a9) and a progress note; not ticked.
- **31.22 is a duplicate**: lane b landed its own 31.22 on main (74c21c8d) while this lane built one (2783d5b4).
  When merging this branch into main, keep main's 31.22 and drop this branch's (ReformStories, arcs/*.json,
  shift_bell.nbt, ReformStoryGameTests and the related edits in Arcs, ArcEffects, Rewards, Reforms, Festivals).

Why it didn't land: two full builds of this stack failed one test, deterministically,
`StewardSafetyGameTests.proposalsStopWhileTwoSitesWaitAndStartOnceSupplied` ("no proposal once supplied"); it passes
alone and on 25563eba. In the full suite the Steward never reaches `StewardWishes.requestPlots` (part 0 of his work).
Best guess: this branch's 31.22 (ReformStories' level tick or the new `any|trait:|mother` arc roles pulling the
Steward away). Dropping this branch's 31.22 in favour of main's may fix it; run the full build to confirm.
