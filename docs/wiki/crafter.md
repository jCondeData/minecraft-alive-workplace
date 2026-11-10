# Job chains (the retired Crafter)

Every job that crafts for the builders now works through whole recipe chains of its own kind: the Carpenter saws logs
into planks, cuts the planks into slabs, then makes the barrel or chair the builder was waiting for; the Mason fires
clay into bricks, lays them into blocks and cuts the stairs. What no job in the village makes stays unmade and is named
on the builder's shortage line. This replaced the Crafter, a separate job that made everything (37.14, retired by
37.14a, owner 2026-10-21: "each job should craft what the builder or other villagers need when they need it"). Part of
Furnished homes and modded storage (2.1), behind its expansion gate (`Expansions.M37`).

Roadmap items: 37.14, 37.14a, 37.15, 37.16, 37.16a, B396, B426, B696, B721, B760, B773, B778, B916, B920, B931, B1018, B1111, B1112

## 1. What a player sees

A builder short of three barrels with only oak logs in the chest: the village's Carpenter fetches the logs, and at the
bench the line above their head says "making 3 Barrels (step 1 of 3)", then step 2 (the slabs), then step 3; the barrels
go into the builder's chest and the build carries on. A Mason with a furnace by the stonecutter turns clay balls into
brick stairs the same way (fired, laid, cut, a coal burnt for every eight fired). The Librarian makes the bookish parts,
the Tinkerer the metal ones (raw iron fired into ingots, then bars or a lantern), the Toolsmith tools, the
Leatherworker leather goods (and meanwhile candles), the Shepherd wool, carpets, beds and banners at his loom
(since 46.2), the Florist pots, dyes and moss carpet (since 46.3), the Cleric brewing stands, ender chests and
beacons at his brewing stand (since 46.4), and, with CobbleFurnies, the
Furnicrafter its furniture (the planks and sticks first, then the piece). A big order is made a bagful at a time until
there's enough. Which job makes what is each job's own list since 46.1 ([Each job's own list](job-lists.md)).

A job only crafts from what no open build needs. When two builds share a chest or the village Storehouse, the logs
stocked for one are never turned into planks for the other: the Carpenter uses only what's left over once every other
build's remaining list is counted, so neither build ends up waiting for a log that went into the other's planks (B696).

What a job makes for a builder goes where that builder looks for materials: the chests by his Blueprint Table, or,
when there are none, the village Storehouse (else another village-mate's chests he takes from). A builder with no
chest anywhere he looks isn't crafted for, so nothing made for him is left lying by the crafter's bench (B721).
A builder who has a chest of his own still gets it there, never in the Storehouse, and two chestless builders each get
theirs.
A job is only taken when what it makes fits in those chests: counting a slot the job's own ingredients empty (the last
two stone bricks in a slot leave room for the four slabs cut from them), and leaving out the room another crafter's
job already counts on, so two crafters never both count the same empty slot. With the chests by his table full, it
goes to another store the builder also takes from (another Storehouse in the village) that has room. With no room
anywhere, the crafter waits and looks again later, and the builder's line says so under "Short of": "No room to bring
Chest, Ladder: the chests he takes from are full", never "Nobody here makes" (B1111, B1112). Making some room in his
Storehouse is enough to get things made again. If they fill up while he carries the job to them, he keeps it in his bag and tries again every two
seconds (for up to two minutes, then takes it home), so nothing is dropped by a full Storehouse and nothing is made
twice (B760). He only takes it home if the chests by his own bench have room for it; with none there, or full ones,
he keeps carrying it and trying the Storehouse, so it never ends up on the ground by his bench (B773).
What he took home isn't lost to the builder: while a builder still waits for something that lies in the chests by
the crafter's own bench (and those aren't chests the builder takes from himself), the crafter carries it on to the
builder's chests or the Storehouse as soon as they have room, before he makes any more of it (B778).

When a job could make a part but a raw material is short, nothing is taken and the builder's shortage line says so,
under "Short of": "Can't be made yet: short of 4 Oak Logs to make 3 Barrels" (items named in the plural after a count above one, B426, for items that have a plural in the language file; every wood's logs and stems have one, "short of 2 Crimson Stems", and so do the countable things crafters run short of, such as sticks, nuggets, ingots, clay balls, feathers, diamonds, bricks, eggs and buckets, B920). When no job in the village makes a crafted
part at all (oak stairs in a village with a Mason but no Carpenter), the line names the job to hire: "Hire a
Carpenter to make Oak Stairs" (46.1); only what no job makes says "Nobody here makes".
A Mason's kiln counts its fuel as a raw material too: twelve clay balls and one coal for three brick stairs (a coal
for every eight fired) is "short of 1 Coal to make 3 Brick Stairs", and nothing is taken (B352).
Coal and charcoal count together: one coal and one charcoal are the two fuels twelve clay needs, and the Mason
takes and burns both (B396). The same goes for a Tinkerer's forge: six raw iron for iron bars and no coal is "short
of 1 Coal", not "Nobody here makes: Iron Bars", and the ore stays in the chest (B408).
The line names what the chest is short of, not some other way to make it: a Librarian with two leather where a
bookshelf's books need three says "short of 1 Leather to make 1 Bookshelf", not "4 Rabbit Hide". It goes further down
only when that uses something that's there (planks still to be sawn from the logs at hand read "short of 1 Oak Log")
(B409). The same holds between two recipes for one thing: a Tinkerer with five raw iron and a coal for iron bars says
"short of 1 Raw Iron", and with coal and no ore at all "short of 6 Raw Iron", never iron ore a player only gets with
Silk Touch; gold and copper read the same (B489).

The Crafter is gone for new villagers: the Crafter's Workbench can't be crafted, isn't in the creative tab or any trade,
and no villager takes one. A Crafter on an existing world becomes jobless the first moment it's loaded (what it carried
drops at its feet) and soon takes another job. A Crafter's Workbench already placed stays a block. The Crafter's
Workshop (37.16) left with it (37.16a): the Blueprint Table no longer offers it, the Steward no longer plans it and
villages no longer grow it; workshops already built or grown stay as plain buildings.

