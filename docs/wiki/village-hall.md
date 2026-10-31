# The Village Hall

The block that turns a group of villagers into a village the mod looks after: its people, food, beds, rank, calendar,
treasury and pages. Most other systems (edicts, guilds, Legends, classes, the Steward) hang off a hall.

Roadmap items: 22.5, 22.6, 30.4a, 26.12, B37, 36.5, 36.9, 36.9a, 36.15, 36.14, 36.18, B301, B303, B362, B406, B413, 40.1, 40.2, 40.3, 40.4, B546, B547

## What a player sees

You craft a Village Hall and put it in the middle of your village. Right-click it and a screen of its own opens, drawn
like the Blueprint Table's (not a chest): under the gold title a strip of icon buttons, the village's numbers (name
and rank, villagers, beds, food in store, guards, wellbeing, requests, buildings going up, quests) and the toolbar
(map, the Book of Edicts, the bell, mercenaries, the Chronicle, a festival, trade, "What next?", the bank). On the
left a list: the page tabs, then everyone who lives within 64 blocks, each as their workstation (stacked as high as
their level) with their name and job. The pane on the right reads out whatever is under the mouse, every line of it;
with nothing there it shows the village's numbers "at a glance". Click a villager and they glow for ten seconds so you
can find them, and stay in the pane (the wheel scrolls a long one; × or Esc puts them away). Click a jobless villager
to see the free workstations and give them one. Every page of the hall opens in the same screen. (Since Milestone 40
the left side is the sidebar and the villagers are the People table; see below.)

**The screen's rules (30.4a, tested in `QaHallCustomScreenGameTests`).** Every button of every section, and every page
it opens (quests, the bank, the Book of Edicts, the chronicle, ...), stays on the hall's own menu type, never a chest.
Only a plain click or a shift-click presses a button: swapping with a hotbar key, throwing, middle-click cloning,
double-click collecting and dragging do nothing, clicks on the player's own slots or outside the window move nothing,
and the sidebar ignores numbers no section has. The screen opened by hand closes when the player is more than 8 blocks
from the hall's center or the hall is broken (clicks a stale screen still sends then do nothing). The Village Ledger's
screen works from any distance while the hall stands and closes when it's broken. Two players can have the hall open
at once: each has their own screen and page, and each one's last section is their own.

