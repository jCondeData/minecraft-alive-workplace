# Classes

Every household in a village with a hall lives as a class: Peasant, Artisan, Burgher or Noble. Meet the next class's
needs and the household rises; lack your own and it falls. Part of 1.8 (Classes and luxuries): off until that
expansion is finished, and several of its pieces are not built yet (see Known limits).

Roadmap items: 34.1, 34.6, 34.7, 34.8, 34.9

## What a player sees

A household is one grown villager, or a married couple. Each has a class. A household that meets the next class's
needs two dawns running rises: golden sparkles and a chime at their door, a chat line to players within 32 blocks, a
line in the chronicle, and "rose in the world" (+10 mood for 2 days). One that lacks a need of its own class three
dawns running comes down in the world (-10). Children take the class of the grown-ups they live with.

At the Village Hall:
- the **Classes** tab says how many households of each class there are. Its page has a button per class: each need
  and want with how many households have it ("A varied diet: 2 of 4"), what the class gives, and the households
  closest to rising with what they lack;
- the people list says each villager's class and ticks the needs of their class and of the next one;
- the food icon lists the luxuries in store;
- "What next?" gives up to three class tips ("2 Peasant households want A varied diet to rise to Artisan").

| Class | Needs | Wants | Gives |
|---|---|---|---|
| Peasant | nothing | nothing | pays the usual tax |
| Artisan | a grade I home, fed 3 days running, a varied diet, a chapel, school or clinic near home, Work Clothes | Cider, a tavern near home | tax x1.5; crafters 10% faster; opens the Artisan jobs |
| Burgher | a grade II home, fed 3 days, a varied diet, a school and one more service, a Market Square, Fine Clothes, Berry Wine, the Gazette | an Amethyst Ring, a library, beauty | tax x2.5; scholars research 15% faster; with 3 such households one more caravan route and market trader; opens the Burgher jobs |
| Noble | a grade III home, fed 3 days, a varied diet, chapel, school, clinic and library, Town rank, beauty, Noble Robes, Vintage Wine, an Emerald Brooch | an Illuminated Book, a Gold Circlet | tax x4; +3% wellbeing per household up to 9%; every other festival is a Noble's Ball |

**Higher jobs need higher classes (34.8).** The Tinkerer, Chef, Netherworker, Nurse, Teacher, Shopkeeper, Innkeeper
and, with Cobblemon, the Ball Smith, Move Tutor, Pokémon Trader and Fossil Scientist need an Artisan; the Scholar,
Undertaker and Trainer Leader a Burgher. Vanilla jobs are open to everyone. Nobody is ever fired: a worker below
their job's class keeps it, and the hall's list marks them.

**The Vintner (34.9).** Stand a villager by a cauldron and sneak-right-click them with sweet berries, glow berries
or an apple. A Novice presses Cider, an Apprentice Berry Wine, and Vintage Wine waits three days to age. The drinks
are luxuries the households take from the store, and players can drink them (the bottle comes back).

## How it works

**Classes are data.** One file each in `data/aliveworkplace/classes/`: its tier, tax, needs, wants, the jobs it
opens and its effects. A data pack replaces one, adds its own or switches ours off. Each file lists all of its needs;
nothing is inherited from the class below.

**The dawn check.** Each dawn the hall checks its households 8 at a time. When the next class's needs held
`classRiseDays` dawns running (2) the household rises one class; when a need of its own class failed
`classFallDays` dawns running (3) it falls one, never below Peasant. One step a day at most. For a couple a need
holds only when it holds for both.

**Needs.** A home's grade comes from the building their bed is in. "Fed" counts dawns fed running. Diet goes by
their last meals. Services (chapel, school, clinic, library, market, tavern) are data too
(`data/aliveworkplace/services/`): a service is given by a worker of the right job or a finished building, and
reaches homes within 48 blocks (the market reaches the whole village). The hall works the list out once a day.
Luxuries (`data/aliveworkplace/luxuries/`) are taken from the village store at dawn, one per household, when due.

**Mood.** Every villager has "has what their class needs" (+5), or -5 for each need lacking (at most -15).

