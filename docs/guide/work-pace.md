# Work pace

```infobox
type: System
image: https://jcondedata.github.io/minecraft-alive-workplace/pace/still-1.jpg
caption: A builder at the cap
Bonuses: Pokémon partners, a well-kept village, Swift Hands, Craftsmanship, Expeditions, Diligent, a happy mood
Penalties: being ill, an unhappy mood, being lazy, a badly kept village
Cap: twice the usual pace (Fastest Work Pace)
Levels: on top of the cap, Novice to Master
Shown: on the hall's list, "Works 25% faster (a happy mood, Swift Hands)"
categories: Jobs, Village life
related: village-needs.md, moods.md, research.md, settings-seasons-and-pace.md
```

How fast a worker works comes down to one number, their **pace**. The [Village Hall](village-hall.md)'s list shows it
for each worker with the reasons: "Works 25% faster (a happy mood, Swift Hands)".

## What speeds a worker up

- **Pokémon partners** from a nearby pasture (with Cobblemon; see [Pokémon and villagers](pokemon.md));
- **a well-kept village**, up to 25% faster ([Village needs](village-needs.md));
- the scholars' **Swift Hands** (every job), **Craftsmanship** (crafters) and **Expeditions** (explorers and
  netherworkers) [research](research.md);
- the **Diligent** [trait](traits.md), 10%;
- **a happy mood**, 7% ([Moods](moods.md)).

All the bonuses together stop at a cap: normally **twice the usual pace**, the **Fastest Work Pace** setting. A
worker who reaches it shows "at the cap".

## What slows a worker down

- **being ill**: half pace ([Sickness](sickness.md));
- **an unhappy mood**: 15% slower;
- the **Lazy** trait: 10% slower;
- **a badly kept village**: up to 20% slower.

Penalties come after the cap, so an ill worker at the cap still works at half of it ("held back by being ill").

## Levels

A worker's **level** (Novice to Master) isn't part of the pace and isn't held to the cap: it speeds them up on top. A
Master builder builds two and a half times as fast as a Novice. Workers level up as they work (see
[Giving villagers jobs](jobs.md)).

## How it interacts

- **Fed by:** almost everything in village life: food, beds and guards, research, traits, moods and Pokémon.
- **Limited by:** the **Fastest Work Pace** setting, 100 to 400% ([Seasons and pace settings](settings-seasons-and-pace.md)).
- **Builders** have their own base speed too, the `workplaceBuildDelay` [game rule](game-rules.md).
