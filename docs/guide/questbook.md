# The questbook

```infobox
type: Guide
image: https://jcondedata.github.io/minecraft-alive-workplace/builders/still-2.jpg
caption: Builders putting up the starter builds, the questbook's second chapter
Needs: FTB Quests on the server
Chapters: Getting Started, The Builder and the Blueprint Table, Hiring and Retraining, The Village Hall and its Ranks, Pokémon in the Village
Rewards: emeralds and experience
categories: Basics, Quests
related: getting-started.md, builder.md, jobs.md, village-hall.md, pokemon.md
```

On a server with **FTB Quests**, the quest book has Alive Workplace's own chapters. They take you from your first day
to a City: your first Pokémon, your first builder, a village at work and the hall's ranks, one step at a time, each with
a few emeralds for your trouble.

Open the quest book the way you always do in FTB Quests. Our chapters come after the pack's own.

## The chapters

1. **Getting Started.** Pick your starter, pick apricorns, craft Poké Balls, make your first catch and win your first
   battle, then a Healing Machine and a PC. With Radical Cobblemon Trainers, two extra quests send you after the
   trainers on the road and your first gym leader. No village nearby? A [Settler's Wagon](getting-started.md) brings one.
2. **The Builder and the Blueprint Table.** The first quest is **Build a Starter Cottage**: your first home, where a
   villager moves in, and the start of a village. It says how, step by step: a Blueprint Table near a villager without
   a job, a Blank Blueprint, Get Blueprint, place it, fill the chests and hand it to the builder. Then the homes and
   their upgrades, I to II to III: the [Starter Cottage](starter-cottage-build.md) II and III, the
   [Stone House](stone-house-build.md) and the [Terrace](terrace-build.md); then crews, the
   [Scan Tool](scan-tool.md), the [Shape Planner](shape-planner.md), builds from the internet and a Master builder. See
   [The Builder and blueprints](builder.md).
3. **Hiring and Retraining.** Pick a job with its item, hire a vanilla worker, and retrain a villager who already has
   a job (5 emeralds for a Novice, up to 50 for a Master). Then one quest per job, each naming the building to put up
   for it and what its worker does there: the [Storehouse](storehouse-build.md) for a Porter, the
   [Farmstead](farmstead-build.md) for a Farmer, the [Smithy](smithy-build.md) for a Miner, the
   [Carpenter's Workshop](carpenters-workshop-build.md) for a Carpenter, and on to the Kitchen, the Mason's Yard, the
   Supply Shop, the Post Office, the Library, the Schoolhouse and more. A job with no table of its own (the Miner works
   at a blast furnace) gets the building that has its block, and the item to sneak-right-click the villager with. See
   [Giving villagers jobs](jobs.md).
4. **The Village Hall and its Ranks.** The [Village Hall](village-hall.md), the [Village Ledger](village-ledger.md),
   quests, the treasury, festivals, research and your first guards, then Village, Town and City
   ([Ranks](ranks.md)).
5. **Pokémon in the Village.** Partners at work from a Pasture Block, and the villagers who train: the Trainer, the
   Trainer Leader, the Move Tutor, the Ball Smith, the Pokémon Trader and the Fossil Scientist. See
   [Pokémon and villagers](pokemon.md).

The two Pokémon chapters are there only when the server has Cobblemon.

## Ticking quests off

Quests that ask for an item tick themselves off when you have it; you keep the item. A build quest ticks itself off
when a builder finishes that building for you: the player who handed over the blueprint gets it, or, if they're not
online, every player within the village's reach (64 blocks). An upgrade (II, III) counts as a building of its own; a
build of your own (scanned, uploaded or imported) and a Wonder's parts don't tick a build quest. A few things the quest
book can't see, like your village becoming a Town, have a box to tick yourself, with a sentence that says exactly what
to do first.

## For server owners

The chapters are added the first time the server starts with FTB Quests. When a new version of the mod brings a new
version of a chapter, it replaces the one on the server on the next start, with its new layout and quests, even after
FTB Quests has saved the chapter in its own way. A chapter you delete stays deleted. To keep your own edits to our
chapters, turn on **Keep Questbook Edits** (`questbookKeepEdits` in `config/aliveworkplace.json`): every chapter
already on the server is then left as it is, and only new chapters come in. The quest text can be translated in FTB
Quests like the pack's own.
