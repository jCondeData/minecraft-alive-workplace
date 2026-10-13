# Raids

```infobox
type: Threat
image: https://jcondedata.github.io/minecraft-alive-workplace/guard/still-2.jpg
caption: A guard fights off three husks
Who comes: zombies, skeletons and spiders (or bandits from a nearby camp)
Needs: a Village Hall and at least 8 villagers
Chance a night: 15%, plus 1% for every villager over 8, at most 35%
Rest: at least 3 days between two raids on one village
Raiders: 3, plus 1 for every 4 villagers, at most 16
Ends: when the raiders are dead, or at dawn
Setting: Monster Raids (on)
categories: Defense
related: defence.md, bandit-camps.md, threat-level.md, settings-threats.md
```

Once your village has grown a little, it starts to draw trouble at night. A **raid** is a band of monsters that gathers
at the edge of the village after dark and goes for its people.

## What happens

A village with a [Village Hall](village-hall.md) and at least 8 villagers may be raided. Every evening the village's
clock decides whether raiders come the next night, so there's a whole day in which you might hear of it. When the raid
starts, the bell rings: the guards rally and everyone else runs home to hide. Players in the village get a message
("12 monsters are raiding Oakvale!").

The raiders gather on one side of the village and attack. Guards fight anywhere in the village while it's raided,
not just round their own post. The raid ends when the last raider is dead ("Oakvale has fought off the raid"), or at
dawn, when the survivors flee. The village's chronicle remembers every raid.

## How big, how often

- **The chance.** 15% a night for a village of 8, one percent more for every villager over that, up to 35%.
- **The size.** 3 raiders, plus one for every 4 villagers. A village with guards also draws up to 4 more raiders in
  iron. Never more than 16.
- **Rest.** At least three days pass between two raids on the same village.

## A day's warning

Post a guard at a Guard Post within 24 blocks of a finished **Lookout Tower** or **Wall Tower**, and the guard on the
night watch spots raiders coming the evening before. You're told who's coming and from which side, even when you're away
if you own the hall, and the chronicle notes it. On the day itself the gates of the village's
[Palisade Gates](palisade-gate-build.md) and Gatehouses shut from noon. A [Seer](seer.md) living in the village sees
further: you're told of a raid two days before it comes. Without a lookout or a Seer, the bell is your only warning.

## How it interacts

- **Fed by:** the village's size (more villagers, likelier and bigger raids) and its guards (more raiders in iron).
- **Bandit camps.** While a [bandit camp](bandit-camps.md) stands near the village, the raids come from the camp, and
  twice as often.
- **Research.** The scholars' Drill (guards hit harder) and Fortification (guards turn aside blows) help you win.
  Warding stops explosions breaking blocks in the village. See [Research](research.md).
- **Mercenaries.** Short of guards? Hire three for a night from the hall (see [Threats and defense](defence.md)).
- **Turned down or off:** the [Threat Level](threat-level.md) setting makes raids rarer, more frequent or stops them;
  the world on **Peaceful** stops them; the [Monster Raids](settings-threats.md) switch off stops the village's night
  raids altogether, the bandits' from a camp included.
- **On demand.** Operators can start or end a raid with [/workplace threat](command-threat.md).
