# Village houses

Villages the world generates now and then grow one of our houses, a workplace with its villager already living in
it, so a player meets Builders, Guards, Nurses and the rest without having to make them. Every house and every
villager stands where it should: no holes in the ground and nobody stuck in a wall.

Roadmap items: B5, B6

## What a player sees

- In a plains, desert, savanna, snowy or taiga village, now and then a house of ours among the vanilla ones, built in
  that village's style: a **Builder's Workshop** (a Builder's Bench and a chest of building supplies) in about every
  other village, and the staffed houses (a Guard House, Clinic, Post Office, Storehouse, Carpenter's Workshop,
  Kitchen, Ferry House with a Travel Post, Orchard House, Flower Shop, Ranch House, Schoolhouse, Inn Room, Mortuary,
  Tinker's Shop, Sifting Shed, Compost Yard...). Each has a bed and a villager who takes its job block.
- With Cobblemon, the Pokémon jobs' houses as well: a Trainer's House (the commonest of all, so most villages have
  someone to battle), a Leader's Hall, a School, a Trade Hall, a Ball Workshop and a Fossil Lab, and the five of
  [Pokémon jobs' village houses](pokemon-village-houses.md). Without Cobblemon villages never grow these.
- The luxury workshops (Winery, Tailor's Shop, Print Shop, Jeweler's Workshop) only while their job is switched on.
- Repurposed Structures' villages (badlands, bamboo, birch, cherry, dark forest, giant taiga, jungle, mountains,
  mushroom, oak, swamp) grow them too, in the style that fits each best (desert for badlands, taiga for mountains...).
- The ground in front of every door is the ground the house landed on: no strip of invisible holes to fall into
  (B5).
- Villagers that come with a house (ours or vanilla's, and the zombie villagers of abandoned villages) stand on the
  floor, never with their head in a slab or wall where they would suffocate (B6, B10).

## How it works

- When the server starts, `VillageHouses` adds our templates (`aliveworkplace:village/<style>_<house>`, 165 of them
  in `data/aliveworkplace/structure/village/`) to the five vanilla house pools (and to a data pack's replacement of
  them) and to Repurposed Structures' house pools that exist. Each is a rigid legacy pool element with no processors.
- Weights, in pools whose vanilla houses add up to 70 to 90 and whose vanilla job houses weigh 2: the workshop 3,
  the Trainer's House 6, the Leader's Hall 5, the Ferry House 4, the Guard House and Storehouse 3, most others 2,
  the Mortuary, Sifting Shed, Compost Yard and four of the Pokémon jobs' houses 1.
- Which houses: `VillageHouses.houseNames` leaves out the Cobblemon houses without Cobblemon, the five Pokémon jobs'
  houses with `pokemonVillageHouses` off, and each luxury house whose job's switch is off (the Crafter's Workshop is
  never added since the Crafter was retired, 37.16a). This is read when the server starts, which is when the pools are
  filled: houses already grown stay.
- B5: villages place a legacy element with a processor that skips only air and structure blocks, so a
  `structure_void` in a template was placed as a real block, a block with no collision. Our templates stored a whole
  row of them in front of the door, at ground level: a trench a villager walking out fell into. The templates no
  longer contain any, and the ground strip in front of each door is checked for every template placed the way a
  village places it.
- B6: vanilla's villager templates store their villager at x+0.72, z+0.63 of its block, off its center, a block above
  the floor. A villager is 0.6 wide, so its box reaches 0.02 into the next block; in a 1-wide gap (vanilla's
  desert_small_house_7 corridor) it fell onto the step beside the gap with its head in the ceiling slab and
  suffocated. `StructureVillagers` centers every villager and zombie villager a structure template places in its
  block, unless the center is blocked and its own spot isn't (a fence post in the middle of the block).
- A house's outside can later be rebuilt in another village style around its room (23.10a, `VillagePieces`; see
  [Workplace looks](workplace-looks.md)).

## Switches

| Switch | Default | What it does |
|---|---|---|
| `pokemonVillageHouses` | on | Off: villages don't grow the five Pokémon jobs' houses (with Cobblemon). |
| `vintners` | on | Off: no Winery in villages. |
| `tailors` | on | Off: no Tailor's Shop in villages. |
| `printers` | on | Off: no Print Shop in villages. |
| `jewellers` | on | Off: no Jeweler's Workshop in villages. |

The weights have no switch. For screenshots only, the JVM properties `aliveworkplace.workshopWeight`,
`aliveworkplace.houseWeight` and `aliveworkplace.pokemonHouseWeight` override them.

## Saved data

Nothing of its own: a grown house is ordinary blocks, and its villager an ordinary villager with its job. The pools
are filled anew at every server start.

## Items, blocks, jobs, commands

No items or commands of its own. The houses' job blocks and jobs are on [Villager jobs](villager-jobs.md).

## Decisions

- Our houses join vanilla's pools rather than generating as structures of their own, so they appear on the streets
  of ordinary villages (and Repurposed Structures', which use vanilla's jigsaw names), and a data pack replacing a
  pool still gets them. Nether and ocean villages are left alone.
- A house whose job does nothing in this game (no Cobblemon, its switch off) isn't grown at all: its villager would
  stand in a job with nothing to do.

## Known limits

- The pools are read at server start: switching a job off doesn't remove houses already grown, and switching it on
  doesn't add any to villages already generated.
- B697 (open): without Cobblemon a jobless villager standing by a Training Post a player placed takes the Trainer job
  by itself; villages themselves never grow those houses without Cobblemon.

## Proof

- `VillageGameTests` (B5: `VillageGameTests.aVillagePlacedWorkshopLeavesNoStructureVoidInTheWorld` and
  `VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`; B6:
  `VillageGameTests.aVillagerFromAVanillaDesertHouseCorridorStandsOnTheFloor`; that every village type can grow the
  workshop and the other houses, and that each luxury house's villager takes its block).
- Showcase scene `village`: one village of each type, with the Pokémon jobs' houses.
