# Edicts

Laws a village's owner proclaims at the Village Hall. Each gives the village a boost at a cost, and each has a reform
that takes the cost away for good. Part of 1.4 (Edicts and civic items): switched on in 1.0 with every other expansion.

Roadmap items: 30.1, 30.1a, 30.3, 30.4, 30.5, 30.6, 30.7, 30.8, 30.9, 30.10, 30.21, 30.22, 31.22, B327

## What a player sees

Open the hall's Book of Edicts (the lectern button on the hall's screen, or sneak-use a Village Ledger). At the top
are the village's edict slots, one per rank: in force with its days, free, or locked with the rank that opens it.
Below is every edict with its boost and its cost. Click an edict twice to proclaim it; click one in force to lift it.
In a realm, the row under the village's slots holds the realm's slots, and the capital's book lists the six realm
edicts too (see [Founding a realm](realms.md), "Realm edicts").
Everyone in the village is told, and the chronicle notes it. Only the hall's owner, their friends, operators and the
village's Lords (see [Reputation](reputation.md)) may click; everyone else can read the book and is told why a click
was refused. An edict that can't be lifted yet says when it can ("Can be lifted tomorrow", "in 2 days").

While an edict is in force its reform appears on the hall's Quests page, one step a morning: bring a few hundred
items, or slay monsters, or win a Pokémon battle. Finish the last step and fireworks go up over the hall: the boost
stays and the cost is gone, for that village, for good. Villagers talk about the edicts in force, and "What next?"
says when a slot is free or a reform step waits.

| Edict | Boost | Cost | Reform (three steps) |
|---|---|---|---|
| Long Shifts | Everyone works 20% faster | Every grown villager is 10 less happy | The Shift Bell: 24 clocks, 128 gold ingots, 300 bread |
| Free Bread | Everyone fed in the last day is 10 happier | The village eats 30% more | The Common Granary: 512 wheat, 128 hay bales, 48 barrels |
| Large Families | Up to two babies a day | A baby needs 24 meals in store and the family eats 12; illness 50% more often | The Midwives: 48 honey bottles, 192 white wool, 64 golden carrots |
| Open Gates | Inns take 4 guests, two travelers a morning, one more market trader, Legends visit twice as often | Bandits camp nearby twice as often | The Watchful Gate: 192 iron ingots, 256 arrows, 40 monsters |
| Festival Season | A festival every 4 days instead of 8 | Each costs the treasury 3 emeralds and 1 more per 4 villagers; no money, no festival | The Festival Fund: 24 cakes, 256 firework rockets, 48 note blocks |
| Tithe | A tenth of what players pay in trades goes to the treasury | Emerald prices are 10% higher | The Fair Ledger: 24 books and quills, 192 gold ingots, a Pokémon battle (or 32 monsters) |
| Curfew | Safe nights in bed, raids half as likely | No night trading; festivals and markets end at dusk; nobody sets out after midday | The Lamplighters: 192 lanterns, 96 glowstone, 32 monsters |
| Conscription | In a raid every healthy grown villager fights with a stone sword | All work stops during the raid and until noon the next day | The Militia Drill: 32 iron swords, 32 shields, 48 monsters |

## How it works

**Edicts are data.** One file each in `data/aliveworkplace/edicts/`: its icon, texts, order in the book, `boost`
and `cost` (lists of effects), what it `excludes`, and its `reform`. A data pack adds its own or switches one of ours
off with `"enabled": false`. Curfew and Open Gates exclude each other.

**Slots go by rank.** Hamlet 1, Village 2, Town 3, City 4. An edict stays in force at least `edictMinDays` days (3)
before it can be lifted. A village that drops a rank loses its newest edict.

**One toolbox of effects.** Each effect has a type (`aliveworkplace:work_pace`, `aliveworkplace:mood`, and so on)
and is read by exactly the system it changes: work speed by the pace rule, food by the hall's needs, births by
village growth, raids by the raid code. The effects in force are added up per hall whenever its edicts change, so
nothing is worked out every tick. Guilds, tonics and classes use the same toolbox.

**Reforms.** Each step sits on the Quests page in the row below the daily quests (a book-and-quill icon), never
expires and pays its emeralds as a quest does. The next step goes up the morning after the last one was done, so a
reform takes three days at least.
Progress is kept per edict, also while the edict is lifted, and a step handed in halfway stays half done. A battle
step falls back to slaying monsters when Cobblemon or a trainer is missing.

