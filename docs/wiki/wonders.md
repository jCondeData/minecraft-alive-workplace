# Wonders

A Wonder is a village's life's work: a huge build raised in four stages by every builder in the village at once, fed by
goods from every job, drawn up by the Master Architect and needing a second Legend. Each of the eight Wonders changes
what its village or its realm can do while it stands. Part of 2.0 (Wonders): the data files, requirements and caps, the
Wonders page, the Wonder Plan, the Foundation Stone with its stages, crews and goods, a finished Wonder's celebration,
upkeep and powers, and the first Wonders' designs (the Great
Cathedral, the Lighthouse, the Great Library, the Observatory, the Great Wall, the Hanging Gardens, the Colossus and, with
Cobblemon, the Stadium) are built, behind the 2.0 gate (`Expansions.M35`), which 1.0 switches on with every other
expansion; the full plan is the design note,
docs/design/M35.md.

Roadmap items: 35.1, 35.2, 35.3, 35.4, 35.5, 35.7, 35.7a, 35.7b, 35.8, 35.8a, 35.9, 35.10, 35.11, 35.12, 35.13, 35.14, 35.15, 35.15a, 35.16, 35.17, 35.18, 35.18a, 35.19, 35.20, 35.21, 35.22, 35.23, 35.23a, 35.24, B139, B141, B140, B138, B678, B703, B270, B308, B326, B461

## What a player sees

**The Wonders page** (35.3): a Wonders tab on the Village Hall (a spare tab, so the tabs kept for add-ons stay free).
Each Wonder on the server's list is a card: its icon, name and power, then "Needs:" with each requirement ticked (✔) or
crossed (✘) with the village's progress ("Finished Chapel: 2 of 3", "Standing in for the Master Architect: …"), the cap
("Wonders: 0 of 1 for a Town") and what a click does. A Wonder going up here shows its stage and how far along it is
("Going up: stage 2 of 3, The Upper Story (33% along)"), a finished one its day.

**Draw up the plans**: with every line ticked and the caps clear, a click on the card pays 32 emeralds (the realm's
treasury if the village is in a realm and realms are on, else the hall's treasury, else the player's own emeralds; free
in creative when neither treasury has them) and gives a **Wonder Plan**, a rolled blueprint with a gold seal. Only the
hall's owner, their friends and operators may. Before that, a click says what's missing and takes nothing.

**The Wonder Plan** holds the whole Wonder's blueprint:

- held and placed, it shows the whole Wonder as see-through blocks, like any blueprint;
- right-click the ground to place it (its front faces you), sneak-right-click the ground to turn it, sneak-right-click
  the air to pick it up again or, not placed, open the style screen, where it can only be mirrored (each Wonder keeps
  its own blocks);
- it goes down only with its center within the hall's reach (64 blocks), every corner within 96 blocks of the hall, and
  not over a finished building or another Wonder; otherwise it says why ("It would stand over the finished Chapel");
- a Book and Quill in the other hand writes the whole Wonder's material list.

**The Foundation Stone** (35.4): right-click a builder of the village (their bench within 64 blocks of the hall) with
the placed plan. A **Foundation Stone**, a carved stone with a brass plaque, goes down just outside the Wonder's front
corner (the next free spot round it if that one's taken); everyone on the server reads "Owner has begun the Test Folly
in Testholm!", the chronicle gets "Owner laid the Foundation Stone of the Test Folly", and stage I starts as soon as
its goods are in (35.7, below; at once if the stockpile holds them already).
The chests within 8 blocks of the stone are the Wonder's stockpile. Refused, saying why, when the plan isn't placed,
the builder has no bench or works for another village or another player, the caps no longer allow it, or there's no
room for the stone.

- **Right-click the stone**: "The Test Folly of Testholm", "Stage 1 of 3, The Ground Story: 34% built", then the
  stage's material list, each line "40 Stone Bricks, 12 still to bring" or "… all there", what's still to bring first.
- **Each stage starts when every part of the one before is done and its own goods are dedicated**, and the builder it
  was handed to takes it on (the owner reads "Stage 2 of 3 of the Test Folly is next: The Upper Story. It begins once
  its goods are in").
- **The builder tells the owner of each part it finishes** (B138): with other parts of the stage still going up, "Odo
  finished a part of the Test Folly: 1 of 2 parts of stage 1 (The Ground Story) stand."; the stage's last part, "Odo
  finished stage 1 of 3 of the Test Folly: The Ground Story."; the Wonder's very last part says nothing itself, as
  everyone hears the Wonder is finished. No blueprint comes back for a part.
- **The builder tells the owner when he takes a part up** (B678): "Odo is starting on a part of stage 1 of the Test
  Folly (The Ground Story) next.", never the part's file name ("Stage 1 0"). With the owner logged off nobody is told,
  and a part whose Wonder is not on a stage (not started, or past its last) gets no line.
- **A part goes by its Wonder's name** (B703): everywhere a building's name would show (the builder's status when you
  sneak-right-click him, the words over his head, the Village Hall's list of buildings going up, the waiting-for-materials
  line and his material list), a part reads "Stage 1 part of the Test Folly", never "Stage 1 0".
- **Sneak-break the stone** (the owner or an operator) or `/workplace cancel` with the Wonder's id (the stone's screen
  has an **[Abandon this Wonder]** link that puts it in the chat; works at any stage, also while it waits for its goods,
  B462) or a part's id to abandon the Wonder: what's built stays, the goods handed in for a stage not yet dedicated
  come back, the open parts end, the plan comes back (to the owner, else by the stone) and the chronicle says so. A
  plain break says to sneak; anyone else is refused. Builders never clear it and pistons can't push it; a finished
  Wonder's stone breaks like any block (it drops nothing).

**Crews** (35.5): every builder of the village (their bench within 64 blocks of the hall) with no build of their own
joins the Wonder's crew, up to 12 (the `wonderCrew` switch, and no more than the Wonder's file allows). The stone's
status says who leads and how many are on it ("Led by Odo, with a crew of 8 (at most 12)"), and so does the Wonder's
card on the hall's Wonders page. Click **[Call every builder]** under the stone's status (the owner or an operator,
within 8 blocks of the stone; `/workplace wonders call`) and the busy builders come too, each as they finish what
they're on: "Every builder is called". The lead is the Master Architect when he's one of the village's builders,
else the builder it was handed to; each next stage goes to the highest-level builder of the crew.

- Each member takes a **column** of its own (the part split at least 8 × 8: a 64-wide part has 8-block columns, the
  9-wide folly 1-block ones), the free one with work nearest to them, and works it in the stage's order: clearing from the top, the rest
  from the bottom. Nobody works more than two layers above the part's lowest unfinished layer (a block the lead gave up
  on, out of reach, doesn't hold the rest back; leveling the ground round it isn't layered), and no structure block
  goes up before the block the drawing puts under it, not even over one the lead gave up (someone stood in it the whole
  time): the crew lays a given-up block once nothing stands in it, and the wall goes on from there. If nobody can lay
  it and the crew has stood still for 10 seconds, the lead gives up the block over it too (every block waiting so at once, B270), so a
  part may end with a hole but never with a block in the air (B254); given-up blocks stay given up after a restart (B139). A
  villager boxed in where a block goes, with nowhere in reach to step to (inside the Colossus's lantern, in the ring of panes
  round its glowstone), is moved to the nearest free spot further off (up to 16 blocks), so the block isn't given up (B308). A block
  the crew laid that then changes by itself (farmland drying out to dirt before its water comes) is left as it is,
  not laid again, and a crop due on dried-out farmland is planted once the builder has tilled it again.
- A member that holds a column and never lays its block (it keeps walking for a block it can't reach) doesn't keep
  the lead waiting: once the crew has stood still for 10 seconds while the lead goes back over the blocks it put off,
  the lead takes that column over and lays the block itself (it can put up scaffolding to reach it), then the ones over
  it. Before, a Wonder crew could stand idle 30 seconds and more near the top of a build (B326).
- Crews add hands, not speed: each builder keeps their own pace. Eight builders raise the folly in about 2,500 to
  3,100 ticks against about 12,150 for one alone (at one block a second): about a quarter of the time.
- The crew fetches from the chests round the stone and the village's stores, and passes materials to each other. In
  one trip each member takes for the rest of its column and a share of the stage, dealt round the crew like cards (each
  block's materials to half of the crew), plus a little of the stages after it (the decoration's lanterns and doors), so
  nobody walks down from a dome to the chests for every block.
- If the lead dies, its part goes to the highest-level member of its crew (else to the builder the next stage would get,
  else to the first to join); what it carried drops where it fell, as for any builder. The same goes for a lead
  retrained to another job. Whoever takes the part over leads the Wonder from then on: the stone and the hall name them
  at once, and the old lead no longer counts on the crew (B467).

**What next?** names the Wonder closest to ready once the village is a Town with room for one: "The Great Cathedral can
be begun", or the first line it lacks ("The Great Cathedral is nearly in reach: Finished Chapel: 2 of 3").

**Goods and the dedication** (35.7): besides its blocks, each stage asks for goods from the village's jobs, the lines of
its file: the feast for the crew, their tools, what the rites need (candles, books, potions) and its treasures. A line
is in when the stockpile's chests and what was handed in at the stone hold its count together. A stage starts only
when every line is in; then, by day, it is **dedicated** (at night it waits for the morning): the bell rings at the
stone and at the village's bell, the goods are taken (exactly each line's count: what was handed in first, then from
the chests), the chronicle says "Stage 2, The Upper Story, of the Test Folly was dedicated: the village gathered at
the stone, the bell rang and the goods were given", the owner reads "Stage 2 of 3 of the Test Folly is dedicated: The
Upper Story begins", and the stage's parts go down for its lead. For the next 30 seconds the villagers off work
gather round the stone, the village's Bard plays a tune beside it ("playing at the dedication") and its guards stand
in a line three blocks out, on the side away from the Wonder ("standing in line at the dedication"), when it has them.

- **The stone's status** shows every line while the stage waits: "Before it begins, this stage needs its goods (in the
  chests within 8 blocks, or handed in here):", then "2 of 4 Flowers · Florist (no Florist in the village)", "0 of 2
  Potion of Healing · Cleric", and how to hand goods in; with every line in, "Every good is in: the stage is dedicated
  shortly" (by night "… in the morning"); once dedicated, "This stage's goods are dedicated".