**What a class gives (34.7).** Taxes are the treasury's daily share times the class's factor, 10% more for each
want the household has; a Peasant pays exactly what every worker paid before, so no village takes in less. Speed
bonuses go through the one pace rule and its cap. A Legend lives among the top class.

**The job gate** applies only when a job is taken: picking it with its item, the hall's free-workstation list, a
grown child taking a parent's trade, the Steward's morning jobs. A hired traveller arrives with the class of their
level (Apprentice: Peasant, Journeyman: Artisan, Expert: Burgher).

## Switches

| Key | Default | What it does |
|---|---|---|
| `villageClasses` | on from 1.8 | Off: no classes, no job gate, no class taxes; what is saved stays |
| `classRiseDays` | 2 (1 to 30) | Dawns running the next class's needs must hold to rise |
| `classFallDays` | 3 (1 to 30) | Dawns running a need of their own class must fail to fall |
| `vintners` | on from 1.8 | Off: no Vintner job, and Vintners already hired stand idle |

## Saved data

On each villager (attachments): `social_class` (their class id; absent until the village is seeded),
`class_progress` (dawns the next class's needs were met, dawns a need was missed, days fed running, and the day last
counted; all 0 at first), `class_standing` (where they stood at their last dawn: needs lacking, their last rise or
fall, wants had; an older save reads the wants as none), `luxuries_had` (the day each luxury was last had; empty).
On the hall: `services` and the day they were worked out, `ballTurn` (false) and `ballDay` (-100) for the Noble's
Ball. Households and the village's sums by class are never saved: they are worked out from the villagers.

## Items, blocks, jobs, commands

- Class ids: `aliveworkplace:peasant`, `aliveworkplace:artisan`, `aliveworkplace:burgher`, `aliveworkplace:noble`.
- Job: Vintner (`aliveworkplace:vintner`), at a cauldron.
- Items: Cider (`aliveworkplace:cider`), Berry Wine (`aliveworkplace:berry_wine`), Vintage Wine
  (`aliveworkplace:vintage_wine`).
- Texts: `class.aliveworkplace.peasant`, `class.aliveworkplace.artisan`, `class.aliveworkplace.burgher`,
  `class.aliveworkplace.noble`.
- No commands.

## Decisions

- 34.7: a Peasant pays exactly the old tax, so switching classes on never makes a village poorer.
- 34.8: vanilla jobs are never gated (owner's call, design note `docs/design/M34.md`), and nobody is ever fired.
- Design note: each class file lists all of its needs. The Noble's file repeats "fed 3 days, a varied diet", which
  the roadmap's Noble line left out; without it a Noble could live on bread alone.
- Design note: the Burgher's "a school and one more service" is two needs, and the Market Square is a building
  need, so the market doesn't double as the "one more".
- Design note: a luxury is taken at dawn before the needs are checked, so one taken that dawn counts that day.

## Known limits

- **Nobody can rise yet with the mod's own data.** The Artisan class needs Work Clothes, and the Tailor who makes
  them (34.10) isn't built; the same goes for Fine Clothes, the Gazette, the rings and the Noble's goods (34.10 to
  34.12). Only the three wines have luxury files today, and a luxury without a file is a need that never holds.
- Villagers have no class until their village is seeded, and seeding (34.22) isn't built: on a real world every
  villager is still without a class, and the job gate doesn't apply to them.
- The grander homes (34.15, 34.16), class outfits (34.17, 34.18), elders and family trees are not built.
- In GameTests classes are switched off unless a class test turns them on, so other tests' numbers don't move.

## Proof

GameTests: `ClassGameTests` (16: the four files are the ladder, each kind of need, one step a day), `ClassHallGameTests`
(6), `ClassPerkGameTests` (7), `ClassJobGameTests` (7), `ServiceGameTests` (8), `LuxuryGameTests` (7),
`LuxuryWorkGameTests` (7), `VintnerGameTests` (10).

Showcase scenes: `classes` (a household rises, and the Classes page), `class_jobs`, `noble_ball`, `vintner`.
