# Classes

Every household in a village with a hall lives as a class: Peasant, Artisan, Burgher or Noble. Meet the next class's
needs and the household rises; lack your own and it falls. Part of 1.8 (Classes and luxuries): switched on in 1.0 with
every other expansion (`Expansions.M34`).

Roadmap items: 34.1, 34.5, 34.6, 34.7, 34.8, 34.9, 34.10, 34.11, 34.17, 34.17a, 34.18, 34.22, B398

## What a player sees

A household is one grown villager, or a married couple. Each has a class. A household that meets the next class's
needs two dawns running rises: golden sparkles and a chime at their door, a chat line to players within 32 blocks, a
line in the chronicle, and "rose in the world" (+10 mood for 2 days). One that lacks a need of its own class three
dawns running comes down in the world (-10). Children take the class of the grown-ups they live with.

Villagers talk of it (three lines each): of their rise or their fall while the mood lasts; of wine or new clothes
when their class (its needs and wants) or the next one's needs ask for a wine or a clothes luxury they haven't had
lately ("Look at these rags. I need something new to wear."); of the Gazette the week their household reads one; and
of the tavern when it reaches their home.

At the Village Hall:
- the **Classes** tab says how many households of each class there are in the village (its circle, not the corners
  beyond it). Its page has a button per class: each need
  and want with how many households have it ("A varied diet: 2 of 4"), what the class gives, and the households
  closest to rising with what they lack;
- the people list says each villager's class and ticks the needs of their class and of the next one;
- the food icon lists the luxuries in store;
- "What next?" gives up to three class tips ("2 Peasant households want A varied diet to rise to Artisan"), each
  with how to meet it ("Keep 3 kinds of meal in the store"; 2 for a plain diet).

| Class | Needs | Wants | Gives |
|---|---|---|---|
| Peasant | nothing | nothing | pays the usual tax |
| Artisan | a grade I home, fed 3 days running, a varied diet, a chapel, school or clinic near home, Work Clothes | Cider, a tavern near home | tax x1.5; crafters 10% faster; opens the Artisan jobs |
| Burgher | a grade II home, fed 3 days, a varied diet, a school and one more service, a Market Square, Fine Clothes, Berry Wine, the Gazette | an Amethyst Ring, a library, beauty (with Cobblecuisine, a Deluxe Shake) | tax x2.5; scholars research 15% faster; with 3 such households one more caravan route and market trader; opens the Burgher jobs |
| Noble | a grade III home, fed 3 days, a varied diet, chapel, school, clinic and library, Town rank, beauty, Noble Robes, Vintage Wine, an Emerald Brooch | an Illuminated Book, a Gold Circlet (with Cobblecuisine, a Clodsire Eclair) | tax x4; +3% wellbeing per household up to 9%; every other festival is a Noble's Ball |

**Higher jobs need higher classes (34.8).** The Tinkerer, Chef, Netherworker, Nurse, Teacher, Shopkeeper, Innkeeper,
Printer and, with Cobblemon, the Ball Smith, Move Tutor, Pokémon Trader and Fossil Scientist need an Artisan; the Scholar,
Undertaker, Jeweler and Trainer Leader a Burgher. Vanilla jobs are open to everyone. Nobody is ever fired: a worker below
their job's class keeps it, and the hall's list marks them.

**The Jeweler (34.12).** Stand a villager by a stonecutter and sneak-right-click them with a gold nugget (a clay
ball brings the Mason back; an amethyst shard picks the Gem Grower). A Burgher's job. A Novice makes the Amethyst Ring
from 2 amethyst shards and 2 copper ingots; an Apprentice the Emerald Brooch from an emerald and 3 gold nuggets; a
Journeyman the Gold Circlet from 2 gold ingots, an emerald and an amethyst shard. The ring is a Burgher's want and
the brooch a Noble's need, every 16 days; the circlet a Noble's want, every 32.

**The Vintner (34.9).** Stand a villager by a cauldron and sneak-right-click them with sweet berries, glow berries
or an apple. A Novice presses Cider (3 apples and a glass bottle), an Apprentice Berry Wine (6 sweet berries, 4 glow berries or,
with Cobblemon, 4 of its berries, and a bottle) and a Journeyman Vintage Wine, from a Berry Wine at least 3 days old. A Berry Wine
bought from a Vintner is pressed the day it's sold and waits its three days too. The drinks are luxuries the households take from the store, and players can drink them (the bottle comes back).

