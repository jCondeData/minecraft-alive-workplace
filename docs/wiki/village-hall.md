# The Village Hall

The block that turns a group of villagers into a village the mod looks after: its people, food, beds, rank, calendar,
treasury and pages. Most other systems (edicts, guilds, Legends, classes, the Steward) hang off a hall.

Roadmap items: 22.5, 22.6, 30.4a, 26.12, B37, 36.5, 36.9, 36.9a, 36.15, 36.14, 36.18, B301, B303

## What a player sees

You craft a Village Hall and put it in the middle of your village. Right-click it and a screen of its own opens, drawn
like the Blueprint Table's (not a chest): under the gold title a strip of icon buttons, the village's numbers (name
and rank, villagers, beds, food in store, guards, wellbeing, requests, buildings going up, quests) and the toolbar
(map, the Book of Edicts, the bell, mercenaries, the Chronicle, a festival, trade, "What next?", the bank). On the
left a list: the page tabs, then everyone who lives within 64 blocks, each as their workstation (stacked as high as
their level) with their name and job. The pane on the right reads out whatever is under the mouse, every line of it;
with nothing there it shows the village's numbers "at a glance". Click a villager and they glow for ten seconds so you
can find them, and stay in the pane (the wheel scrolls a long one; × or Esc puts them away). Click a jobless villager
to see the free workstations and give them one. Every page of the hall opens in the same screen.

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
job's click lists who could take the block as that job (B303). A villager who already works isn't listed; they change job from their own page (36.18, below) or
at the block itself (26.6, see [Villager jobs](villager-jobs.md)), for a price.

**Change job (36.18).** A plain click on a worker in the residents list only makes them glow, as before; a right-click opens their page (back,
the worker, Find, and a **Change job** emerald at slot 22). It's offered when `HallRetraining.canRetrain` holds: rule
`workplaceRetraining` on, a grown villager with a job and a job site, not a nitwit, Legend or caravan carter. Change job
lists `VillageHalls.freeStations` of a job other than theirs, each with the price (`Retraining.price`, 5/10/20/35/50 by
level, config `retrainPrices`), and the Builder: a free Blueprint Table is listed like any block; with none, a grey
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
beds: the Builder never breaks a bed somebody sleeps in.

The buttons open the hall's pages: Quests (a journal), the Chronicle (what happened, day by day), trade routes
(caravans to other halls), "What next?" (advice, or the Steward's desk when there is a Steward), the village map, the
Book of Edicts, the calendar, Legends, the Festival Cup and Classes. The bell calls everyone home. A cake calls a
festival. A named Name Tag used on the hall names the village (the tag isn't used up; like the City Plan, the Village Ledger and
the charter, it works from the off hand when the main hand is empty, B360); otherwise it gets a made-up
name such as Thornholm.

## How it works

**The village is a circle.** Everyone and everything within `villageHallRadius` blocks of the hall (64 by default, 32
up and down) belongs to it; its villagers and its beds are counted by that one circle, so a villager and the bed beside him are
always both in or both out (B361, `VillageHalls.contains`). Once every 600 ticks the hall counts its people, beds, food, guards and light; that count
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
bed at once (B206), so a second birth, an inn's traveller or vanilla breeding can't count the same bed free: a village
with N free beds takes in at most N newcomers. With no bed free the Beds button says **The village is full: nobody new
moves in until a bed is free**.

**Wagon beds are no homes** (B206, B207, `camp/CampBeds`). The bedrolls of a Settler's Wagon's camp or a Colony Camp
are listed when the camp is placed (`aliveworkplace_camp_beds`, per dimension); an older camp's are known by their
look (a brown or green bed with a campfire and a hay bale within 10 blocks) and listed once seen. At each round the
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
have room (the same list a jobless villager's own page shows), each matched to the building whose outline holds it
(finished builds, and builds going up). Who could take a block: a grown villager in the village without a job, not a
nitwit or a Legend. One who has never worked takes any block; one who still has an old job's trades but lost the block
is offered only a block of that same job, because changing job is paid retraining (26.6). A job above a villager's
class (34.8) is greyed with the class it needs. The pick is checked again on the click: if someone took the block
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
| `villageGrowthCap` | 40 (0 to 500) | The village stops having babies at this many villagers; 0: no growth |
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
two free beds take in two newcomers and the hall says it's full, the inn takes a traveller only into a free house bed,
the wagon-bed list and its save),
`HallUiGameTests` (the hall and the Storehouse board on their own screens), `HallPagesGameTests`, `QaHallPagesSeasonsGameTests`, `HallSpecGameTests`, `ProtectionGameTests`,
`ProtectionSpecGameTests`, `HallProtectHintGameTests`, `ReadingFocusGameTests` (36.5: hovered, held on the way into
the pane, let go outside the panel for the pinned button; unpinning and page changes; the wheel and Shift),
`FreeJobsPageGameTests` (36.9: the listing with job, building and distance, `aPickTakesTheFreeBlock`, a block claimed
meanwhile refused, a villager who found work meanwhile left alone, `aGivenBlockSurvivesAReload`),
`UpgradesPageGameTests` (36.14: the list with level, place, cost and free space; `aBuildWithNoNextLevelIsNotListed`;
`aFreeUpgradeIsBuilt` by the village's Builder; `aBlockedUpgradeSaysWhatIsInTheWay` with another build's block, a
fence, a path, then water (natural); `naturalGroundDoesNotBlockAnUpgrade`, all of it cleared;
`residentsKeepTheirBedsThroughAnUpgrade`; `anOrderedUpgradeSurvivesAReload`; every sentence written).

Showcase scenes: `hall` (the screen, a villager read out and kept in the pane, the calendar, GUI scales 3 and 4), `hall_pages` (chronicle and trade routes), `hall_quests`,
`hall_treasury` (treasury, protection and the ledger), `hall_reading` (a long chronicle entry read and scrolled to its end
from the pane without a click, then the Storehouse board), `hall_jobs` (the Free job blocks page, the Storehouse's
takers, Dara picked as its Porter), `hall_upgrade` (the Upgrades page: the Starter Cottage free, the Stone House II
blocked by a garden fence, a click sending Bram to build the Starter Cottage II).
