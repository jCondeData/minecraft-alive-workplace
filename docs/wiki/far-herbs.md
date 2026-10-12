# The far herbs and the Three-Herb Tonic

Eight rare plants that grow only in far-off biomes, and the tonic a healer makes from them: the first part of the
Sickness story (ROADMAP 31.17; the healer and the story itself are [The Sickness](the-sickness.md), 31.18). Part of
the 1.5 expansion.

Roadmap items: 31.17

## What a player sees

- **Eight new plants**, drawn like vanilla's flowers: **Frostbloom** (pale blue bells), **Sunthistle** (a spiky yellow
  thistle), **Mirewort** (reeds with purple buds), **Peakmoss** (a silver-green tuft), **Jungle Orchid** (a magenta
  orchid), **Sea Lavender** (small lilac sprays), **Glowcap** (a pale mushroom that glows) and **Petal Balm** (a herb
  with pink leaves).
- **They are never found in a normal world.** A story arc plants them: a patch of 5 to 9 of one herb, on its own
  ground, somewhere in its biomes. Nothing in an existing world changes.
- **They stay put.** Like a flower, a herb can be picked (it drops itself) and planted again on its own ground, but it
  never spreads, never grows into anything and takes no bone meal.
- **The Glowcap gives light 5** and only grows in shade: something must stand over it, such as a dark forest's leaves.
- **The Three-Herb Tonic** is a round bottle of green tonic with leaves tied round its neck. In the Sickness story a
  village's healer brews it from three far herbs and takes it round to cure the Gray Cough (31.18).

## How it works

Each herb has its biomes and its ground:

| Herb | Biomes | Ground |
|---|---|---|
| Frostbloom | snowy plains, snowy slopes, groves, ice spikes, snowy taiga | snow block, grass |
| Sunthistle | desert, every badlands | sand, red sand, terracotta |
| Mirewort | swamp, mangrove swamp | mud, grass |
| Peakmoss | windswept hills, windswept gravelly hills, stony peaks, jagged peaks, meadow | stone, andesite, diorite, granite, grass |
| Jungle Orchid | every jungle (jungle, sparse, bamboo) | grass |
| Sea Lavender | beach, snowy beach, stony shore | sand, gravel |
| Glowcap | dark forest | grass, podzol, and in shade |
| Petal Balm | cherry grove | grass |

- **Biomes** are the biome tag `aliveworkplace:herb_biomes/<herb>` (in `data/aliveworkplace/tags/worldgen/biome/herb_biomes/`),
  so a datapack or a biome mod can add its own biomes. They say where an arc looks for the herb.
- **Ground** is the block tag `aliveworkplace:herb_ground/<herb>` (in `data/aliveworkplace/tags/block/herb_ground/`), so
  a datapack can add ground too. A herb only stands on its ground, whether a player or an arc plants it, and pops off
  when the block under it goes, like a flower.
- **Shade** for the Glowcap: a block that stops movement or a leaf block somewhere above it in its column. Light alone
  isn't used, because light changes with the time of day and a herb that pops off at noon would be lost.
- **The `herb_patch` arc effect**: `{"type": "herb_patch", "herb": "aliveworkplace:glowcap", "key": "glowcaps", "rule": {...}}`.
  The rule is any `place` rule (ring, road, biome, after, offset, fort); without one it is the nearest of the herb's
  own biomes (`#aliveworkplace:herb_biomes/<herb>`). The spot is noted at once, like a `place`, and planted once a
  player comes near and the ground within 6 blocks is loaded: 5 to 9 herbs, on the surface of random columns within 6
  blocks (under leaves, never in water), only where the herb could stand. With less fit ground it plants what fits.
  The place is then done and the arc's `<key>_done` flag is set, and `give_map` can point at it.

## Switches

none. The herbs are only planted by story arcs, so the arcs' own switches decide whether any appear.

## Saved data

- A herb patch is saved as one of the arc's places (`ArcState`), with the stage `herb_patch:<herb id>` in place of a
  template. Older saves have no such places and read as before.
- The herbs are ordinary blocks; nothing else is saved.

## Items, blocks, jobs, commands

- Blocks with their items: `aliveworkplace:frostbloom`, `aliveworkplace:sunthistle`, `aliveworkplace:mirewort`,
  `aliveworkplace:peakmoss`, `aliveworkplace:jungle_orchid`, `aliveworkplace:sea_lavender`, `aliveworkplace:glowcap`,
  `aliveworkplace:petal_balm`. All in the mod's creative tab.
- Item: `aliveworkplace:three_herb_tonic` (stacks to 16).
- Tags: the block and item tag `#aliveworkplace:herbs` (all eight), the biome tags and ground tags above.

## Decisions

- The herbs never generate and never spread, so adding them changes no world (31.17).
- Biomes and ground are tags rather than code, so datapacks and biome mods can add to both (31.17).
- Glowcap's shade is "something above it", not a light level, so it doesn't pop off with the daylight (31.17, lane c).
- A patch waits, like a `place`, until a player comes near, so it is planted on loaded, real ground (31.17, lane c).
- The tonic is its own item, not 32.18's Remedy (31.17).

## Known limits

- Only the Sickness story (31.18) plants herbs and brews the Three-Herb Tonic; the tonic can't be crafted or drunk by a
  player.
- The herbs can't go in a flower pot.

## Proof

- `HerbGameTests`: `frostbloomGrowsOnSnowAndStays`, `sunthistleGrowsOnRedSandAndStays`, `mirewortGrowsOnMudAndStays`,
  `peakmossGrowsOnStoneAndStays`, `jungleOrchidGrowsOnGrassAndStays`, `seaLavenderGrowsOnGravelAndStays`,
  `glowcapGrowsOnPodzolInShadeAndStays`, `petalBalmGrowsOnGrassAndStays` (placed by a player on its ground and not on
  obsidian, unchanged by 100 random ticks, no bone meal, no spreading, breaks into its item, pops off when its ground
  goes); `eachHerbKeepsToItsGround`, `glowcapGlowsAndNeedsShade`, `herbsHaveBiomesItemsAndNames`;
  `aPatchPlantsOnlyOnFitGround`, `aPatchPlantsOnlyWhatFits`, `aGlowcapPatchKeepsToTheShade`,
  `theArcPlantsAPatchWhereItsRuleSays`.
- Showcase scenes: `herbs` (all eight patches on their ground, and the tonic in hand).
