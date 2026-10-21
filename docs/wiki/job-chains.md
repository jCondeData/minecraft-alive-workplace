# Job chains

The jobs that craft for the builders make whole recipe chains: a Carpenter saws logs into planks, cuts the planks into
slabs, then makes the barrel or chair a builder is waiting for. This took over from the Crafter, who is retired (owner,
2026-10-21: "each job should craft what the builder or other villagers need when they need it"). Part of Furnished
homes and modded storage (2.1), behind its expansion gate (`Expansions.M37`) and the `crafters` switch.

Roadmap items: 37.14, 37.15, 37.16, 37.14a, 37.16a

## 1. What a player sees

When a builder nearby is waiting for something a job makes, that job fetches the makings from the builder's chests and
the village's stores, walks to its workstation and makes it one step after another. The line above its head says
"making 24 Oak Planks (step 1 of 3)", then the slabs, then the barrels. It carries the result to the builder's chests
and the build carries on. A big order is made a bagful at a time until there's enough. Each job makes its own kind of
thing:

- the Carpenter: woodwork (logs to planks to slabs to doors, fences, barrels, and the furniture mods' crafting-table
  pieces, such as Handcrafted's chairs);
- the Mason: stone (cut at the stonecutter, and fired in the furnace beside it: cobblestone into stone into stone brick
  stairs);
- the Librarian's scribe, the Tinkerer, the Toolsmith and the Dyer: their own goods, as before, now planned all the way
  down;
- the Furnicrafter: CobbleFurnies' pieces at its Furni Crafter, with their makings sawn from logs first.

When something can't be made, the job takes nothing and says what's short: "short of 1 Oak Log to make 3 Barrel". The
builder's own line says it too, after what it needs ("needs 3 Barrel · short of 1 Oak Log to make 3 Barrel"). What no
job makes stays in the builder's "needs" line, as before. After the builders, the jobs fill the Storehouse's stock
orders and a Wonder's goods, then what any other worker nearby is waiting for (a miner's ladders).

Several jobs fill one builder's list side by side: a Market Stall's woodwork from the Carpenter, its red wool from the
Dyer.

The Crafter is retired. No villager takes a Crafter's Workbench any more: it's no longer crafted, sold or in the
creative tab, the Crafter's Workshop has left the Blueprint Table, the Steward's plans and village generation. On a
server that had Crafters, each becomes jobless the first time they move, and what they carried drops at their feet.
Workshops already built stay as plain buildings.

## 2. How it works

Each look (every 5 seconds while there's nothing to do) the job goes down the asks in order: builders within the
village whose site is waiting for materials (their missing list, biggest first), the Storehouse's stock orders and a
Wonder's goods, then the requests other workers post. Each is planned with the game's own recipes of that job's kind
(`Chains.plan`), so modded recipes count: 3 steps down (a chair from slabs from planks from logs), with repeated steps
merged. Claims are shared, so two jobs never make the same thing for the same asker.

The whole plan is checked against the stock before anything is taken, so a short raw material wastes nothing. To say
what's short, the plan is worked out again noting each raw material that runs out, along the first way down the recipes.
The line is noted for that builder for 2 minutes (or until a job finds the makings) and shown in the builder's line.

What may be used: from a builder's own chests and their village-mates' stashes everything the rest of that build
doesn't need; from another builder's chests only what their builds can spare. Never a chest outside the village. A
bag's worth at a time: at most 12 stacks of makings, the rest of the 27-slot bag for what's made.

The job in hand is saved on the villager after every step, so a chain half made carries on from the same step after a
restart (the planks already made are in the saved bag). If the makings vanish while fetching, what was fetched goes back
where it came from. The plan's time is the job's usual time for that many crafts, shared out over the steps. Fuel a
furnace step needs (the Mason's kiln, the Tinkerer's smelting) is burnt when the chain is done.

Which jobs plan whole chains: those at the crafting table, the stonecutter and the Tinkerer's workshop (Carpenter,
Mason, scribe, Tinkerer, Toolsmith, Dyer, Furnicrafter). The Chef, the luxury makers, the Fletcher and the Weaponsmith
make what they can in one go, as before.

A Crafter in an older save is let go on their first tick: the workbench's ticket released, the job set to none (level 1,
no trades), `crafter_job` removed and the bag dropped. The workbench POI stays registered but nobody may take it.

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| `crafters` | on from 2.1 | The jobs that craft for the builders plan whole chains, save the job after every step and say what's short. Off: each makes what it can in one go, as before 2.1. (It was the Crafter's switch; the Crafter is retired whatever it says.) |

## 4. Saved data

