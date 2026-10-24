# Random jobs at shared blocks

A jobless villager who takes a block several jobs share by themselves (a composter, a lectern, a Shop Counter...) gets
one of its jobs at random, likeliest the one the village has fewest of, instead of always the block's vanilla job. Part
of the quick additions (Milestone 36); not behind an expansion gate.

Roadmap items: 36.17

## What a player sees

Put four composters down by four villagers without a job: they don't all become farmers. Each takes a composter and
becomes a Farmer, an Orchard Keeper, a Florist or a Composter. A village that already has three farmers and no
florist will most likely get a florist next. Once a villager has a job, they keep it, through a save and reload, like
any job. A job picked with its item (sneak-right-click) is never random: wheat still makes a Farmer.

## How it works

When vanilla gives a jobless villager the job of the block they walked to (its `AssignProfessionFromJobSite`, the
first job registered at the block), `work/RandomJobs` swaps it, at that moment, for one drawn from the block's jobs.
The hook sits on `Villager.setVillagerData` (mixin/VillagerMixin) and draws only when all of these hold:

- the switch `randomStationJobs` is on;
- it happens inside the villager's own brain run (`WorkerLimits.thinker()`), so a villager being loaded from a save, a
  story setting someone's job, or any order (the hall's Free Jobs page, the Steward, a parent's trade, a successor,
  `Jobs.employ`, an item: `WorkerLimits.ordering`) is never redrawn;
- the villager is jobless and is being given the first (vanilla) job of a shared block in `Stations.ALL` that takes
  a villager by itself, at their job site.

The jobs drawn from: the block's own job (always), then each other job of the block that this game has
(`Stations.available`: its config switch on, Cobblemon for the Pokémon jobs) and that the villager's class may hold
(`people/ClassJobs`, with village classes on). The Trainer Leader is never drawn. A block left with one job (the loom
with tailors off) gives that one.

**The rule.** Each job gets the weight `1 / (1 + n)`, where `n` is how many villagers within 48 blocks of the block
(the default village radius of the worker cap) already do it. A new village draws evenly; a job the village has 10 of
weighs 1/11 against a missing job's 1. So villages fill out their jobs instead of piling up farmers, yet any job can
still come up. The draw uses the villager's own random.

**Every block that offers several jobs** (the block's own, vanilla, job first; in brackets the ones that need a
switch or a mod):

| Block | Jobs |
|---|---|
| Composter | Farmer, Orchard Keeper, Florist, Composter, (Berry Breeder: Cobblemon, `berryBreeders`) |
| Blast Furnace | Armorer, Miner |
| Fletching Table | Fletcher, Lumberjack |
| Smithing Table | Toolsmith, Tinkerer, (Ball Smith: Cobblemon) |
| Cauldron | Leatherworker, Sifter, (Vintner: `vintners`), (Well Keeper: `wellKeepers`, disasters on) |
| Loom | Shepherd, (Tailor: `tailors`) |
| Lectern | Librarian, Scholar, Teacher |
| Cartography Table | Cartographer, Netherworker, (Printer: `printers`) |
| Brewing Stand | Cleric, Nurse, Undertaker, (Physician: `physicians`, disasters on) |
| Smoker | Butcher, Chef, Rancher |
| Stonecutter | Mason, (Gem Grower: `gemGrowers`), (Jeweler: `jewellers`) |
| Grindstone | Weaponsmith, Guard, (Firewarden: `firewardens`, village fires on) |
| Shop Counter | Shopkeeper, Innkeeper, (Pokémon Trader: Cobblemon) |
| Training Post (Cobblemon) | Trainer, Move Tutor (the Trainer Leader only with a gold block) |
| Pasture Block (Cobblemon) | Habitat Keeper, Daycare Keeper: never taken by itself, so only by item |

Blocks with one job (a barrel, a Storehouse, a Travel Post, a Blueprint Table, a Mailbox...) are unchanged.

## Switches

| Key | Default | What it does |
|---|---|---|
| `randomStationJobs` | on | A jobless villager taking a shared block by themselves draws one of its jobs, likeliest the fewest. Off: always the block's vanilla job, as before 36.17. |

## Saved data

None new. The job drawn is the villager's profession, which vanilla saves in `VillagerData`; it is never drawn again
(a villager loaded from a save is not in their brain's run, and already has a job).

## Items, blocks, jobs, commands

No new ones. The blocks and jobs are those of [Villager jobs](villager-jobs.md); the setting is
`aliveworkplace.config.randomStationJobs` ("Random Station Jobs") on the settings screen.

## Decisions

- 36.17 (owner, 2026-10-20): "a job block with several jobs gives one at random", weighting toward the jobs the village
  has fewest of left to the lane. Chosen: weight `1 / (1 + n)` over the villagers within 48 blocks, so even a common
  job can still come up and a new village draws evenly.
- Only villagers taking a block by themselves draw (the lane): the item, the hall's Free Jobs page (which names the
  job), the Steward (who fills a gap on purpose), a family trade and a successor keep the job they name. This replaces
  21.1a's "a jobless villager takes the vanilla job by itself" for shared blocks; the Shop Counter's "Shopkeeper by
  itself" (the owner's 21.1a answer) becomes the Shopkeeper or the Innkeeper.
- The Trainer Leader is never drawn (the lane): a gym leader is a title, made by hand with a gold block.
- A job the villager's class can't hold (34.8) is left out of the draw, so a Peasant isn't drawn as a Scholar only to
  be refused; the vanilla job is always in it, as vanilla gave it.
- A switch, on by default (the lane): the owner's live server can go back to the vanilla job.
- The block's vanilla job is still the first in every list and in the tooltip; nothing about picking by item changed.

## Known limits

- The Steward's "nearest free block" and the hall's Free Jobs page still offer a shared block's vanilla job (they name
  the job before it's given).
- Villagers are counted within 48 blocks of the block, not by the Village Hall's own village; a block between two
  villages counts both.
- Village houses that come with a jobless villager (a Trainer's House) now give one of the block's jobs too.

## Proof

- `RandomJobsGameTests.everyJobOfASharedBlockComesUp`: every shared block, 200 draws with a fixed random, every job
  it offers comes up and each holds the block.
- `RandomJobsGameTests.theJobsTheVillageHasFewestOfAreLikeliest`: the weighting, and a new village drawing evenly.
- `RandomJobsGameTests.joblessVillagersAtAComposterTakeEachOfItsJobs`: 24 villagers through vanilla's entry point,
  all four jobs.
- `RandomJobsGameTests.aJoblessVillagerTakesOneOfTheBlocksJobsInPlay`: a jobless villager by a lectern, with only
  vanilla's brain, takes one of its jobs and keeps it.
- `RandomJobsGameTests.aSingleJobBlockIsUnchanged`: a barrel, a Storehouse, the loom with `tailors` off and on.
- `RandomJobsGameTests.ordersItemsAndTheSwitchOffKeepTheJobNamed`: orders, the item, a load, the switch off, its words.
- `RandomJobsGameTests.theJobDrawnSurvivesASaveAndReload`.
- Showcase scenes: `random_job` (four composters, four jobless villagers, named by the job each took).
