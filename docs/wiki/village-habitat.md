# The village's own habitat

With Cobblemon 1.8, a village's Expert Habitat Keeper founds one Habitat Block of the village's own under the centre
stone of a finished Habitat Garden, with Pokémon that suit the biome, and every morning visits the natural Habitat
Blocks round her pasture to post who comes today.

Roadmap items: 28.14

## What a player sees

- **Founding.** Build a Habitat Garden from the Blueprint Table and let a Habitat Keeper reach Expert. Once the garden
  is finished she puts a Habitat Block in Cobblemon's natural mode under its mossy centre stone, looking like the stone
  it replaces. The chronicle says so ("<keeper> founded the village's own habitat: <the pool's name>"). One per village;
  it belongs to the village (protection covers it).
- **The biome's Pokémon.** The pool comes from the biome: plains a flowerbed clearing, a forest a berry patch, a desert
  an oasis, a swamp a peat bog, and so on (20 pool files; anywhere else a zen garden). Cobblemon's own spawner then
  brings Pokémon of that pool round the block.
- **Tending.** Each morning she visits the natural Habitat Blocks within 64 blocks of her pasture (the village's own
  and any Cobblemon habitat structure nearby). The hall's list shows today's phase of each: "Lush Cenote, today:
  Lotad, Wooper, Goomy".
- **Taking it down.** Taking the garden down removes its Habitat Block with no drop; the village may then found another.
- On Cobblemon 1.7 nothing is founded, and her page on the hall says "The village's own Habitat Block needs Cobblemon
  1.8; this server has an older one".

## How it works

- She founds it only as an Expert (level 4 and up), only on a garden the builders finished, only while its centre is
  still the template's mossy cobblestone (or a moss block), and only when the village has none yet.
- The block is set as Cobblemon's habitat structures set theirs: natural spawning style, the biome's pool id, the
  centre stone as its mimic, a 12-block range of influence.
- Tending finds the blocks through the loaded chunks' block entities, never by scanning blocks; a block in its
  activated (player-set) style isn't hers to tend.

## Switches

| Key | Default | What it does |
|---|---|---|
| `villageHabitats` | on | Off: no keeper founds a village habitat |

## Saved data

The Village Hall keeps the position of the village's habitat (`habitat`); a hall saved before 28.14 has none. The
block's own settings are Cobblemon's.

## Items, blocks, jobs, commands

- Block: Cobblemon's own Habitat Block (it has no recipe and drops nothing in survival).
- Job: the Habitat Keeper (`aliveworkplace:habitat_keeper`); build: the Habitat Garden.
- Pool files: `data/aliveworkplace/village_habitats/<name>.json` (a biome tag and one of Cobblemon's pools each).
- No commands.

## Decisions

- 28.14: one village habitat per village, on by default (the owner's call whether survival villages may have a Habitat
  Block at all, since Cobblemon gives none).
- B88: the garden's centre stone is mossy cobblestone in the template; founding takes that or a moss block.

## Known limits

- Needs Cobblemon 1.8.
- Tending sees only loaded chunks.

## Proof

GameTests with Cobblemon (the founding ones on 1.8.1, `-Pcobblemon18=true`): `VillageHabitatCompatTests` (4: the
founding, every biome's pool, a forced spawn round from the pool, the take-down), `QaVillageHabitatCompatTests` (2: an
Expert's only and on a finished garden; tending reaches 64 blocks and not 65, and never an activated block),
`QaStageTwoCompatTests` (the switch off).

Showcase scene: `village_habitat`.
