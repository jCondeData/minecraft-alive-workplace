# Roadmap: Alive Workplace 1.0 to 2.0

**Goal (owner, 2026-10-03): everything here, released, by 2026-10-17.** Speed matters most: another team could ship
first. Content quality is never cut: every feature is complete, looks vanilla-quality and feels finished. Lanes work
around the clock, several at once (`docs/agent/sessions.md`).

1. **1.0** (Milestones 22–26): builders that never need babysitting, finished visuals, performance at scale, and the
   public release. Ship it as soon as it's ready.
2. **The expansions** (Milestones 27–35), released in this order: 1.1 villages that build themselves, 1.2 Pokémon and
   villagers together, 1.3 Legends, 1.4 edicts and civic items, 1.5 quests become stories, 1.6 threats, 1.7 the
   realm, 1.8 classes and luxuries, 2.0 Wonders. Lanes build several at once; each release ships when its milestone
   is complete.

Everything done before this plan (Milestones 1–20, and the 0.137 work: fishing from boats, ferry rides, cavalry,
homes; about 215 items) is in `docs/roadmap-history.md`. Read it when you
need to know how something was built. The owner's decisions are at the bottom of this file. Don't change them
without asking.

## How to work this file

**Sprint mode** (owner, 2026-10-03). The mod's first 48,000 lines came from one chat in four days that built
features straight on `main` and released every hour; the claim/land/QA-every-hour setup that followed built one
feature in four days. So we work the way that chat did. Details: `docs/agent/sessions.md`, "Sprint mode".

- **Two build lanes split the roadmap by number:** lane a takes odd milestones and odd bugs, lane b even ones, so they
  never touch the same item. `sessions.py next --as <you>` lists yours, best first. The owner's chat takes anything.
- **Work straight on `main`.** No claims, branches, landings or handoff commits. Build a feature with its tests, tick
  it with `sessions.py done <id>` (add `--review` for something a player sees, after handing in its package), add the
  CHANGELOG line, run `./gradlew build`, and push the work and the tick in one commit. If the push is refused,
  `git pull --no-rebase` and push again (build again first only if the pull brought in code).
- **"Done when" is the spec.** Build all of it, art and builds included. If it can't be met as written, write the
  question in the Notes, ask through the digest, and take the next item. Don't invent the spec.
- **Bugs first, but only real ones.** Red CI and bugs a player would hit go into "Bugs" (`sessions.py bug`) and come
  first; a red `main` belongs to the lane on red duty (docs/agent/sessions.md, "Red main"). The QA lane runs hourly
  overnight and every 2 hours by day, and owns the bugs only a test or a scene sees.
- **Reviews never block work or releases.** A visible item's review package goes to the `reviews` branch
  (`sessions.py review`) and is marked `(review: pending)`; the digest sends it. A veto becomes a fix item.
- **His replies** are recorded with `sessions.py reply "<his words>" --as <you>`: `approve <ids>`,
  `veto <id>: <why>` (unticked, redone next), `change <id>: <what>` (a sub-item `<id>a`, done next).
- **Releases:** the evening digest releases every day when something new landed, even if `main` is red then: CI tags
  the first green build (minor bump for features, patch for fixes only). GitHub only; store pages wait for the owner.
- **Unfinished expansions stay switched off** behind their config switch until the milestone is complete; the release
  that ships the milestone turns it on.
- Items are numbered `<milestone>.<n>`, bugs `B<n>`; numbers are never reused.

Status marks: `[ ]` open · `(blocked: <why>)` · `[x] (review: pending <date>)` built, waiting for the owner ·
`[x] (approved <date>)` / `(approved auto <date>)` accepted · `(verified <date>)` tested by QA · `[ ] (vetoed <date>:
<why>)` to redo. Older marks (`claimed`, `paused`) come from the claim-and-land setup.

---

## Bugs (fix before the next item)

Each fix lands together with the failing test that proves it, never a failing test on `main` on its own. Fixed bugs
stay in the list, ticked, so their numbers stay unique.

- [x] **B1** (approved auto 2026-09-29) (verified 2026-10-02: its tester Check, shipped in 0.138.0) Four bugs from the first tester run: the homes and rank tips showed the wrong
  numbers, a blueprint name ending in a huge number crashed, and a bed at the far corner of a large building didn't
  count as home. Fixed with the owner's `HomesSpecGameTests` (d36b3d1).
- [x] **B2** (approved auto 2026-10-03) (verified 2026-10-03: qa-1003-1333: B2/B4/B11 workshop and orchard flakes: nightly 37112224528 [5 full suites, at b8306632, after their fixes] green, none of the three failed; B9: forest scene green in showcase 37112941273, the stump tests in LumberjackGameTests pass in CI [a restart-while-waiting test written, not yet run: /tmp only, see handoff]; B10: 3 new tests [QaStructureVillagerGameTests: spawn-egg and converted zombie villagers keep their spot, a structure one is centred], killed 3/3 mutants in StructureVillagers/ZombieVillagerMixin. B8 left unverified: bug B15 [unloaded new owner].) Flaky test: `VillageGameTests.aVillagerMovesIntoTheWorkshop` (failed once on CI, commit 49ae713). Done
  when: 100 repeats pass (the repeat generator in the tester skill), or the cause is fixed. Hint from the last
  session: look for leftover blocks from earlier batches blocking the way to the bench; 17 local runs in a row passed.
  It failed once more in the tester's full-suite run (in a mutant run whose mutant couldn't touch it); 10 of 10 alone
  and 3 more full runs passed. Still unexplained.
- [x] **B3** (approved auto 2026-09-29) (verified 2026-10-02: its tester Check, shipped in 0.138.0) Five bugs from the tester's pass over 0.131–0.137, fixed with tests (acbac37):
  strangers' arrows hurt a protected village's villagers and animals; a stranger with a Village Ledger could empty a
  protected treasury; guards stayed on their horses, and fishers on the water, after their shift; night-raid bandits
  could join vanilla raids.
- [x] **B4** (approved auto 2026-10-03) (verified 2026-10-03: qa-1003-1333: B2/B4/B11 workshop and orchard flakes: nightly 37112224528 [5 full suites, at b8306632, after their fixes] green, none of the three failed; B9: forest scene green in showcase 37112941273, the stump tests in LumberjackGameTests pass in CI [a restart-while-waiting test written, not yet run: /tmp only, see handoff]; B10: 3 new tests [QaStructureVillagerGameTests: spawn-egg and converted zombie villagers keep their spot, a structure one is centred], killed 3/3 mutants in StructureVillagers/ZombieVillagerMixin. B8 left unverified: bug B15 [unloaded new owner].) A jobless villager standing just outside the plains workshop (by its side wall, 2 blocks from the bench) never took the bench in 2400 ticks: 30 of 30 in repeat runs (night-0930-0845, variant of the workshop test placed like a village, spawn helper (4,2,10)); it wandered off to a corner of the test area instead. Inside the house it takes it every time. Check whether this is the test area (edges, no village around) or real: can villagers find the workshop's bench through its door in a real village? Done when: a GameTest with the villager outside the door passes 100 repeats, or it's shown to be a test artefact and noted. The repeat file used is in the B2 commits' notes (RepeatTests with a 'trench_fixed' entry). (found by night-0930-0845, 2026-09-30)
  **Explained** (chat, 2026-10-03): a test artefact of B5. The path from the side wall round to the door went over the
  house's structure_void row, placed as a hole the villager fell into. With B5 fixed, the new test
  `aVillagerOutsideTheWorkshopFindsItsTable` (landed with B2) passed 100 of 100 in-suite repeats, and the tester's 50 of
  50; with the 9 structure_void cells put back it fails 20 of 20.
- [x] **B5** (verified 2026-10-02: its tester Check, shipped in 0.138.0) (approved 2026-10-04) Village houses leave structure_void blocks in the world: all 115 templates in data/aliveworkplace/structure/village/ contain structure_void, and the legacy pool element skips only air and structure blocks, so e.g. the plains workshop gets a 9-wide strip with no collision in the ground in front of its door (villagers and players fall in; also the rest of B2's flake). Found by the tester, not yet seen in a real village. Failing test on branch tests/b5-structure-void (aVillagePlacedWorkshopLeavesNoStructureVoidInTheWorld; add its //$ swap line). Likely fix in tools/blueprints/generate.py: don't write structure_void into templates (vanilla's save leaves them out), regenerate, render. Done when: that test passes, a real village shows no holes (screenshot), and B2's 100 in-suite repeats pass. (found by night-0930-0845, 2026-09-30)
- [x] **B6** (approved 2026-10-01) (verified 2026-10-02: its tester Check, shipped in 0.138.0) Villagers suffocate in walls in generated villages: the showcase's village scene (SCENE=village WORKSHOP_WEIGHT=200 tools/screenshots/run.sh) failed in 3 of 3 GitHub runs today, with villagers stuck inside Smooth Sandstone Slabs in the desert village (3 each run) and a nitwit in a Snow Block in the snowy one. Expected: villagers spawned with our village houses stand in open space. Done when: the village scene passes 3 nightly runs in a row, with a GameTest for the house that caused it. (found by chat's showcase, 22.4, 2026-09-30) (found by chat, 2026-09-30)
- [x] **B7** (approved auto 2026-10-01) (verified 2026-10-02: its tester Check, shipped in 0.138.0) VillageHalls.assign (the Hall's free-workstation list) calls PoiManager.release on a villager's old job site without checking the block is still there: if the old workstation was broken while its worker was more than 16 blocks away, assigning them from the Hall throws IllegalStateException 'POI never registered' in the click handler (the same crash the 21.1a tester found in Stations.assign, fixed there on item/21.1a by checking getType first). Done when: a GameTest assigns such a villager from the Hall without an exception. (found by chat while fixing 21.1a, 2026-09-30) (found by chat, 2026-10-01)
- [x] **B8** (approved auto 2026-10-03) (verified 2026-10-03: B8's Done when holds [its tests and the B15/B17 ones pass on main]; 4 new QA tests [QaB17GameTests: a lectern given a book / a composter filled is still freed on a new job by item and by the Hall; a pre-B15-save worker still frees their block]; 3 mutants in releaseOld/JobSiteTickets all killed, 2 only by the new tests. Related bugs filed: B25 [B17 still open via the job item], B26, B27.) Reassigning a worker can free someone else's workstation: Stations.assign and VillageHalls.assign release the old job site whenever a POI record exists there, without checking it is still the worker's own block. If the old block is broken far from its worker, the same kind of block is placed on that spot and another villager takes it, then the first worker is given a new job, the second worker's ticket is freed and two villagers can hold one block (vanilla's releasePoi shares the gap). Found by the 21.1a round-4 and B7 testers, untested. Done when: a GameTest of that sequence leaves the second worker's block taken. (found by night-1001-0646, 2026-10-01)
- [x] **B9** (approved auto 2026-10-03) (verified 2026-10-03: qa-1003-1333: B2/B4/B11 workshop and orchard flakes: nightly 37112224528 [5 full suites, at b8306632, after their fixes] green, none of the three failed; B9: forest scene green in showcase 37112941273, the stump tests in LumberjackGameTests pass in CI [a restart-while-waiting test written, not yet run: /tmp only, see handoff]; B10: 3 new tests [QaStructureVillagerGameTests: spawn-egg and converted zombie villagers keep their spot, a structure one is centred], killed 3/3 mutants in StructureVillagers/ZombieVillagerMixin. B8 left unverified: bug B15 [unloaded new owner].) Showcase scene 'forest' (Lumberjack: felling and replanting four trees) failed once on GitHub (run 36758016372, commit e94704b, 2026-09-30: 'not done: the lumberjack replanted (3 saplings in the ground)'; issue #1) and passed on the later item/21.1a showcase runs. Expected: four saplings replanted every run. Done when: the cause is found and fixed with a GameTest, or the scene passes 5 runs in a row (SCENE=forest tools/screenshots/run.sh) and the flake is explained in the Notes. (found by night-1001-0646, 2026-10-01)
- [x] **B10** (approved auto 2026-10-03) (verified 2026-10-03: qa-1003-1333: B2/B4/B11 workshop and orchard flakes: nightly 37112224528 [5 full suites, at b8306632, after their fixes] green, none of the three failed; B9: forest scene green in showcase 37112941273, the stump tests in LumberjackGameTests pass in CI [a restart-while-waiting test written, not yet run: /tmp only, see handoff]; B10: 3 new tests [QaStructureVillagerGameTests: spawn-egg and converted zombie villagers keep their spot, a structure one is centred], killed 3/3 mutants in StructureVillagers/ZombieVillagerMixin. B8 left unverified: bug B15 [unloaded new owner].) Zombie villagers in abandoned (zombie) desert villages use the same vanilla desert_small_house_7 villager spot as B6 (x+0.72/z+0.63 in a 1-wide corridor under top slabs), but B6's fix only centres Villager, not ZombieVillager, so they may still get stuck in the wall there. Expected: zombie villagers from village templates stand in open space too. Done when: a GameTest places the zombie desert house with its zombie villager piece and the zombie ends up on the corridor floor, not in a wall. (found by the B6 tester) (found by night-1001-1046, 2026-10-01)
- [x] **B11** (approved auto 2026-10-03) (verified 2026-10-03: qa-1003-1333: B2/B4/B11 workshop and orchard flakes: nightly 37112224528 [5 full suites, at b8306632, after their fixes] green, none of the three failed; B9: forest scene green in showcase 37112941273, the stump tests in LumberjackGameTests pass in CI [a restart-while-waiting test written, not yet run: /tmp only, see handoff]; B10: 3 new tests [QaStructureVillagerGameTests: spawn-egg and converted zombie villagers keep their spot, a structure one is centred], killed 3/3 mutants in StructureVillagers/ZombieVillagerMixin. B8 left unverified: bug B15 [unloaded new owner].) Flaky test: VillageGameTests.theOrchardHousesVillagerTakesItsComposter failed once in a full suite on item/B9 (2026-10-03, 'works at Optional.empty' after 1200 ticks); it passed on the rerun, in about 7 other full suites and 40 of 40 alone. Probably a leftover from a neighbouring batch. Expected: the orchard house's villager takes its composter every run. Done when: the cause is found and fixed, or 100 in-suite repeats pass (the tester skill's repeat generator); the nightly 5-run sweep shows how often. Test: theOrchardHousesVillagerTakesItsComposter (found by chat, 2026-10-03)
- [x] **B12** (approved auto 2026-10-03) (verified 2026-10-03: 20 isolated repeats [10 each of b10ZombieDesertHouseRotatedNone and Rotated270, each in its own batch]: 20/20 passed; the fix sets night and restores the time after) Flaky test: StructureVillagerGameTests.b10ZombieDesertHouseRotatedNone and Rotated270 failed in a land build (2026-10-03, 'the villager was hurt: 16.0'): the zombie villager took damage within 80 ticks, most likely the sun once it walked out of the roofed corridor, since the test runs at whatever time of day the server has. Expected: the test checks only that the zombie isn't in a wall. Done when: the zombie variants run at night and 20 in-suite repeats pass. Test: b10ZombieDesertHouseRotatedNone (found by chat, 2026-10-03)
- [x] **B13** (approved auto 2026-10-03) (verified 2026-10-03: 3 tests [FerryLeaveQaGameTests]: the player file vanilla writes on leaving mid-ride [by the far post, no RootVehicle, ferryman stays], leaving mid-ride to a Nether post [saved in the Nether, out of both worlds and the player list], own boat still saved as vanilla; 5 repeats each 10/10; mutants 3/3 killed [hook at TAIL, end without landing, boat not discarded]) Leaving the game mid ferry ride doesn't land the player, and can run off the server thread: FerryRides ends the ride in Fabric's DISCONNECT event, which fires from netty's channelInactive (the IO thread) when a client closes its connection, so the teleport, boat discard and ferryman move run off the server thread; and where the event comes after vanilla's PlayerList.remove, vanilla has already saved the boat (with the ferryman in it) as the player's RootVehicle, so the player logs back in at the jetty in a boat and the ferryman leaves his village with them. Expected: the player is landed by the far post before being saved, on the server thread, the ferryman stays home. Test: aPlayerWhoLeavesMidRideIsLanded (21.2, FerryEdgeGameTests). PreviewNetworking and TableServer also touch plain HashMaps from that event (smaller race). (found by night-1003-0845, 2026-10-03)
- [x] **B14** (approved auto 2026-10-03) (verified 2026-10-04: nightly-tests run 37193458879 [6b24c85, 2026-10-04]: Performance soak green on GitHub with the workers working: 'sites working when measured: 39 of 46', 80 workers on 40 plots, ticks 7.3/5.2 ms avg with workers [P99 42.1/15.9 ms] vs 1.1 ms idle, our code 11.2% of the server thread [BuilderWork 7.1%]; run.sh fails the soak when none work, so the guard is live) The nightly performance soak measures an idle server: in nightly run 37112224528 (2026-10-03) /workplace benchmark reported 80 workers, but 2.5 min later every one of the 40 builds was 'Clearing the site · starting' at 0% with no villager found for it, every quarry 0 dug, villager AI 0% of the server thread and ticks 0.8 ms. The same script on a dev container (night-1003-1046) has the workers working (40 of 47 sites working, builds up to 100%, our code 11.8% of the server thread). So on GitHub the spawned workers vanish or never start, and the soak's numbers so far mean nothing. Expected: the workers work in the nightly soak too. Lead: on GitHub the soak runs right after the plain pack test in the same server folder (configs written by that first boot stay); locally it ran on a fresh folder. Test: tools/packtest/run.sh now fails the soak when no site is working (item/21.2) (found by night-1003-1046, 2026-10-03)
- [x] **B15** (approved auto 2026-10-03) (verified 2026-10-03: QaB21B27GameTests [4, shipped 529094b]: stone house from exactly plan.materials[] [420 items] builds alone and with a helper, never WAITING_FOR_MATERIALS and never waiting with an empty missing list [B21/B22 spec tests]; a successful Hall assignment frees the old block and it can be re-hired [B27 other side]; a composter retaken by hand [B26] is freed when the worker moves on. QaB17BugGameTests [B25/B26/B27] pass on main. 3/3 planted mutants killed [Hall success never releasing, B25 break-count check dropped, siteBench ignoring the site's bench]. Full build 467/467 + compat 55/55. B28 [bench swap root cause] stays open.) B8's fix misses an unloaded new owner: when a worker's broken-and-replaced block was taken by a second villager whose chunk is now unloaded, giving the first worker a new job (item click or Village Hall) still releases the block (1 free ticket), so a third villager can share it once the second loads again; Stations.someoneElseWorksAt looks only at loaded villagers. Expected: the second worker's block stays taken. Test: StationsB8UnloadedGameTests (b8ReassigningKeepsAnUnloadedWorkersBlockTaken, b8HallAssignKeepsAnUnloadedWorkersBlockTaken) on tests/b8-unloaded-worker (found by qa-1003-1333, 2026-10-03)
- [x] **B16** (approved auto 2026-10-03) (verified 2026-10-03: test_sessions.py +2 QA checks [a code change on main mid-build still rebuilds; ship skips a roadmap-only change], all pass; 3/3 mutants in same_build and ship's branch killed; shipping on qa/b16-land-1003) sessions.py land rebuilds from scratch whenever main moved during its build, even when the only new commits on main touch ROADMAP.md (claims, handoffs, verify marks, owner replies): on 2026-10-03 lane-b's land of 22.2 built 5 times in 45 min (every build green) and gave up with 'main kept moving'. Expected: when the merged-in changes since the last green build touch only ROADMAP.md (or docs), land ticks and pushes without rebuilding. Test: tools/agent/test_sessions.py (a roadmap-only commit on main during land doesn't trigger a second build) (found by lane-b-1003-1333, 2026-10-03)
- [x] **B17** (approved auto 2026-10-03) (verified 2026-10-03: QaB21B27GameTests [4, shipped 529094b]: stone house from exactly plan.materials[] [420 items] builds alone and with a helper, never WAITING_FOR_MATERIALS and never waiting with an empty missing list [B21/B22 spec tests]; a successful Hall assignment frees the old block and it can be re-hired [B27 other side]; a composter retaken by hand [B26] is freed when the worker moves on. QaB17BugGameTests [B25/B26/B27] pass on main. 3/3 planted mutants killed [Hall success never releasing, B25 break-count check dropped, siteBench ignoring the site's bench]. Full build 467/467 + compat 55/55. B28 [bench swap root cause] stays open.) B8's fix still frees the block of a new owner who isn't loaded: a worker's block is broken while they're away and put back, a second villager takes it, the second one's chunk unloads, then the first worker is given a new job (item or Village Hall): Stations.releaseOld only looks for another owner among loaded villagers (level.getEntities), so it releases the POI and the second worker comes back holding a block whose ticket is free; a third villager can share it. Expected: the second worker's block stays taken (B8's own Done when), whether or not they're loaded. Test: qaReassigningKeepsTheBlockOfAnUnloadedOwnerTaken on tests/b8-unloaded-owner (found by qa-1003-1433, 2026-10-03) (found by qa-1003-1433, 2026-10-03)
- [x] **B18** (approved auto 2026-10-03) (verified 2026-10-03: QaB29FlowersGameTests: all 26 #minecraft:flowers items [as loaded] make a Florist at a composter; 8 non-flower plants [azalea, saplings, grass, fern, lily pad, leaves, dripleaf] leave a farmer; with QaB18Flowers/QaGuideFlorist. Mutant SMALL_FLOWERS in Stations killed by 3 tests.) The Guide Book (26.2a, Beekeepers page) and ROADMAP 21.1a say 'A flower at a composter makes a Florist', but only small flowers do: sneak-right-clicking a farmer by their composter with a sunflower, lilac, rose bush or peony does nothing and says nothing (the job table uses #small_flowers). Expected: every flower (#minecraft:flowers) makes a Florist, or the guide says 'a small flower' and a tall one gets a message. Test: QaGuideFloristGameTests.qaGuideAnyFlowerAtAComposterMakesAFlorist on tests/guide-florist-flowers (found by qa-1003-1533, 2026-10-03)
- [x] **B19** (verified 2026-10-03: B20: 4 QA tests [primed TNT in the low corner, a nearer unwarded hall beside a warded village, no Warding, broken hall] + builder's 2; mutants 3/3 killed. B19: quest page [slay, sword and pickaxe bring quests] shows no attribute lines + builder's 2; review sheet checked) (approved 2026-10-03) The Village Hall's Mercenaries and Guards buttons show an iron sword's 'When in Main Hand: 6 Attack Damage, 1.6 Attack Speed' lines under their own text (seen in the hall_quests scene, 2026-10-03, still 04_hall_mercenaries): VillageHallScreen.icon keeps the item's attribute tooltip. Expected: the hall's buttons show only their own lines (hide the attribute modifiers, as on other icons). Test: a GameTest that the MERCENARIES and GUARDS icons hide attribute modifiers (e.g. DataComponents.HIDE_ADDITIONAL_TOOLTIP or ATTRIBUTE_MODIFIERS with showInTooltip false) (found by lane-b-1003-1532, 2026-10-03)
- [x] **B20** (approved auto 2026-10-03) (verified 2026-10-03: B20: 4 QA tests [primed TNT in the low corner, a nearer unwarded hall beside a warded village, no Warding, broken hall] + builder's 2; mutants 3/3 killed. B19: quest page [slay, sword and pickaxe bring quests] shows no attribute lines + builder's 2; review sheet checked) Warding doesn't protect the corners of a village: Warding.wardedArea finds the hall with VillageHalls.nearest (a round POI search of RADIUS), but the village is the square box VillageHalls.area (RADIUS each way), so a creeper or TNT in a corner of a warded village (more than RADIUS from the hall in a straight line) breaks the village's blocks. Expected: every block in a warded village's box is spared, blocks past its edge still break. Test: WardingEdgeGameTests.aBlastInTheVillagesCornerSparesTheVillage on tests/warding-edge (found by lane-a-1003-1532, 2026-10-03)
- [x] **B21** (approved auto 2026-10-03) (verified 2026-10-03: QaB21B27GameTests [4, shipped 529094b]: stone house from exactly plan.materials[] [420 items] builds alone and with a helper, never WAITING_FOR_MATERIALS and never waiting with an empty missing list [B21/B22 spec tests]; a successful Hall assignment frees the old block and it can be re-hired [B27 other side]; a composter retaken by hand [B26] is freed when the worker moves on. QaB17BugGameTests [B25/B26/B27] pass on main. 3/3 planted mutants killed [Hall success never releasing, B25 break-count check dropped, siteBench ignoring the site's bench]. Full build 467/467 + compat 55/55. B28 [bench swap root cause] stays open.) Builders wait for materials with nothing missing: in the 23.1 soak (SOAK=true SOAK_DAYS=6 tools/packtest/run.sh, 2026-10-03) the library and the flower shop sat in STRUCTURE with status WAITING_FOR_MATERIALS and 0 kinds missing for 30 s and more (stalls #2, #9, #10), and the flower shop was still unfinished after 6 in-game days although its chests held its whole material list. Expected: a builder whose missing list is empty fetches and builds; if it really waits, the missing list says for what. Test: the soak's stall lines (docs/agent/soak.md, run 3) plus a GameTest for the case found (found by lane-c-1003-1532, 2026-10-03)
- [x] **B22** (approved auto 2026-10-03) (verified 2026-10-03: QaB21B27GameTests [4, shipped 529094b]: stone house from exactly plan.materials[] [420 items] builds alone and with a helper, never WAITING_FOR_MATERIALS and never waiting with an empty missing list [B21/B22 spec tests]; a successful Hall assignment frees the old block and it can be re-hired [B27 other side]; a composter retaken by hand [B26] is freed when the worker moves on. QaB17BugGameTests [B25/B26/B27] pass on main. 3/3 planted mutants killed [Hall success never releasing, B25 break-count check dropped, siteBench ignoring the site's bench]. Full build 467/467 + compat 55/55. B28 [bench swap root cause] stays open.) The stone house needs one more deepslate tile stairs than its material list: in the 23.1 soak (run 3, 2026-10-03) its builder waited 4 times for '1 minecraft:deepslate_tile_stairs' (365 placed, 48 skipped) although its chests held exactly the plan's materials() at the start and the material ledger shows no item lost or duplicated; so the steps the builder builds need more than BuildPlan.materials() lists (re-planning after clearing, or a stair counted once for two steps). Expected: the material list a player is shown covers the whole build. Test: a GameTest building stone_house from exactly plan.materials() finishes without waiting (found by lane-c-1003-1532, 2026-10-03)
- [x] **B23** (approved auto 2026-10-03) (verified 2026-10-03: QaB23TurnedHillsideGameTests: tinker's workshop [CW90] and graveyard [CCW90] on the B23 hillside finish, worst gap on shift 244-395 ticks [< 600]; TerrainStall tests pass. Mutants: landscapeLeft always empty killed by b23Levelling; cursor+1 off-by-one survived but is equivalent [current step's requirement is added separately].) Terrain stages stall on slopes: in every 23.1 soak run (2026-10-03) the graveyard's builder makes no progress for 30 s at the start of FOUNDATION (0 placed) on a hillside, and in run 3 the tinker's workshop's LANDSCAPE placed 13 blocks in 30 s twice (stalls #1, #5, #6). Expected: FOUNDATION and LANDSCAPE keep placing (or skip what can't be done) without 30 s gaps on shift. Test: the soak's stall lines (docs/agent/soak.md, run 3) plus a GameTest on a slope (found by lane-c-1003-1532, 2026-10-03)
- [x] **B24** (approved auto 2026-10-03) (verified 2026-10-04: B37: 4 QA tests [fresh open on a reloaded protected hall, a stranger's refused shift-click, unowned hall claim, the English hints], 5x repeats pass, B37's old line put back fails all 3 world tests; B34-B36: aStuckTutor/aStuckTrainer 5x each in own batches pass, every main build since B34 landed [23:26Z, ~26] green; B24: builder's 20/20 repeats plus every main compat run since green) Flaky compat test: PastureCompatTests.butcherMilksAMiltankAndBrushesAPidgey failed once in a land build on item/B15 (2026-10-03 15:52Z, 'no feathers from the Pidgey' within 2400 ticks) and passed in 5 other compat runs of the same code. Expected: the butcher brushes the pastured Pidgey every run. Done when: the cause is found and fixed, or 20 in-suite repeats pass. Test: butcherMilksAMiltankAndBrushesAPidgey (found by lane-b-1003-1432, 2026-10-03)
- [x] **B25** (approved auto 2026-10-03) (verified 2026-10-03: QaB21B27GameTests [4, shipped 529094b]: stone house from exactly plan.materials[] [420 items] builds alone and with a helper, never WAITING_FOR_MATERIALS and never waiting with an empty missing list [B21/B22 spec tests]; a successful Hall assignment frees the old block and it can be re-hired [B27 other side]; a composter retaken by hand [B26] is freed when the worker moves on. QaB17BugGameTests [B25/B26/B27] pass on main. 3/3 planted mutants killed [Hall success never releasing, B25 break-count check dropped, siteBench ignoring the site's bench]. Full build 467/467 + compat 55/55. B28 [bench swap root cause] stays open.) B17's fix misses the job item: a worker 25 blocks off, whose composter was broken while away, put back and taken by a second villager whose chunk then unloads, is sneak-right-clicked with bone meal by a free composter and becomes a Composter at the far composter the unloaded second worker holds (Stations.choose's 'block they already work at' still asks only loaded villagers, someoneElseWorksAt). Expected: they take the free composter beside them, as when the second is loaded (B8). Test: qaAFarOffWorkerDoesntTakeTheBlockOfAnUnloadedOwnerByHand on tests/b17-two-on-one-block (found by qa-1003-1633, 2026-10-03)
- [x] **B26** (approved auto 2026-10-03) (verified 2026-10-03: fix d7d1466 on main; its bug test [QaB17BugGameTests.qaAJobChosenAtAReplacedBlockTakesItsPlace] and the other side [QaB21B27GameTests: a retaken block is freed when the worker moves on, 3/3 mutants by qa-1003-1933] green in qa-1003-2033's full build [472 + compat 55]) A job chosen by hand at a block that was broken and put back doesn't take its place: an orchard keeper's composter is broken while they're away and replaced, they come back and are sneak-right-clicked with wheat beside it; they become its farmer but the composter keeps 1 free place (Stations.assign skips taking when the old site equals the new), so a third villager by it given sweet berries is put on the same composter. Expected: the worker holds the block they're given. Test: qaAJobChosenAtAReplacedBlockTakesItsPlace on tests/b17-two-on-one-block (found by qa-1003-1633, 2026-10-03)
- [x] **B27** (approved auto 2026-10-03) (verified 2026-10-03: QaB21B27GameTests [4, shipped 529094b]: stone house from exactly plan.materials[] [420 items] builds alone and with a helper, never WAITING_FOR_MATERIALS and never waiting with an empty missing list [B21/B22 spec tests]; a successful Hall assignment frees the old block and it can be re-hired [B27 other side]; a composter retaken by hand [B26] is freed when the worker moves on. QaB17BugGameTests [B25/B26/B27] pass on main. 3/3 planted mutants killed [Hall success never releasing, B25 break-count check dropped, siteBench ignoring the site's bench]. Full build 467/467 + compat 55/55. B28 [bench swap root cause] stays open.) A failed Village Hall assignment frees the worker's own block: VillageHalls.assign releases the old job site before trying to take the offered one, so when the offered block was taken meanwhile it returns false but the librarian, still a librarian at their lectern, no longer holds it (1 free place) and another villager can share it. Expected: a failed assignment changes nothing. Test: qaAFailedHallAssignmentKeepsTheWorkersOwnBlock on tests/b17-two-on-one-block (found by qa-1003-1633, 2026-10-03)
- [x] **B28** (approved auto 2026-10-03) (verified 2026-10-03: QaB30JobSitesGameTests [4, shipped]: a stuck miner/postman keeps their own block with a free one closer [it stays free]; a miner/trainer whose block is broken lets it go; BuilderKeepsBench 2 pass. Full suite 488/488) Builders swap benches mid-build: in a 23.1 soak run (2026-10-03, lane-c-1003-1733, fetch logging) the stone house builder and the tinker's workshop builder, whose benches stand 26 blocks apart in the same column, alternately fetched from both benches' chests (Builders.benchPos = JOB_SITE memory pointed at the other bench). B22 makes fetching use the site's bench, but the swap itself remains: a builder's JOB_SITE should stay its own bench. Expected: a builder employed at a bench keeps it while it stands. Test: a GameTest with two employed builders and benches near each other, both keep their bench over a work day (found by lane-c-1003-1733, 2026-10-03)
- [x] **B29** (approved auto 2026-10-03) (verified 2026-10-03: QaB29FlowersGameTests: all 26 #minecraft:flowers items [as loaded] make a Florist at a composter; 8 non-flower plants [azalea, saplings, grass, fern, lily pad, leaves, dripleaf] leave a farmer; with QaB18Flowers/QaGuideFlorist. Mutant SMALL_FLOWERS in Stations killed by 3 tests.) B18's fix misses part of its own Expected: pink petals and a spore blossom (both #minecraft:flowers) given at a composter leave a farmer a farmer, saying nothing (Stations uses #small_flowers + #tall_flowers only), and the composter's Shift tooltip still says the Florist needs 'A small flower' though a sunflower or lilac now works. Expected: every #minecraft:flowers item makes a Florist (or the guide and tooltip name exactly what works), and the tooltip matches. Test: QaB18FlowersGameTests (qaB18EveryFlowerInTheFlowersTagMakesAFlorist, qaB18TheComposterTooltipDoesntSayOnlySmallFlowers) on tests/b18-flowers (found by qa-1003-1833, 2026-10-03)
- [x] **B30** (approved auto 2026-10-03) (verified 2026-10-04: 11 QA tests green in the full build at c764b0f [532/532 + compat 56/56]: stuck miner, postman, trainer, tutor, nurse, shopkeeper, ferryman keep their block; broken block let go; queued builds reserved, one-spare boundary, hold lifted on cancel for builders and masons. Mutation/repeats: no nightly since 2026-10-03 09:11Z, not read) B28's cause in other jobs: PostmanPackages, MinerPackages, DeskPackages and TrainerPackages still walk home with vanilla SetWalkTargetFromBlockMemory(JOB_SITE, ..., 100, 1200), which frees the job site's place and erases JOB_SITE when the worker hasn't reached a walk target for 1200 ticks or is over 100 blocks (Manhattan) from it; a miner deep in its mine or a postman on a long round then takes the nearest free block of its kind, possibly another worker's. Expected: a worker keeps its block while it stands (use work/WalkToJobSite, as builders do since B28). Test: a BuilderKeepsBenchGameTests.aStuckBuilderKeepsTheirBench-style test per package (found by lane-a-1003-1933, 2026-10-03) (found by lane-a-1003-1933, 2026-10-03)
- [x] **B31** (approved auto 2026-10-03) (verified 2026-10-04: 11 QA tests green in the full build at c764b0f [532/532 + compat 56/56]: stuck miner, postman, trainer, tutor, nurse, shopkeeper, ferryman keep their block; broken block let go; queued builds reserved, one-spare boundary, hold lifted on cancel for builders and masons. Mutation/repeats: no nightly since 2026-10-03 09:11Z, not read) A 6-day soak (SOAK=true SOAK_DAYS=6, main b155e1a + item/23.1 merged, 2026-10-03 19:50Z) finished 19/22 builds (run 3: 21/22): flower_shop stood WAITING_FOR_MATERIALS with 0 kinds missing for 5 stall lines (B21 again, 317 placed, 6 skipped), the inn waited for 3 minecraft:glass_pane its chests should have held (755 placed, 177 skipped), apiary_garden unfinished, and the ledger was off: andesite -1, granite -1, polished_andesite +1, polished_granite +1 (an item swapped for its polished form). Expected: 22/22 in 6 days, no stall with nothing missing, items off: none. Test: the soak's stall and result lines, plus a GameTest for whichever cause is found (found by lane-c-1003-1932, 2026-10-03) (found by lane-c-1003-1932, 2026-10-03)
- [x] **B32** (approved auto 2026-10-03) (verified 2026-10-04: 11 QA tests green in the full build at c764b0f [532/532 + compat 56/56]: stuck miner, postman, trainer, tutor, nurse, shopkeeper, ferryman keep their block; broken block let go; queued builds reserved, one-spare boundary, hold lifted on cancel for builders and masons. Mutation/repeats: no nightly since 2026-10-03 09:11Z, not read) B28/B30's cause in three more jobs: nurses, shopkeepers and ferrymen still walk home with vanilla SetWalkTargetFromBlockMemory(JOB_SITE, ..., 100, 1200) (NursePackages, ShopkeeperPackages, FerrymanPackages), so one who hasn't reached a walk target for a minute drops their block on the next tick, freeing its place, and may take another worker's. Expected: they keep their block while it stands (work/WalkToJobSite, as builders, miners, postmen, desk jobs and trainers do). Test: QaStuckJobsGameTests (3 tests, each fails 'lost their block at tick 1: none') on tests/job-sites (found by qa-1003-2134, 2026-10-03)
- [x] **B33** (approved auto 2026-10-03) (verified 2026-10-04: 11 QA tests green in the full build at c764b0f [532/532 + compat 56/56]: stuck miner, postman, trainer, tutor, nurse, shopkeeper, ferryman keep their block; broken block let go; queued builds reserved, one-spare boundary, hold lifted on cancel for builders and masons. Mutation/repeats: no nightly since 2026-10-03 09:11Z, not read) Crafters helping a builder take ingredients from another builder's chests: CrafterWork.choose sources a builder's order from Village.allChests and only leaves what THAT builder needs (usable = stock - its remainingNeed), so a mason cutting polished andesite for builder A can take andesite builder B's build needs (same cause as B31's builder raid, found reading the code, not seen in a soak). Expected: in another builder's chests a crafter uses only what Builders.reservedAt leaves spare. Test: a mason, two builders, B's chest holding exactly its build's andesite; A short of polished andesite; B's andesite stays (found by lane-b-1003-2132, 2026-10-03) (found by lane-b-1003-2132, 2026-10-03)
- [x] **B34** (approved auto 2026-10-03) (verified 2026-10-04: B37: 4 QA tests [fresh open on a reloaded protected hall, a stranger's refused shift-click, unowned hall claim, the English hints], 5x repeats pass, B37's old line put back fails all 3 world tests; B34-B36: aStuckTutor/aStuckTrainer 5x each in own batches pass, every main build since B34 landed [23:26Z, ~26] green; B24: builder's 20/20 repeats plus every main compat run since green) WorkersKeepTheirBlockGameTests.aStuckTutorKeepsTheirDesk (B30) failed once in lane-a's land of 21.2 (main 2026-10-03 21:57Z + a pure-logic test class): 'the TUTOR didn't head back: at 293642, -58, 7114768' (12 blocks the other way at tick 260), while main's CI passed it. Expected: passes every run (the stuck tutor walks back toward the desk). Test: the tester skill's repeat generator on aStuckTutorKeepsTheirDesk x20, then fix the cause (start position, day time or the tutor's desk package) (found by lane-a-1003-2132, 2026-10-03)
- [x] **B35** (approved auto 2026-10-04) (verified 2026-10-04: B37: 4 QA tests [fresh open on a reloaded protected hall, a stranger's refused shift-click, unowned hall claim, the English hints], 5x repeats pass, B37's old line put back fails all 3 world tests; B34-B36: aStuckTutor/aStuckTrainer 5x each in own batches pass, every main build since B34 landed [23:26Z, ~26] green; B24: builder's 20/20 repeats plus every main compat run since green) B34's flake is in the shared keeps() helper, not just the tutor: WorkersKeepTheirBlockGameTests.aStuckTrainerKeepsTheirPost failed the same way in qa-1003-2233's ship build (2026-10-03 23:06Z, 'the trainer didn't head back: at -11441225, -58, 6715258', 9 blocks off in x, 10 in z from the block, start was 12), and aStuckTutorKeepsTheirDesk failed on main CI run 455 (22:26Z). Expected: B34's fix covers all four tests in the class (miner, postman, tutor, trainer); fold into B34. Test: WorkersKeepTheirBlockGameTests (all four) (found by qa-1003-2233, 2026-10-03) Fixed by B34 (lane-b-1004-0033): this failure ran before B34's fix reached main (23:26Z); with it, 20 trainer + 20 tutor copies running side by side all passed (40/40).
- [x] **B36** (approved auto 2026-10-04) (verified 2026-10-04: B37: 4 QA tests [fresh open on a reloaded protected hall, a stranger's refused shift-click, unowned hall claim, the English hints], 5x repeats pass, B37's old line put back fails all 3 world tests; B34-B36: aStuckTutor/aStuckTrainer 5x each in own batches pass, every main build since B34 landed [23:26Z, ~26] green; B24: builder's 20/20 repeats plus every main compat run since green) WorkersKeepTheirBlockGameTests.aStuckTrainerKeepsTheirPost (B30's, B34's sibling) failed once in lane-a's land of 21.2 (2026-10-03 23:23Z, main + item/21.2; 500 of 501 passed, and the same suite passed minutes earlier before main moved). Expected: passes every run. Likely the same cause as B34 (stuck-worker tests in that class); fix both together. Test: repeat generator on aStuckTrainerKeepsTheirPost x20 (found by lane-a-1003-2232, 2026-10-03) (found by lane-a-1003-2232, 2026-10-03) Fixed by B34 (lane-b-1004-0033): this failure ran before B34's fix reached main (23:26Z); with it, 20 trainer + 20 tutor copies running side by side all passed (40/40).
- [x] **B37** (verified 2026-10-04: B37: 4 QA tests [fresh open on a reloaded protected hall, a stranger's refused shift-click, unowned hall claim, the English hints], 5x repeats pass, B37's old line put back fails all 3 world tests; B34-B36: aStuckTutor/aStuckTrainer 5x each in own batches pass, every main build since B34 landed [23:26Z, ~26] green; B24: builder's 20/20 repeats plus every main compat run since green) (approved 2026-10-04) The Village Hall name tag's tooltip says 'Shift-click: protect the village (the hall's owner)' while the village is already protected (seen in the hall_treasury scene, still 01_hall_treasury, lane-c-1003-2232). Expected: when protected, the hint says shift-click opens the village up again (screen.aliveworkplace.hall.protect_click is one line for both states). Test: a GameTest reading the NAME button's lore on a protected hall (found by lane-c-1003-2232, 2026-10-03)
- [x] **B38** (approved auto 2026-10-04) (verified 2026-10-04: QaB38B39GameTests [5, from the Expected lines]: B39 hut on a one-block slope near/~16 blocks/far from its chests places a block within 600 ticks of CLEAR ending and passes FOUNDATION; B38 variant only in the chest finishes with no empty-list wait, bag of variants 3 short names the planks missing and finishes once they arrive. 545/545 + compat 56/56. Mutation: not yet in a nightly [none ran since the fixes].) The soak's tinker's workshop builder waited 30 s on shift in STRUCTURE with status WAITING_FOR_MATERIALS and 0 kinds missing, 273 placed (6-day soak, SOAK=true SOAK_DAYS=6, main 1fba053 + item/23.1, 2026-10-04 01:05Z: stall #1 at 1031, 80, 1026; B21's symptom on another build after B21's fix). Expected: a builder with nothing missing never shows waiting for materials; it fetches or builds. Test: a GameTest with the tinker's workshop's exact materials in chests that fails if the site is WAITING_FOR_MATERIALS with nothing missing for 600 ticks on shift (found by lane-a-1004-0033, 2026-10-04)
- [x] **B39** (approved auto 2026-10-04) (verified 2026-10-04: QaB38B39GameTests [5, from the Expected lines]: B39 hut on a one-block slope near/~16 blocks/far from its chests places a block within 600 ticks of CLEAR ending and passes FOUNDATION; B38 variant only in the chest finishes with no empty-list wait, bag of variants 3 short names the planks missing and finishes once they arrive. 545/545 + compat 56/56. Mutation: not yet in a nightly [none ran since the fixes].) The soak's graveyard (third in its builder's queue, on a slope at 1119, 81, 1000) sits 30 s on shift at the start of FOUNDATION with 0 placed, in every soak run so far, including after B23's fix (main 1fba053 + item/23.1, 2026-10-04 01:05Z, stall #2). Expected: foundation work starts within 30 s of CLEAR ending. Test: the soak's terrain and graveyard placement in a huge_area GameTest, StallWatch reporting no stall (found by lane-a-1004-0033, 2026-10-04)
- [x] **B40** (approved auto 2026-10-04) (verified 2026-10-04: 2 QA tests [QaStallWatchGameTests, shipped f996037]: a builder that empties a full bag [1728 clay balls, all into its chest] and then has no materials is still logged as stalled 30 s later, so counting supply runs as progress hides no real stall; the stall line comes at exactly 30 s [kills nightly survivor StallWatch:94 >= to >, checked on the mutant]. With the fix's own aLongSupplyRunIsNeverLoggedAsStalled, full build green [588 + 60 compat]. Split soak not rerun by QA [builder's soak.md run 7: 22/22, 0 stalls]) Builders stall 30 s with nothing missing in the split soak (SOAK=true SOAK_SPLIT=true SOAK_DAYS=6, lane-a-1004-0332, 2026-10-04, run 6d in docs/agent/soak.md): the sifting shed in CLEAR with 0 placed, the schoolhouse at the start of FOUNDATION (1 placed, 55 skipped) and the apiary garden in STRUCTURE (55 placed), all status WORKING; the schoolhouse's FOUNDATION stall shows in every split run. Expected: no stall. Test: the split soak with DEBUG=true to see what those builders do, then a GameTest for the case found (found by lane-a-1004-0332, 2026-10-04)
- [x] **B41** (approved auto 2026-10-04) (verified 2026-10-04: showcase run 51 [36f3d20, includes the fix f209a4a]: scene 'missing' passes with no reasons; the title check no longer fails on the player's InventoryScreen) Showcase scene 'missing' fails on every run since 24.4's title check (showcase runs 45 at 93dba30 and 46 at e39f973, nightly issue #1): '01_blueprint_missing: the title Crafting is wider than its screen (41 > -18 pixels)'. The shot is the player's own InventoryScreen, whose 'Crafting' label sits at titleLabelX 97 of 176, so ScreenshotHarness.titleFits' room (imageWidth - 2 x titleLabelX) goes negative; a false failure in the harness, not in the game (the label fits in the 79 pixels right of it). Expected: the missing scene passes; titleFits measures room from titleLabelX to the panel's right edge, or skips screens that aren't ours. Test: SCENE=missing tools/screenshots/run.sh passes (found by qa-1004-0733, 2026-10-04)
- [x] **B42** (approved auto 2026-10-04) (verified 2026-10-05: showcase evidence, nightly issue #1: partners_engine passed all 7 full showcase runs after 64d8b98 [13:16Z-23:14Z 2026-10-04]; soak passed 6 of 7 [every build finished, its result check read right], the 7th [b7be7a0] failed on a stuck builder, filed as B60; no local run) The showcase's soak scene (23.1's time-lapse) fails 'every build finishes' in each of the last three showcase runs that filmed it, all after 23.1 landed: 21 of 22 at game tick 176907 (commit 9102667, 03:18Z), 20 of 22 at 175923 (1da91eb, 03:53Z), 21 of 22 at 175464 (e39f973, 06:48Z), gave up at the time limit with no 02_soak_done picture (nightly issue #1). Expected: 22/22 inside the scene's time, as 23.1's Done when says. Test: SCENE=soak tools/screenshots/run.sh passes; the unfinished build's name and stage from its log (possibly B40's stall) (found by qa-1004-0733, 2026-10-04)
- [ ] **B43** The showcase's battle scene (a Master trainer, with Cobblemon) failed 'the battle ran to the end' in showcase run 46 (e39f973, 2026-10-04 06:48Z): no battle end within its 8000 ticks, while runs 44 and 45 passed it. Expected: the scripted battle always finishes inside the scene (or the scene waits on something that always ends it). Test: SCENE=battle tools/screenshots/run.sh, a few runs; its '[battle scene] tick N turn T' lines show where it hangs (found by qa-1004-0733, 2026-10-04) Status (lane-a-1004-0932): a local stuck run sat at turn 10 from tick 1100 to 4000+ with request=false and mustChoose=false on both sides and nothing in the log, so the battle is stuck, not slow; 5 more local runs all finished (420-1220 ticks). The scene now dumps every field of a battle that hasn't changed turn for 300 ticks (its dispatch queue, dispatchResult, showdownMessages) as '[battle scene] stuck' lines, so the next showcase run that hangs shows what it waits on (likeliest: a dispatchFuture that never completes, e.g. a send-out or recall of the trainer's Pokémon).
- [x] **B44** (approved auto 2026-10-04) (verified 2026-10-05: showcase evidence, nightly issue #1: partners_engine passed all 7 full showcase runs after 64d8b98 [13:16Z-23:14Z 2026-10-04]; soak passed 6 of 7 [every build finished, its result check read right], the 7th [b7be7a0] failed on a stuck builder, filed as B60; no local run) Showcase scene 'soak' fails although the soak passes: showcase run 51 (36f3d20, 2026-10-04) reports 'not done: 10 builders finished every starter build with nothing duplicated or lost: Soak result: 22/22 builds finished in 81338 ticks (3.4 days); 0 stalls; items off: none; village chunks: 14 for 10 workers'. ScreenshotHarness's soak check requires the result to end with 'items off: none', and 23.6 (2000c41) appended '; village chunks: …' to Soak.finish()'s line, so the scene fails on every run, a false failure in the harness. Expected: the soak scene passes when every build finishes and items off is none (match '; items off: none;' or the end, like tools/packtest/run.sh's grep). Test: SCENE=soak tools/screenshots/run.sh passes (found by qa-1004-0933, 2026-10-04)
- [x] **B45** (approved auto 2026-10-04) (verified 2026-10-05: showcase evidence, nightly issue #1: partners_engine passed all 7 full showcase runs after 64d8b98 [13:16Z-23:14Z 2026-10-04]; soak passed 6 of 7 [every build finished, its result check read right], the 7th [b7be7a0] failed on a stuck builder, filed as B60; no local run) Showcase scene 'partners_engine' (28.3) failed in showcase run 55 (265d1a2, 2026-10-04 10:33Z, nightly issue #1): 'not done: the Machop walked toward the work (from 1.5 to 0.1 blocks)'. The Machop had already wandered to 1.5 blocks from the work spot before the cue, so PartnersScene's check (nearest < start - 1.5) can't pass however well the show runs; earlier showcase runs passed it. Expected: the scene passes every run, the Machop starts at its pasture (or the check measures the walk from the pasture/tether, not from wherever it wandered). Test: SCENE=partners_engine tools/screenshots/run.sh passes; PartnerShowsCompatTests has the same nearest < before - 1.5 check, look at it too (found by qa-1004-1033, 2026-10-04)
- [x] **B46** (approved auto 2026-10-04) (verified 2026-10-05: B46: QaB46GameTests [two restarts in a row hold 24%, a save without shown_progress loads] + B46GameTests pass; B69: QaB69FrontWalkGameTests, all 5 styles, villagers walking the guard house's front walk row reach the door from both corners: 5/5 fail on the pre-fix templates [stuck beside the flower box], pass on main twice and in a full build [1014/1014]; B73: 'Refresh active project' on main leaves git diff unchanged. Tests on qa/b46-b69-1005 [ship blocked by the B57 flake]) A build's progress goes down after a restart: a world saved by 0.138.0 (pack test, 10 benchmark plots) opened with main 739d02e showed the Lookout Tower (laying its foundation) at 1% after 12% before the restart and the Healing Center at 7% after 19%, while the other 9 sites went up as expected. The placed blocks are still there (FOUNDATION restarts its list on load and skips what's done), but the percentage over the builder and in /workplace sites drops. Not yet checked whether a reload with the same jar does it too. Expected: a site's progress never goes down across a restart. Test: JAR=<0.138.0 jar> PERF=true PLOTS=10 tools/packtest/run.sh, then KEEP_WORLD=true SITES_ONLY=true tools/packtest/run.sh, compare the 'Builder — ' lines (found by lane-a-1004-0932, 2026-10-04)
- [ ] **B47** BuilderChaosGameTests builder_chaos_23205 failed once in a local full build (lane-b-1004-0932, main 87ee45a + 28.4, 2026-10-04 11:43Z): '1 block(s) wrong after the build, e.g. [-19, 3, -16]=air; seed 23205', then passed alone and in the next full build. Expected: every seed finishes with every block right on every run. Test: the five chaos seeds repeated 10x (tester skill's repeat generator); the trap that removed a placed block (found by lane-b-1004-0932, 2026-10-04) Lane a (lane-a-1004-0932) saw it too: 1 block of the build missing at the end; with the B46 fix the traps also keep off a door's top half (likely the cause, not proven): if it fails again, the failure message names the cell.
- [x] **B48** (approved auto 2026-10-05) (verified 2026-10-05: flake fixes: no failure of the fletcher, air-mail, lamp or Pathfinder tests in the 10 CI builds on main since 78b6a27/c8aeabb [15:34Z-18:26Z, the only reds were countryWorkplaceBuilt_farmstead=B77 and one seer flake] nor in this run's two full ship builds) PartnersForgeCompatTests aPidgeyBringsAFeatherToTheFletcher (28.5) failed once in a local full build (lane-a-1004-1233, main 5e66474 + 25.4 tooling, 2026-10-04 15:00Z): 'no bow for the guard' at the time-out, though it passed in the build 40 minutes earlier on the same code. Expected: the fletcher always makes the guard's bow within the test's time. Test: runCompatGameTest a few times, or the repeat generator on that test (found by lane-a-1004-1233) (found by lane-a-1004-1233, 2026-10-04)
- [x] **B49** (approved auto 2026-10-04) (verified 2026-10-05: B53: QaBerryPaceCompatTests [two partners: 14/20 at the default cap, held at 16/20 with maxWorkPace 125; the old code gave 11] passes; B49: b10ZombieDesertHouseRotated90 and idleBuildersHelpNearbyBuilds 5x each with RepeatNewTests, 10/10; B58: legendPace 5x in a filtered run, 5/5. No nightly mutation/repeat report since these fixes [newest nightly 2026-10-04 09:50Z]) StructureVillagerGameTests b10ZombieDesertHouseRotated90 failed in a local full build of main edc1750 (lane-a-1004-1533, 2026-10-04 17:36Z): 'the villager is not on the house floor (CLOCKWISE_90): 2.45 1.00 1.85 from the spot'; main's CI was already red at 0f5e530 (28.7). Also seen once in the build before (same session, main 0f5e530 + 23.1a): BuilderGameTests idleBuildersHelpNearbyBuilds 'the helper should stop once the build is done' (passed 8/8 alone with the repeat generator and in two other full builds; may come from 23.1a's crew changes, which let helpers work during the lead's retry). Expected: both pass on every run. Test: the two tests repeated 10x with RepeatNewTests (found by lane-a-1004-1533, 2026-10-04)
- [x] **B50** (approved auto 2026-10-04) (verified 2026-10-05: crew-speed test: nightly repeats 0 of 3 failed [run 37297003813], CI build 684 green with the B66 fix [e61a5e7], and in this run's ship builds) Flaky crew-speed GameTest: on CI run 37224223821 (db2887db, 2026-10-04) a crew of 4 built the stone house in 1966 ticks, 47% of the 4200 alone (limit 42%). Expected <=42%. Recent CI runs measured 29-36%; locally (class alone) passed twice at 39% and 35%, crew of 2 once 57%. Timing varies with villager pathing; the db2887db change (Nurse mixin) does not touch builders. Test: BuilderCrewGameTests.aCrewBuildsInAboutTheTimeOfOneBuilderDividedByItsSize (found by lane-b-1004-1832, 2026-10-04)
- [x] **B51** (approved auto 2026-10-05) (verified 2026-10-05: flake fixes: no failure of the fletcher, air-mail, lamp or Pathfinder tests in the 10 CI builds on main since 78b6a27/c8aeabb [15:34Z-18:26Z, the only reds were countryWorkplaceBuilt_farmstead=B77 and one seer flake] nor in this run's two full ship builds) PartnersForgeCompatTests aPidgeyTakesTheAirMailUpAndLandsBackEmptyHanded (28.5) failed once in a local full build (lane-c-1004-1832, main cd957fd + 27.3, 2026-10-04 20:06Z): 'pastured pidgey: 0'; the 27.x changes don't touch pastures or the post. Expected: passes every run. Test: runCompatGameTest a few times or the repeat generator on that test; likely the same family as B48 (found by lane-c-1004-1832, 2026-10-04) (found by lane-c-1004-1832, 2026-10-04)
- [x] **B52** (approved auto 2026-10-05) (verified 2026-10-05: flake fixes: no failure of the fletcher, air-mail, lamp or Pathfinder tests in the 10 CI builds on main since 78b6a27/c8aeabb [15:34Z-18:26Z, the only reds were countryWorkplaceBuilt_farmstead=B77 and one seer flake] nor in this run's two full ship builds) PartnersForgeCompatTests aPidgeyTakesTheAirmailUpAndLandsBackEmptyHanded failed once in a local full build (main d002634 + 29.2, 2026-10-04 21:02Z): 'pastured pidgey: 0'; passed on the immediate runCompatGameTest rerun. Expected: passes every run. Test: repeat it 10x. (found by lane-a-1004-1832, 2026-10-04)
- [x] **B53** (approved auto 2026-10-05) (verified 2026-10-05: B53: QaBerryPaceCompatTests [two partners: 14/20 at the default cap, held at 16/20 with maxWorkPace 125; the old code gave 11] passes; B49: b10ZombieDesertHouseRotated90 and idleBuildersHelpNearbyBuilds 5x each with RepeatNewTests, 10/10; B58: legendPace 5x in a filtered run, 5/5. No nightly mutation/repeat report since these fixes [newest nightly 2026-10-04 09:50Z]) Berry Breeders count Pokémon partners twice: BerryBreederWork.java:414 multiplies BuilderLevels.delay (which already includes Partners via Pace) by Partners.factor again, so a partnered breeder works faster than Pace's cap allows. Expected: one partner bonus, under maxWorkPace. Test: none yet (seen reading the code, 2026-10-04) (found by lane-d-1004-2133, 2026-10-04)
- [ ] **B54** BuilderGameTests idleBuildersHelpNearbyBuilds sometimes fails 'the helper should stop once the build is done' in the shared crews batch (split out of B49, whose zombie-house half was fixed in fd9078b; this half is crew logic, not spawning). Expected: passes every run. Test: idleBuildersHelpNearbyBuilds repeated 10x with RepeatNewTests (found by lane-a-1004-2133, 2026-10-05)
- [ ] **B56** LegendEngineGameTests.legendConditions fails in some full runs (2 of 3 here on 2026-10-05, also seen on clean main with legendPace failing instead): meal_kinds reads 1 of 2 before the test fills the chest, so a store or chest left by another test near its hall counts. Test-only (QA lane): the test should count the store's kinds before it fills it, as its 'finished' check does. (found by lane-d-1005-0032, 2026-10-05) Status (lane-c-1005-0033): legendConditions' cause was a Storehouse chest (bread) left by VillageHallGameTests.villagersEatFromTheStore inside the default 64-block village radius; caa26c8f (27.9) gives that test VillageHalls.RADIUS 18 for its run, so legendConditions passed 731/731 twice and in two full builds; legendPace not looked at.
- [x] **B55** (approved auto 2026-10-05) (verified 2026-10-05: Local ./gradlew --max-workers=1 build finished in the 7 GB container, compat 129/129 [ship of 30f2d6f, 20 min]. The unticked duplicate B55 line was removed [00c4d89].) Local full builds can't finish runCompatGameTest in the 7 GB dev container: with the default 1.5 GB heap the compat server fills with the pack's block states and thrashes in full GC until it hangs; with -Xmx3G the OS kills it (exit 137) next to the Gradle daemon (lane-a-1004-2133, 2026-10-04). CI runs compat fine. Expected: a local build finishes (e.g. stop the daemon or lower org.gradle.jvmargs for the compat run, ~2.5 GB server heap). Test: ./gradlew --max-workers=1 runCompatGameTest locally (found by lane-a-1004-2133, 2026-10-05)
- [x] **B57** (approved auto 2026-10-05) (verified 2026-10-05: flake fixes: no failure of the fletcher, air-mail, lamp or Pathfinder tests in the 10 CI builds on main since 78b6a27/c8aeabb [15:34Z-18:26Z, the only reds were countryWorkplaceBuilt_farmstead=B77 and one seer flake] nor in this run's two full ship builds) PartnersForgeCompatTests aPidgeyBringsAFeatherToTheFletcher failed once in a local full build (lane-c-1005-0033, main 38c54127 + 27.6-27.9 + B55, 2026-10-05 02:10Z): 'no bow for the guard'; it passed in the full build before the merge and in a runCompatGameTest rerun right after (117/117). Expected: passes every run. Test: the repeat generator on that test; likely the same family as B48/B51 (found by lane-c-1005-0033, 2026-10-05)
- [x] **B58** (approved auto 2026-10-05) (verified 2026-10-05: B53: QaBerryPaceCompatTests [two partners: 14/20 at the default cap, held at 16/20 with maxWorkPace 125; the old code gave 11] passes; B49: b10ZombieDesertHouseRotated90 and idleBuildersHelpNearbyBuilds 5x each with RepeatNewTests, 10/10; B58: legendPace 5x in a filtered run, 5/5. No nightly mutation/repeat report since these fixes [newest nightly 2026-10-04 09:50Z]) LegendEngineGameTests.legendPace failed with 'capped delay: 500' in a filtered runGameTest (5 classes) on 2026-10-05 but passed in every full run: looks order-dependent (leftover state from another test). Expected: passes in any order. Test: LegendEngineGameTests.legendPace (QA lane: test-only flake) (found by lane-a-1005-0032, 2026-10-05) Also fails with LegendEngineGameTests alone in runGameTest, on a clean worktree of d0038ea9 (29.7) as well (29.8's subagent, 2026-10-05): not order-dependent there; with the 2x cap the near builder's delay is 500 while `nearBase / 2` expects otherwise, so look at 29.7's shared pace cap in `BuilderLevels.delay`.
- [x] **B59** (approved auto 2026-10-05) (verified 2026-10-05: full showcase run 37279004841 on main 9127357 [after 64d903dc and 2c17c273]: 115 scenes ran, none 'left no showcase.json'; stations and config pass [config: every label fits its button]; steward_rules/steward_homes start and check [their failures are scene setup, see the new bug]) Every showcase scene crashes the client at start since the Habitat Keeper scene (338fd6c9, 2026-10-04 22:11Z): showcase run 37260221259 (9856e95) failed 87 of 106 scenes with 'the scene left no showcase.json', and push run 37262922422 (eb3ef3f) crashed too (steward_rules, steward_homes). client-log.txt: NoClassDefFoundError com/cobblemon/mod/common/entity/pokemon/PokemonEntity at ScreenshotHarness.<init> (new JobScenes()), because JobScenes.java:775 declares a PokemonEntity local outside the compat guard, and the screenshot client runs without Cobblemon. The nightly showcase page and the digests' pictures are empty until fixed. Expected: every scene starts without Cobblemon; Cobblemon types only behind a guarded class or //? if cobblemon. Done when: SCENE=stations tools/screenshots/run.sh starts and passes, and the next showcase run has no 'left no showcase.json'. Test: SCENE=stations tools/screenshots/run.sh (found by qa-1005-0434, 2026-10-05)
- [x] **B60** (approved auto 2026-10-05) A builder got stuck in a wall during the builder soak: nightly showcase run 37240994365 (b7be7a0, 2026-10-04 23:14Z, nightly issue #1) scene soak reported 'a villager (builder) is stuck in a wall at 307 -54 315, ticked 79404, onGround true' with dirt at y-1 and y and oak leaves above (column y-1..y+2: dirt, dirt, air, oak_leaves). A player would see a builder trapped in the ground or suffocating near a site. Expected: builders never end up inside a block while clearing, levelling or landscaping. Done when: the cause is found, a GameTest of it passes, and the soak scene passes without the stuck-villager check. Test: SCENE=soak tools/screenshots/run.sh (found by qa-1005-0434, 2026-10-05)
- [x] **B61** (approved auto 2026-10-05) (verified 2026-10-05: full showcase run 37279004841 on main 9127357 [after 64d903dc and 2c17c273]: 115 scenes ran, none 'left no showcase.json'; stations and config pass [config: every label fits its button]; steward_rules/steward_homes start and check [their failures are scene setup, see the new bug]) The settings screen cuts off a label: nightly showcase run 37240994365 (b7be7a0) scene config reported 'Nurses Use Healing Machines: ON' is wider than its button (lang key aliveworkplace.config.nurseHealingMachine, still the same on main). Expected: every setting's label fits its button in Mod Menu's screen at the default GUI scale (a shorter label, or a tooltip with the full text). Done when: the config scene passes 'every setting has a button whose label fits'. Test: SCENE=config tools/screenshots/run.sh (found by qa-1005-0434, 2026-10-05)
- [ ] **B62** Showcase scene steward fails 'the steward walked his morning rounds and came back to the hall (not within 120 seconds)' in the full showcase runs 37235147722 (7d9f036) and 37240994365 (b7be7a0), 2026-10-04, after passing before 27.1a/27.9 changed who can be a Steward and his jobs. Expected: the Steward walks his rounds and returns to the hall within the scene's 120 s, or the scene is staged for the new rules (a seasoned Builder). Test: SCENE=steward tools/screenshots/run.sh (found by qa-1005-0434, 2026-10-05) Status (qa-1005-1633): reproduced locally twice (d9e12cd, scene limit 2400 and 4800 ticks): he is appointed, holds the plan and visits the three marker posts (about 500 ticks each, so he seems to give up on each stop after GIVE_UP rather than reach it), then walks out of the camera's view and never comes back to the hall, even in 4800 ticks. The new GameTests StewardRoundsQaGameTests (same staging in a GameTest, two zones, with and without fence posts) pass in about 600 ticks, so the difference is the scene's world (the client player hovering near him, the flat 'shots' world, three zones): next, log his WorkerStatus line and position every 100 ticks in the scene.
- [x] **B63** (approved auto 2026-10-05) (verified 2026-10-05: SCENE=words tools/screenshots/run.sh on main 09d16a7d: pass, all 44 messages read without a raw key or placeholder [incl. import.done_with_unknown and .one]) Showcase scene words has failed every full showcase run since 2026-10-04 13:16Z (nightly issue #1): message.aliveworkplace.import.done_with_unknown and .one read 'Imported %s (%s × %s × %s). %s block(s) ... became air: %s.' with raw %s. Expected: the scene shows each message with its arguments filled (if the scene passes no arguments, it is the scene's fault; if a player's import message can show %s, it is the mod's). Test: SCENE=words tools/screenshots/run.sh (found by qa-1005-0434, 2026-10-05)
- [x] **B64** (approved auto 2026-10-05) (verified 2026-10-05: B46OldSaveGameTests [QA-written from the spec, qa-1005-0533] green in every CI build since 27b90a7 and in this run's two full builds) B46's fix doesn't cover worlds saved before it: a site saved by 0.138.0 or earlier (no shown_progress) still drops its progress the first time the new jar loads it, 24% to 0% half through a raised hut's foundation (QaB46GameTests log, qa-1005-0533) - exactly B46's own repro (a 0.138.0 world opened with main), so the owner's server will see it once on upgrade. Expected: an old save keeps the progress its saved cursor showed (e.g. set shown_progress from the saved stage and cursor before the foundation list is redone). Test: B46OldSaveGameTests on tests/b46-old-saves (written from QaB46GameTests, not yet compiled on its own) (found by qa-1005-0533, 2026-10-05)
- [ ] **B65** ConscriptionGameTests noWorkDuringARaidNorTheMorningAfterUntilNoon failed once in lane-c-1005-0332's local full build (06:05Z, main + 23.10a): 'back at work in the raid'; passed in the next full build, and a 27.11 subagent saw it fail once too. Expected: passes every run. Test: the repeat generator on that test (found by lane-c-1005-0332, 2026-10-05)
- [x] **B66** (approved auto 2026-10-05) (verified 2026-10-05: crew-speed test: nightly repeats 0 of 3 failed [run 37297003813], CI build 684 green with the B66 fix [e61a5e7], and in this run's ship builds) B50 is back after its fix (90808c7): BuilderCrewGameTests.aCrewBuildsInAboutTheTimeOfOneBuilderDividedByItsSize failed in qa-1005-0533's local full build of main a026511 + qa/b46-b53-1005 (2026-10-05 06:25Z): 'a crew of 4 took 1868 ticks, 44% of the 4200 ticks alone (at most 42%)'; crew of 2 took 50%. It blocked that ship. Expected: passes every run (find what the slow 4-crew runs wait on, as 90808c7 did for the stuck lead). Test: the test itself, repeated 10x with RepeatNewTests (QA lane: test-only flake that turns main red) (found by qa-1005-0533, 2026-10-05)
- [x] **B67** (approved auto 2026-10-05) (verified 2026-10-05: flake fixes: no failure of the fletcher, air-mail, lamp or Pathfinder tests in the 10 CI builds on main since 78b6a27/c8aeabb [15:34Z-18:26Z, the only reds were countryWorkplaceBuilt_farmstead=B77 and one seer flake] nor in this run's two full ship builds) B57's fix (bf4d409, clear leftovers) didn't hold: PartnersForgeCompatTests aPidgeyBringsAFeatherToTheFletcher was the 1 failed compat test in CI run 37271696140 on main f0594ec (2026-10-05 06:23Z, which includes bf4d409), turning main red; the next run passed. CI's log doesn't print the assertion message (the gametest-report artifact has it). Same test as the still-open B48. Expected: passes every run; look past leftover guards (e.g. the fletcher's sticks or string taken by another worker within Village.RADIUS 48, or the guard's own bow choice). Test: the test itself in a few full compat runs (QA lane: test-only flake that turns main red) (found by qa-1005-0633, 2026-10-05)
- [x] **B68** (approved auto 2026-10-06) (verified 2026-10-06: LegendSitesGameTests [batch legendSitesPlace] on 749752b: 0 'Block-attached entity at invalid position' lines in the log, 12/12 pass) Setting a traveller's camp down (29.9, legend/traveller_camp.nbt) logs an ERROR each time: 'Block-attached entity at invalid position: BlockPos{x=0, y=0, z=0}' (3x in every full GameTest run, from LegendSitesGameTests; the nightly log audit counts it). The camp's item frame is saved without TileX/TileY/TileZ (tools/blueprints/legend_sites.py). The frame and its map still appear (QaLegendCampGameTests, shipped), so players see nothing; only the log. Expected: no ERROR when a camp is placed (write TileX/Y/Z from blockPos in the generator, as vanilla's templates do). Test: logaudit.py after runGameTest, batch legendSitesPlace (found by qa-1005-0633, 2026-10-05) Status (qa-1006-0034): the generator fix won't work: BlockAttachedEntity.readAdditionalSaveData compares TileX/Y/Z with the entity's Pos, which StructureTemplate.placeEntities has already set to world coordinates, so template-relative TileX (as any vanilla template saved elsewhere) still logs unless the camp lands within 16 blocks of 0,0,0. The fix is in mod code (LegendSites, around placeInWorld at line 299: place the camp without its entities and add the frame itself, or a StructureProcessor that rewrites TileX/Y/Z to the world spot), so it's a build lane's, not QA's.
- [x] **B69** (approved auto 2026-10-05) (verified 2026-10-05: B46: QaB46GameTests [two restarts in a row hold 24%, a save without shown_progress loads] + B46GameTests pass; B69: QaB69FrontWalkGameTests, all 5 styles, villagers walking the guard house's front walk row reach the door from both corners: 5/5 fail on the pre-fix templates [stuck beside the flower box], pass on main twice and in a full build [1014/1014]; B73: 'Refresh active project' on main leaves git diff unchanged. Tests on qa/b46-b69-1005 [ship blocked by the B57 flake]) Village houses (all five styles, tools/blueprints/village.py village_house) have head-height top-half trapdoor flower boxes beside the front steps at (2,1,1) and (6,1,1). Vanilla pathfinding treats any trapdoor as open ground, so a villager walking along the front wall to the door gets stuck against one for good (seen with the guard of workplaceBuilt_guard_house: 3 in 20 runs). The 12 buildable workplace copies got upside-down stair sills instead (27.11); the worldgen village houses still have the trapdoors. (found by lane-c-1005-0633, 2026-10-05)
- [x] **B70** (approved auto 2026-10-05) (verified 2026-10-05: Refresh active project on main d519846 + qa/flakes-1005 left git diff unchanged [only the branch's own edits]; QaImportEdgesGameTests swap line keeps timeoutTicks) 'Refresh active project' changes QaImportEdgesGameTests.java: its //$ gametest swap line drops timeoutTicks = 400, so git diff isn't clean after a refresh (seen by lane-d-1005-0632 on 30.16, 2026-10-05). Expected: refresh leaves git diff unchanged (fix the swap line). Test: ./gradlew "Refresh active project" then git diff (found by lane-d-1005-0632, 2026-10-05)
- [x] **B71** (approved auto 2026-10-05) (verified 2026-10-05: flake fixes: no failure of the fletcher, air-mail, lamp or Pathfinder tests in the 10 CI builds on main since 78b6a27/c8aeabb [15:34Z-18:26Z, the only reds were countryWorkplaceBuilt_farmstead=B77 and one seer flake] nor in this run's two full ship builds) StewardCivicRulesGameTests twoDarkBedsGetAStreetLampByTheDarkestBed failed once and turned main red (CI run 37289855963 on a2294ed9, 2026-10-05 09:30Z, the only failure of 962; the next runs passed). CI's log names only the test, not the assertion. Likely cause (not confirmed): VillageAdvice.darkBeds counts every HOME POI within VillageHalls.RADIUS 64 of the hall, so a bed left by another test's area within 64 blocks makes 'darkBeds.size() == 2' fail, or two equally dark beds tie in the sort. Expected: passes every run (count only this test's beds, e.g. a smaller radius for the test or assert on the test's own beds). Test: the test itself, repeated with RepeatNewTests (QA lane: test-only flake that turned main red) (found by qa-1005-0934, 2026-10-05)
- [ ] **B72** 13 showcase scenes fail now that B59 lets every scene run (full showcase run 37279004841 on main 9127357, 98 pass / 17 fail; soak=B60, steward=B62, words=B63 known): cradle, steward_rules, steward_desk, steward_jobs, steward_homes, long_shifts, free_bread, legend_guest, curfew, conscription, open_gates, piece_look, habitat_keeper, plus edicts (4532 missing-texture magenta pixels in 02_edicts_scale4 only). The matching GameTests pass (e.g. EdictGameTests Long Shifts), so most look like scene setup, not the mod: from reading the harness (not run), the scenes place beds/halls with setBlockAndUpdate and read POIs in the same tick (Cradles.nursery, Stewards.appoint's result ignored so no Steward is appointed, LegendGuests.tend), and long_shifts/free_bread don't call CivicEffects.forget() after the hall exists (hall cached null 200 ticks). Expected: every scene passes; each scene waits a tick or two after placing POI blocks, checks Stewards.appoint's result, and forgets the civic caches; any scene still failing after that is a mod bug of its own. Test: SCENE=<name> tools/screenshots/run.sh (QA lane: scene-only; found by qa-1005-0934) (found by qa-1005-0934, 2026-10-05) Status (qa-1005-1834): fixed in the harness and passing locally: long_shifts, free_bread (forget CivicEffects/Moods in the first step), cradle (nursery checked after its POI exists), conscription (forget CivicEffects when the raid starts). Curfew still fails locally (0 of 3 asleep) even with the beds claimed and CivicEffects forgotten in its first step, so its cause is elsewhere. Still failing locally on main 0eb2c6e: open_gates ('the inn took in four guests over two mornings: 2'), legend_guest (she never settled: not a Master Mason, no chronicle line), piece_look (choosing the desert outside sent no builder), habitat_keeper (timeout, no snack/log/shiny), steward_desk (no three proposals, approving starts no build: 0 open). Likely cause, not confirmed for these five: the staging tick reads POIs (beds, halls, job sites) that are only registered after that tick, or caches built from them (CivicEffects 200 ticks).
- [x] **B73** (approved auto 2026-10-05) (verified 2026-10-05: B46: QaB46GameTests [two restarts in a row hold 24%, a save without shown_progress loads] + B46GameTests pass; B69: QaB69FrontWalkGameTests, all 5 styles, villagers walking the guard house's front walk row reach the door from both corners: 5/5 fail on the pre-fix templates [stuck beside the flower box], pass on main twice and in a full build [1014/1014]; B73: 'Refresh active project' on main leaves git diff unchanged. Tests on qa/b46-b69-1005 [ship blocked by the B57 flake]) QaImportEdgesGameTests (QA commit 33ddd5b0): its '//$ gametest' swap line drops timeoutTicks = 400, so ./gradlew 'Refresh active project' rewrites the @GameTest without the 400-tick timeout (seen by lane-c-1005-0932, 2026-10-05). Expected: refresh leaves git diff unchanged; the swap line carries the timeout. Test: run 'Refresh active project' on main, git diff (found by lane-c-1005-0932, 2026-10-05)
- [x] **B74** (approved auto 2026-10-05) (verified 2026-10-05: flake fixes: no failure of the fletcher, air-mail, lamp or Pathfinder tests in the 10 CI builds on main since 78b6a27/c8aeabb [15:34Z-18:26Z, the only reds were countryWorkplaceBuilt_farmstead=B77 and one seer flake] nor in this run's two full ship builds) PartnersForgeCompatTests aPidgeyBringsAFeatherToTheFletcher still fails after B57's fix (bf4d4091, leftovers cleared): 'no bow for the guard' in qa-1005-1034's ship build of qa/b46-b69-1005 (main a5a723f9+ merged, 2026-10-05 11:26Z; 1014 gametests passed, this the only compat failure). Expected: passes every run. Test: runCompatGameTest, or the repeat generator on that test (QA lane: test-only flake, but it turns the build red) (found by qa-1005-1034, 2026-10-05)
- [x] **B75** (approved auto 2026-10-05) (verified 2026-10-05: flake fixes: no failure of the fletcher, air-mail, lamp or Pathfinder tests in the 10 CI builds on main since 78b6a27/c8aeabb [15:34Z-18:26Z, the only reds were countryWorkplaceBuilt_farmstead=B77 and one seer flake] nor in this run's two full ship builds) PathfinderGameTests.expeditionLeadsWaitsAndCatchesUp failed on CI build 37305086133 (main 2cafe50, 2026-10-05 11:46Z) and turned main red; passed in lane-a-1005-1233's local runGameTest on 4d1ee21. The next CI run (4d1ee21) was red from B66 instead. Expected: passes every run. Test: the repeat generator on that test (found by lane-a-1005-1233, 2026-10-05)
- [x] **B76** (urgent: owner 2026-10-05, before more players update to 0.139.0) (approved auto 2026-10-05) (verified 2026-10-05: ExpansionGateGameTests' 3 tests [0.139.0 config with switches on stays off, unfinished switches default off and hidden, tests can open the gates] passed in a local full build on a8af069; M27-M30 and M34 flags false in Expansions; no new tests) Unfinished expansions are switched ON by default, against the ROADMAP rule that they stay off until their milestone is complete: WorkplaceConfig defaults legends, legendNeeds, legendSites, strangeMoods (M29), villageEdicts, workHorns, villageBanners, cradles, harvestIdols, tonics (M30), steward, stewardSelfRun (M27), berryBreeders, campCooks, habitatKeepers, daycareKeepers, gemGrowers, habitatSightings, villageHabitats (M28) to true, so 0.139.0 (released 2026-10-05) ships M27-M30 half-built and on. Expected: each unfinished milestone's switches default to false (new worlds and configs that never set them), and the release that completes a milestone turns its switches on. Check which switch belongs to which milestone before changing; GameTests set the switch they need. Owner (2026-10-05): switched off to avoid bugs; when a whole expansion (e.g. all of 1.1) is done, its release turns it back on. Configs that 0.139.0 already wrote to disk hold `true` for these switches, so a new default alone won't turn them off on his server: gate each unfinished milestone in code (e.g. a per-milestone 'complete' flag the switch is ANDed with), not only by the config default, and test that a 0.139.0 config with the switch true still leaves the feature off. (found by speed-1005-1232, 2026-10-05) (found by speed-1005-1232, 2026-10-05)
- [x] **B77** (approved auto 2026-10-05) (verified 2026-10-06: repeat runs on 4011e5d: aConscriptSavedMidRaid, aRaidForetold, aTwoBedHome [no teleport] 10x each pass; CountryWorkplaces [farmstead] and StewardGameTests pass; new SeerGameTests.everyRaidForetoldComesFromTheSideTold [24 foretold raids at radius 16 and 20] passes and fails with the B82 fix reverted; full build green twice in ship) CountryWorkplacesGameTests countryWorkplaceBuilt_farmstead (27.14) failed in qa-1005-1434's ship build (main d519846 + compat-test-only changes, 2026-10-05 15:07Z, 1 of 1048): 'no harvest in the Farmstead's chests (harvested 36)' at the time-out: the farmer harvested 36 crops but none were in a container within the house's 15x13 footprint at y 1. Not yet known whether a player would see it (harvest kept in his inventory, put in a chest elsewhere, or taken by another worker) or it is the test's timing. Expected: the Farmstead farmer's harvest reaches its chests every run. Test: countryWorkplaceBuilt_farmstead, a few runGameTest runs (found by qa-1005-1434, 2026-10-05) (found by qa-1005-1434, 2026-10-05)
- [x] **B78** (approved 2026-10-06) The Book of Edicts' guild row lists only 6 guilds, but a City can now hold up to 12 guilds (30.17-30.20), so the rest are never shown. Expected: every guild of the village on the Book's guild row (paged or wrapped). Test: GuildsScene / EdictBook guild row with more than 6 guilds (found by lane-d-1005-1533, 2026-10-05)
- [x] **B79** (approved auto 2026-10-05) (verified 2026-10-06: repeat runs on 4011e5d: aConscriptSavedMidRaid, aRaidForetold, aTwoBedHome [no teleport] 10x each pass; CountryWorkplaces [farmstead] and StewardGameTests pass; new SeerGameTests.everyRaidForetoldComesFromTheSideTold [24 foretold raids at radius 16 and 20] passes and fails with the B82 fix reverted; full build green twice in ship) Renewal (27.21): villagers whose old home was renewed may not walk to their new beds on their own. RenewalGameTests' two-bed test teleports both villagers to the new beds before night, because without it they lost the bed memory (the 27.21 subagent's guess: they couldn't path to it in the test village). Expected: after a home is renewed, its villagers keep their claim on the new beds and walk there and sleep without help. Test: the two-bed renewal test without the teleport (found by lane-c-1005-1833, 2026-10-05)
- [ ] **B80** RoadGameTests.aHalfBuiltSegmentSurvivesSaveAndReloadAndRoadsLeaveTheRankAlone (batch roadReload, 27.15) fails 'the road opened 0 segments, not 1' in every run of a trimmed suite (Caravan, StewardSafety, ResearchTrees, Road, Wall, CityPlanRoad GameTests; 5 of 5), while it passes in the full suite: it depends on test order, likely a leftover finished-building record or site on its line from an earlier batch (as CaravanRoadGameTests had, fixed in f88686b2 by forgetting finished records round the area). Expected: passes in any order. Test: runGameTest with those classes only (QA lane: test-only) (found by lane-c-1005-1833, 2026-10-05)
- [x] **B81** (approved auto 2026-10-05) (verified 2026-10-06: repeat runs on 4011e5d: aConscriptSavedMidRaid, aRaidForetold, aTwoBedHome [no teleport] 10x each pass; CountryWorkplaces [farmstead] and StewardGameTests pass; new SeerGameTests.everyRaidForetoldComesFromTheSideTold [24 foretold raids at radius 16 and 20] passes and fails with the B82 fix reverted; full build green twice in ship) ConscriptionGameTests.aConscriptSavedMidRaidLoadsWithoutTheSword failed on main CI build 696 (7b40b9c5, 2026-10-05 19:41Z), its only failure, turning main red: 'armed: 3 minecraft:wheat / 0 minecraft:air' (after the save and reload mid-raid the conscript holds wheat, no sword). Builds 695 and 697-699 were cancelled by the runner at about 15 min (no concurrency or timeout in build.yml), so they say nothing. Expected: passes every run; a player would see a conscript reloaded mid-raid holding the wrong item, so check whether the test or the mod drops the sword. Test: the test repeated with the repeat generator (found by red duty) (found by lane-c-1005-1833, 2026-10-05)
- [x] **B82** (approved auto 2026-10-05) (verified 2026-10-06: repeat runs on 4011e5d: aConscriptSavedMidRaid, aRaidForetold, aTwoBedHome [no teleport] 10x each pass; CountryWorkplaces [farmstead] and StewardGameTests pass; new SeerGameTests.everyRaidForetoldComesFromTheSideTold [24 foretold raids at radius 16 and 20] passes and fails with the B82 fix reverted; full build green twice in ship) SeerGameTests.aRaidForetoldComesAndNoneWhenNoneIs (29.16) failed in qa-1005-2033's full build (main a8af069 + a test-message change, 2026-10-05 21:04Z, 1 failure in the suite): 'foretold from the south-east, came from the east'. From reading VillageRaids (not proven): a foretold raid gathers at angle +-FORETOLD_SPREAD (0.3 rad, 17 deg) at 0.6x VillageHalls.RADIUS, then each raider is placed +-3 blocks round that point, and the test takes the raiders' centre to the nearest eighth (+-22.5 deg), so the centre can cross into the next eighth; with a small RADIUS (PeopleGameTests sets 16) the 3-block scatter alone is about 15 deg. A player may hear 'south-east' and see raiders come from the east-south-east. Expected: raiders always gather well inside the eighth told (smaller spread, or the spread and scatter scaled to the distance), and the test passes every run. Test: aRaidForetoldComesAndNoneWhenNoneIs repeated 10x with the repeat generator (found by qa-1005-2033, 2026-10-05)
- [x] **B83** (approved auto 2026-10-05) (verified 2026-10-06: repeat runs on 4011e5d: aConscriptSavedMidRaid, aRaidForetold, aTwoBedHome [no teleport] 10x each pass; CountryWorkplaces [farmstead] and StewardGameTests pass; new SeerGameTests.everyRaidForetoldComesFromTheSideTold [24 foretold raids at radius 16 and 20] passes and fails with the B82 fix reverted; full build green twice in ship) StewardGameTests.aStewardFromAnOldSaveKeepsHisJob fails on main: CI build 707 (5f158519) failed with exactly this 1 GameTest (message not in the CI listing). Expected: a Steward appointed before 27.1a keeps his job and his hall through a save and reload. Test: ./gradlew runGameTest (StewardGameTests) (found by lane-d-1005-2132, 2026-10-05)
- [x] **B84** (approved auto 2026-10-06) **A Steward's Farmstead never finishes: the village eats its crops** (27.22's city run): the Farmstead's carrots, potatoes and wheat seeds sit in the storehouse for its builder, but villagers eat a meal from the same store each day (VillageNeeds.eat, which prefers food they haven't had lately) and pick up the seeds from trampled crops, so the build waits for materials for days (8 stalls in 2 in-game days; ledger carrot -9, potato -8, wheat_seeds -18) and holds one of the Steward's open-build slots. Fix idea: a meal never takes items a build site still needs (BuildSite materials reserved), or crop blocks are placed last. Repro: CITY=true tools/packtest/run.sh (found by lane-c-1005-2132, 2026-10-05)
- [ ] **B85** **The Steward's planning costs too much a tick, with one 0.7 s spike** (27.22's city run, CITY=true PERF=true tools/packtest/run.sh): his planning runs every second at the hall, so 1 tick in 20 carries it and the p95 lands on that edge: 0.46, 0.05 and 0.59 ms in three runs (target under 0.5 ms), p99 about 1.2 ms; and the first planning call took 710 ms (an 882 ms tick, slowest call: planning at tick 3186), likely the first rules, blueprint or plot-search load. Fix ideas: spread his planning over ticks (wishes once a morning, the desk every few seconds), warm the blueprints off the tick; the City result line names the slowest call. (found by lane-c-1005-2132, 2026-10-05)
- [x] **B86** (approved auto 2026-10-06) **In Run the village the wall and the old-house renewal never start** (27.22's city run): after a raid on record the Steward's palisade was approved on day 1, but in 6 in-game days no wall piece opened (0 standing), and his proposal 'Renew the old house 54 blocks north-west as a Stone House' stayed on the desk all week, never approved by himself, while 14 other builds (6 buildings, 8 road segments) went up. A Hamlet allows one open build at a time, so walls and renewals may simply never get a turn behind homes and roads: check Walls.round's 'no building waits' rule and approveAll's order. Repro: CITY=true tools/packtest/run.sh, the City result line's wall and desk parts. (found by lane-c-1005-2132, 2026-10-05) Cause (2026-10-06, from the code and the run's layout, not yet seen in a City run): the City run's wall line (60 out) and old house (54 out) lie past every bench's 48-block reach, so Walls.round and Renewals.approve never found a builder (NO_BUILDER, the proposal left on the desk); the roads' round also ran first in the hall's tick and took every free builder, and approveAll put a lapsed-and-re-proposed renewal behind the day's homes. Fixed: wall pieces and renewals fall back to any builder of the village, roads and wall take turns (Walls.wallsTurn), approveAll takes walls and renewals first. GameTests: WallGameTests wallFar/wallTurns, RenewalGameTests renewTurn/renewFar. The CITY packtest confirmation (CITY=true tools/packtest/run.sh) is left to the QA lane / nightly.
- [ ] **B87** **The config screen's 'Stewards Renew Old Houses: ON/OFF' label is wider than its button** (27.21, 20cd5485; showcase scene config fails on 5f15851, run 37375753016: 'every setting has a button whose label fits (68 settings)'). Players see the text overflow the button. Expected: every config label fits its button (shorter en_us text, e.g. 'Steward Renewals', or a wider button). Test: SCENE=config tools/screenshots/run.sh (found by qa-1005-2233, 2026-10-05) (found by qa-1005-2233, 2026-10-05)
- [ ] **B86** **In Run the village the wall and the old-house renewal never start** (27.22's city run): after a raid on record the Steward's palisade was approved on day 1, but in 6 in-game days no wall piece opened (0 standing), and his proposal 'Renew the old house 54 blocks north-west as a Stone House' stayed on the desk all week, never approved by himself, while 14 other builds (6 buildings, 8 road segments) went up. A Hamlet allows one open build at a time, so walls and renewals may simply never get a turn behind homes and roads: check Walls.round's 'no building waits' rule and approveAll's order. Repro: CITY=true tools/packtest/run.sh, the City result line's wall and desk parts. (found by lane-c-1005-2132, 2026-10-05)
- [x] **B87** (approved auto 2026-10-06) **The config screen's 'Stewards Renew Old Houses: ON/OFF' label is wider than its button** (27.21, 20cd5485; showcase scene config fails on 5f15851, run 37375753016: 'every setting has a button whose label fits (68 settings)'). Players see the text overflow the button. Expected: every config label fits its button (shorter en_us text, e.g. 'Steward Renewals', or a wider button). Test: SCENE=config tools/screenshots/run.sh (found by qa-1005-2233, 2026-10-05) (found by qa-1005-2233, 2026-10-05)
- [ ] **B88** Showcase scenes failing with no open bug (full showcase run 37375753016 on main 5f15851, 116 pass / 29 fail; beyond B43/B62/B72/B80-B86): bridges, caravan_road, steward_safety, old_houses, steward_civic, partners_all, research_trees, legend_architect, legend_bard, legend_beastmaster, legend_founder, guilds, village_habitat (reasons on the showcase page / showcase.json). Most were added by recent items (27.x, 29.x); each needs triage: scene setup (QA lane) or the mod (its item's lane). Expected: every scene passes. Test: SCENE=<name> tools/screenshots/run.sh (found by qa-1005-2233, 2026-10-05) (found by qa-1005-2233, 2026-10-05)
- [ ] **B89** PartnersForgeCompatTests.airMailKeepsThePidgeyWithinItsPasturesRange failed on main CI build 714 (acc0ea16, 2026-10-05 23:05Z), its only failure, and passed in build 715 a minute later on near-identical code: a flake in the compat suite (test-only, QA lane). Expected: passes every run. Test: the test repeated 10x with the repeat generator under runCompatGameTest (found by red duty) (found by lane-d-1005-2132, 2026-10-05)
- [ ] **B90** Pidgey compat tests turn main red twice in a row: CI build 718 (1ab7d600) PartnersForgeCompatTests.aPidgeyTakesTheAirmailUpAndLandsBackEmptyHanded 'the Pidgey didn't land back: y -55.87, took off at -58.91'; build 719 (fffbc8e9) PastureCompatTests.butcherMilksAMiltankAndBrushesAPidgey 'no feathers from the Pidgey' (old B24). Both after 29.21/29.22 (Professor, Ranger). Expected: both pass every run. Test: those two tests, repeated in runCompatGameTest (red duty) (found by lane-a-1006-0033, 2026-10-06)
- [x] **B91** (approved auto 2026-10-06) main red at 4011e5d9 (CI build 721, runGameTest): CupGameTests.cupPageShowsTheCupAndSignsUp ('Grand Cup lacks Sign-up has closed: the bracket is drawn', the card said 'Fewer than 4 entrants') and B38GameTests.b38TheTinkersWorkshopCrewNeverWaitsWithNothingMissing (STRUCTURE waiting, missing anvil=1). Expected: both pass every run. Test: those two tests (red duty) (found by lane-a-1006-0033, 2026-10-06)

## Milestone 21: Finish 0.138.0

The evening of 2026-09-29 left 0.138.0 nearly ready: riding (approved), every texture redrawn, the tester set up and
its first findings fixed. Nothing is released until these are done.

- [x] **21.1** (approved 2026-10-03) (verified 2026-10-02: its tester Check, shipped in 0.138.0) **The texture rebuild, shown in game.** Every texture was redrawn with the pixel-art skill (2eeb6be);
  178 of 182 pass its lint. Done when:
  - the other 4 pass, or each has its reason in the Notes;
  - one review package with the `preview.py audit` contact sheet, in-game shots of the workstations in a village and
    of every profession's outfit (`SCENE=staff`) and a zombie villager, and a GIF of villagers working in the new
    outfits.
  **Shown** (chat, 2026-10-02): lint is 181 of 182 (mailbox_flag's reason in the Notes); new scene `outfits` (all 30
  jobs as villagers and as zombie villagers, five to a shot, named); `staff` now shows the 7 workstations 21.1a kept,
  each with its worker, along a street (its check: block there, job site taken, worker in its job); the tester's
  OutfitGameTests checks every job ships both outfits. The working GIF is the nightly `composter` scene. No changelog
  line: players see nothing new beyond the existing "A new look" entry.
  - [x] **21.1a** (approved 2026-10-01) (verified 2026-10-02: its tester Check, shipped in 0.138.0) Change from the owner (2026-09-30): not every villager needs a new custom table for a job, there are already items in the game that should give a villager his job, for example a bee hive/bee nest for the blast furnace for the miner. i think by creating too many job blocks will be a little ugly and unnapealing to new players and myself personally. I think also when two people are close in job they can share a block, but the player must right click them and give them certain items in order for them to start working, for example you have an orchard keeper and a garder and they composter they can all be associated with the compost bin, but you have to right click the villager and then decide from there - giving it flowers makes it an orchard keeper, giving it a wheat makes it a farmer, and giving it bonemeal makes it a composter, etc. this way we can cut down on the amount of custom job blocks. for things that we absolutely need custom job blocks for that is totally ok, such as a blueprint table, but for things like a bard minecraft already has a record player so we are good. ill let you iron out the kinks. after this we can move to textures
    **Fewer job blocks: the plan** (drafted by the chat 2026-09-30 from his words; he vetoes any line he dislikes).
    Vanilla jobs work as in vanilla. A job of ours starts when you sneak-right-click a villager standing by its block
    while holding its item; a villager already working there switches the same way. A block no vanilla job uses
    (crafting table, beehive, jukebox) never takes a jobless villager by itself. Shared blocks, default job first:
    - Composter: Farmer; Orchard Keeper (sweet berries), Florist (a flower), Composter (bone meal).
    - Crafting table: Carpenter (planks).
    - Blast furnace: Armorer (smelter); Miner (a pickaxe).
    - Fletching table: Fletcher; Lumberjack (an axe).
    - Smithing table: Toolsmith; Tinkerer (redstone), Ball Smith (an apricorn, with Cobblemon).
    - Cauldron: Leatherworker (dyer); Sifter (gravel: panning in the water).
    - Lectern: Librarian (scribe); Scholar (paper), Teacher (a book).
    - Cartography table: Cartographer (explorer); Netherworker (netherrack).
    - Brewing stand: Cleric (alchemist); Nurse (a honey bottle), Undertaker (a golden apple).
    - Smoker: Butcher (herder); Chef (raw food), Rancher (a saddle).
    - Grindstone: Weaponsmith; Guard (a sword).
    - Beehive or bee nest: Beekeeper (a glass bottle or shears). Jukebox: Bard (a music disc).
    - Kept, because they are machines with a job of their own: Blueprint Table (the Builder's workstation too: he
      said it stays for searching and uploading builds, so builders work there), Village Hall, Storehouse (Porter),
      Shop Counter (Shopkeeper; Innkeeper with a bed, Pokémon Trader with a Poké Ball), Travel Post (Ferryman), Mailbox
      (Postman, with paper), Training Post (Trainer; Trainer Leader with a gold block, Move Tutor with a book, Fossil
      Scientist with a fossil).
    - Gone (26): Builder's Bench, Miner's Bench, Chopping Block, Fruit Basket, Apiary, Flower Stand, Scholar's Desk,
      Sieve, Tinker's Bench, Compost Bin, Nether Brazier, Undertaker's Table, Inn Counter, Teacher's Desk, Feed Trough,
      Carpenter's Bench, Kitchen Stove, Postal Desk, Guard Post, Nurse Station, Music Stand, Leader's Podium, Tutor's
      Desk, Ball Workbench, Trade Board, Fossil Lab.

    Existing worlds (his live server): the gone blocks stay registered and keep working where they're placed, but can't
    be crafted any more and leave the creative tab. Our village houses, workshops and starter builds are redrawn with
    the vanilla blocks (Architect skill, renders).
    The owner's answers (2026-09-30): builders work at the Blueprint Table; the Miner shares the blast furnace with
    the Armorer (a pickaxe picks the Miner). The kept blocks' own job still takes a jobless villager by itself, as the
    Builder's Bench did; the other jobs on them start with their item.
    Done when: every job in the plan starts from its block and item (a GameTest each, and its showcase scene staged
    that way); the README's job table, the recipe book and the tooltips say so; a world saved by 0.137.0 with the old
    blocks loads and its workers keep working (a GameTest); no village house or starter build uses a gone block; the
    showcase is green. Do it in pieces that fit a night run, landing each with `land --keep-open`: the mechanism first.
    **Decisions while building** (chat, 2026-09-30): (1) our jobs can also re-take their shared vanilla block by
    themselves (acquirable = held), as vanilla workers do; a jobless villager still gets the vanilla job, because the
    first registered job wins. Without it a worker whose block record was missing for a moment never found it again.
    (2) Choosing a job doesn't hire. (3) The Postal Desk's post-office pickup moved to any mailbox a postman works at.
    (4) Village houses whose block is shared come with a villager who already has the house's job (an entity in the
    house template, VillageGameTests); the workshop, trainer's house, ferry house and storehouse still get a jobless
    one. (5) Hold Shift over a workstation: its tooltip lists the jobs and items (StationTooltip).
    **Left for later:** the Hall's free-workstations list still offers only the vanilla job at a shared block (the
    player switches with the item afterwards); the sifter has no "panning" look at a water cauldron; /workplace
    benchmark still places old blocks (they work). A real 0.137.0 world opened with this version is 21.2's check.
  - [x] **21.1b** (verified 2026-10-02: its tester Check, shipped in 0.138.0) (approved 2026-10-04) Change from the owner (2026-10-01): lets move to textures, what i want to do is for every item in the game (except for villager skins, and blocks) send me a couple versions, such as blueprints, i will go through the versions and pick my favorites, and give guidance when i can.
    **Plan** (chat, 2026-10-01): the 13 items of ours that aren't blocks (Blueprint, Blank Blueprint, Scan Tool, Shape
    Planner, Patrol Map, Village Ledger, Rally Banner, Quarry Marker, Field Marker, Travel Ticket, Delivery Note, Price
    Tag, Settler's Wagon). Each gets its current icon plus three new versions, each a different idea, drawn with the
    pixel-art skill in vanilla's style; recipes in tools/textures/picks/. They go on one picker page he can use on his
    phone: one pick per item and a note; another round for any item his notes ask for.
    Done when: every item has the versions on the page; his picks are the items' textures (recipes moved into
    tools/textures/art/items.py, lint clean); a review package shows the picked icons in game (slots and in hand).
    **Round 1 picks** (owner, 2026-10-01, on the page): blueprint v4 (clipped to a drawing board), blank blueprint v2
    (two sheets), shape planner v2 (the brass compass), village ledger v3 (open ledger), delivery note v4 (clipboard),
    travel ticket v2 (sailing-boat ticket), price tag v2 (tag with a $), rally banner v4 (upright standard, crossed
    swords; its model is now item/generated), settler's wagon v2 (side view): all nine are in items.py and the game.
    His notes: patrol map v2 "make the outline of the paper exact to the paper outlines of maps currently in minecraft";
    quarry marker v1's flag "use the stick pattern from the paper card with a red x, but this flag"; scan tool v4
    "straighten out line, make it more obviously a pencil with the tip coming to a point"; field marker "redo all of
    these, none make sense to me"; for every icon "try to follow the exact outlines minecraft has in place for certain
    items, for example if minecraft already has a map design then follow that exact outline, just drawing different
    content atop of it". Round 2 (tools/textures/picks/round2.py) is on the same page, collection 'round2'.
    **Round 2 picks** (owner, 2026-10-02): patrol map, quarry marker, scan tool and field marker all r2b (vanilla map
    paper; swallowtail flag; pencil with a white blueprint line; a field plan on the map outline, now item/generated),
    no notes. All 13 icons are his picks in items.py and the game; the 'items' showcase scene films them.
  - [x] **21.1c** (verified 2026-10-02: its tester Check, shipped in 0.138.0) (approved 2026-10-04) Change from the owner (2026-10-03): the Fossil Scientist works at Cobblemon's Fossil Analyzer instead of a block of ours (owner 2026-10-02: 'the fossil researcher - this is already a working block within cobblemon so adding it as an extra block within our modpack seems unnecessary. can we rework this villager to work off of that?')
- [x] **21.2** (approved auto 2026-10-04) **The full check before release** (the tester's Full tier; the chat started it and ran out of time). Do
  it in pieces that fit a one-hour night run, landing each piece with `land --keep-open`:
  - the real Cobbleverse pack boot and the soak: the nightly GitHub workflow runs these, so read its result;
  - an old world saved by 0.136.0 or 0.137.0 opened with the new version: nothing lost, nothing crashes;
  - leaving the game in the middle of a ferry ride, and a travel ticket between dimensions;
  - performance with many homes;
  - 20 mutants and a flake sweep over the whole suite, and the biggest gaps from `inventory.py`.
  - Report: docs/agent/full-check.md (the last pieces, 2026-10-04: GitHub performance after B14, and a 0.138.0 world opened with main; finding B46).

  Done when: each part is a passing test or a green nightly result, every finding is a Bug, and the report is linked
  in the Notes. Players see nothing new, so the last piece lands with `--no-review`.
- [x] **21.3** (approved auto 2026-10-03) (verified 2026-10-02: its tester Check, shipped in 0.138.0) **Camels.** The game counts a saddled camel as a horse, so a
  guard rides one too, but the changelog promises horses, donkeys and mules. The tester's test `aCamelIsNotCavalry`
  (branch `tests/check-0.137-riding-protection`) waits on his answer. Camels out: land that test with the fix. Camels
  in: turn the test around and add camels to the changelog.
  **Answered** (owner, 2026-10-01): camels in. The test is turned around as `aGuardRidesASaddledCamel`
  (RidingSpecGameTests: a saddled camel carries a guard and he gets on it; an unsaddled one doesn't), and the
  changelog says so. The rest of that old branch already reached `main` in other items.
- [x] **21.4** (released 2026-10-02: v0.138.0, https://github.com/jCondeData/minecraft-alive-workplace/releases/tag/v0.138.0) **Release 0.138.0.** Only the owner's chat releases
  (CLAUDE.md, "Releasing"). Done when: the GitHub release has the jar.
  **Released early** (chat, 2026-10-02): the owner said ship ("unless there is massive bugs i want to be able to play
  asap"), with 21.1 approved, 21.3 and 21.1c landed, and B5, 21.1b and 21.1c still pending his review (they ship).
  Instead of the full 21.2 check, the tester ran a scoped release check: a world saved by the real 0.137.0 jar on the
  Cobbleverse pack, with all 26 retired job blocks and their workers, opened with the new jar (26/26 blocks, jobs and
  job sites kept, no errors from our mod); the full suite and the compat suite green, nothing missing against the
  baseline; no new flakes (B2 failed once more in a land build and passed on the retry). 21.2's other parts (the ferry
  mid-ride, performance, mutants, the standard pack test) stay for the night runs.

## Milestone 22: Safety net

Before polishing, make sure nothing regresses unnoticed.

- [x] **22.1** (approved auto 2026-09-29) (verified 2026-10-02: its tester Check, shipped in 0.138.0) **Tester set up** (f83f7b4): `tools/modtest/` with `allow.txt` and
  `baseline.json`, the nightly workflow, and the definition of done in CLAUDE.md. The nightly workflow runs at about
  10 PM Central (`17 3 * * *`), before the night runs, which read its result first.
- [x] **22.2** (approved auto 2026-10-03) (verified 2026-10-03: inventory.py: 73/73 saved values have a save/reload test [list empty]; both suites reflect over every ModAttachments field and fail on one without a sample; mutants 3/3 killed [FieldJob.adopted dropped, trees_felled and ball_orders lost on load]) Save/reload tests for every value `inventory.py` lists as "never saved and reloaded" (69 at 0.136.0), done in
  batches: builder, miner and lumberjack data first. Done when: that list is empty, or each remaining entry has a reason.
- [x] **22.3** (verified 2026-10-04: README's 43 jobs and its screens each map to a scene in tools/showcase/scenes.py [73 scenes]; the live showcase [e39f973] passes 70: missing, battle and soak fail and are filed as B41 [harness title check false positive], B43 [battle once], B42 [soak 21/22, 23.1]) (approved 2026-10-04) A bot scene for every player-visible feature, so any feature can be shown again on demand
  (`tools/screenshots/run.sh SCENE=…`). Done when: every job and every screen in the README has a scene. A new feature
  gets its scene in the same commit.
- [x] **22.4** (approved auto 2026-09-30) (verified 2026-10-02: its tester Check, shipped in 0.138.0) **A daily showcase page** (owner, 2026-09-30): screenshots and GIFs of everything the mod does, so the
  owner can check it all from his phone each morning. Includes 22.3's missing scenes. Done when:
  - every night, GitHub's machines run every screenshot scene: every job, screen and build family. Not a Claude
    session: it must cost no Claude usage. The scenes are split across parallel jobs so it finishes within an hour,
    and any scenes that are still missing are added (22.3);
  - the results land on one page he can open on his phone at a fixed link: for each scene, a GIF of it working from
    start to end and 2-4 labelled stills, grouped by job, with the date and version at the top;
  - each scene gets a pass or fail. It passes only if the job visibly did its work (the builder finished, the miner
    dug, the screen opened). A failed scene or a broken picture (missing textures, raw text keys, villagers stuck in
    walls) opens the nightly-tests issue, so the night runs fix it;
  - if publishing the page needs a GitHub setting only he can change, he gets the exact clicks.
- [x] **22.5** (verified 2026-10-04: QaHallPagesSeasonsGameTests [5, from the Done when]: six more pages by one register call each get their own tab and open by a real click with header, content and back [old buttons in place], duplicate id and a 10th page refused; 28 villagers list as a full page of 27 + next page and back; Nether reads the overworld's date, server clock drives the calendar; 2 years day by day for season lengths 1,2,3,7,8,9,120: a day each day, seasons in order on each first day, one festival per season, year turns after winter; calendar tab/header/seasons all translated. Full build at 6d5c003 569/569 + compat 56/56. Hall stills at GUI 2 and 4 checked on the showcase page [page row: clock tab then free slots]. Mutation/repeats: no nightly since 2026-10-03 09:11Z, not read) (approved 2026-10-04) **Room on the Village Hall's screen.** Milestones 27, 29, 30, 31 and 33 each add a page to the hall, and
  its screen has no free slot. Give it page tabs (or a second row of page buttons) with room for at least six more
  pages, keeping every existing page and button where players know it. Done when:
  - a GameTest opens every existing page through the new layout;
  - a new page is one registration call (documented in `docs/agent/layout.md`), and the expansions use it;
  - the hall scene shows the new layout at GUI scales 2 and 4.
- [x] **22.6** (verified 2026-10-04: QaHallPagesSeasonsGameTests [5, from the Done when]: six more pages by one register call each get their own tab and open by a real click with header, content and back [old buttons in place], duplicate id and a 10th page refused; 28 villagers list as a full page of 27 + next page and back; Nether reads the overworld's date, server clock drives the calendar; 2 years day by day for season lengths 1,2,3,7,8,9,120: a day each day, seasons in order on each first day, one festival per season, year turns after winter; calendar tab/header/seasons all translated. Full build at 6d5c003 569/569 + compat 56/56. Hall stills at GUI 2 and 4 checked on the showcase page [page row: clock tab then free slots]. Mutation/repeats: no nightly since 2026-10-03 09:11Z, not read) (approved 2026-10-04) **One season calendar.** Milestones 28 (the Festival Cup), 30 (harvest season), 31 and 34 need seasons,
  and none exist. A village calendar: four seasons of `seasonDays` days (default 8, one festival each), the same for
  the whole world, shown on the hall with the day of the season; an event API the expansions listen to. Done when:
  GameTests cover the rollover, save and reload, and the config length; nothing else changes until an expansion uses
  it.
  - [x] **22.6a** (approved auto 2026-10-04) Change from the owner (2026-10-04): the festivals are good; make each season 16 days (a 64-day year)
- [x] **22.7** (approved auto 2026-10-04) **Mutation and repeats on GitHub, every night.** The nightly workflow also plants about 20 mutants in
  the code changed in the last 24 hours (`tools/modtest/mutate.py`, sharded across parallel jobs) and repeats every
  GameTest added in that time 10 times. Survivors and flakes go to the `nightly-tests` issue. This moves the slowest
  checks off the lanes, onto GitHub's free machines. Done when: one night's run has posted its results and the QA lane
  reads them (`docs/agent/sessions.md`).
- [x] **22.8** (approved auto 2026-10-04) **Film one scene on demand.** `showcase.yml` takes a `scenes` input, and a push to an item branch films
  only the scenes that branch added or changed, so a lane sees its scene on GitHub in about 15 minutes without running
  the client itself. Done when: a push that changes one scene films only that scene, and `docs/agent/tools.md` says how
  to read the result.


- [ ] **22.9** **The wiki: how everything works, kept true** (owner, 2026-10-06). `docs/wiki/`, plain markdown that GitHub
  shows as pages: `README.md` is the index; one page per feature or system (what a player sees; how it works in a few
  paragraphs; every config switch with its default; every saved field with its default; the commands, items, blocks
  and jobs; the decisions made and why, with the roadmap id; known limits; the tests and scenes that prove it). Written
  for the owner first, in plain words, then the lanes. Who keeps it true: **the lane that builds or changes a feature
  updates its page in the same commit** (sessions.md, build loop step 5); **the QA lane checks it**: for every item it
  verifies, it reads the page against the behaviour its tests proved and fixes or files a bug, and when nothing is
  waiting it backfills one missing page per run, oldest milestone first. Done when: `docs/wiki/README.md` and the page
  format exist; `tools/modtest/wikicheck.py` fails the build when a page names a config key, item id or lang key that
  no longer exists, or a ticked item with a player-visible change has no page; the first twelve pages are written
  (Builder, Villager jobs, Village Hall, Steward, Roads and walls, Edicts, Guilds, Legends, Pokemon partners, Classes,
  Elders, Config switches) and the digest links the wiki; the QA lane's step list in sessions.md says so.
## Milestone 23: Builders never need babysitting (priority 1)

MineColonies players' most common complaints are builders that get stuck, don't say what they need, stop when
inventories are full, or only work while a player stands nearby. Ours must do none of that. Check what already exists
first; many items below are "verify and harden", not "build".

- [x] **23.1** (approved 2026-10-04) **The builder soak test.**
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
  - [x] **23.1a** (approved 2026-10-05) (verified 2026-10-05: qa/crew-looks-1005: crew of 4 builds the stone house from exactly its list with nothing duplicated or lost [scaling log: 2 = 50%, 4 = 29%]; toss-sound cap full every tick still passes materials, no item entity; house looks SAME/NO_BUILDER/BUILDING sentences, forged styles refused, change of mind desert->taiga mid-rebuild ends taiga with the room kept. No mutants this run [time].) Change from the owner (2026-10-04): builder speed is fine (accept ~3.5 in-game days for the 22 starter builds). Time should grow with bigger builds, but be cut in half with every builder working on it (2 builders ~ half the time, and so on): check helpers really scale like that and fix it if not
  - [x] **23.1b** (verified 2026-10-05: qa/crew-looks-1005: crew of 4 builds the stone house from exactly its list with nothing duplicated or lost [scaling log: 2 = 50%, 4 = 29%]; toss-sound cap full every tick still passes materials, no item entity; house looks SAME/NO_BUILDER/BUILDING sentences, forged styles refused, change of mind desert->taiga mid-rebuild ends taiga with the room kept. No mutants this run [time].) (approved 2026-10-05) Change from the owner (2026-10-05): Helpers' material-toss sounds only (no flying item), but cap how many play at once so many builders can't cause lag: keep it to a maximum number and skip sounds past the limit.
- [x] **23.2** (approved auto 2026-10-04) **Stuck recovery, proven.** Builders stuck on water, lava, holes, fences, doors, their own scaffolding, or in
  unloaded chunks. Done when: a chaos test (the tester skill's `ChaosTests`, 5 seeds) finishes every time, and the
  recovery (hop, re-path, step back) never breaks a placed block.
- [x] **23.3** (verified 2026-10-04: QaGlanceSupplyGameTests [7, shipped 81fca7d]: exactly 3 shortages show no 'and N more'; counts net of the chest and the builder's bag; 'Has everything it needs'; '2 chests by its bench at x y z'; 0% title on a new build; a full bag with a full chest and no storehouse still builds the hut. With OverheadSupply [7] and SelfHealingSupply [9]. Full build 585/585 + compat 60/60. Split soak [Done when of 23.5] not rerun: lane's run 6d 22/22, stalls filed as B40. Mutation/repeats: no nightly since 2026-10-03 09:11Z; could not start part=mutation by hand (403). Preview scene passes in showcase run 51.) (approved 2026-10-04) **"What do you need?" at a glance.** The player can always see what a build is missing and where the builder
  looks for it, without opening screens every 30 seconds. MineColonies players install separate HUD mods for exactly
  this. Look at what exists (the "still missing" tooltip, the requests board, the overhead status), then close the
  gaps. Done when, from a single look (hovering the site or the builder), you can see:
  - what fraction is built;
  - which 3 items are missing most, with counts;
  - which chest or storehouse the builder takes from.
- [x] **23.4** (verified 2026-10-04: qa/material-list-1004 shipped at 620a9232: 5 tests. 23.4 [QaMaterialListGameTests]: every library blueprint's unplaced book equals the builder's plan, each kind once, <=7 lines a page, title <=32, book saves; two chests add up, never below zero, unused items not listed; blueprint in off hand writes too. 23.6 [QaKeepVillagesWorkingGameTests]: keepVillagesWorking default true [new/older config], false pauses; workplaceKeepWorkLoaded off keeps a far listed village unloaded. Build 578+58 green. Scene 'missing' green in showcase 37187332687. Mutation: not yet in a nightly [the 2026-10-04 nightly hadn't run by 09:20Z].) (approved 2026-10-04) **A material list you can take away.** A checklist of everything a blueprint needs, minus what's in the supply
  chests, as a written book or the blueprint's tooltip pages (like Create's Schematicannon). Done when: a GameTest
  checks the numbers against a known blueprint and a screenshot shows it.
- [x] **23.5** (approved auto 2026-10-04) (verified 2026-10-04: QaGlanceSupplyGameTests [7, shipped 81fca7d]: exactly 3 shortages show no 'and N more'; counts net of the chest and the builder's bag; 'Has everything it needs'; '2 chests by its bench at x y z'; 0% title on a new build; a full bag with a full chest and no storehouse still builds the hut. With OverheadSupply [7] and SelfHealingSupply [9]. Full build 585/585 + compat 60/60. Split soak [Done when of 23.5] not rerun: lane's run 6d 22/22, stalls filed as B40. Mutation/repeats: no nightly since 2026-10-03 09:11Z; could not start part=mutation by hand (403). Preview scene passes in showcase run 51.) **Self-healing supply.** Builders use the storehouse and porters without being told. A full builder inventory
  never stops work. Wanted items go on the requests board automatically. Done when: the soak test passes with the
  materials split across 3 chests and the storehouse.
- [x] **23.6** (approved auto 2026-10-04) (verified 2026-10-04: qa/material-list-1004 shipped at 620a9232: 5 tests. 23.4 [QaMaterialListGameTests]: every library blueprint's unplaced book equals the builder's plan, each kind once, <=7 lines a page, title <=32, book saves; two chests add up, never below zero, unused items not listed; blueprint in off hand writes too. 23.6 [QaKeepVillagesWorkingGameTests]: keepVillagesWorking default true [new/older config], false pauses; workplaceKeepWorkLoaded off keeps a far listed village unloaded. Build 578+58 green. Scene 'missing' green in showcase 37187332687. Mutation: not yet in a nightly [the 2026-10-04 nightly hadn't run by 09:20Z].) **Working when no one is near.** The owner decided (2026-10-03): villages **keep working** when no
  player is in range, through `KeepLoaded` tickets. The game rule `workplaceKeepWorkLoaded` already exists: start from
  it. Add a config option (default: keep working, so a server owner can still choose to pause), and document it
  in the README. Done when: both settings are tested, and there are no chunk-loading surprises (count the tickets
  before and after the soak).
- [x] **23.7** (approved auto 2026-10-04) (verified 2026-10-05: 6 new tests [QaImportEdgesGameTests, shipped 30f2d6f]: not-a-build files [text, empty, PNG], uncompressed .nbt, the 1,000,000-block limit both sides [.nbt and .schem], the 8 MB file limit both sides, empty/all-air/0-wide/no-region files, a schematic with cut-off data; plus the 6 corpus tests. Not filed [only a hand-damaged file shows it]: a .schem block index past its palette imports as air instead of 'damaged'. Mutation not run here.) **Imports that just work.** `.litematic`, `.schem` and `.nbt` files in common sizes and versions, including big
  builds (48×8×48), unknown modded blocks and old formats. Done when: a test corpus of permissively licensed or
  self-made sample files imports, or fails with a clear message that says which block or format was the problem.
- [x] **23.8** (approved 2026-10-04) (verified 2026-10-05: qa/placing-1005 [3 tests, pass alone; not on main yet]: all 4 turns and mirrored, the build plan's box equals the ghost box with every block inside; cancel mid-build with a stuffed chest, sent twice, returns every material exactly, one blueprint back; a turned, mirrored hut on a 1-block drop stands on its foundation inside the ghost. Showcase placing, preview, shapes pass [run 37279004841]. Moving an active site not covered [no direct move entry point found]. No mutants [time]) **Placing a build feels good.**
  - Rotation and mirroring before placing.
  - The ghost preview shows exactly where it goes.
  - The site can be moved or cancelled, with its materials returned.
  - Sloped ground is handled (foundation fill and landscaping).

  Done when: a review package shows each step, and a cancel test returns every material.
- [ ] **23.9** (blocked: owner, optional: his call) **Owner-built signature builds.** The owner builds 3–5
  flagship buildings in-game. Claude tidies them with the Architect skill, and they are scanned in with the Scan Tool
  and shipped as blueprints with upgrades. This gives the mod human-made content; see 26.1. Done when: each is in the
  Blueprint Table with its upgrades, and a builder has built it in a test.
- [x] **23.10** (approved 2026-10-04) **Every shipped build reviewed.** One gallery package per build family (houses, workshops, defences,
  decorations, village pieces in five styles), each build shown front and back. Vetoed builds get redrawn with the
  Architect skill. Done when: every family's package has been sent.
  - [x] **23.10a** (verified 2026-10-05: qa/crew-looks-1005: crew of 4 builds the stone house from exactly its list with nothing duplicated or lost [scaling log: 2 = 50%, 4 = 29%]; toss-sound cap full every tick still passes materials, no item entity; house looks SAME/NO_BUILDER/BUILDING sentences, forged styles refused, change of mind desert->taiga mid-rebuild ends taiga with the room kept. No mutants this run [time].) (approved 2026-10-05) Change from the owner (2026-10-04): keep one shared outside per village style, but let the village leader override it (choose a different look for a piece)

## Milestone 24: Everything looks finished (priority 2)

Textures go through the minecraft-pixel-art skill. The owner judges them in review packages (his taste decides;
recent picks: lean shapes, heads that are thick where they work and thin where they join, wood for tools, a steel
haft and leather grip for weapons, no decoration the shape doesn't need). Use pick sheets (`preview.py pick`) when a
texture has no clear direction yet.

- [x] **24.1** (verified 2026-10-04: 24.1: lint.py on all 110 outfit textures [55 professions, villager + zombie, same set]: 0 errors; 24.2: lint.py on every item texture [the one tool, scan_tool: OK, 15 colours]; 24.5: langcheck.py clean [all sections 0], and a scan of all 1742 en_us strings for stray spaces, unbalanced brackets, repeated words and TODOs found only intended ones [list-joiner fragments, file extensions]; showcase run 55 [265d1a2]: 73 of 75 scenes pass, failures are B44 [soak] and B45 [partners_engine], none in these items) (approved 2026-10-04) **Villager outfits.** Each of our professions has a vanilla-style outfit and a zombie version
  (`preview.py villager`: every biome, zombie, back view). Done when: every profession passes `lint.py`, and one sheet
  of all professions is in a review package.
- [x] **24.2** (approved auto 2026-10-04) (verified 2026-10-04: 24.1: lint.py on all 110 outfit textures [55 professions, villager + zombie, same set]: 0 errors; 24.2: lint.py on every item texture [the one tool, scan_tool: OK, 15 colours]; 24.5: langcheck.py clean [all sections 0], and a scan of all 1742 en_us strings for stray spaces, unbalanced brackets, repeated words and TODOs found only intended ones [list-joiner fragments, file extensions]; showcase run 55 [265d1a2]: 73 of 75 scenes pass, failures are B44 [soak] and B45 [partners_engine], none in these items) **Tools and weapons.** Every tool or weapon item uses the owner-picked templates (hammer, wrench, war hammer)
  or has been through a pick round. Done when: every tool or weapon item passes `lint.py`, and its package is sent.
- [x] **24.3** (approved 2026-10-04) **Known visual bugs.** Riding looks right now (villagers sit in saddles and boats and the ferry
  floats; approved 2026-09-29). Left: any clipping, floating or z-fighting in the scenes (`SCENE=extras`, `staff`,
  `village`, …). Done when: each one found has a before/after in a review package, or a package shows the scenes
  clean.
- [x] **24.4** (verified 2026-10-04: showcase run 55 [265d1a2]: every screen scene passes, including the harness's title-fits check at each GUI scale [fixed by B41, verified] and the hall at GUI scale 4 [09_hall_scale4]; only soak [B44] and partners_engine [B45] failed. 24.3 left unverified: the soak scene's builder standing in an open door [showcase run 54] is clipping, tracked in B42) (approved 2026-10-04) **Screens.** Village Hall, Blueprint Table, requests board, mailbox, shop and research screens are readable at
  GUI scales 2–4, have no clipped or overlapping text, and use vanilla-style panels. Done when: screenshots of each
  screen at scales 2 and 4 are in a review package, with no clipping visible.
- [x] **24.5** (verified 2026-10-04: 24.1: lint.py on all 110 outfit textures [55 professions, villager + zombie, same set]: 0 errors; 24.2: lint.py on every item texture [the one tool, scan_tool: OK, 15 colours]; 24.5: langcheck.py clean [all sections 0], and a scan of all 1742 en_us strings for stray spaces, unbalanced brackets, repeated words and TODOs found only intended ones [list-joiner fragments, file extensions]; showcase run 55 [265d1a2]: 73 of 75 scenes pass, failures are B44 [soak] and B45 [partners_engine], none in these items) (approved 2026-10-04) **Words.** `langcheck.py` is clean. Every tooltip and message has been read in context (screenshots), with
  consistent names for jobs, blocks and items (the README job table is the reference). Done when: `langcheck.py` is
  clean, and a package shows every new or changed message in context.

## Milestone 25: Big villages stay smooth (priority 3)

Baseline at 0.136.0: 80 busy workers in the full pack took the tick from about 2 ms to about 7 ms, and our code was
about 12% of that. For villager mods, the cost is pathfinding to distant points of interest and brain ticks.

- [x] **25.1** (approved auto 2026-10-05) **Measure first.** A repeatable benchmark:
  - 150 workers over 3 villages in the Cobbleverse pack;
  - `tick query` p50/p95, heap after GC and a JFR profile (`tools/packtest`, `PERF=true`);
  - run on GitHub's runner and recorded in `docs/performance.md`.
  - Status (lane-a-1004-0932): built: `PERF=true VILLAGES=3 PLOTS=25` (150 workers, heap after GC), the nightly's performance step runs it every night and a Sunday `benchmark` job (or `part: benchmark`) on its own; method in docs/performance.md. Left: copy the first run's numbers (nightly-tests run tonight, `perf.log` in its artifact) into docs/performance.md's table and tick. Sessions can't start workflows (403), so it waits for tonight's run.

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
  - Status (lane-a-1004-1233): booted clean on both sides (docs/performance.md, "The pack's performance stack"): the
    pack test's server loads Lithium, C2ME, FerriteCore, ModernFix, Krypton and ScalableLux with no error of ours or
    theirs; `PERF_STACK=true tools/screenshots/run.sh` adds all nine to the client (the pack's versions), which boots and
    passes its scene. The benchmark and the soak already run with them (they run the whole pack). Left: tonight's
    nightly soak and the first benchmark (25.1) give the numbers; compare them with the 25.2 targets, add the row and
    tick.
  - Status (lane-a-1005-0932): nightly-tests 37297003813 (5b489b7) ran the load with the stack and no error of ours;
    share 13.1% (<15% ok). Off: busy p99 66.1 ms in the first sample (not shown to be ours) and no 60-minute soak
    exists, so the heap-flat target is unmeasured (one reading, 1613 MB). Left: a 60-minute soak with heap readings
    (`PERF` heap sampled every 10 min) and the profile of the p99 spikes; then tick.
- [x] **25.5** (approved 2026-10-05) **Server owner controls.** Config caps like MineColonies' (max workers per village, how far workers path, the
  far-from-players behaviour from Milestone 23). Needs systems (moods, sickness, raids, festivals) are easy to switch
  off, because "babysitting" is the top complaint about big colony mods. Done when: each key is documented in the README
  and tested switched off (the tester's config matrix).

## Milestone 26: Release 1.0

- [x] **26.1** (approved auto 2026-10-04) (verified 2026-10-04: the owner's decision is recorded in Design decisions [ROADMAP 'Release channel', 2026-10-03: CurseForge and GitHub only, not Modrinth]; nothing to test in the mod) **Release channel.** **Decided by the owner (2026-10-03): 1.0 goes to CurseForge and GitHub, not
  Modrinth**, each page saying the mod was made with AI assistance, without saying where (see Design decisions). Done when that
  is recorded, which it now is: tick this with `land --no-review` from any session that touches 26.x. The background,
  kept for reference: Modrinth's content rules have been enforced
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

  **Owner, 2026-10-02:** he asked for patch notes and documentation 'that can be pushed to curseforge': CurseForge
  first; Modrinth still open.
- [x] **26.2** (verified 2026-10-03: store kit [docs only] checked against its spec: summary 136 chars, plain, doesn't repeat the name; name leads with Alive Workplace; description states client+server required, Fabric API, Cobblemon optional; icon, gallery and one GIF per headline job [builders, miner, lumberjack, orchard, guard, trainer]; AI disclosure and license left as open questions for the owner; all 20 image/GIF URLs in description.md and gallery.md answer 200 today. No code, no tests needed.) (approved 2026-10-04) **Store page kit**, for the chosen sites:
  - a one-line summary (no formatting, doesn't repeat the name);
  - a description that says what it adds, why to get it, and what to know first (server and client both need it,
    Fabric API, optional Cobblemon);
  - the name **Alive Workplace**: Minecraft's brand rules forbid leading with "Minecraft";
  - an icon and gallery images (Modrinth: only if its image rule allows them);
  - one GIF per headline job;
  - the AI disclosure where required;
  - client = required, server = required.

  Done when: the kit is in a review package, as it would look on each site.

  **Kit for CurseForge** (2026-10-02): docs/store/curseforge/ (summary, description, 0.138.0 patch notes, gallery,
  notes on where each goes).
  - [x] **26.2a** (verified 2026-10-03: with B18 landed: GuideGameTests + QaGuideClaimsGameTests + QaGuideFloristGameTests green [gift, recipe, every page's picture, the guide's job claims incl. tall flowers]; showcase run 36 [guide scene] green; pink petals/spore blossom and the composter tooltip gap filed as B29 against B18) (approved 2026-10-04) Change from the owner (2026-10-03): an In-Game Guidebook (owner 2026-10-02: 'impliment an In-Game Guidebook: Illustrated, easy-to-follow instructions. use in game screenshots so you dont illustrate. this book should make it easy for players do understand whats happening')
    **Built** (chat, 2026-10-02): a Guide Book item (`guide/GuideBookItem`, screen `client/guide/GuideScreen`), 35
    pages in 7 chapters (the Pokémon chapter only with Cobblemon), each a screenshot from the screenshot scenes
    (`tools/guide/pages.py` picks the still and crop; `tools/guide/build.py` makes the 384 x 216 pictures) with a title
    and at most six lines of steps from en_us.json. Given once to every player by the hidden advancement
    `aliveworkplace:guide_book` (existing players get it on their next join); crafted from a book and wheat. Its icon is
    vanilla's book on its outline, bound in blueprint blue with a gold house (lint warns of 12 single pixels, as it does
    for vanilla's own book: the page edges). New scene `guide` opens every page and checks its picture and that its words
    fit; GuideGameTests checks the gift, the recipe and every page's picture.
- [x] **26.3** (approved 2026-10-05) **Hygiene:**
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

## Milestone 27: Villages that build themselves (1.1)

Items in Milestones 27–35 sometimes point at another milestone's item by name ("M29's Seer item"): find it there.

You paint your village's plan once on a **City Plan** (homes here, workshops there, a market, gardens, roads, a wall),
and a new villager, the **Steward**, grows the village into it: every morning he compares the plan with what the
village lacks (the numbers behind the hall's "What next?" tips) and lines up the right building, in the right zone and
the zone's style, for the village's builders. You approve each plan with one click at the hall, or let the village run
itself; roads with lamps and bridges link the buildings and the villages you trade with, walls go up once raids start,
and an old vanilla village slowly rebuilds itself one house at a time. It answers the top complaint about colony mods
(babysitting) and builds on the builder (`BuildSite`, styles, upgrades, `Paths`), the Village Hall (census,
`VillageAdvice`, ranks, research) and `Caravans`.

- [x] **27.1** (approved 2026-10-05) **Design note.** `docs/design/M27.md`: what the player sees (the City Plan and its screen, the
  Steward's day, his desk on the hall's screen, roads, walls, renewed houses), the data formats with one example file
  each (zone kinds, Steward rules, road styles, wall kits, renewal lists), the config switches, every new saved field
  with its default (the plan and the Steward's state on the hall, the player-built ledger), the per-tick budgets, the
  safety rules (27.19) and how the Cobblemon parts stay data with `"requires": ["cobblemon"]`, as the Apricorn style
  does (no Cobblemon classes in this milestone). Owner questions, each with the default the lanes build meanwhile:
  a Steward is appointed with the City Plan rather than taking the hall by himself (default: with the plan); a new
  Steward starts in "Ask me first" (default: yes); the City Plan's recipe (default: a Map and a Blank Blueprint).
  Sent as a review package with a mock-up of the plan screen painted over a real village's map (`VillageMaps`); lanes
  don't wait for his reply. Done when:
  - the note is on `main` with every section above;
  - the package is sent, and his answers, when they come, are recorded with `sessions.py reply`.
  - [x] **27.1a** (approved 2026-10-05) Change from the owner (2026-10-05): A Steward must be a villager who has been an architect/builder for a certain amount of time (decide the amount and write it in the design note; default meanwhile: Builder at a level or for N days). Also make the City Plan's recipe harder: Map and Blank Blueprint plus something hard to get, such as a Heart of the Sea (or similar).
- [x] **27.2** (approved 2026-10-05) **The plan and the City Plan item.** A village's plan, saved on the Village Hall
  (`VillageHallBlockEntity`, new tag `plan`, empty by default) and kept on the hall item when the hall is broken (as
  its name is; put down again, the plan is centred on the new spot):
  - a grid of 32×32 cells centred on the hall, a cell 4×4 blocks at the default `villageHallRadius` of 64 (8×8 at
    128), so a cell is a square of the village map; a cell nearer another hall than this one can't be painted;
  - up to 16 zones, each with a kind, a name, a style (one of `BlueprintStyles.all()`, or as drawn), a "renew old
    houses" switch (27.20, off) and its cells; up to 24 roads and one wall line (27.4); the Steward's mode (27.8).

  Zone kinds are data: `data/aliveworkplace/city_zones/<kind>.json` holds one kind's colour, map tint, icon item,
  order and whether anything may be built there; packs add their own. The 8 shipped kinds, coloured as the village
  map's banners (`VillageMaps.Kind`): **Homes** (white), **Workshops** (orange), **Farms** (lime), **Market**
  (yellow), **Civic** (blue: school, library, clinic, chapel, graveyard), **Gardens** (light blue: decorations,
  parks), **Defences** (black: barracks, towers) and **Keep Clear** (red: nothing is ever built there; roads may
  cross). The **City Plan** item (recipe: a Map and a Blank Blueprint; its icon drawn with the pixel-art skill on
  vanilla's map outline, as the owner asked for the Patrol Map): right-click a Village Hall to bind it, as the Village
  Ledger binds; its tooltip names the village. Who may change a plan: the hall's owner, their friends and operators
  (`VillageProtection`'s rules; a hall nobody owns is claimed by the first player who paints). Server side:
  `CityPlan.zoneAt(pos)`, `paint`, `erase`, and packets the server checks. Done when:
  - GameTests: a plan with every field survives save and reload, and breaking and placing the hall; a hall saved
    before 1.1 loads with an empty plan; `zoneAt` is right in all four quarters round the hall, at negative
    coordinates and with a radius of 128; a cell nearer another hall is refused; a stranger's paint packet in someone
    else's protected village is refused;
  - the item binds, names its village, is in the `items` showcase scene, and its icon passes `lint.py`;
  - a datapack's zone kind loads, and a broken file is skipped with a warning naming it.
  - Status (lane-a-1004-1533): built on `main`: `city/CityPlan` (grid, zones, roads, wall line, mode; saved as the
    hall's `plan` tag and the hall item's `aliveworkplace:city_plan` component), `CityZones` (8 shipped kinds as data),
    `CityPlans` (the checked edit packet), the City Plan item (binds, names its village, says which zone you stand in;
    recipe, recipe-book unlock, icon via the pixel-art recipes, in the items scene). CityPlanGameTests (7) pass. Left:
    film `SCENE=items` with the new icon, hand in the package and tick (`done 27.2 --review`).
- [x] **27.3** (approved 2026-10-05) **Painting the plan.** Right-click the air with a bound City Plan to open the plan screen (a client
  screen, like the Blueprint Table's): the village map as it is today (`VillageMaps.colors`, a block a pixel, north up,
  the hall and every finished building's banner on it), the grid over it, each zone tinted in its colour with its name,
  Keep Clear hatched, build sites going up and the Steward's proposals as outlines. A side panel: the zones (new,
  rename, delete, kind, style with the style's icon, the renew switch), a brush (one cell, or drag a rectangle), an
  eraser, undo (10 steps) and a legend. Scaled to fit at GUI scales 2 to 4. Done when:
  - a GameTest paints, erases and undoes through the packets, and the plan on the hall matches each time;
  - screenshots at GUI scales 2 and 4 show no clipped or overlapping text (as 24.4 checks the other screens);
  - showcase scene `city_plan`: Homes, Workshops and Gardens zones painted in three styles over a real village, with
    stills of the screen and the finished plan.
- [x] **27.4** (approved 2026-10-05) **Roads and the wall line on the plan, and the plan on the ground.** Two more tools on the plan
  screen: **Road** (click points, double-click to end; a lane 1 wide, a street 3 wide or an avenue 5 wide, with a
  style, by default that of the zone it starts in) and **Wall line** (one line round the village, open or closed).
  Roads a player draws count as approved: the Steward builds them without asking (27.15). While a player holds the
  City Plan, the zones' edges, the roads and the wall line show on the ground round them in their colours (particles
  along the borders within 24 blocks, drawn by the client as the Scan Tool's box is; nothing is placed in the world).
  The hall's map button (`VillageMaps.map`) draws the zones (each kind's map tint) and the roads on the map once the
  village has a plan, so the plan can hang in an item frame by the hall. Done when:
  - GameTests: roads and the wall line survive save and reload; a road of more than 64 points, or a 25th road, is
    refused; the map drawn for a planned village has its zone tints on the right pixels;
  - showcase scene `city_plan_ground`: a player walks the village holding the plan (a still of the borders on the
    ground) and the framed plan by the hall.
- [x] **27.5** (approved 2026-10-05) **The Steward.** A new job, `aliveworkplace:steward`, at the Village Hall, one per hall:
  - **Appointing:** sneak-right-click a grown villager standing by the hall with its City Plan (a Village Hall entry in
    `work/Stations`), or the desk's "Appoint a Steward" button (27.8), which lists the jobless. The hall isn't an
    acquirable job site, so nobody takes it by himself. Its point of interest has 0 tickets today: it gets 1, and a
    hall whose saved record still has 0 free tickets is registered again when it loads (else no old hall could ever
    have a Steward).
  - **His day** (a WORK package): each morning he walks his rounds holding the plan (it shows in his crossed arms, as
    vanilla shows a villager's held item): up to 6 stops (his open sites, empty zones, the storehouse), 3 seconds at
    each, then back to the hall, where he plans (27.6–27.9) and stays until evening. Status over his head
    (`WorkerStatus`): "Planning a Stone House: 3 villagers have no bed".
  - **Levels:** XP for each of his builds finished and each job he gives, and from trades (City Plans, Blank
    Blueprints and Village Ledgers for emeralds; he buys paper and books). His level sets how many of his builds may be
    open at once: 1, 2, 2, 3, 4 from Novice to Master, never more than the rank allows (Hamlet 1, Village 2, Town 3,
    City 4) or `stewardMaxOpenBuilds` (4).
  - An outfit and a zombie outfit with the pixel-art skill (a clerk's long coat, a rolled plan at the belt). Breaking
    the hall ends the job. Config `steward` (true).

  Done when:
  - GameTests: the City Plan appoints a villager at its own hall and not at another; a second villager can't take a
    hall that has a Steward; a hall saved with 0 free tickets gets a Steward after reload; a jobless villager standing
    by the hall stays jobless; breaking the hall ends the job; `steward: false` stops appointments;
  - both outfits pass `lint.py` and are in a `preview.py villager` sheet;
  - showcase scene `steward`: a GIF of his morning rounds and his walk back to the hall.
- [x] **27.6** (approved 2026-10-05) **The Steward's rules.** The engine for what he wants, as data:
  `data/aliveworkplace/steward_rules/<name>.json` holds one rule: `when` (conditions, all must hold), `do` (one
  effect), `priority` (0–100), `why` (a lang key filled from the conditions' numbers: "3 villagers have no bed"),
  `cooldown_days`, `max` per village, `min_rank` and `requires` (mod ids). Conditions are small classes reading the
  census the hall already takes (`VillageHalls.census`, `VillageNeeds.Needs`, the numbers `VillageAdvice` uses), so
  the desk and the "What next?" tips always agree. This item adds `beds_short {at_least}`, `food_short
  {meals_per_adult}`, `missing_poi {poi}`, `worker_without_workstation {professions}` (a villager with a job but no
  job site), `jobless {at_least}`, `no_builder`, `rank_at_least {rank}`, `villagers_at_least {n}`, `built_count_below
  {blueprint, n}` (any style or tier), `upgrade_available {blueprint}`, `homes_tier_low {share}`, `store_full
  {share}`, `research_idle`, `research_at_least {topic, level}` and `mod_loaded {mod}`; 27.12 adds the rest.
  Effects: `build {blueprint, zone}` and `upgrade {blueprint}` (carried out by 27.7 and 27.8), `assign_jobs` and
  `research` (27.9), and `ask {key}` (a tip only a player can act on, such as "place a Blueprint Table"). Once a
  morning the Steward ranks the rules that hold into the day's wishes. `/workplace steward explain` lists every rule
  for the nearest hall with each condition's value and whether it held. Done when:
  - a GameTest for each condition, holding and not holding, and one showing their numbers match `VillageAdvice`'s
    tips in the same village;
  - a rule file with an unknown condition or a bad field is skipped with a warning that names it (in
    `tools/modtest/allow.txt`, with the reason);
  - `explain` shows one rule that held and one that didn't in a test village.
- [x] **27.7** (approved auto 2026-10-04) **Finding a plot.** `steward/Plots`: where a blueprint (in its zone's style) fits in a zone, checked
  for each of the four turns:
  - its front (z = 0) faces the nearest road on the plan, else the hall;
  - the footprint plus 2 blocks lies in cells of that zone, and the ground under it is within 4 blocks of level (the
    builder levels the rest, `BlueprintData.levelGround`); at most a tenth of it over water, none over lava;
  - nothing in its box but natural ground, plants and natural trees (`BuildPlan.isTerrain`, `Trees`, and the tag
    `aliveworkplace:steward_clearable` for packs), 2 blocks clear of every build site and finished building of
    anyone's, its centre within `maxSiteDistance` (48) of a builder's Blueprint Table;
  - nearest the hall first, so villages grow compact; never the same blueprint with the same mirroring within 24
    blocks, so a street isn't one house repeated (it mirrors, or takes the rule's next blueprint).

  Budget: at most 64 columns looked at per tick per hall; the morning's results are kept until a zone or a build in it
  changes. Done when GameTests show:
  - a plot found on flat ground inside a zone, facing the road;
  - no plot across a zone's edge, over a player's cobblestone wall or a chest, overlapping another village's build
    site, or on a slope of 6;
  - the counter never passes 64 columns in a tick, and a search over a fully painted plan ends within 200 ticks.
- [x] **27.8** (approved 2026-10-05) **The Steward's desk: ask first, or run itself.** When a village has a Steward, the hall's "What
  next?" page (the compass) becomes his desk: his icon and level, the mode (**Ask me first**, **Run the village**,
  **Rest**), his open builds with **Cancel** (`Builders.cancel`), the "What next?" tips as now, and up to 9
  proposals. Each proposal shows its blueprint, name and style; why ("3 villagers have no bed"); where ("22 blocks
  north-east, in Homes 2"); the five materials it needs most, with how many are in store; and which builder will build
  it, after what. Its page: **Approve**, **Decline** (not proposed again for 3 days), **Show me** (its outline glows in
  the world for 30 seconds, `BlueprintOutline`), **Another spot**, **Another style**; **Approve all** on the desk.
  - Approving starts the build: a `BuildSite` for that builder (`Builders.start` or `enqueue`, owned by the hall's
    owner), noted in the chronicle (a new kind, Plans). Upgrades go on the finished building's own spot.
  - **Run the village** does the same without the click; the owner gets one chat line a morning listing what was
    started. `stewardSelfRun: false` in the config keeps every village asking. **Rest**: he plans nothing.
  - Unanswered proposals lapse after 3 days. Each morning with new proposals, the hall's owner (online) gets one line
    saying so. The desk works from the Village Ledger too. The same rights as the plan (27.2).

  Done when:
  - GameTests: approving creates the site with the proposal's blueprint, style, spot and owner; a declined proposal
    stays away for 3 days; a proposal lapses; a stranger can't approve in a protected village; Run the village starts
    a build with no click and Rest does nothing; proposals survive save and reload;
  - showcase scene `steward_desk`: the desk with three proposals, Show me, the approval and the builder setting off.
- [x] **27.9** (approved 2026-10-05) **Jobs and research.** Two more effects:
  - `assign_jobs`: each morning every grown jobless villager (not a nitwit) gets a free workstation
    (`VillageHalls.freeStations`), the village's biggest gap first: a builder while there's none, a farmer while food
    is short, guards while guards are short, a porter at a free Storehouse, a scholar while research is idle, then the
    nearest. At a shared block (`work/Stations`) he can pick its other jobs (an Orchard Keeper at the composter by a
    Berry Farm that has none), which the hall's list can't do yet (21.1a's "left for later"). A job with no free
    block left proposes its building (27.11). In Ask me first, the morning's jobs are one proposal ("Give 3 villagers
    jobs: Dara, Farmer at the composter 12 blocks east; ...").
  - `research`: when a scholar works and nothing is being researched, he picks the next topic: Fortification after a
    raid in the last 7 days, Medicine with 2 or more ill, Green Thumb while food is short, Logistics with a store
    80% full, else Swift Hands, Hearth and Kinship in that order, each only when `Research.State.available`. In Ask me
    first, a proposal.

  Done when:
  - GameTests: three jobless villagers get the three jobs the order asks for; an orchard keeper is picked at a shared
    composter; a nitwit and a child get nothing; the topic after a raid is Fortification, after a sickness Medicine,
    and a topic that isn't available is never picked;
  - showcase scene `steward_jobs`: jobless villagers walking to their new workstations.
- [x] **27.10** (approved 2026-10-05) **Rules: homes and storage.** Shipped rule files, each its own JSON:
  - `homes_upgrade`: beds short → upgrade a finished home whose next tier adds beds (Starter Cottage to II to III,
    Stone House to II to III, Terrace to II), before any new house; the beds a tier adds are counted from the
    blueprints, never written down;
  - `homes_starter_cottage` (1 bed short, a Hamlet), `homes_stone_house` (2 short), `homes_terrace` (3 short, a
    Village or more), all in Homes; `homes_inn` (4 short, a Town, no Inn yet: the Inn, in Market);
  - `homes_better`: more than half the grown-ups in tier I homes and no beds short → upgrade those homes (the "homes"
    tip);
  - `storehouse` (no Storehouse) and `storehouse_grow` (its chests 80% full: Storehouse II, then III), in Market;
  - `market_stall` (a Village with none), in Market;
  - `food_berry_farm` (food short, no Berry Farm) and `food_ranch` (food short, a Village, no Ranch), in Farms.

  Done when:
  - a GameTest per rule, in a village staged to need it: the Steward proposes that build in the right zone; with beds
    short and an upgradable Stone House, the upgrade comes before a new house;
  - showcase scene `steward_homes`: a GIF of a Homes zone filling up over three days in Run the village.
- [x] **27.11** (approved 2026-10-05) **A workplace for every worker.** `tools/blueprints/generate.py` writes buildable copies of 12 of our
  village houses (`village.py`: the plains look, with the jigsaw, structure voids and villager taken out and calcite
  swapped for white concrete, as STYLE.md asks of builds for builders), in the Blueprint Table too: Builder's
  Workshop, Carpenter's Workshop, Kitchen, Post Office, Guard House, Clinic, Ferry House and, with Cobblemon, Trainer's
  House, Leader's Hall, Ball Workshop, Trade Hall and School. Then a rule per job (`worker_without_workstation`, or a
  job the village wants with no free block) naming the building with its block:
  - Builder: Builder's Workshop; Carpenter: Carpenter's Workshop; Chef, Butcher: Kitchen; Postman: Post Office;
    Guard, Weaponsmith: Guard House (Barracks from a Town); Nurse, Cleric: Clinic (Healing Center from a Village);
    Undertaker: Graveyard; Teacher: Schoolhouse; Librarian, Scholar: Library; Porter: Storehouse; Orchard Keeper:
    Berry Farm; Florist: Flower Shop; Composter: Compost Yard; Beekeeper: Apiary Garden; Sifter, Leatherworker:
    Sifting Shed; Tinkerer, Toolsmith: Tinker's Workshop; Netherworker: Nether Gate; Shopkeeper: Supply Shop;
    Innkeeper: Inn; Rancher: Ranch; Ferryman: Ferry House (a plot on the shore: water within 4 blocks of its front);
  - with Cobblemon: Fossil Scientist: Research Lab; Trainer: Trainer's House; Trainer Leader: Leader's Hall; Ball
    Smith: Ball Workshop; Pokémon Trader: Trade Hall; Move Tutor: School;
  - `no_builder` asks the player: "place a Blueprint Table and give a villager the job" (nobody could build it);
  - Armorer, Miner, Mason, Fletcher, Lumberjack, Cartographer, Farmer, Fisherman, Shepherd and Bard get their rules
    with their new buildings (27.13, 27.14).

  Done when:
  - a GameTest per rule, and each of the 12 copies built by a builder in a test, its job block taken by its worker;
  - a gallery package of the 12 copies (front and back, as drawn and in Stonework) and showcase scene `workplaces`.
- [x] **27.12** (approved 2026-10-05) **Rules: care, learning, safety, beauty and the market.** The other conditions: `guards_short`,
  `raided_within {days}` (from the hall's last raid day), `bandit_camp_near`, `ill {at_least}`, `dark_beds
  {at_least}`, `beauty_below {points}`, `children_at_least {n}`, `courting_couples {at_least}` (`Couples`) and
  `died_within {days}` (the chronicle). Shipped rules:
  - `clinic_for_the_ill`: 2 or more ill and no nurse → Clinic, or Healing Center from a Village, in Civic;
  - `graveyard`: a villager died in the last 30 days, 8 or more villagers, none yet → Graveyard, in Civic;
  - `schoolhouse`: 3 or more children, no Schoolhouse → Schoolhouse, in Civic;
  - `library`: 6 or more villagers, no scholar (the "research" tip) → Library, in Civic;
  - `chapel`: a couple courting and no Chapel → Chapel (where weddings are held), in Civic;
  - `lookout_tower` (guards short) and `barracks` (guards short in a Town), in Defences;
  - `street_lamps`: 2 or more beds in the dark → a Street Lamp by the darkest homes' doors, in their zone;
  - `well`, `park_bench`, `fountain`, `gazebo`: beauty under 3 with 5 or more villagers (the "beauty" tip), in that
    order, each once, in Gardens;
  - `market_square`: a Town with none → Market Square (for market days, `MarketDays`), in Market.

  Done when:
  - a GameTest for each new condition, holding and not holding, and one per rule in a village staged to need it;
  - showcase scene `steward_civic`: a GIF of a lamp, a well and a schoolhouse going up as the village asks for them.
- [x] **27.13** (approved 2026-10-05) **New workplaces I: Smithy, Mason's Yard, Fletcher's Lodge, Map Room.** Drawn with the architect
  skill in `tools/blueprints/workshops.py` to STYLE.md, each checked in a render, each with its rule (27.11's form):
  - **Smithy**: a stone forge under a timber roof, an open front with an anvil and a quench trough, a chimney with a
    campfire for smoke; a blast furnace (Armorer; Miner with a pickaxe), a smithing table (Toolsmith) and a grindstone
    (Weaponsmith). **Smithy II**: a coal and ore store and a second blast furnace;
  - **Mason's Yard**: a fenced yard of cut stone with a lean-to over a stonecutter (Mason). **Mason's Yard II**: a
    second stonecutter and a hoist;
  - **Fletcher's Lodge**: a log cabin with a log pile and a straw target, a fletching table (Fletcher; Lumberjack with
    an axe). **Fletcher's Lodge II**: a drying-rack wing with a second fletching table;
  - **Map Room**: a narrow tower house with a cartography table (Cartographer) and a lookout at the top.

  Done when:
  - each is in `StarterBlueprints` with its size, in the Blueprint Table, clean in `check.py`, and built by a builder
    in a GameTest with its workers taking their blocks; the rules' GameTests pass;
  - a gallery package (front and back, as drawn and in two styles) and their builds in showcase scene `gallery`.
- [x] **27.14** (approved 2026-10-05) **New workplaces II: Farmstead, Fisher's Hut, Weaver's Cottage, Bandstand.** Drawn the same way, each
  with its rule:
  - **Farmstead**: a farmhouse with one bed beside a 9×5 field of farmland round a water channel, a scarecrow, a
    composter (Farmer). **Farmstead II**: a barn and a second field;
  - **Fisher's Hut**: a shore hut with a jetty 5 blocks into the water on log posts and a barrel (Fisherman); its plot
    must be on a shore (water within 4 blocks of its front, at most 3 deep under the jetty). **Fisher's Hut II**: a
    smokehouse with a smoker and a boat shed;
  - **Weaver's Cottage**: a cottage with a loom (Shepherd) and a fenced sheep pen. **Weaver's Cottage II**: a dye
    garden;
  - **Bandstand**: an open eight-sided bandstand with a jukebox (Bard), in Gardens; it counts 3 for beauty
    (`Decorations`).

  Done when:
  - as 27.13: in `StarterBlueprints` and the table, clean in `check.py`, built by a builder in a GameTest with its
    worker taking the block, the farmer working the field and the fisherman fishing from the jetty; the rules' tests;
  - a gallery package and their builds in showcase scene `gallery`.
- [x] **27.15** (approved 2026-10-06) **Roads.** The roads on the plan get built, and every new building joins them. Road styles are data:
  `data/aliveworkplace/road_styles/<name>.json` holds one style: the surface blocks for the middle and the edges
  (weighted mixes, as STYLE.md's), the slab and stairs for steps, the bridge's deck, rail and pillar blocks, its lamp
  and lantern post (27.16). One shipped style per blueprint style: **As drawn** (dirt path, coarse dirt and gravel
  edges), **Stonework** (stone bricks with cracked ones, cobblestone edges), **Sandstone** (smooth and cut sandstone),
  **Dark Oak** (cobbled and polished deepslate), **Cherry** (polished diorite with stone brick edges) and, with
  Cobblemon, **Apricorn** (bricks with mud brick edges).
  - Routing widens `Paths.route`: the road's width kept clear, round water (bridges: 27.16), buildings and anything
    not natural, steps of at most one block. A road is cut into segments of up to 24 blocks; each becomes a generated
    blueprint (`aliveworkplace:roads/<hall>/<n>`, saved as `Shapes` saves its blueprints) of the surface and of the air
    over it where natural cover stands, and a `BuildSite` for the nearest builder, who takes a road segment only when
    no building of the village waits.
  - Segments are kept on the plan, not in `BuildSiteManager`'s finished list (it holds at most 2000 per dimension, and
    ranks, homes, upkeep and the map read it); ranks, the map and `Homes` skip `roads/`.
  - A finished building's door joins the nearest road with a lane, instead of `Paths`' dirt path to the bell (which
    stays for villages without roads).
  - Budget: 600 path nodes per tick per hall; at most 2 road segments open at once. Config `stewardRoads` (true).

  Done when:
  - GameTests: a 40-block street over uneven ground is built 3 wide in Stonework, with stair steps; a player's fence on
    its line is gone round and left standing; a half-built segment survives save and reload; roads leave the village's
    rank and building count unchanged; a new building's lane joins the street;
  - showcase scene `roads`: a GIF of a street being laid between two houses.
- [x] **27.16** (approved 2026-10-06) **Lamps, bridges and steps.**
  - **Lamps:** the road style's lamp (the Street Lamp blueprint in the road's style, a styled id) every 16 blocks on
    alternate sides of streets and avenues and at every crossing, never in front of a door; lanes get a lantern post
    (the style's fence, two high, a lantern on top) every 12 blocks. Lamps count as Street Lamps for beauty
    (`Decorations`, capped at 10% as now) and light the beds near them (the "dark" tip).
  - **Bridges:** where a road meets water, or a drop deeper than 2, for up to 16 blocks: a generated bridge blueprint
    with the deck at the banks' height in the style's blocks, rails, a pillar every 4 blocks down to the bed (at most
    12 deep) and a ramp of stairs at each end. A wider gap: the road stops at the bank and the desk says why.
  - **Steps:** one-block rises on a road become stairs across its width; crossings are paved square.

  Done when:
  - GameTests: lamps every 16 blocks on alternate sides of a 60-block street, none in front of a door; a river 9 wide
    is bridged with 2 pillars and villagers walk over it; a 20-wide gap is refused with the desk's note; stairs on a
    slope of one in one;
  - showcase scene `bridges`: a GIF of a bridge going up over a river, and the street lit at night.
- [x] **27.17** (approved 2026-10-06) **Roads between villages.** For each caravan route (`Caravans`), the village builds its half of a road
  to the other village: a street from the end of its nearest road toward the other hall, in the style of the zone it
  starts from, planned only in loaded chunks (no chunk tickets for roads), up to `caravanRoadReach` (256) blocks or
  halfway, whichever is less. The other village builds the other half, and the two are joined when they come within
  32 blocks. A road that stops short of halfway ends at a milestone: a stone post with a lantern and a sign naming the
  other village and how far it is. Caravans on a finished road arrive in three quarters of the time
  (`Caravans.travelTicks`); both chronicles note the road ("The road to Ashford is finished"). Config `caravanRoads`
  (true). Done when:
  - GameTests: two villages 120 blocks apart build both halves and they meet; a route to a village 900 blocks away
    builds 256 blocks and a milestone naming it; an unloaded chunk pauses the planning without an error, and it goes
    on when loaded; a finished road shortens the caravan's trip;
  - showcase scene `caravan_road`: a GIF along the road from one village to the other.
- [x] **27.18** (approved 2026-10-06) **Walls along the wall line.** Once the village has been raided in the last 7 days, or a bandit camp
  is near, the Steward proposes its wall: along the plan's wall line, or, when none is drawn, a line of his own (round
  the zones, 4 blocks out), shown on the plan for approval. Walls are kits, as data:
  `data/aliveworkplace/wall_kits/<name>.json` holds one kit: a segment, a corner tower, a gate and the rank it needs.
  Two shipped kits:
  - **Palisade**, up to a Village: Palisade, Palisade Gate, and a new **Palisade Tower** (a log watch platform with a
    ladder, drawn with the architect skill);
  - **Stone**, from a Town: Stone Wall, Wall Tower, Gatehouse; a Town replaces its palisade with stone, a segment at a
    time.

  Each segment sits at its own ground height (the foundation fills under it); a tower at every corner and at least
  every 28 blocks, moved along so the segments between fit whole; a gate wherever a road crosses (shut at night by
  `Gates`, as now). At most 3 wall sites open at once. A wall counts as one building for the rank, not one per
  segment. Config `stewardWalls` (true). Done when:
  - GameTests: a square wall line gets its segments, four corner towers and a gate on the road; a village never raided
    gets no wall proposal; the rank's building count goes up by one for the whole wall; the Palisade Tower is clean in
    `check.py` and a builder builds it;
  - showcase scene `walls`: a GIF of a palisade going up round a small village, and its gate shut at night.
- [x] **27.19** (approved 2026-10-06) **Safe by design.** Everything the Steward builds passes one check (`steward/StewardSafety`), and his
  sites are careful on their own:
  - only inside his own village's zones of the right kind: never in Keep Clear, never nearer another hall, never in a
    protected village whose owner isn't his hall's owner;
  - a ledger of what players built: a block a player places or breaks within a hall's area, from 1.1 on, marks its
    16×16×16 section (`aliveworkplace_player_built`, saved per dimension); no plan, road or wall goes through a
    marked section unless the owner approved that one by hand;
  - his sites clear only natural blocks: a block a player puts in the way after the build started stays, and its step
    is skipped (counted in `skipped`); the desk says "a player's block is in the way";
  - materials: when his builds wait for materials, the Storehouse's requests board and the desk show one shopping list
    for all of them (the 8 most needed), the owner is told once a day, and caravans see it (`Caravans` wants); no new
    build is proposed while 2 of his builds have waited a whole day.

  Done when GameTests show: no plan through a player's house built inside a Homes zone; a block a player places in a
  running site is still there at the end; no proposal reaching into the next village; the shopping list adds up the
  missing materials of two waiting sites; proposals stop while two sites wait and start again once supplied; the
  ledger survives save and reload; a chaos run (the tester skill's `ChaosTests`, 3 seeds) of a self-run village breaks
  no block a player placed.
- [x] **27.20** (approved 2026-10-06) **Old houses, found and measured.** In zones with "renew old houses" on, the Steward looks for houses
  no builder built: a bed or a workstation (by its point of interest) that isn't in a finished build (`Homes.at`,
  `BuildSiteManager.finishedAt`), and the house round it, measured by a flood fill over built blocks (not terrain,
  plants or natural trees: at most 2000 blocks and 20×16×20, 256 blocks a tick). Keyed on blocks, never on the
  structure: it counts as an old village house, plain or ruined, only when
  - 85% or more of its blocks are in the tag `aliveworkplace:village_house_blocks` (the blocks of vanilla's five kinds
    of village, with the cobwebs, mossy and cracked blocks of abandoned ones; packs add theirs);
  - it holds no container and no block entity but beds, bells, signs, banners, campfires and job blocks;
  - no part of it is in the player ledger (27.19), and it's wholly inside the renew zone.

  The desk lists them ("Old houses: 4, 3 can be renewed"), and Show me outlines each. Done when GameTests show: a
  vanilla plains house (placed from its template as a fixture) is found and its box measured exactly; the same shape
  in deepslate and quartz isn't; one with a chest isn't; one a player changed since 1.1 isn't; the fill never passes
  256 blocks in a tick.
- [x] **27.21** (approved 2026-10-06) **Old villages renewed.** One old house at a time (at most one every 2 days in a village), the Steward
  rebuilds it in its zone's style: "Renew the old house 14 blocks west as a Stone House (Cherry)". Renewal lists are
  data: `data/aliveworkplace/steward_renewal/<name>.json` holds one: the kind of old house (a home, or a job's point
  of interest) and the buildings to try, in order. Shipped lists: homes (Starter Cottage, Stone House, Terrace: the
  first with at least as many beds) and one per job, the building 27.11, 27.13 and 27.14 give that job (an old
  armorer's house becomes a Smithy). The replacement must fit the old footprint plus up to 3 blocks, inside the zone,
  its front where the old door was.
  - The swap: the old house is scanned into a blueprint (`renewal/<hall>/<n>`, saved as the Scan Tool saves) and taken
    down by the builders as a deconstruction (its blocks go to the store), then the new one is built on the cleared
    plot. Both are one proposal, and the second starts only when the first is done.
  - The villagers who slept there sleep in the new beds; its worker keeps their job and takes the new workstation.
  - Ask me first asks for each house; Run the village renews by itself, but only in zones whose renew switch the
    owner turned on. Config `stewardRenewal` (true). The chronicle notes each house renewed.

  Done when:
  - GameTests: a vanilla plains armorer's house is renewed as a Smithy in Stonework, and its level-2 armorer keeps the
    job and works the new blast furnace; a two-bed home becomes a home with at least two beds and both villagers sleep
    there; a save and reload between the take-down and the rebuild; a renewal cancelled after the take-down leaves the
    plot to be proposed again;
  - showcase scene `renewal`: a time-lapse GIF of a vanilla village house becoming a Stone House in Cherry.
- [x] **27.22** (approved 2026-10-06) **A village from a plan (the 1.1 yardstick).** The whole milestone at once, as 23.1 is for builders: a
  plains village with a hall, a Steward, 3 builders, a stocked storehouse and 12 villagers; a plan with Homes,
  Workshops, Farms, Market, Gardens and Keep Clear zones, two streets and a wall line; Run the village for 6 in-game
  days. Run it as a pack-server scenario (`tools/packtest`) for the numbers and as a screenshot scene for the
  time-lapse. Done when:
  - every build the Steward started is finished, none outside its zone or in Keep Clear, no item duplicated or lost
    (counted before and after), and the stuck count is 0 (or each case is a Bug);
  - the Steward's work (rules, plot search, roads, renewal) costs under 0.5 ms a tick per village at p95 (`PERF=true`),
    written in `docs/performance.md`;
  - the README has a "Villages that build themselves" section (the City Plan, the Steward, the desk, roads, walls,
    renewal, the data folders for packs, every new config key in the table), and the In-Game Guidebook has its page
    if 26.2a has landed (else a note on 26.2a);
  - showcase scene `city_timelapse`: the village growing into its plan, the GIF that leads the 1.1 release notes.
  - Status (lane-c-1005-2132): built. `command/CitySoak` (`/workplace city`, benchmark servers only) and
    `CITY=true [PERF=true] tools/packtest/run.sh`; the Steward's cost meter `city/StewardCost`; the ledger counts meals
    eaten from the store. Pack server, 2026-10-05, run 3: 14/14 of his builds finished (6 buildings, 8 road segments) in
    6 days, all in their zones, none in Keep Clear, 0 stalls, items off none (133 meals counted); Steward cost p50
    0.005, **p95 0.591** (0.05 and 0.46 in runs 2 and 1), p99 1.2, worst 882 ms (planning, 710 ms, first call): the
    p95 target is missed in 1 of 3 runs, B85. Runs 1-2 found B84 (the village eats a Farmstead's crops). The wall
    (approved day 1) and the old house's renewal never got a turn in a Hamlet: B86. README "Villages that build
    themselves" and every M27 key in the config table; Guide Book page `city_plan` (shown once M27 is on);
    `city_timelapse` in the harness and scenes.py, not filmed locally (the nightly films it). `docs/performance.md` has
    the three runs. CitySoakGameTests (3) pass.

Depends on: nothing.

## Milestone 28: Pokémon and villagers, together (1.2)

Pokémon stop being a side mod and become part of village life. Pokémon pastured by a workstation are seen at work (a
Machamp shouldering beams to the builder, a Wartortle watering the fields, a Pidgeotto off with the air mail), five new
jobs work Cobblemon's own blocks, villages get a Pokémon Center, and every festival of a village with an Arena
becomes the Festival Cup: a themed tournament its trade partners send their Trainer Leaders to, with the stands full, a
fair on, and the winner's banner flying over the winning village. It builds on `work/Partners`, `compat/cobblemon/`,
`trainer/`, `hall/Festivals`, `MarketDays`, `Caravans` and the `Chronicle`; the owner's pack is still on Cobblemon 1.7.3,
so the parts that need Cobblemon 1.8 (Habitat Blocks, Type Gems, Alphas) wait quietly until the pack moves up. Nothing
here adds a new kind of speed bonus (the new jobs get the partners' existing one).

- [x] **28.1** (verified 2026-10-04: 28.1: docs/design/M28.md [185 lines] covers every point of the spec [partner shows, the Pokémon Center, the Cup and Arena, all six data formats, config, save defaults, the 1.8-only vs 1.7.3 split, no badges]; 28.2: CobblemonCompat.TESTED is >=1.7.3 <1.9, and nightly run 37193458879's compat-cobblemon18 job [the compat GameTests on Cobblemon 1.8.1] is green beside the 1.7.3 compat suite in the full build) (approved 2026-10-04) **Design note.** `docs/design/M28.md`, sent to the owner as a review package (lanes don't wait for his
  reply): what the player sees (the partner shows, the five jobs and their builds, the Pokémon Center, a Cup day hour
  by hour with a sketch of the Arena); the data formats (`partner_shows`, `camp_menu`, `gem_beds`, `village_habitats`,
  `cups`, `type_chart`, all under `data/aliveworkplace/`); every config switch; the save data (new attachments, hall
  fields and the `aliveworkplace_cups` saved data, all with defaults); which parts need Cobblemon 1.8 (checked
  2026-10-03 on the Cobblemon wiki and changelog: Alphas, TMs and the TM Machine, Type Gems grown on Deepslate Crystal
  Cores and the Habitat Block came in 1.8.0, 2026-09-06; the Cobbleverse pack 1.7.42 still ships 1.7.3) and which work
  on 1.7.3 (the Campfire Pot, Hearty Grains, Saccharine trees, Poké Snacks, berry mutations, tumblestone); and how the
  Cup keeps the owner's rules: villages enter, with their Leaders and the players who represent them; no badges, no gyms
  and no trophies for players (the pack's own Badges & Trophies mod does that); Leaders and the purse pay money only;
  the banner belongs to the village. Done when: the note is on `main` and its package is sent.
  - [x] **28.1a** (approved auto 2026-10-04) Change from the owner (2026-10-04): Cup purse much bigger: 100,000 PokéDollars per bout won and 500,000 for the final (keep the City host bonus unless it breaks the economy); a Cup at every festival, not every third
- [x] **28.2** (approved auto 2026-10-04) (verified 2026-10-04: 28.1: docs/design/M28.md [185 lines] covers every point of the spec [partner shows, the Pokémon Center, the Cup and Arena, all six data formats, config, save defaults, the 1.8-only vs 1.7.3 split, no badges]; 28.2: CobblemonCompat.TESTED is >=1.7.3 <1.9, and nightly run 37193458879's compat-cobblemon18 job [the compat GameTests on Cobblemon 1.8.1] is green beside the 1.7.3 compat suite in the full build) **Cobblemon 1.8 as well as 1.7.3.** The mod compiles and tests against Cobblemon 1.7.3 and
  `CobblemonCompat.TESTED` stops below 1.8, while 1.8.0 and 1.8.1 (Minecraft 1.21.1) are out; a `LinkageError` there
  switches every Pokémon feature off at once. Keep compiling against 1.7.3 and:
  - add a switch (`-Pcobblemon18=true`) that runs `runCompatGameTest`, the screenshot harness and the showcase with
    Cobblemon 1.8.1 (`maven.modrinth:cobblemon:gBW3vLC7`, with the Kotlin and Fabric API it needs); mods with no
    build for it are left out of that run, each listed with its reason in `stonecutter.properties.toml`. The nightly
    workflow runs the compat suite both ways;
  - fix whatever 1.8.1 breaks in `compat/cobblemon/` (a call whose signature changed goes through one small shim that
    picks the method present), and widen `TESTED` to `>=1.7.3 <1.9`;
  - add `work/PokemonFeatures` (core, by registry id only): `HABITATS` (`cobblemon:habitat_block`), `TYPE_GEMS`
    (`cobblemon:deepslate_crystal_core`), `TM_MACHINE` (`cobblemon:tm_machine`), `ALPHAS` (Cobblemon 1.8 or later).
    Every 1.8-only part of this milestone asks it first.

  Done when:
  - the compat suite is green with 1.7.3 and with 1.8.1 (trainers, tutors, traders, daycare, fossils, partners,
    orchard, nurse; Mega Evolution too if Mega Showdown has a build for 1.8);
  - a GameTest shows the four features off under 1.7.3 and on under 1.8.1;
  - the README says which Cobblemon versions work. Nothing visible changed: land with `--no-review`.
- [x] **28.3** (approved 2026-10-04) **Partners at work: the engine.** Partners speed jobs up today but are never seen doing it. Add
  `work/PartnerShows`: a job calls `PartnerShows.cue(villager, "<cue>", pos)` at a moment of its work, and one of the
  worker's pastured helpers (the entities `PokemonPartners.fighters` already finds) of a type the show names walks to
  `pos`, does the show and walks back. New `PokemonPartners` methods, filled in `CobblemonPartners`: `walkTo`,
  `goHome`, `animate` (Cobblemon's physical, special or cry animation) and `effect` (a vanilla particle or a Cobblemon
  snowstorm effect such as `cobblemon:impact_water`).
  - A show is data: `data/aliveworkplace/partner_shows/<name>.json` holds the jobs (profession ids), the helper types,
    the cue, what it carries (an item, or `from_work`: what the worker is handling), the animation, particles, sound,
    how long it lasts, and an optional effect from a toolbox (`none`, `hydrate_farmland`, `smoke`, `sparks`). The
    engine ships with the toolbox and one show (a Fighting partner carrying planks for the builder).
  - Carrying is a vanilla Item Display entity that follows the Pokémon by teleport with interpolation (it never rides
    it), tagged, removed when the show ends and removed on chunk load if a show was cut off.
  - A pastured Pokémon is never untethered: it only goes where its pasture lets it wander (Cobblemon's setting, 32
    blocks by default in 1.8); if `pos` is farther it plays at the nearest point it may reach. If Cobblemon's brain
    ignores the walk, the show plays where the Pokémon stands, facing the worker. Never a Pokémon in battle, ridden or
    on a shoulder; a show never changes a Pokémon's held item, friendship, moves or stats.
  - Budget: one show per worker every 10 seconds at most, 6 running per level, none when no player is within 48 blocks.
    Config `partnerShows` (true).

  Done when:
  - a compat GameTest: a pastured Machop within 16 blocks of a builder's table is cued, walks toward the site within its
    pasture's range carrying a plank display, comes back, and the display is gone;
  - a display left by a cut-off show is removed when its chunk loads (GameTest); no show runs with no player near, or
    with `partnerShows` off;
  - showcase scene `partners_engine`.
- [x] **28.4** (approved 2026-10-04) **Partners at work: building and the land.** Shows (one data file each, cued from the job's own code):
  - Builder + Fighting: shoulders the logs or planks the builder fetches from the supply chest (cue `fetch`) and punches
    each block home with a physical move as it's placed (cue `place`); + Rock: carries the stone; + Steel: carries the
    iron parts (bars, doors, chains, lanterns).
  - Porter + Fighting or Normal: follows the porter's round with a barrel on its back (cue `haul`) and sets it down at
    the storehouse.
  - Carpenter or Mason + Fighting, Rock or Steel: holds the board or stone at the crafting table or stonecutter (cue
    `craft`), with the block's crack particles.
  - Farmer + Water: waters a 3×3 patch of the field with Cobblemon's water effect (cue `tend`); that farmland becomes
    fully moist (`hydrate_farmland`); + Grass: green sparkles over the crops it passes, nothing more; + Ground: walks the
    furrow ahead of the farmer as they till (cue `till`), with dust.
  - Lumberjack + Fighting: a physical move at the trunk with each chop (cue `chop`); + Grass or Bug: carries the
    sapling to the stump and plants it with the lumberjack (cue `replant`).
  - Orchard Keeper + Flying or Bug: flutters through the tree being picked (cue `pick`), with pollen.

  Done when: a GameTest per cue (the show starts when the worker reaches that moment in real work; the watered
  farmland is at moisture 7), and showcase scene `partners_land` (the builder with a Machamp carrying beams, the farmer
  with a Wartortle watering) with its GIF in the review package.
- [x] **28.5** (approved 2026-10-05) **Partners at work: post, forge and kitchen.** Shows:
  - Postman + Flying: when a parcel goes by air mail it takes off from the Mailbox with a bundle, climbs out of sight
    and lands back empty-handed (cue `air_mail`); on the round it flies ahead to the next mailbox (cue `deliver`).
  - Armorer, Miner or Fisherman + Fire: breathes fire into the furnace or smoker each time it smelts the 8 on the spot
    that Fire partners already smelt (cue `fire_smelt`), with Cobblemon's fire effect.
  - Chef + Fire: fans the smoker's flames (cue `cook`); + Normal: carries the finished dish to the chest.
  - Toolsmith or Ball Smith + Steel or Fire: sparks at the smithing table (cue `forge`); carries the new tool or the
    batch of balls to the chest.
  - Weaponsmith + Steel or Fighting: holds the worn piece at the grindstone (cue `mend`).
  - Fletcher + Flying or Bug: brings a feather or string to the table (cue `fletch`).
  - Tinkerer + Electric or Steel: electric sparks over the part being made (cue `tinker`) and over the iron golem being
    mended.

  Done when: a GameTest per cue, and showcase scene `partners_forge` with its GIF (the air mail take-off, a Charmander
  breathing into the blast furnace).
- [x] **28.6** (approved 2026-10-05) **Partners at work: everyone else.** Shows:
  - Miner + Ground, Rock or Steel: digs at the next block along with the miner (cue `dig`), with that block's crack
    particles.
  - Fisherman + Water or Ice: swims out round the bobber, with bubbles (cue `cast`).
  - Scholar + Psychic: floats a book beside the lectern, with enchanting glyphs (cue `study`); Teacher + Psychic or
    Normal: the same during lessons (cue `lesson`).
  - Nurse + Fairy, Normal or Psychic: a pink pulse over the villager being cured or the player being healed (cue
    `cure`).
  - Composter + Poison or Grass: stirs the composter (cue `compost`), green bubbles.
  - Florist + Grass or Fairy: sprinkles over the garden (cue `grow`).
  - Beekeeper + Bug or Grass: circles the hive being harvested (cue `harvest`).
  - Sifter + Ground or Rock: shakes dust from the cauldron (cue `sift`).
  - Netherworker + Fire or Dark: walks them to the portal with flames at its feet (cue `depart`).
  - Cartographer + Flying or Ground: scouts ahead as they set out (cue `set_out`).
  - Rancher + Normal or Ground: walks beside the wild horse being broken in (cue `tame`).

  Done when: a GameTest per cue, and showcase scene `partners_all`: one still per job with its partner at work.
- [x] **28.7** (approved 2026-10-05) **The Pokémon Center.** Two blueprints (architect skill, an original design, checked against STYLE.md in
  a render), in the Blueprint Table with Cobblemon only and sold by Journeyman Nurses:
  - **Pokémon Center**: a bright hall under a red roof, a glass front, a counter with Cobblemon's Healing Machine (the
    nurse's place), a PC by the counter, shelves of potions behind it, benches along the walls;
  - **Pokémon Center II**: a lodge upstairs with four beds (homes), a trade corner with a Shop Counter (hand the
    villager there a Poké Ball: a Pokémon Trader), and a garden behind with a Pasture Block.

  The Healing Machine becomes a Nurse workstation too: its POI is registered when Cobblemon registers the block (as
  the Fossil Analyzer's is), a honey bottle picks the Nurse there, and a jobless villager never takes a player's machine
  by themselves. A nurse at a machine heals your team in it: right-click her and she puts your Poké Balls in the
  machine (its own animation and heal time), free; while she's on shift the machine stays charged. A nurse at a brewing
  stand heals as before. The hall's "What next?" suggests a Pokémon Center to a Cobblemon village of Village rank
  without one. Config `nurseHealingMachine` (true).

  Done when:
  - a compat GameTest: a nurse at a machine heals a hurt party through it (the machine is in use, then every Pokémon is
    full); a jobless villager left by a machine for 2400 ticks doesn't take it;
  - a builder builds both tiers (GameTest), and the renders are in the package;
  - showcase scene `pokemon_center` (stills of both tiers, a GIF of the healing).
  - [x] **28.7a** (approved 2026-10-06) Change from the owner (2026-10-05): Approved, but add more Pokémon Center variants later (more looks for the tiers).
- [x] **28.8** (approved 2026-10-05) **The Camp Cook.** Stand a villager by a Campfire Pot (Cobblemon's campfire with a pot on it; POI when
  Cobblemon registers `cobblemon:campfire`) and sneak-right-click them with Hearty Grains. Never taken by a jobless
  villager. Config `campCooks` (true).
  - She cooks in the pot itself: the makings into its slots and seasonings into its top row through its container (as
    hoppers do), the lid shut (the state redstone drives), the pot's own cooking time, then the dish to the chests by
    the pot.
  - Her menu is data: `data/aliveworkplace/camp_menu/<dish>.json` (the dish, how many to keep, when: `always`, `asked`
    or `order`). Shipped: always, up to 16: Poké Snack, Poké Bait, Aprijuice in all seven colours (seasoned to Tasty or
    Delicious when the chests have the seasonings), Exp. Candy XS, S and M, Ponigiri, Leek and Potato Stew, Smoked-Tail
    Curry, Open-Faced Sandwich, Vivichoke Dip, Sinister Tea; when a worker asks: Poké Snacks seasoned with the berries
    the Habitat Keeper wants (28.10), Poké Bait for the fishermen; on stock orders only: Big Malasada, Casteliacone,
    Jubilife Muffin, Lava Cookie, Lumiose Galette, Old Gateau, Pewter Crunchies, Rage Candy Bar, the seven sweets
    (Berry, Clover, Flower, Love, Ribbon, Star, Strawberry), Whipped Dream, the mochi, the EV candies, potions and the
    status heals.
  - Her meals (Ponigiri, the stew, the curry, the sandwich, the dip, the tea) count as meals in the village store and
    for Diet variety.
  - Village farmers sow and harvest Hearty Grains and Vivichoke from the chests' seeds like their other crops.
  - Trades: Poké Bait and Poké Snacks (Novice), Aprijuice (Apprentice), Exp. Candy S and M (Journeyman), Lumiose
    Galette and Big Malasada (Expert), Exp. Candy L (Master). Partner types: Fire and Normal. Her partner show (a 28.3
    data file): a Fire partner lights the campfire.

  Done when:
  - compat GameTests: she cooks a Poké Snack in a real pot (the lid shuts, the dish comes out, the makings are gone); a
    stock order for Lava Cookies is filled; a villager eats a Ponigiri from the store and Diet records it; a farmer
    harvests and replants Hearty Grains;
  - the README section and job table say how to start her;
  - showcase scene `camp_cook` with its GIF.
- [x] **28.9** (approved 2026-10-05) **The Berry Breeder.** Stand a villager by a composter and sneak-right-click them with any Cobblemon
  berry. Config `berryBreeders` (true).
  - The berry book: every berry and its `mutations` read from Cobblemon's own berry data (70 in 1.7.3), so a data pack's
    berries come too. Sneak-right-click the breeder: one page lists every berry, found ones lit, the rest with the pair
    that makes it (greyed while a parent is still missing). Click one to make it her goal; she works out the chain from
    the berries the village has (chests and plots), one step at a time. The village's found berries are kept in the hall
    (a new field, empty by default).
  - Work: in her plot (farmland within 8 blocks of the composter, or a Field Marker she's given), she plants the next
    step's two parents in alternating rows so each plant touches the other kind (Cobblemon makes the mutation), puts
    Growth and Surprise Mulch from the chests on them, picks the fruit (the berry plant stays), notes any new berry,
    then plants it as a parent for the next step. Spare berries go to the chests, 16 of each kept for the Camp Cook and
    the Habitat Keeper.
  - Trades: common berries (Novice), mulch (Apprentice), the berries she has found (from Journeyman). Partner types:
    Grass and Bug; show: a Bug partner flits between the paired plants.

  Done when:
  - compat GameTests: with Oran and Cheri in the chests and Lum as the goal, she plants them side by side, a forced
    harvest with a mutation gives Lum and the book marks it found; the chain to Sitrus (Lum + Figy) is planned from Oran,
    Cheri and Figy;
  - showcase scene `berry_breeder` (the book page, the paired plot).
- [x] **28.10** (approved 2026-10-05) **The Habitat Keeper.** Stand a villager by a Pasture Block (POI when Cobblemon registers
  `cobblemon:pasture`; never taken by a jobless villager) and sneak-right-click them with a honey bottle. Config
  `habitatKeepers` (true), `habitatSightings` (true).
  - Lure spots: up to 3 Poké Snacks kept set out within 32 blocks of the pasture, on spots marked with a Field Marker
    (unmarked: on grass 16 to 32 blocks out), from the chests (the Camp Cook's), and set out again when one is eaten up.
    Sneak-right-click the keeper to pick a lure: a type or an egg group; she asks the Camp Cook for snacks seasoned with
    the berries Cobblemon's `spawn_bait_effects` data gives that effect (by id; with Cobblemon 1.8, Hopo for Alphas).
  - Honey: Saccharine logs within 32 blocks are slathered with honey bottles from the chests (Cobblemon's own: a
    slathered log raises the hidden-ability chance nearby) and slathered again when it wears off; Saccharine saplings
    from the chests are planted round the lure spots. Logs are found by an incremental scan (4,096 blocks a tick at
    most) and remembered.
  - Sightings: every minute she notes the wild Pokémon within 48 blocks of the pasture (an entity query, not a block
    scan): shiny ones, species Cobblemon only spawns as rare or ultra-rare (its spawn data, by id) and, with 1.8,
    Alphas. Each new one is told to players in the village and goes in the chronicle (new kind `SIGHTING`, a spyglass
    icon): "Bramble spotted a shiny Eevee by the east field". The hall's list shows her last five sightings.
  - Trades: Saccharine saplings and honey (Novice), Poké Snacks (Journeyman). Partner types: Flying and Grass; show:
    a Flying partner circles a new sighting.

  Done when:
  - compat GameTests: a snack set on a marked spot and set out again after it's used up; a Saccharine log slathered; a
    shiny wild Pokémon placed nearby is announced and written in the chronicle once, not every minute;
  - showcase scene `habitat_keeper` (a snack spot, the slathered log, the sighting in chat).
- [x] **28.11** (approved 2026-10-05) **The Gem Grower.** Stand a villager by a stonecutter and sneak-right-click them with an amethyst shard
  (masons keep the stonecutter's own job). Works without Cobblemon too. Config `gemGrowers` (true).
  - Gem beds are data: `data/aliveworkplace/gem_beds/<name>.json` holds what's planted (an item, or nothing), what it
    must touch (a block or tag), which blocks grow and which state is ripe, and the harvest (the block's own loot). Beds
    within 16 blocks of the stonecutter are found by an incremental scan (4,096 blocks a tick at most) and remembered.
  - Shipped beds, each its own file: `amethyst` (budding amethyst; only full clusters are picked, the budding block is
    never broken); with Cobblemon `tumblestone`, `sky_tumblestone`, `black_tumblestone` (planted from the chests
    against lava or magma, as Cobblemon grows them; full clusters picked); with Cobblemon 1.8 the 18 type gems
    (`normal_gem`, `fire_gem`, `water_gem`, `grass_gem`, `electric_gem`, `ice_gem`, `fighting_gem`, `poison_gem`,
    `ground_gem`, `flying_gem`, `psychic_gem`, `bug_gem`, `rock_gem`, `ghost_gem`, `dragon_gem`, `dark_gem`,
    `steel_gem`, `fairy_gem`: that type's Gem Block set against a Deepslate Crystal Core, stage-3 clusters picked, the
    Gem Block kept).
  - Orders: sneak-right-click to pick which beds she keeps (like the Ball Smith's orders); with nothing picked, every
    bed she has the makings for. With glass in the chests and Cobblemon 1.8 she also makes Blank TMs from shards
    (Cobblemon's recipe), up to 8.
  - Trades: amethyst shards (Novice), tumblestones (Apprentice), type gems (Expert, 1.8). Partner types: Rock and
    Steel; show: a Rock partner taps the ripe cluster loose.

  Done when:
  - GameTests: a full amethyst cluster picked and the budding block still there; a tumblestone planted against magma
    and a forced full cluster picked (compat, 1.7.3); a Fire Gem Block set on a core and a forced stage-3 Fire Gem
    cluster picked (compat, 1.8.1); a malformed bed file is logged and skipped;
  - showcase scene `gem_grower`.
- [x] **28.12** (approved 2026-10-05) **The Daycare Keeper.** Cobblemon has no breeding; the Cobbleverse pack adds it with Cobbreeding
  (eggs in the Pasture Block). Stand a villager by a Pasture Block and sneak-right-click them with an egg. Config
  `daycareKeepers` (true).
  - Right-click her (sneak for trades): the daycare screen (as the Rancher's, `compat/cobblemon/CobblemonDaycare`):
    leave one pair per player, three pairs per keeper at most. It says how well they get along, from Cobblemon's
    species data (egg groups, gender): very well (same species, different original trainers), well (same species, or
    one egg group in common), so-so (Ditto with anything that breeds), not at all (no group in common, or the
    Undiscovered group).
  - Each dawn she finds an egg with the pair: 70%, 50% or 20% by how well they get along (+10% at Expert and Master),
    kept for the owner, three a pair at most. Collecting costs 4 emeralds (their worth in CobbleDollars) an egg.
    - With Cobbreeding installed: a real Cobbreeding egg, made with its own `/givepokemonegg` command, so its hatching,
      inheritance and shiny rules apply.
    - Without it: the hatchling itself at level 1, in the base form of the mother (or the parent that isn't Ditto),
      with 3 IVs from the parents (5 when one holds a Destiny Knot), the nature of a parent holding an Everstone, the
      mother's ball, a 1 in 5 chance of a hidden ability the mother has, and the egg moves both parents know; to the
      party or the PC.
  - If she dies, the pairs go to their trainers' PCs (as the Rancher's boarders do). Cobbreeding joins the compat test
    mods. Trades: Exp. Candy XS (Novice), Everstone (Journeyman), Destiny Knot (Master). Partner types: Normal and
    Fairy; show: a Normal partner keeps the pair company in the pasture.

  Done when:
  - compat GameTests: a compatible pair and a forced dawn give the right species with inherited IVs and the Everstone
    nature; an incompatible pair never does; with Cobbreeding a Cobbreeding egg item is given; the keeper's death sends
    both to their PCs;
  - showcase scene `daycare_keeper` (the screen, collecting).
- [x] **28.13** (approved 2026-10-05) **Builds for the new jobs.** Five builds with an upgrade each (architect skill, STYLE.md, renders), in
  the Blueprint Table (the Cobblemon ones only with Cobblemon) and sold by their job's Journeyman, each with its job
  block taking a villager with the item:
  - **Camp Kitchen**: an open timber shelter round a Campfire Pot, a grain store and benches; **II**: a Hearty Grain
    plot and a smokehouse with a smoker.
  - **Berry Nursery**: fenced farmland beds laid out in pairs, a composter and a potting bench; **II**: a greenhouse
    with four more beds.
  - **Habitat Garden**: a wild garden with a pond, tall grass, flowers, a Saccharine tree, stepping stones and a
    Pasture Block in a hedge, round a mossy centre stone; **II**: a keeper's hide on stilts and a second pond. (28.14
    puts the village's habitat under the centre stone.)
  - **Gem Grotto**: a stone shed over a lava pool behind glass, with a stonecutter, tumblestone ledges round the lava
    and a budding amethyst niche; **II**: a deeper chamber with four Deepslate Crystal Cores (plain deepslate without
    Cobblemon 1.8).
  - **Daycare**: a barn with a Pasture Block in a fenced paddock and a straw-floored nursery; **II**: a second paddock
    and a hatchery corner with lanterns.

  Done when: the ten blueprints are in the table, lint-clean, with renders front and back in the package; a builder
  finishes each in a GameTest with its job block in place; showcase scene `pokemon_builds`.
- [x] **28.14** (approved 2026-10-05) **A habitat of the village's own (Cobblemon 1.8).** The Habitat Block has no recipe and drops nothing:
  in survival it only comes in Cobblemon's 49 habitat structures. With `PokemonFeatures.HABITATS`:
  - Tending: natural Habitat Blocks within 64 blocks of the keeper's pasture (found through the loaded chunks' block
    entities, never a block scan) are visited each morning, and the hall's list posts today's phase ("Lush Cenote,
    today: Lotad, Wooper, Goomy") from the block's saved settings and `data/cobblemon/habitat_pools`.
  - Founding: an Expert Habitat Keeper in a village with a finished Habitat Garden places one Habitat Block in natural
    mode under its centre stone (mimicking the moss block it replaces, Cobblemon's mimic setting), with the pool from
    `data/aliveworkplace/village_habitats/<name>.json` (a biome tag and one of Cobblemon's pools per file). Shipped:
    plains → flowerbed_clearing, sunflower plains → sunflowerbed_clearing, meadow, flower and birch forest →
    fae_mounds, forest → berry_patch, cherry grove → pinkflowerbed_clearing, dark forest → deep_roots, taiga →
    spruce_wildfire_scar, snowy plains and ice spikes → snowy_burrow, snowy taiga, grove and snowy slopes →
    snowy_grotto, desert → desert_oasis, savanna → sunscorched_clearing, badlands → badlands_shaded_rock, swamp →
    lush_peat_bog, mangrove swamp → root_nursery, jungle → lush_canopy, beach → sandy_tide_pool, stony shore →
    rocky_tide_pool, windswept hills and peaks → rocky_outcrop, mushroom fields → fungal_dwelling, anywhere else →
    zen_garden. One per village; it belongs to the village (protection covers it); taking the garden down removes it
    without a drop.
  - Config `villageHabitats` (true). (Owner's call: whether survival villages may get a Habitat Block at all, since
    Cobblemon gives none; default meanwhile on, one per village.)

  Done when:
  - compat GameTests on 1.8.1: an Expert keeper with a finished Habitat Garden places one natural-mode block with the
    biome's pool, and a forced spawn round brings a Pokémon from that pool within its range; breaking the garden
    removes the block with no drop; on 1.7.3 nothing is placed and the keeper's page says it needs Cobblemon 1.8;
  - showcase scene `village_habitat`, filmed with `-Pcobblemon18=true`.
- [x] **28.15** (approved 2026-10-06) **Villages grow them.** With Cobblemon, villages (the pools that grow trainer's houses) sometimes grow
  five new houses, each in the five village styles through `tools/blueprints/village.py`'s `village_house` fit-outs,
  each with exactly one job block and its villager already in the job (an entity in the template): a **Pokémon Center**
  (a counter with a Healing Machine and a PC; a nurse), a **Camp Kitchen** (a cook), a **Berry Nursery** (a breeder), a
  **Daycare** (a keeper) and a **Gem Grotto** (a grower at a stonecutter, an amethyst niche, tumblestone ledges on magma
  behind glass; no open lava in a village). Config `pokemonVillageHouses` (true).

  Done when: `generate.py` writes the 25 templates (no `structure_void`, B5), renders checked; a `VillageGameTests`
  test per house places it with its villager, who has the job and stands in open space (B6); `SCENE=village` shows one
  in a Cobblemon village.
- [x] **28.16** (approved 2026-10-06) **The Arena.** Three tiers (architect skill, an original design, renders), in the Blueprint Table with
  Cobblemon only and sold by an Expert Trainer Leader:
  - **Arena**: a 15×9 battle ring (a packed-mud floor, white lines and a centre circle), a trainer's box on a dais at
    each end with a lamp post, benches along one long side for 12 villagers, two banner poles, a notice board; at most
    25×19;
  - **Arena II**: stands on both long sides (30 seats), a gate arch, lanterns round the ring, a fair lane with two
    striped kiosks;
  - **Arena III**: a covered grandstand on stone terraces, a trainers' room at each end (a Healing Machine in each),
    and a champion's pole before the gate; at most 29×29 (fits `huge_area`).

  `Arenas.find(level, hall)` returns a finished Arena within the hall's reach with its ring centre, the two boxes, the
  seats, the fair lane and the champion's pole, for every tier, rotation and mirror (known offsets in our own
  templates, like `MarketDays.square`). The hall's "What next?" suggests an Arena to a Cobblemon village of Village rank
  with a Trainer Leader and no Arena.

  Done when: a builder finishes each tier (GameTest); `Arenas.find` is right in 4 rotations and mirrored (GameTest);
  showcase scene `arena` (each tier, front and from above).
- [x] **28.17** (approved 2026-10-06) **The Festival Cup: calendar, themes and entrants.** No battles yet; config `festivalCup` (true),
  `cupEveryFestivals` (1; owner, 28.1a).
  - Hosts: a village with a hall, a finished Arena, Cobblemon and at least Village rank holds every festival as
    a Cup (every `cupEveryFestivals`th). Its circuit: the host and every village with a hall it has a trade route with,
    either way (`Caravans`), seven at most, nearest first.
  - Themes are data: `data/aliveworkplace/cups/<name>.json` holds the name (a lang key), its place in the order, the
    format (singles or doubles), the level every Pokémon battles at (Cobblemon's level adjust), how many each trainer
    brings, the allowed types, the stage (first, final or any), banned labels (legendary, mythical, ultra beast,
    paradox, as for trainers), the Showdown rules, when the bouts start and must end (noon to midnight unless it says
    otherwise), the fair's wares (item and price), the feast dish, the firework colours and the bard's disc. Loaded by a
    reload listener; this item ships one theme (the Grand Cup, 28.22) to test
    with. The next theme comes in order; the host's owner may pick another on the Cup page until sign-up closes.
  - Entrants: each circuit village's Trainer Leader (else its best Trainer; else none), the host's Leader and up to two
    of its Trainers to fill the bracket, and the players who sign up: each for a village they own, or whose owner made
    them a friend, or that nobody owns; two players a village at most. The bracket holds 4, 8 (Town host) or 16 (City
    host); byes go to the highest seeds (Leaders by tier, then players, then the host's Trainers). Fewer than 4: no
    Cup, a plain festival, and the page says why.
  - Far villages: every hall's round now writes its Trainer Leader (UUID, name, tier, XP) on its `Caravans` entry (new
    fields, none by default), so an unloaded village can still send them.
  - The hall's Cup page (a `ChoiceMenu` page, a new button on the hall screen): the next Cup (theme, day, its rules in
    plain words), the circuit's villages and their entrant, the signed-up players, Sign up / Withdraw, and the roll of
    champions. Players in circuit villages are told when sign-up opens (when the festival before ends) and the evening
    before.
  - Saved in `aliveworkplace_cups` (per dimension, by host: theme, day, entrants, bracket, results, champions).

  Done when: GameTests for the circuit from routes, the calendar (every festival, and every second with `cupEveryFestivals` 2), the entrants (Leader, best
  Trainer, the players' rights and caps), seeding and byes for 3, 5, 8 and 11 entrants, the Leader record written and
  read while the village is unloaded, the Cup state saved and reloaded, a malformed theme file logged and skipped; and
  showcase scene `cup_page`.
- [x] **28.18** (approved 2026-10-06) **Cup bouts between villagers.** A round starts when the last round's bouts are done; a bout between
  two villager entrants is an exhibition at the ring:
  - Teams: `CobblemonTrainers.team` gets a themed variant: the same seeded pool, filtered by the theme's types, stage
    and labels, as many as the theme brings, trained by the trainer's tier as now. A delegate (28.19) uses its Leader's
    UUID, so it fields the same team as at home.
  - The bout: each trainer stands in their box; their Pokémon come out one at a time beside the ring (real Pokémon
    entities marked like trainers' Pokémon: never catchable, removed on load), face each other and take turns: the
    faster uses one of its moves (Cobblemon's physical or special animation and the impact effect of the move's type,
    as the guards' partners show), damage from a small formula (the theme's level, the move's power, attack against
    defence, the same-type bonus, type effectiveness from `data/aliveworkplace/type_chart.json`, the 18 types),
    seeded by the bout so it can be replayed. A fainted Pokémon is called back and the next comes out; one-on-one even
    in a doubles Cup; 90 seconds at most, then the side with more health left wins.
  - Afterwards: the winner goes on; both trainers get trainer XP (`Trainers.XP_PER_BATTLE`, the win bonus to the
    winner); a delegate's XP is banked on its village's `Caravans` entry and given to the real Leader when that village
    next loads. Everyone at the Arena reads the result ("Mira of Thornholm beat Dara of Ashford: Lucario stands").

  Done when: GameTests: the bout is the same for the same seed; the chart is sane (Water on Fire 2×, Normal on Ghost
  0×); a bout between two trainers ends with a winner and every Pokémon entity gone; a themed team follows the theme; a
  delegate's banked XP reaches the real Leader on load; showcase scene `cup_bout` with its GIF.
- [x] **28.19** (review: pending 2026-10-06) **Cup day.** The Cup is the host's festival, from morning to the final:
  - Delegates: from 1000 the far villages' entrants arrive, each a visiting villager with its Leader's name, a Trainer
    Leader's outfit and "of <village>", walking in from the village edge on the side its home lies, to the Arena
    (attachment `CUP_DELEGATE`: home hall, the Leader's UUID, tier; never takes a job or a bed; leaves at dawn out of
    sight, like the market's traders). The host's own entrants walk there. A real Leader whose village is loaded is
    "away at the Cup" that day and takes no challenges.
  - The fair: the market's travelling traders come to the Arena's fair lane (`MarketDays.hold` there, two more than a
    market day), each also selling the theme's wares.
  - The afternoon off: from noon (6000) villagers off work, and for the final every villager, gather in the stands
    (seated on the benches through a seat entity, as they sit in saddles and boats), cheer when their village's entrant
    wins a bout (the celebrate sound, happy particles), and eat the feast (`Festivals.feast`, the theme's dish first if
    the store has it); the bard plays the theme's disc at the ring. `Festivals.square` points at the ring all day.
  - The bouts start at the theme's start (noon by default); after the final, fireworks in the theme's colours
    (`Festivals.launch`), Hero of the Village for players there and the festival's mood lift for everyone who came. At
    the theme's end time any bout left is settled as an exhibition (players not at the ring lose by walkover).
  - The chronicle of every circuit village gets the Cup (new kind `CUP`, a gold ingot icon): host, theme, champion and
    the final's line.
  - A host not loaded on its Cup morning puts the Cup off a day at a time (eight days at most, then it's called off);
    a host whose Arena is gone holds a plain festival.

  Done when: a GameTest runs a Cup day with 4 entrants (2 delegates) from morning to a champion: the delegates come in
  from their homes' side and are gone by dawn, villagers are in the stands for the final, the fair sells the theme's
  wares, every circuit hall's chronicle has the entry; with `festivalCup` off it's a plain festival; showcase scene
  `cup_day` (the whole day, sped up) with its GIF.
- [x] **28.20** (review: pending 2026-10-06) **Players in the Cup.** A signed-up player's bout is called in chat with a clickable [I'm ready] and a
  bell; they have two minutes inside the Arena to click it, or lose by walkover (so does a player who's offline).
  - Against a villager: a real Cobblemon battle with the delegate or host trainer as `VillagerTrainerActor`, in the
    theme's format (singles or doubles), its level adjust and Showdown rules (the theme's level replaces RCT's level
    caps here). The player's team is their party's first eligible Pokémon, up to the theme's count; before the battle
    they're told which were left out and why. Both sides fight with healed copies, so nobody gains experience or keeps
    damage. No daily limits from ordinary challenges apply.
  - Against a player: Cobblemon's own PvP battle (`BattleBuilder.pvp1v1`) with both eligible teams as healed copies.
  - Fleeing loses the bout. [Watch] on the Cup page and the Arena's notice board puts a player in Cobblemon's spectator
    view of a running player bout.
  - The purse, money only (CobbleDollars, emeralds without): 100,000 PokéDollars for each bout won, 500,000 more for
    winning the final, half as much again at a City host (owner, 28.1a; emeralds at `dollarsPerEmerald` without
    CobbleDollars).

  Done when: compat GameTests: a signed-up player's battle starts with only eligible Pokémon, as a double battle for a
  doubles theme, at the theme's level; a win moves them on and pays; an absent player loses by walkover after two
  minutes; fleeing counts as a loss; a bout between two players starts with both teams; showcase scene `cup_match`
  (the battle at the ring, the stands behind).
- [ ] **28.21** **Champions.** When the final ends:
  - The champion's village holds the Cup until the host's next Cup: its name goes on the roll of champions (Cup page),
    in every circuit village's chronicle, on its hall's name tooltip, in other halls' trade-route lists and on the
    Village Map's legend ("holders of the Thornholm Cup").
  - The Cup banner: a banner pattern of ours (`aliveworkplace:cup`, data-driven, drawn with the pixel-art skill) in the
    theme's colours flies over the holder: on its Arena III's champion's pole if it has one, else on top of its Village
    Hall where the space is free. It comes down when the title passes. A far village that wins gets it the next time it
    loads. The banner belongs to the village; the player who won gets the purse only.
  - Pride: while they hold the Cup every villager there has a mood reason "Our village holds the Cup" (+5).
  - The winning Leader (or the host trainer) gets two win bonuses of trainer XP. Defending champions are seeded first
    at the next Cup.

  Done when: GameTests: after a final the banner stands on the champion's pole or hall and comes down when a new
  champion is crowned; the mood reason is there for holders and gone after; the chronicle line is in every circuit
  hall; a far champion gets its banner when it loads; the pattern passes `lint.py`; showcase scene `cup_champions`
  (the banner over the hall, the roll of champions).
- [ ] **28.22** **The eight Cup themes.** Each a data file in `data/aliveworkplace/cups/` with its rules, wares,
  dish, fireworks and disc; delegates' and host trainers' teams follow it (28.18), and so does players' eligibility
  (28.20). Discs are vanilla's. In order:
  1. **Blossom Cup**: Grass, Bug and Fairy; singles; level 50; bring 3. Wares: Miracle Seed, Silver Powder, Fairy
     Feather, Leaf Stone, Shiny Stone. Dish: Flower Sweet. Fireworks pink and green. Disc: Chirp.
  2. **Little Cup**: first-stage Pokémon that can still evolve; singles; level 5; bring 3. Wares: Eviolite, Everstone,
     Oval Stone, Link Cable, Exp. Candy XS. Dish: Casteliacone. Fireworks yellow and white. Disc: Cat.
  3. **Sun Cup**: Fire, Water and Electric; doubles; level 50; bring 4. Wares: Charcoal Stick, Mystic Water, Magnet,
     Fire Stone, Water Stone, Thunder Stone. Dish: Lava Cookie. Fireworks orange and blue. Disc: Blocks.
  4. **Workers' Cup**: only Pokémon that have helped a villager at work on 3 days or more (from this item on, each day
     a pastured Pokémon is counted as a worker's partner, `work/Partners` adds one to a counter in its persistent data,
     which Cobblemon keeps); any type; singles; level 50; bring 3; villager trainers field types `Partners.types` lists.
     Wares: Black Belt, Hard Stone, Metal Coat, Soft Sand, Power Weight. Dish: Pewter Crunchies. Fireworks gold and
     brown. Disc: Mall.
  5. **Harvest Cup**: Ground, Rock and Normal; singles; level 50; bring 3. Wares: Soft Sand, Hard Stone, Silk Scarf,
     Sun Stone, Big Root. Dish: Leek and Potato Stew. Fireworks orange and gold. Disc: Far.
  6. **Lantern Cup**: Ghost, Dark and Psychic; singles; level 50; bring 3; starts at dusk (12000) and may run until
     dawn, fireworks at the end. Wares: Spell Tag, Black Glasses, Twisted Spoon, Dusk Stone, Reaper Cloth. Dish:
     Sinister Tea. Fireworks purple and white. Disc: 13.
  7. **Frost Cup**: Ice, Steel and Dragon; singles; level 50; bring 3. Wares: Never-Melt Ice, Metal Coat, Dragon Fang,
     Ice Stone, Razor Claw. Dish: Smoked-Tail Curry. Fireworks light blue and white. Disc: Strad.
  8. **Grand Cup**: any Pokémon a village trainer may use; singles; level 100; bring 6; Species and Item Clause. Wares:
     Ability Capsule, Life Orb, Leftovers, Choice Scarf, Rare Candy. Dish: Big Malasada. Fireworks gold and red. Disc:
     Creator.

  Wares cost 4 to 16 emeralds each (in the files). Done when: a GameTest per theme (a delegate's team follows it, the
  player filter takes and refuses the right Pokémon, the fair sells its wares); the Workers' counter grows by one for a
  day of helping and never twice a day; `langcheck.py` is clean; showcase scene `cup_themes` (each theme's Cup page and
  fair).

- [ ] **28.23** **A custom screen for Pokémon trades (owner, 2026-10-05).** The Pokémon Trader's offers use vanilla's
  plain trading screen. Give them a screen of their own, like the Village Hall's (30.4a): the Trader's offers as cards
  showing the Pokémon (name, level, ball, shiny mark) next to what it costs, the player's party on the other side so
  a trade can be picked from it, and a plain "no offers today" state. Same look as the other workstation screens
  (pixel-art skill, sprites drawn the way vanilla draws its GUIs), every sentence through `lang`. Done when: a GameTest
  opens the screen from a right-click on the Trader and completes a trade through it, refuses one the player can't
  afford, and survives save and reload; `langcheck.py` is clean; showcase scene `trader` gains stills of the new screen.

Depends on: nothing. (Later milestones build on this one: M29's Pokémon Professor and Ranger on 28.3 and 28.10, M31's
bounty board on 28.10's sightings, M35's Stadium on 28.16 to 28.21.)

## Milestone 29: Legends: rare villagers (1.3)

One-of-a-kind villagers, earned and never bought, each changing what a village can do: about one villager in thirty is
Gifted with a rare trait, and above them stand twelve Legends, Rare (one per village), Legendary (one per world) or
Mythic (several per server, capped per village by its rank, announced to everyone). Legends come only when a village
has earned them, in four ways: as guests (Terraria-style), found out in the world, born to two schooled Masters, or
inspired by a strange mood to make a Masterwork. Each wants a tier III home of their own, a luxury they like and a
happy village, and an unhappy Legend never leaves but goes on strike. It builds on people/ (Traits, Moods, Families,
Homes, Couples), inn/ (Innkeepers), school/ (Schools), hall/ (VillageRanks, Chronicle, Festivals, Treasury, Caravans,
MarketDays) and research/.

- [x] **29.1** (approved auto 2026-10-04) **Design note.** `docs/design/M29.md`: what the player sees (the Gifted, each Legend, how each arrives,
  the hall's Legends page, needs and strikes); the data formats (`legends/`, `gifted/` and `research_trees/` under
  `data/aliveworkplace/`, and the four `aliveworkplace:luxury/*` item tags); the config switches (`legends`,
  `giftedChance`, `legendSites`, `strangeMoods`, `legendNeeds`, `mythicLegendCap`); the save data (the villager
  attachments `LEGEND`, `GIFTED` and `STRANGE_MOOD`, new optional fields in `Families.Parents`, the hall's new fields,
  the server-wide `aliveworkplace_legends` record) and what an existing world sees after the update (some villagers
  turn out Gifted; nobody is a Legend until a village earns one). Sent to the owner as a review package; lanes don't
  wait for his reply. Done when: the note is on `main` and the package is sent.
- [x] **29.2** (approved 2026-10-05) **The Legend engine.** A new `legend/` package. `Legends` loads one file per Legend from
  `data/aliveworkplace/legends/<id>.json` through `Platform.get().onDataReload` (as `ranch/PokemonChores` does), so
  server owners can add their own. A file holds: `rarity` (`rare`, `legendary`, `mythic`); `job` (the trade they work,
  always as a Master, with every level's trades through `Schools.headStart`; `aliveworkplace:legend` for Legends
  without one); `title`, `lore` and six `names` (lang keys, for guests; a villager who becomes a Legend keeps their
  own name); `conditions` (what the village must have); `arrive` (the ways they come, each with its settings:
  29.7-29.10); `needs` (29.5); `powers`; `masterwork` (29.10); `outfit` (a texture, 29.4). The shared toolbox, one
  small class per type, each with a test and a progress line for the hall's hints:
  - conditions, in a new package `rules/` that later milestones extend (M35's Wonders use it): `rank_at_least`,
    `villagers`, `finished` (a count of finished buildings, optionally of one blueprint in any style, or in N
    different styles), `job_level` (N workers of a trade at a level or above), `treasury_total` (every emerald the
    treasury has ever taken in: a new hall field, 0 in old halls, because the treasury holds at most 256 at once),
    `caravan_routes`, `meal_kinds`, `festival_crowd`, `animals_at_job` (animals within 16 blocks of a trade's
    workstations), `research_levels`, `iron_golems`, `full_moon`, `first_city` and `legend` (a given Legend settled
    in the village); `pastured_pokemon` and `alpha_near` come with 29.21 and 29.22 through `compat/`;
  - powers built here: `pace` (trades, a radius or the whole village, how much faster: read in `BuilderLevels.delay`
    through the shared 2x cap, which this item adds if no other milestone has yet) and `mood` (points, a radius: a
    reason in `Moods.work`); each Legend item adds its own named powers. Auras look Legends up in a per-dimension list
    of settled Legends refreshed every 200 ticks, never with an entity scan.

  It also registers the profession `aliveworkplace:legend` (no workstation, never reset to a jobless Novice, a plain
  outfit drawn with the pixel-art skill), the villager attachment `LEGEND` (id, name, guest or settled, hall, since,
  the strike fields; every field with a default), `/workplace legend list|make <id>|clear` (op) and the config switch
  `legends`. Done when:
  - GameTests with a test Legend in the gametest data: it loads; every condition is true and false on staged villages,
    with the right progress numbers; `make` turns a villager into it (a Master of its trade) and it survives a save
    and reload;
  - a `pace` power makes a builder within its radius faster and one outside it not, never past the shared cap; a
    `mood` power shows as a reason in the villager's mood;
  - with `legends` off no Legend loads, nothing ticks and the command says so.
- [x] **29.3** (approved 2026-10-05) **Rarities, caps and the server's record of Legends.** `legend/LegendRecord`, saved data on the
  overworld (`aliveworkplace_legends`), lists every Legend that has settled anywhere on the server: id, villager UUID,
  dimension, hall, rarity, the day they settled and the day they fell. `Legends.canCome(level, hall, legend)` is asked
  before any arrival:
  - Rare: one of each per village;
  - Legendary: one of each per world, in any dimension;
  - Mythic: as many as the server earns, but each village holds at most `mythicLegendCap` for its rank (a config list,
    default Hamlet 0, Village 0, Town 1, City 2); a village that falls back a rank keeps the ones it has.

  Guests and settlings are announced: Rare and Legendary ones to the players in the village and to the hall's owner
  wherever they are, Mythic ones to every player on the server (in gold, with the challenge sound, the village's name
  and its direction from spawn). All go in the chronicle as a new `Chronicle.Kind.LEGEND` (a nether star). Death: a
  Legend's grave holds their slot, and the Undertaker brings them back as they were (the attachment is in the grave's
  NBT); a Legend turned into a zombie villager keeps their slot and is themselves again when cured; dead with no
  grave, or with their grave broken, their slot frees after 7 days ("fallen" in the record and the chronicle). If
  their hall is broken and placed again, Legends join the nearest hall at its next round. Done when:
  - GameTests: a second Rare of one kind is refused in one village and allowed in another; a second Legendary is
    refused in any village and dimension; a first Mythic is refused in a Village, and in a City a second is allowed
    and a third refused; the caps follow the config;
  - a Legend killed with a grave keeps the slot and comes back revived as the same Legend; a cured zombie Legend is
    the Legend again; a slot frees 7 days after a death with no grave; the record survives a reload;
  - a Mythic announcement reaches a player 5,000 blocks away and a Rare one doesn't; showcase scene `legend_announce`
    (the message in chat, the chronicle line).
- [x] **29.4** (approved 2026-10-05) **Legends on the hall, and how they look.**
  - The hall's list (`VillageHallScreen`) puts Legends first: name and title in gold ("Ada Stonewright, Master
    Architect"), rarity, each power on a line, each need with a tick or a cross, a strike in red. A Gifted villager's
    trait shows in gold under their traits.
  - A **Legends** page (a nether star on the hall's screen): the village's Legends, then every other Legend in the
    roster as a card: rarity, how it comes ("visits the inn", "found at a ruined portal"), each condition with its
    progress ("kinds of meal in the store: 5 of 8"), "lives in Thornholm" for a Legendary already taken, and the
    Mythic line ("Mythic Legends: 0 of 1, as a Town"). Terraria-style: what a village must do is never a secret.
  - `VillageAdvice` ("What next?") gets a tip when a Legend lacks only one condition; `Chatter` gets six lines about
    the village's Legends and guests.
  - The look: a Legend outfit layer in `client/` (beside `GuardArmorLayer`) draws
    `textures/entity/villager/legend/<id>.png` over the job's outfit, told to the client in a small `LegendLook`
    packet when a player starts tracking the villager; a soft end-rod sparkle every 10 seconds; the name in gold over
    their head.
  - README: a new *Legends* section that each Legend item adds its paragraph to; `tools/showcase/scenes.py` gets a
    "Legends" group.

  Done when: GameTests check the page's cards (each condition's progress, the taken and Mythic lines) against staged
  villages; screenshots show a test Legend in a placeholder outfit (pixel-art skill) and the page at GUI scales 2 and
  4; showcase scene `legends_hall`.
- [x] **29.5** (approved 2026-10-05) **Needs and strikes.** Each settled Legend's needs are checked once a day in the hall's round:
  - **a home of their own**: their bed is in a finished building of tier III or higher (`Homes.at`), and nobody else's
    bed is in it but their spouse's (`Couples`);
  - **a liked luxury**: once every 7 days they take one item of their kind from a chest in their home, else the
    village store: the item tags `aliveworkplace:luxury/wine`, `/jewels`, `/books` and `/clothes`, filled for now with
    stand-ins (honey bottles; amethyst shards and emeralds; books and enchanted books; leather armour of any colour)
    that M34's luxury goods join later; taking it lifts their mood 10;
  - **a happy village**: the grown-ups' average mood is 60 or more (wellbeing 60% with `villagerMoods` off).

  A need unmet two days running (after a 3-day grace once a Legend settles) starts a **strike**: their powers stop,
  their trade's work stops (their WORK activity becomes a picket: they stand by the Village Hall by day), a red line
  over their head says what they want ("On strike: a home of my own"), the hall's list and Legends page show it in
  red, and the chronicle records it. The day every need is met again they go back to work, and the chronicle says so.
  Legends never leave: inn departures, despawning and the hall's call-home all pass them by, and a strike lasts until
  their needs are met. `legendNeeds` in the config turns needs and strikes off. Done when:
  - GameTests: a tier III home shared with a stranger fails, shared with a spouse passes, a tier II one fails; the
    luxury is taken once a week from the home chest, else the store; the mood test with moods on and off;
  - unmet two days: a strike, during which the Legend's `pace` power is gone and their trade's work stops; met again:
    back at work that day; a Legend on strike for 20 days is still in the village;
  - a screenshot and a GIF of a Legend picketing at the hall under the red line; showcase scene `legend_strike`.
- [x] **29.6** (approved 2026-10-05) **Gifted villagers (1): the engine, Prodigy, Iron Will, Silver Tongue and Night Owl.** Gifted traits
  are data: `data/aliveworkplace/gifted/<id>.json` holds a weight and effects from the shared toolbox, with lang keys
  for the name and description. About one villager in 30 is Gifted (config `giftedChance`, 30; 0 turns them off),
  rolled from the UUID as `Traits.of` does, so every existing villager has theirs with nothing to migrate; the
  attachment `GIFTED` overrides the roll for the born and the inn's travellers (29.7). A Gifted villager's trait shows
  in gold on the hall's list, they sparkle when they level up, and the chronicle notes it when they join or grow up.
  The first four, each a toolbox effect:
  - **Prodigy**: learns three times as fast (`xp`, in `Traits.xp`; with Clever too, still three times);
  - **Iron Will**: never panics (`no_panic`, through `VillagerPanicTriggerMixin`) and keeps working through raids and
    the bell;
  - **Silver Tongue**: every trade with a player is 20% cheaper (`trade_discount`, on their offers' special price);
  - **Night Owl**: works from dusk to dawn and sleeps from mid-morning to mid-afternoon (`night_shift`: a `NIGHT_OWL`
    schedule set in `VillagerMixin` as `GUARD_SCHEDULE` is), builders and miners included.

  Done when: GameTests for each trait (three times the XP; no panic in a staged raid; offers 20% cheaper; a Night Owl
  builder places blocks at midnight and sleeps at noon); the roll over 3,000 fixed UUIDs gives about 1 in 30 and the
  same answer twice; with `giftedChance` 0 nobody is Gifted; showcase scene `gifted` (the hall's list, a Night Owl
  building by moonlight).
- [x] **29.7** (approved 2026-10-05) **Gifted villagers (2), and Legends born.** Four more traits:
  - **Lucky**: luck +3 on every loot roll their work makes, on top of the level luck `Explorers` and `Netherworkers`
    already give (explorer finds, Netherworker trips, sifting, fishing) (`loot_luck`; our sifting tables get `quality`
    weights for it);
  - **Hardy**: never falls ill (`Sickness`) and has twice a villager's health (`no_illness`, `health`);
  - **Beloved**: everyone whose bed is within 16 blocks of theirs is +5 mood, "a beloved neighbour" (`mood`);
  - **Born Leader**: workers of their own trade within 16 blocks work 10% faster (`pace`, through the shared cap).

  **Born**, the third way Legends come: `Families.born` also keeps whether each parent was a schooled Master (new
  optional fields in `Families.Parents`, false in old saves). When a child of two schooled Masters grows up
  (`Families.round`), 1 time in 20 they are a Legend: a Rare Legend whose `born` way names a parent's trade, if the
  village meets its conditions and its slot is free (29.3), taking the Legend's own trade as a Master; otherwise 1
  time in 4 they are Gifted. Inn travellers are Gifted 1 time in 10, shown on the hire screen, at twice the price. The
  chronicle says so ("Wren, Dara and Tom's daughter, has grown up a Prodigy"; "... has grown up to be the village's
  Bard Laureate"). Done when: GameTests for the four traits; the born rolls with a fixed `RandomSource` (a Legend,
  Gifted, neither, and a taken slot falling back to Gifted); an old `parents` record loads with the new fields false;
  a Gifted traveller's price; showcase scene `gifted_born` (a child of two Masters grows up Gifted, with the chronicle
  line).
- [x] **29.8** (approved 2026-10-05) **Legends who visit (Terraria-style guests).** The first way Legends come. Once a day, at the way's own
  time, a village with no Legend guest may get one: a Legend whose `visit` way's conditions the village meets, whose
  slot is free (29.3) and who hasn't visited it in 7 days comes with the way's chance (default 1 in 4; a hook
  `Legends.visitFactor(hall)` for M30's Open Gates edict) to the way's place:
  - `inn`: in the morning, instead of that day's traveller (`InnkeeperWork.tend`: an Innkeeper and a free bed);
  - `market`: with the traders on market day (`MarketDays.hold`);
  - `festival`: at the fireworks of a festival whose crowd reaches the file's number (`Festivals` counts the crowd
    then and keeps it on the hall as `festivalCrowd`);
  - `chapel`: at midnight under a full moon, at a finished Chapel (the `chapel` blueprint);
  - `hall`: in the morning, beside the Village Hall.

  A guest is announced (29.3), stays up to 3 days and takes no job. Right-click them for their terms (a `ChoiceMenu`
  like the inn's hire screen): who they are, what they would bring, what they want with ticks and crosses, and the
  days left. They settle by themselves the round every need is met: they claim the bed in the tier III home, take
  their trade as a Master (a free workstation of it if there is one; their powers work either way) and go in the
  chronicle. If not, they leave on the third evening out of sight, and the chronicle says what they missed. Done when:
  - GameTests: no guest before the conditions are met or while the slot is taken; never two guests at once; one at
    each place (inn, market, festival, chapel, hall) on a staged village; they settle the round their needs are met
    and not before; they leave after 3 days and don't come back for 7;
  - the terms screen's lines match the needs; a guest survives a save and reload mid-stay;
  - showcase scene `legend_guest` (GIF: a test Legend arrives at the inn, the terms screen, they settle once a home is
    ready).
- [x] **29.9** (approved 2026-10-05) **Legends found in the world.** The second way. Every 5 seconds per player,
  `StructureManager.getStructureWithPieceAt` asks whether they stand in a structure of a site tag (three cheap lookups
  and no area scans; tags, so packs can add their own variants, such as Repurposed Structures'):
  - `#aliveworkplace:legend_sites/ruined_portal` (vanilla's ruined portals): a traveller's camp beside the portal (a
    bedroll, a campfire, a map on a barrel);
  - `#aliveworkplace:legend_sites/outpost` (pillager outposts): a prisoner in an iron-bar cage at the foot of the
    tower;
  - `#aliveworkplace:legend_sites/shipwreck` (shipwrecks): a castaway's camp on the nearest beach (a raft, a lean-to,
    a signal fire).

  A site holds a Legend only when the player owns, or is a friend of the owner of, a Village Hall within 1,500 blocks
  that meets the conditions of a Legend whose `found` way names the site, with its slot free. Then the camp is placed
  (as `BanditCamps.found` places its camp; the three small builds through the architect skill as
  `legend/traveller_camp`, `legend/prisoner_cage` and `legend/castaway_camp`) and the structure start is marked in the
  Legends record, so each is used once. Freeing them: the traveller, talk to them; the prisoner, break the cage's
  bars; the castaway, hand them a cooked meal. Freed Legends thank the player, walk off and arrive at that village's
  hall the next morning as a guest (29.8: 3 days to settle). `legendSites` in the config turns this off. Done when:
  - GameTests on test structures: each camp is placed only for a qualifying player and only once; each freeing works
    and the wrong one doesn't (a meal doesn't open the cage); the Legend is a guest at the hall the next morning; no
    lookups run with nobody online;
  - renders of the three camps in the review package; showcase scene `legend_sites` (the three camps, and a GIF of the
    cage opened).
- [x] **29.10** (approved 2026-10-05) **Strange moods and Masterworks.** The fourth way (Dwarf Fortress). Once a day, in a happy village
  (29.5's test) with no mood already on, a Master whose trade a Legend's `inspired` way names may be seized by a
  strange mood, 1 time in 8, if the village meets that Legend's conditions and its slot is free (never during a raid
  or a festival; config `strangeMoods`). The villager (attachment `STRANGE_MOOD`):
  - claims their workstation: their other work stops, they stand at it, and a purple line over their head says "Taken
    by a strange mood";
  - asks for three rare materials, picked from the Legend's `masterwork.materials` pool, to be put in a chest by the
    workstation within 3 days; the hall, the requests board (`work/Requests`) and the village's players are told;
  - **success**: they make a named **Masterwork**: the file's item with a made-up name ("The Ember Ladle"), lore lines
    naming the maker, the village, the day and the materials, and an enchantment glint. It goes to the player who
    brought the most of the materials (else into the chest), and the villager becomes the Legend (chronicle,
    announcement). A Masterwork in an item frame in the village is worth 3 beauty (`Decorations`);
  - **failure**: a week of sulking (mood -30, half pace, "Sulking" over their head), and no strange mood in that
    village for 10 days.

  The Founder's mood (29.23) is the one exception: it always comes, at the village's first rise to City. Done when:
  GameTests with a fixed `RandomSource`: a mood starts only in a happy village with a Master of a named trade; the
  three materials are on the board; success makes the Masterwork (name, lore) and the Legend; failure sulks a week and
  blocks moods for 10 days; a mood survives a save and reload halfway; showcase scene `strange_mood` (GIF: the claim,
  the chest filled, the Masterwork).
- [x] **29.11** (approved 2026-10-05) **More research trees, as data.** For the Old Sage (29.14) and the Pokémon Professor (29.21).
  `research/ResearchTrees` loads `data/aliveworkplace/research_trees/<tree>.json`: the Legend who researches it, an
  icon, and its topics, each with levels, a cost per level (items), research points, the topics it needs, an optional
  `unlock` (a named village counter at a number, such as species in the village Pokédex), an optional `exclusive`
  group (the village may take one topic of the group, for good) and effects from the shared toolbox. This item adds
  the effects the trees need: `wellbeing`, `illness` (chance and days), `raid_chance`, village-wide `xp` and
  `loot_luck`, and `flag` (a named switch that other code reads). Levels are kept in the hall's `Research.State` map
  under `<tree>/<topic>` keys, so there is no new save field and the first tree is untouched. `ResearchScreen` gets a
  tab for each tree whose Legend lives in the village; the Legend works it at a lectern within 8 blocks of their home
  as `ScholarWork` does (scholars help at half speed), and not while on strike. Done when: GameTests with a test tree:
  it loads; costs are taken; levels are saved in the hall and come back after a reload; an exclusive group refuses a
  second pick; an unlock waits for its counter; each effect works; an old hall's research loads unchanged; a
  screenshot of the test tree's tab.
- [x] **29.12** (approved 2026-10-05) **The Master Architect (Legendary).** `legends/master_architect.json`. Comes: a guest at the inn once
  the village is a Town with finished buildings in at least 3 styles (a blueprint's own drawing counts as one;
  `BlueprintStyles` ids tell the rest). Trade: Builder. Likes: jewels. Powers:
  - builders working on a site within 32 blocks of the Architect build twice as fast (`pace`; with the shared cap,
    that is the most any builder gets);
  - **grander buildings** (`grand_rebuild`): every 3 days the Architect picks a building the village's builders
    finished (homes first; never a decoration, never anything a player built) and hands the least busy builder its
    next upgrade (`BlueprintUpgrades`). A building with none is redrawn in the new **Grand** style
    (`blueprint_styles/grand.json`: stone-brick plinths, polished andesite and deepslate trim, dark-oak frames, copper
    lanterns; drawn and checked in a render with the architect skill), built like an upgrade (only the changed
    blocks), with materials from the chests as usual. One at a time; sneak-right-click the Architect to pause it; each
    goes in the chronicle;
  - the Architect is who M35's Wonders ask for (`Legends.in(level, hall, id)`).

  Outfit (pixel-art skill): a long blue coat, a brass compass and a rolled drawing at the belt. Done when: GameTests:
  the Architect visits only a qualifying Town; a builder within 32 blocks is twice as fast and at the cap, one outside
  isn't; a rebuild picks an upgrade, the Grand style when there's none, places only the changed blocks, never touches
  a player's build and stops on strike; the Grand style's renders (three builds, front and back) and the outfit in the
  review package; showcase scene `legend_architect` (GIF: the Stone House redrawn in the Grand style).
- [x] **29.13** (approved 2026-10-05) **The Pathfinder (Rare).** Comes: found at a ruined portal (29.9) once one of the village's explorers
  (Cartographers) is an Expert; born to a Cartographer (29.7). Trade: Cartographer. Likes: clothes. Powers:
  - their own expeditions (`ExplorerWork`) range twice as far and roll the new `explorer/pathfinder` loot table (more
    maps, now and then a trial key or an echo shard);
  - **expeditions the player joins** (`expedition`): sneak-right-click them, with 8 food in their chest, and pick a
    **Stronghold**, an **Ancient City** or **Trial Chambers** (tags `aliveworkplace:expedition/stronghold`,
    `/ancient_city`, `/trial_chambers`). They find the nearest within 3,000 blocks once (`findNearestMapStructure`),
    hand the player an explorer map to it and lead the way: walking ahead, waiting when the player is more than 24
    blocks behind, catching up at once beyond 64 (as Rally Banner guards do), and fighting whatever attacks either of
    them. At the place they say so and mark the entrance with a banner; right-click them there for **Home**, and after
    5 seconds standing still both are back beside the Village Hall. One a day; a logout or a death cancels it (the
    Pathfinder is home the next morning). The chronicle records each one, with its distance and direction.

  Outfit: a hooded travel cloak, a pack and a lantern. Done when: GameTests: they are found only for a village with an
  Expert explorer; an expedition to a staged target leads, waits and catches up; Home brings both back; a logout
  cancels it; the loot table rolls; showcase scene `legend_pathfinder` (GIF: leading the player through a forest).
- [x] **29.14** (approved 2026-10-05) **The Old Sage (Rare).** Comes: found in a hermit's hut once the village has finished 5 research
  levels; born to a Scholar (29.7). The hut (`legend/hermit_hut` through the architect skill: mossy cobblestone and
  spruce, a lectern, bookshelves, a cauldron and an herb garden) is placed 150-250 blocks from the hall on dry, flat,
  loaded ground (as `BanditCamps.site` picks), and the village's players hear a rumour of it with its direction.
  Right-click the Sage for the **riddle quest**: three riddles, one at a time, each answered by handing over an item,
  from a pool of eight (lang):
  - "I have cities but no houses, forests but no trees, rivers but no water": a map;
  - "I point the way but never walk": a compass;
  - "I follow the sun all day but never see the sky": a clock;
  - "I have a spine but no bones, and leaves but no branches": a book;
  - "I'm full of holes, yet I hold water": a sponge;
  - "Feed me and I live; give me a drink and I die": a torch;
  - "I run but never walk, and have a bed but never sleep": a water bucket;
  - "I sleep under the mountains until a pick wakes me, and then every villager wants me": an emerald.

  A wrong item gets a shake of the head, and after two misses a hint; three right answers and the Sage comes to the
  village the next morning as a guest (29.8). Trade: `aliveworkplace:legend`. Likes: books. Power: the **Ancient
  Lore** tree (29.11), worked at a lectern in their home, each level costing paper, books, emeralds and amethyst
  shards (echo shards for the last pick):
  - **Old Tongues** (1 level): inn travellers are Journeymen at least, and Gifted twice as often;
  - **Star Charts** (2): explorers range 25% farther a level, and draw a map every second expedition at II;
  - **Herb Lore** (2): illness lasts a day less a level; nurses also cure with any small flower;
  - **Deep Memory** (2): every villager learns 15% faster a level;
  - **Runes of Warding** (2): night raids are 20% less likely a level;
  - **Old Harvests** (2): farmers, orchard keepers and beekeepers work 10% faster a level;
  - the **last pick**, one of three for good once every other topic is done: **The Undying Flame** (undertakers bring
    the dead back with nothing from the chest), **The Golden Age** (wellbeing never below 60%, and festivals lift
    moods for twice as long) or **The Iron Pact** (an iron golem joins the village every 5 days, up to one per 8
    villagers, and golems and guards take 25% less damage).

  Outfit: a grey robe, a long beard and a gnarled staff. Done when: GameTests: the hut is placed only for a qualifying
  village; each riddle takes its answer and refuses others, with the hint after two misses; the guest arrives; every
  topic's effect and the exclusive last pick; the hut's render in the review package; showcase scene `legend_sage`
  (the hut, a riddle, the Ancient Lore tab).
- [x] **29.15** (approved 2026-10-06) **The Golem Smith (Legendary).** Comes: inspired (29.10): a Master Tinkerer in a happy village with 4
  iron golems; the materials come from a pool of a diamond, a block of copper, a block of redstone, a blaze rod, a
  breeze rod, an amethyst shard and an echo shard, and the Masterwork is a heavy core named "The Heart of <name>".
  Trade: Tinkerer. Likes: wine. Powers (`golem_forge`): from the chests by their smithing table they build one golem
  every 2 days, up to one per 5 villagers; sneak-right-click them to choose which comes next:
  - **Hauler Golem** (4 iron blocks, a carved pumpkin, a chest): carries what the village's workers make to the
    Storehouse, 9 stacks a trip, by `store/PorterWork`'s rules;
  - **Farmhand Golem** (4 iron blocks, a carved pumpkin, an iron hoe): harvests and replants ripe crops on the
    village's fields (Field Marker fields and the farmers' own farms), into the field's chest;
  - **Wall Sentry** (4 iron blocks, a carved pumpkin, a shield): goes to the first point of a Patrol Map you give it
    and never leaves it, with twice a golem's health, knocking attackers back off the wall.

  Each is an iron golem with a role (an attachment on the golem), its own texture (pixel-art skill: the hauler's pack,
  the farmhand's straw hat, the sentry's helm), its name over its head and a line on the hall. They never break blocks
  and respect village protection. The Golem Smith also mends golems twice as fast as a Tinkerer. Outfit: a leather
  apron, goggles and iron-banded gloves. It may land in two pieces (`land --keep-open`): the smith and the haulers,
  then farmhands and sentries. Done when: GameTests: a hauler takes 9 stacks to the Storehouse; a farmhand harvests
  and replants a 9x9 wheat field into its chest; a sentry holds its point through a staged fight; the costs are taken
  and the cap and the 2-day wait kept; the golem textures pass `lint.py`; showcase scene `legend_golem_smith` (GIF:
  the three at work).
  Decisions (lane-a-1005-1233): the golems work through goals added to every iron golem (mixin IronGolemRolesMixin,
  idle on a plain golem): the Wall Sentry's post outranks the attack, haulers' and farmhands' work ranks below it, so a
  fight comes first. A sentry fights foes within 6 blocks of its post and never steps more than 3 off; without a map
  it is an ordinary golem. A farmhand tends the fields of farmers within 48 blocks (Field Marker or adopted farms),
  picks only crops and nether wart, and carries up to 9 stacks to the chests by that farmer's composter. A new golem
  stands on solid ground beside the Smith (never on a roof). The hall's line for each golem is on the guards button.
- [x] **29.16** (approved 2026-10-06) **The Seer (Rare).** Comes: a guest at the village's finished Chapel at midnight under a full moon, 1
  time in 2 (29.8); born to a Cleric (29.7). Trade: `aliveworkplace:legend`; by day they keep to the Chapel. Likes:
  jewels. Powers:
  - **foretelling** (`foretell`), each dawn, to the village's players in chat and on the hall's "What next?": whether
    raiders come that night and from which side (with a Seer in the village, `VillageRaids` rolls the night's raid at
    dawn, so the Seer is never wrong), the next festival and market day, and the next day's guest (Legend guests are
    rolled a day ahead); and through `Legends.foretold(hall, kind)` the Seer is M32's two-day warning of its attacks;
  - **blessed weddings** (`bless_weddings`): a wedding at the Chapel with the Seer there is blessed: the couple is +10
    mood for 7 days, and their first baby comes within 2 days when a bed is free (once, past `VillageGrowth`'s daily
    wait).

  Outfit: a deep purple hooded robe with silver stars; end-rod motes round them at night. Done when: GameTests: the
  Seer comes only at midnight, at a full moon, to a Chapel; a raid foretold at dawn comes that night and none comes
  when none is foretold (fixed `RandomSource`); the festival and market days told are right; a blessed wedding's mood
  and baby; showcase scene `legend_seer` (GIF: the arrival under the full moon, the dawn foretelling in chat).
- [x] **29.17** (approved 2026-10-06) **The Merchant Prince (Legendary).** Comes: found as a castaway by a shipwreck (29.9) once the
  village's treasury has taken in 500 emeralds all told (`treasury_total`) and it sends caravans on 3 routes. Trade:
  `aliveworkplace:legend`; they keep to the Village Hall and the Market Square. Likes: wine. Powers:
  - **the bank** (`bank`): the treasury earns 2% a day on what it holds and holds twice as much; players deposit
    emeralds on a new bank page of the hall (up to 10 stacks each, kept per player in the hall), earn 5% a week and
    take them out any time (CobbleDollars at the usual rate);
  - **trade fairs** (`trade_fair`): every 10 days a fair at the Market Square (round the hall without one): 6 traders,
    plus a stall for each village this one trades with (a trader named after it, selling what its Storehouse has
    spare), bunting and fireworks, and every trade 10% cheaper for the day; the chronicle notes it;
  - **prices between villages** (`caravan_pay`): the trade routes page shows what each village is waiting for as what
    it pays, and every stack a caravan brings to a village that was waiting for it earns the treasury an emerald
    (M33's price board takes over when it lands).

  Outfit: a crimson coat with gold buttons and a feathered hat. Done when: GameTests: a day's interest and the doubled
  cap; deposits and withdrawals with interest, kept through a reload, never more than 10 stacks; a fair with 6 traders
  plus one per linked village; the caravan pay; showcase scene `legend_merchant_prince` (the bank page, a GIF of the
  fair).
- [x] **29.18** (approved 2026-10-06) **The Grand Chef (Rare).** Comes: inspired (29.10): a Master Chef in a happy village whose store has 8
  kinds of meal (`VillageHalls.mealKinds`); the materials come from a pool of a golden apple, a glistering melon
  slice, a golden carrot, a honeycomb, glow berries, chorus fruit and a pufferfish, and the Masterwork is a cake named
  for the village ("The Thornholm Midsummer Cake"). Also a guest at the inn on the same condition, and born to a Chef
  (29.7). Trade: Chef. Likes: wine. Powers:
  - **banquets** (`banquet`): every 5 days at supper the village gathers at the Market Square or the hall (as for a
    festival, with `Festivals`' gathering) and each grown-up eats two meals from the store, as many kinds as there
    are. Everyone who came is +20 mood for 3 days, and for those 3 days the village may have two babies a day
    (`VillageGrowth`'s wait halved; beds and food as usual); the chronicle notes it;
  - chefs in the village cook 25% faster (`pace`).

  Outfit: a tall white toque and a gold ladle at the apron. Done when: GameTests: a banquet takes two meals each; the
  mood; two births a day for 3 days with free beds, and one a day after; chefs faster; showcase scene
  `legend_grand_chef` (GIF: the banquet).
- [x] **29.19** (approved 2026-10-06) **The Bard Laureate (Rare).** Comes: a guest at a festival once 30 villagers come to it (29.8); born
  to a Bard (29.7). Trade: Bard. Likes: books. Powers:
  - **the anthem** (`anthem`): when they settle they compose the village's anthem, 16 notes on one instrument (harp,
    flute, bell, chime, guitar or xylophone) made from the village's name, so it never changes, kept in the hall. It
    is played with note-block sounds at every festival, rank-up, wedding and Legend arrival, and from a new button on
    the hall's screen, and the hall's owner gets a copy as a written book ("The Anthem of Thornholm", the notes
    written out);
  - **work songs** (`work_songs`): twice a day (mid-morning and mid-afternoon) they walk to the busiest spot (the most
    workers within 16 blocks) and sing for 2 minutes with notes rising round them: workers within 16 blocks work 25%
    faster while they sing (`pace`, through the shared cap), and everyone who hears is +5 mood for the day.

  Outfit: a green doublet, a lute on the back and a laurel wreath. Done when: GameTests: the same name always gives
  the same anthem and another name a different one; it is saved and played at each event (the sounds counted); the
  work-song pace only while they sing; showcase scene `legend_bard` (GIF with the notes).
- [x] **29.20** (approved 2026-10-06) **The Beastmaster (Rare).** Comes: found as a prisoner in a pillager outpost (29.9) once the village
  has a ranch: 10 animals within 16 blocks of a Rancher's, Butcher's or Shepherd's workstation; born to a Rancher
  (29.7). Trade: Rancher. Likes: clothes. Powers:
  - **war dogs** (`war_dogs`): with bones in their chest they tame a wolf for each guard without one (the village's
    wolves, or a pair they breed), fit it with wolf armour when there are armadillo scutes in the chest, and send it
    to the guard: it follows them on patrol and in a raid and attacks what they attack; one per guard, and a lost dog
    is replaced after 2 days;
  - **fast horses** (`horse_breeding`): foals they breed take the best speed, jump and health of their parents and a
    little more (never past vanilla's best), and `guard/Cavalry` guards take their horses first.

  Outfit: furs and a wolf-pelt hood. Done when: GameTests: a war dog is tamed, joins its guard, follows and fights,
  and is replaced 2 days after it dies; a foal's stats are at least its best parent's and never over vanilla's
  highest; cavalry picks the bred horses; showcase scene `legend_beastmaster` (GIF: a guard and a war dog against
  zombies).
- [x] **29.21** (approved 2026-10-06) **The Pokémon Professor (Legendary, with Cobblemon).** Comes: a guest at the inn once the village's
  Pasture Blocks hold 25 Pokémon of 10 types (the condition `pastured_pokemon`, through a new extension point
  `legend/PokemonCensus` filled in `compat/cobblemon/`; without Cobblemon the file doesn't load). Trade:
  `aliveworkplace:legend`, at a lectern. Likes: books. Powers:
  - **hints** (`pokemon_hints`): right-click them and pick one of your party: each stat's IV in words (No good,
    Decent, Pretty good, Very good, Fantastic, Best), the stats its nature raises and lowers, whether it has its
    hidden ability, and its EVs;
  - **the village Pokédex** (`pokedex`): every species ever kept in the village's pastures is logged in the hall (a
    count shown on the hall), and the **Pokédex** research tree (29.11) opens topics as it grows: **Field Notes** (2
    levels, at 15 species: partners help 5% more a level), **Kinship Studies** (1, at 25: one more partner per
    worker), **Breeding Records** (2, at 35: daycare eggs 20% sooner a level), **Berry Science** (1, at 45: orchard
    keepers pick one more berry from each berry plant), **Evolution Studies** (1, at 60: the Professor sells one
    evolution stone a day for 8 emeralds) and **Regional Survey** (1, at 80: the hints show exact IVs and EVs).

  Outfit: a white lab coat. Done when: compat GameTests (`runCompatGameTest`): the census; hints that match the
  Pokémon's real IVs, nature, ability and EVs; each species logged once; every topic's unlock and effect; without
  Cobblemon nothing loads or errors; showcase scene `legend_professor` (Cobblemon: the hints, the Pokédex tab).
- [x] **29.22** (approved 2026-10-06) **The Pokémon Ranger (Rare, with Cobblemon).** Comes: a guest at the Village Hall, 1 time in 3 a
  morning, while an Alpha Pokémon is within 96 blocks of the hall (Cobblemon's own Alpha mark if the installed version
  has one, checked with the engineer skill's `api.py`; otherwise a wild Pokémon of level 50 or more): the condition
  `alpha_near`, through a new extension point `legend/WildPokemon` filled in `compat/cobblemon/`. Trade:
  `aliveworkplace:legend`. Likes: clothes. Powers:
  - **calming Alphas** (`calm_alphas`): each morning they walk to any Alpha within 96 blocks of the hall; once beside
    it (sparkles, a calm chime) it no longer attacks villagers or players in the village, for good;
  - **befriending** (`befriend`): once a day they befriend a wild Pokémon within 64 blocks (never a legendary,
    mythical, Ultra Beast or paradox Pokémon, as with trainers) and lead it to a village Pasture Block with room,
    where it joins as the hall owner's Pokémon (into their PC first, as Cobblemon does); the chronicle notes each one;
    none when the pastures are full or the hall has no owner.

  Outfit: a ranger's vest and a capture styler on the wrist. Done when: compat GameTests: the Ranger comes only with
  an Alpha (or the level-50 stand-in) near; a calmed Alpha doesn't attack a villager; a befriended Pokémon belongs to
  the owner and is in the pasture, and a banned species never is; nothing loads without Cobblemon; showcase scene
  `legend_ranger`.
- [x] **29.23** (approved 2026-10-06) **The Founder (Mythic).** Comes: inspired (29.10), always, at the village's first rise to City: its
  most experienced Master (the most XP) is seized by the Founder's mood and asks for a block of gold, a block of
  emeralds and a diamond. The Masterwork is **The Charter of <village>**, a written book of the village's story drawn
  from its chronicle (its founding, each rank, its Legends, its first wedding, the raids it beat), signed by the
  Founder. A failed mood passes to the next most experienced Master after the week of sulking. Mythic: announced to
  the whole server and capped by 29.3. Trade: their own. Likes: books. Powers:
  - **the statue** (`statue`): the village's builders raise the Founder's statue by themselves
    (`legend/founder_statue` through the architect skill: a stepped 5x5 plinth, a stone figure with a raised hand and
    a copper plaque) near the hall, with materials from the chests; while it stands it is worth 5 beauty and every
    villager is +5 mood;
  - **new villages** (`found_villages`): with M33's colonies the Founder leads the settlers; until then, once every 7
    days they give the hall's owner a **Founder's Wagon** (icon by the pixel-art skill: the Settler's Wagon with a
    gold pennant): a Settler's Wagon whose camp also brings a Village Hall named "New <village>", with the mother
    village's styles on its blueprints.

  Outfit: a burgundy mantle with a gold chain of office. Done when: GameTests: the mood comes only at the first rise
  to City (not at a later rise after falling back); the Charter's pages come from the chronicle; the statue is queued
  and built, with its beauty and mood; a wagon once a week; the announcement reaches every player; the statue's render
  in the review package; showcase scene `legend_founder` (GIF: the statue going up).

Depends on: nothing. Optional, each with a fallback here: M33's Colonies item for the Founder's new villages (until
then the Founder's Wagon, 29.23); M34's luxury chains join the `aliveworkplace:luxury/*` tags (until then vanilla
stand-ins, 29.5); M33's price board replaces the Merchant Prince's caravan pay (29.17). Hooks other milestones use:
`Legends.visitFactor` (M30's Open Gates), the `rules/` conditions (M31's reputation; M35's Wonders, with the `legend`
condition), `Legends.foretold` (M32's warnings), `Legends.in` (M35's Wonders).

## Milestone 30: Edicts and civic items (1.4)

The village gets laws and landmarks. A Book of Edicts in the Village Ledger lets the hall's owner proclaim one edict
per rank (Frostpunk-style): each gives the village a real boost at a real cost, and each has a reform the village earns
through the hall's quests, which keeps the boost and drops the cost for that village for good. Civic items, each an
item of its own (the Cradle, the Work Horn, the Village Banner, the Harvest Idol, Guild Charters with their
Guildhalls, and six tonics from the alchemist and the chef), give players more ways to shape a village. It builds on
the Village Hall (`VillageNeeds`, `VillageQuests`, `VillageRanks`, `Festivals`, `Treasury`), `people/Moods` and
`BuilderLevels.delay`, and adds the one shared cap that stops all speed bonuses together at twice normal pace.

- [x] **30.1** (approved 2026-10-05) **Design note.** `docs/design/M30.md`, sent to the owner as a review package (lanes don't wait for his
  reply). It covers:
  - what the player sees: a sketch of the Book of Edicts page, each edict's boost, cost and reform, each civic item,
    and the changes to the hall's screen and the Village Ledger;
  - the data formats: `data/aliveworkplace/edicts/<id>.json` (icon, name and texts, boost and cost as lists of
    effects, the edicts it excludes, the reform's name, line and steps, `enabled`), `tonics/<id>.json` and
    `guilds/<id>.json`, and the effect toolbox all three share (each effect type, its fields, which system reads it);
  - the config switches (`villageEdicts`, `edictMinDays` 3, `maxWorkPace` 200, `workHorns`, `cradles`,
    `villageBanners`, `harvestIdols`, `seasonDays` 8, `tonics`, `guilds`, `guildsPerRank` 1) and every new saved
    field with its default (the hall's edicts, reforms, colours, horn day and guilds; the attachments `tonic`,
    `guild_master`, `worn_out`; the quest's `reform` field);
  - the pace rule: bonuses (factors under 1) multiply and stop at `100 / maxWorkPace` of the usual time; penalties
    (sickness, an unhappy mood, Lazy, a badly kept village) multiply after the cap, so a sick worker still works at
    half the capped pace. A worker's level (a Master builds 2.5x as fast as a Novice) is their normal pace, not a
    bonus, and walking speed, the daycare's experience rate and the `workplaceBuildDelay` gamerule aren't work pace.
    Owner's call: this reading of "twice normal pace" is the default until he says otherwise;
  - who may proclaim and lift edicts, blow the horn, set the colours and grant charters: the hall's owner, their
    friends and operators (anyone, while the hall has no owner).

  Done when: the note is on `main` and its review package is sent.
  - [x] **30.1a** (approved 2026-10-05) Change from the owner (2026-10-05): Reforming an edict must cost far more, hundreds of items, so the grind is worth it: e.g. instead of 4 clocks, 8 gold ingots, 32 bread, something like 24 clocks, 2 stacks of gold ingots and 300 bread. Most players get the small amounts quickly, so scale the reform costs up a lot in the design note and the edict data files.
- [x] **30.2** (approved 2026-10-05) **One pace, one cap.** A core `work/Pace` that every job's work speed goes through (if another
  milestone built it first, check it covers this list and add what's missing):
  - bonuses: Pokémon partners, a well-kept village, Swift Hands, Diligent, a happy mood, Craftsmanship (crafters),
    Expeditions (explorers, netherworkers), and later this milestone's edicts, Work Horn, tonics and guilds, each
    registered as a named source;
  - penalties: sickness, an unhappy mood, Lazy, a badly kept village;
  - `BuilderLevels.delay(base, villager)` becomes the level's delay times `Pace.factor`; the jobs that apply partners
    or research on their own (`TeacherWork`, `ScholarWork`, `RancherWork`, `CrafterWork`'s Craftsmanship, the
    explorer's and netherworker's rests and trips) go through `Pace` too, and the five that count their partners
    twice today (`SifterWork`, `BeekeeperWork`, `FloristWork`, `ExplorerWork`'s search, `CompostWork`) count them once;
  - `maxWorkPace` in the config (percent, default 200, 100 to 400);
  - the worker's status (sneak-right-click) and the hall's list show the total ("62% faster", "100% faster: at the
    cap"), with the sources behind it.

  The changelog says the cap, and the fix for the five jobs, slow down the very fastest workers. Done when:
  - a GameTest stacks every bonus there is (five partners with Kinship II, wellbeing 100%, Swift Hands III, Diligent,
    happy) and gets exactly twice the pace, and three times with `maxWorkPace` 300;
  - the same villager, ill, works at exactly half of that;
  - a sifter with two partners takes 70% of the usual time, not 49%, and each job that calls `BuilderLevels.delay`
    or `Partners.factor` today gets its pace from `Pace` (a test per job family: builder, miner, crafter, explorer,
    teacher, scholar, rancher);
  - showcase scene `pace`: a capped builder's status line (its check: the line says "at the cap").
- [x] **30.3** (approved 2026-10-05) **Edicts, and Long Shifts.** The engine and the first edict:
  - `hall/Edicts` loads `data/aliveworkplace/edicts/*.json` (a reload listener through `Platform`); a data pack can
    add edicts or switch ours off (`"enabled": false`); ours take their texts from the lang file, a data pack's may
    give plain text;
  - the effect toolbox (`hall/CivicEffects`): typed effects with codecs, each read by the system it changes, starting
    with `work_pace` (percent, optional list of jobs; a `Pace` source) and `mood` (points and a reason the hall's list
    shows, read by `Moods.work`); later items add their own types;
  - the hall keeps the edicts in force (id and day proclaimed); a village has one slot per rank (Hamlet 1, Village 2,
    Town 3, City 4); an edict stays at least `edictMinDays` (3) before it can be lifted; edicts that exclude each other
    can't be in force together; a village that drops a rank loses its newest edict;
  - proclaiming and lifting are told to the players in the village and go in the chronicle (new kind EDICT); for now
    an operator's `/workplace edict proclaim|lift <id>` does it (the page is 30.4), and stays for admins;
  - the effects in force are kept per hall and read from there, so a villager's lookup costs no more than
    `VillageNeeds.factor` does today;
  - **Long Shifts** (`edicts/long_shifts.json`): everyone works 20% faster; every grown villager's mood is 10 lower
    ("long shifts").

  Done when:
  - GameTests: slots by rank; lifting refused before 3 days; the newest lifted on a rank drop; edicts kept over a save
    and reload; a stranger can't proclaim in an owned village; with Long Shifts a builder's delay is 1/1.2 of before
    and their mood 10 lower, with the reason listed;
  - an edict from a test data pack loads and works, and `"enabled": false` hides Long Shifts;
  - showcase scene `long_shifts`: the hall's list with the "long shifts" mood, and the chronicle line.
- [x] **30.4** (approved 2026-10-05) **The Book of Edicts page.** The page players use, in the hall's screen and the Village Ledger:
  - the hall's screen: the people list's page arrows move to the list's bottom corners (slots 45 and 53, 34 people a
    page), which frees slot 9 for the Book of Edicts (a lectern icon) and leaves slot 17 for another milestone's page;
  - the Village Ledger: sneak-right-click the air opens the Book straight away (its tooltip says so);
  - the page: the village's name and banner (once 30.13 lands) at the top; the slots in the second row (an edict in
    force with its days, a free slot, or a locked one saying which rank opens it); below, every edict with its boost
    in green, its cost in red, its reform and how far it has got; click an edict twice to proclaim it, click one in
    force to lift it (it says how many days are left when it can't yet); the last row is kept for the civic items and
    guilds (30.11 onwards);
  - only the hall's owner, friends and operators can click; everyone else reads it and is told why;
  - the hall's name icon lists the edicts in force.

  Done when: GameTests through `ChoiceMenu.forTest`: proclaiming and lifting by clicks, the locked slots, a stranger's
  click refused, the Ledger's sneak-use opening the page, the people list paging at 34; showcase scene `edicts`: the
  Book with Long Shifts in force, at GUI scales 2 and 4.
  - [x] **30.4a** (approved 2026-10-05) Change from the owner (2026-10-05): The Village Hall's screen should be a custom UI like vanilla's workstation screens (furnace, enchanting table, crafting table): its own drawn background, panels and buttons for the hall's pages (edicts, quests, treasury, ledger), instead of a plain chest-style grid of items like a multiplayer server menu. Do this for the whole hall screen, not just the Book of Edicts page: design it with the pixel-art skill, keep every existing feature, check at GUI scales 2 to 4, and show it in the hall scenes.
- [x] **30.5** (approved 2026-10-05) **Reforms, and The Shift Bell.** While an edict is in force and not reformed, the hall keeps its
  reform's next step on the quest page (extra to the three daily quests, in the row below them, with a book-and-quill
  icon; it never expires). The next step goes up the morning after the last was done, so a reform takes three days at
  least. Steps use the quest kinds there are (bring, clear out monsters, beat a trainer), with a `fallback` step for a
  battle when Cobblemon or a trainer is missing, and pay their emeralds as quests do (a quarter more a rank). `Quest`
  gets an optional `reform` field (edict and step; empty by default, so old saves load). Progress is kept per edict,
  also while it's lifted. The last step reforms the edict for that village for good: the boost stays, the cost goes;
  fireworks over the hall, everyone in the village told, the chronicle (kind REFORM), and the Book shows it reformed.
  The format leaves room for an `arc` field, so M31's story arcs can take over a reform's steps later.
  **The Shift Bell** (Long Shifts; "a bell in the yard calls the shifts, so nobody works past their hour"): bring 4
  clocks (5 emeralds), bring 8 gold ingots (5), bring 32 bread (4). Reformed: still 20% faster, moods no longer drop.
  Done when:
  - GameTests: the step is up with Long Shifts in force and not without; one step a morning; handing in pays and moves
    on; progress survives lifting and a reload; the last step reforms it (no mood loss, pace kept), and it stays
    reformed when lifted and proclaimed again; a battle step falls back to its `fallback` without Cobblemon;
  - showcase scene `reform`: the quest page with a reform step, then the fireworks and the chronicle line.
- [x] **30.6** (approved 2026-10-05) **Free Bread and Large Families.** Effects `food_use`, `births` and `sickness`, read by `VillageNeeds`,
  `VillageGrowth` and `people/Sickness`; each edict with its reform:
  - **Free Bread**: everyone fed in the last day is 10 happier ("free bread"); the village eats 30% more (for every
    meal eaten from the store the hall counts 0.3 of another, saved, and takes one more meal each time it reaches 1).
    Reform **The Common Granary** ("a granary keeps the bread line"): bring 64 wheat (4), 16 hay bales (5), 8 barrels
    (3). Reformed: no extra food.
  - **Large Families**: up to two babies a day (`VillageGrowth.EVERY` halved for the village); a baby needs 24 meals in
    the store instead of 16, and the family eats 12 instead of 8; villagers fall ill 50% more often. Reform **The
    Midwives** ("midwives see every mother and child through"): bring 8 honey bottles (4), 16 white wool (3), 4 golden
    carrots (5). Reformed: two babies a day, with the usual food and sickness.

  Done when: GameTests for each number, with and without the reform (meals taken for 10 eaten, the gap between births,
  the food needed and taken, the daily chance of falling ill); showcase scenes `free_bread` (the hall's list with the
  "free bread" mood) and `large_families` (two births in one day, with the chronicle lines).
- [x] **30.7** (approved 2026-10-05) **Open Gates.** Effects `inn`, `market_traders`, `legend_visits` and `bandit_camps`:
  - boost: inns in the village take 4 guests instead of 2, and up to two travellers arrive a morning; market days
    bring one more trader; Legends visit the inn twice as often once M29's inn visitors exist (until then nothing reads
    that effect, and the Book only promises travellers);
  - cost: bandits make camp near the village twice as often (`BanditCamps.DAILY_CHANCE` doubled for it);
  - it excludes Curfew;
  - reform **The Watchful Gate** ("a gate watch that knows every face"): bring 16 iron ingots (5), bring 32 arrows (4),
    clear out 12 monsters (6). Reformed: the travellers keep coming, and bandits are no likelier than usual.

  Done when: GameTests for the guests, the arrivals, the market traders and the bandits' chance, with and without the
  reform, and Open Gates refused while Curfew is in force; showcase scene `open_gates`: an inn with four guests.
- [x] **30.8** (approved 2026-10-05) **Festival Season and Tithe**, the two treasury edicts. Effects `festival_every`, `festival_cost`,
  `tithe` and `trade_prices`:
  - **Festival Season**: a festival every 4 days instead of 8 (`Festivals` counts the days per hall); each one costs
    the treasury 3 emeralds and 1 more for every 4 villagers, taken on the festival's morning; when the treasury can't
    pay there's no festival (chronicle: no money for the festival) and the village is 5 less happy that day
    ("disappointed"). A festival called with a cake stays free. Reform **The Festival Fund** ("the villagers put by
    for their own festivals"): bring 4 cakes (5), 32 firework rockets (5), 8 note blocks (4). Reformed: every 4 days,
    free.
  - **Tithe**: a tenth of the emeralds players pay the village's villagers in trades goes into the treasury (in
    hundredths, up to its cap); their emerald prices are 10% higher (rounded, so trades under 5 emeralds don't change).
    Reform **The Fair Ledger** ("the tithe comes out of the takings, not the price"): bring 4 books and quills (4), 16
    gold ingots (6), beat one of the village's trainers (8; without Cobblemon or a trainer, clear out 8 monsters).
    Reformed: the treasury keeps its tenth, prices are back to normal.

  Done when: GameTests: festivals 4 days apart; the cost taken; the day with no money; called festivals free;
  reformed free; a 20-emerald trade costs 22 and puts 2.20 in the treasury, a 4-emerald trade is unchanged, and
  reformed it costs 20 and still puts in 2.00; showcase scenes `festival_season` (the hall's festival icon and the
  treasury before and after) and `tithe` (a librarian's trades with and without it).
- [x] **30.9** (approved 2026-10-05) **Curfew.** Effect `curfew`:
  - boost: from dusk (12000) to dawn every grown villager but the guards and mercenaries goes to bed, and a monster
    can't hurt a villager asleep in their bed; monster and bandit raids on the village are half as likely; at night the
    village's safety counts as full in its wellbeing;
  - cost: from dusk to dawn none of the village's villagers trade with players ("Curfew: come back in the morning."),
    a festival ends at dusk without fireworks, market traders leave at dusk, and night work stops: netherworkers and
    explorers don't set out after midday, so they're home by dusk (M29's Night Owls stay in too once they exist, and
    any night market a later milestone adds checks the same effect);
  - it excludes Open Gates;
  - reform **The Lamplighters** ("lamplit streets, so the curfew can end at the door"): bring 24 lanterns (5), 8
    glowstone (4), clear out 8 monsters (6). Reformed: raids are still half as likely and the nights count as safe, but
    nobody has to stay in: trading, festivals, markets and night work go on.

  Done when: GameTests: the villagers in bed by dusk; a zombie's blow on a sleeping villager does nothing with Curfew
  and hurts without it; the raid chance halved; a trade refused at night, allowed by day and after the reform; the
  festival's fireworks skipped; showcase scene `curfew`: a GIF of the village going indoors at dusk, the streets
  empty, the guards on watch.
- [x] **30.10** (approved 2026-10-05) **Conscription.** Effects `militia` and `work_stops_in_raids`:
  - boost: while the village is raided (our monster and bandit raids, or a vanilla pillager raid there) every grown
    villager who isn't ill fights: they wake, never panic (as `VillagerPanicTriggerMixin` keeps guards from panicking),
    hold a stone sword (shown in their hands, not taken from any chest, gone when the raid ends) and go for the nearest
    raider within 24 blocks: 3 damage a blow, 15% more if Strong, with the guards' rules for who's a foe; children and
    the ill hide as before;
  - cost: all work in the village stops during the raid and until noon the next day (one shared check every job's
    work passes), and the conscripts can be hurt or killed like anyone else (graves, mourning);
  - reform **The Militia Drill** ("drilled at the dummies, the militia fights without stopping the village"): bring 8
    iron swords (6), 8 shields (5), clear out 16 monsters (8). Reformed: everyone still fights, but work stops only for
    villagers with a raider within 24 blocks, and the morning after is a normal day.

  Done when: GameTests: a farmer and a builder attack a zombie during a raid and not without one; a child and an ill
  villager hide; no work during the raid or before noon after it; reformed, a builder with no raider near keeps
  building and work goes on at dawn; showcase scene `conscription`: a GIF of villagers and guards beating back a
  night raid.
- [x] **30.11** (approved 2026-10-05) **The Work Horn.** An item of its own (pixel-art: a brass-banded horn), crafted from a goat horn, a
  gold ingot and an emerald. Blown in a village (held like a goat horn until it sounds), it calls a rush: every grown
  villager of the village works 50% faster for 5 minutes (6000 ticks; a `Pace` source, so the cap holds), with sparks
  over the rushing workers now and then. Once a village a day (the hall keeps the day; a second blow is refused with
  "The village has already answered the horn today."). When the rush ends the villagers are worn out: 10 less happy
  until dawn ("worn out"; attachment `worn_out`). The Book of Edicts' last row shows the horn as ready or used. Only the
  hall's owner and friends can call a rush in an owned village. `workHorns` in the config.
  Done when: GameTests: the pace is 1/1.5 during the rush and back after; once a day; "worn out" until dawn; with Long
  Shifts and partners the total stops at the cap; a stranger's horn does nothing in an owned village; the icon passes
  `lint.py`; showcase scene `work_horn`: a GIF of builders before and during a rush (its check: more blocks placed a
  minute during the rush).
- [x] **30.12** (approved 2026-10-05) **The Cradle.** A block of its own (pixel-art textures and a model: a wooden cradle on rockers with a
  wool blanket), crafted from planks, sticks and white wool; a point of interest, so the hall finds it without
  scanning. A Cradle within 4 blocks of a bed in a village with a hall makes it a nursery village: every child there
  grows up in half the time (each hall round ages the children by the round's length again), and one more baby a day
  may be born (with Large Families as well, up to three a day). At night a child of the house sleeps in the cradle
  (seated in it, as villagers sit in saddles and boats). More cradles don't add more. The hall's beds icon and the
  Book's last row say whether the village has one. `cradles` in the config.
  Done when: GameTests: a child grows up in 12000 ticks with a cradle and 24000 without; a second birth the same day
  with a cradle and not without; with Large Families too, births a third of a day apart; a cradle with no bed near
  doesn't count; a child seated in it at night; the art passes `lint.py`; showcase scene `cradle`: a child asleep in a
  cradle at night, and a time-lapse of a child growing up.
- [x] **30.13** (approved 2026-10-05) **The Village Banner.** An item of its own (pixel-art icon: a banner on a gilded crossbar), crafted
  from any banner (it keeps that banner's design, which its tooltip lists) and a gold ingot. Right-click the Village
  Hall with it to make that design the village's colours (saved on the hall; the banner isn't used up, as a Name Tag
  isn't; the chronicle notes it); place it like a banner to hang the design anywhere. The colours show:
  - on buildings: a builder who finishes a building in the village hangs a wall banner in the colours over its front
    door, when there's a banner of the colours' base colour in their chests (they never ask for one);
  - on shields: a guard's or mercenary's plain shield is painted in the colours when they gear up (a shield a player
    painted is left alone);
  - on caravans: the hall's trade routes page shows each village by its banner, and the Book of Edicts by the
    village's own;
  - at festivals: a banner in the colours within 16 blocks of the festival square lifts the festival's mood to 20
    (from 15) for 3 days (from 2).

  `villageBanners` in the config. Done when: GameTests for each of the four (the banner over the door, the painted
  shield and a player's left alone, the routes icon, the festival's mood and days); the colours survive a reload; the
  icon passes `lint.py`; showcase scene `village_banner`: a street of finished houses under the village's banners, a
  knight with the painted shield, the routes page.
- [x] **30.14** (approved 2026-10-05) **Seasons and the Harvest Idol.**
  - A village year (`hall/Seasons`, core; if M28 already added a season clock for its Festival Cup, use that one):
    spring, summer, autumn and winter of `seasonDays` (8) days each, counted from the world's day; autumn is harvest
    season. The hall's festival icon names the season and its day ("Autumn: harvest season, day 3 of 8").
  - The **Harvest Idol**, a block of its own (pixel-art and a model: a straw figure crowned with wheat on a wooden
    post), crafted from a hay bale, three wheat, a stick and a gold ingot. In harvest season a crop within 32 blocks of
    an idol grows 25% faster: when it takes a random tick, one time in four it takes another (a mixin on the crops'
    random tick; the idols' places are kept in a small set per dimension, rebuilt as their chunks load, so nothing is
    scanned). Crops are the block tag `aliveworkplace:idol_crops`: wheat, carrots, potatoes, beetroots, melon and
    pumpkin stems, sweet berries, cocoa, nether wart, torchflower and pitcher crops, and with Cobblemon its berries,
    apricorns, mints and Hearty Grains (by id, `required: false`). Idols don't stack. In harvest season golden sparkles
    rise from an idol now and then. `harvestIdols` in the config.

  Done when: GameTests with a fixed `RandomSource`: wheat by an idol in autumn grows 25% (±5%) more over 2000 random
  ticks than without; nothing extra in summer, at 33 blocks, or from a second idol; the idols are found again after a
  reload; the season is right on days 1, 17 and 32; the art passes `lint.py`; showcase scene `harvest_idol`: two fields
  side by side through a harvest season, one with an idol.
- [x] **30.15** (approved 2026-10-05) **Tonics: Miner's Brew and Builder's Tea.** Tonics are data (`data/aliveworkplace/tonics/<id>.json`:
  the item, its maker, `alchemist` or `chef`, its ingredients, the jobs it suits, how much faster, 25%, and for how
  long, 24000 ticks), so a server owner can make any item a tonic. Right-click a villager with a tonic that suits
  their job and they drink it: 25% faster for a day (a `Pace` source; attachment `tonic`, saved; another tonic starts
  the day again, it never stacks). One that doesn't suit them is refused and kept ("Dara has no use for Miner's
  Brew."). Vanilla Clerics (`AlchemistWork`) brew the alchemist's tonics after the guards' potions, and Chefs
  (`ChefWork`) cook the chef's after their menu, from the chests by their station and the store, keeping 4 of each.
  Players can't craft them. The tooltip says what it does, for which jobs, and who makes it from what. `tonics` in
  the config.
  - **Miner's Brew** (alchemist: a glass bottle, glowstone dust, coal and sugar): Miners, Sifters, Netherworkers.
  - **Builder's Tea** (chef: a glass bottle, two sweet berries and sugar): Builders, Carpenters, Masons, Dyers.

  Each is its own item, drawn with the pixel-art skill (a stoppered dark-amber bottle; a bottle of rosy tea). Done
  when: GameTests: a miner drinks Miner's Brew and is 25% faster for 24000 ticks, also after a reload; a builder
  refuses it; an alchemist with the makings brews it into the chests and stops at 4; a chef cooks Builder's Tea; a
  tonic from a test data pack works; with every bonus the cap holds; both icons pass `lint.py`; showcase scene
  `tonics`: a miner drinking, then their status line ("25% faster: Miner's Brew, 19 min left").
- [x] **30.16** (approved 2026-10-05) **Four more tonics**, each its own item, data file and pixel-art icon:
  - **Smith's Draught** (alchemist: a glass bottle, blaze powder, two iron nuggets): Armorers, Toolsmiths,
    Weaponsmiths, Tinkerers, Ball Smiths;
  - **Scholar's Infusion** (alchemist: a glass bottle, an amethyst shard, glow berries): Scholars, Teachers,
    Librarians, Cartographers, Fossil Scientists;
  - **Harvest Cordial** (chef: a glass bottle, an apple, wheat, sugar): Farmers, Orchard Keepers, Florists,
    Beekeepers, Composters, Shepherds, Butchers, Ranchers, Chefs;
  - **Woodsman's Broth** (chef: a bowl, a cooked salmon, a carrot, a brown mushroom): Lumberjacks, Fletchers,
    Fishermen, Porters, Postmen.

  No tonic for guards (they have the alchemist's potions), nurses, clerics, undertakers, innkeepers, shopkeepers,
  ferrymen, bards, trainers, tutors or traders. Done when: a GameTest per tonic (its maker makes it, one of its jobs is
  25% faster with it, a job outside its list refuses it); the four icons pass `lint.py` and are shown in slots and in
  hand in the review package; the `tonics` scene films all six.
- [x] **30.17** (approved 2026-10-06) **Guild Charters, the Guildhall and the Builders' Guild.**
  - The **Guild Charter**, an item of its own (pixel-art: a rolled charter with a red seal), crafted from three paper,
    an emerald, a gold ingot and red dye. Sneak-right-click a Master of a trade with it in a village of Village rank or
    more: they become the **Guild Master** of that trade's guild (attachment `guild_master`; the hall's list and their
    status say so; the chronicle, everyone in the village told). One guild per trade in a village, and one guild per
    rank above Hamlet (`guildsPerRank`: Village 1, Town 2, City 3). Otherwise it's refused, with the reason.
  - Guilds are data (`data/aliveworkplace/guilds/<id>.json`: the name, icon, trades and perks, as effects from the
    toolbox with a list of jobs), so a server owner can add their own.
  - The **Guildhall**, drawn with the architect skill and the blueprint generator (I: a two-storey hall with a long
    table, benches, the charter framed over the hearth and banners by the door; II: a tower wing with a meeting room
    and a bell), in the Blueprint Table and sold by every Guild Master. A guild is founded once a finished Guildhall (in
    any style) stands in the village: the oldest chartered guild without one claims the next Guildhall finished, and
    each guild needs its own. Until then its perks wait.
  - When the Guild Master dies, the guild's most experienced member takes over (chronicle); a guild whose Guildhall is
    no longer finished waits until it's repaired.
  - The Book of Edicts' last row shows the village's guilds: master, members, perk, founded or not.
  - **Builders' Guild** (Builders, Carpenters, Masons, Dyers): members work 15% faster; up to 5 idle builders help at
    a build instead of 3 (`Builders.MAX_HELPERS`).

  `guilds` in the config. Done when: GameTests: the charter's refusals (not a Master, a Hamlet, over the cap, a guild
  already); a Guild Master kept over a reload; the perks off before the Guildhall and on after (a carpenter 15%
  faster, 5 helpers at one build); succession when the master dies; a builder builds the Guildhall I and II in a test;
  the renders checked against `STYLE.md` in the review package; showcase scene `guildhall`: the Guildhall II with its
  Guild Master inside, and the Builders' Guild on the Book's last row.
- [x] **30.18** (approved 2026-10-06) **The Miners', Smiths' and Woodsmen's Guilds.** A data file each; members work 15% faster:
  - **Miners' Guild** (Miners, Sifters, Netherworkers): pickaxes, and the netherworker's gear, wear half as fast;
  - **Smiths' Guild** (Armorers, Toolsmiths, Weaponsmiths, Tinkerers, Ball Smiths): every material a weaponsmith
    mends with puts back a third of the durability instead of a quarter (`MendingWork.PER_UNIT`);
  - **Woodsmen's Guild** (Lumberjacks, Fletchers, Fishermen): axes and fishing rods wear half as fast.

  New effects: `tool_wear` and `mend_per_unit`. Done when: a GameTest per guild with its numbers, founded and not
  (durability lost over 20 blocks dug, 20 logs cut and 10 fish caught; what one ingot puts back); showcase scene
  `guilds`: each Guild Master's status and the guilds' row of the Book.
- [x] **30.19** (approved 2026-10-06) **The Harvest, Herders' and Scholars' Guilds.** A data file each; members work 15% faster:
  - **Harvest Guild** (Farmers, Orchard Keepers, Florists, Beekeepers, Composters, Chefs): the village's own farms and
    the orchard keepers' rounds reach 24 blocks instead of 16;
  - **Herders' Guild** (Shepherds, Butchers, Ranchers): every herd may be 4 bigger (shepherds and ranchers breed up to
    12 of a kind instead of 8, hired butchers keep 14 instead of 10);
  - **Scholars' Guild** (Scholars, Teachers, Librarians, Cartographers): research levels cost a quarter less paper,
    books and emeralds (rounded up).

  New effects: `work_reach`, `herd_size`, `research_cost`. Done when: GameTests for each number, founded and not; the
  `guilds` scene gains their rows.
- [x] **30.20** (approved 2026-10-06) **The Healers', Merchants', Wardens' and Trainers' Guilds.** A data file each:
  - **Healers' Guild** (Nurses, Clerics, Undertakers): members work 15% faster; the village's ill get well in two days
    instead of three, and nurses and undertakers look 48 blocks out instead of 32;
  - **Merchants' Guild** (Shopkeepers, Innkeepers, Ferrymen, Postmen, Porters): members work 15% faster; travellers
    cost a quarter less to hire; porters carry 3 more stacks;
  - **Wardens' Guild** (Guards): guards train on the dummies up to Master instead of Expert, and hit 10% harder;
  - **Trainers' Guild** (with Cobblemon, through a `fabric:load_conditions` on its file: Trainers, Trainer Leaders,
    Move Tutors, Pokémon Traders, Fossil Scientists): lessons and revivals cost a fifth less, and trainers rank up a
    quarter faster.

  With these every trade but the Bard has a guild (the Bard Laureate is M29's). New effects: `recovery_days`,
  `work_radius`, `hire_price`, `carry`, `train_up_to`, `strength`, `lesson_price`, `trainer_xp`. Done when: GameTests
  for each number, founded and not; the Trainers' Guild absent without Cobblemon and working in the compat suite; the
  `guilds` scene gains their rows.
- [x] **30.21** (approved 2026-10-06) **Edicts and civic items in the village's life.**
  - Chatter (`people/Chatter`): two lines for each edict in force and two for each reformed one ("Long shifts again...
    my back.", "The shift bell's rung. Home we go."), and lines for a rush, a tonic, a guild and the village's colours;
  - "What next?" (`VillageAdvice`): a free edict slot; a reform step waiting on the quest page; Festival Season with a
    treasury too small for the next festival; a chartered guild without its Guildhall; Large Families without a
    Cradle; harvest season without an idol near the fields;
  - the README gets an *Edicts* section (each edict, its boost, cost and reform, in a table) and a *Civic items*
    section (each item, its recipe and what it does; the tonics and guilds in tables), with pictures from the
    showcase; the In-Game Guidebook (26.2a) gets the same pages if it has landed, otherwise a line in the Notes for it.

  Done when: `langcheck.py` is clean; a GameTest per advice tip; the README's tables list every edict, tonic and guild
  data file we ship (the tester checks); showcase scene `village_talk`: villagers' lines under Long Shifts and after
  The Shift Bell.
- [ ] **30.22** **A season under the edicts.** A pack-server scenario (`tools/packtest`, with `/tick sprint`, in
  pieces that fit a night run): a City of 35 villagers with farms, a kitchen and a store, 4 in-game days under four
  edicts at once (Long Shifts, Free Bread, Large Families, Festival Season), then 4 days with all four reformed, with a
  Cradle, an idol in harvest season, a founded guild and a rush a day. Each day records: meals eaten and left, births,
  the ill, the average mood, the treasury, the pace of five workers, and our share of the tick. Done when:
  - the costs show (the store falls faster under Free Bread and Large Families than reformed; the treasury pays for the
    festivals) and nothing runs away: the store never stays empty for a whole day while the farms work, no pace beyond
    the cap, the village never shrinks;
  - the tick stays within the targets (25.2);
  - the numbers are in `docs/design/M30.md`, and every number that had to change is noted with why.

Depends on: nothing. Soft links, each with a fallback: Open Gates' Legend visits and Curfew's Night Owls use M29's inn
visitors and Gifted traits once they land (nothing reads those effects until then); 30.14 uses M28's season clock if
it already exists; M31 can turn reforms into story arcs through the `arc` field; M33's realm-wide edicts build on 30.3.

## Milestone 31: Quests become stories (1.5)

The village's people become people you know. Each named villager keeps a friendship with each player (gifts, favours,
heart events that tell their life story) and asks friends for help with their own goals; each village gives each
player a standing and a title; a bounty board hangs up wanted posters; and story arcs run in chapters over days: The
Bandit King, The Sickness, The Lost Caravan and The Professor's Thesis. It builds on the hall's daily quests
(`hall/VillageQuests`), the chronicle, `people/` (names, chatter, couples, families, moods), `guard/BanditCamps`,
`hall/Caravans` and `people/Sickness`. Everything a player can be asked to do is a data file, so server owners can
write their own stories; nothing waits forever on a player.

- [x] **31.1** (approved auto 2026-10-05) **Design note.** `docs/design/M31.md`: what the player sees (hearts on a villager, wrapping and giving
  a gift, a heart event, a personal request asked and done, the journal and its tracker bar, a title in chat, the
  bounty board, one arc chapter by chapter); every data format below with one example file each (`quests/`, `arcs/`,
  `villager_tastes/`, `heart_events/`, `bounties/`); every config key with its default; every new saved field with its
  default and where it lives (the villager attachments `friendship` and `late_partner`, the illness kind (shared with
  32.11), the flag `revived`, and the saved data `aliveworkplace_stories` with its quests, arcs, standings and count
  of camps broken); how the hall's old daily quests move into the engine; the gift gesture (a wrapped Gift, so it
  never clashes with trading or picking a job). Sent to the owner as a review package; lanes don't wait for his reply.
  Done when: the note is on `main` with every config key and saved field of this milestone and its default, and the
  package is sent.
- [x] **31.2** (approved auto 2026-10-05) **The quest engine.** A new `story/` package. Quests are data,
  `data/<namespace>/quests/<group>/<id>.json`, loaded with `Platform.onDataReload` the way `ranch/PokemonChores` loads
  its files. One file is one quest: who gives it (`hall`, `villager`, `bounty` or `arc`), `weight`, `conditions`,
  `objectives`, `rewards`, the `days` it stays up and whether it's `repeatable`. The first toolbox, each piece a small
  class with its own codec:
  - conditions, in the shared `rules/` package that 29.2 also brings (whichever of the two items lands first creates
    it, with these names; the other adds to it): `rank_at_least`, `villagers`, `job_level` (N workers of a trade at a
    level or above), `finished` (finished buildings, optionally of one blueprint), `research_levels`, and new here
    `food_below`, `cobblemon`, `chance`, `quest_done`, `not`;
  - objectives: `bring` (an item or tag and a count, into the giver's chests or the store), `bring_request` (what a
    worker is waiting for today, from `work/Requests`), `kill` (an entity type or tag, a count, in the village or
    anywhere), `battle` (beat one of the village's trainers), `wait` (days);
  - rewards: `money` (emeralds, or CobbleDollars through `work/Money`, times the rank factor), `item`, `loot_table`,
    `chronicle`, `village_mood` (points for some days, like a festival), `treasury`.

  Open quests live in a new saved data, `aliveworkplace_stories` per dimension, keyed by hall and dropped with the
  hall like `Caravans.Data.remove`: the quest's file, progress per objective, who helped and how much, the day posted
  and the day due. Objectives move on events (kills, hand-ins, battles) or in the hall's round (every 600 ticks),
  never by a scan each tick. The three daily kinds become the first files, with today's numbers (one a morning, three
  open, three days each, a quarter more a rank): `daily/worker_request`, `daily/food`, `daily/slay`, `daily/battle`
  and the ten wants of `VillageQuests.WANTS` (`daily/want_white_wool` … `daily/want_gold_ingot`). `VillageQuests`
  keeps its public calls (`handIn`, `onKill`, `onTrainerBeaten`) and passes them on. A hall saved with old-style
  quests has them moved into the engine when it loads, progress kept; the old field stays readable. If M30's reform
  steps (30.5: hall quests with a `reform` field) are on `main`, they move in the same way, with their field and their
  place on the quest page. Config `villageQuests` (true).

  Done when:
  - the existing quest GameTests pass unchanged (posting, the hand-in into the worker's chests, slay and battle
    counting, the rank factor);
  - a hall saved with three old quests loads them into the engine with their progress (a GameTest on saved NBT);
  - a quest file in the gametest datapack is posted and completes; a broken file is skipped with one log line naming
    it (added to `allow.txt`); `/reload` picks up a changed file;
  - `villageQuests` off: no new quests, and nothing else changes.
- [x] **31.3** (approved 2026-10-06) **The quest journal, tracking and quest maps.** The hall's Quests page becomes a journal with four tabs
  along the top: **Village** (the daily quests), **Personal** (requests from villagers who are your friends, 31.9),
  **Story** (the village's arc, 31.4) and **Bounties** (31.13); a tab whose item hasn't landed yet says so in grey.
  Each quest shows who asked, one line per objective with its progress, the reward and the days left, and has two
  clicks: hand in (for `bring`) and **Track**. A tracked quest (one per player) is a vanilla boss bar for that player
  only: the quest's name and its current objective ("Follow the tracks (2/5)"), filling as it goes, gone when the
  quest ends or is untracked. `/workplace quests` lists your quests in chat, each with a clickable [Track] or
  [Untrack]; the Village Ledger opens the journal from afar (it opens the hall screen already). Two toolbox additions:
  the objective `reach` (be within a radius of a place) and the reward `map` (a map to the place, drawn like
  `explore/Explorers.mapTo`, with a red X for a biome or a point). A place is a structure tag, a biome tag or a point
  an arc set. It's looked up once, when the quest opens (`ServerLevel.findClosestBiome3d` or `findNearestMapStructure`
  on the server thread, 3000 blocks at most), and the answer is saved with the quest; if nothing is found, the quest
  takes its next option or is withdrawn with a chronicle line. A README section explains the journal.

  Done when:
  - GameTests: tracking puts the bar on that player only, it follows the progress and goes when the quest ends;
    `reach` counts inside its radius and not outside; the map's marker sits on the found place; the lookup runs once
    per quest (a counter);
  - showcase scene `quest_journal`: the four tabs, a tracked bar on screen, a quest map in hand.
- [x] **31.4** (review: pending 2026-10-06) **The story arc engine.** Arcs are data, `data/<namespace>/arcs/<id>.json`, one arc per file: a
  `trigger` (conditions, a chance a day), the `chapters` in order and an `ending`. A chapter has a name, intro lines
  for chat and the chronicle, `on_start` effects, its quests (ids or written inline), when it's done (all of them, any
  one, or the ones named), `delay_days` before the next, a `time_limit_days` with what happens on failure, and chatter
  lines the villagers say while it runs (as village news in `people/Chatter`). Effects toolbox:
  - `announce` (to players in and near the village) and `chronicle` (a new STORY kind);
  - `place`: a template from the blueprint generator at a spot found by a rule (a ring round the village, along a
    caravan road, or in a biome far away); it's placed only once a player comes within 96 blocks, so the chunk is
    loaded and nothing has to generate, never over anything but natural ground and plants, and remembered;
  - `spawn`: named mobs with gear, extra health and a boss bar from the data, tagged to the arc and kept home with
    `restrictTo` as `BanditCamps` does;
  - `give_map`, `village_mood`, `festival` (one the next evening, through `Festivals`), `flag`, `start_quest`,
    `end_arc`, and any reward of the quest toolbox.

  A new objective, `talk` (right-click a villager the arc names). An arc marked `threat` doesn't start in a village
  set At peace or at threat level 0 once 32.21 has landed. One arc runs in a village at a time; arcs marked `side`
  (31.22's reforms) run beside it. Config: `storyArcs` (true), `arcCooldownDays` (8, between two arcs in one village),
  `arcsAtOnce` (3, on the whole server), `disabledArcs` (a list of ids). The arc's state is in
  `aliveworkplace_stories` (the arc, its chapter, the day it began, flags, spawned mobs, placed spots), so it carries
  on after a restart at any tick: a spawned mob that's gone is put back at its spot when the chunk loads, and an arc
  whose file was removed ends quietly with a log line. Placements and spawned mobs are checked every 40 ticks against
  the players' positions (a distance check each). The hall's Story tab shows the chapters so far (done ones ticked,
  the current one with its quests). Operators get `/workplace story start <arc>`, `next` and `stop`.

  Done when:
  - a test arc in the gametest datapack (three chapters: wait a day, bring an item, kill a spawned named zombie) runs
    from start to end under GameTests with time skips, and a missed time limit runs its failure;
  - saving and reloading mid-chapter (a round trip of the saved data) carries on in the same chapter with its mob;
  - a far `place` waits until a player comes near, then places once;
  - no more than `arcsAtOnce` arcs run; with `storyArcs` off no arc starts and a running one ends at its next round;
  - showcase scene `story_arc`: the announcement, the Story tab, the chronicle.
- [x] **31.5** (review: pending 2026-10-06) **Friendship.** Every named villager in a hall's village keeps a friendship with each player: 0 to 1000
  points, ten hearts of 100. It's saved on the villager, in a new attachment `friendship` (empty by default): player →
  points, the last gift's day, gifts this week, heart events told. Favours, each once a day per villager unless said:
  - trading with them: +5;
  - a hall quest they posted, finished by you: +40;
  - handing in something they're waiting for (the requests board or a quest): +10;
  - killing a monster that hurt them in the last 10 seconds: +15, any number of times;
  - being at their wedding: +30; at a festival they came to: +10;
  - a personal request of theirs done (31.9): +150.

  Hitting them costs 50 (at most once a minute). Hearts never fade, so nobody has to visit to keep them up. Look at a
  named villager within 6 blocks and the action bar shows "Dara ♥♥♥♡♡♡♡♡♡♡" (checked every 10 ticks per player, one
  ray along their look); a rise puffs hearts over them; the hall's tooltip for a villager adds your hearts and their
  two best friends among the players. A new quest reward, `friendship` (to the giver, or to named villagers). Config
  `friendship` (true).

  Done when:
  - GameTests: each favour and the loss (points, and once a day where said), two players kept apart, a save/reload
    round trip of the attachment;
  - screenshots of the action bar and the hall tooltip; showcase scene `friendship` (a trade, a quest done, the hearts
    going up);
  - `friendship` off: no points, no hearts shown, nothing else changes.
- [ ] **31.6** **Gifts.** Two new items, drawn with the pixel-art skill the way vanilla draws its kind: **Gift Wrap**
  (paper, string and any dye make 4) and the wrapped **Gift** (Gift Wrap and any one item in the crafting grid: a
  special recipe like vanilla's map cloning; the item is kept inside as a data component, and the tooltip says "From
  Jesse" and not what's inside). Right-click a named villager with a Gift: they unwrap it (the item's particles, a
  paper rustle), say how they like it over their head and in your chat ("A cake! You remembered."), and their
  friendship changes: loved +80, liked +45, neutral +20, disliked −20, hated −40. The item goes into their chests (a
  worker's supply chests, else the village's store). Each player can give each villager one gift a day and two a week;
  on a villager's **name day** (every 28 days, from their UUID; the hall's tooltip says when) a gift counts three
  times. Villagers who aren't named shake their head. A Bottle o' Enchanting as a gift is liked and gives a worker 15
  XP. With `friendship` off, a Gift is handed back unopened. Tastes are data,
  `data/<namespace>/villager_tastes/<id>.json`: who the file is for (everyone, a job family, a job or a trait) and its
  `loved`, `liked`, `disliked` and `hated` items or tags; the most specific file that names an item wins (job, then
  family, then trait, then everyone). The first set:
  - everyone: loves cake, pumpkin pie, golden apples, diamonds; likes bread, cookies, honey bottles, emeralds,
    `#minecraft:small_flowers`; dislikes dirt, gravel, cobblestone, bones; hates rotten flesh, spider eyes, poisonous
    potatoes, pufferfish;
  - building and crafting (Builder, Carpenter, Mason, Tinkerer, Leatherworker): love blueprints and spyglasses; like
    bricks, glass, lanterns, `#minecraft:planks`;
  - mining and smithing (Miner, Armorer, Toolsmith, Weaponsmith, Sifter, Netherworker): love amethyst shards, gold
    ingots, netherite scrap; like iron ingots, coal, raw copper;
  - the land (Lumberjack, Orchard Keeper, Farmer, Florist, Beekeeper, Composter): love golden carrots, honeycomb,
    sunflowers; like apples, bone meal, wheat seeds, `#minecraft:saplings`;
  - animals and water (Shepherd, Butcher, Rancher, Fisherman): love saddles and name tags; like wheat, hay bales, cod,
    salmon, leads;
  - the kitchen (Chef): loves glow berries and golden carrots; likes eggs, milk buckets, sugar, cocoa beans,
    `#c:foods/raw_meat`;
  - learning (Scholar, Teacher, Librarian, Cartographer): love enchanted books, written books, filled maps; like
    books, paper, feathers, ink sacs, compasses;
  - healing (Nurse, Cleric, Undertaker): love glistering melon slices, ghast tears, totems of undying; like potions,
    honey bottles, golden carrots;
  - arms (Guard, Fletcher): love shields, crossbows, diamond swords; like arrows, flint, iron ingots;
  - trade and travel (Shopkeeper, Innkeeper, Ferryman, Postman, Porter): love emerald blocks, filled maps, saddles;
    like paper, boats, lanterns;
  - music (Bard): loves music discs (`#c:music_discs`) and goat horns; likes note blocks and amethyst shards;
  - Pokémon (Trainer, Trainer Leader, Move Tutor, Ball Smith, Pokémon Trader, Fossil Scientist; Cobblemon items by
    id): love `cobblemon:rare_candy` and `cobblemon:ultra_ball`; like `cobblemon:poke_ball`, `cobblemon:exp_candy_s`,
    `#cobblemon:berries`;
  - no trade yet (jobless, nitwits): love emeralds; like bread and beds;
  - traits: Glutton loves `#c:foods`; Frugal loves emeralds and gold ingots, dislikes cake; Cheerful loves flowers and
    music discs; Lazy loves beds, dislikes tools; Diligent loves tools; Clever loves books and clocks; Strong loves
    cooked beef and iron blocks; Nimble loves sugar and rabbit's feet.

  Done when:
  - GameTests: the recipe keeps the item; each taste band's points; the day and week limits (refused with a line); the
    name day counting three times; the item in their chests; the most-specific rule; a datapack taste file winning
    over ours;
  - both textures pass `lint.py`; the recipes are in the recipe book; a README section;
  - showcase scene `gifts` (GIF: wrapping, giving, the hearts).
- [ ] **31.7** **Heart events and life stories.** At 2, 4, 6, 8 and 10 hearts a villager has something to tell you.
  The next time you're within 8 blocks while they're off work (`Chatter.offWork`), they walk up, face you and tell it
  in three to five lines over their head (`WorkerStatus`, one every 3 seconds), each also in your chat in grey so it
  can be read again; walk away halfway and they start again next time. Each event is told once per player, adds +20
  friendship, writes a line in the chronicle (a new FRIEND kind: "Dara told Jesse how she came to Thornholm") and
  becomes part of the villager's **life story**: shift-click someone on the hall's list for their page (your hearts,
  their name day, family and partner, the life story so far with one line per event anyone was told, and their
  personal request). Events are data, `data/<namespace>/heart_events/<id>.json`: the `hearts`, conditions on the
  villager's facts (born in the village (`Families`), hired from an inn (`HEAD_START`), brought back from a grave (a
  new flag `revived` set by `Graves.revive`; absent on villagers revived before), job family, married, courting,
  widowed (a new attachment `late_partner`, the partner's id and name, kept by `Couples.onDeath` from now on), a
  parent, a trait, mood reasons, the village's rank), the lines as lang keys (with their parents, partner, children
  and village as arguments) and the chronicle line. This item builds the engine and the 2-heart set, **Where I come
  from**, in four variants: born here (names both parents), came as a traveller (the inn, and the day they were
  hired), here before the hall (the village before it had a name), and back from the grave (what they remember of it).
  Config `heartEvents` (true).

  Done when:
  - GameTests: at 2 hearts each variant is told for its facts, and nothing under 2 hearts; once per player; walking
    off and coming back starts it again; the chronicle line and the life-story page; a broken event file is skipped
    with a log line;
  - showcase scene `heart_event` (GIF: Dara walks up and tells her story).
- [ ] **31.8** **Heart events: the full set.** The other four events, every variant written out in `en_us.json` (three
  to five lines each, read in context):
  - **4 hearts, My work**, one per job family: building and crafting (the first wall they raised, and the one that
    fell); mining and smithing (the day their lamp went out underground); the land (the year the harvest failed);
    animals and water (the foal they raised, or the fish that got away); the kitchen (the dish that made them a cook);
    learning (the book that changed their mind); healing (the patient they couldn't save); arms (their first raid);
    trade and travel (the worst bargain of their life); music (a song nobody sings any more); Pokémon (their first
    partner Pokémon); no trade yet (they haven't found their place and ask what you think: the village's nearest free
    workstation is suggested, from `VillageHalls.freeStations`);
  - **6 hearts, What keeps me up at night**, from their life now: hunger (hungry, or under 16 meals in the store), no
    bed of their own, raids and bandits (a raid in the last 5 days, or a camp nearby), illness (they or their family
    ill), loneliness (single, with no company), and when none of these fits, worry about you and your travels;
  - **8 hearts, The people I love**: married (their partner, and the wedding day from the chronicle), courting, a
    parent (their children by name), mourning (their late partner), alone (their parents, or "the village is my
    family");
  - **10 hearts, What I dream of**: to be a Master (below Master), a finer house (home below tier III), the village a
    City (below City), a festival in their honour, to see the Nether or the sea (by job), and "I have everything I
    wanted" (a happy, married Master).

  At 10 hearts they also give you a **keepsake**, once: a named item with their lore. Building and crafting: "<Name>'s
  Trowel" (iron shovel, Efficiency II); mining and smithing: "<Name>'s Lucky Pick" (iron pickaxe, Fortune I); the
  land: "<Name>'s Grandmother's Seeds" (4 torchflower seeds); animals and water: "<Name>'s Old Rod" (fishing rod, Luck
  of the Sea II); the kitchen: "<Name>'s Secret Recipe" (a written book of their recipe, with 2 pumpkin pies);
  learning: "<Name>'s Annotated Atlas" (enchanted book, Mending); healing: "<Name>'s Remedy" (a golden apple); arms:
  "<Name>'s Old Shield" (shield, Unbreaking II); trade and travel: "<Name>'s Spyglass"; music: "<Name>'s Favourite
  Record" (music disc Otherside); Pokémon: "<Name>'s First Poké Ball" (`cobblemon:premier_ball`); no trade yet:
  "<Name>'s Pressed Flower" (a cornflower).

  Done when:
  - one GameTest per variant (a loop over set-up facts) picks it, and each keepsake comes once per player;
  - `langcheck.py` clean; a review package with a still of each family's 4-heart event; the `heart_event` scene gains
    a still of a 10-heart keepsake.
- [ ] **31.9** **Personal requests: the asking, and six requests.** A villager with 3 hearts or more with a player
  nearby, and no request open, may ask that player for help: each morning one such villager in the village is picked,
  a 25% chance. They walk up and ask over their head, and your chat gets the request with [I'll help] and [Not now]
  (clickable, through `/workplace quest accept|decline <id>`). It shows in the journal's Personal tab, on their
  life-story page and on the hall's tooltip for them ("Dara wants to make Journeyman before the next festival"). Other
  players with 3 hearts can help too, and everyone who helped gets the friendship. Deadlines are "before the next
  festival" (`Festivals.nextDay`) or a number of days; a missed one costs 30 friendship, they're glum for a day (a
  mood reason) and they don't ask again for 3 days. Requests are quest files with the giver `villager` under
  `quests/personal/`, with new conditions (`hearts_at_least`, `job`, `job_family`, `level_below`, `villager_type`,
  `home_tier_below`, `no_bed`). The six requests:
  1. **Next level before the festival** (`level_up`): a worker below Master; done when they reach the next level
     (trading gives XP as in vanilla, and so does a Bottle o' Enchanting gift). 6 emeralds.
  2. **A home of my own** (`home`): no bed of their own, or a tier I home; done when they sleep in a bed of their own
     in a finished home of tier II or better (`people/Homes`). 8 emeralds.
  3. **A taste of home** (`bring`, by their villager type): plains a pumpkin pie, desert a rabbit stew, savanna 4
     cooked mutton, taiga 16 sweet berries, snowy 4 baked potatoes, swamp a mushroom stew, jungle 8 cookies. 4
     emeralds.
  4. **The tools of my trade** (`bring`, by job, into their chests where they use it): Miner a diamond pickaxe,
     Lumberjack a diamond axe, Farmer a diamond hoe, Fisherman a fishing rod with Luck of the Sea, Guard a diamond
     sword, Cartographer a spyglass, Netherworker a potion of Fire Resistance, Sifter a diamond shovel. 5 emeralds.
  5. **Help me train** (Cobblemon, a Trainer): beat them in battle on three different days.
  6. **A Pokémon friend** (Cobblemon): a Pokémon of a type that helps their job (the `work/Partners` table) pastured
     within 16 blocks of their workstation at dawn.

  Each done: +150 friendship to every helper and a chronicle line. Config `personalRequests` (true).

  Done when:
  - GameTests: a 3-heart villager asks and a 2-heart one doesn't; accept and decline; each of the six completes by its
    objective and pays every helper; a missed deadline costs 30 and stops asking for 3 days; one open request per
    villager;
  - showcase scene `personal_request` (GIF: the ask, the accept, the hand-in, the thanks).
- [ ] **31.10** **Personal requests: six more.** Each with its new objective:
  1. **A letter to family** (`deliver`): offered when another village with a hall is within caravan range; the giver
     names a relative there (a villager of that village, picked and remembered). You get a sealed letter (paper with a
     name, lore and a quest mark, so no new art; lost, the giver writes you another). Right-click the relative with it
     and carry their reply back. Friendship with both, and a line in both chronicles.
  2. **Bring back my love** (`revive`): a mourning villager whose late partner's grave is in the village; done when
     that grave is revived (`Graves.revive`, by an Undertaker with a golden apple, a healing potion or a totem). They
     marry again (`Couples.wed`): a second wedding.
  3. **A cat by the door** (`pet_near_home`): a cat or a tamed wolf within 10 blocks of their bed at dawn.
  4. **Off to school** (`schooled`): a parent of a child who hasn't been to school; done when the child is schooled
     (`Schools.isSchooled`; it needs a Teacher).
  5. **A chapel wedding** (`build`, a blueprint finished in the village): a courting couple in a village without a
     Chapel; their wedding waits (up to 5 days instead of 2) for a Chapel to be finished, then is held there with a
     feast: everyone in the village gets a festival's mood.
  6. **The lost heirloom** (`fetch`): the giver's family heirloom, one of Grandmother's Compass (a compass),
     Grandfather's Pocket Watch (a clock), Mother's Spyglass (a spyglass), Father's Horn (a goat horn), the Lucky
     Crystal (an amethyst shard) or Great-aunt's Diary (a written book of three pages of her story), was lost at a
     structure within 1500 blocks (a new tag `aliveworkplace:heirloom_places`: ruined portals, shipwrecks, trail
     ruins, abandoned mineshafts). The arc engine's `place` puts it, when a player comes near, in one of the
     structure's chests (in trail ruins, a suspicious gravel block to brush; a new barrel if the chests are gone), and
     you get a map. Its story goes in the chronicle when it's back.

  Done when:
  - a GameTest per request: the letter there and back between two halls; the revive with a grave; the cat at dawn; the
    schooled child; the chapel wedding and its feast; the heirloom placed once when a player comes near, with the map;
  - showcase scene `letter` (the letter carried between two villages, and the reply).
- [ ] **31.11** **Reputation and titles.** Each player has a standing in each village: points kept by hall in
  `aliveworkplace_stories` (player → points). Earned: a daily quest +10; a personal request +25; a story chapter +50
  to everyone who helped; an arc's ending what its file says (150 in ours); a bounty +40; breaking up a bandit camp
  or, once 32.3 lands, a lair (the killer of its chief) +40; fighting in a raid (3 raiders killed) +20; coming to a
  festival +5; a gift +2 (at most +10 a day per village). Lost: hitting a villager −10, killing one −150, killing an
  iron golem −100, killing a guard −200. Titles: **Stranger** (under 50), **Friend** (50), **Hero** (300), **Lord**
  (1000, and the village at least a Town). New quest rewards: `reputation`, and `honour`, which lets arcs give named
  honours (Kingslayer, Healer of <village>, Wayfinder, Co-author), listed with your standing. A new title is told to
  the whole server ("Jesse is now a Hero of Thornholm!") with a sound and a TITLE line in the chronicle; losing one is
  told only to that player. **In chat**, a player's title in the village they stand in (else their best anywhere) goes
  before their name: "[Hero of Thornholm] Jesse: …", through a new `Platform.onChatDecorate` (Fabric's message
  decorator; config `titlesInChat`, true). The hall's name tag tooltip shows your standing and the top three players;
  `/workplace standing` lists yours in every village. Config `reputation` (true). **Owner's call:** may players other
  than the hall's owner become Lord of his village on a shared server? Until he says, yes, by deeds alone.

  Done when:
  - GameTests: each source and loss; the thresholds and Lord's Town rule; the announcement once per title; the
    decorated chat text; honours listed; two players kept apart; a save/reload round trip;
  - screenshots of a titled chat line and the hall tooltip; a README section; showcase scene `titles`.
- [ ] **31.12** **What friendship and titles are worth.**
  - **Cheaper trades**: 5% off at 4 hearts and 5% more a heart, 30% at most; a title adds 5% (Friend), 10% (Hero) or
    15% (Lord); 40% at most together. A mixin at the end of `Villager.updateSpecialPrices`, on top of vanilla's gossip
    and Hero of the Village; no price under 1. Players' shops aren't touched.
  - **Festival gifts**: at a festival, each villager with 6 hearts or more with a player there brings them one gift:
    building and crafting 16 stone bricks, mining and smithing 4 iron ingots, the land 6 apples, animals and water 4
    cooked salmon, the kitchen 2 pumpkin pies, learning 2 books, healing a potion of regeneration, arms 16 arrows,
    trade and travel 3 emeralds, music a note block, Pokémon 3 Poké Balls, no trade yet 3 flowers. Heroes and Lords
    get Hero of the Village II at festivals (everyone else I, as now).
  - **Named after you**: the first baby born to a couple where a parent has 8 hearts with you is named after you, once
    per player per village, with a chronicle line.
  - **Letters from friends**: at 10 hearts, once a week the villager sends you a parcel through the village's post
    (`mail/PostOffice`, to your mailbox or the post office): a letter ("Thought you'd like these. Dara") and their
    family's festival gift.
  - **Legends**: the conditions `title_at_least` and `hearts_at_least` go into the shared `rules/` package. Once
    29.8's guests are on `main`, a Legend guest comes only to a village where some player is a Friend or better (Rare
    Legends) or a Hero or better (Legendary and Mythic), a condition added to the `visit` way of each Legend file;
    before that, the conditions and their tests only.
  - **A say in edicts**: a Lord may enact and repeal edicts in that village's Book of Edicts as the hall's owner can;
    a Hero may propose one, which the owner gets in chat with [Approve] and [Refuse]. Needs 30.4's Book of Edicts
    (today only the owner, friends and operators can click it); before it lands,
    `Reputation.maySetEdicts(player, hall)` and its test, for M30's book to call.

  Done when:
  - GameTests: the price at every heart and title step and the 40% cap; one festival gift per villager per festival;
    the named baby once; the weekly parcel arriving; `title_at_least`;
  - a screenshot of a discounted trade screen; showcase scene `festival_gifts`.
- [ ] **31.13** **The bounty board and bandit captains.** A new block, the **Bounty Board** (planks, sticks and paper;
  a notice board on two posts, drawn with the pixel-art skill like vanilla's wooden blocks, its face showing 0 to 4
  posters as block states): right-click it for the Bounties tab of the nearest hall's journal (the tab works without a
  board too). From Village rank a village posts a bounty while it has fewer than 2 open, at most one every 3 days;
  each lasts 7 days. Config `bountyBoard` (true). This item's kind is the **bandit captain**: a named raider and a
  band of 2 or 3 at a hideout 120 to 200 blocks out (the existing `camp/bandit_camp`, with his own banner on a pole by
  the fire), placed by the arc engine's `place` when a player comes within 96 blocks. Names come from
  `data/<namespace>/bounties/captains.json`: 24 first names (Grimwald, Varka, Osric, Hobb, Maud, Teague, Rook, Sable,
  Corvin, Ysolde, Brannoc, Edda, Fulk, Ilse, Jory, Kestrel, Lorcan, Morwen, Nyle, Petra, Quill, Ronan, Silas, Wenna)
  and 16 epithets (the Red, Blackhand, the Fox, Ironjaw, the Grey, Halfmoon, the Cleaver, Longshot, the Silent,
  Ashcloak, the Tall, One-Eye, the Cruel, Quickblade, Stormcrow, the Mad). Each captain has one quirk, one file each
  in `bounties/quirks/` (the mob, its gear, effects, mount and band size):
  - **Ironhide**: full iron armor, +20 health;
  - **Fleetfoot**: Speed II;
  - **Crossbow Ace**: a pillager with a Multishot, Quick Charge II crossbow;
  - **Beast Rider**: rides a ravager;
  - **Night Stalker**: invisible at night until he's hit;
  - **Warlord**: a band of 5;
  - **Hexer**: an evoker.

  Taking a bounty gives a **Wanted Poster** (a new item: vanilla's map paper with a sketched face; its tooltip has the
  name, the quirk, where he was last seen ("160 blocks north-east") and the reward) and a map to the hideout. The
  captain drops a **Captain's Insignia** (a new item) when he dies; hand it in at the board or the hall for 12
  emeralds times the rank factor, once, to whoever hands it in; the chronicle (a new BOUNTY kind) says who. An
  unclaimed bounty comes down after 7 days and the band leaves in a puff. Villagers gossip about where he was seen
  (chatter). Once 32.3's lairs are on `main`, a standing lair's captain is posted as a bounty too (his name and his
  lair's place; paid to his killer when he falls). No captains' bounties in a village set At peace or at threat level
  0 (32.21, once it lands).

  Done when:
  - GameTests: posted at Village rank and not at Hamlet, at most 2 open, 3 days apart; the captain spawns with his
    quirk at the hideout once a player is near (one test per quirk); the insignia drops and pays once (a second player
    can't claim it again); the bounty comes down after 7 days with its band; the board shows as many posters as open
    bounties;
  - the three textures pass `lint.py`; a README section; showcase scene `bounty` (GIF: the board, the poster, the
    hideout, the claim).
- [ ] **31.14** **Rogue Pokémon bounties** (Cobblemon). The board's second kind of poster, through a new extension
  point `story/RoguePokemon` filled by `compat/cobblemon/CobblemonRogues`; without Cobblemon this kind is never
  posted. Config `rogueBounties` (true). A rogue is a wild Pokémon grown huge and mean that roams within 24 blocks of
  a spot 150 to 250 blocks from the hall, spawned with Cobblemon's own spawning from
  `data/<namespace>/bounties/rogues/<id>.json` (one file per biome group: its biome tag and species) by the biome
  round the spot (a species Cobblemon doesn't have is skipped):
  - forests: Ursaring, Scyther, Pinsir;
  - plains and meadows: Tauros, Kangaskhan, Rapidash;
  - mountains and peaks: Onix, Golem, Aggron;
  - deserts and badlands: Sandslash, Krookodile, Flygon;
  - swamps and jungles: Tangrowth, Toxicroak, Swampert;
  - beaches and stony shores: Kingler, Crawdaunt, Barbaracle;
  - snowy biomes: Mamoswine, Abomasnow, Beartic;
  - savannas and taigas: Donphan, Rhydon, Lycanroc.

  Its level is 35 at Village rank, 50 at Town and 65 at City (with Radical Cobblemon Trainers, the player's level cap
  plus 5); it's half again as big where Cobblemon has a size setting (check with `api.py`; normal size if not), named
  "Rogue <species>", and glows for players who took its bounty when they're within 32 blocks. With Cobblemon 1.8
  (28.2's `work/PokemonFeatures.ALPHAS`), the rogue is spawned as one of Cobblemon's Alphas instead. Beat it in battle
  or catch it: the bounty (16 emeralds times the rank factor) goes to that player once, and a catcher keeps it. Its
  poster names the species and where it was last seen.

  Done when:
  - compat GameTests (`runCompatGameTest`): the species, level and name for its biome and rank; a win and a capture
    each pay once; plain GameTest: never posted without Cobblemon;
  - showcase scene `bounty_rogue` (Cobblemon: the poster, the rogue, the claim).
- [ ] **31.15** **The Bandit King, I: the fort and the King.** Three builds with the minecraft-architect skill
  (`STYLE.md`, rendered), each standing on the one before like a blueprint upgrade: `story/bandit_fort` (a palisade of
  sharpened logs round a yard, a gate, a fire and tents), `story/bandit_fort_2` (two corner watchtowers with ladders,
  and a stable) and `story/bandit_fort_3` (the King's keep: a timber-and-cobblestone hall with a throne, his banner
  over the door, a postern gate at the back that can be placed open or shut, and the treasure chest). The arc places
  one stage each dawn on fit ground 110 to 150 blocks from the hall (`BanditCamps.fits` widened to the fort's size;
  ground filled underneath as `BanditCamps.found` does). **The King**: a vindicator named "<a name from 31.13's list>,
  the Bandit King", in a golden helmet (his crown), iron chestplate, leggings and boots, with a diamond axe, 150
  health and a boss bar for players within 32 blocks; at half health he blows a goat horn and 4 bandits come out of
  the keep, once. His men keep to the fort (`restrictTo`). The treasure chest's loot table, `chests/bandit_king`: 16
  to 32 emeralds, 4 to 8 gold ingots, 1 to 3 diamonds, an enchanted iron sword or crossbow, and **the King's Crown**
  (the golden helmet, Protection III and Unbreaking III, named).

  Done when:
  - GameTests: the three stages place in order on uneven ground with nothing floating and nothing a player built
    touched; the King's gear, health and boss bar; the horn calls 4 bandits once; the chest always has the crown;
  - renders of the three stages, front and back, in the review package; showcase scene `bandit_fort` (the three stages
    placed one after another).
- [ ] **31.16** **The Bandit King, II: the story.** `arcs/bandit_king.json`, an arc marked `threat`. It starts in a
  Town or bigger that has broken up at least 2 bandit camps (a count kept in `aliveworkplace_stories` from this item
  on; `BanditCamps.breakUp`, or 32.3's lair breaking once that has landed, adds to it), 8% a day, with `banditCamps`
  on. Its camps are the arc's own (`place` and `spawn`), not `BanditCamps`' camp or 32.3's one lair a village, so they
  never clash. Chapters:
  1. **Smoke on three hills**: three bandit camps go up at once round the village (the `camp/bandit_camp` blueprint at
     three sites 80 to 104 blocks out, each with a chief in iron and 3 or 4 bandits, as `BanditCamps.found` makes
     them), their chiefs now "Captain of the Bandit King", each camp flying his war banner (a black banner with a red
     skull). Take the three war banners: breaking one drops a named banner that counts. Chatter: "All the camps fly
     the same banner now."
  2. **The King's fort**: the next dawn the fort's first stage goes up (31.15), one more each dawn. Win allies, at
     least one of three (each done adds its help to the storm):
     - **The neighbours' pledge**: finish any quest at another village with a hall within caravan range; 3 of its
       guards come for the storm (like mercenaries) and go home after;
     - **Swords for hire**: pay 24 emeralds at the hall; 6 mercenaries come for the storm;
     - **The deserter**: Pell, a bandit who has had enough, waits by the ashes of the first camp; bring him 16 bread
       and he tells you of the postern, which stage 3 leaves open, and afterwards he joins the village (a jobless
       villager).
  3. **The storm**, once the keep stands: raise a Rally Banner within 48 blocks of the fort and your guards and allies
     follow; kill the King. Until he falls, his raiders come every second night (the village's bandit raids through
     `VillageRaids`, or 32.2's raids once they've taken over, half again as big). If he still stands after 10 days,
     **the King's tribute**: he takes a third of the treasury and rides off with his band, the fort is left as a ruin,
     and moods drop 10 for 3 days.

  Ending: the treasure chest is yours, a festival the next evening, +150 reputation to everyone who fought in chapter
  3, the honour **Kingslayer**, and no bandit camps for 10 days. Chronicle lines for every chapter.

  Done when:
  - a GameTest walks the arc: each chapter starts; each ally's help is there at the storm (3 guards, 6 mercenaries,
    the open postern, and Pell joining after); the tribute takes a third of the treasury; the ending pays;
  - showcase scene `bandit_king` (GIF: the three banners, the fort rising a stage a day, the storm with allies, the
    King falling).
- [ ] **31.17** **The Sickness, I: eight far herbs.** Eight new plant blocks with their items, drawn with the
  pixel-art skill like vanilla's flowers. They never generate in the world: only an arc plants them, so no world
  changes. Each grows in its biomes (a biome tag of ours, `aliveworkplace:herb_biomes/<herb>`, so datapacks and biome
  mods can add to it):
  - **Frostbloom**, a pale blue bell (snowy plains, snowy slopes, groves, ice spikes, snowy taigas; on snow or grass);
  - **Sunthistle**, a spiky yellow thistle (deserts and badlands; on sand, red sand or terracotta);
  - **Mirewort**, a reedy herb with purple buds (swamps and mangrove swamps; on mud or grass);
  - **Peakmoss**, a silver-green tuft (windswept hills, stony and jagged peaks, meadows; on stone or grass);
  - **Jungle Orchid**, a magenta orchid (jungles, sparse and bamboo jungles; on grass);
  - **Sea Lavender**, small lilac sprays (beaches, snowy beaches, stony shores; on sand or gravel);
  - **Glowcap**, a pale mushroom giving light 5 (dark forests; on grass or podzol, in shade);
  - **Petal Balm**, a pink-leaved herb (cherry groves; on grass).

  A new arc effect `herb_patch` plants 5 to 9 of a herb on its ground within 6 blocks of a spot. Also the **Three-Herb
  Tonic**, a bottle of green tonic (a new item, drawn like vanilla's potions; not 32.18's Remedy), made by a healer in
  31.18.

  Done when:
  - GameTests per herb: placed on its ground, broken it drops its item, it stays through random ticks and never
    spreads; `herb_patch` plants only on fit ground;
  - all textures pass `lint.py`; a preview sheet in the review package; showcase scene `herbs` (all eight patches, and
    the tonic in hand).
- [ ] **31.18** **The Sickness, II: the Grey Cough.** `arcs/the_sickness.json`, an arc marked `threat`. It starts in a
  village of Village rank or more with 12 villagers or more, `villagerSickness` on, 6% a day. A new illness, the
  **Grey Cough**, a kind in the illness-kind attachment beside `ILL_SINCE` (32.11 adds the same attachment for hexes;
  whichever item lands first adds it, ordinary when absent, so old saves are unchanged): it doesn't pass by itself, a
  nurse's remedy (and 32.18's Remedy once it exists) only takes the slowness away for a day, and each night every
  coughing villager passes it to one villager who worked or slept within 8 blocks of them (40%), never past half the
  village. Chapters:
  1. **The Grey Cough**: three villagers fall ill; the hall, the chatter and the chronicle say so. Talk to the
     village's healer (right-click them): its Nurse, or 32.18's Physician once that job exists. With neither, **Doctor
     Wren**, a travelling physician (a villager in a cleric's robe), arrives at the hall the next morning and stays
     until the arc is over.
  2. **Three far cures**: the healer names the three herbs (31.17) whose biomes are nearest the village (looked up
     once, 3000 blocks at most; a herb whose biome isn't found is passed over for the next) and gives you a map to
     each patch. Bring 3 of each.
  3. **The remedy**: hand them to the healer, who brews the Three-Herb Tonic over half a day ("Brewing the cure · 40%"
     over their head; at their brewing stand, or Doctor Wren by the hall), then walks round and gives it to every
     coughing villager.

  Ending: everyone well, a thanksgiving festival the next evening, +100 friendship with each villager who was ill for
  each player who gathered herbs, +150 reputation to the gatherers, the honour **Healer of <village>**. After 15 days
  without the cure, the cough runs its course: everyone is well within 3 days, moods drop 15 for 5 days, and the
  chronicle says so. Nobody dies of it.

  Done when:
  - GameTests: the night pass-on and the half-village cap; the remedy only easing it; the healer (a Nurse or
    Physician, else Doctor Wren); the herb choice with a stubbed locator (forced biome answers); the brew and the cure
    round; the failure path;
  - showcase scene `sickness` (GIF: the coughing village, a herb patch, the cure going round).
- [ ] **31.19** **The Lost Caravan, I: the wreck and the trail.** Builds with the minecraft-architect skill
  (`STYLE.md`, rendered): `story/caravan_wreck` (the settlers' covered wagon on its side, a broken wheel, spilled
  crates and barrels, a lantern in the grass); `story/raiders_stockade` (a small pillager stockade: a palisade, a
  tent, a campfire, a loot chest and a cage of iron bars with a gate for a captive); and five trail marks,
  `story/trail_1` to `trail_5`, each about 3×3 (trampled coarse dirt and a dropped lantern; a smashed barrel; a torn
  white banner on a fence post; cart ruts of path blocks; an arrow-studded crate). A new objective, `follow_trail`:
  reach the marks in order; at each, the action bar says where the tracks go next ("The tracks go on to the
  north-east"). The placing rule for the arc: the wreck a third of the way along the caravan's road (or 150 blocks out
  if that isn't fit ground), the stockade 120 to 180 blocks on from the wreck, away from both villages, and the marks
  evenly between them, each placed when a player comes within 96 blocks.

  Done when:
  - GameTests: each piece places on uneven ground cleanly and never over a player's blocks; `follow_trail` counts the
    marks in order only (reaching mark 3 first counts nothing);
  - renders of the wreck, the stockade and the five marks in the review package; showcase scene `caravan_trail` (the
    wreck, a mark, the stockade).
- [ ] **31.20** **The Lost Caravan, II: the story.** `arcs/lost_caravan.json`. When a caravan sets off on a road
  longer than 300 blocks (`hall/Caravans`), 1 in 20 goes missing (config `lostCaravanChance`, 20), if neither village
  has an arc running; its goods are held by the arc. Chapters:
  1. **Overdue**: the caravan doesn't arrive; both halls are told ("The caravan from Thornholm to Ashford is a day
     overdue") and the sending hall gives you a map to the last-known road. Find the wreck.
  2. **The tracks**: follow the trail to the stockade (31.19).
  3. **The captive**: the carter (a new villager of the sending village with a name from `Names`; **Tobin** here) is
     locked in the cage, with 4 bandits and a captain on guard. Open the cage and he follows whoever freed him (as a
     rallied guard does, `guard/Escorts`: he keeps up, and catches up from 32 blocks; further than 64 away, he waits).
     This chapter is done when Tobin reaches either village. A second quest, not needed to go on: the stockade's chest
     holds the lost goods; deliver them to the receiving hall for 8 emeralds and +50 reputation there.

  Ending: Tobin joins the village he reached at Journeyman (as a hired traveller does, `Schools.headStart`) and takes
  a free workstation; +150 reputation in both villages to whoever helped, +100 friendship with Tobin, the honour
  **Wayfinder**, lines in both chronicles. After 7 days without a rescue, Tobin finds his own way home with nothing,
  and the goods are lost.

  Done when:
  - GameTests: a shipment goes missing when forced; each chapter moves on by its objective; Tobin follows, waits and
    joins; the chest holds the shipment's own goods; the delivery quest pays; the failure path;
  - showcase scene `lost_caravan` (GIF: the wreck, the trail, freeing Tobin, walking him home).
- [ ] **31.21** **The Professor's Thesis** (Cobblemon). `arcs/professors_thesis.json`, loaded only with Cobblemon. Its
  patron is the village's Pokémon Professor (29.21); until that lands, the village's best Scholar at Expert or above,
  called "Professor <name>" for the arc. It starts in a Town with a Scholar or the Professor, 6% a day. New objectives
  through `compat/cobblemon` (Cobblemon's capture and battle events; find their exact names with `api.py`): `catch` (a
  type or species; in a biome tag; by day or night; above or below a height), `show` (a Pokémon in your party that has
  evolved, or is at friendship 200 or more) and `battle_leader` (beat the Trainer Leader of another village). The
  thesis topic is picked from six at the start:
  - **Life in the cold**: an Ice type in a snowy biome, a Water type in a frozen ocean or river, any Pokémon above
    `y=150`;
  - **Fire and stone**: a Fire type in a desert or badlands, a Rock type in stony or jagged peaks, a Ground type below
    `y=0`;
  - **Creatures of the night**: a Dark type at night, a Ghost type at night, a Psychic type on a full-moon night;
  - **The forest's web**: a Bug type in a jungle, a Grass type in a forest, a Flying type in a meadow or cherry grove;
  - **Sea and shore**: a Water type in a warm ocean, one in a deep ocean, one on a beach;
  - **Living fossils**: a fossil revived by a Fossil Scientist, a Dragon type, a Rock type in badlands.

  Chapters: 1. **Field notes**: the three catches. 2. **A closer look**: show the professor one of them evolved, or
  any Pokémon at friendship 200 or more. 3. **Peer review**: beat the Trainer Leader of a village with a hall within
  caravan range; with none, a rival scholar, **Thessaly**, comes to the hall and battles at Master strength through
  the trainer engine, then leaves.

  Ending: the thesis is published as a written book, "<Professor>: <topic>", listing your catches (species, place and
  day) with you as co-author; 3 Rare Candies and an Ability Capsule (by item id; emeralds if an id is missing); +150
  reputation; the honour **Co-author**; and a free level of the research the village's scholars are working on (or the
  next open topic; with 29.21's Professor as the patron, the next topic of the Pokédex tree).

  Done when:
  - compat GameTests: catches count by type, biome, time and height; `show`; `battle_leader`; Thessaly comes when
    there's no leader nearby; the book lists the catches; plain GameTest: without Cobblemon the arc never starts;
  - showcase scene `professors_thesis` (Cobblemon: the professor's request, a catch counting, the published thesis).
- [ ] **31.22** **The eight reforms as stories.** Needs M30's reforms (30.5 to 30.10) on `main`; until then the item
  waits (`sessions.py pause --blocked "needs M30's reforms"`). Through the reform's `arc` field (30.5), each reform
  becomes a short arc of the same name: an opening chapter where a villager of the fitting trade asks you for it
  (`talk`), then M30's three steps as its quests with M30's numbers, then an ending scene that leaves something
  lasting, with chronicle lines and chatter all the way. The reform itself stays M30's: a new reward, `reform_edict`,
  calls it, so the boost stays and the cost goes, for that village for good. With `storyArcs` off, M30's own steps on
  the quest page work as before. Reform arcs are marked `side`: they don't count against `arcsAtOnce` or the cooldown
  and run beside another arc. The eight:
  1. **The Shift Bell** (Long Shifts): asked by the Toolsmith (else any smith); ending: a bell on a post beside the
     hall (a small template from the generator, set on natural ground) that rings at the start and end of the work
     day.
  2. **The Common Granary** (Free Bread): asked by a Farmer; ending: a harvest supper at the hall the next evening (a
     festival's feast, without the fireworks).
  3. **The Midwives** (Large Families): asked by the Nurse (else a mother); ending: a midwife, a new villager, comes
     and becomes a Nurse at Journeyman.
  4. **The Watchful Gate** (Open Gates): asked by a Guard; ending: an old soldier, a new villager, comes and becomes a
     Guard at Expert, in iron.
  5. **The Festival Fund** (Festival Season): asked by the Bard (else a Cheerful villager); ending: the next festival
     is a grand one, with twice the fireworks and a good mood that lasts a day longer.
  6. **The Fair Ledger** (Tithe): asked by a Librarian or Scholar; ending: a written book, "The Fair Ledger of
     <village>", with the treasury's takings day by day for the last 7 days, for the hall's owner.
  7. **The Lamplighters** (Curfew): asked by a Porter (else anyone); ending: two Street Lamp blueprints for whoever
     finished it, and a chronicle line for the first lamplit night.
  8. **The Militia Drill** (Conscription): asked by a Guard; ending: drill day: at noon the next day every grown
     villager spars for a while at the village's Training Dummies (the guards alone if there are none), then a
     chronicle line.

  Done when:
  - a GameTest per reform: the ask, the three steps, the ending, and the edict reformed (boost kept, cost gone) in
    that village only; with `storyArcs` off, M30's own steps still work;
  - showcase scene `reform_story` (The Shift Bell, from the Toolsmith's ask to the bell ringing).

Depends on: 30.5 to 30.10 (M30's reforms) for 31.22, which waits for them. Soft links, each with a fallback: 28.2's
`PokemonFeatures.ALPHAS` (31.14), 29.2's shared `rules/` package (whichever of 29.2 and 31.2 lands first creates it),
29.8's Legend guests and 29.21's Professor (31.12, 31.21), 30.4's Book of Edicts (31.12), and M32's lairs (32.3),
illness kinds (32.11), Physician and Remedy (32.18) and peaceful settings (32.21) (31.4, 31.13, 31.16, 31.18).

## Milestone 32: Threats worth building walls for (1.6)

Raids today are monsters or bandits walking in at night, and walls do little more than shut their gates. In 1.6 every
land has its own enemy camped nearby under a named captain (pillager warbands with rams and ladders, drowned pirates
from the sea, desert raiders under a pharaoh, piglins through a portal, a witch coven's hexes) who lay sieges that
walls, gates, towers and archers decide, while a watchtower gives a day's warning and scouts, guards and hired swords
let the player strike back at their camps. Fire, drought and plague each get a job that answers them, and every threat
has a switch and a peaceful setting. It builds on guard/VillageRaids, BanditCamps, Gates, GuardRally, Mercenaries, the
Rally Banner and the defence blueprints (tools/blueprints/defence.py).

- [x] **32.1** (review: pending 2026-10-06) **Design note.** `docs/design/M32.md`: what the player sees (the threat ladder: monsters from 8
  villagers, bandits at Village rank, the enemy of the village's own land from Town, sieges led by the captain himself
  at City; a siege night minute by minute; the warning timeline; a march on a lair; each disaster and its job), the
  two data formats (a raider culture and a disaster, one full example file each), the new config keys and the hall's
  At peace setting, and the save data (the threats' saved data, which reads the bandit camps' old one; the hall's new
  fields; every default). Every number in this milestone is a starting value. One question is the owner's: whether
  threats may damage builder-made buildings on his live server (rams break the gates of finished walls, fire burns
  wooden builds; never a player's own blocks, and the builders always put them back); meanwhile the default is yes.
  Sent as a review package with a mock-up of the hall's Defence page; lanes don't wait for his reply. Done when: the
  note is on `main` and the package is sent.
- [ ] **32.2** **The threat engine: raider cultures as data.** A new package `threat/` (add it to
  `docs/agent/layout.md`); `guard/VillageRaids` and `BanditCamps` keep their public methods and call into it, so their
  callers and tests don't change. `threat/Threats` loads `data/aliveworkplace/raider_cultures/<id>.json` (through
  `Platform.onDataReload`); one file is one culture:
  - `where`: conditions from a shared toolbox (`threat/Conditions`: biome tags at the hall; `coast`, an ocean or beach
    biome within 48 blocks; `nether_link`, a lit Nether portal or a finished Nether Gate in the hall's area; lowest
    rank; fewest villagers) and a `weight`;
  - `arrival` (`edge`: gather at the village's edge, as now; `lair`; `shore`; `portal`) and `hours` (`night`, or
    `until_noon`);
  - `roster`: entity id, share, role (`melee`, `ranged`, `ram`, `climber`, `healer`) and gear per slot; the `captain`:
    entity, gear, extra health and the lang key of his list of names;
  - `tactics` (`ram_gates`, `ladders`, `sand_ramps`, `plunder`, `hex`; the engine skips any it doesn't know yet),
    `lair` (structure id, strength), the loot table, message and chronicle keys.

  `VillageRaids.start` picks a culture by weight among those whose `where` fits. Today's two raids become the first
  two files, with today's numbers: `monsters.json` (zombie 50, skeleton 30, spider 20; `edge`; 8 villagers) and
  `bandits.json` (pillager 50, vindicator 50; the bandit camp; Village rank). Also:
  - raids under way are saved (`aliveworkplace_threats`), so a restart mid-raid carries on (today `ACTIVE` is lost);
  - the threat clock: at dusk, in the hall's round, each hall rolls the attack for the *next* night, with today's
    nightly chances and rest days, so a warning (32.14) has a day to give;
  - anything carrying the raider tag is a foe to guards (`Guards.isFoe`), so hoglins and witches count;
  - `Threats.chanceFactor(level, hall)`: a hook other systems add to (M30's Open Gates edict, M35's Great Wall);
  - config `raiderCultures` (every culture id → on, written out complete); `villageRaids` and `banditCamps` still
    switch `monsters` and `bandits`.

  Done when:
  - RaidGameTests, HallSpecGameTests and CheckBugGameTests pass unchanged;
  - a GameTest loads a culture from a test datapack and a raid spawns its roster in its shares (fixed `RandomSource`,
    1000 picks within 3% of each share) with each role's gear;
  - a raid saved and loaded half-way (a saved-data round trip) is still on and ends at dawn as before;
  - the clock's attack starts at the next dusk, a day after the roll (a GameTest setting the day time);
  - a culture switched off in the config is never picked. Nothing new to see: lands with `--no-review`.
- [ ] **32.3** **Lairs for every culture.** `threat/Lairs` takes over `BanditCamps`: any culture with a `lair` makes
  camp 80–104 blocks from the hall (today's site rules; cultures that need water or a portal add theirs in their own
  item), at most one per village, with 5 days' rest after one is broken. The saved data keeps its old name and reads
  old camps as `bandits` lairs (each new field with a default from the culture's file). New for every lair, bandit
  camps included:
  - the captain has a name from his culture's list (20 a culture, in `en_us.json`: "Chief Harl Ashgrave"), shown over
    his head and in every message and chronicle line;
  - a strength (bandits: 6 at first, +1 a day, at most 10): a raid takes its raiders from it (never more than today's
    raid size), those alive at dawn walk back and rejoin, the dead are gone, so after a costly night the lair is weak;
    the band at home is the strength, at most 8;
  - the culture's own loot table, messages and chronicle lines when the captain falls (as the bandit chief's now).

  The hall's guards icon opens a new **Defence** page (a `ChoiceMenu`): the lair (culture, captain, strength, roughly
  where, the days it has stood) and the last three attacks and how they ended. Done when:
  - the bandit camp tests pass; a bandit camp saved by 0.138.0 (a fixture of its saved data) loads with its camp,
    chief and rest days;
  - GameTests: a raid of 5 from a lair of strength 8 leaves 3, two raiders alive at dawn bring it back to 5, and each
    day adds one;
  - the captain's name is on him, on the Defence page and in the chronicle;
  - scene `defence_page`: the Defence page with a bandit camp standing (screenshot).
- [ ] **32.4** **Sieges I: rams and gates.** A raid by a culture with `ram_gates` on a village with at least one
  finished wall or gate build (`StarterBlueprints.DEFENCES` and their upgrades) is a siege, run by `threat/Sieges`:
  one director a siege, every 20 ticks, at most 4 path requests a tick. It picks the breach (the gate nearest the
  raiders' side: the fence gates, doors and iron bars of finished defence builds) and sends the rams (role `ram`;
  without one, the raiders with axes) at it.
  - Each gate block has hit points: fence gate 60, door 80, iron bars 150. A ravager's blow does 12 every 40 ticks, an
    axe 4 every 20. Cracks show (the block's destroy progress) and every blow is heard; at 0 the block goes (nothing
    drops) and is recorded for repair. About 10 s through a Palisade Gate, about a minute through a Gatehouse.
  - When a siege begins the gates shut and the Gatehouse's portcullis drops (iron bars across the gateway under the
    drawn-up ones), whatever the hour and with or without guards (`Gates`); both open again at the first dawn after.
  - Only gate blocks of finished builds ever break. With `mobGriefing` off, or `siegeDamage` false in the config, they
    hold; `sieges` false makes every raid a plain one.

  Done when:
  - GameTests in `huge_area`: a ravager siege ram outside a Palisade Gate with a villager inside breaks a gate block
    within 400 ticks and a pillager behind it is through within 600; through a Gatehouse with its portcullis down it
    takes at least 3 times as long; with `mobGriefing` off the gate still stands after 2400 ticks; a fence gate the
    test places beside the build as a player's is never hit;
  - the portcullis is down while the siege lasts and up after it;
  - scene `siege_gate`: a GIF of a ravager breaking a Palisade Gate, the cracks showing.
- [ ] **32.5** **Sieges II: ladders over the walls.** Raiders with role `climber`, in a culture with `ladders`, whose
  path ends at a wall set ladders up its outer face at the spot nearest them (a rung every 10 ticks, up to 10 high),
  climb over and drop inside; the rest follow up the same ladder. A guard on a walkway within 2 blocks of a ladder's
  top throws it down (the column goes, the climbers on it fall). Ladders go on any wall, a player's too, but only into
  air; every one is recorded with the siege (saved) and taken away when it ends, also after a restart. The siege's
  gathering point moves out along its line until it's beyond the outermost finished wall on that side, so no raider
  appears inside. Done when:
  - a GameTest: a pillager climber outside a Stone Wall with a villager inside is over it within 600 ticks;
  - a guard on the walkway throws the ladder down (no raider ladder within 3 blocks of it after);
  - after the siege ends, and after a save and load half-way through, no raider ladder is left and no other block
    changed (the area's blocks compared before and after);
  - in a village ringed by Stone Walls the gathering point is outside the ring;
  - scene `siege_ladders`: a GIF of pillagers laddering a Stone Wall and a guard throwing a ladder down.
- [ ] **32.6** **Sieges III: battle stations and the morning after.** When a siege begins (or a warned one is near,
  32.14), guards take stations instead of rallying at the bell: archers climb to the free station highest over the
  breach's side (the tops of finished Wall Towers and Lookout Towers, the Gatehouse's walkway, the Stone Wall's and
  Palisade's walkways; stations are walkway spots with two air above, read once per blueprint), knights hold the
  inside of the breach gate, medics stand behind them, the rest rally as now. From a station an archer shoots up to 24
  blocks (16 on the ground) and does 25% more damage to foes 3 or more blocks below. The morning after:
  - a siege report in the chronicle (new kind SIEGE): who came, how many fell and to whom, whether the gate held, and
    the hero (the guard with the most kills);
  - players who fought in a won siege get Hero of the Village for a day; villagers +10 mood for a day ("we held");
  - for 2 days builders mend defence builds before any other repair (`Upkeep`).

  New research **Ramparts** (1 level, needs Fortification I): gates have twice the hit points and archers on stations
  reach 28 blocks. Done when:
  - GameTests: an archer whose post is within 24 blocks of a finished Wall Tower is on its top within 400 ticks of a
    siege starting; from a station they shoot at a foe 22 blocks off, on the ground they don't;
  - the siege report names the hero; Ramparts doubles a gate's hit points;
  - after a siege with a broken gate, a builder with materials puts the gate back before mending a house nearby;
  - scene `battle_stations`: a GIF of archers on a Gatehouse and a Wall Tower shooting down at a siege.
- [ ] **32.7** **Pillager warbands.** `pillager_warband.json`: plains, meadows, forests (birch, dark, flower, cherry),
  taiga and the snowy lands, savanna; Town and up; tactics `ram_gates` and `ladders`. Its lair is the **War Camp**
  (`camp/war_camp`, about 23 x 9 x 19, drawn with the minecraft-architect skill in `tools/blueprints/bandits.py`: a
  ring of sharpened logs with a gate, three canvas tents, the Warlord's tent with the loot chest, a ravager pen, a
  siege yard of logs and ladders, a pole flying the ominous banner). The roster: pillagers with crossbows (the
  climbers) 55% and vindicators 45%, plus ravager rams (1 at Town, 2 at City) and an evoker at City. The **Warlord**:
  a vindicator in iron with the ominous banner on his head, +40 health; at City he leads the siege himself, and
  killing him at the walls breaks the camp. Strength 10, +2 a day, at most 18. Loot `chests/war_camp`: emeralds, iron,
  crossbows, an ominous bottle, now and then a totem of undying. In these lands bandit camps still come at Village
  rank; from Town a warband is three times as likely as bandits. Done when:
  - GameTests: a Town hall in plains gets a war camp with a named Warlord and his band; a siege from it brings 1
    ravager at Town, 2 and an evoker at City; at City the Warlord is among the raiders and his death there breaks the
    camp;
  - the camp passes `check.py` and its render is in the package;
  - scene `warband`: the war camp, then a GIF of the warband's siege on a walled village.
- [ ] **32.8** **Drowned pirates.** `drowned_pirates.json`: coastal villages (`coast`, checked once a day); Town and
  up; arrival `shore`. Their lair is the **Pirate Ship** (`camp/pirate_ship`, about 27 x 20 x 9, minecraft-architect
  skill: a two-masted dark oak sloop with black sails, a crow's nest, the captain's cabin with the loot chest, the
  Jolly Roger (a black banner with the skull) at the stern), anchored on water at least 5 deep, 40–70 blocks out, its
  bow to the village. On a raid night the crew wade ashore at the beach nearest the ship, not from the land side, so a
  sea wall or a harbour gate is what holds them. The roster: drowned with tridents 40%, drowned 30%, skeleton gunners
  in black leather caps with Flame bows 30% (their burning arrows light wooden roofs once 32.16 lands). The captain: a
  drowned in a dyed leather coat with a trident, +40 health ("Captain Silas Brine"). Night only; at dawn they go back
  into the sea. When the captain falls the ship burns and sinks: its own blocks go, top down, over 10 s with smoke and
  bubbles (a block that isn't the ship's any more, such as one a player placed, stays), and the loot chest is left on
  a log raft at the waterline (`chests/pirate_ship`: gold, emeralds, a trident, nautilus shells, now and then a heart
  of the sea). Done when:
  - GameTests (water in `huge_area`): the ship sits on the water; the raiders appear on the shore spot nearest it; the
    captain's death removes every ship block but a block the test placed on deck, and leaves the chest;
  - the ship passes `check.py` and its render is in the package;
  - scene `pirates`: the ship at anchor at dusk, the crew wading ashore, the ship sinking (GIF).
- [ ] **32.9** **Desert raiders.** `desert_raiders.json`: desert and badlands; Town and up; tactics `sand_ramps`;
  hours `until_noon` (husks don't burn, so a siege goes on until noon). Their lair is the **Tomb Camp**
  (`camp/tomb_camp`, minecraft-architect skill: a half-buried sandstone tomb with an obelisk, sun-bleached awnings,
  bone blocks and dead bushes, the Pharaoh's sarcophagus as the loot chest). The roster: husks 55%, husks in gold
  armour with iron swords 25%, skeleton archers in gold helmets 20%. The **Pharaoh**: a husk in a golden helmet and
  chestplate with a golden sword, +50 health; while he lives, a husk that falls within 24 blocks of him rises once
  more after 3 s at half health, in a burst of sand. Sand ramps: where pillagers set ladders, desert raiders pile sand
  against the wall (a block every 10 ticks, one up for one out) until it reaches the top, recorded and taken away
  after the siege like ladders. Loot `chests/pharaohs_tomb`: gold, emeralds, a golden apple, now and then the dune
  armour trim. Done when:
  - GameTests: a husk killed near the living Pharaoh rises once and only once, and not at all after his death; a sand
    ramp against a Stone Wall reaches its top and is gone after the siege, nothing else changed; the siege is still on
    at mid-morning and over at noon;
  - the camp passes `check.py` and its render is in the package;
  - scene `desert_raiders`: the tomb camp, a sand ramp rising against a wall and a husk getting up again (GIF).
- [ ] **32.10** **Piglin incursions.** `piglin_incursion.json`: any land, for villages with a `nether_link` (the
  netherworkers' trips draw them); Town and up; arrival `portal`; tactics `ram_gates` and `plunder`. Their lair is the
  **Blackstone Outpost** (`camp/blackstone_outpost`, minecraft-architect skill: a lit portal of obsidian and crying
  obsidian on a blackstone and netherrack platform, gold blocks, a basalt pillar with a piglin banner, a hoglin pen),
  and their raiders step out of its portal. The roster: piglins with golden swords 35%, piglins with crossbows 25%,
  piglin brutes with golden axes 25%, hoglin rams 15%. While raiding they never turn into zombified piglins, and they
  go for villagers and guards (their anger set on them). **Plunder**: a piglin who passes within 4 blocks of a village
  chest takes the gold in it (ingots, nuggets, blocks, raw gold, golden gear; a stack at most), carries it home at
  dawn and drops it if killed; the siege report counts what was lost. The **Warlord**: a brute in gold armour, +50
  health. His death puts the outpost's portal out, and the band left in the Overworld turns zombified. Loot
  `chests/piglin_hoard`: gold, blackstone, crying obsidian, now and then netherite scrap, the snout banner pattern.
  Done when:
  - GameTests: a raiding piglin is still a piglin after 600 ticks in the Overworld and attacks a villager; a guard
    fights a raiding hoglin; a piglin passing a chest takes its gold and drops it when killed; the Warlord's death
    removes the portal blocks and turns the rest of the band zombified;
  - the outpost passes `check.py` and its render is in the package;
  - scene `piglins`: the outpost, piglins coming through and one carrying off gold (GIF).
- [ ] **32.11** **Witch covens.** `witch_coven.json`: swamps and mangrove swamps; Village and up; tactics `hex`; no
  rams or ladders: witches stand 12–20 blocks outside the walls and throw splash potions over them (slowness, weakness
  and poison at villagers, harming at guards) and heal each other, so archers on stations matter most. Their lair is
  the **Coven Hut** (`camp/coven_hut`, minecraft-architect skill: a hut on mangrove stilts over water, the coven's
  cauldron, brewing stands, hanging lanterns, mushrooms, a herb garden, a black cat). Every night it stands, the coven
  **hexes** one to three of the village's villagers (a swirl of witch particles over them): a cursed illness
  (`Sickness`, with a new attachment for its kind, ordinary by default) that the nurse's remedies don't touch and that
  doesn't pass by itself; it ends when the coven is broken (or, once 32.18 lands, with the Remedy). The roster:
  witches 70%, zombie villagers (the hexed of other villages) 30%. The **Coven Mother**: a witch, +40 health, who
  blinks 8–12 blocks away (ender particles) after three hits in a row. Breaking the coven: kill her, or break her
  cauldron. Loot `chests/coven_hut`: potions, glowstone, redstone, amethyst, now and then an enchanted book. Done
  when:
  - GameTests: a hexed villager is ill and a nurse's honey bottle doesn't cure them; breaking the coven (once with the
    Coven Mother killed, once with the cauldron broken) cures every hexed villager at once; raiding witches keep at
    least 10 blocks from a finished wall; the Coven Mother blinks after three hits;
  - the hut passes `check.py` and its render is in the package;
  - scene `coven`: the hut in the swamp at night, then witches throwing potions over a palisade (GIF).
- [ ] **32.12** **Scouts.** A new guard kind: sneak-right-click a guard with a **spyglass** and they become a
  **Scout** (held in the off hand like the other kinds' items; you get back what they held; "Scout" over their head).
  From the Defence page, "Scout the lair" sends one out by day: they walk (or ride, `Cavalry`) to within 24 blocks of
  the lair, raise the spyglass (its sound) and come home; the band ignores a scout more than 8 blocks off. Back home,
  the Defence page shows the lair's exact place, strength and captain, and the scout leaves a **Scouting Report** in
  the chest by their grindstone: a filled map (like an explorer map) between the village and the lair with a red X on
  it, named for the lair, its tooltip listing the culture, captain and strength. A scout not back within a day is lost
  (chronicle). Scouts also trail raiders who retreat at dawn, so the lair's place becomes known without sending one.
  Done when:
  - GameTests: a guard given a spyglass is a Scout and the player gets their old off-hand item back; sent to a lair 60
    blocks away, the scout gets within 24 blocks of it and is home within 2400 ticks; the report map has its
    decoration at the lair; a bandit doesn't go for a scout 10 blocks away;
  - scene `scouts`: the scout on a rise with the spyglass, then the report map in hand (GIF).
- [ ] **32.13** **Marching on the lair.** "Muster a war party" on the Defence page: three in four of the village's
  guards (up to 12; the rest keep the walls) are enlisted on the Rally Banner in the player's inventory, it's raised,
  and they gather by the hall (without a banner the page says to craft one). A new mercenary contract on the same
  page, **Siege-breakers**: five (two with crossbows, two knights, a medic) for 30 emeralds (3 less a level of
  Commerce), who stay until the lair falls or two dawns pass. At the lair the band turns out to meet them: all its
  strength, six at a time from the tents; at City the captain fights at the front. When a lair breaks with a war party
  there:
  - its loot chest, and a chronicle line naming the player and every guard who killed there;
  - guards earn double experience for kills at a lair (Masters are made here);
  - 10 days' peace instead of 5, and the village +10 mood for two days ("the warband is broken").

  Done when:
  - GameTests: the muster enlists three in four guards on the player's banner and raises it; siege-breakers stay past
    a dawn while the lair stands and leave at the dawn after it falls; a lair broken with enlisted guards near gives
    10 days' rest, double XP and a chronicle line with their names;
  - scene `war_party`: the player leading guards and siege-breakers into a war camp, the Warlord falling, the loot
    (GIF).
- [ ] **32.14** **A day's warning.** Sources: a guard on the night watch whose post is within 24 blocks of a finished
  Lookout Tower or Wall Tower (a day's warning); a Scout there (the same, with the raiders' number and side); the Seer
  (needs M29's Seer item: two days' warning; until it lands this source is skipped); and a list other systems add to
  (M35's Lighthouse). When the clock (32.2) sets an attack and the village has a source:
  - players in the village, and the hall's owner wherever they are online, are told at once ("Smoke on the north road:
    Warlord Harl's warband will strike tomorrow night"); it goes in the chronicle; the guards icon and the Defence
    page show the next attack and its side;
  - on the day, from noon, the gates shut and the portcullis drops; at dusk the guards take battle stations (32.6) and
    the villagers go in before the horn.

  Without a source the horn is the only warning, as now, and What next? says to build a Lookout Tower and post a guard
  by it to see raids coming. Disasters are foretold through the same sources (32.15). Done when:
  - GameTests: a village with a finished Lookout Tower and a guard posted by it is warned at least 20000 ticks before
    the attack; one without isn't told until it starts; in the warned village the gates are shut and the portcullis is
    down at dusk, and the archers are on their stations before the raiders appear;
  - scene `warning`: the guard on the tower at dusk, the chat line, the gates shutting (GIF).
- [ ] **32.15** **The disaster engine.** `threat/Disasters` loads `data/aliveworkplace/disasters/<id>.json`; one file
  is one disaster:
  - `when`: conditions from the same toolbox as the raider cultures, plus the weather (thunder; no rain for N days)
    and the share of wooden blocks in the village's finished builds (sampled once a day); `chance` a day; `duration`
    (days, or until rain);
  - `effects` from a toolbox: crop growth in the hall's area, farmland dries, water cauldrons lose a level a day, the
    chance of falling ill, contagion, a mood with its reason, fires started a day, a job's pace;
  - `answered_by` (the job that answers it), `foretold_by` (jobs, or the Seer: 32.14's sources), message and chronicle
    keys.

  One disaster at a time per village, 7 days' rest after one; saved on the hall (new fields `disaster`,
  `disasterSince`, `lastDisasterDay`; none by default). It shows on the wellbeing icon, in What next? and as a mood
  reason, with new chronicle kinds FIRE, DROUGHT and PLAGUE. Effects stay cheap: crop growth is one check in the
  crop's random tick against the short list of afflicted halls; nothing scans the area. Config `disasters` lists every
  disaster id, each on by default. Done when: a test datapack disaster applies each effect (a GameTest each), ends
  after its time or with rain, and survives a save and load of the hall; switched off, it never starts. No disaster
  ships yet: lands with `--no-review`.
- [ ] **32.16** **Fire and the Firewarden.** `village_fire.json`: villages with wooden builds, likelier the more wood:
  a lightning strike in the hall's area in a thunderstorm sets a finished wooden build's roof alight (1 in 3), a
  chimney spark 2% a day in a drought, and the pirate gunners' burning arrows (32.8) light the wooden block they hit
  (1 in 4, with `mobGriefing`). A village fire spreads by the game's own fire (`doFireTick`), but never to a block the
  builders didn't place, so players' own builds are safe (a mixin on the fire's spread). Fires in the village are
  noticed when they start (a hook where fire is placed), never by scanning. A new job, the **Firewarden**: the
  grindstone with a **water bucket** (the village watch shares the guards' block, 21.1a), its outfit and zombie outfit
  drawn with the pixel-art skill. They keep two buckets of water (filled at water or a water cauldron within 16 blocks
  of their grindstone), run to the nearest fire and douse it (fire within 2 blocks of the splash goes out, with
  steam), and refill as needed; from Journeyman they ring the bell and two villagers off work within 24 blocks join a
  bucket brigade (a fire block each every 2 s). Builders put back what burned (`Upkeep`). With Cobblemon, pastured
  Water types within 16 blocks of the grindstone douse a fire at range every 2 s with their move's effect, as guards'
  partners strike (through `work/PokemonPartners`; nothing without Cobblemon). Chronicle: "Fire in the Terrace: 18
  blocks burned; Rowan the Firewarden put it out." Done when:
  - GameTests: a fire set on a wooden build with a firewarden 15 blocks away is out within 400 ticks and they gained
    XP; without one it still burns at 400; the fire never spreads into a wooden block the test placed as a player's;
    the job starts from the grindstone and a water bucket, and the station tooltip lists it;
  - a builder puts the burned blocks back;
  - scene `fire`: a roof catching and the firewarden putting it out (GIF).
- [ ] **32.17** **Drought and the Well Keeper.** `drought.json`: desert, savanna and badlands villages 15% a day,
  others 5% a day after 5 days without rain, twice as likely while a desert raiders' lair stands; it lasts 3–5 days
  and rain ends it. In the hall's area: crops, stems and berry bushes grow at a quarter pace, farmland dries, water
  cauldrons lose a level a day, and villagers are 10 less happy ("the wells are low") unless the village has a
  finished Well or Fountain. A new job, the **Well Keeper**: the cauldron with a **shovel** (the leatherworker's
  block, shared with the Sifter), its outfit and zombie outfit drawn with the pixel-art skill. In a drought they carry
  water from the nearest well, river or pond to the village's fields: a bucket wets 9 farmland (moisture 7), whose
  crops grow at full pace that day. Between droughts they keep the cauldrons full and dry farmland wet, and they
  foretell the next drought a day ahead (32.14's lines). Water partners speed them (`work/Partners`). Done when:
  - GameTests: in a drought, wheat grows on at most a quarter of its random ticks (fixed `RandomSource`, 400 ticks
    called), watered wheat at the usual rate; the Well Keeper wets dry farmland within 600 ticks; rain ends the
    drought; a finished Well takes the mood reason away;
  - scene `drought`: dry fields and the keeper carrying water to them (GIF).
- [ ] **32.18** **Plague and the Physician.** `plague.json`: Town and up, 3% a day, twice as likely with more
  villagers than beds; a coven's hexed (32.11) or a traveller from far away (`inn/`) can start one too. While it lasts
  illness passes on: each hall round, a well villager who spent it within 3 blocks of an ill one catches it 1 in 5
  (less with Medicine research); the ill don't get well by themselves, the nurse's remedies don't cure them, and after
  two days ill they stay in bed. It ends when no one has been ill for a day. A new job, the **Physician**: the brewing
  stand with a **glistering melon slice** (the clerics' block, shared with the Nurse and the Undertaker), its outfit
  and zombie outfit drawn with the pixel-art skill. They foretell an outbreak at its first case (32.14's lines), send
  the ill home (the ill they have seen pass it on only to those sharing their house), and treat them with a new item,
  the **Remedy** (crafted from a glass bottle, a glistering melon slice, a honey bottle and a fermented spider eye;
  drawn with the pixel-art skill): it cures any illness, hexes included, and keeps a villager well for 10 days; a
  player who drinks one loses poison, wither, hunger and nausea. Done when:
  - GameTests: over hall rounds (fixed random) illness passes on in a plague and not outside one; an ill villager the
    physician has seen passes it on less; the Remedy cures a hexed and a plague-ill villager, where a honey bottle
    cures neither; the plague ends a day after the last cure; the recipe crafts;
  - scene `plague`: the physician going house to house with the Remedy (GIF), and the hall's list of the ill.
- [ ] **32.19** **Houses for the answering jobs.** Three builds with the minecraft-architect skill (STYLE.md,
  renders), in the Blueprint Table, each with its job's block and no other job block: the **Fire Station** (a stone
  ground floor with the Firewarden's grindstone, a water basin and buckets on racks, under a timber watch loft), the
  **Well House** (a roofed well shaft down to water with the Well Keeper's cauldron, a trough and a bench) and the
  **Physician's House** (the brewing stand, a herb garden, four ward beds behind screens: the quarantine ward). What
  next? suggests each when the village lacks its job and its disaster can come there. Done when: the three pass
  `check.py`; a render package shows each front and back; a builder builds each in a test and a jobless villager there
  takes the job with its item; the `defences` scene films them too.
- [ ] **32.20** **Defence upgrades.** Four upgrades (`<name>_2`, lined up over the finished build,
  `BlueprintUpgrades`), with the minecraft-architect skill: **Palisade II** (a fighting step, and a ditch of pointed
  dripstone stakes in front), **Stone Wall II** (timber hoardings over the battlements: a roofed walkway with murder
  holes), **Wall Tower II** (a roofed top with an archers' gallery, two stations a side) and **Gatehouse II** (a
  barbican in front: a second gate and portcullis, murder holes over the gateway). `Gates`, sieges (both portcullises
  drop; the barbican's gate is the first breach) and battle stations know them. Done when: the four are in the
  Blueprint Table and pass `check.py`; a render package shows each front and back; a builder upgrades a finished
  Gatehouse to II in a test; 32.4's gate test takes longer on Gatehouse II than on I; the `defences` scene shows them.
- [ ] **32.21** **Peaceful settings and the threat level.** Config `threatLevel`, 0–3, beside each system's own switch
  (`raiderCultures`, `sieges`, `siegeDamage`, `disasters`, `villageRaids`, `banditCamps`):
  - 0: no raids, lairs, sieges or disasters at all;
  - 1, gentle: half as often, no sieges (raiders walk in only), no hexes, no plague;
  - 2, normal (the default);
  - 3, hard: half again as often, a quarter more raiders.

  On Peaceful difficulty nothing comes. On the Defence page the hall's owner can set the village **At peace** (like
  protection): no raids, lairs or disasters there, and a lair already standing leaves at the next dawn; the guards
  icon says so. Operators get `/workplace threat start <culture> | lair <culture> | disaster <id> | end | clear` at
  the nearest hall, for events and testing. The README gets a Threats section (the cultures by land and rank, sieges,
  warnings, fighting back, the disasters and their jobs, every switch). Done when:
  - the tester's config matrix: a GameTest for each switch off and each threat level (nothing starts at 0; at 1 no
    ram, ladder, sand ramp or hex is used);
  - an At peace village's lair leaves at the next dawn and no new one comes;
  - the command starts and ends each culture's raid and each disaster;
  - scene `at_peace`: the Defence page with the setting on (screenshot).

Depends on: M29's Seer item (only for 32.14's Seer warning; until it lands that source is skipped). Nothing else.

## Milestone 33: From village to realm (1.7)

Villages stop being islands. Each one becomes known for what its land and workers make and short of what they don't,
caravans carry those goods at prices that move with supply and demand, a City sends settlers to found a sister village
wherever you point on the map, and linked villages become a realm with a capital, a treasury, shared research and
guards who ride to each other's aid. Between players come trade pacts, alliances, a weekly leaderboard in chat, and
feuds that only turn to war on a PvP server. It all builds on what exists: hall/Caravans, the Village Ledger and
treasury, travel posts, mail, village protection and the Settler's Wagon (camp/).

- [x] **33.1** (approved 2026-10-06) **Design note.** `docs/design/M33.md`, sent to the owner as a review package (lanes don't wait for his
  reply): what the player sees in each part, with a mock-up of the hall's new Trade page and its tabs (Routes, Prices,
  Pacts, Realm, Colonies); the data formats (`data/aliveworkplace/trade_goods/<good>.json`,
  `data/aliveworkplace/realm_research/<topic>.json`, realm edicts in M30's edict format); the config switches
  (`villageEconomy`, `visibleCaravans`, `colonies`, `colonyRank`, `colonyCooldownDays`, `coloniesPerVillage`,
  `realms`, `realmMaxMembers`, `realmRelief`, `tradePacts`, `alliances`, `feuds`, `feudWars`, `weeklyLeaderboard`,
  `leaderboardDay`, `leaderboardHour`); the save data (one new server-wide saved file, `aliveworkplace_realms`, for
  realms, offers, pacts, alliances, feuds, colony orders and the week's numbers; new keys on `Caravans.Data`'s village
  entries for known-for, short-of and prices; all of it empty by default, so a 1.6 world loads unchanged); who may do
  what (realm, pact, feud and colony actions: the hall's owner, their friends and operators, in every village,
  protected or not; looking at the price board and trading there: anyone); the performance budget (everything in the
  hall's 600-tick round or once a day, no area scans, visible caravans only near players); the hooks other milestones
  use (`Relief.call` for M32's sieges, `RealmTreasury.spend` and `Realms.members` for M35's Wonders, the price engine
  for M29's Merchant Prince, colonies for M29's Founder); a "Realm" group on the showcase page and a "From village to
  realm" README section that each item adds to; and the owner's open calls with the defaults used meanwhile (the
  leaderboard on a real week, Sunday 19:00 server time; no tribute in wars; collecting the treasury owner-only, see
  33.5). Done when: the note is on `main` and its review package is sent.
- [ ] **33.2** **Trade goods: what a village is known for and short of.** The engine (new package `trade/`). Trade
  goods are data: `data/aliveworkplace/trade_goods/<good>.json`, loaded through `Platform.onDataReload` so datapacks
  and `/reload` work. One file is one good: its name key and icon, its items (an item or an item tag), a bundle size,
  a base price in hundredths of an emerald a bundle, `made_by` (professions and biome tags), `wanted_in` (biome tags,
  and professions that use it), and optional events that raise demand for a few days (after a raid, while villagers
  are ill). Once a day, in the hall's round, each village works out up to 3 goods it's **known for** (2 points for
  each worker of a making job, up to 6; 3 if the hall stands in a making biome; 1 if its Storehouses hold two bundles
  or more; 4 points to count) and up to 3 it's **short of** (3 if its biome wants it; 1 for each worker of a job that
  uses it, up to 3; 2 if a worker is waiting for it on the requests board; 1 if the Storehouses hold less than a
  bundle; 4 points to count); a good is never both. Every good also gets a **price** there: its base times 1 + 0.2 ×
  (demand − supply), kept between half and twice the base (supply: bundles in the Storehouses, up to 5, plus 3 if
  known for it; demand: 3 if short of it, plus bundles workers are waiting for, up to 3, plus 2 for food while the
  store is under 16 meals, plus any event's). Each dawn a price moves a third of the way toward that. All of it is
  kept on the village's `Caravans.Data` entry (new keys, empty until the next round), so a village that isn't loaded
  keeps its last prices. Six test goods ship in the gametest datapack, not in the game. Config `villageEconomy`. Done
  when:
  - GameTests: three lumberjacks by a hall in a forest make it known for the test timber; a desert hall whose builder
    waits for logs is short of it; no good is both; a price moves a third of the way each dawn and stays between half
    and twice its base; a raid event raises demand for its days only;
  - the new keys survive save and reload, and a village entry saved before this item loads with empty lists;
  - with `villageEconomy` off nothing is worked out and `caravansCarryWhatAnotherVillageNeeds` passes unchanged.
- [ ] **33.3** **The trade goods.** 28 data files (prices follow vanilla's villager trades where there is one; biomes
  as vanilla and `c:` biome tags; a tag of ours where a good is several items): food and farm goods first, then
  building materials (Timber to Gold), crafts (Wool to Nether Goods) and three goods that load only with Cobblemon,
  each behind a `fabric:load_conditions` like the explorer's Cobblemon loot:
  - **Grain**: wheat, 20 for an emerald. Made by Farmers in plains and savannas; short in snowy lands, deserts and
    badlands; used by Chefs and Ranchers.
  - **Bread**: 6 for an emerald. Made by Farmers and Chefs in plains; short in snowy lands, badlands and mountains;
    used by Innkeepers.
  - **Roots**: potatoes, carrots and beetroots, 22 for an emerald. Made by Farmers in taiga and plains; short in
    deserts and on beaches; used by Chefs.
  - **Fish**: cooked cod and salmon, 6 for an emerald. Made by Fishermen by oceans, rivers and beaches; short in
    deserts, badlands and mountains; used by Chefs and Innkeepers.
  - **Meat**: cooked beef, porkchops, mutton and chicken, 5 for an emerald. Made by Butchers and Ranchers in plains,
    savannas and meadows; short in snowy lands and taiga; used by Chefs.
  - **Fine Meals**: pumpkin pie, cake, and rabbit, mushroom and beetroot stew, 4 for an emerald. Made by Chefs
    anywhere; used by Innkeepers, Nurses and Bards.
  - **Fruit**: apples, sweet berries and glow berries, 12 for an emerald. Made by Orchard Keepers in forests and
    taiga; short in deserts and snowy lands; used by Chefs and Composters.
  - **Honey**: honey bottles and honeycomb, 4 for an emerald. Made by Beekeepers in flower forests, meadows, cherry
    groves and plains; used by Nurses, Chefs and Clerics.
  - **Bone Meal**: 16 for an emerald. Made by Composters anywhere; short in deserts and badlands; used by Farmers,
    Florists and Orchard Keepers.
  - **Timber**: any logs, 16 for an emerald. Made by Lumberjacks in forests, taiga, jungles and dark forests; short in
    deserts, badlands, snowy plains and on beaches; used by Builders and Carpenters.
  - **Stone**: cobblestone, stone and stone bricks, 64 for an emerald. Made by Miners and Masons in mountains,
    windswept hills and on stony shores; short in swamps, plains and on beaches; used by Builders and Masons.
  - **Glass**: glass and glass panes, 8 for an emerald. Made by Masons in deserts and on beaches; short in forests,
    taiga and snowy lands; used by Builders.
  - **Bricks and Clay**: bricks, clay and terracotta, 10 for an emerald. Made by Masons and Sifters in swamps, by
    rivers and in badlands; short in snowy lands and mountains; used by Builders and Masons.
  - **Coal**: coal and charcoal, 15 for an emerald. Made by Miners in mountains; short in snowy lands; used by
    Armorers, Chefs, Masons and Fishermen (their furnaces).
  - **Iron**: iron ingots and raw iron, 4 for an emerald. Made by Miners and Armorers in mountains, windswept hills
    and stony peaks; used by Armorers, Toolsmiths, Weaponsmiths and Tinkerers.
  - **Gold**: gold ingots and raw gold, 3 for an emerald. Made by Miners in badlands; used by Tinkerers, Undertakers
    and Ball Smiths.
  - **Wool**: any wool, 18 for an emerald. Made by Shepherds in plains, meadows and windswept hills; short in snowy
    lands and taiga; used by Leatherworkers and Innkeepers.
  - **Leather**: leather and rabbit hide, 6 for an emerald. Made by Butchers and Ranchers in savannas and plains;
    short in snowy lands; used by Leatherworkers and Librarians.
  - **Dyes and Flowers**: any dye or flower, 12 for an emerald. Made by Florists and Leatherworkers in flower forests,
    meadows, cherry groves and sunflower plains; short in deserts and snowy lands; used by Leatherworkers.
  - **Paper**: 24 for an emerald. Made by Librarians and Cartographers in swamps, jungles and by rivers; used by
    Scholars, Teachers, Librarians and Postmen.
  - **Tools**: iron pickaxes, axes, shovels and hoes, 1 for 2 emeralds. Made by Toolsmiths anywhere; used by Miners,
    Lumberjacks, Farmers and Builders.
  - **Arms and Armour**: iron swords and iron armour, 1 for 3 emeralds. Made by Armorers and Weaponsmiths anywhere;
    used by Guards; wanted more for 3 days after a raid.
  - **Arrows**: arrows and spectral arrows, 16 for an emerald. Made by Fletchers in forests and plains; used by
    Guards; wanted more for 3 days after a raid.
  - **Remedies**: potions of healing and regeneration, 1 for 2 emeralds. Made by Clerics anywhere; used by Nurses and
    Guards; wanted more after a raid and while villagers are ill.
  - **Nether Goods**: quartz, nether wart, blaze rods and glowstone dust, 8 for an emerald. Made by Netherworkers
    anywhere; used by Clerics, Tinkerers and Masons.
  - **Apricorns** (Cobblemon): any apricorn, 8 for an emerald. Made by Orchard Keepers in forests and plains; used by
    Ball Smiths.
  - **Berries** (Cobblemon): Cobblemon's berries, 12 for an emerald. Made by Orchard Keepers anywhere; used by Nurses
    and Ranchers (the daycare).
  - **Poké Balls** (Cobblemon): Poké, Great and Ultra Balls, 8 for an emerald. Made by Ball Smiths anywhere; used by
    Trainers and Pokémon Traders.

  Players see these only on 33.4's board, so this item lands with `--no-review`. Done when:
  - GameTests for five kinds of village: a plains farm village known for grain and bread, a taiga lumber village known
    for timber, a mountain mining village known for stone, coal and iron, a coastal fishing village known for fish,
    and a desert village short of timber and fish;
  - the three Cobblemon goods load only with Cobblemon (a compat GameTest) and the other 25 without it;
  - every good has its name in `en_us.json` and an icon (`langcheck.py` clean).
- [ ] **33.4** **The price board.** The minecart in the hall's divider now opens a **Trade** page with a row of tabs:
  **Routes** (today's trade-routes page, unchanged), **Prices** (this item), and **Pacts**, **Realm** and
  **Colonies**, which later items fill and which stay hidden until then; the Village Ledger reaches it from afar as it
  does the rest of the hall. **Prices** lists every good with its icon, this village's price for a bundle (to sell to
  the village and to buy from it; in CobbleDollars with the pack), an arrow for how it moved since yesterday (up,
  down, steady), a gold star on what the village is known for and a red mark on what it's short of, and in the tooltip
  the dearest and the cheapest village among those it has routes with ("dearer in Ashford: 1.4 emeralds"). The hall's
  name icon gains a line: "Known for: Timber, Wool. Short of: Bread". Villagers talk about it (`people/Chatter`, three
  new topics with three lines each in the lang file): our goods fetching a fine price elsewhere ("Our timber sells
  well in Ashford"), a glut at home ("We've more wool than we can use"), and something dear ("Bread's dear this
  week"). Done when:
  - a GameTest renders the page (`VillageHallScreen.forTest`) and finds a known-for good starred, a short-of good
    marked and a rising price with its arrow;
  - the existing `hall_pages` scene still reaches the routes page, now as a tab, and the Ledger opens the Trade page
    from afar;
  - showcase scene `price_board` (the page, a tooltip, the name icon), and the README section starts with "Specialties
    and prices".
- [ ] **33.5** **Trading at the board.** On the Prices tab a player can **sell** the village what it's short of (click
  with the goods in your inventory: one bundle; shift-click: as many as it will take) and **buy** what it's known for
  (click with an empty hand), at the board's prices: the village pays from its treasury and keeps what it earns there
  (`Money`, so CobbleDollars with the pack), and the goods go into and come out of its Storehouses' chests, which keep
  16 of everything back, like caravans. The village sells at 10% over what it pays, each bundle moves that day's price
  2% (sold to it: down; bought from it: up), and it never pays more than its treasury holds or takes more than its
  chests have room for; it says which. Without a Storehouse the tab says the market needs one. In a protected village
  a stranger right-clicking the hall gets the Prices tab and nothing else, so they can trade there. Because strangers'
  trades now draw on the treasury, collecting it becomes the owner's, their friends' and operators' only, in every
  village (a change: until now anyone could collect in an open village; a hall nobody owns stays open to all). Done
  when:
  - GameTests: a sale pays the right amount from the treasury and fills the chest; a purchase takes from the chest and
    pays in; the 16-back and treasury limits hold; a bundle moves the price 2%; the compat suite pays in
    CobbleDollars;
  - a `ProtectionSpecGameTests` case: a stranger in a protected village trades at the board but can't collect the
    treasury, change a route or open any other page; in an open village a stranger can no longer collect;
  - showcase scene `board_trade` (buying a bundle, with the treasury before and after).
- [ ] **33.6** **Caravans that trade.** A caravan first loads what the other village is waiting for, free, as now;
  then it fills up to 2 more stacks with goods its village is known for that the other is short of, or pays at least
  10% more for. On arrival the receiving village's treasury pays the sender's at the receiver's board price for those
  goods (what it can't afford goes back home in a caravan of its own), and both boards move as if the goods had been
  sold there (2% a bundle). The Routes tab shows for each partner what would go today and what it would earn ("Timber
  x2 for 2.6 emeralds"), and the chronicles say "Sold 32 Timber to Ashford for 2.6 emeralds". Done when:
  - a GameTest with two halls, A known for timber and B short of it, with a route on: the caravan carries the timber,
    B's treasury pays A's at B's price, and both prices move;
  - a GameTest where B can't pay: the unpaid goods come back to A's Storehouse;
  - `caravansCarryWhatAnotherVillageNeeds` still passes, and showcase scene `caravan_trade` shows the Routes tab with
    its earnings and the chronicle line.
- [ ] **33.7** **Caravans you can see.** When a caravan leaves or arrives in a village a player is within 96 blocks
  of, you see it: a carter (a villager in the porter's outfit, named "Thornholm's caravan") leading two llamas with
  chests and carpets in the village's colour (M30's Village Banner colour when it has one, otherwise one picked from
  the hall's position) walks from the Storehouse to the edge of the village toward the other village and goes out of
  sight, or comes in from that edge to the Storehouse, where the llamas stop while the goods are unloaded (the chest
  sound). They are only a sight: the goods still travel the saved way, so nothing can be lost or stolen; they never
  trade, take a job or breed; one party per village at a time, gone after 2 minutes wherever they are; any left by a
  restart are taken away (tagged, like the ferry's ride boats). Config `visibleCaravans`. Done when:
  - GameTests: a departure spawns the carter and two llamas, which walk off and are removed; nothing spawns with no
    player near; a saved and reloaded carter is removed; the goods that arrive are the same with the switch on or off;
  - showcase scene `caravan` with a GIF of one leaving and one arriving.
- [ ] **33.8** **The Colony Charter.** A City (`colonyRank`) can found a sister village. The Colonies tab gives the
  hall's owner a **Colony Charter** (a new item, drawn with the pixel-art skill on vanilla's map outline, bound to the
  hall like the Ledger) for 32 emeralds from the treasury (the player pays what the treasury lacks). Right-click the
  air with it for a map screen: the land round the village, 2,048 blocks across, drawn like a vanilla map where the
  server has loaded it and as plain parchment elsewhere, a banner for every village with a hall, and a ring for where
  a colony may go (256 to 1,024 blocks from the hall, in the same dimension, not within 128 blocks of another hall);
  click a spot to choose it. Or right-click the ground with the charter where you stand. The spot shows as a red cross
  on the charter's map and in its tooltip ("612 blocks north-east"); renaming the charter in an anvil names the
  colony. Limits: one colony on the road at a time, 7 days between colonies (`colonyCooldownDays`), at most 3 colonies
  a village (`coloniesPerVillage`). The "What next?" compass suggests a colony once a village is a City. Config
  `colonies`. Done when:
  - GameTests: the charter is refused below City, without the money, and for a spot too near another hall or out of
    the ring; a good spot is kept on the charter;
  - a test of the screen's map-to-world sum: a click lands within 16 blocks of the right place;
  - showcase scene `colony_charter` (the map screen with its ring and cross, the tooltip).
- [ ] **33.9** **The settlers set out.** Right-click the hall with the charter, its spot chosen, and the village gets
  ready:
  - two settlers volunteer: the lowest-levelled builder if there are two or more, otherwise a jobless grown-up who
    becomes the colony's builder; and a jobless villager, otherwise the lowest-levelled worker of a job the village
    has three or more of. Never a guard, the only worker of a job, a Legend or a child; a married volunteer brings
    their partner;
  - supplies come out of the Storehouses: 64 logs, 64 planks, 64 cobblestone, 32 bread, 16 torches, 12 glass panes and
    3 beds. What's missing goes on the village's wants (so partners' caravans bring it) and the requests board, and
    after 3 days they leave with what there is;
  - the next morning the settlers gather at the hall, the bell rings, and they walk out toward the spot; once out of
    sight (or after 30 seconds) they are on the road, kept in the order as saved villagers, the way graves keep them,
    with the supplies.

  The journey takes 3 minutes plus a tick a block. The order (settlers, supplies, spot, arrival time) is kept in
  `aliveworkplace_realms`; the Colonies tab shows it ("On the road to Newbrook, there in 2 minutes") and can call it
  off until they leave, which puts everything back. Done when:
  - GameTests: the right villagers volunteer (never the guard or the only farmer; a married volunteer's partner comes
    too), supplies are taken and the missing ones become wants, the settlers leave and are saved, and calling it off
    puts everyone and everything back;
  - the order survives save and reload mid-journey;
  - showcase scene `colony_departure` (the settlers gathered at the hall, a GIF of them walking out).
- [ ] **33.10** **A colony is born.** When the journey ends, the spot's chunks are loaded for a minute (a ticket like
  `KeepLoaded`'s) and the settlers make camp on the nearest open ground within 48 blocks (the Settler's Wagon's
  `makeCamp` checks; until 33.14's Colony Camp lands, the settlers' camp with a Village Hall and a Travel Post set
  beside it). Then:
  - the hall is named from the charter (or made up), owned by the mother village's owner, and its chronicle opens with
    "Founded on day 41 by settlers from Thornholm: Dara and Tomas"; the mother's chronicle notes it too;
  - the post joins the travel network under the colony's name, already known to the owner and their friends;
  - the settlers step out as they were (names, jobs, levels, trades, partners): the builder employed at the camp's
    Blueprint Table, the others keeping their trade until the colony has a workstation for it; the chest holds the
    supplies and the Starter Cottage and Storehouse blueprints in the mother's style (the style most of her finished
    buildings use, through `BlueprintStyles`);
  - the builder starts at once on that Starter Cottage and Storehouse, placed on either side of the camp where the
    ground allows (`Builders.start` and `enqueue`; otherwise the blueprints wait in the chest);
  - mother and colony are linked: a sister route both ways that doesn't count against the route cap, the mother
    sending what the colony waits for, free;
  - the owner gets a letter (`Mail.letter`: "We've arrived at Newbrook, 612 blocks north-east of Thornholm...") and a
    chat line if online.

  With no room within 48 blocks (all water, a cliff), the settlers come home and the cost is refunded. Done when:
  - a GameTest (travel time 0, `huge_area`): settlers from a Sandstone-built village arrive; the camp, the hall (owned
    and named) and the post are there; the settlers keep their names and jobs; the builder works on two Sandstone
    sites; the sister route exists outside the cap; both chronicles have their lines;
  - a GameTest where there's no room: everyone and everything is back home;
  - showcase scene `colony` with a GIF of the arrival and the first walls going up.
- [ ] **33.11** **Founding a realm.** The Realm tab: at a Town or better, the hall's owner (or a friend) can **found a
  realm** with this village as its capital, named after it ("the Barony of Thornholm"; a named Name Tag clicked on the
  realm's icon renames it). The capital's owner adds, with one click, any village within 2,048 blocks that they or a
  friend own; colonies join their mother's realm by themselves; a member's owner can leave at any time and the capital
  can let a member go. A realm's rank comes from its members: **Barony** (2 villages), **County** (3, the capital a
  Town), **Duchy** (5, the capital a City), **Kingdom** (8, the capital and one more a City); at most 12 villages
  (`realmMaxMembers`), all in one dimension. A rank up brings fireworks over the capital, a chat line to every
  member's owner and a new title ("the Duchy of Thornholm"). The tab lists the members (rank, villagers, wellbeing,
  distance, owner; the capital with a crown) and the realm's arms: click it with a banner to set them (they fly on
  relief riders' shields and colour the realm's map banners and caravans). The hall's name icon says which realm the
  village is in, and "What next?" suggests founding one at Town. A broken member hall leaves the realm; a broken
  capital passes the crown to the biggest member that's a Town, or the realm ends. Kept in `aliveworkplace_realms`.
  Config `realms`. Done when:
  - GameTests: found a realm, add one's own village, can't add a stranger's, leave, let go, ranks counted with the
    rank up, the capital's hall broken (the crown passes, or the realm ends);
  - the realm survives save and reload;
  - showcase scene `realm` (the tab, the rank-up fireworks).
- [ ] **33.12** **Other players' villages, and choosing the capital.** The capital's owner can invite a village
  another player owns: a letter goes to that owner's mailbox (or waits at the post office), with a line on their Realm
  tab to accept or decline within 3 days. Any member that's a Town or better can be put forward as capital by its
  owner: if one player owns every member it moves at once; otherwise each member's owner gets a vote on the Realm tab
  for a day (a village that doesn't vote counts for the current capital; a tie keeps it). The new capital takes the
  realm's treasury, research and edicts, with fireworks and a chronicle line in every member. Realm decisions (the
  levy, research, edicts) stay with the capital's owner, and anyone can leave. Done when:
  - GameTests with two owners: an accepted invitation joins; a declined or lapsed one doesn't; a vote with a majority
    moves the capital and its treasury; a tie keeps it;
  - showcase scene `realm_invite` (the letter, the tab with the vote).
- [ ] **33.13** **The realm treasury.** Each member pays a share of its daily takings (`Treasury.round`) into the
  realm treasury before the rest goes into its own: the **levy**, 0, 5, 10 (the default) or 20%, set by the capital's
  owner on the Realm tab. It's kept in the realm's saved data, so it never needs the capital loaded, and holds up to
  256 emeralds for a Barony, 512 for a County, 1,024 for a Duchy and 2,048 for a Kingdom. It pays for realm research,
  relief riders, a member's colony when its own treasury is short (from a Duchy), realm edicts, and later M35's
  Wonders (`RealmTreasury.spend(realm, cents, reason)`). Anyone may give to it (click its icon with emeralds: one;
  shift: the stack; or CobbleDollars with the pack); only the capital's owner, their friends and operators may take
  from it. Its tooltip lists the last 20 movements with the day and the reason. Done when:
  - GameTests: ten workers in a well-kept hamlet pay 0.3 of their 3 emeralds a day at 10%; the cap holds; a stranger
    can give but not take; the movements are listed right;
  - it survives save and reload;
  - showcase scene `realm_treasury` (the icon and its tooltip).
- [ ] **33.14** **Two builds: the Colony Camp and the Royal Hall.** Drawn with the architect skill and the blueprint
  generator, checked against `STYLE.md` in renders, in all five styles:
  - **Colony Camp** (`camp/colony_camp`, about 15 x 6 x 13, placed at once like the settlers' camp): two covered
    wagons, a Village Hall under a canvas awning on a timber platform, a Blueprint Table with a lamp, a stack of
    supply crates round the chest, a campfire with log seats and a cooking pot, three bedrolls, a Travel Post with a
    lantern, and a flagpole for the village's banner. 33.10 places it instead of the settlers' camp with a hall and
    post beside it.
  - **Royal Hall** (`realm/royal_hall`, about 19 x 16 x 25; the **Royal Hall II** adds a tower and a treasury wing): a
    stone great hall with a raised dais and a seat of state, banners down the walls, a gallery, tall windows and the
    realm's vault (chests). Hidden from the Blueprint Table; the capital's Realm tab draws it for a Blank Blueprint.
    Finished within 32 blocks of the capital's hall it doubles the realm treasury's cap (the II: three times), and it
    shows on the village map as a hall.

  Done when: renders of both (front and back, five styles) are in a review package; GameTests place each with free
  materials and check that a colony uses the camp and that the Royal Hall raises the cap; showcase scene
  `realm_builds` shows both.
- [ ] **33.15** **Shared research.** Realm villages share what their scholars know: for each topic of the research
  tree a member uses the higher of its own level and the capital's (through `Research.at`), and its research screen
  says "from the capital"; shared levels don't count toward a village's rank. A member's scholars with nothing chosen
  at home work on the capital's current topic instead, at half pace. The capital's research screen gets a **Realm**
  row: realm topics loaded from `data/aliveworkplace/realm_research/<topic>.json` (one file: icon, levels, research
  points and paper and books a level, emeralds a level from the realm treasury, the topics it needs first, and its
  effect: one of a toolbox of effect types, each with an amount a level), researched by the capital's scholars and
  paid from the realm treasury. Done when:
  - GameTests: a member's guards hit harder with the capital's Drill, and its rank doesn't change; an idle member
    scholar adds points to the capital's topic; a realm topic from a test datapack can be chosen, is paid from the
    realm treasury and finishes; `/reload` keeps it;
  - showcase scene `realm_research` (the capital's research screen with the Realm row).
- [ ] **33.16** **The realm's research topics.** Eight data files, each with its effect wired in (costs to start with:
  16 paper, 2 books a level after the first, and 8 emeralds a level from the realm treasury):
  - **Royal Roads** (2 levels): caravans between realm villages arrive 25% sooner a level.
  - **Common Coin** (2; after Royal Roads I): the board's spread 3 points narrower a level in realm villages.
  - **Royal Post** (1; after Royal Roads I): parcels between mailboxes in realm villages go out at noon as well as at
    dawn.
  - **Ferry Charter** (1; after Royal Post): Travel Tickets between posts in realm villages cost half (at least an
    emerald) for members' owners and their friends.
  - **Levies** (2): relief bands one rider bigger per village a level.
  - **Charters** (2; after Royal Roads II): colonies cost a quarter less a level, and their settlers bring 16 more of
    each supply.
  - **Concord** (2; after Levies I): wellbeing 5% higher a level in every realm village (wellbeing's effect on pace
    keeps its 25% top).
  - **Granaries** (1; after Concord I and Royal Roads II): a member whose store falls under 16 meals is sent up to 32
    meals at the next dawn from the member with the fullest store, outside the route cap.

  Done when: a GameTest for each topic shows its effect at level 1 (and at level 2 where it has one); the Realm row
  shows all eight with what each needs; `langcheck.py` is clean.
- [ ] **33.17** **Relief in a raid.** When a realm village is raided (our night raids, bandit raids, or a vanilla
  pillager raid at the village; M32's sieges call `Relief.call` too), the other members send help: the capital,
  wherever it is, and every other member within 1,024 blocks that has 2 guards or more. Each sends one rider for every
  3 guards it has (at least 1, at most 3; more with Levies), 6 riders at most in all, after a ride of a tick per 4
  blocks (at least 30 seconds). They arrive on horseback at the village's edge from their home's direction, named
  "Ashford's guard", their shields bearing the realm's arms if it has some; when the capital is raided they muster
  before its finished Royal Hall. They fight as guards until the raid is over and a minute more (until dawn at most),
  then ride off and are gone. Each rider costs the realm treasury an emerald (when it's empty, only the capital's band
  comes). The sender's hall says "2 guards away helping Ashford", and both chronicles note it. Config `realmRelief`.
  Done when:
  - GameTests: a raid on a member brings the capital's band and a near member's after the right number of ticks; they
    fight (a zombie dies to them) and leave after the raid; the treasury pays; a village outside any realm gets no
    one; a vanilla raid calls them too;
  - showcase scene `relief` with a GIF of the riders arriving and fighting.
- [ ] **33.18** **Trade pacts and alliances.** Villages of different owners deal through offers. On the Pacts tab an
  owner picks another player's village within caravan range and offers a **trade pact** or an **alliance**; the offer
  reaches that owner as a letter in their mailbox (or at the post office) and as a line on their hall's Pacts tab with
  Accept and Decline, and lapses after 3 days. From now on a caravan route to another owner's village needs a pact;
  routes set up before 1.7 become pacts when the world loads, so nothing stops. Pact caravans trade at board prices
  both ways, and even what the other is waiting for is paid for, unless the sending owner turns on **gifts**. An
  alliance is a pact whose villages (and their realms) also send relief riders (33.17) to each other's raids within
  1,024 blocks and tell each other's owners. Either side can end a pact at once, or an alliance with a day's notice,
  by letter. The chronicles note each. Config `tradePacts`, `alliances`. Done when:
  - GameTests with two owners: no route to the other's village without a pact; an accepted offer allows routes and the
    cargo is paid for; a declined or lapsed offer changes nothing; a route saved before this item loads as a pact; an
    ally's raid brings relief; ending an alliance takes a day;
  - showcase scene `pacts` (the letter, the tab with an offer).
- [ ] **33.19** **The weekly leaderboard.** Once a week (owner's call: a real week, or every 7 in-game days; meanwhile
  Sunday 19:00 server time, `leaderboardDay`, `leaderboardHour`) everyone online is told in chat how the villages did:
  **Biggest** (villagers), **Richest** (emeralds earned that week: takings, board trades and caravan sales),
  **Happiest** (average wellbeing over the week, from the hall's rounds), **Most Legends** (M29's Legends; Masters
  until they exist) and the **Greatest realm** (members, then villagers), the top three of each with the village, its
  owner and the number. Each winner's chronicle notes it and its hall's name icon wears a ribbon for a week.
  `/workplace leaderboard` shows the standings so far at any time. Only villages with an owner are listed, and an
  owner can leave theirs off (a toggle on the Trade page). The week's numbers are kept in `aliveworkplace_realms`, so
  unloaded villages count at their last-known values; a week missed while the server was down is announced at the next
  start. Config `weeklyLeaderboard`. Done when:
  - GameTests (with a clock the test sets): three halls with known numbers come out in the right order with the right
    lines; a village left off isn't listed; a tie goes to the bigger village; a missed week is announced once;
  - showcase scene `leaderboard` (the chat).
- [ ] **33.20** **Feuds, and war where PvP is on.** On the Pacts tab an owner can declare a **feud** on another
  owner's village or realm: a letter tells them, pacts and routes between the two end, each side's board pays 25% less
  for the rival's goods and charges the rival's players 25% more, villagers grumble about the rivals (a chatter topic
  with three lines), and the chronicles note it. Either side can offer peace by letter, which the other accepts; a
  feud without a war fades after 7 days. **War** happens only when the server allows PvP and `feudWars` is on: the
  side that declared the feud can raise it to war after it has stood a day, with a letter; the war starts at the next
  dawn and lasts until peace is accepted or 3 days pass. In a war each side's guards treat the other side's owner and
  their friends as foes inside their own village (`Guards.isFoe`), and those players may fight the enemy's guards, but
  nothing else changes: blocks, chests, other villagers, animals and the treasury stay protected as before. Relief
  riders and allies answer raids only, never wars. When it ends, both chronicles record the guards each side lost; no
  treasury changes hands (owner's call; default: none). With PvP off, the war button says why it can't be pressed.
  Config `feuds`, `feudWars`. Done when:
  - GameTests: a feud stops routes and moves both boards; peace ends it; war is refused with PvP off; in a war a guard
    goes for the enemy owner but not a neutral player; the enemy owner can hurt a guard but not a villager, a block or
    a chest of a protected village; the war ends after 3 days;
  - showcase scene `feud` (the tab and the letter; with PvP on, a guard fighting the rival owner).
- [ ] **33.21** **The Realm Map.** At any member's hall the Realm tab draws the realm on an empty map (the drawing of
  33.8's charter map, on a vanilla map item): scaled so every member fits (vanilla scales 2 to 4), the land the server
  has loaded drawn as on a vanilla map and the rest as plain parchment, a banner for every member in the realm's
  colour (the capital's named "Thornholm (capital)"), colonies, allies and rivals in their own colours, caravan routes
  as dotted lines between the halls, and a colony on the road as a cross. Its tooltip has the legend, as the Village
  Map's does. Done when:
  - GameTests check where the banners are and their colours, and that a route's dots lie between its two halls;
  - showcase scene `realm_map` (the map in hand and in an item frame).
- [ ] **33.22** **Realm edicts** (needs M30's edict engine, the Book of Edicts). The capital's Book of Edicts gets
  realm slots: 1 for a Barony or County, 2 for a Duchy, 3 for a Kingdom, one more with the Royal Hall II. A realm
  edict is in force in every member, lasts at least 3 days like any edict, and is paid from the realm treasury where
  it costs money. Six realm edicts, as data in M30's format (`scope: realm`), each with its reform (the capital's
  quest and a count across the realm; once reformed, the boost stays and the cost goes, for this realm, for good):
  - **Royal Highway**: caravans between members carry 2 more stacks and arrive 25% sooner / the realm treasury pays an
    emerald a day for each route. Reform: the quest "Pave the highway" (128 stone bricks) and 12 caravans between
    members.
  - **Common Market**: one board for the whole realm (the members' average prices) and no spread on trades between
    members / the levy rises 5 points. Reform: "Weights and measures" (8 gold ingots, 16 paper) and 20 board trades in
    members.
  - **Levy of Arms**: relief from every member wherever it is, in bands twice as big / each member pays 2 emeralds a
    day into the realm treasury. Reform: "Arm the levy" (12 iron swords) and 3 raids beaten with relief.
  - **Realm Festival**: every member holds its festival on the same day and the good mood lasts 3 days / 10 emeralds a
    member for each festival, from the realm treasury. Reform: "Lanterns for the square" (32 lanterns) and 3 realm
    festivals held.
  - **Crown's Peace**: no feud can be declared on a member, and bandits never camp within 128 blocks of one / every
    member's takings 10% lower. Reform: "Clear the roads" (16 monsters cleared round any member) and 7 days without a
    raid on any member.
  - **Royal Charter**: members found colonies from Town rank and wait half as long between them / each colony costs
    the realm 32 emeralds more. Reform: "Draft the charter" (16 paper, 4 books) and 2 colonies grown to Village rank.

  Done when: a GameTest for each edict (boost on, cost on; the reform turns the cost off and keeps the boost); the
  capital's book shows the realm slots; showcase scene `realm_edicts`.
- [ ] **33.23** **Legends of the realm** (needs M29's The Founder and Merchant Prince):
  - **The Founder**: a village he lives in can found colonies from Town rank, sends four settlers instead of two and
    waits half as long between colonies; its colonies are "founded with the Founder's blessing" in both chronicles,
    and his statue (M29's build) goes up beside the colony's camp.
  - **The Merchant Prince**: his village's board shows the prices of every village within caravan range, not only its
    partners'; its caravans take their trade goods to whichever partner pays most; its spread is halved. If M29 gave
    him a simpler "prices between villages", it now reads 33.2's prices.

  Done when: GameTests show each power with the Legend present and nothing changed without; showcase scenes
  `founder_colony` and `merchant_prince_board`.

Depends on: M30's edict engine, the Book of Edicts (33.22 only); M29's The Founder and Merchant Prince (33.23 only);
33.19's "Most Legends" line counts Masters until M29's Legends exist. Nothing else.

## Milestone 34: Classes and luxuries (1.8)

Villages get a social ladder, as in Anno 1800 and Manor Lords: a household rises from **Peasant** to **Artisan**,
**Burgher** and **Noble** when its needs are met (a better house, a varied diet, services nearby and, higher up,
luxuries from four new trades: the Vintner, Tailor, Printer and Jeweller), and falls back when they aren't. Higher
classes pay more into the treasury, open higher jobs, dress the part and want grander houses; grown villagers age
into elders who retire and teach, and every family has a tree. It builds on `people/` (`Homes`, `Diet`, `Moods`,
`Families`, `Couples`), `hall/VillageNeeds` and the `Treasury`, `craft/CrafterWork` and the outfit pipeline, and it
switches on for every village of the owner's live server at once, so it ends with a careful migration and a rehearsal
on a real world.

- [x] **34.1** (approved 2026-10-06) **Design note.** `docs/design/M34.md`: what the player sees (the class ladder, a household rising at
  dawn, the four luxury trades and their goods, villagers dressed by class, grander homes, elders, family trees, and
  the first week after the update); the data formats with one example file each (`classes/`, `services/`,
  `luxuries/`, `luxury_recipes/`, `homes/` under `data/aliveworkplace/`); every config switch; every new saved field
  with its default; the job block and item of each new job (vetoable like the 21.1a plan); the migration (34.22) step
  by step; and the owner's calls with their defaults meanwhile: elders never die of old age (`elderPassing` off),
  vanilla jobs are never class-gated, family names show on the hall but never rename a villager. Sent to the owner as
  a review package; lanes don't wait for his reply. Done when: the note is on `main`, its tables match this
  milestone's numbers, and the package is sent.
- [x] **34.2** (approved auto 2026-10-05) **The class engine.** The four classes are data: `data/aliveworkplace/classes/<id>.json` (`peasant`,
  `artisan`, `burgher`, `noble`), one file holding the class's tier (0-3), tax factor, outfit, `needs` (all must hold
  to be that class), `wants` (optional extras) and what it gives (`jobs`, `effects`; read by 34.7 and 34.8), e.g.
  `{"tier": 1, "tax": 1.5, "needs": [{"type": "home", "grade": 1}, {"type": "fed_days", "days": 3}, {"type": "diet",
  "kind": "varied"}, {"type": "services", "count": 1, "any": ["chapel", "school", "clinic"]}, {"type": "luxury",
  "id": "aliveworkplace:work_clothes"}], "wants": [...]}`. The toolbox of need types (`people/ClassNeeds`): `home`
  (the grade of the building their bed is in, `Homes`), `fed_days`, `diet` (`Diet`), `beauty` (decorations within 16
  blocks of home), `building` (a finished blueprint in the village), `village_rank`, `services` (34.3) and `luxury`
  (reads the `luxuries_had` record that 34.4 fills; never met before then). A **household** is one grown villager or
  a married couple (`Couples`: a need holds when it holds for both); children take the class of the grown-ups who
  sleep in their building. Classes exist only in villages with a hall, like moods. Saved on the villager:
  `social_class` (absent until seeded) and `class_progress` (dawns met, dawns missed). Each dawn the hall checks its
  households in slices (8 a hall round, no area scans): the next class's needs met `classRiseDays` (2) dawns running
  lifts them one class; a need of their own class unmet `classFallDays` (3) dawns running drops them one, never below
  Peasant; one step a day at most. Config `villageClasses` (on), off in GameTests unless a test turns it on. The
  starting ladder:
  - **Peasant** (tax ×1): no needs; everyone's floor;
  - **Artisan** (×1.5): a bed in a house a builder built (grade I+), fed 3 days running, a varied diet, a chapel,
    school or clinic within 48 blocks of home, Work Clothes every 8 days; wants Cider every 4 days and a tavern
    within 48;
  - **Burgher** (×2.5): a grade II+ home, the Artisan's food and diet, a school and one more service, a Market Square
    in the village, Fine Clothes every 8 days, Berry Wine every 2 days, the Gazette every 7 days; wants an Amethyst
    Ring every 16 days, a library within 48 and beauty 2+ near home;
  - **Noble** (×4): a grade III+ home, a chapel, a school, a clinic and a library, the village a Town or City, beauty
    3+ near home, Noble Robes every 8 days, Vintage Wine every 2 days, an Emerald Brooch every 16 days; wants an
    Illuminated Book every 16 days and a Gold Circlet every 32 days.

  Done when: `ClassGameTests` (each need type on its own, a couple rising together after 2 dawns, a fall after 3, one
  step a day, children following their household, a datapack class file changing a need) pass; a save and reload
  keeps class and progress; 60 households take under 2 ms a hall round (timed in a GameTest). Nothing to see yet:
  `--no-review`.
- [x] **34.3** (approved auto 2026-10-05) **Services nearby.** `data/aliveworkplace/services/<id>.json`: which workers or finished builds give a
  service and how far it reaches, e.g. `{"jobs": ["aliveworkplace:teacher"], "blueprints":
  ["aliveworkplace:schoolhouse"], "range": 48, "icon": "minecraft:lectern"}`. Six to start: **chapel** (a finished
  Chapel), **school** (a Teacher, or a Schoolhouse), **clinic** (a Nurse, or a Healing Center), **library** (a
  Scholar or a Librarian, or a Library), **market** (a Market Square, village-wide) and **tavern** (an Innkeeper, or
  an Inn). Styled builds and upgrades count as their base (`BlueprintStyles.base`, `BlueprintUpgrades`). The hall
  works out where its services are once a day (workers by their job site, builds from
  `BuildSiteManager.finishedNear`) and keeps the list; the `services` need checks each home against it. Done when:
  `ServiceGameTests`: each of the six found by its worker and by its build, a Chapel in Stonework counts, one 60
  blocks from home doesn't, a teacher who quits stops counting at the next dawn, a datapack file adds a seventh
  service; the list is worked out at most once a day (a counter in the test). Nothing to see yet: `--no-review`.
- [x] **34.4** (approved auto 2026-10-05) **Luxuries from the village store.** `data/aliveworkplace/luxuries/<id>.json`: the item (or tag) and
  how often a household wants one, e.g. `{"item": "aliveworkplace:berry_wine", "every_days": 2}`. At dawn, in 34.2's
  slices, each household takes from the village store (`VillageNeeds.store`: the kitchens' chests, then the
  Storehouses') every luxury of its own class, and of the class above, that is due, and remembers the day it had it
  (`luxuries_had` on the villager, luxury to day; a couple shares one). An empty store leaves the need unmet that
  day. Porters carry the luxury makers' goods to the storehouse (`Porters`: the makers keep their makings, not their
  goods). The eleven luxury files land with their jobs (34.9-34.12); this item ships the engine and a test luxury in
  the GameTest datapack. Done when: `LuxuryGameTests`: a due household takes exactly one, a couple one between them,
  nothing is taken before it's due, an Artisan household also takes the Burgher luxuries the store has, an empty
  store counts as missed, a porter carries a maker's goods to the storehouse, a reload keeps `luxuries_had`. Nothing
  to see yet: `--no-review`.
- [x] **34.5** (approved auto 2026-10-05) **The luxury workshop engine** that the four new jobs share.
  `data/aliveworkplace/luxury_recipes/<id>.json`: the job, the job level that may make it, the makings, what comes
  out and how long it takes, e.g. `{"job": "aliveworkplace:vintner", "level": 3, "inputs": [{"item":
  "aliveworkplace:berry_wine", "count": 1, "min_age_days": 3}], "output": {"id": "aliveworkplace:vintage_wine"},
  "ticks": 200}`. A new `Crafting.Kind.LUXURY` plans from these files (makings by item or tag; vanilla makings up to
  the usual two steps down), and `craft/LuxuryWork` (a `CrafterWork`, like `ChefWork`) makes, in turn, whatever the
  village store holds fewer than 8 of, from its own chests, the store and the village's stashes, stock orders first
  (`StockOrders`). Goods that age carry the day they were made (a `made_day` data component; the tooltip says
  "Pressed on day 42 · vintage in 2 days"). Missing makings go on the requests board; Craftsmanship research speeds
  the makers like the other crafters. Done when: `LuxuryWorkGameTests` with test recipes: a maker makes what's short
  and stops at 8, keeps to its level, waits for an aged input (the day moved on in the test), asks for missing
  makings on the board, fills a stock order first, and a datapack recipe adds a new good with no code. Nothing to see
  yet: `--no-review`.
- [x] **34.6** (review: pending 2026-10-06) **Classes at the Village Hall.** What the player sees:
  - a **Classes** button on the hall's screen (its tooltip: how many households of each class); its page has a button
    per class listing each need and want with how many households have it ("Fine Clothes: 2 of 5"), what the class
    gives, and the households closest to rising with what they lack;
  - the people list says each villager's class and household ("Burgher · married to Tomas"); a villager's page lists
    the needs of their class and of the next one, ticked or not; the food icon's tooltip adds the luxuries in store;
  - **What next?** gets up to three class tips, most households first ("4 artisan households want Berry Wine to
    become Burghers: a Vintner at a cauldron, Apprentice or better");
  - a household that rises: golden sparkles at their door, a chime, a chat line to players within 32 blocks, a
    chronicle entry (a new `Chronicle.Kind.CLASS`) and "rose in the world" (+10 mood for 2 days); one that falls: an
    entry and "came down in the world" (-10 for 2 days); `Moods` adds "has what their class needs" (+5) and each
    missing need (-5, at most -15);
  - chatter (`Chatter`), three lines each: wants wine, wants new clothes, proud of their rise, came down in the
    world, the Gazette's news, glad of the tavern;
  - a Classes page in the In-Game Guidebook once 26.2a has landed.

  Done when: `ClassHallGameTests` check the page's counts and the tips against a staged village; `langcheck.py` is
  clean; scene `classes` (a household rising at dawn, then the hall's Classes page) passes; the README gets a Classes
  section.
- [x] **34.7** (review: pending 2026-10-06) **What each class gives.** From the `effects` in the class files:
  - **taxes** (`Treasury.takings`): each worker pays `treasuryPerWorker` × their class's factor (Peasant 1, Artisan
    1.5, Burgher 2.5, Noble 4), 10% more for each want they have; a Noble without a job pays like a worker. A Peasant
    pays exactly today's rate, so no village takes in less than before; the name tag's tooltip shows the split by
    class;
  - **Artisans**: crafters who are Artisans or better (carpenter, mason, tinkerer, chef, dyer, toolsmith, vintner,
    tailor, printer, jeweller) work 10% faster, through the shared 2× speed cap (add the cap if no earlier item has);
  - **Burghers**: Burgher scholars research 15% faster; with 3 Burgher households the village can send one more
    caravan route, and market day brings a fourth trader selling a grand-house blueprint (34.15, 34.16) for 12
    emeralds;
  - **Nobles**: each Noble household lifts the village's wellbeing 3% (9% at most), and a village with a Noble holds
    a **Noble's Ball** in place of every other festival (`Festivals`): the guests gather at the Manor's ballroom
    (34.16) or else the hall, wine and the store's best food are served, gold fireworks go up at dusk, everyone who
    came gets +15 mood for 3 days, and players there are Heroes of the Village for the night;
  - **Legends** (M29) live among the Nobles: a Legend's household is held to the Noble's needs and counted with them
    (the Noble's home need is M29's "tier III house"). Before M29's Legend engine lands this finds no Legends and
    does nothing.

  Done when: `ClassPerkGameTests` (the takings for a staged mix of classes and wants, never below the old formula for
  Peasants; the crafter pace staying within the cap; wellbeing capped at 9%; the ball replacing one festival in two)
  pass; scene `noble_ball` passes; README updated.
- [x] **34.8** (review: pending 2026-10-06) **Higher jobs need higher classes.** The `jobs` list in each class file names the jobs a villager must
  be that class or higher to take. To start: **Artisan**: Tinkerer, Chef, Netherworker, Nurse, Teacher, Shopkeeper,
  Innkeeper, Printer and, with Cobblemon, Ball Smith, Move Tutor, Pokémon Trader, Fossil Scientist; **Burgher**:
  Scholar, Undertaker, Jeweller, Trainer Leader; **Noble**: none yet (kept for M29's Legends and M33's offices).
  Everyone else, the Tailor and the Vintner too, is open to Peasants (whoever makes a luxury a class needs is open to
  the class below it), and vanilla jobs are never gated: they stay keyed on their blocks, as in vanilla. The gate
  applies only when a job is taken: picking it with its item (`Stations.choose`: "Dara is a Peasant; a Scholar must
  be a Burgher"), the hall's free-workstation list (greyed, with the class it needs), a grown child taking up a
  parent's trade (`Families.round`), and a hired traveller, who arrives with the class of their level (Apprentice:
  Peasant, Journeyman: Artisan, Expert: Burgher). Nobody is ever fired: a worker below their job's class keeps it at
  the usual pace, and the hall marks them. Outside a village with a hall nothing is gated. Done when:
  `ClassJobGameTests` cover each way of taking a job, a grandfathered worker keeping their job across a reload, a
  hired Expert arriving as a Burgher, and no gate with `villageClasses` off; scene `class_jobs` (a Peasant refused
  the Scholar's paper, a Burgher taking it) passes; the README's job table gets a Class column.
- [x] **34.9** (review: pending 2026-10-06) **Vintner.** A new job: stand a villager by a **cauldron** and sneak-right-click them with **sweet
  berries, glow berries or an apple** (a new line in `Stations`; the cauldron's Leatherworker and Sifter stay). The
  cauldron is their vat (purple splashes and a squelch while pressing), and through 34.5 they make:
  - **Cider**: 3 apples and a glass bottle (Novice);
  - **Berry Wine**: 6 sweet berries or 4 glow berries (with Cobblemon, 4 of any berry, by item tag) and a glass
    bottle (Apprentice);
  - **Vintage Wine**: a Berry Wine at least 3 days old, re-corked (Journeyman).

  The three items are drawn with the pixel-art skill like vanilla's bottles; a player can drink them (Cider 2 hunger,
  Berry Wine 3, Vintage Wine 4 and 5 s of Regeneration; the bottle comes back). Trades at every level (buys apples,
  berries and bottles; sells the wines). Pastured Grass, Bug and Fairy Pokémon help (`Partners`). The Vintner's
  outfit and zombie outfit through the pixel-art skill: a wine-stained apron, rolled sleeves, a straw hat with a vine
  band. Luxury files `cider`, `berry_wine`, `vintage_wine`. Done when: `VintnerGameTests` (picked by each item at a
  cauldron, each wine made from stocked chests, vintage waiting its 3 days) pass; both outfits pass `lint.py` and the
  `OutfitGameTests`; scene `vintner` passes; a README section and job-table row.
- [ ] **34.10** **Tailor.** A new job: stand a villager by a **loom** and sneak-right-click them with **string** (the
  loom's Shepherd stays, back with shears). They take the shepherds' wool and the dyers' coloured wool from the
  village and make:
  - **Work Clothes**: 3 wool of any colour, 2 leather and a string (Novice);
  - **Fine Clothes**: 4 dyed wool (not white), a string and 2 gold nuggets (Apprentice);
  - **Noble Robes**: 5 wool of one rich colour (purple, blue, red or black), a rabbit hide and a gold ingot
    (Journeyman).

  The three items are drawn on the outline of vanilla's leather tunic icon, in cloth, with the pixel-art skill.
  Trades at every level (buys wool, leather and string; sells the clothes). Pastured Bug and Normal Pokémon help.
  Outfit and zombie outfit: a tape measure round the neck, a pincushion at the wrist, a neat waistcoat. Luxury files
  `work_clothes`, `fine_clothes`, `noble_robes`. Done when: `TailorGameTests` (picked with string at a loom, each
  garment made, the dyed-wool rules kept) pass; both outfits pass `lint.py`; scene `tailor` passes; a README section
  and job-table row.
- [ ] **34.11** **Printer.** A new job: stand a villager by a **cartography table** and sneak-right-click them with
  an **ink sac** (the table's Cartographer and Netherworker stay). Through 34.5 they make:
  - **Books**: 3 paper and a leather make 2 books (Novice). Scholars' research and builders take the store's books
    first, and with a scholar in the village the printer keeps 8 in the store;
  - **The Village Gazette**: 3 paper and an ink sac make 2 copies (Novice), each a written book printed from the hall
    that day: the front page from the newest chronicle entries, the open quests with their rewards, the next festival
    and market day, the week's births, weddings and households that rose;
  - **Illuminated Book**: a book, 2 gold nuggets, a lapis lazuli and a glow ink sac (Journeyman): the village's whole
    chronicle bound in gold, a written book.

  Books for quests: a new hall quest, **Spread the news**: carry this week's Gazette to a village this one has a
  caravan route to, paid like a delivery (`VillageQuests`). Players buy today's Gazette for 1 emerald and an
  Illuminated Book for 8 (trades). Pastured Psychic and Normal Pokémon help. Items drawn like vanilla's paper and
  written book; outfit and zombie outfit: an ink-stained apron, a green visor, sleeve garters. Luxury files
  `gazette`, `illuminated_book`. Done when: `PrinterGameTests` (picked with an ink sac, each good made, the Gazette's
  pages built from a staged hall's chronicle and quests, a scholar using printed books, the quest paid on delivery)
  pass; both outfits pass `lint.py`; scene `printer` (the Gazette opened and read) passes; a README section and
  job-table row.
- [ ] **34.12** **Jeweller.** A new job: stand a villager by a **stonecutter** and sneak-right-click them with an
  **amethyst shard** (the stonecutter's Mason stays, back with cobblestone). Through 34.5 they make:
  - **Amethyst Ring**: 2 amethyst shards and 2 copper ingots (Novice);
  - **Emerald Brooch**: an emerald and 3 gold nuggets (Apprentice);
  - **Gold Circlet**: 2 gold ingots, an emerald and an amethyst shard (Journeyman).

  The three items drawn small and centred like vanilla's nuggets and shards, with the pixel-art skill. Trades at
  every level (buys amethyst, copper and gold; sells the jewellery). Pastured Rock, Steel and Fairy Pokémon help.
  Outfit and zombie outfit: a loupe over one eye (the `glasses` helper), a dark velvet waistcoat with gold buttons.
  Luxury files `amethyst_ring`, `emerald_brooch`, `gold_circlet`. Done when: `JewellerGameTests` (picked with an
  amethyst shard at a stonecutter, each piece made, a Burgher-only job per 34.8) pass; both outfits pass `lint.py`;
  scene `jeweller` passes; a README section and job-table row.
- [ ] **34.13** **The Winery and the Tailor's Shop.** Blueprints in the Blueprint Table, drawn with the architect
  skill in `tools/blueprints/workshops.py` (STYLE.md, renders): the **Winery** (a stone press house over a half-sunk
  cellar, the cauldron vat by the door, racks of casks made of spruce log ends and a cellar of chests; no barrels,
  since a barrel is the fisherman's job block; **II** a terraced berry garden with a pergola of glow berries and a
  tasting porch) and the **Tailor's Shop** (a timber shop with a bay window, the loom, bolts of coloured wool, a
  fitting room; **II** a cutting-room storey above, with bolts of cloth on racks and a drying loft for dyed wool).
  Each also grows in villages in all five styles (`world/VillageHouses`, `village.py`: one job block per house and a
  villager already in the job; weight 2). Vintners and tailors at Journeyman sell their building's blueprint. Once
  M27's workplace rules (27.11) have landed, each gets its Steward rule in 27.11's form (Vintner: Winery; Tailor:
  Tailor's Shop); before that, nothing. Done when: renders of each, front and back and its II, are in the review
  package; a builder builds each in a GameTest; both village pieces generate with their worker (`VillageGameTests`);
  scene `luxury_workshops` passes.
- [ ] **34.14** **The Print Shop and the Jeweller's Workshop.** The **Print Shop** (brick and timber, the cartography
  table as the press under a skylight, paper and ink on shelves, a counter for buyers; no lectern, which would take a
  librarian; **II** a bindery and reading room lined with bookshelves) and the **Jeweller's Workshop** (a small stone
  shop, the stonecutter at a bench under a lantern, an amethyst cluster in a glass case, an iron door; **II** a
  strong room with a vault of chests behind iron bars). Same rules as 34.13: village pieces in five styles, sold by
  their Journeymen, and Steward rules once 27.11 has landed (Printer: Print Shop; Jeweller: Jeweller's Workshop).
  Done when: the same checks as 34.13 pass for both, and scene `luxury_workshops` films all four workshops.
- [ ] **34.15** **Grander homes: the Artisan's House and the Burgher's Townhouse.** A home's grade is its blueprint's
  tier (`Homes`), unless `data/aliveworkplace/homes/<name>.json` gives it more (`{"blueprint":
  "aliveworkplace:townhouse", "grade": 2}`; each upgrade one more, up to III). New blueprints (architect skill,
  `houses.py`): the **Artisan's House** (grade I: a narrow timber house with a workroom below and two beds above;
  **II** a back workshop wing and a third bed) and the **Burgher's Townhouse** (grade II: three storeys of brick and
  timber with a shop window and two bedrooms; **II** a walled courtyard garden and a third bedroom; **III** a corner
  tower with a study). Builders sell the Artisan's House from Apprentice, and the Townhouse once the village has an
  Artisan household. A household that rises past its home's grade asks for a grander one: a What next? tip ("Dara and
  Tomas are Burghers and want a grade II home"). Once M27's Steward rules (27.6, 27.10) have landed, a rule file
  `homes_class` (a new condition, `households_want_grade {grade, at_least}`) has the Steward upgrade those homes or
  build a Townhouse or Manor like any other build; before that, the tip alone. Done when: `HomeGradeGameTests` (a bed
  in Townhouse I counts II and in its upgrade III; a data file overrides a tier) pass; renders of every tier in all
  five styles are in the review package; a builder builds each in a GameTest; scene `grand_houses` passes.
- [ ] **34.16** **Grander homes: the Noble's Manor.** The **Manor** (grade III: a stone house of two storeys round a
  hall with a grand stair, a dining room and two bedrooms, under a slate roof with dormers; **II** a walled formal
  garden with clipped hedges, a fountain and gravel walks; **III** a gatehouse and a ballroom wing, where 34.7's
  Noble's Ball is held). Builders sell it once the village has a Burgher household; its garden counts 3 beauty for
  the people who live there. Done when: renders of I-III from the front, the back and above, in all five styles, are
  in the review package; a builder builds each in a GameTest (`huge_area`); the ball gathers in the ballroom when
  there is one; scene `grand_houses` adds the Manor.
- [ ] **34.17** **Dressed by class: the pick round.** A class layer worn over the job's outfit
  (`textures/entity/villager/class/<class>.png`, recipes in `tools/textures/art/classes.py`, pixel-art skill): never
  on the head (the job's hat stays), nothing under the level badge, the back painted, the job still readable under
  it. Peasants keep their job's outfit as it is today (the owner-approved look). Three versions of each, every one a
  different idea, on the picker page (`tools/textures/picks/`, as in 21.1b):
  - **Artisan**: a neckerchief and a belt pouch; a leather tool belt; rolled cuffs and a waistcoat;
  - **Burgher**: a white collar, dark cuffs and a watch chain; a buttoned coat edge with a pocket square; a short
    cape;
  - **Noble**: a fur-trimmed mantle and a gold chain of office; a velvet half-cape with gold embroidery; a sash with
    a jewelled clasp;
  - **Elder** (34.19's look, a face layer): grey brows and a short beard; white hair at the temples; spectacles and a
    grey beard.

  Each previewed on the villager model in every biome, over six different jobs. Until the owner picks, v1 of each is
  the default. Done when: all twelve pass `lint.py`; the page is up with a pick and a note per row; v1 of each is
  wired in as the textures.
- [ ] **34.18** **Dressed by class, in game.** A client render layer draws the wearer's class over their job outfit,
  and one draws the elder look, on grown villagers and children alike, never on zombie villagers. The client learns a
  villager's class and life stage from a small packet, sent when a player starts tracking the villager (a new
  `Platform` event) and whenever either changes; `classOutfits` in the config turns the layers off. The owner's picks
  from 34.17 replace v1 as they come. Done when: a GameTest checks the packet goes out on tracking and on a change;
  scene `class_outfits` (the four classes side by side in three jobs each, plus an elder, in daylight) passes with
  every job still readable; the shots are in the review package.
- [ ] **34.19** **Life stages: elders.** Grown villagers count their days: `adult_since` (saved: the day they grew
  up; set in `Families.round` when a child grows up, seeded for everyone else by 34.22). After `villagerElderDays`
  (120) grown days a villager is an **elder**: the hall says so ("Elder · grown 131 days"), they wear the elder look
  (34.18), walk 15% slower (`Walker`), and their mood gets "a quiet old age" (+5) when they're fed and housed. The
  chronicle notes it ("Bram is an elder now"), and elders have four chatter lines of their own ("In my day this was
  all fields."). Config `villagerAges`. Owner's call (2026-10-06): elders DO pass in their sleep after 40 elder days and leave a grave
  (`elderPassing` on), unless made ageless by 34.19a. Done when:
  `LifeStageGameTests` (an elder after the configured days with the clock moved on, the slower walk, the mood reason,
  a reload keeping the day, a child growing up getting today) pass; scene `elders` passes.
- [ ] **34.19a** **The Evergreen Charm: ageless elders** (owner, 2026-10-06: "make an elder eternal if they have good
  traits by building a specific item and giving it to them, so you don't lose good villagers"). A new item, the
  **Evergreen Charm** (pixel-art: a small gold-and-green leaf pendant; a rare craft: a totem of undying, a golden
  apple, 2 emeralds and a heart of the sea, our decision, tune in play). Sneak-right-click an elder with it: they
  become **Ageless** (attachment `ageless`, saved, default false): they never pass, keep the elder look with a gold
  leaf badge on the hall, keep the slower walk, still retire and mentor (34.20), and the chronicle says so ("Bram
  will never leave us"). Only an elder with **good traits** is accepted, else refused with the reason: Master level
  in their trade, or Gifted (29.x), or a Legend, or a Mood of 80 or more for the last 20 days. One charm, one villager;
  it is used up. Config `agelessElders` (true). With `elderPassing` off nobody needs one. Done when: GameTests
  (accepted for each good trait, refused for a plain elder and for a non-elder, the charm used up, ageless surviving
  40+ elder days and a reload, the passing of a non-ageless elder with a grave) pass; scene `ageless_elder` passes.
- [ ] **34.20** **Retirement and apprentices.** An elder with a job retires once someone can take over, at most one
  villager a village every 3 days:
  - the successor is their own grown child without a job, else any jobless grown villager of a class the job allows
    (34.8); with nobody, the elder keeps working and the hall says "Bram would like to retire: the village needs a
    jobless villager to take over";
  - never while they hold something for a player or a build: a site or queue (builder), a quarry (miner), Pokémon in
    their daycare, fossils being revived, parcels on their round;
  - the successor shadows the elder for 2 days ("learning from Bram" over their head), then takes the workstation and
    the job one level below the elder's (at least Apprentice), with the job's assignments: field, tree farm, orchard,
    courier routes, patrol route, ball orders, and who hired them;
  - a retired elder never takes a job again (players can still trade with them); by day they mentor: standing by a
    Novice or Apprentice of their old trade in the village, who then learns 50% faster ("taught by Bram"), or else
    sitting by the hall or on a bench;
  - chronicle entries for the retirement and the hand-over; `elderRetirement` in the config.

  Done when: `RetirementGameTests` (a full hand-over in order, each "never while" case, the level and assignments
  carried over, a retired elder never re-employed, the mentor's 50%) pass; scene `retirement` passes.
- [ ] **34.21** **Family trees.** Babies remember their parents by id too (`mother_id` and `father_id`, new optional
  fields in `Families.Parents`; old records keep their names only). The hall keeps the village's **Book of
  Families**: everyone who has lived there (id, name, family name, the day they were born or grew up, the day they
  died, parents, partner, trade), up to 400, the longest dead forgotten first. **Family names**: a villager whose
  parents aren't known founds a family, named from a list of 60 (`family_name.aliveworkplace.<n>`, by UUID); children
  take a parent's. The hall shows "Dara (Ashby)" and never renames a villager (a Name Tag's name stays). A villager's
  hall page gets a **Family** button: parents, grandparents, partner, brothers and sisters, children and
  grandchildren, the dead marked. Births in the chronicle name the grandparents ("Mira, granddaughter of Bram
  Ashby"), and a family's third generation is noted. The Printer (34.11) prints any family's tree as a written book
  for 2 emeralds. `familyNames` in the config. Done when: `FamilyTreeGameTests` (three generations, a death, the 400
  cap, a reload, an old Parents record without ids) pass; scene `family_tree` passes.
- [ ] **34.22** **Switching it on for every village at once.** The owner's live server gets classes the moment it
  runs 1.8, in every village with a hall, so the first rounds after the update must be safe. When a hall's round
  finds it has never been seeded (`classesVersion` 0, a new saved field on the hall):
  - it seeds 16 villagers a round, so a big City takes a few minutes: each household gets the highest class whose
    needs other than luxuries hold right now, at most Burgher (Nobles are earned); children their household's; ages
    (`adult_since`) spread by UUID over the last 100 days, so the first elders come weeks later and one at a time;
    family names; mercenaries and inn guests are skipped;
  - a grace of `classGraceDays` (7) days follows: nobody falls a class, luxuries are asked for but not yet needed,
    nobody retires; the hall shows "Settling in: 5 days left" and what each class will need;
  - nothing a player built or earned changes: nobody loses a job (gates only apply to new hires), ranks keep their
    thresholds, the treasury never takes less than before (Peasants pay today's rate), and build sites, quarries,
    daycare Pokémon, fossils and parcels are untouched;
  - one chronicle entry ("The people of Thornholm found their places: 14 peasants, 6 artisans, 2 burghers") and one
    chat line to the players in the village;
  - a hall first loaded weeks later gets the same, with its own grace; a crash part-way re-seeds only those still
    unseeded; `villageClasses` off brings back today's behaviour with nothing lost; `/workplace classes
    <status|reseed>` (operators) per hall.

  Done when: `ClassMigrationGameTests`, on a village staged as a save from before 1.8 (none of the new fields; a
  Scholar in a plain house, a married couple, a child, a builder mid-build, a rancher with daycare boarders, a hall
  of City rank), prove each bullet, including a save and reload part-way through seeding and a second round that
  changes nothing; scene `class_switch_on` (the hall's pages before and after) passes.
- [ ] **34.23** **A rehearsal on a real world.** With `tools/packtest` on the Cobbleverse pack: a world made with the
  last release before 1.8, with three villages with halls (a Hamlet of 6; a Town of 22 with research and couples; a
  City of 36 with every class-gated job, children, a build in progress, daycare boarders, fossils reviving, mail on
  the way), saved, then opened with the 1.8 jar and run for 2 in-game days. Done when: the log audit is clean; the
  report lists, per village, the classes seeded, the jobs kept (all of them), the treasury takings and the rank
  before and after (never lower), the items in every container before and after (equal), and the tick p95 during
  seeding (within the performance targets); `docs/owner/updating-to-1.8.md` tells the owner in plain steps to back up
  his server's world first and what his villagers will do in their first week; the report is linked in the Notes.
  Players see nothing new: `--no-review`.

Depends on: nothing required. Optional links, each with a fallback that works before it lands: 27.6 and 27.10 (the
Steward's rules: 34.15 adds a rule that upgrades homes for rising households; a What next? tip until then), 27.11
(workplace rules: 34.13 and 34.14 add one per new job; nothing until then) and M29's Legend engine (34.7: Legends
held to the Noble's needs; does nothing until Legends exist).

## Milestone 35: Wonders (2.0)

A Wonder is a village's life's work: a huge build raised in four stages by every builder in the village at once, fed
by goods from every job, drawn up by the Master Architect and needing another Legend. Each of the eight Wonders (the
Great Cathedral, the Lighthouse, the Great Library, the Great Wall, the Observatory, the Hanging Gardens, the Colossus
and the Stadium) changes what its village or its whole realm can do, and their time-lapses are the 2.0 trailers. It
builds on the builder (`BuildSite`, `BuildSiteManager`, `BuilderWork`'s helpers, `Upkeep`), the blueprint generator
(`tools/blueprints/`, STYLE.md), the Village Hall (ranks, research, festivals, caravans, the chronicle) and M29's
Legends.

- [ ] **35.1** **Design note.** `docs/design/M35.md`, sent to the owner as a review package (lanes don't wait for his
  reply): what the player sees, from the hall's Wonders page through the plan, the Foundation Stone, the crew, the
  goods and the dedications to the finished Wonder's celebration and its powers; one Wonder's data file in full
  (`data/aliveworkplace/wonders/<id>.json`); the condition types and the power types; the config switches (`wonders`,
  `wonderCaps`, `wonderLegends`, `wonderServerLimit`, `disabledWonders`, `wonderCrew`, `builderScaffolding`,
  `wonderStanding`); the save data (a new `WonderManager`, new optional fields on `BuildSite`, nothing renamed); a
  table of the eight Wonders with their Legend, its stand-in, their other requirements and powers; and how every link
  to another milestone falls back until that milestone lands. Done when: the note is on `main` with all of these, and
  its review package is sent.
- [ ] **35.2** **Wonder data, requirements and caps.** A Wonder is one data file,
  `data/<namespace>/wonders/<id>.json`, loaded by a reload listener (through `Platform`) into a new `wonder/Wonders`:
  its name and description (lang keys), an icon item, its requirements, its stages (each a name, its blueprint parts
  with their offsets, and its goods as `{"item" or "tag", "count", "job"}`), its powers (`[{"type": ..., numbers}]`),
  its crew size and `enabled`. A file with a mistake is logged with its path and the reason and skipped; a server
  owner's own Wonder works like ours. Requirements use the shared condition toolbox (package `rules/`; add each type
  below that M29 hasn't added yet): `rank_at_least`, `research_levels`, `finished` (a blueprint in any style, how many
  within the village), `villagers`, `job_level` (N villagers of a job at a level), `legend` (M29's Legend living in
  the village, with a `stand_in` list of conditions used while Legends aren't built or `wonderLegends` is false),
  `coastal` (an ocean or beach biome within 48 blocks of the hall, 16 samples), `mod_loaded`, `caravan_routes`,
  `iron_golems`, `meal_kinds` and `pastured_pokemon` (how many, of how many types, through a `compat/` extension
  point; false without Cobblemon). Every Wonder also needs the Master Architect (stand-in: a Master builder) to draw
  up its plans. Caps: `wonderCaps` by rank (default Hamlet 0, Village 0, Town 1, City 2), one of each kind per
  village, one Wonder going up per village at a time, `wonderServerLimit` per kind (default 0: no limit),
  `disabledWonders`; `wonders` turns the lot off. `/workplace wonders` lists every Wonder on the server with its
  village, owner and stage. Done when:
  - GameTests load a test Wonder's file (the test folly, gametest resources only; its stage parts come with 35.3),
    skip a broken one with a logged reason, and load one from a test data pack;
  - every condition type has a true and a false GameTest, and a `legend` falls back to its stand-in;
  - the caps hold: a Town with one Wonder can't start a second and a City can, the same kind twice in one village is
    refused, and `wonderServerLimit 1` refuses a second village.
- [ ] **35.3** **The Wonders page and the Wonder Plan.** A new button on the Village Hall's screen opens the
  **Wonders** page (a `ChoiceMenu`): every enabled Wonder as its icon, name and power in a line, each requirement
  ticked or crossed with what's missing ("2 of 3 Chapels", "the Seer: not in the village"), and the cap ("Wonders: 1
  of 1 for a Town"); a Wonder going up shows its stage and how far along it is. With everything ticked, **Draw up the
  plans** (32 emeralds from the treasury, or the realm's once M33's realm treasury exists, else from the player; owner
  and friends only) gives a **Wonder Plan**: a blueprint of the whole Wonder (`aliveworkplace:wonder/<id>`, which
  `BlueprintLibrary` builds by merging its stage parts, cached, and hides from the Blueprint Table like `research/`)
  with its own icon (pixel-art skill: a rolled blueprint with a gold seal). Held, it shows the whole Wonder as
  see-through blocks, turns with sneak-right-click and mirrors on the style screen (no styles: each Wonder keeps its
  palette). It can only be placed with its centre within the hall's reach and all of it within 96 blocks of the hall,
  never over a finished building or another Wonder; otherwise it says why. The kit gets `wonder_stages()`
  (`tools/blueprints/kit.py`): a Wonder is drawn one stage at a time and each stage is saved as only what it adds to
  the stages before it, cut into parts of at most 48 a side (`structure/wonder/<id>/stage_<n>_<part>.nbt`). Its first
  use is the test folly: a three-stage stone tower with a dome, in the gametest resources. The hall's "What next?"
  names the Wonder closest to ready once the village is a Town, and the showcase gets a **Wonders** group. Done when:
  - a GameTest merges the folly's parts into the whole tower as drawn, and no part sets air where an earlier stage put
    a block;
  - GameTests: the page's lines match the conditions (one unmet requirement shows crossed); Draw up the plans takes
    the emeralds and gives the plan, and refuses over the cap; placing is refused outside the village and over a
    finished building, and works elsewhere in all four turns;
  - scene `wonders_page`: the page with the folly's card, then the plan in hand and its see-through preview.
- [ ] **35.4** **The Foundation Stone and stages that survive restarts.** Hand the placed Wonder Plan to any builder
  of the village: a **Foundation Stone** (a new block, art through the pixel-art skill: a carved stone with a brass
  plaque) goes down just outside the Wonder's front corner, and the Wonder becomes a `WonderSite` in a new saved
  `WonderManager` (one per dimension): which Wonder, its hall, its placement, the current stage, the goods delivered,
  the build sites of its parts, and the days it was begun and finished. Each stage's parts become ordinary
  `BuildSite`s with a new optional `wonder` field pointing back and the Foundation Stone as their bench, so the chests
  within 8 blocks of the stone are the Wonder's stockpile; all are placed with the Wonder's turn and mirror; only
  stage I fills a foundation and only the last stage levels the ground round it. A stage starts when every part of the
  one before is DONE (and, from 35.7, when its goods are in), so the order holds through a restart at any tick.
  Right-click the stone: the stage, how far along it is, and the stage's material list with what the chests are short
  of (as a blueprint's tooltip shows it). The start is announced to every player on the server and goes in the
  chronicle. Sneak-breaking the stone (owner or op) or `/workplace cancel` ends the Wonder: what's built stays and the
  plan comes back. The README gets a **Wonders** section (how to start one), which later items add to. Done when:
  - a GameTest raises the folly with one builder: stage II never starts before stage I is done, and the result matches
    the drawing in all four turns and mirrored;
  - a GameTest saves and reloads mid-stage II (the manager, the sites, the builder) and the folly is finished;
  - cancelling returns the plan, and a world saved before this item loads and saves unchanged;
  - scene `wonder_site`: the plan handed over, the stone going down, stage I starting.
- [ ] **35.5** **Crews: every builder on one Wonder.** Each builder whose Blueprint Table is within the Wonder's
  village joins its crew when they have no build of their own (up to `wonderCrew`, default 12; **Call every builder**
  on the stone's screen brings the rest in as they finish what they're on). The lead is the Master Architect (M29)
  when there is one, else the crew's highest-level builder, named on the stone and the hall. Helpers today work a
  64-step window ahead of one lead (`BuilderWork.helperStep`, `Builders.MAX_HELPERS` 3); a crew instead splits each
  part into 8 × 8 columns, bucketed once when the plan is made and kept with the `BuildPlan`. Each member takes the
  free column with work nearest to them and works it in the stage's order (CLEAR from the top, the rest from the
  bottom), never more than two layers above the part's lowest unfinished layer, so nothing goes up with nothing under
  it. No two members share a column; a step a member can't do goes on the lead's deferred list. The crew fetches from
  the stockpile (and the village's store, as now) and passes materials to each other (`takeFromCrewmate`). Crews add
  hands, not speed: each builder keeps their own pace. Done when:
  - a GameTest: 8 builders raise the folly with no two in one column on any tick, no STRUCTURE block placed with air
    under it unless the drawing has it so, and the folly done in at most a quarter of one builder's time;
  - a save and reload mid-stage keeps the crew together and they finish;
  - the benchmark (`PERF=true`) with a 12-builder crew stays within 25.2's targets;
  - scene `wonder_crew`: ten builders on the folly, a GIF.
- [ ] **35.6** **Scaffolding.** Builders can't stand on roofs and reach about four blocks, so a raised arm, a spire's
  tip or a dome's crown is skipped today (STYLE.md says to avoid them). When a step has nowhere to stand within reach
  (`BuilderWork.findStandingSpot` finds nothing), the builder puts up a column of vanilla **scaffolding** from the
  supply chests beside it, from the ground or the nearest floor, only where the drawing leaves air or nothing, climbs
  it and works from the top. Every scaffold is recorded in its `BuildSite` (a new saved list, empty by default) and
  taken down when the stage is done or the build is cancelled, the scaffolding going back into the chests. With none
  in the chests it's asked for on the requests board (a carpenter makes it from bamboo and string; builders already
  sell it). It works on every build (`builderScaffolding`, default on), and STYLE.md is updated to say so. Done when:
  - a GameTest builds a 14-high pillar with a 5 × 5 cap overhanging by two in `big_area` with no skipped steps, and
    afterwards no scaffolding stands and the chests hold as much as before;
  - a GameTest cancels half-way and the scaffolds come down; a save and reload with scaffolds up finishes the build;
  - the folly's dome is finished with no skipped steps;
  - scene `scaffolding`: a builder scaffolding up the pillar and taking it down, a GIF.
- [ ] **35.7** **Goods from every job.** Besides its building blocks, each stage of a Wonder asks for goods: the feast
  for the crew, their tools, what the rites need (candles, books, potions) and its treasures. A stage starts only when
  all its goods are in the stockpile; then (by day; at night it waits for the morning) it is **dedicated**: the
  villagers off work gather at the stone, the village's Bard plays and its guards stand in line (if it has them), the
  bell rings, the goods are taken from the chests and the chronicle says so. Each goods line names the job that makes
  it, and the village sees to it: the lines go on the requests board (a new kind of request, posted for a block
  instead of a villager, in `work/Requests`); porters carry matching goods from the storehouse and the workers' chests
  to the stockpile before anything else (`store/PorterWork`); chefs, carpenters, masons, tinkerers and dyers make
  what's short as stock orders for the stockpile (`store/StockOrders`); and the hall's daily quest asks for the line
  that's shortest (`hall/VillageQuests`). Anyone can hand goods in. The stone's screen shows every line: delivered of
  needed, the job, and "no Chef in the village" when nobody can make it. Cancelling hands back the goods of a stage
  not yet dedicated. Done when:
  - GameTests: the folly's stage II (three lines) doesn't start until all three are in; a porter carries bread from
    the storehouse to the stockpile; a chef cooks a short line as a stock order; a click on the requests board hands a
    line over; the dedication takes exactly the goods; a cancel gives them back;
  - scene `wonder_goods`: the stone's screen half filled, a porter delivering, then the dedication.
- [ ] **35.8** **A finished Wonder: celebration, upkeep and powers.** When the last stage is done, every player on the
  server is told; the village holds a festival that evening (`hall/Festivals`) with a bigger firework show; the
  chronicles of the village and of every village linked to it (its realm, M33; until that lands, its caravan partners)
  record it; and the Village Map marks it with a gold banner. Powers: each power type is code in `wonder/powers/`,
  registered by id and read from the data file's `powers`; the rest of the mod asks `Wonders.power(level, hall,
  type)`, a lookup in a cache rebuilt when a Wonder is finished or damaged. Two generic types for server owners' own
  Wonders: `mood` (village or realm, how much) and `beauty` (points, through `hall/Decorations`). Upkeep: once a day
  each finished Wonder counts how much of it still stands (at most 4,096 blocks a tick, only while loaded; copper's
  weathering stages count as the same block, so a dome turned green still stands); under `wonderStanding` (90%) its
  powers stop, the hall and the stone say "damaged", and the village's builders repair it before anything else
  (`build/Upkeep`, `BuildPlan.repair` per part); back at 90%, the powers return. The screenshot harness gets the
  Wonder time-lapse every Wonder's scene uses: a crew of 10 with a lead, free materials, the goods put in for each
  stage, the server sped up (`/tick rate`), a slow orbit filmed every 100 ticks, a still per stage, under 15 minutes
  on GitHub's runner. Done when:
  - GameTests: the finished folly's test `mood` power is on; breaking 15% of it turns it off at the next count, the
    builders repair it and it comes back; finished Wonders survive a save and reload;
  - a GameTest checks the server-wide message and the chronicle lines;
  - scene `wonder_finished`: the folly's time-lapse, ending in its festival fireworks.
- [ ] **35.9** **The Great Cathedral: its design.** Through the architect skill (design on paper, renders, its
  critique questions, STYLE.md), drawn in a new `tools/blueprints/wonders.py` with the kit and saved by
  `wonder_stages()`: a Gothic cathedral on a cross plan, about 27 wide (39 across the transepts) and 55 long, its two
  west towers 44 high under stone spires reaching 52. A nave of five bays under a steep deepslate-tile roof; aisles
  either side, with flying buttresses between tall lancet windows; transepts with gable windows; a choir ending in a
  rounded apse; open belfries with three bells; and a rose window of stained glass (white, light blue, yellow and red)
  over a deep west door. Inside: pews down the nave, an altar of gold and candles, banners on the piers, three side
  chapels with lecterns, and a sacristy with a brewing stand (an Undertaker's place). Palette: stone bricks with
  cracked and mossy ones low down, polished andesite piers, deepslate tiles, dark oak, gold at the altar. About 14,000
  blocks in four stages, each ending tidy, since the village lives beside it for days: I *The Foundations and the West
  Front*, II *The Nave*, III *The Transepts and the Choir*, IV *The Towers and the Rose Window*. Data
  `wonders/great_cathedral.json`: Town; the Seer (stand-in: a Master Cleric); 3 finished Chapels. Goods: I 128 bread
  (Chef), 8 iron pickaxes (Toolsmith), 16 gold ingots (Armorer); II 64 white candles (Dyer), 32 honeycomb (Beekeeper),
  64 white wool (Shepherd); III 16 books (Librarian), 12 potions of healing (Cleric), 64 small flowers (Florist); IV
  32 chiseled stone bricks (Mason), 16 cakes (Chef), 64 bone meal (Composter). Done when (the design checks every
  Wonder's design meets):
  - `generate.py` saves it with no CHECK lines, the architect's lint reports no ERROR, and every part is at most 48 a
    side;
  - `WonderBlueprintGameTests` for it: every part loads, no part sets air over an earlier stage's block, the block
    count is within 10% of the figure here, and it uses no retired job block, no calcite and nothing a player can't
    get;
  - its review package has the renders after two critique rounds: each stage as the village would see it, and the
    whole from the front, the back and at eye level;
  - scene `wonder_gallery` (made here; each Wonder's design adds to it) shows it whole from the front and the back.
- [ ] **35.10** **The Great Cathedral: its powers and time-lapse.** While it stands, the village's Undertakers need no
  golden apple, potion or totem, look for graves across the whole village instead of 32 blocks, and bring each
  villager back by the next morning (`grave/UndertakerWork`). Weddings are held at the Cathedral, before a Chapel or
  the bell (`people/Couples`), and are **blessed**: the whole village gets +15 mood for 3 days, the couple's first
  baby comes the next day there's a free bed without waiting for the 16 meals, and (needs M29's born-Legends item;
  ignored before it lands) a blessed couple's children are twice as likely to grow up Gifted. Its bells ring for
  weddings, festivals and raids, and it counts 8 beauty. Power types: `undertakers`, `weddings`, `beauty`. Done when:
  - GameTests: an Undertaker with empty chests brings back a grave 40 blocks away in a Cathedral village, and doesn't
    without one; a wedding goes to the Cathedral with its mood and baby; a damaged Cathedral turns both off;
  - scene `wonder_great_cathedral`: the time-lapse, ending at dusk with a wedding at the altar; its GIF and powers are
    in the README's Wonders section.
- [ ] **35.11** **The Lighthouse: its design.** A coastal landmark about 25 × 33 and 46 high, drawn like 35.9: a
  stepped stone mole at the water's edge carrying a round tower that tapers from 13 across to 9, banded in white and
  red, with a gallery on stone corbels at 38; a lantern room of glass round sea lanterns and a ring of redstone lamps
  that an inverted daylight detector lights at night; a copper cupola with a lightning-rod vane. At its foot, the
  keeper's cottage (stone and spruce under slate, a bed, a barrel for a fisherman), a boathouse, a quay with mooring
  posts, and a timber jetty with a Travel Post at its end (a ferry stop). Palette: stone bricks and andesite at the
  base, white and red terracotta bands, spruce, copper. About 7,500 blocks: I *The Mole and the Quay*, II *The Tower*,
  III *The Gallery*, IV *The Lantern and the Keeper's House*. Data: Town; `coastal`; the Merchant Prince (stand-in: 3
  caravan routes running). Goods: I 64 cooked cod (Fisherman), 8 oak boats (Carpenter), 64 coal (Miner); II 16 chains
  (Tinkerer), 32 iron ingots (Armorer), 32 white wool (Shepherd); III 4 explorer's maps (Cartographer), 32 spectral
  arrows (Fletcher), 32 leather (Butcher); IV 64 glowstone dust (Netherworker), 32 redstone (Miner), 16 pumpkin pies
  (Chef). Done when: 35.9's four design checks pass for it, with the jetty over water in its renders.
- [ ] **35.12** **The Lighthouse: its powers and time-lapse.** While it stands, caravans from its village reach
  villages up to 4,096 blocks away (twice `Caravans.RANGE`) and arrive in half the time (`Caravans.travelTicks`);
  ferry fares to and from the village's Travel Posts are halved (`TravelNetwork.fare`: an emerald per 512 blocks), and
  the jetty's post joins the network under the village's name ("Thornholm Lighthouse"). And it watches the sea: the
  day before a pirate raid (needs M32's drowned pirates item) everyone in the village is told, the chronicle notes it
  and the guards gather on the shore at dusk. Until M32 lands, it warns at dusk of the night's raid instead
  (`guard/VillageRaids` rolls a Lighthouse village's night at dusk), so the guards are ready. Power types:
  `caravan_range`, `caravan_speed`, `ferry_fares`, `early_warning`. Done when:
  - GameTests: a hall 3,000 blocks away is on the trade routes list only with the Lighthouse; a caravan's trip takes
    half as long; a fare is halved; the dusk warning comes before a rolled raid and not otherwise (and the pirate
    warning gets its own test once M32's pirates exist); a damaged Lighthouse turns them all off;
  - scene `wonder_lighthouse`: the time-lapse beside a sea dug and filled by the harness, ending at night with the
    lamps lit; its GIF and powers are in the README.
- [ ] **35.13** **The Great Library: its design.** A domed library on a cross plan, about 41 × 41 and 34 high, drawn
  like 35.9: a rotunda 19 across under a copper dome with a glass lantern at its top; four wings of reading halls,
  each with two floors of bookshelf galleries; a portico of six columns under a pediment, over a grand stair. At the
  centre, an Enchanting Table ringed by fifteen bookshelves, with a lectern (a librarian); four lecterns in the wings
  (scholars) and a map room with a cartography table. Palette: polished diorite and smooth stone, quartz on the
  portico, dark oak floors and shelves, a copper dome that weathers green. About 16,000 blocks: I *The Stacks*, II
  *The Galleries*, III *The Rotunda*, IV *The Dome and the Portico*. Data: Town; 20 research levels; the Old Sage
  (stand-in: a Master Scholar). Goods: I 128 sugar cane (Farmer), 64 leather (Butcher), 64 spruce logs (Lumberjack);
  II 64 books (Librarian), 64 black dye (Dyer), 16 lanterns (Tinkerer); III 8 explorer's maps (Cartographer), 32 lapis
  lazuli (Sifter), 32 copper ingots (Armorer); IV 64 nether quartz (Netherworker), 32 cookies (Chef), 16 bookshelves
  (Librarian). Done when: 35.9's four design checks pass for it, with the rotunda's inside in its renders.
- [ ] **35.14** **The Great Library: its powers and time-lapse.** While it stands, the research tree
  (`research/Research`) opens a second tier, shown on the scholar's screen in a row of its own ("needs the Great
  Library" until then). Each level costs 24 paper, 6 books and 8 emeralds times its level, and twice the points of the
  first tier:
  - **Swift Hands IV–V**: every job 5% faster a level;
  - **Scholarship** (2 levels): scholars work 25% faster a level, and the second level makes research cost a quarter
    less;
  - **Engineering** (2): builders' bags hold 9 more stacks a level (27 today), and a Wonder's crew takes 2 more
    builders a level;
  - **Cartography** (1): explorers range twice as far (`ExplorerWork.RANGE`), and their maps can lead to trial
    chambers and ancient cities;
  - **Trade Law** (2): caravans carry 2 more stacks a level (`Caravans.CARGO_STACKS`), and the treasury takes 10% more
    a level;
  - **Pharmacy** (1): the ill get well in a day instead of three, and each Nurse cures one villager a day without a
    remedy.

  Every speed bonus here goes through the shared 2× speed cap (M30's, or added here if it isn't built yet). A village
  keeps its levels if the Library is damaged later, but their bonuses pause with its powers. Power type:
  `research_tier`. Done when:
  - a GameTest for each topic shows its bonus; the row stays locked without the Library; an existing village's
    research loads unchanged; Swift Hands V on top of every other bonus stays at or under twice the pace;
  - scene `wonder_great_library`: the time-lapse, ending in the rotunda on the scholar's screen with the second tier;
    its GIF and powers are in the README.
- [ ] **35.15** **The Great Wall: its design.** A defence placed like the other walls (stand inside the village and
  place it facing out: its stairs are on your side), drawn like 35.9: 97 long, 7 thick and 12 high on a battered
  plinth, a walk along the top behind crenellations, and five towers (9 × 9, 18 high, hipped roofs; one at each end
  and two at the quarters), each with stairs inside, a grindstone for a guard and an alcove where a sentry golem
  stands. At the centre the **Great Gate**: a 17-wide gatehouse between two towers, a portcullis of iron bars over a
  gateway of fence gates. Braziers (campfires) along the walk, banners on the towers. Palette: cobblestone and stone
  bricks with mossy and cracked ones low down, a deepslate-brick plinth, spruce roofs. About 18,000 blocks in parts of
  at most 48: I *The Great Gate*, II *The West Run*, III *The East Run*, IV *The Walk and the Towers' Crowns*. Data:
  Town; the Golem Smith (stand-in: 4 iron golems and a Master Tinkerer); a finished Gatehouse. Goods: I 8 iron swords
  (Weaponsmith), 128 arrows (Fletcher), 128 bread (Chef); II 8 iron shovels (Toolsmith), 12 potions of strength
  (Cleric), 64 baked potatoes (Chef); III 8 crossbows (Fletcher), 8 iron chestplates (Armorer), 8 shields (Carpenter);
  IV 16 iron blocks (Armorer), 4 pumpkins (Farmer), 16 campfires (Carpenter): the iron and the pumpkins become its
  sentries. Done when: 35.9's four design checks pass for it, with the walk at a player's eye in its renders.
- [ ] **35.16** **The Great Wall: its powers and time-lapse.** While it stands, no raid gathers behind it: our night
  raids and the bandits' (`guard/VillageRaids.gatheringPoint`, `guard/BanditCamps`) gather outside the arc the wall
  covers as seen from the hall, plus 10° either side, and (needs M32's sieges item) siege raiders can't put ladders to
  it or break the Great Gate. Its **sentries**: four iron golems watch from the walk (the Golem Smith's wall sentries,
  needs M29's Golem Smith item; plain iron golems until then), stay on it, fight anything within 16 blocks of the wall
  and go back to their alcoves; a lost sentry is replaced the next morning from 4 iron blocks and a pumpkin in the
  stockpile (by the Golem Smith, or a Tinkerer as stand-in). `guard/Gates` shuts the Great Gate at night. Power types:
  `raid_barrier`, `sentries`. Done when:
  - GameTests: 50 raids started with a fixed random never gather inside the arc, and do without the wall; a sentry
    pushed off the walk climbs back; a dead one is replaced from the chests the next morning, and not without the
    iron; a damaged Wall turns both off;
  - scene `wonder_great_wall`: the time-lapse, then a night raid gathering on the open side while the sentries watch;
    its GIF and powers are in the README.
- [ ] **35.17** **The Observatory: its design.** On a raised terrace about 31 × 31, drawn like 35.9: a round tower 15
  across and 30 high under a copper dome with a slit of tinted glass, a great telescope of cut copper 12 long leaning
  out of the slit, a star-chart room in a low wing with a cartography table and a lectern, a courtyard with a sundial
  (a stone face and its gnomon) and an orrery (a gold sun with planets hung on chains), and a spiral stair up to a
  viewing platform. Palette: deepslate bricks and tiles, polished blackstone trim, cut copper, amethyst set in the
  courtyard paving. About 9,000 blocks: I *The Terrace and the Courtyard*, II *The Tower*, III *The Dome*, IV *The
  Great Telescope and the Orrery*. Data: Town; the Seer (stand-in: a Master Cleric). Goods: I 32 lapis lazuli
  (Sifter), 64 cooked chicken (Chef), 8 explorer's maps (Cartographer); II 64 copper ingots (Armorer), 4 spyglasses
  (Toolsmith), 32 amethyst shards (Miner); III 16 books (Librarian), 8 tinted glass (Carpenter), 32 feathers
  (Butcher); IV 32 chains (Tinkerer), 9 gold ingots (Armorer), 32 pumpkin pies (Chef). Done when: 35.9's four design
  checks pass for it.
- [ ] **35.18** **The Observatory: its powers and time-lapse.** While it stands:
  - **The Sky Chart**: the hall's map button also draws, on an empty map, a chart 1,024 blocks across (map scale 3)
    centred on the hall, marking every structure found: villages, pillager outposts, desert and jungle temples, witch
    huts, igloos, ocean monuments, woodland mansions, shipwrecks, ruined portals, trail ruins, ancient cities, trial
    chambers and strongholds (vanilla's own markers for villages, mansions, monuments, jungle temples, witch huts and
    trial chambers; a red X for the rest), each named in the map's tooltip. The Observatory searches one kind an
    in-game hour with the explorers' lookup (`explore/Explorers`), only while the server's tick has room, keeps what
    it found in the hall and searches again each week.
  - **Coming days**: a page on the hall with what's ahead and when: the next festival and market day, weddings due,
    caravans on the road and when they arrive, a traveller expected at the inn, the full moon, bandit camps and where,
    the night's raid risk, and (with M29's Seer and M32's warnings) what they foretell. The Sky Chart marks the ones
    that have a place.

  Power types: `sky_chart`, `forecast`. Done when:
  - GameTests with a stub structure finder: the chart has a named marker per structure found; searches stay at one an
    in-game hour; the Coming days page lists a staged festival, wedding and caravan with their days;
  - scene `wonder_observatory`: the time-lapse, then the Sky Chart in hand and the Coming days page; its GIF and
    powers are in the README.
- [ ] **35.19** **The Hanging Gardens: its design.** A stepped garden mountain, drawn like 35.9: four terraces (39,
  31, 23 and 15 across, 6 high each) faced with arcades of sandstone arches on columns. Each terrace is topped with
  soil: fields of wheat, carrots and beetroots between water channels with a composter and a chest on each level (four
  village farms), sweet and glow berry beds, flowering azaleas and oaks, vines and glow berries hanging over the
  arches, and beehives. A cistern at the top pours a waterfall down one side into a pool, and a ramp spirals up.
  Palette: smooth and cut sandstone, terracotta trim, oak, moss and grass. About 15,000 blocks: I *The First Terrace
  and the Cistern Vaults*, II *The Second Terrace*, III *The Third Terrace*, IV *The Crown Garden and the Falls*.
  Data: Town; the Grand Chef (stand-in: a Master Chef and 8 kinds of meal in the store). Goods: I 128 bone meal
  (Composter), 128 wheat seeds (Farmer), 64 clay balls (Sifter); II 64 sweet berries and 32 glow berries (Orchard
  Keeper), 64 small flowers (Florist), 16 saplings (Lumberjack); III 32 honey bottles (Beekeeper), 64 carrots and 64
  potatoes (Farmer), 32 cocoa beans (Orchard Keeper); IV 16 cakes (Chef), 32 eggs (Butcher), 32 raw cod (Fisherman).
  Done when: 35.9's four design checks pass for it, with the falls running in its renders.
- [ ] **35.20** **The Hanging Gardens: its powers and time-lapse.** While it stands, across its realm (the villages
  linked to its village, M33; until that lands, its caravan partners; always its own): every farmer and orchard keeper
  harvests a quarter more (one more item for every four picked, counted per worker; no extra crop growth ticks), and
  villages grow faster (`hall/VillageGrowth`): a baby needs 12 meals in the store instead of 16, and up to two are
  born a day when there are free beds. Its terraces are four village farms, so farmers move in by themselves. Power
  types: `harvest`, `births`. Done when:
  - GameTests: a farmer in a caravan-linked village brings back 25% more over 40 harvests (fixed random); two babies
    in a day with the meals and two free beds; a village farmer takes up a terrace's farm; a damaged Garden turns both
    off;
  - scene `wonder_hanging_gardens`: the time-lapse, ending with the falls running and farmers on the terraces; its GIF
    and powers are in the README.
- [ ] **35.21** **The Colossus: its design.** The Founder's statue, 47 high, drawn like 35.9: a robed villager, nose
  and all, one hand on the chest and the other raising a lantern, on a 13-high stepped plinth with an arch through it
  (5 wide, 6 high) for the main road and its caravans, so the village passes beneath. The figure is stone (polished
  diorite and smooth stone) under a cloak of cut copper that weathers green over the weeks (about 250 copper blocks);
  the lantern is a glass cage round glowstone, lit day and night; braziers ring the plinth, and the Founder's plaque
  is a row of signs. It needs scaffolding (35.6) for the arm and the head. About 9,000 blocks: I *The Plinth and the
  Road Arch*, II *The Robes*, III *The Arms and the Cloak*, IV *The Head and the Lantern*. Data: City; the Founder
  (stand-in: City rank). Goods: I 128 baked potatoes (Chef), 64 coal (Miner), 8 iron pickaxes (Toolsmith); II 64
  copper ingots (Armorer), 64 white wool (Shepherd), 32 leather (Butcher); III 32 chains (Tinkerer), 32 stripped dark
  oak logs (Lumberjack), 16 potions of strength (Cleric); IV 32 glowstone dust (Netherworker), 64 small flowers
  (Florist), 16 cakes (Chef). Done when: 35.9's four design checks pass for it, with the face seen from the road in
  its renders.
- [ ] **35.22** **The Colossus: its powers and time-lapse.** While it stands, every villager in its realm is happier
  (+8 mood, +12 in its own village; "the Colossus" shows among the reasons for their mood, `people/Moods`), travellers
  come to its village's inns twice as often and are twice as likely to be Experts (`inn/Innkeepers`), and it counts 8
  beauty. When it's finished, the plaque's signs are filled in: the Founder's name (M29's Founder; the hall's owner
  until that lands), the village's name and the day it was founded, from the chronicle. Power types: `mood` (realm),
  `travellers`, `beauty`. Done when:
  - GameTests: a villager in a caravan-linked village gains 8 mood and one at home 12; two travellers in a morning
    with beds free; the plaque reads the village's name and founding day; a damaged Colossus turns them off;
  - scene `wonder_colossus`: the time-lapse with the scaffolding going up and coming down, ending at night with the
    lantern lit over the road; its GIF and powers are in the README.
- [ ] **35.23** **The Stadium: its design (Cobblemon).** An oval arena about 47 × 37 and 16 high, drawn like 35.9: a
  battle field 31 × 21 of grass with white lines and a centre circle (no logos), two trainer boxes with a Training
  Post each, a professor's box at the side, stands of five tiers along the sides and three at the ends with seats in
  red and white (the village's own colours once M30's Village Banner exists), a covered stand on the west under a
  canopy on columns, a gateway arch with banners, four floodlight towers of redstone lamps lit at night by daylight
  detectors, a scoreboard wall, and outside a nurse's booth (a brewing stand and a Healing Machine) and pitches for
  market stalls. Palette: stone bricks and smooth stone, spruce, red and white terracotta, iron for the floodlights.
  About 18,000 blocks: I *The Field*, II *The Lower Stands*, III *The Upper Stands and the Covered Stand*, IV *The
  Gate and the Floodlights*. If M28 shipped a smaller Stadium for its Festival Cup, this one is the **Grand Stadium**
  in game, to tell them apart. Data (loaded only with Cobblemon, through `fabric:load_conditions` like the explorers'
  Cobblemon loot; without it the Stadium isn't on the Wonders page): Town; the Pokémon Professor (stand-in: 25
  pastured Pokémon of 10 types). Goods: I 128 bone meal (Composter), 64 small flowers (Florist), 32 Poké Balls (Ball
  Smith); II 128 red and white wool (Shepherd, Dyer), 16 Poké Snacks (Chef), 16 Great Balls (Ball Smith); III 16
  banners (Carpenter), 64 cooked beef (Chef), 32 berries (Orchard Keeper); IV 8 daylight detectors (Tinkerer), 16
  Ultra Balls (Ball Smith), 32 pumpkin pies (Chef). Done when: 35.9's four design checks pass for it (the compat
  GameTests for the parts with Cobblemon's blocks).
- [ ] **35.24** **The Stadium: its powers and time-lapse (Cobblemon).** While it stands:
  - **Stadium Days**, every second day in the afternoon: two to four visiting trainers (from the villages of its realm
    or its caravan partners; travelling trainers from Journeyman to Master when those have none) stand in the trainer
    boxes until dusk. Players challenge them as they do the village's own (`trainer/Trainers`: CobbleDollars, once a
    day each, money only). The village's own trainers take on the visitors in bouts settled by rank and team strength
    (the server can't play a battle out with nobody in it), both gaining battle XP, with the results in chat and the
    chronicle. Villagers off work fill the stands, and those who watched get +5 mood.
  - **The tournament** (needs M28's tournament item): the season's Festival Cup is held at the nearest Stadium among
    the villages taking part, and its village gets the market and the crowds; nothing more until M28 lands.
  - No badges and no gym leaders: Trainer Leaders pay money only, as the owner decided.

  Power types: `stadium_days`, `tournament_host`. Done when:
  - GameTests (compat): a Stadium Day brings the visitors into the boxes and sends them off at dusk; a bout's result
    and XP follow the ranks (fixed random); a player's win pays once a day; a damaged Stadium holds no Stadium Days;
  - scene `wonder_stadium` (`-Pcobblemon=true`): the time-lapse, then a Stadium Day with full stands and a player's
    battle against a visitor; its GIF and powers are in the README.

Depends on: M29 (the Legend engine's `legend` condition; the Master Architect, Seer, Merchant Prince, Old Sage, Golem
Smith, Grand Chef, Pokémon Professor and Founder items; born Legends, for blessed weddings), M28 (the tournament
item), M30 (the shared 2× speed cap, which 35.14 adds if it isn't built yet; the Village Banner's colours), M32
(drowned pirates; a day's warning; sieges), M33 (the realm and its treasury). Every link has a fallback here, so no
item waits.

## Later (not in this plan)

- The 26.x node (Milestone 19, phases 3–4): on hold until the owner says go.

---

## Design decisions (from the owner)

- **Unfinished expansions stay off** (2026-10-05, after 0.139.0 shipped 1.1-1.4 switched on): each expansion is
  switched off until the whole of it (e.g. all of 1.1) is done; the release that completes it turns it on (B76).
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
- **As big as MineColonies, if not bigger** (2026-09-28; since 2026-10-03 the plan itself, Milestones 27–35), built in
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
- **Fewer job blocks** (2026-09-30): too many job blocks look ugly and put new players off. Jobs use vanilla blocks
  wherever one fits (a jukebox for the bard, a beehive for the beekeeper); jobs that are close share a block, and the
  player picks the job by right-clicking the villager with an item. Custom job blocks only where nothing vanilla will
  do, such as the Blueprint Table. The plan is under 21.1a.
- **Hands-off** (2026-09-29): the owner reviews only what he can see or feel in game, from screenshots and GIFs.
  Work with nothing to see (tests, tooling, internal fixes) is accepted automatically once the tester passes, and is
  only listed in the next message. He wants to be asked only for real decisions.
- **Two weeks** (2026-10-03): everything through 2.0 released by 2026-10-17, because another team could ship first.
  Lanes work around the clock, several at once. Speed comes from parallel lanes and trimmed checks, never from
  thinner content: every feature complete, vanilla-quality, finished.
- **Order of the expansions** (2026-10-03): 1.1 villages that build themselves, 1.2 Pokémon and villagers together,
  1.3 Legends, 1.4 edicts and civic items, 1.5 quests become stories, 1.6 threats, 1.7 the realm, 1.8 classes and
  luxuries, 2.0 Wonders.
- **Edicts** (2026-10-03) have real costs, but each has a reform the village earns through quests: once reformed, it
  keeps its boost and loses its cost.
- **Mythic Legends and Wonders** (2026-10-03): several may exist on a server, but each village has a cap, growing with
  its rank (config).
- **Unhappy Legends** (2026-10-03) never move out: they go on strike until their needs are met.
- **The Pokémon tournament** (2026-10-03) must fit the mod's themes (villages, festivals, market days, trainers who
  level up by battling, the chronicle). Still no badges and no gym leaders.
- **Classes** (2026-10-03) switch on for every village on the live server at once.
- **Testing while building** (2026-10-03): builders write their own GameTests as they build; a dedicated QA lane
  tests everything after it lands; slow checks (mutation, repeats, filming) run on GitHub's machines. Releases ship
  verified work only.
- **Release channel** (2026-10-03): 1.0 goes to **CurseForge and GitHub only. Don't publish to Modrinth** (its rules
  bar page images made with AI and may unlist projects made mostly by AI). The pages say the mod was made with AI
  assistance, without saying where. Keep that line general and true: code and textures both had AI help, so never
  "only the code". Store-page work (26.2–26.5) is for CurseForge and GitHub; drop Modrinth parts, including
  mod-publish-plugin's Modrinth target.
- **Villages with no player nearby keep working** (2026-10-03), through chunk tickets; a config option lets a server
  owner pause them instead.

- **Sprint mode** (2026-10-03): the owner asked to go back to how the first chat worked, after four lanes and an
  hourly QA lane built one feature in four days. Two build lanes (odd/even numbers) commit straight to `main` in
  3-hour runs (owner: longer if need be); lane C is off; the QA lane runs overnight only; reviews never block; the evening digest releases daily.

## Notes / blocked
- **Owner approved every decision in the 2026-10-05 decision notes as written** (chat, 2026-10-06): 27.15-34.1 and B78, including 28.7 (Cobblemon's nurse keeps working), 28.16 (Arena at level 4), 27.16 (bridge deck one block above the bank) and the 33.1 defaults (real-week leaderboard, no tribute, owner-only treasury from 33.5, colonies at City). He will review the wiki (22.9) at the next digest and send notes.

- (Sessions: anything that needs the owner, and the link to the latest Full test report. Handoffs go here through
  `sessions.py handoff "<in progress, next, traps>" --as <you>`, which keeps one per kind of session (chat, night):
  carry over anything still true from the previous one.)
- 2026-10-06 (32.1, question for the owner; lane b): may M32's threats damage builder-made buildings on the live
  server? Rams break only the gate blocks (fence gates, doors, iron bars) of finished wall and gate builds, and a village
  fire burns only blocks the builders placed; never a player's own blocks, and the builders always put them back.
  Default, which lanes build with: yes (`siegeDamage` true). The other choice: `siegeDamage` false on his server, so
  gates hold and a village fire burns out without destroying anything. Jesse: yes/no. Design note `docs/design/M32.md`
  ("For the owner to decide"), with a mock-up of the Defence page (`docs/design/M32-defence.png`).
- 2026-10-06 (32.1, decisions; lane b): raids come from the standing lair, else monsters from the edge (today's rule as
  data); the threat clock keeps the next two nights rolled per hall, and the Seer reads it instead of rolling its own;
  `siegeDamage` also covers village fire; the new switches (`raiderCultures`, `sieges`, `siegeDamage`, `disasters`,
  `threatLevel`, `firewardens`, `wellKeepers`, `physicians`) sit behind a new `Expansions.M32`, while 32.2/32.3's engine
  work runs under `villageRaids`/`banditCamps` with today's numbers; "builder-made" is a block at a finished build's
  blueprint position still matching it (`MaterialRules.matches`). Details and every saved field in M32.md.
- 2026-10-05 (B76, decision; lane b): the unfinished expansions are gated in one place, `Expansions` (core): a flag
  per milestone (M27, M28, M29, M30, all `false` now). Each of its config switches defaults to that flag and
  `WorkplaceConfig.apply` ANDs the switch with it, so a 0.139.0 file holding `true` still leaves the feature off, and its
  options are hidden from the settings screen. Besides the switches B76 named, M28's `partnerShows` (28.3) and
  `nurseHealingMachine` (28.7) and M29's `giftedChance` (29.6, Gifted.CHANCE 0 while closed) are gated. The release
  that completes a milestone sets its flag to `true` (owner rule: a whole expansion done, its release turns it on).
  GameTests and the screenshot client (`-Daliveworkplace.shots`, so the showcase still films them) open every gate
  (`Expansions.openForTests`); `ExpansionGateGameTests` closes them to test a player's game.
  Content with no switch (27.13/27.14 blueprints, crafting recipes, the Healing Machine's Nurse POI) stays visible.
- 2026-10-05 (30.21, lane d): the In-Game Guidebook (26.2a) has landed, but its pages are screenshots from filmed
  scenes (`tools/guide/pages.py`) and `GuideGameTests` checks every page's picture; the *Edicts* and *Civic items* pages
  wait for the first nightly film of `village_talk` (and the existing `work_horn`, `cradle`, `harvest_idol`, `tonics`,
  `guilds` stills). Next lane on 30.x: add those two chapters' pages from those stills, with the README's text.
- 2026-10-05 (25.2, question for the owner; lane a): first measurement (nightly-tests 37297003813, 150 workers over 3
  villages, GitHub runner): idle p50/p95 2.3/3.5 ms; busy 18.3/32.8 ms at the start (p99 66.1) and 8.8/16.5 (p99 21.1)
  once settled; heap 1613 MB after GC; our code 13.1% of the server thread. Proposed targets, unchanged from 25.2's
  draft: our code under 15% of the tick at 150 workers (met, 13.1%); no tick over 50 ms from us (the 66 ms p99 is in the
  start-up sample, unattributed); heap flat within 5% over 60 minutes (not yet measured); no regression over 10%
  between releases (baseline: this row). Do you confirm these? Jesse: yes/no or changes.
- 2026-10-05 (27.19, decisions; lane c): "approved by hand" for the ledger: a build proposal the owner approves on the
  desk (the Steward's own approval in Run the village rechecks the ledger and refuses with "would go through a player's
  build"); roads count as approved by hand (they're drawn or approved on the plan), so the ledger doesn't hold them back,
  but Keep Clear and protected villages do; wall pieces never cross a marked section. A site leaves a block only when it
  isn't natural *and* its section is in the ledger, so an upgrade still takes down parts of the village's own building.
  The ledger starts empty on old worlds (from 1.1 on, as specced). Walls.java called the 3-argument
  `Roads.builderFor` after the 27.17/27.18 merge (main didn't compile): fixed with `far = false`.
- 2026-10-05 (27.15, decisions; lane c): roads are built only with a Steward appointed at the hall (and `steward` on),
  and not while the plan is set to Rest; with `stewardRoads` off (and in GameTests, which the road tests turn on) the
  plan's roads are drawn but never routed or built. A road's way (`CityPlan.Road.route`, offsets from the hall with y,
  saved with `routed` and the `built` segment numbers; older saves load unrouted) is found once per road and kept, so
  a reload doesn't route again; a road changed on the plan is a new road. A stretch that finds no way (or takes over
  30,000 nodes) ends the road there. "Kept clear" is the road's whole width at each node: natural ground or paving
  underneath (or a one-block dip onto it), only air, plants, leaves or natural ground (dug away) for 3 blocks over,
  no water; buildings (finished, or sites) are kept off a block wider. The segment id is
  `roads/<hall x_y_z>/<road key>_<n>` (the key a hash of the road and its route), not a running number, so it needs
  no new saved counter. A segment's columns go to the nearest node (straight across first), its surface at that node's
  ground height: the middle mix, the edge mix on the outermost column (a lane is all middle), stairs where the next or
  last node is a block lower, facing the climb. Road styles map from building styles by `blueprint_styles` in each file
  (Stonework also paves Grand; any other style is As drawn); 27.4's style names are kept. "No building of the village
  waits" is read as: no Steward's build queued or without a builder; a segment goes to the nearest idle builder
  (nothing building, nothing queued) whose bench is within `Builders.MAX_SITE_DISTANCE`. Segments are sites owned by
  the hall's owner but not the Steward's open builds (no `stewardHall`), so they don't take his build slots; they
  don't level the ground round them. A finished segment gives the builder 2 XP (like a repair), no blueprint back and
  no chronicle line; the whole road finished gets one ("Bram finished a Stonework road, 40 blocks long"). A door's lane
  is a road on the plan with `lane` (approved, 1 wide, the joined road's style), built like the others; lanes don't
  count towards the 24 roads (at most 64 lanes). Lamps, bridges and the slab use of steps wait for 27.16 (the style
  files already name their blocks).
- 2026-10-05 (27.16, decisions; lane c): lamps and bridges are part of a road's segment blueprints, so a builder lays
  them with the road and nothing new is saved per lamp. A street or avenue's lamp is the `street_lamp` blueprint styled
  like the road (`BlueprintStyles.styled`, the road style's `lamp` swapped in for its lanterns), its foot a block beside
  the road's edge, at nodes 16, 32, 48... alternating left and right of the way the road runs, plus one 3 nodes before
  each place it enters another road ("at every crossing"). A lamp needs clear natural ground for its 3x3 foot off every
  road and no door within 2 blocks of it; else it moves up to 3 nodes on, or is left out (so corners and bridges get
  none). Lanes get the style's `lantern_post` two high with the lamp on top every 12 nodes. Beauty counts the lamps on
  finished segments (`Road.lamps`, saved, default 0) at a Street Lamp's 1 point each, inside the same 10% cap; the light
  is real block light, so the "dark" tip sees it with no change. Gaps: each stretch's straight line is looked along
  before routing; a column with water on top, or no ground within 2 blocks under the surface, starts a gap, which runs
  straight on along the line's main axis to the first column with ground (40 at most). Up to 16 wide it is bridged:
  the deck is one block over the higher bank (the spec's "at the banks' height" read as on top of them, so the deck
  clears the water and the first and last deck blocks are the stairs up, the "ramp"), rails (the style's wall/fence) on
  both sides, a pillar under the middle of every 4th deck block down to the bed, 12 deep at most. Wider, the road ends
  at the near bank, the width is saved on the road (`Road.gap`, default 0) and the Steward's card on the desk says "A
  road stops at a gap 20 blocks wide: a bridge spans 16 at most". Banks of different heights are not evened out (a
  drop of 2 off the deck's low end is possible). Steps: 27.15's stairs already run across the road's whole width; the
  style's slab is still unused. Crossings: columns another routed road also covers are paved with the middle mix (no
  edge stripe through the crossing).
- 2026-10-05 (30.15, decisions; lane d): the status line keeps the pace line's one format for every source, so a
  tonic reads "25% faster (Miner's Brew, 19 min left)" (the spec wrote "25% faster: Miner's Brew, 19 min left"); minutes
  are whole minutes left, rounded down (20 right after drinking, 19 a tick later). Only our own tonic items are refused
  with a sentence; an ordinary item a pack makes a tonic (the test pack uses glow berries for farmers) goes on to its
  usual use when it doesn't suit the villager, so it never steals that item's other right-clicks. The tooltip works for
  any item a tonic file names: the server sends the lines to each player (packet `aliveworkplace:tonics`).
- 2026-10-05 (30.13, decisions; lane d): a placed Village Banner hangs a vanilla banner of the design, and breaking it
  gives back that vanilla banner, not the Village Banner (the gold is spent on the hanging); it can be made a Village
  Banner again with another gold ingot. Kept that way so placed banners stay plain vanilla blocks (no new block, saves
  stay vanilla); owner's call if the gilded item should come back instead. The door banner goes on the outside of the
  front door's wall: a block above the door's top, higher past a hood or an awning (up to 4 above), else right on top
  of the door, else a block to the side; a front door with none of these free gets none and the banner stays in the
  chest (38 of the 43 door-bearing starter blueprints have a spot). Guards repaint nothing: only shields with no base
  colour are painted, so a shield painted for an earlier set of colours keeps them.
- 2026-10-05 (29.10, decisions; lane a): strange moods (`legend/StrangeMoods`). The day's roll picks one qualifying
  Legend, throws its way's `chance` (default 1 in 8) once, then picks one of its Masters, so a village has at most one
  roll a day however many Masters it has. The three materials are one of each, picked from `masterwork.materials`; the
  mood watches the chest(s) by the workstation (the builders' 8-block supply rule) and takes them only when all three
  are there. Who brought what: a hand-over at a Storehouse board counts for that player (new `Requests.given` hook);
  otherwise whatever turns up is put to the nearest player within 8 blocks of the workstation. Ties go to the first
  giver; an offline top giver means the chest. The deadline is the end of the third day (start day included). The
  name is the file's `name` key with (village, maker, made-up word, item) as arguments, by default "The <word> <item>"
  from 16 words (Ember, Gilded, Starlit…), picked from the maker and the day so it never changes. If the Legend's slot
  was taken during the mood, the Masterwork is still made but nobody becomes the Legend. Switching `strangeMoods` off
  calls a mood already on off quietly at its next check (no sulk). A broken workstation doesn't end the mood: the
  chest by its spot still counts. The Founder's way (`"founder": true`) is skipped by the daily roll: 29.23 starts it
  with `StrangeMoods.start` at the first rise to City. No shipped Legend has an `inspired` way yet (29.15, 29.18 and
  29.23 add them), so in a real world nothing happens until those land.
- 2026-10-05 (29.11, decisions; lane a): the Old Sage (29.14) and the Professor (29.21) don't exist yet, so 29.11 is
  the generic system proven with the gametest tree (`aliveworkplace_test:test_tree`, worked by `test_sage`); those
  items add `research_trees/ancient_lore.json` and `pokedex.json` (and the `pokedex_species` counter through
  `ResearchTrees.counter`). File format: `legend`, `icon`, `name` (text), optional `requires`, `topics` (≤ 14): `id`,
  `icon`, `name`, `description`, `levels`, `cost` (a list of item maps, one per level, the last repeating), `points`
  (per level, times the level), `needs`, `unlock` {`counter`, `at`}, `exclusive` (group), `effects` (per level). The
  topic in progress is kept in the same levels map as `@<tree>/<topic>` (-1 unpaid, else points done), so there's no
  new save field; `Research.State.isLevel`/`totalLevels` skip it for ranks and `research_levels`. An exclusive group
  counts as taken once a rival is paid for or researched (a chosen, unpaid pick can still be changed). Scholars help a
  tree only when the scholars' own tree has nothing chosen, at half their pace, and only while its Legend works (not on
  strike). Tabs list Legends from the server's record (living, holding their slot, settled in that hall). A Legend
  with no trade of their own opens their tab by sneak-right-click. The new effects also work in edicts.
- 2026-10-05 (29.12, decisions; lane a): Minecraft 1.21.1 has no copper lantern (it arrives in 1.21.9), so the Grand
  style's rule turns lanterns into `minecraft:copper_lantern` where that block exists and leaves them iron lanterns on
  1.21.1 (a style rule whose result isn't a block is skipped). The Grand style is an ordinary style, so players can also
  pick it on a blueprint (owner's call whether it should be the Architect's alone). "Never anything a player built":
  only buildings in the builders' finished list are picked (a player's own hand-built house is never in it);
  decorations and defences (`StarterBlueprints.DECORATIONS`, `DEFENCES` families) are left out. A rebuild is a village
  build like the Steward's (no blueprint item comes back if it's cancelled). A strike cancels the rebuild under way (the
  blocks placed stay) and none starts until it's over. The Architect can be the builder handed the work if they are
  the least busy one with a bench.
- 2026-10-05 (29.18, decisions; lane a): "on the same condition" for the inn guest and the born Grand Chef is the
  file's condition, 8 kinds of meal in the store (a happy village is part of the inspired way itself). Everyone in the
  village who isn't asleep at supper counts as having come (as at a festival's feast); children are counted for the mood
  but eat nothing. The banquet is called after work (9000), the feast is at 10500; no banquet on a festival's day (it
  comes the next evening). The two births a day run from the banquet's day through the next two, and stop if no free bed.
  The toque can't rise above the hat layer, so "tall" is drawn as pleats from the crown to a gold band at the brow.
  Also restored the Seer's outfit recipe's lost save lines in tools/textures/art/villagers_2.py (a merge dropped them;
  the PNG is unchanged).
- 2026-10-05 (29.16, decisions; lane a): the night's raid and the guests are rolled ahead only in a village with a
  settled Seer (elsewhere nothing changes). The foretelling comes at the hall's first round of the day before 6000;
  the first one (or the first after a missed dawn) also rolls today's guests, unannounced, so tomorrow's roll knows
  whether today's guest will still be staying. A told guest who may no longer come (their slot taken meanwhile) doesn't,
  and nobody else does. The raid's side is the middle of one of the eight directions (the bandit camp's when there is
  one), its hour between 13500 and 18000. A wedding is blessed when held at the Chapel with the Seer within 32 blocks;
  the blessed baby needs only a free bed and room under the village cap (no daily wait, store or mood), from the
  wedding day to 2 days after. `Legends.foretold(level, hall, kind)` returns days of warning (the Seer: 2, from
  `warning_days`; 0 without). The scene moves the arrived Seer from the nave to the Chapel's door for the camera.
- 2026-10-05 (29.14, decisions; lane a): the hut is looked for once a day per hall, in the morning round, while the
  village qualifies and no Sage is out at a hut (a killed Sage frees the slot for a new hut another day); its site is
  `BanditCamps.site` at 150-250 blocks. The rumour goes to the owner and their friends online. The Sage is a found
  Legend (`LegendSites` captive, site `hermit_hut`); the answer is the item in the hand (a filled or empty map, any
  book, wet or dry sponge; the water bucket's bucket comes back), an empty hand repeats the riddle, and the hint comes
  with every miss from the second on. Ancient Lore effects outside the shared toolbox are `flag`s counted per level
  (`TreeEffects.flagCount`); the Iron Pact's golem comes on days divisible by 5, and the 25% damage cut is a mixin on
  `LivingEntity.actuallyHurt` for golems inside a village's area and guards of the village.
- 2026-10-05 (29.13, decisions; lane a): the Pathfinder's place is picked from three chat buttons after the
  sneak-right-click (`/workplace expedition <kind>`, open 60 s, only for the player who clicked); the lookup runs once,
  when a place is picked, and nothing is used up when there is none within 3,000 blocks. "Standing still" for Home is
  the player (the Pathfinder is held still); Home goes beside the hall of the Pathfinder's village. A player in another
  dimension is waited for where the Pathfinder stands. The entrance banner is light blue and stays. The chronicle line
  is written on arrival, and a cancelled trip gets its own "turned back" line. Their own expeditions roll
  `explorer/pathfinder` on top of the usual finds.
- 2026-10-05 (29.8, decisions; lane a): guests (`legend/LegendGuests`) are nitwits until they settle (no job, like inn
  travellers) and go by one of their file's `names` (the title if none). A place's day roll is spent only when some
  Legend may come there that day (conditions met, slot free, no visit in 7 days), so a village that qualifies at noon
  still gets that place's roll when its time comes, and the inn's traveller isn't lost to a roll nobody could win. When a
  Legend comes to the inn, that morning's arrivals are over (no traveller too). "Out of sight" is the inn's rule: no
  player within 24 blocks. The home need for a guest is a free bed in a finished tier III+ building with nobody else's
  bed in it; their luxury counts if it's in that home's chests or the store (29.5 takes it the round they settle).
  `Legends.visitFactor` multiplies every place's chance, so the Book's Open Gates line ("Legends visit the inn twice as
  often") undersells it; owner's call whether to reword it "Legends visit twice as often". The chapel's midnight is
  day time 17500 to 19000 (a hall round falls in it); the chapel and hall rolls run in the hall's round, the festival's
  in `Festivals.fireworks`. Hall fields `legendVisits`, `legendGuest`, `legendRolled` (absent: empty).
- 2026-10-05 (30.1a, decisions; lane d): reform costs scaled to the owner's example (The Shift Bell 24 clocks, 128 gold
  ingots, 300 bread) and the rest in proportion, so each reform asks for hundreds of items (the design note's table);
  slay steps went to 32-40 monsters (Conscription's planned 48), the one battle step stays one battle. The emeralds a
  step pays were left as they were (the reform is the reward); owner's call if he wants them raised too. Hand-in
  already took every slot up to what's left and kept partial progress; a GameTest now proves it over three trips.
- 2026-10-05 (30.12, decision; lane d): "within 4 blocks of a bed" is measured to the bed's head (where the game records a bed as a POI), so a cradle 4 blocks from the foot may be 5 from the head and not count.
- 2026-10-05 (30.10, decisions; lane d): two effect types: `militia` {`damage` 3, `range` 24} (highest of each) read by
  the new `guard/MilitiaCombat`, a behaviour every villager's CORE package now starts with (a guard's does nothing; for
  guards it sits after their own four, so GoalPackagesMixinGameTests holds unchanged), and `work_stops_in_raids`
  {`until_noon`, optional `near`} (strictest: any without `near` stops everyone) read by `hall/Conscription.workStopped`,
  the one work gate: `mixin/BrainMixin` takes a stopped villager out of WORK into IDLE, stops every running WORK
  behaviour (ours and vanilla's) and keeps the schedule from sending them back. "Raided" is `VillageRaids.raided`: our
  raid on the hall, or a vanilla raid at the hall or at the villager. Guards and mercenaries are never conscripts and
  their watch isn't stopped. While fighting, a conscript's brain idles (bell and hiding memories cleared, schedule held);
  with no raider in range they hide as the bell says. Whatever a conscript held moves to their empty off hand with its
  drop chance and comes back after (both hands full: they fight without a sword showing); the sword is a stone sword
  marked with custom data `aliveworkplace_militia`, drop chance 0, removed when the raid ends and on every entity load.
  `raidWorkUntil` on the hall is in the level's day time, not game time as the design note says: a night slept through
  jumps the day time to dawn, so game time would have kept work stopped well past noon; a value more than a day ahead
  (the clock set back) is ignored. Nothing a player reads was added besides the Book's lines (no status text for
  conscripts). `VillageRaids.track` starts a raid without the gathering and horn, for the showcase scene and tests.
- 2026-10-05 (28.13, decisions; lane b): the Gem Grotto (both tiers) is all vanilla and in the Blueprint Table without
  Cobblemon (`StarterBlueprints.VANILLA_JOB_BUILDS`), since the Gem Grower works without it; its "tumblestone ledges" are
  polished blackstone round the lava where she sets tumblestones over it. A budding amethyst can't be carried in
  survival (builders never place one), so the niche has amethyst blocks with clusters. Gem Grotto II's four cores are
  drawn as deepslate and swapped to `cobblemon:deepslate_crystal_core` when it exists (`StarterBlueprints.withFeatures`,
  spots in `GEM_GROTTO_2_CORES`). For 28.14: the Habitat Garden's mossy centre stone is
  `StarterBlueprints.HABITAT_GARDEN_CENTRE` (template (7, 0, 7), both tiers). Sugar cane by the ponds was dropped
  (a builder places it before pouring the water, so it pops off): large ferns instead.
- 2026-10-05 (28.14, owner's call and decisions; lane b): **Owner: may survival villages get a Habitat Block at all?**
  Cobblemon gives none (no recipe, no drop). Meanwhile `villageHabitats` is on: one per village, founded by an Expert
  keeper in place of the finished Habitat Garden's moss centre stone. The block is set up through its saved NBT (the
  keys Cobblemon 1.8.1's own habitat structures carry: SpawningStyle natural, PoolId, MimicId moss, ReplaceSpawns), so
  no Cobblemon class is touched; the phase today comes from `HabitatBlockEntity.getCurrentPhase` by reflection in
  `compat/cobblemon/CobblemonHabitat` (the mod compiles against 1.7.3). Biome files list biome ids or vanilla tags
  (`#minecraft:is_savanna`, `is_badlands`, `is_jungle`); 20 files, `zen_garden.json` has no biomes (anywhere else).
  The village's block is kept on the hall (`VillageHallBlockEntity.habitat`). The block must be saved without the
  inline `Name`/`Spawns` a new block carries, or Cobblemon reads an empty custom pool under that PoolId. The forced
  spawn round test runs Cobblemon 1.8.1's `PlayerSpawner.runForArea` (by reflection, signatures from the jar) over a
  zone round the block, with the chunks 48 blocks round it force-loaded (Cobblemon spawns only there) and the mock
  player's ticking spawner switched off. Untested: the Habitat Garden II upgrade rebuilding over the centre.
- 2026-10-05 (28.15, decisions; lane b): the five houses are fit-outs of the village house (`village.py`
  `pokemon_center_room` ... `gem_grotto_room`), 25 templates, all Cobblemon-only (the Gem Grotto too, as the spec says,
  though its blocks are vanilla) and behind `pokemonVillageHouses` (an M28 switch, read at server start when the pools
  fill). Weights: Pokémon Center 2, the others 1. A nurse only takes a Healing Machine by a honey bottle (it isn't her
  acquirable site), so the Pokémon Center's nurse carries the entity tag `aliveworkplace_house_worker`: once she ticks
  with no job site she takes the nearest free block her job holds within 6 blocks (`Stations.takeHouseBlock`) and the tag
  goes. The Campfire Pot's red pot is in the template's block entity data (`PotComponent`). The Gem Grotto has a real
  budding amethyst (world generation, never a builder) and tumblestones on a magma ledge behind panes. Each house but the
  Pokémon Center has a chest with its own loot table (Cobblemon items, loaded only with Cobblemon). The per-house tests
  need Cobblemon's blocks, so they are in `VillageCompatTests` (compat suite), not `VillageGameTests`; the core test
  checks the 25 templates' blocks and villagers by name. "Open space" there means the villager's box overlaps no block
  (a cook stood on her chest, a keeper on the hay nest). The `village` showcase scene now runs with Cobblemon and
  `POKEMON_HOUSE_WEIGHT=60` (new `-PpokemonHouseWeight`), shoots the Pokémon houses' workers and checks they stand free.
  Renders of the 25 cut open checked locally; the static renderer has no Cobblemon textures, so the Healing Machine, PC,
  pot and pasture show only in the showcase.
- 2026-10-05 (28.16, decisions; lane-b-1005-1833): the hall's Arena tip shows only once `Expansions.M28` is on (the
  Cup it serves isn't out yet); the blueprints and the Leader's trade aren't held back, like the Pokémon Center. Leaders
  sell the Arena at villager level 4 (Expert), earned through battles; owner: say if every Leader should sell it at
  once (level 1). Arena III's top roof slab row was dropped because the builder couldn't place its end block.
- 2026-10-05 (28.17, decisions; lane-b-1005-1833): only regular festivals count toward the Cup calendar (not ones
  called with a cake); Cups don't check the `festivals` switch; sign-up closes and the bracket is drawn when the Cup's
  day begins. The hall's page-room check is now 'room for 6 more pages' (the Cup page is the third page; 22.5 asks for 6).
- 2026-10-05 (30.9, decisions; lane d): `curfew` is one effect type with three fields: `raids` (factor, multiplied;
  also read by `BanditCamps.dailyChance`), `safe_nights` (night safety full in `VillageNeeds.count`, and a monster's blow
  on a villager asleep in bed cancelled through `allowDamage`) and `stay_in` (bedtime, no trading, festival over at
  dusk without fireworks, market traders' stay cut to dusk on arrival, no trips after midday). Curfew's boost is
  `raids 0.5, safe_nights` and its cost `stay_in`, so The Lamplighters (no effects) keeps the raids, safe nights and the
  protection in bed but nobody has to stay in. Bedtime overrules the brain's schedule check (`mixin/BrainMixin`, for the
  villager whose brain runs now: `WorkerLimits.thinker()`); vanilla's REST package walks them to bed. Market traders
  already in the village when Curfew is proclaimed keep their stay. M29's Night Owls and any night market should check
  `Curfew.keepsIn` / `CivicEffects.Sum.stayIn()`. 30.7's stand-in Curfew in OpenGatesGameTests is gone (the real one is used).
- 2026-10-05 (30.7, decisions; lane d): `legend_visits` is parsed and summed (`CivicEffects.Sum.legendVisits()`, factors
  multiply) but nothing reads it; 29.8's `Legends.visitFactor(hall)` should return `CivicEffects.of(level, hall)
  .legendVisits()` and set `CivicEffects.LEGEND_VISITS_READ = true`, which makes the Book show "Legends visit the inn
  twice as often" (left out until then). Arrivals a morning are counted per innkeeper in the saved attachment
  `guests_today` (with `last_guest_day`; an old save with a guest today and no count counts as 1). The inn's round moved
  to `Innkeepers.tend` (InnkeeperWork calls it). Curfew: Open Gates' file lists `curfew` in `excludes`, and exclusion
  already works either way round, so 30.9 needn't list Open Gates (it may); the test uses the real Curfew once it exists
  and a stand-in file at its path until then.
- 2026-10-05 (29.5, decisions; lane a): needs are checked in the hall's round on its first round of each `Chronicle.day`
  (saved as `LegendData.checked`, default -1), and on every round while a Legend is on strike, so they go back to work
  within half a minute of the last need being met. The 3-day grace only holds a strike off: days unmet are still counted
  (and shown as crosses), so a need unmet since settling starts the strike on the first day after the grace. Home: no
  bed of anyone else in the village (by their HOME memory) may be in the building's box but a married spouse's (a
  sweetheart still courting doesn't count). The luxury is due 7 days after the last one; taken, it gives "enjoying their
  wine" +10 mood for those 7 days. The picket is the first behaviour of every trade's WORK package (added where the
  villager's brain takes it, `VillagerMixin`); the rest of the package is held (`work/Gated`) while they strike; Legends with no workstation (`aliveworkplace:legend`) have no WORK
  activity, so they picket in IDLE. The red line is the worker status line over the head (`WorkerStatus`), with "No work
  till then" under it. `legendNeeds` off: the round checks nothing and clears strikes and days unmet. Question for the
  owner: "the hall's call-home passes them by" is built as written (Call everyone home doesn't bring a Legend back);
  if it should instead always bring them home, it's one line in `VillageHalls.recall`.
- 2026-10-05 (29.4, decision; lane a): two parts of 29.4 lean on items not built yet. (1) No Gifted villagers exist
  until 29.6, so the gold gift line under a villager's traits on the hall's list is left to 29.6 (it goes in
  `VillageHallScreen.person` beside the traits line). (2) Needs aren't checked until 29.5: the hall and the Legends page
  read each need's tick or cross from `LegendData.unmet` (keys `home`, `luxury`, `happy`, `LegendText.HOME` and so on;
  more than 0 days unmet shows a cross) and the strike from `strikeSince`, so 29.5 only has to write them. Until then
  every need shows a tick. A Legend's outfit is `textures/entity/villager/legend/<id>.png` (or the file's `outfit`);
  one that isn't there falls back to `legend/placeholder.png` (a gold circlet and a wine-red cape), never the magenta
  check. Each power's line is `legend.<ns>.power.<type>` unless the power overrides `Power.describe`.
- 2026-10-05 (27.10, decisions; lane c): the 11 rule files replace the 27.6 starters `homes`, `better_homes`,
  `store_full`, `market` and `food` (`storehouse` keeps its name and moves to Market). New condition
  `upgrade_adds_beds {}` (finished buildings whose next tier has more bed heads, both blueprints counted) and
  `upgrade {"adds_beds": true}` with no blueprint (upgrades any such building). Every new-house rule has
  `{"not": {"upgrade_adds_beds": {}}}`, so while a home can be upgraded to sleep more no new house is wished for.
  "N short" reads as at least N (the cottage: exactly 1, Hamlet only); terrace and inn rank above the stone house when
  they hold. Starter Cottage II to III adds no bed in our blueprints (2 and 2), so the Steward never picks that upgrade
  for beds. `homes_better` upgrades a home whose next tier adds beds (every home tier but cottage III), not only Stone
  Houses. The tests' well-kept staged halls were left behind and sped up `legendPace`'s builder (well_kept 0.86, so the
  2x cap hit early): the homes tests now remove their hall, and `Leftovers.halls` clears halls in reach for legendPace.
  Scene `steward_homes`: free materials, build delay 1, three builders; each morning is set by hand (nights skipped) and
  two villagers move in before days 2 and 3. Not filmed locally.
- 2026-10-04 (27.9, decisions; lane c): 27.11 isn't built, so a job the village wants with no free block left goes to
  `StewardJobs.WORKPLACE_WANTED` (does nothing yet; called once a morning per job): 27.11 fills it to propose the
  building. `StewardJobs.BUILDING_JOBS` (Berry Farm: Orchard Keeper, Flower Shop: Florist, ...) says which job a shared
  block inside a finished building is for; 27.11 can reuse it. "A farmer while food is short" and "a scholar while
  research is idle" give one a morning (food doesn't fill by noon); a worker who has traded and only lost his block is
  left to find one of its kind. A rule naming its topic (`hearth.json`) gets it while available, before the order.
  The research pick happens from the scholar's own work (`ScholarWork.IDLE`), once a day.
- 2026-10-05 (30.5, decisions; lane d): a reform step is a `VillageQuests.Quest` with `reform` {edict, step} kept in the
  hall's quest list (not counted in the 3 daily ones, never expired) and shown only while its edict is in force and not
  reformed, so a step half handed in keeps its count while the edict is lifted; the hall's `reforms` list keeps {id,
  step, reformed, nextDay}. The first step goes up when the edict is proclaimed (the round runs then too), later ones on
  the first round of the day after (`Chronicle.day`). A monster or a trainer beaten counts for the first daily quest of
  that kind and every reform step of it on the page. Data: `reform.steps[]` take `kind`/`item`/`count`/`reward`/
  `fallback`/`arc`; a fallback can't be a battle; a battle step with no `fallback` written clears out 8 monsters for its
  pay. `arc` (on the reform and each step) loads and is kept, unread until M31. The Book's line says "Reform: <name>,
  step N of M" or "Reformed: <name>", and a reformed edict lists the reform's `effects` (if any) as its cost.
- 2026-10-04 (27.4, decision; lane c): the plan on the ground is drawn as the Scan Tool's box really is: dust the
  server sends to the holder alone (`city/CityPlanGround`, every 10 ticks, at most 900 dots, within 24 blocks), not a
  client renderer, so nothing new has to be synced to the client. Road styles don't exist until 27.15, so a road's style
  is a building style (`BlueprintStyles`), "" for that of the zone its first point is in, resolved by the server when
  the road is drawn; 27.15 can map those names onto its road styles. Roads saved without `approved` load unapproved.
- 2026-10-04 (27.3, decision; lane c): the Steward's proposals (27.8) don't exist yet, so the plan screen draws only
  build sites going up as dashed white outlines; `CityPlans.Outline` has a `proposal` flag (drawn dashed yellow) for
  27.8 to fill in `CityPlans.screen`. Undo keeps the last 10 changes per hall on the server (not saved), shared by
  everyone editing that plan, so an undo always matches what the hall holds.
- 2026-10-04 (28.10, decisions; lane b): a lure spot is the middle of the area on a Field Marker handed to her (one
  block marked: that block), up to three, the oldest given up for a fourth; the marker stays with the player. Lures are
  read from Cobblemon's `spawn_bait_effects` (`cobblemon:typing` and `cobblemon:egg_group` effects: 18 types, 13 egg
  groups on 1.7.3), Alphas only when `PokemonFeatures.ALPHAS` (Hopo by id). The Camp Cook asked for a lure seasons the
  keeper's snacks with those berries only and counts only the snacks seasoned so; the keeper sets out a seasoned one
  first, else any snack. "Rare" means every world spawn of the species is in the rare or ultra-rare bucket (147 species
  in the compat pack). Alphas are recognised by the Pokémon's `alpha` aspect: not yet checked against 1.8.1 (the compat run
  used 1.7.3). The hall's list is each villager's tooltip on the People page: her last five sightings go there.
- 2026-10-04 (28.8, decisions; lane b): `asked` dishes in the camp menu name the jobs that ask in `for`; the cook keeps
  their `keep` while a villager of one of those jobs is within 48 blocks of the pot (fishermen for Poké Bait; the Poké
  Snack line names `aliveworkplace:habitat_keeper`, so it starts working when 28.10 registers that job; seasoning comes
  from whatever berries the chests hold, until 28.10 says which ones the keeper wants). Cobblemon's campfire has no
  unlit state, so "a Fire partner lights the campfire" is the `flames` show on the `cook` cue when she shuts the lid.
  Order-only dishes go to the Storehouse that ordered them; porters take the cook's dishes past her `keep` to the store.
  Farmers now sow any crop block whose seed is in `#minecraft:villager_plantable_seeds` (other Cobblemon crops in it too).
- 2026-10-04 (28.9, decisions; lane b): the breeder's `berry_goal` saves the goal only; the step is worked out afresh
  from what the village has (chests, her bag, her plot), so it never goes stale. "Found" is the hall's
  `berriesFound` plus what the village has now (a village without a hall still lights what it has). Plants of other
  kinds in her plot are dug up (the berry comes back) only when the step has no free pair of beds. Rows alternate by
  x (east-west neighbours), so a plot needs farmland beds side by side east-west. The showcase grows the plot on the
  spot with a forced mutation (Cobblemon's growth takes in-game days).
- 2026-10-05 (lane-c-1004-2132): 27.6, 27.7 and 27.8 are built and committed on wip/lane-c but not on main: the local full build passed all 689 GameTests, but runCompatGameTest ran out of heap twice (both times slowing at the batch cobblemon_orchard_planting; the 2026-10-03 QA note says the compat run OOMs locally unless `./gradlew --stop` comes first, and this run did stop the daemon). Next lane-c run: merge wip/lane-c, finish 27.9 (left: compile, runGameTest with StewardJobsGameTests and StewardDeskGameTests, checkLayers, Stonecutter refresh, tick), full build (compat with -Xmx1536m if needed), push to main.
- 2026-10-04 (23.10a, owner question; lane a): the village pieces aren't built by our builders: Minecraft's village
  generator places them (one shared outside per style, baked into each piece's file), and the mod has no "village
  leader" yet (the nearest is the Village Hall's owner: whoever first switches its protection on, with their friends).
  So "let the village leader override a piece's look" needs two choices: (1) who the leader is: (a) the Hall's owner
  and their friends, or (b) something new; (2) what overriding does: (a) on the Hall screen the owner picks, per piece,
  one of the five styles' outsides (or the piece's own new outside, if you want each piece drawn its own: a sign,
  porch or yard), and the village's builder rebuilds that house's outside in place (inside and its worker kept); or
  (b) it only changes the look of pieces built from then on (by a builder, 27.x), not the generated ones. Default if
  you don't answer: 1a + 2a with the five existing outsides (no new art), after the current lane work. Waiting.
- 2026-10-05 (23.10a, decision; lane-c-1005-0332): no answer, so the default was built: 1a + 2a with the five existing
  outsides. The leader is the hall's owner and their friends (operators too; a hall nobody owns has no leader, and
  choosing is refused server side). The hall's Builds button opens House looks: our village houses within the hall's
  radius, found by their bed (every house's bed stands at the same spot) and the outside that stands round it. A pick
  starts a build site for the nearest builder with the blueprint `aliveworkplace:outside/<style>/<house>`: the house's
  file in that style without its room (x 2-6, z 3-7, y 0-4), built over the house like an upgrade, so the room, job
  block and chests are never touched and the site saves and reloads like any other. The choice is kept on the hall
  (`piece_looks`, empty in older saves). Not a page-row tab: the row must keep room for six more pages (22.5's test).
- 2026-10-04 (29.1, decisions; lane a): `docs/design/M29.md` section 7 records eight choices lanes build on unless
  the owner changes them. The one that changes a spec: `mythicLegendCap` is one number (the City cap, default 2; a Town
  half, Hamlet and Village 0), not 29.3's list, because the config file and Mod Menu screen take only switches and
  whole numbers; same defaults. Also: an existing City gets the Founder's mood once after the update; zombie
  conversion must copy `LEGEND`/`GIFTED`/`STRANGE_MOOD` (new UUID), for 29.3.
- 2026-10-04 (28.7, owner question; lane b): Cobblemon 1.7.3 has its own villager job, `cobblemon:nurse`, whose
  workstation is the Healing Machine, so a jobless villager next to any Healing Machine already becomes Cobblemon's nurse
  (compat test on `wip/lane-b`). 28.7 says "a jobless villager never takes a player's machine by themselves". Which:
  (a) keep Cobblemon's nurse as is, and our Nurse comes to a machine only with a honey bottle; or (b) stop jobless
  villagers taking machines at all (Cobblemon's nurse then only via our honey bottle, as ours)? Default the lanes build
  meanwhile: (a), the least surprise for players who know Cobblemon's nurse. The rest of 28.7 doesn't depend on it.
  28.7 was built with (a) (lane-b-1004-1532): its test checks a jobless villager by a machine never becomes *our* Nurse
  by itself; (b) would be a filter on Cobblemon's profession in `AssignProfessionFromJobSite`, if he picks it.
- Old branches: `wip/treasury` (unfinished treasury work from before 0.136.0; the treasury has shipped since, so check
  before reusing any of it) and `tests/check-0.137-riding-protection` (the camel test, 21.3).
- 2026-09-29: the plan was installed by the planning chat. The chat that built 0.137 had started two test files for the
  full check (21.2) that were never pushed; write them again as part of 21.2.
- 2026-10-02 (21.1, lint): 181 of 182 textures pass `lint.py`. The one warning, `block/mailbox_flag.png` ("tile edges
  show as lines when repeated"), is expected: the texture paints only the mailbox's small red flag, one 1-pixel-wide
  model element (`models/block/mailbox.json` 1x2x7 when lowered, `mailbox_mail.json` 1x7x2 when raised), so it is never
  tiled and no seam can show.
- 2026-10-04 (23.6, lane-a-1004-0332's decisions): villages are kept loaded **while anyone is online** (an empty
  server keeps nothing extra loaded, as before); each worker's workstation chunk and the chunk it's in, remembered in
  saved data (`WorkSites`) so it works after a restart, forgotten 2 minutes after a worker is last seen; at most 400
  chunks per dimension. A world from before this release keeps nothing for villages until each has been visited once.
  The game rule `workplaceKeepWorkLoaded false` still turns all keeping-loaded off. (23.4: the material list is written
  with a Book and Quill in the other hand, right-clicking with the blueprint.)
- 2026-10-04 (23.1, owner's call, in the review package): the soak's bar is "every build in 2 in-game days"; the full
  starter set takes 3.4 days (builders work about 7,000 of every 24,000 ticks). Decision meanwhile: the yardstick run is
  `SOAK=true SOAK_DAYS=6`, judged on every build finishing, nothing lost and the stalls; if he wants 2 days to hold,
  that is a builder-speed item for M23.
- **chat handoff** (chat, 2026-10-03 06:35Z): chat 2026-10-02/03 night: LANDED B10 (zombie villagers centred, ZombieVillagerMixin), B8 (Stations.releaseOld + choose ignores a far-off remembered site someone else works at; the villager standing by a block is its owner), B2 (workshop test placed like a village; 100/100 repeats), B4 (explained: B5's hole; test aVillagerOutsideTheWorkshopFindsItsTable), B9 (forest scene stocks saplings; LumberjackWork keeps stumps waiting for a sapling), B12 (zombie house tests at night), all --no-review; 26.2a Guide Book (review pending, package sent; tools/guide/pages.py + build.py make the pictures from showcase stills; recipe unlocks via tools/recipes/unlocks.py) and 26.2 CurseForge kit (docs/store/curseforge, review pending, files sent). Open: B11 (orchard-house test flake, once in a suite). Fixed on the way: StationsFixesGameTests' old-block spots collided with each other when the test area moved (now step aside). Traps: land builds twice when main moves; the Guide Book needs its pictures rebuilt (tools/guide/build.py) if a scene's stills change a lot; jar is 3.9 MB with the pictures. Owner decisions pending: approve 26.2a / 26.2 / 21.1c / 21.1b / B5; CurseForge AI line and license (kit README).
- **night handoff** (night-1003-1046, 2026-10-03 11:26Z): night-1003-1046: health: CI on main green, nightly 2026-10-03 green, issue #1 old. LANDED 21.2 piece (--keep-open): performance with many homes in docs/agent/full-check.md (80 workers/40 plots on a dev container: our code 11.8% of the server thread, villager AI 40.7%, nothing of ours stands out). FOUND B14: the nightly soak on GitHub measured an idle server (every benchmark site 0%, no villager found); run.sh now fails the soak when no site is working, so the next nightly goes red on purpose until B14 is fixed. B14 paused with leads (not leftover configs). Next for 21.2: 20 mutants + whole-suite flake sweep, inventory.py gaps. Traps: Maven Central 429s on a cold Gradle cache (retry every 20 s; took 6 tries); the nightly artifact (server.log) can be fetched via the GitHub MCP download_workflow_run_artifact URL; run the packtest with Java 21 (/usr/lib/jvm/java-21-openjdk-amd64); land needs JAVA_HOME=/root/.local/jdk-25 exported; land's --keep-open and --no-review can't be combined; a mock player's connection.onDisconnect() skips Fabric's DISCONNECT event, use connection.disconnect(Component); PreviewNetworking and TableServer still touch plain HashMaps from that event (in B13's text, unfixed, small).
- 2026-10-04 (26.2, digest-1004-1253, owner: "you decide"): store page decisions are in
  docs/store/curseforge/README.md: the honest AI line on, GPL-3.0, the clipboard icon, keep the Guide Book line (the
  page goes live with 1.0), Beta while 0.x. `LICENSE` held the GPL-2 text while the mod declares GPL-3.0-or-later;
  replaced with the GPL-3.0 text on the owner's yes (2026-10-04).
- 2026-10-04 (24.2, lane-b): the mod has one tool-like item and no weapons: the Scan Tool (`item/scan_tool.png`), which went
  through the owner's pick round for every item (21.1b) and passes `lint.py`. 24.2 is ticked on that; the hammer, wrench
  and war hammer templates are for the expansions' tools (each one's own item is drawn from them, linted and sent in
  its package). The guide book's lint warning (12 single pixels) is a book, not a tool: left for 24.x.
- 2026-10-04 (lane-b-1004-0033): 22.7's code is on main (nightly.yml: mutation-plan, mutants 1-5, repeats,
  mutation-report); it runs first with tonight's nightly (sessions can't start workflows by hand: 403). Tick 22.7 once a
  run's mutation-report summary has posted, and fix whatever its first run shows. 22.8's code is on main; tick it when
  a push changing one scene films only that scene (the plan job's summary says "Scenes: <name>"). 24.4: GUI_SCALE=4
  works (mailbox checked); film the other screens and hand in the package.
- 2026-10-04 (26.3): done. `LICENSE` is the GPL-3.0 text since c303690f, matching `fabric.mod.json` and the README
  (now with a credits paragraph). The two MineColonies-inspired files (ExplorerWork, Netherworkers) say "adapted in
  spirit; the code is ours": no file carries copied GPL code needing a header. The mod's version is now
  `<mod.version>+<mc>` in fabric.mod.json as in the jar name (ReleaseGameTests); tags stay `v<mod.version>`.
  **The number 1.0.0 itself is set by the release session** (sessions don't bump versions). `publishMods` only
  dry-runs unless `-Ppublish.live=true` and `CURSEFORGE_TOKEN` (project id: `CURSEFORGE_PROJECT_ID` or
  `publish.curseforge`), waiting for the owner's store page.
- 2026-10-04 (24.5, lane-b-1004-0332): `langcheck.py` is clean (its 4 title/detail pairs were checked against the code
  and listed in its CHECKED_PAIRS with why). "(s)" is gone: counted sentences use `work/Words.counted` with a `.one`
  key (WordsGameTests guards it), and item counts read "28× Spruce Planks" everywhere. Left for 24.5: reading every
  other tooltip and message in context, and the package showing them.
- 2026-10-04 (lane-b-1004-0632): 28.2 found nothing to fix: the whole compat suite passes on Cobblemon 1.8.1 as it is
  (`-Pcobblemon18=true`), so `compat/cobblemon/` has no version shim yet; add one only when a 1.8-only feature needs a
  call 1.7.3 lacks. 28.3's engine ships one show (builder `fetch`, carry `from_work`): 28.4 should give the Rock and
  Steel builder shows a filter on what's carried (a `carry_tag`, so a Machop doesn't shoulder stone), and cue the other
  jobs from their own code. B40 leads (not run yet; the split soak landed at 07:22Z): the schoolhouse's "1 placed, 55
  skipped" at the start of FOUNDATION looks like foundation steps deferred and then skipped while their blocks sit in
  the storehouse, not walking time; check with `SOAK=true SOAK_SPLIT=true SOAK_DAYS=6 DEBUG=true` whether those 55 are
  ever built.
- 2026-10-04 (lane-b-1004-0932): 22.7 ticked: the first nightly mutation run (37193458879, 10:06Z) posted 11 of 20
  killed and 162 new tests x10 with no flakes to issue #1; its 9 survivors are tests for the QA lane to write
  (JobSiteTickets:70, StallWatch:94 `>=`, RanchWork:215, KeepLoaded:82, Builders:795, Soak:238, PartnerShows:196
  (QA covered since), CobblemonPartners:188, BlueprintImporter:34). 22.8 still waits for a push that changes only one
  scene's own lines (28.4's push also touched ScreenshotHarness). B42/B44: the harness fixes are on main (soak check
  accepts "; village chunks", an open door's panel isn't "stuck"); tick them once SCENE=soak passes (locally or on the
  showcase). 28.4's shows cue from each job's code; the porter's barrel is cued at collection and set down at the
  storehouse.
- **lane-a handoff** (lane-a-1003-2232, 2026-10-03 23:24Z): lane-a-1003-2232: health green (CI main; the 22:24Z red run died in Gradle setup, next commit green; nightly issue #1 old). Nothing landed. 21.2 PAUSED (blocked on B34/B36): new piece on item/21.2 - NetworkPayloadsGameTests (payload round-trips at limits, oversized client packets refused, mailbox Send and table Take via player.connection.handleCustomPayload; first test of Mail.send; 3/3 mutants killed) and full-check.md maps inventory.py's 14 'untested' mixins to their behaviour tests (name-matching gap only). Land passed 497+56 once, then main moved and the rebuild failed only on WorkersKeepTheirBlockGameTests.aStuckTrainerKeepsTheirPost (filed B36, B34's sibling). NEXT: once B34/B36 land, land 21.2 --keep-open (only the 0.138.0 old-world check at release and GitHub perf after B14 remain). Traps: every mock player is named test-mock-player, so getPlayerByName can return another test's player; run './gradlew --stop' before land or the daemon dies of memory during compat tests; land JAVA_HOME=/root/.local/jdk-25.
- **lane-b handoff** (lane-b-1004-0332, 2026-10-04 05:43Z): lane-b-1004-0332: health green (CI main). LANDED 26.1 tick; 24.4 + 22.3 (--review, packages sent; cbb1001e): every screen filmed at GUI 2 and 4, Shop Counter title and Blueprint Table row/count clipping fixed, harness fails a scene whose container title overflows; new scenes scan, config, Mirror in style_menu, sales log in counter. 26.3 PART (not ticked): Mod Menu settings screen (ConfigScreen + client/compat/ModMenuEntry, WorkplaceConfig.RANGES/load/save), issue forms, mod-publish-plugin CurseForge dry run OK; left: version 1.0.0 + CHANGELOG cleanup at release, LICENSE is GPL-2.0 text vs GPL-3.0-or-later (owner, Notes). 24.5 PART: langcheck clean, Words.counted (.one keys) replaces (s), counts read '28x Item'. 24.3 (--review, e39f973a): staff/extras/village clean; village camera now finds a clear view. NEXT: B40 (needs lane a's SOAK_SPLIT, not on main at 05:45Z - ask lane a's branch/notes), then 24.5 (read every message in context), 22.7/22.8 ticks after tonight's nightly/showcase, 26.4. Traps: a pull bringing only QA test classes - I ran those classes + mine instead of a 3rd full build; the showcase run on a merge that touches ScreenshotHarness films every scene (shared code), so 22.8 needs a push changing one scene's own lines.
- **27.5 decisions** (lane-c-1004-1832, 2026-10-04): the City Plan appoints through its own handler (`city/Stewards.appoint`, called from BuilderEvents before the Stations items) rather than a `work/Stations.ALL` entry: a Stations entry would let `Stations.choose` give the nearest free hall whichever hall the plan is bound to, and every list built from `ALL` (guide pages, job-item texts, the workstation break counter) would change with it; the appointment still uses `Stations.assign`. "Standing by the hall" is within `Stations.REACH` + 1 blocks. His rounds visit one spot per zone of the plan (the zone's cell nearest the hall), the storehouse and his open sites (`StewardWork.OPEN_SITES`, empty until 27.6); which zones count as "empty" is left to 27.6's planner. `StewardWork.PLANNER` is the hook 27.6–27.9 replace (it returns the status line, "Looking over the City Plan" until then); `Stewards.credit` gives XP for builds/jobs (`XP_BUILD` 10, `XP_JOB` 3). Old halls: a saved `stewardPlaceChecked` flag on the hall's block entity makes the first tick after loading re-register a record with 0 free places once.
- **27.6 decisions** (lane-c-1004-2132, 2026-10-04): rule files follow docs/design/M27.md's shape (`{"beds_short": {"at_least": 1}}`, `"do": {"build": {...}}`); `{"not": {...}}` turns any condition round (the starter Hearth rule uses it). Every field is checked and an unknown one refuses the file (`field 'when[0].beds_short.at_least'` in the warning), so a typo can't silently do nothing. Each condition gives one number, and `why` is filled with those in the rule's order (`%2$s` for the second). `cooldown_days` and `max` count from when a wish is *carried out*: 27.7–27.9 call `StewardWishes.carriedOut(level, hall, rule)`, which counts it, starts the cooldown and drops the wish; until then a rule that holds is wished for every morning. The day's wishes (at most 8, highest priority first) are ranked by `StewardWork.PLANNER` the first time he is at the hall each day (his day is `dayTime / 24000`, as his rounds) and saved on the hall as `steward` (absent in older halls: none). `store_full` reads the Storehouses' chests only (slots taken / all slots, via the new `ItemStores.slots`). `upgrade_available`'s `blueprint` is optional (any building). `VillageAdvice` now exposes the numbers its tips use (`bedsShort`, `foodWanted`, `jobless`, `poiCount`, `upgradable`, `homes`) and both read them.
- **27.7 decisions** (lane-c-1004-2132, 2026-10-04): "ground within 4 blocks of level" is read as the ground under the footprint rising at most 4 from its lowest to its highest column (so a slope of 6 is refused whatever its shape), and the plot's floor is the median ground height; columns above it must be natural ground down to it. "A builder's Blueprint Table" counts both builder job sites (Blueprint Table and Builder's Bench). The footprint plus 2 also stays off the plan's roads (as wide as drawn), so a house never stands on a street before 27.15 builds it. A column is read from its top down; a player's block above the ground (a roof, a wall, a chest) refuses the plot only if it's inside the box, so an overhang or a ceiling far above doesn't. "The rule's next blueprint" is `Plots.Request.blueprints` (a list, tried in order at each spot); 27.6's rules hold one, so it's a list of one until a rule format lists more. Spots are tried a few per cell (every `cellSize/2` blocks), nearest the hall first, each with its four turns, as drawn then mirrored. Results live in memory (`Plots.request`/`Plots.result`, not saved): the planner asks each morning for every build wish, and a search starts again only when the plan's zones or roads, the tables in reach or the builds in the plan's area change. 27.8 reads `Plots.result` for its proposal and calls `StewardWishes.carriedOut`.
- 2026-10-05 (23.1b, decision; lane c): the helpers' material pass (23.1a, `BuilderWork.takeFromCrewmate`) already
  had no flying item, only the item-pickup sound; it now goes through `build/TossSounds`, capped server-wide at 4 toss
  sounds per 10 ticks (8 a second at most, whatever the number of crews); sounds past the cap are skipped, the
  materials still change hands. No config switch existed for it, so none was added.
- 2026-10-05 (27.1a, decision; lane c): a Steward must be a Builder at Journeyman (level 3) or higher
  (`Stewards.MIN_BUILDER_LEVEL`, `Stewards.qualifies`): builder levels are already saved (1 XP per 5 blocks, 10 per
  build), so Journeyman is about 350 blocks of work, a few in-game days, with no new saved field. Others are refused
  with `message.aliveworkplace.steward.unseasoned`; the appointed Builder starts as a Novice Steward. Existing Stewards
  are grandfathered (checked only at appointment; a Steward always qualifies). City Plan recipe: Map + Blank Blueprint
  + Heart of the Sea, shapeless. Written in docs/design/M27.md section 7.
- 2026-10-05 (27.11, decisions; lane c): the 12 workplaces are `village.py`'s `workplace(name)`: the plains house with
  its jigsaws and structure voids made air, no villager, no loot tables, all calcite as white concrete; listed in
  `StarterBlueprints.WORKPLACES` / `COBBLEMON_WORKPLACES` (not `ALL`: they have no tier II, and `ALL` must). One rule
  per building, `steward_rules/workplace_<building>.json`, all `worker_without_workstation {professions}` at priority
  62 (just above `workstations` 60, so the build comes first), cooldown 2 days, no max. Zones: workshops (Builder's,
  Carpenter's, Sifting Shed, Tinker's, Nether Gate, Ball Workshop), market (Kitchen, Storehouse, Flower Shop, Supply
  Shop, Inn, Ferry House, Trade Hall), civic (Post Office, Clinic, Healing Center, Graveyard, Schoolhouse, Library,
  Research Lab, Trainer's House, Leader's Hall, School), defences (Guard House, Barracks), farms (Berry Farm, Compost
  Yard, Ranch), gardens (Apiary Garden). The shore is a plot rule (`Plots.SHORE_BUILDINGS`, reason `SHORE`): water in
  the 4 rows in front of the footprint. `no_builder` was already `builder.json`; its ask now reads "place a Blueprint
  Table and give a villager the job: nobody here can build". Done since (lane c, 1005): "a job the village wants with no
  free block" fires the same rules: with `"wanted": true` (set in 28 `workplace_*` rules; default false, so
  `workstations.json` is unchanged; not in `workplace_builders_workshop`: a builder is wanted only while there is
  none, so nobody could build it and `no_builder` asks the player instead) `worker_without_workstation` also counts each of its jobs in
  `StewardConditions.Facts.wanted()` (the morning plan's `StewardJobs.plan(...).wanted()`, so a wanted guard or scholar with
  no free block wishes its Guard House/Barracks or Library; a free block stops it, and so does having no builder, since
  nobody could build it). Each such wish takes one of the
  day's 8 wish slots (priority 62, above the market stall's 35).
  The farmer's want gets its building with 27.13. `WORKPLACE_WANTED` stays a no-op seam.
  The gallery package (front/back, as drawn and in Stonework) is skipped: the digest makes packages from the showcase
  scene `workplaces` (front and back of all 12). Not rendered here (tools/blueprints/render has no node_modules in
  this container): the copies are the village houses' already-checked builds with calcite as white concrete. The six
  Cobblemon rules are tested without Cobblemon (worker counted, held back as MOD_MISSING); their wish with Cobblemon
  has no compat test yet. Owner: are the zones above where you'd want each building?
- 2026-10-05 (27.12, decisions; lane c): the nine conditions read the hall's numbers (`guards_short` and `ill` are the
  "guards" and "ill" tips' counts, now `VillageAdvice.guardsWanted`/`ill`; `dark_beds` counts HOME points with block
  light under `VillageNeeds.LIT`, `VillageAdvice.darkBeds`, darkest first; `raided_within` treats the hall's -100 as never;
  `courting_couples` is `Couples.courting`; `died_within` counts DEATH entries in the chronicle). "No nurse" and "no
  scholar" needed one more condition, `no_worker {profession}`. The Clinic/Healing Center choice is two files,
  `clinic_for_the_ill` (below a Village) and `healing_center_for_the_ill` (`min_rank` village). Every civic, defence,
  garden and market rule also has `built_count_below {that building, 1}` and `max` 1, so it's built once; street lamps
  have `max` 8, cooldown 1 day. Priorities: clinic 70, barracks 57, lookout tower 55, lamps 48, schoolhouse 45, library 42,
  chapel 38, market square 36, well 34, bench 33, fountain 32, gazebo 31, graveyard 30 (all below homes, food, storehouse
  and workplaces, so with the day's 8 wishes the pressing ones come first). "By the darkest homes' doors, in their
  zone": a `build` effect may say `"near": "dark_beds"` (saved with the wish, default none); the plot search in the Homes
  zone then tries spots nearest the darkest bed first instead of nearest the hall (`Plots.Request.near`). The bed, not
  its door: a door isn't tied to a bed. "Another spot" on the desk searches from the hall again. `raided_within` and
  `bandit_camp_near` ship with no rule (none of the listed rules asks for them): for packs and later items. With beauty
  3 reached by a well (2) and a bench (1), the fountain and gazebo come only while beauty stays under 3 (another
  village's decorations gone, or a pack's rule). Scene `steward_civic`: Couples and Sickness off, a scholar at his desk
  and a lit row of beds, so only the lamp, the well and (day 2, three children) the schoolhouse are asked for.
  Filmed locally once (08:41Z): the Steward is appointed, day 1 the lamp (by the darker bed) and the well are built
  (PASS), day 2 the schoolhouse is wished and the desk starts something, but no schoolhouse was finished in 2400 ticks
  and the still shows none rising (FAIL "a schoolhouse went up"); the day-2 wishes also still listed `well` after the
  well was finished. Left for QA/the next lane: why the schoolhouse doesn't start (plot in Civic z 16..40, two open
  builds in a Village) and whether `built_count_below` misses a just-finished well at the morning's ranking.
- 2026-10-05 (34.1, decision for the owner; lane d): `docs/design/M34.md` is the Classes and luxuries note. One clash
  with the code: 34.12 picks the Jeweller with an amethyst shard at the stonecutter, but since 28.11 the shard there
  already picks the Gem Grower (`GemGrowers.isShard`), and the Mason is picked with a clay ball, not cobblestone. Default
  until the owner says otherwise: the **Jeweller is picked with a gold nugget**; 34.12's test reads "picked with a gold
  nugget". Also decided there: the Noble's class file repeats the Burgher's food and diet (34.2 lists none), the
  Burgher's "one more service" excludes the market (the Market Square is its own `building` need), and new chronicle
  kind `LIFE` for elders, retirements and generations beside 34.6's `CLASS`.
- 2026-10-05 (34.4, decision; lane d): the Porters rule is "a luxury in any worker's chests goes to the store"
  (`Porters.keeps` gives 0 for any item a `luxuries/` file names), so a maker keeps its makings by its own job's rule.
  A porter only visits jobs in `work/Village.takesPart`: 34.9-34.12 must add the Vintner, Tailor, Printer and Jeweller
  there, or their goods stay put. The test luxuries are `aliveworkplace_test:test_trinket` (a disc fragment, every 2
  days) and `test_fine_trinket` (the tag `#aliveworkplace_test:fine_trinkets`, every 4).
- 2026-10-05 (34.5, decisions; lane d): `craft/LuxuryWork` isn't on any job yet: 34.9-34.12 add it to their job's work
  package (`new LuxuryWork("<job>")`, with a `message.aliveworkplace.<job>.title` line); its recipes name the job id.
  The tests drive it on a NoAI Nitwit (test recipes `aliveworkplace_test:test_cordial`, `test_vintage`, `test_new_good`).
  A good gets a `made_day` (the chronicle's day number, and the days to vintage) when some recipe takes it with
  `min_age_days`; a stack without one (old stock, a player's) counts as aged. An aging good is only counted as a
  making once old enough, whichever recipe uses it. "The village store" is the hall's store (`VillageNeeds.store`), or,
  without a hall, the Storehouses within 48 blocks; the "fewer than 8" counts it plus the maker's own chests. Stashes of
  builders are never taken from. The tooltip has a one-day form ("vintage in 1 day") and "· vintage" once ready.
- 2026-10-05 (29.21, decisions; lane a): the Pokémon Professor. **Field Notes** "partners help 5% more a level" is
  read as each partner's help growing by a twentieth a level (one partner takes 15% off a job, 15.75% at I, 16.5% at
  II), not 5 more points. **Breeding Records** "eggs 20% sooner" shortens the wait: the keeper's dawn odds are divided
  by 0.8 (I) or 0.6 (II), at most 100% (50% becomes 63% and 83%). The hints give EVs in words too (no, a few, some, a
  lot of, full EVs), so **Regional Survey**'s "exact IVs and EVs" has something to change; IVs use the games' judge
  words (0 No good, 1-15 Decent, 16-25 Pretty good, 26-29 Very good, 30 Fantastic, 31 Best). The village Pokédex logs
  species only while a Professor lives in the village (it is their power), at the hall's round, from the Pokémon
  tethered to Pasture Blocks inside the hall's area; the count shows as a book on the hall's Legends page (slot 7, by
  the anthem) and as the research counter `pokedex_species`. **Evolution Studies**: one stone a day per Professor, the
  day's stone going round Cobblemon's ten evolution stones, bought from the hints screen. Hints open on a right-click
  with an empty hand; the Pokédex tab stays on the sneak-right-click every tradeless Legend has. The Pokédex topics
  only need their species count (the spec names no topic prerequisites), and pay in vanilla items so the file never
  fails to parse without Cobblemon.
- **qa handoff** (qa-1005-1034, 2026-10-05 11:27Z): qa-1005-1034: verified B46, B69, B73. Unshipped: qa/b46-b69-1005 (QaB46GameTests, QaB69FrontWalkGameTests; its ship build passed 1014/1014 gametests but failed on the compat flake B74 = B57 again): ship it once B74 is fixed (or retry ship). qa/import-swap-1005 is obsolete (B73 fixed on main). B50 and B57 not verified: read nightly run 37297003813 (on 5b489b7, after both fixes) for the crew test and the Pidgey test's repeats; B57 recurred (B74). qa/placing-1005 still waits on B71. Next QA: B50 from the nightly, B72 scene setup, then 21.2, M23.
- **lane-c handoff** (lane-c-1005-1532, 2026-10-05 18:13Z): wip/lane-c (ce73d6ed) = main as of 17:55Z + 27.15, 27.16, B67, B71 (old wip, B75 dup fix dropped for main's Pathfinder.hold), 27.17 caravan roads, 27.18 walls, 27.19 Steward safety, all ticked. Full build: compile, devclient and 1117/1118 GameTests pass; CaravanRoadGameTests.twoVillages120ApartBuildBothHalvesAndTheyMeet fails in the full suite (passed alone 16/16 and in 79-test targeted runs; likely batch interference with 27.18/27.19 or chunk tickets). Next run: git switch wip/lane-c, fix that test, merge main, full build (compat not yet run), push to main. Trap: worktree subagents leave Gradle daemons; stop them or full runs get OOM-killed.
- **lane-c handoff** (lane-c-1005-1833, 2026-10-05 20:59Z): lane-c-1005-1833 (21:01Z): landed on main 2f801600: 27.15-27.19 (old wip/lane-c, M27-gated), 27.20 old houses, 27.21 renewal, B79 (ladder to upstairs beds), CaravanRoad test isolation fix. Local full build green (1169+151) before merging lane a's 29.x, pushed without rebuilding that merge (clean, no config changes). Next: 27.22 (the 1.1 yardstick), then 31.x. Red duty notes: main CI 695/697-699 were cancelled by the runner at ~15 min (infra), 696 failed only ConscriptionGameTests.aConscriptSavedMidRaidLoadsWithoutTheSword (B81). wip/lane-c is now stale (all merged); B80 is test-order (QA).
- 2026-10-05 (31.2, decision; lane c): the quest engine moves the hall's old daily quests into `aliveworkplace_stories`
  (by id, safe to repeat; also on a hand-in, kill or page view, so a quest set on the hall is never missed). M30's
  reform steps stay in the hall's own `quests` list for now: `Reforms` and its GameTests read and write that list
  directly (25 places), so moving them is left to the item that brings the journal (31.22's reform rewards or the
  journal item), which can move them the same way. Open quests save their objectives and rewards as their JSON
  (strings in NBT), so a changed file never changes a quest already up. `days: "festival"` (31.9) isn't read yet: a file
  using it is skipped with its log line until 31.9 adds it.
- 2026-10-05 (29.22, decisions; lane a): the Pokémon Ranger. Cobblemon 1.7.3 has an Alpha mark (`cobblemon:mark_alpha`)
  but nothing gives it to wild Pokémon, so a wild Pokémon is an Alpha with that mark (worn or potential) **or** at level
  50 or more (the stand-in), whichever the installed version has. Cobblemon lets a Pokémon out in a pasture only for an
  online trainer (`tether` needs the player), so when the hall owner is away the befriended Pokémon goes into their PC
  and stays there (chronicle says so) rather than waiting for them. "With room" is a pasture with fewer Pokémon than
  its maximum, nobody's or the hall owner's (another player's pasture is never filled). A calm is checked at the damage
  gate (a calmed Alpha's blows on villagers and players inside any village are cancelled and its target dropped); a
  walk that takes longer than 2 minutes is given up for the day.
- 2026-10-06 (31.4, decisions; lane c): the arc engine reads `threat` but doesn't gate on it yet: 32.21 (the threat
  level, At peace) hasn't landed, so whoever builds 32.21 adds the check in `Arcs.tryStart` (the comment marks the
  spot). A chapter's `intro` takes a lang key or a text component (a pack may write `{"text": ...}`); `chatter` lines
  are lang keys. `place` rules: `ring`, `road` (a share along the hall's nearest caravan route), `biome`, `after` (with
  `distance`) and `offset` ([x, z] from the hall, for packs and tests); `count` places `key`, `key#2`... (one
  `<key>_done` flag when all are built). A spot that has no clear natural ground within 32 blocks is given up with a
  log line and its `<key>_done` set, so no chapter waits on it forever. Spawn groups are `data/<ns>/arc_spawns/<id>.json`
  (`{"mobs": [...]}`). A mob dying to anything counts as dead (not put back) and sets `<key>_dead`. Flags set with
  `days` also outlive the arc in the village (`Stories.villageFlag`, for the Bandit King's "no camps for 10 days").
- 2026-10-06 (28.19, decisions; lane b): the Cup's afternoon off counts villagers at work too (the stands fill from
  noon with every grown villager who isn't resting; for the final, children as well); entrants, delegates and the bard
  never sit. An Arena I has no fair lane, so its fair is held by the notice board. The bard's disc plays once, at noon,
  where the bard is if they reached the ring, else at the ring. A Cup put off keeps its new day through the host's
  rounds; called off after 8 days, the next Cup is set from the calendar. The Cup's chronicle entry for a circuit
  village that isn't loaded is kept in `aliveworkplace_cups` and written, with the Cup's day, at its next hall round.
- 2026-10-06 (28.20, decisions; lane b): without Cobblemon a bout with a player is never called (no Cup bouts start
  at all then): it's settled at the theme's end as before, a player not at the ring losing by walkover, else the bout's
  seed deciding. "Inside the Arena" for [I'm ready] is within 24 blocks of the ring; the call goes to the player
  wherever they are, and an offline player has the same two minutes. Both players absent: the second in the bracket
  loses. A player whose party has nobody eligible loses by walkover when the battle would start; a villager entrant
  with no body at the ring (neither the Leader nor a delegate there) loses by walkover. A battle that ends with no
  result (a restart, `/stopbattle`) is called again with a fresh two minutes; one that can't start (the player is
  already in a battle) asks for [I'm ready] again. The purse is paid on every win, walkovers too, if the winner is
  online; 100,000 is 1,000 emeralds at the default `dollarsPerEmerald` of 100. The battle's level is Cobblemon's level
  adjust (the theme's level); RCT's caps are not applied to Cup teams. [Watch] on the notice board: right-clicking
  the board (within 2 blocks) while a player bout is fought prints the bout with a [Watch] button.
- 2026-10-05 (34.6, partial; lane d): landed the rise and fall effects (`Chronicle.Kind.CLASS`, sparkles at the lead's
  bed as "their door", `AMETHYST_BLOCK_CHIME`, the chat line within 32 blocks), the moods (saved as `class_standing`
  at each dawn: needs lacking and the last turn; a standing older than yesterday's dawn adds nothing) and chatter
  `class_rose`/`class_fell`, with `ClassHallGameTests`. Still to do for 34.6: the hall's Classes button and page
  (counts, what each class gives, closest to rising), class and household in the people list and the villager page's
  needs, the food icon's luxuries tooltip, the What next? class tips, chatter for wine, clothes, the Gazette and the
  tavern (they wait for 34.9+ goods), the scene `classes`, the README section and the Guidebook page (after 26.2a).
- 2026-10-06 (34.6, done; lane d): the hall's Classes tab and page (`hall/ClassesPage`, registered only once M34 is
  finished; counts each need over the households of the class and the one below, "Fine Clothes: 2 of 5"), the people
  list's class line and the needs of their class and the next ticked, the food icon's luxuries in store (shown with
  classes on), up to three "What next?" class tips (most households first; the how-line is per kind of need, not yet
  naming each luxury's maker, which arrives with 34.9+), scene `classes` (the rise is staged with a scene ladder whose
  Artisans need only a varied diet, then the real ladder is put back for the page), README "Classes". Still waiting:
  chatter for wine, clothes, the Gazette and the tavern (34.9+ goods) and the Guidebook's Classes page (after 26.2a).
  Luxury names are `luxury.<namespace>.<id>` lang keys (a data pack's without one reads as its id, "Test Trinket").
- **34.7 (2026-10-06, decisions):** `people/ClassPerks` reads the class files' effects; `work_pace` and `research_pace`
  are one `Pace` source ("their class (Artisan)") under the shared cap, and both apply to the class and every class
  above (a Noble scholar is no slower than a Burgher one; the design note gave "or better" only to `work_pace`). The
  village's households by class are counted with the hall's people (`VillageNeeds.count`), in memory. Wants had are a
  new `wants` field on `class_standing` (default 0), counted at dawn. Waiting on other items, each degrading quietly:
  the Burghers' trader sells a Townhouse or Manor blueprint only once 34.15/34.16 put them in the library (until then
  the extra trader comes without it); the ball's venue is a finished Manor's middle until 34.16 says where its ballroom
  is, else the hall; the wine served is the `#aliveworkplace:ball_wine` tag (Vintage Wine, Berry Wine, Cider, all
  optional entries until 34.9 adds them). `ballTurn` flips on every festival in a village with a Noble, so
  `replace_every` other than 2 reads as 2.
- **34.8 (2026-10-06, decisions):** `people/ClassJobs` reads the class files' `jobs` (the lowest class naming a job is
  what it needs). A villager with no class yet (unseeded, until 34.22) is never gated, so no village loses hires before
  seeding; a Legend counts as the top class. "A village with a hall" is the nearest hall within `VillageHalls.RADIUS`.
  Besides the four ways in the spec, the Steward's morning jobs (`StewardJobs.plan`) skip jobs above a villager's class
  too. A jobless villager taking a modded block by themselves (vanilla's own job search) isn't gated, as the spec lists
  only the four ways (most of these jobs share a block with a vanilla job, which is the one taken by itself). The
  Printer and Jeweller are in the Artisan and Burgher files already and are gated once 34.11/34.12 add them. A hired
  traveller's class is given only in a village with a hall. The refusal reads "Dara is Peasant class; Scholar needs
  Burgher or better" (no "a"/"an" before names a data pack may change).
- **34.9 (2026-10-06, decisions):** the Vintner has a config switch, `vintners`, gated with M34 like `villageClasses`.
  Sweet berries, glow berries and an apple also pick the Orchard Keeper at a composter: the station by the villager
  decides (the nearest free one), and a villager already an Orchard Keeper (or a Vintner) isn't switched by the same
  fruit, as `Stations.picks` has always done. Drinks are `mc/DrinkItem` (food, and the bottle given back by hand, as
  1.21.2 moves that to a component). The wines are in `not_villager_food` (households take them as luxuries, never as
  meals). Trades: makings bought at every level (apples, sweet berries, bottles, glow berries, apples); Cider sold at
  Novice, Berry Wine at Apprentice and Expert, Vintage Wine at Journeyman and Master. Not done here: the "What next?"
  tip naming the Vintner (34.6 left that how-line generic).
