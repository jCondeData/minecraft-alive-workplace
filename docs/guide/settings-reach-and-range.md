# Reach and range settings

```infobox
type: Setting
image: https://jcondedata.github.io/minecraft-alive-workplace/config/still-1.jpg
caption: The settings screen's numbers
Chest Reach: 8 blocks (2 to 32)
Build Distance: 128 blocks (16 to 256)
Quarry, Field, Tree Farm, Orchard and Berry Plot Distance: 128 blocks each (16 to 256)
Guard Patrol Range: 24 blocks (8 to 64)
Lumberjack Range: 16 blocks (4 to 48)
Orchard Range: 16 blocks (4 to 48)
Fisherman Range: 16 blocks (4 to 48)
Explorer Range: 48 blocks (16 to 128)
Partner Reach: 16 blocks (4 to 48)
Postman Round: 64 blocks (16 to 256)
Village Size: 48 blocks (0 to 128)
Village Hall Reach: 64 blocks (16 to 160)
categories: Settings
related: settings.md, jobs.md, village-hall.md, settings-worker-limits-and-performance.md
```

Sixteen sliders on the settings screen say how far things reach: how far a worker looks for chests, trees or water, how
far a builder or postman will go, and how big a village is. All are in blocks. A number out of range is set to the
nearest allowed one. See [Settings](settings.md) for where to change them.

## Chests and builds

- **Chest Reach** (normally 8): chests and barrels this close to a workstation are that villager's supply chests. Raise
  it if your chests sit farther from the tables; lower it to keep two workshops' chests apart. Every job uses it (see
  [Giving villagers jobs](jobs.md)).
- **Build Distance** (normally 128): how far from their Blueprint Table a builder takes a build **outside the village**.
  Anywhere inside the village the table stands in is always in reach, however big the village grows (its
  [Village Hall](village-hall.md)'s reach, and every cell painted into a [City Plan](city-plan.md) zone). See
  [The Builder and blueprints](builder.md).
- **Quarry, Field, Tree Farm, Orchard and Berry Plot Distance** (normally 128 each): the same for the areas you mark for
  a miner, a farmer, a lumberjack, an orchard keeper and a berry breeder. Anywhere in the village is in reach; outside
  it, keep the area within this distance of the worker's block. So you can dig your quarry well away from the houses.
  Every distance here is measured across the ground: how deep a quarry goes or how tall a build is never counts.
  A spot too far is refused with what to do: "That spot is outside Mapleridge and 140 blocks from the Miner's Bench the
  miner works at — keep it within 128."

## Workers out in the world

- **Guard Patrol Range** (normally 24): how far from their grindstone guards patrol and fight. During a
  [raid](raids.md) they fight anywhere in the village anyway. See [Threats and defense](defence.md).
- **Lumberjack Range** (16): how far from their fletching table lumberjacks cut trees.
- **Orchard Range** (16): how far from their composter orchard keepers pick fruit.
- **Fisherman Range** (16): how far from their workstation fishermen look for water. See
  [Jobs on the land](jobs-land.md) for all three.
- **Explorer Range** (48): how far from their cartography table explorers go on an expedition.
- **Partner Reach** (16): with Cobblemon, how close a Pasture Block must be to a workstation for its Pokémon to help
  that worker. See [Pokémon and villagers](pokemon.md).
- **Postman Round** (64): how far from their mailbox a postman walks to deliver. Mail for farther away isn't lost: it
  arrives at dawn. See [Porters, post and services](jobs-services.md).

## How big a village is

- **Village Size** (48): workers whose workstations are this close together count as one village and share their
  chests. At 0, every worker keeps to their own chests.
- **Village Hall Reach** (64): how far from a [Village Hall](village-hall.md) its village reaches. Everyone within it
  belongs to the hall: their names, moods, meals and the village's needs, ranks, treasury and
  [protection](village-protection.md) all go by this distance.

## How they interact

Bigger ranges mean workers walk farther, which is slower for them and a little heavier on the server. If a big server
struggles, see [Worker limits and performance](settings-worker-limits-and-performance.md) too.
