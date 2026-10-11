# Desert raiders

ROADMAP 32.9. Part of Milestone 32 (1.6), so it is **off for players until the milestone is finished**
(`Expansions.M32`); GameTests and the showcase run with it on. The desert raiders are a raider culture (see
[Raids and threats](raids.md)) whose lair (see [Lairs and the Defence page](lairs.md)) is an old tomb, and who get over
walls with sand where pillagers use ladders (see [Drowned pirates](pirates.md) for the culture before it).

Roadmap items: 32.9

## What a player sees

- **The Tomb Camp.** By a Town or a City in the desert or the badlands, desert raiders may open their **Tomb Camp**: a
  half-buried sandstone tomb with a stepped roof, an orange terracotta band and a doorway between chiseled pillars,
  dunes heaped against its sides; a cut sandstone **obelisk** with a gold cap; two sun-bleached awnings over seats and
  a fire pit; bone blocks half sunk in the sand, a skull and dead bushes. Inside, by candlelight, the **Pharaoh's
  sarcophagus** (the loot chest boxed in smooth sandstone). Chat says so ("Sand on the wind beyond Thornholm: Pharaoh
  Neferkaru has risen from an old tomb 40 blocks west with his desert raiders...").
- **The Pharaoh.** A husk in a golden helmet and chestplate with a golden sword and 50 more health, his name over his
  head (one of twenty: "Pharaoh Khaemhet the Undying"). He leads the raids on a City himself. **While he lives, a
  husk that falls within 24 blocks of him gets up again** three seconds later, where it fell, at half health and in a
  burst of sand. It rises only once, and none rises once he is dead.
- **The raiders**, all called "Desert raider": husks (55%), husks in gold armour with iron swords (25%) and skeleton
  archers in gold helmets (20%).
- **Sand ramps.** Where pillagers set ladders, the plain husks pile sand against the wall: a block every half second,
  one up for one out, so the ramp rises step by step against the wall until it is level with the top. Then the
  raiders walk up it and drop inside. Chat says so ("Sand is piled against the walls of Thornholm!"). When the raid is
  over every block of the ramp is gone; nothing else is touched and nothing drops.
- **Until noon.** Husks don't burn, so a desert siege doesn't end at dawn: the raiders stay, and the siege goes on, until
  noon.
- **The sarcophagus** (`aliveworkplace:chests/pharaohs_tomb`): gold, emeralds, a golden apple, gold nuggets, bones,
  lapis or an enchanted book, now and then a diamond, and one time in five the dune armour trim.

## How it works

- The culture is the data file `raider_cultures/desert_raiders.json`: `where` (desert, badlands, eroded and wooded
  badlands; Town and up), weight 30, arrival `lair`, hours `until_noon`, tactics `sand_ramps`, the roster, the
  captain, the lair `camp/tomb_camp` (the Pharaoh stands by his sarcophagus, `captain_at`) and the loot.
- **A captain who raises the fallen** (`threat/Risings`): a culture's captain may have `raises` (`entity`, `radius`,
  `delay` in ticks, `health` as a share of full health). He carries the tag `aliveworkplace_raiser`; a mob of that kind
  dying within the radius of a living one is brought back after the delay as the same mob (its gear, its raid, its
  role), tagged `aliveworkplace_risen` so it never rises twice.
- **Sand ramps** (`threat/SandRamps`, tactic `sand_ramps`): a raider with the role `climber` whose path to the hall
  ends at a wall (two to 8 blocks high, not a gate) starts a ramp there. Every block of the ramp goes only into air,
  on solid ground; the blocks go on in stages (each stage a whole ramp one higher than the last), every 10 ticks while
  the climber is near. At most 2 ramps a siege. Once the ramp stands, the raiders go up it and over.
- With the game's mob griefing rule off no sand is piled.
- **Taken away after the siege** like ladders: every block is recorded with the siege and becomes air when the raid
  is over, also after a restart.
- **Strength.** 10 when the camp opens, 2 more a day, at most 16; at most 8 stand at the camp.

## Switches

| Key | Default | What it does |
|---|---|---|
| `raiderCultures` | `desert_raiders: true` | false: no tomb camp opens, and one that stands sends no raids. |
| `sieges` | on with the milestone | false: no siege, so no sand ramps. |

## Saved data

- With each siege (`aliveworkplace_threats`): `sand_ramps` (every block of sand piled), `ramp_sites` and `ramp_dirs`
  (each ramp's foot and the side of its wall) and `ramped` (the players were told). All default to none in an older
  save.
- The lair is saved as every lair is (`aliveworkplace_bandit_camps`, 32.3). Husks waiting the three seconds to rise
  are not saved.

## Items, blocks, jobs, commands

- Raider culture `aliveworkplace:desert_raiders`.
- Structure `aliveworkplace:camp/tomb_camp` (19 x 11 x 19), drawn in `tools/blueprints/bandits.py` (`tomb_camp`).
- Loot table `aliveworkplace:chests/pharaohs_tomb`.

## Decisions

- The Pharaoh leads the raids from a City on, as the Warlord does; at a Town his husks rise only round his camp.
- Strength 10, +2 a day, at most 16, and weight 30: the roadmap gives no numbers; as the warband.
- The plain husks are the climbers who pile sand; the armoured husks and the archers follow up the ramp.
- A ramp needs flat-enough ground: every column of it must stand on something solid (no sand is poured into a hole),
  and a dead bush or grass in the way moves it along the wall, so no block but air is ever replaced.
- The gold armour drops as the game decides for raiders (as the warband's iron).

## Known limits

- A husk killed within three seconds of a restart doesn't rise.
- Sand a player knocks loose from a ramp (by digging under it) falls as sand does, and the block it lands as is
  not taken away after the siege.

## Proof

- `DesertRaiderGameTests.theDesertRaidersFileIsAsTheItemSays`: the file, the template, the loot table and the texts.
- `DesertRaiderGameTests.aHuskRisesOnceNearTheLivingPharaohAndNotAfterHisDeath`: risen after 3 s, not before, at
  half health, in its gear and raid, at the spot; killed again it stays dead; none rises after the Pharaoh's death.
- `DesertRaiderGameTests.aSandRampReachesTheTopOfTheStoneWallAndIsGoneAfterTheSiege`: a block at a time, only the
  recorded sand changes, a save and load half-way, the top level with the wall's, the husk over by the ramp, nothing
  left or changed after the siege and its lifting.
- `DesertRaiderGameTests.noSandWithMobGriefingOff`.
- `DesertRaiderGameTests.theDesertSiegeIsStillOnAtMidMorningAndOverAtNoon`.
- Showcase scene `desert_raiders`: the tomb camp, a sand ramp rising against a Stone Wall and a husk getting up again.
