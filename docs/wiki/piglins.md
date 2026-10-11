# Piglin incursions

ROADMAP 32.10. Part of Milestone 32 (1.6), so it is **off for players until the milestone is finished**
(`Expansions.M32`); GameTests and the showcase run with it on. The piglins are a raider culture (see
[Raids and threats](raids.md)) whose lair (see [Lairs and the Defence page](lairs.md)) has a lit Nether portal they
come through, and who carry off the village's gold (see [Desert raiders](desert.md) for the culture before it).

Roadmap items: 32.10

## What a player sees

- **Who they come to.** A Town or a City with a way to the Nether: a lit Nether portal in its area or a finished
  Nether Gate (the netherworkers' trips draw them).
- **The Blackstone Outpost.** 56 to 80 blocks from the hall, on dry, fairly flat ground: a broken platform of
  blackstone and its bricks with netherrack worn through it; at the back, on a dais of polished blackstone bricks, a
  **lit portal** in an obsidian frame with crying obsidian at its corners and heaped beside it, flanked by two broken
  bastion pillars; a **basalt pillar** flying the black piglin banner with the gold snout; a **hoglin pen** of crimson
  fences on crimson nylium; the **hoard**, gold blocks heaped against a broken wall with the loot chest; a campfire in
  the middle. Chat says so ("A portal has been lit beyond Thornholm: Warlord Grukk the Gilded has raised a blackstone
  outpost 60 blocks west...").
- **The Warlord.** A piglin brute in full gold armour with a golden axe and 50 more health, his name over his head
  (one of twenty: "Warlord Vorgash Goldmaw"). He stays at his outpost.
- **The raiders**, all called "Piglin raider": piglins with golden swords (35%), piglins with crossbows (25%), piglin
  brutes with golden axes (25%) and hoglins as rams (15%). By night they **step out of the outpost's portal** (in a
  flash of portal light; the village's own portal when the outpost's isn't lit or loaded) and march on the village.
- **They never zombify** while they raid or hold the outpost, and they **go for the villagers and the guards**: their
  anger is set on the nearest villager in sight, as if it had struck them. Guards fight them all, hoglins too.
- **Plunder.** A piglin (not a hoglin) that comes within 4 blocks of a chest, barrel or other storage in the village
  takes the gold in it: gold ingots, nuggets, blocks, raw gold and golden tools, weapons and armour, **one stack at
  most**, carried on its head where you can see it. **Kill it and it drops the stack.** If it is still alive at dawn
  it carries the gold home, into the outpost's hoard, and the **siege report** the next morning says how much was
  carried off ("... 64 gold was carried off from the village's chests."), or the chronicle when there was no siege.
- **The Warlord's fall.** Kill him and the outpost is broken: its **portal goes out**, and every piglin and hoglin of
  his band left in the Overworld, at the outpost and raiding, **turns zombified** at once (zombified piglins and
  zoglins, dazed for a moment). Those that were raiding stay in the raid until it ends.
- **The hoard** (`aliveworkplace:chests/piglin_hoard`): gold ingots and blocks, gold nuggets, blackstone, gilded
  blackstone, crying obsidian, golden carrots or an enchanted golden axe, now and then **netherite scrap** (one in
  six) and the **snout banner pattern** (one in four), plus whatever gold the piglins carried home.

## How it works

- The culture is the data file `raider_cultures/piglin_incursion.json`: `where` (`min_rank` town, `nether_link`),
  weight 30, arrival `portal`, hours `night`, tactics `ram_gates` and `plunder`, the roster, the captain (with
  `zombifies_band`), the lair `camp/blackstone_outpost` (with `portal`, the Warlord before the portal, `captain_at`)
  and the loot.
- **Arrival `portal`** (`guard/VillageRaids`): with a lair whose file says `portal`, the raiders stand in its lowest
  portal block nearest the village (`threat/Piglins.portal`); else at the village's own portal; else at its edge.
- **A land lair's `near` and `far`** (new, optional): how far from the hall it makes camp; 80 and 104 when not given.
  The outpost says 56 and 80, so the raiders who step out of its portal reach the village.
- **Piglins in the Overworld** (`threat/Piglins`): every raider and every one of a lair's band that is a piglin, a
  brute or a hoglin is made immune to zombifying (the game's own flag, saved with the mob). Every second each raiding
  one with nothing to fight gets the nearest villager within 16 blocks as its anger target (a piglin's `angry_at`
  and attack target, a hoglin's attack target, as their brains set them when struck); with none in sight it is sent
  12 blocks on toward the nearest villager of the village (or the hall). Piglins shun soul fire, so the outpost is lit
  with plain lanterns and fire.
- **The tactic `plunder`** (`threat/Plunder`): every second, each raider of a culture naming it that isn't a ram and
  carries nothing looks within 4 blocks for a storage block inside the village's area and takes one stack of gold out
  of it through the loader's storage access. What it carries is kept on it (the attachment `plunder`, saved with the
  mob). A new tactic hook, `leave`, is called for each raider leaving at the end of its hours: a plunderer's stack goes
  into the storage block of its lair nearest the lair's middle and is counted for the hall.
- **A captain's `zombifies_band`** and **a lair's `portal`**: when the lair is broken (its captain killed) its portal
  blocks go, and the band (at the lair, and its raiders out in the village) is converted as the game converts
  piglins and hoglins, before the rest is sent away.
- **Strength.** 10 when the outpost is raised, 2 more a day, at most 16; at most 8 stand at the outpost.

## Switches

| Key | Default | What it does |
|---|---|---|
| `raiderCultures` | `piglin_incursion: true` | false: no outpost is raised, and one that stands sends no raids. |
| `sieges` | on with the milestone | false: no siege, so no rams at the gate and no siege report (the chronicle still says what was carried off). |

## Saved data

- `aliveworkplace_threats`: `plunder`, per hall the gold carried off that the village hasn't been told of yet
  (`hall`, `count`); default none.
- On each plunderer: the attachment `plunder` (the stack it carries).
- The lair is saved as every lair is (`aliveworkplace_bandit_camps`, 32.3).

## Items, blocks, jobs, commands

- Raider culture `aliveworkplace:piglin_incursion`.
- Structure `aliveworkplace:camp/blackstone_outpost` (21 x 12 x 19), drawn in `tools/blueprints/bandits.py`
  (`blackstone_outpost`).
- Loot table `aliveworkplace:chests/piglin_hoard`.

## Decisions

- The raiders come out of the outpost's portal, so the outpost stands closer than other lairs (56 to 80 blocks): from
  80 to 104 most raiders would be beyond the raid's reach before they arrived.
- The Warlord doesn't lead raids himself: the roadmap gives him no `leads`, and his death at the outpost is what puts
  the portal out.
- The zombified raiders stay raiders (guards fight them, they leave at dawn); the zombified at the outpost are left
  as ordinary zombified piglins and zoglins.
- What counts as gold: the five gold materials and any golden item that wears out (tools, weapons, armour), not golden
  food, clocks or bells.
- Strength 10, +2 a day, at most 16, and weight 30: the roadmap gives no numbers; as the other 1.6 cultures.

## Known limits

- A plunderer that leaves at dawn while the outpost isn't loaded takes its gold with it: it is counted as lost, and
  no hoard gets it.
- Hoglins shun Nether portals, so a hoglin ram mills about the portal for a moment before it follows the rest.

## Proof

- `PiglinGameTests.thePiglinFileIsAsTheItemSaysAndComesToATownWithAPortal`: the file, the template, the loot table and
  the texts; it comes to a Town with a lit portal, not without one, not to a Village, not when switched off.
- `PiglinGameTests.aRaidingPiglinStaysAPiglinAndAttacksAVillager`: still a piglin after 600 ticks (a plain one in the
  next pen turned zombified), and the villager was attacked.
- `PiglinGameTests.aGuardFightsARaidingHoglin`: the guard strikes the raiding hoglin, and leaves a plain one alone.
- `PiglinGameTests.aPiglinPassingAChestTakesItsGoldAndDropsItWhenKilled`: one stack taken (the rest stays, a chest 6
  blocks off is untouched, the hoglin ram takes nothing), shown on its head, kept through a save and load, all of it
  dropped when killed; gold carried home at dawn is counted, kept through a save and load, and in the siege report.
- `PiglinGameTests.theWarlordsDeathPutsOutThePortalAndZombifiesTheBand`: the outpost with its 12 portal blocks lit, the
  Warlord with 100 health in gold, a raid of 5 standing in the portal, then his death: the outpost broken, no portal
  block left, no piglin or hoglin left unturned, the 5 raiders zombified and still raiders.
- Showcase scene `piglins`: the outpost, a raid stepping out of its portal, a piglin carrying off a stack of gold.
