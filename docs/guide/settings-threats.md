# Threat settings

```infobox
type: Setting
image: https://jcondedata.github.io/minecraft-alive-workplace/config/still-2.jpg
caption: The settings screen's switches
Monster Raids: on
Bandit Camps: on
Threat Level: 2 (0 to 3)
Force Raider Spawns: on
Force Village Spawns: on
Village Protection: on
categories: Settings, Defense
related: raids.md, bandit-camps.md, threat-level.md, village-protection.md
```

Five settings decide how much danger your villages are in, and whether players can protect theirs. See
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

## Force Raider Spawns

Normally **on**: some modpacks stop vanilla's hostile mobs from spawning at all (Cobbleverse does, with a mod called
MobsBeGone that blocks pillagers, zombies and most other vanilla mobs). With this on, a bandit camp's chief and men and a
raid's raiders are put in the world anyway, so a camp is never empty and a raid really comes. Off: those mobs stay out,
so no camp goes up and no raid comes, and the server log says which mob was blocked.

On **Peaceful** difficulty hostile mobs can't exist at all, so nothing comes whatever this says; players near the place
are told so.

## Force Village Spawns

Normally **on**: the same kind of mod can also stop iron golems, wolves, horses, cats and llamas (Cobbleverse's
MobsBeGone blocks all of them). With this on, the village's own animals and golems are put in the world anyway: the
Golem Smith's golems, the Iron Pact's golem, relief riders' horses, the Beastmaster's foals and wolf pups, and a
caravan's pack llamas. Off: they stay out, and the server log says which animal was blocked. Animals and golems the game
spawns by itself are never forced.

## Camp Ruins

Normally **off**: a bandit camp (or another raiders' camp) whose chief is killed comes down a couple of seconds later,
its tents and fire gone and the loot in its chests spilled on the ground. On: the broken camp stays standing as ruins,
its fire put out. Either way the village forgets it at once, and a new camp may come after the quiet days.

## Village Protection

Normally **on**: a Village Hall's owner may [protect](village-protection.md) the village from other players. It's a
setting on each hall, off until its owner turns it on. Off here: nobody on the server can protect a village.

## How they interact

- The switches and the level work together: a threat that's switched off never comes, and the level sets how often the
  others do.
- An operator's [/workplace threat](command-threat.md) order works whatever these say.
- Fewer threats means fewer reasons for guards, walls and lookouts; the village still wants a guard for every ten
  villagers to feel safe (see [Village needs](village-needs.md)).
