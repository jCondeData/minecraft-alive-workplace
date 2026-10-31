# /workplace threat for every raider and disaster

```infobox
type: Command
expansion: M32
Syntax: /workplace threat start <raiders>, lair <raiders>, disaster <disaster>, end, clear
Raiders: monsters, bandits, pillager_warband, desert_raiders, drowned_pirates, piglin_incursion, witch_coven
Disasters: village_fire, drought, plague
Who: operators
Works on: the nearest Village Hall
categories: Commands, Defense, Disasters
related: command-threat.md, disasters.md, lairs-and-sieges.md, defense-page.md
```

The operators' [/workplace threat](command-threat.md) command reaches every kind of raider and every disaster: handy
for a server event, or to see whether your walls really hold before the warband finds out for you.

## The commands

| Command | What it does |
|---|---|
| `/workplace threat start pillager_warband` | a raid by that kind of raider on the nearest village now (any of the seven) |
| `/workplace threat lair witch_coven` | sets that kind's lair down by the village, and says where (any kind that keeps a lair) |
| `/workplace threat disaster drought` | starts that disaster in the village now: `village_fire`, `drought` or `plague` |
| `/workplace threat end` | ends the raid and the disaster under way |
| `/workplace threat clear` | clears the village of everything: the raid, the disaster, the lair and the attacks it was due |

The command suggests the names as you type. You're told how it went, or why it couldn't: a raid or a disaster already
under way, a lair already standing, no hall nearby, a Peaceful world.

## How it fits in

- It's an order, so a raid starts whatever the [Threat Level](threat-level.md) or the village's **At peace** say, on
  any difficulty but Peaceful: there hostile mobs can't exist, so no raid or lair can come, and the command says so. In a village that's quiet that way, though, a lair still packs up at the next dawn and a disaster
  ends almost at once.
- What follows plays out as it would by chance: see [Sieges](sieges.md), [Disasters](disasters.md) and the raiders'
  own pages, such as the [pillager warband](pillager-warband.md) or the [witch coven](witch-coven.md).
