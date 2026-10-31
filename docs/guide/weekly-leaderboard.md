# Weekly leaderboard

```infobox
type: Event
expansion: M33
image: https://jcondedata.github.io/minecraft-alive-workplace/leaderboard/still-1.jpg
caption: The week's leaderboard, read out in chat
When: every Sunday at 19:00 server time
Boards: Biggest, Richest, Happiest, Most Legends, Greatest realm
Shows: the top three of each, with village, owner and number
Winners: a chronicle line and a blue ribbon on the hall for a week
Leave a village off: the toggle on the Trade page
Any time: /workplace leaderboard
categories: Village Hall, Trade
related: command-leaderboard.md, realms.md, economy.md, village-needs.md
```

Once a week the whole server hears whose village is doing best.

## The boards

Every Sunday at 19:00 server time, everyone online gets the week's leaderboard in chat: a gold title, then the top three
on each board, with the village, its owner and the number.

- **Biggest:** villagers ("1. Thornholm (Rowan) 24 villagers").
- **Richest:** emeralds earned that week: its daily takings, what players paid at its [price board](price-board.md),
  and its [caravans' sales](caravan-sales.md).
- **Happiest:** the week's average [wellbeing](village-needs.md).
- **Most Legends:** its Legends.
- **Greatest realm:** the [realm](realms.md) with the most member villages, then the most villagers.

A tie goes to the bigger village (for realms: the one with more villagers). A board nobody is on reads "no village
yet".

## The winners

The first of each board gets a chronicle line ("Won the week's leaderboard: Biggest village, with 24 villagers"), and
its hall's name icon wears a **blue rosette ribbon** for a week. For the Greatest realm it's the capital's hall.

![Ashford's name icon wears the Happiest ribbon](https://jcondedata.github.io/minecraft-alive-workplace/leaderboard/still-2.jpg)

## Leaving a village off

The hall's Trade page (behind the minecart) has a gold ingot in its top row, "On the weekly leaderboard". The owner, a
friend or an operator clicks it to leave the village off, and again to put it back.

## How it fits in

- **What feeds it:** everything that makes a village bigger, richer and happier.
- **A missed week:** if the server was down when it fell due, it's read out once at the next start.
- **See it early:** [/workplace leaderboard](command-leaderboard.md) shows the standings so far.
- **What stops it:** **Weekly Leaderboard** off in the settings screen: nothing counted or announced. **Leaderboard
  Hour** sets the hour; the day is `leaderboardDay` in the config file.
