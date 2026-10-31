# Desert raiders

ROADMAP 32.9. Part of Milestone 32 (1.6), switched on for players in 1.0 with every other expansion
(`Expansions.M32`); GameTests and the showcase run with it on. The desert raiders are a raider culture (see
[Raids and threats](raids.md)) whose lair (see [Lairs and the Defense page](lairs.md)) is an old tomb, and who get over
walls with sand where pillagers use ladders (see [Drowned pirates](pirates.md) for the culture before it).

Roadmap items: 32.9, B382, B437

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
- **The raiders**, all called "Desert raider": husks (55%), husks in gold armor with iron swords (25%) and skeleton
  archers in gold helmets (20%).
- **Sand ramps.** Where pillagers set ladders, the plain husks pile sand against the wall: a block every half second,
  one up for one out, so the ramp rises step by step against the wall until it is level with the top. Then the
  raiders walk up it and drop inside. Chat says so ("Sand is piled against the walls of Thornholm!"). When the raid is
  over every block of the ramp is gone; nothing else is touched and nothing drops. The ramp buries dead bushes, grass
  and snow at the wall's foot (they are gone after the raid), but never your flowers or blocks: a flower in its way
  moves the ramp along the wall, and the flower stays. A ramp standing when the world is saved and loaded again is
  still taken away when the raid is over. Sand you put there
  yourself, plain or red (the badlands' ground), is no obstacle: the ramp starts in it, and it stays after the raid.
- **Until noon.** Husks don't burn, so a desert siege doesn't end at dawn: the raiders stay, and the siege goes on, until
  noon.
- **The sarcophagus** (`aliveworkplace:chests/pharaohs_tomb`): gold, emeralds, a golden apple, gold nuggets, bones,
  lapis or an enchanted book, now and then a diamond, and one time in five the dune armor trim.

## How it works

- The culture is the data file `raider_cultures/desert_raiders.json`: `where` (desert, badlands, eroded and wooded
  badlands; Town and up), weight 30, arrival `lair`, hours `until_noon`, tactics `sand_ramps`, the roster, the
  captain, the lair `camp/tomb_camp` (the Pharaoh stands by his sarcophagus, `captain_at`) and the loot.
- **A captain who raises the fallen** (`threat/Risings`): a culture's captain may have `raises` (`entity`, `radius`,
  `delay` in ticks, `health` as a share of full health). He carries the tag `aliveworkplace_raiser`; a mob of that kind
  dying within the radius of a living one is brought back after the delay as the same mob (its gear, its raid, its
  role), tagged `aliveworkplace_risen` so it never rises twice.
- **Sand ramps** (`threat/SandRamps`, tactic `sand_ramps`): a raider with the role `climber` whose path to the hall
  ends at a wall (two to 8 blocks high, not a gate) starts a ramp there. Every block of the ramp goes only into air
  or a wild plant it buries (the same rule as ladders, `threat/Buriable`), or sand already there (any sand, the `minecraft:sand` block tag: plain, red or suspicious, B437), on solid ground; the blocks go on in stages (each stage a whole ramp one higher than the last), every 10 ticks while
  the climber is near. At most 2 ramps a siege. Once the ramp stands, the raiders go up it and over.
  A raider is let go to fight once it is down inside on its feet, no higher than a step above the ramp's foot (a
  slab or a stair's lower step one up counts as down inside; B330).
  It drops in at the first column past the wall with nothing from two above the foot up, and once wholly in or past
  that column it is let go whatever the ground's height there, a terrace two up as well (B331).
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
- The plain husks are the climbers who pile sand; the armored husks and the archers follow up the ramp.
- A ramp needs flat-enough ground: every column of it must stand on something solid (no sand is poured into a hole),
  and a flower or any block a player put in the way moves it along the wall; the wild ground's dead bushes, grass and
  fallen snow are buried and gone after the raid (B553), and nothing else is ever replaced.
- The gold armor drops as the game decides for raiders (as the warband's iron).

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
- `DesertRaiderGameTests.aHuskComingDownOntoAnInnerStepIsLetGo`: a husk landing on a bottom slab one above the
  ramp's foot, behind a wall four high, is let go there (B330).
- `DesertRaiderGameTests.aHuskComingDownOntoARaisedTerraceIsLetGo`: a husk coming down onto a terrace two above the
  ramp's foot behind the wall is let go there (B331).
- `DesertRaiderGameTests.noSandWithMobGriefingOff`.
- `DesertRaiderGameTests.theDesertSiegeIsStillOnAtMidMorningAndOverAtNoon`.
- Showcase scene `desert_raiders`: the tomb camp, a sand ramp rising against a Stone Wall and a husk getting up again.
