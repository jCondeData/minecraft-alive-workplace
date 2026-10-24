# Plague and the Physician

ROADMAP 32.18. Part of Milestone 32 (1.6), switched on for players in 1.0 with every other expansion
(`Expansions.M32`); GameTests and the showcase run with it on. A Town or bigger can be struck by a plague: illness
passes from villager to villager, the ill take to their beds, and the Nurse's remedies don't help. A new job, the
**Physician**, goes house to house with a new item, the **Remedy**.

Roadmap items: 32.18

## What a player sees

- **A plague breaks out.** In a Town or bigger, 3% a day, twice that when the village has more villagers than beds. A
  coven's hex (32.11) or a traveler from far away at the inn (dressed for another land than the innkeeper's) can bring
  it too (1 in 10 where a plague could start that day). It starts with a first case: one villager falls ill with it.
  Players read "A plague has broken out in Oakvale: the ill pass it on to anyone near them."
- **While it lasts**, each hall round a well villager within 3 blocks of an ill one catches it 1 in 5 (a quarter less
  for each level of Medicine research). The ill work at half pace and sneeze, don't get well by themselves, and the
  Nurse's honey, milk and potions don't cure them. After two days ill they go to bed and stay there, even by day.
- **It ends** when no one in the village has been ill for a day: "The plague in Oakvale is over: no one has been ill
  for a day." The hall's wellbeing line says "Plague, until no one is ill".
- **The hall's list** marks the plague's ill: "Ill with the plague: passes it on (only a Remedy cures it)", "... seen
  by the Physician, kept at home" or "... in bed".
- **The Physician** (a villager at a brewing stand given a glistering melon slice) works the long day shift. With a
  Physician in the village, a plague is foretold at its first case, a day before it spreads: "Mara has seen the first
  case: a plague will spread in Oakvale. Days until it spreads: 1." They go house to house to the ill (the plague's
  first) within 32 blocks of their stand, with a Remedy in hand from the chest by the stand, or brewed from its makings
  there. At the bedside the patient is cured, or, with no Remedy to hand, seen and sent home: an ill villager the
  Physician has seen passes it on only to those who share their house. They ask for Remedies on the requests board.
- **The Remedy** (a glass bottle, a glistering melon slice, a honey bottle and a fermented spider eye, shapeless)
  cures any illness, hexes and the plague included, and keeps a villager well for 10 days (no illness, hex or plague).
  The Gray Cough of [the Sickness](the-sickness.md) it only eases for a day, as 31.18 says (B135).
  Right-click a villager with it to give it to them; drink it yourself to lose poison, wither, hunger and nausea (the
  bottle comes back).
- **With Cobblemon**, Fairy and Psychic Pokémon pastured near the stand shorten the Physician's pause between visits.

## How it works

- `data/aliveworkplace/disasters/plague.json` on the disaster engine (32.15): `when: min_rank town`, `chance 0.03`, a
  `crowded` factor `times 2`, `duration` with the new `ends_when_well` (the engine moves the hall's saved end day to today on
  each round anyone in the village is ill, so it ends the day after the last day anyone was), a `contagion` effect
  (chance 0.2, range 3), `answered_by` and `foretold_by` the Physician.
- `threat/Plague`: the plague's own rules, for the disaster with its id only (a datapack's contagion keeps the
  engine's plain rule). `Disasters.start` gives it a first case; `Disasters.contagion` calls
  `Plague.spread` (one roll a round per well villager near the ill; the Physician's seen reach only their house, a bed
  within 6 blocks of theirs). `carried` starts it from a hexed villager (`Covens.hexNight`) or a far traveler
  (`Innkeepers.arrive`), by chance only (never in GameTests).
- `people/Sickness`: a third illness kind, `plague`, beside the ordinary one and the hex. In the hall's round a
  plague-ill villager doesn't recover while the plague is under way (`Plague.underway`), and after two days is walked
  to their bed and put to sleep there (`Plague.toBed`); `PlagueBedMixin` stops the brain waking them while their bed
  stands. Outside a plague (switched off mid-way) a plague illness passes like an ordinary one.
- `work/PhysicianWork`: the job's WORK behavior; `visit` is the bedside, `ready` takes or brews the Remedy.
- `people/RemedyItem`: the item (a `DrinkItem`), the use-on-villager handler and the player's cure; `Plague.cure`
  recovers the villager and sets `well_until`.

## Switches

