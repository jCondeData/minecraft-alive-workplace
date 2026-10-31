# The Gem Grower

A villager at a stonecutter, picked with an amethyst shard, who tends the gem beds round it: amethyst everywhere, and
with Cobblemon the tumblestones and (on 1.8) the 18 Type Gems. She picks only what is ripe, never the block it grows
on, and carries the picking to her chests.

Roadmap items: 28.11

## What a player sees

- **Getting one.** Stand a villager by a stonecutter and sneak-right-click them with an amethyst shard. A plain
  right-click does nothing; a villager with no free stonecutter within reach is told which block the job needs. Left
  alone, a jobless villager by a stonecutter still becomes a Mason (a clay ball also brings the Mason back). Works
  without Cobblemon. Put a chest by the stonecutter for her picking.
- **Amethyst.** Budding amethyst within 16 blocks of her stonecutter is a bed. She picks only full clusters (small,
  medium and large buds are left to grow), on every face of the budding block, and never breaks the budding block.
  The shards go into her chest.
- **Tumblestones** (with Cobblemon). Tumblestone, Sky Tumblestone and Black Tumblestone, planted from her chests
  against lava or magma (never against plain stone), as a small bud sitting on the nearest sturdy face; up to 4
  plantings round one heat block. Full clusters are picked and the lava or magma stays.
- **Type Gems** (with Cobblemon 1.8). Each type's Gem Block, set from her chests on a Deepslate Crystal Core; the
  stage-3 cluster on it is picked and the Gem Block kept.
- **Blank TMs** (with Cobblemon 1.8). With glass and amethyst shards in her chests she makes Blank TMs at the
  stonecutter by Cobblemon's recipe (two a craft) until her chests hold 8.
- **Orders.** Sneak-right-click her with an empty hand: a screen lists every bed ("Gem beds for <her name>"). Click a
  bed to keep it: she then keeps only the picked beds and leaves the rest, even ripe clusters. With nothing picked
  ("Nothing picked: keeps every bed she has the makings for") she keeps every bed, and a bed she has nothing to plant
  with doesn't hold up the others; she says "A bed wants planting: put what it's planted with in her chests".
- **Trades.** Amethyst shards (4 for an emerald) and glass bought (Novice); tumblestones (with Cobblemon) and tinted
  glass (Apprentice); a spyglass or shards bought, and the Gem Grotto blueprint (Journeyman); two Type Gems (Expert,
  with Cobblemon); amethyst blocks (Master). Without Cobblemon the Cobblemon trades are left out, never sold as nothing.
- **Partners.** Rock and Steel Pokémon pastured nearby help her; the show: a Rock partner taps the ripe cluster loose.
- **Her status line** says what she is doing: "Tending 3 gem beds", "Her stonecutter is gone", "No gem beds within 16
  blocks of her stonecutter (...)", "Gem Growers are switched off (config gemGrowers)", and so on.

## How it works

- Beds are found by an incremental scan round the stonecutter (16 blocks each way, at most 4,096 blocks a tick, up to
  64 beds) and remembered on her. A bed is the block its file names as `touch`; its ripe clusters are looked for
  within 2 blocks of it.
- A ripe cluster is picked as a player with an iron pickaxe would (the block's own loot); the touch block stays.
- A planted bed wants planting while fewer than its file's `plants` growths stand round it and there is a free spot:
  the touch block's faces in the order up, north, south, east, west, down; a directional block sits on the touch block
  first, then the floor, then any sturdy face, facing away from it.
- Her chests keep 16 of each thing she plants, and of glass and shards; the porter takes the rest of her picking to the
  store.
- Blank TMs come from Cobblemon's own `blank_tm` recipe, one craft every 200 ticks, only while what her chests hold
  plus what one craft makes stays at or under 8 (B490: the recipe makes 2, so with 7 she makes none and stays on 7).
- Her stonecutter broken: she stops and says so. Config off: she stands idle and says so.

## Switches

| Key | Default | What it does |
|---|---|---|
| `gemGrowers` | on | Off: a shard picks no job, and growers already hired stand idle |

## Saved data

On the villager: her orders (`gem_orders`, a list of bed names; none means every bed), the beds she has found
(`gem_beds`, positions) and how many clusters she has picked (`gems_picked`, shown in her title). All three survive a
save and reload; a villager saved before 28.11 has none.

## Items, blocks, jobs, commands

- Job: the Gem Grower (`aliveworkplace:gem_grower`) at the vanilla stonecutter (the Mason's block); picked with an
  amethyst shard.
- Bed files: `data/aliveworkplace/gem_beds/<name>.json` (`plant`, `touch`, `grows`, `ripe`, `harvest`, `plants`,
  `requires`, `order`). Shipped: `aliveworkplace:amethyst`, `aliveworkplace:tumblestone`,
  `aliveworkplace:sky_tumblestone`, `aliveworkplace:black_tumblestone` and the 18 Type Gem beds such as
  `aliveworkplace:fire_gem`. A data pack can add more; a malformed file is logged with its name and field, and skipped.
- Build: the Gem Grotto (28.13). No items or commands of its own.

## Decisions

- 28.11: the stonecutter stays the Mason's when a villager takes it by itself; the Gem Grower is only ever picked with a
  shard.
- A bed whose `requires` isn't met (Cobblemon missing, or no Type Gems before 1.8) is passed over quietly.
- The Expert's Type Gem trade needs only Cobblemon (the gems are held items on 1.7.3 too); only the Gem Block beds need
  1.8.

## Known limits

- Any ripe cluster of a bed's kind within 2 blocks of its touch block is hers, including one a player placed there.
- The "no beds" line names magma, lava and the crystal core even without Cobblemon, where only amethyst counts.

## Proof

GameTests: `GemGrowerGameTests` (a full cluster picked and the budding block kept, a planted bed waiting for its
makings, the switch off, a broken stonecutter, the orders screen, malformed bed files, her text),
`QaGemGrowerGameTests` (16 blocks out kept and 17 not; clusters on all six faces), `QaGemGrowerOrdersGameTests` (large
and medium buds left; a bed without makings doesn't hold up the others; orders through the real click and screen, then
unpicked; a save and reload; the Novice shards and no empty trade without Cobblemon), `JewellerGameTests` (the shard,
the clay ball and the nugget at one stonecutter), `QaGemGrowerHireGameTests` (hired through the real sneak-click and
told so, a plain click hiring nobody, no stonecutter near, a Mason's stonecutter not taken from him). With Cobblemon: `GemGrowerCompatTests` (a tumblestone on magma, a
Fire Gem Block on a core with its cluster picked and Blank TMs, the beds loading), `QaGemGrowerCompatTests` (planted
against lava, never stone; 4 plantings round a magma block and no fifth; Blank TMs up to 8 on 1.8.1; tumblestones from
the Apprentice and Type Gems from the Expert), `QaGemGrowerPartnerCompatTests` (Rock and Steel partners; a Geodude
taps the ripe cluster loose as she picks it).

Showcase scene: `gem_grower`.