**Reforms as stories (31.22).** With `storyArcs` on, each reform is told as a short side story of the same name
(`data/aliveworkplace/arcs/<name>.json`, named by the reform's `arc`). When the edict is in force a villager of the
fitting trade asks you for it: talk to them and the reform's three steps follow as the story's quests, the same items,
counts and pay, one a day; the villagers talk about it and the chronicle tells each chapter. The ending pays the new
`reform_edict` reward (the edict reformed for that village, as the last step would) and leaves something lasting:

| Story | Asked by | Ending |
|---|---|---|
| The Shift Bell | the Toolsmith (else any smith) | a bell on two posts beside the hall, on natural ground, ringing at the start (tick 2000) and end (tick 9000) of the work day |
| The Common Granary | a Farmer | a harvest supper at the hall the next evening: a festival's feast, no fireworks |
| The Midwives | the Nurse (else anyone) | a midwife comes to stay, a Nurse at Journeyman |
| The Watchful Gate | a Guard | an old soldier comes to stay, a Guard at Expert in iron armor with an iron sword |
| The Festival Fund | the Bard (else anyone) | the next festival is grand: twice the fireworks, its good mood a day longer |
| The Fair Ledger | a Librarian or Scholar | "The Fair Ledger of <village>", a written book of the treasury's takings day by day for the last 7 days, for the hall's owner |
| The Lamplighters | a Porter (else anyone) | two Street Lamp blueprints for whoever finished it, and a chronicle line on the first lamplit night |
| The Militia Drill | a Guard | drill day: at noon the next day every grown villager spars at the village's Training Dummies (the guards alone with none), then a chronicle line |

Reform stories are `side` arcs: they run beside another story and don't count against `arcsAtOnce` or the cooldown.
While one runs, its M30 step stays off the quest page. With `storyArcs` off, or when nobody of the trade lives there to
ask, the steps go up on the quest page as before, and progress made either way carries over.

**Curfew** overrules the villagers' schedule from dusk (tick 12000) to dawn for everyone but guards and
mercenaries, and a monster can't hurt a villager asleep in bed. **Conscription** gives each conscript a stone sword
made for the raid (never taken from a chest), which is gone when the raid ends and again whenever the villager
loads, so a save in the middle of a raid can't leave a free sword. The sword stays in the conscript's hand all fight:
vanilla's trade display (wares shown to a player standing by) leaves a hand holding it alone, and a hand emptied any
other way takes the sword up again at once (B324, B327).

## Switches

| Key | Default | What it does |
|---|---|---|
| `villageEdicts` | on from 1.0 | Off: none can be proclaimed, and those in force do nothing but stay saved |
| `edictMinDays` | 3 (0 to 30) | Days an edict stays in force before it can be lifted |
| `maxWorkPace` | 200 (100 to 400) | The cap every speed bonus shares, Long Shifts included |

## Saved data

On the hall: `edicts` (each edict in force and the day it was proclaimed; empty by default), `reforms` (each
edict's progress and whether it is reformed; empty), `raidWorkUntil` (Conscription: when work may start again),
`extraMeals` (Free Bread's part-meals carried over). The reform stories (31.22) keep `aliveworkplace_reform_stories`
in the world's data: each village's Shift Bell, the days of a harvest supper, grand festival and drill, whether the
first lamplit night is still to come, and the last 7 days of treasury takings for the Fair Ledger (all empty by
default). The summed effects are not saved; they are added up again when
the hall loads.

## Items, blocks, jobs, commands

- Item: Village Ledger (`aliveworkplace:village_ledger`), sneak-use to open the book.
- Edict ids: `aliveworkplace:long_shifts`, `aliveworkplace:free_bread`, `aliveworkplace:large_families`,
  `aliveworkplace:open_gates`, `aliveworkplace:festival_season`, `aliveworkplace:tithe`, `aliveworkplace:curfew`,
  `aliveworkplace:conscription`.
- Texts: `edict.aliveworkplace.long_shifts` and `edict.aliveworkplace.long_shifts.desc` (and the same for each
  edict), `reform.aliveworkplace.shift_bell`.
- Command (operators): `/workplace edict proclaim|lift <id>` in the village you stand in.
- Command (benchmark servers only, `-Daliveworkplace.benchmark=true`): `/workplace season [days] [both|edicts|reformed]`,
  the season run of 30.22 (see Proof).

## Decisions

- 30.1a (owner, 2026-10-05): a reform must cost far more, hundreds of items, so the grind is worth it. The first
  draft asked for 4 clocks, 8 gold ingots and 32 bread; it became 24 clocks, 128 gold ingots and 300 bread, and the
  others were scaled the same way.
- 30.2 (owner's call in the design note `docs/design/M30.md`): everything that makes villagers faster stops at
  twice the usual pace (`maxWorkPace` 200). Sickness and bad moods still slow them after that, and a villager's
  level doesn't count towards the cap. So Long Shifts, partners, a guild and a tonic together can't pass it.
- 30.3: each edict is a real trade-off (a boost, a cost, and a reform that removes the cost).
- 30.3: an edict in force whose data file is gone stays saved, does nothing, still takes its slot and can be lifted.
- 30.4: the Book of Edicts is a screen of its own, not a page tab, opened from the hall and from the ledger.
- 30.8: Festival Season halves the 8 days between a village's regular festivals; the season festivals and the
  Festival Cup keep their own days.
- 30.10: conscripts can fall like anyone else; the edict's text says so.

## Known limits

- Reforms are per village: a second village reforms the same edict again.
- 31.22: "else a mother" (the Midwives) and "else a Cheerful villager" (the Festival Fund) fall back to any grown
  villager; the arc system casts roles by trade only.
- 31.22: the Shift Bell needs a level 3 by 3 of natural ground 3 to 12 blocks from the hall; with none, the story
  still ends and the edict is reformed, without the bell.
- The Tithe's price rise is rounded to whole emeralds, half an emerald up: trades under 5 emeralds don't change,
  5 emeralds become 6 and 15 become 17. The tithe is a tenth of what was paid, so the 6 put 0.60 in the treasury.
- Free Bread's "fed in the last day" means not hungry yet: a Glutton counts for half a day after a meal, a Frugal
  villager for two days. A villager who has never eaten from the store gets no free bread.
- An edict reaches only the villagers, inns and traders within the hall's radius: an inn or librarian just outside
  keeps the usual guests and prices. A lifted edict stops at once; Free Bread's part-meal stays saved for next time.
- With `villageEdicts` off the book can't be used; edicts already in force come back when it is switched on again.

## Proof

GameTests: `EdictGameTests` (10: loading, slots by rank, the three-day rule, rank drops, save and reload),
`EdictBookGameTests` (6), `ReformGameTests` (7), `FamilyEdictGameTests` (5), `OpenGatesGameTests` (6),
`TreasuryEdictGameTests` (8), `QaEdictEffectsGameTests` (10: the QA lane's boundaries for 30.6 to 30.8),
`QaEdictsGameTests` (14: the QA lane's boundaries for 30.3, 30.5, 30.9 to 30.11, 30.15 and 30.17),
`QaPaceAndBookGameTests` (11: the QA lane's for 30.1a, 30.2, 30.4 and 30.21: the 300th bread, friends' and operators'
clicks, the last day before lifting, the cap at 100 and 400), `CurfewGameTests` (6), `ConscriptionGameTests` (9), `CivicTalkGameTests` (7),
`PaceGameTests.bonusesStopAtTheCapAndPenaltiesComeAfter`, `ReformStoryGameTests` (10: each of the eight stories from
the ask through M30's three steps to its ending and the edict reformed in that village only; `storyArcs` off; side
arcs and `arcsAtOnce`), `QaReformStoryGameTests` (11: the QA lane's for 31.22: the fallback askers (any smith, a
Scholar, anyone), no Guard leaving M30's step on the quest page, `storyArcs` off through the real quest page, a side arc
beside a main arc in its cooldown, the blueprints only to the finisher, a reload with the asker dead, the bell only on
natural ground, the ledger's seven days, every sentence). Showcase scene `reform_story`.

A season under the edicts (30.22): `SEASON=true tools/packtest/run.sh` lays out a City of 35 with six farms, a kitchen
and a store on the real pack server and runs it 4 days under Long Shifts, Free Bread, Large Families and Festival
Season, then 4 days with all four reformed, with a Cradle, a Harvest Idol in harvest season, a founded guild and a
rush a day. Measured on 2026-10-10: 91.8 meals a day from the store under the edicts against 79.8 reformed (with more
villagers), 12 emeralds for the festival against 0, nobody past the pace cap, the village growing from 35 to 51. Our
share of the tick was under 15% on every day but the first (17.1%), and one tick on day 1 spent about 400 ms in our
code (B101), so the run's tick check fails for now. Every day's numbers are in `docs/design/M30.md`, "Numbers from the
season run". `SeasonSoakGameTests` (5) test how the result is judged.

Showcase scenes: `edicts` (the book), `long_shifts`, `reform`, `free_bread`, `large_families`, `open_gates`,
`festival_season`, `tithe`, `curfew`, `conscription`, `village_talk`.