**The sidebar (40.1).** Since 40.1 the screen has a sidebar on the left: the village's name and the date
("Spring, day 1 · Year 1") over ten labeled buttons, Overview, People, Homes & Food, Buildings, Defense, Quests,
Trade, Calendar, Laws & Realm and Map (`hall/HallSections`, the map of what goes where in `docs/design/M40.md`). The
server still lays out the same 54-slot main page with the same clicks; at the end of `VillageHallScreen.render` it
marks each button with the sections it belongs to (an int mask in the icon's custom data, under `HallSections.MARK`;
page tabs by `HallSections.place`/their page id, unplaced pages under Overview; the people list under People) and sets
five numbers on the menu (`ChoiceMenu.state`, data slots only on the hall's menu type): the open section, a bit per
empty section, season, day and year. The client (`VillageHallMenuScreen`, hooks in `ChoiceTableScreen`) shows only
the open section's buttons, all as labeled rows, a plain sentence over the list when the section is empty (People
with nobody, Homes & Food with no beds or food, Buildings with nothing going up, Defense with no guards, Quests with
none open, Trade with no neighbor in range, Laws & Realm with no edict or realm), and with nothing hovered the advice
tips (Overview; the advice button's lines carry every tip and how) or the section's one-line purpose. A sidebar click
is vanilla's menu-button click (`clickMenuButton(player, ordinal)`), which remembers the section per player while
the server runs (`HallSections.last`, not saved) and lays the main page out again, from any page. A page opened from a
section keeps the sidebar; its back button returns to the section. The Book of Edicts or a page opened on its own
(bounty board, ledger sneak-click) has no year set, so no sidebar.

**The People table (40.2).** With People open, the client (`VillageHallMenuScreen.renderBody`, a body hook in
`ChoiceTableScreen`) draws the section as a table in place of the list and pane: the section's other buttons (the
figures, the recall bell, the Legends/Classes/Free jobs tabs) as a strip of icons on top, then "8 · 6 working · 2
jobless" (plus "· 2 children" when there are some), a hint line, and Name | Job | Level | Mood columns. The server
(`hall/HallPeople`) marks each person's button with the table's columns (custom data under `HallPeople.MARK`: uuid,
kind = worker/builder/guard/jobless/nitwit/child, the job's lang key, level, mood word, what Job ▾ does) and sets five
more menu numbers (`HallSections.PEOPLE_TOTAL`, `_WORKING`, `_JOBLESS`, `_CHILDREN`, `_FIRST`; `ChoiceMenu.STATE` is
10). The people list now includes the children, last, nearest the hall first. Big villages: the main page still holds
25 people (`PER_PAGE`); the table scrolls over all of them and, when the scroll reaches people not loaded, sends a
menu-button click of `HallPeople.SCROLL` (1000) + the first person to show, and the server lays the list out from
there (`VillageHallScreen.renderFrom`); rows not yet loaded show "...". Mood: Happy (score >= `Moods.HAPPY`), Unhappy
(< `Moods.UNHAPPY`), Content between, and "No bed" (red) when they have no bed and aren't happy. A row click opens
(client only, kept by uuid) the villager's details under it: every line of the person's hall tooltip (traits, what
they're doing, needs, mood and why, class and how to rise, home, where), with **Job ▾** (the list of jobs, 40.3,
below; grayed for children, nitwits and Legends, hidden on every row with retraining off), **Bed ▾** (the village's beds, 40.4, below), **Find** (the
glow) and, for a named villager (the row's lore carries `life_story_hint`), **Story** (B547: the row's slot clicked as
`QUICK_MOVE`, the shift-click that opens `LifeStory`, then Family, as in the old list). The details leave out the old
list's click hints (`click_to_find`, `worker_click`, `life_story_hint`, `jobless_click`), none of which is true in the
table. The open row is drawn, and takes clicks, while any part of its block shows: a row is culled only when its name
line and its details and lists are all above the table's top (B546); every table hit is clipped to the table. The
header says "1 child" for one (`header_child`, B547). A click on a row whose villager has died or left the village since the screen was laid out lays the list out
again without them (`HallPeople.gone`).

**Job ▾ (40.3).** Job ▾ on an open row opens a list of every job right under it (`hall/HallJobs`), the table
scrolling over it. On top: "A new job for Dara", then the price by their level ("Retraining costs 20 emeralds
(Journeyman)", the Blueprint Table's `retrainPrices`) and "Their trades and level are lost: they start again as a
Novice", before anything is paid; a jobless villager's list says a free block is theirs for nothing instead. Then one
line per job a villager takes at a work block (every registered job with a block, behind its config switch: not the
Steward, Legends, the Trainer Leader, nitwits): those with a free block in the village first, "Mason · 1 free
Stonecutter" (counted over every free block, a shared block such as a lectern counting for each of its jobs, and
named by the free blocks themselves, nearest kind first: "Carpenter · 1 free Carpenter's Bench, 1 free Crafting
Table", B658), then the rest grayed: "Mason · place a Stonecutter first", a job above their class with the class it needs (34.8), "Librarian ·
their job now". The Builder is one of them ("Builder · 1 free Blueprint Table"). Last, for a villager with a job, **No
job**. Hovering a line shows where the nearest free block is, the price and what the click does. For a worker, a first
click on a job sends the offer to chat and marks the line ("Click again to pay 20 emeralds: Dara becomes a Mason"); a
second pays and retrains them through 36.18's `HallRetraining.retrain` (the price, the class check, "someone took that
workstation first", not enough emeralds: nothing changes and the player is told how short), and the list closes on a
row that says the new job. A builder hands his builds back (`Retraining.dropWork`). A jobless villager is given the
block in one click, for nothing (`VillageHalls.assign`, as the free jobs page). No job is free, also two clicks: the
first says plainly that their trades and level go and their block is left free; the second takes the job away
(`Retraining.unemploy`: the builds handed back, the block let go, profession none, level 1). They may then take a free
block nearby by themselves, as any jobless villager does. A grayed line's click says what's missing ("The Mason job
needs a free Stonecutter: place one in the village first"). Children, nitwits and Legends get a grayed Job ▾; with
`workplaceRetraining` off it's hidden on every row. No new packet: Job ▾ is a menu-button click of `HallJobs.OPEN`
(100000) + the row's slot (again: closes), a job is `HallJobs.PICK` (200000) + its registry number (No job:
`VillagerProfession.NONE`'s), `HallJobs.CLOSE` closes it; the list reaches the client inside the open person's button
(a `minecraft:container` of one stack per line, each with its name, tooltip and a `HallJobs.MARK` with its state and
job number), and the row's mark gains `jobs_open` and `job_hidden`. Which list is open, and the job clicked once, live
with the open screen (not saved).

**Bed ▾ (40.4).** Bed ▾ on an open row opens the village's beds right under it (`hall/HallBeds`), the same way: "A bed
for Dara" and where she sleeps now ("Sleeps in: Stone House, bed 2", or "Dara has no bed now."), then the free beds
nearest the hall first, each with where it is: the finished building it's in and which of its beds (`Homes.in`, the
beds of a building numbered by position), else how far and which way from the hall ("free · 14 blocks east"). Then
the taken ones: hers ("… · their bed now"), a bed another villager of the village sleeps in ("… · swap with Jem"), or,
when she has no bed to give in return, "… · Jem's bed" (grayed), and a bed someone from outside sleeps in ("… ·
taken"). With no free bed, "Short of beds? Plan a house ›" opens the Buildings section; with no bed at all the list
says "No beds in the village". The last line says wagon beds and cradles aren't listed (a wagon's bedrolls are no homes
once the hall stands, `CampBeds`; a cradle holds a child of the house at night by itself, `Cradles`). Only the beds
in the village round the hall (`VillageHalls.beds`). A click on a free bed makes it hers at once with vanilla's own
home: the bed's point of interest is taken and her `HOME` memory set (`HallBeds.moveIn`); her old bed is released
(`Villager.releasePoi`), so someone else can have it; a swap sets both villagers' `HOME` to the other's bed (both stay
claimed, so nobody slips in between). Anyone asleep is woken first. She sleeps there that night by vanilla's own
bedtime. The row and the census read the same memory through `VillageNeeds.bed` (B362: a broken bed houses nobody), so
both show it straight away. If the bed changed after the list was laid out, the click changes nothing and the list
shows it as it is now; the action bar says "Someone took that bed first." (a free bed claimed by someone else), "That
bed changed hands: the list shows it as it is now." (a bed whose sleeper changed, such as a swap partner who moved) or
"That bed is gone." (the bed broken).
No new packet: Bed ▾ is `HallBeds.OPEN` (300000) + the row's slot (again: closes), a bed is `HallBeds.PICK` (400000)
+ its line's number (the beds in the order the list showed them, kept with the open screen together with who slept in
each), `HallBeds.CLOSE` closes it; the row's mark gains `beds_open`. Opening one list closes the other. Nothing new is
saved: the bed is vanilla's own memory.

**Reading without clicking (36.5).** A row or cell hovered stays read out while the mouse moves on into the pane
(it changes only when another row or cell is hovered, and lets go once the mouse leaves the panel, when the pane goes
back to the button last clicked), so the wheel there scrolls a long entry with nothing clicked; Shift and the wheel
over a row scroll that row's reading and leave the list where it is. A small arrow at the pane's foot shows while
there's more below, and a name too long for its two lines (a long chronicle entry) carries on at the top of the
reading. The footer says "Hover to read · scroll in the pane or with Shift · click to use". A click in the pane runs
nothing. The rule lives in `work/ReadingFocus` (no drawing); the Storehouse board shares the screen and the rule.

**Free job blocks (36.9).** A tab with a crafting table opens the "Free job blocks" page: every job block in the
village nobody works at, nearest the hall first. Each shows the block, the job it gives, the building it stands in
("In the Storehouse", or "Not in a building"), how far and which way from the hall, and how many villagers without a
job could take it. Click a block and its page lists those villagers; click one and they get the block: they claim it
and walk over to it. So an empty Storehouse or a lectern left in a finished home can be filled from the hall, without
leading anyone into the house. Blocks of one kind in one building share a line with a count ("Fisherman ×4", "In the
Storehouse Shed"); a click takes the nearest. A block several jobs share (a composter, a lectern, a Shop Counter...)
says how many jobs it offers ("Composter: 4 jobs") and lists them; its click opens a page with a line per job, and a
job's click lists who could take the block as that job (B303). Blocks a villager never takes by themselves are listed
too: a crafting table (the Carpenter's, as in a Carpenter's Workshop), a jukebox (the Bard's) and a Mailbox (the
Postman's), so the hall can give those jobs without an item (B635). A villager who already works isn't listed; they change job from their own page (36.18, below) or
at the block itself (26.6, see [Villager jobs](villager-jobs.md)), for a price.

**Change job (36.18).** A plain click on a worker in the residents list only makes them glow, as before; a right-click opens their page (back,
the worker, Find, and a **Change job** emerald at slot 22). It's offered when `HallRetraining.canRetrain` holds: rule
`workplaceRetraining` on, a grown villager with a job and a job site, not a nitwit, Legend or caravan carter. Change job
lists `VillageHalls.freeStations` of a job other than theirs, each with the price (`Retraining.price`, 5/10/20/35/50 by
level, config `retrainPrices`), and the Builder: a free Blueprint Table is listed like any block; with none, a gray
Builder entry says to place one. The first click marks the block (glint, "Click again to pay") and posts the 26.6 offer
text; the second runs `HallRetraining.retrain`: the hired-worker check (`Friends.mayCommand`), the class check (34.8),
the block still free, the money (`Money`, CobbleDollars when present), then `Retraining.retrain` (a builder's
builds handed back, level and XP reset, `Stations.assign`: profession, job site memory and the POI claim all on the click). The
villager gets a walk target and the saved attachment `retrain_walk` (`HallRetraining.Walk`: block, game time). Each
second, `HallRetraining.tick` (from `VillagerMixin`) ends the walk when they're within 3 blocks, the block is gone or no
longer their job site; keeps them walking (and clears vanilla's `CANT_REACH_WALK_TARGET_SINCE`, so vanilla never drops
the job site meanwhile); and after `ARRIVE_TICKS` (1,000, about a minute) moves them to the nearest standing spot by the
block (`Walker.standingSpot`). Asleep, they wait till they wake. The list then shows the new job. Tests:
`HallRetrainingGameTests`; scene `hall_retrain`.

**Repairs (36.15).** A tab with an anvil opens the "Repairs" page: the village's damaged builds, each with what's missing
and where its question stands (waiting for your answer, being repaired by a named Builder, agreed, paused, left for
now). A click on one gives Yes, repair it and Not now, or Pause and Stop the repair while it's being repaired. Builders
ask here (and in chat) before repairing anything; what raiders and village fires broke is repaired without asking. The
whole of it is on the [Builder](builder.md) page.

**Upgrades (36.14).** A tab with bricks opens the "Upgrades" page: the village's finished builds that have a next level
(a Starter Cottage with a Starter Cottage II, a Stone House II with a Stone House III...), nearest the hall first. Each
row shows the build, its level and the next ("Level I, next: level II, the Starter Cottage II"), how far and which way
from the hall, what the next level costs (the blocks in all, and the three it needs most of), and whether its space
is free. A free row has bricks; click it and the village's Builder (the nearest one free,
or the one with the shortest queue) builds the next level over the old one, and the row turns to scaffolding, "A
Builder is building it now". Grass, dirt, stone, sand, trees, snow and water where the next level goes don't stop it:
the row says how many such blocks the Builder clears on the way. A wall, a fence, a path, a chest or another build in
the way does: the row turns to a barrier and says what and where ("Blocked: Oak Fence in the way at 120 64 -38", or
"Blocked by the Smithy: its Stone Bricks at ..."), and a click only says so. The villagers who live there keep their
beds: the Builder never breaks a bed somebody sleeps in. With no Builder free or with room in his queue, the click
says so ("No Builder can take the ...: none has a bench near it and room in their queue") and orders nothing; a busy
Builder gets it queued "after the current build", once however often it is clicked. In a village somebody owns, only
the owner and their friends can order one. The tab says how many builds can go up a level ("1 build", "2 builds").

The buttons open the hall's pages: Quests (a journal), the Chronicle (what happened, day by day), trade routes
(caravans to other halls), "What next?" (advice, or the Steward's desk when there is a Steward), the village map, the
Book of Edicts, the calendar, Legends, the Festival Cup and Classes. The bell calls everyone home. A cake calls a
festival. A named Name Tag used on the hall names the village (the tag isn't used up; like the City Plan, the Village Ledger and
the charter, it works from the off hand when the main hand is empty, B360); otherwise it gets a made-up
name such as Thornholm.

## How it works

**The village is a circle.** Everyone and everything within `villageHallRadius` blocks of the hall (64 by default, 32
up and down) belongs to it; its villagers and its beds are counted by that one circle, so a villager and the bed beside him are
always both in or both out (B361, `VillageHalls.contains`). A villager is housed only while his bed is still there: a bed broken at noon houses nobody from that moment, though vanilla forgets it only at bedtime (B362, `VillageNeeds.bed` checks the bed's point of interest). Once every 600 ticks the hall counts its people, beds, food, guards and light; that count
feeds everything else.

**Clearing-out quests follow the world's foes (B301).** "Clear out 8 monsters" (a `kill` of `monster`, a `slay` reform
step) counts monsters and the mod's own bandits, raiders and lair mobs (by their tags). Monsters can come unless the
world is on Peaceful or another mod's spawn blocklist refuses all of zombie, skeleton, creeper and spider: `hall/Foes`
asks the `BLOCKLIST` extension point, which `compat/mobsbegone` fills from `config/mobsbegone-blacklist.json` (every
quoted id in it, read again when the file changes). Where none can come, wild Pokémon a player battles or catches near
the hall count instead (`compat/cobblemon/CobblemonWildFoes`, Cobblemon's `BATTLE_VICTORY` and `POKEMON_CAPTURED`) and
the quest reads "Defeat 8 wild Pokémon". With neither, the board never posts one, a reform's or story's step asks for
bread instead (as many as the foes, 64 at most, same pay), and the hall's round swaps one already up: a board quest
comes down for another, a story's or reform's step becomes the bread, started afresh.

**Needs set the pace.** Every grown villager eats once a day from the store (chests by the kitchens first, then by
the Storehouses), wants a bed of their own, one guard for every ten villagers and light by the beds. How well that is
met is the village's wellbeing, 0 to 100%. Work goes up to 25% faster in a well-kept village, at the usual pace at
50%, and up to 20% slower in a badly kept one. Villages without a hall work at the usual pace.

**Ranks.** Hamlet, then Village (10 villagers and 5 finished buildings), Town (20, 12, and 3 research levels), City
(35, 25 and 7). A rank up means fireworks, a chronicle line, more caravans, more market traders, quests that pay a
quarter more, and room for ten more villagers. Roads never count as buildings and a whole wall counts as one.

**Growth.** At most once a day, with a free bed, 16 meals in the store and wellbeing of 50% or more, the two
villagers nearest the free bed have a baby (the family eats 8 meals), up to `villageGrowthCap`. The baby takes that
bed at once (B206), so a second birth, an inn's traveler or vanilla breeding can't count the same bed free: a village
with N free beds takes in at most N newcomers. With no bed free the Beds button says **The village is full: nobody new
moves in until a bed is free**.

**Wagon beds are no homes** (B206, B207, `camp/CampBeds`). The bedrolls of a Settler's Wagon's camp or a Colony Camp
are listed when the camp is placed (`aliveworkplace_camp_beds`, per dimension); an older camp's are known by their
look (a brown or green bed with a campfire and a hay bale within 10 blocks and the camp's Blueprint Table, or an old
camp's Builder's Bench, within 12) and listed once seen. A player's brown bed by a campfire and hay, with no camp's table,
stays a home (B715); a list saved before that fix has its beds looked at once more, and a bed without the camp's look is
let go. At each round the
hall moves out anyone homed in one (they wake and forget it, then find a bed in a house) and holds every wagon bed for
nobody, so it is never claimed again. The census leaves them out of Beds and Free, and the Beds button says how many
it left out ("Not counted: 2 wagon beds"). Before a hall stands, the settlers sleep in the wagon as before.

**Treasury.** Each morning every worker puts by `treasuryPerWorker` hundredths of an emerald (20: a fifth of an
emerald), half in a badly kept village, half again in a well kept one, a quarter more per rank. Whoever opens the hall
collects it. It holds up to a stack of emeralds a rank and counts at most 3 missed days.

**Calendar.** Four seasons of `seasonDays` days each (16), the same for the whole world, counted from the overworld's
day. Each season has a festival on its middle day. Festivals also come every 8 days where at least 6 villagers live.

**Protection.** The hall's owner (whoever placed it) can switch protection on by shift-clicking the hall's name
icon. Then only the owner, their friends and operators may break or place blocks, open chests or hurt villagers in the
hall's area. Anyone may still walk in, open doors, trade and ring the bell.

**Page tabs (22.5).** The third row holds 9 tabs. Any feature adds a page with one registration call
(`docs/agent/layout.md`); the mod's own later pages (Classes, Wonders, Free job blocks) give way to pages added by
others when the row is full, newest first.

**Free job blocks (36.9).** The page is read when it's drawn: the village's points of interest that give a job and
have room (the same list a jobless villager's own page shows and Job ▾ counts): the kinds in the game's
`acquirable_job_site` tag, plus the shared stations a job is given at only by an item (`work/Stations`, not
`byItself()`: crafting table, jukebox, Mailbox and Cobblemon's machines, under their first job this game has;
B635: the tag alone left the Carpenter's table out), and a beehive or bee nest nobody works at (B657: they keep no
places, so "have room" never counted them; `Stations.freeAt`), each matched to the building whose outline holds it
(finished builds, and builds going up). Who could take a block: a grown villager in the village without a job, not a
nitwit or a Legend. One who has never worked takes any block; one who still has an old job's trades but lost the block
is offered only a block of that same job, because changing job is paid retraining (26.6). A job above a villager's
class (34.8) is grayed with the class it needs. The pick is checked again on the click: if someone took the block
meanwhile, or the villager found work, nothing changes, the action bar says why and the list comes back. The page
shows up to 36 lines; past that, the last slot says how many more blocks there are further away.

**Several jobs at one block, and lines by building (B303).** `VillageHalls.freeStations` still names each block by its
first job (the one vanilla gives, and the one the Steward, families and stories use). The page asks
`VillageHalls.jobs` for every job the block offers: that first job, then the block's other jobs in `Stations.ALL`'s
order (those the job keeps as its block), then any other mod's job that takes and keeps that kind of block; only jobs
this game has (`Stations.available`), never a nitwit or the Trainer Leader (a gold block makes one). `FreeJobsPage.groups`
puts the blocks of one kind of point of interest in one building outline (or all out in the open) on one line, nearest
first; one block with one job is drawn exactly as before. Picking a job gives the block as a `FreeStation` of that job,
so `VillageHalls.assign` makes the villager that job by order (36.17's random draw never touches it), the class rule
(34.8) is checked for that job, and who could take it is counted for that job: a villager with an old job's trades only
for that same job. The job is the villager's profession, saved by the game.

Another mod's block that is a job block of ours (CobbleFurnies' Furni Crafter, Create's Toolbox and Train Station,
Cobblemon's Campfire Pot and Pasture) and was placed before the update that made it one has no point of interest in
the save; it gets one when its chunk loads, so it shows on the page from then on (B302; `work/NewWorkstations`).

**Upgrades (36.14).** The page reads the village's finished builds (the hall's circle) whose blueprint has a next level
by name (`<name>_2` after `<name>`, `_3` after `_2`, see `BlueprintUpgrades`), the same list the hall's advice and the
Steward's upgrade rules use. The order is the same as every other upgrade: a build site of the next level on the old
one's spot and turn (`Builders.start`, or `enqueue` behind a busy Builder), built only where it differs, recorded over
the old level when done. The cost is what the next level's material list needs more of than the old one's, grouped by
family as the Builder's lists group them. The space check walks every spot the next level's blueprint fills that the
old level left empty or doesn't reach (lowest first); a spot holding air, or already the block the next level wants,
is fine; a spot inside another build's outline (finished or going up) blocks unless it holds only air, water or
something replaceable; elsewhere a spot blocks unless its block is natural: what the Steward's sites clear (ground:
dirt, grass, sand, stone, gravel, clay, snow; growth: logs, leaves that grew, plants), water and lava, ice and ores.
The first blocking spot is the one named. A room left empty in both levels (with what the villagers put in it since)
isn't something the next level adds, so it isn't checked. While clearing, a Builder now leaves a bed a villager has
claimed (its home point is taken) where the blueprint only wants air, so residents keep their beds through any
upgrade, the Steward's and bought ones too. Ordering needs the right to build in the village (the owner, their friends
or an operator; anyone in a village nobody owns), and is checked again on the click.

## Switches

| Key | Default | What it does |
|---|---|---|
| `villageHallRadius` | 64 (16 to 160) | How far from a hall its village reaches |
| `villageGrowthCap` | 40 (0 to 500) | The village stops having babies at this many villagers; 0: no growth (the hall says "Villages don't grow on this server") |
| `villageTreasury` | on | Villages put by takings every morning |
| `treasuryPerWorker` | 20 (0 to 500) | Hundredths of an emerald a worker a day |
| `villageProtection` | on | Owners may protect their village; off: nobody can, on the whole server |
| `villageQuests` | on | Halls post quests; off: no new ones, open ones can still be finished |
| `festivals` | on | Regular festivals; off: only ones a player calls with a cake |
| `marketDays` | on | A weekly market in a village with a Market Square |
| `seasonDays` | 16 (1 to 120) | Days in each of the four seasons |
| `villagerNames` | on | Villagers in a hall's village get names |
| `villagerMoods` | on | Moods that change how fast villagers work |
| `villagerSickness` | on | Villagers fall ill now and then |

## Saved data

On the hall block itself (`VillageHallBlockEntity`). A field missing from an older save reads as zero, false or
empty unless a default is given here:

- Identity: `CustomName` (none: a made-up name), `owner` and `ownerName` (none), `protected` (false), `rank`.
- People: `births`, `lastBirth`, `chronicle` (at most 100 entries, the oldest forgotten).
- Money and days: `treasury`, `treasuryTotal`, `lastTaxDay` (-1: never), `lastQuestDay` (-1), `lastMarketDay` (-1),
  `lastRaidDay` (-100), `festivalDay` (-1), `feastDay` (-1), `festivalCalled` (-100), `festivalMissed` (-1).
- Pages: `quests` and `questsDone`, `research`, `edicts`, `reforms`, `guilds`, `plan` (the City Plan), `steward`
  and `steward_desk`, `services`, `piece_looks`, `bannerBase` and `bannerPatterns` (the village's colors).
- A saved quest that can't be read (a `kind` this version doesn't know, after going back to an older jar, or a hand
  edit) is left out with a warning in the log; the hall's other quests and data load as usual.

The calendar keeps the last day it fired for with the overworld. Friends are saved in `aliveworkplace_friends`.
Not saved: the census (counted again every 600 ticks) and the screen's layout. The Free job blocks page saves
nothing of its own: a block given from it is the villager's job like any other (their job-site memory and the block's
point of interest, saved by Minecraft).

## Items, blocks, jobs, commands

- Block: Village Hall (`aliveworkplace:village_hall`, `block.aliveworkplace.village_hall`). Recipe: gold, a book and
  gold over planks, with an emerald in the middle.
- Item: Village Ledger (`aliveworkplace:village_ledger`): right-click a hall to bind it, then open the hall's screen
  from anywhere nearby; sneak-use opens the Book of Edicts.
- Jobs at the hall: the Steward (see [Steward](steward.md)); the Builder builds what the Upgrades page orders (see [Builder](builder.md)).
- Commands: `/workplace quests`, `/workplace friend add|remove|list`.

## Decisions

- 40.2 (lane c, 2026-10-26): the People table keeps the server's 54 slots: the main page still carries 25 people, and
  the table's scroll moves that window with a menu-button click (1000 + first person), so no new packet and every old
  people-list click still works. Job ▾ and Bed ▾ open what exists today (36.18's Change job or the free jobs page; the
  Homes & Food section) until 40.3 and 40.4 replace them, so neither is a dead button. Children join the people list
  (last), since the table lists them; a click on a child or a nitwit only makes them glow, as before.
- 40.3 (lane c, 2026-10-26): Job ▾'s list rides in the open person's button (a container component of one stack per
  line), so no new packet and the 54 slots stay. "No job" is free (nothing new is learned) but takes two clicks, since
  trades and level go; the jobless villager it leaves may take a free block nearby by themselves (vanilla's way, not
  held back). A jobless villager's first job stays free and one click, as 36.9's free jobs page. With
  `workplaceRetraining` off Job ▾ is hidden for everyone, the jobless included (the spec: "the rule off hides the
  button"); the free jobs page and the jobless villager's own page still give them a block. The Builder's block is the
  Blueprint Table (the spec says Builder's Bench, its old name; an old bench still counts). The old right-click Change
  job page (36.18) stays for the list without the sidebar.
- 40.4 (lane c, 2026-10-26): Bed ▾'s list rides in the open person's button as Job ▾'s does. A swap needs a bed to
  give: a villager with none sees another's bed grayed ("Jem's bed": taking it would leave Jem homeless). "Plan a house
  ›" opens the Buildings section (where the Steward's desk and the builders' plans are), shown when no bed is free.
  The mod has no builder's-bench bed of its own, so the list's note names what isn't listed: wagon beds and cradles.
  Anyone may be given a bed, children included (vanilla lets a child claim one too).

- B301 (bug lane, 2026-10-22): "can monsters come" is read from the world (Peaceful) and the blocklist's own file,
  never from whether a monster was seen lately, so it's the same on every server start and testable. The game rule that
  stops mob spawning isn't counted: spawners, raids and the mod's camps still bring foes.

- 30.4a (owner, 2026-10-05): the hall's screen is a custom UI like vanilla's workstation screens, not a chest of
  icons. The server lays out the buttons and decides every click; the client only draws.
- 26.12 (owner, 2026-10-16: "i like the look of the blueprint table"): the hall and the Storehouse board are drawn in
  the Table's style by one client screen (`ChoiceTableScreen`, the look shared through `TableStyle`). The server's
  menu, buttons and clicks did not change: the top row and the toolbar row are the strip, the rows below are the list,
  and a click is the chest click it always was (left, right, shift). Tooltips became the pane on the right, so long ones
  no longer cover the screen.
- 22.5: page tabs instead of more buttons, because five expansions each needed a page and the screen had no free
  slot. Every button that was there before stayed where players knew it.
- 22.6: one calendar for the whole world, so the Festival Cup, harvest season and stories agree on the date. The
  seasons grew from 8 to 16 days (owner, 2026-10-04); a config written before that reads 8 as "the old default".
- 36.9 (owner, 2026-10-18): filling empty job blocks moved to the hall, block first, because retraining at a block
  meant walking a villager into a finished home. Paid retraining of a working villager (26.6) stays at the block.
  The page is a spare tab (`HallPages.SPARES` went from 2 to 3), so the six tabs kept for other mods stay free. The
  tab is a vanilla crafting table, no new art.
- 36.14 (owner, 2026-10-19): a home is upgraded from the hall. The page is one more spare tab (`HallPages.SPARES` went
  from 4 to 5; it's the newest, so it gives way first). It reuses the one upgrade path (a build site of the next level
  on the same spot), so nothing new is saved: the order is the build site. "Natural" is the Steward's clearable list
  plus water, lava, ice and ores; a block of another build counts as built even when it's grass or dirt, because the
  Builder would be tearing up part of someone else's build. A claimed bed is kept by every clear stage (it was broken
  before when it stood where the next level has air); a claimed bed where the next level puts a block is "in the way".
  The tab and rows use vanilla bricks, a barrier and scaffolding, no new art.
- B37: the name icon's tooltip only offers "protect the village" to the player who may do it.
- Villages keep working with no player near (23.6, owner's call): see `keepVillagesWorking` on
  [Config switches](config-switches.md).

## Known limits

- Halls are meant to stand apart. Where two halls' circles overlap, the City Plan and the Steward keep to the nearer
  hall's side; how the head count splits shared villagers was not checked for this page.
- The hall's numbers are up to 600 ticks (30 seconds) old.
- Nothing in the mod changes with the seasons except festivals, the Festival Cup and the Harvest Idol.
- A hall placed by a machine has no owner until a player turns its protection on.
- Free job blocks: a villager given a block walks there with the usual brain; at night or in a festival their
  schedule may take them elsewhere first. Blocks past the 36th aren't clickable until nearer ones fill. Only grown
  villagers in the hall's circle are offered.
- Upgrades: the space check names the first blocking spot only; clear it and the row names the next one, if any. Spots
  in chunks that aren't loaded aren't checked (the page never loads a chunk); the Builder deals with them when he gets
  there, as for any build. The page lists up to 36 builds.

## Proof

GameTests: `VillageHallGameTests` (23 tests: the census, the name tag, eating from the store, pace, growth),
`B301GameTests` (no clearing-out quest or step where nothing can be fought, bandits and wild Pokémon counting, the
words, a stuck quest swapped after a save and reload, MobsBeGone's file read), compat `WildFoesCompatTests`,
`WagonBedsGameTests` (B206, B207: settlers leave the wagon once the hall stands, an older camp's bedroll is mended,
two free beds take in two newcomers and the hall says it's full, the inn takes a traveler only into a free house bed,
the wagon-bed list and its save, an older list lets a cabin's bed go and keeps the camp's), `QaWagonBedsBug1031Tests`
(B715: a cabin's brown bed by a campfire and hay is still a home),
`HallUiGameTests` (the hall and the Storehouse board on their own screens), `HallPagesGameTests`, `QaHallPagesSeasonsGameTests`, `HallSpecGameTests`, `ProtectionGameTests`,
`ProtectionSpecGameTests`, `HallProtectHintGameTests`, `ReadingFocusGameTests` (36.5: hovered, held on the way into
the pane, let go outside the panel for the pinned button; unpinning and page changes; the wheel and Shift),
`FreeJobsPageGameTests` (36.9: the listing with job, building and distance, `aPickTakesTheFreeBlock`, a block claimed
meanwhile refused, a villager who found work meanwhile left alone, `aGivenBlockSurvivesAReload`),
`UpgradesPageGameTests` (36.14: the list with level, place, cost and free space; `aBuildWithNoNextLevelIsNotListed`;
`aFreeUpgradeIsBuilt` by the village's Builder; `aBlockedUpgradeSaysWhatIsInTheWay` with another build's block, a
fence, a path, then water (natural); `naturalGroundDoesNotBlockAnUpgrade`, all of it cleared;
`residentsKeepTheirBedsThroughAnUpgrade`; `anOrderedUpgradeSurvivesAReload`; every sentence written).
`HallPeopleGameTests` (40.2: the rows and counts of a village of 8 and of 60 scrolled to its last villager, the
details of a builder, a guard, a child and a nitwit and Job ▾ on the builder, a villager who dies or leaves while the
screen is open; scene `hall_people`). `HallPeopleJobGameTests` (40.3, every case through Job ▾'s clicks:
`aChangeThatWorks` (price on top, "Mason · 1 free Stonecutter", the offer, then paid), `aJobWithNoFreeBlock`,
`notEnoughEmeraldsChangesNothing`, `toTheBuilder`, `aBusyBuilderHandsHisBuildBack`, `noJob` (and reloaded),
`aJoblessVillagerIsGivenABlockForNothing`, `childrenAndNitwitsCant`, `theRuleOffHidesTheButton`,
`aChangeSurvivesASaveAndReload` with every new sentence; scene `hall_people_job`). `HallPeopleBedGameTests` (40.4,
through Bed ▾'s clicks: `aFreeBed` (where the free beds are, the census, the old bed freed), `aSwap`, `noBedsAtAll`,
`theChosenBedBrokenBeforeNight`, `aBedAnotherVillagerClaimsFirst`, `theChoiceSurvivesASaveAndReload` with every new
sentence, `theySleepThereAtNight`; scene `hall_people_bed`).
`B635GameTests` (the builder's Carpenter's Workshop: its crafting table is a free Carpenter block, "Carpenter · 1 free
Crafting Table", a jobless and a working villager made the Carpenter from Job ▾; a claimed table isn't offered) and
`QaBuilders1028GameTests` (B635, QA: a table broken while Job ▾ is open makes no Carpenter and the line then says to
place one; an older save's table with no workstation record is offered once its chunk loads; tables 8 blocks from
the hall are offered, 9 aren't).

Showcase scenes: `hall` (the screen, a villager read out and kept in the pane, the calendar, GUI scales 3 and 4), `hall_pages` (chronicle and trade routes), `hall_quests`,
`hall_treasury` (treasury, protection and the ledger), `hall_reading` (a long chronicle entry read and scrolled to its end
from the pane without a click, then the Storehouse board), `hall_jobs` (the Free job blocks page, the Storehouse's
takers, Dara picked as its Porter), `hall_upgrade` (the Upgrades page: the Starter Cottage free, the Stone House II
blocked by a garden fence, a click sending Bram to build the Starter Cottage II), `hall_people_job` (40.3: Job ▾ on
Dara's row, Mason clicked once for the price, then paid).