**The luxury workshop engine (34.5).** The Vintner, Tailor, Printer and Jeweler make their goods from data:
`data/aliveworkplace/luxury_recipes/<id>.json` names the job, the level that may make it, the makings (items or tags,
each with an optional `min_age_days`), the output and the ticks. A maker (`LuxuryWork`) fills the stock orders the store
holds first, then makes, in turn, whichever good the store and their own chests hold fewer than 8 of, from their own
chests, the store and the other workers' chests (never a builder's), and only from recipes up to their level. A good that
ages carries the day it was made (`made_day`): its tooltip reads "Pressed on day 42 · vintage in 2 days" (a Berry Wine),
then "Pressed on day 42 · vintage". A maker takes an aged making only once it is old enough. When nothing short can be made, the first
missing making goes on the requests board. Craftsmanship research speeds the makers like the other crafters. A data pack adds a good with
a new recipe file and no code.

**The Tailor (34.10).** Stand a villager by a loom and sneak-right-click them with string (shears bring the Shepherd
back). A Novice sews Work Clothes from 3 wool of any colors, 2 leather and a string; an Apprentice Fine Clothes from
4 dyed wool (any colors but white), a string and 2 gold nuggets; a Journeyman Noble Robes from 5 wool of one rich
color (purple, blue, red or black), a rabbit hide and a gold ingot. The clothes are the luxuries the Artisans,
Burghers and Nobles need, every 8 days.

**The Printer (34.11).** Stand a villager by a cartography table and sneak-right-click them with an ink sac (a compass
brings the Cartographer back, netherrack the Netherworker). A Novice prints 2 books from 3 paper and a leather (kept in
stock only while the village has a Scholar) and 2 copies of the Village Gazette from 3 paper and an ink sac; a
Journeyman an Illuminated Book from a book, 2 gold nuggets, a lapis lazuli and a glow ink sac. The Gazette is the
luxury the Burghers need every 7 days, the Illuminated Book the one the Nobles want every 16. Both read like a written
book (right-click): the Gazette is printed from the hall that day (the newest chronicle entries, the open quests and
what they pay, the next festival and market day, the week's births, weddings and households that rose), the
Illuminated Book is the whole chronicle. The hall quest **Spread the news** (a Printer and a caravan route) pays 6
emeralds for this week's Gazette handed in at the hall of the village at the other end of the route.

## How it works

**Classes are data.** One file each in `data/aliveworkplace/classes/`: its tier, tax, needs, wants, the jobs it
opens and its effects. A data pack replaces one, adds its own or switches ours off. Each file lists all of its needs;
nothing is inherited from the class below.

**The dawn check.** Each dawn the hall checks its households 8 at a time. When the next class's needs held
`classRiseDays` dawns running (2) the household rises one class; when a need of its own class failed
`classFallDays` dawns running (3) it falls one, never below Peasant. One step a day at most. For a couple a need
holds only when it holds for both. The dawn counts only villagers inside the village's circle (the hall's radius, as
the census counts them), not one standing in the corners of the square round it (B549).

**Needs.** A home's grade comes from the building their bed is in. "Fed" counts dawns fed running. Diet goes by
their last meals. Services (chapel, school, clinic, library, market, tavern) are data too
(`data/aliveworkplace/services/`): a service is given by a worker of the right job or a finished building, and
reaches homes within 48 blocks (the market reaches the whole village). The hall works the list out once a day.
Luxuries (`data/aliveworkplace/luxuries/`) are taken from the village store at dawn, one per household, when due.

**Mood.** Every villager has "has what their class needs" (+5), or -5 for each need lacking (at most -15).

**What a class gives (34.7).** Taxes are the treasury's daily share times the class's factor, 10% more for each
want the household has; a Peasant pays exactly what every worker paid before, so no village takes in less. A Noble
without a job pays like a worker. The hall's name tag tooltip shows the split ("Daily takings by class:", then
"Burgher: 1 pays 0.30 emeralds"). Speed
bonuses go through the one pace rule and its cap. Nobles lift the village's wellbeing 3% a household (9% at most). With a Noble household, every other festival is a
Noble's Ball: the guests gather in a Manor III's ballroom, else a Manor's hall, else the Village Hall; wine and the
store's best food, gold fireworks at dusk, +15 mood for 3 days for everyone who came, and players there are Heroes of
the Village for the night. A Legend lives among the top class.