## 2. How it works

`CrafterWork` is the shared shift of every crafting job (the Carpenter's own, and `MasonWork`, `ScribeWork` for the
Librarian, `TinkererWork`, `ToolsmithWork`, `DyerWork`, `FurnicrafterWork`). With Milestone 37 open (`chains()`, gated by
`Expansions.isOpen("M37")`), each of those:

- plans with `Crafting.chain(level, kind, ...)`: its own recipes (the crafting table's; the stonecutter's, and the
  kiln's with a furnace by it, for the Mason; the blast furnace's for the Tinkerer; CobbleFurnies' Furni Crafter's files
  for the Furnicrafter), `Crafting.CHAIN_DEPTH` (3) steps down, a repeated recipe merged into one step;
- tries the whole count, then half, down to one, so a big order is made a bagful at a time (12 stacks of makings);
- when nothing fits, notes the chain's raw materials short (`Crafting.lacking`: of several recipes for one thing, the
  one closest to what's there, by `Crafting.shortRank`, never one that only unpacks a storage block, B489; or the Furnicrafter's own; the Mason's
  kiln and the Tinkerer's forge add their coal, from `Crafting.shortfall`'s fired steps (`CrafterWork.fuelShort`), and
  name it when the clay or ore is all there) for that
  build site and item in `JobShortages`; when it can make it, or another job has claimed it, notes it as covered;
- makes the plan's steps one after another at the workstation (each in its own time), saving the job on the villager
  after every phase change and step (`crafter_job`); fuel a plan takes that no step uses is burnt at the end; a plan's
  last change is its own step too (the Mason's glass fired from sand, the dyer's concrete hardened from the powder it
  mixed); a plan whose steps still don't add up to what it makes is made all at once as before;
- on its next shift after a restart or reload picks the saved job up where it was (the makings already made are in the
  saved bag; the claim on the request is taken again);
- lets the saved job go when its workstation has been gone for 600 ticks (`CrafterWork.benchlessTick`, every villager's
  AI step, B353): the claim released, `crafter_job` dropped and the bag emptied into the asker's chests (at the
  villager's feet when those are gone or full). A bench put back or moved within that time carries the job on. Another
  job's workstation doesn't count (B1009): a novice who loses his bench is made jobless by vanilla and may take a free
  lectern, so the saved job names the profession that took it (`profession`, a registry id; empty in older saves, which
  are stamped with his profession at the first look after loading, jobless too, B1018; his work on it stamps it
  again, so one who was jobless when saved and gets his bench back in time carries on) and is let go all the same once
  he's in another job that doesn't craft (a Scholar or Teacher): the logs go back and another Carpenter takes the
  request. Every crafting job picks up a saved job at its own workstation, so one who became a Librarian finishes the
  barrels and takes them to the builder himself.