- `crafter_job` on the villager: the job in hand of a Carpenter, Mason or other chain job (who it's for, the chain's
  steps, what it takes, the phase and the next step). None: no job (an older save, or nothing to do). An old Crafter's
  is removed when they're let go.
- The bag (`builder_bag`) and items made (`items_crafted`) are the ones every crafting job already saves.

## 5. Items, blocks, jobs, commands

- Retired, still registered so old saves load: the block `aliveworkplace:crafters_workbench` (no recipe, not in the
  creative tab, mined with an axe), the job `aliveworkplace:crafter`, the blueprint `aliveworkplace:crafters_workshop`
  and the village houses `aliveworkplace:village/<style>_crafters_workshop`.
- The Steward's condition `builders_short_of_parts` stays (it never holds now); its rule is gone.
- Data: `data/<namespace>/crafter_benches/*.json` still loads (`CrafterBenches`), but no job uses it now.

## 6. Decisions

- 37.14 (owner, 2026-10-20): a Crafter job that made what others need, through whole chains.
- 37.14a (owner, 2026-10-21): the Crafter retired; its planning moved into the jobs that craft for builders, each for
  its own kind, through one shared planner (`Chains`, used by `CrafterWork`) rather than copies.
- The `crafters` switch was kept, with a new meaning (the jobs' whole chains), so server configs keep loading and the
  feature has an off switch behind M37 as the Crafter had.
- The Crafter's Workbench recipe was removed (a block nobody can use shouldn't be craftable); the block, its POI and the
  job stay registered.
- 37.16a: the workshop's blueprint and its five village structures stay registered: chunks already generated name
  their pieces by structure id, and a started build site loads its blueprint by id. Only the pools, the Table and the
  Steward's rule lose it.
- Fletcher and Weaponsmith keep their one-go making (not in the owner's list); the Chef and luxury makers too.
- The vanilla stand-in for "a chair from slabs and planks" is the barrel (six planks and two slabs).

## 7. Known limits

- What's short is found along the first way down the recipes: with oak and spruce logs both short it names oak.
- The Furnicrafter's shortage names the first recipe's materials only.
- `crafter_benches` files (a furniture mod's bench for the Crafter) are loaded but unused; CobbleFurnies' bench is the
  Furnicrafter's.
- The showcase scenes are filmed on GitHub, not in this session.

## 8. Proof

- `JobChainGameTests.theCarpenterMakesBarrelsFromLogsInThreeSteps`: the Carpenter's three-step chain, each step saved
  and shown.
- `JobChainGameTests.theMasonFiresAndCutsStoneBrickStairsStepByStep`: a Mason's chain (fired, then cut), the coal burnt.
- `JobChainGameTests.aShortRawMaterialIsNamedInTheBuildersLineAndNothingIsWasted`: a missing raw material, named in the
  builder's line and the carpenter's, nothing taken.
- `JobChainGameTests.aChainHalfMadeCarriesOnAfterAReload`: save and reload mid-chain.
- `JobChainGameTests.anOldSavesCrafterLoadsJobless`: an old save's Crafter loads clean and jobless, the bag dropped,
  the workbench freed, not crafted or in the creative tab.
- `JobChainGameTests.withTheSwitchOffJobsCraftInOneGo`: the switch off.
- `JobChainGameTests.theCarpenterMakesTheSlabsTheBuilderNeeds`, `JobChainGameTests.theCarpenterFetchesTheOneLogItNeeds`,
  `JobChainGameTests.oneCarpenterServesTwoBuilders`, `JobChainGameTests.theCarpenterMakesAnotherWorkersLaddersFromTheStorehouse`.
- `JobChainGameTests.theLackLineNamesEveryShortThingWithACommaBetween`, `JobChainGameTests.beingShortOfJustOneThingIsStillNamed`.
- `FurnicrafterCompatTests.theFurnicrafterMakesAPieceFromLogsForAWaitingBuilder`: a Furnicrafter piece for a waiting
  builder, its makings sawn from logs (compat suite).
- `CrafterFurnitureCompatTests.theCarpenterMakesHandcraftedChairsFromLogsForTheBuilder`: Handcrafted chairs from logs
  (compat suite).
- `CrafterWorkshopGameTests.theWorkshopIsNoLongerOffered`, `CrafterWorkshopGameTests.theStewardNoLongerPlansAWorkshop`,
  `CrafterWorkshopGameTests.villagesNoLongerGrowTheWorkshop` and `VillageGameTests.anOldCraftersWorkshopStaysAPlainBuilding`.

Showcase scenes: `job_chains`, `crafter_furniture`
