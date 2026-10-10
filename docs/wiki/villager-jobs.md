# Villager jobs

How a villager gets a job in this mod, what every job has in common (chests, levels, pace, status lines), and the
list of all the jobs with the block and item that start each.

Roadmap items: 21.1a, 21.1c, 24.1, 30.2, 28.8, 28.9, 28.10, 28.11, 28.12, 34.10, 34.11

## What a player sees

**Vanilla jobs work as in vanilla:** put the block near a jobless villager and they take it. Most of the mod's jobs
share a vanilla block. To start one of ours, stand the villager within about 4 blocks of the block and
**sneak-right-click them holding the job's item**. A villager already working there switches the same way, and the
block's own job comes back with its own item (wheat for a Farmer, coal for an Armorer). You are told what happened
("Dara took the Miner job at the Blast Furnace."), or what is missing (no such block near, or every one taken).
Hold Shift over a workstation in your inventory to see its jobs and their items.

Chests and barrels within 8 blocks of the workstation are that worker's supply chests: tools and materials come out
of them and the work goes into them. A line over a worker's head says what they are doing or waiting for, and
sneak-right-clicking a worker with an empty hand shows their status.

| Job | Workstation and item | Class |
|---|---|---|
| Builder | Blueprint Table | any |
| Miner | Blast Furnace + a pickaxe | any |
| Lumberjack | Fletching Table + an axe | any |
| Orchard Keeper | Composter + sweet berries, glow berries or an apple | any |
| Farmer | Composter (vanilla) | any |
| Florist | Composter + a small flower | any |
| Composter | Composter + bone meal | any |
| Beekeeper | Beehive or Bee Nest + a glass bottle or shears | any |
| Sifter | Cauldron + gravel, sand, red sand or soul sand | any |
| Vintner | Cauldron + sweet berries, glow berries or an apple | any |
| Tailor | Loom + string | any |
| Printer | Cartography Table + an ink sac | any |
| Leatherworker (dyer) | Cauldron (vanilla) | any |
| Scholar | Lectern + paper | Burgher |
| Teacher | Lectern + a book | Artisan |
| Librarian (scribe) | Lectern (vanilla) | any |
| Tinkerer | Smithing Table + redstone | Artisan |
| Toolsmith | Smithing Table (vanilla) | any |
| Netherworker | Cartography Table + netherrack | Artisan |
| Cartographer (explorer) | Cartography Table (vanilla) | any |
| Undertaker | Brewing Stand + a golden apple or a totem | Burgher |
| Nurse | Brewing Stand + a honey bottle | Artisan |
| Cleric (alchemist) | Brewing Stand (vanilla) | any |
| Rancher | Smoker + a saddle or a golden carrot | any |
| Chef | Smoker + raw meat, raw fish or a potato | Artisan |
| Butcher (herder) | Smoker (vanilla) | any |
| Carpenter | Crafting Table + planks | any |
| Mason | Stonecutter (vanilla) | any |
| Gem Grower | Stonecutter + an amethyst shard | any |
| Armorer (smelter) | Blast Furnace (vanilla) | any |
| Weaponsmith | Grindstone (vanilla) | any |
| Guard | Grindstone + a sword | any |
| Fletcher | Fletching Table (vanilla) | any |
| Shepherd | Loom (vanilla) | any |
| Fisherman | Barrel (vanilla) | any |
| Porter | Storehouse | any |
| Postman | Mailbox + paper | any |
| Shopkeeper | Shop Counter | Artisan |
| Innkeeper | Shop Counter + a bed | Artisan |
| Ferryman | Travel Post | any |
| Bard | Jukebox + a music disc | any |
| Steward | Village Hall + its City Plan, on a Journeyman Builder | any |
| Trainer (Cobblemon) | Training Post | any |
| Trainer Leader (Cobblemon) | Training Post + a block of gold | Burgher |
| Move Tutor (Cobblemon) | Training Post + a book | Artisan |
| Ball Smith (Cobblemon) | Smithing Table + an apricorn | Artisan |
| Pokémon Trader (Cobblemon) | Shop Counter + a Poké Ball | Artisan |
| Fossil Scientist (Cobblemon) | Cobblemon's Fossil Analyzer + a fossil | Artisan |
| Berry Breeder (Cobblemon) | Composter + any Cobblemon berry | any |
| Camp Cook (Cobblemon) | Campfire Pot + Hearty Grains | any |
| Habitat Keeper (Cobblemon) | Pasture Block + a honey bottle | any |
| Daycare Keeper (Cobblemon) | Pasture Block + an egg | any |

The Class column only counts once classes are on (see [Classes](classes.md)). Each job's own page or README section
says what it does all day.

## How it works

