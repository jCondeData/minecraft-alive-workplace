# /workplace threat

```infobox
type: Command
Syntax: /workplace threat start <monsters or bandits>, lair bandits, end, clear
Who: operators
Works on: the nearest Village Hall
categories: Commands, Defense
related: raids.md, bandit-camps.md, threat-level.md, settings-threats.md
```

An operator's tool for events and for testing your defenses: it starts or ends the trouble at the nearest
[Village Hall](village-hall.md) right now. It's an order, so it works whatever the settings or the [Threat Level](threat-level.md) say, on any
difficulty but Peaceful: there hostile mobs can't exist, so no raid or camp can come, and the command tells you so.

## The commands

| Command | What it does |
|---|---|
| `/workplace threat start monsters` | a monster [raid](raids.md) on the village now |
| `/workplace threat start bandits` | a bandit raid on the village now |
| `/workplace threat lair bandits` | sets a [bandit camp](bandit-camps.md) down by the village, and says where |
| `/workplace threat end` | ends the raid under way: the raiders go at once and the village is told |
| `/workplace threat clear` | clears the village of everything: the raid, the camp and the attacks it was due |

You're told how it went: how many raiders came, where the camp stands, or why it couldn't (a raid already under way, a
camp already standing, no hall nearby, a Peaceful world).

## How it interacts

- A camp set down by a village where nothing comes (Threat Level 0) leaves again at the next dawn.
- A raid you end with `end` isn't written into the village's list of past attacks.
- See [Threat settings](settings-threats.md) to change how often trouble comes by itself.
