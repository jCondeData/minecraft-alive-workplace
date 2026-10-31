# The questbook

```infobox
type: Guide
image: https://jcondedata.github.io/minecraft-alive-workplace/builders/still-2.jpg
caption: Builders putting up the starter builds, the questbook's first chapter
Needs: FTB Quests on the server
Chapters: Founding, The Hamlet Grows, A Village, A Town, A City, Wonders
Rewards: emeralds and experience
categories: Basics, Quests
related: getting-started.md, builder.md, jobs.md, village-hall.md, ranks.md, pokemon.md
```

On a server with **FTB Quests**, the quest book tells Alive Workplace as one story, from your first builder to a City
and its Wonders. Each chapter is a rank of your village. Along the middle of a chapter runs its main line of quests, one
after the other: each one says exactly what to do next, and the next one opens when it's done. Side quests branch off
above and below it: nice to have, never needed to finish the chapter.

Open the quest book the way you always do in FTB Quests. Our chapters come after the pack's own.

## The chapters

1. **Founding.** A [Blueprint Table](blueprint-table.md) near a villager without a job, so they become your
   [Builder](builder.md); the [Starter Cottage](starter-cottage-build.md); the [Village Hall](village-hall.md); the
   first jobs, a Farmer at the [Farmstead](farmstead-build.md) and a Porter at the [Storehouse](storehouse-build.md);
   16 meals in the store; and your village's first baby in a free bed ([Babies and growth](babies-and-growth.md)).
2. **The Hamlet Grows.** More homes (the [Stone House](stone-house-build.md) and the [Terrace](terrace-build.md)), a
   second builder from the [Builder's Workshop](builders-workshop-build.md) and a crew, your first
   [quest at the hall](quests.md), and the [Market Square](market-square-build.md).
3. **A Village.** Your Hamlet becomes a Village ([Ranks](ranks.md)); a [City Plan](city-plan.md) and a
   [Steward](steward.md); the first festival ([Festivals and market days](festivals-and-markets.md)); the first
   caravan to another village ([Trade and caravans](trade-and-caravans.md)); upgrades, starting with the Starter
   Cottage II, and Village Hall II and III; and the [Library](library-build.md)'s first three levels of research.
4. **A Town.** Guards in the [Barracks](barracks-build.md), a wall with a [Gatehouse](gatehouse-build.md), a
   [raid](raids.md) survived, a [guild](guilds.md), an [edict](edicts.md), your first [Legend](legends.md), and seven
   levels of research.
5. **A City.** Grand homes, the [classes](classes.md) from Peasant to Noble and the [Manor](manor-build.md), a
   [realm](realms.md), a [colony](colonies.md) and a Kingdom.
6. **Wonders.** A Wonder Plan, your first [Wonder](wonders.md), and then every one of them.

A home or hall quest also unlocks the build's next level in your village's Blueprint Table: the Starter Cottage
unlocks Starter Cottage II, then III; the Stone House, Stone House II and III; the Terrace, Terrace II; and the Village
Hall, Village Hall II and III. And every build the story's main line asks for that a village doesn't start with (the
Market Square, the Barracks, the Gatehouse, the Guildhall and the Manor) is unlocked by the quest before it, so you can
always get it at the Table, which says "A questbook reward".

**With Cobblemon**, side quests for your Pokémon join the story where they fit: your starter and first catch, Pokémon
helping at work, the village's Trainers and Trainer Leader, the Pokémon jobs, the Arena and the Festival Cup, the
Pokémon Center and the Stadium. Without Cobblemon they aren't there.

**Expansions.** A quest about something the server hasn't switched on (the Steward, guilds, Legends, the classes and
so on) isn't in the book until it is; the main line simply goes on to the next quest. The Wonders chapter comes in
with Wonders.

## Ticking quests off

Most quests tick themselves off:

- **Items:** when you have the item; you keep it.
- **Buildings:** when a builder finishes that building for you. The player who handed over the blueprint gets it, or,
  if they're not online, every player within the village's reach (64 blocks). An upgrade (II, III) counts as a
  building of its own; a build of your own (scanned, uploaded or imported) and a Wonder's parts don't.
- **Your village's big days:** reaching Village, Town and City rank, the first caravan sent, the first festival held,
  a raid survived, the first hall quest finished, a Steward appointed, a colony founded, a Wonder finished, a baby
  born in the village, 3 and 7 levels of research, an edict proclaimed, the first Burgher and Noble households, a
  realm founded and the takings collected. These go to the village's owner and to every player in the village at the
  time (and to the player who did it, wherever they stand). A village that got there before you had the quest (a
  baby on record, its levels of research, an edict in force, a Burgher or Noble household, a realm it's the capital
  of) ticks it off at its next dawn; the takings tick off the next time you collect at least an emerald.
- **Breaking a raiders' camp:** "Take the fight to them" ticks off for the player who strikes the last blow and
  everyone leading guards in the war party.

A few things the quest book can't see, like a fought bout or a road built, have a box to tick yourself, with a
sentence that says exactly what to do first.

## For server owners

The chapters are added the first time the server starts with FTB Quests. When a new version of the mod brings a new
version of a chapter, it replaces the one on the server on the next start, with its new layout and quests, even after
FTB Quests has saved the chapter in its own way; switching an expansion on, or adding Cobblemon, also brings its quests
into the chapters already there. A chapter you delete stays deleted. Chapters from an earlier questbook that the mod no
longer has (such as the old Getting Started and the chapters per expansion) are removed on the next start.

To keep your own edits to our chapters, turn on **Keep Questbook Edits** (`questbookKeepEdits` in
`config/aliveworkplace.json`): every chapter already on the server is then left as it is, old ones too, and only new
chapters come in. The quest text can be translated in FTB Quests like the pack's own.