- **Anyone can hand goods in**: right-click the stone holding them (up to what the line lacks: "Handed in 4 Flowers for
  the Test Folly: 4 of 4 in"; a full line says "The Test Folly has all the Flowers it needs"), or click the line on a
  Storehouse's board.
- **The requests board**: each line still short is posted for the stone (a request for a block instead of a villager):
  "8 × Bread", "For the Test Folly, 30 blocks away", "Made by the Chef"; a click hands over what you have of it.
- **Porters** carry matching goods from the storehouse (and what a worker's job doesn't keep from their chests; tools
  and potions only from the job that makes them) to the stockpile before anything else ("carrying goods to the Test
  Folly").
- **Crafters** (chefs, carpenters, masons, tinkerers, dyers) make a short line as a stock order for the stockpile, from
  their own chests and the storehouses near (not what those keep for their orders), before the storehouse's own orders.
- **The hall's daily quest** asks for the line furthest behind: "Bring goods for the village's Wonder", as many as it
  lacks, handed in at the hall into the stockpile.
- **Canceling** a Wonder hands the goods handed in for a stage not yet dedicated back to its owner (by the stone if
  they're away): "The 5 goods handed in for the Test Folly came back to you"; what lies in the stockpile's chests stays
  there, and a dedicated stage's goods were given.

**Found goods and rare workers** (35.7a, 35.7b, the owner's change): a Wonder is a month's work for a group, slow
progress towards a big goal with lots of little wins, and never "bring 640 bread". So each stage of the shipped Wonders
asks for two or three hard things **found by exploring**, one of each (a Heart of the Sea from buried treasure, an Echo
Shard from an ancient city, a Heavy Core from a trial chamber's ominous vault, a Nether Star from the Wither), and one
**rare good from an upgraded workshop**, two at most: from stage II on a Journeyman's luxury of M34's workshops
(Vintage Wine, Gold Circlets, Illuminated Books, Noble Robes), in stage I an Apprentice's (Berry Wine, Emerald
Brooches, Fine Clothes; the Observatory's spyglasses, the Stadium's Great Balls). Each found thing has a **helper**, a rare worker who finds it for the village:
a Legend (the Pathfinder, the Old Sage, the Seer, the Beastmaster, the Bard Laureate, the Grand Chef, the Merchant
Prince, the Golem Smith, the Founder; the Pokémon Ranger at the Stadium) or a Master Netherworker, Sifter or
Fisherman. A Wonder's own Legend is never one of its helpers.

- **The stone** says where each found thing is found and who would find it: "0 of 1 Dune Armor Trim Template · found
  in a desert temple's chests · faster with the Pathfinder (finds it in 9 days)"; with the helper in the village, "· the
  Pathfinder is searching: day 4 of 9"; once he has it, "1 of 1 Dune Armor Trim Template · … · brought by the
  Pathfinder". Things whose own name says too little are named in full ("Netherite Upgrade Template", "Music Disc: Lena
  Raine - Pigstep").
- **The search** goes on while the helper is in the village (looked at whenever the village is loaded, remembered while
  it isn't) and somebody is on the server: time in play, not time with the server empty. After the helper's days (a
  Minecraft day each 20 minutes of play) the line is in, nothing taken from the chests for it: the owner reads "The
  Pathfinder found what the Great Library needed: Dune Armor Trim Template" (with a chime), the chronicle says the same
  and the line's post comes down. Players who find it first just bring it; the search then waits.
- **The board** lists found lines like the rest, with "Found in a desert temple's chests" instead of the maker.
  Crafters never get found lines as stock orders, and the hall's daily quest leaves them out (a week's search isn't a
  day's quest); porters carry one from the storehouse like any good.
- **A save from before** (a Wonder waiting for its old goods, 128 bread and so on): what was handed in for a line that
  no longer takes it moves to a line that does, or else goes into the stockpile's chests (by the stone if they're full),
  and the chronicle says "The Great Library asked for new goods: 110 goods handed in before went back to its
  stockpile"; the stockpile's chests keep whatever they held.

**Pacing** (35.7b): `python3 tools/blueprints/wonder_goods.py` prints this table; the hours are hours of play for a
group of three or four with iron and diamond gear. Each found thing has an estimate of the hours the group spends
finding it, from setting out to coming home with it (1.5 for a shipwreck's Coast Armor Trim Template, 2.5 for a Heart
of the Sea, 4 for an Echo Shard, 5 for a Totem of Undying, 7 for a Heavy Core, 8 for a Nether Star, 10 for an Elytra).
A stage takes the hours of the finds no helper covers, one after another, plus 2.5 hours for the workshop goods and the
stage's materials and build that don't overlap the searching; a helper takes one and a half times the group's hours
(the days on the stone), in the background, so the group searches for the rest meanwhile, and the stage waits for the
slowest helper unless searching for that one itself is quicker. "Typical" is a Town with Masters of the Netherworker,
Sifter and Fisherman trades and one Legend, the Pathfinder (the commonest: born to Cartographers). The target is 45
hours: every other day for 3 hours, about 15 sessions, a month.

| Wonder | No helpers (I, II, III, IV) | Typical | Every helper | Helpers |
|---|---|---|---|---|
| Great Cathedral | 6.0, 13.0, 16.5, 24.0 = 59.5 | 46.0 | 30.3 | 8 |
| Lighthouse | 6.5, 12.0, 16.5, 22.0 = 57.0 | 43.2 | 31.0 | 9 |
| Great Library | 7.5, 12.5, 16.5, 17.5 = 54.0 | 46.2 | 30.0 | 7 |
| Observatory | 6.5, 14.5, 13.5, 22.5 = 57.0 | 41.5 | 30.7 | 7 |
| Great Wall | 6.0, 13.5, 13.5, 20.5 = 53.5 | 42.0 | 29.7 | 7 |
| Hanging Gardens | 8.0, 11.0, 14.0, 19.5 = 52.5 | 44.5 | 29.7 | 9 |
| Colossus | 6.5, 12.5, 15.5, 27.5 = 62.0 | 41.7 | 31.0 | 8 |
| Stadium | 7.0, 12.5, 13.5, 22.5 = 55.5 | 46.5 | 30.3 | 9 |

So a typical group takes 42 to 47 hours (mean 44, about 15 sessions); a village without any of the helpers about 56
(19 sessions), one with all of them about 30 (10 sessions). Each helper who arrives saves the group a few hours of one
stage, the little wins on the way; the estimates are reckoned, not yet timed in play (the owner's group is the test).

**A finished Wonder** (35.8). When the last stage is done:

- **Every player on the server is told**, wherever they are: "Pinevale has finished the Test Folly!"
- **The village holds a festival that evening** (the next evening if this one's festival is already over): the usual
  feast, music and dancing, and at dusk a bigger firework show: three rockets every two seconds, large gold balls with
  trails and stars, two from round the square and one from the Wonder's own top (under Curfew's "stay in", none).
- **The chronicles record it**: the village's ("The Test Folly was finished, its Foundation Stone laid on day 3") and
  every village linked to it, its realm's villages or, without a realm, its caravan partners ("Pinevale finished the
  Test Folly").
- **The Village Map marks it with a gold banner** at its middle, with its name (its parts aren't marked one by one).
- **Its powers come on**: those its file lists. Two kinds are made for server owners' own Wonders: `mood` (everyone in
  the village, or with `"scope": "realm"` the whole realm, is that much happier, "proud of the Test Folly" among their
  reasons; `home_amount` for its own village) and `beauty` (points added to the village's beauty, as a fountain's). The
  hall's card lists each power ("✔ Everyone in the village is 4 happier").

**Keeping it up.** Once a day each finished Wonder counts how much of it still stands, every block of every stage (at
most 4,096 blocks a tick, and only while all of it is loaded). A block stands when it's there as drawn; properties
that depend on neighbors don't count, and copper that weathered or was waxed is still the copper it was, so a dome
turned green still stands. The stone and the hall's card say "Standing: 97% of it (counted on day 12). Its powers are
on."

- **Under `wonderStanding` (90%) it is damaged**: its powers stop (the card lists them "(stopped)"), the stone and the
  card say "Damaged: 84% of it stands (90% needed). Its powers have stopped until the builders repair it.", the
  chronicle says so and its owner is told.
- **The village's builders repair it before anything else**: an idle builder of the village puts back every missing
  block, a repair per part, from the chests by its Foundation Stone (nothing players put there is touched), even with
  `builderRepairs` off: "Odo is repairing the Test Folly: 87 blocks to put back, from the chests by its Foundation
  Stone." Each part they finish counts it again.
- **Back at 90%, the powers return**: "The Test Folly of Pinevale stands again (91%): its powers have returned.", and
  the chronicle says it was repaired.

| Wonder | Rank | Legend (stand-in) | Other requirements | Powers |
|---|---|---|---|---|
| Great Cathedral | Town | the Seer (a Master Cleric) | 3 finished Chapels | Undertakers revive anywhere in the village for free; blessed weddings; beauty |
| Lighthouse | Town | the Merchant Prince (3 caravan routes) | on the coast | caravans reach twice as far in half the time; ferry fares halved; raid warnings |
| Great Library | Town | the Old Sage (a Master Scholar) | 20 research levels | a second research tier |
| Great Wall | Town | the Golem Smith (4 iron golems and a Master Tinkerer) | a finished Gatehouse | raids gather outside it; golem sentries on the walk |
| Observatory | Town | the Seer (a Master Cleric) | none | the Sky Chart; the Coming days page |
| Hanging Gardens | Town | the Grand Chef (a Master Chef, 8 kinds of meal) | none | a quarter more harvest across the realm; faster births |
| Colossus | City | the Founder (City rank) | none | more mood across the realm; more and better travelers; beauty |
| Stadium (with Cobblemon) | Town | the Pokémon Professor (25 pastured Pokémon of 10 types) | Cobblemon | Stadium Days with visiting trainers; hosts the Festival Cup |

Every Wonder also needs the Master Architect (stand-in: a Master builder).

**The Great Cathedral** (35.9) is drawn: a Gothic cathedral on a cross plan, 39 blocks across the transepts, 55 long,
its two west towers 44 high under stone spires reaching 52; about 14,250 blocks. A deep west door under three stepped
pointed arches, a rose window of white, light blue, yellow and red glass over it; a nave of five bays with flying
buttresses over the aisles and tall lancet windows; transepts with gable windows over their doors; a choir ending in a
rounded apse; open belfries with three bells. Inside: pews either side of a red runner, banners and lanterns on the
polished andesite piers, an altar of gold and candles, three side chapels with lecterns, and a sacristy with a brewing
stand. It goes up in four stages, each leaving the site tidy: I *The Foundations and the West Front*, II *The Nave*,
III *The Transepts and the Choir*, IV *The Towers and the Rose Window*. Its goods (found, with the rare worker who
finds each and in how many days; then what a workshop makes): I a Prize Pottery Sherd (a Master Sifter, 9 days), a
Ghast Tear (a Master Netherworker, 7 days); made: 2 Fine Clothes (Tailor); II the music disc otherside (the Bard
Laureate, 18 days), an Echo Shard (the Old Sage, 18 days), a Heart of the Sea (the Pathfinder, 11 days); made: 2
Illuminated Books (Printer); III a Totem of Undying (the Merchant Prince, 22 days), an Enchanted Golden Apple (the
Grand Chef, 22 days), an Eye Armor Trim Template (the Old Sage, 18 days); made: 2 Noble Robes (Tailor); IV an Elytra
(the Pathfinder, 45 days), a Nether Star (the Golem Smith, 36 days), the music disc Relic (the Bard Laureate, 16
days); made: 2 Gold Circlets (Jeweler).

**The Great Cathedral's powers** (35.10), while it is finished and stands (`wonderStanding`):

- **Undertakers** (`undertakers`): the village's Undertakers need no golden apple, healing potion or totem; they look
  for graves across the whole village (the hall's 64 blocks), not only 32 (48 with the Healers' Guild) round their
  table; and at the hall's first rounds of the morning (before 2,000 ticks into the day) everyone who died before that
  dawn and still lies in a grave of the village is brought back by the village's Undertaker in the night, credited to
  them, and the players near read "Uma brought Mira back in the night: by the Great Cathedral's grace, nobody lies in
  the graveyard past morning". The village needs an Undertaker at their table for either.
- **Weddings** (`weddings`): a wedding is held before its altar (the red runner's end, before the gold), before a
  Chapel or the bell, and is blessed: the players near read "The bells of the Great Cathedral ring out: Dara and Tom are
  wed at its altar, and the wedding is blessed. All of Testholm is 15 happier for 3 days", the chronicle "Dara and Tom
  were wed at the altar of the Great Cathedral, and the wedding was blessed"; every villager of the village is 15
  happier for 3 days ("a blessed wedding at the Great Cathedral" among their reasons); the couple's first baby comes the
  next day there's a free bed, past the store's meals, the mood and the daily wait (once); and their children, grown
  up, are twice as likely to be Gifted (M29's born gifts: 2 in 4 for a child of two schooled Masters).
- **Bells**: its belfries' bells (every bell of its blueprint still in place) ring for a wedding, a festival's feast and
  a raid beginning, as a player's hit rings them, so raiders near them glow.
- **Beauty** (`beauty`): 8 to the village's beauty.

**The Lighthouse** (35.11) is drawn: a coastal landmark 25 by 33 and 46 high, about 5,000 blocks, its back half in the
sea. A round tower banded in white and red terracotta tapers from 13 across to 9 on a stepped stone mole (mossy at the
waterline, steps up to the door, mooring posts round its foot, an arm along the back sheltering a little harbor). Its
gallery at 38 stands on stone corbels behind an iron railing; above it a lantern room of glass round sea lanterns, a
ring of redstone lamps that an inverted daylight detector lights at night, and a copper cupola with a lightning-rod
vane. Inside, a spiral stair of spruce climbs past five rooms from the door to the lantern. At its foot: the keeper's
cottage (a stone ground floor, an upper story of white terracotta in a spruce frame, a slate roof; a bed upstairs, the
fisherman's barrel by the stove), a spruce boathouse half over the water, a stone quay with mooring posts, and a
timber jetty out over the sea to a ferry stop, the Travel Post under a little roof. Its stages: I *The Mole and the
Quay*, II *The Tower*, III *The Gallery*, IV *The Lantern and the Keeper's House*. Its goods (found, with the rare
worker who finds each and in how many days; then what a workshop makes): I a Heart of the Sea (the Pathfinder, 11
days), a Coast Armor Trim Template (a Master Fisherman, 7 days); made: 2 Emerald Brooches (Jeweler); II a Trident
(the Beastmaster, 14 days), a Sponge (the Founder, 18 days), an Angler Pottery Sherd (the Grand Chef, 11 days); made:
2 Gold Circlets (Jeweler); III a Tide Armor Trim Template (a Master Fisherman, 22 days), a Conduit (the Seer, 18
days), a Totem of Undying (the Old Sage, 22 days); made: 2 Illuminated Books (Printer); IV a Nether Star (the Golem
Smith, 36 days), an Elytra (the Pathfinder, 45 days), a Ghast Tear (a Master Netherworker, 7 days); made: 2 Vintage
Wine (Vintner).

**The Lighthouse's powers** (35.12, `wonder/LighthousePowers`), while it is finished and stands (`wonderStanding`):

- **Farther caravans** (`caravan_range`, `factor` 2): caravans from the village reach villages twice the usual 2,048
  blocks, so up to 4,096 blocks away show on the Trade Routes page.
- **Quicker caravans** (`caravan_speed`, `factor` 0.5): a caravan from the village takes half the time on the road.
- **Cheaper ferries** (`ferry_fares`, `factor` 0.5): a fare to or from one of the village's Travel Posts is halved, at
  least an emerald; the Travel Post at the end of the jetty joins the network under the village's name ("Thornholm
  Lighthouse").
- **The keeper's watch** (`early_warning`): a source of the day's warning ([Raids and threats](raids.md), 32.14) for
  raiders who come from the sea (the drowned pirates, any culture arriving at the `shore`): the day before, everyone in
  the village is told and the chronicle has it, and at dusk on the day the guards gather on the shore.

Each factor is kept between 1/16 and 16. Damaged (or with Wonders off) all four are off; nothing of them is saved.

**The Great Library** (35.13) is drawn: a domed library on a cross plan, 41 by 41 and 34 high, about 15,000 blocks, on
a square terrace of smooth stone with a garden in each corner (azalea hedges, flowers, a bench and a lamp) behind a
diorite balustrade. A grand stair of three quartz steps climbs to a portico of six quartz columns (a wider bay before
the door) under a low pediment with a round window. Behind it the rotunda, 19 across, rises above four wings of
reading halls under deepslate-tile roofs: a drum of polished diorite with clerestory windows and a cornice, a shallow
copper dome with eight ribs (it weathers green), coffered inside in smooth stone and diorite, and a glass lantern on
top. Each wing has two stories of tall windows between smooth stone pilasters; inside, bookshelf stacks out from the
walls under a dark oak gallery on both sides (two floors of bookshelf galleries, a ladder up to each), carrels under
the windows, reading tables with lanterns and low bookcases down the middle, and a coffered dark oak ceiling. The
rotunda has three rings of bookshelves with two galleries round them; at its center an Enchanting Table ringed by
fifteen bookshelves, open towards the door, with the librarian's lectern before it and four reading desks round it. A
lectern for a scholar stands in each wing; the west wing's end is the map room, a carpet map of seas and islands round
a cartography table. Its stages: I *The Stacks*, II *The Galleries*, III *The Rotunda*, IV *The Dome and the Portico*.
Its goods (found, with the rare worker who finds each and in how many days; then what a workshop makes): I a Heart
Pottery Sherd (a Master Sifter, 14 days), a Dune Armor Trim Template (the Pathfinder, 9 days); made: an Illuminated
Book (Printer); II an Eye Armor Trim Template (the Seer, 18 days), the music disc Relic (the Bard Laureate, 16 days),
a Wild Armor Trim Template (the Pathfinder, 11 days); made: 2 Noble Robes (Tailor); III an Echo Shard (the Seer, 18
days), an Enchanted Golden Apple (the Grand Chef, 22 days), a Vex Armor Trim Template (the Merchant Prince, 22 days);
made: 2 Illuminated Books (Printer); IV an Ominous Trial Key (the Pathfinder, 14 days), a Nether Star (the Golem
Smith, 36 days), the music disc otherside (the Bard Laureate, 18 days); made: 2 Gold Circlets (Jeweler). It needs a
Town, 20 research levels and the Old Sage (or a Master Scholar while Legends are off).

**The Great Library's power** (35.14, `research_tier`), while it is finished and stands (`wonderStanding`): the
village's research opens a **second tier**, a row of its own on the scholar's screen (the row under the screen's
title, a bookshelf headed "The Great Library's research"; until the Library stands each of its topics reads "Needs the
Great Library" and a click says "Scholarship needs the Great Library"). Each level costs 24 paper, 6 books and 8
emeralds times its level (Swift Hands IV: 96 paper, 24 books, 32 emeralds) and takes twice the points of a first-tier
level:

- **Swift Hands IV–V** (after Swift Hands III): every job 5% faster a level, 25% at V;
- **Scholarship** (2 levels): scholars 25% faster a level (a pace source, "Scholarship"); at II research costs a
  quarter less (after a Scholars' Guild's quarter, rounded, at least one of what cost any);
- **Engineering** (2): a builder's bag holds 9 more stacks a level (36, then 45), and a Wonder's crew takes 2 more
  builders a level (no more than 32);
- **Cartography** (1): explorers search twice as far from their table, and the maps they draw can lead to trial
  chambers and ancient cities too (the tag `aliveworkplace:explorer_deep_maps`);
- **Trade Law** (2): caravans carry 2 more stacks a level (6, then 8), and the treasury takes 10% more a level of each
  day's takings;
- **Pharmacy** (1): the ill get well after a day instead of three, and each Nurse cures one villager a day with no
  remedy at hand.

Every speed bonus goes through the shared pace cap (`maxWorkPace`, twice the pace by default). A village keeps its
second-tier levels if the Library is damaged later, but their bonuses pause with its powers: Swift Hands counts to III,
the rest to nothing, bags take no new stacks past 27 (nothing in them is lost), a scholar on a second-tier level waits
("Scholarship waits for the Great Library to be repaired") and the row reads "Needs the Great Library" again, "the
levels are kept". Realm members get only the first tier's levels from their capital.

**The Observatory** (35.17) is drawn: a round tower 15 across and 30 high under a copper dome, on a raised terrace 31
by 31 (42 high to the dome's vane, about 8,400 blocks) of deepslate bricks behind a polished blackstone balustrade, a
wide stair up its front. The tower is deepslate bricks and tiles with polished blackstone bands at its floors, eight
blackstone pilasters up its height and tall windows; inside, a dark oak spiral stair climbs past the Seer's study
(bookshelves and a lectern), a store of chests and barrels and the instrument room (a cartography table, a lectern) to
the top, where a walk behind a blackstone parapet runs round the dome: the viewing platform. The dome, of cut copper
on a blackstone drum (it weathers green), has a slit of tinted glass three wide from its foot to its crown on the
courtyard side and a door onto the walk at the back; the great telescope, a cut copper barrel 12 long on a blackstone
mount, leans up and out of the slit over the courtyard. Two low wings under hipped deepslate-tile roofs flank the
tower: the star-chart room (a cartography table on a carpet chart of the night sky, a lectern, chart shelves) and the
Seer's lodging (a bed, a brewing stand, a barrel). The courtyard is paved in deepslate tiles ruled by blackstone, with
amethyst set in it: a compass star round the sundial (a round stone face and its gnomon), studs along the path and a
ring round the tower's foot. Opposite the sundial stands the orrery: a gold sun on a chain under a blackstone frame,
with five planets (iron, copper, lapis, redstone and raw gold) hung on chains round it. Its stages: I *The Terrace and
the Courtyard*, II *The Tower*, III *The Dome*, IV *The Great Telescope and the Orrery*. Its goods (found, with the
rare worker who finds each and in how many days; then what a workshop makes): I a Heart of the Sea (the Merchant
Prince, 11 days), a Ghast Tear (a Master Netherworker, 7 days); made: 2 spyglasses (Toolsmith); II an Echo Shard (the
Old Sage, 18 days), an Eye Armor Trim Template (the Pathfinder, 18 days), the music disc Precipice (the Bard Laureate,
18 days); made: 2 Illuminated Books (Printer); III an Ominous Trial Key (the Golem Smith, 14 days), a Totem of Undying
(the Merchant Prince, 22 days), a Wither Skeleton Skull (a Master Netherworker, 14 days); made: 2 Gold Circlets
(Jeweler); IV a Nether Star (the Golem Smith, 36 days), an Elytra (the Pathfinder, 45 days), a Shulker Shell (the
Beastmaster, 9 days); made: 2 Noble Robes (Tailor). It needs a Town and the Seer (or a Master Cleric while Legends are
off). Its powers, the Sky Chart and the Coming days page, are below (35.18). The Sky Chart covers about 6,144 blocks
across (384 chunks, 12 regions; 35.18a): one locked map at 48 blocks a pixel, since a vanilla map shows at most 2,048.
Its search and the chart itself are built (`wonder/SkyChart`): one region of one structure kind every 5 ticks while
the server's tick has room, at most one kind an in-game hour, saved as it goes; the hall's map button hands it over
(35.18).

**The Great Wall** (35.15) is drawn: a defense 97 long, placed like the other walls (stand in the village and place it
facing out: its stairs are on your side), about 12,300 blocks. A curtain 7 thick and 12 high on a battered plinth of
deepslate bricks, faced with stone bricks to the field (mossy and cracked on the low courses, a buttress every four or
five blocks) and cobblestone to the village; on top a walk paved six wide behind a breastwork corbeled out over the
field, crenellated, with a low parapet on the village side carrying braziers (campfires on chiseled stone). Four towers
9 by 9 and 18 high under hipped spruce roofs, one at each end and two at the quarters. Since 35.15a each tower is solid
stone from the ground to the walk, on a solid base a block wider all round (deepslate bricks under a battered step, the
plinth stepping out round it again), with buttresses up its corners, a ledge of upside-down stairs round it at the walk
with lanterns on its corners, a banner over a blind slit to the field and a lit niche under a keystone and a banner to
the village. You enter a tower from the walk: a room at the walk (doors onto the walk, a guard's grindstone, and on the
side facing the gate an alcove 3 wide and 3 high open to the walk, where a sentry golem stands), a stair on up to a top
room with windows on three sides, a banner to the field and a watch lantern on the roof's peak. Along the runs a banner
hangs in every bay on the field side under an arrow slit, taller chiseled merlons stand over the buttresses, vines grow
on the low courses, lanterns stand on the parapet between the braziers and on corbels on the village side. At the center the Great Gate, a gatehouse 17 wide and as high
as the towers under a long hipped roof: a vaulted gateway 5 wide with spruce fence gates two high across it under a
portcullis of iron bars, which runs up a slot into the hall and hangs from a windlass; a guardroom either side with its
own stair, a hall at the walk, a top room opening onto a crenellated bretèche over the gate, buttresses either side of
the gateway and at its corners, the towers' ledge at the walk, braziers at both its mouths and banners beside them. Two stairs of ten steps climb the village side to
landings by the gatehouse. Its stages: I *The Great Gate*, II *The West Run*, III *The East Run*, IV *The Walk and the
Towers' Crowns* (the walk, breastwork and braziers, the towers' top rooms and roofs). Its goods (found, with the rare worker who finds each and in how many days; then what a workshop makes): I a Sentry
Armor Trim Template (the Beastmaster, 9 days), a Ghast Tear (a Master Netherworker, 7 days); made: 2 Emerald Brooches
(Jeweler); II a Totem of Undying (the Merchant Prince, 22 days), an Ominous Trial Key (the Pathfinder, 14 days), a
Rib Armor Trim Template (the Seer, 14 days); made: 2 Vintage Wine (Vintner); III a Wither Skeleton Skull (the Founder,
14 days), a Netherite Upgrade Template (the Beastmaster, 14 days), a Vex Armor Trim Template (the Merchant Prince, 22
days); made: 2 Gold Circlets (Jeweler); IV a Heavy Core (the Pathfinder, 32 days), a Nether Star (the Founder, 36
days), a Netherite Ingot (the Old Sage, 14 days); made: 2 Noble Robes (Tailor). It needs a Town, a finished Gatehouse
and the Golem Smith (or 4 iron golems and a Master Tinkerer while Legends are off).

**The Great Wall's powers** (35.16), while it is finished and stands (`wonderStanding`):

- **No raid gathers behind it** (`raid_barrier`, `margin_degrees` 10). Seen from the village's hall the Wall covers an
  arc of the compass (from the bearings of its footprint's corners), and 10 degrees more either side. Our night raids,
  the bandits' (who gather toward their camp) and every other culture's that gathers by the village's edge pick a
  bearing outside it: drawn evenly over the open part of the side they come from, or the open bearing nearest it when
  the Wall covers all of that side (a camp right behind the Wall). The raiders gather 0.6 of the village's radius out,
  as ever. A siege's ladders never go up against it, and its Great Gate is no siege's breach: the rams only go for the
  village's own gate builds.
- **Sentries** (`sentries`, `count` 4, `range` 16, `posts`): four Wall Sentries, the Golem Smith's (twice a golem's
  health, their blows throwing foes back), come with the finished Wall and stand in the towers' alcoves on the walk. A
  sentry fights any foe within 16 blocks of the wall as a golem does, and once none is left goes back to its alcove;
  while foes are about further out (within 80 blocks), it steps out onto the walk and watches them. Pushed or knocked
  off the Wall it walks back up, and where there is no way up it climbs the wall's face. A sentry lost is replaced the
  next morning (the hall's round before 2000 ticks of the day) by the village's Golem Smith, or a Tinkerer standing in,
  from 4 iron blocks and a pumpkin (or a carved one) in the stockpile, the chests round the Foundation Stone; the
  chronicle says "Tibbs made a new sentry for the Great Wall from four iron blocks and a pumpkin". Without the iron,
  or nobody to make it, nobody comes.
- **The Great Gate** shuts at nightfall and opens in the morning with the village's gates (guards or none), and stays
  shut through a siege.

Damaged (or with Wonders off) both powers are down: raids gather anywhere, the sentries are plain golems that keep
their marks for when it's repaired, and nobody is replaced.

**The Observatory's powers** (35.18), while it is finished and stands:

- **The Sky Chart** (`sky_chart`, `size` 6,144). The hall searches the land round the village, about 6,144 blocks
  across (384 chunks, 12 regions a side), for villages, pillager outposts, desert and jungle temples, witch huts,
  igloos, ocean monuments, woodland mansions, shipwrecks, ruined portals, trail ruins, ancient cities, trial chambers
  and strongholds: one kind an in-game hour, only while the server's tick has room, and again each week (what it found
  stays on the chart until its region is searched again). The hall's map button then also draws, on a second empty
  map, the Sky Chart: the land in an explorer map's colors with a marker on every structure found (vanilla's own for
  villages, mansions, monuments, jungle temples, witch huts and trial chambers, a red X for the rest) and a red marker
  on each bandit camp. Its tooltip names the twelve nearest ("Plains Village: north-east, about 1,000 blocks") and
  counts the rest.
- **The Coming days** (`forecast`): the hall's calendar page shows, under the seasons, what's ahead and when: the next
  festival and market day, weddings due, caravans on the road and when they arrive, a traveler expected at the inn,
  the full moon, the Legends the Seer foretells, the bandit camps and where, and the night's raid risk (or, after the
  Seer's dawn reading, whether a raid is foretold). The ones with a place say where.

Damaged (or with Wonders off) both are paused: the search waits, the map button draws only the village map and the
calendar has no Coming days; what was found stays in the hall.

**The Hanging Gardens** (35.19) are drawn: a stepped garden mountain on a 39 by 39 foot, 31 high to its treetops,
about 16,200 blocks. Four terraces 6 high, each 4 in from the one below (39, 31, 23 and 15 across), each faced all
round with an arcade of 3-wide arches on cut sandstone columns with chiseled capitals, under a terracotta band and a
cut sandstone coping; behind each arcade a gallery runs round the terrace under its garden, lit by glow berries
hanging in the arch heads and lanterns. Inside the first terrace are the Cistern Vaults: a hall 29 by 29 of 36 columns
round a pool with a chain pump, a doorway from the gallery on each side. Each ledge is a garden on the gallery's roof:
a hedge of azalea and oak leaves on the coping, fields of wheat, carrots and beetroots between water channels, sweet
berry beds, glow berry arbors, flower beds with azaleas, beehives, and on the corners oaks, flowering azalea trees and
a farm corner with a composter and a chest on each level (four village farms). Vines hang down the columns and over
the arches. A ramp spirals up, a stair with a balustrade on each side in turn: along the front from the ground, up the
second terrace's east face, the third's back and the crown's west face. On the crown a cistern with a fountain pours
down a channel to the east edge: the falls drop from a basin on each ledge to the next and into a pool with lily pads
at the foot, past a blind, mossy stretch of arcade. Its stages: I *The First Terrace and the Cistern Vaults*, II *The
Second Terrace*, III *The Third Terrace*, IV *The Crown Garden and the Falls* (the crown, its garden and cistern, the
last ramp and the water, poured from buckets). Its goods (found, with the rare worker who finds each and in how many
days; then what a workshop makes): I a Sniffer Egg (the Beastmaster, 14 days), a Wild Armor Trim Template (the
Pathfinder, 11 days); made: 2 Berry Wine (Vintner); II a Pitcher Pod (the Beastmaster, 18 days), Torchflower Seeds
(the Old Sage, 7 days), a Heart Pottery Sherd (a Master Sifter, 14 days); made: 2 Vintage Wine (Vintner); III an
Enchanted Golden Apple (the Seer, 22 days), a Heart of the Sea (the Pathfinder, 11 days), a Sponge (the Bard Laureate,
18 days); made: 2 Gold Circlets (Jeweler); IV a Totem of Undying (the Merchant Prince, 22 days), a Nether Star (the
Golem Smith, 36 days), a Conduit (the Founder, 18 days); made: 2 Noble Robes (Tailor). It needs a Town and the Grand
Chef (or a Master Chef and 8 kinds of meal in the store while Legends are off). The crown has a field of its own by
its farm corner (wheat and carrots either side of a channel, 35.20), so all four farm corners have a field in a
farmer's reach.

**The Hanging Gardens' powers** (35.20), while they are finished and stand (`wonderStanding`):

- **Harvest** (`harvest`, across the realm): every farmer working a field (a village farmer by their composter, or a
  player's marked field) and every Orchard Keeper in the Gardens' village, and in the villages linked to it (its
  realm's, M33; with realms off, a village whose caravans run to the Gardens' village), brings in one more of each crop
  for every four they pick: 40 wheat cut are 50 in the chest. It is counted per worker and per kind, so two farmers'
  two wheat each are no extra one. Crops grow no faster. The hall's Wonders page reads "Farmers and orchard keepers
  across the realm bring in a quarter more: one more of each crop for every four they pick".
- **Births** (`births`, the Gardens' own village): a baby is on the way once the store holds 12 meals instead of 16,
  and up to two are born a day when there are free beds (half a day between them). The page reads "The village grows
  faster: a baby needs 12 meals in the store, not 16, and up to two are born a day".
- **Its terraces are four village farms**: a farmer whose composter is a farm corner's takes on that terrace's fields
  by themselves, as at any composter with a chest by it.

**The Colossus** (35.21) is drawn: the Founder's statue, 47 high on a 29 by 29 site, about 9,000 blocks, standing over
the village's main road. It is a villager drawn a pixel to a block from the game's own model, so it reads as one from
far off: the tall head (9 across, 10 high), the long nose hanging past the chin, a dark unibrow over white eyes with
emerald pupils, and a robe of polished diorite trimmed with smooth stone at the hem, the belt and down the front,
flaring at the hem over two feet. Its left arm folds across the chest, the hand resting on it; its right arm rises
clear of the head and holds up the lantern, a cage of glass panes round glowstone under a copper cap, lit day and
night. A cloak of cut copper (about 370 blocks with the lantern's copper) hangs from the shoulders to the pedestal in
three pleats, and weathers green over the weeks. The plinth is 13 high in three steps of stone bricks (mossy and
cracked low down) on a skirt of deepslate brick: polished andesite pilasters and a cornice, a terrace with a
balustrade and nine braziers round it, and a smooth stone pedestal. The road passes front to back through an arch 5
wide and 6 high, with lantern niches down the passage and lamp posts at both mouths; over the front arch is the
Founder's plaque, a row of seven dark oak signs on a dark oak panel (35.22 writes them). The arm and the head need
scaffolding (35.6). Its stages: I *The Plinth and the Road Arch*, II *The Robes*, III *The Arms and the Cloak*, IV
*The Head and the Lantern*. Its goods (found, with the rare worker who finds each and in how many days; then what a
workshop makes): I a Heart of the Sea (the Pathfinder, 11 days), a Coast Armor Trim Template (the Merchant Prince, 7
days); made: 2 Emerald Brooches (Jeweler); II a Trident (the Beastmaster, 14 days), a Sponge (the Seer, 18 days), a
Netherite Upgrade Template (a Master Netherworker, 14 days); made: 2 Gold Circlets (Jeweler); III a Tide Armor Trim
Template (a Master Fisherman, 22 days), a Totem of Undying (the Merchant Prince, 22 days), a Wither Skeleton Skull
(the Old Sage, 14 days); made: 2 Noble Robes (Tailor); IV a Heavy Core (the Golem Smith, 32 days), a Nether Star (the
Beastmaster, 36 days), an Elytra (the Pathfinder, 45 days); made: 2 Vintage Wine (Vintner). It needs a City and the
Founder (or City rank while Legends are off).

**The Colossus's powers** (35.22), while it is finished and stands (`wonderStanding`):

- **Mood** (`mood`, `scope: "realm"`, `amount` 8, `home_amount` 12): every grown villager of its own village is 12
  happier ("proud of the Colossus" among their reasons) and every villager of the realm's other villages 8 ("the
  realm's Colossus"). With realms off, or a village in no realm, "the realm" is the village's caravan partners: a
  village with a trade route either way (or a sister route) with it.
- **Travelers** (`travellers`, `factor` 2, `expert_factor` 2): its village's inns take twice as many travelers a morning
  (two instead of one; four under Open Gates) and have room for twice as many at once (four instead of two; eight
  under Open Gates), so they really come twice as often; and each is twice as likely to be an Expert (a Journeyman
  becomes one with chance 0.05 a level of the innkeeper, now 0.10). The Old Sage's Old Tongues still makes them at
  least Journeymen.
- **Beauty** (`beauty`): 8 to the village's beauty.
- **The plaque**: once it is finished, its seven signs over the front arch are written in glowing yellow letters,
  waxed so nobody edits them, read left to right from the road: "Raised in / honor of", "the Founder / *Aldric*",
  "who founded", "*Thornholm*", "on day 3", "Finished by / its people / on day 41", "May it stand / a thousand /
  years". The name is the village's settled Founder (M29); without one, the hall owner's; with neither it is left
  out. The day is the chronicle's first "founded" line (a colony the village sent out doesn't count); a chronicle that
  has forgotten it reads "long ago". A sign broken and put back is written again on the next round (every 5 seconds),
  with the names as they are then.

**The Stadium** (35.23, only with Cobblemon; made bigger, with more dressing and walk-through interiors, by 35.23a) is
drawn: an oval arena 53 by 43, its bowl 18 high (24 to the gate's banners, about 12,100 blocks), round a grass battle
field 31 by 21 ruled in white (its edge, the
half-way line, a center circle; no logos) inside a smooth stone apron and a stone barrier. The stands step up two at a
time, seven tiers along the long sides and five at the ends, climbed by zig-zag aisles of stone brick stairs, with red and white carpet seats in wedges round the bowl;
in a village with a Village Banner the red and the white are built in its banner's two colors instead (the base and
the first other color of its pattern; the field's white lines stay white). A trainer's box is cut into each end, a
stone room under a spruce roof with a Training Post facing the field, and the professor's box stands raised in the
middle of the south stand, a lectern behind its rail. The west end is the covered stand, under a lean-to canopy of
spruce slabs on four spruce columns; over the east end stands the scoreboard, black terracotta in a stone frame under
a red header, showing 2 - 1 on both faces, to the stands and to the road outside. Outside, the back wall has pilasters (buttresses on the straight sides) and bays that take
turns as open, lit entrances (a banner over each) and barred windows with flower beds at their foot, two bands of red
and white terracotta and a parapet with lanterns and banners. Inside it, under the stands, everything is walkable and
lit, three high: a concourse two wide right round; eight vomitories climbing as stairs from it into the front rows; on
the north the first-aid room by the nurse's booth (cots, a red-cross banner), two food kiosks serving onto the concourse
through a hatch, and a trophy room; on the south the players' tunnel from the concourse to the pitch under the
professor's box, with the home and away dressing rooms off it (lockers, a bench, their banner), the referees' room and
the groundsman's store; doors from the trainer boxes onto the concourse; two covered dugouts on the apron. The gateway on the north side is a
tunnel through the stand under two piers and a lintel banded red and white, with standing banners on the piers and
banners hung over the stand. Four floodlight towers stand at the corners, each an iron head with eight redstone lamps
wired round a daylight detector that lights them at night. In the corners outside are the nurse's booth (a striped
stall with a Healing Machine and a brewing stand) and seven market pitches, three with stalls on them. Its stages: I
*The Field*, II *The Lower Stands*, III *The Upper Stands and the Covered Stand*, IV *The Gate and the Floodlights*.
Its goods (found, with the rare worker who finds each and in how many days; then what a workshop makes): I Diamond
Horse Armor (the Beastmaster, 14 days), a Coast Armor Trim Template (the Merchant Prince, 7 days); made: 2 Great Balls
(Ball Smith); II a Trident (a Master Fisherman, 14 days), the music disc Pigstep (the Bard Laureate, 18 days), a
Netherite Upgrade Template (a Master Netherworker, 14 days); made: 2 Gold Circlets (Jeweler); III a Totem of Undying
(the Merchant Prince, 22 days), an Ominous Trial Key (the Pathfinder, 14 days), a Flow Armor Trim Template (the Golem
Smith, 14 days); made: 2 Ultra Balls (Ball Smith); IV a Nether Star (the Golem Smith, 36 days), a Heavy Core (the
Pokémon Ranger, 32 days), an Enchanted Golden Apple (the Grand Chef, 22 days); made: 2 Noble Robes (Tailor). It needs
a Town and the Pokémon Professor (or 25 pastured Pokémon of 10 types while Legends are off). Without Cobblemon its
file isn't loaded, so it isn't on the Wonders page. M28's Arena is a smaller ring of its own, not a Stadium, so this
one keeps the name Stadium.

**The Stadium's powers** (35.24), while it is finished and stands (`wonderStanding`):

- **Stadium Days** (`stadium_days`, `every_days` 2, `min_visitors` 2, `max_visitors` 4): on every second day (as the
  chronicle counts them), from noon (6000) until dusk (12000), two to four visiting trainers (the day's random, fixed by
  the site and the day) stand in the two trainer boxes, two to a box at most, facing the field. Each realm village
  (with realms off, each caravan partner, a route either way) sends the trainer it would send to a Cup
  (`Cups.delegate`: its Leader, else its best Trainer), named "(name) of (village)"; the rest are traveling trainers
  of Journeyman to Master rank, "the Traveler". They are Trainers to a player: right-click to battle, a win pays the
  rank's prize (CobbleDollars, or emeralds), once a day each (`trainer/Trainers`). From 7000, one bout every 1,000
  ticks, the village's own trainers (the highest rank first, in turn) take the visitors on: the Cup's exhibition
  (`CupBout`) played out at once with each side's own team at its rank, its random fixed by the village, the day and
  the two; with no Pokémon on either side the higher rank wins. Both get battle XP (5, and 3 more for the winner, a
  quarter more with the Trainers' Guild), the visitor's trainer at home too (banked on its village's caravans' entry
  when that village isn't loaded); the result goes to the players at the Stadium or in the village and into the
  chronicle ("At the Stadium, (winner) beat (loser)"). The villagers off work walk to the seats (the carpets on the
  tiers) and sit down (as on a Cup day the afternoon is a holiday, so those at work go too; not those resting), and those who watched are 5 happier ("watched a Stadium Day") until the next Stadium Day. At
  dusk the visitors leave: gone where no player is within 32 blocks, else walking out of the gate. A visitor never
  takes a job or a bed. The visitors come once a day, a restart in the afternoon too. A Stadium damaged during a
  Stadium Day (or Wonders switched off) sends its visitors off at once and holds no more bouts; repaired the same
  afternoon, no second set comes that day.
- **The tournament** (`tournament_host`): M28's Festival Cup exists, so it is wired. `Arenas.find` gives the village's
  Stadium first, as an Arena of tier 3: its field's center the ring, its two boxes, its seats, the road in front of
  the gate for the fair and the notice board (no champion's pole). So a Town with a Stadium and no Arena can host a
  Cup, and its village gets the fair and the crowds. A host without a Stadium whose circuit has a Stadium host holds
  no Cup of its own (unless its sign-up has closed), and `Cups.hostFor` gives the nearest Stadium host first: the
  Cup is held at the nearest Stadium among the villages taking part.
- No badges and no gym leaders: visitors are Trainers (a visiting Leader stands as a Trainer of its rank), and battles
  pay money only.

## How it works

**A Wonder is a data file** (35.2), `data/<namespace>/wonders/<id>.json`, read whenever data packs load, so a server
owner can add their own Wonder with a data pack and it works like ours. The file holds:

- `name` and `description`: lang keys. `icon`: any item.
- `requirements`: a list of conditions (below).
- `stages`: each a `name`, its blueprint `parts` (a structure id and its `offset` from the Wonder's corner), and its
  `goods`, each `{"item"` or `"tag", "count", "job"}`, with an optional `potion` to name a kind of potion. A found line
  (35.7a) has `"found": true` (its `job` may be left out: the Cartographer, the village's explorers) and an optional
  `"helper": {"days": 1 to 60, "condition": {...}}`, any condition (a `legend`, a `job_level`) for the rare worker who
  finds it; where it's found is the lang key `goods.aliveworkplace.hint.<namespace>.<item>` ("by exploring" without one).
- `powers`: `[{"type": ..., numbers}]`. Each type is code registered by its id (35.8, `wonder/powers/`): `mood`
  (`scope` `village` or `realm`, `amount`, optional `home_amount`; each -50 to 50) and `beauty` (`points`, 0 to 50) work
  now; the other types our Wonders name are declared (their numbers kept as written) until their Wonder's item fills
  them in. An unknown type, or numbers its reader refuses, fail the file.
- `crew` (default 12, at most 32) and `enabled` (false: the file is skipped). `fabric:load_conditions` are honored,
  so a Wonder can need a mod (the Stadium will need Cobblemon).

A file with a mistake (an unknown condition or power type, a missing field, a count of 0, an unknown item, job or
potion, a stage without parts) is skipped and the log says which file and why; the other files still load.

**Requirements** use the same condition toolbox as Legends (`rank_at_least`, `research_levels`, `finished`,
`villagers`, `job_level`, `caravan_routes`, `iron_golems`, `meal_kinds`, `pastured_pokemon`, …), plus two types new
in 35.2:

- `coastal`: an ocean or beach within 48 blocks of the hall. It is the same coast check the drowned pirates use, looked
  at once a day per hall, so both agree on what a coast is.
- `mod_loaded` (`mod`): that mod is installed.

`legend` gained an optional `stand_in`, a list of conditions: while Legends are off, `wonderLegends` is off, or that
Legend's file isn't loaded, the stand-in list must hold instead (all of it). A `legend` without one reads as before.
`pastured_pokemon` counts through the Cobblemon census; without Cobblemon it is always false.

**Every Wonder needs the Master Architect** to draw up its plans. The mod adds this line to every Wonder itself (with a
Master builder as the stand-in), so no file can leave it out.

**The caps** (checked before the requirements):

- a village may hold `wonderCaps` Wonders by rank: Hamlet 0, Village 0, Town 1, City 2 (finished or going up);
- one of each kind per village;
- one Wonder going up per village at a time;
- `wonderServerLimit` villages on the server may each build the same kind (0: no limit);
- a Wonder in `disabledWonders` can't be started.

Each refusal says why ("Wonders: 1 of 1 for a Town.", "This village already has the Great Cathedral.").

**A Wonder's blueprints** (35.3): each stage's parts are structure files, `structure/wonder/<id>/stage_<n>_<part>.nbt`,
each at most 48 a side and only what that stage adds to the ones before (stage I also clears the site). The whole
Wonder, `<namespace>:wonder/<id>`, is those parts laid at their offsets, merged once and kept until data packs load
again; it is what the plan shows. Nothing under `wonder/` is ever on the Blueprint Table. A Wonder whose part is
missing can't have its plans drawn up (the log names the part). For pack makers drawing their own: the blueprint kit's
`wonder_stages()` (`tools/blueprints/kit.py`) takes the Wonder drawn one stage at a time and writes the parts and their
offsets; `tools/blueprints/wonders.py` draws the test folly with it.

Each Wonder in progress or finished is recorded per dimension, with its village's hall, the player who started it, its
stage and the days it was begun and finished.

**Stages as build sites** (35.4): each stage's parts become ordinary build sites, placed with the Wonder's turn and
mirror at their offsets, pointing back at the Wonder, with the Foundation Stone as their bench (so a builder fetches from
the chests round the stone and empties his bag there). Only stage I fills a foundation (later stages stand on the stage
below), only the last stage with a part on the Wonder's floor levels the ground round it, with those parts alone (35.8a:
the folly's dome, leveled round its own bottom, once filled a band of dirt round the tower under it; the folly now
levels in stage I), and no part hands a blueprint back. Once a second the Wonders
check their stage: when every part's site is done or gone, the next stage's sites are made for the Wonder's builder, or
the Wonder is finished. The check reads only saved data, so the order holds through a restart at any tick. While
`wonders` is off nothing moves on; it all waits, saved.

**Our Wonders' designs** (35.9 on) are drawn whole in `tools/blueprints/wonders.py`, one function each, stage by stage
on a staged build that refuses to draw over an earlier stage's block; `wonder_stages()` cuts each stage into parts and
`wonders.py` writes the Wonder's data file (`aliveworkplace:great_cathedral` is
`data/aliveworkplace/wonders/great_cathedral.json`, `aliveworkplace:lighthouse` is `.../lighthouse.json`, `aliveworkplace:great_library` is `.../great_library.json`, `aliveworkplace:observatory` is `.../observatory.json`, `aliveworkplace:great_wall` is `.../great_wall.json`, `aliveworkplace:hanging_gardens` is `.../hanging_gardens.json`, `aliveworkplace:colossus` is `.../colossus.json`) with those parts, so the two always agree. Each stage is checked
as it will stand, on the stages before it (nothing held up by nothing), and every stage leaves the site tidy, since the
village lives beside it for days.

**Powers and upkeep** (35.8): the rest of the mod asks `Wonders.power(level, hall, type)` (the first) or
`Wonders.powers(level, hall, type)` (every Wonder that grants it, its own village's first, then its realm's): a lookup
in a cache worked out again whenever a Wonder is finished, damaged, repaired, forgotten or loaded, and when the files
are read again. It answers only for finished Wonders that aren't damaged, and nothing while `wonders` is off. A realm
power reaches the village's realm (or its caravan partners). A Wonder's item adds its type with
`WonderPowers.register(type, reader)` and reads the numbers where the power acts. The upkeep count goes one dimension
at a time, a Wonder at a time, 4,096 blocks a tick, and starts over if part of it unloads; it isn't saved, so a
restart only begins that day's count again. Repairs are ordinary repair build sites, one per part with holes, pointing
back at the Wonder with its stone as their bench; the first goes to the builder that looked, the rest in its queue.

**The Cathedral's power types** (35.10, `wonder/powers/`): `undertakers` takes `search` (`"village"`, the default, or
`"table"`) and `revive_by` (`"morning"`, the default, or `"none"`); `weddings` takes `mood` (default 15, at most 50
either way), `days` (default 3), `gifted_factor` (1 to 4, default 1; rounded, it is how many of the gift die's 4 faces
make a child Gifted), `altar` (a spot `[x, y, z]` in the Wonder's own blueprint, turned and mirrored with it; without
one a wedding anywhere in the village is blessed) and `bells` (default true). A server owner's Wonder can use them.
`Couples.venue` asks `WeddingsPower.venue` first; `UndertakerWork` asks `UndertakersPower` for the graves and whether
anything is needed; `VillageGrowth.blessedBirth` takes a Seer-blessed couple first, then a Wonder-blessed one; the
hall's round calls `UndertakersPower.morning` after the couples; `Festivals.feast` and `VillageRaids` ring
`WonderBells`, which finds a Wonder's bells once in its blueprint.

**The Colossus's** (35.22): `travellers` (`wonder/powers/TravellersPower`) takes `factor` and `expert_factor` (each 1
to 8, default 1); `Innkeepers.maxGuests` and `Innkeepers.arrivalsPerMorning` multiply the usual (or Open Gates') numbers by
`factor`, rounded, and `Innkeepers.newcomerLevel` multiplies the Expert chance by `expert_factor` (at most certain). A
server owner's Wonder can use it. `mood` and `beauty` are the generic types (35.8). The plaque is
`wonder/ColossusPlaque`, for the Colossus only: it finds the row of wall signs in the Wonder's blueprint once
(the facing and height of the first; left to right for a reader), writes them from `WonderCelebration.finished` and
again on a level tick every 100 ticks for any sign standing blank and unwaxed; a row of other than seven puts the
middle lines on the middle signs. `Wonders.linked` takes the caravan partners both ways (`Caravans.Data.partners`),
so a realm power reaches a village that sends caravans to the Wonder's as well as one it sends caravans to.

**The Gardens' power types** (35.20, `wonder/GardensPowers`, declared types read where they act, like the
Lighthouse's): `harvest` takes `extra_every` (default 4, 1 to 64) and `scope` (`"realm"` in the Gardens' file);
`births` takes `meals` (default 12) and `per_day` (default 2, at most 8). `FieldWork` (a harvest, not a weed cleared
while tilling) and `OrchardWork` add `GardensPowers.harvest`'s extra items to what was picked, into the worker's bag
and so the chests; the village is the hall nearest the worker's job site. `VillageGrowth.foodNeeded` uses `meals` as
the usual figure (an edict naming its own still wins) and `VillageGrowth.every` takes the larger of the edicts' and
`per_day` babies a day, a Cradle's one more on top.

**The Stadium's** (35.24): `stadium_days` (`wonder/powers/StadiumDaysPower`: `every_days` 1 to 28, `min_visitors` and
`max_visitors` 1 to 4, the least first) and `tournament_host` (`TournamentHostPower`, no numbers). `wonder/StadiumDays`
runs the days on a level tick every 20 ticks. The boxes, the field and the seats come from the Wonder's own drawing,
placed where it stands (its two Training Posts, the field's center between them, a spot before each post and one
beside that, every carpet a seat), so a server owner's Wonder with the power and two Training Posts works the same.
Saved: each site's last Stadium Day (`aliveworkplace_stadium_days`) and each visitor's record (the `stadium_visitor`
attachment); a villager's last watched day is `stadium_watched`. The bouts' teams come from the `CupBattlers` extension
(the trainer's own team at its rank; Cobblemon fills it).

**The Great Wall's** (35.16): `raid_barrier` (`wonder/powers/RaidBarrierPower`) takes `margin_degrees` (0 to 45,
default 10); `wonder/GreatWallPowers` works out each site's footprint once (every stage's parts, turned with it) and
the arc from the hall, and `guard/VillageRaids.gatheringPoint` asks it for each try's bearing (`GreatWallPowers.bearing`,
with the raid's own random; without a Wall the very same draw as before). `threat/Ladders` refuses a ladder against
any block in its footprint (`GreatWallPowers.shields`). `sentries` (`wonder/powers/SentriesPower`) takes `posts` (each
[x, y, z] in the Wonder's own blueprint), `count` (at most the posts, default all) and `range` (4 to 48, default 16).
`wonder/GreatWallSentries` raises a sentry with `GolemSmith.build` (role `sentry`), tags it `aliveworkplace_wall_sentry`
and gives it its post (`golem_post`), so nothing new is saved; its duty is `GreatWallSentries.WatchGoal`, ranked above
the golem's own goals (`IronGolemRolesMixin`), and the Patrol Map's `WallSentries.HoldGoal` leaves it alone. The
"next morning" is `GreatWallSentries.morning`, in the hall's round; the first four come from
`WonderCelebration.finished`. The Wall's sentries don't count toward the Golem Smith's own golems. `guard/Gates` shuts
the fence gates of every finished Wonder with `raid_barrier` (`GreatWallPowers.shutGates`).

**The Observatory's** (35.18): `sky_chart` (`wonder/powers/SkyChartPower`) takes `size` (512 to 12,288, default
6,144); `forecast` (`wonder/powers/ForecastPower`) has no numbers. `wonder/ObservatoryPowers.tick` runs from the hall's
block entity tick every 5 ticks: with the power, a new week (`(day - 1) / 7`) restarts the hall's
`SkyChart.Search`, and `Search.tick` takes one region of one structure set while `SkyChart.tickHasRoom` (the average
tick under 40 ms), starting at most one set an in-game hour, with `SkyChart.VANILLA` (the placement grid and the
structure check, no chunk loaded; tests put a stub in `ObservatoryPowers.finder`). The map button calls
`ObservatoryPowers.draw` after `VillageMaps.draw`: `SkyChart.chart` plus the bandit camps (`MapDecorationTypes.RED_MARKER`)
and the tooltip lines. `ObservatoryPowers.comingDays` reads `Festivals.nextDay`, `MarketDays.nextDay`, each unmarried
couple's `Partner.since` plus `Couples.COURTSHIP_DAYS`, `Caravans.Data.onTheRoad` (to or from the hall), an innkeeper
in the village (`Innkeepers.arrivedOn`), vanilla's moon (day % 8), the hall's `Seer.State` (guests and tonight's
raid), `BanditCamps.all` and `VillageRaids.chance`; `Seasons`' page calls `ObservatoryPowers.fill`.

## Switches

All behind the 2.0 gate (`Expansions.M35`), which 1.0 opens with every other expansion; while it is closed they are off and hidden, whatever the config file says.

| Switch | Default | Off means |
|---|---|---|
| `wonders` | on | no new Wonders; finished ones keep their blocks, everything stays saved |
| `wonderLegends` | on | each Wonder's Legends are replaced by their stand-ins |
| `wonderServerLimit` | 0 (0 to 64) | how many villages may each build the same kind; 0 is no limit |
| `wonderCaps` | 0, 0, 1, 2 | file only: Wonders per village by rank (each 0 to 8; a short list is filled from the defaults) |
| `disabledWonders` | empty | file only: Wonder ids that can't be started (`great_cathedral` or `pack:id`) |
| `wonderCrew` | 12 (1 to 32) | the most builders on one Wonder's crew, its lead included |
| `wonderStanding` | 90 (50 to 100) | the share (%) of a finished Wonder that must stand for its powers; under it, it's damaged and repaired |

`builderScaffolding` (35.6) is on the Builder's pages.

## Saved data

A new saved file per dimension, `aliveworkplace_wonders`, with a list of `sites`: each with its `id`, `wonder`, `hall`,
`owner` and `ownerName`, `stage` (default 1), `begun` and `finished` (absent until it is). A world saved before 35.2 has
no such file and loads unchanged. 35.4 adds, all optional: `placement`, `stone`, `parts` (the current stage's build
sites) and `builder` on a site; 35.5 adds `called` (default false: every builder called to the crew); 35.7 adds
`dedicated` (default false: whether the current stage's goods were dedicated) and `delivered` (default empty: the goods
handed in at the stone for it, each its line, item, potion and count). A site saved before 35.7 with its stage's parts
down counts as started, so a Wonder going up keeps going. 35.8 adds `standing` (default 100: the share standing at the
last count), `counted` (default 0: that count's day), `damaged` (default false) and `festival` (default 0: the day of
its finishing festival); a Wonder saved before 35.8 reads as standing whole, its powers on. 35.10 adds
`wonderWedding` on a Village Hall (absent: none; its day, mood, days and Wonder) and two villager attachments,
`wonder_blessing` (on each of a blessed couple: the day, whether the first baby is still due, the Gifted
factor) and `blessed_child` (a blessed couple's child's Gifted factor), each absent until set, so older
saves load unchanged. 35.7b adds `searches` beside the sites
(absent in a world saved before: nothing searched): for each site waiting for goods, each found line's helper search
(its line, the item, the ticks searched and whether the helper was last seen in the village). A crew member's
job is the ordinary saved builder job (as a helper), so a crew comes back together after a restart; the columns are
worked out again; `wonder` and `no_foundation` on a build site (written only when set, so an ordinary
build saves exactly as before). 35.18 adds `skyChart` on a Village Hall (written once a search has begun; absent:
none begun): what was found, the set and region being searched, the hour the last set began, whether the week is done
and its `week` (absent: -1, a search begun anew). 35.16 saves nothing new: a Great Wall sentry is a Golem Smith's Wall Sentry
(`golem_role` `sentry`) with the tag `aliveworkplace_wall_sentry` and its post as `golem_post`, all saved with the golem.

## Items, blocks, jobs, commands

- `/workplace wonders call`: the stone's [Call every builder], for the owner or an operator within 8 blocks of it.

- `/workplace wonders`: every Wonder on the server, one line each: its name, village, owner and stage ("stage 2 of 3,
  …", "finished on day 12" or "finished on day 12, damaged (84% stands)"). While `wonders` is off it says "Wonders are off."

- **Wonder Plan** (`aliveworkplace:wonder_plan`): drawn up on the Wonders page; not crafted, not in the creative tab.

- **Foundation Stone** (`aliveworkplace:foundation_stone`): laid by a Wonder; not crafted, not in the creative tab,
  drops nothing.

## Decisions

- Found goods (35.7a, 35.7b) are one of each and never made: their counts are what the owner disliked. The workshop
  good is two at most and from a Journeyman's recipe, so the workshop has to be upgraded and staffed. A helper is
  slower than the group (one and a half times its hours) so it frees the group rather than doing the stage; it counts
  only while someone is on the server, because the owner's server runs all day. A helper brings the line without an
  item, so canceling never hands back something nobody found.
- The goods lists live in `tools/blueprints/wonder_goods.py` with their hour estimates and the pacing reckoning;
  `wonders.py` takes them from there, so drawing the Wonders again writes the same lines.
- The Great Cathedral's stages are cut by `wonder_stages()` on a 48-block grid (35.9): nine parts, two of stage I only
  clearing the air up to the spires' tips (y 48 to 52), and the spires' last few blocks a small part of stage IV.
- A stage's round rose opening and the nave's open east end wait for the next stage (35.9): the rose's ring is built
  with the nave (II) and its glass with the towers (IV), as the roadmap splits them; nothing hangs in the air between
  stages.
- The Lighthouse is about 5,000 blocks, not the 7,500 first planned (35.11): a tower 13 to 9 across with the cottage,
  boathouse, quay and jetty fills 25 by 33 by 46 that way, and only solid masonry would reach 7,500. Its base is solid
  round the stair and its wall three thick low down, as a sea tower's is.
- The Lighthouse's sea is the land behind its quay (35.11): the front (z 0) is the land side, where the village is; the
  mole, the boathouse's open end and the jetty stand in the water from 11 back. The jetty's deck is top slabs on log
  posts, so it gets no foundation, as the Fisher's Hut's; the mole's bottom course is filled down to the sea bed.
- The Lighthouse's lamps are redstone lamps under redstone dust that an inverted daylight detector on the lantern's roof
  powers (35.11): lit at night, dark by day, with no clock. The lamps' lit state is not checked as damage.
- The Great Library's walls are two leaves (35.13), diorite outside and smooth stone inside, with deep windows, and it
  stands on a solid terrace three high.
- The Great Library's sixteenth bookshelf place round the Enchanting Table is left open towards the door (35.13), so the
  librarian can walk in; fifteen shelves still give the table its full power.
- One Legend can serve two Wonders (the Seer for the Cathedral and the Observatory): a requirement only checks that the
  Legend lives in the village.
- The Master Architect requirement is added to every Wonder by the mod, so a server owner's own Wonder needs it too.
- Builders' scaffolding came with 2.0, for every build (35.6, [Scaffolding](scaffolding.md); an owner question in the design note).
- Stage I waits for its goods too (35.7): the design note's "the goods are taken and the stage begins" holds for every
  stage. `WonderStages.start` (commands and tests) still starts a stage at once.
- The dedication's state is the moment the goods are taken (35.7): the stage begins then, and the gathering, the Bard
  and the guards' line follow for 30 seconds without being saved, so a restart only cuts the gathering short and a
  stage never waits on villagers walking.
- The bell is heard, not rung (35.7): a rung bell sends every villager into hiding and the guards to a rally.
- Goods handed in are kept by the stone apart from the chests (35.7), so a cancel can give them back; they go to the
  Wonder's owner, whoever brought them.
- "The shortest line" for the daily quest is the one with the least of it in for its size (35.7); potion lines are left
  out of it (a quest's item can't name a potion's kind), and crafters fill only lines that name one plain item.

- A damaged Wonder is repaired whatever `builderRepairs` says (35.8), as what threats took is: its powers wait on
  it. Below the line, the builders' ordinary upkeep still mends a finished part's holes as for any building.
- A block put back as something else (a player's dirt in a wall) doesn't stand, and the builders never take a player's
  block out (35.8): the owner puts it right. A Wonder in the village's colors (the Stadium's seats) also stands in the
  colors it was drawn in, so a new banner doesn't damage it.
- The Village Map's gold banner is a yellow one (35.8): a map's banners come only in the sixteen dye colors.

## Known limits

- All eight Wonders' files ship (the Stadium's only with Cobblemon) and every Wonder's powers are in (35.10, 35.12, 35.14,
  35.16, 35.18, 35.20, 35.22, 35.24). Everything is behind the 2.0 gate that 1.0 opens.
- A lone builder reaches blocks far over anywhere he can stand (the folly's finial lantern, five blocks over the inside
  of its closed dome) only by scaffolding ([Scaffolding](scaffolding.md), 35.6); with no scaffolding in the chests such a
  block is skipped, as before.
- Drawing up the plans doesn't reserve the Wonder: two plans of one Wonder can be drawn; the caps hold when it's begun.
- A stage's `parts` are checked to exist when the plans are drawn up (the log names a missing one), not when loaded.
- `mood`, `beauty`, `undertakers`, `weddings`, `travellers`, the Lighthouse's four (35.12), `research_tier` (35.14), `raid_barrier` and `sentries` (35.16), the Gardens' `harvest` and `births` (35.20) and the Stadium's `stadium_days` and `tournament_host` (35.24) act. The seven types in `WonderPowers.DECLARED` (`caravan_range`, `caravan_speed`, `ferry_fares`,
  `early_warning`, `research_tier`, `harvest`, `births`) keep the generic reader; their Wonder's own code
  (`LighthousePowers`, `LibraryPowers`, `GardensPowers`) reads their numbers through `Wonders.power`.
- The Gardens' extra harvest goes to the mod's farmers and Orchard Keepers: a vanilla farmer with no chest by their
  composter (vanilla's own harvesting) brings in no more (35.20). A worker's count towards the next extra item isn't
  saved: a restart loses at most three of each kind.
- A Cathedral-blessed baby is the couple's first only: later children come as anyone's (35.10). A wedding blessed before
  the Cathedral was damaged keeps its mood and baby; only new weddings stop being blessed.
- Goods handed in come back as the plain item (35.7): a renamed flower comes back unnamed. A tag line's name ("Flowers",
  "Saplings", "Banners", "Berries") comes from the language file; a server owner's own tag shows as `#namespace:path`.
- The stone's status is its chat text, as in 35.4: there is no separate screen.
- A foretold raid (32.14) whose side is behind a standing Great Wall gathers on the open side nearest it, so the warning's
  side can be off by the Wall's arc (35.16). The sentries climb a wall's face only where they bump into it on the
  straight way to the walk beside their post; a Wall with no way up and an overhang there leaves one below until the
  hall's next look (it keeps trying).

## Proof

The design note, docs/design/M35.md. GameTests in `WonderDataGameTests`: the test folly's file loads (gametest
resources only), broken files are skipped with their reasons, a Wonder from a data pack on disk loads beside ours,
every requirement type holds and fails, `legend` falls back to its stand-in, the caps (a Town with one can't start a
second and a City can, the same kind twice, one going up, `wonderServerLimit 1`, disabled, off), `/workplace wonders`,
the switches and the gate, and the saved sites.

GameTests in `WonderPlanGameTests` (35.3): the folly's three parts merge into the tower as drawn (compared block by
block with the drawing), no part sets air over an earlier stage's block, parts are at most 48 a side, the whole
blueprint is cached and never on the Blueprint Table; the page's card ticks every line and offers the plans, one unmet
line (no Master builder) shows crossed, the cap line, and "What next?" names the folly; Draw up the plans takes the
player's 32 emeralds or the treasury's, refuses without the money, over the cap and for a stranger, taking nothing;
the plan is refused outside the village and over a finished building and goes down elsewhere in all four turns.

GameTests in `WonderStageGameTests` and `WonderSiteGameTests` (35.4): the parts at their offsets in all four turns and
mirrored, stage II never before stage I; handing the plan over lays the stone at the front corner (turned and mirrored
too), begins the Wonder in the chronicle and queues stage I with the stone as bench; refused unplaced, with Wonders off,
to a builder without a bench, of another village, and for a second of the same kind; the stone's status with empty and
full chests; the stone refused to a stranger and to a plain break, the owner's sneak-break abandoning it with the plan
back; one real builder raising the folly from the chests round the stone in each of the four turns, plain and
mirrored (8 tests), no stage II block during stage I and stage I complete when stage II begins, ending as drawn (but
the finial lantern, 35.6); a save and reload mid-stage II of the Wonders, every build site and the builder, finished as
drawn; old saves unchanged. Showcase scene `wonder_site`: the plan placed, the stone going down, stage I under way and
the stone's status.

GameTests in `WonderCrewGameTests` (35.5): eight builders raise the folly with no two on one column on any tick, no
structure block up with the drawing's block under it missing, all eight on the crew at once, and done in at most a
quarter of the time one builder alone takes on the same folly (measured in the same test, first); a save and reload mid-stage II with every
member, the crew back together and the folly finished as drawn; `wonderCrew 3` holds the crew to 3, nobody joins with
Wonders off, and a member lost mid-job is replaced; a builder with its own build queued joins only once every builder is
called, a stranger can't call, the stone names the crew and offers the call, the highest-level builder leads; the
lead killed mid-part hands the part to a living member, who leads it, and the crew finishes the folly as drawn.
`QaWonderCrewBugTests` (B467): a lead who dies or is retrained mid-stage is no longer named on the stone or the hall's
card; a living member of the crew who took the part over is.
`QaBuilderFixes1027cGameTests` (B467): `aLeadWhoDiesTwiceIsHandedOnAgainAndSurvivesARestart` (the new lead dies too,
the last member is named on the stone and the card, and still after the Wonders are read back from their save),
`aCrewOfOneWhoseBuilderDiesIsLedByTheNextBuilderToCome` (no lead left, then the next builder takes the part and is named).
Showcase scene `wonder_crew` (group Wonders): ten builders on the folly.

GameTests in `WonderGoodsGameTests` (35.7): the folly's stage II (three lines) waits through one line, two, a line one
short and the wrong potion, each line posted for the stone, then is dedicated taking exactly its goods (the spares and
the water bottle stay), in the chronicle, its posts down, never twice; a porter carries the eight bread stage I lacks
(and no more) from the storehouse to the stockpile; a chef cooks them from the wheat by his stove as a stock order; a
click on a Storehouse's board hands a line over (only what it lacks) and names the Wonder and the Chef; a stranger hands
flowers in at the stone (dirt is refused, a full line takes no more) and the owner's sneak-break gives the goods back
while the chest's stay; at night the stage waits and the stone says it's for the morning; the dedication puts the guard
in line, the Bard by the stone and brings a villager off work, and the stone broken in the middle ends it with nothing
given back; the goods and `dedicated` survive a save and load, a 35.5 site reads with the defaults; with Wonders off
nothing is handed in, posted or dedicated; the stone shows every line with "no Florist in the village" and every
sentence has its text; the hall's quest asks for the line furthest behind. The 35.4 and 35.5 tests put each stage's
goods in the stockpile (`WonderGoodsKit`). Showcase scene `wonder_goods`: the stone's lines half filled, the porter
bringing the books, then the dedication.

`WonderBlueprintGameTests` checks every one of our Wonders' designs (35.9 on): its file loads with the mod's own
loader, every part loads and is at most 48 a side, its name, description and stage names have their lines, the parts
aren't in the Blueprint Table, no part sets air over an earlier stage's block, the block count is within 10% of the
roadmap's (14,000 for the Cathedral, 5,000 for the Lighthouse, 16,000 for the Great Library, 9,000 for the Observatory, 12,300 for the Great Wall, 15,000 for the Hanging Gardens, 9,000 for the Colossus), and it uses no retired job block, no calcite and nothing a player can't get (still water counts: a builder pours it from a bucket).
The Stadium's file and its Healing Machine need Cobblemon, so its same checks run in the compat tests
(`StadiumCompatTests`, 12,100 blocks), with its requirements and goods as listed, its nurse's booth, Training Posts and
floodlights, and its stages built in a village's banner colors (blue and red for a blue banner with a red stripe, the
field's lines still white; red and white for a village without one). `StadiumGameTests`, without Cobblemon: its file
is skipped quietly (not on the page, no mistake logged), and the colors style and how a banner picks it.

Showcase scenes: wonders_page (group Wonders: the folly's card, then the placed plan's see-through tower) and
wonder_gallery (each of our Wonders placed whole, from the front and from behind; the Lighthouse with its sea flooded
in, so the mole and the jetty stand in the water; the Great Wall's front is its village side; the Hanging Gardens' falls are on their east side; the Colossus faces its front, down the road through its arch; the Stadium needs Cobblemon, so the scene is filmed with it; its card shows four of the stills, the rest are in its gallery).

GameTests in `WonderFinishedGameTests` (35.8): the folly raised through its plan has its test mood (+4, "proud of the
Test Folly" among a villager's reasons) and beauty (+2) on; 15% of it broken, the next day's count turns them off, the
stone, the hall's card, the chronicle and the owner say "damaged", the village's builder repairs it with
`builderRepairs` off and the powers come back; every player on the server is told (one 5,000 blocks away too), the
festival is that evening with the Wonder's bigger show (rockets from its top), the village's and its caravan partner's
chronicles record it, the Village Map has its gold banner and no part's; finished Wonders and their upkeep survive a
save and reload (the powers from the loaded copy), old saves read as standing whole; `wonderStanding` 50 against 90,
weathered and waxed copper standing as drawn, Wonders off; every power type registered by id, bad numbers refused, a
type registered later read from a file. Showcase scene `wonder_finished` (group Wonders): the folly's time-lapse, a
crew of ten, ending in its festival's fireworks.

GameTests in `WonderCathedralGameTests` (35.10): an Undertaker with an empty chest and a grave 40 blocks from the table
leaves it for 800 ticks without the Cathedral, then with it standing walks there and brings Mira back, nothing used;
the night's grave emptied at dawn (the Undertaker credited, the players' line), not one from after dawn, not at 3,000
ticks, not without an Undertaker; a wedding goes to the altar and is blessed (the couple, the hall, the chronicle and the
players' lines), a guest 15 happier with its reason for 3 days and not on the 4th, the first baby not on the wedding
day, then the next day past the store and the mood, once, and twice as likely Gifted (the same dice make the blessed
child Gifted and another not); damaged, no free revival, no village-wide search, no morning, the bell's wedding unblessed
and the 8 beauty gone, repaired all back; the file's numbers and refusals, the page's lines, the altar before the gold
in its drawing (and turned with it), and the belfries' bells. Showcase scene `wonder_great_cathedral` (group Wonders):
the Cathedral's time-lapse with a crew of ten, ending at dusk with a blessed wedding at its altar before the guests.

GameTests in `LibraryPowerGameTests` (35.14): without the Library its row reads "Needs the Great Library" and a click
chooses nothing; with it Swift Hands IV waits for III, Scholarship is chosen on the screen and a scholar pays 24 paper,
6 books and 8 emeralds and researches it at twice the points, and damaged the next level waits and the row says the
levels are kept; Swift Hands V makes work 25% faster (15% damaged, the 5 levels kept), Scholarship II a scholar 50%,
and on top of a fivefold rush the pace stops at the cap; Scholarship II's quarter off; Engineering's 36 and 45 stacks
and 4 more crew, a 45-stack bag saved and loaded whole, and damaged new stacks keep to 27; Cartography's range and its
maps' trial chambers and ancient cities; Trade Law's 8 stacks and a fifth more takings; Pharmacy's one day and a
Nurse's one remedy-free cure a day; an old village's research (every first-tier topic, a tree's work in hand) and an
old 27-stack bag loading unchanged. Showcase scene `wonder_great_library` (group Wonders): the Library's time-lapse,
ending in its rotunda on the scholar's screen with the second tier open.

Compat GameTests in `StadiumPowerCompatTests` (35.24, with Cobblemon): a Stadium Day brings two to four travelers
(the day's random) into the boxes at noon of an even day and none in the morning or on an odd day, no more on another
round, all gone at dusk and none back that day; a Master beats a visiting Novice and a visiting Master the village's
Novice, 8 XP to the winner and 5 to the loser, a battle each, in the chronicle, and the bouts one an hour from 7000;
a player's win over a visitor pays once a day and a loss nothing; the Stadium is the village's Arena for the Cup; a
damaged Stadium (and Wonders off) holds no Stadium Day and hosts nothing, repaired the visitors come and a villager by
a seat sits down 5 happier ("watched a Stadium Day"); the drawing's own posts, field center, box spots (room to stand),
over 300 seats and the gate. Showcase scene `wonder_stadium` (group Wonders, with Cobblemon): the Stadium's
time-lapse, then a Stadium Day with the visitors in the boxes, the stands full, a bout in the chronicle and the
player's battle against a visitor.

QA compat GameTests in `QaStadium1028CompatTests` (35.24): the clock's edges from both sides (5999 nobody, 6000 the
visitors, 11999 still there, 12000 gone, the odd day after nobody, the next even day that day's own count); the chat
a player at the Stadium reads ("Stadium Day at (village): (n) visiting trainers stand in the boxes until dusk.
Challenge them!" with n the visitors, then "At the Stadium: (winner) beats (loser)"); the Stadium damaged mid-day
(visitors gone, no bout, none back once repaired); a visitor killed mid-day (no replacement, the rest still fight one
an hour); a visitor and the day's record through a save; two visitors beaten the same day pay once each, in
CobbleDollars only; a resting villager stays out of the stands, the +5 lasts until the next Stadium Day; the power's
line in each form. A failing test on the tests/stadium-thesis-1028 branch (until fixed): a file
with one visitor reads "1 visiting trainers".

GameTests in `ColossusPowerGameTests` (35.22): a villager at home 12 happier with "proud of the Colossus", one in a
village off its routes not at all, then with a caravan route 8 happier with "the realm's Colossus", its 8 beauty;
damaged all back as before, repaired on again, Wonders off off; the inn one traveler a morning and two at most
without it, two arriving the same morning (beds free) and four fitting with it, full at two when damaged, Wonders off
the usual; the same dice making twice as many Experts in 4,000 rolls (every usual Expert one too); the plaque's seven
signs written once finished and not before, reading the hall owner, Thornholm, "on day 3" (a colony's founding line
ignored) and the finishing day, waxed and glowing, written once; a sign put back written with the settled Founder's
name; the type's numbers, defaults, refusals and its page line. Showcase scene `wonder_colossus` (group Wonders): the
Colossus's time-lapse with a crew of ten and its scaffolding, ending at midnight seen from the road, the lantern lit
over the arch and the plaque written.

GameTests in `WonderGardensGameTests` (35.20): a farmer in a village with a caravan route to the Gardens' village brings
in 50 wheat from 40 harvests and a quarter more seeds (a fixed random), 25% more in all, and nothing more without the
Gardens or the route, or once it closes; an Orchard Keeper there a quarter more berries; the Gardens' own farmer the
same; two farmers' wheat counted apart; with the Gardens a baby needs 12 meals (11 aren't enough; without them 16) and
two are born in a day with two free beds, not a third sooner; damaged, both off, repaired, both back; the whole Gardens
built in the area, each of its four farm corners has a chest and a field in reach, and a farmer at the first terrace's
composter takes on its field by themselves, harvests it and brings in the extra; the file's numbers and the page's
lines. Showcase scene `wonder_hanging_gardens` (group Wonders): the Gardens' time-lapse with a crew of ten, the
festival's fireworks, then the next morning the falls running and a farmer at each farm corner taking on its terrace.

GameTests in `GreatWallPowerGameTests` (35.16): the Great Wall placed north of a hall covers about 195 degrees of its
compass with the margin; 50 raids started with a fixed random gather outside it, none of their raiders behind the
Wall itself, and bearings toward a camp behind it come out on the open side; the same 50 with the Wall damaged, and
without it, gather on its side too; repaired its barrier is back, with Wonders off it isn't; its Great Gate's fence
gates (in the gateway) shut at night and open by day with no guards; it shields its blocks from ladders and none of
its parts is a siege's wall or gate. A test wall's sentry, a Wall Sentry with 200 health, holds its post on the walk,
and put down at the wall's foot with no way up it climbs back to its post. A lost sentry isn't replaced the same
evening, nor in the morning with no Golem Smith or Tinkerer; with a Tinkerer it is, from the stockpile's 4 iron blocks
and carved pumpkin (used up) with the chronicle line; not again without the iron, nor from three blocks; from four and
a plain pumpkin it is. Damaged: no duty, no posts, no barrier, no replacement, a sentry left below stays there; Wonders
off the same; back on, it climbs back. A sentry saved and loaded is still one; the types' numbers and refusals. Showcase
scene `wonder_great_wall` (group Wonders): the time-lapse, then at midnight a raid gathering on the open side while the
four sentries watch from the walk and the Great Gate stands shut.

GameTests in `ObservatoryPowerGameTests` (35.18), with a stub structure finder: three structures of three kinds are
found in 432 regions (three kinds of 144) and the hall's map button, for two empty maps, gives the village map and the
Sky Chart with a marker per structure and each named in its tooltip; the first in-game hour searches exactly one kind's
144 regions, none while the tick has no room, the next hour the next kind; the search and its week survive a save and
load, a hall saved without one loads with none begun, and a week later the search begins again keeping what it found;
the Coming days list a staged festival (in 3 days), a wedding (tomorrow, once for the couple) and an incoming caravan
on its arrival day, the full moon within the week, and the calendar page shows them under the seasons; damaged, or
with Wonders off, the search, the chart and the Coming days stop, and repaired the search goes on. The chart in
hand is drawn and marked (B140): most of its pixels hold the land, holding it puts a marker on the map for each
structure found, and the packet sent to its holder carries the whole land and the markers. Showcase scene
`wonder_observatory` (group Wonders): the time-lapse, then the Sky Chart in hand and the hall's calendar with the
Coming days.

QA GameTests in `QaWonderPowersGameTests` (35.10, 35.12, 35.14, 35.18, 35.20, 35.22), from the items' text: the
Colossus's mood reaches a village whose caravans run to its village but not that village's own partner, and its
travelers and beauty stay at home; the Gardens' harvest reaches the partner while its births stay in its own village
(12 meals and two a day there, 16 and one in the partner, never fewer than a village's usual); the Great Cathedral's
undertakers, weddings and beauty, the Great Library's second tier and the Observatory's Sky Chart and Coming days are
their own village's only; the Lighthouse's routes reach 4,096 blocks and not 4,097 (2,048 and not 2,049 without it),
a partner's caravans neither reach further nor go faster, and fares are halved for a post at the village's edge both
ways but not a block beyond it nor in the partner village; each turns off damaged or with Wonders off and comes back
repaired, and the Gardens' and Lighthouse's powers come back from a save (one saved damaged loads powerless).

GameTests for `/workplace cancel` on a Wonder waiting for its goods (B462): `WonderCancelGameTests` (3: with goods in,
a stranger refused and the stone's [Abandon this Wonder] link; with none in; after a save and reload) and QA's
`QaBuilderFixes1027bGameTests` (3: a stack of 64 candles offered twice comes back as exactly 64 and one plan, and a
second cancel gives nothing more; an operator who isn't the owner ends it and the goods and plan go to the owner, not
the operator; a wrong id leaves the Wonder and its handed-in goods alone).
