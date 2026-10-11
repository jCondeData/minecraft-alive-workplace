# Elders, retirement, family trees and the Evergreen Charm

Life stages for villagers: after a long grown life a villager becomes an elder and, in time, passes in their sleep,
unless a player gives a good elder an Evergreen Charm; and every family keeps a tree. Part of 1.8 (Classes and luxuries): off for players until 1.8
is finished. Built so far: ages, the slower walk, the "quiet old age" mood, the elders' chatter and the chronicle's note
(34.19), the passing and the charm (34.19a), retirement, apprentices and mentoring (34.20), family names, the Book of
Families, the Family page and the Printer's family tree (34.21), and the seeding of everyone's age when classes switch
on (34.22, [Classes](classes.md)).

Roadmap items: 34.19, 34.19a, 34.20, 34.21, B95, B99, B102

<!-- Lanes: add each id to the line above in the commit that builds it (34.19, 34.20), and put the new config keys,
     saved fields, tests and scenes in backticks so wikicheck.py checks them. -->

## What a player sees

A villager who grew up in a village with a Village Hall counts their days. After 120 grown days they are an **elder**:
their card on the hall's list says "Elder · grown 131 days", and the chronicle notes the day ("Bram is an elder now").
An elder:

- **walks 15% slower** wherever their work takes them (a builder to the site, a miner to the quarry, a courier on the
  round): beside a younger villager on the same path they fall behind;
- has **"a quiet old age"** (+5) in their mood while they are fed and have a bed of their own; it is the first reason on
  their card ("Happy (80): a quiet old age, fed, a bed of their own, ..."). A hungry elder, or one with no bed, has none;
- **talks like one**. Off work and near a player, an elder now and then says one of four lines of their own: "In my day
  this was all fields.", "I've seen more harvests than you've had hot dinners.", "These knees knew every path in the
  village, once.", "Sit a while. The young ones are always in a hurry."

They do not look any different yet: the elder look comes with the class outfits (34.18).

**Retirement.** An elder with a job retires once someone can take over, at most one villager a village every 3 days.
The successor is their own grown child without a job, else any jobless grown villager whose class the job allows
([Classes](classes.md)). The chronicle says "Tom is learning the Farmer trade from Bram, who would like to retire", and
for 2 days Tom follows Bram about with "learning from Bram" over his head. Then Tom takes Bram's workstation and the job
one level below Bram's (at least Apprentice: an Expert's successor is a Journeyman, a Novice's an Apprentice), with the
job's assignments: field, tree farm, orchard, courier routes, patrol route, ball orders and who hired them. The
chronicle says "Bram retired from the Farmer trade after 132 days grown" and "Tom took over the Farmer trade from Bram,
as Journeyman". With nobody to take over, the elder keeps working and their card on the hall's list says "Bram would
like to retire: the village needs a jobless villager to take over". An elder never retires while holding something for
a player or a build: a site or a queue (builder), a quarry (miner), Pokémon in their daycare, fossils being revived,
parcels on their round.

A **retired** elder never takes a job again, but players can still trade with them (they keep their trades). Their
card says "Retired Farmer · mentors the young". By day they stand by a Novice or Apprentice of their old trade in the
village, who learns 50% faster ("taught by Bram" over their head and on their card), or else sit by the hall.

Forty elder days later their time comes: they pass away in
their sleep (or, with no bed, once night has fallen) and leave a grave. The village is told in chat, the chronicle
notes it ("Bram passed away in their sleep, an elder"), and an Undertaker can bring them back for another 40 days.
From ten days before, the card warns: "Their time comes in 5 days (an Evergreen Charm keeps them)", then "Their time
comes tonight".

The **Evergreen Charm** keeps a good villager for good. It is a gold-and-green leaf pendant, crafted from a totem of
undying, a golden apple, 2 emeralds and a heart of the sea (any arrangement). Sneak-right-click an elder with it:

- an elder with a **good trait** takes it and becomes **Ageless**: totem sparkles, a cheer, "Bram (a Master of their
  trade) takes the Evergreen Charm and will never leave us." The charm is used up (not in creative), the chronicle says
  "Bram will never leave us: Jesse gave them an Evergreen Charm (a Master of their trade)", and their card wears a gold
  leaf badge, "❦ Ageless: will never leave us". They stay an elder in every other way.
- anyone else refuses, with the reason over the hotbar, and the player keeps the charm:
  - "Fenn is not an elder yet (grown 30 of 120 days): the Evergreen Charm is for elders." (or "is not an elder" for a
    child or someone whose age nobody knows);
  - "Dara won't take it. The charm is for an elder who is a Master of their trade, Gifted, a Legend, or happy (mood 80
    or more) for 20 days running. Happy days so far: 19.";
  - "Bram is already ageless.";
  - "Evergreen Charms are switched off on this server." / "Elders don't pass away on this server: nobody needs an
    Evergreen Charm."

**Family trees.** Everyone in a village with a Village Hall has a family name. A villager whose parents nobody knows
founds a family, and their children take it: the hall's list says "Dara (Ashby)", while the name over Dara's head (or
one a Name Tag gave her) never changes. When a baby is born and the village knows the grandparents, the chronicle says
so: "Mira was born to Dara and Tomas, grandchild of Bram Ashby and Ilse Corwen"; the first baby of a family's third
generation gets a line of its own, "The Ashby family has its third generation: Mira". On a villager's page in the hall
(shift-click a named villager on the list) a **Family** button (a flowering azalea) opens their Family page:
grandparents, parents, partner, brothers and sisters, children and grandchildren, each with their trade and days. The
living are name tags, babies eggs, the dead skulls with a cross ("Bram (Ashby) †", "Died on day 40"); a click on anyone
opens their own family. With a Printer in the village, the page's book button prints the family's tree for 2 emeralds:
a written book, "The Ashby Family", with a cover (the village, the day, how many people in how many generations) and
everyone of the family, generation by generation, two to a page.

## How it works

- **Age.** `adult_since` is the `Chronicle.day` a villager grew up. It is written when a child grows up in a hall's
  round (`Families.round`). Whether someone is an elder is never saved: it is `today - adult_since >= villagerElderDays`
  (`people/LifeStages`). A villager with no `adult_since` is never an elder and never passes;
  in a world from before 1.8 the hall's first rounds give everyone one, spread over the last 100 days by their UUID
  (34.22), so the first elders come about three weeks later, one at a time.
- **The chronicle's note.** The hall's round writes "Bram is an elder now" (kind `LIFE`) the first time it sees them
  as an elder, and marks them `elder_noted` so it is written once: on the day itself, or, if the village wasn't loaded
  that day, the next time it is. An elder an Evergreen Charm already wrote into the chronicle gets no second line.
- **The walk.** `work/Walker` multiplies every walk it asks for by `LifeStages.walk`: 0.922 for an elder. A mob's pace
  goes with the square of that number, so 0.922 is 15% less ground in the same time (0.85 would be 28% less). It slows
  the walks our jobs ask for; a stroll the game itself decides on (wandering, going to bed) keeps vanilla's pace.
  The game can still take a job's walk over: Walker also sets where the villager looks, and when its walk target is
  dropped for a moment (a path that failed), vanilla walks to that same block at its own pace (0.5). So when Walker finds
  its block already set at another pace, it sets it again at its own and changes the pace of the walk under way
  (B99); before, the elder then walked the rest of the way at full speed, and a nimble villager lost the faster walk.
- **The mood.** `Moods` adds 5 for an elder who is fed (ate within their day) and has a bed. Because it lifts the mood,
  it also helps an elder towards the 20 happy days below.
- **The talk.** `Chatter` has the topic `elder` (four lines), as likely as any village news.
- **Good traits**, the first that holds: a Legend; Gifted (29.x); Master level (5) in a real trade (not jobless, not a
  nitwit); or 20 **happy days**. Happy days are counted in the hall's round, once a day per villager: a mood of 80 or
  more adds one, a lower mood starts again from 0. Days nobody counted (the village not loaded, moods off) neither add
  nor break the run.