The builder's status (`BuilderStatusSync.supplyLines`) adds `JobShortages.line` under "Short of" while it waits:
first up to two chains short of a raw material, else the crafted parts (something a crafting table, stonecutter,
furnace or furniture bench makes) that no job has looked at in the last 600 ticks, once the site has waited that long.
The cook, the luxury makers, the Weaponsmith and the Fletcher keep their old one-plan way. Their status lines name what they make in the plural after a count above one: "making 8 Spectral Arrows" (B916; bows, crossbows, shears and the stone and iron tools and swords have plurals too), and so do the cook's pies, cookies, cakes and stews ("cooking 2 Pumpkin Pies"). His Smoker dishes too: "cooking 4 Cooked Porkchops" and "cooking 8 Bowls of Beetroot Soup" (B931); the meats and fish that read as a mass ("4 Cooked Chicken", "8 Steak") keep their one form.

The retired Crafter: `Crafters.tick` (every villager's AI step, from `VillagerMixin`) finds a villager whose profession is
`aliveworkplace:crafter` and makes it jobless: the workbench's POI ticket let go, the `crafter_job` dropped (and its claim
released), the bag's contents dropped where it stands, trades, level and experience cleared, its brain rebuilt. The
profession can't acquire a workbench any more, and the workbench left the `acquirable_job_site` tag.

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| (none of its own) | | The chain way follows Milestone 37's gate: before 2.1 every job crafts as it did before. The `crafters` switch (37.14) is gone with the Crafter; an old config file that has it loads fine and the key is ignored. |

## 4. Saved data

