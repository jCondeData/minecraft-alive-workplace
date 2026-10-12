# /workplace classes

```infobox
type: Command
expansion: M34
Syntax: /workplace classes status, /workplace classes reseed
Who: operators
Works on: the Village Hall nearest you
categories: Commands, Classes
related: classes.md, households.md, settings.md
```

Two commands for server owners, for the day [classes](classes.md) come to a village.

## /workplace classes status

Tells you where the nearest hall's village stands:

- "Thornholm is finding its places: 12 of 22 villagers have a class so far." The first time a village has classes,
  everyone gets one over a minute or two.
- "Thornholm is settling in: 5 days left." For 7 days after, nobody falls a class and luxuries aren't needed yet.
- "Thornholm has settled into its classes."

It also counts the households of each class, and says how many newcomers will get a class at the hall's next round.

## /workplace classes reseed

Starts the village over: every villager loses their class and their progress toward the next, and the hall's next
rounds give everyone a class again from what they have now, with a new week to settle in. Ages and family names are
kept. Use it if a village was seeded badly, say before you'd built its homes.

## How it fits with the rest

- With **Village Classes** off in the settings, status says so, and the village keeps its classes for when they're back
  on.
- The settling-in week is **Days to Settle In** in the settings; how many dawns it takes to rise or fall are **Days to
  Rise a Class** and **Days to Fall a Class**.
