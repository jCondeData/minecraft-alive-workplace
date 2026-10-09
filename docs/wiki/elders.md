# Elders

Life stages for villagers: after a long grown life a villager becomes an elder, slows down, hands their job to a
successor and, in time, passes in their sleep. Part of 1.8 (Classes and luxuries).

**Status on 2026-10-09: designed, not in the game yet.** Roadmap items 34.19 (elders) and 34.20 (retirement and
apprentices) are open, so nothing below under "planned" can be seen by a player today. This page records what is
decided and what already exists that elders will stand on. The lane that builds each item replaces the planned part
with what it built, in the same commit.

Roadmap items: none

<!-- Lanes: add each id to the line above in the commit that builds it (34.19, 34.19a, 34.20), and put the new
     config keys, saved fields, tests and scenes in backticks so wikicheck.py checks them. -->

## What a player sees

**Today:** villagers in a village with a hall are born, remember their parents, grow up (the chronicle notes it),
take up a parent's trade if a workstation is free, court, marry and mourn. They never grow old. A grown villager
with a job or a name who dies leaves a grave, and an Undertaker can bring them back.

**Planned (34.19):** after 120 grown days a villager is an elder. The hall says so ("Elder · grown 131 days"), they
wear an elder look, walk 15% slower, and their mood gets "a quiet old age" (+5) when they are fed and housed. The
chronicle notes it ("Bram is an elder now") and elders have four chatter lines of their own ("In my day this was all
fields."). After 40 elder days an elder passes in their sleep and leaves a grave.

**Planned (34.20):** an elder with a job retires once someone can take over, at most one villager a village every 3
days. The successor (their own grown child without a job, else any jobless grown villager whose class the job
allows) shadows the elder for 2 days, then takes the workstation and the job one level lower. A retired elder never
takes a job again; by day they mentor a Novice or Apprentice of their old trade, who then learns 50% faster.

## How it works

Nothing of elders is in the code yet. What exists today and will carry it:

- **Families** (`people/Families`): a baby's parents (names and trades) are saved on the baby, and the hall's daily
  round notices a child that has just grown up. The plan sets the day a villager grew up at that same moment.
- **Walking**: every worker's walk goes through one place (`work/Walker`), which already applies the Nimble trait's
  speed; the elder's slower walk is planned there.
- **Moods** (`people/Moods`): each mood reason is a named line on the hall's list; "a quiet old age" will be one
  more.
- **Graves** (`grave/Graves`): a grown villager with a job or a name who dies leaves a grave holding everything
  they were. An elder's passing is planned to leave the same grave.
- **The class job gate** (see [Classes](classes.md)): a successor must be of a class the job allows.

## Switches

None exist yet. Planned, by the names the roadmap gives them (not in the config file today, so not written as
checked names here): a switch for ages as a whole, the number of grown days before a villager is an elder (120), a
switch for elders passing (on) and one for retirement.

## Saved data

None yet for elders. Planned: the day a villager grew up, saved on the villager, with a default for every villager
alive before the update (set when the village is seeded, 34.22), so no existing save breaks.

Saved today and used by the plan: the attachment `parents` on a child (the parents' names and trades, and
`grown_up`, false until the hall has noted it).

## Items, blocks, jobs, commands

None yet. Graves and the Undertaker exist today (see the README's "Graves and Undertakers").

## Decisions

- 34.19 (owner, 2026-10-06): elders do pass in their sleep after 40 elder days and leave a grave. The design note
  `docs/design/M34.md` had this off by default; the owner turned it on.
- 34.19a (owner, 2026-10-06): a way to keep a good elder for ever, "so you don't lose good villagers". Its section
  is written by the lane building it.
- 34.20: an elder never retires while holding something for a player or a build (a builder's site or queue, a
  miner's quarry, Pokémon in their daycare, fossils being revived, parcels on their round).
- Design note: one chronicle kind for elders, retirements and family generations, rather than a kind each.

## Known limits

- Not built: a player sees none of the planned behaviour, and none of it is tested.
- When it is built, elders will only age in villages whose villagers have a "grew up" day, which waits for the
  seeding item (34.22).

## Proof

For what exists today: `GraveGameTests` (an Undertaker brings a worker back; only workers leave graves),
`GiftedBornGameTests` (a child of two Masters growing up).

For elders: no tests and no scenes yet. The roadmap asks for life-stage tests (an elder after the configured days,
the slower walk, the mood reason, a reload keeping the day) and a scene before 34.19 is ticked.
