# Pokémon jobs' village houses

With Cobblemon, villages sometimes grow a house for one of the five Pokémon jobs, built in the village's own style,
with its villager already in the job.

Roadmap items: 28.15, 52.2

## What a player sees

- Five houses, each in all five village styles (plains, desert, savanna, snowy, taiga): a **Pokémon Center** (a
  counter with a Healing Machine and a PC; a Nurse), a **Camp Kitchen** (a Camp Cook at a Campfire Pot), a **Berry
  Nursery** (a Berry Breeder by her wet berry beds), a **Daycare** (a Daycare Keeper at a Pasture Block) and a **Gem
  Grotto** (a Gem Grower at a stonecutter, an amethyst niche, tumblestone ledges on magma behind glass: no open lava in
  a village).
- Each house has exactly one job block and one villager, who already has that job when the village is found.
- The Camp Kitchen (redrawn in 52.2 with the rebuilt buildable's character) is a square timber room under a steep
  bell-cast pyramid roof of the style's roofing, a copper-capped louvre at its peak with a lantern in it, right over the
  Campfire Pot, which stands alone on a 3 x 3 brick hearth in the middle of the room (template (4, 1, 5)); the chest
  against the west wall in the back corner, a bale and a bench facing the fire beside it, shuttered windows, lanterns
  hung from the flared eave either side of the door, and a lean-to of Hearty Grain bales and split logs on the east.
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
- 52.2: the village Camp Kitchen keeps every cell the village contract reads (the jigsaw at (4, 1, 0), the door at
  (4, 1, 2) and its step, the bed foot (6, 1, 6) and head (6, 1, 7), one pot with its `PotComponent`, one loot chest,
  one Camp Cook, 12 x 11 x 11); only the shell and the room's layout changed (the pot moved from (3, 1, 7) to the
  middle, under the louvre; the chest from (4, 1, 7) to (2, 1, 7)).

## Known limits

- Needs Cobblemon; only villages generated after the mod is installed grow them.

## Proof

GameTests with Cobblemon: `VillageCompatTests` (one style of each house placed with its villager in the job, and the
switch off), `QaPokemonVillageHousesCompatTests` (all 25 templates: one job block of the right job, one villager in
it, no structure void, no lava).

Showcase scene: `village`.