- **Passing.** The hall's round (every 30 seconds) checks each grown villager: an elder with 40 or more elder days who
  is not ageless passes if asleep, or if it is night (13000 to 23000 on the day clock). They die as any villager does
  (damage type `aliveworkplace:old_age`), so their work is handed back, a Legend's slot is freed, and the usual grave is
  left; an elder always gets a grave, even with no job and no name.
- **Brought back.** An elder whose time had come and whom an Undertaker revives gets `passed_day` = that day and 40 more
  days from it (otherwise they would pass again the same night and waste the golden apple). A charm clears it.
- **Retirement** (`people/Retirement`) runs in the hall's round, after the passing. A successor's shadowing ends in the
  hand-over once 2 `Chronicle.day`s have passed and the elder holds nothing; if the elder dies, retires otherwise or the
  successor takes another job, it is called off. While anyone in the village shadows, and for 3 days after a
  retirement, no new one starts. The oldest elder goes first; one who holds something is passed over until they don't.
  At the hand-over the elder lets go of the workstation (`Stations.releaseOld`) and becomes jobless with their trades put
  back; the successor gets it through `VillageHalls.assign` (or the job by order when the elder had no workstation),
  then the level and its least xp. The assignments are moved as they are (`Retirement.ASSIGNMENTS`).
- **Never re-employed.** `WorkerLimits.refuse` turns down any job for a retired villager (by themselves at a
  workstation, or by order), and their search for a workstation sees none (`WorkerLimits.hideFull`).
