# Witch coven

```infobox
type: Threat
expansion: M32
image: https://jcondedata.github.io/minecraft-alive-workplace/coven/still-2.jpg
caption: Potions over the wall
Comes to: a Village, Town or City in a swamp or mangrove swamp
Lair: the Coven Hut, on stilts over a pond, 56 to 80 blocks from the hall
Captain: the Coven Mother, a witch with 40 more health who blinks away when hit
Band: witches, and zombie villagers in dark caps
Camp strength: 8 at first, 1 more a day, 14 at most
The hex: one to three villagers a night fall ill, and no nurse can cure it
Break it: kill the Coven Mother, or break her cauldron
Loot: potions, glowstone, redstone, amethyst, sometimes an enchanted book
categories: Defense
related: lairs-and-sieges.md, remedy.md, sickness.md, plague.md
```

A swamp village may find witch-light in the trees: a coven has moved in, and it doesn't need to break your gates to hurt
you.

## The Coven Hut

56 to 80 blocks from the hall, in the swamp: a hut on mangrove stilts over a pond, purple-tinted windows, a smoking
chimney, lanterns on the deck, and inside brewing stands, candles and a black cat. On the bank in front stands **the
coven's cauldron** over a campfire. Chat says so: "Witch-light glimmers in the swamp beyond Fenwick: Coven Mother Morwen
Hollowmere has raised her coven's hut..."

## The Coven Mother

A witch with 40 more health who stays at her hut. **Hit her three times in a row** and she blinks 8 to 12 blocks away in
a burst of ender particles. The coven starts 8 strong and grows by 1 a day up to 14.

## The nightly hex

Every night the hut stands, the coven hexes one to three of your grown villagers: a swirl of witch-light, a cackle, and
"the coven has hexed 2 of its villagers". A hexed villager is ill: they work at half pace and walk slowly. The Nurse's
remedies don't touch it and it doesn't pass by itself. A [Remedy](remedy.md) cures it, and so does breaking the coven.

## Their raids

The coven's raiders are witches and zombie villagers in dark caps, "the hexed of other villages". They bring no rams and
no ladders. Behind a wall they stand 12 to 20 blocks outside it and throw splash potions over it: slowness, weakness or
poison at villagers, harming at guards, and a potion of healing for a hurt sister. So it's your archers on the walls who
can reach them. They leave at dawn.

## Breaking the coven

**Kill the Coven Mother, or break her cauldron** on the bank. Either way the hut stands empty, her band is gone, and
every hexed villager is cured at once.

## How it fits in

- **What it affects:** your villagers' health and pace, and so every job in the village.
- **What it can bring:** a hex can start a [plague](plague.md) in a Town or a City.
- **What helps:** archers on battle stations (see [Sieges](sieges.md)), Remedies from a [Physician](physician.md), and
  a [war party](war-party.md) to break the hut.
- **Switched off:** `witch_coven` under `raiderCultures` in the config file; every hex lifts on the hall's next round.
  A [Threat Level](threat-level.md) of 1 means witches never hex.
