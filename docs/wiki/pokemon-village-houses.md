# Pokémon jobs' village houses

With Cobblemon, villages sometimes grow a house for one of the five Pokémon jobs, built in the village's own style,
with its villager already in the job.

Roadmap items: 28.15

## What a player sees

- Five houses, each in all five village styles (plains, desert, savanna, snowy, taiga): a **Pokémon Center** (a
  counter with a Healing Machine and a PC; a Nurse), a **Camp Kitchen** (a Camp Cook at a Campfire Pot), a **Berry
  Nursery** (a Berry Breeder by her wet berry beds), a **Daycare** (a Daycare Keeper at a Pasture Block) and a **Gem
  Grotto** (a Gem Grower at a stonecutter, an amethyst niche, tumblestone ledges on magma behind glass: no open lava in
  a village).
- Each house has exactly one job block and one villager, who already has that job when the village is found.
- The Pokémon Center is the one you meet most often (it is twice as likely as each of the other four).
- Without Cobblemon, villages never grow them; neither do they with `pokemonVillageHouses` off (houses already grown
  stay).

## How it works

- The houses join the village pools that grow the other Pokémon houses (the trainer's house and the rest), with
  weights 2 for the Pokémon Center and 1 for each of the others.
- The 25 templates are written by `tools/blueprints/generate.py` through `tools/blueprints/village.py`'s
  `village_house` fit-outs; none holds a structure void (B5), and the villager stands in open space (B6).
- The switch is read when the server starts, which is when the pools are filled.

## Switches

| Key | Default | What it does |
|---|---|---|
| `pokemonVillageHouses` | on | Off: villages don't grow the five Pokémon jobs' houses (houses already grown stay) |

## Saved data

None of its own: a grown house is ordinary blocks, and its villager is an ordinary villager in the job.

## Items, blocks, jobs, commands

- Jobs: `aliveworkplace:nurse`, `aliveworkplace:camp_cook`, `aliveworkplace:berry_breeder`,
  `aliveworkplace:daycare_keeper`, `aliveworkplace:gem_grower`.
- Templates: `data/aliveworkplace/structure/village/<style>_<house>.nbt`.
- No commands.

## Decisions

- 28.15: the Gem Grotto's heat is magma behind glass, never open lava, so a village never starts a fire.

## Known limits

- Needs Cobblemon; only villages generated after the mod is installed grow them.

## Proof

GameTests with Cobblemon: `VillageCompatTests` (one style of each house placed with its villager in the job, and the
switch off), `QaPokemonVillageHousesCompatTests` (all 25 templates: one job block of the right job, one villager in
it, no structure void, no lava).

Showcase scene: `village`.