- **Mentoring.** Each round a retired elder picks a Novice or Apprentice of their old trade (the same pupil as before,
  else the nearest) and teaches them for 90 seconds (`taught_by`), renewed every round. Taught, every xp they earn is
  half again: through `Traits.xp` (builders' levels) and from trades (`Villager.rewardTradeXp`, a mixin). The retired
  elder walks to the pupil by day, or to the hall.
- **The Book of Families** (`people/FamilyBook`) is kept by each hall: everyone who has lived in the village, with
  their id, name, family, the day they were born here or grew up (the day the book first met them, for someone already
  grown and of unknown age), the day they died, their parents' and partner's ids and their trade. The hall's round
  (every 30 seconds) writes everyone living in the village into it (not mercenaries, inn guests or a caravan's carter);
  a death dates their entry; someone an Undertaker brings back is alive in it again. It holds 400: beyond that the
  longest dead are forgotten first (if nobody is dead, the first written in). Partners stay after a death (the widowed
  one's late partner is kept).
- **Family names.** 60 names (`family_name.aliveworkplace.1` to `.60`). A founder's is picked from their UUID; a baby
  takes the first parent's at birth (the one the village's birth picks first; the other parent's if the first has
  none), and the round gives anyone still without one their parent's, else founds one. The name is only a number on the
  villager (`family_name`); the hall shows it in brackets, and only for named villagers.
- **Parents by id.** Every birth now notes the parents' ids as well as their names (`mother_id`, `father_id`). Records
  from before keep the names only: such a child takes the family of a villager living here with the first parent's
  name, and the Family page lists those parents by name ("Known by name only: from before the Book of Families").
- **The chronicle.** At a birth the baby is named at once (when villager names are on) and the line names the
  grandparents the book knows; with none known it is the old "A baby was born to Dara and Tomas". A family's
  generation is one more than their parent's of the same family (founders are 1); the first baby of generation 3 is
  noted once (kind `LIFE`), and the hall remembers which families it noted.
- **The printed tree** is a plain written book (not one of the Printer's own papers), authored by the village's Printer
  and written from the book on the day: the cover, then everyone of the family in order of generation and age, with
  their generation and trade, parents, partner and days. A player pays 2 emeralds (200 CobbleDollars with
  CobbleDollars); with no Printer in the village, or the price not in hand, nothing is printed and the hotbar says why.
- **Ageless** is one saved flag. It only stops the passing: an ageless elder keeps the slower walk, the quiet old age
  and the talk.

## Switches

All six belong to 1.8 (`Expansions.M34`): off for players, whatever the file says, until 1.8 is finished.

- `villagerAges` (true): villagers become elders. Off: nobody is an elder, so nobody is slowed, has the quiet old age,
  talks like one or passes; `adult_since` is kept, so turning it on again makes them the elders they were all along.
- `villagerElderDays` (120, 20 to 1000): grown days before a villager is an elder. Its settings button reads "Days
  Until Elder" (B102: the longer "Days to Become an Elder: 120" was cut off on its button).
- `elderPassing` (true; owner, 2026-10-06): elders pass after 40 elder days. Off: nobody dies of old age, and a charm is
  refused as not needed.
- `agelessElders` (true): Evergreen Charms work. Off: charms are refused and kept; elders already ageless stay ageless.
- `elderRetirement` (true, 34.20): elders retire and mentor. Off: nobody starts retiring; a shadowing under way still
  ends in its hand-over, and those retired stay retired and still mentor.
- `familyNames` (true, 34.21): family names are shown. Off: the hall says "Dara", the chronicle's birth lines name the
  grandparents by first name ("grandchild of Bram and Ilse") and the third generation by its founder ("Bram's family
  has its third generation"), and the printed tree is "Bram's Family". The names are still given and kept, so turning
  it on shows them again. The Book of Families and the Family page have no switch of their own: they come with 1.8.

## Saved data

On the villager (attachments; every one optional, so older saves load unchanged):

- `adult_since` (absent: age unknown, never an elder);
- `ageless` (absent: false);
- `happy_streak`: {`days` 0, `day` 0}, the happy days running and the last day counted (absent: none);
- `passed_day` (absent: never brought back from old age);
- `elder_noted` (absent: the chronicle hasn't noted them as an elder yet);
- `retired`: {`day`, `trade`, `level`}, a retired elder (absent: not retired);
- `retire_shadow`: {`elder`, `elder_name`, `since`}, a successor learning from an elder (absent: not shadowing);
- `retire_waiting` (absent: false), an elder with nobody to take over;
- `mentoring` (a pupil's id) and `taught_by`: {`mentor`, `mentor_name`, `until`} (absent: no teaching);
- `family_name`: the family's number, 1 to 60 (absent: none yet; the next round or a birth gives one);
- in `parents`: `mother_id` and `father_id` (absent in records from before 34.21, which keep the names only).

On the hall: `families`, the Book of Families, a list of {`id`, `name`, `family`, `born`, `born_here`, `died` (absent
for the living), `mother`, `father`, `partner` (each absent when unknown), `trade`}; and `familiesNoted`, the families
whose third generation the chronicle noted. Both absent in halls saved before: the book starts empty and fills at the
next round.

The chronicle has a new kind, `LIFE` (a clock); an older version reads an unknown kind as `FOUNDED`.

## Items, blocks, jobs, commands

- **Evergreen Charm** (`aliveworkplace:evergreen_charm`): stacks to 1, rare. Recipe `evergreen_charm` (shapeless),
  unlocked by holding a totem of undying or a heart of the sea. Texture drawn in `tools/textures/art/items.py`
  (`evergreen_charm`). In the creative tab after the wines.
- Damage type `aliveworkplace:old_age` (death message "%s passed away in their sleep, an elder").
- The family tree (34.21) is a vanilla written book; nothing new is registered. The Family button and page use
  vanilla items as icons (a flowering azalea, logs, a poppy, leaves, a sapling, seeds, name tags, eggs and skulls).

## Decisions

- 34.19 (owner, 2026-10-06): elders do pass in their sleep after 40 elder days and leave a grave. The design note
  `docs/design/M34.md` had this off by default; the owner turned it on.
- 34.19: "15% slower" is measured over the ground, not in the walk target's number (see "The walk").
- 34.19: "a quiet old age" is listed first among the good reasons, since the card shows only four reasons.
- 34.19: the chronicle's note is written once per villager for good: if `villagerElderDays` is raised and lowered again,
  nobody is announced twice.
- 34.19a (owner, 2026-10-06): an elder with good traits can be made eternal with a crafted item; the recipe is ours to
  tune in play.
- 34.19a: with `agelessElders` off, elders already ageless stay so. Turning a switch off should never cost a player a
  villager they paid a totem and a heart of the sea for.
- 34.19a: with `elderPassing` off the charm is refused and kept ("nobody needs one"), not silently used up.
- 34.19a: "a Mood of 80 or more for the last 20 days" is 20 counted days running; days the village wasn't loaded
  don't break the run.
- 34.19a: a revived elder gets 40 more days (see above); this also gives a second chance to hand them a charm.
- 34.19a: an elder leaves a grave even without a job or a name (a retired elder, 34.20, will have no job).
- 34.19a: the hall's card warns from 10 days before, so the player knows when a charm is needed.
- 34.19a: the charm is a sneak-right-click on any villager; it never opens trades and never changes a job.

- 34.20: retirement starts in the hall's round and runs on its days; the 3 days count from a retirement, and a
  shadowing under way also holds the next one back, so a village never has two at once.
- 34.20: a retired elder keeps their trades (no restocking without a workstation) and the level they had; a retired
  Master still counts as a Master for the Evergreen Charm.
- 34.20: "a jobless grown villager" leaves out elders and the retired: an elder never takes over from another.
- 34.20: the successor's own trades are those of their new level (vanilla makes them when the job is new).
- 34.20: "taught" lasts 90 seconds after the round that saw the pair, so a mentor who stops (the pupil levels up past
  Apprentice, either leaves the village) stops teaching within a round.

- 34.21: villagers have no recorded gender, so the chronicle says "grandchild" where the roadmap's example says
  "granddaughter", and "Brothers and sisters" is one group.
- 34.21: a baby takes the first parent's family name (the mother's slot of `Families.Parents`, which is whoever the
  birth picked first); the roadmap only says "a parent's".
- 34.21: babies are named the moment they are born (they used to be named at the hall's next round), so the birth's
  chronicle line can say who was born.
- 34.21: family names are given even with `familyNames` off, and only hidden, so turning it back on loses nothing.
- 34.21: the Family button is on the villager's life story page (shift-click on the hall's list), the page that is
  "theirs"; a click on a relative opens that relative's Family page, and Back goes to the life story.
- 34.21: the Printer prints from the Family page (any family: open anyone's page), with the price taken from the
  player; a fixed trade offer could not say which family. The village must have a Printer (34.11) with `printers` on.
- 34.21: a record from before the ids is matched to a living villager by the first parent's name for the family name
  only; the book keeps such a child's parents unknown, as the roadmap says.

## Known limits

- Planned, not built, so no player sees it (34.18): the elder look. Its art is drawn (34.17: `class/elder.png`, grey
  brows and a short beard until the owner picks; see the Classes page), but the render layer and its packet don't
  exist yet, so an elder looks like any villager of their trade.
  34.18 should draw the look for whoever `LifeStages.stage` calls an elder, and send its packet where the chronicle's
  note is written (`LifeStages.becameElder`).
- Only the walks our jobs ask for are slower (through `Walker`); vanilla's own strolls are not.
- The successor's shadowing walk and the mentor's walk use the villager's own tick, so with the village far from
  every player nothing moves; the hand-over still happens in the hall's round.
- A retired elder "sits by the hall": they walk there and stand; there is no seat to sit on yet.
- The happy-day count needs a Village Hall and `villagerMoods` on; without them only Masters, Gifted and Legends qualify.
- An elder in a village whose hall is never loaded at night doesn't pass until it is.
- A villager who leaves the village alive (a colony's settler, one who wanders off) stays in its book as living; the
  book only learns of deaths near a hall.
- Villagers already grown when the book first meets them are dated from that day (or the day they grew up, when known);
  34.22's seeding of ages rewrites the day of an entry the book wrote before it (the book had dated them that day).
- A family's third generation is noted only when the baby is born in a loaded village with a hall; one born the vanilla
  way is noted at the hall's next round, without the grandparents' birth line.

## Proof

- GameTests: `LifeStageGameTests`: the world's clock moved on to grown day 119 (not an elder) and 120 (an elder, the
  hall's line, the chronicle's note once), and 30 configured days (`aVillagerIsAnElderAfterTheConfiguredDays`); the walk
  target every worker gets, an ageless elder's too, and two villagers walking side by side for 60 ticks, the elder
  covering 80 to 90% of the ground (`anElderWalksSlower`); a walk the game took over at its own pace asked again at the
  elder's (`b95AnElderIsAskedAgainWhenVanillaTakesTheWalkOver`) and a walk the game had already started slowing to 80 to
  90% (`b95AnElderSlowsAWalkVanillaStarted`); the mood 5 higher than a younger villager's same day, first on
  the card, gone when hungry or without a bed, and the four lines word for word (`aFedAndHousedElderHasAQuietOldAge`);
  a reload, an older save's villager, an elder made ageless before this version (`aReloadKeepsTheDay`); a child grown up
  in the hall's own round, and an elder noted by it (`aChildGrowingUpIsGrownFromToday`); `villagerAges` off and on again
  (`withVillagerAgesOffNobodyIsAnElder`).
- Showcase scene: `elders` (the day Bram becomes one, his card, his line, the walk beside young Tom).
- GameTests: `AgelessElderGameTests`: the recipe, tooltip and config (`theCharmIsCraftedAndExplainsItself`); a Master
  through the sneak-right-click, then a Gifted villager, a Legend and a 20-happy-day elder, the charm used up, the gold
  badge, the chronicle, a second charm refused, creative keeps it (`anElderWithAGoodTraitTakesTheCharm`); every refusal
  with its sentence and the charm kept (`anyoneElseRefusesAndTheCharmIsKept`); happy days counted from a real mood and
  reset by a hungry day (`happyDaysAreCountedFromTheMood`); day by day to 45 elder days, the ageless elder alive, the
  others gone on the 40th with graves, a reload, an older save (`anAgelessElderOutlivesFortyElderDaysAndAReload`); the
  passing in the hall's own round with a grave and the chronicle, the revival's 40 more days
  (`anElderPassesInTheirSleepAndLeavesAGrave`); each switch off, and a child grown up getting today
  (`theConfigSwitches`).
- Showcase scene: `ageless_elder` (a refusal, the charm taken, the night after 45 elder days, the badge).
- GameTests: `RetirementGameTests`: the full hand-over in order, the own child chosen over another, two days of
  shadowing kept through a save, then the composter, the level one below, the assignments, the kept trades and both
  chronicle lines (`aFullHandOverInOrder`); each "never while" case and the start once nothing is held
  (`neverWhileTheyHoldSomething`); nobody to take over and the hall's line, `elderRetirement` off, one at a time and 3
  days apart, a Novice's successor an Apprentice, a Master's an Expert, a retired Master still a Master
  (`oneRetirementEveryThreeDays`); a retired elder refused a workstation, a job by order and the successor's place
  (`aRetiredElderIsNeverReEmployed`); the mentor's 50% and "taught by Bram" (`aMentorTeachesFiftyPercentFaster`); the
  switch (`theConfigSwitch`).
- Showcase scene: `retirement` (learning from Bram, the hand-over and Bram's card, taught by Bram).
- GameTests: `FamilyTreeGameTests`: founders' names by UUID, Dara taking Bram's, the hall's "Dara (Ashby)", a baby born
  through the village's own birth with the parents' ids and Dara's family, the birth line naming the grandparents, the
  third generation noted once, the Family button and page, a death dated and marked, the Printer's tree refused without
  a Printer and for 1 emerald, printed for 2 with its cover and pages, the hall's book and the villager's family through
  a save, an older hall loading empty (`threeGenerationsADeathAndTheTree`); 450 people kept to 400, the longest dead
  forgotten, never the living, and a save (`theBookKeepsFourHundredTheLongestDeadForgottenFirst`); an old Parents
  record with no ids, the family by the parent's name, the parents by name only (`anOldParentsRecordWithoutIds`);
  `familyNames` off through the config (`familyNamesOffGoesByFirstNames`); every new sentence and the 60 names
  (`everyNewSentenceReads`).
- Showcase scene: `family_tree` (the hall's list, Dara's Family page, Mira's grandparents with Bram marked, the printed
  tree as a book).