| Key | Default | What it does |
|---|---|---|
| `physicians` | on from 1.0 | Villagers at a brewing stand can be made Physicians with a glistering melon slice. Off: no Physician job (the brewing stand's tooltip leaves it out), and Physicians already hired stand idle. |
| `disasters` | every disaster id → true | `plague: false`: no plague ever starts, and one under way ends at once. |

## Saved data

On a villager: `ill_kind` = `plague` (with `ill_since`), `plague_seen` (true once a Physician has seen them),
`well_until` (the game time a Remedy keeps them well until). The plague itself is saved on the hall by the engine
(`disaster`, `disasterSince`, `disasterEnds`). The Remedy in a Physician's hand is their own hand item (saved by the
game). All new fields are optional, so old saves load unchanged.

## Items, blocks, jobs, commands

- The item `aliveworkplace:remedy`, in the creative tab beside the Vintner's drinks; its recipe `remedy.json`.
- The job `aliveworkplace:physician`, at a brewing stand (shared with the Cleric, the Nurse and the Undertaker), picked
  with a glistering melon slice. Its outfit: a dark felt hat with a narrow brim and a lavender sprig, round spectacles,
  a long bottle-green coat over a white high-collared shirt, a leather satchel across the chest with a Remedy's neck
  showing, a pouch of herbs at the belt and dark gloves (and a zombie copy).
- Trades: glass bottles bought, a Remedy sold (Novice); honey bottles, a glistering melon slice (Apprentice); spider
  eyes (Journeyman); a potion of healing (Expert); a golden apple (Master).
- Pokémon partner types: Fairy, Psychic.

## Decisions

- 32.18 (lane b): "its first case" is when the plague starts: a villager falls ill at once, and with a Physician the
  plague is foretold from that case a day before it spreads (the engine's `foretold_by`).
- 32.18 (lane b): "sharing their house" is a bed within 6 blocks of theirs; an ill villager with no bed, once seen,
  passes it on to no one.
- 32.18 (lane b): "less with Medicine research" is a quarter less per level (Medicine has two levels: half at the top).
- 32.18 (lane b): a traveler "from far away" is one dressed for another land than the innkeeper's; a carrier brings
  the plague 1 in 10 times, and only where the plague could start that day (a Town or bigger, nothing under way or in
  the engine's 7 days of rest).
- 32.18 (lane b): a Remedy given to a well villager still keeps them well for 10 days.

## Known limits

- The Physician looks for the ill within 32 blocks of their stand; a large village wants more than one.
- A bedridden villager whose bed is broken gets up; they go back to a bed only when the game gives them a new home.
- The Fairy and Psychic partners' speed-up is tested only where Cobblemon is installed.

## Proof

- `PlagueGameTests.illnessPassesOnInAPlague`: 20 hall rounds pass nothing on without a plague; in one (fixed
  `RandomSource`) the two villagers beside the ill catch it and the one 10 blocks off doesn't; the chance is 1 in 5.
- `PlagueGameTests.aSeenVillagerPassesItOnLess`: the seen ill pass it to their housemate and not to a neighbor from
  another house; the unseen pass it to the villager beside them.
- `PlagueGameTests.theRemedyCuresWhereHoneyDoesNot`: a honey bottle (a player's hand, the Physician's chest) cures
  neither the hex nor the plague; the Remedy from a player's hand cures the hex, from the Physician's chest the plague,
  for exactly 10 days of being kept well.
- `PlagueGameTests.thePhysicianBrewsARemedy`, `thePlagueEndsADayAfterTheLastCure`, `aPhysicianForetellsItAtItsFirstCase`,
  `theRecipeCrafts`, `drinkingItCuresAPlayer`, `itSurvivesASave`, `theSwitchTakesTheJobAway`,
  `everyNewSentenceHasItsText`; `StationsGameTests`' row; the config and expansion-gate counts.
- `QaDisastersGameTests.thePlaguesChanceIsTownAndUpAndDoubledWhenCrowded` (QA): no chance in a Hamlet or a Village,
  3% in a Town or a City with a bed for everyone, 6% with one villager more than beds (and still none in a crowded
  Village).
- `QaDisastersGameTests.thePlaguesIllStayIllAndKeepToTheirBed`: five days ill in a plague, still ill; a day and a half
  ill, up; two days and more, asleep in their own bed and held there when woken, until a Remedy cures them by day.
- `QaDisastersGameTests.medicineMakesThePlagueLessCatching`: over 400 rounds beside the ill (fixed seed), about 1 in 5
  catch it without Medicine and clearly fewer with two levels.
- `QaDisastersGameTests.theDroughtAndPlagueReadRight`: the chronicle's "A plague broke out in the village.", the
  wellbeing line "Plague, until no one is ill" and the end message with the village's name.
- Showcase scene: `plague`.