- `crafter_job` on the villager (since 37.14, now on every crafting job's villager): the job in hand (who it's for, the
  chain's steps, what it takes, the phase and the next step). None: no job in hand, as on every older save. A job
  that names an item no loaded mod has any more (its target, a step's makings or what it takes; a mod taken out of
  the pack) doesn't load and the villager has no job in hand; the bag still loads whole, and with no job its makings
  go back into the chest by the workstation (`QaCrafterJobRemovedMod1102GameTests`,
  `QaAttachmentRemovedModId1102GameTests`).
- The bag (`builder_bag`) and items made (`items_crafted`) are the ones every crafting job already saves.
- Nothing new is saved: the shortage notes are kept in memory and renewed every look.

## 5. Items, blocks, jobs, commands

- Block: `aliveworkplace:crafters_workbench` stays registered (no recipe, not in the creative tab); job
  `aliveworkplace:crafter` stays registered so old saves load; nobody takes it.
- Blueprint `aliveworkplace:crafters_workshop` and the village pieces `aliveworkplace:village/<style>_crafters_workshop`
  still load by id; not offered, planned or grown.
- Data: `data/<namespace>/crafter_benches/*.json` still read (CobbleFurnies' Furni Crafter's recipe type), used by
  `Crafting.chain(level, target, count, usable, types)`.
- Text: `message.aliveworkplace.job_chain.*` (the step, the lack line, "Can't be made yet", "Nobody here makes").

## 6. Decisions

- 37.14 (owner, 2026-10-20): a new job that makes what others need, through whole chains.
- 37.14a (owner, 2026-10-21): the Crafter is retired and its way of working moved into the jobs that already craft for
  builders, each for its own kind; what no job covers stays unmade and is said on the builder's line.
- 37.14a: the chain way is behind Milestone 37's gate like the rest of 2.1, so the live server's jobs don't change
  before the release; the Crafter's retirement isn't gated (the Crafter never shipped outside the gate; a stray one on a
  test world is made jobless whatever the gate says).
- 37.14a: the job is saved in the attachment the Crafter used (`crafter_job`), so no new saved field; a villager
  without it has no job in hand.
- 37.14a: "nobody here makes" waits 600 ticks after the builder starts waiting (the jobs look every 5 seconds), and
  only names crafted parts, so a missing log is left to the "Short of" line above.
- 37.14a: the other-workers requests the Crafter answered (a miner's ladders) aren't moved into the jobs; a question
  for the owner is in the ROADMAP notes.
- 37.16a: the Crafter's Workshop rule (`workplace_crafters_workshop`) is deleted; its condition
  `builders_short_of_parts` stays (a pack's rule may name it) and never holds, saying why.

- B778 (lane-a-1101-1823): what a job took home after the long wait (B773) is carried on to the builder once his
  chests or the Storehouse have room, as a job with nothing to make (`CrafterWork.carryOn`: up to what he still
  misses, from the chests by the crafter's own bench that the builder doesn't already take from). Chosen over the two
  other ways: carrying it for good (B773's no-room branch) would keep the crafter from all other work for as long as
  the Storehouse stays full, and letting builders take from a crafter's chests (`Village.takesPart`) would change
  every place that list is used (the job board, the hauler golems, enchanting, workplace rules) and let builders take
  the crafter's own makings. Anything of the asked item in those chests is carried on, whoever put it there: they are
  the crafter's, and nothing else takes from them.

## 7. Known limits

- What's short is found along the first way down the recipes: with oak and spruce logs both short it names oak.
- A job only looks at the parts of its own kind, so a part two jobs could make in turns (a lantern from a Tinkerer's
  ingots and a Carpenter's torch) is made only if one job's own recipes go all the way down.
- The showcase scene is filmed on GitHub, not in this session.

## 8. Proof

- `JobChainGameTests.theCarpenterMakesBarrelsFromLogsInThreeSteps`: the Carpenter's three-step chain, its "step 2 of 3"
  line, and the build finishing.
- `JobChainGameTests.theMasonFiresLaysAndCutsBrickStairsFromClay`: a Mason's kiln chain, the fuel burnt.
- `JobChainGameTests.aShortRawMaterialIsNamedOnTheBuildersLineAndNothingIsTaken`: a raw material short, said on the
  builder's line, nothing taken.
- `QaLaneD1104GameTests.qaB426TheRealLackReadsOneAndTwoLogsRight` (QA, B426): from the real lack, "short of 1 Oak Log
  to make 4 Oak Planks", "short of 2 Oak Logs to make 8 Oak Planks" and "short of 2 Oak Logs to make 1 Barrel"
  (and since B920's fix two jungle, cherry, crimson or warped logs read in the plural too:
  `QaLaneD1104GameTests.qaB426EveryWoodsLogsAreNamedInThePlural`).
- `QaLaneC1104eGameTests` (QA, B878/B920/B931): 1 Stick, Egg or Cherry Log reads in the singular and 0, 2, 8 and 64
  in the plural; "short of 2 Sticks, 1 Copper Ingot to make 1 Iron Pickaxe" and "short of 1 Crimson Stem to make 4
  Crimson Planks"; every `plural.aliveworkplace.*` key in the language file names a real item or creature, isn't empty
  and differs from the one-form (but for names already plural, "Fine Clothes", "Drowned"); every countable input and
  output of the mod's luxury recipes has a plural.
- `QaCrafterFurnitureBugTests.b352AMasonShortOfCoalSaysSoAndTakesNothing`: the kiln a coal short names the coal, not
  the bricks, and takes nothing (B352).
- `QaJobChainBugGameTests.aTinkererWithOreButNoCoalNamesTheCoalAndTakesNothing`: the forge with the raw iron but no
  coal names the coal and takes nothing (B408).
- `QaTinkererShortGameTests` (5, QA from B408): the line reads exactly "Can't be made yet: short of 1 Coal to make 3
  Iron Bars" the whole minute and never "Nobody here makes"; coal and no ore names the iron; neither names both; six raw
  iron on one charcoal make the bars; with no Tinkerer it is "Nobody here makes: Iron Bars" and the ore stays.
- `QaTinkererShortBugGameTests` (4, B489): five raw iron and a coal read "short of 1 Raw Iron to make 3 Iron Bars",
  coal alone "short of 6 Raw Iron", one raw gold for pressure plates "short of 5 Raw Gold", all taking nothing; read
  off the forge's recipes, five raw copper for a copper block lack 4 raw copper, nine nuggets from nothing 1 raw iron,
  and a raw iron block is unpacked rather than named.
- `QaJobChainBugGameTests.aLibrarianOneLeatherShortOfTheLastShelfTakesNothingAndNamesIt`: two leather for the last
  bookshelf's three books names "1 Leather", not rabbit hide, and takes nothing (B409).
- `QaJobChainFixesBugTests.aMasonWithOneCoalAndOneCharcoalMakesTheStairs`: one coal and one charcoal make the
  stairs (B396).
- `MasonMixedFuelGameTests.aMasonWithTwoCharcoalMakesTheStairs`, `aMasonWithOneCoalForTwelveClayIsShortOfOneCoal`,
  `aMixedFuelJobReloadedMidWayFinishes`: charcoal alone, a real shortage counted from both kinds, and a mixed-fuel job
  saved and loaded mid-way (B396).
- `QaMixedFuelGameTests` (QA): a mix takes only the fuel it needs, coal first (one coal and five charcoal for twelve
  clay leave four charcoal), a spare coal stays, no fuel at all reads "short of 2 Coal", glass fired on charcoal (from sand
  or crushed cobblestone), glass with no fuel names "1 Coal", and a Tinkerer's forge burning two coal and a charcoal for
  three cauldrons (B396).
- `QaCrafterFurnitureBugTests.b353TheCarpentersBenchBrokenMidChainLosesNothing`: the bench broken at step 2 and not
  put back: the job let go, the wood back in the builder's chest (B353).
- `QaJobChainLetGoGameTests.aBenchBrokenBeforeAnythingWasCarriedLetsTheJobGoCleanly`: the bench broken while the
  Carpenter is still fetching, empty-handed: let go after half a minute, nothing taken, and the bench put back finishes
  the build (B353).
- `QaCrafterNewJob1107BugTests.qaANewCarpenterWhoTakesAnotherJobLetsTheRequestAndTheWoodGo`: a 0-XP Carpenter carrying
  the builder's logs loses his bench and may take a free lectern: the job and its claim let go, the bag emptied (B1009).
- `QaBagNewJob1107cGameTests` (5, B1007-B1009 QA): a novice Weaponsmith with your sword made a Librarian (after a
  reload) or a Scholar the moment vanilla takes his job: one sword and the 10 iron in a chest or on the ground; a novice
  Carpenter with the builder's logs made a Scholar after a reload: the job let go, a second Carpenter takes the request
  before the claim would run out, the barrels made once; made a Librarian: the barrels still made once, no log left in a
  bag; a job from an older save (no `profession`) with no lectern near: still let go, nothing lost.
- `QaBagNewJob1107cBugTests.qaACarpenterFromAnOldSaveWhoBecomesAScholarFreesTheRequest1107c`: the same with the job
  loaded from a save made before B1009 (no profession in it): he becomes a Scholar and still lets the request and the
  logs go (B1018).
- `QaOldSaveBench1107eGameTests` (3, B1018 and B353 QA): a novice Carpenter whose job was loaded from a save made
  before B1009 finishes the builder's three barrels himself when his bench stands (the job stamped
  `aliveworkplace:carpenter`); one saved while jobless (the bench broken first, so the job is stamped `minecraft:none`)
  whose bench is put back 560 ticks after the load carries on with the job and makes the barrels once, as from a newer
  save: his work on the job stamps it again with the job he holds then.
- `QaOldSaveCrafter1107cGameTests` (8, QA, B1018): an old save's job on a jobless novice, a novice, an experienced Carpenter
  with his bench, and after another reload carries on when the bench is put back (early, late, or moved); a new save's
  job the same, as controls.
- `JobChainGameTests.whatNoJobMakesIsNamedOnTheBuildersLine`: "Nobody here makes".
- `JobChainGameTests.aChainHalfMadeCarriesOnAfterAReload`: save and reload mid-chain.
- `QaStewardMaterials1031GameTests.aCarpenterNeverCraftsAwayLogsAnotherOpenBuildNeeds`: two builds sharing a chest,
  the logs there all B's: the Carpenter leaves them, and B finishes (B696);
  `aCarpenterStillCraftsFromALogNoOpenBuildNeeds`: one log more than B needs is crafted into A's planks, and both
  finish.
- `QaCarpenterReserve1031GameTests.aCarpenterLeavesTheStorehouseLogsAnotherBuildNeeds`: the same with B's logs in the
  village Storehouse, out of both benches' reach: the Carpenter leaves them and B finishes (B696);
  `aCarpenterStillCraftsTheStorehousesSpareLog`: one log more there is crafted and brought to A's chest, and both finish.
- `QaCarpenterChestlessBug1031Tests.aCarpentersPlanksForABuilderWithNoChestReachHim`: the same spare log for a builder
  with no chest by his table: the planks go into the Storehouse, never onto the ground, and his row is built (B721).
- `QaCarpenterStorehouse1101GameTests`: the same case counted at every tick, a log as 4 planks, with nothing lost or
  made twice (`aChestlessBuildersPlanksAddUpExactlyThroughTheStorehouse`); a builder with his own chest gets them in it,
  never in the Storehouse (`aBuilderWithHisOwnChestStillGetsThePlanksInIt`); two chestless builders both build
  (`twoChestlessBuildersBothGetTheirPlanks`); the Storehouse chest broken mid-delivery loses no plank
  (`theStorehouseChestBrokenMidDeliveryLosesNoPlanks`); no Storehouse and only the Carpenter's own chest leaves nothing
  lying (`noStorehouseAndNoChestMeansNoPlanksLeftLying`); a save and reload on the way still delivers to the Storehouse,
  once (`reloadedOnTheWayToTheStorehouseDeliversOnce`) (B721).
- `QaCarpenterFullStorehouseBug1101Tests.aFullStorehouseNeverLeavesThePlanksOnTheGround`: with the Storehouse full, no
  log is crafted and no plank lies on the ground (B760); `CarpenterFullStorehouseB760GameTests`:
  `aFullStorehouseThatFreesUpGetsThePlanksThen` (one log used once there's room, the row built) and
  `aStorehouseFilledOnTheWayIsWaitedFor` (held in the bag, put in once room comes back).
- `QaStalledCrafting1109bBugTests`: a builder by a full Storehouse gets his stone brick slabs cut (the last bricks
  free their slot) and the line names what has no room ("No room to bring Chest, Ladder, Spruce Door"), never "Nobody
  here makes" (B1111, B1112); `QaStalledCrafting1109bGameTests`: with room the Mason and the Carpenter make the slabs,
  chest, ladder and spruce door, also after a save and reload; `CraftersFullStoreGameTests`: the freed slot counts once
  (not twice for two crafters, not for part of a stack), the full line's text, and slabs cut into a second Storehouse
  with room when the one by the table is full.
- `QaVillageEconomySaves1101BugTests.aStorehouseThatStaysFullAfterHeSetOffLeavesNoPlanksLying`: a Storehouse that fills
  while he carries the planks and stays full never leaves them on the ground, 3200 ticks on (B773).
- `QaCarpenterFullStore1101cBugTests.planksTakenHomeStillReachTheBuilderWhoAsked`: planks taken home after the long
  wait reach the chestless builder once a Storehouse slot frees, none on the ground and the wood adding up (B778);
  `QaCarpenterHomePlanks1101GameTests.reloadedWhileCarryingHomePlanksOnStillFeedsTheBuilder`: the same with a save and
  reload of the Carpenter on his way, the row built and no job left saved.
- `JobChainGameTests.anOldSavesCrafterLoadsJobless`: an old save's Crafter loads clean and jobless.
- `JobChainGameTests.theCarpenterMakesTheSlabsTheBuilderNeeds`, `theCarpenterFetchesTheOneLogItNeeds`,
  `oneCarpenterServesTwoBuilders`: one step, exactly one log, two askers.
- `JobChainGameTests.theLackLineNamesEveryShortThingWithACommaBetween`, `beingShortOfJustOneThingIsStillNamed`: the
  lack line.
- `JobChainGameTests.aBenchsRecipesOnlyWhileTheBenchIsThere`, `theCobbleFurniesBenchIsIgnoredWithoutCobbleFurnies`: the
  bench files.
- `CrafterWorkshopGameTests.theWorkshopIsNoLongerOfferedButStillLoads`, `theStewardNoLongerPlansAWorkshop`,
  `villagesNoLongerGrowTheWorkshop`, `aSiteAtTheVillagesEdgeCountsOneBeyondItDoesnt`: 37.16a.
- `CrafterFurnitureCompatTests.theCarpenterMakesHandcraftedChairsFromLogsForTheBuilder`: a chair from logs through the
  whole chain, step by step (compat suite).
- `FurnicrafterCompatTests.theFurnicrafterMakesAWaitingBuildersPieceFromLogs`: a Furnicrafter piece for a waiting
  builder, planks first (compat suite).
- `CrafterFurnitureCompatTests.theFurniCraftersRecipesOnlyWhileItStandsInTheWorkshop`,
  `aBenchFileForAModThatIsntInstalledIsIgnored`: the bench files with the pack (compat suite).

Showcase scenes: `job_chains`, `crafter_furniture`