**The job gate** applies only when a job is taken: picking it with its item, the hall's free-workstation list, a
grown child taking a parent's trade (any job a free block offers, so a Scholar's child may take a free lectern as a
Scholar), the Steward's morning jobs. A hired traveler arrives with the class of their
level (Apprentice: Peasant, Journeyman: Artisan, Expert: Burgher).

## Switching it on (34.22)

The day a server first runs 1.8, every village with a hall switches on by itself, a little at a time:

- **Seeding.** A hall saved before 1.8 has never been seeded (its classes version is 0). Each of its rounds (every 30
  seconds) gives a class to the next 16 villagers who have none, so a City of 36 takes about a minute and a half. A
  household gets the highest class whose needs hold right now, not counting luxuries and at most Burgher (Nobles are
  earned); "fed 3 days" counts as "fed now", since nobody counted fed days before. Children get their parents' class
  (else that of the grown-ups in their building, else Peasant). Grown-ups who have no age yet get one spread over the
  last 100 days by their UUID, so the first elders come about three weeks later and one at a time; everyone gets a family
  name. Mercenaries, inn guests and caravan parties are skipped. Only villagers inside the village's circle (the
  hall's radius, as the census counts them) are seeded by its hall; one standing farther off is left for the next
  round that finds them in a village.
- **Told once.** When the last one has a class the chronicle says "The people of Thornholm found their places: 14
  peasants, 6 artisans and 2 burghers", and the players in the village (its circle) get the same line in chat with the days they
  have to settle in.
- **Settling in.** For `classGraceDays` (7) days nobody falls a class, luxuries are taken when due but not yet needed
  (rising still needs them, and the mood doesn't count them as lacking), and no elder starts retiring. The Classes tab
  and page header say "Settling in: 5 days left", and a clock on the Classes page lists each class's luxuries it will
  need ("Artisan households will need Work Clothes").
- **What never changes.** Nobody loses a job (a Scholar who turns out a Peasant keeps working, marked below his job's
  class), ranks keep their thresholds, a Peasant pays what everyone paid before so the treasury never takes in less,
  and build sites, quarries, daycare Pokémon, fossils, parcels and chests are not touched.
- **Later and odd cases.** A hall first loaded weeks later goes through the same with its own week. Nothing about how
  far it got is kept: a villager with a class is seeded, so a crash part-way re-seeds only the rest and nobody twice. A
  villager who arrives later is seeded at the next round, quietly (a hired traveler still gets the class of their
  level). `villageClasses` off: nothing is seeded and nothing held back; what was seeded stays saved.
- **Operators:** `/workplace classes status` (the nearest hall: seeding, settling in or settled, and the counts) and
  `/workplace classes reseed` (the hall goes back to unseeded and its villagers lose their class and progress; ages
  and family names are kept; the next rounds seed them again, with a new week).

## Switches

| Key | Default | What it does |
|---|---|---|
| `villageClasses` | on from 1.0 | Off: no classes, no job gate, no class taxes; what is saved stays |
| `classRiseDays` | 2 (1 to 30) | Dawns running the next class's needs must hold to rise |
| `classFallDays` | 3 (1 to 30) | Dawns running a need of their own class must fail to fall |
| `classGraceDays` | 7 (0 to 30) | Days a village settles in after it is first seeded (34.22): nobody falls, luxuries aren't needed, nobody retires |
| `jewellers` | on from 1.0 | Off: no Jeweler job, and Jewelers already hired stand idle |
| `vintners` | on from 1.0 | Off: no Vintner job, and Vintners already hired stand idle |
| `tailors` | on from 1.0 | Off: no Tailor job, and Tailors already hired stand idle |
| `printers` | on from 1.0 | Off: no Printer job, Printers already hired stand idle, no Spread the news quest |
| `classOutfits` | on from 1.0 | Off: villagers wear only their job's outfit (no class layer, no elder's face) and no class packet is sent |

## Saved data

