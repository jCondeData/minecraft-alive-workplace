# Edicts

Laws a village's owner proclaims at the Village Hall. Each gives the village a boost at a cost, and each has a reform
that takes the cost away for good. Part of 1.4 (Edicts and civic items): off until that expansion is finished.

Roadmap items: 30.1, 30.1a, 30.3, 30.4, 30.5, 30.6, 30.7, 30.8, 30.9, 30.10, 30.21, 30.22

## What a player sees

Open the hall's Book of Edicts (the lectern button on the hall's screen, or sneak-use a Village Ledger). At the top
are the village's edict slots, one per rank: in force with its days, free, or locked with the rank that opens it.
Below is every edict with its boost and its cost. Click an edict twice to proclaim it; click one in force to lift it.
Everyone in the village is told, and the chronicle notes it. Only the hall's owner, their friends and operators may
click; everyone else can read the book and is told why a click was refused.

While an edict is in force its reform appears on the hall's Quests page, one step a morning: bring a few hundred
items, or slay monsters, or win a Pokémon battle. Finish the last step and fireworks go up over the hall: the boost
stays and the cost is gone, for that village, for good. Villagers talk about the edicts in force, and "What next?"
says when a slot is free or a reform step waits.

| Edict | Boost | Cost | Reform (three steps) |
|---|---|---|---|
| Long Shifts | Everyone works 20% faster | Every grown villager is 10 less happy | The Shift Bell: 24 clocks, 128 gold ingots, 300 bread |
| Free Bread | Everyone fed in the last day is 10 happier | The village eats 30% more | The Common Granary: 512 wheat, 128 hay bales, 48 barrels |
| Large Families | Up to two babies a day | A baby needs 24 meals in store and the family eats 12; illness 50% more often | The Midwives: 48 honey bottles, 192 white wool, 64 golden carrots |
| Open Gates | Inns take 4 guests, two travellers a morning, one more market trader, Legends visit twice as often | Bandits camp nearby twice as often | The Watchful Gate: 192 iron ingots, 256 arrows, 40 monsters |
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

**Reforms.** The next step goes up the morning after the last one was done, so a reform takes three days at least.
Progress is kept per edict, also while the edict is lifted, and a step handed in halfway stays half done. A battle
step falls back to slaying monsters when Cobblemon or a trainer is missing.

**Curfew** overrules the villagers' schedule from dusk (tick 12000) to dawn for everyone but guards and
mercenaries, and a monster can't hurt a villager asleep in bed. **Conscription** gives each conscript a stone sword
made for the raid (never taken from a chest), which is gone when the raid ends and again whenever the villager
loads, so a save in the middle of a raid can't leave a free sword.

## Switches

| Key | Default | What it does |
|---|---|---|
| `villageEdicts` | on from 1.4 | Off: none can be proclaimed, and those in force do nothing but stay saved |
| `edictMinDays` | 3 (0 to 30) | Days an edict stays in force before it can be lifted |
| `maxWorkPace` | 200 (100 to 400) | The cap every speed bonus shares, Long Shifts included |

## Saved data

On the hall: `edicts` (each edict in force and the day it was proclaimed; empty by default), `reforms` (each
edict's progress and whether it is reformed; empty), `raidWorkUntil` (Conscription: when work may start again),
`extraMeals` (Free Bread's part-meals carried over). The summed effects are not saved; they are added up again when
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
- A reform's story-arc id loads and is kept, but nothing reads it yet.
- The Tithe's price rise is rounded to whole emeralds, so trades under 5 emeralds don't change.
- With `villageEdicts` off the book can't be used; edicts already in force come back when it is switched on again.

## Proof

GameTests: `EdictGameTests` (10: loading, slots by rank, the three-day rule, rank drops, save and reload),
`EdictBookGameTests` (6), `ReformGameTests` (7), `FamilyEdictGameTests` (5), `OpenGatesGameTests` (6),
`TreasuryEdictGameTests` (8), `CurfewGameTests` (6), `ConscriptionGameTests` (6), `CivicTalkGameTests` (7),
`PaceGameTests.bonusesStopAtTheCapAndPenaltiesComeAfter`.

A season under the edicts (30.22): `SEASON=true tools/packtest/run.sh` lays out a City of 35 with six farms, a kitchen
and a store on the real pack server and runs it 4 days under Long Shifts, Free Bread, Large Families and Festival
Season, then 4 days with all four reformed, with a Cradle, a Harvest Idol in harvest season, a founded guild and a
rush a day. Measured on 2026-10-10: 91.8 meals a day from the store under the edicts against 79.8 reformed (with more
villagers), 12 emeralds for the festival against 0, nobody past the pace cap, the village growing from 35 to 51. Our
share of the tick was under 15% on every day but the first (17.1%), and one tick on day 1 spent about 400 ms in our
code (B100), so the run's tick check fails for now. Every day's numbers are in `docs/design/M30.md`, "Numbers from the
season run". `SeasonSoakGameTests` (5) test how the result is judged.

Showcase scenes: `edicts` (the book), `long_shifts`, `reform`, `free_bread`, `large_families`, `open_gates`,
`festival_season`, `tithe`, `curfew`, `conscription`, `village_talk`.