**Fewer job blocks (21.1a).** Related jobs share a block. A jobless villager by a vanilla block takes the vanilla
job by itself, as in vanilla; the player picks one of ours with its item. A crafting table, a beehive, a jukebox or a
Mailbox never takes a jobless villager by itself. Where one item fits two blocks (paper, a book, a glass bottle), the
block they work at, or else the nearest, decides. The older job blocks (the Builder's Bench, the Fruit Basket...)
can't be crafted any more, but ones already placed keep working, so old worlds are fine.

**Picking is not hiring.** Picking a job gives the villager the job. Hiring a vanilla worker whose job the mod
extends (an Armorer as a smelter, a Toolsmith...) is still that job's item on a villager who already has the job:
hired, they work with your own workers; unhired, for their village.

**Villages share.** Workers whose workstations are within `villageRadius` blocks (48) of each other are one
village: one short of something takes it from another's chests. Only workers who answer to the same people share
(yours and your friends', or village workers nobody hired).

**Pace.** Every job's speed goes through one rule: the worker's level, then every bonus (partners, a well-kept
village, research, traits, mood, edicts, guilds, tonics) up to the cap `maxWorkPace`, then penalties (illness, a bad
mood). The Builder's page has the level table.

**Requests.** What workers are waiting for (materials, a pickaxe, seeds) is posted on the Storehouse's board, where
players can hand things over; the Village Hall lists it too.

**Working with nobody near.** While anyone is online, every worker's workstation chunk stays loaded, so villages
keep working when no player is near (`keepVillagesWorking`). Builds and quarries follow the game rule
`workplaceKeepWorkLoaded`.

**Caps for server owners (25.5).** With `maxWorkersPerVillage` above 0, a jobless villager doesn't take a free
workstation once that many are taken nearby; nobody who has a job loses it. `workerPathRange` limits how far a
worker looks for a path in one go; farther walks are made in legs.

**Outfits (24.1).** Each of the mod's professions has a vanilla-style outfit and a zombie villager version.

## Switches

| Key | Default | What it does |
|---|---|---|
| `supplyRadius` | 8 (2 to 32) | Chests this close to a workstation are its supply chests |
| `villageRadius` | 48 (0 to 128) | Workers this close together share chests; 0: no sharing |
| `maxWorkersPerVillage` | 0 (0 to 500) | The most workers per village; 0: no limit |
| `workerPathRange` | 48 (16 to 128) | How far a worker looks for a path in one go |
| `keepVillagesWorking` | on | Villages keep working while no player is near |
| `maxWorkPace` | 200 (100 to 400) | The cap on every speed bonus together, in percent of the usual pace |
| `workplaceKeepWorkLoaded` | true | Game rule: builds and quarries keep running while their player is online |
| `workplaceVillageFarms` | true | Game rule: a village farmer with no field takes on the farm by their composter |
| `vintners` | on from 1.8 | The Vintner job |
| `tailors` | on from 1.8 | The Tailor job |
| `printers` | on from 1.8 | The Printer job |
| `berryBreeders` | on from 1.2 | The Berry Breeder job |
| `campCooks` | on from 1.2 | The Camp Cook job |
| `habitatKeepers` | on from 1.2 | The Habitat Keeper job |
| `daycareKeepers` | on from 1.2 | The Daycare Keeper job |
| `gemGrowers` | on from 1.2 | The Gem Grower job |

## Saved data

A villager's job is vanilla's own profession and job-site memory. On top of it the mod saves small attachments on
the villager (`registry/ModAttachments`), each absent or zero by default: the job in hand (`builder_job`,
`miner_job`, `farm_field`, `tree_farm`, `orchard`, `courier_routes`, `patrol_route`), who hired them
(`builder_employer`), and a counter per job that the hall shows (`trees_felled`, `fish_caught`, `mail_delivered`
and so on). Status lines and the requests board are not saved: workers post again after a restart.

## Items, blocks, jobs, commands

- Workstations of our own: Blueprint Table (`aliveworkplace:blueprint_table`), Storehouse
  (`aliveworkplace:storehouse`), Mailbox (`aliveworkplace:mailbox`), Shop Counter (`aliveworkplace:shop_counter`),
  Travel Post (`aliveworkplace:travel_post`), Training Post (`aliveworkplace:training_post`).
- Older job blocks that still work where placed: Builder's Bench (`aliveworkplace:builders_bench`), Miner's Bench
  (`aliveworkplace:miners_bench`), Chopping Block (`aliveworkplace:chopping_block`).
- Markers: Quarry Marker (`aliveworkplace:quarry_marker`), Field Marker (`aliveworkplace:field_marker`), Delivery
  Note (`aliveworkplace:delivery_note`), Patrol Map (`aliveworkplace:patrol_map`).
- Texts: `message.aliveworkplace.job.chosen`, `message.aliveworkplace.job.needs_station`,
  `message.aliveworkplace.job.station_taken`, `message.aliveworkplace.job.class_needed`,
  `tooltip.aliveworkplace.station.hint`.
- Commands: `/workplace friend add|remove|list`, `/workplace mail`, `/workplace strip <height>`.

## Decisions

- 21.1a (owner, 2026-09-30): not every villager needs a new custom table; items already in the game should give a
  villager their job. So jobs share vanilla blocks and are picked with an item.
- 21.1c (owner, 2026-10-03): the Fossil Scientist works at Cobblemon's own Fossil Analyzer.
- 21.1a: don't break old worlds. The replaced job blocks stay registered, and blocks that became workstations get
  their record when an old chunk loads.
- 25.5: every "needs" system has its own switch, because babysitting is the top complaint about big colony mods.
- 23.6 (owner's call): villages keep working when no player is near.

## Known limits

- A job needs a free block of its kind within about 4 blocks of the villager when it is picked.
- Jobs given by order (a player's pick, the Steward) aren't held back by the worker cap.
- The five Pokémon jobs, the Gem Grower, the Vintner, the Tailor and the Printer are behind their expansions' switches until 1.2
  and 1.8.
- This page lists how each job starts, not what it does: jobs without a wiki page yet are in the README.

## Proof

GameTests: `StationsGameTests` (11: every item gives its job at its block, a picked job stays, switching at the
same block), `StationsSpecGameTests` (17), `StationsBugGameTests` (12), `StationsFixesGameTests`,
`StationsRetakeGameTests`, `StationsCompatTests` (10, the Pokémon jobs), `OutfitGameTests`, `ConfigGameTests`,
`PaceGameTests`.

Showcase scenes: `stations` (one composter, four jobs), `staff` (every workstation with its villager), `outfits`
(every outfit, and as a zombie), `berry_breeder`, `camp_cook`, `habitat_keeper`, `daycare_keeper`, `gem_grower`,
`vintner`, `tailor`, `printer`.
