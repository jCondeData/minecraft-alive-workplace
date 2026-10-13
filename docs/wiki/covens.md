# Witch covens

ROADMAP 32.11. Part of Milestone 32 (1.6), switched on for players in 1.0 with every other expansion
(`Expansions.M32`); GameTests and the showcase run with it on. The coven is a raider culture (see
[Raids and threats](raids.md)) whose lair (see [Lairs and the Defense page](lairs.md)) is a hut in the swamp, who
never storm the walls but throw potions over them, and who **hex** the village's villagers every night their hut
stands (see [Piglin incursions](piglins.md) for the culture before it).

Roadmap items: 32.11

## What a player sees

- **Who they come to.** A Village, Town or City whose hall stands in a **swamp** or a **mangrove swamp**.
- **The Coven Hut.** 56 to 80 blocks from the hall, in the swamp (its pools are no bar): a hut on **mangrove stilts**
  over a pond with lily pads, mangrove roots tangled round the posts, a deck reached by stairs from the bank, a dark
  oak frame with mangrove plank walls and **purple-tinted windows**, a dark oak roof with a smoking chimney, **hanging
  lanterns** and lanterns on the deck posts. Inside: **brewing stands**, bookshelves, candles, potted mushrooms, the
  loot chest and a **black cat**. On the bank in front, **the coven's cauldron** over a lit campfire on a mossy hearth,
  with a brewing stand and barrels by it; on the other side a **herb garden** of blue orchids, alliums, ferns and
  mushrooms behind a mangrove fence. Chat says so ("Witch-light glimmers in the swamp beyond Fenwick: Coven Mother
  Morwen Hollowmere has raised her coven's hut 64 blocks north...").
- **The Coven Mother.** A witch with 40 more health, her name over her head (one of twenty: "Coven Mother Agatha
  Blackthorn"). She stays at her hut. **Hit her three times in a row** (no more than 5 seconds apart) and she
  **blinks 8 to 12 blocks away** in a burst of ender particles with the enderman's sound; two hits do nothing.
- **The nightly hex.** Every night the hut stands, the coven **hexes one to three** of the village's grown villagers who
  aren't hexed already: a **swirl of witch particles** over each, a witch's cackle, a chat line ("A swirl of
  witch-light over Fenwick: the coven has hexed 2 of its villagers. No nurse's remedy lifts a hex; only breaking the
  coven does.") and a line in the chronicle. A hexed villager is **ill**: they work at half pace and walk slowly, with
  witch particles over them now and then instead of sneezing; the hall's villager list says "Hexed". **The nurse's
  remedies don't touch it** (she passes them by for the ordinarily ill), and **it doesn't pass by itself**.
- **The raiders**, all called "Coven witch": witches (70%) and zombie villagers in dark leather caps (30%, "the hexed
  of other villages"). **No rams and no ladders.** Behind a finished wall they **stand 12 to 20 blocks outside it**
  and **throw splash potions over it** every 3 seconds at whoever is within 28 blocks: **slowness, weakness or
  poison** at villagers, **harming** at guards. A witch of the raid who is hurt gets a **potion of healing** from a
  sister instead. So it's the archers on the walls' stations who can reach them. A village without walls: they come
  within 10 blocks of a villager and throw from there. They leave at dawn.
- **Breaking the coven.** **Kill the Coven Mother**, or **break her cauldron** (the one on the bank). Either way the
  hut stands empty, her band is gone, chat and the chronicle say so, and **every hexed villager is cured at once**
  (the happy puff of a cure). A hexed villager who was out of loaded ground is cured on the hall's next round.
- **The loot** (`aliveworkplace:chests/coven_hut`): two to four potions (healing, regeneration, fire resistance,
  swiftness, slowness, night vision), glowstone dust, redstone, amethyst shards, glass bottles, spider eyes, nether
  wart, sugar, and now and then (one in five) an **enchanted book**.

## How it works

- The culture is the data file `raider_cultures/witch_coven.json`: `where` (`min_rank` village, `biomes` swamp and
  mangrove swamp), weight 30, arrival `lair`, hours `night`, tactics `hex`, the roster, the captain (with `blinks`),
  the lair `camp/coven_hut` (with `wet`, `near` 56, `far` 80, the Coven Mother on the deck) and the loot.
- **A lair's `wet`** (new, optional): its site may have water on it (no deeper than two blocks), as a swamp has; other
  lairs still want dry ground.
- **A captain's `blinks`** (new, optional): `after` (hits in a row, 3), `near` (8) and `far` (12). Hits more than 100
  ticks apart start the count again (`threat/Covens`).
- **The tactic `hex`** (`threat/Covens`): every 10 ticks each witch of a raid by a culture naming it is steered. Her
  spot is 16 blocks beyond the outermost block of the village's finished wall and gate builds on her side (the line
  from the hall through her; at least 13 from any wall block); she walks there when she is nearer than 12 or farther
  than 20 from the walls, or more than 3 blocks off it, and stands. A goal at the top of her list holds her to it (her
  own ranged attack would walk her to the walls). Every 60 ticks she throws: the speed of a 45-degree throw is found
  by flying the potion as the game does, so it lands at the target over the wall. The raid gathers beyond the walls on
  its side, as a siege does, but it is no siege: no gates shut, no rams.
- **The hex** (`people/Sickness`): an illness of a new kind, the attachment `ill_kind` (`hex`; an ordinary illness has
  none). `Sickness.round` never lets it run its course and never cures it for the Hardy; it lifts it once no coven
  (switched on) stands by a village within the village's radius plus 16 of them. The nurse skips the hexed.
- **The nightly hex**: every 40 ticks, at night (13000 to 23000), each coven whose culture is on and whose village is
  loaded hexes once a day (`Covens.Data`, saved).
- **Breaking**: the lair's captain killed breaks it as every lair. Her cauldron: the coven's cauldron is the cauldron
  of the hut nearest its middle; a player breaking it breaks the coven (by them, in the chronicle), and every 40 ticks
  a loaded coven whose cauldron is gone (an explosion, a piston) is broken too. A lair broken listener cures the hexed.
- **Strength.** 8 when the hut is raised, 1 more a day, at most 14; at most 6 stand at the hut.

## Switches

| Key | Default | What it does |
|---|---|---|
| `raiderCultures` | `witch_coven: true` | false: no hut is raised, one that stands sends no raids and hexes no one, and every hex lifts on the hall's next round. |
| `villagerSickness` | true | Only ordinary illness: a coven still hexes with it off. |

## Saved data

- `aliveworkplace_covens`: `hexed`, per hall the day it was last hexed (`hall`, `day`); default none.
- On each villager: the attachment `ill_kind` (`hex`), beside `ill_since`; absent on every villager from before 32.11,
  whose illness stays an ordinary one.
- The lair is saved as every lair is (`aliveworkplace_bandit_camps`, 32.3).

## Items, blocks, jobs, commands

- Raider culture `aliveworkplace:witch_coven`.
- Structure `aliveworkplace:camp/coven_hut` (17 x 18 x 17, with a black cat), drawn in `tools/blueprints/bandits.py`
  (`coven_hut`).
- Loot table `aliveworkplace:chests/coven_hut`.

## Decisions

- The hut stands 56 to 80 blocks out, as the piglins' outpost: the witches walk, and from farther most would be beyond
  the raid's reach before they arrived.
- "Archers on stations matter most": nothing new for the guards; the witches simply stand where only arrows reach.
- A hex is not lifted by a Gifted villager's Hardiness, nor by the Healers' Guild or Medicine: only by breaking the
  coven or the Remedy ([Plague and the Physician](plague.md), 32.18).
- The zombie villagers wear a dark leather cap so they don't burn by day at the hut.
- Strength 8, +1 a day, at most 14, and weight 30: the roadmap gives no numbers; a little weaker than the other 1.6
  lairs, since the hex works on the village every night without a raid.

## Known limits

- Witches drink their own potions (vanilla) as well as throwing ours.
- A coven whose hut is never loaded can't be broken by its cauldron going missing; killing the Coven Mother or
  breaking the cauldron by hand still breaks it.

## Proof

- `CovenGameTests.theCovenFileIsAsTheItemWritesIt`: swamps, Village and up, the hex tactic with no rams or climbers,
  witches 70 and zombie villagers 30, the Coven Mother (+40, blinks 3/8/12), the hut and its loot, every text, the
  switch.
- `CovenGameTests.aNursesHoneyDoesNotCureAHex`: hexed and slowed; with three honey bottles in her chest the nurse cures
  the ordinary patient (one bottle used) and not the hexed one; an ordinary illness doesn't replace a hex; the hex
  survives a save and load, an ordinary illness reads back as ordinary.
- `CovenGameTests.killingTheCovenMotherCuresEveryHex`: a night's hex lays one to three of four villagers low and is
  remembered; the Coven Mother (66 health) killed, the coven is broken and none is ill.
- `CovenGameTests.breakingHerCauldronCuresEveryHex`: her cauldron over the campfire broken, the coven is broken, the
  three hexed cured, the Coven Mother gone.
- `CovenGameTests.raidingWitchesKeepTheirDistanceFromAWall`: three witches started 3 blocks outside a finished
  palisade stand at least 10 blocks from it after 400 ticks, none over it, and potions were thrown.
- `CovenGameTests.theCovenMotherBlinksAfterThreeHits`: two hits, she stays; the third, she is 8 to 12 blocks off.
- `CovenGameTests.switchedOffACovenHexesNoOne`: a hex long past an illness's time stays while the coven stands; with the
  culture switched off it lifts on the round, and no night is hexed.
- Showcase scene `coven`: the hut at night, villagers hexed, witches off a palisade throwing potions over it.
