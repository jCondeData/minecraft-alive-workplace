# Threat settings

```infobox
type: Setting
image: https://jcondedata.github.io/minecraft-alive-workplace/config/still-2.jpg
caption: The settings screen's switches
Monster Raids: on
Bandit Camps: on
Threat Level: 2 (0 to 3)
Village Protection: on
categories: Settings, Defense
related: raids.md, bandit-camps.md, threat-level.md, village-protection.md
```

Four settings decide how much danger your villages are in, and whether players can protect theirs. See
[Settings](settings.md) for where to change them.

## Monster Raids

Normally **on**: monsters [raid](raids.md) bigger villages with a Village Hall at night now and then. Off: no night
raids come to any village, including the bandits' raids from a camp.

## Bandit Camps

Normally **on**: bandits [make camp](bandit-camps.md) near villages of Village rank or more now and then, and raid them
until their chief falls. Off: no camps go up.

## Threat Level

Normally **2**, a slider from 0 to 3: how hard the villages' threats are, beside each one's own switch. 0, none at all;
1, gentle (half as often); 2, normal; 3, hard (half again as often, and a quarter more raiders). On Peaceful nothing
comes. More in [Threat Level](threat-level.md).

## Village Protection

Normally **on**: a Village Hall's owner may [protect](village-protection.md) the village from other players. It's a
setting on each hall, off until its owner turns it on. Off here: nobody on the server can protect a village.

## How they interact

- The switches and the level work together: a threat that's switched off never comes, and the level sets how often the
  others do.
- An operator's [/workplace threat](command-threat.md) order works whatever these say.
- Fewer threats means fewer reasons for guards, walls and lookouts; the village still wants a guard for every ten
  villagers to feel safe (see [Village needs](village-needs.md)).