On each villager (attachments): `social_class` (their class id; absent until the village is seeded),
`class_progress` (dawns the next class's needs were met, dawns a need was missed, days fed running, and the day last
counted; all 0 at first), `class_standing` (where they stood at their last dawn: needs lacking, their last rise or
fall, wants had; an older save reads the wants as none), `luxuries_had` (the day each luxury was last had; empty).
On the hall: `services` and the day they were worked out, `ballTurn` (false) and `ballDay` (-100) for the Noble's
Ball, `classesVersion` (0: never seeded; 1: seeded with 1.8's rules) and `classGraceUntil` (the day settling in ends;
0). Seeding also writes `adult_since` and `family_name` (see [Elders](elders.md)) where they are missing. Households and the village's sums by class are never saved: they are worked out from the villagers.

## Items, blocks, jobs, commands

- Class ids: `aliveworkplace:peasant`, `aliveworkplace:artisan`, `aliveworkplace:burgher`, `aliveworkplace:noble`.
- Jobs: Vintner (`aliveworkplace:vintner`), at a cauldron; Tailor (`aliveworkplace:tailor`), at a loom; Printer
  (`aliveworkplace:printer`), at a cartography table.
- Items: Cider (`aliveworkplace:cider`), Berry Wine (`aliveworkplace:berry_wine`), Vintage Wine
  (`aliveworkplace:vintage_wine`), Work Clothes (`aliveworkplace:work_clothes`), Fine Clothes
  (`aliveworkplace:fine_clothes`), Noble Robes (`aliveworkplace:noble_robes`).
- The Jeweler (`aliveworkplace:jeweller`), at a stonecutter, and their pieces: the Amethyst Ring
  (`aliveworkplace:amethyst_ring`), the Emerald Brooch (`aliveworkplace:emerald_brooch`) and the Gold Circlet
  (`aliveworkplace:gold_circlet`).
- Item tag: `dyed_wool` in `data/aliveworkplace/tags/item/` (every wool but white: what Fine Clothes take).
- Texts: `class.aliveworkplace.peasant`, `class.aliveworkplace.artisan`, `class.aliveworkplace.burgher`,
  `class.aliveworkplace.noble`.
- Commands: `/workplace classes status` and `/workplace classes reseed` (operators, the nearest hall; 34.22).

## Decisions

- 34.7: a Peasant pays exactly the old tax, so switching classes on never makes a village poorer.
- 34.8: vanilla jobs are never gated (owner's call, design note `docs/design/M34.md`), and nobody is ever fired.
- Design note: each class file lists all of its needs. The Noble's file repeats "fed 3 days, a varied diet", which
  the roadmap's Noble line left out; without it a Noble could live on bread alone.
- Design note: the Burgher's "a school and one more service" is two needs, and the Market Square is a building
  need, so the market doesn't double as the "one more".
- Design note: a luxury is taken at dawn before the needs are checked, so one taken that dawn counts that day.
- 34.12: the Jeweler is picked with a gold nugget, not the roadmap's amethyst shard: the shard at a stonecutter has
  picked the Gem Grower since 28.11 (design note `docs/design/M34.md`; the owner can still change it).
- 34.11: the Gazette and the Illuminated Book are our own items (`aliveworkplace:gazette`, `aliveworkplace:illuminated_book`)
  carrying vanilla's written-book content, so the luxury files name them and any other written book doesn't count. A
  Gazette notes its hall and day; "this week's" is 7 days, the day it was printed included. A blank one (bought, or
  from the creative tab) is printed where its holder stands when bought or first read. Quest objective type
  `spread_news` (`quests/daily/spread_news.json`).
- 34.10: Work Clothes and Fine Clothes may mix wool colors; Noble Robes need all five of one color. The Tailor takes
  wool from the village's chests and the store, and dyes white wool themselves when dye is to hand: the dyer still
  only dyes for builders.

- 34.22: seeding caps at the ladder's third class (as a hired traveler's, 34.8), reads `fed_days` as "fed now", and
  sorts children by their parents first (by id), then their building. The age spread is worked out from the UUID, so a
  re-seed gives the same ages. The hall's `families` book entry written before seeding gets the seeded age. The
  migration lives in `people/ClassMigration` (the design note's `hall/ClassMigration`: it works on villagers).

## Dressed by class (34.17, 34.17a)

Each class but the Peasant has a layer worn over the job's outfit, and elders a face layer (34.19's look). Three
versions of each were drawn for a pick round; the owner picked one per row on 2026-10-11 (34.17a), and only those ship:

| Layer | What it is (the owner's pick) |
|---|---|
| Artisan (version 1) | a blue neckerchief knotted behind, and a leather belt pouch with a brass stud |
| Burgher (version 2) | the buttoned edge of a dark frock coat with brass buttons, its tails behind, a pocket square at the breast |
| Noble (version 1) | a crimson mantle with an ermine collar and hem, and a gold chain of office |
| Elder (version 1) | gray brows and a short gray beard |

- Textures: `textures/entity/villager/class/<class>.png` (`artisan`, `burgher`, `noble`; the class file's `outfit`
  names it) and `class/elder.png`. A Peasant has no `outfit`, so the job's outfit stays as the owner approved it.
- The rules every layer keeps: never on the head (the job's and the biome's hats stay), nothing under the level badge,
  the back painted, the job's apron and hat still in view. The elder's layer is only the face, under any hat.
- What a villager wears, bottom to top: their class's layer, then the elder's face if they are an elder (children wear
  their class's too, never the elder's; with `villagerAges` off nobody wears the elder's face). `people/ClassOutfits`
  says this for 34.18's render layer.
- In game (34.18): two client render layers on the villager model (never the zombie villager's), the class's over the
  job's outfit and then the elder's face, on grown villagers and children alike. The client learns each villager's
  class and stage of life from a small packet (`aliveworkplace:class_look`: entity id, class, layer, stage) sent when a
  player starts tracking the villager and again when either changes (a household rises or falls, a child takes their
  parents' class, a villager becomes an elder). `classOutfits` off: nothing is sent or drawn. Scene `class_outfits`.
- Recipes: `tools/textures/art/classes.py`, one per layer (`LAYERS`). The versions not picked, and the picker page
  that showed them, were removed in 34.17a. Nothing saved names a version: a villager's look follows their saved class
  and age, so worlds from before the pick show the picked looks at once.

## Known limits

- Seeding counts villagers loaded round the hall: one standing in an unloaded chunk at the village's edge is seeded
  when it next loads, without a second chronicle line.
- The grander homes are on [Grander homes](grander-homes.md), the elders and family trees on [Elders](elders.md).
- In GameTests classes are switched off unless a class test turns them on, so other tests' numbers don't move.

## Proof

GameTests: `ClassGameTests` (16: the four files are the ladder, each kind of need, one step a day), `ClassHallGameTests`
(6), `ClassPerkGameTests` (7), `ClassJobGameTests` (7), `ServiceGameTests` (8), `LuxuryGameTests` (7),
`LuxuryWorkGameTests` (7), `ClassMigrationGameTests` (7: an old village seeded 16 a round with a save part-way, nothing
a player built or earned changed, the week of settling in, a hall loaded later, `villageClasses` off, the command, every
new sentence), `ClassOutfitGameTests` (2: every class but the Peasant names a 64x64 layer that ships, and
what a villager wears by class and age), `JewellerGameTests` (10), `VintnerGameTests` (10), `TailorGameTests` (10), `PrinterGameTests` (11).
QA: `QaLuxuryTradesGameTests` (13: hiring refused with no job block in reach, at a taken loom and with each switch off;
each recipe's boundary from both sides; a reload mid-recorking; the made-day tooltip's edges; every needed luxury made
by a job the class below may take; the Vintner's trades at every level).
QA: `QaClassHallRetirementGameTests` (34.6 and 34.18 part: a want counted over five households, "A varied diet: 2 of
5" under the wants; the three biggest of five class tips, most households first; -10 for two needs lacking and -15
for three; the rise's chat line read 31 blocks off and not 33 off; a child sent their class and CHILD on tracking, a
zombie villager never sent a look, nothing on tracking with `classOutfits` off, the look surviving the wire).
`ClassChatterGameTests` (5, B551 and B552: wine and clothes talked of when the class lacks them and not once had, the
Gazette the week it's read, the tavern where it reaches home, every new line, the plain diet tip's 2 kinds) and
`QaClassHallRetirementBugTests` (three lines each for wine, clothes, the Gazette and the tavern).

Showcase scenes: `classes` (a household rises, and the Classes page), `class_switch_on` (the hall before and after the
switch), `class_jobs`, `noble_ball`, `jeweller`, `vintner`, `tailor`,
`printer`.
